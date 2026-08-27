# NoteLib — Decision History

> **Module — not a standalone brief.** Paste `GPT_CONTEXT.md` first; this file assumes it.
> Paste this module when the conversation is about **a question that may already have been settled — read before reopening one**.
> Last updated: v0.92.0 - 2026-08-27 (Released). **Adds the `v0.91.0` and `v0.92.0` verdicts — read the Shared Learning Material and Activity Sharing sections below before re-proposing anything in that arc.** Previously v0.90.0 - 2026-08-27. **Adds the three Linked Learners surfacing verdicts taken 2026-08-27 — read them before re-proposing any of the four.** Previously v0.89.1 - 2026-08-20. **Stamp had gone four releases stale (v0.85.0) — restamped with the decisions most likely to be re-proposed.** **⚠️ `v0.89.0` shipped Learning Connections as a CAPABILITY, not a profile type** — a parent, tutor, sibling or mentor can make a quiz for someone, connect with them, and see their progress. **`ProfileType` answers "how do YOU learn?", not "may you help someone?"**, so the old design forced a parent to claim to be a Teacher — changing their own dashboard and practice options — just to help their child. **⚠️ Do NOT re-gate any of this on `ProfileType`; `PARENT` stays unimplemented with zero users.** **⚠️ THE PRIVACY LINE IS RATIFIED AND ABSOLUTE: a supporter sees readiness, progress and quiz performance, NEVER the learner's notes** — this protects the core loop, because learners who suspect notes are visible write less honestly. Teachers still exclusively keep DOCX export, multi-version exports, question-count control and the Exam Builder; only the single-quiz share link opened up. **Ratified 2026-08-19 (Q4):** connections are **invite + accept in BOTH directions, revocable either side**, and **acceptance is load-bearing** — without it anyone could claim a supporting relationship over any account by knowing an email address. **Ratified 2026-08-19 (Q5):** age is collected **at link time, never at signup**, with guardian consent below a threshold that is **configuration, and whose NUMBER remains owner-owned pending counsel** — do not let a shipped default be read as a legal position. **Rejected and recorded:** `learnerLevel` as an age proxy (breaks both ways), adults-only-first (excludes the motivating parent-child case), and a supporter `ProfileType`. Previously v0.85.0 - 2026-08-18. Adds the **Domain Context Catalog** decline (2026-08-17), which is the single most likely thing to be re-proposed from this span. Previously v0.73.0 - 2026-08-12.

---

## Shared Learning Material and Activity Sharing — settled in `v0.91.0` / `v0.92.0`

**⚠️ `NoteVisibility` stays `PRIVATE | PUBLIC`. A `SHARED` enum value was REJECTED and must not be re-proposed.**
Three independent reasons, all verified against code: every read path is `findByIdAndOwnerUserId`, so the enum
would grant nobody anything and would only *assert* what a table decides — two sources of truth that can disagree,
with the label being the one people trust; `AccountPurgeService` **retains `PUBLIC` and deletes `PRIVATE`**, so a
`SHARED` note matches neither branch and would **survive the purge of a deleted account while staying readable by
its recipients**; and the enum has 42 usages across 24 files. Access lives in a `note_shares` grant table, and a
shared note stays `PRIVATE` — excluded from Explore and included in purge **by default rather than by 24 correct
decisions**. **The three-option "Who can access this note?" control is DERIVED, never stored.**

**Selecting *Private* revokes every live share (owner decision, 2026-08-27),** with the count named in the
confirmation. Silently keeping live shares under a control labelled "only you" would be a lie in the UI.
*Public* revokes none, because public is strictly broader.

**⚠️ PHASES ARE SEQUENCED, NOT EVIDENCE-GATED — the owner has ruled this TWICE.** With zero connections in
production, a gating read returns nothing, so gating is not caution, it is an indefinite stop. Checkpoint rows for
this arc are **observational and gate nothing**. Do not propose holding a phase behind the previous phase's read.

**⚠️ Connecting shares NOTHING.** A relationship creates the *capacity* to grant. Nothing is reciprocal by
default: A→B activity sharing on with B→A off must be representable, and the connection DTO carries two
independently computed fields so a single boolean cannot collapse the model.

**⚠️ No relationship-type column (`GUARDIAN | TUTOR | PARTNER`) — REJECTED.** Permissions define the
relationship; a type column would immediately invite gating on it, which is the exact `ProfileType` mistake
`v0.89.0` exists to correct.

**Guardian consent, two settled properties.** It is **asymmetric** — it gates the LEARNER's data only, so a
supporter sharing their *own* activity with a consent-requiring learner is not blocked. And it **fails CLOSED on
an unknown birth year**: it denies nobody today, which is precisely why it must not fail open, since the only
route to a null is a future grant path that produced `ACCEPTED` without one — the exact state the check exists to
catch.

**⚠️ Naming, settled in `v0.92.0`: the quota LABEL ("AI quizzes") and the Challenge Quiz MODE name are different
strings on purpose**, pinned by a regression test. And **no second counter** for shared quizzes — that is a
pricing decision nobody has taken. Disclosure was the fix; **do not move the share-link check into the generation
path**, because generating without sharing is legitimate.

**"Shared with you" Library placement — DECIDED 2026-08-27, before the consultation was even sent.** Diagnosis:
**terminal signal AND distance**, not terminal signal alone — which overturned the session's own hypothesis that
announcing the section would suffice. Ratified: the section **stays below the complete owned-notes flow**, never
above owned material and **never interleaved with owned pagination**; when shared items exist, a compact
persistent **`Shared with you · N` jump affordance** sits in the existing upper Library controls and scrolls to
the section; it renders **nothing at zero count and reserves no empty space**. **⚠️ It is an interim NAVIGATION
solution, explicitly not an announcement banner**, and it **retires when real usage shows *Shared with me* has
become a repeated destination** — the evidence that earns the eventual `[ My Notes ] [ Shared with me ]` tab
architecture. **Rejected: moving the section above the owned notes** (it would displace the learner's own material
on every visit, including for the majority with nothing shared). **Still not open: shared notes are never mixed
into the owned grid, and the section stays hidden when empty.**

**⚠️ Known and NOT yet fixed — do not assume the activity grant bounds what a supporter sees.**
`/linked-learners/{id}/progress` returns the same four engagement fields gated only on `ACCEPTED`, with no grant
check, so the `v0.92.0` sharing control is real learner→supporter and **decorative in reverse**. Fixing it is
reserved for Phase 3, which reimplements that helper over a `PROGRESS` grant.

---

## Linked Learners surfacing — four proposals audited 2026-08-27, three settled

Raised by the owner after `v0.90.0` merged, audited against real code the same day. Full brief:
`docs/claude-plans/linked-learners-surfacing-product-ux-consultation-prompt.md`.

**1. "Spend quiz quota when generating a quiz for someone" — ALREADY THE BEHAVIOUR.** It has always drawn down the
user's own Challenge Quiz allowance (Free 20 / Plus 100 / Pro 200). Nothing to build; what is missing is disclosure,
plus the fact that the stricter share-link cap (Free 3) is enforced only at link creation, after the LLM cost is paid.
See `QUIZ_AND_PRACTICE_CONTEXT.md`.

**2. "The quiz preview looks like the Teacher surface" — an ADMIN artifact, not a product problem.** `canExportDocx`
is `role === "ADMIN" || profileType === "TEACHER"`; `canShareQuiz` is plain `Boolean(authUser)`. An ordinary student
or parent account sees no classroom vocabulary on that page. **The Teacher profile type is not being diluted by
Phase 1.**

**3. "Surface 'Quiz for someone' only when a connection exists" — DECIDED AGAINST, and the reason is not caution.**
Gating it makes forming a connection the **price** of sharing a quiz, so any invitations that follow are
instrumental rather than demand — manufacturing a false pass on `[CHECKPOINT — due 2026-09-19]`, whose entire
question is whether anyone forms a connection, three weeks before it is read. **This is "do not break the
instrument", not "wait for measurement before shipping."** It would also gate on a state no user has ever reached,
dropping reach to zero. The ratified reason still stands independently: a shared-quiz recipient needs no account and
no relationship. **If what is wanted is the removed practice-row button back, that is a separable PLACEMENT question**
— the argument that moved it was that beside *Start Quick Review* it read as an avoidance path, which says nothing
about who should see it.

**4. "Apple Health-style sharing so learners can compete" — the dashboard already ships; competition is blocked on
COMPARABILITY, not privacy.** The supporter progress view has existed since `v0.89.0` and simply never renders with
zero relationships. Reciprocal linking is already permitted at every layer (per-direction uniqueness; no
opposite-direction guard) and surfaced at none. **But every aggregate is computed over the learner's OWN notes and
packs, so there is no shared denominator — a leaderboard would rank library composition, not effort**, and a learner
with three easy notes would outrank one grinding a 77-note board review. Minors and social comparison are a
secondary concern behind that. **Self-comparison over time is safe and already exists as My Progress.**

## Domain Context Catalog — DECLINED 2026-08-17, on production evidence

**This is the one most likely to come back, because the motivating experience is real and recurring.** The owner hit it while building a Civil Engineering Review Set: *where does an* **Engineering Economics** *note go?* The proposal was to make Domain Context an admin-managed catalog — still curated, still closed to learners — so new values could be added without a code change. Its own framing: *"My goal is not to make Domain Context flexible. My goal is to make it scalable while keeping it curated."*

**A cold-context agent assessed it and recommended declining. The production read is what settled it, and it points the opposite way from the proposal's premise:**

- Curator public notes are **12.7% classified** — 121 of 956. **835 carry no Domain Context at all.**
- **Only 4 of 8 existing values have ever been used.** `ACCOUNTANCY` and `NURSING` are at **zero** — against **286 unclassified notes sitting in those exact programs**.
- There is **one curator**. A catalog is coordination infrastructure for a problem of coordination between multiple authors.

**So the taxonomy is not too small. It is unused.** Adding a management surface to a vocabulary where half the values have never been applied would build capacity for a constraint nobody has hit yet, and the notes that would populate the new values are the same notes that have not populated the old ones. **Domain Categories (a grouping layer above the 8 values) was declined on the same ground** — grouping is for navigating a vocabulary too large to scan, and eight items on one screen for one person is not that.

**What the assessment found instead is the part worth remembering:** while auditing what the enum is actually responsible for, it found a **live generation-quality defect much larger than the proposal that surfaced it** — computation guidance was off for 48.4% of the public catalog because the signal was substring-matched against the domain's display **name**. That became `v0.85.0`. **A proposal being declined is not the same as the itch behind it being imaginary**; here the itch was pointing at something real, one level down.

**The answer to the original question, for the record: Engineering Economics belongs to `ENGINEERING_MATHEMATICS`** — `08:57` files it there, and it is time-value-of-money math (annuities, present worth, depreciation, rate of return), a calculation subject wearing a business word. **That answer already existed in a ratified planning doc and appeared nowhere in the product**, which is why `v0.85.0` put each value's covered subjects beside the authoring select.

**Reopen only on new evidence, and the evidence is named:** a second curator, or classification moving materially off 12.7% with authors hitting genuine gaps in the 8 values. **Do not reopen it as free-text Domain Context, automatic creation, or learner-editable values** — all three were out of scope in the original proposal too. Full reasoning: `docs/claude-plans/domain-context-catalog-assessment.md`; reads: `domain-context-adoption-read.sql`, `quantitative-context-coverage-read.sql`.

---

## Slice 3 — Program Family expansion: RATIFIED 2026-08-05, SHIPPED in `v0.71.0`

**All four decisions are closed.** Do not re-open them; they are binding in `docs/architecture/ADR-001-canonical-knowledge-architecture.md`, and the shipped behaviour is recorded under `RELEASES.md` v0.71.0 → Slice 3. (The working decision sheet that produced them, `20-slice-3-family-expansion-decision-sheet.md`, was removed 2026-08-06 once the slice shipped and the rulings were absorbed into the ADR; recover it from git history if the reasoning is ever needed.)

1. **The catalog represents *valid applicability*, not curriculum coverage.** It answers *"who can legitimately study this note?"*; **Review Sets** are what communicate completeness. **The catalog still follows curriculum — a program simply no longer needs a complete Review Set to earn an entry, only legitimate canonical notes applicable to it.** Pre-seeding every PRC engineering program is **rejected as premature**; growth is incremental and demand-driven by authoring. Refines rather than reverses the `v0.70.0` *follow-not-lead* posture; the `Computer Science` / `Software Engineering` exclusions stand.
2. **Program Families stay intentionally dumb** — an authoring shortcut, never a curriculum engine. No hidden inference, no read-time applicability, no curriculum intelligence.
3. **Expansion fills in all family members.** No curated subsets; the author trims.
4. **Expansion is never subject-conditioned** — explicitly rejected, because it would make Families a second curriculum taxonomy and permanently couple Subject knowledge to applicability rules.

**Binding principle:** Program Families are a **productivity feature, not a curriculum feature.** They are deliberately allowed to over-select, because the Note's explicit Applicable Programs are always the source of truth. **The tripwire:** maintaining curriculum rules inside Program Families means the feature has exceeded its responsibility.

**Programs and Review Sets answer different questions — ratified, and it is the reason the catalog can grow on applicability without implying coverage:**

| Surface | Answers | Role |
|---|---|---|
| **Program** | *"What notes are applicable to me?"* | discovery |
| **Review Set** | *"What is my complete learning journey?"* | curriculum completeness |

**Coverage is emergent, not declared.** Every learner-facing program list derives from *notes*, not the catalog, so a program with no applicable notes is invisible to learners and the catalog is effectively author-facing. The residual risk is a **thin** shelf rather than an empty one — a program carrying a few shared foundational notes reads as a curriculum without being one.

**Agreed design direction, deliberately NOT built in slice 3:** communicate coverage **at the Program level** when a learner browses a Program with no dedicated Official Review Set — conceptually *"This Program currently contains shared foundational notes. A dedicated Official Review Set is still being developed."* **Rejected:** per-note coverage indicators and any new coverage metadata system; the completeness signal already exists and it is the Review Set. Needs its own scoping pass.

## Open Question — Explore's default tab (STILL unresolved as of v0.69.0, three releases later)

**The question:** `/explore` (shipped in `v0.67.0`, see above) shows a two-tab segmented control: "Review Sets" (curated, Official/admin-authored collections) and "Notes" (the original public-notes library). As shipped, **"Review Sets" is both the left-positioned tab and the default-selected tab** — a user landing on `/explore` sees curated content first, with the public notes library one click away. The product owner noticed this only after shipping and flagged: NoteLib's original identity was built around Public Library (organic, user-generated, browsable-by-anyone notes) — it was one of only ~3 main nav items for most of the product's history — and the new default may be quietly deprioritizing that identity in favor of the newer curated-content push.

**Opus's opinion (fresh-context agent, read the real code/docs before answering), given 2026-07-30:**
> **Recommendation: flip the default to Notes. Leave tab order alone.**
>
> The identity argument alone is the weakest reason to move a default — products are allowed to outgrow their origins — but the empirical case is strong and points the same way:
>
> 1. **Review Sets can't reward browsing today.** Producing more Official Review Sets is bottlenecked on the still-unscoped Curator pipeline. Only 4 Goals have ever been adopted, ~84% concentrated in two (LET Comprehensive, PNLE Core). Anyone outside those two programs hits an empty "Recommended" state on the default panel of a nav item called Explore.
> 2. **For `BOARD_EXAM` — the segment this whole redefinition targets — Review Sets is redundant as a default.** Onboarding already auto-adopts a qualifying Official Review Set and lands them on it; it is also the Dashboard Primary card; Explore already carries an Exam Hub pointer card. Review Sets has three other entry points. The public library now has exactly one.
> 3. **It biases the release's own checkpoint.** Defaulting to the panel that dead-ends for most users skews the `2026-09-13` engagement read that decides whether Explore was worth building. Flip now, while that window is effectively unstarted, rather than waiting until it contaminates the read.
>
> **Explicitly rejected:** profile-aware branching (adds a second variable to a checkpoint that needs one), and changing tab *order* (in a two-item control the selected panel is ~100% of visible content, so left-position precedence is close to noise).
>
> *Also flagged a hardcoded "Review Sets" tab label bypassing the profile-aware `getCollectionLabels()`, so a `STUDENT` saw a tab labeled "Review Sets" containing "Recommended Study Plans" — **this was fixed in `v0.67.1`** and is no longer open.*

**What's needed from you (GPT):** an independent second opinion on the same question — does defaulting `/explore` to Notes (public library) over Review Sets make sense, both on identity/positioning grounds and against the concrete adoption/engagement facts above? Agree or disagree with Opus's specific recommendation (flip default only, not order; not profile-aware; do it now before the checkpoint window contaminates). No code has been changed yet — this is genuinely still open.

---

## Resolved 2026-08-04 — a note DOES own its depth; the open part is authoring, not the model

Raised by the owner immediately after `v0.69.0` deployed, and **genuinely open** — do not treat the depth axis as settled.

**The owner's position:** notes should not carry a learner level at all. It originally existed so *quizzes* could be pitched correctly, i.e. it was a property of the *reader*. Course/Program arguably already implies difficulty. And it feels absurd that a learner who mis-set their profile to `College` could get a college-level quiz on a grade-school Algebra note. Two related proposals: give the program catalog a `learner_level_id` so depth is inherited rather than chosen; and/or carry the college→board-exam distinction on `ProfileType` instead.

**The counter-case:** the absurd scenario is precisely what the note's level *prevents* — with the note authored at Grade School, a College reader still gets a grade-school quiz, and removing the field makes depth fall back to the reader, which produces the complaint. "Program implies difficulty" also describes the model `v0.69.0` just retired (`Grade School` was a *level* sitting in a program field). And `ProfileType`'s `STUDENT` value spans four distinct depths, so it cannot carry depth without losing them. Where the owner is right: ordinary learners already never see the field, so the friction is Teacher/Admin-only — which argues for **inferring** the value (from the Review Set being authored into, then the author's profile) rather than deleting the axis.

**RESOLVED 2026-08-04 after a GPT pressure test.** The four-axis model **stands unchanged** and the note keeps its own depth: the reframed question is *"what is the canonical source of educational depth?"* and the answer is **the content itself** — Grade School / Senior High / College Engineering / Board Review Algebra are different knowledge artifacts, not one artifact with four quiz settings. Both alternative placements are rejected on the record (depth on the reader alone; depth on the program via `course_programs.learner_level_id`, which fails because Civil Engineering spans Year 1–4 plus Board Review). **What remains open is the authoring experience, not the model:** the direction is inferred metadata with explicit human override (**Review Set → author profile → override**), recorded as a direction section in `ADR-001` with four binding constraints — Subject and Domain Context may not infer depth, inference is a UI pre-fill and never a server-side write (it would destroy the `domain_context IS NULL` promotion marker), year-level granularity is out of scope, and the label may not be renamed to `Intended Audience` because `target_profile_type` already owns that concept. **Not implemented, and gated behind making authoring metadata editable on `STUDY_PACK_READY` notes and then R4.**

**A neutral, self-contained consultation prompt exists** at `docs/claude-prompt/canonical-knowledge-architecture-out/14-learner-level-necessity-gpt-consultation.md`, including a bias warning about who wrote it. **Sequencing note: R4 has not run yet.** It is the evidence on whether Domain Context works at all, and if it shows content drifting generic the answer to this question changes anyway — so resist restructuring the taxonomy before that read exists.

---

## Companion Scoping — Resolved 2026-07-29 (was an open question before v0.63.0 signoff)

**Original question:** should Companion (and by extension Ask Companion) ever reach users who never adopt a Review Set? A GPT exchange proposed keeping Companion permanently Review-Set-only while giving Note Detail "transition features" into Review Set adoption. Pressure-tested with Opus against the real code — **resolution, shipping as `v0.64.0`:**

- **Companion is not "Review-Set-scoped" — it's admin-authored-Official-collection-scoped.** `NoteCollectionService.setCompanion`/`clearCompanion`/`generateCompanion` all gate on `assertAdmin(user)` — a learner-created Review Set can never have a Companion, because nobody curated it. That's the structural reason, not a philosophical stance about curriculum-vs-note altitude — so "permanently Review-Set-only" was dropped in favor of the narrower, true claim above. Personalization (PRO, still Parked not ruled out) stays the mechanism that could eventually reach a no-Review-Set learner some other way.
- **The three-layer guidance model:** content explanation (Study Pack, every Note) → diagnostic guidance (ConceptHealth/readiness/weak concepts, every Note, personalized) → editorial guidance (Companion, curated Official collections only). A note-only user isn't excluded from "the guidance layer" — they're missing only the one layer that structurally requires a human curator to exist at all.
- **GPT's "create a Review Set from this note" bridge was rejected** — a user-created collection still gets no Companion (same `assertAdmin` fact) and clears no Ask Companion eligibility either; it routes a learner to the guidance layer's front door and hands them nothing.
- **A Note Detail recommendation/nudge was rejected** — `DashboardStudyPlanSection` already fires the same "adopt/continue an Official set" recommendation at Onboarding, Dashboard, and Collections (three surfaces, not the two GPT assumed); a fourth surface on Note Detail (the one surface meant to stay focused) would be a nag, not a help.
- **What ships instead:** a small, user-initiated "Add to {Review Set}" action on Note Detail — no recommendation, no matching, no coverage claim, just closes a real navigation gap (extracts the already-built `AddImportedDraftsModal` from the bulk-import flow).
- **The no-Review-Set learner's guidance question was folded into the existing, still-open Primary-Review-Set-vs-Study/Exam-Focus roadmap item**, not opened as a new one — that question already commits to Study/Exam Focus as the load-bearing no-Goal fallback.

## Companion Guidance Doctrine — narrowed 2026-07-29, doctrine text ADOPTED in v0.68.0

**A further GPT exchange proposed something bigger:** stop calling it "Ask Companion" (a chatbot the learner has to think to open) and instead make "Companion" NoteLib's cross-cutting learning-guidance *system* — one voice answering "what should I do next" across Dashboard, Review Set, Progress, and Readiness, deterministic wherever possible (ConceptHealth/Progress/schedule), LLM only when the learner explicitly asks for deeper reasoning. Pressure-tested with Opus — **the taxonomy is right, the literal merge is not:**

- **Adopted:** "one learning responsibility per feature" as doctrine (Notes store, Study Pack teaches, Quiz assesses, Flashcards/Memorization strengthen recall, Progress/ConceptHealth measures, Companion guides the next decision) — a genuinely good organizing principle, worth writing into the docs.
- **Rejected: literally merging today's guidance into one "Companion" system/brand.** "Companion" currently names three structurally different things — admin-authored static content, learner-reactive derived guidance (the docs already call this "Coach"), and the LLM chat (explicitly forbidden from using learner performance today) — and unifying the *name* removes the vocabulary keeping them safely apart. The tell: the owner's own example list included Knowledge Impact ("how is my contribution helping learners?") — a contributor question sharing no signal with study guidance, included only because it sounds adjacent in English.
- **Rejected, for now: a backend merge of the guidance resolvers.** 8 independent "what's next" resolvers already exist (Dashboard alone has 3), each often diverging for a documented reason (e.g. Dashboard/collection pacing must stay uncoupled) — sitting on only 2 shared signal primitives underneath. Real, costly refactor; the sought benefit (felt coherence) is mostly a copy problem, cheaper to solve as an authoring doctrine than a backend rewrite.
- **No rename.** `Feature.ASK_COMPANION`, `ask_companion_sessions`, `AnalyticsEventType.ASK_COMPANION_*` stay exactly as shipped.
- **Real blocking dependency found:** a unified "what should I do next" answer requires one canonical answer to "what am I working toward" — which is exactly the still-open Primary-Review-Set-vs-Study/Exam-Focus question. That question now has three dependents (Personalization, the no-Review-Set guidance surface, and this). Recommended as its own release, ahead of any further Companion-system work.
- **If ever scoped:** Phase 0 resolve Primary-vs-Focus → Phase 1 doctrine + copy audit (docs only, no backend) → Phase 2 additive `reason` field on existing resolvers (substrate, no merge) → Phase 3 lightweight one-off "why this?" explainer (needs a new message-unit mechanism — `ask_companion_sessions`'s session-unit quota and DB-CHECK turn cap don't fit a single question) → Phase 4 Personalization (unchanged). Merge the 8 resolvers only if Phase 1 empirically proves the ladder is identical across surfaces.
- **`v0.64.0` is unaffected by this discussion** — ships exactly as already scoped above.
- Full resolution: `docs/product/ROADMAP.md`'s "Companion Guidance Doctrine" Backlog Index row.

**Status update — `v0.68.0` (2026-08-01):** the **doctrine text itself is now adopted**, as a new `### Companion Guidance Doctrine` section in `AGENTS.md` (placed beside the Page Responsibility Rule it extends). Docs/copy only — no rename, no new user-facing brand, no backend or analytics change, exactly as narrowed above. **Everything past the text is still gated:** Phase 1's copy audit and any merge of the 8 existing "what's next" resolvers still require Phase 0 (Primary-vs-Focus) resolved first.

**Caveat if you are asked to apply the doctrine:** `v0.68.0`'s pressure test found **four internal inconsistencies in the doctrine's own text**, logged as a Known Limitation. It conflicts with the Page Responsibility Rule table it claims to extend (that table assigns `Companion` a single governing question, while the doctrine argues "Companion" names three structurally different things — admin-authored static content, learner-reactive derived guidance, and the LLM chat); its "one question per surface" bullet points at a table enumerating *pages*, not guidance surfaces, so a new surface has no row to look up; its "docs/copy only" header scope contradicts its own third bullet instructing you to extend a resolver (a code change); and it says nothing about a new surface landing on a page that already has a grandfathered resolver. Reconciling that text belongs with Phase 1, not with a casual application of the doctrine.

---

## Onboarding exam-date field — proposed for removal, DECIDED AGAINST 2026-08-12

Scoped into `v0.73.0`, never built, then reviewed and closed. **The reasoning is worth keeping because the
wrong conclusion is the intuitive one.** The ask *is* duplicated by the post-session commitment prompt, which
prefills and requires the date — so deleting the onboarding field looks like obvious tidying. It is not: that
prompt renders on session-**completion** screens, so it reaches only learners who finish a session, while
onboarding reaches everyone who reaches Screen 3. Removing it would leave exam-bound learners who never
complete a session with no exam date at all, disabling the board-exam countdown and degrading the exam-date
segmentation the target-habit definition depends on. The field is **optional**, so it was never a second
required question — which had been the entire basis for calling it a violation of "one question per screen".
The claim was reworded instead. **Do not re-propose without new evidence about the non-session-completing
population.**

## Universal onboarding (run it straight after signup) — OUT OF SCOPE, and the stated premise was false

Proposed on the grounds that "onboarding only appeared after email verification because there were LLM costs
during onboarding — that is no longer true." **It is still true.** The generating screen calls
`/notes/generate` and `/notes/{id}/generate`; both call `requireEmailVerified`. Running onboarding
pre-verification would 403 at the exact moment it promises the learner their first Study Pack, and would expose
paid generation to unverified throwaway accounts.

The funnel independently says it is the wrong target: **97.6% of signups verify — verification loses 9
learners, while onboarding loses 132.**

**The live counter-proposal is not dead:** run Screens 1–4 before verification and move the verification ask to
the generating step, so it lands on someone who has chosen a profile, said what they study, picked a path, and
is one click from the payoff — rather than on a stranger who knows nothing yet. That needs no backend change.
Unbuilt, unscoped.
