package org.moonfin.server.core.model

data class ClientCapabilities(
    val playableMediaTypes: List<MediaType> = emptyList(),
    val supportedCommands: List<String> = emptyList(),
    val supportsMediaControl: Boolean = false,
    val supportsSync: Boolean = false,
    val iconUrl: String? = null,
)
