using System.Collections.Concurrent;
using YoutubeExplode;
using YoutubeExplode.Videos.Streams;

namespace Wave.Music;

/** Where a song's audio comes from: a file on this computer, or a link the system player can open. */
public sealed record AudioStream(string Source, bool IsFile, string Container, long Bitrate);

/**
 * Gets songs ready to play. YouTube's links only work for the client that asked for them, so the
 * system player never gets one directly: a song starts at once through [Relay] (Wave fetches,
 * the player streams and seeks), and the next song is downloaded ahead into a small cache, so
 * skipping is instant and replays don't use the network. AAC in MP4 is preferred: Windows
 * plays it everywhere without extra codecs.
 */
public sealed class Streams(HttpClient http, string cacheDir)
{
    private readonly YoutubeClient youtube = new(http);
    private readonly Relay relay = new(http);
    private readonly ConcurrentDictionary<string, (DateTime at, IStreamInfo info)> picked = new();
    private readonly ConcurrentDictionary<string, Task> downloading = new();
    private static readonly TimeSpan Fresh = TimeSpan.FromMinutes(20);
    /** Songs kept on disk (about 5 MB each). */
    private const int Keep = 40;

    /** Pick the smallest audio (data saver) rather than the best. */
    public bool LowData { get; set; }

    /** Ready to play: the cached file if there is one, otherwise streamed through the relay. */
    public async Task<AudioStream> ResolveAsync(string videoId, CancellationToken ct = default)
    {
        if (Cached(videoId) is { } file) return file;
        var info = await PickAsync(videoId, ct);
        var type = info.Container == Container.Mp4 ? "audio/mp4" : "audio/webm";
        return new AudioStream(relay.Expose(videoId, info.Url, type), false, info.Container.Name, (long)info.Bitrate.BitsPerSecond);
    }

    /** Downloads [videoId] into the cache in the background (the next song). */
    public Task PrefetchAsync(string videoId) =>
        Cached(videoId) is not null ? Task.CompletedTask : downloading.GetOrAdd(videoId, id => Task.Run(async () =>
        {
            try
            {
                Directory.CreateDirectory(cacheDir);
                var info = await PickAsync(id, default);
                var path = Path.Combine(cacheDir, $"{id}.{info.Container.Name}");
                var part = path + ".part";
                await youtube.Videos.Streams.DownloadAsync(info, part);
                File.Move(part, path, overwrite: true);
                Trim();
            }
            catch (Exception) { }
            finally { downloading.TryRemove(id, out Task? _); }
        }));

    private async Task<IStreamInfo> PickAsync(string videoId, CancellationToken ct)
    {
        if (picked.TryGetValue(videoId, out var hit) && DateTime.UtcNow - hit.at < Fresh) return hit.info;
        var manifest = await youtube.Videos.Streams.GetManifestAsync(videoId, ct);
        var audio = manifest.GetAudioOnlyStreams().ToList();
        var mp4 = audio.Where(s => s.Container == Container.Mp4).OrderBy(s => s.Bitrate.BitsPerSecond).ToList();
        var any = audio.OrderBy(s => s.Bitrate.BitsPerSecond).ToList();
        var pick = (LowData ? mp4.FirstOrDefault() ?? any.FirstOrDefault() : mp4.LastOrDefault() ?? any.LastOrDefault())
                   ?? throw new InvalidOperationException("No audio for this song");
        picked[videoId] = (DateTime.UtcNow, pick);
        return pick;
    }

    private AudioStream? Cached(string videoId)
    {
        if (!Directory.Exists(cacheDir)) return null;
        var file = Directory.EnumerateFiles(cacheDir, videoId + ".*").FirstOrDefault(f => !f.EndsWith(".part"));
        if (file is null || new FileInfo(file).Length == 0) return null;
        try { File.SetLastAccessTimeUtc(file, DateTime.UtcNow); } catch (Exception) { }
        return new AudioStream(file, true, Path.GetExtension(file).TrimStart('.'), 0);
    }

    /** Keeps the cache small: the least recently played songs go first. */
    private void Trim()
    {
        try
        {
            var files = new DirectoryInfo(cacheDir).GetFiles().Where(f => f.Extension != ".part").OrderByDescending(f => f.LastAccessTimeUtc).ToList();
            foreach (var old in files.Skip(Keep)) old.Delete();
        }
        catch (Exception) { }
    }

    /** A song that wouldn't play: forget its link and file, so the next try starts fresh. */
    public void Forget(string videoId)
    {
        picked.TryRemove(videoId, out _);
        try
        {
            if (Directory.Exists(cacheDir)) foreach (var f in Directory.EnumerateFiles(cacheDir, videoId + ".*")) File.Delete(f);
        }
        catch (Exception) { }
    }
}
