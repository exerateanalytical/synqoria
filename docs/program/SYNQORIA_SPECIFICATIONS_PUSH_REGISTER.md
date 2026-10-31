# SYNQORIA — Specifications, Project Memory & GitHub Publication Register

**Version:** 1.0  
**Reconciled:** 31 October 2026 (Africa/Douala)  
**Repository:** https://github.com/exerateanalytical/synqoria

## Purpose and scope

This register identifies the available authoritative SYNQORIA specifications, strategic project memory, engineering coordination documents, and the separately published parallel implementation branches. It lets every coding chat find the same requirements without modifying the agreed technology stack. A GitHub-verified file is marked **published**; an open PR means **remote branch published, not merged**. It is not a certification or a statement that every historic chat transcript has been recovered.

## A. Authoritative specifications on `main`

| Priority | Artifact | GitHub path | Verified publication / use |
|---|---|---|---|
| 1 | Platform architecture and technical specifications **v2.1** | `docs/authority/SYNQORIA_Platform_Architecture_v2.1.md` | Published; authoritative cell architecture, backend/frontend stack, data, safety, interoperability and 40-module contract |
| 2 | UI/UX Master Specification **v1.1** | `docs/authority/SYNQORIA_UIUX_Master_v1.1.md` | Published; 261 canonical screens, module flows, Warm Clinical tokens, layouts, navigation, localization, accessibility and mobile design |
| 3 | Project Memory, Vision & Engineering Constitution **v1.1** | `docs/program/SYNQORIA_PROJECT_MEMORY_AND_VISION.md` | Published; synthesized OPES digital-health vision, product boundaries, engineering rules and explicit Work-mode-transcript coverage limitation |
| 4 | Canonical engineering phases | `docs/program/PHASES.md` | Published; phases P00–P19, scope and release gates |
| 5 | Multi-chat engineering handoff | `docs/program/SYNQORIA_MULTI_CHAT_HANDOFF.md` | Published; latest branch/PR reconciliation, conflict-safe workflow, batch reservations and limitations |
| 6 | Cross-chat coding-agent instructions | `AGENTS.md` | Published; mandates reading authoritative specifications, memory and fresh handoff |
| 7 | Navigation/visual quality gates | `docs/program/NAVIGATION_AND_VISUAL_GATES.md` | Published; concrete layout/navigation safety checks |
| 8 | Staff/patient mobile contract | `docs/program/MOBILE_APP_CONTRACT.md` | Published; cross-platform mobile constraints |
| 9 | Scheduling transaction contract | `docs/program/M02_SCHEDULING_TRANSACTION_CONTRACT.md` | Published; implementation-level M02 contract |
| 10 | Batch evidence protocol | `docs/program/BATCH_PROTOCOL.md` | Published; what constitutes a distinct verified batch |

The canonical module/screen registries, test evidence, migrations, OpenAPI and code must be read from the **fresh checked branch** rather than this historic snapshot. Published plans are not an implementation acceptance certificate.

## B. Canonical architecture preserved — do not substitute older OPES implementations

- **Product:** SYNQORIA — *Africa medical intelligence synchronized*.
- **Delivery topology:** independent per-hospital/group cells; modular transactional Java/Spring Boot clinical core and carefully selected independent domain services; API-first/DDD/event-driven design, selective CQRS.
- **Data and integration:** PostgreSQL authoritative state, scoped SQL tenant and patient access checks, durable idempotency and outbox/inbox; Kafka, Debezium, Redis caching only; versioned OpenAPI/FHIR/HL7 integrations where partner approved.
- **Clients:** Next.js/React/TypeScript web and Expo/React Native staff/patient apps; EN/FR; fully specified, accessible screens.
- **Operations:** Kong/equivalent API gateway, OIDC/MFA, encryption, per-cell continuity and recovery, observability, containers/Kubernetes when justified.
- **Deferred choices:** Go query services require profiling; separate Python CDSS or AI is future clinically governed work, never a silent stack or safety replacement.
- **Exact baseline:** 40 modules M01–M40 (28 clinical/business + 12 platform/governance); 261 unique screens (12 shared + 249 module); 20 phases. Neither archived OPES Triage screens nor prototype component counts change these totals.
- **Design authority:** Warm Clinical canvas `#F7F5F0`, white work surfaces `#FFFFFF`, navy `#073656`, primary `#1664D4`, Inter, Lucide. No glow, colour collisions, stretching, clipping, cramping, squeezing or hidden critical medical details.

## C. Remote code branches already published but not on `main`

Read the active pull requests in order; their head and approval status can change at any time:

| PR | Branch | Purpose | State at reconciliation |
|---|---|---|---|
| [#1](https://github.com/exerateanalytical/synqoria/pull/1) | `work/triage-scoped-workflows-v058` | P06-B0369–B0371; M03 triage foundations | Open; head `b7aeba143f4da105ce8c728faffdbeda160f4d63` |
| [#2](https://github.com/exerateanalytical/synqoria/pull/2) | `work/triage-safe-command-recovery-v059` | P06-B0372–B0391; M03 integration | Open, based on PR #1; head `abd2c64646087a36477f6a55bc1f988ef5552f99` |
| [#3](https://github.com/exerateanalytical/synqoria/pull/3) | `work/clinical-encounter-documentation-v060` | P07-B0001–B0060; M04/M05/M06 clinical domains | Open, based on PR #2; head `492000765aebfe602fbcfc49fa885f71601be524` |

**Do not claim these PR changes are merged into `main`.** Reconcile and review in stacked order before merging. Do not force push. A Library versioned ZIP is not proof its contents have been merged into GitHub's default branch.

The shared handoff records the v0.60.0 cumulative ZIP path and hash; it explicitly warns that the local-lineage archive omits newer main-only changes. Preserve unique changes from both lines.

## D. Original chats and related OPES digital-health materials

| Source group | What was captured | Status / next evidence required |
|---|---|---|
| Original “Assess EMR HMS Architecture” specification chat | Canonical architecture v2.1, 40 modules, workload goals, data/safety/design gaps | Reconciled through directly verified GitHub architecture and UI/UX source files |
| “Plan Autonomous Build Phases” and “SYNQORIA Hourly Engineering” | P00–P19, web + mobile, verified batches, handoff and multiple coding chats | Reconciled against project memory and the fresh shared engineering handoff |
| **Separate standalone Work mode specifications chat** | Potential additional requirements | **Not independently retrievable in full. Do not claim exhaustive transcript import.** Obtain an actual transcript, artifact or commit, create a requirement-to-source crosswalk, review conflicts and then update approved specifications |
| Earlier OPES EMR/Triage/OPESCARE/LIS/RADIS/Pharmacy/CDSS and National Health Watch work | Portfolio vision, clinical process context, hybrid hospital operations and regulated interoperability | Captured as contextual product goals in Project Memory v1.1; **separate products/source trees are not silently merged into SYNQORIA** |

## E. Publish and safety acceptance rules

1. Start with newest `main`, then the current open PRs, versioned Library checkpoint, handoff, and batch/feature/screen registers.
2. Count only meaningful implemented changes with executed tests and linked evidence; never convert 60 test scenarios into a claim of 60 clinical modules.
3. Preserve the approved architecture and UI design. Proposed changes to canonical scope or stack require a documented ADR, compatibility assessment, and explicit acceptance.
4. Keep clinical writes fail-closed until real authorization, PostgreSQL/RLS, infrastructure, device, security and clinical safety gates have independently passed.
5. Publish branch, commit/PR URL, changed paths, evidence, unresolved blockers and fresh handoff each execution.
6. Historic OPES products are integration candidates, not transferable code or approved clinical rule content merely because their names recur in the vision.

**Publication statement (31 Oct 2026):** All **identified authoritative SYNQORIA specification and memory documents above** were already present on GitHub `main`; all three **identified implementation PRs** were already present on remote branches. This register is the new explicit cross-reference. It does **not** certify that every document across the account or every Work mode transcript has been recovered, nor does it merge those PRs or claim production readiness.
