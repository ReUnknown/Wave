using System.Net;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace Wave.Music;

/**
 * YouTube Music's own API (InnerTube), spoken the way music.youtube.com speaks it: the WEB_REMIX
 * client, your session cookie (signed with SAPISIDHASH) when you're signed in, a visitor id
 * when you're not. The client version is read from YouTube Music itself at startup, so Wave
 * keeps matching the website without updates.
 */
public sealed class InnerTube
{
    private const string Origin = "https://music.youtube.com";
    private const string UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";

    /** Used until the live version has been read (and if that ever fails). */
    public string ClientVersion { get; private set; } = "1.20261020.10.00";

    public string Language { get; set; } = "en";
    public string Region { get; set; } = "US";

    public string? Cookie { get; private set; }
    public string? VisitorData { get; set; }
    public string? DataSyncId { get; private set; }
    public string AuthUser { get; private set; } = "0";
    public bool SignedIn => !string.IsNullOrEmpty(Cookie) && SapiSid is not null;

    private string? SapiSid;

    public HttpClient Http { get; }

    public InnerTube(HttpClient? http = null)
    {
        Http = http ?? new HttpClient(new SocketsHttpHandler
        {
            AutomaticDecompression = DecompressionMethods.All,
            UseCookies = false,
            PooledConnectionLifetime = TimeSpan.FromMinutes(10),
        })
        { Timeout = TimeSpan.FromSeconds(20) };
    }

    /** Points requests at an account (or back to signed-out with nulls). */
    public void UseSession(string? cookie, string? visitorData, string? dataSyncId, string authUser)
    {
        Cookie = string.IsNullOrWhiteSpace(cookie) ? null : cookie;
        SapiSid = Cookie is null ? null : ReadCookie(Cookie, "SAPISID") ?? ReadCookie(Cookie, "__Secure-3PAPISID");
        if (!string.IsNullOrWhiteSpace(visitorData)) VisitorData = visitorData;
        DataSyncId = string.IsNullOrWhiteSpace(dataSyncId) ? null : dataSyncId.Split("||")[0];
        AuthUser = string.IsNullOrWhiteSpace(authUser) ? "0" : authUser;
    }

    private static string? ReadCookie(string cookie, string name) =>
        cookie.Split(';').Select(p => p.Trim()).FirstOrDefault(p => p.StartsWith(name + "="))?[(name.Length + 1)..];

    /** Reads the current client version and a visitor id from YouTube Music's page. */
    public async Task WarmUpAsync(CancellationToken ct = default)
    {
        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, Origin + "/");
            request.Headers.TryAddWithoutValidation("User-Agent", UserAgent);
            request.Headers.TryAddWithoutValidation("Accept-Language", Language);
            if (Cookie is not null) request.Headers.TryAddWithoutValidation("Cookie", Cookie);
            using var response = await Http.SendAsync(request, ct);
            var html = await response.Content.ReadAsStringAsync(ct);
            var version = Regex.Match(html, "\"INNERTUBE_CLIENT_VERSION\":\"([0-9.]+)\"");
            if (version.Success) ClientVersion = version.Groups[1].Value;
            if (VisitorData is null)
            {
                var visitor = Regex.Match(html, "\"VISITOR_DATA\":\"([^\"]+)\"");
                if (visitor.Success) VisitorData = visitor.Groups[1].Value;
            }
        }
        catch (Exception) when (!ct.IsCancellationRequested)
        {
            // Offline or blocked: the built-in version still works.
        }
    }

    /** POSTs to youtubei/v1/[endpoint] with Wave's context added to [body]. */
    public async Task<JsonNode> PostAsync(string endpoint, JsonObject body, CancellationToken ct = default, string? query = null)
    {
        var client = new JsonObject
        {
            ["clientName"] = "WEB_REMIX",
            ["clientVersion"] = ClientVersion,
            ["hl"] = Language,
            ["gl"] = Region,
        };
        if (VisitorData is not null) client["visitorData"] = VisitorData;
        var context = new JsonObject { ["client"] = client };
        if (DataSyncId is not null && SignedIn) context["user"] = new JsonObject { ["onBehalfOfUser"] = DataSyncId };
        body["context"] = context;

        var url = $"{Origin}/youtubei/v1/{endpoint}?prettyPrint=false" + (query is null ? "" : "&" + query);
        for (var attempt = 0; ; attempt++)
        {
            using var request = new HttpRequestMessage(HttpMethod.Post, url)
            {
                Content = new StringContent(body.ToJsonString(), Encoding.UTF8, "application/json"),
            };
            AddHeaders(request.Headers);
            using var response = await Http.SendAsync(request, ct);
            if ((int)response.StatusCode >= 500 && attempt < 2)
            {
                await Task.Delay(400 * (attempt + 1), ct);
                continue;
            }
            var text = await response.Content.ReadAsStringAsync(ct);
            if (!response.IsSuccessStatusCode) throw new HttpRequestException($"YouTube Music {endpoint}: HTTP {(int)response.StatusCode}", null, response.StatusCode);
            return JsonNode.Parse(text) ?? throw new HttpRequestException($"YouTube Music {endpoint}: empty answer");
        }
    }

    private void AddHeaders(HttpRequestHeaders headers)
    {
        headers.TryAddWithoutValidation("User-Agent", UserAgent);
        headers.TryAddWithoutValidation("Origin", Origin);
        headers.TryAddWithoutValidation("Referer", Origin + "/");
        headers.TryAddWithoutValidation("X-Origin", Origin);
        headers.TryAddWithoutValidation("X-Youtube-Client-Name", "67");
        headers.TryAddWithoutValidation("X-Youtube-Client-Version", ClientVersion);
        headers.TryAddWithoutValidation("Accept-Language", Language);
        if (VisitorData is not null) headers.TryAddWithoutValidation("X-Goog-Visitor-Id", VisitorData);
        if (Cookie is not null) headers.TryAddWithoutValidation("Cookie", Cookie);
        if (SapiSid is not null)
        {
            headers.TryAddWithoutValidation("Authorization", SapiSidHash(SapiSid));
            headers.TryAddWithoutValidation("X-Goog-AuthUser", AuthUser);
        }
    }

    /** Google's request signature for a signed-in session. */
    private static string SapiSidHash(string sapisid)
    {
        var now = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        var hash = SHA1.HashData(Encoding.UTF8.GetBytes($"{now} {sapisid} {Origin}"));
        return $"SAPISIDHASH {now}_{Convert.ToHexStringLower(hash)}";
    }

    /** A plain GET with YouTube Music's headers and your session (for playback tracking pings). */
    public async Task<HttpResponseMessage> GetAsync(string url, CancellationToken ct = default)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        AddHeaders(request.Headers);
        return await Http.SendAsync(request, ct);
    }
}
