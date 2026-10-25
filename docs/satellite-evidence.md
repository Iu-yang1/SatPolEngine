# Satellite evidence ledger (2026-10-25)

This is a literature survey, not live spacecraft attitude telemetry.
The same TLE does not determine spacecraft polarization orientation.

| Satellite | Verified public evidence | Missing requirement | Operational POL angle |
| --- | --- | --- | --- |
| FO-29 / JAS-2 | Spin-stabilized ACS with sun and geomagnetic sensors; historical AMSAT material describes RHCP uplink/downlink. | Present spin phase and attitude unknown; ideal circular polarization has no unique linear-receiver roll optimum. | UNKNOWN |
| SO-50 | AMSAT describes a quarter-wave UHF downlink radiator mounted at a lower corner and tilted inward by 45 degrees. | Body-to-world orientation over time and full vector antenna pattern. | UNKNOWN |
| RS-44 | SatNOGS identifies NORAD 44909 and UHF transmissions. | Reliable downlink antenna polarization model and time-dependent attitude. | UNKNOWN |
| ISS / ARISS | ARISS advises receiving operators to vary polarization and states that no simple analysis predicts its changes during the pass. | Configuration-specific antenna polarization and calibrated empirical measurements. | UNKNOWN |

## Sources

- JAS-2 attitude controller, IFAC (1996): https://doi.org/10.1016/S1474-6670(17)58874-6
- AMSAT FO-29: https://www.amsat.org/two-way-satellites/fo-29-jas-2/
- AMSAT-BB FO-29 polarization archive: https://mail.amsat.org/archives/pipermail/amsat-bb/2012-September/042603.html
- AMSAT SO-50: https://www.amsat.org/two-way-satellites/so-50-satellite-information/
- SatNOGS RS-44: https://db.satnogs.org/satellite/44909/
- ARISS polarization tips: https://www.ariss.org/tips-and-techniques.html

Before accepting a satellite profile, identify downlink polarization, antenna
body coordinates, time-resolved attitude, reference-frame conventions, dated
source provenance, receiver roll zero/sign and quantitative observational
validation. An ideal circular downlink is not meaningfully optimized by
rotating a linear Yagi. Computable Faraday angle alone is not a complete
optimal polarization angle.

## CASSIOPE / Swarm-E S-band telemetry downlink (verified mission facts)

Actual mission Final Operations Report, section 2.1.3:
https://epop.phys.ucalgary.ca/wp-content/Swarm-E_Final_Operations_Report.pdf

- S-band downlink: **2231.5 MHz**, **RHCP**.
- S-band patches for transmit/receive: body top **-Z** and bottom **+Z**
  faces (panel normals). The report describes broad attitude coverage and
  a narrow near-body-equatorial blind zone; it does not supply a measured
  per-frequency vector antenna pattern or the active patch selection at
  our chosen historical UTC.
- **Actual one-second attitude**: Calgary
  `CAS_AttQuat_20170624T000000_20170624T235959_2.0.0.cdf.zip`;
  scalar-last body-to-ITRF, quality source 4 at study epoch.
- **Actual one-second definitive Earth-fixed orbit**:
  `CAS_Orbit_GEO_20170624T000000_20170624T235959_1.1.0.sp3.zip`;
  its 2017 GPS time must be offset **18 seconds** from UTC.
- The real transmitter is circular: rotating a linearly polarized
  receiver yields no ideal unique polarization-mismatch optimum.
  Neither real RF reception nor active transmitter status was measured
  at the chosen UTC. This is a valid failure-to-recommend test, not
  experimental proof of a real 145/435MHz target angle.
