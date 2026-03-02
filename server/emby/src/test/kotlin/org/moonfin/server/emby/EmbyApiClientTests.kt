package org.moonfin.server.emby

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class EmbyApiClientTests : FunSpec({

	fun createClient() = EmbyApiClient(
		appVersion = "1.0.0",
		clientName = "TestClient",
		deviceId = "test-device-id",
		deviceName = "TestDevice",
	)

	test("initial state has empty baseUrl") {
		val client = createClient()
		client.baseUrl shouldBe ""
		client.accessToken shouldBe null
		client.userId shouldBe null
		client.isConfigured shouldBe false
	}

	test("configure sets baseUrl, accessToken, and userId") {
		val client = createClient()
		client.configure("http://emby.local:8096", "test-token", "user-123")

		client.baseUrl shouldBe "http://emby.local:8096"
		client.accessToken shouldBe "test-token"
		client.userId shouldBe "user-123"
		client.isConfigured shouldBe true
	}

	test("configure with null accessToken is not considered configured") {
		val client = createClient()
		client.configure("http://emby.local:8096", null, null)

		client.baseUrl shouldBe "http://emby.local:8096"
		client.accessToken shouldBe null
		client.isConfigured shouldBe false
	}

	test("configure with empty baseUrl clears all services") {
		val client = createClient()
		client.configure("http://emby.local:8096", "token", "user")
		client.isConfigured shouldBe true

		client.configure("", null, null)
		client.baseUrl shouldBe ""
		client.isConfigured shouldBe false
		client.itemsService shouldBe null
		client.userLibraryService shouldBe null
		client.tvShowsService shouldBe null
		client.libraryService shouldBe null
		client.playstateService shouldBe null
		client.userViewsService shouldBe null
		client.liveTvService shouldBe null
		client.instantMixService shouldBe null
		client.displayPreferencesService shouldBe null
		client.mediaInfoService shouldBe null
	}

	test("configure with valid baseUrl initializes all services") {
		val client = createClient()
		client.configure("http://emby.local:8096", "token", "user")

		client.itemsService shouldNotBe null
		client.userLibraryService shouldNotBe null
		client.tvShowsService shouldNotBe null
		client.libraryService shouldNotBe null
		client.playstateService shouldNotBe null
		client.userViewsService shouldNotBe null
		client.liveTvService shouldNotBe null
		client.instantMixService shouldNotBe null
		client.displayPreferencesService shouldNotBe null
		client.mediaInfoService shouldNotBe null
	}

	test("reset clears configuration") {
		val client = createClient()
		client.configure("http://emby.local:8096", "token", "user")
		client.isConfigured shouldBe true

		client.reset()
		client.baseUrl shouldBe ""
		client.accessToken shouldBe null
		client.userId shouldBe null
		client.isConfigured shouldBe false
	}

	test("buildAuthHeader includes client info") {
		val client = createClient()
		val header = client.buildAuthHeader()

		header shouldContain "Client=\"TestClient\""
		header shouldContain "Device=\"TestDevice\""
		header shouldContain "DeviceId=\"test-device-id\""
		header shouldContain "Version=\"1.0.0\""
	}

	test("buildAuthHeader without token omits Token field") {
		val client = createClient()
		val header = client.buildAuthHeader()
		header shouldNotContain "Token="
	}

	test("buildAuthHeader with stored accessToken includes it") {
		val client = createClient()
		client.configure("http://emby.local:8096", "my-access-token", "user")
		val header = client.buildAuthHeader()
		header shouldContain "Token=\"my-access-token\""
	}

	test("buildAuthHeader with explicit token overrides stored token") {
		val client = createClient()
		client.configure("http://emby.local:8096", "stored-token", "user")
		val header = client.buildAuthHeader("explicit-token")
		header shouldContain "Token=\"explicit-token\""
		header shouldNotContain "stored-token"
	}

	test("buildAuthHeader format matches Emby convention") {
		val client = createClient()
		val header = client.buildAuthHeader()
		header shouldContain "Emby Client="
	}

	test("reconfigure overwrites previous configuration") {
		val client = createClient()
		client.configure("http://first.local", "token1", "user1")
		client.baseUrl shouldBe "http://first.local"

		client.configure("http://second.local", "token2", "user2")
		client.baseUrl shouldBe "http://second.local"
		client.accessToken shouldBe "token2"
		client.userId shouldBe "user2"
	}

	test("deviceId is exposed") {
		val client = createClient()
		client.deviceId shouldBe "test-device-id"
	}

	test("validateToken returns false when not configured") {
		val client = createClient()
		client.isConfigured shouldBe false
		client.validateToken() shouldBe false
	}
})
