# Learning Connections — architecture audit and Phase 1–5 implementation plan

**Status:** plan only. No code written. Audited against real code and the live schema on **2026-08-27**.
**Direction of record:** the ratified product direction supplied by the owner (product/UX consultation output).
**Prior artifacts:** `docs/claude-plans/support-another-learner-proposal.md`,
`docs/claude-plans/linked-learners-surfacing-product-ux-consultation-prompt.md`,
`docs/features/linked-learners.md`, `docs/features/shareable-quiz-links.md`.

---

## 1. Current architecture findings

### 1.1 ⚠️ The finding that justifies Phase 1: sharing a note today means publishing it to the world

There is no controlled sharing at all. `NoteVisibility` has exactly two values, `PRIVATE` and `PUBLIC`, and the
note-detail Share action on a private note opens a confirmation that says, verbatim:

> **This note is private.** You need to make this note public before sharing. Anyone with the link will be able
> to view and copy this note.

So a parent who wants to give their child one Study Pack must **publish it into Explore for everyone**. That is
the actual, shipped state of "share learning material" — and it is a stronger argument for Phase 1 than any
privacy-model reasoning. Phase 1 is not adding a nicety; it is adding the only safe way to do the thing the
product already invites people to do.

### 1.2 Note visibility and access

- `NoteVisibility { PRIVATE, PUBLIC }` — **42 usages across 24 files.**
- **Every public-facing query positively matches `= 'PUBLIC'`** (`PublicLibraryRepositoryImpl`,
  `NoteCourseProgramRepository`, `AnalyticsEventRepository`, `NoteRepository`, `NoteCollectionItemRepository`).
  **There is no `!= PRIVATE` anywhere in the backend.** Verified by search, not assumed.
- **Access is owner-scoped everywhere it matters.** `NoteService.getById` and `NoteService.update` both use
  `findByIdAndOwnerUserId`. `StudyPackService.getById` uses `findByIdAndOwnerUserId`. There is **no cross-user
  read path for learning material at all** — the only cross-user read in the product is the supporter progress
  aggregate.
- `NoteService.copyNote` permits a copy when `isOwner || visibility == PUBLIC`; everything else 404s.
- `NoteCollectionService.addItems` calls `loadOwnedNotesByIdOrThrow(userId, ...)` — **a learner cannot put a
  note they do not own into a Study Plan.** No leak; but it is also a Phase 1 boundary (see §5.6).
- `AccountPurgeService.deletePrivateArtifacts` **retains PUBLIC notes deliberately** (via
  `retainedPublicNoteIds`) and deletes only `visibility = PRIVATE`.

### 1.3 Study Pack reads write activity

`StudyPackService.getById` calls `activityTrackingService.recordActivity(ownerUserId, OPENED_STUDY_PACK, ...)`
before returning. Any recipient read path built by reusing this method would either write the **owner's**
activity for someone else's page view, or write the recipient's activity against a pack they do not own. Both
are wrong for different reasons. The recipient path must be a separate method.

### 1.4 Learning Connections as shipped

- `linked_learner_relationships` (supporter, learner, status `PENDING|ACCEPTED|REVOKED`, `initiated_by`,
  created/accepted/revoked timestamps). Live-row uniqueness is **per direction**; the only check constraint
  blocks self-linking.
- `linked_learner_invitations` — email-keyed, expiring (default 30 days), rate-limited on two keys, conditional
  status transitions, claim-before-create on acceptance.
- `linked_learner_guardian_consents` — one row per relationship, required below a configured age threshold.
- Birth year is account-global, learner-corrected, and a downward correction **reverts un-consented `ACCEPTED`
  rows to `PENDING`**.
- Progress read: `GET /linked-learners/{relationshipId}/progress` → readiness, quiz performance, engagement,
  collection progress. **Writes nothing.** Counts and states only, no learner free text.
- **17 endpoints** on `LinkedLearnerController`; invite/accept/revoke/consent/birth-year/progress all exist.

### 1.5 Activity data already recorded — do not invent new measurements

`UserActivityEventEntity` rows carry `(userId, activityType, studyPackId, timestamp)`. `ActivityType` has seven
values, and the enum already declares which of them count:

```
MEANINGFUL_STUDY_ACTIVITIES = { CREATED_STUDY_PACK, STARTED_QUICK_REVIEW, STARTED_ADAPTIVE_PRACTICE,
                                COMPLETED_QUICK_REVIEW, COMPLETED_CHALLENGE_QUIZ, COMPLETED_ADAPTIVE_QUIZ }
```

**`OPENED_STUDY_PACK` is deliberately excluded.** That set is the answer to the direction's "do not fabricate
new activity measurements": *studied* means a meaningful activity, and merely opening a pack is not one.
Streaks (`users.current_streak`, `users.longest_streak`) and `countStudyDaysThisWeek` already exist and already
feed the supporter progress view. **Phase 2 needs no new measurement, only a permissioned projection.**

### 1.6 Two lightweight sharing paths already exist, and they are different things

- **Quiz share links** — `GET /quiz/share/{token}` and `POST /quiz/share/{token}/results`, both anonymous.
  No principal, nothing persisted, recipient needs no account. Generation spends the Challenge Quiz allowance;
  the share-link cap (Free 3) is asserted **only** at link creation.
- **Study Pack share tokens** — `GET /share/{token}` and `/p/{token}`, plus `POST /p/{token}/remix`, which
  copies the pack into the caller's library. This path is older and separate from quiz links.

### 1.7 Instrumentation

**There are zero Learning Connections analytics events.** `AnalyticsEventType` has 123 lines and covers public
notes and quiz share links, but nothing for invitations, acceptance, connection progress views or supporter
activity. That was deliberate — `[CHECKPOINT — due 2026-09-19]` reads the *table* precisely so it cannot stop
firing. It is nonetheless a real gap for §18's retention hypothesis, which needs a shared → opened → studied
funnel that no table can express.

### 1.8 Production state

`linked_learner_relationships` was **completely empty** on 2026-08-26. The landing page still advertises this
capability as *Coming Soon* with a waitlist button, and `/help` is authentication-gated with no supporter
section. **Nothing in this plan should be read as an explanation of the zero until those two are fixed.**

---

## 2. Contradictions between the direction and the current implementation

### 2.1 ⚠️ `PRIVATE / SHARED / PUBLIC` cannot be an enum value — and the direction invited this challenge

The direction states the conceptual model as three states and adds *"determine the cleanest architecture after
auditing"* and *"the user-facing terminology does not necessarily need to expose the word SHARED."* Taking that
invitation: **`SHARED` must not become a third `NoteVisibility` value.** Three independent reasons:

1. **The enum grants nothing.** Access is `findByIdAndOwnerUserId` in every read path. A note marked `SHARED`
   is still unreadable by everyone except its owner until a grant table says otherwise. The enum would be a
   *label asserting* that shares exist while a separate table *is* the access — two sources of truth that can
   disagree, with the label being the one people trust.
2. **Account purge falls through both branches.** `deletePrivateArtifacts` retains `PUBLIC` deliberately and
   deletes `PRIVATE`. A `SHARED` note matches neither: it would **survive the purge of a deleted account and
   remain readable by its recipients**, orphaned from a user row that no longer exists. That is a privacy
   defect introduced by the schema choice itself.
3. **42 usages across 24 files** would each need a decision about whether `SHARED` behaves like `PRIVATE`. The
   repo's own most-cited failure mode is repo-wide changes that under-scope themselves.

**Recommendation: keep `NoteVisibility` at `PRIVATE | PUBLIC` and add a grant table.** A shared note stays
`PRIVATE`, so it stays excluded from every public query, retained by no purge branch, and invisible to Explore
**by default rather than by 24 correct decisions**. The three-way choice remains exactly as designed in the UI;
it is derived, not stored.

### 2.2 ⚠️ Authorization today is role-directional; the ratified model is permission-directional

`LinkedLearnerReadAuthorizationService.requireAcceptedLearnerId` requires `caller == relationship.supporter`
and `status == ACCEPTED`. Direction §9/§13/§14 require the opposite shape: each side independently grants
**activity** and **progress** to the other, so two study partners can both share activity, and a learner can
share progress with a tutor who shares nothing back.

Under the new model, *supporter* and *learner* stop being authorization roles and become **provenance** — who
initiated, and whose birth year drives consent. **Authorization moves to the grant.** See §4 for the migration
path and how guardian consent survives it.

### 2.3 "Shared material leads into the Study Pack" collides with owner-scoped pack reads

Direction §6 requires the recipient to open the Study Pack and enter the normal practice loop. Both the note
read and the pack read are owner-scoped, and the pack read writes activity. This is the largest single piece of
Phase 1 backend work, and it is genuinely new surface area rather than a parameter change.

### 2.4 Progress sharing already exists but is *implicitly* granted

Today, `ACCEPTED` ⇒ the supporter can read progress. Direction §3 and §13 require progress to be an
independent, explicitly granted permission. That is a **behaviour change for existing connections** — of which
there are currently **zero**, so it can be made now at no migration cost. This window closes the moment anyone
connects.

### 2.5 Two items in the direction need no work

- §16, *"Quiz for someone should NOT return to the primary Practice CTA row"* — already true since `v0.90.0`;
  it lives in the note-actions menu. Nothing to do beyond not undoing it.
- §21.16 is the same rule stated as anti-drift. Both are satisfied by the current code.

### 2.6 The Library has no tab structure to graduate into

`frontend/app/library/page.tsx` is a **2,359-line client component** with no tab shell. Direction §7's
preference for a *"Shared with you"* section rather than a tab is therefore also the cheaper option, and the
eventual `[ My Notes ] [ Shared with Me ]` split is a larger refactor than it appears.

---

## 3. Recommended data model

### 3.1 Note sharing — `note_shares`

```
note_shares
  id                uuid PK
  note_id           uuid NOT NULL  → notes(id)          ON DELETE CASCADE
  owner_user_id     uuid NOT NULL  → users(id)          ON DELETE CASCADE   -- denormalized, see below
  grantee_user_id   uuid NOT NULL  → users(id)          ON DELETE CASCADE
  relationship_id   uuid NOT NULL  → linked_learner_relationships(id) ON DELETE CASCADE
  created_at        timestamptz NOT NULL
  revoked_at        timestamptz NULL
  CHECK (owner_user_id <> grantee_user_id)
UNIQUE INDEX ux_note_shares_live ON (note_id, grantee_user_id) WHERE revoked_at IS NULL
INDEX idx_note_shares_grantee ON (grantee_user_id, created_at DESC) WHERE revoked_at IS NULL
```

- `owner_user_id` is denormalized from the note so the "shared with you" list and every authorization check
  avoid a join, and so an ownership change can never silently re-point a grant.
- `relationship_id` is **required**, not optional. It is what makes revocation cascade correctly: ending a
  connection must cut note access, and carrying the relationship on the row makes that a single predicate
  rather than a background sweep.
- Rows are **revoked, never deleted**, so "was this ever shared" stays answerable and re-sharing re-arms rather
  than colliding with the unique index. `ON DELETE CASCADE` still covers note deletion and account purge.

### 3.2 Connection data-sharing grants — `linked_learner_grants`

```
linked_learner_grants
  id                uuid PK
  relationship_id   uuid NOT NULL → linked_learner_relationships(id) ON DELETE CASCADE
  from_user_id      uuid NOT NULL → users(id)   -- the person whose data is shared
  to_user_id        uuid NOT NULL → users(id)   -- the person who may read it
  scope             varchar(16) NOT NULL CHECK (scope IN ('ACTIVITY','PROGRESS'))
  granted_at        timestamptz NOT NULL
  revoked_at        timestamptz NULL
  CHECK (from_user_id <> to_user_id)
UNIQUE INDEX ux_llg_live ON (relationship_id, from_user_id, scope) WHERE revoked_at IS NULL
```

One row per (relationship, direction, scope). Absence of a live row means **no access** — the default is closed,
which satisfies anti-drift rules 4, 5, 6 and 7 structurally rather than by convention.

**No relationship-type column.** Direction §14 is explicit that permissions define the relationship, and adding
`GUARDIAN | TUTOR | PARTNER` would immediately invite gating on it — the exact `ProfileType` mistake `v0.89.0`
was built to correct.

### 3.3 What does **not** change

- `NoteVisibility` stays `PRIVATE | PUBLIC`.
- `linked_learner_relationships`, `_invitations`, `_guardian_consents` keep their current shape and meaning.
  **`[CHECKPOINT — due 2026-09-19]` and `[CHECKPOINT — due 2026-10-13]` both read those tables**, so the
  meaning of a row must not shift under them.
- No new content entity. Direction §6 explicitly prefers controlled Note/Study Pack sharing over a "Shared
  Review" entity, and the audit gives no reason to need one.

---

## 4. Permission model

Three independent axes, exactly as §3 of the direction requires:

| Axis | Granularity | Stored in | Default |
|---|---|---|---|
| **Learning material** | per note × per grantee | `note_shares` | none |
| **Learning activity** | per relationship × per direction | `linked_learner_grants` (`ACTIVITY`) | none |
| **Learning progress** | per relationship × per direction | `linked_learner_grants` (`PROGRESS`) | none |

**Accepting a connection grants nothing.** It creates the *capacity* to grant.

### 4.1 The grant check, and what happens to the existing helper

Introduce `LinkedLearnerGrantAuthorizationService.requireGrant(callerUserId, relationshipId, scope)`, which:

1. loads the relationship and requires `status == ACCEPTED`;
2. requires the caller to be one of the two parties, and resolves `from_user_id` as the *other* party;
3. requires a live grant row for `(relationship, from → caller, scope)`;
4. **if `from_user_id` is the relationship's learner and that learner requires guardian consent, requires the
   consent record** — the same condition that keeps such a relationship out of `ACCEPTED` in the first place,
   re-asserted at read time so a future grant path cannot bypass it;
5. requires the caller's email to be verified, matching the existing progress read.

`LinkedLearnerReadAuthorizationService.requireAcceptedLearnerId` should **not** be deleted or generalized in
place. Keep it, and reimplement it as a thin call into `requireGrant(caller, relationshipId, PROGRESS)` once
Phase 3 lands. Until then it keeps working unchanged. Rewriting it in Phase 1 would change who can read
progress in a release that is not about progress.

### 4.2 Revocation semantics — one rule, applied everywhere

**Access is re-derived on every request; nothing is cached and there is no grace period.** Concretely:

- Relationship revoked → grants and note shares are dead immediately, because every check re-loads the
  relationship and requires `ACCEPTED`. The rows may be left in place; the predicate does the work.
- Owner un-shares a note → `revoked_at` set; the next read 404s.
- Birth-year correction reverts a relationship to `PENDING` → **all three axes cut at once**, because all three
  require `ACCEPTED`. This is a property worth stating in tests, since it is the `v0.89.1` gate re-applying
  itself to two capabilities that did not exist when it was written.

---

## 5. Phase 1 UX surfaces

### 5.1 Note detail — "Who can access this note?"

Replace the two-state visibility menu with the three-option control from the direction. The third option is a
**derived** state: selected when at least one live `note_shares` row exists.

- **Private** — only you. (`visibility = PRIVATE`, no live shares)
- **Share with connections** — reveals a checkbox list of **accepted connections only**. (`visibility = PRIVATE`,
  ≥1 live share)
- **Public** — discoverable in Explore. (`visibility = PUBLIC`)

**DECIDED 2026-08-27 (owner): selecting *Private* revokes every live share.** The confirmation names the count
before it happens. Silently keeping live shares under a control labelled "only you" would be a lie in the UI, and
the owner judged the revoke justifiable. Recipients lose access immediately; any copy they already took is theirs
and is unaffected (§5.6).

Selecting **Public** must **not** revoke shares; public is strictly broader, and the note stays in each
recipient's *Shared with you* list with its provenance intact.

### 5.2 Recipient picker

Checkbox list of accepted connections, counterparty display name and email, matching the existing connection
list. Never pre-checked. No "share with all" affordance — anti-drift rule 4.

### 5.3 Library — "Shared with you"

A distinct section below the owned-notes area, hidden entirely when empty (the pattern
`SupportedLearnersCard` already uses). Card: note title, **"Shared by {name}"**, Study Pack state, and a
`Study` action. No mixing into the owned list — anti-drift rule 8.

### 5.4 Shared note detail (recipient view)

Read-only. Title, content, metadata, provenance line, `Open Study Pack`, `Copy to my Library`. **No** edit,
delete, visibility control, share control, collection-add, or owner analytics. The owner's practice state is
absent by construction because it is fetched by owner id and never requested here.

### 5.5 Shared Study Pack (recipient view)

Summary, key concepts, full notes, and entry into the normal practice loop. The recipient's own
`ConceptHealth`, sessions and activity are written **against the recipient**, exactly as for any other pack
they study. The owner's mastery is never read on this path.

### 5.6 Copy to my Library

Extends the existing copy path with a third permitted case: `isOwner || PUBLIC || live share for this caller`.
The copy is a normal owned note (`visibility = PRIVATE`, provenance columns populated) and is thereafter
independent — including surviving revocation, which is the correct behaviour for a copy the owner authorized
at the time.

**Deliberate v1 boundary:** shared notes cannot be added to a Study Plan without copying first, because
`addItems` validates ownership. This is worth keeping in v1 — it gives the recipient a clear reason to copy,
and it keeps `note_collection_items` free of rows that a revocation would have to chase.

---

## 6. API and backend changes (Phase 1)

| Method | Path | Notes |
|---|---|---|
| `GET` | `/notes/{id}/shares` | Owner-only. Live grants for the note. |
| `PUT` | `/notes/{id}/shares` | Owner-only. Full desired-state list of connection ids; server diffs, revokes and re-arms. Idempotent. |
| `GET` | `/notes/shared-with-me` | Recipient's list, cursor-paged, with provenance. |
| `GET` | `/notes/shared/{id}` | Recipient read of a shared note. New service method; **not** `getById`. |
| `GET` | `/study-packs/shared/{id}` | Recipient read of the pack. **Must not** call `recordActivity` with the owner's id. |
| `POST` | `/notes/shared/{id}/copy` | Reuses `copyNote` with the widened permit rule. |

All require an authenticated, onboarded, **email-verified** caller — the same gate every access-granting
Learning Connections path already uses.

**Anti-drift for the implementer:** none of these endpoints may accept a learner user id. Address shares by
note id and resolve the caller from the principal, exactly as the progress route addresses by relationship id.

---

## 7. Frontend changes (Phase 1)

- `private-note-detail-page-client.tsx` — replace the visibility menu with the three-option control plus the
  recipient picker. This file already exceeds 2,000 lines; the picker belongs in its own component.
- `frontend/app/library/page.tsx` — add the *Shared with you* section. **Do not** add tabs.
- New route + client for the recipient's read-only note view and Study Pack view.
- `frontend/lib/api.ts` — six new calls, through `fetchWithAuth` (never a raw `fetch` — the `v0.80.0` rule).
- Reuse `getCollectionLabels`, `ResponsiveActionLink`, `PageHeader`, `BackLink`, and the existing card and
  empty-state patterns. No new design system work.

---

## 8. Security and privacy considerations

Each row is a test the implementation owes, derived from §22 of the direction.

| Attack / case | Mitigation |
|---|---|
| Read a shared note after the connection is revoked | Every read re-loads the relationship and requires `ACCEPTED`. No cache, no grace. |
| Read after the owner removes the recipient | `revoked_at` on the share row; live-row predicate on every read. |
| Reach note content through a Study Pack endpoint | The recipient pack read is its own method with its own grant check; the owner-scoped `getById` is untouched. |
| Leak the owner's practice state | The recipient DTO is built without any `ConceptHealth`, session, streak or score field. Assert the DTO shape in a test, not just the values. |
| Guess a shared note id | Ids are UUIDs and the grant check is per caller — guessing an id yields 404 without a grant. |
| `PUBLIC → PRIVATE` with live shares | Shares survive the transition (or are revoked per §5.1's decision) — never silently orphaned. |
| `PRIVATE → PUBLIC` | Shares survive; recipients keep provenance. |
| Copy taken before revocation | Remains the recipient's own note, by design. Copies are not retroactively revocable. |
| Account deletion | `note_shares` cascades on both user FKs and on the note. **Because a shared note stays `PRIVATE`, the existing purge deletes it** — the defect §2.1 would have introduced does not arise. |
| Shared note reaching Explore | Impossible without a `visibility` change: every public query matches `= 'PUBLIC'` positively. |
| Recipient adds a shared note to a Study Plan | Blocked today by `loadOwnedNotesByIdOrThrow`; keep it blocked in v1. |

---

## 9. Migration implications

- **Two additive migrations** (`note_shares`, `linked_learner_grants`). No column is altered, dropped or
  backfilled. No enum value is added.
- **No data migration for grants**, because there are zero relationships in production. Had any existed, the
  implicit `ACCEPTED ⇒ progress` rule would have needed backfilling into explicit `PROGRESS` grants; that debt
  is avoided entirely by acting now.
- **`[CHECKPOINT — due 2026-09-19]` and `[CHECKPOINT — due 2026-10-13]` must keep reading what they read.**
  Neither table changes shape or meaning. The 09-19 read gains a caveat — see §14.

---

## 10. Analytics and instrumentation

§18's hypothesis is a **funnel**, and no table can express it. New `AnalyticsEventType` values (added to the
Java enum first, per convention):

| Event | Fired when | Metadata |
|---|---|---|
| `NOTE_SHARED_WITH_CONNECTION` | Owner adds ≥1 recipient | recipient count, whether the pack was ready |
| `NOTE_SHARE_REVOKED` | Owner removes a recipient | — |
| `SHARED_NOTE_OPENED` | Recipient opens a shared note | days since share |
| `SHARED_STUDY_PACK_OPENED` | Recipient opens the pack | — |
| `SHARED_NOTE_COPIED` | Recipient copies to their library | — |

The Phase 1 read is then: *of notes shared, how many were opened; of those, how many led to a practice session
by the recipient within 7 days*. The practice half needs no new event — the existing session and activity rows
already carry it, keyed to the recipient.

**Phase 1 owes a `[CHECKPOINT]` row** in `ROADMAP.md`'s Backlog Index — the repo invariant applies to anything
shipped ahead of its own evidence. **⚠️ DECIDED 2026-08-27 (owner): that row is OBSERVATIONAL and gates NOTHING.**
It is dated deploy + 30 and carries **no kill criterion and no phase dependency**, because with zero connections in
production the read has no denominator and waiting on it would mean waiting indefinitely. Write it so it cannot
later be mistaken for a blocker: the reading limit — *too few to read is a RE-DATE, not a verdict* — goes in the row
itself, following the denominator-clause precedent already used by `[CHECKPOINT — due 2026-09-19]`.

---

## 11. What existing code can be reused

- **The whole relationship layer** — invitations, acceptance, revocation, consent, expiry, rate limiting,
  concurrency. Phase 1 adds no relationship mechanics.
- `copyNote` — one widened permit condition, everything else unchanged.
- The public-note read path is the closest existing model for a non-owner read and is worth reading before
  writing the shared read, though it must not be reused directly (it assumes `PUBLIC`).
- `LinkedLearnerProgressService` and its authorization helper — the pattern for "authorize, then reuse the
  owner-scoped calculation with someone else's id" is exactly right and should be copied for material.
- `ActivityType.MEANINGFUL_STUDY_ACTIVITIES`, streaks and `countStudyDaysThisWeek` — the entire Phase 2
  substrate. No new measurement.
- `SupportedLearnersCard`'s hide-when-empty pattern for the *Shared with you* section.

## 12. What should be removed or reframed

- **Nothing is removed.** Both lightweight paths stay (§16): quiz share links and Study Pack share tokens
  serve one-off, no-account sharing; Learning Connections serves ongoing support. They are not conflated by
  this plan and must not be merged.
- **Reframed:** the note-detail Share action's "you must make this public first" confirmation becomes the
  wrong answer once §5.1 ships — the copy should point at *Share with connections* as the private alternative.
- **Reframed, non-code:** the landing page's *Coming Soon* section and the absent Help coverage (§1.8). Neither
  belongs to Phase 1's engineering scope, but Phase 1 has no audience until they are fixed.

---

## 13. Proposed release boundaries

| Release | Scope | Ships when |
|---|---|---|
| **Phase 1 — Shared Learning Material** | `note_shares`, share/unshare API, recipient note + pack reads, *Shared with you*, Copy to my Library, 5 events, checkpoint row | One release. Migration + backend + frontend — a Codex-sized piece of work, not an inline one. |
| **Phase 2 — Activity Sharing** | `linked_learner_grants` (`ACTIVITY`), directional opt-in UI, momentum view from existing activity rows | Next release after Phase 1. |
| **Phase 3 — Progress Refinement** | `PROGRESS` grants; `requireAcceptedLearnerId` reimplemented over `requireGrant`; permission UI | Next release after Phase 2. **Ship the grant table in Phase 2 and use it in Phase 3** rather than migrating twice. |
| **Phase 4 — Connection Experience** | Shareable invitation links, supporter onboarding, connection management | Sequenced, not gated. No public user search. |
| **Phase 5 — Motivation Experiments** | Deliberately uncommitted — rings, leaderboards and reactions each need their own decision | Last, and the only phase whose *content* is still open. |

**⚠️ DECIDED 2026-08-27 (owner): the phases are SEQUENCED, not evidence-gated.** An earlier draft held Phase 2
behind Phase 1's checkpoint read and Phase 5 behind Phase 2 evidence. With zero connections in production those
reads return nothing, so gating on them is not caution — it is an indefinite stop. **Phases ship one after another
on their own merits.** What survives from the gating idea is only the ordering (a permission model needs the thing
it permits to exist first) and the anti-drift list in §21 of the direction, which is a set of design constraints
rather than a gate.

Phases 2 and 3 share one migration; that is the only cross-phase coupling in this plan.

---

## 14. Risks and decisions still needing the owner

**Both open decisions were taken by the owner on 2026-08-27. Nothing in this plan is now blocked.**

1. ~~**Selecting *Private* on a note with live shares — revoke or block?**~~ **RESOLVED: revoke**, with a
   count-naming confirmation. *(§5.1)*
2. ~~**Should the phases be evidence-gated?**~~ **RESOLVED: no.** Phases are sequenced and ship on their own
   merits; checkpoint rows are recorded but gate nothing. *(§10, §13)* **⚠️ One recording obligation survives and
   is NOT a gate:** Phase 1 ships inside `[CHECKPOINT — due 2026-09-19]`'s window, and that read asks whether
   anyone forms a connection. Phase 1 gives connections their first real purpose, so a rise afterwards is caused
   partly by this feature and partly by the landing-page fix. **Note the deploy split in the row before the read
   runs**, exactly as `v0.74.0`'s secondary metric did. Writing it down costs nothing and delays nothing; failing
   to write it down makes the September read uninterpretable.
3. **Still true, and worth stating in the release notes: the retention hypothesis is not fully testable until
   Phase 2.** The loop in §18 closes only when a
   connection *sees* momentum. Phase 1 tests the first half — will supporters share, will recipients study —
   which is the right first question, but the release notes should not claim to test the loop.
4. **Guardian consent now gates two more capabilities.** A minor's un-consented connection cannot share
   material or activity either, since all three axes require `ACCEPTED`. This is correct and worth stating
   explicitly, because it means a consent lapse cuts more than progress.
5. **`note_shares` grows per note × per recipient.** At the current scale this is irrelevant; it is noted only
   so that a future "share a whole Study Plan" request is recognised as a new decision rather than an
   extension.
