package org.jellyfin.androidtv.di

import android.content.Context
import android.os.Build
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.NetworkFetcher
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import coil3.svg.SvgDecoder
import coil3.util.Logger
import okio.Path.Companion.toOkioPath
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.auth.repository.UserRepositoryImpl
import org.jellyfin.androidtv.data.eventhandling.SocketHandler
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.CustomMessageRepository
import org.jellyfin.androidtv.data.repository.CustomMessageRepositoryImpl
import org.jellyfin.androidtv.data.repository.ExternalAppRepository
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.data.repository.ItemMutationRepositoryImpl
import org.jellyfin.androidtv.data.repository.JellyseerrRepository
import org.jellyfin.androidtv.data.repository.JellyseerrRepositoryImpl
import org.jellyfin.androidtv.data.repository.LocalWatchlistRepository
import org.jellyfin.androidtv.data.repository.MdbListRepository
import org.jellyfin.androidtv.data.repository.TmdbRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepositoryImpl
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepositoryImpl
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.data.service.UpdateCheckerService
import org.jellyfin.androidtv.preference.JellyseerrPreferences
import org.jellyfin.androidtv.data.syncplay.SyncPlayManager
import org.jellyfin.androidtv.integration.dream.DreamViewModel
import org.jellyfin.androidtv.ui.InteractionTrackerViewModel
import org.jellyfin.androidtv.ui.home.mediabar.MediaBarSlideshowViewModel
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.navigation.NavigationRepositoryImpl
import org.jellyfin.androidtv.ui.shuffle.ShuffleManager
import org.jellyfin.androidtv.ui.playback.PlaybackControllerContainer
import org.jellyfin.androidtv.ui.playback.nextup.NextUpViewModel
import org.jellyfin.androidtv.ui.playback.segment.MediaSegmentRepository
import org.jellyfin.androidtv.ui.playback.segment.MediaSegmentRepositoryImpl
import org.jellyfin.androidtv.ui.playback.stillwatching.StillWatchingViewModel
import org.jellyfin.androidtv.ui.player.photo.PhotoPlayerViewModel
import org.jellyfin.androidtv.ui.search.SearchFragmentDelegate
import org.jellyfin.androidtv.ui.search.SearchRepository
import org.jellyfin.androidtv.ui.search.SearchRepositoryImpl
import org.jellyfin.androidtv.ui.search.SearchViewModel
import org.jellyfin.androidtv.ui.syncplay.SyncPlayViewModel
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.jellyfin.androidtv.ui.startup.ServerAddViewModel
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import org.jellyfin.androidtv.ui.startup.UserLoginViewModel
import org.jellyfin.androidtv.util.KeyProcessor
import org.jellyfin.androidtv.util.MarkdownRenderer
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.androidtv.util.apiclient.ReportingHelper
import org.jellyfin.androidtv.util.coil.CoilTimberLogger
import org.jellyfin.androidtv.util.coil.createCoilConnectivityChecker
import org.jellyfin.androidtv.util.sdk.SdkPlaybackHelper
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.jellyfin.sdk.Jellyfin as JellyfinSdk

val defaultDeviceInfo = named("defaultDeviceInfo")

val appModule = module {
	// SDK
	single(defaultDeviceInfo) { androidDevice(get()) }
	single { OkHttpFactory() }
	single { HttpClientOptions() }
	single {
		createJellyfin {
			context = androidContext()

			// Add client info
			val clientName = buildString {
				append("Moonfin Android TV")
				if (BuildConfig.DEBUG) append(" (debug)")
			}
			clientInfo = ClientInfo(clientName, BuildConfig.VERSION_NAME)
			deviceInfo = get(defaultDeviceInfo)

			// Change server version
			minimumServerVersion = ServerRepository.minimumServerVersion

			// Use our own shared factory instance
			apiClientFactory = get<OkHttpFactory>()
			socketConnectionFactory = get<OkHttpFactory>()
		}
	}

	single {
		// Create an empty API instance, the actual values are set by the SessionRepository
		get<JellyfinSdk>().createApi(httpClientOptions = get<HttpClientOptions>())
	}

	single { SocketHandler(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), ProcessLifecycleOwner.get().lifecycle) }

	// Coil (images)
	single {
		val okHttpFactory = get<OkHttpFactory>()
		val httpClientOptions = get<HttpClientOptions>()

		@OptIn(ExperimentalCoilApi::class)
		OkHttpNetworkFetcherFactory(
			callFactory = { okHttpFactory.createClient(httpClientOptions) },
			connectivityChecker = ::createCoilConnectivityChecker,
		)
	}

	single {
		val context = androidContext()
		ImageLoader.Builder(context).apply {
			serviceLoaderEnabled(false)
			logger(CoilTimberLogger(if (BuildConfig.DEBUG) Logger.Level.Warn else Logger.Level.Error))

			// Configure memory cache - use 25% of available memory for images
			memoryCache {
				coil3.memory.MemoryCache.Builder()
					.maxSizePercent(context, percent = 0.25)
					.strongReferencesEnabled(true)
					.build()
			}

			// Configure disk cache - 250MB for image caching
			diskCache {
				coil3.disk.DiskCache.Builder()
					.directory(context.cacheDir.resolve("image_cache").toOkioPath())
					.maxSizeBytes(250L * 1024 * 1024) // 250 MB
					.build()
			}

			components {
				add(get<NetworkFetcher.Factory>())

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AnimatedImageDecoder.Factory())
				else add(GifDecoder.Factory())
				add(SvgDecoder.Factory())
			}
		}.build()
	}

	// Non API related
	single { DataRefreshService() }
	single { PlaybackControllerContainer() }
	// Use single scope to ensure the same instance is used across all playback sessions
	single { InteractionTrackerViewModel(get(), get()) }

	single<UserRepository> { UserRepositoryImpl() }
	single<UserViewsRepository> { UserViewsRepositoryImpl(get()) }
	single<NotificationsRepository> { NotificationsRepositoryImpl(get(), get()) }
	single<ItemMutationRepository> { ItemMutationRepositoryImpl(get(), get()) }
	single<CustomMessageRepository> { CustomMessageRepositoryImpl() }
	single<NavigationRepository> { NavigationRepositoryImpl(Destinations.home) }
	single { ShuffleManager(get(), get(), get(), get()) }
	single<SearchRepository> { SearchRepositoryImpl(get(), get()) }
	single<MediaSegmentRepository> { MediaSegmentRepositoryImpl(get(), get(), get()) }
	single<ExternalAppRepository> { ExternalAppRepository(get()) }
	single { LocalWatchlistRepository(androidContext()) }
	single<org.jellyfin.androidtv.data.repository.MultiServerRepository> { 
		org.jellyfin.androidtv.data.repository.MultiServerRepositoryImpl(get(), get(), get(), get(), get(defaultDeviceInfo), get()) 
	}
	single { org.jellyfin.androidtv.util.sdk.ApiClientFactory(get(), get(), get(defaultDeviceInfo)) }
	single<org.jellyfin.androidtv.data.repository.ParentalControlsRepository> {
		org.jellyfin.androidtv.data.repository.ParentalControlsRepositoryImpl(androidContext(), get(), get())
	}

	// Jellyseerr - Global preferences (server URL, UI settings)
	single(named("global")) { JellyseerrPreferences(androidContext()) }
	// Jellyseerr - User-specific preferences (auth data, API keys) - scoped per user
	factory(named("user")) { (userId: String) -> JellyseerrPreferences(androidContext(), userId) }
	single<JellyseerrRepository> { JellyseerrRepositoryImpl(androidContext(), get(named("global")), get()) }
	single { MdbListRepository(get<OkHttpFactory>().createClient(get()), get()) }
	single { TmdbRepository(get<OkHttpFactory>().createClient(get()), get(), get()) }

	viewModel { StartupViewModel(get(), get(), get(), get()) }
	viewModel { UserLoginViewModel(get(), get(), get(), get(defaultDeviceInfo)) }
	viewModel { ServerAddViewModel(get()) }
	viewModel { NextUpViewModel(get(), get(), get()) }
	viewModel { StillWatchingViewModel(get(), get(), get(), get()) }
	viewModel { PhotoPlayerViewModel(get()) }
	viewModel { SearchViewModel(get(), get(), get(named("global")), get(), get()) }
	viewModel { DreamViewModel(get(), get(), get(), get(), get()) }
	viewModel { SettingsViewModel() }
	viewModel { SyncPlayViewModel() }
	viewModel { org.jellyfin.androidtv.ui.jellyseerr.JellyseerrViewModel(get()) }
	viewModel { org.jellyfin.androidtv.ui.itemdetail.v2.ItemDetailsViewModel(get(), get()) }
	viewModel { org.jellyfin.androidtv.ui.browsing.v2.LibraryBrowseViewModel(get(), get(), get(), get(), get()) }
	viewModel { org.jellyfin.androidtv.ui.browsing.v2.GenresGridViewModel(get(), get(), get(), get()) }
	viewModel { org.jellyfin.androidtv.ui.browsing.v2.MusicBrowseViewModel(get(), get()) }
	viewModel { org.jellyfin.androidtv.ui.browsing.v2.LiveTvBrowseViewModel(get()) }
	viewModel { org.jellyfin.androidtv.ui.browsing.v2.RecordingsBrowseViewModel(get()) }
	viewModel { org.jellyfin.androidtv.ui.browsing.v2.ScheduleBrowseViewModel(get()) }
	viewModel { org.jellyfin.androidtv.ui.browsing.v2.SeriesRecordingsBrowseViewModel(get()) }
	single { MediaBarSlideshowViewModel(get(), get(), get(), get(), androidContext(), get(), get(), get(), get()) }

	// SyncPlay
	single { SyncPlayManager(androidContext(), get(), get()) }

	single { BackgroundService(get(), get(), get(), get(), get(), get(), get()) }
	single { UpdateCheckerService(get()) }

	single { MarkdownRenderer(get()) }
	single { ItemLauncher() }
	single { KeyProcessor() }
	single { ReportingHelper(get(), get(), get()) }
	single<PlaybackHelper> { SdkPlaybackHelper(get(), get(), get(), get(), get()) }
	single { org.jellyfin.androidtv.ui.playback.ThemeMusicPlayer(androidContext()) }

	factory { (context: Context) -> 
		SearchFragmentDelegate(context, get(), get()) 
	}
}
