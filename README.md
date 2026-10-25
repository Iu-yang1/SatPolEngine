# SatPolEngine

**Portable satellite polarization physics core and research harness**, designed to
be validated independently and **eventually integrated back into
[Look4Sat-FT4](https://github.com/Iu-yang1/Look4Sat-FT4)**.

The eventual tracker will display **only the final receive polarization angle
and suggested rotation direction**. Research diagnostics remain in this repository.

## Implemented in milestone 0.1

- WGS-84 observer ECEF coordinates, 3D line-of-sight and receive wavefront basis
- Body quaternion rotation and an **explicitly assumed** nadir/prograde LVLH example
- Ideal linear dipole projection; complex Jones, Stokes and ellipse-major-axis angle
- First-order signed Faraday line integration on the **actual LEO-to-ground path**
- 180°-periodic linear polarization angles, current POL feedback, finite roll limits
- Fail-closed states when attitude, ionosphere, pole/zenith geometry or motor
  position are unavailable; no fake final rotation recommendation
- Independent no-dependency Python numerical reference and Kotlin/CI tests

**Not yet implemented:** live IRI electron density, IGRF-14 spherical harmonics,
TEC assimilation, SGP4 frame transformer, real satellite attitude ingestion,
polarimetric RF measurement, or Android integration. Synthetic test conditions
are **not** actual satellite predictions.

## Build

Requires JDK 17+ and Gradle 8.14+ (no Gradle wrapper checked in yet):

```sh
gradle test
python -m unittest discover -s research/tests -v
```

## Portable API

```kotlin
val observer = GroundStation(latitudeDeg = 0.0, longitudeDeg = 0.0)
val satelliteEcef = observer.ecefM() + observer.up() * 500_000.0 +
    observer.north() * 500_000.0
val basis = requireNotNull(LookBasis.from(observer, satelliteEcef))

// Synthetic research fixture ONLY; replace with real IRI/IGRF integration.
val model = FaradayEstimate(Math.toRadians(7.0), 0.0, "synthetic fixture", 1)
val result = PolarizationEngine.solveIdealDipole(
    observer, satelliteEcef, basis.elevationAxis,
    AttitudeEvidence.ASSUMED, model, currentMechanicalRollDeg = 0.0
)
// result.targetAngleDeg ~ 7°, rotation CLOCKWISE (mathematical convention)
```

A TLE cannot determine spacecraft attitude. Never treat the above **assumed**
body orientation or synthetic Faraday angle as an operational measurement.

See [physics notes](docs/physics.md) and
[Look4Sat integration contract](docs/look4sat-integration.md).

## License and origins

GPL-3.0-or-later, consistent with Look4Sat-FT4.
The geodesy and tracking-state concepts were reviewed for selective transfer;
the formulas here are independently expressed. Original upstream credits
remain with [rt-bishop/Look4Sat](https://github.com/rt-bishop/Look4Sat)
and its contributors.
