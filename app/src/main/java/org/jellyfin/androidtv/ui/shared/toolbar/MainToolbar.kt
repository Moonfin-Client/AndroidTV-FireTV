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
import androidx.compose.runtime.remember
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
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.ShuffleContentType
import org.jellyfin.androidtv.data.repository.UserViewsRepository
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
import org.jellyfin.preference.store.PreferenceStore
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
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
	val api = koinInject<ApiClient>()
	val userViewsRepository = koinInject<UserViewsRepository>()
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
	var shuffleContentType by remember { mutableStateOf("both") }
	LaunchedEffect(Unit) {
		showShuffleButton = userPreferences[UserPreferences.showShuffleButton] ?: true
		showGenresButton = userPreferences[UserPreferences.showGenresButton] ?: true
		showFavoritesButton = userPreferences[UserPreferences.showFavoritesButton] ?: true
		showLibrariesInToolbar = userPreferences[UserPreferences.showLibrariesInToolbar] ?: true
		shuffleContentType = userPreferences[UserPreferences.shuffleContentType] ?: "both"
	}

	// Load user views/libraries
	var userViews by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
	LaunchedEffect(Unit) {
		userViewsRepository.views.collect { views ->
			// Filter out playlists from toolbar
			userViews = views.filter { it.collectionType != CollectionType.PLAYLISTS }.toList()
		}
	}

	MainToolbar(
		userImage = userImage,
		activeButton = activeButton,
		activeLibraryId = activeLibraryId,
		userViews = userViews,
		jellyseerrEnabled = jellyseerrEnabled,
		showShuffleButton = showShuffleButton,
		showGenresButton = showGenresButton,
		showFavoritesButton = showFavoritesButton,
		showLibrariesInToolbar = showLibrariesInToolbar,
		shuffleContentType = shuffleContentType,
	)
}

@Composable
private fun MainToolbar(
	userImage: String? = null,
	activeButton: MainToolbarActiveButton,
	activeLibraryId: UUID? = null,
	userViews: List<BaseItemDto> = emptyList(),
	jellyseerrEnabled: Boolean = false,
	showShuffleButton: Boolean = true,
	showGenresButton: Boolean = true,
	showFavoritesButton: Boolean = true,
	showLibrariesInToolbar: Boolean = true,
	shuffleContentType: String = "both",
) {
	val focusRequester = remember { FocusRequester() }
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	val navigationRepository = koinInject<NavigationRepository>()
	val mediaManager = koinInject<MediaManager>()
	val sessionRepository = koinInject<SessionRepository>()
	val itemLauncher = koinInject<ItemLauncher>()
	val api = koinInject<ApiClient>()
	val settingsViewModel = koinActivityViewModel<SettingsViewModel>()
	val activity = LocalActivity.current
	val context = LocalContext.current
	val scope = rememberCoroutineScope()

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
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				val userImagePainter = rememberAsyncImagePainter(userImage)
				val userImageState by userImagePainter.state.collectAsState()
				val userImageVisible = userImageState is AsyncImagePainter.State.Success

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
							painter = userImagePainter,
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
				IconButton(
					onClick = {
						if (activeButton != MainToolbarActiveButton.Home) {
							navigationRepository.reset(Destinations.home)
						}
					},
					colors = if (activeButton == MainToolbarActiveButton.Home) activeButtonColors else toolbarButtonColors,
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_house),
						contentDescription = stringResource(R.string.lbl_home),
					)
				}

				IconButton(
					onClick = {
						if (activeButton != MainToolbarActiveButton.Search) {
							navigationRepository.navigate(Destinations.search())
						}
					},
					colors = if (activeButton == MainToolbarActiveButton.Search) activeButtonColors else toolbarButtonColors,
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_search),
						contentDescription = stringResource(R.string.lbl_search),
					)
				}

			// Shuffle button (conditional)
			if (showShuffleButton) {
				IconButton(
					onClick = {
						scope.launch {
							try {
								// Fetch random movie or TV show based on preference
								// Note: We explicitly include only MOVIE and/or SERIES to exclude collections (BOX_SET)
								val includeTypes = when (shuffleContentType) {
									"movies" -> setOf(BaseItemKind.MOVIE)
									"tv" -> setOf(BaseItemKind.SERIES)
									else -> setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
								}

								val randomItem = withContext(Dispatchers.IO) {
									val response = api.itemsApi.getItems(
										includeItemTypes = includeTypes,
										recursive = true,
										sortBy = setOf(ItemSortBy.RANDOM),
										filters = setOf(ItemFilter.IS_NOT_FOLDER),
										limit = 1,
									)
									response.content.items?.firstOrNull()
								}

								if (randomItem != null) {
									navigationRepository.navigate(Destinations.itemDetails(randomItem.id))
								} else {
									Timber.w("No random item found")
								}
							} catch (e: Exception) {
								Timber.e(e, "Failed to fetch random item")
							}
						}
					},
					colors = toolbarButtonColors,
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_shuffle),
						contentDescription = stringResource(R.string.lbl_shuffle_all),
					)
				}
			}

			// Genres button (conditional)
			if (showGenresButton) {
				IconButton(
					onClick = {
						navigationRepository.navigate(Destinations.allGenres)
					},
					colors = toolbarButtonColors,
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_masks),
						contentDescription = stringResource(R.string.lbl_genres),
					)
				}
			}

			// Favorites button (conditional)
			if (showFavoritesButton) {
				IconButton(
					onClick = {
						navigationRepository.navigate(Destinations.allFavorites)
					},
					colors = toolbarButtonColors,
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_heart),
						contentDescription = stringResource(R.string.lbl_favorites),
					)
				}
			}

		if (jellyseerrEnabled) {
			IconButton(
				onClick = {
					if (activeButton != MainToolbarActiveButton.Jellyseerr) {
						navigationRepository.navigate(Destinations.jellyseerrDiscover)
					} else {
						val fragmentActivity = activity as? androidx.fragment.app.FragmentActivity
						fragmentActivity?.supportFragmentManager?.popBackStack()
					}
				},
				colors = if (activeButton == MainToolbarActiveButton.Jellyseerr) activeButtonColors else toolbarButtonColors,
			) {
				Icon(
					imageVector = ImageVector.vectorResource(R.drawable.ic_jellyseerr_jellyfish),
					contentDescription = "Jellyseerr Discover",
				)
			}
		}
			
			// Settings button
			IconButton(
				onClick = {
					settingsViewModel.show()
				},
				colors = toolbarButtonColors,
			) {
				Icon(
					imageVector = ImageVector.vectorResource(R.drawable.ic_settings),
					contentDescription = stringResource(R.string.lbl_settings),
				)
			}
			
			// Dynamic library buttons (conditional)
			if (showLibrariesInToolbar) {
				ProvideTextStyle(JellyfinTheme.typography.default.copy(fontWeight = FontWeight.Bold)) {
					userViews.forEach { library ->
					val isActiveLibrary = activeButton == MainToolbarActiveButton.Library &&
						activeLibraryId == library.id
											Button(
							onClick = {
								if (!isActiveLibrary) {
									val destination = itemLauncher.getUserViewDestination(library)
									navigationRepository.navigate(destination)
								}
							},
							colors = if (isActiveLibrary) activeButtonColors else toolbarButtonColors,
							content = { Text(library.name ?: "") }
						)
					}
				}
			}
		}
		},
		end = {
		Row(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			IconButton(
				onClick = {
					settingsViewModel.show()
				},
				colors = ButtonDefaults.colors(
					containerColor = Color.Transparent,
					focusedContainerColor = JellyfinTheme.colorScheme.buttonFocused,
				),
			) {
				Icon(
					imageVector = ImageVector.vectorResource(R.drawable.ic_settings),
					contentDescription = stringResource(R.string.lbl_settings),
				)
			}

			ToolbarClock()
		}
	}
	)
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
