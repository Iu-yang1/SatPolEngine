#!/usr/bin/env bash
set -euo pipefail
# Download original IRI distribution and daily-index inputs from IRI homepage.
# Run in an isolated directory; files must never be committed as stale indices.
HERE="$(cd "$(dirname "$0")" && pwd)"
OUTPUT="${1:-$PWD/iri2026_work}"
mkdir -p "$OUTPUT"
cd "$OUTPUT"
# irimodel.org sometimes denies automated GitHub-hosted curl requests (HTTP 406).
# Honor externally supplied official archives; otherwise try only official URLs.
if [[ -n "${SATPOL_IRI_ARCHIVE:-}" ]]; then
  cp "$SATPOL_IRI_ARCHIVE" iri.zip
else
  USER_AGENT='Mozilla/5.0 (compatible; SatPolEngine-research/0.2; +https://github.com/Iu-yang1/SatPolEngine)'
  downloaded=0
  for url in \
    'https://irimodel.org/IRI-2026/00_iri.zip' \
    'https://www.irimodel.org/IRI-2026/00_iri.zip' \
    'https://irimodel.org/IRI-2026/00_iri.zip?download=1'; do
    if curl -fsSL --max-time 60 --retry 2 -A "$USER_AGENT" \
        -e 'https://irimodel.org/IRI-2026/' \
        -H 'Accept: application/zip,application/octet-stream,*/*' \
        -o iri.zip "$url" && unzip -tq iri.zip >/dev/null 2>&1; then
      downloaded=1; break
    fi
  done
  if [[ "$downloaded" != 1 ]]; then
    echo 'Cannot fetch official IRI-2026 ZIP from GitHub runner.' >&2
    echo 'Download https://irimodel.org/IRI-2026/00_iri.zip and set SATPOL_IRI_ARCHIVE to its local path.' >&2
    exit 86
  fi
fi
if [[ -n "${SATPOL_IRI_APF107:-}" ]]; then
  cp "$SATPOL_IRI_APF107" apf107.dat
else
  curl -fsSL --retry 2 -o apf107.dat 'https://chain-new.chain-project.net/echaim_downloads/apf107.dat'
fi
if [[ -n "${SATPOL_IRI_IG_RZ:-}" ]]; then
  cp "$SATPOL_IRI_IG_RZ" ig_rz.dat
else
  curl -fsSL --retry 2 -o ig_rz.dat 'https://chain-new.chain-project.net/echaim_downloads/ig_rz.dat'
fi
unzip -tq iri.zip >/dev/null
sha256sum iri.zip apf107.dat ig_rz.dat
test "$(stat -c %s iri.zip)" -gt 1000000
test "$(stat -c %s apf107.dat)" -gt 100000
test "$(stat -c %s ig_rz.dat)" -gt 500
unzip -q -o iri.zip
MODEL_SUBDIR="$(find "$OUTPUT" -type f -iname irisub.for -print -quit)"
if [[ -z "$MODEL_SUBDIR" ]]; then echo "Model archive missing irisub.for" >&2; exit 3; fi
MODEL_SUBDIR="$(dirname "$MODEL_SUBDIR")"
cp "$OUTPUT/apf107.dat" "$MODEL_SUBDIR/apf107.dat"
cp "$OUTPUT/ig_rz.dat" "$MODEL_SUBDIR/ig_rz.dat"
cd "$MODEL_SUBDIR"
# Compile from the original extracted model directory, including data files.
gfortran -O2 -std=legacy -ffixed-line-length-none -fallow-argument-mismatch -o iri2026-density \
  "$HERE/iri_density_driver.f90" irisub.for irifun.for iritec.for \
  iridreg.for igrf.for cira.for iriflip.for rocdrift.for
printf '%s\n' "$MODEL_SUBDIR/iri2026-density" > "$OUTPUT/executable.path"
echo "Official IRI-2026 ready: $MODEL_SUBDIR/iri2026-density"
