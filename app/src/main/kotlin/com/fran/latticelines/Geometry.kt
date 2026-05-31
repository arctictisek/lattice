package com.fran.latticelines

data class Point(val x: Int, val y: Int)

fun Point.squaredDistanceTo(other: Point): Int {
    val dx = other.x - x
    val dy = other.y - y
    return dx * dx + dy * dy
}

data class Segment(val a: Point, val b: Point) {
    val squaredLength: Int get() = a.squaredDistanceTo(b)
}
