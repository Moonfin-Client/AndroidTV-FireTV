enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Moonfin-androidtv"

pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
		google()
	}
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

// Application
include(":app")

// Modules
include(":design")
include(":server:core")
include(":server:jellyfin")
include(":server:emby")
include(":playback:core")
include(":playback:jellyfin")
include(":playback:emby")
include(":playback:media3:exoplayer")
include(":playback:media3:session")
include(":preference")

dependencyResolutionManagement {
	repositories {
		mavenCentral()
		google()

		mavenLocal {
			content {
				includeVersionByRegex("org.jellyfin.sdk", ".*", "latest-SNAPSHOT")
				includeGroup("org.emby")
			}
		}
		maven("https://s01.oss.sonatype.org/content/repositories/snapshots/") {
			content {
				includeVersionByRegex("org.jellyfin.sdk", ".*", "master-SNAPSHOT")
				includeVersionByRegex("org.jellyfin.sdk", ".*", "openapi-unstable-SNAPSHOT")
			}
		}

		// NewPipe Extractor (YouTube stream URL resolution with n-parameter descrambling)
		maven("https://jitpack.io")
	}
}
