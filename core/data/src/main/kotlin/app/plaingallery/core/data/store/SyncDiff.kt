package app.plaingallery.core.data.store

/** What changed between the local index and MediaStore, keyed by `_ID` → `GENERATION_MODIFIED`. */
internal data class SyncDiff(val changed: List<Long>, val removed: List<Long>) {
    val isEmpty: Boolean get() = changed.isEmpty() && removed.isEmpty()

    companion object {
        fun between(local: Map<Long, Long>, remote: Map<Long, Long>): SyncDiff = SyncDiff(
            changed = remote.mapNotNull { (id, generation) -> id.takeIf { local[id] != generation } },
            removed = local.keys.filter { it !in remote },
        )
    }
}
