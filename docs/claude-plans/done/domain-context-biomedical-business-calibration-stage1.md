# Domain Context Taxonomy Calibration — Biomedical/Clinical + Business/Finance — Stage 1 Audit

**Status: Stage 1 audit only. Nothing implemented. No enum value added, no migration, no Note
modified, no prompt changed, no catalog change, no commit.**
**Date: 2026-09-13. Repo state: branch `releases/v0.144.0`, last closed release `v0.143.0`.**
**Production reads: READ-ONLY `SELECT` only, per `CLAUDE.md`'s production rule. Every
production-sourced fact below is labelled `[PROD 2026-09-13]` and kept separate from repository
facts.**

---

## 0. How this audit was performed

Every claim about current behaviour was made by opening the file and reading the cited lines. Where a
document and the code disagree, the code is reported and the document is named as stale. Production
numbers are reproducible from the queries described inline.

**Two limitations declared up front:**

1. **`PNLE_COMPREHENSIVE_REVIEW_ARCHITECTURE_2025_TOS.xlsx` was not opened** (binary workbook). The
   PNLE evidence below comes from `docs/curriculum/pnle-comprehensive-review.tsv`, which is the
   committed source-of-truth row set the workbook is *generated from* (`docs/curriculum/README.md`
   line 11: *"Edit this, then regenerate — never hand-edit the workbook"*). That is the correct
   source, but no board blueprint was consulted, and this audit makes **no claim of PRC blueprint
   fidelity** — the same limit `v0.111.0` declared for its own RA 9266 provenance claim
   (`v0.111.0-phase-2-taxonomy-calibration.md:159-161`).
2. **No CPALE review-set plan exists in the repo to read.** `docs/curriculum/` holds `ale-`,
   `civil-engineering-`, `let-` and `pnle-` TSVs and **no `cpale-` file**. The CPALE evidence below is
   therefore production content only, with no forward plan — and that asymmetry is itself one of this
   audit's findings, not an oversight in the reading.

---

## 1. Executive judgment — the brief's premises, corrected

Lead finding first, as the sibling audits do.

| # | Brief's premise | Verdict | Evidence |
|---|---|---|---|
| P1 | "Using NURSING as Domain Context would **potentially** bias a canonical shared Pharmacology Note" | **TRUE, and it is not potential — it is live and reproducible.** | `Antibiotics: Mechanism of Action and Resistance` carries `domain_context = NURSING` with Applicable Programs `Medicine, Nursing, Pharmacy` `[PROD]`. The prompt then emits `Domain: Nursing` plus `DOMAIN_CONSTRAINT` (`OpenAiLlmStudyPackService.java:122-123`): *"All content, terminology, examples, and question framing must belong to that domain. Do not blend in material from unrelated disciplines."* The note's own summary is pure mechanism — beta-lactam cell-wall cross-linking, 30S/50S ribosomal binding, efflux pumps, beta-lactamase — with zero nursing framing. |
| P2 | "at least three enum values exist beyond NURSING/ACCOUNTANCY" | **TRUE but understated. There are eleven values, not eight.** | `DomainContext.java:12-32`. `v0.111.0` appended `ARCHITECTURAL_DESIGN`, `ARCHITECTURAL_HISTORY_AND_THEORY`, `PLANNING_AND_SITE_DEVELOPMENT`. |
| P3 | (implied) the CPALE/business case is symmetric with the health case | **FALSE. They are not comparable, and this is the audit's most decisive finding.** | The health case has **cross-program assignments that already exist** (7 production notes at 3 programs; 10 planned PNLE rows pointing at Pharmacy/Medicine/PT). The business case has **zero cross-program business notes in production**, and `ACCOUNTANCY` — the incumbent value the candidate would carve out of — **has never been applied to a single note.** |
| P4 | (implied) resolver fallback may "accidentally use profile program" or "arbitrarily select a program" on a multi-program Note | **FALSE. Traced on all authoring paths and found correctly gated.** No defect. | §3. |
| P5 | (implied) Domain Context may leak into generated titles | **PARTLY. The doctrine is implemented in the prompt; leakage persists but is overwhelmingly legacy.** | §13. 4 genuine post-rule cases, not 23. |
| P6 | `docs/features/program-families.md` "may be current and relevant" | **Current, and relevant only as a constraint.** Families are save-time pre-fill, never generation context. No family change is needed for either candidate. | §11. |

**And one correction to a ratified document.** `ADR-001:377-378` records, as of 2026-08-29, that
*"three unused values — `Professional Education`, `Nursing`, `Accountancy`"* — and
`v0.111.0-phase-2-taxonomy-calibration.md:180-189` explicitly declined to resolve the
authoring-order-vs-wrong-shape question, naming
`docs/claude-plans/domain-context-adoption-read.sql` as **UNRUN** and writing: *"If it is ever run and
shows those values unused after their review sets are authored, that is a different finding and this
assumption must be revisited."*

**This audit ran that check.** `[PROD 2026-09-13]`:

| Value | Notes | Was it authoring order? |
|---|---|---|
| `PROFESSIONAL_EDUCATION` | **232** | **Yes — resolved.** LET material was authored and the value was adopted. |
| `NURSING` | **35** | **Yes — resolving now.** PNLE authoring is live (2 notes created 2026-09-13). |
| `ACCOUNTANCY` | **0** | **Undetermined, and the corpus now exists.** 154 Accountancy-program notes are authored across the full CPALE spread — FAR, Cost Accounting, MAS, Auditing, Taxation, RFBT — and **every one of them has `domain_context IS NULL`.** |

The authoring-order reading survives for `ACCOUNTANCY` — no CPALE *Review Set* has been built through
the `docs/curriculum/` pipeline, and the 154 notes predate the axis's use. But it is now **testable at
zero cost**, and §7 recommends testing it rather than legislating around it.

---

## 2. Current Domain Context architecture (required output §1)

**Repository implementation** — `backend/src/main/java/com/studysnap/backend/entity/DomainContext.java:12-32`.
`quantitative` is the second constructor argument. `[PROD]` usage is a `GROUP BY domain_context` over `notes`.

| # | Enum value | Display label | `quantitative` | `[PROD]` notes (total / public) |
|---|---|---|---|---|
| 1 | `ENGINEERING_MATHEMATICS` | Engineering Mathematics | `true` | 162 / 85 |
| 2 | `ENGINEERING_SCIENCES` | Engineering Sciences | `true` | 596 / 356 |
| 3 | `CIVIL_ENGINEERING` | Civil Engineering | `true` | 412 / 214 |
| 4 | `PROFESSIONAL_PRACTICE_AND_REGULATION` | Professional Practice & Regulation | `false` | 188 / 145 |
| 5 | `GENERAL_EDUCATION` | General Education | `false` | 110 / 110 |
| 6 | `PROFESSIONAL_EDUCATION` | Professional Education | `false` | 232 / 232 |
| 7 | `NURSING` | Nursing | **`true`** | 35 / 35 |
| 8 | `ACCOUNTANCY` | Accountancy | **`true`** | **0 / 0** |
| 9 | `ARCHITECTURAL_DESIGN` | Architectural Design | `false` | 98 / 53 |
| 10 | `ARCHITECTURAL_HISTORY_AND_THEORY` | History and Theory of Architecture | `false` | 69 / 37 |
| 11 | `PLANNING_AND_SITE_DEVELOPMENT` | Planning and Site Development | `false` | 33 / 32 |
| — | *(NULL — "not yet promoted")* | — | — | **5,682 / 498** |

**Prompt role — one line, one constraint.** `StudyPackGenerationContextResolver.effectiveAuthoringDomain`
(`:186-194`) returns the Domain Context **label** when set, else the resolved course-program string.
`OpenAiLlmStudyPackService.buildGenerationContextBlock:1579-1584` emits:

```
Domain: <label>
Domain constraint: treat the domain above as the authoritative academic domain. All content,
terminology, examples, and question framing must belong to that domain. Do not blend in material
from unrelated disciplines.
```

That is the entire mechanism. A Domain Context value is a **noun substituted into one sentence**, and
its only job is to make that sentence true and useful.

**Quantitative role** — `isQuantitativeContext:1643-1675`. `true` short-circuits to `true`
immediately (`:1644-1646`). `false` is a **no-op** that falls through to a keyword scan
(`QUANTITATIVE_KEYWORDS`, `:168-176`) over domain + subject + tags + key concepts + summary. This is
documented in the enum itself (`DomainContext.java:23-29`) with the warning that `true` is
*"PERMANENT PER NOTE because Study Packs never auto-regenerate"* and must not be flipped without an
owner decision.

**There is no `domain_contexts` table.** Grepped across `backend/` — the value set exists only as the
Java enum plus the frontend union. `ADR-001:417`'s phrase *"the curated `domain_contexts` set"* is
describing the enum, not a table. Confirmed `[PROD]`: `notes.domain_context` is `VARCHAR(64)` with
**zero CHECK constraints** (`information_schema`). **This is why adding a value needs no migration.**

### Documentation/code mismatches found

| Claim | Source | Status |
|---|---|---|
| "Ratified value set (8, as of Release A)" | `ADR-001:332-334` | **Superseded in the same file** at `:346-352` (11 values). Not a defect — the ADR records its own amendment — but a scan reading only `:334` gets the wrong number. |
| "three unused values — Professional Education, Nursing, Accountancy" | `ADR-001:377-378` | **STALE.** Two of the three are now in use (232 and 35). See §1. |
| "the live catalog holds **41 programs**" | `ADR-001:403` (dated 2026-09-04) | **STALE.** `[PROD 2026-09-13]`: **51 rows in `course_programs`** (filter: every row, no exclusions). The governance ratio is therefore **11:51 = 0.216**, not `11:41 = 0.268`. One new value takes it to **12:51 = 0.235** — far below the `0.40` reopen threshold at `ADR-001:409`. |
| `PROFESSIONAL_PRACTICE_AND_REGULATION` description | `frontend/lib/domain-context.ts:54` | **Enumerates a narrower value than it names** — engineering and architecture codes only. See §8; this is the `v0.99.0` defect pattern recurring. |

---

## 3. Current resolver behaviour (required output §3)

All line references are `StudyPackGenerationContextResolver.java` unless stated.

| Input shape | Resolved authoring domain | Line |
|---|---|---|
| **Domain Context set** (any program count) | The Domain Context **label**. Programs never consulted. | `:190-192` |
| **Exactly 1 joined program**, no Domain Context | That **catalog program name**. | `:143-145` |
| **2+ joined programs**, no Domain Context | **Rejected before any work.** `MultiProgramDomainContextRequiredException`. | `:33-39` |
| **0 joined programs**, no Domain Context | `notes.course_program` (the personal-notes string), else profile program. | `:146` |
| **Curator profile fallback** | **Suppressed.** `profileCourseProgram` is null whenever `CuratorAuthoringPredicate.isCurator(user)`. | `:131-133`, `:161-163`, `:178` |
| **Bulk generation** | Same rules; single catalog id resolves by name, else text/profile. | `:149-165` |
| **Study Pack with no reachable source note** | Deliberately guarded fallback; Domain Context and note level both null. | `:167-184` |

### The multi-program gate is enforced everywhere — no defect

The brief anticipated an inconsistency here. There is none. `assertGenerationReady` is called at
**seven** sites:

- `StudyPackService.java:131`, `:210`, `:320`
- `AdminStudyPackTransactionHelper.java:65`, `:129`
- `GeneratedQuizService.java:119`

and the two *authoring* paths that create a note and generate in one action carry their own
equivalent guard rather than relying on it:

- `NoteGenerationService.java:124-126` — `courseProgramIds.size() > 1 && domainContext == null` → throw
- `NoteBulkGenerationService.java:452-453` — identical check

**Reported as verified-clean with line numbers**, because "we checked and it holds" is a result.

### Two narrow behaviours worth recording (neither is a defect)

1. **`resolveCourseProgram` never returns a program *list*.** `StudyPackGenerationContext` carries a
   single `String courseProgram` — there is no field on it that could hold Applicable Programs, so
   **program arrays cannot reach the LLM by construction**, not merely by convention. ADR-001 rule 1
   is structurally enforced.
2. **An ADMIN/TEACHER mid-onboarding is not a curator** (`CuratorAuthoringPredicate:16-21`), so their
   profile program *is* available as a fallback at `:131`. Reachable only at 0 joined programs, which
   is the personal-note shape where a profile fallback is correct. This is the deliberate `v0.71.0`
   onboarding guard, not leakage.

### One genuine catalog defect, latent rather than live

`[PROD]` `course_programs` contains a row named **`Nursing · Medicine`** (middle dot, U+00B7),
`exam_goal_slug = 'pnle'`, carrying **2 notes** — both `Clinical Chemistry`, both
`domain_context = NURSING`.

**This is a curator fusing two programs into one catalog entry**, and it is the single clearest piece
of behavioural evidence that the shared-health gap is being worked around in the field rather than
theorised about.

**It is latent, not live**, and the distinction matters: because both notes carry a non-null Domain
Context, `effectiveAuthoringDomain` returns `"Nursing"` and the fused string is **shadowed — it never
reaches a prompt.** It becomes live the moment either note's Domain Context is cleared, at which point
`Domain: Nursing · Medicine` would be substituted into `DOMAIN_CONSTRAINT`, which then reads *"treat
`Nursing · Medicine` as the authoritative academic domain … do not blend in material from unrelated
disciplines"* — the exact logically-unsatisfiable instruction ADR-001 exists to prevent, **smuggled
past the multi-program gate because the join count is 1.**

Reported as a finding; **not this audit's to fix**, and not a blocker for either candidate.

---

## 4. Biomedical / Clinical evidence (required output §4)

### 4.1 The population `[PROD 2026-09-13]`

Canonical notes only (`copied_from_note_id IS NULL`, at least one join row) — learner copies excluded,
because a copy is not independent evidence of authoring intent.

| Subject | Canonical notes | Applicable Programs |
|---|---|---|
| Pharmacology | **7** | **Medicine, Nursing, Pharmacy** |
| Pharmacology | 11 | Nursing |
| Clinical Chemistry | 2 | `Nursing · Medicine` *(the fused row)* |
| Pharmacy Practice | 2 | Pharmacy |
| Pharmacokinetics · Pharmacodynamics · Clinical Pharmacy · Pharmaceutical Calculations | 1 each (4) | Pharmacy |
| Pathophysiology · Physiology | 1 each (2) | Physical Therapy / Nursing |
| Kinesiology (2) · Sports Rehab · Therapeutic Exercise · Exercise Physiology | 5 | Physical Therapy |

**No Anatomy, Microbiology, Biochemistry or Nutrition notes exist at all.** The brief listed them as
candidate members; the repository and production contain none. **This is the single most
scope-limiting fact in the audit** and it decides the naming question in §5.

### 4.2 The planned population — `docs/curriculum/pnle-comprehensive-review.tsv`

178 rows. Cross-tabulating `domain_context` × `applicable_programs`:

| `domain_context` | `applicable_programs` | Rows |
|---|---|---|
| `NURSING` | Nursing | 151 |
| **`NURSING`** | **Pharmacy** | **7** |
| **`NURSING`** | **Medicine** | **2** |
| **`NURSING`** | **Physical Therapy** | **1** |
| `PROFESSIONAL_PRACTICE_AND_REGULATION` | Nursing | 3 |
| `ENGINEERING_SCIENCES` | Biology, Chemical Eng, Chemistry, Civil Eng, Environmental Eng, Sanitary Eng | 6 *(all `Excluded`)* |
| `(unset)` | Physical Therapy / Psychology / Nursing | 8 *(7 `Excluded`)* |

**The ten bolded rows are the finding.** The curator's own committed plan assigns `NURSING` as the
authoring treatment to ten notes whose stated applicability is **Pharmacy, Medicine or Physical
Therapy — not Nursing.** They are marked `Reuse` or `Existing`, i.e. firmly planned, not speculative:

| Note title | Subject | Program | Status |
|---|---|---|---|
| Pharmacokinetics: Drug Absorption, Distribution, Metabolism, and Excretion | Pharmacokinetics | Pharmacy | Reuse |
| Pharmacodynamics: Agonists, Antagonists, and Receptors | Pharmacodynamics | Pharmacy | Reuse |
| Drug Interactions and Contraindications | Clinical Pharmacy | Pharmacy | Reuse |
| Antibiotics: Mechanism of Action and Resistance | Pharmacology | Pharmacy | Existing |
| Medication Safety and Patient Counseling | Pharmacy Practice | Pharmacy | Reuse |
| Proper Use and Precautions of Over-the-Counter Drugs | Pharmacy Practice | Pharmacy | Reuse |
| Dosage Calculations and Concentration Problems | Pharmaceutical Calculations | Pharmacy | Existing |
| Common Laboratory Values | Clinical Chemistry | Medicine | Reuse |
| Common Normal Laboratory Values | Clinical Chemistry | Medicine | Reuse |
| Pain, Inflammation, and Tissue Healing | Pathophysiology | Physical Therapy | Reuse |

**This is exactly `ADR-001:415`'s forcing function arriving.** As these ten enter the PNLE Review Set
their Applicable Programs become `{Nursing, Pharmacy}` or `{Nursing, Medicine}`, at which point
Domain Context becomes **mandatory** — and the only value that fits is one that does not exist.

### 4.3 The boundary is already drawn in the data — and its name is *professional role*

The same TSV sorts its own Pharmacology family cleanly, which is the strongest possible evidence for
Test D. Every Nursing-program row is **role-framed**; every Pharmacy/Medicine row is
**mechanism-framed**:

| Nursing-program rows (role is the knowledge) | Pharmacy/Medicine rows (mechanism is the knowledge) |
|---|---|
| Medication Administration Rights in Nursing | Pharmacokinetics: Drug Absorption, Distribution, Metabolism, and Excretion |
| Safe Medication Practices in Nursing | Pharmacodynamics: Agonists, Antagonists, and Receptors |
| Pharmacology Core Drug Classes and **Nursing Responsibilities** | Antibiotics: Mechanism of Action and Resistance |
| Opioid Medications and Safety Precautions in **Nursing Practice** | Drug Interactions and Contraindications |
| Adverse Drug Reactions in **Clinical Nursing Practice** | Dosage Calculations and Concentration Problems |
| High Alert Medications in Nursing Pharmacology | — |

The production summaries confirm the split is real, not titular `[PROD]`:

- **`Adverse Drug Reactions in Clinical Nursing Practice`** — *"**Nurses are integral to early
  detection** by monitoring patients, documenting ADRs, communicating with healthcare teams, and
  educating patients…"* → the nursing role **is** the content. `NURSING` is correct.
- **`Antibiotics: Mechanism of Action and Resistance`** — *"Beta-lactams inhibit cell wall
  cross-linking, while macrolides and aminoglycosides target the bacterial ribosome… enzymatic
  degradation (beta-lactamase), alteration of target sites, increased efflux pump activity…"* → **no
  professional role appears anywhere.** `NURSING` is a mis-instruction.
- **`Common Laboratory Values`** — *"essential indicators **used by nurses** to evaluate patient
  health status…"* → the knowledge (reference ranges) is shared; the **framing** was authored for
  nurses. **Genuinely ambiguous**, and §5 marks it so rather than forcing it.

### 4.4 The five-part classification test, applied (required §16)

| Test | Verdict | Working |
|---|---|---|
| **A — Existing-context failure** | **PASS** | For the ten rows in 4.2, every one of the eleven values materially distorts or underspecifies. `NURSING` asserts a professional role the content does not have and instructs the model to frame *all* terminology and examples that way (`:122-123`). `GENERAL_EDUCATION` is general-education material (`domain-context.ts:59`) — the brief calls this "clearly inappropriate" and the code agrees. `PROFESSIONAL_PRACTICE_AND_REGULATION` is codes/laws/ethics (`:54`). The three `ENGINEERING_*` and three architecture values are plainly wrong. `ACCOUNTANCY` is absurd. **No value describes shared biomedical mechanism.** |
| **B — Cross-program stability** | **PASS** | Not argued — **observed.** 7 production notes already carry 3 health programs simultaneously; 10 planned rows point at Pharmacy/Medicine/PT. `Pharmacokinetics: ADME` is the canonical case: absorption, first-pass metabolism, protein binding and CYP450 are taught identically to a nurse, a pharmacist and a physician. |
| **C — Meaningful content volume** | **PASS at the floor — one filter, stated once** | **The filter, used everywhere in this document: *canonical notes (`copied_from_note_id IS NULL`) whose treatment is mechanism-framed shared health science, and which are assigned or firmly planned to be assigned to 2 or more live health programs.*** That set is **exactly the 10 rows tabulated in 4.2** — `ADR-001:397`'s floor is ~10. **All 10 already exist as authored production notes** (their TSV statuses are 2 `Existing` + 8 `Reuse`, and each was located in 4.1's production read), so the **note count clears on the "already authored" reading**. What leans on *"or firmly planned"* is narrower than in `v0.111.0`: **only the multi-program assignment**, which the PNLE plan commits to and which is the trigger that makes Domain Context mandatory (`ADR-001:415`). **This is a stronger clause-(a) position than `v0.111.0`'s** (`:193-196`, where the notes themselves were 241 `New`), and it is stated exactly, not rounded. **It is still only just at the floor — 10, not 30.** |
| **D — Stable semantic boundary** | **PASS — strongest of the five** | The deciding test is one sentence: **"Does a professional role appear in the knowledge itself?"** If the note teaches what a *nurse does* — administration rights, monitoring duties, patient counselling as a nursing act, prioritisation — it is `NURSING`. If it teaches a *mechanism, parameter or calculation* that a nurse, pharmacist and physician all learn unchanged, it is the new value. **The curator has already applied this boundary correctly, unprompted, across 178 rows** (4.3). A boundary curators demonstrably already use is not vague. |
| **E — Prompt usefulness** | **PASS** | The value is substituted into one sentence (`:1582-1583`). Changing `Domain: Nursing` → `Domain: <mechanism domain>` flips the instruction from *"all terminology and examples must belong to Nursing"* to *"…must belong to shared biomedical science"* — directly changing terminology, framing, examples and scope. That is precisely what Test E asks. |

**All five pass. The candidate qualifies.**

---

## 5. Biomedical candidate verdict (required output §5)

# **APPROVE WITH DIFFERENT NAME/SCOPE**

**The need is proven. The proposed name is not, and it fails a binding guard.**

### Why "Biomedical & Clinical Sciences" cannot ship as named

`ADR-001:405` — the 2026-09-04 operative guard, the *first* of three conjunctive conditions:

> Names are **borrowed from real curriculum vocabulary and never invented** — `GENERAL_ENGINEERING`,
> `Built Environment`, **`Health Sciences`** and `Computing` were rejected on this test.

`ADR-001:297` gives the reasoning, and it is the reasoning that matters here:

> Two candidate values that were invented rather than borrowed (**`Health Sciences Foundation`**,
> `Computing`) failed both the learner-comprehension test and the governance rule, independently.
> **That correlation is the rule's justification: an invented name usually signals there is no real
> shared body of knowledge behind it.**

**`Biomedical & Clinical Sciences` is in the same class as the two already-rejected health names.** It
is a plausible grouping, and it is not a subject anyone teaches under. Shipping it would reverse a
2026-09-04 owner ruling by stealth.

Guard 2 (the composition matrix) does **not** discriminate here — Nursing, Pharmacy and Medicine are
all live catalog programs `[PROD]`, so any name serving this cluster serves 3 live programs and is
comfortably clear of the program-mirror presumption. **Guard 1 therefore carries the entire decision.**

### The name is an OWNER DECISION between two borrowed candidates — not settled here

**Stated once, and identically in §17.1 and the decision block, so a release planner does not scope a
name this audit did not settle.** `v0.106.0` established that the repo holds **no board blueprint
metadata**, so **neither candidate's provenance can be verified from inside the repository.** Clause
(b) of `ADR-001:397` requires the owner's recorded decision regardless. What this audit can do is
narrow the field to two borrowed names and say which way its own evidence leans.

| | **Candidate A1 (this audit's lean)** | **Candidate A2** |
|---|---|---|
| **Display label** | **`Basic Medical Sciences`** | `Pharmacology & Therapeutics` |
| **Enum value** | `BASIC_MEDICAL_SCIENCES` | `PHARMACOLOGY_AND_THERAPEUTICS` |
| **Borrowed?** | Yes — standard curriculum vocabulary for the preclinical bundle (anatomy, physiology, biochemistry, microbiology, pathology, pharmacology). | Yes — a named subject of nursing, pharmacy and medical curricula; the brief's own §17 lists it. |
| **Equals a live catalog program name?** | No. | No (`Pharmacy` is the program; this is the subject). |
| **Live programs served** | 4 — Nursing, Pharmacy, Medicine, Physical Therapy. | 3 — Nursing, Pharmacy, Medicine. |
| **Covers the §4.3 boundary?** | **Yes, entirely.** | **No — fails on 2 of 3 ambiguous cases.** |
| **Risk** | Claims a bundle whose non-pharmacology members hold 2 notes today. | A second value will be needed for the next shared health subject. |

**Why the lean is to the coarser name — three points, and the first is decisive:**

1. **`ADR-001:285` is the governing rule: *"Domain Context is the coarsest label under which the
   note's treatment is identical."*** The boundary this audit actually proved (§4.3) is **professional
   role**, and that test applies unchanged to pathophysiology, clinical chemistry and physiology — not
   only to pharmacology. **A pharmacology-scoped name is narrower than the boundary the evidence
   supports**, and a curator holding `Pain, Inflammation, and Tissue Healing` or `Common Laboratory
   Values` could not apply the role test under it. `ADR-001:307-313` records that the owner
   **declined** a proposal to invert this rule toward *"the narrowest existing authoring tradition."*
2. **The pharmacology concentration is an authoring-order artifact, and this audit already said so
   about something else.** 10 of the 10 qualifying rows are pharmacology-family because **PNLE is the
   set being authored right now** — precisely the reasoning §1 used to resolve `PROFESSIONAL_EDUCATION`
   and `NURSING`'s former zero usage. Narrowing the *name* on that distribution would contradict the
   audit's own finding.
3. **The clause-(a) floor is per VALUE, not per sub-subject.** `ADR-001:397` asks for ~10 notes whose
   treatment the existing values cannot represent. The qualifying set is 10 either way. A2 does not
   clear a *higher* bar; it simply covers less.

**The honest cost of the lean, stated rather than buried:** A1 names a bundle whose anatomy,
microbiology and biochemistry members hold **zero** notes today (4.1), and `ADR-001:109` warns *"Do
not pre-seed a program vocabulary."* The counter is that A1 is a **treatment label**, not a program
vocabulary, and the floor is met by the value as a whole. **A reader who weighs `:109` more heavily
than `:285` should choose A2 — that is a legitimate reading and it is the owner's to make.**

**What would settle it empirically, if the owner wants evidence rather than judgment:**
`ADR-001:291`'s generate-under-both-values tie-break, run on `Pharmacokinetics: ADME` and
`Pain, Inflammation, and Tissue Healing`. If A1 produces materially vaguer output on the
pharmacokinetics note, A2 wins. Use the existing runbook at
`docs/claude-prompt/canonical-knowledge-architecture-out/17-r4-verification-runbook.md`; `ADR-001:330`
forbids writing a second rubric.

**Everything downstream of the name is identical** — same 6 files, same semantic-contract shape, same
`NURSING` boundary, same migration (none). **The name choice does not change the release's size.**

### Semantic contract (required §18 / output §13)

Written against the actual prompt architecture — it must be true when substituted into
`buildGenerationContextBlock:1582-1583`, and it must **route adjacent material away**, which is the
lesson of the `v0.99.0` and `v0.111.0` description defects (`domain-context.ts:12-20`, `:76-78`):

**If A1 (`Basic Medical Sciences`):**

> *"Foundational health-science knowledge shared across health professions: drug action and
> therapeutic reasoning (pharmacokinetics, pharmacodynamics, drug classes and mechanisms of action,
> resistance, interactions, adverse-effect mechanisms, dosage and concentration calculation), disease
> mechanism and pathophysiology, normal physiology, and laboratory and diagnostic parameters. Frame
> the material using biomedical mechanism and standard scientific terminology appropriate to nurses,
> pharmacists and physicians alike, **without assuming a specific professional role.** Material whose
> subject is what a nurse does — medication administration rights, nursing assessment and monitoring
> duties, prioritisation, nursing documentation — belongs in **Nursing** instead. Codes, licensure and
> professional-conduct material belongs in **Professional Practice & Regulation**."*

**If A2 (`Pharmacology & Therapeutics`):** the same contract with the pathophysiology, physiology and
laboratory clauses removed, and a fourth routing sentence added sending that material back to
**Nursing** or leaving it **unset**.

The final sentence is load-bearing. A description that enumerates without routing adjacent material
away is what left ~41 Building Utilities notes and 215 ALE rows unclassified.

### Boundary with `NURSING` — `NURSING` is kept, unchanged

**The deciding test:** *does a professional role appear in the knowledge itself, or only in who is
reading it?* Role in the knowledge → `NURSING`. Role only in the audience → the new value.

| Classification | Real examples `[PROD]` / `[TSV]` |
|---|---|
| **Clearly the new value** | `Antibiotics: Mechanism of Action and Resistance` · `Pharmacokinetics: Drug Absorption, Distribution, Metabolism, and Excretion` · `Pharmacodynamics: Agonists, Antagonists, and Receptors` · `Drug Interactions and Contraindications` · `Dosage Calculations and Concentration Problems` · `Proper Use and Precautions of Over-the-Counter Drugs` |
| **Clearly `NURSING`** | `Medication Administration Rights in Nursing` · `Safe Medication Practices in Nursing` · `Adverse Drug Reactions in Clinical Nursing Practice` · `Pharmacology Core Drug Classes and Nursing Responsibilities` · `Opioid Medications and Safety Precautions in Nursing Practice` · `High Alert Medications in Nursing Pharmacology` |
| **Ambiguous — marked, not forced** | `Common Laboratory Values` / `Common Normal Laboratory Values` (shared reference ranges, nurse-framed prose — currently on the fused `Nursing · Medicine` row) · `Medication Safety and Patient Counseling` (counselling is a *pharmacist's* professional act, so the role test points at a third value that does not exist) · `Pain, Inflammation, and Tissue Healing` (shared pathophysiology, authored for Physical Therapy) |
| **Excluded — belongs elsewhere** | The 6 Water Treatment rows (`ENGINEERING_SCIENCES`, already correct, `Excluded` in the plan) · the 3 `PROFESSIONAL_PRACTICE_AND_REGULATION` nursing-law rows · the 5 Physical Therapy kinesiology/exercise notes (below floor, leave NULL) |

**⚠️ These three ambiguous cases are exactly where the A1/A2 name choice bites.** Under **A1** all
three are classifiable by the role test (laboratory parameters and pathophysiology are in scope; only
`Medication Safety and Patient Counseling` stays genuinely ambiguous, because its role is a
*pharmacist's*). Under **A2** two of the three fall outside the value entirely and stay `NURSING` or
`NULL`. **This is the clearest practical statement of the trade-off in §5**, and it is why the lean
is to A1. Either way `ADR-001:295` governs the residue: *"an honest NULL beats a catch-all."*

---

## 6. Business / Finance evidence (required output §6)

### 6.1 The population `[PROD 2026-09-13]`

Notes carrying an `Accountancy`, `Business Administration`, `Senior High – ABM` or `Law` program row:

| Program | Join rows | What is actually there |
|---|---|---|
| **Accountancy** | **154** | Full CPALE spread: MAS 11, Fundamentals of Accounting 11, RFBT 10, Financial Accounting 9, Financial Management 9, Cost Accounting 9, Audit Fundamentals 5, Basic/Advanced/Other Taxation 15, Managerial Decision Making 5, Investments 5, Audit Procedures 5, Budgeting 4, Performance 4, … **All 154 have `domain_context IS NULL`.** |
| **Business Administration** | **17** | **16 of the 17 are one shared Project Management set** already carrying `ENGINEERING_SCIENCES` across 23 programs — correctly, and it is not business content. **The genuine business note count is 1** (`Marketing Mix`). |
| **Senior High – ABM** | **3** | Three Economics notes (`Why Inflation Makes Your Money Feel Smaller`, `Factors Driving Gasoline Price Increases`, `Interest and Debt Growth in Business Finance`). |
| **Law** | **2** | Two `International Law` notes. |

**Cross-program business notes in production: zero.** No note carries both `Accountancy` and
`Business Administration`, or `Accountancy` and `Senior High – ABM`.

### 6.2 The content genuinely splits — and the split is clean

This is the part of the brief that required real reading rather than title classification, and the
content does support a boundary. Actual summaries `[PROD]`:

**Program-neutral — no accounting convention determines the explanation:**

- `Time Value of Money in Accountancy` — *"money available today is worth more than the same amount in
  the future due to its capacity to earn interest. Key components include Present Value (PV)…"*
- `Cost of Capital in Financial Management` — *"cost of debt and the cost of equity, combined through
  the Weighted Average Cost of Capital (WACC)…"*
- `Capital Budgeting in Management Advisory Services` — *"Net Present Value (NPV), which determines the
  difference between the present values of cash inflows and outflows using the firm's…"*
- `Break-Even Analysis and Cost-Volume-Profit Relationships` · `Contribution Margin and Its Role in
  Business Decision-Making` · `Fixed, Variable, and Mixed Costs` · `Risk and Return in Financial
  Management` · `Leverage` · `Capital Structure` · `Dividend Policy` · `Working Capital Management` ·
  `Financial Ratios` · `Balanced Scorecard` · `Residual Income` · `Return on Investment (ROI) in
  Accountancy`
- And three that are not accounting at all: `Application of Linear Programming in Management Advisory
  Services` · `Decision Trees in Management Advisory Services` · `Inventory Models in Management
  Advisory Services` (EOQ, reorder point) — **these are operations-research methods**.

**Accountancy-specific — a standard or convention *is* the explanation:**

- `Fair Value Through Other Comprehensive Income (FVOCI) Investments` — *"debt instruments that meet
  both the business model test — holding assets to collect contractual cash…"*
- `Fair Value Through Profit or Loss (FVTPL) Investments` — *"This classification **under IFRS 9**…"*
- `Financial Assets Measured at Amortized Cost under IFRS` · `Equity Method Accounting for Investments`
  (*"ownership of 20% to 50% of voting stock"*) · `Fundamentals of Consolidated Financial Statements`
  (*"eliminating intercompany transactions"*)
- `Audit Assertions in Financial Statement Auditing` · `Types of Audit Opinions` · `Understanding Audit
  Risk: Inherent, Control, and Detection Risk` · `Professional Skepticism in Auditing`
- Every Philippine tax note: `Creditable and Final Withholding Taxes` · `Percentage Tax` (*"VAT
  threshold of 3 million Philippine pesos"*) · `Documentary Stamp Tax` · `Expanded Withholding Tax` ·
  `Local Business Tax` (*"Republic Act No. 7160"*) · `Philippine Tax Administration`

**So a boundary exists and is testable:** *does an accounting standard, a statute, or a reporting
convention determine the explanation?* Yes → `ACCOUNTANCY`. No → shared.

### 6.3 The five-part test — where it fails

| Test | Verdict | Working |
|---|---|---|
| **A — Existing-context failure** | **FAIL — cannot be answered as posed** | The test asks whether *every existing Domain Context would materially distort or underspecify* the treatment. **The nearest existing value has never been applied to a single note.** `ACCOUNTANCY` is at `0/0` `[PROD]` while all 154 Accountancy notes sit at `domain_context IS NULL` — so they are currently generating on the **program-name fallback** (`:143-145` returns the catalog string `"Accountancy"`), which is a *different* string with *different* behaviour from the enum label. There is **no observation** of `ACCOUNTANCY` distorting anything, because it has never been tried. Test A is unfalsified, not passed. |
| **B — Cross-program stability** | **FAIL on evidence** | 6.2 makes the *conceptual* case well, and it is genuinely strong. But Test B asks whether the same treatment **can serve multiple Course/Programs** — and production has **zero** business notes assigned to 2+ programs. The 16 shared Business Administration rows are the Project Management set on `ENGINEERING_SCIENCES`. Business Administration's real business content is **1 note**; ABM's is **3**. Nothing has been reused across programs even once. |
| **C — Meaningful content volume** | **PASS — and this is the candidate's real strength** | ~40 genuinely program-neutral notes by content (6.2). Volume is emphatically **not** the blocker, and any future re-evaluation should start from this. |
| **D — Stable semantic boundary** | **PASS, conceptually** | "Does a standard, statute or reporting convention determine the explanation?" is as crisp as the health boundary. It is untested against a curator. |
| **E — Prompt usefulness** | **PASS, conceptually** | `Domain: Accountancy` on a WACC note instructs accounting terminology and examples; a shared label would not. Real, but currently hypothetical. |

**Guard 2 (`ADR-001:406`) independently blocks it:** a `Business & Finance` value would serve
`Accountancy` (154 notes) plus `Business Administration` (1 business note) and `Senior High – ABM` (3).
On the live evidence it serves **one** program meaningfully, so it is **presumed a program mirror** of
`ACCOUNTANCY` and must be justified explicitly or deferred.

---

## 7. Business / Finance candidate verdict (required output §7)

# **DEFER**

**Not rejected. The content cluster is real and the boundary is articulable — the *evidence* required
by this project's own governance is not yet in existence, and the cheapest way to produce it ships
nothing.**

Three independent reasons, any one sufficient:

1. **Test A is unanswerable while `ACCOUNTANCY` is at zero usage.** Carving a shared context out of a
   value nobody has ever applied is designing against a hypothesis.
2. **Test B has zero instances.** Cross-program business reuse has not happened once.
3. **Guard 2 presumes it a program mirror**, and the presumption is not currently rebuttable.

### The cheaper alternative that ships nothing, and is also the experiment

This is ADR-sanctioned and requires **no release**:

**Step 1 — classify the 154 Accountancy notes.** They are `NULL` today and are the only large authored
corpus with zero classification `[PROD]`. Classifying them is ordinary curation, not architecture
(`ADR-001:395`: *"Adding notes is authoring. Adding a Domain Context is architecture."*). This alone
resolves the `v0.111.0` open question definitively.

**Step 2 — run `ADR-001:291`'s own empirical tie-break** on the program-neutral subset from 6.2 —
`Time Value of Money`, `Cost of Capital`, `Capital Budgeting`, `Break-Even Analysis and CVP`,
`Inventory Models (EOQ)`, `Linear Programming`, `Balanced Scorecard`:

> **Empirical tie-break of last resort:** because this value is substituted into the generation
> prompt, generate the note under both candidate values and compare the output. For a small team this
> is faster and more decisive than adjudicating definitions.

Use the existing runbook at
`docs/claude-prompt/canonical-knowledge-architecture-out/17-r4-verification-runbook.md`. `ADR-001:330`
is explicit: **do not write a second evaluation rubric.**

**⚠️ One trap in step 1 that must not be walked into.** `ACCOUNTANCY` is `quantitative = true`
(`DomainContext.java:19`). Classifying all 154 wholesale would switch on computation guidance for the
**10 Regulatory Framework & Business Law notes** — `Corporation Code`, `Labor Code`, `Obligations and
Contracts`, `Negotiable Instruments`, `Insurance Code` — where it is wrong. And because `true`
short-circuits at `:1644-1646` and Study Packs never auto-regenerate, **the effect is permanent per
note.** Classify RFBT separately; see §8.

### What would flip this verdict to APPROVE

Stated precisely so the next pass measures rather than re-argues:

> **≥10 notes whose treatment is program-neutral by the 6.2 test, each assigned to 2 or more live
> Course/Programs**, arriving via a real CPALE / Business Administration / ABM Review Set plan in
> `docs/curriculum/` — the same `firmly planned` evidence shape the biomedical candidate clears on.

Today that count is **0**. Note that a `cpale-comprehensive-review.tsv` does not exist; **building one
is the single action that would most change this verdict**, and it is curation work, not engineering.

If it flips, the name to evaluate first is **`Business & Finance`** — it is borrowed vocabulary, does
not equal a live catalog program name, and would then serve 3+ live programs. **The name is not the
problem with this candidate; the evidence is.**

---

## 8. CPALE domain map (required output §8)

Preliminary. Note-level treatment differs within subjects, so no subject is forced to one value.

| CPALE area | `[PROD]` notes | Likely default | Known exceptions | Unresolved gap |
|---|---|---|---|---|
| **Financial Accounting and Reporting** | ~30 (Financial Accounting 9, Fundamentals 11, Financial Reporting 4, Inventory 4, PPE 4, Cash & Receivables 4, Equity 4) | **`ACCOUNTANCY`** — confirmed by content, not title. Recognition and measurement are standard-determined. | None found. | None. |
| **Advanced FAR** | Investments 5, Special Areas 3, Cash Flow 2 | **`ACCOUNTANCY`** — IFRS 9 classification, equity method, consolidation are the most convention-bound content in the corpus. | None. | None. |
| **Auditing** | ~16 (Audit Fundamentals 5, Procedures 5, Reports 4, Evidence 2, Internal Controls 3, Auditing 2) | **`ACCOUNTANCY`** | None. | **None — and this is a negative finding worth recording.** The brief asked whether evidence reveals a broader treatment domain. It does not: every audit note read is framed on financial-statement assertions and audit opinions, which are accounting-profession-specific. **Do NOT create an Audit/Assurance context.** |
| **Management Advisory Services** | ~24 (MAS 11, Managerial Decision Making 5, Decision Making 4, Performance 4) | **`ACCOUNTANCY` today; the largest genuine shared cluster tomorrow.** | `Linear Programming`, `Decision Trees`, `Inventory Models (EOQ)` are operations-research methods with no accounting content at all — arguably closest to `ENGINEERING_MATHEMATICS` (`quantitative = true`, described at `domain-context.ts:7` as *"a computational method"*). **Flagged, not recommended** — that value's name is engineering-specific. | **This is where Candidate B's evidence would come from.** |
| **Regulatory Framework / Business Law** | 10 | **UNRESOLVED — the one genuine gap this audit found on the business side.** | — | See below. |
| **Taxation** | ~19 (Basic 5, Advanced 5, Other 5, Income 4, Business Tax 4) | **`ACCOUNTANCY`** | None found. | **None. Do NOT create `TAXATION`.** Every note read is Philippine-statute-specific (RA 7160, BIR administration, the ₱3M VAT threshold) — that is *narrower* than Accountancy, not broader, and cross-program tax reuse would require Law-program notes that do not exist (Law carries 2 International Law notes). |

### The Business Law gap — reported as instructed, not resolved

The brief said: *"Do not assume `PROFESSIONAL_PRACTICE_AND_REGULATION` applies merely because
regulation/law is involved… If a separate legal/business-law treatment gap exists, REPORT IT."*

**A gap exists, and it is a description defect before it is a taxonomy defect.**

- **PPR's ratified *justification* fits.** `ADR-001:336`: *"justified on **treatment** — legal,
  procedural, and regulatory rather than scientific."* The 10 RFBT notes are exactly that: statutes,
  contract elements, corporate formation, negotiable instruments. Their treatment is prose-legal, not
  computational.
- **PPR's *description* does not.** `frontend/lib/domain-context.ts:54` reads: *"Codes, laws, ethics
  and licensure shared across programs: **Engineering Laws, Ethics and Contracts; Professional
  Practice; Building Laws including the National Building Code and BP 344 accessibility; and
  Construction Safety.**"* **No curator authoring CPALE Business Law would ever select this.**
- **This is the `v0.99.0` defect pattern recurring verbatim.** That release's own comment
  (`domain-context.ts:12-20`) diagnoses it: a description that *"is an enumeration of a NARROWER value
  than the one it names"* is why ~41 Building Utilities notes sat unclassified. PPR is now the same
  shape — a cross-program treatment value described as an engineering/architecture value.
- **PPR is `quantitative = false`** (`DomainContext.java:15`), which is **correct** for Business Law
  and is the opposite of `ACCOUNTANCY`'s `true`. That is a second, independent argument that RFBT
  belongs in PPR rather than swept in with the other 144.

**Recommendation: widen PPR's description rather than mint `BUSINESS_LAW`.** It is a one-string
frontend change with no enum, no migration and no backfill, and it is the coarsest-label rule applied
correctly. **Do NOT add `BUSINESS_LAW`** — it has 10 notes (below the floor), serves one live program,
and would be presumed a program mirror.

**⚠️ Declared honestly:** this is the audit's least-certain recommendation. Widening PPR changes which
notes land there — *"that is exactly what item 4 did to `ENGINEERING_SCIENCES`"* (`domain-context.ts:48-53`)
— and it should be validated by classifying two or three RFBT notes under PPR and reading the output
before the description is widened for everyone.

---

## 9. Health-sciences domain map (required output §9)

| Subject | `[PROD]` canonical | Likely default | When `NURSING` still wins |
|---|---|---|---|
| **Pharmacology** | 18 (7 multi-program) | **Split.** Mechanism/class/resistance → new value. Role/responsibility/administration → `NURSING`. | Whenever the nursing act is the knowledge: administration rights, safe-practice protocols, ADR monitoring duties, high-alert handling. **11 of 18 stay `NURSING`.** |
| **Pharmacokinetics / Pharmacodynamics / Clinical Pharmacy** | 3 | **New value.** Zero role content. | Never, on current content. |
| **Pharmaceutical Calculations** | 1 | **New value.** Dosage and concentration maths is identical across the three programs. | If reframed as *nursing* dosage verification at the bedside. |
| **Pharmacy Practice** | 2 | **Ambiguous.** Counselling is a *pharmacist's* professional act — the role test points at a value that does not exist. | Leave as authored; do not force. |
| **Clinical Chemistry** | 2 | **Ambiguous.** Reference ranges are shared; both notes are nurse-framed prose. Currently on the fused `Nursing · Medicine` row. | `NURSING` wins today on the authored framing. Outside a pharmacology-scoped name regardless. |
| **Pathophysiology / Physiology** | 2 | **Out of scope of the recommended name.** Genuinely shared, but 2 notes is far below the floor. | Leave `NULL` or as authored. |
| **Anatomy / Microbiology / Biochemistry / Nutrition** | **0** | **No content exists.** | N/A — **do not pre-seed.** |
| **Physical Therapy cluster** (Kinesiology, Therapeutic Exercise, Sports Rehab, Exercise Physiology) | 5 | **`NULL`.** Single-program, thin, all `Excluded` in the PNLE plan. | N/A. The program-name fallback serves them correctly. |
| **Medical–Surgical / Psychiatric / Pediatric / Maternal & Child / Fundamentals / Community Health** | ~200 | **`NURSING`, unambiguously.** | Always. **This is the bulk of the corpus and the new value must not touch it** — `NURSING` is kept for a reason, and that reason is ~200 notes. |

---

## 10. Finance program recommendation (required output §10)

# **DEFER**

**Repository + `[PROD]` fact: `Finance` does NOT exist in the catalog.** `[PROD]` lists **51 rows** in
`course_programs`; there is no `Finance`. `V106__course_program_catalog.sql` seeded 21 (including
`Business Administration`, `Law`, `Medicine`, `Pharmacy`, `Senior High – ABM`); `V142` added 6
Education programs; the remaining ~24 were created at runtime via `POST /course-programs`.

**Not `DO NOT ADD`, because a real cluster exists.** The ~13 `Financial Management` + `Investments` +
capital-budgeting notes in 6.2 are genuinely Finance-program material.

**Not `ADD LATER`, because `ADR-001:107-111` sets a specific trigger that has not fired:**

> **Catalog growth is incremental and demand-driven by authoring:** a curator judging that a canonical
> note is applicable to a program is the trigger to add that program.

**No curator has made that judgment.** Every one of those ~13 notes carries exactly one program row —
`Accountancy`. And the **counter-example is decisive**: `Business Administration` *already exists* in
the catalog and has attracted **one** genuine business note in the entire library. Adding `Finance`
now would produce a second thin shelf — precisely the risk `ADR-001:277` names (*"a program carrying a
handful of shared foundational notes can read as a curriculum without being one"*).

**Trigger to revisit:** a curator marks ≥1 canonical note applicable to Finance while authoring — i.e.
the moment a CPALE or Business Review Set plan lists `Finance` in an `applicable_programs` cell. **The
Domain Context decision does not depend on this and must not wait for it.**

---

## 11. Program Family implications (required output §11)

**Current state.** `program_families` is a name plus a nullable FK (`V106`). Two families exist
`[PROD]`: **Engineering** (18 member programs) and **Education** (7, seeded by `V142`). The other 26
programs — including every health and business program — have `program_family_id IS NULL`.
`docs/features/program-families.md` and `V142`'s header confirm the primitive is fully generic and the
combobox derives families dynamically, so seeding a family is a **data migration with no code change**.

**Recommendation: NO family for either group, and do not bundle one into this work.**

- **Health Sciences family** — would expand to Nursing, Pharmacy, Medicine, Physical Therapy,
  Radiologic Technology. `ADR-001:91` requires expansion to be **unconditional and to all members**.
  Radiologic Technology has **0** notes and Physical Therapy **7**, so every use would over-select and
  need manual trimming. The observed multi-program shape is a tight `{Nursing, Pharmacy, Medicine}`
  triple — **a family is the wrong tool for a 3-program pattern a curator can click.**
- **Business / Commerce family** — would expand to Accountancy, Business Administration, ABM, Law.
  With **zero** cross-program business notes, there is no repetitive authoring work to reduce. A
  family here would be a curriculum assertion dressed as a shortcut — the exact tripwire at
  `ADR-001:97`.

**Independence restated, since the brief asks for it explicitly and the code backs it:** Program
Family reaches neither generation nor Domain Context. `StudyPackGenerationContext` has no family
field; `resolveCourseProgram` (`:130-147`) consults `note_course_program` and the personal string only.
Expansion is save-time (`ADR-001:93`). **Domain Context ≠ Program Family ≠ Applicable Programs** holds
structurally, not by convention.

---

## 12. Calibration table (required output §12)

All rows are **real production Notes** `[PROD 2026-09-13]` or **real committed plan rows**
`[TSV: pnle-comprehensive-review.tsv]`. Reasons cite actual summary content.
**Nothing here is reclassified — this is a recommendation table only.**

| Note | Current Context | Applicable Programs | Recommended | Conf. | Reason (from actual content) |
|---|---|---|---|---|---|
| `Antibiotics: Mechanism of Action and Resistance` | **`NURSING`** | **Medicine, Nursing, Pharmacy** | **New value** | **High** | Summary is pure mechanism — *"Beta-lactams inhibit cell wall cross-linking… macrolides and aminoglycosides target the bacterial ribosome… beta-lactamase production, efflux pump activity."* No professional role anywhere. `Domain: Nursing` actively mis-instructs a note serving 3 programs. |
| `Pharmacokinetics: Drug Absorption, Distribution, Metabolism, and Excretion` | `NULL` | Pharmacy `[TSV: →PNLE]` | **New value** | **High** | *"first-pass metabolism… bioavailability… protein binding."* Identical for nurse, pharmacist, physician. Entering PNLE makes it multi-program → Domain Context becomes mandatory. |
| `Pharmacodynamics: Agonists, Antagonists, and Receptors` | `NULL` | Pharmacy `[TSV: →PNLE]` | **New value** | **High** | *"competitive antagonists… noncompetitive antagonists, which bind allosteric sites."* Receptor theory, no role. |
| `Drug Interactions and Contraindications` | `NULL` | Pharmacy `[TSV: →PNLE]` | **New value** | **High** | *"cytochrome P450 enzyme system… enzyme induction or inhibition."* Mechanism-framed. |
| `Dosage Calculations and Concentration Problems` | `NULL` | Pharmacy `[TSV: →PNLE]` | **New value** | **High** | *"Desired Dose = (Available Dose / Available Volume) × Volume to Administer."* Arithmetic is role-independent. |
| `Proper Use and Precautions of Over-the-Counter Drugs` | `NULL` | Pharmacy `[TSV: →PNLE]` | **New value** | Medium | Therapeutic guidance; no single profession's duty framing. |
| `Adverse Drug Reactions in Clinical Nursing Practice` | `NULL` | Nursing | **`NURSING`** | **High** | *"**Nurses are integral to early detection** by monitoring patients, documenting ADRs, communicating with healthcare teams."* The nursing role **is** the content. |
| `Medication Administration Rights in Nursing` | **`NURSING`** | **Medicine, Nursing, Pharmacy** | **`NURSING` (keep) — but the *programs* are over-selected** | **High** | The "rights" framework is a nursing administration protocol. **Finding: the defect on this note is 3 Applicable Programs, not the Domain Context.** |
| `Pharmacology Core Drug Classes and Nursing Responsibilities` | **`NURSING`** | **Medicine, Nursing, Pharmacy** | **`NURSING` (keep); programs over-selected** | **High** | Title and content name nursing responsibilities explicitly. |
| `Respiratory and Gastrointestinal Pharmacology for Nursing` | **`NURSING`** | **Medicine, Nursing, Pharmacy** | **`NURSING` (keep); programs over-selected + title leakage** | **High** | **Created 2026-09-13 — today, during the expansion that prompted this brief.** Role-framed title carrying a `for Nursing` container suffix, yet assigned to Medicine and Pharmacy. |
| `Common Laboratory Values` | **`NURSING`** | **`Nursing · Medicine` (fused row)** | **AMBIGUOUS** | Low | Knowledge (Hgb 13.8–17.2 g/dL, Na⁺ 135–145 mEq/L) is universal; prose is nurse-framed (*"used by nurses to evaluate patient health status"*). Sits on the fused catalog row. Mark ambiguous; do not force. |
| `Pain, Inflammation, and Tissue Healing` | `NULL` | Physical Therapy `[TSV: →PNLE]` | **New value under A1; AMBIGUOUS under A2** | Low | *"inflammation, proliferation, and remodeling"* is shared pathophysiology — the §4.3 role test classifies it cleanly, but only a name whose scope reaches pathophysiology can hold it. **This row is the A1/A2 decision in miniature.** |
| `Time Value of Money in Accountancy` | `NULL` | Accountancy | **`ACCOUNTANCY` (classify); re-test later** | Medium | Content is generic finance (*"Present Value (PV)… discounted using a specified interest or discount…"*). **Also a canonical-title violation** — the prompt's own negative example verbatim. Cross-program *applicability* is unproven. |
| `Cost of Capital in Financial Management` | `NULL` | Accountancy | **`ACCOUNTANCY` (classify); re-test later** | Medium | WACC, cost of debt/equity — identical in BSBA and Finance curricula, but no other program claims it. |
| `Capital Budgeting in Management Advisory Services` | `NULL` | Accountancy | **`ACCOUNTANCY` (classify); re-test later** | Medium | NPV/IRR. Program-neutral content, single-program applicability. |
| `Inventory Models in Management Advisory Services` | `NULL` | Accountancy | **AMBIGUOUS** | Low | EOQ and reorder point are operations research, not accounting. Neither `ACCOUNTANCY` nor a business context is obviously right; `ENGINEERING_MATHEMATICS` fits the *treatment* but not the name. |
| `Fair Value Through Profit or Loss (FVTPL) Investments` | `NULL` | Accountancy | **`ACCOUNTANCY`** | **High** | *"This classification **under IFRS 9**…"* The standard **is** the explanation. Would be actively wrong under a shared business context. |
| `Fundamentals of Consolidated Financial Statements` | `NULL` | Accountancy | **`ACCOUNTANCY`** | **High** | *"combining… line by line while eliminating intercompany transactions."* Pure reporting convention. |
| `Audit Assertions in Financial Statement Auditing` | `NULL` | Accountancy | **`ACCOUNTANCY`** | **High** | *"management's representations regarding… recognition, measurement, presentation, and disclosure."* Profession-specific. |
| `Percentage Tax Principles and Application` | `NULL` | Accountancy | **`ACCOUNTANCY`** | **High** | *"VAT threshold of 3 million Philippine pesos… typically 3%."* Philippine statute. Narrower than Accountancy, not broader. |
| `Corporation Code in Philippine Business Law` | `NULL` | Accountancy | **`PROFESSIONAL_PRACTICE_AND_REGULATION`** | Medium | *"Republic Act No. 11232… juridical entities separate from their stockholders."* Legal/procedural prose, `quantitative = false` — matches PPR's ratified justification but **not** its current description. See §8. |
| `Labor Code as a Regulatory Framework in Philippine Business Law` | `NULL` | Accountancy | **`PROFESSIONAL_PRACTICE_AND_REGULATION`** | Medium | *"Presidential Decree No. 442… employment contracts, wages, working hours."* Same reasoning. |
| `Project Management` (16 notes) | `ENGINEERING_SCIENCES` | 23 programs incl. Business Administration | **No change — correct today** | **High** | Already the shared-treatment model working. **Included as a control**: it shows cross-program reuse *can* work here, and that Business Administration's 17 rows are not business evidence. |

---

## 13. Prompt / generation implications (required output §13)

### Each approved context's effect

The new value changes exactly one substituted noun (`:1582`) and thereby the scope of
`DOMAIN_CONSTRAINT` (`:122-123`). Semantic contract for both name candidates in §5. **No prompt file
changes, under either name.**

### Four verifications the brief requires

| Claim | Verdict | Evidence |
|---|---|---|
| **Applicable Programs do not leak into generation** | **CONFIRMED — structurally, not by convention** | `StudyPackGenerationContext` carries a single `String courseProgram`. There is **no field capable of holding a list.** `resolveCourseProgram:143-145` returns a joined name only at `size() == 1`; at 2+ with no Domain Context, `assertGenerationReady:33-39` throws first. |
| **Review Set does not leak into generation** | **CONFIRMED** | No collection/review-set field exists on `StudyPackGenerationContext`, and `resolve(:41-63)` reads only the note and the user. `ADR-001:20`'s Review-Set-independence rule holds mechanically. |
| **Profile fallback does not bias multi-program canonical notes** | **CONFIRMED** | Curators are excluded from profile fallback at `:131-133`, `:161-163`, `:178`; the multi-program shape is rejected before resolution at `:33-39`. |
| **Domain Context does not leak into canonical titles** | **PARTLY — doctrine implemented, residue is legacy** | See below. |

### Title generation — the honest number

**Domain Context *does* reach the title-emitting prompt.** `buildGenerateNoteInputMessages:645-659`
includes `buildContentContextBlock(context)`, which emits the `Domain:` line and `DOMAIN_CONSTRAINT`.
So leakage is *possible by construction* and is prevented only by prompt instruction.

**The instruction exists and is well-written.** `note-generation-developer.txt:18-31` — and it uses the
brief's own examples verbatim:

```
- "Highway Drainage Systems" -> correct; "Drainage Systems in Civil Engineering" -> the program only names who it is for
- "Time Value of Money" -> correct; "Time Value of Money in Accountancy" -> the program only names who it is for
- "Nursing Management of Acute Asthma" -> correct; nursing management is itself the topic
```

Shipped **2026-08-29** (`6bb5985e`, `126fbf06`), governing both title-emitting prompts.

**`[PROD]` measurement, filtered by the prompt's own rule** (`:24` — a discipline name belongs in the
title when it *is* the knowledge):

- **92 canonical notes** carry a container-suffix pattern; **69 predate 2026-08-29.**
- Of the **23** created on or after that date, most are `Civil Engineering Ethics` / `Civil Engineering
  Laws` rows where the discipline **is** the knowledge — **legitimate under the rule, not leakage.**
- **Genuine post-rule leakage: 4 notes.** `Respiratory and Gastrointestinal Pharmacology for Nursing`
  (2026-09-13) · `Economic Development in Accountancy` (2026-09-08) · `Shear Strength of Cohesive Soil
  in Civil Engineering` (2026-09-04) · `Advanced Construction Materials in Civil Engineering`
  (2026-09-04).

**Conclusion: the doctrine is implemented and mostly effective; ~4 post-rule escapes in ~2 weeks
indicate soft compliance, not a broken mechanism.** Production also holds the literal string
`Time Value of Money in Accountancy` (created 2026-07-03, **before** the rule) — the prompt's own
negative example existing as real data. **Reported, not fixed, per the brief.**

---

## 14. Implementation blast radius (required output §14)

**If — and only if — the owner ratifies a name and a `quantitative` value.** Scoped precisely enough
for a kickoff.

### Files that MUST change (6)

| # | File | Change | Why it is load-bearing |
|---|---|---|---|
| 1 | `backend/.../entity/DomainContext.java` | **Append** one constant after `:32`. | `@Enumerated(EnumType.STRING)` persists the NAME, so ordinal is not data — but `DomainContextTest` uses `containsExactly`, so **append, never insert** (the comment at `:20-22` says exactly this). |
| 2 | `frontend/lib/domain-context.ts` | Append one `DOMAIN_CONTEXT_OPTIONS` entry with the §5 description. | The description **is** the curator's only guidance. Must name what belongs **and route nursing-role material away** — the `v0.99.0`/`v0.111.0` lesson. |
| 3 | `frontend/lib/api.ts:536-547` | Add one union member. | Without it, TypeScript rejects the value everywhere; the dropdown and all DTO types key off this union. |
| 4 | `backend/.../entity/DomainContextTest.java:24` | Add the label to `containsExactly`. | Fails otherwise. |
| 5 | `frontend/lib/domain-context.test.ts:9` | `toHaveLength(11)` → `12`, plus a description assertion. | Fails otherwise. |
| 6 | `docs/architecture/ADR-001-...md` | Revision-log entry: name, reasoning, date, `quantitative`, evidence link. | **`ADR-001:397` clause (b) — this IS the decision record. The release is not complete without it.** |

### Files that must change for correctness-of-record (3)

7. `RELEASES.md` · 8. `docs/features/<feature>.md` (none exists for Domain Context; nearest is
`program-families.md` — **a new `docs/features/domain-context.md` is arguably owed and is the one
discretionary item here**) · 9. `docs/gpt-contexts/GPT_CONTEXT.md` version stamp.

### What does NOT change — each verified, not assumed

| Area | Why not |
|---|---|
| **Database migration** | **None.** `[PROD]`: `notes.domain_context` is `VARCHAR(64)` with **0 CHECK constraints**. `@Enumerated(EnumType.STRING)` writes the name. **No DDL, no Flyway file.** |
| **Backfill** | **None.** No existing row changes value. |
| **Resolver** | `StudyPackGenerationContextResolver` is enum-agnostic — it calls `getLabel()`. **Zero changes.** |
| **Prompts** | Every `prompts/study-pack-v1/*.txt` is value-agnostic; the value arrives as a substituted string. **Zero changes.** |
| **API serialization** | `DomainContext.fromString` (`:42-51`) is `valueOf`-based and already returns `null` for unknown input. **Zero changes.** |
| **Bulk generation / validation** | `NoteAuthoringMetadataParser`, `NoteBulkGenerationService:438`, `NoteGenerationService:121` all parse generically. **Zero changes.** |
| **Analytics** | Domain Context fires no `AnalyticsEventType`. **Zero changes.** |
| **Program catalog / families** | Untouched. §10, §11. |

### Backward compatibility (required §23) — blast radius is genuinely small

| Surface | Impact |
|---|---|
| Persisted values | **None.** Purely additive; no value removed or renamed. |
| Old frontend ↔ new backend | **A stale frontend receives an unknown string.** `getDomainContextLabel` (`domain-context.ts:96-98`) falls back to `?? value` — it renders the raw enum name. Ugly, never a crash. |
| New frontend ↔ old backend | `fromString` returns `null` on unknown (`:48-50`) — the value is silently dropped, not a 500. |
| **Deploy ordering** | **Frontend-first is the safe order**, and the release owes this statement per `CLAUDE.md`. Frontend-first: the new option appears but a save is silently dropped by the old backend — bad, but the correct mitigation is **ship together**. Backend-first is harmless. **⚠️ `CLAUDE.md` warns Vercel and Render deploy through two independent integrations and have diverged before. Run `scripts/check-deploys.sh`.** |
| Existing filters / exports / public DTOs | Additive; no enum is exhaustively switched on in a way that breaks. |

---

## 15. Migration strategy (required output §15)

**Migration required immediately: NO.** No schema change, no backfill, no blind migration.

**Recommended handling, in priority order:**

1. **Reclassify the 7 multi-program Pharmacology notes by hand** `[PROD]`. This is the entire initial
   migration population and it is **7 rows**. Per §12, the correct split is likely **1 note changes
   Domain Context** (`Antibiotics: Mechanism of Action and Resistance`) and **6 notes should instead
   have their over-selected Applicable Programs trimmed** — they are genuinely nursing-role content
   carrying Medicine and Pharmacy rows they do not merit. **⚠️ Do not assume all 7 move; the analysis
   says most should not.**
2. **Classify on touch for everything else.** The PNLE plan's 10 rows get the new value **as they are
   authored into the Review Set**, which is when Domain Context becomes mandatory anyway
   (`ADR-001:415`). Zero extra work.
3. **No title-based migration, ever.** Explicitly forbidden by the brief and independently unsafe here:
   `Antibiotic Classes in Pharmacology for Nursing` and `Antibiotics: Mechanism of Action and
   Resistance` are near-identical titles with **opposite** correct classifications.
4. **Leave the 5,682 NULL-context notes NULL.** `ADR-001:502` — `domain_context IS NULL` is the
   load-bearing promotion-backlog marker. **A server-side default would destroy it.**
5. **Do not touch the fused `Nursing · Medicine` catalog row in this work.** It is latent (§3) and
   deleting or renaming a `course_programs` row referenced by `note_course_program` is its own change
   with its own risk. **Record it; sequence it separately.**

### The `quantitative` decision — evidence, not a pick

`DomainContext.java:29` forbids choosing this without an owner decision, so this audit supplies the
evidence and stops.

**Facts:** `NURSING` is **`true`**. `false` is a no-op falling through to `QUANTITATIVE_KEYWORDS`
(`:168-176`); `true` short-circuits at `:1644-1646` and is **permanent per note** because Study Packs
never auto-regenerate.

**What the keyword scan alone would catch, checked against real summaries `[PROD]`:**

| Note | Trips a keyword? | Which |
|---|---|---|
| `Dosage Calculations and Concentration Problems` | **Yes** | *"converting **units**"*, *"**formula**"* |
| `Common Laboratory Values` | **Yes** | *"**chemistry**"*, *"**balance**"* |
| `Antibiotics: Mechanism of Action and Resistance` | **No** | (correctly — it is not computational) |
| `Pharmacokinetics: Drug Absorption, Distribution, Metabolism, and Excretion` | **Apparently no** | **⚠️ This is the decision.** Half-life, clearance and bioavailability **are** computational, and this note's summary trips nothing. |

**Which way the evidence leans, stated rather than left neutral: `false`, but not because it is
harmless.** `false` causes a **known regression on a named note class** — pharmacokinetics content
moving off `NURSING` (`true`) would lose computation guidance it has today, and half-life, clearance
and bioavailability are genuinely computational. `true` avoids that but over-applies to purely
conceptual mechanism notes (`Antibiotics: Mechanism of Action and Resistance`) and is **permanent per
note**. The lean is `false` **only because its failure is repairable and `true`'s is not**: a missed
note can be fixed by adding `"pharmacokinetic"`/`"half-life"`/`"clearance"` to `QUANTITATIVE_KEYWORDS`
(`:168-176`), which retro-actively corrects every future generation, whereas a wrongly-`true` note
cannot be corrected without regenerating a Study Pack — and Study Packs never auto-regenerate.
**⚠️ If `false` is chosen, widening `QUANTITATIVE_KEYWORDS` should ship in the same release, or the
regression ships unmitigated.** Two values were delivered `true` and had to be corrected
(`DomainContext.java:28`); this is the same trap from the other side. **Owner's call either way.**

---

## 16. Tests eventually required (required output §16)

| Tier | Test | Why |
|---|---|---|
| **Enum/serialization** | `DomainContextTest` — label in `containsExactly`; `fromString` round-trip; **`quantitative` asserted explicitly** | Two values shipped with the wrong `quantitative` and had to be corrected (`DomainContext.java:28`). Assert it, do not assume it. |
| **Resolver** | `StudyPackGenerationContextResolverTest` — new value → `effectiveAuthoringDomain` returns its **label**, not its name | The label is what reaches the prompt (`:191`). |
| **Multi-program** | New value + 3 programs → `assertGenerationReady` does **not** throw; and 3 programs + `null` still **does** | Guards both directions of the ADR rule. |
| **Prompt-context** | `OpenAiLlmStudyPackServiceTest` — assert the new value's **label** appears on the `Domain:` line **and** that no program list appears anywhere in the payload | The negative assertion is the one that protects ADR-001 rule 1. |
| **Quantitative** | Assert `isQuantitativeContext` for the new value matches the ratified flag, **and** that a `false` value still trips on `"units"` | Pins the fall-through the decision depends on. |
| **Title generation** | Assert the title rule block survives; a note with the new Domain Context does **not** produce a title containing the label | §13 shows leakage is possible by construction. |
| **Bulk generation** | `NoteBulkGenerationServiceTest` — the new value parses and reaches the batch context | `:438`, `:452`. |
| **Frontend** | `domain-context.test.ts` — length 12; description **non-empty and mentions the routing-away clause** | The description defect is this file's recurring failure mode. |
| **Backward compatibility** | `getDomainContextLabel` returns the raw value for an unknown string; `fromString` returns `null` | Pins the two skew behaviours in §14. |
| **⚠️ Not required here — both halves of the rule, because a reader will check both** | (a) A `MockMvc` real-request test. (b) A `lib/api-*.test.ts` request-shape test. | `CLAUDE.md`'s transport rule has two halves. **(a)** is owed for a **new endpoint**; this release adds none. **(b)** is owed when the client's **request shape** changes; `frontend/lib/api.ts` *is* touched here, but the change is **a TypeScript union member only — it emits no JavaScript and executes nothing at runtime**, so no request shape moves. **Neither is owed. State both in the release rather than addressing only the MockMvc half**, which would read as an unexamined skip. |

---

## 17. Genuine owner decisions

Only questions the repository cannot answer. Implementation details the architecture already settles
are deliberately absent.

1. ~~**Name and scope for Candidate A — the one genuinely blocking decision.**~~
   **✅ SETTLED 2026-09-13 (owner): `Basic Medical Sciences` / `BASIC_MEDICAL_SCIENCES`** — this
   audit's lean, on `ADR-001:285`'s coarsest-label rule. Full comparison and the honest cost of this
   choice (the bundle's non-pharmacology members hold 2 notes today) remain in §5 for the record.
2. ~~**`quantitative` — `true` or `false`?**~~ **✅ SETTLED 2026-09-13 (owner): `false`.**
   **⚠️ Condition attached and also accepted: widening `QUANTITATIVE_KEYWORDS` (`:168-176`) to cover
   pharmacokinetics terms (e.g. `"pharmacokinetic"`, `"half-life"`, `"clearance"`) ships in the SAME
   release** — per §15, `false` alone regresses computation guidance on pharmacokinetics notes that
   currently inherit it from `NURSING(true)`, and that regression has no mitigation without the
   keyword widening landing together with it.
3. **Does the biomedical work ship before a CPALE Review Set plan exists?** This audit says yes — the
   two candidates are independent and Candidate B is deferred pending curation, not engineering.
   Not re-litigated by the owner; stands as this audit's own answer.
4. ~~**Should `PROFESSIONAL_PRACTICE_AND_REGULATION`'s description be widened to cover business law
   (§8)?**~~ **✅ SETTLED 2026-09-13 (owner): YES, bundle it into the same release.** §8's declared
   uncertainty stands as written — this is a one-string frontend change with no enum, no migration —
   but §8's own validation caution is now the implementer's to weigh at build time, not a reason to
   defer: *"it should be validated by classifying two or three RFBT notes under PPR and reading the
   output before the description is widened for everyone."*
5. **The fused `Nursing · Medicine` catalog row (§3) — rename, split, or leave?** Latent today. A
   catalog change, deliberately out of scope here. Not raised to the owner; remains deferred.

---

## Final decision block

```
DOMAIN CONTEXT CALIBRATION

Biomedical / Clinical candidate:
  APPROVE WITH CHANGES  (name and scope changed; the need is proven)

Recommended name:
  ✅ SETTLED 2026-09-13 (owner): "Basic Medical Sciences" / BASIC_MEDICAL_SCIENCES.
  Lean was A1 on ADR-001:285 — "the coarsest label under which treatment is identical." The boundary
  this audit PROVED (professional role in the knowledge, §4.3) applies unchanged to pathophysiology,
  clinical chemistry and physiology, so A2 would have been narrower than its own evidence;
  ADR-001:307-313 records that the owner DECLINED inverting the rule toward the narrowest tradition.
  The pharmacology concentration (10 of 10 rows) is an AUTHORING-ORDER artifact of PNLE being the set
  in progress — the same reasoning §1 used to resolve PROFESSIONAL_EDUCATION's and NURSING's former
  zero usage.
  Honest cost, accepted along with the decision: this name covers a bundle whose anatomy/microbiology/
  biochemistry members hold ZERO notes today, and ADR-001:109 warns against pre-seeding a vocabulary.
  ⚠️ THE NAME DID NOT CHANGE THE RELEASE'S SIZE — same 6 files either way.
  quantitative: ✅ SETTLED 2026-09-13 (owner): false — accepted ONLY WITH its condition: widening
  QUANTITATIVE_KEYWORDS (:168-176) for pharmacokinetics terms ships in the SAME release, per §15,
  or the pharmacokinetics computation-guidance regression ships unmitigated.

Why the need is approved:
  All five classification tests pass. The failure is live and reproducible, not theoretical:
  `Antibiotics: Mechanism of Action and Resistance` carries domain_context = NURSING across
  Medicine + Nursing + Pharmacy, and DOMAIN_CONSTRAINT (OpenAiLlmStudyPackService.java:122-123)
  then instructs the model that ALL terminology, examples and framing must belong to Nursing —
  on a note whose summary contains no nursing content at all. The committed PNLE plan
  (pnle-comprehensive-review.tsv) assigns NURSING to 10 further rows whose own stated
  applicability is Pharmacy (7), Medicine (2) and Physical Therapy (1). A curator has already
  built a workaround in the catalog: a fused `Nursing · Medicine` program row.
  THE BRIEF'S NAME IS REJECTED: "Biomedical & Clinical Sciences" is INVENTED, and ADR-001:405
  requires names borrowed from real curriculum vocabulary — it already rejected `Health Sciences`
  and `Health Sciences Foundation` on that exact test, and ADR-001:297 explains why that matters:
  "an invented name usually signals there is no real shared body of knowledge behind it." Both
  replacement candidates above are borrowed and neither equals a live catalog program name.
  ⚠️ CLAUSE (a) — ONE FILTER, STATED ONCE: canonical notes (copied_from_note_id IS NULL) whose
  treatment is mechanism-framed shared health science AND which are assigned or firmly planned to
  2+ live health programs = EXACTLY 10 (the rows tabulated in §4.2). All 10 ALREADY EXIST as
  authored production notes (2 `Existing` + 8 `Reuse`), so the NOTE COUNT clears on the "already
  authored" reading; only the MULTI-PROGRAM ASSIGNMENT leans on "firmly planned." That is a
  stronger position than v0.111.0's (241 `New`), and it is still only JUST at the ~10 floor.

Business / Finance candidate:
  DEFER

Recommended name (if it later qualifies):
  "Business & Finance" — the name is fine; the evidence is not.

Why:
  Test A cannot be answered: ACCOUNTANCY has ZERO production usage while all 154 Accountancy
  notes sit at domain_context IS NULL. You cannot show every existing context distorts treatment
  when the nearest one has never been applied to a single note. Test B has zero instances: no
  business note carries 2+ programs; Business Administration's 17 rows are 16 Project Management
  notes already correctly on ENGINEERING_SCIENCES plus ONE real business note; ABM has 3.
  Guard 2 (ADR-001:406) therefore presumes `Business & Finance` a program mirror of ACCOUNTANCY.
  The content cluster IS real (~40 program-neutral finance/managerial notes, §6.2) — volume is
  not the blocker, cross-program applicability is. Cheaper path that ships nothing: classify the
  154 under existing ACCOUNTANCY (curation, not architecture) and run ADR-001:291's own
  generate-under-both-values tie-break. FLIP CONDITION: >=10 program-neutral notes assigned to
  2+ live programs by a real CPALE/BSBA/ABM Review Set plan. Today: 0.
  ⚠️ TRAP: ACCOUNTANCY is quantitative = true, so a wholesale classification would permanently
  switch on computation guidance for the 10 Business Law notes. Classify RFBT separately.

Keep NURSING:     YES — ~200 nursing-role notes depend on it; the boundary is role-in-the-knowledge.
Keep ACCOUNTANCY: YES — and it should finally be USED; its zero usage is this audit's sharpest finding.

Finance Course/Program:
  DEFER
  A real ~13-note financial-management cluster exists, but ADR-001:107-111's trigger (a curator
  judging a note applicable to the program) has NOT fired — every one carries only `Accountancy`.
  Counter-example: Business Administration already exists and has attracted ONE business note.
  Adding Finance now creates a second thin shelf.

Additional Domain Context gaps discovered:
  1. BUSINESS LAW — real, but a DESCRIPTION defect before a taxonomy defect. PPR's ratified
     justification (ADR-001:336, "legal, procedural, regulatory rather than scientific") fits the
     10 RFBT notes and its quantitative=false is correct for them, but its description
     (domain-context.ts:54) enumerates engineering/architecture law only — the v0.99.0 pattern
     recurring. Widen the description; do NOT mint BUSINESS_LAW.
  2. AUDITING — NO gap. Verified against content, not assumed. Do not create Audit/Assurance.
  3. TAXATION — NO gap. Philippine-statute-specific, i.e. NARROWER than Accountancy. Do not create.
  4. OPERATIONS RESEARCH inside MAS (linear programming, decision trees, EOQ) — genuinely fits no
     value. 3 notes, far below floor. Recorded, not proposed.
  5. Shared Anatomy/Physiology/Microbiology — 2 notes total. Below floor. Do NOT pre-seed.

Existing resolver defects discovered:
  NONE. The multi-program gate is enforced at all seven call sites (StudyPackService:131/210/320,
  AdminStudyPackTransactionHelper:65/129, GeneratedQuizService:119) plus both authoring paths that
  create-and-generate (NoteGenerationService:124, NoteBulkGenerationService:452). Curator profile
  fallback is suppressed at :131/:161/:178. Applicable Programs CANNOT reach the LLM — the context
  object has no field able to hold a list. Reported as verified-clean with line numbers.
  ONE CATALOG defect, LATENT NOT LIVE: a fused `Nursing · Medicine` program row (2 notes) would put
  an unsatisfiable two-discipline domain into DOMAIN_CONSTRAINT — but both notes carry a non-null
  Domain Context, so the string is shadowed and never reaches a prompt. It fires only if a Domain
  Context is cleared, and the multi-program gate cannot see it because the join count is 1.
  ONE TITLE finding: the canonical-title doctrine IS implemented (note-generation-developer.txt:18-31,
  shipped 2026-08-29). 92 canonical notes carry container suffixes; 69 predate the rule; of the 23
  after it, most are legitimate (`Civil Engineering Ethics` — the discipline IS the knowledge).
  GENUINE post-rule leakage: 4 notes. Soft compliance, not a broken mechanism.

Migration required immediately:
  NO. notes.domain_context is VARCHAR(64) with ZERO check constraints (verified in production), and
  @Enumerated(EnumType.STRING) persists the name — so no DDL, no Flyway file, no backfill. The entire
  initial reclassification population is 7 notes, and the analysis says only ~1 of them should change
  Domain Context; the other 6 have over-selected Applicable Programs instead. NO title-based migration:
  `Antibiotic Classes in Pharmacology for Nursing` and `Antibiotics: Mechanism of Action and
  Resistance` are near-identical titles with OPPOSITE correct classifications.

Recommended smallest implementation release:
  ⚠️ UPDATED 2026-09-13 — ALL BLOCKING OWNER DECISIONS NOW SETTLED (see §17). Scope below reflects
  the settled name, the settled `quantitative = false` PLUS its required keyword-widening condition,
  and the settled decision to bundle the PPR description fix. This is now kickoff-ready.

  ONE DOMAIN CONTEXT VALUE (`BASIC_MEDICAL_SCIENCES`) + ONE KEYWORD-LIST WIDENING + ONE DESCRIPTION
  FIX. 6 required files for the enum, plus 2 more for the two settled additions. ZERO migrations,
  ZERO backfill, ZERO new prompt files, ZERO resolver-logic changes.
  ⚠️ SIZE, STATED HONESTLY SO A KICKOFF IS NOT MIS-SCOPED: the CODE is ~10-20 lines across items
  1, 3, 4, 5, 7. The WRITING is the real work — item 2 is a multi-sentence curator-facing description
  that must route adjacent material away (the defect that left ~41 Building Utilities notes and 215
  ALE rows unclassified), item 6 is a prose ADR revision-log entry that clause (b) makes
  LOAD-BEARING rather than documentation, and item 8 is the PPR description rewrite that must not
  narrow what already correctly lands there. Size this as "a small code diff plus three prose blocks
  that need real care," NOT as a 20-LOC change.
    1. backend/.../entity/DomainContext.java              — APPEND `BASIC_MEDICAL_SCIENCES("Basic
       Medical Sciences", false)` (never insert; :20-22)
    2. frontend/lib/domain-context.ts                      — one option + the §5 A1 semantic contract
       as the curator-facing description
    3. frontend/lib/api.ts:536-547                          — one union member
    4. backend/.../entity/DomainContextTest.java:24         — label into containsExactly
    5. frontend/lib/domain-context.test.ts:9                — toHaveLength(11) -> (12) + description
       assert
    6. docs/architecture/ADR-001-...md                      — revision-log entry recording the name,
       BASIC_MEDICAL_SCIENCES, quantitative=false, and the QUANTITATIVE_KEYWORDS condition (clause
       (b)'s record)
    7. backend/.../service/impl/OpenAiLlmStudyPackService.java:168-176 — widen QUANTITATIVE_KEYWORDS
       with pharmacokinetics terms (e.g. "pharmacokinetic", "half-life", "clearance") — SETTLED
       CONDITION of the quantitative=false decision; ships in this release, not separately
    8. frontend/lib/domain-context.ts:54                    — widen PROFESSIONAL_PRACTICE_AND_
       REGULATION's description to name Business Law / RFBT material alongside its existing
       engineering/architecture examples — SETTLED, bundled per owner decision 4. §8's validation
       caution (test-classify 2-3 RFBT notes under PPR and read the output before finalizing the
       wording) is the implementer's to satisfy at build time.
    plus RELEASES.md, a new docs/features/domain-context.md, and the GPT_CONTEXT version stamp.
  Route: CLAUDE CODE inline (well under the Codex threshold — no new endpoint, no migration, no
  cross-file backend service change beyond the two named single-purpose edits).
  ⚠️ VERIFICATION TIER: one advisor() call. No new endpoint, so no MockMvc real-request test is owed
  — say that explicitly in the release rather than silently skipping the rule. But the diff DOES
  change behaviour (quantitative fall-through, PPR routing), so tests 4 and 5 must move in the same
  diff (CLAUDE.md's unexercised-change rule), and item 7's keyword widening owes a test asserting a
  pharmacokinetics summary now trips the scan (§16's "Quantitative" row).
  ⚠️ DEPLOY ORDERING: frontend and backend must ship together; run scripts/check-deploys.sh after merge.

Explicitly deferred:
  - Business & Finance Domain Context (evidence, not name)
  - Finance as a Course/Program
  - Health Sciences and Business/Commerce Program Families (no authoring work to reduce)
  - BUSINESS_LAW, AUDITING, TAXATION values (each affirmatively argued against, not merely unproven)
  - The fused `Nursing · Medicine` catalog row (latent; a catalog change with its own risk)
  - Fixing the 4 post-rule leaky titles and the ~69 legacy ones (reported, not fixed, per the brief)
  - Trimming over-selected Applicable Programs on the 6 nursing-role multi-program notes
  - Reclassifying `Antibiotics: Mechanism of Action and Resistance` itself (the 1 note per §15 that
    should move) — this audit recommends it but treats the actual reclassification as a curator/
    content action alongside the release, not a migration the release performs
```

---

## Final architecture statement

> **Programs tell NoteLib *who may legitimately study this knowledge and where it should be
> discoverable*. Domain Context tells NoteLib *in whose professional voice this knowledge must be
> written* — and the whole point of this audit is that, for shared biomedical mechanism, the honest
> answer is "nobody's in particular," which is a treatment NoteLib currently has no way to express.**

**DO NOT IMPLEMENT.**

---

## Housekeeping

**This file needs a row in `ROADMAP.md`'s Backlog Index at the next release kickoff**, per
`CLAUDE.md` kickoff step 8 (every `docs/claude-plans/` file must carry a row). **Not added here** —
this audit does not modify any other file.

**Two sibling files in the same directory need the same row and are also still untracked**, noted
without re-auditing them:

- `docs/claude-plans/note-visibility-learning-status-stage1.md`
- `docs/claude-plans/cross-note-review-consolidation-stage1.md`

**Also worth a row or a correction, discovered incidentally:**

- `docs/claude-plans/domain-context-adoption-read.sql` — named **UNRUN** at
  `v0.111.0-phase-2-taxonomy-calibration.md:186`. §1 of this audit **effectively ran its central
  question**; the `PROFESSIONAL_EDUCATION` and `NURSING` zero-usage assumptions are now **resolved**
  and the `ADR-001:377-378` line asserting three unused values is **stale**.
- `ADR-001:403`'s *"41 programs"* is stale; production holds **51** `[PROD 2026-09-13]`, making the
  governance ratio **11:51 = 0.216** (filter: all rows in `course_programs`, no exclusions).
