package org.satpol

import kotlin.math.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class RollCalibrationTest {
    private val f=FaradayEstimate(0.0,0.0,"explicit ideal-vacuum test only",1)

    @Test fun failClosedWithoutCalibration() {
        val solution=PolarizationEngine.solveJones(Jones.linear(30.0),
            AttitudeEvidence.OBSERVED,f,currentMechanicalRollDeg=0.0)
        assertEquals(30.0,assertNotNull(solution.targetAngleDeg),1e-12)
        assertEquals(PredictionStatus.ANGLE_ONLY,solution.status)
        assertNull(solution.commandAngleDeg)
        assertNull(solution.rotationDeltaDeg)
        assertNull(solution.theoreticalLossDb)
        assertEquals(RotationDirection.UNKNOWN,solution.recommendedRotation)
    }

    @Test fun reversedEncoderDirectionCorrectlyProducesClockwise() {
        val reversed=RollCalibration(0.0,false,"physically measured polarity")
        val target=PolarizationEngine.solveJones(Jones.linear(30.0),
            AttitudeEvidence.OBSERVED,f,0.0,RollLimits(-90.0,90.0),reversed)
        assertEquals(PredictionStatus.VALID,target.status)
        assertEquals(-30.0,assertNotNull(target.commandAngleDeg),1e-12)
        assertEquals(-30.0,assertNotNull(target.rotationDeltaDeg),1e-12)
        assertEquals(RotationDirection.CLOCKWISE,target.recommendedRotation)
        assertEquals(-10.0*log10(0.75),assertNotNull(target.theoreticalLossDb),1e-12)
    }

    @Test fun nonZeroMotorZeroAndEquivalentPolarizerAxes() {
        val calibrated=RollCalibration(25.0,true,"measured reference")
        val s=PolarizationEngine.solveJones(Jones.linear(-80.0),
            AttitudeEvidence.OBSERVED,f,50.0,RollLimits(-90.0,90.0),calibrated)
        assertEquals(PredictionStatus.VALID,s.status)
        assertEquals(75.0,assertNotNull(s.commandAngleDeg),1e-12)
        assertEquals(RotationDirection.CLOCKWISE,s.recommendedRotation)
        assertEquals(25.0,assertNotNull(s.rotationDeltaDeg),1e-12)
    }

    @Test fun limitsWithoutEquivalentSolutionFailClosed() {
        val calibrated=RollCalibration(0.0,false,"calibrated reverse motor")
        val s=PolarizationEngine.solveJones(Jones.linear(50.0),
            AttitudeEvidence.OBSERVED,f,0.0,RollLimits(-10.0,10.0),calibrated)
        assertEquals(PredictionStatus.MECHANICALLY_UNREACHABLE,s.status)
        assertEquals(RotationDirection.UNKNOWN,s.recommendedRotation)
        assertNull(s.commandAngleDeg)
    }
}
