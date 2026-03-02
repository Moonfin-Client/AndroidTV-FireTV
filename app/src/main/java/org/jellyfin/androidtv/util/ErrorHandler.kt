package org.jellyfin.androidtv.util

import org.moonfin.server.emby.EmbyApiException
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Centralized error handling utility for consistent error logging and user-friendly messages.
 * Provides standardized error handling patterns across the application.
 */
object ErrorHandler {
	
	/**
	 * Handle an error with logging and optional user message generation.
	 * 
	 * @param error The throwable to handle
	 * @param context A description of what operation failed (e.g., "load trending content")
	 * @param includeStackTrace Whether to log the full stack trace (default: true)
	 * @return A user-friendly error message
	 */
	fun handle(
		error: Throwable,
		context: String,
		includeStackTrace: Boolean = true
	): String {
		val userMessage = getUserFriendlyMessage(error, context)
		
		if (includeStackTrace) {
			Timber.e(error, "Failed to $context")
		} else {
			Timber.e("Failed to $context: ${error.message}")
		}
		
		return userMessage
	}
	
	/**
	 * Handle an error with a warning level log.
	 * 
	 * @param error The throwable to handle
	 * @param context A description of what operation failed
	 * @param includeStackTrace Whether to log the full stack trace (default: false)
	 * @return A user-friendly error message
	 */
	fun handleWarning(
		error: Throwable,
		context: String,
		includeStackTrace: Boolean = false
	): String {
		val userMessage = getUserFriendlyMessage(error, context)
		
		if (includeStackTrace) {
			Timber.w(error, "Failed to $context")
		} else {
			Timber.w("Failed to $context: ${error.message}")
		}
		
		return userMessage
	}
	
	/**
	 * Convert an exception to a user-friendly error message.
	 * 
	 * @param error The throwable to convert
	 * @param context A description of what operation failed
	 * @return A user-friendly error message
	 */
	fun getUserFriendlyMessage(error: Throwable, context: String = ""): String {
		return when (error) {
			is EmbyApiException -> getEmbyErrorMessage(error, context)
			is UnknownHostException -> "Cannot connect to server. Check your network connection."
			is SocketTimeoutException -> "Connection timed out. The server is taking too long to respond."
			is IOException -> "Network error occurred. Please check your connection."
			else -> {
				val baseMessage = error.message ?: "Unknown error"
				
				when {
					baseMessage.contains("403") || baseMessage.contains("Forbidden") -> 
						"Permission denied. You may not have access to this resource."
					baseMessage.contains("401") || baseMessage.contains("Unauthorized") -> 
						"Authentication failed. Please sign in again."
					baseMessage.contains("404") || baseMessage.contains("Not Found") -> 
						"Resource not found. It may have been removed."
					baseMessage.contains("500") || baseMessage.contains("Internal Server Error") -> 
						"Server error occurred. Please try again later."
					baseMessage.contains("503") || baseMessage.contains("Service Unavailable") -> 
						"Service temporarily unavailable. Please try again later."
					context.isNotEmpty() -> "Failed to $context: $baseMessage"
					else -> baseMessage
				}
			}
		}
	}

	private fun getEmbyErrorMessage(error: EmbyApiException, context: String): String {
		return when (error.statusCode) {
			400 -> "Invalid request. ${error.message}"
			401 -> "Authentication failed. Please sign in again."
			403 -> "Permission denied. You may not have access to this resource."
			404 -> "Resource not found. It may have been removed."
			405 -> "This operation is not supported by the server."
			409 -> "Conflict: the requested change could not be applied."
			429 -> "Too many requests. Please wait and try again."
			500 -> "Emby server error. Please try again later."
			502 -> "Emby server is unreachable (bad gateway)."
			503 -> "Emby server is temporarily unavailable."
			else -> if (context.isNotEmpty()) "Failed to $context (HTTP ${error.statusCode})."
			        else "Server returned error ${error.statusCode}."
		}
	}
	
	/**
	 * Execute a block of code with automatic error handling.
	 * Logs errors and returns a Result.
	 * 
	 * @param context A description of the operation being performed
	 * @param block The code block to execute
	 * @return Result containing either the value or the error
	 */
	inline fun <T> catching(context: String, block: () -> T): Result<T> {
		return runCatching(block).onFailure { error ->
			handle(error, context)
		}
	}
	
	/**
	 * Execute a block of code with automatic warning-level error handling.
	 * Logs warnings and returns a Result.
	 * 
	 * @param context A description of the operation being performed
	 * @param block The code block to execute
	 * @return Result containing either the value or the error
	 */
	inline fun <T> catchingWarning(context: String, block: () -> T): Result<T> {
		return runCatching(block).onFailure { error ->
			handleWarning(error, context)
		}
	}
}

/**
 * Extension function to handle errors in a Result with logging.
 * 
 * @param context A description of what operation failed
 * @return The user-friendly error message if the Result is a failure, null otherwise
 */
fun <T> Result<T>.handleError(context: String): String? {
	return exceptionOrNull()?.let { error ->
		ErrorHandler.handle(error, context)
	}
}

/**
 * Extension function to handle errors in a Result with warning-level logging.
 * 
 * @param context A description of what operation failed
 * @return The user-friendly error message if the Result is a failure, null otherwise
 */
fun <T> Result<T>.handleWarning(context: String): String? {
	return exceptionOrNull()?.let { error ->
		ErrorHandler.handleWarning(error, context)
	}
}
