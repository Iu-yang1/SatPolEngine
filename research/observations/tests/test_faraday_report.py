import math
import unittest

def parse(lines):
    records=[]
    for line in lines.splitlines():
        if line.startswith("FARADAY_DATA,"):
            pairs=dict(x.split("=",1) for x in line.split(",")[1:])
            records.append(pairs)
    return records

class FaradayReportTests(unittest.TestCase):
    def test_exact_frequency_scaling_in_independent_report(self):
        data = """FARADAY_DATA,frequency_mhz=145.0,iri_angle_deg=90,fused_angle_deg=45,iri_stec_tecu=5,fused_stec_tecu=3,epoch=2026-10-20T12:00:00Z
FARADAY_DATA,frequency_mhz=435.0,iri_angle_deg=10,fused_angle_deg=5,iri_stec_tecu=5,fused_stec_tecu=3,epoch=2026-10-20T12:00:00Z"""
        a,b=parse(data)
        self.assertEqual(float(a["iri_angle_deg"])/float(b["iri_angle_deg"]),9)
        self.assertEqual(float(a["fused_angle_deg"])/float(b["fused_angle_deg"]),9)
        self.assertEqual(a["epoch"],b["epoch"])

if __name__=="__main__":unittest.main()
