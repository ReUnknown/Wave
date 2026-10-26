using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Wave.Coloring;
using Wave.Data;
using Wave.Music;
using Wave.Playback;

namespace Wave.App.Views;

/** Pages with their own art: the page takes on its art's colors, and has a back button. */
public abstract class ArtPage : WavePage
{
    protected readonly Palette p = new(Theme.App.Scheme);
    protected readonly StackPanel content = new() { Padding = new Thickness(0, 0, 0, 48) };
    protected readonly ScrollViewer scroller;
    protected readonly LoadingIndicator loading;
    private string? art;

    public override Palette Colors => p;
    public override string? Art => art;

    protected ArtPage()
    {
        Background = p.Background;
        loading = new LoadingIndicator(80, p.Primary);
        scroller = new ScrollViewer { Content = content, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
        Children.Add(scroller);
        Children.Add(Ui.Center(loading));
        var back = Ui.IconButton(Glyph.Back, "Back", Theme.White, () => App.Window.Pop(), 48, 24, new SolidColorBrush(Windows.UI.Color.FromArgb(77, 0, 0, 0)));
        back.HorizontalAlignment = HorizontalAlignment.Left;
        back.VerticalAlignment = VerticalAlignment.Top;
        back.Margin = new Thickness(16, MainWindow.TitleBarHeight + 4, 0, 0);
        Children.Add(back);
    }

    /** Takes on the colors of [url] (when "Match song colors" is on). */
    protected async void UseArt(string? url)
    {
        art = url.AtSize(Music.Art.Full);
        if (!SettingsStore.Current.MatchSongColors || url is null) return;
        var scheme = await Images.SchemeAsync(url, Theme.SystemDark);
        if (scheme is not null) p.Set(scheme);
    }

    /** The big banner: the art, sharp at the top and blurring into the page's color below. */
    protected FrameworkElement Banner(string? url, double height, FrameworkElement overlay)
    {
        var grid = new Grid { Height = height };
        var sharp = new FadedArt(false, 0.55f, 1f) { Url = url.AtSize(Music.Art.Full) };
        var soft = new FadedArt(false, 0.55f, 1f, reverse: true);
        _ = soft.SetBlurredAsync(url, 6);
        grid.Children.Add(sharp);
        grid.Children.Add(soft);
        var shade = new Rectangle();
        void Paint()
        {
            var bg = p.Background.Color;
            shade.Fill = new LinearGradientBrush
            {
                StartPoint = new(0, 0), EndPoint = new(0, 1),
                GradientStops =
                {
                    new GradientStop { Color = Windows.UI.Color.FromArgb(0, bg.R, bg.G, bg.B), Offset = 0.45 },
                    new GradientStop { Color = Windows.UI.Color.FromArgb(140, bg.R, bg.G, bg.B), Offset = 0.75 },
                    new GradientStop { Color = bg, Offset = 1 },
                },
            };
        }
        p.Changed += _ => Paint();
        Paint();
        grid.Children.Add(shade);
        overlay.VerticalAlignment = VerticalAlignment.Bottom;
        grid.Children.Add(overlay);
        return grid;
    }

    protected void Failed(string what)
    {
        loading.Visibility = Visibility.Collapsed;
        var column = Ui.Column(12, Ui.TitleMedium("Couldn't load " + what, p.OnBackground));
        column.HorizontalAlignment = HorizontalAlignment.Center;
        column.Margin = new Thickness(0, 160, 0, 0);
        content.Children.Add(column);
    }
}

/** An album or playlist: cover, title, play and shuffle, then its songs as a table. */
public sealed class CollectionPage : ArtPage
{
    private readonly Album? album;
    private Playlist? playlist;
    private readonly List<Song> songs = [];
    private readonly StackPanel table = new() { Spacing = 0, Margin = new Thickness(16, 0, 16, 0) };
    private string? continuation;

    public string? PlaylistId => playlist?.Id;

    public CollectionPage(Album? album = null, Playlist? playlist = null)
    {
        this.album = album;
        this.playlist = playlist;
        UseArt(album?.Art ?? playlist?.Art);
        _ = LoadAsync();
    }

    private async Task LoadAsync()
    {
        try
        {
            string title, subtitle;
            string? art, description;
            PlaybackContext context;
            if (album is not null)
            {
                var page = await App.Services.YouTube.AlbumAsync(album.BrowseId);
                songs.AddRange(page.Songs);
                title = page.Album.Title;
                art = page.Album.Art;
                subtitle = string.Join(" • ", new[] { "Album", string.Join(", ", page.Album.Artists.Select(a => a.Name)), page.Album.Year ?? "", $"{songs.Count} songs" }.Where(s => s.Length > 0));
                description = page.Description;
                context = new PlaybackContext(page.Album.BrowseId, title, art, page.Album.PlaylistId, true);
                Header(title, subtitle, art, context, page.Album);
                Table(context, null);
                foreach (var section in page.More)
                {
                    content.Children.Add(Items.Header(null, section.Title, p));
                    content.Children.Add(Items.CardRows(section.Items.Select(i => Items.Card(i, p)), 1));
                }
            }
            else
            {
                var page = await App.Services.YouTube.PlaylistAsync(playlist!.Id);
                playlist = page.Playlist;
                songs.AddRange(page.Songs);
                continuation = page.Continuation;
                title = page.Playlist.Title;
                art = page.Playlist.Art ?? playlist.Art;
                subtitle = string.Join(" • ", new[] { page.Playlist.Author ?? "", page.Playlist.SongCount ?? "" }.Where(s => s.Length > 0));
                description = page.Description;
                context = new PlaybackContext(page.Playlist.Id, title, art, page.Playlist.Id, true);
                Header(title, subtitle, art, context, page.Playlist);
                Table(context, page.Playlist.Editable ? page.Playlist.Id : null);
                _ = MoreAsync(context, page.Playlist.Editable ? page.Playlist.Id : null);
            }
            UseArt(art);
            loading.Visibility = Visibility.Collapsed;
            _ = description;
        }
        catch (Exception e)
        {
            Log.Write("collection", e);
            Failed(album is not null ? "this album" : "this playlist");
        }
    }

    /** Long playlists: the rest of the songs, page by page. */
    private async Task MoreAsync(PlaybackContext context, string? editableId)
    {
        while (continuation is not null && Parent is not null)
        {
            try
            {
                var (more, next) = await App.Services.YouTube.PlaylistMoreAsync(continuation);
                var start = songs.Count;
                songs.AddRange(more);
                continuation = next;
                for (var i = start; i < songs.Count; i++) table.Children.Add(TableRow(i, context, editableId));
            }
            catch (Exception) { return; }
        }
    }

    private void Header(string title, string subtitle, string? art, PlaybackContext context, Item item)
    {
        var row = Ui.Grid(columns: "Auto,*");
        row.Margin = new Thickness(32, 0, 32, 16);
        var cover = Ui.Art(art, 240, 32);
        cover.Shadow = new ThemeShadow();
        cover.Translation = new System.Numerics.Vector3(0, 0, 24);
        row.Children.Add(cover.At(0, 0));
        var text = new StackPanel { Margin = new Thickness(28, 0, 0, 0), VerticalAlignment = VerticalAlignment.Bottom, MaxWidth = 720 };
        text.Children.Add(Ui.HeadlineLarge(title, p.OnBackground));
        text.Children.Add(Ui.BodyMedium(subtitle, p.OnSurfaceVariant).Margin(0, 4, 0, 0));
        var buttons = Ui.Row(4,
            Ui.Button(Glyph.Play, "Play", p.Primary, p.OnPrimary, () => { if (songs.Count > 0) Actions.PlayList(songs, 0, context); }, new CornerRadius(28, 10, 10, 28)),
            Ui.Button(Glyph.Shuffle, "Shuffle", p.Faint, p.OnBackground, () => { if (songs.Count > 0) Actions.PlayList(songs, 0, context, shuffle: true); }, new CornerRadius(10, 28, 28, 10), 56, 14, 0.93f));
        buttons.Margin = new Thickness(0, 16, 0, 0);
        var more = Ui.IconButton(Glyph.More, "More options", p.OnBackground, () => { }, 56, 24, p.Faint);
        more.Click += () => ItemMenu.ShowAt(more, item);
        more.Margin = new Thickness(8, 0, 0, 0);
        buttons.Children.Add(more);
        text.Children.Add(buttons);
        row.Children.Add(text.At(0, 1));
        content.Children.Add(Banner(art, 420, row));
    }

    private void Table(PlaybackContext context, string? editableId)
    {
        var head = Ui.Grid(columns: "48,*,Auto");
        head.Margin = new Thickness(26, 8, 26, 6);
        head.Children.Add(Ui.Text("#", 12, p.OnSurfaceVariant, FontWeights.SemiBold).At(0, 0));
        head.Children.Add(Ui.LabelSmall("Title", p.OnSurfaceVariant).At(0, 1));
        head.Children.Add(Ui.LabelSmall("Time", p.OnSurfaceVariant).At(0, 2));
        content.Children.Add(head);
        content.Children.Add(new Rectangle { Height = 1, Fill = p.Fainter, Margin = new Thickness(26, 0, 26, 6) });
        for (var i = 0; i < songs.Count; i++) table.Children.Add(TableRow(i, context, editableId));
        content.Children.Add(table);
    }

    private FrameworkElement TableRow(int i, PlaybackContext context, string? editableId)
    {
        var song = songs[i];
        var tap = new Tap { CornerRadius = new CornerRadius(16), Padding = new Thickness(10, 6, 10, 6) };
        var grid = Ui.Grid(columns: "48,Auto,*,Auto");
        grid.ColumnSpacing = 12;
        var number = Ui.Text((i + 1).ToString(), 14, p.OnSurfaceVariant);
        number.VerticalAlignment = VerticalAlignment.Center;
        grid.Children.Add(number.At(0, 0));
        grid.Children.Add(Ui.Art(song.Art, 44, 10, p.SurfaceContainerHigh).At(0, 1));
        var names = Ui.Column(0, Ui.TitleSmall(song.Title, p.OnBackground), Ui.BodySmall(song.ArtistNames, p.OnSurfaceVariant));
        names.VerticalAlignment = VerticalAlignment.Center;
        grid.Children.Add(names.At(0, 2));
        var time = Ui.Text(song.DurationMs is { } d ? Ui.Time(d) : "", 13, p.OnSurfaceVariant);
        time.VerticalAlignment = VerticalAlignment.Center;
        grid.Children.Add(time.At(0, 3));
        tap.Children.Add(grid);
        var index = i;
        tap.Click += () => Actions.PlayList(songs, index, context);
        tap.PointerEntered += (_, _) => tap.Background = p.Fainter;
        tap.PointerExited += (_, _) => tap.Background = Theme.Clear;
        ItemMenu.Attach(tap, song, editableId is null ? null : new ItemMenu.Source(editableId, () =>
        {
            songs.Remove(song);
            table.Children.Remove(tap);
        }));
        return tap;
    }
}

/** An artist: their picture as a banner, shuffle / radio / follow, top songs, then their shelves. */
public sealed class ArtistPage : ArtPage
{
    private readonly Artist artist;

    public ArtistPage(Artist artist)
    {
        this.artist = artist;
        UseArt(artist.Art);
        _ = LoadAsync();
    }

    private async Task LoadAsync()
    {
        try
        {
            var page = await App.Services.YouTube.ArtistAsync(artist.BrowseId);
            UseArt(page.Artist.Art);
            var text = new StackPanel { Margin = new Thickness(32, 0, 32, 16), MaxWidth = 900, HorizontalAlignment = HorizontalAlignment.Left };
            text.Children.Add(Ui.DisplayMedium(page.Artist.Title, p.OnBackground));
            if (page.Subscribers is { } subs) text.Children.Add(Ui.BodyMedium(subs, p.OnSurfaceVariant));
            var buttons = Ui.Row(4,
                Ui.Button(Glyph.Shuffle, "Shuffle", p.Primary, p.OnPrimary, () => Play(page, true), new CornerRadius(28, 10, 10, 28)),
                Ui.Button(Glyph.Radio, "Radio", p.Faint, p.OnBackground, () => Play(page, false), new CornerRadius(10, 10, 10, 10), 56, 14, 0.93f),
                Follow(page));
            buttons.Margin = new Thickness(0, 16, 0, 0);
            text.Children.Add(buttons);
            content.Children.Add(Banner(page.Artist.Art, 480, text));
            foreach (var section in page.Sections)
            {
                content.Children.Add(Items.Header(null, section.Title, p));
                if (section.Items.All(i => i is Song))
                {
                    var rows = new StackPanel { Margin = new Thickness(14, 0, 14, 0), MaxWidth = 900, HorizontalAlignment = HorizontalAlignment.Left };
                    foreach (var item in section.Items.Take(5)) rows.Children.Add(Items.Row(item, p));
                    content.Children.Add(rows);
                }
                else content.Children.Add(Items.CardRows(section.Items.Select(i => Items.Card(i, p)), 1));
            }
            if (!string.IsNullOrWhiteSpace(page.Description))
            {
                content.Children.Add(Items.Header(null, "About", p));
                content.Children.Add(Ui.Text(page.Description, 15, p.OnSurfaceVariant, lines: 8).Margin(24, 0, 24, 0));
            }
            loading.Visibility = Visibility.Collapsed;
        }
        catch (Exception e)
        {
            Log.Write("artist", e);
            Failed("this artist");
        }
    }

    private void Play(ArtistResult page, bool shuffle) =>
        _ = Actions.PlayAsyncFor(page.Artist, shuffle, page.ShufflePlaylistId, page.RadioPlaylistId);

    /** Follow (subscribe) on YouTube Music; guests are asked to sign in. */
    private Tap Follow(ArtistResult page)
    {
        var following = page.Subscribed;
        var tap = Ui.Button(null, following ? "Following" : "Follow", following ? p.Primary : p.Faint, following ? p.OnPrimary : p.OnBackground, () => { }, new CornerRadius(10, 28, 28, 10), 56, 14, 0.93f);
        tap.Click += async () =>
        {
            if (!App.Services.Api.SignedIn) { Notices.Show("Sign in with Google to follow artists"); return; }
            var next = !following;
            try
            {
                await App.Services.YouTube.SubscribeAsync(page.ChannelId ?? page.Artist.BrowseId, next);
                following = next;
                tap.Background = following ? p.Primary : p.Faint;
                var label = (TextBlock)((StackPanel)tap.Children[0]).Children[0];
                label.Text = following ? "Following" : "Follow";
                label.Foreground = following ? p.OnPrimary : p.OnBackground;
                Notices.Show(following ? $"Following {page.Artist.Title}" : $"Unfollowed {page.Artist.Title}");
            }
            catch (Exception) { Notices.Show("Couldn't update that"); }
        };
        return tap;
    }
}

/** Your library: saved albums & artists, or your history. */
public sealed class LibraryPage : WavePage
{
    public enum Kind { Saved, History, Playlists }

    private readonly Palette p = Theme.App;
    private readonly StackPanel content = new() { Padding = new Thickness(0, MainWindow.TitleBarHeight + 8, 0, 48) };
    private readonly LoadingIndicator loading = new(80);
    public Kind Showing { get; }

    public LibraryPage(Kind kind)
    {
        Showing = kind;
        Children.Add(new ScrollViewer { Content = content, VerticalScrollBarVisibility = ScrollBarVisibility.Auto });
        Children.Add(Ui.Center(loading));
        content.Children.Add(Ui.HeadlineLarge(kind switch { Kind.Saved => "Albums & artists", Kind.Playlists => "Playlists", _ => "History" }, p.OnBackground).Margin(24, 16, 24, 0));
        _ = LoadAsync();
    }

    private async Task LoadAsync()
    {
        try
        {
            if (Showing == Kind.Playlists)
            {
                var lists = await App.Services.YouTube.LibraryPlaylistsAsync();
                content.Children.Add(Items.CardRows(lists.Select(x => Items.Card(x, p)), 50).Margin(0, 20, 0, 0));
            }
            else if (Showing == Kind.Saved)
            {
                var albums = App.Services.YouTube.LibraryAlbumsAsync();
                var artists = App.Services.YouTube.LibraryArtistsAsync();
                var a = await albums;
                var b = await artists;
                if (a.Count > 0)
                {
                    content.Children.Add(Items.Header(null, "Albums", p));
                    content.Children.Add(Items.CardRows(a.Select(x => Items.Card(x, p)), 50));
                }
                if (b.Count > 0)
                {
                    content.Children.Add(Items.Header(null, "Artists", p));
                    content.Children.Add(Items.CardRows(b.Select(x => Items.Card(x, p)), 50));
                }
                if (a.Count == 0 && b.Count == 0) content.Children.Add(Ui.BodyMedium("Albums you save and artists you follow show up here.", p.OnSurfaceVariant).Margin(24, 12, 24, 0));
            }
            else
            {
                foreach (var section in await App.Services.YouTube.HistoryAsync())
                {
                    content.Children.Add(Items.Header(null, section.Title, p));
                    var rows = new StackPanel { Margin = new Thickness(14, 0, 14, 0), MaxWidth = 900, HorizontalAlignment = HorizontalAlignment.Left };
                    foreach (var item in section.Items) rows.Children.Add(Items.Row(item, p));
                    content.Children.Add(rows);
                }
            }
        }
        catch (Exception e)
        {
            Log.Write("library", e);
            content.Children.Add(Ui.BodyMedium("Couldn't load your library.", p.OnSurfaceVariant).Margin(24, 12, 24, 0));
        }
        loading.Visibility = Visibility.Collapsed;
    }
}
