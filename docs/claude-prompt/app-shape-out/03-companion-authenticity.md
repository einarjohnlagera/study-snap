# Fable Output — Learning Companion Content Authenticity

> Raw output from running `docs/claude-prompt/app-shape-prompts/03-companion-authenticity.txt` through Fable. Unedited except for this header.
>
> **Correction (2026-07-12, Claude Code, verified against code):** mechanism 0 below states the
> provenance link is "the one piece that's genuinely new infrastructure" and gates the whole
> evidence-panel feature on a go/no-go for building it. Checked directly: `NoteEntity.java` already
> has `copiedFromNoteId` and `copiedFromUserId` columns (`backend/src/main/java/com/studysnap/backend/entity/NoteEntity.java:62-66`)
> — note-level copy lineage already exists and needs no new field or migration. What's actually
> missing is a *reverse* lookup (note → which collections contain it) — `NoteCollectionItemRepository`
> only supports the forward direction today (collection → its notes; checked, no `findByNoteId`
> variant exists). `StudyPackEntity` has no separate `copiedFromStudyPackId`, but since
> `StudyPackEntity.noteId` is 1:1 with its note, pack lineage is derivable by joining through the
> note's existing `copiedFromNoteId` chain — no new field needed there either. **Net effect: this
> prerequisite is a new repository query, not new infrastructure** — the go/no-go this session
> recommends is real but much cheaper than mechanism 0 implies. See the "Recommended next step" in
> `app-shape-session-plan.md` for the corrected framing.

## Mechanisms

### 0. The prerequisite everything below depends on: adoption provenance

Before any cross-learner aggregation is possible, there is a structural gap worth naming: adoption **snapshot-copies** public notes and linked Study Packs into learner-owned rows (`my-progress.md` line 60), and all ConceptHealth and quiz-session data is written against those *copies*. The Official Review Set itself accumulates almost no learner signal directly. So the genuinely new primitive is a **provenance link** (`copiedFromNoteId` / `copiedFromStudyPackId`, or equivalent lineage table) that lets an aggregation job answer "which owned packs descend from this Official Review Set's packs?" Everything in `analytics.md` and `my-progress.md` is per-event or per-user; this lineage index is the one piece that is genuinely new rather than an extension. If a source id already exists somewhere in the copy path, this drops to a rollup query; if not, it is the first thing to build, and it is forward-looking only (existing adopted copies without the link stay dark).

### 1. Aggregate ConceptHealth "Struggle Map" in the authoring modal — **Core Feature**

- **What data:** For each `keyConcept` in the Review Set's packs (via lineage), counts of learners currently *struggling* / *due* / *mastered* / *not practiced*, using the exact state definitions already locked in `my-progress.md` lines 86–90 — do not invent a parallel mastery definition. Rendered as a ranked list: "Pharmacokinetics: 41% of practicing learners struggling (n=63)".
- **Where it comes from:** Existing `concept_health` rows joined through lineage to the official packs. A nightly admin-side rollup (materialized per official collection), not a live fan-out query — authoring is rare, so even the rollup can be computed on-demand with caching.
- **How it's shown:** A read-only "Evidence" side panel inside the existing ADMIN-only `Manage Companion` modal. No learner-facing surface at all.
- **New vs. extension:** Extension of ConceptHealth's existing per-user model into a cross-user aggregate; the aggregate endpoint (`GET /admin/collections/{id}/companion/evidence` or similar) and rollup are new. The due-threshold stays owned by `ConceptHealthService` per the existing rule.
- **Privacy rule (non-negotiable):** minimum-n suppression — no concept stat shown below a threshold (e.g. 5 distinct learners), counts only, never learner identities.

### 2. Most-missed-question digest with distractor analysis — **Core Feature (backend event) + Future Enhancement (retro-mining)**

- **What data:** Per official pack question: attempt count, miss rate, and the most-picked wrong choice. The most-picked distractor is the single highest-signal input for Common Mistakes — it tells the curator *what learners actually believe instead*, which is exactly what the generic screenshot text lacks.
- **Where it comes from:** Two paths. **Cheap/forward:** a new backend-owned analytics event (e.g. `QUIZ_QUESTION_ANSWERED` in the `AnalyticsEventType` enum, per the existing "add to enum first" rule) with `metadata: {questionHash, keyConcept, correct, selectedChoiceIndex}` — fits `analytics_events` exactly as designed (fire-and-forget, after-commit, `analyticsTaskExecutor`), and aggregation is a plain `event_type + metadata` rollup. **Expensive/retro:** mining `sessionState` JSONB from completed sessions — possible since packs are snapshot copies (question text is identical across copies, so a content hash matches), but `QuizSessionStateUtils` owns that JSON and a bulk-scan path through it is fragile; classify retro-mining as Future Enhancement and do not block on it.
- **New vs. extension:** The event is a pure extension of `analytics.md`'s model. The per-question aggregate view is new.

### 3. Feedback and issue-report digest scoped to the Review Set — **Polish**

- **What data:** `feedback` rows whose `page_url` resolves to a note/pack/session in the Review Set's lineage — especially the inline quiz-review reports ("Report Question", "Confusing Explanation", "Something is wrong"), which are already the closest thing NoteLib has to verbatim learner confusion. Shown as anonymized excerpts (strip `email` and `user_id` before display) in the same Evidence panel, grouped by note.
- **Where it comes from:** The existing `feedback` table; the only work is `page_url` parsing plus lineage resolution. Zero new capture. One small capture improvement worth doing as part of this: have the inline quiz-review report actions attach a structured entity id (note/question) instead of relying on URL parsing — a one-field extension, still `Polish`.
- **New vs. extension:** Almost entirely an extension of `feedback-system.md`; the data is thin but it is *real*, and "three learners flagged this explanation as confusing" is exactly the kind of sentence the current Companion can never say.

### 4. Subject-level pacing/drop-off signal — **Future Enhancement**

- **What data:** Per child Subject Plan: how many adopters started sessions, completed them, and where activity stops — built from existing `*_STARTED` events plus session-completion timestamps on lineage packs. Feeds Study Strategy ("most learners stall at Subject 3 — the current draft's 'begin with fundamentals' advice could instead warn about the actual cliff") and Mentor Tips (a `DAYS_BEFORE_TARGET_DATE` tip written knowing where the real mid-plan slump is).
- **New vs. extension:** Events exist; the funnel-per-collection rollup is new. Lower precision than mechanisms 1–2, so sequence it last.

### 5. Evidence snapshot + staleness signal — **Polish**

When the curator saves via `PUT /collections/{id}/companion`, also stamp a lightweight hash/date of the evidence rollup that was displayed — mirroring the already-shipped `companionStructureSnapshot` pattern exactly. This enables a second ADMIN-only flag alongside `companionMayBeOutdated`: "the struggle data has shifted materially since this Companion was written." It is a *signal to a human*, never a trigger for regeneration, and like the structure snapshot it is never copied on adopt.

## AI-Assist Prompt Changes

The v0.42.0 flow (`POST /collections/{id}/companion/generate`, PREMIUM tier, never persists, Save is the only write path) stays byte-for-byte the same in its lifecycle. The change is entirely in **what context the draft prompt receives** and **what the instructions permit**:

1. **Inject a structured evidence block.** The generate endpoint fetches the same evidence payload the panel shows (mechanisms 1–4) and appends it to the developer prompt as a JSON block: top-N struggling concepts with learner counts, most-missed questions with their dominant distractor, anonymized feedback excerpts, subject drop-off summary. This is authoring-time only — one extra query at generation, zero read/render cost, fully consistent with "authored once, served static."

2. **Section-specific grounding rules:**
   - **Common Mistakes:** "Every mistake you list must trace to an item in the evidence block (a struggling concept, a dominant distractor, or a feedback report). Name the concept exactly as it appears in `keyConcepts`. If evidence is present, do not add mistakes from general knowledge; if the evidence block is empty or below threshold, say the draft is based on note content only." The distractor data is what turns "rushing through foundational concepts can create confusion" into "learners who miss the loop-diuretic questions almost always pick the potassium-sparing option — draft a mistake entry about that specific confusion."
   - **FAQ:** Seed candidate questions from feedback excerpts and confusing-explanation reports before falling back to inferred questions; mark which drafts are evidence-backed so the curator can prioritize.
   - **Mentor Tips:** Draft title/body informed by drop-off and due-concept patterns (e.g. a tip aimed at the real mid-plan stall point) — but `linkedAction` and `surfacingCondition` remain curator-set in the modal, never model-selected, exactly as documented in `companion.md`.
   - **Study Strategy:** Allow the model to propose sequencing informed by aggregate subject difficulty rather than the generic "start with fundamentals."
   - **Overview:** little change; **Resources stays manual-only** (locked — generated URLs untrustworthy).

3. **Anti-fabrication instruction:** the model may reference evidence qualitatively ("many learners confuse X with Y") but must never emit invented statistics or learner counts into the draft prose — numbers live in the Evidence panel for the curator's eyes, not in published learner-facing text, so a stale count never gets frozen into static content.

4. **Make the review gate *meaningful*, not just present:** render the Evidence panel side-by-side with the generated draft in the modal, so the curator can verify each claimed mistake against the data the model saw. The human gate today can only check plausibility; with the panel it checks *truth*. Nothing about the gate itself changes — generation still never writes `note_collections.companion`, and Save remains the only write path.

## Out of Scope

- **Per-learner runtime personalization keeps suggesting itself — reject it every time.** "Show *this* learner's own weak concepts inside Common Mistakes," "reorder FAQ by the viewer's struggle profile," "swap Mentor Tips based on the reader's ConceptHealth" — all of these are read/render-time personalization, which is exactly the already-designed PRO Personalized/Adaptive gate. The aggregate evidence panel is the FREE-tier answer: the *published* text is better because the *author* saw real data, and it is still identical for every reader.
- **New data-driven `surfacingCondition` types** (e.g. `WHEN_LEARNER_STRUGGLING_ON_CONCEPT`) — this is runtime personalization wearing a Mentor Tip costume. The existing condition types (target-date proximity, subjects completed) are progress-mechanical, not profile-adaptive; keep it that way.
- **Auto-refreshing or auto-republishing Companion when evidence shifts** — violates curation-never-generation. The correct shape is mechanism 5's staleness *flag*, modeled on `companionMayBeOutdated`: a signal to a human, never a write.
- **Exposing learner identities or raw feedback with PII to the model or the panel without scrubbing** — `feedback` rows carry `email`; the evidence pipeline must strip identity and enforce minimum-n suppression on aggregates before anything reaches the curator UI or the prompt.
- **Touching quiz generation or the five-mode contract** to "fix" most-missed questions from the Companion side — question-quality remediation is a separate concern; this session only *reads* miss data as authoring evidence.
- **Bulk retro-mining `sessionState` JSONB as the primary miss-data source** — bypassing `QuizSessionStateUtils` semantics at scale is fragile and explicitly against the session-model rule; ship the forward-looking analytics event and accept that history starts at zero.
- **Any pricing/tier motion** — nothing here gates, upsells, or moves PLUS/PRO scope; the entire proposal is admin-side authoring infrastructure for the free static layer.
