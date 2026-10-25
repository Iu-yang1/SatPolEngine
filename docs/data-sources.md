# Source integration, verification, and provenance (2026-10-25)

## Working model inputs

### IGRF-14 main geomagnetic field: implemented in Kotlin

`Igrf14.fromBundledCoefficients()` reads the IAGA degree/order 13 Schmidt
semi-normalized Gauss coefficients derived from the official **2020, 2025,
2025-2030 SV** columns. Interpolate 2020-2025 and extrapolate with published
2025-2030 secular variation. Evaluate geocentric spherical field derivatives
and transform spherical basis to ECEF; outputs Tesla vectors. This is a
main-field model, not an auroral-current perturbation model.
- Source: https://www.ngdc.noaa.gov/IAGA/vmod/coeffs/igrf14coeffs.txt
- Description: https://www.ncei.noaa.gov/products/international-geomagnetic-reference-field
- DOI: https://doi.org/10.1186/s40623-025-02360-0
- Valid dates implemented: 2020.0–2030.0 only.
- **Do not infer full operational validity** from analytical unit tests;
  compare to official NOAA/BGS evaluator at diverse latitudes and heights.

### IRI electron density: real PyIRI model export path implemented

`research/iri_export_path.py` runs the Python **PyIRI** climatological
model using documented `PyIRI.main_library.IRI_density_1day` with URSI F2
coefficient selection, user-supplied dated F10.7 [SFU] and a 5 km vertical
profile. Along the exact satellite->receiver midpoint sampling ray, it
interpolates altitudes and writes electrons/m³ into a precise path TSV.

It is **not** the official new IRI-2026 Fortran implementation and has
different options and data requirements; always use the `PyIRI-` source
identifier. No fabricated real-time F10.7, no synthetic real-time Ne, and
no extrapolation beyond the loaded finite ray. A zero estimate below
80 km is a declared approximation. The source is a physical
climatological model, **not real-time measurement**.

Official IRI-2026 bundle and index files:
- https://irimodel.org/IRI-2026/00_iri.zip (contains common files and coefficients)
- https://irimodel.org/ (latest apf107.dat and ig_rz.dat index files)

Optional Python alternative, MIT licensed:
- https://github.com/victoriyaforsythe/PyIRI
- pip: `pip install PyIRI`
- Reference: Forsythe et al., PyIRI, *Space Weather* (2023).

`IriPathSamples.parseTsv` feeds those density results into the Kotlin
Faraday integrator; it validates epoch and 1-m spatial agreement for every
lookup. Sampling providers are single-use; call `fresh()` to reintegrate
the same path.

## TLE and precise terrestrial frame: integrated

Look4Sat-FT4's SGP4 and SDP4 orbit prediction classes, author credit
and GPL notices were selectively ported under `org.satpol.orbit`.
`OrbitalObject.getTemeState` exposes *both* km and km/s, avoiding
deriving velocity numerically from AZ/EL.
`TleStateBridge.parse` validates NORAD line-number, matching catalog
ID and checksum for ordinary numeric catalog TLEs. Alpha-5 IDs / OMM are
a subsequent extension.

`TemeFrames.toEcef(teme,time,eop)` uses Vallado GMST1982, UT1-UTC
and polar motion (xp,yp) and LOD-corrected angular velocity to generate
rotating terrestrial position and velocity. EOP must be provided by the
calling application and contains a nonempty source label.

- https://celestrak.org/publications/AIAA/2006-6753/AIAA-2006-6753.pdf
- https://github.com/poliastro/vallado-software/blob/master/matlab/teme2ecef.m
- https://www.iers.org/IERS/EN/DataProducts/EarthOrientationData/eop.html

`EarthOrientation.zeroApproximation()` is **only** for approximate numerical
tests or non-precision fallback; it is not an accurate EOP dataset. Accurate
real-time EOP ingestion and SGP4 cross-validation against Vallado baseline
ephemerides are still outstanding.

## GloTEC and IGS: planned observational corrections

- NOAA SWPC GloTEC: https://www.spaceweather.gov/products/glotec
- IGS global ionosphere TEC products: https://igs.org/products/
- Do not conflate total GNSS VTEC with truncated ground-to-LEO slant TEC.
- Observation maps alone do not specify a complete 3D electron-density
  profile; plan background/observation assimilation with explicit altitude
  coverage, observation time and error covariance.
- If forecast observations are stale, do not silently assume they are current.

## Attitude limitations

TLE contains no spacecraft attitude or vector antenna pattern.
`NadirVelocityFrame` is an **assumed** model only; users must explicitly
identify trustworthy spacecraft source data. Without body attitude or
physical field vector, `PolarizationEngine` intentionally does not
output an operational POL target or CW/CCW direction.

No current results should be automatically used to actuate the third-axis
rotator until the sign, calibration, effective model error, and safety
requirements have passed live RF measurements.
