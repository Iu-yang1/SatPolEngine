import datetime as dt
import math
import pathlib
import sys
import unittest
import numpy as np
import xarray as xr
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/"observations"))
from fuse_iri_glotec import correction_ratio, interpolate_field, require_model_snapshot

def fixture():
    t=np.asarray(["2026-10-20T12:00:00"],dtype="datetime64[ns]")
    base=np.ones((1,3,3))
    coords={"time":t,"latitude":[-10.,0.,10.],"longitude":[-175.,-170.,175.]}
    return xr.Dataset({
       "TEC":(("time","latitude","longitude"),base*12,{"units":"Total Electron Content (10^16*m^-2)"}),
       "NmF2":(("time","latitude","longitude"),base*6e11),
       "hmF2":(("time","latitude","longitude"),base*320),
       "quality_flag":(("time","latitude","longitude"),base*4)
    },coords=coords)
class FusionTests(unittest.TestCase):
    def test_expiry(self):
        with self.assertRaisesRegex(ValueError,"stale"):
            require_model_snapshot(fixture(),dt.datetime(2026,10,21,tzinfo=dt.timezone.utc))
    def test_dateline_and_units(self):
        snap,_,_=require_model_snapshot(fixture(),dt.datetime(2026,10,20,12,tzinfo=dt.timezone.utc))
        self.assertAlmostEqual(interpolate_field(snap,"TEC",0.,179.9),12)
        self.assertAlmostEqual(interpolate_field(snap,"NmF2",0.,-179.),6e11)
    def test_positivity_and_no_fulltec_shortcut(self):
        self.assertAlmostEqual(correction_ratio(1e11,6e11,320,6e11,320,320),1e11)
        self.assertAlmostEqual(correction_ratio(1e11,6e11,320,12e11,320,320),2e11)
        with self.assertRaises(ValueError):
            correction_ratio(1e11,6e11,320,100e11,320,320)
    def test_missing_units(self):
        ds=fixture()
        ds["TEC"].attrs.clear()
        with self.assertRaisesRegex(ValueError,"TECU"):
            require_model_snapshot(ds,dt.datetime(2026,10,20,12,tzinfo=dt.timezone.utc))
if __name__=="__main__":unittest.main()
