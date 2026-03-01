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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.data.service.UpdateCheckerService
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.preference.category.DonateDialog
import org.jellyfin.androidtv.ui.preference.category.GlassDialogButton
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject
import org.koin.java.KoinJavaComponent.inject
import org.moonfin.server.core.model.ServerType
import timber.log.Timber

@Composable
fun SettingsMainScreen() {
	val router = LocalRouter.current
	val context = LocalContext.current
	val updateChecker by inject<UpdateCheckerService>(UpdateCheckerService::class.java)
	val userPreferences = koinInject<UserPreferences>()

	val serverRepository = koinInject<ServerRepository>()
	val currentServer by serverRepository.currentServer.collectAsState()
	val syncPlaySupported = currentServer?.serverType != ServerType.EMBY

	var showDonateDialog by remember { mutableStateOf(false) }
	var updateInfoForDialog by remember { mutableStateOf<UpdateCheckerService.UpdateInfo?>(null) }
	var showReleaseNotes by remember { mutableStateOf(false) }

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
				onClick = { router.push(Routes.AUTHENTICATION) },
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_adjust), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_customization)) },
				onClick = { router.push(Routes.CUSTOMIZATION) }
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_moonfin), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_plugin_settings)) },
				captionContent = { Text(stringResource(R.string.pref_plugin_description)) },
				onClick = { router.push(Routes.PLUGIN) }
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_photos), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_screensaver)) },
				onClick = { router.push(Routes.CUSTOMIZATION_SCREENSAVER) }
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_next), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_playback)) },
				onClick = { router.push(Routes.PLAYBACK) }
			)
		}

		if (syncPlaySupported) item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_syncplay), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.syncplay)) },
				captionContent = { Text(stringResource(R.string.syncplay_description)) },
				onClick = { router.push(Routes.MOONFIN_SYNCPLAY) }
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

		if (org.jellyfin.androidtv.BuildConfig.ENABLE_OTA_UPDATES) {
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
						checkForUpdates(context, updateChecker) { info ->
							updateInfoForDialog = info
						}
					}
				)
			}

			item {
				var updateNotificationsEnabled by rememberPreference(userPreferences, UserPreferences.updateNotificationsEnabled)
				ListButton(
					headingContent = { Text("Update Notifications") },
					captionContent = { Text("Show notification on app launch when updates are available") },
					trailingContent = { Checkbox(checked = updateNotificationsEnabled) },
					onClick = { updateNotificationsEnabled = !updateNotificationsEnabled }
				)
			}
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
					showDonateDialog = true
				}
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_jellyfin), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_about_title)) },
				onClick = { router.push(Routes.ABOUT) }
			)
		}
	}

	// Dialogs
	if (showDonateDialog) {
		DonateDialog(onDismiss = { showDonateDialog = false })
	}

	val currentUpdateInfo = updateInfoForDialog
	if (currentUpdateInfo != null && !showReleaseNotes) {
		UpdateAvailableDialog(
			updateInfo = currentUpdateInfo,
			onDownload = {
				updateInfoForDialog = null
				downloadAndInstall(context, updateChecker, currentUpdateInfo)
			},
			onReleaseNotes = { showReleaseNotes = true },
			onDismiss = { updateInfoForDialog = null },
		)
	}

	if (currentUpdateInfo != null && showReleaseNotes) {
		ReleaseNotesDialog(
			updateInfo = currentUpdateInfo,
			onDownload = {
				showReleaseNotes = false
				updateInfoForDialog = null
				downloadAndInstall(context, updateChecker, currentUpdateInfo)
			},
			onViewOnGitHub = {
				openUrl(context, currentUpdateInfo.releaseUrl)
			},
			onDismiss = {
				showReleaseNotes = false
				updateInfoForDialog = null
			},
		)
	}
}

private fun checkForUpdates(
	context: Context,
	updateChecker: UpdateCheckerService,
	onUpdateFound: (UpdateCheckerService.UpdateInfo) -> Unit,
) {
	CoroutineScope(Dispatchers.Main).launch {
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
						onUpdateFound(updateInfo)
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

@Composable
private fun UpdateAvailableDialog(
	updateInfo: UpdateCheckerService.UpdateInfo,
	onDownload: () -> Unit,
	onReleaseNotes: () -> Unit,
	onDismiss: () -> Unit,
) {
	val downloadFocusRequester = remember { FocusRequester() }
	val sizeMB = updateInfo.apkSize / (1024.0 * 1024.0)

	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		Box(
			modifier = Modifier.fillMaxSize(),
			contentAlignment = Alignment.Center,
		) {
			Column(
				modifier = Modifier
					.widthIn(min = 340.dp, max = 460.dp)
					.clip(RoundedCornerShape(20.dp))
					.background(Color(0xE6141414))
					.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
					.padding(vertical = 20.dp),
			) {
				// Title
				Text(
					text = "Update Available",
					fontSize = 20.sp,
					fontWeight = FontWeight.W600,
					color = Color.White,
					modifier = Modifier
						.padding(horizontal = 24.dp)
						.padding(bottom = 12.dp),
				)

				// Divider
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(1.dp)
						.background(Color.White.copy(alpha = 0.08f)),
				)

				Spacer(modifier = Modifier.height(16.dp))

				// Version info
				Text(
					text = "New version ${updateInfo.version} is available!",
					fontSize = 16.sp,
					color = Color.White.copy(alpha = 0.85f),
					modifier = Modifier.padding(horizontal = 24.dp),
				)

				Spacer(modifier = Modifier.height(8.dp))

				Text(
					text = "Size: ${String.format("%.1f", sizeMB)} MB",
					fontSize = 14.sp,
					color = Color.White.copy(alpha = 0.5f),
					modifier = Modifier.padding(horizontal = 24.dp),
				)

				Spacer(modifier = Modifier.height(24.dp))

				// Divider
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(1.dp)
						.background(Color.White.copy(alpha = 0.08f)),
				)

				Spacer(modifier = Modifier.height(16.dp))

				// Buttons
				Column(
					modifier = Modifier.padding(horizontal = 24.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					GlassDialogButton(
						text = "Download",
						onClick = onDownload,
						isPrimary = true,
						modifier = Modifier.focusRequester(downloadFocusRequester),
					)

					GlassDialogButton(
						text = "Release Notes",
						onClick = onReleaseNotes,
					)

					GlassDialogButton(
						text = "Later",
						onClick = onDismiss,
					)
				}
			}
		}
	}

	LaunchedEffect(Unit) {
		downloadFocusRequester.requestFocus()
	}
}

@Composable
private fun ReleaseNotesDialog(
	updateInfo: UpdateCheckerService.UpdateInfo,
	onDownload: () -> Unit,
	onViewOnGitHub: () -> Unit,
	onDismiss: () -> Unit,
) {
	val downloadFocusRequester = remember { FocusRequester() }
	val sizeMB = updateInfo.apkSize / (1024.0 * 1024.0)

	val htmlContent = remember(updateInfo) {
		buildString {
			append("<!DOCTYPE html><html><head>")
			append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
			append("<style>")
			append("body { font-family: sans-serif; padding: 16px; background-color: transparent; color: #e0e0e0; margin: 0; }")
			append("h1, h2, h3 { color: #ffffff; margin-top: 16px; margin-bottom: 8px; }")
			append("h1 { font-size: 1.5em; }")
			append("h2 { font-size: 1.3em; }")
			append("h3 { font-size: 1.1em; }")
			append("p { margin: 8px 0; line-height: 1.5; }")
			append("ul, ol { margin: 8px 0; padding-left: 24px; line-height: 1.6; }")
			append("li { margin: 4px 0; }")
			append("code { background-color: rgba(255,255,255,0.08); padding: 2px 6px; border-radius: 3px; font-family: monospace; color: #f0f0f0; }")
			append("pre { background-color: rgba(255,255,255,0.06); padding: 12px; border-radius: 4px; overflow-x: auto; }")
			append("pre code { background-color: transparent; padding: 0; }")
			append("a { color: #00A4DC; text-decoration: none; }")
			append("blockquote { border-left: 3px solid #00A4DC; margin: 8px 0; padding-left: 12px; color: #b0b0b0; }")
			append("strong { color: #ffffff; }")
			append("hr { border: none; border-top: 1px solid rgba(255,255,255,0.1); margin: 16px 0; }")
			append("</style></head><body>")
			append("<h2>Version ${updateInfo.version}</h2>")
			append("<p><strong>Size:</strong> ${String.format("%.1f", sizeMB)} MB</p>")
			append("<hr>")

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
	}

	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		Box(
			modifier = Modifier.fillMaxSize(),
			contentAlignment = Alignment.Center,
		) {
			Column(
				modifier = Modifier
					.widthIn(min = 500.dp, max = 800.dp)
					.clip(RoundedCornerShape(20.dp))
					.background(Color(0xE6141414))
					.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
					.padding(vertical = 20.dp),
			) {
				// Title
				Text(
					text = "Release Notes",
					fontSize = 20.sp,
					fontWeight = FontWeight.W600,
					color = Color.White,
					modifier = Modifier
						.padding(horizontal = 24.dp)
						.padding(bottom = 12.dp),
				)

				// Divider
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(1.dp)
						.background(Color.White.copy(alpha = 0.08f)),
				)

				// WebView for release notes content
				AndroidView(
					factory = { ctx ->
						WebView(ctx).apply {
							layoutParams = LinearLayout.LayoutParams(
								ViewGroup.LayoutParams.MATCH_PARENT,
								(ctx.resources.displayMetrics.heightPixels * 0.55).toInt()
							)
							setBackgroundColor(android.graphics.Color.TRANSPARENT)
							settings.apply {
								javaScriptEnabled = false
								defaultTextEncodingName = "utf-8"
							}
							loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
						}
					},
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 4.dp),
				)

				// Divider
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(1.dp)
						.background(Color.White.copy(alpha = 0.08f)),
				)

				Spacer(modifier = Modifier.height(16.dp))

				// Buttons
				Column(
					modifier = Modifier.padding(horizontal = 24.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					GlassDialogButton(
						text = "Download",
						onClick = onDownload,
						isPrimary = true,
						modifier = Modifier.focusRequester(downloadFocusRequester),
					)

					GlassDialogButton(
						text = "View on GitHub",
						onClick = onViewOnGitHub,
					)

					GlassDialogButton(
						text = "Close",
						onClick = onDismiss,
					)
				}
			}
		}
	}

	LaunchedEffect(Unit) {
		downloadFocusRequester.requestFocus()
	}
}

private fun downloadAndInstall(
	context: Context,
	updateChecker: UpdateCheckerService,
	updateInfo: UpdateCheckerService.UpdateInfo
) {
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
		if (!context.packageManager.canRequestPackageInstalls()) {
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

	CoroutineScope(Dispatchers.Main).launch {
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
