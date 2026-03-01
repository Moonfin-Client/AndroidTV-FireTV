package org.moonfin.server.core.model

data class PublicSystemInfo(
    val serverName: String,
    val version: String,
    val productName: String,
    val id: String,
    val startupWizardCompleted: Boolean?,
    val localAddress: String? = null,
    val wanAddress: String? = null,
)
