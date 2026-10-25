# Real spacecraft S-band polarization: same-epoch test and independent receiver

**Status: numerical and archived-data validation completed; actual received
RF polarization measurements NOT obtained.**

## 1. Real transmitting satellite / antenna specification

Source: Canadian Space Agency CASSIOPE / Swarm-E Final Operations Report §2.1.3
https://epop.phys.ucalgary.ca/wp-content/Swarm-E_Final_Operations_Report.pdf

The mission specifies a **2231.5 MHz right-hand circular polarization (RHCP)**
S-band downlink. S-band transmit/receive patch antennas are mounted on the
spacecraft body top **-Z** and bottom **+Z** panels. This is a documented real
radio-transmitting spacecraft, not a virtual dipole. No dated active-patch
selector or measured antenna axial-ratio/angular pattern was found for our
2017 sample, so selection of the panel facing the hypothetical ground station
is a GEOMETRIC PROXY, not proof that panel was transmitting.

The RRI experiment's two orthogonal VLF/HF dipoles are **receiving sensors**,
NOT the S-band transmitter: never identify the RRI dipole axes as the actual
transmit polarization vectors.

## 2. Real orbit and attitude at precisely identical UTC

Study instant: **2017-06-24T22:32:14Z**.

| Input | Official archive | Time system / frame |
| --- | --- | --- |
| Position and velocity | https://epop-data.phys.ucalgary.ca/2017/06/24/CAS_Orbit_GEO_20170624T000000_20170624T235959_1.1.0.sp3.zip | GPS 22:32:32; ITRF position and rotating-frame velocity |
| Body quaternion | https://epop-data.phys.ucalgary.ca/2017/06/24/CAS_AttQuat_20170624T000000_20170624T235959_2.0.0.cdf.zip | UTC 22:32:14; active ITRF <- body, scalar last |

The SP3 GPS-to-UTC offset is **18 s on that specific historical date**.
The parser refuses approximate/incorrect epoch matches or implausible SP3
position/velocity; Kotlin rejects asynchronous orbit and attitude inputs.

Source guidance:
- https://epop.phys.ucalgary.ca/data-handbook/orbit-geo-sp3-file/
- https://epop.phys.ucalgary.ca/data-handbook/attitude-quaternion-file-cas_attquat/

The actual position radius from Earth's center was **7383.02714 km**.
For an explicitly **synthetic station placed near the subsatellite point**,
the selected +Z patch panel boresight has geometric offset **38.48435°**.
This does NOT assert an actual 2017 transmission or measured ground QSO.

## 3. Predicted optimum for a real RHCP downlink

Ideal RHCP Jones vector in an orthogonal receiver wavefront basis, up to
handedness convention:

```
E = (1, ±i) / sqrt(2)
v_pol(alpha) = E0 cos(alpha) + E1 sin(alpha)
P_pol(alpha) / P_total = |v_pol|² = 1/2
L_pol = -10 log10(1/2) = +3.0102999566 dB
```

This holds at **all linear receive roll angles**. The receiving linear
polarizer has NO unique optimum, irrespective of ideal Faraday rotation.
The correct engine result is `angle=UNKNOWN, CWCCW=UNKNOWN`, not a guessed
23° target. Real antenna axial ratio, depolarization, multipath and
receiver cross-polar leakage can introduce residual roll dependence. They
have NOT been calibrated with measured downlink waveforms.

## 4. Independent receiver experiment for a *nonexistent* linear transmitter

To check the linear-polarization recommendation mathematics, a clearly
hypothetical **ideal body-X linear dipole** is projected from the real
2017-06-24 body-to-ITRF quaternion and real SP3 position. It is NOT the
documented CASSIOPE S-band transmitter. The propagation is ideal vacuum
(no physically claimed zero-ionosphere reality at that epoch).

Kotlin geometric target: **+22.746802406209°**.
An independently written NumPy coordinate reconstruction obtains
**+22.746802406206°**, difference about **2.83e-12°**.

Independent receiver simulator:
- complex two-component electric field from projected virtual dipole
- virtual physical polarization analyzer swept at 5° intervals over 0..175°
- 64 fresh complex I/Q samples at each analyzer position
- independent circular complex AWGN, receiver SNR 15 dB
- 256 deterministic-seed Monte Carlo replications
- estimates peak using a power-only least-squares fit:
  `P(alpha) = DC + C cos(2alpha) + S sin(2alpha)`
- measured optimum `0.5 atan2(S,C)`, wrapped modulo 180°;
  a modulation depth under 8% means angle UNKNOWN

Results:
- Synthetic linear scene, receiver p95 absolute error **0.596422°**.
- Ideal circular scene, spurious optimum rate **0/256 (0%)**.
- Hypothetical additional independent Gaussian uncertainties
  antenna mounting σ=2.5°, attitude σ=0.06°, propagation σ=4° produce a
  simulated p95 absolute target-vs-measured error **9.628628°**.
  These uncertainty priors are intentionally illustrative, **not estimated
  from recorded RF waveforms** or formal 95% confidence intervals.

End-to-end logs and the downloaded real-orbit/CDF-converted AEM and independent
simulation artifacts:
https://github.com/Iu-yang1/SatPolEngine/actions/runs/40165856890

## 5. What can and cannot be concluded

**Supported:** true same-epoch orbit/attitude integration works for this
historical spacecraft; actual downlink polarization class (RHCP) was sourced
from operator documentation; a physically appropriate no-unique-angle outcome
is obtained; a separate simulated receiver recovers a hypothetical linear
wavefront angle to within a measured simulation error distribution.

**Not supported:** accurate prediction of a real 145/435MHz amateur satellite's
optimal linear feed angle, real CASSIOPE downlink reception at the historical
epoch, calibration of physical Faraday sign or actual antenna axial ratio,
measurement of antenna mismatch in an operational link, verification of
SO-50/FO-29/RS-44 attitude at specific time, or proof that hardware clockwise
direction matches the mathematical coordinate convention.

Before claiming operational accuracy, pair the satellite's actual time-tagged
TRANSMIT Jones/field pattern and attitude with simultaneous two-channel
orthogonal-polarization complex RF reception from an independently
calibrated ground station, allowing QSB/ionospheric scintillation and
mechanical zero/sign uncertainties to enter the error budget.
