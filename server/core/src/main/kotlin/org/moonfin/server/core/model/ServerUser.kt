package org.moonfin.server.core.model

import java.time.Instant

data class ServerUser(
    val id: String,
    val name: String,
    val serverName: String?,
    val primaryImageTag: String?,
    val hasPassword: Boolean,
    val hasConfiguredPassword: Boolean,
    val lastLoginDate: Instant?,
    val lastActivityDate: Instant?,
)
