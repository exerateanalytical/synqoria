# SYNQORIA — mandatory latest **production-supported stable** dependency policy

**Effective:** 2026-11-06. **Scope:** all modules, web/mobile clients, backend, CI, deployment and operations. This document sets development targets; it does **not** state these technologies have all been upgraded or deployed. Machine-readable source: `registers/stack-stable-2026-11-06.json`.

## Binding rule

1. Prefer the most recent generally available, actively maintained stable release **compatible with its complete dependency chain and deployment support**. A newer unverified or incompatible version is a **candidate**, never an automatically activated production version. No alpha, beta, RC, snapshot, canary, pre-release or floating `latest` image tags in production.
2. For Java, **25 LTS** is the default production runtime because this is a long-lived hospital platform; **27 GA** is the latest non-LTS Java and must pass an independent CI compatibility lane. If the product owner explicitly selects Java 27 as production, document the six-month release migration obligation and pass all gates before changing the runtime.
3. Use Spring Boot **4.1.2 GA** (4.2 RC is not acceptable). As of 2026-11-06 official Spring system requirements confirm 4.1.2 supports **up to Java 27**.
4. Use PostgreSQL **18.6** as the target major/patch; **19 RC** is ineligible. DB upgrading requires rehearsal of migrations, RLS, WAL/logical decoding, restore, extensions, and PITR for each hospital cell; never modify a hospital database based only on CI.
5. Web target is **Next 16.4.5 + React/React DOM 19.3.1 + Node 26 LTS**. The web-owner PR #23/#24 lockfile and Next config have to be reconciled. No independent PR may silently overwrite those owners.
6. Native target is **Expo 58.0.23 + React Native 0.88.1** with **Expo-approved React 19.3.0** until React 19.3.1 native compatibility is tested, Android/iOS app build/export and store tests pass. Expo owns the React Native version matrix; never assume matching the web React patch is valid for mobile.
7. Backend, Kafka, Keycloak, Redis, gateway, SRE and analytics targets are listed with upstream provenance. They must be individually tested. For Kong/Grafana patch and deferred Go/Python components, no guessed exact version is accepted.
8. Exact package versions, semantic compatibility constraints, Maven BOM-managed dependencies, signed release checksums/digests, reproducible lockfiles, SBOM and vulnerability review are mandatory. Patch security fixes can be urgent, but never bypass testing of hospital-cell auth, RLS, financial/clinical invariants or permission fences.
9. Use rolling/canary with rollback and change records; every deployment needs an owner, evidence, audit, and rollback. **Never treat CI success as clinical acceptance.**

## GitHub execution ownership
- New independent stacked **latest stable stack** branch is based on [PR #34](https://github.com/exerateanalytical/synqoria/pull/34) rather than mutating workstream owners. Current source action: Java 21 → **25**, CI **27** compatibility, disposable PostgreSQL 16 → **18.6**, CI Node 22 → **26**.
- PR #23/24 owns web/native lock and Expo framework; P03 PR #33 owns staff auth; other P07 event and SRE owners retain ownership. Integrate through reviewed PR and tests. No unverified global dependency bump. Do not rewrite mainline histories.
- Re-run tests from the actual branch head; do not infer status from prior SHA. Clinical release **DENIED**, 0/7 fully operational administrative modules, 0/35 accepted operational screens.

## Upstream release references
- Java GA vs LTS: https://www.oracle.com/java/technologies/downloads/
- Spring Boot requirements: https://docs.spring.io/spring-boot/system-requirements.html
- PostgreSQL: https://www.postgresql.org/docs/release/18.6/
- Node LTS: https://nodejs.org/en/about/previous-releases
- Next: https://www.npmjs.com/package/next
- React: https://github.com/facebook/react/releases
- Expo/RN: https://docs.expo.dev/versions/latest/
- Keycloak: https://github.com/keycloak/keycloak/releases
- Kafka: https://kafka.apache.org/downloads/
- Debezium: https://debezium.io/releases/
- Redis: https://redis.io/docs/latest/operate/oss_and_stack/install/version-mgmt/
- Kubernetes: https://kubernetes.io/releases/
