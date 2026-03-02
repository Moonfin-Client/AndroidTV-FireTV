# Emby Server Support

Moonfin supports Emby Server **4.8.0.0 and newer** as a first-class server backend alongside Jellyfin. Both server types can be used simultaneously in multi-server configurations.

## Supported Versions

| Emby Version | Status |
|--------------|--------|
| 4.9.x | Fully tested, recommended |
| 4.8.x | Supported |
| < 4.8.0.0 | Not supported |

## Server Detection

When adding a server, Moonfin automatically detects whether it is running Jellyfin or Emby by querying the `/System/Info/Public` endpoint. No manual configuration is required. The detected server type is stored with the server entry and used for all subsequent connections.

## Feature Compatibility Matrix

| Feature | Jellyfin | Emby | Notes |
|---------|----------|------|-------|
| **Authentication** | | | |
| Username/Password login | Yes | Yes | Identical |
| QuickConnect (QR code) | Yes | No | Different Emby API, not yet implemented |
| Public user list | Yes | Yes | Identical |
| Token auto-login | Yes | Yes | Identical |
| Emby Connect | No | No | Cloud account linking, not yet implemented |
| **Browsing** | | | |
| Library sections | Yes | Yes | Identical |
| Item details | Yes | Yes | Identical |
| Search | Yes | Yes | Identical |
| Favorites | Yes | Yes | Identical |
| Played/Unplayed | Yes | Yes | Identical |
| Resume items | Yes | Yes | Identical |
| Next Up | Yes | Yes | Identical |
| Similar items | Yes | Yes | Identical |
| Latest media | Yes | Yes | Identical |
| Display preferences | Yes | Yes | Cached in-memory (5 min TTL) |
| **Playback** | | | |
| Video direct play | Yes | Yes | Identical |
| Video transcode (HLS) | Yes | Yes | Identical |
| Audio direct play | Yes | Yes | Identical |
| Audio transcode | Yes | Yes | Identical |
| Subtitles (external) | Yes | Yes | Identical |
| Subtitles (burn-in) | Yes | Yes | Identical |
| Device profile negotiation | Yes | Yes | Same model, different construction |
| Playback progress reporting | Yes | Yes | Identical |
| Chapter navigation | Yes | Yes | Identical |
| Intro/credits skip | Yes | No | Jellyfin Media Segments, not available in Emby |
| Trickplay (seek preview) | Yes | No | Different format (Jellyfin: trickplay, Emby: BIF). BIF not yet implemented |
| Lyrics | Yes | No | Jellyfin-only API |
| **Live TV** | | | |
| Channel list | Yes | Yes | Identical |
| Program guide (EPG) | Yes | Yes | Identical |
| Live TV playback | Yes | Yes | Identical |
| DVR recordings | Yes | Yes | Identical |
| Timer management | Yes | Yes | Identical |
| **Real-Time** | | | |
| WebSocket events | Yes | Yes | Native OkHttp WebSocket for Emby |
| Remote control | Yes | Yes | Similar message types |
| Library change notifications | Yes | Yes | Similar message types |
| SyncPlay (group watch) | Yes | No | Emby uses incompatible "Party" protocol |
| **Other** | | | |
| Multi-server | Yes | Yes | Mix of Jellyfin and Emby servers supported |
| Theme music | Yes | Yes | Identical |
| Client log upload | Yes | No | Jellyfin-only |
| Home screen channels | Yes | Yes | Uses common item abstraction |
| Screensaver | Yes | Yes | Uses common image abstraction |
| External player | Yes | Yes | Stream URL adapts per server type |
| Jellyseerr | Yes | No | Requires Jellyfin server for auth |

## Feature Gating

Features that are not available on Emby are automatically hidden from the UI when connected to an Emby server. This includes:

- SyncPlay menu items and controls
- Media segment skip buttons (intro/credits)
- Lyrics display
- Trickplay seek preview thumbnails
- Client log upload option

No manual configuration is needed. The feature set adapts automatically based on the connected server type.

## ID Format Differences

Jellyfin uses UUID-format IDs (`550e8400-e29b-41d4-a716-446655440000`) while Emby uses numeric IDs (`12345`). Moonfin handles this transparently:

- The `EmbyCompatInterceptor` converts between formats in API requests and responses
- Numeric Emby IDs are mapped to deterministic UUIDs for internal consistency
- No user-visible difference in behavior

## Image Caching

Emby image URLs include an `api_key` query parameter for authentication. Moonfin strips this parameter from Coil cache keys so that image caches survive token rotation. The `tag` parameter is preserved as a cache buster for content changes.

## WebSocket Reconnection

The Emby WebSocket client uses exponential backoff with random jitter to avoid thundering herd problems when multiple clients reconnect after a network interruption. The maximum number of reconnect attempts is 12, after which the connection state transitions to `ServerUnreachable`.

## API Response Caching

The following Emby API responses are cached in memory with a 5-minute TTL to reduce redundant network requests:

| API | Cache Scope | Invalidation |
|-----|-------------|-------------|
| User views (library sections) | Per user ID | Manual via `invalidateCache()`, session change |
| Display preferences | Per (id, userId, client) | Updated on save, manual via `invalidateCache()` |

## Known Limitations

- **Emby Connect** (cloud account linking) is not yet supported
- **BIF trickplay** (seek preview thumbnails) uses a different format than Jellyfin and is not yet implemented
- **QuickConnect** on Emby uses a different API than Jellyfin and is not yet supported
- **SyncPlay** is Jellyfin-only; Emby's "Watch Party" uses an incompatible protocol
- **Jellyseerr** integration requires a Jellyfin server for authentication and does not work with Emby-only setups

## Telemetry

When connected to an Emby server, crash reports include the server type (`emby`) and server version in a dedicated "Server information" section. This helps diagnose server-specific issues. No personally identifiable server information (URLs, tokens, user IDs) is included.
