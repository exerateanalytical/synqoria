# SYNQORIA — Shared Engineering Handoff & Multi-Chat Coordination

**Purpose:** Synchronize every ChatGPT/automation/AI coding workstream in the EMR project. This file is a shared *handoff*, not proof that other chat sessions have read it. Before every engineering run, compare this file with the actual GitHub head, cumulative Library checkpoint, and batch/evidence registers. Never assume this file is newer than those sources.

## Last reconciled checkpoint (checked 2026-10-31)

- **Repository:** `https://github.com/exerateanalytical/synqoria`, branch `main`. This is the shared Git-tracked source authority after successfully verified GitHub publication.
- **Verified cumulative code checkpoint:** `v0.57.0`, Library `/SYNQORIA/SYNQORIA_CURRENT.zip`, full archive SHA-256 `6791e16f2544b9b826d3c7dd18b50b09325a4d04cc8b988a7423ccf91c367c71`; 2,055 entries. The source Git tree published at commit `40d8b6fdfb452cab56cb4b150265e6e9e97bed7b` has SHA `364e677045a805dc54a27b261abf0befdc1b00c7`, covering 1,801 tracked files. GitHub audit commit `d1127f76fcc2e32fc660feec8a90d5d596cd7944` documents recovered-branch differences; do not confuse audit-only commits with new application batches.
- **Latest recorded implementation batch:** `P06-B0368`; **next unreserved candidate** `P06-B0369`. Check *fresh* `main` and registers before assigning this ID; it can become stale immediately when another chat writes.
- **Other identifiable SYNQORIA engineering conversation:** Previously completed `v0.54.0` with `P06-B0229–B0288`. These batches were incorporated into later consolidated checkpoints. **Do not restart at B0289 or duplicate earlier work.**
- **Divergent historical recovery branch:** v0.27.1 is not current authority. It includes unmerged paths and a conflicting `V012` migration; see `docs/program/GITHUB_CONSOLIDATION_2026-10-31.md`. Review and port changes selectively; never merge blindly.
- **Program invariants:** exactly 40 canonical modules M01–M40; 261 stable screens, 12 shared plus 249 module; preserve architecture/UI master documents, module ownership, tenant/facility/patient scope, audited clinical writes, consent, idempotency and outbox guarantees.
- **Verification status:** code/source regressions were run for v0.57.0; **0/261 clinically accepted screens and 0/40 certified modules**. Next.js/Expo production builds, real PostgreSQL/RLS qualification, production identity, and external clinical/security sign-offs are not proven. Formal certification is deferred at user request; runtime safety gates may never be bypassed.

## MANDATORY coordination protocol for every workstream

1. **Read before writing:** inspect the newest GitHub `main` commit/tree, this handoff, `docs/program/PHASES.md`, the canonical batch/phase/feature ledger, the current Library `/SYNQORIA/SYNQORIA_CURRENT.zip` and SHA-256, plus available other-chat work summaries. Determine which source is newer by evidence, not version labels alone. Stop and reconcile if they diverge.
2. **Reserve non-overlapping work:** use a separate branch named `work/<workstream>/<batch-range-or-date>` for parallel changes; never let two chats assume the same batch ID or overwrite each other's files. Check the ledger and recent commits before choosing batch IDs. Reserve ranges in a reviewed shared ledger (commit or PR); if a conflict arises, label work provisional and renumber only when merging with evidence.
3. **Implement and verify:** every counted batch must contain substantive code changes, tests with actual exit codes, changed-file register and honest implementation status. Existing feature tests alone are not proof of new feature implementation. Never invent completion or certification. Keep unsafe clinical actions blocked.
4. **Publish handoff at the end of each run:** record branch, base commit, new commit(s), changed paths, actual executed batch IDs, evidence logs, unresolved blockers, merges and next *unreserved* candidate. Save full cumulative ZIP + SHA-256 in Library if feasible. Update this file (or append a new status file under `docs/program/handoffs/`) so other chats can inspect changes.
5. **Merge safely:** reconcile source and batch ledger with the latest `main` using ordinary fast-forward/PR review; do not force push; do not overwrite or delete unique work. An unrelated Library ZIP must not supersede verified Git history without a file-by-file and manifest reconciliation.
6. **Inform the other chat through the shared record:** after each verified change, publish the handoff link and new branch/commit in the user-visible report. **ChatGPT cannot proactively inject a message into another separate chat with the available connector.** The other chat must read this shared handoff (or the user can paste its URL); do not claim direct delivery without an actual messaging action.
7. **Engineering focus:** target up to 60 meaningful batches per requested run, report actual count out of 60, continue independently unblocked work, and defer formal certification until requested while preserving mandatory safety and release-denied gates.

## Current responsibility and next steps

- Existing published work: v0.57.0 P06 through B0368. Before coding, choose B0369 only if the latest ledger still confirms it is free.
- High-priority candidates: M03 triage production DB/API integration; real scope-and-transaction tests when dependencies are available; web/native staff workflows; patient app flows and safety/UX verification. Separate unblocked work can proceed in parallel with proper branch ownership.
- **This file's creation is a coordination update only: zero new implementation batches.**

## Handoff update template

```
Checked at (UTC):
Chat/workstream:
GitHub base commit / tree:
Library cumulative ZIP SHA-256:
Assigned batch range:
New commit / branch / PR:
Files implemented:
Tests actually run (commands, counts, exit codes):
Migrations and contract changes:
Conflicts / divergence:
Remaining blockers (not certified):
Next unreserved batch candidate:
Other workstream notification: link to this handoff + commit, NOT a claim of direct chat delivery
```

## Active parallel implementation branch (2026-10-31)
- **PR #1**: `work/triage-scoped-workflows-v058`, P06-B0369–B0371 implemented and independently tested; 24 focused tests, UX 224/224.
- Handoff: [`docs/program/handoffs/P06_B0369_B0371_v058_PR1.md`](handoffs/P06_B0369_B0371_v058_PR1.md).
- These IDs are **reserved**. Check PR and the latest shared ledger before assigning P06-B0372 or merging parallel changes. The PR is a draft engineering integration, **not** clinical certification.


## Active parallel implementation branch — v0.59.0 (2026-10-31)

- **Draft PR #2**: https://github.com/exerateanalytical/synqoria/pull/2, branch `work/triage-safe-command-recovery-v059`, stacked on PR #1. GitHub source commit `abd2c64646087a36477f6a55bc1f988ef5552f99` verified.
- **Batch IDs reserved and internally tested**: `P06-B0372` through `P06-B0391` (20 substantial M03 staff workflow areas, 62 separately executed matching tests, UX 286/286 and Java/source/register/status checks passed).
- **Next candidate**: `P06-B0392` after a fresh check of both workstreams. Do not reuse B0372–B0391 or restart at B0369.
- **Handoff**: [P06_B0372_B0391_v059_PR2.md](handoffs/P06_B0372_B0391_v059_PR2.md).
- **Persistent Library**: `/SYNQORIA/SYNQORIA_v0.59.0_P06_B0372_B0391_LOCAL_CUMULATIVE.zip` (SHA-256 `467fb39f0e770eb358faad18a19a6900ed648a750a7a856987dd25e8f806021b`). This local ZIP is the full v0.58.0 **local lineage**; it is **not** a substitute for latest GitHub `main` recovered scheduling files or the full stacked PR tree. Do not overwrite `SYNQORIA_CURRENT.zip` without reconciling GitHub-only changes.
- **Scope**: formal certification deferred, runtime safety gates unchanged, 0/261 accepted screens, 0/40 certified modules; no live PostgreSQL/RLS or production identity approval claimed.


## 2026-10-31 — parallel P07 workstream, **draft PR #3, not merged**

- P06 triage checkpoint PR #2: `work/triage-safe-command-recovery-v059`, head `abd2c64646087a36477f6a55bc1f988ef5552f99`, P06-B0372–B0391. **Next candidate P06-B0392; not reserved by P07.**
- New independent workstream: [draft PR #3](https://github.com/exerateanalytical/synqoria/pull/3), branch `work/clinical-encounter-documentation-v060`, commit `492000765aebfe602fbcfc49fa885f71601be524`, based on PR #2.
- **P07-B0001–B0060 reserved and implemented as 60 distinct internally tested domain operations** across M04 encounter (20), M05 note (20), M06 observation (20). Domain logic is not yet deployed/API-integrated or clinically accepted. Full batch operation mapping in `registers/v600-implementation-batches.json` on PR #3; 60 per-batch Java test logs and 35 adversarial assertions. Canonical 40 modules and 261 screens unchanged.
- Next candidate P07-B0061 for scoped JDBC/HTTP integration. M03 can continue independently from P06-B0392.
- Versioned local-lineage source archive in Library: `/SYNQORIA/SYNQORIA_v0.60.0_P07_60DOMAIN_cumulative.zip`, SHA-256 `c6b4e73cc4f0234ee175d9c87dc17850f0a65dba675c47040a4ec6146b71470a`. Its source base is v0.59.0 local PR #2; it **does not contain all newer main-only recovery/project-memory files**. Do **not** overwrite `/SYNQORIA/SYNQORIA_CURRENT.zip` with it before reconciliation.
- **Merge order:** resolve PR #1 → PR #2 → PR #3, or rebase/reconcile correctly, preserving newer main recovery changes. No force-push, clinical certification or deployed production readiness claimed.
- This shared file is the cross-chat notification mechanism; it does not prove the other conversation has read the update. Any chat must check fresh PR status and ledgers before taking batches.


## 2026-10-31 — P07 v0.61.0 HTTP workstream; draft PR #4 (NOT merged)

- **Draft PR:** https://github.com/exerateanalytical/synqoria/pull/4, branch `work/clinical-http-integration-v061`, commit `ddc924a3911ead1de2a786f88b22e3398752ecbf`; stacked on PR #3 (`492000765aebfe602fbcfc49fa885f71601be524`). **Do not overwrite newer main-only recovery/project memory.**
- **P07-B0061–B0070**: 10 implemented and individually verified M04/M05/M06 engineering integration areas. Authenticated scope/evidence gateway, bounded strict JSON, HTTP encounter/note/observation commands, confidential notes, patient summary and audit/observation read projections, OpenAPI parity and safe TypeScript client. 8 loopback Java scenario checks, 5 OpenAPI checks and 6 TypeScript focused checks; full Java compilation, 60 historical domain tests, 35 adversarial assertions and UX 292/292 passed. Production OIDC, PostgreSQL/RLS, deployed web/mobile and clinical approval NOT verified.
- **Next unreserved P07 candidate:** `P07-B0071` (check branch/ledger again before assigning). Parallel P06 triage work may continue from **P06-B0392**; no overlap.
- **Library checkpoint:** `/SYNQORIA/SYNQORIA_v0.61.0_P07_10BATCH_cumulative.zip`, SHA-256 `0fd5e41a7dec081d9df547d83f36b144a8817a79ae81504950ee2fcbebba259f`; 2,313 archive entries, 2,312 manifest entries checked. The ZIP derives from PR #3 local lineage and is **not a replacement for SYNQORIA_CURRENT.zip** until a full GitHub main reconciliation.
- **Merge order:** reconcile PR #1 → PR #2 → PR #3 → PR #4, or rebase/merge with recorded evidence. Current 40 modules/261 screens unchanged, 0 clinical screens accepted and 0 modules certified; formal certification deferred.
- This GitHub note is the **communication handoff** to other chats, not proof they have read it.

## 2026-10-31 — P07 v0.62.0 secure persistence; draft PR #5 (NOT merged)
- [Draft PR #5](https://github.com/exerateanalytical/synqoria/pull/5), branch `work/longitudinal-secure-persistence-v062`, commit `3fe8289829946a531f6e398a3a8e229d3c8882a7`, stacked on PR #4 head `ddc924a3911ead1de2a786f88b22e3398752ecbf`. This was independently checked on the remote branch (83 changed files). Do not overwrite parallel commits.
- **P07-B0071–B0093**: 23 individually executed, verified engineering implementation slices (requested 100; 77 incomplete). Deliverables: bounded deterministic clinical snapshot codec; AES-256-GCM patient/scope/revision authenticated encryption; scoped JDBC transaction/row locking, persisted audit/receipt/outbox parity and CAS updates; authorized planned encounter provisioning; V020 forced-RLS migration quarantine with no production grants/policies; confidential note-index and bidirectional observation correction HTTP/TypeScript/OpenAPI.
- 23/23 focused checks and 22/22 regression groups passed including 296/296 UX, previous 60 domain checks, 35 adversarial, 245 synthetic JDBC assertions and migration locks. JDBC tests are synthetic, NOT live PostgreSQL/RLS or production KMS/OIDC or clinical certification. V020 does not authorize runtime use. Keep clinical release denied.
- **Next unreserved P07 candidate: P07-B0094**, check fresh branch and ledger before use. Parallel P06 candidate P06-B0392 remains separate. Canonical 40 modules and 261 screens preserved, 0 clinically accepted.
- Cumulative local archive v0.62.0: 2,381 entries, 2,380 verified file hashes; archive SHA-256 `5b38f45007fc8a24fe22d557aecf06fa5982702df59232a93e318c05c4013523`. The versioned Library ZIP upload hit storage limit; user has downloadable archive in this chat. Shared `SYNQORIA_CURRENT.zip` deliberately NOT overwritten.
- Merge source stacked PR1 → PR2 → PR3 → PR4 → PR5, or reconcile/rebase against newer main, preserving recovery and vision. No direct message into other ChatGPT conversations was sent: this GitHub handoff is the shared notification.


## 2026-10-31 — v0.63.0 P07 outbox checkpoint (CODE NOT YET PUSHED)

- **Verified local-only implementation:** P07-B0094–B0104, 11 individual engineering feature checks passed; earlier 100-batch request now totals 34/100 implemented/tested, 66 uncompleted. Modules M04/M05/M06; next unreserved candidate **P07-B0105** (recheck commits and registers before use).
- **Functionality:** privacy-minimal clinical event envelope; scope/expiry dispatcher permits; ordered, fenced single lease per encounter, scoped JDBC claim/CAS ACK/defer, bounded retry, expired-last-lease parking, uncertain ACK at-least-once replay and V021 forced-RLS no-grant schema. No operational broker, live PostgreSQL/RLS, consumer inbox or production identity integration.
- **Evidence:** Java 21 compilation; 11/11 independent batch checks; 17/17 regression groups; UX 296/296; 65 synthetic outbox and 175 synthetic JDBC assertions; migration lock 21 entries. Clinical acceptance remains 0/261 and module certification 0/40; formal certification deferred and release DENIED.
- **Local cumulative ZIP (downloadable in the coding chat, NOT saved to Library due to storage limit):** `SYNQORIA_v0.63.0_P07_11BATCH_cumulative.zip`, SHA-256 `bfdb63e947c4bbabb7b9c2f8d24a03a4d90fcf57bdb50bc8890b3bc41bb32763`; 2,427 entries / 2,426 verified file hashes; preserves all 2,381 previous entries. Does not include newer `main`-only recovery/vision changes.
- **GitHub branch** `work/longitudinal-outbox-v063` was created from draft PR #5 head `3fe8289829946a531f6e398a3a8e229d3c8882a7`. **No v0.63.0 implementation commit or PR exists yet; source publication was blocked by exhausted temporary Library transfer storage. The remote branch is still at its PR #5 base.** Do not claim otherwise. GitHub source authority remains v0.62.0 draft PR #5 for this workstream.
- Parallel P06 next candidate P06-B0392 remains separate. Preserve PR #1→#5 stack and other workstream's newer main commits. Do not overwrite `SYNQORIA_CURRENT.zip` until reconciled.
- This note is a shared handoff record, not a direct notification delivered into the other ChatGPT chat.


## 2026-10-31 — SYNQORIA v0.63.0 pushed; P07 outbox development PR #6 (NOT merged)

- Published **all 57 incremental v0.63.0 source/test/evidence/ledger files** in commit `3caa48e16239907f45374db01f7ade5f4aa98908`, verified Git tree `579395bc40b4a48e8ac941b24501bb75ec87317a` and expected parent `3fe8289829946a531f6e398a3a8e229d3c8882a7`.
- **Draft [PR #6](https://github.com/exerateanalytical/synqoria/pull/6)**, branch `work/longitudinal-outbox-v063`, stacked on draft PR #5 `work/longitudinal-secure-persistence-v062`. This is cumulative Git history: PR #1 → #2 → #3 → #4 → #5 → #6. **Not yet merged into main**; do not claim otherwise, and reconcile newer main changes before merging.
- **P07-B0094–B0104 (11 verified slices)**: bounded ordered clinical event outbox claims; scoped leases and fencing tokens; retry/backoff; idempotent ack, terminal parking and stale-claim recovery; synthetic JDBC protocol, forced-RLS quarantined V021 schema. 11 focused batch checks, 17 regression groups, delivery 65 synthetic assertions, JDBC 175 synthetic assertions passed. No live PostgreSQL, production broker, clinical approvals or formal certification.
- **Next available P07 candidate:** P07-B0105 (verify ledger at execution start). Parallel P06 next candidate P06-B0392 remains untouched. 40 modules and 261 screens unchanged; 0 clinically accepted, 0 certified; release denied.
- **Latest cumulative offline artifact:** `SYNQORIA_v0.63.0_P07_11BATCH_cumulative.zip` downloaded in conversation, SHA-256 available there. Library full ZIP upload was blocked by storage limit; do **not** overwrite shared `SYNQORIA_CURRENT.zip` with a divergent archive.
- This file is the **cross-chat handoff**, not a direct message to a separate ChatGPT conversation. Other workstreams must read the latest GitHub branch and this section.


## 2026-11-01 — Mandatory GitHub push after EVERY verified batch

- **User directive:** Before starting EACH batch, compare latest available other SYNQORIA chats/automation work with fresh GitHub `main`, every active development branch/PR head, `AGENTS.md`, this handoff, canonical batch/phase registers and the latest available cumulative checkpoint. Batch IDs are phase-specific; an older main branch is NOT the latest stacked implementation.
- **GitHub status checked 2026-11-01:** [PR #7](https://github.com/exerateanalytical/synqoria/pull/7), `work/longitudinal-inbox-v064`, head `6f2711e4f230c97ff9637211e063c0c4c33c95af`, contains P07-B0105–B0114, ten source-level unit-tested inbox safety increments; next **candidate P07-B0115**, unreserved subject to a fresh check. Parent PR #6 is P07-B0094–B0104, head `3caa48e16239907f45374db01f7ade5f4aa98908`. Parallel P06 triage PR #2 has P06-B0372–B0391, next **candidate P06-B0392**. Seven draft PRs are open in a non-merged stack. Do not claim v0.64.0 is on main.
- **Publish after EACH batch, not only after a group:** one substantive, independently tested batch per Git commit to its current workstream branch, together with source, tests, evidence and batch ledger. Verify the GitHub remote ref, new commit SHA and changed files before marking it pushed. Update a succinct GitHub handoff and include the commit URL. Reject duplicate IDs and stale source, resolve concurrent commits without force-pushing.
- **Failed publication:** keep source/diff, SHA-256 archive, tests and blockers; mark `NOT PUSHED`, do not invent a successful push, and do not replace latest working source. Release remains denied absent verified real PostgreSQL/RLS, trusted OIDC/KMS, runtime builds and formal clinical approvals.
- **Coordination-only update:** no clinical implementation batch counted here. `AGENTS.md` synchronization commit `d9342a44d5058061eeb8b74fca2f5ff821cae51c`. Hourly automation prompt was updated but the existing task remains **disabled**, and its hourly schedule was not changed.


## 2026-11-01 — P07 v0.65.0 source-level inbox integration; draft PR #8

- Checked other SYNQORIA conversation context, GitHub `main`, seven active draft PRs, prior handoffs and batch registers; latest P07 published before this run was **P07-B0114** on PR #7 (head `6f2711e4f230c97ff9637211e063c0c4c33c95af`). P06 triage last known B0391 on PR #2; next P06 candidate B0392 is a separate stream.
- **Requested 200 new batches in one turn. Actual 5/200 (2.5%) implemented, individually tested, pushed and independently remote-verified. 195/200 NOT DONE.** No synthetic batch padding or unverified work counted.
- New [draft PR #8](https://github.com/exerateanalytical/synqoria/pull/8), `work/longitudinal-inbox-integration-v065`, based on PR #7. Per-batch Git commits: B0115 [`412b4cd2`](https://github.com/exerateanalytical/synqoria/commit/412b4cd2af0bc6f131acefe35e106b24af4dd956), B0116 [`e58d83fc`](https://github.com/exerateanalytical/synqoria/commit/e58d83fcb93ca6509b0e5acada4eb4638d14b412), B0117 [`8401f35e`](https://github.com/exerateanalytical/synqoria/commit/8401f35e0bd3f78b012fbf22494f430c518a0015), B0118 [`2bfecc33`](https://github.com/exerateanalytical/synqoria/commit/2bfecc33ed4d564ed019ff8deedf23a9743a39c9), B0119 [`b61c7140`](https://github.com/exerateanalytical/synqoria/commit/b61c71403428509665b32a5d8db5f0b2e0a172f3). HEAD `b61c71403428509665b32a5d8db5f0b2e0a172f3`. Ten newly published source/test Git blobs matched their local tested Git hashes.
- B0115: V022 forced-RLS, no-role-grant inbox/cursor schema (static SQL tests passed, NOT executed on PostgreSQL). B0116: strict private canonical metadata-only broker frame (10 Java assertions). B0117: transaction-local scoped JDBC context, restricted-role and bypass denial (19 synthetic JDBC assertions). B0118: ACK-after-commit/replay, uncertainty and no-ACK-on-failure (18 fake-broker assertions). B0119: outbox/inbox metadata parity, reject scope/revision/hash/type mismatches (7 assertions with API-compatible local upstream EventRef stub, not full branch compilation). Evidence in `evidence/v650`; range ledger `registers/v650-inbox-integration-batches.json` on PR #8.
- This is intentionally a **stacked draft PR, not merged into `main`**. Latest P07 next *candidate* **P07-B0120**, recheck every branch and chats immediately before assigning. Do not confuse five published batches with 200, or a source/mock test with a live PostgreSQL/Kafka/OIDC integration. Canonical 40 modules / 261 screens unchanged and no clinical acceptance/certification claimed. Production release DENIED.
- For all future batches, check all workstreams before coding, publish EACH verified batch in its own commit, verify remote SHA and file hashes, update handoff. No direct messages were injected into separate ChatGPT chats.


## 2026-11-01 — P07 v0.66.0 operations PR #9 (P07-B0120–B0129)

- [Draft PR #9](https://github.com/exerateanalytical/synqoria/pull/9) `work/longitudinal-inbox-operations-v066`, head `002e1fa99b4efa5d867d9c090dfcd3f3b72f0b25`, stacked on PR #8 head `b61c71403428509665b32a5d8db5f0b2e0a172f3`. NOT merged to `main`.
- P07-B0120–B0129 have **10 independently tested, GitHub-pushed commits**, one per batch, with 20 new Java source/test files, 10 focused logs and 10 batch registers. 57 Java checks passed; inherited extracted v0.61+v0.64+v0.65 harness UX 292/292, inherited inbox and status matrix passed. This does **NOT** establish a full v0.63–v0.66 compilation, live Postgres/Kafka, OIDC/KMS or certification.
- Components: broker record header/digest, timestamp clock window, lease epoch, contiguous offsets, typed failure, backpressure, partition ownership, HMAC quarantine metadata, DB/broker checkpoint reconciliation, runtime readiness.
- Next **P07 candidate B0130** subject to new branch/PR/ledger check; parallel P06 candidate B0392. This run completed 10 of the 195 remaining requested work batches; 185 remain. Historical P07-B0115–B0119 in PR #8 and P07-B0105–B0114 in PR #7 must not be duplicated.
- [Detailed branch handoff](https://github.com/exerateanalytical/synqoria/blob/work/longitudinal-inbox-operations-v066/docs/program/handoffs/P07_B0120_B0129_v066.md). Source delta SHA-256 `585e53d4b710799d9c2101d8a0d22c28be08051371f2bdfc89b195affcf65013`, not cumulative. Do not overwrite `SYNQORIA_CURRENT.zip`. Clinical release DENIED; 0/261 screens accepted, 0/40 modules certified.


## 2026-11-04 — Cross-chat audit and PR notification (authoritative latest audit link)

**Read [the dated reconciliation](handoffs/SYNQORIA_CROSS_CHAT_RECONCILIATION_2026-11-04.md) before allocating another batch.** The project owner identified an invalid assumption that stale `main` register status described all workstreams; this is explicitly corrected in the dated report.

- P06 M03: latest published `P06-B0391`, PR #2 head `abd2c64646087a36477f6a55bc1f988ef5552f99`, next **candidate P06-B0392** (not a reservation).
- P07 M04–M06: latest published `P07-B0129`, PR #9 head `002e1fa99b4efa5d867d9c090dfcd3f3b72f0b25`, next **candidate P07-B0130** (not a reservation). PR #3 → #9 remain **unmerged**.
- Current `main` `registers/phases.json` is v0.57 and incorrectly labels P07 `not_started` in view of branch source. No complete cross-workstream remaining-batch count or reliable phase completion percentage has been established. The previously quoted 185 remaining is limited to the separate 200-batch request.
- GitHub coordination notices have been **posted** to [P06 PR #2](https://github.com/exerateanalytical/synqoria/pull/2#issuecomment-6619722811) and [P07 PR #9](https://github.com/exerateanalytical/synqoria/pull/9#issuecomment-6619723367). These are actual GitHub messages and **not** direct delivery into other ChatGPT conversations.
- Preflight every batch against available project chats, ALL PR/branch heads, current GitHub main, actual files, ledgers and handoff. Reserve a unique phase-specific ID only after reconciliation; individually code, test, push and remotely verify each batch with evidence and SHA. Stop duplicate IDs/overwrites. Update shared handoff after publishing. No clinical-release/certification claims without real evidence.
- This 2026-11-04 update is **coordination only, 0 newly implemented batches**.


## 2026-11-04 — Owner mandates 30 batches at once and stage-ordered delivery

**Supersedes all older 1-, 5-, 10-, 50-, 60- and 100-batch per-run cadence statements** in historical handoffs. Mandatory source of truth: [SYNQORIA_EXECUTION_POLICY_30_BATCHES_2026-11-04.md](SYNQORIA_EXECUTION_POLICY_30_BATCHES_2026-11-04.md).

- For every future engineering run, **assign and attempt 30 substantive, independently implemented/tested batches in one execution**; publish **each** passing batch immediately as a distinct GitHub commit with source, test, evidence and ledger. Do not give 1/5/10 as the normal batch-size response. If all 30 cannot be completed, mark the 30-batch execution *INCOMPLETE* with actual independently tested/pushed counts and blockers. Never fabricate completion.
- After the full planned engineering-batch backlog is demonstrably finished, deliver all **261 canonical screens**; once screens are finished, complete the integration of all **40 canonical modules**; only then begin the **separate comprehensive audit and formal certification programme**. Continuous unit/integration/security/privacy/clinical-safety protection remains mandatory; release stays denied until qualified.
- This is a **four-stage delivery workflow**, not four replacements for the 20 canonical phases P00–P19. The all-platform batch total and remaining phase implementation work are **not yet reconciled**; do not invent estimates.
- Recheck other SYNQORIA chats, current `main`, **all PR and branch heads**, handoffs, actual batch registers and source before assigning any IDs. The latest previous published P06 candidate is `P06-B0392` after B0391, and P07 candidate `P07-B0130` after B0129, **subject to a new concurrency check**. Nine PRs were open on 2026-11-04, not merged.
- Owner asked for a workflow policy update, **not an implementation run**. No new engineering batches counted by these documentation/automation edits.


## 2026-11-04 — P07 30-at-once v0.67.0 published PR #10 (B0130–B0159)

- Verified before work against available SYNQORIA project conversations, latest GitHub `main`, 9 open PRs and all branch heads; latest P07 prior base was [PR #9](https://github.com/exerateanalytical/synqoria/pull/9) SHA `002e1fa99b4efa5d867d9c090dfcd3f3b72f0b25`. Parallel P06 remained at B0391 in PR #2; phase IDs not interchangeable.
- **OWNER'S EXACT 30 AT ONCE achieved for this execution: 30/30 P07-B0130–B0159 substantive Java 21 clinical inbox admission/recovery pure-policy boundaries implemented, individually compiled, tested and GitHub published.** 165 real assertions passed. A combined repeatable runner compiled all new Java classes and ran 30/30 focused test scenarios. No fake batch padding or ID duplication.
- Draft [PR #10](https://github.com/exerateanalytical/synqoria/pull/10) branch `work/longitudinal-inbox-admission-v067`, stacked on PR #9. **All 30 unique per-batch commits remotely verified**; source/test/log/ledger published per batch. GitHub [compare 30 commits](https://github.com/exerateanalytical/synqoria/compare/002e1fa99b4efa5d867d9c090dfcd3f3b72f0b25...e8ed1ce56ca882085f17e23f92b0e10a0789b184) confirms 30 source files, 30 test files, 30 evidence logs and 30 JSON batch registers (plus one reservation document).
- Verified implementation head of batch B0159 `e8ed1ce56ca882085f17e23f92b0e10a0789b184`; subsequent coordination/test-runner commit `b0a1495462f76cc97c57201006bd58ee5e7a1600`. Detailed [v0.67.0 PR handoff](https://github.com/exerateanalytical/synqoria/blob/work/longitudinal-inbox-admission-v067/docs/program/handoffs/P07_B0130_B0159_v067.md), `registers/v067/summary.json`, `tools/check_v067_inboxops.sh`, `evidence/v067/aggregate_tests.log`.
- **GitHub Actions verification:** [v0.67 policy runner workflow](https://github.com/exerateanalytical/synqoria/actions/runs/41755760149) completed successfully on current branch. Broader `engineering-checks` workflow was seen failing in the existing **pinned dependency lock** and TypeScript toolchain installation steps; these remain unresolved independent integration blockers. Do not claim a full stacked app compile or that existing cross-cutting CI passes.
- **Clinical/runtime limitations:** New code is isolated, dependency-free Java policy utilities, **not wired into real Kafka/PostgreSQL, production OIDC/KMS, clinical HTTP, device builds or hospital operations**. Clinical release DENIED; formal certification intentionally deferred. Screens and modules stage must wait for entire core engineering backlog, not merely this completed 30-group. Canonical 40 modules and 261 screens unchanged; 0 clinically accepted screens and 0 certified modules.
- **Next P07 candidate** after fresh cross-chat/branch/ledger check: `P07-B0160`. Parallel P06 candidate remains `P06-B0392` unless another workstream has advanced.
- **Artifact:** `SYNQORIA_v0.67.0_P07_B0130-B0159_DELTA.zip`, SHA-256 `97fb0e01f62fe0eda085fdbb897c8e699ff7d4c08d44dd361f541d4297907f68`; this is an incremental source/test/evidence package, **not** a full cumulative source ZIP or main merge. All GitHub source remains in stacked PRs pending reconciliation and review.
- This handoff update is **coordination only, not an additional batch**. GitHub PR comments will be posted to notify parallel workstreams, without claiming direct delivery to another ChatGPT transcript.


## 2026-11-06 — M21/M22/M23/M25/M29/M31/M37 seven administrative modules (independent branch)

- **Owner branch**: [`work/operations-seven-module-service-2026-11-06`](https://github.com/exerateanalytical/synqoria/tree/work/operations-seven-module-service-2026-11-06), based on verified `main` SHA `96d45960e2b7ad0039818de259b2782d2cbca86f`, not atop unrelated P03/P06/P07 branches. Do not duplicate IDs or overwrite PR #16 document forms, PR #23/#24 build candidate, PR #30 shared auth routes, PR #33 identity service or existing 261 canonical screen workflows. This GitHub handoff coordinates via shared records, not direct chat messaging.
- **Scope**: exactly **7 modules, 35 canonical screens**: M21 6; M22 5; M23 5; M25 5; M29 4; M31 6; M37 4. New Java21 Spring Boot 4.1.2 service includes seven source-level lifecycle models and maker-checker/bounded evidence gates, least-privilege PostgreSQL16 FORCE RLS, atomic JDBC idempotency/aggregate/audit/outbox, OIDC JWT bearer contract with issuer/audience check, **default-deny actual workforce admission**, M22 bounded stock arithmetic and 35 accessible *LOCKED PREVIEW* pages.
- **Engineering verified**: [Java21 test run 42303727918](https://github.com/exerateanalytical/synqoria/actions/runs/42303727918), [real disposable PG16 RLS 42303727902](https://github.com/exerateanalytical/synqoria/actions/runs/42303727902), [real JDBC end-to-end 42303727956](https://github.com/exerateanalytical/synqoria/actions/runs/42303727956), [canonical 35-screen source test 42303727932](https://github.com/exerateanalytical/synqoria/actions/runs/42303727932). Check newer final-head CI when merging; root/default engineering workflow still fails for pre-existing build-owner lock/Expo integration.
- **Not done**: clinical/workforce grant/shift revocation source not deployed, source of supplier/item/move evidence not integrated, actual Next business workstations only static locked previews, actual M31 signature/object store not connected, M37 notifications never dispatched. **0/7 full modules completed and 0/35 screens accepted**. 261-screen production stage NOT AUTHORIZED. **Clinical release DENIED**. See `registers/operations-seven-delivery-2026-11-06.json` and dedicated plan for per-module detailed gaps.
- **Next owner**: merge reviewed API/UX security and data integrations, wire actual stock movements and supplier/asset/dispatch records, replace preview screens with verified authenticated real workflows, then live cell and operational safety acceptance. Do not count domain state-machine tests as full module delivery.


### 2026-11-06 final 7-module *source foundation* build evidence

- At commit `820c57bf4a7d2bf897814cb59d89dde92c3cef35` source engineering checks: [Java21 Maven 42305322201](https://github.com/exerateanalytical/synqoria/actions/runs/42305322201) **SUCCESS**, [actual PostgreSQL16 FORCE RLS 42305322027](https://github.com/exerateanalytical/synqoria/actions/runs/42305322027) **SUCCESS**, [real JDBC transactions 42305324162](https://github.com/exerateanalytical/synqoria/actions/runs/42305324162) **SUCCESS**, [35 screen mapping and acceptance-denied ledger 42305322265](https://github.com/exerateanalytical/synqoria/actions/runs/42305322265) **SUCCESS**, and [actual Next15.5.9/React19.1.2, 35 page generation 42305322016](https://github.com/exerateanalytical/synqoria/actions/runs/42305322016) **SUCCESS with PR #23/#24 temporary CI dependency overlay**. The normal root engineering CI is **FAILURE** because review/merges are outstanding; no mainline acceptance.
- The dedicated Spring Boot/JDBC service is STARTABLE after operators provision isolated database/IdP secrets, but live business actions are intentionally **DENIED by default** until trusted workforce roster evidence and domain-specific service adapters. All 35 UI pages remain **LOCKED PREVIEW**. The 7 modules are **0/7 fully production completed**, operational screens **0/35 accepted**, clinical release **DENIED**. Never convert passing source/Next CI into a finished-module claim.


## 2026-11-06 — Latest stable GA/compatible stack policy, new draft PR #35

- Reviewed parent source branch [PR #34](https://github.com/exerateanalytical/synqoria/pull/34); independent stacked [PR #35](https://github.com/exerateanalytical/synqoria/pull/35) branch `work/latest-stable-stack-governance-2026-11-06`, current tested parent commit `26036d8cdd50bb66af221d6d3c401d43059e2445`. No other owner branch was overwritten; PR #23/24 package source/lock owner unchanged, P03 #33 auth ownership and P07 event contracts preserved. **Stable release policy** `docs/authority/SYNQORIA_STABLE_RELEASE_POLICY_2026-11-06.md`; **21-component target/version/status registry** `registers/stack-stable-2026-11-06.json`.
- Latest stable Java GA is **27** (non-LTS), latest LTS Java is **25**. **Java25 is selected production bytecode**, **Java27 separately CI-qualified**. Spring Boot **4.1.2 GA** is officially compatible through Java27. [Actual dual JDK Maven matrix PASS 42307201716](https://github.com/exerateanalytical/synqoria/actions/runs/42307201716).
- PostgreSQL major **18.6** replaces PG16 in dedicated disposable migration/RLS/JDBC tests; [forced-RLS/denials PASS 42307201820](https://github.com/exerateanalytical/synqoria/actions/runs/42307201820) and [real PG18.6 JDBC idempotency/outbox PASS 42307201701](https://github.com/exerateanalytical/synqoria/actions/runs/42307201701). This does not migrate live hospital cells. Node26 Active LTS is CI baseline.
- **Web latest stable candidate** Next **16.4.5** + React/React DOM **19.3.1** + Node26 compiled actual operations web and UX [PASS 42307201738](https://github.com/exerateanalytical/synqoria/actions/runs/42307201738). **Native latest stable candidate** Expo SDK **58.0.23**, React Native **0.88.1**, Expo-matched React **19.3.0** compiled actual staff AND patient Android bundle exports [PASS 42307201740](https://github.com/exerateanalytical/synqoria/actions/runs/42307201740). Both are **temporary runner overlays** until PR23/24 owners reconcile reviewed package-lock, iOS/device, production UX and clinical acceptance. This success does **not** mean source package.json on main was updated.
- Pending independent upgrade/qualification: Keycloak26.8.2, Kafka4.4.0, Debezium3.7.1, Redis8.10.3, Kubernetes1.37.1, Prometheus3.16.0, OpenSearch3.9.0. Grafana exact patch and Kong exact stable unverified; do not invent. No deployment, no live Keycloak, no clinical release, **0/7 completed admin modules and 0/35 accepted admin screens**, 40 modules/261 canonical screens global backlog OPEN. **Root engineering check remains red** pending lock/reconciliation; clinical release DENIED. GitHub handoff cannot directly send messages to other chat threads.

## 2026-11-07 — PR #36 seven-module transactional workstreams and 35 scoped screen flows

- **Authoritative new stacked PR** [#36](https://github.com/exerateanalytical/synqoria/pull/36), branch `work/seven-operations-real-workflows-2026-11-06`, based on PR #35 Java25/PG18.6 stack. Preserves PR #34 previous foundations, PR #33 identity ownership, PR #23/#24 dependency/Expo, PR #16 document forms, P07 events; do not cherry-pick unrelated branch heads or rewrite them.
- **New source**: V002 independently managed shift-bound/revocable workforce roster + per-action/version/reference verified evidence, V003 exact stock journal identity, V004 six module-specific transactional PostgreSQL schemas; **14 FORCED RLS tables**. `OperationsDomainEffects` enforces qualified suppliers, committed purchase orders/invoice receipts, checked maintenance, exclusive ambulances, controlled facility publications, append-only signed document-version metadata and notification queue/recipient acknowledgement. `OperationsStore` scoped transaction covers aggregate state, domain effect, idempotency, audit and outbox. Seven domain read models exposed through same RBAC/row scoped API and CSRF/origin allowlisted web BFF.
- **Web**: 35 canonical URLs now have `OperationsWorkbench` interactive create/select/edit/transition/stock operations, task-specific field/action/record type definitions in `apps/web/lib/operations-screen-flows.json`, live record status and domain history. OIDC PKCE start/callback stores access token in Secure HttpOnly cookie; production hospital IdP, independently provisioned grant and external evidence authority remain REQUIRED. Thus app always denies actual hospital operations if integration absent.
- **Verified synthetic CI evidence on preceding source**: [real PG18.6 + multi-domain JDBC E2E run 42313396169](https://github.com/exerateanalytical/synqoria/actions/runs/42313396169) SUCCESS; [Java25/27 42313396065](https://github.com/exerateanalytical/synqoria/actions/runs/42313396065) SUCCESS; [Next16.4/React19.3 build 42313396175](https://github.com/exerateanalytical/synqoria/actions/runs/42313396175) SUCCESS; [35 route contracts 42313396149](https://github.com/exerateanalytical/synqoria/actions/runs/42313396149) SUCCESS; final-head CI must be rechecked.
- **Not done/operational blockers**: live hospital IdP/workforce roster issuer, consent and clinical handover; ERP/GL procurement adapters; live biomedical safety signoff; offline publication; real legally valid signatures and immutable encrypted document store; real SMS/email/push broker delivery workers; Android/iOS device accessibility and privacy/security/regulatory acceptance. **0/7 modules accepted for hospital production; 0/35 accepted workstations; clinical release DENIED; root broad engineering CI remains RED** pending independently owned package/lock merge. Source workflow implementation and synthetic CI DO NOT mean operational readiness. All remaining gaps are updated in `registers/operations-seven-delivery-2026-11-06.json`.
