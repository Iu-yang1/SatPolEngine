"""Independent full TEME state regression using the python-sgp4 Vallado implementation.

Run from the repo root after 'pip install sgp4' with Gradle 8.14+ / JDK 17.
Diagnostics intentionally do not self-certify the original Look4Sat SGP4:
small deviations should be understood before marketing the solver as precise.
"""
import datetime as dt
import math
import os
import re
import subprocess
from sgp4.api import Satrec, jday

def tle_checksum(line):
    lead=line.ljust(68)[:68]
    return lead+str(sum(int(x) if x.isdigit() else (1 if x=='-' else 0) for x in lead)%10)

TLE1=tle_checksum("1 25544U 98067A   24182.51187500  .00014091  00000-0  25410-3 0  999")
TLE2=tle_checksum("2 25544  51.6408  75.2780 0006003  97.9389  16.6024 15.5003712445998")

def compare_one(instant):
    sat=Satrec.twoline2rv(TLE1,TLE2)
    jd,fr=jday(instant.year,instant.month,instant.day,instant.hour,instant.minute,
                instant.second+instant.microsecond*1e-6)
    status,pos,vel=sat.sgp4(jd,fr)
    if status!=0:raise RuntimeError(f"Reference SGP4 error {status}")
    env=os.environ.copy()
    env.update(SATPOL_TLE1=TLE1,SATPOL_TLE2=TLE2,
        SATPOL_EPOCH_UTC=instant.isoformat().replace("+00:00","Z"))
    result=subprocess.run(["gradle","--quiet","--no-daemon","orbitVector"],
        capture_output=True,text=True,env=env,check=True)
    hits=re.findall(r"SATPOL_VECTOR=([^\n]+)",result.stdout)
    if len(hits)!=1:raise RuntimeError("No vector from Kotlin CLI: "+result.stdout)
    items=[float(a) for a in hits[0].split(",")]
    rp=math.dist(items[:3],pos);rv=math.dist(items[3:],vel)
    print(f"epoch={instant.isoformat()} | delta position={rp:.6f} km,"
        f" velocity={rv*1000:.6f} m/s")
    return rp,rv

if __name__=="__main__":
    times=[
        dt.datetime(2024,6,30,12,0,tzinfo=dt.timezone.utc),
        dt.datetime(2024,6,30,12,10,tzinfo=dt.timezone.utc),
        dt.datetime(2024,7,1,12,0,tzinfo=dt.timezone.utc)]
    errors=[compare_one(t) for t in times]
    assert all(math.isfinite(x) for pair in errors for x in pair)
    # Preliminary integration guard. A much stricter requirement will be
    # appropriate before absolute-RF geometry is considered validated.
    assert max(x[0] for x in errors)<50.0, "SGP4 port diverges > 50 km from Vallado reference"
    assert max(x[1] for x in errors)<0.050, "SGP4 port diverges > 50 m/s from Vallado reference"
