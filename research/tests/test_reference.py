import math
import pathlib
import sys
import unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
from reference import axis_deg, faraday_uniform, roll_delta_deg

class ReferenceTests(unittest.TestCase):
    def test_faraday_frequency_square(self):
        x=faraday_uniform(435e6,1e6,5e10,20e-6)
        self.assertAlmostEqual(faraday_uniform(145e6,1e6,5e10,20e-6)/x,9.0)
    def test_analytic_angle(self):
        result=math.degrees(faraday_uniform(435e6,1e6,5e10,20e-6))
        self.assertAlmostEqual(result,7.16,delta=0.05)
    def test_zero_density(self):
        self.assertEqual(faraday_uniform(145e6,1e6,0.0,25e-6),0.0)
    def test_jones_linear(self):
        p=math.radians(30)
        self.assertAlmostEqual(axis_deg(complex(math.cos(p)),complex(math.sin(p))),30)
    def test_circular_null(self):
        self.assertIsNone(axis_deg(1+0j,1j))
    def test_180_periodicity(self):
        self.assertAlmostEqual(roll_delta_deg(-89.0,89.0),2.0)

if __name__=="__main__":
    unittest.main()
