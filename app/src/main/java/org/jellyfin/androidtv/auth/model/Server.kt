package org.jellyfin.androidtv.auth.model

import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.sdk.model.ServerVersion
import org.moonfin.server.core.model.ServerType
import java.time.Instant
import java.util.UUID

data class Server(
	val id: UUID,
	val name: String,
	val address: String,
	val version: String? = null,
	val loginDisclaimer: String? = null,
	val splashscreenEnabled: Boolean = false,
	val setupCompleted: Boolean = true,
	val dateLastAccessed: Instant = Instant.MIN,
	val serverType: ServerType = ServerType.JELLYFIN,
) {
	val serverVersion = version?.let(ServerVersion::fromString)

	val versionSupported: Boolean
		get() {
			val sv = serverVersion ?: return false
			return when (serverType) {
				ServerType.JELLYFIN -> sv >= ServerRepository.minimumJellyfinVersion
				ServerType.EMBY -> sv >= ServerRepository.minimumEmbyVersion
			}
		}

	operator fun compareTo(other: ServerVersion): Int = serverVersion?.compareTo(other) ?: -1

	override fun equals(other: Any?) = other is Server
		&& id == other.id
		&& address == other.address

	override fun hashCode(): Int {
		var result = id.hashCode()
		result = 31 * result + address.hashCode()
		return result
	}
}
