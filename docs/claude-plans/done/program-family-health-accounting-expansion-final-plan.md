# Program Family Expansion (Health Sciences + Accounting) — FINAL Implementation Plan

**Status: SUPERSEDED on the schema question by `program-family-many-to-many-final-plan.md` — Product
UX reversed the single-FK architecture decision (§0.3/§H below) after discovering concrete Program
Family overlap use cases. This file's Health Sciences and Accounting membership decisions (§C, §E)
carry forward unchanged into the new plan; its schema (§E), API (§H), migration (§I/§M), and UI blast
radius (§L) do not — read the many-to-many plan instead. Kept for historical trace. 2026-09-15.**

This supersedes `program-family-health-accounting-expansion-product-ux-consultation-prompt.md` (pass 1).
That document's facts are preserved (§A); its governance conclusion and the Accounting-family verdict are
revised per Product UX's pass-2 decisions below. Do not re-read the pass-1 file for anything except
historical trace — everything load-bearing is restated here.

---

## A. Preserved audit facts (from pass 1, unchanged)

- Program Family is already generic, DB-backed (`program_families` + `course_programs.program_family_id`),
  dynamically rendered, additive, never persisted on Note, never sent to generation, never a discovery axis.
  The frontend family-derivation logic (`applicable-programs-combobox.tsx`) is already family-count-agnostic
  — **do not touch it** except for the rendering changes in §J.
- Engineering (18 members) and Education (8 members) are both live, both derived from the FK, not hardcoded.
- `course_programs.program_family_id` is a single nullable FK — one family per program, no overlap possible
  today. Confirmed unchanged on re-check (`id, name, program_family_id, exam_goal_slug, created_at` — no
  other columns exist on `course_programs`).
- No endpoint exists to reassign an *existing* catalog program's family — confirmed unchanged.
- The catalog has 60 live rows; both fused rows ("Nursing · Medicine" 20 notes, "Nursing · Pharmacy" 1 note)
  exist; CMA and CFA exist and are in real use; Finance does not exist.
- **The 35-note, 10-program business tag set is confirmed a single bulk action** — all 350
  `note_course_program` rows written inside one 5-minute window on 2026-09-14. This is preserved as fact.
  §B and §E below revise what conclusion to draw from it — the fact itself is not reopened.
- Health Sciences evidence (18 notes with a genuine 3-way Nursing+Medicine+Pharmacy tag, accumulated across
  five weeks and two independently-invented fused-row workarounds) is preserved and, per Product UX, now
  **locked**, not merely recommended.

---

## B. Revised Program Family governance

**The old rule ("organic historical co-selection is required evidence") is replaced.** Two separate
questions now govern every Program Family decision:

- **Question A — does the shortcut deserve to exist?** Evidence: will a stable set of related programs
  materially reduce recurring curator work? This can come from historical co-selection (Health Sciences),
  from a known upcoming authoring workflow that will repeatedly need the same programs (Accounting, see
  §D/§E), or from repeated manual bulk-selection friction (the 35-note action, reinterpreted below).
- **Question B — which programs belong in it?** Evidence: which programs form a high-precision default
  that rarely needs manual removal? This is a curriculum-semantics question, answered independently of
  Question A, and **organic co-selection is neither required nor sufficient** to answer it on its own — a
  family can be justified by Question A and still need its exact membership set by Question B's own
  evidence, not by whatever a prior bulk action happened to include.

**The 35-note bulk action, reinterpreted (per Product UX's correction):** it does **not** prove all 10
programs belong together. It **does** prove Question A — a curator needed bulk cross-program applicability
strongly enough to hand-roll a 350-row assignment outside any tooling built for it. That is exactly the
authoring friction Program Family exists to remove. It is simply not usable evidence for Question B, which
this plan answers instead from the actual CPALE curriculum structure (§D).

---

## C. Health Sciences — LOCKED, ships this release

| | |
|---|---|
| Name | Health Sciences |
| Members | Nursing, Medicine, Pharmacy |
| Count | 3 |
| Evidence | 18 notes carrying a genuine 3-way tag, accumulated across five weeks and multiple sessions, plus 21 more notes across two independently-invented fused catalog rows — two separate curator actions at different times converging on the same trio |
| Excluded | Physical Therapy (7 notes, 0 co-selection), Radiologic Technology (0 notes) — no comparable evidence; Psychology, Biology, Chemistry — never had supporting evidence, not reopened |

Not reopened per Product UX's explicit instruction; no new contradicting evidence surfaced in this pass.

---

## D. CPALE curriculum evidence

Read directly: `docs/curriculum/cpale-comprehensive-review.tsv` (359 planned notes, the authoritative
source per the documented curriculum pipeline — not the `.xlsx` files, which are generated from it and
must never be hand-edited).

**Headline finding: the TSV's own `applicable_programs` column is not usable evidence — it is a uniform
default, not a curated decision.** All 359 rows carry `applicable_programs = "Accountancy"` with zero
exceptions. `domain_context` is more carefully done (285 `ACCOUNTANCY`, 43 `PROFESSIONAL_PRACTICE_AND_REGULATION`
— correctly routing the RFBT subject plan away from Accountancy, consistent with §11's approved direction —
31 `(unset)`), which shows the strategist pass applied real judgment to Domain Context but evidently never
ran a cross-program applicability pass at all. **Do not read the applicable_programs column as curriculum
evidence for this decision** — it would mean concluding "zero cross-program overlap exists anywhere in
CPALE," which contradicts the plan's own subject-area structure (below) and the doctrine this release
exists to enable (§28).

**What the curriculum's actual structure implies, by subject plan (359 rows, 6 subject plans):**

| Subject plan | Rows | Genuine overlap signal |
|---|---|---|
| Financial Accounting and Reporting | 90 | Accountancy-specific (PFRS recognition/measurement) |
| Advanced Financial Accounting and Reporting | 34 | Accountancy-specific |
| Management Services | 74 | **Strong** — sections are *Budgeting, Capital Budgeting, Cost of Capital, Working Capital Management, Financing/Leverage/Capital Structure, Risk and Return, Economics for Management Decisions, CVP, Relevant Costing, Standard Costing, Variance Analysis, Responsibility Accounting* — this is recognizably the Management Accounting / Managerial Finance syllabus used by Philippine accounting-track programs |
| Auditing | 61 | **Strong** — this is Internal Auditing's core curriculum by definition |
| Taxation | 57 | Accountancy-specific at board-exam depth |
| Regulatory Framework for Business Transactions (RFBT) | 43 | Business-law content (Obligations, Contracts, Sales, Credit Transactions, Corporations, Securities, Labor) nearly identical to a standard BSBA "Business Law" sequence |

**Why Management Accounting, Accounting Information Systems, and Internal Auditing are the defensible
additions, and Business Administration is not:** in the Philippine higher-ed system, BS Management
Accounting, BS Accounting Information Systems, and programs offering Internal Auditing as a track share
most of their *core* coursework with BS Accountancy — they differ mainly in specialization electives, not
in foundational curriculum. That means a default family expansion onto FAR/AFAR/Taxation content (67% of
the plan) is a *reasonable* default for those three, not just for the Management Services/Auditing content
where their case is strongest. Business Administration is structurally a **different kind of program** — a
generalist business degree with its own separate core (marketing, HR, operations, general management) —
and its genuine overlap is concentrated in RFBT (12%) plus a finance-flavored subset of Management Services
(roughly another 10%): **about 22% of the plan**, not "a large majority." Defaulting Business Administration
into every family click would need manual removal on the remaining ~78% — failing §7's own test ("does
adding it by default usually save work, or usually need removing?") in the wrong direction.

**A related finding, outside this plan's scope but worth flagging to the owner separately:** several RFBT
titles in the TSV bake the program into the title itself (*"Obligations and Contracts in Accountancy's
Regulatory Framework," "Credit Transactions in Accountancy: Regulatory Framework and Business Law,"
"Negotiable Instruments in Accountancy and Business Law"*) — a direct violation of the locked Note Title
doctrine (curriculum-neutral titles). Separately, several rows marked `status = "New"` — e.g. "Bonds
Payable and Effective Interest Method," "PFRS 15 Five-Step Revenue Recognition Model," "Segment Reporting,"
"Petty Cash and Cash Short or Over" — are titles that **already exist in production** as part of the
35-note bulk-tagged cluster (§A), meaning the plan may not be aware those canonical notes already exist and
risks duplicate authoring. Neither finding changes this plan's scope (§29 excludes curriculum-content
correction) — they're recorded here because they surfaced during this read and would otherwise go
unindexed.

---

## E. Accounting family comparison — decision: **Candidate A (Accounting)**

**⚠️ Evidence-type disclosure, read before the table:** the TSV's `applicable_programs` column supports
**no** multi-program family at all — it says `Accountancy` on all 359 rows (§D). The percentages below
(~78% accounting-specific vs. ~22% Business-Administration-plausible) are **my arithmetic over the plan's
subject-area/section breakdown, not a value read from any row.** The claim that AIS/Management
Accounting/Internal Auditing share a core curriculum with Accountancy while Business Administration does
not is **external domain knowledge about how Philippine accounting-track academic programs are
structured, not a fact this repository or database can confirm.** The decision doesn't change on this
disclosure — §7's precision-before-coverage principle and Product UX's own stated lean toward the
narrower family both point the same way regardless, and a wrongly-excluded program costs one manual click
— but the owner should know which part of this reasoning they can re-verify with a query and which part
they can't.

| | Candidate A — Accounting | Candidate B — Accounting & Business |
|---|---|---|
| Members | Accountancy, Management Accounting, Accounting Information Systems, Internal Auditing | A + Business Administration |
| Authoring usefulness | High — matches the accounting-track program family used across ~78% of the plan (FAR/AFAR/Taxation/Auditing) plus the MS-core content | High on ~22% (RFBT + finance-flavored MS), but the fifth member is a liability everywhere else |
| False-positive risk | Low — AIS/MA/IA share core curriculum with Accountancy by program design | High — Business Administration would need manual removal on ~78% of uses |
| CPALE coverage | Full-plan safe default | Only safe within RFBT/finance-flavored MS subset |
| Expected removal frequency | Low | High, outside RFBT/finance-flavored MS |
| Naming accuracy | Accurate — names an accounting-track program cluster | Would overclaim a business-wide cluster this curriculum doesn't support |
| Future extensibility | Business Administration (and Economics/Entrepreneurship) can be added manually per-note at no cost; a future family can be proposed separately once real BSBA-adjacent co-selection accumulates | Starting broad and later narrowing is disruptive; starting narrow and manually widening per-note is not |

**Decision: Accounting — Accountancy, Management Accounting, Accounting Information Systems, Internal
Auditing (4 members). Ships this release**, per Product UX's reopened appetite to ship alongside CPALE
authoring rather than wait for further organic accumulation — this is not circular in the way waiting for
repeated manual selection would be, because the evidence here is the curriculum's own subject structure
plus how these specific academic programs are actually constituted, not a prediction that curators will
eventually repeat a pattern.

---

## F. Programs excluded from initial Accounting-family membership

| Program | Disposition | Reason |
|---|---|---|
| Business Administration | EXCLUDE INITIAL, add manually when applicable | Concentrated overlap (~22% of plan) against a structurally different program type — see §D/§E |
| Entrepreneurship | EXCLUDE INITIAL | No dedicated section or signal anywhere in the 359-row plan |
| Economics | EXCLUDE INITIAL, add manually when applicable | Only "Economics for Management Decisions" (3 of 359 rows) — too thin for a default |
| Senior High – ABM | EXCLUDE INITIAL | No signal in the plan, and a structural depth mismatch (CPALE is board-exam/professional depth; ABM is senior-high depth) independent of any future evidence |
| Real Estate Management | EXCLUDE INITIAL | No signal in the plan; too specialized, per the pass-1 finding (unchanged) |
| CMA | EXCLUDE INITIAL (OTHER — pending credential-vs-program taxonomy) | Unresolved whether Course/Program should include credential tracks at all; presence in the 35-note bulk action is not evidence either way (§B) |
| CFA | EXCLUDE INITIAL (OTHER — pending credential-vs-program taxonomy) | Same as CMA |
| Finance | N/A — see §G | Course/Program-level question, handled separately from family membership |

---

## G. Finance

**Course / Program: ADD DURING CPALE CURATION.** Trigger (unchanged, preserved): the first canonical Note
genuinely applicable to Finance during CPALE authoring is sufficient evidence — no note-count floor, no
repeated-usage requirement. **Worth flagging: this trigger looks likely to fire soon.** The Management
Services subject plan's "Cost of Capital" (3), "Working Capital Management" (4), "Financing, Leverage and
Capital Structure" (6), and "Risk and Return" (3) sections — 16 of the 359 planned notes — are textbook
Finance/Financial-Management topics. They haven't been authored yet (`status = New`), so the trigger has
not fired today, but whoever authors those 16 notes should expect it to fire almost immediately.
**Finance Domain Context: NO** — unchanged, Course/Program governance and Domain Context governance remain
separate gates.

---

## H. Family update API

**Endpoint:** `PATCH /course-program-catalog/{id}`, mirroring the existing `NoteCollectionController`
convention (`@PatchMapping("/{id}")`, plain record DTO, no `Optional`/`JsonNullable` wrapper needed because
this endpoint has exactly one mutable field).

**Request DTO:** `UpdateCourseProgramCatalogRequest(UUID programFamilyId)` — `programFamilyId` nullable;
`null` clears membership, a valid id sets/changes it. No partial-PATCH ambiguity exists because there is
only one field to update.

**Service method:** `CourseProgramCatalogService.updateProgramFamily(UUID programId, UUID familyId)` —
loads the program (`ProgramNotFoundException` if missing — a new exception, or reuse an existing
not-found pattern if one already fits `course_programs`), validates `familyId` against `program_families`
when non-null (`UnknownProgramFamilyException`, already exists), otherwise writes `NULL`.

**Repository:** a new `updateProgramFamily(UUID id, UUID familyId)` on `CourseProgramCatalogRepository`
(plain `UPDATE course_programs SET program_family_id = ? WHERE id = ?`, no migration).

**Authorization:** `@PreAuthorize("hasRole('ADMIN')")`, matching every other catalog-management endpoint.

**Validation:** unknown program id → 404/not-found pattern matching existing catalog endpoints; unknown
family id (non-null) → `UnknownProgramFamilyException` (reused).

**Tests:** ADMIN-only guard (reflection-based `@PreAuthorize` check, matching existing pattern); a real
`MockMvc` request with `Content-Type: application/json` (not a bare service-method call — this repo's own
`v0.119.0` lesson); assign-unassigned, change-existing, clear-to-null, unknown program id, unknown family
id.

---

## I. Legacy fused program handling — **Option B, generic `is_active` flag**

**Inspected first, per instruction:** `course_programs` has no lifecycle field today (`id, name,
program_family_id, exam_goal_slug, created_at` only). But the repo already has an established convention
for exactly this: `discount_vouchers` and `quiz_share_links` both carry `is_active BOOLEAN NOT NULL DEFAULT
TRUE`. This is not a new abstraction — it's reusing an existing, small, well-understood pattern.

**Decision: add `is_active BOOLEAN NOT NULL DEFAULT TRUE` to `course_programs`, not a hardcoded frontend
denylist.** One migration does the whole thing in one step, the same way `V142` combined a rename and
inserts: `ALTER TABLE course_programs ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;` followed by
`UPDATE course_programs SET is_active = false WHERE name IN ('Nursing · Medicine', 'Nursing · Pharmacy');`
— targeted by name, not by their (runtime-generated, unpredictable) ids, so the migration is reviewable and
correct regardless of which UUIDs those rows actually have. This is a small, durable, reusable mechanism —
useful for every future deprecated catalog value, not just these two — and it avoids exactly what Product
UX rejected: a frontend array of ids/names that needs its own release to update.

**Semantics:** `GET /course-program-catalog` (the list the authoring combobox renders for *new* selection)
filters to `is_active = true` by default. A Note that already references an inactive row must still
resolve and display it correctly — the combobox needs to keep showing an already-selected inactive program
as a normal chip (fetch selected-by-id regardless of `is_active`, filter only the *pickable* list). Existing
Notes are never touched. `course_programs` rows are never deleted.

**Migration: YES, small, additive-only, no data loss, no backfill of Note data** — this is the one schema
change in this release, and it is justified by being the smallest durable fix and plausibly useful beyond
the two rows it's built for (exactly what Product UX asked the recommendation to weigh).

**⚠️ Split into two steps, not one — the column add and the 2-row backfill must NOT ship in the same
migration.** The column-add migration (`ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE`) ships with
the rest of this release's code. The `UPDATE ... SET is_active = false WHERE name IN (...)` backfill for
the two fused rows ships separately, **gated on confirming the Health Sciences family is already populated
in production** — seeing §O for why bundling them would strand curators between losing the fused shortcut
and gaining the replacement family.

---

## J. Family-chip UX

| State | Behavior |
|---|---|
| None selected | `Engineering · 18` — plain button |
| Partial | `Engineering · 6 remaining` — click adds only the missing 6 |
| Full | **LOCKED visible, inert**: `✓ Engineering · 18` — no click handler, visually distinct from the active state, never disappears |
| Remove member after full | Immediately reverts to partial: `✓ Engineering · 18` → remove one → `Engineering · 1 remaining` |
| Multiple families | Each computes its own remaining-count independently; unaffected by any other family's state (existing test already proves this, unchanged) |
| Mobile | Same compact chip, wraps in the existing `flex flex-wrap` row — no new breakpoint-specific logic needed |

**Accessibility — recommend status text, not `aria-pressed`.** `aria-pressed` implies a persistent toggle
state, which would misrepresent the control — the Note never stores "family selected," only individual
programs. Recommend the inert full-state render as a non-interactive element (e.g. a `<span>` or a
`disabled` button, not a clickable one) with a visible label equivalent to *"Engineering — all 18 programs
added"* for assistive tech, and nothing resembling a toggle semantic. Exact implementation (disabled button
vs. static status text) is an implementation-time choice within this constraint — both satisfy "never
implies persistence," so defer the final pick to whichever reads more naturally against the existing chip
component's other states, not to a new design review.

---

## K. 18+ selected-program inspection

**Not resolved by this planning pass — correctly so, per the instruction not to invent a threshold without
looking.** Define the acceptance check for implementation time instead of guessing a number:

> Render the Engineering family fully expanded (18 selected programs) in `applicable-programs-combobox.tsx`
> at both a standard desktop width and a 375px-wide mobile viewport. Pass if: the selected-chip row wraps
> without overflowing its container, every chip's "×" remains individually clickable, and the form above/
> below the chip row doesn't get pushed below the fold in a way that hides the Save/primary action. Fail
> either check → `MUST CHANGE` (propose a collapse-past-threshold treatment then, informed by what actually
> broke). Pass both → `NO CHANGE — CURRENT WRAPPING ACCEPTABLE`, and say so explicitly in the PR rather than
> silently skipping it.

---

## L. Copy

- Course / Program(s) helper: *"Choose every program this Note genuinely applies to."* Secondary, only if
  still needed: *"Programs control applicability and discovery, not how the Note is written."*
- Program families label: *"Program families"* — no further explanation; the chip interaction should teach
  itself.
- Domain Context: preserve current save-vs-generation-readiness phrasing exactly as pass 1 found it (no
  contradiction exists in the checked surfaces — do not implement a fix for this). Unify the one
  looser-than-the-others phrasing (`bulk-generation-page-client.tsx`'s *"Required when this note applies to
  more than one program"*) to match the other two surfaces' more accurate *"Needed before you can generate
  a Study Pack for a note in more than one program"* — low-risk, clearly improves consistency, matches the
  instruction to unify only where that bar is met.

---

## M. Exact blast radius

| File | Current | Change | Risk | Tests |
|---|---|---|---|---|
| `V???__course_program_is_active.sql` (new migration) | No lifecycle column | **MUST CHANGE** — add `is_active BOOLEAN NOT NULL DEFAULT TRUE`; backfill `is_active = false` for the 2 fused rows by name | Low, additive, reviewable | `CourseProgramCatalogRepositoryProgramFamilyIntegrationTest`-style migration test: before/after row count, the 2 rows' `is_active` value, everything else still `true` |
| `CourseProgramCatalogController.java` | No reassign endpoint | **MUST CHANGE** — add `PATCH /{id}` per §H | Low | Real `MockMvc` request, ADMIN-only guard test |
| `CourseProgramCatalogService.java` / `Repository.java` | `create()` sets family only at insert; `list()` returns all rows | **MUST CHANGE** — add `updateProgramFamily`; `list()` (or a variant used by the authoring combobox) filters `is_active = true` while an admin-facing list (catalog management) may still need to see inactive rows — confirm which endpoint the admin catalog screen uses and keep that one unfiltered | Low-medium — getting the filter on the wrong endpoint would hide inactive rows from the admin screen that's supposed to manage them | Unit + integration tests for both the filtered and unfiltered list paths |
| `applicable-programs-combobox.tsx` | Full-sentence buttons; already family-generic derivation | **MUST CHANGE (rendering only)** — compact `Family · count` / `Family · N remaining` / inert `✓ Family · N` chip states per §J. **Do not touch `availableProgramFamilies`/`handleFamilyExpansion`** | Low if scoped to rendering | Extend existing `applicable-programs-combobox.test.tsx` for the three chip states |
| Same file | Catalog list includes both fused rows, `is_active` not yet consumed | **MUST CHANGE** — render only `is_active` rows in the pickable list; always render an already-selected row regardless of `is_active` | Low | New test: inactive row absent from the picker, present as a chip if already selected |
| `docs/features/program-families.md` | 2 families documented | **MUST CHANGE** — add Health Sciences + Accounting, document the reverse Domain-Context guard (pass-1 finding, still applicable), document `is_active` | n/a | n/a |
| Admin catalog data (production) | Nursing/Medicine/Pharmacy and the 4 Accounting programs unassigned | **Data operation via the new PATCH endpoint**, run by the owner post-deploy | Low, reversible | n/a |
| `program_families`/`course_programs` overlap (single FK) | Unchanged | **NO CHANGE** — known limitation, recorded (§21 of pass 1, unchanged) | — | — |
| Review Set / Domain Context coupling | No coupling found | **CONDITIONAL** — see §P; add a test only at an actual shared seam, not as a standalone proof-of-absence | — | Only if a real seam is found |

---

## N. Database / API impact

- **Database migration: YES** — one small, additive migration (`is_active` column + 2-row backfill by
  name). This is a deliberate change from pass 1, which assumed no migration; Product UX explicitly
  authorized this as the smallest durable mechanism rather than a frontend hack.
- **API change: YES** — one new ADMIN-only `PATCH /course-program-catalog/{id}`.
- **Note backfill: NO** — nothing about any existing Note changes.
- **Catalog-row updates: YES** — `program_family_id` set on 3 Health Sciences + 4 Accounting rows (7 total,
  via the new endpoint, owner-run post-deploy), plus the 2-row `is_active` backfill (in the migration
  itself, not a separate data operation).
- **Family persisted on Note: NO.**

---

## O. Deployment / data ordering

Three independent pieces must land in the right order, because "endpoint deployed" is not "family
populated":

1. **Migration deploys first** (adds `is_active`, sets the 2 fused rows inactive). This can go out ahead of
   everything else — it only affects the picker's visible list, and at this point no replacement family
   exists yet, so a curator temporarily loses the fused shortcut with no replacement. **This is the
   ordering risk Product UX flagged in §O of the prompt — avoid it explicitly:**
2. **Do not deploy the `is_active` backfill until the Health Sciences family is already populated in
   production** — this is the split §I already specifies. Sequence: (a) deploy code (the `ADD COLUMN`
   migration + endpoint + UI, all together is fine, since the UI change alone is inert without populated
   families) → (b) owner runs the PATCH endpoint to assign Nursing/Medicine/Pharmacy to the Health Sciences
   family → (c) **only after (b) is confirmed**, run the separate 2-row `is_active` backfill (a follow-up
   migration or an owner-run `.sql` against the already-added column — either is fine, as long as it is a
   distinct step from (a) and happens after (b)).
3. **Accounting family population (4 rows) has no comparable ordering hazard** — there's no existing fused
   shortcut being retired alongside it, so it can be assigned whenever convenient after the endpoint ships.

---

## P. Testing

Per-item, matching the requested coverage list: assign family (unit + `MockMvc`), change family (unit),
clear family to null (unit), authorization (ADMIN-only reflection test), unknown program/family id
(service-level rejection tests), family chip expansion (existing pattern, extended for 2 more families),
partial family (existing pattern), completed family / inert state (new — assert no click handler fires,
not just visual), deduplication (existing test, unchanged, confirms no regression), manual selection
preserved across family expansion (existing test, unchanged), individual removal reverting a full family to
partial (new, per §J), multiple families (existing test, unchanged), no family persistence on Note
(existing architecture, no new test needed beyond what already covers `PUT /notes/{id}/applicable-programs`),
legacy catalog behavior (new: inactive row excluded from picker, included when already-selected; migration
before/after test per §M).

**Domain Context independence and "family changes don't retroactively alter Notes": add a test only where
a real shared seam exists.** Per Product UX's correction, do not write a cross-component integration test
purely to prove two unrelated components stay unrelated. Checked for an actual seam: the combobox component
structurally receives no `domainContext`/`reviewSetId` prop (confirmed in pass 1) — there's no code path for
a test to exercise that would fail if coupling were added, because coupling would require changing the
component's prop signature first, which itself would be caught by normal type-checking and code review.
**No new test needed here** — the existing architecture already makes this a compile-time rather than a
runtime concern. Likewise, retroactive family sync is structurally impossible because nothing stores which
family a Note's programs came from — **a focused repository test confirming `note_course_program` rows are
never written with any family-derived value (only explicit program ids) is enough**; no heavyweight
integration test is warranted.

---

## Q. Codex routing

Per current `CLAUDE.md`: this touches backend (new migration + new endpoint) and frontend together →
**Codex required: YES.**

**Implementation slices** (for the eventual Codex prompt — do not write the prompt yet, per instruction):

1. **Backend: catalog lifecycle + family-reassignment API.** Migration (`is_active` column), the new
   `PATCH /course-program-catalog/{id}` endpoint/service/repository method, list-endpoint filtering, all
   associated tests (§H, §M, §P).
2. **Frontend: family chip redesign.** Compact `Family · count` / `· N remaining` / inert `✓ Family · N`
   states in `applicable-programs-combobox.tsx`, inactive-row picker filtering, accessibility semantics
   (§J), extended test coverage.
3. **Docs.** `docs/features/program-families.md` update (§M).
4. **Data operations, not code** — owner-run via the new endpoint, sequenced per §O: Health Sciences (3
   rows) and Accounting (4 rows) family assignment; the `is_active` backfill for the 2 fused rows, gated on
   Health Sciences assignment being confirmed first.

These could plausibly be one Codex prompt (small, cohesive, one release) or two (backend slice, frontend
slice) depending on how the prompt-writing pass wants to scope it — both are reasonable; decide at
prompt-writing time, not here.

---

## Final decision block

```
PROGRAM FAMILY EXPANSION — FINAL IMPLEMENTATION PLAN

Product verdict: APPROVE
Program Family purpose: AUTHORING CONVENIENCE ONLY
Governance: CURRICULUM/WORKFLOW EVIDENCE + HIGH-PRECISION MEMBERSHIP, NOT ORGANIC CO-SELECTION ONLY

Existing families: Engineering (18), Education (8)

Health Sciences: APPROVE
Health Sciences members: Nursing, Medicine, Pharmacy
Physical Therapy: EXCLUDE INITIAL
Radiologic Technology: EXCLUDE INITIAL

Accounting-family decision: ACCOUNTING (not Accounting & Business)
Accounting-family members: Accountancy, Management Accounting, Accounting Information Systems,
  Internal Auditing (4)
Business Administration: EXCLUDE INITIAL — add manually when applicable (strongest case: RFBT and
  finance-flavored Management Services notes, ~22% of the CPALE plan)
Entrepreneurship: EXCLUDE INITIAL — no signal in the plan
Economics: EXCLUDE INITIAL, add manually when applicable — only 3 of 359 rows show a signal
Senior High – ABM: EXCLUDE INITIAL — no signal, and a structural depth mismatch independent of evidence
Real Estate Management: EXCLUDE INITIAL — no signal, too specialized
CMA: EXCLUDE INITIAL, OTHER (pending credential-vs-program taxonomy)
CFA: EXCLUDE INITIAL, OTHER (pending credential-vs-program taxonomy)

Finance Course / Program: ADD DURING CPALE CURATION (trigger likely to fire soon — see §G)
Finance Domain Context: NO

Family persisted on Note: NO
Family used for discovery: NO
Family sent to generation: NO
Family changes Domain Context: NO
Family changes Authored Depth: NO
Family inferred from Review Set: NO
Family selection: ADDITIVE

Partial family behavior: SHOW REMAINING COUNT ("Family · N remaining")
Full family behavior: VISIBLE + COMPLETE/INERT ("✓ Family · N"), never disappears
Completed family is persisted state: NO
Retroactive family sync: NO (provable by construction)
Family overlap: NOT SUPPORTED TODAY — known limitation, no schema change

Existing-program family management: ADD GENERIC ADMIN UPDATE/CLEAR CAPABILITY
  (PATCH /course-program-catalog/{id}, programFamilyId nullable)

Legacy Nursing · Medicine: DEPRECATE FROM NEW AUTHORING via is_active = false, preserved for existing Notes
Legacy Nursing · Pharmacy: DEPRECATE FROM NEW AUTHORING via is_active = false, preserved for existing Notes
Hardcoded frontend denylist: REJECTED — used the existing is_active convention instead (discount_vouchers,
  quiz_share_links precedent)
Legacy Note normalization (21 affected Notes): DEFER / FOLLOW-UP, not this release

Selected-program 18+ UX: NOT YET VERIFIED — acceptance check defined in §K, resolve at implementation time
Domain Context UI bug from prior prompt: NOT REPRODUCED — do not implement a fix for a nonexistent bug

Database migration: YES — one small additive column (is_active) + a gated 2-row backfill, sequenced per §O
API change: YES — one new ADMIN-only PATCH endpoint
Note backfill: NO
Catalog data operation: YES — 7 family assignments (3 Health Sciences + 4 Accounting) + 2-row is_active
  backfill, all via the new endpoint / a follow-up migration, owner-run, sequenced per §O

Recommended release composition: one release — migration + endpoint + chip UI + Health Sciences +
  Accounting family population, with the is_active backfill for the fused rows gated behind confirming
  Health Sciences is populated first (§O)

Codex required: YES — backend migration/endpoint + frontend chip redesign, per current CLAUDE.md routing

Biggest UX risk: the inert full-family accessibility semantics (§J) — resolved in principle, exact
  markup choice (disabled button vs. static status text) left to implementation time
Biggest taxonomy risk: Business Administration's exclusion is a judgment call built on external domain
  knowledge about how Philippine accounting-track programs share curricula, not a row-level citation from
  the TSV itself — worth a sanity check with the owner before the family ships, since it's the one
  conclusion in this plan not directly readable from a database query
Biggest data-quality risk: OVER-BROAD APPLICABLE PROGRAM ASSIGNMENT DURING CPALE CURATION — reinforced by
  a new finding (§D): the CPALE TSV's own applicable_programs column is currently uniformly
  under-tagged (Accountancy-only on all 359 rows), the mirror-image risk to the 35-note over-tagging
  incident, and several RFBT titles already violate the Note Title doctrine by naming "Accountancy" in
  the title itself — flagged for the owner, out of this plan's scope to fix

Next step after release: BEGIN CPALE COMPREHENSIVE AUTHORING USING CONSERVATIVE PROGRAM-FAMILY SHORTCUTS
  + NOTE-LEVEL APPLICABILITY REVIEW — and separately, correct the CPALE TSV's applicable_programs/title
  issues found in §D before or during that authoring pass
```

> Program Family is a shortcut, not truth. Saved individual Applicable Programs are truth.
> Historical co-selection can prove that a shortcut is useful, but curriculum semantics determine whether
> its membership is correct.
> Bulk-selection shortcuts should optimize precision before coverage.
> Course / Program answers WHO can study the knowledge. Domain Context answers HOW the knowledge should be
> treated. Authored Depth answers AT WHAT DEPTH it is written. Review Set answers WHERE the knowledge
> belongs in a learning journey.
> Building CPALE should create reusable canonical business/accounting knowledge for future Study Plans —
> not Accountancy-specific duplicates.

**DO NOT IMPLEMENT YET.**
