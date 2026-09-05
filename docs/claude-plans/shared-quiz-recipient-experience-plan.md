# Shared Quiz — Recipient Experience & Continue-Learning Loop

**Status:** PLAN ONLY — nothing implemented. Written 2026-09-05, **revised 2026-09-05** to
incorporate owner product tightening.
**Companion:** `docs/claude-plans/supporter-progress-visibility-audit.md` (§8 here supersedes it
where they differ).

**⚠️ THREE OWNER DECISIONS ARE CONTRADICTED BY THE REPO. Read §1 before anything else.** None is a
disagreement with the product direction; each is a missing substrate the direction assumes exists.

---

## 1. Executive judgment

The recipient direction — *Receive → Answer → Result → Learn more → Continue studying* — is
**sound and mostly buildable on what exists.** Three contradictions must be resolved first.

### ⚠️ Contradiction A — a combined quiz has NO source-note identity (blocks §9/§13 for multi-source)

```java
/** A stored snapshot section; {@code title} is copied from the source note, never resolved later. */
public record CombinedQuizSection(String title, List<QuizItem> questions)
```

`CombinedQuizEntity` stores `sections` as JSONB of `(title, questions)`. **There is no note id
anywhere in a combined quiz** — only a copied title string, and the comment says so deliberately.

| Quiz kind | Source identity | Continue-learning possible? |
|---|---|---|
| **Single-note** shared quiz | `generated_quizzes.note_id` (NOT NULL, unique) | **Yes — no migration** |
| **Combined** shared quiz | copied title strings only | **No — needs a migration** |

**So §13's "multiple eligible public sources" cannot ship for combined quizzes without storing
source note ids.** **Recommendation: ship continue-learning for single-note quizzes in this release
and treat multi-source as follow-up**, rather than adding a migration to a UX release. The
one-source case in §13 is the common one and is free.

### ⚠️ Contradiction B — a shared quiz result is never recorded (blocks §18/§22's supporter loop)

`getSharedQuizResults` (`QuizShareLinkService:182`) grades **in memory and returns**: zero `.save(`,
zero analytics, zero activity tracking in the whole method. And a shared quiz creates **no session**
— `GeneratedQuizService` and `QuizShareLinkService` reference `quick_review_sessions` zero times
(`v0.110.0`).

**So §18 is not a permission question. There is no record to permit.** Even a supporter holding a
full `PROGRESS` grant would not see it, because a shared-quiz completion never enters the learner's
progress data at all. Building it requires **new persistence** — a completion record — which is a
migration and a genuine privacy decision (an anonymous recipient's score attributed to nobody, or a
signed-in recipient's score attributed to them).

**Recommendation: out of scope for this release; record as follow-up.** §18 asked what is already
allowed — the honest answer is *nothing, because nothing is stored.*
**⚠️ The privacy contract for it is now DECIDED (2026-09-05): signed-in recipients only, within an
accepted relationship, anonymous completions never recorded.** That settles what a future build may
do; it does not make it in scope here.

### ⚠️ Contradiction C — readiness trend cannot be reconstructed (bounds §21)

There is **no readiness history or snapshot table** (searched migrations: the only matches are an
unrelated subscription-history guard and a collection-structure snapshot). `ConceptHealth` stores
current state per concept only — `incorrect_streak`, `last_correct_at`, `last_incorrect_at` — with
no historical series.

**So "↑ 4% over the last 30 days" cannot be produced honestly**, exactly as §21 anticipated.

**But quiz-performance history DOES exist and needs no migration:**
`DashboardService.getMasterySnapshot` already reads `recentCompletedSessions` from
`quickReviewSessionRepository` — completed sessions carrying timestamps — and collapses them into
average/best. **§22's recent-results list is a projection change, not new data.**

---

## 2. Current defects confirmed from the repo

| # | Defect | Anchor |
|---|---|---|
| 1 | **Matching questions are unanswerable.** `PublicQuizItem` drops `questionGroup` (present on `QuizItem:27`), and the recipient page branches on one format only (`isMultiSelect`, `:82`) | `QuizShareLinkService:157` |
| 2 | **Answers are append-only** — `[...answers, x]` (`:99`), so a wrong answer cannot be corrected | `quiz/[token]/page.tsx:99,:107` |
| 3 | **CTA overflows on mobile** — `buttonVariants` base carries `whitespace-nowrap`, `size=default` gives fixed `h-10`; a 54-char label in `w-full` can neither wrap nor grow | `button.tsx:18,:31`; `page.tsx:193-201` |
| 4 | **Bare concept string under the stem**, unlabelled, reads as part of the question | `page.tsx:274-275` |
| 5 | **Result screen ends at a score** — one signup CTA, no continuation | `page.tsx:181-201` |
| 6 | Authenticated recipients see the app's bottom tab bar while answering | `app-shell.tsx:63,:510` |

---

## 3. Tightened recipient flow

**The lightweight primitive is preserved (§1): no Learning Connection, no account, no Note share, no
assignment is required to take a shared quiz.** Every addition below is *after* submission or is
purely presentational.

**Answering:** index-addressed answers replacing the append-only arrays, **Previous / Next** (not the
Board Exam navigator), Next gated on the current question being answered, `Submit Answers` on the
last. Concept metadata removed from the active-question surface (§4 of the response — **remove, do
not relabel**; it provides no orientation the stem does not already give).

**Matching (§3 of the response) — both options priced:**

| Option | Work | Verdict |
|---|---|---|
| **A — exclude Matching from shareable quizzes** | One filter at share/generation time | **Recommended for this release.** Immediate, closes the integrity hole. **⚠️ Does nothing for already-shared quizzes** — same residual shape `v0.110.1` recorded; state it, do not imply a full fix |
| **B — support Matching properly** | Carry `questionGroup` in `PublicQuizItem`, reuse `QuizMatchingGroup`, **and change grading** — `getSharedQuizResults` is positional and matching groups are multi-item | **Materially larger**, and it touches the grading path. Follow-up |

**Recommendation: A now, B recorded as follow-up.** B is not modest — the grading contract is
positional, so matching support is not a rendering change.

---

## 4. Perfect / non-perfect result contract

**Two states only. No motivational tiers** (§6).

| State | Framing | Continuation |
|---|---|---|
| **Perfect** | *"Great job — 10/10! You got every question right."* | **Keep this for later** — same source material, retention framing |
| **Not perfect** | *"Nice work — 8/10. Review the source material if you'd like to strengthen the topics covered here."* | **Continue learning** — same source material, remediation framing |

**Score changes tone only, never access.** Both states show identical continuation options when
eligible public material exists.

**⚠️ §14 respected: never say "review the Notes you struggled with."** Per-question source
attribution does not survive to this surface — `QuizItem.sourceStudyPackId` exists but
`PublicQuizItem` drops it, and a combined quiz's only grouping is a section title with no note id.
Use **"Review the source material"** / **"Continue learning"**.

---

## 5. Public-source continuation architecture

**Hard rule (§9, §10, §17): sharing a quiz never implicitly shares its source Note.**

**Payload change — single-note quizzes only, no migration:**
`PublicSharedQuizResponse` gains `sourceNotes: List<PublicSourceNote(id, title)>`, populated **only**
from source notes whose `visibility == PUBLIC`, resolved server-side.

**⚠️ Private sources are omitted entirely — not counted, not hinted, not placeheld.** No *"1 source
is private"*, no *"2 of 3 available"*, no disabled card. If no source is public the field is an
**empty list**, and the client must render nothing rather than an empty-state explaining absence
(§15). **This rule holds even between accepted Learning Connections** (§17).

**⚠️ Mixed public/private is therefore free for single-note quizzes** (there is one source) **and
blocked for combined quizzes** (no ids to filter) — see Contradiction A.

---

## 6. Anonymous vs authenticated behaviour

| Recipient | Primary source action | Why |
|---|---|---|
| **Anonymous** | **View Note** → the public note page | §11. **And there is a repo reason beyond preference:** the existing copy component routes anonymous users to `/signup?redirect=…` (`public-library-copy-action.tsx:161`), but `resolvePostLoginDestination` returns the **gated home** (verify-email → onboarding) *before* reading the redirect param — so a new signup **loses it**. A copy-first anonymous path dead-ends |
| **Authenticated** | **Add to Library** | Reuse is safe and small — see below |

**§12 / Q3 answered: yes, the existing copy action is reusable directly.**
`POST /notes/{id}/copy` (`NoteController:329`) and `copyNote(noteId, options)` (`api.ts:5764`)
already exist, and `public-library-copy-action.tsx` already uses `copyNote` for authenticated users
under the exact label **"Add to Library"** (`:14`). **Reuse that component; do not write a second
copy implementation.**

**Signup CTA stays** as a separate pathway (§11) — but it must no longer be the only thing on the
screen, and its label must shrink (defect 3).

---

## 7. Learning Connection-aware behaviour

**Sender context (§16) — buildable, small, and it needs a lookup.** `PublicSharedQuizResponse`
carries no owner identity (`quizId, noteTitle, questions`), so showing *"Quiz from Maria"* means
resolving the quiz owner **and** confirming an accepted relationship with the viewer.

**⚠️ It must be gated on an accepted relationship, not on quiz ownership alone** — otherwise the
endpoint becomes an identity oracle on a `permitAll` route, disclosing the sender's display name to
anyone holding a link. **For an anonymous or unrelated recipient, disclose nothing.**

**Terminology (§16):** use **Learning Connection** / the person's name. **Do not use "Companion"** —
`AskCompanionService` is an existing distinct product concept and the collision would be permanent.

**§17 restated as code: connection grants nothing here.** The source-note filter is
`visibility == PUBLIC`, full stop — relationship state is never an input to it.

---

## 8. Supporter progress audit (§19–§26)

Full findings: `docs/claude-plans/supporter-progress-visibility-audit.md`. What that audit adds here:

**Q8 — activity data that exists:** `LinkedLearnerActivityResponse(displayName, engagementMode,
currentStreak, longestStreak, studyDaysThisWeek)` behind a separate `ACTIVITY` grant, rendered on
`/linked-learners`, **not** on the progress page. §23's *Learning momentum* card is a **relocation**,
not new data — and it **must disappear entirely when ACTIVITY is off**, which the existing grant
check already enforces.

**Q9 — plan-progress fields:** the progress payload exposes counts only (`collectionCount`,
`totalItems`, `readyItems`, `practicedItems`). `NoteCollectionSummaryResponse` **does** carry `title`
server-side, but the progress service aggregates it away.
**⚠️ Recommendation: keep plan names hidden.** §20 forbids "private curriculum details", and a plan
name is curriculum detail. §24's richer phrasing (*"46 of 210 practiced · 22% complete across 3
plans"*) is a **pure formatting change over data already sent** — ship that, not names.

**Q7 — trends:** readiness trend **impossible** (Contradiction C); quiz-performance history
**available now** from completed sessions with timestamps. §22's recent-results list is a projection
change with no migration. **⚠️ Do not add event sourcing or a snapshot table for readiness** (§21).

**Q10 — "Quizzes you shared":** blocked by Contradiction B. **Not buildable without new persistence.**

**Q11 — contextual "Create a quiz for {Name}" (§26):** the flow is a **note-actions menu item** on
the private note detail page (`private-note-detail-page-client.tsx:2363`), so it starts from a note
the supporter owns. A contextual CTA is therefore a **deep link into a note picker**, not a button
that starts a quiz. Reusable, but it is an entry-point addition — price it as such, and it does not
make quiz sharing connection-only (§26 respected).

**Q12 — migrations:** **none required** for Slices 1–4. Slice 5's supporter enrichments also need
none. Contradictions A and B each need one, which is why both are follow-ups.

---

## 9. Matrices

### §31 — Recipient product-state matrix

| Recipient | Connection? | Score | Public source? | Result framing | Source action |
|---|---|---|---|---|---|
| Anonymous | No | Perfect | Yes | Great job — 10/10 · **Keep this for later** | **View Note** |
| Anonymous | No | Not perfect | Yes | Nice work — N/10 · **Continue learning** | **View Note** |
| Anonymous | No | Any | No | Score + signup CTA only | none |
| Signed in | No | Perfect | Yes | Great job · Keep this for later | **Add to Library** + View Note |
| Signed in | No | Not perfect | Yes | Nice work · Continue learning | **Add to Library** + View Note |
| Signed in | **Yes** | Perfect | Yes | *Quiz from {Name}* + Great job | **Add to Library** + View Note |
| Signed in | **Yes** | Not perfect | Yes | *Quiz from {Name}* + Nice work | **Add to Library** + View Note |
| Signed in | **Yes** | Any | No | *Quiz from {Name}* + score | none |

**Mixed public/private multi-source:** public sources listed, private omitted **without trace**.
**⚠️ Only reachable for single-note quizzes today** (Contradiction A), so in this release the
"multiple sources" rows are unbuildable and the matrix collapses to one source or none.

**Signup CTA** is present in every signed-out row as a separate pathway; **never** the only route to
public material.

### §32 — Supporter permission matrix

| Activity | Progress | Supporter sees | A quiz they personally shared |
|---|---|---|---|
| OFF | OFF | Name + status only. Dashboard card says *"{Name} is not sharing progress with you."* | **Nothing** — never recorded |
| **ON** | OFF | Streaks, longest streak, study days this week (on `/linked-learners`) | **Nothing** |
| OFF | **ON** | Readiness · quiz performance · plan progress (`/progress`) | **Nothing** |
| **ON** | **ON** | Both of the above, on two pages today | **Nothing** |

**⚠️ The last column is uniform because of Contradiction B: a shared-quiz completion is never
persisted, so no permission state can reveal it.** This is not a gap in the permission model — it is
an absent record. Fixing it is new persistence plus a new privacy decision, not a grant change.

**DECIDED 2026-09-05 (owner):** if shared-quiz completions are ever recorded, **signed-in
recipients only**, surfaced to the sender **only where an accepted relationship exists**.
**⚠️ Anonymous completions are never recorded** — not anonymised, not counted, not aggregated —
because a `permitAll` link would otherwise become a tracking surface.
**⚠️ The decision settles the privacy contract; it does not schedule the build.** The completion
record is still new persistence and stays a follow-up (Contradiction B).

---

## 10. Mobile UX

**Focus mode — narrowed per §5 of the response.** Apply `useExamFocusMode(true)` **to the shared quiz
only** while answering. **Do NOT expand to Quick Review, Challenge Quiz, Adaptive Practice or
Interview Practice** — recorded as a future audit, not this release.

**⚠️ The exit affordance is mandatory, not polish.** The shared quiz page renders **no `BackLink`**,
so hiding the tab bar leaves a signed-in recipient with only the browser back button. Ship an
in-page Exit/Back with the focus change or not at all.

**⚠️ Narrower than it looks:** the shell only renders when authenticated (`app-shell.tsx:63`), so an
**anonymous recipient already sees no bottom nav.** This affects signed-in recipients only.

**Result screen:** CTA label shortened **and** allowed to wrap (`whitespace-normal h-auto py-2.5`);
supporting sentence moves to a `<p>` beneath. Source cards stack vertically; one card in the
single-source case. **Sweep for sibling long-label full-width buttons** — the same latent break.

---

## 11. Revised implementation slices

| Slice | Content | Migration | Route |
|---|---|---|---|
| **1 — Recipient assessment integrity** | Matching exclusion (option A); index-addressed answers; Previous/Next; remove the concept line | No | **Codex** (backend filter + frontend state) |
| **2 — Shared-recipient mobile focus** | `useExamFocusMode` on this page only **+ explicit Exit** | No | Claude Code inline |
| **3 — Result legibility** | CTA wrap/shorten; perfect vs non-perfect framing | No | Claude Code inline |
| **4 — Continue learning** | `sourceNotes` (PUBLIC, **single-note quizzes only**); anonymous View Note; authenticated Add to Library via the existing component | No | **Codex** (DTO + service + UI) |
| **5 — Learning Connection integration** | Sender context (gated on accepted relationship); §22 recent-results projection; §23 momentum relocation; §24 plan-progress phrasing; §26 contextual CTA | No | **Codex** |

**⚠️ Recommendation: Slice 5 becomes its own follow-up release.** It touches the product's only
cross-user read and a `permitAll` payload's identity disclosure — a different verification tier from
four contained recipient fixes. §29 explicitly invites this call: *"Do not distort the smaller
recipient fixes merely to ship everything together."* **Slices 1–4 ship first.**

**Slice 1 is the one to ship first if only one ships** — it is the only place a recipient is
currently scored on a question they cannot answer.

**Follow-ups recorded, not built:** Matching support (option B); source note ids for combined
quizzes; shared-quiz completion persistence; readiness trend architecture; repo-wide quiz focus-mode
consistency.

---

## 12. Genuine remaining owner decisions

1. **Matching — exclude now or build support now?** Recommendation: **exclude** (§3). B changes the
   grading contract, so it is not modest.
2. **Combined-quiz continue-learning — accept single-note-only for v1, or add the migration?**
   Recommendation: **single-note only**; a UX release should not carry a schema change.
3. ~~**Record shared-quiz completions at all?**~~ **SETTLED 2026-09-05** — not in this release; if
   ever built, **signed-in recipients only, within an accepted relationship, anonymous never
   recorded** (§9). See `supporter-progress-visibility-audit.md` §11.
4. **Slice 5 in this release or its own?** Recommendation: **its own.**

**Settled by the audit, not owner questions:** anonymous gets View Note (the signup redirect is lost,
§6); copy reuse is safe (§6); plan names stay hidden (§8); readiness trend is impossible (§1C).

---

## 13. Anti-drift checklist

- **⚠️ Do NOT implement Quiz Assignments** — no assignment entity, inbox, due dates, state,
  notifications, rosters or multi-recipient assignment (§28).
- **⚠️ Do NOT require a Learning Connection, account, Note share or assignment to take a shared
  quiz** (§1).
- **⚠️ Do NOT expose a private source Note — its id, title, existence, count, or a placeholder** —
  even between accepted Learning Connections (§10, §17).
- **⚠️ Do NOT expose `correctIndex`, `correctIndices` or `explanation` in `PublicQuizItem`** — its
  javadoc records that the record is the only thing enforcing this.
- **⚠️ Do NOT disclose sender identity to an anonymous or unrelated recipient** — gate on an accepted
  relationship, or the `permitAll` route becomes an identity oracle (§7).
- **⚠️ Do NOT use "Companion" for the human relationship** — it collides with `AskCompanionService`.
- **⚠️ Do NOT invent score tiers** beyond perfect / not perfect (§6).
- **⚠️ Do NOT claim per-source weakness** — item provenance does not reach this surface (§14).
- **⚠️ Do NOT manufacture readiness trend data** (§21), and do not add event sourcing for it.
- **⚠️ Do NOT infer activity from progress metrics** or show it under another label when ACTIVITY is
  off (§23).
- **⚠️ Do NOT expose plan names** on the supporter page (§20, §24).
- **⚠️ Do NOT expand focus mode to other quiz modes in this release** (§5).
- **⚠️ Do NOT persist anonymous recipient answers** — `AGENTS.md` forbids anonymous session state on
  public surfaces; refresh-loses-progress stays a Known limitation.
- **⚠️ Do NOT write a second Note-copy implementation** — reuse `copyNote` / the existing action.
- **⚠️ Do NOT change `QUIZ_SHARE_LINK_CREATED` or `QUIZ_SHARE_LINK_OPENED`** — dated checkpoints read
  them.
- **⚠️ No quota, entitlement or meter change; no new quiz mode or sub-mode; onboarding untouched.**
- **⚠️ No migration in Slices 1–5.** If one appears necessary, the scope has drifted into a recorded
  follow-up.

---

## 14. Verification

**Slices 1–4: a single `advisor()` call** — presentation plus one additive payload field, no money
semantics, no migration. **⚠️ Escalate Slice 4 to one scoped cold agent**, because it adds a field to
an anonymous `permitAll` payload — a cross-boundary disclosure. **Slice 5: one scoped cold agent**
(sender identity on a `permitAll` route + the product's only cross-user read).

**Pre-declared discriminating guards:**

1. **Private source invisibility** — a shared quiz whose source note is **PRIVATE** must return
   `sourceNotes` **empty**, with no id, title or count anywhere in the response. *A public-note
   fixture passes under a version that leaks private ids.*
2. **Answer correction** — answer Q1, advance, go back, change it, submit: the **changed** answer
   must be graded. *A forward-only fixture passes under the append-only defect.*
3. **Matching integrity** — a generated quiz containing a `questionGroup` must not be shareable (or,
   under option B, must render as a group). Assert against a quiz that actually has one set.
4. **Sender identity gating** — an **anonymous** recipient and a **signed-in but unrelated**
   recipient must both receive **no** sender identity. *An accepted-connection fixture passes under a
   version that discloses to everyone.*
5. **Activity separation** — with `ACTIVITY` off and `PROGRESS` on, the supporter response must carry
   **no** streak, study-day or momentum-derived value. *Asserting the card is hidden in the UI is not
   enough — assert the payload.*
6. **CTA layout** — pin the shortened label, or assert wrapping at a narrow viewport. *A desktop
   fixture passes while mobile overflows.*
