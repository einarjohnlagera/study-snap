# Review Sets First-Class + Independent Notes + Learner-Facing "AI" Language — Stage 1 Audit

**Status:** Audit + owner-ratified Stage 2 sequencing. **Nothing in Stage 2 has been implemented.**
**Date:** 2026-08-31; **state note added 2026-09-01 after `v0.100.0` shipped — read it first, below.**
**⚠️ The original "no copy changed" claim no longer holds:** the state note's commit corrected two copy sites
that `v0.100.0` made false. That is a `v0.100.0` correction, not Stage 2 work, and no slice has opened.
**Scope of this document:** Stage 1 (sections A–I of the brief) plus a Stage 2 sequencing proposal.
**Method:** every claim below is anchored to `file:line` in the repo at `releases/v0.99.0`. Where the brief's
premise is contradicted by code, the contradiction is stated rather than smoothed over.

> **⚠️ This does not fold into `v0.99.0`.** That release is in progress with three items, a pre-declared
> scoped cold agent, and an owner decision on `expired_at` already taken. Everything proposed here targets
> `v1.0.0` and later.
>
> **⚠️ `frontend/app/onboarding` is frozen** until `[CHECKPOINT — due 2026-09-11]` (signup-funnel read, 375
> signups against a 62.4% completion baseline). Onboarding is audited below but **no recommendation touches
> it before that date**.

---

# ⚠️ STATE AT `v0.100.0` — read this before acting on anything below

**Added 2026-09-01, after `v0.100.0` (Domain Context Resolution) shipped and was tagged.** Everything below
this section was anchored to `releases/v0.99.0`. **`v0.100.0` moved some of it.** This section exists so the
next session starts current rather than re-deriving — or worse, trusting a line number that has shifted.

**⚠️ EVERY `file:line` REFERENCE BELOW IS AT `v0.99.0` AND MANY HAVE MOVED.** `v0.100.0` edited
`note-editor-form.tsx`, `private-note-detail-page-client.tsx`, `note-editor-page-client.tsx`,
`bulk-generation-page-client.tsx`, `applicable-programs-combobox.tsx`, `domain-context.ts`, `api.ts`,
`NoteService.java`, `StudyPackGenerationContextResolver.java` and `NoteApplicableProgramsService.java`.
**Re-locate by string, never by line.**

## What changed for Slice 1 specifically

| Audit says | Actually, at `v0.100.0` |
|---|---|
| *"the six-site Domain Context string"* (§S2-1 item 2, §G2) | **THREE sites** — `note-editor-form.tsx:514`, `private-note-detail-page-client.tsx:2475`, `bulk-generation-page-client.tsx:639`. `v0.100.0`'s Decision 3 copy absorbed the rest. **Slice 1 is smaller than its own plan says** |
| `applicable-programs-combobox.tsx:323` — *"A program list is never sent to the AI…"* | **Re-worded by `v0.100.0`** and still contains *"sent to the AI"*. Still Category A, different surrounding text |
| `ai-suggestion-modal.tsx` — 8 strings | **Unchanged, still 8.** The densest cluster and still the bulk of the sweep |
| `usage-labels.ts` — `AI quizzes` | **Unchanged.** `quiz-session-history.test.ts:86` still asserts it; `:87` still pins label ≠ mode name |
| `AnalyticsEventType` has *"106 values"* (§I1, §B2 G5) | **108.** `v0.100.0` added `NOTE_AUTHORING_DOMAIN_RECORDED`; `NOTE_ADDED_TO_COLLECTION` (**M1**) is still missing and still the highest-value gap |
| `study-plans-guide.tsx:23` — *"nothing is AI-generated for the set itself"* | **Unchanged and still true.** Still becomes false when Slice 2 ships |

## Three facts that cost real work to re-derive

**1. `frontend/app/onboarding/` contains ZERO `AI` strings.** Verified twice on `main` at `v0.100.0`.
**This is what makes Slice 1's Category A sweep safe against `[CHECKPOINT — due 2026-09-11]`** — no copy item
can reach the frozen path. Without this fact a cautious reader would defer the slice for no reason.

**2. §S2-X4's decision is owed BEFORE the Codex prompt, not after.** Slice 3 falsifies Slice 1's new meter
description *by construction*. Either accept two copy edits across two releases, or write it once in a form
that survives — *"Sessions and quizzes we generate for you"* — at some cost in specificity. **It is an owner
call and it is cheap to make early and expensive to discover late.**

**3. `v0.100.0` moved the multi-program Domain Context rule from SAVE to GENERATION**, so anything below
describing it as a save-time requirement is stale. Three copy sites said *"Required when this note applies to
more than one program"*; **two were corrected in this commit and one was deliberately left**, because bulk
generation creates and generates in one operation and `NoteBulkGenerationService` still throws
pre-generation — so *"Required"* is still true there and only there. **⚠️ That asymmetry is real product
behaviour, not an oversight: do not "fix" the bulk string for consistency.**

## What `v0.100.0` did NOT change

The five Stage 2 slices, their sequencing, the owner's rulings, and every gap in §B2 are **untouched**.
`G1`–`G6` all still hold: no Review Set or Subject Plan can source an exam, the 3–4 note cap stands,
the same-subject rule is still the wrong predicate (§G3a), Adaptive Practice is still single-pack, and
multi-note assessment is still PRO-only. **Nothing in Stage 2 has been implemented.**

---

## Headline: three of the brief's premises are already satisfied, and the real gap is narrower and sharper

The brief opens by assuming NoteLib "still assumes Note first, collection later." **Against the code, that is
substantially not true on the learner surfaces the brief names.** What *is* true is a much more specific and
more valuable finding:

1. **Review Set / Study Plan is already first-class in the product's own navigation, Dashboard, onboarding,
   post-session routing and Library guidance.** Four surfaces the brief lists as suspects already lead with
   the plan. (§B, "already-supported".)
2. **The assessment hierarchy breaks exactly at the level the brief predicted — and worse than predicted.**
   Every multi-note assessment in the product is capped at **3–4 notes that must share the same note
   `subject`**, is **PRO-only**, and **cannot be sourced from a Review Set or Subject Plan at all**. The plan
   is first-class everywhere *except* the one place it would prove its value: you cannot assess against it.
   (§D, §10.)
3. **The grandparent flow's step 6 is a genuine hole, and it is one gate plus one missing artifact — not a
   parallel quiz engine.** A multi-note exam already exists as a *printable DOCX* behind a TEACHER-profile
   check; a *shareable* quiz link is single-note only. (§E.)
4. **The "AI quizzes" label is not merely off-positioning — it is factually incomplete today.** Board Exam
   sessions spend that meter and the helper copy does not say so. (§G.)

The single highest-leverage change in this whole brief is **letting a Subject Plan / Review Set be the source
of a multi-note exam**, because it is simultaneously the assessment gap (§9), the retention loop (§25), the
supporter gap (§15) and the Free-tier question (§16). Everything else is smaller.

---

# A. Current learning journey — the five real flows

## A1. Standalone learner

```
/notes/new  →  note DRAFT
  →  Generate Study Pack (NoteService.startAsyncGenerationFromNote)
  →  /notes/{id}  status polls to STUDY_PACK_READY
  →  /notes/{id}/quick-review        (5 static questions from note.quiz)
  →  Quiz tab unlocks only on a 5/5 Quick Review   (v0.74.0)
  →  /notes/{id}/challenge-quiz      (5 → 20, +5 batches)
  →  post-session next step          (PostSessionNextStepService)
```

**Nothing in this path requires a collection.** Verified: `LongExamStartRequest`
(`backend/src/main/java/com/studysnap/backend/dto/LongExamStartRequest.java`) and
`ChallengeQuizStartRequest` both key on a **study pack id**, never a collection id; generation is gated on
quota and plan, never on membership. The brief's §2 protection is **already structurally true** — there is no
membership check anywhere on the learn/practice path.

**And the standalone learner is already routed toward a plan at the one moment it is earned:**
`PostSessionNextStepService.resolveRecommendedPlan` (`:255-289`) — after a learner *masters* a Quick Review on
a note that is **in no collection**, the secondary action becomes *"Start {plan title}"*, a program-matched
published plan, routed to `/explore` because `/collections/{id}` is owner-scoped and would 404
(`:277-281` carries that reasoning inline). This is exactly the brief's §6 third bullet, already shipped.

## A2. Learner using Review Sets

```
/collections                    list (label varies by profile — see §H)
  →  /collections/{id}          detail: sections, per-item readiness, N% · M due pill
  →  /collections/{id}/builder  authoring: drag reorder, sections, add notes
  →  Dashboard leads with it    DashboardPrimaryCollectionHero (page.tsx:766)
  →  post-session next item     PostSessionNextStepService.resolveNextPlanItem (:290-320)
```

Two-level hierarchy, one table: `NoteCollectionEntity.parentCollectionId` +
`siblingPosition` (`backend/.../entity/NoteCollectionEntity.java:69,72`). A parent is the *Goal*; a child is
the *Subject Plan*. **Sections are a third, derived level** — client-side from per-item
`note_collection_items.label`, not a table (v0.88.0).

## A3. Board-exam learner

Same as A2 with `BOARD_EXAM` profile labels, plus:
- Official Review Set adoption from `/explore` and `/exam` (`EXPLORE_OFFICIAL_SET_ADOPT_CLICKED`,
  `EXAM_HUB_OFFICIAL_SET_ADOPT_CLICKED`).
- **Board Exam Mode**, which is *not* a session mode — see §D2.
- `resolveQuestionCount` (`LongExamService:543-550`) gives `BOARD_EXAM_REVIEW` learners 30 Long Exam
  questions vs 25 for College.

## A4. Supporter using Learning Connections

```
/linked-learners  →  invite by email OR shareable link
  →  learner accepts (acceptance is load-bearing; guardian consent below age 17)
  →  grants:  ACTIVITY  (mutual)  |  PROGRESS  (learner → supporter only)
  →  /linked-learners/{relationshipId}/progress
```
Plus note-level sharing (`note_shares`, v0.91.0) and the anonymous quiz link (`/quiz/{token}`, `permitAll`).

## A5. Recipient of shared material

`/shared/notes/{id}` and `/shared/study-packs/{id}` — authorized reads requiring a live `ACCEPTED`
relationship, re-verified on every read, no cache. Recipient can *Copy to my Library*. The recipient study
pack read deliberately does **not** call `recordActivity` with the owner's id.

---

# B. First-class Review Set gaps

## B1. Already supported — do NOT rebuild these

| Surface | Evidence | Note |
|---|---|---|
| Dashboard leads with the plan | `frontend/app/dashboard/page.tsx:764-773` renders `DashboardPrimaryCollectionHero` **above** Today Focus (`:814`) and the Study Pack grid (`:867`) | Hero computes a `currentStep` — "Review due concepts" / "Start {next subject}" / "Choose your next note" (`dashboard-primary-collection-hero.tsx:20-48`) |
| Post-session → next plan item | `PostSessionNextStepService.resolveNextPlanItem:290-320` | v0.78.0. Picks Primary plan if it contains the note, else most-recent |
| Post-session → adopt a plan (standalone) | `PostSessionNextStepService.resolveRecommendedPlan:255-289` | Fires when the mastered note is in **no** plan |
| Library organization nudge | `frontend/app/library/page.tsx:1447-1452`, rule `library-study-plan-grouping`, `items.length >= 3` | Exactly the brief's §6 fourth bullet |
| "Add to Study Plan" on Note Detail | `private-note-detail-page-client.tsx:2304-2309` — label is `Add to ${getCollectionLabels(profileType).singular}` | The brief's §6 second bullet |
| Onboarding adopts an Official Review Set | `frontend/app/onboarding/page.tsx:1213` fires `ONBOARDING_V2_PRACTICE_FIRST_PLAN_ADOPTED`, lands on the plan detail page (`:1225`) | **Frozen until 2026-09-11** |
| Nav has plans as a first-class destination | `getCollectionLabels(...).navLabel` | Profile-labelled |

**Six of the fourteen surfaces the brief lists as suspects are already plan-first.** Any Stage 2 proposal that
"makes the Dashboard plan-first" is proposing work that shipped.

## B2. Genuine architectural gaps

| # | Gap | Evidence | Severity |
|---|---|---|---|
| **G1** | **A Review Set or Subject Plan cannot source any exam.** Both multi-note paths accept only a list of study pack ids. | `LongExamStartRequest` has `(String difficulty, List<String> additionalStudyPackIds)`; `ChallengeQuizService.resolveAdditionalBoardExamStudyPackIds:1064-1081` parses the same shape | **Highest.** This is the whole thesis |
| **G2** | **Multi-note assessment is capped at 3–4 notes.** Long Exam `MAX_ADDITIONAL_SOURCE_COUNT = 3` (`LongExamService:110`) → 4 notes. Board Exam `MAX_ADDITIONAL_BOARD_EXAM_SOURCE_COUNT = 2` (`ChallengeQuizService:148`) → 3 notes | Hard constants, not config | High |
| **G3** | **All additional sources must match the primary note's `subject` string — and `subject` is FREE TEXT.** Matching is `trim().toLowerCase()` only (`ChallengeQuizService:1854-1856`, `LongExamService:1070-1074`), while `notes.subject` is an uncontrolled string softly canonicalized against existing values (`NoteService.canonicalizeSubject:1796-1805` snaps to an existing subject only on a lookup-normalized match, else stores what was typed). | `LongExamService:837-846`; `ChallengeQuizService:1102-1127`, throws `subjectMismatch` | **Highest of the three.** See G3a below — this rule is already producing wrong rejections, not merely restricting |
| **G4** | **Adaptive Practice is single-study-pack scoped, period.** Every entry point takes `studyPackIdRaw`. | `QuickReviewAdaptivePracticeService.generateAdaptiveQuiz(String studyPackIdRaw, ...)` `:120-190`; source session resolved by `resolveLatestAdaptiveSourceSession(userId, studyPackId)` `:148` | High — §12's whole hypothesis |
| **G5** | **No event records a note entering a plan.** `COLLECTION_CREATED` and `COLLECTION_SECTION_ASSIGNED` exist; there is no `NOTE_ADDED_TO_COLLECTION` / `COLLECTION_ITEM_ADDED` | `AnalyticsEventType.java` — 106 values, none for item add | Medium — blocks §25 |
| **G6** | **Multi-note assessment is PRO-only, both flavours.** `isLongExamAvailable` = `PRO` only (`StudySnapProperties:253-255`); `resolveMonthlyBoardExamLimit` returns 0 for non-PRO (`:198-203`) | Config-backed, so cheap to change | Medium — §16 |

### G3a — the same-subject rule is already broken for plan-shaped usage

Worth stating separately, because it changes G3 from *"a rule we must relax"* to *"a rule that is already
rejecting valid pairings."*

`notes.subject` has **no catalog, no enum and no FK**. `NoteService.resolveCanonicalSubject:1767-1775` →
`canonicalizeSubject:1796-1805` snaps a typed subject onto an existing one **only** when
`normalizeForLookup` matches exactly; otherwise the typed string is stored verbatim. The exam matcher then
compares with nothing but `trim().toLowerCase()`.

So today, two notes a learner **deliberately placed in the same Subject Plan** are rejected from one exam
whenever their subjects were typed differently — *Engineering Mathematics* vs *Engineering Math*,
*Fluid Mechanics* vs *Fluid Mech*. Case and whitespace are forgiven; nothing else is.

**Consequence for Stage 2:** Slice 2 does not need to argue that the same-subject rule is *too strict for a
new capability*. It is already the wrong predicate for the capability that exists — plan membership is a
deliberate, user-authored grouping, and a free-text string comparison is not. That is a cheaper and more
defensible justification than the multi-subject argument alone, and it stands even if the owner scopes
plan-sourced exams to a single Subject Plan.

## B3. UX / discoverability gaps

- **The plan's own terminal action is profile-forked, and it resolves on PROFILE — never on plan.**
  `getCollectionTerminalAction` (`frontend/lib/collection-labels.ts:96-115`) → `TEACHER` → *Build Exam*
  (exam-builder, DOCX); everything else delegates to `resolvePlanPremiumExamMode(profileType)`
  (`frontend/lib/exam-mode-visibility.ts:71-84`): `STUDENT` → *Take the Long Exam*, `BOARD_EXAM` → *Take the
  Board Exam*, `PROFESSIONAL` → *Start Interview Practice*, **everything else → `null`**. Two consequences:
  - **A Free or Plus learner is shown a terminal action they cannot use.** The label renders on profile; the
    gate fires on plan at click (`isLongExamAvailable` = PRO only). The Review Set's concluding CTA is a
    paywall for two of three plans.
  - **A `PARENT`-profile user's collection has no terminal action at all.** `resolvePlanPremiumExamMode`
    returns `null` for `PARENT`, so the plan simply ends. Given §15's supporter scenario, this is the profile
    a grandparent most plausibly holds.
- **The Library nudge does not know whether notes are already organized.** `items.length >= 3` regardless of
  membership (`library/page.tsx:1450`), so a learner whose notes are all in a plan still gets told to make one.
- **Adaptive Practice has no plan-scoped or subject-scoped entry point** — only `/notes/{id}/adaptive-practice`
  and `/study-packs/{id}/adaptive-practice`.

## B4. Copy problems

- `AI_QUIZZES_USAGE_DESCRIPTION` is **incomplete**, not merely off-brand — see §G.
- `study-plans-guide.tsx:23` says *"nothing is AI-generated for the set itself"* — accurate today, and
  **becomes false** the moment a Review Set can source an exam. Flagged now so it is not missed later; this is
  precisely the class of drift CLAUDE.md's "sweep by SURFACE" rule exists for.

---

# C. Independent Note protection — per-recommendation impact

| Recommendation | Effect on a standalone-Note user |
|---|---|
| R1 · Subject Plan / Review Set as an exam source | **None.** Purely additive: a new source shape beside `additionalStudyPackIds`, which is untouched |
| R2 · Relax the same-`subject` rule for plan-sourced exams only | **None.** Note-selected exams keep today's rule |
| R3 · Raise the multi-note source cap | **Improves it** — a standalone learner can already select notes; they just cannot select more than 4 |
| R4 · Limited multi-note assessment on Free | **Improves it materially.** Today a Free learner cannot mix *any* two notes | 
| R5 · A terminal action for Free Review Sets | None (they have no plan) |
| R6 · Adaptive Practice widened to subject/plan scope | **Must be additive.** Note-scoped Adaptive Practice must stay reachable from Note Detail — see the challenge below |
| R7 · `NOTE_ADDED_TO_COLLECTION` event | None |
| R8 · Rename the `AI quizzes` meter label | None (label only) |
| R9 · Library nudge learns about membership | **Improves it** — fewer irrelevant nudges |

**One proposal in the brief is challenged.** §12 suggests that entering Adaptive Practice from Note Detail
"may reasonably expand to related weakness within the same Subject Plan / Subject." **For a standalone note
that expansion has no target**, and for a note in a plan it would silently change what the learner asked for.
Recommendation: keep Note-scoped Adaptive Practice exactly as-is and **add** plan/subject scope as a *separate,
separately-entered* capability. Widening the existing entry point violates the brief's own §29 test
(*"standalone learning remains first-class"*).

---

# D. Assessment audit — actual current behaviour

## D1. Scope map

| Mode | Engine discriminator | Source scope | Questions | Plan gate | Writes `ConceptHealth`? |
|---|---|---|---|---|---|
| Quick Review | `QUICK_REVIEW` | **1 note** (static `note.quiz`) | 5 | All plans, unmetered | **Yes** — `QuickReviewSessionService:304,308` |
| Challenge Quiz | `CHALLENGE` | **1 study pack** | 5 → 20, `+5` batches (`ChallengeQuizService:142-143`) | All plans; meter below | **Yes** — `:608,612` |
| Board Exam Mode | `CHALLENGE` + `mode="board_exam"` in session state | **1–3 study packs, same `subject`** | 12/source, capped 30 (`:146-147,1060-1062`) | **PRO only** | **Yes** — same code path as Challenge |
| Long Exam | `LONG_EXAM` | **1–4 study packs, same `subject`** | 20/25/30 by learner level (`LongExamService:543-550`) | **PRO only** | **Yes** — `:473,481` |
| Adaptive Practice | `ADAPTIVE` | **1 study pack** | — | Free 3 / Plus 10 / Pro 30 per month | **Yes** — `:384,388` |
| Interview Practice | `ADAPTIVE` + `subMode:"INTERVIEW"` | 1 study pack | — | **PRO only** (`interview-practice-pro-only: true`) | Yes — `InterviewPracticeService:281,289` |

**Every assessment mode feeds `ConceptHealth`, including both multi-note modes.** The brief's §10 question
*"whether multi-note results feed Weak Concepts"* → **yes**, via
`recordCorrectAnswersForKnownConcepts` / `recordIncorrectAnswersForKnownConcepts`.

**But Adaptive Practice cannot consume that evidence across notes.** `ConceptHealth` is keyed
`(userId, studyPackId, concept)`, and Adaptive Practice reads it for exactly one study pack. So a Long Exam
across 4 notes writes weakness into 4 buckets, and remediation can only ever address one of them per session.
**That is the precise §12/§13 gap, and it is a scope mismatch, not a threshold problem.**

## D2. Board Exam is not the fifth mode `EXAM_MODES.md` describes

`QuickReviewSessionMode` has **four** values — `QUICK_REVIEW, CHALLENGE, ADAPTIVE, LONG_EXAM`
(`backend/.../entity/QuickReviewSessionMode.java`). `docs/product/EXAM_MODES.md` states five and hedges
Board Exam as *"currently presented as a Challenge variant; future: own discriminator."* **The doc is accurate
about the hedge** — Board Exam is a `mode` string in session-state JSONB, running the Challenge code path
with different constants, `+5 Questions` explicitly blocked (`ChallengeQuizService:685-688`) and question
order deliberately not shuffled (`:337-339`).

**Consequence worth surfacing:** a Board Exam session increments **both** meters —
`incrementChallengeQuizGeneration` **and** `incrementBoardExamGenerationBy`, in both the pooled branch
(`:235-236`) and the live-generation branch (`:343-345`), and it asserts Challenge quota first (`:199`)
unconditionally. This is not a bug — the LLM cost is real either way — but **no surface tells the learner**,
which is what makes the Settings label inaccurate rather than merely unfashionable.

## D3. Weak-concept evidence model (§13)

Two distinct thresholds, and they are not interchangeable:

- **`WEAK_CONCEPT_THRESHOLD = 60`** (`QuizSessionReviewUtils:13`) — a per-session accuracy filter, used for
  the "weak concepts" listed on a *result screen*. Single-session, no accumulation.
- **`TWICE_MISSED_STREAK_THRESHOLD = 2`** (`ConceptHealthService:33`) — **consecutive incorrect answers**
  across sessions, the durable signal. v0.77.0 moved the Dashboard's weak-concept recommendation onto this
  one, precisely so one bad quiz stops triggering a recommendation.

**Against the brief's §13 ask — "we want weakness to mean something":** the *Dashboard* recommendation already
does (2 consecutive misses, cross-session). The *result screens* still use the single-session 60% filter,
which is correct for "here's what you missed just now" and would be wrong as a remediation trigger. **No new
scoring model is needed.** The gap is scope (D1), not evidence strength.

## D4. Challenge Quiz size (§11)

`INITIAL_CHALLENGE_QUIZ_COUNT = 5`, `MAX_CHALLENGE_QUIZ_QUESTIONS = 20`, `GENERATE_MORE_BATCH_SIZE = 5`
(`ChallengeQuizService:142-143,697`). **One meter unit is spent per session start; `+5 Questions` is free** —
verified, the only three `incrementChallengeQuizGeneration` call sites are `ChallengeQuizService:235`, `:343`
and `GeneratedQuizService:151`, all on creation, none in `generateMoreQuestions`.

**Recommendation: do not shrink Challenge Quiz.** Three reasons, all from code:
1. The 20-question ceiling is what makes Challenge the *only* mode a Free learner can use for sustained
   retrieval practice. Shrinking it removes capability from Free before any replacement exists.
2. `+5` is free, so the cost argument for shrinking is weak — a learner who wants 20 questions already pays
   the same single unit as one who stops at 5. The cost is per-session, not per-question.
3. Progressive generation and mid-flight `+5` are named in `EXAM_MODES.md` as **core to Challenge's
   identity**, a locked contract.
The brief's conceptual distinction (§11) is sound, but it is achieved by **giving the mixed-retrieval layer a
real home** (R1–R4), not by making the topic layer smaller. Revisit only if completion-rate data shows long
Challenge sessions being abandoned — which is **not currently measurable**: `CHALLENGE_QUIZ_STARTED` and
`CHALLENGE_QUIZ_COMPLETED` carry `questionCount` at *start*, so abandonment by length is not derivable.

---

# E. Learning Connections — the grandparent walkthrough

| Step | Works today? | Evidence |
|---|---|---|
| 1. Create/adopt a Study Plan | **Yes** | `/collections`, Official Review Set adoption |
| 2. Create/add Notes for topics | **Yes** | `/notes/new`, Add to Collection modal |
| 3. Invite grandchildren | **Yes** | Email-keyed invite or shareable link; guardian consent below 17 |
| 4. Share relevant Notes | **Yes** | `note_shares` (v0.91.0), recipient reads note + study pack |
| 5. Grandchildren study | **Yes** | `/shared/notes/{id}`, `/shared/study-packs/{id}` — the normal study pack, no special surface. §14 already honoured |
| **6. One quiz covering everything they were asked to study** | **NO** | See below |
| 7. Grandchildren take the quiz | Yes, single-note only | `/quiz/{token}`, `permitAll`, no account needed |
| 8. Activity/progress shared | **Yes** | `ACTIVITY` / `PROGRESS` grants |
| 9. Supporter helps again | **Yes** | Progress view |

**Step 6 fails, and the failure is precise.** Two multi-note artifacts exist and neither is shareable:

- `GeneratedQuizService.generate(String noteIdRaw, ...)` — **one note**. `QuizShareLinkService.createShareLink(UUID generatedQuizId, ...)`
  takes **one** generated-quiz id. So the shareable link is structurally single-note.
- `GeneratedQuizService.exportCombinedDocx(...)` (`:220-292`) **does** combine multiple notes — it collects
  `noteIds` from requested sections and joins their generated quizzes. But it produces a **DOCX file**, not a
  session and not a link.
- The UI for it, `/library/exam-builder`, is **TEACHER-profile-only**:
  `frontend/app/library/page.tsx:632` (`isTeacherExamBuilderEnabled = authUser?.profileType === "TEACHER"`)
  and `getCollectionTerminalAction` returns the `exam-builder` kind only for `TEACHER`.

**So the grandparent hits two walls at once:** they are almost certainly not on a `TEACHER` profile, and even
if they were, the output is a printable paper, not something a grandchild can take on a phone.

**This does not need a parallel quiz engine.** The combining logic exists (`exportCombinedDocx`), the sharing
logic exists (`QuizShareLinkService`), the anonymous take-surface exists (`/quiz/{token}`, `permitAll`). What
is missing is a *combined generated-quiz artifact* that a share link can point at. That is the smallest
coherent version of step 6.

**Also confirmed against the brief's §15 constraint:** one-off quiz sharing does **not** require a Learning
Connection today — `/quiz/**` is `permitAll` and the recipient needs no account. Preserve that.

---

# F. Monetization audit

All figures from `backend/src/main/resources/application.yaml:99-140` and
`StudySnapProperties` (defaults where the yaml is silent).

| Capability | Free | Plus | Pro | Meter |
|---|---|---|---|---|
| Study Packs | 10/mo | 50/mo | 100/mo | `study_pack_generations` |
| Quick Review | unlimited | unlimited | unlimited | none |
| **Challenge Quiz** | **20/mo** | **100/mo** | **200/mo** | `challenge_quiz_generations` — 1 per session start; `+5` free |
| Board Exam Mode | **unavailable** | **unavailable** | 10/mo | `board_exam_generations` **plus** a `challenge_quiz_generations` unit |
| Long Exam | **unavailable** | **unavailable** | 12/mo | `long_exam_generations` |
| Adaptive Practice | 3/mo | 10/mo | 30/mo | `adaptive_quiz_generations` |
| Interview Practice | unavailable | unavailable | 10/mo | `interview_practice_generations` |
| Quiz for someone (generate) | — | — | — | shares `challenge_quiz_generations` |
| Quiz share links | 3/mo | 10/mo | unlimited (`0` → `unlimitedToNull`) | `quiz_share_links_created` |
| Ask Companion | 0 | 20/mo | 20/mo | `ask_companion_sessions` |
| DOCX export | 2/mo (teacher 10) | 15 (teacher ∞) | ∞ | `docx_exports` |
| PDF export | 2/mo | 15/mo | ∞ | `pdf_exports` |
| OCR | 20/mo | 50/mo | 100/mo | `ocr_extractions` |
| Note generation | 10/mo | 25/mo | 100/mo | `note_generations` |

## Recommendation on Free multi-note assessment

**Yes — a limited multi-note assessment belongs on Free, and the evidence supports it more strongly than the
brief assumes.**

The argument is not "Free users deserve more." It is that **the current gating makes the learning loop
unfinishable on two of three plans.** A Plus subscriber — a paying customer — has *exactly the same*
mixed-retrieval capability as a Free user: none. Every one of `isLongExamAvailable`
(`StudySnapProperties:253-255`) and `resolveMonthlyBoardExamLimit` (`:198-203`) returns PRO-or-nothing. The
brief's §16 hypothesis ("mixed retrieval may be fundamental to the learning loop, not a premium convenience")
is therefore **understated**: the capability is missing from the middle tier too, which is a pricing-ladder
defect independent of the Free question.

Proposed shape — **no price change, no plan rename**:

| | Free | Plus | Pro |
|---|---|---|---|
| Multi-note exam | **2–3 notes, small question count, ~2/mo** | **plan/subject-sourced, higher cap, ~10/mo** | unchanged: Long Exam 12/mo, Board Exam 10/mo, difficulty control, readiness simulation |

Two implementation notes that keep this cheap and reversible:
- Every limit above is already **config-backed** (`FREE_MONTHLY_*` env vars), so the Free/Plus tier is a
  configuration decision plus a gate change in `FeatureGateService.hasFeatureAccess`, not new billing code.
- **Do not add a new meter.** A separate multi-note counter is a pricing decision nobody has taken (the same
  reasoning v0.92.0 applied to shared quizzes). Reuse `challenge_quiz_generations` for the Free/Plus tier and
  leave `long_exam_generations` / `board_exam_generations` as the PRO meters.

**What stays Pro, and why it still sells:** Board Exam Mode's readiness framing and question pooling, Long
Exam's 30-question board-level sizing, difficulty selection, Interview Practice, unlimited share links,
unlimited exports, 30 Adaptive Practice sessions. Pro remains *readiness simulation*; Free/Plus gain
*mixed retrieval*. That is the distinction §11 asks for, expressed as a price ladder rather than as a
shrunken Challenge Quiz.

---

# G. Learner-facing "AI" language audit

44 occurrences across `frontend/app`, `frontend/components`, `frontend/lib`, `frontend/src` (excluding tests).

## G1. The Settings meter — audit result

**The label is inaccurate, not just off-positioning.** Verified spend sites — the *only* three:

| Site | What spends it |
|---|---|
| `ChallengeQuizService:235` | Board Exam session start (pooled branch) |
| `ChallengeQuizService:343` | Challenge Quiz **and** Board Exam session start (live branch) |
| `GeneratedQuizService:151` | "Quiz for someone" generation |

Current copy (`frontend/lib/usage-labels.ts`):
- label — `"AI quizzes"`
- description — `"Challenge Quiz sessions and quizzes you make for someone."`

**The description omits Board Exam Mode**, which spends a unit of this meter on every start. And `+5 Questions`
spends nothing, so the metered unit is a **session/quiz created**, not a question and not a generation call.

**Proposed replacement:**

- label → **`Quiz generations`**
- description → **`Challenge Quiz and Board Exam sessions, plus quizzes you make for someone.`**

Why `Quiz generations` over the alternatives:
- **`Quizzes`** — rejected, exactly as §18 anticipates. Quick Review, the saved Study Pack quiz, Long Exam and
  Adaptive Practice all cost nothing against it.
- **`Generated quizzes`** — defensible but reads as a count of durable objects, and a Challenge *session* is
  not an object the learner keeps.
- **`Quiz generations`** — describes the metered act (we generated a quiz for you), covers all three spend
  sites, implies nothing about Quick Review, and stays outcome-oriented rather than naming the technology.

**⚠️ Two mechanical consequences of a rename:**
- `frontend/lib/quiz-session-history.test.ts:86` asserts `AI_QUIZZES_USAGE_LABEL === "AI quizzes"` and must be
  updated. **Line `:87` must keep passing** — it pins that the usage *label* differs from the Challenge Quiz
  *mode* name, a v0.92.0 regression guard. `Quiz generations` satisfies it.
- The constant name `AI_QUIZZES_USAGE_LABEL` is an internal identifier. Renaming it is optional tidiness;
  **§19's category C says do not purge code symbols for branding.** Rename the *values*; the symbol can follow
  or not.

## G2. Full classification table

**Category A — remove / rename (learner-facing marketing of implementation):**

| Surface | Current copy | What it means | Proposed | Reason |
|---|---|---|---|---|
| `lib/usage-labels.ts:1` | `AI quizzes` | Challenge + Board Exam sessions + quiz-for-someone | **`Quiz generations`** | §G1 — also fixes an inaccuracy |
| `lib/usage-labels.ts:2` | `Challenge Quiz sessions and quizzes you make for someone.` | Omits Board Exam | **`Challenge Quiz and Board Exam sessions, plus quizzes you make for someone.`** | Factual correction |
| `app/page.tsx:150` | *"Not just AI output — structured for real learning…"* | Positioning vs. ChatGPT | *"Structured for real learning and exam preparation, not just an answer."* | Defines NoteLib by the outcome |
| `app/page.tsx:349` | *"Generic AI tools"* (comparison column) | Competitor column header | *"Generic chat tools"* | Keeps the comparison, drops the category-claim |
| `components/notes/note-editor-form.tsx:372` | *"Left blank, the AI writes one for you when you generate a Study Pack."* | Title auto-fill | *"Left blank, we write one for you when you generate a Study Pack."* | Same promise, no technology |
| `note-editor-form.tsx:390` | *"…AI when you generate."* | Same | *"…when you generate."* | — |
| `note-editor-form.tsx:502`, `bulk-generation-page-client.tsx:323,639`, `private-note-detail-page-client.tsx:1651,2469`, `note-editor-page-client.tsx:130` | *"…it tells the AI how to write it"* | Domain Context explainer | *"…it decides which academic domain the note is written in."* | **One string, six sites — fix once** |
| `metadata/applicable-programs-combobox.tsx:323` | *"A program list is never sent to the AI…"* | Explains `ADR-001` | *"A program list never informs how the note is written…"* | Same guarantee, no technology |
| `components/notes/ai-suggestion-modal.tsx:204,232,245,256,269,281,299,306` | `AI Suggestions`, `AI Title`, `Use AI Title`, `AI Subject`, `AI Tags`, `Merge My Tags + AI Tags`, `Use AI Tags Only` | Metadata suggestions | `Suggestions`, `Suggested title`, `Use suggested title`, `Suggested subject`, `Suggested tags`, `Merge my tags + suggested`, `Use suggested tags only` | **Densest cluster — 8 strings in one component.** "Suggested" is more accurate: they are proposals the user accepts |
| `components/help/study-packs-guide.tsx:17,28,40` | *"The AI reads your note…"*, *"An AI-generated overview…"*, *"Auto-extracted… by the AI"* | Help content | *"NoteLib reads your note…"*, *"A condensed overview of your note…"*, *"Auto-extracted from your note content"* | Help should teach the feature |
| `components/help/study-plans-guide.tsx:23` | *"nothing is AI-generated for the set itself"* | True today | *"nothing is generated for the set itself"* | **⚠️ Becomes false if R1 ships. Re-check on that release** |
| `components/landing/profile-learning-section.tsx:153` | *"per-answer AI feedback"* | Interview Practice | *"per-answer feedback on your reasoning"* | Says what the learner gets |

**Category B — keep (disclosure, safety, or materially necessary):**

| Surface | Copy | Why keep |
|---|---|---|
| `app/privacy/page.tsx:60,64` | *"4. AI Processing"* / *"…may be processed by AI services…"* | Privacy disclosure. §17/§20 explicitly protect this. **Do not touch** |
| `app/terms/page.tsx:63` | *"Abuse AI generation."* | Terms of service |
| `components/study-pack/quiz-working-solution.tsx:265` | *"AI-generated — verify calculations"* | **Verification disclosure on computed working.** §20 protects it. At most re-word to *"Auto-generated — verify calculations"*; do not delete |
| `private-note-detail-page-client.tsx:3218` | *"Questions and answers are AI-generated from your note. Review them before you share this…"* | Verification disclosure at the moment of **sharing to someone else** — the highest-stakes moment in the product for a wrong answer. At most *"generated from your note"*; do not delete |

**Category B/A boundary — "AI critique", requires an owner decision:**

`app/notes/[id]/interview-practice/page.tsx:348,395,397`, `app/study-packs/[id]/challenge-quiz/page.tsx:1796`,
`components/landing/profile-learning-section.tsx:158` (`"AI Critique"` as a **tier label**),
`components/help/professional-guide.tsx:37,52`, `lib/learn-guides.ts:389,390,392,746,751`.

**⚠️ This is a product-feature name, not stray copy.** It appears in `docs/product/EXAM_MODES.md`'s **locked**
sub-mode table as part of Interview Practice's stated differentiator, in two SEO-indexed learn guides
(`learn-guides.ts:389-392` is a guide *title* — changing it changes a public URL's content), and as a pricing
tier label. Proposed replacement if the owner wants it: **`Answer critique`** or **`Per-answer coaching`**.
**Recommendation: do not fold this into a copy sweep.** It touches a locked contract doc and indexed public
content; it deserves its own decision.

**Category C — internal only, do NOT change (§19's explicit carve-out):**

`OpenAiLlmStudyPackService`, `LlmStudyPackService`, `llmParallelTaskExecutor`, `LLM_API_KEY`,
`LLM_MODEL_FREE/PREMIUM/CRITIQUE`, `free-ai-rate-limit-per-minute` /
`premium-ai-rate-limit-per-minute` (`application.yaml:166-167`), `docs/features/ai-suggestions.md`,
prompt files under `resources/prompts/`, every `ai-*` code identifier.

---

# H. Terminology audit

**One entity, four learner-facing names, selected by profile type.** All in
`frontend/lib/collection-labels.ts:29-84`; the persisted entity is `note_collections` /
`NoteCollectionEntity` throughout the backend.

| Profile | Top-level | Child | Section | Primary |
|---|---|---|---|---|
| `STUDENT` | **Study Plan** | **Subject Plan** | Section | Primary Study Plan |
| `BOARD_EXAM` | **Review Set** | **Subject Plan** | Section | Primary Review Set |
| `TEACHER` | **Lesson Plan** | Unit | Part | Primary Lesson Plan |
| `PROFESSIONAL` / `PARENT` / null | **Collection** | Collection | Section | Primary Collection |

**Answering §24 directly:**
- *"Review Set" and "Study Plan" are **the same entity under two profile labels**, not an entity and a mode.*
  There is no `collectionType` column; the only structural distinction is `parentCollectionId`
  (`NoteCollectionEntity:69`) — parent = *Goal*, child = *Subject Plan*.
- **"Official Review Set"** = a `PUBLIC`-visibility top-level collection an admin published, adoptable via
  `sourcePlanId`. It is a **visibility + provenance** state, not a fifth term for a different thing.

## Inconsistencies found — flagged, not renamed

1. **`PROFESSIONAL` gets "Collection", which is the one label that names the data structure rather than the
   learning journey.** Every other profile gets a learner-facing name. This is the clearest violation of the
   brief's own §21 hierarchy and the cheapest to fix (one map entry) — but it is a rename, so it needs owner
   approval per §28.
2. **"Subject Plan" is a child-collection label, while §9's "Subject Exam" implies an assessment scoped to a
   *subject*.** A Review Set's children are Subject Plans, but a note's `subject` field is an independent
   free-text/catalog value — and it is `subject`, not plan membership, that today's multi-note exams key on
   (`LongExamService:837-846`). **Two different meanings of "subject" are one step from colliding.** Any
   Stage 2 work on plan-sourced exams must state which one it means, in the prompt, before implementation.
   **This is open question 1(b) below — it is a decision, not a documentation nicety.**
3. **`PARENT` falls through to `DEFAULT_LABELS`** via `getCollectionLabels(profileType ?? "PROFESSIONAL")`.
   Given §15's supporter use case, a supporter is likely on `PARENT` or `PROFESSIONAL` and therefore sees the
   most abstract vocabulary in the product.
4. **`UNGROUPED_SECTION_NAME = "Not in a section"` is deliberately NOT profile-mapped** (`:104-125` carries
   the reasoning: it names a state, and a second spelling would reintroduce the reserved-name collision).
   **Correct as-is — do not "fix" this for consistency.**

**No rename is proposed in this audit.**

---

# I. Retention instrumentation

## I1. What we can already measure

106 `AnalyticsEventType` values. Relevant chains that are **already complete**:

- **Plan adoption:** `STUDY_PLAN_ADOPTED`, `STUDY_GOAL_ADOPTED`, `COLLECTION_CREATED`,
  `EXPLORE_OFFICIAL_SET_ADOPT_CLICKED`, `EXAM_HUB_OFFICIAL_SET_ADOPT_CLICKED`,
  `ONBOARDING_V2_PRACTICE_FIRST_PLAN_ADOPTED`
- **Plan → next note (the continuation hypothesis):** `POST_SESSION_NEXT_PLAN_ITEM_IMPRESSION` →
  `POST_SESSION_NEXT_PLAN_ITEM_CLICKED` (v0.78.0) — **this is the core of §25 and it is already
  instrumented as a ratio**
- **Plan recommendation to standalone learners:** `STUDY_PLAN_RECOMMENDATION_IMPRESSION` → `_CLICKED`
- **Assessment usage:** `CHALLENGE_QUIZ_STARTED/COMPLETED`, `LONG_EXAM_STARTED/COMPLETED/FORFEITED`,
  `BOARD_EXAM_STARTED`, `ADAPTIVE_PRACTICE_STARTED/COMPLETED`
- **Connections:** `NOTE_SHARED_WITH_CONNECTION`, `SHARED_NOTE_OPENED`, `SHARED_STUDY_PACK_OPENED`,
  `CONNECTION_PROGRESS_VIEWED`, `CONNECTION_ACTIVITY_VIEWED`
- **Standalone note usage:** `NOTE_CREATED`, `STUDY_PACK_GENERATED`, `QUICK_REVIEW_*`

**Also relevant:** v0.80.0 fixed `trackAnalyticsEvent` to route through `fetchWithAuth`, so impression→click
ratios are no longer biased low by 401 drops. Ratios measured after 2026-08-15 are trustworthy; earlier ones
are not.

## I2. The gaps — and only three matter

| # | Missing signal | Why it blocks the hypothesis | Cost |
|---|---|---|---|
| **M1** | **`NOTE_ADDED_TO_COLLECTION`** | The hypothesis is *"one Note → several Notes → organized Study Plan."* `COLLECTION_CREATED` catches plan birth; `COLLECTION_SECTION_ASSIGNED` catches section labelling. **Nothing records a note entering a plan.** So the single transition the whole thesis rests on is invisible | 1 enum value + 1 fire site |
| **M2** | **No `BOARD_EXAM_COMPLETED`** | `BOARD_EXAM_STARTED` exists (`ChallengeQuizService:348-349`) but completion falls through to `CHALLENGE_QUIZ_COMPLETED`, so Board Exam completion rate is **not derivable** — and §16's Free-tier recommendation would want it | 1 enum value + 1 branch |
| **M3** | **Source scope not on multi-note exam events** | `LONG_EXAM_STARTED` carries `questionCount` and `difficulty` but not **how many notes** were selected. So "did anyone actually use multi-note?" is unanswerable — and that is the exact question R1–R4 need answered | 1 metadata key |

**Nothing else is needed, and no analytics infrastructure should be built** (§28). M1 is the one that would
still be worth adding even if every other recommendation here were rejected — it is the cheapest possible
instrumentation of the product's central retention claim, and today the claim is untestable.

---

# Stage 2 — sequencing (REVISED 2026-08-31 on owner decisions)

**⚠️ This section was rewritten after the owner ruled on the four open questions. The four-slice draft it
replaces is gone deliberately — it is superseded, not an alternative.** What the owner locked, verbatim in
effect: five slices not four; **Subject Plan first, whole Review Set explicitly deferred**; **plan membership,
never `notes.subject`, as the plan-sourced predicate**; Challenge Quiz unchanged; Adaptive Practice additive;
no new meter; "AI critique" and the `Collection` label both left alone.

**⚠️ FIVE CONTRADICTIONS WITH THE AUDITED CODE WERE FOUND WHILE REVISING. They are recorded in full at the
end of this section (§S2-X) and each is reflected in the slice it affects.** The largest: **a ~10-note source
cap is arithmetically impossible for most learners today**, and **the plan-scoped remediation read Slice 4
needs already exists** and already made its scoping decision.

---

## S2-1 · Slice 1 — Language + observability

**Unchanged from the draft, and it ships first because instrumentation should predate behaviour change.**

1. `AI quizzes` → **`Quiz generations`**; description → *"Challenge Quiz and Board Exam sessions, plus quizzes
   you make for someone."* Update `quiz-session-history.test.ts:86`; `:87` (label ≠ mode name) must keep passing.
2. Category A copy sweep — the six-site Domain Context string, `ai-suggestion-modal`'s eight strings, the
   study-packs guide, the two landing strings, the applicable-programs explainer.
3. Add **`NOTE_ADDED_TO_COLLECTION`** — the transition the whole retention hypothesis rests on, currently invisible.

**⚠️ The new description is knowingly temporary and Slice 3 falsifies it** (§S2-X4). That is accepted, not
overlooked: Slice 3 must correct it in the same release.

*Excluded:* "AI critique" (owner: separate decision), all Category B disclosures, all Category C identifiers.
*Verification tier:* single `advisor()` call.

---

## S2-2 · Slice 2 — Subject Plan becomes an assessment source

1. A **Subject Plan** can source the existing multi-note assessment capability.
2. **The source set is Subject Plan MEMBERSHIP.** `notes.subject` is not the predicate — it is free text and
   already rejects valid pairings (§G3a).
3. Raise the source cap **on the plan-sourced path** — **but see §S2-X1: the cap is not a free constant.**
4. **The learner is told what is in scope before starting** — *"Architectural Design · Testing material from
   8 Notes in this Subject Plan."* **Eligible** notes only: a source must have a generated Study Pack
   (`LongExamService:799-802`). The collection detail page already computes exactly this set as
   `quizReadyNoteIds` (`collection-detail-page-client.tsx:3046`), so the count is cheap and already correct.
5. Add `BOARD_EXAM_COMPLETED` (**M2**) and source-count/source-scope metadata on exam start events (**M3**).
6. **Correct `study-plans-guide.tsx:23`** — *"nothing is AI-generated for the set itself"* becomes false in
   this release. It has never once been in a diff when the behaviour it describes changed.

**⚠️ Whole-Review-Set sourcing is DEFERRED BY DECISION, not unsupported by accident** (owner, 2026-08-31).
The hierarchy stays: Note → topic; Subject Plan → mixed retrieval; Review Set → readiness, where it will
overlap Board Exam Mode and should be decided together with it.

**⚠️ The manual note-selected path keeps its same-subject rule — and that is now a KNOWN defect consciously
deferred, not an unexamined one** (§S2-X3).

*Verification tier:* **one scoped cold agent, framed as falsification.**

---

## S2-3 · Slice 3 — Mixed retrieval reaches Free and Plus

**The ladder is the locked part; every number is tunable configuration.**

| | Capability | Source cap | Allowance |
|---|---|---|---|
| **Free** | *experience the principle* — manual multi-note selection | ~3 notes | ~2/month |
| **Plus** | *structured* — manual **plus Subject Plan-sourced** | see §S2-X1 — **8, not 10, for a College learner** | ~10/month |
| **Pro** | *readiness simulation* — Long Exam, Board Exam Mode, larger sets, difficulty control, 30 Adaptive Practice | unchanged | unchanged |

Also in this slice: **fix the dishonest terminal CTA.** It resolves on **profile**
(`exam-mode-visibility.ts:71-84`) while the paywall fires on **plan**, so Free/Plus learners are shown a
concluding action they cannot use and a `PARENT` profile is shown none. Fixing it means the resolver takes
plan as well as profile.

**⚠️ Two things this slice must settle that the draft did not (§S2-X2, §S2-X4):** which engine carries a
non-Pro multi-note session — the answer is the **Board Exam pattern, a `mode` string on the `CHALLENGE`
discriminator**, which is proven and adds no mode — and the consequence that it therefore spends
`challenge_quiz_generations` automatically, **falsifying Slice 1's meter description**, which must be
re-corrected here.

*Verification tier:* **one scoped cold agent** — quota semantics change.

---

## S2-4 · Slice 4 — Broader remediation

Add a **plan/subject-scoped Adaptive Practice entry point** from Progress and the plan surface.

**⚠️ Do NOT widen the Note Detail entry point.** *"Note Detail → Adaptive Practice"* keeps meaning exactly
what the learner asked for. Broader remediation is entered from a broader surface and labelled for it —
*"Practice weak areas in Architectural Design."*

**⚠️ This slice is materially smaller than it looks, and its scoping decision is already made
(§S2-X5): `ConceptHealthService.getPersistentlyWeakConceptsByStudyPackIds(userId, studyPackIds)` already
exists**, already takes a plan's worth of packs, already applies `TWICE_MISSED_STREAK_THRESHOLD = 2`, and
**already returns results keyed per pack rather than merged** — with the reason documented in the method:
*"there is no canonical concept identity, so the same idea in two packs cannot be related and must not be
summed."*

**So the invariant is inherited, not invented: union across packs is SAFE; merging concepts across packs is
the ADR-sized work `v0.77.0` named and is NOT in this slice.** Plan-scoped remediation presents weakness
grouped by note, never as one flat merged list.

No new threshold. The durable signal stays the source of truth.

*Verification tier:* single `advisor()` call — no new evidence model, no permission change.

---

## S2-5 · Slice 5 — Supporter multi-note quiz

One combined quiz across selected notes, shareable by link, taken through the existing anonymous surface.
**Not TEACHER-gated. Does not require a Learning Connection** — connections may use it, they do not own it.
Lightweight one-off sharing is preserved.

**⚠️ This is the largest data-model change of the five, and the audit's phrase "one gate plus one missing
artifact" should not be read as trivial.** `GeneratedQuizEntity` is keyed per note and
`QuizShareLinkService.createShareLink` takes a single `generatedQuizId`, so a combined quiz is a **new
persisted artifact**, not a flag. The combining logic (`exportCombinedDocx:220-292`), the share mechanism and
the anonymous take-surface all exist; what does not exist is a thing for a link to point at.

*Verification tier:* **one scoped cold agent** — new persisted artifact reachable by an unauthenticated path.

---

## S2-D · Dependencies

```
Slice 1 ──────────────────────────────────────────────►  (independent; ships first for lead time on M1)
                │
                └── meter copy is re-corrected by ──┐
                                                    │
Slice 2 ─────────────────────────────────────────►  │     (independent of 1; needs no other slice)
   │                                                │
   ├── Plus tier consumes it ────► Slice 3 ◄────────┘     (HARD dependency on 2)
   │                                   │
   └── evidence at plan scope ─────────┴────► Slice 4      (SOFT — see below)

Slice 5 ────────────────────────────────────────────►     (independent of ALL — orderable anywhere)
```

- **Slice 3 hard-depends on Slice 2.** Its Plus tier *is* plan-sourced assessment.
- **Slice 4's dependency on Slice 2 is SOFT, and its real dependency is Slice 3.** Cross-note evidence already
  exists today — Long Exam has always written `ConceptHealth` (`:473,481`) — but only for Pro users. What makes
  plan-scoped remediation worth entering is a *population* that has cross-note weakness, and Slice 3 creates it.
- **Slice 5 depends on nothing.** It could ship second. It is placed fifth by owner preference, not by
  constraint — worth knowing if the supporter use case becomes urgent.

## S2-L · Locked vs tunable

| Locked (owner, 2026-08-31) | Tunable |
|---|---|
| Five slices; supporter quiz and remediation are separate | Slice order beyond the 2→3 dependency |
| **Subject Plan first; whole Review Set deferred by decision** | When whole-Review-Set sourcing is revisited |
| **Plan MEMBERSHIP is the plan-sourced predicate** | — (this one is not tunable at all) |
| The Free/Plus/Pro ladder — *experience / structured / readiness* | Every number in it: 3, 2/mo, 10/mo, and the Plus cap |
| No new usage meter | Which existing meter carries it |
| Challenge Quiz length unchanged | Revisit on abandonment evidence, which does not exist yet |
| Note-scoped Adaptive Practice unchanged | Which surfaces host the broader entry point |
| "AI critique" unchanged; `Collection` label unchanged | Both are open, both deferred |
| Learner sees assessment scope before starting | Copy and visual treatment |

## S2-B · Release-boundary concern from splitting the old Slice 4

**The split is right — the two capabilities share no users, no success criteria and no risk surface.** Two
consequences worth stating before they are discovered:

1. **Three of five slices now fire a cold-agent trigger** (2, 3, 5), where the four-slice draft fired two.
   Splitting did not create risk; it *revealed* that Slice 5 carries a data-model change that was previously
   hidden behind a remediation item. That is the split working.
2. **Slice 2 ships a capability almost nobody can reach.** Plan-sourced assessment lands while Long Exam and
   Board Exam are still Pro-only, so its own instrumentation (M2, M3) reports on the Pro population — and
   `linked_learner_relationships` was empty in production as recently as 2026-08-26, so Pro usage should be
   assumed near zero. **Slice 2's instrumentation will look like a null result until Slice 3 ships.** Do not
   read that as evidence against the capability; record the deploy split before the read, as this repo's
   checkpoint discipline already requires.

---

## S2-X · Contradictions between the revised sequencing and the audited code

**Five. None invalidates a decision; each changes an implementation constraint or a number.**

### S2-X1 — ⚠️ "up to ~10 Notes" for Plus is arithmetically impossible for most learners

The source cap is **not a free constant.** `MIN_QUESTIONS_PER_SOURCE = 3` (`LongExamService:111`) is checked
against `baseQuestionCount = questionCount / sourceCount`, and **`questionCount` is derived from the learner's
LEVEL, not from how many sources they picked** (`resolveQuestionCount:543-550`):

| Learner level | questionCount | **max sources today** |
|---|---|---|
| `GRADE_SCHOOL`, `JUNIOR_HIGH` | 20 | **6** |
| `SENIOR_HIGH`, `COLLEGE`, `PERSONAL_LEARNING` | 25 | **8** |
| `BOARD_EXAM_REVIEW`, `PROFESSIONAL` | 30 | **10** |

So a 10-source Plus cap works **only** for board-exam and professional learners. A College learner — the
default level and the most common — **fails at 9**, and a junior-high learner fails at 7. Raising the cap
therefore forces one of three choices, and it is a real decision rather than a constant edit:
**(a)** scale `questionCount` with source count on the plan-sourced path; **(b)** lower
`MIN_QUESTIONS_PER_SOURCE`, which weakens per-note coverage; or **(c)** state the cap as
`floor(questionCount / 3)` and surface it honestly — *"you can include up to 8 notes at your level."*
**(c) is the smallest and is recommended**, since it also satisfies the owner's §12 requirement that the
learner can predict what is in scope.

### S2-X2 — the engine for a non-Pro multi-note session is undecided, and there is a proven answer

Both multi-note paths are Pro-only *modes*, and the owner ruled Pro is not weakened — so Free/Plus multi-note
cannot simply be "Long Exam, unlocked." The fenced-off rule forbids a new mode.
**The precedent already exists: Board Exam Mode is a `mode` string on the `CHALLENGE` discriminator**, sharing
the Challenge engine with different constants and `+5` explicitly blocked (`ChallengeQuizService:685-688`).
A Free/Plus multi-note assessment should follow that exact shape. It adds no mode, honours the locked
five-mode contract, and reuses a path that already works.

### S2-X3 — the deferred manual predicate gets *more* visible after Slice 2, not less

The owner keeps the same-subject rule on the manual path as the compatibility-preserving choice, "unless the
implementation audit finds a separate bug." **§G3a is that finding** — it is a known defect being consciously
deferred. Worth recording because Slice 2 makes it *worse*: afterwards, a learner can run a plan-sourced exam
across mixed subjects while manual selection of the same notes still rejects them. **One product, two answers
to the same question.** Not a blocker; it should be a named Known limitation on Slice 2 rather than discovered
as a bug report.

### S2-X4 — Slice 3 falsifies Slice 1's meter copy, by construction

If the Free/Plus path rides the Challenge engine (S2-X2), it spends `challenge_quiz_generations` — the
increment at `ChallengeQuizService:343` is unconditional. So Slice 1's corrected description
(*"Challenge Quiz and Board Exam sessions, plus quizzes you make for someone"*) becomes incomplete the moment
Slice 3 ships. The owner already required this be re-checked; recording it as **certain rather than
conditional.** Either accept two copy edits, or write Slice 1's description in a form that survives —
*"Sessions and quizzes we generate for you"* is true across both, at some cost in specificity.

### S2-X5 — Slice 4's evidence read already exists, and its scoping decision is already made

`ConceptHealthService.getPersistentlyWeakConceptsByStudyPackIds(userId, studyPackIds)` (`:211-228`) already
does what plan-scoped remediation needs, and its Javadoc already settles the question Slice 4 would otherwise
re-litigate: results are **keyed per pack and deliberately not summed**, because *"`concept` is free text keyed
per pack — there is no canonical concept identity, so the same idea in two packs cannot be related and must
not be summed."*

**This is the same free-text hazard as §G3a, one layer down.** It de-risks Slice 4 (the read is built) and
constrains it (weakness must be presented grouped by note; a single merged list would violate a documented
invariant and is the ADR-sized work `v0.77.0` deferred).
