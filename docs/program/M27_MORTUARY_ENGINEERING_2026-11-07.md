# SYNQORIA M27 — Mortuary and Decedent Release Engineering Handoff

**Branch:** `work/chatgpt-m27-mortuary-2026-11-07`  
**Base:** `work/seven-operations-real-workflows-2026-11-06` (PR #36).  
**Owner:** M27 only. Claude separately owns M26. Do not modify P03/P06/P07 modules, or PR #16/23/24 code.

## Canonical screens
- M27-S01: Mortuary register and case/identity state
- M27-S02: Verified intake with death record, origin unit, signed authorization, sealed belongings, on-shift independent receiver
- M27-S03: Storage and custody transfers with physical bay exclusivity, seals, condition and independent acceptance
- M27-S04: Release eligibility review and independent second approval, with active legal hold fences
- M27-S05: Verified recipient, legal authority, independent witnessed handover and independently attested belongings return

All 5 screens use `apps/web/components/MortuaryWorkbench.tsx`, the existing authenticated Next BFF, and **NO browser bearer-token storage**. English/French text, responsive cream/white no-glow UI with keyboard focus, role-fenced controlled state actions.

## Backend
- Spring Boot 4.1.2 / Java25 LTS and Java27 compatibility in existing operations core, separate `org.synqoria.operations.mortuary` domain.
- REST APIs in `contracts/openapi/synqoria-m27-mortuary-v1.yaml` and `MortuaryApi.java`.
- A CASE is a pseudonymous pointer to an externally validated decedent identity, **not** a replacement MPI, death registry or patient identity authority.
- Legal death, identity, intake authorization, belongings seals, storage safety, custody acceptance, recipient identity, legal release, independent second approval, final handover and belongings return are independently signed and provisioned to a **read-only-to-runtime** proof table. API claims alone can never satisfy these requirements.
- Exact `synqoria_cell` and `synqoria_facility` in signed issuer/audience JWT, shift/revision-bound independently managed workforce grants, module action capability, maker-checker, distinct two approval officers, third handover actor and separate custody witness.
- PostgreSQL **V005–V009**: eight new FORCE RLS mortuary tables, physical compartment uniqueness, versioned immutable custody history, immutable release approvals, legal hold fence, evidence issuer revocation lock, transactional idempotency/audit/outbox.
- No free-text patient identity/death details are in the outbox. No case, custody, approval or audit deletion grant to runtime.

## Engineering acceptance versus hospital acceptance
Check GitHub Actions for the exact final commit, **never count failed, running or source-only checks as passing**:
1. M27 Java25/Java27 domain unit suite
2. Real disposable PG18.6 V001–V009 migration and least-privilege RLS
3. M27 real JDBC integration, death verification, custody/holding bay, two-person release/handover/release denial
4. Actual Next16.4.5/React19.3.1 five-screen build with PR23/PR24 dependency **CI-only** overlays
5. M27 canonical five-screen source contract

**NOT IMPLEMENTED/NOT AUTHORIZED:** P03-sourced live roster/IdP issuance and revocation, external civil/death registry and funeral/legal authorization adapters, physical biometric/fingerprint identity confirmation, national legal document signatures, archival encrypted belongings photographs and disposal retention; actual iOS/Android device and facility workflow acceptance; staging cell integration/certification. All are required prior to live decedent handling. Existing broad root build remains red owing to unrelated PR23/24 npm lock reconciliation. **M27 operational acceptance 0/1, screen acceptance 0/5, clinical/legal release DENIED**. No invented completion percentage.

## M27 continued hardening — V007
- The independent issuer's mortuary authority evidence identity, contents and authority fields are immutable after issuance; expiry may be shortened and revocation is irreversible. Existing proofs cannot be repurposed or hard-deleted.
- Court/legal hold basis is immutable; only irreversible, timestamped rescission is permitted. Hard deletion/reactivation is denied even for the synthetic migration-admin integration principal.
- V006 serialization fences continue to apply to permitted proof revocation and hold rescission; V007 adds hardening beyond runtime RLS. Updated real PostgreSQL integration CI must prove these rules before this checkpoint is accepted.

## M27 physical storage-bay attestation binding — V008
- New independent STORE/TRANSFER proofs bind the exact intended physical bay. Historical unbound proofs fail closed at runtime, but can still be revoked.
- Java matches the independent authorized bay to the requested destination. Issuers cannot rebind already-issued evidence; occupancy uniqueness remains enforced.
- Dedicated disposable PG tests cover wrong-bay rejection, right-bay acceptance and binding immutability. Clinical acceptance remains denied.

## M27 participant-bound evidence V009
- Proof of custody/storage must bind the actual receiving staff ID and physical bay; legal release authorizations name the independently verified recipient; final handover and belongings receipts bind both actual witness and recipient.
- Issuer cannot alter bindings on an existing proof. Previously issued unbound proof remains revocable but cannot authorize a new movement.
- Disposable PG18.6 integration regression validates a second valid on-shift receiver or witness cannot use someone else's proof. No real institutional legal/clinical approval is implied.
