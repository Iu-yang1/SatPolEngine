package org.satpol

import java.time.Instant
import kotlin.math.*
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.satpol.orbit.TleStateBridge

class RealInputsTest {
    @Test fun igrf14MagneticFieldPhysicalAndDateBounds() {
        val igrf=Igrf14.fromBundledCoefficients()
        val atEquator=GroundStation(0.0,0.0).ecefM()
        val field=igrf.evaluate(atEquator,2025.0)
        assertTrue(field.norm() in 20e-6..70e-6,"Earth main field must be physical: $field")
        // At 0 N, 0 E, geomagnetic field predominantly points geographic north.
        assertTrue(field.z in 15e-6..40e-6,"Unexpected equatorial north field: $field")
        val field2020=igrf.evaluate(atEquator,2020.0)
        val field2030=igrf.evaluate(atEquator,2030.0)
        assertTrue((field2020-field2030).norm()<3e-6)
        assertFailsWith<IllegalArgumentException>{igrf.evaluate(atEquator,2030.0001)}
        assertEquals(2026.0,Igrf14.decimalYear(Instant.parse("2026-01-01T00:00:00Z")),1e-9)
    }

    @Test fun igrf14RealFieldInsertedIntoPathIntegrator() {
        val station=GroundStation(35.0,-105.0)
        val ground=station.ecefM()
        val satellite=ground+station.up()*550000.0+station.east()*250000.0
        val igrf=Igrf14.fromBundledCoefficients()
        val now=Instant.parse("2026-10-25T12:00:00Z")
        val uniform=ElectronDensityProvider {_,_-> 1e11} // lab fixture, not real IRI
        val a=FaradayIntegrator.integrate(satellite,ground,now,145e6,uniform,igrf,"IGRF14 + synthetic Ne",128)
        val b=FaradayIntegrator.integrate(satellite,ground,now,435e6,uniform,igrf,"IGRF14 + synthetic Ne",128)
        assertTrue(a.angleRad.isFinite()&&abs(a.angleRad)>1e-10)
        assertEquals(9.0,a.angleRad/b.angleRad,1e-9)
        assertTrue(a.slantTecElectronsPerM2>5e16)
    }

    @Test fun temeToEcefValladoGeometryAndVelocity() {
        val time=Instant.parse("2026-10-25T12:00:00Z")
        val state=TemeStateKm(Vector3(7000.0,0.0,1000.0),Vector3(0.0,7.5,0.5))
        val eop=EarthOrientation(0.1,0.13,-0.21,0.001,"EOP test fixture")
        val ecef=TemeFrames.toEcef(state,time,eop)
        assertEquals(state.positionKm.norm()*1000.0,ecef.positionM.norm(),1e-5)
        assertTrue(ecef.velocityEcefMps.norm() in 6000.0..8000.0)
        val zero=TemeFrames.toEcef(state,time,EarthOrientation.zeroApproximation())
        assertTrue((ecef.positionM-zero.positionM).norm()>0.01)
        assertTrue((ecef.positionM-zero.positionM).norm()<1000.0)
        val gmst=TemeFrames.gmst1982Radians(time,eop.dut1Seconds)
        assertTrue(gmst in 0.0..(2*PI))
    }

    private fun checksum(s:String):String {
        val left=s.padEnd(68,' ').take(68)
        val sum=left.sumOf{when {
            it in '0'..'9' -> it-'0'
            it=='-' -> 1
            else -> 0
        }}
        return left+(sum%10)
    }

    @Test fun copiedSgp4ReturnsFullTemePositionAndVelocity() {
        val l1=checksum("1 25544U 98067A   24182.51187500  .00014091  00000-0  25410-3 0  999")
        val l2=checksum("2 25544  51.6408  75.2780 0006003  97.9389  16.6024 15.5003712445998")
        val data=TleStateBridge.parse(l1,l2,"ISS")
        assertEquals(25544,data.catnum)
        val time=Instant.parse("2024-06-30T12:00:00Z")
        val teme=TleStateBridge.propagateTeme(data,time)
        assertTrue(teme.positionKm.norm() in 6600.0..7300.0)
        assertTrue(teme.velocityKmPerSecond.norm() in 7.0..9.0)
        val ecef=TleStateBridge.propagateItrf(data,time,EarthOrientation.zeroApproximation())
        assertTrue(ecef.positionM.norm() in 6600000.0..7300000.0)
        assertTrue(ecef.velocityEcefMps.norm() in 6500.0..8200.0)
        assertFailsWith<IllegalArgumentException> {
            TleStateBridge.parse(l1.dropLast(1)+"9",l2)
        }
    }

    @Test fun iriSampleGridRequiresExactEpochAndPath() {
        val time=Instant.parse("2026-10-25T00:00:00Z")
        val sat=Vector3(7000000.0,0.0,0.0); val ground=Vector3(6500000.0,0.0,0.0)
        val points=(0 until 4).map { i ->
            val x=7000000.0-500000.0*(i+0.5)/4.0
            "$x\t0.0\t0.0\t1.0e11"
        }
        val raw="# source=PyIRI-test-fixture;F107=120.0;time=$time\n"+
            "x_m\ty_m\tz_m\tNe_m-3\n"+points.joinToString("\n")
        val profile=IriPathSamples.parseTsv(raw)
        val actual=FaradayIntegrator.integrate(sat,ground,time,435e6,profile,
            MagneticFieldProvider {_,_->Vector3(-20e-6,0.0,0.0)},"PyIRI synthetic fixture",4)
        assertEquals(5e16,actual.slantTecElectronsPerM2,100.0)
        assertFailsWith<IllegalArgumentException> { profile.electronsPerCubicMetre(sat,time) }
        assertFailsWith<IllegalArgumentException> {
            profile.fresh().electronsPerCubicMetre(sat,time.plusSeconds(60))
        }
    }
}
