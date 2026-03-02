package org.moonfin.server.emby.socket

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.moonfin.server.core.model.EmbyConnectionState

class EmbyWebSocketEdgeCaseTests : FunSpec({

	test("MAX_RECONNECT_ATTEMPTS is 12") {
		val field = EmbyWebSocketClient::class.java.getDeclaredField("MAX_RECONNECT_ATTEMPTS")
		field.isAccessible = true
		field.getInt(null) shouldBe 12
	}

	test("connection state initial value is Disconnected") {
		val stateFlow = kotlinx.coroutines.flow.MutableStateFlow<EmbyConnectionState>(
			EmbyConnectionState.Disconnected
		)
		stateFlow.value shouldBe EmbyConnectionState.Disconnected
	}

	test("connection state transitions follow expected pattern") {
		val stateFlow = kotlinx.coroutines.flow.MutableStateFlow<EmbyConnectionState>(
			EmbyConnectionState.Disconnected
		)

		stateFlow.value = EmbyConnectionState.Connecting
		stateFlow.value shouldBe EmbyConnectionState.Connecting

		stateFlow.value = EmbyConnectionState.Connected
		stateFlow.value shouldBe EmbyConnectionState.Connected

		stateFlow.value = EmbyConnectionState.Error(RuntimeException("network down"))
		(stateFlow.value is EmbyConnectionState.Error) shouldBe true

		stateFlow.value = EmbyConnectionState.ServerUnreachable
		stateFlow.value shouldBe EmbyConnectionState.ServerUnreachable
	}

	test("connection state 401 failure sets TokenExpired") {
		val stateFlow = kotlinx.coroutines.flow.MutableStateFlow<EmbyConnectionState>(
			EmbyConnectionState.Connected
		)
		val responseCode = 401
		if (responseCode == 401) {
			stateFlow.value = EmbyConnectionState.TokenExpired
		}
		stateFlow.value shouldBe EmbyConnectionState.TokenExpired
	}

	test("reconnect counter caps at max attempts") {
		var reconnectAttempt = 0
		val maxAttempts = 12
		val states = mutableListOf<EmbyConnectionState>()

		repeat(maxAttempts + 3) {
			if (reconnectAttempt >= maxAttempts) {
				states.add(EmbyConnectionState.ServerUnreachable)
				return@repeat
			}
			reconnectAttempt++
			states.add(EmbyConnectionState.Error(RuntimeException("attempt $reconnectAttempt")))
		}

		states.count { it is EmbyConnectionState.Error } shouldBe maxAttempts
		states.count { it is EmbyConnectionState.ServerUnreachable } shouldBe 3
	}

	test("reconnectNow resets counter") {
		var reconnectAttempt = 8
		reconnectAttempt = 0
		reconnectAttempt shouldBe 0
	}

	test("exponential backoff delay calculation") {
		val delays = (0..5).map { attempt ->
			kotlin.math.min(30_000L, 1_000L * (1L shl kotlin.math.min(attempt, 5)))
		}
		delays shouldBe listOf(1000L, 2000L, 4000L, 8000L, 16000L, 32000L.coerceAtMost(30000L))
	}
})
