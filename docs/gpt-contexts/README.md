# docs/gpt-contexts/

Paste-ready context documents for handing NoteLib context to an external GPT session. These are
snapshots/briefs, not source-of-truth for implementation — for that, always defer to `AGENTS.md`,
`RELEASES.md`, `docs/product/ROADMAP.md`, and the relevant `docs/features/*.md`.

**The product context is a core brief plus modules (split 2026-08-11).** `GPT_CONTEXT.md` used to carry everything and had grown to ~27k tokens — paid on every session before the conversation started, and mostly irrelevant to any single one. Paste the core **always**; add only the modules that match the conversation. **A module is never a substitute for the core.** `GPT_CONTEXT.md`'s own "Which modules to paste" table is the routing guide.

| File | Use for |
|---|---|
| `GPT_CONTEXT.md` | **Core brief — paste first, every time.** App model, current release, the retention constraint, the activation funnel, the redesigned onboarding flow, the September checkpoint calendar, product model, profile types, non-negotiable rules, working agreement. Enough on its own for onboarding, activation, retention and positioning conversations. Update on every version ship or roadmap shift. |
| `REVIEW_SET_SHAPING_CONTEXT.md` | Module: Review Set structure (plan → section → note), the four metadata axes, the closed Domain Context vocabulary and its two hard constraints, reuse-before-authoring costs, the over-inclusive program-tag warning, and the required TSV output block. Paste when designing or rebuilding a board-exam Review Set. |
| `QUIZ_AND_PRACTICE_CONTEXT.md` | Module: the five-mode quiz contract, exam simulation, practice mechanics, and the mastery-integrity rules. Paste for quiz or exam-mode work. |
| `SURFACES_AND_FEATURES_CONTEXT.md` | Module: feature surfaces screen by screen, plus the Note Collections and Learning Companion vision/locked rules. Paste when the conversation is about a specific surface. |
| `STRATEGY_AND_ROADMAP_CONTEXT.md` | Module: Company Redefinition, gated/ungated roadmap candidates, and condensed release history. Paste for "what should we build next" and sequencing questions. |
| `MONETIZATION_CONTEXT.md` | Module: plans, pricing, payments, paywalls, checkout. Paste for monetization work. |
| `DECISION_HISTORY_CONTEXT.md` | Module: questions already settled (and a few still open), each with its reasoning. **Paste before reopening a question** — it exists to stop re-litigating decided things. |
| `NOTES_AND_COLLECTIONS_CONTEXT.md` | Module: Note fields, subject/courseProgram taxonomy, Bulk Generate metadata, and how Note Collections (Study Plans) vs. query-filtered groupings (Exam Hub, Public Library, Saved Filters) work. Paste when the conversation is specifically about Library/notes/collection structure. |
| `NOTELIB_PRODUCT_DECISIONS.md` | Standing role/instruction prompt for a GPT acting as long-term PM/UX strategist. |
| `FACEBOOK_GROUP_STRATEGY.md` | Facebook-group-specific organic growth strategy (June 2026). Partially superseded in substance by `docs/agents/gpt/NoteLib Marketing.md`'s §4 workflow and §6 audience strategy — kept as historical record, not deleted. |
| `NOTELIB_SOCIAL_CONTEXT_v1.0.md` | Content format and writing style rules for social posts. **Carries a "SUPERSEDED 2026-10-20" banner** — its 3-deliverable structure and `/collections` CTA conflict with the locked 4-deliverable workflow and `/explore` CTA in `docs/agents/gpt/NoteLib Marketing.md` §4/§13. Body kept as historical record, not rewritten. |
| `GPT_MARKETER_PROMPT.txt` | Session-starter prompt for a marketing-focused GPT session. **Carries a "SUPERSEDED 2026-10-20" banner** — its load list still names `PROJECT_CONTEXT.md` and the now-deleted `MARKETING_STRATEGY.md`, and its post structure conflicts with the locked workflow. Use `docs/agents/gpt/NoteLib Marketing.md` §18's opening prompt instead. |

**Marketing context moved 2026-10-20.** `MARKETING_STRATEGY.md` and `NoteLib_Marketing_Strategist_Context_v2.md`
(formerly listed here) were retired and consolidated into **`docs/agents/gpt/NoteLib Marketing.md`** —
the current canonical marketing-strategist context, mirroring `NoteLib Product UX.md`'s pattern. Use that
file, not this directory, for a fresh marketing session.
