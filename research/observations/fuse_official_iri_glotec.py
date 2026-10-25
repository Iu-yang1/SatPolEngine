#!/usr/bin/env python3
"""Assimilate REAL NOAA GloTEC F2 observations into OFFICIAL IRI-2026 Fortran
electron-density profiles along an explicitly finite LEO-to-ground ray.

This is a limited altitude-localized NmF2 correction constrained by actual
observations; not a full 3D VTEC tomography inversion or measured satellite POL.
"""
from __future__ import annotations
import datetime as dt
import json
import math
from pathlib import Path
import sys
import numpy as np
import xarray as xr

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from iri_export_path import ecef_to_geodetic
from fuse_iri_glotec import require_model_snapshot,interpolate_field,correction_ratio

def _parse_iri_path(path:Path):
    lines=path.read_text(encoding="utf-8").splitlines()
    if len(lines)<3 or not lines[0].startswith("# source=IRI-2026-official-fortran;"):
        raise ValueError("Input is not explicitly the official IRI-2026 ray output")
    if lines[1]!="x_m\ty_m\tz_m\tNe_m-3":
        raise ValueError("Bad IRI sample fields")
    header=lines[0].removeprefix("# source=").split(";")
    if len(header)!=3 or not header[1].startswith("F107=") or not header[2].startswith("time="):
        raise ValueError("Invalid official IRI data provenance")
    f107=float(header[1].split("=",1)[1])
    time=dt.datetime.fromisoformat(header[2].split("=",1)[1].replace("Z","+00:00"))
    if not (40<f107<400 and time.utcoffset()==dt.timedelta(0)):
        raise ValueError("Bad solar index or UTC epoch")
    points=np.asarray([[float(v) for v in s.split("\t")] for s in lines[2:]],dtype=float)
    if points.ndim!=2 or points.shape[1]!=4 or not np.isfinite(points).all() or (points[:,3]<0).any():
        raise ValueError("Invalid IRI ray field")
    return time,f107,points

def run(official_path:Path,metadata_path:Path,glotec_file:Path,output:Path,min_quality=2):
    epoch,f107,points=_parse_iri_path(official_path)
    f2=json.loads(metadata_path.read_text(encoding="utf-8"))
    if f2["source"] != "official IRI-2026 Fortran, urSI F2 peak, original daily indices":
        raise ValueError("Unrecognized official model F2 provenance")
    meta_time=dt.datetime.fromisoformat(f2["time_utc"].replace("Z","+00:00"))
    if meta_time!=epoch or abs(f2["f107_sfu"]-f107)>0.01:
        raise ValueError("Official model time/F10.7 metadata mismatch")
    if len(f2["samples"])!=len(points):
        raise ValueError("F2 sample size mismatch")
    location=np.asarray([ecef_to_geodetic(*row[:3]) for row in points])
    with xr.open_dataset(glotec_file,decode_cf=True) as ds:
        snapshot,observed_at,gap=require_model_snapshot(ds,epoch,max_minutes=20.0)
        obs=[]
        for lat,lon,_ in location:
            obs.append((
                interpolate_field(snapshot,"NmF2",lat,lon),
                interpolate_field(snapshot,"hmF2",lat,lon),
                interpolate_field(snapshot,"TEC",lat,lon),
                int(round(interpolate_field(snapshot,"quality_flag",lat,lon)))
            ))
    out=[];quality=[]
    for idx,(row,m,o,geo) in enumerate(zip(points,f2["samples"],obs,location)):
        density=float(row[3])
        altkm=float(geo[2])/1000.
        ne_obs,hm_obs,tecu,q=o
        if q not in range(6) or q<min_quality:
            raise ValueError(f"Missing or insufficient GloTEC quality at sample {idx}: {q}")
        if not (math.isfinite(tecu) and 0<tecu<300):
            raise ValueError("Invalid measured-assimilated GloTEC VTEC")
        quality.append(q)
        if altkm<80.0:
            if abs(density)>1e-8:
                raise ValueError("Unexpected low-altitude nonzero IRI density")
            out.append(0.0)
            continue
        if m is None:
            raise ValueError("Official IRI peak model absent at physical sample")
        out.append(correction_ratio(density,float(m["nmf2_m3"]),float(m["hmf2_km"]),
                                    float(ne_obs),float(hm_obs),altkm))
    if not all(math.isfinite(x) and x>=0 for x in out):
        raise ValueError("Invalid corrected IRI electron density")
    epoch_z=epoch.strftime("%Y-%m-%dT%H:%M:%SZ")
    source=f"IRI-2026-official+NOAA-GloTEC-F2-observation-{observed_at}"
    with output.open("w",encoding="utf-8") as fp:
        fp.write(f"# source={source};F107={f107:.5f};time={epoch_z}\n")
        fp.write("x_m\ty_m\tz_m\tNe_m-3\n")
        for sample,ne in zip(points,out):
            fp.write("\t".join([*(format(float(v),".9f") for v in sample[:3]),format(float(ne),".9e")])+"\n")
    report={
        "model":"Official IRI-2026 Fortran NmF2/hmF2 + NOAA observed-assimilated GloTEC F2 correction",
        "source_iri":f2["source"],
        "source_glotec":"NOAA GloTEC 2-D historical NetCDF",
        "requested_utc":epoch_z,
        "observed_utc":observed_at,
        "observation_age_minutes":gap,
        "samples":len(points),
        "minimum_observation_quality":min(quality),
        "official_iri_f107_sfu":f107,
        "density_before_tec_units":"electron density m^-3",
        "density_corrected_max_m3":max(out),
        "full_column_TEC_used_as_slant":False,
        "real_satellite_attitude_used":False,
        "polarization_measured":False,
        "correction":"Gaussian altitude-localized NmF2 scaling, NOT full TEC inversion",
    }
    return report

if __name__=="__main__":
    if len(sys.argv)!=5:
        sys.exit("usage: fuse_official_iri_glotec.py official.tsv official.f2.json archive.nc output.tsv")
    report=run(Path(sys.argv[1]),Path(sys.argv[2]),Path(sys.argv[3]),Path(sys.argv[4]))
    print(json.dumps(report,indent=2))
