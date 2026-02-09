package org.jellyfin.androidtv.di

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.browsing.MainActivity
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.androidtv.ui.playback.PrePlaybackTrackSelector
import org.jellyfin.androidtv.ui.playback.VideoQueueManager
import org.jellyfin.androidtv.ui.playback.rewrite.RewriteMediaManager
import org.jellyfin.androidtv.util.profile.createDeviceProfile
import org.jellyfin.playback.core.playbackManager
import org.jellyfin.playback.jellyfin.jellyfinPlugin
import org.jellyfin.playback.media3.exoplayer.ExoPlayerOptions
import org.jellyfin.playback.media3.exoplayer.exoPlayerPlugin
import org.jellyfin.playback.media3.session.MediaSessionOptions
import org.jellyfin.playback.media3.session.media3SessionPlugin
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.koin.android.ext.koin.androidContext
import kotlin.time.Duration
import org.koin.core.scope.Scope
import org.koin.dsl.module
import kotlin.time.Duration.Companion.milliseconds
import org.jellyfin.androidtv.ui.playback.PlaybackManager as LegacyPlaybackManager

val playbackModule = module {
	single<LegacyPlaybackManager> { LegacyPlaybackManager(get(), get()) }
	single { VideoQueueManager() }
	single<MediaManager> { RewriteMediaManager(get(), get()) }

	single { PlaybackLauncher(get(), get(), get(), get(), get()) }
	single { PrePlaybackTrackSelector(androidContext()) }

	single<HttpDataSource.Factory> {
		val okHttpFactory = get<OkHttpFactory>()
		val httpClientOptions = get<HttpClientOptions>().copy(
			// Disable request timeout for media playback as this causes issues with Live TV
			requestTimeout = Duration.ZERO
		)

		OkHttpDataSource.Factory(okHttpFactory.createClient(httpClientOptions))
	}

	single { createPlaybackManager() }
}

fun Scope.createPlaybackManager() = playbackManager(androidContext()) {
	val activityIntent = Intent(get(), MainActivity::class.java)
	val pendingIntent = PendingIntent.getActivity(get(), 0, activityIntent, PendingIntent.FLAG_IMMUTABLE)

	val notificationChannelId = "session"
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
		val channel = NotificationChannel(
			notificationChannelId,
			notificationChannelId,
			NotificationManager.IMPORTANCE_LOW
		)
		channel.setShowBadge(false)
		NotificationManagerCompat.from(get()).createNotificationChannel(channel)
	}

	val userPreferences = get<UserPreferences>()
	val exoPlayerOptions = ExoPlayerOptions(
		preferFfmpeg = userPreferences[UserPreferences.preferExoPlayerFfmpeg],
		enableDebugLogging = userPreferences[UserPreferences.debuggingEnabled],
		enableLibAssRenderer = userPreferences[UserPreferences.assDirectPlay],
		assSubtitleFontScale = userPreferences[UserPreferences.subtitlesTextSize] / 24f,
		baseDataSourceFactory = get<HttpDataSource.Factory>(),
	)
	install(exoPlayerPlugin(get(), exoPlayerOptions))

	val mediaSessionOptions = MediaSessionOptions(
		channelId = notificationChannelId,
		notificationId = 1,
		iconSmall = R.drawable.app_icon_foreground,
		openIntent = pendingIntent,
	)
	install(media3SessionPlugin(get(), mediaSessionOptions))

	val deviceProfileBuilder = { createDeviceProfile(androidContext(), userPreferences, get()) }
	
	// Create API client resolver for cross-server support
	val apiClientFactory = get<org.jellyfin.androidtv.util.sdk.ApiClientFactory>()
	val sessionRepository = get<org.jellyfin.androidtv.auth.repository.SessionRepository>()
	val authenticationStore = get<org.jellyfin.androidtv.auth.store.AuthenticationStore>()
	val apiClientResolver: (java.util.UUID?) -> org.jellyfin.sdk.api.client.ApiClient? = { serverId ->
		serverId?.let { 
			val currentSession = sessionRepository.currentSession.value
			
			val userId = if (currentSession?.serverId == it) {
				currentSession.userId
			} else {
				authenticationStore.getServer(it)?.users?.entries
					?.firstOrNull { (_, user) -> !user.accessToken.isNullOrBlank() }
					?.key
			}
			
			if (userId != null) {
				apiClientFactory.getApiClient(it, userId) ?: apiClientFactory.getApiClientForServer(it)
			} else {
				apiClientFactory.getApiClientForServer(it)
			}
		}
	}
	
	install(jellyfinPlugin(get(), deviceProfileBuilder, ProcessLifecycleOwner.get().lifecycle, apiClientResolver))

	// Options
	val userSettingPreferences = get<UserSettingPreferences>()
	defaultRewindAmount = { userSettingPreferences[UserSettingPreferences.skipBackLength].milliseconds }
	defaultFastForwardAmount = { userSettingPreferences[UserSettingPreferences.skipForwardLength].milliseconds }
	unpauseRewindAmount = { userSettingPreferences[UserSettingPreferences.unpauseRewindDuration].milliseconds }
}
