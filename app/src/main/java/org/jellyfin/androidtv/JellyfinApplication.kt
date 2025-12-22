package org.jellyfin.androidtv

import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import coil3.ImageLoader
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.acra.ACRA
import org.jellyfin.androidtv.util.apiclient.ioCall
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.eventhandling.SocketHandler
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrHttpClient
import org.jellyfin.androidtv.integration.LeanbackChannelWorker
import org.jellyfin.androidtv.preference.JellyseerrPreferences
import org.jellyfin.androidtv.ui.background.UpdateCheckWorker
import org.jellyfin.androidtv.telemetry.TelemetryService
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named
import timber.log.Timber
import java.util.concurrent.TimeUnit

@Suppress("unused")
class JellyfinApplication : Application(), SingletonImageLoader.Factory {
	override fun onCreate() {
		super.onCreate()

		// Don't run in ACRA service
		if (ACRA.isACRASenderServiceProcess()) return

		val notificationsRepository by inject<NotificationsRepository>()
		notificationsRepository.addDefaultNotifications()
		
		// Monitor Jellyfin user changes and clear Jellyseerr cookies when user switches
		setupJellyseerrUserMonitoring()
	}

	/**
	 * Provide the Koin-configured ImageLoader as the singleton for Coil.
	 * This ensures all Coil Compose components (AsyncImage, rememberAsyncImagePainter, etc.)
	 * use the ImageLoader with proper authentication, caching, and decoders.
	 */
	override fun newImageLoader(context: Context): ImageLoader {
		val imageLoader by inject<ImageLoader>()
		return imageLoader
	}
	
	private fun setupJellyseerrUserMonitoring() {
		val userRepository by inject<UserRepository>()
		val jellyseerrPreferencesGlobal by inject<JellyseerrPreferences>(named("global"))
		
		ProcessLifecycleOwner.get().lifecycleScope.launch {
			userRepository.currentUser.collect { currentUser ->
				val currentUsername = currentUser?.name
				val currentUserId = currentUser?.id?.toString()
				val lastJellyfinUser = jellyseerrPreferencesGlobal[JellyseerrPreferences.lastJellyfinUser]
				
				// Switch cookie storage and preferences when user changes (each user gets their own Jellyseerr session)
				if (currentUserId != null && currentUsername != null) {
					// Switch to this user's cookie storage
					JellyseerrHttpClient.switchCookieStorage(currentUserId)
					
					// Update the stored username in global prefs
					jellyseerrPreferencesGlobal[JellyseerrPreferences.lastJellyfinUser] = currentUsername
				}
			}
		}
	}

	/**
	 * Called from the StartupActivity when the user session is started.
	 */
	suspend fun onSessionStart() = withContext(Dispatchers.IO) {
		val workManager by inject<WorkManager>()
		val socketListener by inject<SocketHandler>()

		// Update background worker
		launch {
			// Cancel all current workers
			workManager.cancelAllWork().await()

			// Recreate periodic workers
			workManager.enqueueUniquePeriodicWork(
				LeanbackChannelWorker.PERIODIC_UPDATE_REQUEST_NAME,
				ExistingPeriodicWorkPolicy.UPDATE,
				PeriodicWorkRequestBuilder<LeanbackChannelWorker>(1, TimeUnit.HOURS)
					.setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
					.build()
			).await()

			// Schedule update check worker (daily)
			workManager.enqueueUniquePeriodicWork(
				UpdateCheckWorker.WORK_NAME,
				ExistingPeriodicWorkPolicy.KEEP,
				PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
					.setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
					.build()
			).await()
		}

		// Update WebSockets
		launch { socketListener.updateSession() }
	}

	override fun attachBaseContext(base: Context?) {
		super.attachBaseContext(base)

		TelemetryService.init(this)
	}
}
