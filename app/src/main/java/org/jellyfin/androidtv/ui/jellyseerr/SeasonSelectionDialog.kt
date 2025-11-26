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
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Dialog for selecting seasons to request for TV shows
 */
class SeasonSelectionDialog(
	context: Context,
	private val showName: String,
	private val numberOfSeasons: Int,
	private val is4k: Boolean,
	private val onConfirm: (selectedSeasons: List<Int>) -> Unit
) : Dialog(context) {

	private val seasonCheckboxes = mutableListOf<CheckBox>()
	private var selectAllCheckbox: CheckBox? = null
	private lateinit var confirmButton: TextView
	private lateinit var cancelButton: TextView
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		
		// Create root container - optimized for TV screens
		val rootContainer = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setBackgroundColor(Color.parseColor("#1F2937")) // gray-800
			setPadding(24.dp(context), 24.dp(context), 32.dp(context), 24.dp(context)) // Reduced left padding from 48dp to 24dp
			layoutParams = ViewGroup.LayoutParams(
				600.dp(context), // Reduced from 800dp
				ViewGroup.LayoutParams.WRAP_CONTENT
			)
		}
		
		// Title
		val titleText = TextView(context).apply {
			text = "Select Seasons"
			textSize = 20f // Reduced from 24f
			setTextColor(Color.WHITE)
			setTypeface(typeface, android.graphics.Typeface.BOLD)
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				bottomMargin = 6.dp(context) // Reduced from 8dp
			}
		}
		rootContainer.addView(titleText)
		
		// Show name and quality
		val subtitleText = TextView(context).apply {
			text = "$showName ${if (is4k) "(4K)" else "(HD)"}"
			textSize = 14f // Reduced from 16f
			setTextColor(Color.parseColor("#9CA3AF")) // gray-400
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				bottomMargin = 16.dp(context) // Reduced from 24dp
			}
		}
		rootContainer.addView(subtitleText)
		
		// Select All checkbox
		selectAllCheckbox = CheckBox(context).apply {
			text = "Select All Seasons"
			textSize = 15f // Reduced from 18f
			setTextColor(Color.WHITE)
			isChecked = true
			isFocusable = true
			isFocusableInTouchMode = true
			scaleX = 1.2f // Reduced from 1.5f
			scaleY = 1.2f
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				bottomMargin = 12.dp(context) // Reduced from 16dp
				leftMargin = 40.dp(context) // Move to match season checkboxes
			}
			setOnCheckedChangeListener { _, isChecked ->
				seasonCheckboxes.forEach { it.isChecked = isChecked }
			}
		}
		rootContainer.addView(selectAllCheckbox)
		
		// Separator
		val separator = View(context).apply {
			setBackgroundColor(Color.parseColor("#374151")) // gray-700
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				1.dp(context) // Reduced from 2dp
			).apply {
				bottomMargin = 12.dp(context) // Reduced from 16dp
			}
		}
		rootContainer.addView(separator)
		
		// Scrollable season list - limit height for better visibility
		val scrollView = ScrollView(context).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				280.dp(context) // Reduced from 400dp for better fit
			)
			isVerticalScrollBarEnabled = true // Enable scroll indicator
			scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
		}
		
		val seasonsContainer = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
		}
		
		// Add checkbox for each season - more compact
		for (season in 1..numberOfSeasons) {
			val checkbox = CheckBox(context).apply {
				text = "Season $season"
				textSize = 14f // Reduced from 16f
				setTextColor(Color.WHITE)
				isChecked = true
				isFocusable = true
				isFocusableInTouchMode = true
				scaleX = 1.1f // Reduced from 1.3f
				scaleY = 1.1f
				layoutParams = LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT
				).apply {
					bottomMargin = 4.dp(context) // Reduced from 8dp
					leftMargin = 40.dp(context) // Move checkboxes to the right
				}
				setOnCheckedChangeListener { _, _ ->
					updateSelectAllCheckbox()
				}
			}
			seasonCheckboxes.add(checkbox)
			seasonsContainer.addView(checkbox)
		}
		
		scrollView.addView(seasonsContainer)
		rootContainer.addView(scrollView)
		
		// Buttons container - centered with spacing
		val buttonsContainer = LinearLayout(context).apply {
			orientation = LinearLayout.HORIZONTAL
			gravity = Gravity.CENTER // Center the buttons
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = 16.dp(context) // Reduced from 24dp
			}
		}
		
		// Cancel button
		cancelButton = TextView(context).apply {
			text = "Cancel"
			textSize = 14f
			setTextColor(Color.WHITE)
			setTypeface(typeface, android.graphics.Typeface.BOLD)
			setPadding(24.dp(context), 12.dp(context), 24.dp(context), 12.dp(context))
			isFocusable = true
			isFocusableInTouchMode = true
			gravity = Gravity.CENTER
			
			// Create state list drawable for focus
			val normalBg = android.graphics.drawable.GradientDrawable().apply {
				setColor(Color.parseColor("#6B7280")) // gray-500
				cornerRadius = 6.dp(context).toFloat()
			}
			val focusedBg = android.graphics.drawable.GradientDrawable().apply {
				setColor(Color.parseColor("#9CA3AF")) // gray-400 (lighter when focused)
				cornerRadius = 6.dp(context).toFloat()
				setStroke(2.dp(context), Color.WHITE)
			}
			
			background = android.graphics.drawable.StateListDrawable().apply {
				addState(intArrayOf(android.R.attr.state_focused), focusedBg)
				addState(intArrayOf(), normalBg)
			}
			
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				marginEnd = 24.dp(context)
			}
			setOnClickListener {
				dismiss()
			}
		}
		buttonsContainer.addView(cancelButton)
		
		// Confirm button
		confirmButton = TextView(context).apply {
			text = "Request Selected"
			textSize = 14f
			setTextColor(Color.WHITE)
			setTypeface(typeface, android.graphics.Typeface.BOLD)
			setPadding(24.dp(context), 12.dp(context), 24.dp(context), 12.dp(context))
			isFocusable = true
			isFocusableInTouchMode = true
			gravity = Gravity.CENTER
			
			// Create state list drawable for focus
			val normalBg = android.graphics.drawable.GradientDrawable().apply {
				setColor(Color.parseColor("#3B82F6")) // blue-500
				cornerRadius = 6.dp(context).toFloat()
			}
			val focusedBg = android.graphics.drawable.GradientDrawable().apply {
				setColor(Color.parseColor("#60A5FA")) // blue-400 (lighter when focused)
				cornerRadius = 6.dp(context).toFloat()
				setStroke(2.dp(context), Color.WHITE)
			}
			
			background = android.graphics.drawable.StateListDrawable().apply {
				addState(intArrayOf(android.R.attr.state_focused), focusedBg)
				addState(intArrayOf(), normalBg)
			}
			
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
			setOnClickListener {
				val selectedSeasons = seasonCheckboxes
					.mapIndexedNotNull { index, checkbox ->
						if (checkbox.isChecked) index + 1 else null
					}
				if (selectedSeasons.isNotEmpty()) {
					onConfirm(selectedSeasons)
					dismiss()
				}
			}
		}
		buttonsContainer.addView(confirmButton)
		
		rootContainer.addView(buttonsContainer)
		
		setContentView(rootContainer)
		
		// Make dialog background transparent
		window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
		
		// Center the dialog on screen
		window?.setLayout(
			ViewGroup.LayoutParams.WRAP_CONTENT,
			ViewGroup.LayoutParams.WRAP_CONTENT
		)
		window?.setGravity(Gravity.CENTER)
		
		// Request focus on confirm button by default
		confirmButton.post {
			confirmButton.requestFocus()
		}
	}
	
	private fun updateSelectAllCheckbox() {
		val allChecked = seasonCheckboxes.all { it.isChecked }
		selectAllCheckbox?.setOnCheckedChangeListener(null)
		selectAllCheckbox?.isChecked = allChecked
		selectAllCheckbox?.setOnCheckedChangeListener { _, isChecked ->
			seasonCheckboxes.forEach { it.isChecked = isChecked }
		}
	}
	
	override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
		// Handle back button
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			dismiss()
			return true
		}
		return super.onKeyDown(keyCode, event)
	}
}

// Extension function for dp to px conversion
private fun Int.dp(context: Context): Int {
	return (this * context.resources.displayMetrics.density).toInt()
}
