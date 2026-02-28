plugins {
	alias(libs.plugins.aboutlibraries)
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlin.serialization)
}

import java.util.Properties
import java.io.FileInputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

android {
	namespace = "org.jellyfin.androidtv"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()

		// Release version - custom applicationId to avoid conflict with official Jellyfin
		applicationId = "org.moonfin.androidtv"
		versionName = project.getVersionName()
		versionCode = getVersionCode(versionName!!)
	}

	buildFeatures {
		buildConfig = true
		viewBinding = true
		compose = true
		resValues = true
	}

	compileOptions {
		isCoreLibraryDesugaringEnabled = true
	}

	flavorDimensions += "distribution"

	productFlavors {
		create("github") {
			dimension = "distribution"
			buildConfigField("boolean", "ENABLE_OTA_UPDATES", "true")
		}

		create("playstore") {
			dimension = "distribution"
			buildConfigField("boolean", "ENABLE_OTA_UPDATES", "false")
		}
	}

	signingConfigs {
		create("release") {
			// Load keystore properties from keystore.properties file
			val keystorePropertiesFile = rootProject.file("keystore.properties")
			if (keystorePropertiesFile.exists()) {
				val keystoreProperties = Properties()
				keystoreProperties.load(FileInputStream(keystorePropertiesFile))
				
				storeFile = file(keystoreProperties["storeFile"] as String)
				storePassword = keystoreProperties["storePassword"] as String
				keyAlias = keystoreProperties["keyAlias"] as String
				keyPassword = keystoreProperties["keyPassword"] as String
			}
		}
	}

	buildTypes {
		release {
			// Use signing config if available
			if (signingConfigs.names.contains("release")) {
				signingConfig = signingConfigs.getByName("release")
			}

			isDebuggable = false
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

			// Set package names used in various XML files (must match applicationId for provider authorities)
			resValue("string", "app_id", defaultConfig.applicationId!!)
			resValue("string", "app_search_suggest_authority", "${defaultConfig.applicationId}.content")
			resValue("string", "app_search_suggest_intent_data", "content://${defaultConfig.applicationId}.content/intent")

			// Set flavored application name
			resValue("string", "app_name", "Moonfin")

			buildConfigField("boolean", "DEVELOPMENT", "false")
		}

		debug {
			// Use different application id to run release and debug at the same time
			applicationIdSuffix = ".debug"
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
			
			// Set package names used in various XML files (must match applicationId for provider authorities)
			val debugAppId = defaultConfig.applicationId + applicationIdSuffix
			resValue("string", "app_id", debugAppId)
			resValue("string", "app_search_suggest_authority", "${debugAppId}.content")
			resValue("string", "app_search_suggest_intent_data", "content://${debugAppId}.content/intent")

			// Set flavored application name
			resValue("string", "app_name", "Moonfin Debug")

			buildConfigField("boolean", "DEVELOPMENT", (defaultConfig.versionCode!! < 100).toString())
		}
	}

	lint {
		lintConfig = file("$rootDir/android-lint.xml")
		abortOnError = false
		sarifReport = true
		checkDependencies = true
	}

	testOptions.unitTests.all {
		it.useJUnitPlatform()
	}
}

base.archivesName.set("moonfin-androidtv-v${project.getVersionName()}")

tasks.register("versionTxt") {
	val path = layout.buildDirectory.asFile.get().resolve("version.txt")

	doLast {
		val versionString = "v${android.defaultConfig.versionName}=${android.defaultConfig.versionCode}"
		logger.info("Writing [$versionString] to $path")
		path.writeText("$versionString\n")
	}
}

// Strip Utils.class from NewPipe Extractor JAR — replaced by local shadow
// (see app/src/main/java/org/schabi/newpipe/extractor/utils/Utils.java).
val pipeExtractorJar: Configuration by configurations.creating {
	isTransitive = false
	isCanBeResolved = true
	isCanBeConsumed = false
}

val stripPipeExtractorUtils by tasks.registering {
	description = "Strips Utils.class from NewPipe Extractor JAR (replaced by local shadow)"

	val inputFiles = pipeExtractorJar
	val outputJar = layout.buildDirectory.file("stripped-libs/NewPipeExtractor-stripped.jar")

	inputs.files(inputFiles)
	outputs.file(outputJar)

	doLast {
		val output = outputJar.get().asFile
		output.parentFile.mkdirs()

		JarFile(inputFiles.singleFile).use { jar ->
			JarOutputStream(output.outputStream()).use { jos ->
				jar.entries().asSequence()
					.filter { it.name != "org/schabi/newpipe/extractor/utils/Utils.class" }
					.forEach { entry ->
						jos.putNextEntry(JarEntry(entry.name))
						if (!entry.isDirectory) {
							jar.getInputStream(entry).use { it.copyTo(jos) }
						}
						jos.closeEntry()
					}
			}
		}
	}
}

dependencies {
	implementation(projects.design)
	implementation(projects.server.core)
	implementation(projects.server.jellyfin)
	implementation(projects.server.emby)
	implementation(projects.playback.core)
	implementation(projects.playback.jellyfin)
	implementation(projects.playback.emby)
	implementation(projects.playback.media3.exoplayer)
	implementation(projects.playback.media3.session)
	implementation(projects.preference)
	implementation(libs.jellyfin.sdk) {
		// Change version if desired
		val sdkVersion = findProperty("sdk.version")?.toString()
		when (sdkVersion) {
			"local" -> version { strictly("latest-SNAPSHOT") }
			"snapshot" -> version { strictly("master-SNAPSHOT") }
			"unstable-snapshot" -> version { strictly("openapi-unstable-SNAPSHOT") }
		}
	}

	// Kotlin
	implementation(libs.kotlinx.coroutines)
	implementation(libs.kotlinx.serialization.json)

	// Ktor (HTTP client for Jellyseerr)
	implementation(libs.bundles.ktor)

	// Android(x)
	implementation(libs.androidx.core)
	implementation(libs.androidx.activity)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.fragment)
	implementation(libs.androidx.fragment.compose)
	implementation(libs.androidx.leanback.core)
	implementation(libs.androidx.leanback.preference)
	implementation(libs.androidx.navigation3.ui)
	implementation(libs.androidx.preference)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.tv.material)
	implementation(libs.androidx.tvprovider)
	implementation(libs.androidx.constraintlayout)
	implementation(libs.androidx.recyclerview)
	implementation(libs.androidx.work.runtime)
	implementation(libs.bundles.androidx.lifecycle)
	implementation(libs.androidx.window)
	implementation(libs.androidx.cardview)
	implementation(libs.androidx.startup)
	implementation(libs.bundles.androidx.compose)
	implementation(libs.accompanist.permissions)

	// Dependency Injection
	implementation(libs.bundles.koin)

	// Media players
	implementation(libs.androidx.media3.exoplayer)
	implementation(libs.androidx.media3.datasource.okhttp)
	implementation(libs.androidx.media3.exoplayer.hls)
	implementation(libs.androidx.media3.ui)
	implementation(libs.jellyfin.androidx.media3.ffmpeg.decoder)

	// Markdown
	implementation(libs.bundles.markwon)

	// Image utility
	implementation(libs.bundles.coil)

	// Crash Reporting
	implementation(libs.bundles.acra)

	// Licenses
	implementation(libs.aboutlibraries)

	// YouTube stream extraction (n-parameter descrambling)
	pipeExtractorJar(libs.pipeextractor)
	compileOnly(libs.pipeextractor)
	compileOnly(libs.findbugs.jsr305)
	runtimeOnly(files(stripPipeExtractorUtils))
	implementation(libs.pipeextractor.nanojson)
	implementation(libs.pipeextractor.jsoup)

	// Logging
	implementation(libs.timber)
	implementation(libs.slf4j.timber)

	// Compatibility (desugaring)
	coreLibraryDesugaring(libs.android.desugar)

	// Testing
	testImplementation(libs.kotest.runner.junit5)
	testImplementation(libs.kotest.assertions)
	testImplementation(libs.mockk)
}
