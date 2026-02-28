plugins {
	alias(libs.plugins.android.library)
}

android {
	namespace = "org.moonfin.server.jellyfin"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
	}

	compileOptions {
		isCoreLibraryDesugaringEnabled = true
	}

	lint {
		lintConfig = file("$rootDir/android-lint.xml")
		abortOnError = false
	}

	testOptions.unitTests.all {
		it.useJUnitPlatform()
	}
}

dependencies {
	api(projects.server.core)

	implementation(libs.jellyfin.sdk) {
		val sdkVersion = findProperty("sdk.version")?.toString()
		when (sdkVersion) {
			"local" -> version { strictly("latest-SNAPSHOT") }
			"snapshot" -> version { strictly("master-SNAPSHOT") }
			"unstable-snapshot" -> version { strictly("openapi-unstable-SNAPSHOT") }
		}
	}

	implementation(libs.kotlinx.coroutines)
	implementation(libs.timber)
	coreLibraryDesugaring(libs.android.desugar)

	testImplementation(libs.kotest.runner.junit5)
	testImplementation(libs.kotest.assertions)
	testImplementation(libs.mockk)
}
