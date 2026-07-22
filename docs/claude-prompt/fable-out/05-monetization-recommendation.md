## Decisions carried forward

**Status: RECOMMENDATION FOR OWNER RATIFICATION — not a decision.** Nothing here changes
prices, quotas, pass durations, or checkout mechanics; it extends the existing Monetization
philosophy (FREE-static / PLUS-interaction / PRO-personalization) to one new capability and
leaves every number to the owner (see "Owner must decide" at the end).

**RECOMMENDATION — tier placement of Smart Review Planning (split by capability layer, matching the shipped ladder):**

- **FREE — static/deterministic layer.** Being *recommended* an existing ACTIVE Official
  Review Set for your course/program (reuse-search step 0) and adopting it. Deterministic,
  rule-based, near-zero marginal cost (DB copy via the existing `includeStudyPack=true`
  spine). Follows the same FREE precedent as the v0.40.0 weekly countdown and the
  "deterministic reordering is not PRO" rule. This is the activation/conversion funnel.
- **PLUS — interaction layer.** *Conversational assembly*: asking the Learning Assistant to
  assemble a custom Review Set from existing PUBLIC notes / Study Packs. Per-query LLM cost,
  interaction-shaped, and gives PLUS a second genuinely distinct capability alongside Ask
  Companion (today PLUS is otherwise quota-only).
- **PRO — personalization layer.** *Adaptive planning*: ConceptHealth-weighted selection,
  readiness-aware sequencing, learning-pattern/LLM-informed re-planning of what to review
  next. This is exactly what the philosophy reserves for PRO — adaptive selection, not
  deterministic reordering.
- The **Internal Curator is never tiered.** It is an ADMIN-role internal tool with an
  admin-owned generation budget; plan tiers apply only to the learner-facing Learning
  Assistant. The Curator/Assistant split is unaffected by any tiering choice.

**RECOMMENDATION — reuse-free / generation-metered principle (Q7):**

- **Reuse of existing notes/Study Packs does NOT consume learner quota.** Adopting an
  Official Review Set or copying a PUBLIC note (with its linked Study Pack) is a DB copy —
  zero marginal LLM cost — and should never decrement `UserUsageEntity` Study Pack counters.
- **Only genuinely NEW generation is metered**: Study Pack generation on the learner's own
  notes stays on the existing quota ladder unchanged; Curator gap-fill generation stays on
  the admin budget (never learner quota), exactly as the foundation doc's cost model states.
- Quota thereby measures what actually costs money. Whether adoption gets any anti-abuse
  cap, and every number on every layer, is **owner-decided** (see final list).

---

# Full detail

## 1. Which tier does Smart Review Planning belong in?

### 1.1 The question is really three questions

"Smart Review Planning" is not one capability; it decomposes along exactly the axis the
existing Monetization philosophy already draws:

| Layer | What the learner experiences | Marginal cost shape | Philosophy line it sits on |
|---|---|---|---|
| **Recommend + adopt** | "Here is the Official Review Set for your course — adopt it." Deterministic match on `courseProgram` against ACTIVE curriculum-linked Official Review Sets (reuse-search step 0). Adoption is the existing copy spine. | ~zero (DB reads + DB copy; no LLM) | **FREE — static guidance** |
| **Conversational assembly** | "Assemble me a Review Set covering X from what already exists." The Learning Assistant searches PUBLIC notes / ready Study Packs / confirmed fulfillments and composes a personal Review Set; on gaps it files a `curator_generation_request` and says so honestly. | per-query LLM (the conversation/selection itself), but the *content* is still reuse | **PLUS — interaction** |
| **Adaptive planning** | The plan reacts to the learner: ConceptHealth-weighted selection ("your weak concepts first"), readiness-aware sequencing, re-planning as mastery changes. | signal is mostly derivable from existing ConceptHealth (high margin); only the adaptive selection/conversation carries recurring LLM cost | **PRO — personalization** |

### 1.2 Why "assemble from existing reusable material" is not automatically FREE

The tempting shortcut is: "it's curation, not generation, and reuse is zero-cost — so all of
it is FREE." That conflates *content cost* with *capability cost*. The philosophy gates
**capabilities, not content**:

- The **content** being assembled is free to serve (already generated, admin-budgeted,
  published). That is why *adoption* belongs in FREE.
- The **act of interactive assembly** is an LLM-backed conversation with recurring per-query
  cost — the same cost shape as Ask Companion, which the philosophy already places at PLUS.
  Putting it in FREE would make FREE carry recurring LLM cost, which breaks the "FREE =
  near-zero marginal cost" invariant that makes FREE sustainable as a funnel.
- The **adaptive layer** is precisely the philosophy's PRO definition: "genuinely adaptive
  guidance… adaptive/learning-pattern/LLM-informed selection." Placing it lower would
  re-open the question the philosophy exists to close.

### 1.3 Why deterministic recommendation must stay FREE

The philosophy is explicit that deterministic, rule-based prioritization follows the FREE
precedent ("not any prioritization… deterministic reordering follows the same FREE precedent
as the v0.40.0 weekly countdown"). "Show the Official Review Set linked to your
courseProgram, sorted by curriculum position" is deterministic rule-following, not adaptive
selection. Gating it would both violate the established line and throttle the adoption loop
that makes the whole curation economy work (Section 2).

It is also the strongest activation story Smart Review Planning has: a FREE exam-taker
lands, declares ALE/PNLE/LET, and immediately receives a complete, curated, adoptable plan.
That is the funnel; PLUS/PRO then sell the *interaction with* and *personalization of* that
plan — never a re-paywalling of the plan itself.

### 1.4 Does adaptive/personalized planning belong at PRO? — Yes

Three reasons, all already codified:

1. **Definitional fit.** PRO = personalization. A plan that reorders and reselects based on
   the individual learner's ConceptHealth and session history is the canonical example.
2. **Cost fit.** The underlying signal (ConceptHealth, readiness) is already computed with
   no per-query LLM — high margin — and only the adaptive selection logic carries recurring
   cost, "controlled via the Interview Practice template" per the philosophy. PRO's price
   supports that recurring cost; FREE/PLUS margins do not.
3. **Ladder coherence.** Pro is positioned as the exam-prep tier (Board Exam Mode, Long
   Exam, difficulty selection, highest Adaptive Practice volume). "Your review plan adapts
   to your weak areas as the exam approaches" is a natural headline addition to that tier
   and strengthens the existing Free → Plus → Pro *study-stage* narrative in PLANS.md
   without adding a single new pricing mechanism.

**Boundary rule to carry forward (RECOMMENDATION):** any planning behavior that can be
expressed as a deterministic rule over already-visible data (curriculum order, "not yet
adopted", published/unpublished) is FREE; behavior requiring a per-learner model of mastery
or an LLM-informed choice is PRO; the conversational middle (LLM-mediated but reuse-only)
is PLUS. This gives future features a mechanical test instead of a per-feature debate.

### 1.5 What is explicitly NOT tiered

- **The Internal Curator** — ADMIN-role tool. Gap scans, draft generation, curriculum
  authoring, publishing. Its costs live on the admin generation budget; plan tiers never
  touch it. Tiering the Assistant differently in no way blurs the Curator/Assistant split:
  the Assistant remains read-only over published content at every tier, and generation
  remains Curator-only behind mandatory human review at every tier.
- **Filing a gap request** (`curator_generation_request`, `demandCount++`). RECOMMENDATION:
  available at all tiers, because demand signal from FREE users is valuable input to the
  admin budget's prioritization and costs nothing to accept. (Whether paid users' demand is
  *weighted* higher in curator prioritization is an owner call — listed below.)
- **Coverage/readiness display** follows the v0.33.0 precedent ("access not billing"):
  seeing what exists is not the paid capability; interacting and personalizing are.

## 2. Q7 — Should reuse be free while only new generation consumes quota?

### 2.1 Recommendation: yes — adopt "reuse-free, generation-metered" as a codified principle

This is already the de facto model in the foundation doc's cost model ("Reuse never consumes
quota; only genuinely new generation does") and in shipped behavior (public-note copy with
`includeStudyPack=true` is a DB copy, not a generation event). The recommendation is to
**ratify it as a named principle** alongside FREE-static/PLUS-interaction/PRO-personalization,
so future features inherit it instead of re-deciding it.

Stated precisely:

> **Quota meters marginal LLM cost, not value received.** An action that triggers no new
> LLM generation (adopting an Official Review Set, copying a PUBLIC note and its linked
> Study Pack, re-reading anything already generated) never decrements a learner quota.
> An action that triggers new LLM generation decrements exactly one quota: the learner's
> (own-note Study Packs, quizzes, topic notes — the existing ladder, unchanged) or the
> admin generation budget (Curator gap-fill) — never both, and never silently.

### 2.2 Cost-sustainability argument

- **Charging for reuse taxes a zero-cost action.** An adopted Review Set is DB rows. If
  adoption consumed Study Pack quota, FREE's 10/month would be devoured by copies that cost
  NoteLib nothing — worst of both worlds: the user feels metered, the meter measures nothing
  real, and the company gains no cost protection because there was no cost.
- **Reuse is the mechanism that makes the economics work.** The foundation doc's model is
  explicit: fulfillments shared across curricula push the Nth curriculum's marginal cost
  toward zero, and `demandCount` prioritizes where the one-time admin budget goes. Every
  adoption amortizes a past admin-budgeted generation across one more learner. Metering
  adoption would suppress exactly the behavior that drives cost-per-learner down.
- **The real recurring costs remain fully metered.** Own-note generation → existing learner
  quotas (unchanged). Gap-fill generation → admin budget with human review (bounded by
  policy, not by user behavior). Interactive assembly → PLUS (per-query cost sits in a paid
  tier). Adaptive planning → PRO (recurring cost sits in the highest tier). Nothing
  cost-bearing is left unmetered; nothing cost-free is metered.

### 2.3 Adoption argument

- **Free reuse maximizes the network effect of published content.** The value of the
  curation economy scales with adoptions per generated artifact. Friction on adoption
  shrinks the denominator that makes curation cheap.
- **It makes FREE "feel useful, not broken"** (pricing.md messaging rule) for the exam-prep
  segment specifically — a FREE ALE/PNLE/LET candidate gets a real, complete review plan on
  day one. That is the strongest top-of-funnel story the product has ever had for its top
  three buckets.
- **The upgrade path gets stronger, not weaker.** The worry — "if reuse is free, why pay?" —
  inverts in practice: reuse cannot cover the learner's *own* notes (personal material still
  requires metered generation), cannot *converse* (PLUS), and cannot *adapt* (PRO). Adopted
  content also feeds Quick Review / Challenge Quiz / Adaptive Practice consumption, all of
  which stay on their existing per-plan quotas — so free adoption *increases* pressure on
  the quotas that already drive upgrades. Reuse is the free sample that makes the metered
  and tiered capabilities worth paying for.

### 2.4 Risks and mitigations (principle-level; numbers are owner's)

- **Adoption abuse / DB bloat.** A pathological account could mass-adopt. Marginal cost is
  storage, not LLM, so the exposure is small — but if the owner wants a guard, a generous
  per-period adoption cap (a new counter, not the Study Pack counter) preserves the
  principle ("reuse is not metered *as generation*") while bounding abuse. **Whether such a
  cap exists, and its number, is owner-decided.**
- **Perceived unfairness to generators.** A learner who generates from own notes pays quota;
  a learner who adopts does not. This is by design (cost-based metering), but pricing copy
  should present adoption as a feature of the library ("official review sets — free to
  adopt"), not as a loophole.
- **Quota-meaning drift.** Once ratified, the principle must be enforced in one place —
  RECOMMENDATION: `FeatureGateService` remains the single source of truth and the copy
  spine simply never calls a quota decrement, mirroring the existing "quota increments only
  after a successful Study Pack is persisted" rule (a copy is not a generation success
  event). No parallel gate logic in the Assistant.

## 3. Owner must decide (deliberately NOT set here)

Every lever below was intentionally left open so the owner ratifies rather than inherits a
guess. Existing numbers (PLANS.md / pricing-config) are untouched by this document.

1. **Ratify or reject the tier split itself** — FREE adopt / PLUS conversational assembly /
   PRO adaptive planning is a recommendation, not a fait accompli.
2. **All prices** — no price point of any kind is proposed or changed here.
3. **PLUS conversational-assembly allowance** — whether Assistant assembly sessions are
   unlimited on PLUS, share a quota with Ask Companion, or get their own monthly counter;
   and every number involved.
4. **PRO adaptive-planning allowance** — whether adaptive re-planning is unlimited on PRO
   or counter-limited (and the number), and whether it shares the Adaptive Practice counter
   or gets its own.
5. **Whether FREE gets any taste of the PLUS layer** — e.g. a small number of assembly
   sessions as a conversion hook (the Adaptive Practice "taste" precedent: Free 3/month).
   Recommended pattern exists; whether to use it and the number are the owner's call.
6. **Adoption cap** — whether free adoption gets any per-period anti-abuse cap at all, and
   if so the number per plan.
7. **The exact free/paid line inside "planning UI"** — e.g. whether a static "suggested
   order" view derived from curriculum position (deterministic → FREE by the boundary rule)
   ships at all tiers, or is held back as paid-tier surface area despite being rule-based.
   The boundary rule recommends FREE; the owner may choose to keep specific surfaces paid.
8. **Admin generation budget** — size, period, and prioritization policy of the Curator
   budget (including whether `demandCount` from paid users weighs more than from FREE users).
9. **Pass durations and checkout mechanics** — untouched; any interplay (e.g. what happens
   to adopted content or adaptive plans when a pass lapses — the existing
   `PASS_DATA_PERMANENCE_NOTE` suggests content stays, adaptivity stops) needs the owner's
   explicit confirmation before any copy is written.
10. **Pricing-page presentation** — whether Smart Review Planning appears as a named row in
    the comparison table at launch, and its plan-card copy (via `plans.ts` /
    `getUpgradeCtas`, per existing rules).
11. **Profile-type overrides** — whether Teacher/Professional profiles get adjusted
    allowances on any layer (the Teacher DOCX-export override is precedent that such
    overrides exist; whether one applies here is not assumed).
