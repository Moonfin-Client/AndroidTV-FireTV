package org.jellyfin.androidtv.ui.settings.screen.moonfin

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.Checkbox
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsMoonfinSyncPlayScreen() {
    val router = LocalRouter.current
    val userPreferences = koinInject<UserPreferences>()

    SettingsColumn {
        item {
            ListSection(
                overlineContent = { Text(stringResource(R.string.pref_syncplay).uppercase()) },
                headingContent = { Text(stringResource(R.string.pref_syncplay_settings)) },
            )
        }

        item {
            var syncPlayEnabled by rememberPreference(userPreferences, UserPreferences.syncPlayEnabled)
            ListButton(
                headingContent = { Text(stringResource(R.string.pref_syncplay_enabled)) },
                captionContent = { Text(stringResource(R.string.pref_syncplay_enabled_description)) },
                trailingContent = { Checkbox(checked = syncPlayEnabled) },
                onClick = { syncPlayEnabled = !syncPlayEnabled }
            )
        }

        // Playback Settings Section
        item { ListSection(headingContent = { Text(stringResource(R.string.pref_syncplay_playback_settings)) }) }

        // Enable Sync Correction
        item {
            var enableSyncCorrection by rememberPreference(userPreferences, UserPreferences.syncPlayEnableSyncCorrection)
            ListButton(
                headingContent = { Text(stringResource(R.string.pref_syncplay_sync_correction)) },
                captionContent = { Text(stringResource(R.string.pref_syncplay_sync_correction_description)) },
                trailingContent = { Checkbox(checked = enableSyncCorrection) },
                onClick = { enableSyncCorrection = !enableSyncCorrection }
            )
        }

        // SpeedToSync settings
        item {
            var useSpeedToSync by rememberPreference(userPreferences, UserPreferences.syncPlayUseSpeedToSync)
            ListButton(
                headingContent = { Text(stringResource(R.string.pref_syncplay_speed_to_sync)) },
                captionContent = { Text(stringResource(R.string.pref_syncplay_speed_to_sync_description)) },
                trailingContent = { Checkbox(checked = useSpeedToSync) },
                onClick = { useSpeedToSync = !useSpeedToSync }
            )
        }

        item {
            val useSpeedToSync by rememberPreference(userPreferences, UserPreferences.syncPlayUseSpeedToSync)
            val minDelay by rememberPreference(userPreferences, UserPreferences.syncPlayMinDelaySpeedToSync)
            ListButton(
                headingContent = { Text(stringResource(R.string.pref_syncplay_min_delay_speed_to_sync)) },
                captionContent = { Text(stringResource(R.string.pref_syncplay_min_delay_speed_to_sync_description, minDelay.toInt())) },
                enabled = useSpeedToSync,
                onClick = { router.push(Routes.MOONFIN_SYNCPLAY_MIN_DELAY) }
            )
        }

        item {
            val useSpeedToSync by rememberPreference(userPreferences, UserPreferences.syncPlayUseSpeedToSync)
            val maxDelay by rememberPreference(userPreferences, UserPreferences.syncPlayMaxDelaySpeedToSync)
            ListButton(
                headingContent = { Text(stringResource(R.string.pref_syncplay_max_delay_speed_to_sync)) },
                captionContent = { Text(stringResource(R.string.pref_syncplay_max_delay_speed_to_sync_description, maxDelay.toInt())) },
                enabled = useSpeedToSync,
                onClick = { router.push(Routes.MOONFIN_SYNCPLAY_MAX_DELAY) }
            )
        }

        item {
            val useSpeedToSync by rememberPreference(userPreferences, UserPreferences.syncPlayUseSpeedToSync)
            val duration by rememberPreference(userPreferences, UserPreferences.syncPlaySpeedToSyncDuration)
            ListButton(
                headingContent = { Text(stringResource(R.string.pref_syncplay_speed_to_sync_duration)) },
                captionContent = { Text(stringResource(R.string.pref_syncplay_speed_to_sync_duration_description, duration.toInt())) },
                enabled = useSpeedToSync,
                onClick = { router.push(Routes.MOONFIN_SYNCPLAY_DURATION) }
            )
        }

        // SkipToSync settings
        item {
            var useSkipToSync by rememberPreference(userPreferences, UserPreferences.syncPlayUseSkipToSync)
            ListButton(
                headingContent = { Text(stringResource(R.string.pref_syncplay_skip_to_sync)) },
                captionContent = { Text(stringResource(R.string.pref_syncplay_skip_to_sync_description)) },
                trailingContent = { Checkbox(checked = useSkipToSync) },
                onClick = { useSkipToSync = !useSkipToSync }
            )
        }

        item {
            val useSkipToSync by rememberPreference(userPreferences, UserPreferences.syncPlayUseSkipToSync)
            val minDelay by rememberPreference(userPreferences, UserPreferences.syncPlayMinDelaySkipToSync)
            ListButton(
                headingContent = { Text(stringResource(R.string.pref_syncplay_min_delay_skip_to_sync)) },
                captionContent = { Text(stringResource(R.string.pref_syncplay_min_delay_skip_to_sync_description, minDelay.toInt())) },
                enabled = useSkipToSync,
                onClick = { router.push(Routes.MOONFIN_SYNCPLAY_MIN_DELAY_SKIP) }
            )
        }

        // Time Sync Settings Section
        item { ListSection(headingContent = { Text(stringResource(R.string.pref_syncplay_time_settings)) }) }

        item {
            val extraTimeOffset by rememberPreference(userPreferences, UserPreferences.syncPlayExtraTimeOffset)
            ListButton(
                headingContent = { Text(stringResource(R.string.pref_syncplay_extra_time_offset)) },
                captionContent = { Text(stringResource(R.string.pref_syncplay_extra_time_offset_description, extraTimeOffset.toInt())) },
                onClick = { router.push(Routes.MOONFIN_SYNCPLAY_EXTRA_OFFSET) }
            )
        }
    }
}
