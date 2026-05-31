package com.fran.latticelines

import kotlin.math.roundToInt
import kotlin.math.sqrt

fun gridPoints(rows: Int = 10, cols: Int = 10): List<Point> =
    (0 until rows).flatMap { y -> (0 until cols).map { x -> Point(x, y) } }

fun allSegments(points: List<Point>): List<Segment> = buildList {
    for (i in points.indices) {
        for (j in i + 1 until points.size) {
            add(Segment(points[i], points[j]))
        }
    }
}

fun countBySquaredLength(segments: List<Segment>): Map<Int, Int> =
    segments.groupingBy { it.squaredLength }.eachCount()

fun maxCountEntry(segments: List<Segment>): Map.Entry<Int, Int> =
    countBySquaredLength(segments).entries.maxBy { it.value }

/**
 * All canonical step-vectors (dx, dy) for segments of this squared length.
 * Canonical form: dy > 0, OR dy == 0 and dx > 0  (matching allSegments ordering).
 * For dy > 0 and dx != 0, both positive and negative dx exist as distinct shapes.
 */
fun shapesForSquaredLength(sq: Int): List<Pair<Int, Int>> = buildList {
    var dy = 0
    while (dy * dy <= sq) {
        val rem = sq - dy * dy
        val dx = sqrt(rem.toDouble()).roundToInt()
        if (dx * dx == rem) {
            when {
                dy == 0 && dx > 0 -> add(dx to 0)
                dy > 0 && dx == 0 -> add(0 to dy)
                dy > 0            -> { add(-dx to dy); add(dx to dy) }
            }
        }
        dy++
    }
}.sortedWith(compareBy({ it.first }, { it.second }))

fun squaredLengthToReadable(sq: Int): String {
    val d = sqrt(sq.toDouble())
    return if (d == d.toLong().toDouble()) d.toLong().toString() else "√$sq"
}
