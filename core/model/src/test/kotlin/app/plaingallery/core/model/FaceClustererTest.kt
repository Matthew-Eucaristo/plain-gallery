package app.plaingallery.core.model

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceClustererTest {
    private val random = Random(7)

    private fun unit(): FloatArray = FaceClusterer.normalize(FloatArray(128) { random.nextFloat() * 2 - 1 })

    /** A face of the person at [identity]: their direction plus noise, cosine ≈ 0.75 to the identity. */
    private fun face(identity: FloatArray, noise: Float = 0.9f): FloatArray {
        val n = unit()
        return FaceClusterer.normalize(FloatArray(128) { identity[it] + noise * n[it] })
    }

    @Test fun separatesPeopleAndGroupsTheirFaces() {
        val people = List(5) { unit() }
        val clusterer = FaceClusterer()
        val faces = people.indices.flatMap { p -> List(20) { p to face(people[p]) } }.shuffled(random)
        val assigned = faces.map { (p, e) -> p to clusterer.assign(e) }
        val moves = clusterer.merge()
        val final = assigned.map { (p, id) -> p to (moves[id] ?: id) }
        // Every group holds one person only.
        final.groupBy({ it.second }, { it.first }).values.forEach { assertEquals(1, it.toSet().size) }
        // And each person ends up (almost entirely) in one group.
        final.groupBy({ it.first }, { it.second }).values.forEach { ids ->
            val biggest = ids.groupingBy { it }.eachCount().values.max()
            assertTrue("person split: $ids", biggest >= 18)
        }
    }

    @Test fun mergeRespectsVetoAndPreference() {
        val person = unit()
        val clusterer = FaceClusterer(assignThreshold = 0.99f) // force one group per face
        val a = clusterer.assign(face(person, 0.3f))
        val b = clusterer.assign(face(person, 0.3f))
        assertNotEquals(a, b)
        assertTrue(clusterer.merge(canMerge = { _, _ -> false }).isEmpty())
        val moves = clusterer.merge(prefer = { _, _ -> b })
        assertEquals(mapOf(a to b), moves)
        assertEquals(2, clusterer.size(b))
    }

    @Test fun restoredGroupsKeepTheirIdsAndNewIdsDoNotCollide() {
        val clusterer = FaceClusterer()
        val person = unit()
        clusterer.add(42, face(person))
        assertEquals(42L, clusterer.assign(face(person)))
        val stranger = clusterer.assign(unit())
        assertTrue(stranger > 42)
        clusterer.remove(42, face(person))
        assertEquals(1, clusterer.size(42))
    }

    @Test fun joinMovesEveryFace() {
        val clusterer = FaceClusterer()
        clusterer.add(1, unit())
        clusterer.add(2, unit())
        clusterer.join(from = 2, into = 1)
        assertEquals(2, clusterer.size(1))
        assertEquals(1, clusterer.groupCount)
    }
}
