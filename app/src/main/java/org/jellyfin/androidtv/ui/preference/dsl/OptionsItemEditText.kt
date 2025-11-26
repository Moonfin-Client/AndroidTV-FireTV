package org.jellyfin.androidtv.ui.preference.dsl

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import java.util.UUID

class OptionsItemEditText(
	private val context: Context
) : OptionsItemMutable<String>() {
	@DrawableRes
	var icon: Int? = null
	var content: String? = null
	var inputType: Int? = null

	fun setTitle(@StringRes resId: Int) {
		title = context.getString(resId)
	}

	fun setContent(@StringRes resId: Int) {
		content = context.getString(resId)
	}

	fun setInputType(type: Int) {
		inputType = type
	}

	override fun build(category: PreferenceCategory, container: OptionsUpdateFunContainer) {
		val pref = EditTextPreference(context).also {
			it.isPersistent = false
			it.key = UUID.randomUUID().toString()
			category.addPreference(it)
			it.isEnabled = dependencyCheckFun() && enabled
			it.isVisible = visible
			icon?.let { icon -> it.setIcon(icon) }
			it.title = title
			it.summary = content
			it.text = binder.get()
			inputType?.let { type ->
				it.setOnBindEditTextListener { editText ->
					editText.inputType = type
				}
			}
			it.setOnPreferenceChangeListener { _, newValue ->
				binder.set(newValue as String)
				it.text = newValue
				container()
				true
			}
		}

		container += {
			pref.isEnabled = dependencyCheckFun() && enabled
			pref.text = binder.get()
		}
	}
}

@OptionsDSL
fun OptionsCategory.editText(init: OptionsItemEditText.() -> Unit) {
	this += OptionsItemEditText(context).apply { init() }
}
