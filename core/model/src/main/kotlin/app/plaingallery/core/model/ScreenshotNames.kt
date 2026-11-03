package app.plaingallery.core.model

/**
 * Which app a screenshot shows, read from its file name. Phones that record it:
 * - Samsung: `Screenshot_20241103_101010_Instagram.jpg` (the app's name)
 * - Xiaomi: `Screenshot_2024-11-03-10-10-10-123_com.instagram.android.jpg` (package)
 * - Huawei, Honor: `Screenshot_20241103_101010_com.instagram.android.jpg` (package)
 * Pixel and others name screenshots by date only; then there is nothing to read.
 */
sealed interface ScreenshotApp {
    data class Label(val name: String) : ScreenshotApp
    data class Package(val name: String) : ScreenshotApp

    companion object {
        private val PREFIX = Regex("^(screenshot|screen_shot|screen shot)[ _-]*", RegexOption.IGNORE_CASE)
        private val TIMESTAMP = Regex("^[0-9][0-9_. -]*")
        private val COPY_SUFFIX = Regex("\\s*[(\\[]\\d+[)\\]]$|[_-]\\d+$")
        private val PACKAGE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
        private val HEX = Regex("^[0-9a-fA-F]{16,}$")

        fun parse(displayName: String): ScreenshotApp? {
            val base = displayName.substringBeforeLast('.').takeIf { displayName.contains('.') } ?: displayName
            val afterPrefix = PREFIX.find(base)?.let { base.substring(it.range.last + 1) } ?: return null
            val rest = afterPrefix.replaceFirst(TIMESTAMP, "").trim('_', '-', ' ').replace(COPY_SUFFIX, "").trim('_', '-', ' ')
            return when {
                rest.isEmpty() || HEX.matches(rest) -> null
                PACKAGE.matches(rest) -> Package(rest)
                rest.any { it.isLetter() } -> Label(rest.replace('_', ' '))
                else -> null
            }
        }

        /** Names for packages the system may not tell us about (apps the user has since removed). */
        private val KNOWN = mapOf(
            "com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp Business",
            "com.instagram.android" to "Instagram", "com.facebook.katana" to "Facebook", "com.facebook.orca" to "Messenger",
            "com.zhiliaoapp.musically" to "TikTok", "com.ss.android.ugc.trill" to "TikTok", "com.twitter.android" to "X",
            "com.google.android.youtube" to "YouTube", "com.android.chrome" to "Chrome", "org.telegram.messenger" to "Telegram",
            "com.google.android.apps.maps" to "Maps", "com.google.android.gm" to "Gmail", "com.shopee.id" to "Shopee",
            "com.tokopedia.tkpd" to "Tokopedia", "com.gojek.app" to "Gojek", "com.grabtaxi.passenger" to "Grab",
            "id.dana" to "DANA", "com.nianticlabs.pokemongo" to "Pokémon GO", "com.spotify.music" to "Spotify",
            "com.lazada.android" to "Lazada", "com.traveloka.android" to "Traveloka", "com.bca" to "BCA mobile",
            "com.miui.home" to "Home screen", "com.sec.android.app.launcher" to "Home screen",
        )

        /** A readable name for a package when the system has none: known apps, else its most telling part. */
        fun fallbackLabel(packageName: String): String = KNOWN[packageName] ?: packageName.split('.')
            .filter { it.lowercase() !in GENERIC && it.any(Char::isLetter) }
            .let { parts -> (parts.firstOrNull { it.length > 2 } ?: parts.lastOrNull() ?: packageName) }
            .replaceFirstChar { it.uppercase() }

        private val GENERIC = setOf("com", "org", "net", "id", "co", "android", "app", "apps", "mobile", "google", "client", "main")
    }
}
