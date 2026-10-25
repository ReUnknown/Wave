using System.IO.Pipes;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Markup;
using Wave.Data;

namespace Wave.App;

/**
 * Wave for Windows. Everything is built in code on WinUI 3, so the app carries no XAML; WinUI's
 * own controls (menus, dialogs, text boxes) still come from XamlControlsResources.
 */
public partial class App : Application, IXamlMetadataProvider
{
    private readonly Microsoft.UI.Xaml.XamlTypeInfo.XamlControlsXamlMetaDataProvider provider = new();
    private readonly string[] args;

    public static Services Services { get; private set; } = null!;
    public static AudioEngine Audio { get; private set; } = null!;
    public static MainWindow Window { get; private set; } = null!;
    public static string Version { get; } = typeof(App).Assembly
        .GetCustomAttributes(typeof(System.Reflection.AssemblyInformationalVersionAttribute), false)
        .OfType<System.Reflection.AssemblyInformationalVersionAttribute>().FirstOrDefault()?.InformationalVersion ?? "1.2.0";

    public App(string[] args)
    {
        this.args = args;
        UnhandledException += (_, e) =>
        {
            // Log, keep running: one bad callback shouldn't close your music.
            Log.Write("unhandled", e.Exception);
            e.Handled = true;
        };
        TaskScheduler.UnobservedTaskException += (_, e) => { Log.Write("task", e.Exception); e.SetObserved(); };
    }

    public IXamlType GetXamlType(Type type) => provider.GetXamlType(type);
    public IXamlType GetXamlType(string fullName) => provider.GetXamlType(fullName);
    public XmlnsDefinition[] GetXmlnsDefinitions() => provider.GetXmlnsDefinitions();

    protected override void OnLaunched(LaunchActivatedEventArgs e)
    {
        Resources.MergedDictionaries.Add(new Microsoft.UI.Xaml.Controls.XamlControlsResources());
        Theme.Install(Resources);

        Audio = new AudioEngine();
        Services = new Services(Audio, Version);
        Audio.Attach(Services.Player);
        Services.Apply(SessionStore.Restore());
        Services.Player.Restore();
        _ = Services.Api.WarmUpAsync();

        Window = new MainWindow();
        Window.Activate();
        SingleInstance.Listen(link => Window.DispatcherQueue.TryEnqueue(() => Window.OpenLink(link)));
        if (args.FirstOrDefault(a => a.StartsWith("http")) is { } first) Window.OpenLink(first);
    }
}

/** Errors worth knowing about, in %APPDATA%\Wave\native.log (kept small). */
public static class Log
{
    public static void Write(string what, Exception? e)
    {
        try
        {
            var path = Paths.File("native.log");
            if (File.Exists(path) && new FileInfo(path).Length > 256 * 1024) File.Delete(path);
            File.AppendAllText(path, $"{DateTime.Now:s} {what}: {e}{Environment.NewLine}");
        }
        catch (Exception) { }
    }
}

/** One Wave at a time: a second launch hands its link to the running one and quits. */
public static class SingleInstance
{
    private const string PipeName = "Wave.Native.Links";
    private static Mutex? mutex;

    /** True when another Wave is running (and has been handed [args]). */
    public static bool HandOff(string[] args)
    {
        mutex = new Mutex(true, "Wave.Native.Instance", out var first);
        if (first) return false;
        try
        {
            using var pipe = new NamedPipeClientStream(".", PipeName, PipeDirection.Out);
            pipe.Connect(1500);
            using var writer = new StreamWriter(pipe);
            writer.WriteLine(args.FirstOrDefault(a => a.StartsWith("http")) ?? "show");
        }
        catch (Exception) { }
        return true;
    }

    public static void Listen(Action<string> onMessage)
    {
        _ = Task.Run(async () =>
        {
            while (true)
            {
                try
                {
                    await using var pipe = new NamedPipeServerStream(PipeName, PipeDirection.In, 1, PipeTransmissionMode.Byte, PipeOptions.Asynchronous);
                    await pipe.WaitForConnectionAsync();
                    using var reader = new StreamReader(pipe);
                    if (await reader.ReadLineAsync() is { } line) onMessage(line);
                }
                catch (Exception) { await Task.Delay(500); }
            }
        });
    }
}
