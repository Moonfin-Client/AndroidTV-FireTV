package org.jellyfin.androidtv.ui.preference.screen

import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.preference.custom.DurationSeekBarPreference
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.link
import org.jellyfin.androidtv.ui.preference.dsl.list
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.androidtv.ui.preference.dsl.seekbar
import org.jellyfin.preference.store.PreferenceStore
import org.koin.android.ext.android.inject

class MoonfinPreferencesScreen : OptionsFragment() {
	private val userSettingPreferences: UserSettingPreferences by inject()
	private val userPreferences: UserPreferences by inject()

	override val stores: Array<PreferenceStore<*, *>>
		get() = arrayOf(userSettingPreferences, userPreferences)

	override val screen by optionsScreen {
		setTitle(R.string.moonfin_settings)

		// Toolbar Customization
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

			checkbox {
				setTitle(R.string.pref_show_libraries_in_toolbar)
				setContent(R.string.pref_show_libraries_in_toolbar_description)
				bind(userPreferences, UserPreferences.showLibrariesInToolbar)
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

		// Home Screen
		category {
			setTitle(R.string.home_section_settings)

			checkbox {
				setTitle(R.string.lbl_merge_continue_watching_next_up)
				setContent(R.string.lbl_merge_continue_watching_next_up_description)
				bind(userPreferences, UserPreferences.mergeContinueWatchingNextUp)
			}

			checkbox {
				setTitle(R.string.pref_confirm_exit)
				setContent(R.string.pref_confirm_exit_description)
				bind(userPreferences, UserPreferences.confirmExit)
			}
		}

		// Media Bar Settings
		category {
			setTitle(R.string.pref_media_bar_title)

			checkbox {
				setTitle(R.string.pref_media_bar_enable)
				setContent(R.string.pref_media_bar_enable_summary)
				bind(userSettingPreferences, UserSettingPreferences.mediaBarEnabled)
			}

			list {
				setTitle(R.string.pref_media_bar_content_type)
				entries = mapOf(
					"movies" to getString(R.string.pref_shuffle_movies),
					"tv" to getString(R.string.pref_shuffle_tv),
					"both" to getString(R.string.pref_shuffle_both)
				)
				bind(userSettingPreferences, UserSettingPreferences.mediaBarContentType)
				
				depends { userSettingPreferences[UserSettingPreferences.mediaBarEnabled] }
			}

			list {
				setTitle(R.string.pref_media_bar_item_count)
				entries = mapOf(
					"5" to getString(R.string.pref_media_bar_5_items),
					"10" to getString(R.string.pref_media_bar_10_items),
					"15" to getString(R.string.pref_media_bar_15_items)
				)
				bind(userSettingPreferences, UserSettingPreferences.mediaBarItemCount)
				
				depends { userSettingPreferences[UserSettingPreferences.mediaBarEnabled] }
			}

		seekbar {
			setTitle(R.string.pref_media_bar_overlay_opacity)
			setContent(R.string.pref_media_bar_overlay_opacity_summary)
			min = 10
			max = 90
			increment = 5
			valueFormatter = object : DurationSeekBarPreference.ValueFormatter() {
				override fun display(value: Int) = "$value%"
			}
			bind(userSettingPreferences, UserSettingPreferences.mediaBarOverlayOpacity)
			
			depends { userSettingPreferences[UserSettingPreferences.mediaBarEnabled] }
		}

		list {
			setTitle(R.string.pref_media_bar_overlay_color)
				entries = mapOf(
					"black" to getString(R.string.pref_media_bar_color_black),
					"gray" to getString(R.string.pref_media_bar_color_gray),
					"dark_blue" to getString(R.string.pref_media_bar_color_dark_blue),
					"purple" to getString(R.string.pref_media_bar_color_purple),
					"teal" to getString(R.string.pref_media_bar_color_teal),
					"navy" to getString(R.string.pref_media_bar_color_navy),
					"charcoal" to getString(R.string.pref_media_bar_color_charcoal),
					"brown" to getString(R.string.pref_media_bar_color_brown),
					"dark_red" to getString(R.string.pref_media_bar_color_dark_red),
					"dark_green" to getString(R.string.pref_media_bar_color_dark_green),
					"slate" to getString(R.string.pref_media_bar_color_slate),
					"indigo" to getString(R.string.pref_media_bar_color_indigo)
				)
				bind(userSettingPreferences, UserSettingPreferences.mediaBarOverlayColor)
				
				depends { userSettingPreferences[UserSettingPreferences.mediaBarEnabled] }
			}
		}

		// Theme Music
		category {
			setTitle(R.string.pref_theme_music_title)

			checkbox {
				setTitle(R.string.pref_theme_music_enable)
				setContent(R.string.pref_theme_music_enable_summary)
				bind(userSettingPreferences, UserSettingPreferences.themeMusicEnabled)
			}

			seekbar {
				setTitle(R.string.pref_theme_music_volume)
				setContent(R.string.pref_theme_music_volume_summary)
				min = 10
				max = 100
				increment = 5
				valueFormatter = object : DurationSeekBarPreference.ValueFormatter() {
					override fun display(value: Int) = "$value%"
				}
				bind(userSettingPreferences, UserSettingPreferences.themeMusicVolume)
				
				depends { userSettingPreferences[UserSettingPreferences.themeMusicEnabled] }
			}
		}

		// Screensaver
		category {
			setTitle(R.string.pref_screensaver)

			list {
				setTitle(R.string.pref_screensaver_mode)
				
				entries = mapOf(
					"library" to getString(R.string.pref_screensaver_mode_library),
					"logo" to getString(R.string.pref_screensaver_mode_logo)
				)
				
				bind(userPreferences, UserPreferences.screensaverMode)
			}

			seekbar {
				setTitle(R.string.pref_screensaver_dimming)
				setContent(R.string.pref_screensaver_dimming_level_description)
				min = 0
				max = 100
				increment = 5
				valueFormatter = object : DurationSeekBarPreference.ValueFormatter() {
					override fun display(value: Int) = if (value == 0) "Off" else "$value%"
				}

				bind(userPreferences, UserPreferences.screensaverDimmingLevel)

				depends { userPreferences[UserPreferences.screensaverInAppEnabled] }
			}
		}

		// Visual Settings
		category {
			setTitle(R.string.pref_visual_settings)
			
			link {
				setTitle(R.string.pref_home_rows_image_type)
				icon = R.drawable.ic_grid
				withFragment<HomeRowsImagePreferencesScreen>()
			}

			seekbar {
				setTitle(R.string.pref_details_background_blur_amount)
				setContent(R.string.pref_details_background_blur_amount_description)
				min = 0
				max = 20
				increment = 5
				valueFormatter = object : DurationSeekBarPreference.ValueFormatter() {
					override fun display(value: Int) = when (value) {
						0 -> getString(R.string.pref_blur_none)
						5 -> getString(R.string.pref_blur_light)
						10 -> getString(R.string.pref_blur_medium)
						15 -> getString(R.string.pref_blur_strong)
						20 -> getString(R.string.pref_blur_extra_strong)
						else -> "${value}dp"
					}
				}
				bind(userSettingPreferences, UserSettingPreferences.detailsBackgroundBlurAmount)
			}
			
			seekbar {
				setTitle(R.string.pref_browsing_background_blur_amount)
				setContent(R.string.pref_browsing_background_blur_amount_description)
				min = 0
				max = 20
				increment = 5
				valueFormatter = object : DurationSeekBarPreference.ValueFormatter() {
					override fun display(value: Int) = when (value) {
						0 -> getString(R.string.pref_blur_none)
						5 -> getString(R.string.pref_blur_light)
						10 -> getString(R.string.pref_blur_medium)
						15 -> getString(R.string.pref_blur_strong)
						20 -> getString(R.string.pref_blur_extra_strong)
						else -> "${value}dp"
					}
				}
				bind(userSettingPreferences, UserSettingPreferences.browsingBackgroundBlurAmount)
			}
		}
	}
}
