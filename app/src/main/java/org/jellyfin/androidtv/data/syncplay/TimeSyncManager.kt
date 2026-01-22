package org.jellyfin.androidtv.data.syncplay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.timeSyncApi
import timber.log.Timber
import java.time.ZoneOffset

/**
 * Manages time synchronization between the client and Jellyfin server.
 * Uses NTP-style algorithm with minimum delay selection for accuracy.
 */
class TimeSyncManager(
    private val api: ApiClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val measurements = mutableListOf<TimeSyncMeasurement>()
    private val maxMeasurements = 8
    
    private var _timeOffset: Long = 0L
    private var _roundTripTime: Long = 0L
    private var _measurementCount: Int = 0
    
    val timeOffset: Long get() = _timeOffset
    val roundTripTime: Long get() = _roundTripTime
    val measurementCount: Int get() = _measurementCount
    val isGreedyMode: Boolean get() = _measurementCount < GREEDY_PING_COUNT
    
    private var syncJob: Job? = null
    private var isSyncing = false
    
    companion object {
        private const val GREEDY_INTERVAL_MS = 1000L
        private const val LOW_PROFILE_INTERVAL_MS = 60000L
        private const val GREEDY_PING_COUNT = 3
        private const val MAX_RTT_MS = 5000L
    }
    
    data class TimeSyncMeasurement(
        val offset: Long,
        val roundTripTime: Long,
        val delay: Long,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Start periodic time synchronization with greedy/low-profile modes
     */
    fun startSync() {
        if (isSyncing) return
        isSyncing = true
        _measurementCount = 0
        
        syncJob = scope.launch {
            while (isSyncing) {
                performSyncMeasurement()
                _measurementCount++
                
                val interval = if (_measurementCount < GREEDY_PING_COUNT) {
                    GREEDY_INTERVAL_MS
                } else {
                    LOW_PROFILE_INTERVAL_MS
                }
                delay(interval)
            }
        }
        Timber.d("TimeSyncManager: Started time synchronization")
    }
    
    /**
     * Stop periodic time synchronization
     */
    fun stopSync() {
        isSyncing = false
        syncJob?.cancel()
        syncJob = null
        measurements.clear()
        _measurementCount = 0
        Timber.d("TimeSyncManager: Stopped time synchronization")
    }
    
    /**
     * Perform a single time sync measurement using NTP-style algorithm.
     * Selects measurement with minimum delay for best accuracy.
     */
    private suspend fun performSyncMeasurement() {
        try {
            val t0 = System.currentTimeMillis()
            
            val response = withContext(Dispatchers.IO) {
                api.timeSyncApi.getUtcTime()
            }
            
            val t3 = System.currentTimeMillis()
            
            val t1 = response.content.requestReceptionTime.toEpochSecond(ZoneOffset.UTC) * 1000 +
                    (response.content.requestReceptionTime.nano / 1_000_000)
            val t2 = response.content.responseTransmissionTime.toEpochSecond(ZoneOffset.UTC) * 1000 +
                    (response.content.responseTransmissionTime.nano / 1_000_000)
            
            val offset = ((t1 - t0) + (t2 - t3)) / 2
            val rtt = (t3 - t0) - (t2 - t1)
            val networkDelay = (t3 - t0) / 2
            
            if (rtt > MAX_RTT_MS || rtt < 0) {
                Timber.w("TimeSyncManager: Discarding measurement with RTT=${rtt}ms")
                return
            }
            
            synchronized(measurements) {
                measurements.add(TimeSyncMeasurement(offset, rtt, networkDelay))
                
                while (measurements.size > maxMeasurements) {
                    measurements.removeAt(0)
                }
                
                if (measurements.isNotEmpty()) {
                    val best = measurements.minByOrNull { it.delay }!!
                    _timeOffset = best.offset
                    _roundTripTime = best.roundTripTime
                }
            }
            
            Timber.v("TimeSyncManager: offset=${_timeOffset}ms, RTT=${_roundTripTime}ms, measurements=${measurements.size}")
        } catch (e: Exception) {
            Timber.w(e, "TimeSyncManager: Failed to sync time")
        }
    }
    
    /**
     * Force an immediate sync measurement
     */
    suspend fun syncNow() {
        performSyncMeasurement()
    }

    fun serverTimeToLocal(serverTimeMs: Long): Long = serverTimeMs - _timeOffset

    fun localTimeToServer(localTimeMs: Long): Long = localTimeMs + _timeOffset

    fun getServerTimeNow(): Long = System.currentTimeMillis() + _timeOffset

    fun getStats(): Map<String, Any> = mapOf(
        "offset" to _timeOffset,
        "rtt" to _roundTripTime,
        "measurements" to measurements.size,
        "greedy" to isGreedyMode,
    )
}
