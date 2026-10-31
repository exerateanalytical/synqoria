# v0.21.0 — Reviewer integrity and native privacy review

The new Java reviewer guards validate command identity, distinguish cross-scope/replay/conflict, validate sequential state transitions, require JDBC serializable transactions, and link outbox metadata to a command and audit digest. They do not execute SQL, obtain database role privileges, guarantee a durable audit/outbox commit, or authorize registration. The JDBC preflight is necessary but insufficient: a trusted restricted DB role, RLS, audited transaction semantics and live integration remain required.

The new TypeScript native guards deny unauthenticated navigation, expire context-bound return tickets, expose EN/FR accessible locked status, invalidate stale foreground epochs and reject clinical mutations offline and online without approved release. Both React Native App.tsx sources display the locked accessibility announcement and policy reason. Actual Expo builds, device screen-reader behavior, keyboard/safe-area runtime and 200% font screenshots are not verified.

**Acceptance:** all new unit checks and source checks pass; release denied; no patient information, medications or real clinical records used. Independent security and clinical review remain outstanding.
