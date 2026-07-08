# collections.md - NoteLib Feature Context

## Goal

Collections give any learner a saved, named, ordered grouping of their own existing notes.

A collection is the reusable container behind v0.27.0 organization workflows:

- a unit
- a study plan
- a review set
- a lesson-plan-shaped note playlist for future Teacher terminal actions

The backend entity is universal and profile-agnostic. It does not decide labels, CTAs, or profile-specific terminal actions.

## Core Model

A `NoteCollection` is a playlist over existing notes.

It is not:

- a new content type
- an AI-synthesized document
- a Study Pack
- a quota bucket
- a user- or teacher-shareable object in v1

Admin-published collections are the v0.31.0 exception: an admin can publish a collection as an adoptable study plan over already-public notes. Learners do not study the source plan directly; adopting creates a private snapshot copy in their own library.

**Study Plans vs saved library filters (do not consolidate).** A Study Plan is a *durable, ordered, named organizer* — the canonical way a learner groups notes by unit/grade level/preference. A saved library filter is a *transient quick lens* (a stored search/filter combo) over the whole library. They serve different jobs and both are intentionally kept: filters are how a learner narrows the library (including while assembling a plan from selection); the plan is the resulting durable grouping.

Fields:

- `id`
- `ownerUserId`
- `title`
- optional `description`
- `visibility` (`PRIVATE` by default, `PUBLIC` only for admin-published plans)
- optional `courseProgram`
- optional `estimatedStudyHours`, curator-entered study-time guidance shown by journey surfaces and copied on adopt
- optional `sourcePlanId` on adopted personal plans
- optional `parentCollectionId` for the v0.33.1 two-level Goal -> Subject hierarchy
- optional `siblingPosition`, used only to order child Subject plans under the same Goal
- ordered `items`
- `createdAt`
- `updatedAt`

Each item stores:

- `noteId`
- optional `label`
- `position`
- `createdAt`

Item labels are neutral backend data. The Study Plan detail page uses them as frontend-only section/module names, and the Teacher Exam Builder frontend uses them as initial section/week/topic names, but the backend does not interpret them.

For Study Plan detail sections, there is no separate section entity or nested-plan model:

- `position` is the single source of truth for item order.
- trimmed, non-empty `label` is the single source of truth for grouping.
- labels are user-defined free text, not course/program, subject, learner level, audience, or taxonomy data.
- a section is the set of items sharing the same case-sensitive trimmed non-empty label.
- section display order follows the minimum `position` among the section's items.
- items within a section stay in `position` order.
- null or empty labels belong to a trailing **Ungrouped** bucket.
- when no item in the plan has a label, detail renders the existing flat ordered list with no section cards.
- reordering (drag-and-drop and Move up/down) is visible only in organize mode and is **scoped to within a section**: each section has its own `SortableContext`, and both reorder paths operate against the grouped display order (sections contiguous) so a within-section move never renumbers another section's positions or reorders the sections. Cross-section drag is a no-op and Move buttons are disabled at section boundaries; to move a note to a different section, change its `label` (the Section control). The flat (no-label) plan reorders globally as before.
- the per-note **Section** control is visible only in organize mode. It uses the shared `SuggestionCombobox` (existing section names as suggestions + free-type a new one), and it **auto-saves on a short debounce** after the last change rather than on blur. Section headers are collapsible card headers, distinct from note titles.

Sections are strictly sections within one plan. They are not child collections, independent plans, or module entities.

**Nested collections reversal (v0.33.1, scoped).** The older "do not add parent/child collections, collection-of-collections, umbrella plans, or independently adoptable sub-plans" rule is deliberately reversed only for one level of Study Plan hierarchy: a top-level **Goal** collection can contain child **Subject** plans through `note_collections.parent_collection_id`. This is constrained to two collection levels (Goal -> Subject) and does not change section behavior. Recursive Goal adopt shipped in v0.33.3 for this two-level shape only. Deeper nesting, per-module mastery/readiness, arbitrary curriculum metadata, and direct note items on a Goal remain out of scope.

Hierarchy storage:

- `note_collections.parent_collection_id UUID NULL REFERENCES note_collections(id) ON DELETE SET NULL`
- `note_collections.sibling_position INTEGER NULL`, scoped only to sibling Subject plans under the same `parent_collection_id`
- indexed by `parent_collection_id`
- indexed by `(parent_collection_id, sibling_position)` for the builder / Goal child order
- `NULL` means top-level. A top-level collection with children is treated as a Goal by the frontend.
- a non-null parent means the collection is a child Subject plan and can still hold note items and label-derived sections.
- deleting a Goal leaves child Subject plans as standalone top-level plans (`ON DELETE SET NULL`), never cascade-deletes them.
- when a child is nested under a Goal, it receives the next `siblingPosition` after the current siblings; clearing its parent clears `siblingPosition`.

Hierarchy constraints:

- a child can be nested only under a parent collection owned by the same user
- self-parent is rejected
- parent must be top-level (`parentCollectionId == null`)
- child must have no children of its own
- the first implementation keeps Goals note-free: a collection must be empty before it can become a Goal, and a Goal cannot accept direct note items
- these rules enforce the maximum two levels and make cycles impossible

### Primary Review Set

`users.primary_collection_id` is a nullable user-level UUID reference to `note_collections.id`. It is stored as a bare UUID column, following the existing collection reference convention, and uses `ON DELETE SET NULL` at the database level like `note_collections.parent_collection_id`.

The reference is the backend source of truth for the learner's default top-level Goal. It remains profile-agnostic: the backend stores and validates the collection id only, while frontend labels still resolve through `getCollectionLabels`.

Rules:

- only an owned top-level Goal can be primary (`parentCollectionId == null`)
- setting a child Subject plan as primary returns `InvalidCollectionRequestException` / `400`
- setting a missing or not-owned collection returns `CollectionNotFoundException` / `404`
- setting the already-primary collection is a no-op success
- clearing primary is always allowed and is a no-op when nothing is set
- when a user has no primary and exactly one owned top-level Goal, that Goal is auto-set as primary
- auto-set applies after top-level Goal creation, standalone public-plan adoption, first-time Goal adoption, deletion, and re-parenting changes that leave exactly one top-level Goal
- an existing valid primary is never overwritten by auto-set
- if the current primary stops being an owned top-level Goal, the reference clears before the exactly-one-top-level auto-set rule is considered
- adopting child Subject plans inside `adoptGoal` does not run primary auto-set for those temporary standalone child copies; the invariant is reasserted once after the first-time Goal adoption is structurally settled

Structural collection mutations reassert this invariant in the same service operation that changes the top-level set:

| Mutation | Primary behavior |
|---|---|
| `POST /collections` | Can auto-set the created collection when it is the user's first top-level Goal. |
| `POST /collections/{id}/adopt` | Can auto-set the adopted standalone plan when it is the user's first top-level collection. |
| `POST /collections/{id}/adopt-goal` | Can auto-set the adopted Goal on genuine first-time adoption; repeat `alreadyAdopted=true` calls do not alter primary. |
| `DELETE /collections/{id}` | If the deleted collection was top-level, clears an invalid primary and can auto-set the one remaining top-level Goal. |
| `PATCH /collections/{id}/parent` | Attaching a primary Goal under another Goal invalidates it; detaching a child back to top-level can trigger auto-set. |

**Frontend consumption (Dashboard + Review Sets list page):** `DashboardStudyPlanSection` (`frontend/app/dashboard/dashboard-study-plan-section.tsx`) accepts an optional `primaryCollectionId` prop. When set, it looks the id up via `listCollections()` and, if found, renders that owned collection instead of the course/program-matched public recommendation — skipping the adopt flow entirely (the CTA always resolves to the existing "already owned" render path, i.e. `router.push` straight to `/collections/{id}`, never `adoptGoal`/`adoptStudyPlan`). The heading swaps from `Recommended {labels.singular}` to `labels.primarySingular` (a new field on `CollectionLabels` in `frontend/lib/collection-labels.ts`: "Primary Study Plan" / "Primary Review Set" / "Primary Lesson Plan" / "Primary Collection", resolved the same profile-aware way as every other collection label), the course/program subtitle and the "See all N" link are both suppressed (neither applies once the plan is an explicit choice rather than a recommendation). If the id isn't found among the user's owned collections (a defensive fallback — the backend invariant should keep this reference valid), rendering falls through to the existing course/program-matched flow, same as if no primary were set at all. Wired into both Dashboard call sites (`STUDENT`/`PROFESSIONAL` and `BOARD_EXAM`) and the `/collections` list page (`frontend/app/collections/collections-page-client.tsx` — the `getMe()` call already fetching `courseProgram` there was renamed `loadProfile` and extended to also read `primaryCollectionId`). The onboarding call site of the same component does not pass this prop — a new user going through onboarding has no owned collections yet, so there's nothing to be primary. The Review Sets list page's v0.40.1 Browse All section and corrected empty-state CTA are documented below in "Browse published plans".

The CTA verb (`Start`/`Continue this {label}`) and the child-count line (`N {label}s`) in `DashboardStudyPlanSection` and `PublicStudyPlanCard` (`frontend/components/study-plan/public-study-plan-card.tsx`) resolve through `labels.goalSingular`/`labels.singular`/`labels.subjectSingular`, same as `primarySingular` above — a hardcoded "Goal"/"Subject plan(s)" wording briefly shipped in these two components (the Goal detail page always did this correctly) and was fixed as part of the v0.40.0 pre-signoff pass. `PublicStudyPlanCard` takes an optional `profileType` prop for this purpose; `published-plans-page-client.tsx` passes the same `profileType` it already resolves for its own page-level labels.

**Frontend consumption (Progress):** `/progress` also reads `primaryCollectionId` from `GET /auth/me`. When the URL has no explicit `?collectionId=`, it applies the Primary Review Set once as the initial scoped readiness view; explicit query-param selections and later `All subjects` picker changes win after that first resolution. Because a primary is always a top-level Goal, Progress uses `GET /collections/{id}/goal` for that default aggregate view, not `GET /collections/{id}/readiness` (which reads only direct note items and is reserved for explicit leaf/Subject plan selections).

### Target Completion Date

`note_collections.target_completion_date` is a nullable `LocalDate` on top-level Goals only (`parentCollectionId == null`) — the optional deadline a learner is aiming for, feeding the v0.40.0 weekly countdown derivation (see below). Deliberately decoupled from `UserEntity.examDate` (the existing profile-level board-exam date, Dashboard-only, unrelated and untouched).

Set/clear split into two different mechanisms, because a nullable `LocalDate` field has no empty-string-style sentinel to distinguish "omit" from "explicit clear" the way text fields do:

- **Set** goes through the general metadata PATCH: `PATCH /collections/{id}` (`updateMetadata`) accepts `targetCompletionDate` with the same omit-preserves semantics as `courseProgram`/`estimatedStudyHours` — omitting it in the request body leaves the existing value untouched.
- **Clear** is a dedicated endpoint: `DELETE /collections/{id}/target-date`, mirroring the `DELETE /collections/{id}/primary` shape. No-op success if nothing is set or if the target is a child Subject plan (which can never have a date to begin with).

Rules:

- setting a target date on a child Subject plan via `updateMetadata` returns `InvalidCollectionRequestException` / `400` — Goal-only, same category as the Primary Review Set top-level-only rule
- `DELETE /collections/{id}/target-date` on a missing/not-owned collection returns `CollectionNotFoundException` / `404`
- **never copied on adopt or self-copy** — `adopt`/`adoptGoal` (including the case where a user adopts their own PUBLIC collection) never carry a source's `targetCompletionDate` onto the created copy; the field simply isn't set on the new entity and defaults to null. No auto-guessed default is generated either — null stays null until the learner deliberately sets one.
- **cleared on reparent** — `updateParent` (`PATCH /collections/{id}/parent`) clears `targetCompletionDate` whenever a top-level Goal is nested under a new parent (becoming a child), in the same branch that sets `parentCollectionId`. Without this, a dated Goal that gets nested and later detached back to top-level (`updateParent(null)`) would resurface its stale date instead of starting fresh — the same category of invariant `reassertPrimaryInvariant` already enforces for the Primary Review Set on this same method, just for the target-date field instead.
- exposed on both `NoteCollectionDetailResponse` (the `updateMetadata`/`create` response) and `GoalCollectionDetailResponse` (the `GET /collections/{id}/goal` response the weekly countdown derivation reads from — see below) — these are two separate DTOs, not one superset of the other.

Post-adopt guidance:

- recursive Goal adoption from Dashboard and public plan cards writes a session-scoped just-adopted flag keyed by the new personal Goal id
- the Goal detail page reads and clears that flag once; if the Goal has no `targetCompletionDate`, it shows the shared `GuidanceTip` with a `Set target date` action
- the action opens the existing edit modal, where target date and study intensity are already edited
- the tip never appears for leaf-plan adoption, normal non-post-adopt visits, child Subject plans, or Goals that already have a target date
- dismissal is permanent through the same localStorage-backed `GuidanceTip` behavior used elsewhere; no new nudge mechanism is introduced

### Weekly Countdown Derivation

`GET /collections/{id}/goal` (`getGoal`) computes three additional nullable fields on `GoalCollectionDetailResponse` — `weeksRemaining`, `conceptsRemaining`, `todaysConceptBudget` — from `targetCompletionDate`, `users.study_days_per_week`, and the existing readiness rollup (`totalConcepts`, `masteredConcepts`, `dueConcepts`, `notPracticedConcepts`). Pure derivation, computed fresh on every request — no stored per-week schedule entity, no new mastery signal, no AI call. Phase 2 layers two additional derived fields onto this same response: nullable per-child `GoalCollectionChildResponse.todaysConceptBudget` values and non-null `weeklyFocusByDay`.

All three fields are `null` when `targetCompletionDate` is null — same degrade-gracefully rule as the rest of the Primary Review Set / target-date surface.

Formula (`NoteCollectionService.computeWeeklyCountdown`):

- `conceptsRemaining = totalConcepts − masteredConcepts`
- `remainingDays = max(0, days between today and targetCompletionDate)` — floored at 0 for an overdue target date, never negative
- `remainingScheduledDays = max(1, round(remainingDays × studyDaysPerWeek ÷ 7))` — floored at 1 so an overdue or same-day target never divides by zero; the practical effect is "cram everything today"
- `weeksRemaining = ceil(remainingDays ÷ 7)`
- `todaysConceptBudget = dueConcepts + ceil(notPracticedConcepts ÷ remainingScheduledDays)` — due concepts are always included as a floor (they're time-sensitive spaced-repetition reviews, not subject to pacing); new-concept learning is paced by spreading only the not-yet-started pool evenly across the remaining scheduled days

**When `users.study_days_per_week` is null** (learner skipped the intensity question), the derivation defaults to 7 (every day) for the math only — nothing is persisted, it's a conservative fallback so the countdown still renders rather than hiding whenever intensity alone is unset. Only a missing `targetCompletionDate` hides the countdown; a missing `studyDaysPerWeek` does not.

Due-concept count can *rise* over time even for a diligent learner — it tracks spaced-repetition review timing, not inactivity. This is correct behavior, not a scheduler bug.

Phase 2 subject allocation:

- `GoalCollectionChildResponse.todaysConceptBudget` is `null` whenever the Goal has no `targetCompletionDate`; otherwise it is `child.dueConcepts + allocatedNewConcepts`.
- The allocated new-concept pool is the exact `newConceptsToday` value used by the Goal-level `todaysConceptBudget` formula above, not a separate recomputation.
- New concepts are split across child Subject plans using largest-remainder (Hamilton's) method weighted by each child's `notPracticedConcepts`: floor every exact proportional share, then distribute leftover units by largest fractional remainder.
- Remainder ties break by the existing child display order (`siblingPosition` / the order already returned in `children`), so identical inputs produce stable output.
- The sum of all non-null child `todaysConceptBudget` values equals the Goal-level `todaysConceptBudget` exactly.

Phase 2 weekday focus:

- `weeklyFocusByDay` is a non-null list of `{ dayOfWeek, collectionIds }` entries. It is empty when the Goal has no `targetCompletionDate` or no child Subject plans.
- `studyDaysPerWeek` remains a count, not a stored weekday preference. The derived weekly template is evenly spaced from Monday: for each position `i` from `0` to `count - 1`, `dayOfWeek = DayOfWeek.of(1 + (i * 7) / count)`.
- Child Subject plan ids are assigned round-robin across those derived study days in the existing child display order. When there are more children than study days, multiple child ids share a day. When there are fewer children than study days, only days that receive at least one child id are returned.
- Entries carry child collection ids only; titles and labels are already present in `GoalCollectionDetailResponse.children`.

### Target Date + Study Intensity Input (frontend)

`EditCollectionModal` (`frontend/app/collections/[id]/collection-detail-page-client.tsx`) is the single edit surface reused for both top-level Goals and child Subject plans. It derives `isTopLevelGoal = collection.parentCollectionId === null` (no separate prop needed — `collection` is already passed in full) and shows the target-date and study-intensity fields **only** when `isTopLevelGoal` is true, per the locked Goal-only rule. The Create Collection modal (`frontend/app/collections/collections-page-client.tsx`) does not have these fields yet — it also lacks `estimatedStudyHours`, so this is consistent with that surface's existing minimal-fields precedent, not a gap introduced here.

- **Target date** reads/writes through `collection.targetCompletionDate`. On save: a non-empty value is included in the same `updateCollection` PATCH call as title/description/estimated hours (omit-preserves semantics); an emptied *previously-set* date instead calls the dedicated `clearCollectionTargetDate` (`DELETE /collections/{id}/target-date`) — omitting the field from the PATCH would leave the old date untouched, not clear it, since `LocalDate` has no PATCH-clear sentinel. An empty field that was already empty triggers neither call.
- **Study intensity** (`studyDaysPerWeek`) is a **user-level** attribute, not a collection field, so it isn't part of `collection` — it's asked on this same screen per the locked UX decision ("one screen, sane default if skipped") but sourced and saved separately via `getMe()` (prefill, fetched only when `isTopLevelGoal`) and `updateStudyDaysPerWeek` (`PUT /users/profile/study-days-per-week`, full-replace). The modal tracks the *baseline* value actually returned by that `getMe()` prefill separately from the field's live contents, and only calls `updateStudyDaysPerWeek` when the baseline resolved **and** the trimmed value differs from it — never while the prefill is still in flight or failed. This is a deliberate fix for a save-before-prefill race (and a `getMe()` failure) that could otherwise silently send `null` and wipe a learner's real intensity before the async prefill ever landed; it is not a fire-and-forget full-replace on every save. Client-side validates 1-7 before submit, mirroring the backend's `@Min`/`@Max`.
- The `<form>` uses `noValidate` — all validation (title-required, 1-7 intensity range) is custom JS, not native HTML5 constraint validation, so error messages are consistent and don't silently block submission before the app's own error UI can render.
- Saving updates `collection` state in full (via `setCollection(saved)` in both call sites), so the modal's next open reflects the latest persisted target date. On the Goal-branch call site, saving also refetches `getCollectionGoal` (see "This Week" section below) so the weekly countdown reflects an edited target date immediately, not just on next page load.

### This Week Section (frontend)

`GoalWeeklyCountdownCard` (`frontend/app/collections/[id]/collection-detail-page-client.tsx`) renders above the existing `ReadinessSummary` inside `GoalDetailView`, showing `weeksRemaining`, `conceptsRemaining`, and `todaysConceptBudget` from `GoalCollectionDetailResponse`. Returns `null` (renders nothing) when `targetCompletionDate` or any of the three derived fields is `null` — same degrade-gracefully contract as the backend derivation itself.

- Date display uses a `formatLocalDate` helper that splits the `LocalDate` string and constructs via the local-time `Date(year, month, day)` constructor rather than `new Date(isoDate)` — the latter parses a date-only string as UTC midnight, which can silently shift a day backward once formatted in a timezone behind UTC (e.g. US timezones). This is a real, easy-to-reintroduce bug if "simplified" later; verified explicitly under `TZ=America/Los_Angeles` in tests.
- **Childless top-level ("leaf") collections also show this card (fixed in v0.40.1).** `goalDetail` is now fetched whenever `parentCollectionId === null` (any top-level collection), not only when `childCount > 0` — a flat, childless top-level Study Plan/Review Set can still carry a target date. A derived `isGoalView = Boolean(goalDetail) && childCount > 0` is the single source of truth for which view renders (Goal view with the children list, vs. the leaf view with Build/notes list); it also gates the two leaf-only readiness effects (`sectionCounts`/`planReadiness`) and the post-adopt guidance tip (which stays Goal-adoption-specific, since `adoptGoal` always produces children — leaf-plan adoption still never shows it). The leaf-view branch renders `GoalWeeklyCountdownCard` directly (reading `goalDetail`'s countdown fields, or `null` when no top-level `goalDetail` exists) above its own `ReadinessSummary`, self-guarding to nothing when no target date is set — same contract as the Goal view.
- **Refetch on edit:** because `weeksRemaining`/`conceptsRemaining`/`todaysConceptBudget` only exist on `GoalCollectionDetailResponse` (not on `NoteCollectionDetail`, which is all `updateCollection`/`clearCollectionTargetDate` return), a client-side field copy after editing the target date cannot refresh these three fields. Both `EditCollectionModal`'s `onSaved` call sites (Goal branch and leaf branch, the latter only when `parentCollectionId === null`) call `getCollectionGoal(collectionId)` again and replace `goalDetail` wholesale; if that refetch fails, it falls back to the previous partial-merge behavior (now including `targetCompletionDate`) so the rest of the page doesn't break, at the cost of a stale countdown until the next successful load.

### Builder Canvas

The builder route is `/collections/{id}/builder`. It first loads the base collection through `GET /collections/{id}`:

- if `childCount > 0`, it renders the Goal builder canvas.
- if `childCount == 0`, it renders the leaf-plan builder canvas for that one collection's notes and sections.

An empty **top-level** collection (`parentCollectionId === null`) with no children and no note items is still undecided. The leaf builder empty state offers both:

- `Add notes` to commit the collection as a flat leaf plan.
- `Add {subjectSingular}` to create a child collection and nest it under the current collection, turning it into a Goal through the existing parent API.

Once the collection has at least one note item, the Goal-building option is hidden because backend hierarchy rules reject nesting under a parent that already has notes. It is also hidden for a collection that is itself already nested under a Goal (`parentCollectionId != null`), even with zero notes — a nested Subject plan can never become a parent itself (backend rejects nesting under a non-top-level collection), so `Add {subjectSingular}` never renders there regardless of note count (v0.36.1 fix).

The v0.33.1 Goal builder turns hierarchy curation into one canvas:

- Goal = canvas.
- Subject plans = draggable, collapsible section blocks.
- Notes = cards inside each Subject.

The Goal path loads the authoritative Goal shape from `GET /collections/{id}/goal`, then loads each child Subject's notes through the existing collection detail endpoint. Refreshing the page reconstructs the same structure from backend state; no client-only builder state is required for persistence.

The builder page header (leaf and Goal) has a single primary action, `Add {subjectSingular}`; there is no standalone header `Refresh`. A `Refresh` control lives inside the `Add notes` modal instead (next to the search field), scoped to re-fetching just the note list so newly created notes appear as selectable — it does not refetch the collection/goal shape and does not affect the modal's current selection (v0.36.1). The Subject-block row itself still switches from stacked to horizontal layout at the `xl` breakpoint rather than `lg`, because the persistent app-shell sidebar (present from `md` up) consumes real width the viewport-relative breakpoint doesn't otherwise account for (v0.36.1).

The `xl` breakpoint change alone didn't fix wrapping on tablet widths (a real gap the user caught via iPad Air/iPad Mini screenshots): each Subject block and note row's action buttons had no responsive treatment of their own, so at some intermediate viewport widths a button would wrap alone onto its own row. The fix is structural, not another breakpoint: `Move up`/`Move down` (Subject blocks) and `Up`/`Down` (note rows) are no longer text buttons in the action row — they're a compact icon-only cluster (`ArrowUp`/`ArrowDown`) rendered next to the drag handle instead, since reorder-by-button is functionally redundant with drag-and-drop and doesn't need equal visual weight to the content actions. Each action row is now fixed at exactly two items (`Add notes` + `Delete` for Subject blocks; `Move` + `Remove` for note rows) and never wraps at any viewport width (v0.36.1).

The builder deliberately orchestrates existing collection endpoints:

- add Subject plan = `POST /collections` to create an empty collection, then `PATCH /collections/{childId}/parent` to nest it under the Goal
- rename Subject = `PATCH /collections/{childId}`
- delete Subject = `DELETE /collections/{childId}`; notes are never deleted
- add notes to a Subject = `POST /collections/{subjectId}/items`
- reorder notes inside a Subject = `PUT /collections/{subjectId}/items/order`
- move a note across Subjects = `DELETE /collections/{sourceSubjectId}/items/{noteId}` then `POST /collections/{targetSubjectId}/items`, followed by order save when needed

The only new backend capability for the builder is sibling ordering for child Subject plans:

- `PUT /collections/{id}/children/order`
- request body: `{ "childIds": ["..."] }`
- owner-scoped and transactional
- the submitted ids must include exactly the current children of the Goal and every child must be owned by the caller
- the service rewrites `siblingPosition` from `0..N-1`
- `GET /collections/{id}/goal` returns children by `siblingPosition asc`, null positions last, with `updatedAt desc` as fallback

Modules remain the existing per-note `label` / Section field inside a child Subject plan. The builder does not add a third drag level for modules, does not add module readiness, and does not reinterpret labels as hierarchy.

The v0.34.0 leaf-plan builder uses the same route for childless collections:

- sections are the existing item `label` values, with `null` / blank labels grouped as **Ungrouped**.
- notes are draggable cards inside section zones.
- moving a note to another section changes only its label.
- reordering and relabeling persist through `PUT /collections/{id}/items/order` with the full item payload.
- removing a note uses `DELETE /collections/{id}/items/{noteId}`.
- there is no Add notes flow in the builder; users add notes from the plan detail page.
- no new backend API, section entity, module entity, hierarchy level, readiness field, or AI call is introduced.

## Profile-Aware Terminal Actions

The backend API must not branch on `ProfileType`.

Profile-aware presentation is a frontend responsibility. The backend responses stay neutral: `title`, `description`, `items`.

| Profile | Frontend label | Primary terminal action |
|---|---|---|
| `TEACHER` | `Lesson Plan` | `Build Exam` → combined sectioned DOCX + shareable quiz links through Exam Builder |
| `STUDENT` | `Study Plan` | `Take the Long Exam` → Long Exam setup |
| `BOARD_EXAM` | `Review Set` | `Take the Board Exam` → Board Exam setup |
| `PROFESSIONAL` | `Collection` | `Start Interview Practice` → Interview Practice setup |
| `PARENT` | `Collection` | No terminal action |

The non-teacher premium-exam mapping is owned by `resolvePlanPremiumExamMode` in `frontend/lib/exam-mode-visibility.ts`, and the profile-aware CTA labels live in `getCollectionTerminalAction`. Do not hardcode profile checks in collection UI components.

Premium-exam eligibility differs from the Teacher Exam Builder: Long/Board/Interview generate their own questions at start, so a note only needs a **ready Study Pack** (`canIncludeCollectionItemInPremiumExam` = `STUDY_PACK_READY`) — a pre-generated quiz is **not** required. The Teacher Exam Builder still requires a generated quiz (`canIncludeCollectionItemInExam` = `generatedQuizId`) because it exports that quiz.

The Study Plan premium-exam launch carries `collectionId` in the URL, not a caller-provided note list. Each exam prescreen fetches the collection, intersects its Study Pack-ready items with the user's Study Pack-ready notes, scopes the additional-notes picker to that plan set, and pre-selects up to the existing per-exam cap:

- Long Exam: primary note route plus up to 3 additional Study Pack ids
- Board Exam: primary Study Pack route plus up to 2 additional Study Pack ids
- Interview Practice: primary note route plus up to 2 additional note ids

If the collection cannot be loaded from a prescreen, the exam falls back to its normal single-note/same-subject setup. The Teacher Exam Builder path is unchanged and still receives the collection id plus quiz-ready note ids.

On a plan launch (`collectionId` present) the prescreen back link returns to the originating plan (`/collections/{collectionId}`) using the profile-aware label from `getCollectionLabels` (`Study Plan` / `Review Set` / `Collection`) rather than "← Note", and the additional-notes picker reads "Add up to N more notes from this plan" (the primary note stays implicit as "Built from …"; the footer total confirms all plan notes are included). The "Choose another mode" button is also hidden on a plan launch — there is no mode-selection grid to return to in that flow, and the back link already routes to the plan. Without `collectionId`, the back link, picker copy, and "Choose another mode" button are unchanged.

Before launching, if one or more exam-eligible (Study Pack-ready) plan notes have not been practiced (`lastSessionCompletedAt === null`), the premium-exam CTA surfaces a soft advisory modal ("Review before the exam?") with `Review first` (stay on the plan) and `Start the exam anyway` (proceed). It is a recommendation, never a block, and routes straight through when every eligible note is already practiced. There is no persistence — it re-evaluates on each launch. The Teacher Exam Builder CTA is unaffected.

## Ownership Rules

Collections remain owner-private by default.

- A collection can contain only notes owned by the requesting user.
- Adding or ordering a note that does not exist or is not owned by the caller returns `NoteNotFoundException` / `404`.
- A note may belong to multiple collections.
- A note may appear at most once in a single collection.
- Adding an already-present note is idempotent and silently skipped.
- Deleting a collection deletes only the collection and item rows.
- Deleting a note removes that note's collection item rows through the `note_collection_items.note_id` FK cascade.
- Deleting a collection must never delete notes.
- Existing owner-scoped endpoints must use `findByIdAndOwnerUserId` semantics and must not expose private collections to other users.

Admin-published plans intentionally lift the read boundary only through public endpoints:

- `visibility=PUBLIC` root collections are world-readable through `/collections/public`; child Subject plans stay reachable through their parent Goal/detail flows, not as standalone public-list cards.
- `visibility=PRIVATE` collections are never returned by public endpoints and return `404` on public detail.
- Publishing a leaf Subject plan is admin-only and requires a non-empty collection where every item note is already `PUBLIC`.
- Publishing a Goal is admin-only, requires at least one child Subject plan, validates every child Subject is non-empty, validates every child item note is already `PUBLIC`, and then cascades `visibility=PUBLIC` to the child Subject plans.
- Unpublishing returns only the selected source collection to `PRIVATE`; it does not cascade to children, and adopted personal plans are unaffected.
- User/teacher-authored collection sharing remains deferred.

## Generation And Quota Rules

Collections add no AI behavior.

- No collection-level Study Pack generation.
- No collection-level quiz generation.
- No LLM call is made by collection CRUD.
- No new usage or quota category exists for collections.
- Existing Study Pack and quiz generation stay per-note and keep their existing quota rules.

## API Surface

All endpoints are authenticated and available to `USER` and `ADMIN` roles.

Base path: `/collections`

### List Collections

`GET /collections`

Returns lightweight summaries ordered by `updatedAt desc`. The owned list returns top-level collections only (`parentCollectionId == null`); nested Subject plans are reached from their Goal detail page.

Response item:

- `id`
- `title`
- `description`
- `visibility`
- `courseProgram`
- `sourcePlanId`
- `parentCollectionId`
- `itemCount`
- `childCount`
- `notesPracticed`
- `createdAt`
- `updatedAt`

`childCount` is included so a top-level Goal card can show how many child Subject plans it contains. `notesPracticed` is included so the owned `/collections` list can show a lightweight execution-status badge for childless leaf plans without opening every plan. It is derived from the same practice definition as the detail rollup: a note counts as practiced when its latest completed quiz-session timestamp resolves to non-null (`lastSessionCompletedAt != null`). `itemCount` remains the total-note count; do not add a redundant `totalNotes` field to the summary DTO.

Owned-list status labels are frontend-derived from `notesPracticed` and `itemCount`:

- `Not started` — `notesPracticed == 0`, including empty plans where `itemCount == 0`
- `In progress` — `0 < notesPracticed < itemCount`
- `Completed` — `itemCount > 0 && notesPracticed >= itemCount`

This is execution status only: it answers whether the learner has practiced the plan's notes. It is not ConceptHealth mastery and must not add percentages, milestones, streaks, weakest-subject routing, or progress bars to collection list cards. Goal list cards may show `childCount` ("N plans") but not readiness percentages. Mastery remains owned by My Progress. The status badge is shown only on childless collections in the authenticated user's owned `/collections` list and is not shown on `/collections/published` or public study-plan cards, where viewer-specific practice status has no meaning.

### Create Collection

`POST /collections`

Request:

- `title` required, trimmed, max `150`
- `description` optional
- `noteIds` optional list of existing owned note IDs

Behavior:

- validates all note IDs before writing
- dedupes repeated note IDs while preserving request order
- appends items starting at position `0`
- returns full detail with ordered items

### Get Collection

`GET /collections/{id}`

Returns full detail with ordered items.

Item response is intentionally lean and private-owner focused:

- `noteId`
- `label`
- `position`
- `title`
- `subject`
- `courseProgram`
- `studyPackStatus`
- `generatedQuizId`
- `lastSessionCompletedAt`
- `dueConceptCount`
- `dueConcepts` (up to 3 ordered names for display)
- `updatedAt`

`studyPackStatus` uses the same note readiness rule as the Note API:

- note `GENERATED` -> `STUDY_PACK_READY`
- note `GENERATING` -> `GENERATING`
- note `FAILED` -> `FAILED`
- no linked Study Pack -> `DRAFT`
- linked Study Pack otherwise -> `STUDY_PACK_READY`

The detail response also includes a read-only `progress` summary:

- `totalNotes` — number of notes in the collection
- `notesWithStudyPack` — items whose resolved `studyPackStatus` is `STUDY_PACK_READY`
- `notesPracticed` — items whose `lastSessionCompletedAt` is not null

`lastSessionCompletedAt` uses the same batched per-note completed-session source as the private Library note list. It covers completed supported quiz modes, including participating notes from multi-note sessions, without issuing one query per collection item. If session history cannot be resolved, item timestamps degrade to null and the notes count as not practiced rather than failing the collection response.

The detail progress rollup is computed only for the collection detail response from the item data already assembled for that request. Collection list cards stay lightweight: they receive only `itemCount` plus the summary `notesPracticed` execution count and derive the three-label badge client-side.

The rollup is profile-agnostic and presentation-neutral. Frontend profile labels still come only from `getCollectionLabels`; the backend returns the same counts for Study Plans, Review Sets, Lesson Plans, and Collections. It adds no persisted progress field, generated content, AI call, or quota category.

Collection detail items also expose a read-only weak-area signal from the existing `ConceptHealthService` due-concept model:

- `dueConceptCount` is the full number of due key concepts for the note's Study Pack.
- `dueConcepts` contains the first 3 concepts in the existing deterministic due order.
- Concept health is loaded once for all Study Packs in the collection; the read path must not issue one query per item.
- The signal is populated only when the user has a Plus or Pro plan and the existing `Feature.ADAPTIVE_QUIZ` entitlement is available, matching the Note Detail concept-health surface.
- Free users and notes without a Study Pack receive `0` and an empty list. Lookup failures also degrade to empty weak-area data without failing collection detail.
- The backend remains profile-agnostic and does not branch on `ProfileType`.

The detail response also exposes neutral hierarchy metadata:

- `parentCollectionId`
- `childCount`

The frontend uses `childCount > 0` to render the Goal view. Childless plans render the existing flat detail unchanged.

The detail response includes optional `estimatedStudyHours` as metadata only. It is nullable, never required, and does not affect adopt, publish, quiz, readiness, quota, or generation behavior.

### Get Collection Readiness

`GET /collections/{id}/readiness`

Returns owner-scoped readiness for the authenticated user's own collection only. Missing, malformed, or not-owned ids return `CollectionNotFoundException` / `404`; public source plans are not served to non-owners through this endpoint.

Response:

- `collectionId`
- `totalNotes`
- `notesWithStudyPack`
- `overallReadinessPercentage`
- `totalConcepts`
- `masteredConcepts`
- `dueConcepts`
- `notPracticedConcepts`
- `subjects: SubjectProgressEntry[]`

Aggregation rules:

- Start from the collection's ordered notes, then load their owned Study Packs.
- Notes without a Study Pack count toward `totalNotes` only.
- `notesWithStudyPack` counts notes in the plan that have an owned Study Pack.
- Only Study Packs with key concepts contribute concepts.
- Subjects use the Study Pack subject; null or blank subjects group under `Other`.
- Per-subject entries and overall counts reuse `ProgressReportService` concept classification and `masteryPercentage`, so plan readiness matches `/me/progress` for the same concept set.
- `overallReadinessPercentage = round(masteredConcepts * 100 / totalConcepts)`, or `0` when `totalConcepts == 0`.
- Empty plans, plans with notes but no Study Packs, and never-practiced Study Packs are valid `200` responses, not errors.
- No new persisted readiness field, generated content, quota category, AI call, trend, snapshot, or batch/progress infrastructure is added.

This is the deliberate v0.33.0 reversal of the older "Study Plans do not duplicate Progress" rule, scoped to the dedicated readiness detail route only. Collection detail execution rows, collection list cards, published-plan cards, and public source plans must still not show subject mastery percentages, milestones, goals, streaks, or weakest-subject routing.

### Get Note Concept Counts

`GET /collections/{id}/note-concept-counts`

Returns owner-scoped per-note readiness counts for the authenticated user's own collection only. Missing, malformed, public-source, or not-owned ids return `CollectionNotFoundException` / `404`.

Response shape:

- map key: `noteId` as a string
- map value:
  - `totalConceptCount`
  - `masteredConceptCount`
  - `dueConceptCount`
  - `notPracticedConceptCount`

Aggregation rules:

- Starts from the collection's note items and loads Study Packs for those notes.
- Notes without Study Packs are omitted from the map.
- Study Packs with zero key concepts return entries with all counts at `0`.
- Empty collections and collections with no Study Packs return `{}`.
- Counts reuse `ProgressReportService` concept classification and the existing `ConceptHealth` model.
- Concept health is loaded in one batch via `findByUserIdAndStudyPackIdIn`; the endpoint must not issue one concept-health query per note.
- This endpoint is lazy readiness data for frontend section aggregation. The backend does not group by section label, add a section entity, add a mastery field, persist readiness, or call AI.
- Readiness counts stay Free. Do not gate this endpoint behind `conceptHealthService.canViewConceptHealth(userId)`; Plus/Pro gating remains limited to review-timing detail such as `dueConceptCount` / `dueConcepts` on collection detail items.

### Get Goal Detail

`GET /collections/{id}/goal`

Returns owner-scoped Goal detail for the authenticated user's own collection. Missing, malformed, or not-owned ids return `CollectionNotFoundException` / `404`. Children are returned in explicit sibling order (`siblingPosition asc`, nulls last, then `updatedAt desc` fallback).

Response:

- `collectionId`
- `title`
- `description`
- `visibility`
- `courseProgram`
- `sourcePlanId`
- `parentCollectionId`
- `itemCount`
- `childCount`
- `overallReadinessPercentage`
- `masteredConcepts`
- `dueConcepts`
- `notPracticedConcepts`
- `totalConcepts`
- `createdAt`
- `updatedAt`
- `children: GoalCollectionChildResponse[]`

Each child response contains:

- `collectionId`
- `title`
- `description`
- `itemCount`
- `overallReadinessPercentage`
- `masteredConcepts`
- `dueConcepts`
- `notPracticedConcepts`
- `totalConcepts`

Goal readiness is deliberately cheap and derived from child Subject readiness counts:

`overallReadinessPercentage = round(100 * sum(child.masteredConcepts) / sum(child.totalConcepts))`, or `0` when the summed denominator is `0`.

Do not re-run concept classification over the merged Goal subtree. That would collapse same-named concepts across subjects (for example, "Assessment" in Professional Education and General Education) and lose the subject-weighted curriculum shape. If one child readiness computation fails, that child degrades to a zero/unavailable shape and the Goal response still succeeds. Empty Goals and children with no Study Packs return zero shapes.

### Set / Clear Parent

`PATCH /collections/{id}/parent`

Request:

- `parentId` nullable UUID

Behavior:

- `parentId = null` clears the parent; clearing an already top-level collection is a safe no-op.
- non-null `parentId` nests the collection under another owned collection.
- validation and write happen in one transaction.
- child or parent not found / not owned returns `404`.
- self-parent returns `400`.
- parent that is not top-level returns `400`.
- child that already has children returns `400`.
- parent with direct note items returns `400`, because Phase 1 Goals are containers of Subject plans, not mixed note folders.
- setting the current parent again is a safe no-op.

### Set / Clear Primary

`PUT /collections/{id}/primary`

Behavior:

- parses `{id}` with the same malformed-id-as-collection-404 pattern as other collection endpoints
- verifies `{id}` exists and is owned by the caller
- verifies the target collection is top-level (`parentCollectionId == null`)
- writes `users.primary_collection_id = {id}`
- setting the existing primary again is a no-op success
- returns `204`

`DELETE /collections/{id}/primary`

Behavior:

- parses `{id}` for route consistency, but the id does not need to match the current primary
- clears `users.primary_collection_id`
- clearing when no primary is set is a no-op success
- returns `204`

`GET /auth/me` exposes the persisted nullable `primaryCollectionId`; there is no separate profile/preference read endpoint for this value.

### Set / Clear Target Date

Setting a target date reuses the general metadata PATCH rather than a dedicated `PUT`, since it is one field among several on that same endpoint:

`PATCH /collections/{id}` (`updateMetadata`)

Behavior:

- accepts an optional `targetCompletionDate` field alongside `title`/`description`/`courseProgram`/`estimatedStudyHours`
- omitting the field preserves the existing value (same omit-preserves semantics as the other optional fields on this endpoint)
- rejects with `InvalidCollectionRequestException` / `400` if the target collection is a child Subject plan (`parentCollectionId != null`)

`DELETE /collections/{id}/target-date`

Behavior:

- parses `{id}` with the same malformed-id-as-collection-404 pattern as other collection endpoints
- verifies `{id}` exists and is owned by the caller
- clears `note_collections.target_completion_date`
- clearing when no date is set, or on a child Subject plan, is a no-op success
- returns the updated `NoteCollectionDetailResponse`

### Reorder Goal Children

`PUT /collections/{id}/children/order`

Request:

- `childIds`: ordered UUID list

Behavior:

- owner-scoped and transactional
- validates the parent Goal belongs to the caller
- validates the submitted ids include exactly the current child Subject plans of `{id}`
- ids that are not children of the Goal or are not owned by the caller are rejected
- rewrites child `siblingPosition` values from `0..N-1`
- returns refreshed Goal detail

### Update Metadata

`PATCH /collections/{id}`

Request:

- `title` optional, but if present it must be non-blank and max `150`
- `description` optional; omit / `null` to leave unchanged, send `""` to clear (blank normalizes to null)
- `courseProgram` optional; same PATCH semantics (omit / `null` preserves, `""` clears); normalized with the same course/program normalization used by notes
- `estimatedStudyHours` optional; omit / `null` preserves the existing value

Behavior:

- **PATCH semantics (v0.37.2): only fields present (non-null) in the request are written; a `null` field means "not included" and is left untouched.** This is what stops a partial caller — e.g. the Goal Builder's title-only rename (`PATCH /collections/{childId}` with `{ title }`) — from silently wiping `description`, `courseProgram`, and `estimatedStudyHours`. To clear a text field, callers send an explicit empty string (`""`), which normalizes to null. Do not revert this to unconditional overwrite; the earlier form (title guarded, other fields overwritten from `null`) was a live data-loss bug.
- **Course/program cascade to blank children (v0.39.1).** When a request sets a non-blank `courseProgram` on any collection, `updateMetadata` cascades that same value to every direct child (via `findOrderedChildrenByParentCollectionIdAndOwnerUserId`) whose own `courseProgram` is currently null/blank — an already-set, intentionally-different child value is never overwritten. This applies on every `updateMetadata` call, not just at initial Goal publish, and fixes a real bug where `publishChildCollections` cascaded `visibility=PUBLIC` on publish but never touched `courseProgram`, leaving newly-published child Subject plans with a blank `courseProgram` that broke course/program-scoped public discovery (v0.33.0). Clearing `courseProgram` to blank (`""`) does **not** cascade anything to children — a clear has nothing useful to forward-fill.
- clearing a previously-set `estimatedStudyHours` back to null via this endpoint is not currently supported — an omitted/`null` value preserves it, and the integer field has no non-null "empty" sentinel. Setting a new value works; clear-to-empty is deferred to a later release.
- bumps `updatedAt`
- returns full detail

### Publish / Unpublish Study Plan

`POST /collections/{id}/visibility`

Admin-only request:

- `visibility`: `PRIVATE` or `PUBLIC`

Behavior:

- publishing a leaf plan validates the collection is non-empty
- publishing a leaf plan validates every item note still has `visibility=PUBLIC`
- publishing a Goal validates it has at least one child Subject plan
- publishing a Goal validates every child Subject plan is non-empty and every child item note still has `visibility=PUBLIC`
- publishing a Goal cascades `visibility=PUBLIC` to all child Subject plans after the Goal is saved
- invalid publish attempts return `CollectionNotPublishableException` / `400`
- unpublishing to `PRIVATE` is allowed and does not cascade to child Subject plans
- returns full detail

**Metadata save is decoupled from publishing (v0.33.0).** Course/program and description persist through `updateMetadata` (`PATCH /collections/{id}`) independently of the publish action, so a blocked publish never discards what the admin typed. In the publish modal (`PublishStudyPlanModal`): `handlePublish` persists a dirty course/program **before** the private-notes/empty gate, and the unpublished state exposes a standalone **Save** action (in addition to Publish) so course/program can be saved without attempting to publish. Publish validation itself is unchanged for leaves — every note public + at least one note, enforced on the backend. Goal publish extends the same rule across every child Subject plan and cascades only when publishing to `PUBLIC`. Do not re-couple these; the decouple is deliberate (it fixed silent course/program loss on a failed publish).

### Public Plan List

`GET /collections/public?courseProgram={value}`

Behavior:

- no authentication required
- returns only root `visibility=PUBLIC` collections (`parentCollectionId IS NULL`)
- optional `courseProgram` filter is normalized before lookup
- private collections are never included
- public child Subject plans are not listed as standalone cards; they are reached through the public Goal/adopted Goal context

### Public Plan Detail

`GET /collections/public/{id}`

Behavior:

- no authentication required
- returns detail only when the collection is `PUBLIC`
- private or missing collections return `CollectionNotFoundException` / `404`
- stale private/deleted item notes are omitted from the public item payload rather than leaked

### Adopt Study Plan

`POST /collections/{id}/adopt`

Behavior:

- authenticated users can adopt only `PUBLIC` source collections
- if the caller already owns a collection with `sourcePlanId={id}`, the endpoint returns that existing personal plan id instead of creating a duplicate
- otherwise the endpoint iterates source items in saved order and calls `copyNote(noteId, userId, includeStudyPack=true)` for each still-public source note
- each source item is isolated; private, deleted, or otherwise unavailable notes are skipped and counted instead of failing the whole adoption
- the personal collection is `PRIVATE`, keeps `sourcePlanId={source id}`, mirrors the source title/description/courseProgram/estimatedStudyHours, and preserves copied item order plus labels
- adoption bills no quota and makes no AI calls
- `sourcePlanId` is lineage/idempotency only; source edits never sync into adopted personal plans
- server analytics fires `STUDY_PLAN_ADOPTED` with `sourcePlanId`, `copiedCount`, `skippedCount`, and `alreadyAdopted`
- **Ownership badge (frontend, v0.40.1).** An "Adopted" pill shows on the `/collections` list page cards and on a collection's own detail page (in the `PlanHeroCard` badge row, alongside Published/Private) whenever `sourcePlanId != null` — for both a leaf plan and a Goal. Nothing shows for self-created collections (unlabeled implies "yours"). No backend change; `sourcePlanId` already existed on every collection response. Showing the *original author's name* is separate, larger, later work — no `authorDisplayName`/`isOfficial`-type field exists on any collection response today.

### Goal Adopt

`POST /collections/{id}/adopt-goal`

Response:

- `goalCollectionId`
- `adoptedSubjectCount`
- `skippedSubjectCount`
- `totalNotesCopied`
- `totalNotesSkipped`
- `alreadyAdopted`

Behavior:

- authenticated users can adopt only `PUBLIC` source Goal collections; a public leaf plan passed to this endpoint returns `CollectionNotFoundException` / `404`
- if the caller already owns a Goal with `sourcePlanId={id}`, the endpoint returns that existing personal Goal id with `alreadyAdopted=true`
- otherwise the endpoint creates a private personal Goal with `sourcePlanId={source Goal id}` and copied title/description/courseProgram/estimatedStudyHours, but no direct items
- each source child Subject plan is adopted through the existing leaf `adopt` flow, so note copying, per-note skip isolation, Study Pack inclusion, idempotency, and concurrent-adopt race recovery stay centralized
- after a child Subject is adopted, a standalone existing personal child (`parentCollectionId == null`) is re-parented under the new personal Goal and receives the source sibling position
- an existing personal child already nested under another personal Goal is skipped; it is not duplicated and not re-parented
- if all children are skipped, the Goal adopt still succeeds and redirects to the new empty personal Goal
- adoption bills no quota and makes no AI calls
- adopted notes and plans are immediately editable by the learner, matching leaf-plan adopt semantics
- server analytics fires `STUDY_GOAL_ADOPTED` with adopted/skipped Subject counts, copied/skipped note totals, and `alreadyAdopted`

### Delete Collection

`DELETE /collections/{id}`

Behavior:

- deletes the collection and its item rows
- never deletes referenced notes
- returns `204`

### Add Items

`POST /collections/{id}/items`

Request:

- `noteIds`

Behavior:

- validates every note is owned by the caller before writing
- dedupes the request list
- skips notes already present in the collection
- rejects adding notes to a collection that currently has child plans
- appends new notes after the current highest position
- bumps `updatedAt`
- returns full detail

### Remove Item

`DELETE /collections/{id}/items/{noteId}`

Behavior:

- removes the matching item
- compacts remaining item positions so positions stay contiguous
- bumps `updatedAt`
- returns `204`

### Set Order

`PUT /collections/{id}/items/order`

Request:

- `items: [{ noteId, label? }]`

Behavior:

- submitted note set must exactly equal the current collection note set
- cannot add or remove membership; use Add Items or Remove Item for that
- rewrites item positions from submitted order
- applies trimmed nullable labels
- rejects labels over `120` characters
- bumps `updatedAt`
- returns full detail

## Error States

- Collection not found or not owned by caller -> `CollectionNotFoundException` / `404`.
- Private or missing public-plan source/detail -> `CollectionNotFoundException` / `404`.
- Publish empty or any-private-note collection -> `CollectionNotPublishableException` / `400`.
- Malformed collection path UUID -> `CollectionNotFoundException` / `404`.
- Blank title on create or update -> `InvalidCollectionRequestException` / `400`.
- Title over `150` characters -> `InvalidCollectionRequestException` / `400`.
- Label over `120` characters -> `InvalidCollectionRequestException` / `400`.
- Missing/null item note ID -> `InvalidCollectionRequestException` / `400`.
- Referenced note does not exist or is not owned by caller -> `NoteNotFoundException` / `404`.
- Remove item for a note not in the collection -> `CollectionItemNotFoundException` / `404`.
- `setOrder` adds, drops, or duplicates a note -> `InvalidCollectionRequestException` / `400`.

## Frontend Core UI

The core Collections UI ships as the universal organization surface:

- `/collections` lists the user's saved collections in backend order (`updatedAt desc`).
- `/collections/[id]` shows one collection, its ordered note items, label-derived sections when present, and a per-note execution-status hint.
- Both leaf and Goal collection detail render the same `PlanHeroCard`: a badges row (course/program, optional `~N hrs` estimated study time, a status badge, and the publish toggle) above the title/description block. The status badge is the one part that differs by shape — leaf shows the Study Pack readiness rollup (`notesWithStudyPack/totalNotes notes ready`), Goal shows its child-Subject count (`N Subject Plan(s)`) since a Goal holds no notes directly. Goal detail's own `ReadinessSummary` (overall readiness %, mastered/due/not-started) renders below the hero, not duplicated in the badge (v0.36.1); leaf detail now mirrors that placement with the shared compact `ReadinessSummary` fetched from `GET /collections/{id}/readiness` (v0.37.0).
- The per-note hint is a learner practice signal, not exam-readiness: `Needs Study Pack` (no `STUDY_PACK_READY` pack yet) → `Not started` (pack ready, `lastSessionCompletedAt == null`) → `Practiced` (`lastSessionCompletedAt != null`), with transient `Generating` / `Generation failed` states preserved for operational feedback. It deliberately does **not** show `Study Pack ready` / `Quiz ready` (the prior hint): plan-level Study Pack readiness already lives in the Progress rollup, and exam-eligibility (quiz-readiness) is surfaced on the Exam Builder, not here.
- `/collections/[id]` header actions: `Edit` and `Delete` live in a single `⋯` context menu (short labels, mirroring Note Detail); the teacher terminal action (`Build Exam`) sits at the bottom-left of the header card via the `PageHeader` `footer` slot, not crammed into the action row. Admin status is read reactively (SSR-safe).
- Admins see a published/private **status badge that is itself the publish control** (Notion-style): it sits in the `PlanHeroCard` badges row **above the title**, alongside course/program and the status badge, and clicking it (`aria-label="Publish settings"`, gear affordance) opens the publish modal. There is no separate `Publish settings` menu item or `Share` button. The boilerplate header description is omitted when the plan has no author-written description.
- The publish modal (not an inline panel): a Course/Program **combobox locked to known buckets** (`CourseProgramCombobox` with `allowCustom={false}` + `inlineDropdown` so the options panel renders in-flow and is not clipped by the modal's overflow — the plan's existing value is always kept selectable), a single `Publish` (requires a non-empty course/program) / `Unpublish` action, and a `Save` for course/program edits while published. The `X` is the only close affordance (no redundant `Close` button).
- Because adopters copy the plan's notes, the publish modal flags any still-private item notes and offers a one-tap `Make N public` (loops `updateNoteVisibility`); admins also see per-row `Private` badges on plan items. Private status is computed frontend-side by joining plan items against the owner's note list (`listNotes`) — no collection-item DTO change. `Publish` is disabled until every plan note is public, matching the backend rule that publishing requires all item notes `PUBLIC`.
- `/collections/[id]` shows a compact progress summary near the header after the inline readiness row: notes practiced and a practiced/total progress bar. It does not repeat Study Pack readiness or mastery readiness — the Plan Hero badge owns Study Pack coverage, the inline `ReadinessSummary` owns mastery, and the Progress card is execution/practice progress only.
- Leaf detail pairs the inline readiness row with two direct next steps when applicable: an entitled-only `Review due concepts` link to `/notes/{noteId}?ref=/collections/{collectionId}` for the first item with due concepts, and a premium-exam CTA driven by the existing terminal action (`Student -> Long Exam`, `Board Taker -> Board Exam`, `Professional -> Interview Practice`). Teacher `Build Exam` remains a hero terminal action and is not duplicated on the readiness row.
- Entitled users see per-note due-concept counts and up to 3 concept names. Free users see no fabricated counts and may see one plan-aware upgrade affordance resolved through `getUpgradeCtas(currentPlan)`.
- A frontend-only `Next in this plan` card derives one action from the already-returned ordered items. It never calls a recommendation endpoint or persists recommendation state.
- `/collections/[id]` opens in read mode by default. Note cards show title, subject/course metadata, execution status, entitled due-concept signals, and admin private badges. Leaf plan curation now links to `/collections/{id}/builder`; the old inline `Organize` toggle is no longer exposed from detail. Drag handles, the per-note Section combobox, Move up/down, and Remove controls remain implementation support for existing organize-mode code paths but are not the primary curation entry point.
- Every plan detail page, Goal or leaf, exposes one top-level `Build` action in the hero that links to `/collections/{id}/builder`. Leaf detail no longer duplicates that route as an `Edit` link in the Notes card header; the `⋯` menu's `Edit` action remains metadata-only.
- When at least one item has a trimmed non-empty `label`, `/collections/[id]` groups the notes into collapsible section cards (`section name + item count + chevron`). Section order follows the first/minimum `position` in each section, items stay in `position` order within each section, and null/empty labels render under a trailing **Ungrouped** section. Section cards start collapsed below the `lg` breakpoint and expanded at `lg` and wider; collapsed sections render only the header row (including in organize mode). When collapsed, the card also shows a **title peek**: the first 1–3 note titles joined by `·`, with a `+N more` suffix when there are more — pure content preview, no progress semantics.
- **Inline section rename (organize mode only).** In organize mode, the section name in each non-Ungrouped section card becomes an editable `<input>`. Clicking it enters edit mode; `Enter` or blur commits; `Escape` cancels without saving. Committing an empty name or the unchanged name is a no-op. Committing the reserved name `"Ungrouped"` is a no-op (that string is the synthetic null-label bucket and cannot be used as a real label). Committing a name that already exists as another section triggers a **merge confirmation modal** ("Merge into 'X'? All notes from 'Y' will be moved into it.") with Cancel / Merge sections actions; confirming sends a single batch `setCollectionItemOrder` with all items from the old section relabeled to the target name. All rename paths use one API call, not one per note. The `Ungrouped` section name is static in organize mode (null-label items cannot be given a shared label this way; use the per-note Section combobox instead).
- Section headers may show the v0.34.0 Free readiness stat `N% · M due`, computed by lazy-loading `GET /collections/{id}/note-concept-counts` after initial render and aggregating by item label client-side. The stat is hidden while organize mode is active and when a section has zero concepts or the lazy fetch fails. Item rows remain execution organization only: no subject mastery, milestones, goals, streaks, weakest-subject routing, or progress bars.
- The next-action phases are evaluated globally in this order, choosing the first matching note in saved order within each phase:
  1. First note without `STUDY_PACK_READY` -> `Generate Study Pack`.
  2. When all Study Packs are ready, first note with no completed practice -> `Study this note`.
  3. When all notes are practiced, first note with due concepts -> `Review due concepts` for entitled users only.
  4. Otherwise -> `All caught up in this plan`.
- The Next card links to `/notes/{noteId}` with `ref=/collections/{collectionId}` so Note Detail returns to the current plan.
- The "Continue where you left off" banner uses the latest non-null `lastSessionCompletedAt` among returned items and links to that note's same next plan action. It is dismissible for the browser session through `sessionStorage` and never persists server state.
- Empty collections show a neutral no-progress state and never calculate a percentage from `0/0`.
- The detail page loads from `GET /collections/{id}` on mount, so a hard refresh renders the persisted order.
- The detail page can edit metadata, including optional estimated study time, delete the collection, and add notes through the shipped CRUD API. Builder owns the primary relabel/reorder/remove curation path for leaf plans.
- Opening a note from the detail page passes `ref=/collections/{id}`, so the note's back link returns to the collection with the profile-aware label (via `getCollectionLabels`) instead of falling back to Library.

Profile-aware labels are resolved only through `frontend/lib/collection-labels.ts`.

### Browse published plans (`/collections/published`)

The Dashboard card surfaces only the top matching published root plan, so publishing several plans per course/program hides all but one. `/collections/published` is the lightweight browse surface for official published root plans (v0.31.1, root-only listing tightened in v0.33.3; unfiltered Browse All added in v0.40.1).

- Recommended section. The top section remains course/program-scoped and reuses root-only `GET /collections/public?courseProgram=` (via `listPublicStudyPlans`) plus the user's `GET /collections` to join each plan to an already-adopted personal collection (`sourcePlanId`). Child Subject plans are not shown as standalone public cards.
- Browse All section. Below Recommended, the page renders `Browse All Official {labels.plural}` with `id="browse-all"`, calls `listPublicStudyPlans({})` / `GET /collections/public` with no course/program filter, sorts the returned public top-level collections alphabetically by title (case-insensitive), and renders the same `PublicStudyPlanCard` component. Duplication between Recommended and Browse All is expected and not de-duplicated.
- The user's `GET /collections` fetch is shared across the Recommended and Browse All adoption joins. If that owned-collections join fails, public cards still render with no adopted state instead of blocking the page.
- This is a surface for *plans*, not the Public Library (which is for *notes*).
- Leaf plans render as a `PublicStudyPlanCard` with `Start this plan` (adopt → `POST /collections/{id}/adopt` → route to the new personal collection) or `Continue this plan` when already adopted.
- Goal plans render with `Start this Goal` (adopt → `POST /collections/{id}/adopt-goal` → route to `goalCollectionId`) or `Continue this Goal` when already adopted. The card describes the Goal as `{childCount} Subject plans · {itemCount} notes`.
- The skipped-note notice uses the shared `lib/study-plan-skipped-notice.ts` key. For Goal adopt, the skipped count reflects skipped child Subject plans; for leaf adopt, it reflects skipped notes.
- `courseProgram` and `profileType` come from `getMe()`; labels resolve through `getCollectionLabels`. It is reached via the `See all N {plural}` link on the Dashboard card (shown only when 2+ plans match), and via the empty-state `Browse all {labels.plural}` link to `/collections/published#browse-all`.
- Recommended states: loading skeleton, error + retry, a guidance state when no course/program is set (links to `/profile`), and an empty state when the track has no published plans. Browse All has its own loading skeleton, error + retry, and distinct empty state for the genuine system-wide case where no official public root collections exist. One section failing does not blank the other. `BackLink` returns to the Dashboard.

**Recommended plans also surface on the user's own Study Plans page (`/collections`) (v0.33.0).** The same Dashboard "Recommended {singular}" section (`DashboardStudyPlanSection`) is reused below the user's own plans — course/program-scoped, with the same `See all N {plural}` link to `/collections/published`. It is **not** tabs, and the recommendation stays scoped to the learner's own course/program. `/collections` fetches `courseProgram` via `getMe()` and reads `profileType` from `getAuthUser()`. `/collections` passes `browseWhenEmpty` so that when the learner's course/program has no curated plan yet it renders an honest "We don't have an official {singular} for {program} yet" empty state instead of nothing, plus a `Browse all {plural}` link to `/collections/published#browse-all`. Dashboard call sites that opt into `browseWhenEmpty` get the same corrected card; call sites without the prop still self-hide when no plan matches.

If the learner already has the matched plan, the section shows an **"In your library"** badge and opens the existing plan instead of offering a re-adopt CTA. "Already has" means either they **adopted** it (a personal collection whose `sourcePlanId` equals the plan id) or they **own the published source itself** (a personal collection whose `id` equals the plan id — the admin/curator case). The CTA reads "Open this plan" for an owned source, "Continue this plan" / "Continue this Goal" for an adopted copy, and only "Start this plan" / "Start this Goal" (which adopts) for a plan the learner does not yet own. This prevents an owner from self-adopting a redundant copy of their own published plan.

The Study Plan remains an execution surface for one curated, ordered set. Collection detail may show the shared inline plan-level readiness summary, but execution-detail rows still do not duplicate Progress: no subject mastery percentages, milestones, goals, streaks, or weakest-subject routing belong on rows or list cards.

The v0.33.0 exception, folded into the canonical `/progress` surface as of v0.36.0, is plan-scoped readiness:

- A "Check readiness" CTA on `/collections/[id]` still deep-links to the full `/progress?collectionId={id}` plan-readiness view (no separate `/collections/[id]/readiness` route). Leaf detail also lazy-loads the same endpoint for its compact inline summary; see `docs/features/my-progress.md` § Plan Readiness Cross-Reference for the full behavior.
- It uses profile-aware naming from `getCollectionLabels` for the not-found state.
- It renders the shared `ReadinessSummary` component: overall ready percentage, mastered/due/not-started counts, and per-subject readiness bars.
- When one or more plan notes have no Study Pack yet, it shows a caveat (`N of M notes in this {plan} don't have a Study Pack yet, so they aren't reflected below`) linking back to `/collections/{id}` instead of a flat Study Pack coverage stat. The caveat is hidden once every note has a Study Pack — this is deliberately not a permanent status readout, only a nudge for incomplete plans.
- It distinguishes `404` not-found/not-owned from transient load failures with retry.
- It fires `PLAN_READINESS_VIEWED` once per distinct plan selected in a session.

| Profile | Singular | Plural / nav |
|---|---|---|
| `TEACHER` | `Lesson Plan` | `Lesson Plans` |
| `STUDENT` | `Study Plan` | `Study Plans` |
| `BOARD_EXAM` | `Review Set` | `Review Sets` |
| `PROFESSIONAL` / default | `Collection` | `Collections` |

Do not hardcode those profile-specific names in page or component code. Components should ask the resolver for `singular`, `plural`, `navLabel`, empty-state copy, and CTA copy.

Core UI behavior:

- The app shell shows the profile-aware Collections nav item directly after Library.
- `/collections` uses the authenticated page header pattern and opens a create modal with a title (max length `150`) and an optional description field. The Library selection-mode create modal (split-button `{singular}`) also collects an optional description (v0.33.0) — both create paths now carry description through `createCollection`, so a plan built from a Library selection is no longer title-only.
- `/collections/[id]` uses `BackLink href="/collections"` with the profile-aware plural label, except a nested Subject plan (`parentCollectionId != null`) back-links to its parent Goal (`/collections/{parentCollectionId}`, parent title as label) once the parent's title has resolved; it falls back to the flat `/collections` list while the parent fetch is in flight or if it fails (v0.36.1).
- Item labels are edited as per-item **Section** assignment controls with max length `120`: users can choose an existing section name from the current plan, type a new free-text section, or clear the value to return the item to Ungrouped. The control still persists through `PUT /collections/{id}/items/order`; no new mutation, DTO field, endpoint, taxonomy, or backend interpretation is added.
- Reorder uses drag-and-drop plus `Move up` / `Move down` buttons for accessibility, all available only in organize mode.
- Reorder/relabel persists through `PUT /collections/{id}/items/order` with the full ordered item set.
- The in-detail note picker uses the user's own notes from the Library note-list API and excludes notes already in the collection.
- Delete is confirm-gated and must state that deleting a collection does not delete its notes.
- Load failures show retry states; a `404` detail response shows a not-found state with a link back to the collections list.

### Profile-aware terminal actions & Library integration

The shipped Prompt B integrations make collections useful from both entry points:

- The Library header is a split button: primary `New Note` plus a caret menu (`Note` / `Import files` / `{singular}`). There is no standalone `Select` button. Choosing `{singular}` enters Library **selection mode** — filter and multi-select notes, then `Create {singular}` (a title modal → `createCollection`); creating with zero notes (an empty plan) is allowed. This is the universal way to create a plan, leveraging the Library's filters; the Study Plan detail's "Add notes" picker still handles adding notes to an *existing* plan. Both surfaces offer a **Select all / Deselect all** toggle scoped to the active search/filter (v0.33.0): the Library selects all notes matching the current filters (including those beyond the first display page, since the filtered set is fully computed client-side), and the detail "Add notes" picker selects all eligible notes matching the search (notes already in the plan stay excluded). Deselect-all only clears the currently-filtered set, so selections made under a different filter persist.
- Teachers reach `Build exam` (Exam Builder) from the same selection — both `Create {singular}` and `Build exam` act on the selected notes; no separate Select entry.
- Library selection accepts any owned note, including `DRAFT` notes without a generated quiz. Quiz readiness is not a plan membership requirement (it is only required for `Build exam`).
- The Library teacher-only `Build exam` action remains gated to Teacher/Admin exam workflows. If the selection mixes ready and non-ready notes, the action proceeds with the selected note IDs and the Exam Builder filters to quiz-ready notes; if zero selected notes are quiz-ready, the action is disabled with recovery copy.
- The collection detail terminal action resolves through the profile-aware terminal-action resolver. `TEACHER` receives `Build exam from this Lesson Plan`; all other profiles receive no terminal CTA for now.
- The teacher terminal CTA passes the collection identity through `/library/exam-builder?collectionId={id}` and may also include ordered quiz-ready note IDs as a resilience fallback. The collection ID is the source of truth for initial sectioning.
- Exam Builder fetches the collection and pre-seeds one section per distinct trimmed item label, in first-occurrence order. Only quiz-ready notes are included, notes keep collection position order, and unlabeled quiz-ready notes collapse into one trailing default section. Labels with no quiz-ready notes create no empty section.
- When a collection contains notes without a generated quiz, the Exam Builder no longer drops them silently: it shows an amber `N of M notes excluded — no quiz generated yet` notice listing those note titles, so a teacher can see exactly which notes to generate quizzes for. This is the canonical place for the quiz-readiness blocker (it is not duplicated on the Study Plan detail rows). Amber, not red — a missing quiz is an incomplete state, not an error.
- Collections with only unlabeled quiz-ready notes seed one default section. Teachers can still rename, reorder, add, rebalance, and replace the initial structure with an existing Exam Builder template.
- The terminal CTA keeps the existing partial-readiness hint when some notes are skipped and disables with `Generate a quiz for at least one note to build an exam.` when none are quiz-ready.
- This handoff is frontend-only. DOCX export and shareable quiz links remain Teacher/Admin-only, and no collection-level generation, analytics, quota, backend, or AI behavior is added.
- Student, board-exam, and professional multi-note practice terminal CTAs are deferred. The existing Long Exam flow is same-subject scoped and meters quota per source note, while collections can be cross-subject and mixed-readiness; a collection-level practice action needs a separate product-shape pass.
- `COLLECTION_CREATED` fires server-side from `NoteCollectionService.create(...)` only, with `itemCount` metadata for the number of initial notes. Add-items, update, remove, and reorder do not fire a creation event.

Deferred Prompt B slots:

- Student, board-exam, and professional multi-note practice actions remain a follow-up for the Long Exam same-subject/per-note-quota reason above.

## Out Of Scope

Do not add these under the collection CRUD spine unless explicitly scoped later:

- profile-aware labels or CTAs in the backend
- DOCX/shareable quiz-link generation directly from collections
- collection-level AI synthesis
- bulk generate across a collection
- user/teacher-authored published or shared collections (admin-published plans shipped in v0.31.0; non-admin publishing stays deferred)
- live-link or shared-progress adopted plans
- plan browse directory
- lesson-plan document parsing
