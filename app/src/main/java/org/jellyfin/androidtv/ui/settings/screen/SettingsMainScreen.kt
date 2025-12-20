package org.jellyfin.androidtv.ui.settings.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.UpdateCheckerService
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.preference.PreferencesActivity
import org.jellyfin.androidtv.ui.preference.category.showDonateDialog
import org.jellyfin.androidtv.ui.preference.screen.JellyseerrPreferencesScreen
import org.jellyfin.androidtv.ui.preference.screen.MoonfinPreferencesScreen
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

@Composable
fun SettingsMainScreen() {
	val context = LocalContext.current
	val router = LocalRouter.current
	val updateChecker by inject<UpdateCheckerService>(UpdateCheckerService::class.java)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.app_name).uppercase()) },
				headingContent = { Text(stringResource(R.string.settings)) },
				captionContent = { Text(stringResource(R.string.settings_description)) },
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_users), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_login)) },
				onClick = { router.push(Routes.AUTHENTICATION) }
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_adjust), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_customization)) },
				onClick = { context.startActivity(ActivityDestinations.customizationPreferences(context)) }
			)
		}

		item {
			ListButton(
				leadingContent = {
					Icon(
						painterResource(R.drawable.ic_moonfin_white),
						contentDescription = null
					)
				},
				headingContent = { Text(stringResource(R.string.moonfin_settings)) },
				captionContent = { Text("Moonfin-specific customization") },
				onClick = {
					val intent = Intent(context, PreferencesActivity::class.java).apply {
						putExtras(
							bundleOf(
								PreferencesActivity.EXTRA_SCREEN to MoonfinPreferencesScreen::class.qualifiedName,
								PreferencesActivity.EXTRA_SCREEN_ARGS to bundleOf(),
							)
						)
					}
					context.startActivity(intent)
				}
			)
		}

		item {
			ListButton(
				leadingContent = {
					Icon(
						painterResource(R.drawable.ic_jellyseerr_jellyfish),
						contentDescription = null,
						modifier = Modifier.size(24.dp)
					)
				},
				headingContent = { Text(stringResource(R.string.jellyseerr)) },
				captionContent = { Text("Jellyseerr integration settings") },
				onClick = {
					val intent = Intent(context, PreferencesActivity::class.java).apply {
						putExtras(
							bundleOf(
								PreferencesActivity.EXTRA_SCREEN to JellyseerrPreferencesScreen::class.qualifiedName,
								PreferencesActivity.EXTRA_SCREEN_ARGS to bundleOf(),
							)
						)
					}
					context.startActivity(intent)
				}
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_next), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_playback)) },
				onClick = { router.push(Routes.PLAYBACK) }
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_error), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_telemetry_category)) },
				onClick = { router.push(Routes.TELEMETRY) }
			)

		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_flask), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_developer_link)) },
				onClick = { router.push(Routes.DEVELOPER) }
			)
		}

		item {
			ListSection(
				headingContent = { Text("Support & Updates") },
			)
		}

		item {
			ListButton(
				leadingContent = {
					Icon(
						painterResource(R.drawable.ic_get_app),
						contentDescription = null
					)
				},
				headingContent = { Text("Check for Updates") },
				captionContent = { Text("Download latest Moonfin version") },
				onClick = {
					checkForUpdates(context, updateChecker)
				}
			)
		}

		item {
			ListButton(
				leadingContent = {
					Icon(
						painterResource(R.drawable.ic_heart),
						contentDescription = null,
						tint = Color.Red
					)
				},
				headingContent = { Text("Support Moonfin") },
				captionContent = { Text("Help us continue development") },
				onClick = {
					showDonateDialog(context)
				}
			)
		}

		settingsAboutItems(
			openLicenses = { router.push(Routes.LICENSES) }
		)
	}
}

private fun checkForUpdates(context: Context, updateChecker: UpdateCheckerService) {
	GlobalScope.launch(Dispatchers.Main) {
		Toast.makeText(context, "Checking for updates…", Toast.LENGTH_SHORT).show()

		try {
			val result = updateChecker.checkForUpdate()
			result.fold(
				onSuccess = { updateInfo ->
					if (updateInfo == null) {
						Toast.makeText(context, "Failed to check for updates", Toast.LENGTH_LONG).show()
					} else if (!updateInfo.isNewer) {
						Toast.makeText(context, "No updates available", Toast.LENGTH_LONG).show()
					} else {
						showUpdateAvailableDialog(context, updateChecker, updateInfo)
					}
				},
				onFailure = { error ->
					Timber.e(error, "Failed to check for updates")
					Toast.makeText(context, "Failed to check for updates", Toast.LENGTH_LONG).show()
				}
			)
		} catch (e: Exception) {
			Timber.e(e, "Error checking for updates")
			Toast.makeText(context, "Error checking for updates", Toast.LENGTH_LONG).show()
		}
	}
}

private fun showUpdateAvailableDialog(
	context: Context,
	updateChecker: UpdateCheckerService,
	updateInfo: UpdateCheckerService.UpdateInfo
) {
	val sizeMB = updateInfo.apkSize / (1024.0 * 1024.0)
	val message = "New version ${updateInfo.version} is available!\n\nSize: ${String.format("%.1f", sizeMB)} MB"

	androidx.appcompat.app.AlertDialog.Builder(context)
		.setTitle("Update Available")
		.setMessage(message)
		.setPositiveButton("Download") { _, _ ->
			downloadAndInstall(context, updateChecker, updateInfo)
		}
		.setNegativeButton("Later", null)
		.setNeutralButton("Release Notes") { _, _ ->
			showReleaseNotesDialog(context, updateChecker, updateInfo)
		}
		.show()
}

private fun showReleaseNotesDialog(
	context: Context,
	updateChecker: UpdateCheckerService,
	updateInfo: UpdateCheckerService.UpdateInfo
) {
	val sizeMB = updateInfo.apkSize / (1024.0 * 1024.0)

	// Create WebView for HTML content
	val webView = WebView(context).apply {
		layoutParams = LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT,
			(context.resources.displayMetrics.heightPixels * 0.85).toInt()
		)
		settings.apply {
			javaScriptEnabled = false
			defaultTextEncodingName = "utf-8"
		}

		// Convert markdown to HTML with dark theme styling
		val htmlContent = buildString {
			append("<!DOCTYPE html><html><head>")
			append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
			append("<style>")
			append("body { font-family: sans-serif; padding: 16px; background-color: #1a1a1a; color: #e0e0e0; margin: 0; }")
			append("h1, h2, h3 { color: #ffffff; margin-top: 16px; margin-bottom: 8px; }")
			append("h1 { font-size: 1.5em; }")
			append("h2 { font-size: 1.3em; }")
			append("h3 { font-size: 1.1em; }")
			append("p { margin: 8px 0; line-height: 1.5; }")
			append("ul, ol { margin: 8px 0; padding-left: 24px; line-height: 1.6; }")
			append("li { margin: 4px 0; }")
			append("code { background-color: #2d2d2d; padding: 2px 6px; border-radius: 3px; font-family: monospace; color: #f0f0f0; }")
			append("pre { background-color: #2d2d2d; padding: 12px; border-radius: 4px; overflow-x: auto; }")
			append("pre code { background-color: transparent; padding: 0; }")
			append("a { color: #64b5f6; text-decoration: none; }")
			append("blockquote { border-left: 3px solid #64b5f6; margin: 8px 0; padding-left: 12px; color: #b0b0b0; }")
			append("strong { color: #ffffff; }")
			append("hr { border: none; border-top: 1px solid #404040; margin: 16px 0; }")
			append("</style></head><body>")
			append("<h2>Version ${updateInfo.version}</h2>")
			append("<p><strong>Size:</strong> ${String.format("%.1f", sizeMB)} MB</p>")
			append("<hr>")

			// Convert basic markdown to HTML
			val releaseNotes = updateInfo.releaseNotes
				.replace("### ", "<h3>")
				.replace("## ", "<h2>")
				.replace("# ", "<h1>")
				.replace(Regex("(?<!<h[1-3]>)(.+)"), "$1</p>")
				.replace(Regex("<h([1-3])>(.+?)</p>"), "<h$1>$2</h$1>")
				.replace(Regex("^- (.+)"), "<li>$1</li>")
				.replace(Regex("((?:<li>.*</li>\n?)+)"), "<ul>$1</ul>")
				.replace(Regex("^\\* (.+)"), "<li>$1</li>")
				.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
				.replace(Regex("`(.+?)`"), "<code>$1</code>")
				.replace("\n\n", "</p><p>")
				.replace(Regex("^(?!<[uh]|<li|<p)(.+)"), "<p>$1")

			append(releaseNotes)
			append("</body></html>")
		}

		loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
	}

	// Create container with padding
	val container = LinearLayout(context).apply {
		orientation = LinearLayout.VERTICAL
		setPadding(48, 24, 48, 24)
		addView(webView)
	}

	androidx.appcompat.app.AlertDialog.Builder(context)
		.setTitle("Update Available")
		.setView(container)
		.setPositiveButton("Download") { _, _ ->
			downloadAndInstall(context, updateChecker, updateInfo)
		}
		.setNegativeButton("Later", null)
		.setNeutralButton("View on GitHub") { _, _ ->
			openUrl(context, updateInfo.releaseUrl)
		}
		.show()
		.apply {
			// Make dialog wider
			window?.setLayout(
				(context.resources.displayMetrics.widthPixels * 0.90).toInt(),
				ViewGroup.LayoutParams.WRAP_CONTENT
			)
		}
}

private fun downloadAndInstall(
	context: Context,
	updateChecker: UpdateCheckerService,
	updateInfo: UpdateCheckerService.UpdateInfo
) {
	// Check for install permission on Android 8.0+
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
		if (!context.packageManager.canRequestPackageInstalls()) {
			// Show dialog to take user to settings
			androidx.appcompat.app.AlertDialog.Builder(context)
				.setTitle("Install Permission Required")
				.setMessage("This app needs permission to install updates. Please grant it in the settings.")
				.setPositiveButton("Open Settings") { _, _ ->
					openInstallPermissionSettings(context)
				}
				.setNegativeButton("Cancel", null)
				.show()
			return
		}
	}

	GlobalScope.launch(Dispatchers.Main) {
		Toast.makeText(context, "Downloading update…", Toast.LENGTH_SHORT).show()

		try {
			val result = updateChecker.downloadUpdate(updateInfo.downloadUrl) { progress ->
				Timber.d("Download progress: $progress%")
			}

			result.fold(
				onSuccess = { apkUri ->
					Toast.makeText(context, "Update downloaded", Toast.LENGTH_SHORT).show()
					updateChecker.installUpdate(apkUri)
				},
				onFailure = { error ->
					Timber.e(error, "Failed to download update")
					Toast.makeText(context, "Failed to download update", Toast.LENGTH_LONG).show()
				}
			)
		} catch (e: Exception) {
			Timber.e(e, "Error downloading update")
			Toast.makeText(context, "Error downloading update", Toast.LENGTH_LONG).show()
		}
	}
}

private fun openUrl(context: Context, url: String) {
	try {
		val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
		context.startActivity(intent)
	} catch (e: Exception) {
		Timber.e(e, "Failed to open URL")
		Toast.makeText(context, "Failed to open URL", Toast.LENGTH_LONG).show()
	}
}

private fun openInstallPermissionSettings(context: Context) {
	try {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
				data = Uri.parse("package:${context.packageName}")
			}
			context.startActivity(intent)
		}
	} catch (e: Exception) {
		Timber.e(e, "Failed to open install permission settings")
		Toast.makeText(context, "Failed to open settings", Toast.LENGTH_LONG).show()
	}
}
