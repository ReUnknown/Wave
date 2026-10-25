using Wave.Music;

namespace Wave.Playback;

/** Turns what you tapped (a song, album, playlist, artist) into a queue to play. */
public sealed class QueueBuilder(YouTubeMusic youtube)
{
    /** Pages loaded in the background: plenty for albums and playlists, a few for endless radios. */
    private const int CollectionPages = 40, RadioPages = 3;

    public async Task<PlayRequest> ForItemAsync(Item item, bool shuffle = false, string? artistShuffleId = null, string? artistRadioId = null)
    {
        switch (item)
        {
            case Song song:
                return await FromNextAsync(song.VideoId, "RDAMVM" + song.VideoId,
                    new PlaybackContext("radio-" + song.VideoId, song.Title + " radio", song.Art, null, false), false);
            case Playlist playlist:
                return await FromNextAsync(null, playlist.Id,
                    new PlaybackContext(playlist.Id, playlist.Title, playlist.Art, playlist.Id, true), shuffle);
            case Album album:
                var albumPlaylist = album.PlaylistId ?? (await youtube.AlbumAsync(album.BrowseId)).Album.PlaylistId
                    ?? throw new InvalidOperationException("Nothing to play");
                return await FromNextAsync(null, albumPlaylist,
                    new PlaybackContext(album.BrowseId, album.Title, album.Art, albumPlaylist, true), shuffle);
            case Artist artist:
                var id = (shuffle ? artistShuffleId : artistRadioId) ?? await ArtistPlaylistAsync(artist.BrowseId, shuffle)
                    ?? throw new InvalidOperationException("Nothing to play for " + artist.Name);
                return await FromNextAsync(null, id, new PlaybackContext(artist.BrowseId, artist.Name + " radio", artist.Art, id, false), false);
            default:
                throw new InvalidOperationException("Can't play this");
        }
    }

    private async Task<string?> ArtistPlaylistAsync(string browseId, bool shuffle)
    {
        var page = await youtube.ArtistAsync(browseId);
        return shuffle ? page.ShufflePlaylistId ?? page.RadioPlaylistId : page.RadioPlaylistId ?? page.ShufflePlaylistId;
    }

    /** A known list (an album or playlist page), starting at [startIndex]. */
    public static PlayRequest ForList(IReadOnlyList<Song> songs, int startIndex, PlaybackContext context, bool shuffle) => new(
        songs.Select(NowPlaying.From).ToList(),
        shuffle ? Random.Shared.Next(Math.Max(1, songs.Count)) : Math.Clamp(startIndex, 0, Math.Max(0, songs.Count - 1)),
        context,
        shuffle);

    public static PlayRequest Single(Song song) =>
        new([NowPlaying.From(song)], 0, new PlaybackContext("song-" + song.VideoId, song.Title, song.Art, null, false), false);

    /** "Play all" on a section of songs. */
    public static PlayRequest ForSongs(string title, IReadOnlyList<Song> songs, bool shuffle) => new(
        songs.Select(NowPlaying.From).ToList(),
        shuffle ? Random.Shared.Next(Math.Max(1, songs.Count)) : 0,
        new PlaybackContext("section-" + title, title, songs.FirstOrDefault()?.Art, null, false),
        shuffle);

    private async Task<PlayRequest> FromNextAsync(string? videoId, string playlistId, PlaybackContext context, bool shuffle)
    {
        // Play as soon as the first page arrives; fetch the rest afterwards.
        var first = await youtube.NextAsync(videoId, playlistId);
        var songs = first.Songs.Where(s => !s.IsEpisode).DistinctBy(s => s.VideoId).ToList();
        if (songs.Count == 0) throw new InvalidOperationException("Nothing to play");
        var seen = songs.Select(s => s.VideoId).ToHashSet();
        Func<Task<IReadOnlyList<NowPlaying>>>? more = null;
        if (first.Continuation is not null)
        {
            more = async () =>
            {
                var extra = new List<NowPlaying>();
                var continuation = first.Continuation;
                var limit = context.Collection ? CollectionPages : RadioPages;
                for (var page = 0; continuation is not null && page < limit; page++)
                {
                    var next = await youtube.NextAsync(null, playlistId, continuation);
                    extra.AddRange(next.Songs.Where(s => seen.Add(s.VideoId)).Select(NowPlaying.From));
                    continuation = next.Continuation;
                }
                return extra;
            };
        }
        return new PlayRequest(songs.Select(NowPlaying.From).ToList(), Math.Clamp(first.CurrentIndex, 0, songs.Count - 1), context, shuffle, more);
    }
}
