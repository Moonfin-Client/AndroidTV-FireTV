# Moonfin for Android TV - Developer Guide

## Project Overview

**Moonfin** is an enhanced fork of the official Jellyfin Android TV client, optimized for Android TV, Nvidia Shield, and Fire TV devices. The key differentiator is **Jellyseerr integration** - the first native Jellyfin client with TMDB-based content discovery and requesting.

## Architecture

### Multi-Module Gradle Structure

```
app/                           # Main Android TV application
playback/
  ├── core/                    # Core playback abstractions
  ├── jellyfin/                # Jellyfin-specific playback integration
  └── media3/
      ├── exoplayer/           # ExoPlayer implementation
      └── session/             # Media3 session management
preference/                    # Shared preferences module
buildSrc/                      # Custom Gradle build logic (VersionUtils)
```

**Key architectural decisions:**
- Custom `playback` module hierarchy abstracts media playback from Jellyfin SDK specifics
- Gradle version catalog (`gradle/libs.versions.toml`) centralizes all dependency versions
- Uses typesafe project accessors (`projects.playback.core`)

### Dependency Injection (Koin)

All DI is managed via **Koin** with modules organized by domain:

```kotlin
// app/src/main/java/org/jellyfin/androidtv/di/
androidModule     // System services (AudioManager, UiModeManager, WorkManager)
appModule         // SDK, repositories, ViewModels, ImageLoader (Coil)
authModule        // Authentication & session management
playbackModule    // PlaybackManager, MediaManager, ExoPlayer setup
preferenceModule  // UserPreferences, JellyseerrPreferences
utilsModule       // Helpers, utilities
```

**Injection patterns:**
- Constructor injection in ViewModels: `class MyViewModel(private val api: ApiClient, ...)`
- Composables: `koinInject<Type>()` or `koinViewModel<T>()`
- Java interop: `KoinJavaComponent.inject<Type>(Type::class.java)`

### UI Architecture: Hybrid Leanback + Compose

The app uses **Android TV Leanback** for list navigation with targeted **Jetpack Compose** integration:

- **Leanback Fragments** handle core browsing (`HomeFragment`, `ItemListFragment`)
- **Compose embedded** via `ComposeView` for:
  - Main toolbar (`MainToolbar`)
  - Featured media bar slideshow (`MediaBarSlideshowView`)
  - Jellyseerr discovery UI
  - Settings screens

**Pattern:** Embed Compose in Leanback presenters/ViewHolders for modern UI elements while preserving TV navigation.

### Jellyfin SDK Integration

- SDK client: `org.jellyfin.sdk:jellyfin-core` v1.8.4
- API client injected via Koin as `ApiClient`
- Custom client name: `"Moonfin Android TV"` (see `appModule.kt`)
- Supports snapshot SDK versions via `gradle.properties`: `sdk.version=snapshot|unstable-snapshot|local|default`

**SDK Usage:**
```kotlin
val api = koinInject<ApiClient>()
val items = api.itemsApi.getResumeItems(userId, ...)
```

### Jellyseerr Integration

Custom Ktor-based HTTP client for TMDB/Jellyseerr API:
- Repository: `JellyseerrRepository` (`app/src/main/java/org/jellyfin/androidtv/data/repository/`)
- API models: `app/src/main/java/org/jellyfin/androidtv/data/model/jellyseerr/`
- UI: `app/src/main/java/org/jellyfin/androidtv/ui/jellyseerr/`
- Configuration: `JellyseerrPreferences` (stored per-server)

**Features:** Content discovery, quality profile selection, season picker, request tracking, NSFW filtering.

## Build System

### Version Management

Versions are defined in `gradle.properties`:
```properties
moonfin.version=v1.3.1
```

**Version code calculation** (`buildSrc/src/main/kotlin/VersionUtils.kt`):
- Format: `MA.MI.PA-PR` → `MAMIPAPR`
- Example: `1.3.1` → `1030199`, `1.3.1-rc.2` → `1030102`
- Pre-release defaults to `99` if omitted

### Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install debug to connected device
./gradlew installDebug

# Build release (requires keystore.properties)
./gradlew assembleRelease

# Run tests
./gradlew test

# Run Detekt linting
./gradlew detekt
```

**Keystore setup:** Copy `keystore.properties.template` → `keystore.properties` and configure signing credentials.

### Build Types

- **Debug:** Application ID `org.moonfin.androidtv.debug`, name "Moonfin Debug"
- **Release:** Application ID `org.moonfin.androidtv`, name "Moonfin"

## Code Conventions

### Language Mix: Kotlin + Java

- **New code:** Write in Kotlin
- **Legacy code:** Java files exist (e.g., `FullDetailsFragment.java`, `PlaybackController.java`)
- **Interop:** Use `@JvmOverloads`, `@JvmStatic`, `KoinJavaComponent` for Java→Kotlin

### Compose Best Practices

- Use `koinInject()` for dependencies in `@Composable` functions
- Prefer `collectAsState()` for Flow observation
- TV focus: Use `Modifier.focusable()` and `onFocusChanged` for D-pad navigation
- **Performance:** Minimize Compose usage in high-frequency list items (use Leanback Presenters)

### State Management

- ViewModels use Kotlin `StateFlow` and `Flow`
- User preferences: `UserPreferences[UserPreferences.key]` pattern
- Repositories expose `Flow` for reactive data

### Image Loading (Coil)

- `ImageLoader` configured with OkHttp, SVG, GIF support
- Helpers: `itemImages()`, `itemBackdropImages()` extensions for Jellyfin image URLs
- Example:
  ```kotlin
  val imageLoader = koinInject<ImageLoader>()
  imageLoader.enqueue(ImageRequest.Builder(context).data(url).target(...).build())
  ```

### Playback Architecture

The app uses a custom `PlaybackManager` (from `playback:core`) with plugins:
- `exoPlayerPlugin` - ExoPlayer integration
- `jellyfinPlugin` - Jellyfin SDK playback reporting
- `media3SessionPlugin` - Media session for notification/lock screen

**Key classes:**
- `PlaybackManager` - Core playback state machine
- `RewriteMediaManager` - Bridges new playback system with legacy `MediaManager`
- `PlaybackController` - Legacy controller (Java), gradually being phased out

## Key Files & Directories

- `app/src/main/java/org/jellyfin/androidtv/ui/home/` - Home screen logic
- `app/src/main/java/org/jellyfin/androidtv/ui/shared/toolbar/MainToolbar.kt` - Compose-based toolbar
- `app/src/main/java/org/jellyfin/androidtv/ui/jellyseerr/` - Jellyseerr discovery features
- `app/src/main/java/org/jellyfin/androidtv/preference/` - User preferences
- `app/src/main/java/org/jellyfin/androidtv/di/` - Koin DI modules
- `app/src/main/res/values/strings.xml` - Localized strings (70+ languages)
- `playback/jellyfin/src/main/kotlin/playsession/` - Playback session reporting to Jellyfin

## Testing

- Unit tests: Use Kotest + MockK
- Test location: `app/src/test/` and module-specific `src/test/`
- Run with: `./gradlew test`
- Tests use JUnit Platform (JUnit 5 via Kotest)

## Common Tasks

### Adding a New Preference

1. Define in `UserPreferences` or `JellyseerrPreferences` (`preference/` module)
2. Add UI in settings fragments or Compose settings screens
3. Access via `userPreferences[UserPreferences.newKey]`

### Adding a Jellyseerr Feature

1. Update API models in `app/src/main/java/org/jellyfin/androidtv/data/model/jellyseerr/`
2. Add repository method in `JellyseerrRepository`
3. Create/update UI in `app/src/main/java/org/jellyfin/androidtv/ui/jellyseerr/`
4. Use Ktor client for HTTP requests

### Updating Jellyfin SDK

Change in `gradle/libs.versions.toml`:
```toml
jellyfin-sdk = "1.x.x"
```
Or use snapshots via `gradle.properties`: `sdk.version=snapshot`

## Debugging

- Use `timber.log.Timber` for logging (already configured)
- LogInitializer sets up Timber automatically via AndroidX Startup
- Debug builds include LeakCanary (disabled by default in `gradle.properties`)

## Brand Identity

- Application name: **Moonfin** (not Jellyfin)
- Package: `org.moonfin.androidtv`
- Client info: "Moonfin Android TV" (sent to Jellyfin server)
- Keep references consistent with Moonfin branding in user-facing strings
