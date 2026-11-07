#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes="$(mktemp -d -t synqoria-m040506-XXXXXX)"
trap 'rm -rf "$classes"' EXIT
# Java 21 required. Test implementation, not clinical qualification.
mapfile -d '' sources < <(find services/clinical-core/src/main/java services/clinical-core/src/test/java -type f -name '*.java' -print0)
javac -d "$classes" "${sources[@]}"
# Full-project compilation is mandatory after HTTP/security adapters depend on sibling packages.
# Re-run the historical 60 domain scenarios without overwriting their original v600 evidence.
for i in $(seq 1 60); do
  batch="$(printf 'P07-B%04d' "$i")"
  result="$(java -ea -cp "$classes" org.synqoria.longitudinal.ClinicalVerticalBatchTest "$batch")"
  [[ "$result" == "PASS $batch "* ]] || { echo "FAIL: $batch regression" >&2; exit 1; }
done
echo "PASS 60 historical P07 domain operations (regression only, no new batches)"
java -ea -cp "$classes" org.synqoria.longitudinal.ClinicalBoundaryAdversarialTest
python3 tests/test_baseline.py
python3 tests/test_navigation_contract.py
python3 tests/test_v240_checkpoint.py
python3 tools/verify_status_matrix.py
