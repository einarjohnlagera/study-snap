# Authored Depth — Legacy Backfill & Recurrence Prevention: Audit and Plan

**AUDIT — DO NOT IMPLEMENT.**

**Date: 2026-09-17.** Not part of `v0.153.0` (The Missing Telemetry). No file other than this one was
created or edited by this audit. No production write of any kind was issued — every statement below is
reproduced verbatim with its result.

**Scope of this document:** ratify or challenge the owner's locked MODIFIED OPTION 1 decision, audit
current repo + production truth against its premises, and turn it into an implementation/operations
plan. Sections A–N follow the owner decision document's §21 REQUIRED OUTPUT exactly.

---

## A. Executive verdict

**CONFIRMED, with one material correction to its denominator and one addition to its ordering.**

MODIFIED OPTION 1 — *"explicitly complete the bounded legacy backfill, prevent silent recurrence, and
measure learner use separately before deciding whether depth-based discovery should survive"* — is the
right decision and every one of its locks (§4 no inference, §5 no Unclassified chip, §6 no retirement,
§7 no limbo, §15 no SQL guessing, §19 no overbuild) survives contact with the code and the data. Nothing
in this audit argues against any of them.

**The correction: the backlog is not bounded at 80, and it is not legacy.**

| Population | Count | Source |
|---|---|---|
| The checkpoint's 80 (ADMIN-owned, PUBLIC, `target_profile_type='STUDENT'`, NULL depth) | **80** | §C Query 1 |
| **All** ADMIN-owned PUBLIC notes with NULL depth | **282** | §C Query 2 |
| …of which created **after** the `v0.83.0` deploy (2026-08-17) | **173** | §C Query 4 |
| All PUBLIC notes with NULL depth (any owner) | **286** | §C Query 2 |

In the 30 days in which **zero** of the 80 were classified, curators published **173 new**
curator-owned public notes with NULL Authored Depth — more than twice the original population. The
checkpoint measured a frozen historical subset and correctly reported it frozen; it could not see that
the same defect was producing new debt at roughly **6 notes/day** the whole time.

**The ordering consequence, which is the highest-value output of this audit: prevention must ship
before the cleanup, not after it.** As currently specified, §18's LEGACY MIGRATION SUCCESS criterion
("all 80 … assigned Authored Depth") can be met in full while the total NULL-depth curator public count
is *higher* on the day it is met than on the day it started. A completion definition that a working
system can invalidate while you satisfy it is not a completion definition. Prevention first gives the
cleanup a stable denominator; the cleanup then terminates.

Nothing else in the decision changes. In particular §8's question — *"can the debt regenerate?"* — is
not hypothetical and does not need to be modelled: **it already did, at 2.2× the original population,
and §E shows the four exact paths.**

---

## B. Checkpoint interpretation

### What `80 → 80` proves

1. **Passive retroactive classification is not a viable metadata migration strategy.** Over 30 days
   with no assignment, no queue, no reminder and no surfacing, exactly zero notes moved. Verified
   today, one day past the checkpoint's own due date — still 80 (§C Query 1).
2. **Historical metadata cleanup does not happen as a by-product of shipping the field that needs it.**
   The `v0.83.0` release note's assumption ("curators will classify the remainder",
   `ROADMAP.md:824`) was a hope in the grammatical mood of a plan.
3. **The measurement instrument was sound.** `ROADMAP.md:824` pre-declared the kill criterion (≥70)
   before the data, chose a direct `SELECT` precisely *because* no instrumentation exists on the
   surface, and named the two permitted responses in advance. That is the checkpoint discipline working
   as designed, and it is why this decision is possible at all.

### What `80 → 80` does NOT prove

1. **Not curator unwillingness.** Nobody was assigned the work, told it existed, or given a list.
   There is no admin surface anywhere in the product that shows a curator which of their notes are
   missing metadata (§ audit finding in F/H: `AdminDashboardSummaryResponse.java:10-21` carries volume
   counts only; the one note-level admin surface,
   `AdminNoteApplicableProgramsController.java:28-35`, lists only the requester's own notes, has no
   missing-metadata filter, and **does not even carry `learnerLevel` in its row DTO**,
   `AdminNoteApplicableProgramsItemResponse.java:6-12`).
2. **Not absent learner demand for depth-based discovery.** There is not a single analytics event on
   the Public Library filter surface (§J). Zero curator classifications and zero learner demand are
   different measurements; only one of them was taken.
3. **Not that Authored Depth failed as a product concept.** It is not discovery-only metadata — it is a
   generation and assessment input with four live consumption sites (§D). A depth value does work
   whether or not anyone ever filters on it.
4. **Not that the work is hard.** Setting depth on one note is one dropdown inside an editor the
   curator already uses (`private-note-detail-page-client.tsx:1909-1922`), and it does not touch the
   existing Study Pack (§D, §11-answer). The work was never attempted, not attempted and abandoned.

### The reusable product lesson (owner's §2, for the eventual record)

> If new metadata matters to an existing corpus, migrating the existing corpus is part of shipping the
> feature. Do not ship the field and assume humans will eventually backfill historical data.

**This audit adds a second clause the checkpoint itself could not have produced:**

> …and do not assume the corpus is static while you wait. A checkpoint scoped to a frozen historical
> subset cannot see new debt accumulating in the same column. Pair every "did the backlog shrink?" read
> with a "did the inflow stop?" read.

**Exact text recommended for the record (see §K for where it goes):**

> **FAILED: Passive retroactive classification is not a viable migration strategy.** 80/80 legacy
> curator-owned public Notes remained unclassified after 30 days. No classification work was assigned or
> surfaced to curators, so the checkpoint does not establish lack of curator willingness or lack of
> learner demand for Authored Depth. It establishes that historical metadata cleanup will not happen
> reliably without an explicit workflow or owner. **Re-audited 2026-09-17: the checkpoint's population
> was a subset. The true curator-owned public NULL-depth population is 282, of which 173 were created
> after the `v0.83.0` deploy — the debt was not static, it was compounding while the checkpoint watched
> a frozen slice of it.**

Original assumption, original kill criterion, actual 30-day result and the resulting owner decision are
all preserved above and in `ROADMAP.md:824` and `ADR-001:64`, which this audit does not propose to
rewrite.

---

## C. Production audit

All queries below were issued through the Render MCP read-only connection (`postgresId
dpg-d6tvb8fkijhs73fda4m0-a`), which wraps every statement in a read-only transaction. Every statement
is a `SELECT`. No PII is exposed — owner identity is reported as a distinct count only.

### Query 1 — Does the exact 80-note population still exist? (Q20.1)

The checkpoint's own query, verbatim from `ROADMAP.md:824`:

```sql
select count(*) as null_depth_student_public_admin
from notes n join users u on u.id = n.owner_user_id
where u.role = 'ADMIN' and n.visibility = 'PUBLIC'
  and n.target_profile_type = 'STUDENT' and n.learner_level is null;
```

```
[{"null_depth_student_public_admin": 80}]
```

**Still exactly 80, one day after the checkpoint's due date. Nothing has moved.**

### Query 2 — The real denominator

```sql
select
  (select count(*) from notes n join users u on u.id=n.owner_user_id
     where u.role='ADMIN' and n.visibility='PUBLIC') as admin_public_total,
  (select count(*) from notes n join users u on u.id=n.owner_user_id
     where u.role='ADMIN' and n.visibility='PUBLIC' and n.learner_level is null) as admin_public_null_depth,
  (select count(*) from notes n join users u on u.id=n.owner_user_id
     where u.role='ADMIN' and n.visibility='PUBLIC' and n.target_profile_type='STUDENT') as admin_public_student,
  (select count(*) from notes where visibility='PUBLIC') as all_public,
  (select count(*) from notes where visibility='PUBLIC' and learner_level is null) as all_public_null_depth,
  (select count(*) from notes) as all_notes;
```

```
[{"admin_public_null_depth": 282, "admin_public_student": 120, "admin_public_total": 1892,
  "all_notes": 8292, "all_public": 1896, "all_public_null_depth": 286}]
```

The `120` denominator from the `v0.83.0` signoff reproduces exactly. **But the 80 is 28% of the real
problem: 282 curator-owned public notes carry NULL Authored Depth.**

### Query 2b — Curator definition check

The checkpoint filters `u.role='ADMIN'`. The product's own curator predicate is broader —
`CuratorAuthoringPredicate.isCurator` (`backend/src/main/java/com/studysnap/backend/service/CuratorAuthoringPredicate.java:16-21`)
returns true for `role == ADMIN` **or** `profileType == TEACHER`, gated on completed onboarding. Re-run
against the product's actual definition:

```sql
select count(*) as curator_public_null_depth_true_predicate
from notes n join users u on u.id=n.owner_user_id
where n.visibility='PUBLIC' and n.learner_level is null
  and u.onboarding_completed_at is not null
  and (u.role='ADMIN' or u.profile_type='TEACHER');
```

```
[{"curator_public_null_depth_true_predicate": 282}]
```

**Same number** — the TEACHER leg contributes zero today. The `role='ADMIN'` shorthand is currently
safe, but it is a shorthand, and 4 of the 286 all-public NULL-depth notes are learner-owned and outside
both filters (286 − 282). Two of those carry the `STUDENT` audience and are invisible to the
checkpoint's query entirely:

```sql
select u.role, u.profile_type, count(distinct n.owner_user_id) as owners, count(*) as notes
from notes n join users u on u.id=n.owner_user_id
where n.visibility='PUBLIC' and n.target_profile_type='STUDENT' and n.learner_level is null
group by 1,2 order by notes desc;
```

```
[{"notes":80,"owners":1,"profile_type":"BOARD_EXAM","role":"ADMIN"},
 {"notes":2,"owners":2,"profile_type":"BOARD_EXAM","role":"USER"}]
```

**The 80 are owned by exactly 1 ADMIN account.** (Owner id withheld; the distinct count is what the
work-queue design needs — §G.)

### Query 3 — The 282 split cleanly on retained Target Audience

```sql
select n.target_profile_type, count(*) as cnt
from notes n join users u on u.id=n.owner_user_id
where u.role='ADMIN' and n.visibility='PUBLIC' and n.learner_level is null
group by 1 order by cnt desc;
```

```
[{"cnt":202,"target_profile_type":"BOARD_TAKER"},
 {"cnt":80,"target_profile_type":"STUDENT"}]
```

282 = **80 `STUDENT`** + **202 `BOARD_TAKER`**, with no third bucket. This split is not a coincidence
and it matters — see Query 6.

### Query 4 — When were they created? (the regeneration proof)

```sql
select date_trunc('month', n.created_at)::date as created_month,
       count(*) filter (where n.learner_level is null) as null_depth,
       count(*) as total_admin_public
from notes n join users u on u.id=n.owner_user_id
where u.role='ADMIN' and n.visibility='PUBLIC'
group by 1 order by 1;
```

```
[{"created_month":"2026-03-01","null_depth":0,"total_admin_public":2},
 {"created_month":"2026-04-01","null_depth":6,"total_admin_public":19},
 {"created_month":"2026-05-01","null_depth":18,"total_admin_public":127},
 {"created_month":"2026-06-01","null_depth":14,"total_admin_public":308},
 {"created_month":"2026-07-01","null_depth":68,"total_admin_public":234},
 {"created_month":"2026-08-01","null_depth":161,"total_admin_public":653},
 {"created_month":"2026-09-01","null_depth":15,"total_admin_public":549}]
```

```sql
select count(*) as created_since_v083_deploy, min(n.created_at) as earliest, max(n.created_at) as latest
from notes n join users u on u.id=n.owner_user_id
where u.role='ADMIN' and n.visibility='PUBLIC' and n.learner_level is null
  and n.created_at >= timestamptz '2026-08-17 00:00:00+00';
```

```
[{"created_since_v083_deploy":173,
  "earliest":"2026-08-18T01:27:04.225602Z","latest":"2026-09-13T02:56:21.509157Z"}]
```

**173 of the 282 were created after the `v0.83.0` deploy.** The debt did not sit still while the
checkpoint ran; it more than doubled.

### Query 5 — What the 173 actually are

```sql
select n.target_profile_type, n.subject, count(*) as cnt
from notes n join users u on u.id=n.owner_user_id
where u.role='ADMIN' and n.visibility='PUBLIC' and n.learner_level is null
  and n.created_at >= timestamptz '2026-08-17 00:00:00+00'
group by 1,2 order by cnt desc limit 20;
```

```
[{"cnt":32,"subject":"Engineering Mechanics","target_profile_type":"BOARD_TAKER"},
 {"cnt":16,"subject":"Soil Mechanics","target_profile_type":"BOARD_TAKER"},
 {"cnt":15,"subject":"Reinforced Concrete Design","target_profile_type":"BOARD_TAKER"},
 {"cnt":15,"subject":"Strength of Materials","target_profile_type":"BOARD_TAKER"},
 {"cnt":11,"subject":"Medical – Surgical Nursing","target_profile_type":"BOARD_TAKER"},
 {"cnt":9,"subject":"Engineering Economics","target_profile_type":"BOARD_TAKER"},
 {"cnt":8,"subject":"Differential Calculus","target_profile_type":"BOARD_TAKER"},
 {"cnt":8,"subject":"Numerical Methods","target_profile_type":"BOARD_TAKER"},
 {"cnt":8,"subject":"Engineering Laws, Ethics, and Professional Practice","target_profile_type":"BOARD_TAKER"},
 {"cnt":8,"subject":"Structural Analysis","target_profile_type":"BOARD_TAKER"},
 {"cnt":8,"subject":"Probability and Statistics","target_profile_type":"BOARD_TAKER"},
 {"cnt":7,"subject":"Integral Calculus","target_profile_type":"BOARD_TAKER"},
 {"cnt":6,"subject":"Steel Design","target_profile_type":"BOARD_TAKER"},
 {"cnt":5,"subject":"Analytic Geometry","target_profile_type":"BOARD_TAKER"},
 {"cnt":5,"subject":"Algebra","target_profile_type":"BOARD_TAKER"},
 {"cnt":4,"subject":"Trigonometry","target_profile_type":"BOARD_TAKER"},
 {"cnt":4,"subject":"Differential Equations","target_profile_type":"BOARD_TAKER"},
 {"cnt":2,"subject":"Pharmacology","target_profile_type":"BOARD_TAKER"},
 {"cnt":1,"subject":"Psychiatric – Mental Health Nursing","target_profile_type":"BOARD_TAKER"},
 {"cnt":1,"subject":"Prioritization and Clinical Judgement","target_profile_type":"BOARD_TAKER"}]
```

The post-deploy inflow is **entirely `BOARD_TAKER`** — Civil Engineering and Nursing board-review
authoring, exactly the corpus NoteLib is weighted toward, published without the depth axis that drives
its own quiz difficulty floor.

### Query 6 — A precedent already exists for the 202, and it was owner-ratified (report only, do NOT act)

`V117__backfill_curator_note_learner_level.sql` (`backend/src/main/resources/db/migration/`) ran in
`v0.82.0`. It maps, **for ADMIN-owned NULL-depth notes only**, `BOARD_TAKER → BOARD_EXAM_REVIEW` and
`PROFESSIONAL → PROFESSIONAL`, behind an audited denylist of program values known not to be licensure
programs, and it deliberately **leaves `STUDENT` NULL** because `STUDENT` spans four depths
(`ADR-001:68`; migration header, lines 1–13). It is a one-time migration, explicitly **not** a runtime
fallback (`AGENTS.md:75`, `docs/features/notes.md:100`).

How many of today's 202 `BOARD_TAKER` rows would that already-ratified rule cover if it ran now:

```sql
select count(*) as v117_eligible_today
from notes n join users u on u.id = n.owner_user_id
where u.role='ADMIN' and n.visibility='PUBLIC' and n.learner_level is null
  and n.target_profile_type in ('BOARD_TAKER','PROFESSIONAL')
  and not (
    coalesce(lower(trim(n.course_program)),'') in ('information technology','grade school','high school')
    or coalesce(lower(trim(n.course_program)),'') like 'junior high%'
    or coalesce(lower(trim(n.course_program)),'') like 'senior high%'
    or exists (
      select 1 from note_course_program ncp join course_programs cp on cp.id = ncp.course_program_id
      where ncp.note_id = n.id and (
        lower(trim(cp.name)) in ('information technology','grade school','high school')
        or lower(trim(cp.name)) like 'junior high%'
        or lower(trim(cp.name)) like 'senior high%')
    )
  );
```

```
[{"v117_eligible_today":193}]
```

**193 of the 202.** This is reported as a fact, **not recommended**, and it is deliberately routed to
§N as an OWNER DECISION. Three things must be said plainly about it:

- It is **Target Audience → Depth**, which is a *different axis pair* from the **Course/Program →
  Depth** inference the owner's §4 locks out. §4's lock is untouched by it.
- It is nonetheless **inference**, it is **a production write**, and CLAUDE.md's production rule plus
  the owner's §15 both put it entirely outside what Claude may execute. If the owner wants it, it is a
  new Flyway migration the owner runs, not something this plan performs.
- **Re-running V117's rule would NOT close the checkpoint's own 80** — `STUDENT` is excluded from it by
  design, for the exact reason §4 gives. The 80 still need human classification either way.

### Query 7 — Operational groupings for the 80 (Q20.3, Q20.4)

By joined catalog program:

```sql
select coalesce(cp.name, '(no joined program)') as program, count(*) as cnt
from notes n join users u on u.id=n.owner_user_id
left join note_course_program ncp on ncp.note_id = n.id
left join course_programs cp on cp.id = ncp.course_program_id
where u.role='ADMIN' and n.visibility='PUBLIC' and n.target_profile_type='STUDENT' and n.learner_level is null
group by 1 order by cnt desc;
```

```
[{"cnt":63,"program":"Information Technology"}, {"cnt":8,"program":"(no joined program)"},
 {"cnt":2,"program":"Physical Therapy"}, {"cnt":1,"program":"Electrical Engineering"},
 {"cnt":1,"program":"Nursing"}, {"cnt":1,"program":"Nursing · Medicine"},
 {"cnt":1,"program":"Accountancy"}, {"cnt":1,"program":"Psychology"},
 {"cnt":1,"program":"Business Administration"}, {"cnt":1,"program":"Criminology"}]
```

By subject:

```sql
select n.subject, count(*) as cnt, min(n.created_at)::date as first_created, max(n.created_at)::date as last_created
from notes n join users u on u.id=n.owner_user_id
where u.role='ADMIN' and n.visibility='PUBLIC' and n.target_profile_type='STUDENT' and n.learner_level is null
group by n.subject order by cnt desc;
```

```
[{"cnt":17,"subject":"Computer Networking","first_created":"2026-07-02","last_created":"2026-07-04"},
 {"cnt":10,"subject":"Database Systems","first_created":"2026-07-02","last_created":"2026-07-02"},
 {"cnt":10,"subject":"System Integration & Architecture","first_created":"2026-07-02","last_created":"2026-07-05"},
 {"cnt":8,"subject":"Application Development","first_created":"2026-07-02","last_created":"2026-07-05"},
 {"cnt":5,"subject":"Computer Science","first_created":"2026-04-06","last_created":"2026-05-21"},
 {"cnt":3,"subject":"Information Assurance and Security"}, {"cnt":3,"subject":"Biology"},
 {"cnt":3,"subject":"Object – Oriented Programming"}, {"cnt":3,"subject":"Advanced Programming"},
 {"cnt":3,"subject":"Professional Elective"}, {"cnt":2,"subject":"Web Development"},
 {"cnt":1,"subject":"Psychology"}, {"cnt":1,"subject":"Science"}, {"cnt":1,"subject":"General"},
 {"cnt":1,"subject":"Clinical Chemistry"}, {"cnt":1,"subject":"Criminology"},
 {"cnt":1,"subject":"Earth Science"}, {"cnt":1,"subject":"Electrical Engineering"},
 {"cnt":1,"subject":"Exercise Physiology"}, {"cnt":1,"subject":"Accounting – Basic Concepts"},
 {"cnt":1,"subject":"Marketing Mix"}, {"cnt":1,"subject":"Microbiology"},
 {"cnt":1,"subject":"Nursing"}, {"cnt":1,"subject":"Physical Therapy"}]
```

(Truncated `first_created`/`last_created` on single-note rows for brevity; all fall between
2026-04-02 and 2026-07-05.)

By Review Set / collection (the §14 question):

```sql
select coalesce(c.title,'(not in any collection)') as collection_title, c.learner_level as collection_level,
       count(distinct n.id) as cnt
from notes n join users u on u.id=n.owner_user_id
left join note_collection_items ci on ci.note_id = n.id
left join note_collections c on c.id = ci.collection_id
where u.role='ADMIN' and n.visibility='PUBLIC' and n.target_profile_type='STUDENT' and n.learner_level is null
group by 1,2 order by cnt desc;
```

```
[{"cnt":29,"collection_level":null,"collection_title":"(not in any collection)"},
 {"cnt":17,"collection_level":null,"collection_title":"🏗️ System Integration and Architecture"},
 {"cnt":15,"collection_level":null,"collection_title":"🌐 Networking II"},
 {"cnt":8,"collection_level":null,"collection_title":"🗄️ Advanced Database Systems"},
 {"cnt":8,"collection_level":null,"collection_title":"⚡ Event-Driven Programming"},
 {"cnt":1,"collection_level":null,"collection_title":"🤰 Maternal and Newborn Nursing"},
 {"cnt":1,"collection_level":null,"collection_title":"🩺 Foundations, Professional Nursing Practice, and Clinical Judgment"},
 {"cnt":1,"collection_level":"BOARD_EXAM_REVIEW","collection_title":"💰 Financial Accounting and Reporting"}]
```

**Operational shape of the 80 (Q20.2–20.4 answered):**

- **One owner.** A single ADMIN account owns all 80. There is no cross-owner coordination problem;
  there is a single-person assignment.
- **Highly clustered by discipline.** 63 of 80 (79%) are Information Technology; 51 of 80 sit in four
  coherent IT Review Sets (System Integration and Architecture 17, Networking II 15, Advanced Database
  Systems 8, Event-Driven Programming 8). 29 are in no collection at all and form the long tail.
- **Genuinely legacy.** Created 2026-04-02 → 2026-07-05. Zero were created after the `v0.83.0` deploy —
  the 173 post-deploy notes are all `BOARD_TAKER` and therefore outside this population. **The 80 are a
  closed set that cannot grow**, which is what makes bounding the cleanup on them coherent even though
  the wider NULL-depth count is not bounded.
- **Sibling context is available but is not permission.** The four IT Review Sets themselves carry
  `learner_level = null`, so there is no inherited depth to lean on; the sibling evidence is the
  *subject matter* (undergraduate IT coursework), which a human reads, not a rule that fires.
- **No note in this population appears undeterminable.** Every one has a subject and (for 72 of 80) a
  joined program, and the corpus is coursework with an obvious academic level to its author. §G reserves
  an exception mechanism anyway, because guessing is worse than recording "unknown".

### Query 8 — Current Public Library depth distribution (for §I)

```sql
select coalesce(learner_level,'(NULL)') as depth, count(*) as public_notes
from notes where visibility='PUBLIC' group by 1 order by public_notes desc;
```

```
[{"depth":"COLLEGE","public_notes":791}, {"depth":"BOARD_EXAM_REVIEW","public_notes":778},
 {"depth":"(NULL)","public_notes":286}, {"depth":"JUNIOR_HIGH","public_notes":27},
 {"depth":"SENIOR_HIGH","public_notes":11}, {"depth":"GRADE_SCHOOL","public_notes":3}]
```

Five of seven enum values are live and render chips. `PROFESSIONAL` and `PERSONAL_LEARNING` have zero
public notes and correctly render no chip. **15.1% of the public library (286/1,896) is unreachable by
any depth chip.**

---

## D. Current lifecycle audit — how `authoredDepth` behaves end to end

**Enum (verified, the task brief's list was correct):** `LearnerLevel` —
`GRADE_SCHOOL, JUNIOR_HIGH, SENIOR_HIGH, COLLEGE, BOARD_EXAM_REVIEW, PROFESSIONAL, PERSONAL_LEARNING`
(`backend/src/main/java/com/studysnap/backend/entity/LearnerLevel.java:6-12`). Column
`notes.learner_level`, `varchar(32)`, **nullable, no DB constraint**
(`NoteEntity.java:46-47`).

### Create

- `NoteService.create` (`NoteService.java:175-217`) parses depth via
  `NoteAuthoringMetadataParser.parseLearnerLevelOrThrow` (`NoteAuthoringMetadataParser.java:23-32`),
  which **returns `null` for null-or-blank** and only throws on an unrecognised string. Writes it at
  `NoteService.java:190`.
- **`UpsertNoteRequest.learnerLevel` carries no `@NotNull`** (`UpsertNoteRequest.java:25`). There is no
  bean-validation requirement at any layer.
- Create always sets `visibility = PRIVATE` (`NoteService.java:194`), so create alone never adds a
  public NULL-depth note.
- Frontend mitigation: on create the control **pre-fills from the author's own profile level**
  (`docs/features/notes.md:99`), so the UI create path usually produces a value. This is a pre-fill,
  not a requirement — blank still saves as null (`notes.md:65`).

### Edit

- `NoteService.update` (`NoteService.java:219-304`) writes depth unconditionally at
  `NoteService.java:283` from the request. `PUT /notes/{id}` is a **full replace, not a merge** — and
  `frontend/lib/api.ts:1758-1779` documents that this exact shape already caused silent data loss once:
  *"Two update call sites on the note detail page omitted them and silently wiped both axes off any note
  they touched."* The TypeScript type now makes `domainContext` and `learnerLevel` **non-optional**
  (`api.ts:1776`), which closes it for in-repo callers but not at the HTTP boundary.
- Editing is curator-gated in the UI: `canEditAuthoringMetadata`
  (`frontend/components/notes/private-note-detail-page-client.tsx:978`) drives the inline metadata
  panel; the depth select writes `metadataDraft.learnerLevel` and saves through
  `handleSaveMetadata` (`:1884-1922`), sending `learnerLevel: nextLearnerLevel` at `:1922`.
- Its existing validation is instructive for §F: that same handler **already blocks the save** when
  Course/Program(s) is empty (`:1888-1891`) and when a multi-program note has no Domain Context
  (`:1894-1898`). A depth requirement would be the third member of a pattern that already exists.

### Generate

`StudyPackGenerationContextResolver.resolve` (`StudyPackGenerationContextResolver.java:40-62`) reads
the note's own column into `StudyPackGenerationContext.noteLearnerLevel`
(`service/model/StudyPackGenerationContext.java:14`). Four live consumption sites — **Authored Depth is
not discovery-only metadata** (Q20.10):

1. **Quiz/exam curriculum floor.** `effectiveCurriculumLevel`
   (`StudyPackGenerationContextResolver.java:196-203`): note depth → profile level → `COLLEGE` default
   (`:26`). This is `ADR-001:41`'s rule 3 in code.
2. **Static content calibration.** `OpenAiLlmStudyPackService.buildGenerationContextBlock:1585-1626`
   reads `noteLearnerLevel` **directly**, never the reader's level, and emits *no* `Curriculum level:`
   line at all when the note has none (`:1596-1601`). `ADR-001:40` rule 2.
3. **Subject-suggestion guidance.** `buildSubjectSuggestionGuidanceBlock:1636-1647` branches on the
   authored level; with a null level it deliberately emits **both** school-level and college-level
   subject lists — the documented "~all-null production rows" fallback (`:1641-1642`).
4. **Reader scaffolding.** `isReaderBelowNoteLevel:1723-1729` softens wording when the reader is below
   the note's authored depth — **and cannot fire at all when the note's depth is null.**

**So a NULL-depth note is not merely unfilterable. It generates less precisely calibrated Study Packs,
gets no curriculum floor for its quizzes, and receives ambiguous subject guidance.** That is the real
cost of the 282 and it is invisible from the discovery surface.

### Publish

- `POST /notes/{id}/visibility` (`NoteController.java:709-721`). The **only** gate is
  `authService.requireEmailVerified(userId)` when the target is `PUBLIC` (`:717-719`).
- `NoteService.updateVisibility` (`NoteService.java:506-535`) sets visibility (`:513`), clears copied
  applicability on publish, fires `PUBLIC_NOTE_PUBLISHED`, queues the official-quiz seed. **It never
  reads `learnerLevel`.** No validation, no warning, no branch.
- Frontend: `private-note-detail-page-client.tsx:1746-1754` gates on `isEmailVerified` and opens a
  confirm dialog. **No depth check.**

### Public Library

`PublicLibraryRepositoryImpl.buildFilter` appends `and n.learner_level = :learnerLevel` **only when a
level is supplied** (`PublicLibraryRepositoryImpl.java:357-362`, guarded by `criteria.learnerLevel() !=
null`). See §I.

### Regenerate

`resolveForStudyPack` (`StudyPackGenerationContextResolver.java:64-82`) re-reads the source note, so a
regeneration picks up whatever depth the note carries **at that moment**. Correcting depth therefore
shapes future generation and only future generation.

---

## E. Recurrence analysis — can NoteLib produce another NULL-depth curator-owned public Note tomorrow?

**YES. It produced 173 of them in the last 30 days (§C Query 4), and there are four distinct
mechanisms.**

### Vector 1 — the ordinary publish path (primary)

```
Note Detail "Make public" → POST /notes/{id}/visibility → NoteController.java:709-721
   (only gate: requireEmailVerified, :717)
→ NoteService.updateVisibility:506-535  (never reads learnerLevel)
→ notes.visibility = 'PUBLIC', learner_level unchanged and possibly NULL
```

Nothing anywhere on this path inspects Authored Depth. A curator who leaves the create-time pre-fill
blank, or clears it, or authors through any path that does not pre-fill, publishes a NULL-depth public
note with no signal of any kind.

### Vector 2 — bulk generation with `makePublic` (the volume vector, and the one that produced the 173)

```
/library/bulk-generate → bulk-generation-page-client.tsx:414 (learnerLevel: learnerLevel || null)
→ POST /notes/bulk-generate  (BulkGenerateNotesRequest.learnerLevel, :23, NO @NotNull)
→ NoteBulkGenerationService.processBatch:302-348
     → noteService.create(...)  with batch.learnerLevel possibly null   (:322-334)
     → if (batch.makePublic) { try { noteService.updateVisibility(note.id, "PUBLIC", ownerUserId); }
                               catch (RuntimeException e) { log.warn(...); } }   (:335-348)
```

**One request publishes N notes.** The depth control pre-fills from the selected Review Set's own or
nearest inherited depth, then the author's profile level
(`docs/features/bulk-generation.md:51-58`), and **no resolved collection level means no pre-fill —
specifically, no `COLLEGE` default** (`bulk-generation.md:43`). `bulk-generation.md:86` records the
field as *"Optional request"* for every profile.

**This mechanism is confirmed directly against the post-deploy corpus, not inferred from the legacy
one.** Every collection holding the 173 carries `learner_level = null` itself, so every
Review-Set-driven batch in that window resolved no pre-fill:

```sql
select coalesce(c.title,'(none)') as collection_title, c.learner_level as collection_level,
       count(distinct n.id) as cnt
from notes n join users u on u.id=n.owner_user_id
left join note_collection_items ci on ci.note_id=n.id
left join note_collections c on c.id=ci.collection_id
where u.role='ADMIN' and n.visibility='PUBLIC' and n.learner_level is null
  and n.created_at >= timestamptz '2026-08-17 00:00:00+00'
group by 1,2 order by cnt desc;
```

```
[{"cnt":55,"collection_level":null,"collection_title":"📐 Engineering Mathematics"},
 {"cnt":43,"collection_level":null,"collection_title":"🏗 Structural Engineering"},
 {"cnt":32,"collection_level":null,"collection_title":"⚙️ Engineering Mechanics"},
 {"cnt":15,"collection_level":null,"collection_title":"🌍 Geotechnical Engineering"},
 {"cnt":13,"collection_level":null,"collection_title":"(none)"},
 {"cnt":6,"collection_level":null,"collection_title":"🚨 Emergency, Critical Care, and High-Acuity Nursing"},
 {"cnt":4,"collection_level":null,"collection_title":"🧬 Adult Health Nursing II — Nutrition, GI, Endocrine, Neurologic, Musculoskeletal, and Sensory"},
 {"cnt":2,"collection_level":null,"collection_title":"💊 Pharmacology, Medication Management, and Dosage Calculation"},
 {"cnt":2,"collection_level":null,"collection_title":"❤️ Adult Health Nursing I — Perioperative, Oxygenation, Fluids, Infection, Immunology, and Oncology"},
 {"cnt":1,"collection_level":null,"collection_title":"🧠 Mental Health and Psychiatric Nursing"}]
```

160 of the 173 sit in **ten** Review Sets — four Civil Engineering, six Nursing — **every one of them
NULL-depth**, and the remaining 13 in no collection at all. The pre-fill chain had nothing to resolve
from in any of the ten. **This is the highest-throughput path to new debt, it matches the 173's shape
exactly, and §F1 is aimed at it on the strength of the post-deploy evidence rather than the legacy
evidence.**

**A secondary observation worth recording:** the ten collections are themselves NULL-depth, so the same
authoring gap exists one level up on `note_collections.learner_level`. This audit does **not** propose
acting on it (§19: keep the response proportional), but it explains *why* the pre-fill never fires and
is the reason a warning at publish time is more useful than tuning the pre-fill chain.

**Critical for §F:** the `updateVisibility` call at `NoteBulkGenerationService.java:337` sits inside a
`try` (`:336`) whose `catch (RuntimeException exception)` (`:338`) does nothing but `log.warn(...)`
(`:339-346`). **A publication-time exception here is swallowed into a server log.** If a hard
publish-time depth requirement were added *only* at `updateVisibility`, a `makePublic=true` batch with
no depth would create N notes that all silently fail to publish, with the failure visible nowhere but
Render logs, and the terminal receipt reporting success. That is a worse failure than the one being
fixed — it directly answers Q20.17 and it constrains §F's design.

### Vector 3 — copy, then publish

`NoteService.copy` inherits the source's depth (`NoteService.java:383`) and forces
`visibility = PRIVATE` (`:387`). A copy of a NULL-depth note is NULL-depth; publishing it re-enters
Vector 1. Low volume, same seam.

### Vector 4 — full-replace edit clears an existing value

`PUT /notes/{id}` writes depth unconditionally from the request (`NoteService.java:283`), so **any
caller that omits `learnerLevel` nulls it out on an already-classified note.** This is documented as
having already happened once (`frontend/lib/api.ts:1758-1769`). The TypeScript type change makes it a
compile error for in-repo callers, but it is not enforced server-side — nothing in
`UpsertNoteRequest.java` or `NoteService.update` distinguishes "omitted" from "explicitly cleared".
**This vector can un-do the cleanup after it completes**, which is a reason the cleanup needs a
re-read, not just a completion tick (§G).

### Paths audited and found NOT to be vectors

- **Review Set / collection publishing.** `NoteCollectionService.assertAllNotesPublic` (≈`:1833-1846`)
  *requires* every item note to be `PUBLIC` already and throws `CollectionNotPublishableException`
  otherwise; `publishChildCollections` (`:1848-1860`) touches collections only. **Publishing a Review
  Set never publishes a note.**
- **Note import** (`NoteController.java:192`) and **bulk regeneration**
  (`NoteController.java:268-278`) — neither publishes; bulk regeneration writes only `tags` and
  `subject` (`StudyPackService.java:1397-1406`).
- **Share with connections.** `ShareService.java:127` and `StudyPackService.java:1006` set `PRIVATE`.
- **Admin surfaces.** No admin controller publishes notes or writes `learnerLevel`. The only
  `setLearnerLevel` call sites on a `NoteEntity` in the entire backend are `NoteService.java:190`
  (create), `:283` (update), `:383` (copy).

**Answer to Q20.5–20.8 in one line:** yes; through the four vectors above; Authored Depth is validated
**nowhere** before publication; and it is required **nowhere** during creation.

---

## F. Prevention recommendation

**Recommendation: (C) ADMIN/CURATOR QUALITY SIGNAL + (B) PUBLICATION-TIME WARNING, staged — and NOT
(A) a hard publication requirement, at least not first.**

### Why not a hard requirement (A) as the opening move

1. **It breaks a real workflow, and silently** (Q20.17 answered YES). `NoteBulkGenerationService:337`
   swallows a publish failure into `log.warn` (§E Vector 2). A hard throw at `updateVisibility` turns a
   `makePublic` batch into N unpublished notes with a success receipt. Any hard requirement would have
   to land at the **bulk request boundary too** (`BulkGenerateNotesRequest`), which makes it a
   multi-surface change with a breaking API contract — and CLAUDE.md's rule on removing or
   making-required an existing API form demands a deploy-ordering statement and simultaneous
   frontend/backend release for exactly that reason.
2. **It would newly reject live accounts.** This repo has been burned three times by gate-tightening
   that locked out existing cohorts (`v0.71.0` ADMIN onboarding lockout; `v0.97.0`/`v0.98.0`
   onboarding-gate cohorts). Every one of the 282 existing public NULL-depth notes would fail
   re-publication after any unpublish, including the common unpublish-to-share-privately flow
   (`private-note-detail-page-client.tsx:1785-1795`).
3. **Legitimate unknowns exist.** `PERSONAL_LEARNING` content and cross-level reference material have
   no honest single depth, and the owner's own doctrine ("unknown metadata should remain unknown")
   argues against forcing a value to clear a gate.
4. **It imposes nothing on learners — but it is still the largest change available**, and §19 says keep
   the response proportional.

### The recommended seam, smallest first

**F1 (ship first, smallest coherent prevention): a publication-time warning at the two publish
surfaces.** In the existing *Make public* confirmation dialog
(`private-note-detail-page-client.tsx:1751`, `showMakePublicConfirm`), and in the Bulk Generate form
when `makePublic` is checked and no depth is selected
(`bulk-generation-page-client.tsx:682` / `:414`), surface a non-blocking line: *this note will not
appear under any Authored Depth filter*. Publication still proceeds. This is copy plus one conditional
in each of two existing components — no API change, no migration, no deploy-ordering hazard, and it
reaches the exact moment the decision is made. **It is also the only option that addresses Vector 2
without touching the swallowed-exception path at all.**

**F2 (ship with or just after F1): a curator-scoped missing-depth count, in an existing surface.**
There is today **no place any curator or admin can see which of their notes are missing metadata**
(§B.1). The natural host already exists and is already scoped to the requesting admin's own notes:
`AdminNoteApplicableProgramsController` / `admin-applicable-programs-section.tsx` on
`/admin/course-programs`. It needs `learnerLevel` added to
`AdminNoteApplicableProgramsItemResponse` (`:6-12`) and a *missing depth* filter/count. **This is
§16-compliant**: no new dashboard, no new sidebar destination, no notification system, no
task-management system — one column and one filter on a page that already lists the curator's notes for
exactly this class of metadata repair.

**F3 (only if F1+F2 fail to move the inflow): reconsider (A).** Gate it on a measurable: re-read the
post-deploy NULL-depth inflow 30 days after F1 ships. If new curator public NULL-depth notes are still
being created at a material rate, the warning was insufficient and a hard requirement is justified by
evidence rather than by instinct — and by then §J's instrumentation question will also have been
decided.

**Deliberately excluded:** making `notes.learner_level` `NOT NULL` at the database level (too broad,
§9 warns against it, and 8,292 rows exist of which the overwhelming majority are learner-owned private
notes that never made this authoring decision); requiring depth at create time (§10 is right — a curator
legitimately creates, drafts, generates, classifies, then publishes); any learner-facing friction.

### Q20.16 — the smallest prevention seam, stated

**The transition into PUBLIC is the correct seam** (the owner's §10 instinct is confirmed by the code:
create forces `PRIVATE` at `NoteService.java:194`, so publication is genuinely the only moment a note
enters the Public Library). The smallest *coherent* mechanism at that seam is a **warning**, not a
throw, because the one caller that would be most affected by a throw cannot report it (§E Vector 2).

---

## G. Legacy cleanup plan

### Target set — three buckets, and the owner must pick the target (see §N)

| Bucket | Count | Nature | Can a rule close it? |
|---|---|---|---|
| **B1** — the checkpoint's 80 (`STUDENT`, ADMIN, PUBLIC, NULL) | 80 | Closed, legacy (created 2026-04→07), one owner | **No.** `STUDENT` spans four depths; `ADR-001:68` and V117 both refuse it by design. Human only. |
| **B2** — `BOARD_TAKER`, ADMIN, PUBLIC, NULL | 202 | 173 created post-deploy; still growing | **Partly** — 193 match V117's already-ratified, already-audited rule. Owner decision, owner-executed. |
| **B3** — learner-owned public NULL-depth | 4 | Outside the curator invariant | Out of scope; the invariant in §18 is about curator-published content. |

**Recommended target for the cleanup task: B1 (80), exactly as the owner scoped it** — and B2 handled
separately, because it is a different problem with a different instrument and because folding it in
would make a bounded task unbounded. That said, **the owner must say explicitly what happens to B2**,
or this plan repeats the `v0.83.0` mistake at 2.5× the scale.

### Work grouping (from §C Query 7)

Batch by Review Set, largest coherent block first. Each batch is one subject family the curator
authored themselves, so the classification decision is made once per batch and applied per note:

1. 🏗️ System Integration and Architecture — 17
2. 🌐 Networking II — 15
3. 🗄️ Advanced Database Systems — 8
4. ⚡ Event-Driven Programming — 8
5. The IT long tail not in a collection — remainder of the 63 IT notes
6. Non-IT singletons — Nursing ×2, Physical Therapy ×2, Accountancy, Psychology, Criminology,
   Electrical Engineering, Business Administration, Biology ×3, and the rest (~17 notes, one decision
   each)

**Sibling metadata is context for the curator, never permission for automatic assignment** (owner §14,
and this plan holds that line). The four IT Review Sets carry no `learner_level` of their own, so there
is not even an inherited value to be tempted by.

### Owner model

**A single named curator — the one ADMIN account that owns all 80** (§C Query 2b). No coordination
required, no assignment ambiguity. The work is theirs because they authored the content and are the only
person who knows the depth it was written at.

### Completion definition

> **0 of the 80 target Notes carry `learner_level IS NULL`, verified by re-running the checkpoint query
> in §C Query 1 and receiving `0` — except for notes recorded on an explicit, named exceptions list with
> a stated reason why Authored Depth cannot be determined.**

Two properties that matter:

- **It is re-read, not ticked.** §E Vector 4 means a later full-replace edit can silently re-null a
  classified note. The verification is a `SELECT`, run at the end, not a checklist the curator marks.
- **Exceptions are enumerated, not implied.** §C found no note that looks undeterminable, so the
  expected exception list is empty; if it is non-empty the reasons are recorded per note, and those
  notes stay NULL rather than receiving a manufactured value.

### Deadline structure

- **Prevention (F1) ships first.** The cleanup starts after it, so the completion criterion is stable.
- **Cleanup window: 14 days from F1's deploy.** 80 single-field edits in ~6 coherent batches is a
  few hours of work, not a project; a 14-day window is generous and still short enough that the result
  is attributable.
- **One checkpoint, not a series.** `[CHECKPOINT — due <F1 deploy + 14d>]` in `ROADMAP.md`'s Backlog
  Index, reading §C Query 1, with a kill criterion **stated before the read**: *if the count is above
  10, assigning the work to a named owner with a deadline is ALSO not sufficient, and the next response
  is tooling (§H) rather than a third extension.* No indefinite extension (owner §7); the checkpoint
  either closes or escalates to exactly one named alternative.
- **A second, separate read of the inflow** — `created_at >= <F1 deploy>` NULL-depth curator public
  count — on the same date. That is what tells you whether F1 worked, and it is the read the original
  checkpoint was missing.

### Manual vs. tooling

**Manual remains preferable for B1.** See §H.

---

## H. Bulk editing verdict

**DO NOT BUILD — and the audit found no reusable capability that changes that.**

### What exists, precisely

- **Multi-select UI is already built.** `frontend/app/library/page.tsx:627-629` (`selectionMode`,
  `selectionIntent`, `selectedNoteIds`), select-all at `:1447`, three intents at `:1705-1790`
  (`collection`, `combined-quiz`, `regenerate`). So the selection mechanism is not the gap.
- **An ids-taking endpoint shape already exists.** `POST /notes/bulk-regenerate`
  (`NoteController.java:268-278`), `BulkRegenerateNotesRequest(List<UUID> noteIds, String scope)`
  (`BulkRegenerateNotesRequest.java:16`). So the endpoint shape is not the gap either.
- **The write path does not exist, and this is decisive.** Bulk regeneration writes **only `tags` and
  `subject`** (`StudyPackService.applyBulkGeneratedMetadataToNote:1397-1406`). The complete set of
  `setLearnerLevel(...)` call sites on a `NoteEntity` in the backend is three:
  `NoteService.java:190` (create), `:283` (update), `:383` (copy). **Nothing writes depth on more than
  one existing note.**
- **`POST /notes/bulk-generate` accepts `learnerLevel` batch-wide** (`BulkGenerateNotesRequest.java:23`)
  — but it is a **create** path producing new notes from topics. It is the single most likely thing to
  be misread as "bulk depth editing already exists." It is not.

### Why not build it

1. **Cost vs. benefit is upside-down.** 80 notes × one dropdown ≈ a few hours, once. A bulk depth
   editor is a new endpoint, authorization, a selection-to-write UI, a real-request `MockMvc` test
   (CLAUDE.md requires one for every new endpoint), an `lib/api-*.test.ts` request-shape test, and a
   feature doc — squarely in Codex-routing territory and far more than a few hours.
2. **It would not remove the human judgment anyway** (owner §12). Each note still needs a semantic
   decision; a bulk UI saves navigation, not thinking. The one thing that would make it cheap —
   assigning one value to a whole selection — is precisely what §4 and §14 forbid doing from a
   Course/Program grouping.
3. **It is a metadata-platform seed.** §19 rules out a bulk metadata platform, and the safest way to
   not build one is to not build its first endpoint for an 80-row, one-time job.
4. **The grouping already does the work a bulk tool would do.** 51 of 80 sit in four coherent Review
   Sets, so the curator opens one Review Set, makes one decision, and applies it 17 times through an
   editor they already know.

**The honest counter-argument, recorded:** if the owner decides B2's 202 must also be classified by
hand, the manual total becomes 282 and the arithmetic shifts. That is exactly why B2's disposition is an
owner decision in §N and not a silent assumption here. Even then, the answer is more likely
"owner-executed migration for the 193 V117-eligible rows" than "build a bulk editor".

---

## I. Public Library behavior

**Confirmed: current behavior is already exactly what the owner's §5 requires. Nothing needs to change,
and this plan proposes no change.**

| Situation | Behavior | Evidence |
|---|---|---|
| No depth filter active | NULL-depth public notes appear normally in browse, search, subject, tag, program and creator filters | `PublicLibraryRepositoryImpl.buildFilter` appends the depth predicate **only** inside `if (criteria.learnerLevel() != null)` (`:357-362`); no other clause references `learner_level` |
| A specific depth chip applied | NULL-depth notes are excluded | `and n.learner_level = :learnerLevel` (`:360`) — equality cannot match NULL |
| Chip list | Only depths actually present on public notes get a chip | `NoteRepository.findLearnerLevelsByVisibility` (`:248-255`): `select distinct n.learnerLevel … where n.visibility = :visibility and n.learnerLevel is not null order by n.learnerLevel` → `NoteService.java:1139-1141` → `NoteController.java:875-878` → `frontend/lib/api.ts:5794-5800` → intersected with `LEARNER_LEVEL_OPTIONS` at `public-library-page-client.tsx:802-804`, rendered at `:1761-1786` |
| Discovery sections (Featured/Popular/Recent) | Ignore `level` entirely, so NULL-depth notes remain fully eligible for the homepage discovery rails | `docs/features/public-library.md:364` |

**Two things verified as NOT defects:**

- The repository query explicitly excludes nulls (`is not null`, `NoteRepository.java:253`), so there is
  no empty or broken chip. A null element cannot reach the chip list.
- 286 of 1,896 public notes (15.1%) are unreachable by depth filtering, but **reachable by every other
  route** — browse, search, subject, program, tag, creator, and all three discovery rails.

**Live coupling worth flagging for whoever next touches this surface (report only, do not fix here):**
`resolveLearnerLevel` (`public-library-page-client.tsx:136-144`) normalizes slug forms client-side and
must stay in step with `LearnerLevel.fromSlug` (`LearnerLevel.java:24-29`) server-side; and
`docs/features/public-library.md:287` records that a new enum value added without a matching
`LEARNER_LEVEL_OPTIONS` entry (`frontend/lib/learning-profile.ts:3-11`) is silently dropped by the
client.

**No learner-facing `Unclassified` chip is proposed.** §5's reasoning is correct and the code gives it
no seam anyway: the chip list is derived from non-null values by construction, so an `Unclassified` chip
would require a sentinel the enum does not have.

---

## J. Instrumentation

**Infrastructure: YES, mature. Coverage of the Public Library filter surface: ZERO.**

### What exists

`AnalyticsEventType` (`backend/src/main/java/com/studysnap/backend/entity/AnalyticsEventType.java:3-166`,
~160 values) → `POST /analytics/events` (`AnalyticsController.java:18-42`) →
`AnalyticsEventListener.java:36-53` (async, `AFTER_COMMIT`) → `analytics_events` table
(`AnalyticsEventEntity.java:19-20`; DDL `db/migration/V25__analytics_events.sql:1-17`). Frontend helper:
`trackAnalyticsEvent` (`frontend/lib/api.ts:3348-3358`); page-view wrapper
`components/analytics/page-view-tracker.tsx:13`.

### What is missing, with the absence established four ways

1. **No vocabulary.** Not one of the ~160 enum values names a filter, search, sort, or depth/level
   action. `frontend/lib/api.ts:733` types the request as `eventType: AnalyticsEventType`, so the
   frontend **cannot** fire one without a backend enum change first (the AGENTS/CLAUDE convention:
   *add to the enum before firing new events*).
2. **The page client fires nothing.** `public-library-page-client.tsx` (2,146 lines) has **no
   `trackAnalyticsEvent` import or call**. `applyModalFilters` (`:1070-1107`) only calls
   `setLastChangedFilter` and `replacePublicLibraryFilters`; the depth chips (`:1773-1784`) only call
   `setLearnerLevelDraft`; the active "Depth: …" clear pill (`:1226-1243`) only rewrites the URL.
3. **No page-view tracker on the surface.** All six `AnalyticsPageViewTracker` usages are `app/page.tsx:529`,
   `app/how-it-works/page.tsx:127`, `app/explore/explore-page-client.tsx:72`,
   `app/exam/[slug]/page.tsx:210`, `app/collections/published/published-plans-page-client.tsx:185`,
   `app/impact/impact-page-client.tsx:177`. Neither `app/public/library/page.tsx` nor its client is
   among them.
4. **Server side is silent.** `PUBLIC_NOTE_VIEWED` fires only from the detail paths
   (`NoteService.java:1156`, `:1190`). `listPublicLearnerLevels` (`:1139-1141`) and the browse list
   path behind `NoteController.java:821` fire nothing.

**One qualification, so this is not overstated:** the surface is not entirely eventless — the card-level
`PublicLibraryCopyAction` fires `PUBLIC_NOTE_COPY_CLICKED`
(`components/notes/public-library-copy-action.tsx:64-70`). The accurate statement is **exactly one
card-level conversion event, and zero discovery/view/filter events.** Explore, the adjacent discovery
surface, has both a view and an interaction event (`explore-page-client.tsx:65,72`); Public Library has
no equivalent.

### Recommendation

**Do NOT ship instrumentation as part of this cleanup.** It would need a backend enum change, a new
frontend call site, and a feature-doc update — small, but a third independent workstream on a decision
that is already about scope discipline (§19). Keep it separate, exactly as the owner's §17 allows.

**Backlog recommendation, with the measurement question stated concretely** (for a future
`ROADMAP.md` Backlog Index row, not added by this audit):

> **Public Library filter instrumentation — can we tell whether learners use Authored Depth
> filtering?** Smallest sufficient shape: one new `AnalyticsEventType` value —
> `PUBLIC_LIBRARY_FILTER_APPLIED` — fired once from `applyModalFilters`
> (`public-library-page-client.tsx:1070-1107`), with metadata `{filterType: "authored_depth" |
> "subject" | "course_program" | "tag" | "creator", filterValue: <enum/slug>, resultCount: <int>}`.
> `filterType` is what makes depth's share readable against the other facets rather than in isolation.
> Add `PUBLIC_LIBRARY_VIEWED` via the existing `AnalyticsPageViewTracker` to get a denominator.
> **Deliberately NOT instrumented:** filter-clear, note-open-after-filter, per-chip hover — §17 says do
> not over-instrument, and the question ("is depth filtering used at all, relative to the other
> facets?") is answered by applied-count over viewed-count. **The measurement question this answers, and
> the decision it gates:** whether cross-program depth discovery in the Public Library earns its place
> long-term — the question the `v0.83.0` checkpoint explicitly could **not** answer (§6, §B).

**This is the one place where "we do not know" is the correct current answer, and §6's REJECTION of
Option 2 follows directly from it: you cannot retire a capability whose usage was never measured.**

---

## K. Documentation — where the result and the decision should be recorded

Identification only. **No file was edited by this audit**, and none of these is part of `v0.153.0`.

| File | What to record | Why |
|---|---|---|
| **`docs/product/ROADMAP.md`** — the `v0.83.0 — will curators actually classify…` Backlog row (line 824) | The §B verdict text, **plus the 282/173 correction**, plus the owner's MODIFIED OPTION 1 ruling and a link to this file. Preserve the original assumption, kill criterion, 30-day result and today's re-read. Do **not** rewrite history. | It is the canonical source of truth for this checkpoint and the input to every future kickoff's scope decision. CLAUDE.md's signoff gate is explicit that a stale Backlog row costs a whole release. |
| **`docs/product/ROADMAP.md`** — Backlog Index, new rows | (a) the prevention item (§F); (b) the bounded 80-note cleanup with its `[CHECKPOINT — due F1+14d]` and pre-stated kill criterion (§G); (c) the instrumentation backlog row (§J); (d) an indexing row for **this file**, per kickoff step 8 — every `docs/claude-plans/` file must carry a row. | Step 8 of the kickoff checklist is the only enforced guard against an unindexed planning artifact, and this document is one. |
| **`docs/architecture/ADR-001-canonical-knowledge-architecture.md:64`** | That paragraph states the 80/120 read as of 2026-08-17 and says *"Those 80 notes remain outside every depth-filtered result until curators classify them."* It should record the 30-day outcome and the 282/173 correction as a dated amendment beneath it. | An ADR **outranks** a feature doc where they disagree, so a stale production claim here is the most expensive kind. Note `ADR-001:520-526` is itself a recorded instance of exactly this failure. |
| **`docs/features/public-library.md:287`** | Same stale claim in the same present tense: *"As of the 2026-08-17 production read, 80 of 120 … remain NULL-depth."* Update with the 2026-09-17 re-read and the true 286/1,896 public figure. | CLAUDE.md's surface-sweep rule: a file that *explains* a feature is exactly the file that never changes when the feature does. |
| **`docs/features/notes.md:99-100`** | If F1 ships: record the publication-time warning. `:100` already correctly scopes V117 as a one-time migration and not a runtime fallback — **that line must survive any B2 decision unchanged**. | Per-PR feature-doc rule. |
| **`docs/features/bulk-generation.md:51-58, :86`** | If F1 ships: record the `makePublic` + no-depth warning. `:86` currently records Note Learner Level as *"Optional request"* — still true under a warning, and it is the line that would need changing if the owner ever chose a hard requirement. | Same. |
| **`docs/features/admin-dashboard.md:28, :156`** | If F2 ships: record the missing-depth signal. `:28` currently scopes admin v1 as *"no editing actions beyond … per-note Applicable Programs …"*, which F2 extends. | Same. |
| **`RELEASES.md`** | A bullet per shipped item, in whichever release carries them. | Standing rule. |
| **`CLAUDE.md` / `AGENTS.md`** | **Nothing.** The doctrine in §N is a product lesson for `ROADMAP.md` and `ADR-001`, not a new anti-drift rule, and `AGENTS.md:75` already carries the only binding constraint in this area. | §19: do not overbuild. |

---

## L. Architecture / ADR impact

**No new ADR required.** (Q20.18: NO.)

Every architectural question this decision raises is **already settled in `ADR-001`**, and the owner's
locks restate its existing text rather than amending it:

- **§4's no-inference lock is already ADR-001.** `ADR-001:485` rejects depth on the program
  (*"One program spans several authored depths — Civil Engineering runs Year 1 through Board Review;
  Nursing runs College and Board Review"*); `ADR-001:62` rejects the ~99.3% Course/Program ↔ audience
  correlation as *"not semantic equivalence"*; `ADR-001:68` states *"`STUDENT` spans four authored
  depths … and must not be guessed."* `AGENTS.md:75` adds the independent runtime prohibition:
  *"Target Audience must never become a runtime depth fallback."* **The owner's §4 needs no new
  authority — it needs a citation.**
- **§11's regeneration claim is already ADR-001.** `ADR-001:523`: *"Correcting either durable authoring
  axis shapes future generation only and never touches the existing Study Pack."* Verified against code
  in §D.
- **The axis separation §5 relies on is already ADR-001:32, :40-41, :119, :480.**
- **Nothing in this plan changes architecture.** A warning dialog, an admin column, and 80 metadata
  edits are operational; none moves a boundary, adds an axis, changes a resolution order, or introduces
  a new source of truth.

**What `ADR-001` does owe is a dated amendment, not a new ADR** — `:64` carries a production claim in
the present tense that is now 31 days stale and understated by 3.5× (§K).

**One thing this audit explicitly declines to decide:** whether B2 (the 202, of which 193 are
V117-eligible) warrants a second one-time migration. That is a data decision within `ADR-001`'s existing
framework (V117 established the precedent and the precedent was owner-approved), not an architectural
one — but it is a **production write**, so it is the owner's alone (§N).

---

## M. Implementation routing (per current `CLAUDE.md`)

| Workstream | Nature | Routing | Rationale |
|---|---|---|---|
| **Operational: classify the 80** | Manual curator work, zero code | **Owner / curator.** Not an agent task at all. | The single ADMIN owner does 80 dropdown edits in ~6 Review-Set batches (§G). No prompt, no release. |
| **F1 — publication-time warning** | Frontend-only, two components, well under ~50 LOC, no new infrastructure | **Claude Code inline** | CLAUDE.md routing table: *"Frontend-only additions ≤ ~50 LOC, no new infrastructure … implement directly"*. Two conditional copy blocks in `private-note-detail-page-client.tsx` (the `showMakePublicConfirm` dialog) and `bulk-generation-page-client.tsx` (the `makePublic` row). **Both need a test that actually renders the branch** — CLAUDE.md's unexercised-change rule (`v0.116.0`/`v0.117.0` silent no-ops) applies directly, and both files have existing test siblings. |
| **F2 — admin missing-depth signal** | Backend DTO + service filter + frontend section: multi-system, new response field, touches an admin controller | **Codex — write a prompt first** | CLAUDE.md: *"Multi-system changes (frontend + backend together) → Codex"*. Use `docs/skills/codex-prompt-generator.md`. **Anti-drift instructions the prompt must carry:** scope is one column + one filter on `AdminNoteApplicableProgramsItemResponse` / `admin-applicable-programs-section.tsx` — **no new dashboard, no new route, no notification system, no generic metadata-quality platform** (§16, §19); the query stays scoped to the requester's own notes as `NoteApplicableProgramsService.getAdminPage:82` already does. **If it adds any endpoint, it owes one real `MockMvc` request test with `.contentType(MediaType.APPLICATION_JSON)`** — CLAUDE.md's `v0.119.0` rule, non-negotiable at every verification tier. |
| **F3 — hard publication requirement** | Not scoped. Gated on F1's 30-day inflow re-read. | **Deferred; would be Codex** (breaking API form, two surfaces, owes a deploy-ordering statement) | Do not scope it now. |
| **B2 — the 202 / 193 V117-eligible** | A production write | **OWNER ONLY.** Claude may draft the exact statement into `docs/claude-plans/*.sql` with expected row count and verification query, and stop. | CLAUDE.md production rule, unconditional. §15 forbids guessing values; V117's mapping is not a guess but it is still a write. |
| **Instrumentation (§J)** | Backend enum value + one frontend call site | **Deferred to backlog.** If ever scoped: Codex (enum-first convention + feature doc). | §17: keep it separate from the cleanup. |
| **Docs (§K)** | Doc-only corrections | **Claude Code inline**, on the appropriate release branch | Small documentation-only fixes may go directly on the release branch; still wait for an explicit "commit it". |

**Verification tier for this work, pre-declared:** F1 and the doc updates are **one `advisor()` call**
each — no authorization boundary moves, no money/quota/production-data semantics change, no two PRs
touch the same shared method. F2, if it ships, is **also one `advisor()` call** unless it grows an
endpoint, in which case the real-request test above is mandatory regardless of tier. **No full
three-agent pressure test is warranted** — this release shape does not meet any of its triggers, and
CLAUDE.md is explicit that defaulting to the heaviest option is itself the error.

**Release placement:** none of this belongs in `v0.153.0` (The Missing Telemetry), which is an
unrelated production-reliability release. This is a candidate for a subsequent version and must be
kicked off properly before any implementation, per the standing kickoff rule.

---

## N. Owner checkpoint

```
AUTHORED DEPTH LEGACY CHECKPOINT — FINAL RECOMMENDATION

30-day result:
80 → 80  (re-verified 2026-09-17: still exactly 80)

⚠️ BUT THE DENOMINATOR WAS WRONG:
  282 curator-owned public Notes carry NULL Authored Depth, not 80.
  173 of those were created AFTER the v0.83.0 deploy (2026-08-18 → 2026-09-13).
  The debt did not sit still for 30 days — it more than doubled.
  Split: 80 STUDENT (the checkpoint's population) + 202 BOARD_TAKER (all post-deploy inflow).

Passive backfill assumption:
FAILED — and additionally, the checkpoint could not see the inflow it was competing with.

Authored Depth product concept:
NOT INVALIDATED BY THIS CHECKPOINT
  (and it is NOT discovery-only metadata: it is the quiz/exam curriculum floor
   at StudyPackGenerationContextResolver.java:196-203, calibrates static content at
   OpenAiLlmStudyPackService.java:1585-1626, drives subject-suggestion guidance at :1636-1647,
   and gates reader scaffolding at :1723-1729. A NULL-depth note generates worse Study Packs,
   whether or not anyone ever filters on depth.)

Depth discovery retirement:
NO — and it cannot be decided at all until §J's instrumentation exists.
  Zero filter events exist on the Public Library surface. Confirmed four ways.

Course/Program → Depth inference:
NO. Already forbidden by ADR-001:62, :68, :485 and AGENTS.md:75. No new authority needed.

Learner-facing Unclassified filter:
NO. Current behavior already matches the requirement exactly: NULL-depth notes are fully
  visible unfiltered and excluded only by an explicit depth chip
  (PublicLibraryRepositoryImpl.java:357-362; chips exclude nulls by construction at
  NoteRepository.java:248-255). Nothing to change.

Legacy 80-note cleanup:
EXPLICIT BOUNDED TASK — one named curator (all 80 are owned by a single ADMIN account),
  six Review-Set-shaped batches (51 of 80 sit in four coherent IT Review Sets, 29 in none),
  14-day window, completion = §C Query 1 returns 0, one checkpoint with a pre-stated
  escalation, no indefinite extension.
  ⚠️ SEQUENCING: prevention ships FIRST. Otherwise the completion criterion is invalidated
  while you satisfy it — at the observed inflow rate, ~6 new NULL-depth notes/day.

Bulk editor:
DO NOT BUILD.
  Multi-select UI exists (library/page.tsx:627-629) and an ids-taking endpoint shape exists
  (BulkRegenerateNotesRequest.java:16) — but NO bulk path writes learnerLevel. The only three
  setLearnerLevel call sites on a NoteEntity are NoteService.java:190 (create), :283 (update),
  :383 (copy). Bulk regeneration writes tags and subject only (StudyPackService.java:1397-1406).
  bulk-generate accepts depth but is a CREATE path, not an editor.
  80 notes × one dropdown is a few hours; the editor is a Codex-scale build that removes
  navigation, not judgment.

Public curator Note prevention:
WARNING (B) at the publish seam, plus an ADMIN QUALITY SIGNAL (C) in the existing admin
  Applicable Programs surface. NOT a hard requirement (A), at least not first.
  ⚠️ The reason is concrete, not cautious: NoteBulkGenerationService.java:336-346 wraps its
  updateVisibility call in catch(RuntimeException) → log.warn. A hard throw at the publish seam
  would make a makePublic batch create N notes that silently fail to publish, with a success
  receipt and the failure visible only in server logs. That answers Q17 YES — hard enforcement
  WOULD break a legitimate existing workflow, invisibly.
  Correct seam confirmed: the PRIVATE → PUBLIC transition. Create already forces PRIVATE
  (NoteService.java:194), so publication is the only entry into the Public Library.
  Re-read the inflow 30 days after the warning ships; escalate to (A) only on evidence.

Study Pack regeneration required for metadata cleanup:
NO — confirmed against code, not just against the consultation context.
  NoteService.update:283 writes the field; the only Study Pack touch in that method is a Subject
  sync (:298-302). Depth shapes FUTURE generation only, via resolveForStudyPack
  (StudyPackGenerationContextResolver.java:64-82), which re-reads the note at generation time.
  ADR-001:523 states the same rule. The 80-note cleanup triggers zero regeneration and destroys
  zero Study Packs.

Public Library depth instrumentation:
INFRASTRUCTURE EXISTS (AnalyticsEventType, ~160 values; POST /analytics/events;
  analytics_events table; trackAnalyticsEvent at frontend/lib/api.ts:3348-3358).
  COVERAGE OF THE FILTER SURFACE IS ZERO — no enum value names a filter, the 2,146-line
  public-library-page-client.tsx contains no trackAnalyticsEvent call at all, the surface has no
  AnalyticsPageViewTracker, and the server fires nothing on the browse or learner-levels paths.
  (One exception, stated so this is not overstated: the card-level PUBLIC_NOTE_COPY_CLICKED fires
  from public-library-copy-action.tsx:64-70.)
  RECOMMENDATION: BACKLOG, not part of this cleanup. Smallest shape specified in §J —
  one PUBLIC_LIBRARY_FILTER_APPLIED event with {filterType, filterValue, resultCount}, plus
  PUBLIC_LIBRARY_VIEWED for a denominator. Do NOT introduce an analytics platform.

ADR required:
NO. Every architectural question here is already settled in ADR-001 (:32, :40-41, :62, :68, :119,
  :480, :485, :523) and AGENTS.md:75. This decision is operational.
  ⚠️ ADR-001:64 DOES owe a dated amendment: it states the 80/120 read in the present tense and is
  now 31 days stale and understated by 3.5×. Same for docs/features/public-library.md:287.

Implementation routing:
  • Classify the 80 ......................... OWNER / CURATOR (manual, no code, no release)
  • F1 publication-time warning ............. CLAUDE CODE INLINE (frontend-only, ≤ ~50 LOC,
                                              2 components + a test that renders each branch)
  • F2 admin missing-depth signal ........... CODEX (multi-system; prompt first, via
                                              docs/skills/codex-prompt-generator.md)
  • B2 / the 193 V117-eligible rows ......... OWNER ONLY (production write). Claude may draft the
                                              SQL into docs/claude-plans/*.sql and stop.
  • Instrumentation ......................... BACKLOG (would be Codex; enum-first)
  • Docs (§K) ............................... CLAUDE CODE INLINE
  Verification tier: one advisor() call per workstream. No full pressure test — no trigger fires.
  None of this belongs in v0.153.0; kick off a new version before any implementation.

Core doctrine:

> Passive metadata cleanup is not a migration strategy.

> If new metadata matters to an existing corpus, migrating the existing corpus is part of
  shipping the feature.

> Unknown metadata should remain unknown until a human or trustworthy process establishes it;
  do not manufacture certainty from a different semantic axis.

> Operational failure is not evidence of absent learner demand.

> Clean the bounded historical debt, prevent it from silently regenerating, and measure learner
  behavior separately.

> ⚠️ ADDED BY THIS AUDIT — the lesson the checkpoint itself could not produce:
  A checkpoint scoped to a frozen historical subset cannot see new debt accumulating in the same
  column. Pair every "did the backlog shrink?" read with a "did the inflow stop?" read — and fix
  the inflow before you define done on the backlog, or the completion criterion is invalidated
  while you satisfy it.


OWNER DECISIONS REQUIRED:

1. ⚠️ WHAT HAPPENS TO THE 202 BOARD_TAKER NULL-DEPTH NOTES? (the highest-value decision here)
   This audit will not decide it, and §18's success criteria as written cover neither it nor the
   173. Three options, all owner-executed:
     (a) A one-time migration re-applying V117's ALREADY-RATIFIED, ALREADY-AUDITED rule
         (BOARD_TAKER → BOARD_EXAM_REVIEW, PROFESSIONAL → PROFESSIONAL, behind its existing
         non-licensure-program denylist). Verified today: 193 of the 202 are eligible.
         ⚠️ This is Target Audience → Depth, a DIFFERENT axis pair from the Course/Program → Depth
         inference §4 locks out — §4 is untouched by it — but it IS inference, it IS a production
         write, and AGENTS.md:75 permits it as migration evidence ONLY, never as a runtime
         fallback. Claude may draft the SQL; only you may run it.
     (b) Add them to the manual cleanup, making the total 282 rather than 80. This changes the
         bulk-editor arithmetic in §H and would need a longer window.
     (c) Leave them, and say so in writing. Not recommended — §7 rejected knowingly-retained limbo,
         and this is 2.5× the population that rejection was written about.

2. Is the cleanup's TARGET SET the 80 (recommended, and the only closed set), or 282?
   Follows from decision 1, but state it explicitly so the completion criterion is unambiguous.

3. CONFIRM THE SEQUENCING: prevention (F1) ships before the 80-note cleanup starts.
   This audit recommends it and the 173 are the argument, but it reverses the owner document's
   implied order (§3 before §9), so it is yours to ratify.

4. Approve the PREVENTION SHAPE: warning (B) + admin quality signal (C), with a hard requirement
   (A) deferred behind a 30-day inflow re-read. The swallowed-exception finding at
   NoteBulkGenerationService.java:336-346 is the technical argument; the product call is yours.

5. Approve F2 at all. §16 permits a tiny admin quality signal in an EXISTING surface; this audit
   found the natural host (admin Applicable Programs) but F2 is still a Codex-scale change and is
   the only item here that could be dropped without weakening the rest.

6. Do the 4 learner-owned public NULL-depth notes (286 − 282) fall inside the recurrence-prevention
   invariant, or is the invariant curator-only? §18 says "curator/admin-owned"; this audit reads
   that as curator-only and excludes them, but 2 of them carry the STUDENT audience and are
   invisible to the checkpoint's own query.

7. Should the checkpoint query's curator definition be corrected from u.role='ADMIN' to the
   product's own CuratorAuthoringPredicate (ADMIN or TEACHER, onboarding-complete)? It returns the
   same 282 today, so this is hygiene rather than a live gap — but it is a shorthand that will
   diverge the first time a TEACHER account publishes.


DO NOT IMPLEMENT YET.
```

---

*End of audit. One file written (`docs/claude-plans/authored-depth-legacy-backfill-audit-and-plan.md`).
No other file created or edited. No git command run beyond read-only investigation. Every production
statement issued was a `SELECT` and is reproduced verbatim above with its result.*
