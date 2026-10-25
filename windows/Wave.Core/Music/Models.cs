namespace Wave.Music;

/** Someone credited on a song or album; [Id] is their channel (null for plain text credits). */
public sealed record ArtistRef(string Name, string? Id);

public sealed record AlbumRef(string Name, string? Id);

/** Anything YouTube Music shows as a card or a row. */
public abstract record Item
{
    /** Unique per kind: a video id, or a browse id. */
    public abstract string Key { get; }
    public abstract string Title { get; }
    public abstract string? Thumbnail { get; }
    /** The grey line under the title. */
    public abstract string Subtitle { get; }
}

public sealed record Song(
    string VideoId,
    string Name,
    IReadOnlyList<ArtistRef> Artists,
    AlbumRef? Album,
    string? Art,
    long? DurationMs,
    bool Explicit = false,
    /** A playlist's own id for this entry (needed to remove it from the playlist). */
    string? SetVideoId = null,
    /** A music video or episode rather than a song. */
    bool IsVideo = false,
    bool IsEpisode = false) : Item
{
    public override string Key => VideoId;
    public override string Title => Name;
    public override string? Thumbnail => Art;
    public string ArtistNames => string.Join(", ", Artists.Select(a => a.Name));
    public override string Subtitle => ArtistNames;
}

public sealed record Album(
    string BrowseId,
    string? PlaylistId,
    string Name,
    IReadOnlyList<ArtistRef> Artists,
    string? Year,
    string? Art,
    bool Explicit = false) : Item
{
    public override string Key => BrowseId;
    public override string Title => Name;
    public override string? Thumbnail => Art;
    public override string Subtitle => string.Join(" • ", new[] { string.Join(", ", Artists.Select(a => a.Name)), Year ?? "" }.Where(s => s.Length > 0));
}

public sealed record Artist(string BrowseId, string Name, string? Art, string? Info = null, bool IsProfile = false) : Item
{
    public override string Key => BrowseId;
    public override string Title => Name;
    public override string? Thumbnail => Art;
    public override string Subtitle => Info ?? "Artist";
}

public sealed record Playlist(
    /** Without the "VL" browse prefix. */
    string Id,
    string Name,
    string? Author,
    string? SongCount,
    string? Art,
    bool Editable = false,
    bool IsPodcast = false) : Item
{
    public override string Key => Id;
    public override string Title => Name;
    public override string? Thumbnail => Art;
    public override string Subtitle => string.Join(" • ", new[] { Author ?? "", SongCount ?? "" }.Where(s => s.Length > 0));
    public string BrowseId => "VL" + Id;
}

/** A row of home, an artist page or search. [MoreBrowseId] opens the full list. */
public sealed record Section(string Title, string? Label, IReadOnlyList<Item> Items, string? MoreBrowseId = null, string? MoreParams = null);

public sealed record HomeResult(IReadOnlyList<Section> Sections, string? Continuation);

public sealed record AlbumResult(Album Album, IReadOnlyList<Song> Songs, string? Description, IReadOnlyList<Section> More);

public sealed record PlaylistResult(Playlist Playlist, IReadOnlyList<Song> Songs, string? Continuation, string? Description);

public sealed record ArtistResult(
    Artist Artist,
    string? Description,
    IReadOnlyList<Section> Sections,
    string? ChannelId,
    bool Subscribed,
    string? Subscribers,
    /** Shuffle and radio for the artist. */
    string? ShufflePlaylistId,
    string? RadioPlaylistId);

/** A queue from "next": what plays after a song, plus where its lyrics and related music live. */
public sealed record NextResult(
    IReadOnlyList<Song> Songs,
    string? Continuation,
    string? LyricsBrowseId,
    string? RelatedBrowseId,
    int CurrentIndex);

public sealed record SearchSuggestions(IReadOnlyList<string> Queries, IReadOnlyList<Item> Items);

public sealed record Account(string Name, string? Email, string? Photo, string? Handle = null);

/** One of your channels (Google accounts can have a few). */
public sealed record Channel(string Name, string? Handle, string? Photo, bool Selected, string? SignInUrl, string? DataSyncId);

public enum SearchFilter { All, Songs, Artists, Albums, Playlists }
