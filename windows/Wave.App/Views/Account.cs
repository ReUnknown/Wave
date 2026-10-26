using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.Web.WebView2.Core;
using Wave.Data;

namespace Wave.App.Views;

/** First screen when signed out: a wall of expressive shapes in Wave's colors, then sign in (or don't). */
public sealed class WelcomeView : Grid
{
    public WelcomeView(MainWindow window)
    {
        var p = Theme.App;
        Background = p.Background;
        var wall = new StackPanel { Spacing = 14, Margin = new Thickness(-60, -40, 0, 0), RenderTransform = new RotateTransform { Angle = -12 } };
        var shapes = new[] { Shapes.Kind.Cookie9, Shapes.Kind.Circle, Shapes.Kind.Sunny, Shapes.Kind.Clover4, Shapes.Kind.Cookie12, Shapes.Kind.Flower, Shapes.Kind.SoftBurst, Shapes.Kind.Cookie9, Shapes.Kind.Sunny };
        var fills = new Brush[] { p.PrimaryContainer, p.TertiaryContainer, p.SecondaryContainer, p.Primary };
        for (var row = 0; row < 4; row++)
        {
            var line = Ui.Row(14);
            for (var col = 0; col < 7; col++)
            {
                var i = row * 7 + col;
                line.Children.Add(Shapes.Make(shapes[i % shapes.Length], 120, fills[(i + row) % fills.Length]));
            }
            wall.Children.Add(line);
        }
        Children.Add(wall);
        Children.Add(new Microsoft.UI.Xaml.Shapes.Rectangle
        {
            Fill = new LinearGradientBrush
            {
                StartPoint = new(0, 0), EndPoint = new(0, 1),
                GradientStops =
                {
                    new GradientStop { Color = Windows.UI.Color.FromArgb(0, p.Background.Color.R, p.Background.Color.G, p.Background.Color.B), Offset = 0.25 },
                    new GradientStop { Color = p.Background.Color, Offset = 0.62 },
                },
            },
        });

        var column = new StackPanel { VerticalAlignment = VerticalAlignment.Bottom, Margin = new Thickness(48, 0, 48, 48), MaxWidth = 560, HorizontalAlignment = HorizontalAlignment.Left };
        var brand = Ui.Row(12);
        var mark = new Grid { Width = 56, Height = 56 };
        mark.Children.Add(Shapes.Make(Shapes.Kind.Cookie12, 56, p.Primary));
        mark.Children.Add(Ui.Center(Shapes.WaveMark(30, p.OnPrimary)));
        brand.Children.Add(mark);
        var name = Ui.Text("Wave", 60, p.OnBackground, FontWeights.ExtraBold, 1, -33);
        name.VerticalAlignment = VerticalAlignment.Center;
        brand.Children.Add(name);
        column.Children.Add(brand);
        column.Children.Add(Ui.Text("Your YouTube Music.\nNothing in the way.", 24, p.OnBackground, FontWeights.Normal, 2).Margin(0, 16, 0, 0));
        column.Children.Add(Ui.Text("No ads. Just your mixes, your playlists and the songs you keep coming back to.", 16, p.OnSurfaceVariant, lines: 3).Margin(0, 10, 0, 0));
        var signIn = Ui.Button(null, "Sign in with Google", p.Primary, p.OnPrimary, window.SignIn, new CornerRadius(32), 64, 18);
        signIn.Margin = new Thickness(0, 28, 0, 0);
        column.Children.Add(signIn);
        var guest = Ui.Button(null, "Continue without an account", Theme.Clear, p.Primary, () =>
        {
            SessionStore.Save(Session.GuestSession);
            window.SessionChanged();
        }, new CornerRadius(26), 52, 16);
        guest.Margin = new Thickness(0, 8, 0, 0);
        column.Children.Add(guest);
        Children.Add(column);
    }
}

/**
 * Google sign-in inside Wave, on Edge WebView2 (part of Windows). Google's own page; once it
 * reaches YouTube Music signed in, Wave keeps the session (your password never touches Wave).
 */
public static class Login
{
    private static string DataFolder => Path.Combine(Paths.Cache, "..", "SignIn");

    /** Forget the web session too. */
    public static void SignOut()
    {
        try { Directory.Delete(DataFolder, recursive: true); } catch (Exception) { }
    }

    public static string Folder => Path.GetFullPath(DataFolder);
}

public sealed class LoginView : Grid
{
    private const string SignInUrl = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com";
    private readonly MainWindow window;
    private readonly WebView2 web = new();
    private readonly TextBlock status;
    private bool done;

    public LoginView(MainWindow window)
    {
        this.window = window;
        var p = Theme.App;
        Background = p.Background;
        var layout = Ui.Grid("Auto,*");
        layout.Padding = new Thickness(0, MainWindow.TitleBarHeight, 0, 0);
        var header = Ui.Grid(columns: "Auto,*");
        header.Margin = new Thickness(16, 4, 16, 8);
        header.Children.Add(Ui.IconButton(Glyph.Close, "Cancel", p.OnBackground, () => window.CloseOverlay(this), 48, 24).At(0, 0));
        status = Ui.Text("Sign in to Google", 18, p.OnBackground, FontWeights.Bold);
        status.VerticalAlignment = VerticalAlignment.Center;
        status.Margin = new Thickness(8, 0, 0, 0);
        header.Children.Add(status.At(0, 1));
        layout.Children.Add(header.At(0));
        var frame = new Border { CornerRadius = new CornerRadius(24), Margin = new Thickness(16, 0, 16, 16), Child = web, MaxWidth = 560 };
        layout.Children.Add(frame.At(1));
        Children.Add(layout);
        Loaded += async (_, _) => await StartAsync();
    }

    private async Task StartAsync()
    {
        try
        {
            Environment.SetEnvironmentVariable("WEBVIEW2_USER_DATA_FOLDER", Login.Folder);
            var env = await CoreWebView2Environment.CreateWithOptionsAsync(null, Login.Folder, new CoreWebView2EnvironmentOptions());
            await web.EnsureCoreWebView2Async(env);
            web.CoreWebView2.NavigationCompleted += async (_, _) => await CheckAsync();
            web.CoreWebView2.Navigate(SignInUrl);
        }
        catch (Exception e)
        {
            Log.Write("login", e);
            status.Text = "Signing in needs Microsoft Edge WebView2, which this PC doesn't have.";
            var get = Ui.Button(null, "Get WebView2", Theme.App.Primary, Theme.App.OnPrimary,
                () => _ = Windows.System.Launcher.LaunchUriAsync(new Uri("https://go.microsoft.com/fwlink/p/?LinkId=2124703")), new CornerRadius(20), 44);
            get.HorizontalAlignment = HorizontalAlignment.Center;
            get.VerticalAlignment = VerticalAlignment.Center;
            Children.Add(get);
        }
    }

    /** On YouTube Music and signed in: read the session, pick a channel, done. */
    private async Task CheckAsync()
    {
        if (done || web.CoreWebView2?.Source is not { } source || !new Uri(source).Host.Equals("music.youtube.com", StringComparison.OrdinalIgnoreCase)) return;
        web.Visibility = Visibility.Collapsed;
        status.Text = "Connecting your YouTube Music…";
        for (var attempt = 0; attempt < 20 && !done; attempt++)
        {
            var raw = await web.CoreWebView2.ExecuteScriptAsync(
                "(function(){var c=(window.yt&&window.yt.config_)||{};return JSON.stringify({v:c.VISITOR_DATA||'',d:c.DATASYNC_ID||'',a:String(c.SESSION_INDEX||0)});})()");
            var json = System.Text.Json.JsonSerializer.Deserialize<string>(raw) ?? "{}";
            var page = System.Text.Json.JsonDocument.Parse(json).RootElement;
            var cookies = await web.CoreWebView2.CookieManager.GetCookiesAsync("https://music.youtube.com");
            var visitor = page.GetProperty("v").GetString() ?? "";
            if (cookies.Any(c => c.Name == "SAPISID") && visitor.Length > 0)
            {
                done = true;
                var cookie = string.Join("; ", cookies.Select(c => c.Name + "=" + c.Value));
                await FinishAsync(cookie, visitor, (page.GetProperty("d").GetString() ?? "").Split("||")[0], page.GetProperty("a").GetString() ?? "0");
                return;
            }
            await Task.Delay(500);
        }
        if (!done) web.Visibility = Visibility.Visible;
    }

    private async Task FinishAsync(string cookie, string visitor, string dataSyncId, string authUser)
    {
        var api = App.Services.Api;
        api.UseSession(cookie, visitor, dataSyncId, authUser);
        try
        {
            // Several channels on this Google account: ask which one.
            var channels = await App.Services.YouTube.ChannelsAsync();
            if (channels.Count > 1)
            {
                var p = Theme.App;
                var column = new StackPanel { Spacing = 4 };
                Dialog? dialog = null;
                var chosen = Math.Max(0, channels.ToList().FindIndex(c => c.Selected));
                for (var i = 0; i < channels.Count; i++)
                {
                    var index = i;
                    var c = channels[i];
                    var tap = new Tap { CornerRadius = new CornerRadius(20), Padding = new Thickness(12), Background = c.Selected ? p.PrimaryContainer : p.SurfaceContainerHighest };
                    var row = Ui.Grid(columns: "Auto,*");
                    row.ColumnSpacing = 12;
                    row.Children.Add(Ui.Art(c.Photo, 40, 20).At(0, 0));
                    var names = Ui.Column(0, Ui.Text(c.Name, 16, p.OnBackground, FontWeights.Bold), Ui.Text(c.Handle ?? "", 13, p.OnSurfaceVariant));
                    names.VerticalAlignment = VerticalAlignment.Center;
                    row.Children.Add(names.At(0, 1));
                    tap.Children.Add(row);
                    tap.Click += () => { chosen = index; dialog?.Close(1); };
                    column.Children.Add(tap);
                }
                dialog = new Dialog("Which channel?").Content(new ScrollViewer { Content = column, MaxHeight = 400 });
                await dialog.ShowAsync();
                var pick = channels[chosen];
                if (!pick.Selected && pick.DataSyncId is { } id) dataSyncId = id.Split("||")[0];
                api.UseSession(cookie, visitor, dataSyncId, authUser);
            }
            var account = await App.Services.YouTube.AccountAsync();
            if (account is null) throw new InvalidOperationException("No account");
            Likes.Stale();
            SessionStore.Save(new Session(cookie, visitor, dataSyncId, authUser, account.Name, account.Email, account.Photo));
            window.CloseOverlay(this);
            window.SessionChanged();
        }
        catch (Exception e)
        {
            Log.Write("login-finish", e);
            status.Text = "Signed in, but couldn't load your YouTube Music account. Try again.";
            done = false;
            web.Visibility = Visibility.Visible;
        }
    }
}
