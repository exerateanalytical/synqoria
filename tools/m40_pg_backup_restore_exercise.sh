#!/usr/bin/env bash
# M40 disposable-environment backup/restore exercise against a REAL PostgreSQL server (synthetic data only).
# It performs a logical backup (pg_dump -Fc), restores it into a fresh database, proves content equality by digest, then corrupts a copy of the
# backup and proves the corruption is detected. It prints measured wall-clock seconds as evidence for THIS synthetic dataset only: it is not an
# RPO/RTO guarantee for any hospital cell, and it does not exercise WAL archiving or point-in-time recovery.
# Usage: PG_CONTAINER=m26-pg PG_USER=postgres bash tools/m40_pg_backup_restore_exercise.sh [evidence.json]
set -euo pipefail
export MSYS_NO_PATHCONV=1
C="${PG_CONTAINER:?set PG_CONTAINER to the disposable postgres container}"
U="${PG_USER:-postgres}"
OUT="${1:-m40-exercise-evidence.json}"
ROWS="${ROWS:-50000}"
psqlc() { docker exec -i "$C" psql -U "$U" -v ON_ERROR_STOP=1 -X -q -At "$@"; }

psqlc -d postgres -c "DROP DATABASE IF EXISTS m40_src" -c "DROP DATABASE IF EXISTS m40_restore" -c "DROP DATABASE IF EXISTS m40_corrupt" -c "CREATE DATABASE m40_src"
psqlc -d m40_src <<SQL
CREATE TABLE encounters_synthetic(id bigint PRIMARY KEY, ward text NOT NULL, code text NOT NULL, amount numeric(12,2) NOT NULL, payload jsonb NOT NULL);
INSERT INTO encounters_synthetic SELECT g, 'ward-'||(g%17), md5(g::text), (g%1000)/10.0, jsonb_build_object('n',g,'h',md5((g*7)::text)) FROM generate_series(1,${ROWS}) g;
CREATE INDEX ON encounters_synthetic(ward);
SQL
digest() { psqlc -d "$1" -c "SELECT md5(string_agg(id||ward||code||amount::text||payload::text, ',' ORDER BY id)) || ':' || count(*) FROM encounters_synthetic"; }
SRC_DIGEST="$(digest m40_src)"

docker exec "$C" sh -c "rm -f /tmp/m40.dump /tmp/m40.corrupt.dump"
T0=$(date +%s.%N)
docker exec "$C" pg_dump -U "$U" -Fc -f /tmp/m40.dump m40_src
T1=$(date +%s.%N)
DUMP_SHA="$(docker exec "$C" sha256sum /tmp/m40.dump | cut -d' ' -f1)"
DUMP_BYTES="$(docker exec "$C" stat -c %s /tmp/m40.dump)"

psqlc -d postgres -c "CREATE DATABASE m40_restore"
T2=$(date +%s.%N)
docker exec "$C" pg_restore -U "$U" -d m40_restore --exit-on-error /tmp/m40.dump
T3=$(date +%s.%N)
RESTORE_DIGEST="$(digest m40_restore)"
[ "$SRC_DIGEST" = "$RESTORE_DIGEST" ] || { echo "FAIL: restored content differs from the source"; exit 1; }

# corrupt a copy: overwrite 4 KiB in the middle of the archive
docker exec "$C" sh -c "cp /tmp/m40.dump /tmp/m40.corrupt.dump && dd if=/dev/zero of=/tmp/m40.corrupt.dump bs=1 seek=\$(( $DUMP_BYTES / 2 )) count=4096 conv=notrunc 2>/dev/null"
CORRUPT_SHA="$(docker exec "$C" sha256sum /tmp/m40.corrupt.dump | cut -d' ' -f1)"
[ "$CORRUPT_SHA" != "$DUMP_SHA" ] || { echo "FAIL: corruption did not change the digest"; exit 1; }
psqlc -d postgres -c "CREATE DATABASE m40_corrupt"
if docker exec "$C" pg_restore -U "$U" -d m40_corrupt --exit-on-error /tmp/m40.corrupt.dump >/dev/null 2>&1; then RESTORE_FAILED=false; else RESTORE_FAILED=true; fi
CORRUPT_DIGEST="$(digest m40_corrupt 2>/dev/null || echo unreadable)"
DETECTED_BY_DIGEST=true                                   # the recorded SHA-256 of the artifact no longer matches: this alone refuses the backup
DETECTED_BY_RESTORE=$RESTORE_FAILED
[ "$CORRUPT_DIGEST" != "$SRC_DIGEST" ] || DETECTED_BY_RESTORE=false

docker exec "$C" rm -f /tmp/m40.dump /tmp/m40.corrupt.dump
psqlc -d postgres -c "DROP DATABASE m40_src" -c "DROP DATABASE m40_restore" -c "DROP DATABASE IF EXISTS m40_corrupt"
SERVER="$(psqlc -d postgres -c 'SHOW server_version')"
BACKUP_S="$(awk -v a="$T1" -v b="$T0" "BEGIN{printf \"%.3f\", a-b}")"; RESTORE_S="$(awk -v a="$T3" -v b="$T2" "BEGIN{printf \"%.3f\", a-b}")"
cat > "$OUT" <<JSON
{"scope":"synthetic logical backup and restore only; no WAL archiving, no point-in-time recovery, no guarantee for any hospital cell",
 "server_version":"$SERVER","rows":$ROWS,"dump_bytes":$DUMP_BYTES,"dump_sha256":"$DUMP_SHA",
 "backup_seconds":$BACKUP_S,"restore_seconds":$RESTORE_S,
 "restored_content_equals_source":true,"source_digest":"$SRC_DIGEST",
 "corrupt_copy_sha256":"$CORRUPT_SHA","corruption_detected_by_digest":$DETECTED_BY_DIGEST,"corrupt_restore_refused_or_differs":$DETECTED_BY_RESTORE}
JSON
cat "$OUT"
[ "$DETECTED_BY_RESTORE" = true ] || echo "NOTE: pg_restore tolerated this corruption; the SHA-256 comparison is what refuses the backup"
