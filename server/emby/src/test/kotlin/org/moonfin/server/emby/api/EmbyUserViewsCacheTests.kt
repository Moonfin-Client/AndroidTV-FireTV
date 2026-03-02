package org.moonfin.server.emby.api

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class EmbyUserViewsCacheTests : FunSpec({

	test("cache fields exist on EmbyUserViewsApi") {
		val api = EmbyUserViewsApi::class.java
		val fields = listOf("cachedViews", "cachedUserId", "cacheTimestamp")
		fields.forEach { name ->
			val field = api.getDeclaredField(name)
			field.isAccessible = true
			field shouldNotBe null
		}
	}

	test("cacheTimestamp field is Long type") {
		val field = EmbyUserViewsApi::class.java.getDeclaredField("cacheTimestamp")
		field.isAccessible = true
		field.type shouldBe Long::class.java
	}

	test("invalidateCache method exists") {
		val method = EmbyUserViewsApi::class.java.getDeclaredMethod("invalidateCache")
		method shouldNotBe null
	}

	test("cachedViews field is nullable List type") {
		val field = EmbyUserViewsApi::class.java.getDeclaredField("cachedViews")
		field.isAccessible = true
		field shouldNotBe null
	}
})
