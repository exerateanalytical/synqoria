# SYNQORIA v0.66.0 — P07-B0120–B0129 clinical inbox operations

Base: `b61c71403428509665b32a5d8db5f0b2e0a172f3` (v0.65 P07-B0119). Work branch `work/longitudinal-inbox-operations-v066`, stacked on draft PR #8, NOT merged to `main`.

## Ten individually implemented, tested, and GitHub-published batches

| Batch | Feature | Verified commit |
|---|---|---|
| P07-B0120 | Private broker record schema/digest/partition binding | `d0b7670c19739b41dc162321e5bbc753231adfc7` |
| P07-B0121 | Tenant event time/future skew fence | `d9e2d011cb0f3409fec6e30b21119a16442fb4a2` |
| P07-B0122 | Consumer lease generation fence | `74d6bb4f200e38314b4855d8f176ecf36d19fadf` |
| P07-B0123 | Contiguous per-partition broker offset watermark | `81fe849456ba2b005f4d2e50eb1c00339f79547b` |
| P07-B0124 | Typed failure retry/quarantine/stop | `ee487d9e4e263a867369c27222aa2008568dd4d8` |
| P07-B0125 | Bounded token-based in-flight reservations | `000dd1b78e2486d6d52b8f319497510a9edeae30` |
| P07-B0126 | Partition ownership and assignment epoch | `4d7f2102a6bdf5379d15f927b4960e36b74195fa` |
| P07-B0127 | HMAC-protected quarantine metadata | `fc17eac02f85a2eece42e6064cce6f14a2cb2af9` |
| P07-B0128 | Broker-vs-DB checkpoint divergence handling | `f51476ee35eae3f78d56f90774cf76592fb5f1fa` |
| P07-B0129 | Fail-closed dependency readiness | `f5ef380c9f238c580e0347c40a3965d5aa509541` |

Each commit adds independent Java source, runnable Java test, focused result and batch register. All ten remote branch updates and final SHA verified. This is **10 of the user's requested remaining 195**; **185 remain**. Next P07 candidate: **B0130**, subject to cross-chat / PR check. Separate P06 next B0392 (not used here).

## Local verification (engineering only)

- Java 21 compile against extracted v0.61 foundation plus v0.64/v0.65 overlays passed.
- Ten independently executed Java test classes: **57 checks**, all passed.
- `npm run check:ux`: **292/292 passed** in that local-lineage harness.
- `bash tools/check_longitudinal_inbox.sh`: inherited 10 scenarios passed.
- `python tools/verify_status_matrix.py`: passed.
- Local v0.66 delta ZIP SHA-256: `585e53d4b710799d9c2101d8a0d22c28be08051371f2bdfc89b195affcf65013`. This ZIP is not the full cumulative stacked branch.
- Entire v0.63–v0.66 stacked branch was **not** compiled end-to-end; no live Kafka, PostgreSQL nonowner RLS, trusted OIDC/KMS, real Next/Expo builds or clinical signoff is asserted.

Canonical 40 modules and 261 screens unchanged; clinically accepted screens 0/261; certified modules 0/40. Formal certification deferred, all clinical release gates DENIED.

Do not merge this draft PR without safely reconciling stacked PRs #1–#8 with current `main` and running a complete build. Do not overwrite `SYNQORIA_CURRENT.zip` with the partial-lineage ZIP.
