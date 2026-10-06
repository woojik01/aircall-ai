package com.woojik.aircallai.privacy

import java.net.URI

object HttpsEndpoint {
    fun isHttpsEndpoint(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
    }.getOrDefault(false)
}
