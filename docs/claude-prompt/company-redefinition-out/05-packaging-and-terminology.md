# 05 — Packaging and Terminology Delta (Creator / Curated Learning + the "Generate" verb)

Planning output only. This is a RECOMMENDATION FOR OWNER RATIFICATION, not a decision — no price,
quota, pass duration, or checkout mechanic is proposed or changed here. It extends the existing
Monetization philosophy (FREE-static / PLUS-interaction / PRO-personalization, `ROADMAP.md` lines
468–476) and the parked `fable-out/05-monetization-recommendation.md`, and extends the terminology
work in `fable-out/06-terminology-rename-map.md` past where it stopped. No code has been changed.

## Decisions carried forward

**Ratified by owner 2026-07-31** (§4 items 1, 2, 3, 6 — see `ROADMAP.md`'s Backlog Index Company
Redefinition row for the recorded decision). §4 items 4, 5, 7, 8, 9 were open at ratification. **Updated 2026-08-01:** items 6 and 8 shipped in `v0.68.0`; item 4 was widened into the standalone Messaging Architecture initiative and its first slice shipped there too; item 7 was touched at the documentation level only (the `fable-out/06` staleness correction). Items 5 and 7's implementation substance, and item 9, remain open.

**RECOMMENDATION — packaging: one product, not two.** Creator (bring-your-own-notes, generate,
private workspace) and Curated Learning (adopt Official Review Sets) should NOT become separate
products or plans. They stay two *modes of use* inside the single existing FREE/PLUS/PRO ladder plus
the existing one-time pass — a messaging/navigation distinction, not a pricing one.

**Why:** (1) the hybrid-moat thesis in `company-redefinition-out/01` depends on every user staying a
potential publisher into the same flywheel regardless of which mode they're using today — a second
priced product would discourage exactly the publishing behavior the moat needs from Curated-Learning
users. (2) The existing ladder already gates by *capability layer* (static/interaction/
personalization) "consistently across profiles... because it gates capabilities, not content"
(ROADMAP:475); Creator-vs-Curated is a *content-origin* axis — a second, orthogonal fork would collide
with the one already ratified. (3) `fable-out/05` already recommends adoption of Official Review Sets
stay FREE and unmetered at every tier — meaning "Curated Learning" is not a distinct paid thing, it
*is* the FREE activation funnel by design; packaging it as a separate paid product contradicts a
principle already awaiting ratification in the same planning line. (4) The given fact that free-tier
quota is essentially never hit rules out volume/quota repackaging as a retention lever for *either*
side — so a "Creator, higher tier" pitch has no packaging value the existing ladder doesn't already
provide; differentiation must stay capability-based, which is what FREE/PLUS/PRO already is.

**RECOMMENDATION — no new pass; reuse the existing Pro exam-pass, re-messaged.** If the owner wants a
named upsell moment for a board-exam/Curated-Learning-motivated visitor, it is the existing Pro
90-day pass (`pricing.md`: "the exam pass"), re-messaged around adaptive planning + Board Exam Mode —
not a new SKU. This applies `fable-out/05`'s already-recommended tier placement (FREE=adopt,
PLUS=conversational assembly, PRO=adaptive planning) rather than inventing a new one.

**RECOMMENDATION — terminology delta, top 3 (full table in Section 3):**
1. **Keep** the generation-flavored verb on "Generate Study Pack" / "Generate Quiz" (teacher path) /
   "Regenerate Quiz" — this names the actual mechanism (a learner's own note becomes structured
   practice) that neither a static bank nor a generic AI note tool can offer; it is already doing real
   positioning work on the public `/how-it-works` walkthrough.
2. **Rename** "Generate Note" / "Generate a note" (topic note generation from a bare prompt, no
   source note) → **"Create a Note" / "Draft a Note"** — this is the one feature structurally closest
   to a generic AI note tool (freeform AI-authored prose, not derived from the learner's own
   material), so keeping "Generate" here borrows the differentiator's language for the least
   differentiated feature. This reverses `fable-out/06`'s blanket "keep, not touched" for onboarding
   with the argument 06 didn't make.
3. Status/loading copy is not an independent decision — it inherits whichever bucket its action verb
   lands in ("Generating..." stays for Study Pack/Quiz; becomes "Creating..."/"Drafting..." wherever
   its parent verb is renamed). One inconsistency already exists as supporting evidence: onboarding
   prose already says "creating your Study Pack" one line away from a button that says "Generate
   Study Pack →."

---

# Full detail

## 1. Creator vs. Curated Learning: two products, or one product with packaging-only distinction?

### 1.1 The recommendation

**Single product. Packaging-only distinction.** Creator and Curated Learning should ship as two
*framings of the same account* — the existing FREE/PLUS/PRO ladder, the existing one-time pass model,
unchanged — rather than as two separate plans, SKUs, or sign-up products. Concretely, "packaging-only"
means: an acquisition surface (e.g. Exam Hub, a future landing section) can *frame* itself around
"Curated Learning" (adopt an Official Review Set, start practicing immediately) or around "Creator"
(bring your own notes, generate your own Study Packs), but underneath, both framings resolve to the
same account, the same FREE/PLUS/PRO entitlements, and the same capability boundaries already
recommended in `fable-out/05`. A user who arrives through the "Curated Learning" door and later starts
authoring notes does not need to buy anything new to do so, and vice versa.

### 1.2 Why not two products — four independent reasons

**(a) The flywheel needs every user to stay a potential publisher.** `company-redefinition-out/01`'s
hybrid-moat thesis (Section 2) is explicit that the moat is authorship inflow *and* the curation layer
*together* — "either piece alone is not" the moat. That inflow only compounds because *any* user can
publish a note that later becomes a fulfillment candidate for someone else's curriculum. If "Creator"
and "Curated Learning" were separately priced products, a Curated-Learning subscriber has no product
reason to ever author or publish a note — they didn't buy the authoring product. That quietly turns
off the exact inflow the flywheel depends on for the segment (board-exam candidates, per Section 3 of
`01`) the redefinition is betting on first. A single product with both capabilities always available
keeps every account a potential contributor to the flywheel, regardless of which mode brought them in.

**(b) The existing ladder already gates by capability layer, not content origin.** The Monetization
philosophy (`ROADMAP.md:468-476`, extended by `fable-out/05` Section 1) draws its line as
FREE-static / PLUS-interaction / PRO-personalization, and states it "applies consistently across
profiles... because it gates capabilities, not content." Creator vs. Curated Learning is a *content
origin* distinction (your own notes vs. official pre-built sets) — a fundamentally different axis than
capability layer. Forking pricing along a second, orthogonal axis creates exactly the kind of
per-feature tiering debate the philosophy exists to close (`fable-out/05`, Section 1.4: "This gives
future features a mechanical test instead of a per-feature debate"). Keeping Creator/Curated as
messaging framing, not pricing, avoids ever having to answer "which axis wins" for a user who is both.

**(c) `fable-out/05` already recommends Curated Learning's core action stay FREE.** Section 1 of that
document places "recommend + adopt an Official Review Set" at FREE precisely because it is
deterministic and near-zero marginal cost, and calls this "the strongest activation story Smart Review
Planning has: a FREE exam-taker lands, declares [board exam], and immediately receives a complete,
curated, adoptable plan." If that recommendation is followed, "Curated Learning" is not a paid product
at all in its primary form — it is the FREE-tier front door. Packaging it as a distinct paid product in
this document would directly contradict a principle this same planning line has already put forward
for ratification. The two documents must agree with each other before either goes to the owner.

**(d) The free-tier-quota-never-hit fact removes the one lever a "Creator, higher tier" pitch would
otherwise reach for.** The natural pitch for a distinct, higher "Creator" tier is "more generation
headroom" — but if free-tier Study Pack/Quiz/topic-note quota is essentially never hit today, learners
are not bumping into a ceiling that a repackaged, differently-named quota would relieve. Repackaging an
unused ceiling under a new product name changes vocabulary, not behavior. The only levers that
plausibly move retention or willingness-to-pay are the capability boundaries already defined
(conversational assembly at PLUS, adaptive planning at PRO, Board Exam Mode / difficulty selection at
PRO) — which the existing ladder already sells. A second product adds packaging complexity without
adding a lever that isn't already in the ladder.

### 1.3 What "packaging-only" looks like, concretely (illustrative, not a spec)

- Exam Hub and similar board-exam-motivated landing surfaces can lead with Curated-Learning framing
  ("start with the Official [Board] Review Set") without implying a different account type.
- A learner's own Notes/Library area keeps Creator framing (bring your own notes, generate your own
  Study Packs) as it does today — nothing changes here.
- Both framings share one Settings/Billing surface, one `GET /api/me/plan` contract, one upgrade path
  (`getUpgradeCtas`). No new plan object, no new checkout flow.
- Whether "Creator" / "Curated Learning" ever become user-facing labels at all (vs. staying an internal
  strategic frame used only in acquisition-page copy decisions) is left to the owner (see Section 4).

## 2. Where a Curated-Learning-flavored upsell sits, if the owner wants one

### 2.1 No new pass — reuse the existing Pro exam-pass

Given Section 1's conclusion, there is no new "Curated Learning pass" to design. If the owner wants a
named upsell moment for a Curated-Learning-heavy, board-exam-motivated user, the existing Pro 90-day
pass already has the right shape and the right name: `pricing.md` documents it today as "the exam
pass" (hero CTA `Get Pro — <price> / 3 months`). The recommendation is to **re-message**, not
re-price: when a user arrives through a Curated-Learning/board-exam framed door, the upgrade story for
that pass leads with the capabilities that are actually relevant to that path — adaptive planning and
Board Exam Mode — rather than a generic "more quota" pitch (which Section 1.2(d) already shows is the
wrong lever for this segment). No new duration, price, or checkout mechanic.

### 2.2 Applying reuse-is-free / generation-is-metered, not inventing a new principle

`fable-out/05` Section 2 already ratifies: *quota meters marginal LLM cost, not value received* —
reuse (adopting an Official Review Set, copying a public note) never consumes quota; only genuinely
new generation does. Nothing about a Curated-Learning-framed upsell changes this. Concretely:

- Adopting an Official Review Set stays free and unmetered at every tier, including FREE — exactly as
  `fable-out/05` recommends. A Curated-Learning pass is never "pay to adopt more."
- The only things worth selling to a Curated-Learning-heavy user are the same PLUS/PRO capability
  layers `fable-out/05` Section 1 already defines: **PLUS** — ask the Learning Assistant to
  conversationally assemble a custom Review Set from existing public material (per-query LLM cost,
  interaction-shaped, same cost logic as Ask Companion); **PRO** — adaptive, ConceptHealth-weighted
  replanning of what to review next, plus Board Exam Mode and difficulty selection, which are already
  Pro-only per `subscriptions-and-usage-limits.md`.
- This means the Curated-Learning upsell path and the Creator upsell path converge on the *same* two
  paid capability layers — reinforcing Section 1's conclusion that there is no separate product here,
  only a separate on-ramp into the same ladder.

### 2.3 What this document assumes vs. ratifies

This document builds on `fable-out/05`'s tier placement (FREE adopt / PLUS conversational assembly /
PRO adaptive planning) as its working assumption, because that placement is what makes "reuse the
existing Pro pass" coherent. It does **not** re-ratify `fable-out/05` — that document's own "owner must
decide" list (ratify-or-reject the tier split, all prices, PLUS/PRO allowance numbers, adoption caps,
etc.) remains fully open and is not resolved here.

## 3. Terminology delta: the "Generate" verb, instance by instance

### 3.1 Why this is the actual remaining lever

`fable-out/06` already did two things: it banned "AI"-branded phrasing everywhere in-product
(`"AI Suggestions"` → `"Suggested Details"`, `"AI Critique"` → `"Answer Critique"`, etc.), and it
explicitly blessed the bare verb "generate" as acceptable action language ("actions are verb-first
without 'AI' — 'Generate a note' is acceptable — 'generate' describes the action, not the
technology"). That leaves "Generate" itself untouched as a blanket "fine to keep" — 06 never asked
whether *every* instance of it should stay, only whether it needed an "AI" prefix removed. Since "AI"
is already fully scrubbed and the landing page already argues against "generic AI tools"
(`app/page.tsx:131`, kept intentionally per `06`), the only remaining lever that could still make the
product read as more differentiated is deciding, verb-by-verb, which of the many "Generate" CTAs
should keep that word and which should not. Doing this requires the exception the task asks for
explicitly, not a blanket strip.

### 3.2 The keep bucket: verbs that name the actual differentiator

| Instance (representative locations) | Recommendation | Why keep |
|---|---|---|
| "Generate Study Pack" — note editor (`note-editor-page-client.tsx`, `private-note-detail-page-client.tsx`), onboarding, collection detail (`collection-detail-page-client.tsx`), dashboard empty state, `/how-it-works` walkthrough step 2 | **Keep** | This is the literal mechanism that makes NoteLib different from a static exam bank (nothing to "generate" — a bank only has things to browse) and from a generic AI note tool (the output is wrapped by the curation model, never raw). `/how-it-works` already spends a whole walkthrough step on it: "Turn your saved note into a structured Study Pack with summary, key concepts, and quiz-ready material" — this verb is doing real positioning work on the single most differentiation-focused public page. |
| "Generate Quiz" / "Regenerate" (teacher note-detail flow, `private-note-detail-page-client.tsx:2109-2150`) | **Keep** | Same mechanism as Study Pack generation — a note becomes a structured quiz. The existing pattern already separates verbs correctly once content exists: "Generate Quiz" (create) → "View Quiz" (consume) → "Regenerate" (redo) — a clean create/consume/redo split worth preserving, not flattening. |
| "Generate Study Pack" step title, `/how-it-works` | **Keep** | Marketing surface; reinforces the differentiator exactly where a visitor is comparing NoteLib to generic tools. |

### 3.3 The rename bucket: the one instance where "Generate" undercuts the argument

| Instance (representative locations) | Recommendation | Why rename |
|---|---|---|
| "Generate Note" / "Generate a note" — topic note generation from a bare topic prompt with no source note (`note-editor-form.tsx` `generateNoteLabel`, onboarding Step 3, demo page) | **Rename to "Create a Note" / "Draft a Note"** | This is the one "Generate X" action where there is no existing learner material being transformed — it is the AI drafting freeform prose from a topic for the learner to then review/edit. Structurally, that is exactly what a generic AI note tool does. Keeping "Generate" here spends the same word the product needs to reserve for its actual differentiator (Study Pack/Quiz generation *from your own notes*) on the one feature that looks most like the thing NoteLib's own marketing argues against. `06` already renamed the feature *noun* here ("AI Note Generator" → "Topic Notes") but explicitly left the *verb* alone ("Onboarding already ships 'Generate a note' / 'Generate Note' — keep") without an argument either way; this closes that gap with one. |

**Explicit statement of the exception, as required:** the rule is not "remove Generate everywhere" —
it is "keep Generate exactly where the input is the learner's own material and the output is
structured practice (Study Pack, Quiz); rename it where the input is a bare prompt and the output is
freeform AI-authored prose the learner hasn't written yet." The differentiator is the *transformation
of your own notes*, not the presence of AI drafting text — so the verb should track the transformation,
not the presence of a model call.

### 3.4 Downstream copy that is not an independent decision

Loading/status copy should inherit whichever bucket its parent action verb lands in, not be decided
separately:

- "Generating Study Pack...", "Generating your quiz...", "Couldn't Generate Study Pack" — **stay as
  is**, matching the kept verb.
- "Generating a note will replace the current content..." (`note-editor-form.tsx:601`), demo page
  "Generating note…" — **become** "Creating a note will replace..." / "Drafting a note will
  replace...", "Creating note…" / "Drafting note…", matching the renamed verb.

**Supporting evidence this drift is already starting on its own:** onboarding already contains one
line of prose that reads "Generate a note first, then review and edit it before **creating** your
Study Pack" (`app/onboarding/page.tsx:1115`) one screen away from buttons labeled "Generate Study Pack
→" and "Generate Note." The app's own copy has already drifted toward an outcome verb ("creating")
for Study Pack in flowing prose while keeping "Generate" in the CTA — the opposite assignment from
what this document recommends (Study Pack should keep "Generate," topic notes should not). This is
cited not as an error to silently fix, but as evidence that the two verbs are currently used
inconsistently with no stated rule — exactly the gap Section 3 closes.

### 3.5 Already-clean patterns worth preserving as precedent

These are not renames — they already match the recommendation and should not regress:

- **"Adopt" / "Adopted"** (Curated Learning side: `adoptGoal`, `adoptStudyPlan`, `AdoptedBadge`) —
  already correctly generation-free, consistent with reuse never being metered or "generated."
- **"+5 Questions" / "Adding..."** (Challenge Quiz progressive growth,
  `study-packs/[id]/challenge-quiz/page.tsx:2163`) — already avoids "Generate More," a clean precedent
  for any future incremental-content action.
- **"Study this note" / "Review due concepts"** (`collection-detail-page-client.tsx` alternate action
  labels once content exists) — already outcome-verb-flavored alongside the "Generate Study Pack"
  create-state label, showing the create/consume split this document recommends is already partially
  in place.

## 4. Owner must decide (deliberately NOT set here)

1. **Ratify or reject the packaging recommendation itself** — one product with Creator/Curated framing
   vs. two separate products — is a recommendation, not a fait accompli.
2. **Whether "Creator" / "Curated Learning" become user-facing labels at all**, anywhere in-product or
   on marketing pages, vs. staying an internal strategic frame used only to sequence which door an
   acquisition page opens with. No copy is proposed here.
3. **Whether `fable-out/05`'s tier placement is ratified** (FREE adopt / PLUS conversational assembly /
   PRO adaptive planning) — this document assumes it as a working basis (Section 2.3) but does not
   re-decide it; its own "owner must decide" list stays fully open.
4. **Any re-messaging copy for the existing Pro exam-pass** around adaptive planning / Board Exam
   Mode for a Curated-Learning-framed upsell — directional only here, no strings proposed.
5. **Which IA/landing changes, if any, follow from the packaging recommendation** (e.g. whether Exam
   Hub itself changes structure) — this document does not propose a redesign of Exam Hub.
6. **The "Generate Note" rename** — whether to adopt "Create a Note," "Draft a Note," or another
   outcome verb, and the exact rollout (onboarding Step 3, note editor `generateNoteLabel` default,
   demo page, and any others found in a full implementation-session grep) is an implementation
   decision once the direction is ratified.
7. **Rollout priority and sequencing** of every rename identified in this document and in
   `fable-out/06` — this document does not sequence a work plan, only recommends which bucket each
   instance belongs in.
8. **Whether adjacent micro-copy oddities noticed in passing** (e.g. the grammatically awkward "Retry
   Generate" label at `private-note-detail-page-client.tsx:2109,2150`) get cleaned up in the same
   implementation pass — flagged here as an observation, not decided, since it is a copy-quality issue
   rather than an AI/Generate-terminology issue in scope for this document.
9. **All prices, quotas, pass durations, and checkout mechanics** — none proposed or changed anywhere
   in this document, per the hard constraint it was written under.
