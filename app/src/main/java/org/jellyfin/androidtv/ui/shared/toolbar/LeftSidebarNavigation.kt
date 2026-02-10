package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.LocalActivity
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.model.AggregatedLibrary
import org.jellyfin.androidtv.data.repository.MultiServerRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.preference.JellyseerrPreferences
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.ClockBehavior
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.playback.ThemeMusicPlayer
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.jellyfin.androidtv.ui.shuffle.ShuffleManager
import org.jellyfin.androidtv.ui.shuffle.ShuffleOptionsDialog
import org.jellyfin.androidtv.ui.syncplay.SyncPlayDialog
import org.jellyfin.androidtv.ui.syncplay.SyncPlayViewModel
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.androidtv.util.sdk.ApiClientFactory
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel
import org.koin.core.qualifier.named
import java.util.UUID

@Composable
fun LeftSidebarNavigation(
	activeButton: MainToolbarActiveButton = MainToolbarActiveButton.None,
	activeLibraryId: UUID? = null,
) {
	val activity = LocalActivity.current
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val userPreferences = koinInject<UserPreferences>()
	val userRepository = koinInject<UserRepository>()
	val sessionRepository = koinInject<SessionRepository>()
	val mediaManager = koinInject<MediaManager>()
	val api = koinInject<ApiClient>()
	val apiClientFactory = koinInject<ApiClientFactory>()
	val settingsViewModel = koinActivityViewModel<SettingsViewModel>()
	val settingsClosedCounter by settingsViewModel.settingsClosedCounter.collectAsState()
	val jellyseerrPreferences = koinInject<JellyseerrPreferences>(named("global"))
	
	// User image - same pattern as MainToolbar
	val currentUser by remember { userRepository.currentUser.filterNotNull() }.collectAsState(null)
	val userImageUrl = remember(currentUser) { currentUser?.primaryImage?.getUrl(api) }
	
	// User preferences
	var showShuffleButton by remember { mutableStateOf(true) }
	var showGenresButton by remember { mutableStateOf(true) }
	var showFavoritesButton by remember { mutableStateOf(true) }
	var showLibrariesInToolbar by remember { mutableStateOf(true) }
	var shuffleContentType by remember { mutableStateOf("both") }
	var enableMultiServer by remember { mutableStateOf(false) }
	var syncPlayEnabled by remember { mutableStateOf(false) }
	var jellyseerrEnabled by remember { mutableStateOf(false) }
	var enableFolderView by remember { mutableStateOf(false) }
	var clockBehavior by remember { mutableStateOf(ClockBehavior.ALWAYS) }
	
	LaunchedEffect(settingsClosedCounter) {
		showShuffleButton = userPreferences[UserPreferences.showShuffleButton]
		showGenresButton = userPreferences[UserPreferences.showGenresButton]
		showFavoritesButton = userPreferences[UserPreferences.showFavoritesButton]
		showLibrariesInToolbar = userPreferences[UserPreferences.showLibrariesInToolbar]
		enableMultiServer = userPreferences[UserPreferences.enableMultiServerLibraries]
		shuffleContentType = userPreferences[UserPreferences.shuffleContentType]
		syncPlayEnabled = userPreferences[UserPreferences.syncPlayEnabled]
		enableFolderView = userPreferences[UserPreferences.enableFolderView]
		clockBehavior = userPreferences[UserPreferences.clockBehavior]
	}
	
	// Check Jellyseerr settings
	LaunchedEffect(currentUser) {
		if (currentUser != null) {
			// All Jellyseerr settings are now per-user
			val userJellyseerrPrefs = JellyseerrPreferences.migrateToUserPreferences(context, currentUser!!.id.toString())
			val enabled = userJellyseerrPrefs[JellyseerrPreferences.enabled]
			val showInToolbar = userJellyseerrPrefs[JellyseerrPreferences.showInToolbar]
			jellyseerrEnabled = enabled && showInToolbar
		} else {
			jellyseerrEnabled = false
		}
	}
	
	// Load user views/libraries
	val userViewsRepository = koinInject<UserViewsRepository>()
	var userViews by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
	
	LaunchedEffect(Unit) {
		userViewsRepository.views.collect { views ->
			userViews = views.toList()
		}
	}
	
	// Load aggregated libraries
	val multiServerRepository = koinInject<MultiServerRepository>()
	var aggregatedLibraries by remember { mutableStateOf<List<AggregatedLibrary>>(emptyList()) }
	
	LaunchedEffect(Unit) {
		if (enableMultiServer) {
			withContext(Dispatchers.IO) {
				aggregatedLibraries = multiServerRepository.getAggregatedLibraries()
			}
		}
	}
	
	// Track if sidebar is expanded (has focus)
	var isExpanded by remember { mutableStateOf(false) }
	
	CollapsibleSidebarContent(
		isExpanded = isExpanded,
		onExpandedChange = { isExpanded = it },
		activeButton = activeButton,
		activeLibraryId = activeLibraryId,
		userImageUrl = userImageUrl,
		userName = currentUser?.name ?: "User",
		activity = activity,
		sessionRepository = sessionRepository,
		mediaManager = mediaManager,
		userViews = userViews,
		aggregatedLibraries = aggregatedLibraries,
		enableMultiServer = enableMultiServer,
		shuffleContentType = shuffleContentType,
		showShuffleButton = showShuffleButton,
		showGenresButton = showGenresButton,
		showFavoritesButton = showFavoritesButton,
		showLibrariesInToolbar = showLibrariesInToolbar,
		jellyseerrEnabled = jellyseerrEnabled,
		syncPlayEnabled = syncPlayEnabled,
		enableFolderView = enableFolderView,
		clockBehavior = clockBehavior,
	)
}

@Composable
private fun CollapsibleSidebarContent(
	isExpanded: Boolean,
	onExpandedChange: (Boolean) -> Unit,
	activeButton: MainToolbarActiveButton,
	activeLibraryId: UUID?,
	userImageUrl: String?,
	userName: String,
	activity: android.app.Activity?,
	sessionRepository: SessionRepository,
	mediaManager: MediaManager,
	userViews: List<BaseItemDto> = emptyList(),
	aggregatedLibraries: List<AggregatedLibrary> = emptyList(),
	enableMultiServer: Boolean = false,
	shuffleContentType: String = "both",
	showShuffleButton: Boolean = true,
	showGenresButton: Boolean = true,
	showFavoritesButton: Boolean = true,
	showLibrariesInToolbar: Boolean = true,
	jellyseerrEnabled: Boolean = false,
	syncPlayEnabled: Boolean = false,
	enableFolderView: Boolean = false,
	clockBehavior: ClockBehavior = ClockBehavior.ALWAYS,
) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val navigationRepository = koinInject<NavigationRepository>()
	val itemLauncher = koinInject<ItemLauncher>()
	val settingsViewModel = koinActivityViewModel<SettingsViewModel>()
	val syncPlayViewModel = koinActivityViewModel<SyncPlayViewModel>()
	val apiClient = koinInject<ApiClient>()
	val apiClientFactory = koinInject<ApiClientFactory>()
	val shuffleManager = koinInject<ShuffleManager>()
	val themeMusicPlayer = koinInject<ThemeMusicPlayer>()
	
	var showShuffleDialog by remember { mutableStateOf(false) }
	val isShuffling by shuffleManager.isShuffling.collectAsState()
	val showShuffle = shuffleContentType != "disabled" && showShuffleButton
	
	val homeIcon = ImageVector.vectorResource(R.drawable.ic_house)
	val searchIcon = ImageVector.vectorResource(R.drawable.ic_search)
	val shuffleIcon = ImageVector.vectorResource(R.drawable.ic_shuffle)
	val genresIcon = ImageVector.vectorResource(R.drawable.ic_masks)
	val favoritesIcon = ImageVector.vectorResource(R.drawable.ic_heart)
	val jellyseerrIcon = ImageVector.vectorResource(R.drawable.ic_jellyseerr_jellyfish)
	val syncplayIcon = ImageVector.vectorResource(R.drawable.ic_syncplay)
	val librariesIcon = ImageVector.vectorResource(R.drawable.ic_clapperboard)
	val settingsIcon = ImageVector.vectorResource(R.drawable.ic_settings)
	
	val sidebarWidth by animateDpAsState(
		targetValue = if (isExpanded) 280.dp else 56.dp,
		label = "sidebarWidth"
	)
	
	val expandedBackground = Brush.horizontalGradient(
		colors = listOf(
			Color.Black.copy(alpha = 0.9f),
			Color.Black.copy(alpha = 0.7f),
			Color.Transparent
		)
	)
	
	val scrollState = rememberScrollState()
	
	val homeFocusRequester = remember { FocusRequester() }
	
	var librariesHasFocus by remember { mutableStateOf(false) }
	
	LaunchedEffect(isExpanded) {
		if (isExpanded) {
			themeMusicPlayer.stop()
			scrollState.animateScrollTo(0)
			homeFocusRequester.requestFocus()
		}
	}

	// Get root view to check for rowsFragment (HomeFragment)
	val rootView = LocalView.current.rootView
	val isHomeFragment = rootView.findViewById<android.view.View?>(R.id.rowsFragment) != null
	val isSearchFragment = activeButton == MainToolbarActiveButton.Search

	// Show clock in top right based on clockBehavior preference
	val showClock = clockBehavior == ClockBehavior.ALWAYS || clockBehavior == ClockBehavior.IN_MENUS

	// Parent Box to contain both sidebar and clock
	Box(
		modifier = Modifier
			.fillMaxHeight()
			.fillMaxWidth()
	) {
		// Sidebar Box
		Box(
			modifier = Modifier
			.fillMaxHeight()
			.width(sidebarWidth)
			.clipToBounds()
			.then(
				if (isExpanded) {
					Modifier.background(expandedBackground)
				} else {
					Modifier
				}
			)
			.then(
				// Add key handler for HomeFragment (media bar case) and SearchFragment
				if (isHomeFragment || isSearchFragment) {
					Modifier.onKeyEvent { keyEvent ->
						if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
							keyEvent.key == Key.DirectionRight) {
							when {
								isHomeFragment -> {
									// Navigate back to rowsFragment
									val rowsFragment = rootView.findViewById<android.view.View?>(R.id.rowsFragment)
									if (rowsFragment != null) {
										rowsFragment.requestFocus()
										true
									} else {
										false
									}
								}
								isSearchFragment -> {
									// For SearchFragment, find any VerticalGridView (search results) or focusable view
									val contentView = rootView.findViewById<android.view.ViewGroup?>(android.R.id.content)
									var focusTarget: android.view.View? = null
									
									// Try to find a VerticalGridView (search results)
									contentView?.let { parent ->
										fun findVerticalGridView(view: android.view.View): android.view.View? {
											if (view is androidx.leanback.widget.VerticalGridView) return view
											if (view is android.view.ViewGroup) {
												for (i in 0 until view.childCount) {
													val result = findVerticalGridView(view.getChildAt(i))
													if (result != null) return result
												}
											}
											return null
										}
										focusTarget = findVerticalGridView(parent)
									}
									
									// If no VerticalGridView found, try any focusable view to the right
									if (focusTarget == null) {
										focusTarget = contentView?.focusSearch(android.view.View.FOCUS_RIGHT)
									}
									
									if (focusTarget != null) {
										focusTarget.requestFocus()
										true
									} else {
										false
									}
								}
								else -> false
							}
						} else {
							false
						}
					}
				} else {
					Modifier
				}
			)
			.onFocusChanged { focusState ->
				onExpandedChange(focusState.hasFocus)
			}
	) {
		Column(
			modifier = Modifier
				.fillMaxHeight()
				.padding(vertical = 16.dp, horizontal = 8.dp),
		) {
			Column {
				SidebarIconItem(
					icon = null,
					imageUrl = userImageUrl,
					label = userName,
					showLabel = isExpanded,
					isExpanded = isExpanded,
					onClick = {
						if (activeButton != MainToolbarActiveButton.User) {
							mediaManager.clearAudioQueue()
							sessionRepository.destroyCurrentSession()
							activity?.startActivity(ActivityDestinations.startup(activity))
							activity?.finishAfterTransition()
						}
					}
				)
			}

			Column(
				modifier = Modifier
					.weight(1f)
					.verticalScroll(scrollState),
				horizontalAlignment = Alignment.Start
			) {
				SidebarIconItem(
					icon = homeIcon,
					label = context.getString(R.string.lbl_home),
					showLabel = isExpanded,
					isExpanded = isExpanded,
					focusRequester = homeFocusRequester,
					onClick = {
						navigationRepository.navigate(Destinations.home)
					}
				)

				Spacer(modifier = Modifier.height(2.dp))

				SidebarIconItem(
					icon = searchIcon,
					label = context.getString(R.string.lbl_search),
					showLabel = isExpanded,
					isExpanded = isExpanded,
					onClick = {
						navigationRepository.navigate(Destinations.search())
					}
				)

				Spacer(modifier = Modifier.height(2.dp))

				if (showShuffle) {
					SidebarIconItem(
						icon = shuffleIcon,
						label = if (isShuffling) "..." else "Shuffle",
						showLabel = isExpanded,
						isExpanded = isExpanded,
						onClick = {
							if (!isShuffling) {
								kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
									shuffleManager.quickShuffle(context)
								}
							}
						},
						onLongClick = { showShuffleDialog = true }
					)
					Spacer(modifier = Modifier.height(2.dp))
				}

				if (showGenresButton) {
					SidebarIconItem(
						icon = genresIcon,
						label = context.getString(R.string.lbl_genres),
						showLabel = isExpanded,
						isExpanded = isExpanded,
						onClick = {
							navigationRepository.navigate(Destinations.allGenres)
						}
					)
					Spacer(modifier = Modifier.height(2.dp))
				}

				if (showFavoritesButton) {
					SidebarIconItem(
						icon = favoritesIcon,
						label = context.getString(R.string.lbl_favorites),
						showLabel = isExpanded,
						isExpanded = isExpanded,
						onClick = {
							navigationRepository.navigate(Destinations.allFavorites)
						}
					)
					Spacer(modifier = Modifier.height(2.dp))
				}

				if (jellyseerrEnabled) {
					SidebarIconItem(
						icon = jellyseerrIcon,
						label = "Jellyseerr",
						showLabel = isExpanded,
						isExpanded = isExpanded,
						onClick = {
							navigationRepository.navigate(Destinations.jellyseerrDiscover)
						}
					)
					Spacer(modifier = Modifier.height(2.dp))
				}

				if (enableFolderView) {
					SidebarIconItem(
						icon = ImageVector.vectorResource(R.drawable.ic_folder),
						label = context.getString(R.string.lbl_folders),
						showLabel = isExpanded,
						isExpanded = isExpanded,
						onClick = {
							navigationRepository.navigate(Destinations.folderView)
						}
					)
					Spacer(modifier = Modifier.height(2.dp))
				}

				if (syncPlayEnabled) {
					SidebarIconItem(
						icon = syncplayIcon,
						label = context.getString(R.string.syncplay),
						showLabel = isExpanded,
						isExpanded = isExpanded,
						onClick = {
							syncPlayViewModel.show()
						}
					)
					Spacer(modifier = Modifier.height(2.dp))
				}

				if (showLibrariesInToolbar) {
					SidebarIconItem(
						icon = librariesIcon,
						label = "Libraries",
						showLabel = isExpanded,
						isExpanded = isExpanded,
						onFocusChanged = { hasFocus ->
							librariesHasFocus = hasFocus
						},
						onClick = {
							// Always navigate to first library when clicking Libraries button
							if (enableMultiServer && aggregatedLibraries.isNotEmpty()) {
								val firstLib = aggregatedLibraries.first()
								scope.launch {
									val destination = when (firstLib.library.collectionType) {
										CollectionType.LIVETV, CollectionType.MUSIC -> {
											itemLauncher.getUserViewDestination(firstLib.library)
										}
										else -> {
											Destinations.libraryBrowser(firstLib.library, firstLib.server.id, firstLib.userId)
										}
									}
									navigationRepository.navigate(destination)
								}
							} else if (userViews.isNotEmpty()) {
								val firstLib = userViews.first()
								val destination = itemLauncher.getUserViewDestination(firstLib)
								navigationRepository.navigate(destination)
							}
						}
					)
					
					if (isExpanded && librariesHasFocus) {
						if (enableMultiServer && aggregatedLibraries.isNotEmpty()) {
							aggregatedLibraries.forEach { aggLib ->
								SidebarTextItem(
									label = aggLib.displayName,
									onFocusChanged = { hasFocus ->
										if (hasFocus) librariesHasFocus = true
									},
									onClick = {
										scope.launch {
											val destination = when (aggLib.library.collectionType) {
												CollectionType.LIVETV, CollectionType.MUSIC -> {
													itemLauncher.getUserViewDestination(aggLib.library)
												}
												else -> {
													Destinations.libraryBrowser(aggLib.library, aggLib.server.id, aggLib.userId)
												}
											}
											navigationRepository.navigate(destination)
										}
									}
								)
							}
						} else {
							userViews.forEach { library ->
								SidebarTextItem(
									label = library.name ?: "",
									onFocusChanged = { hasFocus ->
										if (hasFocus) librariesHasFocus = true
									},
									onClick = {
										val destination = itemLauncher.getUserViewDestination(library)
										navigationRepository.navigate(destination)
									}
								)
							}
						}
					}
					Spacer(modifier = Modifier.height(2.dp))
				}
			}

			Column {
				SidebarIconItem(
					icon = settingsIcon,
					label = "Settings",
					showLabel = isExpanded,
					isExpanded = isExpanded,
					onClick = {
						scope.launch {
							settingsViewModel.show()
						}
					}
				)
			}
		}
	}
		
		// Clock display in top right corner of screen
		if (showClock) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 24.dp, end = 32.dp)
					.align(Alignment.TopEnd)
			) {
				Box(
					modifier = Modifier.fillMaxWidth(),
					contentAlignment = Alignment.TopEnd
				) {
					ToolbarClock()
				}
			}
		}
	} // End of parent Box
	
	if (showShuffleDialog) {
		ShuffleOptionsDialog(
			userViews = userViews,
			aggregatedLibraries = aggregatedLibraries,
			enableMultiServer = enableMultiServer,
			shuffleContentType = shuffleContentType,
			api = apiClient,
			onDismiss = { showShuffleDialog = false },
			onShuffle = { libraryId, serverId, genreName, contentType, libraryCollectionType ->
				showShuffleDialog = false
				kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
					shuffleManager.shuffle(
						context = context,
						libraryId = libraryId,
						serverId = serverId,
						genreName = genreName,
						contentType = contentType,
						libraryCollectionType = libraryCollectionType
					)
				}
			}
		)
	}

	// SyncPlay dialog - hosted here instead of a separate activity-level ComposeView overlay
	val syncPlayVisible by syncPlayViewModel.visible.collectAsState()
	if (syncPlayVisible) {
		SyncPlayDialog(
			visible = true,
			onDismissRequest = { syncPlayViewModel.hide() }
		)
	}
}

@Composable
private fun SidebarIconItem(
	icon: ImageVector?,
	imageUrl: String? = null,
	label: String,
	showLabel: Boolean,
	isExpanded: Boolean,
	focusRequester: FocusRequester? = null,
	onFocusChanged: ((Boolean) -> Unit)? = null,
	onClick: () -> Unit,
	onLongClick: (() -> Unit)? = null
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()
	val scope = rememberCoroutineScope()
	var longPressJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
	var longPressTriggered by remember { mutableStateOf(false) }
	
	LaunchedEffect(isFocused) {
		onFocusChanged?.invoke(isFocused)
	}
	
	val focusedColor = Color(0xFF00A4DC)
	val iconAlpha by animateFloatAsState(
		targetValue = if (isExpanded || imageUrl != null) 1f else 0.5f,
		label = "iconAlpha"
	)
	val iconColor = Color.White.copy(alpha = iconAlpha)
	val textColor = Color.White

	// Delay label appearance until sidebar width animation has partially completed
	var delayedShowLabel by remember { mutableStateOf(showLabel) }
	LaunchedEffect(showLabel) {
		if (showLabel) {
			delay(150)
			delayedShowLabel = true
		} else {
			// Instantly hide labels before width starts shrinking
			delayedShowLabel = false
		}
	}

	Row(
		modifier = Modifier
			.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
			.then(
				if (isFocused) {
					Modifier
						.border(2.dp, focusedColor, RoundedCornerShape(24.dp))
						.padding(horizontal = 4.dp)
				} else {
					Modifier.padding(horizontal = 4.dp)
				}
			)
			.focusable(interactionSource = interactionSource)
			.onKeyEvent { keyEvent ->
				if (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) {
					when (keyEvent.nativeKeyEvent.action) {
						android.view.KeyEvent.ACTION_DOWN -> {
							if (longPressJob == null && onLongClick != null) {
								longPressTriggered = false
								longPressJob = scope.launch {
									delay(500L)
									longPressTriggered = true
									onLongClick()
								}
							}
							true
						}
						android.view.KeyEvent.ACTION_UP -> {
							longPressJob?.cancel()
							longPressJob = null
							if (!longPressTriggered) {
								onClick()
							}
							longPressTriggered = false
							true
						}
						else -> false
					}
				} else {
					false
				}
			}
			.combinedClickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
				onLongClick = onLongClick
			)
			.padding(vertical = 6.dp, horizontal = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Box(
			modifier = Modifier.size(32.dp),
			contentAlignment = Alignment.Center
		) {
			if (imageUrl != null) {
				AsyncImage(
					model = imageUrl,
					contentDescription = label,
					modifier = Modifier
						.size(32.dp)
						.clip(CircleShape),
					contentScale = ContentScale.Crop
				)
			} else if (icon != null) {
				Icon(
					imageVector = icon,
					contentDescription = label,
					modifier = Modifier.size(24.dp),
					tint = iconColor
				)
			}
		}
		
		if (delayedShowLabel) {
			Row {
				Spacer(modifier = Modifier.width(12.dp))
				Text(
					text = label,
					color = textColor,
					fontSize = 16.sp
				)
				Spacer(modifier = Modifier.width(8.dp))
			}
		}
	}
}

@Composable
private fun SidebarTextItem(
	label: String,
	onFocusChanged: ((Boolean) -> Unit)? = null,
	onClick: () -> Unit
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isFocused by interactionSource.collectIsFocusedAsState()
	
	LaunchedEffect(isFocused) {
		onFocusChanged?.invoke(isFocused)
	}
	
	val focusedColor = Color(0xFF00A4DC)
	val textColor = Color.White

	Row(
		modifier = Modifier
			.focusable(interactionSource = interactionSource)
			.onKeyEvent { keyEvent ->
				if (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) {
					when (keyEvent.nativeKeyEvent.action) {
						android.view.KeyEvent.ACTION_UP -> {
							onClick()
							true
						}
						android.view.KeyEvent.ACTION_DOWN -> true
						else -> false
					}
				} else {
					false
				}
			}
			.then(
				if (isFocused) {
					Modifier
						.border(2.dp, focusedColor, RoundedCornerShape(24.dp))
						.padding(horizontal = 4.dp)
				} else {
					Modifier.padding(horizontal = 4.dp)
				}
			)
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick
			)
			.padding(vertical = 6.dp, horizontal = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Spacer(modifier = Modifier.width(48.dp))
		Text(
			text = label,
			color = textColor,
			fontSize = 16.sp
		)
		Spacer(modifier = Modifier.width(8.dp))
	}
}
