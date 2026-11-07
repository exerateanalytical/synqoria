# M01-M37 forensic source reconciliation - report (Issue #60)

**Baseline:** `main` at `fc4f84eb60e7ab7a023895eabfd8266cbda309c5` (902 commits, 3,104 tracked files, 64 remote branches, 61 PRs: 17 merged, 43 closed, 1 open).
**Nature of this work:** a read-only, machine-checked *inventory*. It does not modify production modules, screens, `registers/engineering-ownership.json` or any M38/M39/M40/P03 work. It is **not** acceptance, production integration, hospital acceptance or certification, and it is **not** a human audit of each module.

## Executive summary (exact, evidence-bound)
| Question | Answer | Evidence |
|---|---|---|
| Modules / screens inventoried | 40 modules, 261 screens, each exactly once | `canonical-inventory.json`, validator |
| Confirmed unbuilt modules | **null** - not determinable | no module has had a definitive human audit; the rule is enforced by the validator |
| Confirmed fully built modules | **null** | same |
| Modules with an attributed engineering slice (service code + migrations + OpenAPI contract + service tests) | 18: M01-M06, M16, M24, M26-M28, M30, M32, M34, M35, M38-M40 | `module-inventory.json` |
| Modules with partial attributed source | 8: M21, M22, M23, M25, M29, M31, M33, M37 | no OpenAPI contract is attributed to any of them (operations-core publishes a contract only for M27) |
| Modules where **no** source could be attributed (status **UNKNOWN, not unbuilt**) | 14: M07-M15, M17-M20, M36 | needs human audit; backlog `BL-001`, `BL-002` |
| Screens by evidence state | 117 referenced by implementation source, 5 by catalog/test only, 127 route-registered only | `screen-coverage.json` |
| Duplicate screen ids / routes | 0 / 0; every route resolves to the shared dynamic page, no duplicate static entrypoint | validator |
| Register drift | 17 screens (M01-S02, M38-S01..S05, M39-S01..S05, M40-S01..S06) are marked `route_registered_domain_adapter_missing` while implementation source references them | `BL-003`, owner: integration lead |
| Not proven | runtime behaviour, RLS effectiveness, token/permission enforcement, event delivery, hospital acceptance | out of scope of an inventory |

Attributions for M01-M06 and M33 rely on curated anchors (`registers/reconciliation/inputs/capability-anchors.json`) because the clinical-core code is named by capability (`registration`, `triage`, `longitudinal`, `security`), not by module id. M04, M05 and M06 share one longitudinal slice; per-module attribution is **not verified**. Those slices are explicitly *quarantined* in their own migration names.

## Classification requested by the integration lead
Every module row carries `classification`. Automation produces only three of the six classes: **implemented_requiring_integration** 17 (M01, M02, M04-M06, M16, M24, M26-M28, M30, M32, M34, M35, M38-M40: service+DB+contract+tests+web+CI attributed, none integrated or accepted), **partially_implemented** 9 (M03, which has no CI workflow for its tests, plus M21-M23, M25, M29, M31, M33, M37), **unknown_insufficient_evidence** 14 (M07-M15, M17-M20, M36). **implemented_and_verified**, **designed_not_implemented** and **confirmed_not_implemented** are assigned to **no** module: they require a signed human review, and the validator rejects them without one. So no module is positively confirmed unbuilt, and none "needs no further implementation" yet.

Precondition note: the assignment says to start after PR #56 is merged; #56 is still open, so this baseline (`fc4f84e`) predates it. Re-run the scanner after #56 merges.

## How to regenerate and check
```
python tools/reconciliation/forensic_scan.py --baseline <sha>      # offline, deterministic, ~100 s
python tools/reconciliation/validate_reconciliation.py --rescan     # structural + evidence + byte-identical regeneration
python tools/reconciliation/render_matrix.py                        # regenerates MODULE_EVIDENCE_MATRIX.md
python -m unittest discover -s tests/reconciliation -v              # 28 tests, 19 of them negative
```
Online facts (PR list, workflow runs) are pinned snapshots in `registers/reconciliation/inputs/`; the scanner never uses the network and writes no timestamps.

## 30-package verification matrix
Status vocabulary: **verified** = produced by the tool, checked by the validator and tests; **partial** = produced but limited as stated. Nothing is "complete" beyond what is stated.

| # | Scope | Output | Status | Remaining risk |
|---|---|---|---|---|
| 01 | Immutable manifest | `manifest.json`: baseline SHA, every PR (head SHA, merge commit, reachability), 64 branches, pinned input hashes | verified | PR snapshot is a point-in-time capture |
| 02 | Canonical 40 modules / 261 screens | `canonical-inventory.json` (blob SHAs of the three registers) | verified | |
| 03 | Historical sources and archives | `historical-inventory.json` (archives, status checkpoints, handoffs, batch registers, recovery sources, PR lineage) | partial | `evidence/seed/SYNQORIA_Baseline_v0.1.0.zip` not unpacked; worktrees, unpushed branches and PR review comments inaccessible |
| 04 | Path/class/API/SQL provenance | `provenance.json` (per service Java files, HTTP entrypoints, migrations, tables; OpenAPI paths/operations) | verified | structural counts only |
| 05 | Duplicate fingerprint method | `duplicate-fingerprints.json`: 3 proven byte-identical groups; 5 table-name, 1 class-name, 4 OpenAPI-operation collision **candidates** | partial | candidates are not proven duplication; none were changed |
| 06 | M01-M03 | `module-evidence.json` rows M01, M02, M03 | partial | M01/M03 via anchors, quarantined; M03 has no CI workflow running its Java tests |
| 07 | M04-M06 | rows M04-M06 | partial | one shared longitudinal slice, attribution unverified |
| 08-10 | M01..M07 split as required | rows M01-M07 (M07: no source attributed, UNKNOWN) | partial | automated attribution, not human review |
| 11-15 | M08-M17 in five pairs | rows M08-M17; only M16 has source; M08-M15 and M17 UNKNOWN | partial | UNKNOWN is not unbuilt |
| 16-20 | M18-M27 in five pairs | rows M18-M27; M24/M26/M27 have slices; M21-M23/M25 share operations-core; M18-M20 UNKNOWN. M24/M26/M27 shared-shell and ux-core dependencies are integration-lead scope | partial | web screens of M21-M25 are a shared catalogue workbench, not module-specific code |
| 21-25 | M28-M37 in five pairs | rows M28-M37; M28/M30/M32/M34/M35 slices preserved with their own services and docs, no double counting; M36 UNKNOWN | partial | |
| 26 | Screens: no duplicate entrypoint; forms/reports/templates separately | `screen-coverage.json`: 261/261 resolve to the shared dynamic page; 0 duplicate static entrypoints; 304 document templates and 376 printable entries counted separately (5 ids, P01-P05, overlap between the two catalogs) | verified | 88 screens are referenced from more than one source family (catalog + workbench); candidates only |
| 27 | Events, outbox/inbox, OIDC permissions, RLS, cells | `contracts-events-security.json` (per service: FORCE RLS/policy counts, cell/facility GUC use, outbox/inbox/audit tables, permission strings) | partial | text inventory; nothing executed; clinical-core does not use the `synqoria.cell` GUC pattern (unverified whether an equivalent exists) |
| 28 | 40-row inventory | `module-inventory.json`; M38-M40 carried as claimed/active (PR #50/#51/#52/#56) with no change to their implementation | verified | |
| 29 | Validator + negative tests | `validate_reconciliation.py`, `tests/reconciliation/test_reconciliation.py` (28 tests, 19 negative, byte-identical rescan) | verified | |
| 30 | Ranked backlog + executive report | `backlog.json` (15 items, 12 confirmed, 3 audit/candidate), this report; confirmed-unbuilt count left **null** | verified | |

## Ranked backlog (summary; full records in `backlog.json`)
1. **M03** - no CI workflow executes the triage Java tests (only offline evidence batches). Owner: M03 owner. Acceptance: workflow green.
2. **M29, M31, M33, M37, M21-M23, M25** - no OpenAPI contract attributed (operations-core publishes only M27's). Owner: operations-core owner; the contract must describe the *existing* endpoints, not new ones.
3. **Human audit of 14 UNKNOWN modules** (M07-M15, M17-M20, M36) - audit work, *not* a build order. A module may only be declared absent after a signed human finding.
4. **Screen register drift** for 17 screens - integration lead (owns `registers/screen-routes.json`).
5. `services/identity` has no outbox table; byte-identical files and table-name collisions need review before any consolidation.

## Limits (read before using these numbers)
* Attribution is by module id in paths, service ownership, and curated anchors. Source named only by capability and not anchored is **not seen**; that is why 14 modules are UNKNOWN rather than "missing".
* "Latest mapped CI" is the most recent run of the mapped workflow on any branch; 33 of 47 workflows' latest runs were failures, many on historical or quarantined branches. Per-module run URLs and head SHAs are in the evidence; they are not claimed to be at the baseline SHA and are not a pass/fail verdict on the module.
* No percentage is produced anywhere (the validator rejects non-null percentage fields).
