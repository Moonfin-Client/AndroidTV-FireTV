package org.jellyfin.androidtv.ui.startup.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ApiClientErrorLoginState
import org.jellyfin.androidtv.auth.model.AuthenticatedState
import org.jellyfin.androidtv.auth.model.AuthenticatingState
import org.jellyfin.androidtv.auth.model.PrivateUser
import org.jellyfin.androidtv.auth.model.RequireSignInState
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.ServerUnavailableState
import org.jellyfin.androidtv.auth.model.ServerVersionNotSupported
import org.jellyfin.androidtv.auth.model.User
import org.jellyfin.androidtv.auth.repository.AuthenticationRepository
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.databinding.FragmentServerBinding
import org.jellyfin.androidtv.ui.ServerButtonView
import org.jellyfin.androidtv.ui.card.UserCardView
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import org.jellyfin.androidtv.util.ListAdapter
import org.jellyfin.androidtv.util.MarkdownRenderer
import org.jellyfin.androidtv.util.PinCodeUtil
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class ServerFragment : Fragment() {
	companion object {
		const val ARG_SERVER_ID = "server_id"
	}

	private val startupViewModel: StartupViewModel by activityViewModel()
	private val markdownRenderer: MarkdownRenderer by inject()
	private val authenticationRepository: AuthenticationRepository by inject()
	private val serverUserRepository: ServerUserRepository by inject()
	private val backgroundService: BackgroundService by inject()
	private var _binding: FragmentServerBinding? = null
	private val binding get() = _binding!!

	private var pendingPinUser: User? = null
	private var pendingPinServer: Server? = null
	private var previousFocusedView: View? = null

	private val serverIdArgument get() = arguments?.getString(ARG_SERVER_ID)?.ifBlank { null }?.toUUIDOrNull()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val server = serverIdArgument?.let(startupViewModel::getServer)

		if (server == null) {
			navigateFragment<SelectServerFragment>(keepToolbar = true, keepHistory = false)
			return null
		}

		_binding = FragmentServerBinding.inflate(inflater, container, false)

	val userAdapter = UserAdapter(requireContext(), server, startupViewModel, authenticationRepository, serverUserRepository)
	userAdapter.onItemPressed = { user ->
		// Check if user has PIN protection enabled
		if (PinCodeUtil.isPinEnabled(requireContext(), user.id)) {
			showPinEntry(server, user)
		} else {
			authenticateUser(server, user)
		}
	}
	binding.users.adapter = userAdapter

	// Setup PIN entry controls
	setupPinEntry()

	startupViewModel.users
		.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
		.onEach { users ->
				userAdapter.items = users
				
				// Calculate centering padding once layout is complete
				binding.users.post {
					val parentWidth = (binding.users.parent as? View)?.width ?: 0
					
					if (parentWidth > 0 && users.isNotEmpty()) {
						val density = resources.displayMetrics.density
						val cardWidthPx = (130 * density).toInt()
						val itemSpacingPx = (16 * density).toInt()
						val totalContentWidth = (cardWidthPx * users.size) + (itemSpacingPx * (users.size - 1))
						val padding = maxOf(0, (parentWidth - totalContentWidth) / 2)
						binding.users.setPadding(padding, 0, padding, 0)
					}
				}

				binding.users.isFocusable = users.any()
				binding.noUsersWarning.isVisible = users.isEmpty()
				
				// Show edit button if there are users, hide the action buttons
				val hasUsers = users.isNotEmpty()
				binding.editButton.isVisible = hasUsers
				binding.actionsContainer.isVisible = !hasUsers
				
				binding.root.requestFocus()
			}.launchIn(viewLifecycleOwner.lifecycleScope)

		// Setup edit button to toggle actions visibility
		binding.editButton.setOnClickListener {
			binding.actionsContainer.isVisible = true
			binding.editButton.isVisible = false
			binding.addUserButton.requestFocus()
		}

		startupViewModel.loadUsers(server)

		onServerChange(server)

		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				val updated = startupViewModel.updateServer(server)
				if (updated) startupViewModel.getServer(server.id)?.let(::onServerChange)
			}
		}

		return binding.root
	}

	override fun onDestroyView() {
		super.onDestroyView()

		_binding = null
	}

	private fun setupPinEntry() {
		// Submit on Enter/Done key
		binding.pinInput.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
				verifyAndSubmitPin()
				true
			} else {
				false
			}
		}

		// Create a shared key listener for all PIN entry elements
		val pinKeyListener = View.OnKeyListener { view, keyCode, event ->
			if (event.action == android.view.KeyEvent.ACTION_DOWN) {
				when (keyCode) {
					android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_DPAD_CENTER -> {
						// Let buttons handle their own click, only intercept for text field
						if (view == binding.pinInput) {
							verifyAndSubmitPin()
							true
						} else {
							false // Let the button's click listener handle it
						}
					}
					// Handle numeric key presses
					android.view.KeyEvent.KEYCODE_0 -> {
						appendPinDigit("0")
						true
					}
					android.view.KeyEvent.KEYCODE_1 -> {
						appendPinDigit("1")
						true
					}
					android.view.KeyEvent.KEYCODE_2 -> {
						appendPinDigit("2")
						true
					}
					android.view.KeyEvent.KEYCODE_3 -> {
						appendPinDigit("3")
						true
					}
					android.view.KeyEvent.KEYCODE_4 -> {
						appendPinDigit("4")
						true
					}
					android.view.KeyEvent.KEYCODE_5 -> {
						appendPinDigit("5")
						true
					}
					android.view.KeyEvent.KEYCODE_6 -> {
						appendPinDigit("6")
						true
					}
					android.view.KeyEvent.KEYCODE_7 -> {
						appendPinDigit("7")
						true
					}
					android.view.KeyEvent.KEYCODE_8 -> {
						appendPinDigit("8")
						true
					}
					android.view.KeyEvent.KEYCODE_9 -> {
						appendPinDigit("9")
						true
					}
					android.view.KeyEvent.KEYCODE_DEL -> {
						// Delete key removes last digit
						val text = binding.pinInput.text
						if (text != null && text.isNotEmpty()) {
							text.delete(text.length - 1, text.length)
							binding.pinError.isVisible = false
						}
						true
					}
					android.view.KeyEvent.KEYCODE_BACK -> {
						// Back key deletes digit or closes PIN entry
						val text = binding.pinInput.text
						if (text != null && text.isNotEmpty()) {
							text.delete(text.length - 1, text.length)
							binding.pinError.isVisible = false
						} else {
							hidePinEntry()
							pendingPinUser = null
							pendingPinServer = null
						}
						true
					}
					else -> false
				}
			} else {
				false
			}
		}

		// Apply key listener to all PIN entry elements
		binding.pinInput.setOnKeyListener(pinKeyListener)
		binding.pinButton0.setOnKeyListener(pinKeyListener)
		binding.pinButton1.setOnKeyListener(pinKeyListener)
		binding.pinButton2.setOnKeyListener(pinKeyListener)
		binding.pinButton3.setOnKeyListener(pinKeyListener)
		binding.pinButton4.setOnKeyListener(pinKeyListener)
		binding.pinButton5.setOnKeyListener(pinKeyListener)
		binding.pinButton6.setOnKeyListener(pinKeyListener)
		binding.pinButton7.setOnKeyListener(pinKeyListener)
		binding.pinButton8.setOnKeyListener(pinKeyListener)
		binding.pinButton9.setOnKeyListener(pinKeyListener)
		binding.pinButtonClear.setOnKeyListener(pinKeyListener)
		binding.pinButtonSubmit.setOnKeyListener(pinKeyListener)
		binding.pinEntryContainer.setOnKeyListener(pinKeyListener)

		// Setup numeric keypad buttons
		binding.pinButton0.setOnClickListener { appendPinDigit("0") }
		binding.pinButton1.setOnClickListener { appendPinDigit("1") }
		binding.pinButton2.setOnClickListener { appendPinDigit("2") }
		binding.pinButton3.setOnClickListener { appendPinDigit("3") }
		binding.pinButton4.setOnClickListener { appendPinDigit("4") }
		binding.pinButton5.setOnClickListener { appendPinDigit("5") }
		binding.pinButton6.setOnClickListener { appendPinDigit("6") }
		binding.pinButton7.setOnClickListener { appendPinDigit("7") }
		binding.pinButton8.setOnClickListener { appendPinDigit("8") }
		binding.pinButton9.setOnClickListener { appendPinDigit("9") }
		
		binding.pinButtonClear.setOnClickListener {
			val text = binding.pinInput.text
			if (text != null && text.isNotEmpty()) {
				text.delete(text.length - 1, text.length)
			}
			binding.pinError.isVisible = false
		}
		
		binding.pinButtonSubmit.setOnClickListener {
			verifyAndSubmitPin()
		}
	}

	private fun appendPinDigit(digit: String) {
		val currentText = binding.pinInput.text?.toString() ?: ""
		if (currentText.length < 10) {  // Match maxLength from XML
			binding.pinInput.append(digit)
			binding.pinError.isVisible = false
		}
	}

	private fun verifyAndSubmitPin() {
		val pin = binding.pinInput.text.toString()
		if (pin.isNotEmpty() && pendingPinUser != null) {
			val user = pendingPinUser!!
			val userPrefs = org.jellyfin.androidtv.preference.UserSettingPreferences(requireContext(), user.id)
			val storedHash = userPrefs[org.jellyfin.androidtv.preference.UserSettingPreferences.userPinHash]
			
			if (PinCodeUtil.hashPin(pin) == storedHash) {
				// Correct PIN
				hidePinEntry()
				authenticateUser(pendingPinServer!!, user)
				pendingPinUser = null
				pendingPinServer = null
			} else {
				// Incorrect PIN
				binding.pinError.isVisible = true
				binding.pinInput.text?.clear()
			}
		}
	}

	private fun showPinEntry(server: Server, user: User) {
		// Save currently focused view to restore later
		previousFocusedView = activity?.currentFocus
		
		pendingPinUser = user
		pendingPinServer = server
		binding.pinEntryContainer.isVisible = true
		binding.pinError.isVisible = false
		binding.pinInput.text?.clear()
		binding.pinInput.requestFocus()
	}

	private fun hidePinEntry() {
		binding.pinEntryContainer.isVisible = false
		binding.pinError.isVisible = false
		binding.pinInput.text?.clear()
		
		// Restore focus to the previously focused view (user card) or fallback to users grid
		previousFocusedView?.requestFocus() ?: binding.users.requestFocus()
		previousFocusedView = null
	}

	private fun authenticateUser(server: Server, user: User) {
		startupViewModel.authenticate(server, user).onEach { state ->
			when (state) {
				// Ignored states
				AuthenticatingState -> Unit
				AuthenticatedState -> Unit
				// Actions
				RequireSignInState -> navigateFragment<UserLoginFragment>(bundleOf(
					UserLoginFragment.ARG_SERVER_ID to server.id.toString(),
					UserLoginFragment.ARG_USERNAME to user.name,
				))
				// Errors
				ServerUnavailableState,
				is ApiClientErrorLoginState -> Toast.makeText(context, R.string.server_connection_failed, Toast.LENGTH_LONG).show()

				is ServerVersionNotSupported -> Toast.makeText(
					context,
					getString(
						R.string.server_issue_outdated_version,
						state.server.version,
						ServerRepository.recommendedServerVersion.toString()
					),
					Toast.LENGTH_LONG
				).show()
			}
		}.launchIn(lifecycleScope)
	}

	private fun onServerChange(server: Server) {
		binding.loginDisclaimer.text = server.loginDisclaimer?.let { markdownRenderer.toMarkdownSpanned(it) }

		binding.serverButton.apply {
			state = ServerButtonView.State.EDIT
			name = server.name
			address = server.address
			version = server.version
		}

		binding.addUserButton.setOnClickListener {
			navigateFragment<UserLoginFragment>(
				args = bundleOf(
					UserLoginFragment.ARG_SERVER_ID to server.id.toString(),
					UserLoginFragment.ARG_USERNAME to null
				)
			)
		}

		binding.serverButton.setOnClickListener {
			navigateFragment<SelectServerFragment>(keepToolbar = true)
		}

		if (!server.versionSupported) {
			binding.notification.isVisible = true
			binding.notification.text = getString(
				R.string.server_unsupported_notification,
				server.version,
				ServerRepository.recommendedServerVersion.toString(),
			)
		} else if (!server.setupCompleted) {
			binding.notification.isVisible = true
			binding.notification.text = getString(R.string.server_setup_incomplete)
		} else {
			binding.notification.isGone = true
		}
	}

	private inline fun <reified F : Fragment> navigateFragment(
		args: Bundle = bundleOf(),
		keepToolbar: Boolean = false,
		keepHistory: Boolean = true,
	) {
		requireActivity()
			.supportFragmentManager
			.commit {
				if (keepToolbar) {
					replace<StartupToolbarFragment>(R.id.content_view)
					add<F>(R.id.content_view, null, args)
				} else {
					replace<F>(R.id.content_view, null, args)
				}

				if (keepHistory) addToBackStack(null)
			}
	}

	override fun onResume() {
		super.onResume()

		startupViewModel.reloadStoredServers()
		backgroundService.clearBackgrounds()

		val server = serverIdArgument?.let(startupViewModel::getServer)
		if (server != null) startupViewModel.loadUsers(server)
		else navigateFragment<SelectServerFragment>(keepToolbar = true)
	}

	private class UserAdapter(
		private val context: Context,
		private val server: Server,
		private val startupViewModel: StartupViewModel,
		private val authenticationRepository: AuthenticationRepository,
		private val serverUserRepository: ServerUserRepository,
	) : ListAdapter<User, UserAdapter.ViewHolder>() {
		var onItemPressed: (User) -> Unit = {}

		override fun areItemsTheSame(old: User, new: User): Boolean = old.id == new.id

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
			val cardView = UserCardView(context)

			return ViewHolder(cardView)
		}

		override fun onBindViewHolder(holder: ViewHolder, user: User) {
			holder.cardView.name = user.name
			holder.cardView.image = startupViewModel.getUserImage(server, user)

			holder.cardView.setPopupMenu {
				// Logout button
				if (user is PrivateUser && user.accessToken != null) {
					item(context.getString(R.string.lbl_sign_out)) {
						authenticationRepository.logout(user)
					}
				}

				// Remove button
				if (user is PrivateUser) {
					item(context.getString(R.string.lbl_remove)) {
						serverUserRepository.deleteStoredUser(user)
						startupViewModel.loadUsers(server)
					}
				}
			}

			holder.cardView.setOnClickListener {
				onItemPressed(user)
			}
		}

		private class ViewHolder(
			val cardView: UserCardView,
		) : RecyclerView.ViewHolder(cardView)
	}
}
