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
        if not -1<=alt_km<=2000:
            raise ValueError(f"IRI-2026 ray sample outside supported model domain: {alt_km}")
        if alt_km<80:
            # Explicitly approximate ionospheric Ne=0 below 80km.
            records.append(None)
        else:
            records.append(f"{now.year} {now.month} {now.day} {hours:.8f} "
                           f"{lat:.10f} {lon:.10f} {alt_km:.8f}")
    # Official IRI obtains daily solar indices by reading files relative to cwd.
    exe=pathlib.Path(binary).resolve()
    requested=[row for row in records if row is not None]
    completed=subprocess.run([str(exe)],input="\n".join(requested)+"\n",text=True,
        capture_output=True,cwd=exe.parent,check=True,timeout=180)
    values=[]
    model_f2=[]
    for line in completed.stdout.splitlines():
        line=line.strip()
        if "Error while reading" in line or line.startswith("ERROR:"):
            raise RuntimeError("Official IRI reported invalid model coefficients: "+line)
        if not line.startswith("SATPOL_NE="): continue
        try:
            props=dict(token.split("=",1) for token in line.split(";"))
            value=float(props["SATPOL_NE"].replace("D","E"))
            nmf2=float(props["NMF2"].replace("D","E"))
            hmf2=float(props["HMF2"].replace("D","E"))
            f107=float(props["F107"].replace("D","E"))
        except (ValueError,KeyError):
            raise RuntimeError("Unexpected official IRI density/F2/F107 fields: "+line[:200])
        if not all(math.isfinite(z) for z in (value,nmf2,hmf2,f107)):
            raise RuntimeError("Non-finite IRI 2026 numerical parameter")
        if not (0<=value<1e14 and 1e9<nmf2<1e14 and 120<hmf2<750 and 40<f107<400):
            raise RuntimeError(f"Out-of-range official IRI model result: {line[:200]}")
        values.append(value)
        model_f2.append((nmf2,hmf2,f107))
    if len(values)!=len(requested):
        raise RuntimeError(f"IRI emitted {len(values)}, expected {len(requested)}; stderr={completed.stderr[-600:]}")
    numbered=iter(values)
    complete_values=[0.0 if row is None else next(numbered) for row in records]
    params_iter=iter(model_f2)
    all_params=[None if row is None else next(params_iter) for row in records]
    f107_samples=[p[2] for p in model_f2]
    if max(f107_samples)-min(f107_samples)>2.0:
        raise RuntimeError("IRI F10.7 changed unexpectedly within same UTC day")
    f107_d=f107_samples[0]
    with pathlib.Path(output).open("w",encoding="utf-8") as f:
        f.write(f"# source=IRI-2026-official-fortran;F107={f107_d:.5f};time={fmt}\n")
        f.write("x_m\ty_m\tz_m\tNe_m-3\n")
        for point,value in zip(points,complete_values):
            f.write("\t".join([*(f"{v:.9f}" for v in point),f"{value:.10e}"])+"\n")
    # Traceable model peak state, needed for assimilating real F2 observations.
    metadata={
        "source":"official IRI-2026 Fortran, urSI F2 peak, original daily indices",
        "time_utc":fmt,
        "f107_sfu":f107_d,
        "samples":[None if p is None else
            {"nmf2_m3":p[0],"hmf2_km":p[1],"f107_sfu":p[2]}
            for p in all_params]
    }
    pathlib.Path(output).with_suffix(".f2.json").write_text(
        json.dumps(metadata,indent=2),encoding="utf-8")
    return min(values),max(values)

if __name__=="__main__":
    if len(sys.argv)!=4:
        sys.exit("usage: export_official_path.py request.json IRI_BINARY output.tsv")
    lo,hi=export(json.loads(pathlib.Path(sys.argv[1]).read_text()),
        sys.argv[2],sys.argv[3])
    print(f"Official IRI-2026 samples: density_min={lo:.6e}, density_max={hi:.6e}")
