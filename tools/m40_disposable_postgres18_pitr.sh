#!/usr/bin/env bash
# Real PostgreSQL 18.6 WAL archive + physical basebackup + point-in-time restore.
# CI-ONLY: disposable volumes and synthetic transactions, never clinical data.
set -Eeuo pipefail
test -n "$RUNNER_TEMP"
prefix="synqoria-m40-ci-$$"
primary="$prefix-primary"
restored="$prefix-restored"
wal="$prefix-wal"
base="$prefix-base"
recovery="$prefix-recovery"
cleanup() {
  docker rm -f "$primary" "$restored" >/dev/null 2>&1 || true
  docker volume rm -f "$wal" "$base" "$recovery" >/dev/null 2>&1 || true
}
trap cleanup EXIT
docker volume create "$wal" >/dev/null
docker volume create "$base" >/dev/null
docker volume create "$recovery" >/dev/null

# No published TCP ports; all SQL and replication commands run via docker exec.
docker run -d --name "$primary" \
  -e POSTGRES_PASSWORD=m40-disposable-ci-only \
  -e POSTGRES_DB=m40_ci \
  -v "$wal:/wal" -v "$base:/basebackup" \
  postgres:18.6 \
  -c wal_level=replica \
  -c archive_mode=on \
  -c 'archive_command=test ! -f /wal/%f && cp %p /wal/%f' \
  -c archive_timeout=2s \
  -c full_page_writes=on >/dev/null

ready() {
  local c="$1"
  for i in $(seq 1 120); do
    if docker exec -u postgres "$c" pg_isready -U postgres -d m40_ci >/dev/null 2>&1; then
      return 0
    fi
    if ! docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null | grep -qx true; then
      echo "M40_PITR_FAIL postgres_exit" >&2
      docker logs --tail=60 "$c" >&2 || true
      return 1
    fi
    sleep 1
  done
  echo "M40_PITR_FAIL postgres_not_ready" >&2
  docker logs --tail=60 "$c" >&2 || true
  return 1
}
sql() {
  local c="$1" command="$2"
  docker exec -u postgres "$c" psql -X -A -t -v ON_ERROR_STOP=1 \
    -U postgres -d m40_ci -c "$command"
}
ready "$primary"
# Only disposable dedicated volumes are granted to the postgres runtime UID.
docker exec -u root "$primary" chown postgres:postgres /wal /basebackup
test "$(sql "$primary" "SHOW server_version_num;")" -ge 180000
test "$(sql "$primary" "SHOW archive_mode;")" = on

sql "$primary" "CREATE TABLE public.m40_pitr_probe (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, marker text NOT NULL UNIQUE);" >/dev/null
sql "$primary" "INSERT INTO public.m40_pitr_probe(marker) VALUES ('base_marker');" >/dev/null

# True physical basebackup, including backup_label and streamed WAL.
docker exec -u postgres -e PGPASSWORD=m40-disposable-ci-only "$primary" \
  pg_basebackup -h 127.0.0.1 -U postgres \
  --pgdata=/basebackup --format=plain --wal-method=stream \
  --checkpoint=fast --no-password --progress
docker exec -u postgres "$primary" test -f /basebackup/PG_VERSION
docker exec -u postgres "$primary" test -f /basebackup/backup_label

# Both following transactions commit AFTER the basebackup completed. WAL replay
# must include the first and exclude the second at the chosen UTC target time.
sql "$primary" "INSERT INTO public.m40_pitr_probe(marker) VALUES ('before_target');" >/dev/null
sql "$primary" "SELECT pg_switch_wal();" >/dev/null
target="$(sql "$primary" "SELECT to_char(clock_timestamp() AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS.US') || '+00';")"
test -n "$target"
sleep 2
sql "$primary" "INSERT INTO public.m40_pitr_probe(marker) VALUES ('after_target');" >/dev/null
sql "$primary" "SELECT pg_switch_wal();" >/dev/null
test "$(sql "$primary" "SELECT count(*) FROM public.m40_pitr_probe WHERE marker='before_target';")" = 1
test "$(sql "$primary" "SELECT count(*) FROM public.m40_pitr_probe WHERE marker='after_target';")" = 1

# Verify multiple completed WAL segment archives before loss of the primary.
archived=0
for i in $(seq 1 90); do
  archived="$(sql "$primary" "SELECT archived_count FROM pg_stat_archiver;")"
  if test "$archived" -ge 2; then break; fi
  sleep 1
done
if test "$archived" -lt 2; then
  echo "M40_PITR_FAIL WAL_segments_not_archived" >&2
  exit 1
fi
docker stop -t 30 "$primary" >/dev/null

# Entirely new PostgreSQL 18 PGDATA directory/volume. The primary's data
# volume is NOT reused, and the WAL archive is separately mounted read-only.
docker run --rm -u root -e PITR_TARGET="$target" \
  -v "$base:/basebackup:ro" -v "$recovery:/restore" \
  postgres:18.6 bash -Eeuc '
    test -f /basebackup/PG_VERSION
    mkdir -p /restore/18/docker
    cp -a /basebackup/. /restore/18/docker/
    chown -R postgres:postgres /restore/18/docker
    touch /restore/18/docker/recovery.signal
    cat >> /restore/18/docker/postgresql.auto.conf <<EOF
restore_command = '"'"'cp /wal/%f %p'"'"'
recovery_target_time = '"'"'$PITR_TARGET'"'"'
recovery_target_action = '"'"'pause'"'"'
EOF
  '
docker run -d --name "$restored" \
  -e POSTGRES_PASSWORD=m40-disposable-ci-only \
  -e PGDATA=/var/lib/postgresql/18/docker \
  -v "$recovery:/var/lib/postgresql" -v "$wal:/wal:ro" \
  postgres:18.6 -c archive_mode=off >/dev/null
ready "$restored"

# Prove WAL replayed a committed post-basebackup write but excluded a later
# committed write at the selected target. No synthetic "pass" without SQL.
test "$(sql "$restored" "SELECT pg_is_in_recovery();")" = t
test "$(sql "$restored" "SELECT count(*) FROM public.m40_pitr_probe WHERE marker='base_marker';")" = 1
test "$(sql "$restored" "SELECT count(*) FROM public.m40_pitr_probe WHERE marker='before_target';")" = 1
test "$(sql "$restored" "SELECT count(*) FROM public.m40_pitr_probe WHERE marker='after_target';")" = 0

echo "M40_PITR_DISPOSABLE_PG18_PASS basebackup=true wal_archive_segments_gte_2=true replayed_post_basebackup_write=true excluded_post_target_commit=true recovered_pg_is_in_recovery=true"
