# docs/archive/

Cold storage for content that used to live in an actively-read doc but is no longer needed
in the common case — either because the work it describes has shipped, or because it's a
point-in-time artifact whose finding has already been absorbed into current docs. Nothing
here is deleted history; it's moved here specifically so it doesn't cost tokens to read on
every pass through the live docs, while staying searchable (`git grep`) when it's actually
needed.

## Contents

- **`RELEASES_ARCHIVE.md`** — full `RELEASES.md` sections for v0.40.1 and earlier. `RELEASES.md`
  itself keeps the current + last few versions live, plus a one-line index of every archived
  version pointing here. Condensed (summary-only) per-version notes for the same versions also
  live in `docs/releases/vX.Y.Z.md` — those drop implementation detail (endpoint/column names,
  invariants, pre-signoff test-gap lists) that this archive preserves in full.
- **`ROADMAP_ARCHIVE.md`** — full `docs/product/ROADMAP.md` "(released)" per-version retrospective
  sections. `ROADMAP.md` itself stays forward-looking (current baseline, backlogs, future
  directions, open candidates) plus a one-line pointer per archived version.
- **`STUDY_PLAN_ARCHITECTURE_V2.md`** / **`STUDY_PLAN_HIERARCHY_PLAN.md`** — one-time vision and
  architecture-audit docs written while planning the Goal→Subject hierarchy. The design they
  describe shipped by v0.33.1–v0.34.0; still referenced by ROADMAP.md's "Deeper plan nesting"
  candidate section as prior-art for what a 3+-level extension would need to solve.
- **`conversion-funnel-finding.md`** — a point-in-time prod `/admin/funnel` snapshot (read
  2026-06-24) that diagnosed the v0.32.2/v0.33.0 retention problem. The finding (retention, not
  checkout, was the real constraint) is already carried forward as the origin story in
  RELEASES.md/ROADMAP.md's v0.33.0+ sections; this file is the underlying data pull.
- **`journey-validation-pulls.md`** — SQL queries run to validate the "Journey" (Goal-first study
  experience) direction before building it. The validation ran, the direction shipped as v0.34.0;
  the queries may still have reference value if a similar pre-build validation is needed again.

## When to add something here

A doc belongs here when it's a superseded one-time planning/analysis artifact (not a living
reference doc) or a historical section of an append-only doc that's no longer in the common
read path. Always leave a one-line pointer at the original location — the point of this
directory is that old context stays *findable*, not that it disappears.
