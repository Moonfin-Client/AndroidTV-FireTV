package org.jellyfin.androidtv.ui.settings.screen.moonfin

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.JellyseerrRepository
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrHttpClient
import org.jellyfin.androidtv.preference.JellyseerrPreferences
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import timber.log.Timber

@Composable
fun SettingsJellyseerrScreen() {
	val context = LocalContext.current
	val router = LocalRouter.current
	val scope = rememberCoroutineScope()
	
	val jellyseerrPreferences = koinInject<JellyseerrPreferences>(named("global"))
	val jellyseerrRepository = koinInject<JellyseerrRepository>()
	val apiClient = koinInject<ApiClient>()
	val userRepository = koinInject<UserRepository>()
	
	// Get user-specific preferences
	val userId = userRepository.currentUser.value?.id?.toString()
	val userPrefs = userId?.let { 
		koinInject<JellyseerrPreferences>(named("user")) { parametersOf(it) }
	}
	
	// State
	var enabled by rememberPreference(jellyseerrPreferences, JellyseerrPreferences.enabled)
	var blockNsfw by rememberPreference(jellyseerrPreferences, JellyseerrPreferences.blockNsfw)
	
	// Dialog states
	var showServerUrlDialog by remember { mutableStateOf(false) }
	var showJellyfinLoginDialog by remember { mutableStateOf(false) }
	var showLocalLoginDialog by remember { mutableStateOf(false) }
	
	// API key status
	val apiKeyStatus = remember(userPrefs) {
		val apiKey = userPrefs?.get(JellyseerrPreferences.apiKey) ?: ""
		if (apiKey.isNotEmpty()) {
			context.getString(R.string.jellyseerr_api_key_present)
		} else {
			context.getString(R.string.jellyseerr_api_key_absent)
		}
	}
	
	// Server URL display
	val serverUrl = remember { jellyseerrPreferences[JellyseerrPreferences.serverUrl] ?: "" }

	SettingsColumn {
		// Server Configuration
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.jellyseerr_settings).uppercase()) },
				headingContent = { Text(stringResource(R.string.jellyseerr_server_settings)) },
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.jellyseerr_enabled)) },
				captionContent = { Text(stringResource(R.string.jellyseerr_enabled_description)) },
				trailingContent = { Checkbox(checked = enabled) },
				onClick = { enabled = !enabled }
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.jellyseerr_server_url)) },
				captionContent = { Text(if (serverUrl.isNotEmpty()) serverUrl else stringResource(R.string.jellyseerr_server_url_description)) },
				onClick = { showServerUrlDialog = true }
			)
		}

		// Authentication Methods
		item {
			ListSection(
				headingContent = { Text(stringResource(R.string.jellyseerr_auth_method)) },
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_jellyseerr_jellyfish), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.jellyseerr_connect_jellyfin)) },
				captionContent = { Text(stringResource(R.string.jellyseerr_connect_jellyfin_description)) },
				onClick = { 
					if (enabled) {
						showJellyfinLoginDialog = true
					} else {
						Toast.makeText(context, "Please enable Jellyseerr first", Toast.LENGTH_SHORT).show()
					}
				}
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_user), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.jellyseerr_login_local)) },
				captionContent = { Text(stringResource(R.string.jellyseerr_login_local_description)) },
				onClick = { 
					if (enabled) {
						showLocalLoginDialog = true
					} else {
						Toast.makeText(context, "Please enable Jellyseerr first", Toast.LENGTH_SHORT).show()
					}
				}
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_lightbulb), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.jellyseerr_api_key_status)) },
				captionContent = { Text(apiKeyStatus) },
				onClick = { }
			)
		}

		// Content Preferences
		item {
			ListSection(
				headingContent = { Text(stringResource(R.string.pref_customization)) },
			)
		}

		item {
			ListButton(
				headingContent = { Text(stringResource(R.string.jellyseerr_block_nsfw)) },
				captionContent = { Text(stringResource(R.string.jellyseerr_block_nsfw_description)) },
				trailingContent = { Checkbox(checked = blockNsfw) },
				onClick = { 
					if (enabled) {
						blockNsfw = !blockNsfw
					}
				}
			)
		}
		
		// Discover Rows Configuration
		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_grid), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.jellyseerr_rows_title)) },
				captionContent = { Text(stringResource(R.string.jellyseerr_rows_description)) },
				onClick = { 
					if (enabled) {
						router.push(Routes.JELLYSEERR_ROWS)
					}
				}
			)
		}
	}

	// Server URL Dialog
	if (showServerUrlDialog) {
		ServerUrlDialog(
			currentUrl = serverUrl,
			onDismiss = { showServerUrlDialog = false },
			onSave = { url ->
				jellyseerrPreferences[JellyseerrPreferences.serverUrl] = url
				Toast.makeText(context, "Server URL saved", Toast.LENGTH_SHORT).show()
				showServerUrlDialog = false
			}
		)
	}

	// Jellyfin Login Dialog
	if (showJellyfinLoginDialog) {
		val currentServerUrl = jellyseerrPreferences[JellyseerrPreferences.serverUrl] ?: ""
		if (currentServerUrl.isBlank()) {
			Toast.makeText(context, "Please set server URL first", Toast.LENGTH_SHORT).show()
			showJellyfinLoginDialog = false
		} else {
			val currentUser = userRepository.currentUser.value
			val username = currentUser?.name ?: ""
			val jellyfinServerUrl = apiClient.baseUrl ?: ""
			
			JellyfinLoginDialog(
				username = username,
				onDismiss = { showJellyfinLoginDialog = false },
				onConnect = { password ->
					showJellyfinLoginDialog = false
					scope.launch {
						performJellyfinLogin(
							context = context,
							jellyseerrRepository = jellyseerrRepository,
							jellyseerrPreferences = jellyseerrPreferences,
							userRepository = userRepository,
							jellyseerrServerUrl = currentServerUrl,
							username = username,
							password = password,
							jellyfinServerUrl = jellyfinServerUrl
						)
					}
				}
			)
		}
	}

	// Local Login Dialog
	if (showLocalLoginDialog) {
		val currentServerUrl = jellyseerrPreferences[JellyseerrPreferences.serverUrl] ?: ""
		if (currentServerUrl.isBlank()) {
			Toast.makeText(context, "Please set server URL first", Toast.LENGTH_SHORT).show()
			showLocalLoginDialog = false
		} else {
			LocalLoginDialog(
				onDismiss = { showLocalLoginDialog = false },
				onLogin = { email, password ->
					showLocalLoginDialog = false
					scope.launch {
						performLocalLogin(
							context = context,
							jellyseerrRepository = jellyseerrRepository,
							jellyseerrPreferences = jellyseerrPreferences,
							serverUrl = currentServerUrl,
							email = email,
							password = password
						)
					}
				}
			)
		}
	}
}

@Composable
private fun ServerUrlDialog(
	currentUrl: String,
	onDismiss: () -> Unit,
	onSave: (String) -> Unit
) {
	var url by remember { mutableStateOf(currentUrl) }
	
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.jellyseerr_server_url)) },
		text = {
			Column {
				Text(stringResource(R.string.jellyseerr_server_url_description))
				OutlinedTextField(
					value = url,
					onValueChange = { url = it },
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 16.dp),
					placeholder = { Text("http://192.168.1.100:5055") },
					singleLine = true,
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
				)
			}
		},
		confirmButton = {
			TextButton(onClick = { onSave(url.trim()) }) {
				Text("Save")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text("Cancel")
			}
		}
	)
}

@Composable
private fun JellyfinLoginDialog(
	username: String,
	onDismiss: () -> Unit,
	onConnect: (password: String) -> Unit
) {
	var password by remember { mutableStateOf("") }
	
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.jellyseerr_connect_jellyfin)) },
		text = {
			Column {
				Text("Connecting as: $username\n\nEnter your Jellyfin password to authenticate with Jellyseerr")
				OutlinedTextField(
					value = password,
					onValueChange = { password = it },
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 16.dp),
					placeholder = { Text("Password") },
					singleLine = true,
					visualTransformation = PasswordVisualTransformation(),
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
				)
			}
		},
		confirmButton = {
			TextButton(onClick = { onConnect(password) }) {
				Text("Connect")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text("Cancel")
			}
		}
	)
}

@Composable
private fun LocalLoginDialog(
	onDismiss: () -> Unit,
	onLogin: (email: String, password: String) -> Unit
) {
	var email by remember { mutableStateOf("") }
	var password by remember { mutableStateOf("") }
	
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.jellyseerr_login_local)) },
		text = {
			Column {
				Text("Login with your Jellyseerr local account to get a permanent API key")
				OutlinedTextField(
					value = email,
					onValueChange = { email = it },
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 16.dp),
					placeholder = { Text("Email") },
					singleLine = true,
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
				)
				OutlinedTextField(
					value = password,
					onValueChange = { password = it },
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 8.dp),
					placeholder = { Text("Password") },
					singleLine = true,
					visualTransformation = PasswordVisualTransformation(),
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
				)
			}
		},
		confirmButton = {
			TextButton(
				onClick = { 
					if (email.isNotEmpty() && password.isNotEmpty()) {
						onLogin(email.trim(), password)
					}
				}
			) {
				Text("Login")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text("Cancel")
			}
		}
	)
}

private suspend fun performJellyfinLogin(
	context: android.content.Context,
	jellyseerrRepository: JellyseerrRepository,
	jellyseerrPreferences: JellyseerrPreferences,
	userRepository: UserRepository,
	jellyseerrServerUrl: String,
	username: String,
	password: String,
	jellyfinServerUrl: String
) {
	try {
		// Get current Jellyfin user ID and switch cookie storage
		val currentUser = userRepository.currentUser.value
		val userId = currentUser?.id?.toString()
		if (userId != null) {
			JellyseerrHttpClient.switchCookieStorage(userId)
		}
		
		// Store current Jellyfin username
		jellyseerrPreferences[JellyseerrPreferences.lastJellyfinUser] = username
		
		val result = jellyseerrRepository.loginWithJellyfin(username, password, jellyfinServerUrl, jellyseerrServerUrl)
		
		result.onSuccess { user ->
			val apiKey = user.apiKey ?: ""
			
			jellyseerrPreferences[JellyseerrPreferences.serverUrl] = jellyseerrServerUrl
			jellyseerrPreferences[JellyseerrPreferences.enabled] = true
			jellyseerrPreferences[JellyseerrPreferences.lastConnectionSuccess] = true
			
			val authType = if (apiKey.isEmpty()) {
				"session cookie (persists across restarts, ~30 day expiration)"
			} else {
				"API key (permanent)"
			}
			
			Toast.makeText(context, "Connected successfully using $authType!", Toast.LENGTH_LONG).show()
			Timber.d("Jellyseerr: Jellyfin authentication successful")
		}.onFailure { error ->
			Toast.makeText(context, "Connection failed: ${error.message}", Toast.LENGTH_LONG).show()
			Timber.e(error, "Jellyseerr: Jellyfin authentication failed")
		}
	} catch (e: Exception) {
		Toast.makeText(context, "Connection error: ${e.message}", Toast.LENGTH_LONG).show()
		Timber.e(e, "Jellyseerr: Connection failed")
	}
}

private suspend fun performLocalLogin(
	context: android.content.Context,
	jellyseerrRepository: JellyseerrRepository,
	jellyseerrPreferences: JellyseerrPreferences,
	serverUrl: String,
	email: String,
	password: String
) {
	try {
		Timber.d("Jellyseerr: Performing local login to: $serverUrl")
		
		val result = jellyseerrRepository.loginLocal(email, password, serverUrl)
		
		result.onSuccess { user ->
			Timber.i("Jellyseerr: Local login successful - User ID: ${user.id}, Username: ${user.username}")
			jellyseerrPreferences[JellyseerrPreferences.enabled] = true
			jellyseerrPreferences[JellyseerrPreferences.lastConnectionSuccess] = true
			
			Toast.makeText(context, "Logged in successfully with permanent API key!", Toast.LENGTH_LONG).show()
		}.onFailure { error ->
			Timber.e(error, "Jellyseerr: Local login failed - ${error.message}")
			Toast.makeText(context, "Login failed: ${error.message}", Toast.LENGTH_LONG).show()
		}
	} catch (e: Exception) {
		Timber.e(e, "Jellyseerr: Local login exception")
		Toast.makeText(context, "Login error: ${e.message}", Toast.LENGTH_LONG).show()
	}
}
