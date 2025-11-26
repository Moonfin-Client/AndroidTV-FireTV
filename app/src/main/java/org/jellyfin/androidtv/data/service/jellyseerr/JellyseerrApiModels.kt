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
)

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
)

@Serializable
data class JellyseerrGenreDto(
	val id: Int,
	val name: String,
)

@Serializable
data class JellyseerrNetworkDto(
	val id: Int,
	val name: String,
	val logoPath: String? = null,
	val originCountry: String? = null,
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
