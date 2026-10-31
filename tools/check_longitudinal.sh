#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes="$(mktemp -d -t synqoria-m040506-XXXXXX)"
trap 'rm -rf "$classes"' EXIT
# Java 21 required. Test implementation, not clinical qualification.
mapfile -d '' sources < <(find services/clinical-core/src/main/java services/clinical-core/src/test/java -type f -name '*.java' -print0)
javac -d "$classes" "${sources[@]}"
SYNQORIA_TEST_CLASSES="$classes" python3 tools/run_v600_batches.py
java -ea -cp "$classes" org.synqoria.longitudinal.ClinicalBoundaryAdversarialTest
python3 tests/test_baseline.py
python3 tests/test_navigation_contract.py
python3 tests/test_v240_checkpoint.py
python3 tools/verify_status_matrix.py
