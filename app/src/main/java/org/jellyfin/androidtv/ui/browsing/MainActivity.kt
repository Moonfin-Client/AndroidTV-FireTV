package org.jellyfin.androidtv.ui.browsing

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.SessionRepositoryState
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.service.UpdateCheckerService
import org.jellyfin.androidtv.databinding.ActivityMainBinding
import org.jellyfin.androidtv.integration.LeanbackChannelWorker
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.InteractionTrackerViewModel
import org.jellyfin.androidtv.ui.background.AppBackground
import org.jellyfin.androidtv.ui.navigation.NavigationAction
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.screensaver.InAppScreensaver
import org.jellyfin.androidtv.ui.settings.compat.MainActivitySettings
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.util.applyTheme
import org.jellyfin.androidtv.util.isMediaSessionKeyEvent
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class MainActivity : FragmentActivity() {
	private val navigationRepository by inject<NavigationRepository>()
	private val sessionRepository by inject<SessionRepository>()
	private val userRepository by inject<UserRepository>()
	private val interactionTrackerViewModel by viewModel<InteractionTrackerViewModel>()
	private val workManager by inject<WorkManager>()
	private val updateCheckerService by inject<UpdateCheckerService>()
	private val userPreferences by inject<UserPreferences>()

	private lateinit var binding: ActivityMainBinding
	private var exitConfirmationDialog: AlertDialog? = null

	private val backPressedCallback = object : OnBackPressedCallback(false) {
		override fun handleOnBackPressed() {
			if (navigationRepository.canGoBack) {
				navigationRepository.goBack()
			} else {
				// User is on home screen, show exit confirmation
				showExitConfirmation()
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		applyTheme()

		super.onCreate(savedInstanceState)

		// Wait for session restoration before validating authentication
		// This prevents race condition where activity recreates before session is restored
		lifecycleScope.launch {
			sessionRepository.state
				.filter { it == SessionRepositoryState.READY }
				.first()
			
			if (!validateAuthentication()) return@launch
			
			setupActivity(savedInstanceState)
		}
	}

	private fun setupActivity(savedInstanceState: Bundle?) {
		interactionTrackerViewModel.keepScreenOn.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
			.onEach { keepScreenOn ->
				if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
				else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
			}.launchIn(lifecycleScope)

		onBackPressedDispatcher.addCallback(this, backPressedCallback)
		if (savedInstanceState == null && navigationRepository.canGoBack) navigationRepository.reset(clearHistory = true)

		navigationRepository.currentAction
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach { action ->
				handleNavigationAction(action)
				// Always enable back callback to handle exit confirmation
				backPressedCallback.isEnabled = true
				interactionTrackerViewModel.notifyInteraction(canCancel = false, userInitiated = false)
			}.launchIn(lifecycleScope)

		binding = ActivityMainBinding.inflate(layoutInflater)
		binding.background.setContent { AppBackground() }
		binding.settings.setContent { MainActivitySettings() }
		binding.screensaver.setContent { InAppScreensaver() }
		setContentView(binding.root)

		// Check for updates on app launch
		checkForUpdatesOnLaunch()
	}

	override fun onResume() {
		super.onResume()

		if (!validateAuthentication()) return

		applyTheme()

		interactionTrackerViewModel.activityPaused = false
	}

	private fun validateAuthentication(): Boolean {
		if (sessionRepository.currentSession.value == null || userRepository.currentUser.value == null) {
			Timber.w("Activity ${this::class.qualifiedName} started without a session, bouncing to StartupActivity")
			startActivity(Intent(this, StartupActivity::class.java))
			finish()
			return false
		}

		return true
	}

	private fun checkForUpdatesOnLaunch() {
		lifecycleScope.launch(Dispatchers.IO) {
			try {
				val result = updateCheckerService.checkForUpdate()
				result.onSuccess { updateInfo ->
					if (updateInfo != null && updateInfo.isNewer) {
						// Show toast on main thread
						launch(Dispatchers.Main) {
							Toast.makeText(
								this@MainActivity,
								"Update available: ${updateInfo.version}",
								Toast.LENGTH_LONG
							).show()
						}
						Timber.i("Update available: ${updateInfo.version}")
					} else {
						Timber.d("No updates available")
					}
				}.onFailure { error ->
					Timber.e(error, "Failed to check for updates")
				}
			} catch (e: Exception) {
				Timber.e(e, "Error checking for updates on launch")
			}
		}
	}

	override fun onPause() {
		super.onPause()

		interactionTrackerViewModel.activityPaused = true
	}

	override fun onStop() {
		super.onStop()

		workManager.enqueue(OneTimeWorkRequestBuilder<LeanbackChannelWorker>().build())

		// Only destroy session if app is finishing, not just temporarily stopping
		if (isFinishing) {
			lifecycleScope.launch(Dispatchers.IO) {
				Timber.i("MainActivity finishing - destroying session")
				sessionRepository.restoreSession(destroyOnly = true)
			}
		} else {
			Timber.d("MainActivity stopped (not finishing) - preserving session")
		}
	}

	private fun handleNavigationAction(action: NavigationAction) {

		when (action) {
			is NavigationAction.NavigateFragment -> binding.contentView.navigate(action)
			NavigationAction.GoBack -> binding.contentView.goBack()

			NavigationAction.Nothing -> Unit
		}
	}

	// Forward key events to fragments
	private fun Fragment.onKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
		var result = childFragmentManager.fragments.any { it.onKeyEvent(keyCode, event) }
		if (!result && this is View.OnKeyListener) result = onKey(currentFocus, keyCode, event)
		return result
	}

	private fun onKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
		// Ignore the key event that closes the screensaver
		if (interactionTrackerViewModel.visible.value) {
			interactionTrackerViewModel.notifyInteraction(canCancel = event?.action == KeyEvent.ACTION_UP, userInitiated = true)
			return true
		}

		return supportFragmentManager.fragments
			.any { it.onKeyEvent(keyCode, event) }
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyDown(keyCode, event)

	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyUp(keyCode, event)

	override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyUp(keyCode, event)

	override fun onUserInteraction() {
		super.onUserInteraction()

		interactionTrackerViewModel.notifyInteraction(false, userInitiated = true)
	}

	@Suppress("RestrictedApi") // False positive
	override fun dispatchKeyEvent(event: KeyEvent): Boolean {
		// Ignore the key event that closes the screensaver
		if (!event.isMediaSessionKeyEvent() && interactionTrackerViewModel.visible.value) {
			interactionTrackerViewModel.notifyInteraction(canCancel = event.action == KeyEvent.ACTION_UP, userInitiated = true)
			return true
		}

		@Suppress("RestrictedApi") // False positive
		return super.dispatchKeyEvent(event)
	}

	@Suppress("RestrictedApi") // False positive
	override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean {
		// Ignore the key event that closes the screensaver
		if (!event.isMediaSessionKeyEvent() && interactionTrackerViewModel.visible.value) {
			interactionTrackerViewModel.notifyInteraction(canCancel = event.action == KeyEvent.ACTION_UP, userInitiated = true)
			return true
		}

		@Suppress("RestrictedApi") // False positive
		return super.dispatchKeyShortcutEvent(event)
	}

	override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
		// Ignore the touch event that closes the screensaver
		if (interactionTrackerViewModel.visible.value) {
			interactionTrackerViewModel.notifyInteraction(canCancel = true, userInitiated = true)
			return true
		}

		return super.dispatchTouchEvent(ev)
	}

	private fun showExitConfirmation() {
		// Check if confirmation is enabled in preferences
		if (!userPreferences[UserPreferences.confirmExit]) {
			// Exit immediately without confirmation
			finish()
			return
		}

		// Don't show dialog if one is already showing
		if (exitConfirmationDialog?.isShowing == true) return

		exitConfirmationDialog = AlertDialog.Builder(this)
			.setTitle(R.string.exit_confirmation_title)
			.setMessage(R.string.exit_confirmation_message)
			.setPositiveButton(R.string.lbl_exit) { _, _ ->
				finish()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.setOnCancelListener { exitConfirmationDialog = null }
			.create()

		exitConfirmationDialog?.show()
	}

	override fun onDestroy() {
		exitConfirmationDialog?.dismiss()
		exitConfirmationDialog = null
		super.onDestroy()
	}
}
