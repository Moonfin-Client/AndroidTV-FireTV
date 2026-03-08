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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.data.service.BlurContext
import org.jellyfin.androidtv.ui.background.AppBackground
import org.jellyfin.androidtv.ui.base.CircularProgressIndicator
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType as JellyfinImageType
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class FavoritesBrowseFragment : Fragment() {

	private val viewModel: FavoritesBrowseViewModel by viewModel()
	private val navigationRepository: NavigationRepository by inject()
	private val backgroundService: BackgroundService by inject()
	private val itemLauncher: ItemLauncher by inject()
	private val api: ApiClient by inject()

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
			setContent { JellyfinTheme { FavoritesBrowseContent() } }
		}
		mainContainer.addView(contentView)

		return mainContainer
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewModel.initialize()
	}

	@Composable
	private fun FavoritesBrowseContent() {
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
				FavoritesHeader(uiState = uiState)

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
						FavoritesRows(
							uiState = uiState,
							modifier = Modifier.weight(1f),
						)
					}
				}

				LibraryStatusBar(
					statusText = stringResource(R.string.lbl_favorites),
					counterText = "",
				)
			}
		}
	}

	@Composable
	private fun FavoritesHeader(uiState: FavoritesBrowseUiState) {
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
					text = stringResource(R.string.lbl_favorites),
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
	private fun FavoritesRows(
		uiState: FavoritesBrowseUiState,
		modifier: Modifier = Modifier,
	) {
		val scrollState = rememberScrollState()

		Column(
			modifier = modifier
				.fillMaxWidth()
				.verticalScroll(scrollState)
				.padding(bottom = 16.dp),
		) {
			if (uiState.cast.isNotEmpty()) {
				FavoriteItemRow(
					title = stringResource(R.string.lbl_cast),
					items = uiState.cast,
					cardWidth = 120,
					cardHeight = 180,
				)
			}

			if (uiState.movies.isNotEmpty()) {
				FavoriteItemRow(
					title = stringResource(R.string.lbl_movies),
					items = uiState.movies,
					cardWidth = 140,
					cardHeight = 210,
				)
			}

			if (uiState.shows.isNotEmpty()) {
				FavoriteItemRow(
					title = stringResource(R.string.lbl_series),
					items = uiState.shows,
					cardWidth = 140,
					cardHeight = 210,
				)
			}

			if (uiState.episodes.isNotEmpty()) {
				FavoriteItemRow(
					title = stringResource(R.string.lbl_episodes),
					items = uiState.episodes,
					cardWidth = 200,
					cardHeight = 112,
				)
			}

			if (uiState.playlists.isNotEmpty()) {
				FavoriteItemRow(
					title = stringResource(R.string.lbl_playlists),
					items = uiState.playlists,
					cardWidth = 140,
					cardHeight = 140,
				)
			}
		}
	}

	@Composable
	private fun FavoriteItemRow(
		title: String,
		items: List<BaseItemDto>,
		cardWidth: Int = 140,
		cardHeight: Int = 210,
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
					FavoriteCard(
						item = item,
						cardWidth = cardWidth,
						cardHeight = cardHeight,
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
	private fun FavoriteCard(
		item: BaseItemDto,
		cardWidth: Int,
		cardHeight: Int,
		onClick: () -> Unit,
		onFocused: () -> Unit,
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
					.size(width = cardWidth.dp, height = cardHeight.dp)
					.clip(RoundedCornerShape(4.dp))
					.then(
						if (isFocused) Modifier.background(Color.White.copy(alpha = 0.08f))
						else Modifier
					)
					.background(Color.White.copy(alpha = 0.06f)),
			) {
				val imageUrl = getImageUrl(item)
				if (imageUrl != null) {
					AsyncImage(
						model = imageUrl,
						contentDescription = item.name,
						modifier = Modifier.fillMaxSize(),
						contentScale = ContentScale.Crop,
					)
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

			val subtitle = getSubtitle(item)
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

	private fun getImageUrl(item: BaseItemDto): String? {
		val primary = item.itemImages[JellyfinImageType.PRIMARY]
		if (primary != null) return primary.getUrl(api, maxHeight = 400)

		val thumb = item.itemImages[JellyfinImageType.THUMB]
		if (thumb != null) return thumb.getUrl(api, maxHeight = 400)

		val parentPrimary = item.parentImages[JellyfinImageType.PRIMARY]
		if (parentPrimary != null) return parentPrimary.getUrl(api, maxHeight = 400)

		return null
	}

	private fun getSubtitle(item: BaseItemDto): String {
		return when (item.type) {
			BaseItemKind.MOVIE,
			BaseItemKind.SERIES -> item.productionYear?.toString() ?: ""
			BaseItemKind.EPISODE -> {
				buildString {
					item.seriesName?.let { append(it) }
					val seasonEp = buildList {
						item.parentIndexNumber?.let { add("S${it}") }
						item.indexNumber?.let { add("E${it}") }
					}.joinToString("")
					if (seasonEp.isNotEmpty()) {
						if (isNotEmpty()) append(" — ")
						append(seasonEp)
					}
				}
			}
			BaseItemKind.PLAYLIST -> {
				val count = item.childCount ?: 0
				if (count > 0) "$count items" else ""
			}
			else -> ""
		}
	}

	private fun launchItem(item: BaseItemDto) {
		val rowItem = BaseItemDtoBaseRowItem(item)
		itemLauncher.launch(rowItem, null, requireContext())
	}
}
