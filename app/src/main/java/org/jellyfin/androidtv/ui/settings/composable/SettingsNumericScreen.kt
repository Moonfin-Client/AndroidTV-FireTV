package org.jellyfin.androidtv.ui.settings.composable

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.preference.Preference
import org.koin.compose.koinInject

/**
 * Reusable settings screen for numeric float preferences with a slider-like list of options.
 */
@Composable
fun SettingsNumericScreen(
    route: String,
    preference: Preference<Float>,
    titleRes: Int,
    valueTemplate: Int,
    minValue: Double,
    maxValue: Double,
    stepSize: Double,
) {
    val router = LocalRouter.current
    val userPreferences = koinInject<UserPreferences>()
    var currentValue by rememberPreference(userPreferences, preference)

    // Generate options from min to max with step size
    val options = generateSequence(minValue) { it + stepSize }
        .takeWhile { it <= maxValue + (stepSize / 2) } // Add small epsilon to handle floating point
        .map { it.toFloat() to "${it.toInt()} ms" }
        .toList()

    SettingsColumn {
        item {
            ListSection(
                overlineContent = { Text(stringResource(titleRes).uppercase()) },
                headingContent = { Text(stringResource(titleRes)) },
                captionContent = { Text(stringResource(valueTemplate, currentValue.toInt())) },
            )
        }

        items(options) { (value, label) ->
            ListButton(
                headingContent = { Text(label) },
                trailingContent = { RadioButton(checked = kotlin.math.abs(currentValue - value) < 0.01f) },
                onClick = {
                    currentValue = value
                    router.back()
                }
            )
        }
    }
}
