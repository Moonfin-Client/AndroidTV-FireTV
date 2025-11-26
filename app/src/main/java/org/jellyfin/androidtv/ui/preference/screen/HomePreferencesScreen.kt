package org.jellyfin.androidtv.ui.preference.screen

import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.enum
import org.jellyfin.androidtv.ui.preference.dsl.list
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.preference.store.PreferenceStore
import org.koin.android.ext.android.inject

class HomePreferencesScreen : OptionsFragment() {
	private val userSettingPreferences: UserSettingPreferences by inject()
	private val userPreferences: UserPreferences by inject()

	override val stores: Array<PreferenceStore<*, *>>
		get() = arrayOf(userSettingPreferences, userPreferences)

	override val screen by optionsScreen {
		setTitle(R.string.home_prefs)

		category {
			setTitle(R.string.home_sections)

			userSettingPreferences.homesections.forEachIndexed { index, section ->
				enum<HomeSectionType> {
					title = getString(R.string.home_section_i, index + 1)
					bind(userSettingPreferences, section)
				}
			}
		}

		category {
			setTitle(R.string.pref_toolbar_customization)

			checkbox {
				setTitle(R.string.pref_show_shuffle_button)
				setContent(R.string.pref_show_shuffle_button_description)
				bind(userPreferences, UserPreferences.showShuffleButton)
			}

			checkbox {
				setTitle(R.string.pref_show_genres_button)
				setContent(R.string.pref_show_genres_button_description)
				bind(userPreferences, UserPreferences.showGenresButton)
			}

			checkbox {
				setTitle(R.string.pref_show_favorites_button)
				setContent(R.string.pref_show_favorites_button_description)
				bind(userPreferences, UserPreferences.showFavoritesButton)
			}

		list {
			setTitle(R.string.pref_shuffle_content_type)
			
			entries = mapOf(
				"movies" to getString(R.string.pref_shuffle_movies),
				"tv" to getString(R.string.pref_shuffle_tv),
				"both" to getString(R.string.pref_shuffle_both)
			)
			bind(userPreferences, UserPreferences.shuffleContentType)
		}
	}
}
}
