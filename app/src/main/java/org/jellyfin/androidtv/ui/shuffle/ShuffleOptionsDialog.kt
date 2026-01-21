package org.jellyfin.androidtv.ui.shuffle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.model.AggregatedLibrary
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import timber.log.Timber
import java.util.UUID

internal enum class ShuffleMode {
	MAIN, LIBRARIES, GENRES
}

internal data class LibrarySelection(
	val library: BaseItemDto,
	val serverId: UUID?,
	val displayName: String
)

@Composable
fun ShuffleOptionsDialog(
	userViews: List<BaseItemDto>,
	aggregatedLibraries: List<AggregatedLibrary>,
	enableMultiServer: Boolean,
	shuffleContentType: String,
	api: ApiClient,
	onDismiss: () -> Unit,
	onShuffle: (libraryId: UUID?, serverId: UUID?, genreName: String?, contentType: String, collectionType: CollectionType?) -> Unit,
) {
	var mode by remember { mutableStateOf(ShuffleMode.MAIN) }
	var genres by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
	var loadingGenres by remember { mutableStateOf(false) }

	val libraryOptions = remember(userViews, aggregatedLibraries, enableMultiServer) {
		if (enableMultiServer && aggregatedLibraries.isNotEmpty()) {
			aggregatedLibraries.map { aggLib ->
				LibrarySelection(aggLib.library, aggLib.server.id, aggLib.displayName)
			}
		} else {
			userViews.map { lib ->
				LibrarySelection(lib, null, lib.name ?: "")
			}
		}
	}

	LaunchedEffect(mode) {
		if (mode == ShuffleMode.GENRES && genres.isEmpty()) {
			loadingGenres = true
			try {
				val response = withContext(Dispatchers.IO) {
					api.genresApi.getGenres(
						sortBy = setOf(ItemSortBy.SORT_NAME),
					)
				}
				genres = response.content.items
			} catch (e: Exception) {
				Timber.e(e, "Failed to load genres")
			}
			loadingGenres = false
		}
	}

	Surface(
		modifier = Modifier,
		color = Color.Black.copy(alpha = 0.95f),
		shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge
	) {
		AlertDialog(
			onDismissRequest = onDismiss,
			title = {
				androidx.compose.material3.Text(
					when (mode) {
						ShuffleMode.MAIN -> "Shuffle By"
						ShuffleMode.LIBRARIES -> "Select Library"
						ShuffleMode.GENRES -> "Select Genre"
					},
					color = Color.Gray
				)
			},
			text = {
				Column(modifier = Modifier.fillMaxWidth()) {
					when (mode) {
						ShuffleMode.MAIN -> {
							Button(
								onClick = { mode = ShuffleMode.LIBRARIES },
								modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
								colors = ButtonDefaults.colors(
									containerColor = JellyfinTheme.colorScheme.button,
									contentColor = JellyfinTheme.colorScheme.onButton,
									focusedContainerColor = JellyfinTheme.colorScheme.buttonFocused,
									focusedContentColor = JellyfinTheme.colorScheme.onButtonFocused,
								)
							) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.Start,
									verticalAlignment = Alignment.CenterVertically
								) {
									Icon(
										imageVector = ImageVector.vectorResource(R.drawable.ic_folder),
										contentDescription = null,
										modifier = Modifier.padding(end = 12.dp)
									)
									androidx.compose.material3.Text("Library", color = Color.White)
								}
							}
							Button(
								onClick = { mode = ShuffleMode.GENRES },
								modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
								colors = ButtonDefaults.colors(
									containerColor = JellyfinTheme.colorScheme.button,
									contentColor = JellyfinTheme.colorScheme.onButton,
									focusedContainerColor = JellyfinTheme.colorScheme.buttonFocused,
									focusedContentColor = JellyfinTheme.colorScheme.onButtonFocused,
								)
							) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.Start,
									verticalAlignment = Alignment.CenterVertically
								) {
									Icon(
										imageVector = ImageVector.vectorResource(R.drawable.ic_masks),
										contentDescription = null,
										modifier = Modifier.padding(end = 12.dp)
									)
									androidx.compose.material3.Text("Genre", color = Color.White)
								}
							}
						}
						ShuffleMode.LIBRARIES -> {
							LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
								items(libraryOptions) { libOption ->
									Button(
										onClick = {
											onShuffle(libOption.library.id, libOption.serverId, null, shuffleContentType, libOption.library.collectionType)
										},
										modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
										colors = ButtonDefaults.colors(
											containerColor = JellyfinTheme.colorScheme.button,
											contentColor = JellyfinTheme.colorScheme.onButton,
											focusedContainerColor = JellyfinTheme.colorScheme.buttonFocused,
											focusedContentColor = JellyfinTheme.colorScheme.onButtonFocused,
										)
									) {
										androidx.compose.material3.Text(
											libOption.displayName,
											modifier = Modifier.fillMaxWidth(),
											color = Color.White
										)
									}
								}
							}
						}
						ShuffleMode.GENRES -> {
							if (loadingGenres) {
								Row(
									modifier = Modifier.fillMaxWidth().padding(16.dp),
									horizontalArrangement = Arrangement.Center
								) {
									CircularProgressIndicator(strokeWidth = 2.dp)
								}
							} else {
								LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
									items(genres) { genre ->
										Button(
											onClick = {
												onShuffle(null, null, genre.name, shuffleContentType, null)
											},
											modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
											colors = ButtonDefaults.colors(
												containerColor = JellyfinTheme.colorScheme.button,
												contentColor = JellyfinTheme.colorScheme.onButton,
												focusedContainerColor = JellyfinTheme.colorScheme.buttonFocused,
												focusedContentColor = JellyfinTheme.colorScheme.onButtonFocused,
											)
										) {
											androidx.compose.material3.Text(
												genre.name ?: "",
												modifier = Modifier.fillMaxWidth(),
												color = Color.White
											)
										}
									}
								}
							}
						}
					}
				}
			},
			confirmButton = {
				if (mode != ShuffleMode.MAIN) {
					TextButton(onClick = { mode = ShuffleMode.MAIN }) {
						androidx.compose.material3.Text("Back")
					}
				}
			},
			dismissButton = {
				TextButton(onClick = onDismiss) {
					androidx.compose.material3.Text("Cancel")
				}
			}
		)
	}
}
