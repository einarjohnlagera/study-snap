# [DISCOVERY] Study Journey Purpose / Learner Goal Architecture

**Status: PARKED.** Discovery-and-record only — nothing below is authorized for implementation. No schema, migration, enum, metadata field, UI, prompt change, recommendation behavior, or release scope.

**Not part of BSCS Year 1. Not part of the Computing Domain Context release.** Neither is blocked by this, and this is not blocked by either.

**The name is intentionally unresolved.** The first pass of this discovery used a single working name (`Learning Goal` / `Preparation Goal`). A review pass found that framing too broad — it conflates two things that may not be the same concept. This revision separates them and is explicitly not claiming either needs its own persistence; see §1.

**Discovered during:** the Computing Domain Context review (`docs/claude-plans/computing-domain-context-final-decision-package.md` and its backing documents) — while reasoning about why canonical computing Notes (e.g. *Binary Search Trees*) might eventually need to serve degree study, interview preparation, certification prep, and professional upskilling without being duplicated or having their Domain Context redefined per use case.

**Premise verified against current code, 2026-10-06** (light audit, not a full architecture pass — see §9): nothing in the persisted model already represents either concept below.

---

## 1. Two concepts, not one — do not conflate them

The original framing — *"why is this learner studying this material?"* — is too broad to be one architectural concept. Separate:

**Study Journey Purpose**
> What is this Study Journey designed to help someone accomplish or prepare for?

Examples that might eventually apply: academic study, licensure preparation, certification preparation, interview preparation, professional development.

**Learner Goal**
> What is this particular learner trying to accomplish?

These may align, but are not inherently identical. Example: a learner may adopt **BS Computer Science — 2nd Year** because they personally want to refresh CS fundamentals before a technical interview. That personal motivation does **not** transform the BSCS academic Study Journey into an Interview Preparation journey. Likewise, an Interview Preparation Study Journey can exist independently of whether every adopter shares the same personal motivation.

**Do not assume both concepts require persistence. Do not assume they require separate persistence either.** The future workstream's first job is determining what concrete product behavior requires each concept — not designing storage for either.

## 2. Start from behavioral gaps, not metadata

The future investigation must **not** begin with *"where should this be stored?"* It must begin with:

> What useful product behavior cannot currently be represented correctly using existing Study Journey identity, structure, description, membership, learner state, and profile?

Collection membership may already express much of the semantic relationship. Example: the canonical Note *Binary Search Trees* could participate unchanged in **BS Computer Science — 2nd Year → Data Structures and Algorithms**, **Software Engineering Interview Preparation → Data Structures**, and **Professional Data Structures Refresher → Trees** — with no goal metadata on the canonical Note at all. That is already a meaningful expression of purpose through journey membership.

**Prefer existing structure + membership over new metadata, unless a concrete behavior requires metadata.** Before proposing a new field, the future workstream must prove what existing Study Journey semantics cannot express.

## 3. Do not overload the canonical Note

Preserve the existing Note architecture. The canonical Note *Binary Search Trees* remains reusable knowledge. Its Subject, Domain Context, Authored Depth / Note Learner Level, and Applicable Programs must not change merely because it participates in academic study, interview preparation, certification preparation, or professional development. **Journey purpose is not Domain Context. Journey purpose is not Authored Depth. Journey purpose is not Applicable Programs. Learner Goal is not any of those either.**

Do not introduce goal information onto canonical Notes without overwhelming evidence that a Note-level behavior genuinely requires it. **Strong prior, to verify rather than treat as an approved decision: purpose belongs to the learning journey more naturally than to canonical knowledge.**

## 4. Reusable knowledge vs. goal-specific knowledge stay distinguishable

Preserve this distinction explicitly. *Binary Search Trees* (reusable canonical knowledge) and *How to Explain Binary Search Trees in a Technical Interview* (goal-specific knowledge) are legitimately different Notes because the object of study differs — the second is not *Binary Search Trees* plus a `goal = INTERVIEW` tag; it teaches interview performance and communication around the concept. Certification-specific material may likewise be legitimately distinct when the knowledge itself concerns certification-specific objectives, exam format, vendor-specific terminology, credential-specific procedures, or scope unique to that credential. The architecture must support canonical reuse without pretending genuinely goal-specific knowledge is identical to the canonical source.

## 5. Do not collapse multiple missing dimensions into one taxonomy

The candidate list may contain more than one dimension. Interview Preparation and Certification Preparation look like Study Journey purposes. But **AWS Certified Solutions Architect** would be a specific *credential target*, and **Software Engineer** could be a target *professional role* — different shapes again. Meanwhile Professional Development and Skills/Competency Development may describe broader learning motivations rather than equivalent preparation targets.

> **Do not force Study Journey Purpose, Learner Goal, Target Credential, Target Role, and general learning motivation into one enum merely because none is fully modeled today.**

The future architecture may need none of them, one of them, or some combination — but evidence must justify each independently. Avoid speculative taxonomy growth.

## 6. Keep Applicable Programs clean

Do not create pseudo-programs such as `Software Engineer Interview`, `AWS Certification`, `Job Interview`, or `Professional Upskilling`. Applicable Programs continues to mean legitimate academic/program applicability of canonical knowledge — do not turn Course/Program into a catch-all discovery taxonomy. If future evidence shows roles or credentials need first-class representation, evaluate them separately; do not assume `Applicable Roles` or `Credentials` are required merely because they can be imagined.

## 7. Relates to, but must not couple with, the Learning Journey architecture

Current product direction: Study Plans define the journey; learning evidence tells NoteLib where the learner is; deterministic product logic chooses the next useful action; Companion helps the learner understand the journey, the knowledge, and why the action matters; external retrieval is used only when an answer depends on current/outside-world facts.

Any future Study Journey Purpose / Learner Goal concept must respect those boundaries. **Do not assume Journey Purpose automatically** changes next-action resolution, ConceptHealth, readiness, quiz behavior, Study Pack generation, Domain Context, Authored Depth, canonical Note content, or creates a new recommendation engine.

> Journey structure determines what is learned. Learning evidence determines learner state. Journey purpose may influence presentation or purpose-specific capabilities **only where a concrete product behavior requires it.**

The future workstream identifies those behaviors; it does not assume them.

## 8. Generation boundary

Keep the strong prior: **journey purpose probably should not enter ordinary Study Pack generation for reused canonical knowledge.** *Binary Search Trees* should not silently become differently authored simply because the same Note is reached through BSCS, interview preparation, a professional refresher, or certification preparation.

If a desired learning artifact genuinely needs different treatment, investigate whether that is (A) genuinely different canonical knowledge, (B) a purpose-specific learning/practice layer, (C) a Companion/tutoring behavior, (D) a specialized assessment mode/sub-mode already supported by current architecture, or (E) something else. **Do not automatically add Journey Purpose to the generation prompt.**

## 9. ProfileType remains orthogonal — verified against current code, 2026-10-06

`ProfileType` (`STUDENT`, `BOARD_EXAM`, `TEACHER`, `PARENT`, `PROFESSIONAL`) answers *who is this learner / what broad product experience fits them* — a viewer/account attribute. Study Journey Purpose answers *what is this journey designed for*. Learner Goal answers *what is this learner trying to accomplish*. These are different axes: a `PROFESSIONAL` learner might simultaneously pursue a certification journey, an interview-prep journey, a professional refresher, and an academic Study Journey; a `STUDENT` could use an interview-prep journey.

**Confirmed directly against the entity source (no `goal`/`purpose`/`intent`-shaped field exists on `NoteEntity` or `NoteCollectionEntity`)**, and confirmed that `getCollectionLabels(profileType)` keys terminology only by viewer profile (`STUDENT` → "Study Plan"/"Subject Plan", `BOARD_EXAM` → "Review Set", `TEACHER` → "Course"/"Unit"), never by journey purpose — consistent with this being genuinely unmodeled today, not merely assumed. **`PROFESSIONAL` already exists as an unused `ProfileType` stub** (no feature behind it, per this repo's own documentation) — this is relevant historical evidence that the professional-learning use case has been anticipated before, but it is **not** proof that journey purpose belongs on `ProfileType`. Do not overload `ProfileType` to solve journey purpose.

## 10. Cross-domain pressure tests (for the future workstream to run, not run here)

**Before any future model is approved**, it must be pressure-tested outside computing. At minimum:

| Domain | Canonical Note example | Possible journeys |
|---|---|---|
| Computing | *Binary Search Trees* | BS Computer Science; Technical Interview Preparation; Professional Refresher; a certification-related journey where legitimately relevant |
| Nursing | *Fluid and Electrolyte Balance* | Nursing degree study; PNLE preparation; clinical refresher / continuing professional learning |
| Accountancy | *Financial Ratio Analysis* | Accountancy academic study; CPALE preparation; professional finance/accounting refresher |
| Architecture / Civil Engineering | Building-code-related knowledge | Academic study; licensure review; professional refresher |
| Education | *Assessment and Evaluation* | Education degree; LET preparation; teacher professional development |

For each: is canonical knowledge still genuinely reusable? Does journey membership already express enough? What concrete behavior would additional purpose metadata enable? Is the proposed concept domain-neutral, or are we accidentally modeling an exam-specific assumption as universal architecture?

## 11. Open questions, in order

1. What concrete user/product problem are we solving?
2. What behavior is impossible or incorrect with today's Study Journey identity, description, structure, collection membership, `ProfileType`, and learner state?
3. Is the missing concept Study Journey Purpose, Learner Goal, a target credential, a target professional role, some other concept, or no new persisted concept at all?
4. At what grain does each demonstrated behavior belong: canonical Note, Study Journey, a learner's adoption of a Study Journey, learner profile/preferences, or another existing object?
5. Can one Study Journey legitimately serve learners with different personal goals?
6. Can one learner simultaneously pursue journeys with different purposes?
7. Does any purpose need first-class discovery/filtering?
8. Does any purpose actually alter learning behavior, or only discovery/presentation?
9. Does purpose ever need to reach generation? **Strong prior: not for ordinary reused canonical knowledge.**
10. Are specific credentials or roles genuinely required as entities/taxonomies? Do not infer yes from hypothetical examples.
11. Can existing Study Journey membership solve the use case without new metadata?
12. What is the minimum model that solves demonstrated cross-domain use cases?

## 12. Historical framing — not a defect

This is not a defect requiring migration. Earlier NoteLib journeys primarily represented board/licensure review and academic/program study — the product never needed to distinguish these concepts more formally, because every journey had effectively the same implicit purpose. Expansion into Degree Study Journeys, professional learning, computing, interview preparation, and possible certification learning surfaced the conceptual distinction. **Existing ALE, LET, PNLE, CPALE, Civil Engineering, Degree Study Journeys, and canonical Notes must not be redesigned merely because the distinction has now been named. No backfill is authorized.**

## 13. Future success criterion

> A successful future architecture lets canonical knowledge remain canonical while participating in Study Journeys designed for different purposes, without corrupting Domain Context, Authored Depth, Applicable Programs, or Note identity.

It must additionally: distinguish the purpose of a Study Journey from a learner's personal goal where that distinction materially matters; keep genuinely purpose-specific knowledge distinguishable from reusable canonical knowledge; avoid collapsing journey purpose, credentials, professional roles, and learner motivation into one speculative taxonomy; and introduce no new persisted concept unless a concrete product behavior cannot be expressed correctly with the Study Journey and learner-state architecture NoteLib already has.

## 14. Status remains parked

After this tightening: **stop.** Do not implement, create an enum, create a schema field, create a migration, change Study Pack prompts, change Domain Context, change Applicable Programs, change `ProfileType`, change recommendations, change Companion, change Explore, backfill existing Study Plans, add certification or role catalogs, or create a release. This remains a parked architecture discovery — its purpose is to preserve the problem clearly enough that a future workstream starts from the correct conceptual boundaries rather than inventing a premature metadata solution.
