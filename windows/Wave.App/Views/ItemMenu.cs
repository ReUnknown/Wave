using Microsoft.UI.Xaml;
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
            Build(item, from).ShowAt(target, e.GetPosition(target));
        };
    }

    public static void ShowAt(FrameworkElement target, Item item, Source? from = null, Palette? palette = null) =>
        Build(item, from, palette).ShowAt(target);

    public static Menu Build(Item item, Source? from = null, Palette? palette = null)
    {
        var menu = new Menu(palette);
        var signedIn = App.Services.Api.SignedIn;
        switch (item)
        {
            case Song song:
                menu.Add(Glyph.PlayNext, "Play next", () => Actions.PlayNext(song));
                menu.Add(Glyph.Queue, "Add to queue", () => Actions.AddToQueue(song));
                menu.Add(Glyph.Radio, "Start radio", () => _ = Actions.PlayAsync(song));
                var liked = Wave.Data.Likes.IsLiked(song.VideoId);
                menu.Add(Glyph.ThumbUp, liked ? "Remove like" : "Like", () => App.Services.Player.SetLiked(song.VideoId, !liked), outlined: !liked);
                menu.Add(Glyph.PlaylistAdd, "Add to playlist", () =>
                {
                    if (signedIn) _ = Dialogs.AddToPlaylistAsync(song);
                    else Notices.Show("Sign in with Google to make playlists");
                });
                if (from is not null && song.SetVideoId is { } setId)
                {
                    menu.Add(Glyph.Delete, "Remove from playlist", async () =>
                    {
                        try
                        {
                            await App.Services.YouTube.RemoveFromPlaylistAsync(from.PlaylistId, song.VideoId, setId);
                            from.Removed();
                            Notices.Show("Removed");
                        }
                        catch (Exception) { Notices.Show("Couldn't remove it"); }
                    });
                }
                menu.Separator();
                if (song.Artists.FirstOrDefault(a => a.Id is not null) is { } artist)
                    menu.Add(Glyph.Person, "Go to artist", () => App.Window.Push(new ArtistPage(new Artist(artist.Id!, artist.Name, null))));
                if (song.Album?.Id is { } albumId)
                    menu.Add(Glyph.Album, "Go to album", () => App.Window.Push(new CollectionPage(album: new Album(albumId, null, song.Album.Name, song.Artists, null, song.Art))));
                break;
            case Album album:
                menu.Add(Glyph.Play, "Play", () => _ = Actions.PlayAsync(album));
                menu.Add(Glyph.Shuffle, "Shuffle", () => _ = Actions.PlayAsync(album, shuffle: true));
                if (album.Artists.FirstOrDefault(a => a.Id is not null) is { } albumArtist)
                    menu.Add(Glyph.Person, "Go to artist", () => App.Window.Push(new ArtistPage(new Artist(albumArtist.Id!, albumArtist.Name, null))));
                break;
            case Playlist playlist:
                menu.Add(Glyph.Play, "Play", () => _ = Actions.PlayAsync(playlist));
                menu.Add(Glyph.Shuffle, "Shuffle", () => _ = Actions.PlayAsync(playlist, shuffle: true));
                if (playlist.Editable && playlist.Id != "LM")
                {
                    menu.Add(Glyph.Edit, "Rename", () => _ = Dialogs.RenamePlaylistAsync(playlist));
                    menu.Add(Glyph.Delete, "Delete playlist", () => _ = Dialogs.DeletePlaylistAsync(playlist));
                }
                break;
            case Artist artist2:
                menu.Add(Glyph.Shuffle, "Shuffle", () => _ = Actions.PlayAsync(artist2, shuffle: true));
                menu.Add(Glyph.Radio, "Start radio", () => _ = Actions.PlayAsync(artist2));
                break;
        }
        menu.Add(Glyph.Share, "Copy link", () => Actions.Share(item));
        return menu;
    }
}
