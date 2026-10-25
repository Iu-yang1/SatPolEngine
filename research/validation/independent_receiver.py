#!/usr/bin/env python3
"""Independent synthetic dual-polarization receiving system for CASSIOPE.

The physical spacecraft's published S-band RF downlink is RHCP, not linearly
polarized. Its deterministic ideal linear-receiver loss is 3.01dB at every roll.
A separate COUNTERFACTUAL body-X linear dipole scenario uses real satellite
ephemeris and attitude but is NOT a real flight transmitter.

This receiver simulator never uses Kotlin Jones/roll estimator to synthesize
voltage or estimate polarization: it generates complex port voltages with AWGN,
samples a mechanical roll sweep, and independently fits a 2*angle harmonic.
"""
from __future__ import annotations
import json
import math
from pathlib import Path
import re
import sys
import numpy as np

def ecef_station_at_subpoint(spacecraft_xyz):
    p=np.asarray(spacecraft_xyz,dtype=float)
    lon=math.atan2(p[1],p[0])
    lat=math.atan2(p[2],math.hypot(p[0],p[1]))
    a=6378137.0
    e2=6.6943799901413165e-3
    n=a/math.sqrt(1-e2*math.sin(lat)**2)
    station=np.asarray([
        n*math.cos(lat)*math.cos(lon),
        n*math.cos(lat)*math.sin(lon),
        n*(1-e2)*math.sin(lat)])
    east=np.array([-math.sin(lon),math.cos(lon),0.])
    north=np.array([-math.sin(lat)*math.cos(lon),-math.sin(lat)*math.sin(lon),math.cos(lat)])
    up=np.array([math.cos(lat)*math.cos(lon),math.cos(lat)*math.sin(lon),math.sin(lat)])
    los=p-station
    los/=np.linalg.norm(los)
    elev_up=up-los*np.dot(up,los)
    elev_up/=np.linalg.norm(elev_up)
    az_right=np.cross(los,up)
    az_right/=np.linalg.norm(az_right)
    return station,los,elev_up,az_right

def load_actual_body_x(aem:Path,utc:str):
    matches=[line for line in aem.read_text().splitlines()
             if line.startswith(utc.replace("Z",".")) or line.startswith(utc+" ")]
    if len(matches)!=1:
        raise ValueError("No unique attitude quaternion at UTC "+utc)
    fields=matches[0].split()
    qx,qy,qz,qw=map(float,fields[1:])
    return np.asarray([1-2*(qy*qy+qz*qz),
                       2*(qx*qy+qz*qw),2*(qx*qz-qy*qw)],dtype=float)

def real_geometry_truth(orbit:Path,aem:Path):
    o=json.loads(orbit.read_text())
    p=np.asarray(o["position_ecef_m"],dtype=float)
    station,los,e0,e1=ecef_station_at_subpoint(p)
    bodyx=load_actual_body_x(aem,o["time_utc"])
    transverse=bodyx-los*np.dot(bodyx,los)
    if np.linalg.norm(transverse)<1e-6:
        raise ValueError("Ideal body-X dipole at null; cannot simulate unique roll")
    angle=math.degrees(math.atan2(np.dot(transverse,e1),np.dot(transverse,e0)))
    return (angle+90)%180-90

def wrap_delta_deg(a,b):
    return (a-b+90)%180-90

def sample_sweep(jones,rng,snr_db=15.,samples=64,step_deg=5.):
    """One independent noisy complex-IQ measurement batch per receive angle."""
    e=np.asarray(jones,dtype=complex)
    if not np.isclose(np.vdot(e,e).real,1.,atol=1e-10):
        raise ValueError("Input Jones electric field must have unit power")
    angles=np.arange(0.,180.,step_deg,dtype=float)
    measured=[]
    sigma=10**(-snr_db/20.)
    for angle in angles:
        r=math.radians(float(angle))
        source=e[0]*math.cos(r)+e[1]*math.sin(r)
        thermal=(rng.normal(size=samples)+1j*rng.normal(size=samples))*sigma/math.sqrt(2)
        measured.append(float(np.mean(np.abs(source+thermal)**2)))
    return angles,np.asarray(measured)

def fit_sweep(angles,power,minimum_modulation=0.08):
    """Fit P(alpha) = DC + C*cos(2alpha)+S*sin(2alpha) with least squares.
    No field data from the forward model enters this estimation.
    """
    a=np.deg2rad(angles)
    design=np.column_stack([np.ones(len(a)),np.cos(2*a),np.sin(2*a)])
    dc,c,s=np.linalg.lstsq(design,power,rcond=None)[0]
    modulation=float(math.hypot(c,s)/dc)
    if modulation<minimum_modulation:return None,modulation
    theta=math.degrees(0.5*math.atan2(s,c))
    return (theta+90)%180-90,modulation

def measure_response(truth_angle_deg,rng,snr_db=15.):
    a=math.radians(truth_angle_deg)
    jones=[math.cos(a)+0j,math.sin(a)+0j]
    angles,power=sample_sweep(jones,rng,snr_db)
    return fit_sweep(angles,power)

def run(orbit:Path,aem:Path,kotlin_report:Path,output:Path,trials=256):
    if trials<100:raise ValueError("Too few randomized test trials")
    model=real_geometry_truth(orbit,aem)
    found=re.search(r'COUNTERFACTUAL_LINEAR,angle_deg=([^,]+)',kotlin_report.read_text())
    if not found:raise ValueError("Missing Kotlin theoretical linear target")
    predicted=float(found.group(1))
    cross_check=abs(wrap_delta_deg(predicted,model))
    if cross_check>1e-8:
        raise AssertionError(f"Kotlin vs independent geometry {cross_check} deg")
    rng=np.random.default_rng(20261025)
    actual_diffs=[]
    circular_false=0
    mixed_linear_errors=[]
    for i in range(trials):
        independent_measured,_=measure_response(model,rng)
        if independent_measured is None:
            raise AssertionError("Strong real-geometry linear model incorrectly rejected")
        actual_diffs.append(abs(wrap_delta_deg(independent_measured,predicted)))
        # Actual published RHCP downlink: a linearly polarized feed must not
        # falsely report an optimum, regardless of rotating the antenna.
        angles,power=sample_sweep([1/math.sqrt(2),1j/math.sqrt(2)],rng)
        circular_estimate,_=fit_sweep(angles,power)
        circular_false+=int(circular_estimate is not None)
        # Explicit *hypothetical* uncertainty priors, not calibrated actual
        # satellite RF uncertainties. Independent of the prediction model.
        true_angle=model+rng.normal(0,2.5)+rng.normal(0,0.06)+rng.normal(0,4.0)
        sampled,_=measure_response(true_angle,rng)
        mixed_linear_errors.append(abs(wrap_delta_deg(sampled,predicted)))
    q=lambda a:float(np.percentile(a,95))
    report={
      "real_hardware":"CASSIOPE downlink 2231.5MHz RHCP, ±Z patch panel normals",
      "actual_rf_transmitter_wave_observed":False,
      "truth_kind":"Synthetic independent complex-IQ receiver voltages using actual orbit and attitude",
      "real_orbit_and_attitude_epoch_utc":json.loads(orbit.read_text())["time_utc"],
      "hypothetical_linear_emitter":"Virtual body-X ideal dipole; NOT CASSIOPE transmitter",
      "synthetic_linear_true_deg":model,
      "synthetic_linear_kotlin_prediction_deg":predicted,
      "geometry_independent_difference_deg":cross_check,
      "receiver_snr_db":15.0,
      "per_angle_complex_samples":64,
      "sweep_step_deg":5.0,
      "monte_carlo_trials":trials,
      "linear_noisy_receiver_median_abs_error_deg":float(np.median(actual_diffs)),
      "linear_noisy_receiver_p95_abs_error_deg":q(actual_diffs),
      "rhcp_receiver_optimum_defined":False,
      "rhcp_false_optimum_rate":circular_false/trials,
      "rhcp_expected_roll_independent_loss_db":10*math.log10(2),
      "hypothetical_uncertainty_priors_deg":{
        "antenna_mounting_sigma":2.5,
        "attitude_sigma":0.06,
        "propagation_model_sigma":4.0
      },
      "hypothetical_uncertainty_p95_error_deg":q(mixed_linear_errors),
      "caution":"Simulation validates consistency only, not real link angle or Faraday sign"
    }
    if q(actual_diffs)>1.0:raise AssertionError("Noisy independent linear receiver error >1deg P95")
    if circular_false/trials>0.05:
        raise AssertionError("Simulated circular wave was spuriously assigned a stable optimum")
    output.write_text(json.dumps(report,indent=2),encoding="utf-8")
    return report

if __name__=="__main__":
    if len(sys.argv)!=5:
        sys.exit("Usage: independent_receiver.py realOrbit.json real.aem kotlin_output.txt output.json")
    print(json.dumps(run(Path(sys.argv[1]),Path(sys.argv[2]),Path(sys.argv[3]),Path(sys.argv[4])),indent=2))
