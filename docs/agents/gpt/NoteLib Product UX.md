# NoteLib — Product UX Context (for ChatGPT)

**This document is written FOR ChatGPT, resuming as the owner's long-term NoteLib Product Manager / UX
Strategist. It is not a Claude Code artifact and Claude does not read it as instructions.**

- **Purpose:** let a brand-new ChatGPT conversation resume the product relationship without
  reconstructing months of context, and without repeating conversational beliefs that have since been
  overtaken by shipped code.
- **Last verified:** 2026-09-15, against live repo + one production read-only query, ahead of `v0.148.0 —
  Say What You Mean` (In Progress; `v0.147.0 — The Escape Hatch` is the last Released version).
- **Maintainer note:** this document was assembled by Claude Code (Feature Planner session) at the
  owner's request, from a direct repo/production audit — not transcribed from memory of past chats.
  Several sections below **correct** assumptions the owner's own briefing carried in from prior
  conversations; those corrections are called out explicitly in §1, because silently fixing them would
  defeat the purpose of this document.
- **Source precedence** (highest wins): (1) an explicit current owner decision → (2) canonical
  ADR/source-of-truth repo docs (`docs/architecture/ADR-*.md`, `docs/product/SPEC.md`,
  `docs/product/EXAM_MODES.md`) → (3) current shipped implementation/release truth (`RELEASES.md`,
  code) → (4) current active approved plans (`docs/claude-plans/`) → (5) this document → (6) historical
  conversation memory. **This document never overrides a newer canonical decision** — when it conflicts
  with current repo truth, the repo wins and this document is stale, not the other way around.
- **How to use this in a fresh ChatGPT session** (recommended opening message):
  > "You are my long-term Product Manager and UX Strategist for NoteLib. Please read
  > `docs/agents/gpt/NoteLib Product UX.md` and the current canonical docs. We're continuing our usual
  > workflow: I discuss product/UX with you, we send the audit/plan to Claude, then I bring Claude's
  > findings back to you to tighten before implementation."
- **⚠️ This document is institutional context, not a substitute for current repository truth.**
  Revalidate any implementation fact, count, enum, quota, or metric before it drives a decision — ask the
  owner to run it past Claude for a fresh repo/production read rather than trusting a number recorded here.

---

## 1. Corrections to the owner's own briefing (read this first)

The owner's briefing for this document carried several beliefs from past conversations that a fresh repo
audit found to be stale, reversed, or already-settled-differently. Recording these explicitly is the
actual point of a persistent-context document — silently fixing them would just reintroduce the same drift
next time.

- **Quick Review DOES write `ConceptHealth`.** The briefing assumed this was still locked false. It was
  corrected 2026-08-12 (`docs/features/quick-review.md`: *"Quick Review DOES record to ConceptHealth...
  this section had described the opposite for a month and was wrong."*). The real dividing line (§4) is
  Flashcards/Memorization (never write it) vs. every genuine-assessment mode including Quick Review (does).
- **The Accountancy quantitative-flag question is not open — it's already settled (HELD, `true`).** The
  briefing framed this as an active calibration question. `domain-context-biomedical-business-calibration-stage2.md`
  already resolved it: *"Do NOT test an ACCOUNTANCY arm. It is `quantitative = true`."* — shipped, unchanged.
- **The Accounting & Business Program Family evidence has changed direction since the briefing was
  written — and not the way "approved in principle" assumed.** A parallel Claude audit (same day, see §7)
  found the only production evidence for a 10-program business cluster is **35 notes whose 350 join rows
  were all written in one 5-minute window** — a single bulk action, not accumulated curation. This blocks
  the family this release. Health Sciences, by contrast, is genuine accumulated evidence and is proceeding.
- **PROFESSIONAL is not an unimplemented profile type.** `CLAUDE.md` itself still says "PARENT and
  PROFESSIONAL exist with no feature implementation" — that's now only true for PARENT. PROFESSIONAL has
  its own onboarding flow, dashboard support, exam-mode-visibility branching, and a dedicated re-engagement
  email template. (Worth telling the owner CLAUDE.md itself needs this line corrected — not this document's
  job to fix it.)
- **The retention hypothesis has moved twice since it was last discussed.** The "2.4% W1→W2" figure was a
  measurement artifact (an under-sized instrument that saw 3 of 11 real returners); corrected reading is
  **~7.2%**, and `GPT_CONTEXT.md` explicitly warns not to quote 2.4% as current (though some `ROADMAP.md`
  rows still carry the stale figure — a live doc inconsistency, not resolved here). The universal W1→W2
  frame itself was retired 2026-07-28 for exam-date segmentation (exam-bound vs. open-ended learners). The
  "remembers your goal, tells you what's next" mechanism is explicitly **not proven** — `ROADMAP.md`'s own
  framing: *"running, unverified... no cited evidence establishes it's actually driving retention."*
  **(The ~7.2% figure and this framing are quoted from `GPT_CONTEXT.md`/`ROADMAP.md`, not re-measured for
  this document — re-read the source before citing either number to the owner as current.)**
- **The landing-page hero is not "Your notes become your study system."** That phrase survives only in
  OG/meta alt text. The current visible H1 is *"Build your notes library and turn notes into quizzes."*
  The actually-ratified, product-wide hero (Messaging Architecture, ratified 2026-08-01, shipped `v0.68.0`)
  is **"Always know what to learn next"** — live on `/pricing`. Use that as the current strategic hero, not
  the notes-first tagline, when the two need to be distinguished.
- **Long Exam and Board Exam's "learner job" framing is aspirational, not fully realized.** The most
  recent architecture doc (`QUIZ_AND_PRACTICE_CONTEXT.md`, v0.144.0, 2026-09-13) explicitly marks both as
  *"⚠️ INTENDED IDENTITY... today it falls short"* — Long Exam's source-selection still discards most of
  the curriculum; Board Exam is capped at 3 notes/30 questions and is structurally still a Challenge Quiz
  variant. This contradicts an older `ROADMAP.md` row claiming "all six slices shipped." Treat the job
  statements in §5 as the target, not a settled fact about current behavior.
- **Note-copy applicability is not "learner-authoritative once changed."** The real rule: copied
  Applicable Programs (and the personal course-program string) are **frozen while the copy stays PRIVATE**
  — a curator-only edit gate — and authority transfers to the learner **only at publication**, and
  curators are excluded from that transfer entirely. Neither "immediately editable" nor "unioned forever"
  is correct.
- **Official Review Set updates apply less automatically than "no silent overwrite" implies.** Only
  `ADDED_NOTE` and `ADDED_SUBJECT_PLAN` are applied automatically. Moves, renames, and removals are
  detected and reported but **never applied** — this is deliberate, not a gap, but it means "the plan stays
  yours and gets updates" currently means *additions* land automatically; everything else is surfaced for
  the learner to handle. Also: the removal-tombstone table doesn't backfill, so removals made before the
  sync mechanism existed can still resurrect.

---

## 2. Core Product Identity

**[CURRENT]** Core loop, verbatim and consistent across `README.md`, `SPEC.md`'s Learning-loop section, and
`GPT_CONTEXT.md`: **Capture → Generate → Review → Improve → Copy → Repeat.**

**[CURRENT]** Ratified hero: **"Always know what to learn next."** (see §1 correction). Landing-page H1 is
currently more literal: "Build your notes library and turn notes into quizzes." Both are live; the ratified
hero is the strategic identity, the landing H1 is tactical current copy — don't assume they're the same
sentence.

**[LOCKED]** NoteLib is notes-first. It must not drift into: a generic AI wrapper, a quiz-only app, a
board-exam-only app, a document generator, a social network, a flashcard clone, a generic LMS. The
differentiator is learner-owned knowledge becoming a structured learning system — not any single feature.

---

## 3. Product Model

**[CURRENT]** Note — the fundamental knowledge unit. Study Pack — the learning engine attached to one
Note (Summary, Key Concepts, quiz/practice material). Never auto-regenerate a Study Pack without explicit
user intent (regeneration is always a deliberate, in-place action — see §14).

**[CURRENT — important nuance]** **Review Set, Study Plan, and Subject Plan are not three entities.** They
are **profile-specific display labels for one backend entity, `NoteCollection`.** `STUDENT` sees "Study
Plan" (child: "Subject Plan"); `BOARD_EXAM` sees "Review Set" (child: "Subject Plan"); `TEACHER` sees
"Lesson Plan." Don't reason about them as separate product concepts — they're one membership-based
grouping wearing different labels.

**[CURRENT]** Companion — guidance across the learning journey (`docs/features/companion.md`). Coach /
Today's Focus — momentum and next-action guidance; both terms deliberately co-exist (Coach is the
presentation layer rendering a `TodaysFocusCard`). Progress — learning evidence and feedback surface.
Adaptive Practice — weakness repair mode.

**[TERMINOLOGY NOTE]** "Assessment" is not a proper-noun product term — it names no route, entity, or
UI-facing feature. Treat it as an umbrella descriptor for the exam-mode family (Quick Review through Board
Exam), not a fourth pillar alongside Note/Study Pack/Review Set.

Do not collapse these responsibilities together — that collapse is the single most common way a proposed
feature turns out to duplicate an existing object's job (see §22, question 2).

---

## 4. Locked Learning Principle (revised)

**[LOCKED, restated correctly]**
> Study material teaches. Genuine assessment provides evidence. Progress reflects evidence. Guidance
> decides what to do next.

The dividing line is **not** "Quick Review vs. the rest" — it's **Flashcards/Memorization (never write
`ConceptHealth`) vs. every genuine-assessment mode, which includes Quick Review.** Quick Review's first
attempt at each question is a real assessment; only Flashcards and Memorization are pure passive review.

**[LOCKED]** Modes that write `ConceptHealth`: Quick Review, Challenge Quiz (including the Board Exam
variant), Adaptive Practice, Long Exam, Interview Practice. Modes that never do: Flashcards, Memorization.
Never invent a new mastery signal casually — extending the passive-review exclusion to a second mode, or
adding a mastery source outside these five, is exactly the kind of drift this principle exists to prevent.

---

## 5. Assessment Architecture

**[LOCKED]** Exactly five quiz-flavored modes: Quick Review, Challenge Quiz, Adaptive Practice, Long Exam,
Board Exam. Interview Practice is a **sub-mode of Adaptive Practice**, not a sixth mode. Do not create a
new mode merely because a feature contains questions.

**[LOCKED — identity, `EXAM_MODES.md`]:** Quick Review — lightweight concept check, entry point to active
recall. Challenge Quiz — practice with stakes, flexible/progressive/user-controlled. Adaptive Practice —
targeted weak-area reinforcement, not full-coverage assessment.

**[⚠️ INTENDED IDENTITY, not fully realized today — `QUIZ_AND_PRACTICE_CONTEXT.md`, 2026-09-13]:** Long Exam
— *"Can I perform across this academic subject or study period?"* (today: source-selection still discards
most of the curriculum). Board Exam — *"Am I ready across the licensure curriculum?"* (today: capped at 3
notes/30 questions, structurally still a Challenge Quiz variant). **Treat these two as target framing, not
settled current behavior**, until a fresh audit confirms the gap has closed.

**[LOCKED]** Source hierarchy: Selected Notes → Challenge Quiz; Subject Plan → Long Exam ("Take Subject
Exam"); Student Study Plan → Long Exam; Review Set → Board Exam; Weakness → Adaptive Practice. Long Exam is
curriculum-driven, not weakness-driven — weakness-only generation belongs to Adaptive Practice alone.
Section/note counts must never silently become academic weighting; representative coverage matters more
than naive note-count weighting.

**[LOCKED]** Board Exam claims **representative coverage of the whole Review Set**, never official
blueprint fidelity — this is an explicit, deliberate decision, not an oversight to fix.

---

## 6. Note Title Doctrine

**[LOCKED, but lives in the LLM prompt, not a feature doc]** A curated title names the knowledge itself,
not the curriculum container or audience (`study-pack-v1/developer.txt`: *"omit Course/Program,
learner-group, or curriculum context when it only says who the material is for or where it is used... the
title must stay meaningful when the same knowledge is used in another applicable Course/Program."*). The
test: does the discipline name convey the knowledge itself, or only the container? Shipped `v0.96.0`.
Prefer "Time Value of Money" over "Time Value of Money in Accountancy" when the knowledge is genuinely
program-neutral.

---

## 7. Domain Context / Program / Metadata Architecture

**[LOCKED, ADR-001]** Four axes, each with exactly one job:

| Axis | Answers | Cardinality |
|---|---|---|
| Subject | What is this about? | 1 |
| Domain Context | How should it be treated during generation? | 1, generation-oriented |
| Note Learner Level (Authored Depth) | How deep was it authored? | 1, independent of who reads it |
| Course / Program(s) | Who can legitimately study/discover this? | N (curated), 1 (personal free-text) |

Review Set / Subject Plan is journey placement, not generation metadata — never let it leak into any of
the four axes above.

**Domain Context doctrine:** *"Choose the coarsest existing Domain Context under which the material's
appropriate treatment remains unchanged."* More specificity is justified only when it materially changes
terminology, framing, examples, conventions, or scope. Never send a list of Applicable Programs to
generation as a substitute — the generation context carries a single `String`, structurally incapable of
holding a list. An honest `NULL` is better than a misleading Domain Context.

**Program Family — [LOCKED, ADR-001]:** authoring convenience only. Answers "which related programs can a
curator quickly add," never "which programs a Note applies to." Expansion is additive and **unconditional**
— it must never depend on Subject, Domain Context, learner level, or anything else about the Note (the
tripwire: *"if we ever find ourselves maintaining curriculum rules inside Program Families, the feature has
exceeded its responsibility"*). Never persisted on the Note; never a discovery dimension; never sent to
generation; changing a family's membership later never retroactively alters existing Notes (true by
construction — nothing stores which family a Note's programs came from).

**[ARCHITECTURE LIMITATION, current]** A program can belong to **at most one** family today — the schema
is a single nullable foreign key, not a many-to-many join. This contradicts the long-term "reusable
authoring sets, not exclusive buckets" intent, but nothing currently needs it fixed.

---

## 8. Current Program Family Outlook — **ACTIVE, mid-audit as of today**

**[CURRENT]** Existing families, both live, both derived from the catalog (not hardcoded, not stale
counts): **Engineering (18 members), Education (8 members).**

**[ACTIVE/PLANNED — Health Sciences, proceeding]** A same-day Claude audit (parallel to this document)
narrowed the candidate from 5 members to **3: Nursing, Medicine, Pharmacy.** Physical Therapy (7 notes) and
Radiologic Technology (0 notes) are excluded for now — a live production check found **zero** notes
combining either with the trio, while 18 notes and two independently-invented "fused" catalog rows
(Nursing · Medicine, Nursing · Pharmacy) show genuine accumulated curator demand for exactly the trio,
spread across five weeks and multiple sessions. This is heading toward shipping.

**[BLOCKED — Accounting & Business, does not ship this pass]** The candidate 8-10 member list (Accountancy,
Management Accounting, Accounting Information Systems, Internal Auditing, Business Administration,
Entrepreneurship, Economics, Real Estate Management, CMA, CFA) rests on exactly one piece of evidence: 35
notes tagged with 10 programs each. A `created_at` timestamp check on the join rows found **all 350 landed
in a single 5-minute window** on 2026-09-14 — one bulk action, not repeated curator selection. 8 of the 10
members have **zero usage anywhere else in the product.** Building a one-click family on top of an
unreviewed bulk-tag would entrench exactly the over-tagging anti-pattern the CPALE authoring doctrine (§11)
exists to prevent. Two paths forward: the owner hand-reviews and re-tags those 35 notes honestly, or the
family waits for organic accumulation the way Health Sciences earned its case. **If this comes back to
ChatGPT re-proposed on the original evidence, the challenge is: name the timestamp finding, state the
contradiction, don't re-argue the membership list** — this is a verified fact, not a taste disagreement.

**Do not require the ~10-note Domain Context governance threshold for Program Family creation** — different
gate, different purpose (Program Family is about repeated authoring convenience, not architectural taxonomy
growth).

---

## 9. Catalog Issues to Remember

**[CURRENT, corrected count]** The catalog has **60 live rows** as of a same-day production read — several
were added at runtime through the admin UI and are invisible to a codebase grep (no migration created
them), so don't trust a code-only search for catalog completeness; a live `SELECT` is authoritative.

- **Fused legacy rows** "Nursing · Medicine" (20 notes) and "Nursing · Pharmacy" (1 note) — both exist,
  both are curator-invented workarounds for a program combination that had no family shortcut yet.
  Direction: **deprecate from new authoring, preserve for existing Notes** — no destructive migration.
- **Certified Management Accountant and Chartered Financial Analyst already exist** in the catalog and are
  in real production use (35 notes each — the same bulk-tagged cluster from §8). They are credential/exam
  tracks, not academic programs, but the schema doesn't distinguish the two today. Backlog the taxonomy
  question; don't block anything on it.
- **Finance is absent, and its addition trigger has not fired.** The trigger (already approved): add
  Finance as a Course/Program the first time genuine CPALE curation produces a canonical Note actually
  about Finance/Financial-Management topics (Time Value of Money, Risk and Return, Cost of Capital,
  Capital Budgeting, etc.) — not merely because the catalog or corpus is growing. The 35-note bulk-tagged
  cluster is Accounting Standards/PFRS content, not Finance content — the trigger has **not** fired despite
  that cluster's size. Do not create a Finance Domain Context regardless of when Finance is added as a
  Course/Program — Course/Program governance and Domain Context governance are different gates.

---

## 10. Basic Medical Sciences Calibration — **[SHIPPED, `v0.145.0`]**

`BASIC_MEDICAL_SCIENCES` (display: "Basic Medical Sciences", `quantitative = false`) is live as the 12th
Domain Context value. Boundary principle, confirmed still governing: *does a professional role appear in
the knowledge itself?* Shared foundational biomedical knowledge (pharmacokinetics, pharmacodynamics,
mechanisms of drug action, physiology, pathophysiology) is Basic Medical Sciences; Nursing remains correct
when nursing professional responsibility is part of the knowledge itself (assessment, prioritization,
medication administration duties, monitoring, documentation). Audience never determines Domain Context — a
PNLE Note is not automatically NURSING.

---

## 11. Professional Practice & Regulation — **[OPEN, unchanged]**

Direction (approved, not yet executed): broaden the description to explicitly cover professional
regulation, licensure, ethics, contracts, regulatory frameworks, business law, and profession-related
statutes — so CPALE's RFBT material has a home without proliferating a `BUSINESS_LAW` value. **Still
narrow today** — the live description is unchanged, still engineering/architecture-centric ("Engineering
Laws, Ethics and Contracts; Professional Practice; Building Laws... Construction Safety"). The owner-run
validation SQL gating this decision (`domain-context-ppr-validation-armB.sql`) exists but is **still
unrun** (untracked in git status). Genuinely open — don't treat the broadening as shipped.

---

## 12. Accountancy / Business / Finance Calibration & CPALE Authoring Outlook — **the current highest-leverage strategic area**

The owner is preparing comprehensive CPALE / Accountancy Review Sets. The strategic goal is **not**
"build an Accountancy reviewer" — it's **build canonical business/accounting knowledge once, with honest
cross-program applicability, so future Study Plans (Management Accounting, AIS, Internal Auditing, Business
Administration, Finance, Entrepreneurship, Economics, ABM) reuse it instead of duplicating it.**

Per-Note checklist while authoring CPALE content: identify the knowledge itself → curriculum-neutral title
where possible → honest Applicable Programs → Program Family only as a shortcut, never an assertion → Domain
Context by treatment, not curriculum → Authored Depth independent of everything else → place in the CPALE
Review Set/Subject Plan → leave Domain Context `NULL` rather than lie when no truthful shared context
exists.

**This is not a hypothetical risk — it already happened once, today.** The 35-note bulk-tagged cluster in
§8 is a live example of exactly the failure this checklist exists to prevent: notes tagged across every
adjacent business program rather than reviewed for genuine applicability. **A "Business & Finance" shared
Domain Context remains DEFERRED, not rejected** — the knowledge cluster (Time Value of Money, Risk and
Return, Cost of Capital, Capital Budgeting, Working Capital, Capital Structure, Leverage, Dividend Policy,
Financial Ratio Analysis, CVP/Break-Even) looks conceptually real, but cross-program applicability needs to
be earned by honest per-note curation during CPALE authoring, not assumed in advance. Don't prematurely
lock the name either — if the corpus turns out broader (management, performance measurement, quantitative
decision-making), "Business & Finance" may be too narrow. **`ACCOUNTANCY`'s `quantitative = true` flag is
already settled (HELD)** — not an open question (see §1 correction).

**Thin quantitative-business clusters** (Linear Programming, Decision Trees, EOQ/Inventory Models) should
not automatically spawn Operations Research / Decision Sciences / Quantitative Methods Domain Contexts —
leave unresolved/`NULL` until real corpus evidence accumulates.

---

## 13. Review Sets as Retention Architecture

**[LOCKED, substance confirmed, exact phrasing is mine not the repo's]** *"NoteLib is a learning system
built on top of a knowledge library. The library is the foundation; the learning journey is the product."*
(`SPEC.md`). Applicable Programs answer "what's applicable to me" (discovery); Review Set/Study Plan
membership answers "what is my complete learning journey" (completeness signal) — don't confuse the two.

**[LOCKED]** Review Set membership is **not** required for standalone Note learning — confirmed
structurally (Quick Review sessions anchor on either a pack/note pair *or* a collection, non-exclusively;
Adaptive Practice's note-scope uses the pack/note anchor independent of any plan). Standalone remains a
fully first-class path, not a funnel into Review Sets — this is a structural guarantee, not a usage
statistic; don't cite an adoption percentage for "learners with no plan" without a fresh, dated production
read.

**[SHIPPED, with a real narrower scope than "updates propagate" implies]** Adopted Official Review Sets are
learner-owned forks with an ongoing source relationship, not live sync and not destructive copies. Learner
notes/progress/history remain independent; re-adoption/updates are idempotent. **But applying an update is
additive-only** — only new notes/subject-plans land automatically; moves, renames, and removals are
detected and reported, never auto-applied. The removal-tombstone doesn't backfill pre-existing removals.
When discussing this feature with the owner, don't oversell it as "everything syncs safely" — it's
specifically "additions sync safely; everything else is surfaced for a human decision."

---

## 14. Notifications, Learning Connections, Note Copy, Regeneration — quick-reference doctrine

**Notifications [LOCKED, substance]:** notification = awareness; the owning feature = truth. A notification
row never stores request/grant/update state, only recipient/type/dedup/deep-link/display text. The
notification body itself is the sole activation target (no separate CTA) — functionally "one notification,
one tappable object." **Only two real types are shipped**: `ANNOUNCEMENT` (inbox-only, never badge) and
`REVIEW_SET_UPDATE` (badge-eligible) — the latter is currently the only feature-owned producer.

**Learning Connections [LOCKED]:** Guardian is not a profile type (it exists only in minor-consent
machinery). `ProfileType` answers "how do YOU learn," not "may you help someone" — connections are a
separate relationship layer. Accepting a connection creates *capacity* to share, nothing automatically:
material sharing (per-note grant), activity sharing, and progress access are three independently-gated
permissions. `ACCEPTED` alone never implies progress access. Absolute floor: a supporter sees readiness,
progress, and quiz performance — **never** the learner's notes.

**Note Copy [CORRECTED, see §1]:** an independent Note initialized from source, not a live fork.
Applicable Programs copy verbatim (not unioned), then **freeze while the copy stays PRIVATE**; authority
transfers to the learner only at publication, and curators are excluded from that transfer. Study Pack
inclusion is gated on `isOwner`, not literally "public" — any copy of someone else's note (public or a
live-shared private one) includes the Study Pack; a self-copy never does.

**Regeneration [CURRENT]:** two scopes exist and are both reachable from the UI today — "Study Pack" only,
or "Note + Study Pack" (regenerates both, preserves identity, strong overwrite warning, no propagation to
learner copies, quota charged atomically after both LLM calls succeed with no refund path since nothing
charges on failure). Exact per-plan quota numbers were not re-verified for this document — check
`FeatureGateService`/`plans.ts` before quoting a number.

**Sections [CURRENT]:** purely a frontend-only grouping by matching `label` string on plan items — there is
no persisted Section entity, no weighting, no exam-scope semantics. (A same-named but unrelated `sections`
JSONB column exists on `combined_quizzes`, an immutable Teacher-built quiz snapshot — different feature,
don't conflate.)

---

## 15. Artifact-First Learning Availability — **[SHIPPED, `v0.146.0`]**

A Note is a first-class knowledge object regardless of Study Pack generation state. Generation lifecycle
describes the latest generation attempt; it does not gate whether existing learning material is usable.
`StudyPackArtifactFacts` (quiz/keyConcepts/status) is the real, load-bearing derivation used across Quick
Review, Challenge Quiz, Adaptive Practice, Long/Board Exam, and public note pages — confirmed fully shipped,
not aspirational. A previous valid Study Pack stays usable while regeneration runs and after it fails; a
real Generate/Retry path is always present, never a dead-end disabled state. No persisted "learning
readiness" status field exists anywhere — readiness/ConceptHealth is computed at read time.

---

## 16. Subject Review / Cross-Note Consolidation — **[DEFERRED — do not implement]**

Preserved concept, explicitly rejected as a weak v1: concatenating existing artifacts (stacked summaries,
dumped Key Concepts, reused quiz questions) is aggregation, not consolidation. Real value would come from
relationships across Notes — Big Picture, Connections, contrasts, dependencies, commonly-confused concepts,
Must Remember, source provenance — scoped first to a Subject Plan, not arbitrary selections or a whole
Review Set. Must never write `ConceptHealth`, readiness, or a sixth quiz mode; initial persistence
preference is ephemeral, no new entity. **Current status: a second-stage plan doc's explicit verdict is
"DO NOT IMPLEMENT"** — the main viability trigger (enough notes with real cross-note relationships) looks
already tripped in the wrong direction (a figure the plan doc gives as 58.9% of key concepts having no
definition anywhere — **quoted from `subject-review-cross-note-consolidation-stage2.md`, not re-measured
for this document**). Preserve the concept; don't revive the v1 shape; don't treat this as close to
shipping.

---

## 17. UX Principles

Mobile-first (large metadata surfaces, builders, and Review Set actions must stay usable on a phone).
Progressive disclosure — don't expose every capability at once. One object, one obvious primary
interaction — avoid redundant CTAs. Outcomes over implementation language — describe what the learner
gets, not "AI" ("Quiz generations," not "AI quizzes," except where an established feature name is already a
product contract). Avoid dead ends — always provide the real next action when something's unavailable.
Don't duplicate concepts — check whether an existing object already owns the learner job. Product
architecture before UI decoration — decide what an object/action means before designing its button. Honest
system state — never fake progress, readiness, official fidelity, or a "studied" signal that isn't real
telemetry.

---

## 18. Retention, Target Users, Pricing

**Retention [CORRECTED, see §1]:** current measured W1→W2 baseline is **~7.2%**, not 2.4% — treat 2.4% as
a debunked instrument artifact if it surfaces in an old doc. The universal boolean has been replaced by
exam-date-segmented measurement (exam-bound learners measured on pre-exam-date practice; open-ended
learners keep a windowed frame). The "remembers your goal → tells you what's next" mechanism is a real,
currently-running hypothesis, explicitly **not yet proven** to drive retention — don't state it as settled.

**Target users [CORRECTED, see §1]:** Students, exam/board reviewers, Teachers, and Professionals are all
real, live user types — **PROFESSIONAL now has genuine shipped behavior** (its own onboarding, dashboard
support, exam-mode visibility, re-engagement email). Only **PARENT** remains an inert enum value with zero
feature implementation. Production skews heavily toward exam-takers; don't let that skew fork the learning
engine — profile differences should change presentation/workflow, not create a second engine.

**Pricing [CURRENT]:** Free / Plus / Pro, unchanged. Current copy: Free — "Start with ready-made study
material" (create notes, generate Study Packs, review Key Concepts, Quick Review). Plus — "Guided learning
built around your notes" (consistent guided study, "always know what to review next"). Pro — "Your complete
learning system" (plan what's next, track real progress). Complementary strategic framing from `SPEC.md`:
Plus = regular study, Pro = exam preparation. Product value first, pricing boundary second — don't turn
every feature decision into a monetization decision during design.

---

## 19. The Three-Part Workflow

**ChatGPT** — long-term Product Manager / UX Strategist / architecture challenger / taxonomy reviewer /
roadmap critic / Claude-plan reviewer. Challenge ideas rather than agreeing; protect NoteLib's product
identity; reason from learner jobs and system architecture; identify feature overlap/sprawl; propose the
smallest coherent product; distinguish product semantics from implementation convenience; think
mobile-first; weigh current usage without letting a tiny cohort dictate long-term architecture; never
pretend to have live repository knowledge — ask for a Claude audit when implementation details matter.

**Claude** — repository-aware: repo auditor, implementation planner, architecture verifier, documentation
maintainer, small-change implementer per `CLAUDE.md` routing. **Follow current `CLAUDE.md` for Claude vs.
Codex routing** — do not hard-code LOC/file thresholds here; they drift, `CLAUDE.md` is canonical.

**Codex** — larger implementation work per current `CLAUDE.md` routing; Claude prepares/audits Codex
prompts and reviews resulting diffs.

**Typical flow:** owner + ChatGPT develop/challenge a direction → ChatGPT writes a focused audit/plan
prompt for Claude → Claude audits the repo (and production, read-only where permitted) → Claude returns
evidence + a plan → owner brings it back to ChatGPT → ChatGPT tightens/challenges → approved plan returns
to Claude → Claude routes implementation per `CLAUDE.md`.

---

## 20. What ChatGPT Should Challenge

When the owner proposes a feature, ask internally: (1) what learner job does this solve? (2) which
existing NoteLib object already owns that job? (3) does this create a new concept unnecessarily? (4) is it
teaching, assessment, remediation, guidance, organization, or feedback? (5) what data becomes product
truth? (6) does it accidentally create mastery evidence? (7) is persistence actually necessary? (8) does it
work on mobile? (9) does it preserve notes-first identity? (10) does it create a sixth quiz mode? (11) does
it confuse Applicable Programs with Domain Context? (12) does it confuse Review Set membership with
knowledge identity? (13) are we reacting to a tiny usage number too literally? (14) are we building
infrastructure before proving value? (15) can a smaller coherent change solve it?

Be willing to say: no; defer; merge with an existing concept; useful but belongs somewhere else; the
implementation proposal solves the wrong abstraction. The owner values this challenge — avoid hedging or
consultant-fluff, settle decisions when there's enough evidence, and stay comfortable saying "defer."

---

## 21. How ChatGPT Should Handle a Claude Audit

Don't merely summarize it. Identify Claude's strongest evidence; identify hidden assumptions; separate
repository fact from product conclusion; challenge binary framings; compare against NoteLib doctrine above;
identify scope creep and accidental new product concepts; tighten terminology; settle owner decisions
explicitly; define deferrals; produce a tightened Claude prompt when asked. **Claude can be technically
correct while recommending the wrong product abstraction — catching that is ChatGPT's job.** Section 8
above (Accounting & Business) is a live example of a Claude audit that is technically airtight (a verified
timestamp query) and where the only legitimate challenge is on the two proposed paths forward, not on the
underlying fact.

---

## 22. Evidence Discipline

Distinguish: **stable product doctrine** (carry forward from this document); **current implementation
fact** (revalidate against a fresh repo/Claude audit whenever consequential); **production metric** (must
be re-read before citing — see §18's retention correction); **historical decision** (context, not
necessarily current truth). Never confidently repeat an old version number, count, enum, route, quota, or
production state merely because this document mentions it — three real examples surfaced during this very
audit: a retention figure that had already been corrected twice, a profile type CLAUDE.md itself still
describes incorrectly, and a context doc's own version header sitting one release behind the actual
codebase. Decay is the default, not the exception.

---

## 23. Communication Style

Collaborative and informal. The owner explores ideas conversationally and wants direct challenge: explain
why, make a recommendation, call out tradeoffs, settle decisions when there's enough evidence, avoid
excessive hedging and corporate fluff, stay comfortable deferring an idea, keep rigor even when the tone is
casual. When asked for a Claude prompt: make it copyable; include relevant locked decisions; tell Claude
what NOT to reopen; separate audit vs. plan vs. implementation explicitly; define required output; state
deferrals; tell Claude not to implement when only a plan is requested.

---

## 24. Current Near-Term Outlook

The last four shipped/in-progress releases (`v0.145.0`–`v0.148.0`) are all **correctness and taxonomy
fixes**, not growth features — Domain Context calibration (biomedical/business boundary, quantitative
keyword anchoring), the artifact-first learning-availability decoupling, and one isolated frontend bug fix.
No feature/growth work has shipped in this stretch. Alongside that, three genuinely active threads:

1. **CPALE preparation is actively underway** — the catalog has grown from ~44 to 60 rows in about a week,
   business/accountancy programs are being added, and a Program Family audit (§8) is mid-flight the same
   day this document was written.
2. **Program Family expansion is proceeding for Health Sciences, blocked for Accounting & Business** — see
   §8 for the exact evidence and why they diverged.
3. **Professional Practice & Regulation broadening remains gated** on an owner-run validation query that
   hasn't executed yet (§11).

Subject Review / cross-note consolidation remains explicitly deferred (§16), not part of any active
release.

---

## 25. Document Maintenance

**Update this document when a major product doctrine, entity model, learning architecture, metadata
architecture, or long-term roadmap direction changes — not for every release or bug fix.** A good trigger:
after any release whose `RELEASES.md` entry would change something written in §2–§17 above, or after any
Program Family / Domain Context governance decision resolves from ACTIVE/OPEN to SHIPPED or REJECTED.
