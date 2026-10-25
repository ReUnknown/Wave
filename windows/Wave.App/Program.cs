using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;

namespace Wave.App;

public static class Program
{
    [STAThread]
    public static void Main(string[] args)
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
