using System.Collections.Concurrent;
using System.Net;
using System.Text;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace Wave.Music;

public sealed record LyricWord(long StartMs, long EndMs, string Text, bool SpaceAfter);

public sealed record LyricLine(long StartMs, long EndMs, IReadOnlyList<LyricWord> Words, IReadOnlyList<LyricWord>? Background = null)
{
    public string Text => string.Concat(Words.Select(w => w.Text + (w.SpaceAfter ? " " : ""))).Trim();
}

/** [WordSynced]: real per-word timing. Otherwise lines are timed (words spread by length), or untimed. */
public sealed record Lyrics(IReadOnlyList<LyricLine> Lines, bool Synced, bool WordSynced, string Source);

/**
 * Lyrics, best first: word-timed from Better Lyrics (Apple Music's TTML), line-timed from LRCLIB,
 * then YouTube Music's plain text. Word and line sources are asked at once; cached per song.
 */
public sealed class LyricsSource(HttpClient http, YouTubeMusic youtube)
{
    private readonly ConcurrentDictionary<string, Lyrics> cache = new();
    private readonly ConcurrentDictionary<string, Task<Lyrics?>> inFlight = new();

    public Task<Lyrics?> LoadAsync(string videoId, string title, string artist, long durationMs, string? album = null)
    {
        if (cache.TryGetValue(videoId, out var hit)) return Task.FromResult<Lyrics?>(hit);
        return inFlight.GetOrAdd(videoId, _ => Task.Run(async () =>
        {
            try
            {
                var words = Safe(() => FromBetterLyrics(title, artist, durationMs, album));
                var lines = Safe(() => FromLrcLib(title, artist, durationMs));
                var found = (await words) is { WordSynced: true } w ? w
                    : (await lines) is { Synced: true } l ? l
                    : await words ?? await lines ?? await Safe(() => FromYouTube(videoId));
                if (found is not null) cache[videoId] = found;
                return found;
            }
            finally
            {
                inFlight.TryRemove(videoId, out Task<Lyrics?>? _);
            }
        }));
    }

    private static async Task<Lyrics?> Safe(Func<Task<Lyrics?>> source)
    {
        try { return await source(); } catch (Exception) { return null; }
    }

    private static string CleanTitle(string title)
    {
        title = Regex.Replace(title, @"\s*[(\[](feat|ft|with|prod)\.?[^)\]]*[)\]]", "", RegexOptions.IgnoreCase);
        title = Regex.Replace(title, @"\s*-\s*(remaster(ed)?|live|radio edit).*$", "", RegexOptions.IgnoreCase);
        return title.Trim();
    }

    private static string MainArtist(string artist) =>
        artist.Split([",", "&", " x ", " feat"], StringSplitOptions.None)[0].Trim();

    private async Task<string?> GetAsync(string url)
    {
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(8));
        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.TryAddWithoutValidation("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Wave");
        request.Headers.TryAddWithoutValidation("Accept", "application/json");
        using var response = await http.SendAsync(request, cts.Token);
        return response.IsSuccessStatusCode ? await response.Content.ReadAsStringAsync(cts.Token) : null;
    }

    private static string Query(params (string key, string? value)[] pairs) =>
        string.Join("&", pairs.Where(p => !string.IsNullOrEmpty(p.value)).Select(p => p.key + "=" + Uri.EscapeDataString(p.value!)));

    // ---- Better Lyrics ----

    private async Task<Lyrics?> FromBetterLyrics(string title, string artist, long durationMs, string? album)
    {
        var url = "https://lyrics-api.boidu.dev/getLyrics?" + Query(("s", CleanTitle(title)), ("a", MainArtist(artist)),
            ("d", durationMs > 0 ? (durationMs / 1000).ToString() : null), ("al", album));
        var body = await GetAsync(url);
        var ttml = body is null ? null : JsonNode.Parse(body)?["ttml"]?.GetValue<string>();
        if (string.IsNullOrWhiteSpace(ttml)) return null;
        var lines = ParseTtml(ttml);
        if (lines.Count == 0) return null;
        var wordTimed = lines.Any(l => l.Words.Count > 1);
        return new Lyrics(wordTimed ? lines : lines.Select(SpreadWords).ToList(), true, wordTimed, "Apple Music via Better Lyrics");
    }

    private static long ParseTime(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return -1;
        var parts = value.Trim().TrimEnd('s').Split(':');
        var seconds = double.Parse(parts[^1], System.Globalization.CultureInfo.InvariantCulture);
        long minutes = parts.Length >= 2 ? long.Parse(parts[^2]) : 0;
        long hours = parts.Length >= 3 ? long.Parse(parts[^3]) : 0;
        return (long)(((hours * 3600 + minutes * 60) + seconds) * 1000);
    }

    private static readonly Regex Token = new(@"<(/?)([\w:]+)([^>]*?)(/?)>|([^<]+)", RegexOptions.Compiled);

    /** Apple-style TTML: <p> lines with timed <span> words, x-bg spans for backing vocals. */
    private static List<LyricLine> ParseTtml(string ttml)
    {
        var lines = new List<LyricLine>();
        long lineStart = -1, lineEnd = -1, spanStart = -1, spanEnd = -1;
        var words = new List<LyricWord>();
        var background = new List<LyricWord>();
        bool inP = false, inWord = false;
        var bgDepth = 0;
        var spanText = new StringBuilder();

        List<LyricWord> Target() => bgDepth > 0 ? background : words;
        void MarkSpace()
        {
            var list = Target();
            if (list.Count > 0) list[^1] = list[^1] with { SpaceAfter = true };
        }
        static string? Attr(string attrs, string name) =>
            Regex.Match(attrs, @"(?:^|\s)" + Regex.Escape(name) + "=\"([^\"]*)\"") is { Success: true } m ? m.Groups[1].Value : null;

        foreach (Match token in Token.Matches(ttml))
        {
            if (token.Groups[5].Success)
            {
                if (!inP) continue;
                var t = WebUtility.HtmlDecode(token.Groups[5].Value);
                if (inWord) spanText.Append(t);
                else if (t.Length > 0 && string.IsNullOrWhiteSpace(t)) MarkSpace();
                else if (!string.IsNullOrWhiteSpace(t)) Target().Add(new LyricWord(lineStart, lineEnd, t.Trim(), t.EndsWith(' ')));
                continue;
            }
            var closing = token.Groups[1].Value == "/";
            var name = token.Groups[2].Value;
            var attrs = token.Groups[3].Value;
            var selfClosing = token.Groups[4].Value == "/";
            if (!closing)
            {
                if (name == "p")
                {
                    inP = true;
                    lineStart = ParseTime(Attr(attrs, "begin"));
                    lineEnd = ParseTime(Attr(attrs, "end"));
                    words = [];
                    background = [];
                }
                else if (name == "span" && inP && !selfClosing)
                {
                    if (Attr(attrs, "ttm:role") == "x-bg") bgDepth++;
                    else
                    {
                        spanStart = ParseTime(Attr(attrs, "begin"));
                        spanEnd = ParseTime(Attr(attrs, "end"));
                        spanText.Clear();
                        inWord = spanStart >= 0;
                    }
                }
            }
            else if (name == "span")
            {
                if (inWord)
                {
                    var raw = spanText.ToString();
                    if (!string.IsNullOrWhiteSpace(raw)) Target().Add(new LyricWord(spanStart, spanEnd, raw.Trim().Trim('(', ')'), raw.EndsWith(' ')));
                    inWord = false;
                }
                else if (bgDepth > 0) bgDepth--;
            }
            else if (name == "p")
            {
                inP = false;
                if (words.Count > 0 || background.Count > 0) lines.Add(new LyricLine(lineStart, lineEnd, words.ToList(), background.ToList()));
            }
        }
        return lines.OrderBy(l => l.StartMs).ToList();
    }

    // ---- LRCLIB ----

    private static readonly Regex Stamp = new(@"\[(\d+):(\d+)(?:[.:](\d{1,3}))?]", RegexOptions.Compiled);

    private async Task<Lyrics?> FromLrcLib(string title, string artist, long durationMs)
    {
        var track = CleanTitle(title);
        var name = MainArtist(artist);
        var candidates = new List<JsonNode>();
        if (await GetAsync("https://lrclib.net/api/get?" + Query(("track_name", track), ("artist_name", name), ("duration", durationMs > 0 ? (durationMs / 1000).ToString() : null))) is { } exact
            && JsonNode.Parse(exact) is { } one)
            candidates.Add(one);
        if (!candidates.Any(c => !string.IsNullOrWhiteSpace(c.Str("syncedLyrics"))))
        {
            if (await GetAsync("https://lrclib.net/api/search?" + Query(("track_name", track), ("artist_name", name))) is { } found && JsonNode.Parse(found) is JsonArray list)
                candidates.AddRange(list.Where(x => x is not null)!);
        }
        var seconds = durationMs / 1000.0;
        var best = candidates
            .Where(c => durationMs <= 0 || Math.Abs((c.Num("duration") ?? seconds) - seconds) < 6)
            .OrderByDescending(c => !string.IsNullOrWhiteSpace(c.Str("syncedLyrics")))
            .FirstOrDefault();
        if (best is null) return null;
        if (best.Str("syncedLyrics") is { Length: > 0 } lrc)
        {
            var lines = ParseLrc(lrc, durationMs);
            if (lines.Count > 0) return new Lyrics(lines, true, false, "LRCLIB");
        }
        return best.Str("plainLyrics") is { Length: > 0 } plain ? Plain(plain, "LRCLIB") : null;
    }

    private static List<LyricLine> ParseLrc(string lrc, long durationMs)
    {
        var timed = new List<(long start, string text)>();
        foreach (var raw in lrc.Split('\n'))
        {
            var stamps = Stamp.Matches(raw);
            if (stamps.Count == 0) continue;
            var last = stamps[^1];
            var text = raw[(last.Index + last.Length)..].Trim();
            foreach (Match m in stamps)
            {
                var frac = m.Groups[3].Value;
                long ms = frac.Length switch { 0 => 0, 1 => long.Parse(frac) * 100, 2 => long.Parse(frac) * 10, _ => long.Parse(frac[..3]) };
                timed.Add((long.Parse(m.Groups[1].Value) * 60_000 + long.Parse(m.Groups[2].Value) * 1000 + ms, text));
            }
        }
        timed.Sort((a, b) => a.start.CompareTo(b.start));
        var lines = new List<LyricLine>();
        for (var i = 0; i < timed.Count; i++)
        {
            var (start, text) = timed[i];
            var next = i + 1 < timed.Count ? timed[i + 1].start : durationMs > 0 ? durationMs : start + 5000;
            var end = Math.Min(next, start + Math.Max(1500, (long)((next - start) * 0.9)));
            lines.Add(SpreadWords(new LyricLine(start, end, [new LyricWord(start, end, text, false)])));
        }
        return lines;
    }

    /** Line-timed only: spread the line's time over its words by length. */
    private static LyricLine SpreadWords(LyricLine line)
    {
        var tokens = Regex.Split(line.Text, @"\s+").Where(t => t.Length > 0).ToList();
        if (tokens.Count <= 1) return line;
        double total = tokens.Sum(t => t.Length + 1);
        var span = Math.Max(line.EndMs - line.StartMs, tokens.Count * 120L);
        double t = line.StartMs;
        var words = new List<LyricWord>();
        for (var i = 0; i < tokens.Count; i++)
        {
            var d = span * (tokens[i].Length + 1) / total;
            words.Add(new LyricWord((long)t, (long)(t + d), tokens[i], i < tokens.Count - 1));
            t += d;
        }
        return line with { Words = words };
    }

    // ---- YouTube Music ----

    private async Task<Lyrics?> FromYouTube(string videoId)
    {
        var next = await youtube.NextAsync(videoId);
        if (next.LyricsBrowseId is null) return null;
        var text = await youtube.LyricsAsync(next.LyricsBrowseId);
        return string.IsNullOrWhiteSpace(text) ? null : Plain(text, "YouTube Music");
    }

    private static Lyrics Plain(string text, string source) =>
        new(text.Split('\n').Select(l => new LyricLine(-1, -1, [new LyricWord(-1, -1, l.TrimEnd('\r'), false)])).ToList(), false, false, source);
}

/** Album art at the size it's shown, so small covers don't download a full-size image. */
public static class Art
{
    private static readonly int[] Steps = [120, 226, 400, 544, 720, 1080];
    private static readonly Regex SizeParams = new(@"=w\d+-h\d+", RegexOptions.Compiled);
    private static readonly Regex YtImg = new(@"/(default|mqdefault|hqdefault|sddefault|maxresdefault)\.jpg", RegexOptions.Compiled);

    public const int Thumb = 226, Blur = 120, Card = 544, Full = 1080;

    public static string? AtSize(this string? url, int px)
    {
        if (url is null) return null;
        var step = Steps.FirstOrDefault(s => s >= px, Steps[^1]);
        if (SizeParams.IsMatch(url)) return SizeParams.Replace(url, $"=w{step}-h{step}");
        if (url.Contains("i.ytimg.com")) return YtImg.Replace(url, step <= 226 ? "/mqdefault.jpg" : "/maxresdefault.jpg");
        return url;
    }
}
