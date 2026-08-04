# Release B (`v0.71.0`) — slice sequence

Scoped 2026-08-04, at kickoff. Governs how `RELEASES.md` v0.71.0's Planned Scope is cut, the way
`09-release-a-pr-sequence.md` did for Release A.

**Three slices, split along the irreversibility boundary rather than by layer.** Release A split by
layer because every part of it was additive and reversible. Release B is not: ADR-001's Consequences
say step 3 is *"not reversible once filters and badges read the join."* That single sentence is what
this sequence is organised around.

---

## The finding this sequence rests on: Release B is not blocked. One third of it is.

Both `RELEASES.md` v0.71.0 and the ROADMAP were opened with Planned Scope deliberately empty,
"blocked on the applicability question." **That is too broad a reading of ADR-001's own gate.**

The ADR's exact wording, in the Ratified value set section:

> **Applicability defaults for these values are NOT yet verified against current PRC board syllabi**
> and must be curator-checked **before family-expansion defaults are set**.

Scoped to **family-expansion defaults**. Not to the join table, not to the backfill, not to a curator
adding programs to a note by hand. And ADR-001 rule 5 says the same thing from the other side:

> **Program Families are an authoring shortcut only.** Selecting a family expands to explicit
> `note_course_program` rows at save time. Applicability is never inferred from a family at read time.

**A curator marking one Algebra note applicable to eleven engineering programs *is* the per-note
judgment.** It needs no syllabus table. What needs the syllabus table is the *preset* that fills
those eleven in with one click.

So the authoring unblock the entire ADR exists for — one canonical note, many programs, no
duplication — is reachable in slice 1, before the irreversible step and before the curator pass.

---

## Slice 1 — `note_course_program`, 1:1 backfill, admin write surface

**Additive and reversible. Not gated on anything. This is the slice that delivers the ADR's purpose.**

- New join table `note_course_program` (note id, course program id), with the obvious uniqueness
  constraint and an index supporting lookup by note.
- **Backfill exactly one row per note** from the existing `notes.course_program` string, resolved
  against the `course_programs` catalog `v0.70.0` seeded. A note whose string has no catalog row gets
  **no join row** — the same NULL-FK outcome `V106` already established for unmappable values, and
  for the same reason. Do not invent rows for values the catalog deliberately excluded.
- Admin/Teacher UI to add and remove Applicable Programs on a note, sourced from the catalog. Per
  `AGENTS.md:1222` this is a combobox/dropdown over catalog rows — never freetext.
- **`notes.course_program` is not touched, not cleared, not deprecated.** Every read path still uses
  it. ADR-001's second Legacy-data corollary still defers clearing out of scope entirely.

**Deliberately not in this slice:** no filter, facet, badge, search predicate, or URL reads the join.
Nothing user-facing changes for a learner.

**Migration number: derive numerically at write time.** `V106` is the current max; a lexical `ls`
reports `V99`. This repo already shipped a migration-collision hotfix (`v0.47.1`).

**Verification:** every note that had a catalog-resolvable program has exactly one join row; notes
carrying an excluded value (`Civil Service`, `Biology`, `Grade School`, `High School`, …) have zero
and keep their string; no `course_program` string is modified. Verify against local Postgres in
`BEGIN`/`ROLLBACK` — the H2 suite hand-writes table DDL and proves nothing about the real schema.

## Slice 2 — read paths move to the join

**This is the irreversible commitment. Ship it deliberately, not as a follow-on to slice 1.**

Filters, facet counts, badges, and the search predicate move to join/`EXISTS` semantics across
`NoteLibraryRepositoryImpl` and `PublicLibraryRepositoryImpl`.

**The equivalence test that makes this safe, and it only exists while slice 1 is fresh.** Facet counts
today are `count(*)` grouped by `course_program` on `notes` (`NoteLibraryRepositoryImpl:195-204`). At
exactly one join row per note, the join version returns **identical counts**. So the regression test is
concrete rather than a judgment call:

> Same filter, same facet counts, same result sets, before and after — asserted on real data, while
> every note still has exactly one program.

**Run slice 2 before curators start adding second programs**, or that equivalence is gone and the
migration becomes untestable against a known-good baseline.

**Known and correct consequence, not a bug:** once notes carry several programs, facet counts sum
above the note total. ADR-001 says this explicitly and calls for a UI affordance, not a fix.

**Known risk:** this is a hot paginated path that already required a dedicated performance release
(`v0.51.0`). Budget for query-plan work, and do not assume an `EXISTS` rewrite is free.

**Also retires here:** `ExamGoalConfig`'s program lists already read the catalog as of `v0.70.0`;
check whether anything else still resolves applicability through the legacy string.

## Slice 3 — Program Family expansion

**The only slice gated on the curator pass.**

Selecting a family (e.g. `Engineering`) expands to explicit `note_course_program` rows at save time.
The expansion **preset** — which programs a family fills in, and which subjects justify it — is the
curriculum fact ADR-001 says must be curator-checked first.

**Gate:** `[EFFORT]`, not `[EVIDENCE]`. No query answers "does `Engineering Sciences` span 8 or 11
engineering programs." It is a syllabus reading, and it blocks nothing in slices 1 and 2.

**R4 did not settle this.** R4 validated the Domain Context *value set* — that a broader value does
not degrade authored content. It says nothing about which programs share which subjects. Do not let
"R4 passed" be read as "applicability is settled"; ADR-001, `RELEASES.md`, and the ROADMAP R4 row all
now carry that caveat explicitly.

---

## Sequencing notes

**Dependencies:** slice 2 needs slice 1. Slice 3 needs slice 1 and the curator pass; it does **not**
need slice 2. So if the curator answer arrives early, slice 3 can land before slice 2 — the ordering
here is by risk, not by hard dependency.

**Every slice that changes behavior updates its feature docs in the same PR** — `docs/features/notes.md`,
`library.md`, `public-library.md`, and `explore.md` for slice 2; `notes.md` and `admin-dashboard.md`
for slice 1. Per `CLAUDE.md`, updating `RELEASES.md` alone is not enough.

**Routing:** all three slices are backend + migration + frontend, so all three are Codex prompts, not
inline work.

**Pre-signoff pressure test:** this release will meet the full-pressure-test bar on the same two
counts `v0.70.0` did — one concept across 3+ surfaces, and more than one PR touching the same
repository methods. Budget for it. On `v0.70.0` it caught a permanently-memoised fallback and a live
Public Library filter being silently rewritten, neither visible to per-PR review.

**`/audit-diff` after every Codex delivery**, heaviest on slice 2.

## Decisions this sequence does NOT reopen

1. **`course_program` strings are never cleared** — ADR-001's second Legacy-data corollary, deferred
   out of Release A entirely and not revived here.
2. **The catalog's exclusions stand** — bare levels, goals, subjects, the `Engineering` family, and
   the owner-ratified `Computer Science` / `Software Engineering` exclusion. A value with no catalog
   row gets no join row.
3. **Review Sets compose freely.** A Review Set may contain any note regardless of Applicable
   Programs, and its own course/program stays a curation label — never derived from, never validated
   against, its notes.
4. **`notes.target_profile_type` survives unchanged.** Whether precise program facets make that
   coarse audience facet redundant is judged at the **end of slice 2**, against real filter usage —
   ADR-001 says so, and it is not an input to scoping.
