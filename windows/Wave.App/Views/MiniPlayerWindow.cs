using System.Runtime.InteropServices;
using Microsoft.UI.Text;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Wave.Coloring;
using Wave.Data;
using Wave.Music;
using Wave.Playback;
using Windows.Graphics;

namespace Wave.App.Views;

/**
 * The mini player that stays on top of everything: the cover's face fading into the song's
 * colors, the song, progress and controls. Wave's window minimizes while it's open. Drag it
 * anywhere and it springs to the nearest corner; fling it against the left or right edge and it
 * tucks away as a round Wave button (click it or pull it out to get it back). Three sizes
 * (right-click), and Bigger has a tray with volume and speed. It remembers its corner and size.
 */
public sealed class MiniPlayerWindow : Window
{
    public enum Size { Tiny, Compact, Bigger }
    private const int Margin = 16, Bubble = 56, TrayHeight = 56;

    private static MiniPlayerWindow? open;

    private readonly Palette p = new(Scheme.DarkBlue);
    private readonly Grid root = new() { Background = Theme.Black };
    private readonly BlurredArt backdrop = new(8);
    private readonly Grid face = new();
    private readonly Grid layer = new();
    private Size size;
    private bool tray, tucked, tuckRight = true;
    private int corner;
    private PointInt32? tuckAt;
    private string? shownArt;

    public static void Open()
    {
        if (App.Services.Player.State.Current is null) return;
        if (open is not null) { open.Activate(); return; }
        open = new MiniPlayerWindow();
        open.Activate();
        // It takes Wave's place.
        if (App.Window.AppWindow.Presenter is OverlappedPresenter p) p.Minimize();
    }

    public static void CloseIfOpen() => open?.Close();

    private MiniPlayerWindow()
    {
        Title = "Wave mini player";
        size = Enum.TryParse<Size>(SettingsStore.Current.MiniSize, out var s) ? s : Size.Compact;
        corner = Math.Clamp(SettingsStore.Current.MiniCorner, 0, 3);
        AppWindow.SetIcon(System.IO.Path.Combine(AppContext.BaseDirectory, "Assets", "wave.ico"));
        if (AppWindow.Presenter is OverlappedPresenter presenter)
        {
            presenter.IsAlwaysOnTop = true;
            presenter.IsResizable = false;
            presenter.IsMaximizable = false;
            presenter.IsMinimizable = false;
            presenter.SetBorderAndTitleBar(false, false);
        }
        AppWindow.IsShownInSwitchers = false;
        root.RequestedTheme = ElementTheme.Dark;
        root.Children.Add(backdrop);
        root.Children.Add(new Rectangle { Fill = new SolidColorBrush(Windows.UI.Color.FromArgb(115, 0, 0, 0)) });
        root.Children.Add(face);
        root.Children.Add(layer);
        Content = root;
        HookDrag(root);
        root.RightTapped += (_, e) => SizeMenu().ShowAt(root, new Microsoft.UI.Xaml.Controls.Primitives.FlyoutShowOptions { Position = e.GetPosition(root) });
        root.KeyDown += (_, e) =>
        {
            if (e.Key == Windows.System.VirtualKey.Space) { App.Services.Player.TogglePlay(); e.Handled = true; }
        };
        App.Services.Player.Changed += OnState;
        Closed += (_, _) =>
        {
            App.Services.Player.Changed -= OnState;
            open = null;
        };
        Place(animate: false);
        Draw();
        OnState(App.Services.Player.State);
    }

    // ---------------------------------------------------------------- Size and place

    private (int w, int h) LogicalSize() => tucked ? (Bubble, Bubble) : size switch
    {
        Size.Tiny => (300, 68),
        Size.Bigger => (320, 480 + (tray ? TrayHeight : 0)),
        _ => (420, 156),
    };

    private double Scale => Native.GetDpiForWindow(WinRT.Interop.WindowNative.GetWindowHandle(this)) / 96.0;

    private RectInt32 WorkArea(PointInt32 near) => DisplayArea.GetFromPoint(near, DisplayAreaFallback.Nearest).WorkArea;

    private RectInt32 Target()
    {
        var (lw, lh) = LogicalSize();
        var w = (int)(lw * Scale);
        var h = (int)(lh * Scale);
        var m = (int)(Margin * Scale);
        var center = new PointInt32(AppWindow.Position.X + AppWindow.Size.Width / 2, AppWindow.Position.Y + AppWindow.Size.Height / 2);
        if (tucked)
        {
            var at = tuckAt ?? center;
            var area = WorkArea(at);
            var x = tuckRight ? area.X + area.Width - w - (int)(6 * Scale) : area.X + (int)(6 * Scale);
            var y = Math.Clamp(at.Y - h / 2, area.Y + m, area.Y + area.Height - h - m);
            return new RectInt32(x, y, w, h);
        }
        var a = WorkArea(open is null || AppWindow.Size.Width < 10 ? Cursor() : center);
        return new RectInt32(
            corner % 2 == 0 ? a.X + m : a.X + a.Width - w - m,
            corner < 2 ? a.Y + m : a.Y + a.Height - h - m,
            w, h);
    }

    private Animator? glide;

    /** Springs the window to where it belongs (moving and resizing together), then cuts its shape. */
    private void Place(bool animate = true)
    {
        var to = Target();
        if (!animate)
        {
            AppWindow.MoveAndResize(to);
            Shape();
            return;
        }
        var from = new RectInt32(AppWindow.Position.X, AppWindow.Position.Y, AppWindow.Size.Width, AppWindow.Size.Height);
        glide = Motion.Value(0, t =>
        {
            int L(int a, int b) => a + (int)((b - a) * t);
            AppWindow.MoveAndResize(new RectInt32(L(from.X, to.X), L(from.Y, to.Y), L(from.Width, to.Width), L(from.Height, to.Height)));
            Shape();
        });
        glide.To(1);
    }

    /** The window cut to the mini player's rounded shape (a circle when tucked). */
    private void Shape()
    {
        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(this);
        var w = AppWindow.Size.Width;
        var h = AppWindow.Size.Height;
        var radius = tucked ? Math.Max(w, h) : (int)((size switch { Size.Tiny => 34, Size.Bigger => 30, _ => 26 }) * 2 * Scale);
        var region = CreateRoundRectRgn(0, 0, w + 1, h + 1, radius, radius);
        SetWindowRgn(hwnd, region, true);
    }

    [DllImport("gdi32.dll")] private static extern nint CreateRoundRectRgn(int x1, int y1, int x2, int y2, int w, int h);
    [DllImport("user32.dll")] private static extern int SetWindowRgn(nint hwnd, nint region, bool redraw);
    [DllImport("user32.dll")] private static extern bool GetCursorPos(out PointInt32 point);

    private static PointInt32 Cursor() => GetCursorPos(out var pt) ? pt : new PointInt32(0, 0);

    private void Save() => SettingsStore.Update(s => s with { MiniSize = size.ToString(), MiniCorner = corner });

    // ---------------------------------------------------------------- Dragging, snapping, tucking

    private void HookDrag(UIElement surface)
    {
        PointInt32 grab = default, origin = default;
        var pressed = false;
        var dragging = false;
        var trail = new List<(DateTime at, int x)>();
        surface.AddHandler(UIElement.PointerPressedEvent, new PointerEventHandler((_, e) =>
        {
            if (!e.GetCurrentPoint(root).Properties.IsLeftButtonPressed) return;
            pressed = true;
            dragging = false;
            grab = Cursor();
            origin = AppWindow.Position;
            trail.Clear();
        }), true);
        surface.AddHandler(UIElement.PointerMovedEvent, new PointerEventHandler((_, e) =>
        {
            if (!pressed) return;
            var now = Cursor();
            if (!dragging && Math.Abs(now.X - grab.X) + Math.Abs(now.Y - grab.Y) > 8 * Scale)
            {
                // A drag the progress line or tray handle took is theirs.
                if (e.OriginalSource is FrameworkElement f && IsOwnDrag(f)) { pressed = false; return; }
                dragging = true;
                root.CapturePointer(e.Pointer);
            }
            if (!dragging) return;
            AppWindow.Move(new PointInt32(origin.X + now.X - grab.X, origin.Y + now.Y - grab.Y));
            trail.Add((DateTime.UtcNow, now.X));
            if (trail.Count > 6) trail.RemoveAt(0);
        }), true);
        surface.AddHandler(UIElement.PointerReleasedEvent, new PointerEventHandler((_, e) =>
        {
            if (!pressed) return;
            pressed = false;
            root.ReleasePointerCaptures();
            if (!dragging)
            {
                if (tucked) { tucked = false; Draw(); Place(); }
                return;
            }
            dragging = false;
            e.Handled = true;
            Released(trail);
        }), true);
    }

    private static bool IsOwnDrag(FrameworkElement f)
    {
        for (DependencyObject? x = f; x is not null; x = VisualTreeHelper.GetParent(x))
            if (x is WavyProgress || x is FrameworkElement { Tag: "tray" }) return true;
        return false;
    }

    private void Released(List<(DateTime at, int x)> trail)
    {
        var pointer = Cursor();
        var area = WorkArea(pointer);
        var speed = trail.Count >= 2 ? (trail[^1].x - trail[0].x) / Math.Max(0.001, (trail[^1].at - trail[0].at).TotalSeconds) : 0;
        var edge = (int)(24 * Scale);
        var atLeft = pointer.X <= area.X + edge || AppWindow.Position.X < area.X - AppWindow.Size.Width / 3;
        var atRight = pointer.X >= area.X + area.Width - edge || AppWindow.Position.X + AppWindow.Size.Width > area.X + area.Width + AppWindow.Size.Width / 3;
        var flungLeft = speed < -2200 * Scale && pointer.X < area.X + area.Width / 3;
        var flungRight = speed > 2200 * Scale && pointer.X > area.X + area.Width * 2 / 3;
        if (atLeft || atRight || flungLeft || flungRight)
        {
            tuckRight = atRight || flungRight;
            tuckAt = pointer;
            if (!tucked) { tucked = true; Draw(); }
        }
        else
        {
            corner = (pointer.Y > area.Y + area.Height / 2 ? 2 : 0) + (pointer.X > area.X + area.Width / 2 ? 1 : 0);
            if (tucked) { tucked = false; Draw(); }
            Save();
        }
        Place();
    }

    private MenuFlyout SizeMenu()
    {
        var menu = new MenuFlyout();
        foreach (var s in Enum.GetValues<Size>())
        {
            var item = new MenuFlyoutItem { Text = s.ToString(), FontWeight = s == size ? FontWeights.Bold : FontWeights.Normal };
            item.Click += (_, _) => Resize(s);
            menu.Items.Add(item);
        }
        var back = new MenuFlyoutItem { Text = "Back to Wave" };
        back.Click += (_, _) => BackToWave();
        menu.Items.Add(back);
        var close = new MenuFlyoutItem { Text = "Close" };
        close.Click += (_, _) => Close();
        menu.Items.Add(close);
        return menu;
    }

    private void Resize(Size s)
    {
        size = s;
        if (s != Size.Bigger) tray = false;
        Draw();
        Place();
        Save();
    }

    private void BackToWave()
    {
        Close();
        Native.Bring(App.Window);
    }

    // ---------------------------------------------------------------- Drawing

    private void OnState(PlayerState s)
    {
        if (s.Current is null) { BackToWave(); return; }
        if (s.Current.Art != shownArt)
        {
            shownArt = s.Current.Art;
            backdrop.Url = s.Current.Art;
            _ = Recolor(s.Current.Art);
            Draw();
        }
    }

    private async Task Recolor(string? art)
    {
        var scheme = await Images.SchemeAsync(art, dark: true);
        if (art == shownArt) p.Set(scheme ?? Scheme.DarkBlue);
    }

    /** Builds the layout for the current size (it fades and grows in as the window reshapes). */
    private void Draw()
    {
        layer.Children.Clear();
        face.Children.Clear();
        var state = App.Services.Player.State;
        if (state.Current is not { } song) return;
        if (tucked)
        {
            var bubble = new Grid { Background = p.Primary };
            bubble.Children.Add(Ui.Center(Shapes.WaveMark(28, p.OnPrimary)));
            layer.Children.Add(bubble);
            return;
        }
        switch (size)
        {
            case Size.Compact: Compact(song); break;
            case Size.Bigger: Bigger(song); break;
            default: Tiny(song); break;
        }
        Motion.Grow(layer, 0.94f, 320);
    }

    private FrameworkElement Face(NowPlaying song, bool horizontal, double extent)
    {
        var art = new FadedArt(horizontal, 0.4f, 1f) { Url = song.Art.AtSize(Music.Art.Card) };
        if (horizontal) { art.Width = extent; art.HorizontalAlignment = HorizontalAlignment.Left; }
        else { art.Height = extent; art.VerticalAlignment = VerticalAlignment.Top; }
        return art;
    }

    private UIElement Names(NowPlaying song, double titleSize)
    {
        var column = Ui.Column(0, Ui.Text(song.Title, titleSize, Theme.White, FontWeights.Bold), Ui.Text(song.Artist, titleSize > 16 ? 14 : 13, Theme.White78));
        column.VerticalAlignment = VerticalAlignment.Center;
        return column;
    }

    private Grid Progress()
    {
        var row = Ui.Grid(columns: "Auto,*,Auto");
        row.ColumnSpacing = 8;
        row.Children.Add(PlayerParts.Time(Theme.White78, false).At(0, 0));
        var wave = new WavyProgress(p.Primary, new SolidColorBrush(Windows.UI.Color.FromArgb(77, 255, 255, 255)), 3, 2, 14, maxFps: 24) { Height = 14, AlwaysActive = true };
        wave.Seek = f => App.Services.Player.Seek((long)(f * App.Services.Player.State.DurationMs));
        row.Children.Add(wave.At(0, 1));
        var total = Ui.Text(Ui.Time(App.Services.Player.State.DurationMs), 11, Theme.White78);
        total.VerticalAlignment = VerticalAlignment.Center;
        row.Children.Add(total.At(0, 2));
        return row;
    }

    private Tap Like()
    {
        var liked = App.Services.Player.State.Liked;
        var b = Ui.IconButton(Glyph.ThumbUp, liked ? "Liked" : "Like", liked ? p.Primary : Theme.White, App.Services.Player.ToggleLike, 44, 20, outlined: !liked);
        App.Services.Player.Changed += s =>
        {
            var icon = (FontIcon)b.Children[0];
            icon.FontFamily = s.Liked ? Theme.Icons : Theme.IconsOutlined;
            icon.Foreground = s.Liked ? p.Primary : Theme.White;
        };
        return b;
    }

    /** Resize, back to Wave and close: small, faded in on hover, in a spot of their own. */
    private FrameworkElement WindowButtons(bool bigger)
    {
        var glass = new SolidColorBrush(Windows.UI.Color.FromArgb(102, 0, 0, 0));
        var row = Ui.Row(2,
            Ui.IconButton(bigger ? Glyph.Collapse : Glyph.Expand, bigger ? "Smaller mini player" : "Bigger mini player", Theme.White, () => Resize(bigger ? Size.Compact : Size.Bigger), 30, 16, glass),
            Ui.IconButton(Glyph.OpenInNew, "Back to Wave", Theme.White, BackToWave, 30, 16, glass),
            Ui.IconButton(Glyph.Close, "Close mini player", Theme.White, Close, 30, 16, glass));
        row.Opacity = 0;
        root.PointerEntered += (_, _) => Motion.Fade(row, 1, 180);
        root.PointerExited += (_, _) => Motion.Fade(row, 0, 180);
        return row;
    }

    private void Compact(NowPlaying song)
    {
        face.Children.Add(Face(song, true, 210));
        var column = Ui.Grid("Auto,*,Auto,*,Auto");
        column.Margin = new Thickness(150, 14, 14, 12);
        var top = Ui.Grid(columns: "*,Auto");
        top.ColumnSpacing = 8;
        top.Children.Add(((FrameworkElement)Names(song, 16)).At(0, 0));
        var buttons = WindowButtons(false);
        buttons.VerticalAlignment = VerticalAlignment.Top;
        top.Children.Add(buttons.At(0, 1));
        column.Children.Add(top.At(0));
        column.Children.Add(Progress().At(2));
        var controls = Ui.Grid(columns: "Auto,*,Auto");
        controls.Children.Add(Like().At(0, 0));
        var group = Ui.Row(4,
            Ui.IconButton(Glyph.Previous, "Previous song", Theme.White, App.Services.Player.Previous),
            PlayerParts.PlayButton(60, 46, 14, 23, 22, p),
            Ui.IconButton(Glyph.Next, "Next song", Theme.White, App.Services.Player.Next));
        group.HorizontalAlignment = HorizontalAlignment.Right;
        controls.Children.Add(group.At(0, 2));
        column.Children.Add(controls.At(4));
        layer.Children.Add(column);
    }

    private void Bigger(NowPlaying song)
    {
        var column = Ui.Grid("*,Auto,Auto,Auto,Auto");
        var top = new Grid();
        top.Children.Add(Face(song, false, 9999));
        var names = Ui.Grid(columns: "*,Auto");
        names.ColumnSpacing = 8;
        names.VerticalAlignment = VerticalAlignment.Bottom;
        names.Margin = new Thickness(18, 0, 18, 0);
        names.Children.Add(((FrameworkElement)Names(song, 22)).At(0, 0));
        names.Children.Add(Like().At(0, 1));
        top.Children.Add(names);
        var buttons = WindowButtons(true);
        buttons.HorizontalAlignment = HorizontalAlignment.Right;
        buttons.VerticalAlignment = VerticalAlignment.Top;
        buttons.Margin = new Thickness(12);
        top.Children.Add(buttons);
        column.Children.Add(top.At(0));
        column.Children.Add(Progress().Margin(18, 12, 18, 0).At(1));
        var group = Ui.Grid(columns: "72,*,72");
        group.ColumnSpacing = 4;
        group.Height = 56;
        group.Margin = new Thickness(18, 14, 18, 0);
        var soft = new SolidColorBrush(Windows.UI.Color.FromArgb(41, p.Primary.Color.R, p.Primary.Color.G, p.Primary.Color.B));
        group.Children.Add(PlayerParts.GroupButton(Glyph.Previous, "Previous song", new CornerRadius(28, 12, 12, 28), soft, Theme.White, 72, 56, App.Services.Player.Previous).At(0, 0));
        var play = PlayerParts.PlayButton(double.NaN, 56, 16, 28, 26, p);
        play.HorizontalAlignment = HorizontalAlignment.Stretch;
        group.Children.Add(play.At(0, 1));
        group.Children.Add(PlayerParts.GroupButton(Glyph.Next, "Next song", new CornerRadius(12, 28, 28, 12), soft, Theme.White, 72, 56, App.Services.Player.Next).At(0, 2));
        column.Children.Add(group.At(2));

        // The tray: drag the handle up (or click it) for volume and speed.
        var handle = new Tap { Height = 24, Tag = "tray" };
        handle.Children.Add(Ui.Center(new Border { Width = 36, Height = 4, CornerRadius = new CornerRadius(2), Background = new SolidColorBrush(Windows.UI.Color.FromArgb(115, 255, 255, 255)) }));
        handle.Click += () => SetTray(!tray);
        double startY = 0;
        handle.PointerPressed += (_, e) => startY = e.GetCurrentPoint(root).Position.Y;
        handle.PointerReleased += (_, e) =>
        {
            var dy = e.GetCurrentPoint(root).Position.Y - startY;
            if (dy < -12) SetTray(true); else if (dy > 12) SetTray(false);
        };
        column.Children.Add(handle.At(3));
        if (tray)
        {
            var extras = Ui.Grid(columns: "*,Auto");
            extras.Height = TrayHeight;
            extras.Padding = new Thickness(12, 0, 18, 8);
            extras.Children.Add(new SlimVolume(Theme.White, Theme.White25) { VerticalAlignment = VerticalAlignment.Center }.At(0, 0));
            extras.Children.Add(PlayerParts.Speed(Theme.White, Theme.White16).At(0, 1));
            column.Children.Add(extras.At(4));
        }
        layer.Children.Add(column);
    }

    private void SetTray(bool on)
    {
        if (on == tray) return;
        tray = on;
        Draw();
        Place();
    }

    private void Tiny(NowPlaying song)
    {
        face.Children.Add(Face(song, true, 130));
        var row = Ui.Grid(columns: "*,Auto");
        row.ColumnSpacing = 10;
        row.Margin = new Thickness(92, 0, 10, 0);
        var names = new Tap();
        names.Children.Add(Names(song, 14));
        names.Click += () => Resize(Size.Compact);
        row.Children.Add(names.At(0, 0));
        var play = PlayerParts.PlayButton(48, 48, 14, 24, 20, p);
        play.VerticalAlignment = VerticalAlignment.Center;
        row.Children.Add(play.At(0, 1));
        layer.Children.Add(row);
    }
}
