# M23 preventive maintenance, calibration and servicing calendar - handoff (Issue #68)

**Source-engineering only.** Not hospital acceptance, deployment approval, manufacturer certification or clinical release. Branch `work/claude-m23-preventive-maintenance-2026-11-07`, based on `main` `2f6fb5b205a999efdb6e8dff6026dfe32750ee50`. Not self-merged.
The package matrix is at the end; **30/30 is not claimed** (see packages 26 and 29).

## 1. What already existed (reused, not rebuilt)
| Existing | Where | How it is reused |
|---|---|---|
| M23 module catalogue, ASSET + WORK_ORDER state machine (`REGISTERED -> COMMISSIONED -> ACTIVE <-> MAINTENANCE -> RETIRED`; `WORK_DRAFT -> WORK_OPEN -> WORK_ASSIGNED -> WORK_COMPLETE_PENDING_VERIFICATION -> WORK_VERIFIED`) | `domain/ModuleCatalog.java` | **Unchanged.** No new asset or work-order state. Work orders are still created/opened/assigned/completed/verified only through `/api/v1/operations/M23/items/...`. |
| Trusted evidence gates (`biomed_inspection`, `calibration_safe`) | `OperationsStore.authoritativeEvidence`, table `external_evidence` | Unchanged; calibration and return-to-service keep using it. |
| `maintenance_orders` (V004), `workforce_grants` (V002), `items`, `audit_events`, `outbox`, `command_idempotency` | `db/migration` | Read/joined; audit+outbox reused for every maintenance mutation. |
| Five canonical screens M23-S01..S05 | `registers/canonical-screens.json`, `OperationsWorkbench`, `/workforce/clinical/<id>` | Unchanged routes; a read-only panel is mounted on S01/S03/S05. |
| Verified-JWT + live workforce-grant admission | `WorkforceAdmission`, `OperationsActor` | Every new endpoint goes through it. |

## 2. What was added (all M23-owned)
* `db/migration/V013__m23_preventive_maintenance.sql` - 10 tables, all `ENABLE`+`FORCE ROW LEVEL SECURITY` on cell+facility: `pm_policy_versions`, `pm_plans`, `pm_plan_versions`, `pm_occurrences`, `pm_calibrations`, `pm_service_references`, `pm_cost_references`, `pm_escalations`, `pm_planner_state`, `pm_commands`. History tables are insert-only for the runtime role; version rows are immutable by trigger (only the one-way decision columns change); occurrences only move `PLANNED -> WORK_LINKED|CANCELLED`, `WORK_LINKED -> COMPLETED` under trigger-enforced preconditions.
* `domain/MaintenanceRules.java` (pure rules), `MaintenanceService.java`, `MaintenanceApi.java` (18 operations under `/api/v1/operations/M23/maintenance`), `MaintenanceGuards.java`.
* `contracts/openapi/synqoria-m23-maintenance-v1.yaml` and `tests/test_m23_openapi_contract.py`.
* `apps/web/components/maintenance/MaintenancePanel.tsx` (read-only).
* CI `m23-preventive-maintenance-ci.yml`.

### Surgical edits to shared files (all M23-specific; please review)
| File | Change | Why |
|---|---|---|
| `OperationsDomainEffects.java` | M23 `ASSIGN_WORK` now requires the asset still offline and the technician to be a live, on-shift workforce member with `m23:maintenance`; M23 `VERIFY_WORK` requires the verifier differ from the technician; M23 `RETURN_TO_SERVICE` requires a valid calibration when the asset's approved plan includes calibration | packages 11, 13, 14, 21, 23 |
| `OperationalRules.java` | M23 asset metadata validation (serial, manufacturer, model, safety class) | package 15 |
| `OperationsErrors.java` | adds HTTP status mapping for the new M23 error codes (409/400) | API behaviour |
| `OperationsWorkbench.tsx` | one label: technician is now a workforce member UUID | matches the new rule |
| `app/api/operations/proxy/[...segments]/route.ts` | allow-lists five M23 maintenance **GET** paths; validates `from`/`to`/`staleAfterMinutes` | package 26 |
| `app/workforce/clinical/[screenId]/page.tsx` | mounts the panel for M23 screens only | package 26 |
| `five-operational-integrations-ci.yml` | FORCE-RLS table count 25 -> 35 (V013 adds 10) | existing assertion would otherwise fail |
| `OperationsBusinessPgIntegrationTest.java` | M23 test fixture: technician is a granted workforce member (was free text `biomed-42`) | behaviour change above |

**Behaviour change to be aware of:** `technicianRef` on an M23 work order must now be the UUID of a workforce member with a current shift and `m23:maintenance`. Free-text technician names are refused (fail closed).

## 3. How it works
* **Policy** (`pm_policy_versions`): per facility and safety class; propose + independent approval; never looser than the explicit safe default (CRITICAL 30d/90d cal, HIGH 90/180, MEDIUM 180/365, LOW 365/730). Absent an approved policy the defaults apply.
* **Plan** (`pm_plans` + immutable `pm_plan_versions`): one live plan per asset; interval/calibration interval bounded by the policy; time zone, anchor date, local time, `EXACT_DAY` or `NEXT_WEEKDAY`; propose -> independent approve; approving a new version supersedes the old and withdraws only never-linked future occurrences.
* **Occurrences**: due instant is local-calendar arithmetic in the plan zone (DST-safe), persisted as UTC; occurrence id is a name-based UUID of `(plan, version, seq)`, so regeneration never double-schedules. Generation window is `[now - interval, now + horizon]`, capped (24 per plan, 200 plans per run).
* **Planner lease** (`pm_planner_state`): `runPlanner` = acquire lease (own transaction, fence token) -> fenced generation (own transaction) -> release. A second planner gets `planner_lease_held`; a holder that lost the lease gets `planner_lease_lost` and writes nothing. **Nothing starts the planner by itself:** a trusted scheduler (cron / platform job) must call `POST /planner/generate` and `/planner/escalate` with a service identity holding `m23:pm_plan_run`. `GET /planner/health` reports `UNKNOWN / HEALTHY / DEGRADED / STALE` and never reports unknown or stale as healthy.
* **Linking**: an occurrence links to an **existing** open work order for the same asset while the asset is `MAINTENANCE`; a unique index allows one active service per asset and one occurrence per work order. The planner never takes an asset offline and never returns one to service.
* **Completion**: `completeOccurrence` closes the occurrence only after the existing independently verified work order (and, for calibration kinds, a calibration recorded after the link); completer must differ from the linker and the technician. It never touches the asset.
* **Calibration**: accepted only with independently issued, unexpired, single-use `calibration_safe` evidence for `RECORD_CALIBRATION` at the asset's current version whose approver is not the recorder; interval bounded by policy.
* **Escalation**: one row per `(occurrence, level 1..3)` computed from lateness past `due + grace`; each emits `audit_events` + `outbox` event `M23_PM_OVERDUE_ESCALATION` with payload `{occurrenceId, assetId, level, dueAt}`. This is the **M37 contract only** (ids and level; no recipient, no message body). No sender is created and M37 is untouched.
* **References**: supplier / engineer document references are stored `attested=false` (CHECK-enforced) and never satisfy a trusted-evidence gate. Cost references are administrative (`amountMinor`, ISO currency, cost-centre ref) and nothing is posted.
* **Read models**: bounded calendar (<= 366 days, <= 200 rows) with projection `SCHEDULED/DUE/OVERDUE/BLOCKED/IN_SERVICE/DONE/CANCELLED`; asset history without staff identities unless `m23:audit_read`; inspection queue limited to work the caller may independently verify, with a fail-closed `evidenceReady` flag.

### Permissions introduced (fit the existing `m23:[a-z_]+` grant grammar)
`m23:pm_propose`, `m23:pm_approve`, `m23:pm_plan_run`, `m23:pm_link`, `m23:pm_complete`, `m23:calibrate`, `m23:pm_record`, `m23:audit_read` (plus existing `m23:read`, `m23:return`). The hospital roster feed must provision them; none is hard-coded.

## 4. Verification evidence (local, this machine)
* Java 25 (Temurin 25.0.5), Maven 3.9.16, PostgreSQL **18.6** disposable container, restricted non-owner `synqoria_operations_runtime` (NOBYPASSRLS), migrations V001-V013 applied in order; 35 FORCE-RLS tables.
* Whole `operations-core` module: **83 tests, 0 failures** (`mvn verify` with `SYNQORIA_OPS_PG_TEST=1`) - 75 before the unit tests were added + 8 `MaintenanceRulesTest`.
  * `MaintenancePgIntegrationTest` **25** (real PostgreSQL), `MaintenanceRulesTest` **8**, existing 50 retained (including the adapted M23 business test).
* `python tests/test_m23_openapi_contract.py` OK (18 operations; negative mutation run fails as intended).
* `node --test tests/m23_maintenance_frontend.test.mjs tests/operations_screen_catalog.test.mjs` 7 pass.
* Strict `tsc` on `MaintenancePanel.tsx`: clean except the ambient `*.module.css` declaration that the ad-hoc config lacks (Next provides it).
* CI runs on the PR head are recorded in the PR (see below); a local pass is not a CI pass.

### Test inventory (PostgreSQL integration, one fresh cell/facility per test)
policy maker-checker / immutability / safe-default bound; plan bounds, one live plan, DB-level maker-checker; command replay and collision; version supersession; deterministic idempotent generation; lease exclusion and stale-fence rejection; 8-thread concurrent planners (no duplicates); planner health UNKNOWN/HEALTHY/DEGRADED/STALE; leveled idempotent escalation with outbox-only emission; link rules and one-active-service-per-asset (incl. direct-insert bypass refused); technician shift/grant enforcement; repair stops when asset no longer offline; independent verifier; inspection queue exclusions and evidence flag; calibration evidence rules and insert-only table; calibration-bearing plan blocks return to service; expired/missing evidence blocks return to service; occurrence completion preconditions; unattested references; cost references without ledger/finance export; bounded calendar and BLOCKED projection; history privacy; audit+outbox atomicity and rollback; cross-cell/facility isolation and unscoped sessions see nothing; asset metadata validation.

## 5. Residual hospital / integration dependencies (nothing here removes them)
1. A trusted **scheduler** must invoke the planner; none is included. Health detects its absence.
2. A real **workforce roster feed** must populate `workforce_grants` with the new permissions and shifts; this repo only provides the table and checks.
3. A real **evidence issuer** (outside the runtime role) must write `external_evidence` for `RECORD_CALIBRATION`, `COMPLETE_WORK`, `VERIFY_WORK`, `RETURN_TO_SERVICE`. The engineering tests insert synthetic evidence as the migration owner.
4. **M37** consumption of `M23_PM_OVERDUE_ESCALATION` is the integration lead's; no delivery, recipient resolution or receipt exists.
5. **Calendar**: `NEXT_WEEKDAY` skips weekends only; no public-holiday or hospital closure calendar is modelled.
6. **UI**: planning, linking, calibration and approvals have no screen controls; the browser proxy exposes read paths only. The panel was **not rendered in a real browser** and the canonical Next build was not run locally (shared build problems are the integration lead's).
7. Policy values (safe defaults, intervals, grace, escalation hours) are engineering defaults awaiting the hospital's biomedical engineering sign-off. A plan is not manufacturer certification.
8. Production migration approval, issuer configuration, per-cell deployment and clinical-engineering acceptance are not done.
9. The shared `pm_commands` replay stores results indefinitely; a retention rule is not defined.

## 6. 30-package matrix
Legend: **V** implemented and verified by the named evidence; **P** partial (what is missing is stated).
| # | Package | St | Evidence |
|---|---|---|---|
| 1 | Preflight, ownership, inventory | V | main `2f6fb5b`, ownership register read, section 1; open PRs listed |
| 2 | Map existing M23 APIs/entities/transitions/screens/tests | V | section 1 and web test asserting the five canonical screens and flows are unchanged (inventory is documentary, not machine-extracted) |
| 3 | Asset lifecycle extension only for proved gaps | V | no new state; `SevenModuleWorkflowTest` and the existing M23 test still pass |
| 4 | Due-maintenance policy per class and facility, safe defaults | V | `MaintenanceRulesTest.defaults...`, `policyIsMakerChecker...` |
| 5 | Versioned cell-scoped plan table with FORCE RLS | V | V013; `anotherCellCannotSee...` (10 tables FORCE RLS, unscoped session sees 0) |
| 6 | Approval, immutable version history, maker-checker | V | `planRequires...`, `policyIs...` (trigger and CHECK refuse even the owner) |
| 7 | Interval / time zone / calendar, UTC persisted | V | `dueTimeKeepsTheLocalClockAcrossDaylightSaving...`, `nextWeekdayRule...` (no holiday calendar) |
| 8 | Deterministic occurrence generation, stable ids | V | `occurrenceIdsAreStable...`, `generationIsDeterministic...` |
| 9 | Transactional idempotency for plan / occurrence generation | V | `replayedCommandReturnsTheStoredResult...`, generation replay |
| 10 | Mutually exclusive servicing per asset | V | unique index; `occurrenceLinksOnly...` incl. direct-insert refusal |
| 11 | Asset `MAINTENANCE` before repair; no unsafe auto-return | V | `repairCannotContinue...`, link/trigger tests, completion leaves asset in `MAINTENANCE` |
| 12 | Occurrences integrate with existing work orders, no duplicated state machine | V | `occurrenceLinksOnly...`, `completingAnOccurrence...` (work orders only via existing API) |
| 13 | Technician only with live verified workforce grant/shift | V | `technicianMustBeALiveWorkforceMember...` (roster source itself is dependency 2) |
| 14 | Calibration interval and independent external evidence | V | `calibrationRequiresIndependent...`, `aCalibrationBearingPlanBlocks...` |
| 15 | Manufacturer / serial / service-class validation without sensitive data | V | `assetMetadataIsValidated...`, `assetMetadataAcceptsTechnicalIdentityOnly` |
| 16 | Due / overdue / blocked / escalation projections | V | rules tests, `calendarIsBounded...` |
| 17 | Restart/replay safety and planner lease, no double scheduling | V | lease/fence tests, 8-thread concurrency test (crash simulated by lease expiry, not by killing a process) |
| 18 | Scheduler staleness health check | V | `plannerHealthIs...` (no external alerting wired; M40 untouched) |
| 19 | Bounded RLS-scoped calendar API | V | `calendarIsBounded...`, isolation test |
| 20 | Asset history / audit API with privacy-safe fields | V | `assetHistoryHidesStaffIdentities...` |
| 21 | Pending inspection queue, independent reviewer controls | V | `inspectionQueueListsOnly...`, `theVerifierMustBeIndependent...` |
| 22 | Supplier / engineer evidence references, no fabricated attestation | V | `externalServiceReferencesAreUnattested...` (CHECK `attested=false`) |
| 23 | Fail closed: missing inspection, expired evidence, broken equipment | V | `expiredOrMissingInspectionEvidence...`, calibration tests, retired/uncommissioned asset -> BLOCKED; there is no separate "faulty" state because package 3 forbids inventing one |
| 24 | Administrative repair-cost / currency references, no GL | V | `repairCostsAreAdministrativeReferences...` |
| 25 | M37 contract via outbox only, no second sender | V | escalation test, contract test bans sender code; **producer side only** (dependency 4) |
| 26 | Wire only the five existing screens, EN/FR, no glow | **P** | read-only panel on S01/S03/S05, proxy allow-list, web tests; **no write UI, not rendered in a browser** |
| 27 | Exact OpenAPI contract and parity tooling | V | `test_m23_openapi_contract.py` (routes, permissions, request records, idempotency, `$ref`) |
| 28 | Java 25 / PostgreSQL 18.6 integration tests (RLS, cross-cell, concurrency, idempotency, maker-checker, asset safety) | V | 25 PG tests; module 83/83 locally |
| 29 | Java 27 compatibility, canonical Next build, broader regression, exact CI | **P** | broader module regression passed locally on Java 25; Java 27 and the Next build are verified only by the CI run named in the PR (update there) |
| 30 | Handoff, source maps, residual dependencies, PR with matrix | V | this document and the PR |

**28 verified, 2 partial** (26, 29) at the time of writing; 29 may be re-marked once CI results are attached to the PR.

## 7. What the integration lead should do next
* Review the shared-file edits in section 2 (small, M23-only) and the technician behaviour change.
* Provision the new `m23:*` permissions in the roster/identity contract and decide who owns the planner schedule.
* Decide the M37 consumer for `M23_PM_OVERDUE_ESCALATION`.
* Do not mark M23 production-integrated or clinically accepted from this PR.
