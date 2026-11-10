package com.wilmer.reproductor.data

import kotlin.math.abs

/**
 * Videos parecidos por peso o duración. No depende de Android para poder probarlo:
 * recibe cualquier lista y una función que da el valor a comparar (bytes o milisegundos).
 */
object Similarity {

    /** El valor está dentro de ±tolerancia (0.10 = 10 %) del valor de referencia. */
    fun isSimilar(value: Long, reference: Long, tolerance: Double): Boolean =
        value > 0 && reference > 0 && abs(value - reference) <= reference * tolerance

    /** Los que se parecen a la referencia, del más parecido al menos parecido. */
    fun <T> similarTo(items: List<T>, reference: Long, tolerance: Double, value: (T) -> Long): List<T> =
        items.filter { isSimilar(value(it), reference, tolerance) }.sortedBy { abs(value(it) - reference) }

    /**
     * Grupos de al menos dos videos con valores parecidos entre sí (posibles copias repetidas).
     * Cada grupo empieza en su valor más pequeño y llega hasta ese valor + tolerancia.
     * Los grupos de valores más grandes van primero.
     */
    fun <T> groups(items: List<T>, tolerance: Double, value: (T) -> Long): List<List<T>> {
        val sorted = items.filter { value(it) > 0 }.sortedBy(value)
        val result = mutableListOf<List<T>>()
        var current = mutableListOf<T>()
        var start = 0L
        for (item in sorted) {
            val v = value(item)
            if (current.isNotEmpty() && v <= start + start * tolerance) {
                current.add(item)
            } else {
                if (current.size >= 2) result.add(current)
                current = mutableListOf(item)
                start = v
            }
        }
        if (current.size >= 2) result.add(current)
        return result.sortedByDescending { value(it.first()) }
    }
}
