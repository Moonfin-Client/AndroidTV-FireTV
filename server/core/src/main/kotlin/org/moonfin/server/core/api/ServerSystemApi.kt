package org.moonfin.server.core.api

import org.moonfin.server.core.model.PublicSystemInfo
import org.moonfin.server.core.model.SystemInfo

interface ServerSystemApi {
    suspend fun getPublicSystemInfo(): PublicSystemInfo
    suspend fun getSystemInfo(): SystemInfo
}
