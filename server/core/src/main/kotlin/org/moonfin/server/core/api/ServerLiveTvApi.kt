package org.moonfin.server.core.api

import org.moonfin.server.core.model.ItemsResult
import org.moonfin.server.core.model.LiveTvGuideInfo
import org.moonfin.server.core.model.LiveTvSeriesTimerInfo
import org.moonfin.server.core.model.LiveTvTimerInfo

interface ServerLiveTvApi {
    suspend fun getChannels(userId: String? = null, startIndex: Int? = null, limit: Int? = null): ItemsResult
    suspend fun getPrograms(channelIds: List<String>? = null, userId: String? = null, startIndex: Int? = null, limit: Int? = null): ItemsResult
    suspend fun getRecordings(channelId: String? = null, seriesTimerId: String? = null, startIndex: Int? = null, limit: Int? = null): ItemsResult
    suspend fun getTimers(channelId: String? = null, seriesTimerId: String? = null): List<LiveTvTimerInfo>
    suspend fun getSeriesTimers(sortBy: String? = null, startIndex: Int? = null, limit: Int? = null): List<LiveTvSeriesTimerInfo>
    suspend fun createTimer(timer: LiveTvTimerInfo)
    suspend fun cancelTimer(timerId: String)
    suspend fun getRecommendedPrograms(userId: String? = null, limit: Int? = null): ItemsResult
    suspend fun getGuideInfo(): LiveTvGuideInfo
}
