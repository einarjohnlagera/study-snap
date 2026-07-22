# Planning Analysis: Public Note "Quiz Yourself" Flow (Part A) and Related-Notes Links/Grid (Part B)

Run 2026-07-17 via the `fable` model. Prompt: `../public-note-copy-and-related-links-prompts/01-copy-flow-and-link-consistency.txt`.
Session plan: `../public-note-copy-and-related-links-session-plan.md`.

Grounding files referenced throughout:
- `frontend/lib/public-note-copy.ts` — copy-intent redirect builder (`generate=1` + `startQuickReview=1` params, with an existing `skipGenerate` option)
- `frontend/components/notes/private-note-detail-page-client.tsx` — the racing effects (`startQuickReview` handler ~line 1353, `notelib-awaiting-suggestion` sessionStorage flag ~lines 441–487, `AiSuggestionModal`)
- `frontend/app/public/library/[subject]/[slug]/page.tsx` — both related-notes sections (lines 334–376 course/program, 378–420 subject)

---

## PART A — "Quiz yourself on this note"

### A1. Is "copy as-is, skip regeneration" the right default? Yes — and here is the mechanism.

The person tapping "Quiz yourself on this note" is at the end of a specific funnel: they found a public note, read enough of it to decide *this material* is worth practicing, and tapped a button whose promise is a quiz **on this note**. In the first ten seconds after the tap, they want two things, in order:

1. **Speed to the first question.** This is an activation moment — often a brand-new account created seconds earlier via the auth-gated CTA. Every second of "Generating your Study Pack…" between signup and first question is churn surface, and this audience is on Philippine mobile connections where an LLM round-trip plus polling is not a 2-second wait.
2. **Fidelity to what they just read.** They chose this note *because of* the author's curation. A regeneration that quietly rewrites the summary and key concepts breaks the implicit contract of the button — the quiz they get is no longer on the artifact they evaluated and chose. The user's framing is correct: silent regeneration *weakens* the value of copy, it doesn't add to it.

Personalization is genuinely third on this list, and it is the only thing regeneration buys. So the ordering of wants directly implies the default: **copy the source Study Pack verbatim (the existing synchronous, zero-LLM deep-copy path), start Quick Review against it immediately.**

Two locked product rules independently confirm this isn't a judgment call being made fresh here:
- Public-note copies are *documented* to include the linked Study Pack when one exists. Regenerating on the very next tap makes that documented behavior pointless — you copied the pack only to overwrite it.
- Study Packs *never* auto-regenerate; regeneration requires explicit user confirmation. Tapping "Quiz yourself" is not consent to regenerate — the button says nothing about generation. The current fallback behavior is arguably already a violation of this rule in spirit.

Mechanically, this is small: the flow already passes through `buildCopiedNotePath(noteId, "quick-review")` in `lib/public-note-copy.ts`, which already supports `skipGenerate`. The recommendation is to make skip-generate the *only* behavior for this CTA whenever the copy came back with a ready pack (which the frontend already detects), rather than a conditional optimization.

### A2. The personalization counter-argument, taken seriously.

Is there real value in a Quick Review quiz calibrated to the copier's learner level rather than the author's? **Yes, some — but it is second-session value, and the moment under discussion is a first-session moment.** Three reasons the value is real but small *right here*:

- Quick Review is the entry-level mode by design — short, low-stakes. Level miscalibration hurts most in Challenge/Adaptive/Board Exam contexts, which the copier reaches later, after they own the note and can regenerate deliberately.
- The copier has zero performance signal yet. A level-recalibrated regeneration is personalizing on a profile field, not on evidence. The product's *actual* personalization machinery (weak-concept-targeted follow-up quizzes) requires a completed session as input — meaning the fastest route to genuinely personalized questions is to **complete the un-personalized copied quiz first**. Blocking the first quiz to personalize it delays the very signal that real personalization needs.
- The cost is concrete and lands on the worst possible user: a full LLM generation (real money, per constraint 4) plus a multi-second polling wait, paid by a brand-new user before they've seen a single question.

**Is the right capture mechanism a blocking regeneration? No. Is a separate, optional, post-copy action right? Yes — and it already exists.** `private-note-detail-page-client.tsx` already ships `COPIED_STUDY_PACK_REGENERATE_HINT_MESSAGE`: *"This Study Pack was copied. If the difficulty doesn't match your level, regenerate it to get a version tailored to you."* (line 122). That is precisely the non-blocking personalization affordance the counter-argument calls for — visible when the user lands back on their copied note, explicit-confirmation-compliant, and paid for only by users who actually feel the mismatch. **No new mechanism should be built.** The recommendation to the engineer is: rely on the existing hint; optionally, verify it also surfaces on the post-quiz return path so a copier who just finished a too-easy/too-hard quiz sees it at the moment the mismatch is freshest. Nothing more.

### A3. The rare fallback case (source note has no ready Study Pack at copy time).

Recommendation, in two layers:

1. **Prevent it at the CTA, server-side.** The public note page is server-rendered with the note's data; when the source note lacks a ready Study Pack, don't render "Quiz yourself on this note" as a quiz-launching CTA at all. Render the copy action alone (or the CTA in a disabled/explanatory state — "Practice quiz available once this note's Study Pack is ready"). A visitor should not be promised a quiz the source can't deliver. This makes the fallback a race-window case (pack deleted/failed between page load and tap) rather than a normal path.
2. **If the fallback is still reached:** copy the note (the user keeps the content — never discard the copy), land on the copied note's detail page in the normal not-yet-generated state with the standard explicit **Generate Study Pack** CTA and a short notice ("This note doesn't have a Study Pack yet — generate one to start quizzing"). **Do not silently auto-generate.** This honors the explicit-confirmation rule, reuses the completely standard owned-note generation flow (zero new UI), and converts a confusing silent wait into a comprehensible one-tap choice. The user loses a few seconds in an already-rare path; the product gains rule consistency.

Explicitly rejected for this case: silently regenerating with the race bug fixed (still violates the no-auto-generation rule and still makes a new user pay an unexplained LLM wait), and hard-failing the copy ("this note isn't ready" with no copy) — the note content itself is still valuable and the copy should succeed regardless of pack state.

### A4. The AI-suggested-metadata modal on this flow.

**It has no job here — skip it entirely on the copy-and-quiz path.** The modal exists to reconcile AI-suggested title/subject/tags against metadata *the user typed as a first guess* on their own fresh note. On a public-note copy, the metadata is the original author's curated, published metadata, carried over verbatim — the exact same behavior as every other copy action in the product. There are no AI suggestions (nothing was generated) and no first-guess metadata to improve. Showing the modal would present the user with a reconciliation between two identical or non-existent things.

For the A3 fallback: once the fallback becomes "land on note detail + explicit Generate tap" (per above), the generation that follows is the *standard* owned-note generation flow, and the modal appears there as it normally would. That's acceptable — no special-casing needed — with one honest caveat the human reviewer should weigh: on a copied note the metadata is author-curated rather than first-guess, so the modal's value is lower than in its native context. Leave the standard flow untouched (consistency, zero extra scope) rather than add a "suppress modal if note was copied" branch, which is a special case with ongoing maintenance cost for a rare path.

**The race-condition fix ships regardless**, but note its blast radius shrinks: with copy-as-is as the mandatory default, `startQuickReview=1` and the `notelib-awaiting-suggestion` flag should never coexist for this CTA. The race fix still matters for any other path where generation-completion triggers both navigation and the modal fetch (the two effects at ~lines 441–487 and ~1353 of `private-note-detail-page-client.tsx`), and should be fixed as ordering (modal-eligibility resolved before any auto-navigation), not as a timing patch.

### Part A — explicit rejections

- **Blocking, level-calibrated regeneration before the first question** (generalizing today's fallback). Rejected: pays a real LLM cost and a multi-second delay at the single most churn-sensitive moment, to personalize on a profile field with zero performance evidence, in the mode (Quick Review) where calibration matters least.
- **Building a "regenerate quiz questions only" capability** for first-time copiers. Rejected: new backend capability (doesn't exist today — questions are fixed at pack creation), still an LLM call, still either blocks the first question or delivers questions mid-session; and the existing regenerate hint already covers the need without new machinery.
- **Reusing the weak-concept follow-up generator for the first quiz.** Rejected on its own terms: it requires a completed prior session as input, which by definition doesn't exist yet. Repurposing it would mean gutting its input contract — that's a new feature wearing an old feature's name.
- **Asking the copier's learner level in an interstitial at copy time** ("quiz at your level?"). Rejected: adds a decision screen between tap and first question, which is the exact friction this fix removes; the level already lives on the profile anyway.
- **Auto-generating in the fallback path** (today's behavior, bug-fixed). Rejected per A3 — conflicts with the explicit-confirmation rule.
- **Showing the AI-suggestion modal on the copy-as-is path.** Rejected per A4 — nothing to reconcile.

---

## PART B — Related-notes link wording and grid columns

### B1. Recommended wording: **"See all →"** (sentence case, matching the codebase's existing link style).

Evaluation against the stated criteria:

- **Clarity under the heading.** Both links sit in a header row directly beside a heading that already names the set ("More {Course/Program} notes", "More in {Subject}"). "See all →" reads as "see all of *these*" — the referent is supplied by the heading two centimeters away. This also matches the product's own established UI convention (header-row link = different destination, here the filtered Public Library) and its existing verb: the current label is already "See **all** in {X}", so "See all →" is a truncation of the existing wording, not a new vocabulary item.
- **Wrap-proof.** Two short words plus an arrow (~9 characters). No real subject or course name can make it wrap because it no longer contains one — "Pediatric Nursing" and "Architectural Design" stop being the link's problem entirely.
- **Against the user's candidates.** "See All →" — right idea, wrong case; ship it sentence-cased as "See all →" to match "See all in Public Library" on the dashboard and the rest of the link system. "See More →" / "View More →" — rejected: "more" is weaker than "all" here because the destination genuinely is the complete filtered set, and "more" is ambiguous between "load more cards here" (in-place pagination) and "go somewhere else"; "all" unambiguously signals a destination. Per the product's own placement convention, a header-row link means *different destination*, and "all" reinforces that; "more" muddies it.
- **One accessibility note for the engineer:** with the visible text shortened, both sections' links become visually identical and screen-reader-identical ("See all", "See all"). Add `aria-label={"See all in " + note.subject}` (and the course/program equivalent) so assistive tech keeps the full context while the visual text stays short.

Exact changes: `page.tsx` line 388 `See all in {note.subject} →` → `See all →`; line 346's non-hub branch `` `See all in ${courseProgram} →` `` → `` `See all →` `` (hub branch untouched, see B3).

### B2. Same wording on mobile and desktop? **Yes — one label everywhere.**

Ship "See all →" at every breakpoint. The desktop case for a longer label is weak because the heading already carries the full name at all breakpoints — desktop users lose zero information. Against that near-zero benefit, a responsive label swap costs: two variants to test, duplicated text nodes for SEO/screen readers, and a precedent of per-breakpoint copy that this codebase currently doesn't have and shouldn't acquire for a link label. Mobile-first audience, one string, done.

### B3. "Browse {Hub} hub →" — **confirm: leave it completely untouched.**

Yes. The generic link and the hub link point to *different classes of destination* (generic filtered Public Library view vs. a curated board-exam hub), and the wording difference is the only signal the user gets about that before clicking. Flattening both to "See all →" would make a PNLE note's course-program link and an un-hubbed course's link look identical while behaving differently — exactly the kind of silent inconsistency this session is trying to remove. Also, practically: the hub label uses the hub's `shortName` (e.g. "PNLE"), so it doesn't suffer the long-name wrapping problem that motivated Part B in the first place. No change needed, no change recommended.

### B4. Grid columns: **yes, collapse the subject section to 2 columns.**

Change `page.tsx` line 391 from `grid gap-4 sm:grid-cols-2 lg:grid-cols-3` to `grid gap-4 sm:grid-cols-2` (matching line 349).

Why 2-col is right rather than the 3-col having a reason to live:

- The two sections are **stacked on the same page using the same `SharedNoteCard`** — that was the entire point of the recent card-cascade unification (v0.50.2's theme is literally "Note Card Content Consistency"). Same cards at two different widths on one page reads as a rendering bug, not a design choice. There is no hierarchy being expressed by the column difference; it's an accident of two sections being built at different times.
- The shared card's content budget (`previewLines={2}`, `tagDisplayLimit={3}`) is tuned once; 3-up cards on `lg` get meaningfully narrower and squeeze exactly the long titles/subjects this audience actually has.
- The audience is overwhelmingly mobile, where `lg:grid-cols-3` never fires — so the 3-col variant is extra visual inconsistency purchased for a minority of sessions.
- The one legitimate defense of 3-col considered: the subject section caps at 3 cards, so `lg:grid-cols-3` renders one clean full row, whereas 2-col renders a 2+1 layout with an orphan third card. That is a real, if minor, aesthetic cost — but an orphan card in a 2-col grid is a normal, unremarkable pattern (cards are `h-full`, left-aligned), whereas mismatched column counts between adjacent identical sections is not. Consistency wins. If the 2+1 orphan is bothersome in practice, the minimal remedy is raising the subject section's fetch cap from 3 to 4 to fill a 2×2 grid like the course/program section — flagged as an option only, not recommended as part of this change (out of this decision's scope).

### Part B — explicit rejections

- **"See More →" / "View More →".** Rejected: "more" ambiguously suggests in-place pagination and undersells that the destination is the complete filtered set; "all" is both the existing vocabulary and the stronger promise.
- **Title-case "See All →".** Rejected: the codebase's links are sentence-cased ("See all in Public Library"); introducing title case creates a new micro-inconsistency while fixing another.
- **Responsive dual wording** (long label on desktop, short on mobile). Rejected per B2: near-zero desktop benefit, real test/markup/precedent cost.
- **Collapsing the hub link into the generic wording.** Rejected per B3: the wording difference is the user's only pre-click signal of a different destination class.
- **Keeping `lg:grid-cols-3` for the subject section.** Rejected per B4: its only defense (one clean 3-card row) doesn't outweigh same-page inconsistency between identical shared cards.
- **Touching the dashboard's "See all in Public Library →"** (`app/dashboard/dashboard-community-notes-section.tsx`). Deliberately out of scope: its object is a fixed short phrase with no wrapping problem, and Part B is scoped to the public note detail page. Noted only as a place the wording pattern also lives, should a future sweep want full uniformity.

---

## Engineer-ready summary

**Part A (needs a Codex prompt per task routing — backend-adjacent flow change across multiple files):**
1. Make copy-as-is the only behavior for the public "Quiz yourself" CTA: always use the synchronous Study Pack deep-copy; never set `generate=1` on the quick-review redirect (`lib/public-note-copy.ts` already has `skipGenerate`).
2. Skip the AI-suggestion modal on this flow entirely (nothing generated, nothing to reconcile).
3. Gate the CTA server-side on the source note having a ready Study Pack; if the fallback is still hit, complete the copy, land on the copied note detail in the standard not-generated state with the explicit Generate CTA — never auto-generate.
4. Keep the existing copied-pack regenerate hint as the personalization affordance; build nothing new for personalization.
5. Fix the navigation-vs-modal race as an ordering guarantee in `private-note-detail-page-client.tsx`, independent of the above.
6. Update `docs/features/public-notes.md` (and `quick-review.md` if it documents the entry path) after shipping.

**Part B (small frontend-only change — Claude Code can implement directly per task routing):**
- `app/public/library/[subject]/[slug]/page.tsx`: line 346 non-hub branch and line 388 → "See all →" with full-context `aria-label`s; line 391 grid → `grid gap-4 sm:grid-cols-2`. Hub branch untouched.
