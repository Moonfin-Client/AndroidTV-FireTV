package org.moonfin.server.emby

import org.emby.client.api.DisplayPreferencesServiceApi
import org.emby.client.api.InstantMixServiceApi
import org.emby.client.api.ItemsServiceApi
import org.emby.client.api.LibraryServiceApi
import org.emby.client.api.LiveTvServiceApi
import org.emby.client.api.MediaInfoServiceApi
import org.emby.client.api.PlaystateServiceApi
import org.emby.client.api.SessionsServiceApi
import org.emby.client.api.TvShowsServiceApi
import org.emby.client.api.UserLibraryServiceApi
import org.emby.client.api.UserServiceApi
import org.emby.client.api.UserViewsServiceApi
import org.emby.client.model.AuthenticateUserByName

data class EmbyUserInfo(
    val id: String,
    val name: String?,
    val serverId: String?,
    val primaryImageTag: String?,
    val hasPassword: Boolean?,
    val hasConfiguredPassword: Boolean?,
)

data class EmbyAuthResult(
    val accessToken: String?,
    val user: EmbyUserInfo?,
    val serverId: String?,
)

class EmbyApiClient(
    private val appVersion: String,
    private val clientName: String,
    val deviceId: String,
    private val deviceName: String,
) {
    var baseUrl: String = ""
        private set
    var accessToken: String? = null
        private set
    var userId: String? = null
        private set

    private var userService: UserServiceApi? = null
    private var sessionsService: SessionsServiceApi? = null
    var itemsService: ItemsServiceApi? = null
        private set
    var userLibraryService: UserLibraryServiceApi? = null
        private set
    var tvShowsService: TvShowsServiceApi? = null
        private set
    var libraryService: LibraryServiceApi? = null
        private set
    var playstateService: PlaystateServiceApi? = null
        private set
    var userViewsService: UserViewsServiceApi? = null
        private set
    var liveTvService: LiveTvServiceApi? = null
        private set
    var instantMixService: InstantMixServiceApi? = null
        private set
    var displayPreferencesService: DisplayPreferencesServiceApi? = null
        private set
    var mediaInfoService: MediaInfoServiceApi? = null
        private set

    fun configure(baseUrl: String, accessToken: String?, userId: String?) {
        this.baseUrl = baseUrl
        this.accessToken = accessToken
        this.userId = userId
        if (baseUrl.isEmpty()) {
            userService = null
            sessionsService = null
            itemsService = null
            userLibraryService = null
            tvShowsService = null
            libraryService = null
            playstateService = null
            userViewsService = null
            liveTvService = null
            instantMixService = null
            displayPreferencesService = null
            mediaInfoService = null
            return
        }
        userService = UserServiceApi(baseUrl = baseUrl).also { if (accessToken != null) it.setApiKey(accessToken) }
        sessionsService = SessionsServiceApi(baseUrl = baseUrl).also { if (accessToken != null) it.setApiKey(accessToken) }
        itemsService = ItemsServiceApi(baseUrl = baseUrl).also { if (accessToken != null) it.setApiKey(accessToken) }
        userLibraryService = UserLibraryServiceApi(baseUrl = baseUrl).also { if (accessToken != null) it.setApiKey(accessToken) }
        tvShowsService = TvShowsServiceApi(baseUrl = baseUrl).also { if (accessToken != null) it.setApiKey(accessToken) }
        libraryService = LibraryServiceApi(baseUrl = baseUrl).also { if (accessToken != null) it.setApiKey(accessToken) }
        playstateService = PlaystateServiceApi(baseUrl = baseUrl).also { if (accessToken != null) it.setApiKey(accessToken) }
        userViewsService = UserViewsServiceApi(baseUrl = baseUrl).also { if (accessToken != null) it.setApiKey(accessToken) }
        liveTvService = LiveTvServiceApi(baseUrl = baseUrl).also { if (accessToken != null) it.setApiKey(accessToken) }
        instantMixService = InstantMixServiceApi(baseUrl = baseUrl).also { if (accessToken != null) it.setApiKey(accessToken) }
        displayPreferencesService = DisplayPreferencesServiceApi(baseUrl = baseUrl).also { if (accessToken != null) it.setApiKey(accessToken) }
        mediaInfoService = MediaInfoServiceApi(baseUrl = baseUrl).also { if (accessToken != null) it.setApiKey(accessToken) }
    }

    fun reset() = configure("", null, null)

    fun buildAuthHeader(token: String? = null): String = buildString {
        append("Emby Client=\"$clientName\"")
        append(", Device=\"$deviceName\"")
        append(", DeviceId=\"$deviceId\"")
        append(", Version=\"$appVersion\"")
        val t = token ?: accessToken
        if (t != null) append(", Token=\"$t\"")
    }

    suspend fun validateCurrentUser(): EmbyUserInfo {
        val id = userId ?: error("EmbyApiClient: userId not configured")
        val dto = userService!!.getUsersById(id).body()
        return EmbyUserInfo(
            id = dto.id ?: id,
            name = dto.name,
            serverId = dto.serverId,
            primaryImageTag = dto.primaryImageTag,
            hasPassword = dto.hasPassword,
            hasConfiguredPassword = dto.hasConfiguredPassword,
        )
    }

    suspend fun authenticateByName(username: String, password: String): EmbyAuthResult {
        val body = AuthenticateUserByName(username = username, pw = password)
        val result = UserServiceApi(baseUrl = baseUrl).postUsersAuthenticatebyname(buildAuthHeader(), body).body()
        val userDto = result.user
        return EmbyAuthResult(
            accessToken = result.accessToken,
            serverId = result.serverId,
            user = userDto?.let {
                EmbyUserInfo(
                    id = it.id ?: "",
                    name = it.name,
                    serverId = it.serverId,
                    primaryImageTag = it.primaryImageTag,
                    hasPassword = it.hasPassword,
                    hasConfiguredPassword = it.hasConfiguredPassword,
                )
            },
        )
    }

    suspend fun postCapabilities(
        playableMediaTypes: String,
        supportedCommands: String,
        supportsMediaControl: Boolean,
    ) {
        sessionsService?.postSessionsCapabilities(
            id = "",
            playableMediaTypes = playableMediaTypes,
            supportedCommands = supportedCommands,
            supportsMediaControl = supportsMediaControl,
            supportsSync = false,
        )
    }

    suspend fun logout() = runCatching { sessionsService?.postSessionsLogout() }

    val isConfigured: Boolean get() = baseUrl.isNotEmpty() && accessToken != null
}
