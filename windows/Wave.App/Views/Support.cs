using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
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

/** Your account's dropdown: who you are, settings, and signing in or out. */
public static class AccountMenu
{
    public static void Show(FrameworkElement anchor, MainWindow window)
    {
        var session = SessionStore.Current;
        var p = Theme.App;
        var flyout = new Flyout { Placement = FlyoutPlacementMode.BottomEdgeAlignedRight };
        var column = new StackPanel { Width = 260, Spacing = 2 };
        column.Children.Add(Ui.Text(session?.Guest == false ? session.Name : "Guest", 16, p.OnBackground, FontWeights.Bold).Margin(12, 6, 12, 0));
        column.Children.Add(Ui.Text(session?.Guest == false ? session.Email ?? "Signed in with Google" : "Not signed in. Likes stay on this computer.", 13, p.OnSurfaceVariant, lines: 2).Margin(12, 0, 12, 8));
        Tap Row(string glyph, string label, Action click)
        {
            var tap = new Tap { Height = 44, CornerRadius = new CornerRadius(12), Padding = new Thickness(12, 0, 12, 0) };
            var row = Ui.Row(14, Ui.Icon(glyph, 20, p.OnBackground), Ui.Text(label, 15, p.OnBackground));
            row.VerticalAlignment = VerticalAlignment.Center;
            tap.Children.Add(row);
            tap.PointerEntered += (_, _) => tap.Background = p.Fainter;
            tap.PointerExited += (_, _) => tap.Background = Theme.Clear;
            tap.Click += () => { flyout.Hide(); click(); };
            return tap;
        }
        column.Children.Add(Row(Glyph.Settings, "Settings", () => SettingsView.Open(window)));
        if (session?.Guest == false)
        {
            column.Children.Add(Row(Glyph.Logout, "Sign out", () =>
            {
                Login.SignOut();
                SessionStore.SignOut();
                window.SessionChanged();
            }));
        }
        else
        {
            column.Children.Add(Row(Glyph.Account, "Sign in with Google", window.SignIn));
        }
        flyout.Content = column;
        flyout.ShowAt(anchor);
    }
}

/** Playlist dialogs: new, add to, rename, delete. */
public static class Dialogs
{
    private static ContentDialog Dialog(string title, object content, string primary, string close = "Cancel") => new()
    {
        Title = title,
        Content = content,
        PrimaryButtonText = primary,
        CloseButtonText = close,
        DefaultButton = ContentDialogButton.Primary,
        XamlRoot = App.Window.Content.XamlRoot,
        RequestedTheme = Theme.SystemDark ? ElementTheme.Dark : ElementTheme.Light,
    };

    public static async Task<string?> CreatePlaylistAsync()
    {
        var box = new TextBox { PlaceholderText = "Playlist name" };
        var dialog = Dialog("New playlist", box, "Create");
        if (await dialog.ShowAsync() != ContentDialogResult.Primary || string.IsNullOrWhiteSpace(box.Text)) return null;
        try
        {
            var id = await App.Services.YouTube.CreatePlaylistAsync(box.Text.Trim());
            Notices.Show("Playlist created");
            _ = App.Window.Nav.LoadPlaylistsAsync();
            return id;
        }
        catch (Exception) { Notices.Show("Couldn't make the playlist"); return null; }
    }

    public static async Task AddToPlaylistAsync(Song song)
    {
        var lists = App.Window.Nav.Playlists.Where(p => p.Editable || p.Id != "LM").ToList();
        var list = new ListView { SelectionMode = ListViewSelectionMode.Single, MaxHeight = 360 };
        list.Items.Add("＋ New playlist");
        foreach (var p in lists) list.Items.Add(p.Title);
        var dialog = Dialog("Add to playlist", list, "Add");
        if (await dialog.ShowAsync() != ContentDialogResult.Primary || list.SelectedIndex < 0) return;
        var target = list.SelectedIndex == 0 ? await CreatePlaylistAsync() : lists[list.SelectedIndex - 1].Id;
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
        var box = new TextBox { Text = playlist.Title };
        if (await Dialog("Rename playlist", box, "Rename").ShowAsync() != ContentDialogResult.Primary || string.IsNullOrWhiteSpace(box.Text)) return;
        try
        {
            await App.Services.YouTube.RenamePlaylistAsync(playlist.Id, box.Text.Trim());
            Notices.Show("Renamed");
            _ = App.Window.Nav.LoadPlaylistsAsync();
        }
        catch (Exception) { Notices.Show("Couldn't rename it"); }
    }

    public static async Task DeletePlaylistAsync(Playlist playlist)
    {
        if (await Dialog($"Delete \"{playlist.Title}\"?", "It's removed from your YouTube Music library.", "Delete").ShowAsync() != ContentDialogResult.Primary) return;
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
