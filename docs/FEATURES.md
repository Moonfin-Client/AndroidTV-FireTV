# Moonfin-Exclusive Settings

Settings unique to Moonfin that are **not available in upstream Jellyfin AndroidTV**.

> **Legend:**  
> ⚠️ Requires Special Action/Input

---

## Jellyseerr Integration

 *Settings → Jellyseerr*

⚠️ *Requires Jellyseerr server*

- **Enable Jellyseerr** 
  - Enable integration with Jellyseerr for content requests
- **Jellyseerr URL**  ⚠️
  - Server URL for your Jellyseerr instance
- **Login with Jellyfin**  ⚠️
  - Automatically authenticate using your current Jellyfin account for ~30 days
- **Login with Local Account**  ⚠️
  - Authenticate using email and password
- **Jellyseerr API Key**  ⚠️
  - API key from Jellyseerr for authentication
- **Jellyseerr Rows** 
  - Configure which discovery rows to display (Trending Movies, Trending TV, Popular Movies, Popular TV, Upcoming Movies, Upcoming TV)
  - Enable/disable individual rows and customize their order

---

## Additional Ratings (MDBList)

 *Settings → Customization*

- **Enable Additional Ratings** 
  - Show ratings from external sources (AniList, IMDB, Letterboxd, Metacritic, MyAnimeList, Roger Ebert, TMDB, Trakt)
- **MDBList API Key**  ⚠️
  - Obtain from https://mdblist.com/ → Account Settings → API Key
  - Required for fetching additional metadata and ratings

---

## Toolbar Customization

 *Settings → Customization*

- **Navbar Position** 
  - Top (default)
  - Left
- **Show Shuffle Button** 
  - Display shuffle button in toolbar
- **Show Genres Button** 
  - Display genres button in toolbar
- **Show Favorites Button** 
  - Display favorites button in toolbar
- **Show Libraries in Toolbar** 
  - Display library shortcuts in toolbar
- **Shuffle Content Type** 
  - Movies, Series, or Mixed

---

## Home Screen Enhancements

 *Settings → Customization → Home*

- **Merge Continue Watching & Next Up** 
  - Combine both rows into one unified "Continue" row
- **Enable Multi-Server Libraries** 
  - Show libraries from multiple connected servers on home
- **Enable Folder View** 
  - Display folder structure navigation in libraries
- **Confirm Exit** 
  - Show confirmation dialog when pressing back to exit app

---

## Media Bar

 *Settings → Customization → Home*

Background media carousel on home screen.

- **Enable Media Bar** 
  - Show rotating background media on home screen
- **Media Bar Content Type** 
  - Movies, Series, or Mixed
- **Media Bar Item Count** 
  - 5, 10, or 15 items
- **Media Bar Opacity** 
  - Background overlay opacity (10-90% in 5% increments)
- **Media Bar Overlay Color** 
  - Black, Dark Gray, or Gray

---

## Visual Customization

 *Settings → Customization → Appearance*

- **Seasonal Surprise** 
  - None
  - Winter
  - Spring
  - Summer
  - Halloween
  - Fall
- **Home Rows Image Type** 
  - Override toggle to force a single image type for all home rows
  - Options: Backdrop, Poster, or Thumbnail
- **Details Screen Blur** 
  - Background blur intensity on detail screens (None, Light, Medium, Strong, Extra Strong)
- **Browsing Blur** 
  - Background blur in browsing/library screens (None, Light, Medium, Strong, Extra Strong)

---

## Theme Music

 *Settings → Customization*

- **Enable Theme Music** 
  - Play theme music when viewing item details
- **Theme Music on Home Rows** 
  - Play theme music when focused on items in home rows
- **Theme Music Volume** 
  - Volume for item theme music playback (10-100% in 5% increments)

---

## Playback Enhancements

 *Settings → Playback → Advanced*

- **Enable libass Subtitle Renderer** 
  - Use libass for ASS/SSA subtitle rendering
  - Better support for styled/animated subtitles

---

## SyncPlay Advanced Settings

 *Settings → SyncPlay*

Fine-tuning for synchronized playback sessions.

- **Enable SyncPlay** 
  - Enable synchronized playback with other users
- **Enable Sync Correction** 
  - Automatically adjust playback to stay in sync with the group
- **Use Speed to Sync** 
  - Enable speed-based synchronization (temporarily speeds up/slows down playback)
- **Min Delay for Speed Sync** 
  - Minimum delay before adjusting playback speed (10-1000ms)
- **Max Delay for Speed Sync** 
  - Maximum delay before adjusting playback speed (10-1000ms)
- **Speed Sync Duration** 
  - Duration of speed adjustment (500-5000ms)
- **Use Skip to Sync** 
  - Enable skip-based synchronization (jumps to correct position)
- **Min Delay for Skip Sync** 
  - Minimum delay before skipping to sync (10-5000ms)
- **Extra Time Offset** 
  - Additional time offset adjustment (-1000 to +1000ms)

---

## Parental Controls

 *Settings → Customization*

- **Blocked Ratings** 
  - Select specific content ratings to block (e.g., R, TV-MA, NC-17)
  - Blocks content with selected ratings from appearing in libraries and search results

---

## Updates & Support

 *Settings → Support & Updates*

- **Check for Updates**  ⚠️
  - Manually check for new Moonfin versions
  - Download and install APK updates directly
  - View release notes before updating
  - ⚠️ Requires "Install Unknown Apps" permission on Android 8+
- **Update Notifications** 
  - Show notification on app launch when updates are available
- **Support Moonfin** 
  - Donation links and ways to contribute

---

## Notes

### Settings Requiring External Setup

| Setting | Requirement |
|---------|-------------|
| MDBList API Key | Register at https://mdblist.com/ |
| Jellyseerr | Separate Jellyseerr server installation |
| Parental Controls | PIN code must be set first |
| Update Installation | "Install Unknown Apps" permission (Android 8+) |

### Feature Availability

- **Jellyseerr rows**: Only visible when Jellyseerr is configured
- **Multi-Server features**: Only useful with multiple servers connected
- **SyncPlay tuning**: Only relevant during active SyncPlay sessions

---

*Last Updated: 2026-01-28*
