package org.jellyfin.androidtv.ui.itemdetail.v2

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.ui.home.mediabar.SponsorBlockApi
import org.jellyfin.androidtv.ui.home.mediabar.TrailerJsBuilder
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Fullscreen fragment that plays a YouTube trailer via an Invidious WebView
 * with sound enabled. The user exits by pressing Back.
 *
 * Arguments:
 *  - "VideoId"      — YouTube video ID
 *  - "StartSeconds" — Start position in seconds (from SponsorBlock)
 *  - "SegmentsJson" — JSON array of SponsorBlock segments
 */
class TrailerPlayerFragment : Fragment() {

	companion object {
		const val ARG_VIDEO_ID = "VideoId"
		const val ARG_START_SECONDS = "StartSeconds"
		const val ARG_SEGMENTS_JSON = "SegmentsJson"
	}

	private val navigationRepository: NavigationRepository by inject()
	private var webView: WebView? = null

	private val invidiousInstances = listOf(
		"inv.nadeko.net",
		"invidious.fdn.fr",
		"yewtu.be",
		"vid.puffyan.us",
	)

	@SuppressLint("SetJavaScriptEnabled")
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		val videoId = requireArguments().getString(ARG_VIDEO_ID)!!
		val startSeconds = requireArguments().getDouble(ARG_START_SECONDS, 0.0)
		val segmentsJson = requireArguments().getString(ARG_SEGMENTS_JSON, "[]")

		val segments = try {
			Json.decodeFromString<List<SegmentDto>>(segmentsJson).map {
				SponsorBlockApi.Segment(it.start, it.end, it.category, it.action)
			}
		} catch (e: Exception) {
			Timber.w(e, "TrailerPlayer: Failed to parse segments JSON")
			emptyList()
		}

		val injectionScript = TrailerJsBuilder.build(segments = segments, muted = false)

		val startParam = if (startSeconds > 0) "&t=${startSeconds.toInt()}" else ""
		val embedUrl = "https://${invidiousInstances[0]}/embed/$videoId?autoplay=1&controls=0&quality=dash$startParam"

		val root = object : FrameLayout(requireContext()) {
			override fun dispatchKeyEvent(event: KeyEvent): Boolean {
				if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
					goBack()
					return true
				}
				return super.dispatchKeyEvent(event)
			}
		}.apply {
			setBackgroundColor(android.graphics.Color.BLACK)
			isFocusable = true
			isFocusableInTouchMode = true
		}

		var instanceIndex = 0

		fun tryNextInstance(wv: WebView) {
			instanceIndex++
			if (instanceIndex < invidiousInstances.size) {
				val nextStartParam = if (startSeconds > 0) "&t=${startSeconds.toInt()}" else ""
				val nextUrl = "https://${invidiousInstances[instanceIndex]}/embed/$videoId?autoplay=1&controls=0&quality=dash$nextStartParam"
				Timber.d("TrailerPlayer: Trying next instance: $nextUrl")
				wv.loadUrl(nextUrl)
			} else {
				Handler(Looper.getMainLooper()).post { goBack() }
			}
		}

		val wv = WebView(requireContext()).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT,
			)

			settings.apply {
				javaScriptEnabled = true
				mediaPlaybackRequiresUserGesture = false
				domStorageEnabled = true
				loadWithOverviewMode = true
				useWideViewPort = true
				allowContentAccess = true
				userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
					"AppleWebKit/537.36 (KHTML, like Gecko) " +
					"Chrome/120.0.0.0 Safari/537.36"
			}

			setBackgroundColor(android.graphics.Color.BLACK)
			webChromeClient = WebChromeClient()

			val webViewRef = this

			addJavascriptInterface(object {
				@JavascriptInterface
				fun onVideoEnded() {
					Timber.d("TrailerPlayer: Video ended for $videoId")
					Handler(Looper.getMainLooper()).post { goBack() }
				}

				@JavascriptInterface
				fun onVideoError(errorCode: String) {
					Timber.w("TrailerPlayer: Error $errorCode for video $videoId")
					Handler(Looper.getMainLooper()).post { goBack() }
				}

				@JavascriptInterface
				fun onVideoPlaying() {
					Timber.d("TrailerPlayer: Video playing for $videoId")
				}

				@JavascriptInterface
				fun onLoadFailed() {
					Timber.w("TrailerPlayer: Instance timed out or failed, trying next")
					Handler(Looper.getMainLooper()).post { tryNextInstance(webViewRef) }
				}
			}, "Android")

			webViewClient = object : WebViewClient() {
				override fun onPageFinished(view: WebView?, url: String?) {
					super.onPageFinished(view, url)
					Timber.d("TrailerPlayer: Page loaded: $url")
					view?.evaluateJavascript(injectionScript, null)
				}

				override fun onReceivedError(
					view: WebView?,
					errorCode: Int,
					description: String?,
					failingUrl: String?,
				) {
					super.onReceivedError(view, errorCode, description, failingUrl)
					Timber.w("TrailerPlayer: WebView error $errorCode: $description")
					view?.let { tryNextInstance(it) }
				}
			}

			loadUrl(embedUrl)
		}

		webView = wv
		root.addView(wv)
		return root
	}

	private fun goBack() {
		if (navigationRepository.canGoBack) {
			navigationRepository.goBack()
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		webView?.let { wv ->
			wv.loadUrl("about:blank")
			wv.destroy()
		}
		webView = null
	}
}

/**
 * Simple DTO for deserializing SponsorBlock segments from the arguments bundle.
 */
@kotlinx.serialization.Serializable
private data class SegmentDto(
	val start: Double,
	val end: Double,
	val category: String = "",
	val action: String = "skip",
)
