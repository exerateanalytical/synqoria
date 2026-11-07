# ADR-M38: Offline synchronization and ownership reconciliation

Status: proposed (engineering candidate, 2026-11-07).

## Context
Hospital cells can lose connectivity. Devices keep working only for operations that a domain owner has explicitly approved for offline use. When they reconnect, their changes must reach the authoritative record without silently overwriting newer clinical data, and a record must never have two authoritative owners.

## Decisions
1. **The sync service owns the envelope, not the data.** It stores devices, immutable command envelopes, conflicts, ownership epochs and reconciliation decisions. Clinical truth stays in the owning domain; accepted commands are dispatched through the transactional outbox.
2. **Operation contracts decide what may be offline.** A contract is created by one person and approved by another. An operation that requires a clinical safety gate can never be offline (database CHECK). No contract means rejection.
3. **Optimistic versions, never last-writer-wins.** A command carries the base version and epoch it saw. Equal to the head → applied. Older → conflict, unless the contract is mergeable *and* the changed field sets are disjoint. Overlap or a non-mergeable contract → conflict for human review.
4. **Ownership epochs are monotonic.** An owner change requires an independently approved transfer with positive fencing evidence (the source confirms its last version, equal to the authoritative head) and raises the epoch by exactly one. A trigger enforces monotonicity; a partial unique index allows one open transfer per record. Writers holding an old epoch are fenced into a conflict.
5. **Freeze before fence.** From the moment a transfer is frozen, writes answer `record_frozen` (retryable, not stored).
6. **Idempotency at three levels:** the batch key, the per-device command id (duplicate → DUPLICATE, altered content → `replay_mismatch`) and the per-device sequence (`sequence_reuse`).
7. **Decisions are immutable.** Resolved conflicts and reconciliation rows cannot be changed (triggers). The offline source version is always retained.

## Consequences
* Safe by default: nothing is offline until a domain owner approves it.
* Reviewers carry the clinical decision; the system supplies the evidence.
* Without an offline client and domain adapters the module is a verified server foundation, not an end-to-end offline capability.
