# M40 authenticated catalogue-bound artifact inspection

The previous M40 artifact utility accepted a caller-supplied hash and size. This integration component instead retrieves those values from **M40's authenticated backup catalogue** before inspecting a separately staged, read-only local artifact.

## Invoking

```bash
python tools/m40_catalogue_backup_inspector.py \
  --api-base https://reliability.internal.example/api/v1/reliability \
  --backup-id 00000000-0000-0000-0000-000000000123 \
  --artifact /restricted/staging/artifact.dump \
  --token-file /run/secrets/m40-backup-read-token
```

The token file must be accessible only to the verifier identity (e.g. mode 0600). The caller must supply a valid short-lived OIDC token with `m40:backup_read`, properly scoped to the relevant hospital cell and facility. The service validates issuer, audience, signature, scope and expiry. The verifier enforces HTTPS and refuses redirects. Do not expose the token to logs, command arguments or web clients. The endpoint must be trusted and use managed TLS roots; do not disable certificate checking.

Results distinguish an **integrity comparison** from catalogue VERIFIED status. Even a matching artifact in RECORDED state remains RECORDED; the utility does not submit the five M40 checks. Machine-readable result exits 0 for digest+size match and 2 otherwise. Patient identifiers and artifact filenames are omitted from output.

## Required orchestration before a hospital deployment

1. Use a trusted backup source and immutable, read-only staging; pin artifact source identity. This utility does not download or attest the remote object.
2. Retrieve a time-limited OIDC token from approved workload identity and expose it as a private token file; never grant `backup_verify` to the backup producer.
3. Retrieve scoped catalogue metadata from GET `/backups/{id}`; reject mismatched IDs and invalid size/digest metadata. Inspect actual file bytes and detect modification during read.
4. Preserve an immutable evidence record referencing the run, matched digest, item ID, source object version, tool version, job identity and time (without PHI). This utility does not persist that record.
5. A **different** authorized verifier must independently establish inventory, encryption-key usability, completeness and *actual restore readiness* before it may submit all five checks to POST `/backups/{id}/verification`.
6. Conduct disposable PostgreSQL WAL/PITR and real restore rehearsals with operational authorization before making RPO/RTO or production recovery claims.

## Explicit remaining gaps

There is no Kubernetes CronJob, token issuer integration, immutable object-store fetch, M40 API verification writer, restore executor, WAL replay, failover actuator or live hospital deployment here. This is one tested read-only integration building block, not a finished backup agent.
