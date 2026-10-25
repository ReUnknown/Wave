using Microsoft.UI.Input;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Windows.Foundation;
using Windows.UI.Text;
using Path = Microsoft.UI.Xaml.Shapes.Path;

namespace Wave.App;

/** Material icon glyphs (Material Icons Round). */
public static class Glyph
{
    public const string Play = "", Pause = "", Next = "", Previous = "", ThumbUp = "",
        Shuffle = "", Repeat = "", RepeatOne = "", Lyrics = "", Queue = "", Expand = "",
        Collapse = "", MiniPlayer = "", Search = "", Home = "", Album = "", History = "",
        Add = "", Close = "", More = "", VolumeUp = "", VolumeDown = "", VolumeOff = "",
        Back = "", Account = "", Settings = "", Logout = "", Share = "", PlaylistAdd = "",
        ChevronDown = "", OpenInNew = "", Delete = "", Edit = "", Radio = "", Person = "",
        Library = "", PlayNext = "", Check = "", Update = "";
}

/** Clickable area with a hand cursor and keyboard focus (WinUI keeps cursors protected). */
public class Tap : Grid
{
    public event Action? Click;

    public Tap()
    {
        ProtectedCursor = InputSystemCursor.Create(InputSystemCursorShape.Hand);
        IsTabStop = true;
        UseSystemFocusVisuals = true;
        Background = Theme.Clear;
        Tapped += (_, e) => { e.Handled = true; Click?.Invoke(); };
        KeyDown += (_, e) =>
        {
            if (e.Key is Windows.System.VirtualKey.Enter or Windows.System.VirtualKey.Space) { e.Handled = true; Click?.Invoke(); }
        };
    }
}

/** Little helpers that keep the views readable. */
public static class Ui
{
    public static TextBlock Text(string? text, double size, Brush brush, FontWeight? weight = null, int lines = 1, int spacing = 0)
    {
        var t = new TextBlock
        {
            Text = text ?? "",
            FontSize = size,
            Foreground = brush,
            FontWeight = weight ?? FontWeights.Normal,
            FontFamily = size >= 20 ? Theme.Display : Theme.Text,
            TextTrimming = TextTrimming.CharacterEllipsis,
            CharacterSpacing = spacing,
            IsTextSelectionEnabled = false,
        };
        if (lines > 0) t.MaxLines = lines;
        if (lines != 1) t.TextWrapping = TextWrapping.WrapWholeWords;
        return t;
    }

    // Wave's type scale (Material 3, with heavier titles).
    public static TextBlock DisplayMedium(string? s, Brush b) => Text(s, 45, b, FontWeights.ExtraBold, 2, -31);
    public static TextBlock HeadlineLarge(string? s, Brush b) => Text(s, 32, b, FontWeights.ExtraBold, 2, -25);
    public static TextBlock HeadlineSmall(string? s, Brush b) => Text(s, 24, b, FontWeights.ExtraBold, 1, -12);
    public static TextBlock TitleMedium(string? s, Brush b) => Text(s, 16, b, FontWeights.Bold);
    public static TextBlock TitleSmall(string? s, Brush b) => Text(s, 14, b, FontWeights.Medium);
    public static TextBlock BodyMedium(string? s, Brush b) => Text(s, 14, b);
    public static TextBlock BodySmall(string? s, Brush b) => Text(s, 12, b);
    public static TextBlock LabelSmall(string? s, Brush b) => Text(s?.ToUpperInvariant(), 12, b, FontWeights.SemiBold, 1, 67);

    public static FontIcon Icon(string glyph, double size, Brush brush, bool outlined = false) => new()
    {
        Glyph = glyph,
        FontSize = size,
        Foreground = brush,
        FontFamily = outlined ? Theme.IconsOutlined : Theme.Icons,
        IsHitTestVisible = false,
    };

    /** A round icon button (44 px, like Wave's bar icons). */
    public static Tap IconButton(string glyph, string label, Brush tint, Action onClick, double size = 44, double iconSize = 22, Brush? fill = null, bool outlined = false)
    {
        var tap = new Tap { Width = size, Height = size, CornerRadius = new CornerRadius(size / 2), Background = fill ?? Theme.Clear };
        tap.Children.Add(Center(Icon(glyph, iconSize, tint, outlined)));
        tap.Click += onClick;
        ToolTipService.SetToolTip(tap, label);
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(tap, label);
        return tap.Pressable(0.86f);
    }

    /** A filled pill or shape button with an icon and/or label. */
    public static Tap Button(string? glyph, string? label, Brush fill, Brush ink, Action onClick, CornerRadius radius, double height = 56, double fontSize = 14, float pressed = 0.95f)
    {
        var tap = new Tap { Height = height, CornerRadius = radius, Background = fill, Padding = new Thickness(20, 0, 20, 0) };
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        if (glyph is not null) row.Children.Add(Icon(glyph, 22, ink));
        if (label is not null) row.Children.Add(Text(label, fontSize, ink, FontWeights.Bold));
        tap.Children.Add(row);
        tap.Click += onClick;
        if (label is not null) Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(tap, label);
        return tap.Pressable(pressed);
    }

    public static T Center<T>(T e) where T : FrameworkElement
    {
        e.HorizontalAlignment = HorizontalAlignment.Center;
        e.VerticalAlignment = VerticalAlignment.Center;
        return e;
    }

    public static StackPanel Column(double spacing = 0, params UIElement[] children)
    {
        var s = new StackPanel { Spacing = spacing };
        foreach (var c in children) s.Children.Add(c);
        return s;
    }

    public static StackPanel Row(double spacing = 0, params UIElement[] children)
    {
        var s = new StackPanel { Orientation = Orientation.Horizontal, Spacing = spacing };
        foreach (var c in children) s.Children.Add(c);
        return s;
    }

    public static T Margin<T>(this T e, double l, double t, double r, double b) where T : FrameworkElement
    {
        e.Margin = new Thickness(l, t, r, b);
        return e;
    }

    public static T Margin<T>(this T e, double all) where T : FrameworkElement
    {
        e.Margin = new Thickness(all);
        return e;
    }

    /** A cover with rounded corners (or a circle), decoded at the size it's shown. */
    public static Border Art(string? url, double size, double radius, Brush? placeholder = null)
    {
        var px = (int)Math.Ceiling(size * 1.5);
        return new Border
        {
            Width = size,
            Height = size,
            CornerRadius = new CornerRadius(radius),
            Background = (Brush?)Images.Brush(url, px) ?? placeholder ?? Theme.App.SurfaceContainerHigh,
        };
    }

    /** Grid with the given rows ("Auto", "*", "48"...). */
    public static Grid Grid(string rows = "", string columns = "")
    {
        var g = new Grid();
        foreach (var r in rows.Split(',', StringSplitOptions.RemoveEmptyEntries)) g.RowDefinitions.Add(new RowDefinition { Height = Length(r.Trim()) });
        foreach (var c in columns.Split(',', StringSplitOptions.RemoveEmptyEntries)) g.ColumnDefinitions.Add(new ColumnDefinition { Width = Length(c.Trim()) });
        return g;
    }

    private static GridLength Length(string s) => s switch
    {
        "Auto" => GridLength.Auto,
        "*" => new GridLength(1, GridUnitType.Star),
        _ when s.EndsWith('*') => new GridLength(double.Parse(s[..^1], System.Globalization.CultureInfo.InvariantCulture), GridUnitType.Star),
        _ => new GridLength(double.Parse(s, System.Globalization.CultureInfo.InvariantCulture)),
    };

    public static T At<T>(this T e, int row = 0, int column = 0, int rowSpan = 1, int columnSpan = 1) where T : FrameworkElement
    {
        Microsoft.UI.Xaml.Controls.Grid.SetRow(e, row);
        Microsoft.UI.Xaml.Controls.Grid.SetColumn(e, column);
        if (rowSpan > 1) Microsoft.UI.Xaml.Controls.Grid.SetRowSpan(e, rowSpan);
        if (columnSpan > 1) Microsoft.UI.Xaml.Controls.Grid.SetColumnSpan(e, columnSpan);
        return e;
    }

    public static string Time(long ms)
    {
        var s = Math.Max(0, ms) / 1000;
        return s >= 3600 ? $"{s / 3600}:{s / 60 % 60:00}:{s % 60:00}" : $"{s / 60}:{s % 60:00}";
    }
}

/** Material 3 Expressive shapes, drawn as paths (cookies, flowers, bursts). */
public static class Shapes
{
    public enum Kind { Cookie9, Cookie12, Sunny, Clover4, Flower, SoftBurst, Circle }

    /** A shape filling [size]×[size]. */
    public static Path Make(Kind kind, double size, Brush fill)
    {
        var (lobes, depth) = kind switch
        {
            Kind.Cookie9 => (9, 0.085),
            Kind.Cookie12 => (12, 0.06),
            Kind.Sunny => (8, 0.06),
            Kind.Clover4 => (4, 0.2),
            Kind.Flower => (8, 0.13),
            Kind.SoftBurst => (10, 0.1),
            _ => (0, 0.0),
        };
        return new Path { Data = Polar(size, lobes, depth), Fill = fill, Width = size, Height = size, Stretch = Stretch.None };
    }

    /** r(θ) = R·(1 − depth + depth·cos(lobes·θ)): smooth scallops, like Material's rounded polygons. */
    private static PathGeometry Polar(double size, int lobes, double depth)
    {
        var r = size / 2;
        var figure = new PathFigure { IsClosed = true, IsFilled = true };
        var poly = new PolyLineSegment();
        const int steps = 180;
        for (var i = 0; i <= steps; i++)
        {
            var a = i * 2 * Math.PI / steps - Math.PI / 2;
            var k = lobes == 0 ? 1 : 1 - depth + depth * Math.Cos(lobes * (a + Math.PI / 2));
            var p = new Point(r + r * k * Math.Cos(a), r + r * k * Math.Sin(a));
            if (i == 0) figure.StartPoint = p; else poly.Points.Add(p);
        }
        figure.Segments.Add(poly);
        var g = new PathGeometry();
        g.Figures.Add(figure);
        return g;
    }

    /** The Wave logo: two parallel waves. */
    public static Path WaveMark(double size, Brush color)
    {
        var s = size / 60;
        var g = new PathGeometry();
        foreach (var y in new[] { 44.0, 62.0 })
        {
            var f = new PathFigure { StartPoint = new Point((26 - 24) * s, (y - 26) * s), IsFilled = false };
            f.Segments.Add(new QuadraticBezierSegment { Point1 = new Point((40 - 24) * s, (y - 38) * s), Point2 = new Point((53 - 24) * s, (y - 26) * s) });
            f.Segments.Add(new QuadraticBezierSegment { Point1 = new Point((66 - 24) * s, (y - 14) * s), Point2 = new Point((80 - 24) * s, (y - 26) * s) });
            g.Figures.Add(f);
        }
        return new Path
        {
            Data = g, Stroke = color, StrokeThickness = 6.5 * s, StrokeStartLineCap = PenLineCap.Round, StrokeEndLineCap = PenLineCap.Round,
            Width = size, Height = size, IsHitTestVisible = false,
        };
    }
}

/**
 * Play ⇄ pause that morphs: the two pause bars slide together into the play triangle (each bar
 * becomes one half of it) on a bouncy spring, instead of one icon swapping for the other.
 */
public sealed class PlayPauseIcon : Grid
{
    private readonly Path path = new() { Stretch = Stretch.None };
    private readonly Animator t;
    private readonly double size;

    public PlayPauseIcon(double size, Brush tint, bool playing = false)
    {
        this.size = size;
        Width = Height = size;
        path.Fill = tint;
        Children.Add(path);
        IsHitTestVisible = false;
        t = Motion.Value(playing ? 0 : 1, Draw);
        Draw(t.Current);
    }

    public Brush Tint { set => path.Fill = value; }

    public bool Playing { set => t.To(value ? 0 : 1); }

    private void Draw(double v)
    {
        var k = size / 24;
        Point P(double ax, double ay, double bx, double by) => new((ax + (bx - ax) * v) * k, (ay + (by - ay) * v) * k);
        var g = new PathGeometry();
        void Quad(Point a, Point b, Point c, Point d)
        {
            var f = new PathFigure { StartPoint = a, IsClosed = true, IsFilled = true };
            f.Segments.Add(new LineSegment { Point = b });
            f.Segments.Add(new LineSegment { Point = c });
            f.Segments.Add(new LineSegment { Point = d });
            g.Figures.Add(f);
        }
        Quad(P(6, 5, 8, 5), P(10, 5, 14.2, 8.95), P(10, 19, 14.2, 15.05), P(6, 19, 8, 19));
        Quad(P(14, 5, 13.5, 8.5), P(18, 5, 19, 12), P(18, 19, 19, 12), P(14, 19, 13.5, 15.5));
        path.Data = g;
    }
}
