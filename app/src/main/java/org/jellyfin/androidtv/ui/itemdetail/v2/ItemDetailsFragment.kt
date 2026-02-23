package org.jellyfin.androidtv.ui.itemdetail.v2

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import coil3.toBitmap
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.R
import android.os.Build
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.focusBorderColor
import org.jellyfin.androidtv.ui.base.CircularProgressIndicator
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.playback.PrePlaybackTrackSelector
import org.jellyfin.androidtv.ui.playlist.showAddToPlaylistDialog
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.androidtv.ui.playback.ThemeMusicPlayer
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.androidtv.util.TimeUtils
import org.jellyfin.androidtv.util.apiclient.Response
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.androidtv.util.apiclient.getLogoImage
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.androidtv.util.sdk.compat.canResume
import org.jellyfin.androidtv.util.sdk.TrailerUtils.getExternalTrailerIntent
import org.jellyfin.androidtv.util.sdk.TrailerUtils.hasPlayableTrailers
import org.jellyfin.androidtv.ui.home.mediabar.TrailerResolver
import org.jellyfin.androidtv.ui.browsing.composable.inforow.InfoRowMultipleRatings
import org.jellyfin.androidtv.ui.shared.toolbar.LeftSidebarNavigation
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.constant.NavbarPosition
import org.jellyfin.androidtv.util.BitmapBlur
import org.koin.compose.koinInject
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID

class ItemDetailsFragment : Fragment() {

	private val viewModel: ItemDetailsViewModel by viewModel()
	private val navigationRepository: NavigationRepository by inject()
	private val playbackHelper: PlaybackHelper by inject()
	private val mediaManager: MediaManager by inject()
	private val userPreferences: UserPreferences by inject()
	private val userSettingPreferences: UserSettingPreferences by inject()
	private val trackSelector: PrePlaybackTrackSelector by inject()
	private val playbackLauncher: PlaybackLauncher by inject()
	private val dataRefreshService: DataRefreshService by inject()
	private val themeMusicPlayer: ThemeMusicPlayer by inject()

	private var backdropImage: ImageView? = null
	private var gradientView: View? = null
	private var sidebarId: Int = View.NO_ID
	private var contentId: Int = View.NO_ID
	private var toolbarId: Int = View.NO_ID
	private var lastFocusedBeforeSidebar: View? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		sidebarId = View.generateViewId()
		contentId = View.generateViewId()
		toolbarId = View.generateViewId()

		val mainContainer = object : FrameLayout(requireContext()) {
			override fun dispatchKeyEvent(event: KeyEvent): Boolean {
				if (event.action == KeyEvent.ACTION_DOWN) {
					// Intercept RIGHT when focus is in sidebar to redirect to content
					if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
						val sidebar = findViewById<View>(sidebarId)
						val focused = findFocus()
						if (sidebar != null && focused != null && isDescendantOf(focused, sidebar)) {
							// Restore focus to where the user was before entering the sidebar
							val restoreTarget = lastFocusedBeforeSidebar
							if (restoreTarget != null && restoreTarget.isAttachedToWindow && restoreTarget.isFocusable) {
								restoreTarget.requestFocus()
								return true
							}
							// Fallback to content ComposeView
							val content = findViewById<View>(contentId)
							if (content != null) {
								content.requestFocus()
								return true
							}
						}
					}

					// Intercept DOWN when focus is in top toolbar to redirect to content
					if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
						val toolbar = findViewById<View>(toolbarId)
						val focused = findFocus()
						if (toolbar != null && focused != null && isDescendantOf(focused, toolbar)) {
							val restoreTarget = lastFocusedBeforeSidebar
							if (restoreTarget != null && restoreTarget.isAttachedToWindow && restoreTarget.isFocusable) {
								restoreTarget.requestFocus()
								return true
							}
							val content = findViewById<View>(contentId)
							if (content != null) {
								content.requestFocus()
								return true
							}
						}
					}
				}

				// Consume LEFT when already in sidebar so focus doesn't get trapped
				if (event.action == KeyEvent.ACTION_DOWN &&
					event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
					val sidebar = findViewById<View>(sidebarId)
					val focused = findFocus()
					if (sidebar != null && focused != null && isDescendantOf(focused, sidebar)) {
						return true
					}
				}

				// Consume UP when already in toolbar so focus doesn't get trapped
				if (event.action == KeyEvent.ACTION_DOWN &&
					event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
					val toolbar = findViewById<View>(toolbarId)
					val focused = findFocus()
					if (toolbar != null && focused != null && isDescendantOf(focused, toolbar)) {
						return true
					}
				}

				// Let children (Compose) process the event first
				val handled = super.dispatchKeyEvent(event)

				// If LEFT wasn't handled by Compose (focus is at left edge), redirect to sidebar
				// Only on fresh press (repeatCount == 0) to avoid triggering when holding left to fast-scroll
				if (!handled && event.action == KeyEvent.ACTION_DOWN &&
					event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.repeatCount == 0) {
					val sidebar = findViewById<View>(sidebarId)
					if (sidebar != null && sidebar.isVisible) {
						// Save current focus before entering sidebar
						lastFocusedBeforeSidebar = findFocus()
						sidebar.requestFocus()
						return true
					}
				}

				// If UP wasn't handled by Compose (focus is at top edge), redirect to toolbar
				// Only on fresh press (repeatCount == 0) to avoid triggering when holding up
				if (!handled && event.action == KeyEvent.ACTION_DOWN &&
					event.keyCode == KeyEvent.KEYCODE_DPAD_UP && event.repeatCount == 0) {
					val toolbar = findViewById<View>(toolbarId)
					if (toolbar != null && toolbar.isVisible) {
						lastFocusedBeforeSidebar = findFocus()
						toolbar.requestFocus()
						return true
					}
				}

				return handled
			}
		}.apply {
			layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
			)
			setBackgroundColor(AndroidColor.parseColor("#0A0A0A"))
		}

		backdropImage = ImageView(requireContext()).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT
			)
			scaleType = ImageView.ScaleType.CENTER_CROP
			alpha = 0.8f
		}
		mainContainer.addView(backdropImage)

		gradientView = View(requireContext()).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT
			)
			setBackgroundResource(R.drawable.detail_backdrop_gradient)
		}
		mainContainer.addView(gradientView)

		val contentView = ComposeView(requireContext()).apply {
			id = contentId
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT
			)
			setContent {
				JellyfinTheme {
					ItemDetailsContent()
				}
			}
		}
		mainContainer.addView(contentView)

		val navbarPosition = userPreferences[UserPreferences.navbarPosition]

		when (navbarPosition) {
			NavbarPosition.LEFT -> {
				val sidebarOverlay = ComposeView(requireContext()).apply {
					id = sidebarId
					layoutParams = FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.WRAP_CONTENT,
						FrameLayout.LayoutParams.MATCH_PARENT
					)
					setContent {
						LeftSidebarNavigation(
							activeButton = MainToolbarActiveButton.None,
						)
					}
				}
				mainContainer.addView(sidebarOverlay)
			}
			NavbarPosition.TOP -> {
				val toolbarOverlay = ComposeView(requireContext()).apply {
					id = toolbarId
					layoutParams = FrameLayout.LayoutParams(
						FrameLayout.LayoutParams.MATCH_PARENT,
						FrameLayout.LayoutParams.WRAP_CONTENT
					)
					setContent {
						MainToolbar(
							activeButton = MainToolbarActiveButton.None,
						)
					}
				}
				mainContainer.addView(toolbarOverlay)
			}
		}

		return mainContainer
	}

	private fun isDescendantOf(view: View, ancestor: View): Boolean {
		var current: android.view.ViewParent? = view.parent
		while (current != null) {
			if (current === ancestor) return true
			current = current.parent
		}
		return false
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val itemIdStr = arguments?.getString("ItemId")
		val serverIdStr = arguments?.getString("ServerId")

		val itemId = Utils.uuidOrNull(itemIdStr) ?: return
		val serverId = Utils.uuidOrNull(serverIdStr)

		viewModel.loadItem(itemId, serverId)

		viewModel.uiState
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach { uiState ->
				val item = uiState.item
				if (item != null) {
					// Play theme music for the item
					themeMusicPlayer.playThemeMusicForItem(item)

					if (item.type == BaseItemKind.PERSON || item.type == BaseItemKind.PLAYLIST) {
						backdropImage?.isVisible = false
						gradientView?.isVisible = false
					} else {
						backdropImage?.isVisible = true
						gradientView?.isVisible = true
						val backdropUrl = getBackdropUrl(item)
						if (backdropUrl != null) {
						val blurAmount = userSettingPreferences[UserSettingPreferences.detailsBackgroundBlurAmount]
						val imageLoader = coil3.SingletonImageLoader.get(requireContext())
						lifecycleScope.launch {
							val result = imageLoader.execute(
								coil3.request.ImageRequest.Builder(requireContext())
									.data(backdropUrl)
									.build()
							)
							val bitmap = result.image?.toBitmap()
							if (bitmap != null) {
								val useComposeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
								val finalBitmap: android.graphics.Bitmap = if (!useComposeBlur && blurAmount > 0) {
									BitmapBlur.blur(bitmap, blurAmount)
								} else {
									if (useComposeBlur && blurAmount > 0) {
										// On Android 12+, apply RenderEffect blur
										backdropImage?.setRenderEffect(
											android.graphics.RenderEffect.createBlurEffect(
												blurAmount.toFloat(), blurAmount.toFloat(),
												android.graphics.Shader.TileMode.CLAMP
											)
										)
									}
									bitmap
								}
								backdropImage?.setImageBitmap(finalBitmap)
								backdropImage?.alpha = 0.8f
							}
						}
					}
					}
				}
			}
			.launchIn(lifecycleScope)
	}

	override fun onDestroyView() {
		themeMusicPlayer.stop()
		super.onDestroyView()
	}

	@Composable
	private fun ItemDetailsContent() {
		val uiState by viewModel.uiState.collectAsState()
		val contentFocusRequester = remember { FocusRequester() }

		if (uiState.isLoading) {
			Box(
				modifier = Modifier.fillMaxSize(),
				contentAlignment = Alignment.Center,
			) {
				CircularProgressIndicator()
			}
		} else {
			val item = uiState.item ?: return

			when (item.type) {
				BaseItemKind.PERSON -> PersonDetailsContent(uiState, contentFocusRequester, showBackdrop = true)
				BaseItemKind.SEASON -> SeasonDetailsContent(uiState, contentFocusRequester, showBackdrop = false)
				BaseItemKind.PLAYLIST -> MainDetailsContent(uiState, contentFocusRequester, showBackdrop = true)
				else -> MainDetailsContent(uiState, contentFocusRequester, showBackdrop = false)
			}
		}
	}

	@Composable
	private fun MainDetailsContent(uiState: ItemDetailsUiState, contentFocusRequester: FocusRequester, showBackdrop: Boolean = true) {
		val item = uiState.item ?: return
		val listState = rememberLazyListState()
		val playButtonFocusRequester = remember { FocusRequester() }
		val collectionFirstItemFocusRequester = remember { FocusRequester() }
		val titleFocusRequester = contentFocusRequester

		val isEpisode = item.type == BaseItemKind.EPISODE
		val isSeries = item.type == BaseItemKind.SERIES
		val isBoxSet = item.type == BaseItemKind.BOX_SET
		val isMusicAlbum = item.type == BaseItemKind.MUSIC_ALBUM
		val isPlaylist = item.type == BaseItemKind.PLAYLIST

		val backdropUrl = getBackdropUrl(item)
		val posterUrl = getPosterUrl(item)
		val logoUrl = getLogoUrl(item)

		// Playlist rotating backdrop state
		var focusedBackdropUrl by remember { mutableStateOf<String?>(null) }
		val playlistBackdropUrls = if (isPlaylist) {
			remember(uiState.tracks) {
				uiState.tracks.mapNotNull { getBackdropUrl(it) }.distinct().take(10)
			}
		} else {
			emptyList()
		}
		var playlistBackdropIndex by remember { mutableStateOf(0) }

		if (isPlaylist) {
			LaunchedEffect(playlistBackdropUrls) {
				if (playlistBackdropUrls.size > 1) {
					while (true) {
						delay(8000)
						if (focusedBackdropUrl == null) {
							playlistBackdropIndex = (playlistBackdropIndex + 1) % playlistBackdropUrls.size
						}
					}
				}
			}
		}

		Box(modifier = Modifier.fillMaxSize()) {
			if (showBackdrop && isPlaylist) {
				val displayUrl = focusedBackdropUrl ?: playlistBackdropUrls.getOrNull(playlistBackdropIndex)
				if (displayUrl != null) {
					Crossfade(
						targetState = displayUrl,
						animationSpec = tween(1000),
						label = "playlist_backdrop_slideshow",
					) { url ->
						AsyncImage(
							model = url,
							contentDescription = null,
							modifier = Modifier
								.fillMaxSize()
								.graphicsLayer { alpha = 0.6f },
							contentScale = ContentScale.Crop,
						)
						Box(
							modifier = Modifier
								.fillMaxSize()
								.background(
									brush = androidx.compose.ui.graphics.Brush.verticalGradient(
										colors = listOf(
											Color.Black.copy(alpha = 0.3f),
											Color.Black.copy(alpha = 0.6f),
										),
									),
								),
						)
					}
				} else {
					Box(
						modifier = Modifier
							.fillMaxSize()
							.background(
								brush = androidx.compose.ui.graphics.Brush.linearGradient(
									colors = listOf(
										Color(0xFF1A1A2E),
										Color(0xFF16213E),
										Color(0xFF0F3460),
									),
								),
							),
					)
				}
			} else if (showBackdrop) {
				DetailBackdrop(imageUrl = backdropUrl, blurAmount = userSettingPreferences[UserSettingPreferences.detailsBackgroundBlurAmount])
			}

			// Track action dialog state
			var trackActionIndex by remember { mutableStateOf<Int?>(null) }

			val trackActionIndex2 = trackActionIndex
			if (trackActionIndex2 != null && trackActionIndex2 in uiState.tracks.indices) {
				val actionTrack = uiState.tracks[trackActionIndex2]
				val isPlaylist = item.type == BaseItemKind.PLAYLIST
				val canRemoveFromPlaylist = isPlaylist && item.canDelete == true
				val actions = buildList {
					// Open for non-audio items in playlists (e.g. video items)
					if (actionTrack.type != BaseItemKind.AUDIO) {
						add(TrackAction(
							label = getString(R.string.lbl_open),
							onClick = {
								navigationRepository.navigate(
									Destinations.itemDetails(actionTrack.id, viewModel.serverId)
								)
							},
						))
					}

					// Play from here plays all tracks starting from the selected index
					add(TrackAction(
						label = getString(R.string.lbl_play_from_here),
						onClick = {
							val trackIds = uiState.tracks.subList(trackActionIndex2, uiState.tracks.size).map { it.id }
							playbackHelper.retrieveAndPlay(trackIds, false, null, null, requireContext())
						},
					))

					// Play only the selected track
					add(TrackAction(
						label = getString(R.string.lbl_play),
						onClick = {
							playbackHelper.retrieveAndPlay(actionTrack.id, false, requireContext())
						},
					))

					// Add to queue - audio items only
					if (actionTrack.type == BaseItemKind.AUDIO) {
						add(TrackAction(
							label = getString(R.string.lbl_add_to_queue),
							onClick = {
								mediaManager.queueAudioItem(actionTrack)
							},
						))
					}

					// Instant mix - audio items only
					if (actionTrack.type == BaseItemKind.AUDIO) {
						add(TrackAction(
							label = getString(R.string.lbl_instant_mix),
							onClick = {
								playbackHelper.playInstantMix(requireContext(), actionTrack)
							},
						))
					}

					// Remove from playlist
					if (canRemoveFromPlaylist && actionTrack.playlistItemId != null) {
						add(TrackAction(
							label = getString(R.string.lbl_remove_from_playlist),
							onClick = {
								viewModel.removeFromPlaylist(trackActionIndex2)
							},
						))
					}
				}

				TrackActionDialog(
					trackTitle = actionTrack.name ?: "",
					actions = actions,
					onDismiss = { trackActionIndex = null },
				)
			}

			LazyColumn(
				state = listState,
				contentPadding = PaddingValues(top = 100.dp, start = 48.dp, end = 48.dp, bottom = 48.dp),
				modifier = Modifier.fillMaxSize(),
			) {
				// ---- Header + Action buttons in same item so they stay together ----
				item {
					// Focusable title area - receives initial focus so page stays at top
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.focusRequester(titleFocusRequester)
							.focusable()
							.onKeyEvent { keyEvent ->
								if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
									when (keyEvent.key) {
										Key.DirectionDown -> {
											if (isBoxSet) {
												try { collectionFirstItemFocusRequester.requestFocus() } catch (_: Exception) {}
											} else {
												try { playButtonFocusRequester.requestFocus() } catch (_: Exception) {}
											}
											true
										}
										else -> false
									}
								} else false
							},
					) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Column(
								modifier = Modifier.weight(1f).padding(end = if (posterUrl != null) 24.dp else 0.dp),
						) {
							if (isEpisode) {
								Row(verticalAlignment = Alignment.CenterVertically) {
									item.seriesName?.let { seriesName ->
										Text(
											text = seriesName,
											fontSize = 16.sp,
											fontWeight = FontWeight.W500,
											color = Color.White.copy(alpha = 0.7f),
										)
									}
									if (item.parentIndexNumber != null && item.indexNumber != null) {
										Spacer(modifier = Modifier.width(8.dp))
										Text(
											text = "S${item.parentIndexNumber} E${item.indexNumber}",
											fontSize = 13.sp,
											color = Color.White.copy(alpha = 0.5f),
											modifier = Modifier
												.background(
													Color.White.copy(alpha = 0.1f),
													JellyfinTheme.shapes.extraSmall,
												)
												.padding(horizontal = 8.dp, vertical = 2.dp),
										)
									}
								}
								Spacer(modifier = Modifier.height(8.dp))
							}

							if (logoUrl != null) {
								AsyncImage(
									model = logoUrl,
									contentDescription = item.name,
									modifier = Modifier
										.width(300.dp)
										.height(80.dp),
									contentScale = ContentScale.Fit,
									alignment = Alignment.CenterStart,
								)
							} else {
								Text(
									text = item.name ?: "",
									fontSize = 32.sp,
									fontWeight = FontWeight.W700,
									color = Color.White,
									maxLines = 2,
									overflow = TextOverflow.Ellipsis,
									lineHeight = 38.sp,
								)
							}

							Spacer(modifier = Modifier.height(10.dp))
							InfoRow(item, isSeries, uiState.badges)
							Spacer(modifier = Modifier.height(6.dp))
							InfoRowMultipleRatings(item = item)
							Spacer(modifier = Modifier.height(10.dp))

							item.taglines?.firstOrNull()?.let { tagline ->
								Text(
									text = "\u201C$tagline\u201D",
									fontSize = 16.sp,
									fontStyle = FontStyle.Italic,
									color = Color.White.copy(alpha = 0.6f),
									lineHeight = 22.sp,
								)
								Spacer(modifier = Modifier.height(8.dp))
							}

							item.overview?.let { overview ->
								Text(
									text = overview,
									fontSize = 15.sp,
									color = Color.White.copy(alpha = 0.8f),
									lineHeight = 24.sp,
									maxLines = 4,
									overflow = TextOverflow.Ellipsis,
								)
							}
						}

						if (posterUrl != null) {
							PosterImage(
								imageUrl = posterUrl,
								isLandscape = isEpisode,
								isSquare = isMusicAlbum || isPlaylist,
								item = item,
							)
						}
					}
					} // close focusable title Box

					// Action buttons (same item as header so they don't cause scroll)
					if (!isBoxSet) {
						Spacer(modifier = Modifier.height(24.dp))
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.focusRestorer(playButtonFocusRequester),
							horizontalArrangement = Arrangement.Center,
						) {
							ActionButtonsRow(item, uiState, playButtonFocusRequester)
						}
					}
				}

				// ---- Metadata ----
				item {
					Spacer(modifier = Modifier.height(24.dp))
					if (isPlaylist) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.clip(RoundedCornerShape(8.dp))
								.background(Color.White.copy(alpha = 0.03f))
								.border(
									1.dp,
									Color.White.copy(alpha = 0.06f),
									RoundedCornerShape(8.dp),
								)
								.padding(vertical = 12.dp, horizontal = 18.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							Text(
								text = "To reorder items, press left and right on the D-pad",
								fontSize = 14.sp,
								color = Color.White.copy(alpha = 0.6f),
							)
						}
					} else {
						MetadataSection(item, uiState)
					}
				}

				// ---- Next Up ----
				if (uiState.nextUp.isNotEmpty()) {
					item {
						SectionWithCards(
							title = "Next Up",
							items = uiState.nextUp,
							isLandscape = true,
						)
					}
				}

				// ---- Seasons ----
				if (isSeries && uiState.seasons.isNotEmpty()) {
					item {
						SeasonsSection(uiState.seasons)
					}
				}

				// ---- Episodes ----
				if (isEpisode && uiState.episodes.isNotEmpty()) {
					item {
						EpisodesHorizontalSection(
							title = item.parentIndexNumber?.let { "Season $it Episodes" } ?: "Episodes",
							episodes = uiState.episodes,
							currentEpisodeId = item.id,
						)
					}
				}

				// ---- Collection items ----
				if (isBoxSet && uiState.collectionItems.isNotEmpty()) {
					item {
						SectionWithCards(
							title = "Items in Collection",
							items = uiState.collectionItems,
							firstItemFocusRequester = collectionFirstItemFocusRequester,
						)
					}
				}

				// ---- Albums (Music Artist) ----
				if (uiState.albums.isNotEmpty()) {
					item {
						SectionWithCards(
							title = "Albums",
							items = uiState.albums,
						)
					}
				}

				// ---- Tracks (Music Album / Playlist) ----
				if (uiState.tracks.isNotEmpty()) {
					val canReorder = isPlaylist && item.canDelete == true

					item {
						Text(
							text = "Tracks",
							fontSize = 22.sp,
							fontWeight = FontWeight.W600,
							color = Color.White,
							modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
						)
					}

					items(uiState.tracks.size, key = { uiState.tracks[it].id }) { index ->
						val track = uiState.tracks[index]
						TrackItemCard(
							trackNumber = track.indexNumber ?: (index + 1),
							title = track.name ?: "",
							artist = track.artists?.firstOrNull() ?: track.albumArtist,
							runtime = track.runTimeTicks?.let { TimeUtils.formatMillis(it / 10_000) },
							onClick = {
								trackActionIndex = index
							},
							onMenuAction = {
								playbackHelper.retrieveAndPlay(track.id, false, requireContext())
							},
							onFocused = if (isPlaylist) {
								{ focusedBackdropUrl = getBackdropUrl(track) }
							} else {
								null
							},
							onMoveUp = if (canReorder && index > 0) {
								{ viewModel.movePlaylistItem(index, index - 1) }
							} else null,
							onMoveDown = if (canReorder && index < uiState.tracks.size - 1) {
								{ viewModel.movePlaylistItem(index, index + 1) }
							} else null,
							isFirst = index == 0,
							isLast = index == uiState.tracks.size - 1,
							modifier = Modifier.padding(bottom = 12.dp),
						)
					}
				}

				// ---- Cast & Crew ----
				if (uiState.cast.isNotEmpty()) {
					item {
						CastSection(uiState.cast)
					}
				}

				// ---- More Like This ----
				if (uiState.similar.isNotEmpty()) {
					item {
						SectionWithCards(
							title = "More Like This",
							items = uiState.similar,
							onItemFocused = if (isPlaylist) {
								{ focusItem -> focusedBackdropUrl = getBackdropUrl(focusItem) }
							} else {
								null
							},
						)
					}
				}
			}
		}

		// Auto-focus title area so page starts at the top
		LaunchedEffect(item.id) {
			for (attempt in 1..5) {
				delay(if (attempt == 1) 300L else 200L)
				listState.scrollToItem(0)
				try {
					titleFocusRequester.requestFocus()
					break
				} catch (_: Exception) {
					// Composable not yet laid out, retry
				}
			}
		}
	}

	@Composable
	private fun InfoRow(
		item: BaseItemDto,
		isSeries: Boolean,
		badges: List<MediaBadge>,
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(0.dp),
			) {
				var hasItem = false

				item.productionYear?.let { year ->
					InfoItemText(text = year.toString())
					hasItem = true
				}

				item.officialRating?.let { rating ->
					if (hasItem) InfoItemSeparator()
					InfoItemText(text = rating)
					hasItem = true
				}

				if (!isSeries) {
					item.runTimeTicks?.let { ticks ->
						if (hasItem) InfoItemSeparator()
						InfoItemText(text = formatDuration(ticks))
						hasItem = true
						InfoItemSeparator()
						InfoItemText(text = getEndsAt(ticks))
					}
				}

				if (isSeries) {
					val seasonCount = item.childCount ?: 0
					if (seasonCount > 0) {
						if (hasItem) InfoItemSeparator()
						InfoItemText(text = "$seasonCount Season${if (seasonCount != 1) "s" else ""}")
						hasItem = true
					}
				}

				item.communityRating?.let { rating ->
					if (hasItem) InfoItemSeparator()
					Row(verticalAlignment = Alignment.CenterVertically) {
						Icon(
							imageVector = ImageVector.vectorResource(R.drawable.ic_star),
							contentDescription = null,
							modifier = Modifier.height(14.dp).width(14.dp),
							tint = Color(0xFFFFC107),
						)
						Spacer(modifier = Modifier.width(3.dp))
						InfoItemText(text = String.format("%.1f", rating))
					}
				}
			}

			if (badges.isNotEmpty()) {
				Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
					badges.forEach { badge ->
						MediaBadgeChip(badge = badge)
					}
				}
			}

		}
	}

	@Composable
	private fun ActionButtonsRow(
		item: BaseItemDto,
		uiState: ItemDetailsUiState,
		playButtonFocusRequester: FocusRequester,
	) {
		val hasPlaybackPosition = item.canResume
		val mediaSources = item.mediaSources
		val firstSource = mediaSources?.firstOrNull()
		val audioStreams = firstSource?.mediaStreams?.filter { it.type == MediaStreamType.AUDIO } ?: emptyList()
		val subtitleStreams = firstSource?.mediaStreams?.filter { it.type == MediaStreamType.SUBTITLE } ?: emptyList()
		val hasMultipleVersions = (mediaSources?.size ?: 0) > 1
		val canPlay = item.type in listOf(
			BaseItemKind.MOVIE, BaseItemKind.EPISODE, BaseItemKind.VIDEO,
			BaseItemKind.RECORDING, BaseItemKind.TRAILER, BaseItemKind.MUSIC_VIDEO,
			BaseItemKind.SERIES, BaseItemKind.SEASON, BaseItemKind.PROGRAM,
			BaseItemKind.MUSIC_ALBUM, BaseItemKind.PLAYLIST, BaseItemKind.MUSIC_ARTIST,
		)

		// Dialog state
		var showAudioDialog by remember { mutableStateOf(false) }
		var showSubtitleDialog by remember { mutableStateOf(false) }
		var showVersionDialog by remember { mutableStateOf(false) }

		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.Center,
		) {
			Row(
				horizontalArrangement = Arrangement.spacedBy(16.dp),
			) {
				if (hasPlaybackPosition && canPlay) {
					val resumeTime = item.userData?.playbackPositionTicks?.let { formatDuration(it) } ?: ""
					DetailActionButton(
						label = "Resume",
						icon = ImageVector.vectorResource(R.drawable.ic_play),
						onClick = { handleResume(item) },
						detail = resumeTime,
						modifier = Modifier.focusRequester(playButtonFocusRequester),
					)
				}

				if (canPlay) {
					DetailActionButton(
						label = if (hasPlaybackPosition) "Restart" else "Play",
						icon = if (hasPlaybackPosition)
							ImageVector.vectorResource(R.drawable.ic_loop)
						else
							ImageVector.vectorResource(R.drawable.ic_play),
						onClick = { handlePlay(item, uiState) },
						modifier = if (!hasPlaybackPosition) Modifier.focusRequester(playButtonFocusRequester) else Modifier,
					)
				}

				if ((item.isFolder == true || item.type == BaseItemKind.MUSIC_ARTIST) && item.type != BaseItemKind.BOX_SET) {
					DetailActionButton(
						label = "Shuffle",
						icon = ImageVector.vectorResource(R.drawable.ic_shuffle),
						onClick = { handleShuffle(item) },
					)
				}

				if (item.type == BaseItemKind.MUSIC_ARTIST) {
					DetailActionButton(
						label = "Instant Mix",
						icon = ImageVector.vectorResource(R.drawable.ic_mix),
						onClick = { playbackHelper.playInstantMix(requireContext(), item) },
					)
				}

				if (hasMultipleVersions) {
					DetailActionButton(
						label = "Version",
						icon = ImageVector.vectorResource(R.drawable.ic_guide),
						onClick = { showVersionDialog = true },
					)
				}

				if (audioStreams.size > 1) {
					DetailActionButton(
						label = "Audio",
						icon = ImageVector.vectorResource(R.drawable.ic_select_audio),
						onClick = { showAudioDialog = true },
					)
				}

				if (subtitleStreams.isNotEmpty()) {
					DetailActionButton(
						label = "Subtitles",
						icon = ImageVector.vectorResource(R.drawable.ic_select_subtitle),
						onClick = { showSubtitleDialog = true },
					)
				}

				if (hasPlayableTrailers(requireContext(), item)) {
					DetailActionButton(
						label = "Trailer",
						icon = ImageVector.vectorResource(R.drawable.ic_trailer),
						onClick = { playTrailers(item) },
					)
				}

				if (item.userData != null && item.type != BaseItemKind.PERSON && item.type != BaseItemKind.MUSIC_ARTIST) {
						DetailActionButton(
						label = if (item.userData?.played == true) "Watched" else "Unwatched",
						icon = ImageVector.vectorResource(R.drawable.ic_check),
						onClick = { viewModel.toggleWatched() },
						isActive = item.userData?.played == true,
						activeColor = Color(0xFF2196F3),
					)
				}

				if (item.userData != null) {
					DetailActionButton(
						label = if (item.userData?.isFavorite == true) "Favorited" else "Favorite",
						icon = ImageVector.vectorResource(R.drawable.ic_heart),
						onClick = { viewModel.toggleFavorite() },
						isActive = item.userData?.isFavorite == true,
						activeColor = Color(0xFFFF4757),
					)
				}

				if (item.userData != null && item.type != BaseItemKind.PERSON) {
					DetailActionButton(
						label = "Playlist",
						icon = ImageVector.vectorResource(R.drawable.ic_add),
						onClick = { showAddToPlaylistDialog(requireContext(), item.id) },
					)
				}

				if (item.type == BaseItemKind.EPISODE && item.seriesId != null) {
					DetailActionButton(
						label = "Go to Series",
						icon = ImageVector.vectorResource(R.drawable.ic_tv),
						onClick = {
							item.seriesId?.let { seriesId ->
								navigationRepository.navigate(Destinations.itemDetails(seriesId, viewModel.serverId))
							}
						},
					)
				}

				if (item.canDelete == true) {
					DetailActionButton(
						label = "Delete",
						icon = ImageVector.vectorResource(R.drawable.ic_delete),
						onClick = { confirmDeleteItem(item) },
					)
				}
			}
		}

		// Audio track selector dialog
		if (showAudioDialog) {
			val audioTracks = trackSelector.getAudioTracks(item)
			if (audioTracks.isEmpty()) {
				LaunchedEffect(Unit) {
					Toast.makeText(requireContext(), "No audio tracks available", Toast.LENGTH_SHORT).show()
					showAudioDialog = false
				}
			} else {
				val selectedAudioIndex = trackSelector.getSelectedAudioTrack(item.id.toString())
				val trackNames = audioTracks.map { trackSelector.getAudioTrackDisplayName(it) } + listOf("Default")
				val checkedIndex = audioTracks.indexOfFirst { it.index == selectedAudioIndex }
					.let { if (it == -1) trackNames.size - 1 else it }

				TrackSelectorDialog(
					title = "Audio Track",
					options = trackNames,
					selectedIndex = checkedIndex,
					onSelect = { which ->
						if (which < audioTracks.size) {
							val track = audioTracks[which]
							trackSelector.setSelectedAudioTrack(item.id.toString(), track.index)
							Toast.makeText(requireContext(), "Audio: ${trackSelector.getAudioTrackDisplayName(track)}", Toast.LENGTH_SHORT).show()
						} else {
							trackSelector.setSelectedAudioTrack(item.id.toString(), null)
							Toast.makeText(requireContext(), "Audio: Default", Toast.LENGTH_SHORT).show()
						}
						showAudioDialog = false
					},
					onDismiss = { showAudioDialog = false },
				)
			}
		}

		// Subtitle track selector dialog
		if (showSubtitleDialog) {
			val subtitleTracks = trackSelector.getSubtitleTracks(item)
			val selectedSubIndex = trackSelector.getSelectedSubtitleTrack(item.id.toString())
			val trackNames = listOf("None") + subtitleTracks.map { trackSelector.getSubtitleTrackDisplayName(it) } + listOf("Default")
			val checkedIndex = when {
				selectedSubIndex == -1 -> 0
				selectedSubIndex == null -> trackNames.size - 1
				else -> subtitleTracks.indexOfFirst { it.index == selectedSubIndex }.let { if (it == -1) trackNames.size - 1 else it + 1 }
			}

			TrackSelectorDialog(
				title = "Subtitle Track",
				options = trackNames,
				selectedIndex = checkedIndex,
				onSelect = { which ->
					when (which) {
						0 -> {
							trackSelector.setSelectedSubtitleTrack(item.id.toString(), -1)
							Toast.makeText(requireContext(), "Subtitles: None", Toast.LENGTH_SHORT).show()
						}
						trackNames.size - 1 -> {
							trackSelector.setSelectedSubtitleTrack(item.id.toString(), null)
							Toast.makeText(requireContext(), "Subtitles: Default", Toast.LENGTH_SHORT).show()
						}
						else -> {
							val track = subtitleTracks[which - 1]
							trackSelector.setSelectedSubtitleTrack(item.id.toString(), track.index)
							Toast.makeText(requireContext(), "Subtitles: ${trackSelector.getSubtitleTrackDisplayName(track)}", Toast.LENGTH_SHORT).show()
						}
					}
					showSubtitleDialog = false
				},
				onDismiss = { showSubtitleDialog = false },
			)
		}

		// Version selector dialog
		if (showVersionDialog) {
			val versions = item.mediaSources ?: emptyList()
			val versionNames = versions.mapIndexed { i, source -> source.name ?: "Version ${i + 1}" }

			TrackSelectorDialog(
				title = "Select Version",
				options = versionNames,
				selectedIndex = -1,
				onSelect = { which ->
					val selectedSource = versions[which]
					val sourceId = selectedSource.id
					if (sourceId != null) {
						lifecycleScope.launch {
							try {
								val newItem = withContext(Dispatchers.IO) {
									viewModel.effectiveApi.userLibraryApi.getItem(itemId = item.id).content
								}
								viewModel.loadItem(newItem.id)
							} catch (e: ApiClientException) {
								Timber.e(e, "Failed to load version")
							}
						}
					}
					showVersionDialog = false
				},
				onDismiss = { showVersionDialog = false },
			)
		}
	}

	@Composable
	private fun MetadataSection(
		item: BaseItemDto,
		uiState: ItemDetailsUiState,
	) {
		val metaItems = mutableListOf<Pair<String, String>>()

		val genres = item.genres ?: emptyList()
		if (genres.isNotEmpty()) {
			metaItems.add("Genres" to genres.take(3).joinToString(", "))
		}
		if (uiState.directors.isNotEmpty()) {
			metaItems.add("Director" to uiState.directors.joinToString(", ") { it.name ?: "" })
		}
		if (uiState.writers.isNotEmpty()) {
			metaItems.add("Writers" to uiState.writers.joinToString(", ") { it.name ?: "" })
		}
		val studios = item.studios ?: emptyList()
		if (studios.isNotEmpty()) {
			metaItems.add("Studio" to studios.joinToString(", ") { it.name ?: "" })
		}

		if (metaItems.isNotEmpty()) {
			MetadataGroup(items = metaItems)
			Spacer(modifier = Modifier.height(24.dp))
		}
	}

	@Composable
	private fun SeasonsSection(seasons: List<BaseItemDto>) {
		Column {
			SectionHeader(title = "Seasons")
			LazyRow(
				horizontalArrangement = Arrangement.spacedBy(16.dp),
				contentPadding = PaddingValues(horizontal = 0.dp),
			) {
				items(seasons, key = { it.id }) { season ->
					SeasonCard(
						name = season.name ?: "Season",
						imageUrl = getPosterUrl(season),
						isWatched = season.userData?.played == true,
						unplayedCount = season.userData?.unplayedItemCount,
						onClick = {
							navigationRepository.navigate(Destinations.itemDetails(season.id, viewModel.serverId))
						},
						item = season,
					)
				}
			}
		}
	}

	@Composable
	private fun EpisodesHorizontalSection(
		title: String,
		episodes: List<BaseItemDto>,
		currentEpisodeId: UUID,
	) {
		Column {
			SectionHeader(title = title)
			LazyRow(
				horizontalArrangement = Arrangement.spacedBy(16.dp),
				contentPadding = PaddingValues(horizontal = 0.dp),
			) {
				items(episodes, key = { it.id }) { ep ->
					EpisodeCard(
						episodeNumber = ep.indexNumber,
						title = ep.name ?: "",
						runtime = ep.runTimeTicks?.let { formatDuration(it) },
						imageUrl = getPosterUrl(ep),
						progress = ep.userData?.playedPercentage ?: 0.0,
						isCurrent = ep.id == currentEpisodeId,
						isPlayed = ep.userData?.played == true,
						onClick = {
							navigationRepository.navigate(Destinations.itemDetails(ep.id, viewModel.serverId))
						},
					)
				}
			}
		}
	}

	@Composable
	private fun CastSection(cast: List<org.jellyfin.sdk.model.api.BaseItemPerson>) {
		Column {
			SectionHeader(title = "Cast & Crew")
			LazyRow(
				horizontalArrangement = Arrangement.spacedBy(24.dp),
				contentPadding = PaddingValues(horizontal = 0.dp),
			) {
				items(cast, key = { it.id }) { person ->
					CastCard(
						name = person.name ?: "",
						role = person.role ?: person.type.toString(),
						imageUrl = person.primaryImageTag?.let { tag ->
							viewModel.effectiveApi.imageApi.getItemImageUrl(
								itemId = person.id,
								imageType = ImageType.PRIMARY,
								tag = tag,
								maxHeight = 280,
							)
						},
						onClick = {
							navigationRepository.navigate(Destinations.itemDetails(person.id, viewModel.serverId))
						},
					)
				}
			}
		}
	}

	@Composable
	private fun SectionWithCards(
		title: String,
		items: List<BaseItemDto>,
		isLandscape: Boolean = false,
		firstItemFocusRequester: FocusRequester? = null,
		onItemFocused: ((BaseItemDto) -> Unit)? = null,
	) {
		Column {
			SectionHeader(title = title)
			LazyRow(
				horizontalArrangement = Arrangement.spacedBy(16.dp),
				contentPadding = PaddingValues(horizontal = 0.dp),
			) {
				items(items.size) { index ->
					val item = items[index]
					val cardModifier = if (index == 0 && firstItemFocusRequester != null)
						Modifier.focusRequester(firstItemFocusRequester)
					else Modifier

					if (isLandscape) {
						LandscapeItemCard(
							title = item.name ?: "",
							imageUrl = getEpisodeThumbnailUrl(item),
							subtitle = item.seriesName,
							onClick = {
								navigationRepository.navigate(Destinations.itemDetails(item.id, viewModel.serverId))
							},
							onFocused = onItemFocused?.let { callback -> { callback(item) } },
							modifier = cardModifier,
							item = item,
						)
					} else {
						SimilarItemCard(
							title = item.name ?: "",
							imageUrl = getPosterUrl(item),
							year = item.productionYear,
							onClick = {
								navigationRepository.navigate(Destinations.itemDetails(item.id, viewModel.serverId))
							},
							onFocused = onItemFocused?.let { callback -> { callback(item) } },
							modifier = cardModifier,
							item = item,
						)
					}
				}
			}
		}
	}

	@Composable
	private fun SeasonDetailsContent(uiState: ItemDetailsUiState, contentFocusRequester: FocusRequester, showBackdrop: Boolean = true) {
		val item = uiState.item ?: return
		val listState = rememberLazyListState()
		val playButtonFocusRequester = remember { FocusRequester() }
		val titleFocusRequester = contentFocusRequester
		val backdropUrl = getBackdropUrl(item)
		val posterUrl = getPosterUrl(item)

		Box(modifier = Modifier.fillMaxSize()) {
			if (showBackdrop) {
				DetailBackdrop(imageUrl = backdropUrl, blurAmount = userSettingPreferences[UserSettingPreferences.detailsBackgroundBlurAmount])
			}

			LazyColumn(
				state = listState,
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(top = 180.dp, start = 100.dp, end = 100.dp, bottom = 80.dp),
			) {
				// Season header + action buttons in same item
				item {
					// Focusable title area
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.focusRequester(titleFocusRequester)
							.focusable()
							.onKeyEvent { keyEvent ->
								if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
									when (keyEvent.key) {
										Key.DirectionDown -> {
											try { playButtonFocusRequester.requestFocus() } catch (_: Exception) {}
											true
										}
										else -> false
									}
								} else false
							},
					) {
						Row(
							verticalAlignment = Alignment.Bottom,
							modifier = Modifier.padding(bottom = 48.dp),
						) {
							if (posterUrl != null) {
								Box(modifier = Modifier.width(220.dp)) {
									AsyncImage(
										model = posterUrl,
										contentDescription = null,
										modifier = Modifier
											.fillMaxWidth()
											.background(
												Color.White.copy(alpha = 0.05f),
												JellyfinTheme.shapes.medium,
											),
										contentScale = ContentScale.FillWidth,
									)
								}
								Spacer(modifier = Modifier.width(32.dp))
							}

							Column(modifier = Modifier.padding(bottom = 8.dp)) {
								item.seriesName?.let { seriesName ->
									Text(
										text = seriesName,
										fontSize = 22.sp,
										fontWeight = FontWeight.W500,
										color = Color.White.copy(alpha = 0.6f),
									)
									Spacer(modifier = Modifier.height(4.dp))
								}

								Text(
									text = item.name ?: "",
									fontSize = 48.sp,
									fontWeight = FontWeight.W700,
									color = Color.White,
									lineHeight = 55.sp,
								)

								Spacer(modifier = Modifier.height(4.dp))

								Text(
									text = "${uiState.episodes.size} Episode${if (uiState.episodes.size != 1) "s" else ""}",
									fontSize = 18.sp,
									color = Color.White.copy(alpha = 0.5f),
								)
							}
						}
					} // close focusable title Box

					if (uiState.episodes.isNotEmpty()) {
						Row(
							modifier = Modifier
								.fillMaxWidth(),
							horizontalArrangement = Arrangement.Center,
						) {
							Row(
								horizontalArrangement = Arrangement.spacedBy(24.dp),
								modifier = Modifier.focusGroup(),
							) {
								DetailActionButton(
									label = "Play",
									icon = ImageVector.vectorResource(R.drawable.ic_play),
									onClick = {
										val unwatched = uiState.episodes.firstOrNull { !(it.userData?.played ?: false) }
										val episode = unwatched ?: uiState.episodes.first()
										play(episode, 0, false)
									},
									modifier = Modifier.focusRequester(playButtonFocusRequester),
								)

								DetailActionButton(
									label = if (item.userData?.played == true) "Watched" else "Unwatched",
									icon = ImageVector.vectorResource(R.drawable.ic_check),
									onClick = { viewModel.toggleWatched() },
									isActive = item.userData?.played == true,
									activeColor = Color(0xFF2196F3),
								)

								DetailActionButton(
									label = if (item.userData?.isFavorite == true) "Favorited" else "Favorite",
									icon = ImageVector.vectorResource(R.drawable.ic_heart),
									onClick = { viewModel.toggleFavorite() },
									isActive = item.userData?.isFavorite == true,
									activeColor = Color(0xFFFF4757),
								)
							}
						}
					}
				}

				item {
					Spacer(modifier = Modifier.height(36.dp))
				}

				items(uiState.episodes.size) { index ->
					val ep = uiState.episodes[index]
					SeasonEpisodeItem(
						episodeNumber = ep.indexNumber,
						title = ep.name ?: "",
						overview = ep.overview,
						runtime = ep.runTimeTicks?.let { formatDuration(it) },
						imageUrl = getEpisodeThumbnailUrl(ep),
						progress = ep.userData?.playedPercentage ?: 0.0,
						isPlayed = ep.userData?.played == true,
						onClick = {
							navigationRepository.navigate(Destinations.itemDetails(ep.id, viewModel.serverId))
						},
						modifier = Modifier.padding(bottom = 12.dp),
					)
				}
			}
		}

		// Auto-focus title area so page starts at the top
		LaunchedEffect(item.id) {
			for (attempt in 1..5) {
				delay(if (attempt == 1) 300L else 200L)
				listState.scrollToItem(0)
				try {
					titleFocusRequester.requestFocus()
					break
				} catch (_: Exception) {
					// Composable not yet laid out, retry
				}
			}
		}
	}

	@Composable
	private fun PersonDetailsContent(uiState: ItemDetailsUiState, contentFocusRequester: FocusRequester, showBackdrop: Boolean = true) {
		val item = uiState.item ?: return
		val listState = rememberLazyListState()
		val titleFocusRequester = contentFocusRequester
		val filmographyFocusRequester = remember { FocusRequester() }

		val personMovies = uiState.similar.filter { it.type == BaseItemKind.MOVIE }
		val personSeries = uiState.similar.filter { it.type == BaseItemKind.SERIES }

		val backdropUrls = remember(uiState.similar) {
			uiState.similar
				.mapNotNull { filmItem -> getBackdropUrl(filmItem) }
				.distinct()
				.take(10) // Limit to 10 backdrops
		}

		var currentBackdropIndex by remember { mutableStateOf(0) }
		var focusedBackdropUrl by remember { mutableStateOf<String?>(null) }

		LaunchedEffect(backdropUrls) {
			if (backdropUrls.size > 1) {
				while (true) {
					delay(8000)
					if (focusedBackdropUrl == null) {
						currentBackdropIndex = (currentBackdropIndex + 1) % backdropUrls.size
					}
				}
			}
		}

		Box(modifier = Modifier.fillMaxSize()) {
			if (showBackdrop && backdropUrls.isNotEmpty()) {
				val displayUrl = focusedBackdropUrl ?: backdropUrls.getOrNull(currentBackdropIndex)
				Crossfade(
					targetState = displayUrl,
					animationSpec = tween(1000),
					label = "person_backdrop_slideshow"
				) { backdropUrl ->
					if (backdropUrl != null) {
						AsyncImage(
							model = backdropUrl,
							contentDescription = null,
							modifier = Modifier
								.fillMaxSize()
								.graphicsLayer { alpha = 0.6f },
							contentScale = ContentScale.Crop,
						)
						Box(
							modifier = Modifier
								.fillMaxSize()
								.background(
									brush = androidx.compose.ui.graphics.Brush.verticalGradient(
										colors = listOf(
											Color.Black.copy(alpha = 0.3f),
											Color.Black.copy(alpha = 0.6f),
										),
									)
								)
						)
					}
				}
			} else if (showBackdrop) {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.background(
							brush = androidx.compose.ui.graphics.Brush.linearGradient(
								colors = listOf(
									Color(0xFF1A1A2E),
									Color(0xFF16213E),
									Color(0xFF0F3460),
								),
							)
						)
				)
			}

			LazyColumn(
				state = listState,
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(top = 100.dp, start = 48.dp, end = 48.dp, bottom = 80.dp),
			) {
				item {
					// Focusable title area - receives initial focus
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.focusRequester(titleFocusRequester)
							.focusable()
							.onKeyEvent { keyEvent ->
								if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
									when (keyEvent.key) {
										Key.DirectionDown -> {
											try { filmographyFocusRequester.requestFocus() } catch (_: Exception) {}
											true
										}
										else -> false
									}
								} else false
							},
					) {
						Row(modifier = Modifier.padding(bottom = 24.dp)) {
							Box(modifier = Modifier.width(160.dp)) {
								val personPhotoUrl = getPosterUrl(item)
								if (personPhotoUrl != null) {
									AsyncImage(
										model = personPhotoUrl,
										contentDescription = item.name,
										modifier = Modifier
											.fillMaxWidth()
											.height(240.dp)
											.background(
												Color.White.copy(alpha = 0.05f),
												JellyfinTheme.shapes.medium,
											),
										contentScale = ContentScale.Crop,
									)
								} else {
									Box(
										modifier = Modifier
											.fillMaxWidth()
											.height(240.dp)
											.background(
												Color.White.copy(alpha = 0.08f),
												JellyfinTheme.shapes.medium,
											),
										contentAlignment = Alignment.Center,
									) {
										Text(
											text = item.name?.firstOrNull()?.toString() ?: "",
											fontSize = 48.sp,
											color = Color.White.copy(alpha = 0.25f),
										)
									}
								}
							}

							Spacer(modifier = Modifier.width(32.dp))

							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = item.name ?: "",
									fontSize = 36.sp,
									fontWeight = FontWeight.W700,
									color = Color.White,
									lineHeight = 40.sp,
								)

								Spacer(modifier = Modifier.height(8.dp))

								item.premiereDate?.let { birthDate ->
									val age = java.time.temporal.ChronoUnit.YEARS.between(
										birthDate,
										item.endDate ?: java.time.LocalDateTime.now(),
									)
									val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")
									Text(
										text = "Born ${birthDate.toLocalDate().format(formatter)} (age $age)",
										fontSize = 18.sp,
										color = Color.White.copy(alpha = 0.7f),
									)
									Spacer(modifier = Modifier.height(4.dp))
								}

								item.productionLocations?.firstOrNull()?.let { birthPlace ->
									Text(
										text = birthPlace,
										fontSize = 18.sp,
										color = Color.White.copy(alpha = 0.7f),
									)
									Spacer(modifier = Modifier.height(8.dp))
								}

								item.overview?.let { overview ->
									Text(
										text = overview,
										fontSize = 18.sp,
										color = Color.White.copy(alpha = 0.8f),
										lineHeight = 26.sp,
										maxLines = 4,
										overflow = TextOverflow.Ellipsis,
									)
								}
							}
						}
					}
				}

				if (personMovies.isNotEmpty()) {
					item {
						SectionWithCards(
							title = "Movies (${personMovies.size})",
							items = personMovies,
							firstItemFocusRequester = filmographyFocusRequester,
							onItemFocused = { focusItem -> focusedBackdropUrl = getBackdropUrl(focusItem) },
						)
					}
				}
				if (personSeries.isNotEmpty()) {
					item {
						Spacer(modifier = Modifier.height(24.dp))
						SectionWithCards(
							title = "Series (${personSeries.size})",
							items = personSeries,
							onItemFocused = { focusItem -> focusedBackdropUrl = getBackdropUrl(focusItem) },
							firstItemFocusRequester = if (personMovies.isEmpty()) filmographyFocusRequester else null,
						)
					}
				}
			}
		}

		// Request initial focus on title
		LaunchedEffect(Unit) {
			for (attempt in 1..5) {
				delay(if (attempt == 1) 300L else 200L)
				try {
					titleFocusRequester.requestFocus()
					break
				} catch (_: Exception) {
					// Composable not yet laid out, retry
				}
			}
		}
	}

	private fun getBackdropUrl(item: BaseItemDto): String? {
		// Try item's own backdrop first, then fall back to parent's backdrop
		val backdropImage = item.itemBackdropImages.firstOrNull()
			?: item.parentBackdropImages.firstOrNull()
		return backdropImage?.getUrl(
			viewModel.effectiveApi,
			maxWidth = 1920,
		)
	}

	private fun getPosterUrl(item: BaseItemDto): String? {
		return when {
			item.type == BaseItemKind.EPISODE -> {
				val thumbImage = item.itemImages[ImageType.THUMB]
				val primaryImage = item.itemImages[ImageType.PRIMARY]
				(thumbImage ?: primaryImage)?.getUrl(viewModel.effectiveApi, maxWidth = 500)
			}
			else -> {
				item.itemImages[ImageType.PRIMARY]?.getUrl(viewModel.effectiveApi, maxHeight = 600)
			}
		}
	}

	private fun getLogoUrl(item: BaseItemDto): String? {
		val logoImage = item.getLogoImage()
		return logoImage?.getUrl(viewModel.effectiveApi, maxWidth = 400)
	}

	private fun getEpisodeThumbnailUrl(ep: BaseItemDto): String? {
		val primaryImage = ep.itemImages[ImageType.PRIMARY]
		return primaryImage?.getUrl(viewModel.effectiveApi, maxWidth = 400)
	}

	private fun formatDuration(ticks: Long): String {
		val totalMinutes = (ticks / 10_000_000 / 60).toInt()
		val hours = totalMinutes / 60
		val minutes = totalMinutes % 60
		return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
	}

	private fun getEndsAt(ticks: Long): String {
		val endTime = java.util.Date(System.currentTimeMillis() + ticks / 10_000)
		val cal = java.util.Calendar.getInstance().apply { time = endTime }
		val hours = cal.get(java.util.Calendar.HOUR)
		val minutes = cal.get(java.util.Calendar.MINUTE)
		val ampm = if (cal.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "AM" else "PM"
		val h = if (hours == 0) 12 else hours
		val m = if (minutes < 10) "0$minutes" else "$minutes"
		return "$h:$m $ampm"
	}

	/**
	 * Launch playback using the same flow as the old FullDetailsFragment:
	 * getItemsToPlay() expands the queue (following episodes, intros for movies)
	 * then PlaybackLauncher.launch() navigates to the player.
	 * Position is in milliseconds.
	 */
	private fun play(item: BaseItemDto, positionMs: Int, shuffle: Boolean) {
		playbackHelper.getItemsToPlay(
			requireContext(),
			item,
			positionMs == 0 && item.type == BaseItemKind.MOVIE,
			shuffle,
			object : Response<List<BaseItemDto>>(lifecycle) {
				override fun onResponse(response: List<BaseItemDto>) {
					if (!isActive) return
					if (response.isEmpty()) {
						Timber.e("No items to play - ignoring play request.")
						return
					}
					playbackLauncher.launch(requireContext(), response, positionMs, false, 0, shuffle)
				}
			}
		)
	}

	private fun handlePlay(item: BaseItemDto, uiState: ItemDetailsUiState) {
		when (item.type) {
			BaseItemKind.SERIES -> {
				if (uiState.nextUp.isNotEmpty()) {
					play(uiState.nextUp.first(), 0, false)
				} else if (uiState.seasons.isNotEmpty()) {
					navigationRepository.navigate(Destinations.itemDetails(uiState.seasons.first().id, viewModel.serverId))
				}
			}
			BaseItemKind.SEASON -> {
				if (uiState.episodes.isNotEmpty()) {
					val unwatched = uiState.episodes.firstOrNull { !(it.userData?.played ?: false) }
					val episode = unwatched ?: uiState.episodes.first()
					play(episode, 0, false)
				}
			}
			else -> {
				play(item, 0, false)
			}
		}
	}

	private fun handleResume(item: BaseItemDto) {
		val prerollMs = (userPreferences[UserPreferences.resumeSubtractDuration].toIntOrNull() ?: 0) * 1000
		val posMs = ((item.userData?.playbackPositionTicks ?: 0L) / 10_000).toInt()
		val position = maxOf(posMs - prerollMs, 0)
		play(item, position, false)
	}

	private fun handleShuffle(item: BaseItemDto) {
		play(item, 0, true)
	}

	private fun playTrailers(item: BaseItemDto) {
		val localTrailerCount = item.localTrailerCount ?: 0

		if (localTrailerCount < 1) {
			// External trailer — resolve YouTube video and play in-app WebView
			lifecycleScope.launch {
				try {
					val trailerInfo = withContext(Dispatchers.IO) {
						TrailerResolver.resolveTrailerFromItem(item)
					}

					if (trailerInfo != null) {
						val segmentsJson = trailerInfo.segments.joinToString(",", "[", "]") { seg ->
							"""{"start":${seg.startTime},"end":${seg.endTime},"category":"${seg.category}","action":"${seg.actionType}"}"""
						}
						navigationRepository.navigate(Destinations.trailerPlayer(
							videoId = trailerInfo.youtubeVideoId,
							startSeconds = trailerInfo.startSeconds,
							segmentsJson = segmentsJson,
						))
					} else {
						// No YouTube trailer found — fall back to external intent
						val intent = getExternalTrailerIntent(requireContext(), item)
						if (intent != null) {
							val chooser = Intent.createChooser(intent, getString(R.string.lbl_play_trailers))
							startActivity(chooser)
						} else {
							Toast.makeText(requireContext(), getString(R.string.no_player_message), Toast.LENGTH_LONG).show()
						}
					}
				} catch (e: Exception) {
					Timber.w(e, "Failed to resolve trailer")
					// Fall back to external intent
					try {
						val intent = getExternalTrailerIntent(requireContext(), item)
						if (intent != null) {
							val chooser = Intent.createChooser(intent, getString(R.string.lbl_play_trailers))
							startActivity(chooser)
						}
					} catch (e2: ActivityNotFoundException) {
						Timber.w(e2, "Unable to open external trailer")
						Toast.makeText(requireContext(), getString(R.string.no_player_message), Toast.LENGTH_LONG).show()
					}
				}
			}
		} else {
			// Local trailer
			lifecycleScope.launch {
				try {
					val trailers = withContext(Dispatchers.IO) {
						viewModel.effectiveApi.userLibraryApi.getLocalTrailers(itemId = item.id).content
					}
					if (trailers.isNotEmpty()) {
						val trailerIds = trailers.map { it.id }
						playbackHelper.retrieveAndPlay(trailerIds, false, null, null, requireContext())
					}
				} catch (e: ApiClientException) {
					Timber.e(e, "Error retrieving trailers for playback")
					Toast.makeText(requireContext(), getString(R.string.msg_video_playback_error), Toast.LENGTH_LONG).show()
				}
			}
		}
	}

	private fun confirmDeleteItem(item: BaseItemDto) {
		android.app.AlertDialog.Builder(requireContext())
			.setTitle(R.string.item_delete_confirm_title)
			.setMessage(R.string.item_delete_confirm_message)
			.setNegativeButton(R.string.lbl_no, null)
			.setPositiveButton(R.string.lbl_delete) { _, _ ->
				deleteItem(item)
			}
			.show()
	}

	private fun deleteItem(item: BaseItemDto) {
		lifecycleScope.launch {
			try {
				withContext(Dispatchers.IO) {
					viewModel.effectiveApi.libraryApi.deleteItem(itemId = item.id)
				}
			} catch (e: ApiClientException) {
				Timber.e(e, "Failed to delete item ${item.name} (id=${item.id})")
				Toast.makeText(requireContext(), getString(R.string.item_deletion_failed, item.name), Toast.LENGTH_LONG).show()
				return@launch
			}
			dataRefreshService.lastDeletedItemId = item.id
			if (navigationRepository.canGoBack) navigationRepository.goBack()
			else navigationRepository.navigate(Destinations.home)
			Toast.makeText(requireContext(), getString(R.string.item_deleted, item.name), Toast.LENGTH_LONG).show()
		}
	}
}
