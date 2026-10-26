using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media;
using Wave.Coloring;
using Windows.UI;

namespace Wave.App;

/**
 * A set of Material colors as live brushes. Everything drawn with a palette's brushes changes
 * with it, and [Set] fades from one song's colors to the next instead of jumping.
 */
public sealed class Palette
{
    public SolidColorBrush Primary { get; } = new();
    public SolidColorBrush OnPrimary { get; } = new();
    public SolidColorBrush PrimaryContainer { get; } = new();
    public SolidColorBrush OnPrimaryContainer { get; } = new();
    public SolidColorBrush SecondaryContainer { get; } = new();
    public SolidColorBrush OnSecondaryContainer { get; } = new();
    public SolidColorBrush TertiaryContainer { get; } = new();
    public SolidColorBrush OnTertiaryContainer { get; } = new();
    public SolidColorBrush Background { get; } = new();
    public SolidColorBrush OnBackground { get; } = new();
    public SolidColorBrush OnSurfaceVariant { get; } = new();
    public SolidColorBrush SurfaceContainer { get; } = new();
    public SolidColorBrush SurfaceContainerHigh { get; } = new();
    public SolidColorBrush SurfaceContainerHighest { get; } = new();
    public SolidColorBrush OutlineVariant { get; } = new();
    public SolidColorBrush Outline { get; } = new();
    /** onBackground at 12% (buttons and chips on the background), 6%, and 18%. */
    public SolidColorBrush Faint { get; } = new();
    public SolidColorBrush Fainter { get; } = new();
    public SolidColorBrush Track { get; } = new();
    /** What darkens (dark mode) or lightens (light mode) a busy picture under text. */
    public SolidColorBrush Scrim { get; } = new();

    public Scheme Scheme { get; private set; }
    public event Action<Scheme>? Changed;

    private Scheme from;
    private Scheme to;
    private DateTime started;
    private bool animating;

    public Palette(Scheme scheme)
    {
        Scheme = from = to = scheme;
        Paint(scheme);
    }

    public bool IsDark => Scheme.IsDark;

    /** Fades to [scheme] over ~0.4 s (or jumps, for the first colors of a new page). */
    public void Set(Scheme scheme, bool animate = true)
    {
        if (scheme == to) return;
        to = scheme;
        if (!animate)
        {
            from = scheme;
            Paint(scheme);
            return;
        }
        from = Scheme;
        started = DateTime.UtcNow;
        if (animating) return;
        animating = true;
        CompositionTarget.Rendering += Step;
    }

    private void Step(object? sender, object e)
    {
        var t = Math.Min(1, (DateTime.UtcNow - started).TotalMilliseconds / 420);
        var eased = 1 - Math.Pow(1 - t, 3);
        Paint(Scheme.Lerp(from, to, eased));
        if (t >= 1)
        {
            CompositionTarget.Rendering -= Step;
            animating = false;
        }
    }

    private void Paint(Scheme s)
    {
        Scheme = s;
        Primary.Color = C(s.Primary);
        OnPrimary.Color = C(s.OnPrimary);
        PrimaryContainer.Color = C(s.PrimaryContainer);
        OnPrimaryContainer.Color = C(s.OnPrimaryContainer);
        SecondaryContainer.Color = C(s.SecondaryContainer);
        OnSecondaryContainer.Color = C(s.OnSecondaryContainer);
        TertiaryContainer.Color = C(s.TertiaryContainer);
        OnTertiaryContainer.Color = C(s.OnTertiaryContainer);
        Background.Color = C(s.Background);
        OnBackground.Color = C(s.OnBackground);
        OnSurfaceVariant.Color = C(s.OnSurfaceVariant);
        SurfaceContainer.Color = C(s.SurfaceContainer);
        SurfaceContainerHigh.Color = C(s.SurfaceContainerHigh);
        SurfaceContainerHighest.Color = C(s.SurfaceContainerHighest);
        OutlineVariant.Color = C(s.OutlineVariant);
        Outline.Color = C(s.Outline);
        Faint.Color = C(s.OnBackground.WithAlpha(0.12));
        Fainter.Color = C(s.OnBackground.WithAlpha(0.06));
        Track.Color = C(s.OnBackground.WithAlpha(0.18));
        Scrim.Color = s.IsDark ? Colors.Black : Colors.White;
        Changed?.Invoke(s);
    }

    public static Color C(Argb a) => Color.FromArgb(a.A, a.R, a.G, a.B);
}

/** Wave's look: the app's palette, type sizes and shapes. */
public static class Theme
{
    /** The whole app's colors: the song's when "Match song colors" is on. */
    public static Palette App { get; } = new(Base);

    public static bool SystemDark => Application.Current.RequestedTheme == ApplicationTheme.Dark;
    public static Scheme Base => SystemDark ? Scheme.DarkBlue : Scheme.Light;

    public static readonly FontFamily Icons = new("ms-appx:///Assets/Fonts/MaterialIconsRound-Regular.otf#Material Icons Round");
    public static readonly FontFamily IconsOutlined = new("ms-appx:///Assets/Fonts/MaterialIconsOutlined-Regular.otf#Material Icons Outlined");
    public static readonly FontFamily Display = new("Segoe UI Variable Display, Segoe UI");
    public static readonly FontFamily Text = new("Segoe UI Variable Text, Segoe UI");

    public static readonly SolidColorBrush White = new(Colors.White);
    public static readonly SolidColorBrush White78 = new(Color.FromArgb(199, 255, 255, 255));
    public static readonly SolidColorBrush White16 = new(Color.FromArgb(41, 255, 255, 255));
    public static readonly SolidColorBrush White25 = new(Color.FromArgb(64, 255, 255, 255));
    public static readonly SolidColorBrush Black = new(Colors.Black);
    public static readonly SolidColorBrush Clear = new(Colors.Transparent);

    public static void Install(ResourceDictionary resources)
    {
        // WinUI's own controls (menus, dialogs, text boxes, sliders) in Wave's colors.
        resources["SystemAccentColor"] = Palette.C(Base.Primary);
        resources["ContentDialogBackground"] = App.SurfaceContainerHigh;
        resources["MenuFlyoutPresenterBackground"] = App.SurfaceContainerHigh;
        resources["FlyoutPresenterBackground"] = App.SurfaceContainerHigh;
        resources["OverlayCornerRadius"] = new CornerRadius(20);
        resources["ControlCornerRadius"] = new CornerRadius(12);
        // Text fields: Windows' box and underline off; Wave draws the pill around them (Field).
        foreach (var state in new[] { "", "PointerOver", "Focused", "Disabled" })
        {
            resources["TextControlBackground" + state] = Clear;
            resources["TextControlBorderBrush" + state] = Clear;
            resources["TextControlForeground" + state] = App.OnBackground;
            resources["TextControlPlaceholderForeground" + state] = App.OnSurfaceVariant;
        }
        resources["TextControlBorderThemeThickness"] = new Thickness(0);
        resources["TextControlBorderThemeThicknessFocused"] = new Thickness(0);
        resources["TextControlButtonForeground"] = App.OnSurfaceVariant;
    }
}
