package org.jellyfin.playback.jellyfin

import androidx.lifecycle.Lifecycle
import org.jellyfin.playback.core.plugin.playbackPlugin
import org.jellyfin.playback.jellyfin.mediastream.JellyfinMediaStreamResolver
import org.jellyfin.playback.jellyfin.playsession.PlaySessionService
import org.jellyfin.playback.jellyfin.playsession.PlaySessionSocketService
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.DeviceProfile
import java.util.UUID

fun jellyfinPlugin(
	api: ApiClient,
	deviceProfileBuilder: () -> DeviceProfile,
	lifecycle: Lifecycle? = null,
	apiClientResolver: ((UUID?) -> ApiClient?)? = null,
) = playbackPlugin {
	provide(JellyfinMediaStreamResolver(api, deviceProfileBuilder))

	val playSessionService = PlaySessionService(api, apiClientResolver)
	provide(playSessionService)
	provide(PlaySessionSocketService(api, playSessionService, lifecycle))

	provide(LyricsPlayerService(api))
}
