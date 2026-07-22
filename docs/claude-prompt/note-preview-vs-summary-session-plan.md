# Session Plan — Note Preview vs. Study Pack Summary Card Content Strategy

> **Purpose.** The user flagged two related-but-distinct observations from screenshots: (1) the public
> note detail page has two "related notes" sections built six weeks apart that render differently for no
> real reason, and (2) more fundamentally, whether showing raw note-preview text on cards (Public
> Library, private Library, both related-notes sections) still makes sense now that a growing share of
> notes are AI-authored via `Generate Note` rather than user-written. This is a content-strategy planning
> session — not implementation, not a redesign of anything beyond what's asked.

## Where this stands right now

Nothing has changed in code. Two concrete, verified facts anchor this session:

- `frontend/app/public/library/[subject]/[slug]/page.tsx`'s `More {courseProgram} notes` section (shipped
  2026-06-02, commit `ea5a7887`) is a bespoke inline card showing title + subject + `summaryPreview` only.
  Its `More in {subject}` section (shipped 2026-07-14, commit `616cdf7c`) reuses `SharedNoteCard`
  (`frontend/components/notes/shared-note-card.tsx`), which shows both `contentPreview` ("NOTE PREVIEW")
  and `summaryPreview` ("SUMMARY PREVIEW") plus tags — a richer component that didn't exist in its current
  form when the first section shipped. This is shipping drift, not a deliberate design difference.
- `SharedNoteCard` with both previews is also what renders private Library cards
  (`frontend/app/library/page.tsx`) and Public Library's "More in {Subject}" grid — so this same
  dual-preview pattern is the default across most of the app's note-card surfaces already.

## The real question underneath both observations

`docs/features/public-library.md` has a standing, explicit rule: **"prioritize original note preview
over generated summary when scanning cards."** That rule assumed a note reliably meant a human wrote it.
That assumption no longer holds uniformly — see below.

## What already exists — given to Fable as ground truth, don't rediscover it

- **Three note origins exist today, verified in `docs/features/notes.md`:** manually written, AI
  **`Generate Note`** from a topic (a shipped, plan-gated feature — separate from Study Pack generation
  and OCR — that drafts the note's own body content, not just a derived summary), and OCR/file import.
  `Generate Note`'s output structure is `Overview`, `Core Concepts`, `Key Details`, optional `Examples`.
- **No persisted field distinguishes note origin.** `NoteEntity`
  (`backend/src/main/java/com/studysnap/backend/entity/NoteEntity.java`) has no creation-source column.
  A generic `NOTE_CREATED` analytics event exists but doesn't capture which of the three creation paths
  was used in a queryable way. **Any origin-conditional card treatment needs new instrumentation first —
  this is a real cost, not a free choice.** There is also no existing measurement of what fraction of
  notes are AI-generated vs. written vs. imported; that split is genuinely unknown.
- **Public Library and public note detail are explicitly documented as acquisition/trust surfaces**, not
  just app screens (`docs/features/public-library.md`, "Public Note Detail" section): "teach first,
  convert second," and the recommended page structure's step 7 CTA is literally *"Turn your own notes
  into something like this"* — a phrase that assumes the visitor has (or will have) their own
  human-authored notes, in tension with a product that increasingly writes the note for them.
- **A separate, locked SEO rule** (`docs/features/seo.md`) already forbids "generic AI-tool" positioning
  and requires notes-library-first messaging — this is about marketing copy/metadata, not card content,
  but the same underlying identity tension applies: leaning into "we wrote this" content-side risks
  friction with that positioning.
- **"Curation, never generation" is a separate, unrelated locked rule** (see `docs/product/ROADMAP.md`)
  about Review Set / Study Plan *auto-assembly* — it does not govern individual note authorship and is
  not implicated by `Generate Note` (already shipped, already sanctioned). Do not confuse the two.
- **Featured Notes ranking** (`docs/features/public-library.md`) already uses "note preview is not empty"
  as an eligibility signal for the Featured section — so raw note content is load-bearing beyond card
  display today, in ranking too.

## Hard constraints (Fable starts cold, repeat these)

1. Do not propose anything that touches "Curation, never generation" (Review Set / Study Plan assembly)
   — unrelated, out of scope, already locked.
2. Do not propose changing note visibility rules, ownership, or the copy/adopt model.
3. Do not propose retiring or gating `Generate Note` itself — it's a shipped, sanctioned feature; the
   question is what to *show on cards*, not whether the feature should exist.
4. Any origin-conditional recommendation must explicitly account for the fact that origin isn't tracked
   today — say plainly whether the instrumentation cost is worth it now, or whether a uniform (non-
   conditional) treatment is the better near-term call given that gap.
5. Respect the existing acquisition-surface framing ("teach first, convert second") for the public note
   detail page and Public Library — don't propose a redesign of the funnel itself, only the card-content
   question.
6. Keep the scope to card content strategy across these four surfaces: public note detail's two
   related-notes sections, Public Library's card grids, and private Library's card grid. Don't redesign
   the pages themselves beyond what card content requires.

## Prompt

Full paste-ready prompt: `note-preview-vs-summary-prompts/01-card-content-strategy.txt`

## Output

`docs/claude-prompt/note-preview-vs-summary-out/01-card-content-strategy.md` (once run)

## Status

Run 2026-07-16 via the `fable` model. Verdict: the standing "prioritize note preview" rule is right for
the wrong reason — its human-authorship rationale is broken, but a separate "the note is the source
object, cards preview the destination" rationale still holds and doesn't depend on authorship.
Recommendation: one uniform rule across all four surfaces — single excerpt per card (note preview first,
labeled "Summary" fallback if the note body is empty/too short, never both), migrate the bespoke
"More {Course/Program} notes" card to the shared component with identical content to "More in {Subject}"
(differ only in query/count, not template). Phase 2 (later, separate decision): build creation-time
`origin` tracking (MANUAL/AI_GENERATED/IMPORTED) for measurement and a future Featured-eligibility
decision — explicitly NOT for origin-conditional card rendering, which is rejected even with the data.
Full reasoning in `note-preview-vs-summary-out/01-card-content-strategy.md`.
