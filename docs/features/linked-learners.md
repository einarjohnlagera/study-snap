# Linked Learners

## Scope

Linked Learners records a directional supporter → learner relationship only after mutual agreement. Phase 2 established the relationship; Phase 3 lets the supporter read a deliberately narrow progress projection after the relationship is accepted.

A supported learner remains a full, ordinary NoteLib account with their own login, plan and quota. A supporter may also be a learner. The relationship is separate from `ProfileType`; no supporter profile type exists or is required.

## States and actions

| State | Meaning | Allowed actions |
|---|---|---|
| `PENDING` | One party invited and the other has not completed acceptance, or an accepted connection was paused after a birth-year correction made guardian consent necessary | Invited party may accept; either party may revoke; the learner may record a birth year; the supporter may record required guardian consent |
| `ACCEPTED` | The invited party explicitly accepted after any required consent was recorded | Either party may revoke |
| `REVOKED` | Either party ended or declined the relationship | Revoke remains idempotent; a new invitation may create a new row |

Either party can initiate. `initiated_by` records whether the supporter or learner sent the invitation, and only the opposite side can accept it. Knowing an account's email address is therefore never enough to create an accepted relationship.

Live duplicate rows for the same supporter → learner direction are prevented by a partial unique index covering `PENDING` and `ACCEPTED`. `REVOKED` history does not block a fresh invitation. A database check and a service guard both prevent self-linking.

## Invitation privacy

Invitations are addressed by normalized email. The API always returns the same generic response for an active account, an unknown email and an inactive account. **⚠️ This is NOT a full anonymity boundary, and the doc previously overclaimed here.** The *response* is identical, but a real account also writes a `PENDING` row that then appears in the inviter's own list, so account existence remains observable to an authenticated caller. The counterparty's **display name is withheld until the link is actually accepted**, so the list no longer harvests names — but existence still leaks. The durable fix is email-keyed invitations; see the `v0.89.0` Known limitations in `RELEASES.md`.

For a real active account, NoteLib stores the pending invitation before attempting email delivery. Delivery uses the shared email service and template mechanism. A delivery failure is logged and does not roll back the invitation; sending the same invitation again provides a retry path without creating a second live row.

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

For supporter-initiated invitations, the invited learner can provide their year during acceptance. For learner-initiated invitations, the learner can record it on the pending link before the invited supporter accepts. A link that requires consent remains `PENDING` until the consent record exists.

## Relationship-list privacy boundary

The caller's link list contains only:

- counterparty display name and email;
- caller and initiator direction;
- relationship status;
- created, accepted and revoked dates;
- workflow flags needed to finish birth-year and consent steps.

The relationship list itself contains no readiness, progress, score, quiz performance, note, Study Pack, collection or `ConceptHealth` data.

Notes remain private. The link is free metadata and does not pool, transfer or change subscription or generation quota.

## Phase 3 supporter progress read

The progress route is addressed only as `/linked-learners/{relationshipId}/progress`. It never accepts a learner user id. One shared authorization helper loads that relationship, verifies that the caller is its supporter, verifies that its status is exactly `ACCEPTED`, and returns the authorized learner id used by the aggregate services. A learner cannot use the route to read their supporter. A third party, a `PENDING` link, a `REVOKED` link and a missing relationship receive no data; revoked and missing relationships use the same not-found response.

Every request performs that authorization again. Revocation therefore cuts access immediately, with no cached view or grace period. The read is transactionally read-only and reuses the existing owner-scoped Dashboard, Progress and collection calculations with the authorized learner id. It creates no session, changes no `ConceptHealth`, progress timestamp, streak or engagement counter, and attributes no learner analytics event.

### What a supporter can see

- quiz-performance aggregates: recent average, recent best and Study Packs reviewed;
- engagement aggregates: current and longest streak, study days this week and engagement mode;
- readiness counts: total, mastered, due and not-started concepts, plus the derived readiness percentage;
- collection counts: plans, total items, ready items and practiced items;
- the counterparty display identity already present on the relationship.

A learner with no activity returns a successful empty aggregate and is shown as **No learning activity yet**. A pending invitation is shown as pending and has no progress action.

### Free-text names decision

Phase 3 deliberately chooses **counts and states only**. It does not expose concept names, subjects, note titles, Study Pack titles, collection titles or any other learner-authored or generated free text. Although an existing owner DTO carries concept names, reusing that DTO cross-user would let personal text cross the privacy line. Aggregate counts answer whether the learner is on track and where support may be needed without revealing what they wrote or studied. Any future proposal to expose names is a new privacy decision, not a DTO convenience.

The absolute exclusion remains: supporters never receive note bodies, note content, summaries, Study Pack prose or other learning material.

## Dashboard presentation

The Dashboard adds a **People you support** section for accounts with live supporter-side relationships. Accepted links lead to the relationship-scoped progress view; pending links explain that acceptance is still required. This section is additive: a person who is both a learner and a supporter sees their own learning workspace and the people they support together, without a mode switch or a profile-type distinction. A supporter with no notes of their own therefore still has a useful home surface.
