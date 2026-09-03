# ALE/LET unblock + multidisciplinary Domain Context calibration — revised plan

**Status:** Plan only. Nothing implemented, no release opened, no taxonomy changed.
**Revised 2026-09-03** on owner tightening. Repo state: `releases/v0.107.0`.
**Audience:** owner + Product UX.

> **⚠️ THE SEQUENCING CHANGED BY OWNER DECISION.** The prior revision deferred taxonomy design to
> `[CHECKPOINT — due 2026-09-28]`. The owner has ruled that the checkpoint is **calibrating, not
> gating**, and that one deliberate multidisciplinary calibration happens **now**. That decision was
> taken after the deferral argument was put and is recorded as reaffirmed — this plan executes it.
> **Two costs of that choice are stated in §Contradictions rather than buried; neither blocks it.**

---

## Superseded assumptions

| Old assumption (prior revision) | Status |
|---|---|
| Taxonomy design waits for `2026-09-28` | **Superseded.** Checkpoint is calibrating, not an approval gate |
| Production/authoring evidence is the only admissible evidence for a new value | **Superseded.** Prospective design allowed for current + deliberately planned families |
| LET completion is a prerequisite for taxonomy design | **Superseded.** LET *plan* structure is evidence now; LET *authoring* is later validation |
| "Tag Architecture only" is the recommended model for multi-program notes | **Superseded.** Emergency workaround only — never institutionalized (§Phase 1b) |
| `ARCHITECTURE` is settled as rejected | **Superseded.** Re-opened on the multi-program case specifically |
| Medicine is a future hypothetical | **Superseded — it was already false.** Medicine is seeded in `V106` |
| R4's runbook is the sole decision rubric | **Narrowed.** Valid where it applies; supplemented by the ratified expansion criteria |

**Retained unchanged:** targeted ALE pass over a full rerun; before/after distribution capture; honest
unset as evidence; no mass regeneration; normalization on touch; single-valued, closed, non-admin-editable
taxonomy; no arbitrary program chosen from a multi-program note; no duplicate canonical notes; no
Subject / Authored Depth / Review Set redesign.

---

## Phase 0 — Factual verification (owner, ~10 min)

1. **Architectural Engineering — is it in the production catalog?** Not seeded: `V106` seeds 21 programs
   and `grep -rn "Architectural" backend/src/main/resources/db/migration/` returns nothing. The catalog
   is admin-manageable, so production may differ. **This gates Phase 1b only — not the taxonomy
   question**, per owner §4.
2. **Medicine is already seeded** (`20000000-…-014`). Record it; it is part of the current design
   problem, not a horizon item.
3. **Capture the live catalog** — it is the denominator of ADR-001's failure condition (§Contradictions).

---

## Phase 1 — Targeted ALE Domain Context correction (no code, no release)

### Why this is the unblock

`ale-comprehensive-review.tsv` carries **215 `(unset)`** of 364 rows. That is not a taxonomy gap — it is
the output of a strategist run against a shaping module that pre-committed the answer with *"Architecture,
notably, deliberately has no Domain Context."* **That line is gone**
(`REVIEW_SET_SHAPING_CONTEXT.md:60-69`, which now warns against exactly that pre-commitment and routes
building services to `ENGINEERING_SCIENCES`), and `domain-context.ts:12-14` records the matching
`ENGINEERING_SCIENCES` widening shipped in `v0.99.0`.

### Shape of the targeted pass

**Input per row** — from the existing TSV, unchanged: `subject_plan`, `section`, `note_title`,
`note_subject`. **Output: `domain_context` only.**

**Rules handed to the strategist:** §Domain Context of `REVIEW_SET_SHAPING_CONTEXT.md` **verbatim** — do
not paraphrase, and do not re-state the taxonomy in the prompt, or the two copies drift.

**Explicit instructions to include:**
- ⚠️ *You are not re-planning. Plan, section, title, subject and placement are settled and must be
  echoed back unchanged.*
- ⚠️ *Do not assume any program is expected to come back unset. Assess each note.*
- ⚠️ *Building services (plumbing, HVAC, electrical distribution, lighting, acoustics, fire protection,
  vertical transportation) and construction materials/testing/management are `ENGINEERING_SCIENCES`.*
- ⚠️ *An honest unset is a valid answer and a findable backlog marker. Do not force a value to reach 100%.*

**Then:** regenerate via `build_review_set_workbook.py`. **Never hand-edit the `.xlsx`.**

**Capture, before touching anything:** the before distribution is
`(unset) 215 · PPR 77 · ENG_SCI 50 · CIVIL 16 · ENG_MATH 6`. Capture after, plus the residue list.

### Phase 1b — the multi-program case, if Phase 0.1 says it exists

`StudyPackGenerationContextResolver.assertGenerationReady:33-39` throws
`MultiProgramDomainContextRequiredException` when `domain_context` is null and the note has >1 program.
**Generation is blocked outright — there is no Automatic fallback.**

Until Phase 3 ships, unblock by setting an honest explicit value where one exists. **⚠️ Stripping
Architectural Engineering from Applicable Programs is an emergency workaround only.** Applicable Programs
means *where this knowledge applies*; deliberately making it less accurate to fit the authoring enum is
an architecture workaround, not a model. Log every note where it was used — that list is Phase 2 input.

---

## Phase 2 — Multidisciplinary taxonomy calibration (now)

**Inputs:** live catalog · corrected ALE distribution + residue · completed CE authoring · LET planned
structure · Medicine/Nursing · Computing/IT · Accountancy/Business · Education · Engineering family.

**Method — the bar every candidate must clear** (ratified expansion criteria, unchanged):
recurring knowledge family · recognizable authoring tradition · existing contexts cannot preserve
terminology/framing/examples/conventions/scope · not merely Subject · not merely Applicable Program ·
generation-not-discovery · stable across many notes · serves multiple programs **or** a genuinely
distinct professional tradition · **Automatic cannot express it in the relevant multi-program cases** ·
family is current or deliberately planned.

**Naming rule, carried forward and binding:** *borrow real curriculum vocabulary; never invent.*
`GENERAL_ENGINEERING`, `Built Environment`, `Health Sciences`, `Health Sciences Foundation` and
`Computing` were rejected on this test — **plausible groupings nobody teaches under, instructing the
model toward no particular treatment.** Per owner §9, **name rejection ≠ family rejection**: the
underlying gaps may be re-examined, but any replacement must be vocabulary a curriculum actually uses.

**Per-candidate proof table** (owner §14): enum · label · why existing contexts fail · exact treatment
difference · programs served · ≥5 notes that SHOULD use it · ≥3 notes from those same programs that
should NOT · Automatic failure case · quantitative flag · evidence source · confidence. **No
Low-confidence value ships.**

**Plus the Program → Domain Context composition matrix** (owner §15) — the guard against recreating the
program catalog inside the enum. Healthy: one program → several contexts, one context → several programs.

**⚠️ Carry into Phase 2 as a first-class question:** three existing values — `NURSING`, `ACCOUNTANCY`,
`PROFESSIONAL_EDUCATION` — have **zero production usage**. Designing health or business siblings around
them presumes they are correctly shaped. Phase 2 must state which explanation it is assuming (authoring
order vs. wrong shape) rather than assuming silently. **No existing value is removed** — removal can lock
existing multi-program rows.

---

## Phase 3 — Owner approval, then one release

Present: retained values · proposed values · values explicitly **not** proposed · matrix · proof tables ·
impact · **the ADR-001 amendment (§Contradictions)** · one release recommendation. **No implementation
before approval.**

## Phase 4 — Curriculum continues

ALE and LET proceed on the approved taxonomy. **Do not stop curriculum work for historical cleanup**;
normalization stays on touch.

## Phase 5 — `2026-09-28` calibration read

Preserved, role changed: validate and refine the shipped taxonomy against CE → Architecture → Education
authoring. May trigger correction. **Does not gate Phases 2–4.** **⚠️ Re-specify the query before the
read** — see §Contradictions (2).

---

## Contradictions with Accepted ADRs and production behaviour

### 1. ⚠️ ADR-001 has a numeric failure condition that this work can trip — an amendment is required

`ADR-001:378`, reviewed at **every** kickoff:

> **Failure condition:** if the number of Domain Context values ever approaches the number of course
> programs, the taxonomy has failed and has collapsed back into the free-text field it replaced. […]
> the ratio this condition actually watches is **8:21**. A ratio trending toward 1:1 is the signal to
> **stop and consolidate, not to keep adding.**

A plausible Phase 2 output — `ARCHITECTURE`, a biomedical value, a clinical value, a computing value —
takes **8:21 → 12:21**, moving 0.38 → 0.57. Four more takes it past 0.65.

**This is a genuine contradiction, and it is not fatal — but it must be resolved explicitly, not
silently.** The owner's intent (*durable authoring traditions, not program mirrors*) is aligned with the
ADR's **spirit**; the ADR's **letter** is a bare ratio that cannot tell the two apart. Recommendation:
**Phase 3 ships an ADR-001 amendment** restating the failure condition in terms the new posture can be
held to — e.g. a ceiling ratio plus the naming rule (*no value may equal a catalog program name unless
it is a board subject-area name*) plus the matrix as the standing evidence. Raise it the way the
`v0.95.0` column prohibition amendments were raised: named, reasoned, dated. **Do not let a release
quietly blow through a kickoff-reviewed gate** — the next kickoff scan will flag it, and by then the
reasoning will have to be reconstructed.

### 2. ⚠️ The original `2026-09-28` question becomes permanently unanswerable

The checkpoint asks *"is the **eight-value** vocabulary the right shape to author against?"* and
`v0.96.0` deliberately left the zero-usage observation unresolved *"specifically so that resolving it by
reasoning would not make this checkpoint unfalsifiable before it runs."*

Changing the vocabulary first does not confound that read — **it retires it.** Phase 5 is a different,
still-useful read (*is the new taxonomy used as designed?*), but the original cannot be recovered
afterwards. **Stated so it is a choice, not a discovery in three weeks.** Phase 5's query must be
re-specified before it runs, and the retirement recorded on the row.

### 3. Adding values while three sit unused is the exact pattern the failure condition watches

Not an ADR violation, but the same signal from a different direction, and it belongs in Phase 2's
reasoning rather than in a later post-mortem.

### 4. No contradiction found where one might be expected — **no migration is required**

`notes.domain_context` is **`VARCHAR(64)` with no CHECK constraint** (`V102:2`). Adding enum values is a
**code and docs change only** — no migration, no backfill, no schema risk. `ADR-001` constraints 1, 4 and
7 (multi-program requirement, free Review Set composition, resolution confined to
`StudyPackGenerationContextResolver`) are all preserved by this plan.

---

## Recommendation on release shape

**Yes — one coherent release, after approval.** Three reasons, and one binding constraint.

Because there is no migration (§4 above), the whole change is: enum values · labels · `quantitative`
flags · `frontend/lib/domain-context.ts` descriptions · `REVIEW_SET_SHAPING_CONTEXT.md` rules ·
`ADR-001` amendment · tests. That is genuinely one coherent unit, and splitting it would ship a taxonomy
whose curator-facing descriptions and strategist rules lag the enum — **the precise defect that produced
the 215 unset rows.**

**⚠️ The binding constraint is the `quantitative` flag, and it is the one irreversible thing in the
release.** Per `v0.85.0`: `false` is a **no-op** that falls through to the untouched keyword scan, while
`true` is a **new signal that is permanent per note**, because Study Packs never auto-regenerate. Every
new value must default to `false` unless computation guidance is affirmatively wanted for that tradition.
`PROFESSIONAL_EDUCATION` and `PROFESSIONAL_PRACTICE_AND_REGULATION` were both delivered as `true` and
corrected for exactly this reason. **Getting a flag wrong is not fixable by a later release for notes
already generated.**

**Do not extend `QUANTITATIVE_KEYWORDS`** — that restores the guess the declared flag replaced.
