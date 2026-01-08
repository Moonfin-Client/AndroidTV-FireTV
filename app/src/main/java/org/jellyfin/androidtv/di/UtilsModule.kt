package org.jellyfin.androidtv.di

import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.util.ImageHelper
import org.koin.dsl.module

val utilsModule = module {
	single { ImageHelper(get(), get(), get<SessionRepository>()) }
}
