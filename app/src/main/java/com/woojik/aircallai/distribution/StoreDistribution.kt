package com.woojik.aircallai.distribution

/** Build channel describes the submission target, not the detected installer. */
object StoreDistribution {
    fun label(channel: String, development: Boolean): String = if (development) "개발용 앱" else when (channel) {
        "onestore" -> "원스토어용 앱"
        "play" -> "Google Play용 앱"
        else -> "직접 설치용 앱"
    }

    fun oneStoreProductUrl(productId: String): String? = productId.trim()
        .takeIf { it.matches(Regex("[0-9]{10}")) }
        ?.let { "https://onesto.re/$it" }
}
