# Session Plan — Public Note Copy/Quiz Flow + Related-Notes Link Consistency

> **Purpose.** Two unrelated-but-adjacent findings from direct user testing of the public note detail
> page, bundled into one session since both live on the same page and were reported together. Part A is
> a real architecture/product question (should "Quiz yourself on this note" regenerate the whole Study
> Pack, or reuse the copy-as-is path that already exists). Part B is a smaller UX-consistency question
> (mobile link wrapping, desktop grid-column mismatch) the user explicitly wants a second opinion on
> alongside Part A, not because it's architecturally hard but because they want outside taste on wording.
> Planning only — no code changes from this session.

## Part A — "Quiz yourself on this note" copy/generation flow

### What's already confirmed, given to Fable as ground truth

- **A real, reproducible race-condition bug exists today**, independent of whatever Part A concludes.
  `frontend/components/notes/private-note-detail-page-client.tsx` has two separate `useEffect`s that both
  react to a note's Study Pack becoming ready: one (`~line 1352`) starts a Quick Review session and
  navigates away *synchronously*; the other (`maybeShowGeneratedMetadataSuggestion`, `~line 440`) opens an
  AI-suggested-metadata modal, but only after an `await getMyStudyPack(...)` network round-trip. The
  quick-review effect has no async gap and wins the race every time a fresh generation was triggered,
  so the modal never gets a chance to render before the page navigates away. This will be fixed as part
  of whatever ships from this session, regardless of Part A's product answer.
- **The backend already does copy-as-is for free, in the common case.** `NoteService.copyNote()`
  (`backend/src/main/java/com/studysnap/backend/service/NoteService.java:182-245`), when
  `includeStudyPack=true` and the source note has a ready Study Pack, deep-copies the summary, key
  concepts, and quiz **synchronously, no LLM call**, and returns `studyPackStatus: "STUDY_PACK_READY"`
  immediately. The frontend (`public-seo-copy-cta.tsx`) checks this and skips forcing regeneration when
  it's true. So the *only* time a fresh generation is triggered today is a fallback case — the source
  note somehow lacking a ready Study Pack at copy time.
- **Quick Review questions are a fixed list baked in at Study Pack generation time, not synthesized
  per-session.** `QuickReviewSessionService.startSession()` reads `studyPack.getQuiz()` directly. There is
  **no existing capability to regenerate "just the quiz" independent of the whole Study Pack** for a
  first-time session.
- **One real precedent for quiz-only regeneration exists, but doesn't fit this use case.**
  `QuickReviewAdaptivePracticeService.generateAdaptiveQuiz()` does call an LLM to generate new quiz
  questions without touching summary/key concepts — but it's built to target *weak concepts from a prior
  completed session*, and requires that prior session to exist. A first-time copier has no session
  history, so this mechanism can't be reused as-is for "personalize on first quiz."
- **Locked product rules already on record** (`CLAUDE.md`): "Public-note copies include the linked
  StudyPack when one exists (intentional, documented exception)" and "The Study Pack is the generated
  version of a note. Never auto-regenerate. Regeneration always requires explicit user confirmation."
  Both already point toward copy-as-is being the intended default, not silent regeneration.

### The question actually being asked

The user's own framing: "when a user copies a study pack, can we just regenerate the quick review instead
of the whole study pack? Regenerating the study pack just doesn't make sense — the reason they copied
that note is because they want that note; regenerating and modifying the original seems like it doesn't
make sense for the copier." They also floated a possible upside worth weighing honestly, not dismissing:
"it tailors the quick review on the learner level of the copier... although it will cost us upfront."

A pre-formed direct opinion is already on record (to give Fable something to agree or push back on, not
to bias it toward agreement): the copy-as-is path already works and is free; the right fix is to stop
forcing regeneration for this CTA entirely, skip the AI-suggestion modal (nothing was regenerated, so
there's nothing to reconcile — title/subject/tags already carry over verbatim, same as every other copy
CTA in the app), and go straight from copy to Quick Review against the copied, unmodified Study Pack.

## Part B — Related-notes section link wording and grid consistency

### What's already confirmed

- Public note detail has two related-notes sections, both migrated to a shared card component in v0.50.2:
  "More {Course/Program} notes" (links out as `See all in {Course/Program} →`, or `Browse {Hub} hub →`
  when the course/program maps to a board-exam hub — deliberately different wording for a deliberately
  different, more curated destination) and "More in {Subject}" (links out as `See all in {Subject} →`).
- On mobile, `See all in {Course/Program} →` / `See all in {Subject} →` wraps to two lines for longer
  labels (e.g. "See all in Accountancy", "See all in Pediatric Nursing") — reported directly from a user
  screenshot as looking broken.
- On desktop, the two sections' cards already look identical (v0.50.2), but the **grid column count**
  differs: "More {Course/Program} notes" uses a fixed 2-column grid (`sm:grid-cols-2`, holds up to 4
  cards); "More in {Subject}" uses up to 3 columns (`sm:grid-cols-2 lg:grid-cols-3`, holds up to 3 cards).
  The user wants both sections to use the course/program section's 2-column layout.

## Hard constraints (Fable starts cold, repeat these)

1. Do not propose changing the Exam Hub distinction — `Browse {Hub} hub →` must stay visually/textually
   different from whatever the generic "see all" wording becomes, since it points to a genuinely
   different, more curated destination than the filtered Public Library view.
2. Do not propose new quiz modes or touching the locked 5-mode `EXAM_MODES.md` contract.
3. Do not propose changes to note visibility, ownership, or the copy/adopt data model beyond what Part
   A's recommendation requires.
4. Any regeneration mechanism proposed for Part A must explicitly account for the real cost (LLM call,
   time-to-first-quiz delay) — don't wave the cost away just because the personalization idea sounds
   appealing.
5. Part B is a wording/layout decision only — don't propose restructuring the two related-notes sections
   beyond link text and grid column count.

## Prompt

Full paste-ready prompt: `public-note-copy-and-related-links-prompts/01-copy-flow-and-link-consistency.txt`

## Output

`docs/claude-prompt/public-note-copy-and-related-links-out/01-copy-flow-and-link-consistency.md` (once run)

## Status

Run 2026-07-17 via the `fable` model. Verdicts:
- **Part A:** copy-as-is (skip regeneration entirely) is the right default for "Quiz yourself on this
  note" — speed-to-first-question and fidelity to the curated source both outrank personalization at this
  specific moment, and personalization is already covered by the existing (non-blocking) copied-pack
  regenerate hint, so no new mechanism should be built. The rare fallback (source lacks a ready Study
  Pack) should gate the CTA server-side rather than silently auto-generate. The AI-suggestion modal should
  be skipped entirely on this flow — nothing gets regenerated, so there's nothing to reconcile. The
  race-condition bug gets fixed as an ordering guarantee regardless.
- **Part B:** both related-notes links become "See all →" (sentence case, same everywhere, with a full
  `aria-label` for accessibility) — rejects "See More"/"View More" as weaker/more ambiguous than "all."
  `Browse {Hub} hub →` stays untouched (different destination class, deliberately distinct). The subject
  section's grid collapses from 3 columns to 2, matching the course/program section — same shared card,
  same page, no reason for two column counts.

Full reasoning in `public-note-copy-and-related-links-out/01-copy-flow-and-link-consistency.md`.
