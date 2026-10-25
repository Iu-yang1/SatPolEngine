#!/usr/bin/env python3
"""Generate midpoint Ne along an ECEF LEO ray with official IRI-2026 Fortran.
Input JSON schema matches ../iri_export_path.py.
Requires the official IRI-2026 Fortran executable built by build_official.sh.
"""
import datetime as dt
import json
import math
import pathlib
import subprocess
import sys

sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
from iri_export_path import ecef_to_geodetic

def export(request, binary, output):
    now=dt.datetime.fromisoformat(request["time_utc"].replace("Z","+00:00"))
    if now.utcoffset()!=dt.timedelta(0):
        raise ValueError("UTC timestamp required")
    fmt=now.strftime("%Y-%m-%dT%H:%M:%S")+"Z"
    sat=list(map(float,request["satellite_ecef_m"]))
    ground=list(map(float,request["ground_ecef_m"]))
    if len(sat)!=3 or len(ground)!=3 or not all(map(math.isfinite,sat+ground)):
        raise ValueError("Invalid ray endpoints")
    count=int(request["samples"])
    if count<1 or count>4096:
        raise ValueError("Invalid sample count")
    points=[tuple(s+(g-s)*(i+0.5)/count for s,g in zip(sat,ground))
            for i in range(count)]
    geos=[ecef_to_geodetic(*p) for p in points]
    hours=now.hour+now.minute/60+(now.second+now.microsecond/1e6)/3600
    records=[]
    for lat,lon,alt_m in geos:
        alt_km=alt_m/1000
        if not 80<=alt_km<=2000:
            raise ValueError(f"IRI-2026 driver supported height 80..2000km, got {alt_km}")
        records.append(f"{now.year} {now.month} {now.day} {hours:.8f} "
                       f"{lat:.10f} {lon:.10f} {alt_km:.8f}")
    # Official IRI obtains daily solar indices by reading files relative to cwd.
    exe=pathlib.Path(binary).resolve()
    completed=subprocess.run([str(exe)],input="\n".join(records)+"\n",text=True,
        capture_output=True,cwd=exe.parent,check=True,timeout=180)
    values=[]
    for line in completed.stdout.splitlines():
        line=line.strip()
        if not line or line.startswith(("WARNING","NOTE")): continue
        if line.startswith("ERROR:"):
            raise RuntimeError("IRI model: "+line)
        try: value=float(line.replace("D","E"))
        except ValueError:
            raise RuntimeError("Unexpected official IRI output: "+line[:200])
        if not math.isfinite(value) or value<0 or value>=1e14:
            raise RuntimeError("Invalid IRI density")
        values.append(value)
    if len(values)!=count:
        raise RuntimeError(f"IRI emitted {len(values)}, expected {count}; stderr={completed.stderr[-600:]}")
    with pathlib.Path(output).open("w",encoding="utf-8") as f:
        f.write(f"# source=IRI-2026-official-fortran;F107=-1;time={fmt}\n")
        f.write("x_m\ty_m\tz_m\tNe_m-3\n")
        for point,value in zip(points,values):
            f.write("\t".join([*(f"{v:.9f}" for v in point),f"{value:.10e}"])+"\n")
    return min(values),max(values)

if __name__=="__main__":
    if len(sys.argv)!=4:
        sys.exit("usage: export_official_path.py request.json IRI_BINARY output.tsv")
    lo,hi=export(json.loads(pathlib.Path(sys.argv[1]).read_text()),
        sys.argv[2],sys.argv[3])
    print(f"Official IRI-2026 samples: density_min={lo:.6e}, density_max={hi:.6e}")
