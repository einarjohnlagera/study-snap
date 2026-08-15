# GPT_CONTEXT.md - NoteLib Product Context Handoff

> **This is the core brief. Paste it as your first message in a new GPT chat session.**
> Then paste any module below that matches the conversation — see "Which modules to paste".
> Update this file whenever a new version ships or the roadmap shifts significantly.
> Last updated: v0.79.0 (Released) - 2026-08-15

---

Here's the current context for our NoteLib product session. Treat this as a compact product snapshot, not a replacement for the repo docs. For implementation work, always defer to `AGENTS.md`, `RELEASES.md`, `docs/product/ROADMAP.md`, and the relevant `docs/features/*.md`.

---

## START HERE — orientation in 19 lines

Read this block first. Everything below is detail behind it.

1. **What it is:** NoteLib is a notes-first study workspace. Capture notes → generate Study Packs → practice with quizzes → track readiness → reuse the library.
2. **What we sell:** the *learning system*, not features. Hero: **"Always know what to learn next."** Features are evidence for that promise, never the promise itself. **Ratified 2026-08-05: NoteLib is a learning system built on top of a knowledge library — the library is the foundation, the learning journey is the product.** Three layers earned strictly in order: **Trust** (comprehensive Official Review Sets) → **Habit** (Study Packs, Companion, Progress, Review Sets) → **Community** (user-created knowledge). **Community content is no longer the primary acquisition strategy** — the community vision is not abandoned, it is being earned in the correct order. Do not propose UGC as top-of-funnel.
3. **Current state:** `v0.79.0 - Catalog-First Vocabulary` is **Released 2026-08-15**. **No version is currently open.** Learner Course / Program fields on `/profile`, the Note Editor, private Note Detail and the Dashboard profile-completion prompt now suggest the canonical 21-row catalog first (free text still allowed, nothing migrated), and the public program filter lists catalog names only. **The pre-deploy baseline narrowed what this can achieve, and it is recorded as such:** learner *notes* were already only **0.6%** off-catalog, while learner *profiles* are **13.9%** (32 of 231) — and a profile program is set overwhelmingly during **onboarding**, which this release deliberately excluded to protect `[CHECKPOINT — due 2026-09-11]`. **So this release is the machinery and the measurement; the onboarding follow-up is the intervention**, and it carries its own Backlog Index row gated on that checkpoint. **It is the counter-proposal, NOT the `ADR-001` amendment** — locking the field and the *Request Program* queue remain unratified. Previous: `v0.78.0 - Post-Mastery Next Step` (Released 2026-08-15).
4. **The single biggest constraint — RESIZED 2026-08-11, and the correction matters more than the number.** For months this line read *"W1→W2 retention is 2.4%."* That figure counted only activity in **days 7–14** after a learner's first Study Pack, and a production read found it sees **3 of the 11 learners who actually returned** — it misses everyone coming back in days 2–7 and everyone returning after day 14. Measured over a wider window, retention is **~7.2%**. **Both halves matter:** the number we steered by understated reality ~3.7×, *and* 7.2% is still bad — 141 of 152 activated learners never came back at all. The constraint is real; it was **mis-sized, not imagined**. Do not treat this correction as good news, and do not quote 2.4% as a current figure. Read "Retention Is the Proven Constraint" before proposing anything new.
5. **The funnel's largest single leak is onboarding, not retention or verification.** Of 375 all-time signups: 366 verify their email (97.6%), **234 complete onboarding (62.4%)**, 195 generate a first Study Pack (52.0%). So **132 people — 35.2% of everyone who ever signed up — verify and then never finish onboarding.** Verification loses 9. `v0.73.0` was built against this and is now being judged by a `[CHECKPOINT — due 2026-09-11]` measuring onboarding completion for **post-deploy signups only** against the **62.4%** baseline. **Kill criterion:** if it is not measurably above 62.4%, the comprehension hypothesis is unsupported — reopen the framing rather than proposing more onboarding polish. If completion *dropped*, the 5→8 screen split is the first suspect.
6. **Activation is already 52.2%, which structurally caps "get more users activated" as a lever.** Because returning learners = activated × retention rate, both levers are multiplicative, so the comparison is rate-independent: perfect activation would beat doubling retention only if activation were below 50%. It isn't. A read in `v0.72.1` tested exactly this and the volume hypothesis failed. Do not propose activation-volume work on the grounds that it will move returning-user counts — propose it, if at all, on comprehension or product-quality grounds.
7. **Decided 2026-08-12, and easy to re-derive wrongly: the onboarding exam-date field STAYS.** It is duplicated by the post-session commitment prompt, which prefills and requires the date — so removing it looks like obvious tidying. It is not: that prompt renders on session-*completion* screens, so it reaches only learners who finish a session, while onboarding reaches everyone who gets to Screen 3. Removing it would leave exam-bound learners who never complete a session with **no exam date at all**, disabling the board-exam countdown and degrading the exam-date segmentation the target-habit definition depends on. The field is also **optional**, so it was never a second required question. **Do not propose removing it without new evidence about the non-session-completing population.**
8. **Ten dated checkpoints are live, the earliest 2026-09-10.** 09-10 H1+H5 proximal (carries a kill criterion) · **09-11 onboarding completion** *and* Knowledge Impact · **09-12 ×2, both from `v0.74.0`** (the Quiz-unlock rate, and whether removing Adaptive Practice from Quick Review cost it adoption) · 09-13 Explore engagement · **09-14 `v0.78.0`** (does a NAMED plan recommendation convert where a generic pointer did not — kill criterion on the proximal Dashboard arm) · **09-14 `v0.79.0` proximal** (does catalog-first ordering change what learners pick — kill criterion says onboarding is the whole intervention if not) · 10-15 Challenge Quiz read (c) **and `v0.79.0` distal** · 11-09 H1+H5 distal. **The live consequence is sequencing:** a proposal that changes what a checkpoint measures before it is read destroys the read. That is not hypothetical — it is why `v0.76.0` is a copy slice rather than anything better, and why the post-mastery next-step and the onboarding-vocabulary residuals are both parked until mid-September **on measurement grounds, not merit**.
9. **A framing died on real data 2026-08-13 — do not repeat it.** The Challenge Quiz adoption reads finally ran. Conversion after the `5/5 → 4/5` promotion change: **41.2% → 44.7% at 24h** and **58.8% → 47.0% at 7d**, neither distinguishable from zero — **while promotion exposure rose 68.6% → 92.0%.** A population *more* eligible for the lever converted no better, which makes it a harder negative, not a confounded one. **So *"low Challenge adoption is a motivation problem, not a placement problem"* is now UNCONFIRMED and should be reopened, not patched with further promotion tweaks.** The ineffective change is already reverted (`v0.74.0` moved the threshold back to verified mastery). One read remains — CTA impressions vs. clicks, newly measurable — at `[CHECKPOINT — due 2026-10-15]`.
10. **Two product rules shipped in the last two releases that proposals routinely violate.** (a) **The Study Pack's Quiz tab is LOCKED until the learner scores a perfect Quick Review** (`v0.74.0`). It stays visible and explains itself; it is not hidden. The reason is not gamification — that tab rendered the saved quiz **with answers revealed**, and Quick Review administers *those same questions*, so it was the answer key to its own test, which corrupted `ConceptHealth`. **Mastery = a perfect score, and reaching it via the retry round counts.** Challenge Quiz stays open from the start, because it generates its own questions and cannot be spoiled. **Adaptive Practice is no longer offered from Quick Review at all** — it remains reachable from the Dashboard and the mode-selection screen. (b) **Curator note authoring now pre-fills depth** (`v0.75.0`) from the selected Review Set, falling back to the author's own profile level. **Pre-fill only, never a server-side default write**, and **curators only** — the control is not shown to learners, so nothing is written on their behalf. The user-facing label is now **"Authored Depth"** (the column is still `notes.learner_level`). **Domain Context is never inferred** — there is no authorized source for it, and `domain_context IS NULL` is the promotion-backlog marker.
11. **Pricing is settled for now** — quota raised, price change deferred pending data. Don't reopen it.
12. **Positioning copy no longer needs a conversion test** (owner call, 2026-08-01). Pricing/checkout *mechanics* still do.
13. **Mastery comes only from graded assessment.** Self-review surfaces (Flashcards, Memorization) are firewalled from readiness by design.
14. **There are exactly 5 quiz modes.** The contract is locked. Do not propose a 6th.
15. **Biggest recurring failure mode in this repo:** repo-wide copy/terminology changes that under-scope themselves. `v0.68.0` under-counted its own sweep four times; `v0.67.1` scoped three items and shipped seven. Assume one grep is insufficient.
16. **A note is classified on five independent axes, and one note can serve many programs.** Subject (*what*) · **Domain Context** (*how it is authored* — **the sole LLM domain constraint**, 8 ratified values) · Note Learner Level, labelled **“Authored Depth”** in the UI since `v0.75.0` (*how deep* — a curriculum floor, and yes, a note owns its own depth; the rename was copy-only, the column is still `notes.learner_level`) · **Applicable Programs** (*where it appears* — one or many catalog programs, **discovery only, never reaches a prompt**) · Target Audience (*who* — discovery only, never depth). **Never propose the same note once per program** — that duplication is what Release B exists to remove. Multi-program notes **require** a Domain Context. Full detail, the 21 catalog programs and the 8 Domain Context values: `NOTES_AND_COLLECTIONS_CONTEXT.md` §0.
17. **Open questions awaiting your input:** (a) Explore's default tab — still unresolved three releases later; (b) ~~whether an admin curating a learner's own note should be constrained~~ **RESOLVED 2026-08-11 and shipped** — `ADR-001` → *Curation authority*: an Applicable Program row may only be authored onto a note its author owns. Ownership rather than visibility, because a learner can flip a curated public note back to private; no `source` provenance column was needed. **Settled, do not reopen:** the learner-depth question (2026-08-04 — notes own their depth; the *“authoring by inference” direction it left open **shipped as `v0.75.0`**, so that is no longer a pending direction either); **R4** (passed 2026-08-04 — it validated the Domain Context *value set*, **not** applicability, and must not be cited as settling anything about which programs a note applies to); and the four Program Family decisions (ratified 2026-08-05, shipped in Slice 3).
18. **Don't propose:** a 6th quiz mode, price changes, AI-generated per-concept definitions, feeding self-review into readiness, user-facing "Creator"/"Curated Learning" labels, a 9th Domain Context value, or duplicate notes per program.
19. **The repo is `studysnap` internally.** The product is NoteLib. Database and package names still say `studysnap` — that is intentional, not debt to fix.

---

## Which modules to paste

This file used to carry everything, which cost ~27k tokens before the conversation started. It is now the **core brief**, and the rest is split into modules you paste only when relevant. **A module never replaces this file** — paste core first, always.

| Conversation is about | Also paste |
|---|---|
| Onboarding, activation, retention, positioning, general product | *nothing — core is enough* |
| Quiz modes, exam simulation, practice mechanics | `QUIZ_AND_PRACTICE_CONTEXT.md` |
| A specific screen or feature surface, Note Collections, the Companion | `SURFACES_AND_FEATURES_CONTEXT.md` |
| What to build next, sequencing, why past releases went as they did | `STRATEGY_AND_ROADMAP_CONTEXT.md` |
| Pricing, plan tiers, paywalls, checkout | `MONETIZATION_CONTEXT.md` |
| A question that may already be settled — **read before reopening one** | `DECISION_HISTORY_CONTEXT.md` |
| Library/notes/collection structure, note fields, taxonomy | `NOTES_AND_COLLECTIONS_CONTEXT.md` |

---

## App: NoteLib

**What it is:** NoteLib is a notes-first study workspace. Users capture notes, generate Study Packs, practice with quizzes/exams, track readiness, and keep a reusable library of learning material.

**Core loop:** Capture -> Generate -> Review -> Improve -> Make a Copy -> Repeat

**Positioning:** **Ratified 2026-08-01 as a product-wide Messaging Architecture** — *"We sell the learning system. Features simply support that promise."* Ratified hero: **"Always know what to learn next."** / *"NoteLib turns your notes into a complete learning system — organized, prioritized, and ready whenever you sit down to study."* The hierarchy is **locked**: Hero (universal emotional outcome, profile-agnostic) → Supporting paragraph → Profile-specific bullets (via the existing `ProfileType`-keyed copy-resolution pattern, not a new mechanism) → Features as *evidence* for the promise. Board Exam Mode, Companion, Adaptive Practice, Review Sets, Progress, Knowledge Impact, and Explore all live at the Features layer — **none of them becomes the hero anywhere.**

Two things a new session usually gets wrong here:

- **The old "external copy hasn't caught up / needs a conversion test first" framing is dead.** `v0.68.0` shipped the ratified hero and supporting paragraph on `/pricing` word for word, plus the Plus/Pro taglines. And the "no positioning-copy change without a conversion test" bar was **explicitly lifted by the owner on 2026-08-01** — narrative consistency with the product vision is treated as a design decision, measured post-launch, not an optimization experiment. Pricing/checkout *mechanics* changes still need evidence; positioning copy does not.
- **Rollout is deliberately incremental, and the second slice is IN FLIGHT.** `v0.68.0` shipped `/pricing`'s hero, supporting paragraph and the Plus/Pro taglines. **`v0.76.0` (open now) is scoped to the *money surfaces*:** the `FREE` tagline — the one item the roadmap names as explicitly owed — the `PLAN_COMPARISON_ROWS` "Best for" row, the per-plan descriptions, and the in-app upgrade prompts. **The landing page and the Exam Hub upsell are deliberately NOT in it** — they answer different questions (*what is this?* / *why this mode?*) and each still needs its own scoping pass. **A trap worth knowing if you are asked about the `FREE` tagline:** an outcome-framed candidate was written in `v0.68.0` and **reverted**, because it had been derived from consistency with the Plus/Pro taglines, which contradicts the ratified `FREE = adopt` tier placement. If a proposed Free tagline only reads well beside its siblings, it is wrong again. `PROFESSIONAL`'s profile bullets are flagged **aspirational — do not wire live** until that profile has real capability behind it (it is enum-only today).

**Rebrand note:** The product is NoteLib. Code, package names, and database/schema names still use `studysnap` in many places unless explicitly changed.

**Current baseline: `v0.76.1 - Adaptive Practice Entry Attribution`, Released 2026-08-14.** Instrumentation only, no user-visible change: `ADAPTIVE_PRACTICE_STARTED` now records **where** a session was launched from (seven values plus `direct`), so the open `[CHECKPOINT — due 2026-09-12]` can test whether learners actually discover remediation from the Dashboard. **⚠️ Standing constraint: do NOT propose removing the Challenge Quiz Adaptive Practice entry point before 2026-09-12** — `v0.74.0` already removed the Quick Review route, and a second removal inside the same window confounds that read.

**Previous: `v0.76.0 - Messaging Architecture: The Money Surfaces`, Released 2026-08-14.** Copy only. `FREE.title` is now *"Start with ready-made study material"* — the Messaging Architecture item that had been explicitly owed since `v0.68.0` is closed. **Three copy rules were established and are binding:** upgrade *button* labels stay feature-named (a button says what the click does); paywall headlines are narrative for capability paywalls and factual for quota ones; `PLAN_CARD_SUBTEXT` describes the tier, never a feature, because it renders on every paywall.

**Previous: `v0.75.0 - Authoring by Inference`, Released and deployed 2026-08-14.** Curator authoring now infers a note's depth instead of asking for it: Review Sets carry an authored depth that child plans inherit, bulk-generate can author straight into a Review Set (pre-filling depth from it and adding the finished notes to it), and the depth control is renamed **Authored Depth**. **Two things worth carrying:** (a) the `ADR-001` section gating this work had been **stale for four releases** — it named two prerequisites that were both already satisfied, so unblocked work looked blocked; an ADR outranks a feature doc, so a stale gate inside one is expensive. (b) Its pre-signoff pressure test found **ten** issues, the blocker being that the depth pre-fill fired for *every* profile while the control renders only for curators — so learners silently persisted an invisible, unclearable value that acts as a curriculum floor. **Four findings were in code the authoring session had written, and two were protected by tests that session wrote or edited**, which is the second consecutive release where cold-context review was the deciding factor.

**Previous: `v0.74.0 - Quiz Progression`, Released and deployed 2026-08-13.** Closed a scored assessment whose answer key sat on an adjacent tab — see orientation line 10(a) for the rule and its reasoning. It also fixed quiz content rendering raw LaTeX (`\frac{...}` printed literally) by adding a math-notation rule to all ten content-generating prompts and normalising stored content at display time; **~23 affected Study Packs still await hand regeneration**, which is indexed in the Backlog Index rather than lost.

**Previous: `v0.73.0 - Onboarding Redesign`, Released and deployed 2026-08-12.** It went at the funnel's largest leak (line 5) and was justified on **comprehension, not retention** — the activation-volume argument was tested and failed. Eight of ten planned items shipped; one (removing the exam-date question) was never built and was then **decided against on review** — see the exam-date decision in the orientation block. It carries a `[CHECKPOINT — due 2026-09-11]`. **Its pre-signoff pressure test found the funnel it shipped to measure itself was miscounting the branch it introduced** — a screen that is a *branch* of Screen 5 had been ordered as a step after the final screen, producing a negative drop-off; fixed before signoff.

**Previous: `v0.71.0 - Applicable Programs`, Released 2026-08-10, merged and deployed.** Release B of `ADR-001` — making applicability a many-to-many fact so one canonical note surfaces under every program that needs it. Cut along the irreversibility boundary rather than by layer, because Release B is not reversible once reads move to the join. **Scoped as three slices and finished as five** — slice 4 (removing the "Primary Course / Program" second field) and slice 5 (the Onboarding Intent Router) were both added mid-release. All five shipped.

- **Slice 1 — merged (PR #990).** `note_course_program` join table, `V107` one-row-per-note backfill from the legacy string, and Teacher/Admin + Admin Dashboard write surfaces. Nothing read the join.
- **Slice 2 — merged (PR #991).** Discovery reads move to the join. **Its original safety premise turned out to be false and the semantics changed as a result:** the slice was scoped on "at one join row per note the join returns identical facet counts," but `V107` deliberately creates **no** join row for a catalog-*excluded* program value, so a pure-join rewrite would have changed *result sets* — an excluded value's facet vanishing, its filter returning 0 instead of N, its public shareable slug URL ceasing to resolve. Owner ruling 2026-08-05: reads are **join-first with a legacy-string fallback** (`EXISTS(join rows) OR (no join rows AND legacy string matches)`). Equivalence was then verified on real data: 0 differing facet rows, 0 differing note IDs across four filters. **Accepted cost: `notes.course_program` stays load-bearing on read paths.** Retiring the fallback is unscheduled.
- **Slice 3 — shipped.** Program Family expansion: selecting the `Engineering` family adds all three members as explicit selections, which the author can trim. Gate cleared 2026-08-05 by narrowing the question, not by answering it — expansion is unconditional, so no subject→program mapping was needed.
- **Slice 4 — shipped (added mid-release).** "Primary Course / Program" removed. One `Course / Program(s)` picker across Note Editor, Note Detail, Create-from-topic and Bulk Generate. **This is why there is no primary program any more.**
- **Slice 5 — shipped (added mid-release, after the pre-signoff pressure test).** The Onboarding Intent Router, plus repair of an activation-blocking regression the release itself introduced: onboarding creates its first note without sending a program, and a new `throw` on that path made onboarding uncompletable for every new user on the create-note branch.

**Deployed.** `V107` and `V108` have run in production. `V108` deletes join rows mechanically derived onto learner-owned non-copy notes — **a learner's personal free-text program must never be materialized into a catalog Applicable Program row**, which is now ratified doctrine in `ADR-001` under *Representation authority*.

**Superseded baseline:** `v0.70.0 - Canonical Knowledge Completion` released 2026-08-04 — the `course_programs` catalog (21 seeded programs, 11 excluded) + `program_families`, pool/bank learner-level re-keying, and authoring metadata made correctable after generation. **R4 resolved 2026-08-04, passed on all three steps**; bulk authoring is unblocked. Earlier baseline: `v0.69.0 - Canonical Knowledge Foundation`, deployed 2026-08-04 (PR #981). **What shipped:** Release A of `ADR-001` — the repo's first Architecture Decision Record — splitting the overloaded `notes.course_program` field into separate axes. `notes.domain_context` (a curated closed 8-value enum) is now the *sole* domain constraint sent to the LLM, and note-level `notes.learner_level` carries authored depth. Why it existed: authoring of the Civil Engineering Review Set's Engineering Mathematics plan had been deliberately halted, because one Algebra note under a one-program-per-note model would have to be duplicated for eleven engineering programs. **Two Planned Scope items were deliberately deferred to `v0.70.0`** — the `course_programs` catalog + `program_families`, and the question pool/bank re-keying — both blocked on production reads the release branch could not perform, not on engineering time. **The R4 verification did not run before signoff and is now a `[CHECKPOINT — due 2026-08-18]`**: it needs the new columns live in production, production runs `main`, and every release commit sat on the release branch — so signoff *is* what deployed it. **Bulk authoring must not begin until R4 step 2 passes.** A full pre-signoff pressure test (two agents plus an independent review) found five defects, two of them blockers that per-PR review was structurally unable to see — including silent permanent wiping of both new fields whenever a note's details were edited from the note detail page. Superseded baseline: `v0.68.0 - Topic Note Rename` shipped 2026-08-01 (PR #966). **What shipped:** the bare-topic drafting action renamed **"Generate Note" → "Create a Note"** (Company Redefinition Phase 4 §4 item 6), reserving "Generate" for operations that transform the learner's *own* material — "Generate Study Pack", "Generate Quiz", and "Regenerate" are deliberately unchanged, and that distinction is the whole point of the rename. Five further ungated items were folded in after kickoff: a "Retry Generate" → "Retry Generation" grammar fix (§4 item 8), the Companion Guidance Doctrine's docs-only text, the Messaging Architecture's first slice on `/pricing`, and — on explicit owner review after the first three shipped — two consistency batches the rename's own narrow scope had left behind (`plans.ts`'s pricing-table and upgrade-highlight strings, and the topic-note quota's entire user-facing vocabulary moving to **"topic note(s)"**).

**Two process facts from `v0.68.0` worth carrying forward, because they recurred:**

1. **Scoping under-counted itself four separate times in one release.** The rename's own "swept the whole repo" claim missed five surfaces inside the renamed action's *own* flow; the quota batch's sweep was truncated and missed the actual paywall copy (including a term the batch had explicitly rejected on evidence); a layout fix addressed the pill and title size but not the description-length drift that was the real cause; and `RELEASES.md` contained four wrong counts. All were caught by a **five-agent pre-signoff pressure test**, not by per-PR audits — because none of them appeared in any single PR's diff. If you are proposing a repo-wide copy or terminology change, assume one grep is not enough.
2. **A backend string can be user-facing copy.** The release was scoped "frontend-only" and still shipped a bug: `BulkNoteGenerationQuotaExceededException`'s 422 message rendered the retired vocabulary into the *same* `role="alert"` element as its renamed frontend twin. Fixed as an explicit scope amendment. Backend exception messages propagate verbatim to the UI through `api.ts` — treat them as copy, not internals.

Twelve pre-existing findings are recorded as **Known Limitations tagged `v0.68.1` candidates** in `RELEASES.md`. Note this project's own history here: `v0.67.0`'s candidate tagging was itself incomplete, and `v0.67.1` ended up shipping seven items after scoping three — so review that list directly rather than trusting the tags.

**Immediately prior releases:** `v0.67.1 - Explore Convergence Follow-ups` (2026-07-31) and `v0.67.0 - Explore Convergence` (2026-07-30, PR #949) — the latter notable for claiming its slot after **8 reclaims** by unrelated gate-cleared work, and for shipping on an **explicit owner gate override** with a dated `[CHECKPOINT — due 2026-09-13]` attached so the override gets checked against real engagement data. Full detail for both in the release list below.

---

## Retention Is the Proven Constraint (read this before proposing anything)

**⚠️ THE NUMBER WAS RESIZED 2026-08-11 — read this before quoting any retention figure.**

**What we said for months:** W1→W2 retention is **2.4%** (production read 2026-07-15; 3 of 127 eligible activated users returned in week 2), the core strategic constraint since v0.32.2 first flagged it (5.6% then), **not meaningfully improved** despite three feature releases aimed at it (v0.44.0, v0.46.0, v0.48.0).

**What `v0.72.1` found.** That metric asked one narrow question: did the learner do anything in **days 7–14** after their first Study Pack? A learner returning on day 3, or on day 20, scored as churned. Measured across four windows on the same population (152 learners, first pack ≥30 days ago):

| window | returned | rate |
|---|---|---|
| strict, days 7–14 *(the figure we quoted)* | 3 | **1.97%** |
| unbounded after day 7 | 7 | 4.61% |
| days 2–30 | 10 | 6.58% |
| unbounded after day 1 | 11 | **7.24%** |

Of the **11** learners who ever came back, the old metric saw **3 — 27%**. Four returned in days 2–7 before the window opened; four only after it closed.

**How to talk about this — both halves, always.** The number we steered by understated reality by roughly **3.7×**, *and* **141 of 152 activated learners never came back at all**. The constraint is **mis-sized, not imagined**. Treating the correction as good news is the failure mode to avoid; so is continuing to quote 2.4%. The small sample makes the exact multiplier uncertain (3.7× could be 2.5× or 5×) but **not** the finding — excluding days 2–7 and day 15+ is definitional arithmetic, not sampling noise. The admin dashboard now reports all four windows side by side, with the strict figure preserved unchanged so historical comparisons still work. Free-tier quota was essentially never hit at the old limits (5/25/50/month), which is one reason the owner ratified a large quota increase in v0.61.0 rather than treating quota as the retention lever on its own — see the Current Release note above.

**Diagnosis (two independent Fable sessions converged):** every content-rich retention trigger the product has shipped **default-OFF**, gated behind the exact engagement it's meant to create. The first study session also ends in a psychologically "complete" feeling (Zeigarnik effect) rather than an open loop that pulls the learner back. `v0.48.0` (merged 2026-07-15) shipped the cheap fixes for both (open-loop first-quiz ending, due-concepts digest default-ON) — **both remain UNPROVEN, mechanism shipped, lift not measured.** Do not describe either as a retention win in external-facing copy.

**H1+H5 SHIPPED in `v0.72.0` (2026-08-11) — this section previously said it was "still gated" and the query "has not yet been run."** Both are now false. What happened, recorded because the reasoning matters more than the outcome: the re-read ran on 2026-08-11, 13 days after its window closed (a kickoff gate scan caught that it had gone unrun). Result: **0 of 31** in the gated cohort vs. a **1.95%** pre-`v0.48.0` baseline. **That 0% was underpowered, not negative** — expected returns at baseline for n=31 is 0.60, and P(zero | no change) is **54.3%**. Ambiguous, so the pre-committed rule (*"positive **or ambiguous** → ship"*) fired and H1+H5 shipped. **Do not describe H1+H5 as validated by data** — it was a decision on a rule written before the answer was known, precisely so the outcome could not be rationalised afterwards.

**What shipped:** after any learner's first completed session, a prompt asks for their exam date and which weekdays they want to review; the due-concepts digest then respects those weekdays and links straight into a Quick Review session for the note with the most concepts due, rather than dropping them on the dashboard. Two dated checkpoints are open: **2026-09-10** (do learners commit when asked, and act on the digest — this one carries the kill criterion) and **2026-11-09** (the retention read itself, re-specified to the wider window after the finding above, since the original spec would have judged H1+H5 through the broken metric).

**The 2026-07-24 signup surge reversed the "go straight to Phase 2" plan and inserted a Diagnostic Read + a new Reusable Practice Assets initiative ahead of it — full detail in the Company Redefinition section of `STRATEGY_AND_ROADMAP_CONTEXT.md`, which supersedes the old post-v0.48.0 sequencing.** The retained/churned exam-dated user interview script is still written, ready, zero engineering cost, and still hasn't been run — the one open item on this whole track that can't happen from a keyboard.

**The target habit was redefined 2026-07-28 — read Round 2 through this, not the raw blended 2.4%.** The single W1→W2 calendar-week boolean is retired as the universal yardstick. Segment by whether `UserEntity.examDate` is set (not `profile_type`, a coarser proxy for the same thing):

- **Exam-bound learners** — the majority; `BOARD_EXAM` alone is **70.94%** of profile-typed accounts, confirmed product-wide rather than a surge artifact. Their arc is naturally episodic: signup → sustained practice → sit the exam → legitimately stop. Scored **only once their exam date has passed**, on whether they had activity in the final 7 pre-exam days. Still-in-flight users are excluded from the denominator entirely rather than penalized on a calendar clock that doesn't match their arc — going quiet *after* the exam is not churn.
- **Open-ended learners** — `STUDENT`, no exam date, ~27%+. Keep the existing W1→W2-style frame, which fits them better.

Two guardrails on reading this:

- **It does not excuse the 0/41 exam-dated-retention finding** (users going quiet *below* their own exam date). That is disengagement before the goal — a real problem under either frame.
- **Expect a small scored group at first** (recent signups, PRC-clustered exam dates). A near-single-digit denominator means "not yet measurable," not a verdict.

Full definition: `docs/product/ROADMAP.md`'s "Target-habit definition" Backlog Index row.

**Full backlog, current status, and exactly what un-parks each item lives in `docs/product/ROADMAP.md`'s Backlog Index table (~55 rows) — check it before proposing or resurfacing anything.** See "Roadmap Candidates: Gated & Ungated" below for a synthesized, status-grouped view of that same table, restricted to items still actually open (shipped/resolved rows are dropped from that view — check `RELEASES.md` for those). Do not propose roadmap items from partial memory of past sessions; the index is the current source of truth.

---

---

## The activation funnel — new as of 2026-08-11, and it relocates the biggest problem

All-time, production:

| stage | users | % of signups | lost here |
|---|---|---|---|
| signed up | 375 | — | — |
| verified email | 366 | 97.6% | 9 |
| **completed onboarding** | **234** | **62.4%** | **132** |
| generated a first Study Pack | 195 | 52.0% | 39 |

**132 learners — 35.2% of everyone who ever signed up — verify their email and then never finish onboarding.** That is the largest single drop anywhere in the funnel, and it was not visible before this read.

**Two things this settles, and one it does not.**

**Settled: "get more users activated" is not a lever on returning-user counts.** Returning learners = activated × retention rate, so both levers are multiplicative and the comparison is rate-independent — the retention rate cancels out. Perfect activation (every signup activating) would beat merely doubling retention only if activation were **below 50%**. It is **52.2%**. Scenarios, in returning learners per month: baseline **1.02**, retention doubles **2.03**, *every* signup activates **1.95**, activation recovered at half the retention rate **1.48**. A pre-committed rule required the sensitivity case to clear the retention case; it did not. **Do not propose onboarding or activation work on the grounds that it will move returning-user counts.** It is defensible on comprehension or product-quality grounds — 132 people meet the product and leave — but that is a different argument and should be made as one.

**Settled: verification is not the problem.** 97.6% verify. Any proposal built on "users bounce off the verification wall" is aiming at 9 people.

**NOT settled: which onboarding step they abandon on.** Profile type is only persisted at the *final* step, so the database cannot distinguish a step-1 abandon from a step-4 one, and clean step-level analytics only exist from 2026-07-28 (n≈5 so far). If a recommendation depends on knowing where the drop is, say so — that is an instrumentation question, not an unknowable one.

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
- **Note metadata is five separate axes as of `v0.69.0` (`ADR-001`), not one field** (the orientation block lists all five; this bullet details the four that `ADR-001` introduced or redefined, with Target Audience covered under Profile Types). *Subject* = what it is about. **Domain Context** = how it is authored, and the **only** thing that reaches the LLM as a domain constraint (curated closed 8-value set). **Note Learner Level** = how deep it is authored. *Applicable Programs* = where it appears — discovery only, **never reaches a prompt**. **Built as of `v0.71.0` Slices 1–2**: stored as explicit `note_course_program` rows, curated by Teacher/Admin, and read by Library/Public Library filters, facets, badges, and public search — join-first, falling back to the legacy string only for a note with no join rows. Facet counts can now correctly sum above the note total, which is expected under many-to-many and is explained in the filter panels rather than hidden. `courseProgram` survives as a legacy label and a fallback, and is no longer the classification apex. Resolution lives only in `StudyPackGenerationContextResolver`: domain = note domain context → note program → profile program; depth = note level → reader level → `College`. **Static content never falls back to the reader's level** (a Grade School reader cannot dilute a College note); **quizzes treat the note's level as a floor** — a lower reader level may soften wording and add scaffolding but never lowers curriculum, and a higher reader level never raises difficulty. Both new fields are **Teacher/Admin-only** and NULL on nearly every note; NULL is the designed norm, and `domain_context IS NULL` is itself the marker for "not yet promoted." **As of `v0.75.0`** the depth field is labelled **"Authored Depth"** in the UI (copy-only; the column is still `notes.learner_level`), and a curator creating a note gets it **pre-filled** — from the selected Review Set's own or inherited depth, else from the author's profile level. **That pre-fill is a UI default only, never a server-side write, and never applies to learners or to an existing note**, because a depth change on a generated note strands its Challenge-bank rows at the old level. **Note Collections also carry an optional `learnerLevel`** now, inherited down one level from a Goal to its child plans, used solely as that pre-fill source.
- **Study Pack** is generated content attached to a Note: summary, key concepts, quiz, metadata suggestions, and downstream quiz/exam entry points.
- **Note Collections (Study Plans / Review Sets)** organize owned notes into an ordered, curated unit, with an optional one-level Goal -> Subject hierarchy. This is the product's primary retention lever — see the dedicated vision section in `SURFACES_AND_FEATURES_CONTEXT.md`.
- **Learning Companion** is a persisted, curator-authored guidance layer (JSONB) on top-level Review Sets — the "premium guided learning experience" layer riding on top of the Study Plan journey. See the dedicated vision section in `SURFACES_AND_FEATURES_CONTEXT.md`.
- **ConceptHealth** is the recency spine for readiness and Progress: `lastCorrectAt`, `lastIncorrectAt`, due/not-due classification, and struggling state. This is the *only* mastery-integrity signal in the app, and it's locked (since v0.37.0) to move only from genuine assessment — see the Quiz / Practice Mode Contract in `QUIZ_AND_PRACTICE_CONTEXT.md`.
- **Quiz sessions** share `quick_review_sessions` with mode stored as enum and session state in JSONB. Question **format** (MCQ, True/False, Multi-Select, Matching, Identification, Enumeration) is a separate axis from mode.
- **Flashcards and Memorization** are non-scored review surfaces that sit entirely outside the quiz-session engine — no session row, no `ConceptHealth` write, ever.
- **Generated teacher quizzes** use `generatedQuiz`, not student quiz sessions.

Versioning rule:

- Never auto-regenerate generated content.
- Regeneration is explicit, user-confirmed, owner-only, and updates the existing Study Pack in place.
- Owner self-copy copies authored note fields only.
- Public-note copy is the exception: when the public source has a Study Pack, the copy includes that Study Pack and arrives `STUDY_PACK_READY`.

---

## Profile Types

| Profile | Current focus | Important rules |
|---|---|---|
| Student | Notes, Study Packs, Quick Review, Challenge Quiz, Adaptive Practice, Long Exam | Target Audience hidden; backend saves `STUDENT`. |
| Board Exam / Exam Reviewer | Exam-date context, Board Exam Mode, readiness for licensure-style prep | Target Audience hidden; backend saves `BOARD_TAKER`. |
| Teacher | Quiz generation, preview, DOCX export, Exam Builder | Teacher flow uses `generatedQuiz` only; never reuse student quiz sessions for preview. No adoption surface — Flashcards/Memorization and the Dashboard adoption nudge are hidden for Teacher. |
| Professional | Certification review, Long Exam as Full Practice Exam, Interview Practice | Target Audience hidden; backend saves `PROFESSIONAL`. |
| Parent | Enum exists, no real product implementation | Do not propose implementation without parent-child relationship design. |

**Onboarding was REDESIGNED in `v0.73.0` (2026-08-12) — the "this flow is locked, do not redesign it" instruction that stood here for months is retired.** It is now **eight screens, one *required* question each**: profile type → course/program → learner level → first intent → input method → the note → generating → done. Screen 5 branches: the ready-made path shows an Official Study Plan, an availability check, or a "no plan yet" fallback where a learner can **ask for a plan that does not exist** (recorded as demand, no email is sent). Exam takers also see an **optional** exam-date field on Screen 3 — kept deliberately — see the exam-date decision in the orientation block. Tap-to-advance applies only to closed-set choices that stay inside onboarding (Screens 4 and 5's input method); typed input and the learner-level `<select>` keep an explicit Continue, because re-choosing an already-selected `<select>` value fires no change event and once stranded learners entirely. Backend content-creating mutations must still enforce profile setup for the legacy completed-but-null cohort through `ProfileSetupRequiredException`. **Onboarding still runs AFTER email verification, not straight after signup — this did not change, and the reason is a hard constraint rather than a preference.** Running it pre-verification was proposed and put out of scope: the generating screen calls `/notes/generate` and `/notes/{id}/generate`, both of which require a verified email, so it would **403 at the exact moment onboarding promises the first Study Pack** — and it would expose paid LLM generation to unverified throwaway accounts. The funnel also says it is the wrong target: **verification loses 9 learners, onboarding loses 132.** The live counter-proposal, unbuilt, is to run Screens 1–4 pre-verification and move the verification ask to the generating step — from the door to the payoff — which needs no backend change.

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
- Do not propose inferring **Domain Context** from anything. No source is authorized, and `domain_context IS NULL` is the promotion-backlog marker a default would destroy. Depth may be inferred; the domain axis may not.
- Do not propose pre-filling authoring metadata into a control the author cannot see, or onto a note that already has a Study Pack.
- Do not propose unlocking the Study Pack Quiz tab on anything less than a perfect Quick Review, or hiding it instead of locking it — both were decided in `v0.74.0`.
- Do not plan-gate question *formats* — format variety is a learning-quality dimension, not a monetization lever. Gate modes/workflows/quotas instead.
- Do not nest Note Collections beyond two levels; no per-module mastery.
- Do not treat `/onboarding` as locked — it was **redesigned in `v0.73.0`** and the old "locked, do not redesign" rule is retired. What *is* fixed: it runs **after** email verification (a hard constraint, not a preference — see Profile Types), and it is being measured until 2026-09-11, so changing it before that read lands destroys the read.
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

## Codex prompt format

This file used to inline the Long/Short Codex prompt templates. It no longer does — **use `docs/skills/codex-prompt-generator.md`**, which is the source of truth, or the `/codex-prompt` skill.

The inlined copy had drifted: it was missing the `## ERROR STATES` section the real template carries. That section is the first thing the post-delivery audit checks, so a prompt drafted from the stale copy would systematically omit it. Do not re-inline the template here — a second copy is exactly how that drift happened.

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
