package org.jellyfin.androidtv.util

import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ImageResult

class EmbyCacheKeyInterceptor : Interceptor {

	override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
		val request = chain.request
		val url = request.data?.toString() ?: return chain.proceed()

		if (!url.contains("api_key=")) return chain.proceed()

		val normalized = stripApiKey(url)
		val newRequest = request.newBuilder()
			.memoryCacheKey(MemoryCache.Key(normalized))
			.diskCacheKey(normalized)
			.build()
		return chain.withRequest(newRequest).proceed()
	}

	private fun stripApiKey(url: String): String = url
		.replace(Regex("&api_key=[^&]*"), "")
		.replace(Regex("\\?api_key=[^&]*&"), "?")
		.replace(Regex("\\?api_key=[^&]*$"), "")
}
