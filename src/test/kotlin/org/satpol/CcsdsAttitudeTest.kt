package org.satpol

import java.time.Instant
import kotlin.math.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class CcsdsAttitudeTest {
    val fixture="""
CCSDS_AEM_VERS = 1.0
META_START
OBJECT_NAME = TEST-SAT
OBJECT_ID = 2026-001A
CENTER_NAME = EARTH
REF_FRAME_A = SC_BODY
REF_FRAME_B = ITRF
ATTITUDE_DIR = A2B
TIME_SYSTEM = UTC
START_TIME = 2026-10-25T00:00:00Z
STOP_TIME = 2026-10-25T00:00:10Z
ATTITUDE_TYPE = QUATERNION
QUATERNION_TYPE = LAST
META_STOP
DATA_START
2026-10-25T00:00:00Z 0 0 0 1
2026-10-25T00:00:10Z 0 0 1 0
DATA_STOP
""".trimIndent()
    private val state=OrbitStateEcef(Vector3(7000000.0,0.0,0.0),
        Vector3(0.0,7500.0,0.0))
    @Test fun slerpHalfTurnAndObserveAxis() {
        val attitude=CcsdsAttitude.parseAemKvn(fixture,"SYNTHETIC AEM TEST")
            .withCalibratedAntennaAxis(Vector3(1.0,0.0,0.0),"synthetic RF axis fixture")
        val vec=assertNotNull(attitude.axisEcef(state,Instant.parse("2026-10-25T00:00:05Z")))
        assertEquals(AttitudeEvidence.OBSERVED,vec.evidence)
        assertEquals(0.0,vec.directionEcef.x,1e-12)
        assertEquals(1.0,vec.directionEcef.y,1e-12)
        assertNull(attitude.axisEcef(state,Instant.parse("2026-10-25T00:00:11Z")))
    }
    @Test fun rejectInertialFrameAndUncalibratedAntenna() {
        val data=CcsdsAttitude.parseAemKvn(fixture,"SYNTHETIC AEM TEST")
        assertNull(data.axisEcef(state,Instant.parse("2026-10-25T00:00:05Z")))
        assertFailsWith<IllegalArgumentException>{
            CcsdsAttitude.parseAemKvn(fixture.replace("REF_FRAME_B = ITRF","REF_FRAME_B = J2000"),
                "SYNTHETIC AEM TEST")
        }
    }
    @Test fun rejectReverseQuaternionDirection() {
        assertFailsWith<IllegalArgumentException>{
            CcsdsAttitude.parseAemKvn(
                fixture.replace("ATTITUDE_DIR = A2B","ATTITUDE_DIR = B2A"),
                "synthetic reverse frame",5.0)
        }
    }

    @Test fun rejectStaleInterpolatingGap() {
        val data=CcsdsAttitude.parseAemKvn(fixture,"SYNTHETIC AEM TEST",maximumGapSeconds=2.0)
            .withCalibratedAntennaAxis(Vector3(1.0,0.0,0.0))
        assertNull(data.axisEcef(state,Instant.parse("2026-10-25T00:00:05Z")))
    }
}
