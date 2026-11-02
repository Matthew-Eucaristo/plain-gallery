package app.plaingallery.core.data.share

enum class ShareQuality {
    /** The file exactly as stored. */
    Original,

    /** Re-encodes formats some apps reject (HEIC, AVIF, WebP, PNG) as full-quality JPEG. */
    Compatible,

    /** A small JPEG (longest side 1600 px) without metadata, location included. */
    Small,
}
