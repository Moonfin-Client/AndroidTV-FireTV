package org.moonfin.server.core.model

import java.time.Instant

data class LiveTvSeriesTimerInfo(
    val id: String,
    val name: String?,
    val channelId: String?,
    val channelName: String?,
    val recordAnyChannel: Boolean?,
    val recordAnyTime: Boolean?,
    val recordNewOnly: Boolean?,
    val startDate: Instant?,
    val endDate: Instant?,
)
