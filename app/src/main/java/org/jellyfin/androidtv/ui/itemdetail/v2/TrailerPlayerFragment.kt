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

		val injectionScript = buildInjectionScript(segments, videoId, startSeconds)

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
					Timber.w("TrailerPlayer: Invidious instance failed")
					Handler(Looper.getMainLooper()).post { goBack() }
				}
			}, "Android")

			webViewClient = object : WebViewClient() {
				private var instanceIndex = 0

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
					instanceIndex++
					if (instanceIndex < invidiousInstances.size) {
						val nextStartParam = if (startSeconds > 0) "&t=${startSeconds.toInt()}" else ""
						val nextUrl = "https://${invidiousInstances[instanceIndex]}/embed/$videoId?autoplay=1&controls=0&quality=dash$nextStartParam"
						Timber.d("TrailerPlayer: Trying next instance: $nextUrl")
						view?.loadUrl(nextUrl)
					} else {
						Handler(Looper.getMainLooper()).post { goBack() }
					}
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

	/**
	 * Builds JS injected after the Invidious embed page loads.
	 * Unlike the media bar version, sound is enabled (not muted).
	 */
	private fun buildInjectionScript(
		segments: List<SponsorBlockApi.Segment>,
		videoId: String,
		startSeconds: Double,
	): String {
		val segmentsJs = segments.joinToString(",") { s ->
			"""{"start":${s.startTime},"end":${s.endTime}}"""
		}
		return """
(function() {
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
    'video { object-fit: contain !important; width: 100vw !important; height: 100vh !important; }';
  document.head.appendChild(style);

  var segments = [$segmentsJs];

  var attempts = 0;
  var maxAttempts = 50;
  var waitForVideo = setInterval(function() {
    attempts++;
    var video = document.querySelector('video');

    if (video) {
      clearInterval(waitForVideo);
      setupVideo(video);
    } else if (attempts >= maxAttempts) {
      clearInterval(waitForVideo);
      try { Android.onVideoError('no_video_element'); } catch(e) {}
    }
  }, 100);

  function setupVideo(video) {
    video.muted = false;
    video.autoplay = true;
    video.controls = false;

    var playBtn = document.querySelector('.vjs-big-play-button');
    if (playBtn) playBtn.click();

    video.addEventListener('ended', function() {
      try { Android.onVideoEnded(); } catch(e) {}
    });
    video.addEventListener('playing', function() {
      try { Android.onVideoPlaying(); } catch(e) {}
    });
    video.addEventListener('error', function() {
      try { Android.onVideoError('media_error'); } catch(e) {}
    });

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

    var playPromise = video.play();
    if (playPromise) {
      playPromise.catch(function(e) {
        // Autoplay with sound may be blocked — try muted first, then unmute
        video.muted = true;
        video.play().then(function() {
          setTimeout(function() { video.muted = false; }, 500);
        }).catch(function(e2) {
          var player = document.querySelector('.video-js');
          if (player && player.player && player.player.play) {
            player.player.play();
          }
        });
      });
    }
  }
})();
""".trimIndent()
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
