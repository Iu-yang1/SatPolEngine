#!/usr/bin/env python3
"""Join independent CASSIOPE definitive SP3 ITRF orbit and CDF-derived
ITRF<-BODY attitude at EXACT UTC epoch, rejecting an uncorrected GPS-time join.

CASSIOPE SP3 uses GPS time; 2017-06-24 GPS-UTC offset was +18 s.
Source: University of Calgary e-POP daily Orbit GEO SP3 product.
This is a historical orbit+attitude validation, not live TLE prediction.
"""
import datetime as dt
import json
import math
from pathlib import Path
import re
import sys
from urllib.request import Request,urlopen
from urllib.parse import urljoin
import zipfile
import io

DAY="2017-06-24"
UTC=dt.datetime(2017,6,24,22,32,14,tzinfo=dt.timezone.utc)
DIRECTORY="https://epop-data.phys.ucalgary.ca/2017/06/24/"
CANDIDATES=[
  f"CAS_Orbit_GEO_20170624T000000_20170624T235959_{v}.sp3.zip"
  for v in ("1.1.0","1.2.0","1.3.0","2.0.0")
]

def retrieve(path:Path):
    attempts=[]
    urls=[urljoin(DIRECTORY,name) for name in CANDIDATES]
    try:
        req=Request(DIRECTORY,headers={"User-Agent":"SatPolEngine-Research/0.3"})
        html=urlopen(req,timeout=30).read().decode("utf-8","replace")
        names=re.findall(r'(CAS_Orbit_GEO_20170624[^"<>\s]+?\.sp3\.zip)',html,re.I)
        urls=[urljoin(DIRECTORY,name.replace("&amp;","&")) for name in names]+urls
    except Exception as exc: attempts.append(f"index:{type(exc).__name__}")
    for url in dict.fromkeys(urls):
        try:
            req=Request(url,headers={"User-Agent":"SatPolEngine-Research/0.3"})
            with urlopen(req,timeout=75) as reqobj:
                data=reqobj.read()
            if len(data)>100000 and zipfile.is_zipfile(io.BytesIO(data)):
                path.write_bytes(data)
                return url
            attempts.append(f"{url}:badZip/short")
        except Exception as exc:
            attempts.append(f"{url}:{type(exc).__name__}")
    raise RuntimeError("Unable to retrieve actual orbit SP3; "+"; ".join(attempts))

def parse_epoch(line):
    fields=line[1:].split()
    if len(fields)<6:raise ValueError("Bad SP3 epoch line")
    yy,mm,dd,hh,minute=map(int,fields[:5])
    sec=float(fields[5])
    return dt.datetime(yy,mm,dd,hh,minute,tzinfo=dt.timezone.utc)+dt.timedelta(seconds=sec)

def parse_sp3(text,utc=UTC,gps_minus_utc_seconds=18):
    gps=utc+dt.timedelta(seconds=gps_minus_utc_seconds)
    target=None
    current=None
    position=None
    vel=None
    matched=[]
    for line in text.splitlines():
        if line.startswith("*"):
            if current==gps and position is not None and vel is not None:
                matched.append((position,vel))
            current=parse_epoch(line)
            position=None;vel=None
        elif line.startswith("P") and current==gps:
            a=line[4:46]
            try: xyz=[float(a[i:i+14]) for i in (0,14,28)]
            except ValueError: xyz=list(map(float,line.split()[1:4]))
            if len(xyz)==3 and all(math.isfinite(v) and abs(v)<25000 for v in xyz):
                position=[v*1000.0 for v in xyz]
        elif line.startswith("V") and current==gps:
            try: components=[float(line[i:i+14]) for i in (4,18,32)]
            except ValueError: components=list(map(float,line.split()[1:4]))
            if len(components)==3 and all(math.isfinite(v) and abs(v)<200000 for v in components):
                # SP3 velocity = dm/s, so multiply by 0.1 to convert m/s.
                vel=[v*0.1 for v in components]
    if current==gps and position is not None and vel is not None:
        matched.append((position,vel))
    if len(matched)!=1:
        raise ValueError(f"No unique same-epoch P/V in definitive SP3 (got {len(matched)})")
    p,v=matched[0]
    length=math.sqrt(sum(x*x for x in p))
    speed=math.sqrt(sum(x*x for x in v))
    if not 6.4e6<length<10e6 or not 1e3<speed<10e3:
        raise ValueError(f"Physically implausible ECEF p/v: {length} m, {speed} m/s")
    return {"time_utc":utc.isoformat().replace("+00:00","Z"),
        "time_gps":gps.isoformat().replace("+00:00","Z"),
        "gps_minus_utc_s":gps_minus_utc_seconds,
        "position_ecef_m":p,"velocity_ecef_m_s":v,
        "speed_ecef_m_s":speed,"radius_m":length}

def export(archive:Path,out_json:Path):
    if archive.is_file():source=f"user-supplied:{archive.name}"
    else:source=retrieve(archive)
    with zipfile.ZipFile(archive) as z:
        names=[x for x in z.namelist() if x.lower().endswith(".sp3")]
        if len(names)!=1:raise ValueError(f"Unexpected SP3 members {names}")
        data=z.read(names[0]).decode("ascii","replace")
    point=parse_sp3(data)
    point["source"]=source
    point["reference_frame"]="ITRF / ECEF"
    point["mission"]="CASSIOPE / Swarm-E"
    point["data_kind"]="Real definitive orbit SP3, NOT SGP4/TLE"
    out_json.write_text(json.dumps(point,indent=2),encoding="utf-8")
    return point

if __name__=="__main__":
    if len(sys.argv)!=3:sys.exit("Usage: cassiope_orbit.py orbit.zip output.json")
    print(json.dumps(export(Path(sys.argv[1]),Path(sys.argv[2])),indent=2))
