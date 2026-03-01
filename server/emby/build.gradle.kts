plugins {
	alias(libs.plugins.android.library)
}

android {
	namespace = "org.moonfin.server.emby"
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
	implementation("org.emby:emby-client:4.9.3.0")

	implementation(libs.ktor.client.core)
	implementation(libs.kotlinx.coroutines)
	implementation(libs.timber)
	coreLibraryDesugaring(libs.android.desugar)

	testImplementation(libs.kotest.runner.junit5)
	testImplementation(libs.kotest.assertions)
	testImplementation(libs.mockk)
}
