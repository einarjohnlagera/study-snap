# GPT_CONTEXT.md - NoteLib Product Context Handoff

> Paste the block below as your first message in a new GPT chat session.
> Update this file whenever a new version ships or the roadmap shifts significantly.
> Last updated: v0.71.0 (In Progress, Slices 1–2 merged) - 2026-08-05

---

Here's the current context for our NoteLib product session. Treat this as a compact product snapshot, not a replacement for the repo docs. For implementation work, always defer to `AGENTS.md`, `RELEASES.md`, `docs/product/ROADMAP.md`, and the relevant `docs/features/*.md`.

---

## START HERE — orientation in 12 lines

Read this block first. Everything below is detail behind it.

1. **What it is:** NoteLib is a notes-first study workspace. Capture notes → generate Study Packs → practice with quizzes → track readiness → reuse the library.
2. **What we sell:** the *learning system*, not features. Hero: **"Always know what to learn next."** Features are evidence for that promise, never the promise itself.
3. **Current state:** `v0.71.0 - Applicable Programs` is **In Progress**. `v0.70.0 - Canonical Knowledge Completion` released 2026-08-04. Release B of `ADR-001` is cut into three slices; **Slices 1 and 2 are merged** (PRs #990, #991) and **Slice 3 is gated on a curator ruling that is the live open question — see "Open decisions: Slice 3."**
4. **The single biggest constraint:** W1→W2 retention is **2.4%**. It has not moved across multiple releases. Read "Retention Is the Proven Constraint" before proposing anything new.
5. **Pricing is settled for now** — quota raised, price change deferred pending data. Don't reopen it.
6. **Positioning copy no longer needs a conversion test** (owner call, 2026-08-01). Pricing/checkout *mechanics* still do.
7. **Mastery comes only from graded assessment.** Self-review surfaces (Flashcards, Memorization) are firewalled from readiness by design.
8. **There are exactly 5 quiz modes.** The contract is locked. Do not propose a 6th.
9. **Biggest recurring failure mode in this repo:** repo-wide copy/terminology changes that under-scope themselves. `v0.68.0` under-counted its own sweep four times; `v0.67.1` scoped three items and shipped seven. Assume one grep is insufficient.
10. **Open questions awaiting your input:** (a) **the four Slice 3 Program Family decisions — the live one, see "Open decisions: Slice 3" below**; (b) Explore's default tab — still unresolved three releases later; (c) the learner-depth question is **resolved** 2026-08-04 — the four-axis model stands and notes own their depth; what remains is evolving authoring toward inference, recorded as a direction in `ADR-001`. **R4 is RESOLVED** (passed, 2026-08-04) — it validated the Domain Context *value set*, **not** applicability, and must not be cited as settling Slice 3.
11. **Don't propose:** a 6th quiz mode, price changes, AI-generated per-concept definitions, feeding self-review into readiness, or user-facing "Creator"/"Curated Learning" labels.
12. **The repo is `studysnap` internally.** The product is NoteLib. Database and package names still say `studysnap` — that is intentional, not debt to fix.

---

## App: NoteLib

**What it is:** NoteLib is a notes-first study workspace. Users capture notes, generate Study Packs, practice with quizzes/exams, track readiness, and keep a reusable library of learning material.

**Core loop:** Capture -> Generate -> Review -> Improve -> Make a Copy -> Repeat

**Positioning:** **Ratified 2026-08-01 as a product-wide Messaging Architecture** — *"We sell the learning system. Features simply support that promise."* Ratified hero: **"Always know what to learn next."** / *"NoteLib turns your notes into a complete learning system — organized, prioritized, and ready whenever you sit down to study."* The hierarchy is **locked**: Hero (universal emotional outcome, profile-agnostic) → Supporting paragraph → Profile-specific bullets (via the existing `ProfileType`-keyed copy-resolution pattern, not a new mechanism) → Features as *evidence* for the promise. Board Exam Mode, Companion, Adaptive Practice, Review Sets, Progress, Knowledge Impact, and Explore all live at the Features layer — **none of them becomes the hero anywhere.**

Two things a new session usually gets wrong here:

- **The old "external copy hasn't caught up / needs a conversion test first" framing is dead.** `v0.68.0` shipped the ratified hero and supporting paragraph on `/pricing` word for word, plus the Plus/Pro taglines. And the "no positioning-copy change without a conversion test" bar was **explicitly lifted by the owner on 2026-08-01** — narrative consistency with the product vision is treated as a design decision, measured post-launch, not an optimization experiment. Pricing/checkout *mechanics* changes still need evidence; positioning copy does not.
- **Rollout is deliberately incremental, not done.** Only `/pricing` has shipped. The landing page, paywall modal, Exam Hub upsell, `PLAN_COMPARISON_ROWS`'s "Best for" row, and per-plan feature bullets are all still un-actioned and each needs its own scoping pass and `/kickoff`. `PROFESSIONAL`'s profile bullets are flagged **aspirational — do not wire live** until that profile has real capability behind it (it is enum-only today).

**Rebrand note:** The product is NoteLib. Code, package names, and database/schema names still use `studysnap` in many places unless explicitly changed.

**Current baseline: `v0.71.0 - Applicable Programs`, In Progress.** Release B of `ADR-001` — making applicability a many-to-many fact so one canonical note surfaces under every program that needs it. Cut into **three slices along the irreversibility boundary**, not by layer, because Release B is not reversible once reads move to the join.

- **Slice 1 — merged (PR #990).** `note_course_program` join table, `V107` one-row-per-note backfill from the legacy string, and Teacher/Admin + Admin Dashboard write surfaces. Nothing read the join.
- **Slice 2 — merged (PR #991).** Discovery reads move to the join. **Its original safety premise turned out to be false and the semantics changed as a result:** the slice was scoped on "at one join row per note the join returns identical facet counts," but `V107` deliberately creates **no** join row for a catalog-*excluded* program value, so a pure-join rewrite would have changed *result sets* — an excluded value's facet vanishing, its filter returning 0 instead of N, its public shareable slug URL ceasing to resolve. Owner ruling 2026-08-05: reads are **join-first with a legacy-string fallback** (`EXISTS(join rows) OR (no join rows AND legacy string matches)`). Equivalence was then verified on real data: 0 differing facet rows, 0 differing note IDs across four filters. **Accepted cost: `notes.course_program` stays load-bearing on read paths.** Retiring the fallback is unscheduled.
- **Slice 3 — NOT started, gated.** Program Family expansion. The gate is the open decision below.

**Not yet deployed.** Both slices are merged to the release branch; production still runs `main`, so `V107` has not run and production has zero join rows. Signoff is what deploys.

**Superseded baseline:** `v0.70.0 - Canonical Knowledge Completion` released 2026-08-04 — the `course_programs` catalog (21 seeded programs, 11 excluded) + `program_families`, pool/bank learner-level re-keying, and authoring metadata made correctable after generation. **R4 resolved 2026-08-04, passed on all three steps**; bulk authoring is unblocked. Earlier baseline: `v0.69.0 - Canonical Knowledge Foundation`, deployed 2026-08-04 (PR #981). **What shipped:** Release A of `ADR-001` — the repo's first Architecture Decision Record — splitting the overloaded `notes.course_program` field into separate axes. `notes.domain_context` (a curated closed 8-value enum) is now the *sole* domain constraint sent to the LLM, and note-level `notes.learner_level` carries authored depth. Why it existed: authoring of the Civil Engineering Review Set's Engineering Mathematics plan had been deliberately halted, because one Algebra note under a one-program-per-note model would have to be duplicated for eleven engineering programs. **Two Planned Scope items were deliberately deferred to `v0.70.0`** — the `course_programs` catalog + `program_families`, and the question pool/bank re-keying — both blocked on production reads the release branch could not perform, not on engineering time. **The R4 verification did not run before signoff and is now a `[CHECKPOINT — due 2026-08-18]`**: it needs the new columns live in production, production runs `main`, and every release commit sat on the release branch — so signoff *is* what deployed it. **Bulk authoring must not begin until R4 step 2 passes.** A full pre-signoff pressure test (two agents plus an independent review) found five defects, two of them blockers that per-PR review was structurally unable to see — including silent permanent wiping of both new fields whenever a note's details were edited from the note detail page. Superseded baseline: `v0.68.0 - Topic Note Rename` shipped 2026-08-01 (PR #966). **What shipped:** the bare-topic drafting action renamed **"Generate Note" → "Create a Note"** (Company Redefinition Phase 4 §4 item 6), reserving "Generate" for operations that transform the learner's *own* material — "Generate Study Pack", "Generate Quiz", and "Regenerate" are deliberately unchanged, and that distinction is the whole point of the rename. Five further ungated items were folded in after kickoff: a "Retry Generate" → "Retry Generation" grammar fix (§4 item 8), the Companion Guidance Doctrine's docs-only text, the Messaging Architecture's first slice on `/pricing`, and — on explicit owner review after the first three shipped — two consistency batches the rename's own narrow scope had left behind (`plans.ts`'s pricing-table and upgrade-highlight strings, and the topic-note quota's entire user-facing vocabulary moving to **"topic note(s)"**).

**Two process facts from `v0.68.0` worth carrying forward, because they recurred:**

1. **Scoping under-counted itself four separate times in one release.** The rename's own "swept the whole repo" claim missed five surfaces inside the renamed action's *own* flow; the quota batch's sweep was truncated and missed the actual paywall copy (including a term the batch had explicitly rejected on evidence); a layout fix addressed the pill and title size but not the description-length drift that was the real cause; and `RELEASES.md` contained four wrong counts. All were caught by a **five-agent pre-signoff pressure test**, not by per-PR audits — because none of them appeared in any single PR's diff. If you are proposing a repo-wide copy or terminology change, assume one grep is not enough.
2. **A backend string can be user-facing copy.** The release was scoped "frontend-only" and still shipped a bug: `BulkNoteGenerationQuotaExceededException`'s 422 message rendered the retired vocabulary into the *same* `role="alert"` element as its renamed frontend twin. Fixed as an explicit scope amendment. Backend exception messages propagate verbatim to the UI through `api.ts` — treat them as copy, not internals.

Twelve pre-existing findings are recorded as **Known Limitations tagged `v0.68.1` candidates** in `RELEASES.md`. Note this project's own history here: `v0.67.0`'s candidate tagging was itself incomplete, and `v0.67.1` ended up shipping seven items after scoping three — so review that list directly rather than trusting the tags.

**Immediately prior releases:** `v0.67.1 - Explore Convergence Follow-ups` (2026-07-31) and `v0.67.0 - Explore Convergence` (2026-07-30, PR #949) — the latter notable for claiming its slot after **8 reclaims** by unrelated gate-cleared work, and for shipping on an **explicit owner gate override** with a dated `[CHECKPOINT — due 2026-09-13]` attached so the override gets checked against real engagement data. Full detail for both in the release list below.

---

## Open decisions: Slice 3 — Program Family expansion (LIVE, 2026-08-05)

**This is the question currently blocking `v0.71.0` from closing with all three slices.** Full sheet with the query and rulings: `docs/claude-prompt/canonical-knowledge-architecture-out/20-slice-3-family-expansion-decision-sheet.md`.

**What Program Families are.** ADR-001 rule 5: *Program Families are an authoring shortcut only. Selecting a family expands to explicit `note_course_program` rows at save time. Applicability is never inferred from a family at read time.* So expansion is a save-time pre-fill producing explicit, editable rows — but a pre-fill **is** a default, and the gate was written knowing that. "The author can trim it afterwards" is not grounds to skip the ruling.

**The recorded gate is narrower than three documents imply.** ADR-001, `RELEASES.md`, and the ROADMAP all state it as *"is `Engineering Sciences` shared by all **11 engineering programs**, or a subset?"* — but **NoteLib's catalog holds 3 engineering programs** (Civil, Electrical, Mechanical), and exactly **one** family (`Engineering`). The "11" came from an early taxonomy doc reasoning about Philippine engineering curricula in general, not about the catalog. So the syllabus reading is one sitting's work, not a survey.

**Four decisions, in the order they change scope:**

1. **Does the catalog gain more engineering programs?** A product/expansion call needing a seed migration, separable from any syllabus reading — and it is what makes "8 vs 11" real or moot.
2. **Does Slice 3 seed additional families?** With 3 members, family expansion ships with almost no reach — worth asking whether it earns a slice at that size. Candidates visible in the catalog: health sciences (Nursing, Pharmacy, Physical Therapy, Medicine, Radiologic Technology), Education + Special Needs Education – Generalist, and the three Senior High strands. **These memberships are the substantive curator decisions**, more than the engineering subset.
3. **Does a family expand to all its members, or a curated subset?** `V106` defines `program_families(id, name)` plus `course_programs.program_family_id` — **membership and nothing else, no preset table.** If expansion is "all members," the preset already *is* the FK and Slice 3 is materially smaller than scoped.
4. **Is expansion subject-conditioned?** The biggest scope fork. An early taxonomy doc proposed `Engineering Mathematics` reaching all engineering programs but `Engineering Sciences` only "most — expand explicitly." Unconditional needs no subject logic; conditioned needs a subject→family-subset map, new schema, a curation surface — and it risks re-coupling the two axes `ADR-001` just separated (Applicable Programs = *where a note appears*; Domain Context = *how it is authored*).

**House recommendation, open to challenge:** unconditional expansion. It matches rule 5's "authoring shortcut only," keeps the axes separate, and the author's trim-after-expand is the per-note judgment ADR-001 already says carries the real applicability decision.

**Evidence available:** a production query in the sheet lists subjects already appearing under more than one program, with note counts — turning part of this from a blank-page syllabus reading into a ruling against real data. **Local data cannot inform it:** the local DB has zero Civil/Electrical/Mechanical Engineering notes, while production holds ~197 Civil Engineering official notes.

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

## Retention Is the Proven Constraint (read this before proposing anything)

**The number:** W1→W2 retention is **2.4%** (production read, 2026-07-15; 3 of 127 eligible activated users returned in week 2). This has been the core strategic constraint since v0.32.2 first flagged it (was 5.6% then) — it has **not meaningfully improved** despite three intervening feature releases aimed at it (v0.44.0, v0.46.0, v0.48.0). Free-tier quota was essentially never hit at the old limits (5/25/50/month), which is one reason the owner ratified a large quota increase in v0.61.0 rather than treating quota as the retention lever on its own — see the Current Release note above.

**Diagnosis (two independent Fable sessions converged):** every content-rich retention trigger the product has shipped **default-OFF**, gated behind the exact engagement it's meant to create. The first study session also ends in a psychologically "complete" feeling (Zeigarnik effect) rather than an open loop that pulls the learner back. `v0.48.0` (merged 2026-07-15) shipped the cheap fixes for both (open-loop first-quiz ending, due-concepts digest default-ON) — **both remain UNPROVEN, mechanism shipped, lift not measured.** Do not describe either as a retention win in external-facing copy.

**H1+H5 (commitment device + pre-decided return action) is the pre-committed next retention move if the v0.48.0 cohort re-read is positive-or-ambiguous — still gated, not abandoned.** The re-read needs a cohort that actually experienced the v0.48.0 changes to clear its 14-day W1→W2 window: that window closes **2026-07-29 (tomorrow, as of this update)**. The query is written and ready (`next-priority-new-user-focus-out/02-h1-h5-cohort-recheck-and-cpale-depth.sql` Query 1) but has not yet been run as of this update.

**The 2026-07-24 signup surge reversed the "go straight to Phase 2" plan and inserted a Diagnostic Read + a new Reusable Practice Assets initiative ahead of it — full detail in the Company Redefinition section below, which supersedes the old post-v0.48.0 sequencing.** The retained/churned exam-dated user interview script is still written, ready, zero engineering cost, and still hasn't been run — the one open item on this whole track that can't happen from a keyboard.

**The target habit was redefined 2026-07-28 — read Round 2 through this, not the raw blended 2.4%.** The single W1→W2 calendar-week boolean is retired as the universal yardstick. Segment by whether `UserEntity.examDate` is set (not `profile_type`, a coarser proxy for the same thing):

- **Exam-bound learners** — the majority; `BOARD_EXAM` alone is **70.94%** of profile-typed accounts, confirmed product-wide rather than a surge artifact. Their arc is naturally episodic: signup → sustained practice → sit the exam → legitimately stop. Scored **only once their exam date has passed**, on whether they had activity in the final 7 pre-exam days. Still-in-flight users are excluded from the denominator entirely rather than penalized on a calendar clock that doesn't match their arc — going quiet *after* the exam is not churn.
- **Open-ended learners** — `STUDENT`, no exam date, ~27%+. Keep the existing W1→W2-style frame, which fits them better.

Two guardrails on reading this:

- **It does not excuse the 0/41 exam-dated-retention finding** (users going quiet *below* their own exam date). That is disengagement before the goal — a real problem under either frame.
- **Expect a small scored group at first** (recent signups, PRC-clustered exam dates). A near-single-digit denominator means "not yet measurable," not a verdict.

Full definition: `docs/product/ROADMAP.md`'s "Target-habit definition" Backlog Index row.

**Full backlog, current status, and exactly what un-parks each item lives in `docs/product/ROADMAP.md`'s Backlog Index table (~55 rows) — check it before proposing or resurfacing anything.** See "Roadmap Candidates: Gated & Ungated" below for a synthesized, status-grouped view of that same table, restricted to items still actually open (shipped/resolved rows are dropped from that view — check `RELEASES.md` for those). Do not propose roadmap items from partial memory of past sessions; the index is the current source of truth.

---

## Company Redefinition & Where Things Stand Now (read this before proposing what comes next)

**The strategic redefinition (2026-07-23).** The owner encountered `boardready.ph` (a simpler PH board-exam-review competitor with strong traction) and worked with GPT toward a company-level reframe: NoteLib as a **"learning OS"** — a learner's own notes become curriculum, curation turns that curriculum into a compounding reusable asset, AI is the machinery behind the curtain, never the thing a learner is asked to trust directly. A 6-session Fable plan produced a full design synthesizing this onto already-shipped architecture (Companion, Goal→Subject hierarchy, ConceptHealth, `ExamQuestionPool`) — `docs/claude-prompt/company-redefinition-out/01-09`. **`v1.0.0` is explicitly reserved for later**, tagged only once this redefinition's user-visible core is live and the product succeeds.

**Where each of the 4 phases actually stands today:**
- **Phase 1 (practice-first activation onboarding) — shipped as `v0.57.0`.** A `BOARD_EXAM` learner with a depth-qualifying Official Review Set skips note-authoring entirely, adopts in one tap, lands on the set's detail page. Zero LLM call on this path.
- **Diagnostic Read — ratified, Round 1 run 2026-07-24/25, re-read due after 2026-08-06, not yet run.** A real-time signup surge (~29 signups on 2026-07-23 alone) reversed the "go straight to Phase 2" decision made hours earlier that same day. Round 1 was inconclusive by construction (the surge cohort's 14-day window hadn't closed) but found one real, durable signal: a **chronic ~50% onboarding non-completion rate across recent signups generally** — not surge-specific, the surge day actually completed onboarding *better* than baseline. Full results: `company-redefinition-out/08-diagnostic-read-methodology.md`.
- **Reusable Practice Assets & the Return Loop — shipped as `v0.58.0`.** Turned on Board/Long Exam's dormant per-user question pool, extended the same per-user pattern to Challenge Quiz, added a "redo what you missed" surface reusing existing `ConceptHealth`/weak-concept machinery. Framed as a retention primitive (a learner who never gets a second crack at a missed question has no spaced-repetition mechanic to retain against), not a cost play — no token/dollar metering existed anywhere in the system before this.
- **Phase 2 (IA / Explore convergence) — split, re-gated 2026-07-24, both chunks now shipped.** `v0.59.0` (Dashboard hero → Primary Review Set card, Progress promotion) shipped once Reusable Practice Assets shipped. `Explore Convergence` (new nav item, segmented Review-Sets/Notes control, Exam Hub additive official-set check) shipped as **`v0.67.0`, 2026-07-30, via an explicit owner override of its Diagnostic Read gate** (still unmet — see Current Baseline above for the full override/checkpoint detail) — the slot changed hands **8 times** before this, each time to unrelated ratified/gate-cleared work (`v0.60.0` through `v0.66.0`).
- **Phase 3a (cross-user Challenge Quiz template pooling) — shipped as `v0.60.0`.** Retargeted from the original Long/Board Exam pool design after confirming those modes are PRO-gated with ~zero production load; real gate evidence (two Official Review Sets with 23 and 9 concentrated recent adopters) cleared the proposed adoption-volume gate on real data. **Phase 3b** (curator-side pool expansion) stays parked on its own unresolved review-queue dependency.
- **Phase 4 (packaging/terminology)** — drafted, needs an explicit owner decision on its source doc's own "§4 Owner must decide" section. No engineering dependency on anything else, free to move anytime once ratified.

**Since Phase 3a shipped, three more Challenge Quiz patch releases closed real bugs and shaped the mode further — all released, all off `v0.60.0`'s line, none consuming a minor-version slot:** `v0.60.1` (5-bug fix pass found exercising the new template-sharing feature in production — executor saturation, missing shuffle, an inert difficulty selector removed entirely, abandoned sessions silently auto-submitted, a non-functional Redo Missed Questions button); `v0.60.2` (3 narrower known-limitations closed: claim-release transaction isolation, an expiry-vs-completion lock race, a missing Resume/Start-Fresh prompt at one entry point); `v0.60.3` (adaptive initial question count, a Redo-Missed-Questions session-matching fix, an incomplete-submission guard — a 4th scoped item, onboarding coverage-gap capture for `STUDENT` profiles, was **deferred out at signoff** when its gate query found real `STUDENT` presence in the surge cohort, 2/29 = 6.9%, failing the required "effectively zero" bar; it now tracks as its own version-less Backlog Index row, gated on the Diagnostic Read closing).

**Knowledge Impact (creator-recognition dashboard, "your notes helped N learners") — ratified by the owner 2026-07-28, despite its own data gate failing.** GPT originally proposed this off the platform's aggregate engagement growth. The gate query (run 2026-07-28) came back decisively negative:

- **3** distinct non-official public-note creators — the un-park threshold was order-of-magnitude **20–30+**
- **697 official vs. 4 community** public notes (99.4% official)
- Community engagement share ~1.8% of views, ~0.07% of copies
- Community-publish rate went to **zero in 2026-07**, the highest-signup month on record

**Why the owner ratified proceeding anyway:** near-zero publishing may exist *because* creators get nothing back for it — a chicken-and-egg counter the original CTO evaluation (`company-redefinition-out/09-knowledge-impact.md`) had already named as a live unresolved branch, not an argument invented after the fact to override the data. Not yet scoped; `09`'s "Answers to the memo's 12 questions" is the design brief when it is (passive/pull dashboard, retrospective and aggregate framing, nothing comparative/ranked/real-time, private to the creator).

**Pricing/quota debate (raised 2026-07-25, resolved 2026-07-27/28).** The owner argued current pricing (PLUS ₱179 / PRO ₱249, FREE capped at 5 Challenge Quizzes/month) can't convert PH students against `boardready.ph`'s reported ₱99 one-time / ~unlimited practice access, and proposed pooling-funded quota loosening plus a price cut.

Three counter-findings from the analysis:

- NoteLib's plans are **already one-time passes, not subscriptions** — the "recurring vs. one-time" competitor framing didn't actually hold.
- **Pooling buys generosity, not a price cut** — two different levers.
- **FREE quota "never being hit" is ambiguous** — it doesn't discriminate between "quota is genuinely sufficient" and "users disengage before ever approaching it."

A three-way consensus (Claude + independent Fable + independent GPT, ~90% aligned) held that prices shouldn't be cut without first pulling the already-instrumented paywall funnel. **Owner resolution, ratified 2026-07-28:** raise the Challenge Quiz monthly quota substantially now (shipped as `v0.61.0`), defer the price decrease until paywall-conversion / onboarding-retention / post-increase usage data exists.

A literal **daily-reset** quota model was also proposed and **explicitly rejected** in favor of a bigger monthly ceiling: real study behavior is bursty around exams, and a daily reset would force the product to dictate the learner's schedule.

**v0.61.0 shipped both items 2026-07-28** — quota increase and LLM telemetry, both audited (backend 1285 tests, frontend 1544 tests, clean). One documentation-precision correction made at signoff, not a functional bug: Board Exam sessions draw from both their own dedicated 10-unit cap and the shared Challenge Quiz counter this release raised — the dedicated cap remains the binding constraint in every realistic path, so this release strictly improved Board Exam's effective headroom rather than changing its own limit. One real finding logged as a Known Limitation rather than silently dropped: the new telemetry has no reader yet (no baseline, threshold, or query), and by design stays null for bank/template-served sessions — a naive "mostly null" read could be mistaken for low spend rather than most sessions never calling the LLM. Next step: a read of the columns ~30 days post-deploy.

**Profile-type population mix resolved 2026-07-28 (production query, not just the surge cohort):** `BOARD_EXAM` is 70.94% of profile-typed accounts vs. `STUDENT` 27.09% — and this predates the surge (63.3%/34.0% pre-surge), so it's not a surge-only skew. This confirms the `BOARD_EXAM`-only practice-first adopt-a-Review-Set fast-path reaches the majority segment, not a minority carve-out. Separately, 40.1% of all accounts have `profile_type` still NULL, corroborating the Diagnostic Read's ~50% onboarding non-completion finding via an independent measurement path. A same-day funnel re-check (re-running Round 1's own Query 7/Query 8) found onboarding completion trending up (50.4%→58.46%) with no known causal driver — encouraging but not a confirmed trend — and surfaced the `ONBOARDING_V2_ABANDONED` bug fixed in v0.61.0 above.

**Decided firmly, not open for re-litigation:** Adaptive Practice stays **out** of any pooling scope — its entire value is personalized reactivity to one learner's own misses.

---

## Tech Stack

| Layer | Stack |
|---|---|
| Frontend | Next.js App Router, React 19, TypeScript, Tailwind, shadcn-style UI |
| Backend | Spring Boot, Java 21, PostgreSQL, Flyway |
| Auth | JWT, refresh tokens, Google OAuth, email verification |
| Payments | Xendit hosted checkout, webhook-confirmed access only |
| AI/OCR | OpenAI for notes/Study Packs/quizzes, Google Cloud Vision for OCR |
| Analytics | Shared analytics event model; backend persists after commit through async executor |

---

## Product Model

- **Note** is the primary entity. State: `DRAFT`, `GENERATING`, `FAILED`, `STUDY_PACK_READY`. Visibility: `PRIVATE`, `PUBLIC`.
- **Note metadata is four separate axes as of `v0.69.0` (`ADR-001`), not one field.** *Subject* = what it is about. **Domain Context** = how it is authored, and the **only** thing that reaches the LLM as a domain constraint (curated closed 8-value set). **Note Learner Level** = how deep it is authored. *Applicable Programs* = where it appears — discovery only, **never reaches a prompt**. **Built as of `v0.71.0` Slices 1–2**: stored as explicit `note_course_program` rows, curated by Teacher/Admin, and read by Library/Public Library filters, facets, badges, and public search — join-first, falling back to the legacy string only for a note with no join rows. Facet counts can now correctly sum above the note total, which is expected under many-to-many and is explained in the filter panels rather than hidden. `courseProgram` survives as a legacy label and a fallback, and is no longer the classification apex. Resolution lives only in `StudyPackGenerationContextResolver`: domain = note domain context → note program → profile program; depth = note level → reader level → `College`. **Static content never falls back to the reader's level** (a Grade School reader cannot dilute a College note); **quizzes treat the note's level as a floor** — a lower reader level may soften wording and add scaffolding but never lowers curriculum, and a higher reader level never raises difficulty. Both new fields are **Teacher/Admin-only** and NULL on nearly every note; NULL is the designed norm, and `domain_context IS NULL` is itself the marker for "not yet promoted."
- **Study Pack** is generated content attached to a Note: summary, key concepts, quiz, metadata suggestions, and downstream quiz/exam entry points.
- **Note Collections (Study Plans / Review Sets)** organize owned notes into an ordered, curated unit, with an optional one-level Goal -> Subject hierarchy. This is the product's primary retention lever — see the dedicated vision section below.
- **Learning Companion** is a persisted, curator-authored guidance layer (JSONB) on top-level Review Sets — the "premium guided learning experience" layer riding on top of the Study Plan journey. See the dedicated vision section below.
- **ConceptHealth** is the recency spine for readiness and Progress: `lastCorrectAt`, `lastIncorrectAt`, due/not-due classification, and struggling state. This is the *only* mastery-integrity signal in the app, and it's locked (since v0.37.0) to move only from genuine assessment — see Quiz / Practice Mode Contract.
- **Quiz sessions** share `quick_review_sessions` with mode stored as enum and session state in JSONB. Question **format** (MCQ, True/False, Multi-Select, Matching, Identification, Enumeration) is a separate axis from mode.
- **Flashcards and Memorization** are non-scored review surfaces that sit entirely outside the quiz-session engine — no session row, no `ConceptHealth` write, ever.
- **Generated teacher quizzes** use `generatedQuiz`, not student quiz sessions.

Versioning rule:

- Never auto-regenerate generated content.
- Regeneration is explicit, user-confirmed, owner-only, and updates the existing Study Pack in place.
- Owner self-copy copies authored note fields only.
- Public-note copy is the exception: when the public source has a Study Pack, the copy includes that Study Pack and arrives `STUDY_PACK_READY`.

---

## Note Collections (Study Plans / Review Sets): Vision & Locked Rules

Profile-aware terminology — "Study Plan" (Student / Board Taker), "Lesson Plan" (Teacher), "Review Set" (Professional) — all the same underlying `NoteCollection` entity, labeled through `getCollectionLabels(profileType)`.

**The vision, in one line:** a Note Collection is not a folder — it is a trackable **readiness journey**, and it is the product's primary retention lever (chosen for this role in v0.33.0 when W1→W2 was ~5.6%: give a learner a number that only moves by returning to practice, and a credible zero-notes on-ramp via curated adoptable plans).

**Locked structure and rules:**
- A top-level **Goal** can contain child **Subject** collections through `parent_collection_id` — exactly two levels, no arbitrary depth, no per-module mastery, cycles impossible.
- **Adoption** (admin-published collections) is free, idempotent, makes no AI call, creates a private snapshot copy — source edits never sync into adopted copies. Recursive Goal adopt copies every child Subject plan and note in one action.
- **Readiness** derives entirely from existing `ConceptHealth`/`ProgressReportService` — no new mastery signal, ever — but is deliberately *not* shown everywhere: dedicated plan-detail/`/progress` surfaces show it, execution rows/list cards/published-plan cards/public source plans deliberately do not (list-level mastery display was tried and rolled back — it created role confusion between "browsing" and "monitoring"). Vocabulary is locked: `ready / mastered / due / not started`.
- **Mastery integrity is protected:** Flashcards and Memorization are locked to never write `ConceptHealth`. Quick Review also writes it today (a deliberate 2026-07-11 change, corrected in `EXAM_MODES.md`/this doc 2026-07-29 after a v0.63.0 pressure test found the "Quick Review never writes it" framing had gone stale) — every quiz-session mode (Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, Board Exam, Interview Practice) can move the readiness number; only the two non-engine review surfaces cannot.
- The **Builder** (`/collections/{id}/builder`) is the single authoring canvas for both Goal and leaf plans — deliberately not a study/monitoring surface, no readiness ring on the Builder itself.
- **Primary Review Set + Weekly Countdown (v0.40.0+):** a nullable `primaryCollectionId` (top-level Goal only) plus optional `target_completion_date` and `studyDaysPerWeek` drive a derived — never stored — weekly countdown (`weeksRemaining`/`conceptsRemaining`/`todaysConceptBudget`). No adaptive/AI scheduling, streaks, or calendar integration — pure read-time derivation from existing readiness + date math.
- **Review Set Detail (v0.41.1)** is composed Identity → Current Journey → Primary Action → Readiness → Guidance (Companion) → Subject Plans/Notes — "what should I do next, in this Review Set," while staying collection-scoped (cross-journey "which set" stays Dashboard's job).

**Intentionally still parked:** standalone adoption of a single child Subject plan (unresolved re-parenting interaction with `adoptGoal`'s idempotency check) — not worth solving without a real discovery need.

---

## Learning Companion: Vision & Locked Rules

**The organizing insight:** Review Centers aren't valuable because they provide PDFs or quizzes — they're valuable because they provide **guidance** (structure, direction, pacing, coaching, confidence). The Companion is NoteLib's guidance layer riding on top of Notes (knowledge) + Study Packs (learning engine) + Review Sets (journey).

**Success criterion:** *"Every Official Review Set should feel like a premium guided learning experience rather than a collection of notes."* Not feature count, not revenue.

**Content model:** a single nullable JSONB column, `note_collections.companion` — 1:1 with a top-level collection only. Five long-form sections (Overview, Study Strategy, Common Mistakes, FAQ, Resources) plus an atomic `mentorTips` array (each tip has its own identity, an optional curator-tagged linked action, and an optional deterministic surfacing condition — never inferred at render time, never adaptive/LLM-driven selection). **No runtime LLM call to serve a Companion** — authored once, served static, zero per-view cost.

**Curation, never generation (locked, clarified not reversed in v0.42.0):** a learner never receives an auto-generated plan or tip. ADMIN-only `Generate Companion` produces a **draft only** — the curator must review, edit, and click Save/Publish, in every path including Mentor Tips. Official-author-only today; FREE for all learners, zero paid uplift on the Companion itself by design.

**Coach vs. Companion, the locked split:**
- **Coach (dynamic).** Reacts to the learner: continue-where-you-left-off, pacing, readiness, due concepts, resolved next action. This is `TodaysFocusCard` — zero new cost, just naming what already existed.
- **Companion (timeless).** Authored, does not react to daily progress. Mindset, expectations, common mistakes, practical advice — reads like mentor advice, not reference material. `CompanionDisplayCard` collapses by default on every viewport behind "View Full Guide."
- **Curriculum.** Subject Plans → Notes → Practice. Unaffected by this split.

**Result-Screen Companion Bridge (v0.55.0):** Quick Review, Challenge Quiz (both branches), and Adaptive Practice result screens show a labeled excerpt of the primary Review Set's Common Mistakes/Study Strategy — curator-published content only, no generation, no mid-exam coaching.

**Ask Companion (v0.63.0, shipped):** PLUS/PRO learners can ask up to 6 questions per conversation against a top-level owned Review Set's renderable Companion content, on a dedicated collection-detail chat panel. Grounded retrieval only (the system prompt refuses unsupported/outside-knowledge questions, never fabricates) — not a departure from "curation, never generation," since the model answers only from already-curator-published text. 20 sessions/month, 6-turn cap, cheapest model tier, reuses the existing per-minute AI rate limit. FREE sees a plan-aware upgrade prompt. **Twice-missed concept → Ask Companion (same release):** a consecutive-incorrect streak on `ConceptHealth` (resets on a correct answer) fires an "ask about this" CTA at streak 2 on Adaptive Practice/Challenge Quiz/Quick Review results, reusing the same Primary Review Set resolver and tradeoff `CompanionResultBridgeCard` already accepted. See the "Open Question This Session" section above — Companion (and both these features) remain strictly Review-Set-scoped; a user with no Review Set gets neither.

**Monetization philosophy (long-term principle, not a repricing of today's plans):** FREE = static guidance (the Companion itself). PLUS = interaction (**shipped v0.63.0** — Ask Companion, grounded Q&A reusing the Interview Practice cost-control template). PRO = personalization (future, gated — genuinely adaptive/learning-pattern-driven guidance selection, explicitly not deterministic rule reordering, which is the FREE-tier precedent). Personalization is the one future tier still not scoped to a version.

**Future, gated, not yet scoped:** AI-generated Review Sets (curator pipeline, effectively closed/ruled out — see Roadmap Candidates below); Personalized/Adaptive guidance (PRO) — gated on the still-open Primary-Review-Set-vs-Study/Exam-Focus philosophy question, a different/separate gate from what Ask Companion needed. See "Roadmap Candidates" below.

---

## Profile Types

| Profile | Current focus | Important rules |
|---|---|---|
| Student | Notes, Study Packs, Quick Review, Challenge Quiz, Adaptive Practice, Long Exam | Target Audience hidden; backend saves `STUDENT`. |
| Board Exam / Exam Reviewer | Exam-date context, Board Exam Mode, readiness for licensure-style prep | Target Audience hidden; backend saves `BOARD_TAKER`. |
| Teacher | Quiz generation, preview, DOCX export, Exam Builder | Teacher flow uses `generatedQuiz` only; never reuse student quiz sessions for preview. No adoption surface — Flashcards/Memorization and the Dashboard adoption nudge are hidden for Teacher. |
| Professional | Certification review, Long Exam as Full Practice Exam, Interview Practice | Target Audience hidden; backend saves `PROFESSIONAL`. |
| Parent | Enum exists, no real product implementation | Do not propose implementation without parent-child relationship design. |

Onboarding is active for verified users. It collects profile type, study goal, input method, Study Pack generation, completion, learner level, and course/program. This flow is **locked** — do not redesign it. Backend content-creating mutations must enforce profile setup for the legacy completed-but-null profile cohort through `ProfileSetupRequiredException`.

---

## Quiz / Practice Mode Contract

The product has a locked hierarchy of five top-level modes:

1. **Quick Review** - all plans, saved questions, lightweight practice — writes `ConceptHealth` on completion (since 2026-07-11) same as the other assessment modes below.
2. **Challenge Quiz** - all plans with quota, progressive generation up to 20 questions per session.
3. **Adaptive Practice** - Plus/Pro practice targeting weak concepts.
4. **Long Exam** - Pro exam mode, fixed long-form practice, supports multi-note sources.
5. **Board Exam** - Pro high-stakes exam simulation for Exam Reviewer profile.

Professional **Interview Practice** is a sub-mode of Adaptive Practice, not a sixth top-level mode.

Rules:

- Do not add a sixth top-level mode without updating `docs/product/EXAM_MODES.md` and roadmap/spec docs together.
- Premium exam paywalls fire from Start CTAs after setup/prescreen, not from card click.
- Study Plan premium-exam launches carry `collectionId` and scope additional-note pickers to quiz-ready notes in that plan.

### Question formats (a separate axis from modes)

Within the five modes, individual questions carry a `questionFormat`: `MCQ`, `TRUE_FALSE`, `MULTI_SELECT`, `MATCHING`, plus two free-text formats:

- **Identification** — fill-in-the-blank / name-the-term. Scored deterministically against a generation-time `acceptableAnswers[]` list — no per-submission LLM call.
- **Enumeration** — name every item in a 2–5 item set. Scored all-or-nothing via exhaustive bipartite matching against `acceptableAnswerGroups[]` — no partial credit.

Both formats are Challenge Quiz-only for now, and both are **ungated across every plan tier** — a deliberate stance: question-format variety is a learning-quality dimension, not a monetization lever. Monetization stays in mode-level and quota-level gates.

### Non-engine review surfaces: Flashcards and Memorization

Both are free on every plan, live on the Note Detail **Key Concepts tab** (deliberately *not* the quiz-mode CTA row), are hidden in Teacher mode, and exist only on private authenticated Note Detail — never on public notes, public library, or shared quiz links. Neither is a quiz mode: no `QuickReviewSessionEntity`, no session row, no timer, no score, no result screen, nothing in quiz history.

They are frequently mistaken for two skins on the same feature. They are pedagogically different tools, and the difference is load-bearing.

**Flashcards is a coverage pass. Memorization is a retention engine.**

Flashcards is **stateless** — a linear deck with previous/next, flipping concept → definition at your own pace. Nothing is recorded, so every visit yields the identical deck. Memorization is **stateful** — it shows **one due card at a time**, you self-grade it, and that grade rewrites when the card returns, persisted per `user_id` + `study_pack_id` + normalized `concept` in `memorization_cards`.

**Four distinctions that matter:**

1. **Recognition vs. committed retrieval.** Flashcards lets you flip at the first flicker of familiarity — the fluency illusion, where recognizing an answer feels like knowing it. Memorization forces a judgment *after* the attempt, and the judgment has a consequence.
2. **Massed vs. spaced.** Flashcards has no concept of time at all: one sitting, any order, all cards. Memorization distributes across days. Spacing is the mechanism that produces durable memory, and it is the entire reason Memorization is a separate surface rather than a button on the deck.
3. **Coverage vs. drillability — and they deliberately disagree.** Flashcards shows **every** key concept, rendering `No definition yet for this concept.` where no explanation matched. Memorization **excludes** those concepts entirely, because self-grading a card with no answer is meaningless. Only ~56% of key concepts get a matched definition (up from ~18% under exact-only matching), and this is a **permanent structural limit, not a bug to keep chasing**: `keyConcepts` (5–10) and `quiz` (a smaller fixed count) are independently generated, so there will always be more concepts than explanations. **Consequence to expect: Memorization legitimately shows fewer cards than Flashcards on the same note, and shows a caught-up state on days when Flashcards still offers the full deck.**
4. **Neither one measures the learner.** See the firewall below.

**When each is the right tool:** just-generated Study Pack → Flashcards (see the whole landscape, including the gaps). Weeks out from an exam, returning regularly → Memorization (spacing builds durability; the due-card loop is the return mechanism). Night-before cramming → Flashcards (no scheduler telling you a card isn't due). Want to know actual readiness → **neither; take a quiz.**

One-line version: **Flashcards answers "what's in this note?" — Memorization answers "what have I not yet made stick?"**

**Memorization's scheduling algorithm (simplified SM-2).** New cards start `repetitions = 0`, `intervalDays = 0`, `easeFactor = 2.5`, due now. Grades:

| Grade | Interval | Ease factor | Repetitions |
|---|---|---|---|
| **Again** | `0` — due now, returns in the same session | −0.20 (floor `1.3`) | **reset to 0** |
| **Hard** | `max(1, previous × 1.2)` | −0.15 (floor `1.3`) | +1 |
| **Good** | `1` day on first success, then `previous × easeFactor` | unchanged | +1 |
| **Easy** | `4` days on first success, then `previous × easeFactor × 1.3` | **+0.15** | +1 |

On a brand-new card all four collapse to *now / 1 / 1 / 4 days* — they only fan out once a card has history, because Good and Easy compound through `easeFactor` while Hard erodes it. **Again is the only destructive grade:** it zeroes `repetitions`, so a card built up to a 30-day interval restarts from scratch.

**Three firewalls — the most likely things for a future proposal to try to break:**

- **Never writes `ConceptHealth`.** Excluded from `ProgressReportService`, note readiness, plan readiness, My Progress, and `Overall Readiness`. The reasoning, not just the rule: **self-assessment is rehearsal, not evidence.** Only objectively graded quizzes and exams move mastery. A learner could rate every card "Easy" for a month and readiness would not move — that is correct. Wiring SRS recall into readiness would let a learner grade themselves ready, which is exactly the assessment-only mastery boundary this preserves.
- **Never calls the LLM.** Concepts with no matched explanation are *excluded* (Memorization) or shown with an explicit empty state (Flashcards) — never filled by generation. Closing the gap properly would need a real per-concept definition field in Study Pack generation: a schema and prompt change with real token cost, where existing packs would only benefit after regeneration. That is a deliberate non-goal, not an oversight.
- **Not routed through the Quiz Session Engine.** Do not add a `quizSession` discriminator, create a session row, or make either surface count toward quiz performance.

---

## Plans, Pricing, and Payments

Runtime entitlement source of truth is the backend subscription model and `GET /api/me/plan`.

- Plans: Free, Plus, Pro. Checkout: Xendit hosted checkout via backend `POST /api/payments/create`. Paid access is granted only by validated webhook-confirmed payments.
- Pricing is backend-owned; frontend pricing surfaces use billing/pricing APIs and shared plan config.
- **Paid plans are one-time, time-boxed passes in UI copy, not auto-renewing subscriptions.** This is a load-bearing fact — it's why the "recurring vs. one-time" framing in competitor comparisons doesn't actually apply to NoteLib.
- Cancellation is scheduled for period end; paid access remains active until then.
- Do not add plan flags to `users`. Do not change prices, quota numbers, pass durations, billing, or checkout mechanics as part of readiness/UX work unless explicitly scoped.
- New question *formats* are explicitly kept out of plan-gating (see Quiz / Practice Mode Contract) — a considered exception, not an oversight.
- **Quota numbers currently live in two independent places that must move together:** backend enforcement defaults (`application.yaml`) and frontend marketing/display copy (`frontend/lib/pricing-config.ts`'s `pricingConfig` object) — the latter is not derived from the former. A quota change that only touches one will silently desync marketing copy from actual enforcement.
- **Pricing itself stays unchanged as of `v0.61.0`** — see the Company Redefinition section above for the full resolution (quota raised now, price deferred pending paywall/retention/usage data). Do not propose a price change as if this is still an open debate; it's been resolved for now, revisit only once that data exists.
- **Plan taglines were re-messaged in `v0.68.0`; plan *names* were not.** `PLUS.title` → "Guided learning built around your notes", `PRO.title` → "Your complete learning system" (from "For regular study" / "Best for exam prep"). `name: "Plus"` / `name: "Pro"` are **untouched everywhere** — checkout, Settings, badges, receipts, support — a deliberate decision to avoid a bifurcated vocabulary, the same reasoning that kept Creator/Curated Learning internal-frame-only. `FREE.title` ("For getting started") is **still owed** to the Messaging Architecture: an outcome-framed candidate was written and reverted in `v0.68.0` because deriving it from Plus/Pro consistency contradicted the ratified **FREE=adopt** tier placement. It must be written against the locked hierarchy, not against its siblings.
- **Four separate components render plan `title`/`description`** — `/pricing`'s `PricingPlansSection`, the landing page's `SimplePricingSection`, `app/settings/page.tsx`, and `components/billing/paywall-modal.tsx`. `v0.68.0` shipped a real misalignment bug because plan `description` lengths had drifted to 62/96/133 characters, which cannot render at equal line counts in a multi-column card grid. They are now balanced at **88/88/92**. If you propose changing a plan description, keep the three within a few characters of each other, or you will silently break card alignment on four surfaces at once.

Upgrade CTA rule:

- Use `getUpgradeCtas(currentPlan)` from `frontend/src/config/plans.ts`.
- Free -> primary `Upgrade to Plus`, secondary `Go Pro`.
- Plus -> primary `Upgrade to Pro`.
- Pro -> no upgrade CTA.

---

## Previous Releases (condensed — see `RELEASES.md` and `docs/releases/*.md` for full detail)

**v0.71.0 - Applicable Programs (In Progress; Slices 1–2 merged 2026-08-05, PRs #990, #991).** See Current baseline above. **The process lesson worth carrying forward:** Slice 2's stated safety property was **false**, and was false when written — it assumed the backfill produced one join row per note, but the catalog deliberately excludes values, so ~a third of local notes had no row and a pure-join rewrite would have silently deleted their facets, filters, and public URLs. It was caught by checking a single number from Slice 1's own verification output against the plan, before any code was written. Two smaller catches in the same release: a migration that would have failed the deploy on ordinary user edits (`RAISE EXCEPTION` on a disagreement with a deliberately stale FK), and a derived-set rule that would have stranded every note gaining a program after creation.

**v0.70.0 - Canonical Knowledge Completion (Released 2026-08-04).** The two `v0.69.0` deferrals plus the two authoring-surface gaps found in the first hour of real use. The `course_programs` catalog seeds **21 curator-approved programs and deliberately excludes 11** (bare levels, goals, subjects, the `Engineering` family, and — on an owner ruling that reversed an earlier doc on evidence — `Computer Science` and `Software Engineering`). **That exclusion set is what later falsified Slice 2's premise**, so it is a live architectural fact, not history. Pool/bank re-keying moved persisted quiz reuse off the *reader's* level onto the note's. A full pressure test found four defects, the sharpest being a permanently-memoised fail-open fallback and a Target Audience rewrite on a live Public Library filter — both interactions between separately-reviewed PRs.

**v0.69.0 - Canonical Knowledge Foundation (Released and deployed 2026-08-04, PR #981).** See Current baseline above for full detail — Domain Context, Note Learner Level, the legacy level-in-program backfill (49 notes; the ambiguous `High School` ones classified per note from actual content, six deliberately left unclassified because they turned out not to be K-12 curriculum notes at all), two deferrals to `v0.70.0`, and an R4 checkpoint. **Two process lessons worth carrying forward:** (1) the whole-release pressure test earned its cost — both blockers lived outside any single PR's diff, one in a file no PR touched; (2) an unrelated production bug (`Create from topic` intermittently rejecting valid formula content) was found sitting *untracked* in `docs/claude-prompt/`, which exposed that the Backlog Index invariant cannot see a planning file that was never committed to git.

**v0.68.0 - Topic Note Rename (Released 2026-08-01, PR #966, four feature PRs).** — the "Create a Note" rename, the "topic note" quota vocabulary, the Messaging Architecture's first `/pricing` slice, the Companion Guidance Doctrine text, and a five-agent pre-signoff pressure test whose ~30 findings included a backend/frontend copy split in one alert element, a four-renderer layout regression, and four wrong counts in `RELEASES.md` itself.

**v0.67.1 - Explore Convergence Follow-ups (Released 2026-07-31, PR #959).** Cleaned up `v0.67.0`'s Known Limitations: Explore's hardcoded "Review Sets" tab label colliding with `BOARD_EXAM`'s Collections nav label, Exam Hub's official-set card hardcoding `adoptedCollection={null}`, two stacked Explore CTAs on the zero-note Dashboard, an unreachable `?ref=` back-link, a dark-mode contrast bug on Explore's segmented control (found from a live screenshot, not any test), plus two pre-existing bugs surfaced only by explicit owner review of the full list. **Worth remembering as a pattern:** the release scoped three candidates and shipped seven — its own tagging under-counted it by more than half.

**v0.67.0 - Explore Convergence (Released 2026-07-30, PR #949).** New "Explore" nav item (desktop and mobile) replacing the standalone "Public Library" item, compositing the Official Review Set catalog (`/collections/published`) and the public notes library (`/public/library`) behind a segmented control, plus an additive exact-`courseProgram` Official Review Set preview/adopt path on the anonymous-accessible Exam Hub pages. Shipped via an **explicit owner override of its own stated gate** — the Diagnostic Read (due after 2026-08-06, still not re-run) was supposed to show a discovery problem first. A dated **`[CHECKPOINT — due 2026-09-13]`** was committed at kickoff, with a new `EXPLORE_VIEWED`/`EXPLORE_TAB_SWITCHED`/adopt-click analytics set shipped specifically so the override can be checked against real engagement rather than being ship-and-forget. Codex-delivered, then audited against the real diff by an independent fresh-context Opus review, which caught a real gap: no page-view event for `/explore` itself, which would have made that checkpoint unmeasurable.

**v0.66.2 - Card Surface Token Fix (Released 2026-07-30, PR #947).** Registered a `--color-card` Tailwind v4 theme token that had silently never existed, so `bg-card` (23 literal usages across 9 files) had resolved to no CSS output since it was first used — every one of those usages read as a bordered-but-unfilled panel in production and no one had noticed. Aliased to `--background` based on `getComputedStyle`-verified nested-panel evidence, not a screenshot alone — a requested independent Codex audit caught a real reasoning error in the original scoping (a claimed "deliberate override" on two `Card` call sites that, per compiled-CSS cascade order, was never actually happening) and corrected it before signoff.

**v0.66.1 - Goal Detail Due-Concept Signal (Released 2026-07-30, PR #945).** Small, ungated Post-v0.40.0 Polish Backlog candidate: an amber due-color warning on a Goal's child Subject-plan cards when `dueConcepts > 0`, plus a doc fix formalizing an already-shipped, previously-undocumented second exception to the collection-detail no-mastery rule.

**v0.66.0 - Challenge Quiz Result Clarity (Released 2026-07-30, PR #943).** Consolidated standard Challenge Quiz mode's result branch onto the same `ScoreReveal`-based pattern Board Exam Mode already used, closing a Known Limitation logged at `v0.65.0` signoff (Challenge Quiz's result screen read as an accreted card stack, the weakest of the three). An owner-requested independent Opus review (fresh context, real diff) found and fixed two real presentation gaps before merge.

**v0.65.0 - Study Effectiveness Polish (Released 2026-07-30, four PRs: #938, #939, #940, #941).** Closed out the remaining real candidates from the 2026-07-22 Study Effectiveness/UI Polish consultation: Study Pack scope surfacing on note cards/Summary tab, a card-accretion layout pass across Review Set Detail and quiz result screens, a collapsed-Companion teaser fix, and an Adaptive Practice per-question rationale tag (why this concept was selected — due, weak, or both).

**v0.64.0 - Add to Review Set (Released 2026-07-29, PR #935).** Ships "Add to {Review Set}" on Note Detail, extracted from the existing `AddImportedDraftsModal` — the one durable piece of a broader GPT-proposed "Note Detail transition features" idea that survived an Opus pressure test (see Companion Scoping section below for the full resolution of that side discussion, including why a Note Detail Companion nudge and subject-matching browse feature were both rejected).

**v0.63.0 - Ask Companion (Released 2026-07-29, three PRs).** Ask Companion grounded Q&A (PLUS/PRO) and twice-missed concept → Ask Companion — see Current Baseline above and the Learning Companion vision section for full shipped detail. Owner-requested Opus pre-signoff pressure test fixed five issues (most notably an OpenAI multi-turn conversation bug) and logged seven Known Limitations. Full detail: `docs/releases/v0.63.0.md`, `docs/product/ROADMAP.md`'s own "v0.63.0" section.

**v0.62.0 - Knowledge Impact (Released 2026-07-29, four PRs).** A private "Your Impact" dashboard on the creator's own public profile — distinct learners helped, weighted toward a genuinely *completed* downstream quiz session, never raw views/copies. Shipped despite its own data gate failing (3 non-official public-note creators against a 20-30+ un-park threshold) — a deliberate, ratified bet with a 2026-09-11 kill-criterion checkpoint, not yet run. New conditional-rate analytics events make that checkpoint measurable; an opt-in monthly digest reuses existing Email Preferences infrastructure. A pre-signoff pressure test found and fixed a real correctness bug: all three "learners helped" queries counted `completedAt` alone, but Long Exam/Interview Practice both set it on forfeit too — fixed by requiring `status = COMPLETED`. A bonus fix, unrelated: stale legacy `PREMIUM_MONTHLY_*` env vars were silently overriding current Pro quota defaults in production. Full detail: `docs/releases/v0.62.0.md`.

**v0.61.0 - Challenge Quiz Quota Increase (Released 2026-07-28, two PRs).** Monthly quota FREE 5→20, PLUS 25→100, PRO 50→200 — uniform 4x, preserves the 1:5:10 tier ratio, reuses existing rolling-billing-period tracking. Challenge Quiz LLM token/cost telemetry — wires the same token-extraction machinery Study Pack generation already uses into the Challenge Quiz path, persisted on `quick_review_sessions`, accumulated across a session including "+5" growth; Board Exam, Long Exam, and Adaptive Practice out of scope. Bonus: the `ONBOARDING_V2_ABANDONED` instrumentation fix (see Company Redefinition section above). Price decrease deliberately deferred, not part of this release. Full detail: `docs/releases/v0.61.0.md`, `docs/product/ROADMAP.md`'s own "v0.61.0" section.

**v0.60.x line (Challenge Quiz, all Released, all patch releases off `v0.60.0`, none consuming a minor-version slot):** `v0.60.3` (adaptive question count, Redo Missed Questions session-matching fix, incomplete-submission guard — bundled into one Codex prompt/PR, audited before commit, caught and fixed a real race condition in the redo-session provenance marker); `v0.60.2` (3 known-limitations closed: claim-release transaction isolation, an expiry/completion lock race, a missing Resume/Start-Fresh prompt); `v0.60.1` (5-bug fix pass on the newly-shipped template-sharing feature: executor saturation, a whole-array shuffle that never existed, an inert difficulty selector removed entirely, abandoned-session auto-submit, a non-functional Redo Missed Questions button); `v0.60.0` (Shared Official Pool Foundation — Phase 3a, Challenge Quiz question template sharing across users adopting the same Official content).

**v0.57.0 → v0.59.0 (Company Redefinition Phases 1-2, Reusable Practice):** `v0.59.0` Dashboard & Progress Reorg (Primary Review Set condensed card on Dashboard, Progress promoted to first-class nav); `v0.58.0` Reusable Practice Assets & the Return Loop (per-user pooling turned on for Board/Long Exam, extended to Challenge Quiz, "redo what you missed"); `v0.57.0` Practice-First Activation Onboarding (Phase 1 — see Company Redefinition section above for all three).

**v0.51.0 → v0.56.0 (retention-pivot posture — new-user acquisition + real-time friction capture, not cold outreach to churned users):** `v0.56.0` weak-concept explanation links on quiz results; `v0.55.0` Result-Screen Companion Bridge; `v0.54.1` public note copy correctness fixes; `v0.54.0` CPALE Exam Hub (Wave 2); `v0.53.0` SEO discoverability (Exam Hub depth, organic attribution); `v0.52.0`/`v0.52.1` proactive in-app feedback prompts at first-quiz and early-lifecycle moments (the pivot-defining release — cold outreach to churned users was judged unlikely to work, so the posture shifted to *"we're not chasing our previous users anymore, we're now chasing new users to retain"*); `v0.51.0`/`v0.51.1` read-path performance pass (Private/Public Library, Collection detail, Dashboard).

**v0.39.0 → v0.50.4 (mobile nav, flexible review formats, retention/conversion UX audits, Companion MVP through Mentor Tips):** mobile bottom tab bar (`v0.50.0`+ polish); Identification/Enumeration question formats and Flashcards/Memorization non-engine review surfaces (`v0.39.0`); the full Learning Companion build-out `v0.41.0`→`v0.43.1` (see dedicated vision section above); a multi-release conversion/retention UX audit (`v0.44.0`→`v0.47.1`) that established retention as the proven constraint over conversion.

**v0.31.0 → v0.38.0 and earlier:** the Note Collections adoption model, Goal/Subject hierarchy, Builder, and readiness-as-retention-lever arc (see dedicated vision section above); read-path optimization; production memory-incident response; premium exam paywall/pricing-as-one-time-pass groundwork.

---

## Roadmap Candidates: Gated & Ungated

Synthesized from `docs/product/ROADMAP.md`'s Backlog Index (~55 rows) — the authoritative table. **Shipped/resolved/superseded rows are dropped from this view entirely** (check `RELEASES.md` for those) — this section is restricted to what's actually still open, grouped by status. **Treat the Backlog Index itself as the source of truth if the two ever disagree.**

### Active now — no gate, just not yet done
- **`v0.71.0` Slice 3 — Program Family expansion.** The one live gate; see "Open decisions: Slice 3" above. Four rulings needed before it can be scoped at all.
- **Retiring the legacy-string discovery fallback.** Slice 2 left `notes.course_program` load-bearing on read paths for notes with no join row. Retiring it is **unscheduled** and depends on what happens to notes carrying catalog-excluded values — a population that currently **grows**, because the legacy course/program field still accepts freetext (`CourseProgramCombobox` defaults `allowCustom = true` and neither authoring surface overrides it). A production query sizing this exists at `19-slice-2-facet-equivalence-impact.sql`.
- **`notes.target_profile_type` redundancy check.** ADR-001 says to judge at the *end* of Slice 2, against real filter usage, whether precise program facets make the coarse 3-value audience facet redundant. Slice 2 has now merged, so this is due — and it is a product question, not an engineering one.
- **Messaging Architecture rollout (ratified 2026-08-01, one slice shipped).** No gate on the architecture — it is an owner decision, and explicitly **not** gated on a conversion experiment. `/pricing` shipped in `v0.68.0`; **the landing page, paywall modal, Exam Hub upsell, `PLAN_COMPARISON_ROWS`'s "Best for" row, and per-plan feature bullets are all still un-actioned**, each needing its own scoping pass and `/kickoff`. `FREE.title` is the one already-identified concrete item and must be written against the locked hierarchy rather than derived from Plus/Pro consistency. Explicitly incremental — do not propose this as one release.
- **Company Redefinition Phase 4 items 5 and 7.** Item 5 (which IA/landing changes follow from the packaging recommendation, e.g. whether Exam Hub changes structure) and item 7's implementation substance — the unexecuted **"AI" de-emphasis rename** in `fable-out/06`: ~13 actionable rows across `ai-suggestion-modal.tsx`, `profile-learning-section.tsx`, `paywall-content.ts`, `learn-guides.ts`, `study-packs-guide.tsx`, `professional-guide.tsx` ("AI Suggestions" → "Suggested Details", "AI Critique" → "Answer Critique", Help-guide mechanism copy to NoteLib-as-actor). It touches public SEO content and landing copy, so it needs its own scoping pass and `/kickoff` — **not a fold into an unrelated release.** Item 9 (prices, quotas, pass durations, checkout mechanics) is out of scope by the source doc's own constraint.
- **User interviews (retained + churned exam-dated).** Script written, zero engineering cost. The one item on the entire retention track that can't happen from a keyboard.
- **P1 (Google Search Console setup) and P3 (exam-named Learn guides).** Non-engineering — P1 needs domain access, P3 needs a human curator.
- **Knowledge Impact.** Ratified 2026-07-28 despite a failed data gate (see Company Redefinition section above) — needs a scoping pass before it can kick off. Design brief already exists in `company-redefinition-out/09-knowledge-impact.md`. **Checkpoint committed 2026-07-28:** re-run the gate query 30-60 days after this ships — a continued-zero or still-collapsed community-publish rate settles the "can we create creators" hypothesis for good, not a reason to keep waiting. The only trend data available now (publish rate 1→3→0 across May/June/July, zero in the highest-signup month on record) argues against the hypothesis rather than being neutral.

### Gated — condition is close or partially cleared
- **Onboarding coverage-gap capture (deferred out of `v0.60.3`).** Design done, unchanged, still valid. Gated on the Diagnostic Read closing (~2026-08-06) AND a clean re-run of its gate query (`STUDENT`-profile presence in the surge cohort must read effectively zero — it currently doesn't, 2/29). **Further from clearing as of 2026-07-28's population-mix query:** `STUDENT` is 27.09% of profile-typed accounts product-wide, not a surge-only artifact — this isn't a narrow surge-cohort question anymore.
- **Retention H1+H5.** Gate: v0.48.0 cohort re-read positive-or-ambiguous. Window closes 2026-07-29 (tomorrow, as of this update) — not yet run.
- **Wave 2 Exam Hubs beyond CPALE** (Civil/Electrical/Mechanical Engineering, Pharmacy, Physical Therapy, Civil Service Exam). Each needs its own production depth check (~25-30 notes); CPALE cleared this, the rest haven't been checked.
- **Price decrease.** Deferred as of `v0.61.0` — needs paywall conversion / onboarding retention / post-quota-increase usage data before revisiting.
- **P7 (exam quick-facts block per hub)** — wait for GSC (P1) first. **P8 (off-page community presence)** — non-engineering, needs an owner to do outreach. **L2 (earned-depth pathway for non-exam subjects)** — double-gated on depth + post-GSC organic-impression data.
- **F9/F10 (client-side caching, denormalized engagement counts)** — pending production evidence that hasn't been checked.
- **Study Effectiveness remaining candidates** (Note Detail tab-order, Study Pack scope surfacing, Adaptive Practice per-question rationale tag, Review Set Detail layout pass, twice-missed-concept re-explanation, Plus-tier review-timing-gate instrumentation). One item from this batch already shipped as `v0.56.0`; the rest are unscoped.
- **Parent Readiness Digest.** Conditional on the H1 read being positive AND an explicit shape decision.
- **Offline Study Pack access.** Heavy mobile usage confirmed (~75%); PDF export volume ruled out as the offline-fallback signal; needs either offline-fallback hit-rate instrumentation (doesn't exist) or a direct interview signal.
- **Bulk Quiz Generation & Teacher-Flow Polish.** Auto-schedules once ≥5 active teacher accounts exist.

### Held indefinitely — behind the retention constraint clearing
- **App Shape Core** (Companion Live Milestones, Concept-to-Note Back-Annotation, "Struggle Map") and **App Shape Polish stragglers.**
- **Photo Capture of handwritten notes.** Next recommended Core-Feature-sized bet once the retention loop is actually proven (W1→W2 lift confirmed), not before.
- **Smart Review Planning (Internal Curator, 7 fully-architected docs in `docs/claude-prompt/fable-out/`).** The single largest piece of paused planning material in the repo. Triple-gated: interviews confirm content-gap churn AND a manual coverage sprint proves lift AND hand-curation saturates.
- **Manual Official-coverage sprint** (hand-curate ALE/PNLE/LET/CPALE gaps). Conditional on interviews surfacing "no content for my exam" as a real churn reason.
- **Listen Mode / Bilingual UI / Study Buddy.** Low priority, gated on an interview signal. Study Buddy specifically confirmed lowest — a pairing mechanic multiplies churn risk at 2.4% W1→W2 rather than countering it.
- **PDF export surfacing.** Do not build — near-zero usage (1 export, ever).
- **Conversion-audit deferred pair** (adoption-count social proof, "Trending this week"). Held on windowed backend engagement counts that don't exist.

### Parked — needs an explicit product go-decision, not a data gate
- **AI-generated Review Sets.** Curator pipeline (public notes → suggest Subject Plans → generate Companion → human review → publish). Effectively closed/ruled out — re-affirmed by the locked curation-never-generation architecture.
- **Runtime Companion — Personalization (PRO) only** (Ask Companion, the other half, **shipped as `v0.63.0`** — see Current Baseline above). Gated on the still-open Primary-Review-Set-vs-Study/Exam-Focus philosophy question, which Ask Companion's own gate (the persisted Companion existing since v0.41.0) didn't share.
- **Review-Set-Centric Navigation** (Official catalog as the scalable replacement for hand-built Exam Hub pages). Direction only — Phase 2's Explore Convergence is a bounded step toward this, not the full thing.
- **Deeper plan nesting (3+ level hierarchy).** Feasible but a real project (cycle detection, recursive readiness rollup) — nice-to-have, no gate stated.
- **Note Detail readiness as its own tab.** Blocked on a mobile tab-overflow design pass.
- **Legacy "Future Directions" block** (pre-v0.20 items). Explicitly flagged stale — needs a fresh audit before anything in it is trusted.

---

## Core Feature Surfaces

### Navigation (App Shell)

Three coexisting navigation surfaces, **updated in `v0.67.0`**: **desktop sidebar** (Dashboard / profile-aware Collections label / Library / Explore / Progress), **mobile hamburger drawer** (same, full nav on mobile), **mobile bottom tab bar** (persistent 4-tab subset — Dashboard, Library, the Collections label, Explore — icon+text, below the `md` breakpoint, auto-hides during exam focus/active assessment). "Public Library" is no longer a standalone nav item on either surface — `/explore` composites it with the Official Review Set catalog behind a segmented control (see Current Baseline / Open Question above); `/public/library` and `/collections/published` both remain live, unchanged routes, just no longer directly nav-anchored. Progress stays off the mobile tab bar, deliberately not a 5th tab there. Do not add a 5th mobile tab or expand the tab bar's scope without checking `RELEASES.md` v0.50.0's anti-drift notes first.

### Landing / Public

- Marketing positioning is notes-library-first: notes -> summaries -> quizzes -> review.
- Public nav exposes Home, Public Library, Learn, Pricing, Login, Get Started. Public Library is accessible without login.
- Public legal routes: `/privacy`, `/terms`. Contact email: `support@mail.notelib.app`.
- Branding uses the NL monogram for navbar/app shell/favicon and full logo for marketing headers/footers.

### Library and Notes

- Library is the authenticated note workspace. Notes can be private or public.
- Note creation must respect profile setup, target audience defaults, and Study Pack usage rules.
- **Terminology, locked in `v0.68.0` — get this right, it is the most recently-enforced naming rule in the product.** Drafting a note from a bare topic prompt is **"Create a Note"** (never "Generate Note"), and its metered monthly allowance is a **"topic note"** in all user-facing copy. **"Generate" is reserved for operations that transform the learner's own material** — "Generate Study Pack", "Generate Quiz", "Regenerate", and the `Retry Generation` failure label all keep it deliberately. Internal names are unchanged and should stay that way: `noteGenRemaining`, `noteGenerationsRemaining`, the `note-generation-limit` CTA context, and the `GENERATE_NOTE_LIMIT` / `GENERATE_NOTE` analytics identifiers. "Note draft" is **not** available as a synonym for the allowance — `Draft` is already a user-visible state meaning "no Study Pack yet", and a hand-written note is also a Draft while consuming zero topic-note quota.
- Async generation saves the note first, marks it `GENERATING`, redirects to Note Detail, and lets Note Detail poll. Failed generation preserves note content and exposes `Retry Generation`.
- Note Detail is the owner study hub: summary, key concepts, quiz, full notes, practice actions, recent sessions, readiness signal, Flashcards/Memorization entry points. Key Concepts entries sort by readiness (struggling → due → not-started → mastered) once ConceptHealth loads.
- Quiz result screens carry two authored/derived guidance surfaces: a `CompanionResultBridgeCard` excerpting the primary Review Set's Companion content, and deep-links from missed/weak concepts to their matching Key Concepts explanation. Both are same-session learning aids, not retention/return mechanics.

### Public Notes and Profiles

- Public note detail is read-only and separate from private Note Detail.
- Public note actions copy/create private owned notes first; private study actions never run against a public source note.
- Public Profile is `/public/creator/{username}` canonical, `/public/profile/{userId}` legacy-compatible.
- Profile Settings (`/profile`) is private editing; Public Profile owns visibility and sharing.

### Note Collections (Study Plans / Review Sets)

See the dedicated vision section above. Quick reference: a collection is a top-level **Goal** or a **Subject** (child of a Goal, or standalone) — exactly two levels. Published/admin collections are source plans; adoption creates owned snapshot copies. Recommended plans surface course/program-scoped on Dashboard and `/collections`; `/collections/published` is the full browse surface. The Builder is the single authoring canvas. Plan detail execution rows show action/status, not mastery — dedicated readiness detail lives at `/progress?collectionId={id}`. Top-level Goal detail renders `TodaysFocusCard` (Coach) → Progress (readiness + countdown) → collapsed `CompanionDisplayCard` ("View Full Guide").

### Progress and Readiness

- `/progress` is available to all plans, the canonical subject-level detail surface, including plan-scoped readiness via `?collectionId={id}`. Reads ConceptHealth only.
- Subjects group by Study Pack subject; blank/null subject is `Other`.
- Classification: mastered (recent correct signal), due (stale correct signal), not started (no correct signal), struggling (latest incorrect newer than correct).
- Goal milestones are fixed read-time checkpoints, not persisted. Note and plan readiness reuse this spine.

### Settings, Account, and Email

- Settings order: Preferences, Plan & Billing, Account. Preferences include Learning Style (`engagementMode`) and Study Reminders.
- Account deletion is soft-delete first, purge later. Data export is owner-only, excludes secrets/analytics/billing.

### Admin

- Admin Dashboard is internal, read-only v1, ADMIN-only — overview, billing, engagement, public-content growth, recent upgrades, failed payments, feedback.
- Feedback submissions persist `message`, authenticated `userId`, `email`, and current page URL.

---

## Non-Negotiable Rules

- Do not reuse student quiz session logic for teacher preview.
- Do not auto-regenerate generated content.
- Do not grant paid access from frontend logic or redirect callbacks.
- Do not hardcode backend checkout pricing.
- Do not add new chart libraries for readiness.
- Do not add batch/progress infrastructure except the documented v0.29.1 terminal bulk-generation receipt.
- Do not expose Study Plan mastery/readiness on execution rows, list cards, published-plan cards, or public source plans.
- Do not expose per-concept review timing to Free users on Note Detail.
- Do not move Learning Style or reminder preferences into Profile.
- Do not label nav as "Account Settings"; use "Settings".
- Do not let Flashcards or Memorization write `ConceptHealth` — every quiz-session mode, including Quick Review, does.
- Do not plan-gate question *formats* — format variety is a learning-quality dimension, not a monetization lever. Gate modes/workflows/quotas instead.
- Do not nest Note Collections beyond two levels; no per-module mastery.
- Do not redesign the locked `/onboarding` flow.
- Do not let a learner receive an auto-generated Companion/Mentor Tip — curator-facing AI-assist only, mandatory human review before publish, never autonomous.
- Do not serve the Companion via a per-view/runtime LLM call — authored once, served static.
- Do not reorder the five authored Companion sections or infer a Mentor Tip's linked action at render time.
- Do not make Mentor Tip/Companion surfacing adaptive or LLM-driven — deterministic date/progress rules only; that tier is reserved for the gated PRO Personalization candidate.
- Do not let a surfacing condition permanently hide a Mentor Tip — "View Full Guide" always lists every authored tip.
- Do not backfill a reminder-email preference default change onto existing users — new-signup defaults apply only at account creation.
- Do not propose or re-surface roadmap candidates without checking `docs/product/ROADMAP.md`'s Backlog Index first.
- Do not treat quota/pricing numbers as living in one place — backend enforcement (`application.yaml`) and frontend marketing copy (`frontend/lib/pricing-config.ts`) are independent and must move together.
- Use `globalThis`, not `window`, in frontend code.
- Backend exceptions should be named `AppException` subclasses, not inline raw `new AppException(...)`.
- Repeated logic-bearing strings should be constants. Java range clamps should use `Math.clamp`.

---

## How We Work Together

- GPT: product thinking, roadmap decisions, UX philosophy, feature scoping, architecture tradeoffs, implementation prompt drafting.
- Claude Code: implementation prompt drafting, doc writing, code review, small direct changes.
- Codex: standard implementation, multi-system changes, refactors, tests.

For implementation tasks, GPT output should usually be a structured Codex prompt. Use the repo skills:

- `docs/skills/roadmap-feature-audit.md` before scoping roadmap/feature work.
- `docs/skills/codex-prompt-generator.md` before writing Codex prompts.
- `docs/skills/ux-product-review.md` for UX/product review.
- `docs/skills/release-doc-alignment.md` after shipping code/doc changes.

Always check `docs/codex-prompts/` for an existing prompt before writing a new one.

---

## Prompt Format To Preserve

Long mode for new features, backend changes, multi-system work, or behavior/documentation updates:

```markdown
Prompt mode: Long

Use the following docs as the source of truth:
- AGENTS.md
- docs/product/ROADMAP.md
- docs/features/[feature].md

## TASK
## GOAL
## CONTEXT
## REQUIRED CHANGES
## TESTING
## DOCUMENTATION
## CLEANUP
## ACCEPTANCE CRITERIA
## OUTPUT
```

Short mode for small UI polish, narrow bug fixes, and incremental follow-ups:

```markdown
Prompt mode: Short

Use the following docs as the source of truth:
- AGENTS.md
- docs/features/[feature].md

## TASK
## GOAL
## CHANGES
## ACCEPTANCE CRITERIA
## OUTPUT
```

Prompt rules:

- `CONTEXT` is the most important section.
- Paste the relevant anti-drift rules from `AGENTS.md`.
- `DOCUMENTATION` always includes `RELEASES.md` when behavior changes.
- Acceptance criteria must be checkable.
- Mention whether the task should avoid quota, billing, pricing, checkout, AI, schema, or new infrastructure changes.

---

## Model / Effort Recommendations

| Task type | Tool | Effort |
|---|---|---|
| UX / product review | Claude Sonnet | Standard |
| Architecture discussion | Claude Sonnet or Opus | High |
| Roadmap / doc review | Claude Sonnet | Standard |
| Prompt drafting | Claude Sonnet | Standard |
| Standard feature implementation | Codex | Medium |
| Multi-system or ambiguous implementation | Codex | High |
| Refactor / cleanup / migration | Codex | Medium |
| Tests for agreed behavior | Codex | Low / Medium |

---

## Key Source-of-Truth Docs

- `AGENTS.md` - implementation rules and anti-drift constraints
- `CLAUDE.md` - Claude Code routing, commands, and commit rules
- `RELEASES.md` - current and historical release state
- `docs/product/ROADMAP.md` - current release sequencing and future scope; its Backlog Index table is the authoritative list of every open candidate, its status, and its gate condition
- `docs/product/SPEC.md` - canonical product behavior
- `docs/product/EXAM_MODES.md` - locked quiz mode hierarchy, question formats, and non-engine review surfaces
- `docs/product/PLANS.md` - plan tiers and quotas
- `docs/features/companion.md` - Learning Companion / Coach / Mentor Tips behavior rules
- `docs/features/` - per-feature behavior rules
- `docs/codex-prompts/` - ready prompts for active work
- `docs/releases/` - per-version release notes
- `docs/skills/` - reusable AI workflow guidance

---

Context loaded. What are we working on?
