import datetime as dt
import pathlib
import sys
import unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/"attitude"))
from cassiope_orbit import parse_sp3,UTC

class Sp3Tests(unittest.TestCase):
    def row(self,code,x,y,z):
        return code+"CAS"+ "".join(f"{v:14.6f}" for v in (x,y,z))
    def test_gps_utc_leap_offset_required(self):
        # This is synthetic SP3 text; 2017-06-24 GPS-UTC offset is +18s.
        txt="*  2017  6 24 22 32 32.00000000\n"+self.row("P",7000,0,0)+"\n"+
            self.row("V",0,75000,0)+"\n"
        x=parse_sp3(txt,UTC)
        self.assertEqual(x["time_gps"],"2017-06-24T22:32:32Z")
        self.assertEqual(x["position_ecef_m"],[7000000,0,0])
        self.assertEqual(x["velocity_ecef_m_s"],[0,7500,0])
        with self.assertRaisesRegex(ValueError,"No unique"):
            parse_sp3(txt,UTC,gps_minus_utc_seconds=0)

if __name__=="__main__":unittest.main()
