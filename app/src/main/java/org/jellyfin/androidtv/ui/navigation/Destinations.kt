package org.jellyfin.androidtv.ui.navigation

import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.ui.browsing.AllFavoritesFragment
import org.jellyfin.androidtv.ui.browsing.BrowseGridFragment
import org.jellyfin.androidtv.ui.browsing.ByLetterFragment
import org.jellyfin.androidtv.ui.browsing.CollectionFragment
import org.jellyfin.androidtv.ui.browsing.FolderViewFragment
import org.jellyfin.androidtv.ui.browsing.GenericFolderFragment
import org.jellyfin.androidtv.ui.browsing.SuggestedMoviesFragment
import org.jellyfin.androidtv.ui.browsing.v2.GenresGridV2Fragment
import org.jellyfin.androidtv.ui.browsing.v2.LibraryBrowseFragment
import org.jellyfin.androidtv.ui.browsing.v2.LiveTvBrowseFragment
import org.jellyfin.androidtv.ui.browsing.v2.MusicBrowseFragment
import org.jellyfin.androidtv.ui.browsing.v2.RecordingsBrowseFragment
import org.jellyfin.androidtv.ui.browsing.v2.ScheduleBrowseFragment
import org.jellyfin.androidtv.ui.browsing.v2.SeriesRecordingsBrowseFragment
import org.jellyfin.androidtv.ui.home.HomeFragment
import org.jellyfin.androidtv.ui.itemdetail.FullDetailsFragment
import org.jellyfin.androidtv.ui.itemdetail.ItemListFragment
import org.jellyfin.androidtv.ui.itemdetail.v2.ItemDetailsFragment
import org.jellyfin.androidtv.ui.itemdetail.MusicFavoritesListFragment
import org.jellyfin.androidtv.ui.jellyseerr.BrowseFilterType
import org.jellyfin.androidtv.ui.jellyseerr.DiscoverFragment
import org.jellyfin.androidtv.ui.jellyseerr.JellyseerrBrowseByFragment
import org.jellyfin.androidtv.ui.jellyseerr.MediaDetailsFragment
import org.jellyfin.androidtv.ui.jellyseerr.PersonDetailsFragment
import org.jellyfin.androidtv.ui.jellyseerr.RequestsFragment
import org.jellyfin.androidtv.ui.jellyseerr.SettingsFragment as JellyseerrSettingsFragment
import org.jellyfin.androidtv.ui.livetv.LiveTvGuideFragment
import org.jellyfin.androidtv.ui.playback.AudioNowPlayingFragment
import org.jellyfin.androidtv.ui.playback.CustomPlaybackOverlayFragment
import org.jellyfin.androidtv.ui.playback.nextup.NextUpFragment
import org.jellyfin.androidtv.ui.playback.stillwatching.StillWatchingFragment
import org.jellyfin.androidtv.ui.player.photo.PhotoPlayerFragment
import org.jellyfin.androidtv.ui.player.video.VideoPlayerFragment
import org.jellyfin.androidtv.ui.search.SearchFragment
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SeriesTimerInfoDto
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID

@Suppress("TooManyFunctions")
object Destinations {
	// General
	val home = fragmentDestination<HomeFragment>()
	fun search(query: String? = null) = fragmentDestination<SearchFragment>(
		SearchFragment.EXTRA_QUERY to query,
	)

	// Browsing
	// TODO only pass item id instead of complete JSON to browsing destinations
	@JvmOverloads
	fun libraryBrowser(item: BaseItemDto, serverId: UUID? = null, userId: UUID? = null) = fragmentDestination<LibraryBrowseFragment>(
		Extras.Folder to Json.Default.encodeToString(item),
		"ServerId" to serverId?.toString(),
		"UserId" to userId?.toString(),
	)

	// TODO only pass item id instead of complete JSON to browsing destinations
	@JvmName("libraryBrowserWithType")
	fun libraryBrowser(item: BaseItemDto, includeType: String) =
		fragmentDestination<BrowseGridFragment>(
			Extras.Folder to Json.Default.encodeToString(item),
			Extras.IncludeType to includeType,
		)

	fun liveTvBrowser(item: BaseItemDto) = fragmentDestination<LiveTvBrowseFragment>(
		Extras.Folder to Json.Default.encodeToString(item),
	)

	@JvmOverloads
	fun musicBrowser(item: BaseItemDto, serverId: UUID? = null, userId: UUID? = null) = fragmentDestination<MusicBrowseFragment>(
		Extras.Folder to Json.Default.encodeToString(item),
		"ServerId" to serverId?.toString(),
		"UserId" to userId?.toString(),
	)

	// TODO only pass item id instead of complete JSON to browsing destinations
	@JvmOverloads
	fun collectionBrowser(item: BaseItemDto, serverId: UUID? = null, userId: UUID? = null) = fragmentDestination<CollectionFragment>(
		Extras.Folder to Json.Default.encodeToString(item),
		"ServerId" to serverId?.toString(),
		"UserId" to userId?.toString(),
	)

	// TODO only pass item id instead of complete JSON to browsing destinations
	@JvmOverloads
	fun folderBrowser(item: BaseItemDto, serverId: UUID? = null, userId: UUID? = null) = fragmentDestination<GenericFolderFragment>(
		Extras.Folder to Json.Default.encodeToString(item),
		"ServerId" to serverId?.toString(),
		"UserId" to userId?.toString(),
	)

	// All genres across all libraries (new grid view)
	val allGenres = fragmentDestination<GenresGridV2Fragment>()

	// All favorites across all libraries
	val allFavorites = fragmentDestination<AllFavoritesFragment>()

	// Folder view - browse by folder structure
	val folderView = fragmentDestination<FolderViewFragment>()

	// Browse items by genre (using the V2 library browser)
	fun genreBrowse(
		genreName: String,
		parentId: UUID? = null,
		includeType: String? = null,
		serverId: UUID? = null,
		displayPreferencesId: String? = null,
		parentItemId: UUID? = null,
	) = fragmentDestination<LibraryBrowseFragment>(
		LibraryBrowseFragment.ARG_GENRE_NAME to genreName,
		LibraryBrowseFragment.ARG_PARENT_ID to parentId?.toString(),
		LibraryBrowseFragment.ARG_INCLUDE_TYPE to includeType,
		LibraryBrowseFragment.ARG_SERVER_ID to serverId?.toString(),
		LibraryBrowseFragment.ARG_DISPLAY_PREFS_ID to displayPreferencesId,
		LibraryBrowseFragment.ARG_PARENT_ITEM_ID to parentItemId?.toString(),
	)

	// TODO only pass item id instead of complete JSON to browsing destinations
	fun libraryByGenres(item: BaseItemDto, includeType: String) =
		fragmentDestination<GenresGridV2Fragment>(
			Extras.Folder to Json.Default.encodeToString(item),
			Extras.IncludeType to includeType,
		)

	// TODO only pass item id instead of complete JSON to browsing destinations
	fun libraryByLetter(item: BaseItemDto, includeType: String) =
		fragmentDestination<ByLetterFragment>(
			Extras.Folder to Json.Default.encodeToString(item),
			Extras.IncludeType to includeType,
		)

	// TODO only pass item id instead of complete JSON to browsing destinations
	fun librarySuggestions(item: BaseItemDto) =
		fragmentDestination<SuggestedMoviesFragment>(
			Extras.Folder to Json.Default.encodeToString(item),
		)

	// Item details
	@JvmOverloads
	fun itemDetails(item: UUID, serverId: UUID? = null) = fragmentDestination<ItemDetailsFragment>(
		"ItemId" to item.toString(),
		"ServerId" to serverId?.toString(),
	)

	@JvmOverloads
	fun itemDetailsLegacy(item: UUID, serverId: UUID? = null) = fragmentDestination<FullDetailsFragment>(
		"ItemId" to item.toString(),
		"ServerId" to serverId?.toString(),
	)

	// TODO only pass item id instead of complete JSON to browsing destinations
	fun channelDetails(item: UUID, channel: UUID, programInfo: BaseItemDto) =
		fragmentDestination<FullDetailsFragment>(
			"ItemId" to item.toString(),
			"ChannelId" to channel.toString(),
			"ProgramInfo" to Json.Default.encodeToString(programInfo),
		)

	// TODO only pass item id instead of complete JSON to browsing destinations
	fun seriesTimerDetails(item: UUID, seriesTimer: SeriesTimerInfoDto) =
		fragmentDestination<FullDetailsFragment>(
			"ItemId" to item.toString(),
			"SeriesTimer" to Json.Default.encodeToString(seriesTimer),
		)

	@JvmOverloads
	fun itemList(item: UUID, serverId: UUID? = null) = fragmentDestination<ItemListFragment>(
		"ItemId" to item.toString(),
		"ServerId" to serverId?.toString(),
	)

	fun musicFavorites(parent: UUID) = fragmentDestination<MusicFavoritesListFragment>(
		"ParentId" to parent.toString(),
	)

	// Live TV
	val liveTvGuide = fragmentDestination<LiveTvGuideFragment>()
	val liveTvSchedule = fragmentDestination<ScheduleBrowseFragment>()
	val liveTvRecordings = fragmentDestination<RecordingsBrowseFragment>()
	val liveTvSeriesRecordings = fragmentDestination<SeriesRecordingsBrowseFragment>()

	// Playback
	val nowPlaying = fragmentDestination<AudioNowPlayingFragment>()

	fun photoPlayer(
		item: UUID,
		autoPlay: Boolean,
		albumSortBy: ItemSortBy?,
		albumSortOrder: SortOrder?,
	) = fragmentDestination<PhotoPlayerFragment>(
		PhotoPlayerFragment.ARGUMENT_ITEM_ID to item.toString(),
		PhotoPlayerFragment.ARGUMENT_ALBUM_SORT_BY to albumSortBy?.serialName,
		PhotoPlayerFragment.ARGUMENT_ALBUM_SORT_ORDER to albumSortOrder?.serialName,
		PhotoPlayerFragment.ARGUMENT_AUTO_PLAY to autoPlay,
	)

	fun videoPlayer(position: Int?) = fragmentDestination<CustomPlaybackOverlayFragment>(
		"Position" to (position ?: 0)
	)

	fun videoPlayerNew(position: Int?) = fragmentDestination<VideoPlayerFragment>(
		VideoPlayerFragment.EXTRA_POSITION to position
	)

	fun nextUp(item: UUID) = fragmentDestination<NextUpFragment>(
		NextUpFragment.ARGUMENT_ITEM_ID to item.toString()
	)

	fun stillWatching(item: UUID) = fragmentDestination<StillWatchingFragment>(
		NextUpFragment.ARGUMENT_ITEM_ID to item.toString()
	)

	// Jellyseerr
	val jellyseerrDiscover = fragmentDestination<DiscoverFragment>()
	val jellyseerrRequests = fragmentDestination<RequestsFragment>()
	val jellyseerrSettings = fragmentDestination<JellyseerrSettingsFragment>()
	
	fun jellyseerrBrowseBy(
		filterId: Int, 
		filterName: String, 
		mediaType: String,
		filterType: BrowseFilterType = BrowseFilterType.GENRE
	) = fragmentDestination<JellyseerrBrowseByFragment>(
		"filter_id" to filterId,
		"filter_name" to filterName,
		"media_type" to mediaType,
		"filter_type" to filterType.name,
	)
	
	// Convenience methods for specific filter types
	fun jellyseerrBrowseByGenre(genreId: Int, genreName: String, mediaType: String) = 
		jellyseerrBrowseBy(genreId, genreName, mediaType, BrowseFilterType.GENRE)
	
	fun jellyseerrBrowseByNetwork(networkId: Int, networkName: String) = 
		jellyseerrBrowseBy(networkId, networkName, "tv", BrowseFilterType.NETWORK)
	
	fun jellyseerrBrowseByStudio(studioId: Int, studioName: String) = 
		jellyseerrBrowseBy(studioId, studioName, "movie", BrowseFilterType.STUDIO)
	
	fun jellyseerrMediaDetails(itemJson: String) = fragmentDestination<MediaDetailsFragment>(
		"item" to itemJson
	)
	
	fun jellyseerrPersonDetails(personId: Int) = fragmentDestination<PersonDetailsFragment>(
		"personId" to personId.toString()
	)
}
