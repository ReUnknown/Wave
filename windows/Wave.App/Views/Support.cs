using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Wave.Coloring;
using Wave.Data;
using Wave.Music;

namespace Wave.App.Views;

/** The whole app takes on the colors of what's playing (or home's banner when nothing is). */
public static class SongColors
{
    private static string? homeArt;
    private static string? applied;

    public static string? HomeArt
    {
        set { homeArt = value; Update(); }
    }

    public static void Start()
    {
        App.Services.Player.Changed += _ => Update();
        SettingsStore.Changed += _ => Update();
        Update();
    }

    private static async void Update()
    {
        var art = SettingsStore.Current.MatchSongColors ? App.Services.Player.State.Current?.Art ?? homeArt : null;
        if (art == applied) return;
        applied = art;
        var scheme = art is null ? null : await Images.SchemeAsync(art, Theme.SystemDark);
        if (applied != art) return;
        Theme.App.Set(scheme ?? Theme.Base);
    }
}

/** Opening music.youtube.com links (passed to Wave, or pasted into search). */
public static class Links
{
    public static async Task<bool> OpenAsync(string link)
    {
        if (!Uri.TryCreate(link.Trim(), UriKind.Absolute, out var uri) || !uri.Host.EndsWith("youtube.com") && uri.Host != "youtu.be") return false;
        var query = System.Web.HttpUtility.ParseQueryString(uri.Query);
        try
        {
            if (uri.Host == "youtu.be" || query["v"] is not null)
            {
                var id = uri.Host == "youtu.be" ? uri.AbsolutePath.Trim('/') : query["v"]!;
                var request = await App.Services.Queues.ForItemAsync(new Song(id, "", [], null, null, null));
                App.Services.Player.Play(request);
                App.Window.OpenPlayer();
            }
            else if (query["list"] is { } list)
            {
                App.Window.Push(new CollectionPage(playlist: new Playlist(list, "", null, null, null)));
            }
            else if (uri.AbsolutePath.StartsWith("/browse/MPREb"))
            {
                App.Window.Push(new CollectionPage(album: new Album(uri.AbsolutePath["/browse/".Length..], null, "", [], null, null)));
            }
            else if (uri.AbsolutePath.StartsWith("/channel/"))
            {
                App.Window.Push(new ArtistPage(new Artist(uri.AbsolutePath["/channel/".Length..], "", null)));
            }
            else return false;
            return true;
        }
        catch (Exception)
        {
            Notices.Show("Couldn't open that link");
            return false;
        }
    }
}

/** Your account's dropdown: who you are, your library (narrow windows), settings, signing in or out. */
public static class AccountMenu
{
    public static void Show(FrameworkElement anchor, MainWindow window)
    {
        var session = SessionStore.Current;
        var signedIn = session?.Guest == false;
        var menu = new Menu().Header(signedIn ? session!.Name : "Guest",
            signedIn ? session!.Email ?? "Signed in with Google" : "Not signed in. Likes stay on this computer.");
        menu.Separator();
        // Narrow windows have no sidebar: your library is here instead.
        if (window.Narrow && signedIn)
        {
            menu.Add(Glyph.Library, "Playlists", () => window.Push(new LibraryPage(LibraryPage.Kind.Playlists)));
            menu.Add(Glyph.Album, "Albums & artists", () => window.Push(new LibraryPage(LibraryPage.Kind.Saved)));
            menu.Add(Glyph.History, "History", () => window.Push(new LibraryPage(LibraryPage.Kind.History)));
            menu.Separator();
        }
        menu.Add(Glyph.Settings, "Settings", () => SettingsView.Open(window));
        if (signedIn)
        {
            menu.Add(Glyph.Logout, "Sign out", () =>
            {
                Login.SignOut();
                SessionStore.SignOut();
                window.SessionChanged();
            });
        }
        else menu.Add(Glyph.Account, "Sign in with Google", window.SignIn);
        menu.ShowAt(anchor);
    }
}

/** Playlist dialogs: new, add to, rename, delete. */
public static class Dialogs
{
    public static async Task<string?> CreatePlaylistAsync()
    {
        var field = new Field("Playlist name", height: 52);
        var dialog = new Dialog("New playlist").Content(field).Button("Cancel", 0).Button("Create", 1, primary: true);
        field.Box.KeyDown += (_, e) => { if (e.Key == Windows.System.VirtualKey.Enter) dialog.Close(1); };
        var show = dialog.ShowAsync();
        field.Box.Focus(FocusState.Programmatic);
        if (await show != 1 || string.IsNullOrWhiteSpace(field.Text)) return null;
        try
        {
            var id = await App.Services.YouTube.CreatePlaylistAsync(field.Text.Trim());
            Notices.Show("Playlist created");
            _ = App.Window.Nav.LoadPlaylistsAsync();
            return id;
        }
        catch (Exception) { Notices.Show("Couldn't make the playlist"); return null; }
    }

    public static async Task AddToPlaylistAsync(Song song)
    {
        var p = Theme.App;
        var lists = App.Window.Nav.Playlists.Where(x => x.Editable && x.Id != "LM").ToList();
        if (lists.Count == 0 && App.Window.Nav.Playlists.Count == 0)
            lists = (await App.Services.YouTube.LibraryPlaylistsAsync()).Where(x => x.Editable && x.Id != "LM").ToList();
        var column = new StackPanel { Spacing = 2 };
        Dialog? dialog = null;
        string? picked = null;
        Tap Row(string? art, string title, Action click)
        {
            var tap = new Tap { CornerRadius = new CornerRadius(16), Padding = new Thickness(8) };
            var grid = Ui.Grid(columns: "Auto,*");
            grid.ColumnSpacing = 12;
            grid.Children.Add((art is null ? (FrameworkElement)new Border { Width = 44, Height = 44, CornerRadius = new CornerRadius(12), Background = p.PrimaryContainer, Child = Ui.Center(Ui.Icon(Glyph.Add, 24, p.OnPrimaryContainer)) } : Ui.Art(art, 44, 12)).At(0, 0));
            var name = Ui.Text(title, 15, p.OnBackground, FontWeights.SemiBold);
            name.VerticalAlignment = VerticalAlignment.Center;
            grid.Children.Add(name.At(0, 1));
            tap.Children.Add(grid);
            tap.PointerEntered += (_, _) => tap.Background = p.Fainter;
            tap.PointerExited += (_, _) => tap.Background = Theme.Clear;
            tap.Click += click;
            return tap;
        }
        column.Children.Add(Row(null, "New playlist", () => { picked = ""; dialog?.Close(1); }));
        foreach (var list in lists) column.Children.Add(Row(list.Thumbnail, list.Title, () => { picked = list.Id; dialog?.Close(1); }));
        dialog = new Dialog("Add to playlist").Content(new ScrollViewer { Content = column, MaxHeight = 380 }).Button("Cancel", 0);
        if (await dialog.ShowAsync() != 1 || picked is null) return;
        var target = picked.Length == 0 ? await CreatePlaylistAsync() : picked;
        if (target is null) return;
        try
        {
            await App.Services.YouTube.AddToPlaylistAsync(target, song.VideoId);
            Notices.Show("Added");
        }
        catch (Exception) { Notices.Show("Couldn't add it"); }
    }

    public static async Task RenamePlaylistAsync(Playlist playlist)
    {
        var field = new Field("Playlist name", height: 52) { Text = playlist.Title };
        var dialog = new Dialog("Rename playlist").Content(field).Button("Cancel", 0).Button("Rename", 1, primary: true);
        field.Box.KeyDown += (_, e) => { if (e.Key == Windows.System.VirtualKey.Enter) dialog.Close(1); };
        if (await dialog.ShowAsync() != 1 || string.IsNullOrWhiteSpace(field.Text)) return;
        try
        {
            await App.Services.YouTube.RenamePlaylistAsync(playlist.Id, field.Text.Trim());
            Notices.Show("Renamed");
            _ = App.Window.Nav.LoadPlaylistsAsync();
        }
        catch (Exception) { Notices.Show("Couldn't rename it"); }
    }

    public static async Task DeletePlaylistAsync(Playlist playlist)
    {
        var dialog = new Dialog($"Delete \"{playlist.Title}\"?").Text("It's removed from your YouTube Music library.").Button("Cancel", 0).Button("Delete", 1, primary: true);
        if (await dialog.ShowAsync() != 1) return;
        try
        {
            await App.Services.YouTube.DeletePlaylistAsync(playlist.Id);
            Notices.Show("Deleted");
            _ = App.Window.Nav.LoadPlaylistsAsync();
            if (App.Window.Top is CollectionPage c && c.PlaylistId == playlist.Id) App.Window.Pop();
        }
        catch (Exception) { Notices.Show("Couldn't delete it"); }
    }
}
