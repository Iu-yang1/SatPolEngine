package org.satpol

import java.time.Instant

/**
 * End-to-end satellite polarization prediction with scientific data provenance.
 *
 * This pipeline does not fabricate attitude from TLE and does not fabricate
 * ionospheric data. A calibrated real spacecraft-body antenna axis and
 * actual time-tagged attitude are needed for an absolute target angle.
 * Motor instructions remain mathematical only until real sign/zero calibration.
 */
object PredictionPipeline {
    data class Inputs(
        val station:GroundStation,
        val spacecraft:OrbitStateEcef,
        val timeUtc:Instant,
        val frequencyHz:Double,
        val spacecraftAttitude:AttitudeAxisModel?,
        val densityModel:ElectronDensityProvider?,
        val magneticModel:MagneticFieldProvider?,
        val propagationSource:String?,
        val currentPolRollDeg:Double?=null,
        val limits:RollLimits?=null,
        val samples:Int=256
    )

    fun solve(x:Inputs):PolarizationSolution {
        require(x.frequencyHz.isFinite() && x.frequencyHz>0)
        val axis=x.spacecraftAttitude?.axisEcef(x.spacecraft,x.timeUtc)
        if(axis==null) {
            return PolarizationEngine.solveIdealDipole(
                station=x.station,
                satelliteEcefM=x.spacecraft.positionM,
                dipoleAxisEcef=null,
                attitudeEvidence=AttitudeEvidence.UNKNOWN,
                faraday=null,
                currentMechanicalRollDeg=x.currentPolRollDeg,
                mechanicalLimits=x.limits
            )
        }
        val magnetic=x.magneticModel
        val density=x.densityModel
        val faraday=if(magnetic!=null && density!=null && !x.propagationSource.isNullOrBlank())
            FaradayIntegrator.integrate(
                x.spacecraft.positionM, x.station.ecefM(), x.timeUtc, x.frequencyHz,
                density,magnetic,x.propagationSource,x.samples
            )
        else null
        return PolarizationEngine.solveIdealDipole(
            station=x.station, satelliteEcefM=x.spacecraft.positionM,
            dipoleAxisEcef=axis.directionEcef, attitudeEvidence=axis.evidence,
            faraday=faraday, currentMechanicalRollDeg=x.currentPolRollDeg,
            mechanicalLimits=x.limits
        )
    }
}
