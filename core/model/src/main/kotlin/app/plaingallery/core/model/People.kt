package app.plaingallery.core.model

/** A face's bounds as fractions (0..1) of the upright image, so it holds at any decode size. */
data class FaceBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** One group of faces that belong to the same person, as clustered on this device. */
data class Person(
    val id: Long,
    /** Set by the user; unnamed people show their face only. */
    val name: String?,
    val isHidden: Boolean,
    /** Photos showing this person, counting only items that are not in Trash. */
    val mediaCount: Int,
    /** The photo and face shown as this person's avatar. */
    val coverMediaId: Long,
    val coverBox: FaceBox,
)

/**
 * Who appears in which photo. Built by the face index; [Library] combines it with the media so
 * counts and person collections only include live items.
 */
class People(
    /** Every known group, including small and hidden ones. */
    val groups: List<Person>,
    private val mediaByPerson: Map<Long, Set<Long>>,
) {
    /** Person ids per media id. */
    val personsByMedia: Map<Long, List<Long>> by lazy {
        val map = HashMap<Long, MutableList<Long>>()
        mediaByPerson.forEach { (person, media) -> media.forEach { map.getOrPut(it) { ArrayList(2) }.add(person) } }
        map
    }

    fun mediaIds(personId: Long): Set<Long> = mediaByPerson[personId].orEmpty()

    operator fun get(personId: Long): Person? = groups.firstOrNull { it.id == personId }

    companion object {
        val Empty = People(emptyList(), emptyMap())

        /** Unnamed groups need this many photos before they are shown; one-off faces are mostly strangers. */
        const val MIN_PHOTOS = 3

        /** Names compare without case or surrounding spaces, so "budi " and "Budi" are the same person. */
        fun sameName(a: String?, b: String?): Boolean =
            a != null && b != null && a.trim().equals(b.trim(), ignoreCase = true)
    }
}
