# v0.48.0 reconciled cumulative engineering

Fifteen newly implemented and individually tested scheduling correctness batches P06-B0070–B0084 were built from the verified v0.44.0 Library checkpoint. Four additional source-level migration safety components were independently adopted and tested from parallel v0.46.0/v0.47.0 branches and remapped to B0085–B0088 because the parallel branches reused B0070–B0073 for different work.

**Parallel source integrity warning:** Both parallel archives had matching outer SHA-256/ZIP CRC but stale embedded SHA-256 manifests (9 mismatches or missing files each), and new migration gate files were not covered by those manifests. Neither parallel archive was treated as a verified full cumulative source. The named gate files were individually copied, inspected, tested and added to this new cumulative archive with a regenerated full manifest. Parallel work is NOT counted among the 15 new batches this run.

Migration inventory 4 tests, immutable checksum lock 6 tests, combined appointment preflight 5 tests passed; all 14 existing migration hashes matched. No live PostgreSQL/RLS execution, no clinical authorization, no release certification.
