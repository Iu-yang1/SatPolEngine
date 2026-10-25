package org.satpol

import kotlin.math.sqrt

/** Cartesian vector in a single, explicitly documented coordinate frame. */
data class Vector3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun unaryMinus() = Vector3(-x, -y, -z)
    operator fun times(scale: Double) = Vector3(x * scale, y * scale, z * scale)
    operator fun div(scale: Double) = Vector3(x / scale, y / scale, z / scale)
    fun dot(other: Vector3) = x * other.x + y * other.y + z * other.z
    fun cross(other: Vector3) = Vector3(
        y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x
    )
    fun squaredNorm() = dot(this)
    fun norm() = sqrt(squaredNorm())
    fun isFinite() = x.isFinite() && y.isFinite() && z.isFinite()
    fun unit(): Vector3 {
        val length = norm()
        require(length.isFinite() && length > 1e-12) { "Undefined zero-length/nonfinite vector" }
        return this / length
    }
}

/** Unit quaternion, active rotation from body to the explicitly selected world frame. */
data class Quaternion(val w: Double, val x: Double, val y: Double, val z: Double) {
    fun rotate(vector: Vector3): Vector3 {
        val n = sqrt(w*w + x*x + y*y + z*z)
        require(n.isFinite() && n > 1e-12) { "Invalid quaternion" }
        val q = Vector3(x/n, y/n, z/n)
        val t = q.cross(vector) * 2.0
        return vector + t * (w/n) + q.cross(t)
    }
}
