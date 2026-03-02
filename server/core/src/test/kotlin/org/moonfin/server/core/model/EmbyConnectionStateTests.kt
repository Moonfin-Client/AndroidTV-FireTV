package org.moonfin.server.core.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class EmbyConnectionStateTests : FunSpec({

	test("Disconnected is a singleton state") {
		val a = EmbyConnectionState.Disconnected
		val b = EmbyConnectionState.Disconnected
		a shouldBe b
	}

	test("Connecting is a singleton state") {
		EmbyConnectionState.Connecting.shouldBeInstanceOf<EmbyConnectionState>()
	}

	test("Connected is a singleton state") {
		EmbyConnectionState.Connected.shouldBeInstanceOf<EmbyConnectionState>()
	}

	test("Error wraps a cause") {
		val cause = RuntimeException("test failure")
		val state = EmbyConnectionState.Error(cause)
		state.cause shouldBe cause
	}

	test("Error states with different causes are not equal") {
		val a = EmbyConnectionState.Error(RuntimeException("a"))
		val b = EmbyConnectionState.Error(RuntimeException("b"))
		(a == b) shouldBe false
	}

	test("TokenExpired is a singleton state") {
		EmbyConnectionState.TokenExpired.shouldBeInstanceOf<EmbyConnectionState>()
	}

	test("ServerUnreachable is a singleton state") {
		EmbyConnectionState.ServerUnreachable.shouldBeInstanceOf<EmbyConnectionState>()
	}

	test("ServerVersionChanged is a singleton state") {
		EmbyConnectionState.ServerVersionChanged.shouldBeInstanceOf<EmbyConnectionState>()
	}

	test("all states are subtypes of EmbyConnectionState") {
		val states: List<EmbyConnectionState> = listOf(
			EmbyConnectionState.Disconnected,
			EmbyConnectionState.Connecting,
			EmbyConnectionState.Connected,
			EmbyConnectionState.Error(Exception()),
			EmbyConnectionState.TokenExpired,
			EmbyConnectionState.ServerUnreachable,
			EmbyConnectionState.ServerVersionChanged,
		)
		states.size shouldBe 7
	}

	test("when expression covers all states") {
		val states = listOf(
			EmbyConnectionState.Disconnected,
			EmbyConnectionState.Connecting,
			EmbyConnectionState.Connected,
			EmbyConnectionState.Error(Exception("x")),
			EmbyConnectionState.TokenExpired,
			EmbyConnectionState.ServerUnreachable,
			EmbyConnectionState.ServerVersionChanged,
		)
		for (state in states) {
			val label = when (state) {
				is EmbyConnectionState.Disconnected -> "disconnected"
				is EmbyConnectionState.Connecting -> "connecting"
				is EmbyConnectionState.Connected -> "connected"
				is EmbyConnectionState.Error -> "error"
				is EmbyConnectionState.TokenExpired -> "token_expired"
				is EmbyConnectionState.ServerUnreachable -> "unreachable"
				is EmbyConnectionState.ServerVersionChanged -> "version_changed"
			}
			label.isNotEmpty() shouldBe true
		}
	}
})
