# SatPolEngine

Portable orbital geometry, ionospheric propagation and polarization research
engine, with a future **minimal presentation-only bridge** to
[Look4Sat-FT4](https://github.com/Iu-yang1/Look4Sat-FT4).

The final Look4Sat tracker should display **only optimal receive polarization
angle and suggested CW/CCW direction**; research diagnostics belong here.

## Implemented

- **IGRF-14** degree/order 13 geomagnetic field in Tesla/ECEF, using the
  official 2020/2025 Gauss coefficients and 2025–2030 secular variation.
- **Look4Sat SGP4/SDP4** orbital code selectively ported under GPL with original
  copyright notices, with a new full **TEME position and velocity** output.
- **Vallado TEME→ITRF/ECEF** coordinate and velocity conversion: UTC+UT1,
  GMST82, polar motion and excess length of day; mandatory explicit EOP.
- **IERS EOP CSV** loader with linear interpolation, source labels, staleness
  rejection and leap-second discontinuity guard (the official EOP file
  download/parser is not yet integrated).
- **IRI-family electronic density** research export: actual PyIRI
  climatological model evaluation on finite satellite-to-ground sample points,
  using externally supplied, dated F10.7; exact-path Kotlin import.
- WGS-84 ground coordinates, Earth-fixed look basis and explicit attitude
  model hypotheses; Jones/Stokes/ellipse axis; first-order signed Faraday
  integrator; 180°-equivalent roll planning and mechanical limits.
- **Fail-closed** unknown-attitude and missing-propagation states.

## Not yet implemented / restrictions

- The *official Fortran IRI-2026* library and its updated indices are **not
  bundled**. PyIRI is a separately versioned IRI-family implementation; its
  profiles are climatological, not direct electron-density measurements.
- GloTEC / IGS live TEC assimilation and a complete real-time IERS EOP
  downloader/validator are not implemented. No accurate operational ionosphere
  prediction should be inferred without fully specified sources and timestamps.
- Dynamic real satellite quaternion telemetry and antenna vector patterns,
  calibration of propagation/polarization sign and real polarimetric hardware
  verification are outstanding; tests are primarily synthetic plus official
  Gauss coefficient inputs.
- No Look4Sat Android UI modification has been made.
- Hardware third-axis CW/CCW requires the *actual* calibrated POL position and
  direction mapping; AZ/EL rotator feedback is not POL feedback.

## Quick checks

Requires JDK 17+, Gradle 8.14+, Python 3.12+ for research scripts:

```sh
gradle test
python -m unittest discover -s research/tests -v
```

To run the **actual PyIRI model**, install its external Python package:

```sh
python -m pip install PyIRI numpy
python research/iri_export_path.py research/iri-request.example.json pyiri-samples.tsv
```

The repository also includes the manual workflow **PyIRI model integration**,
which installs PyIRI, executes the export and publishes a TSV test artifact.
An example request contains an **illustrative F10.7 of 120 SFU, not an
observed value**. Supply a defensible real-world dated index before treating
the output as a model-based estimate for a particular pass.

Key classes:

- `Igrf14.fromBundledCoefficients()`: official IGRF-14 model inputs.
- `TleStateBridge.parse`, `propagateTeme`, `propagateItrf`: source orbit.
- `TemeFrames.toEcef` / `EarthOrientationTable.parseCsv`: frame & EOP.
- `IriPathSamples.parseTsv`: exact ray electron-density model samples.
- `FaradayIntegrator.integrate`: model propagation term.
- `PolarizationEngine.solveJones`: final receive-axis estimate, if all
  relevant inputs are established.

## Technical notes

- [Data sources, IGRF, IRI and orbit input](docs/data-sources.md)
- [Physical equations and validity](docs/physics.md)
- [Satellite attitude evidence](docs/satellite-evidence.md)
- [Future Look4Sat integration contract](docs/look4sat-integration.md)

## Licensing

GPL-3.0-or-later for the repository. Imported SGP4/SDP4 classes retain the
original `2019–2026 Arty Bishop and contributors` notices and corresponding
source attribution. The IGRF-14 coefficients are attributed to IAGA/IERS
with authoritative source links in the data provenance documentation.
The PyIRI package is a separately installed MIT-licensed dependency and is not
vendored here.
