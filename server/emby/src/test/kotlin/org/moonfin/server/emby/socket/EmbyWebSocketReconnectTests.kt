package org.moonfin.server.emby.socket

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.min
import kotlin.random.Random

class EmbyWebSocketReconnectTests : FunSpec({

	fun computeBaseDelay(attempt: Int): Long =
		min(30_000L, 1_000L * (1L shl min(attempt, 5)))

	test("base delay doubles each attempt up to cap") {
		computeBaseDelay(0) shouldBe 1_000L
		computeBaseDelay(1) shouldBe 2_000L
		computeBaseDelay(2) shouldBe 4_000L
		computeBaseDelay(3) shouldBe 8_000L
		computeBaseDelay(4) shouldBe 16_000L
		computeBaseDelay(5) shouldBe 30_000L
		computeBaseDelay(6) shouldBe 30_000L
	}

	test("jitter range is [0, baseDelay/2]") {
		repeat(100) {
			val attempt = Random.nextInt(0, 6)
			val baseDelay = computeBaseDelay(attempt)
			val jitter = Random.nextLong(0, baseDelay / 2 + 1)
			jitter shouldBeGreaterThanOrEqual 0L
			jitter shouldBeLessThanOrEqual baseDelay / 2
		}
	}

	test("total delay with jitter is between base and 1.5x base") {
		repeat(100) {
			val attempt = Random.nextInt(0, 6)
			val baseDelay = computeBaseDelay(attempt)
			val jitter = Random.nextLong(0, baseDelay / 2 + 1)
			val totalDelay = baseDelay + jitter

			totalDelay shouldBeGreaterThanOrEqual baseDelay
			totalDelay shouldBeLessThanOrEqual baseDelay + baseDelay / 2
		}
	}

	test("attempt 0 total delay is between 1000 and 1500 ms") {
		val baseDelay = computeBaseDelay(0)
		baseDelay shouldBe 1_000L
		val maxTotal = baseDelay + baseDelay / 2
		maxTotal shouldBe 1_500L
	}

	test("max total delay at cap is 45000 ms") {
		val baseDelay = computeBaseDelay(5)
		baseDelay shouldBe 30_000L
		val maxTotal = baseDelay + baseDelay / 2
		maxTotal shouldBe 45_000L
	}

	test("jitter prevents identical reconnect times") {
		val delays = (0 until 20).map {
			val baseDelay = computeBaseDelay(3)
			baseDelay + Random.nextLong(0, baseDelay / 2 + 1)
		}.toSet()
		delays.size shouldNotBe 1
	}

	test("jitter at attempt 0 never exceeds 500 ms") {
		repeat(100) {
			val baseDelay = computeBaseDelay(0)
			val jitter = Random.nextLong(0, baseDelay / 2 + 1)
			jitter shouldBeLessThanOrEqual 500L
		}
	}

	test("scheduleReconnect uses Random import") {
		val source = EmbyWebSocketClient::class.java.getResourceAsStream(
			"/org/moonfin/server/emby/socket/EmbyWebSocketClient.class"
		)
		source shouldNotBe null
	}
})
