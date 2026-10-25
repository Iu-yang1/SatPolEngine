package org.satpol

import kotlin.math.*

enum class AttitudeEvidence { OBSERVED, DOCUMENTED_MODEL, ASSUMED, UNKNOWN }
enum class PredictionStatus {
    VALID, ANGLE_ONLY, ATTITUDE_UNKNOWN, PROPAGATION_UNKNOWN,
    BELOW_HORIZON, ZENITH_SINGULARITY, ANTENNA_NULL, AXIS_UNDEFINED,
    MECHANICALLY_UNREACHABLE
}

data class PolarizationSolution(
    val targetAngleDeg: Double?,
    val recommendedRotation: RotationDirection,
    val commandAngleDeg: Double?,
    val rotationDeltaDeg: Double?,
    val status: PredictionStatus,
    val geometricAngleDeg: Double?,
    val faradayAngleDeg: Double?,
    val theoreticalLossDb: Double?,
    val attitudeEvidence: AttitudeEvidence,
    val propagationSource: String?
)

/**
 * No implicit satellite attitude and no implicit zero-ionosphere fallback.
 * A known ECEF dipole axis can be supplied from telemetry or an explicitly
 * documented/assumed spacecraft attitude model.
 */
object PolarizationEngine {
    fun solveIdealDipole(
        station: GroundStation,
        satelliteEcefM: Vector3,
        dipoleAxisEcef: Vector3?,
        attitudeEvidence: AttitudeEvidence,
        faraday: FaradayEstimate?,
        currentMechanicalRollDeg: Double? = null,
        mechanicalLimits: RollLimits? = null,
        rollCalibration: RollCalibration? = null
    ): PolarizationSolution {
        val basis = LookBasis.from(station, satelliteEcefM)
            ?: return unavailable(PredictionStatus.ZENITH_SINGULARITY,attitudeEvidence,faraday)
        if (basis.elevationDeg <= 0.0) return unavailable(PredictionStatus.BELOW_HORIZON,attitudeEvidence,faraday)
        if (attitudeEvidence == AttitudeEvidence.UNKNOWN || dipoleAxisEcef == null) {
            return unavailable(PredictionStatus.ATTITUDE_UNKNOWN,attitudeEvidence,faraday)
        }
        require(dipoleAxisEcef.isFinite())
        val dipole = dipoleAxisEcef.unit()
        val los = basis.lineOfSightGroundToSatellite
        val transverse = dipole - los * dipole.dot(los)
        if (transverse.norm() < 1e-8) return unavailable(PredictionStatus.ANTENNA_NULL,attitudeEvidence,faraday)
        val emitted = Jones(
            Complex64(transverse.dot(basis.elevationAxis)),
            Complex64(transverse.dot(basis.azimuthAxis))
        )
        return solveJones(emitted,attitudeEvidence,faraday,currentMechanicalRollDeg,mechanicalLimits,rollCalibration)
    }

    /**
     * emitted: complex field expressed ALREADY in the receive wavefront basis,
     * before Faraday rotation. General antenna-pattern integration can use this.
     */
    fun solveJones(
        emitted: Jones?,
        attitudeEvidence: AttitudeEvidence,
        faraday: FaradayEstimate?,
        currentMechanicalRollDeg: Double? = null,
        mechanicalLimits: RollLimits? = null,
        rollCalibration: RollCalibration? = null
    ): PolarizationSolution {
        if (attitudeEvidence == AttitudeEvidence.UNKNOWN || emitted == null) {
            return unavailable(PredictionStatus.ATTITUDE_UNKNOWN,attitudeEvidence,faraday)
        }
        val geometricAngle = emitted.majorAxisDeg()
        if (faraday == null) return unavailable(
            PredictionStatus.PROPAGATION_UNKNOWN,attitudeEvidence,null,geometricAngle
        )
        val received = emitted.rotated(faraday.angleRad)
        val axis = received.majorAxisDeg()
            ?: return unavailable(PredictionStatus.AXIS_UNDEFINED,attitudeEvidence,faraday,geometricAngle)
        val instruction=if(rollCalibration!=null) {
            val motorTarget=rollCalibration.motorTargetFromSkyAxis(axis)
            val raw=RollRecommendation.calculate(motorTarget,currentMechanicalRollDeg,mechanicalLimits)
            raw.copy(direction=rollCalibration.physicalDirection(raw.deltaDeg))
        } else RollRecommendation(null,null,RotationDirection.UNKNOWN)
        val status = when {
            currentMechanicalRollDeg == null || rollCalibration == null -> PredictionStatus.ANGLE_ONLY
            instruction.commandDeg == null -> PredictionStatus.MECHANICALLY_UNREACHABLE
            else -> PredictionStatus.VALID
        }
        val lossDb = if(rollCalibration==null) null else currentMechanicalRollDeg?.takeIf { it.isFinite() }?.let {
            val plf = received.linearMatch(rollCalibration.skyAxisAtMotorRoll(it))
            if (plf <= 0.0) Double.POSITIVE_INFINITY else -10.0 * log10(plf)
        }
        return PolarizationSolution(axis,instruction.direction,instruction.commandDeg,
            instruction.deltaDeg,status,geometricAngle,faraday.angleDeg,lossDb,
            attitudeEvidence,faraday.source)
    }

    private fun unavailable(
        status: PredictionStatus,
        evidence: AttitudeEvidence,
        faraday: FaradayEstimate?,
        geometryDeg: Double? = null
    ) = PolarizationSolution(null, RotationDirection.UNKNOWN,null,null,status,
        geometryDeg,faraday?.angleDeg,null,evidence,faraday?.source)
}
