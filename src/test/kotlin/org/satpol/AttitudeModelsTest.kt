package org.satpol

import java.time.Instant
import kotlin.math.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class AttitudeModelsTest {
    private val state=OrbitStateEcef(
        Vector3(6800000.0,0.0,0.0), Vector3(0.0,7600.0,0.0)
    )
    private val t=Instant.parse("2026-10-25T00:00:00Z")

    @Test fun orthonormalNadirAndProgradeBasis() {
        val x=NadirVelocityFrame.bodyAxisToEcef(state,Vector3(1.0,0.0,0.0))
        val y=NadirVelocityFrame.bodyAxisToEcef(state,Vector3(0.0,1.0,0.0))
        val z=NadirVelocityFrame.bodyAxisToEcef(state,Vector3(0.0,0.0,1.0))
        assertEquals(1.0,x.norm(),1e-12)
        assertEquals(1.0,y.norm(),1e-12)
        assertEquals(1.0,z.norm(),1e-12)
        assertEquals(0.0,x.dot(y),1e-12)
        assertEquals(0.0,y.dot(z),1e-12)
        assertEquals(0.0,z.dot(x),1e-12)
        assertEquals(1.0,x.cross(y).dot(z),1e-12)
        assertEquals(-1.0,z.x,1e-12)
    }

    @Test fun observedQuaternionFrameAndAge() {
        val q=Quaternion(cos(PI/4),0.0,0.0,sin(PI/4))
        val model=TelemetryAttitude(Vector3(1.0,0.0,0.0),q,t,"telemetry",maxAgeSeconds=3.0)
        val got=assertNotNull(model.axisEcef(state,t.plusSeconds(2)))
        assertEquals(AttitudeEvidence.OBSERVED,got.evidence)
        assertEquals(1.0,got.directionEcef.y,1e-12)
        assertNull(model.axisEcef(state,t.plusSeconds(5)))
    }

    @Test fun modelSpinRequiresDeclaredPhaseAndRate() {
        val base=AssumedNadirPrograde(Vector3(1.0,0.0,0.0),"synthetic nadir frame")
        val spin=AssumedSpinAboutBodyAxis(
            Vector3(1.0,0.0,0.0),Vector3(0.0,0.0,1.0),
            PI/2,0.0,t,"synthetic spin")
        val first=assertNotNull(spin.axisEcef(state,t))
        assertEquals(AttitudeEvidence.ASSUMED,first.evidence)
        val expected0=assertNotNull(base.axisEcef(state,t)).directionEcef
        assertEquals(1.0,first.directionEcef.dot(expected0),1e-12)
        val second=assertNotNull(spin.axisEcef(state,t.plusSeconds(1)))
        val expected= NadirVelocityFrame.bodyAxisToEcef(state,Vector3(0.0,1.0,0.0))
        assertEquals(1.0,second.directionEcef.dot(expected),1e-12)
    }
}
