# Fable Session — Study Effectiveness, UI Polish, and Pricing Fit (raw output)

> Run 2026-07-22, single pass, fresh Fable session (no memory of prior NoteLib consultations). Explicitly scoped to exclude retention-trigger mechanics — that workstream is separately owned and mid-experiment (v0.48.0 cohort read closes 2026-07-29). Grounded in `docs/features/*.md` (note-detail, quiz, adaptive-practice, collections, companion, library, public-library, study-pack-generation) and `frontend/src/config/plans.ts` / `frontend/lib/pricing-config.ts`, not just the product snapshot handed to it. See `docs/claude-prompt/study-effectiveness-session-plan.md` for the prompt, constraints, and classified synthesis.

---

## 1. Flow

The pipeline is more sophisticated than it looks at first glance — `PostSessionNextStep` already does server-resolved weak-concept routing, Adaptive Practice already merges due + weak concepts, Quick Review already has a retry round. The gaps aren't in the scaffolding; they're in two specific places: (a) the loop from "you got this wrong" to "here's what to do about it right now, in this session" isn't fully closed, and (b) the product tells you almost nothing about what you're about to study until you're already inside it. Neither is a "bring them back" mechanic — both are about whether the current session actually teaches something.

None of the ideas below are retention plays — confirming that explicitly since it's the one thing the brief asked to filter for.

**Ranked candidates:**

1. **Link missed/weak concepts on result screens directly to their explanation, not just their name.** Today, Adaptive Practice/Challenge Quiz result screens name the weak concepts but the only paths forward are "Review Answers," "← Back to Note," or a new practice session — there's no one-click path from "you missed Glycolysis" to the actual explanation of Glycolysis. The Study Pack already generates per-concept explanations (Key Concepts) and the codebase already has the deep-link/anchor pattern for this (`#full-notes` hash-scroll, already shipped for public notes). This is the single most direct "does the product help you learn, not just get scored" fix available.
   *Core Feature — low/medium cost (reuses existing data + existing routing pattern, no new generation).*

2. **Note Detail's own reading-flow order isn't the tab order.** `note-detail.md` explicitly documents two different orders: the intended reading flow is `Summary → Full Notes → Key Concepts → Quiz`, but the actual tab order is `Summary → Key Concepts → Quiz → Full Notes`. That means a learner can reach Quiz having only seen an AI summary and AI-extracted key concepts — never the source material they wrote or uploaded. There's a "View Full Notes →" CTA inside Summary, but nothing before Quiz nudges a return to source.
   *Polish — trivial cost if it's just tab reorder; small net-new if you instead keep the tab order and add a one-line nudge before Quiz.*

3. **Surface Study Pack "scope" up front** — concept count, quiz length, rough review time — on the note card and/or Summary tab, before the learner commits to a session. Right now depth/coverage is invisible until you've clicked into every tab; a learner has no way to calibrate effort or trust "is this actually a complete pack" before starting.
   *Polish — low cost, pure surfacing of counts the backend already has.*

4. **Sort the Key Concepts tab by readiness** (due / not-started first, mastered last) instead of generation order. The due/mastery badges already exist per-concept (`quiz.md`); nothing currently spends the learner's limited attention where it's actually needed first.
   *Polish — low cost, frontend sort over already-fetched concept-health data.*

5. **Per-question "why am I being asked this" tag in Adaptive Practice** (e.g. "Reviewing: Ohm's Law — missed last time"). Personalization currently only shows up in aggregate, at the pre/post screens — inside the session itself, a targeted question looks identical to a generic one.
   *Polish — low/medium cost.*

6. **On-demand "explain this differently" for a concept missed twice** — an alternate LLM re-explanation, distinct from another quiz attempt. This is real, but it's compute-cost and roadmap-adjacent: it sits right next to the already-documented (not-yet-built) "Ask Companion" Plus-tier idea in the pricing brief. Don't build both independently — decide which surface owns "I still don't get it" before scoping either.
   *Future Enhancement — medium/high cost, real LLM spend, needs a product decision first.*

---

## 2. UI Polish

The five surfaces are not uniformly polished. Library (private + public) shows heavy, recent, deliberate iteration in the docs — single-excerpt card fix (v0.50.2), stats strip, saved filters, an explicit badge-tier classification system. Not manufacturing a finding there. The real gap is concentrated in two places where features have shipped correctly one at a time, release after release, without ever getting a layout pass as a whole: **Review Set Detail** and the **quiz result screens**.

1. **Card accretion on Review Set Detail and quiz result screens.** Trace what's stacked on Review Set Detail today: Hero → `TodaysFocusCard` → a Mentor Tip → compact Progress (with countdown) → a post-adopt `GuidanceTip` → the Companion card (collapsed). On the quiz result screens: score → weak-concepts block → `PostSessionNextStep` actions → confidence input → `WeeklyPacingEchoCard` → `CompanionResultBridgeCard` → review-answers link → back-to-note. Each block is well-reasoned in isolation — the individual shipping notes are careful — but nobody has stepped back and asked "does this read as one coherent moment, or as six independently-shipped widgets glued together." For a product explicitly trying to feel like a "premium guided experience," a result screen that reads as a stack of cards competing for attention undercuts that positioning more than any single component does.
   *Polish — presentation-only, no logic change; medium cost because it touches 3 result-screen variants plus the detail page.*

2. **The Companion is the most hidden thing on the page it's supposed to anchor.** It's collapsed by default behind a plain text+chevron toggle, while the ordinary note-list sections above it default open on desktop. The feature docs themselves acknowledge the tension ("a first-time learner has no way of knowing mentor advice/FAQ/common-mistakes content exists behind it") and mitigated it once with toggle copy — but the underlying problem is unresolved: the literal feature built to deliver "premium guided experience" is the one thing on the page requiring an extra click to even discover. A one-line teaser of the Overview text while collapsed (not the full card — just enough to signal there's something there) would fix the discoverability problem without reversing the collapse decision (which was made for good reasons — reducing documentation-feel).
   *Polish — low/medium cost, presentational only.*

3. *(Cross-reference, not a new item)* — the Note Detail tab-order/reading-flow mismatch from the Flow section above also has a visual-IA dimension: the tab order visually implies a flow the product's own spec says isn't the intended one. Fix once, benefits both angles.

---

## 3. Pricing Fit

The structural question is sharper than "are the numbers right": given Free quota is confirmed non-binding, **Plus's entire value proposition is currently "more of a thing you don't need more of."** Every one of the four genuinely qualitative gates — Board Exam, Long Exam, Interview Practice, Difficulty Selection — sits at Pro, not Plus. A Free user who never feels the pinch of Free's numeric limits, and who wants any of the four things actually worth paying for, has zero reason to stop at Plus; they go straight to Pro. That's consistent with Plus being structurally weak by design, not by mistake, but it's worth surfacing because "no one buys the middle tier" is usually invisible until someone looks at the funnel.

1. **Move Difficulty Selection from Pro-only to Plus.** Of the four Pro-exclusives, it's the cheapest to deliver (a prompt parameter, not a new generation mode) and the least tied to the compute-heavy, licensure-exam-anchored value prop that Board/Long/Interview genuinely carry — the same segment the whole Exam Hub/ALE-PNLE-LET-CPALE strategy targets. Giving Plus this one qualitative unlock gives it an actual reason to exist between Free and Pro, without touching the three modes that should stay scarce (they're both expensive to generate and the real reason someone pays for Pro specifically).
   *Tier-boundary change — low engineering cost (gating config only), real pricing-strategy cost (this is a monetization decision, not just a flag flip — model it before shipping).*

2. **Review-timing detail (`Due — Nd ago`, gated Plus+) is worth testing before touching, not changing outright.** It's the one place in the product where a real, near-zero-marginal-cost capability — not a quota number — is withheld from Free. Given Free quota is confirmed non-binding, this field is plausibly the single highest-leverage "does this gate actually convert anyone" question in the whole free tier, and there's no data cited either way here.
   *Future Enhancement — needs instrumentation/experiment data before any change.*

3. **Companion staying free on every tier is the right call — no change needed.** It's a near-zero-marginal-cost hook at the base tier, and the documented future split ("Ask Companion" grounded Q&A at Plus, adaptive guidance selection at Pro) correctly reserves compute-costly personalization for paid tiers while keeping the static authored layer universal. Flagging only because the brief asked directly — this is a confirmation, not a recommendation to act on.

4. **Explicitly flagged and not analyzed further:** whether Free's share-link quota (3/mo) is quietly constraining the organic distribution the Exam Hub/SEO work is actively trying to grow. This sits at the exact intersection of pricing and the acquisition workstream flagged as out of scope and mid-experiment (cohort read closes 2026-07-29) — raising it so it isn't silently lost, not proposing a change here.

---

## Top recommendation if the team can only act on one thing

**Flow #1 — link missed/weak concepts on result screens to their explanation.** It's the most direct answer to the actual question this consultation was framed around ("is NoteLib good at helping someone study, once they're using it"), it's cheap because it reuses data and routing patterns that already exist, it has no dependency on the in-flight retention/acquisition experiments, and it reinforces the Companion's own "premium guided" positioning by making the product visibly smarter within a single session rather than only across sessions.

**Second pick — Pricing #1 (Difficulty Selection to Plus).** It's a near-free engineering change addressing a real structural gap (Plus currently has no qualitative reason to exist), independent of the other two workstreams, though it needs a real pricing-strategy sign-off before shipping, not just a config flip.
