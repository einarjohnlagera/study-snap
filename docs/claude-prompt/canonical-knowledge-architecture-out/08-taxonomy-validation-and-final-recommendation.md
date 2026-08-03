# Taxonomy validation (Query K) + answers to the seven governance questions

Written 2026-08-03 after `06` §7's Query K ran against production. **Query K invalidates part of `06`'s Tier 1 and Tier 2 proposal. `06` should be read through this document, not on its own.**

---

## Part 1 — What Query K changed

### Finding 1 (blocking): three proposed Domain Context values already exist as *subjects*

| Proposed as Domain Context in `06` | Already a `notes.subject` value | Notes |
|---|---|---|
| `Engineering Mathematics` | yes | **10** (Civil Engineering) |
| `Professional Education` | yes | **250** (Education) |
| `Nursing` | yes | **62** (Nursing) |

And the same shape appears without my proposing it: `Accountancy` as a subject (8), `Architecture` (3), `Civil Engineering` (1 under CE + 1 under Architecture).

This is not a naming coincidence — it is the **existing subject-guidance rule already being violated**. `OpenAiLlmStudyPackService.buildSubjectSuggestionGuidanceBlock` (`:1553-1564`) instructs: *"Do not suggest overly broad subjects such as Business, Medicine, Engineering, or Law"* and *"Do not echo the course/program name as the subject."* `Engineering Mathematics`, `Professional Education`, `Nursing`, `Accountancy`, and `Architecture` as subject values are exactly what that rule exists to prevent — ~334 notes carry a too-broad subject today.

**Recommendation — turn the collision into a signal, not a prohibition.** Subject and Domain Context are genuinely different axes, and a value legitimately appearing in both is not an error (a broad survey note about nursing really is `subject = Nursing`). But for a small team it is a footgun. Since Domain Context is a *curated closed set* and Subject is *open text*, the cheap guard is:

> When a note's `subject` equals its `domain_context`, surface an admin-side warning that the subject is probably too broad — a nudge, never a hard validation error.

That reuses the intent of a rule the codebase already has, costs one comparison, and needs no new vocabulary. **Do not rename the Domain Context values to dodge the collision** — see Q5, where borrowed board-subject-area names are the reason the vocabulary reads well to learners.

### Finding 2 (structural): the sharing is *ragged and cross-family*, not nested

Civil Engineering's 24 real subjects, split by how widely their treatment is genuinely shared:

| Likely shared with | Subjects | Notes |
|---|---|---|
| **all 11 engineering programs** | Engineering Mathematics (10), **Engineering Laws, Ethics, and Contracts (9)** | 19 |
| **most engineering programs** | Strength of Materials (10), Engineering Mechanics (9) | 19 |
| **several** | Hydraulics (12), Environmental Engineering (10) | 22 |
| **two — and one crosses a family boundary** | Surveying and Geomatics (14) → Civil + Geodetic; Construction Materials (11) → Civil + **Architecture** | 25 |
| **Civil only** | Structural Analysis, Steel Design, Reinforced Concrete Design, Geotechnical, Foundation Engineering, Soil Mechanics, Transportation, Hydrology, Water Resources I & II, Water Supply, Hydraulic Structures, Construction Engineering & Management, + strays | 126 |

Two consequences, both important:

**(a) `06` missed a bundle — and the argument for it is *treatment*, not applicability.** `Engineering Laws, Ethics, and Contracts` (9 notes) sits beside Architecture's `Professional Practice` (12), `Building Laws` (15), and `BP 334` (32) — **68 notes whose treatment is legal, procedural, and regulatory rather than scientific or mathematical.** That is a genuinely distinct authoring treatment with no proposed context, and 68 notes clears the governance floor comfortably.

Being precise about which claim carries the weight: the justification is **treatment differs**, which holds regardless of how many programs share it. **How widely it applies is a separate, unverified syllabus question** — in exactly the same category as `Engineering Sciences`' span, and it must not be asserted. Every note currently in this bundle is Civil Engineering or Architecture; whether all 11 engineering boards carry an equivalent laws/ethics component is for the curator to confirm, not for me to claim.

**(b) This is now empirical proof that applicability cannot be derived from anything.** `Construction Materials` is shared by Civil Engineering and Architecture — *different program families*. So applicability cannot be derived from Program Family. And `Engineering Sciences` would over-apply if it carried applicability, since Strength of Materials and Hydraulics are shared by different subsets. **Explicit per-note Applicable Programs is forced by the data**, which retires `01` §4's alternative A1 (inverse mapping, program → domain contexts) definitively — it is now empirically dead, not merely theoretically weak. See Q6.

### Finding 3: `06` over-created two contexts, by its own governance rule

- **`Health Sciences Foundation`** — justified in `06` by the Nursing/Pharmacy Pharmacology split. But Pharmacy has **one** generic pharmacology note, and Pharmacy + Physical Therapy + Medicine total ~38 notes. That is Tier 3 territory. **Drop it from Release A.**
- **`Computing`** — Information Technology (74) + Software Engineering (4). Students identify with the program names, not with "Computing," and there is no evidence yet of shared treatment needing a joint context. **Drop it from Release A.**

Applying the owner's own proposed governance rule (Q3) to my own draft removes two of ten values. That is the rule working.

### Revised value set for Release A — 8 values

**Engineering (unblocks the Algebra work)**
1. `Engineering Mathematics` — Algebra, Trigonometry, Analytic Geometry, Calculus, Differential Equations, Probability & Statistics, Engineering Economics
2. `Engineering Sciences` — Strength of Materials, Engineering Mechanics, Hydraulics/Fluid Mechanics, Thermodynamics, Engineering Materials
3. `Civil Engineering` — Structural Analysis & Design, Steel/RC Design, Geotechnical, Foundation, Soil Mechanics, Surveying, Transportation, Hydrology, Water Resources, Construction Management
4. **`Professional Practice & Regulation`** *(new — Finding 2a; justified on **treatment**, 68 notes; its applicability across engineering boards is unverified)* — Engineering Laws/Ethics/Contracts, Architecture's Professional Practice, Building Laws, BP 334

**Existing content**
5. `General Education` — absorbs the 49 level-in-program notes; level carried by `learner_level`
6. `Professional Education` — Educational Psychology, Assessment of Learning, Curriculum Development, Teaching Profession
7. `Nursing` — Med-Surg, Psychiatric, Pediatric, Maternal & Child, Fundamentals, nursing-framed Pharmacology
8. `Accountancy` — FAR, Taxation, Auditing, MAS, RFBT, Financial Management

**`Architecture` — DECIDED 2026-08-03: no Domain Context; use the program-name fallback.** Its five big subject plans (Site Planning 175, National Building Code 151, Architectural Design 141, Building Technology 140, Building Utilities 137) are Architecture-specific, so a context for them would reduce no duplication — and duplication reduction, not note volume, is what earns a Domain Context. At 837 notes this is the clearest worked example of the governance rule: **a program can be among the largest in the library and still not warrant a Domain Context.** It gets promoted if and when shared canonical knowledge accumulates (`Construction Materials` and `Professional Practice & Regulation` are already carved out of it, which makes promotion *less* likely, not more).

> **Still unverified, and it matters:** every "likely shared with" grouping above is my inference from subject names plus general knowledge of Philippine engineering curricula. **I have not checked current PRC board syllabi.** Whether `Engineering Sciences` spans 8 or 11 programs, and whether `Engineering Laws, Ethics, and Contracts` is universal, are syllabus facts that must be curator-verified before the applicability defaults are set.

### Data hygiene surfaced in passing (pre-existing, out of scope, will surface during the catalog work)

`COMMUNICABLE DISEASE` in all caps (Nursing); ~334 notes with program-name-as-subject; `Structural Engineering` (2) alongside `Structural Analysis` (10); `Water Resources Engineering II` as a roman-numeral variant of `Water Resources Engineering`; `Construction Engineering` (1) alongside `Construction Engineering and Management` (10); and wildly inconsistent subject granularity in Accountancy, where `PPE` (4), `Inventory` (4), `Equity` (4) and `Cash Flow` (6) are **topics** sitting in the subject field beside proper subjects like `Financial Accounting` (37). **Domain Context will not fix subject granularity, and may make it more visible.** Worth its own Backlog Index row.

---

## Part 2 — Answers to the seven questions

### Q1 — Does the architecture actually achieve the success metric?

**Partially, and Query K lets me quantify which part rather than assert it.**

Of Civil Engineering's 211 notes, roughly **85 (40%) sit in potentially-shared subjects** and **126 (60%) are Civil-specific**.

The avoided-duplication figure must be computed **per bundle**, not by multiplying all 85 by 11 — the whole point of Finding 2 is that these subjects are shared by *different* subsets, so a flat multiplier would contradict the finding it rests on:

| Shared with | Notes | × programs | Copies under duplication | Avoided |
|---|---|---|---|---|
| all 11 (Engineering Mathematics 10, Laws/Ethics 9) | 19 | 11 | 209 | **190** |
| most, assume ~8 (Strength of Materials 10, Engineering Mechanics 9) | 19 | ~8 | ~152 | ~133 |
| several, assume ~6 (Hydraulics 12, Environmental 10) | 22 | ~6 | ~132 | ~110 |
| two (Surveying → Civil+Geodetic 14; Construction Materials → Civil+Architecture 11) | 25 | 2 | 50 | 25 |
| **Total** | **85** | — | **~543** | **~460** |

**Only the first row is high-confidence** — Engineering Mathematics is unambiguously universal across engineering boards. The `~8` and `~6` multipliers are my estimates, not verified against syllabi, and they carry most of the uncertainty. So the defensible claim is:

> **~190 notes of duplication avoided with high confidence, plausibly 400–550 once the shared/specific split is verified against real PRC syllabi** — plus, in every case, the Study Packs, question pools, flashcard sets, memorization decks, and ongoing maintenance those notes would each carry.

Do not quote a single precise figure. A sharp number derived from unverified groupings reads as more certain than its inputs support, and this one would get repeated.

**But be precise about what it does not do**, because the metric as written ("significantly reduces the effort required to build comprehensive Official Review Sets") is broader than what this architecture touches:

1. **It does not reduce the 60% program-specific authoring load** — the bulk of any single Review Set.
2. **It does not help assembly.** Adding notes to each program's Review Set stays manual per program. Review Sets compose by explicit reference, deliberately.
3. **The gap to "several hundred notes" is mostly volume, not duplication.** Civil Engineering already has 211 notes authored, and the four comprehensive Review Sets average 58 by rollup. Getting to several hundred is an authoring-volume and assembly problem that this architecture does not address.

**RATIFIED WORDING (owner, 2026-08-03):** the metric is now *"Eliminates duplication of shared knowledge as NoteLib expands to more programs."* Comprehensive Review Sets, faster curriculum expansion, and lower maintenance are downstream benefits, not the architectural guarantee.

**Honest verdict:** the architecture removes the *multiplier* on every program after the first. It does not reduce the cost of the first. For an expansion from 4 programs to 15+, removing the multiplier is the dominant term — so yes, it achieves the metric, but the metric should be restated as *"eliminates duplication of shared knowledge as program count grows"* rather than *"reduces the effort to build a Review Set,"* which over-promises and would read as a failure when CE still takes real work to finish.

### Q2 — Is the stronger rule better?

**No — the longer version is more specific but operationally worse.** Adding four conjunctive criteria ("learning objectives, terminology, examples, and expected treatment remain materially identical") invites *more* disagreement, not less: if terminology matches but examples differ, is it identical? A curator now has four axes to argue over instead of one judgment to make.

**Keep the short rule as the statement, and add a binary test plus a tiebreaker:**

> **Rule:** Domain Context is the coarsest label under which the note's treatment is identical.
>
> **Test (binary, curator-answerable):** would a student in a sibling program be served by this exact note, unchanged?
>
> **Tiebreaker, only when the test is genuinely unclear:** compare learning objectives, terminology, examples, and depth. First material difference decides.

**And the real objectivity gain is not in the wording at all.** Because Domain Context is substituted into the generation prompt, there is an *empirical* test available that no amount of definitional precision can match: **generate the note under both candidate contexts and compare the output.** For a two-person team that is faster and more decisive than adjudicating definitions. Recommend documenting that as the tie-break of last resort — it is the one advantage of this field being prompt-facing.

### Q3 — Should the governance rule be in the ADR?

**Yes, unambiguously — this is the most valuable of the seven proposals.** Your wording is good; it needs one thing added, because "a sustained body of canonical knowledge" is unfalsifiable as written and a future curator under deadline pressure will read it generously.

Proposed ADR text:

> **Domain Context governance.** A new Domain Context value may be introduced only when **both** hold: (a) there is a sustained body of canonical knowledge — as a concrete floor, **~10 or more notes already authored or firmly planned** — whose treatment cannot be accurately represented by an existing value; and (b) an explicit owner decision is recorded in this ADR's revision log. **When in doubt, reuse an existing Domain Context.**
>
> **Failure condition, reviewed at every `/kickoff`:** if the number of Domain Context values ever approaches the number of course programs, the taxonomy has failed and collapsed back into the field it replaced. Baseline at ratification: **8 contexts against 27+ programs.** A ratio trending toward 1:1 is the signal to stop and consolidate, not to keep adding.

The failure condition is the part that makes this real governance rather than an aspiration — it is measurable, has a recorded baseline, and rides the review ritual that already exists.

### Q4 — Should the program fallback be documented as temporary?

**Yes — and better, make it mechanically visible rather than only documented.** Prose intent decays; a queryable state does not.

**Do not add thin-program fallbacks to the curated `domain_contexts` table at all.** Let them resolve through the existing chain (`domain_context` → `course_program` → user's `course_program`). Then **`domain_context IS NULL` *is* the marker of "not yet promoted"**, and the promotion backlog becomes a one-line query:

```sql
SELECT course_program, COUNT(*) AS notes_awaiting_a_domain_context
FROM notes WHERE domain_context IS NULL AND course_program IS NOT NULL
GROUP BY course_program ORDER BY notes DESC;
```

A curator who runs that sees exactly which programs have crossed the ~10-note governance floor. That is self-documenting in a way a paragraph never is — and it costs nothing, because the fallback chain has to exist anyway for user-authored notes.

### Q5 — Does the vocabulary read naturally to learners?

**Query K answers this with evidence, and the answer is a clean principle.** Your existing subject names already *are* learner-facing vocabulary, and they are good: `Medical – Surgical Nursing`, `National Building Code`, `Educational Psychology`, `Reinforced Concrete Design`, `Assessment of Learning`. Students recognize these because they are the board exam's own subject areas.

| Value | Reads to a student? |
|---|---|
| `Engineering Mathematics` | **Excellent** — a board subject area, instantly recognized |
| `General Education` | **Excellent** — universally understood in PH education |
| `Professional Education` | **Excellent** — a LET subject area, and already a subject on 250 notes |
| `Nursing`, `Accountancy`, `Architecture`, `Civil Engineering` | **Fine** — program names, unambiguous |
| `Engineering Sciences` | **Adequate** — recognized as a grouping, less crisp than the others |
| `Professional Practice & Regulation` | **Adequate** — clear but slightly formal; `Laws, Ethics & Practice` may read better |
| ~~`Health Sciences Foundation`~~ | **Poor — invented-sounding, no student says this.** Dropped (Finding 3) |
| ~~`Computing`~~ | **Weak — vague; students say "IT" or "Software Engineering."** Dropped (Finding 3) |

**The principle:** the vocabulary reads well exactly where it borrows names learners already encounter — board subject areas and recognized curriculum bundles — and reads badly where I invented a grouping. **Derive Domain Context names from real curriculum vocabulary; never invent one.** Both values that failed the learner test were my inventions, and both also failed the governance rule independently. That correlation is not a coincidence: an invented name is usually a sign there is no real shared body of knowledge behind it.

No separate learner-facing label layer is needed. Adding one would mean two vocabularies to keep in sync for a two-person team — pure cost, no benefit, once the internal names are already the learner-recognized ones.

### Q6 — Is the Domain Context / Program Family separation still cleanest?

**Yes, and Query K upgrades this from "clean" to "forced by your data."** Two facts do it:

- **`Construction Materials` is shared by Civil Engineering and Architecture** — *different program families*. So applicability cannot be derived from Program Family.
- **`Engineering Sciences` subjects are shared by different subsets** (Strength of Materials broadly, Hydraulics narrowly). So applicability cannot be derived from Domain Context either.

Sharing is ragged and crosses family boundaries. Therefore **explicit per-note Applicable Programs is the only correct model**, Program Family is genuinely only an authoring shortcut, and Domain Context must stay the sole generation authority. Your separation is right.

This also **retires `01` §4's alternative A1** (inverse mapping: program → domain contexts, notes stay single-valued). I had kept it as a documented fallback if Step 3 proved too expensive. Query K kills it: under ragged cross-family sharing it would need a sparse per-note override table immediately, which is `note_course_program` with extra steps. **Remove it as a live fallback.**

### Q7 — Is "Domain Context" the best long-term name?

**Recommendation: `Domain Context`.** One argument decides it — the field's single job in code is to feed the prompt line that reads *"treat the {value} above as the authoritative academic **domain**."* A field named `domain_context` makes its responsibility self-evident to the next engineer who opens `buildGenerationContextBlock`; `domain_context` is mildly circular, since all note metadata is context about content.

`Authoring Context` is a close second and is the better name for *curators* (it names who acts on it). `Academic Context` is weakest — it excludes the professional and licensure content that is most of your library.

Two honest caveats: this is a genuine coin-flip whose cost is low either way, and the learner-facing concern from Q5 does not bind — a badge displays the *value* ("Engineering Mathematics"), never the field name. But **renaming is free only until Release A ships.** Decide before the migration, not after.

---

## Part 3 — Final deliverable

### 1. Would I still ratify?

**Yes.** The four-axis model is sound, the evidence supports it, and Query K strengthened rather than weakened it — the ragged cross-family sharing it revealed is direct proof that the axes must be separate. Ratify with the revised 8-value set, the governance rule, and the four items below closed.

### 2. What remains under-specified

1. **Subject/Domain Context collision** — ~334 notes carry a program-name-as-subject, and three proposed context values already exist as subjects. Needs the Finding 1 nudge rule decided.
2. **Applicability defaults are unverified against real syllabi.** Whether `Engineering Sciences` spans 8 or 11 programs is a curriculum fact neither I nor a language model should assert. Needs curator verification before family-expansion defaults are set.
3. **User-authored notes never get a Domain Context.** Normal users do not see the field (correctly — low friction), so their notes always resolve through the fallback chain and never receive canonical treatment. That is acceptable, but it should be *stated*, because it means Domain Context is an Official-content mechanism, not a product-wide one.
4. **Subject granularity is inconsistent and this architecture does not fix it** — Accountancy has `PPE` and `Inventory` as subjects beside `Financial Accounting`. Domain Context may make the inconsistency more visible on cards.

### 3. Hidden long-term risks

- **Domain Context becomes the new program field for curators** — because it will be the only *curated* vocabulary, it will attract every classification urge that has nowhere else to go. The governance rule's failure condition (Q3) is the guard; it must actually be reviewed at kickoff, not just written down.
- **Family shortcuts silently over-apply.** Given ragged sharing, "expand Engineering family" will over-assign programs on subjects like Hydraulics. The expansion must be *editable before save* and never re-applied automatically afterward.
- **The 40/60 split may not hold for later programs.** Civil Engineering is unusually mathematics-heavy; a program like Geodetic or Sanitary may share more, Chemical less. Do not build volume forecasts on 40%.
- **Two-vocabulary drift** if a learner-facing label layer is ever added over the internal names. Q5's recommendation is specifically to avoid this.

### 4. Changes recommended before implementation

1. Adopt the **8-value** set, not 10 — drop `Health Sciences Foundation` and `Computing`; add `Professional Practice & Regulation`.
2. Adopt the **governance rule with the numeric floor and the measurable failure condition** (Q3) into the ADR.
3. **Do not catalog thin-program fallbacks** — use `domain_context IS NULL` as the promotion marker (Q4).
4. Keep the **short rule + binary test + tiebreaker**, and document the generate-under-both empirical tie-break (Q2).
5. Decide the **`Domain Context` rename now**, before the migration (Q7).
6. Add the **subject-equals-context admin nudge** (Finding 1).
7. **Remove alternative A1** from `01` §4 as a live fallback (Q6).
8. Restate the **success metric** as *"eliminates duplication of shared knowledge as program count grows"* (Q1).

### 5. ADR

`docs/architecture/ADR-001-canonical-knowledge-architecture.md` is updated in the same change set as this document: revised value set, governance rule, fallback semantics, the ragged-sharing evidence, and the naming decision recorded as an open owner call.
