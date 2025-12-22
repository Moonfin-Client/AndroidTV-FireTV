package org.jellyfin.androidtv.ui.itemhandling

import android.content.Context
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.data.model.AggregatedItem
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.apiclient.albumPrimaryImage
import org.jellyfin.androidtv.util.apiclient.getBannerImage
import org.jellyfin.androidtv.util.apiclient.getPrimaryImage
import org.jellyfin.androidtv.util.apiclient.getThumbImage
import org.jellyfin.androidtv.util.apiclient.getThumbImageWithFallback
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.androidtv.util.apiclient.seriesPrimaryImage
import org.jellyfin.androidtv.util.apiclient.seriesThumbImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID

/**
 * Row item for multi-server aggregated items (Continue Watching, Latest Media, etc.)
 * Includes server and user information to enable session switching when clicked.
 * Uses the item's original server API client for image URLs.
 */
class AggregatedItemBaseRowItem @JvmOverloads constructor(
	aggregatedItem: AggregatedItem,
	preferParentThumb: Boolean = false,
	staticHeight: Boolean = false,
	selectAction: BaseRowItemSelectAction = BaseRowItemSelectAction.ShowDetails,
	val server: Server = aggregatedItem.server,
	val userId: UUID = aggregatedItem.userId,
	private val apiClient: ApiClient = aggregatedItem.apiClient,
) : BaseItemDtoBaseRowItem(
	item = aggregatedItem.item,
	preferParentThumb = preferParentThumb,
	staticHeight = staticHeight,
	selectAction = selectAction,
) {
	override fun getImageUrl(
		context: Context,
		imageHelper: ImageHelper,
		imageType: ImageType,
		fillWidth: Int,
		fillHeight: Int
	): String? {
		val item = baseItem ?: return null
		
		Timber.d("AggregatedItemBaseRowItem.getImageUrl OVERRIDE: Item ${item.id} type=$imageType has serverId=${item.serverId}, using apiClient from server ${server.name} (baseUrl=${apiClient.baseUrl})")
		
		return when {
			imageType == ImageType.BANNER -> item.getBannerImage()?.getUrl(
				api = apiClient,
				fillWidth = fillWidth,
				fillHeight = fillHeight
			)

			imageType == ImageType.THUMB -> item.getThumbImageWithFallback()?.getUrl(
				api = apiClient,
				fillWidth = fillWidth,
				fillHeight = fillHeight
			)

			imageType == ImageType.POSTER && item.type == BaseItemKind.EPISODE -> {
				val seriesPoster = item.seriesPrimaryImage
				if (seriesPoster != null) {
					seriesPoster.getUrl(
						api = apiClient,
						fillWidth = fillWidth,
						fillHeight = fillHeight
					)
				} else {
					getPrimaryImageForItem(item, fillWidth, fillHeight)
				}
			}

			else -> getPrimaryImageForItem(item, fillWidth, fillHeight)
		}
	}
	
	private fun getPrimaryImageForItem(item: org.jellyfin.sdk.model.api.BaseItemDto, fillWidth: Int, fillHeight: Int): String? {
		// Check if this is a Jellyseerr item with a direct TMDB URL
		val imageTag = item.imageTags?.get(org.jellyfin.sdk.model.api.ImageType.PRIMARY)
		if (imageTag?.startsWith("http") == true) {
			return imageTag
		}
		
		val image = when {
			preferParentThumb && item.type == BaseItemKind.EPISODE -> item.parentImages[org.jellyfin.sdk.model.api.ImageType.THUMB] ?: item.seriesThumbImage
			!preferParentThumb && item.type == BaseItemKind.EPISODE -> item.seriesPrimaryImage
			item.type == BaseItemKind.SEASON -> item.seriesPrimaryImage
			item.type == BaseItemKind.PROGRAM && item.imageTags?.containsKey(org.jellyfin.sdk.model.api.ImageType.THUMB) == true -> 
				item.itemImages[org.jellyfin.sdk.model.api.ImageType.THUMB]
			item.type == BaseItemKind.AUDIO -> item.albumPrimaryImage
			else -> null
		} ?: item.itemImages[org.jellyfin.sdk.model.api.ImageType.PRIMARY]

		return image?.getUrl(
			api = apiClient,
			fillWidth = fillWidth,
			fillHeight = fillHeight,
		).also { url ->
			Timber.d("AggregatedItemBaseRowItem: Generated URL for item ${item.id}: $url")
		}
	}
}
