package org.moonfin.server.core.model

sealed class EmbyConnectionState {
	data object Disconnected : EmbyConnectionState()
	data object Connecting : EmbyConnectionState()
	data object Connected : EmbyConnectionState()
	data class Error(val cause: Throwable) : EmbyConnectionState()
	data object TokenExpired : EmbyConnectionState()
	data object ServerUnreachable : EmbyConnectionState()
	data object ServerVersionChanged : EmbyConnectionState()
}
