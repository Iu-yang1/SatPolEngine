"""Stdlib-only, independent SI-unit reference calculations for scientific regression."""
from __future__ import annotations
import math

FARADAY_K = 2.3646e4

def faraday_uniform(
    frequency_hz: float, path_m: float, density_m3: float, parallel_field_t: float
) -> float:
    """Radians, with positive B_parallel defined along spacecraft -> receiver."""
    if not all(map(math.isfinite, (frequency_hz, path_m, density_m3, parallel_field_t))):
        raise ValueError("nonfinite input")
    if frequency_hz <= 0 or path_m < 0 or density_m3 < 0:
        raise ValueError("invalid physical input")
    return FARADAY_K * density_m3 * parallel_field_t * path_m / frequency_hz**2

def axis_deg(e0: complex, e1: complex) -> float | None:
    s0 = abs(e0)**2 + abs(e1)**2
    s1 = abs(e0)**2 - abs(e1)**2
    s2 = 2.0 * (e0 * e1.conjugate()).real
    if s0 <= 1e-24 or math.hypot(s1, s2) / s0 < 1e-10:
        return None
    a = math.degrees(0.5*math.atan2(s2,s1))
    return (a+90)%180-90

def roll_delta_deg(target: float, current: float) -> float:
    return (target-current+90)%180-90
