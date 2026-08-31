# Domain Context taxonomy calibration — Stage 1 audit

**Run 2026-08-31** by two cold-context agents on non-overlapping halves (implementation reality;
taxonomy judgment), commissioned by the owner after hitting Architecture notes with no fitting
Domain Context while building the ALE Review Set.

**⚠️ READ THIS BEFORE PROPOSING ANY DOMAIN CONTEXT CHANGE.** It exists because the agents' findings
are not recorded anywhere else, and because the owner intends to continue this work in a new session.

---

## VERDICT: no taxonomy change is warranted. `ARCHITECTURE` is NOT added.

**⚠️ The reason is NOT that it failed the bar. It PASSED the bar and is still a no-op — and that
distinction is the whole finding.** Anyone re-reading this who takes away "Architecture didn't
qualify" has taken away the wrong thing and will re-open it.

### What it passed

- **Governance floor (`ADR-001`):** ~143 planned Architecture-only rows (Architectural Design 57,
  History 33, Site Planning 20, Urban Planning 14, Theory 11, Conservation 4, Landscape 4) against a
  "~10 notes authored or firmly planned" bar.
- **The exclusion trap** — name notes from the SAME program that should NOT use it: **173 of 364 ALE
  rows.** Building Utilities 41, Building Technology 31, Structural Concepts 48, Architectural
  Practice 53. So it is not a program default in costume.
- **A real tradition exists.** Adjacency and bubble diagrams, proxemics and territoriality, form/
  space/order, adaptive reuse. Not engineering-science material; `GENERAL_EDUCATION` would be a
  category error.

### Why it dies anyway — the mechanism

**A Domain Context value's ENTIRE generation payload is two things:**

1. the **label string**, interpolated by `OpenAiLlmStudyPackService.buildGenerationContextBlock` as
   `Domain: {label}` plus a fixed `DOMAIN_CONSTRAINT` line — there is **no per-value instruction
   body, no prompt fragment, no description that reaches the model**; and
2. the **`quantitative` boolean**, where `true` short-circuits `isQuantitativeContext` and `false` is
   a **no-op** falling through to the untouched keyword scan.

`StudyPackGenerationContextResolver.resolveCourseProgram` falls back to the single joined catalog
program name. **So a single-program Architecture note ALREADY sends `Domain: Architecture` today.**
An `ARCHITECTURE("Architecture", false)` value would emit a **byte-identical** line and short-circuit
to the **identical** scan.

**Zero generation delta, provably.** Expansion-test criterion 3 ("the coarser context would
materially weaken…") cannot even be evaluated in its intended form: there is no coarser value to
compare against. The real comparison is *a new enum value vs. a fallback emitting the same string*.

### ⚠️ The strongest argument AGAINST this verdict — recorded at full strength

Do not re-derive this; it was considered and answered.

`note_course_program` is many-to-many, and `ADR-001` makes catalog growth cheap and demand-driven.
**Interior Design** would legitimately consume Architectural Design, Theory, Human Factors and
Conservation notes; **Environmental Planning** would consume the whole Site Planning and Urban
Planning families. Neither is in the catalog today.

The moment either is added, **every one of those ~143 notes tagged to a second program becomes
illegal with NULL** — `NoteApplicableProgramsService` rejects a null context on 2+ programs — and has
no fitting value to move to. Domain Context is permanent per note and Study Packs never
auto-regenerate, so the cost lands as a bulk classification exercise on a single curator against a
~100-notes/month ceiling, instead of a one-line enum addition today while the notes are being
authored anyway. **And because it is a no-op, adding it now is free.**

**Why it still loses:** the promotion path stays open — `ARCHITECTURE` becomes a one-line addition
*at that moment, with real evidence*. NULL is queryable, so the backlog is observable rather than
forgotten. "Free optionality" is precisely how a closed vocabulary drifts back toward free text, and
`ADR-001`'s failure condition is a context count trending toward the program count. **The correct
ratio is 8:21** — see the baseline correction below.

---

## What the owner is ACTUALLY hitting — two mechanisms, neither fixed by a new value

### (a) Computation guidance is silently OFF for ~41 Building Utilities notes — a live defect

`QUANTITATIVE_KEYWORDS` contains no `architecture` / `architectural`. For an unset Building Utilities
note the haystack is `Architecture` + `Building Utilities` + tags. *"Air-Conditioning Systems," "Room
Acoustics," "Building Water Supply Systems," "Vertical Transportation Systems," "Automatic Sprinkler
Systems," "Lighting Fundamentals"* trip **nothing**. Only the `Electrical…` titles rescue themselves.

**This is content whose substance is sizing calculation** — pipe and duct sizing, load and
illumination computation, sprinkler hydraulics, lift traffic analysis — **generated with no
computation guidance.** Same class as the `v0.85.0` defect.

**⚠️ The remedy is CLASSIFICATION, not vocabulary.** `ENGINEERING_SCIENCES` is `quantitative=true`
and is the coarsest fitting value. Precedent is already ratified: `ADR-001`'s 2026-08-29 worked
example rules *Water Supply Engineering* → `ENGINEERING_SCIENCES` on the binary test, and *"Building
Water Supply Systems"* is the same family. Plumbing is applied hydraulics; HVAC is applied
thermodynamics — both already named in that value.

### (b) The multi-program landmine is what "no context fits" feels like from the authoring seat

`review-set-workbook-spec.md` already names it: an unset row *"is a landmine on any shared note."*
The moment a note is tagged for a second program, unset is rejected. For Building Utilities and
Building Technology — the families most likely to also be tagged CE/EE/ME — **a value already
exists**; the block is classification, not vocabulary.

---

## ⚠️ The curator UI is what makes (a) hard to fix by classification alone

`frontend/lib/domain-context.ts` describes `ENGINEERING_SCIENCES` as *"Strength of Materials,
Engineering Mechanics, Hydraulics or Fluid Mechanics, Thermodynamics, and Engineering Materials."*

Plumbing, HVAC, electrical distribution, lighting, acoustics, fire protection, vertical transport and
building automation **are not in that list.** The description is a **Civil-Engineering-flavoured
enumeration of a broader ratified value**, and it is under-inclusive against the value's own name and
against the water-cases ruling. **A curator reading it would not pick `ENGINEERING_SCIENCES` for
HVAC** — which is exactly why the notes are unset.

`CIVIL_ENGINEERING`'s description is wrong in the other direction: it claims *Surveying* and
*Construction Management*, which production practice places elsewhere (7 Construction Materials and 6
Construction/Project Management notes carry `ENGINEERING_SCIENCES` and cross CE → Architecture
**unchanged**).

---

## ⚠️ The ALE plan's 215 `(unset)` rows are NOT evidence

`docs/gpt-contexts/REVIEW_SET_SHAPING_CONTEXT.md` instructs the strategist: *"Some programs
(**Architecture, notably**) deliberately have no Domain Context and rely on this fallback."*

**The answer was pre-committed before the plan was produced.** The 215 unset rows are that
instruction reproduced — evidence neither of insufficiency nor of sufficiency. **This pre-empts the
exact question `[CHECKPOINT — due 2026-09-28]` exists to ask.**

**The load-bearing evidence is the 47 `Reuse` rows instead** — notes authored for Civil Engineering
and reused *unchanged* in Architecture. That is `ADR-001`'s binary test passing in production data,
independent of any strategist judgment:

| Reused family | Domain Context | Rows |
|---|---|---|
| National Building Code / BP 344 / Construction Safety / Prof Practice | `PPR` | 19 |
| Construction Materials and Testing | `ENGINEERING_SCIENCES` | 7 |
| Project + Construction Management | `ENGINEERING_SCIENCES` | 6 |
| Construction Cost Engineering | `ENGINEERING_MATHEMATICS` | 6 |
| Foundation Engineering | `CIVIL_ENGINEERING` | 8 |
| Structural Components | `ENGINEERING_SCIENCES` | 1 |

---

## Existing-value review

| Value | Verdict | Note |
|---|---|---|
| `ENGINEERING_MATHEMATICS` | **Keep** | Active; the ADR's founding case. Correctly used for Construction Cost Engineering — that **teaches** the computational method, so it is the right side of the "uses vs teaches mathematics" line |
| `ENGINEERING_SCIENCES` | **Keep, widen its description** | The workhorse; R4 was run on it and passed |
| `CIVIL_ENGINEERING` | **Clarify** | Evidence of **over-selection**: 8 Foundation Engineering notes carry it yet cross into Architecture unchanged, which the binary test places in the shared bundle. A selection-guidance case, **not a removal case**. ⚠️ Do not read the 8 `Excluded` rows as counter-evidence — exclusion from ALE is a Review Set scope decision, not a treatment difference |
| `PROFESSIONAL_PRACTICE_AND_REGULATION` | **Keep** | Strongest non-engineering value: 77 of 364 ALE rows, 19 reused cross-program |
| `GENERAL_EDUCATION` | **Keep** | Senior High strands correctly keep NULL so the strand name reaches the prompt |
| `PROFESSIONAL_EDUCATION` | **Insufficient evidence** | Zero usage |
| `NURSING` | **Insufficient evidence** | Zero usage against 132 notes on fallback |
| `ACCOUNTANCY` | **Insufficient evidence** | Zero usage against 154 notes on fallback |

**⚠️ ON THE THREE ZEROS — DO NOT RESOLVE BY REASONING.** `ADR-001` records the usage observation as
**UNRESOLVED EVIDENCE** with two competing explanations preserved (authoring order vs. wrongly-shaped
values), precisely so `[CHECKPOINT — due 2026-09-28]` stays falsifiable. The Architecture rebuild is
now a **third** program authored without reaching any of them — that is a data point **for** the read,
not a resolution of it. **No removal is recommended in any case**: removing a value could lock
existing multi-program rows, since a null context is rejected there.

## Naming: mixed altitude is correct

Because a value's whole payload is its label string, naming reduces to *which string best instructs
the model* — an empirical question, not an ontological one.

- **Borrow real curriculum vocabulary; never invent.** Both invented candidates — `Health Sciences
  Foundation` and `Computing` — were already rejected for failing the learner-comprehension test.
  `Built Environment` / `Health Sciences` are the same shape: plausible groupings nobody teaches
  under, instructing the model toward no particular treatment. Same objection that killed
  `GENERAL_ENGINEERING`.
- **A label equal to a catalog program name is a no-op for single-program notes by construction**, so
  it must justify itself entirely on the multi-program case. (This is the `ARCHITECTURE` finding,
  generalised — it is the most useful naming rule available.)
- **Program-shaped is fine when the name IS the board's subject-area vocabulary.** `Nursing`,
  `Accountancy`, `Civil Engineering` are PRC board subject-area names. The failure mode is not
  program-shape; it is minting one per catalog row.

### The falsifiable successor hypothesis — recorded, NOT proposed

If a future evaluation finds **MEP / building-services** material consistently mis-framed under
`ENGINEERING_SCIENCES` — accurate but generic in terminology, conventions and scope, which is exactly
the Water Treatment calibration shape — then **`Building Services` / `Building Systems`** is the value
to examine: a real, taught, cross-program tradition serving Architecture, CE, EE, ME and Sanitary
alike. **Below the bar today on the same mechanism argument, and `ENGINEERING_SCIENCES` has not been
tried on that family yet.** ⚠️ Do not write a second rubric — R4's runbook
(`docs/claude-prompt/canonical-knowledge-architecture-out/17-r4-verification-runbook.md`) is the one
that exists.

---

## Corrections to standing docs

- **`ADR-001`'s "27+ programs" baseline is wrong for the purpose it serves.** That figure is the
  **pre-catalog free-text spread** (32 distinct values at ratification), not catalog rows. The catalog
  seeds **21**. The ADR's failure-condition ratio depends on this denominator: it is **8:21**.
- **`REVIEW_SET_SHAPING_CONTEXT.md`** pre-commits the Architecture answer (see above).
- **`domain-context.ts`** descriptions for `ENGINEERING_SCIENCES` (under-inclusive) and
  `CIVIL_ENGINEERING` (claims Surveying / Construction Management).
- **`docs/features/notes.md`** says Domain Context is *"visible only to Teacher/Admin authors in the
  product UI"* — true of the UI, but the **API returns it on learner-facing and public payloads**
  (`NoteListItemResponse`, `PublicProfileNoteResponse`, `PublicLibraryRepositoryImpl`). Not secret,
  but not curator-scoped at the transport layer either.

## Implementation defects found incidentally

- **`Automatic — based on the program` is accurate for ONE of five operative cases.** Worst: on a
  newly-created curator note with zero Applicable Programs and no Domain Context,
  `note.course_program` is null by construction, so **the domain constraint sent to the LLM is the
  curator's own profile program** — invisible in the UI, and different between two curators
  generating byte-identical notes. The prompt emits a byte-identical `Domain:` line whether the value
  is a curated enum label or a free-typed personal string, and asserts over it *"the authoritative
  academic domain."*
- **`isQuantitativeContext_preservesEveryPreviouslyQuantitativeEngineeringDomain` is VACUOUS.**
  Flipping `quantitative` to `false` on all three engineering values leaves it green, because three
  of the eight **labels self-trigger the keyword scan** the flag was built to replace. The declared
  flag is only independently observable for `NURSING` and `ACCOUNTANCY`.
- **The quantitative signal is not stable across prompts for one note** — the seven call sites pass
  different haystacks, so the same note can be quantitative for a Challenge Quiz and not for its
  Study Pack.
- **Zero real-row coverage** for the three predicates that decide Domain Context behaviour, one of
  which (`NoteCourseProgramShadowing.isShadowed`) `ADR-001` records as having already shipped
  inverted once, *"propagating it into four documents and a test that asserted it as correct."*

---

## Production reads — READ-ONLY, NOT YET RUN

Uses the canonical curator predicate (`u.role = 'ADMIN' AND n.visibility = 'PUBLIC'`) so results are
comparable to the recorded **32.6% (370/1,135)** rather than introducing a fourth denominator.
**⚠️ Do not cite 12.7% or "4 of 8 values" — superseded 2026-08-17 figures.**

```sql
-- Q1. Distribution across curator-owned public notes, NULL counted explicitly.
SELECT coalesce(n.domain_context, '(NULL — unclassified)') AS domain_context,
       count(*) AS notes,
       round(100.0 * count(*) / sum(count(*)) OVER (), 1) AS pct_of_catalog
FROM notes n JOIN users u ON u.id = n.owner_user_id
WHERE u.role = 'ADMIN' AND n.visibility = 'PUBLIC'
GROUP BY 1 ORDER BY notes DESC;

-- Q2. Classification rate and value coverage.
SELECT count(*) AS public_curator_notes,
       count(*) FILTER (WHERE n.domain_context IS NOT NULL) AS classified,
       count(*) FILTER (WHERE n.domain_context IS NULL) AS unclassified,
       round(100.0 * count(*) FILTER (WHERE n.domain_context IS NOT NULL)
             / nullif(count(*), 0), 1) AS pct_classified,
       count(DISTINCT n.domain_context) AS distinct_values_in_use,
       8 - count(DISTINCT n.domain_context) AS values_never_used
FROM notes n JOIN users u ON u.id = n.owner_user_id
WHERE u.role = 'ADMIN' AND n.visibility = 'PUBLIC';

-- Q3. Unclassified grouped by legacy program string — the promotion backlog.
-- ⚠️ High School / Grade School / Senior High strands appear here BY DESIGN (ADR-001 declined
--    classifications). They are NOT demand. The ~10-note floor is NECESSARY, NOT SUFFICIENT —
--    the shared-treatment clause decides. Read as "candidates to examine", never "domains to create".
SELECT coalesce(n.course_program, '(no program string)') AS course_program,
       count(*) AS unclassified_notes, count(DISTINCT n.subject) AS distinct_subjects
FROM notes n JOIN users u ON u.id = n.owner_user_id
WHERE u.role = 'ADMIN' AND n.visibility = 'PUBLIC' AND n.domain_context IS NULL
GROUP BY 1 ORDER BY unclassified_notes DESC;

-- Q3b. Same backlog keyed on the JOINED catalog program — what the resolver actually reads.
SELECT coalesce(cp.name, '(no joined program)') AS joined_program,
       count(DISTINCT n.id) AS unclassified_notes
FROM notes n JOIN users u ON u.id = n.owner_user_id
LEFT JOIN note_course_program ncp ON ncp.note_id = n.id
LEFT JOIN course_programs cp ON cp.id = ncp.course_program_id
WHERE u.role = 'ADMIN' AND n.visibility = 'PUBLIC' AND n.domain_context IS NULL
GROUP BY 1 ORDER BY unclassified_notes DESC;

-- Q4. Up to 5 representative notes per value in use.
SELECT domain_context, subject, title, learner_level, course_program FROM (
  SELECT n.domain_context, n.subject, n.title, n.learner_level, n.course_program,
         row_number() OVER (PARTITION BY n.domain_context ORDER BY n.updated_at DESC) AS rn
  FROM notes n JOIN users u ON u.id = n.owner_user_id
  WHERE u.role = 'ADMIN' AND n.visibility = 'PUBLIC' AND n.domain_context IS NOT NULL
) ranked WHERE rn <= 5 ORDER BY domain_context, rn;

-- Q5. ⚠️ RUN THIS ONE FIRST. Sizes the profile-fallback problem: how many curator notes resolve
-- their prompt Domain from a legacy string or the AUTHOR'S ACCOUNT rather than the note.
SELECT CASE
         WHEN n.domain_context IS NOT NULL         THEN '1. Domain Context label'
         WHEN j.join_rows = 1                      THEN '2. single joined program name'
         WHEN coalesce(n.course_program, '') <> '' THEN '3. note legacy course_program string'
         ELSE '4. author profile course_program (or NO Domain line at all)'
       END AS effective_authoring_domain_source,
       count(*) AS notes
FROM notes n JOIN users u ON u.id = n.owner_user_id
LEFT JOIN LATERAL (
  SELECT count(*) AS join_rows FROM note_course_program ncp WHERE ncp.note_id = n.id
) j ON true
WHERE u.role = 'ADMIN' AND n.visibility = 'PUBLIC'
GROUP BY 1 ORDER BY 1;

-- Q6. Invariant check. A NON-ZERO result means a write path regressed — a live bug, not legacy data.
SELECT n.id, n.title, count(ncp.course_program_id) AS program_count
FROM notes n JOIN note_course_program ncp ON ncp.note_id = n.id
WHERE n.domain_context IS NULL
GROUP BY n.id, n.title HAVING count(ncp.course_program_id) > 1
ORDER BY program_count DESC;
```

---

## Recommended actions — NONE is a taxonomy change

| # | Action | Kind | Blocks ALE? |
|---|---|---|---|
| 1 | Classify Building Utilities (41) + Building Technology (31) as `ENGINEERING_SCIENCES` | **Curator work — no release needed** | — |
| 2 | Widen `ENGINEERING_SCIENCES`'s description; correct `CIVIL_ENGINEERING`'s | Frontend copy | **YES — action 1 is not discoverable without it** |
| 3 | Re-word `REVIEW_SET_SHAPING_CONTEXT.md` — state the rule, drop the worked answer | Docs | **YES — it pre-commits the strategist** |
| 4 | Correct `ADR-001`'s program baseline to 21 | Docs | No |
| 5 | Record `Building Services` as the successor hypothesis for `2026-09-28` | Docs | No |

**⚠️ NONE OF 1–5 PRE-EMPTS `[CHECKPOINT — due 2026-09-28]`.** Classifying under existing values is not
a taxonomy action, and `ADR-001` states the multi-program rule **is itself the forcing function**
generating the calibration evidence. Whether the classified notes then generate well **feeds** the
read: good output confirms the vocabulary, consistently mis-framed output promotes `Building Services`.

## ⚠️ Anti-drift for whoever picks this up

- **Do NOT add `ARCHITECTURE`.** It passed the bar and is a provable no-op. Re-deriving this costs a
  release and changes nothing.
- **Do NOT add `GENERAL_ENGINEERING`** or any catch-all. An honest NULL beats a false classification.
- **Do NOT extend `QUANTITATIVE_KEYWORDS`** — that restores the guess the declared flag replaced.
- **Do NOT create one Domain Context per Course/Program.** Ratio watched at every kickoff: **8:21**.
- **Do NOT remove `NURSING`, `ACCOUNTANCY` or `PROFESSIONAL_EDUCATION`** on zero usage — the
  explanation is deliberately unresolved, and removal could lock existing multi-program rows.
- **Do NOT resolve the zero-usage question by reasoning.** It is what the checkpoint reads.
- **Do NOT bulk-rewrite notes or retroactively regenerate Study Packs.** Domain Context changes affect
  **future generation only**; classify during meaningful review. Curator time is the binding constraint.
- **Do NOT invent a second evaluation rubric** — R4's runbook exists.


---

# Stage 2 — owner/product decisions, 2026-08-31

The Stage 1 findings above went to the product-UX consultation and came back as **13 ratified
decisions**. They **accept** the audit: no taxonomy expansion, no `ARCHITECTURE`, no catch-all, no
removals, and the shipped description corrections stand. **The work is reframed as resolver + UX +
mental model.** Full decision text is in the owner's consultation record; the operative summary:

| # | Decision |
|---|---|
| 1 | Replace `Automatic — based on the program` with **`Automatic — use note context`**. NOT "infer from context" (implies AI classification the resolver does not do); NOT "Not set" (Automatic is not a no-op) |
| 2 | **Show the EFFECTIVE writing domain** as derived UI state (`Writing domain: Architecture`), or `Writing domain needs attention`. **No new persisted field.** Must reuse the backend resolver, never a frontend re-implementation that can drift |
| 3 | Applicable Programs helper copy states the boundary: *they determine where this note applies and is discoverable; they do not determine its Domain Context* |
| 4 | **Canonical curated generation must NOT silently inherit the curator's profile program.** Invariant: *two curators generating the same canonical note must not get different authoring-domain instructions because their personal profiles differ.* **Learner personalization is NOT removed** — audit the boundary first |
| 5 | Domain Context stays **OPTIONAL** where Automatic resolves trustworthily. Forcing a choice produces plausible-but-wrong values, which are worse than an honest blank because they are invisible |
| 6 | **Block ambiguity at GENERATION, not at note save.** *Note validity ≠ generation readiness* |
| 7 | Preserve learner behaviour; report rather than invent a brittle role check if no clean canonical-generation concept exists |
| 8 | **New doctrine criterion:** *a missing authoring tradition does not justify a new enum when existing resolution already produces the same effective context.* Architecture is the worked example |
| 9 | The September checkpoint must measure **effective** domains and their **source**, not just persisted enum usage |
| 10 | Four named patterns (A authoring-order / B wrong-shape / C enum-redundancy / D avoidance) to distinguish the zero-usage explanations |
| 11 | **Override behaviour is the strongest signal:** when Automatic produces a domain, does the curator accept or change it? |
| 12 | The UI shows the **outcome**, never the resolver's five precedence levels |
| 13 | Keep the shipped description fixes; audit the other values' descriptions for the same narrowing failure |

## ⚠️ Three conflicts/couplings found against real code — flag these before implementing

**1. Decisions 1 and 4 are COUPLED. Shipping 1 without 4 makes the label wrong again, in a new way.**
`Automatic — use note context` is only truthful once the **profile fallback is removed for curated
generation** — until then, "note context" can still be the curator's own account setting, which is
not note context at all. **Do not ship the copy change alone.** (The label is curator-only —
`showAuthoringMetadataFields` / `canEditAuthoringMetadata` / `isTeacherOrAdmin` — so it need not
describe the learner path, where profile fallback legitimately survives.)

**2. Decision 6 COLLIDES with an existing save-time block, and the decisions do not address it.**
`NoteService:181` already calls `assertMultiProgramHasDomainContext` on create, and
`NoteApplicableProgramsService:74-76` throws `MultiProgramDomainContextRequiredException` — so a note
with **2+ Applicable Programs and no Domain Context is ALREADY hard-blocked at save**, in four
independent write paths. Decision 6 says not to hard-block save. **Either that rule is an explicit
exception to Decision 6** (defensible: multi-program is a data-integrity rule, because the resolver
genuinely cannot choose between programs, whereas unresolved-single is a generation-readiness
question) **or it moves to generation time too** — which is a larger change touching four call sites
and an existing error contract. **This needs an owner ruling; do not decide it in implementation.**

**3. Decision 4's boundary ALREADY EXISTS — no brittle role check needs inventing.** Decision 7 asked
this be reported rather than guessed. `isTeacherSelectableOwner` (`NoteService:1524`) and `isCurator`
(`NoteGenerationService:116`, `NoteBulkGenerationService:411`) are the same predicate —
`TEACHER || ADMIN`, **preceded by an onboarding guard** — and it is **already used for exactly this
field**: `NoteService:182` writes `entity.setCourseProgram(curator ? null : resolveRequested…)`. So
the codebase already decides "curator notes carry no personal program" at creation; Decision 4
extends that same rule to resolution. **The `ADMIN`-alone concern is moot** — the predicate is not
`ADMIN` alone, and it is documented with its `v0.71.0` rationale.

## ⚠️ Decision 11 is NOT observable from current data — this bounds Decisions 9–11

Domain Context authoring emits **zero analytics events** (verified at Stage 1). Nothing records what
Automatic *would have* produced at the moment a curator chose, so **"did the curator accept or
override Automatic?" cannot be reconstructed retroactively at all.**

- **Patterns A, C and D are partly reconstructable now** by SQL — Q5 in this document already
  classifies every curator note by its effective-domain SOURCE, which is Decision 9's item 3.
- **Pattern B and Decision 11 require instrumentation**, and it must exist *before* the behaviour it
  measures occurs.

**⚠️ The September read is 28 days out.** If override behaviour is wanted for it, the smallest
honest addition is a single event at authoring capturing *(what Automatic resolved, what was
persisted)* — proportionate, and consistent with Decision 9's "do not build a large analytics
system". **If that is not added, Decisions 10-B and 11 are simply not answerable this cycle, and the
read should say so rather than infer.**

## Placement

**This is a release, not a fold.** It changes resolver behaviour on the generation path (Decision 4),
adds a derived API value (Decision 2), adds a generation-time gate (Decision 6), and touches curator
copy (1, 3, 12, 13). `v0.99.0` is feature-complete with a different theme.

**Verification tier, provisionally:** Decision 4 changes what reaches the LLM on the canonical
authoring path, and Decision 6 adds a gate — **one scoped cold agent** at minimum; re-evaluate if the
Decision 6 ruling widens to moving the existing save-block.

**⚠️ The A–F implementation audit the decisions request has NOT been run.** Sections A (resolver
boundary — partly answered by conflict 3 above), B (effective-domain exposure), C (generation
readiness), D (exact copy), E (observability — partly answered above) and F (tests) are owed before
coding.
