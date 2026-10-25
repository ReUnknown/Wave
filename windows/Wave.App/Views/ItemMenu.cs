using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Wave.Music;

namespace Wave.App.Views;

/** The right-click menu for a song, album, playlist or artist. */
public static class ItemMenu
{
    /** A song shown inside one of your playlists (so it can be removed from it). */
    public sealed record Source(string PlaylistId, Action Removed);

    public static void Attach(FrameworkElement target, Item item, Source? from = null)
    {
        target.RightTapped += (_, e) =>
        {
            e.Handled = true;
            Build(item, from).ShowAt(target, new FlyoutShowOptions { Position = e.GetPosition(target) });
        };
    }

    public static void ShowAt(FrameworkElement target, Item item, Source? from = null) =>
        Build(item, from).ShowAt(target, new FlyoutShowOptions { Placement = FlyoutPlacementMode.BottomEdgeAlignedRight });

    private static MenuFlyoutItem Entry(string glyph, string text, Action click)
    {
        var entry = new MenuFlyoutItem { Text = text, Icon = new FontIcon { Glyph = glyph, FontFamily = Theme.Icons } };
        entry.Click += (_, _) => click();
        return entry;
    }

    public static MenuFlyout Build(Item item, Source? from = null)
    {
        var menu = new MenuFlyout();
        var signedIn = App.Services.Api.SignedIn;
        switch (item)
        {
            case Song song:
                menu.Items.Add(Entry(Glyph.PlayNext, "Play next", () => Actions.PlayNext(song)));
                menu.Items.Add(Entry(Glyph.Queue, "Add to queue", () => Actions.AddToQueue(song)));
                menu.Items.Add(Entry(Glyph.Radio, "Start radio", () => _ = Actions.PlayAsync(song)));
                var liked = Wave.Data.Likes.IsLiked(song.VideoId);
                menu.Items.Add(Entry(Glyph.ThumbUp, liked ? "Remove like" : "Like", () => App.Services.Player.SetLiked(song.VideoId, !liked)));
                menu.Items.Add(signedIn
                    ? Entry(Glyph.PlaylistAdd, "Add to playlist", () => _ = Dialogs.AddToPlaylistAsync(song))
                    : Entry(Glyph.PlaylistAdd, "Add to playlist", () => Notices.Show("Sign in with Google to make playlists")));
                if (from is not null && song.SetVideoId is { } setId)
                {
                    menu.Items.Add(Entry(Glyph.Delete, "Remove from playlist", async () =>
                    {
                        try
                        {
                            await App.Services.YouTube.RemoveFromPlaylistAsync(from.PlaylistId, song.VideoId, setId);
                            from.Removed();
                            Notices.Show("Removed");
                        }
                        catch (Exception) { Notices.Show("Couldn't remove it"); }
                    }));
                }
                menu.Items.Add(new MenuFlyoutSeparator());
                if (song.Artists.FirstOrDefault(a => a.Id is not null) is { } artist)
                    menu.Items.Add(Entry(Glyph.Person, "Go to artist", () => App.Window.Push(new ArtistPage(new Artist(artist.Id!, artist.Name, null)))));
                if (song.Album?.Id is { } albumId)
                    menu.Items.Add(Entry(Glyph.Album, "Go to album", () => App.Window.Push(new CollectionPage(album: new Album(albumId, null, song.Album.Name, song.Artists, null, song.Art)))));
                break;
            case Album album:
                menu.Items.Add(Entry(Glyph.Play, "Play", () => _ = Actions.PlayAsync(album)));
                menu.Items.Add(Entry(Glyph.Shuffle, "Shuffle", () => _ = Actions.PlayAsync(album, shuffle: true)));
                if (album.Artists.FirstOrDefault(a => a.Id is not null) is { } albumArtist)
                    menu.Items.Add(Entry(Glyph.Person, "Go to artist", () => App.Window.Push(new ArtistPage(new Artist(albumArtist.Id!, albumArtist.Name, null)))));
                break;
            case Playlist playlist:
                menu.Items.Add(Entry(Glyph.Play, "Play", () => _ = Actions.PlayAsync(playlist)));
                menu.Items.Add(Entry(Glyph.Shuffle, "Shuffle", () => _ = Actions.PlayAsync(playlist, shuffle: true)));
                if (playlist.Editable && playlist.Id != "LM")
                {
                    menu.Items.Add(Entry(Glyph.Edit, "Rename", () => _ = Dialogs.RenamePlaylistAsync(playlist)));
                    menu.Items.Add(Entry(Glyph.Delete, "Delete playlist", () => _ = Dialogs.DeletePlaylistAsync(playlist)));
                }
                break;
            case Artist artist2:
                menu.Items.Add(Entry(Glyph.Shuffle, "Shuffle", () => _ = Actions.PlayAsync(artist2, shuffle: true)));
                menu.Items.Add(Entry(Glyph.Radio, "Start radio", () => _ = Actions.PlayAsync(artist2)));
                break;
        }
        menu.Items.Add(Entry(Glyph.Share, "Copy link", () => Actions.Share(item)));
        return menu;
    }
}
