package org.moonfin.server.emby.api

import org.moonfin.server.core.api.ServerImageApi
import org.moonfin.server.core.model.ImageType
import org.moonfin.server.emby.EmbyApiClient

class EmbyImageApi(private val apiClient: EmbyApiClient) : ServerImageApi {

    override fun getItemImageUrl(
        itemId: String,
        imageType: ImageType,
        maxWidth: Int?,
        maxHeight: Int?,
        tag: String?,
    ): String = buildUrl("Items/$itemId/Images/${imageType.toPathSegment()}") {
        maxWidth?.let { param("maxWidth", it) }
        maxHeight?.let { param("maxHeight", it) }
        tag?.let { param("tag", it) }
        apiClient.accessToken?.let { param("api_key", it) }
    }

    override fun getUserImageUrl(
        userId: String,
        imageType: ImageType,
        tag: String?,
    ): String = buildUrl("Users/$userId/Images/${imageType.toPathSegment()}") {
        tag?.let { param("tag", it) }
        apiClient.accessToken?.let { param("api_key", it) }
    }

    private fun buildUrl(path: String, block: QueryBuilder.() -> Unit): String {
        val base = apiClient.baseUrl.trimEnd('/')
        val builder = QueryBuilder()
        builder.block()
        val query = builder.build()
        return if (query.isEmpty()) "$base/$path" else "$base/$path?$query"
    }

    private class QueryBuilder {
        private val params = mutableListOf<Pair<String, Any>>()

        fun param(key: String, value: Any) {
            params += key to value
        }

        fun build(): String = params.joinToString("&") { (k, v) -> "$k=$v" }
    }

    private fun ImageType.toPathSegment(): String = when (this) {
        ImageType.PRIMARY -> "Primary"
        ImageType.BACKDROP -> "Backdrop"
        ImageType.BANNER -> "Banner"
        ImageType.THUMB -> "Thumb"
        ImageType.LOGO -> "Logo"
        ImageType.ART -> "Art"
        ImageType.SCREENSHOT -> "Screenshot"
    }
}
