package com.wilmer.reproductor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarityTest {

    private data class V(val name: String, val size: Long)

    private val mb = 1L shl 20

    @Test
    fun dentroYFueraDeLaTolerancia() {
        assertTrue(Similarity.isSimilar(105 * mb, 100 * mb, 0.10))
        assertTrue(Similarity.isSimilar(90 * mb, 100 * mb, 0.10))
        assertFalse(Similarity.isSimilar(115 * mb, 100 * mb, 0.10))
        // Sin duración conocida (0) nunca es parecido
        assertFalse(Similarity.isSimilar(0, 100, 0.10))
    }

    @Test
    fun parecidosOrdenadosPorCercania() {
        val items = listOf(V("a", 120 * mb), V("b", 98 * mb), V("c", 108 * mb), V("d", 300 * mb))
        val result = Similarity.similarTo(items, 100 * mb, 0.10) { it.size }
        assertEquals(listOf("b", "c"), result.map { it.name })
    }

    @Test
    fun gruposDeCopiasRepetidas() {
        val items = listOf(
            V("peli1", 1400 * mb), V("peli1-copia", 1410 * mb),
            V("solo", 700 * mb),
            V("clip", 50 * mb), V("clip2", 52 * mb), V("clip3", 54 * mb),
            V("vacio", 0),
        )
        val groups = Similarity.groups(items, 0.10) { it.size }
        assertEquals(
            listOf(listOf("peli1", "peli1-copia"), listOf("clip", "clip2", "clip3")),
            groups.map { g -> g.map { it.name } },
        )
    }
}
