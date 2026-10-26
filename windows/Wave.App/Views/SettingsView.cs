using System.Diagnostics;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Wave.Data;

namespace Wave.App.Views;

/** Settings, and keeping Wave up to date. */
public static class SettingsView
{
    private static Updater.Release? available;
    private static DateTime lastCheck = DateTime.MinValue;

    /** Once a day (if allowed): a newer Wave on GitHub is offered with a notice. */
    public static async void CheckForUpdates(bool force = false)
    {
        if (!force && (!SettingsStore.Current.CheckUpdates || DateTime.UtcNow - lastCheck < TimeSpan.FromHours(24))) return;
        lastCheck = DateTime.UtcNow;
        try
        {
            available = await App.Services.Updater.CheckAsync();
            if (available is not null) Notices.Show($"Wave {available.Version} is ready. Open Settings to update.");
            else if (force) Notices.Show("Wave is up to date");
        }
        catch (Exception)
        {
            if (force) Notices.Show("Couldn't check for updates");
        }
    }

    /** Downloads the installer, then hands over: it closes Wave, updates quietly and reopens it. */
    private static async Task InstallAsync(Updater.Release release, ProgressBar bar)
    {
        try
        {
            var folder = Path.Combine(Paths.Cache, "Updates");
            var file = await App.Services.Updater.DownloadAsync(release, folder, new Progress<double>(v => bar.Value = v * 100));
            Process.Start(new ProcessStartInfo("cmd.exe", $"/c start \"\" \"{file}\" /SILENT /SUPPRESSMSGBOXES /NORESTART /RELAUNCH=1")
            {
                CreateNoWindow = true,
                UseShellExecute = false,
            });
            MainWindow.Quit();
        }
        catch (Exception)
        {
            Notices.Show("Couldn't download the update");
        }
    }

    public static async void Open(MainWindow window)
    {
        var p = Theme.App;
        var column = new StackPanel { Spacing = 6, Width = 480 };

        void Section(string title) => column.Children.Add(Ui.LabelSmall(title, p.Primary).Margin(4, 18, 0, 4));

        Border Card(UIElement child) => new() { Background = p.SurfaceContainer, CornerRadius = new CornerRadius(20), Child = child };

        void Toggle(string title, string detail, bool value, Func<Settings, bool, Settings> set)
        {
            var row = Ui.Grid(columns: "*,Auto");
            row.ColumnSpacing = 16;
            row.Padding = new Thickness(18, 14, 16, 14);
            var text = Ui.Column(2, Ui.Text(title, 15, p.OnBackground, FontWeights.SemiBold), Ui.Text(detail, 13, p.OnSurfaceVariant, lines: 0));
            row.Children.Add(text.At(0, 0));
            var toggle = new Switch(value) { VerticalAlignment = VerticalAlignment.Center };
            toggle.Toggled += on => SettingsStore.Update(s => set(s, on));
            row.Children.Add(toggle.At(0, 1));
            column.Children.Add(Card(row));
        }

        void Choice(string title, string[] options, int selected, Action<int> pick)
        {
            var box = new StackPanel { Spacing = 10, Padding = new Thickness(18, 14, 16, 14) };
            box.Children.Add(Ui.Text(title, 15, p.OnBackground, FontWeights.SemiBold));
            box.Children.Add(new Segmented(options, selected, pick));
            column.Children.Add(Card(box));
        }

        var s = SettingsStore.Current;
        Section("Look & feel");
        Toggle("Match song colors", "The whole app takes on the colors of what's playing", s.MatchSongColors, (x, v) => x with { MatchSongColors = v });
        Toggle("Move with the music", "Shapes turn while music plays", s.MoveWithMusic, (x, v) => x with { MoveWithMusic = v });
        Section("Playback");
        Choice("Audio quality", ["Best available", "Data saver"], s.AudioQuality == AudioQuality.High ? 0 : 1,
            i => SettingsStore.Update(x => x with { AudioQuality = i == 0 ? AudioQuality.High : AudioQuality.Low }));
        Toggle("Autoplay", "When a playlist ends, keep going with songs like it", s.Autoplay, (x, v) => x with { Autoplay = v });
        Toggle("Even volume", "Loud masters are turned down so every song plays at a similar volume", s.EvenVolume, (x, v) => x with { EvenVolume = v });
        var fades = new[] { 0, 3, 6, 9, 12 };
        Choice("Crossfade", ["Off", "3 s", "6 s", "9 s", "12 s"], Math.Max(0, Array.IndexOf(fades, s.CrossfadeSeconds)),
            i => SettingsStore.Update(x => x with { CrossfadeSeconds = fades[i] }));
        Toggle("Player beside the app", "The player opens on half the window instead of filling it", s.SplitPlayer, (x, v) => x with { SplitPlayer = v });

        Section("About");
        Toggle("Check for updates", "Look for a new version of Wave once a day", s.CheckUpdates, (x, v) => x with { CheckUpdates = v });
        var about = new StackPanel { Spacing = 12, Padding = new Thickness(18, 14, 16, 16) };
        about.Children.Add(Ui.Text($"Wave {App.Version} for Windows", 15, p.OnBackground, FontWeights.SemiBold));
        var bar = new ProgressBar { Maximum = 100, Visibility = Visibility.Collapsed, Foreground = p.Primary };
        Tap action = available is { } release
            ? Ui.Button(Glyph.Update, $"Update to {release.Version}", p.Primary, p.OnPrimary, () =>
            {
                bar.Visibility = Visibility.Visible;
                _ = InstallAsync(release, bar);
            }, new CornerRadius(20), 44)
            : Ui.Button(null, "Check for updates", p.Faint, p.OnBackground, () => CheckForUpdates(force: true), new CornerRadius(20), 44);
        action.HorizontalAlignment = HorizontalAlignment.Left;
        about.Children.Add(action);
        about.Children.Add(bar);
        column.Children.Add(Card(about));

        await new Dialog("Settings").Content(new ScrollViewer { Content = column, MaxHeight = 540, Padding = new Thickness(0, 0, 14, 0) }).Button("Done", 0, primary: true).ShowAsync();
        _ = window;
    }
}
