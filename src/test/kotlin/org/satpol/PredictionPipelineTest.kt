package org.satpol

import java.time.Instant
import kotlin.math.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class PredictionPipelineTest {
    @Test fun requireAttitudeAndPropagationEvenWithRealGeomagneticModel() {
        val station=GroundStation(40.0,-105.0)
        val satellite=station.ecefM()+station.up()*600_000.0+station.north()*100_000.0
        val orbit=OrbitStateEcef(satellite,Vector3(0.0,7500.0,0.0))
        val base=PredictionPipeline.Inputs(
            station,orbit,Instant.parse("2026-10-25T12:00:00Z"),435e6,
            null,null,Igrf14.fromBundledCoefficients(),"IGRF-14"
        )
        val withoutAtt=PredictionPipeline.solve(base)
        assertEquals(PredictionStatus.ATTITUDE_UNKNOWN,withoutAtt.status)
        assertNull(withoutAtt.targetAngleDeg)
        val unknownNe=PredictionPipeline.solve(base.copy(
            spacecraftAttitude=AssumedNadirPrograde(Vector3(0.0,1.0,0.0),"TEST ONLY")
        ))
        assertEquals(PredictionStatus.PROPAGATION_UNKNOWN,unknownNe.status)
        assertNull(unknownNe.targetAngleDeg)
    }

    @Test fun fullSyntheticPolarizationChainMustBeLabelledAsSynthetic() {
        val observer=GroundStation(40.0,-105.0)
        val sat=observer.ecefM()+observer.up()*600_000.0+observer.north()*100_000.0
        val orbit=OrbitStateEcef(sat,Vector3(0.0,7500.0,0.0))
        val model=AssumedNadirPrograde(Vector3(0.0,1.0,0.0),"synthetic LVLH assumption")
        val x=PredictionPipeline.Inputs(
            observer,orbit,Instant.parse("2026-10-25T12:00:00Z"),
            435e6,model,
            ElectronDensityProvider{_,_->1e11},
            Igrf14.fromBundledCoefficients(),"SYNTHETIC Ne + REAL IGRF-14",
            currentPolRollDeg=0.0,samples=32
        )
        val predicted=PredictionPipeline.solve(x)
        assertEquals(PredictionStatus.VALID,predicted.status)
        assertEquals(AttitudeEvidence.ASSUMED,predicted.attitudeEvidence)
        assertTrue(assertNotNull(predicted.targetAngleDeg).isFinite())
        assertTrue(assertNotNull(predicted.faradayAngleDeg).isFinite())
        assertTrue(predicted.propagationSource!!.contains("SYNTHETIC"))
    }
}
