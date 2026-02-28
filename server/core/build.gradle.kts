plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlin.serialization)
}

android {
	namespace = "org.moonfin.server.core"
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
	api(libs.kotlinx.coroutines)
	api(libs.kotlinx.serialization.json)
	api(libs.koin.core)

	coreLibraryDesugaring(libs.android.desugar)
}
