package org.moonfin.server.core.model

data class ServerValidationResult(
    val address: String,
    val isValid: Boolean,
    val serverType: ServerType?,
    val systemInfo: PublicSystemInfo?,
    val errorMessage: String?,
)
