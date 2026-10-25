# SatPolEngine scientific validation – 2026-10-25

## Scope

This report separates (a) computer-to-computer numerical checks, (b) true
external geophysical and satellite-attitude datasets, and (c) radio-link
polarization measurements. **Category (c) has NOT been completed.**

## Numerically verified results

| Path | Evidence | Meaning | Limit |
|---|---|---|---|
| IGRF-14 vs independent ppigrf | https://github.com/Iu-yang1/SatPolEngine/actions/runs/40160951393 | E/N/U field errors up to 0.064 nT at four cases | Not real geomagnetic measurements |
| Look4Sat SGP4 vs Python-sgp4 | https://github.com/Iu-yang1/SatPolEngine/actions/runs/40160805395 | ~2.13 m difference and ~0.0024 m/s at 3 ISS epochs | Single sample TLE; not an orbit determination |
| Original official IRI-2026 | https://github.com/Iu-yang1/SatPolEngine/actions/runs/40163243216 | Fortran IRI model, original indices and real F2 output | Climatological model, not measured electron density |
| NOAA archived GloTEC F2 correction | https://github.com/Iu-yang1/SatPolEngine/actions/runs/40163583700 | Real external archived model on matched UTC, quality-gated, source-stamped | Only F2 peak-density update, NOT 3D tomography |
| IRI + GloTEC + IGRF14 Faraday | https://github.com/Iu-yang1/SatPolEngine/actions/runs/40164771373 | Signed first-order Faraday integrals at 145/435 MHz | Synthetic ray geometry; electromagnetic sign not RF-calibrated |
| Real CASSIOPE spacecraft attitude | https://github.com/Iu-yang1/SatPolEngine/actions/runs/40163738170 | 25 real 1-Hz body-to-ITRF quaternions, source quality 4; Python vs Kotlin body-X error 6.53e-13 | **No documented RF transmit antenna axis** used |

### Matched historical NOAA + IRI example

UTC requested: **2026-10-20 12:00:00**. Actual archived NOAA GloTEC
sample used: **11:55 UTC**, 5 minutes old; minimum quality flag = 5.
Official IRI-2026's **actual** F10.7 input is approximately **122.157 SFU**.
The station and LEO position are deliberately *synthetic* research geometry,
not a confirmed TLE pass.

| Test measurement | Original IRI-2026 | Limited GloTEC correction |
|---|---:|---:|
| Truncated path sTEC | 3.70569658 TECU | 4.10302851 TECU |
| Faraday at 145 MHz | +93.9754° | +104.1139° |
| Faraday at 435 MHz | +10.4417° | +11.5682° |

Both calculations used the identical ray, UTC and geomagnetic coefficients.
The ratio of 145-MHz to 435-MHz rotation is 9 within the integration tolerance.
A positive number is the *software wavefront convention*; its physical sign
must be established with calibrated RF observations.

### Actual satellite quaternion checks

A source-attributed publicly archived **2017-06-24** CASSIOPE/Swarm-E
AttQuat CDF was downloaded from the University of Calgary, converted from
scalar-last quaternion records into a strictly labelled CCSDS-style BODY to
ITRF AEM, then parsed by Kotlin. The 2017-06-24T22:32:02–22:32:26Z segment
had 25 samples with reported attitude sensor provenance/quality code 4.
For the body +X *reference axis*, the independently calculated ECEF vector
at 22:32:14Z was (0.459289631570, -0.887127918607, -0.045355158030)
and agreed with the Kotlin result to ~6.53e-13 Euclidean norm.
This is a quaternion coordinate test, not a CASSIOPE RF polarization angle.

## Conservative display contract

1. **No verified attitude / antenna mounting evidence**: absolute POL = UNKNOWN.
2. **No time-valid electron density or field**: physical propagation POL = UNKNOWN.
3. Valid sky polarization angle but **no independent POL motor calibration**:
   display the angle if its assumptions are explicit; mechanical CW/CCW = UNKNOWN.
4. Known encoder angle plus measured axis zero/sign and hard stops: direction
   can be calculated as a mathematical *recommendation*, not a motor command.
5. ASSUMED attitude must remain visibly ASSUMED; no fabricated observational
   evidence or implication of a measured QSO.

## Outstanding before Look4Sat-FT4 production adoption

- Real satellite-specific transmitting antenna vector/Jones response, body
  mounting and time-resolved attitude for the requested satellite.
- Actual receiver POL zero/sign test and mechanical stop feedback.
- RF polarization measurements over passes, preferably dual-polarized
  reception, to verify predicted angle **and sign**, including multipath,
  ellipticity, satellite antenna nulls and Faraday/attitude uncertainty.
- Appropriate precise EOP data, current ionosphere indices, validation of
  GloTEC assimilation beyond the local NmF2 correction and real orbit/position
  for the same epoch as spacecraft attitude.
- No autonomous third-axis motor movement is authorized by this work.

## Added: real transmitting satellite, common-epoch orbit and independent receiver

See `docs/real-transmitter-validation.md`, including physical assumptions,
historical precise-orbit provenance, same-UTC quaternion join, real S-band RHCP
antenna data, synthetic counterfactual linear-emitter antenna, and 256-trial
independent complex-IQ receiver Monte Carlo. A simulated receiver does **not**
provide real-world downlink POL measurement accuracy; no real RF POL data
have been checked and no actual transmission is established for the epoch.

- CI: https://github.com/Iu-yang1/SatPolEngine/actions/runs/40165856890
- CASSIOPE GPS-time SP3 sample: 2017-06-24T22:32:32 GPS.
- CASSIOPE AttQuat CDF sample: 2017-06-24T22:32:14 UTC.
- RHCP downlink 2231.5MHz: no unique linear-roll target, ideal loss 3.0103dB.
- Synthetic +X ideal linearly polarized transmitter angle: 22.746802°.
- Independent noisy receiver p95 absolute angular error: 0.596422°.
- Hypothetical transmitter/attitude/propagation error prior p95: 9.628628°.
