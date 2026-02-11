package org.jellyfin.androidtv.ui.presentation

import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.ui.DetailRowView
import org.jellyfin.androidtv.ui.itemdetail.MyDetailsOverviewRow
import org.jellyfin.androidtv.util.InfoLayoutHelper
import org.jellyfin.androidtv.util.MarkdownRenderer
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.PersonKind

class MyDetailsOverviewRowPresenter(
	private val markdownRenderer: MarkdownRenderer,
) : RowPresenter() {
	class ViewHolder(
		private val detailRowView: DetailRowView,
		private val markdownRenderer: MarkdownRenderer,
	) : RowPresenter.ViewHolder(detailRowView) {
		private val binding get() = detailRowView.binding

		fun setItem(row: MyDetailsOverviewRow) {
			setTitle(row.item.name)

			InfoLayoutHelper.addInfoRow(view.context, row.item, row.item.mediaSources?.getOrNull(row.selectedMediaSourceIndex), binding.fdMainInfoRow, false)
			InfoLayoutHelper.addRatingsRow(view.context, row.item, binding.fdRatingsRow)
			// Hide genre row - now in grouped metadata
			binding.fdGenreRow.isVisible = false

			// Hide left sidebar info items - now in grouped metadata
			binding.infoTitle1.isVisible = false
			binding.infoValue1.isVisible = false
			binding.infoTitle2.isVisible = false
			binding.infoValue2.isVisible = false
			binding.infoTitle3.isVisible = false
			binding.infoValue3.isVisible = false

			binding.mainImage.load(row.imageDrawable, null, null, 1.0, 0)

			// Set tagline
			val tagline = row.item.taglines?.firstOrNull()
			binding.fdTagline.isVisible = !tagline.isNullOrBlank()
			binding.fdTagline.text = tagline

			setSummary(row.summary)

			if (row.item.type == BaseItemKind.PERSON) {
				binding.fdSummaryText.maxLines = 9
			}

			// Populate grouped metadata
			populateGroupedMetadata(row)

			binding.fdButtonRow.removeAllViews()
			for (button in row.actions) {
				val parent = button.parent
				if (parent is ViewGroup) parent.removeView(button)

				binding.fdButtonRow.addView(button)
			}
		}

	private fun populateGroupedMetadata(row: MyDetailsOverviewRow) {
		val item = row.item

		val genres = if (item.type == BaseItemKind.EPISODE) null else item.genres?.joinToString(", ")
		binding.fdGenresGroup.isVisible = !genres.isNullOrBlank()
		binding.fdGenresContent.text = genres

		val director = item.people?.filter { it.type == PersonKind.DIRECTOR }?.joinToString(", ") { it.name ?: "" }
		binding.fdDirectorGroup.isVisible = !director.isNullOrBlank()
		binding.fdDirectorContent.text = director

		val writers = item.people?.filter { it.type == PersonKind.WRITER }?.joinToString(", ") { it.name ?: "" }
		binding.fdWritersGroup.isVisible = !writers.isNullOrBlank()
		binding.fdWritersContent.text = writers

		val studios = item.studios?.joinToString(", ") { it.name ?: "" }
		binding.fdStudiosGroup.isVisible = !studios.isNullOrBlank()
		binding.fdStudiosContent.text = studios

		// Use InfoItem values from FullDetailsFragment instead of recalculating
		val runs = row.infoItem2?.value
		binding.fdRunsGroup.isVisible = !runs.isNullOrBlank()
		binding.fdRunsContent.text = runs

		val ends = row.infoItem3?.value
		binding.fdEndsGroup.isVisible = !ends.isNullOrBlank()
		binding.fdEndsContent.text = ends
	}

		fun setTitle(title: String?) {
			binding.fdTitle.text = title
			binding.fdTitle.isSelected = true
		}

		fun setSummary(summary: String?) {
			binding.fdSummaryText.text = summary?.let { markdownRenderer.toMarkdownSpanned(it) }
		}

		fun setInfoValue3(text: String?) {
			binding.infoValue3.text = text
		}

		fun updateEndTime(text: String?) {
			binding.fdEndsContent.text = text
			binding.fdEndsGroup.isVisible = !text.isNullOrBlank()
		}
	}

	var viewHolder: ViewHolder? = null
		private set

	init {
		syncActivatePolicy = SYNC_ACTIVATED_CUSTOM
	}

	override fun createRowViewHolder(parent: ViewGroup): ViewHolder {
		val view = DetailRowView(parent.context)
		viewHolder = ViewHolder(view, markdownRenderer)
		return viewHolder!!
	}

	override fun onBindRowViewHolder(viewHolder: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(viewHolder, item)
		if (item !is MyDetailsOverviewRow) return
		if (viewHolder !is ViewHolder) return

		viewHolder.setItem(item)
	}

	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) = Unit
}
