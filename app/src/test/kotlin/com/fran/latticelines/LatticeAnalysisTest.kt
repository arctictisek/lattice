package com.fran.latticelines

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class LatticeAnalysisTest {

    @Test
    fun `gridPoints produces correct number of points`() {
        assertEquals(9, gridPoints(3, 3).size)
        assertEquals(100, gridPoints(10, 10).size)
    }

    @Test
    fun `allSegments produces C(n,2) segments`() {
        val pts = gridPoints(3, 3) // 9 points → C(9,2) = 36
        assertEquals(36, allSegments(pts).size)

        val three = listOf(Point(0, 0), Point(1, 0), Point(2, 0))
        assertEquals(3, allSegments(three).size)
    }

    @Test
    fun `unit segment count on 3x3 grid is 12`() {
        val segments = allSegments(gridPoints(3, 3))
        val histogram = countBySquaredLength(segments)
        assertEquals(12, histogram[1])
    }

    @Test
    fun `unit segment count on 10x10 grid is 180`() {
        val segments = allSegments(gridPoints(10, 10))
        val histogram = countBySquaredLength(segments)
        assertEquals(180, histogram[1])
    }

    @Test
    fun `histogram sums to total segment count`() {
        val segments = allSegments(gridPoints(10, 10))
        val total = countBySquaredLength(segments).values.sum()
        assertEquals(segments.size, total)
    }

    @Test
    fun `knight-move length has more segments than unit length on 10x10`() {
        val segments = allSegments(gridPoints(10, 10))
        val histogram = countBySquaredLength(segments)
        assertTrue((histogram[5] ?: 0) > (histogram[1] ?: 0))
    }

    @Test
    fun `squaredLengthToReadable formats correctly`() {
        assertEquals("1", squaredLengthToReadable(1))
        assertEquals("2", squaredLengthToReadable(4))
        assertEquals("√2", squaredLengthToReadable(2))
        assertEquals("√5", squaredLengthToReadable(5))
    }
}
