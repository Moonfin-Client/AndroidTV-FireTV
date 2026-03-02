package org.jellyfin.androidtv.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.moonfin.server.emby.EmbyApiException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorHandlerTests : FunSpec({

	test("UnknownHostException yields network message") {
		val msg = ErrorHandler.getUserFriendlyMessage(UnknownHostException("host"))
		msg shouldContain "network"
	}

	test("SocketTimeoutException yields timeout message") {
		val msg = ErrorHandler.getUserFriendlyMessage(SocketTimeoutException("timeout"))
		msg shouldContain "timed out"
	}

	test("IOException yields network error message") {
		val msg = ErrorHandler.getUserFriendlyMessage(IOException("broken pipe"))
		msg shouldContain "Network error"
	}

	test("generic 401 in message yields auth message") {
		val msg = ErrorHandler.getUserFriendlyMessage(RuntimeException("401 Unauthorized"))
		msg shouldContain "sign in"
	}

	test("generic 403 in message yields permission message") {
		val msg = ErrorHandler.getUserFriendlyMessage(RuntimeException("Forbidden"))
		msg shouldContain "Permission denied"
	}

	test("generic 404 in message yields not found message") {
		val msg = ErrorHandler.getUserFriendlyMessage(RuntimeException("Not Found"))
		msg shouldContain "not found"
	}

	test("generic 500 yields server error message") {
		val msg = ErrorHandler.getUserFriendlyMessage(RuntimeException("Internal Server Error"))
		msg shouldContain "Server error"
	}

	test("generic 503 yields unavailable message") {
		val msg = ErrorHandler.getUserFriendlyMessage(RuntimeException("Service Unavailable"))
		msg shouldContain "unavailable"
	}

	test("unknown error with context includes context") {
		val msg = ErrorHandler.getUserFriendlyMessage(RuntimeException("weird"), "load items")
		msg shouldContain "load items"
	}

	test("unknown error without context returns raw message") {
		val msg = ErrorHandler.getUserFriendlyMessage(RuntimeException("raw error"))
		msg shouldBe "raw error"
	}

	test("EmbyApiException 401 yields auth message") {
		val msg = ErrorHandler.getUserFriendlyMessage(EmbyApiException(401, "Unauthorized"))
		msg shouldContain "sign in"
	}

	test("EmbyApiException 403 yields permission message") {
		val msg = ErrorHandler.getUserFriendlyMessage(EmbyApiException(403, "Forbidden"))
		msg shouldContain "Permission denied"
	}

	test("EmbyApiException 404 yields not found message") {
		val msg = ErrorHandler.getUserFriendlyMessage(EmbyApiException(404, "Not found"))
		msg shouldContain "not found"
	}

	test("EmbyApiException 429 yields rate limit message") {
		val msg = ErrorHandler.getUserFriendlyMessage(EmbyApiException(429, "Rate limited"))
		msg shouldContain "many requests"
	}

	test("EmbyApiException 500 yields Emby server error") {
		val msg = ErrorHandler.getUserFriendlyMessage(EmbyApiException(500, "Error"))
		msg shouldContain "Emby server error"
	}

	test("EmbyApiException 502 yields bad gateway") {
		val msg = ErrorHandler.getUserFriendlyMessage(EmbyApiException(502, "Error"))
		msg shouldContain "bad gateway"
	}

	test("EmbyApiException 503 yields unavailable") {
		val msg = ErrorHandler.getUserFriendlyMessage(EmbyApiException(503, "Error"))
		msg shouldContain "unavailable"
	}

	test("EmbyApiException 400 includes original message") {
		val msg = ErrorHandler.getUserFriendlyMessage(EmbyApiException(400, "Missing field"))
		msg shouldContain "Missing field"
	}

	test("EmbyApiException 405 yields not supported") {
		val msg = ErrorHandler.getUserFriendlyMessage(EmbyApiException(405, "Error"))
		msg shouldContain "not supported"
	}

	test("EmbyApiException 409 yields conflict") {
		val msg = ErrorHandler.getUserFriendlyMessage(EmbyApiException(409, "Error"))
		msg shouldContain "Conflict"
	}

	test("EmbyApiException unknown code with context") {
		val msg = ErrorHandler.getUserFriendlyMessage(EmbyApiException(418, "Teapot"), "brew coffee")
		msg shouldContain "brew coffee"
		msg shouldContain "418"
	}

	test("EmbyApiException unknown code without context") {
		val msg = ErrorHandler.getUserFriendlyMessage(EmbyApiException(418, "Teapot"))
		msg shouldContain "418"
	}

	test("handle logs and returns message") {
		val msg = ErrorHandler.handle(IOException("fail"), "load data")
		msg shouldContain "Network error"
	}

	test("handleWarning logs and returns message") {
		val msg = ErrorHandler.handleWarning(UnknownHostException("host"), "fetch")
		msg shouldContain "network"
	}

	test("catching returns success on no error") {
		val result = ErrorHandler.catching("test") { 42 }
		result.isSuccess shouldBe true
		result.getOrNull() shouldBe 42
	}

	test("catching returns failure on error") {
		val result = ErrorHandler.catching("test") { error("boom") }
		result.isFailure shouldBe true
	}

	test("catchingWarning returns success on no error") {
		val result = ErrorHandler.catchingWarning("test") { "ok" }
		result.isSuccess shouldBe true
		result.getOrNull() shouldBe "ok"
	}

	test("catchingWarning returns failure on error") {
		val result = ErrorHandler.catchingWarning("test") { error("boom") }
		result.isFailure shouldBe true
	}

	test("Result handleError returns null on success") {
		val result: Result<Int> = Result.success(1)
		result.handleError("test") shouldBe null
	}

	test("Result handleError returns message on failure") {
		val result: Result<Int> = Result.failure(IOException("fail"))
		val msg = result.handleError("test")
		msg shouldContain "Network error"
	}

	test("Result handleWarning returns null on success") {
		val result: Result<Int> = Result.success(1)
		result.handleWarning("test") shouldBe null
	}

	test("Result handleWarning returns message on failure") {
		val result: Result<Int> = Result.failure(SocketTimeoutException("timeout"))
		val msg = result.handleWarning("test")
		msg shouldContain "timed out"
	}
})
