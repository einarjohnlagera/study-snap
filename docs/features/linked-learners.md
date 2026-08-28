# Linked Learners

## Scope

Linked Learners records a directional supporter → learner relationship only after mutual agreement. The relationship layer and the supporter progress read both shipped in `v0.89.0`; controlled note sharing shipped in `v0.91.0`, directional activity sharing in `v0.92.0`, and explicit learner-granted progress permission in `v0.93.0`.

**⚠️ PHASE NUMBERING — read this before using the word "Phase" anywhere in this file.** Two schemes exist and this document has already mixed them. The **canonical** scheme is the ratified Learning Connections plan (`docs/claude-plans/learning-connections-phase-plan.md`, and `ROADMAP.md`): **Phase 1 = shared learning material (`v0.91.0`, shipped), Phase 2 = activity sharing (`v0.92.0`, shipped), Phase 3 = per-scope `PROGRESS` permission (`v0.93.0`, shipped).** A superseded `v0.89.0`-era scheme numbered the *rollout of the relationship layer itself* and called the original supporter progress read "Phase 3". Under the canonical scheme the read is `v0.89.0` behaviour whose implicit acceptance-based authorization was replaced by the Phase 3 grant.

A supported learner remains a full, ordinary NoteLib account with their own login, plan and quota. A supporter may also be a learner. The relationship is separate from `ProfileType`; no supporter profile type exists or is required.

## States and actions

| State | Meaning | Allowed actions |
|---|---|---|
| `PENDING` | A relationship exists but is not yet active: the invitation was accepted and required guardian consent is still outstanding, or an accepted connection was paused after a birth-year correction made consent necessary | Either party may revoke; the learner may record a birth year; the supporter may record required guardian consent; either party may withdraw their own surviving activity grant, and the learner may withdraw their surviving progress grant |
| `ACCEPTED` | The invited party explicitly accepted after any required consent was recorded | Either party may revoke or change their activity grant; the learner may change their progress grant |
| `REVOKED` | Either party ended or declined the relationship | Revoke remains idempotent; a new invitation may create a new row |

Either party can initiate. `initiated_by` records whether the supporter or learner sent the invitation, and only the opposite side can accept it. Knowing an account's email address is therefore never enough to create an accepted relationship.

**⚠️ Since `v0.90.0` a relationship row is created only at acceptance.** An unaccepted invitation lives in `linked_learner_invitations`, not here, so a `PENDING` relationship no longer means "awaiting acceptance" — it means accepted but not yet active. `[CHECKPOINT — due 2026-09-19]` reads this table, and an unresolved invitation is not a connection of any kind.

**⚠️ Rows written BEFORE `V122` still carry the old meaning, and nothing marks them.** A pre-migration `PENDING` row genuinely is awaiting acceptance. Any surface describing a pending connection must therefore stay neutral when no birth-year or consent blocker is present — that combination is the legacy case, and asserting either meaning would be wrong for one of the two populations. `frontend/lib/linked-learner-status.ts` owns that vocabulary for both the Dashboard card and the Learning Connections page, so the two cannot drift apart.

### Concurrent transitions

Relationship state is safe under concurrent requests, and the mechanism is deliberate rather than incidental:

- **The birth-year decision holds a pessimistic write lock on the learner** (`findByIdForUpdate`). Acceptance reads the birth year under that lock, so a correction into the consent range cannot land between the read and the write. Both orderings are safe: if acceptance wins, the correction then observes the new `ACCEPTED` row and pauses it; if the correction wins, acceptance reads the corrected year and requires consent. **Every writer of `users.birth_year` takes this lock** — invite, accept, record and correct — because leaving one out reopens the window through that path.
- **Status transitions are conditional updates**, never read-modify-save. Acceptance applies only while the row is still `PENDING`; revocation applies while it is `PENDING` **or** `ACCEPTED`, so revoking still wins when an acceptance committed first; and the correction's pause applies only while the row is `ACCEPTED`, so a revoke committing mid-correction is not resurrected.
- **Grant creation is conditional on the relationship still being `ACCEPTED` in the insert statement.** A grant request that read `ACCEPTED` but loses a race to relationship revocation writes no row. Because a zero-row insert can also mean an identical live grant already exists, the service rechecks the live row: an idempotent repeat succeeds, while a lost authorization race returns not-found. Withdrawal deliberately has no status predicate, so a surviving grant can always be turned off during a pause.
- Only one row is ever locked, and it is always the learner's, so no lock cycle exists.

These six interleavings are pinned by `LinkedLearnerConcurrencyTest`, which runs two real transactions on two threads and asserts persisted state rather than trusting a returned DTO. Its acceptance/correction cases take the **real** learner-row lock. **⚠️ That part of the harness must model two things at once — the lock AND Hibernate returning a stale managed entity.** Each earlier version modelled one and silently lost the other, in both directions; a harness that skips the lock leaves that mechanism uncovered while still reporting green.

Live duplicate rows for the same supporter → learner direction are prevented by a partial unique index covering `PENDING` and `ACCEPTED`. `REVOKED` history does not block a fresh invitation. A database check and a service guard both prevent self-linking. Invitations carry their own partial unique index over inviter and address, active only while the invitation is `PENDING`.

## Invitation privacy

Invitations are **keyed to the normalized email address, never to a resolved user id**. `v0.90.0` closed the account-existence oracle this section previously documented as open: an invitation row is now written for **any** syntactically valid address, whether or not an account exists behind it, so an unknown address and a real one produce the same generic response *and* the same observable state in the inviter's own list. Nothing about the invitee is looked up at invite time.

This also unlocks inviting someone who has not signed up. The invitation waits against the address; whoever later proves control of that address can accept it.

NoteLib stores the invitation before attempting email delivery. Delivery uses the shared email service and template mechanism. A delivery failure is logged and does not roll back the invitation; sending the same invitation again provides a retry path without creating a second live row.

The **invitation** list never carries a counterparty name — the inviter typed the address and learns nothing further from it. **Relationship** rows do carry the name, and since `v0.90.0` that is safe by construction rather than by withholding: a relationship exists only once the invited party accepted, so both sides have agreed to the link. Withholding it until `accepted_at` was set became actively wrong when `PENDING` changed meaning, since a consent-pending connection legitimately has a null `accepted_at`.

### Invite form validation

The invite form owns its validation (`noValidate`); the browser's native constraint bubble is never used. Errors
are **field-level and inline** — `aria-invalid` plus `aria-describedby` on the offending input, and focus moves
to it. A toast is the wrong surface here because it reports an outcome away from the field that caused it, and a
disabled submit button is wrong because it cannot say *which* field is incomplete.

The birth-year input accepts **digits only, four at most** (`inputMode="numeric"`, not `type="number"` — a scroll
wheel must not silently edit a value this consequential), and carries a **stepper** for ±1 adjustments. Its range
is validated against 1900–current year, mirroring `persistBirthYear`, and that check runs **as the fourth digit is
typed**, not only on submit, so an impossible year cannot sit in the field looking accepted. Both the live check
and the submit check call `birthYearRangeError`, so they cannot diverge. The correction field on the same page
uses the identical control, as do the two acceptance forms (accepting an invitation, and accepting a
relationship) — **all four year inputs on this page are the same component**, and a fifth must not be
hand-rolled. When this was first written only two of the four had been converted; a cold-context audit at the
`v0.91.0` signoff found the other two still raw, one of them with no digit filter and no bounds at all.

**⚠️ The steppers are disabled until four digits are present, and they seed nothing.** Stepping up from an empty
field would have to start somewhere, and any starting year is a declaration the person did not make — the same
reason the field has no default. They also clamp to the server's range, so the stepper can never produce an
invalid year.

**⚠️ A blank birth year is NOT a client-side error, and must not become one.** The year is required only when the
account has none recorded yet, and the client cannot know that before a connection exists — the server owns that
decision. Blocking a blank would lock out a returning learner who declared their year on an earlier connection,
and `page.test.tsx` pins the learner-initiated invite that sends `null`.

**⚠️ There is deliberately NO default birth year, and one must not be added.** The value is account-global and
effectively write-once, driving guardian consent for every connection the account will ever form. A pre-filled
year is a declaration nobody made: defaulting young puts every adult under the consent threshold, defaulting old
disables the gate the threshold exists for, and either way tabbing past the field asserts an age. Collecting it
at link time rather than at signup is pointless if the field answers itself.

### Verified email is the authorization

Because an invitation is addressed to a string rather than to an account, **proving control of that address is the whole basis for acting on it**. Accepting an invitation, listing invitations, revoking one, accepting a relationship, recording a birth year and recording guardian consent all require a verified email. Signup issues a session token without inbox access, so without this gate anyone who guessed or knew an invited address could register it and inherit the invitation.

Two paths are deliberately **left ungated**, because they cut or narrow access rather than granting it, and blocking them would disable a safety mechanism: **revoking a relationship**, and the learner's own **birth-year correction**.

### Expiry

An invitation is a standing offer to whoever controls an address, so it lapses. `expires_at` is set from `studysnap.linked-learners.invitation-ttl-days` (default 30) and is a real column, not `created_at` plus an interval — re-arming a lapsed invitation must not reset when the address was **first** invited, which `created_at` records and the list displays.

Expiry is enforced in three places, not one: the recipient's incoming lookup, the inviter's outgoing list, and acceptance itself. Filtering only at acceptance would leave an expired invitation listed and actionable. Re-inviting a lapsed address **re-arms** it rather than failing, because the live-row unique index would otherwise block that address permanently.

### Rate limiting

Invites are metered on **two** keys: total per inviter, and per inviter **and address**. The second exists because re-posting an address re-sends mail, so a volume-only cap still permits repeatedly mailing one victim. Both come from `studysnap.linked-learners.*` configuration.

**⚠️ The meter runs after the verified-email gate and before anything is written or sent, and is keyed only on caller and address.** It must never depend on whether the address has an account — a limit that behaves differently for real and unknown addresses would reopen the oracle `V122` closed.

### Concurrency

Invitation status transitions are **conditional updates** (`... where status = 'PENDING'`), not read-modify-write, and acceptance **claims the invitation before creating the relationship**. Two callers racing — an accept against a revoke — would otherwise both observe `PENDING`, and the accept would build a relationship, a live cross-user read, behind an invitation the other party had just revoked. A claim that affects zero rows aborts. Revocation is idempotent: zero rows means it was already accepted or revoked, which is not an error.

## Birth year and guardian consent

Birth year is first collected only while forming a link. It is nullable on `users` and is not part of signup, onboarding or profile editing. It is account-global rather than relationship-scoped: the one current value drives the consent decision for every supporter connection the learner forms. NoteLib stores a year rather than an age that becomes stale or a full birthdate that collects unnecessary precision.

The guardian-consent threshold comes from `studysnap.linked-learners.guardian-consent-max-age`. Its shipped default is deliberately conservative engineering configuration pending counsel and is not a legal position. Birth-year precision is handled protectively: if the learner could still be at or below the configured age in the current year, consent is required.

Required consent is a separate persisted fact, not a boolean on the relationship. It records the relationship, learner, attesting supporter, timestamp and attestation version. User-facing attestation wording is explicitly marked as a placeholder for counsel.

### Birth-year correction

The learner may correct only their own account-level birth year from `/linked-learners`. The correction endpoint takes no relationship id or target user id, so a supporter cannot change a learner's declaration. Signup, onboarding and profile editing remain outside this path.

Before applying a correction that would make guardian consent necessary, the UI previews the consequence using the configured threshold and warns with the number of active connections that will pause. In the same transaction as the year update, every `ACCEPTED` relationship where this account is the learner and no consent record exists reverts to `PENDING`, and its `accepted_at` is cleared. Existing consent records keep their relationships `ACCEPTED`; existing `PENDING` and `REVOKED` rows are untouched. Corrections toward an older age do not reactivate or otherwise rewrite relationships.

The `PENDING` transition immediately cuts supporter progress access because the shared cross-user authorization path requires status to be exactly `ACCEPTED`. Both parties see that guardian consent is required and what action unblocks the connection. The current value and nullable `users.birth_year_updated_at` are the only correction data retained; NoteLib stores no declaration history, and inherited pre-correction rows are not back-dated.

### Known circularity

The learner declares their own account-global birth year, while a minor may not be able to consent on their own behalf, and the supporter is often the guardian giving consent. A mistaken or coached declaration therefore affects every future connection, not merely the link being formed. The learner-only correction path limits that risk and re-applies the gate to existing links, but the implementation does not pretend this removes every trust or legal question. It records the current learner declaration and each supporter's relationship-specific attestation as separate facts so the limitation is visible and auditable; counsel still owns the threshold and final attestation wording.

For supporter-initiated invitations, the invited learner provides their year during acceptance. For **learner-initiated** invitations the year is captured **at invite time**, from the learner themselves, because no relationship exists yet for them to record it against and only the learner may declare it — without this the supporter's acceptance would need a year nobody could supply, which was a permanent dead end before `v0.90.0`. A link that requires consent is created in `PENDING` and stays there until the consent record exists.

**⚠️ Invite-time capture widens the `v0.89.1` circularity above**, since a write-once account-global year can now be declared before any counterparty exists. The learner-only correction path remains the mitigation.

## Relationship-list privacy boundary

The caller's link list contains only:

- counterparty display name and email;
- caller and initiator direction;
- relationship status;
- created, accepted and revoked dates;
- workflow flags needed to finish birth-year and consent steps.
- four grant-derived states: activity and progress shared by the caller, plus activity and progress actually shared with the caller. `*SharedByMe` reflects the live row even while a connection is paused; `*SharedWithMe` additionally requires `ACCEPTED`, because it represents current access.

The relationship list itself contains no readiness counts, progress aggregates, score, quiz performance, note, Study Pack, collection or `ConceptHealth` data; its progress booleans are permission state only.

Notes remain private. The link is free metadata and does not pool, transfer or change subscription or generation quota.

## Directional activity sharing

Accepting a Learning Connection grants no activity access. It creates only the capacity for either person to
opt in to sharing their own activity. Grants are directional: A sharing with B never writes, implies or enables
B sharing with A. The connection response therefore carries two separately computed fields,
`activitySharedByMe` and `activitySharedWithMe`; neither is derived from the other.

Each person can change only the grant whose `from_user_id` is their authenticated user id. Relationship and
counterparty ids are derived from the loaded relationship, never supplied by the request. Turning sharing off
sets `revoked_at`; it never deletes history, and turning it back on inserts a new row. Both operations are
idempotent. The same table also stores `PROGRESS`; `v0.93.0` began using that scope without another migration.

Every momentum read reloads the relationship and requires it to be exactly `ACCEPTED`, then requires the live
counterparty-to-caller `ACTIVITY` grant. There is no cache or grace period, so revoking either the grant or the
relationship cuts the next read. A birth-year correction that pauses `ACCEPTED` to `PENDING` cuts it through the
same status predicate.

Guardian consent is deliberately asymmetric. It is re-asserted only when the shared data belongs to the
relationship's learner. Reading a consent-requiring learner's activity needs the relationship consent row;
reading the supporter's own shared activity does not, even when the counterparty learner requires consent. This
is defence in depth over the `PENDING` gate, not a second age rule: one shared `GuardianConsentPolicy` owns the
configuration-backed age decision.

**That defence fails CLOSED.** If the learner's data is requested and their birth year is unknown, the read is
denied rather than waved through. Acceptance records the year, so an `ACCEPTED` relationship always carries one
and this denies nobody today — which is precisely why it must not fail open. The only way to reach it with a
null year is a future grant path that produced `ACCEPTED` without one, and that is the exact state the check
exists to catch; treating "unknown age" as "no consent needed" would let such a path silently reopen the
`v0.89.1` gate.

The momentum response is a read-only projection of activity NoteLib already records: display name, engagement
mode, current streak, longest streak and meaningful study days this week. It adds no activity type or score,
deliberately excludes `OPENED_STUDY_PACK` through the existing `MEANINGFUL_STUDY_ACTIVITIES` definition, and
writes no activity event, `ConceptHealth`, progress timestamp or user state. Zeroes render as an honest empty
answer rather than being hidden.

Because the progress response contains those same four engagement fields, supporter-facing copy describes
activity **access** across both scopes. When `ACTIVITY` is off but `PROGRESS` is on, the card says activity is
visible through shared progress; it does not claim an activity grant exists, and the momentum panel remains
unavailable.

### Activity-sharing analytics

Phase 2's grant-to-view loop uses three product-analytics events, separate from learner activity tracking:

- `CONNECTION_ACTIVITY_SHARED` fires only when enabling sharing inserts a new live grant;
- `CONNECTION_ACTIVITY_SHARE_REVOKED` fires only when disabling sharing revokes a live grant;
- `CONNECTION_ACTIVITY_VIEWED` fires only after an authorized momentum response is successfully assembled.

The relationship id is the analytics entity id. Grant events carry only the caller's relationship role
(`SUPPORTER` or `LEARNER`); view metadata is empty. Repeating an already-applied grant setting emits nothing,
and denied or failed momentum reads emit nothing. Analytics publication is best-effort and cannot fail the grant
transition or momentum response. These events contain no streak, study-day, score, mastery, concept, title or
other learning-content field, and they do not create `UserActivityEventEntity` rows or change learning state.

## Learner-granted supporter progress read

The aggregate read shipped in `v0.89.0`; `v0.93.0` replaced its implicit `ACCEPTED`-means-access rule with an explicit `PROGRESS` grant. No payload field changed.

**The owner-facing share listing is relationship-aware.** `GET /notes/{id}/shares` returns only live shares whose
relationship is still `ACCEPTED`, matching what `PUT` will accept. Filtering on `revoked_at` alone kept a lapsed
connection listed and made a round-tripped list fail validation. **⚠️ The diff source inside `PUT` stays
unfiltered** — it must see the true live set, or removing a lapsed recipient would never revoke their row and
re-adding them would collide on `ux_note_shares_live`.

**Authorization faults deny; unreadable material does not.** A recipient's mid-session access recheck tolerates a
Study Pack it cannot *read* — completing a session whose pack was deleted or corrupted has always succeeded, and
the caller owns the session regardless. But a fault raised while *deciding* whether a non-owner may read shared
material is not evidence of access, so it denies. Unknown is not permission, the same rule the `v0.89.1` consent
gate applies.

The learner alone may create a `PROGRESS` grant, directed learner → supporter. Activity remains grantable by either side; progress does not. The write requires a verified email, relationship membership and `ACCEPTED` when enabling. Disabling requires membership but not `ACCEPTED`, so a learner can withdraw a surviving row while a birth-year correction has paused the relationship. Repeat writes are idempotent, and analytics fires only for a real insert or revoke.

The progress route is addressed only as `/linked-learners/{relationshipId}/progress`. It never accepts a learner user id. The authorization helper keeps the caller-is-supporter assertion explicit, then requires the live learner-to-supporter `PROGRESS` grant through the shared grant authorization service. That service re-verifies `ACCEPTED`, direction, guardian consent for learner data and the caller's verified email on every read. A learner cannot use the route to read their supporter even if a reverse-direction row somehow exists. A third party, no grant, a `PENDING` link, a `REVOKED` link and a missing relationship receive no data through the progress route's established `LINKED_LEARNER_PROGRESS_NOT_FOUND` contract.

The caller must also have a **verified email**. That is redundant while every path granting an `ACCEPTED` relationship is itself gated, and it is deliberate: it means a future grant path that loses its gate cannot silently open this read too. It costs nothing, because `email_verified_at` is monotonic — nothing clears it, and an address change re-stamps it only once the new address is confirmed.

Every request performs that authorization again. Grant revocation, relationship revocation, or a birth-year correction therefore cuts access immediately, with no cached view or grace period. The read is transactionally read-only and reuses the existing owner-scoped Dashboard, Progress and collection calculations with the authorized learner id. It creates no session, changes no `ConceptHealth`, progress timestamp, streak or engagement counter, and attributes no learner activity event. A successful response emits only relationship-scoped `CONNECTION_PROGRESS_VIEWED` product analytics; denied reads emit nothing and analytics failure cannot fail the response.

### What a supporter can see

- quiz-performance aggregates: recent average, recent best and Study Packs reviewed;
- engagement aggregates: current and longest streak, study days this week and engagement mode;
- readiness counts: total, mastered, due and not-started concepts, plus the derived readiness percentage;
- collection counts: plans, total items, ready items and practiced items;
- the counterparty display identity already present on the relationship.

A learner with no activity returns a successful empty aggregate and is shown as **No learning activity yet**. A supporter sees *View progress* only when `progressSharedWithMe` is true. A pending connection shows its existing paused explanation, offers no view action, and leaves the learner's live sharing toggles reachable for withdrawal.

### Free-text names decision

Phase 3 deliberately chooses **counts and states only**. It does not expose concept names, subjects, note titles, Study Pack titles, collection titles or any other learner-authored or generated free text. Although an existing owner DTO carries concept names, reusing that DTO cross-user would let personal text cross the privacy line. Aggregate counts answer whether the learner is on track and where support may be needed without revealing what they wrote or studied. Any future proposal to expose names is a new privacy decision, not a DTO convenience.

The absolute exclusion remains: supporters never receive note bodies, note content, summaries, Study Pack prose or other learning material.

## Shared learning material

An accepted Learning Connection creates the capacity to share; it shares nothing automatically and grants nothing reciprocally. Controlled material access is stored per note and grantee in `note_shares`, including the owner id and the exact `relationship_id` that authorized the grant. The note remains `PRIVATE`; no relationship or share row changes `NoteVisibility`, and no shared note enters Explore.

Every recipient request re-derives access in this order: a live share exists for the note and caller, its relationship exists and is exactly `ACCEPTED`, and the note or Study Pack still exists. There is no cache or grace period. Revoking the share, revoking the relationship, or a birth-year correction that returns the relationship to `PENDING` cuts access on the next request. All denials use the same not-found response and no endpoint accepts an owner or learner user id.

The owner writes a complete desired relationship-id set transactionally. Any missing, unrelated, pending, or revoked relationship rejects the whole request before a row changes. Omitted live shares receive `revoked_at`; re-sharing inserts a new historical row. Sending the unchanged set writes nothing and emits no duplicate analytics.

Recipient note and Study Pack responses are separate allowlist DTOs containing learning material and owner display provenance only. They never carry the owner's `ConceptHealth`, mastery, weak concepts, attempts, scores, sessions, streaks, or progress timestamps. Opening a recipient Study Pack never records `OPENED_STUDY_PACK` for the owner. Genuine practice creates sessions and updates `ConceptHealth` for the recipient user id; the owner's state is neither read nor written.

## Dashboard presentation

The Dashboard adds a **People you support** section for accounts with live supporter-side relationships. An accepted link leads to the relationship-scoped progress view only when the learner's live `PROGRESS` grant makes `progressSharedWithMe` true. Accepted-without-grant and pending links offer no progress action; pending links name the actual blocker — the learner's birth year, guardian consent outstanding, or consent recorded and activation finishing — and never claim acceptance is still required, which since `V122` is false for every row written after the migration. This section is additive: a person who is both a learner and a supporter sees their own learning workspace and the people they support together, without a mode switch or a profile-type distinction. A supporter with no notes of their own therefore still has a useful home surface.
