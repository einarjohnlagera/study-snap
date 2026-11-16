# Phase 0 scope: collection publication-boundary fail-closed fix (implementation-ready, planning only)

**Status:** PROPOSAL, planning only. Owner R1-R6 FINAL 2026-11-11. Nothing here authorizes implementation, a
merge, a deploy, a migration, an env change or a production-data write. No version number or date allocated.
**Amended 2026-11-14 for the four Product UX decisions (P1 to P4, section 9).** Cold falsification of the 2026-11-14 delta by an independent Opus agent, 2026-11-16 (section 9.7). Base text committed at `eb58264f`; code re-read at `origin/main` `a928029b` (no change to `NoteCollectionService.java` since `d5eaf383`, `git diff --stat` empty).
**Parent plan:** `docs/claude-plans/2026-11-10-study-plan-discovery-and-community-collections-plan.md`, section D
"Phase 0", "Publication contract: parent, stamped child, note", and E4. This file does not repeat them.
**Code read:** `origin/main` at `d5eaf3837812c965f4f7ca03d9a8bd9948269ac6` (2026-11-11), `git show`/`git grep`
only. `Svc` = `backend/src/main/java/com/studysnap/backend/service/NoteCollectionService.java`.
**Risk tier:** one scoped cold falsification agent (a privacy boundary moves). **Size:** S, Svc plus tests,
roughly 60-100 LOC of production code (ASSUMPTION). **Audience:** input to a later Codex brief written by the
Release Implementor; this is not a brief and contains no code.

Labels: **CONFIRMED** (file:line at `d5eaf383`), **ASSUMPTION**, **PROPOSAL**.

## 1. Goal and non-goals

**Goal (PROPOSAL):** a withdrawn or deleted Goal leaves none of its Subject Plans publicly readable, adoptable or
listed as Official. The change is **additive and only narrows reachability**.

**Non-goals (each is a hard exclusion for the brief):**
- No change to the published-stamp child selection: `getPublic` (CONFIRMED Svc:768-770), `listPublic`
  (Svc:308-310), `adoptGoal` (Svc:1157-1164, comment :1157 "The publication stamp, rather than visibility,
  decides"), `inspectSourceUpdate` (Svc:2364-2371). The ancestor check keys on the **parent**, never on the
  child's own visibility.
- No API shape change, no new endpoint, no `Cache-Control` change, no migration, no UI change.
- No change to adoption semantics, adopted copies, their notes, or a running Goal-adoption job (the job copies a
  frozen child-id list and does not re-check source visibility, CONFIRMED Svc:1157-1158, :1319-1323; leave it).
- **The nine PRIVATE-but-stamped sections (ALE 7, PNLE 2) keep appearing exactly as today** ("legacy" labels today's nine, not a closed set: section 9, U2) inside their
  PUBLIC Goals, and keep 404-ing by their own id (R5).
- No data repair. No unpublish/republish of ALE or PNLE (R5 guardrail).

## 2. Exact methods and changes

### 2.1 Ancestor check (PROPOSAL: a new private helper, e.g. `assertPublicAncestry(collection)`)
- **Behaviour:** if `collection.getParentCollectionId() != null`, require
  `collectionRepository.findByIdAndVisibility(parentId, PUBLIC)` to be present; otherwise throw
  `CollectionNotFoundException` (same exception and body as never-existed). A root (`parentId == null`) passes.
  Two persisted levels (ADR-003), so one lookup suffices. Sits beside and is called after
  `assertPublishedIfSubjectPlan` (CONFIRMED Svc:3391-3395).
- **Call sites (CONFIRMED gates at `d5eaf383`):**
  - `getPublic` (Svc:762-765): after the `findByIdAndVisibility` and `assertPublishedIfSubjectPlan`.
  - `adopt(source, userId, reassert)` (Svc:1095-1100): same place, before `adoptSource`. **Not** inside
    `adoptSource` (Svc:1111-1145): `adoptGoal`'s job calls `adoptSource` directly for children
    (Svc:1348), including PRIVATE-but-stamped ones, and must keep doing so.
  - `adoptGoal` (Svc:1147-1150): the source is required PUBLIC; add the check so a PUBLIC child id under a
    non-public parent is a 404 here too. **Corrected 2026-11-16 (cold review): this is NOT mere defence in depth.** The existing-copy early return (Svc:1151-1155) runs BEFORE the stamped-children check (:1160-1166), so a learner who already holds a copy whose `source_plan_id` is that child's id gets `existingGoalJobResponse`, not a 404 (405 child-source copies exist inside adopted Goals). The check therefore goes **before :1151**, right after the PUBLIC lookup. Behaviour change, intended: that call now answers 404 when the child's parent is not PUBLIC. Test 21.
  - `inspectSourceUpdate` (Svc:2350-2361, used by `GET`/`POST /collections/{id}/source-update`, Ctl:301-318):
    when the adopted copy's source is a child whose parent is not PUBLIC, return
    `SourceUpdateInspection.detached(adoptedRoot)` exactly as for a non-public source. **Why:** an adopter of a
    standalone Subject Plan would otherwise keep receiving additions, titles and terms from a withdrawn Goal.
    With the 2.2 cascade the source becomes PRIVATE and is already detached; the check covers states the
    cascade did not produce (P1 is 0 today, so no current learner changes).
  - `applySourceUpdate`'s post-pass re-check (Svc:1523-1526) and the MOVED/RETIRED relocation lookup
    (Svc:2657-2660): see section 8 (to verify; recommend the same helper).
- **Reuse:** `findByIdAndVisibility`, `CollectionNotFoundException`, `assertPublishedIfSubjectPlan`.

### 2.2 Unpublish cascade in `updateVisibility` (CONFIRMED Svc:1057-1090)
- **Before:** `PRIVATE` sets only the Goal; children keep `PUBLIC` (pinned by
  `NoteCollectionServiceTest.updateVisibility_unpublishingGoalDoesNotCascadeToChildren`, :4341-4355, which
  verifies `saveAll` and `findOrderedChildrenByParentCollectionIdAndOwnerUserId` are never called).
- **After (PROPOSAL):** when `visibility == PRIVATE`, load children with the same query the publish side uses
  (`findOrderedChildrenByParentCollectionIdAndOwnerUserId`, Svc:2069-2070), set each `PUBLIC` child to `PRIVATE`,
  `touch`, `saveAll`, in the same `@Transactional` method. Mirror of `publishChildCollections` (Svc:2068-2080).
- **Leave untouched:** `published_at` on children and items, `last_update_published_at`. The comment at
  Svc:1072-1082 makes `last_update_published_at` the "has ever been published" discriminator, so a later
  republish still skips `publishInitialCurriculum` (Svc:1083) and rows added while private stay unpublished.
- **Nine sections:** already PRIVATE, so a no-op for them (but see the republish caution).
- **Republish caution (unchanged behaviour, CONFIRMED Svc:2075-2076):** a republish sets **every** child PUBLIC.
  After this change an unpublish/republish round trip of ALE or PNLE would flip the nine PUBLIC. R5 forbids it. **A repeat PUBLIC request on an already-PUBLIC Goal is now specified as an idempotent no-op (2.2b, Product UX Decision 1, Option A, 2026-11-14), so that route stops being a hazard once it ships.** **What remains is the unpublish-then-republish round trip: PRIVATE to PUBLIC still flips every child PUBLIC (2.2b), so the R5 guardrail (never unpublish ALE or PNLE) stays in force for that route (section 9, U1).** Also (low): `updateVisibility` and `delete` take no row lock while `publishReviewSetUpdate` locks the root `FOR UPDATE` (Svc:802); an unpublish queued behind a publish can still send update notifications after commit (Svc:822). Accept and record, or add the lock.

### 2.2b Repeat PUBLIC on an already-PUBLIC Goal is an idempotent no-op (Product UX Decision 1, Option A; added 2026-11-14)
- **Before (CONFIRMED Svc:1057-1068):** `updateVisibility` captures `previousVisibility` (:1059) but uses it only for the initial-curriculum branch (:1083); the child cascade `publishChildCollections` (:1068) runs on every PUBLIC request. The UI offers Publish only while private (`collection-detail-page-client.tsx:2783`, and Unpublish when public), so no screen sends the repeat call, but the API accepts it.
- **After (PROPOSAL):** when `previousVisibility == PUBLIC` and the target is `PUBLIC`: still resolve the owner (`getOwnedCollectionOrThrow`), still run `validatePublishable` (authorization and validation stay enforced, so an already-public Goal with a section whose note went private still answers `CollectionNotPublishableException`, as today), then **return the current detail without calling `publishChildCollections` or `publishInitialCurriculum` and without any child write**. **Recommended in the brief: also skip `touch` and `save` of the Goal**, because `listPublic` orders roots by `updatedAt` desc (Svc:286-288), so a "no-op" that still touches the row would reorder Explore. The brief confirms this choice; Product UX did not specify it.
- **PRIVATE to PUBLIC is unchanged:** an explicit first publication (or republication) still flips every child PUBLIC and runs the initial-curriculum branch under its existing `last_update_published_at` rule. Product UX Option C (publish only never-stamped sections) is **rejected**: it keeps an implicit publication side effect.
- **Public Goal, new section (CONFIRMED; corrected 2026-11-16 from "two controls" to three ways, none uses the repeat-PUBLIC path):** (i) **Publish update** (`publishReviewSetUpdate`, Svc:800-): stamps every unstamped child and item (`NoteCollectionRepository.java:177-188`, no visibility predicate) and sets no `visibility`; this is what puts a new section into the public Goal view and into adoption. (ii) The section's own Publish control (`updateVisibility` on the child): flips only that section's `visibility`, stamps nothing, so by itself it does **not** make the section readable (`assertPublishedIfSubjectPlan`, Svc:3391-3395, still 404s an unstamped child). (iii) **Re-parenting (found by the 2026-11-16 cold review):** `updateParent` attaches any owned collection under any owned top-level Goal (Svc:916-935). Its only guards are `validateParentCanAcceptChild` (the parent is top-level and holds no notes; Svc:2013-2019, no visibility or stamp check) and the term guard (Svc:3461-3477); it sets no stamp. Because Goal views select children by `published_at`, **attaching an already-stamped collection to a PUBLIC Goal puts it into `getPublic(goal)`, the `listPublic` counts, `adoptGoal` and adopters' `inspectSourceUpdate` at once, with no Publish update and no notification.** V141 stamped every pre-existing row, private drafts included (`V141__review_set_publication_boundary.sql:7-9`), and detach keeps the stamp (2.6), so a detached section re-attached to its own Goal returns as a PRIVATE-but-stamped section. Option A changes none of the three. See section 9, U5.
- **Fixture note (CONFIRMED):** `NoteCollectionEntity.java:42` defaults `visibility` to PRIVATE, so `updateVisibility_publishingGoalCascadesPublicVisibilityToChildren` (:4310) is a PRIVATE to PUBLIC case and stays green unchanged. The brief greps every test that calls `updateVisibility(PUBLIC)` on an entity it pre-set PUBLIC; none is known.

### 2.3 Goal delete (CONFIRMED Svc:1564-1581; `V83__collection_parent_hierarchy.sql:2` `ON DELETE SET NULL`)
- **Before:** the row is deleted; children survive with `parent_collection_id = NULL`, i.e. become roots, and a
  PUBLIC one then matches `listPublic`'s root query (Svc:286) as an Official card.
- **After (PROPOSAL):** inside the existing `@Transactional delete`, before `collectionRepository.delete`, if
  `collection.getSourcePlanId() == null` (a source, not an adopted copy), load its children and set those whose
  visibility is `PUBLIC` to `PRIVATE` (**whatever the Goal's own visibility is**, corrected by the cold review) (`saveAll`, flushed before the delete in the same transaction). Only PUBLIC
  children matter; PRIVATE ones and every stamp are left as they are. Children are not deleted (E7).
- **CONFIRMED 2026-11-14:** children always share the Goal's owner: every parent assignment is owner-scoped (`updateParent` requires both owned by the caller; `adoptGoalChild` and `createSubjectAddition` attach only the adopter's own copies), and a production read found 0 children whose owner differs from their parent's. The owner-scoped query therefore finds them all.

### 2.4 `listPublic` count parity (CONFIRMED Svc:283-335)
- **Before:** item and ready counts include items whose note is no longer PUBLIC (Svc:313-320).
- **After (PROPOSAL):** count only items whose note is currently PUBLIC, matching `toPublicItemResponses`
  (Svc:3828-3834). **Feasible with no new query:** `readyCountsForNoteIds` (Svc:3066-3076) already loads
  `NoteCollectionNoteProjection`, which carries `visibility` (CONFIRMED
  `repository/NoteCollectionNoteProjection.java:11-20`). Refactor so the one projection load feeds both a
  PUBLIC-note filter on `publishedItems` (before `countItemsByCollectionId`) and the ready count. Keep it in Java;
  do not touch the shared queries (comment Svc:300-304). If the refactor cannot keep a single note-projection
  query per call, move this item to Phase 2a. Today's overcount is 0 (section C baseline).
- **⚠️ Refactor trap:** `readyCountsForNoteIds` is shared. `loadReadyCounts` (Svc:3062-3064) calls it for the
  authenticated owner lists (Svc:234, :260), where adopted copies hold PRIVATE notes. **The PUBLIC-note filter
  lives only on `listPublic`'s path (Svc:313-318)**, never inside the shared helper, or every adopter's ready
  count drops to zero. Guard: test 13.

### 2.5 `parentCollectionId` on the public mapper (CONFIRMED Svc:3658 in `toPublicDetailResponse` :3630-3674)
- **Consumers (CONFIRMED by grep):** the only frontend caller of `GET /collections/public/{id}` is
  `getPublicStudyPlanDetail` (`frontend/lib/api.ts:5618-5623`), used only by `PublicStudyPlanCard`
  (`public-study-plan-card.tsx:89`), which reads no `parentCollectionId` from the preview. Every frontend
  `parentCollectionId` read is on owner routes (`app/collections/[id]/…`). No backend consumer.
- **Recommendation (PROPOSAL): always `null` on the public mapper** (simplest, no extra lookup, matches the
  existing `resolvedLearnerLevel = null` rationale at Svc:3645-3651). `listPublic`'s summary emits it too, but
  only for roots, where it is already null. The DTO field stays (no shape change).

### 2.6 Detach (`updateParent` with a null parent) and the leak paths it closes (added 2026-11-14)
- **Before (CONFIRMED by code reading, not reproduced):** `PATCH /collections/{id}/parent` with a null parent (`updateParent`, Svc:~897-910; `NoteCollectionController.java:174-182`, USER or ADMIN) detaches a child and **leaves its `visibility` untouched**; it also clears `sibling_position`, `term_label` and `term_order`. The only guard, `assertPublishedSubjectMoveKeepsTermsCoherent` (Svc:3461-3477), blocks a published child only when it carries a term label or the destination uses terms. So a published **term-less** child (every board-exam Goal's sections) detached from its Goal becomes a **PUBLIC top-level collection**, which `listPublic` shows on Explore as an "Official" card. A PUBLIC child under a **PRIVATE** parent is also reachable today (an unguarded single-child publish, or a move under a private Goal) and is promoted the same way.
- **After (PROPOSAL):** when `updateParent` detaches (`parentId == null`) a child that is **PUBLIC, whatever its former parent's visibility**, set it PRIVATE in the same transaction, leaving every `published_at` stamp untouched; the curator can republish it deliberately as a standalone plan. Re-parenting to another parent is unchanged.
- **Adopter-visible consequence (intended, previously unlisted):** a detached child now reads RETIRED in an adopter's source-update report instead of MOVED with its live title (Svc:2657-2668).
- **Why write-side:** a read-time ancestor check cannot see a former child (its parent is null). Promoted roots would also surface in `PostSessionNextStepService.resolveRecommendedPlan` (:263-312), which puts the title in the next-step label, and in `NoteService.mapToPublicDetail` via `findOfficialReviewSetRootForNote` (`NoteCollectionItemRepository:53-70`, `NoteService:1615`), which names the "Official" set on the anonymous public note page. Only (2.3) and (2.6) cover those.
- **Product UX Decision 2 rules (2026-11-14):** detach makes a formerly attached PUBLIC section PRIVATE and withdraws its public visibility and adoption eligibility. **Detachment is never a publication action.** A detached section can be published again deliberately as a standalone collection through the existing control (CONFIRMED by reading: as a root it needs only `visibility == PUBLIC`, `assertPublishedIfSubjectPlan` applies only when a parent id exists, and `publishInitialCurriculum` runs for it on a PRIVATE to PUBLIC request with no `last_update_published_at`). An intentionally published standalone collection keeps its own rules, and a section that is still attached keeps respecting its parent Goal's boundary (2.1).
- **Builder UI and detach:** it never detaches (it creates a collection and attaches it, `study-plan-builder-page-client.tsx:2171`), so **detach** adds no PRIVATE-but-stamped section. **Corrected 2026-11-14: that does not mean the nine cannot grow.** Publish update stamps a new PRIVATE section into the public Goal (2.2b; section 9, U2).

### 2.7 Learner copies when a source section is withdrawn (Product UX Decision 2 verification; added 2026-11-14)
Verified so the brief can state it: **a source withdrawal changes no learner-owned content or progress, so no product decision on copied content is needed.**
- **Copies are independent rows.** `copySourceItems` copies each note with `noteService.copyNote(sourceItem.getNoteId().toString(), userId, true)` (Svc:2161), so the learner owns the note rows; the adopted collection is the learner's own row; `source_plan_id` is a plain nullable UUID with no foreign key (`V76__collection_visibility_and_plan_source.sql:4`).
- **Progress is keyed to the learner's own material.** `QuickReviewSessionEntity` carries the learner's `note_id` (:41-42), and its `source_collection_id` is set NULL on collection removal rather than destroying completed sessions (:130-131 comment).
- **What Phase 0 writes:** only `visibility` on source rows (2.2, 2.3, 2.6). It writes nothing on adopter rows or notes.
- **`applySourceUpdate` is additive** (Svc:1416-1558, read to its end): it creates Subject Plans and note copies, and its only writes to an existing copy are the sync-snapshot fields (`acknowledgeSourceSnapshots`: title, parent, position and synced-at on the copy, label, position and synced-at on its items). It never deletes, hides or unpublishes anything. `RETIRED` and `MOVED` are report rows only (Svc:2639-2668).
- **Learner-visible but non-destructive effects (cold review, 2026-11-16):** an adopter of a *standalone child* sees the existing "Detached from source" card (`collection-detail-page-client.tsx:1062-1068`) when its parent Goal is unpublished; a Goal-adoption job already running keeps copying from a section withdrawn mid-run (Svc:1319, additive). Foreign keys checked: `goal_adoption_jobs.goal_id` references the adopter's own Goal (`V153`, line 3), and `quick_review_sessions.source_collection_id` is ON DELETE SET NULL (`V133`, lines 26-27) and is set on the learner's own collections.
- **The only adopter-visible effect on the update report:** a detached or withdrawn section reads RETIRED instead of MOVED in the update report, and `inspectSourceUpdate` reports a withdrawn source as detached. The wording for that report is settled and deferred (section 9.6, F2); Phase 0 changes no screen.

## 3. Acceptance tests (named; layer in brackets)

Real-request rule: `NoteCollectionControllerTest` uses `standaloneSetup` (CONFIRMED: the call is at :833; :67 is its import) and checks
`@PreAuthorize` by reflection (:89-108), so it cannot prove a 403. Security tests need a full context with
`@AutoConfigureMockMvc` (pattern: `NoteCollectionServiceProjectionIntegrationTest`, :61-64). That class runs on
**H2** with Flyway disabled (`backend/src/test/resources/application.yaml:3`, `:9-10`), so real-PostgreSQL tests
follow `GoalAdoptionPostgresIntegrationTest` (Testcontainers, Flyway on, :28-33), which also exercises the real
`ON DELETE SET NULL`.
**Feasibility (cold review, 2026-11-16, CONFIRMED):** `backend/pom.xml` has no `spring-security-test` and no existing full-context MockMvc test authenticates a valid user (only anonymous requests or an invalid bearer, `PublicApiDiagnosticsFilterIntegrationTest:94`), so the ROLE_USER cases in tests 1, 5 and 16 must mint a real token with `JwtService.generateAccessToken(UserEntity)` (without one the adopt call sees a null `@AuthenticationPrincipal` and answers 500, not 404). A real 404 body carries a per-request `requestId` (`GlobalExceptionHandler.java:32,51`), so test 9 compares status, code and message, not the whole body. `SecurityConfig` (`security/SecurityConfig.java`) sets no header policy, so Spring Security's default `Cache-Control` headers already apply; test 12 asserts the header is **unchanged by this release**, not absent. The H2 integration class builds its own schema (`initSchema`), so foreign-key and `ON DELETE` behaviour needs the PostgreSQL tests.

1. `unpublishingGoal_makesChildUnreadableAndUnadoptableById` [MockMvc real request, full context]: anonymous
   `GET /collections/public/{childId}` → 404; ROLE_USER `POST /collections/{childId}/adopt` → 404.
2. `unpublishingGoal_cascadesPublicChildrenToPrivateAndKeepsStamps` [unit, replaces :4341]: children PRIVATE,
   `published_at` and item stamps unchanged, `last_update_published_at` unchanged.
3. `deletingSourceGoal_formerChildrenAreNotListedNorReadable` [real PostgreSQL]: after delete, rows survive with
   null parent and PRIVATE; absent from `listPublic`; `getPublic` 404.
4. `deletingAdoptedGoal_doesNotTouchChildren` [unit]: `sourcePlanId != null` → no visibility writes.
5. `updateVisibility_isForbiddenForUserAndUnauthorizedForAnonymous` [MockMvc real request, full context]:
   `POST /collections/{id}/visibility` with `.contentType(APPLICATION_JSON)` and body `{"visibility":"PRIVATE"}`:
   ROLE_USER → 403, anonymous → 401 (CONFIRMED: `SecurityConfig.java:38-40` wires `ApiAuthenticationEntryPoint`,
   which sets `SC_UNAUTHORIZED`, `ApiAuthenticationEntryPoint.java:29`). A direct controller call proves nothing
   (repo `CLAUDE.md` lesson).
6. `listPublic_countsOnlyCurrentlyPublicNotes` [real PostgreSQL]: make one note PRIVATE; card `itemCount`
   equals `getPublic(...).items().size()`; one note-projection query per call (SQL capture: `SqlCaptureStatementInspector` is registered in `NoteCollectionServiceProjectionIntegrationTest:31,61`, which is H2; `GoalAdoptionPostgresIntegrationTest` does NOT register it, so the PostgreSQL test must).
7. **`aleShapedGoal_privateStampedChildStillIncludedButNotDirectlyReadable`** [real PostgreSQL]: a PUBLIC Goal
   with one PUBLIC and one PRIVATE-but-stamped child: `getPublic(goal)` includes both children's items;
   `listPublic` counts include both; `adoptGoal` copies both (extends :159
   `stampedPrivateChildIsAdoptedTheSameAsAPublicOne`); `getPublic(privateChildId)` → 404.
8. `republishingGoal_stillFlipsEveryChildPublic` [unit, keeps `…publishingGoalCascadesPublicVisibilityToChildren`
   :4310]: pins the unchanged **PRIVATE to PUBLIC** flip so nobody "fixes" it silently (PUBLIC to PUBLIC is test 15).
9. `withdrawnAndNeverExisted_returnIdenticalNotFoundBody` [MockMvc real request]: same status, error code and message (not the whole body: it carries a per-request `requestId`).
10. `unpublishAndDelete_leaveAdoptedCopiesAndTheirNotesUntouched` [real PostgreSQL].
11. `sourceUpdate_detachesWhenSourceParentIsNotPublic` [unit].
12. Guard: no new controller mapping, and the `Cache-Control` header on `/collections/public/**` is unchanged by this release [MockMvc header assertion against the pre-change value; Spring Security's default header already applies].
13. `ownerList_readyCountsUnchangedForAdoptedCopyWithPrivateNotes` [real PostgreSQL]: the authenticated `list()`
    counts for a collection holding PRIVATE notes are identical before and after the change (2.4 trap).
14. `detachingPublicChild_setsItPrivateAndKeepsStamps` [unit + real PostgreSQL]: detach a PUBLIC term-less child under a PUBLIC Goal, and under a PRIVATE Goal: PRIVATE, absent from `listPublic`, `getPublic` 404, stamps unchanged; detaching a PRIVATE child and re-parenting are unchanged; an adopter's source-update reads RETIRED, not MOVED.
15. `repeatPublishOnPublicGoal_isIdempotentNoOp` [unit + real PostgreSQL] (P1): a PUBLIC Goal with PUBLIC sections and PRIVATE-but-stamped sections (the ALE shape), then `PUBLIC` again: success; no section's `visibility`, `published_at` or item stamp changes; `last_update_published_at` unchanged; no `saveAll`; the Goal's `updatedAt` unchanged (if the brief adopts the no-touch choice); `listPublic` order unchanged; the private-stamped section still 404s by its own id.
16. `repeatPublish_stillEnforcesAuthorizationAndValidation` [unit + MockMvc real request]: ROLE_USER 403 and anonymous 401 (reuse test 5's harness, `.contentType(APPLICATION_JSON)` and body); an already-public Goal whose section now holds a PRIVATE note answers `CollectionNotPublishableException` and writes nothing.
17. `detachedSection_canBeRepublishedAsStandalone` [real PostgreSQL] (P2): detach a PUBLIC section (PRIVATE, stamps kept), then `PUBLIC` through `updateVisibility` as a root: it lists as a standalone Official-owner collection and `getPublic` answers; detach alone never published anything.
18. `publishUpdate_stampsNewPrivateSectionWithoutChangingVisibility` [real PostgreSQL] (guard for 2.2b): add a section to a public Goal, run `publishReviewSetUpdate`: the section is stamped and still PRIVATE, appears in `getPublic(goal)`, 404s by its own id. Pins that Option A leaves the Publish update flow untouched.
19. `withdrawingSource_leavesLearnerCopiesNotesAndSessionsUntouched` [real PostgreSQL] (P2; extends test 10): after unpublish, delete and detach of source sections, an adopter's copy rows, copied notes and quiz sessions are byte-identical, and `applySourceUpdate` adds nothing for the withdrawn section and removes nothing.
20. `attachingStampedSectionToPublicGoal_isIncludedWithoutPublishUpdate` [real PostgreSQL] (U5 characterization): a stamped PRIVATE collection re-parented under a PUBLIC Goal appears in `getPublic(goal)` and is adoptable via `adoptGoal`, and 404s by its own id. **Documents current behaviour so nobody changes or relies on it silently; it is not an endorsement.**
21. `adoptGoal_returns404ForChildOfNonPublicParent_evenWhenLearnerHoldsACopy` [unit] (2.1): learner holds a copy with `source_plan_id` = a PUBLIC child whose parent is PRIVATE: 404, not `existingGoalJobResponse`.
22. `sourceUpdate_publicChildUnderPrivateParent_readsRetiredAndApplyRechecks` [unit] (2.1, 2.6): the relocation lookup (Svc:2657-2668) answers RETIRED, not MOVED, for a PUBLIC child under a non-public parent, and the `applySourceUpdate` post-pass re-check (Svc:1523) uses the same helper. Test 14 reaches RETIRED only through PRIVATE visibility.
23. `deletingPrivateGoalWithPublicChildren_setsThemPrivate` [real PostgreSQL] (2.3) and `repeatPublish_onPublicLeaf_isNoOp` [unit] (2.2b).
24. `publicDetail_parentCollectionIdIsNull` [unit] (2.5).
Tests that pin current behaviour and must change: **three, not one (cold review, 2026-11-14):** `:4341` (`updateVisibility_unpublishingGoalDoesNotCascadeToChildren`), and `:3942` (`detailMappersExposeAcademicTermForOwnedAndAnonymousChildReads`) and `:4432` (`adopt_carriesTermFromParentedSourceChildOntoStandaloneCopy`), which read or adopt a PUBLIC child with an unstubbed parent id so Mockito returns `Optional.empty()` and the new ancestor check would hit not-found: stub the parent lookup in both (state so in the release). **Also change (cold review 2026-11-16, CONFIRMED by reading; supersedes the earlier "must stay green" for :3834 and :3887):** the three `listPublic` count tests `:3731` (`listPublic_filtersByNormalizedCourseProgramAndReturnsPublicPlansOnly`), `:3834` (`listPublic_rollsUpGoalItemAndReadyCountsFromCascadePublishedChildren`) and `:3887` (`listPublic_excludesUnpublishedAdditionsFromEveryPublicSummaryCount`). `buildNote` defaults notes to PRIVATE (`NoteCollectionServiceTest.java:6683`), so a PUBLIC-note filter (2.4) drops their asserted counts to 0, and :3731 stubs no note projection at all. Fix the fixtures (set the notes PUBLIC and stub `findCollectionNoteProjectionsByIdIn`); **do not** make them pass by changing the shared `readyCountsForNoteIds` (the 2.4 trap). And `:5210` (`sourceUpdate_survivesANullPositionBaselineWhenTheCuratorNestsAPreviouslyStandalonePlan`): its source's new parent is unstubbed (`:5220`), so the ancestor lookup returns empty and the inspection would take the detached branch, silently no longer reaching the null-baseline path it guards: stub the parent.
Must stay green:
:4310, :5885 `adoptGoal_snapshotsStampedPrivateChildrenForTheJob`, :5903, :5924, :5941, :4027.

## 4. Owner-run READ-ONLY pre-checks (SELECT only; re-run at release time, snapshots decay)

Claude may run these read-only; the owner may too. **Any deviation from EXPECTED is STOP and report, never an
auto-fix** (N9 is the one exception: see its comment). Any repair is an owner-run write in a separate `docs/claude-plans/*.sql`, never run by Claude.

```sql
-- P1 PUBLIC child whose parent is not PUBLIC. EXPECTED 0
SELECT count(*) FROM note_collections c JOIN note_collections p ON p.id = c.parent_collection_id
WHERE c.visibility = 'PUBLIC' AND p.visibility <> 'PUBLIC';
-- P2 PUBLIC collections owned by a non-ADMIN. EXPECTED 0
SELECT count(*) FROM note_collections c JOIN users u ON u.id = c.owner_user_id
WHERE c.visibility = 'PUBLIC' AND u.role <> 'ADMIN';
-- P2b PUBLIC adopted copies. EXPECTED 0
SELECT count(*) FROM note_collections WHERE visibility = 'PUBLIC' AND source_plan_id IS NOT NULL;
-- P3 roster of PUBLIC top-level collections. EXPECTED exactly the 8 known Official roots (CELE, CPALE, ALE, LET, BSCS, BSIT, BSIS, PNLE), all owned by an ADMIN. Any extra row is STOP.
-- (Replaces the old marker check, which missed a child promoted by detach because updateParent clears sibling_position and term_label.)
SELECT c.id, c.title, u.role AS owner_role FROM note_collections c JOIN users u ON u.id = c.owner_user_id
WHERE c.visibility = 'PUBLIC' AND c.parent_collection_id IS NULL ORDER BY c.title;
-- P6 children whose owner differs from their parent's owner. EXPECTED 0
SELECT count(*) FROM note_collections c JOIN note_collections p ON p.id = c.parent_collection_id
WHERE c.owner_user_id <> p.owner_user_id;
-- Totals. EXPECTED 8 roots, 60 children
SELECT count(*) FILTER (WHERE parent_collection_id IS NULL) AS public_roots,
       count(*) FILTER (WHERE parent_collection_id IS NOT NULL) AS public_children
FROM note_collections WHERE visibility = 'PUBLIC';
-- P5 published items in PUBLIC collections whose note is not PUBLIC. EXPECTED 0
SELECT count(*) FROM note_collection_items i JOIN note_collections c ON c.id = i.collection_id
JOIN notes n ON n.id = i.note_id
WHERE c.visibility = 'PUBLIC' AND i.published_at IS NOT NULL AND n.visibility <> 'PUBLIC';
-- P5b same, for stamped children of PUBLIC roots (any child visibility). EXPECTED 0
SELECT count(*) FROM note_collection_items i JOIN note_collections c ON c.id = i.collection_id
JOIN note_collections p ON p.id = c.parent_collection_id JOIN notes n ON n.id = i.note_id
WHERE p.visibility = 'PUBLIC' AND c.published_at IS NOT NULL AND i.published_at IS NOT NULL
  AND n.visibility <> 'PUBLIC';
-- N9 PRIVATE-but-stamped sections under PUBLIC roots. EXPECTED 9 rows at 2026-11-14: ALE 7 (54,38,42,48,41,31,52 = 306), PNLE 2 (9,25 = 34).
-- NOT a STOP by itself if higher: a Publish update after 2026-11-14 legitimately adds a row (section 9, U2). Record the pre-deploy count; the post-deploy count must EQUAL it.
SELECT p.title AS root, c.title AS section, count(i.id) AS stamped_items
FROM note_collections c JOIN note_collections p ON p.id = c.parent_collection_id
LEFT JOIN note_collection_items i ON i.collection_id = c.id AND i.published_at IS NOT NULL
WHERE p.visibility = 'PUBLIC' AND c.visibility = 'PRIVATE' AND c.published_at IS NOT NULL
GROUP BY p.title, c.title, c.id ORDER BY p.title, c.title;  -- c.id so two same-titled sections cannot merge into one row (cold review 2026-11-16)
```
Enum columns are stored as strings (CONFIRMED `@Enumerated(EnumType.STRING)`: `UserEntity.java:156`,
`NoteEntity.java:60`, `NoteCollectionEntity.java:40`), so the literals above match.
```sql
-- T stamped items across the PUBLIC roots and their stamped children (any child visibility). EXPECTED 2393
SELECT count(*) FROM note_collection_items i JOIN note_collections c ON c.id = i.collection_id
LEFT JOIN note_collections p ON p.id = c.parent_collection_id
WHERE i.published_at IS NOT NULL
  AND ((c.parent_collection_id IS NULL AND c.visibility = 'PUBLIC')
    OR (p.visibility = 'PUBLIC' AND c.published_at IS NOT NULL));
```
**Post-deploy re-run (read-only):** all of the above again; every value must equal the pre-deploy value (the
deploy writes nothing).

## 5. Verification tier and review
- `advisor()` **before** the Codex brief is written (repo `CLAUDE.md` rule), and again on the diff.
- One scoped cold agent (`model: "sonnet"`), framed as **falsification** of: (a) no public read/adopt/source-update
  path reaches a child of a non-PUBLIC parent; (b) the nine-section shape is unchanged on every Goal path;
  (c) no stamp or `last_update_published_at` is written by unpublish or delete; (d) `listPublic` adds no query;
  (e) adopted copies are untouched. Hand it the Svc line list above and the test list; ask it to name any added
  file with no test that executes it.
- Real-request tests at every tier (tests 1, 5, 9).

## 6. Release, deploy and rollback inputs (not approvals)
- Backend only, so the merge **redeploys Render**. Its own deploy, **≥ 24 h from any other deploy**, and ≥ 24 h after any non-deploy kill (the `v0.180.0` waiver is not reusable; CONFIRMED `CLAUDE.md:43`). **State re-read 2026-11-14 (Render deploy list, a snapshot):** `v0.181.0`'s frontend merge deployed 2026-11-12T13:29:37Z, and a **manual Render deploy `dep-dbri0sbtqb8s73ekjrg0` went live 2026-11-13T14:37:48Z**, consistent with the owner's report that the Philippine price environment save is done (I did not read the variable). Both are history, not planned actions; the 24 h clock now runs from the next deploy, whichever it is. Per `CLAUDE.md` as read 2026-11-16, `v0.180.0`'s +24 h read met its criterion and its +72 h read PASSED on 2026-11-14 and was confirmed by the owner (I did not re-read the underlying reports; the Render connection failed in this session). Any merge to `main` still needs the owner's explicit approval.
- **It is a two-platform event, not Render-only:** every merge to `main` rebuilds Vercel production with
  unchanged frontend code and moves the one-step rollback target (CONFIRMED `CLAUDE.md:43`); a release that bumps
  `frontend/package.json` starts a Vercel build on its own. So Phase 0 must **not merge while `v0.181.0`'s
  frontend could still need a one-step Vercel rollback** (i.e. inside its watch window).
- Separate explicit owner approval; the section-5 checklist: verified backup, attended watch, watch and stop
  conditions written before T0, off-peak per the deploy plans, the clean-window rule.
- Merge shape: a squash merge (one commit) and the per-commit range check that the pushed range is what was
  reviewed; run `scripts/check-deploys.sh` after.
- **Watch:** 404s on `/collections/public/{id}` for the 8 known roots must not rise (diagnostics
  `PUBLIC_API_DIAGNOSTICS` minute lines; path constant `PublicApiDiagnosticsFilter.java:25`); no signal 9; DB
  memory vs the hard stop (above 260 MB of 268.4 MB at two consecutive reads, CONFIRMED
  `2026-11-10-v0180-deploy-plan.md` section 6, :156); 5xx and connections per the same section.
- **Rollback:** revert the commit (another Render deploy, same rules). No data to undo, because Phase 0 writes
  visibility only when a curator unpublishes or deletes; any child set PRIVATE meanwhile stays PRIVATE after a
  revert and is restored only by an owner-reviewed action (never an ALE/PNLE republish).

## 7. Risks
- **Too-strict check hides a legitimate root:** mitigated by keying only on a non-null parent; test 7 and the
  post-deploy P-checks.
- **Republish flip (C8):** unchanged behaviour, now more likely to be exercised; R5 guardrail and test 8.
- **Stale tests:** :4341 must change; a test still asserting "no cascade" means the change did not land.
- **Owner-scoped child query misses a child:** closed (2.3 CONFIRMED by construction and by data); the P3 roster and P6 detect any promoted or mismatched row.
- **Adopters' update reports change** for a detached child (RETIRED instead of MOVED, 2.6): intended; say so in the release.
- **Repeat PUBLIC flips every child:** closed by 2.2b (P1). **Unpublish-then-republish still flips every child PUBLIC:** open, guardrail only (section 9, U1).
- **Publish update keeps producing PRIVATE-but-stamped sections** (U2): the nine are not a closed set; N9 and the roster expectations are written to tolerate growth.
- **Tests:** seven existing tests change (:4341, :3942, :4432, :3731, :3834, :3887, :5210; section 3), plus the new overload for the `listPublic` count filter (2.4), whose authenticated path must stay byte-identical (test 13).

## 8. Open items (closed 2026-11-14 unless noted; evidence in `docs/claude-plans/2026-11-14-phase0-open-items-verification.md`)
1. `applySourceUpdate` re-check and the MOVED/RETIRED relocation lookup: **include**: reuse the ancestor helper so a child under a non-public parent reads RETIRED. A withdrawn Goal is already detached before the relocation branch is reached.
2. `inspectSourceUpdate`: the **ancestor check stays in 2.1 (with test 11)**; aligning the published-stamp check on a child source stays out of scope (no live population: 0 learners adopted a standalone child plan; all 405 child-source copies sit inside adopted Goals).
3. Children share the Goal owner: **closed** (2.3).
4. A curator can unpublish a single child: **CONFIRMED possible** through the API (no guard in `updateVisibility`) and the admin "Publish settings" control renders on any collection page; Phase 0 must not change child-level toggling (the nine legacy sections may come from it). The child-page modal flow was not read.
5. `SqlCaptureStatementInspector`: **exists** (`backend/src/test/java/com/studysnap/backend/testutil/`, used by `NativeQueryPostgresIntegrationTest` and `QuickReviewSessionRepositoryTest`); it is ALREADY registered in `NoteCollectionServiceProjectionIntegrationTest` (:31, :61, H2); `GoalAdoptionPostgresIntegrationTest` does not register it (corrected 2026-11-16). Also: `NoteCollectionServiceProjectionIntegrationTest` is H2 but has `@AutoConfigureMockMvc` with the full context (:61-62), so the 401/403 real-request test can live there.
6. **CLOSED 2026-11-14 (Product UX Decision 1):** option (g) is approved as Option A, specified in 2.2b.
7. **OPEN, owner sequencing question:** `docs/claude-plans/2026-11-14-phase0-open-items-verification.md` said the Preview spike precedes the Phase 0 brief; the plan lists Phase 0 as "Dependencies: none" and the spike result gates only the public page (Phase 2b). No Phase 0 code depends on it. Whether to run the spike first is the owner's call.
8. **CLOSED 2026-11-14 (Product UX, second consult, section 9.6, F2):** no frontend copy in Phase 0; the future update-report strings are recorded.

## 9. Product UX decision record (2026-11-14; planning only)

Recorded from Product UX's reply "Study Plan Discovery Phase 0, APPROVED WITH CONDITIONS". It approves decisions and planning-document updates only: **no implementation, code change, production-data write, migration, merge, deployment, environment change, Preview spike execution, release version or schedule is authorized.** Engineering and release go/no-go stay with the owner.

### 9.1 The four decisions
| # | Decision | Where it lands |
|---|---|---|
| P1 | **Option A, idempotent no-op** for a repeat Publish on an already-PUBLIC Goal. Publishing a PRIVATE Goal stays the explicit first-publication action. Authorization and validation still enforced. A new section on an already-public Goal is published through its own explicit control. Option C rejected. | 2.2b; tests 15, 16, 18 |
| P2 | **Private-on-detach with preservation safeguards.** Detach never publishes; a detached section can be republished deliberately as standalone; learner copies, notes and progress are preserved; learner-facing copy **refined in the second consult (9.6, F2)**: "This section is no longer available." is **not** approved for any current learner-facing screen. | 2.6, 2.7; tests 14, 17, 19; 9.6 |
| P3 | **Order: Phase 0, Phase 1a, public collection page, Explore improvements,** keeping the approved phased dependencies. Production database reliability stays an overriding deployment consideration; the interactive migration keeps its own gates; the price change is deployed (not future); no slot or date assigned. | plan section F placement and Order paragraph |
| P4 | **Genuine 404 preferred; HTTP 200 plus `noindex` accepted as a documented fallback** with fail-closed, no-leak, crawler-visible `noindex`, and analytics safeguards. | plan, Phase 2b "Unavailable-page spec" |

### 9.2 Acceptance criteria added to Phase 0
- A repeat PUBLIC request on an already-PUBLIC Goal changes no child `visibility`, no `published_at` (collection or item) and no `last_update_published_at` (test 15), and still enforces authorization and validation (test 16).
- Publishing a PRIVATE Goal behaves as before (test 8, :4310 unchanged).
- Detaching a PUBLIC section makes it PRIVATE, keeps every stamp, and is never a publication action; it can be republished as a standalone collection (tests 14, 17).
- Unpublish, delete and detach leave adopter copies, copied notes, quiz sessions and `applySourceUpdate` additivity unchanged (tests 10, 19).
- Direct-id reads and adoption respect the parent boundary (tests 1, 9, 11); the nine PRIVATE-but-stamped sections keep their shape on every Goal path (test 7); the new-section Publish update flow is pinned (test 18).
- No production data is written, republished, reconciled or rewritten by Phase 0 (N9 and the roster are read-only, pre- and post-deploy equal).
- **Phase 0 changes no frontend copy or string** (9.6, F2): the diff touches no path under `frontend/`, and the adopter update report keeps its current wording (`collection-detail-page-client.tsx:700-702`, `:777-784`) until a separate frontend change.
- A direct public link to a withdrawn section or collection answers the same not-found as a never-existed id (tests 1, 9); it is never a different page and never says "This section is no longer available."

### 9.3 Confirmations Product UX asked for
- **Legitimate standalone collections remain supported.** The ancestor check keys only on a non-null parent id (2.1); a root passes. The 8 PUBLIC Official roots are ADMIN-owned roots and are untouched. Detach changes only the act of detaching. A standalone can be published through the existing control (test 17).
- **Existing learner copies remain supported and unchanged** (2.7, tests 10 and 19). No decision on copied content is needed because none is affected.

### 9.4 Unresolved interactions
- **U1 (the nine, owner decision): Option A does not close unpublish-then-republish.** With the Phase 0 unpublish cascade, unpublishing a Goal sets its PUBLIC children PRIVATE, and republishing (PRIVATE to PUBLIC, the explicit first-publication contract Product UX preserved) flips **every** child PUBLIC, including the nine. The R5 guardrail (never unpublish and republish ALE or PNLE) therefore **stays in force for that route**. `validateGoalPublishable` validates every child (Svc:2036-2046), so one empty child, a child holding a PRIVATE note or incoherent terms would block a republish, but the nine are valid and are not protected by that. Closing it would change the first-publication contract (for example, skipping stamped PRIVATE children on a republish of a Goal that was ever published). I have **not** proposed or specified that; it is an explicit decision for Product UX and the owner, per the instruction not to resolve a legacy-data conflict through broader visibility changes.
- **U2 (the nine are not closed; E4's cause corrected): "Publish update" produces the same shape.** Read-only production check 2026-11-14: ALE's seven carry `published_at = created_at` and predate V141 (2026-09-08T12:23:16Z), the backfill shape; **PNLE's two were created 2026-09-10 (after V141) and stamped together at 2026-09-14T13:41:29.361368Z, exactly the PNLE root's `last_update_published_at`**, i.e. by a Publish update. `publishUnpublishedReviewSetCollections` has no visibility predicate (`NoteCollectionRepository.java:177-188`), so every section a curator adds to a public Goal and publishes through Publish update is stamped and stays PRIVATE until someone also uses its own control. **"Legacy" labels today's nine, not a closed population,** and 2.6's earlier "cannot create a tenth" line is corrected above. Phase 0 is unaffected (the ancestor check keys on the parent and 2.2b leaves Publish update alone), but R5's wording "nine legacy sections" and the pre-checks assumed a closed set. Does Product UX or the owner want new sections to become PUBLIC at Publish update time, or keep the stamp-decides contract? Not decided here. **Corrected 2026-11-16: Publish update is not the only route; see U5.**
- **U3 (copy surface): RESOLVED 2026-11-14 (9.6, F2).** Original question, kept for the record: the new "no longer available" copy had no home in a backend-only Phase 0. Today the adopter's update report says "{topic} was retired from the Official Review Set" and "N topics were retired from the Official Review Set" (`collection-detail-page-client.tsx:700-702`, `:777-784`), with "A topic" as the fallback for a section-level row. Product UX's sentence ("This section is no longer available.") is a frontend string and Phase 0's non-goals say no UI change. Options: keep Phase 0 backend-only and carry the sentence to a frontend phase once Product UX names the surface; or add a frontend copy change to Phase 0 (it then needs a Vercel deploy as well and loses its backend-only shape). **Recommendation: the first.** Product UX chose the first (Option A) with refined copy.
- **U5 (re-attachment is a publication route; owner and Product UX decision, found by the 2026-11-16 cold review):** a section's stamp survives detach (2.6), and `updateParent` attaches any owned collection, stamped or not, under a PUBLIC Goal with no visibility or stamp check (2.2b (iii)). So **re-attaching a detached section, or attaching any stamped collection, publishes it into the Goal's public view and adoption immediately, with no Publish update and no notification.** Product UX's "detachment itself never constitutes a publication action" holds for detach, but not for the re-attach that follows. Phase 0 as scoped changes none of this (it is existing behaviour; test 20 pins it). Options, none specified: (a) accept and document; (b) clear the stamp on detach so a re-attach needs Publish update (changes the stamp-keeping rule in 2.6, which Product UX did not approve); (c) refuse to attach a stamped collection to a PUBLIC Goal. Not decided here.
- **U4 (a proposal to confirm, not a conflict):** 2.2b recommends no `touch`/`save` on the no-op so Explore's `updatedAt` ordering does not move.

### 9.5 Readiness
**The Phase 0 change set (2.1 to 2.7, tests 1 to 24, the read-only pre-checks) is implementation-ready as input to a Release Implementor brief.** Remaining owner approvals, none given: a release slot and kickoff (none allocated); separate explicit approval and the section-5 checklist for its own Render deploy (also a Vercel rebuild); the owner's view on U1 (guardrail only, or a contract change); and the spike-versus-brief sequencing question (section 8, item 7). Nothing here approves any of them. The 2026-11-16 cold review added U5 (an owner and Product UX decision), seven changed existing tests, and tests 20 to 24.

### 9.6 Second consult, 2026-11-14 (Product UX, APPROVED, planning only)
Two follow-up copy decisions. They authorize no implementation, release assignment, merge, deployment or production change.
- **F1, unavailable public page: Option A.** One fixed heading for every unavailable, nonexistent or unverifiable public collection: **"This study plan isn't available."** Keep the approved supporting copy and the "Explore collections" CTA to `/explore`. **Do not vary the heading, metadata or any other identifying content by the withdrawn collection's former type.** Recorded in the plan, Phase 2b item 4a. This closes the noun conflict raised in 9.4.
- **F2, withdrawn section: Option A with refined copy.** Phase 0 stays backend-only and makes **no frontend copy change now.** For a **future** Official Review Set update-report improvement (no phase or release assigned) use:
  - Singular: "This section is no longer included in the Official Review Set. Your copy and study progress are safe."
  - Plural: "{N} sections are no longer included in the Official Review Set. Your copies and study progress are safe."
  - **Condition:** these strings apply only where the learner's existing copy and progress are **verified** to remain intact; otherwise the case returns for a separate product decision. Verified for source withdrawal, delete and detach as of 2026-11-14 (2.7); the future change must **re-verify against the then-current code** and must not assume this paragraph still holds.
  - **"This section is no longer available." is NOT approved for any current learner-facing screen.** Treat it as referring to the withdrawn public source only. Direct public links keep using the generic unavailable page (F1).

### 9.7 Cold falsification of this delta (independent Opus agent, 2026-11-16; read-only, code at `origin/main` `a928029b`)
Nine claims tried. **Held:** C1 (the repeat-PUBLIC no-op breaks no caller: the only frontend caller sends PUBLIC from the private branch, `collection-detail-page-client.tsx:2840`; the controller is the only backend caller), C3 (withdrawal writes nothing to learner copies, notes or sessions), C4 (detach and Goal-delete behaviour), C5 (the republish round trip flips every child), C6 (no unlisted public read of a child; `PostSessionNextStepService:285` and `findOfficialReviewSetRootForNote` are root-only and covered by 2.3 and 2.6), C9 (the `listPublic` count filter can stay local). **Failed:** C2 (a third route, re-parenting: U5). **Partial:** C7 (tests: the corrections in section 3) and C8 (N9 grouping: fixed). Also corrected: the `adoptGoal` claim (2.1), the SqlCapture registration claim, the `standaloneSetup` citation, and the four added test families (20 to 24) for behaviour the first 19 did not execute. **Not verifiable by the agent (needs production data or other sources):** the production counts, the PNLE and ALE stamp timestamps, the Render deploy ids and times, `v0.180.0`'s +72 h read, and `study-plan-builder-page-client.tsx:2171`.
