# NoteLib — Decision History

> **Module — not a standalone brief.** Paste `GPT_CONTEXT.md` first; this file assumes it.
> Paste this module when the conversation is about **a question that may already have been settled — read before reopening one**.
> Last updated: v0.72.1 - 2026-08-11

---

## Slice 3 — Program Family expansion: RATIFIED 2026-08-05, SHIPPED in `v0.71.0`

**All four decisions are closed.** Do not re-open them; they are binding in `docs/architecture/ADR-001-canonical-knowledge-architecture.md`, and the shipped behaviour is recorded under `RELEASES.md` v0.71.0 → Slice 3. (The working decision sheet that produced them, `20-slice-3-family-expansion-decision-sheet.md`, was removed 2026-08-06 once the slice shipped and the rulings were absorbed into the ADR; recover it from git history if the reasoning is ever needed.)

1. **The catalog represents *valid applicability*, not curriculum coverage.** It answers *"who can legitimately study this note?"*; **Review Sets** are what communicate completeness. **The catalog still follows curriculum — a program simply no longer needs a complete Review Set to earn an entry, only legitimate canonical notes applicable to it.** Pre-seeding every PRC engineering program is **rejected as premature**; growth is incremental and demand-driven by authoring. Refines rather than reverses the `v0.70.0` *follow-not-lead* posture; the `Computer Science` / `Software Engineering` exclusions stand.
2. **Program Families stay intentionally dumb** — an authoring shortcut, never a curriculum engine. No hidden inference, no read-time applicability, no curriculum intelligence.
3. **Expansion fills in all family members.** No curated subsets; the author trims.
4. **Expansion is never subject-conditioned** — explicitly rejected, because it would make Families a second curriculum taxonomy and permanently couple Subject knowledge to applicability rules.

**Binding principle:** Program Families are a **productivity feature, not a curriculum feature.** They are deliberately allowed to over-select, because the Note's explicit Applicable Programs are always the source of truth. **The tripwire:** maintaining curriculum rules inside Program Families means the feature has exceeded its responsibility.

**Programs and Review Sets answer different questions — ratified, and it is the reason the catalog can grow on applicability without implying coverage:**

| Surface | Answers | Role |
|---|---|---|
| **Program** | *"What notes are applicable to me?"* | discovery |
| **Review Set** | *"What is my complete learning journey?"* | curriculum completeness |

**Coverage is emergent, not declared.** Every learner-facing program list derives from *notes*, not the catalog, so a program with no applicable notes is invisible to learners and the catalog is effectively author-facing. The residual risk is a **thin** shelf rather than an empty one — a program carrying a few shared foundational notes reads as a curriculum without being one.

**Agreed design direction, deliberately NOT built in slice 3:** communicate coverage **at the Program level** when a learner browses a Program with no dedicated Official Review Set — conceptually *"This Program currently contains shared foundational notes. A dedicated Official Review Set is still being developed."* **Rejected:** per-note coverage indicators and any new coverage metadata system; the completeness signal already exists and it is the Review Set. Needs its own scoping pass.

## Open Question — Explore's default tab (STILL unresolved as of v0.69.0, three releases later)

**The question:** `/explore` (shipped in `v0.67.0`, see above) shows a two-tab segmented control: "Review Sets" (curated, Official/admin-authored collections) and "Notes" (the original public-notes library). As shipped, **"Review Sets" is both the left-positioned tab and the default-selected tab** — a user landing on `/explore` sees curated content first, with the public notes library one click away. The product owner noticed this only after shipping and flagged: NoteLib's original identity was built around Public Library (organic, user-generated, browsable-by-anyone notes) — it was one of only ~3 main nav items for most of the product's history — and the new default may be quietly deprioritizing that identity in favor of the newer curated-content push.

**Opus's opinion (fresh-context agent, read the real code/docs before answering), given 2026-07-30:**
> **Recommendation: flip the default to Notes. Leave tab order alone.**
>
> The identity argument alone is the weakest reason to move a default — products are allowed to outgrow their origins — but the empirical case is strong and points the same way:
>
> 1. **Review Sets can't reward browsing today.** Producing more Official Review Sets is bottlenecked on the still-unscoped Curator pipeline. Only 4 Goals have ever been adopted, ~84% concentrated in two (LET Comprehensive, PNLE Core). Anyone outside those two programs hits an empty "Recommended" state on the default panel of a nav item called Explore.
> 2. **For `BOARD_EXAM` — the segment this whole redefinition targets — Review Sets is redundant as a default.** Onboarding already auto-adopts a qualifying Official Review Set and lands them on it; it is also the Dashboard Primary card; Explore already carries an Exam Hub pointer card. Review Sets has three other entry points. The public library now has exactly one.
> 3. **It biases the release's own checkpoint.** Defaulting to the panel that dead-ends for most users skews the `2026-09-13` engagement read that decides whether Explore was worth building. Flip now, while that window is effectively unstarted, rather than waiting until it contaminates the read.
>
> **Explicitly rejected:** profile-aware branching (adds a second variable to a checkpoint that needs one), and changing tab *order* (in a two-item control the selected panel is ~100% of visible content, so left-position precedence is close to noise).
>
> *Also flagged a hardcoded "Review Sets" tab label bypassing the profile-aware `getCollectionLabels()`, so a `STUDENT` saw a tab labeled "Review Sets" containing "Recommended Study Plans" — **this was fixed in `v0.67.1`** and is no longer open.*

**What's needed from you (GPT):** an independent second opinion on the same question — does defaulting `/explore` to Notes (public library) over Review Sets make sense, both on identity/positioning grounds and against the concrete adoption/engagement facts above? Agree or disagree with Opus's specific recommendation (flip default only, not order; not profile-aware; do it now before the checkpoint window contaminates). No code has been changed yet — this is genuinely still open.

---

## Resolved 2026-08-04 — a note DOES own its depth; the open part is authoring, not the model

Raised by the owner immediately after `v0.69.0` deployed, and **genuinely open** — do not treat the depth axis as settled.

**The owner's position:** notes should not carry a learner level at all. It originally existed so *quizzes* could be pitched correctly, i.e. it was a property of the *reader*. Course/Program arguably already implies difficulty. And it feels absurd that a learner who mis-set their profile to `College` could get a college-level quiz on a grade-school Algebra note. Two related proposals: give the program catalog a `learner_level_id` so depth is inherited rather than chosen; and/or carry the college→board-exam distinction on `ProfileType` instead.

**The counter-case:** the absurd scenario is precisely what the note's level *prevents* — with the note authored at Grade School, a College reader still gets a grade-school quiz, and removing the field makes depth fall back to the reader, which produces the complaint. "Program implies difficulty" also describes the model `v0.69.0` just retired (`Grade School` was a *level* sitting in a program field). And `ProfileType`'s `STUDENT` value spans four distinct depths, so it cannot carry depth without losing them. Where the owner is right: ordinary learners already never see the field, so the friction is Teacher/Admin-only — which argues for **inferring** the value (from the Review Set being authored into, then the author's profile) rather than deleting the axis.

**RESOLVED 2026-08-04 after a GPT pressure test.** The four-axis model **stands unchanged** and the note keeps its own depth: the reframed question is *"what is the canonical source of educational depth?"* and the answer is **the content itself** — Grade School / Senior High / College Engineering / Board Review Algebra are different knowledge artifacts, not one artifact with four quiz settings. Both alternative placements are rejected on the record (depth on the reader alone; depth on the program via `course_programs.learner_level_id`, which fails because Civil Engineering spans Year 1–4 plus Board Review). **What remains open is the authoring experience, not the model:** the direction is inferred metadata with explicit human override (**Review Set → author profile → override**), recorded as a direction section in `ADR-001` with four binding constraints — Subject and Domain Context may not infer depth, inference is a UI pre-fill and never a server-side write (it would destroy the `domain_context IS NULL` promotion marker), year-level granularity is out of scope, and the label may not be renamed to `Intended Audience` because `target_profile_type` already owns that concept. **Not implemented, and gated behind making authoring metadata editable on `STUDY_PACK_READY` notes and then R4.**

**A neutral, self-contained consultation prompt exists** at `docs/claude-prompt/canonical-knowledge-architecture-out/14-learner-level-necessity-gpt-consultation.md`, including a bias warning about who wrote it. **Sequencing note: R4 has not run yet.** It is the evidence on whether Domain Context works at all, and if it shows content drifting generic the answer to this question changes anyway — so resist restructuring the taxonomy before that read exists.

---

## Companion Scoping — Resolved 2026-07-29 (was an open question before v0.63.0 signoff)

**Original question:** should Companion (and by extension Ask Companion) ever reach users who never adopt a Review Set? A GPT exchange proposed keeping Companion permanently Review-Set-only while giving Note Detail "transition features" into Review Set adoption. Pressure-tested with Opus against the real code — **resolution, shipping as `v0.64.0`:**

- **Companion is not "Review-Set-scoped" — it's admin-authored-Official-collection-scoped.** `NoteCollectionService.setCompanion`/`clearCompanion`/`generateCompanion` all gate on `assertAdmin(user)` — a learner-created Review Set can never have a Companion, because nobody curated it. That's the structural reason, not a philosophical stance about curriculum-vs-note altitude — so "permanently Review-Set-only" was dropped in favor of the narrower, true claim above. Personalization (PRO, still Parked not ruled out) stays the mechanism that could eventually reach a no-Review-Set learner some other way.
- **The three-layer guidance model:** content explanation (Study Pack, every Note) → diagnostic guidance (ConceptHealth/readiness/weak concepts, every Note, personalized) → editorial guidance (Companion, curated Official collections only). A note-only user isn't excluded from "the guidance layer" — they're missing only the one layer that structurally requires a human curator to exist at all.
- **GPT's "create a Review Set from this note" bridge was rejected** — a user-created collection still gets no Companion (same `assertAdmin` fact) and clears no Ask Companion eligibility either; it routes a learner to the guidance layer's front door and hands them nothing.
- **A Note Detail recommendation/nudge was rejected** — `DashboardStudyPlanSection` already fires the same "adopt/continue an Official set" recommendation at Onboarding, Dashboard, and Collections (three surfaces, not the two GPT assumed); a fourth surface on Note Detail (the one surface meant to stay focused) would be a nag, not a help.
- **What ships instead:** a small, user-initiated "Add to {Review Set}" action on Note Detail — no recommendation, no matching, no coverage claim, just closes a real navigation gap (extracts the already-built `AddImportedDraftsModal` from the bulk-import flow).
- **The no-Review-Set learner's guidance question was folded into the existing, still-open Primary-Review-Set-vs-Study/Exam-Focus roadmap item**, not opened as a new one — that question already commits to Study/Exam Focus as the load-bearing no-Goal fallback.

## Companion Guidance Doctrine — narrowed 2026-07-29, doctrine text ADOPTED in v0.68.0

**A further GPT exchange proposed something bigger:** stop calling it "Ask Companion" (a chatbot the learner has to think to open) and instead make "Companion" NoteLib's cross-cutting learning-guidance *system* — one voice answering "what should I do next" across Dashboard, Review Set, Progress, and Readiness, deterministic wherever possible (ConceptHealth/Progress/schedule), LLM only when the learner explicitly asks for deeper reasoning. Pressure-tested with Opus — **the taxonomy is right, the literal merge is not:**

- **Adopted:** "one learning responsibility per feature" as doctrine (Notes store, Study Pack teaches, Quiz assesses, Flashcards/Memorization strengthen recall, Progress/ConceptHealth measures, Companion guides the next decision) — a genuinely good organizing principle, worth writing into the docs.
- **Rejected: literally merging today's guidance into one "Companion" system/brand.** "Companion" currently names three structurally different things — admin-authored static content, learner-reactive derived guidance (the docs already call this "Coach"), and the LLM chat (explicitly forbidden from using learner performance today) — and unifying the *name* removes the vocabulary keeping them safely apart. The tell: the owner's own example list included Knowledge Impact ("how is my contribution helping learners?") — a contributor question sharing no signal with study guidance, included only because it sounds adjacent in English.
- **Rejected, for now: a backend merge of the guidance resolvers.** 8 independent "what's next" resolvers already exist (Dashboard alone has 3), each often diverging for a documented reason (e.g. Dashboard/collection pacing must stay uncoupled) — sitting on only 2 shared signal primitives underneath. Real, costly refactor; the sought benefit (felt coherence) is mostly a copy problem, cheaper to solve as an authoring doctrine than a backend rewrite.
- **No rename.** `Feature.ASK_COMPANION`, `ask_companion_sessions`, `AnalyticsEventType.ASK_COMPANION_*` stay exactly as shipped.
- **Real blocking dependency found:** a unified "what should I do next" answer requires one canonical answer to "what am I working toward" — which is exactly the still-open Primary-Review-Set-vs-Study/Exam-Focus question. That question now has three dependents (Personalization, the no-Review-Set guidance surface, and this). Recommended as its own release, ahead of any further Companion-system work.
- **If ever scoped:** Phase 0 resolve Primary-vs-Focus → Phase 1 doctrine + copy audit (docs only, no backend) → Phase 2 additive `reason` field on existing resolvers (substrate, no merge) → Phase 3 lightweight one-off "why this?" explainer (needs a new message-unit mechanism — `ask_companion_sessions`'s session-unit quota and DB-CHECK turn cap don't fit a single question) → Phase 4 Personalization (unchanged). Merge the 8 resolvers only if Phase 1 empirically proves the ladder is identical across surfaces.
- **`v0.64.0` is unaffected by this discussion** — ships exactly as already scoped above.
- Full resolution: `docs/product/ROADMAP.md`'s "Companion Guidance Doctrine" Backlog Index row.

**Status update — `v0.68.0` (2026-08-01):** the **doctrine text itself is now adopted**, as a new `### Companion Guidance Doctrine` section in `AGENTS.md` (placed beside the Page Responsibility Rule it extends). Docs/copy only — no rename, no new user-facing brand, no backend or analytics change, exactly as narrowed above. **Everything past the text is still gated:** Phase 1's copy audit and any merge of the 8 existing "what's next" resolvers still require Phase 0 (Primary-vs-Focus) resolved first.

**Caveat if you are asked to apply the doctrine:** `v0.68.0`'s pressure test found **four internal inconsistencies in the doctrine's own text**, logged as a Known Limitation. It conflicts with the Page Responsibility Rule table it claims to extend (that table assigns `Companion` a single governing question, while the doctrine argues "Companion" names three structurally different things — admin-authored static content, learner-reactive derived guidance, and the LLM chat); its "one question per surface" bullet points at a table enumerating *pages*, not guidance surfaces, so a new surface has no row to look up; its "docs/copy only" header scope contradicts its own third bullet instructing you to extend a resolver (a code change); and it says nothing about a new surface landing on a page that already has a grandfathered resolver. Reconciling that text belongs with Phase 1, not with a casual application of the doctrine.

---
