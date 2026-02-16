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
import org.jellyfin.androidtv.preference.UserPreferences

class SeasonSelectionDialog(
	context: Context,
	private val showName: String,
	private val numberOfSeasons: Int,
	private val is4k: Boolean,
	private val unavailableSeasons: Set<Int> = emptySet(),
	private val onConfirm: (selectedSeasons: List<Int>) -> Unit
) : Dialog(context) {

	private val seasonCheckboxes = mutableListOf<CheckBox>()
	private var selectAllCheckbox: CheckBox? = null
	private lateinit var confirmButton: TextView
	private lateinit var cancelButton: TextView
	
	private fun getFocusColor(): Int {
		val userPreferences: UserPreferences by org.koin.java.KoinJavaComponent.inject(UserPreferences::class.java)
		val appTheme = userPreferences[UserPreferences.appTheme]
		return appTheme.colorValue.toInt()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		
		val focusBorderColor = getFocusColor()

		val rootContainer = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setBackgroundColor(Color.parseColor("#1F2937"))
			setPadding(24.dp(context), 24.dp(context), 32.dp(context), 24.dp(context))
			layoutParams = ViewGroup.LayoutParams(
				600.dp(context),
				ViewGroup.LayoutParams.WRAP_CONTENT
			)
		}
		
		val titleText = TextView(context).apply {
			text = "Select Seasons"
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
		
		val subtitleText = TextView(context).apply {
			text = "$showName ${if (is4k) "(4K)" else "(HD)"}"
			textSize = 14f
			setTextColor(Color.parseColor("#9CA3AF"))
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				bottomMargin = 16.dp(context)
			}
		}
		rootContainer.addView(subtitleText)
		
		val availableSeasons = (1..numberOfSeasons).filter { it !in unavailableSeasons }
		
		if (availableSeasons.isNotEmpty()) {
			selectAllCheckbox = CheckBox(context).apply {
				text = if (unavailableSeasons.isEmpty()) "Select All Seasons" else "Select All Available"
				textSize = 15f
				setTextColor(Color.WHITE)
				isChecked = true
				isFocusable = true
				isFocusableInTouchMode = true
				scaleX = 1.2f
				scaleY = 1.2f
				layoutParams = LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT
				).apply {
					bottomMargin = 12.dp(context)
					leftMargin = 40.dp(context)
				}
				setOnCheckedChangeListener { _, isChecked ->
					seasonCheckboxes.forEachIndexed { index, checkbox ->
						if ((index + 1) !in unavailableSeasons) {
							checkbox.isChecked = isChecked
						}
					}
				}
			}
			rootContainer.addView(selectAllCheckbox)
		}
		
		val separator = View(context).apply {
			setBackgroundColor(Color.parseColor("#374151"))
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				1.dp(context)
			).apply {
				bottomMargin = 12.dp(context)
			}
		}
		rootContainer.addView(separator)
		
		val scrollView = ScrollView(context).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				280.dp(context)
			)
			isVerticalScrollBarEnabled = true
			scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
		}
		
		val seasonsContainer = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
		}
		
		for (season in 1..numberOfSeasons) {
			val isUnavailable = season in unavailableSeasons
			val checkbox = CheckBox(context).apply {
				text = if (isUnavailable) "Season $season (Already Requested)" else "Season $season"
				textSize = 14f
				setTextColor(if (isUnavailable) Color.parseColor("#6B7280") else Color.WHITE)
				isChecked = !isUnavailable
				isEnabled = !isUnavailable
				isFocusable = !isUnavailable
				isFocusableInTouchMode = !isUnavailable
				scaleX = 1.1f
				scaleY = 1.1f
				alpha = if (isUnavailable) 0.5f else 1.0f
				layoutParams = LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT
				).apply {
					bottomMargin = 4.dp(context)
					leftMargin = 40.dp(context)
				}
				if (!isUnavailable) {
					setOnCheckedChangeListener { _, _ ->
						updateSelectAllCheckbox()
					}
				}
			}
			seasonCheckboxes.add(checkbox)
			seasonsContainer.addView(checkbox)
		}
		
		scrollView.addView(seasonsContainer)
		rootContainer.addView(scrollView)
		
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
		
		cancelButton = TextView(context).apply {
			text = "Cancel"
			textSize = 14f
			setTextColor(Color.WHITE)
			setTypeface(typeface, android.graphics.Typeface.BOLD)
			setPadding(24.dp(context), 12.dp(context), 24.dp(context), 12.dp(context))
			isFocusable = true
			isFocusableInTouchMode = true
			gravity = Gravity.CENTER
			
			val normalBg = android.graphics.drawable.GradientDrawable().apply {
				setColor(Color.parseColor("#6B7280"))
				cornerRadius = 6.dp(context).toFloat()
			}
			val focusedBg = android.graphics.drawable.GradientDrawable().apply {
				setColor(Color.parseColor("#9CA3AF"))
				cornerRadius = 6.dp(context).toFloat()
				setStroke(2.dp(context), focusBorderColor)
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
		
		confirmButton = TextView(context).apply {
			text = "Request Selected"
			textSize = 14f
			setTextColor(Color.WHITE)
			setTypeface(typeface, android.graphics.Typeface.BOLD)
			setPadding(24.dp(context), 12.dp(context), 24.dp(context), 12.dp(context))
			isFocusable = true
			isFocusableInTouchMode = true
			gravity = Gravity.CENTER
			
			val normalBg = android.graphics.drawable.GradientDrawable().apply {
				setColor(Color.parseColor("#3B82F6"))
				cornerRadius = 6.dp(context).toFloat()
			}
			val focusedBg = android.graphics.drawable.GradientDrawable().apply {
				setColor(Color.parseColor("#60A5FA"))
				cornerRadius = 6.dp(context).toFloat()
				setStroke(2.dp(context), focusBorderColor)
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
		
		window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
		window?.setLayout(
			ViewGroup.LayoutParams.WRAP_CONTENT,
			ViewGroup.LayoutParams.WRAP_CONTENT
		)
		window?.setGravity(Gravity.CENTER)
		
		confirmButton.post {
			confirmButton.requestFocus()
		}
	}
	
	private fun updateSelectAllCheckbox() {
		// Only consider available (enabled) season checkboxes
		val availableCheckboxes = seasonCheckboxes.filter { it.isEnabled }
		if (availableCheckboxes.isEmpty()) return
		
		val allAvailableChecked = availableCheckboxes.all { it.isChecked }
		selectAllCheckbox?.setOnCheckedChangeListener(null)
		selectAllCheckbox?.isChecked = allAvailableChecked
		selectAllCheckbox?.setOnCheckedChangeListener { _, isChecked ->
			seasonCheckboxes.forEachIndexed { index, checkbox ->
				if ((index + 1) !in unavailableSeasons) {
					checkbox.isChecked = isChecked
				}
			}
		}
	}
	
	override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
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
