# Production results — program vocabulary + pool/bank impact

`11-program-vocabulary-seed.sql` and `12-pool-bank-relevel-impact.sql` run against production
**2026-08-04**, after `v0.69.0` deployed (production at V105). These are the reads PR 5 and PR 6a
were deferred on. Recorded here for the same reason `05-vocabulary-results.md` exists: the numbers
inform decisions that outlive the session.

---

## Part 1 — program vocabulary (Query 11)

**32 distinct values across notes and users, zero character-level collisions.** Query B returned no
rows, so the finding that made PR 5 a cheap curated seed rather than a reconciliation project
**still holds** four weeks after the original audit.

### The five user-side-only values, finally named

`05` counted them but never listed them. They are:

| Value | Notes | Users |
|---|---|---|
| `Computer Science` | 0 | 3 |
| `Bsed` | 0 | 1 |
| `Radiologic Technology` | 0 | 1 |
| `Self Study / Personal Learning` | 0 | 1 |
| `Special Needs Education – Generalist` | 0 | 1 |

### FINDING 1 — there is a fourth en-dash value, and no audit had seen it

`Special Needs Education – Generalist` contains **U+2013** (36 chars, `has_non_ascii = true`). Every
prior document treats the three `Senior High – …` values as the complete en-dash set — `V105`'s
header says exactly that, and `10-high-school-classification.sql`'s Query B2 only probed for
`%senior high%`, `%junior high%`, `%high school%`, `%grade school%`, so this value was never in
range of any check.

**Consequence for PR 5:** the seed must copy all four verbatim. A retyped hyphen matches zero rows
and fails silently — the same trap V105 used a prefix match to dodge. Related: `Object – Oriented
Programming` appears as a *subject* with an en dash, so the character is loose in subject data too.

### FINDING 2 — the CS / IT / SE decision was made without this data

`08` ruled that Computer Science, Information Technology and Software Engineering all survive as
distinct programs. The distribution does not support three:

| Value | Notes | Users | Distinct subjects |
|---|---|---|---|
| Information Technology | **74** | 2 | 11 (Advanced Programming, Computer Networking, Database Systems, Mobile Programming 1, Web Development, …) |
| Software Engineering | 4 | 3 | **1** — `Computer Science` |
| Computer Science | **0** | 3 | — |

Information Technology is unambiguously a real program with real curriculum breadth. Software
Engineering has four notes whose only subject is *Computer Science*. Computer Science has **no
notes at all** — three user profiles and nothing authored.

> **RESOLVED — ruled by the owner 2026-08-04. PR 5 is scopeable.** Seed `Information Technology` as
> a program; `Computer Science` and `Software Engineering` stay user-side values with a NULL catalog
> FK pending a curator ruling, rather than minting two programs on 4 notes and 0 notes respectively.
> Nothing is deleted — both keep their existing strings, like the catalog's other unmappable values.
> This reverses part of `08` **on evidence `08` did not have**; it is ratified, not assumed, and PR 5
> must not re-litigate it.

### The settled judgment calls, confirmed against content

All match `08`; recorded so they are not re-litigated:

| Value | Notes | Subjects found | Ruling |
|---|---|---|---|
| `Civil Service` | 7 | Civil Service, English, Logical Reasoning, Mathematics | **goal**, not a program — those are exam sections |
| `Professional / Board Exam Review` | 5 | Social Science (0 public) | **goal** |
| `Engineering` | 2 | Aeronautical Engineering, Calculus | **family** |
| `Biology` | 1 | Microbiology | **subject** |

`Civil Service` also carries a note whose `subject` equals its program — exactly the collision the
v0.69.0 subject-equals-context nudge was built to surface.

### Query D — legacy level labels, post-V105

Matches `13-post-deploy-verification.sql` exactly: Grade School 3, Junior High 24, High School
3+1+6 (classified/classified/deliberately unclassified), the three strands 4/4/3 at `SENIOR_HIGH`
with NULL Domain Context. `Senior High – ABM` is 4 notes but only 3 public — the one non-public
row in the legacy set.

---

## Part 2 — pool / bank re-level impact (Query 12)

Run in full (not the V101 variant), since production is at V105.

| | Total | Unstamped | **At risk** | Source note has level |
|---|---|---|---|---|
| `exam_question_pool` (READY) | 1,244 | 256 | **57 (4.6%)** | 52 |
| `challenge_quiz_question_bank` | 6,235 | 15 | **5 (0.08%)** | — (55 incorrect rows) |

### DECISION — PR 6a ships without a pre-stamping migration

57 pools out of 1,244 will mismatch on next access and lazily regenerate. That is a non-event
spread across normal traffic, not a wave. **Ship 6a as one PR and record the one-time cost in
`RELEASES.md`**, per `12`'s own decision rule.

**The number is growing, which is the argument for doing it now.** The V101-variant floor was **9**;
the full read is **57**. The 48-row difference is the 42 notes V104/V105 gave authored levels to.
Every note authored from here widens the gap, so this is cheapest today.

### FINDING 3 — the read/write divergence is latent, not live

Query C: 15 packs with incorrect rows, 13 distinct owners, **0 where the note level differs from
the reader level.**

That zero is not reassurance. It holds only because so few notes carry an authored level that
`COALESCE(note, reader, COLLEGE)` still collapses to the reader's level. **`PostSessionNextStepService`
must still be threaded in the same PR as the five `ChallengeQuizService` write sites** — the moment
authoring proceeds, the Redo Missed Questions availability check and the claim query begin
disagreeing, and that failure is silent (a CTA that appears with nothing claimable, or hides when
questions exist).

### Noted, out of scope

`unstamped = 256` — roughly 20% of READY pools have a NULL `learner_level`.

> **CORRECTED 2026-08-05, after PR 6a (#986) shipped. The paragraph below originally claimed all 256
> "already fail `sameLearnerLevel` and regenerate on access today, before any PR 6 change." That is
> wrong for 40 of them, and the error mattered.**
>
> `sameLearnerLevel` used to receive the **raw nullable reader level**, and its `generatedLearnerLevel
> IS NULL → return current == null` branch meant a NULL pool level *matched* a NULL user level and
> sampled without refreshing. So the pools owned by users whose profile level is null were **not**
> regenerating today — they were matching. `effectiveCurriculumLevel` never returns null, so that
> pairing now mismatches and takes the lazy refresh path. Measured 2026-08-04 during #986's
> pre-commit audit: **40 READY pools and 15 bank rows** in that null-by-null cohort.
>
> The 40 pools self-heal — one refresh stamps them `COLLEGE` and they match thereafter. The 15 bank
> rows do not, and are permanently unclaimable for their owners. Both are recorded in `RELEASES.md`
> v0.70.0 under Known Limitations.
>
> **Do not cost the 256-pool question off the original sentence.** `12-pool-bank-relevel-impact.sql`
> compared note level against reader level and never covered the null-by-null cell at all, so that
> cohort sits outside the 57 / 5 headline figures as well.

The remaining unstamped pools are a pre-existing regeneration cost that nobody has sized, unaffected
by the re-keying. Worth its own look; not part of PR 6a.

---

## Part 3 — the seed list itself (Query `16`, 2026-08-05)

`15` recorded Query A's *findings* but never its *output*, so the exact program strings did not exist
anywhere in the repo and PR 5 could not be written from this document. `16-program-vocabulary-seed-followup.sql`
closed that, and added `note_collections.course_program` — a third source (`V76`) that `11` never read.

**Results:**

- **32 values, unchanged.** Collections contributed **no** new values: every collection-side program
  (Education, Architecture, Accountancy, Nursing, Information Technology) was already in the notes+users set.
- **Zero collisions across all three sources** (Query C, no rows). The finding that makes PR 5 a curated
  seed rather than a reconciliation project holds four weeks on.
- **Exactly four U+2013 values** (Query B): `Senior High – ABM` / `– STEM` / `– HUMSS`, and
  `Special Needs Education – Generalist`. FINDING 1's count was right.

### FINDING 4 — `Medical – Surgical Nursing` is a phantom

It matches **0 notes, 0 users, 0 collections**. It exists only in `ExamGoalConfig:15` and
`frontend/lib/exam-hub-config.ts:26` as PNLE's second course/program, under a comment in both files
asserting that "CourseProgram values must match production DB values exactly." It never has.

`RELEASES.md:253` cites PNLE's two programs as the worked example of the Exam Hub's multi-program
dedupe — a case that cannot occur. The value is also a PNLE board *subject area* rather than a degree
program, failing the catalog's classification rule on the same grounds as `Biology`.

**Owner ruled 2026-08-05: drop it.** PNLE maps to `Nursing` only. Behavior-preserving in effect, since
the dropped value matched nothing.

### The seed decisions, ratified 2026-08-05

The full 32-row table, the 21/11 seed/exclude split, and the reasons are in
`docs/codex-prompts/v0.70.0-course-programs-catalog.md`. Recorded here so they survive that file being
untracked:

- **Seed the three `Senior High – …` strands.** Closes the call `11`'s Query D left open. They are
  curriculum tracks, not bare levels: `V105` deliberately left their `domain_context` NULL so the strand
  keeps reaching the prompt as the effective authoring domain, and excluding them would leave the value
  that actually reaches generation outside the catalog. `Grade School` / `Junior High` / `High School`
  stay excluded as bare levels.
- **Seed `Radiologic Technology` and `Special Needs Education – Generalist`** despite zero notes. The
  `Computer Science` / `Software Engineering` exclusion turned on the contested computing space, not on
  note count, so it does not generalise to two unambiguous degree programs.
- **`Bsed` → `Education` is the only non-exact FK mapping**, as an enumerated single-row alias. The
  stored string stays `Bsed`.
