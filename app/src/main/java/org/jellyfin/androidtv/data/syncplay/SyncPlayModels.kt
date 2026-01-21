package org.jellyfin.androidtv.data.syncplay

import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.GroupStateType

data class SyncPlayState(
    val enabled: Boolean = false,
    val groupInfo: GroupInfoDto? = null,
    val groupState: GroupStateType = GroupStateType.IDLE,
)
