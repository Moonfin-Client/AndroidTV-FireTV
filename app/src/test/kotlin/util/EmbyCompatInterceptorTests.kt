package org.jellyfin.androidtv.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.moonfin.server.core.model.ServerType

class EmbyCompatInterceptorTests : FunSpec({

	test("numericToUuid pads short numbers correctly") {
		EmbyCompatInterceptor.numericToUuid("123") shouldBe
			"00000000-0000-0000-0000-000000000123"
	}

	test("numericToUuid handles zero") {
		EmbyCompatInterceptor.numericToUuid("0") shouldBe
			"00000000-0000-0000-0000-000000000000"
	}

	test("numericToUuid handles max-length number") {
		EmbyCompatInterceptor.numericToUuid("12345678901234567890123456789012") shouldBe
			"12345678-9012-3456-7890-123456789012"
	}

	test("numericToUuid handles large realistic ID") {
		val result = EmbyCompatInterceptor.numericToUuid("927")
		result shouldBe "00000000-0000-0000-0000-000000000927"
	}

	test("uuidToNumeric converts valid all-digit UUID") {
		EmbyCompatInterceptor.uuidToNumeric("00000000-0000-0000-0000-000000000123") shouldBe "123"
	}

	test("uuidToNumeric returns 0 for all-zeros UUID") {
		EmbyCompatInterceptor.uuidToNumeric("00000000-0000-0000-0000-000000000000") shouldBe "0"
	}

	test("uuidToNumeric returns null for real UUID with hex chars") {
		EmbyCompatInterceptor.uuidToNumeric("550e8400-e29b-41d4-a716-446655440000") shouldBe null
	}

	test("uuidToNumeric returns null for wrong-length string") {
		EmbyCompatInterceptor.uuidToNumeric("short") shouldBe null
		EmbyCompatInterceptor.uuidToNumeric("") shouldBe null
	}

	test("uuidToNumeric returns null for 36-char string without valid format") {
		EmbyCompatInterceptor.uuidToNumeric("abcdefgh-ijkl-mnop-qrst-uvwxyz012345") shouldBe null
	}

	test("roundtrip numericToUuid -> uuidToNumeric") {
		val original = "42"
		val uuid = EmbyCompatInterceptor.numericToUuid(original)
		EmbyCompatInterceptor.uuidToNumeric(uuid) shouldBe original
	}

	test("roundtrip for zero") {
		val uuid = EmbyCompatInterceptor.numericToUuid("0")
		EmbyCompatInterceptor.uuidToNumeric(uuid) shouldBe "0"
	}

	test("roundtrip for large number") {
		val original = "98765432101234"
		val uuid = EmbyCompatInterceptor.numericToUuid(original)
		EmbyCompatInterceptor.uuidToNumeric(uuid) shouldBe original
	}

	test("uuidToNumeric handles numeric-only UUID segments") {
		val uuid = "00000000-0000-0000-0000-000000012345"
		EmbyCompatInterceptor.uuidToNumeric(uuid) shouldBe "12345"
	}

	test("numericToUuid produces valid UUID format") {
		val uuid = EmbyCompatInterceptor.numericToUuid("999")
		uuid.length shouldBe 36
		uuid.count { it == '-' } shouldBe 4
		uuid.split("-").map { it.length } shouldBe listOf(8, 4, 4, 4, 12)
	}

	test("uuidToNumeric rejects UUID with letters") {
		EmbyCompatInterceptor.uuidToNumeric("0000000a-0000-0000-0000-000000000001") shouldBe null
	}

	test("setOnTokenExpired stores callback") {
		val interceptor = EmbyCompatInterceptor()
		var called = false
		interceptor.setOnTokenExpired { called = true }
		called shouldBe false
	}

	test("setOnTokenExpired with null clears callback") {
		val interceptor = EmbyCompatInterceptor()
		interceptor.setOnTokenExpired { }
		interceptor.setOnTokenExpired(null)
	}
})
