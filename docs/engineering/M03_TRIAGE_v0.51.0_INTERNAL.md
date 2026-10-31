# M03 Triage — SYNQORIA v0.51.0 Internal Implementation

**Source checkpoint:** verified v0.50.0 cumulative source, with no changes to canonical module or screen IDs.

**Clinical status:** NOT APPROVED. The new capabilities are domain/read source implementations and synthetic standalone Java tests, not deployed workflows.

## Functional deliverables

The 60 implementation slices B0094–B0153 provide 50 guarded triage domain capabilities and 10 patient-scoped worklist/read projections, across intake, queues, assessments, human-entered priority, handover and scoped reads. This is an internal domain layer, **not 60 completed modules, HTTP APIs, or screens**.

Every mutation requires trusted backend-authenticated patient/tenant/facility identity, per-patient access evidence, active shift/MFA/privilege checks, optimistic revision matching, and a chain-linked state digest. Synthetic tests run individually by batch ID and check state effects, stale revision rejection and audit integrity. The receiving clinician explicitly accepts and acknowledges a handover. No urgency algorithm or treatment protocol is generated.

## Implementation slices

| Batch | Category | Capability | Test evidence |
|---|---|---|---|
| P06-B0094 | intake | authenticated arrival registration | `evidence/v510/P06-B0094.log` |
| P06-B0095 | intake | arrival reason capture | `evidence/v510/P06-B0095.log` |
| P06-B0096 | intake | preferred language capture | `evidence/v510/P06-B0096.log` |
| P06-B0097 | intake | interpreter request tracking | `evidence/v510/P06-B0097.log` |
| P06-B0098 | intake | accessibility support request | `evidence/v510/P06-B0098.log` |
| P06-B0099 | intake | identity verification reference | `evidence/v510/P06-B0099.log` |
| P06-B0100 | intake | arrival channel controlled selection | `evidence/v510/P06-B0100.log` |
| P06-B0101 | intake | referral provenance capture | `evidence/v510/P06-B0101.log` |
| P06-B0102 | intake | attendant contact reference | `evidence/v510/P06-B0102.log` |
| P06-B0103 | intake | consent evidence linking | `evidence/v510/P06-B0103.log` |
| P06-B0104 | queue | scoped queue entry | `evidence/v510/P06-B0104.log` |
| P06-B0105 | queue | safe queue call | `evidence/v510/P06-B0105.log` |
| P06-B0106 | queue | explicit queue recall | `evidence/v510/P06-B0106.log` |
| P06-B0107 | queue | reasoned return to waiting | `evidence/v510/P06-B0107.log` |
| P06-B0108 | queue | reasoned queue pause | `evidence/v510/P06-B0108.log` |
| P06-B0109 | queue | queue resume with state guard | `evidence/v510/P06-B0109.log` |
| P06-B0110 | queue | reasoned station transfer | `evidence/v510/P06-B0110.log` |
| P06-B0111 | queue | patient not responding handling | `evidence/v510/P06-B0111.log` |
| P06-B0112 | queue | authorized safety hold | `evidence/v510/P06-B0112.log` |
| P06-B0113 | queue | reasoned safety hold release | `evidence/v510/P06-B0113.log` |
| P06-B0114 | assessment | clinician assessment start | `evidence/v510/P06-B0114.log` |
| P06-B0115 | assessment | timestamped observation provenance | `evidence/v510/P06-B0115.log` |
| P06-B0116 | assessment | observation correction lineage | `evidence/v510/P06-B0116.log` |
| P06-B0117 | assessment | clinician assessment narrative | `evidence/v510/P06-B0117.log` |
| P06-B0118 | assessment | observation device provenance | `evidence/v510/P06-B0118.log` |
| P06-B0119 | assessment | patient statement preservation | `evidence/v510/P06-B0119.log` |
| P06-B0120 | assessment | allergy review evidence link | `evidence/v510/P06-B0120.log` |
| P06-B0121 | assessment | clinical assessment sign off | `evidence/v510/P06-B0121.log` |
| P06-B0122 | assessment | immutable signed assessment addendum | `evidence/v510/P06-B0122.log` |
| P06-B0123 | assessment | declined observation with rationale | `evidence/v510/P06-B0123.log` |
| P06-B0124 | priority | human-assigned urgency | `evidence/v510/P06-B0124.log` |
| P06-B0125 | priority | reasoned human priority revision | `evidence/v510/P06-B0125.log` |
| P06-B0126 | priority | reassessment request | `evidence/v510/P06-B0126.log` |
| P06-B0127 | priority | reassessment start | `evidence/v510/P06-B0127.log` |
| P06-B0128 | priority | clinician reassessment documentation | `evidence/v510/P06-B0128.log` |
| P06-B0129 | priority | completed reassessment evidence | `evidence/v510/P06-B0129.log` |
| P06-B0130 | priority | manual escalation flag | `evidence/v510/P06-B0130.log` |
| P06-B0131 | priority | manual escalation acknowledgement | `evidence/v510/P06-B0131.log` |
| P06-B0132 | priority | reasoned escalation resolution | `evidence/v510/P06-B0132.log` |
| P06-B0133 | priority | clinical urgency evidence reference | `evidence/v510/P06-B0133.log` |
| P06-B0134 | handover | clinician assignment | `evidence/v510/P06-B0134.log` |
| P06-B0135 | handover | reasoned clinician reassignment | `evidence/v510/P06-B0135.log` |
| P06-B0136 | handover | handover request initiation | `evidence/v510/P06-B0136.log` |
| P06-B0137 | handover | receiving clinician acceptance | `evidence/v510/P06-B0137.log` |
| P06-B0138 | handover | receiving clinician rejection | `evidence/v510/P06-B0138.log` |
| P06-B0139 | handover | handover note capture | `evidence/v510/P06-B0139.log` |
| P06-B0140 | handover | receiving clinician acknowledgement | `evidence/v510/P06-B0140.log` |
| P06-B0141 | handover | acknowledged safe handover | `evidence/v510/P06-B0141.log` |
| P06-B0142 | handover | reasoned triage closure | `evidence/v510/P06-B0142.log` |
| P06-B0143 | handover | receiving destination capture | `evidence/v510/P06-B0143.log` |
| P06-B0144 | read | patient-scoped case summary | `evidence/v510/P06-B0144.log` |
| P06-B0145 | read | authorized facility queue projection | `evidence/v510/P06-B0145.log` |
| P06-B0146 | read | station-scoped queue projection | `evidence/v510/P06-B0146.log` |
| P06-B0147 | read | handover outstanding worklist | `evidence/v510/P06-B0147.log` |
| P06-B0148 | read | clinician-entered escalation worklist | `evidence/v510/P06-B0148.log` |
| P06-B0149 | read | reassessment request worklist | `evidence/v510/P06-B0149.log` |
| P06-B0150 | read | safety holds worklist | `evidence/v510/P06-B0150.log` |
| P06-B0151 | read | assigned clinician worklist | `evidence/v510/P06-B0151.log` |
| P06-B0152 | read | bounded arrivals worklist | `evidence/v510/P06-B0152.log` |
| P06-B0153 | read | hash-checked per-case history | `evidence/v510/P06-B0153.log` |

## Quarantined persistence design

`V015__triage_workflow_quarantine.sql` defines scoped case state, audit events, and outbox with RLS ENABLE/FORCE and PUBLIC REVOKE; **no policies/grants** exist. Not applied to a live database. SQL migration inventory, checksum lock, and adversarial static tests pass. Clinical PHI encryption, restricted grants, RLS and consent enforcement need independent review.

## Verification and major limitations

- Full Java tree compiled in available Java 21 toolchain; each of 60 feature cases executed in a separate Java process with actual exit code/evidence.
- Synthetic end-to-end appointment-linked arrival-to-closed triage journey and tamper/authorization tests pass.
- Existing UX-core, navigation and source contract suites run separately; **Next.js and native Expo builds remain unverified**.
- All data are synthetic. No real hospital data, clinical thresholds, triage score algorithms or medical protocols were introduced.
- `TriageCase` domain state and read projections are not connected to transactional JDBC, a verified production HTTP gateway, OIDC, staff mobile UI or patient mobile UI.
- A structured schema exists but is intentionally quarantined; **no live PostgreSQL RLS/security enforcement has been tested**.
- Clinically approved priority terminology, provider authorizations, regulatory retention, encryption at rest, concurrent transaction testing, signed clinical review and pilot are outstanding.
- 0/261 canonical screens clinically accepted and 0/40 modules clinically certified. Phase P06 remains NOT CERTIFIED.

## Next engineering batch

**P06-B0154:** implement patient-scoped transactional triage repository integration, restricted database connection and outbox/audit consistency, then run real PostgreSQL RLS/role tests before any deployment claim.
