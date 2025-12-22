package org.jellyfin.androidtv.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.androidtv.data.model.AggregatedItem
import org.jellyfin.androidtv.data.model.AggregatedLibrary
import org.jellyfin.androidtv.preference.LibraryPreferences
import org.jellyfin.androidtv.preference.PreferencesRepository
import org.jellyfin.androidtv.util.sdk.forUser
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Data class holding server, user, and API client information for multi-server operations.
 */
data class ServerUserSession(
	val server: Server,
	val userId: UUID,
	val apiClient: ApiClient
)

/**
 * Repository for aggregating data from multiple logged-in Jellyfin servers.
 * Enables displaying libraries and content from all servers simultaneously.
 */
interface MultiServerRepository {
	/**
	 * Get all servers that have logged-in users with valid authentication.
	 * Returns ServerUserSession containing server, user, and ApiClient info.
	 */
	suspend fun getLoggedInServers(): List<ServerUserSession>

	/**
	 * Aggregate libraries from all logged-in servers.
	 * Returns libraries with display names including server context.
	 * @param includeHidden If false, excludes libraries marked as hidden in preferences
	 */
	suspend fun getAggregatedLibraries(includeHidden: Boolean = false): List<AggregatedLibrary>

	/**
	 * Aggregate resume items (Continue Watching) from all logged-in servers.
	 * Sorted by most recent first across all servers.
	 */
	suspend fun getAggregatedResumeItems(limit: Int): List<AggregatedItem>

	/**
	 * Aggregate latest items (Recently Added) from all logged-in servers.
	 * Results are grouped by library and server.
	 * @param serverId Optional - if provided, only returns items from that specific server
	 */
	suspend fun getAggregatedLatestItems(parentId: UUID, limit: Int, serverId: UUID? = null): List<AggregatedItem>

	/**
	 * Aggregate next up items from all logged-in servers.
	 * Sorted by air date or most recent first.
	 */
	suspend fun getAggregatedNextUpItems(limit: Int): List<AggregatedItem>
}

class MultiServerRepositoryImpl(
	private val jellyfin: Jellyfin,
	private val serverRepository: ServerRepository,
	private val sessionRepository: SessionRepository,
	private val authenticationStore: AuthenticationStore,
	private val defaultDeviceInfo: DeviceInfo,
	private val userViewsRepository: UserViewsRepository,
	private val preferencesRepository: PreferencesRepository,
) : MultiServerRepository {

	companion object {
		// Timeout for each server query to prevent hanging
		private val SERVER_TIMEOUT = 8.seconds
	}

	private fun BaseItemDto.withServerId(serverId: UUID): BaseItemDto =
		copy(serverId = serverId.toString())

	override suspend fun getLoggedInServers(): List<ServerUserSession> = withContext(Dispatchers.IO) {
		val servers = serverRepository.storedServers.value
		Timber.d("MultiServerRepository: Checking ${servers.size} stored servers for logged-in users")

		val loggedInServers = servers.mapNotNull { server ->
			try {
				// Check if this server has any logged-in users
				val serverStore = authenticationStore.getServer(server.id)
				if (serverStore == null || serverStore.users.isEmpty()) {
					Timber.d("MultiServerRepository: Server ${server.name} has no stored users")
					return@mapNotNull null
				}

				// Find a user with valid access token for this server
				val userWithToken = serverStore.users.entries.find { (_, user) ->
					!user.accessToken.isNullOrBlank()
				}

				if (userWithToken == null) {
					Timber.d("MultiServerRepository: Server ${server.name} has no users with access tokens")
					return@mapNotNull null
				}

				val (userId, userInfo) = userWithToken
				Timber.d("MultiServerRepository: Found logged-in user ${userInfo.name} on server ${server.name}")

				// Create ApiClient for this server and user
				val deviceInfo = defaultDeviceInfo.forUser(userId)
				val apiClient = jellyfin.createApi(
					baseUrl = server.address,
					accessToken = userInfo.accessToken,
					deviceInfo = deviceInfo
				)

				ServerUserSession(server, userId, apiClient)
			} catch (e: Exception) {
				Timber.e(e, "MultiServerRepository: Error checking server ${server.name}")
				null
			}
		}

		// Fallback: if no stored servers found, try using the current session (single-server mode)
		if (loggedInServers.isEmpty()) {
			Timber.d("MultiServerRepository: No multi-server logins found, checking current session")
			val currentSession = sessionRepository.currentSession.value
			if (currentSession != null) {
				try {
					// Get server info for the current session
					val server = serverRepository.getServer(currentSession.serverId)
					if (server != null) {
						Timber.d("MultiServerRepository: Using current session for server ${server.name}")
						val deviceInfo = defaultDeviceInfo.forUser(currentSession.userId)
						val apiClient = jellyfin.createApi(
							baseUrl = server.address,
							accessToken = currentSession.accessToken,
							deviceInfo = deviceInfo
						)
						return@withContext listOf(ServerUserSession(server, currentSession.userId, apiClient))
					} else {
						Timber.w("MultiServerRepository: Current session server not found")
					}
				} catch (e: Exception) {
					Timber.e(e, "MultiServerRepository: Error creating session from current user")
				}
			} else {
				Timber.d("MultiServerRepository: No current session available")
			}
		}

		loggedInServers
	}

	override suspend fun getAggregatedLibraries(includeHidden: Boolean): List<AggregatedLibrary> = withContext(Dispatchers.IO) {
		val loggedInServers = getLoggedInServers()
		Timber.d("MultiServerRepository: Aggregating libraries from ${loggedInServers.size} servers")
		val hasMultipleServers = loggedInServers.size > 1

		loggedInServers.flatMap { session ->
			try {
				// Query libraries with timeout
				val libraries = withTimeoutOrNull(SERVER_TIMEOUT) {
					val response = session.apiClient.userViewsApi.getUserViews()
					response.content.items
						.filter { userViewsRepository.isSupported(it.collectionType) }
				}

				if (libraries == null) {
					Timber.w("MultiServerRepository: Timeout getting libraries from ${session.server.name}")
					return@flatMap emptyList()
				}

				Timber.d("MultiServerRepository: Got ${libraries.size} libraries from ${session.server.name}")

				// Filter out hidden libraries if requested (using LibraryPreferences per library)
				val filteredLibraries = if (!includeHidden) {
					libraries.filter { library ->
						val displayPreferencesId = library.displayPreferencesId
						if (displayPreferencesId != null) {
							val prefs = preferencesRepository.getLibraryPreferences(displayPreferencesId, session.apiClient)
							!prefs[LibraryPreferences.hidden]
						} else {
							true // Show libraries without displayPreferencesId
						}
					}
				} else {
					libraries
				}

				// Map to AggregatedLibrary with server context
				filteredLibraries.map { library ->
					AggregatedLibrary(
						library = library,
						server = session.server,
						userId = session.userId,
						displayName = if (hasMultipleServers) "${library.name} (${session.server.name})" else library.name ?: ""
					)
				}
			} catch (e: Exception) {
				Timber.e(e, "MultiServerRepository: Error getting libraries from ${session.server.name}")
				emptyList()
			}
		}.sortedWith(
			compareBy<AggregatedLibrary> { it.library.name }
				.thenBy { it.server.name }
		)
	}

	override suspend fun getAggregatedResumeItems(limit: Int): List<AggregatedItem> = withContext(Dispatchers.IO) {
		val loggedInServers = getLoggedInServers()
		Timber.d("MultiServerRepository: Aggregating resume items from ${loggedInServers.size} servers")

		// Request more items per server to ensure all servers are represented
		// Then limit after combining and sorting globally
		val perServerLimit = minOf(limit * 3, 100) // Get 10x per server to ensure good mix

		// Query all servers in parallel
		val allItems = loggedInServers.map { session ->
			async {
				try {
					withTimeoutOrNull(SERVER_TIMEOUT) {
						val query = GetResumeItemsRequest(
							limit = perServerLimit,
							fields = ItemRepository.itemFields,
							imageTypeLimit = 1,
							enableTotalRecordCount = false,
						)

						val response = session.apiClient.itemsApi.getResumeItems(query)
						
						Timber.d("MultiServerRepository: Got ${response.content.items.size} resume items from ${session.server.name}")

							response.content.items.map { item ->
							val itemWithServer = item.withServerId(session.server.id)
							Timber.d("MultiServerRepository: Resume item ${itemWithServer.id} from ${session.server.name} has serverId=${itemWithServer.serverId}")
							AggregatedItem(
								item = itemWithServer,
									server = session.server,
									userId = session.userId,
									apiClient = session.apiClient
								)
							}
					} ?: run {
						Timber.w("MultiServerRepository: Timeout getting resume items from ${session.server.name}")
						emptyList()
					}
				} catch (e: Exception) {
					Timber.e(e, "MultiServerRepository: Error getting resume items from ${session.server.name}")
					emptyList()
				}
			}
		}.awaitAll().flatten()

		// Sort by most recent (userData.lastPlayedDate) and take limit AFTER combining
		allItems.sortedByDescending { it.item.userData?.lastPlayedDate }
			.take(limit)
	}

	override suspend fun getAggregatedLatestItems(parentId: UUID, limit: Int, serverId: UUID?): List<AggregatedItem> = withContext(Dispatchers.IO) {
		val loggedInServers = getLoggedInServers()
			.let { servers -> serverId?.let { id -> servers.filter { it.server.id == id } } ?: servers }
		Timber.d("MultiServerRepository: Aggregating latest items for library $parentId from ${loggedInServers.size} servers")

		// Request more items per server to ensure all servers are represented
		// Then limit after combining and sorting globally
		val perServerLimit = minOf(limit * 3, 100) // Get 10x per server to ensure good mix

		// Query all servers in parallel
		val allItems = loggedInServers.map { session ->
			async {
				try {
					withTimeoutOrNull(SERVER_TIMEOUT) {
						val query = GetLatestMediaRequest(
							parentId = parentId,
							fields = ItemRepository.itemFields,
							imageTypeLimit = 1,
							limit = perServerLimit,
							groupItems = true,
						)

						val response = session.apiClient.userLibraryApi.getLatestMedia(query)
						
						Timber.d("MultiServerRepository: Got ${response.content.size} latest items from ${session.server.name}")

							response.content.map { item ->
							val itemWithServer = item.withServerId(session.server.id)
							Timber.d("MultiServerRepository: Latest item ${itemWithServer.id} from ${session.server.name} has serverId=${itemWithServer.serverId}")
							AggregatedItem(
								item = itemWithServer,
									server = session.server,
									userId = session.userId,
									apiClient = session.apiClient
								)
							}
					} ?: run {
						Timber.w("MultiServerRepository: Timeout getting latest items from ${session.server.name}")
						emptyList()
					}
				} catch (e: Exception) {
					Timber.e(e, "MultiServerRepository: Error getting latest items from ${session.server.name}")
					emptyList()
				}
			}
		}.flatMap { it.await() }

		// Sort by date created (most recent first) and take limit AFTER combining
		allItems.sortedByDescending { it.item.dateCreated }
			.take(limit)
	}

	override suspend fun getAggregatedNextUpItems(limit: Int): List<AggregatedItem> = withContext(Dispatchers.IO) {
		val loggedInServers = getLoggedInServers()
		Timber.d("MultiServerRepository: Aggregating next up items from ${loggedInServers.size} servers")

		// Request more items per server to ensure all servers are represented
		// Then limit after combining and sorting globally
		val perServerLimit = minOf(limit * 3, 100) // Get 10x per server to ensure good mix

		// Query all servers in parallel
		val allItems = loggedInServers.map { session ->
			async {
				try {
					withTimeoutOrNull(SERVER_TIMEOUT) {
						val query = GetNextUpRequest(
							imageTypeLimit = 1,
							limit = perServerLimit,
							fields = ItemRepository.itemFields,
						)

						val response = session.apiClient.tvShowsApi.getNextUp(query)
						
						Timber.d("MultiServerRepository: Got ${response.content.items.size} next up items from ${session.server.name}")

							response.content.items.map { item ->
								AggregatedItem(
									item = item.withServerId(session.server.id),
									server = session.server,
									userId = session.userId,
									apiClient = session.apiClient
								)
							}
					} ?: run {
						Timber.w("MultiServerRepository: Timeout getting next up items from ${session.server.name}")
						emptyList()
					}
				} catch (e: Exception) {
					Timber.e(e, "MultiServerRepository: Error getting next up items from ${session.server.name}")
					emptyList()
				}
			}
		}.flatMap { it.await() }

		// Sort by premiere date (most recent first) and take limit AFTER combining
		allItems.sortedWith(
			compareByDescending<AggregatedItem> { it.item.premiereDate }
				.thenByDescending { it.item.dateCreated }
		).take(limit)
	}
}
