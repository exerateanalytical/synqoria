# Mandatory integration and no-duplication policy (2026-11-07)
This is the SYNQORIA **source-of-truth** integration policy for ChatGPT, Claude, Codex, Copilot and local contributors.
Its authoritative production-code baseline is `main` once the reconciliation PR merges. Do not use a private chat as a substitute for live repository evidence.

1. Before **every** new workstream, fetch current `origin/main`, open PRs, integration branches, existing service paths, canonical 40-module and 261-screen registers, relevant API contracts, migrations, tests and `registers/production-source-ledger-2026-11-07.json`.
2. Determine exact code owner and existing implementation with `git grep` and branch/commit comparison. Never regenerate already built screens, modules, forms, printables, APIs, migrations, translations, adapters or workflows. Continue existing code with surgical edits, or clearly document why replacement is necessary.
3. The **only** canonical workstation URL is `/workforce/clinical/<SCREEN_ID>`; legacy module URLs must redirect. Never create duplicate screens. Existing screens are extended through authorized domain APIs.
4. Use branches for concurrent work, but do not leave built code perpetually in isolated PR stacks. Reconcile, test, merge and update main after each verified source change, unless it would break existing runtime security/integrity. Do not merge incompatible or unreviewed code silently.
5. Claude/ChatGPT/Codex handoffs must record current branch, full commit SHA, exact changed files, module IDs, per-screen IDs, CI run, unresolved authority gaps, and any migration impacts in the repository. A chat-to-chat message is not evidence that a different model received the handoff.
6. Distinguish **source-integrated** from **institutionally deployed, operationally accepted, and certified**. Certification is a later phase; no software build, synthetic database test, pull request merge or screen render alone constitutes legal/clinical authorization. Never bypass RBAC, OIDC, patient consent, tenant isolation, audit/outbox, data integrity or patient-safety gates to accelerate integration.
7. On conflicting versions, resolve explicitly against the authoritative architecture, latest verified code and regression tests; preserve historical commits and record the chosen version. No force push or deletion of a collaborator's work.

---
# SYNQORIA cross-workstream instructions

This repository is developed in multiple ChatGPT and engineering conversations. **Before any code changes, read [docs/program/SYNQORIA_MULTI_CHAT_HANDOFF.md](docs/program/SYNQORIA_MULTI_CHAT_HANDOFF.md)**, [docs/program/PHASES.md](docs/program/PHASES.md), and the canonical source/batch/screen registers.

**Product memory:** Before planning or changing product scope, clinical workflows, stack, brand, or UX, also read [docs/program/SYNQORIA_PROJECT_MEMORY_AND_VISION.md](docs/program/SYNQORIA_PROJECT_MEMORY_AND_VISION.md). It preserves the wider OPES digital-health vision and established decisions; authoritative architecture v2.1, UI/UX v1.1, live code and actual evidence take precedence over memory.

1. Fetch and verify the newest `main` commit, check the Library `/SYNQORIA/SYNQORIA_CURRENT.zip` against its SHA-256, and compare the other workstream's checkpoint before selecting the next work. The handoff file can be stale.
2. Never overwrite work from another chat, duplicate a batch ID, or overwrite the canonical cumulative archive without reconciling independent changes.
3. Keep development branches separate; record base commit, implemented batches, changed paths, real test evidence and next unreserved batch in the shared handoff on every execution.
4. Exactly **40 canonical modules and 261 screens**; preserve authorized clinical navigation, safety and patient/tenant scoping. Formal certification is deferred but safety controls must not be bypassed.
5. Target up to **60 meaningful, individually verified engineering batches** when requested; report the true count. An unrun or failing test is not a pass.
6. Publish GitHub commit/PR links, Library checkpoint SHA, and a readable handoff after each run so other workstreams can synchronize. No automated direct messaging to other ChatGPT chats is assumed.

Do not claim GitHub pushes, release qualification, clinical acceptance, or cross-chat notifications without a confirmed action.
