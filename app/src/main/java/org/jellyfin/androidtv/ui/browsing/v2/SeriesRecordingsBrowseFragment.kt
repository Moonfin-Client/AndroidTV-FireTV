package org.jellyfin.androidtv.ui.browsing.v2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.background.AppBackground
import org.jellyfin.androidtv.ui.base.CircularProgressIndicator
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.model.api.SeriesTimerInfoDto
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class SeriesRecordingsBrowseFragment : Fragment() {

	private val viewModel: SeriesRecordingsBrowseViewModel by viewModel()
	private val navigationRepository: NavigationRepository by inject()
	private val backgroundService: BackgroundService by inject()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		val mainContainer = FrameLayout(requireContext()).apply {
			layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT,
			)
		}

		val contentView = ComposeView(requireContext()).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT,
			)
			setContent { JellyfinTheme { SeriesRecordingsContent() } }
		}
		mainContainer.addView(contentView)

		return mainContainer
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewModel.initialize()
	}

	@Composable
	private fun SeriesRecordingsContent() {
		val uiState by viewModel.uiState.collectAsState()

		Box(modifier = Modifier.fillMaxSize()) {
			AppBackground()

			val currentBg by backgroundService.currentBackground.collectAsState()
			val overlayAlpha = if (currentBg != null) 0.45f else 0.75f
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(NavyBackground.copy(alpha = overlayAlpha)),
			)

			Column(modifier = Modifier.fillMaxSize()) {
				SeriesRecordingsHeader(uiState = uiState)

				when {
					uiState.isLoading -> {
						Box(
							Modifier
								.fillMaxSize()
								.weight(1f),
							contentAlignment = Alignment.Center,
						) {
							CircularProgressIndicator(
								modifier = Modifier.size(48.dp),
								color = JellyfinBlue,
							)
						}
					}
					else -> {
						SeriesRecordingsRows(
							uiState = uiState,
							modifier = Modifier.weight(1f),
						)
					}
				}

				LibraryStatusBar(
					statusText = stringResource(R.string.lbl_series_recordings),
					counterText = "",
				)
			}
		}
	}

	@Composable
	private fun SeriesRecordingsHeader(uiState: SeriesRecordingsBrowseUiState) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(start = 60.dp, end = 60.dp, top = 12.dp, bottom = 4.dp),
		) {
			Box(
				modifier = Modifier.fillMaxWidth(),
				contentAlignment = Alignment.Center,
			) {
				Text(
					text = stringResource(R.string.lbl_series_recordings),
					fontSize = 26.sp,
					fontWeight = FontWeight.Light,
					color = Color.White,
				)
			}

			Spacer(modifier = Modifier.height(6.dp))

			SeriesTimerHud(
				timer = uiState.focusedTimer,
				modifier = Modifier.fillMaxWidth(),
			)

			Spacer(modifier = Modifier.height(6.dp))

			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				LibraryToolbarButton(
					iconRes = R.drawable.ic_house,
					contentDescription = stringResource(R.string.home),
					onClick = { navigationRepository.navigate(Destinations.home) },
				)
			}
		}
	}

	@Composable
	private fun SeriesTimerHud(
		timer: SeriesTimerInfoDto?,
		modifier: Modifier = Modifier,
	) {
		Box(
			modifier = modifier.height(40.dp),
			contentAlignment = Alignment.CenterStart,
		) {
			if (timer != null) {
				Column {
					Text(
						text = timer.name ?: "",
						fontSize = 16.sp,
						fontWeight = FontWeight.SemiBold,
						color = Color.White,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					val subtitle = buildSeriesTimerSubtitle(timer)
					if (subtitle.isNotEmpty()) {
						Text(
							text = subtitle,
							fontSize = 12.sp,
							fontWeight = FontWeight.Normal,
							color = Color.White.copy(alpha = 0.6f),
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}
			}
		}
	}

	@Composable
	private fun SeriesRecordingsRows(
		uiState: SeriesRecordingsBrowseUiState,
		modifier: Modifier = Modifier,
	) {
		val scrollState = rememberScrollState()

		Column(
			modifier = modifier
				.fillMaxWidth()
				.verticalScroll(scrollState)
				.padding(bottom = 16.dp),
		) {
			if (uiState.seriesTimers.isEmpty()) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 40.dp),
					contentAlignment = Alignment.Center,
				) {
					Text(
						text = stringResource(R.string.lbl_no_items),
						fontSize = 16.sp,
						color = Color.White.copy(alpha = 0.5f),
					)
				}
			} else {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 12.dp),
				) {
					Text(
						text = stringResource(R.string.lbl_series_recordings),
						fontSize = 18.sp,
						fontWeight = FontWeight.SemiBold,
						color = Color.White,
						modifier = Modifier.padding(start = 60.dp, bottom = 8.dp),
					)

					LazyRow(
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						contentPadding = PaddingValues(horizontal = 60.dp),
					) {
						items(uiState.seriesTimers) { timer ->
							SeriesTimerCard(
								timer = timer,
								onClick = { launchSeriesTimer(timer) },
								onFocused = { viewModel.setFocusedTimer(timer) },
							)
						}
					}
				}
			}
		}
	}

	@Composable
	private fun SeriesTimerCard(
		timer: SeriesTimerInfoDto,
		onClick: () -> Unit,
		onFocused: () -> Unit,
		cardWidth: Int = 200,
		cardHeight: Int = 112,
	) {
		val interactionSource = remember { MutableInteractionSource() }
		val isFocused by interactionSource.collectIsFocusedAsState()

		LaunchedEffect(isFocused) {
			if (isFocused) onFocused()
		}

		val scale = if (isFocused) 1.08f else 1.0f
		val alpha = if (isFocused) 1.0f else 0.75f

		Column(
			modifier = Modifier
				.width(cardWidth.dp)
				.graphicsLayer {
					scaleX = scale
					scaleY = scale
					this.alpha = alpha
				}
				.clickable(
					interactionSource = interactionSource,
					indication = null,
					onClick = onClick,
				),
			horizontalAlignment = Alignment.Start,
		) {
			Box(
				modifier = Modifier
					.width(cardWidth.dp)
					.height(cardHeight.dp)
					.clip(RoundedCornerShape(4.dp))
					.then(
						if (isFocused) Modifier.background(Color.White.copy(alpha = 0.12f))
						else Modifier
					)
					.background(Color.White.copy(alpha = 0.06f)),
				contentAlignment = Alignment.Center,
			) {
				Icon(
					imageVector = ImageVector.vectorResource(R.drawable.ic_record_series),
					contentDescription = null,
					modifier = Modifier.size(48.dp),
					tint = if (isFocused) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f),
				)
			}

			Spacer(modifier = Modifier.height(5.dp))

			Text(
				text = timer.name ?: "",
				fontSize = 13.sp,
				fontWeight = FontWeight.Medium,
				color = Color.White,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)

			val subtitle = buildSeriesTimerSubtitle(timer)
			if (subtitle.isNotEmpty()) {
				Text(
					text = subtitle,
					fontSize = 11.sp,
					fontWeight = FontWeight.Normal,
					color = Color.White.copy(alpha = 0.5f),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}

	private fun buildSeriesTimerSubtitle(timer: SeriesTimerInfoDto): String {
		val parts = mutableListOf<String>()
		val channelText = if (timer.recordAnyChannel == true) {
			getString(R.string.all_channels)
		} else {
			timer.channelName
		}
		channelText?.let { if (it.isNotBlank()) parts.add(it) }
		timer.dayPattern?.let { parts.add(it.toString()) }
		return parts.joinToString(" • ")
	}

	private fun launchSeriesTimer(timer: SeriesTimerInfoDto) {
		val id = timer.id?.toUUIDOrNull() ?: return
		navigationRepository.navigate(Destinations.seriesTimerDetails(id, timer))
	}
}
