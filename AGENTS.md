# SYNQORIA cross-workstream instructions

This repository is developed in multiple ChatGPT and engineering conversations. **Before any code changes, read [docs/program/SYNQORIA_MULTI_CHAT_HANDOFF.md](docs/program/SYNQORIA_MULTI_CHAT_HANDOFF.md)**, [docs/program/PHASES.md](docs/program/PHASES.md), and the canonical source/batch/screen registers.

**Product memory:** Before planning or changing product scope, clinical workflows, stack, brand, or UX, also read [docs/program/SYNQORIA_PROJECT_MEMORY_AND_VISION.md](docs/program/SYNQORIA_PROJECT_MEMORY_AND_VISION.md). It preserves the wider OPES digital-health vision and established decisions; authoritative architecture v2.1, UI/UX v1.1, live code and actual evidence take precedence over memory.\n
1. Fetch and verify the newest `main` commit, check the Library `/SYNQORIA/SYNQORIA_CURRENT.zip` against its SHA-256, and compare the other workstream's checkpoint before selecting the next work. The handoff file can be stale.
2. Never overwrite work from another chat, duplicate a batch ID, or overwrite the canonical cumulative archive without reconciling independent changes.
3. Keep development branches separate; record base commit, implemented batches, changed paths, real test evidence and next unreserved batch in the shared handoff on every execution.
4. Exactly **40 canonical modules and 261 screens**; preserve authorized clinical navigation, safety and patient/tenant scoping. Formal certification is deferred but safety controls must not be bypassed.
5. Target up to **60 meaningful, individually verified engineering batches** when requested; report the true count. An unrun or failing test is not a pass.
6. Publish GitHub commit/PR links, Library checkpoint SHA, and a readable handoff after each run so other workstreams can synchronize. No automated direct messaging to other ChatGPT chats is assumed.

Do not claim GitHub pushes, release qualification, clinical acceptance, or cross-chat notifications without a confirmed action.
