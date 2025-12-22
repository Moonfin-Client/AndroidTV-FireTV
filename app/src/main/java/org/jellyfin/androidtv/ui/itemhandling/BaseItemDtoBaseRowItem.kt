package org.jellyfin.androidtv.ui.itemhandling

import android.content.Context
import android.graphics.drawable.Drawable
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.TimeUtils
import org.jellyfin.androidtv.util.apiclient.seriesPrimaryImage
import org.jellyfin.androidtv.util.sdk.getFullName
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber

open class BaseItemDtoBaseRowItem @JvmOverloads constructor(
	item: BaseItemDto,
	preferParentThumb: Boolean = false,
	staticHeight: Boolean = false,
	selectAction: BaseRowItemSelectAction = BaseRowItemSelectAction.ShowDetails,
) : BaseRowItem(
	baseRowType = when (item.type) {
		BaseItemKind.TV_CHANNEL,
		BaseItemKind.LIVE_TV_CHANNEL -> BaseRowType.LiveTvChannel

		BaseItemKind.PROGRAM,
		BaseItemKind.TV_PROGRAM,
		BaseItemKind.LIVE_TV_PROGRAM -> BaseRowType.LiveTvProgram

		BaseItemKind.RECORDING -> BaseRowType.LiveTvRecording
		else -> BaseRowType.BaseItem
	},
	staticHeight = staticHeight,
	preferParentThumb = preferParentThumb,
	selectAction = selectAction,
	baseItem = item,
) {
	override val showCardInfoOverlay
		get() = when (baseItem?.type) {
			BaseItemKind.FOLDER,
			BaseItemKind.PHOTO_ALBUM,
			BaseItemKind.USER_VIEW,
			BaseItemKind.COLLECTION_FOLDER,
			BaseItemKind.PHOTO,
			BaseItemKind.VIDEO,
			BaseItemKind.PERSON,
			BaseItemKind.PLAYLIST,
			BaseItemKind.MUSIC_ARTIST -> true

			else -> false
		}

	override val itemId get() = baseItem?.id

	override val isFavorite get() = baseItem?.userData?.isFavorite == true
	override val isPlayed get() = baseItem?.userData?.played == true

	val childCountStr: String?
		get() {
			// Playlist
			if (baseItem?.type == BaseItemKind.PLAYLIST) {
				val childCount = baseItem.cumulativeRunTimeTicks?.ticks?.let { duration ->
					TimeUtils.formatMillis(duration.inWholeMilliseconds)
				}
				if (childCount != null) return childCount
			}

			// Folder
			if (baseItem?.isFolder == true && baseItem.type != BaseItemKind.MUSIC_ARTIST) {
				val childCount = baseItem.childCount
				if (childCount != null && childCount > 0) return childCount.toString()
			}

			// Default
			return null
		}

	override fun getCardName(context: Context) = when {
		baseItem?.type == BaseItemKind.AUDIO && baseItem.artists != null -> baseItem.artists?.joinToString(", ")
		baseItem?.type == BaseItemKind.AUDIO && baseItem.albumArtists != null -> baseItem.albumArtists?.joinToString(", ")
		baseItem?.type == BaseItemKind.AUDIO && baseItem.albumArtist != null -> baseItem.albumArtist
		baseItem?.type == BaseItemKind.AUDIO && baseItem.album != null -> baseItem.album
		else -> baseItem?.getFullName(context)
	}

	override fun getFullName(context: Context) = baseItem?.getFullName(context)
	override fun getName(context: Context) = when (baseItem?.type) {
		BaseItemKind.AUDIO -> baseItem.getFullName(context)
		else -> baseItem?.name
	}

	override fun getSummary(context: Context) = baseItem?.overview

	override fun getSubText(context: Context): String? = null

	override fun getImageUrl(
		context: Context,
		imageHelper: ImageHelper,
		imageType: ImageType,
		fillWidth: Int,
		fillHeight: Int
	): String? {
		Timber.d("BaseItemDtoBaseRowItem.getImageUrl called for item ${baseItem?.id} type=$imageType")
		return when {
			imageType == ImageType.BANNER -> imageHelper.getBannerImageUrl(
				requireNotNull(baseItem), fillWidth, fillHeight
			)

			imageType == ImageType.THUMB -> imageHelper.getThumbImageUrl(
				requireNotNull(baseItem), fillWidth, fillHeight
			)

			imageType == ImageType.POSTER && baseItem?.type == BaseItemKind.EPISODE -> {
				val seriesPoster = baseItem?.seriesPrimaryImage
				if (seriesPoster != null) {
					imageHelper.getImageUrl(seriesPoster, baseItem!!, fillWidth, fillHeight)
				} else {
					imageHelper.getPrimaryImageUrl(
						baseItem!!,
						preferParentThumb,
						null,
						fillHeight
					)
				}
			}

			else -> imageHelper.getPrimaryImageUrl(
				baseItem!!,
				preferParentThumb,
				null,
				fillHeight
			)
		}
	}

	override fun getBadgeImage(
		context: Context,
		imageHelper: ImageHelper,
	): Drawable? = null
}
