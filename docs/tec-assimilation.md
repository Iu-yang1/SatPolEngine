# GloTEC / IGS 2-D VTEC assimilation design (investigation, 2026-10-25)

## Retrieved public products

- NOAA GloTEC (near real-time): https://www.spaceweather.gov/products/glotec
- NOAA machine-readable directory: https://services.swpc.noaa.gov/products/glotec/
  - `netcdf_2d_urt/` — public 2-D output; lat, lon, UTC, TEC, quality_flag.
  - `geojson_2d_urt/` — map delivery format.
  - Documentation says `quality_flag` is the mean number of assimilated
    F-region observations per vertical profile, quantized to 0–5. It is a
    measure of assimilation support, **not** calibrated TEC error variance.
- IGS combined global ionosphere TEC:
  https://igs.org/products/
  - Standard IONEX 2.5° latitude by 5° longitude, ~2-hour maps.
  - Rapid (~24h latency), final (~11 days), and other predicted/realtime options.
  - Products: `IGS0OPSRAP_yyyyddd0000_01D_02H_GIM.INX.gz` etc.
  - Download directory: https://cddis.nasa.gov/archive/gnss/products/ionex/YYYY/DDD/

## What these maps DO and DO NOT establish

The output of GNSS ionosphere models is normally a *vertical column* from
Earth through the GNSS satellite (or a product-specific ionospheric boundary),
in TECU, not the partial slant electron column between ground and an arbitrary
400-800 km LEO satellite.

The physically required Faraday integrand is
`Ne(geographic latitude, longitude, altitude, UTC) * B_parallel`.
A 2-D TEC map cannot uniquely invert a 3-D electron-density profile.

## Proposed assimilation algorithm

1. Select a dated, signed model profile: `Ne_b(lat,lon,h,time)` from IRI/PyIRI.
2. Load the correct observation time, grid reference, map units (TECU), flags,
   missing-value markers, and provenance for GloTEC/IONEX.
3. Interpolate VTEC in longitude with periodic wrapping, in latitude and
   between actual map epochs. For IONEX parse the `EXPONENT` header.
   Do not extrapolate stale or unavailable time slices.
4. Model the *same* GNSS VTEC observation operator on the background profile,
   including upper electron density / plasmaspheric contribution where required.
   If the model stops at 2000 km and does not represent higher-altitude
   electrons, do not compare the truncated integral directly to the full VTEC.
5. Correct profile parameters (e.g. NmF2, hmF2, B0/B1) using the actual
   observation operator. A simple scale factor `VTEC_obs/VTEC_background`
   is a **limited diagnostic baseline** only, because it leaves F2 peak height
   and shape unchanged and may misallocate topside TEC below the spacecraft.
6. A formal regularized correction could minimize
   `||H(Ne)-y||_(R^-1)^2 + ||Ne-Ne_b||_(P^-1)^2`, with observation covariance
   `R` validated independently. NOAA's quality_flag alone is not `R`.
7. Along the finite ground-to-LEO path, interpolate the corrected Ne field
   in 3-D and integrate `Ne*(B·k)` using the existing IGRF14 provider.
8. Quantify effect on total POL prediction with propagation and attitude
   uncertainty separately, compare historical passes with dual-polarization
   observations, and disable absolute roll when spacecraft attitude unknown.

## Priority and acceptance tests

- P0: IONEX parser / NetCDF decoder with correct time and TECU scaling.
- P1: Longitude seam (+180/-180), missing pixels, sparse coverage, altitude
  mismatch, and look-ahead bias tests.
- P2: Assimilation at 10+ geographically diverse locations and multiple days
  versus an independent IGS analysis center, with age/source tracking.
- P3: Controlled geomagnetic storm and low-elevation sensitivity experiments,
  showing the change in Faraday phase and uncertainty at 145 and 435 MHz.

Archival NOAA GloTEC F2 model correction is now implemented and tested
along a synthetic ground-to-LEO ray. It uses actual timestamped observed-
assimilated GloTEC F2 NmF2/hmF2 maps but **not** direct user-receiver
measurements, 3D tomography, real-time IGS ingest, uncertainty covariance,
or independent RF POL validation. See `validation-2026-10-25.md` for results.
