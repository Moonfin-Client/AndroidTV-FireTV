package org.jellyfin.androidtv.ui.playlist

import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.MultiServerRepository
import org.jellyfin.androidtv.data.repository.ServerUserSession
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.koin.compose.koinInject
import timber.log.Timber
import java.util.UUID

/**
 * Show the add to playlist dialog from Java code (e.g., FullDetailsFragment)
 */
fun showAddToPlaylistDialog(
	context: android.content.Context,
	itemId: UUID,
) {
	val dialog = androidx.appcompat.app.AppCompatDialog(context, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
	dialog.setContentView(
		androidx.compose.ui.platform.ComposeView(context).apply {
			setContent {
				JellyfinTheme {
					val api = koinInject<ApiClient>()
					val userPreferences = koinInject<UserPreferences>()
					val multiServerRepository = koinInject<MultiServerRepository>()
					val scope = rememberCoroutineScope()
					var showDialog by remember { mutableStateOf(true) }
					var showCreatePlaylistFor by remember { mutableStateOf<ApiClient?>(null) }

					val enableMultiServer = remember { 
						userPreferences[UserPreferences.enableMultiServerLibraries]
					}

					var serverSessions by remember { mutableStateOf<List<ServerUserSession>>(emptyList()) }
					LaunchedEffect(enableMultiServer) {
						if (enableMultiServer) {
							serverSessions = withContext(Dispatchers.IO) {
								multiServerRepository.getLoggedInServers()
							}
						}
					}
					
					if (showCreatePlaylistFor != null) {
						CreatePlaylistDialog(
							itemId = itemId,
							apiClient = showCreatePlaylistFor!!,
							onDismiss = {
								showCreatePlaylistFor = null
								showDialog = false
								dialog.dismiss()
							},
							onBack = {
								showCreatePlaylistFor = null
								showDialog = true
							},
							onPlaylistCreated = {
								showCreatePlaylistFor = null
								showDialog = false
								dialog.dismiss()
							},
						)
					}

					if (showDialog) {
						AddToPlaylistDialog(
							itemId = itemId,
							api = api,
							enableMultiServer = enableMultiServer,
							serverSessions = serverSessions,
							onDismiss = {
								showDialog = false
								dialog.dismiss()
							},
							onAddToPlaylist = { playlistId, serverApi ->
								scope.launch {
									try {
										withContext(Dispatchers.IO) {
											serverApi.playlistsApi.addItemToPlaylist(
												playlistId = playlistId,
												ids = listOf(itemId)
											)
										}
										withContext(Dispatchers.Main) {
											Toast.makeText(
												context,
												context.getString(R.string.msg_added_to_playlist),
												Toast.LENGTH_SHORT
											).show()
										}
									} catch (e: Exception) {
										Timber.e(e, "Failed to add item to playlist")
										withContext(Dispatchers.Main) {
											Toast.makeText(
												context,
												context.getString(R.string.msg_failed_to_add_to_playlist),
												Toast.LENGTH_SHORT
											).show()
										}
									}
									showDialog = false
									dialog.dismiss()
								}
							},
							onCreateNewPlaylist = { serverApi ->
								showDialog = false
								showCreatePlaylistFor = serverApi
							}
						)
					}
				}
			}
		}
	)
	dialog.show()
}

