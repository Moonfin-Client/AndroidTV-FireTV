package org.jellyfin.androidtv.ui.preference.screen

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.enum
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.preference.enumPreference
import org.jellyfin.preference.store.PreferenceStore
import org.koin.android.ext.android.inject

class HomeRowsImagePreferencesScreen : OptionsFragment() {
	private val userSettingPreferences by inject<UserSettingPreferences>()
	
	// Use a flow to observe active home sections
	private val activeHomeSections by lazy {
		// Create a flow that emits the current active sections
		kotlinx.coroutines.flow.flow {
			emit(userSettingPreferences.activeHomesections)
		}.stateIn(lifecycleScope, SharingStarted.Lazily, emptyList())
	}

	override val stores: Array<PreferenceStore<*, *>>
		get() = arrayOf(userSettingPreferences)

	override val rebuildOnResume = true

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// Rebuild when home sections change
		activeHomeSections.onEach {
			rebuild()
		}.launchIn(lifecycleScope)
	}

	override val screen by optionsScreen {
		setTitle(R.string.pref_home_rows_image_type)

		category {
			setTitle(R.string.pref_universal_override)
			
			checkbox {
				setTitle(R.string.pref_home_rows_universal_override)
				setContent(R.string.pref_home_rows_universal_override_description)
				bind(userSettingPreferences, UserSettingPreferences.homeRowsUniversalOverride)
			}

			enum<ImageType> {
				setTitle(R.string.pref_home_rows_universal_image_type)
				bind(userSettingPreferences, UserSettingPreferences.homeRowsUniversalImageType)
				depends { userSettingPreferences[UserSettingPreferences.homeRowsUniversalOverride] }
			}
		}

		category {
			setTitle(R.string.pref_home_rows_per_row)
			
			// Generate options for each active home section
			activeHomeSections.value.forEach { sectionType ->
				// Create a dynamic preference for each row
				val rowPref = enumPreference<ImageType>(
					"homeRowImageType_${sectionType.serializedName}",
					ImageType.POSTER
				)
				
				enum<ImageType> {
					title = getString(sectionType.nameRes)
					bind(userSettingPreferences, rowPref)
					depends { !userSettingPreferences[UserSettingPreferences.homeRowsUniversalOverride] }
				}
			}
		}
	}
}
