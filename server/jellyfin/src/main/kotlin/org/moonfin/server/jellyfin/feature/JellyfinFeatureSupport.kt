package org.moonfin.server.jellyfin.feature

import org.moonfin.server.core.feature.ServerFeature
import org.moonfin.server.core.feature.ServerFeatureSupport

class JellyfinFeatureSupport : ServerFeatureSupport {
    override val supportedFeatures: Set<ServerFeature> = setOf(
        ServerFeature.QUICK_CONNECT,
        ServerFeature.SYNC_PLAY,
        ServerFeature.MEDIA_SEGMENTS,
        ServerFeature.TRICKPLAY,
        ServerFeature.LYRICS,
        ServerFeature.CLIENT_LOG,
    )
}
