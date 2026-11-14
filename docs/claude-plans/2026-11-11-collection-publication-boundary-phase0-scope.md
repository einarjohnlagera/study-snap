# Phase 0 scope: collection publication-boundary fail-closed fix (implementation-ready, planning only)

**Status:** PROPOSAL, planning only. Owner R1-R6 FINAL 2026-11-11. Nothing here authorizes implementation, a
merge, a deploy, a migration, an env change or a production-data write. No version number or date allocated.
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
- **The nine legacy PRIVATE-but-stamped sections (ALE 7, PNLE 2) keep appearing exactly as today** inside their
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
    non-public parent is a 404 here too. Today such an id already fails at Svc:1165-1166 (a child has no stamped
    children), so this is defence in depth, not a behaviour change for any current row.
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
  After this change an unpublish/republish round trip of ALE or PNLE would flip the nine PUBLIC. R5 forbids it, and the cold review (2026-11-14) widened the guardrail: **any** PUBLIC visibility request, even on an already-public Goal, runs `publishChildCollections` (Svc:1067-1068, no `previousVisibility` guard), so never send one to the ALE or PNLE roots. **Option (g), OWNER DECISION, not included by default:** skip the child cascade when the Goal is already PUBLIC; it closes the accidental-flip route in code but changes API behaviour. Also (low): `updateVisibility` and `delete` take no row lock while `publishReviewSetUpdate` locks the root `FOR UPDATE` (Svc:802); an unpublish queued behind a publish can still send update notifications after commit (Svc:822). Accept and record, or add the lock.

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
- **Builder UI:** it never detaches (it creates a collection and attaches it, `study-plan-builder-page-client.tsx:2171`), so this cannot create a tenth PRIVATE-but-stamped section through the UI.

## 3. Acceptance tests (named; layer in brackets)

Real-request rule: `NoteCollectionControllerTest` uses `standaloneSetup` (CONFIRMED :67) and checks
`@PreAuthorize` by reflection (:89-108), so it cannot prove a 403. Security tests need a full context with
`@AutoConfigureMockMvc` (pattern: `NoteCollectionServiceProjectionIntegrationTest`, :61-64). That class runs on
**H2** with Flyway disabled (`backend/src/test/resources/application.yaml:3`, `:9-10`), so real-PostgreSQL tests
follow `GoalAdoptionPostgresIntegrationTest` (Testcontainers, Flyway on, :28-33), which also exercises the real
`ON DELETE SET NULL`.

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
   equals `getPublic(...).items().size()`; one note-projection query per call (SQL capture, ASSUMPTION usable).
7. **`aleShapedGoal_privateStampedChildStillIncludedButNotDirectlyReadable`** [real PostgreSQL]: a PUBLIC Goal
   with one PUBLIC and one PRIVATE-but-stamped child: `getPublic(goal)` includes both children's items;
   `listPublic` counts include both; `adoptGoal` copies both (extends :159
   `stampedPrivateChildIsAdoptedTheSameAsAPublicOne`); `getPublic(privateChildId)` → 404.
8. `republishingGoal_stillFlipsEveryChildPublic` [unit, keeps `…publishingGoalCascadesPublicVisibilityToChildren`
   :4310]: pins the unchanged flip so nobody "fixes" it silently.
9. `withdrawnAndNeverExisted_returnIdenticalNotFoundBody` [MockMvc real request]: same status and body.
10. `unpublishAndDelete_leaveAdoptedCopiesAndTheirNotesUntouched` [real PostgreSQL].
11. `sourceUpdate_detachesWhenSourceParentIsNotPublic` [unit].
12. Guard: no new controller mapping, no `Cache-Control` header on `/collections/public/**` [MockMvc header
    assertion].
13. `ownerList_readyCountsUnchangedForAdoptedCopyWithPrivateNotes` [real PostgreSQL]: the authenticated `list()`
    counts for a collection holding PRIVATE notes are identical before and after the change (2.4 trap).
14. `detachingPublicChild_setsItPrivateAndKeepsStamps` [unit + real PostgreSQL]: detach a PUBLIC term-less child under a PUBLIC Goal, and under a PRIVATE Goal: PRIVATE, absent from `listPublic`, `getPublic` 404, stamps unchanged; detaching a PRIVATE child and re-parenting are unchanged; an adopter's source-update reads RETIRED, not MOVED.
Tests that pin current behaviour and must change: **three, not one (cold review, 2026-11-14):** `:4341` (`updateVisibility_unpublishingGoalDoesNotCascadeToChildren`), and `:3942` (`detailMappersExposeAcademicTermForOwnedAndAnonymousChildReads`) and `:4432` (`adopt_carriesTermFromParentedSourceChildOntoStandaloneCopy`), which read or adopt a PUBLIC child with an unstubbed parent id so Mockito returns `Optional.empty()` and the new ancestor check would hit not-found: stub the parent lookup in both (state so in the release). Must stay green:
:4310, :5885 `adoptGoal_snapshotsStampedPrivateChildrenForTheJob`, :5903, :5924, :5941, :3834, :3887, :4027.

## 4. Owner-run READ-ONLY pre-checks (SELECT only; re-run at release time, snapshots decay)

Claude may run these read-only; the owner may too. **Any deviation from EXPECTED is STOP and report, never an
auto-fix.** Any repair is an owner-run write in a separate `docs/claude-plans/*.sql`, never run by Claude.

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
-- N9 the nine legacy sections. EXPECTED 9 rows: ALE 7 (54,38,42,48,41,31,52 = 306), PNLE 2 (9,25 = 34)
SELECT p.title AS root, c.title AS section, count(i.id) AS stamped_items
FROM note_collections c JOIN note_collections p ON p.id = c.parent_collection_id
LEFT JOIN note_collection_items i ON i.collection_id = c.id AND i.published_at IS NOT NULL
WHERE p.visibility = 'PUBLIC' AND c.visibility = 'PRIVATE' AND c.published_at IS NOT NULL
GROUP BY p.title, c.title ORDER BY p.title, c.title;
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
- Backend only, so the merge **redeploys Render**. Its own deploy, **≥ 24 h from any other deploy, including
  `v0.181.0`'s frontend merge and its env save**, and ≥ 24 h after any non-deploy kill (the `v0.180.0` waiver is
  not reusable; CONFIRMED `CLAUDE.md:43`). No merge to `main` before `v0.180.0`'s +24 h read.
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
- **Any PUBLIC request flips every child** (2.2): guardrail, and option (g) is the owner's call.
- **Tests:** three existing tests change (section 3), plus the new overload for the `listPublic` count filter (2.4), whose authenticated path must stay byte-identical (test 13).

## 8. Open items (closed 2026-11-14 unless noted; evidence in `docs/claude-plans/2026-11-14-phase0-open-items-verification.md`)
1. `applySourceUpdate` re-check and the MOVED/RETIRED relocation lookup: **include**: reuse the ancestor helper so a child under a non-public parent reads RETIRED. A withdrawn Goal is already detached before the relocation branch is reached.
2. `inspectSourceUpdate`: the **ancestor check stays in 2.1 (with test 11)**; aligning the published-stamp check on a child source stays out of scope (no live population: 0 learners adopted a standalone child plan; all 405 child-source copies sit inside adopted Goals).
3. Children share the Goal owner: **closed** (2.3).
4. A curator can unpublish a single child: **CONFIRMED possible** through the API (no guard in `updateVisibility`) and the admin "Publish settings" control renders on any collection page; Phase 0 must not change child-level toggling (the nine legacy sections may come from it). The child-page modal flow was not read.
5. `SqlCaptureStatementInspector`: **exists** (`backend/src/test/java/com/studysnap/backend/testutil/`, used by `NativeQueryPostgresIntegrationTest` and `QuickReviewSessionRepositoryTest`); registering it in the service-level integration test is an ASSUMPTION. Also: `NoteCollectionServiceProjectionIntegrationTest` is H2 but has `@AutoConfigureMockMvc` with the full context (:61-62), so the 401/403 real-request test can live there.
6. **OPEN, owner decision:** option (g), the repeat-PUBLIC guard (2.2). Default: not included; the guardrail stands.
