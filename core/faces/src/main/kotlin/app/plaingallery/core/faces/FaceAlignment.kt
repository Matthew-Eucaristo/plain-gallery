package app.plaingallery.core.faces

/** Geometry for turning five landmarks into the upright 112×112 crop SFace expects. */
object FaceAlignment {
    const val SIZE = 112

    /** Where the five landmarks land in the crop (the ArcFace template OpenCV's `FaceRecognizerSF` uses). */
    private val TEMPLATE = floatArrayOf(
        38.2946f, 51.6963f,
        73.5318f, 51.5014f,
        56.0252f, 71.7366f,
        41.5493f, 92.3655f,
        70.7299f, 92.2041f,
    )

    /**
     * Least-squares similarity transform (rotation, uniform scale, translation; no reflection) from
     * [landmarks] to the template, as a row-major 2×3 matrix `[a, -b, tx, b, a, ty]`.
     */
    fun similarity(landmarks: FloatArray): FloatArray {
        require(landmarks.size == 10)
        var msx = 0.0
        var msy = 0.0
        var mdx = 0.0
        var mdy = 0.0
        for (k in 0 until 5) {
            msx += landmarks[2 * k]
            msy += landmarks[2 * k + 1]
            mdx += TEMPLATE[2 * k]
            mdy += TEMPLATE[2 * k + 1]
        }
        msx /= 5; msy /= 5; mdx /= 5; mdy /= 5
        var variance = 0.0
        var sa = 0.0
        var sb = 0.0
        for (k in 0 until 5) {
            val sx = landmarks[2 * k] - msx
            val sy = landmarks[2 * k + 1] - msy
            val dx = TEMPLATE[2 * k] - mdx
            val dy = TEMPLATE[2 * k + 1] - mdy
            variance += sx * sx + sy * sy
            sa += dx * sx + dy * sy
            sb += dy * sx - dx * sy
        }
        if (variance == 0.0) return floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f)
        val a = sa / variance
        val b = sb / variance
        val tx = mdx - (a * msx - b * msy)
        val ty = mdy - (b * msx + a * msy)
        return floatArrayOf(a.toFloat(), (-b).toFloat(), tx.toFloat(), b.toFloat(), a.toFloat(), ty.toFloat())
    }

    /**
     * Where the nose sits along the line between the eyes: about 0.5 for a face looking at the camera,
     * towards 0 or 1 (and beyond) as it turns into profile. Profile faces make poor embeddings.
     */
    fun noseOffset(landmarks: FloatArray): Float {
        val vx = landmarks[2] - landmarks[0]
        val vy = landmarks[3] - landmarks[1]
        val length = vx * vx + vy * vy
        if (length == 0f) return 0.5f
        return ((landmarks[4] - landmarks[0]) * vx + (landmarks[5] - landmarks[1]) * vy) / length
    }

    /** Whether a detection is good enough to recognize: confident, big enough and not in profile. */
    fun isRecognizable(face: DetectedFace, scale: Float): Boolean =
        face.score >= MIN_SCORE &&
            minOf(face.width, face.height) / scale >= MIN_FACE_PX &&
            noseOffset(face.landmarks) in MIN_NOSE..MAX_NOSE

    /** Measured on labeled photos: real faces score ≥ 0.6, frontal noses fall in 0.18–0.85. */
    const val MIN_SCORE = 0.7f
    const val MIN_FACE_PX = 40f
    const val MIN_NOSE = 0.1f
    const val MAX_NOSE = 0.9f
}
