package org.satpol

import java.time.Instant
import kotlin.math.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class RealTransmitterTest {
    private val t=Instant.parse("2017-06-24T22:32:14Z")
    @Test fun documentedCircularDownlinkHasNoUniqueLinearReceiveRoll() {
        val model=CircularPatchTransmitter.cassiopeSband()
        assertTrue(model.rightHandCircular)
        assertEquals(2_231_500_000.0,model.frequencyHz,0.0)
        val q=Quaternion(1.0,0.0,0.0,0.0)
        val result=model.modelAt(
            Vector3(7_100_000.0,0.0,0.0),
            Vector3(6_378_137.0,0.0,0.0),q,t,t)
        assertNull(result.polarizationAngleDeg)
        assertEquals(RotationDirection.UNKNOWN,result.recommendedRotation)
        assertEquals(10.0*log10(2.0),result.linearReceiverMismatchDb,1e-12)
        // Independently check the ideal circular Jones feed against every roll.
        val circular=Jones(Complex64(1/sqrt(2.0)),Complex64(0.0,1/sqrt(2.0)))
        for(deg in -180..180 step 5){
            assertEquals(0.5,circular.linearMatch(deg.toDouble()),1e-12)
        }
    }
    @Test fun timeMismatchCannotMasqueradeAsRealAttitudeAlignedWithOrbit() {
        val t2=t.plusSeconds(1)
        assertFailsWith<IllegalArgumentException>{
            CircularPatchTransmitter.cassiopeSband().modelAt(
                Vector3(7e6,0.0,0.0),Vector3(6e6,0.0,0.0),
                Quaternion(1.0,0.0,0.0,0.0),t,t2)
        }
    }
}
