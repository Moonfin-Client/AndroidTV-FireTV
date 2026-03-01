package org.jellyfin.androidtv.di

import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.auth.repository.AuthenticationRepository
import org.jellyfin.androidtv.auth.repository.AuthenticationRepositoryImpl
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerRepositoryImpl
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepositoryImpl
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.SessionRepositoryImpl
import org.jellyfin.androidtv.auth.store.AuthenticationPreferences
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.sdk.model.DeviceInfo
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.moonfin.server.emby.EmbyApiClient

val authModule = module {
	single { AuthenticationStore(get()) }
	single { AuthenticationPreferences(get()) }

	single {
		val deviceInfo = get<DeviceInfo>(defaultDeviceInfo)
		EmbyApiClient(
			appVersion = BuildConfig.VERSION_NAME,
			clientName = "Moonfin Android TV",
			deviceId = deviceInfo.id,
			deviceName = deviceInfo.name,
		)
	}

	single<AuthenticationRepository> {
		AuthenticationRepositoryImpl(get(), get(), get(), get(), get(), get(defaultDeviceInfo), get(), get(named("global")), get())
	}
	single<ServerRepository> { ServerRepositoryImpl(get(), get()) }
	single<ServerUserRepository> { ServerUserRepositoryImpl(get(), get()) }
	single<SessionRepository> {
		SessionRepositoryImpl(get(), get(), get(), get(), get(defaultDeviceInfo), get(), get(), get(), get())
	}

	factory {
		val serverRepository = get<ServerRepository>()
		serverRepository.currentServer.value?.serverVersion ?: ServerRepository.minimumServerVersion
	}
}
