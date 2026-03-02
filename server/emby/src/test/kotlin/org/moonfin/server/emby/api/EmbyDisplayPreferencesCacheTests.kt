package org.moonfin.server.emby.api

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class EmbyDisplayPreferencesCacheTests : FunSpec({

	test("cache map field exists") {
		val field = EmbyDisplayPreferencesApi::class.java.getDeclaredField("cache")
		field.isAccessible = true
		field shouldNotBe null
	}

	test("CacheKey data class has id, userId, client fields") {
		val cacheKeyClass = Class.forName(
			"org.moonfin.server.emby.api.EmbyDisplayPreferencesApi\$CacheKey"
		)
		val fields = cacheKeyClass.declaredFields.map { it.name }
		fields shouldContainAll listOf("id", "userId", "client")
	}

	test("CacheEntry data class has prefs and timestamp fields") {
		val cacheEntryClass = Class.forName(
			"org.moonfin.server.emby.api.EmbyDisplayPreferencesApi\$CacheEntry"
		)
		val fieldNames = cacheEntryClass.declaredFields.map { it.name }
		fieldNames shouldContainAll listOf("prefs", "timestamp")
	}

	test("invalidateCache method exists") {
		val method = EmbyDisplayPreferencesApi::class.java.getDeclaredMethod("invalidateCache")
		method shouldNotBe null
	}

	test("CacheEntry timestamp field is Long type") {
		val cacheEntryClass = Class.forName(
			"org.moonfin.server.emby.api.EmbyDisplayPreferencesApi\$CacheEntry"
		)
		val timestampField = cacheEntryClass.getDeclaredField("timestamp")
		timestampField.type shouldBe Long::class.java
	}
})
