using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Documents;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Wave.Coloring;
using Wave.Data;
using Wave.Music;
using Wave.Playback;

namespace Wave.App.Views;

/**
 * The full player. The song's art, blurred, fills the window behind everything: the cover above
 * the controls on the left, Lyrics and Up next in a panel on the right. Always in the song's dark
 * colors. With "Player beside the app" it takes half the window and the app stays beside it.
 */
public sealed class NowPlayingView : Grid
{
    public enum Tab { Lyrics, UpNext }

    private readonly MainWindow window;
    private readonly Palette p = new(Scheme.DarkBlue);
    private readonly BlurredArt backdrop = new(12);
    private readonly Rectangle shade = new();
    private readonly Border cover = new() { CornerRadius = new CornerRadius(40), VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Center };
    private readonly TextBlock title;
    private readonly TextBlock artist;
    private readonly TextBlock from;
    private readonly Tap likeButton;
    private readonly FontIcon likeIcon;
    private readonly Animator likeCorner;
    private readonly Tap shuffleMode, repeatMode;
    private readonly Grid panelHost = new();
    private readonly Tap lyricsTab, queueTab;
    private readonly LyricsPanel lyrics;
    private readonly QueuePanel queue;
    private Tab tab = Tab.UpNext;
    private readonly Grid body;
    private readonly Grid left;
    private readonly Grid panel;
    private readonly Tap playerTab;
    private readonly Grid narrowButtons;
    /** Narrow windows: the player and the panel take turns. */
    private bool panelInFront;
    private string? shownArt;
    private string? shownId;

    public bool Open { get; private set; }
    public bool Docked => SettingsStore.Current.SplitPlayer;

    public NowPlayingView(MainWindow window)
    {
        this.window = window;
        Visibility = Visibility.Collapsed;
        Background = p.Background;
        RequestedTheme = ElementTheme.Dark;
        Children.Add(backdrop);
        Children.Add(shade);
        p.Changed += _ => PaintShade();
        PaintShade();

        var layout = Ui.Grid("Auto,*");
        layout.Padding = new Thickness(0, MainWindow.TitleBarHeight, 0, 0);

        // Header: collapse, where it's playing from, more.
        var header = Ui.Grid(columns: "Auto,*,Auto");
        header.Margin = new Thickness(16, 8, 16, 8);
        header.Children.Add(Glass(Glyph.ChevronDown, "Collapse player", Close).At(0, 0));
        from = Ui.Text("", 14, Theme.White, FontWeights.Bold);
        from.HorizontalAlignment = HorizontalAlignment.Center;
        var fromLabel = Ui.Text("PLAYING FROM", 11, new SolidColorBrush(Windows.UI.Color.FromArgb(204, 255, 255, 255)), FontWeights.SemiBold, 1, 72);
        fromLabel.HorizontalAlignment = HorizontalAlignment.Center;
        header.Children.Add(Ui.Column(0, fromLabel, from).At(0, 1));
        var more = Glass(Glyph.More, "More options", () => { });
        more.Click += () =>
        {
            if (App.Services.Player.State.Current is { } s)
                ItemMenu.ShowAt(more, palette: p, item: new Song(s.VideoId, s.Title, [new ArtistRef(s.Artist, null)], s.Album is null ? null : new AlbumRef(s.Album, null), s.Art, s.DurationMs));
        };
        header.Children.Add(more.At(0, 2));
        layout.Children.Add(header.At(0));

        body = Ui.Grid(columns: "*,*");
        body.ColumnSpacing = 32;
        body.Margin = new Thickness(24, 0, 24, 16);

        // Left: the cover above the controls.
        left = Ui.Grid("*,Auto");
        var coverBox = new Grid();
        coverBox.Children.Add(cover);
        coverBox.SizeChanged += (_, e) => cover.Width = cover.Height = Math.Max(0, Math.Min(e.NewSize.Width, e.NewSize.Height));
        cover.Shadow = new ThemeShadow();
        cover.Translation = new System.Numerics.Vector3(0, 0, 32);
        left.Children.Add(coverBox.At(0));

        var controls = new StackPanel { MaxWidth = 560, Margin = new Thickness(0, 20, 0, 0) };
        var titleRow = Ui.Grid(columns: "*,Auto");
        titleRow.ColumnSpacing = 12;
        title = Ui.Text("", 32, p.OnBackground, FontWeights.ExtraBold, 2, -16);
        artist = Ui.Text("", 18, p.OnSurfaceVariant);
        var names = Ui.Column(0, title, artist);
        names.VerticalAlignment = VerticalAlignment.Bottom;
        titleRow.Children.Add(names.At(0, 0));
        likeButton = new Tap { Width = 52, Height = 52, CornerRadius = new CornerRadius(18), Background = p.PrimaryContainer, VerticalAlignment = VerticalAlignment.Bottom };
        likeIcon = Ui.Icon(Glyph.ThumbUp, 24, p.OnPrimaryContainer, outlined: true);
        likeButton.Children.Add(Ui.Center(likeIcon));
        likeButton.Click += App.Services.Player.ToggleLike;
        likeCorner = Motion.Value(18, v => likeButton.CornerRadius = new CornerRadius(v));
        titleRow.Children.Add(likeButton.Pressable(0.88f).At(0, 1));
        controls.Children.Add(titleRow);

        var progress = new WavyProgress(p.OnBackground, new SolidColorBrush(Windows.UI.Color.FromArgb(64, 255, 255, 255)), 5, 4, 24, showThumb: true) { Height = 32, Margin = new Thickness(0, 16, 0, 0) };
        p.Changed += s => ((WavyProgress)progress).Children.OfType<Microsoft.UI.Xaml.Shapes.Path>().First().Stroke = p.OnBackground;
        progress.Seek = f => App.Services.Player.Seek((long)(f * App.Services.Player.State.DurationMs));
        controls.Children.Add(progress);
        var times = Ui.Grid(columns: "*,Auto");
        times.Children.Add(PlayerParts.Time(p.OnSurfaceVariant, false).At(0, 0));
        times.Children.Add(PlayerParts.Time(p.OnSurfaceVariant, true).At(0, 1));
        controls.Children.Add(times);

        var group = Ui.Grid(columns: "84,*,84");
        group.ColumnSpacing = 4;
        group.Height = 92;
        group.Margin = new Thickness(0, 14, 0, 0);
        group.Children.Add(PlayerParts.GroupButton(Glyph.Previous, "Previous song", new CornerRadius(46, 14, 14, 46), p.SecondaryContainer, p.OnSecondaryContainer, 84, 92, App.Services.Player.Previous).At(0, 0));
        var play = PlayerParts.PlayButton(double.NaN, 92, 30, 46, 46, p);
        play.HorizontalAlignment = HorizontalAlignment.Stretch;
        group.Children.Add(play.At(0, 1));
        group.Children.Add(PlayerParts.GroupButton(Glyph.Next, "Next song", new CornerRadius(14, 46, 46, 14), p.SecondaryContainer, p.OnSecondaryContainer, 84, 92, App.Services.Player.Next).At(0, 2));
        controls.Children.Add(group);

        var modes = Ui.Grid(columns: "*,*");
        modes.ColumnSpacing = 8;
        modes.Height = 48;
        modes.Margin = new Thickness(0, 10, 0, 0);
        shuffleMode = Mode(Glyph.Shuffle, App.Services.Player.ToggleShuffle);
        repeatMode = Mode(Glyph.Repeat, App.Services.Player.CycleRepeat);
        modes.Children.Add(shuffleMode.At(0, 0));
        modes.Children.Add(repeatMode.At(0, 1));
        controls.Children.Add(modes);
        var extras = Ui.Grid(columns: "*,Auto");
        extras.Margin = new Thickness(0, 10, 0, 0);
        extras.Children.Add(new SlimVolume(p.OnBackground, p.Track).At(0, 0));
        extras.Children.Add(PlayerParts.Speed(p.OnBackground, p.Faint, p).At(0, 1));
        controls.Children.Add(extras);
        narrowButtons = Ui.Grid(columns: "*,*");
        narrowButtons.ColumnSpacing = 8;
        narrowButtons.Margin = new Thickness(0, 10, 0, 0);
        narrowButtons.Children.Add(Ui.Button(Glyph.Queue, "Up next", p.Faint, p.OnBackground, () => Front(Tab.UpNext), new CornerRadius(22), 44).At(0, 0));
        narrowButtons.Children.Add(Ui.Button(Glyph.Lyrics, "Lyrics", p.Faint, p.OnBackground, () => Front(Tab.Lyrics), new CornerRadius(22), 44).At(0, 1));
        controls.Children.Add(narrowButtons);
        left.Children.Add(controls.At(1));
        body.Children.Add(left.At(0, 0));

        // Right: Lyrics and Up next, one tap apart.
        panel = Ui.Grid("Auto,*");
        panel.CornerRadius = new CornerRadius(32);
        panel.Background = new SolidColorBrush(Windows.UI.Color.FromArgb(51, 0, 0, 0));
        var tabs = Ui.Grid(columns: "Auto,*,*");
        tabs.ColumnSpacing = 3;
        tabs.Height = 44;
        tabs.Margin = new Thickness(12);
        lyricsTab = TabButton("Lyrics", new CornerRadius(22, 8, 8, 22), () => ShowTab(Tab.Lyrics));
        queueTab = TabButton("Up next", new CornerRadius(8, 22, 22, 8), () => ShowTab(Tab.UpNext));
        playerTab = Ui.IconButton(Glyph.Back, "Back to the player", p.OnBackground, () => { panelInFront = false; Fit(); }, 44, 22);
        tabs.Children.Add(playerTab.At(0, 0));
        tabs.Children.Add(lyricsTab.At(0, 1));
        tabs.Children.Add(queueTab.At(0, 2));
        panel.Children.Add(tabs.At(0));
        lyrics = new LyricsPanel(p);
        queue = new QueuePanel(p);
        panelHost.Children.Add(lyrics);
        panelHost.Children.Add(queue);
        panel.Children.Add(panelHost.At(1));
        body.Children.Add(panel.At(0, 1));

        layout.Children.Add(body.At(1));
        Children.Add(layout);

        App.Services.Player.Changed += Show;
        Show(App.Services.Player.State);
        ShowTab(Tab.UpNext);
        window.LayoutChanged += Fit;
        Fit();
    }

    private void Front(Tab which)
    {
        panelInFront = true;
        ShowTab(which);
        Fit();
    }

    /** Wide: the player and the panel side by side. Narrow: one at a time. */
    private void Fit()
    {
        var narrow = window.Narrow;
        narrowButtons.Visibility = playerTab.Visibility = narrow ? Visibility.Visible : Visibility.Collapsed;
        var showLeft = !narrow || !panelInFront;
        var showPanel = !narrow || panelInFront;
        left.Visibility = showLeft ? Visibility.Visible : Visibility.Collapsed;
        panel.Visibility = showPanel ? Visibility.Visible : Visibility.Collapsed;
        body.ColumnDefinitions[0].Width = showLeft ? new GridLength(1, GridUnitType.Star) : new GridLength(0);
        body.ColumnDefinitions[1].Width = showPanel ? new GridLength(1, GridUnitType.Star) : new GridLength(0);
        body.ColumnSpacing = showLeft && showPanel ? 32 : 0;
    }

    private void PaintShade()
    {
        var bg = p.Background.Color;
        shade.Fill = new LinearGradientBrush
        {
            StartPoint = new(0, 0), EndPoint = new(0, 1),
            GradientStops =
            {
                new GradientStop { Color = Windows.UI.Color.FromArgb(115, bg.R, bg.G, bg.B), Offset = 0 },
                new GradientStop { Color = Windows.UI.Color.FromArgb(209, bg.R, bg.G, bg.B), Offset = 1 },
            },
        };
    }

    private static Tap Glass(string glyph, string label, Action click) =>
        Ui.IconButton(glyph, label, Theme.White, click, 48, 24, new SolidColorBrush(Windows.UI.Color.FromArgb(77, 0, 0, 0)));

    private Tap TabButton(string label, CornerRadius shape, Action click)
    {
        var tap = new Tap { CornerRadius = shape };
        tap.Children.Add(Ui.Center(Ui.Text(label, 14, p.OnBackground, FontWeights.SemiBold)));
        tap.Click += click;
        return tap.Pressable(0.95f);
    }

    /** Shuffle / repeat: filled with firmer corners when on, an outline when off. */
    private Tap Mode(string glyph, Action click)
    {
        var tap = new Tap { CornerRadius = new CornerRadius(24), BorderThickness = new Thickness(1) };
        var row = Ui.Row(8, Ui.Icon(glyph, 22, p.OnBackground), Ui.Text("", 15, p.OnBackground, FontWeights.Bold));
        row.HorizontalAlignment = HorizontalAlignment.Center;
        row.VerticalAlignment = VerticalAlignment.Center;
        tap.Children.Add(row);
        tap.Click += click;
        return tap.Pressable(0.95f);
    }

    private void SetMode(Tap mode, bool on, string glyph, string label)
    {
        var row = (StackPanel)mode.Children[0];
        var icon = (FontIcon)row.Children[0];
        var text = (TextBlock)row.Children[1];
        icon.Glyph = glyph;
        text.Text = label;
        icon.Foreground = text.Foreground = on ? p.OnPrimary : p.OnBackground;
        mode.Background = on ? p.Primary : Theme.Clear;
        mode.BorderBrush = on ? Theme.Clear : p.Track;
        mode.CornerRadius = new CornerRadius(on ? 18 : 24);
    }

    private void ShowTab(Tab which)
    {
        tab = which;
        lyrics.Visibility = which == Tab.Lyrics ? Visibility.Visible : Visibility.Collapsed;
        queue.Visibility = which == Tab.UpNext ? Visibility.Visible : Visibility.Collapsed;
        foreach (var (t, on) in new[] { (lyricsTab, which == Tab.Lyrics), (queueTab, which == Tab.UpNext) })
        {
            t.Background = on ? p.Primary : p.SurfaceContainerHighest;
            ((TextBlock)((FrameworkElement)t.Children[0])).Foreground = on ? p.OnPrimary : p.OnBackground;
        }
        Motion.Fade(which == Tab.Lyrics ? lyrics : queue, 1, 200);
        if (which == Tab.Lyrics) lyrics.Refresh();
    }

    private async void Show(PlayerState s)
    {
        if (s.Current is not { } song) return;
        from.Text = s.Context?.Title ?? "";
        if (song.VideoId != shownId)
        {
            var forward = shownId is not null;
            shownId = song.VideoId;
            title.Text = song.Title;
            artist.Text = song.Artist;
            if (forward && Open) Motion.SlideIn(title.Parent as UIElement ?? title, 24, 0, 280);
        }
        likeIcon.FontFamily = s.Liked ? Theme.Icons : Theme.IconsOutlined;
        likeIcon.Foreground = s.Liked ? p.OnPrimary : p.OnPrimaryContainer;
        likeButton.Background = s.Liked ? p.Primary : p.PrimaryContainer;
        likeCorner.To(s.Liked ? 26 : 18);
        SetMode(shuffleMode, s.Shuffle, Glyph.Shuffle, s.Shuffle ? "Shuffle on" : "Shuffle off");
        SetMode(repeatMode, s.Repeat != Repeat.Off, s.Repeat == Repeat.One ? Glyph.RepeatOne : Glyph.Repeat,
            s.Repeat switch { Repeat.All => "Repeat all", Repeat.One => "Repeat one", _ => "Repeat off" });
        if (song.Art != shownArt)
        {
            shownArt = song.Art;
            backdrop.Url = song.Art;
            cover.Background = (Brush?)Images.Brush(song.Art, 720) ?? p.SurfaceContainerHigh;
            Motion.Fade(cover, 1, 320);
            var scheme = await Images.SchemeAsync(song.Art, dark: true);
            if (shownArt == song.Art) p.Set(scheme ?? Scheme.DarkBlue);
        }
    }

    public void Show(Tab which)
    {
        if (App.Services.Player.State.Current is null) return;
        ShowTab(which);
        if (Open) return;
        Open = true;
        Visibility = Visibility.Visible;
        if (Docked)
        {
            Width = Math.Max(480, window.Content.XamlRoot.Size.Width / 2);
            HorizontalAlignment = HorizontalAlignment.Right;
            CornerRadius = new CornerRadius(28, 0, 0, 28);
            Motion.SlideIn(this, (float)Width, 0, 380);
        }
        else
        {
            Width = double.NaN;
            HorizontalAlignment = HorizontalAlignment.Stretch;
            CornerRadius = new CornerRadius(0);
            Motion.SlideIn(this, 0, 120, 380);
        }
    }

    public async void Close()
    {
        if (!Open) return;
        Open = false;
        await (Docked ? Motion.SlideOut(this, (float)ActualWidth, 0, 260) : Motion.SlideOut(this, 0, 120, 240));
        if (!Open) Visibility = Visibility.Collapsed;
    }
}

/**
 * Lyrics: big lines; the current one bright with its words lighting up as they're sung (real
 * per-word timing when the source has it), the rest dimmed. Scrolls itself to the singing.
 */
public sealed class LyricsPanel : Grid
{
    private readonly Palette p;
    private readonly ScrollViewer scroller = new() { VerticalScrollBarVisibility = ScrollBarVisibility.Hidden };
    private readonly StackPanel lines = new() { Spacing = 18, Padding = new Thickness(28, 12, 28, 200) };
    private readonly TextBlock status;
    private readonly Microsoft.UI.Dispatching.DispatcherQueueTimer timer;
    private Lyrics? current;
    private string? loadedFor;
    private int active = -1;
    private readonly List<(TextBlock block, List<(Run run, LyricWord word)> words)> rendered = [];

    public LyricsPanel(Palette p)
    {
        this.p = p;
        scroller.Content = lines;
        Children.Add(scroller);
        status = Ui.Text("", 18, Theme.White, FontWeights.Bold, 0);
        status.HorizontalAlignment = HorizontalAlignment.Center;
        status.VerticalAlignment = VerticalAlignment.Center;
        status.TextAlignment = TextAlignment.Center;
        Children.Add(status);
        timer = Microsoft.UI.Dispatching.DispatcherQueue.GetForCurrentThread().CreateTimer();
        timer.Interval = TimeSpan.FromMilliseconds(80);
        timer.Tick += (_, _) => Tick();
        App.Services.Player.Changed += _ => { if (Visibility == Visibility.Visible) Refresh(); };
        IsVisibleChanged();
    }

    private void IsVisibleChanged() =>
        RegisterPropertyChangedCallback(VisibilityProperty, (_, _) => { if (Visibility == Visibility.Visible) { Refresh(); timer.Start(); } else timer.Stop(); });

    public async void Refresh()
    {
        if (App.Services.Player.State.Current is not { } song || song.VideoId == loadedFor) return;
        loadedFor = song.VideoId;
        lines.Children.Clear();
        rendered.Clear();
        active = -1;
        status.Text = "";
        var found = await App.Services.Lyrics.LoadAsync(song.VideoId, song.Title, song.Artist, song.DurationMs ?? App.Services.Player.State.DurationMs, song.Album);
        if (loadedFor != song.VideoId) return;
        current = found;
        if (found is null)
        {
            status.Text = "No lyrics for this one\nInstrumental, or nobody has added them yet.";
            return;
        }
        foreach (var line in found.Lines)
        {
            var block = new TextBlock { FontSize = 32, FontWeight = FontWeights.ExtraBold, TextWrapping = TextWrapping.WrapWholeWords, CharacterSpacing = -15, FontFamily = Theme.Display, LineHeight = 40 };
            var words = new List<(Run, LyricWord)>();
            foreach (var word in line.Words)
            {
                var run = new Run { Text = word.Text + (word.SpaceAfter ? " " : ""), Foreground = Dim };
                block.Inlines.Add(run);
                words.Add((run, word));
            }
            if (line.Background is { Count: > 0 } backing)
            {
                block.Inlines.Add(new LineBreak());
                foreach (var word in backing) block.Inlines.Add(new Run { Text = word.Text + " ", FontSize = 20, FontWeight = FontWeights.Bold, Foreground = Dim });
            }
            if (!found.Synced) foreach (var (run, _) in words) run.Foreground = Theme.White;
            var tap = new Tap { Background = Theme.Clear };
            tap.Children.Add(block);
            if (found.Synced) tap.Click += () => App.Services.Player.Seek(line.StartMs);
            lines.Children.Add(tap);
            rendered.Add((block, words));
        }
        lines.Children.Add(Ui.Text("Lyrics from " + found.Source, 13, Dim).Margin(0, 20, 0, 0));
    }

    private static readonly SolidColorBrush Dim = new(Windows.UI.Color.FromArgb(90, 255, 255, 255));

    private void Tick()
    {
        if (current is not { Synced: true } lyrics) return;
        var at = App.Services.Player.Position;
        var index = -1;
        for (var i = 0; i < lyrics.Lines.Count; i++) if (lyrics.Lines[i].StartMs <= at + 150) index = i;
        if (index != active)
        {
            if (active >= 0 && active < rendered.Count) foreach (var (run, _) in rendered[active].words) run.Foreground = Dim;
            active = index;
            if (index >= 0 && index < rendered.Count)
            {
                var target = rendered[index].block;
                var point = target.TransformToVisual(lines).TransformPoint(new Windows.Foundation.Point(0, 0));
                scroller.ChangeView(null, Math.Max(0, point.Y - scroller.ActualHeight * 0.3), null, false);
            }
        }
        if (active >= 0 && active < rendered.Count)
            foreach (var (run, word) in rendered[active].words)
                run.Foreground = word.StartMs <= at + 80 ? Theme.White : Dim;
    }
}

/** Up next: the songs that play next, in play order. Click to jump, right-click for more, × to remove. */
public sealed class QueuePanel : Grid
{
    private readonly Palette p;
    private readonly StackPanel list = new() { Spacing = 2, Padding = new Thickness(12, 0, 12, 24) };
    private readonly TextBlock heading;
    private string? shownKey;

    public QueuePanel(Palette p)
    {
        this.p = p;
        var layout = Ui.Grid("Auto,*");
        heading = Ui.Text("Coming up", 22, p.OnBackground, FontWeights.ExtraBold);
        layout.Children.Add(heading.Margin(24, 8, 24, 8).At(0));
        layout.Children.Add(new ScrollViewer { Content = list, VerticalScrollBarVisibility = ScrollBarVisibility.Auto }.At(1));
        Children.Add(layout);
        App.Services.Player.Changed += Show;
        Show(App.Services.Player.State);
    }

    private void Show(PlayerState s)
    {
        var key = string.Join(",", s.Upcoming.Take(60)) + "|" + s.Index + "|" + s.Queue.Count;
        if (key == shownKey) return;
        shownKey = key;
        list.Children.Clear();
        foreach (var i in s.Upcoming.Take(60))
        {
            if (i >= s.Queue.Count) continue;
            var song = s.Queue[i];
            var index = i;
            var item = new Song(song.VideoId, song.Title, [new ArtistRef(song.Artist, null)], song.Album is null ? null : new AlbumRef(song.Album, null), song.Art, song.DurationMs);
            var remove = Ui.IconButton(Glyph.Close, "Remove from queue", p.OnSurfaceVariant, () => App.Services.Player.RemoveAt(index), 36, 18);
            list.Children.Add(Items.Row(item, p, _ => App.Services.Player.SkipTo(index), remove));
        }
        if (list.Children.Count == 0) list.Children.Add(Ui.Text("Nothing after this song", 15, p.OnSurfaceVariant).Margin(14, 12, 0, 0));
    }
}
