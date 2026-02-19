package org.jellyfin.androidtv.ui.home.mediabar

/**
 * Shared JavaScript builder for Invidious trailer playback.
 * Used by both [YouTubeTrailerWebView] (media bar, muted) and
 * [TrailerPlayerFragment][org.jellyfin.androidtv.ui.itemdetail.v2.TrailerPlayerFragment] (details, unmuted).
 *
 * The generated script handles:
 *  - Hiding all Invidious player chrome (controls, title, overlays)
 *  - Forcing autoplay with configurable mute
 *  - SponsorBlock segment skipping
 *  - Quality forcing via the Video.js qualityLevels API
 *  - Android bridge callbacks (ended, playing, error, load failure)
 *  - Instance-level timeout: retries via Android bridge if video
 *    doesn't reach "playing" within [INSTANCE_TIMEOUT_MS]
 */
object TrailerJsBuilder {

	/** Max time to wait for the video to start playing before trying the next instance. */
	private const val INSTANCE_TIMEOUT_MS = 12_000

	/** Polling interval for detecting the video element. */
	private const val VIDEO_POLL_MS = 50

	/** Max polling attempts before giving up (50ms × 100 = 5s). */
	private const val MAX_POLL_ATTEMPTS = 100

	/** How often to check for SponsorBlock segment skips. */
	private const val SPONSORBLOCK_POLL_MS = 500

	/**
	 * Build the JavaScript injection snippet.
	 *
	 * @param segments SponsorBlock segments to skip during playback
	 * @param muted Whether the video should play muted (media bar) or with sound (details)
	 * @param objectFit CSS object-fit value: "cover" for media bar, "contain" for fullscreen
	 */
	fun build(
		segments: List<SponsorBlockApi.Segment>,
		muted: Boolean,
		objectFit: String = if (muted) "cover" else "contain",
	): String {
		val segmentsJs = segments.joinToString(",") { s ->
			"""{"start":${s.startTime},"end":${s.endTime}}"""
		}
		val mutedJs = if (muted) "true" else "false"
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
    'video { object-fit: $objectFit !important; width: 100vw !important; height: 100vh !important; }';
  document.head.appendChild(style);

  var segments = [$segmentsJs];
  var isMuted = $mutedJs;
  var hasPlayed = false;
  var hasSignaledReady = false;

  // Instance-level timeout: if video doesn't play within ${INSTANCE_TIMEOUT_MS}ms, report failure
  var instanceTimeout = setTimeout(function() {
    if (!hasPlayed) {
      try { Android.onLoadFailed(); } catch(e) {}
    }
  }, $INSTANCE_TIMEOUT_MS);

  var attempts = 0;
  var waitForVideo = setInterval(function() {
    attempts++;
    var video = document.querySelector('video');
    if (video) {
      clearInterval(waitForVideo);
      setupVideo(video);
    } else if (attempts >= $MAX_POLL_ATTEMPTS) {
      clearInterval(waitForVideo);
      try { Android.onVideoError('no_video_element'); } catch(e) {}
    }
  }, $VIDEO_POLL_MS);

  function setupVideo(video) {
    video.muted = isMuted;
    video.autoplay = true;
    video.controls = false;

    var playBtn = document.querySelector('.vjs-big-play-button');
    if (playBtn) playBtn.click();

    video.addEventListener('ended', function() {
      try { Android.onVideoEnded(); } catch(e) {}
    });
    video.addEventListener('playing', function() {
      hasPlayed = true;
      clearTimeout(instanceTimeout);
      if (!hasSignaledReady) {
        (function checkBuffer(attempts) {
          if (hasSignaledReady) return;
          var buffered = video.buffered;
          if (buffered.length > 0) {
            var bufferAhead = buffered.end(buffered.length - 1) - video.currentTime;
            if (bufferAhead >= 2.0 || video.readyState >= 4) {
              hasSignaledReady = true;
              try { Android.onVideoPlaying(); } catch(e) {}
              return;
            }
          }
          if (attempts < 50) {
            setTimeout(function() { checkBuffer(attempts + 1); }, 200);
          } else {
            hasSignaledReady = true;
            try { Android.onVideoPlaying(); } catch(e) {}
          }
        })(0);
      }
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
      }, $SPONSORBLOCK_POLL_MS);
      video.addEventListener('ended', function() { clearInterval(skipInterval); });
      setTimeout(function() { clearInterval(skipInterval); }, 300000);
    }

    // Force highest quality via Video.js qualityLevels API
    try {
      var vjsEl = document.querySelector('.video-js');
      if (vjsEl && vjsEl.player && vjsEl.player.qualityLevels) {
        var ql = vjsEl.player.qualityLevels();

        function enforceHighest() {
          var best = -1, bestIdx = -1;
          for (var i = 0; i < ql.length; i++) {
            if (ql[i].height > best) { best = ql[i].height; bestIdx = i; }
          }
          if (bestIdx >= 0) {
            for (var i = 0; i < ql.length; i++) { ql[i].enabled = (i === bestIdx); }
          }
        }

        ql.on('addqualitylevel', enforceHighest);
        enforceHighest();
      }
    } catch(qe) {}

    var playPromise = video.play();
    if (playPromise) {
      playPromise.catch(function(e) {
        if (!isMuted) {
          video.muted = true;
          video.play().then(function() {
            setTimeout(function() { video.muted = false; }, 500);
          }).catch(function(e2) {
            var player = document.querySelector('.video-js');
            if (player && player.player && player.player.play) {
              player.player.play();
            }
          });
        } else {
          var player = document.querySelector('.video-js');
          if (player && player.player && player.player.play) {
            player.player.play();
          }
        }
      });
    }
  }
})();
""".trimIndent()
	}
}
