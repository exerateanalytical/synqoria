# SYNQORIA — UI/UX Master Specification

**Africa medical intelligence synchronized**

Version 1.1 • 27 October 2026 • English/French • Companion to architecture revision 2.1

## 1. Authority, scope and honest completion status

This is the proposed canonical UI specification for the 40-module SYNQORIA scope. It defines a concrete screen inventory, screen-level baseline contracts, module workflows, reusable layouts, visual tokens, responsive behavior, icon mappings and coding-agent rules. It is not a set of rendered mockups, production software or evidence of clinical/accessibility certification. All screens start at **Specified—baseline; not designed, implemented, tested or accepted**. Adoption is authorized by the user's request; clinical rules and high-risk usability decisions still require qualified review.

Architecture source: [SYNQORIA architecture revision 2.1](https://chatgpt.com/library?file_id=libfile_9320b05dea90819181856957fb002d41&account_id=personal). The authoritative 40-module register and clinical safety protocols were checked against that file. This UI companion does not replace its consistency, ownership, authorization or recovery rules.

No screen count is a claim that all future hospital specialties, payer formats or device types have been discovered. This version fixes a baseline that can be changed only through a recorded scope decision. For every screen, its register row, module flow, layout template and universal state contract together form its specification. Field groups are explicit; database types, exact terminology bindings and clinical value thresholds belong in reviewed module contracts and must not be guessed by a coding agent.

## 2. Counting and change control

**261 canonical screens: 12 shared screens + 249 module screens across 40 modules.** M17 includes 24 explicitly named specialty pages across eight packages; they remain inside M17. This count excludes 26 reusable components, 12 dialog variants and 18 printable outputs.

| Module | Screens | Module | Screens |
|---|---:|---|---:|
| M01 Patient registration and master identity | 6 | M21 Procurement and suppliers | 6 |
| M02 Appointments and resource scheduling | 5 | M22 General stores inventory | 5 |
| M03 Triage emergency intake and queues | 5 | M23 Assets biomedical maintenance and facilities | 5 |
| M04 Encounters ADT and beds | 7 | M24 HR staffing and payroll | 6 |
| M05 Clinical notes and diagnoses | 5 | M25 Transport and ambulance | 5 |
| M06 Observations and vitals | 5 | M26 Catering and dietary orders | 4 |
| M07 Allergies intolerances and reconciliation | 5 | M27 Mortuary and decedent release | 5 |
| M08 Medication prescribing and reconciliation | 7 | M28 Hospital operations and BI | 5 |
| M09 Medication administration | 5 | M29 Facility department location and services | 4 |
| M10 Nursing care plans and handovers | 5 | M30 Terminology formularies and clinical rules | 5 |
| M11 Laboratory specimens and results | 9 | M31 Forms templates documents and signatures | 6 |
| M12 Imaging radiology and PACS | 7 | M32 Consent privacy and disclosure | 5 |
| M13 Pharmacy dispensing and inventory | 7 | M33 Identity access and workforce privileges | 7 |
| M14 Surgery theatre anesthesia and recovery | 6 | M34 Audit provenance and retention | 5 |
| M15 Blood bank and transfusion | 6 | M35 Interoperability and API developer product | 6 |
| M16 Referrals and care transitions | 5 | M36 Devices analyzers and bedside gateways | 5 |
| M17 Specialty clinical packages | 28 | M37 Notifications communication and escalation | 4 |
| M18 Patient portal mobile and proxy access | 9 | M38 Offline sync and ownership reconciliation | 5 |
| M19 Charges invoices cashier and payments | 7 | M39 Tenant cell routing and fleet releases | 5 |
| M20 Insurance payer authorization and claims | 6 | M40 Reliability backup recovery and telemetry | 6 |


A screen is a distinct navigable task surface, with its own stable identity and meaningful navigation/history behavior. Responsive variants are the same screen. Tabs, dialogs, drawers, inline alerts, printable outputs and loading/error states are separately registered components or variants, not extra screens. The external PACS diagnostic viewer is an integration surface, not a claim that SYNQORIA implements certified image interpretation.

IDs never change when a screen is renamed. Removed IDs are retired, not reused. New IDs require a scope entry identifying module, user need, workflow predecessor/successor, fields, permission, risk, tests and owner. Do not manufacture placeholder routes to make inventory coverage appear complete. Patient-facing pages use G authentication screens with the patient shell; workforce-only controls are absent. Screen metadata IDs must not become a giant numeric route dispatcher: use named feature routes and attach the screen ID as metadata and a test selector.

## 3. Product navigation and information hierarchy

Provide separate workforce, patient/proxy and platform-operations shells. A hospital administrator is not automatically authorized to read clinical records. Navigation is capability-aware and tenant-scoped; the backend enforces permissions independently. Hide unavailable licensed modules, show safe explanations for denied deep links, and never expose patient existence in unauthorized responses.

Workforce shell: facility/role/shift and connectivity → patient/context strip when applicable → breadcrumb and task title → urgent actionable exceptions → quick actions → working content → secondary statistics. Dashboards prioritize tasks over decorative KPIs. Clinical workspaces must not start with financial marketing cards. Platform operators use cell context rather than a patient banner. Portal shell always states **My record** or **Acting for [dependent]** with grant scope and expiry.

Patient context shows full name, stable local identifier and a second approved identifier, encounter and location, plus explicit allergy assessment state. Show birth date and age according to policy; do not infer sex-related clinical facts from gender identity. Similar-name and provisional-identity markers have text and icons. Switching patient with an unsaved form requires save-draft/discard/cancel choices; clear patient-specific cached queries and recheck authorization. Browser back, multiple tabs and deep links must preserve correct context. Do not put names or clinical facts in URLs, page analytics or document titles visible outside the secure app.

## 4. Mandatory visual restrictions

R01 No glow: no neon, text-shadow, drop-shadow filter, luminous borders, pulsing highlights or decorative bloom.

R02 No colour collisions: only registered tokens and approved foreground/background pairs; no random per-card colour schemes. No gradients, glassmorphism, translucent clinical cards, background photography or decorative patterns behind work content. A future approved logo may retain its own artwork; do not invent or redraw a logo. Until a source asset is supplied, use the plain SYNQORIA wordmark and tagline in the approved typography.

R03 No distortion: SVG aspect ratios remain intact; images use contain unless explicitly an approved thumbnail crop. Never stretch the logo, patient images, signatures, scans or diagnostic images. Scanned documents preserve page proportions; diagnostic viewing belongs in the approved PACS viewer.

R04 No cramping: never solve overflow with smaller fonts, compressed line-height, negative margins, transform:scale, fixed-height text clipping or blanket overflow:hidden. Give long labels wrapping space. Containers grow or reflow. Do not hide medical details to match a screenshot.

R05 No squeezing: layouts use minmax(0,1fr), min-width:0 on flex/grid content and controlled word wrapping. Forms turn to one column before labels collide. Toolbars wrap or use a labelled More actions menu. Critical actions stay visible.

R06 No decorative clutter: no gratuitous icon circles, emoji UI, faux 3D elements or coloured glow charts. Use neutral surfaces, clear headings and controlled spacing. Do not put every label in a badge or every paragraph in a separate card.

R07 No critical truncation: medication name/formulation, dose/unit/route, identifiers, allergy substance/reaction and critical instructions must wrap or have an explicit accessible expansion. Never silently ellipsize them. Noncritical descriptions may truncate only with a reachable full-text view.

R08 No ambiguous outcomes: Draft saved locally, Saved to hospital server, Pending synchronization, Synchronized and Outcome unknown are distinct. A spinner, toast or HTTP timeout is never proof of success or failure.

R09 No colour-only meanings: pair status colour with text and a consistent icon. Critical alerts state issue, consequence and next permitted action. Ordinary successes do not create permanent banners.

R10 No invented safety: UI disablement is supplementary. All permissions, versions, clinical preconditions and effect deduplication are enforced server-side. Never display a clinical safety pass solely from a cached calculation.

## 5. Design tokens and approved colour use — Warm Clinical

**Visual direction: Warm Clinical.** A light cream canvas frames white work surfaces, with deep navy navigation and restrained blue actions. Warmth comes from neutral backgrounds; freshness comes from white surfaces, clear type and generous spacing. Clinical status colours remain reserved for meaning. This replaces the cool page canvas from version 1.0.

Light clinical theme only in this baseline. Dark mode requires a separately tested token matrix; no automatic colour inversion. Theme selection cannot change clinical meanings.

| Token | Hex | Use |
|---|---|---|
| brand.navy | #073656 | Navigation, wordmark, strong headings |
| action.primary | #1664D4 | Primary action surface; white label |
| action.hover | #1253B1 | Primary hover surface; white label |
| action.pressed | #0E438F | Primary pressed surface; white label |
| link | #0B4AA2 | Underlined inline links on approved light surfaces |
| nav.hover | #104766 | Sidebar item hover; white text |
| nav.selected | #165A7A | Selected sidebar item; white text and solid white indicator |
| surface.hover | #F5F7FA | Neutral row and ghost-action hover |
| surface.pressed | #E2E8F0 | Neutral pressed state |
| destructive.hover | #912018 | Destructive hover; white label |
| destructive.pressed | #781B14 | Destructive pressed; white label |
| overlay.scrim | rgba(7,54,86,0.40) | Modal backdrop only, never a text surface |
| canvas | #F7F5F0 | Warm cream page background; not warning yellow |
| surface | #FFFFFF | Cards, fields, dialogs |
| surface.subtle | #F0EEE7 | Warm neutral section headers and read-only grouping |
| text.primary | #172B4D | Main text |
| text.secondary | #475569 | Supporting text, placeholders that remain readable |
| border.decorative | #DEDCD5 | Nonessential separators only |
| border.control | #64748B | Required input outlines and meaningful boundaries |
| focus | #1664D4 | Solid keyboard focus indicator with white separation |
| selected.bg | #E8F0FE | Selected rows/navigation |
| selected.fg | #0B4AA2 | Selected label and icon |
| info.bg / info.fg | #EAF2FF / #174EA6 | Informational status |
| success.bg / success.fg | #E8F5EC / #166534 | Confirmed successful state |
| warning.bg / warning.fg | #FFF4D6 / #854D0E | Attention required |
| danger.bg / danger.fg | #FDECEC / #B42318 | Error or urgent hazard |
| neutral.bg / neutral.fg | #EDF2F7 / #475569 | Neutral, pending with explicit text |

Status foreground colours pair only with their named backgrounds or independently tested white surfaces. Destructive solid buttons use danger.fg with white text. Disabled controls use neutral tokens, a disabled semantic state and an adjacent explanation when needed; do not dim an entire section containing important information. White text is not allowed on pale status backgrounds.

Calculated approved pair checks (version 1.1):

| Pair | Contrast ratio | Minimum | Result |
|---|---:|---:|---|
| #172B4D text on #FFFFFF | 14.10:1 | 4.5:1 | Pass token calculation |
| #475569 text on #FFFFFF | 7.58:1 | 4.5:1 | Pass token calculation |
| #073656 text on #FFFFFF | 12.57:1 | 4.5:1 | Pass token calculation |
| #0B4AA2 text on #FFFFFF | 8.35:1 | 4.5:1 | Pass token calculation |
| #172B4D text on #F7F5F0 | 12.94:1 | 4.5:1 | Pass token calculation |
| #475569 text on #F7F5F0 | 6.95:1 | 4.5:1 | Pass token calculation |
| #073656 text on #F7F5F0 | 11.53:1 | 4.5:1 | Pass token calculation |
| #0B4AA2 text on #F7F5F0 | 7.66:1 | 4.5:1 | Pass token calculation |
| #172B4D text on #F0EEE7 | 12.15:1 | 4.5:1 | Pass token calculation |
| #475569 text on #F0EEE7 | 6.53:1 | 4.5:1 | Pass token calculation |
| #073656 text on #F0EEE7 | 10.82:1 | 4.5:1 | Pass token calculation |
| #0B4AA2 text on #F0EEE7 | 7.19:1 | 4.5:1 | Pass token calculation |
| #172B4D text on #F5F7FA | 13.14:1 | 4.5:1 | Pass token calculation |
| #475569 text on #F5F7FA | 7.06:1 | 4.5:1 | Pass token calculation |
| #073656 text on #F5F7FA | 11.71:1 | 4.5:1 | Pass token calculation |
| #0B4AA2 text on #F5F7FA | 7.78:1 | 4.5:1 | Pass token calculation |
| #172B4D text on #E2E8F0 | 11.44:1 | 4.5:1 | Pass token calculation |
| #475569 text on #E2E8F0 | 6.15:1 | 4.5:1 | Pass token calculation |
| #073656 text on #E2E8F0 | 10.19:1 | 4.5:1 | Pass token calculation |
| #0B4AA2 text on #E2E8F0 | 6.77:1 | 4.5:1 | Pass token calculation |
| #172B4D text on #E8F0FE | 12.31:1 | 4.5:1 | Pass token calculation |
| #475569 text on #E8F0FE | 6.61:1 | 4.5:1 | Pass token calculation |
| #073656 text on #E8F0FE | 10.97:1 | 4.5:1 | Pass token calculation |
| #0B4AA2 text on #E8F0FE | 7.29:1 | 4.5:1 | Pass token calculation |
| White text on #073656 | 12.57:1 | 4.5:1 | Pass token calculation |
| White text on #104766 | 9.93:1 | 4.5:1 | Pass token calculation |
| White text on #165A7A | 7.56:1 | 4.5:1 | Pass token calculation |
| White text on #1664D4 | 5.52:1 | 4.5:1 | Pass token calculation |
| White text on #1253B1 | 7.25:1 | 4.5:1 | Pass token calculation |
| White text on #0E438F | 9.47:1 | 4.5:1 | Pass token calculation |
| White text on #B42318 | 6.57:1 | 4.5:1 | Pass token calculation |
| White text on #912018 | 8.66:1 | 4.5:1 | Pass token calculation |
| White text on #781B14 | 10.68:1 | 4.5:1 | Pass token calculation |
| White text on #172B4D | 14.10:1 | 4.5:1 | Pass token calculation |
| Info | 6.96:1 | 4.5:1 | Pass token calculation |
| Success | 6.35:1 | 4.5:1 | Pass token calculation |
| Warning | 6.25:1 | 4.5:1 | Pass token calculation |
| Danger | 5.76:1 | 4.5:1 | Pass token calculation |
| Neutral | 6.73:1 | 4.5:1 | Pass token calculation |
| Control outline on #FFFFFF | 4.76:1 | 3:1 | Pass token calculation |
| Control outline on #F7F5F0 | 4.37:1 | 3:1 | Pass token calculation |
| Control outline on #F0EEE7 | 4.10:1 | 3:1 | Pass token calculation |
| Control outline on #EDF2F7 | 4.23:1 | 3:1 | Pass token calculation |
| Chart line #1664D4 on white | 5.52:1 | 3:1 | Pass token calculation |
| Chart line #0F766E on white | 5.47:1 | 3:1 | Pass token calculation |
| Chart line #6D28D9 on white | 7.10:1 | 3:1 | Pass token calculation |
| Chart line #92400E on white | 7.09:1 | 3:1 | Pass token calculation |
| State/focus indicator #B42318 on #FFFFFF | 6.57:1 | 3:1 | Pass token calculation |
| State/focus indicator #1664D4 on #FFFFFF | 5.52:1 | 3:1 | Pass token calculation |
| State/focus indicator #1664D4 on #F7F5F0 | 5.06:1 | 3:1 | Pass token calculation |
| State/focus indicator #FFFFFF on #165A7A | 7.56:1 | 3:1 | Pass token calculation |

Ratios are mathematical sRGB checks of named token pairs, not proof that implemented screens conform. Test real states, chart lines, browser autofill, hover, focus, validation and third-party components. Charts use labelled series, shapes/dashes and accessible data tables; colour cannot carry distinctions alone. Do not use warning/success colours as arbitrary chart categories. Clinical waveform colours and diagnostic presentation require their own specialist approval.


### 5.1 How colours blend across a complete screen

Use this exact layer order: cream page → white working panel → restrained warm section band where grouping is needed → white input → dark text. Pale blue is reserved for a selected entity or information state, not every card. Use the same white surface for all ordinary cards; do not tint modules differently. Navy anchors the sidebar and headings. Blue identifies the next primary action and interactive selection. Cream must not be used inside clinical images or printed reports as a filter.

| Surface or object | Background | Text/icon | Border/indicator |
|---|---|---|---|
| Application canvas | canvas | text.primary | None |
| Global top bar | surface | text.primary; brand.navy wordmark | bottom border.decorative |
| Main sidebar | brand.navy | #FFFFFF | No decorative border |
| Sidebar item hover | nav.hover | #FFFFFF | Same item dimensions |
| Sidebar selected item | nav.selected | #FFFFFF at weight 600 | 3px white leading indicator; label retained |
| Patient context strip | surface | text.primary | bottom border.decorative; separate alert badges |
| Standard content card | surface | text.primary | 1px border.decorative; 12px radius |
| Form section band | surface.subtle | brand.navy | No additional coloured stripe |
| Standard editable input | surface | text.primary | 1px border.control |
| Read-only value group | surface.subtle | text.primary | Labelled Read only where ambiguous |
| Selected record row | selected.bg | text.primary; selected.fg marker | 3px selected.fg leading marker and selected label/check |
| Disabled action | neutral.bg | neutral.fg | border.control; no opacity reduction |
| Tooltip | text.primary | #FFFFFF | 8px radius; no glow |
| Modal content | surface | text.primary | border.decorative; permitted neutral overlay shadow |
| Printed clinical document | #FFFFFF | #172B4D or #000000 | Monochrome-safe rules |

Never place cream text on cream, white text on pale blue, light gray text on white, blue body text on navy, or a coloured status label on an arbitrary tinted surface. Dark text inside every status badge uses the paired semantic foreground. Cream and warning yellow are deliberately different: an actual warning also includes a warning icon, explicit heading and action. Decorative separation does not need to masquerade as a control border; actionable controls always use the stronger control outline or a clearly contrasted filled shape.

### 5.2 Selection, errors and competing states

When multiple states coexist: preserve the background of the control's semantic state, add focus as an independent outline, and show selection with an independent marker. Focus must never erase an error. Example: an invalid focused field keeps its danger border and error text plus the blue focus outline separated by a white gap. A selected row containing a critical result keeps the selected background and a distinct danger badge; never recolour the whole row bright red. Focus inside navy navigation uses a solid white outline. Critical warnings remain visible regardless of hover, selection or density.

Disabled controls do not receive hover/pressed styling and never look like a loading button. Loading retains its enabled colour and dimensions, announces progress and blocks duplicate submissions. Skeletons use neutral.bg on white, no animated shimmer. Empty states use text.secondary and a relevant outline icon without decorative illustrations. Toasts use white with a semantic icon/border and readable text; blocking errors belong next to the affected workflow.

### 5.3 Data visuals and print colour rules

Approved non-semantic chart series: #1664D4 blue, #0F766E teal, #6D28D9 violet, #92400E brown. Assign them by stable series order, not random render order. Use solid/dashed/dotted line distinctions, point shapes, direct labels and a data table. Trend direction must not automatically mean good/bad. Reserve danger/success colours for a clinically or operationally defined state. Chart surfaces are white; no gradient fills. Heatmaps require a labelled scale and numeric/list alternative. These are general analytics defaults; specialist waveform and diagnostic displays require their own reviewed scheme.

No pale text or colour-only status in print. High-contrast monochrome printing must preserve document status, severity and signatures. Cream is a screen canvas choice, not a compulsory page-filling printer background.

## 6. Typography, spacing and dimensions

Use locally hosted **Inter**, with system-ui, Segoe UI, sans-serif fallbacks and French accents. No network font dependency for local hospital operation. Font files/licences must be recorded. Base text and editable inputs: 16px equivalent in rem; body line-height 1.5. Table text and labels: 14px minimum, line-height 1.4; noncritical metadata: 12px minimum, never clinical values/instructions. Page title 28/36px desktop and 24/32px mobile; section title 20/28px; card title 16/24px semibold. Weights: 400, 500, 600, 700; no ultralight clinical text. Use tabular numerals for aligned measurements, quantities and money; never depend on ambiguous glyphs for identity checks.

Spacing scale: 4, 8, 12, 16, 20, 24, 32, 40, 48px. Page gutters: 16px mobile, 24px tablet, 32px wide desktop. Cards: 16px mobile/24px desktop padding. Section gap 24px; related field gap 16px; label-to-control gap 8px; control-to-help gap 4px. Panel radius 12px, control radius 8px, small status badge 4px. Borders 1px. No fixed card heights for text content.

Interactive touch targets: project minimum 44×44px; bedside primary controls 48px minimum. These are project rules, not a statement that WCAG AA universally requires 44px. Inputs minimum 44px high, bedside 48px. Icons inside buttons may be 20px while their hit area remains at least 44px. Data rows minimum 48px and grow for content. Compact desktop density may use 40px rows only for read-only text rows with no interactive control; interactive rows remain at least 48px. No compact bedside mode.

Only a neutral elevation shadow on overlays is allowed: 0 8px 24px rgba(23,43,77,0.12). Cards use borders, not shadows. Motion is limited to 120–180ms opacity or minor position transitions, removed for reduced-motion preferences. No automatic flashing or sound; clinically necessary alarms require a separate approved alarm design.


### 6.1 Exact typography hierarchy

All sizes below are CSS-pixel equivalents at the default root size; implement in rem and respect user scaling. Do not hard-code device-pixel sizes. Native apps use platform text scaling and equivalent tokens. Only Inter is the product typeface; code samples use ui-monospace, SFMono-Regular, Consolas, monospace. Never introduce a second decorative font per module.

| Role | Desktop size/line-height | Mobile size/line-height | Weight | Colour |
|---|---|---|---:|---|
| Page title | 28/36 | 24/32 | 600 | brand.navy |
| Section title | 20/28 | 20/28 | 600 | brand.navy |
| Card heading | 16/24 | 16/24 | 600 | text.primary |
| Body and narrative | 16/24 | 16/24 | 400 | text.primary |
| Clinical value | 16/24 | 16/24 | 500 | text.primary |
| Form label | 14/20 | 14/20 | 600 | text.primary |
| Form input | 16/24 | 16/24 | 400 | text.primary |
| Button label | 14/20 | 16/24 for 48px buttons | 600 | Defined by variant |
| Tab and navigation label | 14/20 | 14/20 | 500; active 600 | Defined by component |
| Table body | 14/20; clinical critical fields 16/24 | 16/24 card values | 400; key value 500 | text.primary |
| Table header | 14/20 | 14/20 | 600 | text.secondary |
| Help and validation text | 14/20 | 14/20 | 400; error 500 | text.secondary or danger.fg |
| Nonclinical metadata | 12/16 | 12/16 | 400 | text.secondary |
| KPI number | 28/36 | 24/32 | 600 | brand.navy |
| Patient name in context | 18/26 | 18/26 | 600 | text.primary |

Sentence case for labels, buttons and headings; no all-caps clinical paragraphs. Acronyms and authoritative code displays keep their true spelling. Links in body text are underlined; hovered links remain underlined. Weight 600 does not replace hierarchy. Avoid a whole screen of bold labels. Font smoothing or synthetic bold must not be used to compensate for contrast. Use normal letter spacing; no negative tracking on clinical identifiers. Do not use letter spacing to compress labels.

### 6.2 Buttons — geometry and complete variants

| Size token | Minimum height | Horizontal padding | Vertical padding | Icon | Radius | Usage |
|---|---:|---:|---:|---:|---:|---|
| control.standard | 44px | 16px | 12px with 20px line box | 20px | 8px | Desktop and tablet general actions |
| control.touch | 48px | 20px | 12px with 24px line box | 20px | 8px | Mobile and bedside default |
| control.emphasis | 56px | 24px | 16px with 24px line box | 24px | 8px | Kiosk or approved single dominant task |
| control.icon | 44×44px minimum | 12px | 12px | 20px | 8px | Labelled accessible icon-only utility |

Heights are minimums; labels wrapping or larger text increase height. With border-box sizing, line-height plus padding and borders may exceed the minimum; do not clip to force an exact total. Icon-to-label gap 8px. Text-button minimum width 88px, content-driven maximum up to container width. No fixed 120px width for every action. Related buttons have 8px gap; major action groups 16px. A long translated label wraps at word boundaries or becomes a full-width action. No two-line ellipsis.

| Variant | Default background / label | Hover | Pressed | Border | Use |
|---|---|---|---|---|---|
| Primary | action.primary / white | action.hover / white | action.pressed / white | same as background | Main next action in current task |
| Secondary | white / brand.navy | surface.hover / brand.navy | surface.pressed / brand.navy | border.control | Alternative nondestructive action |
| Tertiary | parent surface / link | surface.hover / link | selected.bg / link | transparent | Low-priority light-surface action |
| Destructive | danger.fg / white | destructive.hover / white | destructive.pressed / white | same as background | Explicit final destructive/reversal decision |
| Danger secondary | white / danger.fg | danger.bg / danger.fg | danger.bg / danger.fg | danger.fg | Open cancellation/reversal review |
| Disabled, all variants | neutral.bg / neutral.fg | unchanged | unchanged | border.control | Unavailable action with explanation |

All enabled variants have focus-visible outline 2px solid focus with 2px white separation; dark navigation controls use white. Buttons do not move, grow or cast a glow on hover. Do not use a green Save button in one module and blue Save elsewhere. Successful completion is a status, not a new primary-button brand. Normally one primary action per active task area; independent rows can contain repeated contextual actions, but avoid a page filled with competing blue buttons.

Submitting: retain width, label changes to a meaningful in-progress verb, optional 16px non-flashing progress indicator, aria-busy, no duplicate command. Do not disable Cancel if the task truly remains cancellable; otherwise explain why. A cancel click cannot pretend to stop a command already committed remotely. After result, announce the confirmed outcome and preserve audit context.

Desktop action order in left-to-right EN/FR: tertiary/back at the left, secondary cancel then primary at the right. Mobile actions stack when needed; primary appears first in the visible action group, secondary follows with 8px spacing; visual and DOM focus order match. High-risk confirmation describes the patient/resource and effect; never label the final action merely Yes. Red is reserved for harmful/removal/reversal intent, not routine signing or administering.

### 6.3 Tabs, segmented controls and navigation

Default content tabs: white background, 48px minimum high, 16px horizontal padding per tab, 8px gap, 14/20px text. Inactive text text.secondary; hover surface.hover with text.primary; active text selected.fg at 600 plus a 3px selected.fg underline. Divider border.decorative is not the active indicator. Active state never relies on text colour alone. Focus is a separate visible outline and cannot be clipped by the tab container. Counts use neutral badges and remain available to assistive technology.

Use tabs only to switch related panels within one record, not to navigate different patients. Use real navigation links for separate pages. Do not combine an underline, gradient, glow, filled pill and coloured icon on the same tab. On mobile, use a labelled section selector when the tab set does not fit; preserve current section and keyboard navigation. No invisible offscreen selected tab. For genuine tab widgets use tablist/tab/tabpanel semantics with arrow navigation and manual activation when panel loading is slow.

Segmented controls are limited to two to four short alternatives such as List/Board. Container surface.subtle, 4px internal padding, 8px outer radius; segments at least 44px high, selected white with text.primary and a border.control outline; unselected text.secondary. Long translations switch to a select/radio group. Segments do not become compressed tiny pills.

Sidebar item: 48px minimum high, 12px side padding, 8px icon gap, 8px radius; 8px outer sidebar inset. Group heading 12/16px, white, sentence case. Sidebar selected indicator and white text are specified in section 5.1. Breadcrumbs 14/20px with underlined links, wrapping into additional lines when necessary. Do not hide patient context to preserve a one-line breadcrumb.

### 6.4 Forms — structure, state and spacing

Form content target width 960px maximum for general entry; narrative editors may use 1040px. Wide review comparisons use two labelled columns only where both are readable. Each group has a title, optional short instruction and fields with 16px horizontal/vertical gap. Use 24px between groups. Standard field label sits above the control, 8px gap; helper/error text 4px below. Required markers have explanatory text and semantic required attributes. A field is never represented by only an icon and placeholder.

Text/select/date control: minimum height 44px desktop, 48px mobile/bedside; padding 12px horizontally and enough vertical space for the 24px input line; border 1px control; radius 8px; white background. Textarea minimum height 120px, full width, user-resizable vertically on desktop. Do not make dosage or clinical narrative one-line scrolling fields. Input adornments reserve 20px plus 8px gap; units are visible adjacent text, not placeholder content. Search clear buttons have 44px hit areas and do not overlap text.

| State | Background | Border | Text / supporting treatment |
|---|---|---|---|
| Default | surface | border.control | text.primary; placeholder text.secondary |
| Hover | surface | brand.navy | No displacement |
| Focus | surface | action.primary | Independent focus outline with white gap |
| Invalid | surface | danger.fg, 2px using reserved space | danger.fg error message plus icon; retain label and value |
| Invalid and focused | surface | danger.fg | Focus outline remains blue; error persists |
| Read only | surface.subtle | border.control if rendered as input | text.primary; selectable/copyable; explicit read-only semantics |
| Disabled | neutral.bg | border.control | neutral.fg; reason nearby; do not disable a fact the user needs to copy |
| Pending external validation | surface | border.control | text.secondary status; no premature success tick |
| Verified value | surface | border.control | Ordinary field; verified marker only if meaningful and server-confirmed |

Reserve border geometry to prevent error/focus layout shifts; use inset outline or allocated space. Never decorate every valid field with a green border. Place an error summary at the top after failed submission and move focus to it or the first actionable error according to the workflow. Link summary items to fields. Clear errors only after relevant validation, not merely after any keystroke. Do not validate an untouched empty form with immediate red errors.

Checkbox/radio visual glyph: 20px, border.control outline, at least 44px clickable labelled region. Checked state action.primary with white check/dot. Mixed state has a visible dash and correct semantic state. Groups use fieldset/legend where appropriate. A switch is for a reversible immediate setting, not a substitute for consent, signature or medication authorization. Dropdown options are at least 44px high and wrap. Long lists support accessible search, keyboard selection, loading/empty/error states and visible code system/version where clinically needed. Avoid native date parsing assumptions across EN/FR.

Use two form columns only if each column has at least 280px usable width and the label/input content fits. At smaller widths, full-width fields. Long address, drug instruction, note, consent and attachment fields span both columns. The grid responds to available content width after navigation, not merely the screen's nominal breakpoint. Save actions remain reachable after keyboard appearance and validation expansion.

### 6.5 Cards, tables, dialogs and alerts

Cards: white, 1px decorative border, 12px radius, 24px desktop/16px mobile padding, no shadow. Title and body gap 12px. Related cards gap 16px; major sections 24px. Do not nest more than one card inside a card; use headings/dividers instead. Clickable cards have an explicit link/button and keyboard focus, never an undiscoverable whole-card click handler. Critical chart content does not hide in hover tooltips.

Table header: surface.subtle, text.secondary at 600, 48px minimum high. Body: white, 48px minimum rows; 12px vertical and 16px horizontal cell padding when content needs it. Hover surface.hover; selection selected.bg plus marker. Row height grows for wrapping. Numeric values align right; names and descriptions left; units remain paired. Header sorting controls have accessible names/state. No merged cells for visual decoration. A sticky header must not obscure keyboard focus or patient context. On small screens use the defined record-card view unless true two-dimensional comparison is essential.

Dialog: white, 12px radius, 24px desktop/16px mobile padding, labelled title 20/28px, 16px title/content gap, 24px content/actions gap. Backdrop only uses overlay.scrim, no blur. Simple dialog max width 560px; review max 960px; use viewport gutters and content max-height with scrolling. Complex mobile work uses a full-screen view. Close control has 44px hit area. Escape/close does not silently discard changes; apply the draft guard. Do not put another modal inside a modal.

Alerts: named semantic background/foreground, 4px semantic leading border, 20px icon, 12px icon/text gap, 16px padding, 8px radius. Text has meaningful heading and action. Critical alerts stay until resolved or acknowledged according to policy; do not auto-dismiss them. Informational toast may auto-dismiss only if it is redundant with persistent status, user preferences allow it, and it does not require action. Avoid several differently coloured alert blocks competing above a form; prioritize and group by clinical importance.

### 6.6 Screen composition and responsive dimensions

The default desktop composition is cream canvas, navy sidebar, white top bar, white patient-context strip and white working panels. Do not use a white sidebar on some modules and navy on others. Portal and operations shells may have different navigation structures, but tokens and components remain identical. User roles change accessible tasks, not the meaning of colour or the size of buttons.

| Property | Mobile <768px | Tablet 768–1199px | Desktop ≥1200px |
|---|---|---|---|
| Page gutter | 16px | 24px | 32px |
| Panel padding | 16px | 24px when fit, otherwise 16px | 24px |
| Section gap | 24px | 24px | 24px |
| Quick actions | 2 columns when readable; otherwise 1 | 2–3 columns when fit | Content-sized row or 3–4 columns |
| Form fields | 1 column; approved short paired controls only when fit | 1–2 columns with 280px minimum | 1–2 columns with 280px minimum |
| Buttons/inputs | 48px minimum | 44px; bedside 48px | 44px; bedside 48px |
| Task panels | Stacked | Stacked or split when fit | Main + secondary panel if meaningful |
| Navigation | Drawer; labelled bottom navigation where applicable | Collapsible rail/drawer | 248px sidebar |
| Clinical actions | Reachable full-width group when space constrained | Wrapping action group | Right-aligned action group |

Breakpoints do not authorize squeezing. At 200% text enlargement, a desktop shell may use the mobile arrangement. A French label is allowed to make a control taller. At 320px no ordinary page can require horizontal scrolling. Do not set fixed viewport heights on complete forms. Safe-area insets, virtual keyboard, landscape height and browser zoom are tested. Widths/heights describe minimums/targets rather than a license to crop.

### 6.7 Consistency enforcement and definition of clean

Clean means a consistent alignment grid, legible labels, enough separation, predictable hierarchy and no irrelevant decoration. It does not mean removing clinically necessary information. Fresh means white working surfaces, restrained cream framing, dark readable type and current-state labels; it does not mean pastel text or animated glows.

Mandatory implementation hierarchy: primitive tokens → semantic tokens → shared components → module compositions. A module cannot supply its own primary blue, new radius, arbitrary font family or 38px save button. Theme values are versioned centrally. Hard-coded colour values in module code are rejected except source diagnostic imagery and explicitly reviewed exceptions. Raw spacing must map to the spacing scale. Components carry visual regression snapshots for all variants; shared updates run regression checks across representative screens in every shell.

Before design acceptance verify: one clear page title; alignment to common content edges; quick actions above secondary statistics; no competing primary action in the current task; meaningful empty space; no duplicate labels; no unlabelled icons; no clipped text or overlapped controls; no random card colours; no gradients/glow; no logo distortion; no hidden critical facts; consistent hover/focus/disabled rules; correct mobile reflow and EN/FR expansion. Visual acceptance is separate from backend/clinical acceptance.

### 6.8 Component token implementation contract

The following CSS is a token declaration for web implementation, not a complete stylesheet. Native applications map the same semantic names to platform equivalents. All colour values here match section 5. States must be implemented by the shared components, not copied into each screen.

```css
:root {
  --sq-canvas: #F7F5F0;
  --sq-surface: #FFFFFF;
  --sq-surface-subtle: #F0EEE7;
  --sq-surface-hover: #F5F7FA;
  --sq-surface-pressed: #E2E8F0;
  --sq-navy: #073656;
  --sq-primary: #1664D4;
  --sq-primary-hover: #1253B1;
  --sq-primary-pressed: #0E438F;
  --sq-link: #0B4AA2;
  --sq-text: #172B4D;
  --sq-text-secondary: #475569;
  --sq-border: #DEDCD5;
  --sq-border-control: #64748B;
  --sq-selected-bg: #E8F0FE;
  --sq-selected-fg: #0B4AA2;
  --sq-nav-hover: #104766;
  --sq-nav-selected: #165A7A;
  --sq-info-bg: #EAF2FF;
  --sq-info-fg: #174EA6;
  --sq-success-bg: #E8F5EC;
  --sq-success-fg: #166534;
  --sq-warning-bg: #FFF4D6;
  --sq-warning-fg: #854D0E;
  --sq-danger-bg: #FDECEC;
  --sq-danger-fg: #B42318;
  --sq-danger-hover: #912018;
  --sq-danger-pressed: #781B14;
  --sq-neutral-bg: #EDF2F7;
  --sq-neutral-fg: #475569;
  --sq-focus: #1664D4;
  --sq-scrim: rgba(7, 54, 86, 0.40);
  --sq-font: Inter, system-ui, "Segoe UI", sans-serif;
  --sq-font-body: 1rem;
  --sq-font-label: 0.875rem;
  --sq-radius-control: 0.5rem;
  --sq-radius-panel: 0.75rem;
  --sq-control-min: 2.75rem;
  --sq-control-touch-min: 3rem;
  --sq-control-emphasis-min: 3.5rem;
  --sq-space-1: 0.25rem;
  --sq-space-2: 0.5rem;
  --sq-space-3: 0.75rem;
  --sq-space-4: 1rem;
  --sq-space-5: 1.25rem;
  --sq-space-6: 1.5rem;
  --sq-space-8: 2rem;
  --sq-space-10: 2.5rem;
  --sq-space-12: 3rem;
}
```

No global opacity styling for disabled sections; no fixed component height that clips scaled text. Colour values outside the token block are reference examples, not permission to duplicate constants throughout code. Chart tokens and any future theme are centrally registered using the same review process.

## 7. Responsive layout contracts

Breakpoints are project defaults: mobile below 768 CSS px; tablet 768–1199; desktop 1200 and above. Content fit and zoom override device labels. Baseline width checks: 320, 360, 390, 412, 768, 1024, 1280, 1440, 1920px. Test short landscape heights too. Rem units respect user text scaling.

Desktop: 248px sidebar, 64px global header minimum, growing context strip. Tablet: collapsible 72px labelled-on-expansion rail or drawer; content can reclaim width. Mobile: navigation drawer and, for patient/bedside tasks, at most four primary bottom destinations plus More. Bottom navigation includes labels, safe-area padding and does not cover actions. Long labels may increase navigation height. Header height grows for language/text scaling.

Mobile quick actions use a two-column grid when each item fits; long labels wrap. Clinical forms, long cards and primary workflows span full width. The user's two-column visual preference must never force two cramped clinical fields at 320px or enlarged text. Dashboard statistics can use two columns with sufficient width; otherwise stack. On desktop forms use two columns only for related short fields; narrative and medication instruction fields stay full width. Readable narrative line length target 60–80 characters; operational boards may use the full viewport.

No page-level horizontal scrolling for ordinary screens. Large true two-dimensional tables, timelines, odontograms and diagnostic images may have an explicitly labelled contained pan/scroll viewport plus an accessible list/table or task alternative where feasible. Do not shrink a whole schedule to fit a phone. Long operational tables become labelled record cards or a selected-column list; retain filters, sorting and access to all fields. Imaging interpretation is not declared mobile-capable merely because a viewer opens.

Dialogs: maximum 560px for simple tasks, 960px for comparison; longer work opens a full screen. Mobile complex dialogs become full-screen task views. Dialog content scrolls, but header/actions cannot conceal focus. Do not stack dialogs. Side panels require at least 360px usable width; otherwise use full screen. Sticky bars use measured heights and scroll padding, not fixed offsets that overlap wrapped content. Virtual keyboards must not cover the active field or commit action; preserve drafts during rotation and resizing.

## 8. Layout templates inherited by every screen

| Code | Desktop sections in order | Mobile adaptation | Standard secondary actions |
|---|---|---|---|
| H — Task home | Context, urgent tasks, quick actions, worklists, secondary statistics | Two-column quick actions where fit; tasks first; stats last | Filter scope; refresh; open related work |
| L — Worklist | Context/title, filters, results/count, pagination, record navigation | Filter drawer with active chips; labelled record cards | Search; clear filters; sort; open detail |
| D — Detail | Context/title/status, summary, sections/tabs, history, related records | Single column; section menu; no clipped tabs | Back to source; authorized related actions; history |
| F — Form | Context/title, prerequisites, grouped fields, review summary, action bar | Full-width long fields; stepper only for meaningful stages | Save permitted draft; cancel; contextual help |
| X — Review/decision | Context, current/proposed evidence, blockers, reason, explicit decision | Stack labelled versions; changes highlighted by text as well as colour | Reject/return; inspect source; cancel |
| B — Board/schedule | Context, time/location filters, lanes/grid, selected item details | Equivalent grouped list with filters; board optional | Change date/scope; open item; accessible move action |
| T — Timeline/analytics | Context, scope/time filters, labelled visual, accessible table, provenance | Stack plot and table; contained pan if essential | Change range; inspect point; authorized export |

Every register row names its primary action and domain-specific field groups. List fields are columns/card labels; detail fields are sections; form fields are inputs or sourced read-only facts; review fields are compared evidence. Do not turn all listed fields into editable text boxes. Each screen opens from its module worklist, relevant cross-module link or authorized deep link, and returns to the caller preserving safe filters/scroll. Primary actions either navigate to the named next task or execute the explicit domain transition; action labels must state which. Command response includes durable status and authoritative version; then refresh or fetch the resulting record. No generic Save label for signature, release, administration, refund or ownership transfer.

## 9. Universal interaction and state contract

S00 Initial: establish identity, role, tenant/cell, record scope and capability before sensitive data render. S01 Loading: skeletons reserve section space; announce loading once; commands are unavailable until prerequisites load. S02 Empty: distinguish no records, no search matches and filtered-out data; provide permitted next action. S03 Ready: show source timestamp/version and actionable state. S04 Dirty: explicit draft state and navigation guard. S05 Validating/submitting: one pending command per idempotency key, no accidental double effects. S06 Confirmed: show returned outcome/version and persistent status. S07 Validation error: error summary links to fields; preserve input, focus first actionable error. S08 Denied/expired session: protect data, recover safely through authentication and reauthorization. S09 Offline/stale: show actual permitted local capabilities and last known times. S10 Conflict: show current versus submitted values, preserve draft and require permitted resolution. S11 Unknown outcome: retain command reference, look up status; never tell users to repeat an irreversible action blindly. S12 Service failure: bounded retry, support reference and safe downtime route. S13 Retired/not found: no patient-existence leakage; safe return route.

All read screens implement S00–03 and S08–13. All mutation screens additionally implement S04–07. A state is marked not applicable only with a written reason. Async lists do not reorder the row under a pointer or keyboard focus; show New items available and apply on user action unless an approved emergency pattern applies. Preserve filters during polling. Live notifications use restrained accessible announcements, not repetitive screen-reader interruptions.

Drafts require explicit policy per screen: default in-memory session only; encrypted local drafts only on approved managed devices and permitted workflows, with TTL, wipe, provenance and separation of users. Do not put health data in localStorage, ordinary browser analytics, error replay recordings or unencrypted service-worker caches. Offline drafts are not authorized prescriptions or signed records. Authentication loss does not silently expose a previous user's drafts.

Form validation: required fields have labels and explanations; no placeholder-only labels. Preserve unknown, not asked, declined and not applicable where clinically meaningful. Numeric inputs display units adjacent to values and accept locale-aware entry only through validated parsing. Never silently round or convert medication values. Date/time inputs retain timezone and distinguish occurrence from recording time. Backdated actions are visibly marked and permission-controlled. Money uses explicit currency and exact backend decimal values. Status chips have text, not only red/green dots.

English/French: all UI strings use translation keys, including errors, dates, plurals, status, print and accessible labels. Test French expansion at least 40% beyond short English labels and actual accented names; no flag icons as language selectors. Search supports accented names without changing authoritative spelling. Store canonical codes separately from localized displays. Locale change does not modify clinical data or measurement units automatically.

## 10. Icons and component catalogue

Use **Lucide** via named imports from pinned web/native packages. Default size 20px, navigation 22px, prominent standalone icon 24px; stroke width 2, no arbitrary fills. Decorative icons beside text are hidden from assistive technology; icon-only controls need accessible names and a visible tooltip available to keyboard focus. Essential actions retain visible text. Import checks must verify every selected icon exists in the pinned package; if an export differs, propose a documented alias rather than silently swapping artwork.

| Meaning | Lucide component |
|---|---|
| Search / filter | Search / ListFilter |
| Add / edit / inspect | Plus / Pencil / Eye |
| Back / forward / expand | ArrowLeft / ArrowRight / ChevronDown |
| Save draft / sign reviewed content | Save / Signature |
| Confirm / cancel / close | Check / Ban / X |
| Delete disposable draft only | Trash2 |
| Print / download / upload | Printer / Download / Upload |
| History / copy identifier | History / Copy |
| Help / information | CircleHelp / Info |
| Warning / urgent error | TriangleAlert / CircleAlert |
| Success / pending | CircleCheck / Clock |
| Connected / disconnected / sync | Wifi / WifiOff / RefreshCw |
| Locked / access policy | LockKeyhole / ShieldCheck |
| More actions / navigation | Ellipsis / Menu |
| Notifications / language | Bell / Languages |

Module navigation icons are specified in each module register below. No icon is a clinical diagnosis or severity classification by itself.

Reusable components: C01 application shell; C02 patient context; C03 encounter context; C04 role/task header; C05 action toolbar; C06 filter panel; C07 accessible data table; C08 record card; C09 form field/error; C10 terminology selector; C11 measurement with unit; C12 status badge; C13 alert; C14 timeline; C15 clinical note editor; C16 version comparison; C17 signature review; C18 provenance display; C19 connectivity/freshness strip; C20 pagination; C21 chart plus data table; C22 file attachment; C23 empty state; C24 command status; C25 proxy banner; C26 print document shell. Each component requires its own visual and interaction states in the component gallery before reuse.

Registered dialog variants: D01 unsaved work (save permitted draft/discard/cancel); D02 patient switch verification; D03 explicit high-risk confirmation; D04 correction reason; D05 stale-version comparison; D06 session expiry; D07 access request; D08 scoped export with authority; D09 attachment preview; D10 server-confirmed outcome/status lookup; D11 date/resource picker; D12 keyboard shortcuts/help. Screen identity merge, prescription authorization and complex handover remain full review screens, not tiny generic confirmations. These variants do not add to the screen count.

## 11. Canonical screen register

All module roles below describe eligible personas, not blanket permissions. Specific actions require the authorization matrix and server checks. Defaults: patient-specific records require current patient scope; workforce/finance/governance records have their own purpose and resource scopes. Clinical critical actions are online-to-authoritative-owner only; a hospital-local authoritative service counts as available during WAN loss. The device being offline from that service does not permit the action.

Each row is a complete baseline composition of its named fields/action with sections 3–10 and the module workflow. Screen-specific clinical/business validation still requires the release gates in section 16. No row is marked implemented or tested.

### Shared screens

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| G-S01 | Sign in | F | Organization, identity, authentication method, local service availability | Authenticate |
| G-S02 | MFA and account recovery | F | Challenge, approved recovery method, expiry and safe retry | Verify challenge |
| G-S03 | Facility role and shift selection | F | Permitted facility, role, shift and active scope | Enter selected workspace |
| G-S04 | Role home and task hub | H | Assigned work, urgent exceptions, quick actions, service status and secondary statistics | Open assigned task |
| G-S05 | Global patient search | L | Identifiers, demographics, scope and permitted matching results | Open verified patient context |
| G-S06 | Personal settings and accessibility | F | Language, time display, density, accessibility preferences and notification choices | Save preferences |
| G-S07 | Session lock and reauthentication | F | Named account, safe draft status, reason and reauthentication | Resume session |
| G-S08 | Access denied and access request | D | Safe reason, request reference, support route and permitted access request | Request authorized review |
| G-S09 | Emergency access request | X | Named actor, patient scope, permitted reason, duration and acknowledgement | Activate policy-approved access |
| G-S10 | Connectivity and service interruption | D | Affected services, local capability, pending work, timestamps and approved downtime procedure | Open available local workflow |
| G-S11 | Help and support | D | Contextual guide, accessible help, support contact and redacted incident reference | Open support request |
| G-S12 | Not found or retired feature | D | Safe navigation context, replacement route where authorized and support reference | Return to permitted workspace |

### M01 — Patient registration and master identity

Eligible personas: **Registration officer; identity steward**. Navigation icon: **Users**. Screens: **6**.

Workflow: Search before registration; possible matches require comparison; provisional identities remain visibly provisional; merge requires independent review and must preserve both histories.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M01-S01 | Patient search | L | Identifier, name, birth date or estimated age, contact, match confidence | Open selected patient |
| M01-S02 | New patient registration | F | Names, identifiers and issuer, birth date or estimated flag, relevant demographic fields, contacts, address, language | Register patient |
| M01-S03 | Patient identity profile | D | Identifiers, demographic source, contacts, verification status, duplicate flags | Request demographic correction |
| M01-S04 | Demographic correction | F | Current and proposed values, source, reason, effective time | Submit correction |
| M01-S05 | Duplicate identity review | X | Candidate records, distinguishing identifiers, encounters, merge impact | Propose merge |
| M01-S06 | Merge approval and lineage | X | Survivor, absorbed record, reference changes, approvals, reversal history | Approve reviewed merge |

### M02 — Appointments and resource scheduling

Eligible personas: **Scheduler; department coordinator**. Navigation icon: **CalendarDays**. Screens: **5**.

Workflow: Search availability, book, confirm, arrive and fulfil; conflicting reservations return alternatives; cancellation and no-show retain reasons.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M02-S01 | Appointment calendar | B | Date, clinician, room, service, occupancy and status | Select available slot |
| M02-S02 | Appointment booking | F | Patient, service, resource, time zone, duration, referral, contact preference | Book appointment |
| M02-S03 | Appointment detail | D | Booking, reminders, arrival, resource changes and history | Confirm arrival |
| M02-S04 | Waitlist and rescheduling | L | Priority, preferred dates, offers, expiry, accepted offer | Offer replacement slot |
| M02-S05 | Resource availability | F | Working hours, exceptions, capacity, approved overbooking policy | Publish availability |

### M03 — Triage emergency intake and queues

Eligible personas: **Triage nurse; emergency clinician**. Navigation icon: **Activity**. Screens: **5**.

Workflow: Identify patient or create provisional identity, assess, assign approved priority, hand over; reassessment and overdue escalation remain explicit.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M03-S01 | Triage queue | B | Patient identifiers, arrival, priority label, wait duration, assigned staff | Start assessment |
| M03-S02 | Emergency intake | F | Provisional identity, arrival mode, presenting concern, immediate disposition | Open emergency encounter |
| M03-S03 | Triage assessment | F | Symptoms, observations with units, approved score inputs, red flags, assessment time | Submit triage assessment |
| M03-S04 | Triage reassessment | F | Previous assessment, changed observations, reason, new approved priority | Record reassessment |
| M03-S05 | Queue assignment and handover | X | Destination, receiving clinician, readiness, acknowledgement | Assign and request handover |

### M04 — Encounters ADT and beds

Eligible personas: **ADT officer; ward coordinator; clinician**. Navigation icon: **BedDouble**. Screens: **7**.

Workflow: Create encounter, admit or assign, transfer with receiving acknowledgement, discharge plan then authorized discharge; never imply bed assignment on a failed reservation.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M04-S01 | Encounter worklist | L | Patient, type, department, clinician, status and start time | Open encounter |
| M04-S02 | Encounter overview | D | Identity banner, active problems, care team, timeline, orders and disposition | Open clinical workspace |
| M04-S03 | Admission assessment | F | Admission reason, clinician, ward, infection precautions, payer reference | Request admission |
| M04-S04 | Bed board | B | Ward, bed, availability, cleaning, isolation constraints, reservation | Reserve eligible bed |
| M04-S05 | Transfer coordination | X | Current and destination location, handover, outstanding tasks, receiving acceptance | Confirm accepted transfer |
| M04-S06 | Discharge planning | F | Readiness, medications, pending results, follow-up, instructions and responsible clinician | Submit discharge plan |
| M04-S07 | Discharge authorization | X | Approved plan, unresolved risks, discharge time, signature | Authorize discharge |

### M05 — Clinical notes and diagnoses

Eligible personas: **Clinician; authorized clinical records reviewer**. Navigation icon: **FileText**. Screens: **5**.

Workflow: Draft and review before signing; signatures preserve original content; corrections use linked amendments, never silent overwrite.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M05-S01 | Clinical workspace | D | Patient banner, encounter timeline, problems, allergies, current orders, relevant results | Start clinical note |
| M05-S02 | Note editor | F | Template version, history, examination, assessment, plan, author and draft status | Save draft |
| M05-S03 | Diagnosis and problem list | L | Code system, code, description, status, onset, certainty and author | Add diagnosis |
| M05-S04 | Note signing review | X | Rendered complete note, diagnoses, author, encounter and missing mandatory items | Sign note |
| M05-S05 | Signed note and amendments | D | Original signature, amendments, reason, author and timestamps | Create linked amendment |

### M06 — Observations and vitals

Eligible personas: **Nurse; clinician; verified device reviewer**. Navigation icon: **HeartPulse**. Screens: **5**.

Workflow: Capture measurement and provenance, verify, then publish; suspect device values need review; corrections retain original values.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M06-S01 | Observation capture | F | Type, value, unit, measurement time, method, source, patient and encounter | Record observation |
| M06-S02 | Observation verification queue | L | Unverified values, source, unit mismatch and plausibility flags | Review measurement |
| M06-S03 | Observation verification | X | Raw and normalized value, device association, source time, reviewer | Verify observation |
| M06-S04 | Longitudinal measurements | T | Values, units, reference intervals, gaps, corrections and time range | Inspect selected measurement |
| M06-S05 | Observation correction | F | Original value, corrected value, reason and verification | Submit correction |

### M07 — Allergies intolerances and reconciliation

Eligible personas: **Clinician; authorized nurse; pharmacist reviewer**. Navigation icon: **ShieldAlert**. Screens: **5**.

Workflow: Distinguish not assessed, patient reports none, and known allergies; review each entry and source; reconcile through authoritative commands.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M07-S01 | Allergy summary | D | Assessment status, substances, reactions, severity, verification, source and latest review | Start reconciliation |
| M07-S02 | Allergy entry | F | Terminology system and code, substance, reaction, severity, onset, source and verification | Record allergy |
| M07-S03 | Allergy reconciliation | X | Existing and incoming entries, duplicates, discrepancies, reviewed source | Confirm reconciliation |
| M07-S04 | Allergy detail and history | D | Clinical and verification status, prior revisions, author and reason | Request status correction |
| M07-S05 | Allergy status correction | F | Existing status, proposed status, entered-in-error reason and source | Submit correction |

### M08 — Medication prescribing and reconciliation

Eligible personas: **Prescriber; authorized medication reviewer**. Navigation icon: **Pill**. Screens: **7**.

Workflow: Reconcile history, draft order, evaluate current safety inputs, review and authorize; hold/cancel and reassessment are explicit branches.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M08-S01 | Medication worklist | L | Active orders, review-required flags, status, prescriber and fulfilment | Open medication order |
| M08-S02 | Medication reconciliation | X | Reported, prescribed and external medications, source, last dose, discrepancies | Confirm reconciliation |
| M08-S03 | Prescription editor | F | Drug and formulation, dose and unit, route, frequency, duration, indication, weight when required | Request safety assessment |
| M08-S04 | Medication safety review | X | Input freshness, allergy and interaction findings, missing facts, rule version, approved override fields | Authorize reviewed order |
| M08-S05 | Medication order detail | D | Signed prescription, safety revision, fulfilment, administrations, changes | Request order change |
| M08-S06 | Medication hold and cancellation | F | Reason, effective time, pending dispensing, already released doses | Submit hold or cancellation |
| M08-S07 | Medication reassessment queue | L | Changed inputs, affected active orders, priority and responsible prescriber | Reassess affected order |

### M09 — Medication administration

Eligible personas: **Nurse; authorized administering clinician**. Navigation icon: **ClipboardCheck**. Screens: **5**.

Workflow: Select due dose, identify patient and medication, complete required checks, administer and document; omitted/refused/held are distinct outcomes.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M09-S01 | Medication administration record | B | Due doses, actual times, status text, route, late and held markers | Open due dose |
| M09-S02 | Bedside dose verification | X | Patient identifiers, dose ID, medication, barcode result, route, current order and safety status | Confirm required checks |
| M09-S03 | Administration recording | F | Actual dose and unit, route, administration time, site, actor, witness when required | Record administration |
| M09-S04 | Omitted refused or held dose | F | Dose ID, outcome category, reason, clinical escalation and review time | Record non-administration |
| M09-S05 | Administration correction | X | Original record, proposed correction, reason, witness and audit history | Submit linked correction |

### M10 — Nursing care plans and handovers

Eligible personas: **Nurse; ward lead**. Navigation icon: **ClipboardList**. Screens: **5**.

Workflow: Assess, plan, assign, perform and review; handover is complete only after receiver acknowledgement.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M10-S01 | Ward nursing worklist | L | Patients, assigned nurse, acuity, tasks, overdue care and precautions | Open care plan |
| M10-S02 | Nursing care plan | F | Needs, goals, interventions, frequency, responsible staff and review date | Publish care plan |
| M10-S03 | Nursing task execution | F | Task, observations, completion or exception, time and escalation | Record task outcome |
| M10-S04 | Shift handover | X | Patient summary, outstanding risks, tasks, receiver and acknowledgement | Submit handover |
| M10-S05 | Care plan review | X | Progress, unmet goals, changes and approving nurse | Approve revised plan |

### M11 — Laboratory specimens and results

Eligible personas: **Laboratory staff; verifier; ordering clinician**. Navigation icon: **FlaskConical**. Screens: **9**.

Workflow: Order, accept, collect, receive, process, verify and release; rejected specimens require explicit recollection; critical results require acknowledgement tracking.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M11-S01 | Laboratory orders | L | Patient, tests, priority, specimen requirements and order status | Open laboratory request |
| M11-S02 | Laboratory request | F | Tests, indication, collection constraints, ordering clinician and priority | Submit laboratory request |
| M11-S03 | Specimen collection | F | Patient verification, specimen ID, type, container, collector and collection time | Confirm collection and print label |
| M11-S04 | Specimen receipt and rejection | X | Specimen ID, condition, transport time, acceptance or rejection reason | Record receipt decision |
| M11-S05 | Laboratory bench worklist | L | Accepted specimens, tests, analyzer, processing and quality flags | Open result entry |
| M11-S06 | Result entry | F | Analyte, value, unit, reference interval, method, flags and source | Submit for verification |
| M11-S07 | Result verification and release | X | Results, specimen provenance, quality checks, delta flags and verifier | Verify and release report |
| M11-S08 | Critical result acknowledgement | L | Critical finding, recipient, notification attempts, acknowledgement and escalation | Record clinical acknowledgement |
| M11-S09 | Laboratory report and amendment | D | Released report, signature, corrected results and amendment lineage | Create report amendment |

### M12 — Imaging radiology and PACS

Eligible personas: **Radiographer; radiologist; ordering clinician**. Navigation icon: **ScanLine**. Screens: **7**.

Workflow: Request and schedule, perform identity/procedure checks, acquire, report and verify; viewer failures do not imply missing studies.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M12-S01 | Imaging requests | L | Patient, procedure, priority, screening and status | Open imaging request |
| M12-S02 | Imaging request and screening | F | Procedure, indication, approved contraindication questions, contrast requirements | Submit imaging request |
| M12-S03 | Imaging schedule | B | Modality, room, slot, preparation and patient arrival | Book examination |
| M12-S04 | Acquisition worklist | L | Verified patient, accession, procedure, modality and acquisition status | Confirm acquisition outcome |
| M12-S05 | Study viewer launch | D | Accession, patient identifiers, series metadata, PACS availability | Open authorized PACS viewer |
| M12-S06 | Radiology report editor | F | Findings, impression, comparison, author and report version | Submit report for verification |
| M12-S07 | Radiology verification and amendment | X | Report, study references, reviewer, signature and amendment reason | Verify report |

### M13 — Pharmacy dispensing and inventory

Eligible personas: **Pharmacist; pharmacy stock officer**. Navigation icon: **Package**. Screens: **7**.

Workflow: Reserve stock, request clinical release, prepare, consume valid ticket then hand over; unknown consume response blocks repeat and enters reconciliation.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M13-S01 | Dispensing queue | L | Patient, authorized prescription, priority, reservation and release status | Open dispensing workbench |
| M13-S02 | Dispensing workbench | F | Order, quantity, batch, expiry, substitution approval, preparation checks | Reserve selected stock |
| M13-S03 | Clinical release review | X | Dispense ID, order version, safety review, ticket expiry and current decision | Consume release authorization |
| M13-S04 | Dispensing handover | X | Durable authorization, patient or recipient check, quantity and instructions | Confirm physical handover |
| M13-S05 | Dispensing exceptions | X | Unknown response, consumed ticket, actual handover evidence, stock discrepancy | Submit reconciled outcome |
| M13-S06 | Medication stock and expiry | L | Product, batch, location, available and reserved quantity, expiry | Open batch record |
| M13-S07 | Medication receipt return and adjustment | F | Movement type, batch, quantity, source, reason and approval | Submit stock movement |

### M14 — Surgery theatre anesthesia and recovery

Eligible personas: **Surgeon; anesthetist; theatre nurse**. Navigation icon: **Scissors**. Screens: **6**.

Workflow: Book, assess, verify consent and checklist, perform, recover and hand over; emergency exceptions require approved recording paths.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M14-S01 | Theatre schedule | B | Procedure, patient, team, room, readiness and time | Open theatre case |
| M14-S02 | Surgical booking | F | Procedure, laterality, surgeon, resource needs and priority | Submit booking |
| M14-S03 | Preoperative readiness | X | Identity, procedure, site, consent, investigations, equipment and required checklist | Confirm readiness review |
| M14-S04 | Anesthesia assessment and record | F | Assessment, approved monitoring fields, medications, times and clinician | Record anesthesia entry |
| M14-S05 | Intraoperative record | F | Procedure, team, counts, specimens, events and completion time | Record operative outcome |
| M14-S06 | Recovery and handover | X | Recovery observations, approved discharge criteria, destination and receiver | Complete recovery handover |

### M15 — Blood bank and transfusion

Eligible personas: **Blood bank staff; authorized transfusion clinician**. Navigation icon: **Droplet**. Screens: **6**.

Workflow: Register unit, screen, inventory, crossmatch, reserve and issue; bedside identity and unit checks precede administration; reactions trigger escalation.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M15-S01 | Blood unit inventory | L | Unit identifier, product, group, status, expiry and storage | Open blood unit |
| M15-S02 | Blood unit intake and screening | F | Origin, collection, component, screening results, quarantine and reviewer | Submit screening review |
| M15-S03 | Compatibility and reservation | X | Patient, request, samples, compatibility results, selected unit and expiry | Reserve compatible unit |
| M15-S04 | Blood unit issue and return | F | Unit, patient, issue time, custody, transport and return condition | Record unit movement |
| M15-S05 | Bedside transfusion verification | X | Patient identifiers, unit ID, product, prescription, compatibility and witness | Confirm transfusion checks |
| M15-S06 | Transfusion monitoring and reaction | F | Start and finish, observations, volume, suspected reaction and escalation | Record transfusion outcome |

### M16 — Referrals and care transitions

Eligible personas: **Referring clinician; receiving coordinator**. Navigation icon: **ArrowRightLeft**. Screens: **5**.

Workflow: Prepare referral, seek acceptance, hand over and track attendance; rejection returns ownership to referring team.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M16-S01 | Referral worklist | L | Patient, destination, urgency, sender, status and response deadline | Open referral |
| M16-S02 | Referral preparation | F | Reason, summary, current treatment, attachments, recipient and disclosure basis | Send authorized referral |
| M16-S03 | Referral acceptance review | X | Clinical request, capacity, responsible receiver, acceptance or rejection reason | Record receiving decision |
| M16-S04 | Transition handover | X | Transport, clinical risks, outstanding results, receiver and acknowledgement | Confirm handover |
| M16-S05 | Referral follow-up and closure | F | Attendance, returned advice, unresolved tasks and closure reason | Close reviewed referral |

### M17 — Specialty clinical packages

Eligible personas: **Specialty clinician; specialty nurse**. Navigation icon: **Stethoscope**. Screens: **28**.

Workflow: Use specialty workspace to enrol, assess, document treatment and review; shared orders remain owned by prescribing, laboratory and imaging modules.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M17-S01 | Specialty service worklist | L | Service, patients, visit type, due follow-up and responsible clinician | Open specialty episode |
| M17-S02 | Specialty episode enrolment | F | Specialty, referral, episode dates, team and approved pathway | Enrol patient |
| M17-S03 | Specialty longitudinal overview | D | Episode, assessments, shared orders, outcomes and follow-up | Open specialty assessment |
| M17-S04 | Specialty outcome and closure | F | Outcome measures, transfer, follow-up and closure reason | Close specialty episode |
| M17-P01-S01 | Obstetrics and gynecology assessment | F | pregnancy episode, dating source, obstetric history, antenatal observations and approved care pathway | Save specialty assessment |
| M17-P01-S02 | Obstetrics and gynecology treatment record | F | maternal and fetal observations, labour events or gynecologic procedure, team and timing | Submit reviewed specialty record |
| M17-P01-S03 | Obstetrics and gynecology follow-up | F | postnatal or gynecologic outcomes, newborn link where applicable, follow-up and escalation | Record specialty follow-up |
| M17-P02-S01 | Pediatrics and neonatology assessment | F | guardian authority, birth history, age calculation, growth measurements and approved charts | Save specialty assessment |
| M17-P02-S02 | Pediatrics and neonatology treatment record | F | weight source and time, feeding, development, neonatal observations and shared treatment orders | Submit reviewed specialty record |
| M17-P02-S03 | Pediatrics and neonatology follow-up | F | growth trend, immunization record sources, developmental follow-up and guardian instructions | Record specialty follow-up |
| M17-P03-S01 | Cardiology assessment | F | cardiac history, symptoms, observations, investigations and approved assessments | Save specialty assessment |
| M17-P03-S02 | Cardiology treatment record | F | ECG or study references, procedure details, shared orders and monitoring | Submit reviewed specialty record |
| M17-P03-S03 | Cardiology follow-up | F | symptoms, outcomes, monitoring plan and follow-up | Record specialty follow-up |
| M17-P04-S01 | Dentistry assessment | F | tooth notation system, odontogram, history, findings and consent | Save specialty assessment |
| M17-P04-S02 | Dentistry treatment record | F | tooth or surface, procedure, materials, local anesthesia reference and team | Submit reviewed specialty record |
| M17-P04-S03 | Dentistry follow-up | F | healing, complications, hygiene advice and recall | Record specialty follow-up |
| M17-P05-S01 | Dermatology assessment | F | lesion location, findings, image consent, history and diagnosis | Save specialty assessment |
| M17-P05-S02 | Dermatology treatment record | F | procedure, specimen link, shared orders and treatment record | Submit reviewed specialty record |
| M17-P05-S03 | Dermatology follow-up | F | lesion changes, treatment response, images with consent and follow-up | Record specialty follow-up |
| M17-P06-S01 | Endocrinology and chronic disease assessment | F | condition, longitudinal markers, relevant observations and risk assessment | Save specialty assessment |
| M17-P06-S02 | Endocrinology and chronic disease treatment record | F | agreed goals, shared medication orders, education and monitoring | Submit reviewed specialty record |
| M17-P06-S03 | Endocrinology and chronic disease follow-up | F | trends, adherence discussion, complications and follow-up | Record specialty follow-up |
| M17-P07-S01 | Oncology assessment | F | diagnostic evidence, pathology, stage source, multidisciplinary decision and protocol | Save specialty assessment |
| M17-P07-S02 | Oncology treatment record | F | approved regimen reference, cycle, prerequisites, independent checks and shared orders | Submit reviewed specialty record |
| M17-P07-S03 | Oncology follow-up | F | response criteria, toxicity assessment, supportive care and follow-up | Record specialty follow-up |
| M17-P08-S01 | Rehabilitation assessment | F | functional status, approved assessment scales, goals and precautions | Save specialty assessment |
| M17-P08-S02 | Rehabilitation treatment record | F | session plan, intervention, assistance, tolerance and therapist | Submit reviewed specialty record |
| M17-P08-S03 | Rehabilitation follow-up | F | functional change, goals, aids, home plan and discharge readiness | Record specialty follow-up |

### M18 — Patient portal mobile and proxy access

Eligible personas: **Patient; verified proxy**. Navigation icon: **UserRound**. Screens: **9**.

Workflow: Authenticate, select self or authorized dependent, access permitted records and services; proxy scope and expiry are checked for each request.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M18-S01 | Patient home | H | Upcoming care, pending tasks, released results and communication preferences | Open next care task |
| M18-S02 | My health record | D | Released notes, allergies, medications, reports, provenance and access limits | Open released document |
| M18-S03 | Patient appointment booking | F | Service, location, available slots, contact and preparation information | Request appointment |
| M18-S04 | Patient appointment detail | D | Status, location, preparation, cancellation policy and check-in eligibility | Check in when eligible |
| M18-S05 | Patient messages | L | Care-team threads, response expectations, unread status and attachments | Open secure conversation |
| M18-S06 | Patient message composer | F | Recipient team, subject, text, attachments and emergency-use notice | Send message |
| M18-S07 | Proxy access management | X | Dependent, relationship evidence, permitted scope, expiry and revocation | Request or revoke proxy grant |
| M18-S08 | Patient bills and payments | D | Invoices, payments, outstanding balance, currency and receipt | Start payment |
| M18-S09 | Patient preferences and correction requests | F | Language, contact channels, privacy preferences and requested correction | Submit preference or correction |

### M19 — Charges invoices cashier and payments

Eligible personas: **Cashier; billing officer; finance approver**. Navigation icon: **Receipt**. Screens: **7**.

Workflow: Post charges, issue invoice, take payment and reconcile; refunds and reversals retain lineage and authorization.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M19-S01 | Billing account | D | Patient, encounter, itemized charges, payer shares, balances and currency | Review draft invoice |
| M19-S02 | Charge capture | F | Service, quantity, approved tariff, discount basis, encounter and source | Post charge |
| M19-S03 | Invoice review and issue | X | Line items, totals, tax configuration, payer allocation and approvals | Issue invoice |
| M19-S04 | Cashier payment | F | Invoice, amount, currency, tender, payment reference and received amount | Submit payment |
| M19-S05 | Payment receipt | D | Confirmed payment, allocated invoices, balance, receipt number and issuer | Print receipt |
| M19-S06 | Refund and reversal | X | Original transaction, amount, reason, approvals and settlement status | Submit authorized reversal |
| M19-S07 | Cashier shift reconciliation | X | Opening balance, tenders, receipts, counted cash, variance and approver | Close reconciled shift |

### M20 — Insurance payer authorization and claims

Eligible personas: **Claims officer; payer liaison**. Navigation icon: **BadgeCheck**. Screens: **6**.

Workflow: Verify eligibility, seek authorization, assemble claim, submit and reconcile adjudication; denied and appealed cases retain versions.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M20-S01 | Payer eligibility review | X | Policy, beneficiary, coverage dates, source response and exclusions | Record eligibility decision |
| M20-S02 | Payer authorization request | F | Services, clinical support, estimate, payer and requested dates | Submit authorization request |
| M20-S03 | Authorization tracking | L | Requests, response deadlines, approvals, denials and limits | Open authorization |
| M20-S04 | Claim preparation | F | Encounter, codes, itemized charges, evidence and authorization references | Validate claim |
| M20-S05 | Claim submission and adjudication | D | Submitted version, payer responses, remittance, denials and status | Record adjudication |
| M20-S06 | Claim appeal and settlement | X | Denied items, grounds, supporting evidence, approved amounts and payments | Submit appeal or reconciliation |

### M21 — Procurement and suppliers

Eligible personas: **Requester; procurement officer; approver**. Navigation icon: **ShoppingCart**. Screens: **6**.

Workflow: Requisition, approve, order, receive and match; requester cannot approve their own restricted purchase.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M21-S01 | Procurement requisitions | L | Requester, department, items, value, urgency and approval status | Open requisition |
| M21-S02 | Requisition editor | F | Items, quantities, justification, budget reference and delivery need | Submit requisition |
| M21-S03 | Purchase approval | X | Request, budget, quotations, conflicts of interest and approver | Approve requisition |
| M21-S04 | Purchase order | F | Supplier, items, price, terms, delivery dates and approval | Issue purchase order |
| M21-S05 | Goods receipt and invoice matching | X | Order, delivered quantities, condition, receipt and supplier invoice | Record matched receipt |
| M21-S06 | Supplier directory and qualification | D | Supplier identity, contacts, terms, qualification documents and status | Update supplier qualification |

### M22 — General stores inventory

Eligible personas: **Storekeeper; stock approver**. Navigation icon: **Boxes**. Screens: **5**.

Workflow: Receive, inspect, make available, issue and count; adjustments require explained movements.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M22-S01 | Stores inventory | L | Item, location, available, reserved, reorder and unit | Open stock item |
| M22-S02 | Stores receipt | F | Item, quantity, unit, supplier, document, location and inspection | Receive goods |
| M22-S03 | Stock request and issue | F | Requesting unit, items, quantities, destination and approval | Issue approved stock |
| M22-S04 | Stock transfer and return | F | Source, destination, custody, condition and quantity | Record movement |
| M22-S05 | Stock count and adjustment | X | Expected quantity, counted quantity, variance, reason and approver | Approve adjustment |

### M23 — Assets biomedical maintenance and facilities

Eligible personas: **Biomedical engineer; facilities officer**. Navigation icon: **Wrench**. Screens: **5**.

Workflow: Register, commission, maintain and release asset; unsafe equipment is blocked from allocation.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M23-S01 | Asset register | L | Asset ID, type, location, operational status and next maintenance | Open asset |
| M23-S02 | Asset detail and commissioning | D | Serial, supplier, warranty, installation, commissioning evidence and safety status | Record commissioning decision |
| M23-S03 | Maintenance work orders | L | Asset, fault, severity, technician, due time and downtime | Open work order |
| M23-S04 | Maintenance execution | F | Findings, work, parts, calibration, tests and technician | Submit maintenance record |
| M23-S05 | Asset return to service or retirement | X | Safety tests, reviewer, restrictions, disposal evidence and status | Approve asset disposition |

### M24 — HR staffing and payroll

Eligible personas: **HR officer; rostering lead; payroll approver**. Navigation icon: **BriefcaseBusiness**. Screens: **6**.

Workflow: Maintain employment, roster, approve attendance, calculate and approve payroll; clinical privilege approval remains M33.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M24-S01 | Staff directory | L | Staff ID, department, role, employment status and contacts | Open staff profile |
| M24-S02 | Employment record | F | Contract, assignment, dates, confidential documents and HR access scope | Save employment change |
| M24-S03 | Staff roster | B | Shift, staff, coverage, leave and conflicts | Publish reviewed roster |
| M24-S04 | Attendance and leave review | X | Recorded time, leave, exceptions, corrections and approver | Approve attendance |
| M24-S05 | Payroll preparation | F | Period, approved inputs, earnings, deductions, currency and exceptions | Submit payroll batch |
| M24-S06 | Payroll approval and payment reconciliation | X | Totals, variances, authorizations, payment references and failures | Approve payroll release |

### M25 — Transport and ambulance

Eligible personas: **Dispatcher; ambulance team**. Navigation icon: **Ambulance**. Screens: **5**.

Workflow: Request, assign, dispatch, travel, receive and close; transport tracking must expose stale location.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M25-S01 | Transport dispatch board | B | Requests, urgency, vehicles, crew, availability and position timestamp | Assign transport |
| M25-S02 | Transport request | F | Patient, pickup, destination, clinical needs, time and requester | Submit transport request |
| M25-S03 | Dispatch assignment | F | Vehicle, crew, route, equipment checks and receiving site | Dispatch assigned vehicle |
| M25-S04 | Transport execution and handover | F | Departure, arrival, events, care notes, receiver and acknowledgement | Complete handover |
| M25-S05 | Transport history and exceptions | D | Trip timeline, delays, cancellations, vehicle issues and escalations | Record exception resolution |

### M26 — Catering and dietary orders

Eligible personas: **Dietitian; kitchen coordinator; ward staff**. Navigation icon: **Utensils**. Screens: **4**.

Workflow: Prescribe diet, reconcile restrictions, prepare, deliver and acknowledge; bed moves and fasting changes trigger review.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M26-S01 | Dietary patient worklist | L | Patient, location, diet, restrictions, fasting and last update | Open dietary order |
| M26-S02 | Dietary order | F | Approved diet, restrictions, texture, effective times and prescriber | Submit dietary order |
| M26-S03 | Meal production plan | B | Meal, ward, portions, restrictions and reviewed cutoff | Release production plan |
| M26-S04 | Meal delivery and acknowledgement | F | Patient identification, tray, current restrictions, delivery time and exception | Record delivery outcome |

### M27 — Mortuary and decedent release

Eligible personas: **Mortuary officer; authorized release approver**. Navigation icon: **Archive**. Screens: **5**.

Workflow: Receive, establish identity, store, authorize and release with documented chain of custody.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M27-S01 | Mortuary register | L | Decedent ID, received time, storage, identification and release status | Open custody record |
| M27-S02 | Mortuary intake | F | Identity evidence, originating unit, authorizing document, belongings and receiving staff | Record intake |
| M27-S03 | Storage and custody transfer | F | Current and destination storage, seals, actors, time and condition | Record custody transfer |
| M27-S04 | Decedent release review | X | Authority, recipient evidence, required documents, dual checks and approvals | Authorize release |
| M27-S05 | Decedent handover record | F | Recipient verification, authorization, belongings, witnesses and handover time | Confirm release |

### M28 — Hospital operations and BI

Eligible personas: **Hospital director; authorized analyst**. Navigation icon: **ChartNoAxesCombined**. Screens: **5**.

Workflow: Select authorized scope, inspect freshness and definitions, drill down and export with disclosure controls.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M28-S01 | Hospital operations dashboard | H | Service activity, capacity, finance summaries, definitions and freshness | Open operational worklist |
| M28-S02 | Clinical service analytics | T | Aggregated measures, filters, denominators, exclusions and reporting period | Inspect measure breakdown |
| M28-S03 | Financial analytics | T | Revenue, receivables, payments, reconciliation and currency | Inspect financial breakdown |
| M28-S04 | Report catalogue and execution | L | Report definitions, allowed scope, parameters and refresh schedule | Run authorized report |
| M28-S05 | Report export and history | D | Requested scope, format, approval, expiry and completed exports | Download authorized export |

### M29 — Facility department location and services

Eligible personas: **Facility administrator; configuration approver**. Navigation icon: **Building2**. Screens: **4**.

Workflow: Draft configuration, validate dependencies, review impact and publish version; cannot silently invalidate active care.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M29-S01 | Facility hierarchy | D | Organization, sites, departments, locations and ownership | Open configuration node |
| M29-S02 | Location and service editor | F | Code, name, parent, service type, availability and effective dates | Submit configuration draft |
| M29-S03 | Service catalogue | L | Services, departments, eligible resources, status and effective version | Open service configuration |
| M29-S04 | Configuration impact and publication | X | Changed values, dependent workflows, effective time, approvals and rollback | Publish reviewed configuration |

### M30 — Terminology formularies and clinical rules

Eligible personas: **Clinical informaticist; clinical approver**. Navigation icon: **BookOpen**. Screens: **5**.

Workflow: Import, validate, approve, activate and monitor; immutable versions and approved rollback are required.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M30-S01 | Terminology browser | L | System, version, code, display, synonyms, mappings and licensing metadata | Inspect concept |
| M30-S02 | Formulary editor | F | Product, formulation, permitted routes, restrictions and effective version | Submit formulary change |
| M30-S03 | Clinical rule package workbench | D | Package version, rule inventory, dependencies, provenance and approval status | Open validation results |
| M30-S04 | Content validation and approval | X | Test cases, mappings, golden results, unresolved failures and clinical sign-off | Approve content package |
| M30-S05 | Content activation and rollback | X | Target cell, current and proposed versions, impact, schedule and rollback evidence | Activate approved package |

### M31 — Forms templates documents and signatures

Eligible personas: **Template designer; document approver; authorized signer**. Navigation icon: **Files**. Screens: **6**.

Workflow: Design, preview, validate, approve and publish template; signed documents preserve original version and amendments.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M31-S01 | Template catalogue | L | Template, module, locale, version, status and owner | Open template |
| M31-S02 | Template editor | F | Sections, fields, data bindings, validation, layout and language strings | Save template draft |
| M31-S03 | Template preview and approval | X | Desktop mobile print previews, sample data, accessibility and approval | Approve template version |
| M31-S04 | Document workbench | F | Template version, patient context, content, attachments and draft status | Save document draft |
| M31-S05 | Document signature and amendment | X | Final rendered content, signer authority, signature, prior versions and amendment reason | Sign reviewed document |
| M31-S06 | Document archive and print | D | Document version, status, access scope, retention and print metadata | Print authorized document |

### M32 — Consent privacy and disclosure

Eligible personas: **Privacy officer; authorized care staff; patient where permitted**. Navigation icon: **ShieldCheck**. Screens: **5**.

Workflow: Record directive or other applicable authority, verify scope, enforce restrictions and manage revocation; retention obligations remain separate.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M32-S01 | Consent and privacy overview | D | Directives, legal basis metadata, purpose, recipients, proxies, expiry and restrictions | Open directive |
| M32-S02 | Consent directive capture | F | Subject, scope, purpose, recipient, dates, evidence, interpreter and withdrawal information | Submit directive |
| M32-S03 | Consent verification and revocation | X | Authority evidence, active scope, conflicts, proposed revocation and consequences | Record directive decision |
| M32-S04 | Disclosure request review | X | Requester, requested records, purpose, authority, redactions, approvals and expiry | Authorize scoped disclosure |
| M32-S05 | Privacy requests and incidents | L | Access or correction requests, complaints, deadlines, incidents and accountable owner | Open privacy case |

### M33 — Identity access and workforce privileges

Eligible personas: **Security administrator; credentialing approver**. Navigation icon: **KeyRound**. Screens: **7**.

Workflow: Verify workforce, grant scoped and expiring access, review and revoke; privileged access is independently approved.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M33-S01 | Workforce accounts | L | Identity, account status, organization, roles and expiry | Open workforce account |
| M33-S02 | Account onboarding and verification | F | Identity evidence, organization, contact, employment reference and verifier | Activate verified account |
| M33-S03 | Role and permission policy | F | Role, actions, tenant scope, department, purpose and restrictions | Submit policy change |
| M33-S04 | Clinical privilege review | X | Credentials, authorized procedures, dates, evidence and approver | Approve scoped privilege |
| M33-S05 | Access reviews and revocation | X | Current grants, usage, reviewer, revocation scope and effective time | Revoke selected access |
| M33-S06 | Sessions and authentication security | D | Active sessions, authenticators, last activity, local grants and revocation status | Revoke session |
| M33-S07 | Emergency access review | X | Named actor, patient scope, reason, duration, actions and retrospective reviewer | Record emergency access review |

### M34 — Audit provenance and retention

Eligible personas: **Audit reviewer; security investigator; retention approver**. Navigation icon: **History**. Screens: **5**.

Workflow: Search within authorized purpose, inspect evidence and chain, export with control; application users cannot erase audit evidence.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M34-S01 | Audit search | L | Actor, patient, action, outcome, time, cell and purpose filters | Search authorized evidence |
| M34-S02 | Audit event detail | D | Event ID, actor, subject, policy, timestamp, outcome, correlation and integrity status | Inspect related events |
| M34-S03 | Clinical provenance timeline | T | Record revisions, signatures, sources, amendments and linked evidence | Open original revision |
| M34-S04 | Audit evidence export | X | Case, scope, redaction, integrity manifest, recipient and approval | Create controlled evidence export |
| M34-S05 | Retention hold and disposal review | X | Policy, eligible records, legal hold, scope, approvals and disposal evidence | Approve eligible retention action |

### M35 — Interoperability and API developer product

Eligible personas: **Integration operator; authorized partner developer**. Navigation icon: **Plug**. Screens: **6**.

Workflow: Onboard partner, scope credentials, test contracts, enable traffic; failed messages quarantine and replay requires effect-aware review.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M35-S01 | Partner integration directory | L | Partner, contracts, scopes, environment, status and owner | Open partner integration |
| M35-S02 | Developer API catalogue | D | Versioned contracts, examples, authentication, quotas, changelog and deprecation | Open endpoint documentation |
| M35-S03 | Developer application and credentials | F | Application, environment, approved scopes, credential expiry and callback domains | Request scoped credentials |
| M35-S04 | Integration message monitor | L | Correlation ID, source, destination, contract, processing status and redacted error | Open message trace |
| M35-S05 | Mapping and contract validation | X | Source fields, target fields, terminology mappings, fixture results and version | Approve mapping version |
| M35-S06 | Quarantine and replay review | X | Failure, original event, deduplication, external effects, proposed replay scope | Authorize controlled replay |

### M36 — Devices analyzers and bedside gateways

Eligible personas: **Biomedical integrator; device operator**. Navigation icon: **Monitor**. Screens: **5**.

Workflow: Enroll, map, associate patient, validate feeds; identity or clock ambiguity quarantines data before clinical use.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M36-S01 | Device and gateway fleet | L | Device ID, location, status, connectivity, firmware and last seen | Open device |
| M36-S02 | Device enrolment and mapping | F | Identity, protocol, approved model, field mapping, units and credentials | Submit device enrolment |
| M36-S03 | Patient device association | X | Patient identifiers, device, location, association start and end, verifier | Confirm association |
| M36-S04 | Device data review and quarantine | X | Raw reference, normalized value, units, clock quality, association and errors | Release validated observation |
| M36-S05 | Gateway diagnostics | D | Connection, queue, last delivery, clock drift, retry and error history | Run authorized diagnostic |

### M37 — Notifications communication and escalation

Eligible personas: **Care-team recipient; communications operator**. Navigation icon: **Bell**. Screens: **4**.

Workflow: Queue and deliver, then separately track acknowledgement; critical escalation requires named clinical responsibility.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M37-S01 | Notification inbox | L | Priority label, patient context when authorized, sender, sent time and acknowledgement | Open notification |
| M37-S02 | Notification detail and acknowledgement | D | Message, source task, delivery history, acknowledgement and deadline | Acknowledge responsibility |
| M37-S03 | Communication template and routing | F | Purpose, language, approved message, channels, recipients and escalation | Submit routing change |
| M37-S04 | Delivery failures and escalation | L | Attempt history, channel errors, due escalation, accountable owner and next action | Resolve delivery failure |

### M38 — Offline sync and ownership reconciliation

Eligible personas: **Local operations; authorized reconciliation reviewer**. Navigation icon: **RefreshCw**. Screens: **5**.

Workflow: Queue locally, validate ownership and versions, synchronize or conflict; unknown status never appears as saved centrally.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M38-S01 | Offline synchronization status | D | Local availability, owner cell, last successful sync, queued items and limits | Inspect pending changes |
| M38-S02 | Pending changes queue | L | Record, action, local timestamp, idempotency key, status and reason | Open pending change |
| M38-S03 | Synchronization conflict review | X | Local and authoritative versions, ownership epoch, source, differing fields and permitted resolutions | Submit reviewed resolution |
| M38-S04 | Ownership transfer review | X | Source, destination, freeze state, positive fencing evidence, epoch and reconciliation | Authorize qualified transfer |
| M38-S05 | Reconciliation history | T | Applied changes, rejected changes, reviewer, evidence and final versions | Inspect reconciled record |

### M39 — Tenant cell routing and fleet releases

Eligible personas: **Platform operator; release approver**. Navigation icon: **Network**. Screens: **5**.

Workflow: Provision, qualify, activate, stage releases and observe; migration freezes and fences before route change.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M39-S01 | Tenant and cell fleet | L | Tenant, placement, tier, capacity, version and lifecycle | Open cell |
| M39-S02 | Tenant provisioning | F | Identity, region, tier, services, quotas, residency constraints and owner | Submit provisioning plan |
| M39-S03 | Cell qualification | X | Isolation, dependencies, capacity, identity continuity, backup and test evidence | Approve cell activation |
| M39-S04 | Fleet release rollout | B | Artifact version, canaries, migration compatibility, health gates and rollout status | Start approved staged release |
| M39-S05 | Cell migration and routing | X | Copy, catchup, freeze, fencing, verify, switch, observe and retirement evidence | Advance permitted migration step |

### M40 — Reliability backup recovery and telemetry

Eligible personas: **SRE; incident commander; recovery approver**. Navigation icon: **Database**. Screens: **6**.

Workflow: Monitor, respond, restore in isolation, reconcile and verify before reopening writes or external effects.

| Screen ID | Exact screen name | Layout | Data/input groups | Primary action |
|---|---|---|---|---|
| M40-S01 | Service health and capacity | H | Critical workflow SLIs, dependencies, saturation, backlog and cell scope | Open incident investigation |
| M40-S02 | Incident workspace | D | Timeline, impact, owner, actions, communication and recovery criteria | Record incident action |
| M40-S03 | Backup catalogue and verification | L | Backup time, WAL coverage, keys, objects, integrity and restore evidence | Open backup set |
| M40-S04 | Recovery plan and execution | X | Target point, fencing, database, objects, audit, external effects, checks and approvals | Advance verified recovery step |
| M40-S05 | Recovery validation and reopening | X | Reconciliation results, smoke tests, residual risks, egress status and approvals | Authorize scoped reopening |
| M40-S06 | Operational telemetry explorer | T | Metrics, traces, redacted logs, correlation, time range and access scope | Inspect failure trace |

## 12. Cross-module workflow maps and exceptional paths

The following paths specify navigation and ownership, not a requirement to perform every step for every patient. Each transition checks permissions and preserves the same verified patient/encounter context. Billing policy must not silently prevent emergency care; exception handling is approved per hospital.

| Flow | Normal path by screen ID | Mandatory alternate/exception path |
|---|---|---|
| W01 Outpatient visit | M01-S01 → M01-S03 → M04-S01/S02 → M03-S03 → M05-S01/S02 → M05-S04 | New identity M01-S02; provisional identity M03-S02; duplicate review M01-S05; urgent escalation takes precedence over routine queue |
| W02 Scheduled arrival | M02-S01 → M02-S02 → M02-S03 → M04-S02 | Slot conflict retains request and offers alternatives; cancellation/no-show in appointment detail with reason; waitlist M02-S04 |
| W03 Laboratory | M11-S02 → M11-S01 → M11-S03 → M11-S04 → M11-S05/S06 → M11-S07 → M11-S09 | Rejection at receipt returns recollection task; critical finding creates M11-S08; amendment preserves original report and re-notifies affected recipients |
| W04 Imaging | M12-S02 → M12-S03 → M12-S04 → M12-S05/S06 → M12-S07 | Failed screening routes to authorized reviewer; PACS outage shows unavailable status; signed report correction uses amendment |
| W05 Medication | M08-S02 → M08-S03 → M08-S04 → M13-S01/S02 → M13-S03 → M13-S04 → M09-S01/S02/S03 | Missing safety facts block or use approved override; cancellation M08-S06; lost release response M13-S05; held/refused dose M09-S04 |
| W06 Admission to discharge | M04-S03 → M04-S04 → M04-S02 → M10-S02/S03 → M04-S06 → M04-S07 | Transfer M04-S05 requires acceptance; bed conflict returns board; pending results remain assigned after discharge; discharge cancellation is versioned |
| W07 Patient payment | M19-S01/S02 → M19-S03 → M19-S04 → M19-S05 → M19-S07 | Gateway timeout is unknown pending status lookup; no second charge; refund M19-S06; insurance flow M20-S01–S06 |
| W08 Referral | M16-S02 → M16-S03 → M16-S04 → M16-S05 | Rejection returns responsibility; no acknowledgement remains outstanding; external transmission uses disclosure authority |
| W09 Patient/proxy portal | G-S01/S02 → M18-S01 → M18-S02/S03/S05/S08 | Proxy selection visibly changes subject; expired/revoked scope removes access; unreleased results remain inaccessible; payment outcome uses durable lookup |
| W10 Surgery | M14-S02 → M14-S01 → M14-S03 → M14-S04/S05 → M14-S06 | Missing consent/checklist uses approved emergency exception or blocks; cancelled case preserves preparation records |
| W11 Transfusion | M15-S02 → M15-S01 → M15-S03 → M15-S04 → M15-S05 → M15-S06 | Compatibility or identity mismatch blocks; reaction record escalates; returned unit requires review before reuse |
| W12 Offline continuity | G-S10 → M38-S01/S02 → authoritative local clinical screens if available → M38-S05 | Device-only drafts remain pending; conflicts go to M38-S03; ownership cannot transfer without M38-S04 fencing evidence |
| W13 Privacy disclosure | M32-S01/S02/S03 → M32-S04 → M31-S06 or scoped export | Restricted scope, revoked authority or missing evidence returns for review; access and export audit recorded |
| W14 Platform recovery | M40-S01/S02 → M40-S03/S04 → M40-S05 | External side effects remain disabled until reconciliation; incomplete audit/object/key recovery blocks reopening |

M17 specialty assessment, treatment and follow-up pages open from M17-S03, link to shared orders rather than duplicating prescription/result ownership, and close through M17-S04. Specialty-specific measurements, scoring, oncology regimens and procedural thresholds require reviewed clinical content. This UI document does not supply treatment protocols.

### Medication safety interaction detail

The UI submits a version-bound command to the authoritative core; it does not acquire database locks itself. Server safety results show assessment time, relevant input versions, missing facts and rule version. If state changes, invalidate the prior authorizing action and present the new assessment. Do not auto-retry an authorization with new facts without the required user review.

Pharmacy labels are explicit: Stock reserved → Release review pending → Authorized for handover → Handed over → Reconciled. Ticket expiry or denial before consumption blocks handover. A successful stock reservation is never shown as authorization. A missing consume response shows Outcome unknown with status lookup, not a new consume or dispense command. After durable consumption, a cancellation becomes stop/reconciliation according to architecture rules; the UI must not claim physical medication was recalled automatically. Handover confirmation records what actually happened, with an exception path for interrupted recording. Administration has its own unique dose identity and cannot be inferred from dispensing.

## 13. Tabs, actions, printouts and patient documents

Tabs are bounded by the screen's field groups: use meaningful labels such as Overview, Results, History, Documents; do not add unrelated generic tabs. Each tab needs permission, empty/loading/error states and a deep-linkable selection when safe. Switching tabs never discards drafts. At narrow widths use a section selector or wrapping navigation rather than clipped tab labels.

Action contract for every mutation: screen ID, permission, current version, required inputs, validation source, idempotency handling, irreversible effect, review text, server response, audit event, retry/status lookup and resulting navigation. This is required implementation metadata. Bulk actions show selected count and scope; pagination must never silently select all patients. Destructive operations act on explicitly reviewed targets.

Print register: P01 patient identity card; P02 appointment/preparation slip; P03 specimen label; P04 laboratory report; P05 radiology report; P06 prescription; P07 medication administration export; P08 discharge summary; P09 referral letter; P10 invoice; P11 payment receipt; P12 consent copy; P13 operative summary; P14 transfusion record; P15 purchase order; P16 goods receipt; P17 custody/release record; P18 audit export manifest. Document layouts need A4 and any specific printer media, preview, language, pagination and long-content tests. Printer-specific specimen labels must preserve barcode quality and required human-readable identifiers; no guessed label dimensions.

Printed clinical documents identify patient, facility, document/version, status, author/signature where applicable, issue time and page x/y. Draft/watermark status is unmistakable but never obscures clinical text. No page-break splitting of medication dose/unit lines or signatures from their record. PDF text remains selectable when generated from structured data. Print/export is permission-controlled and audited; screenshots are not document generation. Every printable item has a documented retention/disclosure policy. Do not declare a legally valid signature solely because a signature image is displayed.

## 14. Accessibility and usability validation

Target WCAG 2.2 AA for web interfaces; compliance is an implementation and assessment outcome, not a promise made by this specification. Validate normal text contrast at least 4.5:1, large text at least 3:1 and meaningful non-text boundaries at least 3:1. Test reflow at 320 CSS px and text resizing, keyboard-only use, visible unobscured focus, labels/errors and assistive-technology navigation. The project adopts 44px interactive targets as a stronger practical default. Equivalent keyboard/button actions must exist for drag-only boards. Test browser and native assistive technologies relevant to the deployment, including screen readers and mobile text scaling.

Use real headings, landmarks, table headers and form labels. Restore focus after dialogs and mutations; trap focus only inside true modal dialogs. No keyboard trap. Support paste and password managers in authentication; avoid unnecessary cognitive puzzles. Essential time limits need warning and permitted extension, except justified security/safety exceptions. Focus ring is 2px solid with 2px separation; check contrast in light/dark navigation surfaces. Never remove focus styling globally.

Clinical usability sessions include registration staff, nurses, prescribers, pharmacists, lab staff, patients/proxies and operations staff. Observe patient selection, interruptions, long names, similar names, dose/unit reading, overrides, duplicate submissions, clinical corrections and downtime. Record task completion, critical use errors, recovery and user feedback. Do not invent numerical pass rates before a test protocol and participant plan exist. Unresolved high-severity use errors block the affected release.

## 15. AI development directive — mandatory repository policy

Copy this section into the project instructions file and reference this version. Claude Code, Codex and Copilot receive the same canonical register and contracts. Agent names/accounts do not confer approval authority.

1. Build only assigned screen IDs and named shared components. Do not rename, omit, substitute or mark screens accepted without evidence. No generic dashboard in place of a specified clinical task.
2. Use the approved tokens, typography, Lucide imports, spacing and layouts. No raw ad hoc colours outside the token definition, glow effects, emoji icons, fabricated logos or layout compression. A lint exception needs a recorded design decision.
3. Use named feature routes and typed contracts. Do not create one giant numeric route dispatcher or use any-casts to bypass domain types. Frontend mocks must be explicitly labelled and must not count as backend integration evidence.
4. Backend authorization, tenant isolation, idempotency and clinical version checks remain authoritative. Do not replace them with hidden buttons, cached status or client-only validation.
5. Implement applicable S00–S13 states and test EN/FR, mobile/tablet/desktop, keyboard, long content and failures. Do not accept a screenshot of one ideal state as completion.
6. Shared component, token, API and migration changes require review by their owner before integration. Work in isolated branches/worktrees; integrate small reviewed changes daily. Do not have agents edit the same file concurrently without coordination.
7. Create tests from acceptance requirements, not only by mirroring the implementation. Independently test wrong-patient context, stale versions, denied access, double submission and lost responses where relevant.
8. Do not automatically approve code because another model agrees. CI evidence plus responsible technical review is required; safety-critical screens also require qualified clinical usability review.
9. Preserve signed records and audit evidence. Do not use delete controls for signed notes, administrations, issued invoices or audit events; use the approved correction/reversal workflow.
10. At every handoff report assigned IDs, changed files, implemented states, tests actually run, evidence paths, unresolved gaps and exact status. Never claim certification, production readiness or test success without recorded evidence.

Suggested repository layout: design/tokens; design/icons; design/components; specs/screens; specs/flows; contracts/openapi; contracts/events; apps/web/features; apps/mobile/features; tests/accessibility; tests/visual; tests/workflows; evidence/releases. Each screen has metadata linking its ID to named routes, permission policies, commands/queries, test IDs and evidence. Native and web implementations share semantics/tokens; do not assume DOM CSS can be copied into native components.

Per-screen implementation record template:

```yaml
screen_id: M07-S02
name: Allergy entry
spec_version: '1.1'
status: specified_baseline
owner: unassigned
route: pending_review
roles: [authorized_clinician, authorized_nurse]
permissions: pending_authorization_contract
layout: F
fields: [substance_system, substance_code, reaction, severity, onset, source, verification]
primary_action: Record allergy
queries: pending_OpenAPI_binding
commands: pending_OpenAPI_binding
preconditions: [verified_patient_context, authoritative_owner_available]
states_required: [S00, S01, S02, S03, S04, S05, S06, S07, S08, S09, S10, S11, S12, S13]
local_draft_policy: disabled_until_approved
translations: [en, fr]
test_evidence: []
clinical_review: pending
```

This example is an honest metadata template; pending contracts must be completed before implementation is declared ready. Do not copy its fields or permissions into unrelated screens.

## 16. Acceptance matrix and release gates

| Gate | Evidence required | Blocks |
|---|---|---|
| UX01 Scope | Exact screen ID/name, parent module, fields, transitions, tabs/dialogs and owner | Missing or substituted screen |
| UX02 Contracts | Reviewed field dictionary, OpenAPI bindings, permission matrix, code systems and clinical validations | Guessed rule, editable sourced fact, missing authority |
| UX03 Visual | Screenshots at registered sizes with approved tokens and no clipping/overlap/distortion | Glow, collision, squeezing, illegible text |
| UX04 Responsive | 320px reflow, tablet/desktop, portrait/landscape, text scaling, zoom, virtual keyboard | Hidden critical action/data, global overflow |
| UX05 Accessibility | Automated findings reviewed plus keyboard and assistive-technology checks | Unresolved applicable AA failure |
| UX06 Localization | English/French strings, real long labels, accent/number/date tests | Truncation, untranslated errors, unit corruption |
| UX07 Workflow | Normal, cancellation, correction, denied, stale, timeout, retry and offline paths | False success, lost draft, repeat external effect |
| UX08 Security | Cross-tenant and wrong-patient tests, redacted telemetry, proxy expiry and export control | Unauthorized data, stale privilege use |
| UX09 Clinical safety | Qualified review of identity, doses, units, alerts, signature, reconciliation and handover | Unresolved severe use error |
| UX10 Operations | Real API integration, performance under representative data, outage/downtime and recovery checks | Mock-only implementation or untested recovery |

Statuses: Specified—baseline → Specified—implementation-ready → Designed → Implemented → Tested → Clinically reviewed where applicable → Accepted. Record separate status columns if work proceeds in parallel; never infer later gates from earlier ones. Screens without clinical actions require the appropriate business/security reviewer rather than a meaningless clinical signature.

Visual regression scenarios: 80-character names; identical names/different IDs; long French labels; unknown birth date; provisional identity; 1000-row result list with pagination/virtualization; multi-year chart; lengthy drug names; multiline dose instructions; missing units; delayed safety facts; concurrent edits; multiple open patient tabs; slow network; dropped command response; expired session; offline local server available; local server unavailable; 200% text; 400% browser zoom; printer unavailable. Use synthetic data, never production records in public CI artifacts.

Clinical screens and integration components must expose bounded loading and failure states. Do not fetch a decade of chart history into one browser payload. Use cursor/page loading and selective sections while keeping record counts and available history clear. Rendering latency and API latency are measured separately. Do not animate charts or poll dashboards so aggressively that bedside workflows lose bandwidth.

## 17. Implementation sequence and remaining decisions

Start with tokens/icon wrappers, shells, patient context, forms, worklists, review/decision and command status components. Implement the first vertical workflow G-S01 → M01-S01/S02/S03 → M04-S02 → M06-S01 → M07-S02/S03 → M05-S02/S04. Then add diagnostics, medication and finance with their own safety gates. Subsequent screens follow the architecture's domain dependencies; screen count is not a delivery quota.

Decisions still requiring evidence: final supplied brand assets; supported browser/native/device matrix; full per-screen field types/requiredness and terminology; role/action matrix; actual OpenAPI routes; exact clinical scoring/rules and specialty protocols; partner PACS limitations; locally approved consent/signature/legal wording; printer sizes; screen-specific offline draft policies; clinician-approved alert hierarchy; rendered wireframes and usability evidence. These are tracked build gates, not permission to improvise. The present document delivers the requested master inventory and visual/interaction specification; it does not claim those external inputs or renderings already exist.

## 18. References and validation scope

Primary references checked 27 October 2026:

- W3C WCAG 2.2: https://www.w3.org/TR/WCAG22/
- Lucide accessibility guidance: https://github.com/lucide-icons/lucide/blob/main/docs/how-to/accessibility.md
- Lucide icon catalogue: https://lucide.dev/icons/
- SYNQORIA architecture revision 2.1, canonical modules and safety protocol (linked in section 1).

The design rules, layout dimensions, scope and workflow descriptions are project proposals. Accessibility requirements above are a concise selection; assessment must use the complete applicable standard. No external reference certifies this product. Validation for this document checks unique screen IDs, module coverage, cross-references and specified colour-pair calculations. It does not execute an application or certify clinical safety.

Document checks completed for version 1.1: 40/40 modules covered; 261/261 unique screen IDs; all explicit screen references resolve; 51/51 specified colour pairs pass their stated contrast threshold. These are document-level checks only. No UI screenshots, accessibility audit, clinical validation or application tests have been run.


## 19. Revision 1.1 record

User-requested refinement: cream background direction; comprehensive colour layering and interaction states; exact typography and button geometry; tabs, forms, tables, alerts and responsive rules; shared-token enforcement. The 261-screen inventory, module ownership and clinical workflows remain unchanged. Contrast values were recalculated for the revised palette. No rendered component review or live application test is implied.
