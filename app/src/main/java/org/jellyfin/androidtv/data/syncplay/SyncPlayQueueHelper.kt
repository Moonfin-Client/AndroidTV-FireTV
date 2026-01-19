package org.jellyfin.androidtv.data.syncplay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncherHelper
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber
import java.util.UUID

object SyncPlayQueueHelper {
    
    data class QueueResult(
        val items: List<BaseItemDto>,
        val startIndex: Int,
        val startPositionMs: Long,
    )
    
    interface QueueCallback {
        fun onQueueReady(items: List<BaseItemDto>, startIndex: Int, startPositionMs: Long)
        fun onError()
    }
    
    /**
     * Fetches items for a SyncPlay queue concurrently.
     * 
     * @param itemIds List of item UUIDs to fetch
     * @param startIndex The index to start playback from
     * @param startPositionTicks The position in ticks to start playback from
     * @return QueueResult with fetched items, or null if no items could be fetched
     */
    suspend fun fetchQueue(
        itemIds: List<UUID>,
        startIndex: Int,
        startPositionTicks: Long,
    ): QueueResult? = withContext(Dispatchers.IO) {
        if (itemIds.isEmpty()) {
            Timber.w("SyncPlayQueueHelper: Empty itemIds list")
            return@withContext null
        }
        
        // Fetch all items concurrently for better performance
        val deferredItems = itemIds.map { itemId ->
            async {
                try {
                    ItemLauncherHelper.getItemBlocking(itemId)
                } catch (e: Exception) {
                    Timber.e(e, "SyncPlayQueueHelper: Failed to fetch item $itemId")
                    null
                }
            }
        }
        
        val items = deferredItems.awaitAll().filterNotNull()
        
        if (items.isEmpty()) {
            Timber.e("SyncPlayQueueHelper: Failed to fetch any items for queue")
            return@withContext null
        }
        
        if (items.size < itemIds.size) {
            Timber.w("SyncPlayQueueHelper: Only fetched ${items.size}/${itemIds.size} items")
        }
        
        QueueResult(
            items = items,
            startIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
            startPositionMs = SyncPlayUtils.ticksToMs(startPositionTicks),
        )
    }
    
    /**
     * Java-friendly version that uses a callback.
     * Launches a coroutine tied to the provided lifecycle.
     */
    @JvmStatic
    fun fetchQueue(
        lifecycleOwner: LifecycleOwner,
        itemIds: List<UUID>,
        startIndex: Int,
        startPositionTicks: Long,
        callback: QueueCallback,
    ) {
        lifecycleOwner.lifecycleScope.launch {
            val result = fetchQueue(itemIds, startIndex, startPositionTicks)
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
