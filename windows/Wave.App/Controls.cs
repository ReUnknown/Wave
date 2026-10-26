using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Media;
using Windows.Foundation;

namespace Wave.App;

/**
 * Material 3 Expressive pieces Wave draws itself (instead of Windows' own menus, dialogs and
 * switches): rounded surfaces in the current colors, springy, and the same as on every other
 * device.
 */

/** A dropdown menu: a rounded surface with icon rows that grows out from where you clicked. */
public sealed class Menu
{
    private readonly StackPanel rows = new() { Spacing = 2 };
    private readonly Popup popup = new() { IsLightDismissEnabled = true };
    private readonly Border surface;
    private readonly Palette p;

    public Menu(Palette? palette = null)
    {
        p = palette ?? Theme.App;
        surface = new Border
        {
            Background = p.SurfaceContainerHigh,
            CornerRadius = new CornerRadius(20),
            Padding = new Thickness(6),
            MinWidth = 220,
            Child = rows,
            Shadow = new ThemeShadow(),
            Translation = new System.Numerics.Vector3(0, 0, 32),
        };
        popup.Child = surface;
    }

    public Menu Add(string? glyph, string text, Action click, bool bold = false, bool outlined = false)
    {
        var tap = new Tap { Height = 46, CornerRadius = new CornerRadius(14), Padding = new Thickness(12, 0, 16, 0) };
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 14, VerticalAlignment = VerticalAlignment.Center };
        if (glyph is not null) row.Children.Add(Ui.Icon(glyph, 20, p.OnBackground, outlined));
        row.Children.Add(Ui.Text(text, 15, p.OnBackground, bold ? FontWeights.Bold : FontWeights.Normal));
        tap.Children.Add(row);
        tap.PointerEntered += (_, _) => tap.Background = p.Fainter;
        tap.PointerExited += (_, _) => tap.Background = Theme.Clear;
        tap.Click += () =>
        {
            popup.IsOpen = false;
            click();
        };
        rows.Children.Add(tap);
        return this;
    }

    /** Words at the top of the menu (your name and email). */
    public Menu Header(string title, string subtitle)
    {
        var column = new StackPanel { Margin = new Thickness(12, 8, 12, 8) };
        column.Children.Add(Ui.Text(title, 16, p.OnBackground, FontWeights.Bold));
        column.Children.Add(Ui.Text(subtitle, 13, p.OnSurfaceVariant, lines: 2));
        rows.Children.Add(column);
        return this;
    }

    public Menu Separator()
    {
        rows.Children.Add(new Border { Height = 1, Background = p.OutlineVariant, Margin = new Thickness(12, 4, 12, 4), Opacity = 0.6 });
        return this;
    }

    /** Opens at [at] inside [anchor] (where you right-clicked), or under it. */
    public void ShowAt(FrameworkElement anchor, Point? at = null)
    {
        popup.XamlRoot = anchor.XamlRoot;
        var root = anchor.XamlRoot.Size;
        var origin = anchor.TransformToVisual(null).TransformPoint(at ?? new Point(anchor.ActualWidth, anchor.ActualHeight + 4));
        surface.Measure(new Size(double.PositiveInfinity, double.PositiveInfinity));
        var w = surface.DesiredSize.Width;
        var h = surface.DesiredSize.Height;
        var x = at is null ? origin.X - w : origin.X;
        var y = origin.Y;
        popup.HorizontalOffset = Math.Clamp(x, 8, Math.Max(8, root.Width - w - 8));
        popup.VerticalOffset = y + h > root.Height - 8 ? Math.Max(8, y - h) : y;
        popup.IsOpen = true;
        Motion.Grow(surface, 0.9f, 220);
    }
}

/** A dialog: a rounded surface over a dimmed window, with text buttons in the song's colors. */
public sealed class Dialog
{
    private readonly Grid layer = new();
    private readonly StackPanel body = new() { Spacing = 16 };
    private readonly StackPanel buttons = new() { Orientation = Orientation.Horizontal, Spacing = 8, HorizontalAlignment = HorizontalAlignment.Right };
    private readonly TaskCompletionSource<int> result = new();
    private readonly Palette p;

    public Dialog(string title, Palette? palette = null)
    {
        p = palette ?? Theme.App;
        layer.Background = new SolidColorBrush(Windows.UI.Color.FromArgb(110, 0, 0, 0));
        layer.Tapped += (_, e) => { if (e.OriginalSource == layer) Close(-1); };
        var card = new Border
        {
            Background = p.SurfaceContainerHigh,
            CornerRadius = new CornerRadius(28),
            Padding = new Thickness(24),
            MaxWidth = 560,
            MinWidth = 320,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(24),
        };
        var column = new StackPanel { Spacing = 20 };
        column.Children.Add(Ui.Text(title, 24, p.OnBackground, FontWeights.Bold, 2));
        column.Children.Add(body);
        column.Children.Add(buttons);
        card.Child = column;
        layer.Children.Add(card);
        layer.KeyDown += (_, e) => { if (e.Key == Windows.System.VirtualKey.Escape) { e.Handled = true; Close(-1); } };
    }

    public Dialog Content(UIElement element)
    {
        body.Children.Add(element);
        return this;
    }

    public Dialog Text(string text)
    {
        body.Children.Add(Ui.Text(text, 15, p.OnSurfaceVariant, lines: 0));
        return this;
    }

    /** A button that closes the dialog with [code]; [primary] is filled. */
    public Dialog Button(string label, int code, bool primary = false)
    {
        var b = primary
            ? Ui.Button(null, label, p.Primary, p.OnPrimary, () => Close(code), new CornerRadius(20), 40)
            : Ui.Button(null, label, Theme.Clear, p.Primary, () => Close(code), new CornerRadius(20), 40);
        buttons.Children.Add(b);
        return this;
    }

    public Task<int> ShowAsync()
    {
        App.Window.ShowLayer(layer);
        Motion.Fade(layer, 1, 160);
        Motion.Grow(layer.Children[0], 0.92f, 280);
        return result.Task;
    }

    public void Close(int code)
    {
        if (result.Task.IsCompleted) return;
        App.Window.HideLayer(layer);
        result.TrySetResult(code);
    }
}

/** Material 3 switch: the track fills and the thumb grows (and slides) when it's on. */
public sealed class Switch : Tap
{
    private readonly Border track = new() { Width = 52, Height = 32, CornerRadius = new CornerRadius(16), BorderThickness = new Thickness(2) };
    private readonly Border thumb = new() { HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Center };
    private readonly Animator slide;
    private readonly Palette p;
    private bool on;

    public event Action<bool>? Toggled;

    public Switch(bool value, Palette? palette = null)
    {
        p = palette ?? Theme.App;
        on = value;
        Width = 52;
        Height = 32;
        CornerRadius = new CornerRadius(16);
        Children.Add(track);
        Children.Add(thumb);
        slide = Motion.Value(on ? 1 : 0, Paint);
        Click += () =>
        {
            on = !on;
            slide.To(on ? 1 : 0);
            Toggled?.Invoke(on);
        };
        Paint(slide.Current);
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(this, "Switch");
    }

    private void Paint(double t)
    {
        var size = 16 + 8 * Math.Clamp(t, 0, 1);
        thumb.Width = thumb.Height = size;
        thumb.CornerRadius = new CornerRadius(size / 2);
        thumb.Margin = new Thickness(8 - (size - 16) / 2 + 20 * t, 0, 0, 0);
        track.Background = on ? p.Primary : p.SurfaceContainerHighest;
        track.BorderBrush = on ? p.Primary : p.Outline;
        thumb.Background = on ? p.OnPrimary : p.Outline;
    }
}

/** A row of connected choices (Material 3 segmented buttons). */
public sealed class Segmented : StackPanel
{
    public Segmented(string[] options, int selected, Action<int> pick, Palette? palette = null)
    {
        var p = palette ?? Theme.App;
        Orientation = Orientation.Horizontal;
        Spacing = 3;
        var taps = new List<Tap>();
        for (var i = 0; i < options.Length; i++)
        {
            var index = i;
            var first = i == 0;
            var last = i == options.Length - 1;
            var tap = new Tap
            {
                Height = 40,
                Padding = new Thickness(14, 0, 14, 0),
                CornerRadius = new CornerRadius(first ? 20 : 8, last ? 20 : 8, last ? 20 : 8, first ? 20 : 8),
            };
            tap.Children.Add(Ui.Center(Ui.Text(options[i], 14, p.OnBackground, FontWeights.SemiBold)));
            tap.Click += () =>
            {
                selected = index;
                Paint();
                pick(index);
            };
            taps.Add(tap);
            Children.Add(tap.Pressable(0.95f));
        }
        void Paint()
        {
            for (var i = 0; i < taps.Count; i++)
            {
                var on = i == selected;
                taps[i].Background = on ? p.Primary : p.SurfaceContainerHighest;
                ((TextBlock)taps[i].Children[0]).Foreground = on ? p.OnPrimary : p.OnBackground;
            }
        }
        Paint();
    }
}

/** Material 3 Expressive loading indicator: a shape that turns and morphs while it waits. */
public sealed class LoadingIndicator : Grid
{
    private readonly Microsoft.UI.Xaml.Shapes.Path shape;
    private readonly Animator morph;
    private int step;
    private static readonly Shapes.Kind[] Kinds = [Shapes.Kind.SoftBurst, Shapes.Kind.Cookie9, Shapes.Kind.Flower, Shapes.Kind.Sunny, Shapes.Kind.Clover4];
    private readonly Microsoft.UI.Dispatching.DispatcherQueueTimer timer;

    public LoadingIndicator(double size = 64, Brush? fill = null)
    {
        Width = Height = size;
        IsHitTestVisible = false;
        shape = Shapes.Make(Kinds[0], size * 0.8, fill ?? Theme.App.Primary);
        shape.HorizontalAlignment = HorizontalAlignment.Center;
        shape.VerticalAlignment = VerticalAlignment.Center;
        Children.Add(shape);
        morph = Motion.Value(1, v =>
        {
            var s = (float)(0.85 + 0.15 * v);
            var visual = Microsoft.UI.Xaml.Hosting.ElementCompositionPreview.GetElementVisual(shape);
            visual.Scale = new System.Numerics.Vector3(s, s, 1);
        });
        timer = Microsoft.UI.Dispatching.DispatcherQueue.GetForCurrentThread().CreateTimer();
        timer.Interval = TimeSpan.FromMilliseconds(650);
        timer.Tick += (_, _) =>
        {
            step = (step + 1) % Kinds.Length;
            shape.Data = Shapes.Make(Kinds[step], size * 0.8, shape.Fill).Data;
            morph.To(0, animate: false);
            morph.To(1);
        };
        Loaded += (_, _) => { timer.Start(); Motion.Spin(shape, true, 1.6); };
        Unloaded += (_, _) => { timer.Stop(); Motion.Spin(shape, false); };
        RegisterPropertyChangedCallback(VisibilityProperty, (_, _) =>
        {
            if (Visibility == Visibility.Visible) { timer.Start(); Motion.Spin(shape, true, 1.6); }
            else { timer.Stop(); Motion.Spin(shape, false); }
        });
    }
}

/** A text field drawn as a Material pill (Windows' text box inside, with its own look turned off). */
public sealed class Field : Grid
{
    public TextBox Box { get; } = new()
    {
        BorderThickness = new Thickness(0),
        Background = Theme.Clear,
        FontSize = 16,
        VerticalAlignment = VerticalAlignment.Center,
        Padding = new Thickness(0),
    };

    public Field(string placeholder, Palette? palette = null, double height = 56, string? glyph = null)
    {
        var p = palette ?? Theme.App;
        Height = height;
        CornerRadius = new CornerRadius(height / 2);
        Background = p.Faint;
        BorderBrush = p.Track;
        BorderThickness = new Thickness(1);
        Padding = new Thickness(18, 0, 18, 0);
        Box.PlaceholderText = placeholder;
        Box.Foreground = p.OnBackground;
        Box.PlaceholderForeground = p.OnSurfaceVariant;
        var grid = Ui.Grid(columns: "Auto,*");
        grid.ColumnSpacing = 12;
        if (glyph is not null) grid.Children.Add(Ui.Center(Ui.Icon(glyph, 22, p.OnBackground)).At(0, 0));
        grid.Children.Add(Box.At(0, 1));
        Children.Add(grid);
        Box.GotFocus += (_, _) => BorderBrush = p.Primary;
        Box.LostFocus += (_, _) => BorderBrush = p.Track;
    }

    public string Text
    {
        get => Box.Text;
        set => Box.Text = value;
    }
}
