# v0.18.0 execution incidents and resolutions

- First `javac` compilation failed (exit 1): `Map<String,CAP#1> cannot be converted to Map<String,Object>` in the new V180 header test, due to a heterogeneous `Map.of` collection. The `OidcProtectedHeaderFence.allows` input was corrected to `Map<String,?>`; recompilation succeeded and all five new Java batch tests passed. No failed compilation was counted as verified.
- First historical `tests/test_v170_checkpoint.py` regression failed (exit 1) because it required `version == 0.17.0` even for later checkpoints. Its version assertion now accepts versions >= 0.17.0 while retaining exact historical batch IDs, test evidence, status and release-lock assertions. It passed after correction. This is test-maintenance work, not an additional implementation batch.
- No PostgreSQL server or executable was available. The reviewer transaction `P05-B0027` remains blocked and is not counted.
- Static Chromium checks are not Next.js or Expo runtime/device checks. No browser screenshots are claimed to represent compiled clinical applications.
