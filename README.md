# SatPolEngine

Portable orbital geometry, ionospheric propagation and polarization research
engine, with a future **minimal presentation-only bridge** to
[Look4Sat-FT4](https://github.com/Iu-yang1/Look4Sat-FT4).

The final Look4Sat tracker should display **only optimal receive polarization
angle and suggested CW/CCW direction**; research diagnostics belong here.

## Amateur satellite support without new TLM

An evidence-gated `AmateurPolarizationService` now supports **AO-7
Mode A/B, FO-29, SO-50, RS-44 and AO-73** by NORAD ID and documented
downlink frequency. It can identify nominal circular signals whose ideal
linear-feed roll optimum is undefined, and compute a separate ionospheric
Faraday term for supported VHF/UHF line-like or unverified downlinks.

**This is not yet an absolute SO-50/RS-44/AO-73 POL angle prediction.**
Satellite attitude and calibrated transmitter Jones patterns must not be
fabricated from a TLE or the term "linear transponder." A conditional
ideal-dipole solution is allowed only when an externally established
antenna axis and attitude model are explicitly provided, and unknown
or assumed spacecraft data never silently authorize motor movement.

[Source profiles, status contract and future TLM integration](docs/amateur-satellite-support.md).

## Real spacecraft RF profile and independent receiver validation

CASSIOPE / Swarm-E has a **documented 2231.5 MHz RHCP S-band downlink**
from patch antennas on the spacecraft's ±Z panels. We use a genuine
2017-06-24 one-second SP3 ITRF orbit and independently downloaded real
ITRF<-Body quaternion data, joined at precisely
**2017-06-24T22:32:14Z** (GPS time 22:32:32).
The receive station is a **synthetic sub-satellite reference site**, not
a historical RF observer. The selected model patch is +Z, and its geometric
off-boresight angle in that research geometry is about 38.48°.

**A perfect circularly polarized downlink has no unique ideal linear-feed
rotation optimum.** SatPolEngine returns angle UNKNOWN and direction UNKNOWN.
For algorithm validation only, a virtual LINEAR body-X dipole is considered
without claiming that it exists on the spacecraft. An independently written
complex-IQ receiver simulator performs 256 independent noise-realization
polarizer sweeps at 15 dB SNR. Its 95th-percentile angular error is about
0.596°; an illustrative uncertainty-prior Monte Carlo widens the 95th
percentile to about 9.63°. These are *simulated* errors, **not** measured
actual downlink prediction accuracy.

- [Real transmitter, orbit and independent-receiver scientific report](docs/real-transmitter-validation.md)
- [Reproducible same-epoch real-orbit and receiver experiment](https://github.com/Iu-yang1/SatPolEngine/actions/runs/40165856890)
- [Source document: CASSIOPE mission operations report](https://epop.phys.ucalgary.ca/wp-content/Swarm-E_Final_Operations_Report.pdf)

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
- **Official IRI-2026** source compilation and actual Fortran-derived path electron
  density, F2 peak metadata and dated F10.7 from official daily indices.
  Independent PyIRI remains an optional research baseline.
- **NOAA GloTEC 2D F2 observations**: archived NetCDF read, time and quality
  checks, limited peak-density correction of official IRI profiles, plus
  source-specific diagnostics. This is not full 3D ionospheric tomography.
- **Real CASSIOPE/Swarm-E attitude**: independently checked BODY->ITRF
  quaternion time series and strict CCSDS AEM A2B converter/reader.
- **Calibrated receiver POL only**: receiver-axis zero/sign and mechanical
  limits must be supplied before reporting CW/CCW or motor targets.
- **IRI-family electronic density** research export: actual PyIRI
  climatological model evaluation on finite satellite-to-ground sample points,
  using externally supplied, dated F10.7; exact-path Kotlin import.
- WGS-84 ground coordinates, Earth-fixed look basis and explicit attitude
  model hypotheses; Jones/Stokes/ellipse axis; first-order signed Faraday
  integrator; 180°-equivalent roll planning and mechanical limits.
- **Fail-closed** unknown-attitude and missing-propagation states.

## Not yet implemented / restrictions

- The official IRI-2026 Fortran model is downloaded and compiled in CI, not
  redistributed; the daily indices and reference coefficients must be updated
  for operational use. It is a model, not a direct path-resolved density measurement.
- GloTEC archived F2 correction is implemented and tested, but full 3D TEC
  assimilation, independent GNSS validation, live IGS ingest and a complete
  real-time IERS EOP downloader/validator are not implemented. No accurate operational ionosphere
  prediction should be inferred without fully specified sources and timestamps.
- Real CASSIOPE attitude data have been verified, but documented transmitter
  antenna field vectors, calibrated Faraday sign and real RF polarization
  comparison for FO-29, SO-50, RS-44 and ISS remain outstanding.
  Correct spacecraft quaternion math alone does **not** validate downlink POL.
- No Look4Sat Android UI modification has been made.
- Hardware third-axis CW/CCW requires explicit measured zero, sign, current
  POL angle and limits; AZ/EL rotator feedback is not POL feedback.
  A theoretical sky polarization angle alone gives direction UNKNOWN.

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

- [2026-10-25 independent validation and remaining scientific gaps](docs/validation-2026-10-25.md)
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
