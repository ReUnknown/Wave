using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;

namespace Wave.App;

public static class Program
{
    [STAThread]
    public static void Main(string[] args)
    {
        AppDomain.CurrentDomain.UnhandledException += (_, e) => Crash.Report(e.ExceptionObject as Exception);
        try
        {
            Run(args);
        }
        catch (Exception e)
        {
            Crash.Report(e);
        }
    }

    private static void Run(string[] args)
    {
        WinRT.ComWrappersSupport.InitializeComWrappers();
        // One Wave at a time: a second launch (a link opened with Wave) goes to the one running.
        if (SingleInstance.HandOff(args)) return;
        Application.Start(_p =>
        {
            SynchronizationContext.SetSynchronizationContext(new DispatcherQueueSynchronizationContext(DispatcherQueue.GetForCurrentThread()));
            _ = new App(args);
        });
    }
}

/** Wave couldn't start: say why (a message box, and %APPDATA%\Wave\native-crash.txt) instead of vanishing. */
public static partial class Crash
{
    public static void Report(Exception? e)
    {
        var text = e?.ToString() ?? "Unknown error";
        try
        {
            var dir = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Wave");
            Directory.CreateDirectory(dir);
            File.WriteAllText(System.IO.Path.Combine(dir, "native-crash.txt"), $"{DateTime.Now:s}\n{text}");
        }
        catch (Exception) { }
        MessageBoxW(0, "Wave couldn't start.\n\n" + (e?.Message ?? "") + "\n\nDetails are in %APPDATA%\\Wave\\native-crash.txt", "Wave", 0x10);
        Environment.Exit(1);
    }

    [System.Runtime.InteropServices.LibraryImport("user32.dll", StringMarshalling = System.Runtime.InteropServices.StringMarshalling.Utf16)]
    private static partial int MessageBoxW(nint hwnd, string text, string caption, uint type);
}
