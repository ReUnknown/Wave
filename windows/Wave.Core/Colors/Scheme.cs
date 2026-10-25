namespace Wave.Coloring;

/** An ARGB color. */
public readonly record struct Argb(uint Value)
{
    public byte A => (byte)(Value >> 24);
    public byte R => (byte)(Value >> 16);
    public byte G => (byte)(Value >> 8);
    public byte B => (byte)Value;

    public static Argb From(byte a, byte r, byte g, byte b) => new(((uint)a << 24) | ((uint)r << 16) | ((uint)g << 8) | b);
    public Argb WithAlpha(double alpha) => From((byte)Math.Round(Math.Clamp(alpha, 0, 1) * 255), R, G, B);

    /** This color (with its alpha) laid over an opaque [background]. */
    public Argb Over(Argb background)
    {
        var a = A / 255.0;
        byte Mix(byte f, byte b) => (byte)Math.Round(f * a + b * (1 - a));
        return From(255, Mix(R, background.R), Mix(G, background.G), Mix(B, background.B));
    }

    /** Relative luminance (0 dark .. 1 light). */
    public double Luminance
    {
        get
        {
            static double C(byte v) { var c = v / 255.0; return c <= 0.03928 ? c / 12.92 : Math.Pow((c + 0.055) / 1.055, 2.4); }
            return 0.2126 * C(R) + 0.7152 * C(G) + 0.0722 * C(B);
        }
    }

    public static Argb Lerp(Argb a, Argb b, double t)
    {
        byte L(byte x, byte y) => (byte)Math.Round(x + (y - x) * t);
        return From(L(a.A, b.A), L(a.R, b.R), L(a.G, b.G), L(a.B, b.B));
    }

    public static readonly Argb White = new(0xFFFFFFFF);
    public static readonly Argb Black = new(0xFF000000);
    public static readonly Argb Transparent = new(0x00000000);
}

/** The Material 3 colors Wave uses. */
public sealed record Scheme(
    Argb Primary,
    Argb OnPrimary,
    Argb PrimaryContainer,
    Argb OnPrimaryContainer,
    Argb SecondaryContainer,
    Argb OnSecondaryContainer,
    Argb TertiaryContainer,
    Argb OnTertiaryContainer,
    Argb Background,
    Argb OnBackground,
    Argb Surface,
    Argb OnSurface,
    Argb OnSurfaceVariant,
    Argb SurfaceContainer,
    Argb SurfaceContainerHigh,
    Argb SurfaceContainerHighest,
    Argb Outline,
    Argb OutlineVariant)
{
    public bool IsDark => Background.Luminance < 0.18;

    /** Pixel's "blue" dark scheme: Wave's colors before any song has colored them. */
    public static readonly Scheme DarkBlue = new(
        Primary: new(0xFFA0CAFD), OnPrimary: new(0xFF003258),
        PrimaryContainer: new(0xFF194975), OnPrimaryContainer: new(0xFFD1E4FF),
        SecondaryContainer: new(0xFF3B4858), OnSecondaryContainer: new(0xFFD7E3F7),
        TertiaryContainer: new(0xFF523F5F), OnTertiaryContainer: new(0xFFF2DAFF),
        Background: new(0xFF111418), OnBackground: new(0xFFE1E2E8),
        Surface: new(0xFF111418), OnSurface: new(0xFFE1E2E8), OnSurfaceVariant: new(0xFFC3C7CF),
        SurfaceContainer: new(0xFF1D2024), SurfaceContainerHigh: new(0xFF282A2F), SurfaceContainerHighest: new(0xFF32353A),
        Outline: new(0xFF8D9199), OutlineVariant: new(0xFF43474E));

    /** Material 3's baseline light scheme (light mode before song colors). */
    public static readonly Scheme Light = new(
        Primary: new(0xFF6750A4), OnPrimary: new(0xFFFFFFFF),
        PrimaryContainer: new(0xFFEADDFF), OnPrimaryContainer: new(0xFF4F378B),
        SecondaryContainer: new(0xFFE8DEF8), OnSecondaryContainer: new(0xFF4A4458),
        TertiaryContainer: new(0xFFFFD8E4), OnTertiaryContainer: new(0xFF633B48),
        Background: new(0xFFFEF7FF), OnBackground: new(0xFF1D1B20),
        Surface: new(0xFFFEF7FF), OnSurface: new(0xFF1D1B20), OnSurfaceVariant: new(0xFF49454F),
        SurfaceContainer: new(0xFFF3EDF7), SurfaceContainerHigh: new(0xFFECE6F0), SurfaceContainerHighest: new(0xFFE6E0E9),
        Outline: new(0xFF79747E), OutlineVariant: new(0xFFCAC4D0));

    public static Scheme Lerp(Scheme a, Scheme b, double t) => new(
        Argb.Lerp(a.Primary, b.Primary, t), Argb.Lerp(a.OnPrimary, b.OnPrimary, t),
        Argb.Lerp(a.PrimaryContainer, b.PrimaryContainer, t), Argb.Lerp(a.OnPrimaryContainer, b.OnPrimaryContainer, t),
        Argb.Lerp(a.SecondaryContainer, b.SecondaryContainer, t), Argb.Lerp(a.OnSecondaryContainer, b.OnSecondaryContainer, t),
        Argb.Lerp(a.TertiaryContainer, b.TertiaryContainer, t), Argb.Lerp(a.OnTertiaryContainer, b.OnTertiaryContainer, t),
        Argb.Lerp(a.Background, b.Background, t), Argb.Lerp(a.OnBackground, b.OnBackground, t),
        Argb.Lerp(a.Surface, b.Surface, t), Argb.Lerp(a.OnSurface, b.OnSurface, t), Argb.Lerp(a.OnSurfaceVariant, b.OnSurfaceVariant, t),
        Argb.Lerp(a.SurfaceContainer, b.SurfaceContainer, t), Argb.Lerp(a.SurfaceContainerHigh, b.SurfaceContainerHigh, t),
        Argb.Lerp(a.SurfaceContainerHighest, b.SurfaceContainerHighest, t),
        Argb.Lerp(a.Outline, b.Outline, t), Argb.Lerp(a.OutlineVariant, b.OutlineVariant, t));
}

/**
 * A color scheme pulled from album art, the way Wave does it everywhere: the colorful family that
 * covers the most of the cover sets the colors (a speck of blue never wins), and dark covers get
 * calmer colors.
 */
public static class ArtColors
{
    private sealed record Swatch(uint Rgb, int Population)
    {
        public readonly float[] Hsl = ToHsl(Rgb);
    }

    /** [pixels] ARGB (any size; ~128×128 is plenty). Null when the picture has no usable colors. */
    public static Scheme? FromPixels(ReadOnlySpan<uint> pixels, bool dark, Scheme? baseScheme = null)
    {
        var picked = PickSeed(Quantize(pixels));
        if (picked is null) return null;
        var (seed, mood) = picked.Value;
        var b = baseScheme ?? (dark ? Scheme.DarkBlue : Scheme.Light);
        return dark ? DarkScheme(new Argb(seed), mood, b) : LightScheme(new Argb(seed), b);
    }

    private static List<Swatch> Quantize(ReadOnlySpan<uint> pixels)
    {
        var count = new int[4096];
        var r = new long[4096];
        var g = new long[4096];
        var bl = new long[4096];
        foreach (var p in pixels)
        {
            if (p >> 24 < 128) continue;
            int pr = (int)(p >> 16) & 0xFF, pg = (int)(p >> 8) & 0xFF, pb = (int)p & 0xFF;
            var key = ((pr >> 4) << 8) | ((pg >> 4) << 4) | (pb >> 4);
            count[key]++;
            r[key] += pr;
            g[key] += pg;
            bl[key] += pb;
        }
        var swatches = new List<Swatch>();
        for (var k = 0; k < 4096; k++)
        {
            var n = count[k];
            if (n == 0) continue;
            swatches.Add(new Swatch(0xFF000000u | ((uint)(r[k] / n) << 16) | ((uint)(g[k] / n) << 8) | (uint)(bl[k] / n), n));
        }
        return swatches;
    }

    private static (uint seed, float mood)? PickSeed(List<Swatch> swatches)
    {
        float total = swatches.Sum(s => s.Population);
        if (total <= 0) return null;
        var mood = swatches.Sum(s => s.Population * s.Hsl[2]) / total;
        static float HueGap(float a, float b) { var d = Math.Abs(a - b); return d > 180 ? 360 - d : d; }
        var colorful = swatches.Where(s => s.Hsl[1] >= 0.22f && s.Hsl[2] is >= 0.12f and <= 0.88f).ToList();
        float FamilyShare(float hue) => colorful.Where(s => HueGap(s.Hsl[0], hue) <= 22f).Sum(s => s.Population) / total;
        var best = colorful
            .Where(s => s.Hsl[1] >= 0.28f && s.Hsl[2] is >= 0.18f and <= 0.82f)
            .Select(s => (s, share: FamilyShare(s.Hsl[0])))
            .Where(x => x.share >= 0.08f)
            .OrderByDescending(x => x.share * 0.6f + x.s.Hsl[1] * 0.25f + x.s.Population / total * 0.15f)
            .Select(x => x.s)
            .FirstOrDefault();
        var seed = best ?? swatches.MaxBy(s => s.Population);
        return seed is null ? null : (seed.Rgb, mood);
    }

    private static Argb Tone(Argb seed, float lightness, float maxSaturation)
    {
        var hsl = ToHsl(seed.Value);
        hsl[1] = Math.Min(hsl[1], maxSaturation);
        hsl[2] = lightness;
        return new Argb(FromHsl(hsl));
    }

    private static Argb RotateHue(Argb c, float degrees)
    {
        var hsl = ToHsl(c.Value);
        hsl[0] = (hsl[0] + degrees) % 360f;
        return new Argb(FromHsl(hsl));
    }

    private static Scheme DarkScheme(Argb seed, float mood, Scheme b)
    {
        var calm = Math.Clamp((mood - 0.12f) / 0.33f, 0f, 1f);
        var sat = 0.48f + 0.44f * calm;
        var background = Tone(seed, 0.09f, 0.42f * sat);
        var onBackground = Tone(seed, 0.93f, 0.3f * sat);
        return b with
        {
            Primary = Tone(seed, 0.6f + 0.17f * calm, 0.75f * sat),
            OnPrimary = Tone(seed, 0.16f, 0.85f * sat),
            PrimaryContainer = Tone(seed, 0.25f, 0.52f * sat),
            OnPrimaryContainer = Tone(seed, 0.9f, 0.7f * sat),
            SecondaryContainer = Tone(seed, 0.2f, 0.3f * sat),
            OnSecondaryContainer = Tone(seed, 0.88f, 0.35f * sat),
            TertiaryContainer = Tone(RotateHue(seed, 40f), 0.24f, 0.4f * sat),
            OnTertiaryContainer = Tone(RotateHue(seed, 40f), 0.9f, 0.55f * sat),
            Background = background,
            OnBackground = onBackground,
            Surface = background,
            OnSurface = onBackground,
            OnSurfaceVariant = Tone(seed, 0.78f, 0.22f * sat),
            SurfaceContainer = Argb.White.WithAlpha(0.06).Over(background),
            SurfaceContainerHigh = Argb.White.WithAlpha(0.1).Over(background),
            SurfaceContainerHighest = Argb.White.WithAlpha(0.14).Over(background),
        };
    }

    private static Scheme LightScheme(Argb seed, Scheme b)
    {
        var background = Tone(seed, 0.965f, 0.35f);
        var onBackground = Tone(seed, 0.11f, 0.3f);
        return b with
        {
            Primary = Tone(seed, 0.36f, 0.7f),
            OnPrimary = Tone(seed, 0.98f, 0.4f),
            PrimaryContainer = Tone(seed, 0.86f, 0.6f),
            OnPrimaryContainer = Tone(seed, 0.16f, 0.7f),
            SecondaryContainer = Tone(seed, 0.9f, 0.35f),
            OnSecondaryContainer = Tone(seed, 0.18f, 0.4f),
            TertiaryContainer = Tone(RotateHue(seed, 40f), 0.88f, 0.45f),
            OnTertiaryContainer = Tone(RotateHue(seed, 40f), 0.16f, 0.55f),
            Background = background,
            OnBackground = onBackground,
            Surface = background,
            OnSurface = onBackground,
            OnSurfaceVariant = Tone(seed, 0.34f, 0.18f),
            SurfaceContainer = Argb.Black.WithAlpha(0.04).Over(background),
            SurfaceContainerHigh = Argb.Black.WithAlpha(0.07).Over(background),
            SurfaceContainerHighest = Argb.Black.WithAlpha(0.1).Over(background),
        };
    }

    public static float[] ToHsl(uint color)
    {
        float rf = ((color >> 16) & 0xFF) / 255f, gf = ((color >> 8) & 0xFF) / 255f, bf = (color & 0xFF) / 255f;
        float max = Math.Max(rf, Math.Max(gf, bf)), min = Math.Min(rf, Math.Min(gf, bf));
        float delta = max - min, l = (max + min) / 2f, h, s;
        if (max == min) { h = 0; s = 0; }
        else
        {
            h = max == rf ? (gf - bf) / delta % 6f : max == gf ? (bf - rf) / delta + 2f : (rf - gf) / delta + 4f;
            s = delta / (1f - Math.Abs(2f * l - 1f));
        }
        h = h * 60f % 360f;
        if (h < 0) h += 360f;
        return [Math.Clamp(h, 0, 360), Math.Clamp(s, 0, 1), Math.Clamp(l, 0, 1)];
    }

    public static uint FromHsl(float[] hsl)
    {
        float h = hsl[0], s = hsl[1], l = hsl[2];
        var c = (1f - Math.Abs(2 * l - 1f)) * s;
        var m = l - 0.5f * c;
        var x = c * (1f - Math.Abs(h / 60f % 2f - 1f));
        var (r, g, b) = ((int)h / 60) switch
        {
            0 => (c + m, x + m, m),
            1 => (x + m, c + m, m),
            2 => (m, c + m, x + m),
            3 => (m, x + m, c + m),
            4 => (x + m, m, c + m),
            _ => (c + m, m, x + m),
        };
        static uint Ch(float v) => (uint)Math.Clamp((int)Math.Round(v * 255), 0, 255);
        return 0xFF000000u | (Ch(r) << 16) | (Ch(g) << 8) | Ch(b);
    }
}
