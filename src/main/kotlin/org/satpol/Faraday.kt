package org.satpol

import java.time.Instant
import kotlin.math.*

/** In m^-3, at the given Earth-fixed point. Real IRI/GloTEC adapters belong outside the core. */
fun interface ElectronDensityProvider {
    fun electronsPerCubicMetre(ecefM: Vector3, utc: Instant): Double
}

/** Tesla vector in ECEF, at the same position and epoch as the density sample. */
fun interface MagneticFieldProvider {
    fun teslaEcef(ecefM: Vector3, utc: Instant): Vector3
}

data class FaradayEstimate(
    val angleRad: Double,
    val slantTecElectronsPerM2: Double,
    val source: String,
    val sampleCount: Int
) {
    val angleDeg get() = Math.toDegrees(angleRad)
}

/**
 * First-order quasi-longitudinal Faraday rotation:
 * psi = K/f^2 integral(Ne * B.dot(k) ds).
 * k points from spacecraft TO receiver; positive result is defined to rotate
 * Jones axis0 TOWARD axis1. Model validity and physical handedness must be verified
 * against ITU-R P.531 and calibrated measurements before live control.
 * Only integrate the actual ground-to-LEO segment; no full-GNSS VTEC shortcut.
 */
object FaradayIntegrator {
    const val COEFFICIENT_SI: Double = 2.3646e4

    fun integrate(
        satelliteEcefM: Vector3,
        groundEcefM: Vector3,
        utc: Instant,
        frequencyHz: Double,
        density: ElectronDensityProvider,
        magnetic: MagneticFieldProvider,
        source: String,
        samples: Int = 256
    ): FaradayEstimate {
        require(satelliteEcefM.isFinite() && groundEcefM.isFinite())
        require(frequencyHz.isFinite() && frequencyHz > 0.0)
        require(samples in 1..100_000)
        require(source.isNotBlank()) { "Data provenance is mandatory" }
        val ray = groundEcefM-satelliteEcefM
        val length = ray.norm()
        require(length > 1.0 && length.isFinite())
        val k = ray/length
        val ds = length/samples
        var integrated = 0.0
        var tec = 0.0
        for (i in 0 until samples) {
            val mid = satelliteEcefM + ray * ((i+0.5)/samples)
            val ne = density.electronsPerCubicMetre(mid,utc)
            val b = magnetic.teslaEcef(mid,utc)
            require(ne.isFinite() && ne >= 0.0) { "Invalid electron density" }
            require(b.isFinite()) { "Invalid magnetic field" }
            val parcelTec = ne*ds
            tec += parcelTec
            integrated += parcelTec * b.dot(k)
        }
        val angle = COEFFICIENT_SI/(frequencyHz*frequencyHz)*integrated
        require(angle.isFinite() && tec.isFinite())
        return FaradayEstimate(angle,tec,source,samples)
    }
}
