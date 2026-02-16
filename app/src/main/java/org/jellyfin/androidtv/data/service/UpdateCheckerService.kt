package org.jellyfin.androidtv.data.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.androidtv.BuildConfig
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Service for checking GitHub releases and downloading updates
 */
class UpdateCheckerService(private val context: Context) {
	private val httpClient = OkHttpClient.Builder()
		.connectTimeout(30, TimeUnit.SECONDS)
		.readTimeout(30, TimeUnit.SECONDS)
		.build()

	private val json = Json {
		ignoreUnknownKeys = true
		isLenient = true
	}

	companion object {
		private const val GITHUB_OWNER = "Moonfin-Client"
		private const val GITHUB_REPO = "AndroidTV-FireTV"
		private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
		private const val PLUGIN_UPDATE_PATH = "/Moonfin/ClientUpdate"
	}

	/** Cached result from the last plugin update check (populated on startup sync). */
	@Volatile
	var latestPluginUpdateInfo: UpdateInfo? = null
		private set

	@Serializable
	data class GitHubRelease(
		@SerialName("tag_name") val tagName: String,
		@SerialName("name") val name: String,
		@SerialName("body") val body: String?,
		@SerialName("html_url") val htmlUrl: String,
		@SerialName("assets") val assets: List<GitHubAsset>,
		@SerialName("published_at") val publishedAt: String,
	)

	@Serializable
	data class GitHubAsset(
		@SerialName("name") val name: String,
		@SerialName("browser_download_url") val downloadUrl: String,
		@SerialName("size") val size: Long,
	)

	/**
	 * Response from the Moonfin plugin's `/Moonfin/ClientUpdate` endpoint.
	 */
	@Serializable
	data class PluginUpdateResponse(
		val version: String? = null,
		val downloadUrl: String? = null,
		val releaseNotes: String? = null,
		val releaseUrl: String? = null,
		val apkSize: Long? = null,
	)

	data class UpdateInfo(
		val version: String,
		val releaseNotes: String,
		val downloadUrl: String,
		val releaseUrl: String,
		val isNewer: Boolean,
		val apkSize: Long,
	)

	/**
	 * Check if an update is available via the Moonfin server plugin.
	 * Returns `null` if the plugin endpoint is unavailable.
	 */
	suspend fun checkForUpdateViaPlugin(
		baseUrl: String,
		accessToken: String,
	): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
		runCatching {
			val url = "$baseUrl$PLUGIN_UPDATE_PATH"
			Timber.d("Checking for update via plugin: $url")

			val request = Request.Builder()
				.url(url)
				.addHeader("Authorization", "MediaBrowser Token=\"$accessToken\"")
				.build()

			httpClient.newCall(request).execute().use { response ->
				if (!response.isSuccessful) {
					Timber.d("Plugin update endpoint not available: ${response.code}")
					return@runCatching null
				}

				val body = response.body?.string() ?: return@runCatching null
				val pluginResponse = json.decodeFromString<PluginUpdateResponse>(body)

				val latestVersion = pluginResponse.version ?: return@runCatching null
				val currentVersion = BuildConfig.VERSION_NAME
				val isNewer = compareVersions(latestVersion, currentVersion) > 0

				Timber.d("Plugin update check — Current: $currentVersion, Latest: $latestVersion, Newer: $isNewer")

				UpdateInfo(
					version = latestVersion,
					releaseNotes = pluginResponse.releaseNotes ?: "No release notes available",
					downloadUrl = pluginResponse.downloadUrl ?: "",
					releaseUrl = pluginResponse.releaseUrl ?: "",
					isNewer = isNewer,
					apkSize = pluginResponse.apkSize ?: 0L,
				).also { latestPluginUpdateInfo = it }
			}
		}
	}

	/**
	 * Check if an update is available via GitHub releases
	 */
	suspend fun checkForUpdate(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
		runCatching {
			val request = Request.Builder()
				.url(GITHUB_API_URL)
				.addHeader("Accept", "application/vnd.github.v3+json")
				.build()

			httpClient.newCall(request).execute().use { response ->
				if (!response.isSuccessful) {
					Timber.e("Failed to check for updates: ${response.code}")
					return@runCatching null
				}

				val body = response.body?.string() ?: return@runCatching null
				val release = json.decodeFromString<GitHubRelease>(body)

				// Find the APK asset
				val apkAsset = release.assets.firstOrNull { asset ->
					asset.name.endsWith(".apk", ignoreCase = true) &&
						(asset.name.contains("debug", ignoreCase = true) ||
							asset.name.contains("release", ignoreCase = true))
				}

				if (apkAsset == null) {
					Timber.w("No APK found in release")
					return@runCatching null
				}

				// Compare versions
				val currentVersion = BuildConfig.VERSION_NAME
				val latestVersion = release.tagName.removePrefix("v")
				val isNewer = compareVersions(latestVersion, currentVersion) > 0

				Timber.d("Current version: $currentVersion, Latest version: $latestVersion, Is newer: $isNewer")

				UpdateInfo(
					version = latestVersion,
					releaseNotes = release.body ?: "No release notes available",
					downloadUrl = apkAsset.downloadUrl,
					releaseUrl = release.htmlUrl,
					isNewer = isNewer,
					apkSize = apkAsset.size,
				)
			}
		}
	}

	/**
	 * Download the APK update
	 * @param downloadUrl The URL to download from
	 * @param onProgress Callback for download progress (0-100)
	 * @return The file URI of the downloaded APK
	 */
	suspend fun downloadUpdate(
		downloadUrl: String,
		onProgress: (Int) -> Unit = {}
	): Result<Uri> = withContext(Dispatchers.IO) {
		runCatching {
			val request = Request.Builder()
				.url(downloadUrl)
				.build()

			httpClient.newCall(request).execute().use { response ->
				if (!response.isSuccessful) {
					throw Exception("Failed to download update: ${response.code}")
				}

				val body = response.body ?: throw Exception("Empty response body")
				val contentLength = body.contentLength()

				// Create downloads directory
				val downloadsDir = File(context.getExternalFilesDir(null), "downloads")
				downloadsDir.mkdirs()

				// Create APK file
				val apkFile = File(downloadsDir, "update.apk")
				if (apkFile.exists()) {
					apkFile.delete()
				}

				// Download with progress
				FileOutputStream(apkFile).use { output ->
					val buffer = ByteArray(8192)
					var bytesRead: Int
					var totalBytesRead = 0L

					body.byteStream().use { input ->
						while (input.read(buffer).also { bytesRead = it } != -1) {
							output.write(buffer, 0, bytesRead)
							totalBytesRead += bytesRead

							if (contentLength > 0) {
								val progress = (totalBytesRead * 100 / contentLength).toInt()
								withContext(Dispatchers.Main) {
									onProgress(progress)
								}
							}
						}
					}
				}

				Timber.d("Update downloaded to: ${apkFile.absolutePath}")

				// Return FileProvider URI
				FileProvider.getUriForFile(
					context,
					"${context.packageName}.fileprovider",
					apkFile
				)
			}
		}
	}

	/**
	 * Install the downloaded APK
	 */
	fun installUpdate(apkUri: Uri) {
		val intent = Intent(Intent.ACTION_VIEW).apply {
			setDataAndType(apkUri, "application/vnd.android.package-archive")
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		context.startActivity(intent)
	}

	/**
	 * Compare two semantic version strings
	 * Returns: negative if v1 < v2, zero if v1 == v2, positive if v1 > v2
	 */
	private fun compareVersions(v1: String, v2: String): Int {
		val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
		val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

		val maxLength = maxOf(parts1.size, parts2.size)
		for (i in 0 until maxLength) {
			val p1 = parts1.getOrNull(i) ?: 0
			val p2 = parts2.getOrNull(i) ?: 0
			if (p1 != p2) return p1 - p2
		}
		return 0
	}
}
