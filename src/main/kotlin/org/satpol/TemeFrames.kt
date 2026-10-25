package org.satpol

import java.time.Instant
import kotlin.math.*

/** TEME orbit state in km and km/s exactly as returned by an SGP4/SDP4 propagator. */
data class TemeStateKm(val positionKm: Vector3, val velocityKmPerSecond: Vector3) {
    init {
        require(positionKm.isFinite() && velocityKmPerSecond.isFinite())
        require(positionKm.norm() > 6000.0)
    }
}

/**
 * Earth orientation parameters (IERS), explicit provenance mandatory.
 * UT1-UTC [s]; pole xp,yp [arcsec]; excess length of day [s].
 * Zeros may only be used if *explicitly* labelled approximate, not silently.
 */
data class EarthOrientation(
    val dut1Seconds: Double,
    val polarMotionXArcsec: Double,
    val polarMotionYArcsec: Double,
    val lengthOfDayExcessSeconds: Double,
    val source: String
) {
    init {
        require(source.isNotBlank())
        require(listOf(dut1Seconds,polarMotionXArcsec,polarMotionYArcsec,lengthOfDayExcessSeconds)
            .all { it.isFinite() })
        require(abs(dut1Seconds) < 3.0 && abs(polarMotionXArcsec) < 10.0 &&
            abs(polarMotionYArcsec) < 10.0 && abs(lengthOfDayExcessSeconds) < 1.0)
    }
    companion object {
        fun zeroApproximation() = EarthOrientation(0.0,0.0,0.0,0.0,"ZERO-EOP: APPROXIMATION ONLY")
    }
}

/**
 * Vallado TEME -> pseudo Earth fixed -> ITRF/ECEF via GMST 1982 and polar motion.
 * Position and velocities transformed together; velocity includes Earth rotation.
 * Polar matrix follows Vallado (1980) form, applied transposed to PEF.
 *
 * Input time is UTC Instant; EOP UT1 correction must match that epoch.
 * All returned vectors are metres and metres/s, in rotating ECEF frame.
 */
object TemeFrames {
    fun gmst1982Radians(utc: Instant, dut1Seconds: Double): Double {
        require(dut1Seconds.isFinite())
        val jdUt1=2440587.5+(utc.epochSecond + utc.nano*1e-9 + dut1Seconds)/86400.0
        val t=(jdUt1-2451545.0)/36525.0
        val secs=67310.54841+(876600.0*3600.0+8640184.812866)*t+
            0.093104*t*t-6.2e-6*t*t*t
        val angle=((secs % 86400.0)+86400.0)%86400.0 * (2.0*PI/86400.0)
        return angle
    }

    fun toEcef(teme: TemeStateKm, utc: Instant, eop: EarthOrientation): OrbitStateEcef {
        val gmst=gmst1982Radians(utc,eop.dut1Seconds)
        val c=cos(gmst)
        val s=sin(gmst)
        // transpose ROT3(theta), consistent with Vallado's TEME axes.
        fun toPef(v: Vector3)=Vector3(c*v.x+s*v.y,-s*v.x+c*v.y,v.z)
        val rPef=toPef(teme.positionKm*1000.0)
        val omega=7.29211514670698e-5*(1.0-eop.lengthOfDayExcessSeconds/86400.0)
        val vPef=toPef(teme.velocityKmPerSecond*1000.0) -
            Vector3(-omega*rPef.y,omega*rPef.x,0.0)

        val xp=Math.toRadians(eop.polarMotionXArcsec/3600.0)
        val yp=Math.toRadians(eop.polarMotionYArcsec/3600.0)
        val cx=cos(xp); val sx=sin(xp)
        val cy=cos(yp); val sy=sin(yp)
        // PEF->ITRF, the transpose of Vallado's 1980 polar-motion matrix.
        fun toItrf(v: Vector3) = Vector3(
            cx*v.x + sx*sy*v.y + sx*cy*v.z,
            cy*v.y - sy*v.z,
            -sx*v.x + cx*sy*v.y + cx*cy*v.z
        )
        return OrbitStateEcef(toItrf(rPef),toItrf(vPef))
    }
}
