#!/usr/bin/env python3
"""IRI-family 3D-profile background corrected by archived NOAA GloTEC F2-layer
observations. This is an *experimental shape-preserving F2 update*, NOT a full
TEC tomography inversion. The 2-D TEC field is used only as an independent
diagnostic, never confused with partial ground-to-LEO sTEC.

Requires PyIRI numpy xarray netcdf4. Geographic grid lat/lon interpolated,
GloTEC observation time and quality verified. No stale-data fallback.
"""
import datetime as dt
import json
import math
from pathlib import Path
import sys

import numpy as np
import xarray as xr
import PyIRI
import PyIRI.main_library as iri

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from iri_export_path import ecef_to_geodetic

def longitude_wrap(x):
    return (float(x)+180)%360-180

def require_model_snapshot(ds, instant, max_minutes=20.):
    for item in ("TEC","NmF2","hmF2","quality_flag"):
        if item not in ds:
            raise ValueError("Missing GloTEC field: "+item)
        if ds[item].dims!=("time","latitude","longitude"):
            raise ValueError("Unexpected GloTEC axes")
    if "10^16" not in ds["TEC"].attrs.get("units",""):
        raise ValueError("GloTEC TECU unit unknown")
    epoch=np.datetime64(instant.replace(tzinfo=None))
    values=np.asarray(ds.time.values,dtype="datetime64[ns]")
    if not values.size or np.isnat(values).any():
        raise ValueError("No valid UTC observation time")
    idx=int(np.argmin(np.abs(values-epoch)))
    gap=abs((values[idx]-epoch)/np.timedelta64(1,"m"))
    if gap>max_minutes: raise ValueError(f"GloTEC stale by {gap:.1f} minutes")
    return ds.isel(time=idx),str(values[idx])+"Z",float(gap)

def interpolate_field(ds, name, lat,lon):
    field=ds[name].sortby("latitude").sortby("longitude")
    # Longitude grids are periodic; explicit extension avoids the date-line seam.
    grid=np.asarray(field.longitude.values,dtype=float)
    if len(grid)<3 or any(np.diff(grid)<=0):
        raise ValueError("Invalid longitude grid")
    left=field.isel(longitude=-1).assign_coords(longitude=grid[-1]-360)
    right=field.isel(longitude=0).assign_coords(longitude=grid[0]+360)
    extended=xr.concat([left,field,right],dim="longitude")
    longitude=longitude_wrap(lon)
    while longitude<float(extended.longitude.min()):longitude+=360
    while longitude>float(extended.longitude.max()):longitude-=360
    if not float(extended.latitude.min())<=lat<=float(extended.latitude.max()):
        raise ValueError("GloTEC latitude outside supported grid")
    return float(extended.interp(latitude=float(lat),longitude=longitude).values)

def correction_ratio(density_bg, nm_bg, hm_bg, nm_obs, hm_obs, altitude_km):
    """Positive smooth update around the F2 peak, with guarded model correction."""
    if not all(math.isfinite(x) for x in [density_bg,nm_bg,hm_bg,nm_obs,hm_obs,altitude_km]):
        raise ValueError("Nonfinite F2 observation/model")
    if not (density_bg>=0 and 1e9<nm_bg<1e14 and 1e9<nm_obs<1e14):
        raise ValueError("Unphysical F2 peak density")
    if not (120<hm_bg<750 and 120<hm_obs<750):
        raise ValueError("Unphysical F2 peak height")
    ratio=nm_obs/nm_bg
    if not 0.25<=ratio<=4 or abs(hm_obs-hm_bg)>120:
        raise ValueError("GloTEC and background are incompatible; refuse assimilation")
    # A local height / peak-density adjustment, NOT total-column TEC scaling.
    weight=math.exp(-0.5*((altitude_km-hm_obs)/150.0)**2)
    return density_bg*math.exp(weight*math.log(ratio))

def fuse(request, glotec_file:Path, output:Path, min_quality=2):
    now=dt.datetime.fromisoformat(request["time_utc"].replace("Z","+00:00"))
    if now.tzinfo is None or now.utcoffset()!=dt.timedelta(0):
        raise ValueError("Explicit UTC time required")
    sat=np.asarray(request["satellite_ecef_m"],dtype=float)
    ground=np.asarray(request["ground_ecef_m"],dtype=float)
    if sat.shape!=(3,) or ground.shape!=(3,) or not np.isfinite(sat).all() or not np.isfinite(ground).all():
        raise ValueError("Invalid ray coordinates")
    count=int(request["samples"])
    if not 1<=count<=4096:raise ValueError("Samples outside 1..4096")
    f107=float(request["f107_sfu"])
    if not math.isfinite(f107) or not 45<=f107<=400:raise ValueError("Invalid dated F10.7")
    points=np.asarray([sat+(ground-sat)*((i+0.5)/count) for i in range(count)])
    geos=np.asarray([ecef_to_geodetic(*p) for p in points])
    lat,lon,alt=geos[:,0],geos[:,1],geos[:,2]/1000.
    if not np.all((alt>=80)&(alt<=2000)):
        raise ValueError("Ray points outside supported PyIRI altitudes")
    heights=np.arange(80.,2001.,5.)
    hour=now.hour+now.minute/60+(now.second+now.microsecond/1e6)/3600
    f2,_,_,_,_,_,edp=iri.IRI_density_1day(now.year,now.month,now.day,
        np.asarray([hour]),lon,lat,heights,f107,PyIRI.coeff_dir,1)
    edp=np.asarray(edp)
    if edp.shape!=(1,len(heights),count):
        raise ValueError(f"Unexpected PyIRI shape: {edp.shape}")
    background=np.asarray([np.interp(alt[k],heights,edp[0,:,k],left=0.0) for k in range(count)])
    bg_nm=np.asarray(f2["Nm"])[0,:]
    bg_hm=np.asarray(f2["hm"])[0,:]
    with xr.open_dataset(glotec_file,decode_cf=True) as ds:
        frame,observed_epoch,gap=require_model_snapshot(ds,now)
        nm=[];hm=[];qualities=[];vtec=[]
        for la,lo in zip(lat,lon):
            nm.append(interpolate_field(frame,"NmF2",la,lo))
            hm.append(interpolate_field(frame,"hmF2",la,lo))
            # Quality is categorical; use nearest center rather than interpolate levels.
            q=int(round(interpolate_field(frame,"quality_flag",la,lo)))
            qualities.append(q)
            vtec.append(interpolate_field(frame,"TEC",la,lo))
    if min(qualities)<min_quality:
        raise ValueError(f"Insufficient GloTEC observational coverage: {min(qualities)}<{min_quality}")
    vals=[]
    for k in range(count):
        if not (math.isfinite(vtec[k]) and 0<vtec[k]<300):
            raise ValueError("Invalid GloTEC TEC observation")
        vals.append(0.0 if alt[k]<80.0 else correction_ratio(background[k],bg_nm[k],bg_hm[k],nm[k],hm[k],alt[k]))
    if not np.isfinite(vals).all():raise ValueError("Nonfinite fused densities")
    ver=getattr(PyIRI,"__version__","unknown")
    epoch=now.strftime("%Y-%m-%dT%H:%M:%SZ")
    # Keep header compatible with IriPathSamples.parseTsv; use source token, F107, time.
    with output.open("w",encoding="utf-8") as f:
        f.write(f"# source=PyIRI-{ver}+NOAA-GloTEC-F2-assimilated-{observed_epoch};F107={f107};time={epoch}\n")
        f.write("x_m\ty_m\tz_m\tNe_m-3\n")
        for point,ne in zip(points,vals):
            f.write("\t".join([*(f"{v:.9f}" for v in point),f"{ne:.10e}"])+"\n")
    return {"time_utc":epoch,"glotec_observation_utc":observed_epoch,
        "observation_age_minutes":gap,"samples":count,"quality_min":min(qualities),
        "model":"PyIRI background + NOAA GloTEC F2 peak density correction; explicit zero density below 80km",
        "observed_tec_tecu_min":min(vtec),"observed_tec_tecu_max":max(vtec),
        "note":"TEC shown for diagnosis only; not inverted into LEO slant path"}

if __name__=="__main__":
    if len(sys.argv)!=4:sys.exit("usage: fuse_iri_glotec.py request.json GloTEC.nc output.tsv")
    req=json.loads(Path(sys.argv[1]).read_text())
    print(json.dumps(fuse(req,Path(sys.argv[2]),Path(sys.argv[3])),indent=2))
