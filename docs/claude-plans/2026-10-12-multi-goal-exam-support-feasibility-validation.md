# [VALIDATION — NOT AN IMPLEMENTATION AUTHORIZATION] Multi-Goal Exam Support: Storage, State Transitions, Onboarding Semantics, Cost, Acceptance Criteria

**Author:** Feature Planner session, per the explicit owner instruction recorded in `RELEASES.md`'s `v0.169.0` entry and `CLAUDE.md`'s current-version block: *"before any Codex prompt, Feature Planner is to validate the storage migration, state transitions, onboarding semantics, cost, and acceptance criteria against the current code."*

**This document validates feasibility. It does not authorize implementation, does not write a Codex prompt, and does not change any code.** All findings below are MEASURED (read directly from current code or production, 2026-10-12, branch `releases/v0.169.0`) unless labeled INFERRED, DERIVED, or UNKNOWN. Three parallel code-mapping passes plus direct production reads fed this document; every claim is traceable to a `file:line` citation.

---

## 0. Where this stands against the owner's own scheduled gate

**This is not an open decision to re-litigate here — `ROADMAP.md`'s Backlog Index already carries a `[CHECKPOINT — due 2026-12-07]` row for exactly this population read, per `RELEASES.md`'s `v0.168.0` entry.** This section reports the current reading against that scheduled gate; it does not ask the owner to choose between proceeding now or waiting, since that choice is already on a calendar.

The 2026-10-08 decision file (`docs/claude-plans/study-journey-purpose-learner-goal-discovery-decision.md:134`) set the gate's trigger: **"re-open only if the ≥2-distinct-goal population grows past a handful."**

**Re-verified fresh against production, 2026-10-12 (MEASURED, not carried forward from the 2026-10-09 reading):**
- **2 of 426** `USER`-role accounts have adopted content spanning ≥2 distinct `exam_goal_slug` values (1 user at 2 distinct slugs, 1 user at 3). Unchanged from the 2026-10-09 count cited in `CLAUDE.md`'s `v0.169.0` block and `RELEASES.md`'s `v0.168.0` entry.
- **19 of 426** users have set `studyGoal` at all (any value, EXAM or SUBJECT). Of those 19: **16 hold a valid exam slug** (`ale`/`pnle`/`let`/`cpale`/`ce`), **3 hold free text** (a SUBJECT-style string). This is a precise split this pass obtained; it did not exist anywhere in the repo's code, migrations, or discovery docs before now.
- **Neither of the 2 multi-adoption users has `studyGoal` set at all** (MEASURED, verified directly) — their multi-exam-adoption behavior exists purely at the content layer, with no corresponding exam-goal *preference* signal today. Worth noting: the 2-user population this gate tracks is not the same 19 users who've ever engaged with the goal-setting feature itself.
- **For context the original decision file didn't have: 107 of 426 users have adopted content under exactly one `exam_goal_slug`**, versus 16 who've actually set that slug as their `studyGoal`. The more visible gap in the data is **single-goal-setting adoption** (roughly 15% of single-exam adopters ever set the matching goal) — not goal multiplicity, which remains at 2 regardless of how the single-goal number moves.

**This reading is ready to hand to §1-§6 of this document whenever the 2026-12-07 checkpoint is read, or sooner if the owner makes an explicit override — the same shape of decision already made once for CE Exam Hub.** This document does not make that call; it reports the number the gate was defined against, the way the original decision file itself modeled ("state it, don't fill the gap with intuition"). §7 returns to this with the technical verdict.

---

## 1. Storage migration — validated as cleanly buildable for EXAM goals; one small, genuinely owner-decision edge case

**Structural fact (MEASURED, `UserEntity.java:60-65`, `backend/.../service/AuthService.java:600-721`, `ProgressReportService.java:40-43`):** `studyGoal` (a single nullable `text` column, no DB constraint — `V70__rename_exam_goal_to_study_goal.sql:1-2`) is overloaded into **three** semantics, re-disambiguated independently on every read:

1. **EXAM** — `studyGoal` holds one of 5 values in `ExamGoalConfig.VALID_SLUGS` (`ale, pnle, let, cpale, ce` — `ExamGoalConfig.java:20`), mirrored by a DB `CHECK` on `course_programs.exam_goal_slug` (widened `V106→V154` to add `'ce'`, confirmed in sync as of this read).
2. **SUBJECT (legacy, singular)** — `studyGoal` holds free text that is *not* a valid slug. Documented as "legacy/current" in `docs/features/profile.md:89`.
3. **SUBJECT_FOCUS (current, plural)** — a **wholly separate** `focusSubjects text[]` column (`V71__add_focus_subjects.sql`), only consulted when `studyGoal IS NULL`.

**The proposed join table (`user_exam_goals(user_id, exam_goal_slug, is_active, …)`) is a clean, lossless fit for case 1 only — and that is exactly the scope the owner's 5 points describe ("multi-goal *exam* support").** Cases 2 and 3 are untouched by this design and should stay exactly as they are; nothing in the owner's 5 points asks to generalize free-text subjects into multiple rows, and nothing found in code argues that they should.

**For the 16 valid-slug rows: migration is trivially lossless — but the migration must be two steps, not one, or it recreates the exact redundant-storage problem the owner's point 1 explicitly forbids.** `INSERT INTO user_exam_goals (user_id, exam_goal_slug, is_active) SELECT id, lower(trim(study_goal)), true FROM users WHERE lower(trim(study_goal)) IN (...)` is step one. **Step two, required, not optional: clear the migrated slug values out of `studyGoal` in the same migration** (`UPDATE users SET study_goal = NULL WHERE lower(trim(study_goal)) IN (...)`), so `studyGoal` and the new join table never hold the same fact twice. Without this second step, the 16 slugs would live in both places — exactly the "redundant active-state storage... that can drift from the join rows" the owner's point 1 names as something to avoid. After this migration, **`studyGoal` becomes legacy-free-text-only** (serving only the 3 SUBJECT rows going forward); every reader needs an explicit precedence order rather than an implicit one:

> **Reader precedence (proposed, owner-confirmable): an active row in `user_exam_goals`, else `studyGoal` (now free-text-only), else `focusSubjects`.**

**DB-level enforcement for "one active goal per user," since nothing enforces exclusivity at the DB layer today (§2) and this is a natural place to add it where the old design had none:** a partial unique index `CREATE UNIQUE INDEX ON user_exam_goals (user_id) WHERE is_active` is the direct fit (at most one active row per user, enforced by Postgres, not just app code) — recommended over relying on app-level discipline alone, given §2 below shows the equivalent app-level discipline on the current design already has gaps.

**Cost note on the new table's own slug constraint:** a `CHECK (exam_goal_slug IN ('ale','pnle','let','cpale','ce'))` on `user_exam_goals` would be a **third** hardcoded copy of this same 5-value list, alongside `ExamGoalConfig.VALID_SLUGS` and `course_programs.exam_goal_slug`'s own `CHECK` — exactly the drift class this document's §1 shared-resolver recommendation already exists to close. If a third copy is added, it inherits the identical maintenance risk `ExamGoalConfig.java:38-43`'s own comment already documents one incident of.

**For the 3 free-text rows: this is the one genuine owner decision, not resolvable from code.** A table CHECK-constrained to 5 slug values structurally cannot hold arbitrary subject text. Three code-legal dispositions exist:
- **(a) Leave untouched** — `studyGoal` keeps serving these 3 users exactly as today (no join-table row, `GOAL_TYPE_SUBJECT` framing continues). Lowest risk, fully lossless, recommended given the size (3 users).
- **(b) Fuzzy-match** against `course_programs.name` and backfill where a match is found. Unnecessary complexity for 3 rows.
- **(c) Do nothing special** — equivalent in practice to (a).
- **Recommendation: (a).** At n=3, building matching logic is not proportionate; explicit owner sign-off is still needed since this is a product decision, not a code one.
- **⚠️ "Leave untouched" is not cost-free: it creates a new exclusivity gap unless closed explicitly.** If one of these 3 users later adds an exam goal through the new picker, they would end up with both an active `user_exam_goals` row **and** a legacy free-text `studyGoal` — exactly the EXAM/SUBJECT coexistence the owner's point 3 says must not happen. The fix is the same mechanism as today's exclusivity clearing, just extended: **adding any active exam goal must clear legacy `studyGoal`**, mirroring `AuthService.java:606`'s existing pattern. This closes the gap without needing to resolve the 3 rows' disposition any differently than "leave them until the user's next exam-goal action."

**Secondary finding, worth fixing as part of this work regardless of the above decision:** four separate backend call sites independently re-derive "is this an EXAM goal" via `ExamGoalConfig.isValidSlug(...)` with no shared resolver — `AuthService.java:717`, `ProgressReportService.java:90,366,706`, `PostSessionNextStepService.java:577`. `ExamGoalConfig.java:38-43`'s own comment documents a *prior* drift incident between this class and the DB catalog, caught only at signoff. A multi-goal migration that touches all four sites without consolidating them into one shared resolver would be repeating exactly the pattern that already caused one incident. **Recommend introducing a single shared "resolve active exam goal(s)" method as part of this work**, not as separately-scoped cleanup.

**Out of scope but flagged for awareness:** `UserEntity.examDate` (`:98-99`) has the identical single-valuedness problem `studyGoal` had — a single date for whichever exam. The owner's 5 points don't ask for a per-goal target date, so this is correctly out of scope; it will recur the day someone asks for one.

---

## 2. State transitions — exclusivity generalizes cleanly; the post-session exception does **not** already exist and needs real new logic

**EXAM/SUBJECT exclusivity today is real, deliberate, bidirectional app-level mutual-clearing — not an accident of one column holding one value (MEASURED, `AuthService.java:600-610,509-518`):**
```java
// updateStudyGoal (:600-610): setting a non-null studyGoal clears focusSubjects
if (normalizedStudyGoal != null) { user.setFocusSubjects(new String[0]); }   // :606
// updateFocusSubjects (:509-518): setting non-empty focusSubjects clears studyGoal
if (!focusSubjects.isEmpty()) { user.setStudyGoal(null); }                    // :514
```
No DB constraint enforces this — it is two service methods each remembering to clear the other, matching `docs/features/profile.md:94-97`'s own description exactly (doc and code agree as of this read). The owner's point 3 ("EXAM/SUBJECT exclusivity unchanged... never a Note/Plan access restriction") is already true today in exactly the way described — it's a profile-preference rule only, confirmed nowhere joined to Note/StudyPack/assessment access.

**⚠️ Correction: the two clearing directions generalize asymmetrically, not identically — get the direction wrong and point 3 breaks.**
- **Exam-goal-adds-clear-SUBJECT direction:** "setting an active exam goal clears `focusSubjects`" generalizes to "**does the user have ≥1 active row in `user_exam_goals`**" — correct as originally stated, because multiple active exam goals are allowed to coexist with each other (point 3), so this check only needs to ask "is there any."
- **SUBJECT-clears-exam-goals direction is NOT the mirror of that.** Setting `focusSubjects` must clear **every** exam-goal row, active or not — not just the active one. An *inactive* adopted exam goal still structurally "coexists" with the user under point 3's "multiple exam goals may coexist with each other" language, and point 3 is unambiguous that SUBJECT focus "can't coexist with any exam goal" (not "any active exam goal"). Checking only the active row and leaving inactive rows in place would let a user end up with a subject focus *and* dormant exam goals simultaneously — a real coexistence, just an invisible one. **The correct generalization clears the whole `user_exam_goals` table for that user, not one row.**
- **Asymmetry worth flagging to the owner (§8):** point 4 requires explicit disclosure before an exam-intent flow overwrites a subject focus, but says nothing about disclosure in the reverse direction — a user with several adopted exam goals who sets a subject focus from Profile would silently lose all of them in one save, with no equivalent "are you sure, this clears N exam goals" step named anywhere in the owner's 5 points. Worth deciding explicitly rather than inheriting asymmetric UX by omission.

**State transitions, enumerated (the owner asked for these by name; they were not previously listed as a discrete set):**

| # | Transition | What should happen | Confidence |
|---|---|---|---|
| 1 | Add first exam goal (no existing goals of any kind) | New active row created; no clearing needed | High — direct extension of today's single-value write |
| 2 | Add an additional exam goal (≥1 already active) | Point 4: "add and activate" — new row created **and** flagged active; does the *previous* active goal stay active (now 2 active) or get deactivated (exactly 1 active, by the new one)? **Point 1 says "one goal flagged active per user" (singular) — reading that literally, activating a new goal must deactivate the previous one.** Flag as owner-confirmable since point 4's own wording ("add and activate") doesn't explicitly settle whether "activate" is exclusive. | Medium — plausible default identified, not yet owner-confirmed |
| 3 | Explicitly set a different goal active (no add) | Exactly one row flips to active; all others flip inactive | High |
| 4 | Remove an inactive goal | Row deleted; active row (if any) unaffected; no content/history cascade (confirmed §2 above) | High |
| 5 | **Remove the active goal** | Point 5 says removing "only changes which goal drives nudges" — implying something *should* still drive nudges afterward if other goals exist. **Does removal auto-promote another adopted goal to active, or does the user end up with zero active goals?** `NoteCollectionService.reassertPrimaryInvariant` (`:1969-1987`) is a direct precedent for "auto-reassign when exactly one candidate remains, clear when none do" — recommend modeling this transition the same way, but this is a proposed default, not a code fact, and needs explicit owner sign-off. | **Owner decision needed — not resolvable from code or from the 5 points as written** |
| 6 | Set `focusSubjects` while ≥1 exam-goal row exists (any state) | Clears **all** `user_exam_goals` rows for that user, not just the active one (corrected above) | High, once the correction above is accepted |
| 7 | Clear everything (today's `handleClearStudyGoal`, `profile/page.tsx:735-749`) | Generalizes to: delete all `user_exam_goals` rows, null `studyGoal`, empty `focusSubjects` — a full reset, matching today's existing "Clear" semantics | High |
| 8 | Exam Hub intent, new user (not yet signed up) | No state change possible pre-signup; intent cookie stages the value for post-signup use exactly as today (§3) | High — unchanged by multi-goal |
| 9 | Exam Hub intent, existing user with other active exam goals | Point 4: add-and-activate per transition #2's open question; must **not** silently replace the others | Depends on #2's resolution |

**"Removing a goal must never delete adopted content or assessment history" — already satisfied by construction, zero new work needed.** Adopted content lives on `NoteCollectionEntity` rows (keyed by `sourcePlanId`/UUID), and assessment history on session/StudyPack entities — neither has any FK to `studyGoal`, an exam slug, or (would have none to) a future `user_exam_goals` row (confirmed by grep across repositories/entities). There is no FK path for a goal removal to cascade through, so this requirement needs no defensive code — it is already true of the schema shape.

**⚠️ The post-session next-step exception (owner point 2's carve-out) does NOT already exist — this needed checking, and the result is a real gap, not a formality.** `PostSessionNextStepService.resolveGoalNudge` (`:534-574`) reads `user.getStudyGoal()` directly (`:535`) and, when the just-completed note does **not** match that goal, unconditionally still nudges toward it (`:563`). In a single-goal world this is correct — there's nothing else to nudge toward. **In a multi-goal world, this is precisely backwards from what the owner asked for**: a session on a legitimate, adopted-but-not-active goal's material would get nudged toward the *active* goal instead of being left alone. Fixing this requires checking the note against the **full set** of adopted goals before deciding whether to fire a nudge for the active one — genuine new logic, not a field swap.

**A usable design precedent for exactly this shape already exists in the same file**, applied to a different feature: `resolveNextPlanItem` (`:316-356`) prefers the user's "primary" collection *only if* the just-completed note is actually in it, else falls back to whichever collection the note is actually in (`:329-331`). This is the right pattern — "prefer the active/primary signal, but never override what the session itself demonstrates" — just not yet applied to exam goals. Recommend reusing this pattern's shape, not its code (it operates on `NoteCollectionEntity`, not exam slugs).

**The dashboard-level nudge/banner suppression also needs logic change, not a swap.** `GoalPromptBanner` (`frontend/components/dashboard/goal-prompt-banner.tsx:50-55`) currently self-suppresses entirely if *any* `studyGoal` is set — under multi-goal this needs to mean "no *active* goal yet," while still being aware of other adopted-but-inactive goals so it doesn't re-suggest one the user already holds. `dashboard/page.tsx:441,761`'s gate has the same shape of change.

**Four of the roughly nine read-sites of `studyGoal` for dashboard/progress purposes are genuine one-line swaps** (`MeController.getProgress:64`, `getGoal:81,85,90`, `AuthService.toMeResponse:641`, `LinkedLearnerProgressService:49-53` needs no change at all — it already deliberately passes `null` for cross-user reads). The EXAM/SUBJECT type-resolution and course-program filtering logic inside `ProgressReportService` is goal-*value*-agnostic and would work unchanged fed whichever single value is "active" — this half of the system is close to trivial.

---

## 3. Onboarding semantics — two of three rules already hold today; one needs new disclosure UI; a naming collision risk was independently found by all three investigation passes

**Owner rule (c) — "signing up via an Exam Hub must not auto-adopt a Review Set" — already true today, confirmed at both layers, zero work needed.** The Exam Hub CTA (`frontend/components/exam-hub/exam-hub-cta.tsx:17-27,69-92`) only stages a 30-minute intent cookie (`frontend/lib/exam-intent.ts:1-10`) and redirects — it does not call `setStudyGoal` or any adoption endpoint. The actual `studyGoal` write happens later, on the dashboard, via `GoalPromptBanner`, and only on an explicit "Set as my goal" click (`:67-88`). Separately, the "Start this Review Set" adoption button (`PublicStudyPlanCard.handleStart`, `:125-166`) is a different component entirely, and `NoteCollectionService.adopt()`/`.adoptGoal()` (`:1090,1146`) contain zero reference to `studyGoal`/`AuthService`. **Goal-setting and content-adoption are already fully decoupled, at the UI, controller, and service layers.** This rule describes the status quo; it is not a change to make.

**Owner rule (a) — "add and activate, never silently replace other exam goals" — not yet applicable, because there is currently only ever one slot to replace.** Today, picking an exam goal via the Profile combobox (`handleSelectExamGoal`, `frontend/app/profile/page.tsx:751-765`) always overwrites the single `studyGoal` value — there is no "other goals" to preserve yet, so this isn't a bug to fix so much as new capability to build from zero. Confirmed via grep: **zero** existing scaffolding anywhere in the codebase for `activeGoal`/`primaryGoal`/`isActive*Goal`-shaped concepts.

**Owner rule (b) — "clear disclosure before the intent flow overwrites a SUBJECT focus" — does not exist today, in either relevant path, needs new UI, and this pass found a live, pre-existing defect while checking it that is independent of multi-goal entirely and arguably the most immediately actionable finding in this document.**

**⚠️ Confirmed live defect, verified directly against production (not inferred): `GoalPromptBanner` can already silently destroy a user's subject focus today, with zero disclosure, and it has already happened to at least one real account.**
- `GoalPromptBanner`'s render gate (`goal-prompt-banner.tsx:50-55`) checks only `studyGoal` — it does **not** check `focusSubjects` at all. Confirmed by reading the exact prop list the dashboard passes it (`dashboard/page.tsx:755-763`): `studyGoal`, `courseProgram`, `profileType` — no `focusSubjects` prop exists on this component at all.
- This means a user who has set a `focusSubjects` list (and has `studyGoal = NULL`, which today's exclusivity logic guarantees — §2) is shown the banner **as if they had no goal set**, because the banner's own idea of "has a goal" only looks at `studyGoal`.
- If that user clicks "Set as my goal," `handleSetGoal` calls `setStudyGoal(...)` → `AuthService.updateStudyGoal:606` → **silently clears `focusSubjects` to empty**, with no warning anywhere in that path that anything besides "setting a new goal" was about to happen.
- **Verified against production, 2026-10-12: 1 of 426 users currently has `focus_subjects` non-empty with `study_goal` null** — i.e., exactly the state that makes them eligible to see this banner and lose their subject focus on one click, today, independent of anything in the owner's multi-goal direction.
- **This should be read as a standing defect to fix regardless of the multi-goal decision in §7/§8** — it predates this feasibility question and isn't created by it, but this pass touches the exact same component and should either fix it in the same change or explicitly defer it with the owner's eyes open (§8).

- The Profile page's exam-goal combobox (`:1360-1373`) renders unconditionally alongside the subject-focus UI and writes `setStudyGoal(slug)` immediately on selection (`:751-765`) — **no confirmation step exists today, even for the current single-value case.** Same class of gap as the banner above, second site.
- **A reusable confirm-modal pattern already exists and fits this exactly:** `ProfileTypeSwitchModal` (`frontend/app/profile/page.tsx:147-199`), built on the generic `AppModal` primitive (`frontend/components/ui/app-modal.tsx`), already implements "stage a pending change, show a modal naming what will be affected, confirm/cancel before the real write." **No new UI primitive is needed** — only wiring this pattern onto both the banner and the exam-goal selection path.
- **Narrowed, not fully open: which field does "SUBJECT focus" in rule (b) most plausibly mean?** `docs/features/profile.md`'s own vocabulary ("Study Focus," "Focusing on: ...") is written entirely in terms of `focusSubjects` — the doc never uses "subject focus" to describe a free-text string living inside `studyGoal`. **Proposed default: rule (b) means `focusSubjects`**, consistent with both the doc's own terminology and the live defect just confirmed above (which is specifically a `focusSubjects`-loss scenario). This is offered as a concrete reading for the owner to confirm or correct in §8, not left as a fully open question.

**⚠️ Naming collision, found independently by all three parallel investigation passes — strong signal this is real and must be called out explicitly in any future spec.** `NoteCollectionService` has its own, **entirely unrelated** "goal" vocabulary: a `STUDY_GOAL_ADOPTED` analytics event (`AnalyticsEventType.java:27`), `trackStudyGoalAdopted` (`NoteCollectionService.java:1391,2990`), `sourceGoalId`/`personalGoalId`, and `UserEntity.primaryCollectionId` (`UserEntity.java:194`) with its own `reassertPrimaryInvariant` (`NoteCollectionService.java:1969-1987`). This is the **Study Plan/collection adoption** feature — "goal" here means "an adopted published Study Plan collection," keyed by UUID, and it already supports many-adopted/one-primary with an invariant-reassertion function. **Confirmed zero actual calls to `user.getStudyGoal()`/`setStudyGoal()` anywhere inside `NoteCollectionService.java`** — the shared vocabulary is coincidental, not a hidden coupling. Any future spec or Codex prompt must explicitly distinguish "exam goal" (`UserEntity.studyGoal`/the proposed join table) from "Study Plan goal adoption" (`NoteCollectionService`), or risk a reader — human or Codex — conflating two unrelated systems that happen to share a word.

**Interesting, unprompted finding worth the owner's attention:** that same `NoteCollectionService` "many-adopted/one-primary, with an invariant-reassertion function" shape is architecturally *closer* to what the owner's 5 points describe than anything in the current exam-goal system — it already solves "many things adopted, exactly one flagged primary, removing the primary one reassigns cleanly" for a different entity. It operates on `NoteCollectionEntity`, not exam slugs, so it cannot be reused directly, but its *shape* is a reasonable design precedent to point a future Codex prompt at.

---

## 4. Picker UI (Profile's Study Focus) — partially shipped, correctly identified as needing new work

Confirmed live and matching the discovery decision's description of Candidate B exactly: a constrained `SuggestionCombobox` (`allowCustom={false}`, `frontend/app/profile/page.tsx:1362-1372`), fed from `examGoalDefinitions`, with `Change`/`Clear` actions (`:1248-1257,1285-1294,767-772`). **`Clear` is a hard reset of both `studyGoal` and `focusSubjects` together** (`handleClearStudyGoal:735-749`) — there is no scoped "remove just one thing" today, consistent with there being only one thing to remove.

**Confirmed: zero existing scaffolding for add/remove/set-active anywhere in the codebase.** The owner's point 5 ("a real goal-management surface... not a single→multi-select conversion") is accurately describing new UI work, not a UI defect — there is nothing partially built to extend here beyond the single-value combobox shell and the `AppModal` confirmation primitive noted in §3.

---

## 5. Cost assessment

**Genuinely small / largely mechanical:**
- New `user_exam_goals` table + migration (lossless for 16/19 current goal-setters; 3 free-text rows explicitly left untouched per §1).
- The dashboard/progress read-path (`MeController`, `ProgressReportService`'s type-resolution and filtering) — close to a one-line swap once "the active goal" is defined, confirmed goal-value-agnostic.
- Analytics: **no backend schema change needed.** `AnalyticsEventEntity.metadataJson` is unvalidated JSONB and `AnalyticsEventRequest` already has an unused `entityId` field — adding an active/inactive signal to `STUDY_GOAL_SET`/`GOAL_NUDGE_SHOWN`/etc. is a frontend-only, additive change (confirmed, `AnalyticsService.trackEvent:43-60` does no key validation).
- Reusing `AppModal` for the disclosure step (§3) — no new UI primitive.

**Genuinely new logic, not swaps — the real cost center:**
- `PostSessionNextStepService.resolveGoalNudge`'s off-goal-nudge behavior (§2) — must check membership against the full adopted set, not just the active goal.
- `GoalPromptBanner`'s suppression condition and the Profile combobox's write-immediately behavior (§2, §3) — both need "is there an active goal" plus "what other goals exist" awareness they don't have today.
- The add/remove/set-active picker UI itself (§4) — genuinely new, no partial scaffolding exists.
- Introducing the shared EXAM-slug resolver (§1) — recommended regardless of scope, to close an existing drift risk.
- The disclosure modal's trigger wiring (§3) — mechanically simple once the "which field" ambiguity is resolved, but blocked on that owner answer first.

**Secondary, smaller items surfaced but not blocking:**
- `GoalNudgeResponse`/`GoalSummaryResponse`/`DataExportResponse` (GDPR export) all currently assume a single scalar `studyGoal` string — each needs its shape reconsidered for plural goals (`DataExportResponse.java:38`, `AccountDataExportService.java:83`).
- `AuthService.updateFocusSubjects` is not `@Transactional` while `updateStudyGoal` is (`AuthService.java:509` vs `:600`) — a pre-existing minor inconsistency on a write path this work will touch regardless; worth fixing in the same change, not separately.
- `GoalPromptBanner`'s dismissal state is component-local (`useState`, not persisted) — a pre-existing bug (the banner re-shows on reload) unrelated to multi-goal but adjacent, since this work touches the same component. Flagged as a bundle-or-defer decision, not a blocker.
- Two "is this note in this goal" predicates already disagree on normalization today (`ProgressReportService.filterGoalStudyPacks` normalizes course-program keys; `PostSessionNextStepService.isCurrentNoteInGoal` does a raw `Objects.equals`, `:576-582`) — pre-existing drift, not introduced by multi-goal, but multi-goal multiplies the surface where this could bite.
- ~10 existing test files pin today's single-goal behavior and would need updating (`UserProfileControllerTest`, `AuthServiceTest`, `ProgressReportServiceTest`, `PostSessionNextStepServiceTest`, and frontend equivalents for the Profile page, dashboard, goal banner/card/nudge-card).

**Nothing found increases LLM cost exposure.** This entire feature is profile-preference/presentation logic — no path touches generation, ConceptHealth, or any LLM call.

---

## 6. Draft acceptance criteria (for whenever a Codex prompt is eventually written — not proposed as final)

1. A real `PostgreSQL`-backed test (per `CLAUDE.md`'s own standing rule — not Mockito) seeding a user with 2 active exam goals, asserting: (a) the migration correctly carries over an existing single `studyGoal` as the sole active row; (b) adding a second exam goal does not deactivate or remove the first; (c) removing an exam-goal row does not touch any `NoteCollectionEntity`/StudyPack/session row for that user.
2. A test proving `PostSessionNextStepService` does **not** nudge toward the active goal when the just-completed session's material belongs to a different, adopted-but-inactive goal.
3. A test proving setting any active exam goal still clears `focusSubjects` (and vice versa) — the generalized exclusivity invariant, not a removed one.
4. A test proving the free-text SUBJECT path (3 current users) is untouched by the migration — `studyGoal` continues to serve them exactly as today.
5. A frontend test proving the Profile picker's add/remove/set-active actions never fire the underlying write without going through the confirmation step when an existing SUBJECT focus would be affected (once §3's "which field" ambiguity is resolved).
6. A test confirming the Exam Hub intent flow still never calls an adoption endpoint as a side effect of goal-setting (regression-proofing an already-true invariant, §3).
7. A `MockMvc`/request-shape test for whichever new endpoint(s) back add/remove/activate — per this repo's own `v0.119.0` lesson that a direct method-call test can pass while the real HTTP path 400s.

---

## 7. Recommendation

**Technical verdict: every one of the owner's 5 points is buildable, with no blocking contradiction found anywhere in the current code.** The storage migration is clean for the overwhelming majority of the existing population (16 of 19 goal-setters, trivially lossless); the one genuine ambiguity (3 free-text rows) is small enough that "leave untouched" is a reasonable default, pending owner sign-off. The exclusivity invariant generalizes naturally. The content/history-preservation requirement is already satisfied by construction. Two real gaps exist and need genuine new logic — the post-session nudge exception, and the disclosure-before-overwrite UI — but both have clean precedents already in the codebase (`resolveNextPlanItem`'s fallback pattern; the `AppModal`/`ProfileTypeSwitchModal` confirm-modal pattern) to build from, not from scratch.

**Evidence state: the scheduled gate (§0) has not cleared, and is not yet due.** The `[CHECKPOINT — due 2026-12-07]` population read remains the mechanism for deciding this, not this document. §0's number — 2 of 426 users — is not "past a handful" by any plain reading, and this document reports that plainly rather than filling the gap with intuition, matching the original decision file's own standard.

**On the explicit ask to weigh prioritization against the product-health release plan: this document's own 3-day-old framing of that plan's state has already gone stale, which is itself worth naming.** At the time this task was handed off, two of the plan's own release candidates had already moved: **RC-1 (the Study Pack quota fix) shipped as `v0.168.0`** (deployed 2026-10-09T14:41:25+08:00 — this is the date the plan's own 4-week post-fix baseline window should be measured from, not "in progress" as an earlier draft of this document said), and **RC-5 (Public Note → Study Journey discovery) was explicitly sequenced to `v0.170.0`** by `advisor()` during `v0.169.0`'s kickoff, per the stability-checkpoint reasoning this plan's own RC-5 spec already stated. **RC-0 (the Xendit checkout diagnosis)'s current status is not confirmed anywhere in `RELEASES.md` or `ROADMAP.md` as of this read** — this document does not know whether it has run, and says so rather than repeating a 3-day-old assumption that it's still "the single highest-value unclaimed action." **The honest statement here is: ask Release Implementor or the owner directly whether RC-0 has been actioned, rather than trust this document's own prioritization claim without a fresh check.** What can be said without a fresh check: at 2 of 426 users, this feature's population is comparably small to the product-health audit's own highest-priority findings (11 genuine checkout attempts, 0 completions, audit §11) — both are small-n, but one bears on whether the business generates any revenue at all, and the other refines an experience for 2 users. This is offered as a framing, not a ranking this document is positioned to finalize without that fresh check.

**This validation does not authorize a Codex prompt.** §1-§6 are ready to hand to one whenever the scheduled gate clears or the owner makes an explicit override — the same shape of decision already made once, for CE Exam Hub.

---

## 8. Owner decisions needed before any Codex prompt is written

1. **The 3 free-text `studyGoal` rows:** leave untouched (recommended, §1) or attempt a fuzzy match to a course program? If left untouched, confirm adding any active exam goal should clear the legacy value (§1's exclusivity-gap note).
2. **Confirm the proposed default reading of rule (b)'s "SUBJECT focus": `focusSubjects`, not the legacy free-text-in-`studyGoal` case** (§3) — this document proposes that reading from `profile.md`'s own vocabulary rather than leaving it fully open; the owner can confirm or correct it.
3. **The SUBJECT-clears-exam-goals asymmetry (§2):** setting `focusSubjects` should clear *all* `user_exam_goals` rows, not just the active one — confirm this reading, and decide whether that direction also needs a disclosure step (point 4 only requires one in the exam-intent-overwrites-subject direction; the reverse is currently unaddressed).
4. **Goal-removal auto-promotion (§2, transition #5):** when the active goal is removed, does another adopted goal auto-promote to active (this document's proposed default, modeled on `reassertPrimaryInvariant`), or does the user end up with zero active goals?
5. **Adding an additional goal (§2, transition #2):** does "add and activate" deactivate the previously-active goal (this document's proposed reading of point 1's "one goal flagged active," singular), or can more than one stay active at once?
6. **⚠️ `GoalPromptBanner`'s confirmed live defect (§3): fix now, as its own small unrelated change, or bundle into this work?** It predates multi-goal and affects at least 1 real user today (silently destroys `focusSubjects` on a single click with zero disclosure) — this document recommends not leaving it unaddressed either way, given it was found while validating this exact component.
7. **Introduce the shared EXAM-slug resolver** (§1) as part of this work, given it closes an existing, previously-incident-causing drift risk?
8. **`GoalPromptBanner`'s separate dismissal-persistence bug** (component-local state, re-shows on reload) — bundle into this work since it touches the same component, or explicitly defer as unrelated?
9. **Prioritization against the product-health release plan** (§7) — this needs a fresh status check (is RC-0 actioned?) before it can be answered meaningfully; not something this document can resolve on a 3-day-old read.

---

## Appendix: population query methodology

The 2-of-426 count (§0) was obtained by joining each user's adopted collections (`note_collections` with `source_plan_id IS NOT NULL`) one level up to their immediate source, then to `course_programs.exam_goal_slug` by name. **This is a one-level join, not the original decision file's full walk-to-root** (that file's own methodology note explains why a full walk was needed: most adopted child Subject Plans carry `courseProgram = NULL`, only root collections have it set). Despite the methodological difference, this pass's result (2 users) matches the decision file's own 2026-10-09 reading exactly — the two methods agree on this population, though a one-level join could in principle undercount a multi-level adoption hierarchy the original method wouldn't miss. Flagging this rather than asserting the two methods are equivalent in general.

```sql
-- Distinct exam-goal-slug count per user, one-level join (see caveat above)
WITH user_slugs AS (
  SELECT u.id AS user_id,
    array_agg(DISTINCT cp.exam_goal_slug) FILTER (WHERE cp.exam_goal_slug IS NOT NULL) AS slugs
  FROM users u
  JOIN note_collections nc ON nc.owner_user_id = u.id AND nc.source_plan_id IS NOT NULL
  JOIN note_collections root ON root.id = coalesce(nc.source_plan_id, nc.id)
  LEFT JOIN course_programs cp ON cp.name = root.course_program
  WHERE u.role = 'USER'
  GROUP BY u.id
)
SELECT array_length(slugs,1) AS distinct_slug_count, count(*) AS users
FROM user_slugs GROUP BY 1 ORDER BY 1 DESC;

-- studyGoal split: valid exam slug vs free text
SELECT
  count(*) FILTER (WHERE study_goal IS NOT NULL AND study_goal != '') AS total_with_goal,
  count(*) FILTER (WHERE lower(trim(study_goal)) IN ('ale','pnle','let','cpale','ce')) AS valid_exam_slug,
  count(*) FILTER (WHERE study_goal IS NOT NULL AND study_goal != ''
    AND lower(trim(study_goal)) NOT IN ('ale','pnle','let','cpale','ce')) AS free_text_subject
FROM users WHERE role='USER';

-- Live banner-defect exposure (§3)
SELECT count(*) FROM users WHERE role='USER'
  AND (study_goal IS NULL OR study_goal='') AND cardinality(focus_subjects) > 0;
```

All three are read-only `SELECT`s against `notelib-db-prod`, consistent with `CLAUDE.md`'s production-access rule.

---

*Inputs to this validation: `docs/claude-plans/study-journey-purpose-learner-goal-discovery-brief.md`, `docs/claude-plans/study-journey-purpose-learner-goal-discovery-decision.md`, `RELEASES.md`'s `v0.168.0` and `v0.169.0` entries, `docs/features/profile.md`, three parallel read-only code-mapping passes (storage/write-path; dashboard/nudge/progress read-path; onboarding/exam-hub-intent/Profile-UI), and four direct read-only production queries against `notelib-db-prod` (population count, exam-slug/free-text split, multi-adoption users' studyGoal values, banner-defect exposure). No code was written, no migration was run, no production data was changed.*
