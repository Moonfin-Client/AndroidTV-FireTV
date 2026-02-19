package org.jellyfin.androidtv.ui.search

import android.content.Context
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.data.service.BlurContext
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.CustomListRowPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter

class SearchFragmentDelegate(
	private val context: Context,
	private val backgroundService: BackgroundService,
	private val itemLauncher: ItemLauncher,
	private val userPreferences: UserPreferences,
) {
	val rowsAdapter = MutableObjectAdapter<Row>(CustomListRowPresenter(
		focusZoomFactor = if (userPreferences[UserPreferences.cardFocusExpansion]) FocusHighlight.ZOOM_FACTOR_MEDIUM else FocusHighlight.ZOOM_FACTOR_NONE
	))

	fun showResults(searchResultGroups: Collection<SearchResultGroup>) {
		rowsAdapter.clear()
		val adapters = mutableListOf<ItemRowAdapter>()
		for ((labelRes, baseItems) in searchResultGroups) {
			val enableMultiServer = userPreferences[UserPreferences.enableMultiServerLibraries]
			val adapter = ItemRowAdapter(
				context,
				baseItems.toList(),
				CardPresenter(showInfo = true, imageType = ImageType.POSTER, staticHeight = 150, uniformAspect = false, showServerBadge = enableMultiServer),
				rowsAdapter,
				QueryType.Search
			).apply {
				setRow(ListRow(HeaderItem(context.getString(labelRes)), this))
			}
			adapters.add(adapter)
		}
		for (adapter in adapters) adapter.Retrieve()
	}

	val onItemViewClickedListener = OnItemViewClickedListener { _, item, _, row ->
		if (item !is BaseRowItem) return@OnItemViewClickedListener
		row as ListRow
		val adapter = row.adapter as ItemRowAdapter
		itemLauncher.launch(item as BaseRowItem?, adapter, context)
	}

	val onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
		val baseItem = item?.let { (item as BaseRowItem).baseItem }
		if (baseItem != null) {
			backgroundService.setBackground(baseItem, BlurContext.BROWSING)
		} else {
			backgroundService.clearBackgrounds()
		}
	}
}
