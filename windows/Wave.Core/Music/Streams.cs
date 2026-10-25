using YoutubeExplode;
using YoutubeExplode.Videos.Streams;

namespace Wave.Music;

/** A playable audio link for a song. [Bitrate] in bits per second. */
public sealed record AudioStream(string Url, string Container, long Bitrate, long SizeBytes);

/**
 * Turns a song into a playable audio link, through YoutubeExplode (which keeps up with YouTube's
 * link signing). AAC in MP4 is preferred: Windows plays it everywhere without extra codecs.
 * Links are kept for a few minutes, so replaying or prefetching doesn't ask again.
 */
public sealed class Streams(HttpClient http)
{
    private readonly YoutubeClient youtube = new(http);
    private readonly Dictionary<string, (DateTime at, AudioStream stream)> cache = new();
    private static readonly TimeSpan Fresh = TimeSpan.FromMinutes(20);

    public async Task<AudioStream> ResolveAsync(string videoId, CancellationToken ct = default)
    {
        lock (cache)
        {
            if (cache.TryGetValue(videoId, out var hit) && DateTime.UtcNow - hit.at < Fresh) return hit.stream;
        }
        var manifest = await youtube.Videos.Streams.GetManifestAsync(videoId, ct);
        var audio = manifest.GetAudioOnlyStreams().ToList();
        var pick = audio.Where(s => s.Container == Container.Mp4).OrderByDescending(s => s.Bitrate.BitsPerSecond).FirstOrDefault()
                   ?? audio.OrderByDescending(s => s.Bitrate.BitsPerSecond).FirstOrDefault()
                   ?? throw new InvalidOperationException("No audio for this song");
        var stream = new AudioStream(pick.Url, pick.Container.Name, (long)pick.Bitrate.BitsPerSecond, pick.Size.Bytes);
        lock (cache) cache[videoId] = (DateTime.UtcNow, stream);
        return stream;
    }

    /** Forget a link that stopped working, so the next try asks for a fresh one. */
    public void Forget(string videoId)
    {
        lock (cache) cache.Remove(videoId);
    }
}
