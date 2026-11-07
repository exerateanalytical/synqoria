# M38 offline synchronization — operations runbook (engineering draft)

Not operationally accepted. Rehearse in a disposable environment before any hospital use.

## Lost or stolen device
1. Status screen → select the device → give a reason → **Revoke device**. Further uploads answer `device_revoked`; revocation cannot be undone (a new device must be registered).
2. Review the device's `Needs review` items in the queue; its already-received commands remain as evidence.

## A device cannot synchronise
* `device_session_expired`: register a new device (administrator); sessions cannot be extended in place.
* `record_frozen`: an ownership transfer is in progress; the device retries automatically with the same command id.
* `queue_full`: resolve open conflicts for that device; the queue limit counts unresolved conflicts.

## Conflict review
1. Open the conflict; compare the offline change with the authoritative version, epoch and changed fields.
2. Choose *Keep the authoritative record* or *Accept the offline change*, pick a reason (a note is required for Other). Accepting requires that you reviewed the *current* authoritative version; if it moved you are asked to reload.
3. The author of a change can never resolve it.

## Ownership transfer
Propose → Freeze writers → Record fencing evidence (the source cell's confirmed last version must equal the authoritative version) → independent approval → Complete. Abort at any point before completion unfreezes the record. Completion raises the epoch by exactly one; devices still on the old epoch are fenced and their changes go to review.

## Recovery of the service
Commands are idempotent; after a restart devices re-send from their cursor. Do not edit tables by hand; the immutability and epoch triggers will refuse it.
