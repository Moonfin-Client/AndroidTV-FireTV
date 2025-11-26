package org.jellyfin.androidtv.data.service.jellyseerr

import android.content.Context
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Persistent cookie storage that saves cookies to SharedPreferences
 * This allows cookies to survive app restarts
 */
class PersistentCookiesStorage(context: Context) : CookiesStorage {
	private val preferences = context.getSharedPreferences("jellyseerr_cookies", Context.MODE_PRIVATE)
	private val mutex = Mutex()
	private val cookies = mutableMapOf<String, Cookie>()

	init {
		// Load cookies from preferences on initialization
		loadCookies()
	}

	private fun loadCookies() {
		try {
			preferences.all.forEach { (key, value) ->
				if (value is String) {
					val cookie = deserializeCookie(value)
					if (cookie != null && !isExpired(cookie)) {
						cookies[key] = cookie
						Timber.d("PersistentCookiesStorage: Loaded cookie: $key")
					}
				}
			}
		} catch (e: Exception) {
			Timber.e(e, "PersistentCookiesStorage: Error loading cookies")
		}
	}

	private fun saveCookies() {
		try {
			preferences.edit().apply {
				clear()
				cookies.forEach { (key, cookie) ->
					putString(key, serializeCookie(cookie))
				}
				apply()
			}
		} catch (e: Exception) {
			Timber.e(e, "PersistentCookiesStorage: Error saving cookies")
		}
	}

	override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
		val now = GMTDate()
		
		// Remove expired cookies
		val expiredKeys = cookies.filter { (_, cookie) ->
			isExpired(cookie)
		}.keys
		
		if (expiredKeys.isNotEmpty()) {
			expiredKeys.forEach { cookies.remove(it) }
			saveCookies()
		}

		// Return cookies that match the URL
		cookies.values.filter { cookie ->
			matchesDomain(cookie, requestUrl) && matchesPath(cookie, requestUrl)
		}.also {
			if (it.isNotEmpty()) {
				Timber.d("PersistentCookiesStorage: Found ${it.size} cookie(s) for ${requestUrl.host}")
				it.forEach { cookie ->
					val expiresIn = cookie.expires?.let { expires ->
						val diff = expires.timestamp - GMTDate().timestamp
						val days = diff / (24 * 60 * 60 * 1000)
						"$days days"
					} ?: "session"
					Timber.d("  - Cookie: ${cookie.name}, value length: ${cookie.value.length}, expires in: $expiresIn")
				}
			} else {
				Timber.w("PersistentCookiesStorage: No cookies found for ${requestUrl.host}, total stored: ${cookies.size}")
			}
		}
	}

	override suspend fun addCookie(requestUrl: Url, cookie: Cookie): Unit = mutex.withLock {
		val key = "${cookie.name}_${requestUrl.host}"
		cookies[key] = cookie
		saveCookies()
		
		// Log cookie details including expiration
		val expiresIn = cookie.expires?.let { expires ->
			val diff = expires.timestamp - GMTDate().timestamp
			val days = diff / (24 * 60 * 60 * 1000)
			"$days days"
		} ?: "session"
		Timber.d("PersistentCookiesStorage: Saved cookie: ${cookie.name} for ${requestUrl.host}, expires in: $expiresIn, maxAge: ${cookie.maxAge}")
	}

	override fun close() {
		// Save cookies one final time before closing
		try {
			saveCookies()
		} catch (e: Exception) {
			Timber.e(e, "PersistentCookiesStorage: Error during close")
		}
	}

	/**
	 * Clear all stored cookies
	 */
	suspend fun clearAll() = mutex.withLock {
		cookies.clear()
		preferences.edit().clear().apply()
		Timber.d("PersistentCookiesStorage: Cleared all cookies")
	}

	private fun isExpired(cookie: Cookie): Boolean {
		val expires = cookie.expires ?: return false
		return expires.timestamp < GMTDate().timestamp
	}

	private fun matchesDomain(cookie: Cookie, url: Url): Boolean {
		val domain = cookie.domain?.lowercase() ?: return true
		val host = url.host.lowercase()
		
		return if (domain.startsWith(".")) {
			// Domain cookie: matches host and all subdomains
			host == domain.substring(1) || host.endsWith(domain)
		} else {
			// Exact match
			host == domain
		}
	}

	private fun matchesPath(cookie: Cookie, url: Url): Boolean {
		val cookiePath = cookie.path ?: "/"
		val urlPath = url.encodedPath
		return urlPath.startsWith(cookiePath)
	}

	private fun serializeCookie(cookie: Cookie): String {
		return buildString {
			append(cookie.name)
			append("|")
			append(cookie.value)
			append("|")
			append(cookie.domain ?: "")
			append("|")
			append(cookie.path ?: "")
			append("|")
			append(cookie.expires?.timestamp ?: 0)
			append("|")
			append(cookie.maxAge)
			append("|")
			append(cookie.secure)
			append("|")
			append(cookie.httpOnly)
		}
	}

	private fun deserializeCookie(data: String): Cookie? {
		return try {
			val parts = data.split("|")
			if (parts.size < 8) return null

			Cookie(
				name = parts[0],
				value = parts[1],
				domain = parts[2].ifEmpty { null },
				path = parts[3].ifEmpty { null },
				expires = parts[4].toLongOrNull()?.let { GMTDate(it) },
				maxAge = parts[5].toIntOrNull() ?: 0,
				secure = parts[6].toBoolean(),
				httpOnly = parts[7].toBoolean()
			)
		} catch (e: Exception) {
			Timber.e(e, "PersistentCookiesStorage: Error deserializing cookie")
			null
		}
	}
}
