package org.jellyfin.androidtv.ui.playback.overlay

import android.content.Context
import android.util.AttributeSet
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.playback.segment.MediaSegmentRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
private fun getSkipButtonText(
	nextEpisodeTitle: String?,
	timeRemaining: Duration?,
	segmentType: String?
): String {
	return when {
		nextEpisodeTitle != null && timeRemaining != null -> {
			val seconds = timeRemaining.inWholeSeconds
			stringResource(R.string.play_next_episode_countdown, seconds)
		}
		nextEpisodeTitle != null -> {
			// Next episode available but timer disabled - show play next without countdown
			stringResource(R.string.lbl_play_next_up)
		}
		segmentType == "INTRO" -> stringResource(R.string.skip_intro)
		segmentType == "RECAP" -> stringResource(R.string.skip_recap)
		segmentType == "COMMERCIAL" -> stringResource(R.string.skip_commercial)
		segmentType == "PREVIEW" -> stringResource(R.string.skip_preview)
		segmentType == "OUTRO" -> stringResource(R.string.skip_outro)
		else -> stringResource(R.string.segment_action_skip)
	}
}

@Composable
fun SkipOverlayComposable(
	visible: Boolean,
	nextEpisodeTitle: String?,
	timeRemaining: Duration?,
	segmentType: String?,
) {
	Box(
		contentAlignment = Alignment.BottomEnd,
		modifier = Modifier
			.padding(60.dp, 80.dp)
	) {
		AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
			Row(
				modifier = Modifier
					.clip(RoundedCornerShape(4.dp))
					.background(Color.White.copy(alpha = 0.95f))
					.border(
						width = 2.dp,
						color = Color.White,
						shape = RoundedCornerShape(4.dp)
					)
					.padding(horizontal = 24.dp, vertical = 14.dp),
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				val text = getSkipButtonText(nextEpisodeTitle, timeRemaining, segmentType)

				Text(
					text = text,
					color = Color.Black,
					fontSize = 22.sp,
					fontWeight = FontWeight.SemiBold,
				)
				
				Icon(
					imageVector = ImageVector.vectorResource(R.drawable.ic_next),
					contentDescription = null,
					tint = Color.Black,
				)
			}
		}
	}
}

class SkipOverlayView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyle: Int = 0
) : AbstractComposeView(context, attrs, defStyle) {
	private val _currentPosition = MutableStateFlow(Duration.ZERO)
	private val _targetPosition = MutableStateFlow<Duration?>(null)
	private val _skipUiEnabled = MutableStateFlow(true)
	private val _nextEpisodeTitle = MutableStateFlow<String?>(null)
	private val _episodeEndPosition = MutableStateFlow<Duration?>(null)
	private val _segmentType = MutableStateFlow<String?>(null)
	
	var onPlayNext: (() -> Unit)? = null

	var currentPosition: Duration
		get() = _currentPosition.value
		set(value) {
			_currentPosition.value = value
		}

	var currentPositionMs: Long
		get() = _currentPosition.value.inWholeMilliseconds
		set(value) {
			_currentPosition.value = value.milliseconds
		}

	var targetPosition: Duration?
		get() = _targetPosition.value
		set(value) {
			_targetPosition.value = value
		}

	var targetPositionMs: Long?
		get() = _targetPosition.value?.inWholeMilliseconds
		set(value) {
			_targetPosition.value = value?.milliseconds
		}

	var skipUiEnabled: Boolean
		get() = _skipUiEnabled.value
		set(value) {
			_skipUiEnabled.value = value
		}
	
	var nextEpisodeTitle: String?
		get() = _nextEpisodeTitle.value
		set(value) {
			_nextEpisodeTitle.value = value
		}
		
	var episodeEndPosition: Duration?
		get() = _episodeEndPosition.value
		set(value) {
			_episodeEndPosition.value = value
		}
	
	var episodeEndPositionMs: Long?
		get() = _episodeEndPosition.value?.inWholeMilliseconds
		set(value) {
			_episodeEndPosition.value = value?.milliseconds
		}
	
	var segmentType: String?
		get() = _segmentType.value
		set(value) {
			_segmentType.value = value
		}

	val visible: Boolean
		get() {
			val enabled = _skipUiEnabled.value
			val targetPosition = _targetPosition.value
			val currentPosition = _currentPosition.value
			val hasContent = _nextEpisodeTitle.value != null || _segmentType.value != null

			return enabled && targetPosition != null && hasContent && currentPosition <= (targetPosition - MediaSegmentRepository.SkipMinDuration)
		}

	@Composable
	override fun Content() {
		val skipUiEnabled by _skipUiEnabled.collectAsState()
		val currentPosition by _currentPosition.collectAsState()
		val targetPosition by _targetPosition.collectAsState()
		val nextEpisodeTitle by _nextEpisodeTitle.collectAsState()
		val episodeEndPosition by _episodeEndPosition.collectAsState()
		val segmentType by _segmentType.collectAsState()

		val visible by remember(skipUiEnabled, currentPosition, targetPosition) {
			derivedStateOf { visible }
		}
		
		val timeRemaining by remember(episodeEndPosition, currentPosition, nextEpisodeTitle) {
			derivedStateOf {
				val endPos = episodeEndPosition
				if (nextEpisodeTitle != null && endPos != null) {
					(endPos - currentPosition).coerceAtLeast(Duration.ZERO)
				} else {
					null
				}
			}
		}

		// Auto hide for regular skip
		LaunchedEffect(skipUiEnabled, targetPosition, nextEpisodeTitle) {
			if (nextEpisodeTitle == null) {
				// Regular skip behavior - auto hide after duration
				delay(MediaSegmentRepository.AskToSkipAutoHideDuration)
				_targetPosition.value = null
			}
		}
		
		// Auto-play next episode when timer expires
		LaunchedEffect(timeRemaining, nextEpisodeTitle) {
			val remaining = timeRemaining
			if (nextEpisodeTitle != null && remaining != null && remaining <= Duration.ZERO) {
				// Clear overlay state immediately to hide button before transition
				_targetPosition.value = null
				_nextEpisodeTitle.value = null
				_episodeEndPosition.value = null
				onPlayNext?.invoke()
			}
		}

		SkipOverlayComposable(visible, nextEpisodeTitle, timeRemaining, segmentType)
	}
}
