package org.satpol

import java.time.Instant
import kotlin.math.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class AmateurSatellitePolarizationTest {
    private val now=Instant.parse("2026-10-25T12:00:00Z")
    private val station=GroundStation(40.0,-105.0)
    private val high=OrbitStateEcef(
        station.ecefM()+station.up()*550_000.0+station.north()*200_000.0,
        Vector3(0.0,7500.0,0.0)
    )

    private fun request(id:Int, frequency:Double)=AmateurPolarizationService.Inputs(
        noradId=id,downlinkFrequencyHz=frequency,utc=now,
        station=station,orbit=high
    )

    @Test fun catalogueDistinguishesModeFromFrequency() {
        val modeA=assertNotNull(AmateurSatelliteCatalog.find(7530,29.45e6))
        val modeB=assertNotNull(AmateurSatelliteCatalog.find(7530,145.95e6))
        assertEquals(DownlinkWaveClass.NOMINAL_LINEAR,modeA.waveClass)
        assertEquals(DownlinkWaveClass.NOMINAL_CIRCULAR,modeB.waveClass)
        assertNull(AmateurSatelliteCatalog.find(7530,432.150e6)) // uplink, not downlink
        assertNull(AmateurSatelliteCatalog.find(27607,145.850e6)) // SO-50 uplink
        assertEquals("SO-50 / SaudiSat-1C",
            AmateurSatelliteCatalog.find(27607,436.795e6)?.name)
    }

    @Test fun fo29NominalCircularDoesNotRequestPolMovement() {
        // In this test a failing Ne provider MUST NOT be called, because
        // perfect nominal CP provides no unique roll axis without any Ne.
        val x=request(24278,435.850e6).copy(
            density=ElectronDensityProvider {_,_->error("must not sample ionosphere")},
            magnetic=MagneticFieldProvider {_,_->error("must not sample magnetism")},
            propagationSource="unused",currentMotorRollDeg=35.0,
            motorCalibration=RollCalibration(2.0,false,"measured synthetic rig")
        )
        val out=AmateurPolarizationService.predict(x)
        assertEquals(AmateurPolarizationStatus.NOMINAL_CIRCULAR_NO_UNIQUE_ROLL,out.status)
        assertNull(out.targetAngleDeg)
        assertNull(out.faradayAngleDeg)
        assertEquals(RotationDirection.UNKNOWN,out.direction)
        assertEquals(10.0*log10(2.0),
            assertNotNull(out.idealCircularToLinearLossDb),1e-12)
    }

    @Test fun ao7BNoUniqueAngleAo7ANoHfApproximation() {
        val b=AmateurPolarizationService.predict(request(7530,145.950e6))
        assertEquals(AmateurPolarizationStatus.NOMINAL_CIRCULAR_NO_UNIQUE_ROLL,b.status)
        val a=AmateurPolarizationService.predict(request(7530,29.450e6))
        assertEquals(AmateurPolarizationStatus.LOW_FREQUENCY_OUTSIDE_APPROXIMATION,a.status)
        assertNull(a.targetAngleDeg)
        assertNull(a.faradayAngleDeg)
    }

    @Test fun so50ReceivesRealIgrfFieldAndOnlyPropagationTerm() {
        val x=request(27607,436.795e6).copy(
            density=ElectronDensityProvider {_,_->8e10},
            magnetic=Igrf14.fromBundledCoefficients(),
            propagationSource="IGRF14 with SYNTHETIC Ne - SCIENCE TEST",
            samples=32,
            currentMotorRollDeg=14.0,
            motorCalibration=RollCalibration(0.0,true,"measured synthetic rig")
        )
        val output=AmateurPolarizationService.predict(x)
        assertEquals(AmateurPolarizationStatus.FARADAY_ONLY_ATTITUDE_UNKNOWN,output.status)
        assertNotNull(output.faradayAngleDeg)
        assertNotNull(output.slantTecTecU)
        assertNull(output.targetAngleDeg)
        assertEquals(RotationDirection.UNKNOWN,output.direction)
        assertTrue(output.notes.contains("NOT an absolute target"))
    }

    @Test fun unknownTransmitterRSPassbandNeverMeansLinearRFPolarization() {
        val x=request(44909,435.640e6)
        val out=AmateurPolarizationService.predict(x)
        assertEquals(DownlinkWaveClass.UNVERIFIED,out.profile?.waveClass)
        assertEquals(AmateurPolarizationStatus.ABSOLUTE_ANGLE_UNAVAILABLE,out.status)
        assertNull(out.targetAngleDeg)
        assertNull(out.faradayAngleDeg)
        assertEquals(RotationDirection.UNKNOWN,out.direction)
    }

    @Test fun ao73BpskBeaconIsNotArbitrarilyMappedToTransponder() {
        assertNull(AmateurSatelliteCatalog.find(39444,145.935e6))
        val out=AmateurPolarizationService.predict(request(39444,145.960e6))
        assertEquals(DownlinkWaveClass.UNVERIFIED,out.profile?.waveClass)
        assertNull(out.targetAngleDeg)
    }

    @Test fun spacecraftBelowHorizonIsRejectedBeforeAnyModelPrediction() {
        val orbit=OrbitStateEcef(station.ecefM()-station.up()*100_000.0,
            Vector3(0.0,7500.0,0.0))
        val out=AmateurPolarizationService.predict(
            request(24278,435.850e6).copy(orbit=orbit))
        assertEquals(AmateurPolarizationStatus.BELOW_HORIZON,out.status)
        assertNull(out.idealCircularToLinearLossDb)
        assertNull(out.targetAngleDeg)
    }

    @Test fun syntheticS050AttitudeIsConditionalNotMotorCommand() {
        val x=request(27607,436.795e6).copy(
            density=ElectronDensityProvider {_,_->8e10},
            magnetic=Igrf14.fromBundledCoefficients(),
            propagationSource="SYNTHTEST",samples=32,
            attitudeAndAntennaAxis=AssumedNadirPrograde(
                Vector3(1.0,0.0,0.0),"NOT actual SO-50 attitude"),
            antennaAxisDocumentation="SYNTHETIC antenna axis ONLY",
            currentMotorRollDeg=15.0,
            motorCalibration=RollCalibration(0.0,true,"synthetic motor"),
            motorLimits=RollLimits(-90.0,90.0)
        )
        val out=AmateurPolarizationService.predict(x)
        assertEquals(AmateurPolarizationStatus.ANGLE_CONDITIONAL_ON_ASSUMPTIONS,out.status)
        assertNotNull(out.targetAngleDeg)
        assertEquals(RotationDirection.UNKNOWN,out.direction)
        assertEquals(AttitudeEvidence.ASSUMED,out.attitudeEvidence)
    }

    @Test fun antennaAxisMustHaveProvenanceBeforeAnyAngle() {
        val x=request(27607,436.795e6).copy(
            density=ElectronDensityProvider {_,_->8e10},
            magnetic=Igrf14.fromBundledCoefficients(),
            propagationSource="LAB",samples=16,
            attitudeAndAntennaAxis=AssumedNadirPrograde(
                Vector3(0.0,1.0,0.0),"hypothesis"),
            antennaAxisDocumentation=null
        )
        val out=AmateurPolarizationService.predict(x)
        assertNull(out.targetAngleDeg)
        assertNotNull(out.faradayAngleDeg)
    }

    @Test fun unsupportedSatelliteNeverGetsInventedAngle() {
        val out=AmateurPolarizationService.predict(request(99999,436.795e6))
        assertEquals(AmateurPolarizationStatus.UNSUPPORTED_DOWNLINK,out.status)
        assertNull(out.profile)
        assertNull(out.targetAngleDeg)
        assertEquals(RotationDirection.UNKNOWN,out.direction)
    }
}
