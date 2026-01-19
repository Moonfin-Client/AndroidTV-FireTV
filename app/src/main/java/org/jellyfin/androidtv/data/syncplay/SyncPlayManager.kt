package org.jellyfin.androidtv.data.syncplay

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.syncPlayApi
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.GroupStateType
import org.jellyfin.sdk.model.api.GroupUpdate
import org.jellyfin.sdk.model.api.JoinGroupRequestDto
import org.jellyfin.sdk.model.api.NewGroupRequestDto
import org.jellyfin.sdk.model.api.PingRequestDto
import org.jellyfin.sdk.model.api.PlayRequestDto
import org.jellyfin.sdk.model.api.SendCommand
import org.jellyfin.sdk.model.api.SendCommandType
import timber.log.Timber
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.abs

/**
 * SyncPlay Manager handles all SyncPlay operations including:
 * - Creating/joining/leaving groups
 * - Playback synchronization
 */
class SyncPlayManager(
    private val context: Context,
    private val api: ApiClient,
    private val userPreferences: UserPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(SyncPlayState())
    val state: StateFlow<SyncPlayState> = _state.asStateFlow()

    private val _availableGroups = MutableStateFlow<List<GroupInfoDto>>(emptyList())
    val availableGroups: StateFlow<List<GroupInfoDto>> = _availableGroups.asStateFlow()

    private val timeSyncManager = TimeSyncManager(api)
    private var currentCommand: SendCommand? = null
    private var isSpeedCorrecting = false
    private var speedCorrectionJob: Job? = null
    private var driftCheckJob: Job? = null
    private var pingJob: Job? = null

    private val enableSyncCorrection get() = userPreferences[UserPreferences.syncPlayEnableSyncCorrection]
    private val useSpeedToSync get() = userPreferences[UserPreferences.syncPlayUseSpeedToSync]
    private val useSkipToSync get() = userPreferences[UserPreferences.syncPlayUseSkipToSync]
    private val minDelaySpeedToSync get() = userPreferences[UserPreferences.syncPlayMinDelaySpeedToSync]
    private val maxDelaySpeedToSync get() = userPreferences[UserPreferences.syncPlayMaxDelaySpeedToSync]
    private val speedToSyncDuration get() = userPreferences[UserPreferences.syncPlaySpeedToSyncDuration]
    private val minDelaySkipToSync get() = userPreferences[UserPreferences.syncPlayMinDelaySkipToSync]
    private val extraTimeOffset get() = userPreferences[UserPreferences.syncPlayExtraTimeOffset]
    
    var playbackCallback: SyncPlayPlaybackCallback? = null
    var queueLaunchCallback: ((itemIds: List<UUID>, startIndex: Int, startPositionTicks: Long) -> Unit)? = null
    
    interface SyncPlayPlaybackCallback {
        fun onPlay(positionMs: Long)
        fun onPause(positionMs: Long)
        fun onSeek(positionMs: Long)
        fun onStop()
        fun onLoadQueue(itemIds: List<UUID>, startIndex: Int, startPositionTicks: Long)
        fun getCurrentPositionMs(): Long
        fun isPlaying(): Boolean
        fun setPlaybackSpeed(speed: Float)
        fun getPlaybackSpeed(): Float
    }
    
    companion object {
        private const val DRIFT_CHECK_INTERVAL_MS = 1000L
        private const val PING_INTERVAL_MS = 5000L
        private const val SPEED_FAST = 1.05f
        private const val SPEED_SLOW = 0.95f
        private const val SPEED_NORMAL = 1.0f
    }

    private suspend fun <T> executeApiCall(operation: String, block: suspend () -> T): Result<T> {
        return try {
            Result.success(withContext(Dispatchers.IO) { block() })
        } catch (e: Exception) {
            Timber.e(e, "SyncPlay: Failed to $operation")
            Result.failure(e)
        }
    }

    suspend fun refreshGroups() {
        val result = executeApiCall("get groups") {
            api.syncPlayApi.syncPlayGetGroups().content
        }
        _availableGroups.value = result.getOrElse { 
            if (it is org.jellyfin.sdk.api.client.exception.InvalidStatusException && 
                it.message?.contains("403") == true) {
                Timber.d("SyncPlay: Permission denied for getting groups")
            }
            emptyList()
        }
    }

    /**
     * Refresh current group info to get updated participant list
     */
    private suspend fun refreshGroupInfo() {
        val currentGroupId = _state.value.groupInfo?.groupId ?: return
        try {
            val response = withContext(Dispatchers.IO) {
                api.syncPlayApi.syncPlayGetGroup(currentGroupId)
            }
            val updatedGroupInfo = response.content
            _state.value = _state.value.copy(groupInfo = updatedGroupInfo)
        } catch (e: org.jellyfin.sdk.api.client.exception.InvalidStatusException) {
            // 403 or 404 likely means the group no longer exists
            if (e.message?.contains("403") == true || e.message?.contains("404") == true) {
                Timber.w("SyncPlay: Group no longer exists on server, clearing local state")
                _state.value = SyncPlayState()
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "SyncPlay group was disbanded", Toast.LENGTH_SHORT).show()
                }
            } else {
                Timber.e(e, "SyncPlay: Failed to refresh group info")
            }
        } catch (e: Exception) {
            Timber.e(e, "SyncPlay: Failed to refresh group info")
        }
    }

    /**
     * Create a new SyncPlay group
     */
    suspend fun createGroup(groupName: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                api.syncPlayApi.syncPlayCreateGroup(
                    NewGroupRequestDto(groupName = groupName)
                )
            }
            Result.success(Unit)
        } catch (e: org.jellyfin.sdk.api.client.exception.InvalidStatusException) {
            if (e.message?.contains("403") == true) {
                Timber.e("SyncPlay: Permission denied - user does not have SyncPlay access")
            } else {
                Timber.e(e, "SyncPlay: Failed to create group")
            }
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "SyncPlay: Failed to create group")
            Result.failure(e)
        }
    }

    /**
     * Join an existing SyncPlay group
     */
    suspend fun joinGroup(groupId: UUID): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                api.syncPlayApi.syncPlayJoinGroup(
                    JoinGroupRequestDto(groupId = groupId)
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "SyncPlay: Failed to join group")
            Result.failure(e)
        }
    }

    /**
     * Leave the current SyncPlay group
     */
    suspend fun leaveGroup(): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                api.syncPlayApi.syncPlayLeaveGroup()
            }
            _state.value = SyncPlayState()
            Result.success(Unit)
        } catch (e: org.jellyfin.sdk.api.client.exception.InvalidStatusException) {
            // If server returns 403, it likely means the group no longer exists
            // (e.g., owner left and group was dissolved). Clear local state anyway.
            if (e.message?.contains("403") == true) {
                Timber.w("SyncPlay: Group no longer exists on server (403), clearing local state")
                _state.value = SyncPlayState()
                Result.success(Unit)
            } else {
                Timber.e(e, "SyncPlay: Failed to leave group")
                Result.failure(e)
            }
        } catch (e: Exception) {
            Timber.e(e, "SyncPlay: Failed to leave group")
            Result.failure(e)
        }
    }

    suspend fun requestPlay() {
        executeApiCall("request play") {
            api.syncPlayApi.syncPlayUnpause()
        }
    }

    suspend fun requestPause() {
        executeApiCall("request pause") {
            api.syncPlayApi.syncPlayPause()
        }
    }

    suspend fun requestSeek(positionTicks: Long) {
        executeApiCall("request seek") {
            api.syncPlayApi.syncPlaySeek(
                org.jellyfin.sdk.model.api.SeekRequestDto(positionTicks = positionTicks)
            )
        }
    }

    suspend fun requestStop() {
        executeApiCall("request stop") {
            api.syncPlayApi.syncPlayStop()
        }
    }

    suspend fun setPlayQueue(itemIds: List<UUID>, startIndex: Int = 0, startPositionTicks: Long = 0) {
        executeApiCall("set play queue") {
            api.syncPlayApi.syncPlaySetNewQueue(
                PlayRequestDto(
                    playingQueue = itemIds,
                    playingItemPosition = startIndex,
                    startPositionTicks = startPositionTicks,
                )
            )
        }
    }

    /**
     * Convert server time to local time
     */
    fun serverTimeToLocal(serverTime: Long): Long = timeSyncManager.serverTimeToLocal(serverTime)

    /**
     * Convert local time to server time
     */
    fun localTimeToServer(localTime: Long): Long = timeSyncManager.localTimeToServer(localTime)

    /**
     * Get stats for debug display
     */
    fun getStats(): Map<String, String> = mapOf(
        "Time Offset" to "${timeSyncManager.timeOffset}ms",
        "RTT" to "${timeSyncManager.roundTripTime}ms",
        "Group State" to _state.value.groupState.name,
        "In Group" to "${_state.value.enabled}",
        "Sync Correction" to if (enableSyncCorrection) "ON" else "OFF",
        "Speed Correcting" to "$isSpeedCorrecting",
    )
    
    /**
     * Start time synchronization and drift checking when joining a group
     */
    private fun startSyncServices() {
        timeSyncManager.startSync()
        startDriftChecking()
        startPingUpdates()
    }
    
    /**
     * Stop time synchronization and drift checking when leaving a group
     */
    private fun stopSyncServices() {
        timeSyncManager.stopSync()
        stopDriftChecking()
        stopPingUpdates()
        stopSpeedCorrection()
        currentCommand = null
    }
    
    /**
     * Start periodic drift checking
     */
    private fun startDriftChecking() {
        driftCheckJob?.cancel()
        driftCheckJob = scope.launch {
            while (isActive) {
                delay(DRIFT_CHECK_INTERVAL_MS)
                checkAndCorrectDrift()
            }
        }
    }
    
    /**
     * Stop periodic drift checking
     */
    private fun stopDriftChecking() {
        driftCheckJob?.cancel()
        driftCheckJob = null
    }
    
    /**
     * Start periodic ping updates to server
     */
    private fun startPingUpdates() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                sendPing()
            }
        }
    }
    
    /**
     * Stop periodic ping updates
     */
    private fun stopPingUpdates() {
        pingJob?.cancel()
        pingJob = null
    }
    
    /**
     * Send ping to server with current RTT
     */
    private suspend fun sendPing() {
        try {
            val ping = timeSyncManager.roundTripTime
            withContext(Dispatchers.IO) {
                api.syncPlayApi.syncPlayPing(PingRequestDto(ping = ping))
            }
        } catch (e: Exception) {
            // Ping failures are not critical, just log at debug level
            Timber.d(e, "SyncPlay: Failed to send ping")
        }
    }
    
    /**
     * Check current playback drift and apply correction if needed
     */
    private fun checkAndCorrectDrift() {
        if (!enableSyncCorrection) return
        if (currentCommand == null) return
        if (_state.value.groupState != GroupStateType.PLAYING) return
        
        val callback = playbackCallback ?: return
        if (!callback.isPlaying()) return
        
        // Calculate expected position based on command
        val command = currentCommand ?: return
        val commandWhenMs = command.`when`.toEpochSecond(ZoneOffset.UTC) * 1000 +
                (command.`when`.nano / 1_000_000)
        val commandPositionMs = SyncPlayUtils.ticksToMs(command.positionTicks ?: 0)
        
        // Time elapsed since command was issued (in server time)
        val serverNow = timeSyncManager.getServerTimeNow()
        val elapsedMs = serverNow - commandWhenMs
        
        // Expected position = command position + elapsed time + user offset
        val expectedPositionMs = commandPositionMs + elapsedMs + extraTimeOffset.toLong()
        
        // Current actual position
        val currentPositionMs = callback.getCurrentPositionMs()
        
        // Calculate drift (positive = we're ahead, negative = we're behind)
        val driftMs = currentPositionMs - expectedPositionMs
        val absDriftMs = abs(driftMs)
        
        Timber.v("SyncPlay drift: ${driftMs}ms (current=$currentPositionMs, expected=$expectedPositionMs)")
        
        // Apply correction based on drift magnitude
        when {
            // Skip correction needed (large drift)
            useSkipToSync && absDriftMs >= minDelaySkipToSync -> {
                Timber.d("SyncPlay: SkipToSync - drift=${driftMs}ms, seeking to $expectedPositionMs")
                stopSpeedCorrection()
                scope.launch(Dispatchers.Main) {
                    callback.onSeek(expectedPositionMs)
                }
            }
            // Speed correction needed (medium drift)
            useSpeedToSync && absDriftMs >= minDelaySpeedToSync && absDriftMs < maxDelaySpeedToSync -> {
                if (!isSpeedCorrecting) {
                    val targetSpeed = if (driftMs > 0) SPEED_SLOW else SPEED_FAST
                    Timber.d("SyncPlay: SpeedToSync - drift=${driftMs}ms, speed=$targetSpeed")
                    startSpeedCorrection(callback, targetSpeed)
                }
            }
            // No correction needed or already correcting
            absDriftMs < minDelaySpeedToSync && isSpeedCorrecting -> {
                // Drift is within acceptable range, stop correction
                Timber.d("SyncPlay: Drift within range, stopping speed correction")
                stopSpeedCorrection()
            }
        }
    }
    
    /**
     * Start speed-based sync correction
     */
    private fun startSpeedCorrection(callback: SyncPlayPlaybackCallback, targetSpeed: Float) {
        if (isSpeedCorrecting) return
        
        // Cancel any existing job first to prevent race conditions
        speedCorrectionJob?.cancel()
        speedCorrectionJob = null
        
        isSpeedCorrecting = true
        
        scope.launch(Dispatchers.Main) {
            callback.setPlaybackSpeed(targetSpeed)
        }
        
        speedCorrectionJob = scope.launch {
            delay(speedToSyncDuration.toLong())
            stopSpeedCorrection()
        }
    }
    
    /**
     * Stop speed-based sync correction and restore normal speed
     */
    private fun stopSpeedCorrection() {
        val wasSpeedCorrecting = isSpeedCorrecting
        isSpeedCorrecting = false
        
        speedCorrectionJob?.cancel()
        speedCorrectionJob = null
        
        // Only restore normal speed if we were actually correcting
        if (wasSpeedCorrecting) {
            scope.launch(Dispatchers.Main) {
                playbackCallback?.setPlaybackSpeed(SPEED_NORMAL)
            }
        }
    }

    /**
     * Handle incoming SyncPlay command from server
     */
    fun onPlaybackCommand(command: SendCommand) {
        currentCommand = command
        stopSpeedCorrection()
        
        when (command.command) {
            SendCommandType.UNPAUSE -> {
                val targetTimeMs = command.`when`.toEpochSecond(ZoneOffset.UTC) * 1000 +
                        (command.`when`.nano / 1_000_000)
                val localTargetTime = serverTimeToLocal(targetTimeMs)
                val adjustedTargetTime = localTargetTime + extraTimeOffset.toLong()
                schedulePlay(adjustedTargetTime, command.positionTicks ?: 0)
            }
            SendCommandType.PAUSE -> {
                currentCommand = null
                schedulePause(command.positionTicks ?: 0)
            }
            SendCommandType.SEEK -> {
                scheduleSeek(command.positionTicks ?: 0)
            }
            SendCommandType.STOP -> {
                currentCommand = null
                scheduleStop()
            }
        }
    }

    /**
     * Handle incoming SyncPlay group update from server
     */
    fun onGroupUpdate(update: GroupUpdate) {
        when (update) {
            is org.jellyfin.sdk.model.api.SyncPlayGroupJoinedUpdate -> {
                val groupInfo = update.data
                _state.value = _state.value.copy(
                    enabled = true,
                    groupInfo = groupInfo,
                )
                // Start sync services when joining a group
                startSyncServices()
            }
            is org.jellyfin.sdk.model.api.SyncPlayUserJoinedUpdate -> {
                val userName = update.data
                // Show toast notification
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "$userName joined the group", Toast.LENGTH_SHORT).show()
                }
                // Refresh group info to get updated participant list
                scope.launch(Dispatchers.IO) {
                    refreshGroupInfo()
                }
            }
            is org.jellyfin.sdk.model.api.SyncPlayUserLeftUpdate -> {
                val userName = update.data
                // Show toast notification
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "$userName left the group", Toast.LENGTH_SHORT).show()
                }
                // Refresh group info to get updated participant list
                scope.launch(Dispatchers.IO) {
                    refreshGroupInfo()
                }
            }
            is org.jellyfin.sdk.model.api.SyncPlayGroupLeftUpdate -> {
                _state.value = SyncPlayState()
                // Stop sync services when leaving a group
                stopSyncServices()
            }
            is org.jellyfin.sdk.model.api.SyncPlayStateUpdate -> {
                val stateData = update.data
                _state.value = _state.value.copy(groupState = stateData.state)
            }
            is org.jellyfin.sdk.model.api.SyncPlayPlayQueueUpdate -> {
                val queue = update.data
                
                // Extract item IDs from the playlist
                val itemIds = queue.playlist.mapNotNull { it.itemId }
                if (itemIds.isNotEmpty()) {
                    val startIndex = queue.playingItemIndex
                    val startPosition = queue.startPositionTicks
                    
                    // Notify callback to load the queue
                    scope.launch(Dispatchers.Main) {
                        if (playbackCallback != null) {
                            playbackCallback?.onLoadQueue(itemIds, startIndex, startPosition)
                        } else {
                            // No active playback - use launch callback to start new playback
                            queueLaunchCallback?.invoke(itemIds, startIndex, startPosition)
                        }
                    }
                }
            }
            else -> {
                // Unhandled group update type
            }
        }
    }

    // Scheduling methods - these should coordinate with the PlaybackController
    private var scheduledPlayJob: Job? = null

    private fun schedulePlay(targetLocalTime: Long, positionTicks: Long) {
        scheduledPlayJob?.cancel()
        scheduledPlayJob = scope.launch(Dispatchers.Main) {
            val delayMs = targetLocalTime - System.currentTimeMillis()
            if (delayMs > 0) {
                delay(delayMs)
            }
            playbackCallback?.onPlay(SyncPlayUtils.ticksToMs(positionTicks))
        }
    }

    private fun schedulePause(positionTicks: Long) {
        scheduledPlayJob?.cancel()
        scope.launch(Dispatchers.Main) {
            playbackCallback?.onPause(SyncPlayUtils.ticksToMs(positionTicks))
        }
    }

    private fun scheduleSeek(positionTicks: Long) {
        scheduledPlayJob?.cancel()
        scope.launch(Dispatchers.Main) {
            playbackCallback?.onSeek(SyncPlayUtils.ticksToMs(positionTicks))
        }
    }

    private fun scheduleStop() {
        scheduledPlayJob?.cancel()
        scope.launch(Dispatchers.Main) {
            playbackCallback?.onStop()
        }
    }
}
