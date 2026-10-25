# Look4Sat-FT4 extraction / future re-integration

Source: https://github.com/Iu-yang1/Look4Sat-FT4 (GPL-3.0), `main`.

We reviewed the following interfaces as of October 2026:
- `core/domain/.../predict/GeoPos.kt`: geographic station location
- `core/domain/.../predict/OrbitalObject.kt`: SGP4/SDP4 and observer state
- `core/domain/.../predict/OrbitalPos.kt`: AZ/EL/distance but **not** a public full Cartesian state
- `feature/radar/.../RadarViewModel.kt`: per-second `tickPass()`, `orbitalPos`
- `feature/radar/.../RadarState.kt`: existing tracker state

This repository *selectively reproduces the independent mathematical
capabilities* (WGS-84 ground state, topocentric basis and orbit-state consumer)
without porting Look4Sat UI, CAT, CW, logbook, APRS or Android dependencies.

The future orbit bridge should:
1. obtain the original SGP4/TEME Cartesian position **and velocity** at UTC,
2. transform consistently to terrestrial frame with correct EOP and velocity terms,
3. create `OrbitStateEcef(positionM, velocityEcefMps)`,
4. resolve a **real** satellite attitude (or an explicitly ASSUMED model),
5. calculate an ideal-dipole vector or full emitted Jones response,
6. provide an actual ionospheric Faraday estimate with model provenance,
7. pass the current POL third-axis angle and hard mechanical limits to the solver,
8. render only `targetAngleDeg` and `recommendedRotation` in the radar UI.

**AZ/EL feedback is not POL feedback.** If current POL is not measured or
manually supplied, a target angle can exist but rotation direction is UNKNOWN.
If attitude/propagation are unknown, display a placeholder instead of 0°.
At exact zenith the AZ/EL polarization reference is singular; suspend
absolute roll recommendations rather than jumping direction. Never command
a motor from experimental model output without calibration and operator approval.

Units: WGS-84 ellipsoidal altitude in metres, Earth-fixed xyz in metres,
velocities in metres per second, frequency in Hz, rotation in degrees.

This first milestone **does not yet claim full TLE propagator extraction,
IRI/IGRF integration or Look4Sat UI modifications**.
