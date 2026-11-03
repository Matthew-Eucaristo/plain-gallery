package app.plaingallery.core.model

import kotlin.math.sqrt

/**
 * Groups face embeddings (L2-normalized, compared by cosine similarity) into people, incrementally.
 *
 * Each new face joins the group whose mean embedding it is closest to, if that is at least
 * [assignThreshold]; otherwise it starts a new group. Because early groups are formed from few faces,
 * [merge] later joins groups whose means have converged. Thresholds are deliberately strict: two
 * people mixed up is worse than one person split in two, which the user fixes by giving both the same name.
 *
 * Thresholds were checked against SFace on labeled pairs: same person ≥ 0.52, different people ≤ 0.37.
 * Not thread-safe; one indexer owns it.
 */
class FaceClusterer(
    private val dimensions: Int = 128,
    private val assignThreshold: Float = ASSIGN_THRESHOLD,
    private val mergeThreshold: Float = MERGE_THRESHOLD,
) {
    private class Group(dimensions: Int) {
        val sum = FloatArray(dimensions)
        val centroid = FloatArray(dimensions)
        var count = 0

        fun update() {
            var norm = 0f
            for (v in sum) norm += v * v
            norm = sqrt(norm)
            for (i in sum.indices) centroid[i] = if (norm > 0f) sum[i] / norm else 0f
        }
    }

    private val groups = LinkedHashMap<Long, Group>()
    private var nextId = 1L

    val groupCount: Int get() = groups.size

    fun size(id: Long): Int = groups[id]?.count ?: 0

    /** Restores a face that was already grouped (e.g. loaded from the index). */
    fun add(id: Long, embedding: FloatArray) {
        require(id > 0) { "Group ids are positive" }
        val group = groups.getOrPut(id) { Group(dimensions) }
        accumulate(group, embedding, +1f)
        if (id >= nextId) nextId = id + 1
    }

    /** Takes a face out of its group, e.g. when the user says it is not this person. */
    fun remove(id: Long, embedding: FloatArray) {
        val group = groups[id] ?: return
        accumulate(group, embedding, -1f)
        if (group.count <= 0) groups.remove(id)
    }

    /** Adds a new face and returns its group, existing or new. */
    fun assign(embedding: FloatArray): Long {
        var bestId = 0L
        var best = -1f
        for ((id, group) in groups) {
            val similarity = dot(group.centroid, embedding)
            if (similarity > best) {
                best = similarity
                bestId = id
            }
        }
        val id = if (bestId != 0L && best >= assignThreshold) bestId else nextId++
        add(id, embedding)
        return id
    }

    /** Cosine similarity of a face to a group's mean, or null if there is no such group. */
    fun similarity(id: Long, embedding: FloatArray): Float? = groups[id]?.let { dot(it.centroid, embedding) }

    /**
     * Joins groups whose means are at least [mergeThreshold] apart. Larger groups absorb smaller ones unless
     * [prefer] says otherwise (e.g. a named group keeps its id); [canMerge] can veto a pair (two different
     * names). Returns every absorbed id mapped to the id it now belongs to.
     */
    fun merge(
        canMerge: (Long, Long) -> Boolean = { _, _ -> true },
        prefer: (Long, Long) -> Long = { a, _ -> a },
    ): Map<Long, Long> {
        val moves = LinkedHashMap<Long, Long>()
        repeat(MAX_MERGE_SWEEPS) {
            var changed = false
            val ids = groups.keys.sortedByDescending { groups.getValue(it).count }
            val gone = HashSet<Long>()
            for (i in ids.indices) {
                var a = ids[i]
                if (a in gone) continue
                for (j in i + 1 until ids.size) {
                    val b = ids[j]
                    if (b in gone || a in gone) continue
                    val ga = groups[a] ?: break
                    val gb = groups[b] ?: continue
                    if (dot(ga.centroid, gb.centroid) < mergeThreshold || !canMerge(a, b)) continue
                    val keep = prefer(a, b).takeIf { it == a || it == b } ?: a
                    val drop = if (keep == a) b else a
                    val into = groups.getValue(keep)
                    val from = groups.getValue(drop)
                    for (k in into.sum.indices) into.sum[k] += from.sum[k]
                    into.count += from.count
                    into.update()
                    groups.remove(drop)
                    gone += drop
                    moves.entries.forEach { if (it.value == drop) it.setValue(keep) }
                    moves[drop] = keep
                    changed = true
                    a = keep
                }
            }
            if (!changed) return moves
        }
        return moves
    }

    /** Moves every face of [from] into [into] (the user named both the same). */
    fun join(from: Long, into: Long) {
        if (from == into) return
        val source = groups.remove(from) ?: return
        val target = groups.getOrPut(into) { Group(dimensions) }
        for (k in target.sum.indices) target.sum[k] += source.sum[k]
        target.count += source.count
        target.update()
        if (into >= nextId) nextId = into + 1
    }

    /** A fresh id that no group uses, for a face the user moved out on its own. */
    fun newId(): Long = nextId++

    private fun accumulate(group: Group, embedding: FloatArray, sign: Float) {
        require(embedding.size == dimensions) { "Expected $dimensions dimensions, got ${embedding.size}" }
        for (i in group.sum.indices) group.sum[i] += sign * embedding[i]
        group.count += sign.toInt()
        group.update()
    }

    companion object {
        const val ASSIGN_THRESHOLD = 0.48f
        const val MERGE_THRESHOLD = 0.55f
        private const val MAX_MERGE_SWEEPS = 3

        fun dot(a: FloatArray, b: FloatArray): Float {
            var s = 0f
            for (i in a.indices) s += a[i] * b[i]
            return s
        }

        /** Scales [v] to unit length in place and returns it. */
        fun normalize(v: FloatArray): FloatArray {
            var norm = 0f
            for (x in v) norm += x * x
            norm = sqrt(norm)
            if (norm > 0f) for (i in v.indices) v[i] /= norm
            return v
        }
    }
}
