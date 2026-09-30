# Domain Context Taxonomy Calibration — Stage 2 Tightened Plan
# Basic Medical Sciences + Accountancy / Business / Finance

**Status: Stage 2 PLAN ONLY. Nothing implemented. No enum value added, no migration, no Note
modified, no prompt changed, no catalog change, no commit.**
**Authored 2026-09-14. Repo state: branch `main` at `22983935`, last closed release `v0.144.0 — No
Backdoor Left` (Released 2026-09-13). No release is currently open.**
**Production reads: READ-ONLY `SELECT` only, via the Render MCP server, per `CLAUDE.md`'s production
rule. Every production-sourced fact is labelled `[PROD 2026-09-13]` — the reads were taken on
2026-09-13 and are ONE DAY STALE as of authoring. The label is the read date, deliberately, not the
authoring date; see the decay note at the end of this document.**

**Predecessor: `docs/claude-plans/domain-context-biomedical-business-calibration-stage1.md`** (1,104
lines, read in full). This document **tightens** that audit; it does not re-derive it. Carried-forward
findings are cited as *"per Stage 1 §X."* New claims carry `file:line` or `[PROD]` evidence.

---

## 0. How this Stage 2 pass was performed, and what it found that Stage 1 could not

Stage 1's blast radius, boundary and semantic contract held up well and are carried forward largely
intact. Two things changed materially, and both were found by **measuring rather than re-reading**:

1. **The `QUANTITATIVE_KEYWORDS` widening — the settled condition attached to owner decision 2 — was
   measured against production and two of its three proposed strings do nothing or actively harm.**
   Stage 1 proposed `"pharmacokinetic"`, `"half-life"`, `"clearance"`. `[PROD]`: `"half-life"` appears
   in **zero** notes anywhere in the corpus; `"clearance"` adds **zero** notes at the tier where the
   regression actually lives and adds **two false positives** at the other tier, one of them on a
   `quantitative = false`-by-decision value. Only `"pharmacokinetic"` works. **§A5.**
2. **The biomedical population grew ~2.6× between Stage 1's read and this one — on the same calendar
   day.** Multi-program canonical Pharmacology notes went from **7 → 18** `[PROD]`, `NURSING` from
   **35 → 46**, and the fused `Nursing · Medicine` catalog row from **2 notes → 20**. Clause (a) is no
   longer *"only just at the floor"*: **six notes now clear it on the strictest reading with no
   *firmly planned* clause at all**, where Stage 1 had zero on that reading. **§A1.**

**One methodological note that decided finding 1, recorded because it generalizes.** The quantitative
keyword scan runs over **two different haystacks depending on the caller**, and only one of them
contains the Study Pack summary:

| Caller | Haystack | Line |
|---|---|---|
| **Quick Review / Study Pack developer prompt** | domain label + subject + tags **only** — `conceptHints` is `List.of()` and `summary` is `null` | `OpenAiLlmStudyPackService.java:293` |
| Challenge · Board Exam · Long Exam · Adaptive Practice · Interview Practice | domain label + subject + tags + key concepts + Study Pack summary | `:741`, `:771`, `:816`, `:850`, `:900` |
| Teacher preview | domain label + subject + tags + tags-again + **note content** | `:930` |

A first pass of this audit measured the keyword widening against the **full** haystack and concluded
it was a no-op. That was wrong, and it was wrong in the direction that would have reversed a settled
owner decision. **The regression lives only at the Quick Review tier**, because that is the only tier
whose haystack is thin enough for a pharmacokinetics note to trip nothing. Re-measured at the correct
tier, the widening is **required**, exactly as the owner accepted. The number that belongs in a plan
is the one taken at the tier where the behaviour occurs.

---
---

# PART A — BASIC MEDICAL SCIENCES

## A1. Revalidated findings

Every Stage 1 claim this plan depends on, re-read against current code and current production.

### A1.1 Repository claims — all held, no corrections

| Stage 1 claim | Status | Current evidence |
|---|---|---|
| 11 enum values, `quantitative` is the 2nd ctor arg | **HELD** | `DomainContext.java:12-32`, ctor `:37-40` |
| `NURSING` is `quantitative = true`; `ACCOUNTANCY` `true`; PPR `false` | **HELD** | `DomainContext.java:18`, `:19`, `:15` |
| Frontend union has 11 members | **HELD** | `frontend/lib/api.ts:536-547` |
| `DOMAIN_CONTEXT_OPTIONS` has 11 entries | **HELD** | `frontend/lib/domain-context.ts:3-94` |
| PPR's description enumerates engineering/architecture law only | **HELD — still the `v0.99.0` defect shape** | `domain-context.ts:54` |
| `effectiveAuthoringDomain` returns the **label**, else the program string | **HELD** | `StudyPackGenerationContextResolver.java:186-194`; consumed at `OpenAiLlmStudyPackService.java:1579-1583` |
| `DOMAIN_CONSTRAINT` is the entire mechanism — one substituted noun | **HELD** | `OpenAiLlmStudyPackService.java:122-123` (the constant), `:1581-1583` (emission) |
| `true` short-circuits; `false` falls through to the keyword scan | **HELD** | `isQuantitativeContext:1643-1675`; short-circuit at `:1644-1646` |
| `QUANTITATIVE_KEYWORDS` is a 48-entry `List.of` | **HELD** | `OpenAiLlmStudyPackService.java:168-176` |
| `fromString` is `valueOf`-based, returns `null` on unknown | **HELD** | `DomainContext.java:42-51` |
| `DomainContextTest` uses `containsExactly` + a `@CsvSource` quantitative matrix | **HELD** | `DomainContextTest.java:22-36` (labels), `:38-57` (`quantitative`) |
| `domain-context.test.ts` asserts `toHaveLength(11)` | **HELD** | `domain-context.test.ts:9` |
| No `domain_contexts` table; `notes.domain_context` is `VARCHAR(64)`, no CHECK | **HELD** `[PROD]` | `information_schema.columns`: `notes.domain_context` = `character varying` |
| Multi-program gate enforced at all seven `assertGenerationReady` sites + both authoring paths | **HELD — carried forward per Stage 1 §3, not re-traced** | Stage 1 §3 lists the ten line numbers |

**Nothing in Stage 1's repository reading has gone stale.** The `v0.144.0` release that shipped between
Stage 1 and this pass touched admin exam-pool invalidation and did not reach any Domain Context file.

### A1.2 Production claims — four moved, and one moved a lot

`[PROD 2026-09-13]`, re-read rather than carried forward, per `CLAUDE.md`'s snapshot rule.

| Fact | Stage 1 | **Now** | Note |
|---|---|---|---|
| Total notes | — | **7,617** | New baseline figure. |
| `domain_context IS NULL` | 5,682 | **5,671** | Ordinary drift. |
| `NURSING` | 35 | **46** (all canonical) | **PNLE authoring is live and moving within the day.** |
| `ACCOUNTANCY` | **0** | **0** | **Unchanged. The calibration window in Part B is still open.** |
| `course_programs` rows | 51 | **51** | Governance ratio **11:51 = 0.216**, far below `ADR-001:409`'s `0.40` reopen threshold. One new value → **12:51 = 0.235**. |
| A `Finance` program exists? | No | **No** — 0 rows matching `%financ%` | Part B §B7 unchanged. |
| Accountancy-program join rows | 154 | **154**, all canonical, **all `domain_context IS NULL`** | Exactly reproduced. |
| Business Administration / ABM / Law join rows | 17 / 3 / 2 | **17 / 3 / 2** | Exactly reproduced. |
| `ENGINEERING_SCIENCES` / `CIVIL_ENGINEERING` / `PROFESSIONAL_EDUCATION` / PPR / `GENERAL_EDUCATION` | 596 / 412 / 232 / 188 / 110 | **identical** | — |
| Architecture trio (`AD` / `AHT` / `PSD`) | 98 / 69 / 33 | **identical** | — |

### A1.3 ⚠️ The one finding that materially changes Workstream A's justification

**Canonical multi-program health-science notes: 7 → 18.** `[PROD]` Canonical notes
(`copied_from_note_id IS NULL`) joined to two or more of `Nursing`, `Medicine`, `Pharmacy`,
`Physical Therapy`, `Radiologic Technology`, `Nursing · Medicine`:

**All 18 are subject `Pharmacology`, all carry `domain_context = NURSING`, and all carry four catalog
rows — `Medicine`, `Nursing`, `Pharmacy` and the fused `Nursing · Medicine`** (three distinct live
programs plus the fused row).

Two further things changed with them, both visible only because the titles were re-read:

- **The curator has been stripping `in Nursing` container suffixes from these titles.** Stage 1 quoted
  `Medication Administration Rights in Nursing`, `Safe Medication Practices in Nursing`,
  `Pharmacology Core Drug Classes and Nursing Responsibilities`, `High Alert Medications in Nursing
  Pharmacology`, `Opioid Medications and Safety Precautions in Nursing Practice` and
  `Respiratory and Gastrointestinal Pharmacology for Nursing`. `[PROD]` they now read
  `Medication Administration Rights`, `Safe Medication Practices`, `Pharmacology Core Drug Classes and
  Responsibilities`, `High Alert Medications`, `Opioid Medications and Safety Precautions` and
  `Respiratory and Gastrointestinal Pharmacology`. **Stage 1 §13's "4 genuine post-rule leaky titles"
  is therefore already down to at most 3** — one of its four named cases has been fixed by the curator
  since. Reported as decay in Stage 1's own finding, not as new work.
- **The fused `Nursing · Medicine` catalog row now carries 20 notes, not 2** `[PROD]` — the 18
  Pharmacology notes plus Stage 1's 2 `Clinical Chemistry` notes. It is the **only** fused row in the
  catalog (one row matching `%·%`). **It remains LATENT**, exactly as Stage 1 argued: all 20 carry a
  non-null `domain_context`, so `effectiveAuthoringDomain` (`:190-192`) returns the label and the
  fused string is shadowed. **But its blast radius grew 10× in a day**, which is a reason to sequence
  §A12's deferral sooner rather than a reason to pull it into this release.

**Why this strengthens clause (a) rather than merely enlarging it.** `ADR-001:397` clause (a) asks for
*"~10 or more notes already authored **or firmly planned**"* whose treatment no existing value
represents. Stage 1 reported exactly 10 and was scrupulous that **all 10 leaned on *firmly planned*
for the multi-program half** (§4.2, §C). That is no longer the position. Applying the §A2 boundary to
the 18 notes' **actual summaries** `[PROD]` — not their titles — gives:

| Verdict by the role test | Count | Notes |
|---|---|---|
| **Mechanism-framed → `BASIC_MEDICAL_SCIENCES`** | **6** | `Antibiotic Classes in Pharmacology` · `Antibiotics: Mechanism of Action and Resistance` · `Pharmacological Management of Diabetes` · `Pharmacological Management of Hypertension: Antihypertensive Drugs` · `Pharmacology of Insulin: Types and Their Peak Action Times` · `Respiratory and Gastrointestinal Pharmacology` |
| **Role-framed → stays `NURSING`** | **11** | `Adverse Drug Reactions in Clinical Nursing Practice` · `Common Drug Toxicities` · `Comparison of Common Anticoagulants` · `High Alert Medications` **(×2 — duplicate)** · `Medication Administration Rights` · `Pharmacological Approaches to Pain Management in Nursing` · `Pharmacology and Clinical Application of Anticoagulants in Nursing Practice` · `Pharmacology Core Drug Classes and Responsibilities` · `Psychiatric Pharmacology: Major Drug Classes and Responsibilities` · `Safe Medication Practices` |
| **Not read this pass** | 1 | `Opioid Medications and Safety Precautions` — title suggests role-framed; **classify, do not assume** |

**⚠️ The clause-(a) count of 6 is safe whether or not the unread note qualifies.** If
`Opioid Medications and Safety Precautions` turns out mechanism-framed the mechanism column becomes 7,
not 5 — the unread note can only *raise* the count. **6 is therefore a floor, and it is the number
this plan publishes.** Stated explicitly so a reviewer checking the arithmetic does not find an
unresolved note inside a load-bearing tally and have to re-derive it.

**So clause (a) now stands at 6 notes that are *already authored* AND *already assigned to 2+ live
programs*, plus the ~9 PNLE-planned rows from Stage 1 §4.2 that remain single-program pending Review
Set entry — call it ~15.** Stage 1 had **zero** on the strict reading. **The candidate no longer sits
"only just at the floor," and no part of its case now depends on a plan file.**

⚠️ **Six of these 18 are being actively mis-instructed right now, not one.** Stage 1's headline live
defect (`Antibiotics: Mechanism of Action and Resistance` generating under `Domain: Nursing` with zero
nursing content) is **one instance of six**. The summaries confirm it: `Pharmacological Management of
Hypertension` reads *"reduce blood pressure through various pharmacological mechanisms including
lowering systemic vascular resistance, cardiac output"* — no professional role anywhere — while
`DOMAIN_CONSTRAINT` instructs the model that *all* content, terminology, examples and question framing
must belong to Nursing. The eleven role-framed notes are correctly `NURSING`; their defect is
**over-selected Applicable Programs**, which is §A11's curator work, not this release's.

### A1.4 ⚠️ Stage 1 §15's keyword table is wrong on two of its four rows

This is the correction that drives §A5, and it was found by replicating `isQuantitativeContext`'s
haystack in SQL and running it against real content rather than eyeballing summaries.

| Note | Stage 1 §15 said | `[PROD]` **Quick Review tier** | `[PROD]` **full tier** |
|---|---|---|---|
| `Dosage Calculations and Concentration Problems` | Yes — *"units", "formula"* | **`ratio`, `unit conversion`** | `analysis`, `formula`, `ratio`, `unit conversion`, `units` |
| `Common Laboratory Values` | Yes — *"chemistry", "balance"* | **`chemistry`** | `balance`, `chemistry` |
| `Antibiotics: Mechanism of Action and Resistance` | **"No" — *"correctly, it is not computational"*** | **⚠️ `resistance`** | ⚠️ `ratio`, `resistance` |
| `Pharmacokinetics: ADME` | **"Apparently no" — *"⚠️ This is the decision"*** | **`(NONE)` — correct** | **⚠️ `formula`, `ratio`** |

Both errors come from the same cause, and it is worth naming because it explains why the scan is
unreliable in *both* directions: **`isQuantitativeContext` uses `String.contains`, so every keyword
matches unanchored substrings.** Proved directly `[PROD]`:

| Keyword | Matches inside | |
|---|---|---|
| `ratio` | `corporation`, `operations`, `administration`, `concentration` | ✅ all true |
| `solve` | `resolve` | ✅ |
| `current` | `currently` | ✅ |
| `interest` | `interested parties` | ✅ |
| `integral` | `an integral part of` | ✅ |
| `balance` | `balance sheet` | ✅ |
| `units` | `business units` | ✅ |

`ratio` ⊂ `administration` is why **9 of the 10** §A4 candidate notes trip `ratio` at the full tier:
any pharmacology note mentioning drug *administration* is scored quantitative. `resistance` (an
electrical keyword) is why the antibiotics-resistance note trips at **both** tiers.

**⚠️ This is reported and explicitly NOT scoped.** See §A12 and the Housekeeping note. Word-boundary
matching would flip computation guidance on an unknown share of the **4,874** notes `[PROD]` that are
currently quantitative *via keywords only*, permanently per note, and brief §11 says *prefer the
smallest coherent solution* while §23 forbids a resolver rewrite. **It also couples to §A5**: the
recommended keyword works *because* matching is unanchored — see the warning there.

---

## A2. Locked product decision

All three owner decisions from Stage 1 §17 were re-verified against current code. **Two stand exactly
as settled. The third stands, with its companion condition's STRINGS corrected and its RATIONALE
corrected — the decision itself is not reopened.**

**Name (display label):**

> ## Basic Medical Sciences

**Enum value:**

> ## `BASIC_MEDICAL_SCIENCES`

**Quantitative:**

> ## `false`

**Status of the three settled decisions after re-verification:**

| # | Settled decision (Stage 1 §17) | Re-verification verdict |
|---|---|---|
| 1 | Name `Basic Medical Sciences` / `BASIC_MEDICAL_SCIENCES` | **STANDS, and is better supported than when settled.** The coarsest-label rule (`ADR-001:285`) was the basis; §A1.3's six mechanism notes span antibiotics, antihypertensives, antidiabetics, insulin pharmacokinetics and respiratory/GI pharmacology — broader than any pharmacology-scoped name would hold comfortably, and the boundary reaches `Pain, Inflammation, and Tissue Healing` (pathophysiology) and `Common Laboratory Values` (diagnostic parameters) unchanged. Borrowed vocabulary, does not equal a live catalog program name `[PROD]`. **No contradiction.** |
| 2 | `quantitative = false`, conditional on widening `QUANTITATIVE_KEYWORDS` in the same release | **THE DECISION STANDS. THE CONDITION'S THREE PROPOSED STRINGS DO NOT — two of three are measurably wrong, and the stated rationale is vacuous.** `false` is now *more* clearly right than when settled. Full measurement and the corrected single-string recommendation: **§A5.** |
| 3 | Widen PPR's description to cover Business Law / RFBT, same release | **STANDS.** `domain-context.ts:54` is unchanged and still enumerates engineering/architecture law only. **Two tightenings required before it ships:** it must route **Taxation** away or it becomes the catch-all brief §16 forbids, and **nothing currently tests PPR's description**, so the change would ship unexercised. **§A6, §A7, §A9.** |

**Boundary (locked, per Stage 1 §4.3 — validated against 18 real summaries this pass rather than 7):**

> **Does a professional role appear in the knowledge itself, or only in who is reading it?**
>
> - Role **in the knowledge** → `NURSING`.
> - Role **only in the audience** → `BASIC_MEDICAL_SCIENCES`.

Audience never decides. A note being inside PNLE does not make it `NURSING`; a note being applicable
to Nursing does not make it `NURSING`. **The 18-note classification in §A1.3 is this boundary applied
to real content, and it separated them 6/11/1 without a single hard case** — the strongest available
evidence for `ADR-001` Test D, and stronger than Stage 1's, which had 7 notes to work with.

---

## A3. Final semantic contract

The exact recommended curator-facing description for `frontend/lib/domain-context.ts`. Written against
the real prompt architecture: it is substituted into `Domain: <label>` at
`OpenAiLlmStudyPackService.java:1581` and must make `DOMAIN_CONSTRAINT` (`:122-123`) true and useful.

**Changes from Stage 1 §5's draft, and why:** Stage 1's draft opened *"Foundational health-science
knowledge"*; the brief's §5 wording says *"Foundational biomedical knowledge"*, which is tighter and
avoids colliding with `Health Sciences` — a name `ADR-001:405` has already rejected twice. Stage 1's
bracketed pharmacology enumeration is trimmed to read as prose rather than a list, and the third
routing sentence is added to match the brief. **The routing sentences are load-bearing**, not
decoration: a description that enumerates without routing adjacent material away is the defect that
left ~41 Building Utilities notes and 215 ALE rows unclassified (`domain-context.ts:12-20`, `:76-78`).

```
Foundational biomedical knowledge shared across health professions: drug action and therapeutic
mechanisms, pharmacokinetics and pharmacodynamics, drug classes, mechanisms of resistance and
interaction, disease mechanisms and pathophysiology, normal physiology, and shared laboratory or
diagnostic parameters. Use biomedical mechanisms and standard scientific terminology without
assuming a specific professional role. Knowledge whose subject is the nursing role itself —
medication administration rights, nursing assessment and monitoring duties, prioritization, or
nursing documentation — belongs in Nursing. Licensure, codes, ethics, and professional regulation
belong in Professional Practice & Regulation.
```

Three properties a reviewer should check, because each is the reason a clause is present:

1. **It names the six §A1.3 mechanism notes' content** — antibiotic classes and resistance,
   antihypertensive and antidiabetic mechanisms, insulin onset/peak/duration, respiratory and GI drug
   classes — so a curator holding any of them selects this value.
2. **It reaches past pharmacology** — pathophysiology, normal physiology, laboratory parameters — which
   is what makes the coarser name honest and what lets the role test classify `Pain, Inflammation, and
   Tissue Healing` and `Common Laboratory Values` at all.
3. **It routes in two directions**, to `Nursing` and to `Professional Practice & Regulation`. The
   second matters more than it looks: without it, nursing **law** and licensure material has two
   plausible homes.

---

## A4. Boundary table

Real production notes `[PROD 2026-09-13]` and real committed plan rows
`[TSV: docs/curriculum/pnle-comprehensive-review.tsv]`. **Nothing here is reclassified — this is a
recommendation table.** Reasons cite actual summary content.

### Clearly `BASIC_MEDICAL_SCIENCES`

| Note | Current DC | Programs | Why — from the summary |
|---|---|---|---|
| `Antibiotics: Mechanism of Action and Resistance` | **`NURSING`** | Med, Nur, Pha (+fused) | *"Beta-lactams inhibit cell wall cross-linking… beta-lactamase, efflux pump activity."* No role. **Live mis-instruction.** |
| `Antibiotic Classes in Pharmacology` | **`NURSING`** | Med, Nur, Pha (+fused) | *"categorized based on chemical structure, mechanism of action, and spectrum of bacterial activity."* No role. |
| `Pharmacological Management of Hypertension: Antihypertensive Drugs` | **`NURSING`** | Med, Nur, Pha (+fused) | *"reduce blood pressure through various pharmacological mechanisms including lowering systemic vascular resistance, cardiac output."* No role. |
| `Pharmacological Management of Diabetes` | **`NURSING`** | Med, Nur, Pha (+fused) | *"insulin and oral antidiabetic agents… classified by onset and duration."* Therapeutic mechanism. |
| `Pharmacology of Insulin: Types and Their Peak Action Times` | **`NURSING`** | Med, Nur, Pha (+fused) | *"onset, peak, and duration… within 10 to 30 minutes and peak within 0.5 to 3 hours."* Pure pharmacokinetics, **and quantitative**. |
| `Respiratory and Gastrointestinal Pharmacology` | **`NURSING`** | Med, Nur, Pha (+fused) | *"bronchodilators such as beta-2 agonists, corticosteroids, mucolytics."* Drug classes, no role. |
| `Pharmacokinetics: Drug Absorption, Distribution, Metabolism, and Excretion` | `NULL` | Pha `[TSV → PNLE]` | *"first-pass metabolism… bioavailability… protein binding."* Identical for all three professions. |
| `Pharmacodynamics: Agonists, Antagonists, and Receptors` | `NULL` | Pha `[TSV → PNLE]` | *"competitive antagonists… noncompetitive antagonists, which bind allosteric sites."* Receptor theory. |
| `Drug Interactions and Contraindications` | `NULL` | Pha `[TSV → PNLE]` | *"cytochrome P450 enzyme system… enzyme induction or inhibition."* Mechanism-framed. |
| `Dosage Calculations and Concentration Problems` | `NULL` | Pha `[TSV → PNLE]` | *"Desired Dose = (Available Dose / Available Volume) × Volume to Administer."* Arithmetic is role-independent. |

### Clearly `NURSING` — the value is kept, unchanged

| Note | Why the role **is** the knowledge |
|---|---|
| `Medication Administration Rights` | *"a fundamental safety framework **in nursing**… **Nurses use** a systematic checklist."* |
| `Safe Medication Practices` | *"essential **in nursing** to prevent medication errors."* |
| `Common Drug Toxicities` | *"**In nursing pharmacology**, recognizing these toxicities is essential."* |
| `Comparison of Common Anticoagulants` | *"**Nursing professionals must understand** distinctions… administration methods, monitoring needs."* |
| `High Alert Medications` (×2) | *"require strict **nursing oversight** during prescription, preparation."* |
| `Pharmacology Core Drug Classes and Responsibilities` | *"core drug classes and associated **nursing responsibilities**."* |
| `Psychiatric Pharmacology: Major Drug Classes and Responsibilities` | *"each requiring distinct **nursing considerations**."* |
| `Adverse Drug Reactions in Clinical Nursing Practice` | *"**Nurses are integral to early detection** by monitoring patients, documenting ADRs."* |
| `Pharmacological Approaches to Pain Management in Nursing` · `Pharmacology and Clinical Application of Anticoagulants in Nursing Practice` | Role named in the title and carried through the content. |
| **~200 `Medical–Surgical` / `Psychiatric` / `Pediatric` / `Maternal & Child` / `Fundamentals` / `Community Health` notes** | **Always. This is the bulk of the corpus and the new value must not touch it** — it is why `NURSING` is kept. |

### Ambiguous — marked, not forced

| Note | Why | Handling |
|---|---|---|
| `Opioid Medications and Safety Precautions` | **Not read this pass.** Title suggests role-framed; the de-suffixing in §A1.3 means the title is no longer reliable evidence either way. | **Classify by reading the summary. Do not assume from the title.** |
| `Common Laboratory Values` · `Common Normal Laboratory Values` | Knowledge (Hgb 13.8–17.2 g/dL, Na⁺ 135–145 mEq/L) is universal; prose is nurse-framed (*"used by nurses to evaluate patient health status"*). Both on the fused row. | **`BASIC_MEDICAL_SCIENCES` is in scope for them under this name** (diagnostic parameters). The framing, not the knowledge, is what currently reads as nursing — a **content** fix, not a taxonomy one. |
| `Medication Safety and Patient Counseling` | Counselling is a **pharmacist's** professional act, so the role test points at a third value that does not exist. | Leave as authored. **Do not force.** `ADR-001:295` — an honest NULL beats a catch-all. |
| `Pain, Inflammation, and Tissue Healing` | *"inflammation, proliferation, and remodeling"* — shared pathophysiology, authored for Physical Therapy. | **In scope under this name**; it is the clearest case that the coarser name was the right choice. |

### Belongs elsewhere

| Material | Value | Why |
|---|---|---|
| 6 Water Treatment TSV rows | `ENGINEERING_SCIENCES` | Already correct; `Excluded` in the PNLE plan. |
| 3 nursing-law TSV rows | `PROFESSIONAL_PRACTICE_AND_REGULATION` | Already correct. |
| 5 Physical Therapy kinesiology / exercise notes | **`NULL`** | Single-program, thin, all `Excluded` in the plan. The program-name fallback serves them correctly. |
| Anatomy · Microbiology · Biochemistry · Nutrition | **N/A — zero notes exist** `[PROD]` | **Do not pre-seed** (`ADR-001:109`). The name covers them; the corpus does not yet contain them, and that cost was accepted with the name. |

---

## A5. Quantitative keyword change

**This section replaces Stage 1 §15's proposal. The owner's decision (`quantitative = false`) is not
reopened; the condition attached to it is corrected on measured evidence.**

### A5.1 What the flag actually does — brief §11 questions 1 and 2

`quantitative = true` short-circuits `isQuantitativeContext` (`:1644-1646`), which feeds
`buildComputationGuidance` (`:1498-1520`), which is substituted as `{COMPUTATION_GUIDANCE}` into
**seven quiz-mode developer prompts** (`:293`, `:748`, `:777`, `:824`, `:857`, `:909`, `:937`). It
reaches **no other surface** — not note generation, not the static Study Pack body.

`false` is a **no-op**: it falls through to the keyword scan. It does not suppress anything.

Two properties of the emitted guidance matter for every judgment below:

1. **It is hedged.** Every mode's string is conditional — *"Include computation… **when appropriate**"*,
   *"when the notes support them"*, *"**only when** the notes clearly support them"* (`:1503-1510`).
   A false positive is therefore a **soft** cost, not a distortion.
2. **It is not only prose.** For every mode except `BOARD_EXAM` it appends the `questionType` /
   `workingSolution` / LaTeX contract (`:1520`). That is a real output-shape change, so a false
   positive is soft but not free.

### A5.2 The measurement — net-new trips at the tier where the regression lives

`[PROD 2026-09-13]`. Method: replicate `isQuantitativeContext`'s haystack in SQL at the **Quick Review
tier** (`domain label + subject + tags`, per `:293`), and count notes that are **not already
quantitative** — i.e. their Domain Context is not one of the five `true` values **and** they trip none
of the 48 existing keywords. That is the only population a new keyword can change.

Baseline: **4,827** notes are non-quantitative at the Quick Review tier.

| Candidate string | Net-new at QR tier | Canonical / copies | Verdict |
|---|---|---|---|
| **`"pharmacokinetic"`** | **17** | **2 canonical** + 15 copies of one note | ✅ **ADD — the only string that works** |
| `"pharmacodynamic"` | 2 **(only 1 unique** — `Drug Interactions` is already covered by `"pharmacokinetic"`**)** | 2 canonical | ❌ **REJECT — see A5.4** |
| `"half-life"` | **0** | — | ❌ **REJECT — the string appears in ZERO notes anywhere in the corpus**, at any tier |
| `"clearance"` | **0** | — | ❌ **REJECT — and it is actively harmful; see A5.3** |
| `"dosage"` | 0 | — | ❌ Redundant — every match already trips |
| `"bioavailability"` | 0 | — | ❌ 1 note corpus-wide, already trips |
| `"concentration"` | 0 | — | ❌ Redundant, and 87 matches corpus-wide make it dangerous |
| `"titration"` · `"pharmaceutical calculation"` | 0 | — | ❌ No effect |

**The two canonical notes `"pharmacokinetic"` newly reaches are exactly the right ones:**

- **`Pharmacokinetics: Drug Absorption, Distribution, Metabolism, and Excretion`** — *the note Stage 1
  §15 named as "⚠️ This is the decision."* Genuinely computational (half-life, clearance, volume of
  distribution, bioavailability), and `(NONE)` at the Quick Review tier today.
- `Drug Interactions and Contraindications` — mechanism-framed and only mildly computational; a
  **soft** over-application, and the hedged guidance text absorbs it.

The 15 copies are learner copies of `Pharmacology of Insulin: Types and Their Peak Action Times`,
whose canonical is already `NURSING(true)`. They are genuinely quantitative (*"peak within 0.5 to 3
hours"*), so reaching them is correct, but **do not report 17 as if it were 17 distinct notes.**

### A5.3 ⚠️ `"clearance"` must not ship — it is a live false positive

`[PROD]` `"clearance"` matches **77** notes corpus-wide, spanning `ARCHITECTURAL_DESIGN`,
`CIVIL_ENGINEERING`, `ENGINEERING_SCIENCES`, `PROFESSIONAL_PRACTICE_AND_REGULATION` and `NULL`. It is
**building** clearance — ceiling clearance, accessibility clearance, clearance from a property line —
far more often than renal clearance.

Net-new effect: **0 at the Quick Review tier**, and **+2 at the full tier — both false positives**, one
`NULL` and one on **`PROFESSIONAL_PRACTICE_AND_REGULATION`**, a value whose `false` is a documented
decision defended in a nine-line comment at `domain-context.ts:48-53` and pinned by
`DomainContextTest`'s `@CsvSource`. **Adding `"clearance"` would quietly begin flipping PPR notes to
quantitative** — precisely the harm that comment exists to prevent, and permanent per note.

Stage 1 wrote *"e.g."* before these strings and the brief says *"Do not blindly use exactly these
strings."* **This is that check returning a result.**

### A5.4 Why `"pharmacodynamic"` is rejected despite hitting a candidate note

It newly reaches `Pharmacodynamics: Agonists, Antagonists, and Receptors` and
`Drug Interactions and Contraindications` — the latter already covered by `"pharmacokinetic"`, so its
unique contribution is **one note**. And that note is **not computational**: *"competitive
antagonists… noncompetitive antagonists, which bind allosteric sites"* is receptor theory. Adding it
would hand computation guidance to a conceptual note, which is the exact error the
`quantitative = false` philosophy exists to avoid. **One string, one note, wrong direction — reject.**

### A5.5 ⚠️ The condition's stated RATIONALE is vacuous, and the plan should say so plainly

Owner decision 2's condition was accepted on Stage 1 §15's reasoning: `false` *"causes a known
regression on a named note class — pharmacokinetics content moving off `NURSING` (`true`) would lose
computation guidance it has today."*

**`[PROD]` that regression affects zero notes.** Only **3** of the 10 §A4 candidates carry `NURSING`
today, and **all three trip existing keywords at BOTH tiers**: `Antibiotics: Mechanism of Action and
Resistance` on `resistance`, and `Common Laboratory Values` / `Common Normal Laboratory Values` on
`chemistry`. The other seven are `NULL` today, so they already resolve through the program-name
fallback and are already non-quantitative — **moving them to `BASIC_MEDICAL_SCIENCES(false)` changes
their quantitative outcome not at all.**

**The correct framing, which the release note must use:** the widening is **not** a regression
mitigation. It closes a **pre-existing** gap — genuinely computational pharmacokinetics material gets
no Quick Review computation guidance today, and would not under either flag value. **Still ship it, in
the same release, exactly as the owner conditioned** — the condition is satisfied, and shipping it
alongside the new value is the only moment anyone will look at this code. But **do not write it up as
preventing a regression**, because a reader checking that claim will find no regression to prevent.
That is the `v0.116.0` / `v0.117.0` failure mode — an item recorded as shipped with a named
user-visible consequence that did not exist.

### A5.6 ⚠️ The recommended string depends on the substring defect, so it is coupled to §A12

`"pharmacokinetic"` reaches `Pharmacokinetics: ADME` **because** matching is unanchored — the subject
is `Pharmacokinetics` and `contains("pharmacokinetic")` matches it. **If the substring defect in §A12
is ever fixed with word boundaries, this entry silently stops working** and must become
`pharmacokinetics` or a `pharmacokinetic(s)?` pattern. **Record this coupling in the
`QUANTITATIVE_KEYWORDS` comment beside the new entry** — it costs one line now and is invisible later.

### A5.7 Final recommendation

**Add exactly one string to `QUANTITATIVE_KEYWORDS` (`OpenAiLlmStudyPackService.java:168-176`),
alphabetically between `"physics"` and `"probability"`:**

```java
"pharmacokinetic",
```

**Reject `"half-life"`, `"clearance"`, `"pharmacodynamic"`, `"dosage"`, `"bioavailability"`,
`"concentration"` and `"titration"`, each on the measurement in §A5.2.** False-positive risk of the
single recommended string: **low and bounded** — `"pharmacokinetic"` has no non-pharmacological
meaning, and all 17 production matches are pharmacology notes.

---

## A6. PPR description

**Current** (`frontend/lib/domain-context.ts:54`, unchanged since `v0.99.0`):

> *"Codes, laws, ethics and licensure shared across programs: Engineering Laws, Ethics and Contracts;
> Professional Practice; Building Laws including the National Building Code and BP 344 accessibility;
> and Construction Safety."*

The defect stands as Stage 1 §8 diagnosed it: this **enumerates a narrower value than it names**, so a
curator authoring CPALE Business Law would never select it — the `v0.99.0` pattern recurring verbatim.

### Recommended tightened description

```
Codes, laws, ethics, licensure and regulatory frameworks shared across programs, where the
treatment is legal, procedural and regulatory rather than scientific or computational:
professional practice and ethics; engineering laws and contracts; building laws including the
National Building Code and BP 344 accessibility; construction safety; and commercial and business
law such as obligations and contracts, partnerships and corporations, sales, credit transactions,
negotiable instruments, insurance and labour regulation. Material where a statute governs a
computation or an accounting treatment — tax computation, or recognition and measurement under an
accounting standard — belongs in Accountancy. Scientific or engineering mechanisms governed by a
code belong in Engineering Sciences.
```

### How it covers the three required scopes without becoming a catch-all

| Scope | Covered by | Guard against over-reach |
|---|---|---|
| **Engineering / architecture regulation** | *"engineering laws and contracts; building laws including the National Building Code and BP 344 accessibility; construction safety"* — **every existing clause is preserved verbatim in substance.** | Final sentence routes code-governed **mechanisms** (HVAC, plumbing, structural) back to `ENGINEERING_SCIENCES`, matching `domain-context.ts:82`'s existing routing shape. |
| **Professional regulation** | *"professional practice and ethics… licensure"* | Unchanged from today. |
| **Business Law / RFBT** | *"commercial and business law such as obligations and contracts, partnerships and corporations, sales, credit transactions, negotiable instruments, insurance and labour regulation"* — these are the **actual subjects of the 10 production RFBT notes** `[PROD]`, not invented examples. | — |

**⚠️ The Taxation routing sentence is not optional, and this is a Stage 2 tightening the brief's own
§16 implies but does not state.** §B4 assigns Taxation (~19 notes, all Philippine-statute-specific) to
`ACCOUNTANCY`. Tax notes **are** statutory — `Republic Act No. 7160`, BIR administration, the ₱3M VAT
threshold — so a PPR description reading *"legal, procedural and regulatory material including business
law"* is a magnet for every one of them. **Without the routing sentence, widening PPR silently
re-homes ~19 Taxation notes**, which is exactly the *"anything legal"* catch-all the brief forbids and
a repeat of what widening `ENGINEERING_SCIENCES` did in `v0.99.0` (`domain-context.ts:48-53`
documents that as a *known* consequence of widening).

**`quantitative` stays `false`** and is unchanged by this edit. It is **correct** for RFBT and is a
second, independent argument for routing RFBT here rather than sweeping it into `ACCOUNTANCY(true)` —
see §B3.

---

## A7. RFBT validation

**Required before the §A6 wording is final.** Stage 1 §8 called this its least-certain recommendation
and asked for it explicitly; the owner accepted decision 3 *with* that caution attached.

**Use the existing runbook.** `docs/claude-prompt/canonical-knowledge-architecture-out/17-r4-verification-runbook.md`.
`ADR-001:330` is explicit: **do not write a second evaluation rubric.** `ADR-001:291` is the tie-break
this exercise instantiates — *generate the note under both candidate values and compare the output.*

**The three representative Notes, chosen to span RFBT's actual range** `[PROD 2026-09-13]` — all three
are canonical, `Accountancy`-only, `domain_context IS NULL`, subject `Regulatory Framework & Business
Law`:

| # | Note | Why this one |
|---|---|---|
| 1 | `Corporation Code in Philippine Business Law` | **Statutory doctrine** — *"Republic Act No. 11232… juridical entities separate from their stockholders."* The purest legal-prose case. ⚠️ Note it already trips `interest` and `ratio` **only because `ratio` ⊂ `corporation`** (§A1.4) — so it is currently, accidentally, quantitative. |
| 2 | `Obligations and Contracts in Accountancy's Regulatory Framework` | **Doctrinal core of RFBT**, and the case most likely to reveal whether PPR's engineering-contract framing distorts civil-law obligations. |
| 3 | `Negotiable Instruments in Accountancy and Business Law` | **The hard case on purpose** — commercial paper sits closest to accounting treatment, so if PPR distorts anywhere it distorts here. |

**Comparison arms** (per `ADR-001:291`, two arms only):

- **Arm A — current behaviour.** `domain_context = NULL`, single program → `resolveCourseProgram`
  (`:143-145`) returns the catalog string, so the prompt emits `Domain: Accountancy`.
- **Arm B — proposed.** `domain_context = PROFESSIONAL_PRACTICE_AND_REGULATION` → `:190-192` returns
  the label, so the prompt emits `Domain: Professional Practice & Regulation`.

**Do NOT test an `ACCOUNTANCY` arm.** It is `quantitative = true`, so it confounds the domain variable
with computation guidance and the result would not be single-variable. Keep it out.

**Pass condition:** Arm B preserves statutory citations (RA numbers, PD 442), legal terminology and
doctrinal structure, and does not import engineering-contract or construction framing. **Fail
condition:** Arm B genericizes the legal content or reframes it toward engineering practice.

**If it fails, ship the `BASIC_MEDICAL_SCIENCES` value WITHOUT the PPR description change.** The two
are independent edits to different array entries; they were bundled by owner decision 3 for
convenience, not by any technical coupling. **Report the failure before shipping, and say in the
release that decision 3 is blocked by validation** — do not widen the description and hope.

⚠️ **This validation is curator/content work performed against production data by the owner, not
something the implementing session executes** — Arm B requires *setting* a Domain Context on a
production note, which is a write. **Hand the owner the two-arm design above; Claude does not run it.**

---

## A8. Exact implementation blast radius

**This section is the routing decision for Workstream A.** Stage 1 §14's list was re-verified
file-by-file; it was correct, and it is sharpened here with current line numbers and two additions it
missed (`DomainContextTest`'s method **name**, and the absent PPR description test).

### MUST CHANGE (8)

| # | File | Exact change | Why load-bearing |
|---|---|---|---|
| 1 | `backend/src/main/java/com/studysnap/backend/entity/DomainContext.java` | **Append** `BASIC_MEDICAL_SCIENCES("Basic Medical Sciences", false)` after `:32` (`PLANNING_AND_SITE_DEVELOPMENT`), with a comment recording the owner date and the `quantitative = false` decision. | **APPEND, NEVER INSERT** — `:20-22` says exactly this, and `DomainContextTest` uses `containsExactly`. `@Enumerated(EnumType.STRING)` persists the NAME, so ordinal carries no data. |
| 2 | `frontend/lib/domain-context.ts` | **Append** one `DOMAIN_CONTEXT_OPTIONS` entry (after `:93`) with §A3's description verbatim. | The description **is** the curator's only guidance. Must route adjacent material away. |
| 3 | `frontend/lib/domain-context.ts:54` | **Replace** PPR's description with §A6's. | Owner decision 3. **Gated on §A7 passing.** |
| 4 | `frontend/lib/api.ts:536-547` | Add `\| "BASIC_MEDICAL_SCIENCES"` to the `DomainContext` union. | Without it TypeScript rejects the value everywhere; every DTO type keys off this union. |
| 5 | `backend/.../service/impl/OpenAiLlmStudyPackService.java:168-176` | Add `"pharmacokinetic"` to `QUANTITATIVE_KEYWORDS` + the §A5.6 coupling comment. | Owner decision 2's companion condition, with §A5's corrected string. |
| 6 | `backend/src/test/java/com/studysnap/backend/entity/DomainContextTest.java` | `:23` **rename** `valuesExposeTheElevenRatifiedLabels` → `...TwelveRatifiedLabels`; `:24-35` add `"Basic Medical Sciences"` to `containsExactly`; `:39-53` add `"BASIC_MEDICAL_SCIENCES, false"` to `@CsvSource`. | Fails otherwise. **⚠️ The method NAME is part of the change** — Stage 1 listed only the `containsExactly` list, and a method called `...Eleven...` asserting twelve labels is the kind of stale-in-place artifact `CLAUDE.md` warns about. |
| 7 | `frontend/lib/domain-context.test.ts` | `:9` `toHaveLength(11)` → `(12)`; add the new value's routing assertions **and a PPR routing assertion** (§A9). | Fails otherwise on `:9`. **⚠️ The PPR assertion is NEW and mandatory — see §A9.** |
| 8 | `docs/architecture/ADR-001-canonical-knowledge-architecture.md` | Revision-log entry after the `v0.111.0` amendment (`:346-359`): name, enum, `quantitative = false`, the keyword condition as actually shipped, clause-(a) evidence, the updated governance ratio **12:51 = 0.235**. | **`ADR-001:397` clause (b) — this IS the owner's decision record. The release is not complete without it.** |

### SHOULD CHANGE (4) — correctness of record

| # | File | Change |
|---|---|---|
| 9 | `RELEASES.md` | Bullets under the open version. **Must state: `quantitative = false` prevents no regression (§A5.5); no MockMvc test is owed and why (§A9); deploy ordering (§A10).** |
| 10 | `docs/features/domain-context.md` | **Does not exist** — `ls docs/features/` confirms no Domain Context feature doc among the 48. This release adds a value, a description and a keyword; a doc is arguably owed and is **the one genuinely discretionary item**. |
| 11 | `docs/gpt-contexts/GPT_CONTEXT.md` | Version stamp. |
| 12 | `ADR-001:377-378` and `:403` | **Two stale lines Stage 1 already identified.** `:377-378` says *"three unused values — Professional Education, Nursing, Accountancy"* — `[PROD]` two are in use (232 and 46). `:403` says *"41 programs"* — `[PROD]` **51**. Correct in the same commit as item 8; the ADR is being edited anyway. |

### NO CHANGE — each verified this pass, not assumed

| Area | Why not |
|---|---|
| **Database migration** | **NONE.** `[PROD]` `notes.domain_context` is `character varying` with no CHECK constraint; `@Enumerated(EnumType.STRING)` writes the name. **No DDL, no Flyway file.** |
| **Backfill** | **NONE.** No existing row changes value. §A11 is curator work. |
| `StudyPackGenerationContextResolver` | Enum-agnostic — calls `getLabel()` (`:190-192`). **Zero changes.** |
| `prompts/study-pack-v1/*.txt` | Value-agnostic; the value arrives as a substituted string (`:1581`). **Zero changes.** |
| `DomainContext.fromString` | `valueOf`-based, already `null`-safe on unknown input (`:42-51`). **Zero changes.** |
| `isQuantitativeContext` **logic** | Only the keyword **data** changes. **No resolver rewrite** (brief §23). |
| Bulk generation / validation | `NoteAuthoringMetadataParser`, `NoteBulkGenerationService:438/:452`, `NoteGenerationService:121/:124` all parse generically. **Zero changes.** |
| Analytics | Domain Context fires no `AnalyticsEventType`. **Zero changes.** |
| Program catalog / Program Families | Untouched. §A12. |
| The substring-matching defect | **Reported, not scoped.** §A12. |

### Size and routing

**Code: ~12 lines across items 1, 4, 5.** Two test files move. **Three prose blocks are the real
work** — item 2 (a multi-sentence curator-facing description that must route in two directions), item 3
(a PPR rewrite that must not narrow what already correctly lands there, and must route Taxation away),
item 8 (a clause-(b) ADR entry that is *load-bearing*, not documentation).

> ### Route: **CLAUDE CODE inline.**

Against `CLAUDE.md`'s task-routing table: no new endpoint, no migration, no new infrastructure, no
service-logic change (item 5 is one array element), 12 files but ~12 LOC, and the judgment-heavy parts
are description and ADR prose — *"UX decisions, product questions, architecture tradeoffs, doc
writing → Claude Code."* **It does not meet any Codex trigger.** The tiebreaker confirms it: this is
not a refactor requiring `AGENTS.md` anti-drift rules applied across many files.

**⚠️ Size it as "a small code diff plus three prose blocks that need real care," NOT as a 12-LOC
change.** That is the one framing that would mis-scope a kickoff.

---

## A9. Tests

**Minimum required, all in the same diff as the behaviour they cover** — `CLAUDE.md`'s
unexercised-change rule, and the reason `v0.116.0` and `v0.117.0` shipped silent no-ops.

| # | File | Assertion | Why this one |
|---|---|---|---|
| 1 | `DomainContextTest` | `containsExactly` includes `"Basic Medical Sciences"`, method renamed to `...Twelve...` | Pins the label, **which is the prompt payload** (`:1581`). |
| 2 | `DomainContextTest` `@CsvSource` | `"BASIC_MEDICAL_SCIENCES, false"` | **Two values shipped with the wrong `quantitative` and had to be corrected** (`DomainContext.java:28`). The mechanism already exists at `:38-57`; add the row. |
| 3 | `DomainContextTest` | `fromString("basic_medical_sciences")` round-trips; `fromString("unknown")` stays `null` | Pins the skew behaviour in §A10. |
| 4 | `domain-context.test.ts:9` | `toHaveLength(12)` | Fails otherwise. |
| 5 | `domain-context.test.ts` | New value's description **contains** `"without assuming a specific professional role"` **and** `"belongs in Nursing"` | Pins the **routing clause**, not merely that a description exists — the file's own comment (`:38-41`) says this is the recurring failure mode. |
| 6 | **`domain-context.test.ts` — PPR** | PPR's description **contains** `"business law"` (or `"negotiable instruments"`) **and** `"belongs in Accountancy"` | **⚠️ NEW AND MANDATORY. Nothing currently pins PPR's description.** The file has exact-string `.toBe` assertions on `GENERAL_EDUCATION`, `PROFESSIONAL_EDUCATION`, `NURSING` and `ACCOUNTANCY` (`:28-34`) — **PPR is absent**. So the §A6 rewrite breaks no test, which means it **executes** no test. That is `CLAUDE.md`'s rule verbatim: a diff that changes behaviour while touching no test that runs it. |
| 7 | `OpenAiLlmStudyPackServiceTest` | A context whose subject is `"Pharmacokinetics"` and whose Domain Context is the new value → `isQuantitativeContext` is **true** via the keyword path | **The keyword widening's own guard.** Without it item 5 of §A8 is an unexercised data change — and §A5 showed two of the three originally-proposed strings would have been measurable no-ops. |
| 8 | `OpenAiLlmStudyPackServiceTest` | New value's **label** appears on the `Domain:` line, **and no program list appears anywhere in the payload** | The **negative** assertion is what protects `ADR-001` rule 1. |
| 9 | `StudyPackGenerationContextResolverTest` | New value → `effectiveAuthoringDomain` returns the **label**, not the enum name | The label is what reaches the prompt (`:190-192`). |
| 10 | Multi-program guard | New value + 3 programs → `assertGenerationReady` does **not** throw; 3 programs + `null` **still does** | Guards **both** directions of the ADR rule. |

### ⚠️ The transport rule — both halves, stated explicitly

`CLAUDE.md` requires a real-request test for a new endpoint and a request-shape test when the client's
request shape changes. **Neither is owed here, and the release must say so rather than silently skip:**

- **(a) `MockMvc` real-request test — NOT OWED.** This release adds **no endpoint**. Say this in
  `RELEASES.md`; a silent skip reads as an unexamined one.
- **(b) `lib/api-*.test.ts` request-shape test — NOT OWED.** `frontend/lib/api.ts` *is* touched, but
  the change is **a TypeScript union member only** (`:536-547`). It emits no JavaScript and executes
  nothing at runtime, so **no request shape moves.**

---

## A10. Deployment / backward compatibility

Stage 1 §14's analysis re-verified; conclusions unchanged.

| Surface | Impact |
|---|---|
| Persisted values | **None.** Purely additive; no value removed or renamed. |
| Old frontend ↔ new backend | Stale frontend receives an unknown string; `getDomainContextLabel` (`domain-context.ts:96-98`) falls back to `?? value` and renders the raw enum name. **Ugly, never a crash.** |
| New frontend ↔ old backend | `fromString` returns `null` on unknown (`:46-50`) — the value is **silently dropped**, not a 500. |
| Existing filters / exports / public DTOs | Additive. No enum is exhaustively switched on in a breaking way. |
| `QUANTITATIVE_KEYWORDS` change | Affects **future generations only**. Study Packs never auto-regenerate, so no persisted pack changes. |
| PPR description change | **Frontend-only string.** No backend or persisted effect. Changes which notes curators *select* going forward, never any existing row. |

**Deploy ordering — owed per `CLAUDE.md`, and the honest answer is "together":**

Backend-first is **harmless** (the backend accepts a value no frontend offers). Frontend-first is
**bad but not fatal** — the new option appears in the dropdown and an old backend silently drops the
save via `fromString`'s `null`, which is a **silent data loss on a curator action**. **Ship both
together.** Vercel and Render deploy from the same push through **two independent integrations** and
have diverged before — `v0.136.0` ran a `v0.136.0` backend against a `v0.135.0` frontend and the
feature it shipped was dead on arrival. **Run `scripts/check-deploys.sh` after the release PR merges**;
it exits 2 rather than 0 when it cannot check.

---

## A11. Curator follow-up — outside the code release

**The release adds the capability. Note metadata correction is explicit curator/content work.** Per
brief §6: **no title-based migration, no bulk SQL backfill, no automatic move of multi-program
Pharmacology notes.** Handed to the owner as a worklist, not as migration logic.

| # | Action | Population | Note |
|---|---|---|---|
| 1 | **Set `BASIC_MEDICAL_SCIENCES`** on the six mechanism-framed notes in §A1.3 | **6 notes** | The entire initial reclassification population. Each verified against its own summary this pass. |
| 2 | **Classify `Opioid Medications and Safety Precautions`** by reading its summary | 1 note | Not read this pass. **Do not infer from the title** — titles were de-suffixed (§A1.3), so they are no longer reliable evidence. |
| 3 | **Trim over-selected Applicable Programs** on the eleven role-framed notes | **11 notes** | They are correctly `NURSING`; their defect is carrying `Medicine` + `Pharmacy` rows they do not merit. **⚠️ This is the larger and more consequential half of the cleanup, and it is not what the release enables.** Stage 1 found this at 6 notes; it is now 11. |
| 4 | **Resolve the duplicate `High Alert Medications`** | 2 rows, same title, both 4 programs, both `NURSING` | Near-identical summaries. |
| 5 | **Resolve the duplicate `Credit Transactions in Accountancy: Regulatory Framework and Business Law`** | 2 rows `[PROD]` | Found during §B4. Same title, same subject. |
| 6 | **Classify PNLE rows on Review Set entry** | ~9 TSV rows | Zero extra work — Domain Context becomes mandatory at that moment anyway (`ADR-001:415`). |
| 7 | **Leave the 5,671 NULL-context notes NULL** | 5,671 | `ADR-001:417` — `domain_context IS NULL` **is** the promotion-backlog marker. A server-side default would destroy it. |

⚠️ **No title-based migration, ever.** `Antibiotic Classes in Pharmacology` and
`Pharmacology Core Drug Classes and Responsibilities` are near-identical in shape and have **opposite**
correct classifications (mechanism vs. role). The de-suffixing in §A1.3 makes titles *less* reliable
than when Stage 1 wrote this warning, not more.

---

## A12. Explicitly deferred

| Deferred item | Status | Why it stays out |
|---|---|---|
| **The fused `Nursing · Medicine` catalog row** | **⚠️ Grew 2 → 20 notes in one day** `[PROD]`. Still **latent**: all 20 carry a non-null Domain Context, so the fused string is shadowed at `:190-192` and never reaches a prompt. | Brief §7: keep it out of Workstream A. A catalog change with its own risk — deleting or renaming a `course_programs` row referenced by `note_course_program`. **Sequence it separately and sooner than Stage 1 implied**, because it fires the moment any one of 20 Domain Contexts is cleared. |
| **⚠️ The `QUANTITATIVE_KEYWORDS` substring defect** | **NEW this pass.** `ratio` ⊂ `corporation`/`operations`/`administration`, `solve` ⊂ `resolve`, `current` ⊂ `currently`, `integral` ⊂ `"an integral part of"`, `interest` ⊂ `interested`, `balance` ⊂ `balance sheet`, `units` ⊂ `business units` — all proved `[PROD]` (§A1.4). | **OUT OF SCOPE for both workstreams.** Word-boundary matching would flip guidance on an unknown share of the **4,874** notes that are quantitative *via keywords only*, permanently per note. Brief §11: *prefer the smallest coherent solution*; §23: no resolver rewrite. **Owes a Backlog Index row** — see Housekeeping. |
| **Business & Finance Domain Context** | **DEFER** | Part B. Evidence, not name. |
| **`Finance` as a Course/Program** | **DEFER** | §B7. `[PROD]` still absent from the 51-row catalog. |
| **Health Sciences / Business Program Families** | **NO** | Brief §21. No repetitive multi-program selection to reduce; `ADR-001:91` requires unconditional expansion to all members, and Radiologic Technology has 0 notes. |
| **Title cleanup** | **Reported, not fixed** | Stage 1's 4 post-rule leaky titles is now ≤3 — the curator fixed one. ~69 legacy cases remain. |
| **Broad biomedical backfill** | **NO** | §A11. The population is 6 notes, by hand. |
| **`ACCOUNTANCY` mass assignment** | **NO** | §B2, §B9. |

---
---

# PART B — ACCOUNTANCY / BUSINESS / FINANCE / MANAGEMENT

## B1. Current corpus state

`[PROD 2026-09-13]`, re-read this pass. Stage 1's business-side numbers reproduced **exactly**.

| Fact | Value | Change from Stage 1 |
|---|---|---|
| **`ACCOUNTANCY` Domain Context usage** | **0 notes** | **Unchanged. The calibration window is still open.** |
| Accountancy-program join rows | **154**, all canonical, **all `domain_context IS NULL`** | Unchanged |
| Business Administration join rows | **17** — 16 are one shared `Project Management` set already on `ENGINEERING_SCIENCES` across 23 programs; **genuine business content = 1 note** (`Marketing Mix`) | Unchanged |
| Senior High – ABM join rows | **3** (all Economics) | Unchanged |
| Law join rows | **2** (both `International Law`) | Unchanged |
| **Cross-program business notes** | **ZERO.** No note carries `Accountancy` + `Business Administration`, or `Accountancy` + `ABM` | Unchanged |
| `Finance` in the catalog | **Absent.** 0 of 51 rows match `%financ%` | Unchanged |
| **CPALE curriculum plan** | **Still absent.** `docs/curriculum/` holds `ale-`, `civil-engineering-`, `let-`, `pnle-` TSVs and **no `cpale-` file** | Unchanged |

**The asymmetry Stage 1 identified is fully intact**: the health case has cross-program assignments
that already exist (now **18** canonical multi-program notes, up from 7); the business case has
**zero**, and its nearest incumbent value has never been applied once.

### The Accountancy corpus by subject `[PROD]` — the input to §B4 and §B5

| Subject | Notes | Character |
|---|---|---|
| Management Advisory Services | **11** | CVP, capital budgeting, standard/relevant costing, variance analysis, responsibility accounting, **+ linear programming, decision trees, inventory models** |
| Financial Management | **9** | TVM, cost of capital, capital structure, leverage, dividend policy, working capital, risk and return, financial ratios, compound interest |
| Investments | **5** | FVOCI, FVTPL, amortized cost under IFRS, equity method, consolidation |
| Managerial Decision Making | **5** | Capital budgeting techniques, performance measurement, relevant costing, responsibility accounting, budget preparation |
| Decision Making | **4** | Limiting factor, make-or-buy, product mix, special order |
| Performance | **4** | Balanced Scorecard, residual income, ROI, transfer pricing |
| **Regulatory Framework & Business Law** | **10** (incl. 1 duplicate) | Corporation Code, Labor Code, obligations & contracts, negotiable instruments, insurance, partnership, sales, credit transactions, IP |
| FAR / Fundamentals / Reporting / Inventory / PPE / Cash & Receivables / Equity | ~30 | Recognition and measurement |
| Auditing (Fundamentals, Procedures, Reports, Evidence, Internal Controls) | ~16 | Assertions, opinions, risk, skepticism, controls |
| Taxation (Basic, Advanced, Other, Income, Business) | ~19 | Philippine statute-specific |

---

## B2. ACCOUNTANCY quantitative audit

> # VERDICT: **KEEP TRUE**

**This is the Stage 2 decision the brief called most important, and it is decided by measurement
rather than philosophy.**

### B2.1 Method

`quantitative = false` is a **no-op** that falls through to `QUANTITATIVE_KEYWORDS` (§A5.1). So the
question *"should `ACCOUNTANCY` be `false`?"* reduces to a measurable one:

> **On how many of the 154 notes would `false` actually produce a different outcome from `true`, and
> is that difference right or wrong?**

`isQuantitativeContext`'s haystack was replicated in SQL and run against all 154 notes' real subject,
tags, key concepts and Study Pack summaries `[PROD 2026-09-13]`. All 154 have a Study Pack.

### B2.2 The measurement

| Tier | Notes tripping ≥1 existing keyword with `quantitative = false` |
|---|---|
| **Full tier** (Challenge, Board, Long, Adaptive, Interview) | **145 of 154 (94%)** |
| **Quick Review tier** (domain + subject + tags only) | **94 of 154 (61%)** |

**`false` changes the outcome for only 9 of 154 notes at the full tier.** Those 9, read individually:

| The 9 notes `false` would actually change | Subject | Genuinely computational? |
|---|---|---|
| `Audit Evidence and Assurance Engagements` | Audit Fundamentals | **No** — `false` correct |
| `Professional Skepticism in Auditing: Concept and Application` | Audit Fundamentals | **No** — `false` correct |
| `Audit Planning and Risk Assessment` | Audit Procedures | **No** — `false` correct |
| `Compliance Testing Procedures in Auditing` | Audit Procedures | **No** — `false` correct |
| `Audit Reports: Disclaimer of Opinion` | Audit Reports | **No** — `false` correct |
| `Estate Tax Fundamentals in Advanced Taxation` | Advanced Taxation | **⚠️ YES** — estate tax computation. `false` **wrong** |
| `Fundamentals of Individual Income Taxation` | Basic Taxation | **⚠️ YES** — bracket computation. `false` **wrong** |
| `Risk and Return in Financial Management` | Financial Management | **⚠️ YES** — expected return, σ, beta, CAPM. `false` **wrong** |
| `Residual Income in Performance Measurement` | Performance | **⚠️ YES** — `RI = NOPAT − (capital × required return)`. `false` **wrong** |

> ### **`quantitative = false` would be a 5-save / 4-lose trade across 154 notes.**

**That is the single piece of evidence that decided the verdict.** `false` is not a protective change
here — it is a coin flip that saves five conceptual audit notes and wrongly strips computation guidance
from four genuinely computational ones. It does not deliver the protection its advocates want, because
**the other 140 notes trip the keyword scan regardless of the flag** — including purely conceptual
ones: `Audit Assertions in Financial Statement Auditing` trips `accounting`, `balance` and `interest`;
`Corporation Code in Philippine Business Law` trips `interest` and `ratio` (the latter only because
`ratio` ⊂ `corporation`, §A1.4); `Risk Assessment Components in Internal Controls` trips `integral`
and `ratio`; `Types of Audit Opinions` trips `accounting`.

### B2.3 Why the asymmetry argument that carried `false` for BASIC_MEDICAL_SCIENCES does not transfer

Stage 1's §15 reasoning for `false` was an asymmetry of repairability: *a missed note can be fixed by
adding a keyword; a wrongly-`true` note cannot be fixed without regenerating a Study Pack.* Sound for
biomedicine. **It fails here, for a structural reason worth stating precisely:**

For pharmacokinetics the repair exists — **`"pharmacokinetic"` is a precise, discipline-specific token
with no other meaning**, and §A5 measured it working. For accounting computation **no such token
set exists.** The words that discriminate computational from conceptual accounting are `tax`, `cost`,
`income`, `return`, `capital`, `depreciation`, `valuation` — either generic English or shared across
every discipline in the library. Under `String.contains` matching (§A1.4) they would be catastrophic:
`cost` would trip construction notes, `return` would trip nearly everything.

**So the repair mechanism the `false` decision depends on is unavailable for Accountancy.** The
permanence argument becomes symmetric: with `false`, the four computational notes lose guidance
permanently in exactly the way a wrongly-`true` note keeps it permanently.

### B2.4 Why non-computational Accountancy material is not harmed — brief §B2's required explanation

| Reason | Detail |
|---|---|
| **The guidance is hedged, not directive** | Every mode's string is conditional — *"when appropriate"*, *"when the notes support them"*, *"**only when** the notes clearly support them"* (`:1503-1510`). A model handed *"the material appears quantitative"* over a Professional Skepticism note has explicit permission to include no computation. |
| **The affected population is small and shrinking** | Within `ACCOUNTANCY`'s **proper** boundary (§B3, after RFBT moves to PPR per §B4), the systematically non-computational slice is Auditing — **~16 of ~144 notes, ~11%.** FAR, Advanced FAR, Cost, MAS, Financial Management and Taxation all compute. |
| **`false` protects almost none of it anyway** | Of those ~16 audit notes, **11 already trip keywords** at the full tier. `false` would reach only the 5 in §B2.2. |
| **The residual cost is bounded and namable** | Five conceptual audit notes would receive a hedged sentence and the `questionType`/`workingSolution` contract. **Recorded as an accepted cost, not smoothed over** — and §B9 gives the mitigation: those five sit at `NULL` today and correctly trip nothing, so **leaving them NULL is already the honest handling**, and assigning `ACCOUNTANCY` is the one place `true` genuinely over-applies. |

### B2.5 Persisted-note compatibility — brief §11's required statement

> **`ACCOUNTANCY` carries ZERO production notes `[PROD 2026-09-13]`, so changing its `quantitative`
> flag today has EXACTLY ZERO persisted-note consequence.** No Study Pack was generated under it, so
> nothing is locked in.
>
> **This window closes permanently at first broad adoption.** Study Packs never auto-regenerate
> (`DomainContext.java:23-29`), so the first cohort classified under `ACCOUNTANCY` fixes its own
> `quantitative` behaviour for good. **That argues for deciding now — which this section does — rather
> than deferring**, and it is why the brief was right to call this the most important Stage 2 question.

**Verdict restated: KEEP TRUE. No code change to `DomainContext.java:19`.** The tightening that
matters is **not** the flag but §B9's NULL strategy and §B4's routing of RFBT away — both of which
reduce wrong `true` application far more than flipping the flag would.

---

## B3. ACCOUNTANCY semantic boundary

**`ACCOUNTANCY` is retained** (brief §13). The question was its boundary. Tightened against real
corpus examples `[PROD]`:

> **Does an accounting standard, a reporting convention, a tax statute, or an
> audit/accounting professional framework materially determine the explanation?**
>
> - **Yes → `ACCOUNTANCY`.**
> - **No → another treatment context, or an honest `NULL` pending shared-business calibration.**

### Belongs in `ACCOUNTANCY` — verified against summaries

| Note | Why the standard **is** the explanation |
|---|---|
| `Fair Value Through Profit or Loss (FVTPL) Investments` | *"This classification **under IFRS 9**…"* |
| `Financial Assets Measured at Amortized Cost under IFRS` | Standard named in the title and determinative. |
| `Equity Method Accounting for Investments` | *"ownership of 20% to 50% of voting stock"* — a convention-set threshold. |
| `Fundamentals of Consolidated Financial Statements` | *"line by line while **eliminating intercompany transactions**."* |
| `Depreciation and Accounting for Property, Plant, and Equipment` | Recognition and measurement convention. |
| `Trial Balance in Accounting: Purpose and Preparation` | Bookkeeping procedure. |
| `Audit Assertions in Financial Statement Auditing` | *"management's representations regarding recognition, measurement, presentation, and disclosure."* |
| `Types of Audit Opinions` · `Unmodified/Qualified/Adverse Opinion in Audit Reports` · `Understanding Audit Risk` · `Professional Skepticism in Auditing` | Profession-specific frameworks. |
| `Percentage Tax Principles and Application` | *"VAT threshold of 3 million Philippine pesos… typically 3%."* Philippine statute. |
| `Earnings Per Share (EPS) in Financial Accounting` | Computation **defined by** a reporting standard. |

### Does NOT belong in `ACCOUNTANCY`

| Class | Goes to | Why |
|---|---|---|
| **RFBT / Business Law** (10 notes) | **`PROFESSIONAL_PRACTICE_AND_REGULATION`**, after §A6/§A7 | Treatment is legal/procedural, matching PPR's *ratified justification* at `ADR-001:336`. And PPR is `quantitative = false`, correct for them — a second independent argument. |
| **Program-neutral finance / managerial** (§B5) | **`NULL` for now** | No accounting convention determines TVM, WACC or CVP. §B9. |
| **Operations-research methods** (3 notes) | **`NULL`** | Linear programming, decision trees, EOQ contain no accounting content at all. §B9. |

---

## B4. CPALE domain map

Preliminary and **note-level exceptions are explicitly allowed** — no subject is forced to one value.

| CPALE area | `[PROD]` notes | Likely default | Note-level exceptions | Status |
|---|---|---|---|---|
| **Financial Accounting and Reporting** | ~30 | **`ACCOUNTANCY`** | None found | Confirmed by content |
| **Advanced FAR** (Investments, Special Areas, Cash Flow) | ~10 | **`ACCOUNTANCY`** | None | The most convention-bound content in the corpus — IFRS 9, equity method, consolidation |
| **Auditing** | ~16 | **`ACCOUNTANCY`** | None | **Negative finding preserved: do NOT create an Audit/Assurance context.** Every note read is framed on financial-statement assertions and audit opinions — accounting-profession-specific. **⚠️ But see §B9:** the 5 conceptual audit notes in §B2.2 are the one place `ACCOUNTANCY(true)` genuinely over-applies. |
| **Management Advisory Services** | 11 | **`ACCOUNTANCY`** today | **⚠️ `Linear Programming`, `Decision Trees`, `Inventory Models (EOQ)` → `NULL`** | **The largest genuine shared cluster tomorrow.** §B5, §B6. |
| **Financial Management** | 9 | **`NULL` pending calibration** — *not* `ACCOUNTANCY` | `Compound Interest, Liabilities, and Debt Analysis` may be genuinely accounting-flavoured | **This is the strongest program-neutral cluster.** §B5. |
| **Cost / Managerial Accounting** | ~13 (Cost Accounting 9 + Decision Making 4) | **Split.** Standard costing, responsibility accounting, variance analysis → `ACCOUNTANCY`. CVP, contribution margin, relevant costing → **`NULL`** | — | Treatment genuinely differs within the subject |
| **Managerial Decision Making / Performance** | 9 | **Split**, same test | `Balanced Scorecard`, `Residual Income`, `ROI` → `NULL`; `Transfer Pricing` → likely `ACCOUNTANCY` | §B6 |
| **Regulatory Framework / Business Law** | 10 | **`PROFESSIONAL_PRACTICE_AND_REGULATION`** | — | **Gated on §A7 validation.** ⚠️ Contains a **duplicate** row (§A11 item 5). |
| **Taxation** | ~19 | **`ACCOUNTANCY`** | ⚠️ **Do not force at Subject level — see §B4.1** | **Do NOT create `TAXATION`.** |
| **Investments** | 5 | **`ACCOUNTANCY`** | — | IFRS-determined |
| **Quantitative decision methods** | 3 | **`NULL`** | — | **Do NOT create Operations Research / Decision Sciences / Quantitative Methods.** Far below the ~10 floor. |

### B4.1 Taxation — careful wording, per brief §14

**Do not lock `Taxation = ACCOUNTANCY` at Subject level.** The default is `ACCOUNTANCY` because every
note read is Philippine-statute-specific (`RA 7160`, BIR administration, the ₱3M VAT threshold) —
which is **narrower** than Accountancy, not broader, and cross-program tax reuse would need Law-program
notes that do not exist (`Law` carries 2 International Law notes `[PROD]`).

**But allow note-level divergence**, on these three questions:

1. Is the note teaching **tax computation or accounting treatment**? → `ACCOUNTANCY`.
2. Is it teaching **legal/regulatory doctrine**? → candidate for `PROFESSIONAL_PRACTICE_AND_REGULATION`
   — **but only after §A7 validates, and only if the §A6 Taxation routing sentence is honoured.**
3. Is it **general taxation knowledge** that could legitimately serve Law / Business Administration /
   Finance? → **`NULL`** pending applicability curation.

**⚠️ §A6's Taxation routing sentence exists precisely to stop a widened PPR from silently swallowing
all ~19.** Tax notes are statutory, so a PPR description that says *"legal, procedural and regulatory
including business law"* without routing tax computation away is a magnet for them. Read §A6 and §B4.1
together; neither is safe alone.

**Do NOT create `TAXATION`.**

---

## B5. Shared business candidate map

Real production notes whose treatment appears **program-neutral** by §B3's test. **No metadata is
changed here.** Applicability is the missing evidence, not content.

| Cluster | Notes `[PROD]` | Programs to evaluate during CPALE authoring |
|---|---|---|
| **Time value & valuation** | `Time Value of Money in Accountancy` · `Compound Interest, Liabilities, and Debt Analysis` | Accountancy, Business Administration, ABM, Finance |
| **Cost of capital & structure** | `Cost of Capital in Financial Management` (WACC) · `Capital Structure` · `Leverage` · `Dividend Policy` | Accountancy, BSBA, Finance |
| **Capital budgeting** | `Capital Budgeting in Management Advisory Services` (NPV) · `Capital Budgeting Techniques in Managerial Decision Making` | Accountancy, BSBA, Finance |
| **Risk & return** | `Risk and Return in Financial Management` | Accountancy, BSBA, Finance |
| **Working capital & ratios** | `Working Capital Management` · `Financial Ratios in Financial Management` | Accountancy, BSBA, ABM, Finance |
| **CVP / cost behaviour** | `Break-Even Analysis and Cost-Volume-Profit Relationships` · `Cost-Volume-Profit Analysis in MAS` · `Cost Behavior in MAS` · `Fixed, Variable, and Mixed Costs` | Accountancy, BSBA, ABM |
| **Performance measurement** | `Balanced Scorecard in Performance Measurement` · `Residual Income` · `Return on Investment (ROI)` | Accountancy, BSBA |
| **Managerial decision analysis** | `Limiting Factor Analysis` · `Make or Buy Decisions` · `Product Mix Decisions` · `Special Order Decisions` · `Relevant Costing` (×2) | Accountancy, BSBA |
| **Operations-research methods** | `Application of Linear Programming` · `Decision Trees` · `Inventory Models (EOQ)` | **Unresolved — leave NULL.** No accounting content at all. |

**Approximately 24–28 genuinely program-neutral notes by content.** **Volume is emphatically not the
blocker** — cross-program *applicability* is, and it stands at **zero** `[PROD]`.

**⚠️ Do NOT stamp `ACCOUNTANCY` on these merely because Accountancy is currently their only Applicable
Program** (brief §12C). For a single-program note, `NULL` + the program-name fallback is strictly
better than a knowingly-too-narrow authoring treatment. §B9.

---

## B6. Management analysis

> ### Does the likely shared cluster extend materially beyond "Finance"? **YES — decisively, and it is counted rather than asserted.**

`[PROD 2026-09-13]`, by subject:

| Cluster | Notes |
|---|---|
| **Management / managerial** — MAS **11** + Managerial Decision Making **5** + Decision Making **4** + Performance **4** | **24** |
| **Finance** — Financial Management | **9** |

> **The management/managerial cluster is 2.7× the size of the finance cluster.**

Its content is management, not finance: Balanced Scorecard, ROI, residual income, transfer pricing,
responsibility accounting, standard costing, variance analysis, relevant costing, limiting-factor
analysis, make-or-buy, product mix, special-order decisions, linear programming, decision trees, EOQ.
Much of it is **performance measurement and operational decision-making**, which no reasonable reading
of *"Finance"* covers.

### Effect on future naming

**`Business & Finance` — Stage 1's leading hypothesis — is demonstrably too narrow for the corpus that
actually exists.** A curator holding `Balanced Scorecard` or `Product Mix Decisions` would not select
it, which is the `v0.99.0` description defect arriving *in the name* rather than in the description.

**Per brief §9, the name is NOT ratified and no alternative is minted here.** Constraints for the
future comparison:

- **Compare only after the applicability map exists.** The corpus shape decides the name; today's
  24-vs-9 split is a *hypothesis about* the eventual corpus, not the corpus.
- **Names must be BORROWED** (`ADR-001:405`) — `Health Sciences` and `Health Sciences Foundation` were
  both rejected on exactly this test, and `ADR-001:297` explains why: *"an invented name usually
  signals there is no real shared body of knowledge behind it."*
- **Do NOT create `Business & Management`** merely because the brief mentions it, and do not invent
  umbrella terminology. Candidates must be real curriculum vocabulary supported by the actual corpus.
- **Do NOT mint `Operations Research`, `Decision Sciences` or `Quantitative Methods`** from a 3-note
  cluster.

> **Management is materially represented: YES.** That is this section's answer, and it is the single
> most useful thing Stage 2 adds to the eventual naming decision.

---

## B7. Finance Program trigger

**`Finance` does not exist in the catalog** `[PROD 2026-09-13]` — 0 of 51 rows match `%financ%`.

**Do NOT pre-seed it.** `ADR-001:107-111` makes catalog growth demand-driven by authoring, and the
counter-example is decisive: **`Business Administration` already exists and has attracted exactly ONE
genuine business note** in the entire library `[PROD]`. Adding `Finance` now produces a second thin
shelf — the risk `ADR-001:277` names by name.

> ### The exact trigger
>
> **The first time CPALE curation identifies a canonical Note genuinely applicable to Finance — i.e.
> the first moment a CPALE / Business / ABM curriculum plan lists `Finance` in an
> `applicable_programs` cell — add `Finance` to the Course/Program catalog.**

Three properties of this trigger, per brief §18:

1. **It is ONE note, not ten.** The ~10-note floor is **Domain Context** governance (`ADR-001:397`);
   it has never governed Course/Program applicability. **Keep the two decisions separate.**
2. **It is a curation event, not a volume threshold.** A curator's honest judgment that a note is
   applicable to Finance *is* the trigger (`ADR-001:107-111`).
3. **Adding `Finance` does NOT justify a Finance Domain Context.** `ADR-001:399`: *"A new Course /
   Program does NOT imply a new Domain Context… The catalog growing is not evidence for the taxonomy
   growing."* And a Finance Domain Context would serve one live program → presumed a program mirror
   under guard 2 (`ADR-001:406`).

---

## B8. Future Domain Context trigger

The Business shared-context decision should be **re-audited** — not implemented — when:

> **≥10 canonical Notes whose treatment remains materially unchanged across 2 or more legitimate live
> Course/Programs**, arriving via a committed CPALE / Business Administration / ABM curriculum plan in
> `docs/curriculum/` — the same *firmly planned* evidence shape the biomedical candidate clears on.

**Today that count is 0** `[PROD]`.

**10 is `ADR-001:397`'s governance floor, not statistical precision** (brief §19). Note the biomedical
case now clears a **stronger** bar: 6 notes *already authored AND already multi-program*, plus ~9
planned (§A1.3). A business candidate arriving at exactly 10 *firmly planned* is at the floor, not
above it.

**When it fires, re-run all five tests** (`ADR-001` / Stage 1 §4.4, §6.3):

| Test | Where it stands today |
|---|---|
| **A — Existing-context failure** | **UNFALSIFIED, not failed.** `ACCOUNTANCY` has never been applied to a single note, so there is no observation of it distorting anything. **§B2/§B9's NULL strategy is what will generate this evidence.** |
| **B — Cross-program stability** | **ZERO instances.** No business note carries 2+ programs. **This is the binding gap.** |
| **C — Meaningful content volume** | **PASS — the candidate's real strength.** ~24–28 program-neutral notes (§B5). |
| **D — Stable semantic boundary** | **PASS conceptually.** §B3's test is as crisp as the health boundary. Untested against a curator. |
| **E — Prompt usefulness** | **PASS conceptually.** `Domain: Accountancy` on a WACC note instructs accounting terminology; a shared label would not. |

**Guard 2 (`ADR-001:406`) independently blocks it today:** a shared business value would serve
`Accountancy` (154) plus `Business Administration` (1 business note) and `ABM` (3) — **one program
meaningfully**, so it is presumed a program mirror of `ACCOUNTANCY`.

> **State of the hypothesis: STRONG — and stronger than Stage 1 recorded it, on two counts.** §B6
> quantified the cluster at 24 management + 9 finance notes (Stage 1 described it qualitatively), and
> §B2 established that the corpus is measurably classifiable by treatment. **What did not change is
> the thing that actually gates it: cross-program applicability is still exactly zero.** Per brief §8:
> this is *a strong taxonomy hypothesis awaiting CPALE applicability curation*, **not** a rejected or
> speculative idea, and it must not be described as lacking conceptual evidence.

---

## B9. NULL strategy

`ADR-001:295`: *"An honest NULL beats a catch-all."* `ADR-001:417`: `domain_context IS NULL` **is** the
promotion-backlog marker — a queryable state, not a gap.

**Categories that should intentionally remain `NULL`:**

| Category | Notes | Why NULL is the honest answer |
|---|---|---|
| **Program-neutral finance** — TVM, cost of capital, capital structure, leverage, dividend policy, working capital, risk and return, generic ratios | ~9 | Single-program today. The program-name fallback (`:143-145`) resolves them correctly. Stamping `ACCOUNTANCY` asserts an accounting treatment their content does not have **and destroys the evidence §B8's re-audit needs.** |
| **Program-neutral managerial** — CVP, contribution margin, cost behaviour, relevant costing, Balanced Scorecard, ROI, residual income, the four decision-analysis notes | ~12 | Same. §B6 shows this is the larger half of the eventual shared cluster. |
| **Operations-research methods** — linear programming, decision trees, EOQ | 3 | Fit no existing value. Far below the ~10 floor. **Do NOT create a context; do NOT force `ENGINEERING_MATHEMATICS`** — its treatment fits but its **name** is engineering-specific and would violate `ADR-001:405`. |
| **⚠️ The 5 conceptual audit notes from §B2.2** | 5 | **The one place `ACCOUNTANCY(true)` genuinely over-applies.** `[PROD]` they sit at `NULL` today and correctly trip **no** keyword at either tier — so **current behaviour is already right for them**, and assigning `ACCOUNTANCY` would be the change that breaks it, permanently per note. **Classify these last, or leave them NULL.** This is the concrete mitigation for §B2.4's accepted cost. |
| **Ambiguous Taxation** — general taxation knowledge serving Law / BSBA / Finance | TBD | §B4.1. |

**⚠️ Do NOT mass-assign `ACCOUNTANCY` to all 154 notes** (brief §12). Stage 1 proposed it as a
zero-cost experiment; **Stage 2 tightens that to NO.** The corpus contains at least four materially
different treatment classes (§B10), and a wholesale stamp would:

- assert an accounting treatment on ~21 program-neutral notes that do not have one;
- sweep 10 RFBT notes into `quantitative = true` when PPR's `false` is correct for them;
- **destroy the very evidence the §B8 re-audit depends on**, by removing the `NULL` marker that
  identifies the program-neutral cluster;
- and permanently fix `quantitative` behaviour on all 154 at once (§B2.5), closing the calibration
  window with no staged read.

---

## B10. CPALE authoring instructions

The practical per-Note classification sequence for the curator. **Follow it in order.**

> **For each canonical Note:**
>
> **1. Identify the knowledge itself.** What does this note teach? Ignore the title's container
> suffix, ignore the Review Set, ignore who will read it. ⚠️ **Titles are not evidence** — §A1.3 found
> titles being de-suffixed under the same content, and §A11 forbids title-based classification.
>
> **2. Identify Applicable Programs honestly.** Who can *legitimately* study this? **Under-selecting
> is recoverable; over-selecting is what produced the 11 over-programmed nursing notes in §A11.** Do
> not mechanically assign Accountancy + BSBA + ABM + Finance + Law.
>
> **3. Determine whether professional, accounting or legal treatment materially changes it.**
> - An accounting standard / reporting convention / tax statute / audit framework determines the
>   explanation → **`ACCOUNTANCY`**.
> - Treatment is legal, procedural or regulatory → **`PROFESSIONAL_PRACTICE_AND_REGULATION`**
>   *(gated on §A7 validation)*.
> - Neither → the knowledge is program-neutral.
>
> **4. Choose an existing Domain Context only if truthful.** `ADR-001:285` — the **coarsest** label
> under which treatment is identical. The existence of a narrower value is **not** a reason to pick it
> (`ADR-001:318`).
>
> **5. Otherwise leave `NULL` if the Note is single-program.** The program-name fallback resolves it
> correctly, and `NULL` is the promotion-backlog marker. **An honest NULL beats a misleading Domain
> Context.**
>
> **6. Flag any MULTI-program Note with no truthful context for taxonomy review.** This is the forcing
> function (`ADR-001:415`) — generation is *rejected* server-side for a multi-program Note with a null
> Domain Context (`:33-39`), so these surface themselves. **These flags are the §B8 evidence.**

**Two rules that override anything above:**

- **⚠️ Review Set membership must NEVER choose Domain Context.** `ADR-001:316`: membership of a Civil
  Engineering Review Set does not imply `CIVIL_ENGINEERING`. Structurally enforced —
  `StudyPackGenerationContext` has no collection field (Stage 1 §13) — and it must also hold in the
  curator's head.
- **⚠️ Applicable Programs must NEVER choose Domain Context.** Multi-program applicability alone does
  not imply a shared value (`ADR-001:315`), and a Note being applicable to Nursing does not make it
  `NURSING` (§A2).

**Flag container leakage without rewriting titles** (brief §22): `Time Value of Money in Accountancy`
should be `Time Value of Money` — the prompt's own negative example existing as real production data
(`note-generation-developer.txt:18-31`). **Report; do not auto-rewrite. No title-based migration.**

---

## B11. Explicitly NOT approved

| Candidate | Status |
|---|---|
| **`Business & Finance` Domain Context** | **NOT YET.** Hypothesis is **strong**; cross-program applicability is **zero**. §B8. And §B6 shows the name is likely too narrow for the corpus. |
| **`Business & Management` Domain Context** | **NOT YET.** Not minted here, and **not to be created merely because the brief names it.** §B6. |
| **`Finance` Domain Context** | **NO.** Would serve one live program → presumed a program mirror (`ADR-001:406`). The **Course/Program** is a separate, live trigger (§B7). |
| **`BUSINESS_LAW` Domain Context** | **NO.** 10 notes (below floor), one live program, and PPR's *ratified justification* already fits. Widen the description (§A6); do not mint the value. |
| **`AUDITING` / `AUDIT_AND_ASSURANCE`** | **NO.** Revalidated: every audit note read is framed on financial-statement assertions and audit opinions — accounting-profession-specific, so `ACCOUNTANCY` holds. **Branch closed.** |
| **`TAXATION` Domain Context** | **NO.** Philippine-statute-specific, i.e. **narrower** than Accountancy, not broader. Note-level classification only. §B4.1. |
| **`OPERATIONS_RESEARCH` / `DECISION_SCIENCES` / `QUANTITATIVE_METHODS`** | **NOT YET.** 3 notes, far below the floor. `NULL`. §B9. |
| **Business / Commerce Program Family** | **NO.** Zero cross-program business notes, so there is no repetitive authoring work to reduce — a family here would be a curriculum assertion dressed as a shortcut (`ADR-001:97`). |
| **Mass-assigning `ACCOUNTANCY` to the 154-note corpus** | **NO.** Tightened from Stage 1's proposal. §B9. |
| **Changing `ACCOUNTANCY`'s `quantitative` flag** | **NO — KEEP TRUE.** §B2. |

---
---

# Final decision block

```
DOMAIN CONTEXT CALIBRATION — STAGE 2

WORKSTREAM A — BASIC MEDICAL SCIENCES

Status:
  READY FOR IMPLEMENTATION

Display label:
  Basic Medical Sciences

Enum:
  BASIC_MEDICAL_SCIENCES

Quantitative:
  false   (owner, 2026-09-13 — re-verified, STANDS, and is better supported than when settled)

Quantitative keyword widening:
  REQUIRED — but with ONE string, not Stage 1's three.

  ADD:    "pharmacokinetic"   [PROD] +17 net-new at the Quick Review tier (2 canonical, incl.
                              `Pharmacokinetics: ADME` — the note Stage 1 named as "the decision" —
                              plus 15 learner copies of `Pharmacology of Insulin`)
  REJECT: "half-life"         [PROD] the string appears in ZERO notes corpus-wide, at any tier
  REJECT: "clearance"         [PROD] 0 net-new at the QR tier; +2 at the full tier and BOTH are
                              FALSE POSITIVES, one on PROFESSIONAL_PRACTICE_AND_REGULATION — a
                              quantitative=false-BY-DECISION value (domain-context.ts:48-53).
                              Building clearance, not renal clearance: 77 matches corpus-wide across
                              ARCHITECTURAL_DESIGN, CIVIL_ENGINEERING, ENGINEERING_SCIENCES, PPR.
  REJECT: "pharmacodynamic"   +1 unique note, and that note is CONCEPTUAL (receptor theory) — wrong
                              direction for a quantitative flag.

  ⚠️ THE CONDITION'S STATED RATIONALE IS VACUOUS, AND THE RELEASE MUST NOT REPEAT IT.
  Stage 1 justified the condition as preventing a regression: pharmacokinetics content moving off
  NURSING(true) would lose computation guidance. [PROD] that regression affects ZERO notes. Only 3
  of the 10 candidate notes carry NURSING today, and ALL THREE trip existing keywords at BOTH tiers
  (Antibiotics -> "resistance"; Common [Normal] Laboratory Values -> "chemistry"). The other seven
  are already NULL, hence already non-quantitative. SHIP THE WIDENING ANYWAY, in the same release
  as conditioned — it closes a PRE-EXISTING Quick Review gap — but write it up as that, not as a
  regression mitigation. Recording a shipped item with a named consequence that does not exist is
  the v0.116.0 / v0.117.0 failure mode.

  ⚠️ MEASUREMENT DISCIPLINE, RECORDED BECAUSE IT NEARLY REVERSED AN OWNER DECISION: the keyword scan
  runs over TWO haystacks. Quick Review / Study Pack (:293) passes conceptHints=List.of() and
  summary=null, so its haystack is domain + subject + tags ONLY; all six other quiz modes include
  key concepts and the Study Pack summary. Measured at the FULL tier every candidate string looks
  like a no-op. The regression lives ONLY at the QR tier. Measure at the tier where the behaviour
  occurs.

  ⚠️ COUPLING: "pharmacokinetic" works BECAUSE matching is unanchored (it matches the subject
  "Pharmacokinetics"). If the substring defect below is ever fixed with word boundaries, this entry
  silently stops working. Record that in the comment beside it.

NURSING retained:
  YES — ~200 nursing-role notes plus 11 of the 18 multi-program Pharmacology notes depend on it.
  Boundary: does a professional role appear in the KNOWLEDGE, or only in the AUDIENCE?

PPR description widened:
  YES — BLOCKED PENDING VALIDATION (§A7), which is the owner's to run, not Claude's (Arm B requires
  a production write). Owner decision 3 stands; two tightenings are mandatory before it ships:
    (i)  it MUST route Taxation away, or a widened PPR silently swallows ~19 Philippine tax notes
         and becomes the "anything legal" catch-all brief §16 forbids;
    (ii) NOTHING currently tests PPR's description — domain-context.test.ts has exact-string
         assertions on GENERAL_EDUCATION, PROFESSIONAL_EDUCATION, NURSING and ACCOUNTANCY but NOT
         PPR — so the rewrite would break no test, therefore EXECUTE no test. A routing assertion
         is owed in the same diff.
  If validation fails, ship BASIC_MEDICAL_SCIENCES WITHOUT it. They are independent array entries,
  bundled for convenience, not coupled technically.

Database migration:
  NO. [PROD] notes.domain_context is character varying with ZERO check constraints, and
  @Enumerated(EnumType.STRING) persists the NAME. No DDL, no Flyway file.

Backfill:
  NO. Six notes, by hand, as curator work outside the release.

Resolver rewrite:
  NO. isQuantitativeContext's LOGIC is untouched; only one array element changes.

Program catalog change:
  NO.

Program Family change:
  NO.

Exact blast radius:
  8 MUST CHANGE + 4 SHOULD CHANGE.
  MUST:   DomainContext.java (append) | domain-context.ts (append option) | domain-context.ts:54
          (PPR rewrite) | api.ts:536-547 (union member) | OpenAiLlmStudyPackService.java:168-176
          (one keyword) | DomainContextTest.java (label + @CsvSource row + METHOD RENAME) |
          domain-context.test.ts (length 12 + new-value routing + PPR routing) | ADR-001 (clause (b))
  SHOULD: RELEASES.md | a new docs/features/domain-context.md (the one discretionary item — no
          Domain Context feature doc exists among the 48) | GPT_CONTEXT.md stamp | ADR-001's two
          stale lines (:377-378 "three unused values" — two are now in use at 232 and 46; :403
          "41 programs" — [PROD] 51)
  CODE: ~12 lines. THE WRITING IS THE WORK: three prose blocks (the new description, the PPR
  rewrite, the ADR entry) each of which is load-bearing rather than documentation.

Route:
  CLAUDE CODE INLINE. No endpoint, no migration, no infrastructure, no service-logic change; the
  judgment-heavy parts are description and ADR prose. Meets no Codex trigger in CLAUDE.md's table.

Verification tier:
  ONE advisor() CALL. No new endpoint, so NO MockMvc real-request test is owed — SAY SO in the
  release rather than skipping it silently. api.ts is touched but the change is a TypeScript union
  member that emits no JavaScript, so NO api-*.test.ts request-shape test is owed either — say that
  too. But the diff DOES change behaviour (quantitative fall-through, PPR routing), so the tests in
  §A9 must move in the SAME diff per CLAUDE.md's unexercised-change rule.

Deploy ordering:
  SHIP TOGETHER. Backend-first is harmless; frontend-first silently DROPS a curator's save via
  fromString's null return. Vercel and Render deploy through two independent integrations and have
  diverged before (v0.136.0). Run scripts/check-deploys.sh after merge.

Biggest implementation risk:
  NOT the code — it is ~12 lines. It is the PPR DESCRIPTION WIDENING (§A6), and the risk has three
  parts: it is currently untested, it is gated on a validation Claude cannot run, and if it ships
  without the Taxation routing sentence it silently re-homes ~19 tax notes. Second risk: writing up
  the keyword widening as a regression mitigation when [PROD] shows no regression exists.

Curator follow-up (outside the release):
  1. Set BASIC_MEDICAL_SCIENCES on 6 mechanism-framed notes (§A1.3) — the whole initial population.
  2. Classify `Opioid Medications and Safety Precautions` by READING IT (titles are not evidence).
  3. Trim over-selected Applicable Programs on 11 role-framed notes — the LARGER half of the
     cleanup, and NOT what the release enables. Stage 1 had this at 6; it is now 11.
  4. Resolve the duplicate `High Alert Medications` (2 rows, same title).
  5. Resolve the duplicate `Credit Transactions in Accountancy` RFBT row.
  6. Classify the ~9 PNLE rows on Review Set entry (zero extra work — mandatory then anyway).
  7. Leave the 5,671 NULL-context notes NULL — it is the promotion-backlog marker.

Explicitly deferred:
  - The fused `Nursing · Medicine` catalog row — ⚠️ GREW 2 -> 20 NOTES IN ONE DAY [PROD]. Still
    latent (all 20 carry a non-null Domain Context, so the fused string is shadowed at :190-192),
    but it fires the moment any one of those 20 is cleared. Sequence it SOONER than Stage 1 implied.
  - ⚠️ THE QUANTITATIVE_KEYWORDS SUBSTRING DEFECT (new this pass). String.contains means `ratio`
    matches inside `corporation`/`operations`/`administration`, `solve` inside `resolve`, `current`
    inside `currently`, `integral` inside "an integral part of", `interest` inside `interested`,
    `balance` inside "balance sheet", `units` inside "business units" — all proved [PROD]. It
    explains why the scan is unreliable in BOTH directions. OUT OF SCOPE for both workstreams: word
    boundaries would flip guidance on an unknown share of the 4,874 notes that are quantitative via
    keywords only, permanently per note. Owes a Backlog Index row.
  - Business & Finance Domain Context | Finance as a Course/Program | Program Families | title
    cleanup (now <=3 post-rule cases, down from 4 — the curator fixed one) | broad biomedical
    backfill.

Stage 1 findings CORRECTED by this pass (each with evidence):
  1. Multi-program canonical health notes: 7 -> 18 [PROD]. Six are mechanism-framed and being
     MIS-INSTRUCTED right now, not one. Clause (a) now stands at 6 notes already authored AND
     already multi-program (Stage 1: ZERO on that strict reading), plus ~9 planned.
  2. NURSING: 35 -> 46. NULL: 5,682 -> 5,671. Total notes: 7,617.
  3. Fused `Nursing · Medicine` row: 2 -> 20 notes.
  4. Stage 1 §15's keyword table is WRONG on 2 of 4 rows. `Antibiotics: Mechanism of Action and
     Resistance` DOES trip (`resistance`, both tiers) — Stage 1 said "No, correctly." And
     `Pharmacokinetics: ADME` DOES trip at the full tier (`formula`, `ratio`) — Stage 1 said
     "apparently no" and built the whole keyword condition on that.
  5. Two of Stage 1's three proposed keywords are measurably wrong (above).
  6. Stage 1's 4 post-rule leaky titles is now <=3 — the curator renamed
     `Respiratory and Gastrointestinal Pharmacology for Nursing`. Stage 1's own finding decayed
     within the day, which is the snapshot rule applying to an audit rather than to a doc.

---

WORKSTREAM B — ACCOUNTANCY / BUSINESS / FINANCE / MANAGEMENT

Status:
  PRE-CPALE CALIBRATION. No Domain Context implementation.

ACCOUNTANCY retained:
  YES. The question was its boundary, never its existence.

ACCOUNTANCY current production adoption:
  ZERO notes [PROD 2026-09-13], while all 154 Accountancy-program notes sit at
  domain_context IS NULL. Unchanged from Stage 1 — the calibration window is STILL OPEN.

ACCOUNTANCY quantitative:
  KEEP TRUE

Reason:
  THE DECIDING MEASUREMENT: with quantitative=false, 145 of the 154 notes (94%) ALREADY trip the
  existing QUANTITATIVE_KEYWORDS scan on their real subject/tags/key-concepts/summary [PROD]. So
  `false` would change the outcome for only 9 notes — and reading all 9 individually, FIVE are
  genuinely non-computational (Audit Evidence, Professional Skepticism, Audit Planning, Compliance
  Testing, Disclaimer of Opinion) while FOUR are genuinely computational (Estate Tax Fundamentals,
  Individual Income Taxation, Risk and Return, Residual Income).

  quantitative=false is therefore a 5-SAVE / 4-LOSE TRADE ACROSS 154 NOTES — not a protective
  change but a coin flip. At the Quick Review tier it is worse: only 94 of 154 trip, so `false`
  would strip guidance from 60 notes there.

  And the asymmetry argument that correctly carried `false` for BASIC_MEDICAL_SCIENCES DOES NOT
  TRANSFER. That argument rests on the false-negative class being REPAIRABLE by a precise keyword —
  and it is, for pharmacokinetics ("pharmacokinetic" is discipline-specific and was measured
  working). No such token set exists for accounting computation: the discriminating words are `tax`,
  `cost`, `income`, `return`, `capital`, `depreciation` — generic English, catastrophic under
  substring matching. With the repair unavailable, permanence is SYMMETRIC and `true` wins.

  Non-computational material is not harmed, for four reasons: the guidance is explicitly HEDGED
  ("include computation WHEN APPROPRIATE / only when the notes clearly support them", :1503-1510);
  the systematically non-computational slice is Auditing at ~16 of ~144 notes (~11%) once RFBT
  moves to PPR; 11 of those 16 already trip keywords anyway; and §B9 gives the mitigation — the 5
  conceptual audit notes sit at NULL today and correctly trip NOTHING, so leaving them NULL is
  already the honest handling and is the ONE place ACCOUNTANCY(true) genuinely over-applies.

Persisted-note compatibility of changing the flag now:
  ZERO CONSEQUENCE. ACCOUNTANCY carries no notes, so no Study Pack was generated under it. THE
  WINDOW CLOSES PERMANENTLY AT FIRST BROAD ADOPTION, because Study Packs never auto-regenerate —
  which is exactly why this is decided NOW rather than deferred.

Mass-assign ACCOUNTANCY to current corpus:
  NO. Tightened from Stage 1, which proposed classifying all 154 as a zero-cost experiment. It is
  not zero-cost: it would assert an accounting treatment on ~21 program-neutral notes, sweep 10
  RFBT notes into quantitative=true when PPR's false is correct for them, permanently fix
  quantitative behaviour on all 154 at once, and DESTROY THE VERY EVIDENCE the future re-audit
  needs by removing the NULL marker that identifies the program-neutral cluster.

Business & Finance Domain Context:
  DEFER

State of hypothesis:
  STRONG — and stronger than Stage 1 recorded, on two counts: §B6 quantified the cluster
  (24 management notes vs 9 finance), and §B2 established the corpus is measurably classifiable by
  treatment. Per brief §8 it must NOT be described as lacking conceptual evidence.

Why deferred:
  The missing evidence is CURATED CROSS-PROGRAM APPLICABILITY, not content. Test A is UNFALSIFIED
  rather than failed (the nearest existing value has never been applied to one note). Test B has
  ZERO instances — no business note carries 2+ programs; Business Administration's 17 rows are 16
  Project Management notes already correctly on ENGINEERING_SCIENCES plus ONE real business note;
  ABM has 3. Guard 2 (ADR-001:406) therefore presumes it a program mirror of ACCOUNTANCY. Volume is
  NOT the blocker: ~24-28 program-neutral notes exist. No cpale- TSV exists in docs/curriculum/.

Management materially represented:
  YES — DECISIVELY, AND COUNTED RATHER THAN ASSERTED. [PROD] MAS 11 + Managerial Decision Making 5
  + Decision Making 4 + Performance 4 = 24 MANAGEMENT notes, against 9 Financial Management notes.
  The management cluster is 2.7x the finance cluster: Balanced Scorecard, ROI, residual income,
  transfer pricing, responsibility accounting, standard costing, variance analysis, limiting-factor
  analysis, make-or-buy, product mix, special-order decisions, linear programming, decision trees,
  EOQ.
  CONSEQUENCE FOR NAMING: "Business & Finance" is demonstrably TOO NARROW for the corpus that
  actually exists — the v0.99.0 description defect arriving in the NAME rather than the
  description. It stays the leading hypothesis and is NOT ratified. Do not mint "Business &
  Management" merely because the brief names it. Compare only AFTER the applicability map exists,
  and only among BORROWED curriculum terms (ADR-001:405).

Finance Course/Program:
  ADD WHEN THE CURATION TRIGGER FIRES

Finance trigger:
  The FIRST time CPALE curation identifies a canonical Note genuinely applicable to Finance — i.e.
  the first moment a CPALE/Business/ABM curriculum plan lists `Finance` in an applicable_programs
  cell. ONE note, not ten: the ~10-note floor is DOMAIN CONTEXT governance (ADR-001:397) and has
  never governed Course/Program applicability. Keep the two decisions separate. Adding Finance does
  NOT justify a Finance Domain Context (ADR-001:399), which would serve one live program and be
  presumed a program mirror. [PROD] Finance is absent from all 51 catalog rows. Do NOT pre-seed:
  Business Administration already exists and has attracted exactly ONE genuine business note.

Business shared-context re-audit trigger:
  >=10 canonical Notes whose treatment remains materially unchanged across 2+ legitimate live
  Course/Programs, arriving via a committed CPALE/BSBA/ABM curriculum plan in docs/curriculum/.
  Today: 0. Then re-run tests A-E and guard 2.

RFBT:
  PROFESSIONAL_PRACTICE_AND_REGULATION where validated (§A7). Do NOT create BUSINESS_LAW — 10
  notes, one live program, and PPR's ratified justification (ADR-001:336, "legal, procedural and
  regulatory rather than scientific") already fits. PPR's quantitative=false is correct for them,
  which is a second independent argument against sweeping them into ACCOUNTANCY(true).

Auditing:
  ACCOUNTANCY on current evidence. Revalidated against content: every audit note read is framed on
  financial-statement assertions and audit opinions — accounting-profession-specific. Do NOT create
  AUDITING / AUDIT_AND_ASSURANCE. BRANCH CLOSED.

Taxation:
  NOTE-LEVEL CLASSIFICATION; NO NEW TAXATION CONTEXT. Default ACCOUNTANCY because every note read
  is Philippine-statute-specific (RA 7160, BIR administration, the P3M VAT threshold) — NARROWER
  than Accountancy, not broader. But allow note-level divergence, and ⚠️ §A6's PPR Taxation routing
  sentence is MANDATORY or a widened PPR silently swallows all ~19.

Operations Research:
  NULL / DEFER. 3 notes (linear programming, decision trees, EOQ), far below the floor. Do NOT
  create Operations Research / Decision Sciences / Quantitative Methods. Do NOT force
  ENGINEERING_MATHEMATICS — its treatment fits but its NAME is engineering-specific and would
  violate ADR-001:405.

Program Family:
  NO.

Next required product work:
  CPALE COMPREHENSIVE CURRICULUM + APPLICABLE PROGRAMS CALIBRATION.
  This is CURATION, not Domain Context implementation. Follow docs/curriculum/'s established
  pipeline — run review-set-reshape-read.sql, paste results with REVIEW_SET_SHAPING_CONTEXT.md,
  save the TSV verbatim, run build_review_set_workbook.py. ⚠️ Every plan file MUST carry an
  applicable_programs column filled on every row — the builder REFUSES a file without it, and it
  has been lost twice. ⚠️ Never hand-transcribe a strategist's proposal into rows.
```

---

## Closing principles

> **Programs answer who can legitimately study the knowledge.**
>
> **Domain Context answers how the knowledge should be treated during generation.**
>
> **Review Set membership must never choose Domain Context.**
>
> **A shared Domain Context is justified by stable treatment across programs, not merely by a shared
> subject name.**
>
> **An honest NULL is better than a misleading Domain Context.**
>
> **Basic Medical Sciences is ready because the shared-treatment evidence already exists** — and
> since Stage 1 it stopped depending on a plan file: six notes are already authored, already assigned
> to three live health programs, and already being mis-instructed today.
>
> **The business-side gap should be resolved through CPALE applicability curation before a new shared
> business Domain Context is created.**

**DO NOT IMPLEMENT.**

---

## Housekeeping

**This file needs a row in `ROADMAP.md`'s Backlog Index at the next release kickoff**, per `CLAUDE.md`
kickoff step 8 (every `docs/claude-plans/` file must carry a row). **Not added here** — this plan
modifies no other file.

### Sibling untracked files — checked, not re-audited

`git status` shows **seven** untracked files in `docs/claude-plans/` + `docs/claude-findings/`. Grepped
against `ROADMAP.md` and `RELEASES.md`:

| File | ROADMAP row? | Note |
|---|---|---|
| `docs/claude-findings/2026-09-12-bulk-regeneration-modal-wedged-stale-batch-id.md` | ✅ **Yes** | `ROADMAP.md:433` — indexed with the finding summarized in-row |
| `docs/claude-plans/2026-09-12-bulk-regeneration-404-terminal-state-fix-plan.md` | ✅ **Yes** | `ROADMAP.md:433`, same row. Investigator recommends opening as `v0.145.0` |
| `docs/claude-plans/note-visibility-learning-status-stage1.md` | ✅ **Yes** | `ROADMAP.md:434` — with an explicit warning that the row is *"a compressed pointer, not a substitute for the file"* |
| **`domain-context-biomedical-business-calibration-stage1.md`** | ❌ **NO** | This plan's Stage 1 predecessor. **Owes a row.** |
| **`cross-note-review-consolidation-stage1.md`** | ❌ **NO** | **Owes a row.** |
| **`subject-review-cross-note-consolidation-stage2.md`** | ❌ **NO** | **Owes a row.** Appears to be the Stage 2 of the above. |
| **`artifact-first-learning-availability-stage2.md`** | ❌ **NO** | **Owes a row.** |

> **⚠️ FIVE `docs/claude-plans/` files — the four above plus this one — currently have no Backlog
> Index row.** `CLAUDE.md` calls kickoff step 8 *"the only enforced checkpoint against a large planning
> effort silently going unindexed across release cycles,"* and its own history records step 8 catching
> one unindexed file per cycle for three consecutive releases. **This is five at once**, and at least
> three of them are Stage 1/Stage 2 pairs representing substantial work. Flagged for the next kickoff;
> **not added here**, per the brief's instruction to modify no other file.
>
> These files are untracked by owner instruction (`ROADMAP.md:433-434` records that explicitly for
> two of them), so the Index row is currently the **only** in-repo record that the work exists.

### Two further items this pass owes the Index

1. **⚠️ The `QUANTITATIVE_KEYWORDS` substring defect (§A1.4, §A12) needs its own Backlog row.** New
   this pass, proved `[PROD]`, out of scope for both workstreams, and it silently affects which of
   4,874 notes receive computation guidance. **Record the §A5.6 coupling in the same row**: fixing it
   with word boundaries would break the `"pharmacokinetic"` entry this plan recommends adding.
2. **`ADR-001`'s two stale lines** (`:377-378` *"three unused values"* — two are now in use at 232 and
   46; `:403` *"41 programs"* — `[PROD]` **51**). Stage 1 identified both; neither has been corrected.
   §A8 item 12 folds them into the release's ADR edit, which is the cheapest moment to fix them.

### A note on this plan's own decay

Every `[PROD]` figure here was read **2026-09-13 — one day stale as of authoring (2026-09-14)** — and
**the biomedical population moved 2.6× within a single day** (§A1.3), so a one-day-old read on this
particular population is not a safe one. `CLAUDE.md`'s snapshot rule applies to this document as
forcefully as to any other: **re-read the counts before any of them reaches a kickoff, a prompt or a
release note.** The
figures most likely to have moved are `NURSING`'s 46, the 18 multi-program notes, and the fused row's
20 — the three that moved between Stage 1 and Stage 2. `ACCOUNTANCY`'s zero and the 154 NULL rows have
been stable across both reads.
