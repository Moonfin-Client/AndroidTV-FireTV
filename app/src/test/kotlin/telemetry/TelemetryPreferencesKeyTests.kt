package org.jellyfin.androidtv.telemetry

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jellyfin.androidtv.preference.TelemetryPreferences

class TelemetryPreferencesKeyTests : FunSpec({

	test("serverType preference key") {
		TelemetryPreferences.serverType.key shouldBe "server_type"
	}

	test("serverVersion preference key") {
		TelemetryPreferences.serverVersion.key shouldBe "server_version"
	}

	test("crashReportToken preference key") {
		TelemetryPreferences.crashReportToken.key shouldBe "server_token"
	}

	test("crashReportUrl preference key") {
		TelemetryPreferences.crashReportUrl.key shouldBe "server_url"
	}

	test("shared preferences name") {
		TelemetryPreferences.SHARED_PREFERENCES_NAME shouldBe "telemetry"
	}
})
