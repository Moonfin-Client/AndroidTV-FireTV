package org.jellyfin.androidtv.ui.preference.custom

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.getSystemService
import org.jellyfin.androidtv.R

object PinCodeDialog {
	enum class Mode {
		SET,      // Setting a new PIN
		VERIFY    // Verifying existing PIN
	}

	fun show(
		context: Context,
		mode: Mode,
		onComplete: (String?) -> Unit
	) {
		val container = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(50, 40, 50, 10)
			layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
			)
		}

		val title = TextView(context).apply {
			text = when (mode) {
				Mode.SET -> context.getString(R.string.lbl_enter_new_pin)
				Mode.VERIFY -> context.getString(R.string.lbl_enter_pin)
			}
			textSize = 18f
			gravity = Gravity.CENTER
			setPadding(0, 0, 0, 20)
		}

		val pinInput = EditText(context).apply {
			inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
			hint = context.getString(R.string.lbl_pin_code_hint)
			gravity = Gravity.CENTER
			textSize = 20f
			setPadding(20, 20, 20, 20)
		}

		container.addView(title)
		container.addView(pinInput)

		if (mode == Mode.SET) {
			val confirmInput = EditText(context).apply {
				inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
				hint = context.getString(R.string.lbl_confirm_pin)
				gravity = Gravity.CENTER
				textSize = 20f
				setPadding(20, 20, 20, 20)
			}
			container.addView(confirmInput)
			
			val dialog = AlertDialog.Builder(context)
				.setView(container)
				.setPositiveButton(android.R.string.ok) { _, _ ->
					val pin = pinInput.text.toString()
					val confirm = confirmInput.text.toString()
					
					when {
						pin.isEmpty() -> {
							android.widget.Toast.makeText(
								context,
								R.string.lbl_pin_code_empty,
								android.widget.Toast.LENGTH_SHORT
							).show()
							onComplete(null)
						}
						pin.length < 4 -> {
							android.widget.Toast.makeText(
								context,
								R.string.lbl_pin_code_too_short,
								android.widget.Toast.LENGTH_SHORT
							).show()
							onComplete(null)
						}
						pin != confirm -> {
							android.widget.Toast.makeText(
								context,
								R.string.lbl_pin_code_mismatch,
								android.widget.Toast.LENGTH_SHORT
							).show()
							onComplete(null)
						}
						else -> onComplete(pin)
					}
				}
				.setNegativeButton(android.R.string.cancel) { _, _ -> onComplete(null) }
				.create()
			
			// First PIN field: move to confirmation, keep keyboard open
			pinInput.setOnEditorActionListener { _, actionId, _ ->
				if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
					confirmInput.requestFocus()
					true
				} else false
			}
			
			// Confirmation field: hide keyboard and focus OK button
			confirmInput.setOnEditorActionListener { _, actionId, _ ->
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					confirmInput.clearFocus()
					val imm = context.getSystemService<InputMethodManager>()
					imm?.hideSoftInputFromWindow(confirmInput.windowToken, 0)
					// Focus OK button after keyboard dismissed
					confirmInput.postDelayed({
						dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
					}, 150)
					true
				} else false
			}
			
			dialog.show()
			pinInput.requestFocus()
		} else {
			val dialog = AlertDialog.Builder(context)
				.setView(container)
				.setPositiveButton(android.R.string.ok) { _, _ ->
					val pin = pinInput.text.toString()
					if (pin.isEmpty()) {
						android.widget.Toast.makeText(
							context,
							R.string.lbl_pin_code_empty,
							android.widget.Toast.LENGTH_SHORT
						).show()
						onComplete(null)
					} else {
						onComplete(pin)
					}
				}
				.setNegativeButton(android.R.string.cancel) { _, _ -> onComplete(null) }
				.create()
			
			// Hide keyboard and focus OK button when done pressed
			pinInput.setOnEditorActionListener { _, actionId, _ ->
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					pinInput.clearFocus()
					val imm = context.getSystemService<InputMethodManager>()
					imm?.hideSoftInputFromWindow(pinInput.windowToken, 0)
					pinInput.postDelayed({
						dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
					}, 150)
					true
				} else false
			}
			
			dialog.show()
			pinInput.requestFocus()
		}
	}
}
