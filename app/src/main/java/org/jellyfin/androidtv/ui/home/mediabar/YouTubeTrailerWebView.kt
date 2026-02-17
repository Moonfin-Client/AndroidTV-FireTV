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
 * @param modifier Standard Compose modifier
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeTrailerWebView(
	videoId: String,
	startSeconds: Double,
	segments: List<SponsorBlockApi.Segment>,
	isVisible: Boolean,
	onVideoEnded: () -> Unit = {},
	modifier: Modifier = Modifier,
) {
	var webView by remember { mutableStateOf<WebView?>(null) }

	// Invidious instances to try (in order of reliability)
	val invidiousInstances = remember {
		listOf(
			"inv.nadeko.net",
			"invidious.fdn.fr",
			"yewtu.be",
			"vid.puffyan.us",
		)
	}

	// Build the embed URL for the first instance (fallback handled via JS)
	val embedUrl = remember(videoId, startSeconds, invidiousInstances) {
		val startParam = if (startSeconds > 0) "&t=${startSeconds.toInt()}" else ""
		"https://${invidiousInstances[0]}/embed/$videoId?autoplay=1&muted=true&controls=0&quality=dash$startParam"
	}

	// Build the post-load injection script (SponsorBlock + callbacks + CSS overrides)
	val injectionScript = remember(segments, invidiousInstances, videoId, startSeconds) {
		buildInjectionScript(segments, invidiousInstances, videoId, startSeconds)
	}

	// Animate alpha: 0 during buffering (WebView loads in background),
	// 1 when visible (trailer plays). The WebView is always in the
	// composition tree so it can load/buffer regardless of visibility.
	val trailerAlpha by animateFloatAsState(
		targetValue = if (isVisible) 1f else 0f,
		animationSpec = tween(durationMillis = 800),
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

					// Bridge to notify Kotlin when video ends or errors
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
						}

						@JavascriptInterface
						fun onLoadFailed() {
							Timber.w("YouTubeTrailer: Invidious instance failed, trying next")
							Handler(Looper.getMainLooper()).post { onVideoEnded() }
						}
					}, "Android")

					webViewClient = object : WebViewClient() {
						private var instanceIndex = 0

						override fun onPageFinished(view: WebView?, url: String?) {
							super.onPageFinished(view, url)
							Timber.d("YouTubeTrailer: Invidious page loaded: $url")
							// Inject our script after the Invidious player page loads
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
							// Try next Invidious instance
							instanceIndex++
							if (instanceIndex < invidiousInstances.size) {
								val startParam = if (startSeconds > 0) "&t=${startSeconds.toInt()}" else ""
								val nextUrl = "https://${invidiousInstances[instanceIndex]}/embed/$videoId?autoplay=1&muted=true&controls=0&quality=dash$startParam"
								Timber.d("YouTubeTrailer: Trying next instance: $nextUrl")
								view?.loadUrl(nextUrl)
							} else {
								Handler(Looper.getMainLooper()).post { onVideoEnded() }
							}
						}
					}

					// Navigate directly to the Invidious embed page
					loadUrl(embedUrl)
					webView = this
				}
			},
			update = { /* no-op — video auto-plays from Invidious embed params */ },
			modifier = Modifier.fillMaxSize()
		)
	}
}

/**
 * Builds a JavaScript snippet injected after the Invidious embed page loads.
 * It does the following:
 *  1. Hides all Invidious UI controls/overlays so only the video is visible
 *  2. Finds the HTML5 <video> element and forces muted autoplay
 *  3. Sets up SponsorBlock segment skipping via currentTime polling
 *  4. Hooks video events (ended, playing, error) to the Android bridge
 *  5. If the video element isn't found (Invidious error page), reports failure
 */
private fun buildInjectionScript(
	segments: List<SponsorBlockApi.Segment>,
	instances: List<String>,
	videoId: String,
	startSeconds: Double,
): String {
	val segmentsJs = segments.joinToString(",") { s ->
		"""{"start":${s.startTime},"end":${s.endTime}}"""
	}
	return """
(function() {
  // Hide Invidious player chrome: controls, title bar, etc.
  var style = document.createElement('style');
  style.textContent = '* { cursor: none !important; } ' +
    '.vjs-control-bar, .vjs-big-play-button, .vjs-loading-spinner, ' +
    '.vjs-text-track-display, .vjs-modal-dialog, .vjs-poster, ' +
    '.vjs-title-bar, .vjs-title-bar-title, .vjs-title-bar-description, ' +
    '#player-container > :not(video), .video-js .vjs-tech { ' +
    '  cursor: none !important; } ' +
    '.vjs-big-play-button, .vjs-title-bar { display: none !important; } ' +
    'h1, h2, h3, .title, [class*="title"], [class*="info"], ' +
    '.vjs-info-overlay, .vjs-overlay { display: none !important; } ' +
    'body { background: #000 !important; overflow: hidden !important; margin: 0 !important; } ' +
    'video { object-fit: cover !important; width: 100vw !important; height: 100vh !important; }';
  document.head.appendChild(style);

  var segments = [$segmentsJs];

  // Wait for the video element to appear (Invidious may load it asynchronously)
  var attempts = 0;
  var maxAttempts = 50; // 5 seconds max wait
  var waitForVideo = setInterval(function() {
    attempts++;
    var video = document.querySelector('video');

    if (video) {
      clearInterval(waitForVideo);
      setupVideo(video);
    } else if (attempts >= maxAttempts) {
      clearInterval(waitForVideo);
      // No video element found — this Invidious instance might be broken
      try { Android.onVideoError('no_video_element'); } catch(e) {}
    }
  }, 100);

  function setupVideo(video) {
    // Force muted autoplay
    video.muted = true;
    video.autoplay = true;
    video.controls = false;

    // Remove any click-to-play overlays by clicking them
    var playBtn = document.querySelector('.vjs-big-play-button');
    if (playBtn) playBtn.click();

    // Android bridge callbacks
    video.addEventListener('ended', function() {
      try { Android.onVideoEnded(); } catch(e) {}
    });
    video.addEventListener('playing', function() {
      try { Android.onVideoPlaying(); } catch(e) {}
    });
    video.addEventListener('error', function() {
      try { Android.onVideoError('media_error'); } catch(e) {}
    });

    // SponsorBlock segment skipping
    if (segments.length > 0) {
      var skipInterval = setInterval(function() {
        if (!video || video.paused) return;
        var t = video.currentTime;
        for (var i = 0; i < segments.length; i++) {
          var seg = segments[i];
          if (t >= seg.start && t < seg.end - 0.5) {
            video.currentTime = seg.end;
            break;
          }
        }
      }, 500);
      video.addEventListener('ended', function() { clearInterval(skipInterval); });
      setTimeout(function() { clearInterval(skipInterval); }, 300000);
    }

    // Force highest quality in DASH player
    try {
      var vjsEl = document.querySelector('.video-js');
      if (vjsEl && vjsEl.player && vjsEl.player.qualityLevels) {
        var ql = vjsEl.player.qualityLevels();
        var best = -1, bestIdx = -1;
        for (var i = 0; i < ql.length; i++) {
          if (ql[i].height > best) { best = ql[i].height; bestIdx = i; }
        }
        if (bestIdx >= 0) {
          for (var i = 0; i < ql.length; i++) { ql[i].enabled = (i === bestIdx); }
        }
      }
    } catch(qe) {}

    // Force play if not already playing
    var playPromise = video.play();
    if (playPromise) {
      playPromise.catch(function(e) {
        // If autoplay was blocked, try clicking the player
        var player = document.querySelector('.video-js');
        if (player && player.player && player.player.play) {
          player.player.play();
        }
      });
    }
  }
})();
""".trimIndent()
}
