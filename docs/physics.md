# Physical model and scientific evidence

## Definition and limitations

The **receiver polarization axis** is a line with a 180° period; a mechanical direction
requires a separately calibrated current roll position. It is **not** inferred from TLE
or an AZ/EL rotator. The internal basis is axis0 = projected local UP in the wavefront
(elevation-up), axis1 = increasing azimuth to the right for an observer looking toward
the satellite. Positive rotation is from axis0 toward axis1. Hardware CW/CCW labels
need a separate mechanical-sign calibration.

The raw SGP4 state is TEME, while the engine consumes **ITRF/ECEF metres and
rotating-ECEF metres/second**. An accurate upstream adapter is responsible for UTC,
UT1/EOP, terrestrial reference-frame conversion, and source epoch quality. The engine's
optional nadir/prograde attitude frame is explicitly **ASSUMED** unless supported by
documented satellite behavior or telemetry. A TLE provides no body attitude.

For a far-field ideal dipole with axis `p` and ground-to-space line of sight `s`,
the observed transverse electric field is proportional to:
```
p_transverse = p - (p·s) s
```
If its length vanishes, polarization angle is undefined (dipole null).

For a general complex E field in the receive wavefront basis, Jones and Stokes:
```
S0 = |E0|²+|E1|²
S1 = |E0|²-|E1|²
S2 = 2 Re(E0 conj(E1))
psi = 0.5 atan2(S2,S1) modulo 180°
```
If S1 and S2 both vanish, the major-axis angle is undefined (circular/zero
linear Stokes content), and the engine refuses to recommend a roll.

## First-order Faraday rotation

```
psi_F [rad] = 2.3646e4 / f_Hz² · integral Ne[m^-3] · B_parallel[T] · ds[m]
```
The software *defines* propagation from satellite to ground and positive
B_parallel as a positive mathematical Jones rotation axis0 -> axis1. Operational
handedness needs validation against known measured geometry and the selected
time-harmonic/IEEE circular-polarization convention before motor actuation.

The integrator operates only on the finite LEO-to-receiver ray and retains
the **signed** magnetic projection, so it never silently equates GNSS full-column
VTEC with a shorter LEO slant segment.

The engine now bundles official IGRF-14 2020/2025/SV coefficients and the
spherical-harmonic field evaluator. An optional PyIRI path-density exporter
can provide an actual IRI-family climatological profile, but it is **not**
the official IRI-2026 Fortran implementation. Synthetic uniform electron
densities appear **only** in physics tests. Atmospheric bending, ray splitting, scintillation,
attitude uncertainty and antenna-vector patterns require further work.

## Data providers planned

- CelesTrak GP (TLE/OMM) for orbit elements: https://celestrak.org/NORAD/documentation/gp-data-formats.php
- IRI (model versions must be recorded): https://www.irimodel.org/
- NOAA GloTEC for TEC constraints, not an assumed publicly available 3D volume: https://www.spaceweather.gov/products/glotec
- IGS IONEX TEC as external validation: https://igs.org/products/
- IGRF-14 main field plus secular variation: https://www.ncei.noaa.gov/products/international-geomagnetic-reference-field
- IERS EOP: https://www.iers.org/
- Attitude: operator telemetry, spacecraft documentation or calibrated observations; otherwise UNKNOWN.

The propagation reference is **ITU-R P.531-16 (2025)**:
https://www.itu.int/rec/R-REC-P.531/en

## Provenance and validity contract

Never issue a numerically certain optimal receive angle when either the emitted
Jones vector or the ionospheric rotation is missing. `FaradayEstimate` accepts a
mandatory non-blank source label. Simulated vacuum propagation must be explicitly
tagged as such and never shown as a real observation. `AttitudeEvidence.ASSUMED`
must be surfaced as model-dependent to any downstream integrator.

Results are not suitable for automatic polarization rotator actuation until
end-to-end sign calibration, live IRI/IGRF integration, data staleness handling,
and measured validation are completed.
