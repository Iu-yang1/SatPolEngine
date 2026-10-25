package org.satpol

import java.time.Instant
import kotlin.math.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class ScientificCoreTest {
    private fun vacuum() = FaradayEstimate(0.0,0.0,"explicit ideal-vacuum test only",1)

    @Test fun earthCoordinatesAndBasis() {
        val ground = GroundStation(0.0,0.0)
        assertEquals(6378137.0,ground.ecefM().x,0.001)
        assertEquals(0.0,ground.east().dot(ground.north()),1e-12)
        val sat=ground.ecefM()+ground.up()*500_000.0+ground.north()*500_000.0
        val basis=assertNotNull(LookBasis.from(ground,sat))
        assertEquals(45.0,basis.elevationDeg,1e-9)
        assertEquals(0.0,basis.elevationAxis.dot(basis.azimuthAxis),1e-12)
        assertEquals(1.0,basis.elevationAxis.norm(),1e-12)
    }

    @Test fun wrapAndShortestRoll() {
        assertEquals(-90.0,LinearAngles.wrapDeg(90.0),1e-12)
        assertEquals(-89.0,LinearAngles.wrapDeg(91.0),1e-12)
        assertEquals(2.0,LinearAngles.shortestDeltaDeg(-89.0,89.0),1e-12)
        val instruction=RollRecommendation.calculate(-80.0,80.0)
        assertEquals(RotationDirection.CLOCKWISE,instruction.direction)
        assertEquals(20.0,assertNotNull(instruction.deltaDeg),1e-12)
        val bounded=RollRecommendation.calculate(-80.0,80.0,RollLimits(-90.0,90.0))
        assertEquals(RotationDirection.COUNTERCLOCKWISE,bounded.direction)
        assertEquals(-160.0,assertNotNull(bounded.deltaDeg),1e-12)
    }

    @Test fun jonesLinearRotationAndCircularUndefinedAxis() {
        val j=Jones.linear(0.0).rotated(Math.toRadians(30.0))
        assertEquals(30.0,assertNotNull(j.majorAxisDeg()),1e-10)
        assertEquals(1.0,j.linearMatch(30.0),1e-12)
        assertEquals(0.75,j.linearMatch(0.0),1e-12)
        val circular=Jones(Complex64(1.0),Complex64(0.0,1.0))
        assertNull(circular.majorAxisDeg())
        assertEquals(0.5,circular.linearMatch(62.0),1e-12)
    }

    @Test fun analyticFaradayAndFrequencyScaling() {
        val sat=Vector3(7e6,0.0,0.0)
        val station=Vector3(6e6,0.0,0.0)
        val density=ElectronDensityProvider { _,_ -> 5e10 }
        val field=MagneticFieldProvider { _,_ -> Vector3(-20e-6,0.0,0.0) }
        val instant=Instant.parse("2026-10-25T00:00:00Z")
        val r435=FaradayIntegrator.integrate(sat,station,instant,435e6,density,field,"synthetic",128)
        val r145=FaradayIntegrator.integrate(sat,station,instant,145e6,density,field,"synthetic",128)
        val expected=2.3646e4*5e16*20e-6/(435e6*435e6)
        assertEquals(expected,r435.angleRad,1e-12)
        assertEquals(5e16,r435.slantTecElectronsPerM2,100.0)
        assertEquals(9.0,r145.angleRad/r435.angleRad,1e-10)
    }

    @Test fun zeroElectronsAndTransverseFieldZeroRotation() {
        val sat=Vector3(7e6,0.0,0.0)
        val station=Vector3(6e6,0.0,0.0)
        val now=Instant.EPOCH
        val zero=FaradayIntegrator.integrate(sat,station,now,435e6,
            ElectronDensityProvider { _,_ -> 0.0 },
            MagneticFieldProvider { _,_ -> Vector3(-30e-6,0.0,0.0) },"synthetic")
        assertEquals(0.0,zero.angleRad,0.0)
        val transverse=FaradayIntegrator.integrate(sat,station,now,435e6,
            ElectronDensityProvider { _,_ -> 1e11 },
            MagneticFieldProvider { _,_ -> Vector3(0.0,30e-6,0.0) },"synthetic")
        assertEquals(0.0,transverse.angleRad,0.0)
    }

    @Test fun knownDipoleAndRotationRecommendation() {
        val ground=GroundStation(0.0,0.0)
        val satellite=ground.ecefM()+ground.up()*500_000.0+ground.north()*500_000.0
        val basis=assertNotNull(LookBasis.from(ground,satellite))
        val model=FaradayEstimate(Math.toRadians(30.0),0.0,"synthetic",1)
        val result=PolarizationEngine.solveIdealDipole(
            ground,satellite,basis.elevationAxis,AttitudeEvidence.OBSERVED,model,0.0,
            rollCalibration=RollCalibration(0.0,true,"test fixture calibration"))
        assertEquals(PredictionStatus.VALID,result.status)
        assertEquals(30.0,assertNotNull(result.targetAngleDeg),1e-9)
        assertEquals(RotationDirection.CLOCKWISE,result.recommendedRotation)
        assertEquals(30.0,assertNotNull(result.rotationDeltaDeg),1e-9)
        assertEquals(1.249387,assertNotNull(result.theoreticalLossDb),0.01)
    }

    @Test fun noFabricatedAttitudeOrIonosphere() {
        val ground=GroundStation(0.0,0.0)
        val satellite=ground.ecefM()+ground.up()*500_000.0+ground.north()*500_000.0
        val basis=assertNotNull(LookBasis.from(ground,satellite))
        val noAttitude=PolarizationEngine.solveIdealDipole(
            ground,satellite,null,AttitudeEvidence.UNKNOWN,vacuum())
        assertEquals(PredictionStatus.ATTITUDE_UNKNOWN,noAttitude.status)
        assertNull(noAttitude.targetAngleDeg)
        val noIonosphere=PolarizationEngine.solveIdealDipole(
            ground,satellite,basis.elevationAxis,AttitudeEvidence.ASSUMED,null)
        assertEquals(PredictionStatus.PROPAGATION_UNKNOWN,noIonosphere.status)
        assertNull(noIonosphere.targetAngleDeg)
    }

    @Test fun rejectZenithDipoleNullCircularAndUnreachable() {
        val station=GroundStation(0.0,0.0)
        val satZenith=station.ecefM()+station.up()*600_000.0
        assertEquals(PredictionStatus.ZENITH_SINGULARITY,
            PolarizationEngine.solveIdealDipole(station,satZenith,
                station.east(),AttitudeEvidence.ASSUMED,vacuum()).status)
        val sat=station.ecefM()+station.up()*500_000.0+station.north()*500_000.0
        val los=(sat-station.ecefM()).unit()
        assertEquals(PredictionStatus.ANTENNA_NULL,
            PolarizationEngine.solveIdealDipole(station,sat,
                los,AttitudeEvidence.ASSUMED,vacuum()).status)
        val cp=Jones(Complex64(1.0),Complex64(0.0,1.0))
        assertEquals(PredictionStatus.AXIS_UNDEFINED,
            PolarizationEngine.solveJones(cp,AttitudeEvidence.OBSERVED,vacuum()).status)
        val out=PolarizationEngine.solveJones(Jones.linear(50.0),
            AttitudeEvidence.OBSERVED,vacuum(),0.0,RollLimits(-10.0,10.0),
            rollCalibration=RollCalibration(0.0,true,"test fixture calibration"))
        assertEquals(PredictionStatus.MECHANICALLY_UNREACHABLE,out.status)
        assertEquals(RotationDirection.UNKNOWN,out.recommendedRotation)
    }

    @Test fun invalidInputsRejected() {
        assertFailsWith<IllegalArgumentException> { GroundStation(95.0,0.0) }
        assertFailsWith<IllegalArgumentException> {
            FaradayIntegrator.integrate(Vector3(7e6,0.0,0.0),Vector3(6e6,0.0,0.0),Instant.EPOCH,
                435e6,ElectronDensityProvider { _,_ -> -1.0 },
                MagneticFieldProvider { _,_ -> Vector3(1e-5,0.0,0.0) },"test")
        }
    }
}
