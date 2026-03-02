package org.moonfin.server.emby

class EmbyApiException(
	val statusCode: Int,
	override val message: String,
	override val cause: Throwable? = null,
) : Exception(message, cause) {

	val isAuthError: Boolean get() = statusCode == 401 || statusCode == 403

	val isNotFound: Boolean get() = statusCode == 404

	val isServerError: Boolean get() = statusCode in 500..599

	val isRateLimited: Boolean get() = statusCode == 429

	override fun toString(): String = "EmbyApiException(status=$statusCode, message=$message)"

	companion object {
		fun fromStatus(statusCode: Int, context: String = ""): EmbyApiException {
			val msg = when (statusCode) {
				400 -> "Bad request"
				401 -> "Unauthorized"
				403 -> "Forbidden"
				404 -> "Not found"
				405 -> "Method not allowed"
				409 -> "Conflict"
				429 -> "Rate limited"
				500 -> "Internal server error"
				502 -> "Bad gateway"
				503 -> "Service unavailable"
				else -> "HTTP $statusCode"
			}
			val fullMsg = if (context.isNotEmpty()) "$msg: $context" else msg
			return EmbyApiException(statusCode, fullMsg)
		}
	}
}
