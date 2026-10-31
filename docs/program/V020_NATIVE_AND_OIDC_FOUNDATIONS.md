# v0.20.0 — Native layout and OIDC foundation review

Five P02 increments update staff and patient React Native sources, package dependencies, and native accessibility/privacy guards. Safe-area/keyboard components are not compiled and Android/iOS layout remains unverified. The registration action remains disabled with button semantics. No clinical PHI is loaded.

Five P03 increments add Java security foundations with isolated synthetic tests. `OidcIssuerDiscoveryFence` checks configured issuer, same realm and TLS endpoints but never performs trusted discovery; `OidcPinnedKeyset` requires externally validated keys; `OidcTokenReplayFence` and `OidcSecurityBurstFence` are process-local and insufficient for multi-cell admission; `OidcFacilitySessionFence` requires independently verified claims and a server-side grant. No component alone or together grants production clinical access.

Release remains NOT_CERTIFIED. External qualified clinical/security sign-offs, PostgreSQL/RLS, production IdP, native compilation, and device testing remain mandatory.
