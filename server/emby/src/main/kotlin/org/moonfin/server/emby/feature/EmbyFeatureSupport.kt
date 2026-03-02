package org.moonfin.server.emby.feature

import org.moonfin.server.core.feature.ServerFeature
import org.moonfin.server.core.feature.ServerFeatureSupport

object EmbyFeatureSupport : ServerFeatureSupport {
    override val supportedFeatures: Set<ServerFeature> = setOf(
        ServerFeature.WATCH_PARTY,
        ServerFeature.BIF_TRICKPLAY,
        ServerFeature.EMBY_CONNECT,
        ServerFeature.JELLYSEERR,
    )
}
