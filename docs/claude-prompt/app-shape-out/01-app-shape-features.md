# Fable Output — App-Shape Feature Ideas (not conversion)

> Raw output from running `docs/claude-prompt/app-shape-prompts/01-app-shape-features.txt` through Fable. Unedited except for this header.
>
> **Correction (2026-07-12, Claude Code, verified against RELEASES.md):** idea #1's header frames
> Milestones as "the deferred Timeline/Checklist, done the mandated way," implying it closes an open
> backlog item. It doesn't — checked `RELEASES.md`'s v0.42.0 Shipped section directly: "Timeline/
> Checklist live-feature embeds — satisfied by placement, no code change... no separate embed was
> built" — that deferred item was formally closed in v0.42.0 by *not* building a literal Timeline/
> Checklist at all (card adjacency substituted for embedding). Milestones is a genuinely **new**
> feature idea that happens to live in the same spirit as the old deferred item, not a completion of
> existing backlog scope. The Core Feature classification and the idea itself are unaffected — only
> the "done the mandated way" framing is corrected, so a future kickoff doesn't scope this thinking
> it's closing a known TODO.

## Ideas

*Framing note: every idea below composes pieces that already exist — no new top-level entity except where explicitly flagged, no 6th quiz mode, no generation served to a learner without curator publish, no pricing/quota/conversion changes. Classifications use the definitions in `docs/skills/roadmap-feature-audit.md`.*

---

### 1. Companion Live Milestones (the deferred Timeline/Checklist, done the mandated way)

Let the Official curator author **milestone definitions** on a Companion — e.g. "By week 4: Fundamentals of Nursing readiness ≥ 70%", "Before exam week: zero red concepts in Pharmacology" — and render them **live** against the learner's actual ConceptHealth readiness and the shipped v0.40.0 weekly countdown. The curator authors the *thresholds and narrative*; the *state* (met / behind / ahead) comes from the learner's real Progress data. This is the first surface where the guidance layer (Companion) and the readiness layer (Progress) actually compose instead of sitting side by side — the Companion stops being a static handbook and becomes a plan the learner is measurably inside of.

- **Extends:** ROADMAP.md line 90's explicit contract — "Study Timeline and Final Checklist are explicitly deferred and must NOT be static prose — when built (v0.42.0+) they link the already-shipped, already-free **live** features (the v0.40.0 weekly countdown and readiness), never re-author them." Also the Guided Learning success criterion: "premium guided learning experience rather than a collection of notes" (ROADMAP.md line 77).
- **Classification:** Core Feature (future release — it's the already-named next Companion increment).
- **Locked rules:** Curator-authored, statically served — no runtime LLM to render a milestone (Companion anti-drift, ROADMAP.md line 97). Milestones travel on adopt per the v0.31.0 snapshot-copy rule, same as the rest of the Companion. JSONB Companion column extension, no new entity.

### 2. Concept-to-Note Back-Annotation (closing the "Improve" stage)

The loop is Capture → Generate → Review → **Improve** → Make a Copy → Repeat, but today weak-concept data flows *forward* (Focus Areas → Adaptive Practice / "Revisit Note") and never *back onto the note itself*. Put a "Needs attention" concept strip on Note Detail: the note's own weak concepts from ConceptHealth, shown where the learner actually edits. "Revisit Note" currently drops the learner at the top of the note with no indication of *what* to revisit — this tells them, and turns note editing into the improvement action the loop names. Read-only over existing ConceptHealth data; no LLM, no new tracking.

- **Extends:** Note-Centric Design in `docs/skills/ux-product-review.md` — "study history trace back to the note, not just the session" and "the note is the primary entity"; direct continuation of the Focus Areas "Revisit Note" behavior in `docs/features/dashboard.md`.
- **Classification:** Core Feature (small — one backend read path + Note Detail section).
- **Locked rules:** Must never suggest or trigger regeneration ("never auto-regenerate", CLAUDE.md versioning rule). Signal-only; the learner edits, the system never rewrites.

### 3. Unified Next-Step Resolver (one "what next" engine instead of four)

Dashboard Continue Studying, Focus Areas, the Review Set detail Primary Action (v0.41.1), and the Coach dynamic layer (v0.43.0) are four independently-computed answers to the same question: *what should this learner do next?* They can disagree — Dashboard says resume a Quick Review while the Review Set's Primary Action says start a different subject. Extract one backend-resolved recommendation contract (priority-ordered: unfinished session > red concept in primary Review Set > next Subject Plan step > new note) that Dashboard, Review Set detail, and the post-session result screen all consume. This is the same architectural move as `exam-mode-visibility.ts` and `FeatureGateService`: a single source of truth replacing scattered per-surface logic.

- **Extends:** `docs/features/dashboard.md` goal statement — "Dashboard is a guidance surface... help users decide what to do next in the loop"; the Continue Studying rule "use the backend `resumeType` label directly — do not infer on the frontend" (already the pattern: backend resolves, frontend renders); ux-product-review anti-pattern "Learning loop broken by a dead end."
- **Classification:** Core Feature (structural — backend service + three consuming surfaces; Codex-scale).
- **Locked rules:** Recommendations only ever point at the 5 existing modes or note/Companion surfaces; respects `exam-mode-visibility.ts` per profile; no runtime LLM (deterministic rules over existing session/ConceptHealth data).

### 4. Result-Screen Companion Bridge (Common Mistakes, at the moment of a mistake)

Curators already author **Common Mistakes** and **Study Strategy** sections on Official Companions (v0.41.0's four sections) — but they're only readable on the Review Set page, decoupled from the moment they matter. When a session completes on a note that belongs to a Review Set with a Companion, surface the relevant curator-authored excerpt on the session result screen ("From your Review Set's guide: ..."). Matching can be dumb-simple in v1 (the note's Subject Plan → that subject's tagged Companion excerpts). This makes the guidance layer *reactive* without making it generative — the curator's words arrive exactly when a wrong answer makes them relevant.

- **Extends:** Emotional Hierarchy in `docs/skills/ux-product-review.md` — "the result screen reinforces progress, not just a score" / anti-pattern "score-only result screens with no clear next step"; the Coach dynamic layer precedent (v0.43.0) of contextualizing static Companion content.
- **Classification:** Polish (feature exists; this improves when it reaches the learner — no new data model beyond an optional subject tag on Companion sections).
- **Locked rules:** Serves only curator-published Companion content — "curation, never generation" holds by construction. No mid-exam coaching (result screen only, post-session — respects EXAM_MODES.md's no-interactive-AI constraint).

### 5. Note Lineage View (making "Make a Copy → Repeat" visible)

"Make a Copy" is the locked versioning action, but the product never *shows* the version chain — copies are orphaned siblings in the Library. Persist the copy-source link (it partially exists for public copies) and render a small lineage strip on Note Detail: "v2 of *Cardio Notes* — compare session history." The payoff is longitudinal: a learner who copied a note, improved it, and re-practiced can see readiness on v2 vs v1 — direct evidence that the Improve step worked. This turns the loop's last two stages from a convention into a visible product structure.

- **Extends:** The `Make a Copy` UI-terminology convention *(correction 2026-07-12: this specific quote is from AGENTS.md's "UI Terminology" section — "Make a Copy" as canonical term, "avoid... regenerate/overwrite flows" — not CLAUDE.md, which is cited correctly two lines below for a different rule)* and Note-Centric Design's "study history traces back to the note."
- **Classification:** Future Enhancement (valid, desirable, needs a data-model decision on lineage persistence — not current-release material).
- **Locked rules:** Owner self-copies still exclude generated content; public-note copies still include the linked Study Pack — lineage display must not "fix" either documented exception (CLAUDE.md versioning rule).

### 6. Review Set Facet in the Private Library

Library and Review Sets are currently parallel universes: the Library filters by Course/Program, Subject, Tags, readiness — but not by *journey membership*. Add a "Review Set" filter (single-select with search, in the existing More Filters sheet, exactly where Course/Program lives) so "show me my notes that are in my PNLE Review Set — which ones are still Draft?" becomes answerable. The faceted Stats Strip then automatically shows subject breakdown *within* the set. This is the cheapest real step toward the deferred Review-Set-Centric Navigation direction — it lets Review Sets become a lens on the Library without committing the nav reorg that's explicitly gated on the Primary concept proving out.

- **Extends:** `docs/features/library.md`'s entire filter architecture (URL-param state, More Filters sheet, faceted client-side Stats Strip — this is a new value in an existing pattern, not a new pattern); a deliberate non-committal precursor to ROADMAP.md lines 214–221 ("subject mastery becomes a facet of the Review Set").
- **Classification:** Polish bordering Core Feature (one new filter param + a membership lookup; no new entity).
- **Locked rules:** Filter state stays in URL params, never sessionStorage (library.md anti-drift note); filter label resolves through `getCollectionLabels` per profile, not hardcoded "Review Set" (ROADMAP.md line 226).

### 7. Subject Plan Concept Coverage Map (which notes teach which weak concept)

Within a Subject Plan, ConceptHealth knows the learner's weak concepts and each note's concepts — but nothing joins them. Build a coverage view on the Review Set detail page (extending the v0.41.1 This-Set Study Dashboard): for each red/yellow concept in this set, which notes in the set cover it — and, critically, which weak concepts are covered by *no* note (a genuine gap in the learner's library, with "Create a note" / "Find in Public Library" as the next step). Concept labels join through the same shared-normalization approach the Library already uses so "Fluid & Electrolytes" and "fluids and electrolytes" don't split.

- **Extends:** v0.41.1 Review Set detail hierarchy (Readiness → Guidance already live on that page, ROADMAP.md line 212); the label-normalization precedent in `docs/features/library.md` ("equivalent variants should collapse through shared normalization"); ux-product-review's "every dead end needs a clear next step" — an uncovered weak concept is currently an invisible dead end.
- **Classification:** Future Enhancement (needs concept-normalization design work first — the "requires significant design work before implementation" deferral trigger in roadmap-feature-audit.md).
- **Locked rules:** Deterministic joins over existing ConceptHealth data — no runtime LLM to compute coverage; gap remediation points to note creation or curator-published public notes, never to generating content for the learner.

### 8. Weak-Concept → Official Note Refresher (curated remediation bridge)

When Focus Areas surfaces a weak concept and the learner's own note on it is thin or nonexistent (the gap idea #7 detects), the current answers are "Revisit Note" or Adaptive Practice — both loops over the learner's *existing* material. Add a third path: suggest a matching **Official** (curator-published) public note covering that concept, using the Public Library's existing filter endpoint, with "Make a Copy" as the action so it enters their library and their loop. This is remediation via curation — the human-reviewed publishing pipeline becomes the recovery path for knowledge gaps, which is precisely the role "curation, never generation" implies it should play.

- **Extends:** The Community Notes dashboard section precedent (`docs/features/dashboard.md`: reuses `GET /notes/public?...`, no new endpoint); Public Library Strategy in ux-product-review.md *(correction 2026-07-12: the quoted phrase here wasn't literal text from that section — the real, closest line is "Does the CTA after engaging with public content invite creation, not just consumption?", which supports the same point)*; Focus Areas' existing note-resolution fallback chain.
- **Classification:** Future Enhancement (depends on concept↔public-note matching quality; Official corpus is still small, so ship after idea #7's normalization exists).
- **Locked rules:** Suggestion pool is strictly curator-published Official notes — "curation, never generation" is the feature's premise, not just a constraint. Copy semantics follow the shipped rule (public copies include the linked Study Pack). Must not become an upsell surface — it's a learning-loop bridge, and per this task's own constraint it carries no conversion framing.

---

**The shape argument in one line:** ideas 1, 3, and 4 make the *guidance layer* consume the *readiness layer* (Companion × Progress); ideas 2 and 5 close the loop's back-edge (practice data flowing back into notes and their versions); ideas 6, 7, and 8 make Review Sets a *lens* over the Library and the curated corpus rather than a parallel container — together they connect the five shipped layers into one system instead of five adjacent features.
