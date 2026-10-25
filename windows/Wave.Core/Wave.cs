using System.Text.Json.Nodes;
using Wave.Data;
using Wave.Music;
using Wave.Playback;

namespace Wave;

/** What sits at the very top of home. */
public sealed record HomeFeed(Item? Hero, IReadOnlyList<Song> Recent, IReadOnlyList<Section> Sections);

/** Wave's services, created once at startup. */
public sealed class Services
{
    public InnerTube Api { get; }
    public YouTubeMusic YouTube { get; }
    public Streams Streams { get; }
    public LyricsSource Lyrics { get; }
    public QueueBuilder Queues { get; }
    public Player Player { get; }
    public Updater Updater { get; }

    public Services(IAudioEngine engine, string appVersion)
    {
        Api = new InnerTube();
        YouTube = new YouTubeMusic(Api);
        Streams = new Streams(Api.Http);
        Lyrics = new LyricsSource(Api.Http, YouTube);
        Queues = new QueueBuilder(YouTube);
        Player = new Player(engine, YouTube, Streams);
        Updater = new Updater(Api.Http, appVersion);
    }

    /** Points requests at the saved account (or a guest). */
    public void Apply(Session? session)
    {
        if (session is null || session.Guest) Api.UseSession(null, null, null, "0");
        else Api.UseSession(session.Cookie, session.VisitorData, session.DataSyncId, session.AuthUser);
    }

    // ---------------------------------------------------------------- Home

    private (DateTime at, HomeFeed feed)? cachedHome;

    /** Home: the first page fast (with history), then again once two more pages are in. */
    public async IAsyncEnumerable<HomeFeed> HomeAsync(bool refresh = false)
    {
        if (!refresh && cachedHome is { } c && DateTime.UtcNow - c.at < TimeSpan.FromMinutes(15))
        {
            yield return c.feed;
            yield break;
        }
        var historyTask = Api.SignedIn ? YouTube.HistoryAsync() : Task.FromResult<IReadOnlyList<Section>>([]);
        var first = await YouTube.HomeAsync();
        var sections = first.Sections.ToList();
        IReadOnlyList<Section> history;
        try { history = await historyTask; } catch (Exception) { history = []; }
        var recent = history.SelectMany(s => s.Items).OfType<Song>().Where(s => !s.IsEpisode).DistinctBy(s => s.VideoId).Take(12).ToList();
        yield return Build(sections, recent);

        var continuation = first.Continuation;
        var added = false;
        for (var page = 0; page < 2 && continuation is not null; page++)
        {
            HomeResult? next = null;
            try { next = await YouTube.HomeAsync(continuation); } catch (Exception) { }
            if (next is null) break;
            sections.AddRange(next.Sections);
            continuation = next.Continuation;
            added = true;
        }
        var full = Build(sections, recent);
        cachedHome = (DateTime.UtcNow, full);
        if (added) yield return full;
    }

    public void ForgetHome() => cachedHome = null;

    private static bool IsPodcast(Item item) => item switch
    {
        Playlist p => p.IsPodcast,
        Song s => s.IsEpisode,
        _ => false,
    };

    private static HomeFeed Build(List<Section> sections, IReadOnlyList<Song> recent)
    {
        var music = sections
            .Select(s => s with { Items = s.Items.Where(i => !IsPodcast(i)).DistinctBy(i => i.Key).ToList() })
            .Where(s => s.Items.Count > 0)
            .DistinctBy(s => s.Title)
            .ToList();
        var items = music.SelectMany(s => s.Items).ToList();
        var hero = items.OfType<Playlist>().FirstOrDefault() ?? (Item?)items.OfType<Album>().FirstOrDefault() ?? items.FirstOrDefault();
        return new HomeFeed(hero, recent, music);
    }

    // ---------------------------------------------------------------- Search

    /** Search with podcasts, videos and profiles left out (Wave is for music). */
    public async Task<IReadOnlyList<Section>> SearchAsync(string query, SearchFilter filter)
    {
        static bool Music(Item i) => i switch
        {
            Playlist p => !p.IsPodcast,
            Song s => !s.IsEpisode && !s.IsVideo,
            Artist a => !a.IsProfile,
            _ => true,
        };
        var groups = await YouTube.SearchAsync(query, filter);
        return groups
            .Where(g => g.Title is not ("Videos" or "Episodes" or "Podcasts" or "Profiles"))
            .Select(g => g with { Items = g.Items.Where(Music).DistinctBy(i => i.Key).ToList() })
            .Where(g => g.Items.Count > 0)
            .ToList();
    }

    // ---------------------------------------------------------------- Likes

    /** Learns which songs you've liked (Liked music), unless the saved list is recent. */
    public async Task LoadLikesAsync()
    {
        if (!Api.SignedIn || Likes.Fresh) return;
        try
        {
            var page = await YouTube.PlaylistAsync("LM");
            var all = page.Songs.Select(s => s.VideoId).ToList();
            var next = page.Continuation;
            for (var i = 0; next is not null && i < 50; i++)
            {
                var (songs, more) = await YouTube.PlaylistMoreAsync(next);
                all.AddRange(songs.Select(s => s.VideoId));
                next = more;
            }
            Likes.ReplaceAll(all);
        }
        catch (Exception) { }
    }
}

/**
 * Wave updates itself from its GitHub releases: the newest release (betas included) with a Windows
 * installer attached. The installer runs quietly over this copy and reopens Wave.
 */
public sealed class Updater(HttpClient http, string currentVersion)
{
    private const string Repo = "ReUnknown/Wave";

    public sealed record Release(string Version, string Notes, string DownloadUrl, string PageUrl);

    public async Task<Release?> CheckAsync(CancellationToken ct = default)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, $"https://api.github.com/repos/{Repo}/releases?per_page=10");
        request.Headers.TryAddWithoutValidation("Accept", "application/vnd.github+json");
        request.Headers.TryAddWithoutValidation("User-Agent", "Wave/" + currentVersion);
        using var response = await http.SendAsync(request, ct);
        response.EnsureSuccessStatusCode();
        var list = JsonNode.Parse(await response.Content.ReadAsStringAsync(ct)) as JsonArray ?? [];
        var best = list.Where(r => r is not null && r["draft"]?.GetValue<bool>() != true)
            .Select(r =>
            {
                var asset = (r!["assets"] as JsonArray)?.FirstOrDefault(a => a?["name"]?.GetValue<string>()?.EndsWith("-windows-x64.exe") == true);
                return asset is null ? null : new Release(
                    (r["tag_name"]?.GetValue<string>() ?? "").TrimStart('v'),
                    r["body"]?.GetValue<string>() ?? "",
                    asset["browser_download_url"]!.GetValue<string>(),
                    r["html_url"]?.GetValue<string>() ?? "");
            })
            .OfType<Release>()
            .OrderByDescending(r => r.Version, Comparer<string>.Create(Compare))
            .FirstOrDefault();
        return best is not null && Compare(best.Version, currentVersion) > 0 ? best : null;
    }

    /** Downloads the installer to [folder], reporting progress 0..1. */
    public async Task<string> DownloadAsync(Release release, string folder, IProgress<double>? progress = null, CancellationToken ct = default)
    {
        Directory.CreateDirectory(folder);
        foreach (var old in Directory.GetFiles(folder)) { try { File.Delete(old); } catch (Exception) { } }
        var path = Path.Combine(folder, $"Wave-{release.Version}-windows-x64.exe");
        using var response = await http.GetAsync(release.DownloadUrl, HttpCompletionOption.ResponseHeadersRead, ct);
        response.EnsureSuccessStatusCode();
        var total = response.Content.Headers.ContentLength ?? -1;
        await using var input = await response.Content.ReadAsStreamAsync(ct);
        await using var output = File.Create(path);
        var buffer = new byte[81920];
        long done = 0;
        int n;
        while ((n = await input.ReadAsync(buffer, ct)) > 0)
        {
            await output.WriteAsync(buffer.AsMemory(0, n), ct);
            done += n;
            if (total > 0) progress?.Report((double)done / total);
        }
        return path;
    }

    /** "1.2.0" > "1.2.0-beta.3" > "1.2.0-beta.2" > "1.1.0". */
    public static int Compare(string a, string b)
    {
        static (int[] nums, string? pre) Split(string v)
        {
            var parts = v.Trim().TrimStart('v').Split('-', 2);
            return (parts[0].Split('.').Select(x => int.TryParse(x, out var n) ? n : 0).ToArray(), parts.Length > 1 ? parts[1] : null);
        }
        var (na, pa) = Split(a);
        var (nb, pb) = Split(b);
        for (var i = 0; i < Math.Max(na.Length, nb.Length); i++)
        {
            var d = (i < na.Length ? na[i] : 0).CompareTo(i < nb.Length ? nb[i] : 0);
            if (d != 0) return d;
        }
        if (pa is null && pb is null) return 0;
        if (pa is null) return 1;
        if (pb is null) return -1;
        var qa = pa.Split('.');
        var qb = pb.Split('.');
        for (var i = 0; i < Math.Max(qa.Length, qb.Length); i++)
        {
            if (i >= qa.Length) return -1;
            if (i >= qb.Length) return 1;
            var d = int.TryParse(qa[i], out var x) && int.TryParse(qb[i], out var y) ? x.CompareTo(y) : string.CompareOrdinal(qa[i], qb[i]);
            if (d != 0) return d;
        }
        return 0;
    }
}
