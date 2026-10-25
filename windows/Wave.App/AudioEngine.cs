using Microsoft.UI.Dispatching;
using Wave.Music;
using Wave.Playback;
using Windows.Media;
using Windows.Media.Core;
using Windows.Media.Playback;
using Windows.Storage.Streams;

namespace Wave.App;

/**
 * Wave's audio on Windows' own media player (Media Foundation): hardware-accelerated decoding,
 * no audio code of our own. One MediaPlayer per song (two overlap during a crossfade). Windows'
 * media controls (media keys, the volume flyout's player, the lock screen) are Wave's too.
 */
public sealed class AudioEngine : IAudioEngine
{
    private readonly DispatcherQueue ui = DispatcherQueue.GetForCurrentThread();
    // A silent player that only carries Windows' media controls for whichever song is on.
    private readonly MediaPlayer controlsHost = new();
    private readonly SystemMediaTransportControls controls;
    private Player? player;
    private string? shownId;
    private bool shownPlaying;

    public AudioEngine()
    {
        controlsHost.CommandManager.IsEnabled = false;
        controls = controlsHost.SystemMediaTransportControls;
        controls.IsEnabled = true;
        controls.IsPlayEnabled = controls.IsPauseEnabled = controls.IsNextEnabled = controls.IsPreviousEnabled = true;
        controls.ButtonPressed += (_, e) => Post(() =>
        {
            switch (e.Button)
            {
                case SystemMediaTransportControlsButton.Play: player?.Resume(); break;
                case SystemMediaTransportControlsButton.Pause: player?.Pause(); break;
                case SystemMediaTransportControlsButton.Next: player?.Next(); break;
                case SystemMediaTransportControlsButton.Previous: player?.Previous(); break;
            }
        });
        controls.PlaybackPositionChangeRequested += (_, e) => Post(() => player?.Seek((long)e.RequestedPlaybackPosition.TotalMilliseconds));
    }

    public void Attach(Player p)
    {
        player = p;
        p.Changed += Show;
        Show(p.State);
    }

    public void Post(Action action) => ui.TryEnqueue(() =>
    {
        try { action(); } catch (Exception e) { Log.Write("player", e); }
    });

    public IVoice Create(AudioStream stream, NowPlaying song, long startMs, double gain) => new Voice(this, stream, startMs, gain);

    /** Windows' media controls: what's playing, playing or paused. */
    private void Show(PlayerState state)
    {
        controls.PlaybackStatus = state.Current is null ? MediaPlaybackStatus.Closed : state.IsPlaying ? MediaPlaybackStatus.Playing : MediaPlaybackStatus.Paused;
        if (state.Current is { } song && (song.VideoId != shownId || state.IsPlaying != shownPlaying))
        {
            shownId = song.VideoId;
            shownPlaying = state.IsPlaying;
            var d = controls.DisplayUpdater;
            d.Type = MediaPlaybackType.Music;
            d.MusicProperties.Title = song.Title;
            d.MusicProperties.Artist = song.Artist;
            d.MusicProperties.AlbumTitle = song.Album ?? "";
            if (song.Art.AtSize(Art.Card) is { } art) d.Thumbnail = RandomAccessStreamReference.CreateFromUri(new Uri(art));
            d.Update();
            var timeline = new SystemMediaTransportControlsTimelineProperties
            {
                StartTime = TimeSpan.Zero,
                MinSeekTime = TimeSpan.Zero,
                Position = TimeSpan.FromMilliseconds(player?.Position ?? 0),
                EndTime = TimeSpan.FromMilliseconds(state.DurationMs),
                MaxSeekTime = TimeSpan.FromMilliseconds(state.DurationMs),
            };
            controls.UpdateTimelineProperties(timeline);
        }
    }

    /** One song playing. */
    private sealed class Voice : IVoice
    {
        private readonly AudioEngine engine;
        private readonly MediaPlayer media = new();
        private double gain;
        private double fade = 1;
        private bool paused;
        private bool disposed;
        private DispatcherQueueTimer? fader;

        public event Action? Ready;
        public event Action? Ended;
        public event Action<string>? Failed;

        public bool Started { get; private set; }

        public Voice(AudioEngine engine, AudioStream stream, long startMs, double gain)
        {
            this.engine = engine;
            this.gain = gain;
            media.CommandManager.IsEnabled = false;
            media.AudioCategory = MediaPlayerAudioCategory.Media;
            media.Volume = gain;
            media.MediaOpened += (_, _) => engine.Post(() =>
            {
                if (disposed) return;
                if (startMs > 0) media.PlaybackSession.Position = TimeSpan.FromMilliseconds(startMs);
                Started = true;
                if (!paused) media.Play();
                Ready?.Invoke();
            });
            media.MediaEnded += (_, _) => engine.Post(() => { if (!disposed) Ended?.Invoke(); });
            media.MediaFailed += (_, e) => engine.Post(() => { if (!disposed) Failed?.Invoke(e.ErrorMessage); });
            media.Source = MediaSource.CreateFromUri(new Uri(stream.Url));
        }

        public long PositionMs => disposed ? 0 : (long)media.PlaybackSession.Position.TotalMilliseconds;
        public long DurationMs => disposed ? 0 : (long)media.PlaybackSession.NaturalDuration.TotalMilliseconds;

        public bool Paused
        {
            get => paused;
            set
            {
                paused = value;
                if (disposed || !Started) return;
                if (value) media.Pause(); else media.Play();
            }
        }

        public double Gain
        {
            get => gain;
            set { gain = value; Apply(); }
        }

        public double Speed
        {
            get => media.PlaybackSession.PlaybackRate;
            set { if (!disposed) media.PlaybackSession.PlaybackRate = value; }
        }

        public void Seek(long ms)
        {
            if (!disposed) media.PlaybackSession.Position = TimeSpan.FromMilliseconds(ms);
        }

        private void Apply()
        {
            if (!disposed) media.Volume = Math.Clamp(gain * fade, 0, 1);
        }

        public void FadeIn(long ms) => Ramp(0, 1, ms);
        public void FadeOut(long ms) => Ramp(fade, 0, ms);

        /** Equal-power fades, stepped every 30 ms. */
        private void Ramp(double from, double to, long ms)
        {
            fader?.Stop();
            var start = DateTime.UtcNow;
            fade = from;
            Apply();
            fader = engine.ui.CreateTimer();
            fader.Interval = TimeSpan.FromMilliseconds(30);
            fader.Tick += (t, _) =>
            {
                var p = Math.Min(1, (DateTime.UtcNow - start).TotalMilliseconds / ms);
                fade = to > from ? Math.Sin(p * Math.PI / 2) : Math.Cos(p * Math.PI / 2) * from;
                Apply();
                if (p >= 1) t.Stop();
            };
            fader.Start();
        }

        public void Dispose()
        {
            if (disposed) return;
            disposed = true;
            fader?.Stop();
            try { media.Pause(); } catch (Exception) { }
            media.Source = null;
            media.Dispose();
        }
    }
}
