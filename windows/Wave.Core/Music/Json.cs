using System.Text.Json.Nodes;

namespace Wave.Music;

/** Walking YouTube's deeply nested JSON without a model class for every renderer. */
internal static class J
{
    /** Follows a path of keys (strings) and indexes (ints); null as soon as a step is missing. */
    public static JsonNode? At(this JsonNode? node, params object[] path)
    {
        foreach (var step in path)
        {
            if (node is null) return null;
            node = step switch
            {
                string key => node is JsonObject o && o.TryGetPropertyValue(key, out var v) ? v : null,
                int i => node is JsonArray a && i >= 0 && i < a.Count ? a[i] : (node is JsonArray b && i < 0 && b.Count + i >= 0 ? b[b.Count + i] : null),
                _ => null,
            };
        }
        return node;
    }

    public static string? Str(this JsonNode? node, params object[] path) =>
        node.At(path) is JsonValue v && v.TryGetValue<string>(out var s) ? s : null;

    public static bool Bool(this JsonNode? node, params object[] path) =>
        node.At(path) is JsonValue v && v.TryGetValue<bool>(out var b) && b;

    public static double? Num(this JsonNode? node, params object[] path)
    {
        if (node.At(path) is not JsonValue v) return null;
        if (v.TryGetValue<double>(out var d)) return d;
        if (v.TryGetValue<string>(out var s) && double.TryParse(s, System.Globalization.CultureInfo.InvariantCulture, out d)) return d;
        return null;
    }

    public static IEnumerable<JsonNode> Each(this JsonNode? node, params object[] path) =>
        node.At(path) is JsonArray a ? a.Where(x => x is not null).Select(x => x!) : [];

    /** The text of a "runs" object, joined. */
    public static string? Text(this JsonNode? runsHolder) =>
        runsHolder?["runs"] is JsonArray runs
            ? string.Concat(runs.Select(r => r.Str("text")))
            : runsHolder.Str("simpleText");

    public static IEnumerable<JsonNode> Runs(this JsonNode? runsHolder) => runsHolder.Each("runs");

    /** The biggest thumbnail of a thumbnail list holder. */
    public static string? Thumb(this JsonNode? holder)
    {
        foreach (var path in new object[][]
                 {
                     ["musicThumbnailRenderer", "thumbnail", "thumbnails"],
                     ["croppedSquareThumbnailRenderer", "thumbnail", "thumbnails"],
                     ["thumbnail", "thumbnails"],
                     ["thumbnails"],
                 })
        {
            if (holder.At(path) is JsonArray a && a.Count > 0) return Clean(a[^1].Str("url"));
        }
        return null;
    }

    private static string? Clean(string? url) => url is null ? null : url.StartsWith("//") ? "https:" + url : url;

    public static string? PageType(this JsonNode? endpoint) =>
        endpoint.Str("browseEndpoint", "browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType");

    /** "3:45" or "1:02:03" → milliseconds. */
    public static long? ParseDuration(string? text)
    {
        if (string.IsNullOrWhiteSpace(text)) return null;
        long total = 0;
        foreach (var part in text.Trim().Split(':'))
        {
            if (!long.TryParse(part, out var n)) return null;
            total = total * 60 + n;
        }
        return total * 1000;
    }
}
