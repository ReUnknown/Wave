using Wave.Data;
using Wave.Music;

namespace Wave.Playback;

/** A song in the queue. */
public sealed record NowPlaying(string VideoId, string Title, string Artist, string? Art, string? Album = null, long? DurationMs = null)
{
    public static NowPlaying From(Song s) => new(s.VideoId, s.Title, s.ArtistNames, s.Art.AtSize(Music.Art.Full), s.Album?.Name, s.DurationMs);
}

/** Where the queue came from: [PlaylistId] rebuilds it, [Collection] for albums and playlists. */
public sealed record PlaybackContext(string Id, string Title, string? Art, string? PlaylistId, bool Collection);

public enum Repeat { Off = 0, One = 1, All = 2 }

public sealed record PlayRequest(
    IReadOnlyList<NowPlaying> Items,
    int StartIndex,
    PlaybackContext Context,
    bool Shuffle,
    /** The rest of a long queue, loaded after playback has started. */
    Func<Task<IReadOnlyList<NowPlaying>>>? More = null);

public sealed record PlayerState(
    NowPlaying? Current,
    bool IsPlaying,
    bool IsBuffering,
    long DurationMs,
    bool Shuffle,
    Repeat Repeat,
    int Index,
    IReadOnlyList<NowPlaying> Queue,
    PlaybackContext? Context,
    bool Liked,
    /** Queue indices in the order they'll actually play next. */
    IReadOnlyList<int> Upcoming,
    double Speed)
{
    public static readonly PlayerState Empty = new(null, false, false, 0, false, Repeat.Off, 0, [], null, false, [], 1.0);
}

/** One song playing through the platform's audio engine. Events may arrive on any thread. */
public interface IVoice : IDisposable
{
    event Action? Ready;
    event Action? Ended;
    event Action<string>? Failed;
    bool Started { get; }
    long PositionMs { get; }
    long DurationMs { get; }
    bool Paused { get; set; }
    /** The song's own gain (even volume) times Wave's volume; fades multiply on top. */
    double Gain { get; set; }
    double Speed { get; set; }
    void Seek(long ms);
    void FadeIn(long ms);
    void FadeOut(long ms);
}

public interface IAudioEngine
{
    IVoice Create(AudioStream stream, NowPlaying song, long startMs, double gain);
    /** Runs [action] on the thread the player lives on (the UI thread). */
    void Post(Action action);
}

/**
 * Wave's player: the queue, shuffle order and play modes on top of the platform's voices.
 * Crossfade, even volume, autoplay when the queue runs out, plays counted on YouTube, a failed
 * stream retried once, and the queue kept between launches. Lives on the UI thread.
 */
public sealed class Player
{
    private readonly IAudioEngine engine;
    private readonly YouTubeMusic youtube;
    private readonly Streams streams;

    private List<NowPlaying> queue = [];
    private List<int> order = [];
    private int index = -1;
    private PlaybackContext? context;
    private bool shuffle;
    private Repeat repeat;
    private double speed = 1;
    private bool wantsPlay;
    private bool buffering;
    private IVoice? voice;
    private IVoice? fadingOut;
    private int loadGeneration;
    private int failures;
    private string? autoplayFrom;
    private long restoredPosition;
    private readonly Dictionary<string, double?> loudness = new();

    public PlayerState State { get; private set; } = PlayerState.Empty;
    public event Action<PlayerState>? Changed;
    /** A song started that you should hear about (prefetch lyrics, colors...). */
    public event Action<NowPlaying>? SongStarted;

    public long Position => voice?.PositionMs ?? restoredPosition;

    public Player(IAudioEngine engine, YouTubeMusic youtube, Streams streams)
    {
        this.engine = engine;
        this.youtube = youtube;
        this.streams = streams;
        Likes.Changed += () => engine.Post(Publish);
        SettingsStore.Changed += _ => engine.Post(() => { if (voice is not null) voice.Gain = GainFor(Current); });
        _ = TickAsync();
    }

    private NowPlaying? Current => index >= 0 && index < queue.Count ? queue[index] : null;

    private async Task TickAsync()
    {
        var ticks = 0;
        while (true)
        {
            await Task.Delay(200);
            try
            {
                CheckCrossfade();
                if (++ticks % 75 == 0 && wantsPlay) Save();
            }
            catch (Exception) { }
        }
    }

    // ---------------------------------------------------------------- Loading

    private void Load(int at, long startMs = 0, long fadeInMs = 0)
    {
        if (at < 0 || at >= queue.Count) return;
        var song = queue[at];
        index = at;
        restoredPosition = 0;
        var generation = ++loadGeneration;
        if (fadeInMs == 0) { voice?.Dispose(); }
        voice = null;
        buffering = true;
        Publish();
        _ = LoadAsync(song, generation, startMs, fadeInMs);
    }

    private async Task LoadAsync(NowPlaying song, int generation, long startMs, long fadeInMs)
    {
        AudioStream stream;
        try
        {
            streams.LowData = SettingsStore.Current.AudioQuality == AudioQuality.Low;
            stream = await Task.Run(() => streams.ResolveAsync(song.VideoId));
        }
        catch (Exception e)
        {
            if (generation == loadGeneration) OnFailed(song, e.Message);
            return;
        }
        if (generation != loadGeneration) return;
        var created = engine.Create(stream, song, startMs, GainFor(song));
        created.Paused = !wantsPlay;
        created.Speed = speed;
        if (fadeInMs > 0) created.FadeIn(fadeInMs);
        voice = created;
        created.Ended += () => engine.Post(() => { if (voice == created) Advance(auto: true); });
        created.Failed += message => engine.Post(() => { if (voice == created) OnFailed(song, message); });
        created.Ready += () => engine.Post(() =>
        {
            if (voice != created) return;
            buffering = false;
            failures = 0;
            Publish();
            CountPlay(song, created);
            SongStarted?.Invoke(song);
            PrefetchNext();
            MaybeAutoplay();
            Save();
        });
        _ = LearnLoudnessAsync(song);
    }

    /** Even volume needs how loud a song is mastered: asked once, in the background. */
    private async Task LearnLoudnessAsync(NowPlaying song)
    {
        if (loudness.ContainsKey(song.VideoId) || !youtube.Api.SignedIn) return;
        loudness[song.VideoId] = null;
        try
        {
            var (db, _, _) = await youtube.PlayerInfoAsync(song.VideoId);
            loudness[song.VideoId] = db;
            if (Current?.VideoId == song.VideoId && voice is not null) voice.Gain = GainFor(song);
        }
        catch (Exception) { }
    }

    private void PrefetchNext()
    {
        if (NextIndex() is { } next && next < queue.Count)
        {
            _ = streams.PrefetchAsync(queue[next].VideoId);
        }
    }

    private double GainFor(NowPlaying? song)
    {
        var settings = SettingsStore.Current;
        var even = 1.0;
        if (settings.EvenVolume && song is not null && loudness.TryGetValue(song.VideoId, out var db) && db is > 0)
            even = Math.Clamp(Math.Pow(10, -db.Value / 20), 0.2, 1);
        var app = settings.AppVolume;
        return even * app * app;
    }

    /** A stream that won't play gets one fresh try, then Wave moves on instead of going silent. */
    private void OnFailed(NowPlaying song, string message)
    {
        System.Diagnostics.Debug.WriteLine($"Wave: {song.Title} failed: {message}");
        streams.Forget(song.VideoId);
        if (++failures <= 1)
        {
            Load(index, Position);
            return;
        }
        failures = 0;
        buffering = false;
        if (NextIndex() is not null) Advance(auto: false);
        else { wantsPlay = false; Publish(); }
    }

    /** Like YouTube, a song counts once it has played for a few seconds. */
    private async void CountPlay(NowPlaying song, IVoice playing)
    {
        var heard = 0;
        while (heard < 4000)
        {
            await Task.Delay(500);
            if (voice != playing) return;
            if (wantsPlay) heard += 500;
        }
        RecentPlays.Add(song);
        if (!youtube.Api.SignedIn) return;
        try
        {
            var (_, tracking, _) = await youtube.PlayerInfoAsync(song.VideoId);
            if (tracking is not null) await youtube.ReportPlayAsync(tracking, context?.PlaylistId);
        }
        catch (Exception) { }
    }

    // ---------------------------------------------------------------- Moving through the queue

    private int? NextIndex()
    {
        var spot = order.IndexOf(index);
        return spot >= 0 && spot + 1 < order.Count ? order[spot + 1] : null;
    }

    private int? PreviousIndex()
    {
        var spot = order.IndexOf(index);
        return spot > 0 ? order[spot - 1] : null;
    }

    private void Advance(bool auto)
    {
        if (auto && repeat == Repeat.One) { Load(index); return; }
        var next = NextIndex() ?? (repeat == Repeat.All && order.Count > 0 ? order[0] : null);
        if (next is null)
        {
            wantsPlay = false;
            voice?.Dispose();
            voice = null;
            Publish();
            return;
        }
        Load(next.Value);
    }

    /** Crossfade: near the end of a song, the next one starts quietly and the two overlap. */
    private void CheckCrossfade()
    {
        var seconds = SettingsStore.Current.CrossfadeSeconds;
        var current = voice;
        if (current is null || seconds <= 0 || !wantsPlay || !current.Started || repeat == Repeat.One || fadingOut is not null) return;
        var duration = current.DurationMs;
        if (duration <= 0 || duration < seconds * 2000L + 10_000) return;
        if (NextIndex() is not { } next) return;
        if (duration - current.PositionMs > seconds * 1000L) return;
        current.FadeOut(seconds * 1000L);
        fadingOut = current;
        _ = Task.Delay(seconds * 1000 + 500).ContinueWith(_ => engine.Post(() =>
        {
            current.Dispose();
            if (fadingOut == current) fadingOut = null;
        }));
        voice = null;
        Load(next, fadeInMs: seconds * 1000L);
    }

    /** Autoplay: when you reach the last song (repeat off), add songs like it. */
    private async void MaybeAutoplay()
    {
        if (!SettingsStore.Current.Autoplay || repeat != Repeat.Off || queue.Count == 0) return;
        if (order.Count - order.IndexOf(index) - 1 > 1) return;
        var seed = queue[order[^1]].VideoId;
        if (autoplayFrom == seed) return;
        autoplayFrom = seed;
        try
        {
            var have = queue.Select(q => q.VideoId).ToHashSet();
            var next = await youtube.NextAsync(seed, "RDAMVM" + seed);
            var fresh = next.Songs.Where(s => !have.Contains(s.VideoId) && !s.IsVideo && !s.IsEpisode).DistinctBy(s => s.VideoId).Take(25).Select(NowPlaying.From).ToList();
            if (fresh.Count > 0) Append(fresh);
        }
        catch (Exception) { }
    }

    private void Append(IReadOnlyList<NowPlaying> items)
    {
        var start = queue.Count;
        queue.AddRange(items);
        var added = Enumerable.Range(start, items.Count).ToList();
        if (shuffle)
        {
            var at = Math.Max(0, order.IndexOf(index));
            foreach (var i in added.OrderBy(_ => Random.Shared.Next())) order.Insert(Random.Shared.Next(at + 1, order.Count + 1), i);
        }
        else order.AddRange(added);
        Publish();
        Save();
    }

    private void BuildOrder(int current)
    {
        var all = Enumerable.Range(0, queue.Count).ToList();
        order = shuffle && queue.Count > 0
            ? all.Where(i => i != current).OrderBy(_ => Random.Shared.Next()).Prepend(current).ToList()
            : all;
    }

    // ---------------------------------------------------------------- Controls

    public void Play(PlayRequest request, long startMs = 0)
    {
        queue = request.Items.ToList();
        context = request.Context;
        shuffle = request.Shuffle;
        autoplayFrom = null;
        var start = Math.Clamp(request.StartIndex, 0, Math.Max(0, queue.Count - 1));
        BuildOrder(start);
        wantsPlay = true;
        Load(start, startMs);
        if (request.More is { } more) _ = LoadMoreAsync(more, request.Context.Id);
    }

    private async Task LoadMoreAsync(Func<Task<IReadOnlyList<NowPlaying>>> more, string contextId)
    {
        try
        {
            var extra = await more();
            var have = queue.Select(q => q.VideoId).ToHashSet();
            var fresh = extra.Where(s => have.Add(s.VideoId)).ToList();
            if (fresh.Count > 0 && context?.Id == contextId) Append(fresh);
        }
        catch (Exception) { }
    }

    public void PlayNext(IReadOnlyList<NowPlaying> items)
    {
        if (queue.Count == 0) { Play(new PlayRequest(items, 0, new PlaybackContext("next", items[0].Title, items[0].Art, null, false), false)); return; }
        var at = index + 1;
        queue.InsertRange(at, items);
        order = order.Select(i => i >= at ? i + items.Count : i).ToList();
        order.InsertRange(order.IndexOf(index) + 1, Enumerable.Range(at, items.Count));
        Publish();
        Save();
    }

    public void AddToQueue(IReadOnlyList<NowPlaying> items)
    {
        if (queue.Count == 0) { PlayNext(items); return; }
        Append(items);
    }

    public void TogglePlay()
    {
        if (queue.Count == 0) return;
        wantsPlay = !wantsPlay;
        if (voice is null && wantsPlay && buffering == false)
        {
            Load(Math.Max(0, index), restoredPosition);
            return;
        }
        if (voice is not null) voice.Paused = !wantsPlay;
        if (fadingOut is not null) fadingOut.Paused = !wantsPlay;
        Publish();
        Save();
    }

    public void Pause() { if (wantsPlay) TogglePlay(); }
    public void Resume() { if (!wantsPlay) TogglePlay(); }

    public void Next() { wantsPlay = true; Advance(auto: false); }

    /** Restarts the song if it's a few seconds in, otherwise goes back one. */
    public void Previous()
    {
        if (Position > 3000) { Seek(0); return; }
        var previous = PreviousIndex();
        wantsPlay = true;
        if (previous is null) Seek(0); else Load(previous.Value);
    }

    public void Seek(long ms)
    {
        if (voice is null) restoredPosition = ms; else voice.Seek(ms);
        Publish();
    }

    public void SkipTo(int queueIndex)
    {
        if (queueIndex < 0 || queueIndex >= queue.Count) return;
        wantsPlay = true;
        Load(queueIndex);
    }

    public void RemoveAt(int queueIndex)
    {
        if (queueIndex < 0 || queueIndex >= queue.Count) return;
        var wasCurrent = queueIndex == index;
        var nextBefore = wasCurrent ? NextIndex() : null;
        queue.RemoveAt(queueIndex);
        order = order.Where(i => i != queueIndex).Select(i => i > queueIndex ? i - 1 : i).ToList();
        if (index > queueIndex) index--;
        if (wasCurrent)
        {
            var target = nextBefore is { } n ? (n > queueIndex ? n - 1 : n) : (int?)null;
            if (target is { } t && t < queue.Count) Load(t);
            else { voice?.Dispose(); voice = null; wantsPlay = false; }
        }
        Publish();
        Save();
    }

    public void Move(int from, int to)
    {
        if (from < 0 || from >= queue.Count || to < 0 || to >= queue.Count) return;
        var current = Current;
        var song = queue[from];
        queue.RemoveAt(from);
        queue.Insert(to, song);
        if (current is not null) index = queue.IndexOf(current);
        if (!shuffle) order = Enumerable.Range(0, queue.Count).ToList();
        Publish();
        Save();
    }

    public void SetSpeed(double value)
    {
        speed = value;
        if (voice is not null) voice.Speed = value;
        Publish();
    }

    public void ToggleShuffle()
    {
        shuffle = !shuffle;
        BuildOrder(Math.Max(0, index));
        Publish();
        Save();
    }

    public void CycleRepeat()
    {
        repeat = repeat switch { Repeat.Off => Repeat.All, Repeat.All => Repeat.One, _ => Repeat.Off };
        Publish();
        Save();
    }

    public void ToggleLike()
    {
        if (Current is not { } song) return;
        SetLiked(song.VideoId, !Likes.IsLiked(song.VideoId));
    }

    public void SetLiked(string videoId, bool liked)
    {
        Likes.Set(videoId, liked);
        if (youtube.Api.SignedIn) _ = youtube.LikeAsync(videoId, liked).ContinueWith(_ => { });
    }

    /** Stops and forgets the queue (sign-out). */
    public void Reset()
    {
        loadGeneration++;
        voice?.Dispose();
        fadingOut?.Dispose();
        voice = null;
        fadingOut = null;
        queue = [];
        order = [];
        index = -1;
        context = null;
        wantsPlay = false;
        QueueFile.Clear();
        Publish();
    }

    /** Wave is closing: save where you are, stop the audio. */
    public void Shutdown()
    {
        Save();
        voice?.Dispose();
        fadingOut?.Dispose();
    }

    private void Publish()
    {
        var current = Current;
        var spot = order.IndexOf(index);
        State = new PlayerState(
            current,
            wantsPlay && current is not null,
            buffering,
            voice?.DurationMs is > 0 ? voice.DurationMs : current?.DurationMs ?? 0,
            shuffle,
            repeat,
            Math.Max(0, index),
            queue.ToList(),
            context,
            current is not null && Likes.IsLiked(current.VideoId),
            spot >= 0 ? order.Skip(spot + 1).ToList() : [],
            speed);
        Changed?.Invoke(State);
    }

    // ---------------------------------------------------------------- Keeping your place

    private void Save()
    {
        if (queue.Count == 0) return;
        var saved = new QueueFile.Saved(
            queue.Select(q => new QueueFile.Item(q.VideoId, q.Title, q.Artist, q.Art, q.Album, q.DurationMs)).ToList(),
            index, Position, shuffle, (int)repeat, order.ToList(),
            context is null ? null : new QueueFile.Context(context.Id, context.Title, context.Art, context.PlaylistId, context.Collection));
        _ = Task.Run(() => QueueFile.Write(saved));
    }

    /** The queue from last time, paused where you left it. */
    public void Restore()
    {
        var saved = QueueFile.Read();
        if (saved is null || saved.Items.Count == 0) return;
        queue = saved.Items.Select(i => new NowPlaying(i.Id, i.Title, i.Artist, i.Art, i.Album, i.DurationMs)).ToList();
        index = Math.Clamp(saved.Index, 0, queue.Count - 1);
        shuffle = saved.Shuffle;
        repeat = (Repeat)saved.Repeat;
        order = saved.Order.Count == queue.Count ? saved.Order.ToList() : Enumerable.Range(0, queue.Count).ToList();
        context = saved.Context is { } c ? new PlaybackContext(c.Id, c.Title, c.Art, c.PlaylistId, c.Collection) : null;
        restoredPosition = saved.PositionMs;
        wantsPlay = false;
        Publish();
    }
}

/** The queue between launches (queue.json, the same file and format as the earlier Wave). */
public static class QueueFile
{
    public sealed record Item(string Id, string Title, string Artist, string? Art, string? Album, long? DurationMs);
    public sealed record Context(string Id, string Title, string? Art, string? PlaylistId, bool Collection);
    public sealed record Saved(List<Item> Items, int Index, long PositionMs, bool Shuffle, int Repeat, List<int> Order, Context? Context);

    public static void Write(Saved saved)
    {
        try { JsonFile.Write("queue.json", saved); } catch (Exception) { }
    }

    public static Saved? Read() => JsonFile.Read<Saved>("queue.json");
    public static void Clear() => JsonFile.Delete("queue.json");
}

/** Songs played in Wave since it started, newest first ("Recently played" shows them right away). */
public static class RecentPlays
{
    private static readonly List<NowPlaying> songs = [];
    public static event Action? Changed;

    public static IReadOnlyList<NowPlaying> All { get { lock (songs) return songs.ToList(); } }

    public static void Add(NowPlaying song)
    {
        lock (songs)
        {
            songs.RemoveAll(s => s.VideoId == song.VideoId);
            songs.Insert(0, song);
            if (songs.Count > 12) songs.RemoveRange(12, songs.Count - 12);
        }
        Changed?.Invoke();
    }
}
