plugins {
	alias(libs.plugins.android.library)
}

android {
	namespace = "org.moonfin.playback.emby"
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
	api(projects.playback.core)
	api(projects.server.emby)

	implementation(libs.kotlinx.coroutines)
	implementation(libs.timber)
	coreLibraryDesugaring(libs.android.desugar)

	testImplementation(libs.kotest.runner.junit5)
	testImplementation(libs.kotest.assertions)
	testImplementation(libs.mockk)
}
