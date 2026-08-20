# NoteLib — Feature Surfaces

> **Module — not a standalone brief.** Paste `GPT_CONTEXT.md` first; this file assumes it.
> Paste this module when the conversation is about **a specific screen or feature surface, Note Collections, or the Companion**.
> Last updated: v0.89.1 - 2026-08-20 (v0.89.1 is IN PROGRESS, not released — `v0.89.0` is the last released version). **`v0.89.1` makes `users.birth_year` correctable.** It was account-global and write-once, so a learner who declared an adult year — by mistake, or coached by a supporter who wanted the guardian-consent step gone — permanently disabled consent for **every future connection they would ever form**. **⚠️ The load-bearing half is the re-evaluation:** correcting to a younger year pauses every ACCEPTED connection that has no consent record, reverting it to PENDING (never REVOKED — the gate is about consent, not about ending a connection both people agreed to). Consented links are untouched; consent already given is not retracted. **⚠️ Birth year is still collected at LINK TIME only — never at signup, onboarding or profile** — and only the current value plus a last-changed timestamp is stored, with no history, because it is a minor's personal data. **✅ SURFACING DECIDED 2026-08-20 (owner).** **Learning Connections stays a CAPABILITY — never a profile mode, never an opt-in toggle.** **"Quiz for someone" leaves the practice row entirely** — it is a support/share action, not learner practice — and moves to a secondary note action/share surface. **⚠️ Quiz sharing is NOT gated on having a connection: a shared-quiz recipient needs no account and no relationship, so the two are independent.** Accepted connections gate **supporter-specific** surfaces only (People You Support, progress). Learning Connections stays independently discoverable. **Invitations stay one-at-a-time by principle — the quiz LINK is the many-recipient mechanism; a permission-bearing relationship stays deliberate.** Decision recorded in `docs/claude-plans/learning-companion-surfacing-product-ux-consultation-prompt.md`. **Surfaces added by `v0.89.0`:** a `Learning connections` main-nav item and `/linked-learners` page (invite, accept, revoke), a per-connection progress page, a People-you-support Dashboard card, and the `Quiz for someone` button on note detail. **⚠️ A supporter-only account previously had a Dashboard empty by construction; the card exists to fix that.** Previously v0.89.0 - 2026-08-19. **`v0.89.0` shipped Support Another Learner in three phases.** (1) Creating a shareable quiz no longer requires the `TEACHER` profile — **`ProfileType` answers "how do YOU learn?", not "may you help someone?"**, so a parent previously had to misrepresent their own profile, which drives dashboard emphasis, quiz-mode availability and generation behaviour. (2) **Linked learners**: invite + accept in BOTH directions, revocable either side, acceptance mandatory; age captured AT LINK TIME (never at signup) with guardian consent below a configurable threshold whose number is **owner-owned pending counsel**. (3) **The product's FIRST cross-user read** — a supporter with an `ACCEPTED` link sees readiness, progress and quiz performance. **⚠️ THE PRIVACY LINE IS ABSOLUTE: never the learner's notes**, and the projection is counts-only — no concept names, note titles or collection titles. **⚠️ Viewing writes NOTHING** (no `ConceptHealth`, no streak, no study day), so supporter *view* frequency is unmeasurable by design. **⚠️ Account existence is still observable** via the inviter's own list; the fix is email-keyed invitations, and **multi-recipient invites must not ship before it**. **⚠️ `users.birth_year` is account-global and write-once with no correction path.** Owes `[CHECKPOINT — due 2026-09-19]`: zero `ACCEPTED` relationships kills the demand hypothesis. Previously v0.88.0 - 2026-08-19. **`v0.88.0` made Subject Plan sections authorable.** Sections are still DERIVED from the existing per-item `label` — **no table, no new entity, no third collection level** — but a curator can now create one from the Builder's per-note control, and **Bulk Generate takes an optional Section, pre-filled from the batch subject and editable**, so generated notes arrive already grouped. **`Group by subject`** sections a whole plan from the subjects the notes already carry. **⚠️ `Not in a section` is a RESERVED sentinel defined once in `collection-labels.ts`** — every authoring surface refuses it and both reading surfaces fold any casing into the bucket, because a second copy of that string lets a curator mint a section that renders identically. **⚠️ Only the sentinel comparison is case-folded; grouping between real sections stays case-sensitive.** Also: `subject` (64) and `course/program` (120) request bounds now match their columns — user input is REJECTED, **LLM-generated values are CLAMPED**, because rejecting one would discard an already-billed generation. Previously v0.87.0 - 2026-08-18. **The Library bulk failure banner now shows a reason beneath each failed topic** when the receipt recorded one; receipts predating `v0.87.0` render exactly as before, and `Retry these` is unchanged. No other surface changed in `v0.86.0` or `v0.87.0`. Previously v0.85.0 - 2026-08-18. **Stamp had gone thirteen releases stale (v0.72.1) while the CONTENT was kept current** — the `v0.84.0` Explore changes below were written in without restamping, which is the drift this line exists to prevent: a reader trusts the stamp, not the body. Restamped at the `v0.85.0` signoff after re-reading the nav and Explore sections against the code. **No surface changed in `v0.85.0`** — it is a generation-signal release whose only UI change is curator-facing descriptions beside the Domain Context select on Note Editor, Bulk Generate and Note Detail.

---

## Note Collections (Study Plans / Review Sets): Vision & Locked Rules

Profile-aware terminology — "Study Plan" (Student / Board Taker), "Lesson Plan" (Teacher), "Review Set" (Professional) — all the same underlying `NoteCollection` entity, labeled through `getCollectionLabels(profileType)`.

**The vision, in one line:** a Note Collection is not a folder — it is a trackable **readiness journey**, and it is the product's primary retention lever (chosen for this role in v0.33.0 when W1→W2 was ~5.6%: give a learner a number that only moves by returning to practice, and a credible zero-notes on-ramp via curated adoptable plans).

**Locked structure and rules:**
- A top-level **Goal** can contain child **Subject** collections through `parent_collection_id` — exactly two levels, no arbitrary depth, no per-module mastery, cycles impossible.
- **Adoption** (admin-published collections) is free, idempotent, makes no AI call, creates a private snapshot copy — source edits never sync into adopted copies. Recursive Goal adopt copies every child Subject plan and note in one action.
- **Readiness** derives entirely from existing `ConceptHealth`/`ProgressReportService` — no new mastery signal, ever — but is deliberately *not* shown everywhere: dedicated plan-detail/`/progress` surfaces show it, execution rows/list cards/published-plan cards/public source plans deliberately do not (list-level mastery display was tried and rolled back — it created role confusion between "browsing" and "monitoring"). Vocabulary is locked: `ready / mastered / due / not started`.
- **Mastery integrity is protected:** Flashcards and Memorization are locked to never write `ConceptHealth`. Quick Review also writes it today (a deliberate 2026-07-11 change, corrected in `EXAM_MODES.md`/this doc 2026-07-29 after a v0.63.0 pressure test found the "Quick Review never writes it" framing had gone stale) — every quiz-session mode (Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, Board Exam, Interview Practice) can move the readiness number; only the two non-engine review surfaces cannot.
- The **Builder** (`/collections/{id}/builder`) is the single authoring canvas for both Goal and leaf plans — deliberately not a study/monitoring surface, no readiness ring on the Builder itself.
- **Primary Review Set + Weekly Countdown (v0.40.0+):** a nullable `primaryCollectionId` (top-level Goal only) plus optional `target_completion_date` and `studyDaysPerWeek` drive a derived — never stored — weekly countdown (`weeksRemaining`/`conceptsRemaining`/`todaysConceptBudget`). No adaptive/AI scheduling, streaks, or calendar integration — pure read-time derivation from existing readiness + date math.
- **Review Set Detail (v0.41.1)** is composed Identity → Current Journey → Primary Action → Readiness → Guidance (Companion) → Subject Plans/Notes — "what should I do next, in this Review Set," while staying collection-scoped (cross-journey "which set" stays Dashboard's job).

**Intentionally still parked:** standalone adoption of a single child Subject plan (unresolved re-parenting interaction with `adoptGoal`'s idempotency check) — not worth solving without a real discovery need.

---

## Learning Companion: Vision & Locked Rules

**The organizing insight:** Review Centers aren't valuable because they provide PDFs or quizzes — they're valuable because they provide **guidance** (structure, direction, pacing, coaching, confidence). The Companion is NoteLib's guidance layer riding on top of Notes (knowledge) + Study Packs (learning engine) + Review Sets (journey).

**Success criterion:** *"Every Official Review Set should feel like a premium guided learning experience rather than a collection of notes."* Not feature count, not revenue.

**Content model:** a single nullable JSONB column, `note_collections.companion` — 1:1 with a top-level collection only. Five long-form sections (Overview, Study Strategy, Common Mistakes, FAQ, Resources) plus an atomic `mentorTips` array (each tip has its own identity, an optional curator-tagged linked action, and an optional deterministic surfacing condition — never inferred at render time, never adaptive/LLM-driven selection). **No runtime LLM call to serve a Companion** — authored once, served static, zero per-view cost.

**Curation, never generation (locked, clarified not reversed in v0.42.0):** a learner never receives an auto-generated plan or tip. ADMIN-only `Generate Companion` produces a **draft only** — the curator must review, edit, and click Save/Publish, in every path including Mentor Tips. Official-author-only today; FREE for all learners, zero paid uplift on the Companion itself by design.

**Coach vs. Companion, the locked split:**
- **Coach (dynamic).** Reacts to the learner: continue-where-you-left-off, pacing, readiness, due concepts, resolved next action. This is `TodaysFocusCard` — zero new cost, just naming what already existed.
- **Companion (timeless).** Authored, does not react to daily progress. Mindset, expectations, common mistakes, practical advice — reads like mentor advice, not reference material. `CompanionDisplayCard` collapses by default on every viewport behind "View Full Guide."
- **Curriculum.** Subject Plans → Notes → Practice. Unaffected by this split.

**Result-Screen Companion Bridge (v0.55.0):** Quick Review, Challenge Quiz (both branches), and Adaptive Practice result screens show a labeled excerpt of the primary Review Set's Common Mistakes/Study Strategy — curator-published content only, no generation, no mid-exam coaching.

**Ask Companion (v0.63.0, shipped):** PLUS/PRO learners can ask up to 6 questions per conversation against a top-level owned Review Set's renderable Companion content, on a dedicated collection-detail chat panel. Grounded retrieval only (the system prompt refuses unsupported/outside-knowledge questions, never fabricates) — not a departure from "curation, never generation," since the model answers only from already-curator-published text. 20 sessions/month, 6-turn cap, cheapest model tier, reuses the existing per-minute AI rate limit. FREE sees a plan-aware upgrade prompt. **Twice-missed concept → Ask Companion (same release):** a consecutive-incorrect streak on `ConceptHealth` (resets on a correct answer) fires an "ask about this" CTA at streak 2 on Adaptive Practice/Challenge Quiz/Quick Review results, reusing the same Primary Review Set resolver and tradeoff `CompanionResultBridgeCard` already accepted. See the "Open Question This Session" section above — Companion (and both these features) remain strictly Review-Set-scoped; a user with no Review Set gets neither.

**Monetization philosophy (long-term principle, not a repricing of today's plans):** FREE = static guidance (the Companion itself). PLUS = interaction (**shipped v0.63.0** — Ask Companion, grounded Q&A reusing the Interview Practice cost-control template). PRO = personalization (future, gated — genuinely adaptive/learning-pattern-driven guidance selection, explicitly not deterministic rule reordering, which is the FREE-tier precedent). Personalization is the one future tier still not scoped to a version.

**Future, gated, not yet scoped:** AI-generated Review Sets (curator pipeline, effectively closed/ruled out — see Roadmap Candidates in `STRATEGY_AND_ROADMAP_CONTEXT.md`); Personalized/Adaptive guidance (PRO) — gated on the still-open Primary-Review-Set-vs-Study/Exam-Focus philosophy question, a different/separate gate from what Ask Companion needed. See "Roadmap Candidates" in `STRATEGY_AND_ROADMAP_CONTEXT.md`.

---

## Core Feature Surfaces

### Navigation (App Shell)

Three coexisting navigation surfaces, **updated in `v0.67.0`**: **desktop sidebar** (Dashboard / profile-aware Collections label / Library / Explore / Progress), **mobile hamburger drawer** (same, full nav on mobile), **mobile bottom tab bar** (persistent 4-tab subset — Dashboard, Library, the Collections label, Explore — icon+text, below the `md` breakpoint, auto-hides during exam focus/active assessment). "Public Library" is no longer a standalone nav item on either surface — `/explore` composites it with the Official Review Set catalog behind a segmented control (see Current Baseline / Open Question above); `/public/library` and `/collections/published` both remain live, unchanged routes, just no longer directly nav-anchored. Progress stays off the mobile tab bar, deliberately not a 5th tab there. Do not add a 5th mobile tab or expand the tab bar's scope without checking `RELEASES.md` v0.50.0's anti-drift notes first.

### Landing / Public

- Marketing positioning is notes-library-first: notes -> summaries -> quizzes -> review.
- Public nav exposes Home, **Explore**, Learn, Pricing, Login, Get Started. **⚠️ Changed in `v0.84.0`: the nav names Explore, not Public Library**, and **`/explore` itself is now anonymous** (it redirected signed-out visitors to `/login` before that). Both `/explore` and `/public/library` are accessible without login; `/public/library` keeps its canonical route and only lost navigation primacy.
- Public legal routes: `/privacy`, `/terms`. Contact email: `support@mail.notelib.app`.
- Branding uses the NL monogram for navbar/app shell/favicon and full logo for marketing headers/footers.

### Library and Notes

- Library is the authenticated note workspace. Notes can be private or public.
- Note creation must respect profile setup, target audience defaults, and Study Pack usage rules.
- **Terminology, locked in `v0.68.0` — get this right, it is the most recently-enforced naming rule in the product.** Drafting a note from a bare topic prompt is **"Create a Note"** (never "Generate Note"), and its metered monthly allowance is a **"topic note"** in all user-facing copy. **"Generate" is reserved for operations that transform the learner's own material** — "Generate Study Pack", "Generate Quiz", "Regenerate", and the `Retry Generation` failure label all keep it deliberately. Internal names are unchanged and should stay that way: `noteGenRemaining`, `noteGenerationsRemaining`, the `note-generation-limit` CTA context, and the `GENERATE_NOTE_LIMIT` / `GENERATE_NOTE` analytics identifiers. "Note draft" is **not** available as a synonym for the allowance — `Draft` is already a user-visible state meaning "no Study Pack yet", and a hand-written note is also a Draft while consuming zero topic-note quota.
- Async generation saves the note first, marks it `GENERATING`, redirects to Note Detail, and lets Note Detail poll. Failed generation preserves note content and exposes `Retry Generation`.
- Note Detail is the owner study hub: summary, key concepts, quiz, full notes, practice actions, recent sessions, readiness signal, Flashcards/Memorization entry points. Key Concepts entries sort by readiness (struggling → due → not-started → mastered) once ConceptHealth loads.
- Quiz result screens carry two authored/derived guidance surfaces: a `CompanionResultBridgeCard` excerpting the primary Review Set's Companion content, and deep-links from missed/weak concepts to their matching Key Concepts explanation. Both are same-session learning aids, not retention/return mechanics.

### Public Notes and Profiles

- Public note detail is read-only and separate from private Note Detail.
- Public note actions copy/create private owned notes first; private study actions never run against a public source note.
- Public Profile is `/public/creator/{username}` canonical, `/public/profile/{userId}` legacy-compatible.
- Profile Settings (`/profile`) is private editing; Public Profile owns visibility and sharing.

### Note Collections (Study Plans / Review Sets)

See the dedicated vision section above. Quick reference: a collection is a top-level **Goal** or a **Subject** (child of a Goal, or standalone) — exactly two levels. Published/admin collections are source plans; adoption creates owned snapshot copies. Recommended plans surface course/program-scoped on Dashboard and `/collections`; `/collections/published` is the full browse surface. The Builder is the single authoring canvas. Plan detail execution rows show action/status, not mastery — dedicated readiness detail lives at `/progress?collectionId={id}`. Top-level Goal detail renders `TodaysFocusCard` (Coach) → Progress (readiness + countdown) → collapsed `CompanionDisplayCard` ("View Full Guide").

### Progress and Readiness

- `/progress` is available to all plans, the canonical subject-level detail surface, including plan-scoped readiness via `?collectionId={id}`. Reads ConceptHealth only.
- Subjects group by Study Pack subject; blank/null subject is `Other`.
- Classification: mastered (recent correct signal), due (stale correct signal), not started (no correct signal), struggling (latest incorrect newer than correct).
- Goal milestones are fixed read-time checkpoints, not persisted. Note and plan readiness reuse this spine.

### Settings, Account, and Email

- Settings order: Preferences, Plan & Billing, Account. Preferences include Learning Style (`engagementMode`) and Study Reminders.
- Account deletion is soft-delete first, purge later. Data export is owner-only, excludes secrets/analytics/billing.

### Admin

- Admin Dashboard is internal, read-only v1, ADMIN-only — overview, billing, engagement, public-content growth, recent upgrades, failed payments, feedback.
- Feedback submissions persist `message`, authenticated `userId`, `email`, and current page URL.

---
