package org.jellyfin.androidtv.util

import java.util.UUID

/**
 * Utility functions for UUID handling, especially for multi-server support
 * where UUIDs may be serialized without hyphens.
 */
object UUIDUtils {
	/**
	 * Normalize a UUID string by adding hyphens if missing.
	 * The Jellyfin SDK's JSON serialization may remove hyphens from UUIDs,
	 * so this ensures the UUID format includes hyphens for proper parsing.
	 * 
	 * @param serverId The server ID string, possibly without hyphens
	 * @return The normalized string with hyphens, or null if input is null/empty
	 */
	@JvmStatic
	fun normalizeUUIDString(serverId: String?): String? {
		if (serverId.isNullOrEmpty() || serverId == "jellyseerr") return null
		
		// If already in proper UUID format (with hyphens), return as-is
		if (serverId.contains("-")) return serverId
		
		// If it's 32 characters (UUID without hyphens), add them back: 8-4-4-4-12 format
		if (serverId.length == 32) {
			return "${serverId.substring(0, 8)}-${serverId.substring(8, 12)}-${serverId.substring(12, 16)}-${serverId.substring(16, 20)}-${serverId.substring(20)}"
		}
		
		// Return as-is for any other format
		return serverId
	}

	/**
	 * Parse a UUID string, normalizing format if needed.
	 * 
	 * @param serverId The server ID string, possibly without hyphens
	 * @return The parsed UUID, or null if parsing fails
	 */
	@JvmStatic
	fun parseUUID(serverId: String?): UUID? {
		val normalized = normalizeUUIDString(serverId) ?: return null
		return try {
			UUID.fromString(normalized)
		} catch (e: IllegalArgumentException) {
			null
		}
	}
}
