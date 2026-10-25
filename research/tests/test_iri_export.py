import unittest
import pathlib
import sys
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
from iri_export_path import ecef_to_geodetic

class GeodeticConversionTests(unittest.TestCase):
    def test_equator(self):
        a,b,h=ecef_to_geodetic(6378137,0,0)
        self.assertAlmostEqual(a,0)
        self.assertAlmostEqual(b,0)
        self.assertAlmostEqual(h,0,places=6)
    def test_polar_height(self):
        a,b,h=ecef_to_geodetic(0,0,6356752.314245+400000)
        self.assertAlmostEqual(a,90)
        self.assertAlmostEqual(h,400000,delta=.002)
