"""IGRF-14 comparison with independent IAGA-VMOD/ppigrf evaluator."""
import datetime as dt
import math
import os
import re
import subprocess
import numpy as np
import ppigrf

CASES=[
    (0.0,0.0,0.0,2025),
    (45.0,120.0,400.0,2026),
    (60.0,-105.0,0.0,2029),
    (-30.0,72.0,800.0,2020),
]

for lat,lon,hkm,year in CASES:
    be,bn,bu=ppigrf.igrf(lon,lat,hkm,dt.datetime(year,1,1))
    ref=np.array([float(np.asarray(be).item()),float(np.asarray(bn).item()),float(np.asarray(bu).item())])
    env=os.environ.copy()
    env.update(SATPOL_LAT_DEG=str(lat),SATPOL_LON_DEG=str(lon),
        SATPOL_ALT_KM=str(hkm),SATPOL_YEAR=str(year))
    got=subprocess.run(["gradle","-q","--no-daemon","igrfVector"],env=env,
        check=True,capture_output=True,text=True)
    match=re.findall(r"SATPOL_FIELD=([^\n]+)",got.stdout)
    if len(match)!=1:raise RuntimeError("Missing Kotlin IGRF field: "+got.stdout)
    impl=np.array([float(x) for x in match[0].split(",")])
    err=np.linalg.norm(impl-ref)
    print(f"lat={lat},lon={lon},alt={hkm}km,year={year} "
        f"error={err:.9f} nT; E/N/U expected={ref}, actual={impl}")
    assert math.isfinite(err)
    assert err<5.0, f"IGRF-14 field mismatch beyond 5nT: {err}"
