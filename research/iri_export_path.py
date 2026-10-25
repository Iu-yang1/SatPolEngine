#!/usr/bin/env python3
"""Export REAL PyIRI daily-profile results along a finite LEO path.

PyIRI is an independently maintained MIT-licensed Python implementation of
the IRI family, NOT the original official Fortran IRI-2026. The output is
a climatological model estimate with an EXPLICIT measured/provided F10.7,
never measured Ne or real-time assimilation.

Requires: pip install PyIRI numpy
Usage:
 python research/iri_export_path.py request.json samples.tsv

JSON input:
{
  "satellite_ecef_m": [6878137, 10000, 0],
  "ground_ecef_m": [6378137, 0, 0],
  "time_utc": "2026-10-25T12:00:00Z",
  "f107_sfu": 120.0,
  "samples": 128
}
"""
from __future__ import annotations
import datetime as dt
import json
import math
import pathlib
import sys

A = 6378137.0
E2 = 6.6943799901413165e-3

def ecef_to_geodetic(x:float,y:float,z:float):
    """WGS-84 geodetic conversion, robust for equator and poles."""
    lon=math.atan2(y,x)
    p=math.hypot(x,y)
    if p < 1e-6:
        lat=math.copysign(math.pi/2,z)
        return math.degrees(lat), math.degrees(lon), abs(z)-A*math.sqrt(1-E2)
    lat=math.atan2(z,p*(1-E2))
    h=0.0
    for _ in range(15):
        s=math.sin(lat)
        n=A/math.sqrt(1-E2*s*s)
        h=p/math.cos(lat)-n
        new=math.atan2(z,p*(1-E2*n/(n+h)))
        if abs(new-lat)<1e-13: break
        lat=new
    return math.degrees(lat),math.degrees(lon),h

def export(data:dict, output:pathlib.Path):
    import numpy as np
    import PyIRI
    import PyIRI.main_library as iri
    sat=np.asarray(data["satellite_ecef_m"],dtype=float)
    ground=np.asarray(data["ground_ecef_m"],dtype=float)
    if sat.shape!=(3,) or ground.shape!=(3,) or not np.isfinite(sat).all() or not np.isfinite(ground).all():
        raise ValueError("Invalid ECEF ray")
    date=dt.datetime.fromisoformat(data["time_utc"].replace("Z","+00:00"))
    if date.utcoffset()!=dt.timedelta(0):
        raise ValueError("time_utc must be explicitly UTC")
    time_str=date.strftime("%Y-%m-%dT%H:%M:%S") + (".%06d" % date.microsecond if date.microsecond else "")+"Z"
    n=int(data["samples"])
    if not 1<=n<=100000: raise ValueError("samples outside 1..100000")
    f107=float(data["f107_sfu"])
    if not math.isfinite(f107) or not 45<=f107<=400:
        raise ValueError("F10.7 outside supported validation range 45..400 sfu")
    mid=np.asarray([sat+(ground-sat)*((i+.5)/n) for i in range(n)])
    geo=np.asarray([ecef_to_geodetic(*p) for p in mid])
    lat=geo[:,0];lon=geo[:,1];alt_km=geo[:,2]/1000
    if max(alt_km)>2000:
        raise ValueError("This initial IRI export supports LEO rays with maximum height <=2000 km")
    if min(alt_km)<-1:
        raise ValueError("Ray intersects ground beyond endpoint")
    # PyIRI supports a vertical grid; don't extrapolate into unmodelled topside.
    # Model output below 80km is intentionally treated as 0 density, an explicit
    # lower-ionosphere approximation (important mainly for lower atmosphere).
    aalt=np.arange(80.0, max(2001.0,math.ceil(max(alt_km)/5)*5+5),5.0)
    hour=date.hour+date.minute/60+(date.second+date.microsecond/1e6)/3600
    _,_,_,_,_,_,edp=iri.IRI_density_1day(
        date.year,date.month,date.day,np.asarray([hour]),
        lon,lat,aalt,f107,PyIRI.coeff_dir,1)
    edp=np.asarray(edp)
    if edp.shape != (1,len(aalt),n):
        raise ValueError(f"Unexpected PyIRI electron-density shape: {edp.shape}")
    density=np.array([np.interp(alt_km[i],aalt,edp[0,:,i],left=0.0)
                      for i in range(n)])
    if not np.isfinite(density).all() or (density < 0).any():
        raise ValueError("Invalid physical electron density from PyIRI")
    version=getattr(PyIRI,"__version__","unknown")
    with output.open("w",encoding="utf-8") as f:
        f.write(f"# source=PyIRI-{version}-URSI-CCIR-climatology;F107={f107};time={time_str}\n")
        f.write("x_m\ty_m\tz_m\tNe_m-3\n")
        for row,ne in zip(mid,density):
            f.write("\t".join([*(format(float(v),".10g") for v in row),format(float(ne),".10g")])+"\n")

def main():
    if len(sys.argv)!=3:
        raise SystemExit("Usage: iri_export_path.py request.json samples.tsv")
    data=json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
    export(data,pathlib.Path(sys.argv[2]))

if __name__=="__main__":
    main()
