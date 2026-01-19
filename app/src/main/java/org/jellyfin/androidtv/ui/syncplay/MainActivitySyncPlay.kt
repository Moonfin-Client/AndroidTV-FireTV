package org.jellyfin.androidtv.ui.syncplay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
fun MainActivitySyncPlay() {
    val viewModel = koinActivityViewModel<SyncPlayViewModel>()
    val visible by viewModel.visible.collectAsState()

    // Only render dialog when visible to avoid unnecessary composition overhead
    if (visible) {
        JellyfinTheme {
            SyncPlayDialog(
                visible = true,
                onDismissRequest = { viewModel.hide() }
            )
        }
    }
}
