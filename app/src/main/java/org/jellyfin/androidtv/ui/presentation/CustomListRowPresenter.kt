package org.jellyfin.androidtv.ui.presentation

import android.view.View
import androidx.core.view.isVisible
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter

open class CustomListRowPresenter @JvmOverloads constructor(
	private val topPadding: Int? = null,
	focusZoomFactor: Int = FocusHighlight.ZOOM_FACTOR_MEDIUM,
) : ListRowPresenter(focusZoomFactor) {
	init {
		headerPresenter = CustomRowHeaderPresenter()
	}

	override fun isUsingDefaultShadow() = false

	@Suppress("UNUSED_PARAMETER")
	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) {
		// No action needed
	}

	// Main implementation that handles binding
	@Suppress("DEPRECATION")
	override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(holder, item)
		updateView(holder, item)
	}

	private fun updateView(holder: RowPresenter.ViewHolder, item: Any) {
		val view = holder.view.parent as? View ?: return
		if (topPadding != null) {
			view.setPadding(view.paddingLeft, topPadding, view.paddingRight, view.paddingBottom)
		}

		// Hide header view when the item doesn't have one
		holder.headerViewHolder.view.isVisible = !(item is ListRow && item.headerItem == null)
	}
}
