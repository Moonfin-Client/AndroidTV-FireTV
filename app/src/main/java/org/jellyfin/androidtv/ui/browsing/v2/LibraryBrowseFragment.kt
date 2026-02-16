package org.jellyfin.androidtv.ui.browsing.v2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.constant.PosterSize
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
import org.jellyfin.androidtv.ui.navigation.ProvideRouter
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsDialog
import org.jellyfin.androidtv.ui.settings.composable.SettingsRouterContent
import org.jellyfin.androidtv.ui.settings.routes
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType as JellyfinImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.UUID

class LibraryBrowseFragment : Fragment() {

	private val viewModel: LibraryBrowseViewModel by viewModel()
	private val navigationRepository: NavigationRepository by inject()
	private val backgroundService: BackgroundService by inject()
	private val itemLauncher: ItemLauncher by inject()
	private val sessionRepository: SessionRepository by inject()

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
			setContent { JellyfinTheme { LibraryBrowseContent() } }
		}
		mainContainer.addView(contentView)

		return mainContainer
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val genreName = arguments?.getString(ARG_GENRE_NAME)
		if (genreName != null) {
			// Genre mode
			val parentId = Utils.uuidOrNull(arguments?.getString(ARG_PARENT_ID))
			val includeType = arguments?.getString(ARG_INCLUDE_TYPE)
			val serverId = Utils.uuidOrNull(arguments?.getString(ARG_SERVER_ID))
			val userId = Utils.uuidOrNull(arguments?.getString("UserId"))
			val displayPrefsId = arguments?.getString(ARG_DISPLAY_PREFS_ID)
			val parentItemId = Utils.uuidOrNull(arguments?.getString(ARG_PARENT_ITEM_ID))
			viewModel.initializeGenre(genreName, parentId, includeType, serverId, userId, displayPrefsId, parentItemId)
		} else {
			// Library mode
			val folderJson = arguments?.getString(Extras.Folder) ?: return
			val serverId = Utils.uuidOrNull(arguments?.getString("ServerId"))
			val userId = Utils.uuidOrNull(arguments?.getString("UserId"))
			viewModel.initialize(folderJson, serverId, userId)
		}
	}

	companion object {
		const val ARG_GENRE_NAME = "genre_name"
		const val ARG_PARENT_ID = "parent_id"
		const val ARG_INCLUDE_TYPE = "include_type"
		const val ARG_SERVER_ID = "server_id"
		const val ARG_DISPLAY_PREFS_ID = "display_prefs_id"
		const val ARG_PARENT_ITEM_ID = "parent_item_id"
	}

	// ──────────────────────────────────────────────
	// Composable content
	// ──────────────────────────────────────────────

	@Composable
	private fun LibraryBrowseContent() {
		val uiState by viewModel.uiState.collectAsState()
		var settingsVisible by remember { mutableStateOf(false) }

		val folderJson = arguments?.getString(Extras.Folder)
		val folder = remember(folderJson) {
			folderJson?.let { kotlinx.serialization.json.Json.decodeFromString(BaseItemDto.serializer(), it) }
		}

		Box(modifier = Modifier.fillMaxSize()) {
			// Activity background (backdrop from BackgroundService)
			AppBackground()

			// Semi-transparent dark overlay for readability
			val currentBg by backgroundService.currentBackground.collectAsState()
			val overlayAlpha = if (currentBg != null) 0.45f else 0.75f
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(NavyBackground.copy(alpha = overlayAlpha)),
			)

			Column(modifier = Modifier.fillMaxSize()) {
				// ── Header area ──
				LibraryHeader(
					uiState = uiState,
					onSortSelected = { viewModel.setSortOption(it) },
					onToggleFavorites = { viewModel.toggleFavorites() },
					onToggleUnwatched = { viewModel.toggleUnwatched() },
					onLetterSelected = { viewModel.setStartLetter(it) },
					onSettingsClicked = { settingsVisible = true },
					onHomeClicked = { navigationRepository.navigate(Destinations.home) },
				)

				// ── Grid ──
				when {
					uiState.isLoading && uiState.items.isEmpty() -> {
						Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
							CircularProgressIndicator()
						}
					}
					uiState.items.isEmpty() -> {
						Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
							Text(
								text = stringResource(R.string.lbl_no_items),
								fontSize = 18.sp,
								color = Color.White.copy(alpha = 0.5f),
							)
						}
					}
					else -> {
						LibraryGrid(
							uiState = uiState,
							modifier = Modifier.weight(1f),
						)
					}
				}

				// ── Status bar ──
				LibraryStatusBar(
					statusText = buildStatusText(uiState),
					counterText = "${uiState.items.size} | ${uiState.totalItems}",
				)
			}

			// Settings dialog overlay
			val displayPrefsId = if (uiState.isGenreMode) {
				uiState.displayPreferencesId
			} else {
				folder?.displayPreferencesId
			}
			val settingsItemId = if (uiState.isGenreMode) {
				uiState.parentItemId
			} else {
				folder?.id
			}
			if (displayPrefsId != null && settingsItemId != null) {
				val currentSession by sessionRepository.currentSession.collectAsState()
				ProvideRouter(
					routes,
					Routes.LIBRARIES_DISPLAY,
					mapOf(
						"itemId" to settingsItemId.toString(),
						"displayPreferencesId" to displayPrefsId,
						"serverId" to (currentSession?.serverId?.toString() ?: UUID(0, 0).toString()),
						"userId" to (currentSession?.userId?.toString() ?: UUID(0, 0).toString()),
					)
				) {
					SettingsDialog(
						visible = settingsVisible,
						onDismissRequest = {
							settingsVisible = false
						viewModel.refreshDisplayPreferences()
						}
					) {
						SettingsRouterContent()
					}
				}
			}
		}
	}

	// ──────────────────────────────────────────────
	// Header: focused item HUD (left), library name (center), item count
	// Controls row: sort/filter (left), A-Z (right)
	// ──────────────────────────────────────────────

	@Composable
	private fun LibraryHeader(
		uiState: LibraryBrowseUiState,
		onSortSelected: (SortOption) -> Unit,
		onToggleFavorites: () -> Unit,
		onToggleUnwatched: () -> Unit,
		onLetterSelected: (String?) -> Unit,
		onSettingsClicked: () -> Unit,
		onHomeClicked: () -> Unit,
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(start = 60.dp, end = 60.dp, top = 12.dp, bottom = 4.dp),
		) {
			// Row 0: Centered library name + item count
			Box(
				modifier = Modifier.fillMaxWidth(),
				contentAlignment = Alignment.Center,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = uiState.libraryName,
						fontSize = 26.sp,
						fontWeight = FontWeight.Light,
						color = Color.White,
					)

					if (uiState.totalItems > 0) {
						Spacer(modifier = Modifier.width(12.dp))
						Text(
							text = "${uiState.totalItems} Items",
							fontSize = 12.sp,
							fontWeight = FontWeight.Normal,
							color = Color.White.copy(alpha = 0.4f),
						)
					}
				}
			}

			Spacer(modifier = Modifier.height(6.dp))

			// Row 1: Focused item HUD (left)
			FocusedItemHud(
				item = uiState.focusedItem,
				modifier = Modifier.fillMaxWidth(),
			)

			Spacer(modifier = Modifier.height(6.dp))

			// Row 2: Sort/filter/settings/home buttons (left) — A-Z picker (right)
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				// Left: sort/filter controls
				LibraryToolbarRow(
					uiState = uiState,
					onSortSelected = onSortSelected,
					onToggleFavorites = onToggleFavorites,
					onToggleUnwatched = onToggleUnwatched,
					onSettingsClicked = onSettingsClicked,
					onHomeClicked = onHomeClicked,
				)

				Spacer(modifier = Modifier.weight(1f))

				// Right: A-Z letter filter
				if (uiState.currentSortOption.sortBy == ItemSortBy.SORT_NAME) {
					AlphaPickerBar(
						selectedLetter = uiState.startLetter,
						onLetterSelected = onLetterSelected,
					)
				}
			}
		}
	}

	@Composable
	private fun LibraryToolbarRow(
		uiState: LibraryBrowseUiState,
		onSortSelected: (SortOption) -> Unit,
		onToggleFavorites: () -> Unit,
		onToggleUnwatched: () -> Unit,
		onSettingsClicked: () -> Unit,
		onHomeClicked: () -> Unit,
	) {
		var showFilterDialog by remember { mutableStateOf(false) }

		Row(
			horizontalArrangement = Arrangement.spacedBy(4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			// Home
			LibraryToolbarButton(
				iconRes = R.drawable.ic_house,
				contentDescription = stringResource(R.string.home),
				onClick = onHomeClicked,
			)

			// Filter & Sort button
			LibraryToolbarButton(
				iconRes = R.drawable.ic_sort,
				contentDescription = stringResource(R.string.lbl_sort_by),
				onClick = { showFilterDialog = true },
			)

			// Settings (always available)
			LibraryToolbarButton(
				iconRes = R.drawable.ic_settings,
				contentDescription = stringResource(R.string.lbl_settings),
				onClick = onSettingsClicked,
			)
		}

		// Glass-morphism filter/sort dialog
		if (showFilterDialog) {
			FilterSortDialog(
				title = "Sort & Filter",
				sortOptions = viewModel.sortOptions,
				currentSort = uiState.currentSortOption,
				filterFavorites = uiState.filterFavorites,
				filterUnwatched = uiState.filterUnwatched,
				showUnwatchedToggle = uiState.collectionType == CollectionType.MOVIES ||
					uiState.collectionType == CollectionType.TVSHOWS,
				onSortSelected = onSortSelected,
				onToggleFavorites = onToggleFavorites,
				onToggleUnwatched = onToggleUnwatched,
				onDismiss = { showFilterDialog = false },
			)
		}
	}

	// ──────────────────────────────────────────────
	// Poster grid
	// ──────────────────────────────────────────────

	@Composable
	private fun LibraryGrid(
		uiState: LibraryBrowseUiState,
		modifier: Modifier = Modifier,
	) {
		val gridState = rememberLazyGridState()

		val (cardWidth, cardHeight) = imageTypeToCardDimensions(uiState.posterSize, uiState.imageType)

		val columns = GridCells.Adaptive(minSize = (cardWidth + 16).dp)

		// Infinite scroll
		val shouldLoadMore by remember {
			derivedStateOf {
				val lastIdx = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
				lastIdx >= uiState.items.size - 10
			}
		}
		LaunchedEffect(shouldLoadMore) {
			if (shouldLoadMore && uiState.hasMoreItems) viewModel.loadMore()
		}

		LazyVerticalGrid(
			columns = columns,
			state = gridState,
			modifier = modifier
				.fillMaxWidth()
				.padding(horizontal = 60.dp),
			contentPadding = PaddingValues(top = 20.dp, bottom = 16.dp),
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			itemsIndexed(uiState.items) { _, item ->
				LibraryPosterCard(
					item = item,
					imageUrl = getItemImageUrl(item, uiState.imageType),
					cardWidth = cardWidth,
					cardHeight = cardHeight,
					onClick = { launchItem(item) },
					onFocused = {
						viewModel.setFocusedItem(item)
						backgroundService.setBackground(item, BlurContext.BROWSING)
					},
					showLabels = uiState.isGenreMode,
					showBadge = uiState.isGenreMode,
				)
			}
		}
	}

	// ──────────────────────────────────────────────
	// Helpers
	// ──────────────────────────────────────────────

	private fun getItemImageUrl(item: BaseItemDto, imageType: ImageType): String? {
		val jellyfinType = when (imageType) {
			ImageType.POSTER -> JellyfinImageType.PRIMARY
			ImageType.THUMB -> JellyfinImageType.THUMB
			ImageType.BANNER -> JellyfinImageType.BANNER
			ImageType.SQUARE -> JellyfinImageType.PRIMARY
		}
		// Try the preferred image type, fall back to PRIMARY
		val image = item.itemImages[jellyfinType] ?: item.itemImages[JellyfinImageType.PRIMARY]
		return image?.getUrl(viewModel.effectiveApi, maxHeight = 400)
	}

	private fun launchItem(item: BaseItemDto) {
		val rowItem = BaseItemDtoBaseRowItem(item)
		itemLauncher.launch(rowItem, null, requireContext())
	}

	/**
	 * Maps a [PosterSize] + [ImageType] to (width, height) in dp for the poster cards.
	 */
	private fun imageTypeToCardDimensions(posterSize: PosterSize, imageType: ImageType): Pair<Int, Int> {
		return when (imageType) {
			ImageType.POSTER -> when (posterSize) {
				PosterSize.SMALLEST -> 100 to 150
				PosterSize.SMALL -> 120 to 180
				PosterSize.MED -> 140 to 210
				PosterSize.LARGE -> 180 to 270
				PosterSize.X_LARGE -> 220 to 330
			}
			ImageType.THUMB -> when (posterSize) {
				PosterSize.SMALLEST -> 160 to 90
				PosterSize.SMALL -> 190 to 107
				PosterSize.MED -> 220 to 124
				PosterSize.LARGE -> 280 to 158
				PosterSize.X_LARGE -> 340 to 191
			}
			ImageType.BANNER -> when (posterSize) {
				PosterSize.SMALLEST -> 300 to 52
				PosterSize.SMALL -> 360 to 62
				PosterSize.MED -> 420 to 72
				PosterSize.LARGE -> 500 to 86
				PosterSize.X_LARGE -> 600 to 103
			}
			ImageType.SQUARE -> when (posterSize) {
				PosterSize.SMALLEST -> 100 to 100
				PosterSize.SMALL -> 120 to 120
				PosterSize.MED -> 140 to 140
				PosterSize.LARGE -> 180 to 180
				PosterSize.X_LARGE -> 220 to 220
			}
		}
	}

	@Composable
	private fun buildStatusText(uiState: LibraryBrowseUiState): String {
		val parts = mutableListOf<String>()
		parts.add(stringResource(R.string.lbl_showing))
		if (!uiState.filterFavorites && !uiState.filterUnwatched) {
			parts.add(stringResource(R.string.lbl_all_items))
		} else {
			if (uiState.filterUnwatched) parts.add(stringResource(R.string.lbl_unwatched))
			if (uiState.filterFavorites) parts.add(stringResource(R.string.lbl_favorites))
		}
		if (uiState.startLetter != null) {
			parts.add("${stringResource(R.string.lbl_starting_with)} ${uiState.startLetter}")
		}
		parts.add("${stringResource(R.string.lbl_from)} '${uiState.libraryName}'")
		parts.add("${stringResource(R.string.lbl_sorted_by)} ${uiState.currentSortOption.name}")
		return parts.joinToString(" ")
	}
}
