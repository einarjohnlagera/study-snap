# Note + Study Pack Regeneration — Stage 1 Architecture & UX Audit

**Status:** STAGE 1 COMPLETE — **owner-ratified 2026-09-05. READY FOR IMPLEMENTATION-PROMPT DRAFTING.**
No implementation, no migrations, no quota changes, no code modified.
**Date:** 2026-09-05 (audit), revised 2026-09-05 (owner decisions §20 ratified).
**Method:** direct repo audit; every claim carries a `file:line` anchor.

**Owner decisions are ratified in §20 and are binding.** §20a carries the concurrency rule, which
required two further code proofs taken after the first draft.

---

## 1. Executive judgment

**The feature is substantially smaller than the brief assumes, and the reason is a single fact:
`NoteGenerationService.generateFromTopic` does not create a Note.** It returns
`GenerateNoteFromTopicResponse(generatedContent)` — a content string (`NoteGenerationService:63`).
The frontend saves it through the ordinary note-create path afterwards.

Three consequences:

1. **§38's headline prohibition — "do not delete and recreate the Note" — is not a risk to be
   mitigated. It never arises.** The topic-generation path has no note-creation coupling to break.
   §39.B.5 ("what assumptions currently require creation of a new Note?") answers: **none.**
2. **The reuse principle in §6 is already satisfiable, using a seam that already exists and is
   already used.** A 3-arg overload `generateFromTopic(request, userId, resolvedContext)` accepts a
   pre-resolved `StudyPackGenerationContext` (`:46-63`) and is already consumed by
   `NoteBulkGenerationService:310`. Regeneration resolves the context **from the existing note** via
   `StudyPackGenerationContextResolver.resolve(ownerUserId, note)` (`:41`) and passes it in.
3. **Every relationship in §3 survives automatically**, because in-place regeneration changes no
   identity. See §5.

**The genuinely new engineering problem is §24 — the partial-failure pairing invariant.** It has a
clean answer that requires no new state machine, no staging table and no versioning: the existing
async generator **already takes the note text as a parameter** rather than re-reading it
(`StudyPackService.generateStudyPackFromExistingNoteAsync(..., String normalizedText, ...)`, `:675`),
so both LLM calls can run before any write, and both artifacts commit in one transaction. See §10.

**Two owner assumptions are contradicted by the code** (§4 of the brief's own instruction to surface
these): the quota terminology in §2/§9/§23, and two entries in §3's preservation list. Both are
reported in §20.

**One real gap the brief does not name:** a shared quiz (`generated_quizzes`) survives Note
regeneration untouched, leaving a live share link serving questions built from replaced content —
the exact hazard `v0.110.2` shipped to close on the adjacent path. See §7.

**Recommendation: proceed.** The architecture is unusually well-suited to this feature.

---

## 2. Current Study Pack regeneration architecture (§39.A)

**A1 — Where initiated.** `frontend/components/notes/private-note-detail-page-client.tsx`. Menu
action → `handleOpenRegenerateConfirm` (`:1731`) → `AppModal` titled *"Regenerate Study Pack?"*
(`:3358-3380`) → `handleConfirmRegenerate` (`:1745`) → `handleGenerate`.

**A2 — Endpoint / service.** There is **no dedicated regeneration endpoint.** `handleGenerate` calls
`createStudyPackFromNote(note.id)` → `POST /notes/{id}/generate` (`api.ts`) →
`NoteController:210-220` → `studyPackService.startAsyncGenerationFromNote(id, userId,
autoApplyMetadata)`. **Regeneration and first generation are the same code path**, distinguished only
by whether a pack row already exists.

**A3/A4 — Records replaced or created.** `saveStudyPack` resolves the target as:

```java
StudyPackEntity entity = noteId == null
        ? new StudyPackEntity()
        : studyPackRepository.findByNoteId(noteId).orElseGet(StudyPackEntity::new);
```

So regeneration **mutates the existing row in place**: same `id`, same `created_at`, same
`owner_user_id`. `title`, `summary`, `keyConcepts`, `quiz`, `sourceText`, token counts, cost,
`model_used` and `updated_at` are all overwritten. **There is no versioning and no archive — the
previous Study Pack content is unrecoverable after regeneration.** This is deliberate and matches
`CLAUDE.md`'s versioning rule ("updates the existing Study Pack in-place so quiz/session history
stays linked").

**A5 — Quiz history preservation.** Preserved by **snapshot, not by reference.** Session questions
live in `quick_review_sessions.session_state` JSONB and are read back with
`QuizSessionStateUtils.extractQuiz(session.getSessionState())` (`:335`). A completed session
therefore carries its own copy of the questions and is unaffected by later pack mutation. See §12.

**A6 — Quota.** `userUsageService.incrementStudyPackGeneration(ownerUserId, now)` fires **inside
`saveStudyPack`, after successful generation, inside the commit transaction**. A generation failure
therefore charges nothing — an implicit rollback identical in shape to the one `v0.106.0` documented
for Board Exam.

**A7 — Failure.** The `catch` in `generateStudyPackFromExistingNoteAsync` calls
`markNoteGenerationFailed(noteId, ownerUserId)` in its own transaction. A **recovery interlock**
(`:695`) makes a late worker discard its result if the note is no longer `GENERATING`. Concurrent
starts are rejected with HTTP 409 `NOTE_GENERATION_IN_PROGRESS` (`:628-635`).

---

## 3. Current Note-from-topic architecture (§39.B)

**B1 — Where.** `NoteController:146-155` → `NoteGenerationService.generateFromTopic`.

**B2/B3 — Metadata consumed, and the resolver.** `resolveAuthoringContext` (`:65-98`) branches on
`CuratorAuthoringPredicate.isCurator(user)`:

- **curator** → validated catalog `courseProgramIds` + `domainContext`, and throws
  `MultiProgramDomainContextRequiredException` when 2+ programs carry a null Domain Context;
- **learner** → `courseProgramText` (request, else profile), throwing
  `CourseProgramSelectionRequiredException` when absent.

Both call `generationContextResolver.resolveForBulkGeneration(...)`. The result is a
`StudyPackGenerationContext` of exactly six fields: `learnerLevel`, `courseProgram` (a **single
string**), `subject`, `tags`, `domainContext`, `noteLearnerLevel`.

**B4 — Can it be reused for an existing Note? Yes, without modification to its prompt path.** The
3-arg overload takes a pre-resolved context, and
`StudyPackGenerationContextResolver.resolve(ownerUserId, note)` (`:41-63`) builds that context
**from a note entity** — note `domainContext`, note `subject`, note `tags`, note `learnerLevel`, and
`resolveCourseProgram(note, user)`.

**B5 — Assumptions requiring a new Note: none.** The method performs no note write of any kind.

**Quota timing.** `assertQuotaAvailable` before the LLM call; `recordUsage` **after** it (`:62`),
outside any transaction. This matters for §10.

---

## 4. Generation metadata contract (§39.E, §7)

| Field | Reaches the LLM? | Via |
|---|---|---|
| Note `title` | **Yes** — as the topic | `generateFromTopic(normalizedTopic, ...)` |
| Note `subject` | **Yes** | context `subject` |
| Note `domainContext` | **Yes** — the authoring-treatment axis | context `domainContext` |
| Note `learnerLevel` (Authored Depth) | **Yes** — curriculum floor | context `noteLearnerLevel` |
| Note `tags` | **Yes** | context `tags` |
| User `learnerLevel` | **Yes** — reader level | context `learnerLevel` |
| Course/Program | **Yes — as ONE resolved string** | `resolveCourseProgram(note, user)` |
| **Applicable Programs (`note_course_program`)** | **NO — never as a list** | — |

**§7 is structurally satisfied and needs no regeneration-specific exception.**
`StudyPackGenerationContext` has **no collection-typed program field** — `courseProgram` is a single
`String`. Per `resolveCourseProgram`, a joined catalog program name is used **only when exactly one
program is joined**; otherwise it falls back to note `courseProgram`, then profile `courseProgram`.
Multi-program notes with a null Domain Context are rejected outright by `assertGenerationReady`
(`:33-39`) rather than having one program arbitrarily win.

**Recommendation: do not surface Applicable Programs in the regeneration modal at all** — not even
read-only. It is discovery metadata and showing it beside "Uses your current Note details" would
misrepresent it as an input. This preserves the ADR-001 boundary at the UI as well as the code.

---

## 5. Note identity preservation audit (§3, §39.C)

**In-place regeneration mutates columns, never identity.** `notes.id` and `study_packs.id` are both
stable, so every dependent row survives by construction.

**Seven tables key on `note_id`:** `study_packs`, `note_collection_items` (plan/section membership,
`position`, `label`), `note_shares`, `public_note_likes`, `generated_quizzes`,
`quick_review_sessions`, `user_activity_events`.

**Eight key on `study_pack_id`:** `concept_health`, `exam_question_pool`,
`challenge_quiz_question_bank`, `quiz_questions`, `memorization_cards`, `share_links`,
`quick_review_sessions`, `user_activity_events`.

**All fifteen survive.** Note columns not touched by regeneration: `id`, `owner_user_id`,
`visibility`, `subject`, `course_program`, `domain_context`, `learner_level`, `target_profile_type`,
`source_note_id`, `copied_from_*` (five provenance columns), `created_at`.

**⚠️ Two items in the brief's §3 list do not exist as note columns** — reported rather than assumed:

- **view count** — `notes` has **no** view counter. The only `view_count` in the schema is on
  `share_links` (`ShareLinkEntity:38`), a different artifact.
- **copy count** — derived, not stored: `select n.copiedFromNoteId, count(n) ... group by`
  (`NoteRepository:229-233`), keyed on note id.

Both are therefore preserved trivially — there is nothing to preserve. **No relationship is lost by
in-place regeneration.**

---

## 6. Ownership / provenance audit (§18, §39.D)

**`NoteEntity` carries no provenance marker for authorship of content.** There is no
`origin`/`isGenerated`/`authoredBy` field, and a topic-generated note is saved through the same
`createNote` path as a hand-typed one.

| State | Distinguishable? | How |
|---|---|---|
| A. Generated, never edited | **No** | no marker exists |
| B. Generated, later edited | **No** | no marker; `updated_at` also moves for unrelated reasons |
| C. Manually authored by learner | **No** | indistinguishable from A and B |
| D. Curator / admin canonical | **Yes** | owner role via `CuratorAuthoringPredicate.isCurator` |

**Recommendation — the brief's own conservative fallback, and it is the right one:** show the
stronger overwrite warning for **all non-curator learner-owned Notes**. Do not build a provenance
framework for this feature (§38 forbids it, and the smallest safe rule needs none).

---

## 7. Public Note implications (§20)

Public notes are `visibility = PUBLIC`; regeneration does not change visibility. The public detail
route `frontend/app/public/notes/[id]/page.tsx` is a **redirect-only** server component
(`getServerPublicNoteById` → `redirect(...)`), so there is no per-note cached HTML to invalidate.

**⚠️ Gap the brief does not name — a live shared quiz goes stale.** `generated_quizzes` holds one
row per note (`uq_generated_quizzes_note_id`) and `quiz_share_links.generated_quiz_id` points at it.
`StudyPackService` references neither (**zero matches**). So regenerating a Note replaces the content
while a live share link keeps serving questions generated from the **replaced** content.

This is the exact hazard `v0.110.2` shipped to close for the adjacent path (regenerating a shared
quiz deactivates its link rather than swapping questions underneath a recipient). **Recommendation:
apply the same rule — deactivate, do not delete** (deleting spends share-link quota and punishes the
owner for a fix). In scope; small; reuses an existing mechanism.

---

## 8. Existing-copy implications (§4)

**Locked doctrine holds and requires no work.** `copied_from_note_id` records origin only; there is
no propagation mechanism anywhere. `NoteService`'s copy path mints an independent note owned by the
copier. Regenerating a canonical Note therefore changes **only** that note; every learner copy is a
separate row and is untouched.

**Nothing must be built to satisfy §4 — only nothing must be added.** No sync, no propagation, no
inheritance, no live fork. The *"Updated source material available"* idea stays out (§34).

---

## 9. Quota architecture (§23, §39.F)

**Two independent meters:**

| Meter | Free | Plus | Pro | Charged | Where |
|---|---|---|---|---|---|
| `noteGenerations` | 10 | 25 | 100 | after LLM success, **outside** any tx | `NoteGenerationService:62` |
| `studyPackGenerations` | 10 | 50 | 100 | after success, **inside** the commit tx | `saveStudyPack` |

Config: `StudySnapProperties:115-117, 138-140`; `application.yaml:144-146, 164-166`.

**Admin/curator behaviour.** Neither `resolveMonthlyNoteGenerationLimit` nor
`resolveMonthlyStudyPackLimit` inspects role — both switch on plan only. Bypass exists at the *call*
level: `startAsyncGenerationFromNote(..., enforceLimits = false, ...)`, used by
`NoteBulkGenerationService:350`. The learner-facing regeneration path must pass `enforceLimits =
true`.

**Both-quota precheck (§23) is straightforward** — both assertions already exist
(`NoteGenerationUsageProtectionService.assertQuotaAvailable`,
`StudyPackService.assertMonthlyStudyPackQuotaAvailable`). Call both **before** either LLM call.

**⚠️ Pre-existing, not introduced:** `assertQuotaAvailable` is an unlocked read, so concurrent starts
can both pass. Named, **not fixed here** — fixing it is a separate decision and this feature does not
worsen it materially.

---

## 10. Combined-regeneration atomicity analysis (§24) — **recommended architecture**

**Binding invariant:** a regenerated Note must never be presented with a stale Study Pack as though
that pack came from the new content.

**The naive sequence violates it.** Write new content → dispatch pack generation → pack fails → note
is `FAILED` **with new content and the old pack row intact**, which is exactly the forbidden pairing.

**The architecture already affords a clean answer, and it needs no new machinery.**
`generateStudyPackFromExistingNoteAsync` receives the note text as the `normalizedText`
**parameter** and never re-reads the note body. So the new content does not have to be persisted
before the pack can be generated from it.

**Recommended shape — two LLM calls, then one commit:**

```
1. assert BOTH quotas (note + study pack)          — no writes
2. resolve context from the EXISTING note          — resolver.resolve(ownerUserId, note)
3. LLM call 1 → new note content                   — in memory
4. LLM call 2 → new Study Pack from that content   — in memory
5. ONE transaction:
     note.content := new content
     saveStudyPack(...)            (mutates the existing pack row)
     markNoteGenerated(noteId, note)
     record BOTH quota units
```

**Why this is the smallest robust solution:**

- **It is the repo's existing idiom, not a new one.** `CLAUDE.md` records "two short transactions
  with the LLM call between them" as the correct pattern, already implemented three times
  (`StudyPackService`, `ExamQuestionPoolService`, `OfficialChallengeQuizTemplateService`).
- **No staging table, no version column, no job state machine, no `REQUIRES_NEW`** (twice recorded as
  a landmine that broke production).
- **The pairing invariant holds by construction** — nothing is written until both artifacts exist.
- The note stays `GENERATING` throughout, a state every surface already renders; the existing
  recovery interlock (`:695`) and `FAILED` sweeper apply unchanged.

**Quota on partial failure.** Recording both units inside the commit means **a failure at any point
charges nothing**, satisfying §24's fairness requirement exactly. This requires deferring
`NoteGenerationService`'s `recordUsage` for this path. **The smallest form is a `boolean recordUsage`
parameter mirroring `saveStudyPack(..., boolean recordUsage)` and
`startAsyncGenerationFromNote(..., boolean enforceLimits, ...)`** — an established parameter idiom in
this codebase, not a new concept, and it leaves the default path untouched.

**⚠️ Cost to state plainly:** two LLM calls are held before any commit, so the operation's wall time
is the sum of both. It must run on the existing async generation executor (as Study Pack generation
already does), never inline on the request thread. **The `v0.112.0` connection-hold finding applies:
do not hold a JDBC connection across either LLM call** — resolve the context, close, generate, then
open the commit transaction.

---

## 11. Failure-state matrix (§41)

`N` = Note, `SP` = Study Pack. "Old available" = the pre-regeneration artifacts remain intact.

| # | Failure | User-visible | Persisted | Quota | Retry | Old available |
|---|---|---|---|---|---|---|
| 1 | Insufficient topic quota | Blocked before start, limit modal | nothing | none | after reset/upgrade | **yes** |
| 2 | Insufficient SP quota | Blocked before start, limit modal | nothing | none | after reset/upgrade | **yes** |
| 3 | N generation fails (LLM/moderation) | Note `FAILED`, error toast | nothing | **none** | immediate | **yes** |
| 4 | N succeeds, SP fails | Note `FAILED` | **nothing** — no partial write | **none** | immediate | **yes** |
| 5 | SP succeeds, persistence fails | Note `FAILED` | nothing (tx rollback) | **none** | immediate | **yes** |
| 6 | Network/client interruption | Async continues server-side; poll resumes | commits normally | charged on success | n/a | replaced on success |
| 7 | User closes modal mid-generation | Same as 6 — dispatch already committed | commits normally | charged on success | n/a | replaced on success |
| 8 | Concurrent Note edit | **Rejected — 409 `NOTE_GENERATION_IN_PROGRESS`** (§20a) | nothing | unchanged | after regen finishes | **yes** |
| 9 | Concurrent SP regeneration | **409 `NOTE_GENERATION_IN_PROGRESS`** (`:628-635`) | nothing | none | after first finishes | **yes** |
| 10 | Public Note viewed during regeneration | Old content served until commit | old row until commit | n/a | n/a | **yes** |
| 11 | Retry after partial failure | Clean restart — nothing partial exists | as a fresh run | charged on success | yes | **yes** |

**Row 4 is the invariant, and the recommended architecture makes it structurally unreachable.**

**Row 8 was the one genuine residual and is now closed by owner decision** — see §20a. Editing is
rejected while the note is `GENERATING` rather than silently overwritten at commit, which also fixes
the same pre-existing race on today's Study Pack regeneration path.

---

## 12. Session / history implications (§26)

**Safe, and the reason is structural rather than incidental.** Sessions store their question list in
`quick_review_sessions.session_state` (JSONB) and read it back via
`QuizSessionStateUtils.extractQuiz`. A completed session is **self-contained**: it does not
dereference the pack's current `quiz` column.

Therefore, after regeneration:

- historical sessions still render the questions actually answered;
- their FK to `study_pack_id` remains valid (the pack row is mutated, not replaced);
- new practice draws from the regenerated pack.

**Nothing must be built, and nothing must be rewritten.** §38's "do not silently rewrite historical
sessions" is satisfied by leaving this alone.

---

## 13. ConceptHealth implications (§27)

**The desired honest behaviour is already what the architecture does — no migration engine needed.**

`concept_health` is keyed `(user_id, study_pack_id, concept)` with free-text `concept`. Because the
**pack id is stable**, rows survive regeneration. The decisive question is whether stale concepts
surface, and they do not:

`ConceptHealthService.getDueConceptsByStudyPackIds` is driven by a caller-supplied
`conceptsByStudyPackId` map — the pack's **current** key concepts — and looks health up per supplied
concept. So:

| Concept after regeneration | Behaviour |
|---|---|
| Survives with the same string | **Keeps its history** — evidence continues |
| Removed or renamed | **Never read** — the row is inert, not surfaced |
| Newly introduced | Starts with no history and accumulates fresh evidence |

**This is precisely the outcome the brief hoped for** ("historical evidence remains historical, while
new Study Pack concepts begin accumulating new evidence").

**Nothing reconciles or deletes these rows** — the only `concept_health` deletion in the codebase is
`AccountPurgeService:169` (`deleteByUserId`).

**⚠️ Residual, pre-existing and unchanged by this feature:** a *renamed* concept silently loses its
history and leaves an inert row behind forever. **This already happens on every Study Pack
regeneration today.** Do not build a concept-migration engine for it — cross-pack canonical concept
identity is ADR-sized and out (`v0.107.0`), and a rename-matcher is the same class of claim.

---

## 14. Search / cache / index implications (§29)

**Reported strictly as what the repo contains.**

| System | Exists? | Impact |
|---|---|---|
| Search index (Elasticsearch/OpenSearch/Algolia) | **No** | none |
| Embeddings / vector retrieval / pgvector | **No** | none |
| Explicit cache-invalidation hooks | **No** | none |
| Next.js ISR | **Yes** — `revalidate = 300` on `/public/library/[subject]` | stale ≤ 5 min, then self-heals |
| Public note detail page cache | **No** — redirect-only server component | none |
| `sitemap.ts` | **Yes** — `lastModified: note.updatedAt` (`:92`) | regeneration correctly signals freshness |
| OG / social metadata | Generated per-request from note data | reflects new content automatically |

**No cache invalidation work is owed.** The only time-bound staleness is the 5-minute ISR window on
subject landing pages, which resolves itself.

---

## 15. Note timestamps / freshness (§28)

- **`created_at` is never touched after insert.** Creation identity stays historical — the brief's
  requirement is met with no work.
- **`updated_at` is already bumped by Study Pack generation today** —
  `sourceNote.setUpdatedAt(generationEnqueuedAt)` before dispatch.

**Downstream consumers of `notes.updated_at`, verified:**

- learner Library ordering — `findByOwnerUserIdOrderByUpdatedAtDesc` (`NoteRepository:70`), plus
  visibility- and status-scoped variants (`:83`, `:88`, `:117`);
- public library ordering — `findByVisibilityOrderByUpdatedAtDesc`;
- `sitemap.ts` `lastModified`.

**Consequence to state, not to fix:** a regenerated note floats to the top of the owner's Library and
of public listings sorted by `updated_at`. That is already true of today's Study Pack regeneration,
and it is defensible — the content genuinely changed. It does **not** make the note look newly
*created*, since `created_at` is untouched.

---

## 16. Responsive UX recommendation (§42, §39.K)

### Primitives available

`AppModal` (`frontend/components/ui/app-modal.tsx`) already provides:

- **a mobile bottom-sheet variant and a desktop centred dialog** — `rounded-t-2xl rounded-b-none
  max-h-[85dvh]` vs `rounded-xl max-h-[90dvh]` (`:163-164`). **§35's bottom-sheet question is already
  answered by the existing primitive; no new mobile pattern is needed.**
- `role="dialog"`, `aria-modal`, `aria-labelledby`, `aria-describedby`, body scroll lock, and a
  labelled close button (`:181-254`) — most of §36 for free;
- `flex flex-col overflow-hidden` with a `shrink-0` header, so a scrollable body region is supported;
- a `panelClassName` escape hatch.

**⚠️ One contradiction with §9.** The default panel is `max-w-[420px]`. **Two side-by-side cards do
not fit at 420px.** Resolve by passing `panelClassName` (e.g. `sm:max-w-[560px]`) — an existing prop,
not a new primitive.

**Selectable-card precedent:** `quiz-choice-list.tsx` — `<ul><li><button type="button"
aria-pressed={isSelected}>` with the **entire card** as the button (`:73-116`). Reuse this visual and
interaction language.

### Desktop

- Modal widened to ~560px via `panelClassName`.
- Two selector cards side by side, each the full tap target, default **Study Pack**.
- Below the cards, a dynamic details region that changes with selection:
  - **Study Pack** → one line ("Your Note won't change…") + quota line.
  - **Note + Study Pack** → scope sentence, then an explicit metadata block (Topic / Subject /
    Writing context / Depth), then warnings, then **Edit Note details →**.
- Warning hierarchy, most severe first: learner-written overwrite → public content → routine scope.
- Quota line rendered as quiet secondary text under the selected card, not as a badge, using the
  ratified copy (§20.1): **Uses 1 Study Pack** / **Uses 1 topic note and 1 Study Pack**.
- Actions right-aligned: `Cancel`, then the primary CTA — **`Regenerate`**, or
  **`Regenerate Note + Study Pack`** in the strong-overwrite state (§20.3). No second modal.

### Mobile

- Cards **stacked vertically**, full width, generous tap targets.
- Metadata **compacted to a scannable summary** — `Site Planning · Planning & Site Development` /
  `Board Exam Review` — not a field list.
- Warnings keep full text; they are the one thing that must not be abbreviated.
- Actions pinned in the sheet's non-scrolling footer so the CTA is reachable without scrolling past
  the metadata.
- Selected state must be conveyed by more than colour (border + check icon), per the existing
  choice-list pattern.

### Accessibility (§36)

**⚠️ Finding: there is no `role="radio"` or `role="radiogroup"` anywhere in the repo.** The nearest
precedent (`quiz-choice-list`) uses `aria-pressed`, which is a toggle semantic rather than a
single-choice one.

**Recommendation:** wrap the two cards in `role="radiogroup"` with an accessible label and give each
card `role="radio"` + `aria-checked`, with arrow-key navigation and roving tabindex. This is a small
new pattern, and it is named as new rather than described as reuse. Visual language still comes from
`quiz-choice-list`. Warnings should be in an `aria-live="polite"` region so a change of selection
announces the new consequence.

---

## 17. Product-state matrix (§40)

Derived from §6 (only curator vs learner is distinguishable) and the locked contract.

| Note state | SP regen | Note + Pack regen | Warning shown | Public warning | Note+Pack CTA | Copies affected? |
|---|---|---|---|---|---|---|
| Canonical private | Allowed | Allowed | routine scope | — | `Regenerate` | **No** |
| Canonical public | Allowed | Allowed | routine scope | **Yes** | `Regenerate` | **No** |
| Learner generated private | Allowed | Allowed | **strong overwrite** ¹ | — | **`Regenerate Note + Study Pack`** | **No** |
| Learner generated public | Allowed | Allowed | **strong overwrite** ¹ | **Yes** | **`Regenerate Note + Study Pack`** | **No** |
| Learner written/edited private | Allowed | Allowed | **strong overwrite** | — | **`Regenerate Note + Study Pack`** | **No** |
| Learner written/edited public | Allowed | Allowed | **strong overwrite** | **Yes** | **`Regenerate Note + Study Pack`** | **No** |

¹ **The system cannot distinguish a learner-generated note from a learner-written one** (§6), so the
strong warning applies to all four learner rows. This is the conservative fallback the brief
specifies, and it is the smallest safe rule.

**"Copies affected" is `No` in every row** — structurally, not by policy (§8).

---

## 18. Recommended implementation architecture

1. **One new endpoint**, not a flag on the existing one: `POST /notes/{id}/regenerate` with a scope
   field (`STUDY_PACK` | `NOTE_AND_STUDY_PACK`). `STUDY_PACK` delegates to today's
   `startAsyncGenerationFromNote` unchanged, so the existing path keeps its exact semantics.
2. **`NOTE_AND_STUDY_PACK` follows §10's sequence** — both quota asserts, context resolved from the
   note, two LLM calls, one commit.
3. **Reuse, do not duplicate:** `generationContextResolver.resolve(ownerUserId, note)`,
   `generateFromTopic(request, userId, resolvedContext)`, `saveStudyPack`, `markNoteGenerated`.
   No second prompt architecture (§6), no second Study Pack replacement model (§25).
4. **Deactivate a live quiz share link** for the note on `NOTE_AND_STUDY_PACK` (§7), reusing
   `v0.110.2`'s mechanism.
5. **No migration.** No schema change is required by anything in this audit.

---

## 19. Implementation slices and release boundary

| Slice | Content | Depends on | Route |
|---|---|---|---|
| **1** | Combined regeneration backend: endpoint + scope, both-quota precheck, two-generation/one-commit atomicity, usage recording, concurrency protection (§20a) | — | **Codex** |
| **2** | Shared quiz integrity: deactivate the live quiz share link on `NOTE_AND_STUDY_PACK` (§7) | 1 | Claude Code inline |
| **3** | Scope-selector UX: two cards, responsive, single-choice semantics | 1 | Claude Code inline |
| **4** | Generation-context guidance: current metadata summary + Edit Note details route | 3 | Claude Code inline |
| **5** | Contextual protections: learner overwrite warning, public Note warning, strong-overwrite CTA | 3 | Claude Code inline |

**⚠️ RELEASE BOUNDARY (owner-ratified). Internally these may be separate PRs; separate PRs are
expected and this does not require one PR. But `NOTE_AND_STUDY_PACK` must NOT become
user-accessible until slices 3 and 5 are present.** The backend is capable of replacing Note
content; that capability must not reach a learner without the UX that explains its consequences.

**Concretely:** slices 1 and 2 may merge and deploy with the scope defaulting to `STUDY_PACK` and
no selector rendered, leaving product behaviour unchanged. **The feature is product-complete only
after slices 1–5 land.**

**⚠️ Slice 1 alone changes money semantics** (one operation charging two meters) and therefore sets
the verification tier.

**Bulk regeneration is not in this sequence** — see §21.

---

## 20. Owner decisions — RATIFIED 2026-09-05

All four open questions from the audit are decided. Recorded as settled, not as options.

**1. Quota copy — DECIDED.** Use the established learner vocabulary, not a new meter name:

| Scope | Copy |
|---|---|
| Study Pack | **Uses 1 Study Pack** |
| Note + Study Pack | **Uses 1 topic note and 1 Study Pack** |

*"Topic generation" is not introduced.* The product already says **topic note**
(`note-editor-page-client.tsx:682`) and **Study Packs** (`settings/page.tsx:1072`).
**⚠️ Whether Settings should expose the topic-note meter is a separate future cleanup and is NOT
part of this feature** — but note the consequence, stated plainly: until that cleanup, this modal
is the only place a learner learns that meter exists.

**2. Concurrent editing — DECIDED.** Silent last-write-wins loss is not accepted. Note body editing
is blocked while `NOTE_AND_STUDY_PACK` regeneration is active, reusing existing in-progress
semantics. **No new lock subsystem.** The exact rule, including the metadata question, is §20a.

**3. Strong overwrite CTA — DECIDED.** For learner-owned Notes showing the stronger overwrite
warning, the primary CTA is **`Regenerate Note + Study Pack`**. Ordinary Study Pack regeneration
keeps **`Regenerate`**. For curator/admin canonical Notes the selector already establishes scope, so
either label is acceptable if consistent. **No second confirmation modal in any case.**

**4. Public canonical Note — DECIDED.** An inline warning is sufficient; **no second confirmation
dialog because a Note is public.** Semantics that must survive any copy refinement: the public
source changes, Note identity remains, existing learner copies are untouched.

> **This Note is public**
>
> Regenerating will replace the content people see on this Note. Existing learner copies won't
> change.

**5. Shared-quiz fix stays in scope — DECIDED.** Deactivate (never delete) the live quiz share link
on `NOTE_AND_STUDY_PACK`, reusing `v0.110.2` semantics. It belongs to this feature because otherwise
the new operation leaves a live artifact serving questions derived from replaced content.

**6. ConceptHealth behaviour — LOCKED as audited.** Identical concept string keeps its history; a
removed or renamed concept's row becomes inert and is no longer surfaced; a new concept starts
empty. **Build no rename matching, no concept migration, no canonical concept identity, and no
cleanup of inert rows.** The renamed-concept history loss ships as a **named existing Known
limitation**, not a blocker — it already occurs on every Study Pack regeneration today.

**Settled by the audit and never owner questions:** partial-failure content preservation (§10 makes
it structural), quota-on-partial-failure (charge nothing), learner-edited detection (§6 — not
detectable; use the curator/non-curator fallback).

---

## 20a. Concurrency rule — content vs metadata editing

The owner asked for metadata editing to stay unblocked **unless the commit model proves a concurrent
metadata edit would produce an internally inconsistent result.** It does, on two independent
grounds, both verified in code after the first draft.

**Proof 1 — the API cannot separate them.** `PUT /notes/{id}` (`NoteController:199` →
`NoteService.update:219`) is the **only** note content/metadata endpoint, and it is a single upsert:
it writes `content`, `title`, `subject`, `domainContext`, `learnerLevel` and programs from one
request, and **content is mandatory** — `normalizeRequiredContent` throws `EMPTY_NOTE_CONTENT` on a
blank body (`:1410-1418`). **So a metadata-only edit is not expressible: every metadata save
necessarily rewrites `content`.** Permitting metadata editing during regeneration therefore permits
exactly the content overwrite that decision 2 forbids.

**Proof 2 — Subject is re-read at commit, not snapshotted.** The generation context is snapshotted
at start, but `saveStudyPack` re-reads the note's Subject inside the commit transaction:

```java
String noteSubject = noteId != null
        ? noteRepository.findById(noteId).map(NoteEntity::getSubject).orElse(null)   // :590
        : null;
```

A Subject edit landing mid-generation would therefore label the new Study Pack with the new Subject
while its content was generated under the old one.

**⚠️ RULE: block `PUT /notes/{id}` while the note is `GENERATING`.** This is not a broadened lock
chosen for convenience — it is the narrowest rule the current contract permits, because content and
metadata are inseparable at the only endpoint that writes either.

**Implementation:** reuse the existing status, exactly as the generation start guard already does —
`NoteService.update` gains the check `StudyPackService:628-635` already applies, rejecting with
`409 NOTE_GENERATION_IN_PROGRESS`. No new column, no lock table, no new subsystem.

**⚠️ This also closes a pre-existing hole.** `NoteService.update` has **no status guard today**
(verified: zero `NoteStatus.GENERATING` references in the method), so a note can already be edited
mid-Study-Pack-generation. The guard therefore fixes an existing race as well as enabling this
feature — state it in the release notes rather than letting it read as new restriction.

**Not blocked, deliberately:** `PUT /notes/{id}/shares`, visibility changes, and collection/section
placement. None is read by generation and none is written at commit.

**The §15/§22 guidance flow is unaffected** — *Edit Note details →* runs **before** regeneration
starts, when the note is not `GENERATING`.

## 21. Anti-drift checklist

- **⚠️ Do NOT delete and recreate the Note, or mint a new note id.** Not needed — `generateFromTopic`
  creates no note.
- **⚠️ Do NOT build a second Note generation architecture.** Use the existing 3-arg overload with a
  note-resolved context.
- **⚠️ Do NOT build a second Study Pack replacement model.** `saveStudyPack` already mutates in place.
- **⚠️ Do NOT send Applicable Programs to generation** — the context has no list field; keep it that
  way, and do not display them in the modal as inputs.
- **⚠️ Do NOT propagate anything to learner copies.** No sync, no inheritance, no live fork.
- **⚠️ Do NOT silently rewrite Note metadata** — title, subject, domain context, depth and programs
  are inputs. Do not auto-persist any LLM-suggested metadata without a separate decision.
- **⚠️ Do NOT rewrite historical sessions.** They snapshot their own questions; leave them alone.
- **⚠️ Do NOT build a concept-migration engine.** Stale ConceptHealth rows are already inert.
- **⚠️ Do NOT add a provenance framework** to detect learner edits — use the curator/non-curator rule.
- **⚠️ Do NOT add content versioning.** The previous Study Pack is already unrecoverable today; this
  feature does not change that and must not be the reason to build versioning.
- **⚠️ Bulk regeneration: NOT architecturally rejected — OUT OF SCOPE FOR PHASE 1.** Do NOT add
  bulk regeneration to this release, and do NOT automatically or mass-regenerate production Notes.
  **A separate curator-selected Bulk Regeneration feature may be audited and built later on top of
  the verified single-Note primitive.** Automatic regeneration stays permanently locked: no
  regeneration on metadata edit, on Domain Context change, on Review Set placement, or on deploy
  (§33).
- **⚠️ Do NOT use `REQUIRES_NEW`** — twice recorded as breaking production.
- **⚠️ Do NOT hold a JDBC connection across either LLM call** (`v0.112.0`).
- **⚠️ No migration, no pricing or entitlement change, no new plan gate, no new quiz mode.**
- **⚠️ `frontend/app/onboarding` stays frozen.**
- **⚠️ Do NOT combine this with Official Review Set update architecture** (§34) or with the five
  authoring/quiz legibility fixes.

---

## Verification strategy

**Tier: ONE SCOPED COLD AGENT framed as falsification**, on slice 1. Trigger, named by the gate:
**money/quota semantics change** — a single operation charges two meters. Not the three-agent test:
no permission substrate, no cross-user read, no migration.

**Pre-declared discriminating guards** (each fails under the defect, passes under the fix):

1. **Pairing invariant** — a `NOTE_AND_STUDY_PACK` run whose **Study Pack generation fails** must
   leave the note's **original content** and **original pack** intact. *A fixture whose generation
   succeeds passes under both the defect and the fix and proves nothing.*
2. **Quota on failure** — the same failing run must leave **both** meters unchanged. Assert the
   persisted counters, not the call.
3. **Identity preservation** — after a successful run, assert the **persisted** `notes.id`,
   `notes.created_at`, `study_packs.id`, and a surviving `note_collection_items` row with its
   `position` and `label` unchanged.
4. **Copy independence** — a note with an existing copy must leave the copy **byte-identical** after
   regeneration.
5. **ConceptHealth honesty** — a pack whose regenerated key concepts drop one concept and keep
   another must still surface history for the **kept** concept and surface **nothing** for the
   dropped one.
6. **Concurrency guard (§20a)** — a `PUT /notes/{id}` issued while the note is `GENERATING` must be
   **rejected with 409**, and the note's stored `content` must be **unchanged** afterwards. *A
   fixture that edits before generation starts passes under both the defect and the fix.*
7. **Applicable Programs isolation** — a note joined to **two** catalog programs with a set Domain
   Context must produce a context whose `courseProgram` is **not** either program name concatenated,
   and must not throw. *A single-program fixture passes under a concatenation bug.*

**Carried lessons:** mutate and confirm a **named** test fails; read `./mvnw`'s exit status directly;
count executed tests from `target/surefire-reports/*.xml` after cleaning it; run `npm test`; sweep by
**surface**, not by diff; verify "X already does Y" against code before it reaches a prompt; and call
`advisor()` **before** writing the Codex prompt for slice 1.
