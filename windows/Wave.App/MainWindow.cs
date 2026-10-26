using Microsoft.UI;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Wave.App.Views;
using Wave.Data;
using Wave.Music;
using Windows.System;
using Windows.UI;

namespace Wave.App;

/** A page in the window (home, search, an album...): its own colors and art for the window around it. */
public abstract class WavePage : Grid
{
    /** This page's colors (the window's sidebar and top follow the page on top). */
    public virtual Palette Colors => Theme.App;
    /** Art to show blurred across the top of the window behind the sidebar. */
    public virtual string? Art => null;
    /** Page shown again after coming back to it. */
    public virtual void Shown() { }
}

/**
 * Wave's window: the sidebar, the page you're on, the player bar along the bottom and the full
 * player over everything. Wave's colors go right up to the top edge, under Windows' own window
 * buttons (like on macOS). Animations pause while the window is minimized or in the background.
 */
public sealed class MainWindow : Window
{
    public const double TitleBarHeight = 40;

    private readonly Grid root = new();
    private readonly BlurredArt ambientArt = new(10);
    private readonly Grid ambient = new() { Height = 640, VerticalAlignment = VerticalAlignment.Top, IsHitTestVisible = false };
    private readonly Rectangle ambientShade = new();
    private readonly Grid shell = Ui.Grid("*,Auto", "Auto,*");
    private readonly Grid pageHost = new();
    private readonly List<WavePage> stack = [];
    private readonly Sidebar sidebar;
    private readonly PlayerBar playerBar;
    private readonly NowPlayingView nowPlaying;
    private readonly Grid noticeHost = new() { VerticalAlignment = VerticalAlignment.Bottom, HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 0, 0, 120), IsHitTestVisible = false };
    private readonly Border dragRegion = new() { Height = TitleBarHeight, VerticalAlignment = VerticalAlignment.Top, Background = Theme.Clear };
    private readonly Grid overlay = new();
    private HomePage? home;
    private Palette? followed;

    /** A narrow window: no sidebar (the library moves into the account menu), a slimmer player bar. */
    public bool Narrow { get; private set; }
    public event Action? LayoutChanged;

    /** The window is on screen and in front (decorative motion runs only then). */
    public bool Active { get; private set; } = true;
    public event Action? ActiveChanged;

    /** The palette the sidebar and window edge use: the page on top's. */
    public Palette Side { get; } = new(Theme.App.Scheme);

    public MainWindow()
    {
        Title = "Wave";
        AppWindow.SetIcon(System.IO.Path.Combine(AppContext.BaseDirectory, "Assets", "wave.ico"));
        ExtendsContentIntoTitleBar = true;
        AppWindow.TitleBar.PreferredHeightOption = TitleBarHeightOption.Tall;
        // The strip along the top belongs to Windows: it always drags, snaps and double-click
        // maximizes, with Wave's colors showing through. Nothing clickable is ever placed in it.
        SetTitleBar(dragRegion);
        SizeAndCenter();

        root.Background = Side.Background;
        root.RequestedTheme = Theme.SystemDark ? ElementTheme.Dark : ElementTheme.Light;

        // The playing song's art (or the page's), blurred across the top behind sidebar and page.
        ambient.Children.Add(ambientArt);
        ambient.Children.Add(ambientShade);
        root.Children.Add(ambient);
        Side.Changed += _ => PaintAmbient();
        PaintAmbient();

        sidebar = new Sidebar(this);
        playerBar = new PlayerBar(this);
        nowPlaying = new NowPlayingView(this);

        shell.Children.Add(sidebar.At(0, 0));
        shell.Children.Add(pageHost.At(0, 1));
        shell.Children.Add(playerBar.At(1, 0, columnSpan: 2));
        root.Children.Add(shell);
        root.Children.Add(nowPlaying);
        root.Children.Add(overlay);
        root.Children.Add(noticeHost);
        root.Children.Add(dragRegion);
        Content = root;

        Notices.Shown += ShowNotice;
        Theme.App.Changed += _ => CaptionButtons();
        Side.Changed += _ => CaptionButtons();
        CaptionButtons();

        Activated += (_, e) => SetActive(e.WindowActivationState != WindowActivationState.Deactivated);
        AppWindow.Changed += (_, e) =>
        {
            if (e.DidPresenterChange || e.DidSizeChange)
                if (AppWindow.Presenter is OverlappedPresenter p) SetActive(p.State != OverlappedPresenterState.Minimized && Active);
        };
        Closed += (_, _) => Quit();

        root.KeyDown += OnKey;
        root.SizeChanged += (_, e) => Relayout(e.NewSize.Width);
        App.Services.Player.Changed += s => { UpdateAmbient(); if (s.Current is null && nowPlaying.Open) nowPlaying.Close(); };
        Theme.App.Changed += _ => { if (stack.Count <= 1) Side.Set(Theme.App.Scheme, animate: false); };
        SongColors.Start();

        ShowStart();
    }

    /** Small windows fall back to a simpler layout, so nothing ever gets squeezed. */
    private void Relayout(double width)
    {
        var narrow = width < 900;
        if (narrow == Narrow) return;
        Narrow = narrow;
        sidebar.Visibility = narrow ? Visibility.Collapsed : Visibility.Visible;
        LayoutChanged?.Invoke();
    }

    private void SizeAndCenter()
    {
        var area = DisplayArea.GetFromWindowId(AppWindow.Id, DisplayAreaFallback.Primary).WorkArea;
        var scale = (Content?.XamlRoot?.RasterizationScale) ?? 1.0;
        var dpi = GetDpi() / 96.0;
        var w = (int)Math.Min(1180 * dpi, area.Width * 0.85);
        var h = (int)Math.Min(780 * dpi, area.Height * 0.85);
        AppWindow.MoveAndResize(new Windows.Graphics.RectInt32(area.X + (area.Width - w) / 2, area.Y + (area.Height - h) / 2, w, h));
        if (AppWindow.Presenter is OverlappedPresenter p)
        {
            p.PreferredMinimumWidth = (int)(480 * dpi);
            p.PreferredMinimumHeight = (int)(560 * dpi);
        }
        _ = scale;
    }

    private double GetDpi() => Native.GetDpiForWindow(WinRT.Interop.WindowNative.GetWindowHandle(this));

    private void SetActive(bool active)
    {
        if (active == Active) return;
        Active = active;
        ActiveChanged?.Invoke();
    }

    /** Windows' minimize/maximize/close buttons, readable over whatever's under them. */
    private void CaptionButtons()
    {
        var t = AppWindow.TitleBar;
        var ink = Side.IsDark ? Colors.White : Colors.Black;
        t.ButtonBackgroundColor = Colors.Transparent;
        t.ButtonInactiveBackgroundColor = Colors.Transparent;
        t.ButtonForegroundColor = ink;
        t.ButtonInactiveForegroundColor = Color.FromArgb(150, ink.R, ink.G, ink.B);
        t.ButtonHoverBackgroundColor = Color.FromArgb(30, ink.R, ink.G, ink.B);
        t.ButtonHoverForegroundColor = ink;
        t.ButtonPressedBackgroundColor = Color.FromArgb(50, ink.R, ink.G, ink.B);
        t.ButtonPressedForegroundColor = ink;
    }

    private void PaintAmbient()
    {
        var bg = Side.Background.Color;
        var scrim = Side.IsDark ? Colors.Black : Colors.White;
        ambientShade.Fill = new LinearGradientBrush
        {
            StartPoint = new(0, 0),
            EndPoint = new(0, 1),
            GradientStops =
            {
                new GradientStop { Color = Color.FromArgb(71, scrim.R, scrim.G, scrim.B), Offset = 0 },
                new GradientStop { Color = Color.FromArgb(89, bg.R, bg.G, bg.B), Offset = 0.5 },
                new GradientStop { Color = bg, Offset = 1 },
            },
        };
    }

    private void UpdateAmbient()
    {
        var top = stack.LastOrDefault();
        ambientArt.Url = (top is not null && top is not HomePage ? top.Art : null)
                         ?? App.Services.Player.State.Current?.Art
                         ?? home?.Art;
    }

    // ---------------------------------------------------------------- Start: welcome or home

    private void ShowStart()
    {
        overlay.Children.Clear();
        if (SessionStore.Current is null)
        {
            shell.Visibility = Visibility.Collapsed;
            overlay.Children.Add(new WelcomeView(this));
            return;
        }
        shell.Visibility = Visibility.Visible;
        if (home is null)
        {
            home = new HomePage(this);
            stack.Add(home);
            pageHost.Children.Add(home);
        }
        sidebar.Refresh();
        _ = App.Services.LoadLikesAsync();
        SettingsView.CheckForUpdates();
    }

    /** Signed in (or chose to go without an account): home, with that account. */
    public void SessionChanged()
    {
        App.Services.Apply(SessionStore.Current);
        App.Services.ForgetHome();
        while (stack.Count > 1) PopNow();
        if (home is not null)
        {
            pageHost.Children.Remove(home);
            stack.Clear();
            home = null;
        }
        if (SessionStore.Current is null)
        {
            App.Services.Player.Reset();
            Likes.Clear();
        }
        ShowStart();
    }

    public void SignIn()
    {
        var login = new LoginView(this);
        overlay.Children.Add(login);
        Motion.Grow(login, 0.96f);
    }

    public void CloseOverlay(UIElement e) => overlay.Children.Remove(e);

    /** Dialogs sit over everything (and under the window's drag strip). */
    public void ShowLayer(UIElement e)
    {
        root.Children.Insert(root.Children.IndexOf(dragRegion), e);
        if (e is Control c) c.Focus(FocusState.Programmatic);
    }

    public void HideLayer(UIElement e) => root.Children.Remove(e);

    // ---------------------------------------------------------------- Pages

    public WavePage Top => stack[^1];

    public void Push(WavePage page)
    {
        if (nowPlaying.Open && !nowPlaying.Docked) nowPlaying.Close();
        var below = stack.LastOrDefault();
        stack.Add(page);
        pageHost.Children.Add(page);
        Motion.SlideIn(page, 48, 0);
        if (below is not null) _ = HideLater(below, page);
        Follow(page);
    }

    private static async Task HideLater(WavePage below, WavePage above)
    {
        await Task.Delay(380);
        // Covered pages stop drawing until they're shown again.
        if (above.Parent is not null) below.Visibility = Visibility.Collapsed;
    }

    public void Pop()
    {
        if (stack.Count <= 1) return;
        var top = stack[^1];
        stack.RemoveAt(stack.Count - 1);
        var below = stack[^1];
        below.Visibility = Visibility.Visible;
        below.Shown();
        Follow(below);
        _ = Motion.SlideOut(top, 48, 0).ContinueWith(_ => DispatcherQueue.TryEnqueue(() => pageHost.Children.Remove(top)));
    }

    private void PopNow()
    {
        var top = stack[^1];
        stack.RemoveAt(stack.Count - 1);
        pageHost.Children.Remove(top);
        stack[^1].Visibility = Visibility.Visible;
    }

    public void GoHome()
    {
        while (stack.Count > 2) PopNow();
        Pop();
    }

    /** The sidebar and window top take the colors and art of the page on top. */
    private void Follow(WavePage page)
    {
        if (followed is not null) followed.Changed -= OnFollowed;
        followed = page.Colors;
        followed.Changed += OnFollowed;
        Side.Set(page.Colors.Scheme);
        sidebar.Highlight(page);
        UpdateAmbient();
    }

    private void OnFollowed(Coloring.Scheme s) => Side.Set(s, animate: false);

    // ---------------------------------------------------------------- Player

    public void OpenPlayer(NowPlayingView.Tab tab = NowPlayingView.Tab.UpNext) => nowPlaying.Show(tab);
    public NowPlayingView Player => nowPlaying;
    public Sidebar Nav => sidebar;
    public double PlayerBarHeight => playerBar.ActualHeight;

    public void OpenSearch()
    {
        if (Top is SearchPage search) { search.FocusBox(); return; }
        Push(new SearchPage(this));
    }

    public void OpenLink(string link)
    {
        if (link == "show") { Native.Bring(this); return; }
        Native.Bring(this);
        _ = Links.OpenAsync(link);
    }

    // ---------------------------------------------------------------- Keys

    private void OnKey(object sender, KeyRoutedEventArgs e)
    {
        var ctrl = Microsoft.UI.Input.InputKeyboardSource.GetKeyStateForCurrentThread(VirtualKey.Control).HasFlag(Windows.UI.Core.CoreVirtualKeyStates.Down);
        var typing = FocusManager.GetFocusedElement(root.XamlRoot) is TextBox or AutoSuggestBox;
        var player = App.Services.Player;
        switch (e.Key)
        {
            case VirtualKey.Space when !typing: player.TogglePlay(); break;
            case VirtualKey.Right when ctrl: player.Next(); break;
            case VirtualKey.Left when ctrl: player.Previous(); break;
            case VirtualKey.L when ctrl: player.ToggleLike(); break;
            case VirtualKey.F when ctrl: OpenSearch(); break;
            case (VirtualKey)191 when !typing: OpenSearch(); break; // "/"
            case VirtualKey.Escape:
                if (nowPlaying.Open) nowPlaying.Close();
                else if (stack.Count > 1) Pop();
                else return;
                break;
            case VirtualKey.GoBack:
                if (stack.Count > 1) Pop(); else return;
                break;
            default: return;
        }
        e.Handled = true;
    }

    // ---------------------------------------------------------------- Notices

    private void ShowNotice(string message)
    {
        var pill = new Border
        {
            Background = Theme.App.SurfaceContainerHighest,
            CornerRadius = new CornerRadius(24),
            Padding = new Thickness(20, 12, 20, 12),
            Child = Ui.Text(message, 14, Theme.App.OnBackground, Microsoft.UI.Text.FontWeights.SemiBold),
        };
        noticeHost.Children.Clear();
        noticeHost.Children.Add(pill);
        Motion.SlideIn(pill, 0, 24, 300);
        _ = Task.Delay(2400).ContinueWith(_ => DispatcherQueue.TryEnqueue(async () =>
        {
            if (!noticeHost.Children.Contains(pill)) return;
            await Motion.SlideOut(pill, 0, 24, 200);
            noticeHost.Children.Remove(pill);
        }));
    }

    // ---------------------------------------------------------------- Quitting

    /** Closing Wave quits it completely: saved, stopped, gone. */
    public static void Quit()
    {
        try { App.Services.Player.Shutdown(); } catch (Exception) { }
        MiniPlayerWindow.CloseIfOpen();
        Environment.Exit(0);
    }
}

/** A few Win32 bits WinUI doesn't wrap. */
internal static partial class Native
{
    [System.Runtime.InteropServices.LibraryImport("user32.dll")]
    public static partial uint GetDpiForWindow(nint hwnd);

    [System.Runtime.InteropServices.LibraryImport("user32.dll")]
    [return: System.Runtime.InteropServices.MarshalAs(System.Runtime.InteropServices.UnmanagedType.Bool)]
    public static partial bool SetForegroundWindow(nint hwnd);

    public static void Bring(Window window)
    {
        if (window.AppWindow.Presenter is OverlappedPresenter p && p.State == OverlappedPresenterState.Minimized) p.Restore();
        window.Activate();
        SetForegroundWindow(WinRT.Interop.WindowNative.GetWindowHandle(window));
    }
}
