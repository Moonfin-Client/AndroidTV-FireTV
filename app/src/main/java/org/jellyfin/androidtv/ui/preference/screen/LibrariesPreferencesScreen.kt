package org.jellyfin.androidtv.ui.preference.screen

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.MultiServerRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.browsing.DisplayPreferencesScreen
import org.jellyfin.androidtv.ui.livetv.GuideOptionsScreen
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.link
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.koin.android.ext.android.inject

/**
 * Wrapper to hold library info with optional server context for display
 */
private data class LibraryDisplayItem(
	val library: BaseItemDto,
	val displayName: String,
	val isMultiServer: Boolean
)

class LibrariesPreferencesScreen : OptionsFragment() {
	private val userViewsRepository by inject<UserViewsRepository>()
	private val multiServerRepository by inject<MultiServerRepository>()

	// Flow for libraries (supports both single and multi-server)
	private val libraries = MutableStateFlow<List<LibraryDisplayItem>>(emptyList())

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// Load libraries based on server count
		lifecycleScope.launch {
			loadLibraries()
		}

		libraries.onEach {
			rebuild()
		}.launchIn(lifecycleScope)
	}

	private suspend fun loadLibraries() {
		val loggedInServers = multiServerRepository.getLoggedInServers()

		if (loggedInServers.size > 1) {
			// Multi-server: use aggregated libraries with server names
			val aggregatedLibraries = multiServerRepository.getAggregatedLibraries(includeHidden = true)
			libraries.value = aggregatedLibraries.map { aggregated ->
				LibraryDisplayItem(
					library = aggregated.library,
					displayName = aggregated.displayName,
					isMultiServer = true
				)
			}
		} else {
			// Single server: use existing behavior
			userViewsRepository.views.collect { views ->
				libraries.value = views.map { library ->
					LibraryDisplayItem(
						library = library,
						displayName = library.name ?: "",
						isMultiServer = false
					)
				}
			}
		}
	}

	override val screen by optionsScreen {
		setTitle(R.string.pref_libraries)

		category {
			libraries.value.forEach { item ->
				val allowViewSelection = userViewsRepository.allowViewSelection(item.library.collectionType)

				link {
					title = item.displayName

					if (userViewsRepository.allowGridView(item.library.collectionType)) {
						icon = R.drawable.ic_folder
						withFragment<DisplayPreferencesScreen>(bundleOf(
							DisplayPreferencesScreen.ARG_ALLOW_VIEW_SELECTION to allowViewSelection,
							DisplayPreferencesScreen.ARG_PREFERENCES_ID to item.library.displayPreferencesId,
						))
					} else if (item.library.collectionType == CollectionType.LIVETV) {
						icon = R.drawable.ic_guide
						withFragment<GuideOptionsScreen>()
					} else {
						icon = R.drawable.ic_folder
						enabled = false
					}
				}
			}
		}
	}
}
