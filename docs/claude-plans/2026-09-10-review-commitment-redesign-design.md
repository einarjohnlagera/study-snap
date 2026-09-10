# Design — review-commitment prompt: un-strand the ask, shrink it to one tap (2026-09-10)

**For:** the Codex prompt for `v0.139.0` items 1–2. **Written by:** Claude Code, 2026-09-10.
**Evidence:** `docs/claude-findings/2026-09-10-september-checkpoint-reads.md`, plus the read-only
production queries reproduced below. **Status: DESIGN ONLY. No code changed.**

**⚠️ Read §1 before §3.** The design changed direction once already, because the owner's decision and
my first proposal were both made against a reading that the data then refuted.

---

## 1. What the data actually says — and what it refutes

**The `v0.72.0` proximal checkpoint fired: 9 learners shown the prompt, 0 committed, 1 declined.** The
zero is real (`COMMITTED` and `DECLINED` share one call site and `DECLINED` fired; independently
`users.review_days` is empty for all 396 accounts).

**⚠️ BUT THE PROMPT IS NOT FAILING TO CONVERT. IT IS FAILING TO BE SEEN, AND THAT REFRAMES EVERYTHING.**
All figures **VERIFIED** read-only 2026-09-10:

| Measure | Value |
|---|---|
| Users total | 396 |
| Ask still "outstanding" (`review_commitment_prompted_at IS NULL`) | **395** |
| Ask ever resolved | **1** |
| Learners with `review_days` set | **0** |
| Eligible (≥1 completed session) **and** outstanding | **136** |
| — whose first session predates the 2026-08-11 deploy (**moment permanently passed**) | **128** |
| — reachable after deploy | **8** |
| Distinct learners who saw the prompt | **9** |

**⚠️ THE PROMPT REACHED ESSENTIALLY EVERYONE IT COULD.** Nine impressions against eight structurally
reachable learners. **`n = 9` is not a sampling accident — it is the ceiling of the current design**,
and no copy change moves it.

### ⚠️ The mechanism: two gates that disagree, and a state nothing can clear

- The frontend renders on **`isFirstCompletedSessionEver === true`** — true exactly **once per learner,
  ever**.
- `reviewCommitmentOutstanding` is **`user.getReviewCommitmentPromptedAt() == null`**
  (`AuthService:630`), and `reviewCommitmentPromptedAt` is stamped **only inside
  `updateReviewCommitment`** (`AuthService:579`) — i.e. only when the learner presses *Set my review
  plan* or *Not now*.

**So a learner who navigates away stays `outstanding` forever while the render gate can never fire
again.** The ask is permanently owed and permanently unaskable. That is the 8-of-9 who vanished, and
it is why 395 of 396 accounts read "never asked".

### ⚠️ AND THE FIX I FIRST PROPOSED WAS REFUTED BY ITS OWN NUMBERS — DO NOT RE-PROPOSE IT

My first instinct was to re-trigger on *return after a gap*, since 127 of 129 learners with any study
history are >3 days idle. **That is wrong, and the query that kills it is:**

| Cohort | Learners |
|---|---|
| Stranded (eligible, never asked) | 136 |
| — with a completed session in the **last 14 days** (in-app reachable) | **7** |
| — with a completed session in the last 30 days | **13** |

**The 128 stranded are stranded BECAUSE they stopped opening the app.** Any in-app trigger — however
well designed — has a ceiling of roughly **7–13 learners a month**. **An in-app redesign cannot fix
reach for the existing cohort, and this release must not claim it does.**

The surface that reaches them is **email**, and that is blocked by **R1** — the 100/day cap
(`application.yaml:510`) gates `INACTIVITY` only and is already breached at 156–159 observed. R1 is
indexed in the Backlog Index and blocks every future email producer.

**⚠️ SO THE HONEST FRAME, WHICH THE RELEASE NOTE MUST CARRY: this redesign COMPOUNDS FOR FUTURE
LEARNERS — every new learner gets more than one chance instead of exactly one — and DOES NOT RECOVER
THE 128.** The owner chose it with that stated. It is a bet on future intake, not a retention fix.

---

## 2. Two more instrumentation defects, both found by running the read

1. **Abandonment is invisible.** `review-commitment-prompt.tsx:96-107` fires an event **only after a
   successful save**. Closing or navigating away emits nothing, so the funnel cannot distinguish
   *ignored* from *considered and rejected* — the distinction that decides whether to fix the prompt
   or abandon the framing.
2. **`DUE_CONCEPTS_DIGEST_LANDED` carries a NULL `user_id` on all 11 rows.** ⚠️ **The analytics client
   is NOT at fault — this was controlled:** `REVIEW_COMMITMENT_PROMPT_SHOWN` is 9/9 *with* a user and
   `DECLINED` 1/1. Only the digest-arrival path loses it, and `AnalyticsController:27` stores
   `user == null ? null : user.userId()` without complaint. **The checkpoint's second metric —
   *digest → first answer among committers* — was therefore never computable.**

### ⚠️ A THIRD DEFECT, NAMED AS A HYPOTHESIS RATHER THAN A CONCLUSION

**`DUE_CONCEPTS_DIGEST_FIRST_ANSWER_SUBMITTED` has ZERO rows, ever** — against 11 landings in a month.

- The fire site is **called** from three places (`quick-review/page.tsx:869, 897, 940`), so this is not
  a declared-but-uncalled event.
- Both it and `LANDED` are gated on `isDueConceptsDigestVisit`, which reads **`?source=due-concepts-digest`
  from the QUERY STRING** (`:235-238`). `LANDED` fires on arrival, when the param is present.
- **HYPOTHESIS (NOT VERIFIED): if starting a session changes or replaces the URL, the param is lost,
  the gate goes false, and the first-answer event can never fire** — the `v0.117.0` shape, a callback
  on a branch its consumer never reaches.
- **The alternative is that engagement is genuinely zero** — 11 learners clicked a digest and none
  answered a question. That is a real product finding if true.

⚠️ **DO NOT ASSUME EITHER. This is a named claim for the cold agent to falsify** (§7). The
discriminating test is whether `isDueConceptsDigestVisit` is still true at the moment an answer is
submitted, exercised through the real navigation, not a component rendered with the param hard-coded.

---

## 3. The design

### 3.1 Trigger — un-strand the ask (this is the whole point of item 2)

**Replace `isFirstCompletedSessionEver` with a backend-computed, RE-SHOWABLE eligibility.**

Show the prompt after **any** completed session when all hold:

- the learner has no `review_days` set, **and**
- `review_commitment_prompted_at IS NULL` (never *answered*), **and**
- `review_commitment_prompt_count < 3`, **and**
- `review_commitment_last_prompted_at` is null or older than **14 days**.

Cap and cooldown together mean **at most three asks over ~6 weeks**, then silence forever. A learner
who answers either way is never asked again.

### 3.2 ⚠️ Schema — a NEW column, and `prompted_at` KEEPS ITS MEANING

One migration, two columns on `users`:

- `review_commitment_prompt_count INTEGER NOT NULL DEFAULT 0`
- `review_commitment_last_prompted_at TIMESTAMPTZ NULL`

**⚠️⚠️ `review_commitment_prompted_at` CONTINUES TO MEAN "ANSWERED", IS NOT REPURPOSED, AND IS NOT
BACKFILLED.** It currently holds NULL for 395 of 396 accounts and is stamped only on commit/decline.
**Do NOT "tidy" the three columns into one, and do NOT set `prompted_at` on an impression** — that
would silently resolve 395 outstanding rows and permanently close the ask for every one of them. This
is the single most destructive change available in this design.

### 3.3 Recording an impression needs a server call

The impression count must be authoritative, so it cannot live in an analytics event. Add
**`POST /me/review-commitment/prompted`**, which increments `review_commitment_prompt_count` and sets
`review_commitment_last_prompted_at`. It must be **idempotent per impression** — the client already
guards with a ref and `sessionStorage` elsewhere; reuse that pattern.

**⚠️ THIS IS A NEW ENDPOINT, SO THE TRANSPORT LESSON APPLIES.** Per `CLAUDE.md`: it owes **one test
that issues a REAL REQUEST** — `MockMvc` with `.contentType(MediaType.APPLICATION_JSON)` and a body,
the pattern already in `NoteControllerTest`. **A direct method call is not a substitute: it passes
under the defect by construction.** `v0.119.0` shipped a feature whose every POST omitted
`Content-Type` while 2,182 frontend tests passed.

### 3.4 ⚠️⚠️ THE ASK HAS NO BENEFIT TO OFFER — §3.4/§3.5 BELOW ARE SUPERSEDED, READ THIS FIRST

**Found 2026-09-10 by `advisor()` BEFORE the Codex prompt was written, which is exactly where this
check is supposed to happen.** The one-tap design below was about to ship a button that grants the
learner nothing.

`RetentionService.isEligibleReviewDay:403-408`:

```java
String[] reviewDays = user.getReviewDays();
if (reviewDays == null || reviewDays.length == 0) {
    return true;                       // eligible on EVERY dispatch day
}
return Arrays.stream(reviewDays).anyMatch(dispatchDay.name()::equals);
```

**A learner who never answers the prompt is eligible for the digest on every dispatch day.** The
7-day `dueConceptsDigestCooldownDays` caps them at roughly one digest a week regardless. `retention-emails.md:26`
states this in prose — *"A null or empty value means the existing schedule, never 'never send'"* — and
the code confirms it.

**⚠️ CONFIRMED EMPIRICALLY, NOT ONLY BY READING: `review_days` is set for ZERO of 396 accounts, and 11
digests have been sent. Every digest in the product's history went to a learner who never answered
this prompt.**

**Therefore:**

- Choosing `Mon/Wed/Fri` does **not** turn reminders on. It **narrows** eligibility from seven days to
  three. **It is a restriction, not a benefit.**
- A one-tap *"Remind me Mon, Wed & Fri"* button would be a **no-op at best** for the learner, and the
  current copy — *"Choose the days you want NoteLib to remind you when concepts are due"* — **implies
  reminders are contingent on answering, which is false.**
- ⚠️ **AND IT UNDERMINES THE `v0.72.0` H1/H5 PREMISE ITSELF.** That work was scoped as a *commitment
  device*: the hypothesis was that committing drives return. **But the commitment gates nothing** — the
  email sends either way. What the prompt actually collects is a **scheduling preference**, which is a
  settings concern, not a commitment device. **The 0/9 result is unsurprising for an ask that offers
  the learner no change in outcome.**

**⚠️ DO NOT WRITE THE CODEX PROMPT AGAINST §3.5's COPY OR THE ONE-TAP BUTTON UNTIL THE OWNER HAS
RE-DECIDED WHAT ITEM 2 IS.** The trigger work (§3.1–§3.3) and all of item 1 are unaffected and remain
correct — they are about reach and measurement, not about the benefit on offer.

---

### 3.4-R ⚠️ RESOLVED DESIGN — owner decision 2026-09-10: **make the commitment mean something**

**The learner must be strictly BETTER OFF for answering. Today they are strictly worse off** — answering
narrows eligibility from seven days to three and grants nothing.

**⚠️⚠️ NON-NEGOTIABLE SAFETY CONSTRAINT, VERIFIED: NOBODY LOSES THE DIGEST.** `DUE_CONCEPTS_DIGEST` has
**619 sends all time to 115 distinct recipients, 114 in the last 7 days** — and **every one of them has
`review_days = NULL`**. Flipping `isEligibleReviewDay`'s empty-case from `true` to `false` — the naive
reading of "make the commitment mean something" — **silently cuts off 100% of digest recipients.**
**DO NOT DO THIS. It is the single most destructive change available in this release.**

**The design instead makes committing an UPGRADE:**

| | Eligible days | Cooldown | Net |
|---|---|---|---|
| **Uncommitted (unchanged)** | every day | 7 days | ~1 nudge/week, on a day they did not choose |
| **Committed (new)** | only their chosen days | **1 day** | a nudge on **each chosen day** they actually have concepts due |

Implemented as a **conditional cooldown**: `dueConceptsDigestCooldownDays` stays 7 for learners with no
`review_days`, and drops to 1 for learners who have chosen days. Eligibility already restricts committers
to their own days (`isEligibleReviewDay:403-408` is correct and unchanged), so a Mon/Wed/Fri committer
receives **at most three** digests a week, and only when `dueConceptCount > 0`.

**So answering converts "one nudge a week, whenever" into "a nudge on the days I said I would study."**
That is the H1/H5 mechanism — an implementation intention plus a cue arriving at the pre-decided moment —
and it is the first version of this prompt where the commitment gates anything at all.

### 3.5-R Copy — tell the truth, which is currently not told

Current copy — *"Choose the days you want NoteLib to remind you when concepts are due"* — **implies
reminders are contingent on answering. They are not.** Every learner already gets them.

The honest ask is about **timing and frequency**, and it is now a real benefit:
- lead with what they already have (a weekly nudge),
- offer what choosing adds (it lands on the days they picked, and on each of them),
- **do not promise a count** the digest may not have (`v0.135.0`: no number in copy that later becomes false).

### 3.6-R ⚠️ R1 INTERACTION — FAVOURABLE, BUT STATE IT AND DO NOT CLAIM THE FIX

**VERIFIED 2026-09-10 — the digest IS R1's breach.** Daily totals against the 100/day cap
(`application.yaml:510`):

| Day | Digest | Inactivity | Total |
|---|---|---|---|
| 2026-08-31 | **97** | 60 | **158** ⚠️ over cap |
| 2026-09-08 | **97** | 45 | **143** ⚠️ over cap |
| all other days in the window | 0–9 | 45–60 | ~60 |

**The digest fires as a synchronized weekly burst of ~97, because a flat 7-day cooldown locks every
eligible learner onto the same dispatch day.** That is the 156–159 breach R1 records, and it had not
previously been attributed to this producer.

- **Chosen days DE-SYNCHRONIZE the burst**, so this design pushes R1 in the right direction.
- ⚠️ **But each committer can now receive up to 3/week instead of 1**, so it adds per-committer volume.
  Near-term impact is ~zero (committers today: **0**; in-app reach is 7–13/month per §1), **but the
  release must bound it and must NOT claim to fix R1.**
- ⚠️ **The real fix for the burst is to stagger the DEFAULT schedule for non-committers — that is NOT in
  this release's scope.** Record it against R1's Backlog row with the attribution above, because R1
  currently names no cause.

---

### 3.4 (SUPERSEDED — kept for the record) The ask — one tap

Current ask: an optional exam date, a **seven-way weekday multi-select**, and *Set my review plan*.
`DEFAULT_REVIEW_DAYS` is already `MONDAY, WEDNESDAY, FRIDAY` and is **already pre-selected** — so the
multi-select is friction in front of a default the learner is very likely to accept unchanged.

- **Primary, one tap:** *"Remind me Mon, Wed & Fri"* — saves the default immediately.
- **Secondary:** *"Pick my days"* — reveals the existing chip selector, unchanged.
- **Tertiary:** *"No thanks"* — the existing decline. Stamps `prompted_at`; never asked again.
- **Close / navigate away:** records a dismiss (§3.6), increments the count, sets
  `last_prompted_at`, and **does NOT stamp `prompted_at`**.

**⚠️ KEEP THE EXAM-DATE FIELD. Do not remove it.** It renders when
`me.examDate !== null || me.profileType === "BOARD_EXAM"`, and **79 of 185 BOARD_EXAM accounts (43%)
have a NULL `exam_date`** — for them this prompt is a live collection path. Removing it would silently
drop that. It stays optional and must not gate the one-tap action.

### 3.5 Copy — make the benefit concrete, not hypothetical

Current: *"When will you come back?"* / *"Choose the days you want NoteLib to remind you when concepts
are due."* That asks a learner to predict their own behaviour, at the end of a first session, for a
benefit they have never experienced and which nothing has yet produced.

Anchor it to what just happened and what exists — the concepts they just worked through. Keep it to
one sentence, and **do not promise a count the digest cannot deliver** (the `v0.135.0` lesson: no
number in the copy that later becomes false).

### 3.6 Instrumentation (item 1) — ships first, blocking

- **`REVIEW_COMMITMENT_DISMISSED`** — new `AnalyticsEventType` value **with a real fire site on the
  close/abandon path**, carrying which exit was taken. ⚠️ **No enum value without a producer** — that
  is the defect `v0.134.0`'s audit opens with.
- **Fix `DUE_CONCEPTS_DIGEST_LANDED`'s NULL `user_id`.** The client is not at fault (§2), so the fix is
  in the digest-arrival path. **⚠️ The guard must assert the PERSISTED ROW carries a non-null
  `user_id`, not that the client sent one** — all 11 existing rows prove the gap is at
  persistence/auth-resolution time.

---

## 4. ⚠️ Anti-drift — do not do these

- ❌ **Do NOT ship item 2 without item 1.** A redesign that cannot be measured reproduces exactly the
  position this release is in.
- ❌ **Do NOT backfill, repurpose or write `review_commitment_prompted_at` on an impression** (§3.2).
- ❌ **Do NOT remove the exam-date field** — 79 BOARD_EXAM accounts depend on it (§3.4).
- ❌ **Do NOT re-trigger on "return after a gap"** as the headline fix — refuted in §1, ceiling 7–13.
- ❌ **Do NOT claim this recovers the 128 stranded learners.** It does not, and the release says so.
- ❌ **Do NOT add an email producer to reach them** — R1 (the 100/day cap, breached at 156–159) blocks
  every future email producer and is out of this release's scope.
- ❌ **Do NOT raise the ask's size back up** by adding fields to the one-tap path.
- ❌ **No `frontend/app/onboarding` work** before the `2026-09-11` read. **No Learning Connections work**
  (`[CHECKPOINT — due 2026-09-19]`, denominator ONE).

## 5. Pre-declared guards — written so a fixture cannot pass under both defect and fix

- **Trigger:** assert the prompt shows for a learner whose **first completed session is NOT the current
  one** — ⚠️ **this is the assertion that cannot pass against `main`**, and every other trigger test
  can. A test that only renders the component with `visible=true` proves nothing about the gate.
- **Cap:** assert the **fourth** impression does not render; assert an answered learner never renders.
- **⚠️ `prompted_at` protection:** assert that after an impression, `review_commitment_prompted_at` is
  **still NULL** and `reviewCommitmentOutstanding` is **still true**. This is the guard against the
  destructive change in §3.2 and it must exist.
- **Dismiss event:** assert it fires on the **close/abandon path specifically** — ⚠️ a test asserting
  "some event fires" passes today, because save already fires one.
- **Digest `user_id`:** assert the **persisted** row has a non-null user.
- **New endpoint:** one **real-request `MockMvc`** test with `.contentType(...)` and a body (§3.3).

## 6. What this design does NOT do — state it in the release

- It does **not** reach the 128 stranded learners (§1). In-app ceiling is 7–13/month.
- It does **not** fix R1, and therefore does not open the email path.
- It does **not** establish that the commitment framing works. **`n = 9` was a ceiling, not a verdict** —
  the `v0.72.0` framing stays **unconfirmed**, and item 1 is what makes the next read decisive
  regardless of whether the redesign converts.

## 7. Verification tier — owner decision, taken 2026-09-10

**ONE SCOPED COLD AGENT, framed as FALSIFICATION**, not the single `advisor()` call the release
originally declared. Three surfaces (migration + trigger/eligibility + UI), and **a trigger change is
precisely the class where a test passes against a condition production never produces** — the
`v0.116.0`/`v0.117.0` shape this release's own anti-drift already names.

Claims to hand it, each to be disproved against real code:

1. An impression does **not** write `review_commitment_prompted_at`, and 395 outstanding rows stay
   outstanding.
2. The new trigger condition is **reachable in production** — not merely true in a fixture.
3. The cap and cooldown actually bound the ask; a learner cannot be asked a fourth time.
4. The dismiss event fires on the abandon path, not only on save.
5. The digest `user_id` fix is verified against a **persisted row**.
6. **§2's hypothesis:** whether `DUE_CONCEPTS_DIGEST_FIRST_ANSWER_SUBMITTED`'s zero rows are a dead
   gate (query param lost on navigation) or genuine zero engagement.

**Routing: CODEX** — frontend across the five surfaces that render the prompt, plus a backend
migration, endpoint and DTO change.
