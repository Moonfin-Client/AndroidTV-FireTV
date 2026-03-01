package org.moonfin.server.core.api

import org.moonfin.server.core.model.ImageType

interface ServerImageApi {
    fun getItemImageUrl(itemId: String, imageType: ImageType, maxWidth: Int? = null, maxHeight: Int? = null, tag: String? = null): String
    fun getUserImageUrl(userId: String, imageType: ImageType, tag: String? = null): String
}
