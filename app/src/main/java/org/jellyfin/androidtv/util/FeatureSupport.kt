package org.jellyfin.androidtv.util

import org.jellyfin.androidtv.auth.model.Server
import org.moonfin.server.core.feature.ServerFeature
import org.moonfin.server.core.feature.ServerFeatureSupport
import org.moonfin.server.core.model.ServerType
import org.moonfin.server.emby.feature.EmbyFeatureSupport
import org.moonfin.server.jellyfin.feature.JellyfinFeatureSupport

fun ServerType.featureSupport(): ServerFeatureSupport = when (this) {
    ServerType.JELLYFIN -> JellyfinFeatureSupport
    ServerType.EMBY -> EmbyFeatureSupport
}

fun Server?.supportsFeature(feature: ServerFeature): Boolean =
    this?.serverType?.featureSupport()?.isSupported(feature) ?: true
