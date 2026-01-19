package org.jellyfin.androidtv.preference

import android.content.Context
import androidx.preference.PreferenceManager
import org.jellyfin.androidtv.preference.UserPreferences.Companion.screensaverInAppEnabled
import org.jellyfin.androidtv.preference.constant.AppTheme
import org.jellyfin.androidtv.preference.constant.AudioBehavior
import org.jellyfin.androidtv.preference.constant.ClockBehavior
import org.jellyfin.androidtv.preference.constant.NextUpBehavior
import org.jellyfin.androidtv.preference.constant.RatingType
import org.jellyfin.androidtv.preference.constant.RefreshRateSwitchingBehavior
import org.jellyfin.androidtv.preference.constant.StillWatchingBehavior
import org.jellyfin.androidtv.preference.constant.WatchedIndicatorBehavior
import org.jellyfin.androidtv.preference.constant.ZoomMode
import org.jellyfin.androidtv.ui.playback.segment.MediaSegmentAction
import org.jellyfin.androidtv.ui.playback.segment.toMediaSegmentActionsString
import org.jellyfin.preference.booleanPreference
import org.jellyfin.preference.enumPreference
import org.jellyfin.preference.floatPreference
import org.jellyfin.preference.intPreference
import org.jellyfin.preference.longPreference
import org.jellyfin.preference.store.SharedPreferenceStore
import org.jellyfin.preference.stringPreference
import org.jellyfin.sdk.model.api.MediaSegmentType
import kotlin.time.Duration.Companion.minutes

/**
 * User preferences are configurable by the user and change behavior of the application.
 * When changing preferences migration should be added to the init function.
 *
 * @param context Context to get the SharedPreferences from
 */
class UserPreferences(context: Context) : SharedPreferenceStore(
	sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
) {
	companion object {
		/* Display */
		/**
		 * Select the app theme
		 */
		var appTheme = enumPreference("app_theme", AppTheme.DARK)

		/**
		 * Enable background images while browsing
		 */
		var backdropEnabled = booleanPreference("pref_show_backdrop", true)

		/* Toolbar Customization */
		/**
		 * Show shuffle button in toolbar
		 */
		var showShuffleButton = booleanPreference("pref_show_shuffle_button", true)

		/**
		 * Show genres button in toolbar
		 */
		var showGenresButton = booleanPreference("pref_show_genres_button", true)

		/**
		 * Show favorites button in toolbar
		 */
		var showFavoritesButton = booleanPreference("pref_show_favorites_button", true)

		/**
		 * Show library buttons in toolbar
		 */
		var showLibrariesInToolbar = booleanPreference("pref_show_libraries_in_toolbar", true)

		var syncPlayEnabled = booleanPreference("pref_syncplay_enabled", true)

		/**
		 * Content type for shuffle button (movies, tv, or both)
		 */
		var shuffleContentType = stringPreference("pref_shuffle_content_type", "both")

		/* SyncPlay Settings */
		/**
		 * Enable sync correction during playback
		 */
		var syncPlayEnableSyncCorrection = booleanPreference("syncplay_enable_sync_correction", false)

		/**
		 * Use SpeedToSync method to catch up with group
		 */
		var syncPlayUseSpeedToSync = booleanPreference("syncplay_use_speed_to_sync", true)

		/**
		 * Use SkipToSync method to catch up with group
		 */
		var syncPlayUseSkipToSync = booleanPreference("syncplay_use_skip_to_sync", true)

		/**
		 * Minimum delay for SpeedToSync to kick in (ms)
		 */
		var syncPlayMinDelaySpeedToSync = floatPreference("syncplay_min_delay_speed_to_sync", 60f)

		/**
		 * Maximum delay after which SkipToSync is used instead of SpeedToSync (ms)
		 */
		var syncPlayMaxDelaySpeedToSync = floatPreference("syncplay_max_delay_speed_to_sync", 3000f)

		/**
		 * Duration for which playback is sped up (ms)
		 */
		var syncPlaySpeedToSyncDuration = floatPreference("syncplay_speed_to_sync_duration", 1000f)

		/**
		 * Minimum delay for SkipToSync to kick in (ms)
		 */
		var syncPlayMinDelaySkipToSync = floatPreference("syncplay_min_delay_skip_to_sync", 400f)

		/**
		 * Extra time offset for fine-tuning sync (ms)
		 */
		var syncPlayExtraTimeOffset = floatPreference("syncplay_extra_time_offset", 0f)

		/**
		 * Show confirmation dialog when exiting the app
		 */
		var confirmExit = booleanPreference("confirm_exit", true)

		/**
		 * Enable automatic update notifications on app launch
		 */
		var updateNotificationsEnabled = booleanPreference("update_notifications_enabled", true)

		/**
		 * Seasonal surprise effect selection (none, winter, spring, fall)
		 */
		var seasonalSurprise = stringPreference("seasonal_surprise", "none")

		/**
		 * Enable multi-server library aggregation
		 * When enabled, shows libraries and content from all logged-in servers
		 */
		var enableMultiServerLibraries = booleanPreference("enable_multi_server_libraries", false)

		/* Playback - General*/
		/**
		 * Maximum bitrate in megabit for playback.
		 */
		var maxBitrate = stringPreference("pref_max_bitrate", "100")

		/**
		 * Auto-play next item
		 */
		var mediaQueuingEnabled = booleanPreference("pref_enable_tv_queuing", true)

		/**
		 * Enable the next up screen or not
		 */
		var nextUpBehavior = enumPreference("next_up_behavior", NextUpBehavior.EXTENDED)

		/**
		 * Next up timeout before playing next item
		 * Stored in milliseconds
		 */
		var nextUpTimeout = intPreference("next_up_timeout", 1000 * 7)

		/**
		 * Duration in seconds to subtract from resume time
		 */
		var resumeSubtractDuration = stringPreference("pref_resume_preroll", "0")

		/**
		 * Enable cinema mode
		 */
		var cinemaModeEnabled = booleanPreference("pref_enable_cinema_mode", true)

		/**
		 * Enable still watching
		 */
		var stillWatchingBehavior = enumPreference("enable_still_watching", StillWatchingBehavior.DISABLED)

		/* Playback - Video */
		/**
		 * Whether to use an external playback application or not.
		 */
		var useExternalPlayer = booleanPreference("external_player", false)

		/**
		 * Component name for the external playback application.
		 */
		var externalPlayerComponentName = stringPreference("external_player_component", "")

		/**
		 * Change refresh rate to match media when device supports it
		 */
		var refreshRateSwitchingBehavior = enumPreference("refresh_rate_switching_behavior", RefreshRateSwitchingBehavior.DISABLED)

		/**
		 * Whether ExoPlayer should prefer FFmpeg renderers to core ones.
		 */
		var preferExoPlayerFfmpeg = booleanPreference("exoplayer_prefer_ffmpeg", defaultValue = false)

		/* Playback - Audio related */
		/**
		 * Preferred behavior for audio streaming.
		 */
		var audioBehaviour = enumPreference("audio_behavior", AudioBehavior.DIRECT_STREAM)

		/**
		 * Preferred behavior for audio streaming.
		 */
		var audioNightMode = enumPreference("audio_night_mode", false)

		/**
		 * Enable AC3
		 */
		var ac3Enabled = booleanPreference("pref_bitstream_ac3", true)

		/* Live TV */
		/**
		 * Use direct play
		 */
		var liveTvDirectPlayEnabled = booleanPreference("pref_live_direct", true)

		/* Developer options */
		/**
		 * Show additional debug information
		 */
		var debuggingEnabled = booleanPreference("pref_enable_debug", false)

		/**
		 * Use playback rewrite module for video
		 */
		var playbackRewriteVideoEnabled = booleanPreference("playback_new", false)

		/**
		 * When to show the clock.
		 */
		var clockBehavior = enumPreference("pref_clock_behavior", ClockBehavior.ALWAYS)

		/**
		 * Set which ratings provider should show on MyImageCardViews
		 */
		var defaultRatingType = enumPreference("pref_rating_type", RatingType.RATING_TOMATOES)

		/**
		 * Set when watched indicators should show on MyImageCardViews
		 */
		var watchedIndicatorBehavior = enumPreference("pref_watched_indicator_behavior", WatchedIndicatorBehavior.ALWAYS)

		/**
		 * Enable series thumbnails in home screen rows
		 */
		var seriesThumbnailsEnabled = booleanPreference("pref_enable_series_thumbnails", false)

		/**
		 * Merge Continue Watching and Next Up into a single row
		 */
		var mergeContinueWatchingNextUp = booleanPreference("pref_merge_continue_watching_next_up", false)

		/**
		 * Subtitles foreground color
		 */
		var subtitlesBackgroundColor = longPreference("subtitles_background_color", 0x00FFFFFF)

		/**
		 * Subtitles bold text
		 */
		var subtitlesTextWeight = intPreference("subtitles_text_weight", 400)

		/**
		 * Subtitles foreground color
		 */
		var subtitlesTextColor = longPreference("subtitles_text_color", 0xFFFFFFFF)

		/**
		 * Subtitles stroke color
		 */
		var subtitleTextStrokeColor = longPreference("subtitles_text_stroke_color", 0xFF000000)

		/**
		 * Subtitles font size
		 */
		var subtitlesTextSize = floatPreference("subtitles_text_size", 24f)

		/**
		 * Subtitles offset
		 */
		var subtitlesOffsetPosition = floatPreference("subtitles_offset_position", 0.08f)

		/**
	 * Force subtitles to default to None instead of server default
	 */
	var subtitlesDefaultToNone = booleanPreference("subtitles_default_to_none", false)

	/**
	 * Show screensaver in app
	 */
	var screensaverInAppEnabled = booleanPreference("screensaver_inapp_enabled", true)

	/**
	 * Screensaver mode (library or logo)
	 */
	var screensaverMode = stringPreference("pref_screensaver_mode", "library")

	/**
	 * Screen dimming level for the screensaver (1-100%).
	 * 0 means no dimming.
	 */
		var screensaverDimmingLevel = intPreference("pref_screensaver_dimming_level", 0)

		/**
		 * Timeout before showing the screensaver in app, depends on [screensaverInAppEnabled].
		 */
		var screensaverInAppTimeout = longPreference("screensaver_inapp_timeout", 5.minutes.inWholeMilliseconds)

		/**
		 * Age rating used to filter items in the screensaver. Use -1 to disable (omits parameter from requests).
		 */
		var screensaverAgeRatingMax = intPreference("screensaver_agerating_max", 13)

		/**
		 * Whether items shown in the screensaver are required to have an age rating set.
		 */
		var screensaverAgeRatingRequired = booleanPreference("screensaver_agerating_required", true)

		/**
		 * Show clock in screensaver when enabled
		 */
		var screensaverShowClock = booleanPreference("screensaver_show_clock", true)

		/**
		 * Delay when starting video playback after loading the video player.
		 */
		var videoStartDelay = longPreference("video_start_delay", 0)

		/**
		 * The actions to take for each media segment type. Managed by the [MediaSegmentRepository].
		 */
		var mediaSegmentActions = stringPreference(
			key = "media_segment_actions",
			defaultValue = mapOf(
				MediaSegmentType.INTRO to MediaSegmentAction.ASK_TO_SKIP,
				MediaSegmentType.OUTRO to MediaSegmentAction.ASK_TO_SKIP,
			).toMediaSegmentActionsString()
		)

		/**
		 * Preferred behavior for player aspect ratio (zoom mode).
		 */
		var playerZoomMode = enumPreference("player_zoom_mode", ZoomMode.FIT)

		/**
		 * Enable TrickPlay in legacy player user interface while seeking.
		 */
		var trickPlayEnabled = booleanPreference("trick_play_enabled", false)

		/**
  		 * Enable PGS subtitle direct-play.
		 */
		var pgsDirectPlay = booleanPreference("pgs_enabled", true)
	}

	init {
		// Note: Create a single migration per app version
		// Note: Migrations are never executed for fresh installs
		// Note: Old migrations are removed occasionally
		runMigrations {
			// v0.15.z to v0.16.0
			migration(toVersion = 7) {
				// Enable playback rewrite for music
				putBoolean("playback_new_audio", true)
			}

			// v0.17.z to v0.18.0
			migration(toVersion = 8) {
				// Set subtitle background color to black if it was enabled in a previous version
				val subtitlesBackgroundEnabled = it.getBoolean("subtitles_background_enabled", true)
				putLong("subtitles_background_color", if (subtitlesBackgroundEnabled) 0XFF000000L else 0X00FFFFFFL)

				// Set subtitle text stroke color to black if it was enabled in a previous version
				val subtitleStrokeSize = it.getInt("subtitles_stroke_size", 0)
				putLong("subtitles_text_stroke_color", if (subtitleStrokeSize > 0) 0XFF000000L else 0X00FFFFFFL)
			}

// v0.19.0 to v0.20.0
			migration(toVersion = 9) {
				// Reset subtitle text size as we changed from fractional sizing to absolute sizing
				remove("subtitles_text_size")
			}

			// v1.2.0 to v1.3.0
			migration(toVersion = 10) { prefs ->
				// Migrate screensaver dimming from boolean to int (0-100)
				val oldDimmingKey = "pref_screensaver_dimming"
				val wasEnabled = prefs.getBoolean(oldDimmingKey, false)
				val dimmingLevel = if (wasEnabled) 50 else 0
				putInt(screensaverDimmingLevel.key, dimmingLevel)
				remove(oldDimmingKey)
				
				// Migrate mediaBarOverlayOpacity from String to Int
				val overlayKey = "mediaBarOverlayOpacity"
				if (prefs.contains(overlayKey)) {
					try {
						// Try reading as String first (old format)
						val stringValue = prefs.getString(overlayKey, null)
						if (stringValue != null) {
							val intValue = stringValue.toIntOrNull() ?: 50
							putInt(overlayKey, intValue)
						}
					} catch (e: ClassCastException) {
						// Already stored as Int, no migration needed
					}
				}
			}
		}
	}
}
