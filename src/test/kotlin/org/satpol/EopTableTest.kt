package org.satpol

import java.time.Instant
import kotlin.test.*
import org.junit.jupiter.api.Test

class EopTableTest {
    private val example="utc,dut1_seconds,xp_arcsec,yp_arcsec,lod_excess_seconds\n"+
        "2026-10-25T00:00:00Z,-0.10,0.10,0.20,0.0002\n"+
        "2026-10-26T00:00:00Z,-0.12,0.11,0.21,0.0004\n"

    @Test fun interpolateEopAndRejectStale() {
        val table=EarthOrientationTable.parseCsv(example,"IERS unit fixture")
        val mid=table.at(Instant.parse("2026-10-25T12:00:00Z"))
        assertEquals(-0.11,mid.dut1Seconds,1e-12)
        assertEquals(0.105,mid.polarMotionXArcsec,1e-12)
        assertEquals(0.0003,mid.lengthOfDayExcessSeconds,1e-12)
        assertTrue(mid.source.contains("interpolated"))
        assertFailsWith<IllegalArgumentException>{
            table.at(Instant.parse("2026-10-27T00:00:00Z"))
        }
    }
    @Test fun rejectLeapSecondInterpolation() {
        val table=EarthOrientationTable.parseCsv(
            "utc,dut1_seconds,xp_arcsec,yp_arcsec,lod_excess_seconds\n"+
            "2016-12-31T00:00:00Z,-0.4,0.1,0.2,0.0\n"+
            "2017-01-01T00:00:00Z,0.6,0.1,0.2,0.0\n",
            "IERS leap second fixture"
        )
        assertFailsWith<IllegalArgumentException>{
            table.at(Instant.parse("2016-12-31T12:00:00Z"))
        }
    }
}
