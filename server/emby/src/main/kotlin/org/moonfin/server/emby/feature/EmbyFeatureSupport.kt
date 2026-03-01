package org.moonfin.server.emby.feature

import org.moonfin.server.core.feature.ServerFeature
import org.moonfin.server.core.feature.ServerFeatureSupport

class EmbyFeatureSupport : ServerFeatureSupport {
    override val supportedFeatures: Set<ServerFeature> = setOf(
        ServerFeature.WATCH_PARTY,
        ServerFeature.TRICKPLAY,
    )
}
