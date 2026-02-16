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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.data.service.BlurContext
import org.jellyfin.androidtv.ui.background.AppBackground
import org.jellyfin.androidtv.ui.base.CircularProgressIndicator
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType as JellyfinImageType
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class LiveTvBrowseFragment : Fragment() {

	private val viewModel: LiveTvBrowseViewModel by viewModel()
	private val navigationRepository: NavigationRepository by inject()
	private val backgroundService: BackgroundService by inject()
	private val itemLauncher: ItemLauncher by inject()
	private val userRepository: UserRepository by inject()

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
			setContent { JellyfinTheme { LiveTvBrowseContent() } }
		}
		mainContainer.addView(contentView)

		return mainContainer
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val libraryName = arguments?.getString(Extras.Folder)?.let { json ->
			try {
				kotlinx.serialization.json.Json.decodeFromString(BaseItemDto.serializer(), json).name
			} catch (_: Exception) {
				null
			}
		} ?: getString(R.string.pref_live_tv_cat)

		val canManage = userRepository.currentUser.value?.policy?.enableLiveTvManagement == true
		viewModel.initialize(libraryName, canManage)
	}

	@Composable
	private fun LiveTvBrowseContent() {
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
				LiveTvHeader(uiState = uiState)

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
						LiveTvRows(
							uiState = uiState,
							modifier = Modifier.weight(1f),
						)
					}
				}

				LibraryStatusBar(
					statusText = uiState.libraryName,
					counterText = "",
				)
			}
		}
	}

	@Composable
	private fun LiveTvHeader(uiState: LiveTvBrowseUiState) {
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
					text = uiState.libraryName,
					fontSize = 26.sp,
					fontWeight = FontWeight.Light,
					color = Color.White,
				)
			}

			Spacer(modifier = Modifier.height(6.dp))

			FocusedItemHud(
				item = uiState.focusedItem,
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
	private fun LiveTvRows(
		uiState: LiveTvBrowseUiState,
		modifier: Modifier = Modifier,
	) {
		val scrollState = rememberScrollState()

		Column(
			modifier = modifier
				.fillMaxWidth()
				.verticalScroll(scrollState)
				.padding(bottom = 16.dp),
		) {
			LiveTvViewsRow(uiState = uiState)

			if (uiState.onNow.isNotEmpty()) {
				LiveTvItemRow(
					title = stringResource(R.string.lbl_on_now),
					items = uiState.onNow,
				)
			}

			if (uiState.comingUp.isNotEmpty()) {
				LiveTvItemRow(
					title = stringResource(R.string.lbl_coming_up),
					items = uiState.comingUp,
				)
			}

			if (uiState.favoriteChannels.isNotEmpty()) {
				LiveTvItemRow(
					title = stringResource(R.string.lbl_favorite_channels),
					items = uiState.favoriteChannels,
				)
			}

			if (uiState.otherChannels.isNotEmpty()) {
				LiveTvItemRow(
					title = stringResource(R.string.lbl_other_channels),
					items = uiState.otherChannels,
				)
			}

			if (uiState.recentRecordings.isNotEmpty()) {
				LiveTvItemRow(
					title = stringResource(R.string.lbl_recent_recordings),
					items = uiState.recentRecordings,
				)
			}

			if (uiState.pastDay.isNotEmpty()) {
				LiveTvItemRow(
					title = stringResource(R.string.past_24_hours),
					items = uiState.pastDay,
				)
			}

			if (uiState.pastWeek.isNotEmpty()) {
				LiveTvItemRow(
					title = stringResource(R.string.past_week),
					items = uiState.pastWeek,
				)
			}

			if (uiState.scheduledNext24h.isNotEmpty()) {
				LiveTvItemRow(
					title = stringResource(R.string.scheduled_in_next_24_hours),
					items = uiState.scheduledNext24h,
				)
			}
		}
	}

	@Composable
	private fun LiveTvItemRow(
		title: String,
		items: List<BaseItemDto>,
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = 12.dp),
		) {
			Text(
				text = title,
				fontSize = 18.sp,
				fontWeight = FontWeight.SemiBold,
				color = Color.White,
				modifier = Modifier.padding(start = 60.dp, bottom = 8.dp),
			)

			LazyRow(
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				contentPadding = PaddingValues(horizontal = 60.dp),
			) {
				items(items) { item ->
					LiveTvCard(
						item = item,
						onClick = { launchItem(item) },
						onFocused = {
							viewModel.setFocusedItem(item)
							backgroundService.setBackground(item, BlurContext.BROWSING)
						},
					)
				}
			}
		}
	}

	@Composable
	private fun LiveTvCard(
		item: BaseItemDto,
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
						if (isFocused) Modifier.background(Color.White.copy(alpha = 0.08f))
						else Modifier
					)
					.background(Color.White.copy(alpha = 0.06f)),
			) {
				val imageUrl = getLiveTvImageUrl(item)
				if (imageUrl != null) {
					AsyncImage(
						model = imageUrl,
						contentDescription = item.name,
						modifier = Modifier.fillMaxSize(),
						contentScale = ContentScale.Crop,
					)
				} else {
					Box(
						modifier = Modifier.fillMaxSize(),
						contentAlignment = Alignment.Center,
					) {
						Icon(
							imageVector = ImageVector.vectorResource(R.drawable.ic_tv),
							contentDescription = null,
							modifier = Modifier.size(48.dp),
							tint = Color.White.copy(alpha = 0.2f),
						)
					}
				}
			}

			Spacer(modifier = Modifier.height(5.dp))

			Text(
				text = item.name ?: "",
				fontSize = 13.sp,
				fontWeight = FontWeight.Medium,
				color = Color.White,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)

			val subtitle = getLiveTvSubtitle(item)
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

	@Composable
	private fun LiveTvViewsRow(uiState: LiveTvBrowseUiState) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = 4.dp),
		) {
			Text(
				text = stringResource(R.string.lbl_views),
				fontSize = 18.sp,
				fontWeight = FontWeight.SemiBold,
				color = Color.White,
				modifier = Modifier.padding(start = 60.dp, bottom = 8.dp),
			)

			LazyRow(
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				contentPadding = PaddingValues(horizontal = 60.dp),
			) {
				item {
					LiveTvNavButton(
						label = stringResource(R.string.lbl_live_tv_guide),
						iconRes = R.drawable.ic_tv_guide,
						onClick = {
							navigationRepository.navigate(Destinations.liveTvGuide)
						},
					)
				}
				item {
					LiveTvNavButton(
						label = stringResource(R.string.lbl_recorded_tv),
						iconRes = R.drawable.ic_record,
						onClick = {
							navigationRepository.navigate(Destinations.liveTvRecordings)
						},
					)
				}
				if (uiState.canManageRecordings) {
					item {
						LiveTvNavButton(
							label = stringResource(R.string.lbl_schedule),
							iconRes = R.drawable.ic_tv_timer,
							onClick = {
								navigationRepository.navigate(Destinations.liveTvSchedule)
							},
						)
					}
					item {
						LiveTvNavButton(
							label = stringResource(R.string.lbl_series),
							iconRes = R.drawable.ic_record_series,
							onClick = {
								navigationRepository.navigate(Destinations.liveTvSeriesRecordings)
							},
						)
					}
				}
			}
		}
	}

	@Composable
	private fun LiveTvNavButton(
		label: String,
		iconRes: Int,
		onClick: () -> Unit,
	) {
		val interactionSource = remember { MutableInteractionSource() }
		val isFocused by interactionSource.collectIsFocusedAsState()

		val bgColor = when {
			isFocused -> Color.White.copy(alpha = 0.20f)
			else -> Color.White.copy(alpha = 0.08f)
		}

		Column(
			modifier = Modifier
				.width(140.dp)
				.clip(RoundedCornerShape(8.dp))
				.background(bgColor)
				.clickable(
					interactionSource = interactionSource,
					indication = null,
					onClick = onClick,
				)
				.padding(vertical = 20.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Icon(
				imageVector = ImageVector.vectorResource(iconRes),
				contentDescription = label,
				modifier = Modifier.size(32.dp),
				tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
			)

			Spacer(modifier = Modifier.height(8.dp))

			Text(
				text = label,
				fontSize = 14.sp,
				fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
				color = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}

	private fun getLiveTvImageUrl(item: BaseItemDto): String? {
		val thumb = item.itemImages[JellyfinImageType.THUMB]
		if (thumb != null) return thumb.getUrl(viewModel.api, maxHeight = 300)

		val primary = item.itemImages[JellyfinImageType.PRIMARY]
		if (primary != null) return primary.getUrl(viewModel.api, maxHeight = 300)

		val parentThumb = item.parentImages[JellyfinImageType.THUMB]
		if (parentThumb != null) return parentThumb.getUrl(viewModel.api, maxHeight = 300)

		val parentPrimary = item.parentImages[JellyfinImageType.PRIMARY]
		if (parentPrimary != null) return parentPrimary.getUrl(viewModel.api, maxHeight = 300)

		return null
	}

	private fun getLiveTvSubtitle(item: BaseItemDto): String {
		val parts = mutableListOf<String>()

		item.channelName?.let { if (it.isNotBlank()) parts.add(it) }

		item.episodeTitle?.let { if (it.isNotBlank()) parts.add(it) }

		return parts.joinToString(" • ")
	}

	private fun launchItem(item: BaseItemDto) {
		val rowItem = BaseItemDtoBaseRowItem(item)
		itemLauncher.launch(rowItem, null, requireContext())
	}
}
