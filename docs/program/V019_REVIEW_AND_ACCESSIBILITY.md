# v0.19.0 review: what was and was not validated

Reviewer fences are pure Java deterministic checks and do not grant permission. Event-chain verifier validates both SHA-256 linkage and legal event transitions but does not prove external immutable custody or prevent a privileged rewrite of all events. Idempotency check needs a database unique constraint and atomic transaction; in-memory equality cannot prevent races. Outbox descriptor intentionally excludes patient identifiers and clinical payloads. Reviewer freshness checks require trusted clocks and authoritative revocation state.

UX guards are supplementary, not backend authorization. Native source contains a new bilingual disabled registration reason, but Expo, iOS/Android accessibility, safe areas and keyboard behavior have not been built/tested. Native text safety uses platform font scaling, no fixed line height or truncation for critical fields; this is a unit-tested design contract, not a certified drug administration screen.

**Release:** no registration POST, clinical approval, patient/proxy disclosure or live patient data is authorized by these components. **All clinical gates remain closed.**

## Verification incident

A monolithic sequential Python test command timed out during the SQL static tests after 15 successful scripts. It was not marked passed. The full set of 36 non-browser Python scripts was rerun with individual timeouts (36 passed), and the three static Chromium scripts were separately rerun (19 + 10 + 19 checks passed). No browser output establishes a compiled Next.js or native Expo application.
