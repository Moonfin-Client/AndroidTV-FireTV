package org.jellyfin.androidtv.ui.settings.screen.moonfin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsMoonfinMdblistApiKeyScreen() {
	val router = LocalRouter.current
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	var mdblistApiKey by rememberPreference(userSettingPreferences, UserSettingPreferences.mdblistApiKey)
	var tempApiKey by remember { mutableStateOf(mdblistApiKey) }

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_enable_additional_ratings).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_mdblist_api_key)) },
				captionContent = { Text(stringResource(R.string.pref_mdblist_api_key_description)) },
			)
		}

		item {
			Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
				OutlinedTextField(
					value = tempApiKey,
					onValueChange = { tempApiKey = it },
					modifier = Modifier.fillMaxWidth(),
					label = { androidx.compose.material3.Text("API Key", color = Color.White) },
					placeholder = { androidx.compose.material3.Text("Enter your MDBList API key", color = Color.Gray) },
					singleLine = true,
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
					colors = OutlinedTextFieldDefaults.colors(
						focusedTextColor = Color.White,
						unfocusedTextColor = Color.White,
						focusedBorderColor = Color.White,
						unfocusedBorderColor = Color.Gray,
						cursorColor = Color.White
					)
				)
			}
		}

		item {
			ListButton(
				headingContent = { Text("Save") },
				onClick = {
					mdblistApiKey = tempApiKey.trim()
					router.back()
				}
			)
		}

		item {
			ListButton(
				headingContent = { Text("Clear") },
				onClick = {
					tempApiKey = ""
					mdblistApiKey = ""
				}
			)
		}
	}
}
