package org.satpol

import java.time.Instant
import kotlin.math.*

/**
 * Supported AMATEUR DOWNLINK wave classes, not claims about the instantaneous
 * polarization received on the ground.
 *
 *  - NOMINAL_CIRCULAR: physically documented CP antenna or flight polarization;
 *    may become elliptical/off-axis after antenna effects, reflections, etc.
 *  - NOMINAL_LINEAR: a documented downlink linear antenna; full Jones pattern
 *    and time-resolved attitude may still be unknown.
 *  - WHIP_LINEAR_APPROXIMATION: quarter-wave radiator often approximated as
 *    linear, but spacecraft chassis and off-axis radiation can change it.
 *  - UNVERIFIED: frequency is documented, downlink polarization is not.
 */
enum class DownlinkWaveClass {
    NOMINAL_CIRCULAR,
    NOMINAL_LINEAR,
    WHIP_LINEAR_APPROXIMATION,
    UNVERIFIED
}

data class AmateurDownlinkProfile(
    val noradId: Int,
    val name: String,
    val mode: String,
    val centerFrequencyHz: Double,
    val minFrequencyHz: Double,
    val maxFrequencyHz: Double,
    val waveClass: DownlinkWaveClass,
    val sourceUrl: String,
    val evidence: String,
    val limitations: String
) {
    init {
        require(noradId > 0 && name.isNotBlank() && mode.isNotBlank())
        require(centerFrequencyHz in minFrequencyHz..maxFrequencyHz)
        require(minFrequencyHz.isFinite() && maxFrequencyHz.isFinite())
        require(minFrequencyHz > 0.0)
        require(sourceUrl.startsWith("https://"))
        require(evidence.isNotBlank() && limitations.isNotBlank())
    }
}

/**
 * Profiles are intentionally explicit by DOWNLINK MODE. AO-7's HF Mode A and
 * VHF Mode B have different physical antennas and polarization types.
 *
 * Current operational state is NOT inferred from inclusion in this catalogue.
 * In particular old FO-29 RHCP records are nominal specifications, not
 * calibrated in-flight per-pass axial-ratio evidence.
 */
object AmateurSatelliteCatalog {
    val entries: List<AmateurDownlinkProfile> = listOf(
        AmateurDownlinkProfile(
            7530, "AO-7", "Mode B VHF downlink",
            145_950_000.0, 145_925_000.0, 145_975_000.0,
            DownlinkWaveClass.NOMINAL_CIRCULAR,
            "https://www.amsat.org/two-way-satellites/ao-7/",
            "AMSAT: VHF/UHF canted turnstile fed in quadrature for nominal CP",
            "Actual far-field axial ratio and handedness vary with observation direction; "
                + "Mode B may be inactive when not sunlit"
        ),
        AmateurDownlinkProfile(
            7530, "AO-7", "Mode A HF downlink",
            29_450_000.0, 29_400_000.0, 29_500_000.0,
            DownlinkWaveClass.NOMINAL_LINEAR,
            "https://www.amsat.org/two-way-satellites/ao-7/",
            "AMSAT: HF dipole antenna for Mode A, not the VHF CP turnstile",
            "HF ray magnetoionic propagation differs from quasi-longitudinal "
                + "VHF/UHF Faraday approximation; no instantaneous spacecraft attitude"
        ),
        AmateurDownlinkProfile(
            24278, "FO-29 / JAS-2", "V/U analog transponder",
            435_850_000.0, 435_795_000.0, 435_900_000.0,
            DownlinkWaveClass.NOMINAL_CIRCULAR,
            "https://www.jamsat.or.jp/ml/news/200905/msg00011.html",
            "JAMSAT historical technical summary lists U downlink polarization RHCP",
            "Historical nominal RHCP is not a measured instantaneous axial ratio "
                + "or verification of transponder activity"
        ),
        AmateurDownlinkProfile(
            27607, "SO-50 / SaudiSat-1C", "UHF FM downlink",
            436_795_000.0, 436_775_000.0, 436_815_000.0,
            DownlinkWaveClass.WHIP_LINEAR_APPROXIMATION,
            "https://www.amsat.org/two-way-satellites/so-50-satellite-information/",
            "AMSAT: quarter-wave UHF radiator in bottom corner, canted 45 degrees inward",
            "Quarter-wave whip is not a measured Jones pattern; exact body-frame "
                + "azimuth, phase, and current attitude are unavailable"
        ),
        AmateurDownlinkProfile(
            44909, "RS-44 / DOSAAF-85", "UHF linear transponder",
            435_640_000.0, 435_610_000.0, 435_670_000.0,
            DownlinkWaveClass.UNVERIFIED,
            "https://satdb.amsat.org/",
            "AMSAT catalogue identifies the UHF downlink passband",
            "Linear transponder is a modulation architecture, NOT proof of "
                + "linearly polarized RF; actual downlink antenna Jones vector unknown"
        ),
        AmateurDownlinkProfile(
            39444, "AO-73 / FUNcube-1", "VHF analog transponder",
            145_960_000.0, 145_950_000.0, 145_970_000.0,
            DownlinkWaveClass.UNVERIFIED,
            "https://www.amsat.org/live-linear-satellites/",
            "AMSAT describes VHF transponder and 145.935 MHz telemetry",
            "A 'linear transponder' means SSB/CW relay, not linear RF polarization; "
                + "instantaneous attitude and transmit antenna vector are not established"
        )
    )

    /**
     * Match a NOMINAL downlink RF frequency, not an uplink.
     * Do not use large arbitrary Doppler tolerances or match satellites by name.
     */
    fun find(noradId: Int, downlinkHz: Double): AmateurDownlinkProfile? {
        require(downlinkHz.isFinite() && downlinkHz > 0.0)
        val choices = entries.filter {
            it.noradId == noradId &&
                downlinkHz in it.minFrequencyHz..it.maxFrequencyHz
        }
        return choices.minByOrNull { abs(downlinkHz - it.centerFrequencyHz) }
    }
}

enum class AmateurPolarizationStatus {
    UNSUPPORTED_DOWNLINK,
    BELOW_HORIZON,
    NOMINAL_CIRCULAR_NO_UNIQUE_ROLL,
    LOW_FREQUENCY_OUTSIDE_APPROXIMATION,
    FARADAY_ONLY_ATTITUDE_UNKNOWN,
    ABSOLUTE_ANGLE_UNAVAILABLE,
    ANGLE_CONDITIONAL_ON_ASSUMPTIONS,
    ANGLE_MODELLED_NOT_RF_VALIDATED
}

data class AmateurPolarizationAdvice(
    val profile: AmateurDownlinkProfile?,
    val status: AmateurPolarizationStatus,
    /** Receiving linear polarization axis; null if not scientifically identifiable. */
    val targetAngleDeg: Double?,
    /** Mathematical motor direction; never supplied without encoder calibration. */
    val direction: RotationDirection,
    /** For partial prediction only, NOT the final target antenna angle. */
    val faradayAngleDeg: Double?,
    val slantTecTecU: Double?,
    /** IDEAL circular-to-linear PLF only, not an in-flight calibration. */
    val idealCircularToLinearLossDb: Double?,
    val propagationSource: String?,
    val attitudeEvidence: AttitudeEvidence,
    val notes: String
)

/**
 * Minimal operation-safe amateur satellite frontend for SatPolEngine.
 *
 * Supports useful NO-ROTATION verdicts for documented nominal CP spacecraft,
 * and separate first-order Faraday term for linearly polarized / unknown modes.
 *
 * Does not infer attitude from TLE alone. With externally supplied antenna
 * axis and time-dependent attitude, solves a CONDITIONAL sky polarization
 * target, preserving an explicit model-evidence classification.
 */
object AmateurPolarizationService {
    /** First-order VHF/UHF model range. HF is deliberately not auto-predicted. */
    const val MIN_FARADAY_MODEL_FREQUENCY_HZ = 100_000_000.0

    data class Inputs(
        val noradId: Int,
        val downlinkFrequencyHz: Double,
        val utc: Instant,
        val station: GroundStation,
        val orbit: OrbitStateEcef,
        val density: ElectronDensityProvider? = null,
        val magnetic: MagneticFieldProvider? = null,
        val propagationSource: String? = null,
        /** Do not supply unless body->ECEF axis AND antenna mounting are known. */
        val attitudeAndAntennaAxis: AttitudeAxisModel? = null,
        val antennaAxisDocumentation: String? = null,
        val currentMotorRollDeg: Double? = null,
        val motorCalibration: RollCalibration? = null,
        val motorLimits: RollLimits? = null,
        val samples: Int = 256
    )

    fun predict(x: Inputs): AmateurPolarizationAdvice {
        require(x.downlinkFrequencyHz.isFinite() && x.downlinkFrequencyHz > 0.0)
        val p = AmateurSatelliteCatalog.find(x.noradId, x.downlinkFrequencyHz)
            ?: return AmateurPolarizationAdvice(
                null, AmateurPolarizationStatus.UNSUPPORTED_DOWNLINK, null,
                RotationDirection.UNKNOWN, null, null, null, null,
                AttitudeEvidence.UNKNOWN,
                "No source-backed downlink profile for this NORAD/frequency pairing"
            )

        val ray = (x.orbit.positionM - x.station.ecefM()).unit()
        if (ray.dot(x.station.up()) <= 0.0) {
            return partial(p, AmateurPolarizationStatus.BELOW_HORIZON,
                null, AttitudeEvidence.UNKNOWN,
                "Below geometric horizon; satellite mode/activity is not assumed")
        }

        if (p.waveClass == DownlinkWaveClass.NOMINAL_CIRCULAR) {
            return AmateurPolarizationAdvice(
                p, AmateurPolarizationStatus.NOMINAL_CIRCULAR_NO_UNIQUE_ROLL,
                null, RotationDirection.UNKNOWN, null, null,
                10.0 * log10(2.0), null, AttitudeEvidence.UNKNOWN,
                "Ideal CP to linear feed has constant 3.01 dB loss at any roll. "
                    + "This is a NOMINAL design property, not a measured axial ratio. "
                    + "No automatic POL movement is justified. " + p.limitations
            )
        }

        if (x.downlinkFrequencyHz < MIN_FARADAY_MODEL_FREQUENCY_HZ) {
            return partial(p, AmateurPolarizationStatus.LOW_FREQUENCY_OUTSIDE_APPROXIMATION,
                null, AttitudeEvidence.UNKNOWN,
                "HF magnetoionic propagation requires a more complete model; "
                    + "do not extrapolate VHF first-order Faraday to AO-7 Mode A")
        }

        val faraday = if (x.density != null && x.magnetic != null &&
            !x.propagationSource.isNullOrBlank()
        ) {
            FaradayIntegrator.integrate(
                x.orbit.positionM, x.station.ecefM(), x.utc,
                x.downlinkFrequencyHz, x.density, x.magnetic,
                x.propagationSource, x.samples
            )
        } else null

        val axis = if (x.attitudeAndAntennaAxis != null &&
            !x.antennaAxisDocumentation.isNullOrBlank()
        ) x.attitudeAndAntennaAxis.axisEcef(x.orbit, x.utc) else null

        if (axis != null && faraday != null) {
            // The source is explicitly a line-like feed or a measured body axis.
            // For UNVERIFIED spacecraft, an antenna-axis document alone is not
            // enough to establish the transmitted wave polarization.
            if (p.waveClass != DownlinkWaveClass.UNVERIFIED) {
                val solution = PolarizationEngine.solveIdealDipole(
                    x.station, x.orbit.positionM, axis.directionEcef,
                    axis.evidence, faraday, x.currentMotorRollDeg,
                    x.motorLimits, x.motorCalibration
                )
                if (solution.targetAngleDeg != null) {
                    val status =
                        if (axis.evidence == AttitudeEvidence.ASSUMED ||
                            p.waveClass == DownlinkWaveClass.WHIP_LINEAR_APPROXIMATION
                        ) AmateurPolarizationStatus.ANGLE_CONDITIONAL_ON_ASSUMPTIONS
                        else AmateurPolarizationStatus.ANGLE_MODELLED_NOT_RF_VALIDATED

                    // Conditional assumptions may never authorize even a
                    // calibrated motor recommendation.
                    val mechanicalDirection =
                        if (status == AmateurPolarizationStatus.ANGLE_MODELLED_NOT_RF_VALIDATED)
                            solution.recommendedRotation
                        else RotationDirection.UNKNOWN

                    return AmateurPolarizationAdvice(
                        p, status, solution.targetAngleDeg, mechanicalDirection,
                        faraday.angleDeg, faraday.slantTecElectronsPerM2 / 1e16,
                        null, faraday.source, axis.evidence,
                        "Ideal dipole / nominal line-like transmitter model; "
                            + "no real in-flight Jones pattern or RF polarimetry. "
                            + "Antenna axis evidence: ${x.antennaAxisDocumentation}"
                    )
                }
            }
        }

        return partial(
            p, if (faraday != null)
                AmateurPolarizationStatus.FARADAY_ONLY_ATTITUDE_UNKNOWN
            else AmateurPolarizationStatus.ABSOLUTE_ANGLE_UNAVAILABLE,
            faraday, axis?.evidence ?: AttitudeEvidence.UNKNOWN,
            "Faraday is a propagation component, NOT an absolute target angle. "
                + "Attitude, body-mounted RF Jones vector, or ionosphere is "
                + "unavailable / insufficient. " + p.limitations
        )
    }

    private fun partial(
        p: AmateurDownlinkProfile,
        status: AmateurPolarizationStatus,
        faraday: FaradayEstimate?,
        attitudeEvidence: AttitudeEvidence,
        notes: String
    ) = AmateurPolarizationAdvice(
        p, status, null, RotationDirection.UNKNOWN,
        faraday?.angleDeg, faraday?.slantTecElectronsPerM2?.div(1e16),
        null, faraday?.source, attitudeEvidence, notes
    )
}
