package org.moonfin.server.emby.api

import org.emby.client.model.PlaybackInfoRequest as EmbyPlaybackInfoRequest
import org.emby.client.model.PlaybackStartInfo
import org.emby.client.model.PlaybackProgressInfo
import org.emby.client.model.PlaybackStopInfo
import org.emby.client.model.PlayMethod as EmbyPlayMethod
import org.emby.client.model.PlaybackErrorCode as EmbyPlaybackErrorCode
import org.moonfin.server.core.api.ServerPlaybackApi
import org.moonfin.server.core.model.PlaybackErrorCode
import org.moonfin.server.core.model.PlaybackInfoRequest
import org.moonfin.server.core.model.PlaybackInfoResult
import org.moonfin.server.core.model.PlaybackProgressReport
import org.moonfin.server.core.model.PlaybackStartReport
import org.moonfin.server.core.model.PlaybackStopReport
import org.moonfin.server.core.model.PlayMethod
import org.moonfin.server.core.model.StreamParams
import org.moonfin.server.emby.EmbyApiClient
import org.moonfin.server.emby.mapper.toServerMediaSource

class EmbyPlaybackApi(private val apiClient: EmbyApiClient) : ServerPlaybackApi {

    override suspend fun getPlaybackInfo(itemId: String, request: PlaybackInfoRequest): PlaybackInfoResult {
        val embyRequest = EmbyPlaybackInfoRequest(
            id = itemId,
            userId = request.userId,
            maxStreamingBitrate = request.maxStreamingBitrate,
            startTimeTicks = request.startTimeTicks,
            audioStreamIndex = request.audioStreamIndex,
            subtitleStreamIndex = request.subtitleStreamIndex,
            mediaSourceId = request.mediaSourceId,
            enableDirectPlay = request.enableDirectPlay,
            enableDirectStream = request.enableDirectStream,
            enableTranscoding = request.enableTranscoding,
            allowVideoStreamCopy = request.allowVideoStreamCopy,
            allowAudioStreamCopy = request.allowAudioStreamCopy,
        )
        val response = apiClient.mediaInfoService!!.postItemsByIdPlaybackinfo(itemId, embyRequest).body()
        return PlaybackInfoResult(
            mediaSources = response.mediaSources?.map { it.toServerMediaSource() } ?: emptyList(),
            playSessionId = response.playSessionId,
            errorCode = response.errorCode?.toCore(),
        )
    }

    override fun getVideoStreamUrl(itemId: String, params: StreamParams): String {
        val base = apiClient.baseUrl.trimEnd('/')
        return buildString {
            append("$base/Videos/$itemId/stream.${params.container}")
            append("?Static=true")
            append("&MediaSourceId=${params.mediaSourceId}")
            append("&PlaySessionId=${params.playSessionId}")
            append("&DeviceId=${params.deviceId}")
            params.audioStreamIndex?.let { append("&AudioStreamIndex=$it") }
            params.subtitleStreamIndex?.let { append("&SubtitleStreamIndex=$it") }
            apiClient.accessToken?.let { append("&api_key=$it") }
        }
    }

    override fun getAudioStreamUrl(itemId: String, params: StreamParams): String {
        val base = apiClient.baseUrl.trimEnd('/')
        return buildString {
            append("$base/Audio/$itemId/stream.${params.container}")
            append("?Static=true")
            append("&MediaSourceId=${params.mediaSourceId}")
            append("&PlaySessionId=${params.playSessionId}")
            append("&DeviceId=${params.deviceId}")
            apiClient.accessToken?.let { append("&api_key=$it") }
        }
    }

    override suspend fun reportPlaybackStart(info: PlaybackStartReport) {
        apiClient.playstateService!!.postSessionsPlaying(
            PlaybackStartInfo(
                itemId = info.itemId,
                playSessionId = info.playSessionId,
                mediaSourceId = info.mediaSourceId,
                positionTicks = info.positionTicks,
                audioStreamIndex = info.audioStreamIndex,
                subtitleStreamIndex = info.subtitleStreamIndex,
                playMethod = info.playMethod.toEmby(),
                isPaused = info.isPaused,
                isMuted = info.isMuted,
                volumeLevel = info.volumeLevel,
                canSeek = true,
            )
        )
    }

    override suspend fun reportPlaybackProgress(info: PlaybackProgressReport) {
        apiClient.playstateService!!.postSessionsPlayingProgress(
            PlaybackProgressInfo(
                itemId = info.itemId,
                playSessionId = info.playSessionId,
                mediaSourceId = info.mediaSourceId,
                positionTicks = info.positionTicks,
                audioStreamIndex = info.audioStreamIndex,
                subtitleStreamIndex = info.subtitleStreamIndex,
                playMethod = info.playMethod.toEmby(),
                isPaused = info.isPaused,
                isMuted = info.isMuted,
                volumeLevel = info.volumeLevel,
                canSeek = true,
            )
        )
    }

    override suspend fun reportPlaybackStopped(info: PlaybackStopReport) {
        apiClient.playstateService!!.postSessionsPlayingStopped(
            PlaybackStopInfo(
                itemId = info.itemId,
                playSessionId = info.playSessionId,
                mediaSourceId = info.mediaSourceId,
                positionTicks = info.positionTicks,
                failed = info.failed,
            )
        )
    }
}

private fun PlayMethod.toEmby(): EmbyPlayMethod = when (this) {
    PlayMethod.DIRECT_PLAY -> EmbyPlayMethod.DIRECT_PLAY
    PlayMethod.DIRECT_STREAM -> EmbyPlayMethod.DIRECT_STREAM
    PlayMethod.TRANSCODE -> EmbyPlayMethod.TRANSCODE
}

private fun EmbyPlaybackErrorCode.toCore(): PlaybackErrorCode = when (this) {
    EmbyPlaybackErrorCode.NOT_ALLOWED -> PlaybackErrorCode.NOT_ALLOWED
    EmbyPlaybackErrorCode.NO_COMPATIBLE_STREAM -> PlaybackErrorCode.NO_COMPATIBLE_STREAM
    EmbyPlaybackErrorCode.RATE_LIMIT_EXCEEDED -> PlaybackErrorCode.RATE_LIMIT_EXCEEDED
}
