package org.jellyfin.androidtv.ui.jellyseerr

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrQualityProfileDto
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrRootFolderDto
import org.jellyfin.androidtv.util.dp
import timber.log.Timber

/**
 * Data class containing the user's advanced request options selections
 */
data class AdvancedRequestOptions(
	val profileId: Int?,
	val rootFolderId: Int?,
	val serverId: Int?
)

/**
 * Dialog for advanced request options (profile and root folder selection)
 * Shown to users with REQUEST_ADVANCED permission
 * 
 * @param context The context
 * @param title The title of the content being requested
 * @param is4k Whether this is a 4K request
 * @param isMovie Whether this is a movie (true) or TV show (false)
 * @param coroutineScope Scope for launching coroutines to fetch data
 * @param onLoadData Callback to load server details, returns (profiles, rootFolders, defaultProfileId, defaultRootFolder)
 * @param onConfirm Callback with the selected options
 * @param onCancel Callback when dialog is cancelled
 */
class AdvancedRequestOptionsDialog(
	context: Context,
	private val title: String,
	private val is4k: Boolean,
	private val isMovie: Boolean,
	private val coroutineScope: CoroutineScope,
	private val onLoadData: suspend () -> ServerDetailsData?,
	private val onConfirm: (AdvancedRequestOptions) -> Unit,
	private val onCancel: () -> Unit
) : Dialog(context) {

	data class ServerDetailsData(
		val serverId: Int,
		val profiles: List<JellyseerrQualityProfileDto>,
		val rootFolders: List<JellyseerrRootFolderDto>,
		val defaultProfileId: Int,
		val defaultRootFolder: String
	)

	private data class OptionButtonData(val view: TextView, val id: Int?)

	private var selectedProfileId: Int? = null
	private var selectedRootFolderId: Int? = null
	private var serverId: Int? = null
	private var defaultProfileIdValue: Int? = null
	private var defaultRootFolderIdValue: Int? = null
	
	private lateinit var contentContainer: LinearLayout
	private lateinit var loadingIndicator: ProgressBar
	private lateinit var confirmButton: TextView
	private lateinit var cancelButton: TextView
	
	private var profileButtons = mutableListOf<OptionButtonData>()
	private var rootFolderButtons = mutableListOf<OptionButtonData>()
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		
		// Create root container
		val rootContainer = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setBackgroundColor(Color.parseColor("#1F2937")) // gray-800
			setPadding(24.dp(context), 24.dp(context), 24.dp(context), 24.dp(context))
			layoutParams = ViewGroup.LayoutParams(
				650.dp(context),
				ViewGroup.LayoutParams.WRAP_CONTENT
			)
		}
		
		// Title
		val titleText = TextView(context).apply {
			text = "Request Options"
			textSize = 20f
			setTextColor(Color.WHITE)
			setTypeface(typeface, android.graphics.Typeface.BOLD)
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				bottomMargin = 6.dp(context)
			}
		}
		rootContainer.addView(titleText)
		
		// Subtitle with content title and quality
		val subtitleText = TextView(context).apply {
			val mediaType = if (isMovie) "Movie" else "TV Show"
			val quality = if (is4k) "4K" else "HD"
			text = "$title ($quality $mediaType)"
			textSize = 14f
			setTextColor(Color.parseColor("#9CA3AF")) // gray-400
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				bottomMargin = 16.dp(context)
			}
		}
		rootContainer.addView(subtitleText)
		
		// Loading indicator
		loadingIndicator = ProgressBar(context).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				gravity = Gravity.CENTER
				topMargin = 24.dp(context)
				bottomMargin = 24.dp(context)
			}
		}
		rootContainer.addView(loadingIndicator)
		
		// Content container (hidden until data loads)
		contentContainer = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			visibility = View.GONE
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
		}
		rootContainer.addView(contentContainer)
		
		// Buttons container
		val buttonsContainer = LinearLayout(context).apply {
			orientation = LinearLayout.HORIZONTAL
			gravity = Gravity.CENTER
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = 16.dp(context)
			}
		}
		
		// Cancel button
		cancelButton = TextView(context).apply {
			text = "Cancel"
			textSize = 14f
			setTextColor(Color.WHITE)
			setBackgroundColor(Color.parseColor("#374151")) // gray-700
			setPadding(32.dp(context), 12.dp(context), 32.dp(context), 12.dp(context))
			isFocusable = true
			isFocusableInTouchMode = true
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				marginEnd = 16.dp(context)
			}
			setOnClickListener {
				onCancel()
				dismiss()
			}
			setOnFocusChangeListener { _, hasFocus ->
				setBackgroundColor(
					if (hasFocus) Color.parseColor("#4B5563") // gray-600
					else Color.parseColor("#374151") // gray-700
				)
			}
		}
		buttonsContainer.addView(cancelButton)
		
		// Confirm button
		confirmButton = TextView(context).apply {
			text = "Request"
			textSize = 14f
			setTextColor(Color.WHITE)
			setBackgroundColor(Color.parseColor("#7C3AED")) // purple-600
			setPadding(32.dp(context), 12.dp(context), 32.dp(context), 12.dp(context))
			isFocusable = true
			isFocusableInTouchMode = true
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
			setOnClickListener {
				submitRequest()
			}
			setOnFocusChangeListener { _, hasFocus ->
				setBackgroundColor(
					if (hasFocus) Color.parseColor("#6D28D9") // purple-700
					else Color.parseColor("#7C3AED") // purple-600
				)
			}
		}
		buttonsContainer.addView(confirmButton)
		
		rootContainer.addView(buttonsContainer)
		
		setContentView(rootContainer)
		
		// Configure dialog window
		window?.apply {
			setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
			setLayout(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
			)
			setGravity(Gravity.CENTER)
		}
		
		setCancelable(true)
		setOnCancelListener { onCancel() }
		
		// Load data
		loadServerDetails()
	}
	
	private fun loadServerDetails() {
		coroutineScope.launch {
			try {
				val data = onLoadData()
				withContext(Dispatchers.Main) {
					if (data != null) {
						serverId = data.serverId
						defaultProfileIdValue = data.defaultProfileId
						selectedProfileId = data.defaultProfileId
						defaultRootFolderIdValue = data.rootFolders.find { it.path == data.defaultRootFolder }?.id
						selectedRootFolderId = defaultRootFolderIdValue
						buildContent(data)
						loadingIndicator.visibility = View.GONE
						contentContainer.visibility = View.VISIBLE
					} else {
						showError("Failed to load server configuration")
					}
				}
			} catch (e: Exception) {
				Timber.e(e, "Failed to load server details")
				withContext(Dispatchers.Main) {
					showError("Error: ${e.message}")
				}
			}
		}
	}
	
	private fun showError(message: String) {
		loadingIndicator.visibility = View.GONE
		val errorText = TextView(context).apply {
			text = message
			textSize = 14f
			setTextColor(Color.parseColor("#EF4444")) // red-500
			gravity = Gravity.CENTER
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = 16.dp(context)
			}
		}
		contentContainer.addView(errorText)
		contentContainer.visibility = View.VISIBLE
	}
	
	private fun buildContent(data: ServerDetailsData) {
		contentContainer.removeAllViews()
		
		// Scrollable content
		val scrollView = ScrollView(context).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				320.dp(context) // Max height
			)
			isVerticalScrollBarEnabled = true
		}
		
		val scrollContent = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
		}
		
		// Quality Profile section
		val profileSection = createSectionHeader("Quality Profile")
		scrollContent.addView(profileSection)
		
		// Server Default option for profiles (id = null represents server default)
		val defaultProfileButton = createOptionButton(
			"Server Default",
			isSelected = true,
			isDefault = true
		) {
			selectedProfileId = defaultProfileIdValue
			updateProfileSelection(null) // null means server default
		}
		profileButtons.add(OptionButtonData(defaultProfileButton, null))
		scrollContent.addView(defaultProfileButton)
		
		// Profile options
		data.profiles.forEach { profile ->
			val profileButton = createOptionButton(
				profile.name,
				isSelected = false,
				isDefault = false
			) {
				selectedProfileId = profile.id
				updateProfileSelection(profile.id)
			}
			profileButtons.add(OptionButtonData(profileButton, profile.id))
			scrollContent.addView(profileButton)
		}
		
		// Separator
		scrollContent.addView(createSeparator())
		
		// Root Folder section
		val rootFolderSection = createSectionHeader("Root Folder")
		scrollContent.addView(rootFolderSection)
		
		// Find default root folder
		val defaultRootFolder = data.rootFolders.find { it.path == data.defaultRootFolder }
		
		// Server Default option for root folders (id = null represents server default)
		val defaultRootFolderButton = createOptionButton(
			"Server Default" + (defaultRootFolder?.let { " (${getDisplayPath(it.path)})" } ?: ""),
			isSelected = true,
			isDefault = true
		) {
			selectedRootFolderId = defaultRootFolderIdValue
			updateRootFolderSelection(null)
		}
		rootFolderButtons.add(OptionButtonData(defaultRootFolderButton, null))
		scrollContent.addView(defaultRootFolderButton)
		
		// Root folder options
		data.rootFolders.forEach { folder ->
			val isDefault = folder.path == data.defaultRootFolder
			if (!isDefault) { // Don't duplicate the default
				val folderButton = createOptionButton(
					getDisplayPath(folder.path),
					isSelected = false,
					isDefault = false
				) {
					selectedRootFolderId = folder.id
					updateRootFolderSelection(folder.id)
				}
				rootFolderButtons.add(OptionButtonData(folderButton, folder.id))
				scrollContent.addView(folderButton)
			}
		}
		
		scrollView.addView(scrollContent)
		contentContainer.addView(scrollView)
		
		// Focus on first focusable option
		profileButtons.firstOrNull()?.view?.requestFocus()
	}
	
	private fun createSectionHeader(text: String): TextView {
		return TextView(context).apply {
			this.text = text
			textSize = 16f
			setTextColor(Color.WHITE)
			setTypeface(typeface, android.graphics.Typeface.BOLD)
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				bottomMargin = 8.dp(context)
				topMargin = 8.dp(context)
			}
		}
	}
	
	private fun createSeparator(): View {
		return View(context).apply {
			setBackgroundColor(Color.parseColor("#374151")) // gray-700
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				1.dp(context)
			).apply {
				topMargin = 12.dp(context)
				bottomMargin = 4.dp(context)
			}
		}
	}
	
	private fun createOptionButton(
		text: String,
		isSelected: Boolean,
		isDefault: Boolean,
		onClick: () -> Unit
	): TextView {
		return TextView(context).apply {
			val displayText = if (isSelected) "● $text" else "○ $text"
			this.text = displayText
			textSize = 14f
			setTextColor(if (isSelected) Color.parseColor("#A78BFA") else Color.WHITE) // purple-400 or white
			setBackgroundColor(Color.TRANSPARENT)
			setPadding(16.dp(context), 10.dp(context), 16.dp(context), 10.dp(context))
			isFocusable = true
			isFocusableInTouchMode = true
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
			tag = text // Store original text for updates
			setOnClickListener { onClick() }
			setOnFocusChangeListener { _, hasFocus ->
				setBackgroundColor(
					if (hasFocus) Color.parseColor("#374151") // gray-700
					else Color.TRANSPARENT
				)
			}
		}
	}
	
	private fun updateButtonVisualState(button: TextView, isSelected: Boolean) {
		val originalText = button.tag as String
		val displayText = if (isSelected) "● $originalText" else "○ $originalText"
		button.text = displayText
		button.setTextColor(
			if (isSelected) Color.parseColor("#A78BFA") // purple-400
			else Color.WHITE
		)
	}
	
	private fun updateProfileSelection(selectedId: Int?) {
		profileButtons.forEach { buttonData ->
			val isSelected = buttonData.id == selectedId
			updateButtonVisualState(buttonData.view, isSelected)
		}
	}
	
	private fun updateRootFolderSelection(selectedId: Int?) {
		rootFolderButtons.forEach { buttonData ->
			val isSelected = buttonData.id == selectedId
			updateButtonVisualState(buttonData.view, isSelected)
		}
	}
	
	private fun getDisplayPath(path: String): String {
		// Show the full path for better clarity when multiple drives have same endpoint folder names
		return path.trimEnd('/')
	}
	
	private fun submitRequest() {
		onConfirm(AdvancedRequestOptions(
			profileId = selectedProfileId,
			rootFolderId = selectedRootFolderId,
			serverId = serverId
		))
		dismiss()
	}
	
	override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			onCancel()
			dismiss()
			return true
		}
		return super.onKeyDown(keyCode, event)
	}
}
