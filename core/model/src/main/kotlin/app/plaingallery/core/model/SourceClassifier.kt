package app.plaingallery.core.model

/** Derives a [MediaSource] from MediaStore columns. Pure and cheap; runs once per file at sync. */
object SourceClassifier {
    private val whatsAppName = Regex("""-WA\d{4}""")
    private val whatsAppPackages = setOf("com.whatsapp", "com.whatsapp.w4b")

    fun classify(relativePath: String, ownerPackage: String?, displayName: String): MediaSource {
        val path = relativePath.lowercase()
        return when {
            "screenshot" in path || "screenrecord" in path || "screen recording" in path ||
                "screen_record" in path -> MediaSource.Screenshot
            "com.whatsapp" in path || "whatsapp" in path || ownerPackage in whatsAppPackages ||
                whatsAppName.containsMatchIn(displayName) -> MediaSource.WhatsApp
            "telegram" in path || ownerPackage?.startsWith("org.telegram") == true -> MediaSource.Telegram
            path.startsWith("download/") -> MediaSource.Download
            path.startsWith("dcim/") -> MediaSource.Camera
            else -> MediaSource.Other
        }
    }
}
