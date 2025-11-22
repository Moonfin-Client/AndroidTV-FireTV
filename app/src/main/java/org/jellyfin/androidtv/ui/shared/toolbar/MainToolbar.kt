package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.compose.koinInject
import timber.log.Timber
import java.util.UUID

enum class MainToolbarActiveButton {
	User,
	Home,
	Library,
	Search,

	None,
}


@Composable
fun MainToolbar(
	activeButton: MainToolbarActiveButton = MainToolbarActiveButton.None,
	activeLibraryId: UUID? = null,
) {
	val userRepository = koinInject<UserRepository>()
	val api = koinInject<ApiClient>()
	val userViewsRepository = koinInject<UserViewsRepository>()
	val scope = rememberCoroutineScope()

	// Prevent user image to disappear when signing out by skipping null values
	val currentUser by remember { userRepository.currentUser.filterNotNull() }.collectAsState(null)
	val userImage = remember(currentUser) { currentUser?.primaryImage?.getUrl(api) }
	
	// Load user views/libraries
	var userViews by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
	LaunchedEffect(Unit) {
		userViewsRepository.views.collect { views ->
			userViews = views.toList()
		}
	}

	MainToolbar(
		userImage = userImage,
		activeButton = activeButton,
		activeLibraryId = activeLibraryId,
		userViews = userViews,
	)
}

@Composable
private fun MainToolbar(
	userImage: String? = null,
	activeButton: MainToolbarActiveButton,
	activeLibraryId: UUID? = null,
	userViews: List<BaseItemDto> = emptyList(),
) {
	val focusRequester = remember { FocusRequester() }
	val navigationRepository = koinInject<NavigationRepository>()
	val mediaManager = koinInject<MediaManager>()
	val sessionRepository = koinInject<SessionRepository>()
	val itemLauncher = koinInject<ItemLauncher>()
	val api = koinInject<ApiClient>()
	val activity = LocalActivity.current
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	
	val activeButtonColors = ButtonDefaults.colors(
		containerColor = JellyfinTheme.colorScheme.buttonActive,
		contentColor = JellyfinTheme.colorScheme.onButtonActive,
	)

	Toolbar(
		modifier = Modifier
			.focusRestorer(focusRequester)
			.focusGroup(),
		start = {
			ToolbarButtons {
				val userImagePainter = rememberAsyncImagePainter(userImage)
				val userImageState by userImagePainter.state.collectAsState()
				val userImageVisible = userImageState is AsyncImagePainter.State.Success

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
					colors = if (activeButton == MainToolbarActiveButton.User) activeButtonColors else ButtonDefaults.colors(),
					contentPadding = if (userImageVisible) PaddingValues(3.dp) else IconButtonDefaults.ContentPadding,
				) {
					Image(
						painter = if (userImageVisible) userImagePainter else rememberVectorPainter(ImageVector.vectorResource(R.drawable.ic_user)),
						contentDescription = stringResource(R.string.lbl_switch_user),
						contentScale = ContentScale.Crop,
						modifier = Modifier
							.aspectRatio(1f)
							.clip(IconButtonDefaults.Shape)
					)
				}

				NowPlayingComposable(
					onFocusableChange = {},
				)
			}
		},
		center = {
			ToolbarButtons(
				modifier = Modifier
					.focusRequester(focusRequester)
			) {
				// Home button (house icon)
				IconButton(
					onClick = {
						if (activeButton != MainToolbarActiveButton.Home) {
							navigationRepository.navigate(
								Destinations.home,
								replace = true,
							)
						}
					},
					colors = if (activeButton == MainToolbarActiveButton.Home) activeButtonColors else ButtonDefaults.colors(),
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_house),
						contentDescription = stringResource(R.string.lbl_home),
					)
				}
				
				// Search button (magnifying glass icon)
				IconButton(
					onClick = {
						if (activeButton != MainToolbarActiveButton.Search) {
							navigationRepository.navigate(Destinations.search())
						}
					},
					colors = if (activeButton == MainToolbarActiveButton.Search) activeButtonColors else ButtonDefaults.colors(),
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_search),
						contentDescription = stringResource(R.string.lbl_search),
					)
				}
				
				// Random/Shuffle button
				IconButton(
					onClick = {
						scope.launch {
							try {
								// Fetch random movie or TV show
								val randomItem = withContext(Dispatchers.IO) {
									val result by api.itemsApi.getItems(
										includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
										recursive = true,
										sortBy = setOf(ItemSortBy.RANDOM),
										limit = 1,
									)
									result.items.firstOrNull()
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
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_shuffle),
						contentDescription = stringResource(R.string.lbl_shuffle_all),
					)
				}
				
				// Genres button (masks icon)
				IconButton(
					onClick = {
						navigationRepository.navigate(Destinations.allGenres)
					},
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_masks),
						contentDescription = stringResource(R.string.lbl_genres),
					)
				}
				
				// Dynamic library buttons
				ProvideTextStyle(JellyfinTheme.typography.default.copy(fontWeight = FontWeight.Bold)) {
					userViews.forEach { library ->
						val isActiveLibrary = activeButton == MainToolbarActiveButton.Library && 
							activeLibraryId == library.id
						
						Button(
							onClick = {
								if (!isActiveLibrary) {
									// Navigate to the library using ItemLauncher logic
									val destination = itemLauncher.getUserViewDestination(library)
									navigationRepository.navigate(destination)
								}
							},
							colors = if (isActiveLibrary) activeButtonColors else ButtonDefaults.colors(),
							content = { Text(library.name ?: "") }
						)
					}
				}
			}
		},
		end = {
			ToolbarButtons {
				IconButton(
					onClick = {
						activity?.startActivity(ActivityDestinations.userPreferences(activity))
					},
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
