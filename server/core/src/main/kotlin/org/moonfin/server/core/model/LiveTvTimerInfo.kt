package org.moonfin.server.core.model

import java.time.Instant

data class LiveTvTimerInfo(
    val id: String,
    val name: String?,
    val channelId: String?,
    val channelName: String?,
    val programId: String?,
    val seriesTimerId: String?,
    val startDate: Instant?,
    val endDate: Instant?,
    val prePaddingSeconds: Int?,
    val postPaddingSeconds: Int?,
    val status: String?,
)
