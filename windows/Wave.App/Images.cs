using System.Collections.Concurrent;
using System.Runtime.InteropServices.WindowsRuntime;
using Microsoft.UI.Composition;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Hosting;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Wave.Coloring;
using Wave.Music;
using Windows.Graphics.Imaging;
using Windows.Storage.Streams;

namespace Wave.App;

/**
 * Pictures, done cheaply: covers decoded at the size they're shown, blurred backdrops blurred once
 * into a tiny bitmap (never again per frame), and song colors read from a 128 px copy.
 */
public static class Images
{
    private static readonly HttpClient Http = new();
    private static readonly ConcurrentDictionary<string, Task<byte[]?>> Downloads = new();
    private static readonly Lru<string, WriteableBitmap> Blurred = new(48);
    private static readonly Lru<string, Scheme> Schemes = new(96);

    private static Task<byte[]?> BytesAsync(string url) => Downloads.GetOrAdd(url, u => Task.Run(async () =>
    {
        try { return await Http.GetByteArrayAsync(u); }
        catch (Exception) { Downloads.TryRemove(u, out Task<byte[]?>? _); return null; }
        finally { if (Downloads.Count > 64) Downloads.Clear(); }
    }));

    /** BGRA pixels of [url] squeezed into [size]×[size]. */
    private static async Task<byte[]?> PixelsAsync(string url, int size)
    {
        var bytes = await BytesAsync(url);
        if (bytes is null) return null;
        try
        {
            using var stream = new InMemoryRandomAccessStream();
            await stream.WriteAsync(bytes.AsBuffer());
            stream.Seek(0);
            var decoder = await BitmapDecoder.CreateAsync(stream);
            var data = await decoder.GetPixelDataAsync(BitmapPixelFormat.Bgra8, BitmapAlphaMode.Premultiplied,
                new BitmapTransform { ScaledWidth = (uint)size, ScaledHeight = (uint)size, InterpolationMode = BitmapInterpolationMode.Fant },
                ExifOrientationMode.IgnoreExifOrientation, ColorManagementMode.DoNotColorManage);
            return data.DetachPixelData();
        }
        catch (Exception) { return null; }
    }

    /** Colors from a cover (dark or light), or null if it has none to give. */
    public static async Task<Scheme?> SchemeAsync(string? url, bool dark)
    {
        if (url is null) return null;
        var key = url.AtSize(Art.Thumb) + "|" + dark;
        if (Schemes.Get(key) is { } hit) return hit;
        var px = await PixelsAsync(url.AtSize(Art.Thumb)!, 96);
        if (px is null) return null;
        var scheme = await Task.Run(() =>
        {
            var argb = new uint[px.Length / 4];
            for (var i = 0; i < argb.Length; i++)
                argb[i] = 0xFF000000u | ((uint)px[i * 4 + 2] << 16) | ((uint)px[i * 4 + 1] << 8) | px[i * 4];
            return ArtColors.FromPixels(argb, dark, dark ? Scheme.DarkBlue : Scheme.Light);
        });
        if (scheme is not null) Schemes.Put(key, scheme);
        return scheme;
    }

    /** [url] blurred by [radius] px (on a 64 px copy), as a bitmap to draw scaled up. */
    public static async Task<WriteableBitmap?> BlurredAsync(string? url, int radius)
    {
        if (url is null) return null;
        var key = url + "|" + radius;
        if (Blurred.Get(key) is { } hit) return hit;
        const int size = 64;
        var px = await PixelsAsync(url.AtSize(Art.Blur)!, size);
        if (px is null) return null;
        await Task.Run(() => BoxBlur(px, size, size, radius));
        var bitmap = new WriteableBitmap(size, size);
        using (var buffer = bitmap.PixelBuffer.AsStream()) await buffer.WriteAsync(px);
        bitmap.Invalidate();
        Blurred.Put(key, bitmap);
        return bitmap;
    }

    /** Three box blurs ≈ a Gaussian blur; edges clamp, so they never darken. */
    private static void BoxBlur(byte[] px, int w, int h, int r)
    {
        if (r <= 0) return;
        var tmp = new byte[px.Length];
        for (var pass = 0; pass < 3; pass++)
        {
            Horizontal(px, tmp, w, h, r);
            Vertical(tmp, px, w, h, r);
        }
    }

    private static void Horizontal(byte[] src, byte[] dst, int w, int h, int r)
    {
        var n = 2 * r + 1;
        for (var y = 0; y < h; y++)
            for (var c = 0; c < 4; c++)
            {
                int sum = 0;
                for (var i = -r; i <= r; i++) sum += src[(y * w + Math.Clamp(i, 0, w - 1)) * 4 + c];
                for (var x = 0; x < w; x++)
                {
                    dst[(y * w + x) * 4 + c] = (byte)(sum / n);
                    sum += src[(y * w + Math.Min(x + r + 1, w - 1)) * 4 + c] - src[(y * w + Math.Max(x - r, 0)) * 4 + c];
                }
            }
    }

    private static void Vertical(byte[] src, byte[] dst, int w, int h, int r)
    {
        var n = 2 * r + 1;
        for (var x = 0; x < w; x++)
            for (var c = 0; c < 4; c++)
            {
                int sum = 0;
                for (var i = -r; i <= r; i++) sum += src[(Math.Clamp(i, 0, h - 1) * w + x) * 4 + c];
                for (var y = 0; y < h; y++)
                {
                    dst[(y * w + x) * 4 + c] = (byte)(sum / n);
                    sum += src[(Math.Min(y + r + 1, h - 1) * w + x) * 4 + c] - src[(Math.Max(y - r, 0) * w + x) * 4 + c];
                }
            }
    }

    /** A cover decoded at the size it's drawn (not the full image). */
    public static BitmapImage? Bitmap(string? url, int px)
    {
        var sized = url.AtSize(px);
        if (sized is null) return null;
        return new BitmapImage(new Uri(sized)) { DecodePixelWidth = px, DecodePixelType = DecodePixelType.Logical };
    }

    public static ImageBrush? Brush(string? url, int px) =>
        Bitmap(url, px) is { } b ? new ImageBrush { ImageSource = b, Stretch = Stretch.UniformToFill } : null;
}

/** A blurred backdrop of a cover that crossfades when the cover changes. */
public sealed class BlurredArt : Grid
{
    private readonly Image a = new() { Stretch = Stretch.UniformToFill };
    private readonly Image b = new() { Stretch = Stretch.UniformToFill, Opacity = 0 };
    private bool showingA = true;
    private string? url;
    private readonly int radius;

    public BlurredArt(int radius = 8)
    {
        this.radius = radius;
        Children.Add(a);
        Children.Add(b);
        IsHitTestVisible = false;
    }

    public string? Url
    {
        get => url;
        set
        {
            if (value == url) return;
            url = value;
            _ = LoadAsync(value);
        }
    }

    private async Task LoadAsync(string? value)
    {
        var bitmap = await Images.BlurredAsync(value, radius);
        if (value != url) return;
        var next = showingA ? b : a;
        var current = showingA ? a : b;
        next.Source = bitmap;
        Motion.Fade(next, 1, 320);
        Motion.Fade(current, 0, 320);
        showingA = !showingA;
    }
}

/**
 * A picture with a soft fade (or blur ramp) to transparent along one direction, drawn by the
 * compositor (WinUI has no opacity masks): a surface brush masked by a gradient.
 */
public sealed class FadedArt : Grid
{
    private readonly Compositor compositor;
    private readonly SpriteVisual visual;
    private readonly CompositionMaskBrush mask;
    private readonly CompositionLinearGradientBrush gradient;
    private readonly CompositionSurfaceBrush surface;
    private string? url;

    /** Fade from [from] (fully shown before it) to [to] (gone after), as fractions along the axis. */
    public FadedArt(bool horizontal, float from, float to, bool reverse = false)
    {
        IsHitTestVisible = false;
        compositor = ElementCompositionPreview.GetElementVisual(this).Compositor;
        surface = compositor.CreateSurfaceBrush();
        surface.Stretch = CompositionStretch.UniformToFill;
        gradient = compositor.CreateLinearGradientBrush();
        gradient.StartPoint = new(0, 0);
        gradient.EndPoint = horizontal ? new(1, 0) : new(0, 1);
        var opaque = Windows.UI.Color.FromArgb(255, 0, 0, 0);
        var clear = Windows.UI.Color.FromArgb(0, 0, 0, 0);
        gradient.ColorStops.Add(compositor.CreateColorGradientStop(from, reverse ? clear : opaque));
        gradient.ColorStops.Add(compositor.CreateColorGradientStop(to, reverse ? opaque : clear));
        mask = compositor.CreateMaskBrush();
        mask.Source = surface;
        mask.Mask = gradient;
        visual = compositor.CreateSpriteVisual();
        visual.Brush = mask;
        ElementCompositionPreview.SetElementChildVisual(this, visual);
        SizeChanged += (_, e) => visual.Size = new((float)e.NewSize.Width, (float)e.NewSize.Height);
    }

    public string? Url
    {
        get => url;
        set
        {
            if (value == url) return;
            url = value;
            surface.Surface = value is null ? null : LoadedImageSurface.StartLoadFromUri(new Uri(value));
        }
    }

    /** A blurred copy instead of the sharp picture (for blur ramps). */
    public async Task SetBlurredAsync(string? value, int radius)
    {
        url = value;
        var bitmap = await Images.BlurredAsync(value, radius);
        if (url != value || bitmap is null) return;
        var stream = new InMemoryRandomAccessStream();
        var encoder = await BitmapEncoder.CreateAsync(BitmapEncoder.PngEncoderId, stream);
        var pixels = new byte[bitmap.PixelWidth * bitmap.PixelHeight * 4];
        using (var s = bitmap.PixelBuffer.AsStream()) await s.ReadAsync(pixels);
        encoder.SetPixelData(BitmapPixelFormat.Bgra8, BitmapAlphaMode.Premultiplied, (uint)bitmap.PixelWidth, (uint)bitmap.PixelHeight, 96, 96, pixels);
        await encoder.FlushAsync();
        stream.Seek(0);
        if (url == value) surface.Surface = LoadedImageSurface.StartLoadFromStream(stream);
    }
}

/** A small least-recently-used cache. */
public sealed class Lru<TKey, TValue>(int max) where TKey : notnull
{
    private readonly Dictionary<TKey, LinkedListNode<(TKey key, TValue value)>> map = new();
    private readonly LinkedList<(TKey key, TValue value)> order = new();

    public TValue? Get(TKey key)
    {
        lock (map)
        {
            if (!map.TryGetValue(key, out var node)) return default;
            order.Remove(node);
            order.AddFirst(node);
            return node.Value.value;
        }
    }

    public void Put(TKey key, TValue value)
    {
        lock (map)
        {
            if (map.TryGetValue(key, out var old)) order.Remove(old);
            map[key] = order.AddFirst((key, value));
            while (map.Count > max)
            {
                var last = order.Last!;
                order.RemoveLast();
                map.Remove(last.Value.key);
            }
        }
    }
}
