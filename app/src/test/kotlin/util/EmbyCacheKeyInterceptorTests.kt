package util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.util.EmbyCacheKeyInterceptor

class EmbyCacheKeyInterceptorTests : FunSpec({

	val interceptor = EmbyCacheKeyInterceptor()

	fun stripApiKey(url: String): String {
		val method = EmbyCacheKeyInterceptor::class.java.getDeclaredMethod("stripApiKey", String::class.java)
		method.isAccessible = true
		return method.invoke(interceptor, url) as String
	}

	test("strips api_key as only query parameter") {
		stripApiKey("http://emby:8096/Items/123/Images/Primary?api_key=token123") shouldBe
			"http://emby:8096/Items/123/Images/Primary"
	}

	test("strips api_key as last query parameter") {
		stripApiKey("http://emby:8096/Items/123/Images/Primary?maxWidth=300&api_key=token123") shouldBe
			"http://emby:8096/Items/123/Images/Primary?maxWidth=300"
	}

	test("strips api_key as first query parameter") {
		stripApiKey("http://emby:8096/Items/123/Images/Primary?api_key=token123&maxWidth=300") shouldBe
			"http://emby:8096/Items/123/Images/Primary?maxWidth=300"
	}

	test("strips api_key as middle query parameter") {
		stripApiKey("http://emby:8096/Items/123/Images/Primary?maxWidth=300&api_key=token123&tag=abc") shouldBe
			"http://emby:8096/Items/123/Images/Primary?maxWidth=300&tag=abc"
	}

	test("returns url unchanged when no api_key present") {
		stripApiKey("http://emby:8096/Items/123/Images/Primary?maxWidth=300") shouldBe
			"http://emby:8096/Items/123/Images/Primary?maxWidth=300"
	}

	test("returns url unchanged when no query parameters") {
		stripApiKey("http://emby:8096/Items/123/Images/Primary") shouldBe
			"http://emby:8096/Items/123/Images/Primary"
	}

	test("preserves tag parameter for cache busting") {
		stripApiKey("http://emby:8096/Items/123/Images/Primary?tag=abc123&api_key=token") shouldBe
			"http://emby:8096/Items/123/Images/Primary?tag=abc123"
	}

	test("same image with different api_key produces same cache key") {
		val url1 = "http://emby:8096/Items/123/Images/Primary?maxWidth=300&tag=abc&api_key=token1"
		val url2 = "http://emby:8096/Items/123/Images/Primary?maxWidth=300&tag=abc&api_key=token2"
		stripApiKey(url1) shouldBe stripApiKey(url2)
	}

	test("different images produce different cache keys") {
		val url1 = "http://emby:8096/Items/123/Images/Primary?tag=abc&api_key=token"
		val url2 = "http://emby:8096/Items/456/Images/Primary?tag=abc&api_key=token"
		val key1 = stripApiKey(url1)
		val key2 = stripApiKey(url2)
		(key1 == key2) shouldBe false
	}

	test("different tags produce different cache keys") {
		val url1 = "http://emby:8096/Items/123/Images/Primary?tag=abc&api_key=token"
		val url2 = "http://emby:8096/Items/123/Images/Primary?tag=def&api_key=token"
		val key1 = stripApiKey(url1)
		val key2 = stripApiKey(url2)
		(key1 == key2) shouldBe false
	}
})
