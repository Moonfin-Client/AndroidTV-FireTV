package org.jellyfin.androidtv.ui.preference.screen

import android.widget.Toast
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.action
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.androidtv.ui.preference.custom.PinCodeDialog
import org.jellyfin.androidtv.util.PinCodeUtil
import org.koin.android.ext.android.inject

class PinCodePreferencesScreen : OptionsFragment() {
	private val userRepository: UserRepository by inject()

	// Get user-specific preferences for the current user
	private val userSettingPreferences: UserSettingPreferences by lazy {
		val userId = userRepository.currentUser.value?.id
		UserSettingPreferences(requireContext(), userId)
	}

	override val screen by optionsScreen {
		setTitle(R.string.lbl_pin_code)

		category {
			checkbox {
				setTitle(R.string.lbl_pin_code_enabled)
				setContent(R.string.lbl_pin_code_enabled_description)
				bind(userSettingPreferences, UserSettingPreferences.userPinEnabled)

				depends {
					// Can only enable if PIN is set
					userSettingPreferences[UserSettingPreferences.userPinHash].isNotEmpty()
				}
			}

			action {
				setTitle(R.string.lbl_set_pin_code)
				setContent(R.string.lbl_set_pin_code_description)
				
				onActivate = {
					showSetPinDialog()
				}
			}

			action {
				setTitle(R.string.lbl_change_pin_code)
				setContent(R.string.lbl_change_pin_code_description)

				depends {
					userSettingPreferences[UserSettingPreferences.userPinHash].isNotEmpty()
				}

				onActivate = {
					showChangePinDialog()
				}
			}

			action {
				setTitle(R.string.lbl_remove_pin_code)
				setContent(R.string.lbl_remove_pin_code_description)

				depends {
					userSettingPreferences[UserSettingPreferences.userPinHash].isNotEmpty()
				}

				onActivate = {
					showRemovePinDialog()
				}
			}
		}
	}

	private fun showSetPinDialog() {
		PinCodeDialog.show(requireContext(), PinCodeDialog.Mode.SET) { pin ->
			if (pin != null) {
				val hash = PinCodeUtil.hashPin(pin)
				userSettingPreferences[UserSettingPreferences.userPinHash] = hash
				userSettingPreferences[UserSettingPreferences.userPinEnabled] = true
				Toast.makeText(requireContext(), R.string.lbl_pin_code_set, Toast.LENGTH_SHORT).show()
				rebuild()
			}
		}
	}

	private fun showChangePinDialog() {
		PinCodeDialog.show(requireContext(), PinCodeDialog.Mode.VERIFY) { oldPin ->
			if (oldPin != null) {
				val currentHash = userSettingPreferences[UserSettingPreferences.userPinHash]
				if (PinCodeUtil.hashPin(oldPin) == currentHash) {
					PinCodeDialog.show(requireContext(), PinCodeDialog.Mode.SET) { newPin ->
						if (newPin != null) {
							val hash = PinCodeUtil.hashPin(newPin)
							userSettingPreferences[UserSettingPreferences.userPinHash] = hash
							Toast.makeText(requireContext(), R.string.lbl_pin_code_changed, Toast.LENGTH_SHORT).show()
							rebuild()
						}
					}
				} else {
					Toast.makeText(requireContext(), R.string.lbl_pin_code_incorrect, Toast.LENGTH_SHORT).show()
				}
			}
		}
	}

	private fun showRemovePinDialog() {
		PinCodeDialog.show(requireContext(), PinCodeDialog.Mode.VERIFY) { pin ->
			if (pin != null) {
				val currentHash = userSettingPreferences[UserSettingPreferences.userPinHash]
				if (PinCodeUtil.hashPin(pin) == currentHash) {
					userSettingPreferences[UserSettingPreferences.userPinHash] = ""
					userSettingPreferences[UserSettingPreferences.userPinEnabled] = false
					Toast.makeText(requireContext(), R.string.lbl_pin_code_removed, Toast.LENGTH_SHORT).show()
					rebuild()
				} else {
					Toast.makeText(requireContext(), R.string.lbl_pin_code_incorrect, Toast.LENGTH_SHORT).show()
				}
			}
		}
	}
}
