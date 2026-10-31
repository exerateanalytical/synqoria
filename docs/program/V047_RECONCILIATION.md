# v0.47.0 concurrent-source reconciliation

A parallel checkpoint v0.46.0 appeared in `/SYNQORIA` after this execution had already verified and started from v0.44.0. The parallel ZIP SHA256/CRC were valid, but its embedded cumulative manifest was stale: **9 mismatched or missing entries** (including README and cache files) and it did not cover new migration-gate files. It therefore was **not** accepted as a fully verified cumulative authority.

Five named files (two migration gates, two tests, one immutable SHA-256 lock) were independently copied and tested from the parallel source. The inventory suite passed 4 tests, the checksum suite passed 6 tests, and all 14 migration hashes matched. These are recorded as P06-B0085 and P06-B0086, **reconciled**, not counted among the 15 newly implemented batches in this execution. The parallel source originally used P06-B0070/B0071 for different work; remapping prevents ID collisions and double counting.

The v0.47.0 ZIP is a new complete archive with a freshly regenerated and independently verified manifest. No PostgreSQL migration was executed; release remains DENIED.
