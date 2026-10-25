#!/usr/bin/env bash
set -euo pipefail
# Download original IRI distribution and daily-index inputs from IRI homepage.
# Run in an isolated directory; files must never be committed as stale indices.
HERE="$(cd "$(dirname "$0")" && pwd)"
OUTPUT="${1:-$PWD/iri2026_work}"
mkdir -p "$OUTPUT"
cd "$OUTPUT"
curl --fail --location --retry 3 --max-time 90 -o iri.zip 'https://irimodel.org/IRI-2026/00_iri.zip'
curl --fail --location --retry 3 --max-time 90 -o apf107.dat 'https://chain-new.chain-project.net/echaim_downloads/apf107.dat'
curl --fail --location --retry 3 --max-time 90 -o ig_rz.dat 'https://chain-new.chain-project.net/echaim_downloads/ig_rz.dat'
test "$(stat -c %s iri.zip)" -gt 1000000
test "$(stat -c %s apf107.dat)" -gt 100000
test "$(stat -c %s ig_rz.dat)" -gt 500
unzip -q -o iri.zip
# Original README specifies this exact source-file compilation sequence.
gfortran -O2 -std=legacy -ffixed-line-length-none -o iri2026-density \
  "$HERE/iri_density_driver.f90" irisub.for irifun.for iritec.for \
  iridreg.for igrf.for cira.for iriflip.for rocdrift.for
echo "Official IRI-2026 ready: $OUTPUT/iri2026-density"
