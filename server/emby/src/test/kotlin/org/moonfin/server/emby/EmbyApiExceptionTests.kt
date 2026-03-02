package org.moonfin.server.emby

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EmbyApiExceptionTests : FunSpec({

	test("statusCode is stored") {
		val ex = EmbyApiException(404, "Not found")
		ex.statusCode shouldBe 404
		ex.message shouldBe "Not found"
	}

	test("isAuthError for 401") {
		EmbyApiException(401, "Unauthorized").isAuthError shouldBe true
	}

	test("isAuthError for 403") {
		EmbyApiException(403, "Forbidden").isAuthError shouldBe true
	}

	test("isAuthError false for 404") {
		EmbyApiException(404, "Not found").isAuthError shouldBe false
	}

	test("isNotFound for 404") {
		EmbyApiException(404, "Not found").isNotFound shouldBe true
	}

	test("isNotFound false for 401") {
		EmbyApiException(401, "Unauthorized").isNotFound shouldBe false
	}

	test("isServerError for 500") {
		EmbyApiException(500, "Internal server error").isServerError shouldBe true
	}

	test("isServerError for 503") {
		EmbyApiException(503, "Service unavailable").isServerError shouldBe true
	}

	test("isServerError false for 400") {
		EmbyApiException(400, "Bad request").isServerError shouldBe false
	}

	test("isRateLimited for 429") {
		EmbyApiException(429, "Rate limited").isRateLimited shouldBe true
	}

	test("isRateLimited false for 500") {
		EmbyApiException(500, "Error").isRateLimited shouldBe false
	}

	test("cause is preserved") {
		val cause = RuntimeException("underlying")
		val ex = EmbyApiException(500, "Error", cause)
		ex.cause shouldBe cause
	}

	test("toString includes status and message") {
		val ex = EmbyApiException(401, "Unauthorized")
		ex.toString() shouldBe "EmbyApiException(status=401, message=Unauthorized)"
	}

	test("fromStatus creates proper exception for known codes") {
		val ex = EmbyApiException.fromStatus(404)
		ex.statusCode shouldBe 404
		ex.message shouldBe "Not found"
	}

	test("fromStatus includes context") {
		val ex = EmbyApiException.fromStatus(401, "fetching user info")
		ex.statusCode shouldBe 401
		ex.message shouldBe "Unauthorized: fetching user info"
	}

	test("fromStatus handles unknown code") {
		val ex = EmbyApiException.fromStatus(418)
		ex.statusCode shouldBe 418
		ex.message shouldBe "HTTP 418"
	}

	test("fromStatus covers all standard codes") {
		val codes = mapOf(
			400 to "Bad request",
			401 to "Unauthorized",
			403 to "Forbidden",
			404 to "Not found",
			405 to "Method not allowed",
			409 to "Conflict",
			429 to "Rate limited",
			500 to "Internal server error",
			502 to "Bad gateway",
			503 to "Service unavailable",
		)
		for ((code, expected) in codes) {
			EmbyApiException.fromStatus(code).message shouldBe expected
		}
	}

	test("exception is throwable") {
		val ex = EmbyApiException(500, "Error")
		val result = runCatching { throw ex }
		result.isFailure shouldBe true
		(result.exceptionOrNull() is EmbyApiException) shouldBe true
	}
})
