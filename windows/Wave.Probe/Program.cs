using Wave.Music;

var api = new InnerTube();
await api.WarmUpAsync();
Console.WriteLine($"client {api.ClientVersion}, visitor {(api.VisitorData is null ? "none" : "ok")}");
var yt = new YouTubeMusic(api);

void Show(string label, IEnumerable<Item> items) =>
    Console.WriteLine($"  {label}: " + string.Join(" | ", items.Take(4).Select(i => $"{i.GetType().Name}:{i.Title} — {i.Subtitle}{(i.Thumbnail is null ? " (no art)" : "")}")));

var home = await yt.HomeAsync();
Console.WriteLine($"HOME {home.Sections.Count} sections, more={home.Continuation is not null}");
foreach (var s in home.Sections.Take(5)) Show($"{s.Label}/{s.Title}", s.Items);
if (home.Continuation is not null)
{
    var more = await yt.HomeAsync(home.Continuation);
    Console.WriteLine($"HOME page 2: {more.Sections.Count} sections");
}

foreach (var filter in new[] { SearchFilter.All, SearchFilter.Songs, SearchFilter.Albums, SearchFilter.Artists, SearchFilter.Playlists })
{
    var groups = await yt.SearchAsync("the weeknd", filter);
    Console.WriteLine($"SEARCH {filter}: {groups.Count} groups");
    foreach (var g in groups.Take(4)) Show(g.Title, g.Items);
}
var sug = await yt.SuggestionsAsync("blind");
Console.WriteLine($"SUGGEST {string.Join(", ", sug.Queries.Take(5))}");
Show("suggested items", sug.Items);

var songs = (await yt.SearchAsync("blinding lights", SearchFilter.Songs))[0].Items.OfType<Song>().ToList();
var song = songs[0];
Console.WriteLine($"SONG {song.Title} by {song.ArtistNames}, album {song.Album?.Name}/{song.Album?.Id}, {song.DurationMs} ms");

var album = await yt.AlbumAsync(song.Album!.Id!);
Console.WriteLine($"ALBUM {album.Album.Title} by {string.Join(",", album.Album.Artists.Select(a => a.Name))} {album.Album.Year} playlist={album.Album.PlaylistId} songs={album.Songs.Count} more={album.More.Count}");
Show("tracks", album.Songs);

var artistId = song.Artists.First(a => a.Id is not null).Id!;
var artist = await yt.ArtistAsync(artistId);
Console.WriteLine($"ARTIST {artist.Artist.Title} subs={artist.Subscribers} channel={artist.ChannelId} shuffle={artist.ShufflePlaylistId} radio={artist.RadioPlaylistId} about={(artist.Description?.Length ?? 0)} chars");
foreach (var s in artist.Sections) Show($"{s.Title} (more {s.MoreBrowseId})", s.Items);

var playlists = (await yt.SearchAsync("throwback hits", SearchFilter.Playlists))[0].Items.OfType<Playlist>().ToList();
var playlist = await yt.PlaylistAsync(playlists[0].Id);
Console.WriteLine($"PLAYLIST {playlist.Playlist.Title} by {playlist.Playlist.Author} count={playlist.Playlist.SongCount} songs={playlist.Songs.Count} cont={playlist.Continuation is not null}");
Show("songs", playlist.Songs);
if (playlist.Continuation is not null)
{
    var (more, next) = await yt.PlaylistMoreAsync(playlist.Continuation);
    Console.WriteLine($"  more: {more.Count} songs, cont={next is not null}");
}

var next1 = await yt.NextAsync(song.VideoId, "RDAMVM" + song.VideoId);
Console.WriteLine($"NEXT {next1.Songs.Count} songs, index {next1.CurrentIndex}, cont={next1.Continuation is not null}, lyrics={next1.LyricsBrowseId}, related={next1.RelatedBrowseId}");
Show("queue", next1.Songs);
if (next1.Continuation is not null)
{
    var more = await yt.NextAsync(null, "RDAMVM" + song.VideoId, next1.Continuation);
    Console.WriteLine($"  next more: {more.Songs.Count}");
}
if (next1.LyricsBrowseId is not null)
{
    var lyrics = await yt.LyricsAsync(next1.LyricsBrowseId);
    Console.WriteLine($"LYRICS {(lyrics?.Length ?? 0)} chars");
}
var info = await yt.PlayerInfoAsync(song.VideoId);
Console.WriteLine($"PLAYER loudness={info.loudnessDb} tracking={(info.trackingUrl is null ? "none" : "ok")} duration={info.durationMs}");

var streams = new Streams(api.Http, Path.Combine(Path.GetTempPath(), "wave-probe-audio"));
var t0 = DateTime.UtcNow;
var stream = await streams.ResolveAsync(song.VideoId);
Console.WriteLine($"STREAM {stream.Container} {stream.Bitrate / 1000} kbps ready in {(DateTime.UtcNow - t0).TotalMilliseconds:0} ms via {stream.Source}");
using (var local = new HttpClient())
{
    var req = new HttpRequestMessage(HttpMethod.Get, stream.Source);
    req.Headers.Range = new System.Net.Http.Headers.RangeHeaderValue(1000, 50999);
    t0 = DateTime.UtcNow;
    using var res = await local.SendAsync(req);
    var bytes = await res.Content.ReadAsByteArrayAsync();
    Console.WriteLine($"  relay: HTTP {(int)res.StatusCode} {bytes.Length} bytes, {res.Content.Headers.ContentRange}, {res.Content.Headers.ContentType} in {(DateTime.UtcNow - t0).TotalMilliseconds:0} ms");
}
t0 = DateTime.UtcNow;
await streams.PrefetchAsync(song.VideoId);
var again = await streams.ResolveAsync(song.VideoId);
Console.WriteLine($"  prefetched in {(DateTime.UtcNow - t0).TotalMilliseconds:0} ms; now plays from file: {again.IsFile}");

// ---- Queues, lyrics, colors, updater ----
var queues = new Wave.Playback.QueueBuilder(yt);
foreach (var item in new Item[] { song, album.Album, playlists[0], artist.Artist })
{
    var req = await queues.ForItemAsync(item, artistShuffleId: artist.ShufflePlaylistId, artistRadioId: artist.RadioPlaylistId);
    var extra = req.More is null ? 0 : (await req.More()).Count;
    Console.WriteLine($"QUEUE {item.GetType().Name} '{req.Context.Title}': {req.Items.Count} songs (+{extra} more), start {req.StartIndex}, first '{req.Items[0].Title}' art={(req.Items[0].Art is null ? "none" : "ok")}");
}
var lyricsSource = new LyricsSource(api.Http, yt);
var ly = await lyricsSource.LoadAsync(song.VideoId, song.Title, song.ArtistNames, song.DurationMs ?? 0, song.Album?.Name);
Console.WriteLine($"LYRICS {ly?.Source}: {ly?.Lines.Count} lines, synced={ly?.Synced}, words={ly?.WordSynced}; line 3: {ly?.Lines.ElementAtOrDefault(3)?.Text}");
var px = new uint[64 * 64];
for (var i = 0; i < px.Length; i++) px[i] = i % 5 == 0 ? 0xFF2050C0u : 0xFF101014u;
var scheme = Wave.Coloring.ArtColors.FromPixels(px, dark: true)!;
Console.WriteLine($"COLORS primary #{scheme.Primary.Value:X8} background #{scheme.Background.Value:X8}");
var updater = new Wave.Updater(api.Http, "1.1.0");
var release = await updater.CheckAsync();
Console.WriteLine($"UPDATE newer than 1.1.0: {release?.Version ?? "none"}; compare beta: {Wave.Updater.Compare("1.2.0-beta.1", "1.1.0")} {Wave.Updater.Compare("1.2.0", "1.2.0-beta.9")}");
