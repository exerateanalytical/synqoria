# Navigation, styling, responsive and usability rules

Authority: UI/UX Master v1.1, §§3–10,14–17. **No speculative menu entries.**

## Shells
- Workforce web: top bar facility, role, shift, connectivity; patient-context strip only when in patient workflow; breadcrumbs; primary tasks and quick actions above secondary KPIs.
- Patient/proxy web and mobile: explicit "My record" vs "Acting for [dependent]", grant scope and expiry. No staff-only controls.
- Platform operations: hospital cell and deployment context, never patient banner by default.
- Web sidebar has only stable top-level workspaces or modules the actor may use. Details, record IDs, edit/create/review flow steps, dialogs, tabs and one-off deep links belong within the relevant workspace, NOT as top-level menu items. Module-level nested menus need actual route maps and authority checks. Sidebar labels, selected state and icons come from a central registry; feature-gated modules disappear without broken links.
- Patient switches require unsaved-work guard and reauthorization; deep links/back preserve safe context. No patient names or clinical facts in URL, telemetry, page title or query.
- Mobile workforce: maximum four primary bottom destinations including More, e.g. Work, Patients, Tasks, More. Role-specific nursing/triage/lab/pharmacy actions live in contextual flows and More; alerts must never silently disappear. Patient mobile: Home, Appointments, Records, More. All entries have text labels and accessible names. A bottom tab is not a direct clinical-commit shortcut.

## Approved Warm Clinical tokens
Canvas `#F7F5F0`; white surface `#FFFFFF`; subtle `#F0EEE7`; navy/sidebar `#073656`; primary button `#1664D4` with white text; primary hover `#1253B1`; normal text `#172B4D`; secondary `#475569`. Semantic feedback uses separately defined success/warning/error pairs; never use colour alone. Only approved pair matrix (source specification §5).

Locally hosted **Inter**; base inputs/body `16px` at 1.5 line-height; form labels/table 14px min; noncritical metadata 12px min; desktop page title 28px/mobile 24px; touch targets 44px, bedside 48px; cards 12px radius, controls 8px; desktop sidebar 248px.

Mobile widths `<768px`; tablet `768–1199px`; desktop `>=1200px`. Visual checks 320,360,390,412,768,1024,1280,1440,1920 CSS px, EN and FR, text scaling 200%, zoom 400% (browser), portrait and landscape, virtual keyboard and real device inset. Long clinical instructions are never truncated. Forms become single column before cramping; mobile quick-action grids may be two columns only where fits; critical fields always get full width as necessary.

## Prohibited, including on mobile
Glow, neon, gradients, glassmorphism, random card colours, decorative patterns behind content, stretched images/logos, negative-margin hacks, scaled/squeezed controls, missing focus rings, overflow hidden to conceal clinical text, unexplained tab overflow, invisible draft/unknown-outcome states, wrong-patient access, client-only permission checks and screen duplication. All states S00–S13 are covered according to applicability. No mock API counted as integration.

## Acceptance automation
1. Route inventory check: 261 canonical IDs have named intended route or honest `not_implemented`, no duplicate routes or orphaned deep links; per-role navigation generated from capability registry.
2. Automated token usage/style scan; contrast checks on rendered combinations; screenshot/perceptual diff and overflow/overlap checks.
3. Keyboard/AT and native screen-reader checks, touch-target checks, localization and long-text tests. Fail closed if critical clinical labels/actions cannot be read or activated.
4. Verify component workflows navigate back to originating worklist and preserve non-PHI filters. Permission-denied deep links do not reveal existence.
