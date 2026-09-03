# Unblocking the ALE and LET Review Set builds — and letting the taxonomy decision arrive on evidence

**Status:** Plan only. Nothing implemented, no release opened, no taxonomy changed.
**Date:** 2026-09-03. Repo state: `releases/v0.107.0`.
**Audience:** owner + Product UX (for tightening before anything is scoped).

---

## The finding that reshapes this whole plan

**The ALE build is blocked by copy that has already been fixed, not by a missing Domain Context — and
the fix has shipped but has not been applied to the ALE workbook.**

`docs/curriculum/ale-comprehensive-review.tsv` currently reads:

| domain_context | rows |
|---|---|
| **(unset)** | **215** |
| `PROFESSIONAL_PRACTICE_AND_REGULATION` | 77 |
| `ENGINEERING_SCIENCES` | 50 |
| `CIVIL_ENGINEERING` | 16 |
| `ENGINEERING_MATHEMATICS` | 6 |

Those 215 unset rows are **not** a taxonomy gap. They are the output of a strategist run against a
version of `docs/gpt-contexts/REVIEW_SET_SHAPING_CONTEXT.md` that pre-committed the answer with
*"Architecture, notably, deliberately has no Domain Context."*

**That line is gone.** The module now says, at `:60-69`:

> **⚠️ Do NOT assume any particular program is expected to come back unset.** An earlier version of this
> line named Architecture as deliberately having no Domain Context, and that instruction produced 215
> `(unset)` rows in the ALE plan — the strategist reproducing a pre-committed answer rather than
> assessing each note. […] **⚠️ Building services — plumbing, HVAC, electrical distribution, lighting,
> acoustics, fire protection, vertical transportation — and construction materials, testing and
> management are `ENGINEERING_SCIENCES`, not unset.**

And `frontend/lib/domain-context.ts:12-14` records the matching widening of the `ENGINEERING_SCIENCES`
description, shipped in `v0.99.0`.

**So the unblock is to re-run the Domain Context assignment against the corrected module.** No enum
change, no release, no code.

---

## Phase 0 — two facts to confirm before anything else (owner, ~10 minutes)

### 0.1 ⚠️ Is "Architectural Engineering" actually in the production catalog?

**It is not in the seed.** The `V106` catalog seeds 21 programs; the full list is Education,
Architecture, Nursing, Accountancy, Civil Engineering, Information Technology, Pharmacy, Electrical
Engineering, Mechanical Engineering, Physical Therapy, Senior High – ABM/STEM/HUMSS, Medicine,
Criminology, Law, Aviation, Business Administration, Psychology, Radiologic Technology, Special Needs
Education – Generalist. `grep -rn "Architectural" backend/src/main/resources/db/migration/` returns
nothing.

The catalog is admin-manageable, so it *may* have been added in production — that cannot be read from
the repo.

**This matters more than anything else in the plan.** If Architectural Engineering is not in the
catalog, then a note **cannot be tagged Architecture + Architectural Engineering at all**, the
multi-program blocker does not exist yet, and the entire taxonomy detour is prospective rather than
live. Check `/admin/course-programs` before spending another hour on it.

### 0.2 Medicine is already in the catalog

Seeded as `20000000-…-014`. Any future taxonomy discussion that describes Medicine as "not yet present
in the Course/Program catalog" is working from a false premise.

---

## Phase 1 — Unblock ALE by re-running Domain Context assignment (no code)

### The choice that needs Product UX's opinion

**Option A — full strategist re-run.** Re-run steps 1–3 of the `docs/curriculum/` pipeline end to end
with the corrected shaping module.
*Pro:* every field is internally consistent, one pass.
*Con:* **it will also reshuffle plan/section/title decisions you have already settled.** The ALE target
shape is 10 Subject Plans / 75 Sections / 568 placements; a full re-run puts all of that back on the
table to fix a single column.

**Option B — targeted Domain Context pass (recommended).** Hand the strategist the *existing* ALE rows
and the corrected §Domain Context rules, and ask only for `domain_context` per row. Everything else in
the TSV is untouched.
*Pro:* fixes the actual defect, preserves settled curriculum decisions, diffs cleanly in one column.
*Con:* needs a small purpose-written prompt rather than the standard module.

**Recommendation: Option B.** The defect is one column produced by one bad instruction. Re-running the
whole strategist to fix it re-opens 568 placements that are not in question.

### What Phase 1 should produce

1. A regenerated `ale-comprehensive-review.tsv` differing from today's **only** in `domain_context`.
2. Rebuild the workbook via `build_review_set_workbook.py` — **never hand-edit the `.xlsx`**
   (`review-set-workbook-spec.md`).
3. **A recorded before/after distribution.** Today's is the table at the top of this document. The
   after-distribution is Phase 4's primary evidence and costs nothing to capture now.

### Expected shape of the result

Per the corrected module, most of the 215 should resolve. Building services and construction
materials/testing/management → `ENGINEERING_SCIENCES`. Codes, laws, ethics, accessibility →
`PROFESSIONAL_PRACTICE_AND_REGULATION`. Computational method → `ENGINEERING_MATHEMATICS`.

**⚠️ Whatever comes back honestly unset after this is the real signal.** Design history, theory,
architectural design and space planning are the plausible residue — and *that residue is exactly what
`[CHECKPOINT — due 2026-09-28]` reads.* Do not force those rows to a value to make the column look
complete; an honest unset is a findable backlog marker, and a forced value is a decision that only
looks made.

---

## Phase 2 — The multi-program case, only if Phase 0.1 says it is real

If Architectural Engineering **is** in the catalog and you want a note tagged for both:

`StudyPackGenerationContextResolver.assertGenerationReady:33-39` throws
`MultiProgramDomainContextRequiredException` when `domain_context` is null and the note has more than
one joined program. **Generation is blocked outright** — there is no Automatic fallback for a
multi-program note. Three ways forward, in preference order:

1. **Tag the note Architecture only.** `ADR-001` constraint 4: *"Review Sets compose freely. A Review
   Set may contain any note regardless of its Applicable Programs."* So the note still sits in any
   Review Set you like; the resolver falls back to the single joined program and sends
   `Domain: Architecture`, which is the correct treatment. **You lose Architectural Engineering
   discovery on that note and nothing else.**
2. **Set an explicit value where one is honestly right.** Structural, building-services and materials
   content shared with ArchE is `ENGINEERING_SCIENCES` under the corrected rules.
3. **Record it as evidence and move on.** Keep a list of notes where you *wanted* multi-program tagging
   and no value was honest. That list is a Phase 4 input.

**⚠️ Do NOT add `ARCHITECTURE` to unblock this.** The recorded verdict
(`docs/claude-plans/domain-context-taxonomy-calibration-audit.md`) is that it passed the governance bar
and is still a **provable no-op for single-program notes** — its whole payload is a label string, and
the resolver already sends `Domain: Architecture`. It could only justify itself on the multi-program
case, and that case is unproven until Phase 0.1 is answered.

---

## Phase 3 — LET, and why it is the most valuable of the three

**LET maps onto the existing taxonomy better than any program so far**, which is itself a result worth
having:

| LET component | Existing Domain Context | Confidence |
|---|---|---|
| General Education | `GENERAL_EDUCATION` | High — name-for-name |
| Professional Education | `PROFESSIONAL_EDUCATION` | High — name-for-name |
| **Specialization** (Math, English, Science, Filipino, Social Studies, TLE, MAPEH, Values) | **open** | **Low — this is the question** |

**⚠️ Specialization is the one genuinely open item, and it must not be answered by reflex.** A LET
Mathematics specialization note is *not* `ENGINEERING_MATHEMATICS` — that value's whole purpose is
engineering-flavoured treatment. Whether `GENERAL_EDUCATION` authors secondary-mathematics content
correctly is an empirical question, and it is precisely the kind the checkpoint exists to answer.

**⚠️ And here is the sequencing point that should drive everything above:**
`[CHECKPOINT — due 2026-09-28]`'s trigger is stated canonically as *sufficient representative authoring
across **Civil Engineering → Architecture → Education***. CE is done. ALE is Phase 1. **LET is the third
leg.** Finishing ALE and then LET does not merely precede the checkpoint — **it completes the condition
that makes the checkpoint answerable.**

`PROFESSIONAL_EDUCATION` currently has **zero production usage**, and LET is the program that would
first use it. That makes LET a direct test of one of the two competing explanations the checkpoint
holds open (authoring order vs. wrongly-shaped values) — which is why it should not be pre-empted.

---

## Phase 4 — The taxonomy decision, at the checkpoint, on evidence

By 2026-09-28 this plan will have produced, at zero extra cost:

- the ALE Domain Context distribution **before and after** the copy fix;
- the honest-unset residue from a strategist that was no longer pre-committed;
- the list of notes where multi-program tagging was wanted and no value was honest;
- the first real exercise of `GENERAL_EDUCATION` / `PROFESSIONAL_EDUCATION`, and a concrete answer on
  LET Specialization;
- three programs of representative authoring — the stated trigger.

Candidates already on record, **not** to be re-derived:

- **`Building Services` / `Building Systems`** — the audit's recorded falsifiable successor: *"a real,
  taught, cross-program tradition serving Architecture, CE, EE, ME and Sanitary alike."* Promote only
  if MEP material is found consistently mis-framed under `ENGINEERING_SCIENCES`.
- **`ARCHITECTURE`** — passed the bar, provable no-op, must justify itself on the multi-program case.
- **Rejected by name, do not re-propose:** `GENERAL_ENGINEERING`, `Built Environment`,
  `Health Sciences`, `Health Sciences Foundation`, `Computing`. All failed the same test — *borrow real
  curriculum vocabulary; never invent*; plausible groupings nobody teaches under instruct the model
  toward no particular treatment.

Evaluate against **R4's existing runbook**
(`docs/claude-prompt/canonical-knowledge-architecture-out/17-r4-verification-runbook.md`).
**⚠️ Do not write a second rubric.** And note R4 is a prior that *favours* the current taxonomy: a
broader Domain Context does not degrade authored content, so a passing calibration confirms an existing
finding rather than producing a new one.

---

## Explicitly out of scope

- **The comprehensive multidisciplinary taxonomy design.** It asks the checkpoint's question 25 days
  early and by reasoning, which is the one thing the checkpoint's design forbids: `v0.96.0` left the
  zero-usage question unresolved *"specifically so that resolving it by reasoning would not make this
  checkpoint unfalsifiable before it runs."* Run it **at** the checkpoint, with Phase 1–3 evidence in
  hand, repaired for the defects noted separately.
- Any enum addition, removal or rename.
- Mass re-classification of existing notes — normalization stays **on touch**.
- Mass Study Pack regeneration — Domain Context affects **future generation only**.
- Anything about PNLE. Already lined up separately.

## Open questions for Product UX

1. **Option A or B in Phase 1?** Full strategist re-run vs. a targeted Domain Context pass. This is the
   only real decision in the plan.
2. **Is Architectural Engineering in the production catalog?** Phase 0.1 — it gates whether Phase 2
   exists at all.
3. **How much honest-unset is acceptable in a shipped Review Set?** The pipeline treats unset as a
   findable backlog marker, but nobody has said what fraction is tolerable in a plan being promoted.
4. **LET Specialization** — is one value per specialization area even on the table, or is the answer
   *"`GENERAL_EDUCATION` unless generation proves otherwise"*? The second is cheaper and testable.
