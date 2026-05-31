package com.fran.latticelines

import kotlin.math.sqrt

fun main() {
    val rows = 10
    val cols = 10
    val points = gridPoints(rows, cols)
    val segments = allSegments(points)
    val histogram = countBySquaredLength(segments)

    val biggestSide = maxOf(rows, cols) - 1
    val totalSegments = segments.size
    val unitCount = histogram[1] ?: 0
    val best = maxCountEntry(segments)

    println("Q1. Biggest square side length : $biggestSide")
    println("Q2. Total line segments        : $totalSegments")
    println("Q3. Segments of length 1       : $unitCount")
    println()
    println("Q4. Length with most segments  : ${squaredLengthToReadable(best.key)} (squared=${best.key}) → ${best.value} segments")
    if (best.key != 1) {
        println("    (beats unit-length count of $unitCount by ${best.value - unitCount})")
    }

    println()
    println("--- full histogram (squaredLength → count) ---")
    histogram.entries.sortedBy { it.key }.forEach { (sq, cnt) ->
        val marker = if (sq == best.key) " ← max" else if (sq == 1) " ← unit" else ""
        println("  ${squaredLengthToReadable(sq).padEnd(6)} (sq=$sq): $cnt$marker")
    }
}
