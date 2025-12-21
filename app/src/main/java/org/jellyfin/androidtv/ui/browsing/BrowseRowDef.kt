package org.jellyfin.androidtv.ui.browsing

import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.querying.GetSeriesTimersRequest
import org.jellyfin.androidtv.data.querying.GetSpecialsRequest
import org.jellyfin.sdk.model.api.request.GetAlbumArtistsRequest
import org.jellyfin.sdk.model.api.request.GetArtistsRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetLiveTvChannelsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetRecommendedProgramsRequest
import org.jellyfin.sdk.model.api.request.GetRecordingsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest

class BrowseRowDef {
	var headerText: String = ""
		private set
	var query: GetItemsRequest? = null
		private set
	var nextUpQuery: GetNextUpRequest? = null
		private set
	var similarQuery: GetSimilarItemsRequest? = null
		private set
	var latestItemsQuery: GetLatestMediaRequest? = null
		private set
	var tvChannelQuery: GetLiveTvChannelsRequest? = null
		private set
	var programQuery: GetRecommendedProgramsRequest? = null
		private set
	var recordingQuery: GetRecordingsRequest? = null
		private set
	var seriesTimerQuery: GetSeriesTimersRequest? = null
		private set
	var artistsQuery: GetArtistsRequest? = null
		private set
	var albumArtistsQuery: GetAlbumArtistsRequest? = null
		private set
	var resumeQuery: GetResumeItemsRequest? = null
		private set
	var mergedNextUpQuery: GetNextUpRequest? = null
		private set
	var specialsQuery: GetSpecialsRequest? = null
		private set
	var queryType: QueryType = QueryType.Items
		private set
	var chunkSize: Int = 0
		private set
	var isStaticHeight: Boolean = false
		private set
	var preferParentThumb: Boolean = false
		private set
	var changeTriggers: Array<ChangeTriggerType>? = null
		private set
	var sectionType: HomeSectionType? = null
		private set

	@JvmOverloads
	constructor(
		header: String?,
		query: GetItemsRequest,
		chunkSize: Int = 0,
		preferParentThumb: Boolean = false,
		staticHeight: Boolean = false
	) {
		this.headerText = header ?: ""
		this.query = query
		this.chunkSize = chunkSize
		this.preferParentThumb = preferParentThumb
		this.isStaticHeight = staticHeight
		this.queryType = QueryType.Items
	}

	constructor(
		header: String?,
		query: GetItemsRequest,
		chunkSize: Int,
		changeTriggers: Array<ChangeTriggerType>
	) {
		this.headerText = header ?: ""
		this.query = query
		this.chunkSize = chunkSize
		this.changeTriggers = changeTriggers
		this.queryType = QueryType.Items
	}

	constructor(
		header: String?,
		query: GetItemsRequest,
		chunkSize: Int,
		preferParentThumb: Boolean,
		staticHeight: Boolean,
		changeTriggers: Array<ChangeTriggerType>
	) {
		this.headerText = header ?: ""
		this.query = query
		this.chunkSize = chunkSize
		this.preferParentThumb = preferParentThumb
		this.isStaticHeight = staticHeight
		this.changeTriggers = changeTriggers
		this.queryType = QueryType.Items
	}

	constructor(
		header: String?,
		query: GetItemsRequest,
		chunkSize: Int,
		preferParentThumb: Boolean,
		staticHeight: Boolean,
		changeTriggers: Array<ChangeTriggerType>,
		queryType: QueryType
	) {
		this.headerText = header ?: ""
		this.query = query
		this.chunkSize = chunkSize
		this.queryType = queryType
		this.isStaticHeight = staticHeight
		this.preferParentThumb = preferParentThumb
		this.changeTriggers = changeTriggers
	}

	constructor(
		header: String?,
		query: GetArtistsRequest,
		chunkSize: Int,
		changeTriggers: Array<ChangeTriggerType>
	) {
		this.headerText = header ?: ""
		this.artistsQuery = query
		this.chunkSize = chunkSize
		this.queryType = QueryType.Artists
		this.changeTriggers = changeTriggers
	}

	constructor(
		header: String?,
		query: GetAlbumArtistsRequest,
		chunkSize: Int,
		changeTriggers: Array<ChangeTriggerType>
	) {
		this.headerText = header ?: ""
		this.albumArtistsQuery = query
		this.chunkSize = chunkSize
		this.queryType = QueryType.AlbumArtists
		this.changeTriggers = changeTriggers
	}

	constructor(header: String?, query: GetSeriesTimersRequest) {
		this.headerText = header ?: ""
		this.seriesTimerQuery = query
		this.isStaticHeight = true
		this.queryType = QueryType.SeriesTimer
	}

	constructor(
		header: String?,
		query: GetNextUpRequest,
		changeTriggers: Array<ChangeTriggerType>
	) {
		this.headerText = header ?: ""
		this.nextUpQuery = query
		this.queryType = QueryType.NextUp
		this.isStaticHeight = true
		this.changeTriggers = changeTriggers
	}

	constructor(
		header: String?,
		query: GetLatestMediaRequest,
		changeTriggers: Array<ChangeTriggerType>
	) {
		this.headerText = header ?: ""
		this.latestItemsQuery = query
		this.queryType = QueryType.LatestItems
		this.isStaticHeight = true
		this.changeTriggers = changeTriggers
	}

	constructor(header: String?, query: GetLiveTvChannelsRequest) {
		this.headerText = header ?: ""
		this.tvChannelQuery = query
		this.queryType = QueryType.LiveTvChannel
	}

	constructor(header: String?, query: GetRecommendedProgramsRequest) {
		this.headerText = header ?: ""
		this.programQuery = query
		this.queryType = QueryType.LiveTvProgram
	}

	@JvmOverloads
	constructor(
		header: String?,
		query: GetRecordingsRequest,
		chunkSize: Int = 0
	) {
		this.headerText = header ?: ""
		this.recordingQuery = query
		this.chunkSize = chunkSize
		this.queryType = QueryType.LiveTvRecording
	}

	constructor(
		header: String?,
		query: GetSimilarItemsRequest,
		type: QueryType
	) {
		this.headerText = header ?: ""
		this.similarQuery = query
		this.queryType = type
	}

	constructor(
		header: String?,
		query: GetResumeItemsRequest,
		chunkSize: Int,
		preferParentThumb: Boolean,
		staticHeight: Boolean,
		changeTriggers: Array<ChangeTriggerType>
	) {
		this.headerText = header ?: ""
		this.resumeQuery = query
		this.chunkSize = chunkSize
		this.queryType = QueryType.Resume
		this.isStaticHeight = staticHeight
		this.preferParentThumb = preferParentThumb
		this.changeTriggers = changeTriggers
	}

	// Constructor for merged Continue Watching + Next Up
	constructor(
		header: String?,
		resumeQuery: GetResumeItemsRequest,
		nextUpQuery: GetNextUpRequest,
		preferParentThumb: Boolean,
		staticHeight: Boolean,
		changeTriggers: Array<ChangeTriggerType>
	) {
		this.headerText = header ?: ""
		this.resumeQuery = resumeQuery
		this.mergedNextUpQuery = nextUpQuery
		this.queryType = QueryType.MergedContinueWatching
		this.isStaticHeight = staticHeight
		this.preferParentThumb = preferParentThumb
		this.changeTriggers = changeTriggers
	}

	constructor(header: String?, query: GetSpecialsRequest) {
		this.headerText = header ?: ""
		this.specialsQuery = query
		this.queryType = QueryType.Specials
	}

	fun setSectionType(type: HomeSectionType) {
		this.sectionType = type
	}
}
