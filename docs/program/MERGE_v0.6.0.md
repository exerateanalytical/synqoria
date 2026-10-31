# v0.6.0 Two-checkpoint lineage reconciliation

Two independently produced v0.5.0 checkpoints were detected in `/SYNQORIA`: original scheduled engineering automation and interactive build. Both descend from matching SHA-256 v0.4.0 snapshot `9ab5784c6952f9abcc1ae1a7252b25550094f7a9b77a33aef7687da321fd570a`.

- Scheduled checkpoint SHA: `1feb22dd5d9b4e03753365b6cd2dfcddf12a29e9fe7b84bde6a1c25cc5cc322e`, 10 unit/static-tested batches recorded.
- Interactive checkpoint SHA: `eb1b1b5c4c351c0df1847ac97b4a0aa1a9f8dd0cd92e22141dfefed14ad69677`, 10 unit-tested batches recorded.
- Merged and retested 9 independent additions from the interactive build under new globally unique batch IDs. The parallel FacilityLifecycle class/test were retained as single canonical implementation; competing alternate implementation excluded. There is NO claim that 20 distinct deliverables were certified.
- The `registers/batch-ledger.json` retains 19 distinct v0.5/v0.6 additive batch records, preserves original historical batch IDs and prior evidence files. All 261 clinical screens remain unimplemented. No clinical acceptance.
- Final `SYNQORIA_CURRENT.zip` advances only after all available regressions pass and manifest SHA verification.

Conflict resolution: scheduled snapshot is the base for overlapping files (`FacilityLifecycle`, batch evidence, SQL migrations, CI). Independent TypeScript guards and Java domain reference components were cherry-picked. No fabricated tests or claim of database, native app, OIDC, HTTP or hospital deployment.
