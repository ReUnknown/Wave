package app.wave.music.ui.settings

/** What's new in each version of Wave, newest first (Settings › About › What's new). */
object Changelog {
    class Version(val version: String, val title: String, val notes: List<String>)

    val versions = listOf(
        Version("2.0.0-alpha.14", "Lighter and tablet-ready", listOf(
            "Uses less battery: nothing redraws between lyric lines, motion is held to 60 fps, and the player checks in less while paused",
            "No more listening to the beat in the background (nothing used it any more)",
            "Tablets: a centered dock, Prisma's header beside the songs on album and artist pages, library tiles in one row",
            "The old computer layouts are gone, for a smaller, simpler app",
        )),
        Version("2.0.0-alpha.13", "Smoother all over", listOf(
            "A new update screen: a peek in About, then the full list of changes and one button to download and install",
            "Back swipes follow your finger, and pages slide and settle more naturally",
            "Home and Library cross over, the mini player grows into the dock, Up next rows glide",
            "Lyrics no longer jump when a song changes or after a seek",
            "Tablets get the whole new interface",
            "A shorter, real Attribution list, and some old code cleared out",
        )),
        Version("2.0.0-alpha.12", "Settings, rebuilt", listOf(
            "Settings in groups: Customize, Appearance, Playback & sound, Storage, Battery and About",
            "Storage: see what saved songs and covers take, set how much room they get, clear them",
            "Battery: a simpler look and lighter audio while battery saver is on, and Calm motion",
            "About: Wave's icon, version and GitHub, What's new, Attribution and Report a bug",
            "Choose the tab Wave opens on; audio quality is Auto to begin with",
            "Legacy uses the new settings; tablets get Prisma's pages in a tablet-sized layout",
            "No more hard line at the top of search",
        )),
        Version("2.0.0-alpha.11", "Make it yours", listOf(
            "Light mode, and a Theme option: System, Light or Dark",
            "Background, Blur, Corners, Text size, Denser home and Haptics options",
            "Skip 10 seconds instead of songs, and an option for the mini player's buttons",
            "The top search bar stays pinned; Legacy mixes 1.0's home with Prisma's",
        )),
        Version("2.0.0-alpha.10", "Setup and Customize", listOf(
            "A first-run setup to pick your look and layout",
            "Modern or Legacy, bottom bar or top search, mini player on its own",
            "Erase info, the cover blurred behind everything, and Prisma on tablets",
        )),
        Version("2.0.0-alpha.9", "Depth", listOf(
            "Album and artist photos stay behind the page as you scroll",
            "Sharper player art, frosted strips, calmer lyrics",
        )),
        Version("2.0.0-alpha.8", "Frosted menus", listOf(
            "Menus blur what's behind them; Up next is one list with the playing song lit up",
            "A song you pick shows in the mini player right away while it loads",
        )),
        Version("2.0.0-alpha.7", "The ⋮ is the menu", listOf(
            "Menus grow out of the ⋮ you press",
            "A cleaner player, a new Up next, lyrics that ripple into place",
        )),
        Version("2.0.0-alpha.6", "Rounder", listOf("A round follow button, more art in the player")),
        Version("2.0.0-alpha.5", "Settings pages", listOf("Settings on their own pages, menus at your finger, full-art albums")),
        Version("2.0.0-alpha.4", "One dock", listOf("The mini player and tabs in one frosted dock, new page tops")),
        Version("2.0.0-alpha.3", "A new search", listOf("A clean search, a blurred bottom bar, AMOLED black")),
        Version("2.0.0-alpha.2", "Denser home", listOf("Blurred pick captions, Google Sans, your phone's colors")),
        Version("2.0.0-alpha.1", "Wave Prisma", listOf("The first look at Wave 2.0: a bottom bar, and a new home")),
        Version("1.2.1", "Pointing to WaveMini", listOf("The last update for computers, pointing to WaveMini")),
        Version("1.2.0", "Lyrics that move", listOf("Word-by-word lyrics like WaveMini's, a cleaner settings screen")),
        Version("1.1.1", "Docking", listOf("Drag the player bar to a half to dock it on computers, and WaveMini")),
        Version("1.1.0", "Wave everywhere", listOf("Wave for Windows, macOS and Linux, and Wave without an account")),
        Version("1.0.0", "Wave 1.0", listOf(
            "Share, open links, follow artists, light mode",
            "Truer song colors, instant menus, Wave notices",
            "Player modes, volume and speed, history",
            "A big-screen layout with a player bar, and Googlebook support",
        )),
        Version("0.9.9", "Getting close", listOf("Instant back, full songs, autoplay, and a motion polish pass")),
        Version("0.8.9", "The first Wave", listOf("A personal YouTube Music client for Pixel")),
    )
}
