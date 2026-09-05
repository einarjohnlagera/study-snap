# Quiz Assignments Through Learning Connections — Stage 1 Audit

**Status:** FUTURE CAPABILITY — **NOT IMPLEMENTED, NOT SCHEDULED.** No code, no migration, no
classroom build. This document exists to preserve architectural direction so the current shared-quiz
work does not drift into the wrong abstraction.
**Date:** 2026-09-05. Every claim carries a `file:line` anchor.
**Related:** `shared-quiz-recipient-experience-plan.md`, `supporter-progress-visibility-audit.md`.

**Central contract:** *Sharing is lightweight. Assignment is relational.*

---

## 1. Executive judgment

**The primitive is buildable on what exists, and one existing mechanism solves the hardest
requirement outright.** Three findings shape everything below.

### ⚠️ Finding 1 — the content-stability rule (§11) splits along the two existing quiz kinds

A share link points at **one of two** things (`QuizShareLinkEntity:24-28`, resolved at
`resolveSharedQuestions`):

| Kind | Storage | Stable? |
|---|---|---|
| **Combined quiz** | `combined_quizzes.sections` JSONB — `CombinedQuizSection(title, questions)`, documented as *"a stored snapshot section; title is copied from the source note, **never resolved later**"* | **Already immutable** |
| **Single-note quiz** | `generated_quizzes.questions` — regeneration **reuses the row and overwrites questions**, id unchanged | **Mutable — changes underneath** |

**⚠️ And `v0.110.2`'s fix does not transfer.** That release solved the same hazard for share links by
**deactivating the link** when the quiz is regenerated. For an assignment, deactivation would destroy
the assignment — the learner's outstanding task would vanish because the sender edited something.

**Recommendation: snapshot every assignment into a `combined_quizzes` row.**
`CombinedQuizService.assemble` requires only `!noteIds.isEmpty()` (`:76`) with a ceiling of
`MAX_SOURCE_NOTES = 20` — **there is no minimum of two.** So a one-section combined quiz is legal
today, which means:

- assignment content is **stable by construction**, satisfying §11 with no new content table;
- **single-note and multi-note assignments share one abstraction**, answering §9 and §30.H together;
- the flatten path already exists (`flattenCombinedQuestions`), so delivery is unchanged.

### ⚠️ Finding 2 — assignment completion CANNOT feed the learner's mastery

`ConceptHealthService` resolves the pack with **`findByIdAndOwnerUserId(studyPackId, userId)`**
(`:98`) — a ConceptHealth write requires the user to **own** the Study Pack. An assigned quiz's
source packs belong to the **assigner**. So writing mastery for the learner would fail the lookup.

**This is structural, not a policy choice.** §26's caution is correct and stronger than stated:
assignment completion cannot feed `ConceptHealth` at all unless the learner independently owns a copy
of the source note. **Do not "fix" this by relaxing the ownership check** — it is the guard that keeps
one person's assessment evidence out of another person's mastery.

### ⚠️ Finding 3 — "relationship-neutral" collides with a directional substrate

`LinkedLearnerRelationshipEntity` stores **`supporter_user_id` and `learner_user_id`, both NOT
NULL** (`:25-29`). Roles are fixed at invitation time. §4 wants the primitive to serve
*sibling → sibling* and *study partner → study partner*, but in those pairs the roles are **arbitrary**
— and if assignment is restricted to supporter → learner, **the partner recorded as "learner" can
never assign back.**

**This is a genuine owner decision (§9.1), not an implementation detail.** It does not require a
relationship-type column (which stays forbidden); it requires deciding whether the assignment
primitive reads the supporter/learner axis at all.

---

## 2. §30.A — What currently represents the quiz a recipient answers

`quiz_share_links` (token, `owner_user_id`, `is_active`, and **exactly one** of `generated_quiz_id` /
`combined_quiz_id`). The recipient payload is `PublicSharedQuizResponse(quizId, noteTitle,
questions)` with `PublicQuizItem(question, choices, concept, questionFormat)`.

**⚠️ Nothing represents a completion.** `getSharedQuizResults` (`QuizShareLinkService:182`) grades in
memory and returns — zero `.save(`, zero analytics, zero activity tracking in the method — and a
shared quiz creates **no session** (`quick_review_sessions`: zero references from
`GeneratedQuizService` / `QuizShareLinkService`).

**So Assignment introduces the product's first quiz-completion record.** Its privacy contract is
already decided (2026-09-05, recorded in `supporter-progress-visibility-audit.md` §11): **signed-in
recipients only, surfaced only within an accepted relationship, anonymous completions never
recorded.** Assignment inherits that decision rather than reopening it — and since assignment already
requires an accepted connection (§3), the recipient is signed-in by construction.

## 3. §30.B — Snapshot semantics

**Yes — existing persistence is sufficient.** See Finding 1. `combined_quizzes` is already a stored
snapshot with an explicit "never resolved later" contract, and it accepts a single section.

**⚠️ Do NOT point an assignment at `generated_quizzes`.** Regeneration overwrites that row's
questions in place, which is precisely the *"assignment points at mutable creator quiz and changes
underneath learner"* outcome §11 forbids.

**⚠️ Do NOT invent a third snapshot mechanism.** One snapshot concept, already tested in production.

## 4. §30.C — What relationship state authorizes assignment

`ACCEPTED` — the same state every cross-user read requires, re-verified per action, never cached.

**⚠️ Two states need explicit handling and are easy to miss:**

- **The consent pause.** A `v0.89.1` birth-year correction moves `ACCEPTED → PENDING` while grant rows
  survive by design. An assignment must not be *creatable* during a pause; **existing assignments
  should persist but not be startable** until the relationship returns to `ACCEPTED`.
- **`EXPIRED`** (`v0.97.0`) and **`REVOKED`** are terminal — see the matrix in §12.

## 5. §30.D — Permission model (§20)

**The key semantic: an assignment is relationship-owned state, not learner-global activity.** The
assigner created the artifact, chose the recipient and already knows the assignment exists.
Completion of *their own* assignment discloses nothing about the learner's private study system.

Recommended split — **each field judged by what it discloses, not by who created the assignment**:

| Field | Requires | Reasoning |
|---|---|---|
| **Assignment exists / was sent** | Accepted relationship only | The assigner's own act |
| **Completed / not completed** | Accepted relationship only | Relationship-owned state about a known artifact |
| **Score** | **`PROGRESS` grant** | Assessment performance — the exact class `PROGRESS` governs |
| **Opened / in-progress** | **`ACTIVITY` grant** | Behavioural signal — when and whether they engaged |
| **Learner's other quiz history** | `PROGRESS` grant | Unchanged, unrelated to assignment |

**⚠️ Do NOT expose everything because the supporter created the assignment.** Completion is a fact
about the assignment; **score is a fact about the learner.** Collapsing them would let anyone with an
accepted connection extract assessment performance without a `PROGRESS` grant — a back door around
the learner's control.

**⚠️ Permission revocation is not retroactive deletion.** Revoking `PROGRESS` must hide the score
**from that point forward**; it must not delete the completion record or the learner's history.

## 6. §30.E — Assigner/recipient without new role semantics

**Representable with no new user-role concepts.** The relationship already names both parties, and an
assignment record needs only `(relationship_id, assigned_by_user_id, ...)` — the recipient is the
other party.

**⚠️ Do NOT add a relationship-type column** (`GUARDIAN | TUTOR | PARTNER`) — locked since `v0.92.0`,
and it would immediately invite gating on it. **⚠️ Do NOT add `ProfileType` gating** — `v0.89.0`
records that axis error as the thing Learning Connections exists to correct. Teacher-specific tooling
sits **above** this primitive later (§24), never inside it.

**⚠️ But see Finding 3** — whether `assigned_by_user_id` may be the *learner* side is undecided.

## 7. §30.F/G — Surfaces

**Learner (§13, §25):** *Assigned practice* belongs on the **Dashboard as a bounded card**, beside
the existing `SupportedLearnersCard` pattern, plus a full list under Learning Connections.
**⚠️ Do NOT create a new top-level navigation destination** without evidence, and **⚠️ do not let
assignments outrank the learner's own next-step guidance** — NoteLib's retention promise is *always
know what to learn next*, and an assignment is **one** candidate next action, not the whole
Dashboard. Recommended rule: the card shows a **count and an entry point**, never a queue that
displaces Today Focus / Continue Studying.

**Supporter (§18):** an *Assigned practice* section on the existing supporter progress surface —
the same page Phase 2 of the supporter plan consolidates. **No new page.**

## 8. §30.I — Learning evidence

| Evidence | Flows from assignment completion? |
|---|---|
| **ConceptHealth / mastery** | **No — structurally impossible** (Finding 2) |
| **Quiz performance (`getMasterySnapshot`)** | **No** — it reads `quick_review_sessions`; an assignment creates none |
| **Activity / streaks** | **Only if** completion writes a `user_activity_event`; a deliberate decision, not automatic |
| **Assignment history** | **Yes** — the completion record itself |

**⚠️ Recommendation: assignment completion is assignment evidence only.** Do not back-door it into
mastery or quiz performance. `v0.104.0`'s provenance work means evidence must be attributable to a
pack the learner owns; an assigned quiz fails that test. **Assessment-evidence integrity outranks
feature integration** — §26 says so and the repo enforces it.

**⚠️ If activity credit is ever wanted**, it must be a separate explicit decision, and it would make
assignment completion visible through the `ACTIVITY` grant as a side effect — which is a permission
consequence, not a metrics tweak.

## 9. §30.J — Teacher extensibility, minimally preserved

Three cheap decisions now that avoid blocking one-to-many later, **without building any of it**:

1. **Key the assignment on `(assignment_id, recipient_user_id)` rather than assuming one recipient
   per assignment record** — or at minimum do not make one-to-one an invariant that a later
   migration must undo.
2. **Keep the content snapshot separate from the recipient row** — one snapshot, N recipients later.
3. **Do not put due dates, grading or roster fields in the model now** (§22, §23) — absent columns are
   easy to add; wrong semantics are not.

**⚠️ Nothing else.** Do not build classroom, roster, class code, sections, gradebook, deadlines,
score distributions or exports. Phase D is **not assumed inevitable** (§28).

---

## 10. §21 — Notifications

**Verified: there is no in-app notification infrastructure.** The only notification-shaped services
are `FeedbackService` and `SubscriptionExpiryEmailService`; `EmailService` / `EmailTemplateService`
exist for transactional mail.

**⚠️ Assignment must be useful with zero notifications** — the learner discovers assignments on the
Dashboard, the supporter sees status on the progress page. **Do NOT build notifications, reminders,
due-date nagging or recurring prompts in any early phase.** This constraint is also shared with the
supporter plan's Phase 1, which needs a learner-visible request prompt for the same reason.

## 11. §19 — Assignment titles and privacy

| Title source | Safe to show the supporter? | Safe to show the learner? |
|---|---|---|
| Quiz title the supporter authored | **Yes** — their own artifact | Yes |
| Source note title, note owned by the **supporter** | **Yes** | Yes — they were sent its quiz |
| Source note title, note owned by the **learner** | **⚠️ No** — leaks a learner-private title back | Yes (their own) |
| Combined quiz section titles | Follows the same rule per section | Yes |

**⚠️ The third row is the trap.** If assignment ever allows assigning a quiz built from the
*learner's* note, the title must not be echoed to the supporter. **Simplest safe contract for v1:
an assignment may only be created from a quiz the assigner owns** — which makes every displayed title
the assigner's own and removes the leak by construction.

## 12. §31 — Product-state matrix

| Event | Assignment remains? | Learner can take it? | Supporter sees | Source material | Result stays historical |
|---|---|---|---|---|---|
| Assignment created | yes | yes | *Not started* | per public/share rules | n/a |
| Learner opens | yes | yes | *Opened* **only with `ACTIVITY`** | unchanged | n/a |
| Learner completes | yes | no (done) | *Completed*; **score only with `PROGRESS`** | continuation offered | **yes** |
| Learner dismisses | **yes — hidden, not deleted** | no (until unhidden) | *Not started* — **no "declined" state** | n/a | n/a |
| Supporter disconnects / relationship `REVOKED` | **yes, as history** | **no** | nothing (no accepted relationship) | no | yes, to the learner |
| Relationship `EXPIRED` | yes, as history | no | nothing | no | yes |
| Consent pause (`ACCEPTED → PENDING`) | yes | **no — paused** | paused | no | yes |
| `ACTIVITY` revoked | yes | yes | loses opened/in-progress **going forward** | unchanged | yes |
| `PROGRESS` revoked | yes | yes | loses **score**; keeps completed/not | unchanged | yes |
| Source note becomes private | yes | yes | unchanged | **continuation disappears** | yes |
| Source note regenerated | yes | yes | unchanged | new content on the note page | yes |
| Source **quiz** regenerated | **yes, unchanged** | yes | unchanged | unchanged | yes |
| Assigner deletes source note | **yes** | **yes** | unchanged | **no continuation** | yes |
| Recipient account deleted | purged with the account | n/a | nothing | n/a | no |

**⚠️ The "source quiz regenerated" row is the whole point of Finding 1** — with a snapshot the
assignment is unaffected, which is the behaviour §11 demands. Without one it would silently change.

**⚠️ "Dismiss hides, never deletes"** (§15): the learner gets agency without destroying completion
evidence, and the supporter must not be shown a distinct *declined* state — dismissed reads as *not
started*, mirroring the supporter plan's rule that declined and ignored are indistinguishable.

## 13. §27 — Share vs Assignment, corrected against the repo

| Capability | Share link | Assignment |
|---|---|---|
| Connection required | **No** — `/quiz/share/**` is `permitAll` | **Yes** — `ACCEPTED` |
| Anonymous recipient | **Yes** | **No** — by construction |
| Content stability | **Single-note: NO** (mutable row); combined: yes | **Yes** — snapshot required |
| Persistent recipient task | No | Yes |
| Sender tracks completion | **No — nothing is recorded today** | Yes, permission-aware |
| Multiple outstanding items | URLs may exist; no inbox | Yes |
| Learner inbox | No | Yes |
| Source note automatically shared | **No** | **No** — unchanged |
| Feeds mastery / ConceptHealth | No | **No** (Finding 2) |
| Classroom semantics | No | Future layer only |

**⚠️ Assignment never replaces sharing** (§2, §29). The lightweight path stays: anonymous recipients,
external channels, no connection, one-off use.

## 14. §28 — Evolution path (conceptual only)

**Phase A** one-to-one assignment on an accepted connection · **Phase B** richer history if usage
earns it · **Phase C** one quiz → several connected recipients · **Phase D** teacher classroom layer
**only if teacher usage justifies it — not assumed inevitable.**

---

## 15. §32 — Genuine owner decisions

1. **Can the learner-side party assign back?** (Finding 3) The relationship is directional by
   construction, so *study partner → study partner* only works if assignment ignores the
   supporter/learner axis. **Recommendation: allow either party to assign**, since the primitive is
   defined as *"one user assigns a quiz to another connected user"* and restricting it silently
   breaks two of §4's stated use cases.
2. **Does completion status require `PROGRESS`?** **Recommendation: no** — completion is
   relationship-owned state about the assigner's own artifact; **score** requires `PROGRESS` (§5).
3. **Does opened/in-progress require `ACTIVITY`?** **Recommendation: yes** — it is a behavioural
   signal, and exempting it would let assignment leak engagement data around the `ACTIVITY` grant.
4. **Decline vs dismiss?** **Recommendation: dismiss only** — hides locally, no supporter-visible
   declined state, no new permission. A distinct *decline* creates a social signal the connection
   model deliberately avoids.
5. **Where does *Assigned practice* live?** **Recommendation: a bounded Dashboard card + a list under
   Learning Connections**; no new top-level nav.
6. **Should assignment completion write an activity event?** **Recommendation: no in Phase A** — it
   would make completion visible through `ACTIVITY` as a side effect (§8).

**Pre-answered and NOT reopened:** snapshot reuses `combined_quizzes` (§3); mastery cannot be fed
(§8); the completion-record privacy contract is already decided (§2).

---

## 16. §29 — Anti-drift checklist

- **⚠️ Do NOT implement any of this now.** Audit only.
- **⚠️ Do NOT replace share links with assignments**, or require a connection for ordinary sharing.
- **⚠️ Do NOT point an assignment at a mutable `generated_quizzes` row** — snapshot it.
- **⚠️ Do NOT invent a second snapshot mechanism** — `combined_quizzes` accepts one section.
- **⚠️ Do NOT feed ConceptHealth or quiz performance from assignment completion**, and **do not relax
  `findByIdAndOwnerUserId` in `ConceptHealthService`** to make it possible.
- **⚠️ Do NOT expose score without `PROGRESS`, or opened/in-progress without `ACTIVITY`**, merely
  because the supporter created the assignment.
- **⚠️ Do NOT auto-share source Notes** — assigning a quiz is not sharing its Note; private sources
  stay invisible, including between accepted connections.
- **⚠️ Do NOT echo a learner-owned note title back to the supporter** (§11).
- **⚠️ Do NOT build classroom, roster, class code, sections, gradebook, due dates, deadlines,
  grading, score distributions, exports, or multi-recipient assignment.**
- **⚠️ Do NOT build assignment-specific question generation** — Assignment is a delivery and tracking
  layer over the existing engine.
- **⚠️ Do NOT add bulk assignment initially**; multiple assignments accumulating naturally is not the
  same as assigning several at once.
- **⚠️ Do NOT build notifications, reminders or due-date nagging.**
- **⚠️ Do NOT add a relationship-type column, a `ProfileType` gate, or a fourth connection permission**
  unless §15.1–§15.3 force one.
- **⚠️ Do NOT let assignments dominate the learner's Dashboard** or displace their own next step.
- **⚠️ Do NOT delete completion evidence on dismiss, disconnect or permission revocation** — hide it.
- **⚠️ Do NOT use "Companion"** for the human relationship — it collides with `AskCompanionService`.
- **⚠️ Do NOT remove the Teacher profile distinction** — teacher tooling sits above this primitive.
