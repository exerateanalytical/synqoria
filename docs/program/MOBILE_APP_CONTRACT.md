# Mobile products — not a responsive website substitute

Two separately authorized native application shells share contracts/tokens but not identity/session grants:

**Staff Android/iOS**: enrolled-device login, MFA and role/shift, verified patient context, assigned tasks/triage/vitals, doctor rounds and permitted note drafts, eMAR with device/barcode verification and server authority, lab specimen capture/handovers, dispensing verification, escalation, notifications, shift handover. Role/capability aware menus. No autonomous final medication authorization or administration when authoritative owner inaccessible. Native device camera/scanner permissions, accessibility, screen scaling, tablets/landscape, kiosk/ward profiles.

**Patient Android/iOS**: own record and explicit dependent proxy switching, appointments, results/documents allowed for release, prescriptions/medication list, consent controls, alerts, settings/support and local language. Proxy access expires according to source policy. No staff credentials or facility admin menus.

Both: Expo/React Native where needed hardware capabilities fit (otherwise reviewed native modules), pinned dependencies, EN/FR, encrypted device storage for permitted bounded offline drafts, secure session lifecycle, device enrollment/revocation, no PHI in push previews/analytics/crash logs, safe background sync, connection loss, clock drift and conflict/unknown-outcome treatment. Web, staff mobile and patient mobile must use the same authoritative versioned API contracts, but must each have independent interaction/visual/device test evidence.

Native workflow tests must cover lost response after clinical commit, wrong patient scan, denied actions, proxy expiry, screen rotation, 200% font scale, RTL only if enabled by locale policy, hardware scanner/camera denial, stale local policy, offline device vs local-network-available distinction, Android/iOS push and deep-link authentication. Production-grade APK/AAB/IPA distribution and store/privacy disclosures require real signing and human approval. No app store certification is claimed until achieved.
