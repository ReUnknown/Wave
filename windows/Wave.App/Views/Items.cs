using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Wave.Music;
using Wave.Playback;

namespace Wave.App.Views;

/** What tapping and right-clicking things does, everywhere: open pages, play, and the item menu. */
public static class Actions
{
    public static void Open(Item item)
    {
        switch (item)
        {
            case Song song: _ = PlayAsync(song); break;
            case Album album: App.Window.Push(new CollectionPage(album: album)); break;
            case Playlist playlist: App.Window.Push(new CollectionPage(playlist: playlist)); break;
            case Artist artist: App.Window.Push(new ArtistPage(artist)); break;
        }
    }

    /** Plays a song with its radio after it (or an album/playlist/artist from the start). */
    public static async Task PlayAsync(Item item, bool shuffle = false)
    {
        try
        {
            var request = await App.Services.Queues.ForItemAsync(item, shuffle);
            App.Services.Player.Play(request);
        }
        catch (Exception e)
        {
            Notices.Show("Couldn't play that");
            Log.Write("play", e);
        }
    }

    /** An artist's shuffle or radio (their own playlists, when the page already has them). */
    public static async Task PlayAsyncFor(Artist artist, bool shuffle, string? shuffleId, string? radioId)
    {
        try
        {
            App.Services.Player.Play(await App.Services.Queues.ForItemAsync(artist, shuffle, shuffleId, radioId));
        }
        catch (Exception e)
        {
            Notices.Show("Couldn't play that");
            Log.Write("play", e);
        }
    }

    public static void PlayList(IReadOnlyList<Song> songs, int start, PlaybackContext context, bool shuffle = false) =>
        App.Services.Player.Play(QueueBuilder.ForList(songs, start, context, shuffle));

    public static void PlayNext(Song s)
    {
        App.Services.Player.PlayNext([NowPlaying.From(s)]);
        Notices.Show("Playing next");
    }

    public static void AddToQueue(Song s)
    {
        App.Services.Player.AddToQueue([NowPlaying.From(s)]);
        Notices.Show("Added to queue");
    }

    public static string? Link(Item item) => item switch
    {
        Song s => "https://music.youtube.com/watch?v=" + s.VideoId,
        Album a => a.PlaylistId is null ? "https://music.youtube.com/browse/" + a.BrowseId : "https://music.youtube.com/playlist?list=" + a.PlaylistId,
        Playlist p => "https://music.youtube.com/playlist?list=" + p.Id,
        Artist a => "https://music.youtube.com/channel/" + a.BrowseId,
        _ => null,
    };

    public static void Share(Item item)
    {
        if (Link(item) is not { } url) return;
        var data = new Windows.ApplicationModel.DataTransfer.DataPackage();
        data.SetText(url);
        Windows.ApplicationModel.DataTransfer.Clipboard.SetContent(data);
        Notices.Show("Link copied");
    }
}

/** The cards and rows Wave lists music with. */
public static class Items
{
    /** A 148 px card: cover (round for artists), title and subtitle. Click opens; right-click for more. */
    public static FrameworkElement Card(Item item, Palette p, Action<Item>? onClick = null)
    {
        var round = item is Artist;
        var tap = new Tap { Width = 148 };
        var column = new StackPanel { Spacing = 0 };
        column.Children.Add(Ui.Art(item.Thumbnail, 148, round ? 74 : 24, p.SurfaceContainerHigh));
        column.Children.Add(Ui.TitleSmall(item.Title, p.OnBackground).Margin(0, 8, 0, 0));
        if (item.Subtitle.Length > 0) column.Children.Add(Ui.BodySmall(item.Subtitle, p.OnSurfaceVariant));
        tap.Children.Add(column);
        tap.Click += () => (onClick ?? Actions.Open)(item);
        ItemMenu.Attach(tap, item);
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(tap, item.Title);
        return tap.Pressable(0.95f);
    }

    /** A list row: 52 px cover, title, subtitle, and whatever goes on the end. */
    public static FrameworkElement Row(Item item, Palette p, Action<Item>? onClick = null, UIElement? trailing = null, ItemMenu.Source? from = null)
    {
        var tap = new Tap { CornerRadius = new CornerRadius(20), Padding = new Thickness(10, 8, 10, 8) };
        var grid = Ui.Grid(columns: "Auto,*,Auto");
        grid.ColumnSpacing = 14;
        grid.Children.Add(Ui.Art(item.Thumbnail, 52, item is Artist ? 26 : 14, p.SurfaceContainerHigh).At(0, 0));
        var text = Ui.Column(0, Ui.TitleSmall(item.Title, p.OnBackground), Ui.BodySmall(item.Subtitle, p.OnSurfaceVariant));
        text.VerticalAlignment = VerticalAlignment.Center;
        grid.Children.Add(text.At(0, 1));
        if (trailing is FrameworkElement t) grid.Children.Add(t.At(0, 2));
        tap.Children.Add(grid);
        tap.Click += () => (onClick ?? Actions.Open)(item);
        tap.PointerEntered += (_, _) => tap.Background = p.Fainter;
        tap.PointerExited += (_, _) => tap.Background = Theme.Clear;
        ItemMenu.Attach(tap, item, from);
        return tap.Pressable(0.97f);
    }

    /** The big expressive-shape tile that plays a whole section; its shape turns while music plays. */
    public static FrameworkElement LeadTile(Shapes.Kind kind, Brush fill, Brush ink, bool shuffle, string caption, string detail, Palette p, Action onClick)
    {
        var tap = new Tap { Width = 148 };
        var column = new StackPanel();
        var box = new Grid { Width = 148, Height = 148 };
        var shape = Shapes.Make(kind, 148, fill);
        box.Children.Add(shape);
        box.Children.Add(Ui.Center(Ui.Icon(shuffle ? Glyph.Shuffle : Glyph.Play, 56, ink)));
        column.Children.Add(box);
        column.Children.Add(Ui.TitleSmall(caption, p.OnBackground).Margin(0, 8, 0, 0));
        column.Children.Add(Ui.BodySmall(detail, p.OnSurfaceVariant));
        tap.Children.Add(column);
        tap.Click += onClick;
        // Turning slowly while something plays (and "Move with the music" is on); still otherwise.
        void Update(PlayerState s) => Motion.Spin(shape, s.IsPlaying && Wave.Data.SettingsStore.Current.MoveWithMusic && App.Window.Active);
        shape.Loaded += (_, _) => { App.Services.Player.Changed += Update; App.Window.ActiveChanged += OnActive; Update(App.Services.Player.State); };
        shape.Unloaded += (_, _) => { App.Services.Player.Changed -= Update; App.Window.ActiveChanged -= OnActive; };
        void OnActive() => Update(App.Services.Player.State);
        return tap.Pressable(0.95f);
    }

    /** A section's title (with an optional small label above it). */
    public static FrameworkElement Header(string? label, string title, Palette p)
    {
        var column = new StackPanel { Margin = new Thickness(24, 28, 24, 12) };
        if (!string.IsNullOrEmpty(label)) column.Children.Add(Ui.LabelSmall(label, p.OnSurfaceVariant));
        column.Children.Add(Ui.HeadlineSmall(title, p.OnBackground));
        return column;
    }

    /** Cards in rows that wrap (two rows at most on wide windows), or one sideways row. */
    public static FrameworkElement CardRows(IEnumerable<FrameworkElement> cards, int maxRows = 2)
    {
        var all = cards.ToList();
        var host = new Grid { Margin = new Thickness(24, 0, 24, 0) };
        var wrap = new WrapPanel(20, 20, maxRows);
        foreach (var c in all) wrap.Children.Add(c);
        host.Children.Add(wrap);
        return host;
    }
}

/** Lays children left to right, wrapping into at most [maxRows] rows (the rest are hidden). */
public sealed class WrapPanel(double hSpacing, double vSpacing, int maxRows) : Panel
{
    protected override Windows.Foundation.Size MeasureOverride(Windows.Foundation.Size available)
    {
        double x = 0, y = 0, rowHeight = 0;
        var rows = 1;
        foreach (var child in Children)
        {
            child.Measure(new Windows.Foundation.Size(double.PositiveInfinity, double.PositiveInfinity));
            var s = child.DesiredSize;
            if (x > 0 && x + s.Width > available.Width)
            {
                if (rows == maxRows) break;
                rows++;
                x = 0;
                y += rowHeight + vSpacing;
                rowHeight = 0;
            }
            x += s.Width + hSpacing;
            rowHeight = Math.Max(rowHeight, s.Height);
        }
        return new Windows.Foundation.Size(double.IsInfinity(available.Width) ? x : available.Width, y + rowHeight);
    }

    protected override Windows.Foundation.Size ArrangeOverride(Windows.Foundation.Size final)
    {
        double x = 0, y = 0, rowHeight = 0;
        var rows = 1;
        var hidden = false;
        foreach (var child in Children)
        {
            var s = child.DesiredSize;
            if (!hidden && x > 0 && x + s.Width > final.Width)
            {
                if (rows == maxRows) hidden = true;
                else
                {
                    rows++;
                    x = 0;
                    y += rowHeight + vSpacing;
                    rowHeight = 0;
                }
            }
            if (hidden)
            {
                child.Arrange(new Windows.Foundation.Rect(0, 0, 0, 0));
                continue;
            }
            child.Arrange(new Windows.Foundation.Rect(x, y, s.Width, s.Height));
            x += s.Width + hSpacing;
            rowHeight = Math.Max(rowHeight, s.Height);
        }
        return final;
    }
}

/** Short messages at the bottom of the window ("Link copied"). */
public static class Notices
{
    public static event Action<string>? Shown;
    public static void Show(string message) => Shown?.Invoke(message);
}
