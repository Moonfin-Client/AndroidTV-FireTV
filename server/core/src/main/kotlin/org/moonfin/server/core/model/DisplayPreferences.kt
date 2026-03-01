package org.moonfin.server.core.model

data class DisplayPreferences(
    val id: String? = null,
    val sortBy: String? = null,
    val sortOrder: SortOrder? = null,
    val customPrefs: Map<String, String>? = null,
    val client: String? = null,
)
