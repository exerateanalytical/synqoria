#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/services/clinical-core/src/main/java/org/synqoria/longitudinal/inboxops"
TEST="$ROOT/services/clinical-core/src/test/java/org/synqoria/longitudinal/inboxops"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
javac -encoding UTF-8 --release 21 -d "$BUILD" "$SRC"/Inbox*.java "$TEST"/Inbox*Test.java
count=0
for source in "$TEST"/Inbox*Test.java; do
  test_class="$(basename "$source" .java)"
  java -cp "$BUILD" "org.synqoria.longitudinal.inboxops.$test_class"
  count=$((count+1))
done
if [[ "$count" != "30" ]]; then
  printf 'FAIL expected 30 executed Java test classes, got %s\n' "$count" >&2
  exit 2
fi
printf 'PASS v0.67.0 30/30 individually executed new Java test classes\n'
