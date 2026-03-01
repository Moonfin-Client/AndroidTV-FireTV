package org.moonfin.server.core.model

data class SystemInfo(
    val serverName: String,
    val version: String,
    val productName: String,
    val id: String,
    val localAddress: String?,
    val wanAddress: String?,
    val operatingSystem: String?,
    val httpServerPortNumber: Int?,
    val httpsPortNumber: Int?,
    val webSocketPortNumber: Int?,
    val hasPendingRestart: Boolean?,
    val isShuttingDown: Boolean?,
    val canSelfRestart: Boolean?,
    val canSelfUpdate: Boolean?,
    val startupWizardCompleted: Boolean?,
)
