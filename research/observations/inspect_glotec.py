#!/usr/bin/env python3
"""Download one REAL NOAA archived GloTEC daily NetCDF, enumerate its field metadata.

All parsing is CF/NetCDF aware and no unknown variable name is guessed.
The snapshot is used for EVIDENCE ONLY until value/units/epoch normalization
is explicitly mapped to an observation schema.
"""
from __future__ import annotations
import datetime as dt
import json
from pathlib import Path
import sys
from urllib.request import urlopen, Request

BASE="https://services.swpc.noaa.gov/products/glotec/netcdf_2d_urt"
def download(day: str, dest: Path) -> None:
    date=dt.date.fromisoformat(day)
    link=f"{BASE}/GloTEC_TEC_{date:%Y_%m_%d}.nc"
    req=Request(link,headers={"User-Agent":"SatPolEngine-Research/0.2 (NOAA TEC)"})
    with urlopen(req,timeout=90) as source, dest.open("wb") as f:
        import shutil
        shutil.copyfileobj(source,f)
    if dest.stat().st_size<50000:
        raise ValueError("Not a valid GloTEC model archive")
    print(f"NOAA GloTEC archive: {link}, bytes={dest.stat().st_size}")

def report(path:Path):
    import xarray as xr
    ds=xr.open_dataset(path,decode_cf=True)
    try:
        summary={
            "source":"NOAA SWPC archived GloTEC NetCDF",
            "global_attributes":{k:str(v)[:150] for k,v in ds.attrs.items()},
            "dimensions":{k:int(v) for k,v in ds.sizes.items()},
            "coordinates":{k:{"dims":list(v.dims),"shape":list(v.shape),
                               "attrs":{a:str(b)[:150] for a,b in v.attrs.items()}}
                           for k,v in ds.coords.items()},
            "variables":{k:{"dims":list(v.dims),"shape":list(v.shape),
                            "attrs":{a:str(b)[:150] for a,b in v.attrs.items()}}
                          for k,v in ds.data_vars.items()}
        }
        print(json.dumps(summary,ensure_ascii=False,indent=2))
        return summary
    finally:ds.close()

if __name__=="__main__":
    day=sys.argv[1] if len(sys.argv)>1 else "2026-10-20"
    out=Path(sys.argv[2]) if len(sys.argv)>2 else Path("/tmp/satpol-glotec.nc")
    download(day,out)
    report(out)
