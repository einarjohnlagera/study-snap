# Program Family Expansion (Health Sciences + Accounting & Business) — Audit & Plan

**Status: SUPERSEDED by `program-family-health-accounting-expansion-final-plan.md` (pass 2, FINAL).
Kept for historical trace only — do not implement from this file. 2026-09-15.**
**Do not implement from this document as-is — several membership numbers below differ from the
original prompt's candidate lists, and one section (§0.2) flags a data-quality question that should be
resolved before either family's membership is locked.**

This responds to the "Program Family Expansion + Curator UX Polish" prompt. All findings below are
sourced from live code (file:line) and **live read-only production queries run today (2026-09-15)**
against `notelib-db-prod` — not from the prompt's assumed catalog or from prior planning docs' numbers,
which are 1-9 days old and, in one major case, already stale.

---

## 0. Headline findings — read this section first

### 0.1 The prompt's central premise was evaluated and rejected two days ago — and that rejection is now itself stale

`docs/claude-plans/domain-context-biomedical-business-calibration-stage1.md` (2026-09-13) and
`-stage2.md` (2026-09-14) — both committed, both indexed in `ROADMAP.md` (rows 565-566) — evaluated
almost exactly these two families against production data and ruled **"NO family for either group, and
do not bundle one into this work,"** citing: Health Sciences would over-select onto Radiologic Technology
(0 notes) and Physical Therapy (7 notes); Business/Commerce would have **zero cross-program business
notes** — "no repetitive authoring work to reduce."

**That was true on 2026-09-13/14. It is not true today.** Fresh queries run for this plan show:

| Measurement | Stage 1/2 (2026-09-13/14) | Live today (2026-09-15) |
|---|---|---|
| Notes with 2+ of {Nursing, Medicine, Pharmacy, Physical Therapy, Radiologic Technology} | not isolated this way in stage docs | **18 notes** carry exactly 3 (Nursing+Medicine+Pharmacy pattern); 1 note carries 2; **0 notes carry 4 or 5** |
| "Nursing · Medicine" fused catalog row usage | 2 → grew to 20 notes | **20 notes**, unchanged since stage 2 |
| "Nursing · Pharmacy" fused catalog row | not confirmed to exist by one research pass | **confirmed live, 1 note** |
| Radiologic Technology usage | 0 notes | **0 notes**, unchanged |
| Physical Therapy usage | 7 notes | **7 notes**, unchanged |
| Cross-program business notes | **0** | **35 notes**, each tagged with exactly 10 of the same business/accountancy programs simultaneously |
| Full catalog size | 51 rows (2026-09-13) | **60 rows** today |

**The business number is the one that matters: it went from 0 to 35 in roughly 24-48 hours.** This is a
real example of the CLAUDE.md rule "a claim about production state is a snapshot, not a fact" — the
stage 1/2 rejection was correct when written and is simply overtaken by events. The health-sciences
picture, by contrast, is essentially unchanged from stage 1/2 (still a tight 3-way cluster, still zero
PT/RT co-selection) — so that rejection's *reasoning* still applies, just pointed at a narrower membership
than either the original stage docs or this prompt proposed. See §C/§D.

### 0.2 ⚠️ CONFIRMED: the 35-note business cluster is a single bulk action, not accumulated curation — this is now a fact, not an open question

I pulled the 35 notes' titles first. All 35 are Intermediate/Financial Accounting topics — *Bonds Payable
and Effective Interest Method*, *PFRS 15 Five-Step Revenue Recognition Model*, *Segment Reporting*, *Petty
Cash and Cash Short or Over*, *Regulation and Sectors of the Philippine Accountancy Profession*, etc. —
`domain_context = ACCOUNTANCY` on every one, `status = GENERATED` (real, finished content, not stubs).
Every one of the 35 is tagged with **all ten** of: Accountancy, Management Accounting, Accounting
Information Systems, Internal Auditing, Business Administration, Entrepreneurship, Economics, Real Estate
Management, CMA, CFA.

**Then I ran the query the advisor pass flagged as decisive: `note_course_program.created_at`, bucketed by
minute, for exactly these 35 notes' join rows.** Result — all 350 rows (35 notes × 10 programs):

| Minute | Join rows written | Distinct notes |
|---|---|---|
| 2026-09-14 14:33 | 40 | 4 |
| 2026-09-14 14:34 | 100 | 10 |
| 2026-09-14 14:35 | 90 | 9 |
| 2026-09-14 14:36 | 80 | 8 |
| 2026-09-14 14:37 | 40 | 4 |

**Every one of the 350 rows landed inside one 5-minute window**, in a monotonic per-note sequence (10 rows
per note, notes processed back-to-back) — the signature of a single script or bulk-edit action, not
accumulated per-note curation. Confirming this further: Real Estate Management, CMA, CFA, Entrepreneurship,
Economics, Management Accounting, Accounting Information Systems, and Internal Auditing each sit at
**exactly 35 notes total, system-wide** (§ table in §0.1) — meaning **none of those eight programs has any
usage anywhere in the product outside this one action.** There is no independent corroborating signal for
any of them, unlike Nursing/Medicine/Pharmacy (§ below).

**Contrast — I ran the same check on the Health Sciences trio's 18 three-way-tagged notes:**

| Hour | Join rows | Distinct notes |
|---|---|---|
| 2026-08-10 14:00 | 16 | 16 |
| 2026-09-13 02:00 | 16 | 7 |
| 2026-09-13 14:00 | 35 | 13 |
| 2026-09-13 15:00 | 5 | 5 |

Spread across **five weeks and multiple separate sessions**, plus two independently-invented fused catalog
rows ("Nursing · Medicine" and "Nursing · Pharmacy," 21 more notes between them, created at different times
by what looks like different curator sessions reaching for a shortcut that didn't exist yet) — two
different actions, at different times, converging on the same trio. That is what real repeated
co-selection looks like, and it's the opposite pattern from the business cluster.

**Conclusion: this is settled, not an open question for the owner to recall.** The business-cluster tag
set is a single bulk action with zero independent corroboration for 8 of its 10 members. It is not evidence
of genuine shared applicability, and it should not be treated as the evidentiary basis for a new Program
Family. See the revised §D verdict below — this is no longer "MODIFY, conditional," it's **BLOCKED**.

### 0.3 Two architectural facts neither the prompt nor prior plans stated

- **`program_family_id` is a single nullable foreign key on `course_programs`, not a join table.** A
  program can belong to **at most one** family today. §25 of the prompt hopes overlap is "architecturally
  possible" — it is not, currently. No candidate member of either new family already belongs to Engineering
  or Education, so nothing forces a schema change *for this release*, but it's a real limitation against
  the stated long-term intent ("Program Families are reusable authoring sets, not exclusive taxonomy
  buckets") and should be recorded as a known limitation, not silently assumed away.
- **There is no endpoint to reassign an *existing* catalog program to a family.** `CourseProgramCatalogService.create()`
  sets `program_family_id` only at program-creation time; `CourseProgramCatalogController` exposes `GET/POST
  /course-program-catalog` and `GET/POST /course-program-catalog/families`, and nothing else (confirmed by
  reading the controller directly). Every single candidate member of both new families — Nursing, Medicine,
  Pharmacy, Accountancy, Business Administration, all of it — **already exists as a catalog row**. Standing
  up either family therefore requires reassigning existing rows, which today has no code path at all. This
  is a real, small, backend change (see §L), not a data-only operation — contrary to the prompt's §28
  expectation of "no migration by default" (true) reading as "no code change" (not true).

---

## A. Revalidated architecture

Program Family is **fully generic and DB-backed already** — this is not the hardcoded, per-family-code
situation the prompt's §3 worried about.

- `program_families` table (`V106__course_program_catalog.sql:1-6`): `id`, unique `name`. `course_programs.program_family_id`
  is a nullable FK into it (`V106:7-19`).
- Backend: `CourseProgramCatalogRepository` (family reads/writes), `CourseProgramCatalogService` (`listProgramFamilies()`,
  `createProgramFamily()`), `CourseProgramCatalogController` (`GET/POST /course-program-catalog/families`, both
  `@PreAuthorize("hasRole('ADMIN')")`). DTOs: `ProgramFamilyResponse`, `CreateProgramFamilyRequest`. Exceptions:
  `ProgramFamilyNameConflictException`, `UnknownProgramFamilyException`, `InvalidProgramFamilyNameException`.
- Frontend: `frontend/components/metadata/applicable-programs-combobox.tsx` derives family groupings **dynamically
  from the fetched catalog** at render time (`availableProgramFamilies`, grouping by `programFamilyId`/`programFamilyName`)
  — there is no static per-family list or "Engineering" literal anywhere in this component. `handleFamilyExpansion`
  only pushes catalog program ids into the same `selectedIds` array a manual pick would produce; nothing about
  "family" is persisted anywhere on the Note.
- The button label's count (`"Add all N ... programs"`) is **`unselectedCount`, not the family's total size** —
  recomputed via `useMemo` on every render from `memberIds` minus currently-selected ids, and families at
  `unselectedCount === 0` are filtered out of the row entirely rather than shown as a disabled/zero button.
- Guard against Domain-Context coupling is explicit in code, not just convention:
  `applicable-programs-combobox.tsx` carries a comment directly above `handleFamilyExpansion` citing ADR-001
  and stating the component "deliberately receives no note context, which is the structural guard; do not
  add one" — confirmed structurally: its props include no `domainContext`, `learnerLevel`, or review-set field.
- Feature doc `docs/features/program-families.md` states the binding tripwire: *"If Program Families acquire
  subject rules, context rules, learner-level rules, curated subsets, read-time inference, or other
  curriculum intelligence, the feature has exceeded its responsibility."*
- **Creating a family today costs zero migration and zero code** — `POST /course-program-catalog/families`
  already exists (shipped in `v0.133.0`, specifically so a third family would never need a migration the way
  Education's `V142` did). The gap is narrower than "can we add families" — it's "can we point *existing*
  catalog rows at a newly created family" (§0.3).

## B. Current family definitions (verified live)

**Engineering — 18 members**, all currently unassigned to any other family:
Aeronautical Engineering, Agricultural and Biosystems Engineering, Architectural Engineering, Chemical
Engineering, Civil Engineering, Computer Engineering, Construction Engineering and Management, Electrical
Engineering, Electronics Engineering, Environmental Engineering, Geodetic Engineering, Geological
Engineering, Geotechnical Engineer, Industrial Engineering, Marine Engineering, Mechanical Engineering,
Mining Engineering, Sanitary Engineering.

**Education — 8 members**:
Early Childhood Education, Education, Elementary Education, Physical Education, Secondary Education,
Special Needs Education, Teacher Certification, Technical-Vocational Teacher Education.

Both counts are **derived from the live FK relationship, not hardcoded** — confirmed by the query above and
by the frontend's `useMemo` deriving `unselectedCount` from `memberIds.length` every render. No drift is
possible between "18"/"8" and reality; those numbers are exactly what the button currently displays.

## C. Proposed Health Sciences family — recommend **MODIFY**

**Prompt's candidate (5):** Nursing, Medicine, Pharmacy, Physical Therapy, Radiologic Technology.

**Recommended membership (3): Nursing, Medicine, Pharmacy.**

Live usage: Nursing 339 notes (incl. combos), Medicine 19, Pharmacy 30, Physical Therapy 7, Radiologic
Technology **0**. Cross-tabulating notes with 2+ of the five: **18 notes carry exactly 3** (the
Nursing+Medicine+Pharmacy pattern — consistent with Nursing/Medicine/Pharmacy totals), **1 note carries 2**,
**zero notes carry 4 or 5**. Additionally, the fused catalog rows "Nursing · Medicine" (20 notes) and
"Nursing · Pharmacy" (1 note) are themselves curator workarounds for exactly this trio (§F) — more
behavioral evidence pointing at the same 3, not 5.

This satisfies the prompt's own instruction in §4 ("optimize common curator work, not represent every
theoretically related discipline") more faithfully than the original 5-member list: Physical Therapy and
Radiologic Technology would be **unconditional over-selection** on every single use today (ADR-001's
"expansion is unconditional" rule means adding them to the family adds them to every note the family
expands into, regardless of fit) — which is exactly what stage 1/2 flagged, and the fresh numbers confirm
unchanged. **Excluded, pending future evidence:** Physical Therapy, Radiologic Technology — no code or
doc change needed to add them later; it's a data-only reassignment once real cross-program use appears.

## D. Proposed Accounting & Business family — recommend **BLOCKED, do not ship this release**

**Prompt's candidate (8):** Accountancy, Management Accounting, Accounting Information Systems, Internal
Auditing, Business Administration, Entrepreneurship, Economics, Senior High – ABM.

§0.2 settles this with a timestamp query, not inference: the only production evidence for any
cross-program business pattern is 35 notes whose 350 join rows were **all written inside one 5-minute
window** on 2026-09-14, and 8 of the proposed 10 members (Management Accounting, Accounting Information
Systems, Internal Auditing, Entrepreneurship, Economics, Real Estate Management, CMA, CFA) have **zero
usage anywhere else in the product** — every note carrying any of them is one of the same 35. That is a
single bulk action, not a repeatedly-observed curator pattern, and it fails §24's own governance bar
("a stable cluster of related Course/Programs is repeatedly selected together during canonical Note
authoring") on its face — there has been exactly one selection event, ever.

**Recommend: do not create this family this release, on this evidence.** Building a one-click "add all
10" shortcut on top of an unreviewed bulk-tag would entrench, not fix, the exact anti-pattern §10 of the
prompt warns against — and would make it one click to reproduce the same over-broad tag set on the *next*
35 notes. Two paths forward, either of which is fine, neither of which is "ship as originally proposed":

1. **The owner reviews the 35 notes' applicability by hand** (Accountancy, Management Accounting, AIS,
   Internal Auditing, and Business Administration are the plausible core — PFRS-level financial reporting
   is genuinely shared curriculum for those; Real Estate Management, Entrepreneurship, Economics, CMA, and
   CFA are the ones with the weakest title-level case) and re-saves each note's actual applicable programs.
   Once *that* review is done, if a real pattern remains across a meaningful subset, propose a
   (likely smaller) family from the corrected data — a fresh, second pass, not this one.
2. **Wait for organic accumulation**, the same way Nursing/Medicine/Pharmacy earned its family — real
   per-note curation over multiple sessions, the pattern this plan's §C is built on.

Either way, this is not a code or architecture question — nothing here blocks shipping Health Sciences
(§C) or the UI/endpoint work (§I/§L) this release without Accounting & Business.

## E. Finance decision: **ADD DURING CPALE CURATION** (unchanged from stage 1/2; trigger has not fired)

Confirmed live: no "Finance" row exists in `course_programs` (60 rows, zero matches). The 35-note business
cluster is Financial Accounting/PFRS content (see §0.2's title list) — **not** the Financial
Management/Finance topics the prompt names in its own §9 (Time Value of Money, Risk and Return, Cost of
Capital, Capital Budgeting, Working Capital, Capital Structure, Leverage, Dividend Policy). The
previously-settled trigger (`domain-context-biomedical-business-calibration-stage2.md` §B7: *"the first
canonical Note encountered during CPALE curation that is genuinely applicable to Finance is sufficient
evidence"*) has **not fired** — none of the 35 notes are Finance topics. Recommend leaving this exactly as
already decided: add Finance the moment a genuine Finance-topic canonical note is authored, not now, and
**do not create a Finance Domain Context** (unchanged — Course/Program governance and Domain Context
governance are different gates, per ADR-001).

## F. Legacy fused-program decision — both fused rows exist; recommend **DEPRECATE FROM NEW AUTHORING**

Correction to the record: **both** "Nursing · Medicine" (20 notes) **and** "Nursing · Pharmacy" (1 note)
exist as live `course_programs` rows — not just the first, as one research pass concluded from a
code-only search (both were created at runtime via `POST /course-program-catalog`, which has no name-format
restriction beyond length/uniqueness, so a codebase grep for migrations misses them entirely).

1. **Why they exist:** no migration created either — both are curator-invented workarounds for bundling
   Nursing+Medicine and Nursing+Pharmacy into one selectable catalog string, made before any Program Family
   existed for the health cluster.
2. **Production usage:** confirmed live — 20 and 1 notes respectively, both `GENERATED`/real content.
3. **No filter, discovery, URL, or Review Set logic special-cases either string** — the middle-dot
   character is just the app's generic multi-value display separator (`.join(" · ")`), used pervasively
   elsewhere; these are opaque catalog names to every consumer.
4. **Still selectable for new authoring:** yes — `GET /course-program-catalog` returns every row with no
   filtering, and there's no DELETE/rename endpoint.
5. **Safe to deprecate from new selection:** yes, once the Health Sciences family ships — a curator who
   wants Nursing+Medicine+Pharmacy together can click one family button instead of hunting for a fused
   string. Deprecation only needs to hide the two rows from the **selection list for new picks**; existing
   Notes referencing them are untouched (the join table stores program ids, not names — nothing about an
   existing Note's data changes). **⚠️ Deploy-ordering constraint: the fused-row filter must not go live
   until the 3-row Nursing/Medicine/Pharmacy family assignment is confirmed done in production** (endpoint
   deployed is not sufficient — the owner must have actually run it). Filter-first-then-assign leaves a
   curator with neither the fused shortcut nor a populated family chip for the same trio — strictly worse
   than today. This is the same class of statement CLAUDE.md requires for any two-sided deploy: say the
   order, don't assume both sides land together.
6. **Cleanup path:** recommend a client-side denylist filter (by id) in `applicable-programs-combobox.tsx`'s
   catalog-rendering step — zero migration, zero backend change, reversible by editing one array. Do
   **not** add a `hidden_from_authoring`/`is_deprecated` column this release; that's a real schema change for
   a problem a filter already solves, and would need a migration this plan is explicitly trying to avoid.

## G. Credential-program audit (CMA / CFA) — recommend **BACKLOG, do not block**

Both exist as live catalog rows (35 notes each, all inside the §D cluster) — corrected from an earlier
research pass that found zero hits, because both were added at runtime, invisible to a migrations/code-only
grep. There is **no separate architectural axis** for "credential track" vs. "academic program" —
`course_programs` is a single flat table with no type/kind discriminator, so today CMA and CFA are
*structurally* indistinguishable from Nursing or Accountancy. In production use they're already being
applied exactly like ordinary programs (co-tagged on real notes alongside Accountancy/Business
Administration). Per the prompt's own §8 instruction, this is a taxonomy question worth recording, not one
that should gate this release — no architecture exists today that would even need to change to include them
in a family, since family membership only cares about `program_family_id`, not what *kind* of program a row
represents.

## H. Final Program Family semantics (durable contract, restated with one gap closed)

- **Authoring only; additive expansion; no persistence on Note** — confirmed structurally (§A): the join
  table stores program ids, family concept never appears in any Note DTO or entity.
- **No retroactive sync** — confirmed by construction, not just by policy: because family membership isn't
  stored anywhere on the Note, a later change to a family's membership has no mechanism by which it *could*
  reach an already-saved Note. This needs a regression test (§N) precisely because it's easy for a future
  change to accidentally add one, not because today's code is at risk.
- **Overlap: NOT ALLOWED today** — `program_family_id` is a single nullable FK, not a join table (§0.3).
  Flag as a known limitation; no candidate member of either new family needs it this release.
- **Domain Context independent** — confirmed both structurally (single-`String` field on the generation
  context, incapable of holding a program list) and by an explicit code comment in the combobox component
  citing ADR-001. This is enforced by several separate mechanisms rather than one named rule; recommend
  this plan's shipped feature doc update state the **reverse** direction explicitly ("Family/Program
  selection must never set or suggest Domain Context") since ADR-001 currently states only the forward
  direction.
- **Review Set independent** — no coupling found in the combobox's props or in `NoteApplicableProgramsService.replace`,
  but this was not verified with the same rigor as Domain Context (no dedicated test asserts it). Recommend
  an explicit test (§N) rather than treating "nothing found" as proof.
- **Discovery through individual programs only** — unchanged, ADR-001 axis rule.

## I. UI redesign

Today's row (`applicable-programs-combobox.tsx`) is `flex flex-wrap gap-2` of `size="sm"` outline buttons,
each carrying a full sentence ("Add all 18 Engineering programs") — not full-width stacked buttons as the
prompt's §14 assumed, but full-sentence labels that will wrap awkwardly once there are 4 of them and badly
past that.

**Recommend:** shorten each family control to a compact chip in the `"{Name} · {count}"` shape the
prompt's own §15 sketches (e.g. `Health Sciences · 3`; Accounting & Business does not get a chip this
release per §D — the redesign is generic to N families regardless), grouped under an
explicit "Program families" label so a curator understands these are shortcuts, not selections. Keep the
row as horizontally-wrapping chips (prompt's §16 option D) — with 4 families expected near-term this needs
no popover, hover, or overflow menu; revisit only if family count grows past ~6-8. **Full-family state:**
today a family disappears entirely once `unselectedCount === 0` (§A) — recommend changing this to an inert,
checked-looking state (`✓ Health Sciences · 3`, no click handler) rather than vanishing, so a curator can
see at a glance which families are already fully represented rather than wondering whether one exists.
This is a real UX call, not a code constraint either way — flagging for Product UX to pick (§ "Open
questions").

**Selected-programs area:** the prompt's §17 worry (18+ chips overwhelming the form) is real and current —
Engineering alone can dump all 18 into the selected-chips row today. **Not independently verified by this
audit** whether the existing multiselect already handles this gracefully (e.g. via wrapping alone) or needs
a collapse-past-threshold treatment; flagging as open rather than asserting either way. Recommend checking
this concretely during implementation and, if needed, a `"{n} programs selected — Expand"` collapse past
~8 chips, preserving individual removal once expanded.

## J. Copy

- **Course / Program(s) helper (all 3 authoring surfaces, unified):** *"Choose every program this Note
  genuinely applies to."* Secondary, only if still needed: *"Programs control applicability and
  discovery, not how the Note is written."*
- **Program families label:** *"Program families"* above the chip row — no further explanation needed if
  the chips themselves read as shortcuts (`Family · count`).
- **Domain Context, ≤1 program:** keep existing dynamic pattern; recommend unifying the three surfaces'
  slightly different current strings (`bulk-generation-page-client.tsx`'s *"Required when this note applies
  to more than one program"* vs. the other two's more accurate *"Needed before you can generate a Study
  Pack for a note in more than one program"* — the latter phrasing is preferred, since it correctly
  distinguishes save-validity from generation-readiness per `docs/features/notes.md`).
- **Domain Context, 2+ programs:** keep existing *"You've added more than one program. Choose the academic
  domain this note should be written in — it shapes how the note is written, while the programs decide who
  finds it."* (already good, no change needed).
- **Selected-programs collapse label (if built):** *"{n} programs selected — Expand"*.

**Note on the prompt's §19:** the described "optional" label / "required" helper text contradiction was
checked across all three note-authoring surfaces that render a Domain Context field, plus the fourth
combobox consumer (admin surface, which has no Domain Context field at all) — **the contradiction as
literally described was not found**; label and helper text are already gated on the identical
`programCount > 1` condition in code, so they cannot disagree simultaneously in any of the checked
surfaces. If this was seen live on a specific page, naming that page would help — it might be a stale
client bundle, or the minor cross-surface phrasing inconsistency noted above read as a contradiction at a
glance. Either way, the copy unification above addresses the phrasing gap regardless of cause.

## K. Interaction matrix

| State | Behavior |
|---|---|
| No programs selected | No selected-chips row; family chips show full counts |
| One program | Domain Context optional; family chips show counts minus that one if it's a member |
| Partial family | Chip reads `Family · k remaining`; click adds the missing members only |
| Full family | **Open design call** — disappear (current) vs. inert checked state (recommended, §I) |
| Multiple families | Each computes `unselectedCount` independently; clicking one never touches another's members (existing test already asserts this) |
| Overlapping family membership | Not currently possible (§0.3/§H) — no test needed until the schema changes |
| 18+ selected programs | **Open**, not verified this pass — see §I |
| Remove one member after family expansion | Existing "×" chip removal, unrelated code path — unaffected by any of this |
| Add family after manual selections | Additive-only by construction (`if (!nextIdSet.has(memberId))`) — existing test already asserts manual selections survive a subsequent family expansion |

## L. Exact implementation blast radius

| File | Current | Planned change | Risk | Tests |
|---|---|---|---|---|
| `CourseProgramCatalogController.java` | No reassign endpoint | **MUST CHANGE** — add `PATCH /course-program-catalog/{id}` (ADMIN-only) to set `programFamilyId` on an existing row | Low — additive endpoint, mirrors existing create pattern | New `MockMvc` test with real `Content-Type: application/json`, per this repo's own transport-testing rule |
| `CourseProgramCatalogService.java` / `Repository.java` | `create()` sets family only at insert | **MUST CHANGE** — add `reassignFamily(id, familyId)` | Low | Unit test + real-DB integration test (existing `CourseProgramCatalogRepositoryProgramFamilyIntegrationTest` pattern) |
| `applicable-programs-combobox.tsx` | Full-sentence buttons; family derivation already generic | **MUST CHANGE (visual only)** — shorten button labels to `Family · count` chips, add inert full-state. **Do NOT touch `availableProgramFamilies`/`handleFamilyExpansion` derivation logic** — `program-family-generalization-and-education-family.md` explicitly settled that this logic is already family-count-agnostic; touching it would be unscoped drift | Low if scoped to rendering only | Existing `applicable-programs-combobox.test.tsx` suite extended for new chip states |
| Same file | Catalog list includes fused rows | **MUST CHANGE** — client-side denylist filter hiding "Nursing · Medicine" / "Nursing · Pharmacy" from new-selection rendering | Low, reversible, no schema | New test: fused rows absent from render, still resolvable by id for any note that already has them |
| `docs/features/program-families.md` | Documents 2 families, doesn't state reverse Domain-Context rule | **MUST CHANGE** — add Health Sciences + Accounting & Business, state the reverse guard explicitly (§H) | n/a | n/a (docs) |
| Admin catalog data (production) | Nursing/Medicine/Pharmacy unassigned | **Data operation, not code** — owner runs the new PATCH endpoint via the admin UI (or a one-time `.sql` if the endpoint is rejected) to assign `programFamilyId` on the 3 Health Sciences rows. Accounting & Business rows are **not** touched this release (§D — blocked) | Low — reversible, no migration | n/a |
| `program_families` / `course_programs` schema | Single nullable FK | **NO CHANGE** this release (§0.3 limitation recorded, not fixed) | — | — |
| `DomainContext` enum / resolver | 12 values, no coupling | **NO CHANGE** | — | — |
| Review Set logic | No coupling found | **NO CHANGE**, but add the missing explicit test (§N) | — | New test |

## M. Database / API impact

- **Database migration: NO.**
- **API change: YES, small** — one new `PATCH /course-program-catalog/{id}` (ADMIN-only), reusing existing
  validation/exception patterns (`UnknownProgramFamilyException`, etc.).
- **Persist Program Family on Note: NO.**
- **Backfill: NO, and note the distinction this hides:** "backfill" here would mean touching every existing
  *Note's* data, which nothing in this plan does. What *is* needed — pointing 3 pre-existing **catalog
  rows** (Nursing, Medicine, Pharmacy) at a new family id — is a one-time catalog-config write, not a
  Note-data backfill, and it's 3 rows, not the ~13 the original candidate lists implied, because
  Accounting & Business is not populated this release (§D). Done via the new PATCH endpoint as an admin
  action post-deploy, or a one-time owner-run `.sql` if the endpoint is dropped from scope.

## N. Test plan

- `CourseProgramCatalogControllerTest.java`: new `PATCH` endpoint is ADMIN-only (reflection-based
  `@PreAuthorize` check, matching the existing pattern at L43-61); a **real `MockMvc` request** with
  `Content-Type: application/json` and a body proves the transport works, not a bare method call — this
  repo's own `v0.119.0` incident (two JSON POSTs with no `Content-Type`, invisible to 2,182 passing tests
  because everything mocked `lib/api` or called the handler directly) is exactly the class of gap to avoid
  here.
- `CourseProgramCatalogServiceTest.java`: reassigning an existing program's family; rejecting an unknown
  family id; rejecting a nonexistent program id.
- `applicable-programs-combobox.test.tsx`: new chip rendering (`Family · count`); full-family inert state
  (no click handler, `aria-pressed`/similar semantic, not just visual); fused rows absent from the
  rendered list; existing additive/cross-family-isolation tests untouched (confirms no drift into the
  derivation logic §L flags as out of scope).
- `lib/api-program-families.test.ts`: extend for the new endpoint's exact request shape (byte-for-byte, per
  the existing pattern in this file — it was written specifically to catch a mocked-API test suite passing
  while the real request is malformed).
- **New, currently missing:** a test asserting selecting/expanding a Review Set has no effect on Applicable
  Programs or Domain Context (§H) — this invariant exists in code today but isn't independently proven the
  way the Domain-Context-independence invariant is.
- **New, currently missing:** a test asserting a later change to a family's membership does not alter an
  already-saved Note's `note_course_program` rows (§H) — provable today because there's no mechanism that
  could do otherwise, but worth pinning before any future change could introduce one.

## O. Release recommendation

Ship the family-reassignment endpoint, the UI chip redesign, the Health Sciences family (Nursing/Medicine/Pharmacy),
and the fused-row deprecation-from-new-authoring together — all frontend-plus-thin-backend, one coherent
release, no migration. **Deploy order matters within this release** (§F point 5): the fused-row filter
must not activate until the owner has actually run the 3-row family assignment in production — code
deployed is not the same as the data operation completed. **Accounting & Business does not ship this release** (§D) — the infrastructure
(endpoint, chip UI) is generic and ready for it the moment the §D review resolves, but no family should be
created or populated on the current evidence. Finance catalog addition: separate, later, tiny (a
single-row insert when the CPALE trigger fires) — do not fold into this release. Credential taxonomy
cleanup (CMA/CFA axis question): backlog, no release attached.

---

## Final decision block (recommended — DRAFT, pending Product UX tightening)

```
PROGRAM FAMILY EXPANSION — IMPLEMENTATION PLAN (DRAFT)

Product verdict: SPLIT — Health Sciences ships this release, Accounting & Business does not (see §C/§D)
Program Family purpose: AUTHORING CONVENIENCE ONLY

Existing families: Engineering (18), Education (8) — both verified live, both derived not hardcoded

New family — Health Sciences: MODIFY, APPROVE — ships this release
Health Sciences members: Nursing, Medicine, Pharmacy  (3 — drop Physical Therapy, Radiologic Technology
  pending future evidence). Evidence: 18 notes accumulated a real 3-way tag across five weeks and
  multiple sessions, plus 21 more notes across two independently-invented fused catalog rows — genuine
  repeated co-selection, clears §24's governance bar.

New family — Accounting & Business: BLOCKED — does not ship this release
Accounting & Business is not created or populated this release. The only production evidence for any
  10-program business cluster is 35 notes whose 350 join rows were all written in one 5-minute window on
  2026-09-14 — a single bulk action, not repeated curation. 8 of the 10 proposed members have zero usage
  anywhere else in the product. See §D for the two paths forward (owner reviews and re-tags the 35 notes
  by hand, or wait for organic accumulation).

Family persisted on Note: NO
Family used for discovery: NO
Family sent to generation: NO
Family changes Domain Context: NO
Family changes Authored Depth: NO
Family inferred from Review Set: NO
Family selection: ADDITIVE
Partial family behavior: chip reads "Family · k remaining"; click adds missing members only
Full family behavior: OPEN — recommend inert "✓ Family · N" state, not disappearance (current behavior)
Retroactive family sync: NO (provable by construction — no storage path exists)
Family overlap: NOT ALLOWED (schema is a single nullable FK; flagged as a known limitation, not fixed
  this release)

Finance Course / Program: ADD DURING CPALE CURATION (trigger not yet fired — the 35-note business
  cluster is Accounting Standards content, not Finance/Financial-Management content)
Finance Domain Context: NO

Nursing · Medicine: DEPRECATE FROM NEW AUTHORING (client-side filter; 20 existing notes preserved)
Nursing · Pharmacy: DEPRECATE FROM NEW AUTHORING (client-side filter; 1 existing note preserved;
  corrects the record that only "Nursing · Medicine" exists — both fused rows are live)

CMA/CFA catalog issue: BACKLOG — both already live, already in real production use, no architecture
  distinguishes "credential" from "academic program" today; not blocking this release

UI: REPLACE FULL-SENTENCE "ADD ALL N ... PROGRAMS" BUTTONS WITH COMPACT "FAMILY · COUNT" CHIPS

Domain Context optional/required contradiction: NOT FOUND AS DESCRIBED in any of the 4 authoring
  surfaces checked — label and helper are already gated on the identical condition; a minor
  cross-surface phrasing inconsistency was found and is addressed by the copy unification in §J

Database migration: NO
API change: YES — one new ADMIN-only PATCH endpoint (course-program family reassignment), because
  Nursing/Medicine/Pharmacy are pre-existing catalog rows and no reassignment path exists today; the
  same endpoint is what Accounting & Business would use later, once §D resolves
Backfill: NO

Recommended release count: 1 (Health Sciences family + reassignment endpoint + chip UI redesign +
  fused-row deprecation together; Accounting & Business excluded — see §D)

Codex required: YES — touches backend (new endpoint) and frontend together; per CLAUDE.md's routing
  table this is a Codex prompt, not Claude-Code-direct, regardless of how small the endpoint is

Biggest UX risk: the "full family" disappear-vs-inert design call (§I) — left as an open question
Biggest taxonomy risk: §0.2 (RESOLVED, not merely flagged) — the 35-note business cluster is a confirmed
  single bulk action, not accumulated curation; shipping Accounting & Business on top of it would have
  entrenched exactly the anti-pattern this feature is meant to prevent, which is why it's blocked rather
  than shipped-with-caveats

Next step after release: BEGIN CPALE COMPREHENSIVE AUTHORING WITH HONEST CROSS-PROGRAM APPLICABILITY
```

> Program Families make related programs faster to select; they do not decide which programs a Note
> actually applies to.
> Course / Program answers WHO can study the knowledge. Domain Context answers HOW the knowledge should
> be treated. Review Set answers WHERE the knowledge belongs in a learning journey.
> Building CPALE should also build reusable business knowledge for future Study Plans — not
> Accountancy-specific duplicates.

---

## Open questions for Product UX / owner to resolve in the tightening pass

1. **§0.2 is resolved by data, not open** — the 35-note tag set is confirmed a single bulk action
   (§0.2's timestamp query). The remaining decision is what to do about it: does the owner want to review
   and re-tag those 35 notes by hand now (§D path 1), or leave Accounting & Business to accumulate
   organically before proposing it again (§D path 2)? Either is fine; shipping the family on the current
   evidence is not.
2. **Health Sciences: 3 members (measured) vs. the original 5 — any new information justifying Physical
   Therapy/Radiologic Technology inclusion despite 0-7 notes of usage?**
3. **New backend endpoint vs. owner-run one-time SQL** — does adding one small ADMIN PATCH endpoint clear
   the bar, or would the owner prefer a pure data operation with zero code this release? (Affects §L/§M/§O
   and whether this still needs a Codex prompt at all.)
4. **Full-family visual state** — disappear (current) or inert "✓ Family · N" (recommended)?
5. **Does "UI redesign" include the selected-programs area (§I's 18+-chip question), or only the family
   button row?** Left open, not verified this pass.

**Not this plan's job, flagged only for awareness:** `ROADMAP.md`'s Backlog Index row near line 725
("Program Family management — no way to create a family... without a migration") is stale — that shipped
in `v0.133.0` — and the four `docs/curriculum/cpale-*` artifacts dated today look unindexed. Both are
housekeeping for whoever next touches the Backlog Index, not part of this feature's scope.
