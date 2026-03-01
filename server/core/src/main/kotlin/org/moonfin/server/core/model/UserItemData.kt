package org.moonfin.server.core.model

import java.time.Instant

data class UserItemData(
    val playedPercentage: Double?,
    val unplayedItemCount: Int?,
    val playbackPositionTicks: Long,
    val playCount: Int,
    val isFavorite: Boolean,
    val lastPlayedDate: Instant?,
    val played: Boolean,
    val key: String,
    val itemId: String?,
)
