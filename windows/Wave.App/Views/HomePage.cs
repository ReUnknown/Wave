using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Wave.Data;
using Wave.Music;
using Wave.Playback;
using Windows.UI;

namespace Wave.App.Views;

/**
 * Home: the search bar and your account up top, a greeting, the big banner (what's playing, or
 * today's #1 pick) with "jump back in" tiles beside it on wide windows, then your recent spins
 * and YouTube Music's shelves as rows of cards, each led by a big shape that plays it all.
 */
public sealed class HomePage : WavePage
{
    private readonly MainWindow window;
    private readonly Palette p = Theme.App;
    private readonly StackPanel content = new() { Padding = new Thickness(0, 0, 0, 40) };
    private readonly ScrollViewer scroller;
    private readonly Grid heroHost = new() { Height = 560 };
    private readonly ProgressRing loading = new() { Width = 64, Height = 64, IsActive = true };
    private HomeFeed? feed;
    private int attempt;

    public override Palette Colors => p;
    public override string? Art => (feed?.Hero?.Thumbnail).AtSize(Music.Art.Full);

    public HomePage(MainWindow window)
    {
        this.window = window;
        loading.Foreground = p.Primary;
        scroller = new ScrollViewer { Content = content, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, HorizontalScrollMode = ScrollMode.Disabled };
        // The space up top for the search bar, which stays put while the page scrolls under it.
        content.Children.Add(new Border { Height = MainWindow.TitleBarHeight + 64 });
        content.Children.Add(Greeting());
        content.Children.Add(heroHost);
        Children.Add(scroller);
        Children.Add(HeaderShade());
        Children.Add(Header());
        Children.Add(Ui.Center(loading));
        App.Services.Player.Changed += _ => { if (feed is not null) DrawHero(); };
        RecentPlays.Changed += () => DispatcherQueue.TryEnqueue(() => { if (feed is not null) Draw(); });
        _ = LoadAsync(false);
    }

    private FrameworkElement HeaderShade()
    {
        var shade = new Rectangle { Height = MainWindow.TitleBarHeight + 80, VerticalAlignment = VerticalAlignment.Top, IsHitTestVisible = false };
        void Paint()
        {
            var bg = p.Background.Color;
            shade.Fill = new LinearGradientBrush
            {
                StartPoint = new(0, 0), EndPoint = new(0, 1),
                GradientStops =
                {
                    new GradientStop { Color = Color.FromArgb(150, bg.R, bg.G, bg.B), Offset = 0 },
                    new GradientStop { Color = Color.FromArgb(90, bg.R, bg.G, bg.B), Offset = 0.6 },
                    new GradientStop { Color = Color.FromArgb(0, bg.R, bg.G, bg.B), Offset = 1 },
                },
            };
        }
        p.Changed += _ => Paint();
        Paint();
        shade.Opacity = 0;
        scroller.ViewChanged += (_, _) => shade.Opacity = Math.Clamp(scroller.VerticalOffset / 120, 0, 1);
        return shade;
    }

    /** The search bar (it opens search) with your account at its end. */
    private FrameworkElement Header()
    {
        var bar = new Tap
        {
            Height = 56,
            MaxWidth = 760,
            CornerRadius = new CornerRadius(28),
            Background = p.Faint,
            BorderBrush = p.Track,
            BorderThickness = new Thickness(1),
            Padding = new Thickness(16, 0, 8, 0),
            Margin = new Thickness(16, MainWindow.TitleBarHeight + 4, 16, 0),
            VerticalAlignment = VerticalAlignment.Top,
            HorizontalAlignment = HorizontalAlignment.Stretch,
        };
        var row = Ui.Grid(columns: "Auto,*,Auto");
        row.ColumnSpacing = 12;
        row.Children.Add(Ui.Center(Ui.Icon(Glyph.Search, 22, p.OnBackground)).At(0, 0));
        var hint = Ui.Text("Search songs, artists, albums", 16, p.OnBackground);
        hint.Opacity = 0.85;
        hint.VerticalAlignment = VerticalAlignment.Center;
        row.Children.Add(hint.At(0, 1));
        row.Children.Add(Avatar().At(0, 2));
        bar.Children.Add(row);
        bar.Click += window.OpenSearch;
        var host = new Grid { VerticalAlignment = VerticalAlignment.Top };
        host.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star), MaxWidth = 792 });
        host.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(0, GridUnitType.Auto) });
        host.Children.Add(bar.Pressable(0.99f));
        return host;
    }

    private FrameworkElement Avatar()
    {
        var session = SessionStore.Current;
        var tap = new Tap { Width = 40, Height = 40, CornerRadius = new CornerRadius(20), VerticalAlignment = VerticalAlignment.Center };
        if (session?.Photo is { } photo) tap.Children.Add(Ui.Art(photo, 40, 20));
        else tap.Children.Add(Ui.Center(Ui.Icon(Glyph.Account, 40, p.OnBackground)));
        tap.Click += () => AccountMenu.Show(tap, window);
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(tap, "Your account");
        return tap.Pressable(0.9f);
    }

    private FrameworkElement Greeting()
    {
        var hour = DateTime.Now.Hour;
        var greeting = hour is >= 5 and <= 11 ? "Good morning" : hour is >= 12 and <= 16 ? "Good afternoon" : "Good evening";
        var session = SessionStore.Current;
        var column = new StackPanel { Margin = new Thickness(28, 14, 0, 0) };
        var hello = Ui.Text(session?.Guest == false ? greeting + "," : greeting, 22, p.OnBackground, FontWeights.Medium);
        hello.Opacity = 0.88;
        column.Children.Add(hello);
        if (session?.Guest == false) column.Children.Add(Ui.Text(session.Name.Split(' ')[0], 22, p.OnBackground, FontWeights.ExtraBold));
        return column;
    }

    private async Task LoadAsync(bool refresh)
    {
        var mine = ++attempt;
        loading.Visibility = Visibility.Visible;
        try
        {
            await foreach (var next in App.Services.HomeAsync(refresh))
            {
                if (mine != attempt) return;
                feed = next;
                loading.Visibility = Visibility.Collapsed;
                Draw();
                SongColors.HomeArt = feed.Hero?.Thumbnail;
            }
        }
        catch (Exception e)
        {
            Log.Write("home", e);
            if (mine != attempt) return;
            loading.Visibility = Visibility.Collapsed;
            var retry = Ui.Column(12, Ui.TitleMedium("Couldn't load your home", p.OnBackground),
                Ui.Button(null, "Try again", p.Primary, p.OnPrimary, () => _ = LoadAsync(true), new CornerRadius(20), 40));
            retry.HorizontalAlignment = HorizontalAlignment.Center;
            retry.Margin = new Thickness(0, 80, 0, 0);
            while (content.Children.Count > 3) content.Children.RemoveAt(3);
            content.Children.Add(retry);
        }
    }

    public override void Shown()
    {
        if (feed is null) _ = LoadAsync(false);
    }

    private void Draw()
    {
        if (feed is null) return;
        DrawHero();
        while (content.Children.Count > 3) content.Children.RemoveAt(3);
        var recent = RecentPlays.All.Select(n => (Item)new Song(n.VideoId, n.Title, [new ArtistRef(n.Artist, null)], n.Album is null ? null : new AlbumRef(n.Album, null), n.Art, n.DurationMs))
            .Concat(feed.Recent).DistinctBy(i => i.Key).Take(16).ToList();
        if (recent.Count > 0)
        {
            content.Children.Add(Items.Header("Your recent spins", "Recently played", p));
            content.Children.Add(Items.CardRows(recent.Select(i => Items.Card(i, p, item => _ = Actions.PlayAsync(item)))));
        }
        var shapes = new[] { Shapes.Kind.Cookie9, Shapes.Kind.Sunny, Shapes.Kind.Clover4, Shapes.Kind.Cookie12, Shapes.Kind.Flower, Shapes.Kind.SoftBurst };
        var tones = new[] { (p.Primary, p.OnPrimary), (p.PrimaryContainer, p.OnPrimaryContainer), (p.TertiaryContainer, p.OnTertiaryContainer) };
        for (var i = 0; i < feed.Sections.Count; i++)
        {
            var section = feed.Sections[i];
            content.Children.Add(Items.Header(section.Label, section.Title, p));
            var songsOnly = section.Items.All(x => x is Song);
            var (fill, ink) = tones[i % tones.Length];
            var lead = Items.LeadTile(shapes[i % shapes.Length], fill, ink, !songsOnly, songsOnly ? "Play all" : "Shuffle all",
                $"{section.Items.Count} {(songsOnly ? "songs" : "picks")}", p, () => PlaySection(section, !songsOnly));
            content.Children.Add(Items.CardRows(section.Items.Select(x => Items.Card(x, p)).Prepend(lead)));
        }
    }

    private static void PlaySection(Section section, bool shuffle)
    {
        var songs = section.Items.OfType<Song>().ToList();
        if (songs.Count == section.Items.Count) App.Services.Player.Play(QueueBuilder.ForSongs(section.Title, songs, shuffle));
        else if (section.Items.FirstOrDefault() is { } first) _ = Actions.PlayAsync(first, shuffle: true);
    }

    // ---------------------------------------------------------------- The banner

    private void DrawHero()
    {
        heroHost.Children.Clear();
        var state = App.Services.Player.State;
        var playing = state.Current;
        string label, title;
        string? detail, art;
        if (playing is not null)
        {
            label = "Now playing";
            title = playing.Title;
            detail = playing.Artist;
            art = playing.Art;
        }
        else if (feed?.Hero is { } hero)
        {
            label = "Your #1 pick today";
            title = hero.Title;
            detail = hero.Subtitle;
            art = hero.Thumbnail.AtSize(Music.Art.Full);
        }
        else return;

        var wide = ActualWidth >= 1180 && window.Nav.Playlists.Count > 0;
        var row = Ui.Grid(columns: wide ? "Auto,*,Auto" : "Auto,*");
        row.VerticalAlignment = VerticalAlignment.Bottom;
        row.Margin = new Thickness(32, 0, 32, 8);
        var coverSize = Math.Min(heroHost.Height - 200, wide ? 300 : 340);
        var cover = Ui.Art(art, coverSize, 36);
        cover.Shadow = new ThemeShadow();
        cover.Translation = new System.Numerics.Vector3(0, 0, 28);
        row.Children.Add(cover.At(0, 0));

        var text = new StackPanel { MaxWidth = 560, Margin = new Thickness(32, 0, 0, 0), VerticalAlignment = VerticalAlignment.Bottom };
        text.Children.Add(Ui.LabelSmall(label, p.Primary));
        text.Children.Add(Ui.DisplayMedium(title, p.OnBackground));
        if (!string.IsNullOrEmpty(detail)) text.Children.Add(Ui.BodyMedium(detail, p.OnSurfaceVariant));
        var buttons = Ui.Grid(columns: "*,150");
        buttons.ColumnSpacing = 4;
        buttons.Margin = new Thickness(0, 16, 0, 0);
        Tap primary, secondary;
        if (playing is not null)
        {
            primary = Ui.Button(state.IsPlaying ? Glyph.Pause : Glyph.Play, state.IsPlaying ? "Pause" : "Play", p.Primary, p.OnPrimary,
                App.Services.Player.TogglePlay, new CornerRadius(28, 10, 10, 28));
            secondary = Ui.Button(Glyph.Expand, "Player", p.Faint, p.OnBackground, () => window.OpenPlayer(), new CornerRadius(10, 28, 28, 10), 56, 14, 0.93f);
        }
        else
        {
            var hero = feed!.Hero!;
            primary = Ui.Button(Glyph.Play, "Play", p.Primary, p.OnPrimary, () => _ = Actions.PlayAsync(hero), new CornerRadius(28, 10, 10, 28));
            secondary = Ui.Button(Glyph.Shuffle, "Shuffle", p.Faint, p.OnBackground, () => _ = Actions.PlayAsync(hero, shuffle: true), new CornerRadius(10, 28, 28, 10), 56, 14, 0.93f);
        }
        buttons.Children.Add(primary.At(0, 0));
        buttons.Children.Add(secondary.At(0, 1));
        text.Children.Add(buttons);
        row.Children.Add(text.At(0, 1));
        if (wide) row.Children.Add(JumpBackIn().At(0, 2));
        heroHost.Children.Add(row);
    }

    /** Wide windows: tiles beside the banner to jump back into your playlists. */
    private FrameworkElement JumpBackIn()
    {
        var column = new StackPanel { Width = 420, Spacing = 10, Margin = new Thickness(32, 0, 0, 0), VerticalAlignment = VerticalAlignment.Bottom };
        var title = Ui.LabelSmall("Jump back in", p.OnBackground);
        title.Opacity = 0.8;
        column.Children.Add(title);
        var lists = window.Nav.Playlists.Take(6).ToList();
        for (var i = 0; i < lists.Count; i += 2)
        {
            var row = Ui.Grid(columns: "*,*");
            row.ColumnSpacing = 10;
            for (var j = 0; j < 2 && i + j < lists.Count; j++)
            {
                var item = lists[i + j];
                var tap = new Tap { Height = 64, CornerRadius = new CornerRadius(16), Background = p.Fainter };
                var inner = Ui.Grid(columns: "64,*");
                inner.ColumnSpacing = 12;
                inner.Children.Add(Ui.Art(item.Thumbnail, 64, 0).At(0, 0));
                var name = Ui.Text(item.Title, 14, p.OnBackground, FontWeights.Bold, 2);
                name.VerticalAlignment = VerticalAlignment.Center;
                name.Margin = new Thickness(0, 0, 10, 0);
                inner.Children.Add(name.At(0, 1));
                tap.Children.Add(inner);
                tap.Click += () => Actions.Open(item);
                row.Children.Add(tap.Pressable(0.96f).At(0, j));
            }
            column.Children.Add(row);
        }
        return column;
    }
}
