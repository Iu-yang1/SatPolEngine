# Amateur satellite downlink polarization support (phase 0.3)

## Scope: actionable behavior now, without new onboard telemetry

`AmateurSatelliteCatalog` and `AmateurPolarizationService` implement a
**source-backed downlink-specific** polarization classification, plus a
Faraday-only fallback for noncircular/unknown waveforms. This service is
safe to call for a given UTC and an already propagated real ECEF orbit.
It does NOT imply an operational RF measurement, automated motor control,
or a satellite-specific absolute-angle prediction when spacecraft attitude
is unknown.

The aim is to eventually display only the final angle and recommended
rotation direction in Look4Sat-FT4, but intermediate `faradayAngleDeg`
**must not be displayed as the final absolute POL angle**.

### Supported profiles

| Spacecraft | NORAD | Receiver downlink (nominal) | Source-backed classification | Without TLM |
| --- | ---: | --- | --- | --- |
| AO-7 Mode B | 7530 | 145.925–145.975 MHz | nominal circular canted turnstile VHF | No unique ideal linear roll; direction UNKNOWN |
| AO-7 Mode A | 7530 | 29.400–29.500 MHz | HF downlink dipole, nominal linear | No angle; the present VHF quasi-longitudinal Faraday approximation is **not extended into HF** |
| FO-29 V/U | 24278 | 435.795 beacon / 435.800–435.900 MHz transponder | U-band nominal RHCP, **historical specification** | No unique ideal linear roll; no claim on actual off-axis ellipticity |
| SO-50 FM | 27607 | 436.795 MHz | quarter-wave whip canted 45°, approximate linear | IRI-2026 + IGRF-14 Faraday term, but no total target without attitude |
| RS-44 linear transponder | 44909 | 435.610–435.670 MHz | RF downlink polarization **unverified** | IRI-2026 + IGRF-14 Faraday term only |
| AO-73 / FUNcube-1 | 39444 | 145.950–145.970 MHz | RF downlink polarization **unverified** | IRI-2026 + IGRF-14 Faraday term only |

**Note**: A *linear transponder* is an SSB/CW signal relay. The phrase
does **NOT** describe RF polarization; do not map AO-73 or RS-44's
transponder types to linear receive polarization without antenna evidence.

Primary sources:

- AO-7 turnstile and HF dipole: https://www.amsat.org/two-way-satellites/ao-7/
- AO-7 antenna engineering history:
  https://amsat.org/wordpress/wp-content/uploads/2024/10/AO-7-History-Lesson-Complete_241015.pdf
- FO-29 historical V/U RHCP:
  https://www.jamsat.or.jp/ml/news/200905/msg00011.html
- FO-29 transponder frequencies:
  https://www.amsat.org/two-way-satellites/fo-29-jas-2/
- SO-50 quarter-wave UHF mounting and frequencies:
  https://www.amsat.org/two-way-satellites/so-50-satellite-information/
- RS-44, AO-73 downlink frequencies:
  https://satdb.amsat.org/
  https://www.amsat.org/live-linear-satellites/

No source here proves that any mode was transmitting during a specific
user-observed pass. The catalogue is a hardware-and-nominal-band dataset,
**not** a real-time satellite operational-state service.

## How the service handles each result

| Status | targetAngleDeg | faradayAngleDeg | CW/CCW |
| --- | --- | --- | --- |
| `NOMINAL_CIRCULAR_NO_UNIQUE_ROLL` | null | null | UNKNOWN |
| `FARADAY_ONLY_ATTITUDE_UNKNOWN` | null | numerical if actual model data were provided | UNKNOWN |
| `ABSOLUTE_ANGLE_UNAVAILABLE` | null | null | UNKNOWN |
| `LOW_FREQUENCY_OUTSIDE_APPROXIMATION` | null | null | UNKNOWN |
| `ANGLE_CONDITIONAL_ON_ASSUMPTIONS` | numerical, explicitly conditional | numerical | UNKNOWN |
| `ANGLE_MODELLED_NOT_RF_VALIDATED` | numerical (if real attitude, documented antenna and propagation exist) | numerical | only if physical POL calibration is additionally supplied |
| `BELOW_HORIZON` or `UNSUPPORTED_DOWNLINK` | null | null | UNKNOWN |

A nominal circular wave **does not impose a unique linear-polarizer
rotation**. In a perfectly circular case, a linear feed receives exactly
half the available polarized power at any mechanical roll angle,
corresponding to 3.0103 dB ideal polarization loss. Real antenna axial
ratio, local reflections, spacecraft attitude or ionospheric
depolarization can change the observed scene; this is a
`NOMINAL_CIRCULAR` classification and not a guarantee of constant
actual received signal power.

For SO-50, a mechanically canted monopole is only a line-like
approximation; the spacecraft frame orientation, active downlink antenna
Jones vector, chassis coupling and attitude telemetry are missing.
`faradayAngleDeg` is explicitly a component, not the final target.
No raw TLE-to-POL empirical shortcut is implemented.

## Example of an already verified, matched-date IRI/IGRF input

```kotlin
val station = GroundStation(latitudeDeg = 40.0, longitudeDeg = -105.0)
val spacecraft = /* real, correctly transformed Earth-fixed orbit state */
    OrbitStateEcef(positionM, velocityEcefMps)

val iriPath = IriPathSamples.parseTsv(iri2026RaySamples)
val output = AmateurPolarizationService.predict(
    AmateurPolarizationService.Inputs(
        noradId = 27607,
        downlinkFrequencyHz = 436_795_000.0,
        utc = observationUtc,
        station = station,
        orbit = spacecraft,
        density = iriPath.fresh(),
        magnetic = Igrf14.fromBundledCoefficients(),
        propagationSource = iriPath.provenance
    )
)
// With no trustworthy attitude: output.targetAngleDeg == null.
// A computed output.faradayAngleDeg is NOT an absolute receive POL target.
```

The density TSV is an **ordered midpoint profile** and requires exact
geometry and UTC agreement with the orbit above; do not reuse the sample
of one pass for another. The existing `IriPathSamples` parser enforces
sample coordinate alignment and timestamp. The IRI export and GloTEC
limited F2 correction remain the physically labelled research path.

## Integrating future TLM without unsafe shortcuts

If satellite onboard telemetry becomes available, add a separately
versioned satellite-specific decoder. It must supply:

1. Sensor frame definitions and mounting, quaternion component ordering
   or magnetometer/sun-sensor calibration and reference frames.
2. Satellite **sample acquisition UTC** and latency/time sync,
   measured or bounded angular rates and quality/uncertainty flags.
3. Frequency-specific transmitting RF antenna element vector/Jones
   pattern and how it transforms from the spacecraft body frame.
4. Geomagnetic/ionospheric source time and total uncertainty.
5. Independent calibrated POL motor zero, sign and hard-stop limits for
   CW/CCW. `AZ/EL` feedback is **not** POL feedback.

`AttitudeAxisModel`, `TelemetryAttitude` and `CcsdsAttitude` already
accept calibrated time-tagged attitude. In this phase, no real SO-50,
RS-44, AO-73 or FO-29 fresh TLM has been ingested.

## Acceptance and caveats

All six catalog profiles and their status transitions have unit tests:
uplinks do not match downlink profiles, AO-7 modes remain different,
SO-50 with IGRF but no attitude stays Faraday-only, nominal circular
downlinks never command an uncalibrated motor, and assumed SO-50 body
attitude remains conditional.

The current branch may be used as a **non-actuating research prediction
service**. Full operational absolute antenna rotation guidance for an
individual linear downlink still requires actual attitude, RF antenna
pattern and independently measured polarization agreement.
