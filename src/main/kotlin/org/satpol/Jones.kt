package org.satpol

import kotlin.math.*

data class Complex64(val re: Double, val im: Double = 0.0) {
    operator fun plus(b: Complex64) = Complex64(re+b.re, im+b.im)
    operator fun times(scale: Double) = Complex64(re*scale, im*scale)
    operator fun times(b: Complex64) = Complex64(re*b.re-im*b.im, re*b.im+im*b.re)
    fun conjugate() = Complex64(re, -im)
    fun power() = re*re + im*im
}

/**
 * Jones components in the receive wavefront basis: axis0 = elevation-up,
 * axis1 = azimuth-right. All rotations are in this same fixed real basis.
 * Circular handedness requires a separate time-phasor convention.
 */
data class Jones(val axis0: Complex64, val axis1: Complex64) {
    val power get() = axis0.power() + axis1.power()

    fun rotated(angleRad: Double): Jones {
        require(angleRad.isFinite())
        val c = cos(angleRad)
        val s = sin(angleRad)
        return Jones(axis0*c + axis1*(-s), axis0*s + axis1*c)
    }

    fun stokes(): Stokes {
        val mixed = axis0 * axis1.conjugate()
        return Stokes(power, axis0.power()-axis1.power(), 2*mixed.re, 2*mixed.im)
    }

    /** The major axis of the polarization ellipse. Null for circular/zero field. */
    fun majorAxisDeg(): Double? {
        if (!power.isFinite() || power <= 1e-24) return null
        val s = stokes()
        val linear = hypot(s.s1, s.s2)
        if (linear / power < 1e-10) return null
        return LinearAngles.wrapDeg(Math.toDegrees(0.5 * atan2(s.s2, s.s1)))
    }

    /** Power delivered to an ideal unit-gain linear polarizer at the stated angle. */
    fun linearMatch(angleDeg: Double): Double {
        require(power.isFinite() && power > 1e-24)
        val a = Math.toRadians(angleDeg)
        val out = axis0 * cos(a) + axis1 * sin(a)
        return (out.power() / power).coerceIn(0.0, 1.0)
    }

    companion object {
        fun linear(angleDeg: Double): Jones {
            val r = Math.toRadians(angleDeg)
            return Jones(Complex64(cos(r)), Complex64(sin(r)))
        }
    }
}
data class Stokes(val s0: Double, val s1: Double, val s2: Double, val s3: Double)

object LinearAngles {
    /** Polarization axes are lines rather than directed vectors: period = 180 degrees. */
    fun wrapDeg(angle: Double): Double {
        require(angle.isFinite())
        val r = ((angle + 90.0) % 180.0 + 180.0) % 180.0 - 90.0
        return if (r == -0.0) 0.0 else r
    }
    fun shortestDeltaDeg(target: Double, current: Double): Double = wrapDeg(target-current)
}

/**
 * Explicit receiver mechanical POL calibration. Both sign and zero must be
 * measured as seen by an operator at the station LOOKING TOWARD the satellite.
 * Sky axis0=elevation-up, axis1=azimuth-right, positive sky angle is CW.
 * A motor encoder angle is not interchangeable with a sky polarization angle.
 */
data class RollCalibration(
    val skyAxisAtMotorZeroDeg: Double,
    val positiveMotorRollIsClockwise: Boolean,
    val calibrationSource: String
) {
    init {
        require(skyAxisAtMotorZeroDeg.isFinite())
        require(calibrationSource.isNotBlank())
    }
    private val sign: Double get() = if (positiveMotorRollIsClockwise) 1.0 else -1.0
    fun skyAxisAtMotorRoll(motorDeg: Double): Double =
        LinearAngles.wrapDeg(skyAxisAtMotorZeroDeg + sign * motorDeg)
    fun motorTargetFromSkyAxis(targetAxisDeg: Double): Double =
        sign * LinearAngles.wrapDeg(targetAxisDeg - skyAxisAtMotorZeroDeg)
    fun physicalDirection(motorDeltaDeg: Double?): RotationDirection {
        if (motorDeltaDeg==null||!motorDeltaDeg.isFinite()) return RotationDirection.UNKNOWN
        val skyDelta=sign * motorDeltaDeg
        return when {
            abs(skyDelta)<=0.05 -> RotationDirection.HOLD
            skyDelta>0 -> RotationDirection.CLOCKWISE
            else -> RotationDirection.COUNTERCLOCKWISE
        }
    }
}

enum class RotationDirection { CLOCKWISE, COUNTERCLOCKWISE, HOLD, UNKNOWN }

/** In an arbitrary calibrated mechanical roll coordinate, inclusive physical limits. */
data class RollLimits(val minDeg: Double, val maxDeg: Double) {
    init { require(minDeg.isFinite() && maxDeg.isFinite() && minDeg <= maxDeg) }
}

data class RollRecommendation(val commandDeg: Double?, val deltaDeg: Double?, val direction: RotationDirection) {
    companion object {
        fun calculate(targetAxisDeg: Double, currentRollDeg: Double?, limits: RollLimits? = null): RollRecommendation {
            val axis = LinearAngles.wrapDeg(targetAxisDeg)
            if (currentRollDeg == null || !currentRollDeg.isFinite()) {
                return RollRecommendation(null, null, RotationDirection.UNKNOWN)
            }
            if (limits != null && currentRollDeg !in limits.minDeg..limits.maxDeg) {
                return RollRecommendation(null, null, RotationDirection.UNKNOWN)
            }
            val command = if (limits == null) {
                currentRollDeg + LinearAngles.shortestDeltaDeg(axis, currentRollDeg)
            } else {
                (-24..24).asSequence().map { axis+180.0*it }
                    .filter { it in limits.minDeg..limits.maxDeg }
                    .minByOrNull { abs(it-currentRollDeg) }
                    ?: return RollRecommendation(null, null, RotationDirection.UNKNOWN)
            }
            val delta = command - currentRollDeg
            return RollRecommendation(command, delta, when {
                abs(delta) <= 0.05 -> RotationDirection.HOLD
                delta > 0.0 -> RotationDirection.CLOCKWISE
                else -> RotationDirection.COUNTERCLOCKWISE
            })
        }
    }
}
