<h1 align="center">Moonfin for Android TV</h1>
<h3 align="center">Enhanced Jellyfin client for Android TV, Nvidia Shield, and Amazon Fire TV devices</h3>

---

<p align="center">
   <img width="4305" height="2659" alt="splash-background" src="https://github.com/user-attachments/assets/8618363e-d982-4828-8274-a2c3c7623ddb" />
</p>

[![License](https://img.shields.io/github/license/Moonfin-Client/AndroidTV-FireTV.svg)](https://github.com/Moonfin-Client/AndroidTV-FireTV)
[![Release](https://img.shields.io/github/release/Moonfin-Client/AndroidTV-FireTV.svg)](https://github.com/Moonfin-Client/AndroidTV-FireTV/releases)

<a href="https://www.buymeacoffee.com/moonfin" target="_blank"><img src="https://github.com/user-attachments/assets/fe26eaec-147f-496f-8e95-4ebe19f57131" alt="Buy Me A Coffee" ></a>

> **[← Back to main Moonfin project](https://github.com/Moonfin-Client)**

Moonfin for Android TV is an enhanced fork of the official Jellyfin Android TV client, optimized for the viewing experience on Android TV, Nvidia Shield, and Amazon Fire TV devices.

## Features & Enhancements

Moonfin for Android TV builds on the solid foundation of Jellyfin with targeted improvements for TV viewing:

### Cross-Server Content Playback
- **Unified Library Support** - Seamless playback from multiple Jellyfin servers
- Seamless switching between servers for content playback
- Improved server selection logic

### SyncPlay (Beta)
- **Synchronized Group Playback** - Watch together with friends and family in perfect sync
- Dynamic playback speed adjustments based on drift calculations
- Buffering and ready state reporting for better synchronization
- User notifications for group join/leave events

### Playlist System
- **Full Playlist Support** - Create, manage, and share playlists
- Add to Playlist button on detail screens with modal selection
- Create new playlists or add to existing ones
- Public playlist support for sharing with other users
- Remove from Playlist on long press
- Replaced the previous local-only Watchlist feature

### Jellyseerr & Seerr Integration

Moonfin is the first Android TV client with native Jellyseerr and Seerr support.

- Browse trending, popular, and recommended movies/shows and filter content by Series/Movie Genres, Studio, Network, and keywords
- Request content in HD or 4K directly from your TV  
- **Moonfin Proxy Mode** — route all Jellyseerr/Seerr requests through the Moonfin server plugin (no direct connection needed)  
- **NSFW Content Filtering** (optional) using Jellyseerr/TMDB metadata  
- Smart season selection when requesting TV shows  
- View all your pending, approved, and available requests with distinct status icons  
- Jellyseerr badges on search results for quick discovery status  
- Per-user Jellyseerr settings — each user on the device can have their own configuration  
- CSRF token handling for secure state-changing requests  
- Global search includes Jellyseerr results  
- Rich backdrop images for a more cinematic discovery experience  

> **Deprecation Notice:** In future versions, the legacy authentication methods (Jellyfin auth and local account login from within the app) will be removed. All Jellyseerr/Seerr connections will be managed exclusively through the Moonfin server plugin.

### Plugin Sync
- **Bidirectional Settings Sync** — sync preferences between the app and the Moonfin server plugin
- Three-way merge strategy ensures no settings are lost during sync
- Settings push automatically on change with debounced uploads
- Consolidated **Plugin Settings** screen for all synced preferences

### MDBList Ratings Integration
- **Multiple Rating Sources** — display ratings from various platforms:
  - AniList, IMDB, Letterboxd, Metacritic, Metacritic User
  - MyAnimeList, Roger Ebert, Rotten Tomatoes, RT Audience, TMDB, Trakt
- **Server-hosted rating icons** — icons served from the Moonfin plugin (no bundled assets needed)
- No client-side API keys required — all requests routed through the server plugin
- TMDB episode ratings with series community rating fallback
- Episode ratings displayed in library views

### 🛠️ Customizable Toolbar
- **Toggle buttons** - Show/hide Shuffle, Genres, and Favorites buttons
- **Library row toggle** - Show/hide the entire library button row for a cleaner home screen
- **Shuffle filter** - Choose Movies only, TV Shows only, or Both
- **Pill-shaped design** - Subtle rounded background with better contrast
- Dynamic library buttons that scroll horizontally for 5+ libraries

### 🎬 Featured Media Bar
- Rotating showcase of 15 random movies and TV shows right on your home screen
- **Profile-aware refresh** - Automatically refreshes content when switching profiles to prevent inappropriate content from appearing on child profiles
- See ratings, genres, runtime, and a quick overview without extra clicks
- Smooth crossfade transitions as items change, with matching backdrop images
- Height and positioning tuned for viewing from the couch

### 🧭 Enhanced Navigation
- **Left Sidebar Navigation** - New sidebar with expandable icons/text and configurable navbar position
- **Folder View** - Browse media in folder structure for organized access
- Quick access home button (house icon) and search (magnifying glass)
- Shuffle button for instant random movie/TV show discovery with genre-specific shuffle on long press
- Genres redesigned as sortable tiles with random backdrop images
- Dynamic library buttons automatically populate based on your Jellyfin libraries
- One-click navigation to any library or collection directly from the toolbar
- Cleaner icon-based design for frequently used actions

### In-App Trailer Previews
- **Trailer playback** directly inside the app via Invidious (privacy-friendly YouTube frontend)
- **SponsorBlock integration** — automatically skips intros and sponsor segments in trailers
- **Episode preview overlays** on card focus in home rows
- **Series trailer overlays** for YouTube-hosted trailers on the Featured Media Bar
- **DASH quality support** for trailer playback
- **Preview Audio toggle** — control whether previews play muted or with sound

### Redesigned Libraries (Compose)
- Libraries rebuilt in Jetpack Compose with a modern, fluid grid UI
- Adaptive card sizing with filter/sort dialogs and infinite scroll
- Dedicated views for Movies, Series, Music, Live TV, Recordings, and Schedules
- Genre grid browser with search and filtering

### Redesigned Details Screen (Compose)
- Full-featured Compose details view for Movies, Series, Episodes, Music Albums, Playlists, and Collections
- Action buttons for play, trailer, favorite, watched, shuffle, and more
- Playlist item reordering and track action dialogs for music

### 🎵 Playback & Media Control
- **ASS/SSA Subtitle Support** - Direct-play and rendering support for ASS/SSA subtitle formats with customizable font scaling
- **Subtitle Delay & Positioning** - Fine-tune subtitle sync and adjust position/size for wide aspect ratio videos
- **Max Video Resolution** - New preference to limit video resolution
- **Unpause Rewind** - Automatically rewinds a configurable amount when unpausing playback
- **Theme Music Playback** - Background theme music support for TV shows and movies with volume control, plays on details screens
- **Pre-Playback Track Selection** - Choose your preferred audio track and subtitle before playback starts (configurable in settings)
- **Next Episode Countdown** - Skip button shows countdown timer when next episode is available
- **Subtitles Default to None** - Option to default subtitle selection to none instead of auto-selecting
- **Trickplay Scrub** - Auto-confirm seeking with improved caching
- **Automatic Screensaver Dimming** - Reduces brightness after 90 seconds of playback inactivity to prevent screen burn-in with dynamic logo/clock movement
- **Exit Confirmation Dialog** - Optional confirmation prompt when exiting the app (configurable in settings)
- **OTA Update System** - Automatic check for new Moonfin versions with in-app update notifications

### Centralized Shuffle System
- Hybrid approach: server-side random sort first, client-side fallback if needed
- Configurable shuffle content type (Movies, TV Shows, or Both)
- Genre-specific shuffle on long press

### UI Polish
- **Focus Color** - Replaced the old App Theme system with a customizable accent color for focus highlights, stored per-user
- **Card Focus Expansion** - Cards expand on focus with a configurable toggle
- **Home Rows Image Size Preference** - Choose your preferred poster size
- **Adjustable Backdrop Blur** - Customizable background blur amount with slider control
- **Media Bar Opacity Control** - Slider-based opacity adjustment for the featured media bar overlay
- **Show/Hide Rating Labels** - Toggle rating source labels on or off
- **Clock Display** - Optional clock in toolbar and sidebar
- Compose-based dialogs for Exit, Create Playlist, Add to Playlist, Shuffle, Release Notes, and Donate
- Buttons look better when not focused (transparent instead of distracting)
- Better contrast makes text easier to read
- Transitions and animations feel responsive
- Consistent icons and visual elements throughout

## Screenshots
<img width="1920" height="1080" alt="Screenshot_20260219_115555" src="https://github.com/user-attachments/assets/11d61a01-b9be-4a30-9c28-ea355f962dd2" />
<img width="1920" height="1080" alt="Screenshot_20260219_115456" src="https://github.com/user-attachments/assets/481665ea-0e22-42e4-9424-058b21f968c1" />

<img width="1920" height="1080" alt="Screenshot_20260219_120753" src="https://github.com/user-attachments/assets/2beceee0-2c15-4f7d-927b-5de079aa53b8" />
<img width="1920" height="1080" alt="Screenshot_20260219_115429" src="https://github.com/user-attachments/assets/bff08ce4-a31e-4859-87aa-6246d9494460" />

<img width="1920" height="1080" alt="Screenshot_20260219_121254" src="https://github.com/user-attachments/assets/96a5b744-2b25-426a-bb6e-44374240fe24" />
<img width="1920" height="1080" alt="Screenshot_20260219_121317" src="https://github.com/user-attachments/assets/5321e05b-96d8-453f-ab05-766e78f871e4" />

<img width="1920" height="1080" alt="image" src="https://github.com/user-attachments/assets/0da9d98e-cd02-45fb-a86d-f177fcaa58f2" />
<img width="1920" height="1080" alt="Screenshot_20260219_121032" src="https://github.com/user-attachments/assets/d94d0fdc-f9f2-4a31-9a62-162604fa9682" />

## Videos

https://github.com/user-attachments/assets/c52e96c9-30e9-4478-8940-64c2418aeea0

https://github.com/user-attachments/assets/113fa9d8-039c-4f14-8c95-785ca6d47c9b

---

**Disclaimer:** Screenshots shown in this documentation feature media content, artwork, and actor likenesses for demonstration purposes only. None of the media, studios, actors, or other content depicted are affiliated with, sponsored by, or endorsing the Moonfin client or the Jellyfin project. All rights to the portrayed content belong to their respective copyright holders. These screenshots are used solely to demonstrate the functionality and interface of the application.

---

## Installation

### Pre-built Releases
Download the latest APK from the [Releases page](https://github.com/Moonfin-Client/AndroidTV-FireTV/releases).

**Supported Devices:**
- Android TV devices (Android 6.0+)
- Nvidia Shield TV
- Amazon Fire TV / Fire TV Stick
- Google TV (Chromecast with Google TV)

### Jellyseerr / Seerr Setup (Optional)
To enable media discovery and requesting:

1. Install the **Moonfin server plugin** on your Jellyfin server and configure Jellyseerr/Seerr in the plugin settings
2. In Moonfin, go to **Settings → Plugin** and enable **Plugin Sync**
3. Jellyseerr/Seerr will be configured automatically via the server plugin proxy

**Legacy (Direct Connection) — will be removed in next update:**
1. Install and configure Jellyseerr on your network ([jellyseerr.dev](https://jellyseerr.dev))
2. In Moonfin, go to **Settings → Plugin → Jellyseerr**
3. Enter your Jellyseerr server URL (e.g., `http://192.168.1.100:5055`)
4. Click **Connect with Jellyfin** and enter your Jellyfin password

Your session is saved securely and will reconnect automatically.

### Sideloading Instructions
1. Enable "Unknown Sources" or "Install Unknown Apps" in your device settings
2. Transfer the APK to your device or download it directly
3. Use a file manager app to install the APK

## Building from Source

### Prerequisites
- Android Studio Arctic Fox or newer
- JDK 11 or newer
- Android SDK with API 23+ installed

### Steps

1. **Clone the repository:**
```bash
git clone https://github.com/Moonfin-Client/AndroidTV-FireTV.git
cd AndroidTV-FireTV
```

2. **Build debug version:**
```bash
./gradlew assembleDebug
```

3. **Install to connected device:**
```bash
./gradlew installDebug
```

4. **Build release version:**

First, create a `keystore.properties` file in the root directory (use `keystore.properties.template` as a guide):
```properties
storeFile=/path/to/your/keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

Then build:
```bash
./gradlew assembleRelease
```

The APK will be in `app/build/outputs/apk/release/`

## Development

### Developer Notes
- Uses Gradle wrapper (no need to install Gradle separately)
- Android Studio is recommended for development
- Keep Android SDK and build tools updated
- Code style follows upstream Jellyfin conventions
- UI changes should be tested on actual TV devices when possible

## Contributing

We welcome contributions to Moonfin for Android TV!

### Guidelines
1. **Check existing issues** - See if your idea/bug is already reported
2. **Discuss major changes** - Open an issue first for significant features
3. **Follow code style** - Match the existing codebase conventions
4. **Test on TV devices** - Verify changes work on actual Android TV hardware
5. **Consider upstream** - Features that benefit all users should go to Jellyfin first!

### Pull Request Process
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes with clear commit messages
4. Test thoroughly on Android TV devices
5. Submit a pull request with a detailed description

## Translating

Translations are maintained through the Jellyfin Weblate instance:
- [Jellyfin Android TV on Weblate](https://translate.jellyfin.org/projects/jellyfin-android/jellyfin-androidtv)

Translations contributed to Moonfin that are universally applicable will be submitted upstream to benefit the entire community.

## Support & Community

- **Issues** - [GitHub Issues](https://github.com/Moonfin-Client/AndroidTV-FireTV/issues) for bugs and feature requests
- **Discussions** - [GitHub Discussions](https://github.com/Moonfin-Client/AndroidTV-FireTV/discussions) for questions and ideas
- **Upstream Jellyfin** - [jellyfin.org](https://jellyfin.org) for server-related questions

## Credits

Moonfin for Android TV is built upon the excellent work of:

- **[Jellyfin Project](https://jellyfin.org)** - The foundation and upstream codebase
- **[MakD](https://github.com/MakD)** - Original Jellyfin-Media-Bar concept that inspired our featured media bar
- **Jellyfin Android TV Contributors** - All the developers who built the original client
- **Moonfin Contributors** - Everyone who has contributed to this fork

## License

This project inherits the GPL v2 license from the upstream Jellyfin Android TV project. See the [LICENSE](LICENSE) file for details.

---

<p align="center">
   <strong>Moonfin for Android TV</strong> is an independent fork and is not affiliated with the Jellyfin project.<br>
   <a href="https://github.com/Moonfin-Client">← Back to main Moonfin project</a>
</p>
