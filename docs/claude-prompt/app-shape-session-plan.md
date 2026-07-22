# Session Plan — App Shape, App-Like UI, and Companion Authenticity

> **Purpose.** Three independent questions run through Fable, planning-only, to source candidate ideas for a
> future release — explicitly *not* about conversion (v0.44.0 and the separate conversion-audit backlog
> already own that). Unlike `fable-smart-review-audit-session-plan.md`, these three sessions have no
> anchor/fan-out dependency on each other — each was run standalone, in parallel, in one pass. Fable
> produced planning text only; nothing here has been scoped, kicked off, or committed to a release.

---

## The three questions

| # | Question | Prompt | Fable's output |
|---|---|---|---|
| 1 | What features could deepen the app's **shape** (not conversion) — how Notes, Study Packs, Review Sets, Companion, and Progress compose as one system? | `app-shape-prompts/01-app-shape-features.txt` | `app-shape-out/01-app-shape-features.md` |
| 2 | How could the five highest-traffic pages (Note Detail, Review Sets list, Review Set detail, Private Library, Public Library) read as an **app** rather than a website/documentation? | `app-shape-prompts/02-app-like-ui.txt` | `app-shape-out/02-app-like-ui.md` |
| 3 | How could the **Learning Companion**'s authored content (and its AI-assist draft) be grounded in real learner experience data instead of reading like generic AI prior knowledge? | `app-shape-prompts/03-companion-authenticity.txt` | `app-shape-out/03-companion-authenticity.md` |

Hard constraints repeated in all three prompts (Fable starts cold each time): planning only, no pricing/paywall/quota/conversion changes, "curation, never generation" stays locked, the 5-mode quiz contract stays locked, build on shipped architecture rather than reinventing it. Session 3 additionally locks "authored once, served static, zero per-view cost" — no per-learner runtime personalization, ever.

---

## Synthesis — what's actually worth picking up

*Classifications are Fable's own, using this repo's `docs/skills/roadmap-feature-audit.md` definitions. Nothing below is scoped to a version; this is a candidate list for a future `/kickoff`, not planned scope.*

### Core Feature candidates (need their own kickoff + scoping pass)

- **Companion Live Milestones** — curator-authored milestone thresholds rendered live against real ConceptHealth/Progress data. Directly closes the deferred Timeline/Checklist gap named in ROADMAP.md's Companion section. *(Session 1)*
- **Unified Next-Step Resolver** — one backend-resolved "what should this learner do next" contract consumed by Dashboard, Review Set detail, and the post-session result screen, replacing four independently-computed answers that can disagree today. *(Session 1)*
- **Concept-to-Note Back-Annotation** — a "needs attention" concept strip on Note Detail so "Revisit Note" tells the learner *what* to revisit. Smaller than the other two Core items; closes the loop's "Improve" stage. *(Session 1)*
- **Mobile bottom tab bar** — persistent app-shell navigation (Dashboard / Library / Review Sets / Public Library), flagged as the single strongest "app" signal available, but explicitly its own roadmap item (keyboard-overlap, safe-area, per-route-visibility concerns) — not a polish-pass add-on. *(Session 2)*
- **Companion authoring "Struggle Map" evidence panel** — an ADMIN-only aggregate view (struggling/due/mastered concept counts, most-missed questions + dominant wrong answer, anonymized feedback excerpts) shown beside the authoring modal and fed into the v0.42.0 AI-assist prompt as grounding context. This is the direct fix for the "reads like generic AI" complaint. **Prerequisite, corrected 2026-07-12:** Fable flagged an adoption-provenance link as "genuinely new infrastructure" needing a go/no-go — checked directly against code and this overstates it. `copiedFromNoteId`/`copiedFromUserId` already exist on `NoteEntity`; what's actually missing is a reverse lookup (note → containing collections), which is a new repository query, not a new entity or migration. See the correction note atop `app-shape-out/03-companion-authenticity.md` for the full trace. The prerequisite is real but cheap — this lowers the bar for picking this item up. *(Session 3)*

### Polish candidates (cheap, high-leverage, no new entity)

- Shared note-card press feedback (`motion-pressable`/`motion-surface` on `shared-note-card.tsx`) — one edit, benefits Private Library, Public Library, and Dashboard at once. Fable's single highest leverage-to-cost item. *(Session 2)*
- Skeleton-first initial load reusing existing GENERATING placeholders (Note Detail) and existing skeleton components (Review Sets list). *(Session 2)*
- Sticky search/filter toolbar on Private Library and Public Library (same treatment, build once). *(Session 2)*
- Collapse-by-default for Note Detail's Recent Sessions / Performance Overview, reusing the exact `CompanionDisplayCard` "View Full Guide" pattern. *(Session 2)*
- Result-Screen Companion Bridge — surface a session's relevant curator-authored Common Mistakes excerpt on the post-quiz result screen. *(Session 1)*
- Review Set filter facet in the Private Library (single-select, in the existing More Filters sheet). Borders Core Feature — watch scope if it grows beyond a filter param. *(Session 1)*
- Feedback/issue-report digest scoped to a Review Set, and an evidence-snapshot staleness flag mirroring `companionMayBeOutdated`. *(Session 3)*

### Future Enhancement (queued, needs more design work first)

- Horizontal swipe between Note Detail tabs (gesture + scroll-conflict edge cases). *(Session 2)*
- Minimal PWA manifest (installability/home-screen icon) — explicitly recommended to come *after* the interaction work above, not instead of it; offline caching is a separate initiative and should not ride along. *(Session 2)*
- Note Lineage View (needs a data-model decision on persisting copy lineage). *(Session 1)*
- Subject Plan Concept Coverage Map, and the Weak-Concept → Official Note Refresher that depends on it (needs concept-normalization work first). *(Session 1)*
- Subject-level pacing/drop-off signal for Companion authoring; retro-mining historical session data (forward-looking analytics event should ship first; do not bulk-scan `sessionState` JSON as the primary source). *(Session 3)*

### Explicitly out of scope (Fable's own guardrails — worth keeping visible)

- Any per-learner runtime personalization of Companion content (reordering FAQ, per-viewer Common Mistakes, ConceptHealth-driven Mentor Tip swapping) — that's the already-designed gated PRO Personalization tier, not this. *(Session 3)*
- New "adaptive" Mentor Tip surfacing conditions — progress-mechanical conditions only (date/subject-count), never profile-adaptive. *(Session 3)*
- Auto-refreshing or auto-republishing Companion content when evidence shifts — a staleness *flag* to a human, never an automated write. *(Session 3)*
- Exposing learner identities or raw feedback PII in the evidence panel or the AI-assist prompt — minimum-n suppression and PII stripping are non-negotiable. *(Session 3)*
- A 6th quiz mode, or touching quiz-taking screens themselves, or the EXAM_MODES.md contract, anywhere in this list.
- Any pricing, paywall, quota, or conversion-funnel change, anywhere in this list.

---

## Recommended next step

None of this is scoped yet. Before any `/kickoff`, run this through `docs/skills/roadmap-feature-audit.md`'s classification against the *actual* next release's theme/priorities. The Session 3 provenance-link prerequisite was checked against code (2026-07-12) and is cheaper than originally flagged — `copiedFromNoteId`/`copiedFromUserId` already exist; only a reverse lookup query is missing — so it no longer blocks committing to the evidence-panel work the way a from-scratch lineage table would have. Also note (same date, checked against `RELEASES.md`): Session 1's Milestones idea is genuinely new scope, not a completion of the old deferred Timeline/Checklist item, which v0.42.0 already closed by placement.

**Correction / re-prioritization, 2026-07-15:** a post-v0.48.0 Fable strategy checkpoint (`docs/claude-prompt/retention-diagnosis-session-plan.md`'s "Strategy checkpoint" section) re-evaluated the Core Feature tier through a retention-first lens. Verdict: Companion Live Milestones, Concept-to-Note Back-Annotation, and the Struggle Map evidence panel are held indefinitely — none touch the proven retention constraint. The **Unified Next-Step Resolver is reframed as infrastructure for the deferred H5 retention direction** (pre-decided-return-action), not standalone app-shape work — pick it up only if/when H5 is scoped, not on its own. The **mobile bottom tab bar is conditional on a device-mix data pull** (mobile vs. desktop session share) that's part of the same checkpoint's action list — promote it if that pull shows heavy mobile usage. Full reasoning in the linked section; don't re-derive from scratch.
