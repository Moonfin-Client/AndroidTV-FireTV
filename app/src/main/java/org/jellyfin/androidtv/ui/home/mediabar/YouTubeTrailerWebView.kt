package org.jellyfin.androidtv.ui.home.mediabar

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import timber.log.Timber

/**
 * Composable that renders a YouTube trailer preview in a WebView by
 * loading an Invidious embed URL directly.
 *
 * Previous approaches that failed on Android TV WebView:
 *  - YouTube IFrame API via loadDataWithBaseURL → error 152
 *  - YouTube embed URL via loadUrl → error 153
 *  - Piped API + HTML5 <video> via loadDataWithBaseURL → play button shown, no autoplay
 *
 * This approach uses Invidious, an open-source YouTube frontend:
 *  1. Navigate the WebView directly to an Invidious /embed/ URL
 *  2. Invidious serves its own HTML5 video player that proxies the stream
 *  3. No YouTube embed restrictions apply since Invidious is the origin
 *  4. After page load, inject JS for SponsorBlock skipping + Android callbacks
 *
 * @param videoId The YouTube video ID to play
 * @param startSeconds The start position in seconds (from SponsorBlock calculation)
 * @param segments SponsorBlock segments to skip during playback
 * @param isVisible Whether the WebView should be visible (controls fade in/out)
 * @param onVideoEnded Called when the video finishes or encounters an error
 * @param onVideoReady Called when the video has buffered enough to play smoothly
 * @param modifier Standard Compose modifier
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeTrailerWebView(
	videoId: String,
	startSeconds: Double,
	segments: List<SponsorBlockApi.Segment>,
	muted: Boolean = true,
	isVisible: Boolean,
	onVideoEnded: () -> Unit = {},
	onVideoReady: () -> Unit = {},
	modifier: Modifier = Modifier,
) {
	var webView by remember { mutableStateOf<WebView?>(null) }

	val invidiousInstances = remember {
		listOf(
			"inv.nadeko.net",
			"invidious.fdn.fr",
			"yewtu.be",
			"vid.puffyan.us",
		)
	}

	val embedUrl = remember(videoId, startSeconds, invidiousInstances, muted) {
		buildEmbedUrl(invidiousInstances[0], videoId, startSeconds, muted)
	}

	val injectionScript = remember(segments, videoId, startSeconds, muted) {
		TrailerJsBuilder.build(segments = segments, muted = muted)
	}

	val trailerAlpha by animateFloatAsState(
		targetValue = if (isVisible) 1f else 0f,
		animationSpec = tween(durationMillis = 400),
		label = "trailerAlpha",
	)

	DisposableEffect(Unit) {
		onDispose {
			webView?.let { wv ->
				wv.loadUrl("about:blank")
				wv.destroy()
			}
			webView = null
		}
	}

	Box(
		modifier = modifier
			.fillMaxSize()
			.alpha(trailerAlpha)
			.background(Color.Black)
	) {
		AndroidView(
			factory = { ctx ->
				WebView(ctx).apply {
					layoutParams = ViewGroup.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT,
						ViewGroup.LayoutParams.MATCH_PARENT
					)

					settings.apply {
						javaScriptEnabled = true
						mediaPlaybackRequiresUserGesture = false
						domStorageEnabled = true
						loadWithOverviewMode = true
						useWideViewPort = true
						allowContentAccess = true
						// Standard desktop UA - Invidious doesn't care but it
						// helps ensure a consistent page layout
						userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
							"AppleWebKit/537.36 (KHTML, like Gecko) " +
							"Chrome/120.0.0.0 Safari/537.36"
					}

					setBackgroundColor(android.graphics.Color.BLACK)

					webChromeClient = WebChromeClient()

					var instanceIndex = 0

					fun tryNextInstance() {
						instanceIndex++
						if (instanceIndex < invidiousInstances.size) {
								val nextUrl = buildEmbedUrl(invidiousInstances[instanceIndex], videoId, startSeconds, muted)
							Timber.d("YouTubeTrailer: Trying next instance: $nextUrl")
							loadUrl(nextUrl)
						} else {
							Handler(Looper.getMainLooper()).post { onVideoEnded() }
						}
					}

					addJavascriptInterface(object {
						@JavascriptInterface
						fun onVideoEnded() {
							Timber.d("YouTubeTrailer: Video ended for $videoId")
							Handler(Looper.getMainLooper()).post { onVideoEnded() }
						}

						@JavascriptInterface
						fun onVideoError(errorCode: String) {
							Timber.w("YouTubeTrailer: Error $errorCode for video $videoId")
							Handler(Looper.getMainLooper()).post { onVideoEnded() }
						}

						@JavascriptInterface
						fun onVideoPlaying() {
							Timber.d("YouTubeTrailer: Video playing for $videoId")
							Handler(Looper.getMainLooper()).post { onVideoReady() }
						}

						@JavascriptInterface
						fun onLoadFailed() {
							Timber.w("YouTubeTrailer: Instance timed out or failed, trying next")
							Handler(Looper.getMainLooper()).post { tryNextInstance() }
						}
					}, "Android")

					webViewClient = object : WebViewClient() {
						override fun onPageFinished(view: WebView?, url: String?) {
							super.onPageFinished(view, url)
							Timber.d("YouTubeTrailer: Invidious page loaded: $url")
							view?.evaluateJavascript(injectionScript, null)
						}

						override fun onReceivedError(
							view: WebView?,
							errorCode: Int,
							description: String?,
							failingUrl: String?,
						) {
							super.onReceivedError(view, errorCode, description, failingUrl)
							Timber.w("YouTubeTrailer: WebView error $errorCode: $description for $failingUrl")
							tryNextInstance()
						}
					}

					loadUrl(embedUrl)
					webView = this
				}
			},
			update = { /* no-op — video auto-plays from Invidious embed params */ },
			modifier = Modifier.fillMaxSize()
		)
	}
}

private fun buildEmbedUrl(
	instance: String,
	videoId: String,
	startSeconds: Double,
	muted: Boolean,
): String {
	val startParam = if (startSeconds > 0) "&t=${startSeconds.toInt()}" else ""
	val mutedParam = if (muted) "&muted=true" else ""
	return "https://$instance/embed/$videoId?autoplay=1${mutedParam}&controls=0&quality=dash$startParam"
}
