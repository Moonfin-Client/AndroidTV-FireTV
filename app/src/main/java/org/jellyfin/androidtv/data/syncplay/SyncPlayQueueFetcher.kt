package org.jellyfin.androidtv.data.syncplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

object SyncPlayQueueFetcher {
    @JvmStatic
    fun fetchQueueAsync(
        itemIds: List<UUID>,
        startIndex: Int,
        startPositionTicks: Long,
        callback: SyncPlayQueueHelper.QueueCallback
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            val result = SyncPlayQueueHelper.fetchQueue(itemIds, startIndex, startPositionTicks)
            withContext(Dispatchers.Main) {
                if (result != null) {
                    callback.onQueueReady(result.items, result.startIndex, result.startPositionMs)
                } else {
                    callback.onError()
                }
            }
        }
    }
}
