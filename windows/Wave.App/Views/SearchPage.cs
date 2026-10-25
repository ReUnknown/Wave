using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Wave.Data;
using Wave.Music;

namespace Wave.App.Views;

/**
 * Search: the field up top (suggestions while you type, your recent searches when it's empty),
 * then results as Top / Songs / Artists / Albums / Playlists. Only a result that really matches
 * what you typed gets the big top spot.
 */
public sealed class SearchPage : WavePage
{
    private readonly Palette p = Theme.App;
    private readonly TextBox box;
    private readonly StackPanel body = new() { Padding = new Thickness(0, 0, 0, 48) };
    private readonly StackPanel chips = Ui.Row(8);
    private string? submitted;
    private SearchFilter filter = SearchFilter.All;
    private CancellationTokenSource? typing;

    public SearchPage(MainWindow window)
    {
        var layout = Ui.Grid("Auto,Auto,*");
        layout.Padding = new Thickness(0, MainWindow.TitleBarHeight + 4, 0, 0);
        var top = Ui.Grid(columns: "Auto,*");
        top.ColumnSpacing = 8;
        top.Margin = new Thickness(16, 0, 16, 0);
        top.MaxWidth = 792;
        top.HorizontalAlignment = HorizontalAlignment.Left;
        top.Children.Add(Ui.IconButton(Glyph.Back, "Back", p.OnBackground, window.Pop, 48, 24).At(0, 0));
        box = new TextBox
        {
            PlaceholderText = "Search songs, artists, albums",
            FontSize = 16,
            Height = 56,
            CornerRadius = new CornerRadius(28),
            Padding = new Thickness(20, 16, 20, 0),
            Background = p.Faint,
            BorderBrush = p.Track,
        };
        box.TextChanged += (_, _) => OnTyping();
        box.KeyDown += OnKey;
        top.Children.Add(box.At(0, 1));
        layout.Children.Add(top.At(0));
        chips.Margin = new Thickness(24, 12, 24, 4);
        chips.Visibility = Visibility.Collapsed;
        foreach (var (label, f) in new[] { ("Top", SearchFilter.All), ("Songs", SearchFilter.Songs), ("Artists", SearchFilter.Artists), ("Albums", SearchFilter.Albums), ("Playlists", SearchFilter.Playlists) })
        {
            var chip = Ui.Button(null, label, p.Faint, p.OnBackground, () => { filter = f; PaintChips(); _ = SearchAsync(); }, new CornerRadius(18), 36, 14, 0.95f);
            chip.Tag = f;
            chips.Children.Add(chip);
        }
        layout.Children.Add(chips.At(1));
        layout.Children.Add(new ScrollViewer { Content = body, VerticalScrollBarVisibility = ScrollBarVisibility.Auto }.At(2));
        Children.Add(layout);
        Loaded += (_, _) => FocusBox();
        ShowRecent();
    }

    public void FocusBox()
    {
        box.Focus(FocusState.Programmatic);
        box.SelectAll();
    }

    private void PaintChips()
    {
        foreach (var c in chips.Children.OfType<Tap>())
        {
            var on = (SearchFilter)c.Tag == filter;
            c.Background = on ? p.Primary : p.Faint;
            ((TextBlock)((StackPanel)c.Children[0]).Children[0]).Foreground = on ? p.OnPrimary : p.OnBackground;
        }
    }

    private async void OnKey(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key != Windows.System.VirtualKey.Enter || string.IsNullOrWhiteSpace(box.Text)) return;
        e.Handled = true;
        var q = box.Text.Trim();
        // A pasted YouTube Music link opens straight away.
        if (q.StartsWith("http") && await Links.OpenAsync(q)) return;
        submitted = q;
        SearchHistory.Remember(q);
        filter = SearchFilter.All;
        PaintChips();
        _ = SearchAsync();
    }

    private async void OnTyping()
    {
        typing?.Cancel();
        var q = box.Text.Trim();
        if (q == submitted) return;
        if (q.Length == 0) { ShowRecent(); return; }
        var cts = typing = new CancellationTokenSource();
        try
        {
            await Task.Delay(180, cts.Token);
            var s = await App.Services.YouTube.SuggestionsAsync(q, cts.Token);
            if (cts.IsCancellationRequested) return;
            chips.Visibility = Visibility.Collapsed;
            body.Children.Clear();
            foreach (var query in s.Queries.Take(6)) body.Children.Add(QueryRow(query, Glyph.Search));
            var items = s.Items.Where(i => i is not Song { IsVideo: true } && i is not Song { IsEpisode: true }).Take(4).ToList();
            if (items.Count > 0)
            {
                var rows = new StackPanel { Margin = new Thickness(14, 8, 14, 0), MaxWidth = 776, HorizontalAlignment = HorizontalAlignment.Left };
                foreach (var item in items) rows.Children.Add(Items.Row(item, p));
                body.Children.Add(rows);
            }
        }
        catch (Exception) { }
    }

    private FrameworkElement QueryRow(string query, string glyph, bool removable = false)
    {
        var tap = new Tap { Height = 48, CornerRadius = new CornerRadius(24), Padding = new Thickness(16, 0, 8, 0), Margin = new Thickness(14, 0, 14, 0), MaxWidth = 776, HorizontalAlignment = HorizontalAlignment.Stretch };
        var grid = Ui.Grid(columns: "Auto,*,Auto");
        grid.ColumnSpacing = 16;
        grid.Children.Add(Ui.Center(Ui.Icon(glyph, 22, p.OnSurfaceVariant)).At(0, 0));
        var text = Ui.Text(query, 16, p.OnBackground);
        text.VerticalAlignment = VerticalAlignment.Center;
        grid.Children.Add(text.At(0, 1));
        if (removable) grid.Children.Add(Ui.IconButton(Glyph.Close, "Forget", p.OnSurfaceVariant, () => { SearchHistory.Forget(query); ShowRecent(); }, 36, 18).At(0, 2));
        tap.Children.Add(grid);
        tap.PointerEntered += (_, _) => tap.Background = p.Fainter;
        tap.PointerExited += (_, _) => tap.Background = Theme.Clear;
        tap.Click += () =>
        {
            box.Text = query;
            submitted = query;
            SearchHistory.Remember(query);
            filter = SearchFilter.All;
            PaintChips();
            _ = SearchAsync();
        };
        return tap;
    }

    private void ShowRecent()
    {
        submitted = null;
        chips.Visibility = Visibility.Collapsed;
        body.Children.Clear();
        var recent = SearchHistory.All;
        if (recent.Count == 0) return;
        body.Children.Add(Ui.LabelSmall("Recent searches", p.OnSurfaceVariant).Margin(30, 16, 24, 8));
        foreach (var q in recent) body.Children.Add(QueryRow(q, Glyph.History, removable: true));
    }

    private async Task SearchAsync()
    {
        if (submitted is not { } q) return;
        chips.Visibility = Visibility.Visible;
        body.Children.Clear();
        var ring = new ProgressRing { IsActive = true, Width = 48, Height = 48, Foreground = p.Primary, Margin = new Thickness(0, 60, 0, 0) };
        body.Children.Add(ring);
        var asked = filter;
        try
        {
            var groups = await App.Services.SearchAsync(q, filter);
            if (submitted != q || asked != filter) return;
            body.Children.Clear();
            if (groups.Count == 0) body.Children.Add(Ui.BodyMedium("Nothing found", p.OnSurfaceVariant).Margin(28, 24, 0, 0));
            foreach (var group in groups)
            {
                var items = group.Items.ToList();
                if (filter == SearchFilter.All && group == groups[0] && items.Count > 0 && IsStrongMatch(q, items[0]))
                {
                    body.Children.Add(Items.Header(null, "Top result", p));
                    body.Children.Add(TopResult(items[0]));
                    items = items.Skip(1).ToList();
                    if (items.Count == 0) continue;
                    body.Children.Add(Items.Header(null, "More from the top result", p));
                }
                else body.Children.Add(Items.Header(null, group.Title, p));
                if (items.All(i => i is Song) || filter != SearchFilter.All && filter == SearchFilter.Songs)
                {
                    var rows = new StackPanel { Margin = new Thickness(14, 0, 14, 0), MaxWidth = 900, HorizontalAlignment = HorizontalAlignment.Left };
                    foreach (var item in items.Take(filter == SearchFilter.All ? 4 : 50)) rows.Children.Add(Items.Row(item, p));
                    body.Children.Add(rows);
                }
                else body.Children.Add(Items.CardRows(items.Select(i => Items.Card(i, p)), filter == SearchFilter.All ? 1 : 20));
            }
        }
        catch (Exception)
        {
            if (submitted != q) return;
            body.Children.Clear();
            body.Children.Add(Ui.BodyMedium("Couldn't search right now", p.OnSurfaceVariant).Margin(28, 24, 0, 0));
        }
    }

    /** Typing "e" shouldn't crown whatever YouTube happens to list first. */
    private static bool IsStrongMatch(string query, Item item)
    {
        static string Norm(string s) => new(s.ToLowerInvariant().Where(char.IsLetterOrDigit).ToArray());
        var q = Norm(query);
        if (q.Length < 2) return false;
        var title = Norm(item.Title);
        var subtitle = Norm(item.Subtitle);
        return title.Contains(q) || q.Contains(title) && title.Length >= 3 || (title + subtitle).Contains(q) || q.Split().All(w => (title + subtitle).Contains(w));
    }

    /** The top result, big: a sharp cover with its name and what it is. */
    private FrameworkElement TopResult(Item item)
    {
        var tap = new Tap { CornerRadius = new CornerRadius(28), Background = p.Fainter, Padding = new Thickness(16), Margin = new Thickness(24, 0, 24, 0), MaxWidth = 640, HorizontalAlignment = HorizontalAlignment.Left };
        var grid = Ui.Grid(columns: "Auto,*");
        grid.ColumnSpacing = 20;
        grid.Children.Add(Ui.Art(item.Thumbnail, 112, item is Artist ? 56 : 20).At(0, 0));
        var kind = item switch { Song => "Song", Album => "Album", Artist => "Artist", Playlist => "Playlist", _ => "" };
        var text = Ui.Column(4, Ui.Text(item.Title, 26, p.OnBackground, FontWeights.ExtraBold, 2), Ui.BodyMedium(string.Join(" • ", new[] { kind, item.Subtitle }.Where(s => s.Length > 0)), p.OnSurfaceVariant));
        text.VerticalAlignment = VerticalAlignment.Center;
        grid.Children.Add(text.At(0, 1));
        tap.Children.Add(grid);
        tap.Click += () => Actions.Open(item);
        ItemMenu.Attach(tap, item);
        return tap.Pressable(0.98f);
    }
}
