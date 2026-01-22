package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.Session
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.model.AggregatedLibrary
import org.jellyfin.androidtv.data.repository.MultiServerRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.util.sdk.ApiClientFactory
import org.jellyfin.androidtv.ui.NowPlayingComposable
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.base.button.IconButton
import org.jellyfin.androidtv.ui.base.button.IconButtonDefaults
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.preference.JellyseerrPreferences
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.jellyfin.androidtv.ui.shuffle.ShuffleOptionsDialog
import org.jellyfin.androidtv.ui.shuffle.executeShuffle
import org.jellyfin.androidtv.ui.syncplay.SyncPlayViewModel
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel
import org.koin.core.qualifier.named
import timber.log.Timber
import java.util.UUID

enum class MainToolbarActiveButton {
	User,
	Home,
	Library,
	Search,
	Jellyseerr,

	None,
}


@Composable
fun MainToolbar(
	activeButton: MainToolbarActiveButton = MainToolbarActiveButton.None,
	activeLibraryId: UUID? = null,
) {
	val context = LocalContext.current
	val userRepository = koinInject<UserRepository>()
	val settingsViewModel = koinActivityViewModel<SettingsViewModel>()
	val settingsClosedCounter by settingsViewModel.settingsClosedCounter.collectAsState()
	val api = koinInject<ApiClient>()
	val userViewsRepository = koinInject<UserViewsRepository>()
	val multiServerRepository = koinInject<org.jellyfin.androidtv.data.repository.MultiServerRepository>()
	val sessionRepository = koinInject<org.jellyfin.androidtv.auth.repository.SessionRepository>()
	val jellyseerrPreferences = koinInject<JellyseerrPreferences>(named("global"))
	val userPreferences = koinInject<UserPreferences>()
	val imageLoader = koinInject<coil3.ImageLoader>()
	val scope = rememberCoroutineScope()

	// Prevent user image to disappear when signing out by skipping null values
	val currentUser by remember { userRepository.currentUser.filterNotNull() }.collectAsState(null)
	val userImage = remember(currentUser) { currentUser?.primaryImage?.getUrl(api) }

	// Preload user image into cache as soon as we have the URL
	LaunchedEffect(userImage) {
		if (userImage != null) {
			withContext(Dispatchers.IO) {
				val request = coil3.request.ImageRequest.Builder(context)
					.data(userImage)
					.memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
					.diskCachePolicy(coil3.request.CachePolicy.ENABLED)
					.build()
				imageLoader.execute(request)
			}
		}
	}

	var jellyseerrEnabled by remember { mutableStateOf(false) }
	LaunchedEffect(currentUser) {
		// Check if Jellyseerr is globally enabled
		val globalEnabled = jellyseerrPreferences[JellyseerrPreferences.enabled]
		if (globalEnabled && currentUser != null) {
			// Check if current user has enabled Jellyseerr in toolbar
			val userJellyseerrPrefs = JellyseerrPreferences(context = context, userId = currentUser!!.id.toString())
			val showInToolbar = userJellyseerrPrefs[JellyseerrPreferences.showInToolbar]
			// Show icon based purely on user preference
			jellyseerrEnabled = showInToolbar
		} else {
			jellyseerrEnabled = false
		}
	}

	// Load toolbar customization preferences
	var showShuffleButton by remember { mutableStateOf(true) }
	var showGenresButton by remember { mutableStateOf(true) }
	var showFavoritesButton by remember { mutableStateOf(true) }
	var showLibrariesInToolbar by remember { mutableStateOf(true) }
	var syncPlayEnabled by remember { mutableStateOf(false) }
	var enableMultiServer by remember { mutableStateOf(false) }
	var shuffleContentType by remember { mutableStateOf("both") }
	LaunchedEffect(settingsClosedCounter) {
		showShuffleButton = userPreferences[UserPreferences.showShuffleButton] ?: true
		showGenresButton = userPreferences[UserPreferences.showGenresButton] ?: true
		showFavoritesButton = userPreferences[UserPreferences.showFavoritesButton] ?: true
		showLibrariesInToolbar = userPreferences[UserPreferences.showLibrariesInToolbar] ?: true
		syncPlayEnabled = userPreferences[UserPreferences.syncPlayEnabled] ?: false
		enableMultiServer = userPreferences[UserPreferences.enableMultiServerLibraries] ?: false
		shuffleContentType = userPreferences[UserPreferences.shuffleContentType] ?: "both"
	}

	// Load user views/libraries
	var userViews by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
	LaunchedEffect(Unit) {
		userViewsRepository.views.collect { views ->
			userViews = views.toList()
		}
	}
	
	// Load aggregated libraries from all servers
	val aggregationScope = rememberCoroutineScope()
	var aggregatedLibraries by remember { mutableStateOf<List<org.jellyfin.androidtv.data.model.AggregatedLibrary>>(emptyList()) }
	LaunchedEffect(enableMultiServer) {
		if (enableMultiServer) {
			aggregationScope.launch(Dispatchers.IO) {
				try {
					aggregatedLibraries = multiServerRepository.getAggregatedLibraries()
				} catch (e: Exception) {
				}
			}
		}
	}
	
	// Track current session for server switching
	val currentSession by sessionRepository.currentSession.collectAsState()

	MainToolbar(
		userImage = userImage,
		activeButton = activeButton,
		activeLibraryId = activeLibraryId,
		userViews = userViews,
		aggregatedLibraries = aggregatedLibraries,
		enableMultiServer = enableMultiServer,
		currentSession = currentSession,
		jellyseerrEnabled = jellyseerrEnabled,
		showShuffleButton = showShuffleButton,
		showGenresButton = showGenresButton,
		showFavoritesButton = showFavoritesButton,
		showLibrariesInToolbar = showLibrariesInToolbar,
		syncPlayEnabled = syncPlayEnabled,
		shuffleContentType = shuffleContentType,
	)
}

@Composable
private fun MainToolbar(
	userImage: String? = null,
	activeButton: MainToolbarActiveButton,
	activeLibraryId: UUID? = null,
	userViews: List<BaseItemDto> = emptyList(),
	aggregatedLibraries: List<org.jellyfin.androidtv.data.model.AggregatedLibrary> = emptyList(),
	enableMultiServer: Boolean = false,
	currentSession: Session? = null,
	jellyseerrEnabled: Boolean = false,
	showShuffleButton: Boolean = true,
	showGenresButton: Boolean = true,
	showFavoritesButton: Boolean = true,
	showLibrariesInToolbar: Boolean = true,
	syncPlayEnabled: Boolean = false,
	shuffleContentType: String = "both",
) {
	val focusRequester = remember { FocusRequester() }
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	val navigationRepository = koinInject<NavigationRepository>()
	val mediaManager = koinInject<MediaManager>()
	val sessionRepository = koinInject<SessionRepository>()
	val itemLauncher = koinInject<ItemLauncher>()
	val api = koinInject<ApiClient>()
	val apiClientFactory = koinInject<ApiClientFactory>()
	val settingsViewModel = koinActivityViewModel<SettingsViewModel>()
	val syncPlayViewModel = koinActivityViewModel<SyncPlayViewModel>()
	val activity = LocalActivity.current
	val context = LocalContext.current
	val scope = rememberCoroutineScope()

	var showShuffleDialog by remember { mutableStateOf(false) }

	val activeButtonColors = ButtonDefaults.colors(
		containerColor = JellyfinTheme.colorScheme.buttonActive,
		contentColor = JellyfinTheme.colorScheme.onButtonActive,
	)

	val toolbarButtonColors = ButtonDefaults.colors(
		containerColor = Color.Transparent,
		contentColor = JellyfinTheme.colorScheme.onButton,
		focusedContainerColor = JellyfinTheme.colorScheme.buttonFocused,
		focusedContentColor = JellyfinTheme.colorScheme.onButtonFocused,
	)

	// Get overlay preferences for toolbar styling
	val overlayOpacity = userSettingPreferences[UserSettingPreferences.mediaBarOverlayOpacity] / 100f
	val overlayColor = when (userSettingPreferences[UserSettingPreferences.mediaBarOverlayColor]) {
		"black" -> Color.Black
		"dark_blue" -> Color(0xFF1A2332)
		"purple" -> Color(0xFF4A148C)
		"teal" -> Color(0xFF00695C)
		"navy" -> Color(0xFF0D1B2A)
		"charcoal" -> Color(0xFF36454F)
		"brown" -> Color(0xFF3E2723)
		"dark_red" -> Color(0xFF8B0000)
		"dark_green" -> Color(0xFF0B4F0F)
		"slate" -> Color(0xFF475569)
		"indigo" -> Color(0xFF1E3A8A)
		else -> Color.Gray
	}

	Toolbar(
		modifier = Modifier
			.focusRestorer(focusRequester)
			.focusGroup(),
		start = {
			Row(
				horizontalArrangement = Arrangement.spacedBy(4.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				val userImagePainter = userImage?.let { rememberAsyncImagePainter(it) }
				val userImageState by userImagePainter?.state?.collectAsState()
					?: remember { mutableStateOf<AsyncImagePainter.State?>(null) }
				val userImageVisible = userImagePainter != null && userImageState is AsyncImagePainter.State.Success

				val interactionSource = remember { MutableInteractionSource() }
				val isFocused by interactionSource.collectIsFocusedAsState()
				val scale by animateFloatAsState(if (isFocused) 1.1f else 1f, label = "UserAvatarFocusScale")

				IconButton(
					onClick = {
						if (activeButton != MainToolbarActiveButton.User) {
							mediaManager.clearAudioQueue()
							sessionRepository.destroyCurrentSession()

							// Open login activity
							activity?.startActivity(ActivityDestinations.startup(activity))
							activity?.finishAfterTransition()
						}
					},
					colors = if (userImageVisible) {
						ButtonDefaults.colors(
							containerColor = Color.Transparent,
							contentColor = JellyfinTheme.colorScheme.onButton,
							focusedContainerColor = JellyfinTheme.colorScheme.buttonFocused,
							focusedContentColor = JellyfinTheme.colorScheme.onButtonFocused,
						)
					} else {
						toolbarButtonColors
					},
					contentPadding = if (userImageVisible) PaddingValues(0.dp) else IconButtonDefaults.ContentPadding,
					interactionSource = interactionSource,
					modifier = Modifier.scale(scale),
				) {
					if (!userImageVisible) {
						Icon(
							imageVector = ImageVector.vectorResource(R.drawable.ic_user),
							contentDescription = stringResource(R.string.lbl_switch_user),
						)
					} else {
						Image(
							painter = requireNotNull(userImagePainter),
							contentDescription = stringResource(R.string.lbl_switch_user),
							contentScale = ContentScale.Crop,
							modifier = Modifier
								.aspectRatio(1f)
								.border(
									width = if (isFocused) 2.dp else 0.dp,
									color = if (isFocused) JellyfinTheme.colorScheme.buttonFocused else Color.Transparent,
									shape = IconButtonDefaults.Shape
								)
								.clip(IconButtonDefaults.Shape)
						)
					}
				}

				ToolbarButtons(
					backgroundColor = overlayColor,
					alpha = overlayOpacity
				) {
					NowPlayingComposable(
						onFocusableChange = {},
					)
				}
			}
		},
		center = {
			ToolbarButtons(
				modifier = Modifier
					.focusRequester(focusRequester),
				backgroundColor = overlayColor,
				alpha = overlayOpacity
			) {
				ExpandableIconButton(
					icon = ImageVector.vectorResource(R.drawable.ic_house),
					label = stringResource(R.string.lbl_home),
					onClick = {
						navigationRepository.reset(Destinations.home)
					},
					colors = toolbarButtonColors,
				)

				ExpandableIconButton(
					icon = ImageVector.vectorResource(R.drawable.ic_search),
					label = stringResource(R.string.lbl_search),
					onClick = {
						navigationRepository.navigate(Destinations.search())
					},
					colors = toolbarButtonColors,
				)

				if (showShuffleButton) {
					ExpandableIconButton(
						icon = ImageVector.vectorResource(R.drawable.ic_shuffle),
						label = "Shuffle",
						onClick = {
							scope.launch {
								executeShuffle(
									libraryId = null,
									serverId = null,
									genreName = null,
									contentType = shuffleContentType,
									libraryCollectionType = null,
									api = api,
									apiClientFactory = apiClientFactory,
									navigationRepository = navigationRepository
								)
							}
						},
						onLongClick = { showShuffleDialog = true },
						colors = toolbarButtonColors,
					)
				}

				// Genres button (conditional)
				if (showGenresButton) {
					ExpandableIconButton(
						icon = ImageVector.vectorResource(R.drawable.ic_masks),
						label = stringResource(R.string.lbl_genres),
						onClick = {
							navigationRepository.navigate(Destinations.allGenres)
						},
						colors = toolbarButtonColors,
					)
			}

			if (showFavoritesButton) {
					ExpandableIconButton(
						icon = ImageVector.vectorResource(R.drawable.ic_heart),
						label = stringResource(R.string.lbl_favorites),
						onClick = {
							navigationRepository.navigate(Destinations.allFavorites)
						},
						colors = toolbarButtonColors,
					)
				}

				if (jellyseerrEnabled) {
					ExpandableIconButton(
						icon = ImageVector.vectorResource(R.drawable.ic_jellyseerr_jellyfish),
						label = "Jellyseerr",
						onClick = {
							navigationRepository.navigate(Destinations.jellyseerrDiscover)
						},
						colors = toolbarButtonColors,
					)
				}
				
				if (syncPlayEnabled) {
					ExpandableIconButton(
						icon = ImageVector.vectorResource(R.drawable.ic_syncplay),
						label = stringResource(R.string.syncplay),
						onClick = {
							syncPlayViewModel.show()
						},
						colors = toolbarButtonColors,
					)
				}

				if (showLibrariesInToolbar) {
					ExpandableLibrariesButton(
						activeLibraryId = activeLibraryId,
						userViews = userViews,
						aggregatedLibraries = aggregatedLibraries,
						enableMultiServer = enableMultiServer,
						currentSession = currentSession,
						colors = toolbarButtonColors,
						activeColors = activeButtonColors,
						navigationRepository = navigationRepository,
						itemLauncher = itemLauncher,
					)
				}

				ExpandableIconButton(
					icon = ImageVector.vectorResource(R.drawable.ic_settings),
					label = "Settings",
					onClick = {
						settingsViewModel.show()
					},
					colors = toolbarButtonColors,
				)
			}
		},
		end = {
			// Clock only - settings icon is already in the navbar
			ToolbarClock()
		}
	)

	if (showShuffleDialog) {
		ShuffleOptionsDialog(
			userViews = userViews,
			aggregatedLibraries = aggregatedLibraries,
			enableMultiServer = enableMultiServer,
			shuffleContentType = shuffleContentType,
			api = api,
			onDismiss = { showShuffleDialog = false },
			onShuffle = { libraryId, serverId, genreName, contentType, libraryCollectionType ->
				showShuffleDialog = false
				scope.launch {
					executeShuffle(
						libraryId = libraryId,
						serverId = serverId,
						genreName = genreName,
						contentType = contentType,
						libraryCollectionType = libraryCollectionType,
						api = api,
						apiClientFactory = apiClientFactory,
						navigationRepository = navigationRepository
					)
				}
			}
		)
	}
}

fun setupMainToolbarComposeView(
	composeView: androidx.compose.ui.platform.ComposeView,
	activeButton: MainToolbarActiveButton = MainToolbarActiveButton.None,
	activeLibraryId: UUID? = null,
) {
	composeView.setContent {
		JellyfinTheme {
			MainToolbar(
				activeButton = activeButton,
				activeLibraryId = activeLibraryId,
			)
		}
	}
}
