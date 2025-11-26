package org.jellyfin.androidtv.ui.jellyseerr

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.JellyseerrFetchLimit
import org.jellyfin.androidtv.databinding.FragmentJellyseerrSettingsBinding
import org.jellyfin.androidtv.preference.JellyseerrPreferences
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class SettingsFragment : Fragment(R.layout.fragment_jellyseerr_settings) {
	private val viewModel: JellyseerrViewModel by viewModel()
	private val preferences: JellyseerrPreferences by inject()
	private val apiClient: ApiClient by inject()
	private val userPreferences: UserPreferences by inject()
	private val userRepository: UserRepository by inject()

	private var _binding: FragmentJellyseerrSettingsBinding? = null
	private val binding get() = _binding!!

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		_binding = FragmentJellyseerrSettingsBinding.bind(view)

		setupUI()
		setupObservers()
		loadSavedSettings()
	}

	private fun setupUI() {
		binding.connectJellyfinButton.setOnClickListener {
			connectWithJellyfin()
		}

		binding.testConnectionButton.setOnClickListener {
			testConnection()
		}

		// Enable/disable toggle
		binding.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
			preferences[JellyseerrPreferences.enabled] = isChecked
			preferences[JellyseerrPreferences.showInToolbar] = isChecked
			binding.settingsGroup.alpha = if (isChecked) 1f else 0.5f
		}

		// Setup fetch limit spinner
		setupFetchLimitSpinner()
	}

	private fun setupFetchLimitSpinner() {
		val fetchLimitOptions = JellyseerrFetchLimit.values()
		val displayNames = fetchLimitOptions.map { getString(it.nameRes) }
		
		val adapter = ArrayAdapter(
			requireContext(),
			android.R.layout.simple_spinner_item,
			displayNames
		).apply {
			setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
		}
		
		binding.fetchLimitSpinner.adapter = adapter
		
		// Set initial selection
		val currentLimit = preferences[JellyseerrPreferences.fetchLimit]
		val currentIndex = fetchLimitOptions.indexOf(currentLimit)
		if (currentIndex >= 0) {
			binding.fetchLimitSpinner.setSelection(currentIndex)
		}
		
		// Handle selection changes
		binding.fetchLimitSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				val selectedLimit = fetchLimitOptions[position]
				preferences[JellyseerrPreferences.fetchLimit] = selectedLimit
				Timber.d("Fetch limit changed to: ${selectedLimit.limit} items")
			}
			
			override fun onNothingSelected(parent: AdapterView<*>?) {
				// Do nothing
			}
		}
	}

	private fun setupObservers() {
		// Monitor connection state
		lifecycleScope.launch {
			viewModel.isAvailable.collect { isAvailable ->
				updateConnectionStatus(isAvailable)
			}
		}

		// Monitor loading state for user feedback
		lifecycleScope.launch {
			viewModel.loadingState.collect { state ->
				when (state) {
					is JellyseerrLoadingState.Loading -> {
						binding.testConnectionButton.isEnabled = false
						binding.statusText.text = "Testing connection..."
					}
					is JellyseerrLoadingState.Success -> {
						binding.testConnectionButton.isEnabled = true
						binding.statusText.text = "✓ Connected successfully!"
						binding.statusIcon.setImageResource(R.drawable.ic_check)
						Toast.makeText(
							requireContext(),
							"Connected to Jellyseerr!",
							Toast.LENGTH_SHORT
						).show()
					}
					is JellyseerrLoadingState.Error -> {
						binding.testConnectionButton.isEnabled = true
						binding.statusText.text = "✗ Connection failed: ${state.message}"
						Toast.makeText(
							requireContext(),
							"Connection error: ${state.message}",
							Toast.LENGTH_LONG
						).show()
					}
					is JellyseerrLoadingState.Idle -> {
						binding.testConnectionButton.isEnabled = true
						binding.statusText.text = "Not tested"
					}
				}
			}
		}
	}

	private fun loadSavedSettings() {
		// Load saved URL
		val savedUrl = preferences[JellyseerrPreferences.serverUrl]
		val isEnabled = preferences[JellyseerrPreferences.enabled]

		binding.serverUrlInput.setText(savedUrl)
		binding.enabledSwitch.isChecked = isEnabled

		// Update UI based on enabled state
		binding.settingsGroup.alpha = if (isEnabled) 1f else 0.5f

		// Show last connection status
		if (savedUrl.isNotBlank()) {
			val wasSuccessful = preferences[JellyseerrPreferences.lastConnectionSuccess]
			if (wasSuccessful) {
				binding.statusText.text = "✓ Connected"
				binding.statusIcon.setImageResource(R.drawable.ic_check)
			}
		}
	}

	private fun testConnection() {
		val serverUrl = preferences[JellyseerrPreferences.serverUrl]

		// Validate that connection was set up
		if (serverUrl.isNullOrEmpty()) {
			Toast.makeText(requireContext(), "Please connect with Jellyfin first", Toast.LENGTH_SHORT).show()
			return
		}

		// Test connection via ViewModel (using cookie auth)
		viewModel.initializeJellyseerr(serverUrl, "")

		Timber.d("Testing Jellyseerr connection to: $serverUrl")
	}

	private fun connectWithJellyfin() {
		val jellyseerrServerUrl = binding.serverUrlInput.text.toString().trim()
		
		if (jellyseerrServerUrl.isEmpty()) {
			Toast.makeText(requireContext(), "Please enter Jellyseerr server URL first", Toast.LENGTH_LONG).show()
			return
		}

		// Get the Jellyfin server URL from current connection
		val jellyfinServerUrl = apiClient.baseUrl ?: run {
			Toast.makeText(requireContext(), "Could not determine Jellyfin server URL", Toast.LENGTH_LONG).show()
			return
		}

		// Get the current logged-in user's username
		val currentUser = userRepository.currentUser.value
		val username = currentUser?.name ?: run {
			Toast.makeText(requireContext(), "Could not determine current user", Toast.LENGTH_LONG).show()
			return
		}

		// Prompt only for password (username is pre-filled from current session)
		val passwordInput = android.widget.EditText(requireContext()).apply {
			hint = "Enter your Jellyfin password"
			inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
		}

		val layout = android.widget.LinearLayout(requireContext()).apply {
			orientation = android.widget.LinearLayout.VERTICAL
			setPadding(48, 32, 48, 32)
			addView(android.widget.TextView(requireContext()).apply {
				text = "Connecting as: $username\n\nEnter your Jellyfin password to authenticate with Jellyseerr"
				setPadding(0, 0, 0, 32)
			})
			addView(passwordInput)
		}

		android.app.AlertDialog.Builder(requireContext())
			.setTitle("Connect with Jellyfin")
			.setView(layout)
			.setPositiveButton("Connect") { _, _ ->
				val password = passwordInput.text.toString().trim()
				
				if (password.isEmpty()) {
					Toast.makeText(
						requireContext(),
						"Password is required",
						Toast.LENGTH_SHORT
					).show()
					return@setPositiveButton
				}

				performJellyfinLogin(jellyseerrServerUrl, username, password, jellyfinServerUrl)
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
		binding.connectJellyfinButton.isEnabled = false
		binding.statusText.text = "Connecting..."

		lifecycleScope.launch {
			try {
				val result = viewModel.loginWithJellyfin(username, password, jellyfinServerUrl, jellyseerrServerUrl)
				
				result.onSuccess { user ->
					// Save credentials (using cookie-based auth)
					preferences[JellyseerrPreferences.serverUrl] = jellyseerrServerUrl
					preferences[JellyseerrPreferences.enabled] = true
					binding.enabledSwitch.isChecked = true
					
				// Initialize connection (using cookie-based auth)
				viewModel.initializeJellyseerr(jellyseerrServerUrl, "")
				
				Toast.makeText(
					requireContext(),
					"Connected successfully using session cookie!",
					Toast.LENGTH_LONG
				).show()
				
				Timber.d("Jellyseerr: Jellyfin authentication successful using cookie authentication")
				}.onFailure { error ->
					Toast.makeText(
						requireContext(),
						"Connection failed: ${error.message}",
						Toast.LENGTH_LONG
					).show()
					binding.statusText.text = "✗ Connection failed"
					Timber.e(error, "Jellyseerr: Jellyfin authentication failed")
				}
			} finally {
				binding.connectJellyfinButton.isEnabled = true
			}
		}
	}



	private fun updateConnectionStatus(isAvailable: Boolean) {
		if (isAvailable) {
			// Update preferences to record successful connection
			preferences[JellyseerrPreferences.lastConnectionSuccess] = true
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}
