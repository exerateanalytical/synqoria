# SYNQORIA — cross-chat engineering reconciliation (2026-11-04)

> **Coordination record, NOT a clinical certification or software implementation batch.** All workstreams must compare fresh GitHub branch/PR heads and their actual implementation/evidence registers before creating a batch. This record is a snapshot and can become stale.

## Evidence checked

- User-requested project-conversation retrieval: available SYNQORIA coding conversations and previous handoff instructions. This tool does not guarantee access to every separate ChatGPT transcript.
- GitHub repository: https://github.com/exerateanalytical/synqoria
- GitHub `main` at start of audit: `892f309c8802cabd2fdfaa57f3975227ecc8b2c5` (latest commit is a coordination update, not an implementation batch).
- All 9 GitHub pull requests found **open**, none merged, and all branch-head SHAs read from the live repository.
- Main's `registers/phases.json` is **version 0.57.0** and reports P07 as `not_started`: this is stale against the P07 source published in stacked PRs #3 through #9. Do not treat this main-only status as an accurate all-workstream progress count.
- Latest P07 `registers/v660-inbox-operations-batches.json`; latest P06 `docs/program/handoffs/P06_B0372_B0391_v059_PR2.md`.
- Existing SYNQORIA hourly automation prompt includes cross-chat and GitHub publishing rules, but the automation was **disabled** when checked (no automatic runs should be inferred).

## Active implementation workstreams

| Workstream | Verified published extent | Branch / PR | Verified branch head | Next candidate (not reserved) |
| --- | --- | --- | --- | --- |
| P06, M03 triage | P06-B0369..B0391 on top of main's historical P06-B0368 | [PR #2](https://github.com/exerateanalytical/synqoria/pull/2), stacked on [PR #1](https://github.com/exerateanalytical/synqoria/pull/1), `work/triage-safe-command-recovery-v059` | `abd2c64646087a36477f6a55bc1f988ef5552f99` | **P06-B0392** |
| P07, M04–M06 encounter, notes, observations and clinical event inbox/outbox | P07-B0001..B0129 across stacked [PR #3](https://github.com/exerateanalytical/synqoria/pull/3) → [#4](https://github.com/exerateanalytical/synqoria/pull/4) → [#5](https://github.com/exerateanalytical/synqoria/pull/5) → [#6](https://github.com/exerateanalytical/synqoria/pull/6) → [#7](https://github.com/exerateanalytical/synqoria/pull/7) → [#8](https://github.com/exerateanalytical/synqoria/pull/8) → [#9](https://github.com/exerateanalytical/synqoria/pull/9) | [PR #9](https://github.com/exerateanalytical/synqoria/pull/9), `work/longitudinal-inbox-operations-v066` | `002e1fa99b4efa5d867d9c090dfcd3f3b72f0b25` | **P07-B0130** |

These are **per-phase batch IDs**, not mutually interchangeable. A higher number in P06 does not supersede P07, nor vice versa. PRs are unmerged; do not assume all published branch files are already on `main`.

## Correction to previously reported project progress

- **15/200 done and 185/200 remaining** refers only to a specific recent 200-new-batch user request; it is NOT the total SYNQORIA development remaining.
- The 20-phase architecture register on `main` lists P00 internally completed, P01–P06 in progress, and P07–P19 not started, **but this register is stale**: P07 has significant source-level work through B0129 on separate PRs. Therefore **no authoritative, reconciled whole-programme phases-remaining or batches-remaining number is verified**.
- The 40 canonical modules / 261 stable screens remain the authority. **0 clinically accepted screens, 0 certified modules** reported. No verified production PostgreSQL RLS, OIDC/KMS, live Kafka or full Next.js/Expo clinical deployment and acceptance. Formal certification deferred; runtime security and clinical release gates remain enforced.
- Neither other chats' private uncommitted files nor complete transcripts can be inferred from GitHub; conflicts must be explicitly investigated.

## Mandatory protocol before, during and after EVERY batch

1. **Cross-chat preflight:** Search all available SYNQORIA project conversation history and the shared workstream handoffs; fetch current GitHub `main`, **all** branch heads and open PRs, live batch/phase registers, latest cumulative ZIP SHA/checkpoint, and implementation files relevant to the proposed batch. Log the observed source, ref SHAs, branch and tested changes. If any source is missing or differs, label it as unverified and reconcile rather than guess.
2. **Single-writer reservation:** Choose one unique, phase-specific unreserved batch ID. Reserve explicitly in GitHub-visible ledger/handoff before implementation if multiple writers might overlap. A tentative `next candidate` is **not** a reservation.
3. **Real implementation:** Each batch must contain substantive reviewed source changes and an individual repeatable test with recorded actual exit code and evidence. No splitting tests of one unchanged feature into fake batches. No release/certification assumptions.
4. **Push AFTER EACH passed batch:** Commit the code, test, evidence and ledger as a separate Git commit to the correct non-destructive workstream branch; verify branch SHA, commit URL and changed paths via GitHub after every push. Continue only from that verified head. No force-push or overwriting another workstream; use reviewed stacked PRs.
5. **Notify via GitHub:** Update this shared handoff (or a dated successor) with actual batch IDs and verified commits, and post a coordination update on the relevant P06/P07 PR discussion so collaborating coding workstreams can see it. **GitHub comments are not direct messages injected into other ChatGPT chats; no receipt/read confirmation is implied.**
6. **Failure:** If testing, source recovery, remote push or concurrency verification fails, report the failure and preserve a checksum-backed recovery artifact. Never claim the batch was pushed, and never blindly advance past conflicting work. Cert/security/live clinical integration remain independently gated.
7. **Close every run:** State exact completed/pushed/verified counts against the applicable target, what remains unimplemented, current branch and commit, next candidate conditioned on recheck, and unresolved blockers. Keep phase/whole-programme estimates **UNKNOWN** until a reconciled verified work breakdown structure and acceptance criteria are established.

## Coordination messages sent

PRs #2 and #9 should be notified using GitHub PR discussion comments after this handoff is committed. Comments are a shared communication surface, not proof other ChatGPT chats received them.

**Document created for coordination only. New clinical engineering batches in this audit: 0.**
