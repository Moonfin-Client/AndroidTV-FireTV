package org.jellyfin.androidtv.ui.playback.overlay

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.leanback.widget.PlaybackSeekDataProvider
import org.jellyfin.androidtv.R
import coil3.ImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import coil3.request.transformations
import coil3.size.Dimension
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
	@Volatile private var diskCacheReady = false
	private val preloadedThumbnails = ConcurrentHashMap<Int, Bitmap>()
	private val pendingPreloads = ConcurrentHashMap<Int, Boolean>()
	private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
		Timber.e(throwable, "Uncaught exception in trickplay coroutine")
	}
	private val preloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
	private var diskCacheJob: Job? = null
	private var memoryPreloadJob: Job? = null
	private var lastPreloadCenter = -1
	private var lastSeekDirection = 1

	private val authHeaders: NetworkHeaders by lazy {
		NetworkHeaders.Builder().apply {
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
		}.build()
	}

	companion object {
		private const val VISIBLE_THUMBNAILS = 7
		private const val PRELOAD_AHEAD = 3
		private const val PRELOAD_RETRIGGER_THRESHOLD = 2
	}

	init {
		if (trickPlayEnabled) {
			preloadTilesToDiskCache()
		}
	}

	private fun preloadTilesToDiskCache() {
		diskCacheJob?.cancel()
		diskCacheReady = false
		diskCacheJob = preloadScope.launch {
			val item = videoPlayerAdapter.currentlyPlayingItem
			val mediaSource = videoPlayerAdapter.currentMediaSource
			val mediaSourceId = mediaSource?.id?.toUUIDOrNull()
			if (item == null || mediaSource == null || mediaSourceId == null) return@launch

			val trickPlayResolutions = item.trickplay?.get(mediaSource.id)
			val trickPlayInfo = trickPlayResolutions?.values?.firstOrNull()
			if (trickPlayInfo == null) return@launch

			if (trickPlayInfo.interval <= 0 || trickPlayInfo.tileWidth <= 0 || trickPlayInfo.tileHeight <= 0) {
				Timber.w("Invalid trickplay metadata: interval=${trickPlayInfo.interval}, tile=${trickPlayInfo.tileWidth}x${trickPlayInfo.tileHeight}")
				return@launch
			}

			val duration = videoPlayerAdapter.duration
			if (duration <= 0) return@launch
			val totalPositions = ceil(duration.toDouble() / forwardTime.toDouble()).toInt() + 1
			val totalTiles = mutableSetOf<Int>()
			for (i in 0 until totalPositions) {
				val currentTimeMs = (i * forwardTime).coerceIn(0, duration)
				val currentTile = currentTimeMs.floorDiv(trickPlayInfo.interval).toInt()
				val tileSize = trickPlayInfo.tileWidth * trickPlayInfo.tileHeight
				val tileIndex = currentTile / tileSize
				totalTiles.add(tileIndex)
			}

			Timber.d("Pre-loading ${totalTiles.size} trickplay tiles into disk cache")

			var loadedCount = 0
			for (tileIndex in totalTiles) {
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
					memoryCachePolicy(CachePolicy.DISABLED)
					httpHeaders(authHeaders)
				}.build()

				try {
					imageLoader.execute(request)
					loadedCount++
					if (loadedCount % 5 == 0 || loadedCount == totalTiles.size) {
						Timber.d("Trickplay cache: $loadedCount/${totalTiles.size} tiles loaded")
					}
				} catch (e: Exception) {
					Timber.w(e, "Failed to cache trickplay tile $tileIndex")
				}
			}

			diskCacheReady = true
			Timber.d("Trickplay disk cache ready: $loadedCount/${totalTiles.size} tiles")
		}
	}

	private fun preloadThumbnailsAroundPosition(centerIndex: Int) {
		if (!diskCacheReady) return

		if (lastPreloadCenter >= 0 && abs(centerIndex - lastPreloadCenter) < PRELOAD_RETRIGGER_THRESHOLD) {
			return
		}

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

			if (trickPlayInfo.interval <= 0 || trickPlayInfo.tileWidth <= 0 || trickPlayInfo.tileHeight <= 0) {
				Timber.w("Invalid trickplay metadata: interval=${trickPlayInfo.interval}, tile=${trickPlayInfo.tileWidth}x${trickPlayInfo.tileHeight}")
				return@launch
			}

			val duration = videoPlayerAdapter.duration
			if (duration <= 0 || forwardTime <= 0) return@launch
			val totalPositions = ceil(duration.toDouble() / forwardTime.toDouble()).toInt() + 1

			val halfVisible = VISIBLE_THUMBNAILS / 2
			val visibleStart = max(0, centerIndex - halfVisible)
			val visibleEnd = min(totalPositions - 1, centerIndex + halfVisible)
			val preloadStart = max(0, visibleStart - PRELOAD_AHEAD)
			val preloadEnd = min(totalPositions - 1, visibleEnd + PRELOAD_AHEAD)

			preloadedThumbnails.keys.filter { it < preloadStart || it > preloadEnd }.forEach {
				preloadedThumbnails.remove(it)
				pendingPreloads.remove(it)
			}

			val indicesToPreload = mutableListOf<Int>()
			fun needsLoad(i: Int) = !preloadedThumbnails.containsKey(i) && !pendingPreloads.containsKey(i)

			if (needsLoad(centerIndex)) indicesToPreload.add(centerIndex)

			// Prioritize visible range in seek direction, then buffer range
			if (lastSeekDirection > 0) {
				((centerIndex + 1)..visibleEnd).filter(::needsLoad).forEach { indicesToPreload.add(it) }
				((centerIndex - 1) downTo visibleStart).filter(::needsLoad).forEach { indicesToPreload.add(it) }
			} else {
				((centerIndex - 1) downTo visibleStart).filter(::needsLoad).forEach { indicesToPreload.add(it) }
				((centerIndex + 1)..visibleEnd).filter(::needsLoad).forEach { indicesToPreload.add(it) }
			}
			if (lastSeekDirection > 0) {
				((visibleEnd + 1)..preloadEnd).filter(::needsLoad).forEach { indicesToPreload.add(it) }
				((visibleStart - 1) downTo preloadStart).filter(::needsLoad).forEach { indicesToPreload.add(it) }
			} else {
				((visibleStart - 1) downTo preloadStart).filter(::needsLoad).forEach { indicesToPreload.add(it) }
				((visibleEnd + 1)..preloadEnd).filter(::needsLoad).forEach { indicesToPreload.add(it) }
			}

			if (indicesToPreload.isNotEmpty()) {
				Timber.d("Preloading ${indicesToPreload.size} thumbnails around position $centerIndex (visible: $visibleStart-$visibleEnd, buffer: $preloadStart-$preloadEnd)")
			}

			for (i in indicesToPreload) {
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
					httpHeaders(authHeaders)
					transformations(SubsetTransformation(offsetX, offsetY, trickPlayInfo.width, trickPlayInfo.height))
					target(
						onSuccess = { image ->
							try {
								preloadedThumbnails[i] = image.toBitmap()
							} catch (e: Exception) {
								Timber.e(e, "Error converting trickplay image to bitmap at index $i")
							} finally {
								pendingPreloads.remove(i)
							}
						},
						onError = { _ ->
							Timber.w("Failed to load trickplay thumbnail at index $i")
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
		if (duration <= 0 || forwardTime <= 0) return LongArray(0)
		val size = ceil(duration.toDouble() / forwardTime.toDouble()).toInt() + 1
		return LongArray(size) { i -> min(i * forwardTime, duration) }
	}

	override fun getThumbnail(index: Int, callback: ResultCallback) {
		if (!trickPlayEnabled) return

		if (!diskCacheReady) {
			val trickPlayInfo = videoPlayerAdapter.currentlyPlayingItem
				?.trickplay?.get(videoPlayerAdapter.currentMediaSource?.id)
				?.values?.firstOrNull()
			if (trickPlayInfo != null && trickPlayInfo.width > 0 && trickPlayInfo.height > 0) {
				callback.onThumbnailLoaded(getPlaceholderThumbnail(trickPlayInfo.width, trickPlayInfo.height), index)
			}
			return
		}

		preloadThumbnailsAroundPosition(index)

		preloadedThumbnails[index]?.let { bitmap ->
			callback.onThumbnailLoaded(bitmap, index)
			return
		}

		val currentRequest = imageRequests[index]
		if (currentRequest?.isDisposed == false) currentRequest.dispose()

		val item = videoPlayerAdapter.currentlyPlayingItem
		val mediaSource = videoPlayerAdapter.currentMediaSource
		val mediaSourceId = mediaSource?.id?.toUUIDOrNull()
		if (item == null || mediaSource == null || mediaSourceId == null) return

		val trickPlayResolutions = item.trickplay?.get(mediaSource.id)
		val trickPlayInfo = trickPlayResolutions?.values?.firstOrNull()
		if (trickPlayInfo == null) return

		if (trickPlayInfo.interval <= 0 || trickPlayInfo.tileWidth <= 0 || trickPlayInfo.tileHeight <= 0) {
			Timber.w("Invalid trickplay metadata: interval=${trickPlayInfo.interval}, tile=${trickPlayInfo.tileWidth}x${trickPlayInfo.tileHeight}")
			return
		}

		val duration = videoPlayerAdapter.duration
		if (duration <= 0 || forwardTime <= 0) return

		val currentTimeMs = (index * forwardTime).coerceIn(0, duration)
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

		val placeholderThumbnail = getPlaceholderThumbnail(trickPlayInfo.width, trickPlayInfo.height)

		imageRequests[index] = imageLoader.enqueue(ImageRequest.Builder(context).apply {
			data(url)
			size(Size.ORIGINAL)
			maxBitmapSize(Size(Dimension.Undefined, Dimension.Undefined))
			httpHeaders(authHeaders)
			transformations(SubsetTransformation(offsetX, offsetY, trickPlayInfo.width, trickPlayInfo.height))

			target(
				onStart = { _ -> callback.onThumbnailLoaded(placeholderThumbnail, index) },
				onError = { _ ->
					Timber.w("Failed to load trickplay thumbnail at index $index")
					callback.onThumbnailLoaded(placeholderThumbnail, index)
				},
				onSuccess = { image ->
					try {
						val bitmap = image.toBitmap()
						preloadedThumbnails[index] = bitmap
						callback.onThumbnailLoaded(bitmap, index)
					} catch (e: Exception) {
						Timber.e(e, "Error converting trickplay image to bitmap at index $index")
						callback.onThumbnailLoaded(placeholderThumbnail, index)
					}
				}
			)
		}.build())
	}

	override fun reset() {
		diskCacheJob?.cancel()
		memoryPreloadJob?.cancel()
		diskCacheReady = false
		for (request in imageRequests.values) {
			if (!request.isDisposed) request.dispose()
		}
		imageRequests.clear()
		preloadedThumbnails.clear()
		pendingPreloads.clear()
		lastPreloadCenter = -1
		lastSeekDirection = 1
	}

	private var cachedPlaceholderThumbnail: Bitmap? = null

	private fun getPlaceholderThumbnail(width: Int, height: Int): Bitmap {
		if (cachedPlaceholderThumbnail?.width == width && cachedPlaceholderThumbnail?.height == height) {
			return cachedPlaceholderThumbnail!!
		}
		val color = ContextCompat.getColor(context, R.color.black_transparent_light)
		val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		result.eraseColor(color)
		cachedPlaceholderThumbnail = result
		return result
	}
}
