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
import org.jellyfin.sdk.model.api.BufferRequestDto
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.GroupStateType
import org.jellyfin.sdk.model.api.GroupUpdate
import org.jellyfin.sdk.model.api.JoinGroupRequestDto
import org.jellyfin.sdk.model.api.NewGroupRequestDto
import org.jellyfin.sdk.model.api.PingRequestDto
import org.jellyfin.sdk.model.api.PlayRequestDto
import org.jellyfin.sdk.model.api.ReadyRequestDto
import org.jellyfin.sdk.model.api.SendCommand
import org.jellyfin.sdk.model.api.SendCommandType
import timber.log.Timber
import java.time.LocalDateTime
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
    
    // Track last executed command for duplicate detection
    private var lastExecutedCommand: ExecutedCommand? = null
    
    data class ExecutedCommand(
        val type: SendCommandType,
        val whenMs: Long,
        val positionTicks: Long,
        val playlistItemId: UUID?
    )
    
    // Track current playlist to avoid reloading the same item
    private var currentPlaylistItemIds: List<UUID> = emptyList()
    private var currentPlaylistIndex: Int = -1
    
    // Track buffering state to avoid spamming reports
    private var lastReportedBufferingState: Boolean? = null
    
    // Sync cooldown tracking
    private var lastSyncCorrectionTime: Long = 0L
    private var currentSpeedCorrectionTarget: Float = SPEED_NORMAL
    
    // Seek tracking for proper ready reporting
    private var pendingSeekPosition: Long? = null
    private var seekTimeoutJob: Job? = null

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
        private const val SPEED_NORMAL = 1.0f
        private const val MIN_PLAYBACK_SPEED = 0.90f
        private const val MAX_PLAYBACK_SPEED = 1.10f
        private const val SEEK_TIMEOUT_MS = 5000L
        private const val DUPLICATE_COMMAND_THRESHOLD_MS = 500L
        private const val DUPLICATE_POSITION_THRESHOLD_TICKS = 10_000_000L // 1 second
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

    suspend fun leaveGroup(): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                api.syncPlayApi.syncPlayLeaveGroup()
            }
            _state.value = SyncPlayState()
            refreshGroups()
            Result.success(Unit)
        } catch (e: org.jellyfin.sdk.api.client.exception.InvalidStatusException) {
            if (e.message?.contains("403") == true) {
                Timber.w("SyncPlay: Group no longer exists on server (403), clearing local state")
                _state.value = SyncPlayState()
                refreshGroups()
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

    suspend fun setPlayQueue(itemIds: List<UUID>, startIndex: Int = 0, startPositionTicks: Long = 0, autoPlay: Boolean = true) {
        executeApiCall("set play queue") {
            api.syncPlayApi.syncPlaySetNewQueue(
                PlayRequestDto(
                    playingQueue = itemIds,
                    playingItemPosition = startIndex,
                    startPositionTicks = startPositionTicks,
                )
            )
        }
        if (autoPlay) {
            requestPlay()
        }
    }

    fun reportBuffering() {
        if (!_state.value.enabled) return
        if (lastReportedBufferingState == true) return
        lastReportedBufferingState = true
        
        val currentPositionTicks = playbackCallback?.getCurrentPositionMs()?.let { SyncPlayUtils.msToTicks(it) } ?: 0L
        val isPlaying = playbackCallback?.isPlaying() == true
        val playlistItemId = currentCommand?.playlistItemId ?: UUID.fromString("00000000-0000-0000-0000-000000000000")
        val serverTime = timeSyncManager.getServerTimeNow()
        val whenTime = LocalDateTime.ofEpochSecond(
            serverTime / 1000,
            ((serverTime % 1000) * 1_000_000).toInt(),
            ZoneOffset.UTC
        )
        
        Timber.d("SyncPlay: Reporting buffering at position ${currentPositionTicks / 10000}ms")
        
        scope.launch {
            executeApiCall("report buffering") {
                api.syncPlayApi.syncPlayBuffering(
                    BufferRequestDto(
                        `when` = whenTime,
                        positionTicks = currentPositionTicks,
                        isPlaying = isPlaying,
                        playlistItemId = playlistItemId
                    )
                )
            }
        }
    }

    fun reportReady() {
        if (!_state.value.enabled) return
        if (lastReportedBufferingState == false) return
        lastReportedBufferingState = false
        
        cancelSeekTimeout()
        pendingSeekPosition = null
        
        val currentPositionTicks = playbackCallback?.getCurrentPositionMs()?.let { SyncPlayUtils.msToTicks(it) } ?: 0L
        val isPlaying = playbackCallback?.isPlaying() == true
        val playlistItemId = currentCommand?.playlistItemId ?: UUID.fromString("00000000-0000-0000-0000-000000000000")
        val serverTime = timeSyncManager.getServerTimeNow()
        val whenTime = LocalDateTime.ofEpochSecond(
            serverTime / 1000,
            ((serverTime % 1000) * 1_000_000).toInt(),
            ZoneOffset.UTC
        )
        
        Timber.d("SyncPlay: Reporting ready at position ${currentPositionTicks / 10000}ms")
        
        scope.launch {
            executeApiCall("report ready") {
                api.syncPlayApi.syncPlayReady(
                    ReadyRequestDto(
                        `when` = whenTime,
                        positionTicks = currentPositionTicks,
                        isPlaying = isPlaying,
                        playlistItemId = playlistItemId
                    )
                )
            }
        }
    }

    fun serverTimeToLocal(serverTime: Long): Long = timeSyncManager.serverTimeToLocal(serverTime)

    fun localTimeToServer(localTime: Long): Long = timeSyncManager.localTimeToServer(localTime)
    
    fun getCurrentDriftMs(): Long? {
        val command = currentCommand ?: return null
        val callback = playbackCallback ?: return null
        if (_state.value.groupState != GroupStateType.PLAYING) return null
        
        val expectedPositionMs = estimateServerPosition(command)
        val currentPositionMs = callback.getCurrentPositionMs()
        return currentPositionMs - expectedPositionMs
    }

    fun getStats(): Map<String, String> {
        val drift = getCurrentDriftMs()
        return mapOf(
            "Time Offset" to "${timeSyncManager.timeOffset}ms",
            "RTT" to "${timeSyncManager.roundTripTime}ms",
            "Sync Mode" to if (timeSyncManager.isGreedyMode) "Greedy" else "Low-Profile",
            "Measurements" to "${timeSyncManager.measurementCount}",
            "Group State" to _state.value.groupState.name,
            "In Group" to "${_state.value.enabled}",
            "Sync Correction" to if (enableSyncCorrection) "ON" else "OFF",
            "Speed Correcting" to if (isSpeedCorrecting) "$currentSpeedCorrectionTarget" else "OFF",
            "Drift" to if (drift != null) "${drift}ms" else "N/A",
        )
    }
    
    fun onAppResume() {
        if (!_state.value.enabled) return
        
        Timber.d("SyncPlay: App resumed, re-syncing time and validating group")
        
        scope.launch {
            timeSyncManager.syncNow()
            
            try {
                refreshGroupInfo()
            } catch (e: Exception) {
                Timber.w(e, "SyncPlay: Failed to validate group on resume")
            }
        }
    }
    
    fun onAppPause() {
        if (!_state.value.enabled) return
        Timber.d("SyncPlay: App paused")
        lastExecutedCommand = null
    }
    
    suspend fun attemptRejoinGroup(): Boolean {
        val groupId = _state.value.groupInfo?.groupId ?: return false
        
        Timber.d("SyncPlay: Attempting to rejoin group $groupId")
        
        return try {
            withContext(Dispatchers.IO) {
                api.syncPlayApi.syncPlayJoinGroup(JoinGroupRequestDto(groupId = groupId))
            }
            timeSyncManager.syncNow()
            true
        } catch (e: org.jellyfin.sdk.api.client.exception.InvalidStatusException) {
            if (e.message?.contains("403") == true || e.message?.contains("404") == true) {
                Timber.w("SyncPlay: Group $groupId no longer exists")
                _state.value = SyncPlayState()
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "SyncPlay group was disbanded", Toast.LENGTH_SHORT).show()
                }
            }
            false
        } catch (e: Exception) {
            Timber.e(e, "SyncPlay: Failed to rejoin group")
            false
        }
    }
    
    private fun startSyncServices() {
        timeSyncManager.startSync()
        startDriftChecking()
        startPingUpdates()
    }
    
    private fun stopSyncServices() {
        timeSyncManager.stopSync()
        stopDriftChecking()
        stopPingUpdates()
        stopSpeedCorrection()
        clearScheduledCommand()
        cancelSeekTimeout()
        currentCommand = null
        lastExecutedCommand = null
        currentPlaylistItemIds = emptyList()
        currentPlaylistIndex = -1
        lastReportedBufferingState = null
        lastSyncCorrectionTime = 0L
        currentSpeedCorrectionTarget = SPEED_NORMAL
        pendingSeekPosition = null
    }
    
    private fun startDriftChecking() {
        driftCheckJob?.cancel()
        driftCheckJob = scope.launch {
            while (isActive) {
                delay(DRIFT_CHECK_INTERVAL_MS)
                checkAndCorrectDrift()
            }
        }
    }
    
    private fun stopDriftChecking() {
        driftCheckJob?.cancel()
        driftCheckJob = null
    }
    
    private fun startPingUpdates() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                sendPing()
            }
        }
    }
    
    private fun stopPingUpdates() {
        pingJob?.cancel()
        pingJob = null
    }
    
    private suspend fun sendPing() {
        try {
            val ping = timeSyncManager.roundTripTime
            withContext(Dispatchers.IO) {
                api.syncPlayApi.syncPlayPing(PingRequestDto(ping = ping))
            }
        } catch (e: Exception) {
            Timber.d(e, "SyncPlay: Failed to send ping")
        }
    }
    
    private fun checkAndCorrectDrift() {
        if (!enableSyncCorrection) return
        if (currentCommand == null) return
        if (_state.value.groupState != GroupStateType.PLAYING) return
        
        val callback = playbackCallback ?: return
        if (!callback.isPlaying()) return
        
        val command = currentCommand ?: return
        val expectedPositionMs = estimateServerPosition(command)
        val currentPositionMs = callback.getCurrentPositionMs()
        
        val driftMs = currentPositionMs - expectedPositionMs
        val absDriftMs = abs(driftMs)
        
        Timber.v("SyncPlay drift: ${driftMs}ms (current=$currentPositionMs, expected=$expectedPositionMs)")
        
        when {
            useSkipToSync && absDriftMs >= minDelaySkipToSync -> {
                Timber.d("SyncPlay: SkipToSync - drift=${driftMs}ms, seeking to $expectedPositionMs")
                stopSpeedCorrection()
                lastSyncCorrectionTime = System.currentTimeMillis()
                scope.launch(Dispatchers.Main) {
                    callback.onSeek(expectedPositionMs)
                }
            }
            useSpeedToSync && absDriftMs >= minDelaySpeedToSync && absDriftMs < maxDelaySpeedToSync -> {
                val now = System.currentTimeMillis()
                val cooldownMs = minDelaySpeedToSync / 2
                if (!isSpeedCorrecting && (now - lastSyncCorrectionTime) >= cooldownMs) {
                    val targetSpeed = calculateDynamicSpeed(driftMs)
                    val correctionDuration = calculateCorrectionDuration(driftMs, targetSpeed)
                    Timber.d("SyncPlay: SpeedToSync - drift=${driftMs}ms, speed=$targetSpeed, duration=${correctionDuration}ms")
                    startSpeedCorrection(callback, targetSpeed, correctionDuration)
                }
            }
            absDriftMs < minDelaySpeedToSync && isSpeedCorrecting -> {
                Timber.d("SyncPlay: Drift within range, stopping speed correction")
                stopSpeedCorrection()
            }
        }
    }
    
    private fun estimateServerPosition(command: SendCommand): Long {
        val commandWhenMs = command.`when`.toEpochSecond(ZoneOffset.UTC) * 1000 +
                (command.`when`.nano / 1_000_000)
        val commandPositionMs = SyncPlayUtils.ticksToMs(command.positionTicks ?: 0)
        val serverNow = timeSyncManager.getServerTimeNow()
        val elapsedMs = serverNow - commandWhenMs
        return commandPositionMs + elapsedMs + extraTimeOffset.toLong()
    }
    
    private fun calculateDynamicSpeed(driftMs: Long): Float {
        val duration = speedToSyncDuration.toFloat()
        val speed = if (driftMs > 0) {
            1.0f - (driftMs / duration)
        } else {
            1.0f + (abs(driftMs) / duration)
        }
        return speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
    }
    
    private fun calculateCorrectionDuration(driftMs: Long, targetSpeed: Float): Long {
        val speedDiff = abs(targetSpeed - SPEED_NORMAL)
        return if (speedDiff > 0) {
            (abs(driftMs) / speedDiff).toLong().coerceAtLeast(100L)
        } else {
            speedToSyncDuration.toLong()
        }
    }
    
    private fun startSpeedCorrection(callback: SyncPlayPlaybackCallback, targetSpeed: Float, duration: Long = speedToSyncDuration.toLong()) {
        if (isSpeedCorrecting) return
        
        speedCorrectionJob?.cancel()
        speedCorrectionJob = null
        
        isSpeedCorrecting = true
        currentSpeedCorrectionTarget = targetSpeed
        lastSyncCorrectionTime = System.currentTimeMillis()
        
        scope.launch(Dispatchers.Main) {
            callback.setPlaybackSpeed(targetSpeed)
        }
        
        speedCorrectionJob = scope.launch {
            delay(duration)
            stopSpeedCorrection()
        }
    }
    
    private fun stopSpeedCorrection() {
        val wasSpeedCorrecting = isSpeedCorrecting
        isSpeedCorrecting = false
        currentSpeedCorrectionTarget = SPEED_NORMAL
        
        speedCorrectionJob?.cancel()
        speedCorrectionJob = null
        
        if (wasSpeedCorrecting) {
            scope.launch(Dispatchers.Main) {
                playbackCallback?.setPlaybackSpeed(SPEED_NORMAL)
            }
        }
    }

    fun onPlaybackCommand(command: SendCommand) {
        val commandWhenMs = command.`when`.toEpochSecond(ZoneOffset.UTC) * 1000 +
                (command.`when`.nano / 1_000_000)
        val positionTicks = command.positionTicks ?: 0
        
        if (isDuplicateCommand(command.command, commandWhenMs, positionTicks, command.playlistItemId)) {
            Timber.d("SyncPlay: Ignoring duplicate command ${command.command}")
            return
        }
        
        Timber.i("SyncPlay: Received command ${command.command}, positionTicks=$positionTicks")
        currentCommand = command
        stopSpeedCorrection()
        clearScheduledCommand()
        
        val localTargetTime = serverTimeToLocal(commandWhenMs)
        val adjustedTargetTime = localTargetTime + extraTimeOffset.toLong()
        
        lastExecutedCommand = ExecutedCommand(
            type = command.command,
            whenMs = commandWhenMs,
            positionTicks = positionTicks,
            playlistItemId = command.playlistItemId
        )
        
        when (command.command) {
            SendCommandType.UNPAUSE -> {
                schedulePlay(adjustedTargetTime, positionTicks)
            }
            SendCommandType.PAUSE -> {
                currentCommand = null
                schedulePause(adjustedTargetTime, positionTicks)
            }
            SendCommandType.SEEK -> {
                scheduleSeek(adjustedTargetTime, positionTicks)
            }
            SendCommandType.STOP -> {
                currentCommand = null
                lastExecutedCommand = null
                scheduleStop()
            }
        }
    }
    
    private fun isDuplicateCommand(
        type: SendCommandType,
        whenMs: Long,
        positionTicks: Long,
        playlistItemId: UUID?
    ): Boolean {
        val last = lastExecutedCommand ?: return false
        
        if (last.type != type) return false
        if (last.playlistItemId != playlistItemId) return false
        
        val timeDiff = abs(whenMs - last.whenMs)
        val positionDiff = abs(positionTicks - last.positionTicks)
        
        return timeDiff < DUPLICATE_COMMAND_THRESHOLD_MS && 
               positionDiff < DUPLICATE_POSITION_THRESHOLD_TICKS
    }

    fun onGroupUpdate(update: GroupUpdate) {
        when (update) {
            is org.jellyfin.sdk.model.api.SyncPlayGroupJoinedUpdate -> {
                val groupInfo = update.data
                _state.value = _state.value.copy(
                    enabled = true,
                    groupInfo = groupInfo,
                )
                startSyncServices()
            }
            is org.jellyfin.sdk.model.api.SyncPlayUserJoinedUpdate -> {
                val userName = update.data
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "$userName joined the SyncPlay group", Toast.LENGTH_SHORT).show()
                }
                scope.launch(Dispatchers.IO) {
                    refreshGroupInfo()
                }
            }
            is org.jellyfin.sdk.model.api.SyncPlayUserLeftUpdate -> {
                val userName = update.data
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "$userName left the SyncPlay group", Toast.LENGTH_SHORT).show()
                }
                scope.launch(Dispatchers.IO) {
                    refreshGroupInfo()
                }
            }
            is org.jellyfin.sdk.model.api.SyncPlayGroupLeftUpdate -> {
                _state.value = SyncPlayState()
                stopSyncServices()
            }
            is org.jellyfin.sdk.model.api.SyncPlayStateUpdate -> {
                val stateData = update.data
                _state.value = _state.value.copy(groupState = stateData.state)
            }
            is org.jellyfin.sdk.model.api.SyncPlayPlayQueueUpdate -> {
                val queue = update.data
                val reason = queue.reason
                
                val itemIds = queue.playlist.mapNotNull { it.itemId }
                if (itemIds.isNotEmpty()) {
                    val startIndex = queue.playingItemIndex
                    val startPosition = queue.startPositionTicks
                    
                    val isNewQueue = reason == org.jellyfin.sdk.model.api.PlayQueueUpdateReason.NEW_PLAYLIST ||
                        itemIds != currentPlaylistItemIds ||
                        (startIndex != currentPlaylistIndex && playbackCallback == null)
                    
                    if (isNewQueue) {
                        Timber.i("SyncPlay: Loading new queue (reason=$reason, items=${itemIds.size}, index=$startIndex)")
                        currentPlaylistItemIds = itemIds
                        currentPlaylistIndex = startIndex
                        
                        scope.launch(Dispatchers.Main) {
                            if (playbackCallback != null) {
                                playbackCallback?.onLoadQueue(itemIds, startIndex, startPosition)
                            } else {
                                queueLaunchCallback?.invoke(itemIds, startIndex, startPosition)
                            }
                        }
                    } else {
                        Timber.v("SyncPlay: Ignoring queue update echo (reason=$reason)")
                    }
                }
            }
            else -> Unit
        }
    }

    private var scheduledPlayJob: Job? = null
    private var scheduledPauseJob: Job? = null
    private var scheduledSeekJob: Job? = null

    private fun clearScheduledCommand() {
        scheduledPlayJob?.cancel()
        scheduledPlayJob = null
        scheduledPauseJob?.cancel()
        scheduledPauseJob = null
        scheduledSeekJob?.cancel()
        scheduledSeekJob = null
        cancelSeekTimeout()
        pendingSeekPosition = null
    }

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

    private fun schedulePause(targetLocalTime: Long, positionTicks: Long) {
        scheduledPauseJob?.cancel()
        scheduledPauseJob = scope.launch(Dispatchers.Main) {
            val delayMs = targetLocalTime - System.currentTimeMillis()
            if (delayMs > 0) {
                delay(delayMs)
            }
            playbackCallback?.onPause(SyncPlayUtils.ticksToMs(positionTicks))
        }
    }

    private fun scheduleSeek(targetLocalTime: Long, positionTicks: Long) {
        scheduledSeekJob?.cancel()
        val targetPositionMs = SyncPlayUtils.ticksToMs(positionTicks)
        pendingSeekPosition = targetPositionMs
        
        scheduledSeekJob = scope.launch(Dispatchers.Main) {
            val delayMs = targetLocalTime - System.currentTimeMillis()
            if (delayMs > 0) {
                delay(delayMs)
            }
            startSeekTimeout(targetPositionMs)
            playbackCallback?.onSeek(targetPositionMs)
        }
    }
    
    private fun startSeekTimeout(targetPositionMs: Long) {
        cancelSeekTimeout()
        seekTimeoutJob = scope.launch {
            delay(SEEK_TIMEOUT_MS)
            if (pendingSeekPosition == targetPositionMs) {
                Timber.w("SyncPlay: Seek timeout, retrying seek to $targetPositionMs ms")
                pendingSeekPosition = null
                scope.launch(Dispatchers.Main) {
                    playbackCallback?.onSeek(targetPositionMs)
                }
                startSeekTimeout(targetPositionMs)
            }
        }
    }
    
    private fun cancelSeekTimeout() {
        seekTimeoutJob?.cancel()
        seekTimeoutJob = null
    }

    private fun scheduleStop() {
        clearScheduledCommand()
        scope.launch(Dispatchers.Main) {
            playbackCallback?.onStop()
        }
    }
}
