package org.jellyfin.androidtv.ui.browsing.v2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.Fragment
import coil3.compose.AsyncImage
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.data.service.BlurContext
import org.jellyfin.androidtv.ui.background.AppBackground
import org.jellyfin.androidtv.ui.base.CircularProgressIndicator
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.focusBorderColor
import org.jellyfin.androidtv.ui.browsing.genre.JellyfinGenreItem
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class GenresGridV2Fragment : Fragment() {

	private val viewModel: GenresGridViewModel by viewModel()
	private val navigationRepository: NavigationRepository by inject()
	private val backgroundService: BackgroundService by inject()

	private var folder: BaseItemDto? = null

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
			setContent { JellyfinTheme { GenresGridContent() } }
		}
		mainContainer.addView(contentView)

		return mainContainer
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val folderJson = arguments?.getString(Extras.Folder)
		folder = folderJson?.let {
			try {
				Json.decodeFromString(BaseItemDto.serializer(), it)
			} catch (_: Exception) {
				null
			}
		}
		val includeType = arguments?.getString(Extras.IncludeType)

		viewModel.initialize(folder, includeType)
	}

	// ──────────────────────────────────────────────
	// Composable content
	// ──────────────────────────────────────────────

	@Composable
	private fun GenresGridContent() {
		val uiState by viewModel.uiState.collectAsState()

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
				GenresHeader(uiState = uiState)

				// ── Grid ──
				when {
					uiState.isLoading -> {
						Box(
							modifier = Modifier
								.weight(1f)
								.fillMaxWidth(),
							contentAlignment = Alignment.Center,
						) {
							CircularProgressIndicator(
								modifier = Modifier.size(48.dp),
								color = JellyfinBlue,
							)
						}
					}
					uiState.genres.isEmpty() -> {
						Box(
							modifier = Modifier
								.weight(1f)
								.fillMaxWidth(),
							contentAlignment = Alignment.Center,
						) {
							Text(
								text = stringResource(R.string.lbl_no_items),
								fontSize = 18.sp,
								color = Color.White.copy(alpha = 0.5f),
							)
						}
					}
					else -> {
						GenresGrid(
							uiState = uiState,
							modifier = Modifier.weight(1f),
						)
					}
				}

				// ── Status bar ──
				LibraryStatusBar(
					statusText = buildStatusText(uiState),
					counterText = "${uiState.genres.size} Genres",
				)
			}
		}
	}

	// ──────────────────────────────────────────────
	// Header
	// ──────────────────────────────────────────────

	@Composable
	private fun GenresHeader(uiState: GenresGridUiState) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(start = 60.dp, end = 60.dp, top = 12.dp, bottom = 4.dp),
		) {
			// Row 0: Centered title + genre count
			Box(
				modifier = Modifier.fillMaxWidth(),
				contentAlignment = Alignment.Center,
			) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text(
						text = uiState.title,
						fontSize = 26.sp,
						fontWeight = FontWeight.Light,
						color = Color.White,
					)

					if (uiState.totalGenres > 0) {
						Spacer(modifier = Modifier.width(12.dp))
						Text(
							text = "${uiState.totalGenres} Genres",
							fontSize = 12.sp,
							fontWeight = FontWeight.Normal,
							color = Color.White.copy(alpha = 0.4f),
						)
					}
				}
			}

			Spacer(modifier = Modifier.height(6.dp))

			// Row 1: Focused genre HUD
			FocusedGenreHud(
				genre = uiState.focusedGenre,
				modifier = Modifier.fillMaxWidth(),
			)

			Spacer(modifier = Modifier.height(6.dp))

			// Row 2: Toolbar buttons
			GenresToolbarRow(uiState = uiState)
		}
	}

	@Composable
	private fun FocusedGenreHud(
		genre: JellyfinGenreItem?,
		modifier: Modifier = Modifier,
	) {
		Column(
			modifier = modifier.defaultMinSize(minHeight = 36.dp),
			verticalArrangement = Arrangement.Center,
		) {
			if (genre != null) {
				Box(
					modifier = Modifier.basicMarquee(
						iterations = Int.MAX_VALUE,
						initialDelayMillis = 1200,
					),
				) {
					Text(
						text = genre.name,
						fontSize = 20.sp,
						fontWeight = FontWeight.SemiBold,
						color = Color.White,
						maxLines = 1,
						overflow = TextOverflow.Clip,
					)
				}

				Text(
					text = "${genre.itemCount} Items",
					fontSize = 14.sp,
					fontWeight = FontWeight.Normal,
					color = Color.White.copy(alpha = 0.6f),
					maxLines = 1,
				)
			}
		}
	}

	@Composable
	private fun GenresToolbarRow(uiState: GenresGridUiState) {
		var showSortDialog by remember { mutableStateOf(false) }
		var showLibraryDialog by remember { mutableStateOf(false) }

		Row(
			horizontalArrangement = Arrangement.spacedBy(4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			// Home
			LibraryToolbarButton(
				iconRes = R.drawable.ic_house,
				contentDescription = stringResource(R.string.home),
				onClick = { navigationRepository.navigate(Destinations.home) },
			)

			// Sort button
			LibraryToolbarButton(
				iconRes = R.drawable.ic_sort,
				contentDescription = stringResource(R.string.lbl_sort_by),
				onClick = { showSortDialog = true },
			)

			// Library filter button (only when there are multiple libraries)
			if (uiState.libraries.size > 1 && folder == null) {
				LibraryToolbarButton(
					iconRes = R.drawable.ic_filter,
					contentDescription = "Filter by Library",
					isActive = uiState.selectedLibraryId != null,
					onClick = { showLibraryDialog = true },
				)
			}
		}

		// Sort dialog
		if (showSortDialog) {
			GenreSortDialog(
				currentSort = uiState.currentSort,
				onSortSelected = {
					viewModel.setSortOption(it)
					showSortDialog = false
				},
				onDismiss = { showSortDialog = false },
			)
		}

		// Library filter dialog
		if (showLibraryDialog) {
			LibraryFilterDialog(
				libraries = uiState.libraries,
				selectedLibraryId = uiState.selectedLibraryId,
				libraryServerNames = uiState.libraryServerNames,
				onLibrarySelected = {
					viewModel.setLibraryFilter(it)
					showLibraryDialog = false
				},
				onDismiss = { showLibraryDialog = false },
			)
		}
	}

	// ──────────────────────────────────────────────
	// Genre Grid
	// ──────────────────────────────────────────────

	@Composable
	private fun GenresGrid(
		uiState: GenresGridUiState,
		modifier: Modifier = Modifier,
	) {
		val gridState = rememberLazyGridState()

		// Wide landscape cards for genres
		val cardWidth = 280
		val cardHeight = 158

		LazyVerticalGrid(
			columns = GridCells.Fixed(4),
			state = gridState,
			modifier = modifier
				.fillMaxWidth()
				.padding(horizontal = 60.dp),
			contentPadding = PaddingValues(top = 20.dp, bottom = 16.dp),
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			itemsIndexed(uiState.genres) { _, genre ->
				GenreCard(
					genre = genre,
					cardWidth = cardWidth,
					cardHeight = cardHeight,
					onClick = { onGenreClicked(genre) },
					onFocused = {
						viewModel.setFocusedGenre(genre)
						// If the genre has a backdrop, set it as the background
						genre.backdropUrl?.let { url ->
							backgroundService.setBackgroundUrl(url, BlurContext.BROWSING)
						}
					},
				)
			}
		}
	}

	// ──────────────────────────────────────────────
	// Genre Card
	// ──────────────────────────────────────────────

	@Composable
	private fun GenreCard(
		genre: JellyfinGenreItem,
		cardWidth: Int,
		cardHeight: Int,
		onClick: () -> Unit,
		onFocused: () -> Unit,
		modifier: Modifier = Modifier,
	) {
		val interactionSource = remember { MutableInteractionSource() }
		val isFocused by interactionSource.collectIsFocusedAsState()

		LaunchedEffect(isFocused) {
			if (isFocused) onFocused()
		}

		val scale = if (isFocused) 1.08f else 1.0f
		val cardAlpha = if (isFocused) 1.0f else 0.6f
		val borderColor = focusBorderColor()

		Box(
			modifier = modifier
				.size(width = cardWidth.dp, height = cardHeight.dp)
				.graphicsLayer {
					scaleX = scale
					scaleY = scale
					alpha = cardAlpha
				}
				.clip(RoundedCornerShape(6.dp))
				.then(
					if (isFocused) Modifier.border(2.dp, borderColor, RoundedCornerShape(6.dp))
					else Modifier
				)
				.background(Color.White.copy(alpha = 0.06f))
				.clickable(
					interactionSource = interactionSource,
					indication = null,
					onClick = onClick,
				),
		) {
			// Backdrop image
			if (genre.backdropUrl != null) {
				AsyncImage(
					model = genre.backdropUrl,
					contentDescription = genre.name,
					modifier = Modifier.fillMaxSize(),
					contentScale = ContentScale.Crop,
				)
			}

			// Gradient overlay at bottom for text readability
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(
						Brush.verticalGradient(
							colors = listOf(
								Color.Transparent,
								Color.Black.copy(alpha = 0.75f),
							),
							startY = 0.4f * cardHeight,
						)
					),
			)

			// Genre name + item count
			Column(
				modifier = Modifier
					.align(Alignment.BottomStart)
					.padding(12.dp),
			) {
				Text(
					text = genre.name,
					fontSize = 18.sp,
					fontWeight = FontWeight.SemiBold,
					color = Color.White,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					text = "${genre.itemCount} Items",
					fontSize = 12.sp,
					fontWeight = FontWeight.Normal,
					color = Color.White.copy(alpha = 0.6f),
				)
			}
		}
	}

	// ──────────────────────────────────────────────
	// Genre Sort Dialog
	// ──────────────────────────────────────────────

	@Composable
	private fun GenreSortDialog(
		currentSort: GenreSortOption,
		onSortSelected: (GenreSortOption) -> Unit,
		onDismiss: () -> Unit,
	) {
		val initialFocusRequester = remember { FocusRequester() }

		Dialog(
			onDismissRequest = onDismiss,
			properties = DialogProperties(usePlatformDefaultWidth = false),
		) {
			Box(
				modifier = Modifier.fillMaxSize(),
				contentAlignment = Alignment.Center,
			) {
				Column(
					modifier = Modifier
						.widthIn(min = 340.dp, max = 440.dp)
						.clip(RoundedCornerShape(20.dp))
						.background(Color(0xE6141414))
						.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
						.padding(vertical = 20.dp),
				) {
					// Title
					Text(
						text = "Sort Genres",
						fontSize = 20.sp,
						fontWeight = FontWeight.W600,
						color = Color.White,
						modifier = Modifier
							.padding(horizontal = 24.dp)
							.padding(bottom = 12.dp),
					)

					// Divider
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.height(1.dp)
							.background(Color.White.copy(alpha = 0.08f)),
					)

					Spacer(modifier = Modifier.height(4.dp))

					LazyColumn {
						itemsIndexed(viewModel.sortOptions) { index, option ->
							val interactionSource = remember { MutableInteractionSource() }
							val isFocused by interactionSource.collectIsFocusedAsState()
							val isSelected = option == currentSort

							val focusModifier = if (option == currentSort) {
								Modifier.focusRequester(initialFocusRequester)
							} else {
								Modifier
							}

							Row(
								modifier = focusModifier
									.fillMaxWidth()
									.clickable(
										interactionSource = interactionSource,
										indication = null,
									) { onSortSelected(option) }
									.focusable(interactionSource = interactionSource)
									.background(
										if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent,
									)
									.padding(horizontal = 24.dp, vertical = 12.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								// Radio circle
								Box(
									modifier = Modifier
										.size(18.dp)
										.border(
											width = 2.dp,
											color = if (isSelected) JellyfinBlue else Color.White.copy(alpha = 0.3f),
											shape = CircleShape,
										),
									contentAlignment = Alignment.Center,
								) {
									if (isSelected) {
										Box(
											modifier = Modifier
												.size(10.dp)
												.background(JellyfinBlue, CircleShape),
										)
									}
								}

								Spacer(modifier = Modifier.width(16.dp))

								Text(
									text = option.label,
									fontSize = 16.sp,
									fontWeight = if (isSelected) FontWeight.W600 else FontWeight.W400,
									color = when {
										isSelected -> JellyfinBlue
										isFocused -> Color.White
										else -> Color.White.copy(alpha = 0.8f)
									},
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
									modifier = Modifier.weight(1f),
								)
							}
						}
					}
				}
			}

			LaunchedEffect(Unit) {
				initialFocusRequester.requestFocus()
			}
		}
	}

	// ──────────────────────────────────────────────
	// Library Filter Dialog
	// ──────────────────────────────────────────────

	@Composable
	private fun LibraryFilterDialog(
		libraries: List<BaseItemDto>,
		selectedLibraryId: java.util.UUID?,
		libraryServerNames: Map<java.util.UUID, String> = emptyMap(),
		onLibrarySelected: (BaseItemDto?) -> Unit,
		onDismiss: () -> Unit,
	) {
		val initialFocusRequester = remember { FocusRequester() }

		Dialog(
			onDismissRequest = onDismiss,
			properties = DialogProperties(usePlatformDefaultWidth = false),
		) {
			Box(
				modifier = Modifier.fillMaxSize(),
				contentAlignment = Alignment.Center,
			) {
				Column(
					modifier = Modifier
						.widthIn(min = 340.dp, max = 440.dp)
						.clip(RoundedCornerShape(20.dp))
						.background(Color(0xE6141414))
						.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
						.padding(vertical = 20.dp),
				) {
					// Title
					Text(
						text = "Filter by Library",
						fontSize = 20.sp,
						fontWeight = FontWeight.W600,
						color = Color.White,
						modifier = Modifier
							.padding(horizontal = 24.dp)
							.padding(bottom = 12.dp),
					)

					// Divider
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.height(1.dp)
							.background(Color.White.copy(alpha = 0.08f)),
					)

					Spacer(modifier = Modifier.height(4.dp))

					LazyColumn {
						// "All Libraries" option
						item {
							val interactionSource = remember { MutableInteractionSource() }
							val isFocused by interactionSource.collectIsFocusedAsState()
							val isSelected = selectedLibraryId == null

							val focusModifier = if (isSelected) {
								Modifier.focusRequester(initialFocusRequester)
							} else {
								Modifier
							}

							Row(
								modifier = focusModifier
									.fillMaxWidth()
									.clickable(
										interactionSource = interactionSource,
										indication = null,
									) { onLibrarySelected(null) }
									.focusable(interactionSource = interactionSource)
									.background(
										if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent,
									)
									.padding(horizontal = 24.dp, vertical = 12.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								Box(
									modifier = Modifier
										.size(18.dp)
										.border(
											width = 2.dp,
											color = if (isSelected) JellyfinBlue else Color.White.copy(alpha = 0.3f),
											shape = CircleShape,
										),
									contentAlignment = Alignment.Center,
								) {
									if (isSelected) {
										Box(
											modifier = Modifier
												.size(10.dp)
												.background(JellyfinBlue, CircleShape),
										)
									}
								}

								Spacer(modifier = Modifier.width(16.dp))

								Text(
									text = "All Libraries",
									fontSize = 16.sp,
									fontWeight = if (isSelected) FontWeight.W600 else FontWeight.W400,
									color = when {
										isSelected -> JellyfinBlue
										isFocused -> Color.White
										else -> Color.White.copy(alpha = 0.8f)
									},
								)
							}
						}

						// Individual libraries
						itemsIndexed(libraries) { index, library ->
							val interactionSource = remember { MutableInteractionSource() }
							val isFocused by interactionSource.collectIsFocusedAsState()
							val isSelected = library.id == selectedLibraryId

							val focusModifier = if (isSelected && selectedLibraryId != null) {
								Modifier.focusRequester(initialFocusRequester)
							} else {
								Modifier
							}

							// Build display text with server name if available
							val serverName = libraryServerNames[library.id]
							val displayText = if (serverName != null) {
								"${library.name ?: ""} ($serverName)"
							} else {
								library.name ?: ""
							}

							Row(
								modifier = focusModifier
									.fillMaxWidth()
									.clickable(
										interactionSource = interactionSource,
										indication = null,
									) { onLibrarySelected(library) }
									.focusable(interactionSource = interactionSource)
									.background(
										if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent,
									)
									.padding(horizontal = 24.dp, vertical = 12.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								Box(
									modifier = Modifier
										.size(18.dp)
										.border(
											width = 2.dp,
											color = if (isSelected) JellyfinBlue else Color.White.copy(alpha = 0.3f),
											shape = CircleShape,
										),
									contentAlignment = Alignment.Center,
								) {
									if (isSelected) {
										Box(
											modifier = Modifier
												.size(10.dp)
												.background(JellyfinBlue, CircleShape),
										)
									}
								}

								Spacer(modifier = Modifier.width(16.dp))

								Text(
									text = displayText,
									fontSize = 16.sp,
									fontWeight = if (isSelected) FontWeight.W600 else FontWeight.W400,
									color = when {
										isSelected -> JellyfinBlue
										isFocused -> Color.White
										else -> Color.White.copy(alpha = 0.8f)
									},
								)
							}
						}
					}
				}
			}

			LaunchedEffect(Unit) {
				initialFocusRequester.requestFocus()
			}
		}
	}

	// ──────────────────────────────────────────────
	// Navigation
	// ──────────────────────────────────────────────

	private fun onGenreClicked(genre: JellyfinGenreItem) {
		navigationRepository.navigate(
			Destinations.genreBrowse(
				genreName = genre.name,
				parentId = genre.parentId,
				includeType = viewModel.includeType,
				serverId = genre.serverId,
				displayPreferencesId = folder?.displayPreferencesId,
				parentItemId = folder?.id,
			)
		)
	}

	// ──────────────────────────────────────────────
	// Helpers
	// ──────────────────────────────────────────────

	@Composable
	private fun buildStatusText(uiState: GenresGridUiState): String {
		val parts = mutableListOf<String>()
		parts.add(stringResource(R.string.lbl_showing))
		parts.add("${uiState.totalGenres} genres")
		if (uiState.selectedLibraryName != null) {
			parts.add("${stringResource(R.string.lbl_from)} '${uiState.selectedLibraryName}'")
		} else {
			parts.add("${stringResource(R.string.lbl_from)} all libraries")
		}
		parts.add("sorted by ${uiState.currentSort.label}")
		return parts.joinToString(" ")
	}
}
