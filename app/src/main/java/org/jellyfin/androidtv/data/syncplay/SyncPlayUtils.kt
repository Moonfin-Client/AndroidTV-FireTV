package org.jellyfin.androidtv.data.syncplay

object SyncPlayUtils {
    const val TICKS_PER_MS = 10000L
    
    @JvmStatic
    fun ticksToMs(ticks: Long): Long = ticks / TICKS_PER_MS
    
    @JvmStatic
    fun msToTicks(ms: Long): Long = ms * TICKS_PER_MS
}
