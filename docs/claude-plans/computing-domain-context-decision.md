# Computing Domain Context: Product Decision Recommendation

**Prepared:** 2026-09-29 · **Role:** Product Manager / UX Strategist evaluation · **Status:** Recommendation only. Nothing here is authorized for implementation.
**Inputs:** the team's consultation document (*"NoteLib Product UX Review: Computing Domain Context Gap"*); a consolidated repo + production audit (5 cold-context passes, read-only, 2026-09-29); ADR-001 (`docs/architecture/ADR-001-canonical-knowledge-architecture.md`) read directly for this evaluation; a post-synthesis verification pass (below) that checked the two external curriculum claims this recommendation depends on.
**Audience:** the product owner, then an external strategic reviewer who has **not** seen the audit. Every fact that reviewer needs is stated inline.

Each item below separates four kinds of statement:

- **[Existing behavior]**: what the product does today, anchored to code or ADR text.
- **[Audit finding]**: verified by the 2026-09-29 read-only audit (code reads or production `SELECT`s).
- **[Recommendation]**: my product judgment.
- **[Verify]**: a claim not yet confirmed that must be checked before anyone acts on it.

---

## ⚠️ Post-synthesis verification pass (read this first)

This document was drafted by an Opus synthesis pass that flagged several claims as **[Verify]** — recalled, not checked against sources. Before this went to the product owner, those claims were checked directly. Results:

**✅ Confirmed — the two external curriculum facts the whole recommendation leans on:**

1. **ACM/IEEE Computing Curricula (CC2020) does use "computing" as the umbrella term.** Verified via web search against ACM's own publication: *CC2020: Paradigms for Global Computing Education* (ACM/IEEE-CS, developed by a 50-member international task force) explicitly names five computing disciplines under that umbrella — **Computer Engineering, Computer Science, Information Systems, Information Technology, and Software Engineering** — the same five sibling programs this recommendation's scope test targets. Source: [ACM CC2020 report](https://www.acm.org/binaries/content/assets/education/curricula-recommendations/cc2020.pdf), [ACM bulletin](https://www.acm.org/articles/bulletins/2021/march/computing-curricula-2020-bulletin-educators).
2. **CHED CMO No. 25, s. 2015 does define "Introduction to Computing" as a shared core course across BSCS, BSIS, and BSIT.** Verified via web search corroborated by the official CHED-hosted PDF: the *Revised Policies, Standards and Guidelines for BSCS, BSIS and BSIT Programs* lists common courses across all three — **Introduction to Computing, Computer Programming 1 (Fundamentals of Programming), Computer Programming 2 (Intermediate Programming), Data Structures and Algorithms, Information Management, and Applications Development and Emerging Technologies** — totaling 18.0 units. Source: [ched.gov.ph, CMO No. 25, s. 2015](https://ched.gov.ph/wp-content/uploads/2017/10/CMO-no.-25-s.-2015.pdf) (existence and title confirmed; a direct fetch of the PDF's full text hit a transient tool outage, so the exact clause wording is corroborated by multiple independent secondary sources rather than a primary-source quote — sufficient for this recommendation, but a strategist doing final ADR drafting should pull the primary PDF directly).

**✅ Resolved in a follow-up pass, 2026-09-30, once the tool outage cleared:**

- **Catalog check (gates §3.5 Guard 2 and §7's Verify note).** `SELECT cp.name FROM course_programs cp WHERE cp.name ILIKE '%comput%' OR cp.name ILIKE '%information system%' OR cp.name ILIKE '%software engineer%' ORDER BY cp.name;` → **`Computer Engineering`, `Computer Science`, `Information Systems`, `Software Engineering`** all exist live (plus unrelated matches: `Accounting Information Systems`, `Certified Information Systems Auditor`, `Certified in Risk and Information Systems Control`). **No program named `Computing` exists** — confirming it does not collide with the naming rule's "no live catalog program name" guard. **This is materially stronger than the draft assumed**: `Computing` would serve **four** confirmed live sibling programs (CS, IT, IS, SE), not two. See the updated §3.5 Guard 2.
- **The 24 IT-tagged, `Engineering Sciences`-assigned notes (gates §6, Group C) — titles read.** All 24 fall into 8 distinct titles, none of them computing: *Cost Management in Engineering Projects*, *Procurement Management in Engineering Project Management*, *Project Communication in Engineering Project Management*, *Quality Management in Engineering Project Management*, *Risk Management in Engineering Project Management*, *Scope Management in Project Management*, *Stakeholder Management in Engineering Project Management*, *Time Management in Engineering Project Management*. **This reverses the draft's framing** — these are not a live misclassification of computing content and are not evidence for `Computing`. See the corrected §6 and §3.5.
- **Frontend test coverage (gates §10.2).** `frontend/lib/domain-context.test.ts` exists and pins the options array — confirmed as a required test-update site.

**⚠️ Still not found, low priority — does not gate the recommendation:**

- How `v0.145.0`'s `BASIC_MEDICAL_SCIENCES` addition was routed/delivered. `git log --all --grep="Basic Medical Sciences" -i` returned no hits — the commit likely used different phrasing than the ADR's own prose. Not re-attempted with a broader search; the §10.2 Claude-direct routing recommendation stands on its own stated reasoning (LOC count, no new logic, no migration) independent of this precedent.

---

## Summary

**Diagnosis.** There is a real taxonomy gap. The multi-program validation rule is not the cause; it is what brought the gap to light. The rule is behaving exactly as ADR-001 designed it.

**Direction.** Go back to **`Computing`**, the name ADR-001 has already rejected. Do it openly, through ADR-001's own amendment process, as a fresh, dated owner decision that explicitly supersedes the 2026-09-04 rejection. Do not change the validation rule. Every alternative fails either ADR-001's naming guard or the scope test, and `Computer Science` fails on exactly the ground that sank `Architecture`, because it matches a live program name (see item 3). `Computing` is borrowed vocabulary, not invented: it is the confirmed umbrella term of the ACM/IEEE *Computing Curricula* framework, and "Introduction to Computing" is the confirmed, literal title of a shared first-year course across BSCS, BSIS and BSIT under CHED CMO No. 25, s. 2015 — both now verified against primary/near-primary sources (see the verification pass above), not merely recalled.

**Biggest risk to this recommendation.** The shared-treatment evidence rests entirely on *firmly planned* rows, not authored notes — more entirely than first drafted, since a follow-up read (below) disproved the one piece of already-live "evidence" the draft leaned on. If BSCS foundations get written in a CS-specific way, `Computing` becomes a stand-in for the CS program, which is the `Architecture` failure.

**Urgent now.** 70% of the current BSCS Year 1 proposal (167 of 240 rows) sits on a literal `PENDING_PRODUCT_UX_DECISION` placeholder. This is a present blocker, not a future risk. (The 24 Information Technology notes assigned `Engineering Sciences` are a separate, already-resolved finding — see the verification note below and §6: their titles are confirmed generic engineering-project-management board-review content, not computing, so they are correctly placed and need no relabel.)

---

## Corrections to the consultation document before reading further

1. **Two Domain Context labels are misquoted.**
   - Item 10 is **"History and Theory of Architecture"**, not "Architectural History & Theory".
   - Item 11 is **"Planning and Site Development"**, not "Planning & Site Development".
   - The other ten match the Java enum `DomainContext.java` and its frontend mirror `frontend/lib/domain-context.ts`. Production holds exactly these 12 values, with no stray 13th.
2. **There are 128 IT-tagged notes, not 72.**
   - **72** are single-program (IT only), with Domain Context and Authored Depth both unset. This is the case the consultation describes.
   - **56** more are already multi-program, and **all 56 already carry a Domain Context**: 27 `General Education`, **24 `Engineering Sciences`**, 5 `Accountancy`.
3. **A "Computer Science" catalog program already exists.**
   - It has 48 tagged notes. "Computer Engineering" also exists, with 193.
   - Sampled CS-tagged notes are generic board-review material, not computing content. Examples: *Algebraic Expressions and Equations*, *Financial Mathematics in Everyday Life*, *Procurement Management in Engineering Project Management*.
   - The consultation's substantive claim, "almost no dedicated first-year CS foundations", holds. Its implied claim that no CS program or CS-tagged population exists does not.
4. **The multi-program rule is not a save-time rule.** The consultation says "the current server requires a Domain Context when a Note has multiple Applicable Programs." In fact it applies **only at Study Pack generation** (details in item 1).
5. **"Computing" is not untried.** ADR-001 records it as a rejected candidate (details in item 3).

---

## 1. Diagnosis of the actual problem

**[Existing behavior] How the model works.**
- Domain Context is *"the sole LLM domain constraint"* (ADR-001).
- The generation prompt tells the model to *"treat the domain above as the authoritative academic domain… Do not blend in material from unrelated disciplines."*
- A single value is substituted into that instruction. If Domain Context is unset, the resolver falls back to the one joined catalog program, then the note's legacy `courseProgram`, then the user's profile program.
- Applicable Programs (ADR-001's "Course / Program(s)") is discovery-only and never reaches the prompt.

**[Existing behavior] The rule.**
- The check is `programCount > 1 && domainContext == null`, which rejects with HTTP 400 `MULTI_PROGRAM_DOMAIN_CONTEXT_REQUIRED`. The error text reads: *"A note shared across several programs needs a Domain Context, so the AI knows which academic domain to write in."*
- It is identical in three places: `StudyPackGenerationContextResolver.assertGenerationReady`, `NoteGenerationService`, and `NoteBulkGenerationService`.
- It runs **only at generation time**. There is no database constraint (`domain_context` is a nullable `VARCHAR(64)`), and the save path that changes a note's programs (`NoteApplicableProgramsService.replace()`) never reads `domainContext`.
- **This is the ratified design, not a defect.** ADR-001 says multi-program notes with no Domain Context are *"valid to save, but rejected at generation, server-side."*

**[Audit finding] One document contradicts the code.**
- `docs/curriculum/review-set-workbook-spec.md:118-119` claims the server rejects such a *save*. That is false.
- `NoteService`'s own comment says multi-program notes may be saved without a Domain Context. Commit `ec347ccb` (2026-08-11) calls the save-time invariant one *"that no write path enforces."*
- The spec doc is stale. The design is not.

### Answering Question A: taxonomy gap, or the validation rule?

**It is a genuine taxonomy gap.** The two views of the rule are reconciled as follows.

- **"The rule is cheap to relax" is true in code.**
  - It is three `if` statements with no migration.
- **"The rule is foundational" is true in product terms, and that is what decides it.**
  - ADR-001 calls the prompt instruction *"logically unsatisfiable given a list"*: it would name several disciplines while forbidding any blending between them. The ADR calls this *"the founding observation of this ADR… why Domain Context exists as a separate single-valued axis at all."*
  - Relaxing the rule leaves two options for a multi-program note with no Domain Context:
    - **Send the program list to the model.** ADR-001 *explicitly rejects* this, and says revisiting it *"requires an R4-style generate-and-diff read first"*. R4 is ADR-001's earlier verification read, in which the same note was generated under two Domain Context values and the outputs compared. So the real price of relaxing is a structured generation experiment plus an ADR reversal, not three `if`s.
    - **Silently fall back to the note's legacy `courseProgram` or the author's profile program.** That makes the authoring domain depend on accident, which is the exact overlap the four-axis model was built to remove.
- **Conclusion.** The rule did its job. It stopped a list from becoming the domain signal, and it made the curator (and the strategist, who refused to write an illegal `(unset)`) state the real question: *what is the honest single authoring domain for shared computing foundations?* Today there is no honest answer in the vocabulary.
  - `Engineering Sciences` is dishonest for programming content. ADR-001's own ruling says *"multi-program applicability alone does not imply Engineering Sciences."*
  - `General Education` is dishonest for Data Structures.
  - The program-name fallback cannot apply, because the note has several programs.

**[Recommendation] Two further problems, separate from the taxonomy decision:**
- **(i) Enforcement and UX gap.** A curator can make a note non-generable by adding a second program, and nothing warns them until generation fails. This is a real usability defect, but relaxing the rule does not fix it (see items 8 and 10).
- **(ii) A misplaced population.** The existing "Computer Science" catalog program is filled with generic board-review notes. This is an Applicable Programs (discovery) problem. **A Domain Context decision cannot fix it and must not be used to fix it.**

---

## 2. New Domain Context, validation change, or another approach?

**[Recommendation] Add one new Domain Context value. Keep the validation rule unchanged. Correct the stale documents alongside it.**

| Option | Verdict | Why |
|---|---|---|
| **New Domain Context value** | **Recommended** | It is the only option that gives shared computing foundations an honest single authoring signal while keeping one canonical note across programs. |
| Relax or remove the multi-program rule | **Rejected** | It re-opens ADR-001's founding decision. It requires an R4-style read before it can even be proposed. It hands the prompt either a list (explicitly rejected) or an accidental fallback domain. It also fixes nothing about the 24 notes already put into `Engineering Sciences`. |
| Force computing into `Engineering Sciences` | **Rejected** | This is the anti-pattern the consultation warns about. ADR-001 rules against it. It has probably already happened to up to 24 notes (see item 6). |
| Put shared computing foundations in `General Education` | **Rejected** | It fails ADR-001's binary sibling test ("would a student in a sibling program be served by this exact note, unchanged?") in the other direction. A Nursing or Accountancy student is not the audience for Data Structures, and GE is a real, distinct CHED curriculum bundle. |
| Tag shared notes to a single program (CS only), leave Domain Context NULL, and use the program-name fallback | **The fallback only if the owner declines the new value** | It is legal today and honest ("an honest NULL beats a catch-all"). It gives up IT/IS/SE discoverability until the question is resolved. It is not the default, because it leaves the pack framed for CS permanently once generated (see item 9). |
| Split into per-program duplicate notes | **Rejected** | Violates the consultation's own constraints and ADR-001's canonical-note premise. |

---

## 3. Recommended taxonomy direction and rationale

### 3.1 The constraints any new value must pass

These are the ADR-001 governance guards, as amended 2026-09-04 in `v0.111.0` by owner decision.

1. **Naming rule.**
   - No Domain Context may equal a live catalog program name, unless that name is a documented subject area of a licensure curriculum.
   - Names are *"borrowed from real curriculum vocabulary… never invented."*
2. **Composition matrix, computed against programs that exist.**
   - *"A value serving exactly one live program is presumed a program mirror and must be justified explicitly or deferred."*
3. **Clause (a):** about 10 or more notes, already authored or firmly planned, whose treatment no existing value can accurately represent.
4. **Clause (b):** an explicit, dated owner decision recorded in ADR-001's revision log.
5. **Not a precondition:** *"A new Course / Program does NOT imply a new Domain Context"*. BSCS being a new degree is not evidence on its own.

### 3.2 Why every alternative name fails, which is why re-opening `Computing` is the only route

| Candidate | Fails on | Detail |
|---|---|---|
| **`Computer Science`** | Guard 1 and guard 2 | It equals a live catalog program name exactly, and there is no licensure curriculum behind it (no PRC board exam for CS). This is the guard that rejected `ARCHITECTURE` in `v0.111.0`. It would also serve one program, making it a presumed mirror. |
| **`Computing & Information Technology`** | Guard 1 (in spirit) | It embeds a live program name ("Information Technology") and tells the model to favour one sibling's framing over the others. |
| **`Information Technology Education`** (the CHED cluster name) | Guard 1 (in spirit) | Same problem as above. It also reads to curators as "IT", the program. |
| **`Software Engineering`, `Programming`, `Computer Programming`** | Scope | Too narrow for Discrete Structures or Introduction to Computing, and would drive the value set toward one value per course. |
| **Invented labels** (`Computing Foundations`, `Digital Sciences`, `Information Sciences`) | Naming rule | Exactly what ADR-001 forbids. |

### 3.3 Confronting the Architecture precedent directly

- **The precedent.** `Architecture` was rejected on 2026-08-03, owner decision, despite **837 notes**, because its subject plans were Architecture-specific: *"a context for them would reduce nothing. Note volume is explicitly not a qualifying criterion; shared treatment is."* It was rejected again in `v0.111.0` under the naming rule, for equalling the live program name.
- **The lesson for this case.** Nothing about BSCS's size, newness, or importance qualifies a value. A `Computer Science` value would repeat the `Architecture` mistake exactly: one program, program-named, and nothing reduced.
- **Why `Computing` is a different case.** It is shaped like the `v0.111.0` counter-precedent (`Architectural Design` and its two siblings):
  - it is treatment-based, not program-based;
  - its shared-treatment evidence spans **four confirmed live sibling programs** — Computer Science, Information Technology, Information Systems, and Software Engineering (see the verification-pass note above and §3.5 Guard 2);
  - the evidence comes from curation judgment plus a **now-confirmed** recognized curriculum structure (CC2020's five-discipline umbrella; CHED CMO No. 25 s. 2015's shared core), not note volume.
- **The honest limit.** Like `v0.111.0`, clause (a) here leans on the *"firmly planned"* reading (see 3.5).

### 3.4 The chosen path: re-open `Computing` explicitly (path (b))

**What the record actually says. Do not understate it.**
- ADR-001 cites `Computing` twice.
- The first citation is in the naming-rule paragraph. It says `Health Sciences Foundation` and `Computing` *"failed both the learner-comprehension test and the governance rule… independently. That correlation is the rule's justification: an invented name usually signals there is no real shared body of knowledge behind it."*
- The second sits inside the **`v0.111.0` amendment, dated 2026-09-04, which is itself clause (b)'s recorded owner decision**. It lists `Computing` among names *"rejected on this test."*
- So this is **a recorded owner decision, not an undated aside.** What is thin is the *reasoning*: the record never says why `Computing` counts as invented, and it cites no evidence. Unlike the `Architecture` entry, it contains no note counts, no programs examined, and no sibling test.
- Reversing it therefore needs a **fresh owner decision that explicitly supersedes the 2026-09-04 rejection** and is recorded as clause (b). Adding the value silently would be improper.

**The new evidence, answering both stated grounds of rejection — now verified, not recalled:**

1. **Governance / naming rule ("invented").** `Computing` is borrowed, arguably more directly than any currently ratified value.
   - **✅ Confirmed**: it is the discipline-family name of the international curriculum framework for exactly these programs: ACM/IEEE *Computing Curricula 2020 (CC2020): Paradigms for Global Computing Education*, which explicitly names Computer Engineering, Computer Science, Information Systems, Information Technology, and Software Engineering as the five disciplines under its "computing" umbrella. (See the verification-pass note above for sources.)
   - **✅ Confirmed**: in the Philippine curriculum, **"Introduction to Computing"** is the literal title of a first-year course shared across BSCS, BSIT and BSIS under CHED CMO No. 25, s. 2015, alongside Computer Programming 1 & 2, Data Structures and Algorithms, Information Management, and Applications Development and Emerging Technologies as the shared core (18.0 units). (See the verification-pass note above for sources.)
   - **✅ Confirmed**: `Computing` is not a live catalog program name — a follow-up catalog read found `Computer Engineering`, `Computer Science`, `Information Systems`, and `Software Engineering` as the live computing-adjacent programs, with no program named `Computing`. It is a Domain Context borrowing curriculum vocabulary, not colliding with a program name.
   - The *"invented name signals no real shared body of knowledge"* reasoning fails on the now-confirmed facts. The shared body is codified: CHED's common core for the three programs, and the CC2020 knowledge areas.
2. **Learner-comprehension test.**
   - ADR-001's own governance section says *"learners do not see this authoring vocabulary."* So the test can really only apply to **curators and the strategist**: can the person assigning the value reliably tell what belongs in it?
   - "Computing" is the name of a course every curator will be authoring. Its comprehension cost is low, given the boundary test in item 4.
   - **[Verify]** whether ADR-001 or its planning directory (`docs/claude-prompt/canonical-knowledge-architecture-out/`) defines this test more precisely. If it has a learner-facing surface I am unaware of, this argument must be re-made against it. (Not attempted this pass — lower priority than the two curriculum-fact checks above, which were the load-bearing ones.)

**[Recommendation] Proposed value.**
- Label **`Computing`**, enum `COMPUTING`, **`quantitative = false`** (reasoning in item 5).
- It represents a broad **authoring tradition**, not a degree program: the shared treatment of computation, information and software as objects of study.

### 3.5 Clause (a) evidence: stated honestly

| Evidence | Counts toward clause (a)? |
|---|---|
| The 72 single-program IT notes | **No.** They are representable today through the program-name fallback, so their treatment is not "unrepresentable by an existing value". They become relevant only when reused across programs. |
| The 24 IT notes currently set to `Engineering Sciences` | **No — resolved, and reversed from the draft's assumption.** Titles read (see verification note above): *Cost/Procurement/Project Communication/Quality/Risk/Scope/Stakeholder/Time Management in Engineering Project(s)* — 8 distinct titles, all generic engineering-project-management board-review content, none of it computing. These notes are correctly placed at `Engineering Sciences` (or at minimum are not miscategorized as computing) and contribute **nothing** to clause (a). They are the same over-tagging pattern as the "Computer Science" and "Computer Engineering" populations in §7 — an Applicable Programs (discovery) problem, not a Domain Context problem. See the corrected §6, Group C. |
| The 167 `PENDING_PRODUCT_UX_DECISION` rows in the current BSCS proposal (e.g. every "Introduction to Computing" row, tagged `Computer Science, Information Technology, Information Systems`) | **Yes, as "firmly planned"** — and now the entirety of the clause (a) case, since the 24-note row above no longer contributes. This is the same reading `v0.111.0` relied on, and it should be recorded as a limit in the same way, more prominently than the original draft implied. Not all 167 will be shared foundations; some will be CS-only and belong on the fallback. The subset that is genuinely 2+ program is what counts. **[Verify]** that subset's size by counting rows with 2+ programs in the TSV — still outstanding, not attempted this pass. |

**Guard 2 check. Fully confirmed this pass, and stronger than drafted.**
- The composition matrix is computed against programs that **exist**. A follow-up catalog read (`SELECT cp.name FROM course_programs cp WHERE cp.name ILIKE '%comput%' OR cp.name ILIKE '%information system%' OR cp.name ILIKE '%software engineer%'`) confirms **Computer Science, Information Technology, Computer Engineering, Information Systems, AND Software Engineering are all live today.** The same query confirms **no program is named `Computing`** — it does not collide with the naming rule.
- `Computing` would therefore serve **four** confirmed live sibling programs (CS, IT, IS, SE) at minimum, not two as first drafted. That is a meaningfully stronger position than any of the three `v0.111.0` values, two of which serve exactly one live program each.

**Watch figure.** 13 contexts across 51 programs gives a ratio of 0.255 (as of 2026-09-14 production; re-read the catalog count before quoting). That is well below the 0.40 threshold that re-opens the amendment.

---

## 4. Inclusion and exclusion boundaries

### 4.1 The binary boundary test

This is modelled on the `v0.145.0` "Test D" for Basic Medical Sciences. Test D asked whether a professional role appears in the knowledge itself, or only in who is reading it.

> **Is computation, information, or software itself the object of study, treated the same way regardless of which computing program is reading?**
> - **Yes → `Computing`.**
> - **No, it is physical/electrical engineering analysis (circuits, signals, device physics, board-exam engineering computation) → the existing engineering values**, or the Computer Engineering program fallback.
> - **No, it is general literacy or civic knowledge that happens to involve technology → `General Education`.**
> - **No, it is framed by one program's professional identity** (for example IT's systems-administration practice, CS's theory-of-computation formalism, IS's business-process framing) **→ leave Domain Context NULL and use the program fallback** until sibling sharing is shown.

It must be applied **per note**, alongside ADR-001's standing sibling test: *"would a student in a sibling program be served by this exact note, unchanged?"*

### 4.2 Worked cases

These are representative. Each is a judgment to be confirmed by a curator, not a ruling.

| Note (example) | Result | Reason |
|---|---|---|
| Introduction to Computing | **`Computing`** | Confirmed shared first-year course title across the three CHED programs (see verification pass). The treatment is identical. |
| Programming Fundamentals I/II (variables, control flow, functions, arrays) | **`Computing`** | Concept-level treatment is the same across CS, IT, IS and SE, which is exactly the "Variables and Data Types" case the consultation wants kept canonical. |
| Data Structures; Algorithms and Problem Solving; OOP concepts | **`Computing`** | Core shared knowledge areas. The sibling test passes for CS, IT, SE, and CpE software tracks. |
| Discrete Structures (logic, sets, relations, graphs, proof for algorithms) | **`Computing`** | Framed for computation, not board-exam engineering math. `Engineering Mathematics` is calculus- and board-oriented and would mis-frame it. **Edge case**: a generic "Sets and Logic" GE-math note stays `General Education`. |
| Computer Systems / Organization (abstraction, ISA, memory hierarchy) | **`Computing`** | The CS/IT framing. |
| Digital Logic Design, circuits, microprocessor interfacing (CpE-framed) | **Engineering values / CpE fallback** | Electrical-engineering analysis. Placing these under `Computing` would mis-frame CpE board content. |
| "Living in the IT Era" / basic ICT literacy / productivity tools | **`General Education`** | General literacy, not the study of computation. **[Verify]** the CHED GE classification of this course. |
| Data Privacy Act (RA 10173), cybercrime law | **Edge case, owner call** | This is legal/regulatory treatment. `Professional Practice & Regulation` is engineering-board regulatory in origin. Default: `Computing` only if framed as "Social and Professional Issues in Computing". Otherwise **NULL plus fallback**. Do not stretch `Professional Practice & Regulation`. |
| Theory of Computation / Automata | **NULL, CS fallback** | CS-specific. No sibling sharing. |
| Network/systems administration practice | **NULL, IT fallback** | IT-specific professional practice. |
| Accounting information systems, enterprise/business process | **`Accountancy`, or NULL + IS fallback** | Business-domain framing. |
| Generic algebra, financial math, engineering project management (the current CS-tagged population) | **Unchanged** (`General Education` / `Engineering Sciences`) | Not computing. Their CS tag is a discovery question (item 7), not a Domain Context question. |

### 4.3 Anti-catch-all guard

- ADR-001 rejected `General Engineering` as *"a vague catch-all… strictly worse than the honest signal NULL."* `Computing` carries the same risk if it becomes "anything technical".
- The guard is the boundary test above, plus a standing rule: **single-program computing specialization keeps NULL.**
- `Computing` must never be assigned merely because a note is technical, or merely because it is multi-program. This mirrors ADR-001's *"multi-program applicability alone does not imply Engineering Sciences"*.

---

## 5. Implications for Study Pack generation

**[Existing behavior] What a Domain Context value actually does.**
- The enum carries exactly **two properties**: `label` and `quantitative` (verified in `DomainContext.java`).
- A new value changes generation in two ways only:
  - **label substitution** into the prompt's authoritative-domain instruction;
  - **the `quantitative` flag**, which feeds whether quantitative question/problem handling applies.
- There is no per-value prompt guidance. Adding `Computing` therefore **does not** by itself add code-example, pseudocode, or language-neutrality guidance.

**[Recommendation] Question C: code, pseudocode, algorithms, and so on.**
- **Do not bundle computing-specific prompt guidance with the value.**
- ADR-001's R4 read (2026-08-04) showed a broad label does not make authored content drift generic in *engineering*. That has not been shown for computing.
- The right sequence:
  1. ratify the value;
  2. generate 3 to 5 representative notes (one conceptual, one programming, one Discrete Structures, one algorithms) under `Computing`, using the **existing** R4 runbook (`docs/claude-prompt/canonical-knowledge-architecture-out/17-r4-verification-runbook.md`) and not a new rubric;
  3. only if output is consistently mis-framed (for example, language-specific code where the concept is language-neutral, or code forced into a conceptual note) consider a separate, measured prompt change.
- The consultation is right that not every computing note needs source code, and nothing in the proposal forces any.
- **[Verify]** whether the study-pack prompts contain any existing code-block or pseudocode handling — a grep of `backend/src/main/resources/prompts/study-pack-v1/` was attempted and blocked by the transient tool outage this session; re-run before the first `Computing` generation.

**[Recommendation] `quantitative = false`.**
- The enum's own comments give the reason. `false` is a no-op that falls through to the existing keyword scan. `true` is *"permanent per note because Study Packs never auto-regenerate"*. `Professional Education` and `Professional Practice & Regulation` both shipped `true` and had to be corrected. The `v0.111.0` and `v0.145.0` values all shipped `false` as a decision.
- Computing mixes quantitative content (number systems, complexity, discrete counting) with non-quantitative content (programming concepts, systems).
- If a false-negative class appears, repair it the `v0.145.0` way: a measured, discipline-specific keyword added to `QUANTITATIVE_KEYWORDS`, checked against production. **[Verify]** after first generation.

**[Existing behavior] The asymmetry between the two fields.**

| Field changed on an existing note | Consequence |
|---|---|
| **Domain Context** | **Safe.** `NoteService.update()` writes a plain field. There is no auto-regeneration, no staleness flag, and no effect on already-served content. It only affects *future* generation requests. |
| **Authored Depth (`learnerLevel`)** | **NOT safe.** Board/Long Exam pools compare their stored level with the currently resolved level at sampling time (`ExamQuestionPoolService.sampleQuestions`). A mismatch **automatically refreshes the pool with no curator confirmation** the next time anyone starts a Board/Long Exam from that pack. Challenge Quiz's "Redo Missed Questions" bank silently orphans rows at the old level. |

**Corollary.**
- Relabelling a note's Domain Context **does not fix its existing Study Pack**. The pack stays as it was generated.
- Correcting content framing requires an **explicit, user-confirmed regeneration per note**, under the Versioning Rule that nothing is ever auto-regenerated.
- There is no bulk metadata endpoint. `domainContext` is editable only through the single-note editor, which requires resubmitting the note's full content.

---

## 6. Treatment of the existing IT notes (128 total)

**[Audit finding] Breakdown.**

| Group | Count | Programs | Domain Context | Authored Depth |
|---|---|---|---|---|
| A: clean single-program | 72 | IT only | NULL | NULL |
| B: multi-program, General Education | 27 | 2+ | `General Education` | (not reported) |
| C: multi-program, Engineering Sciences | 24 | 2+ | `Engineering Sciences` | (not reported) |
| D: multi-program, Accountancy | 5 | 2+ | `Accountancy` | (not reported) |

**[Recommendation] Answers to Question D.**

1. **Group A (72): leave untouched until a specific note is actually reused.**
   - They generate legally today under the IT program fallback. That is honest, and ADR-001 calls it transitional but legitimate.
   - When a BSCS row reuses one, the curator applies the boundary test in item 4 at that moment:
     - if it passes, set `Computing` (safe) and add the second program;
     - if it fails, it stays IT-only and BSCS gets its own note only if treatment genuinely differs.
   - **No blanket migration.** It gains nothing: the notes are not blocked. It costs about 72 one-at-a-time editor passes with no bulk endpoint, by one curator.
2. **Group C (24): reviewed and resolved — NOT a computing misclassification, no relabel warranted.** This is the group the consultation did not know about, and the finding reverses what the draft assumed.
   - **Titles read (2026-09-30):** 8 distinct titles across the 24 notes — *Cost Management in Engineering Projects*, *Procurement Management in Engineering Project Management*, *Project Communication in Engineering Project Management*, *Quality Management in Engineering Project Management*, *Risk Management in Engineering Project Management*, *Scope Management in Project Management*, *Stakeholder Management in Engineering Project Management*, *Time Management in Engineering Project Management*. All generic engineering-project-management board-review material. None is computing content under the item 4 boundary test.
   - **Verdict: `Engineering Sciences` is the correct (or at least defensible) placement for this content.** Applying the boundary test confirms these fail every branch that would route to `Computing` — they are not the object-of-study-is-computation case.
   - **No relabel, no regeneration review, no per-note action needed for these 24 specifically.**
   - **What this group actually is: a THIRD instance of the discovery-tagging pattern named in §7**, alongside "Computer Science"'s 48 notes and "Computer Engineering"'s 193 notes — generic multi-program board-review content tagged broadly across programs (here, IT plus others) rather than a Domain Context problem. Fold this into the same discovery-tagging review Backlog item recommended in §7, rather than tracking it as its own "Group C" line item.
   - **Consequence for the overall case**: this was the one piece of already-authored, already-live evidence the draft's clause (a) case leaned on. With it gone, clause (a) now rests entirely on the 167 "firmly planned" TSV rows (§3.5) — still a legitimate basis under the same reading `v0.111.0` used, but the case is now less diversified than first presented. This does not change the recommendation; it changes how confidently clause (a) can be stated in the ADR amendment.
3. **Groups B and D (32): out of scope.** No sign of misclassification. Re-examine only if a note is opened for another reason.
4. **Missing Authored Depth: do not set it in the same pass as Domain Context.**
   - Leave `learnerLevel` NULL on the 72, where the reader's level governs and falls back to `COLLEGE`, unless a separate, deliberate depth pass is authorized.
   - Before setting depth on any existing note, run a read-only check that it has no generated Board/Long Exam pool. For notes that do, treat the depth change as a deliberate regeneration decision, because the pool *will* silently refresh.
   - Domain Context and Authored Depth sit on the same form. Their blast radii are very different. Curator guidance must say so.
5. **Would assigning the new value trigger regeneration?** No. See item 5.

---

## 7. Implications for future computing-related programs

**[Recommendation] How `Computing` scales.**
- It produces the healthy shape ADR-001 asks for: **one context serving several programs.**
  - BSCS, BSIT, BSIS and BSSE share Introduction to Computing, programming fundamentals, data structures and algorithms under `Computing` — confirmed as a real, named CHED shared-core pattern for at least BSCS/BSIT/BSIS (see verification pass).
  - Computer Engineering shares only its software-facing notes. Its hardware and board content stays on the engineering values, where 162 of its 193 notes already sit under `Engineering Mathematics`.
- **Discipline-specific knowledge stays on the program-name fallback (NULL)** until sibling sharing is shown. Examples: CS theory of computation, IT systems administration, IS enterprise and business process, SE process and formal methods, CpE hardware.
- This means **no second computing value is foreseeable**. A future `Software Engineering` or `Information Systems` value would have to clear the same bar on its own, and would fail guard 1 if named after its program.
- **Distinguishing shared from discipline-specific knowledge.** The test is the sibling test, per note, at authoring time. It is not decided per course and not per program.
- **Interdisciplinary subjects** follow the same test:
  - data science is `Computing` if framed as computation;
  - statistics for GE stays `General Education`;
  - bioinformatics is a per-note call, and the default is NULL.

**[Audit finding] Adjacent problem, now confirmed to be THREE populations, not one: broad board-review tagging, unrelated to Domain Context.**
- The existing "Computer Science" catalog program carries generic algebra, financial math and engineering project management notes (48 notes).
- "Computer Engineering" is dominated by `Engineering Mathematics` content (162 of 193 notes).
- **Confirmed this pass**: the 24 IT-tagged notes flagged in §6 Group C are the same pattern — *Cost/Procurement/Quality/Risk/Scope/Stakeholder/Time Management in Engineering Project(s)*, generic board-review material broadly tagged across programs.
- All three are on the **Applicable Programs axis** (discovery), not Domain Context. Likely leftovers from broad board-review tagging that predates program-specific curation. Separate from the Domain Context question, and **must not be solved by adding or changing Domain Context.**
- Its learner-facing risk is real: ADR-001 warns that *"a thin shelf… looks like a curriculum without being one."* A BSCS learner browsing the program today sees algebra and procurement management.
- **[Recommendation]** Track as ONE Backlog item covering all three populations: a discovery-tagging review of the Computer Science (48), Computer Engineering (193, or at least its non-`Engineering Mathematics` subset), and the 24 IT/`Engineering Sciences` notes. **Do not solve it by adding or changing Domain Context**, and do not treat solving one as solving the other.
- **✅ Confirmed this pass**: "Information Systems" and "Software Engineering" both exist live in the catalog — see the verification note at the top of this document.

---

## 8. Risks and overlooked edge cases

| # | Risk | Mitigation |
|---|---|---|
| 1 | **Program-mirror collapse.** Shared-treatment evidence is mostly *planned*. If BSCS foundations get written in a CS-specific way, `Computing` effectively serves one program, repeating `Architecture`. | Record the "firmly planned" limit in the amendment, as `v0.111.0` did. Add a dated `[CHECKPOINT]` row: after BSCS Year 1 authoring, count `Computing` notes tagged 2+ live programs. If most are single-program, re-open the value. |
| 2 | **Catch-all drift.** Curators use `Computing` for "anything technical" (the `General Engineering` failure). | The boundary test in item 4, and the rule that single-program specialization stays NULL. |
| 3 | **Reversing a recorded owner decision** without addressing its stated grounds erodes the governance process. | The amendment must quote the 2026-09-04 rejection, answer both stated grounds, and be dated and owner-decided. |
| 4 | ~~The recalled curriculum facts (CHED CMO, CC2020) are wrong in detail.~~ **RESOLVED — confirmed by this pass** (see verification-pass note). | N/A. |
| 5 | **Generation quality under a broad computing label is unproven.** R4 covered engineering only. | Run the R4-style read on 3 to 5 notes before bulk generation of shared rows (item 9). |
| 6 | **Silent non-generable saves.** Curators, and the pipeline (`build_review_set_workbook.py` validates only that the column is present, not its values or program count), can create multi-program NULL notes that fail only at generation. The placeholder `PENDING_PRODUCT_UX_DECISION` would not be rejected by the script. | Correct the stale spec doc now. Optionally add a pipeline check and a non-blocking save-time hint (item 10, optional). A hard save-time block would contradict ADR-001's "valid to save" design. |
| 7 | **Authored Depth pool refresh** during a metadata clean-up. | Never pair a depth change with a Domain Context edit. Check pools first (item 6). |
| 8 | **Stale packs after relabel.** Relabelled notes keep engineering-framed packs. | Per-note, explicit regeneration decision (item 6). No sweep. |
| 9 | ~~Information Systems absent from the catalog.~~ **RESOLVED — confirmed present**, along with Software Engineering (see verification note). The 3-program TSV rows can be tagged as written. | N/A. |
| 13 | **Clause (a) evidence is now thinner than first drafted.** The 24 `Engineering Sciences` IT notes turned out not to be computing content (§6), removing the one piece of already-live evidence the draft cited. | The 167 "firmly planned" TSV rows are still a legitimate clause (a) basis (same reading `v0.111.0` used), but the ADR amendment should state this honestly as planned-only evidence, not imply live corroboration that isn't there. |
| 10 | **Strategist context doc forbids proposing values.** `REVIEW_SET_SHAPING_CONTEXT.md:64-65` hands the strategist a fixed 12-value list and says *"do not propose a new value."* | Correct behaviour until ratification. Update the list in the same change that ratifies `Computing`, or the strategist will keep emitting placeholders. |
| 11 | **Frontend and backend version skew.** The enum and the frontend combobox deploy through separate integrations. | The backend must accept `COMPUTING` before the frontend offers it. Backend-first is safe for an additive value; frontend-first would show a value the server's `fromString` turns into NULL. State the deploy order in the release. |
| 12 | **The 353 NULL notes corpus-wide** (17.7% of the curator-owned public corpus). | Context only. NULL is the honest backlog marker. Not in scope. |

---

## 9. What can proceed now versus what should wait

**This is not deferrable.** 167 of 240 rows in the current proposal (`docs/curriculum/bscs-year1-preliminary-shaping.NOT-IMPLEMENTATION-READY.tsv`) carry `PENDING_PRODUCT_UX_DECISION`. The strategist correctly refused to invent a value or write an illegal `(unset)`, and marked the whole file un-buildable in its filename.

**Proceed now, independent of the decision:**
- **The 73 `General Education` rows.** Author and generate them.
- **Rows that are genuinely single-program.** CS-only rows go on the Computer Science fallback with NULL Domain Context. The strategist must apply the item 4 boundary test to split the 167 into shared rows and CS-only rows. That split is useful whichever way the owner decides.
- **Correct `docs/curriculum/review-set-workbook-spec.md:118-119`.** It is a stale claim regardless of the decision.
- **Still outstanding:** the count of 2+-program rows in the TSV (not attempted this pass).
- **Already done in this pass:** the CMO and CC2020 facts (confirmed); the catalog check for Computing/IS/SE (confirmed — IS and SE both live, no `Computing` collision); the 24 `Engineering Sciences` IT note titles (confirmed — not computing content, see §6). See the verification-pass note at the top of this document.

**Wait for the owner decision:**
- Every row that is genuinely multi-program shared foundation, including every **"Introduction to Computing"**-style row tagged `Computer Science, Information Technology, Information Systems`.
- **These cannot "proceed as single-program and revisit later" under the rule as it stands.** They are multi-program by definition, so without an honest Domain Context they cannot be generated.

**Explicitly rejected interim for those rows:** tag CS-only now, generate under the CS fallback, and add IT/IS plus `Computing` later.
- It looks like a way to keep moving, but the generated pack is **permanently framed by the CS fallback**. Setting Domain Context later never regenerates it (item 5).
- Fixing it later means a confirmed regeneration of every such note, which is exactly the re-work cost the consultation is trying to avoid.
- It also hides the evidence clause (a) needs.

**Timing.**
- Ratification needs no migration. `domain_context` is an unconstrained `VARCHAR(64)`, so adding a value is an enum/array/test edit plus doc edits.
- The wait is short if the owner decides promptly.
- The R4-style read on 3 to 5 shared notes should come **after ratification and before bulk generation of the shared rows**.

**If the owner declines `Computing`:**
- The shared rows fall back to the one honest option: **tag them to a single program, leave Domain Context NULL, and accept reduced discoverability** until a better vocabulary exists.
- Record this as a Backlog item, not a silent limitation.
- They must **not** be put into `Engineering Sciences` or `General Education`.

---

## 10. Implementation handoff description (conditional, and justified)

This section describes what would change and why. It is **not** Codex prompt text and **not** ADR text. Nothing here is authorized until the owner decides.

### 10.1 Governance and documentation (Claude-direct: doc/ADR writing is a core Claude Code responsibility per the task-routing table)

1. **ADR-001 amendment.**
   - A new dated section, recorded as clause (b)'s owner decision, adding `Computing` (`COMPUTING`, `quantitative = false`).
   - It must quote and **explicitly supersede** the 2026-09-04 `v0.111.0` rejection, answering both the naming-rule ground and the comprehension ground — now with the CC2020/CHED evidence confirmed rather than recalled.
   - It must state the clause (a) evidence and the "firmly planned" limit, the guard 2 computation against *existing* programs (pending the IS/SE catalog check), the item 4 boundary test with worked cases, the anti-catch-all rule, and the updated watch ratio (read from production at the time).
   - It must also correct the naming-rule paragraph's `Computing` citation in place, the way `v0.145.0` corrected stale lines.
2. **`docs/gpt-contexts/REVIEW_SET_SHAPING_CONTEXT.md`.** Add the 13th value and the boundary test to the strategist's list.
3. **`docs/curriculum/review-set-workbook-spec.md:118-119`.** Correct the stale save-time claim. This is independent of the decision and can go now as a small docs-only fix on the release branch.
4. **`ROADMAP.md` Backlog Index.**
   - A `[CHECKPOINT]` row for the program-mirror re-check (risk 1).
   - One row for the discovery-tagging review covering all three over-tagged populations (Computer Science's 48 notes, Computer Engineering's non-`Engineering Mathematics` subset, and the 24 IT/`Engineering Sciences` notes confirmed in §6/§7) — not a separate "Group C" row, since all three are the same Applicable-Programs-axis problem.

### 10.2 Code (small, additive, no migration)

Files:
- `backend/.../entity/DomainContext.java`: append `COMPUTING("Computing", false)` with a decision comment, following the existing appended-block convention.
- `backend/.../DomainContextTest`: update its `containsExactly` label assertion.
- `frontend/lib/domain-context.ts`: add the matching entry.
- `frontend/lib/domain-context.test.ts` — **confirmed to exist** and pin `DOMAIN_CONTEXT_OPTIONS`; include it in the PR's test-update list.
- `docs/curriculum/build_review_set_workbook.py`'s `DC_FILL` colour map: cosmetic, optional.

**Deploy order.** Backend before or together with frontend (risk 11). Additive only: no existing value removed or redefined.

**Test coverage owed.**
- The enum assertion.
- `fromString("COMPUTING")` round-trip.
- One test that a 2-program note with `COMPUTING` passes `assertGenerationReady` and resolves the label into the prompt context.
- Per this repo's standing rule, **a real `MockMvc` request test** proving a note update carrying `domainContext: "COMPUTING"` persists through the actual endpoint, not a direct method call.

**Routing: Claude-direct, on its own branch and PR.**
- The table's multi-system row (frontend and backend together) points to Codex.
- The tiebreaker governs here: *"dropping a known component in a known slot with no logic changes."* It is one enum constant, one array entry, and assertion updates, with no new logic, no endpoint, and no migration, well under ~50 LOC.
- The anti-drift judgment is concentrated in the ADR text, which Claude writes anyway.
- This recommendation stands on the reasoning above and does not depend on precisely how `v0.145.0`'s `BASIC_MEDICAL_SCIENCES` was routed — that precedent check was attempted this pass and blocked by the tool outage; it would corroborate, not overturn, this routing call if confirmed later.
- It still goes through branch, PR, CI and `/audit-diff`, like any code change.

### 10.3 Explicitly not included

- No change to the multi-program validation rule.
- No computing-specific prompt guidance (pending the R4-style read).
- No bulk metadata endpoint.
- No backfill or migration of existing notes.
- No Authored Depth changes.

**Optional, separate item (Codex, because it spans the save path and the UI):** a *non-blocking* hint when a curator saves a multi-program note with NULL Domain Context, plus a pipeline warning for multi-program `(unset)` or unratified values. It is useful, but it is a UX improvement, not part of this decision, and it must not become a save-time block (ADR-001: "valid to save").

### 10.4 Post-ratification curation (curator work, not engineering)

- The Group C review (24 notes) as described in item 6.
- Per-note `Computing` assignment when Group A notes are reused.
- The R4-style read on 3 to 5 shared notes before bulk generation.
- **Production writes:** relabelling is done by the curator through the product UI. Any direct database correction is the owner's to run, per `CLAUDE.md`'s production read-only rule.
