package org.satpol

import kotlin.math.*

/** WGS-84 ground station; angles in degrees, altitude above ellipsoid in metres. */
data class GroundStation(val latitudeDeg: Double, val longitudeDeg: Double, val altitudeM: Double = 0.0) {
    init {
        require(latitudeDeg.isFinite() && latitudeDeg in -90.0..90.0)
        require(longitudeDeg.isFinite() && longitudeDeg in -180.0..180.0)
        require(altitudeM.isFinite())
    }
    private val lat get() = Math.toRadians(latitudeDeg)
    private val lon get() = Math.toRadians(longitudeDeg)

    fun ecefM(): Vector3 {
        val a = 6378137.0
        val e2 = 6.6943799901413165e-3
        val n = a / sqrt(1.0 - e2 * sin(lat).pow(2))
        return Vector3(
            (n + altitudeM) * cos(lat) * cos(lon),
            (n + altitudeM) * cos(lat) * sin(lon),
            (n * (1.0 - e2) + altitudeM) * sin(lat)
        )
    }
    fun east() = Vector3(-sin(lon), cos(lon), 0.0)
    fun north() = Vector3(-sin(lat)*cos(lon), -sin(lat)*sin(lon), cos(lat))
    fun up() = Vector3(cos(lat)*cos(lon), cos(lat)*sin(lon), sin(lat))
}

/**
 * Satellite state already converted from SGP4/TEME to Earth-fixed ITRF/ECEF.
 * velocityEcefMps is the derivative in the rotating ECEF frame.
 * Never feed raw TEME coordinates to this class.
 */
data class OrbitStateEcef(val positionM: Vector3, val velocityEcefMps: Vector3) {
    init {
        require(positionM.isFinite() && velocityEcefMps.isFinite())
        require(positionM.norm() > 1e6)
    }
    /** Inertial velocity expressed on ECEF axes, useful for the LVLH approximation. */
    fun inertialVelocityExpressedEcef(): Vector3 {
        val omega = 7.2921150e-5
        val earthRotation = Vector3(-omega * positionM.y, omega * positionM.x, 0.0)
        return velocityEcefMps + earthRotation
    }
}

/** Ground-antenna polarization basis: 'elevation-up' = axis 0, rightward azimuth = axis 1. */
data class LookBasis(
    val lineOfSightGroundToSatellite: Vector3,
    val elevationAxis: Vector3,
    val azimuthAxis: Vector3,
    val elevationDeg: Double
) {
    companion object {
        fun from(station: GroundStation, satelliteEcefM: Vector3): LookBasis? {
            require(satelliteEcefM.isFinite())
            val los = (satelliteEcefM - station.ecefM()).unit()
            val up = station.up()
            val sine = los.dot(up).coerceIn(-1.0, 1.0)
            val projected = los - up * sine
            // At zenith the az/el mechanical orientation is singular, not a unique roll reference.
            if (projected.norm() < 1e-9) return null
            val horizontal = projected.unit()
            val elevationAxis = (up - los * sine).unit()
            val azimuthAxis = horizontal.cross(up).unit()
            return LookBasis(los, elevationAxis, azimuthAxis, Math.toDegrees(asin(sine)))
        }
    }
}

/**
 * Explicit, assumed local vertical/local horizontal attitude.
 * Body +X = projected inertial prograde; +Z = nadir; +Y = Z cross X.
 * Not observational attitude telemetry; NEVER silently apply to unknown spacecraft.
 */
object NadirVelocityFrame {
    fun bodyAxisToEcef(state: OrbitStateEcef, bodyAxis: Vector3): Vector3 {
        val z = (-state.positionM).unit()
        val v = state.inertialVelocityExpressedEcef()
        val t = v - z * v.dot(z)
        val x = t.unit()
        val y = z.cross(x).unit()
        return x * bodyAxis.x + y * bodyAxis.y + z * bodyAxis.z
    }
}
