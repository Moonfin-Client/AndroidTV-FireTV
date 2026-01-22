package org.jellyfin.androidtv.ui.syncplay

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.syncplay.SyncPlayManager
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.ui.settings.composable.SettingsDialog
import org.koin.compose.koinInject
import java.util.UUID

@Composable
fun SyncPlayDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    // Early return if not visible - prevents unnecessary state collection and recomposition
    if (!visible) return
    
    val syncPlayManager = koinInject<SyncPlayManager>()
    val userRepository = koinInject<org.jellyfin.androidtv.auth.repository.UserRepository>()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var isOperationInProgress by remember { mutableStateOf(false) }

    val state by syncPlayManager.state.collectAsState()
    val availableGroups by syncPlayManager.availableGroups.collectAsState()
    val currentUser by userRepository.currentUser.collectAsState()

    LaunchedEffect(Unit) {
        syncPlayManager.refreshGroups()
    }

    fun canStartOperation(): Boolean = !isOperationInProgress && !state.enabled
    
    suspend fun waitForStateChange(targetEnabled: Boolean, timeoutMs: Int = 5000): Boolean {
        if (syncPlayManager.state.value.enabled == targetEnabled) return true
        
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs.toLong()) {
            syncPlayManager.state.first { it.enabled == targetEnabled }
        } != null
    }

    SettingsDialog(
        visible = true,
        onDismissRequest = onDismissRequest,
    ) {
        SyncPlayContent(
            isInGroup = state.enabled,
            groupName = state.groupInfo?.groupName,
            groupState = state.groupState.name,
            participantCount = state.groupInfo?.participants?.size ?: 0,
            availableGroups = availableGroups.map { GroupItem(it.groupId, it.groupName ?: "Unnamed Group", it.participants.size) },
            isLoading = isOperationInProgress,
            onCreateGroup = {
                if (canStartOperation()) {
                    isOperationInProgress = true
                    coroutineScope.launch {
                        val userName = currentUser?.name
                        val groupName = if (userName != null) "$userName's group" else "SyncPlay Group"
                        val result = syncPlayManager.createGroup(groupName)
                        if (result.isFailure) {
                            val error = result.exceptionOrNull()
                            val errorMessage = when {
                                error is org.jellyfin.sdk.api.client.exception.InvalidStatusException && error.message?.contains("403") == true ->
                                    "SyncPlay access denied. Enable SyncPlay permission for this user in Jellyfin server settings."
                                error is org.jellyfin.sdk.api.client.exception.InvalidStatusException && error.message?.contains("401") == true ->
                                    "Authentication error. Please sign out and sign in again."
                                else -> "Failed to create group: ${error?.message ?: "Unknown error"}"
                            }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    context,
                                    errorMessage,
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            val success = waitForStateChange(targetEnabled = true)
                            if (!success) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Group created but connection timed out. Try refreshing the group list.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                                // Refresh groups to show the newly created group
                                syncPlayManager.refreshGroups()
                            }
                        }
                        isOperationInProgress = false
                    }
                }
            },
            onJoinGroup = { groupId ->
                if (canStartOperation()) {
                    isOperationInProgress = true
                    coroutineScope.launch {
                        val result = syncPlayManager.joinGroup(groupId)
                        if (result.isFailure) {
                            val error = result.exceptionOrNull()
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Failed to join group: ${error?.message ?: "Unknown error"}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            val success = waitForStateChange(targetEnabled = true)
                            if (!success) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Join request sent but connection timed out. The group may no longer exist.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                                // Refresh to update group list
                                syncPlayManager.refreshGroups()
                            }
                        }
                        isOperationInProgress = false
                    }
                }
            },
            onLeaveGroup = {
                if (!isOperationInProgress && state.enabled) {
                    isOperationInProgress = true
                    coroutineScope.launch {
                        val result = syncPlayManager.leaveGroup()
                        // leaveGroup clears state on success or 403, so check the result
                        if (result.isSuccess || !state.enabled) {
                            // Successfully left or state was already cleared
                        } else {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Failed to leave group: ${result.exceptionOrNull()?.message ?: "Unknown error"}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        isOperationInProgress = false
                    }
                }
            },
            onRefresh = {
                if (!isOperationInProgress) {
                    isOperationInProgress = true
                    coroutineScope.launch {
                        syncPlayManager.refreshGroups()
                        isOperationInProgress = false
                    }
                }
            },
            onDismiss = onDismissRequest,
        )
    }
}

data class GroupItem(
    val id: UUID,
    val name: String,
    val participantCount: Int,
)

@Composable
private fun SyncPlayContent(
    isInGroup: Boolean,
    groupName: String?,
    groupState: String?,
    participantCount: Int,
    availableGroups: List<GroupItem>,
    isLoading: Boolean,
    onCreateGroup: () -> Unit,
    onJoinGroup: (UUID) -> Unit,
    onLeaveGroup: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsColumn {
        // Header
        item {
            ListSection(
                overlineContent = { Text(stringResource(R.string.syncplay).uppercase()) },
                headingContent = {
                    Text(
                        if (isInGroup) stringResource(R.string.syncplay_in_group)
                        else stringResource(R.string.syncplay_not_in_group)
                    )
                },
                captionContent = {
                    Text(
                        if (isInGroup) stringResource(R.string.syncplay_group_info, groupName ?: "", participantCount)
                        else stringResource(R.string.syncplay_description)
                    )
                },
            )
        }

        if (isInGroup) {
            // Current group info and leave option
            item {
                ListButton(
                    leadingContent = {
                        Icon(
                            painterResource(R.drawable.ic_syncplay),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    headingContent = { Text(groupName ?: stringResource(R.string.syncplay_unknown_group)) },
                    captionContent = {
                        Text(
                            stringResource(
                                R.string.syncplay_participants_count,
                                participantCount
                            ) + (groupState?.let { " • $it" } ?: "")
                        )
                    },
                    onClick = { /* Show group details */ },
                )
            }

            item {
                ListButton(
                    leadingContent = {
                        Icon(
                            painterResource(R.drawable.ic_logout),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    headingContent = { Text(stringResource(R.string.syncplay_leave_group)) },
                    captionContent = { Text(stringResource(R.string.syncplay_leave_group_description)) },
                    onClick = onLeaveGroup,
                    enabled = !isLoading,
                )
            }
        } else {
            // Create group option
            item {
                ListButton(
                    leadingContent = {
                        Icon(
                            painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    headingContent = { Text(stringResource(R.string.syncplay_create_group)) },
                    captionContent = { Text(stringResource(R.string.syncplay_create_group_description)) },
                    onClick = onCreateGroup,
                    enabled = !isLoading,
                )
            }

            // Refresh groups option
            item {
                ListButton(
                    leadingContent = {
                        Icon(
                            painterResource(R.drawable.ic_refresh),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    headingContent = { Text(stringResource(R.string.syncplay_refresh_groups)) },
                    captionContent = { Text(stringResource(R.string.syncplay_refresh_groups_description)) },
                    onClick = onRefresh,
                    enabled = !isLoading,
                )
            }

            // Available groups section
            if (availableGroups.isNotEmpty()) {
                item {
                    ListSection(
                        overlineContent = { Text(stringResource(R.string.syncplay_available_groups).uppercase()) },
                        headingContent = { Text(stringResource(R.string.syncplay_join_existing)) },
                        captionContent = { Text(stringResource(R.string.syncplay_groups_found, availableGroups.size)) },
                    )
                }

                availableGroups.forEach { group ->
                    item {
                        ListButton(
                            leadingContent = {
                                Icon(
                                    painterResource(R.drawable.ic_syncplay),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            headingContent = { Text(group.name) },
                            captionContent = {
                                Text(
                                    stringResource(R.string.syncplay_participants_count, group.participantCount)
                                )
                            },
                            onClick = { onJoinGroup(group.id) },
                            enabled = !isLoading,
                        )
                    }
                }
            } else {
                item {
                    ListSection(
                        overlineContent = { Text(stringResource(R.string.syncplay_available_groups).uppercase()) },
                        headingContent = { Text(stringResource(R.string.syncplay_no_groups)) },
                        captionContent = { Text(stringResource(R.string.syncplay_no_groups_description)) },
                    )
                }
            }
        }

        // Close button
        item {
            Spacer(modifier = Modifier.height(16.dp))
            ListButton(
                leadingContent = {
                    Icon(
                        painterResource(R.drawable.chevron_left),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                headingContent = { Text(stringResource(R.string.lbl_close)) },
                onClick = onDismiss,
            )
        }
    }
}
