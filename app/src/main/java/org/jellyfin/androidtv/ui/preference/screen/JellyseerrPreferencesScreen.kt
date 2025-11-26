package org.jellyfin.androidtv.ui.preference.screen

import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.JellyseerrFetchLimit
import org.jellyfin.androidtv.data.repository.JellyseerrRepository
import org.jellyfin.androidtv.preference.JellyseerrPreferences
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.action
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.enum
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.android.ext.android.inject
import timber.log.Timber

class JellyseerrPreferencesScreen : OptionsFragment() {
	private val jellyseerrPreferences: JellyseerrPreferences by inject()
	private val jellyseerrRepository: JellyseerrRepository by inject()
	private val apiClient: ApiClient by inject()
	private val userRepository: UserRepository by inject()

	override val screen by optionsScreen {
		setTitle(R.string.jellyseerr_settings)

		category {
			setTitle(R.string.jellyseerr_server_settings)

			checkbox {
				setTitle(R.string.jellyseerr_enabled)
				setContent(R.string.jellyseerr_enabled_description)
				bind(jellyseerrPreferences, JellyseerrPreferences.enabled)
			}

			action {
				setTitle(R.string.jellyseerr_server_url)
				setContent(R.string.jellyseerr_server_url_description)
				icon = R.drawable.ic_settings
				onActivate = {
					showServerUrlDialog()
				}
			}

			action {
				setTitle(R.string.jellyseerr_connect_jellyfin)
				setContent(R.string.jellyseerr_connect_jellyfin_description)
				icon = R.drawable.ic_jellyseerr_jellyfish
				depends { jellyseerrPreferences[JellyseerrPreferences.enabled] }
				onActivate = {
					connectWithJellyfin()
				}
			}

			action {
				setTitle(R.string.jellyseerr_test_connection)
				setContent(R.string.jellyseerr_test_connection_description)
				icon = R.drawable.ic_check
				depends { jellyseerrPreferences[JellyseerrPreferences.enabled] }
				onActivate = {
					testConnection()
				}
			}

			enum<JellyseerrFetchLimit> {
				setTitle(R.string.jellyseerr_fetch_limit_title)
				bind(jellyseerrPreferences, JellyseerrPreferences.fetchLimit)
				depends { jellyseerrPreferences[JellyseerrPreferences.enabled] }
			}
		}
	}

	private fun showServerUrlDialog() {
		val currentUrl = jellyseerrPreferences[JellyseerrPreferences.serverUrl] ?: ""
		val input = EditText(requireContext()).apply {
			setText(currentUrl)
			hint = "http://192.168.1.100:5055"
			inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
		}

		AlertDialog.Builder(requireContext())
			.setTitle(R.string.jellyseerr_server_url)
			.setMessage(R.string.jellyseerr_server_url_description)
			.setView(input)
			.setPositiveButton("Save") { _, _ ->
				val url = input.text.toString().trim()
				if (url.isNotEmpty()) {
					jellyseerrPreferences[JellyseerrPreferences.serverUrl] = url
					Toast.makeText(requireContext(), "Server URL saved", Toast.LENGTH_SHORT).show()
					Timber.d("Jellyseerr: Server URL saved: $url")
				}
			}
			.setNegativeButton("Cancel", null)
			.show()
	}

	private fun connectWithJellyfin() {
		val serverUrl = jellyseerrPreferences[JellyseerrPreferences.serverUrl]
		
		if (serverUrl.isNullOrBlank()) {
			Toast.makeText(requireContext(), "Please set server URL first", Toast.LENGTH_SHORT).show()
			return
		}

		// Get current Jellyfin user
		val currentUser = userRepository.currentUser.value
		val username = currentUser?.name
		if (username.isNullOrBlank()) {
			Toast.makeText(requireContext(), "Could not determine current user", Toast.LENGTH_SHORT).show()
			return
		}

		// Get Jellyfin server URL
		val jellyfinServerUrl = apiClient.baseUrl
		if (jellyfinServerUrl.isNullOrBlank()) {
			Toast.makeText(requireContext(), "Could not determine Jellyfin server URL", Toast.LENGTH_SHORT).show()
			return
		}

		// Show password dialog
		val passwordInput = EditText(requireContext()).apply {
			hint = "Enter your Jellyfin password"
			inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
		}

		val layout = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(48, 32, 48, 32)
			addView(TextView(requireContext()).apply {
				text = "Connecting as: $username\n\nEnter your Jellyfin password to authenticate with Jellyseerr"
				setPadding(0, 0, 0, 32)
			})
			addView(passwordInput)
		}

		AlertDialog.Builder(requireContext())
			.setTitle(R.string.jellyseerr_connect_jellyfin)
			.setView(layout)
			.setPositiveButton("Connect") { _, _ ->
				val password = passwordInput.text.toString().trim()
				
				if (password.isEmpty()) {
					Toast.makeText(requireContext(), "Password is required", Toast.LENGTH_SHORT).show()
					return@setPositiveButton
				}

				performJellyfinLogin(serverUrl, username, password, jellyfinServerUrl)
			}
			.setNegativeButton("Cancel", null)
			.show()
	}

	private fun performJellyfinLogin(
		jellyseerrServerUrl: String,
		username: String,
		password: String,
		jellyfinServerUrl: String
	) {
		Toast.makeText(requireContext(), "Connecting...", Toast.LENGTH_SHORT).show()

		lifecycleScope.launch {
			try {
				val result = jellyseerrRepository.loginWithJellyfin(username, password, jellyfinServerUrl, jellyseerrServerUrl)
				
				result.onSuccess { user ->
					// Get API key if available, otherwise use empty string for cookie auth
					val apiKey = user.apiKey ?: ""

					// Save credentials
					jellyseerrPreferences[JellyseerrPreferences.serverUrl] = jellyseerrServerUrl
					jellyseerrPreferences[JellyseerrPreferences.enabled] = true
					jellyseerrPreferences[JellyseerrPreferences.lastConnectionSuccess] = true
					
					// Initialize connection
					jellyseerrRepository.initialize(jellyseerrServerUrl, apiKey)
					
					val authType = if (apiKey.isEmpty()) {
						"session cookie (persists across restarts, ~30 day expiration)"
					} else {
						"API key (permanent)"
					}
					
					Toast.makeText(
						requireContext(),
						"Connected successfully using $authType!",
						Toast.LENGTH_LONG
					).show()
					
					Timber.d("Jellyseerr: Jellyfin authentication successful (using ${if (apiKey.isEmpty()) "cookie" else "API key"} authentication)")
				}.onFailure { error ->
					Toast.makeText(
						requireContext(),
						"Connection failed: ${error.message}",
						Toast.LENGTH_LONG
					).show()
					Timber.e(error, "Jellyseerr: Jellyfin authentication failed")
				}
			} catch (e: Exception) {
				Toast.makeText(
					requireContext(),
					"Connection error: ${e.message}",
					Toast.LENGTH_LONG
				).show()
				Timber.e(e, "Jellyseerr: Connection failed")
			}
		}
	}

	private fun testConnection() {
		val serverUrl = jellyseerrPreferences[JellyseerrPreferences.serverUrl]
		
		if (serverUrl.isNullOrBlank()) {
			Toast.makeText(requireContext(), "Please set server URL first", Toast.LENGTH_SHORT).show()
			return
		}

		Toast.makeText(requireContext(), "Testing connection...", Toast.LENGTH_SHORT).show()

		lifecycleScope.launch {
			try {
				// Initialize with empty API key (uses cookie auth)
				val result = jellyseerrRepository.initialize(serverUrl, "")
				
				result.onSuccess {
					Toast.makeText(
						requireContext(),
						R.string.jellyseerr_connection_success,
						Toast.LENGTH_SHORT
					).show()
					Timber.d("Jellyseerr: Connection test successful")
				}.onFailure { error ->
					Toast.makeText(
						requireContext(),
						"Connection failed: ${error.message}",
						Toast.LENGTH_LONG
					).show()
					Timber.e(error, "Jellyseerr: Connection test failed")
				}
			} catch (e: Exception) {
				Toast.makeText(
					requireContext(),
					"Connection error: ${e.message}",
					Toast.LENGTH_LONG
				).show()
				Timber.e(e, "Jellyseerr: Test connection failed")
			}
		}
	}
}
