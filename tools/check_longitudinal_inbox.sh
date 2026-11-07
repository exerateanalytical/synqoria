#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
SRC="$ROOT/services/clinical-core/src/main/java/org/synqoria/longitudinal"
TEST="$ROOT/services/clinical-core/src/test/java/org/synqoria/longitudinal"
javac -encoding UTF-8 --release 21 -d "$BUILD" "$SRC/ClinicalCaseCore.java" "$SRC/LongitudinalInboxSafety.java" "$TEST/LongitudinalInboxSafetyTest.java"
for scenario in envelope consumer-permit dedupe sequence-gate lease-fence retries redrive commit processor receipt-privacy; do
  java -cp "$BUILD" org.synqoria.longitudinal.LongitudinalInboxSafetyTest "$scenario"
done
