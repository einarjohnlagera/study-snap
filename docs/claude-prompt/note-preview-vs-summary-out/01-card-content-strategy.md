# Card Content Strategy: Note Preview vs. Summary Preview

Run 2026-07-16 via the `fable` model. Prompt: `../note-preview-vs-summary-prompts/01-card-content-strategy.txt`.
Session plan: `../note-preview-vs-summary-session-plan.md`. Planning analysis — no code, for human review
before scoping.

---

## 1. Diagnosis: the rule is right for the wrong reason

The standing rule ("prioritize note preview over summary") conflates **two distinct mechanisms**, and the
Generate Note feature broke one of them while leaving the other intact. Separating them is the whole
diagnosis.

**Mechanism A — provenance signaling (broken).** The rule's written rationale is "a note means a real
person wrote this," i.e., the note excerpt functions as an authenticity signal to a stranger on an
acquisition surface. That mechanism is now unreliable in both directions. The product cannot verify it
(no origin field exists), the visitor never could verify it, and Generate Note output actively works
against it: a structured first draft opening with "Overview" / "Core Concepts" *reads* as templated AI
content to exactly the visitor the signal was meant to reassure. The note preview is now a noisy
provenance signal — sometimes it says "a real reviewer wrote this," sometimes it says "this is machine
output," and the product has no way to know which it's emitting on any given card.

**Mechanism B — source fidelity of the preview (intact).** In a notes-first product, the note is the
canonical object and the Study Pack is a derivative. A card is a promise about the destination: click it
and the primary content you land on is the note body. Previewing the note body is therefore the *honest*
preview regardless of who authored it — an AI-generated note's body is still what the visitor will
actually read on the detail page. A summary-only card previews a derivative and misrepresents the click
target, and it only exists conditionally (notes without Study Packs have no summary). This mechanism does
not depend on human authorship at all, and it still holds.

**Mechanism C — the dual display is its own separate problem: redundancy and scan cost.** Showing "NOTE
PREVIEW" and "SUMMARY PREVIEW" stacked puts two near-duplicate excerpts of the same material on one card.
For an AI-generated note with an AI-generated summary, it's two AI paraphrases of the same topic. It
roughly doubles card height, cuts cards-per-viewport on browse grids (scanning surfaces where density is
the job), and gives the visitor a comparison task they didn't ask for and can't complete at excerpt
length.

**Verdict on the rule:** Keep the *priority* (note preview first), discard the *rationale* (human
authorship), and stop interpreting "prioritize" as "show both with the note on top." The rule should be
rewritten in the docs as: *the note is the source object; cards preview the destination's primary
content; the summary is a fallback, not a co-equal.* This decouples the rule from an authorship claim
that is no longer true and won't become true again.

The important strategic consequence: **switching to summary-first would not fix the trust problem.** The
summary is also AI text. Swapping one AI-adjacent excerpt for another recovers zero human voice while
breaking Mechanism B and drifting the card presentation toward exactly the generic "AI tool" framing the
locked SEO/messaging rule forbids. The trust exposure lives in the *composition of the library* (what
share of public notes are AI-authored), not in which excerpt the card shows — and that composition is
currently unmeasurable, which is what Phase 2 below is for.

---

## 2. Recommendation: one uniform rule, one excerpt, all four surfaces

### The rule (hand-to-engineer specific)

**Every note card shows exactly one preview excerpt, chosen by this cascade:**

1. **Note preview** (excerpt of the note's own body), if the note body is non-empty after trimming and
   meets a minimal length threshold (suggest: ≥ 40 characters after whitespace collapse — below that it's
   a stub, not a preview).
2. **Summary preview** (Study Pack summary excerpt) as fallback, shown with a small inline label — e.g. a
   muted "Summary" tag — so summary text is never silently passed off as note text.
3. **Neither** → no excerpt block at all; the card renders title, subject/course meta, and tags only. Do
   not pad with placeholder copy.

**Card anatomy, in order:** title → subject/course meta line → single excerpt (3-line clamp, owned by the
shared component) → tags → status chip where applicable. Remove the "NOTE PREVIEW" / "SUMMARY PREVIEW"
section labels entirely; a lone excerpt needs no label (except the fallback "Summary" tag above).

**Applied per surface:**

| Surface | Change |
|---|---|
| Public note detail — "More in {Subject}" | Shared card: drop the stacked dual preview, apply the cascade. |
| Public note detail — "More {Course/Program} notes" | Migrate bespoke card to the shared component (see §3). |
| Public Library browse grid | Same shared card, same cascade. |
| Private Library grid | Same shared card, same cascade, **plus** a Study Pack status chip (Draft / Generating / Study Pack ready). |

### Why uniform rather than surface-differentiated

The strongest case for differing: on the private Library, the owner already knows what they wrote, so
authenticity-signaling isn't the card's job — maybe summary-first serves them better ("what did the AI
make of this?"). Rejected for three reasons:

1. **Recognition beats digest privately.** The job of a private card is "which note is this?" — and the
   best recognition cue for your own note is its own opening lines, not an AI paraphrase in a voice you
   didn't write. True whether the user typed the note or generated-then-edited it.
2. **"Does a Study Pack exist?" is a status question, not a text question.** A status chip answers it in
   ~20px; a summary excerpt answers it in ~3 lines while displacing the recognition cue. The chip is the
   cheaper, clearer instrument.
3. **Per-surface rules are how you got here.** The two related-notes sections diverged because they were
   built six weeks apart under no single enforced rule. One rule, one shared component, one place to
   change it. Surface differentiation should carry its weight in mechanism, and it doesn't.

The surfaces legitimately differ in *ancillary* card content (private status chip; public tags/course
emphasis) — never in the excerpt rule.

### Featured ranking

The cascade is a display rule only; it changes no data. Notes still store their body, so the "non-empty
note preview" gate for Featured eligibility is untouched by Phase 1. There is a latent integrity question
here that Phase 2 surfaces — see below.

---

## 3. Resolving the two public-note-detail sections directly

**Migrate "More {Course/Program} notes" to the shared card component, and show identical preview content
in both sections** (the single-excerpt cascade). This also retires the older summary-only treatment,
which is a relic of the earlier "Study Packs are the value" framing.

A deliberate distinction was considered — "More in {Subject}" as near-proximity comparison (richer cards)
vs. "More {Course/Program}" as broad discovery (lighter cards) — and rejected. The visitor doesn't know
the sections' internal taxonomy distinction and won't read two card treatments on one page as intentional
hierarchy; they'll read it as sloppiness, on a page whose explicit job is trust. The six-week drift
already proved no one was maintaining a deliberate difference. If the sections should differ at all,
differ in **query and count** (e.g., 3 course-level cards vs. 6 subject-level cards), never in card
anatomy.

---

## 4. Phased path

### Phase 1 — now, no new data required
- Implement the single-excerpt cascade in the shared card component; delete the dual-preview layout and
  its labels.
- Migrate the bespoke "More {Course/Program} notes" card to the shared component.
- Rewrite the documented product rule's rationale as described in §1 (source-object framing, not
  human-authorship framing), in the relevant `docs/features/*.md`.
- Cost: frontend-only display change plus one bespoke-card migration. No schema, no pipeline, no
  visibility changes.

### Phase 2 — build origin tracking, but narrowly, and not for card rendering

**Real opinion: yes, build the instrumentation — no, don't make cards origin-aware even once you have
it.**

*Why instrumentation is worth it.* The origin split (manual / AI-generated / imported) is genuinely
unknown today, and two real decisions are blocked on it:
1. **Positioning risk sizing.** The public library's trust posture depends on how much of it is
   AI-authored. If Generate Note output is 5% of public notes, the provenance concern in §1 is
   theoretical; if it's 60%, the "teach first" surfaces are substantially previewing machine text and the
   team should know that before the SEO/messaging rules calcify further. You cannot manage what you
   cannot measure, and right now this is unmeasurable.
2. **Featured-section integrity.** An AI-generated note qualifies for Featured via its auto-generated
   body — satisfying the letter of the "non-empty note preview" gate while arguably voiding its spirit
   (the gate existed as a proxy for "someone put real work in"). Whether Featured eligibility should
   consider origin is a genuine open decision, **but explicitly not recommended now**: it edges against
   the constraint that Generate Note is sanctioned and must not be disadvantaged, and it should only be
   evaluated once the measured split shows whether it's even a live problem. Phase 2 makes the decision
   *possible*; it doesn't pre-make it.

*The honest cost, owned explicitly:* a new nullable `origin` enum column on the note entity (`MANUAL` /
`AI_GENERATED` / `IMPORTED`), set at creation time in three code paths (manual create, Generate Note,
OCR/import); a matching analytics event property (added to the `AnalyticsEventType` contract first, per
convention); a migration. **All existing notes are permanently `UNKNOWN` — backfill is not possible**,
and template-sniffing heuristics ("starts with Overview/Core Concepts") should not be used to fake it:
they false-positive on humans who write structured notes and false-negative on edited drafts. This is a
backend change touching a migration and multiple service paths — Codex-prompt territory under the
task-routing rules, not a free toggle.

*Why origin-aware card rendering is rejected even with the data:*
- **It doesn't fix a mechanism.** An AI-generated note's body is still the destination content;
  previewing it is still the honest preview (Mechanism B). Origin changes nothing about what the card
  should show.
- **Origin is creation-time provenance, not current-authorship truth.** Generate Note produces a *first
  draft* that users edit. A heavily human-revised AI draft carries `AI_GENERATED` forever; rendering it
  differently would be wrong on the merits.
- **It creates a visible two-class library**, which quietly disadvantages a sanctioned feature's output,
  and invites users to game whichever class renders better.

---

## 5. Is "show both" ever defensible? (Honest answer: no)

The best steelman is fidelity-checking — letting a visitor verify the AI summary faithfully represents
the source note. It fails at card scale: two 2-to-3-line excerpts, truncated from different parts of two
documents, cannot establish fidelity. Verification is a detail-page job, where the full note and full
Study Pack sit adjacent — and that page already does it.

What the dual preview actually is: **an unresolved compromise between two product eras.** The older
bespoke card (summary-only) belongs to the era where the Study Pack was the pitch; the note preview was
bolted on top when the notes-first rule landed, and nothing was removed. The stack is the fossil record of
the pivot, not a designed feature. Retire it.

---

## 6. The conversion CTA — flag, not rewrite

"Turn your own notes into something like this" remains **directionally consistent** with this
recommendation — cards stay note-fronted, so the copy and the cards tell the same story. No forced
change.

Two soft misalignments flagged for a future messaging pass: (a) "your own notes" assumes a
bring-your-notes visitor, while the product now also serves start-from-a-topic users via Generate Note —
the copy quietly narrows the funnel it sits on top of; (b) when the fallback "Summary" excerpt shows on a
card adjacent to that CTA, the visitor is being sold "notes like this" while reading Study Pack text. Both
are minor today; the second one Phase 1 actually *reduces* by making summary text the labeled exception
rather than a permanent co-equal block. Any future copy evolution must stay inside the locked
notes-library-first rule — which, notably, this card strategy reinforces rather than strains.

---

## 7. Explicit rejections

1. **Summary-first everywhere** — swaps one AI-adjacent excerpt for another (no trust recovered), breaks
   source-fidelity of the preview, only exists conditionally, and drifts card presentation toward the "AI
   tool" framing the locked messaging rule forbids.
2. **Keeping the dual preview but reordering/restyling it** — restyling the compromise is still the
   compromise; redundancy and grid density don't improve.
3. **Origin-aware card rendering** — rejected even post-instrumentation: no mechanism it fixes, provenance
   ≠ current authorship after editing, visible two-class library disadvantaging a sanctioned feature.
4. **Heuristic origin detection for display** (template-sniffing note bodies) — brittle in both
   directions; never make render decisions on it.
5. **Surface-differentiated excerpt rules** (public = note, private = summary) — private recognition is
   better served by the note's own opening; a status chip answers the pack-status question more cheaply;
   per-surface rules recreate the exact drift being cleaned up.
6. **An "AI-generated" provenance badge on public cards** — impossible to do honestly today (no origin
   data), and permanently misleading even after Phase 2 because all pre-existing notes are `UNKNOWN`; a
   badge that's absent-by-ignorance reads as a claim of human authorship.
7. **Deliberately keeping the two related-notes sections visually different** — no visitor-facing
   rationale survives scrutiny; differ in query and count only.
8. **Changing Featured eligibility now** — named as a real future decision Phase 2 unlocks, deliberately
   not recommended today; it should be evaluated against the measured origin split, and it sits in
   tension with the "don't disadvantage Generate Note" constraint.

---

**Summary of the recommendation in one line:** one excerpt per card everywhere — note preview first,
labeled summary as fallback, never both — migrate the bespoke section to the shared card, rewrite the
rule's rationale from "a human wrote this" to "the note is the source object," and build creation-time
origin tracking in a later phase for measurement and Featured-integrity decisions, explicitly not for
per-origin card layouts.
