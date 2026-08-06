# Onboarding — activation repair and the Intent Router

## Status: OWNER-RATIFIED 2026-08-06. Scoped into `v0.71.0`. No code has changed yet — implementation has not started.

**Read this section first if you are picking this up in a new session.** The owner made binding decisions in two
passes on 2026-08-06 that override parts of the original analysis below. Where the prose and these decisions
disagree, the decisions win, and the affected sections are annotated inline.

**If you only read two things: this Status block, and §13** — §13 is the authoritative user-facing specification.
Sections 1–12 are the analysis that produced it and are preserved for their reasoning, not as instructions.

### Guiding philosophy (owner, 2026-08-06 — carry this into every design call)

> **Onboarding is no longer just collecting profile information. It is an intelligent router that helps every
> learner reach the fastest successful study experience available to them.**

NoteLib has shifted from a notes-first AI workflow to a learning system with multiple valid entry points. The
first impression must reflect that immediately. This is the reason the redesign ships now rather than later.

### The five ratified decisions

| # | Decision | Effect on this plan |
|---|---|---|
| **1** | **Keep learner-owned Applicable Program rows.** Do not clear join rows on learner updates. Validate against the **stored** rows. Course / Program(s) is one of the four architectural axes and means the same thing regardless of ownership — ownership must not change the semantics of the metadata model. | Resolves pressure-test **C1** (validate against `findIdsByNoteId(noteId).size()`, not the request). **Leaves B2 open — see "Open decision A" below.** |
| **2** | **Redesign onboarding inside `v0.71.0`**, not as a later phased release. Onboarding is telling the wrong product story; shipping a knowingly outdated model is worse than the added scope, provided existing building blocks and routing logic are reused safely. | **Supersedes §7's three-release recommendation.** See §7-REVISED. |
| **3** | **Set expectations before selection, not after.** Adopt the principle behind option C, but adjust the intent card's supporting copy *dynamically, before the user taps* — never surprise them after choosing. | **Supersedes §1.3 option C.** See §1.3-REVISED. |
| **4** | **Audit production before designing the resolution layer.** Do not assume. Determine whether Official Review Sets use catalog names, exam slugs, or a mixture. Solve the real data; do not introduce normalization we may not need. If production is mixed, document it explicitly as a matching issue and design around actual data. | **DONE 2026-08-06 — GATE CLEARED.** All four published plans use **exact catalog names**; no slugs, no case drift, no mixture. **No resolution layer is needed** — building one would be the unnecessary normalization this decision warned against. Results and their consequences: `docs/claude-plans/onboarding-review-set-vocabulary-audit-results.md`. |
| **5** | Propagate all decisions into `ROADMAP.md`, `RELEASES.md`, and onboarding documentation before implementation. Remove tracked `.DS_Store` files and gitignore them. | Done — see §11 and the changelog at the foot of this file. |
| **6–11** | **Second ruling pass, 2026-08-06** — final UX direction for the intent step, the unsupported-program experience, the Dashboard question, teacher copy, completion behaviour, and the analytics set. | **See §13, which is the authoritative UX specification.** It supersedes §1.3-REVISED and §6 where they differ. |

### Open decision A — B2 is not resolved by decision 1

Decision 1 fixes C1 but **not B2** (a learner's Course / Program edit is permanently inert because a stale
V107-backfilled join row wins every read). Keeping rows and not clearing them leaves that untouched.

There are **two distinct populations** of learner-owned join rows, and decision 1 clearly protects only one:

1. **Inherited from copying a curated note** — authored by a curator, genuinely valuable. Keep, unambiguously.
2. **Auto-created by V107's unfiltered backfill** — derived mechanically from the learner's own string at
   migration time. Nobody authored these. These are what cause B2.

### Owner reframing, 2026-08-06 — this supersedes my recommendation

I originally recommended giving learners visibility and removal on their own notes' Applicable Programs. **The
owner rejected that, and the reasoning is better than mine:**

> The core problem is that the migration created metadata that no learner intentionally authored. **That is a
> migration problem before it is a UX problem.** We should not expose Applicable Programs to learners merely to
> repair migration artifacts — that would leak the curator publishing model into personal note authoring, which
> is contrary to the architecture we just established. The important discriminator is **provenance rather than
> ownership**. Curated rows, inherited rows and migration-generated rows are fundamentally different kinds of
> data; migration-generated rows are **derived data, not user-authored data**. That distinction is more faithful
> to ADR-001 than introducing Applicable Program editing into the learner experience.
>
> **Keep the learner experience simple. Move complexity into curation.**

Why this is right and my version was not: my fix treated a data-provenance defect as a permissions gap, and
would have paid for it with permanent UI complexity in the one surface ADR-001 deliberately kept simple. It also
quietly conceded that learners need to understand Applicable Programs — a concept the two-mode model exists to
keep away from them.

### The decision gate — SIZE IT FIRST

**No option may be chosen before the affected population is measured.** Queries:
**`docs/claude-plans/b2-migration-artifact-sizing.sql`** (run against production). Query 1 is the gate; it splits
learner-owned join rows by provenance, using "is the note a copy?" as the proxy, and Query 3 cross-checks that
proxy against `created_at` clustering.

Thresholds set by the owner **in advance of seeing the data**, which is the right way round:

| Result | Direction |
|---|---|
| `already_diverged = 0` | Prevent future divergence and document the migration behaviour. **No UI.** |
| A handful diverged | **Corrective migration**, not permanent UI complexity. |
| Meaningfully large | Provenance becomes the real architectural distinction — **Option 5 (a `source` column on `note_course_program`) deserves serious consideration.** |

**Local dev baseline** (run 2026-08-06, not representative — the local corpus is admin-heavy): 8 learner notes
carry join rows, **all 8 inherited from copies, 0 backfill-derived, 0 diverged**. Production has 364 accounts
with 179 learners concentrated on the four catalog programs the backfill matched, so the backfill-derived count
there should be materially higher. Do not generalise from the local read.

Note what the local shape hints at, if it holds: if most learner-owned join rows turn out to be
**copy-inherited** rather than backfill-derived, then decision 1 ("keep them") and B2's fix stop being in tension
at all — the rows worth keeping and the rows causing the bug would be almost disjoint populations.

**Status: gated on the sizing query. Do not implement any B2 fix until it has been run.** The B0 repair and
C1's validation fix are both independent of this and can proceed.

---

Written 2026-08-06 in response to the `v0.71.0` pre-signoff pressure test
(`docs/claude-findings/v0.71.0-pre-signoff-pressure-test.md`), which found onboarding to be the flow most
severely affected by the Applicable Programs architecture change — including **B0, an activation-blocking
regression introduced by this release**.

Every claim below was verified against the real code. Where something is genuinely unknowable from the repo
(production Official Review Set volume, production collection tagging), it is marked as an open question with a
query attached rather than estimated.

---

# 1. Critique of the proposed model

The proposal is directionally right and its Branch A/A2 structure is better than what ships today. Six
substantive objections, in order of how much they change the design.

## 1.1 Branch A is **not buildable as specified** — program matching is exact, case-sensitive string equality

> **WITHDRAWN for current data, 2026-08-06, by the production audit
> (`onboarding-review-set-vocabulary-audit-results.md`).** All four published plans are tagged with **exact
> catalog names**. Matching works as-is; no resolution layer is needed and none should be built. The
> four-vocabularies analysis below remains accurate as a **drift risk**, not a present defect — production is
> clean, but nothing keeps it clean, because the admin publish picker draws its options from values on public
> *notes* rather than from the catalog. The fix for that is a guard (point the picker at the catalog), not a
> normalizer. See the results doc, "What to build instead of a resolution layer."
>
> Retained below because it is the reasoning that motivated the audit, and because the drift risk it identifies
> is real and still unaddressed.

This is the objection that reshapes the plan. The proposal assumes the router can ask "does a Review Set exist
for this user's program?" That question cannot currently be answered reliably.

- `note_collections.course_program` is a free `VARCHAR(120)` with **no catalog FK** (`V76:3`). V106 added
  `course_program_id` to `notes` and `users` **only**.
- Matching is a Spring Data derived query with **no `IgnoreCase`** (`NoteCollectionRepository.java:79`), so it
  compiles to `WHERE course_program = ?` — exact, case-sensitive.
- Normalisation forgives dash/whitespace shape and nothing else (`SubjectNormalizationUtils.java:10-19`). A
  `normalizeForLookup` with `.toLowerCase()` exists but **is not used on this path**.
- `docs/features/exam-hub.md:60` confirms this is deliberate: *"no subject-level, partial, or fuzzy matching."*

**So `"Education"` cannot match a set tagged `"LET"`, and `"nursing"` cannot match `"Nursing"`.**

And there are **four disagreeing vocabularies**, none authoritative:

| Layer | Vocabulary | Evidence |
|---|---|---|
| Storage | free `VARCHAR(120)`, no FK | `V76:3` |
| API writes | unvalidated, any string | `NoteCollectionService.java:580-581` |
| Admin publish UI | `listCoursePrograms("public")` — **derived from values already on public notes**, not the catalog | `collection-detail-page-client.tsx:2382-2391`; `NoteService.java:1156-1162` |
| Onboarding UI | free text + 31 hardcoded suggestions | `onboarding/page.tsx:1146-1155` |

The admin's "locked" publish list is itself derived from user-typed free text. If any public note carries
`"LET"`, `"LET"` becomes a publishable tag.

**Consequence for the plan:** a resolution layer is a **prerequisite**, not a detail. Without it the router
silently mis-routes — it will tell a Nursing learner "we're still building this" while PNLE sits published under
a differently-cased or slug-shaped tag. That is strictly worse than today's silent fallback, because it makes a
confident false claim.

**This is also the sequencing argument** — see §7.

## 1.2 The A2 "we're still building this" branch is the *majority* case — and that argues **for** it

> **CORRECTED 2026-08-06 by the production audit. This section's premise was wrong.** It argued from *program
> count* (4 of 21 covered) that Coming-soon would be the majority experience. Weighted by **actual users** it is
> the opposite: the four covered programs — Education 58, Accountancy 50, Nursing 40, Architecture 31 — hold
> **179 of 218 program-holding accounts (82.1%)**. Coverage is concentrated in exactly the programs people
> actually study. Coming-soon serves ~18% (9 users on catalog programs with no plan yet, 30 on off-catalog
> values).
>
> The conclusion below still holds and is if anything strengthened: an honest availability state is worth
> building. But it serves a **minority** path, so it should not dominate the design or the copy budget, and
> Branch A deserves to be the visually primary door for most users.

Only 4 of 21 catalog rows carry an `exam_goal_slug`, and the ROADMAP's own production read names exactly four
comprehensive Official Review Sets (CPALE 74, PNLE 63, ALE 52, LET 43). Producing more is bottlenecked on the
still-unscoped Curator pipeline.

I initially read this as an argument against the intent step. It is not. Today, a STUDENT who wants existing
material is silently dumped into note authoring with no acknowledgement. A2's honest screen is strictly better.
**Thin availability is the reason to build A2, not a reason to skip the redesign.**

What it *does* argue against is presenting two visually co-equal doors when one is locked for 17 of 21 programs.

## 1.3 A named tradeoff: intent-first vs availability-labelled

The proposal states three times that the user must choose the fallback and that unmet intent must be
acknowledged. I take that as a firm principle, so I am **not** recommending availability-gating the question.
But the binary isn't the only option:

| Option | Behaviour | Cost |
|---|---|---|
| **A. Intent-first (as proposed)** | Always ask; A2 acknowledges + offers chosen fallbacks | Honest, but ~80% of users click a door that opens onto "not yet" |
| **B. Availability-first** | Check first; only offer the door if it opens | Fewest dead ends; **violates the stated principle** and loses the demand signal from the click |
| **C. Availability-labelled (recommended)** | Always show both doors, but label the official one with its real state — *"Official sets aren't ready for Nursing yet — see what's available"* | Preserves choice and acknowledgement; removes the surprise; still records the click as demand |

**Recommendation: C.** It satisfies every stated principle — the user still chooses, unmet intent is still
acknowledged, no one is force-routed — while removing the bait. The A2 screen still exists for users who choose
the labelled door; it just stops being a surprise.

### §1.3-REVISED — ratified 2026-08-06 (supersedes the table above)

The owner adopted C's principle but sharpened the mechanism: **the intent card's supporting copy is resolved
dynamically against the user's program and rendered before selection.** The intent itself is unchanged; only
expectations move earlier.

| Program state | "Study existing materials" supporting copy |
|---|---|
| Supported (a qualifying Official Review Set resolves) | *Start learning immediately with Official Review Sets.* |
| Unsupported | *Coming soon for {Program}. You can still explore community notes or build your own Study System.* |

Three consequences for implementation:

1. **Availability must be resolved before Step 3 renders**, not after the tap. That means the qualifying lookup
   moves earlier in the flow, and its latency sits on the Step 2→3 transition. Resolve it during Step 2 submit
   (where the program is now persisted anyway) and carry the result forward, so Step 3 renders instantly.
2. **The lookup must fail open.** If the availability check errors or times out, render the *supported* copy
   rather than falsely claiming "Coming soon" — a false negative is the worse error, because it tells a learner
   content does not exist when it does. This mirrors the existing practice-first behaviour, which already
   swallows lookup errors and falls through (`onboarding/page.tsx:738`).
3. **The unsupported copy names the two fallbacks up front**, so branch 4b becomes a confirmation of what the
   card already said rather than a new disclosure. 4b's screen copy should echo the card, not restate it
   differently.

This is strictly better than my option C: C removed the surprise but still made the user read a label and infer.
The ratified version tells them what happens next, in the same words they will see when they get there.

Note on the demand signal: we do **not** need the intent click to know demand. We already know the user's
program. A "Request this program" wishlist can be driven off program values alone. The click adds *intent*
(wanted-official vs wanted-to-author), which is worth something but is not the primary signal.

## 1.4 "Branch on curator status at Step 2" collides with when `profileType` is persisted

The proposal asks Step 2 to offer a catalog-backed input for Teachers/Admins. But **`profileType` is not
persisted until Step 5** (`page.tsx:592`) — during onboarding the server sees `profile_type = null`.

Two consequences:

1. **Good news, and it shrinks the mandatory patch.** `isTeacherSelectableOwner` is
   `role == ADMIN || profileType == TEACHER` (`NoteService.java:1420-1422`). A self-selected Teacher at Step 3
   is still `profileType = null`, so they take the **learner** branch. Teacher onboarding is *not* B0-blocked.
   Only a pre-existing **ADMIN** hits the curator branch — and `AuthService` only ever sets `UserRole.USER`
   (`:168`, `:930`), with no promote endpoint anywhere, so ADMIN accounts are provisioned by hand and do not
   sign up. Near-zero volume.
2. **Bad news for the redesign.** Branching Step 2 on curator status requires either persisting `profileType` at
   Step 1, or branching on the client draft only. Persisting at Step 1 is the honest fix, but it interacts with
   `OnboardingGuardService`, whose exemption is *deliberately* written around the current ordering
   (`OnboardingGuardService.java:21-24`, and `onboarding.md:281` says do not narrow it). That is a separate,
   deliberate decision that must update the comment and the doc in the same change.

## 1.5 "Persist Step 2 early" is right, but cannot use the existing endpoint

`updateLearningProfileContext` → `PUT /users/profile` is a **full replace**, and:

- `learnerLevel` is `@NotNull` (`UpdateUserProfileRequest.java:26-27`) → cannot be called before a level exists.
- The client compensates with a `getMe()` read-modify-write (`api.ts:2284-2299`) — two round trips, **racy**, and
  it always re-sends `email`, which hits `AuthService.java:500` and **silently nulls `pendingEmail`**, cancelling
  any in-flight email-change verification.
- `normalizeUsername("")` throws (`:1067-1076`), so the endpoint hard-depends on a populated username.

**Recommendation:** add a narrow `PUT /users/profile/learning-context` taking only `learnerLevel` +
`courseProgram`, mirroring the five single-purpose endpoints that already exist on `UserProfileController`
(`/profile/exam-date`, `/profile/goal`, `/profile/focus-subjects`, `/profile/public-visibility`,
`/profile/study-days-per-week`). This is an established pattern, not a new one.

## 1.6 An explicit anti-drift lock must be consciously superseded

`docs/features/dashboard.md:235`: *"Do not restructure `/onboarding`'s Profile Type → Study Goal → Input Method →
Study Pack Generation → Completion flow… that flow is locked and remains unchanged."*

The redesign restructures exactly that. The lock must be superseded deliberately, in the same commit, with the
reason recorded. (Note the lock itself is already stale — it names a "Study Goal" step that does not exist; see
§11.)

## 1.7 Smaller objections

- **"Best match" is really "most recently updated."** `…OrderByUpdatedAtDesc` + `[0]` (`page.tsx:730`). A newer,
  thinner set outranks an older complete one. Routing a first session on that ordering is fragile.
- **The gate and the render use different criteria.** Onboarding gates on `itemCount > 0 && readyCount > 0`, then
  renders `DashboardStudyPlanSection`, which **re-fetches and shows `publicPlans[0]` unfiltered**
  (`dashboard-study-plan-section.tsx:150-153`). Reusing that component inherits the split.
- **Adoption can silently succeed with zero notes.** `adopt()` is deliberately non-transactional with per-item
  `RuntimeException` swallowed into `skippedCount` (`NoteCollectionService.java:1270-1279`). An intent flow
  ending in adoption must read `copiedCount`/`skippedCount` and handle zero. **This is also where B1 from the
  pressure test bites** — every item copy goes through `copyNote`, which currently throws an FK violation for any
  note with join rows.
- **No program deep-link exists for Review Sets.** `/explore?courseProgram=X` forwards the param to the *Notes*
  tab only; the Review Sets panel reads the saved profile instead (`published-plans-page-client.tsx:106-107`).
  The "Explore Community Notes" fallback can deep-link (`/public/library?courseProgram=X`); an
  "official sets for my program" link cannot, today.

---

# 2. Recommended step sequence

Unchanged from today: **5 steps**. The intent question replaces nothing and adds no step, because it absorbs the
existing input-method choice.

| Step | Name | Content | Persisted on submit |
|---|---|---|---|
| 1 | Profile Type | Student / Exam Reviewer / Teacher / Professional | **`profileType`** (new — see §4) |
| 2 | Learning Context | Learner Level, Course / Program, optional Exam Date | **`learnerLevel`, `courseProgram`, `examDate`** (new — see §4) |
| 3 | **First Intent** | Two availability-labelled doors (option C, §1.3) | nothing |
| 4a | Study existing → confirm & adopt | practice-first screen (existing) | `onboardingCompletedAt` on adopt |
| 4b | Study existing → not yet available | honest state + three chosen fallbacks | `onboardingCompletedAt` on fallback choice |
| 4c | Create my own → input + generation | existing Step 3/4 merged | `onboardingCompletedAt` on reaching completion |
| 5 | Completion | branch-specific (see §6) | — |

The current flow's Step 3 ("How do you want to start?" — generate vs own note) becomes a **sub-choice inside 4c**,
not a top-level step. That is what keeps the step count flat.

---

# 3. Routing table — Profile Type × Intent × availability

`A` = a qualifying Official Review Set resolves for the user's program (`itemCount > 0 && readyCount > 0`, after
the resolution layer of §7.1). Today only ~4 programs can be `A`.

| Profile | Intent | Availability | Route | Completion persisted |
|---|---|---|---|---|
| BOARD_EXAM | Study existing | **A** | practice-first confirm → adopt → `/collections/{id}` | on adopt |
| BOARD_EXAM | Study existing | **not A** | A2 honest state + fallbacks | on fallback choice |
| BOARD_EXAM | Create own | any | 4c create flow | on completion |
| STUDENT | Study existing | **A** | practice-first confirm → adopt → `/collections/{id}` **(new — currently unreachable)** | on adopt |
| STUDENT | Study existing | **not A** | A2 honest state + fallbacks | on fallback choice |
| STUDENT | Create own | any | 4c create flow | on completion |
| TEACHER | Study existing | **A** | practice-first, teacher copy ("ready-made Lesson Plan") | on adopt |
| TEACHER | Study existing | **not A** | A2, teacher copy | on fallback choice |
| TEACHER | Create own | any | 4c, teacher copy ("create teaching material") | on completion |
| PROFESSIONAL | Study existing | **A** | practice-first, "Collection" label | on adopt |
| PROFESSIONAL | Study existing | **not A** | A2 | on fallback choice |
| PROFESSIONAL | Create own | any | 4c create flow | on completion |

**Labels come from `getCollectionLabels` / `collection-labels.ts:43-84`** (BOARD_EXAM→"Review Set",
STUDENT→"Study Plan", TEACHER→"Lesson Plan", PROFESSIONAL→"Collection"). No new label vocabulary is needed.

**The only structural change to eligibility is opening practice-first to non-BOARD_EXAM profiles**, currently
blocked by `page.tsx:723-726`. Given BOARD_EXAM is 70.94% of profile-typed accounts and STUDENT 27.09%, opening
it to STUDENT roughly doubles the addressable population for Branch A — but only where content exists.

**Teacher recommendation (asked for explicitly):** same two intent choices, teacher-specific copy, and **no
catalog-backed program input during onboarding**. Reasons: (a) `profileType` isn't persisted yet so the server
can't treat them as a curator anyway; (b) a catalog-only input would make onboarding *unsatisfiable* for a
teacher whose real field isn't in the 21-row catalog (Computer Science, Software Engineering…) — recreating B0
in a new place; (c) a teacher who also studies personally must not be trapped in a curator-only workflow. The
onboarding program stays personal context. The curator catalog selection belongs at first authoring, where a
"your profile program isn't in the curated catalog — pick the programs this note applies to" affordance solves
C8 without blocking activation. **One rule: onboarding collects personal context; curation vocabulary is chosen
at authoring time.**

---

# 4. Exact persistence points

Current state (verified):

| Field | Where persisted today | Problem |
|---|---|---|
| `profileType` | Step 5, `POST /auth/onboarding` (`page.tsx:592`) | Server sees null for the whole flow |
| `learnerLevel` | Step 5, fire-and-forget `PUT /users/profile` (`:610`) | **Permanently lost on failure, silently** |
| `courseProgram` | Step 5, same call | Same; and **absent from `CompleteOnboardingRequest` entirely** |
| `examDate` | Step 5, `POST /auth/onboarding` | **Nulled for non-BOARD_EXAM** (`AuthService.java:383`, `:615-620`) — undocumented |
| `onboardingCompletedAt` | Step 5, guarded/idempotent | Correct |

Target state:

| Field | Persist at | Endpoint |
|---|---|---|
| `profileType` | **Step 1 submit** | `POST /auth/onboarding/profile-type` (exists, additive, safe — writes only `profileType`) |
| `learnerLevel`, `courseProgram` | **Step 2 submit** | **new** `PUT /users/profile/learning-context` |
| `examDate` | **Step 2 submit** (BOARD_EXAM only) | `PUT /users/profile/exam-date` (exists) |
| `onboardingCompletedAt` | branch-specific — see §3 table | `POST /auth/onboarding` |

Two required care points:
- Persisting `profileType` at Step 1 makes `OnboardingGuardService`'s exemption tightenable — but tightening is a
  **separate deliberate decision**; the guard comment and `onboarding.md:281` must be updated together with it.
  Default: leave the guard as-is.
- `POST /auth/onboarding`'s unconditional `setExamDate` must be fixed or explicitly documented, otherwise
  persisting exam date at Step 2 gets silently reverted at Step 5 for anyone who switches profile type.

---

# 5. The immediate B0 repair (must ship in `v0.71.0`)

**Strict minimum — one payload field.** `courseProgramText` is not sent on note creation; the backend now
requires a resolvable program; the profile is still null at Step 3.

1. `frontend/app/onboarding/page.tsx:913-919` — add
   `courseProgramText: draft.courseProgram.trim() || null` to the `createNote` payload. `canContinueFromStepTwo`
   (`:315`) already guarantees it is non-blank, and this covers **both** the `generate` and `own_note` paths
   since both use this one call.
2. `frontend/app/onboarding/page.tsx:866` — pass the program to `generateNoteFromTopic` as well. Not required to
   unblock, but `profile-learning-context.md:62` already mandates it ("must be read at submit time, sent on the
   first generation request"), and without it the first generated note has no domain.
3. `frontend/app/onboarding/page.test.tsx:582-588` and `:581` — update the two exact-match assertions. The
   existing comment there claims the payload cannot silently drop an authoring axis; it should be corrected to
   say the program axis is now carried.
4. **Not required:** early persistence, the new endpoint, teacher branching, ADMIN's curator path. All deferred.

**Why this is enough:** a self-selected Teacher is `profileType = null` at Step 3 → learner branch → the field
resolves. Only pre-existing ADMIN accounts hit `validateCuratedProgramIds(null)`, and ADMIN is hand-provisioned
with no signup path. That residual is a Known Limitation, not an activation blocker.

**Add to `RELEASES.md` v0.71.0 Known Limitations:** the fire-and-forget learning-context write can still lose
`learnerLevel`/`courseProgram` silently; `POST /auth/onboarding` nulls `examDate` for non-BOARD_EXAM; an ADMIN
account re-running onboarding cannot complete the create-note path.

---

# 6. The redesigned target

Steps 1–2 as §2, persistence as §4, routing as §3, plus:

**Step 3 — First Intent.** Two cards, availability-labelled (option C). Copy per the proposal, with the official
card carrying its real state for the user's program.

**Branch 4b — the honest state.** *"We're still building this learning path — you're among the first learners
looking for {program}."* Three chosen fallbacks, none automatic: **Build your own Study System** (primary) →
4c; **Explore Community Notes** (secondary) → `/public/library?courseProgram={program}`, which is the one real
program-filtered deep link that exists; **Go to Dashboard** (tertiary). Design the screen so a future
**Request this program** action drops in cleanly — deferred, not built.

**Branch 4c — create.** Preserves every guided-flow exception in §9.

**Completion.** Existing-material → complete on adopt, land on `/collections/{id}`, **no Study Pack completion
screen**. Create → existing hierarchy (Open your Study Pack primary, Dashboard secondary, recommended set
supplementary). Unmet-intent → complete when the fallback is chosen, route there, never return to note generation.

---

# 7. Ship in `v0.71.0`, or split?

## Recommendation: **B0 repair in `v0.71.0`; the redesign as its own release immediately after.**

The size of `v0.71.0` is **not** the argument — that reasoning was explicitly pre-rejected, and rightly. The
argument is a dependency:

**7.1 The redesign's core mechanism sits on a layer this release just found broken.** Availability routing needs
program matching to be trustworthy. The pressure test found program matching broken in three independent places
in this very release: **B2** (stale join rows win over a learner's edited program), **B3** (the non-paginated
public path is blind to join rows entirely), **C2** (the program dropdown offers values that return zero
results). Add the pre-existing exact-case-sensitive-equality problem and four disagreeing vocabularies (§1.1).
Building a router that tells users "no official content exists for your program" on top of that will produce
confident false statements. **The resolution layer is a prerequisite, and it is its own piece of work.**

**7.2 The slice-4 precedent cuts the other way, and someone will raise it.** Slice 4 was pulled into `v0.71.0`
because deferring carried a re-learning cost — teachers would learn a two-field model and then unlearn it. That
asymmetry does not exist here. **Onboarding is a one-time flow.** A user who onboards between `v0.71.0` and the
redesign re-learns nothing; they simply never see the new flow. The thing that made slice 4 free to pull in is
exactly the thing that makes this free to defer.

**7.3 What deferring does *not* defer.** B0 is fixed before signoff. No new-user activation path stays broken.
The target flow is recorded here and indexed, so it cannot be lost.

## Proposed sequence

| Release | Scope |
|---|---|
| **`v0.71.0`** | B0 repair (§5) + doc corrections (§11). Known Limitations for the rest. |
| **`v0.72.0` — Onboarding Activation Repair** | Early persistence + the narrow endpoint (§4); the `examDate` null fix; program-vocabulary resolution layer (§7.1) — `normalizeForLookup` on both sides of the collection match plus `exam_goal_slug` ↔ catalog-name resolution via the existing `ExamGoalCourseProgramProvider`; reconcile the onboarding suggestion list against the catalog; the C8 authoring-time affordance. **No flow restructure.** |
| **`v0.73.0` — Onboarding Intent Router** | Steps 1–5 as §2/§3/§6, practice-first opened beyond BOARD_EXAM, the A2 honest state, availability labelling. Gated on `v0.72.0`'s resolution layer landing and on the availability question in §12 being answered. |

If the owner prefers, `v0.72.0` and `v0.73.0` can merge — but **not** before the resolution layer exists.

---

## §7-REVISED — ratified 2026-08-06 (supersedes the recommendation above)

**The redesign ships in `v0.71.0`.** The owner's reasoning: onboarding is now telling the wrong product story.
NoteLib has moved from a notes-first AI workflow to a learning system with multiple valid entry points, and the
first impression should reflect that immediately rather than shipping a knowingly outdated model — provided the
existing building blocks and routing logic are reused safely.

**My §7.1 objection is not withdrawn — it is mitigated by decision 4.** The concern was that availability
routing sits on a program-matching layer this release found broken, so the router could confidently tell a
learner "Coming soon for Nursing" while PNLE sits published under a differently-cased tag. Decision 4 addresses
this directly by auditing production *before* the resolution layer is designed. The risk is therefore managed by
sequencing rather than by deferral:

### Blocking prerequisite — SATISFIED 2026-08-06, gate cleared

**Run against production 2026-08-06. Verdict: all four published plans use exact catalog names — no slugs, no
case drift, no mixture. No resolution layer is needed; step 4 of the ordering below is struck.** Full results and
their consequences: `docs/claude-plans/onboarding-review-set-vocabulary-audit-results.md`.

The tripwire below was **not** triggered. What the audit did surface is a *drift* risk rather than a present
defect: the admin publish picker is fed from values on public notes, not the catalog, so a plan could be tagged
with anything in future. The recommended guard is to point that picker at the catalog — a small frontend change,
not a matching-path change.

The original decision table, retained for the record:

| Query 1 verdict | Resolution layer required |
|---|---|
| All catalog names | Case-insensitivity only — use the existing, currently-unused `CourseProgramNormalizationUtils.normalizeForLookup` on both sides of the collection match |
| All exam slugs | Slug→name resolution via the existing `ExamGoalCourseProgramProvider` |
| **Mixed** | **A live matching bug.** Document explicitly, then design around both conventions. Do not paper over it with normalization that hides which records are mis-tagged. |

Queries 2–4 size how load-bearing the "Coming soon for {Program}" copy is; Query 5 captures a pre-change
onboarding-completion baseline so the redesign can be measured against it.

### Ordering within `v0.71.0`

1. **B0 repair first, on its own branch** (§5). It is three lines and it unblocks activation. It must not wait
   behind the redesign, so that if the redesign slips, signoff is still possible.
2. ~~**Production audit**~~ — **DONE 2026-08-06, gate cleared.**
3. **Activation repair** — early persistence + the narrow endpoint (§4), the `examDate` null fix. No flow change.
4. ~~**Resolution layer**~~ — **STRUCK.** The audit found production tagging clean; building one would be
   unnecessary normalization. Replaced by a much smaller **drift guard**: point the admin publish picker at
   `GET /course-program-catalog` instead of `listCoursePrograms("public")`
   (`collection-detail-page-client.tsx:2382-2391`, which already passes `allowCustom={false}`).
5. **Intent Router** — steps 1–5 per §2/§3/§6, with the dynamic pre-selection copy of §1.3-REVISED.

Steps 3–5 were previously scoped as `v0.72.0`/`v0.73.0`; they now land inside `v0.71.0`. The three-release table
above is retained only as a record of the original recommendation and the reasoning behind it.

**Availability lookup is now trivially implementable.** Because tags are exact catalog names and the user's
program is the same vocabulary for the 86% of users whose value is in the catalog, the Step-2 availability check
is the existing `listCourseProgramStudyPlans(program)` call plus the existing qualifying predicate — no new
matching logic. All four published sets have `items == ready` exactly (74/74, 63/63, 52/52, 43/43), so the
predicate is nowhere near its boundary and Branch A will not flicker on marginal content.

### Risk accepted, and how to tell if it is going wrong

The honest residual risk is that `v0.71.0` already carries five blockers, and the redesign touches the most
abandonment-sensitive flow in the product. The mitigations that make this acceptable: the B0 repair is
independent and lands first; every guided-flow exception in §9 is enumerated and must be preserved; and the
whole-release pressure test re-runs against the final state before signoff. **The tripwire:** if the production
audit returns *mixed* conventions, the resolution layer stops being a small change — at that point re-open the
sequencing question with the owner rather than absorbing it silently.

---

# 8. Required changes

## Backend
- **New** `PUT /users/profile/learning-context` (`learnerLevel`, `courseProgram`) on `UserProfileController`,
  following the five existing single-purpose endpoints. Avoids the `@NotNull`, read-modify-write, race, and
  `pendingEmail`-clobber problems of the full-replace path.
- Fix `AuthService.completeOnboarding`'s unconditional `setExamDate` (`:383`) so a non-BOARD_EXAM completion does
  not null a previously-set date — or document it.
- **Resolution layer** (`v0.72.0`): case-insensitive collection matching via the existing but unused
  `normalizeForLookup`, plus slug↔name resolution. Changes semantics on a live path — needs its own care.
- Consider a real `isOfficial` marker on collections. Today "official" is purely procedural (admin-owned +
  PUBLIC + root), with **no queryable flag** (`collections.md:715`). Any tiering needs one. Out of scope unless
  tiering is wanted.
- `OnboardingGuardService`: no change by default; if tightened, update the comment and `onboarding.md:281`.

## Frontend
- `onboarding/page.tsx`: the B0 payload fields (§5); then Step 1 / Step 2 persistence calls; a new Step 3 intent
  screen; the 4b honest state; open practice-first past `:723-726`; branch copy by profile.
- Do **not** reuse `DashboardStudyPlanSection` unchanged for the confirm screen — it re-fetches and renders
  `publicPlans[0]` with no qualifying predicate, so the gate and the render disagree (§1.7). Pass the already-
  resolved plan down instead.
- Handle `copiedCount`/`skippedCount` on adopt; a zero-copy adopt must not present as success.
- Reconcile `COURSE_PROGRAM_SUGGESTIONS` with the catalog; fix the `/profile` vs lightweight-prompt vs onboarding
  disagreement (pressure-test M15).
- Add the missing required marker on Step 2's Course / Program (pressure-test L11).

---

# 9. Guided-flow exceptions that must survive

Each verified; losing any is a regression.

1. **Generation idempotency** — `if (draft.noteId) { goToStep(4); return; }` (`:895-898`), persisted in
   localStorage so it survives refresh. Retry calls `createStudyPackFromNote` on the **existing** note, never
   `createNote`. No `Create Again` button (asserted absent, `page.test.tsx:548-549`).
2. **No Back during generation** — step 4's footer has no Back branch (`:1669-1701`). `handleBack` *does* have a
   destructive branch (`:833-844`) that is unreachable only because nothing invokes it. **A redesign adding a
   generic Back would silently re-expose it mid-generation.**
3. **`autoApplyMetadata: true`** — fill-only, never overwrite (`StudyPackService.java:1005-1019`). The subject is
   a by-product of a generation that must run anyway; a pre-generation LLM call would add cost and latency at the
   most abandonment-sensitive moment. Do not confuse with `applyBulkGeneratedMetadataToNote`, which *does*
   overwrite.
4. **The study-pack-limit partial-failure split** (`:937-958`) — `savedNote` is captured before generation, so a
   limit error routes to a dedicated "Your note is saved." screen at step 5 rather than stranding the note or
   leaving the user un-onboarded.
5. **Completion attempted once per mount** (`completionAttemptedRef`, `:584-588`) — guards React 18 StrictMode
   double-invoke.
6. **`ONBOARDING_V2_COMPLETED` fires in `.finally()`** so funnel completion isn't undercounted on transient errors.
7. **Deferred-completion fallback** (`:617-621`) — prevents a redirect loop for a user whose completion failed.
8. **Practice-first lands on the collection detail page, not a quiz** — a live-testing finding recorded in a
   5-line comment at `:816-821`.
9. **Poller swallows errors and keeps polling** (`:572-574`).

---

# 10. Tests

**Must change for B0 (`v0.71.0`):** `page.test.tsx:582-588` (exact `createNote` payload), `:581`
(`generateNoteFromTopic` single-arg).

**Will change for the redesign:** `:565-568`, `:783-788`, `:392-395` (exact `completeOnboarding` args);
`:571-573` (`updateLearningProfileContext` asserted at Step 5); `:274-279` (`ABANDONED` `{last_step: 3}`);
`:385` ("Step 5 of 5"); `:314`; `:548-549`; and many copy-exact assertions.

**Coverage gaps worth closing — every onboarding frontend test is fully mocked; none exercises a real backend.**
- No test asserts `PUT /users/profile` rejects a null `learnerLevel`.
- No test covers the `getMe` → `PUT /users/profile` read-modify-write sequence.
- No test asserts `completeOnboarding` nulls `examDate` for non-BOARD_EXAM.
- No test at any layer for `AuthService.updateProfileType`.
- **Recommended:** one real integration test spanning onboarding → note creation, which is exactly the seam B0
  fell through. A mocked `createNote` cannot catch a backend contract change; that is precisely why B0 shipped
  green.

---

# 11. Documentation updates

| Doc | Change |
|---|---|
| `docs/features/onboarding.md` | **:30, :72-83** — delete the "Step 2 — Study Goal" section; no such step exists. **:213** — "learnerLevel and courseProgram are saved before the user advances" is **flatly false**; they are saved at Step 5, fire-and-forget. **:201-205** — record that `POST /auth/onboarding` nulls `examDate` for non-BOARD_EXAM. **:29/:40/:85/:106** — "Board Taker" → "Exam Reviewer". Add the v0.71.0 program-axis behaviour. |
| `docs/features/dashboard.md:235` | Consciously supersede the flow lock; it also names a step that does not exist. |
| `docs/features/profile-learning-context.md` | **:28** — "guaranteed to have a saved profile learner level" is false while the write is fire-and-forget. **:62** — note that onboarding currently violates the send-at-submit rule (fixed by §5.2). |
| `docs/features/collections.md` | If practice-first opens beyond BOARD_EXAM, update adoption/labels. |
| `docs/features/teacher-flow.md` | Currently has **zero** onboarding mentions; add the §3 teacher rule. |
| `RELEASES.md` | v0.71.0: the B0 fix + Known Limitations (§5). |
| `docs/product/ROADMAP.md` | New Backlog Index row (invariant: no planning doc without one) **and** an update to "Phase 1 — Practice-first activation onboarding branch", which is the ratified source of truth for this flow and says to update it in the same commit as any reprioritization. |
| `docs/claude-findings/v0.71.0-pre-signoff-pressure-test.md` | Annotate which onboarding findings this plan covers vs. which stay open. |

---

# 12. Unresolved assumptions and contradictions

1. **What do production Official Review Sets actually hold in `course_program`?** — **OWNER-ASSIGNED 2026-08-06
   (decision 4), blocking the Intent Router.** Decides whether the resolution layer needs slug↔name mapping or
   only case-insensitivity. Local data is two rows of dev junk; the ROADMAP names the four sets by exam slug;
   test fixtures use both conventions. Runnable queries live in
   **`docs/claude-plans/onboarding-review-set-vocabulary-audit.sql`** (Query 1 is decisive; 2–4 size the
   "Coming soon" population; 5 captures a pre-change completion baseline). If both conventions appear, that is a
   **live matching bug**, not a fixture artefact — document it rather than normalizing over it.
2. **How many qualifying Official Review Sets exist in production, for which programs?** Zero ship in migrations;
   all are admin-created at runtime, so this is unknowable from the repo. It sets the true A/not-A ratio and
   therefore how load-bearing the A2 screen is.
3. **Should `profileType` persist at Step 1?** Needed for Step 2 curator branching; interacts with a guard
   whose exemption is deliberate. My recommendation (§3) avoids needing it — but if catalog-backed teacher input
   is wanted at Step 2, this must be decided first.
4. **Should "official" become a queryable flag?** Today it is procedural only. Any tiering or ranking needs one.
5. **Does opening practice-first to STUDENT invalidate the Phase 1 retention read?** The ROADMAP's validation
   design compares create-first vs practice-first cohorts *on the same covered tracks*. Changing eligibility
   mid-measurement may confound Round 2, which is already due for re-read after 2026-08-06.
6. **The wishlist / "Request this program" feature** is deferred. The A2 screen should be built so it drops in,
   but nothing here depends on it.
7. **Contradiction to flag:** the proposal says "do not force a user into a fallback they did not choose", while
   the current dashboard for a new user is a creation-first shell whose only path to existing material is one
   tertiary text link (`dashboard-empty.tsx:87-93`). Routing an unmet-intent user to Dashboard *is* a soft force
   toward creation. Either the Dashboard empty state changes too, or "Go to Dashboard" stops being offered as a
   neutral third option.

---

# Changelog

**2026-08-06 — written.** Full analysis following the `v0.71.0` pre-signoff pressure test. Original
recommendation: B0 repair in `v0.71.0`, redesign split across `v0.72.0`/`v0.73.0`.

**2026-08-06 — owner-ratified, five decisions.** Recorded in the Status block at the head of this file.
Superseding annotations added inline as §1.3-REVISED and §7-REVISED; the original text of both is retained
deliberately, so a future session can see what was recommended and why the owner decided otherwise. The
guiding philosophy ("onboarding is an intelligent router…") was added by the owner and governs the redesign.

Carried out in the same pass: `.DS_Store` untracked and gitignored; the production audit written to
`onboarding-review-set-vocabulary-audit.sql`; propagation into `ROADMAP.md`, `RELEASES.md` and
`docs/features/onboarding.md`.

**2026-08-06 — production audit run, gate cleared.** Owner ran
`onboarding-review-set-vocabulary-audit.sql`; results and consequences in
`onboarding-review-set-vocabulary-audit-results.md`. Verdict: all four published plans use exact catalog names,
so **no resolution layer is needed** and step 4 of the §7-REVISED ordering is struck, replaced by a small drift
guard. Two corrections to this plan followed: **§1.1 is withdrawn for current data** (matching works as-is), and
**§1.2's premise was wrong** — Branch A covers 82.1% of program-holding users, not a minority. Both are
annotated inline rather than deleted.

**2026-08-06 — second owner ruling pass, UX direction locked. §13 added and is now authoritative** for the
intent step, the unsupported-program experience, the Dashboard question, teacher copy, completion behaviour and
analytics. It supersedes §1.3-REVISED and §6 where they differ; both are retained as reasoning. Three
refinements the owner made on my proposal, each of which improved it: the unavailable path stays **selectable**
with a small neutral line rather than a dominant "Coming soon" (an 18% path should not read as a dead end before
the user sees what is behind it); Dashboard redesign is explicitly **out of scope**, with `Finish setup` as a
quiet tertiary action instead; and **no shared completion screen** — reaching the chosen first experience *is*
the completion. I collapsed the eleven requested analytics signals into **3 new events + 4 reused**, and flagged
the mechanical consequence of five exit points: completion must persist *before* navigation at each one.

**Open at time of writing:** Open decision A only — how B2 is resolved given decision 1. It does not block the
B0 repair, which should land first and independently.

---

# 13. Ratified UX specification (owner, 2026-08-06 — second ruling pass)

**This section is authoritative for the Intent Router's user-facing behaviour.** Where it differs from
§1.3-REVISED or §6, this wins. Those sections are retained as the reasoning that led here.

Everything below is grounded in the same two things the rest of this plan is: the **B0 activation repair**
(§5, ships first and independently) and the **vocabulary / persistence defects** the pressure test found (§4,
§12). The UX cannot work without those — an intent router that routes on a program value which was never
persisted, or which the note-creation call never sends, is routing on nothing.

## 13.1 The intent step

Two **equal-weight, selectable** paths. Neither is ever disabled.

| | Title | Supporting copy |
|---|---|---|
| **1** | Study with ready-made materials | Start with an Official Review Set built for your program. |
| **2** | Build from my own notes | Write, paste, or create a note and turn it into a Study Pack. |

When no Official Review Set exists for the learner's program, option 1 shows a **small neutral availability
line** — not a warning, not a badge competing with the title, and **not a disabled state**:

> No Official Review Set yet for {Program}

**The option stays selectable**, because its next screen still offers genuinely useful alternatives. This is the
key refinement over §1.3-REVISED, which proposed a dominant "Coming soon" message inside the card: that framing
made an 18% path read as a dead end before the user had seen what was behind it.

Two implementation notes:

- **Availability must resolve before this step renders.** Resolve it during Step 2 submit — where the program is
  now persisted anyway (§4) — and carry the result forward, so the intent step paints instantly.
- **The lookup must fail open.** On error or timeout, render *without* the availability line rather than
  asserting absence. A false "no Review Set yet" is the worse error: it tells a learner content does not exist
  when it does. This mirrors the existing practice-first behaviour, which already swallows lookup errors and
  falls through (`onboarding/page.tsx:738`).

**Open detail worth deciding before build:** ~30 users hold programs outside the 21-entry catalog, the largest
being `Professional / Board Exam Review` (14 users). Interpolating free text yields *"No Official Review Set yet
for Professional / Board Exam Review"*, which is honest but reads awkwardly. Consider a generic fallback
phrasing when the value is not a catalog program.

## 13.2 The unsupported-program experience

Ratified copy:

> **We're still building an Official Review Set for {Program}.**
>
> You can still start learning today with your own notes or explore material already shared in NoteLib.

Action hierarchy — **the user always chooses; never auto-redirect**:

| Rank | Action | Destination |
|---|---|---|
| 1 — primary | Build from my own notes | the creation flow (identical to intent option 2) |
| 2 — secondary | Explore related public notes | `/public/library?courseProgram={program}` |
| 3 — quiet tertiary | Finish setup | completes onboarding, routes to Dashboard |

**"Request this program" is deliberately NOT in this release** unless it records a real, durable demand signal.
A control that only registers a promise is worse than its absence. Leave layout room for it; build nothing.

Note that we do not need the button to measure demand — the profile program value already tells us which
programs learners are asking for, and `ONBOARDING_V2_INTENT_UNSUPPORTED_VIEWED` (§13.5) makes that queryable
per-user without shipping a control.

Two things this settles by construction:

- The primary fallback is the *other* intent, so the unsupported screen is a soft, explained re-route rather
  than a rejection. That is coherent, and it is why option 1 can stay selectable in §13.1.
- `/public/library?courseProgram=X` is the one real program-filtered deep link that exists today. `/explore`
  accepts the parameter but its Review Sets tab **ignores it** and reads the saved profile instead
  (`published-plans-page-client.tsx:106-107`) — so "Explore related public notes" must point at the public
  library, not at `/explore`, or the filter silently does nothing.

## 13.3 Dashboard — explicitly out of scope

**No Dashboard empty-state redesign in this release.** The onboarding fallback screen owns the unmet-intent
explanation and offers the useful alternatives *before* Dashboard is ever reached.

`Go to Dashboard` is renamed **Finish setup** and stays visually tertiary so it does not compete with the two
meaningful learning actions.

This resolves the contradiction logged at §12.7 — routing an unmet-intent learner to a creation-first dashboard
was a soft push toward the thing they had just declined. The fix is that they now arrive there having already
been offered, and declined, both learning paths. The dashboard's creation-first empty state
(`dashboard-empty.tsx:59-97`) remains as-is and is a separate, unscheduled question.

## 13.4 Teacher onboarding

Same two-intent structure, adapted copy. **No separate personal-study-versus-teaching branch** — the selected
action expresses immediate intent without forcing a second identity choice on top of Profile Type.

| | Title | Supporting copy |
|---|---|---|
| **1** | Use existing teaching and study materials | Browse Official Review Sets and public notes. |
| **2** | Create teaching or study materials | Write, paste, or create notes for yourself or your learners. |

This composes correctly with the constraint in §3 that onboarding collects *personal* context and curation
vocabulary is chosen at authoring time — a teacher still enters a free-text program here and is never blocked by
the 21-entry catalog.

**Open detail worth deciding before build:** the teacher copy says "Browse Official Review Sets **and public
notes**", which is a broader destination than the learner path's one-tap adopt. Decide whether a teacher whose
program *does* have a qualifying set still gets the one-tap adopt (adopting a Lesson Plan is a sensible teacher
action), or always lands on a browse surface. Recommendation: keep one-tap adopt when a set qualifies — it is
the strongest moment in the existing flow — and fall back to browse otherwise.

## 13.5 Completion behaviour

**Each path finishes at its real destination. No shared completion screen. Reaching the chosen first experience
is itself the completion.**

| Path | Destination |
|---|---|
| Official Review Set adopted | Review Set detail |
| Study Pack created | existing Study Pack completion screen |
| Explore selected | Explore / public library |
| Build from own notes selected | creation flow |
| Finish setup selected | Dashboard |

**This has a mechanical consequence that must not be missed.** Today the completion effect is keyed on
`currentStep === 5` (`onboarding/page.tsx:583-645`). Once paths stop passing through step 5, **completion must be
persisted at each branch point, before navigating away**. Three rules follow:

1. **Persist `onboardingCompletedAt` before `router.push`, not after.** Navigating first and completing second
   leaves a user who closes the tab mid-transition permanently un-onboarded.
2. **Keep the deferred-completion fallback** (`:617-621`, `:789-792`). It exists because `completeOnboarding`
   can fail, and without it a failed completion traps the user in a redirect loop back into onboarding they
   cannot finish. With five exit points instead of one, this matters more, not less.
3. **Keep `completionAttemptedRef`** (`:584-588`) or an equivalent per-exit guard, so React 18 StrictMode's
   double-invoke cannot fire `POST /auth/onboarding` twice.

The existing practice-first path already does this correctly — it completes, *then* pushes to the collection
(`:762-798`, `:821`). Model the other four exits on it.

## 13.6 Analytics

The core outcome question, and the thing the event set must answer:

> **Did the user reach the first experience they explicitly selected?**

Answering that needs a selection event carrying availability, and a terminal event carrying the destination.
The eleven signals the owner listed collapse into **three new events plus four reused ones** — the existing
`ONBOARDING_V2_*` inventory is a clean 1:1 (17 values, all firing, no orphans), so reuse is cheap and adding
eleven would be noise.

| Owner's signal | Event | New? |
|---|---|---|
| intent step viewed | `ONBOARDING_V2_STEP_VIEWED` with `step_name: "intent"` | reuse |
| selected ready-made / selected own notes / Review Set available-unavailable | **`ONBOARDING_V2_INTENT_SELECTED`** — `{intent: "ready_made"\|"own_notes", review_set_available: bool, course_program}` | **new** |
| unsupported state viewed | **`ONBOARDING_V2_INTENT_UNSUPPORTED_VIEWED`** — `{course_program}` | **new** |
| selected own-notes / Explore / Finish setup fallback | **`ONBOARDING_V2_FALLBACK_SELECTED`** — `{fallback: "own_notes"\|"explore"\|"finish_setup", course_program}` | **new** |
| Official Review Set adopted | `ONBOARDING_V2_PRACTICE_FIRST_PLAN_ADOPTED` | reuse |
| Study Pack completed | `ONBOARDING_V2_STUDY_PACK_GENERATED` | reuse |
| onboarding completed | `ONBOARDING_V2_COMPLETED` — **add `intent` and `destination` to its metadata** | reuse, extended |

Carrying `intent` and `destination` on `ONBOARDING_V2_COMPLETED` is what makes the outcome question answerable
from a single event rather than a session reconstruction.

Four notes for whoever implements this:

- **Add to the Java `AnalyticsEventType` enum before firing** (`CLAUDE.md` rule), and mirror in the frontend
  union in `lib/api.ts`.
- **`ONBOARDING_V2_INPUT_METHOD_SELECTED` is still needed and is not the same thing.** Intent is "what do I want
  to do first"; input method is "generate from a topic vs paste my own", now a sub-choice inside the create
  branch. Keep both.
- **`ONBOARDING_V2_CTA_GO_TO_DASHBOARD` currently fires with no metadata at all.** "Finish setup" from the
  unsupported screen should use the new `FALLBACK_SELECTED` event; leave the existing CTA event for the
  Study-Pack completion screen's own Dashboard button so the two are distinguishable.
- **Pre-existing gap worth closing while here:** the Step-5 adopt card (`DashboardStudyPlanSection
  context="onboarding"`) has *zero* instrumentation — it fires no analytics of its own, so recommended-set
  impressions and adoptions from the completion screen are currently invisible.

Baseline for measuring all of this: Query 5 of `onboarding-review-set-vocabulary-audit.sql`, captured pre-change
at **38.7% non-completion (141/364)**. Re-run it after ship.
