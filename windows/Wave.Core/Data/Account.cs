namespace Wave.Data;

public enum AudioQuality { High, Low }

/** Wave's settings (the same ones as on every other device). */
public sealed record Settings
{
    /** The whole app takes on the colors of what's playing. */
    public bool MatchSongColors { get; init; } = true;
    /** Shapes, waves and lyrics react to the beat. */
    public bool MoveWithMusic { get; init; } = true;
    public AudioQuality AudioQuality { get; init; } = AudioQuality.High;
    /** When the queue runs out, keep going with songs like the last one. */
    public bool Autoplay { get; init; } = true;
    /** Turn down songs mastered louder than YouTube's reference. */
    public bool EvenVolume { get; init; } = true;
    /** Seconds songs overlap as one fades into the next; 0 = off. */
    public int CrossfadeSeconds { get; init; }
    /** Look for a new version of Wave on GitHub once a day. */
    public bool CheckUpdates { get; init; } = true;
    /** The player opens beside the app (half the window) instead of filling it. */
    public bool SplitPlayer { get; init; }
    /** Wave's own volume (0..1), separate from Windows'. */
    public double AppVolume { get; init; } = 1.0;
    /** Interface size (1 = Windows' own size). */
    public double UiScale { get; init; } = 1.0;
    /** Mini player: size name and screen corner (0 top-left .. 3 bottom-right). */
    public string MiniSize { get; init; } = "Compact";
    public int MiniCorner { get; init; } = 3;
}

public static class SettingsStore
{
    private const string FileName = "native-settings.json";
    private static Settings? current;

    public static event Action<Settings>? Changed;

    public static Settings Current => current ??= Load();

    private static Settings Load()
    {
        if (JsonFile.Read<Settings>(FileName) is { } saved) return saved;
        // First run: bring over the earlier Wave's settings.
        var old = OldPreferences.Read("settings");
        var s = new Settings();
        T Get<T>(string key, T fallback) => old.TryGetValue(key, out var v) && v is T t ? t : fallback;
        return s with
        {
            MatchSongColors = Get("match_song_colors", s.MatchSongColors),
            MoveWithMusic = Get("move_with_music", s.MoveWithMusic),
            AudioQuality = Get("audio_quality", "HIGH") == "LOW" ? AudioQuality.Low : AudioQuality.High,
            Autoplay = Get("autoplay", s.Autoplay),
            EvenVolume = Get("even_volume", s.EvenVolume),
            CrossfadeSeconds = Get("crossfade_seconds", s.CrossfadeSeconds),
            CheckUpdates = Get("check_updates", s.CheckUpdates),
            SplitPlayer = Get("split_player", s.SplitPlayer),
            AppVolume = Get("app_volume", 1f),
        };
    }

    public static void Update(Func<Settings, Settings> change)
    {
        var next = change(Current);
        current = next;
        try { JsonFile.Write(FileName, next); } catch (Exception) { }
        Changed?.Invoke(next);
    }

    /** For this session only (a slider mid-drag); [Update] saves. */
    public static void Preview(Func<Settings, Settings> change)
    {
        current = change(Current);
        Changed?.Invoke(current);
    }
}

/** What's kept after a Google sign-in. A guest has an empty cookie. */
public sealed record Session(string Cookie, string VisitorData, string DataSyncId, string AuthUser, string Name, string? Email, string? Photo)
{
    public bool Guest => string.IsNullOrEmpty(Cookie);
    public static readonly Session GuestSession = new("", "", "", "0", "Guest", null, null);
}

public static class SessionStore
{
    private const string FileName = "native-session.json";

    private sealed record Saved(string? CookieEnc, string? VisitorData, string? DataSyncId, string? AuthUser, string? Name, string? Email, string? Photo, bool Guest);

    public static Session? Current { get; private set; }
    public static event Action<Session?>? Changed;

    /** The saved session (or the earlier Wave's), or null when signed out. */
    public static Session? Restore()
    {
        var saved = JsonFile.Read<Saved>(FileName);
        if (saved is null)
        {
            var old = OldPreferences.Read("session");
            string? Str(string k) => old.TryGetValue(k, out var v) ? v as string : null;
            saved = new Saved(Str("cookie_enc"), Str("visitor_data"), Str("data_sync_id"), Str("auth_user"), Str("name"), Str("email"), Str("photo_url"),
                old.TryGetValue("guest", out var g) && g is true);
        }
        Session? session = null;
        if (saved.CookieEnc is { } enc && SecretBox.Decrypt(enc) is { Length: > 0 } cookie)
            session = new Session(cookie, saved.VisitorData ?? "", saved.DataSyncId ?? "", saved.AuthUser ?? "0", saved.Name ?? "", saved.Email, saved.Photo);
        else if (saved.Guest)
            session = Session.GuestSession;
        Current = session;
        return session;
    }

    public static void Save(Session session)
    {
        Current = session;
        JsonFile.Write(FileName, new Saved(session.Guest ? null : SecretBox.Encrypt(session.Cookie), session.VisitorData, session.DataSyncId,
            session.AuthUser, session.Name, session.Email, session.Photo, session.Guest));
        Changed?.Invoke(session);
    }

    public static void SignOut()
    {
        Current = null;
        // Signed out stays signed out: don't bring the earlier Wave's session back next launch.
        JsonFile.Write(FileName, new Saved(null, null, null, null, null, null, null, false));
        Changed?.Invoke(null);
    }
}

/**
 * Songs you've liked, known locally so every heart and thumb shows instantly (likes.txt, shared
 * with the earlier Wave). Guests' likes stay on this computer.
 */
public static class Likes
{
    private static readonly object Gate = new();
    private static HashSet<string>? ids;
    public static event Action? Changed;

    private static HashSet<string> Ids
    {
        get
        {
            lock (Gate)
            {
                if (ids is not null) return ids;
                try
                {
                    var path = Paths.File("likes.txt");
                    ids = File.Exists(path) ? File.ReadAllLines(path).Where(l => l.Length > 0).ToHashSet() : [];
                }
                catch (Exception) { ids = []; }
                return ids;
            }
        }
    }

    public static bool IsLiked(string videoId) { lock (Gate) return Ids.Contains(videoId); }

    /** True when the saved list is recent enough not to ask YouTube again. */
    public static bool Fresh => File.Exists(Paths.File("likes.txt")) && DateTime.UtcNow - File.GetLastWriteTimeUtc(Paths.File("likes.txt")) < TimeSpan.FromHours(6);

    public static void Set(string videoId, bool liked)
    {
        lock (Gate) { if (liked) Ids.Add(videoId); else Ids.Remove(videoId); }
        Save();
        Changed?.Invoke();
    }

    public static void ReplaceAll(IEnumerable<string> all)
    {
        lock (Gate) ids = all.ToHashSet();
        Save();
        Changed?.Invoke();
    }

    public static void Clear()
    {
        lock (Gate) ids = [];
        try { File.Delete(Paths.File("likes.txt")); } catch (Exception) { }
        Changed?.Invoke();
    }

    /** Just signed in: ask YouTube for this account's likes on the next load. */
    public static void Stale()
    {
        try { File.SetLastWriteTimeUtc(Paths.File("likes.txt"), DateTime.UnixEpoch); } catch (Exception) { }
    }

    private static void Save()
    {
        string[] snapshot;
        lock (Gate) snapshot = [.. Ids];
        try { File.WriteAllLines(Paths.File("likes.txt"), snapshot); } catch (Exception) { }
    }
}

/** Your recent searches (newest first, ten at most). */
public static class SearchHistory
{
    private const string FileName = "native-searches.json";
    public static IReadOnlyList<string> All => JsonFile.Read<List<string>>(FileName) ?? [];

    public static void Remember(string query)
    {
        var list = All.Where(q => !q.Equals(query, StringComparison.OrdinalIgnoreCase)).Prepend(query).Take(10).ToList();
        JsonFile.Write(FileName, list);
    }

    public static void Forget(string query) => JsonFile.Write(FileName, All.Where(q => q != query).ToList());
    public static void Clear() => JsonFile.Delete(FileName);
}
