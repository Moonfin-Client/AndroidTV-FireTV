package org.jellyfin.androidtv.ui.playlist

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.shuffle.GlassDialogRow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.model.api.CreatePlaylistDto
import timber.log.Timber
import java.util.UUID

@Composable
fun CreatePlaylistDialog(
	itemId: UUID,
	apiClient: ApiClient,
	onDismiss: () -> Unit,
	onBack: () -> Unit,
	onPlaylistCreated: () -> Unit,
) {
	var playlistName by remember { mutableStateOf("") }
	var isPublic by remember { mutableStateOf(false) }
	var isCreating by remember { mutableStateOf(false) }
	val context = LocalContext.current
	val nameInputFocusRequester = remember { FocusRequester() }

	Dialog(
		onDismissRequest = onBack,
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
				// Title row with back button
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 24.dp)
						.padding(bottom = 12.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					val backInteraction = remember { MutableInteractionSource() }
					val backFocused by backInteraction.collectIsFocusedAsState()
					Box(
						modifier = Modifier
							.size(32.dp)
							.clip(RoundedCornerShape(8.dp))
							.background(if (backFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent)
							.clickable(
								interactionSource = backInteraction,
								indication = null,
							) { onBack() }
							.focusable(interactionSource = backInteraction),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = "\u276E",
							fontSize = 16.sp,
							color = if (backFocused) Color.White else Color.White.copy(alpha = 0.6f),
						)
					}
					Spacer(modifier = Modifier.width(12.dp))

					Text(
						text = stringResource(R.string.lbl_create_new_playlist),
						fontSize = 20.sp,
						fontWeight = FontWeight.W600,
						color = Color.White,
					)
				}

				// Divider
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(1.dp)
						.background(Color.White.copy(alpha = 0.08f)),
				)

				Spacer(modifier = Modifier.height(16.dp))

				// Playlist name label
				Text(
					text = stringResource(R.string.lbl_playlist_name),
					fontSize = 12.sp,
					color = Color.White.copy(alpha = 0.5f),
					modifier = Modifier.padding(horizontal = 24.dp),
				)

				Spacer(modifier = Modifier.height(8.dp))

				// Playlist name input
				val nameInteraction = remember { MutableInteractionSource() }
				val nameFieldFocused by nameInteraction.collectIsFocusedAsState()
				BasicTextField(
					value = playlistName,
					onValueChange = { playlistName = it },
					textStyle = TextStyle(
						color = Color.White,
						fontSize = 16.sp,
					),
					cursorBrush = SolidColor(Color(0xFF00A4DC)),
					singleLine = true,
					interactionSource = nameInteraction,
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 24.dp)
						.clip(RoundedCornerShape(12.dp))
						.background(Color.White.copy(alpha = 0.08f))
						.border(
							width = 1.dp,
							color = if (nameFieldFocused) Color(0xFF00A4DC) else Color.White.copy(alpha = 0.15f),
							shape = RoundedCornerShape(12.dp),
						)
						.padding(horizontal = 16.dp, vertical = 14.dp)
						.focusRequester(nameInputFocusRequester),
					decorationBox = { innerTextField ->
						Box {
							if (playlistName.isEmpty()) {
								Text(
									text = stringResource(R.string.lbl_playlist_name),
									color = Color.White.copy(alpha = 0.3f),
									fontSize = 16.sp,
								)
							}
							innerTextField()
						}
					},
				)

				Spacer(modifier = Modifier.height(16.dp))

				// Public playlist toggle row
				val switchInteraction = remember { MutableInteractionSource() }
				val switchFocused by switchInteraction.collectIsFocusedAsState()
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.clickable(
							interactionSource = switchInteraction,
							indication = null,
						) { isPublic = !isPublic }
						.focusable(interactionSource = switchInteraction)
						.background(
							if (switchFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent,
						)
						.padding(horizontal = 24.dp, vertical = 14.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = stringResource(R.string.lbl_public_playlist),
						fontSize = 16.sp,
						fontWeight = FontWeight.W400,
						color = if (switchFocused) Color.White else Color.White.copy(alpha = 0.8f),
						modifier = Modifier.weight(1f),
					)
					Switch(
						checked = isPublic,
						onCheckedChange = null,
						colors = SwitchDefaults.colors(
							checkedThumbColor = Color.White,
							checkedTrackColor = Color(0xFF00A4DC),
							uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
							uncheckedTrackColor = Color.White.copy(alpha = 0.2f),
							uncheckedBorderColor = Color.White.copy(alpha = 0.3f),
						),
					)
				}

				Spacer(modifier = Modifier.height(8.dp))

				// Divider
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(1.dp)
						.background(Color.White.copy(alpha = 0.08f)),
				)

				Spacer(modifier = Modifier.height(4.dp))

				// Create & Add button
				if (isCreating) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(24.dp),
						horizontalArrangement = Arrangement.Center,
					) {
						CircularProgressIndicator(
							strokeWidth = 2.dp,
							color = Color(0xFF00A4DC),
						)
					}
				} else {
					GlassDialogRow(
						icon = ImageVector.vectorResource(R.drawable.ic_add),
						label = stringResource(R.string.lbl_create_and_add),
						onClick = {
							val name = playlistName.trim()
							if (name.isBlank()) {
								Toast.makeText(
									context,
									context.getString(R.string.msg_enter_playlist_name),
									Toast.LENGTH_SHORT,
								).show()
								return@GlassDialogRow
							}
							isCreating = true
							CoroutineScope(Dispatchers.Main).launch {
								try {
									withContext(Dispatchers.IO) {
										val createRequest = CreatePlaylistDto(
											name = name,
											ids = listOf(itemId),
											users = emptyList(),
											isPublic = isPublic,
										)
										apiClient.playlistsApi.createPlaylist(createRequest)
									}
									Toast.makeText(
										context,
										context.getString(R.string.msg_playlist_created),
										Toast.LENGTH_SHORT,
									).show()
									onPlaylistCreated()
								} catch (e: Exception) {
									Timber.e(e, "Failed to create playlist")
									Toast.makeText(
										context,
										context.getString(R.string.msg_failed_to_create_playlist),
										Toast.LENGTH_SHORT,
									).show()
									isCreating = false
								}
							}
						},
					)

					// Cancel
					GlassDialogRow(
						label = "Cancel",
						onClick = onDismiss,
						contentColor = Color.White.copy(alpha = 0.5f),
					)
				}
			}
		}

		LaunchedEffect(Unit) {
			nameInputFocusRequester.requestFocus()
		}
	}
}
