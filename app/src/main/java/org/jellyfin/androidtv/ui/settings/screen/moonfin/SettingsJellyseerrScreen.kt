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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import timber.log.Timber

@Composable
fun SettingsJellyseerrScreen() {
	val context = LocalContext.current
	val router = LocalRouter.current
	val scope = rememberCoroutineScope()
	
	val jellyseerrPreferences = koinInject<JellyseerrPreferences>(named("global"))
	val jellyseerrRepository = koinInject<JellyseerrRepository>()
	val userPreferences = koinInject<UserPreferences>()
	val apiClient = koinInject<ApiClient>()
	val userRepository = koinInject<UserRepository>()
	
	// Get user-specific preferences (with migration from global if needed)
	val userId = userRepository.currentUser.value?.id?.toString()
	val userPrefs = remember(userId) {
		userId?.let { JellyseerrPreferences.migrateToUserPreferences(context, it) }
	}
	
	// State - all preferences are now per-user
	var enabled by rememberPreference(userPrefs ?: jellyseerrPreferences, JellyseerrPreferences.enabled)
	var blockNsfw by rememberPreference(userPrefs ?: jellyseerrPreferences, JellyseerrPreferences.blockNsfw)
	
	// Dialog states
	var showServerUrlDialog by remember { mutableStateOf(false) }
	var showJellyfinLoginDialog by remember { mutableStateOf(false) }
	var showLocalLoginDialog by remember { mutableStateOf(false) }
	var showApiKeyLoginDialog by remember { mutableStateOf(false) }
	var showLogoutConfirmDialog by remember { mutableStateOf(false) }
	
	var apiKey by remember { mutableStateOf(userPrefs?.get(JellyseerrPreferences.apiKey) ?: "") }
	var authMethod by remember { mutableStateOf(userPrefs?.get(JellyseerrPreferences.authMethod) ?: "") }

	val isMoonfinMode by jellyseerrRepository.isMoonfinMode.collectAsState()
	val moonfinDisplayName = remember(userPrefs, isMoonfinMode) {
		userPrefs?.get(JellyseerrPreferences.moonfinDisplayName) ?: ""
	}
	var showMoonfinDisconnectDialog by remember { mutableStateOf(false) }
	
	LaunchedEffect(userPrefs) {
		apiKey = userPrefs?.get(JellyseerrPreferences.apiKey) ?: ""
		authMethod = userPrefs?.get(JellyseerrPreferences.authMethod) ?: ""
	}
	
	val apiKeyStatus = when {
		apiKey.isNotEmpty() -> "Permanent API key active"
		authMethod.isNotEmpty() -> "Cookie-based auth (expires ~30 days)"
		else -> context.getString(R.string.jellyseerr_not_logged_in)
	}
	
	val serverUrl = remember { userPrefs?.get(JellyseerrPreferences.serverUrl) ?: "" }
	var isReconnecting by remember { mutableStateOf(false) }

	SettingsColumn {
		if (isMoonfinMode) {
			// Moonfin Proxy Status
			item {
				ListSection(
					overlineContent = { Text(stringResource(R.string.jellyseerr_settings).uppercase()) },
					headingContent = { Text(stringResource(R.string.jellyseerr_moonfin_proxy)) },
					captionContent = { Text(stringResource(R.string.jellyseerr_moonfin_proxy_status)) },
				)
			}

			item {
				val statusCaption = if (moonfinDisplayName.isNotEmpty()) {
					stringResource(R.string.jellyseerr_moonfin_connected, moonfinDisplayName)
				} else {
					stringResource(R.string.jellyseerr_moonfin_not_authenticated)
				}
				ListButton(
					leadingContent = { Icon(painterResource(R.drawable.ic_moonfin), contentDescription = null) },
					headingContent = { Text(stringResource(R.string.jellyseerr_moonfin_proxy)) },
					captionContent = { Text(statusCaption) },
					onClick = { }
				)
			}

			item {
				ListButton(
					leadingContent = { Icon(painterResource(R.drawable.ic_logout), contentDescription = null) },
					headingContent = { Text(stringResource(R.string.jellyseerr_moonfin_disconnect)) },
					captionContent = { Text(stringResource(R.string.jellyseerr_moonfin_disconnect_description)) },
					onClick = { showMoonfinDisconnectDialog = true }
				)
			}
		} else {
			// Reconnect via Moonfin Plugin option (shown when plugin sync is enabled)
			if (userPreferences[UserPreferences.pluginSyncEnabled]) {
				item {
					ListButton(
						leadingContent = { Icon(painterResource(R.drawable.ic_moonfin), contentDescription = null) },
						headingContent = { Text(stringResource(R.string.jellyseerr_moonfin_reconnect)) },
						captionContent = { Text(stringResource(R.string.jellyseerr_moonfin_reconnect_description)) },
						onClick = {
							if (!isReconnecting) {
								isReconnecting = true
								scope.launch {
									val baseUrl = apiClient.baseUrl
									val token = apiClient.accessToken
									if (!baseUrl.isNullOrBlank() && !token.isNullOrBlank()) {
										val result = jellyseerrRepository.configureWithMoonfin(baseUrl, token)
										result.onSuccess { status ->
											if (status.authenticated || status.enabled) {
												Toast.makeText(context, context.getString(R.string.jellyseerr_moonfin_reconnect_success), Toast.LENGTH_SHORT).show()
											} else {
												Toast.makeText(context, context.getString(R.string.jellyseerr_moonfin_not_enabled), Toast.LENGTH_SHORT).show()
											}
										}.onFailure {
											Toast.makeText(context, context.getString(R.string.jellyseerr_moonfin_reconnect_failed), Toast.LENGTH_SHORT).show()
										}
									}
									isReconnecting = false
								}
							}
						}
					)
				}
			}

			// Direct Mode — Server Configuration
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
					headingContent = { Text(stringResource(R.string.jellyseerr_login_api_key)) },
					captionContent = { Text(stringResource(R.string.jellyseerr_login_api_key_description)) },
					onClick = { 
						if (enabled) {
							showApiKeyLoginDialog = true
						} else {
							Toast.makeText(context, "Please enable Jellyseerr first", Toast.LENGTH_SHORT).show()
						}
					}
				)
			}

			item {
				ListButton(
					leadingContent = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
					headingContent = { Text(stringResource(R.string.jellyseerr_api_key_status)) },
					captionContent = { Text(apiKeyStatus) },
					onClick = { }
				)
			}

			item {
				ListButton(
					leadingContent = { Icon(painterResource(R.drawable.ic_logout), contentDescription = null) },
					headingContent = { Text(stringResource(R.string.jellyseerr_logout)) },
					captionContent = { Text(stringResource(R.string.jellyseerr_logout_description)) },
					onClick = { showLogoutConfirmDialog = true }
				)
			}
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
				userPrefs?.set(JellyseerrPreferences.serverUrl, url)
				Toast.makeText(context, "Server URL saved", Toast.LENGTH_SHORT).show()
				showServerUrlDialog = false
			}
		)
	}

	// Jellyfin Login Dialog
	if (showJellyfinLoginDialog) {
		val currentServerUrl = userPrefs?.get(JellyseerrPreferences.serverUrl) ?: ""
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
		val currentServerUrl = userPrefs?.get(JellyseerrPreferences.serverUrl) ?: ""
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

	// API Key Login Dialog
	if (showApiKeyLoginDialog) {
		val currentServerUrl = userPrefs?.get(JellyseerrPreferences.serverUrl) ?: ""
		if (currentServerUrl.isBlank()) {
			Toast.makeText(context, "Please set server URL first", Toast.LENGTH_SHORT).show()
			showApiKeyLoginDialog = false
		} else {
			ApiKeyLoginDialog(
				onDismiss = { showApiKeyLoginDialog = false },
				onLogin = { apiKey ->
					showApiKeyLoginDialog = false
					scope.launch {
						performApiKeyLogin(
							context = context,
							jellyseerrRepository = jellyseerrRepository,
							jellyseerrPreferences = jellyseerrPreferences,
							serverUrl = currentServerUrl,
							apiKey = apiKey
						)
					}
				}
			)
		}
	}

	// Logout Confirmation Dialog
	if (showLogoutConfirmDialog) {
		AlertDialog(
			onDismissRequest = { showLogoutConfirmDialog = false },
			title = { Text(stringResource(R.string.jellyseerr_logout_confirm_title)) },
			text = { Text(stringResource(R.string.jellyseerr_logout_confirm_message)) },
			confirmButton = {
				TextButton(
					onClick = {
						showLogoutConfirmDialog = false
						scope.launch {
							jellyseerrRepository.logout()
							Toast.makeText(context, context.getString(R.string.jellyseerr_logout_success), Toast.LENGTH_SHORT).show()
						}
					}
				) {
					Text("Log Out")
				}
			},
			dismissButton = {
				TextButton(onClick = { showLogoutConfirmDialog = false }) {
					Text("Cancel")
				}
			}
		)
	}

	// Moonfin Disconnect Confirmation Dialog
	if (showMoonfinDisconnectDialog) {
		AlertDialog(
			onDismissRequest = { showMoonfinDisconnectDialog = false },
			title = { Text(stringResource(R.string.jellyseerr_moonfin_disconnect)) },
			text = { Text(stringResource(R.string.jellyseerr_moonfin_disconnect_description)) },
			confirmButton = {
				TextButton(
					onClick = {
						showMoonfinDisconnectDialog = false
						scope.launch {
							jellyseerrRepository.logoutMoonfin()
							Toast.makeText(context, context.getString(R.string.jellyseerr_logout_success), Toast.LENGTH_SHORT).show()
						}
					}
				) {
					Text("Disconnect")
				}
			},
			dismissButton = {
				TextButton(onClick = { showMoonfinDisconnectDialog = false }) {
					Text("Cancel")
				}
			}
		)
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

@Composable
private fun ApiKeyLoginDialog(
	onDismiss: () -> Unit,
	onLogin: (apiKey: String) -> Unit
) {
	var apiKey by remember { mutableStateOf("") }
	
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.jellyseerr_login_api_key)) },
		text = {
			Column {
				Text(stringResource(R.string.jellyseerr_api_key_input_description))
				OutlinedTextField(
					value = apiKey,
					onValueChange = { apiKey = it },
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 16.dp),
					placeholder = { Text(stringResource(R.string.jellyseerr_api_key_input)) },
					singleLine = true
				)
			}
		},
		confirmButton = {
			TextButton(
				onClick = { 
					if (apiKey.isNotEmpty()) {
						onLogin(apiKey.trim())
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
	// Input validation
	if (username.isBlank() || password.isBlank() || jellyfinServerUrl.isBlank() || jellyseerrServerUrl.isBlank()) {
		Toast.makeText(context, "All fields are required", Toast.LENGTH_SHORT).show()
		return
	}
	
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
			
			val authType = if (apiKey.isNotEmpty()) {
				"permanent API key"
			} else {
				"cookie-based auth (expires ~30 days)"
			}
			
			Toast.makeText(context, "Connected successfully using $authType!", Toast.LENGTH_LONG).show()
			Timber.d("Jellyseerr: Jellyfin authentication successful")
		}.onFailure { error ->
			val errorMessage = when {
				error.message?.contains("configuration error") == true -> {
					"Server Configuration Error\n\n${error.message}"
				}
				error.message?.contains("Authentication failed") == true -> {
					"Authentication Failed\n\n${error.message}"
				}
				else -> "Connection failed: ${error.message}"
			}
			Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
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
		val result = jellyseerrRepository.loginLocal(email, password, serverUrl)
		
		result.onSuccess { user ->
			jellyseerrPreferences[JellyseerrPreferences.enabled] = true
			jellyseerrPreferences[JellyseerrPreferences.lastConnectionSuccess] = true
			
			val message = if (user.apiKey?.isNotEmpty() == true) {
				"Logged in successfully using permanent API key!"
			} else {
				"Logged in successfully using cookie-based auth (expires ~30 days)"
			}
			Toast.makeText(context, message, Toast.LENGTH_LONG).show()
		}.onFailure { error ->
			Timber.e(error, "Jellyseerr: Local login failed")
			Toast.makeText(context, "Login failed: ${error.message}", Toast.LENGTH_LONG).show()
		}
	} catch (e: Exception) {
		Timber.e(e, "Jellyseerr: Local login exception")
		Toast.makeText(context, "Login error: ${e.message}", Toast.LENGTH_LONG).show()
	}
}

private suspend fun performApiKeyLogin(
	context: android.content.Context,
	jellyseerrRepository: JellyseerrRepository,
	jellyseerrPreferences: JellyseerrPreferences,
	serverUrl: String,
	apiKey: String
) {
	try {
		val result = jellyseerrRepository.loginWithApiKey(apiKey, serverUrl)
		
		result.onSuccess {
			jellyseerrPreferences[JellyseerrPreferences.enabled] = true
			jellyseerrPreferences[JellyseerrPreferences.lastConnectionSuccess] = true
			Toast.makeText(context, "Logged in successfully using permanent API key!", Toast.LENGTH_LONG).show()
		}.onFailure { error ->
			Timber.e(error, "Jellyseerr: API key login failed")
			Toast.makeText(context, "Login failed: ${error.message}", Toast.LENGTH_LONG).show()
		}
	} catch (e: Exception) {
		Timber.e(e, "Jellyseerr: API key login exception")
		Toast.makeText(context, "Login error: ${e.message}", Toast.LENGTH_LONG).show()
	}
}
