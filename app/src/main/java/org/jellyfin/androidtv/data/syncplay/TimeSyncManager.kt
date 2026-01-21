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
import kotlin.math.abs

/**
 * Manages time synchronization between the client and Jellyfin server.
 * 
 * Uses the NTP-like algorithm to calculate clock offset:
 * 1. Record local time before request (t0)
 * 2. Server records request reception time (t1)
 * 3. Server records response transmission time (t2)
 * 4. Record local time after response (t3)
 * 
 * Offset = ((t1 - t0) + (t2 - t3)) / 2
 * Round-trip time = (t3 - t0) - (t2 - t1)
 */
class TimeSyncManager(
    private val api: ApiClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val measurements = mutableListOf<TimeSyncMeasurement>()
    private val maxMeasurements = 8
    
    private var _timeOffset: Long = 0L
    private var _roundTripTime: Long = 0L
    
    val timeOffset: Long get() = _timeOffset
    val roundTripTime: Long get() = _roundTripTime
    
    private var syncJob: Job? = null
    private var isSyncing = false
    
    companion object {
        private const val SYNC_INTERVAL_MS = 5000L
        private const val INITIAL_SYNC_COUNT = 3
        private const val MAX_RTT_MS = 5000L
    }
    
    data class TimeSyncMeasurement(
        val offset: Long,
        val roundTripTime: Long,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Start periodic time synchronization
     */
    fun startSync() {
        if (isSyncing) return
        isSyncing = true
        
        syncJob = scope.launch {
            // Do initial sync measurements
            repeat(INITIAL_SYNC_COUNT) {
                performSyncMeasurement()
                delay(500) // Small delay between initial measurements
            }
            
            // Then continue with periodic sync
            while (isSyncing) {
                delay(SYNC_INTERVAL_MS)
                performSyncMeasurement()
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
        Timber.d("TimeSyncManager: Stopped time synchronization")
    }
    
    /**
     * Perform a single time sync measurement
     */
    private suspend fun performSyncMeasurement() {
        try {
            // Record local time before request
            val t0 = System.currentTimeMillis()
            
            // Make the request
            val response = withContext(Dispatchers.IO) {
                api.timeSyncApi.getUtcTime()
            }
            
            // Record local time after response
            val t3 = System.currentTimeMillis()
            
            // Extract server times
            val t1 = response.content.requestReceptionTime.toEpochSecond(ZoneOffset.UTC) * 1000 +
                    (response.content.requestReceptionTime.nano / 1_000_000)
            val t2 = response.content.responseTransmissionTime.toEpochSecond(ZoneOffset.UTC) * 1000 +
                    (response.content.responseTransmissionTime.nano / 1_000_000)
            
            // Calculate offset and RTT
            // Offset = ((t1 - t0) + (t2 - t3)) / 2
            // This gives us: local_time + offset = server_time
            val offset = ((t1 - t0) + (t2 - t3)) / 2
            val rtt = (t3 - t0) - (t2 - t1)
            
            // Discard measurement if RTT is too high (network issues)
            if (rtt > MAX_RTT_MS || rtt < 0) {
                Timber.w("TimeSyncManager: Discarding measurement with RTT=${rtt}ms")
                return
            }
            
            // Add measurement
            synchronized(measurements) {
                measurements.add(TimeSyncMeasurement(offset, rtt))
                
                // Keep only the most recent measurements
                while (measurements.size > maxMeasurements) {
                    measurements.removeAt(0)
                }
                
                // Calculate weighted average (newer measurements have more weight)
                // Also weight by inverse RTT (faster responses are more accurate)
                if (measurements.isNotEmpty()) {
                    var totalWeight = 0.0
                    var weightedOffset = 0.0
                    var weightedRtt = 0.0
                    
                    measurements.forEachIndexed { index, measurement ->
                        // Weight increases with index (newer = higher weight)
                        // Also weight inversely by RTT
                        val ageWeight = (index + 1).toDouble()
                        val rttWeight = 1.0 / (measurement.roundTripTime.coerceAtLeast(1))
                        val weight = ageWeight * rttWeight
                        
                        weightedOffset += measurement.offset * weight
                        weightedRtt += measurement.roundTripTime * weight
                        totalWeight += weight
                    }
                    
                    _timeOffset = (weightedOffset / totalWeight).toLong()
                    _roundTripTime = (weightedRtt / totalWeight).toLong()
                }
            }
            
            Timber.v("TimeSyncManager: offset=${_timeOffset}ms, RTT=${_roundTripTime}ms")
        } catch (e: Exception) {
            Timber.w(e, "TimeSyncManager: Failed to sync time")
        }
    }
    
    /**
     * Force an immediate sync measurement (useful before critical operations)
     */
    suspend fun syncNow() {
        performSyncMeasurement()
    }
    
    /**
     * Convert server time to local time
     */
    fun serverTimeToLocal(serverTimeMs: Long): Long = serverTimeMs - _timeOffset
    
    /**
     * Convert local time to server time
     */
    fun localTimeToServer(localTimeMs: Long): Long = localTimeMs + _timeOffset
    
    /**
     * Get current server time estimate
     */
    fun getServerTimeNow(): Long = System.currentTimeMillis() + _timeOffset
}
