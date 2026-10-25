// Wave's Google sign-in window on Windows, on Edge WebView2 (nothing downloaded).
// Google's sign-in page runs here; once it lands on music.youtube.com signed in, this prints the
// session (cookies and page config) as one line of JSON to Wave and quits. If anything goes wrong
// it prints {"error": "..."} instead (and logs to %LOCALAPPDATA%\Wave\SignIn\signin.log), never a
// crash dialog.
//   WaveSignIn [url]        sign in (or open a channel-switch URL)
//   WaveSignIn --sign-out   forget the web session
using System;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

static class Program
{
    const string SignInUrl = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com";

    static string DataFolder => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Wave", "SignIn");

    [STAThread]
    static int Main(string[] args)
    {
        if (args.Length > 0 && args[0] == "--sign-out")
        {
            try { if (Directory.Exists(DataFolder)) Directory.Delete(DataFolder, true); } catch { }
            return 0;
        }
        // No "unhandled exception" dialogs: anything that goes wrong is reported to Wave.
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (s, e) => Fail("crash", e.Exception);
        AppDomain.CurrentDomain.UnhandledException += (s, e) => Fail("crash", e.ExceptionObject as Exception);
        TaskScheduler.UnobservedTaskException += (s, e) => { e.SetObserved(); Fail("crash", e.Exception); };

        // Edge WebView2 ships with Windows 11 (and most Windows 10); without it there's nothing to sign in on.
        string edge;
        try { edge = CoreWebView2Environment.GetAvailableBrowserVersionString(); }
        catch (Exception e) { return Fail("webview2-missing", e); }
        if (string.IsNullOrEmpty(edge)) return Fail("webview2-missing", new Exception("WebView2 Runtime not found"));

        Application.EnableVisualStyles();
        var form = new Form { Text = "Sign in to Wave", Width = 500, Height = 720, StartPosition = FormStartPosition.CenterScreen };
        var web = new WebView2 { Dock = DockStyle.Fill, DefaultBackgroundColor = Color.FromArgb(17, 20, 24) };
        form.Controls.Add(web);
        var exitCode = 1;
        var done = false;
        form.Load += async (s, e) =>
        {
          try
          {
            var env = await CoreWebView2Environment.CreateAsync(null, DataFolder);
            await web.EnsureCoreWebView2Async(env);
            web.CoreWebView2.NavigationCompleted += async (s2, e2) =>
            {
                if (done || new Uri(web.Source.ToString()).Host != "music.youtube.com") return;
                form.Hide();
                for (var attempt = 0; attempt < 20 && !done; attempt++)
                {
                    var raw = await web.CoreWebView2.ExecuteScriptAsync(
                        "(function(){var c=(window.yt&&window.yt.config_)||{};return JSON.stringify({v:c.VISITOR_DATA||'',d:c.DATASYNC_ID||'',a:String(c.SESSION_INDEX||0)});})()");
                    var cookies = await web.CoreWebView2.CookieManager.GetCookiesAsync("https://music.youtube.com");
                    var page = Json.ParseFlat(Json.Unquote(raw));
                    var visitor = page.ContainsKey("v") ? page["v"] : "";
                    if (cookies.Any(c => c.Name == "SAPISID") && visitor.Length > 0)
                    {
                        var cookie = string.Join("; ", cookies.Select(c => c.Name + "=" + c.Value));
                        var line = "{\"cookie\":" + Json.Quote(cookie) + ",\"visitorData\":" + Json.Quote(visitor) +
                                   ",\"dataSyncId\":" + Json.Quote(page.ContainsKey("d") ? page["d"] : "") +
                                   ",\"authUser\":" + Json.Quote(page.ContainsKey("a") ? page["a"] : "0") + "}";
                        var stdout = new StreamWriter(Console.OpenStandardOutput(), new UTF8Encoding(false)) { AutoFlush = true };
                        stdout.WriteLine(line);
                        done = true;
                        exitCode = 0;
                        form.Close();
                        return;
                    }
                    await Task.Delay(500);
                }
                if (!done) form.Show();
            };
            web.CoreWebView2.Navigate(args.Length > 0 ? args[0] : SignInUrl);
          }
          catch (Exception ex)
          {
            Fail("crash", ex);
          }
        };
        Application.Run(form);
        return exitCode;
    }

    static readonly object reported = new object();
    static bool failed;

    /** Tells Wave what went wrong (one JSON line), logs the details and quits. */
    static int Fail(string code, Exception e)
    {
        lock (reported)
        {
            if (failed) return 2;
            failed = true;
        }
        try
        {
            Directory.CreateDirectory(DataFolder);
            File.AppendAllText(Path.Combine(DataFolder, "signin.log"), DateTime.Now.ToString("s") + " " + code + ": " + e + Environment.NewLine);
        }
        catch { }
        try
        {
            var stdout = new StreamWriter(Console.OpenStandardOutput(), new UTF8Encoding(false)) { AutoFlush = true };
            stdout.WriteLine("{\"error\":" + Json.Quote(code) + ",\"detail\":" + Json.Quote(e?.Message ?? "") + "}");
        }
        catch { }
        Environment.Exit(2);
        return 2;
    }
}

/** Just enough JSON for one flat object of strings. */
static class Json
{
    public static string Quote(string s)
    {
        var b = new StringBuilder("\"");
        foreach (var ch in s)
        {
            if (ch == '"' || ch == '\\') b.Append('\\').Append(ch);
            else if (ch < 0x20) b.Append("\\u").Append(((int)ch).ToString("x4"));
            else b.Append(ch);
        }
        return b.Append('"').ToString();
    }

    public static string Unquote(string s)
    {
        if (s == null || s.Length < 2 || s[0] != '"') return s ?? "";
        var b = new StringBuilder();
        for (var i = 1; i < s.Length - 1; i++)
        {
            var ch = s[i];
            if (ch == '\\' && i + 1 < s.Length - 1)
            {
                var n = s[++i];
                if (n == 'u') { b.Append((char)Convert.ToInt32(s.Substring(i + 1, 4), 16)); i += 4; }
                else b.Append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
            }
            else b.Append(ch);
        }
        return b.ToString();
    }

    public static System.Collections.Generic.Dictionary<string, string> ParseFlat(string json)
    {
        var map = new System.Collections.Generic.Dictionary<string, string>();
        foreach (System.Text.RegularExpressions.Match m in System.Text.RegularExpressions.Regex.Matches(json ?? "", "\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\""))
            map[m.Groups[1].Value] = Unquote("\"" + m.Groups[2].Value + "\"");
        return map;
    }
}
