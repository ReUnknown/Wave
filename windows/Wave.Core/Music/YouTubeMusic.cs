using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace Wave.Music;

/**
 * Everything Wave asks YouTube Music for, turned into Wave's own small models. Only the fields
 * Wave shows are read; YouTube's renderers are walked as plain JSON.
 */
public sealed partial class YouTubeMusic(InnerTube api)
{
    public InnerTube Api { get; } = api;

    // ---------------------------------------------------------------- Home

    public async Task<HomePage> HomeAsync(string? continuation = null, CancellationToken ct = default)
    {
        JsonNode root;
        JsonNode? list;
        if (continuation is null)
        {
            root = await Api.PostAsync("browse", new JsonObject { ["browseId"] = "FEmusic_home" }, ct);
            list = root.At("contents", "singleColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content", "sectionListRenderer");
        }
        else
        {
            root = await Api.PostAsync("browse", new JsonObject(), ct, $"ctoken={Uri.EscapeDataString(continuation)}&continuation={Uri.EscapeDataString(continuation)}&type=next");
            list = root.At("continuationContents", "sectionListContinuation");
        }
        var sections = list.Each("contents").Select(Carousel).OfType<Section>().ToList();
        var next = list.Str("continuations", 0, "nextContinuationData", "continuation");
        return new HomePage(sections, next);
    }

    /** A carousel (or a shelf) of cards as a Section. */
    private static Section? Carousel(JsonNode node)
    {
        var shelf = node.At("musicCarouselShelfRenderer") ?? node.At("musicImmersiveCarouselShelfRenderer");
        if (shelf is not null)
        {
            var header = shelf.At("header", "musicCarouselShelfBasicHeaderRenderer") ?? shelf.At("header", "musicImmersiveCarouselShelfBasicHeaderRenderer");
            var title = header.At("title").Text();
            if (string.IsNullOrWhiteSpace(title)) return null;
            var items = shelf.Each("contents").Select(ParseItem).OfType<Item>().ToList();
            var more = header.At("moreContentButton", "buttonRenderer", "navigationEndpoint", "browseEndpoint");
            return items.Count == 0 ? null : new Section(title, header.At("strapline").Text(), items, more.Str("browseId"), more.Str("params"));
        }
        var musicShelf = node.At("musicShelfRenderer");
        if (musicShelf is not null)
        {
            var title = musicShelf.At("title").Text() ?? "";
            var items = musicShelf.Each("contents").Select(ParseItem).OfType<Item>().ToList();
            var more = musicShelf.At("bottomEndpoint", "browseEndpoint") ?? musicShelf.At("title", "runs", 0, "navigationEndpoint", "browseEndpoint");
            return items.Count == 0 ? null : new Section(title, null, items, more.Str("browseId"), more.Str("params"));
        }
        var grid = node.At("gridRenderer");
        if (grid is not null)
        {
            var title = grid.At("header", "gridHeaderRenderer", "title").Text() ?? "";
            var items = grid.Each("items").Select(ParseItem).OfType<Item>().ToList();
            return items.Count == 0 ? null : new Section(title, null, items);
        }
        return null;
    }

    // ---------------------------------------------------------------- Items

    /** Any card or row YouTube Music sends: a song, album, artist or playlist. */
    internal static Item? ParseItem(JsonNode node)
    {
        if (node.At("musicTwoRowItemRenderer") is { } twoRow) return TwoRow(twoRow);
        if (node.At("musicResponsiveListItemRenderer") is { } row) return Row(row);
        if (node.At("playlistPanelVideoRenderer") is { } panel) return PanelSong(panel);
        if (node.At("playlistPanelVideoWrapperRenderer", "primaryRenderer", "playlistPanelVideoRenderer") is { } wrapped) return PanelSong(wrapped);
        return null;
    }

    private static readonly HashSet<string> TypeWords = new(StringComparer.OrdinalIgnoreCase)
    {
        "Song", "Video", "Episode", "Album", "Single", "EP", "Playlist", "Artist", "Podcast", "Profile", "Station", "Audiobook",
    };

    /** Runs split at " • " into groups of runs. */
    private static List<List<JsonNode>> Groups(IEnumerable<JsonNode> runs)
    {
        var groups = new List<List<JsonNode>> { new() };
        foreach (var run in runs)
        {
            if (run.Str("text") is " • " or "•") groups.Add([]);
            else groups[^1].Add(run);
        }
        return groups.Where(g => g.Count > 0).ToList();
    }

    private static bool IsExplicit(JsonNode? badges) =>
        badges.At(0) is not null && badges is JsonArray a && a.Any(b => b.Str("musicInlineBadgeRenderer", "icon", "iconType") == "MUSIC_EXPLICIT_BADGE");

    private static List<ArtistRef> ArtistsFrom(IEnumerable<JsonNode> runs)
    {
        var groups = Groups(runs);
        var linked = groups.SelectMany(g => g)
            .Where(r => r.At("navigationEndpoint").PageType() is "MUSIC_PAGE_TYPE_ARTIST" or "MUSIC_PAGE_TYPE_USER_CHANNEL")
            .Select(r => new ArtistRef(r.Str("text")!, r.Str("navigationEndpoint", "browseEndpoint", "browseId")))
            .ToList();
        if (linked.Count > 0) return linked;
        // Unlinked credits: the first group that isn't a type word, a year or a play count.
        var group = groups.FirstOrDefault(g =>
        {
            var text = string.Concat(g.Select(r => r.Str("text")));
            return !TypeWords.Contains(text) && !Regex.IsMatch(text, @"^\d{4}$") && !Regex.IsMatch(text, @"\d+(\.\d+)?[KMB]? (plays|views|subscribers|monthly audience)", RegexOptions.IgnoreCase) && !Regex.IsMatch(text, @"^\d+:\d\d");
        });
        if (group is null) return [];
        return string.Concat(group.Select(r => r.Str("text"))).Split([", ", " & "], StringSplitOptions.RemoveEmptyEntries).Select(n => new ArtistRef(n.Trim(), null)).ToList();
    }

    private static AlbumRef? AlbumFrom(IEnumerable<JsonNode> runs) =>
        runs.Where(r => r.At("navigationEndpoint").PageType() == "MUSIC_PAGE_TYPE_ALBUM")
            .Select(r => new AlbumRef(r.Str("text")!, r.Str("navigationEndpoint", "browseEndpoint", "browseId")))
            .FirstOrDefault();

    private static (bool video, bool episode) VideoType(JsonNode? watchEndpoint)
    {
        var type = watchEndpoint.Str("watchEndpointMusicSupportedConfigs", "watchEndpointMusicConfig", "musicVideoType");
        return (type is "MUSIC_VIDEO_TYPE_UGC" or "MUSIC_VIDEO_TYPE_OMV", type is "MUSIC_VIDEO_TYPE_PODCAST_EPISODE");
    }

    private static Item? TwoRow(JsonNode r)
    {
        var title = r.At("title").Text();
        if (string.IsNullOrEmpty(title)) return null;
        var art = r.At("thumbnailRenderer").Thumb();
        var nav = r.At("navigationEndpoint");
        var subtitle = r.At("subtitle").Runs().ToList();
        var explicitBadge = IsExplicit(r.At("subtitleBadges"));
        if (nav.Str("watchEndpoint", "videoId") is { } videoId)
        {
            var (video, episode) = VideoType(nav.At("watchEndpoint"));
            return new Song(videoId, title, ArtistsFrom(subtitle), AlbumFrom(subtitle), art, null, explicitBadge, IsVideo: video, IsEpisode: episode);
        }
        var browseId = nav.Str("browseEndpoint", "browseId");
        if (browseId is null) return null;
        switch (nav.PageType())
        {
            case "MUSIC_PAGE_TYPE_ALBUM":
                var year = subtitle.Select(x => x.Str("text")).LastOrDefault(t => t is not null && Regex.IsMatch(t, @"^\d{4}$"));
                var playlistId = r.Str("thumbnailOverlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint", "watchPlaylistEndpoint", "playlistId");
                return new Album(browseId, playlistId, title, ArtistsFrom(subtitle), year, art, explicitBadge);
            case "MUSIC_PAGE_TYPE_ARTIST":
                return new Artist(browseId, title, art, r.At("subtitle").Text());
            case "MUSIC_PAGE_TYPE_USER_CHANNEL":
                return new Artist(browseId, title, art, r.At("subtitle").Text(), IsProfile: true);
            case "MUSIC_PAGE_TYPE_PLAYLIST":
                var groups = Groups(subtitle).Select(g => string.Concat(g.Select(x => x.Str("text")))).Where(t => t != "Playlist").ToList();
                return new Playlist(browseId.StartsWith("VL") ? browseId[2..] : browseId, title,
                    groups.FirstOrDefault(), groups.Skip(1).FirstOrDefault(), art);
            case "MUSIC_PAGE_TYPE_PODCAST_SHOW_PAGE":
                return new Playlist(browseId, title, r.At("subtitle").Text(), null, art, IsPodcast: true);
            default:
                return null;
        }
    }

    private static Item? Row(JsonNode r)
    {
        var columns = r.Each("flexColumns").Select(c => c.At("musicResponsiveListItemFlexColumnRenderer", "text")).ToList();
        var title = columns.FirstOrDefault().Text();
        if (string.IsNullOrEmpty(title)) return null;
        var art = r.At("thumbnail").Thumb();
        var rest = columns.Skip(1).SelectMany(c => c.Runs()).ToList();
        var explicitBadge = IsExplicit(r.At("badges"));
        var nav = r.At("navigationEndpoint");
        var pageType = nav.PageType();

        var watch = r.At("overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint")
                    ?? columns.FirstOrDefault().At("runs", 0, "navigationEndpoint", "watchEndpoint");
        var videoId = r.Str("playlistItemData", "videoId") ?? watch.Str("videoId");
        if (videoId is not null && pageType is null)
        {
            var (video, episode) = VideoType(watch);
            var durationText = r.At("fixedColumns", 0, "musicResponsiveListItemFixedColumnRenderer", "text").Text()
                               ?? rest.Select(x => x.Str("text")).LastOrDefault(t => t is not null && Regex.IsMatch(t, @"^\d+(:\d\d){1,2}$"));
            return new Song(videoId, title, ArtistsFrom(rest), AlbumFrom(rest), art, J.ParseDuration(durationText), explicitBadge,
                SetVideoId: r.Str("playlistItemData", "playlistSetVideoId"), IsVideo: video, IsEpisode: episode);
        }
        var browseId = nav.Str("browseEndpoint", "browseId");
        if (browseId is null) return null;
        switch (pageType)
        {
            case "MUSIC_PAGE_TYPE_ALBUM":
                var year = rest.Select(x => x.Str("text")).LastOrDefault(t => t is not null && Regex.IsMatch(t, @"^\d{4}$"));
                return new Album(browseId, null, title, ArtistsFrom(rest), year, art, explicitBadge);
            case "MUSIC_PAGE_TYPE_ARTIST":
                return new Artist(browseId, title, art, Groups(rest).Select(g => string.Concat(g.Select(x => x.Str("text")))).LastOrDefault());
            case "MUSIC_PAGE_TYPE_USER_CHANNEL":
                return new Artist(browseId, title, art, null, IsProfile: true);
            case "MUSIC_PAGE_TYPE_PLAYLIST":
                var groups = Groups(rest).Select(g => string.Concat(g.Select(x => x.Str("text")))).Where(t => t != "Playlist").ToList();
                return new Playlist(browseId.StartsWith("VL") ? browseId[2..] : browseId, title, groups.FirstOrDefault(), groups.Skip(1).FirstOrDefault(), art);
            case "MUSIC_PAGE_TYPE_PODCAST_SHOW_PAGE":
                return new Playlist(browseId, title, null, null, art, IsPodcast: true);
            default:
                return null;
        }
    }

    private static Song? PanelSong(JsonNode r)
    {
        var videoId = r.Str("videoId");
        var title = r.At("title").Text();
        if (videoId is null || title is null) return null;
        var byline = r.At("longBylineText").Runs().ToList();
        if (byline.Count == 0) byline = r.At("shortBylineText").Runs().ToList();
        var (video, episode) = VideoType(r.At("navigationEndpoint", "watchEndpoint"));
        return new Song(videoId, title, ArtistsFrom(byline), AlbumFrom(byline), r.At("thumbnail").Thumb(),
            J.ParseDuration(r.At("lengthText").Text()), IsExplicit(r.At("badges")), IsVideo: video, IsEpisode: episode);
    }

    // ---------------------------------------------------------------- Search

    private static string? FilterParams(SearchFilter filter) => filter switch
    {
        SearchFilter.Songs => "EgWKAQIIAWoKEAkQBRAKEAMQBA==",
        SearchFilter.Albums => "EgWKAQIYAWoKEAkQChAFEAMQBA==",
        SearchFilter.Artists => "EgWKAQIgAWoKEAkQChAFEAMQBA==",
        SearchFilter.Playlists => "EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ=",
        _ => null,
    };

    /** Search results as groups: "Top result", "Songs", "Albums"... (or one group for a filter). */
    public async Task<IReadOnlyList<Section>> SearchAsync(string query, SearchFilter filter = SearchFilter.All, CancellationToken ct = default)
    {
        var body = new JsonObject { ["query"] = query };
        if (FilterParams(filter) is { } p) body["params"] = p;
        var root = await Api.PostAsync("search", body, ct);
        var contents = root.At("contents", "tabbedSearchResultsRenderer", "tabs", 0, "tabRenderer", "content", "sectionListRenderer");
        var sections = new List<Section>();
        // Newer result pages list single items with no headings: they're grouped by kind here,
        // in the order each kind first shows up.
        var loose = new List<(string group, Item item)>();
        foreach (var node in contents.Each("contents"))
        {
            if (node.At("musicCardShelfRenderer") is { } card)
            {
                var items = new List<Item>();
                var top = TopResult(card);
                if (top is not null) items.Add(top);
                // Songs listed under an artist's card don't repeat the artist's name.
                var cardArtists = top is Artist a ? new List<ArtistRef> { new(a.Name, a.BrowseId) } : null;
                items.AddRange(card.Each("contents").Select(ParseItem).OfType<Item>()
                    .Select(i => i is Song s && s.Artists.Count == 0 && cardArtists is not null ? s with { Artists = cardArtists } : i));
                if (items.Count > 0) sections.Add(new Section(card.At("header", "musicCardShelfHeaderBasicRenderer", "title").Text() ?? "Top result", null, items));
            }
            else if (node.At("itemSectionRenderer") is { } itemSection)
            {
                foreach (var item in itemSection.Each("contents").Select(ParseItem).OfType<Item>())
                {
                    loose.Add((item switch
                    {
                        Song { IsVideo: true } => "Videos",
                        Song { IsEpisode: true } => "Episodes",
                        Song => "Songs",
                        Album => "Albums",
                        Artist { IsProfile: true } => "Profiles",
                        Artist => "Artists",
                        Playlist { IsPodcast: true } => "Podcasts",
                        Playlist => "Playlists",
                        _ => "More",
                    }, item));
                }
            }
            else if (Carousel(node) is { } section)
            {
                sections.Add(section);
            }
        }
        foreach (var group in loose.GroupBy(x => x.group))
            sections.Add(new Section(group.Key, null, group.Select(x => x.item).ToList()));
        return sections;
    }

    private static Item? TopResult(JsonNode card)
    {
        var titleRun = card.At("title", "runs", 0);
        var title = titleRun.Str("text");
        var nav = titleRun.At("navigationEndpoint") ?? card.At("onTap");
        if (title is null || nav is null) return null;
        var art = card.At("thumbnail").Thumb();
        var subtitle = card.At("subtitle").Runs().ToList();
        if (nav.Str("watchEndpoint", "videoId") is { } videoId)
        {
            var (video, episode) = VideoType(nav.At("watchEndpoint"));
            var duration = subtitle.Select(x => x.Str("text")).LastOrDefault(t => t is not null && Regex.IsMatch(t, @"^\d+(:\d\d){1,2}$"));
            return new Song(videoId, title, ArtistsFrom(subtitle), AlbumFrom(subtitle), art, J.ParseDuration(duration), IsVideo: video, IsEpisode: episode);
        }
        var browseId = nav.Str("browseEndpoint", "browseId");
        if (browseId is null) return null;
        return nav.PageType() switch
        {
            "MUSIC_PAGE_TYPE_ALBUM" => new Album(browseId, null, title, ArtistsFrom(subtitle), subtitle.Select(x => x.Str("text")).LastOrDefault(t => t is not null && Regex.IsMatch(t, @"^\d{4}$")), art),
            "MUSIC_PAGE_TYPE_ARTIST" => new Artist(browseId, title, art, Groups(subtitle).Select(g => string.Concat(g.Select(x => x.Str("text")))).LastOrDefault()),
            "MUSIC_PAGE_TYPE_PLAYLIST" => new Playlist(browseId.StartsWith("VL") ? browseId[2..] : browseId, title, null, null, art),
            _ => null,
        };
    }

    public async Task<SearchSuggestions> SuggestionsAsync(string input, CancellationToken ct = default)
    {
        var root = await Api.PostAsync("music/get_search_suggestions", new JsonObject { ["input"] = input }, ct);
        var queries = new List<string>();
        var items = new List<Item>();
        foreach (var section in root.Each("contents"))
        {
            foreach (var entry in section.Each("searchSuggestionsSectionRenderer", "contents"))
            {
                if (entry.Str("searchSuggestionRenderer", "navigationEndpoint", "searchEndpoint", "query") is { } q) queries.Add(q);
                else if (ParseItem(entry) is { } item) items.Add(item);
            }
        }
        return new SearchSuggestions(queries, items);
    }

    // ---------------------------------------------------------------- Pages

    private static JsonNode? HeaderOf(JsonNode root) =>
        root.At("contents", "twoColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content", "sectionListRenderer", "contents", 0) is { } first
            ? first.At("musicResponsiveHeaderRenderer") ?? first.At("musicEditablePlaylistDetailHeaderRenderer", "header", "musicResponsiveHeaderRenderer")
            : null;

    private static JsonNode? Secondary(JsonNode root) =>
        root.At("contents", "twoColumnBrowseResultsRenderer", "secondaryContents", "sectionListRenderer");

    public async Task<AlbumPage> AlbumAsync(string browseId, CancellationToken ct = default)
    {
        var root = await Api.PostAsync("browse", new JsonObject { ["browseId"] = browseId }, ct);
        var header = HeaderOf(root);
        var title = header.At("title").Text() ?? "";
        var artists = ArtistsFrom(header.At("straplineTextOne").Runs());
        var subtitle = header.At("subtitle").Runs().Select(r => r.Str("text")).ToList();
        var year = subtitle.LastOrDefault(t => t is not null && Regex.IsMatch(t, @"^\d{4}$"));
        var art = header.At("thumbnail").Thumb();
        var canonical = root.Str("microformat", "microformatDataRenderer", "urlCanonical");
        var playlistId = canonical?.Split("list=") is { Length: 2 } parts ? parts[1] : null;
        var album = new Album(browseId, playlistId, title, artists, year, art, IsExplicit(header.At("subtitleBadge")));
        var albumRef = new AlbumRef(title, browseId);
        var secondary = Secondary(root);
        var songs = secondary.Each("contents", 0, "musicShelfRenderer", "contents").Select(ParseItem).OfType<Song>()
            // Album tracks come without art or (often) artists: they're the album's.
            .Select(s => s with { Art = s.Art ?? art, Artists = s.Artists.Count > 0 ? s.Artists : artists, Album = albumRef })
            .ToList();
        var more = secondary.Each("contents").Skip(1).Select(Carousel).OfType<Section>().ToList();
        var description = header.At("description", "musicDescriptionShelfRenderer", "description").Text();
        return new AlbumPage(album, songs, description, more);
    }

    public async Task<PlaylistPage> PlaylistAsync(string playlistId, CancellationToken ct = default)
    {
        var id = playlistId.StartsWith("VL") ? playlistId[2..] : playlistId;
        var root = await Api.PostAsync("browse", new JsonObject { ["browseId"] = "VL" + id }, ct);
        var header = HeaderOf(root);
        var editable = root.At("contents", "twoColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content", "sectionListRenderer", "contents", 0, "musicEditablePlaylistDetailHeaderRenderer") is not null;
        var title = header.At("title").Text() ?? (id == "LM" ? "Liked music" : "");
        var count = header.At("secondSubtitle").Runs().Select(r => r.Str("text")).FirstOrDefault(t => t is not null && Regex.IsMatch(t, @"\d"));
        var playlist = new Playlist(id, title, header.At("straplineTextOne").Text() ?? header.At("facepile", "avatarStackViewModel", "text", "content")?.ToString(),
            count, header.At("thumbnail").Thumb(), editable);
        var shelf = Secondary(root).At("contents", 0, "musicPlaylistShelfRenderer");
        var (songs, continuation) = PlaylistItems(shelf.Each("contents"));
        continuation ??= shelf.Str("continuations", 0, "nextContinuationData", "continuation");
        var description = header.At("description", "musicDescriptionShelfRenderer", "description").Text();
        return new PlaylistPage(playlist, songs, continuation, description);
    }

    private static (List<Song> songs, string? continuation) PlaylistItems(IEnumerable<JsonNode> contents)
    {
        var songs = new List<Song>();
        string? continuation = null;
        foreach (var node in contents)
        {
            if (node.Str("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token") is { } token) continuation = token;
            else if (ParseItem(node) is Song song) songs.Add(song);
        }
        return (songs, continuation);
    }

    /** More songs of a long playlist. */
    public async Task<(IReadOnlyList<Song> songs, string? continuation)> PlaylistMoreAsync(string continuation, CancellationToken ct = default)
    {
        var root = await Api.PostAsync("browse", new JsonObject { ["continuation"] = continuation }, ct);
        var items = root.Each("onResponseReceivedActions", 0, "appendContinuationItemsAction", "continuationItems").ToList();
        if (items.Count == 0) items = root.Each("continuationContents", "musicPlaylistShelfContinuation", "contents").ToList();
        var (songs, next) = PlaylistItems(items);
        next ??= root.Str("continuationContents", "musicPlaylistShelfContinuation", "continuations", 0, "nextContinuationData", "continuation");
        return (songs, next);
    }

    public async Task<ArtistPage> ArtistAsync(string browseId, CancellationToken ct = default)
    {
        var root = await Api.PostAsync("browse", new JsonObject { ["browseId"] = browseId }, ct);
        var header = root.At("header", "musicImmersiveHeaderRenderer") ?? root.At("header", "musicVisualHeaderRenderer") ?? root.At("header", "musicHeaderRenderer");
        var name = header.At("title").Text() ?? "";
        var art = header.At("thumbnail").Thumb() ?? header.At("foregroundThumbnail").Thumb();
        var subscribe = header.At("subscriptionButton", "subscribeButtonRenderer");
        var sections = new List<Section>();
        string? about = header.At("description").Text();
        foreach (var node in root.Each("contents", "singleColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content", "sectionListRenderer", "contents"))
        {
            if (node.At("musicDescriptionShelfRenderer") is { } d) about ??= d.At("description").Text();
            else if (Carousel(node) is { } section) sections.Add(section);
        }
        var artist = new Artist(browseId, name, art, subscribe.At("longSubscriberCountText").Text());
        return new ArtistPage(artist, about, sections,
            subscribe.Str("channelId") ?? browseId,
            subscribe.Bool("subscribed"),
            subscribe.At("longSubscriberCountText").Text() ?? subscribe.At("subscriberCountText").Text(),
            header.Str("playButton", "buttonRenderer", "navigationEndpoint", "watchEndpoint", "playlistId"),
            header.Str("startRadioButton", "buttonRenderer", "navigationEndpoint", "watchEndpoint", "playlistId")
                ?? header.Str("startRadioButton", "buttonRenderer", "navigationEndpoint", "watchPlaylistEndpoint", "playlistId"));
    }

    /** "See all" of an artist's albums, singles, videos... */
    public async Task<IReadOnlyList<Item>> BrowseItemsAsync(string browseId, string? @params, CancellationToken ct = default)
    {
        var body = new JsonObject { ["browseId"] = browseId };
        if (@params is not null) body["params"] = @params;
        var root = await Api.PostAsync("browse", body, ct);
        var list = root.At("contents", "singleColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content", "sectionListRenderer", "contents", 0);
        return Carousel(list ?? new JsonObject())?.Items ?? [];
    }

    // ---------------------------------------------------------------- Playback

    /**
     * The queue that plays after a song: [playlistId] for a playlist/album/radio, or the song's
     * own radio. Also says where the song's lyrics and related music are.
     */
    public async Task<NextPage> NextAsync(string? videoId, string? playlistId = null, string? continuation = null, string? @params = null, CancellationToken ct = default)
    {
        var body = new JsonObject { ["isAudioOnly"] = true, ["enablePersistentPlaylistPanel"] = true, ["tunerSettingValue"] = "AUTOMIX_SETTING_NORMAL" };
        if (videoId is not null) body["videoId"] = videoId;
        if (playlistId is not null) body["playlistId"] = playlistId;
        if (@params is not null) body["params"] = @params;
        if (continuation is not null) body["continuation"] = continuation;
        var root = await Api.PostAsync("next", body, ct);
        if (continuation is not null)
        {
            var more = root.At("continuationContents", "playlistPanelContinuation");
            return new NextPage(more.Each("contents").Select(ParseItem).OfType<Song>().ToList(),
                more.Str("continuations", 0, "nextRadioContinuationData", "continuation") ?? more.Str("continuations", 0, "nextContinuationData", "continuation"),
                null, null, 0);
        }
        var tabs = root.At("contents", "singleColumnMusicWatchNextResultsRenderer", "tabbedRenderer", "watchNextTabbedResultsRenderer", "tabs");
        var panel = tabs.At(0, "tabRenderer", "content", "musicQueueRenderer", "content", "playlistPanelRenderer");
        var songs = panel.Each("contents").Select(ParseItem).OfType<Song>().ToList();
        var index = Math.Max(0, songs.FindIndex(s => s.VideoId == videoId));
        return new NextPage(songs,
            panel.Str("continuations", 0, "nextRadioContinuationData", "continuation") ?? panel.Str("continuations", 0, "nextContinuationData", "continuation"),
            tabs.Str(1, "tabRenderer", "endpoint", "browseEndpoint", "browseId"),
            tabs.Str(2, "tabRenderer", "endpoint", "browseEndpoint", "browseId"),
            index);
    }

    /** YouTube Music's plain lyrics, from the browse id [NextAsync] gave. */
    public async Task<string?> LyricsAsync(string browseId, CancellationToken ct = default)
    {
        var root = await Api.PostAsync("browse", new JsonObject { ["browseId"] = browseId }, ct);
        return root.At("contents", "sectionListRenderer", "contents", 0, "musicDescriptionShelfRenderer", "description").Text();
    }

    /** How loud the song is mastered (for even volume) and where to report that it played. */
    public async Task<(double? loudnessDb, string? trackingUrl, long? durationMs)> PlayerInfoAsync(string videoId, CancellationToken ct = default)
    {
        var root = await Api.PostAsync("player", new JsonObject { ["videoId"] = videoId }, ct);
        var seconds = root.Num("videoDetails", "lengthSeconds");
        return (root.Num("playerConfig", "audioConfig", "loudnessDb"),
            root.Str("playbackTracking", "videostatsPlaybackUrl", "baseUrl"),
            seconds is null ? null : (long)(seconds.Value * 1000));
    }

    /** Counts a play on your account (History, "Recently played", recommendations). */
    public async Task ReportPlayAsync(string trackingUrl, string? playlistId = null, CancellationToken ct = default)
    {
        const string alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";
        var cpn = new string(Enumerable.Range(0, 16).Select(_ => alphabet[Random.Shared.Next(64)]).ToArray());
        var url = $"{trackingUrl}&ver=2&c=WEB_REMIX&cpn={cpn}" + (playlistId is null ? "" : "&list=" + Uri.EscapeDataString(playlistId));
        using var _ = await Api.GetAsync(url, ct);
    }

    // ---------------------------------------------------------------- Your library

    private async Task<List<Item>> GridAllAsync(string browseId, CancellationToken ct)
    {
        var root = await Api.PostAsync("browse", new JsonObject { ["browseId"] = browseId }, ct);
        var section = root.At("contents", "singleColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content", "sectionListRenderer", "contents", 0);
        var grid = section.At("gridRenderer");
        var shelf = section.At("musicShelfRenderer");
        var items = (grid is not null ? grid.Each("items") : shelf.Each("contents")).Select(ParseItem).OfType<Item>().ToList();
        var next = (grid ?? shelf).Str("continuations", 0, "nextContinuationData", "continuation");
        for (var page = 0; next is not null && page < 30; page++)
        {
            var more = await Api.PostAsync("browse", new JsonObject(), ct, $"ctoken={Uri.EscapeDataString(next)}&continuation={Uri.EscapeDataString(next)}&type=next");
            var g = more.At("continuationContents", "gridContinuation");
            var s = more.At("continuationContents", "musicShelfContinuation");
            items.AddRange((g is not null ? g.Each("items") : s.Each("contents")).Select(ParseItem).OfType<Item>());
            next = (g ?? s).Str("continuations", 0, "nextContinuationData", "continuation");
        }
        return items;
    }

    public async Task<IReadOnlyList<Playlist>> LibraryPlaylistsAsync(CancellationToken ct = default) =>
        (await GridAllAsync("FEmusic_liked_playlists", ct)).OfType<Playlist>().Where(p => !p.IsPodcast).DistinctBy(p => p.Id).ToList();

    public async Task<IReadOnlyList<Album>> LibraryAlbumsAsync(CancellationToken ct = default) =>
        (await GridAllAsync("FEmusic_liked_albums", ct)).OfType<Album>().DistinctBy(a => a.BrowseId).ToList();

    public async Task<IReadOnlyList<Artist>> LibraryArtistsAsync(CancellationToken ct = default) =>
        (await GridAllAsync("FEmusic_library_corpus_artists", ct)).OfType<Artist>().Where(a => !a.IsProfile).DistinctBy(a => a.BrowseId).ToList();

    /** Your listening history, as "Today", "Yesterday"... sections. */
    public async Task<IReadOnlyList<Section>> HistoryAsync(CancellationToken ct = default)
    {
        var root = await Api.PostAsync("browse", new JsonObject { ["browseId"] = "FEmusic_history" }, ct);
        return root.Each("contents", "singleColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content", "sectionListRenderer", "contents")
            .Select(Carousel).OfType<Section>().ToList();
    }

    public async Task<Account?> AccountAsync(CancellationToken ct = default)
    {
        var root = await Api.PostAsync("account/account_menu", new JsonObject(), ct);
        var header = root.At("actions", 0, "openPopupAction", "popup", "multiPageMenuRenderer", "header", "activeAccountHeaderRenderer");
        var name = header.At("accountName").Text();
        return name is null ? null : new Account(name, header.At("email").Text(), header.At("accountPhoto").Thumb(), header.At("channelHandle").Text());
    }

    /** Your channels, for picking one after sign-in. */
    public async Task<IReadOnlyList<Channel>> ChannelsAsync(CancellationToken ct = default)
    {
        var root = await Api.PostAsync("account/accounts_list", new JsonObject { ["requestType"] = "ACCOUNTS_LIST_REQUEST_TYPE_CHANNEL_SWITCHER" }, ct);
        var channels = new List<Channel>();
        foreach (var section in root.Each("contents", 0, "accountSectionListRenderer", "contents"))
        {
            foreach (var entry in section.Each("accountItemSectionRenderer", "contents"))
            {
                var item = entry.At("accountItem");
                if (item is null) continue;
                var tokens = item.Each("serviceEndpoint", "selectActiveIdentityEndpoint", "supportedTokens").ToList();
                var dataSync = tokens.Select(t => t.Str("datasyncIdToken", "datasyncIdToken")).FirstOrDefault(t => t is not null);
                var signIn = tokens.Select(t => t.Str("accountSigninToken", "signinUrl")).FirstOrDefault(t => t is not null);
                channels.Add(new Channel(item.At("accountName").Text() ?? "", item.At("channelHandle").Text(), item.At("accountPhoto").Thumb(),
                    item.Bool("isSelected"), signIn is null ? null : (signIn.StartsWith("http") ? signIn : "https://www.youtube.com" + signIn), dataSync));
            }
        }
        return channels;
    }

    public Task LikeAsync(string videoId, bool like, CancellationToken ct = default) =>
        Api.PostAsync(like ? "like/like" : "like/removelike", new JsonObject { ["target"] = new JsonObject { ["videoId"] = videoId } }, ct);

    public Task SubscribeAsync(string channelId, bool subscribe, CancellationToken ct = default) =>
        Api.PostAsync(subscribe ? "subscription/subscribe" : "subscription/unsubscribe", new JsonObject { ["channelIds"] = new JsonArray(channelId) }, ct);

    public async Task<string?> CreatePlaylistAsync(string title, CancellationToken ct = default)
    {
        var root = await Api.PostAsync("playlist/create", new JsonObject { ["title"] = title, ["privacyStatus"] = "PRIVATE" }, ct);
        return root.Str("playlistId");
    }

    private Task EditPlaylistAsync(string playlistId, JsonObject action, CancellationToken ct) =>
        Api.PostAsync("browse/edit_playlist", new JsonObject { ["playlistId"] = playlistId, ["actions"] = new JsonArray(action) }, ct);

    public Task AddToPlaylistAsync(string playlistId, string videoId, CancellationToken ct = default) =>
        EditPlaylistAsync(playlistId, new JsonObject { ["action"] = "ACTION_ADD_VIDEO", ["addedVideoId"] = videoId, ["dedupeOption"] = "DEDUPE_OPTION_SKIP" }, ct);

    public Task RemoveFromPlaylistAsync(string playlistId, string videoId, string setVideoId, CancellationToken ct = default) =>
        EditPlaylistAsync(playlistId, new JsonObject { ["action"] = "ACTION_REMOVE_VIDEO", ["removedVideoId"] = videoId, ["setVideoId"] = setVideoId }, ct);

    public Task RenamePlaylistAsync(string playlistId, string name, CancellationToken ct = default) =>
        EditPlaylistAsync(playlistId, new JsonObject { ["action"] = "ACTION_SET_PLAYLIST_NAME", ["playlistName"] = name }, ct);

    public Task DeletePlaylistAsync(string playlistId, CancellationToken ct = default) =>
        Api.PostAsync("playlist/delete", new JsonObject { ["playlistId"] = playlistId }, ct);
}
