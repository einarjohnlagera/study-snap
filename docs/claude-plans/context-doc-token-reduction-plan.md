# Context Doc Token Reduction — Full Audit & Plan

**Status:** AUDIT + PLAN. Nothing deleted, moved or edited yet.
**Date:** 2026-09-07. **Trigger:** Claude Max expires 2026-09-10; on Pro the window is smaller, so
per-session overhead stops being free.

**⚠️ REVISED: this is not a new idea. The repo already has a documented archiving convention with
exactly this rationale — it was applied once on 2026-07-10 and has not been applied since.** The
recommendation is to **run that pass again**, not to invent a trim.

---

## 1. The convention already exists

`docs/archive/README.md`, verbatim:

> Cold storage for content that used to live in an actively-read doc but is no longer needed in the
> common case… Nothing here is deleted history; it's moved here specifically **so it doesn't cost
> tokens to read on every pass through the live docs**, while staying searchable (`git grep`) when
> it's actually needed.

And the stated design for the two big files:

> **`RELEASES_ARCHIVE.md`** — full `RELEASES.md` sections for v0.40.1 and earlier. **`RELEASES.md`
> itself keeps the current + last few versions live**, plus a one-line index of every archived version
> pointing here.
>
> **`ROADMAP_ARCHIVE.md`** — full per-version retrospective sections. **`ROADMAP.md` itself stays
> forward-looking** (current baseline, backlogs, future directions, open candidates) plus a one-line
> pointer per archived version.

`RELEASES_ARCHIVE.md`'s own header says the move was made *"to keep `RELEASES.md` itself lean and
**under the single-read size limit**"* — and that it is **a MOVE, not a delete**, content preserved
verbatim.

### ⚠️ The cadence lapsed — 85 releases ago

| | Archived through | Live now | Releases since |
|---|---|---|---|
| `RELEASES.md` | v0.40.1 (2026-07-10) | **116 sections, v0.41.0 → v0.125.0** | **85** |
| `ROADMAP.md` | v0.41.0 (2026-07-10) | still carries `(Released)` retrospectives for v0.60.1, v0.60.3, v0.62.0, … | same |

**The design says "current + last few versions." It holds 116.** The file is now ~465,000 tokens —
far past the "single-read size limit" its own archive header names as the reason the pass was run.

**Why it lapsed:** `.claude/commands/kickoff.md` and `signoff.md` never mention archiving. The 2026-07-10
pass was a one-off with no recurring step, so nothing has triggered it since.

---

## 2. What a session pays today

| Tier | File | ≈ tokens | When |
|---|---|---:|---|
| **Automatic** | `CLAUDE.md` | **89,000** | every session |
| **Automatic** | `MEMORY.md` | 1,200 | every session |
| **Near-automatic** | `AGENTS.md` — *"Always check this first"* | **53,000** | most implementation sessions |
| **Per task** | `docs/features/<feature>.md` | 2,300 median — **29,400** for `collections.md` | any feature change |
| **Per task** | `ADR-001` · `SPEC.md` | 22,500 · 22,700 | metadata / behaviour questions |
| **Scan** | `ROADMAP.md` → **Backlog Index alone** | **138,900** | kickoff steps 8–9 |
| **Reference** | `RELEASES.md` | **465,000** | on demand |

**A collections session pays ~171,000 tokens before reading any code.** And kickoff's instruction to
*"scan `ROADMAP.md`'s Backlog Index"* is, on its own, a **138,900-token** read.

---

## 3. The same accumulation, in four places

Everything below is **shipped-release narrative living in an actively-read doc** — precisely what
`docs/archive/README.md` defines as cold-storage material.

| Location | Size | Nature |
|---|---:|---|
| `RELEASES.md` v0.41.0 → v0.120.x | **~445,000 tok** | archived-class; already the canonical record |
| `ROADMAP.md` → `Backlog Index` | **~138,900 tok** | mixed: live gates + closed items |
| `ROADMAP.md` → `Current Release Baseline` | **~61,600 tok** | a "current baseline" that accumulated |
| `ROADMAP.md` → `(Released)` per-version sections | ~50,000 tok+ | **exactly what `ROADMAP_ARCHIVE.md` exists to hold** |
| **`CLAUDE.md` line 39** | **~80,000 tok** | 63 chained `Previous:` blocks, v0.37.0 → v0.125.0, **one line of 322,317 chars** |
| **`AGENTS.md` preamble** (before the first `##`) | **~14,700 tok** | release status narrative; largest line 27,031 chars |

**The `CLAUDE.md` and `AGENTS.md` copies do not even need archiving — they are already duplicated.**

| Release | `CLAUDE.md` | `RELEASES.md` |
|---|---:|---:|
| v0.113.0 | 1 line | 18 matches |
| v0.107.0 | 1 line | 32 matches |
| v0.95.0 | 1 line | 19 matches |

---

## 4. What an archive pass recovers

**`RELEASES.md`** (newest-first, so the cut is by line):

| Cut at | Keep live | Archive |
|---|---:|---:|
| **v0.121.0** (current + last 5) | **~20,400 tok** | ~445,000 tok |
| v0.116.0 (current + last 10) | ~56,500 tok | ~409,000 tok |

**`RELEASES.md`: ~465,000 → ~20,400 tokens** at the cut that matches the documented design.

**`CLAUDE.md`: ~89,000 → ~9,000.** **`AGENTS.md`: ~53,000 → ~41,000** (→ ~16,000 if its
*Required Product Architecture* section, 24,800 tok, is also moved later).

**A collections session: ~171,000 → ~79,000 tokens before code.**

---

## 5. What is genuinely long — do not archive, handle differently

No runaway line; the content is real and hand-trimming would lose rules.

| File | ≈ tokens |
|---|---:|
| `docs/features/collections.md` (1,107 lines, largest line 2,276 ch) | 29,400 |
| `AGENTS.md` → *Required Product Architecture* | 24,800 |
| `SPEC.md` · `ADR-001` · `GPT_CONTEXT.md` | 22,700 · 22,500 · 21,400 |

**Feature docs are mostly fine — median 2,284 tokens.** The pain is the eight largest, which are also
the most-touched areas.

**Costs nothing, leave alone:** `docs/archive/` (602k chars) is referenced by no instruction, and
`docs/claude-plans/` loads only when a session names a file.

---

## 6. Plan

| # | Change | Saves | Risk |
|---|---|---:|---|
| **1** | **Run the archive pass on `RELEASES.md`** — move v0.41.0 → v0.120.x into `RELEASES_ARCHIVE.md`, keep current + last 5, leave the one-line index | **~445,000** on any read | **None** — documented convention, a MOVE |
| **2** | **Run it on `ROADMAP.md`** — move `(Released)` retrospectives into `ROADMAP_ARCHIVE.md` | ~50,000+ | None — same |
| **3** | **Trim `CLAUDE.md` line 39 to the open release** | **~80,000 / session** | None — duplicated in `RELEASES.md` |
| **4** | **Trim `AGENTS.md`'s preamble narrative** | ~12,000 / session | None — same |
| **5** | **Add archiving to `signoff.md`; make `kickoff.md` REPLACE, not prepend** | keeps 1–4 from lapsing again | None |
| **6** | Prune closed items from the Backlog Index | up to ~100,000 per kickoff | Low — needs care |
| **7** | Split `AGENTS.md`'s architecture section | ~24,800 | **Medium** — see caution |

**Items 1–5 are the whole story: no information lost, every move already sanctioned by
`docs/archive/README.md`, and one process change so it does not lapse a third time.**

**⚠️ Items 3 and 4 delete from the documents that govern anti-drift.** `## Key conventions`,
`## Task routing`, the production-database rule, the source-of-truth list and every `Anti-Drift Rules`
entry must survive untouched. **Only shipped-release narrative goes.**

**⚠️ Item 6 needs judgment, not a script.** The Backlog Index carries live `[CHECKPOINT — due …]`
gates alongside closed items; the invariant that every `docs/claude-plans/` and `docs/claude-findings/`
file has a row is enforced by kickoff step 8. **Archive only rows whose gate has fired and been
actioned** — and per that same step, an unindexed planning doc is the failure this repo has already
paid for twice.

**⚠️ Item 7 is the one to hold back.** This repo has twice recorded that **a rule that stops being
found stops being followed**. If `AGENTS.md` is split, the core must still name every split file and
retain every anti-drift rule.

---

## 7. Execution order

1. `RELEASES.md` archive pass — cut at v0.121.0, append moved sections to `RELEASES_ARCHIVE.md`
   verbatim, update its header range, keep the one-line index in `RELEASES.md`.
2. `ROADMAP.md` archive pass — move `(Released)` sections to `ROADMAP_ARCHIVE.md`, leave one-line
   pointers.
3. Rewrite `CLAUDE.md` line 39 → product description + open release only.
4. Trim the `AGENTS.md` preamble → durable rules + a pointer to `RELEASES.md`.
5. Patch `kickoff.md` (replace, don't prepend) and `signoff.md` (archive when the live file exceeds
   the last-N window), each with a one-line reason so a later session does not restore the chain.
6. Update `docs/archive/README.md`'s Contents with the new ranges.
7. Re-measure and confirm.

**⚠️ Do not run any of this without a go-ahead**, and do it as **one reviewable commit on a branch** —
items 1–4 move ~2.2 million characters across the project's governing documents.

**⚠️ Verify before deleting:** items 3 and 4 are safe *because* the content is duplicated in
`RELEASES.md`. Spot-checks above cover three releases; confirm the full v0.37.0 → v0.125.0 range is
represented before removing the chain.
