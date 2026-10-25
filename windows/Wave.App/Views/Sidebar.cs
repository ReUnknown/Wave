using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Wave.Data;
using Wave.Music;

namespace Wave.App.Views;

/**
 * The sidebar: Wave, Home, your albums & artists, history and your playlists. It sits on the
 * window's background (no panel of its own) and takes the colors of the page you're on.
 */
public sealed class Sidebar : Grid
{
    private readonly MainWindow window;
    private readonly Palette p;
    private readonly StackPanel nav = new() { Spacing = 4 };
    private readonly StackPanel playlists = new() { Spacing = 2 };
    private readonly Dictionary<string, Tap> navRows = new();
    private IReadOnlyList<Playlist> lists = [];

    public Sidebar(MainWindow window)
    {
        this.window = window;
        p = window.Side;
        Width = 264;
        Padding = new Thickness(14, MainWindow.TitleBarHeight, 14, 0);
        RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        RowDefinitions.Add(new RowDefinition());

        var top = new StackPanel { Spacing = 4 };
        var brand = Ui.Row(10);
        brand.Margin = new Thickness(10, 0, 0, 16);
        var mark = new Grid { Width = 36, Height = 36 };
        mark.Children.Add(new Border { CornerRadius = new CornerRadius(18), Background = p.Primary });
        mark.Children.Add(Ui.Center(Shapes.WaveMark(22, p.OnPrimary)));
        brand.Children.Add(mark);
        var name = Ui.Text("Wave", 22, p.OnBackground, FontWeights.ExtraBold);
        name.VerticalAlignment = VerticalAlignment.Center;
        brand.Children.Add(name);
        top.Children.Add(brand);
        top.Children.Add(nav);
        Children.Add(top.At(0));

        var scroller = new ScrollViewer { Content = playlists, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Padding = new Thickness(0, 0, 0, 16) };
        Children.Add(scroller.At(1));
    }

    private Tap NavRow(string key, string glyph, string label, Action onClick)
    {
        var tap = new Tap { Height = 46, CornerRadius = new CornerRadius(23), Padding = new Thickness(14, 0, 14, 0) };
        var row = Ui.Row(14, Ui.Icon(glyph, 22, p.OnBackground), Ui.Text(label, 14, p.OnBackground, FontWeights.SemiBold));
        row.VerticalAlignment = VerticalAlignment.Center;
        foreach (var c in row.Children) ((FrameworkElement)c).VerticalAlignment = VerticalAlignment.Center;
        tap.Children.Add(row);
        tap.Click += onClick;
        navRows[key] = tap;
        return tap.Pressable(0.97f);
    }

    /** Rebuilds for the current account (guests get a sign-in invitation instead of a library). */
    public void Refresh()
    {
        nav.Children.Clear();
        navRows.Clear();
        playlists.Children.Clear();
        nav.Children.Add(NavRow("home", Glyph.Home, "Home", window.GoHome));
        if (SessionStore.Current?.Guest != false)
        {
            var invite = new StackPanel { Spacing = 10, Padding = new Thickness(16), Margin = new Thickness(0, 18, 0, 0) };
            var card = new Border { CornerRadius = new CornerRadius(20), Background = p.Fainter, Child = invite };
            invite.Children.Add(Ui.Text("Your playlists, albums and history show up here when you sign in.", 13, p.OnBackground, lines: 0));
            var button = Ui.Button(null, "Sign in with Google", p.Primary, p.OnPrimary, window.SignIn, new CornerRadius(20), 40, 14);
            button.HorizontalAlignment = HorizontalAlignment.Left;
            invite.Children.Add(button);
            nav.Children.Add(card);
            Highlight(window.Top);
            return;
        }
        nav.Children.Add(NavRow("saved", Glyph.Album, "Albums & artists", () => Open(new LibraryPage(LibraryPage.Kind.Saved))));
        nav.Children.Add(NavRow("history", Glyph.History, "History", () => Open(new LibraryPage(LibraryPage.Kind.History))));

        var header = Ui.Grid(columns: "*,Auto");
        header.Margin = new Thickness(14, 18, 0, 4);
        var title = Ui.Text("PLAYLISTS", 12, p.OnBackground, FontWeights.Bold, 1, 83);
        title.VerticalAlignment = VerticalAlignment.Center;
        title.Opacity = 0.75;
        header.Children.Add(title.At(0, 0));
        header.Children.Add(Ui.IconButton(Glyph.Add, "New playlist", p.OnBackground, () => _ = Dialogs.CreatePlaylistAsync(), 36, 22).At(0, 1));
        nav.Children.Add(header);
        _ = LoadPlaylistsAsync();
        Highlight(window.Top);
    }

    private void Open(WavePage page)
    {
        if (window.Top.GetType() == page.GetType() && window.Top is LibraryPage a && page is LibraryPage b && a.Showing == b.Showing) return;
        window.Push(page);
    }

    public async Task LoadPlaylistsAsync()
    {
        try { lists = await App.Services.YouTube.LibraryPlaylistsAsync(); }
        catch (Exception) { return; }
        playlists.Children.Clear();
        foreach (var playlist in lists)
        {
            var tap = new Tap { CornerRadius = new CornerRadius(14), Padding = new Thickness(8, 6, 8, 6) };
            var grid = Ui.Grid(columns: "Auto,*");
            grid.ColumnSpacing = 12;
            grid.Children.Add(Ui.Art(playlist.Thumbnail, 42, 10, p.SurfaceContainerHigh).At(0, 0));
            var text = Ui.Column(0, Ui.Text(playlist.Title, 14, p.OnBackground, FontWeights.SemiBold), Ui.Text(playlist.SongCount ?? playlist.Author, 12, p.OnSurfaceVariant));
            text.VerticalAlignment = VerticalAlignment.Center;
            grid.Children.Add(text.At(0, 1));
            tap.Children.Add(grid);
            tap.Click += () => window.Push(new CollectionPage(playlist: playlist));
            tap.PointerEntered += (_, _) => tap.Background = p.Fainter;
            tap.PointerExited += (_, _) => tap.Background = Theme.Clear;
            ItemMenu.Attach(tap, playlist);
            playlists.Children.Add(tap.Pressable(0.97f));
        }
    }

    public IReadOnlyList<Playlist> Playlists => lists;

    /** The row for the page you're on is filled. */
    public void Highlight(WavePage page)
    {
        var key = page switch
        {
            HomePage => "home",
            LibraryPage { Showing: LibraryPage.Kind.Saved } => "saved",
            LibraryPage { Showing: LibraryPage.Kind.History } => "history",
            _ => null,
        };
        foreach (var (k, tap) in navRows) tap.Background = k == key ? p.SecondaryContainer : Theme.Clear;
    }
}
