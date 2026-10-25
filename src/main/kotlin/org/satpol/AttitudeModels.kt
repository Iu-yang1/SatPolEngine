package org.satpol

import java.time.Duration
import java.time.Instant
import kotlin.math.*

/**
 * A physically explicit spacecraft body-to-ECEF reference axis solution.
 * The direction is the fixed *mechanical dipole axis* in body coordinates,
 * not a generic RF vector antenna pattern.
 */
data class AttitudeAxisEcef(
    val directionEcef: Vector3,
    val evidence: AttitudeEvidence,
    val source: String
) {
    init {
        require(directionEcef.isFinite() && directionEcef.norm()>1e-10)
        require(evidence != AttitudeEvidence.UNKNOWN)
        require(source.isNotBlank())
    }
}

sealed interface AttitudeAxisModel {
    fun axisEcef(state:OrbitStateEcef,time:Instant):AttitudeAxisEcef?
}

/**
 * A quaternion from calibrated telemetry with an explicit UTC sample time.
 * Quaternion is an active body -> **ECEF** rotation. An ECI/TEME quaternion
 * must first be transformed consistently into this frame.
 */
data class TelemetryAttitude(
    val axisBody:Vector3,
    val bodyToEcef:Quaternion,
    val sampleUtc:Instant,
    val source:String,
    val maxAgeSeconds:Double = 10.0
):AttitudeAxisModel {
    init {
        require(axisBody.isFinite() && axisBody.norm()>1e-10)
        require(maxAgeSeconds.isFinite() && maxAgeSeconds>=0)
        require(source.isNotBlank())
    }
    override fun axisEcef(state:OrbitStateEcef,time:Instant):AttitudeAxisEcef? {
        val ageSeconds=abs(Duration.between(sampleUtc,time).toNanos()*1e-9)
        if(ageSeconds>maxAgeSeconds) return null
        return AttitudeAxisEcef(bodyToEcef.rotate(axisBody.unit()).unit(),
            AttitudeEvidence.OBSERVED,source)
    }
}

/**
 * Explicit LVLH/nadir assumption: +X prograde, +Z nadir and +Y=Z×X.
 * DO NOT classify as measured or documented attitude without external proof.
 */
data class AssumedNadirPrograde(
    val antennaAxisBody:Vector3,
    val assumptionSource:String
):AttitudeAxisModel {
    init {
        require(antennaAxisBody.isFinite() && antennaAxisBody.norm()>1e-10)
        require(assumptionSource.isNotBlank())
    }
    override fun axisEcef(state:OrbitStateEcef,time:Instant):AttitudeAxisEcef =
        AttitudeAxisEcef(NadirVelocityFrame.bodyAxisToEcef(state,antennaAxisBody.unit()).unit(),
            AttitudeEvidence.ASSUMED,assumptionSource)
}

/**
 * Simplified deterministic spin about a defined BODY axis, referenced to an
 * assumed nadir/prograde base frame. It needs both spin phase and rate.
 * The model cannot infer phase from TLE or signal strength alone.
 */
data class AssumedSpinAboutBodyAxis(
    val antennaAxisBodyAtEpoch:Vector3,
    val spinAxisBody:Vector3,
    val radiansPerSecond:Double,
    val phaseAtEpochRad:Double,
    val epoch:Instant,
    val assumptionSource:String
):AttitudeAxisModel {
    init {
        require(antennaAxisBodyAtEpoch.isFinite() && antennaAxisBodyAtEpoch.norm()>1e-10)
        require(spinAxisBody.isFinite() && spinAxisBody.norm()>1e-10)
        require(radiansPerSecond.isFinite() && phaseAtEpochRad.isFinite())
        require(assumptionSource.isNotBlank())
    }
    override fun axisEcef(state:OrbitStateEcef,time:Instant):AttitudeAxisEcef {
        val axis=spinAxisBody.unit()
        val offset=Duration.between(epoch,time)
        val elapsed=offset.seconds.toDouble()+offset.nano*1e-9
        val theta=(phaseAtEpochRad + elapsed*radiansPerSecond)%(2.0*PI)
        val initial=antennaAxisBodyAtEpoch.unit()
        val spun=initial*cos(theta)+axis.cross(initial)*sin(theta)+
            axis*(axis.dot(initial)*(1-cos(theta)))
        return AttitudeAxisEcef(NadirVelocityFrame.bodyAxisToEcef(state,spun).unit(),
            AttitudeEvidence.ASSUMED,assumptionSource)
    }
}
