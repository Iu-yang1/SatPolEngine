#!/usr/bin/env python3
"""Read ACTUAL 2017-06-24 Swarm-E/CASSIOPE 1Hz definitive spacecraft
quaternions; transform BODY->ITRF and export CCSDS-style AEM KVN for Kotlin.

The historical CDF file is publicly hosted by University of Calgary.
Its q ordering is scalar LAST; 0..6 sensor quality flags are retained in
JSON sidecar; never treat a spacecraft RRI receiving dipole as proof of
an independently calibrated transmitted VHF/UHF wave polarization.
"""
from __future__ import annotations
import datetime as dt
import hashlib
import json
import math
from pathlib import Path
import sys
import tempfile
from urllib.request import Request,urlopen
import zipfile
import numpy as np
import cdflib

URL=("https://epop-data.phys.ucalgary.ca/2017/06/24/"
     "CAS_AttQuat_20170624T000000_20170624T235959_2.0.0.cdf.zip")
CENTRE=dt.datetime.fromisoformat("2017-06-24T22:32:14+00:00")

def download(dest:Path):
    req=Request(URL,headers={"User-Agent":"SatPolEngine-Science/0.3"})
    with urlopen(req,timeout=90) as r, dest.open("wb") as f:
        import shutil
        shutil.copyfileobj(r,f)
    if dest.stat().st_size < 2_000_000: raise ValueError("CASSIOPE CDF archive unexpectedly small")

def _cdf_epoch_to_utc(value):
    times=cdflib.cdfepoch.to_datetime(value)
    # UTC-naive numpy datetime64 values from CDF's UTC epoch axis.
    return np.asarray(times,dtype="datetime64[ns]")

def export(archive:Path,out_aem:Path,out_report:Path):
    with zipfile.ZipFile(archive) as z:
        members=[name for name in z.namelist() if name.lower().endswith(".cdf")]
        if len(members)!=1:raise ValueError("Unexpected CDF member count")
        with tempfile.TemporaryDirectory() as td:
            path=Path(z.extract(members[0],td))
            cdf=cdflib.CDF(str(path))
            try:
                q=np.asarray(cdf.varget("q"),dtype=float)
                ts=_cdf_epoch_to_utc(cdf.varget("Timestamp"))
                sources_a=np.asarray(cdf.varget("Data_Source_Previous"),dtype=int)
                sources_b=np.asarray(cdf.varget("Data_Source_Next"),dtype=int)
            finally: del cdf
    if len(q)!=len(ts) or q.shape[-1]!=4:
        raise ValueError("Invalid official CASSIOPE 1Hz attitude shape")
    if len(q)!=len(sources_a) or len(q)!=len(sources_b):
        raise ValueError("Attitude provenance fields length mismatch")
    if not np.all(np.diff(ts.astype("int64"))>0):
        raise ValueError("Out-of-order definitive attitude epochs")
    centre=np.datetime64(CENTRE.replace(tzinfo=None),"ns")
    mask=(ts>=centre-np.timedelta64(12,"s"))&(ts<=centre+np.timedelta64(12,"s"))
    ids=np.where(mask)[0]
    accepted=[]
    for i in ids:
        row=q[i]
        quality=min(sources_a[i],sources_b[i])
        if not np.isfinite(row).all() or quality<2:continue
        magnitude=float(np.linalg.norm(row))
        if not 0.999<=magnitude<=1.001:
            raise ValueError("Non-unit CASSIOPE attitude quaternion")
        accepted.append((i,quality))
    if len(accepted)<2:
        raise ValueError("No usable REAL telemetry samples in chosen RRI interval")
    # CDF scalar-last [qx,qy,qz,qw] has documented ITRF<-Body transform.
    # Export only continuous valid samples from the selected 25-second interval.
    indices=[i for i,_ in accepted]
    if not all(b-a==1 for a,b in zip(indices,indices[1:])):
        raise ValueError("Sensor-verified sequence is not continuous")
    def iso(t):
        s=np.datetime_as_string(t,unit="ms",timezone="UTC")
        return s if s.endswith("Z") else s+"Z"
    start=iso(ts[indices[0]]);end=iso(ts[indices[-1]])
    aem=[
        "CCSDS_AEM_VERS = 2.0",
        "ORIGINATOR = SatPolEngine research conversion (not original AEM)",
        "META_START",
        "OBJECT_NAME = CASSIOPE-SWARM-E",
        "OBJECT_ID = 2013-055A",
        "CENTER_NAME = EARTH",
        "REF_FRAME_A = SC_BODY",
        "REF_FRAME_B = ITRF",
        "TIME_SYSTEM = UTC",
        f"START_TIME = {start}",
        f"STOP_TIME = {end}",
        "ATTITUDE_TYPE = QUATERNION",
        "QUATERNION_TYPE = LAST",
        "META_STOP",
        "DATA_START",
    ]
    for i,_ in accepted:
        aem.append(iso(ts[i])+" "+" ".join(f"{v:.15g}" for v in q[i]))
    aem+=["DATA_STOP",""]
    out_aem.write_text("\n".join(aem),encoding="utf-8")
    report={
        "source":URL,
        "source_sha256":hashlib.sha256(archive.read_bytes()).hexdigest(),
        "mission":"CASSIOPE / Swarm-E",
        "source_data":"REAL definitive AttQuat, scalar-last q ITRF<-Body",
        "selected_RRI_event_UTC":"2017-06-24T22:32:14Z",
        "valid_samples":len(accepted),
        "start":start,"end":end,
        "sensor_provenance_min":int(min(x[1] for x in accepted)),
        "sensor_provenance_max":int(max(x[1] for x in accepted)),
        "axis_used_for_math":"body +X reference axis ONLY; not antenna absolute RF polarization",
        "real_radio_polarization_angle_measured":False,
        "absolute_satellite_pol_prediction_validated":False,
    }
    out_report.write_text(json.dumps(report,indent=2),encoding="utf-8")
    return report

if __name__=="__main__":
    if len(sys.argv)!=4:sys.exit("usage: cassiope_attitude.py archive.zip out.aem report.json")
    archive=Path(sys.argv[1])
    if not archive.is_file():download(archive)
    report=export(archive,Path(sys.argv[2]),Path(sys.argv[3]))
    print(json.dumps(report,indent=2))
