# M39 externally observed cell readiness — operator runbook

M39 previously accepted health assertions, but had no actual network probe worker. This one-shot observer makes HTTPS calls to multiple explicitly configured readiness endpoints and sends a strictly bounded health result to the existing M39 API.

```bash
python tools/m39_cell_health_observer.py \
  --probe https://cell-internal.example/ready/clinical \
  --probe https://cell-internal.example/ready/storage \
  --api-base https://fleet-control.internal.example/api/v1/fleet \
  --cell-id 00000000-0000-0000-0000-000000000111 \
  --token-file /run/secrets/fleet-health-reporter-oidc-token
```

The worker issues **HEALTHY only if all configured probes return HTTP 200 with explicit JSON `{"ready":true}`**. Any bad/missing response results in **UNHEALTHY**, which M39 must refuse to route. It never asserts DEGRADED. Invalid configuration or inability to submit observations is an error, not a healthy result. M39's five-minute freshness gate will fail closed if the observer is unavailable.

## Operational requirements

- Use an authorized workload identity with only `m39:health_report`, correct cell/facility and tenant scope. Never provide a platform administrator token. Store token only in a private mode-0600 file and rotate it.
- Run in a trusted dedicated execution environment with network policy allowlisting exactly the planned readiness endpoints and the control-plane API. Do not accept endpoints from untrusted request data or permit public URLs.
- Probe independent clinical-readiness, storage and database-backed operations, not merely a web server liveness page. Specify at least two independent readiness endpoints; their semantic independence is a deployment configuration responsibility.
- Schedule at a suitable cadence below M39's 300-second freshness threshold, with non-overlapping worker executions. Observe failed submission and alert separately.
- Use separate authenticated TLS connections; reject HTTP, credentials in URL, query-borne tokens, redirects and false/ambiguous response formats.
- Alert on two or more consecutive failures; do not convert them into HEALTHY or silently ignore missing observations.
- Include real M39 identity service, network policies, workload scheduler and gateway verification in disposable end-to-end tests before hospital deployment.

## Limitations

This component is not yet attached to a Kubernetes CronJob or an actual hospital. A positive HTTP readiness response does not independently prove the hospital cell is safe or medically qualified. It does not provision cells, enforce storage residency, execute failover, approve deployments or authenticate clinical access. Health reports can only represent the endpoints that were configured and checked.
