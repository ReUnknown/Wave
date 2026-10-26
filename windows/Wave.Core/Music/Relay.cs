using System.Net;
using System.Net.Sockets;
using System.Text;

namespace Wave.Music;

/**
 * A tiny HTTP relay on 127.0.0.1 so the system's player can stream a song straight away (and
 * seek in it) while Wave does the talking to YouTube: each request (with its byte range) is
 * fetched by Wave's own HttpClient, which YouTube accepts, and passed through. Only this
 * computer can reach it, and only for songs Wave has asked to play.
 */
public sealed class Relay : IDisposable
{
    private readonly HttpClient http;
    private readonly TcpListener listener = new(IPAddress.Loopback, 0);
    private readonly Dictionary<string, (string url, string type)> songs = new();
    private readonly CancellationTokenSource stop = new();
    private readonly string token = Guid.NewGuid().ToString("N");

    public int Port { get; }

    public Relay(HttpClient http)
    {
        this.http = http;
        listener.Start();
        Port = ((IPEndPoint)listener.LocalEndpoint).Port;
        _ = AcceptAsync();
    }

    /** The local address that plays [remoteUrl]. */
    public string Expose(string videoId, string remoteUrl, string contentType)
    {
        lock (songs) songs[videoId] = (remoteUrl, contentType);
        return $"http://127.0.0.1:{Port}/{token}/{videoId}";
    }

    private async Task AcceptAsync()
    {
        while (!stop.IsCancellationRequested)
        {
            TcpClient client;
            try { client = await listener.AcceptTcpClientAsync(stop.Token); }
            catch (Exception) { return; }
            _ = Task.Run(() => ServeAsync(client));
        }
    }

    private async Task ServeAsync(TcpClient client)
    {
        using var _ = client;
        try
        {
            var stream = client.GetStream();
            var head = await ReadHeadAsync(stream);
            if (head is null) return;
            var lines = head.Split("\r\n");
            var request = lines[0].Split(' ');
            var parts = request.Length > 1 ? request[1].Trim('/').Split('/') : [];
            (string url, string type) song = default;
            bool known;
            lock (songs) known = parts.Length == 2 && parts[0] == token && songs.TryGetValue(parts[1], out song);
            if (!known)
            {
                await WriteAsync(stream, "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
                return;
            }
            var range = lines.FirstOrDefault(l => l.StartsWith("Range:", StringComparison.OrdinalIgnoreCase))?[6..].Trim();
            using var upstream = new HttpRequestMessage(request[0] == "HEAD" ? HttpMethod.Head : HttpMethod.Get, song.url);
            if (range is not null) upstream.Headers.TryAddWithoutValidation("Range", range);
            using var response = await http.SendAsync(upstream, HttpCompletionOption.ResponseHeadersRead, stop.Token);
            var status = (int)response.StatusCode;
            var reply = new StringBuilder($"HTTP/1.1 {status} {response.ReasonPhrase}\r\n");
            reply.Append($"Content-Type: {song.type}\r\nAccept-Ranges: bytes\r\nConnection: close\r\n");
            if (response.Content.Headers.ContentLength is { } length) reply.Append($"Content-Length: {length}\r\n");
            if (response.Content.Headers.ContentRange is { } contentRange) reply.Append($"Content-Range: {contentRange}\r\n");
            reply.Append("\r\n");
            await WriteAsync(stream, reply.ToString());
            if (request[0] != "HEAD") await response.Content.CopyToAsync(stream, stop.Token);
        }
        catch (Exception)
        {
            // The player hung up (a seek or a skip): nothing to do.
        }
    }

    private static async Task<string?> ReadHeadAsync(NetworkStream stream)
    {
        var buffer = new byte[8192];
        var total = 0;
        while (total < buffer.Length)
        {
            var n = await stream.ReadAsync(buffer.AsMemory(total));
            if (n == 0) return null;
            total += n;
            var text = Encoding.ASCII.GetString(buffer, 0, total);
            var end = text.IndexOf("\r\n\r\n", StringComparison.Ordinal);
            if (end >= 0) return text[..end];
        }
        return null;
    }

    private static Task WriteAsync(NetworkStream stream, string text) => stream.WriteAsync(Encoding.ASCII.GetBytes(text)).AsTask();

    public void Dispose()
    {
        stop.Cancel();
        listener.Stop();
    }
}
