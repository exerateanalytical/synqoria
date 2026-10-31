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
