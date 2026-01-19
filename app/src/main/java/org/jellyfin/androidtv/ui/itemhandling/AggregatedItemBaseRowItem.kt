package org.jellyfin.androidtv.ui.itemhandling

import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.data.model.AggregatedItem
import org.jellyfin.sdk.api.client.ApiClient
import java.util.UUID

/**
 * Row item for multi-server aggregated items (Continue Watching, Latest Media, etc.)
 * Includes server and user information to enable session switching when clicked.
 * Image URLs are resolved via ApiClientFactory using the item's serverId.
 */
class AggregatedItemBaseRowItem @JvmOverloads constructor(
	aggregatedItem: AggregatedItem,
	preferParentThumb: Boolean = false,
	staticHeight: Boolean = false,
	selectAction: BaseRowItemSelectAction = BaseRowItemSelectAction.ShowDetails,
	val server: Server = aggregatedItem.server,
	val userId: UUID = aggregatedItem.userId,
	val apiClient: ApiClient = aggregatedItem.apiClient,
) : BaseItemDtoBaseRowItem(
	item = aggregatedItem.item,
	preferParentThumb = preferParentThumb,
	staticHeight = staticHeight,
	selectAction = selectAction,
)
