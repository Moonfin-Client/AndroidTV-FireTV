package org.jellyfin.androidtv.ui.itemhandling

import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.util.apiclient.Response
import org.jellyfin.androidtv.util.sdk.ApiClientFactory
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.java.KoinJavaComponent
import timber.log.Timber
import java.util.UUID

object ItemLauncherHelper {
	@JvmStatic
	fun getItem(itemId: UUID, callback: Response<BaseItemDto>) {
		getItem(itemId, null, callback)
	}

	@JvmStatic
	fun getItem(itemId: UUID, serverId: UUID?, callback: Response<BaseItemDto>) {
		ProcessLifecycleOwner.get().lifecycleScope.launch {
			val defaultApi by KoinJavaComponent.inject<ApiClient>(ApiClient::class.java)
			
			// If serverId is provided, try to get the API client for that server
			val api = if (serverId != null) {
				val apiClientFactory by KoinJavaComponent.inject<ApiClientFactory>(ApiClientFactory::class.java)
				val serverApi = apiClientFactory.getApiClientForServer(serverId)
				if (serverApi != null) {
					Timber.d("ItemLauncherHelper: Using API client for server $serverId")
					serverApi
				} else {
					Timber.w("ItemLauncherHelper: Could not get API client for server $serverId, using default")
					defaultApi
				}
			} else {
				defaultApi
			}

			try {
				val response = withContext(Dispatchers.IO) {
					api.userLibraryApi.getItem(itemId = itemId).content
				}
				callback.onResponse(response)
			} catch (error: ApiClientException) {
				callback.onError(error)
			}
		}
	}
}
