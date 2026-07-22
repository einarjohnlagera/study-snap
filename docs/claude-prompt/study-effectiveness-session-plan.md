# Session Plan — Study Effectiveness, UI Polish, and Pricing Fit

> **Purpose.** A single Fable pass, planning-only, explicitly *not* about retention (the retention-diagnosis track already owns that, and is mid-experiment — see `retention-diagnosis-session-plan.md`, gate closes 2026-07-29) and *not* about acquisition/SEO (`seo-strategy-session-plan.md` owns that). This session asked: is NoteLib actually good at helping someone study once they're already using it, does the UI live up to the "premium guided experience" positioning the Companion feature is meant to establish, and does the Free/Plus/Pro tier structure still make sense given what's actually built. Nothing here has been scoped, kicked off, or committed to a release as of this writing.

---

## The question

| # | Question | Fable's output |
|---|---|---|
| 1 | Flow gaps: is there a missing step in Capture → Generate → Review → Improve that makes a study session itself more effective (not just more likely to recur)? | `study-effectiveness-out/01-study-effectiveness-ui-pricing.md` §1 |
| 2 | UI polish: across the five high-traffic surfaces and the quiz result screens, what undercuts the "premium guided learning experience" positioning? | same, §2 |
| 3 | Pricing fit: given the actual feature set (locked 5-mode hierarchy, ungated question formats, free Companion, one-time-pass framing, non-binding Free quota), do the Free/Plus/Pro tier boundaries still make sense? | same, §3 |

Hard constraint given to Fable: ignore retention-trigger mechanics entirely (no digests, no nudges, no streaks, no "bring them back" hooks) — that's out of scope here by design, not an oversight. Also explicitly told not to re-derive Smart Review Planning (separate, already-scoped, parked initiative — see `fable-smart-review-audit-session-plan.md`).

---

## Synthesis — what's actually worth picking up

*Classifications are Fable's own, using this repo's `docs/skills/roadmap-feature-audit.md` definitions. Nothing below is scoped to a version; this is a candidate list for a future `/kickoff`, not planned scope.*

### Core Feature candidates

- **Link missed/weak concepts on quiz result screens directly to their Study Pack explanation** — today result screens name a missed concept but the only paths forward are "Review Answers" / "← Back to Note" / a new session, with no one-click path to the actual explanation. Reuses existing per-concept explanation data and the already-shipped `#full-notes` hash-anchor pattern. *Fable's #1 overall pick* — cheapest, most direct answer to "does this help you learn right now," no dependency on the in-flight retention/acquisition experiments. *(§1, item 1)*

### Polish candidates (cheap, high-leverage, no new entity)

- **Fix the Note Detail reading-flow vs. tab-order mismatch.** `docs/features/note-detail.md` documents the intended order as `Summary → Full Notes → Key Concepts → Quiz`; the actual tab order is `Summary → Key Concepts → Quiz → Full Notes` — a learner can reach Quiz having never seen their own source material. Either reorder the tabs or add a pre-Quiz nudge back to Full Notes. Has both a flow dimension and a visual-IA dimension (the tab order visually implies a flow the spec itself says isn't real) — fix once, benefits both. *(§1 item 2, §2 item 3)*
- **Surface Study Pack scope up front** (concept count, quiz length, rough review time) on the note card / Summary tab, before the learner commits to a session — pure surfacing of counts the backend already has. *(§1, item 3)*
- **Sort Key Concepts tab by readiness** (due/not-started first, mastered last) instead of generation order — frontend sort over already-fetched concept-health data. *(§1, item 4)*
- **Per-question "why am I being asked this" tag in Adaptive Practice** (e.g. "Reviewing: Ohm's Law — missed last time") — personalization currently only visible at the pre/post screens, not inside the session itself. *(§1, item 5)*
- **A layout pass on Review Set Detail and the quiz result screens.** Both have accreted into a stack of independently-shipped, individually-well-reasoned cards (on Review Set Detail: Hero → TodaysFocusCard → Mentor Tip → Progress → GuidanceTip → Companion; on results: score → weak concepts → PostSessionNextStep → confidence input → WeeklyPacingEchoCard → CompanionResultBridgeCard) that's never been looked at as one coherent moment. Presentation-only, no logic change; medium cost since it touches 3 result-screen variants plus the detail page. *(§2, item 1)*
- **A collapsed-state teaser for the Companion card** — one line of the Overview text visible before expanding, so the feature built to deliver "premium guided experience" isn't also the one thing on its own page requiring an extra click to discover. Keeps the existing collapse decision (made for good reason — reduces documentation-feel); just fixes discoverability. *(§2, item 2)*

### Future Enhancement candidates (real cost or needs a decision/data first)

- **On-demand "explain this differently" for a twice-missed concept** — real LLM spend, and overlaps the not-yet-built "Ask Companion" Plus-tier idea from the Companion monetization roadmap (`docs/features/companion.md`). Needs a product decision on which surface owns "I still don't get it" before either gets scoped. *(§1, item 6)*
- **Instrument before touching the Plus+-gated review-timing detail** (`Due — Nd ago`). Given Free quota is confirmed non-binding, this near-zero-marginal-cost gate is plausibly the highest-leverage "does this actually convert anyone" question in the whole Free tier — no data cited either way yet. *(§3, item 2)*
- **Flagged, not analyzed:** whether Free's 3/mo share-link quota is quietly constraining the organic distribution the Exam Hub/SEO work is actively trying to grow. Sits at the intersection of pricing and the acquisition workstream — explicitly out of scope for this session, raised only so it isn't lost. *(§3, item 4)*

### Pricing-structure finding (not a feature, a monetization decision)

- **Move Difficulty Selection from Pro-only to Plus.** Since Free quota is confirmed non-binding, Plus currently has no qualitative reason to exist — all four real unlocks (Board Exam, Long Exam, Interview Practice, Difficulty Selection) sit at Pro, so a motivated Free user has no reason to stop at Plus. Difficulty Selection is the cheapest of the four to deliver (a prompt parameter, not a new generation mode) and least tied to the compute-heavy exam-simulation value prop the other three genuinely carry. Low engineering cost, real pricing-strategy cost — needs sign-off as a monetization decision, not a config flip. *Fable's #2 overall pick.* *(§3, item 1)*
- **No change recommended:** Companion staying free on every tier — confirmed correct as designed, near-zero marginal cost, correctly reserves compute-costly personalization (Ask Companion / adaptive guidance) for paid tiers. *(§3, item 3)*

---

## Status

Not yet scoped to any release. See `docs/product/ROADMAP.md`'s Backlog Index for current status and next action.
