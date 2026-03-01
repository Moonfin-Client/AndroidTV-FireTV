@file:JvmName("ModelUtils")

package org.jellyfin.androidtv.util.sdk

import org.jellyfin.androidtv.auth.model.PublicUser
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.moonfin.server.core.model.ServerType

fun ServerDiscoveryInfo.toServer(serverType: ServerType = ServerType.JELLYFIN): Server = Server(
	id = id.toUUID(),
	name = name,
	address = address,
	serverType = serverType,
)

fun UserDto.toPublicUser(): PublicUser? {
	return PublicUser(
		id = id,
		name = name ?: return null,
		serverId = serverId?.toUUIDOrNull() ?: return null,
		accessToken = null,
		imageTag = primaryImage?.tag
	)
}
