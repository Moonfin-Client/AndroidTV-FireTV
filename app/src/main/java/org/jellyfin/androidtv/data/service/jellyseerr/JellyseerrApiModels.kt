package org.jellyfin.androidtv.data.service.jellyseerr

import android.os.Parcel
import android.os.Parcelable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Jellyseerr API Models
 * Models for communicating with Jellyseerr API endpoints
 */

// ==================== Request Models ====================

@Serializable
data class JellyseerrRequestDto(
	val id: Int,
	val status: Int, // 1 = pending, 2 = approved, 3 = declined, 4 = available
	val createdAt: String? = null,
	val updatedAt: String? = null,
	val type: String, // "movie" or "tv"
	val media: JellyseerrMediaDto? = null,
	val requestedBy: JellyseerrUserDto? = null,
	val seasonCount: Int? = null,
	val externalId: String? = null,
	val is4k: Boolean = false,
	val seasons: List<JellyseerrSeasonRequestDto>? = null, // Per-season request info for TV shows
) {
	companion object {
		// Request status constants
		const val STATUS_PENDING = 1
		const val STATUS_APPROVED = 2
		const val STATUS_DECLINED = 3
		const val STATUS_AVAILABLE = 4
	}
}

/**
 * Season-level request information
 */
@Serializable
data class JellyseerrSeasonRequestDto(
	val id: Int,
	val seasonNumber: Int,
	val status: Int, // 1 = pending, 2 = approved, 3 = declined, 4 = available
	val createdAt: String? = null,
	val updatedAt: String? = null,
)

@Serializable
data class JellyseerrMediaDto(
	val id: Int,
	val mediaType: String? = null, // "movie" or "tv"
	val tmdbId: Int? = null,
	val tvdbId: Int? = null,
	val imdbId: String? = null,
	val status: Int? = null, // Media status
	val status4k: Int? = null,
	@SerialName("mediaAddedAt")
	val mediaAddedAt: String? = null,
	val serviceId: Int? = null,
	val serviceId4k: Int? = null,
	val externalServiceId: Int? = null,
	val externalServiceId4k: Int? = null,
	val externalServiceSlug: String? = null,
	val externalServiceSlug4k: String? = null,
	val ratingKey: String? = null,
	val ratingKey4k: String? = null,
	val title: String? = null,
	val name: String? = null,
	val posterPath: String? = null,
	val backdropPath: String? = null,
	val overview: String? = null,
	val releaseDate: String? = null,
	val firstAirDate: String? = null,
	val originalLanguage: String? = null,
	val genreIds: List<Int>? = null,
	val voteAverage: Double? = null,
	val externalIds: JellyseerrExternalIds? = null,
	@SerialName("requests")
	val requestList: List<JellyseerrRequestDto>? = null,
)

@Serializable
data class JellyseerrExternalIds(
	val tvdbId: Int? = null,
	val tmdbId: Int? = null,
	val imdbId: String? = null,
)

@Serializable
data class JellyseerrUserDto(
	val id: Int,
	val username: String? = null,
	val email: String? = null,
	val avatar: String? = null,
	val apiKey: String? = null,
	val permissions: Int? = null, // Permission bitfield
) {
	// Jellyseerr permission constants (bitfield) - from server/lib/permissions.ts
	companion object {
		const val PERMISSION_NONE = 0
		const val PERMISSION_ADMIN = 2
		const val PERMISSION_MANAGE_SETTINGS = 4
		const val PERMISSION_MANAGE_USERS = 8
		const val PERMISSION_MANAGE_REQUESTS = 16
		const val PERMISSION_REQUEST = 32
		const val PERMISSION_AUTO_APPROVE = 128
		const val PERMISSION_REQUEST_4K = 1024
		const val PERMISSION_REQUEST_4K_MOVIE = 2048
		const val PERMISSION_REQUEST_4K_TV = 4096
		const val PERMISSION_REQUEST_ADVANCED = 8192
		const val PERMISSION_REQUEST_MOVIE = 262144
		const val PERMISSION_REQUEST_TV = 524288
	}
	
	/**
	 * Check if user has specific permission(s)
	 */
	fun hasPermission(permission: Int): Boolean {
		val perms = permissions ?: 0
		// Admin permission bypasses all other permissions
		if (perms and PERMISSION_ADMIN != 0) return true
		return perms and permission != 0
	}
	
	/**
	 * Check if user can request 4K content (movies or TV)
	 */
	fun canRequest4k(): Boolean {
		return hasPermission(PERMISSION_REQUEST_4K) || 
			hasPermission(PERMISSION_REQUEST_4K_MOVIE) || 
			hasPermission(PERMISSION_REQUEST_4K_TV)
	}
	
	/**
	 * Check if user can request 4K movies specifically
	 */
	fun canRequest4kMovies(): Boolean {
		return hasPermission(PERMISSION_REQUEST_4K) || hasPermission(PERMISSION_REQUEST_4K_MOVIE)
	}
	
	/**
	 * Check if user can request 4K TV specifically
	 */
	fun canRequest4kTv(): Boolean {
		return hasPermission(PERMISSION_REQUEST_4K) || hasPermission(PERMISSION_REQUEST_4K_TV)
	}
	
	/**
	 * Check if user has advanced request permission (can modify server/profile/folder)
	 */
	fun hasAdvancedRequestPermission(): Boolean {
		return hasPermission(PERMISSION_REQUEST_ADVANCED) || hasPermission(PERMISSION_MANAGE_REQUESTS)
	}
	
	/**
	 * Check if user is admin (has ADMIN permission)
	 */
	fun isAdmin(): Boolean {
		return hasPermission(PERMISSION_ADMIN)
	}
}

// ==================== Discover/Trending Models ====================

@Serializable
data class JellyseerrDiscoverPageDto(
	val results: List<JellyseerrDiscoverItemDto> = emptyList(),
	val totalPages: Int = 0,
	val totalResults: Int = 0,
	val page: Int = 1,
)

@Serializable
data class JellyseerrDiscoverItemDto(
	val id: Int,
	val mediaType: String? = null, // "movie" or "tv"
	val title: String? = null,
	val name: String? = null, // TV shows use 'name' instead of 'title'
	val originalTitle: String? = null,
	val originalName: String? = null,
	val posterPath: String? = null,
	val backdropPath: String? = null,
	val overview: String? = null,
	val releaseDate: String? = null,
	val firstAirDate: String? = null,
	val originalLanguage: String? = null,
	val genreIds: List<Int> = emptyList(),
	val voteAverage: Double? = null,
	val voteCount: Int? = null,
	val popularity: Double? = null,
	val adult: Boolean = false,
	val mediaInfo: JellyseerrMediaInfoDto? = null, // Status information
) : Parcelable {
	constructor(parcel: Parcel) : this(
		id = parcel.readInt(),
		mediaType = parcel.readString(),
		title = parcel.readString(),
		name = parcel.readString(),
		originalTitle = parcel.readString(),
		originalName = parcel.readString(),
		posterPath = parcel.readString(),
		backdropPath = parcel.readString(),
		overview = parcel.readString(),
		releaseDate = parcel.readString(),
		firstAirDate = parcel.readString(),
		originalLanguage = parcel.readString(),
		genreIds = mutableListOf<Int>().apply { parcel.readList(this, Int::class.java.classLoader) },
		voteAverage = if (parcel.readByte() != 0.toByte()) parcel.readDouble() else null,
		voteCount = if (parcel.readByte() != 0.toByte()) parcel.readInt() else null,
		popularity = if (parcel.readByte() != 0.toByte()) parcel.readDouble() else null,
		adult = parcel.readByte() != 0.toByte(),
		mediaInfo = null // Not parcelable
	)

	override fun writeToParcel(parcel: Parcel, flags: Int) {
		parcel.writeInt(id)
		parcel.writeString(mediaType)
		parcel.writeString(title)
		parcel.writeString(name)
		parcel.writeString(originalTitle)
		parcel.writeString(originalName)
		parcel.writeString(posterPath)
		parcel.writeString(backdropPath)
		parcel.writeString(overview)
		parcel.writeString(releaseDate)
		parcel.writeString(firstAirDate)
		parcel.writeString(originalLanguage)
		parcel.writeList(genreIds)
		parcel.writeByte(if (voteAverage != null) 1 else 0)
		if (voteAverage != null) parcel.writeDouble(voteAverage)
		parcel.writeByte(if (voteCount != null) 1 else 0)
		if (voteCount != null) parcel.writeInt(voteCount)
		parcel.writeByte(if (popularity != null) 1 else 0)
		if (popularity != null) parcel.writeDouble(popularity)
		parcel.writeByte(if (adult) 1 else 0)
		// mediaInfo not written to parcel
	}

	override fun describeContents(): Int = 0
	
	/**
	 * Check if this media is already available in the library
	 */
	fun isAvailable(): Boolean = mediaInfo?.status == 5 || mediaInfo?.status == 4
	
	/**
	 * Check if this media has been blacklisted
	 * Status 6 = blacklisted
	 */
	fun isBlacklisted(): Boolean = mediaInfo?.status == 6

	companion object CREATOR : Parcelable.Creator<JellyseerrDiscoverItemDto> {
		override fun createFromParcel(parcel: Parcel): JellyseerrDiscoverItemDto {
			return JellyseerrDiscoverItemDto(parcel)
		}

		override fun newArray(size: Int): Array<JellyseerrDiscoverItemDto?> {
			return arrayOfNulls(size)
		}
	}
}

@Serializable
data class JellyseerrMovieDetailsDto(
	val id: Int,
	val mediaType: String? = "movie",
	val title: String,
	val tagline: String? = null,
	val posterPath: String? = null,
	val backdropPath: String? = null,
	val overview: String? = null,
	val releaseDate: String? = null,
	val status: String? = null, // e.g., "Released", "In Production", "Post Production"
	val runtime: Int? = null,
	val budget: Long? = null,
	val revenue: Long? = null,
	val voteAverage: Double? = null,
	val voteCount: Int? = null,
	val genres: List<JellyseerrGenreDto> = emptyList(),
	val credits: JellyseerrCreditsDto? = null,
	val externalIds: JellyseerrExternalIds? = null,
	val mediaInfo: JellyseerrMediaInfoDto? = null,
	val keywords: List<JellyseerrKeywordDto> = emptyList(),
)

@Serializable
data class JellyseerrTvDetailsDto(
	val id: Int,
	val mediaType: String? = "tv",
	val name: String? = null,
	val title: String? = null,
	val posterPath: String? = null,
	val backdropPath: String? = null,
	val overview: String? = null,
	val tagline: String? = null,
	val firstAirDate: String? = null,
	val lastAirDate: String? = null,
	val status: String? = null, // e.g., "Returning Series", "Ended", "Canceled"
	val numberOfSeasons: Int? = null,
	val numberOfEpisodes: Int? = null,
	val voteAverage: Double? = null,
	val voteCount: Int? = null,
	val genres: List<JellyseerrGenreDto> = emptyList(),
	val credits: JellyseerrCreditsDto? = null,
	val networks: List<JellyseerrNetworkDto> = emptyList(),
	val externalIds: JellyseerrExternalIds? = null,
	val mediaInfo: JellyseerrMediaInfoDto? = null,
	val keywords: List<JellyseerrKeywordDto> = emptyList(),
)

@Serializable
data class JellyseerrGenreDto(
	val id: Int,
	val name: String,
	val backdrops: List<String> = emptyList(),
)

@Serializable
data class JellyseerrNetworkDto(
	val id: Int,
	val name: String,
	val logoPath: String? = null,
	val originCountry: String? = null,
)

@Serializable
data class JellyseerrStudioDto(
	val id: Int,
	val name: String,
	val logoPath: String? = null,
)

@Serializable
data class JellyseerrKeywordDto(
	val id: Int,
	val name: String,
)

@Serializable
data class JellyseerrCreditsDto(
	val cast: List<JellyseerrCastMemberDto> = emptyList(),
	val crew: List<JellyseerrCrewMemberDto> = emptyList(),
)

@Serializable
data class JellyseerrCastMemberDto(
	val id: Int,
	val name: String,
	val character: String? = null,
	val profilePath: String? = null,
	val order: Int? = null,
)

@Serializable
data class JellyseerrCrewMemberDto(
	val id: Int,
	val name: String,
	val department: String? = null,
	val job: String? = null,
	val profilePath: String? = null,
)

@Serializable
data class JellyseerrMediaInfoDto(
	val id: Int? = null,
	val tmdbId: Int? = null,
	val tvdbId: Int? = null,
	val status: Int? = null, // 1=unknown, 2=pending, 3=processing, 4=partially_available, 5=available
	val status4k: Int? = null, // Same status values as status, but for 4K
	val requests: List<JellyseerrRequestDto>? = null,
)

// ==================== Person Models ====================

@Serializable
data class JellyseerrPersonDetailsDto(
	val id: Int,
	val name: String,
	val biography: String? = null,
	val birthday: String? = null,
	val deathday: String? = null,
	val placeOfBirth: String? = null,
	val profilePath: String? = null,
	val knownForDepartment: String? = null,
	val popularity: Double? = null,
)

@Serializable
data class JellyseerrPersonCombinedCreditsDto(
	val cast: List<JellyseerrDiscoverItemDto> = emptyList(),
	val crew: List<JellyseerrDiscoverItemDto> = emptyList(),
)

// ==================== Request/Response Wrappers ====================

@Serializable
data class JellyseerrListResponse<T>(
	val pageInfo: JellyseerrPageInfoDto? = null,
	val results: List<T> = emptyList(),
)

@Serializable
data class JellyseerrPageInfoDto(
	val pages: Int,
	val pageSize: Int,
	val results: Int,
	val page: Int,
)

@Serializable
data class JellyseerrCreateRequestDto(
	val mediaId: Int,
	val mediaType: String, // "movie" or "tv"
	@Serializable(with = SeasonsSerializer::class)
	val seasons: Seasons? = null, // For TV shows: specific seasons or "all"
	val tvdbId: Int? = null,
	val imdbId: String? = null,
	val is4k: Boolean = false,
	val profileId: Int? = null, // Custom Radarr/Sonarr quality profile
	val rootFolderId: Int? = null, // Custom root folder
	val serverId: Int? = null, // Custom Radarr/Sonarr server instance
)

// Wrapper to support seasons as either array of ints or "all" string
@Serializable
sealed class Seasons {
	@Serializable
	@SerialName("list")
	data class List(val seasons: kotlin.collections.List<Int>) : Seasons()
	
	@Serializable
	@SerialName("all")
	object All : Seasons()
}

// Custom serializer to serialize Seasons as either array or string
class SeasonsSerializer : KSerializer<Seasons> {
	override val descriptor = buildClassSerialDescriptor("Seasons")
	
	override fun serialize(encoder: Encoder, value: Seasons) {
		when (value) {
			is Seasons.List -> encoder.encodeSerializableValue(
				ListSerializer(Int.serializer()),
				value.seasons
			)
			is Seasons.All -> encoder.encodeString("all")
		}
	}
	
	override fun deserialize(decoder: Decoder): Seasons {
		// Not needed for our use case (we only send, not receive)
		throw NotImplementedError("Seasons deserialization not implemented")
	}
}

// ==================== Settings/Configuration ====================

@Serializable
data class JellyseerrMainSettingsDto(
	val apiKey: String,
	val appLanguage: String? = null,
	val applicationTitle: String? = null,
	val applicationUrl: String? = null,
	val hideAvailable: Boolean? = null,
	val partialRequestsEnabled: Boolean? = null,
	val localLogin: Boolean? = null,
	val mediaServerType: Int? = null,
	val newPlexLogin: Boolean? = null,
	val defaultPermissions: Int? = null,
	val enableSpecialEpisodes: Boolean? = null,
)

@Serializable
data class JellyseerrStatusDto(
	val appData: JellyseerrAppDataDto? = null,
)

@Serializable
data class JellyseerrAppDataDto(
	val version: String? = null,
	val initialized: Boolean = false,
)

// ==================== Service Configuration ====================

@Serializable
data class JellyseerrRadarrSettingsDto(
	val id: Int,
	val name: String,
	val hostname: String,
	val port: Int,
	val apiKey: String,
	val useSsl: Boolean = false,
	val baseUrl: String? = null,
	val activeProfileId: Int,
	val activeProfileName: String,
	val activeDirectory: String,
	val activeAnimeProfileId: Int? = null,
	val activeAnimeProfileName: String? = null,
	val activeAnimeDirectory: String? = null,
	val is4k: Boolean = false,
	val minimumAvailability: String,
	val isDefault: Boolean = false,
	val externalUrl: String? = null,
	val syncEnabled: Boolean = false,
	val preventSearch: Boolean = false,
	val tagRequests: Boolean = false,
	val tags: List<Int> = emptyList(),
	val profiles: List<JellyseerrQualityProfileDto> = emptyList(),
	val rootFolders: List<JellyseerrRootFolderDto> = emptyList(),
)

@Serializable
data class JellyseerrSonarrSettingsDto(
	val id: Int,
	val name: String,
	val hostname: String,
	val port: Int,
	val apiKey: String,
	val useSsl: Boolean = false,
	val baseUrl: String? = null,
	val activeProfileId: Int,
	val activeProfileName: String,
	val activeDirectory: String,
	val activeAnimeProfileId: Int? = null,
	val activeAnimeProfileName: String? = null,
	val activeAnimeDirectory: String? = null,
	val activeLanguageProfileId: Int? = null,
	val is4k: Boolean = false,
	val enableSeasonFolders: Boolean = false,
	val isDefault: Boolean = false,
	val externalUrl: String? = null,
	val syncEnabled: Boolean = false,
	val preventSearch: Boolean = false,
	val tagRequests: Boolean = false,
	val tags: List<Int> = emptyList(),
	val profiles: List<JellyseerrQualityProfileDto> = emptyList(),
	val rootFolders: List<JellyseerrRootFolderDto> = emptyList(),
)

@Serializable
data class JellyseerrQualityProfileDto(
	val id: Int,
	val name: String,
)

@Serializable
data class JellyseerrRootFolderDto(
	val id: Int,
	val path: String,
	val freeSpace: Long? = null,
	val totalSpace: Long? = null,
)

@Serializable
data class JellyseerrTagDto(
	val id: Int,
	val label: String,
)

// ==================== Service API Models (for non-admin users) ====================

/**
 * Basic server info from /api/v1/service/radarr or /api/v1/service/sonarr
 * Available to all authenticated users (not just admins)
 */
@Serializable
data class JellyseerrServiceServerDto(
	val id: Int,
	val name: String,
	val is4k: Boolean = false,
	val isDefault: Boolean = false,
	val activeProfileId: Int,
	val activeDirectory: String,
	val activeAnimeProfileId: Int? = null,
	val activeAnimeDirectory: String? = null,
	val activeLanguageProfileId: Int? = null,
	val activeAnimeLanguageProfileId: Int? = null,
	val activeTags: List<Int> = emptyList(),
	val activeAnimeTags: List<Int>? = null,
)

/**
 * Detailed server info from /api/v1/service/radarr/:id or /api/v1/service/sonarr/:id
 * Available to all authenticated users (not just admins)
 * Contains profiles, root folders, language profiles, and tags
 */
@Serializable
data class JellyseerrServiceServerDetailsDto(
	val server: JellyseerrServiceServerDto,
	val profiles: List<JellyseerrQualityProfileDto> = emptyList(),
	val rootFolders: List<JellyseerrRootFolderDto> = emptyList(),
	val languageProfiles: List<JellyseerrLanguageProfileDto>? = null,
	val tags: List<JellyseerrTagDto> = emptyList(),
)

@Serializable
data class JellyseerrLanguageProfileDto(
	val id: Int,
	val name: String,
)
