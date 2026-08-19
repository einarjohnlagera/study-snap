# Linked Learners

## Scope

Linked Learners is the Phase 2 relationship layer for helping another learner. It records a directional supporter → learner relationship only after mutual agreement. It does not grant access to either person's learning activity.

A supported learner remains a full, ordinary NoteLib account with their own login, plan and quota. A supporter may also be a learner. The relationship is separate from `ProfileType`; no supporter profile type exists or is required.

## States and actions

| State | Meaning | Allowed actions |
|---|---|---|
| `PENDING` | One party invited and the other has not completed acceptance | Invited party may accept; either party may revoke; the learner may record a birth year; the supporter may record required guardian consent |
| `ACCEPTED` | The invited party explicitly accepted after any required consent was recorded | Either party may revoke |
| `REVOKED` | Either party ended or declined the relationship | Revoke remains idempotent; a new invitation may create a new row |

Either party can initiate. `initiated_by` records whether the supporter or learner sent the invitation, and only the opposite side can accept it. Knowing an account's email address is therefore never enough to create an accepted relationship.

Live duplicate rows for the same supporter → learner direction are prevented by a partial unique index covering `PENDING` and `ACCEPTED`. `REVOKED` history does not block a fresh invitation. A database check and a service guard both prevent self-linking.

## Invitation privacy

Invitations are addressed by normalized email. The API always returns the same generic response for an active account, an unknown email and an inactive account. This keeps the authenticated endpoint from becoming an account-existence oracle.

For a real active account, NoteLib stores the pending invitation before attempting email delivery. Delivery uses the shared email service and template mechanism. A delivery failure is logged and does not roll back the invitation; sending the same invitation again provides a retry path without creating a second live row.

## Birth year and guardian consent

Birth year is collected only while forming a link. It is nullable on `users`, is not part of signup, onboarding or profile editing, and is not requested again once recorded. NoteLib stores a year rather than an age that becomes stale or a full birthdate that collects unnecessary precision.

The guardian-consent threshold comes from `studysnap.linked-learners.guardian-consent-max-age`. Its shipped default is deliberately conservative engineering configuration pending counsel and is not a legal position. Birth-year precision is handled protectively: if the learner could still be at or below the configured age in the current year, consent is required.

Required consent is a separate persisted fact, not a boolean on the relationship. It records the relationship, learner, attesting supporter, timestamp and attestation version. User-facing attestation wording is explicitly marked as a placeholder for counsel.

### Known circularity

The learner declares their own birth year, while a minor may not be able to consent on their own behalf, and the supporter is often the guardian giving consent. The implementation does not pretend this removes every trust or legal question. It records the learner's declaration and the supporter's attestation as separate facts so the limitation is visible and auditable; counsel still owns the threshold and final attestation wording.

For supporter-initiated invitations, the invited learner can provide their year during acceptance. For learner-initiated invitations, the learner can record it on the pending link before the invited supporter accepts. A link that requires consent remains `PENDING` until the consent record exists.

## Phase 2 privacy boundary

The caller's link list contains only:

- counterparty display name and email;
- caller and initiator direction;
- relationship status;
- created, accepted and revoked dates;
- workflow flags needed to finish birth-year and consent steps.

It contains no readiness, progress, score, quiz performance, note, Study Pack, collection or `ConceptHealth` data. Phase 2 adds no cross-user activity endpoint or query. Phase 3 is the first cross-user read and must authorize every such read against an `ACCEPTED` relationship; none of that behavior is claimed here.

Notes remain private. The link is free metadata and does not pool, transfer or change subscription or generation quota.
