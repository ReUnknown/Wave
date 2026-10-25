using Microsoft.UI.Input;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Wave.Data;
using Wave.Playback;
using Windows.Foundation;
using Path = Microsoft.UI.Xaml.Shapes.Path;

namespace Wave.App.Views;

/**
 * Wave's progress line: the played part is a travelling wave while music plays and springs flat
 * when paused; click or drag to seek. It only redraws while it moves (and while Wave is in front,
 * or at a capped rate for the mini player).
 */
public sealed class WavyProgress : Grid
{
    private readonly Path wave = new() { StrokeStartLineCap = PenLineCap.Round, StrokeEndLineCap = PenLineCap.Round, StrokeLineJoin = PenLineJoin.Round };
    private readonly Line track = new() { StrokeStartLineCap = PenLineCap.Round, StrokeEndLineCap = PenLineCap.Round };
    private readonly Rectangle thumb = new() { Width = 5, Height = 24, RadiusX = 2.5, RadiusY = 2.5, VerticalAlignment = VerticalAlignment.Top, HorizontalAlignment = HorizontalAlignment.Left };
    private readonly Animator amplitudeScale;
    private readonly double amplitude, wavelength, stroke;
    private readonly int maxFps;
    private readonly bool showThumb;
    private double phase;
    private DateTime lastFrame = DateTime.UtcNow;
    private bool running;
    private bool dragging;
    private double dragFraction;

    /** Called with 0..1 when you click or finish dragging. */
    public Action<double>? Seek { get; set; }

    public WavyProgress(Brush color, Brush trackColor, double stroke = 3, double amplitude = 2.5, double wavelength = 20, bool showThumb = false, int maxFps = 0)
    {
        this.stroke = stroke;
        this.amplitude = amplitude;
        this.wavelength = wavelength;
        this.showThumb = showThumb;
        this.maxFps = maxFps;
        wave.Stroke = color;
        wave.StrokeThickness = stroke;
        track.Stroke = trackColor;
        track.StrokeThickness = stroke;
        thumb.Fill = color;
        thumb.Visibility = showThumb ? Visibility.Visible : Visibility.Collapsed;
        Children.Add(track);
        Children.Add(wave);
        Children.Add(thumb);
        Background = Theme.Clear;
        ProtectedCursor = InputSystemCursor.Create(InputSystemCursorShape.Hand);
        amplitudeScale = Motion.Value(0, _ => Draw());
        SizeChanged += (_, _) => Draw();
        PointerPressed += (_, e) => { dragging = true; CapturePointer(e.Pointer); dragFraction = FractionAt(e); Draw(); };
        PointerMoved += (_, e) => { if (dragging) { dragFraction = FractionAt(e); Draw(); } };
        PointerReleased += (_, e) => { if (!dragging) return; dragging = false; ReleasePointerCapture(e.Pointer); Seek?.Invoke(FractionAt(e)); };
        Loaded += (_, _) => { App.Services.Player.Changed += OnState; App.Window.ActiveChanged += OnActive; OnState(App.Services.Player.State); };
        Unloaded += (_, _) => { App.Services.Player.Changed -= OnState; App.Window.ActiveChanged -= OnActive; Stop(); };
    }

    /** Animates even when the main window isn't in front (the mini player). */
    public bool AlwaysActive { get; set; }

    private double FractionAt(PointerRoutedEventArgs e) => Math.Clamp(e.GetCurrentPoint(this).Position.X / Math.Max(1, ActualWidth), 0, 1);

    private void OnActive() => OnState(App.Services.Player.State);

    private void OnState(PlayerState s)
    {
        amplitudeScale.To(s.IsPlaying ? 1 : 0);
        if (s.IsPlaying && (App.Window.Active || AlwaysActive)) Start(); else Stop();
        Draw();
    }

    private void Start()
    {
        if (running) return;
        running = true;
        lastFrame = DateTime.UtcNow;
        CompositionTarget.Rendering += Frame;
    }

    private void Stop()
    {
        if (!running) return;
        running = false;
        CompositionTarget.Rendering -= Frame;
    }

    private void Frame(object? sender, object e)
    {
        var now = DateTime.UtcNow;
        var dt = (now - lastFrame).TotalMilliseconds;
        if (maxFps > 0 && dt < 1000.0 / maxFps) return;
        lastFrame = now;
        phase = (phase + dt / 1400) % 1;
        Draw();
    }

    private void Draw()
    {
        var w = ActualWidth;
        var h = ActualHeight;
        if (w <= stroke * 2 || h <= 0) return;
        var state = App.Services.Player.State;
        var fraction = dragging ? dragFraction : state.DurationMs > 0 ? Math.Clamp((double)App.Services.Player.Position / state.DurationMs, 0, 1) : 0;
        var cy = h / 2;
        var x = Math.Clamp(w * fraction, stroke, w);
        var amp = amplitude * amplitudeScale.Current;
        var gap = showThumb ? 8 : 4;

        var figure = new PathFigure { StartPoint = new Point(stroke / 2, cy + amp * Math.Sin(2 * Math.PI * (stroke / 2 / wavelength - phase))) };
        var line = new PolyLineSegment();
        if (amp < 0.05) line.Points.Add(new Point(x, cy));
        else for (var px = stroke / 2 + 3; px < x + 3; px += 3)
        {
            var at = Math.Min(px, x);
            line.Points.Add(new Point(at, cy + amp * Math.Sin(2 * Math.PI * (at / wavelength - phase))));
        }
        figure.Segments.Add(line);
        var geometry = new PathGeometry();
        geometry.Figures.Add(figure);
        wave.Data = geometry;

        track.X1 = Math.Min(w, x + gap);
        track.X2 = w - stroke / 2;
        track.Y1 = track.Y2 = cy;
        track.Visibility = x + gap < w ? Visibility.Visible : Visibility.Collapsed;
        if (showThumb) thumb.Margin = new Thickness(x - 2.5, cy - 12, 0, 0);
    }
}

/** Wave's own volume (separate from Windows'): click the speaker to mute; drag, click or scroll the line. */
public sealed class SlimVolume : StackPanel
{
    private readonly FontIcon icon;
    private readonly Grid line = new() { Width = 104, Height = 28, Margin = new Thickness(6, 0, 0, 0), Background = Theme.Clear };
    private readonly Rectangle rest = new() { Height = 4, RadiusX = 2, RadiusY = 2 };
    private readonly Rectangle level = new() { Height = 4, RadiusX = 2, RadiusY = 2, HorizontalAlignment = HorizontalAlignment.Left };
    private readonly Ellipse knob = new() { Width = 12, Height = 12, HorizontalAlignment = HorizontalAlignment.Left };
    private bool dragging;

    public SlimVolume(Brush color, Brush faded)
    {
        Orientation = Orientation.Horizontal;
        icon = Ui.Icon(Glyph.VolumeUp, 22, color);
        var speaker = new Tap { Width = 36, Height = 36, CornerRadius = new CornerRadius(18) };
        speaker.Children.Add(Ui.Center(icon));
        speaker.Click += () => SettingsStore.Update(s => s with { AppVolume = s.AppVolume > 0 ? 0 : 0.7 });
        Children.Add(speaker.Pressable(0.86f));
        rest.Fill = faded;
        level.Fill = color;
        knob.Fill = color;
        line.Children.Add(rest);
        line.Children.Add(level);
        line.Children.Add(knob);
        line.PointerPressed += (_, e) => { dragging = true; line.CapturePointer(e.Pointer); Set(e, false); };
        line.PointerMoved += (_, e) => { if (dragging) Set(e, false); };
        line.PointerReleased += (_, e) => { dragging = false; line.ReleasePointerCapture(e.Pointer); Set(e, true); };
        line.PointerWheelChanged += (_, e) =>
        {
            var delta = e.GetCurrentPoint(line).Properties.MouseWheelDelta / 120.0 * 0.05;
            SettingsStore.Update(s => s with { AppVolume = Math.Clamp(s.AppVolume + delta, 0, 1) });
            e.Handled = true;
        };
        Children.Add(line);
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(this, "Wave volume");
        SettingsStore.Changed += s => DispatcherQueue.TryEnqueue(() => Show(s.AppVolume));
        line.SizeChanged += (_, _) => Show(SettingsStore.Current.AppVolume);
        Show(SettingsStore.Current.AppVolume);
    }

    private void Set(PointerRoutedEventArgs e, bool save)
    {
        var v = Math.Clamp(e.GetCurrentPoint(line).Position.X / line.Width, 0, 1);
        if (save) SettingsStore.Update(s => s with { AppVolume = v });
        else SettingsStore.Preview(s => s with { AppVolume = v });
    }

    private void Show(double v)
    {
        icon.Glyph = v == 0 ? Glyph.VolumeOff : v < 0.5 ? Glyph.VolumeDown : Glyph.VolumeUp;
        level.Width = Math.Max(0, line.Width * v);
        knob.Margin = new Thickness(Math.Clamp(line.Width * v - 6, -6, line.Width - 6), 0, 0, 0);
    }
}

/** Shared pieces of the player bar, full player and mini player. */
public static class PlayerParts
{
    /** The speed button: shows the speed; click for the choices. */
    public static Tap Speed(Brush ink, Brush active)
    {
        var tap = new Tap { Height = 32, CornerRadius = new CornerRadius(16), Padding = new Thickness(10, 0, 10, 0) };
        var label = Ui.Text("1×", 13, ink, FontWeights.Bold);
        label.VerticalAlignment = VerticalAlignment.Center;
        tap.Children.Add(label);
        void Show(PlayerState s)
        {
            label.Text = s.Speed == 1 ? "1×" : $"{s.Speed:0.##}×";
            tap.Background = s.Speed == 1 ? Theme.Clear : active;
        }
        App.Services.Player.Changed += Show;
        Show(App.Services.Player.State);
        tap.Click += () =>
        {
            var menu = new MenuFlyout();
            foreach (var speed in new[] { 0.5, 0.75, 1, 1.25, 1.5, 2 })
            {
                var item = new MenuFlyoutItem { Text = speed == 1 ? "Normal" : $"{speed:0.##}×" };
                if (Math.Abs(speed - App.Services.Player.State.Speed) < 0.01) item.FontWeight = FontWeights.Bold;
                item.Click += (_, _) => App.Services.Player.SetSpeed(speed);
                menu.Items.Add(item);
            }
            menu.ShowAt(tap);
        };
        ToolTipService.SetToolTip(tap, "Playback speed");
        return tap.Pressable(0.9f);
    }

    /** The play/pause button: a rounded square while playing that springs into a pill when paused. */
    public static Tap PlayButton(double width, double height, double playingCorner, double pausedCorner, double iconSize, Palette p)
    {
        var tap = new Tap { Width = width, Height = height, Background = p.Primary, CornerRadius = new CornerRadius(pausedCorner) };
        var icon = new PlayPauseIcon(iconSize, p.OnPrimary);
        tap.Children.Add(Ui.Center(icon));
        var corner = Motion.Value(pausedCorner, v => tap.CornerRadius = new CornerRadius(Math.Max(0, v)));
        void Show(PlayerState s)
        {
            icon.Playing = s.IsPlaying;
            corner.To(s.IsPlaying ? playingCorner : pausedCorner);
            Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(tap, s.IsPlaying ? "Pause" : "Play");
        }
        App.Services.Player.Changed += Show;
        Show(App.Services.Player.State);
        tap.Click += App.Services.Player.TogglePlay;
        return tap.Pressable(0.9f);
    }

    /** One end of a connected button group (previous / next). */
    public static Tap GroupButton(string glyph, string label, CornerRadius shape, Brush fill, Brush ink, double width, double height, Action click)
    {
        var tap = new Tap { Width = width, Height = height, CornerRadius = shape, Background = fill };
        tap.Children.Add(Ui.Center(Ui.Icon(glyph, 24, ink)));
        tap.Click += click;
        ToolTipService.SetToolTip(tap, label);
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(tap, label);
        return tap.Pressable(0.88f);
    }

    /** Elapsed or remaining time, refreshed twice a second while playing. */
    public static TextBlock Time(Brush ink, bool remaining)
    {
        var text = Ui.Text("0:00", 12, ink, FontWeights.SemiBold);
        text.VerticalAlignment = VerticalAlignment.Center;
        var timer = App.Window.DispatcherQueue.CreateTimer();
        timer.Interval = TimeSpan.FromMilliseconds(500);
        void Update()
        {
            var s = App.Services.Player.State;
            var pos = App.Services.Player.Position;
            text.Text = remaining ? (s.DurationMs > 0 ? "−" + Ui.Time(s.DurationMs - pos) : "") : Ui.Time(pos);
        }
        timer.Tick += (_, _) => Update();
        text.Loaded += (_, _) => { timer.Start(); Update(); };
        text.Unloaded += (_, _) => timer.Stop();
        return text;
    }
}

/**
 * The player as a bar across the bottom of the window, the song's art blurred behind it: the song
 * on the left, the controls and progress in the middle; lyrics, up next, speed, volume, the mini
 * player and the full player on the right.
 */
public sealed class PlayerBar : Grid
{
    private readonly MainWindow window;
    private readonly BlurredArt art = new(10);
    private readonly Border cover;
    private readonly TextBlock title;
    private readonly TextBlock artist;
    private readonly FontIcon like;
    private readonly FontIcon shuffle;
    private readonly FontIcon repeat;

    public PlayerBar(MainWindow window)
    {
        this.window = window;
        var p = Theme.App;
        Background = Theme.Black;
        Height = 96;
        Children.Add(art);
        Children.Add(new Rectangle
        {
            Fill = new LinearGradientBrush
            {
                StartPoint = new(0, 0), EndPoint = new(1, 0),
                GradientStops =
                {
                    new GradientStop { Color = Windows.UI.Color.FromArgb(107, 0, 0, 0), Offset = 0 },
                    new GradientStop { Color = Windows.UI.Color.FromArgb(148, 0, 0, 0), Offset = 0.5 },
                    new GradientStop { Color = Windows.UI.Color.FromArgb(107, 0, 0, 0), Offset = 1 },
                },
            },
        });
        Children.Add(new Rectangle { Height = 1, VerticalAlignment = VerticalAlignment.Top, Fill = new SolidColorBrush(Windows.UI.Color.FromArgb(26, 255, 255, 255)) });

        var row = Ui.Grid(columns: "*,1.2*,*");
        row.ColumnSpacing = 24;
        row.Padding = new Thickness(20, 0, 20, 0);
        row.ColumnDefinitions[1].MinWidth = 360;
        row.ColumnDefinitions[1].MaxWidth = 560;

        // The song: click it to open the player.
        var song = new Tap { CornerRadius = new CornerRadius(18), Padding = new Thickness(6), VerticalAlignment = VerticalAlignment.Center };
        var songRow = Ui.Grid(columns: "Auto,*,Auto");
        songRow.ColumnSpacing = 14;
        cover = Ui.Art(null, 60, 14, Theme.White16);
        songRow.Children.Add(cover.At(0, 0));
        title = Ui.Text("", 15, Theme.White, FontWeights.Bold);
        artist = Ui.Text("", 13, Theme.White78);
        var names = Ui.Column(0, title, artist);
        names.VerticalAlignment = VerticalAlignment.Center;
        songRow.Children.Add(names.At(0, 1));
        var likeButton = Ui.IconButton(Glyph.ThumbUp, "Like", Theme.White, App.Services.Player.ToggleLike);
        like = (FontIcon)((Grid)likeButton).Children[0];
        songRow.Children.Add(likeButton.At(0, 2));
        song.Children.Add(songRow);
        song.Click += () => window.OpenPlayer();
        row.Children.Add(song.Pressable(0.98f).At(0, 0));

        // Controls and progress.
        var center = new StackPanel { VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Stretch };
        var controls = Ui.Row(6);
        controls.HorizontalAlignment = HorizontalAlignment.Center;
        var shuffleButton = Ui.IconButton(Glyph.Shuffle, "Shuffle", Theme.White78, App.Services.Player.ToggleShuffle);
        shuffle = (FontIcon)((Grid)shuffleButton).Children[0];
        controls.Children.Add(shuffleButton);
        var group = Ui.Row(3,
            PlayerParts.GroupButton(Glyph.Previous, "Previous song", new CornerRadius(22, 8, 8, 22), Theme.White16, Theme.White, 52, 44, App.Services.Player.Previous),
            PlayerParts.PlayButton(76, 44, 12, 22, 24, p),
            PlayerParts.GroupButton(Glyph.Next, "Next song", new CornerRadius(8, 22, 22, 8), Theme.White16, Theme.White, 52, 44, App.Services.Player.Next));
        controls.Children.Add(group);
        var repeatButton = Ui.IconButton(Glyph.Repeat, "Repeat", Theme.White78, App.Services.Player.CycleRepeat);
        repeat = (FontIcon)((Grid)repeatButton).Children[0];
        controls.Children.Add(repeatButton);
        center.Children.Add(controls);
        var progressRow = Ui.Grid(columns: "Auto,*,Auto");
        progressRow.ColumnSpacing = 10;
        progressRow.Margin = new Thickness(0, 4, 0, 0);
        progressRow.Children.Add(PlayerParts.Time(Theme.White78, remaining: false).At(0, 0));
        var progress = new WavyProgress(Theme.White, Theme.White25, 3, 2.5, 20, showThumb: true) { Height = 18 };
        progress.Seek = f => App.Services.Player.Seek((long)(f * App.Services.Player.State.DurationMs));
        progressRow.Children.Add(progress.At(0, 1));
        progressRow.Children.Add(PlayerParts.Time(Theme.White78, remaining: true).At(0, 2));
        center.Children.Add(progressRow);
        row.Children.Add(center.At(0, 1));

        // Lyrics, up next, speed, volume, mini player, the full player.
        var right = Ui.Row(0,
            Ui.IconButton(Glyph.Lyrics, "Lyrics", Theme.White, () => window.OpenPlayer(NowPlayingView.Tab.Lyrics)),
            Ui.IconButton(Glyph.Queue, "Up next", Theme.White, () => window.OpenPlayer(NowPlayingView.Tab.UpNext)),
            PlayerParts.Speed(Theme.White, Theme.White16),
            new SlimVolume(Theme.White, Theme.White25) { Margin = new Thickness(0, 0, 6, 0), VerticalAlignment = VerticalAlignment.Center },
            Ui.IconButton(Glyph.MiniPlayer, "Mini player", Theme.White, MiniPlayerWindow.Open),
            Ui.IconButton(Glyph.Expand, "Open the player", Theme.White, () => window.OpenPlayer()));
        right.HorizontalAlignment = HorizontalAlignment.Right;
        right.VerticalAlignment = VerticalAlignment.Center;
        foreach (var c in right.Children) ((FrameworkElement)c).VerticalAlignment = VerticalAlignment.Center;
        row.Children.Add(right.At(0, 2));
        Children.Add(row);

        App.Services.Player.Changed += Show;
        Show(App.Services.Player.State);
    }

    private string? shownArt;

    private void Show(PlayerState s)
    {
        Visibility = s.Current is null ? Visibility.Collapsed : Visibility.Visible;
        if (s.Current is not { } song) return;
        title.Text = song.Title;
        artist.Text = song.Artist;
        if (song.Art != shownArt)
        {
            shownArt = song.Art;
            art.Url = song.Art;
            cover.Background = (Brush?)Images.Brush(song.Art, 96) ?? Theme.White16;
        }
        like.Glyph = Glyph.ThumbUp;
        like.FontFamily = s.Liked ? Theme.Icons : Theme.IconsOutlined;
        like.Foreground = s.Liked ? Theme.App.Primary : Theme.White;
        shuffle.Foreground = s.Shuffle ? Theme.App.Primary : Theme.White78;
        repeat.Glyph = s.Repeat == Repeat.One ? Glyph.RepeatOne : Glyph.Repeat;
        repeat.Foreground = s.Repeat != Repeat.Off ? Theme.App.Primary : Theme.White78;
    }
}
