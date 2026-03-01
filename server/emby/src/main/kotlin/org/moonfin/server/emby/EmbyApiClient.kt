package org.moonfin.server.emby

import org.emby.client.api.SessionsServiceApi
import org.emby.client.api.UserServiceApi
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
    private val deviceId: String,
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

    fun configure(baseUrl: String, accessToken: String?, userId: String?) {
        this.baseUrl = baseUrl
        this.accessToken = accessToken
        this.userId = userId
        if (baseUrl.isEmpty()) {
            userService = null
            sessionsService = null
            return
        }
        userService = UserServiceApi(baseUrl = baseUrl).also { api ->
            if (accessToken != null) api.setBearerToken(accessToken)
        }
        sessionsService = SessionsServiceApi(baseUrl = baseUrl).also { api ->
            if (accessToken != null) api.setBearerToken(accessToken)
        }
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

    suspend fun logout() = runCatching { sessionsService?.postSessionsLogout() }

    val isConfigured: Boolean get() = baseUrl.isNotEmpty() && accessToken != null
}
