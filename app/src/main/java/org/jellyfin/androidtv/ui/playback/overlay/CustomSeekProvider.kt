package org.jellyfin.androidtv.ui.playback.overlay

import android.content.Context
import android.graphics.Bitmap
import androidx.leanback.widget.PlaybackSeekDataProvider
import coil3.ImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import coil3.request.transformations
import coil3.size.Dimension
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.util.coil.SubsetTransformation
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.trickplayApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class CustomSeekProvider(
	private val videoPlayerAdapter: VideoPlayerAdapter,
	private val imageLoader: ImageLoader,
	private val api: ApiClient,
	private val context: Context,
	private val trickPlayEnabled: Boolean,
	private val forwardTime: Long
) : PlaybackSeekDataProvider() {
	private val imageRequests = mutableMapOf<Int, Disposable>()
	private val preloadedThumbnails = ConcurrentHashMap<Int, Bitmap>()
	private val pendingPreloads = ConcurrentHashMap<Int, Boolean>()
	private val preloadScope = CoroutineScope(Dispatchers.IO)
	private var diskCacheJob: Job? = null
	private var memoryPreloadJob: Job? = null
	private var lastPreloadCenter = -1
	private var lastSeekDirection = 1 // 1 = forward, -1 = backward

	companion object {
		// Number of visible thumbnails in the seek bar
		private const val VISIBLE_THUMBNAILS = 7
		// Pre-load a small buffer beyond visible (3 on each side = 13 total in memory)
		private const val PRELOAD_AHEAD = 3
		// Total memory buffer size
		private const val MEMORY_BUFFER_SIZE = VISIBLE_THUMBNAILS + (PRELOAD_AHEAD * 2)
		// Re-trigger preload when we've moved this many positions from last preload center
		private const val PRELOAD_RETRIGGER_THRESHOLD = 2
	}

	init {
		if (trickPlayEnabled) {
			preloadTilesToDiskCache()
		}
	}

	private fun preloadTilesToDiskCache() {
		diskCacheJob?.cancel()
		diskCacheJob = preloadScope.launch {
			val item = videoPlayerAdapter.currentlyPlayingItem
			val mediaSource = videoPlayerAdapter.currentMediaSource
			val mediaSourceId = mediaSource?.id?.toUUIDOrNull()
			if (item == null || mediaSource == null || mediaSourceId == null) return@launch

			val trickPlayResolutions = item.trickplay?.get(mediaSource.id)
			val trickPlayInfo = trickPlayResolutions?.values?.firstOrNull()
			if (trickPlayInfo == null) return@launch

			val duration = videoPlayerAdapter.duration
			val totalPositions = ceil(duration.toDouble() / forwardTime.toDouble()).toInt() + 1
			
			// Calculate the total number of unique tiles
			val totalTiles = mutableSetOf<Int>()
			for (i in 0 until totalPositions) {
				val currentTimeMs = (i * forwardTime).coerceIn(0, duration)
				val currentTile = currentTimeMs.floorDiv(trickPlayInfo.interval).toInt()
				val tileSize = trickPlayInfo.tileWidth * trickPlayInfo.tileHeight
				val tileIndex = currentTile / tileSize
				totalTiles.add(tileIndex)
			}

			Timber.d("Pre-loading ${totalTiles.size} trickplay tile images into Coil disk cache")

			// Pre-load all unique tile images into Coil's disk cache in the background
			totalTiles.forEach { tileIndex ->
				val url = api.trickplayApi.getTrickplayTileImageUrl(
					itemId = item.id,
					width = trickPlayInfo.width,
					index = tileIndex,
					mediaSourceId = mediaSourceId,
				)

				val request = ImageRequest.Builder(context).apply {
					data(url)
					size(Size.ORIGINAL)
					maxBitmapSize(Size(Dimension.Undefined, Dimension.Undefined))
					httpHeaders(NetworkHeaders.Builder().apply {
						set(
							key = "Authorization",
							value = AuthorizationHeaderBuilder.buildHeader(
								api.clientInfo.name,
								api.clientInfo.version,
								api.deviceInfo.id,
								api.deviceInfo.name,
								api.accessToken
							)
						)
					}.build())
				}.build()

				imageLoader.enqueue(request)
			}
		}
	}

	private fun preloadThumbnailsAroundPosition(centerIndex: Int) {
		// Don't re-preload if we haven't moved far enough from the last preload center
		if (lastPreloadCenter >= 0 && abs(centerIndex - lastPreloadCenter) < PRELOAD_RETRIGGER_THRESHOLD) {
			return
		}

		// Track seek direction for prioritization
		if (lastPreloadCenter >= 0) {
			lastSeekDirection = if (centerIndex > lastPreloadCenter) 1 else -1
		}

		lastPreloadCenter = centerIndex
		memoryPreloadJob?.cancel()
		memoryPreloadJob = preloadScope.launch {
			val item = videoPlayerAdapter.currentlyPlayingItem
			val mediaSource = videoPlayerAdapter.currentMediaSource
			val mediaSourceId = mediaSource?.id?.toUUIDOrNull()
			if (item == null || mediaSource == null || mediaSourceId == null) return@launch

			val trickPlayResolutions = item.trickplay?.get(mediaSource.id)
			val trickPlayInfo = trickPlayResolutions?.values?.firstOrNull()
			if (trickPlayInfo == null) return@launch

			val duration = videoPlayerAdapter.duration
			val totalPositions = ceil(duration.toDouble() / forwardTime.toDouble()).toInt() + 1

			// Calculate visible window (centered on current position)
			val halfVisible = VISIBLE_THUMBNAILS / 2
			val visibleStart = max(0, centerIndex - halfVisible)
			val visibleEnd = min(totalPositions - 1, centerIndex + halfVisible)

			// Calculate preload window (visible + small buffer ahead)
			val preloadStart = max(0, visibleStart - PRELOAD_AHEAD)
			val preloadEnd = min(totalPositions - 1, visibleEnd + PRELOAD_AHEAD)

			// Remove thumbnails outside the preload window to free memory
			val indicesToRemove = preloadedThumbnails.keys.filter { it < preloadStart || it > preloadEnd }
			indicesToRemove.forEach { 
				preloadedThumbnails.remove(it)
				pendingPreloads.remove(it)
			}

			// Build list of indices to preload, prioritizing:
			// 1. Current center position
			// 2. Visible thumbnails in seek direction
			// 3. Visible thumbnails behind seek direction
			// 4. Buffer thumbnails in seek direction
			// 5. Buffer thumbnails behind seek direction
			val indicesToPreload = mutableListOf<Int>()
			
			// Helper to check if index needs loading
			fun needsLoad(i: Int) = !preloadedThumbnails.containsKey(i) && !pendingPreloads.containsKey(i)
			
			// Current position first
			if (needsLoad(centerIndex)) {
				indicesToPreload.add(centerIndex)
			}
			
			// Visible range: prioritize direction of seek
			if (lastSeekDirection > 0) {
				// Seeking forward: load ahead first
				((centerIndex + 1)..visibleEnd).filter(::needsLoad).forEach { indicesToPreload.add(it) }
				((centerIndex - 1) downTo visibleStart).filter(::needsLoad).forEach { indicesToPreload.add(it) }
			} else {
				// Seeking backward: load behind first
				((centerIndex - 1) downTo visibleStart).filter(::needsLoad).forEach { indicesToPreload.add(it) }
				((centerIndex + 1)..visibleEnd).filter(::needsLoad).forEach { indicesToPreload.add(it) }
			}
			
			// Buffer range: prioritize direction of seek
			if (lastSeekDirection > 0) {
				// Seeking forward: buffer ahead first
				((visibleEnd + 1)..preloadEnd).filter(::needsLoad).forEach { indicesToPreload.add(it) }
				((visibleStart - 1) downTo preloadStart).filter(::needsLoad).forEach { indicesToPreload.add(it) }
			} else {
				// Seeking backward: buffer behind first
				((visibleStart - 1) downTo preloadStart).filter(::needsLoad).forEach { indicesToPreload.add(it) }
				((visibleEnd + 1)..preloadEnd).filter(::needsLoad).forEach { indicesToPreload.add(it) }
			}

			if (indicesToPreload.isNotEmpty()) {
				Timber.d("Preloading ${indicesToPreload.size} thumbnails around position $centerIndex (visible: $visibleStart-$visibleEnd, buffer: $preloadStart-$preloadEnd)")
			}

			for (i in indicesToPreload) {
				// Mark as pending before starting request
				if (pendingPreloads.putIfAbsent(i, true) != null) continue

				val currentTimeMs = (i * forwardTime).coerceIn(0, duration)
				val currentTile = currentTimeMs.floorDiv(trickPlayInfo.interval).toInt()

				val tileSize = trickPlayInfo.tileWidth * trickPlayInfo.tileHeight
				val tileOffset = currentTile % tileSize
				val tileIndex = currentTile / tileSize

				val tileOffsetX = tileOffset % trickPlayInfo.tileWidth
				val tileOffsetY = tileOffset / trickPlayInfo.tileWidth
				val offsetX = tileOffsetX * trickPlayInfo.width
				val offsetY = tileOffsetY * trickPlayInfo.height

				val url = api.trickplayApi.getTrickplayTileImageUrl(
					itemId = item.id,
					width = trickPlayInfo.width,
					index = tileIndex,
					mediaSourceId = mediaSourceId,
				)

				val request = ImageRequest.Builder(context).apply {
					data(url)
					size(Size.ORIGINAL)
					maxBitmapSize(Size(Dimension.Undefined, Dimension.Undefined))
					httpHeaders(NetworkHeaders.Builder().apply {
						set(
							key = "Authorization",
							value = AuthorizationHeaderBuilder.buildHeader(
								api.clientInfo.name,
								api.clientInfo.version,
								api.deviceInfo.id,
								api.deviceInfo.name,
								api.accessToken
							)
						)
					}.build())

					transformations(SubsetTransformation(offsetX, offsetY, trickPlayInfo.width, trickPlayInfo.height))

					target(
						onSuccess = { image ->
							preloadedThumbnails[i] = image.toBitmap()
							pendingPreloads.remove(i)
						},
						onError = { _ ->
							pendingPreloads.remove(i)
						}
					)
				}.build()

				imageLoader.enqueue(request)
			}
		}
	}

	override fun getSeekPositions(): LongArray {
		if (!videoPlayerAdapter.canSeek()) return LongArray(0)

		val duration = videoPlayerAdapter.duration
		val size = ceil(duration.toDouble() / forwardTime.toDouble()).toInt() + 1
		return LongArray(size) { i -> min(i * forwardTime, duration) }
	}

	override fun getThumbnail(index: Int, callback: ResultCallback) {
		if (!trickPlayEnabled) return

		// Trigger pre-loading of thumbnails around this position
		preloadThumbnailsAroundPosition(index)

		// Check if we have this thumbnail already in memory
		preloadedThumbnails[index]?.let { bitmap ->
			callback.onThumbnailLoaded(bitmap, index)
			return
		}

		// Not in memory yet - load it now (will likely come from disk cache)
		val currentRequest = imageRequests[index]
		if (currentRequest?.isDisposed == false) currentRequest.dispose()

		val item = videoPlayerAdapter.currentlyPlayingItem
		val mediaSource = videoPlayerAdapter.currentMediaSource
		val mediaSourceId = mediaSource?.id?.toUUIDOrNull()
		if (item == null || mediaSource == null || mediaSourceId == null) return

		val trickPlayResolutions = item.trickplay?.get(mediaSource.id)
		val trickPlayInfo = trickPlayResolutions?.values?.firstOrNull()
		if (trickPlayInfo == null) return

		val currentTimeMs = (index * forwardTime).coerceIn(0, videoPlayerAdapter.duration)
		val currentTile = currentTimeMs.floorDiv(trickPlayInfo.interval).toInt()

		val tileSize = trickPlayInfo.tileWidth * trickPlayInfo.tileHeight
		val tileOffset = currentTile % tileSize
		val tileIndex = currentTile / tileSize

		val tileOffsetX = tileOffset % trickPlayInfo.tileWidth
		val tileOffsetY = tileOffset / trickPlayInfo.tileWidth
		val offsetX = tileOffsetX * trickPlayInfo.width
		val offsetY = tileOffsetY * trickPlayInfo.height

		val url = api.trickplayApi.getTrickplayTileImageUrl(
			itemId = item.id,
			width = trickPlayInfo.width,
			index = tileIndex,
			mediaSourceId = mediaSourceId,
		)

		imageRequests[index] = imageLoader.enqueue(ImageRequest.Builder(context).apply {
			data(url)
			size(Size.ORIGINAL)
			maxBitmapSize(Size(Dimension.Undefined, Dimension.Undefined))
			httpHeaders(NetworkHeaders.Builder().apply {
				set(
					key = "Authorization",
					value = AuthorizationHeaderBuilder.buildHeader(
						api.clientInfo.name,
						api.clientInfo.version,
						api.deviceInfo.id,
						api.deviceInfo.name,
						api.accessToken
					)
				)
			}.build())

			transformations(SubsetTransformation(offsetX, offsetY, trickPlayInfo.width, trickPlayInfo.height))

			target(
				onStart = { _ -> callback.onThumbnailLoaded(null, index) },
				onError = { _ -> callback.onThumbnailLoaded(null, index) },
				onSuccess = { image ->
					val bitmap = image.toBitmap()
					preloadedThumbnails[index] = bitmap
					callback.onThumbnailLoaded(bitmap, index)
				}
			)
		}.build())
	}

	override fun reset() {
		diskCacheJob?.cancel()
		memoryPreloadJob?.cancel()
		for (request in imageRequests.values) {
			if (!request.isDisposed) request.dispose()
		}
		imageRequests.clear()
		preloadedThumbnails.clear()
		pendingPreloads.clear()
		lastPreloadCenter = -1
		lastSeekDirection = 1
	}
}
