# R4 verification runbook — does a broader Domain Context degrade generated content?

**Owner action, through the production UI. Written 2026-08-04, after `v0.70.0` deployed.**
`[CHECKPOINT — due 2026-08-18]`. Carried from `v0.69.0` → `v0.70.0`; the owner elected 2026-08-04 to
run it before opening the next version rather than carry it a third time.

**Why it exists.** A Domain Context is often *broader* than the `course_program` it replaced —
`Engineering Mathematics` where a note used to say `Civil Engineering`. A vaguer domain constraint
can make generated content drift generic. **No automated test can detect this**: the prompt-building
tests assert which values reach the model, never whether the output is any good.

**Kill criterion.** If step 2 shows drift, the fix is a **narrower Domain Context value set** —
amending ADR-001's ratified 8 values. **Bulk authoring must not begin until step 2 passes.**

**Why it is now expensive to fail.** 43 notes carry `GENERAL_EDUCATION` via V104/V105, 31 notes
carry some Domain Context, and PRs 1–4b and 7 of `v0.69.0` plus all of `v0.70.0` depend on the set.
That cost is the reason this is worth 30 focused minutes rather than a skim.

---

## Prerequisites (all now true)

- `v0.70.0` is merged to `main` and deployed. Production is at **V106**.
- Both authoring axes are editable on a `STUDY_PACK_READY` note from **Note Detail → ⋯ → Edit**
  (the inline metadata panel), for Teacher/Admin accounts. This is what `#985` unblocked.
- You are signed in on an account whose `profileType` is `TEACHER` or whose role is `ADMIN`.
  If the two selects do not appear, check `localStorage["study_snap_auth_user"]` before checking the
  code — role-gated UI reads a login-time snapshot, not a live call, so stale auth hides the fields
  silently.

## Step 0 — RESOLVED 2026-08-04. Use these notes.

The candidate queries were run against production. **Pick these three; the reasoning is below.**

| Step | Note | id |
|---|---|---|
| 1 — control | Design and Function of Irrigation Canals in Hydraulic Structures | `a9590aac-27df-4443-b888-b558a6b3e894` |
| 2 — the real test | Fundamentals and Design Principles of Pressure Vessels | `db80be4d-c54d-44a3-8704-e297089c6476` |
| 3 — level precedence | **reuse the step-1 note**, after step 1 has set it to Board Exam Review | `a9590aac-…` |

**Three things the read settled, which change how the steps run:**

**1. No note carries an authored level of `COLLEGE` or `BOARD_EXAM_REVIEW`.** The candidate query
sorted `learner_level` as text, so `BOARD_EXAM_REVIEW` and `COLLEGE` sort *before* `GRADE_SCHOOL` —
and the output began at `GRADE_SCHOOL`, so those two have zero rows. Only the 49 legacy backfilled
notes carry a level at all (Grade School / Junior High / Senior High). **Step 3 therefore has no
pre-existing candidate**, and is rewritten below to reuse step 1's note, which step 1 sets to
`Board Exam Review` anyway. No profile change and no extra regeneration.

**2. Step 2 must use Strength of Materials, not Algebra.** Every Algebra note in production is
`Junior High` or `Senior High – STEM`; there is no College or board-level Algebra note. Setting
`Engineering Mathematics` on a Junior High Algebra note is a category error that would test "does a
wrong label produce wrong content," which is not the question. ADR-001's headline Algebra example is
still **forward-looking** — `05-vocabulary-results.md` already recorded that today's Algebra spread
is a *level* artifact, not the eleven-engineering-programs case.

**3. Steps 1 and 2 use different notes on purpose**, so neither regeneration contaminates the other.
`Pressure Vessels` is the longest Strength of Materials note; `Irrigation Canals` is the longest
Civil Engineering note that is not also a Strength of Materials note.

> **Optional, high value if you have appetite for one more.** `Stress and Strain in Strength of
> Materials` (`5c066e9c-bb49-4d3f-8bd2-3fcf2c5f0742`, Civil Engineering) and `Stress, Strain, and
> Material Strength` (`580932b9-72d8-47e1-b27a-46b0ba41318e`, Mechanical Engineering) are the exact
> semantic-duplicate pair ADR-001 cites as its smoking gun. Set the CE one to `Engineering Sciences`,
> regenerate, and compare against the untouched ME twin. If one canonical note under a shared domain
> can serve both, that is the ADR's core claim demonstrated rather than argued. Not required to
> clear the checkpoint.

## Step 0 (archive) — how those candidates were found (read-only, PRODUCTION)

```sql
-- Candidates for step 1 (control): a Civil Engineering note that already has a Study Pack.
SELECT n.id, n.title, n.subject, n.course_program, n.domain_context, n.learner_level
FROM notes n
JOIN study_packs sp ON sp.note_id = n.id
WHERE n.course_program = 'Civil Engineering'
ORDER BY length(n.content) DESC
LIMIT 10;

-- Candidates for step 2 (the real test): Strength of Materials or Algebra, with a Study Pack.
SELECT n.id, n.title, n.subject, n.course_program, n.domain_context, n.learner_level
FROM notes n
JOIN study_packs sp ON sp.note_id = n.id
WHERE n.subject IN ('Strength of Materials', 'Algebra')
ORDER BY n.subject, length(n.content) DESC
LIMIT 20;

-- Candidates for step 3 (level precedence): a note whose authored level is ABOVE College.
-- NOTE: this one was WRONG as written. `learner_level` is a VARCHAR, so `ORDER BY n.learner_level`
-- sorts alphabetically, not by the enum's semantic order, and `LIMIT 20` then truncated the result
-- before it could show whether COLLEGE or BOARD_EXAM_REVIEW rows exist. Use a count instead:
SELECT n.learner_level, count(*)
FROM notes n
JOIN study_packs sp ON sp.note_id = n.id
WHERE n.learner_level IS NOT NULL
GROUP BY n.learner_level
ORDER BY count(*) DESC;
```

**Prefer a long, substantive note in every case.** A thin note produces thin output under any domain
label, which is the single easiest way to get a false pass on step 2.

**Before regenerating anything, save the current Study Pack.** Copy the summary and key concepts out
to a scratch file, per note. Regeneration updates the pack **in place** — there is no version
history, and without a saved copy there is nothing to diff against.

---

## Step 1 — Control

**Setup.** Open **Irrigation Canals** (`a9590aac-27df-4443-b888-b558a6b3e894`). It currently has
`course_program = Civil Engineering` and both new axes NULL, so `Civil Engineering` is already its
effective authoring domain through the fallback chain — which is exactly what makes it a control.

Set `Domain Context = Civil Engineering` and `Note Learner Level = Board Exam Review`. Save, then
**⋯ → Regenerate** and confirm. **Keep this note at Board Exam Review — step 3 reuses it.**

**Compare** the new pack against the copy you saved.

**Pass:** no meaningful change in scope, depth, terminology, or example selection. The domain label
is nearly identical to the program it replaced, so the output should be too.

**Fail:** material change. That means the regression is in the **wiring**, not the taxonomy — the
fault is in how the value reaches the prompt, and steps 2–3 are not meaningful until it is fixed.
Stop and report rather than continuing.

## Step 2 — The actual test of the architecture

**This is the step the checkpoint exists for. Steps 1 and 3 are supporting.**

**Setup.** Open **Pressure Vessels** (`db80be4d-c54d-44a3-8704-e297089c6476`) — subject `Strength of
Materials`, program `Civil Engineering`, both new axes currently NULL. Set **`Domain Context =
Engineering Sciences`** and leave the learner level blank. Regenerate.

That is the whole test in one move: the note's effective authoring domain goes from `Civil
Engineering` to the deliberately broader `Engineering Sciences`, and nothing else changes.

**The judgment is "did it drift generic," and that is too vague to leave as a vibe.** Score these
five, against the saved pre-regeneration copy:

| # | Check | Drift looks like |
|---|---|---|
| 1 | **Worked examples** | Beams, columns, trusses, shafts → replaced by abstract `x`/`y` algebra with no physical referent |
| 2 | **Terminology** | `flexural stress`, `modulus of elasticity`, `factor of safety` → `force`, `strength`, `safety margin` |
| 3 | **Units and conventions** | SI/engineering units and standard notation → unitless or arithmetic-textbook framing |
| 4 | **Board-exam framing** | PRC-style application/problem framing → general-education explanation |
| 5 | **Key concepts list** | Recognisably an engineering syllabus → generic topic headings |

**Pass:** content stays engineering-specific and board-appropriate. Broadening the label changed the
*breadth of what could be included*, not the *depth or specificity of what was*.

**Fail:** two or more of the five drift. Then ADR-001's 8-value set is too coarse, and the fix is a
narrower set — which is a schema-weight change, not a copy tweak. **Do not start bulk authoring.**

**Ambiguous (exactly one drifts):** re-run once on a second note before deciding. One LLM sample is
one sample; ADR-001's own tie-break of last resort is to generate under both candidate values and
compare, so generate the same note under both the narrow and broad value and diff those two rather
than trusting a single broad-value run.

## Step 3 — Level precedence

**Setup.** Reuse the step-1 note, **Irrigation Canals** — step 1 set it to `Board Exam Review`.
Confirm your own profile learner level is `College` (Profile → Learning Profile), then generate a
quiz from that note.

`BOARD_EXAM_REVIEW` sits above `COLLEGE` in the `LearnerLevel` enum, so this is the note-above-reader
case the rule is about, with no profile change and no third regeneration. **This is why step 1 must
leave the note at Board Exam Review.**

If your profile is already at Board Exam Review, either drop it to College for this check, or use the
Senior High note `0e8c752e-180b-4435-a3e5-f04712080335` (`Linear Equations and Basic Algebra`,
`SENIOR_HIGH`) with your profile temporarily at Grade School — a two-level gap, which is a stronger
signal but needs the profile change.

**Pass:** questions sit at the **note's** level. Wording may be gentler and scaffolding heavier, but
curriculum, terminology, and difficulty stay at the note's level.

**Fail:** questions drop to your profile level. That is ADR-001 rule 3 violated — the reader's level
lowered the curriculum, which is the exact failure the note-level axis exists to prevent.

## Step 4 — already resolved 2026-08-03

The Senior High strand question was settled before `v0.69.0` shipped: V105 sets only
`learner_level = SENIOR_HIGH` and leaves `domain_context` NULL, so STEM/ABM/HUMSS keep reaching the
prompt as the effective authoring domain. **Nothing to run.**

---

## Recording the result — do not leave it in a chat reply

This is a ratified-decision-level outcome. It needs to land in three places:

1. **`ADR-001`** — its own text says to "resolve it in the owed R4 pass and record the answer here."
   Add a dated line to the Legacy-data policy's "Left open, deliberately" paragraph and, if the value
   set changes, to the Ratified value set section.
2. **`RELEASES.md`** — under the next version's section, or as a closing note on `v0.70.0`'s
   Verification block.
3. **`docs/product/ROADMAP.md`** — the R4 Backlog Index row moves from `[CHECKPOINT — due
   2026-08-18]` to a resolved status, and the bulk-authoring block is either lifted or restated.

**Record a fail in exactly as much detail as a pass.** A pass unblocks bulk authoring for 800+ notes;
a fail amends a ratified architecture record. Either way the next session needs to know which of the
five checks moved and on which note, not just the verdict.
