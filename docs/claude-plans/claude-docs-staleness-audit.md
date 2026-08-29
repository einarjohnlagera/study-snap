# `docs/claude-*` staleness audit — 2026-08-29

**⚠️ SUPERSEDED IN PART — ACTIONED AT THE `v0.97.0` KICKOFF, 2026-08-29. READ THIS BLOCK BEFORE THE
AUDIT BELOW; TWO OF ITS CLASSIFICATIONS WERE WRONG AND ARE CORRECTED HERE, NOT SILENTLY IN PLACE.**

Both owner principles were taken (once each, not per file): **(1)** a pressure test stays while any
finding it records is unresolved; **(2)** delete a spent file and leave dated scan notes naming it
intact. **THREE files deleted:** the two answered `v0.71.0` consultation prompts and
`v0.95.0-session-handoff.md`.

**⚠️ PRINCIPLE (1), APPLIED RIGOROUSLY, DELETES NOTHING IN `claude-findings/`. All three files carry an
unresolved item and all three are KEPT — the ~92 KB this audit calls "the largest single reduction
available" is not available at all.** Two were deleted on their Backlog rows' closure summaries and then
restored after the files themselves were read. **A row's status records what was TAKEN; it never records
everything the file RECORDS.**
- `v0.71.0-signoff-pressure-test.md` — **Frontend #4 is explicitly deferred and still carried**: the
  deferred-completion marker is never retried, and that exit is the only terminal one that does not
  `clearOnboardingDraft`.
- `v0.75.0-pre-signoff-pressure-test.md` — closes with *"Still open — an owner decision … whether this
  release owes a `[CHECKPOINT]` row … **Must not be left silent.**"* **It was left silent**; there is no
  `v0.75.0` checkpoint row anywhere in the Backlog Index. The file is the only record of that obligation,
  which now has a row of its own.

**⚠️ CORRECTION 1 — Tier D is wrong about the 63 KB file. `v0.71.0-pre-signoff-pressure-test.md` is
LIVE (Tier B) and is KEPT.** This audit calls its findings closed. They are not: **M9 and M12 carry
live `[DECISION]` markers** in the Backlog Index (*"may be answered … but may not be patched around"*;
M12 is an ADR-level question), and **M13, M15, M16, C8 and C9 were routed to the live Onboarding Intent
Router row.** The ROADMAP rows preserve only the identifiers; this file holds the `file:line` findings.
It was the largest single reduction on offer, and taking it would have destroyed the only record of
open items.

**⚠️ CORRECTION 2 — Tier C is wrong about `v0.74.0-challenge-conversion-read.sql`. It is KEPT and now
has a Backlog Index row of its own.** This audit classified it as a historical record because its only
name-mention is a dated scan note. **But its header names `[CHECKPOINT — due 2026-09-30]` and calls
itself a deploy blocker**, and it carries reads (a) and (b) for the *Challenge Quiz adoption* row, whose
status is **Open — obligation never closed**. The exemption definition's own closing sentence settles it
without judgment: *an unrun query past its release's signoff stops being an artifact and needs a row.*

**⚠️ THE METHOD LESSON, and it is this document's own warning turned on itself: both errors are the
basename false-negative flagged below for the `v0.82.0` CSVs — reproduced one section later. A reference
count cannot see a citation made by CONTENT. Read the header, do not count mentions of the name.**

**Everything below is the audit as written on 2026-08-29, retained unedited as the reasoning of record.**

---

**AUDIT ONLY. Nothing deleted.** 136 files: `claude-plans/` 56, `claude-prompt/` 77,
`claude-findings/` 3.

## ⚠️ The finding that reframes the request

**Deleting a file here is never a single-file act.** Kickoff step 8 requires a Backlog Index
row for every `docs/claude-plans/` file and every `docs/claude-prompt/*-out/` directory, and
the mechanical check confirms the invariant holds: **every `*-out/` directory and almost every
plan file is referenced by `ROADMAP.md`.** So "stale" cannot mean "unreferenced" — the index
guarantees a reference exists.

Deletion is therefore **retiring a row and its artifact together**. Delete the file alone and
the row dangles; delete the row alone and you lose recorded reasoning the roadmap explicitly
values (one withdrawn row is kept with the note *"the reasoning is worth keeping"*).

**Corollary: a struck-through row does NOT make its artifact deletable.** Worked
counter-example, verified: `engineering-mathematics-section-recovery.sql`'s row is struck as
`RESOLVED. DONE 2026-08-18` — yet the row's own Gate column says it is **"Kept rather than
exempted as a release artifact: the file carries an unrun optional step (2b) and is the
working template for the *Set sections from note subjects* action."** Struck row, live file.

## Method, and its one known gap

For every file in `claude-plans/` and `claude-findings/`, and every `claude-prompt/*/`
directory, I counted repo-wide references excluding self. **Basename matching produced false
negatives, so low-count results were re-checked by stem** — that caught the five
`v0.82.0-post-deploy-query-*.csv` files, which read as zero-reference but are cited by
`v0.82.0-curator-depth-backfill-reversal.md:202` and attached to a live **OWED** row.

**Gap: I did not audit the 77 individual files *inside* `claude-prompt/*-out/`.** They are
covered by their directory's row, and thinning a directory's contents while keeping its row is
a different and riskier operation. That needs its own pass.

---

## Tier A — NOT ELIGIBLE. Do not propose deleting these in any pass.

Cited from files loaded into **every session** (`CLAUDE.md`, `AGENTS.md`) or from **source
code**. A dangling path in `CLAUDE.md` silently degrades every future session's context; a
dangling path in a code comment is the easiest reference to break and the hardest to notice.

| File | Cited from |
|---|---|
| `v0.86.0-note-item-limit-mismatch.md` | **`CLAUDE.md` + `AGENTS.md` + `OpenAiLlmStudyPackService.java`** |
| `learning-connections-phase-plan.md` | `CLAUDE.md` + `AGENTS.md` + `docs/features/linked-learners.md` |
| `v0.89.0-regeneration-reproduction.md` | `CLAUDE.md` — the preserved harness for a hypothesis explicitly **not** killed |
| `subject-plan-sections-assessment.md` | `CLAUDE.md` |
| `discovery-system-stage-0-scoping.md` | `CLAUDE.md` |
| `next-release-candidates-consultation-prompt.md` | `CLAUDE.md` |
| `onboarding-redesign-ux-review.md` | `CLAUDE.md` |
| `course-program-canonical-catalog-proposal.md` | `CLAUDE.md` |
| `domain-context-catalog-assessment.md` | `CLAUDE.md` |
| `target-audience-removal-proposal.md` | `CLAUDE.md` |
| `quantitative-context-coverage-read.sql` | `CLAUDE.md` |
| `v0.85.0-domain-context-classification.sql` | `CLAUDE.md` |
| `v0.86.0-stuck-generation-sizing.sql` | `CLAUDE.md` |
| `v0.82.0-post-deploy-narrowing.sql` | `CLAUDE.md` |
| `v0.82.0-curator-depth-backfill-population.csv` | `CLAUDE.md` |
| `claude-prompt/public-library-seo-expansion-out/` | `frontend/lib/server-public-notes.ts` |

**16 items. Removing any of these requires editing `CLAUDE.md`/`AGENTS.md`/code first — which
makes it a doctrine change, not a cleanup.**

---

## Tier B — LIVE. Backing an open checkpoint, an unrun query, or in-flight work.

| File | Why it is live |
|---|---|
| `v0.82.0-post-deploy-narrowing.sql` + `-inline.sql` + `curator-depth-backfill-{population,written}.csv` + `post-deploy-query-{1..5}.csv` + `curator-depth-backfill-reversal.md` | Row: **"OWED NOW, and it is a blocker on the `2026-09-16` row"** — the narrowing query is still unrun |
| `september-2026-checkpoint-reads.sql` | **"Ready to run"** — covers every live checkpoint metric |
| `domain-context-adoption-read.sql` | Queries 1 and 3 are the reads for the Domain Context taxonomy work |
| `engineering-mathematics-section-recovery.sql` | Unrun step 2b + template for a scoped feature (see above) |
| `hydraulics-fluid-mechanics-consolidation.sql` | Row: **"NOT RUN, and it must not be run in the order written"** |
| `construction-subjects-domain-context-regeneration.sql` | Written 2026-08-29, unrun |
| `canonical-curated-note-title-policy-audit.md` | Holds the §5 read gating `v0.96.0` scope |
| `v0.97.0-session-handoff.md` | Handoff for the **next** session |
| `learning-connections-phase-plan.md` | Live phase plan (also Tier A) |

**⚠️ Standing flag repeated at the `v0.96.0` kickoff: "an indexed row does not survive
`git clean`."** `hydraulics-fluid-mechanics-consolidation.sql`,
`canonical-curated-note-title-policy-audit.md`, `construction-subjects-domain-context-regeneration.sql`
and this file are **uncommitted**. Indexed but untracked is the worst state — commit them
before any cleanup pass touches the tree.

---

## Tier C — CLEAN CANDIDATES. File and row (or mention) to remove, as a pair.

Verified: each appears in `ROADMAP.md` **only** in the release-artifact exemption definition or
in a historical kickoff scan note — never in a live Backlog Index row — and nowhere else in the
repo.

| File | Only mention | What to do |
|---|---|---|
| `v0.95.0-session-handoff.md` | Lines 211/213/217, all kickoff scan notes; **untracked in git** | **Safest deletion in the set.** Not in the repo at all — `v0.95.0` is Released and the handoff is spent. Working-tree delete, no doc edit |
| `b2-learner-owned-applicable-programs-consultation-prompt.md` | Line 205 only — named as an *example* in the exemption definition | Delete + replace the example name in the exemption paragraph, or leave the paragraph and accept a historical name |
| `onboarding-intent-router-product-ux-consultation-prompt.md` | Line 205 only — same | Same |
| `v0.74.0-challenge-conversion-read.sql` | Line 257 only — a historical scan record | Delete; the scan note becomes a record naming a file that no longer exists |

**The judgment call these three share:** their only mentions are *historical log entries*, not
live pointers. Deleting the file does not break anything operational, but it does make a dated
scan record reference a file that is gone. That is a documentation-integrity preference, not a
correctness question — decide it once and apply it consistently.

---

## Tier D — NEEDS AN OWNER CALL

| File | Tension |
|---|---|
| `claude-findings/v0.71.0-pre-signoff-pressure-test.md` (63 KB) | Cited by `RELEASES.md` + 4 plan files. The largest file here; its findings are closed but it is the evidence behind several rulings |
| `claude-findings/v0.71.0-signoff-pressure-test.md`, `v0.75.0-pre-signoff-pressure-test.md` | Same shape — closed findings, cited by `RELEASES.md` |
| `target-audience-removal-consultation-prompt.md` | Appears in a **live** row (line 312); the amendment is ratified but Phase 4 is still blocked on `2026-09-16` |
| The 12 `claude-prompt/*-session-plan.md` files | Each pairs with an `-out/` directory that has a row. Deleting the plan while keeping the output is a half-measure |
| `claude-prompt/` loose files (`conversion-audit-prioritized-backlog.md`, `topic-note-quick-recall-validation-review.md`, `smart-review-planning-and-product-language.txt`) | Referenced 2–4 times but not obviously attached to a live row |

**The `claude-findings/` question is the real one, and it is worth asking explicitly:** pressure
tests are *evidence*, not plans. If the project wants to keep the reasoning behind past rulings
auditable, they stay forever. If `RELEASES.md`'s Known-limitations entries are considered the
durable record, all three can go. **That is one decision covering ~92 KB — the largest single
reduction available, and the only one that needs a principle rather than a per-file check.**

---

## Out of scope, but found while auditing — flagging, not fixing

1. **`ROADMAP.md`'s per-version sections stop at `v0.85.0`, and it is labelled
   `(In Progress, base branch releases/v0.85.0)`** while the project is on `v0.96.0`. The
   Backlog Index and Current Release Baseline are current; only the per-version prose sections
   are ten releases behind. Either they were deliberately discontinued or the practice lapsed —
   worth a one-line decision either way.
2. **Four uncommitted files in `docs/claude-plans/`**, two of them carrying live Backlog Index
   rows. See the Tier B flag.

## Recommended sequence when you act

1. Commit the four uncommitted plan files (or explicitly decide they stay untracked).
2. Delete `v0.95.0-session-handoff.md` — untracked, spent, zero doc impact.
3. Take the `claude-findings/` decision as a principle, then apply it to all three at once.
4. Decide the historical-log-entry preference, then act on the three remaining Tier C files.
5. Leave Tier A and Tier B alone until their citing doctrine or open item changes.
6. Audit `claude-prompt/*-out/` internals as a separate pass, if at all.
