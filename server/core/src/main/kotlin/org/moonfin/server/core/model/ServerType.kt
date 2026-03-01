package org.moonfin.server.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ServerType {
	JELLYFIN,
	EMBY;

	companion object {
		fun fromProductName(productName: String?): ServerType = when {
			productName?.contains("Jellyfin", ignoreCase = true) == true -> JELLYFIN
			productName?.contains("Emby", ignoreCase = true) == true -> EMBY
			else -> JELLYFIN
		}
	}
}
