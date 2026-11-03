Status: Phase B draft, 2026-11-03. Assessment for owner review. NOT implementation-ready. No Note was modified.

# BSIT Discrete Mathematics: coverage and reuse assessment

## 1. Evidence limits (read first)
- CHED lists **Discrete Mathematics (MS101, 3 units)** for BSIT (FACT, OCR). Its learning outcomes and prerequisites were not legible. **Nothing here claims the retained Notes satisfy the full CHED course.**
- Four of five surveyed schools place a Discrete Mathematics course in Year 1 (UC 1.2, EVSU 1.2, MSU 1.1, ADZU summer after Year 1). Their checklists give titles only, **no syllabus for any of them was read**. Coverage statements below are therefore INFERENCE from the typical content of a single-semester discrete mathematics course, not evidence about a named syllabus.
- What I read: the full body of all five Notes the owner flagged, a keyword screen over all 34 bodies, and a sentence-level read of every programming or algorithm mention in the other 29. I did **not** read the 29 bodies in full.

## 2. Disposition of the 34 Discrete Structures I Notes

| Disposition | Count | Notes |
|---|---|---|
| Reuse, **content-verified** by a full Strategist read | 2 | Boolean Algebra and Digital Logic; Mathematical Induction |
| Reuse, **conditional on curator verification** (screened, not fully read) | 29 | the other 29 (listed in the manifest) |
| **Excluded for BSIT** (stay intact in BSCS) | 3 | Strong Induction; Proving Program Correctness: Assertions and Loop Invariants; Logic in Programming and Software Specifications |

### The five owner-flagged Notes, individually
| Note | Finding | Disposition |
|---|---|---|
| **Boolean Algebra and Digital Logic** | Variables 0 and 1, truth tables, De Morgan, minterms, absorption, NAND/NOR completeness, with hardware and software connections. Introductory and accessible. It is the only place the journey meets digital logic, a topic IT students meet again (UC's BSIT has a separate Digital Logic course in Year 2). Treatment transfers unchanged | **Reuse** |
| **Mathematical Induction** | Base case, hypothesis, step, conclusion, a summation example, and the standard cautions (circular reasoning, base-case coverage). The computing examples (recursive algorithms, loop behaviour) are motivation only. Induction is a normal part of a one-semester discrete mathematics course. Treatment transfers unchanged | **Reuse** |
| **Strong Induction** | Strong hypothesis, well-ordering principle, least-counterexample argument, recursive-algorithm correctness. It adds proof rigor beyond what a first-year IT course needs, and the ordinary-induction Note already states strong induction in one bullet | **Excluded** (curator may reinstate) |
| **Proving Program Correctness** | Hoare triples, the assignment rule, loop invariants, variants, partial versus total correctness. This is formal verification, clearly beyond a first-year IT Discrete Mathematics course and it presupposes CP2-level fluency | **Excluded** (curator may reinstate) |
| **Logic in Programming and Software Specifications** | The first half repeats Notes already in the plan (implication, De Morgan, quantifier negation); the second half introduces contracts, Hoare triples and loop invariants, which duplicates Proving Program Correctness | **Excluded** (curator may reinstate). A lighter "logic in software conditions" Note could be authored if the owner wants that topic, which is not recommended without CHED evidence |

I did not exclude all five: two are retained. If the curator disagrees on any, the change is one row (status and section) in the TSV, not a restructure.

## 3. Proof-oriented weight
Proof Techniques holds 6 of 31 retained Notes (19%): Introduction to Mathematical Proof, Direct Proof, Proof by Contraposition, Proof by Contradiction, Proof by Cases and Counterexamples, Mathematical Induction. Set Identities and Membership Proofs and Common Logical Fallacies add a little more. **Excluding the three formal-verification Notes removes the heaviest proof treatment.** I judge the remainder appropriate. (INFERENCE; no BSIT syllabus was read.) Several retained proof Notes mention algorithm correctness and loop invariants as examples (see the keyword screen: Proof by Contradiction, Proof by Cases and Counterexamples, Divisibility, Greatest Common Divisors). These read as motivation. A curator should confirm none leans on invariants as a prerequisite.

## 4. Coverage and gaps (INFERENCE)

| Topic typical of a one-semester discrete mathematics course | Covered by | Gap? |
|---|---|---|
| Propositional logic, truth tables, equivalence, conditionals | Propositional Logic (5), plus a Logic Note in Mathematics in the Modern World | No |
| Predicates, quantifiers, nested quantifiers, translation | Predicate Logic (3) | No |
| Rules of inference, fallacies | Rules of Inference and Arguments (3) | No |
| Sets, operations, power sets, cardinality | Sets (4) | No |
| Relations, equivalence relations, partial orders | Relations (4) | No |
| Functions, injective/surjective/bijective, composition, sequences, summations | Functions and Sequences (4) | No |
| Divisibility, division algorithm, gcd, Euclid | Integers and Divisibility (2). Modular arithmetic is in Mathematics in the Modern World | No |
| Proof techniques and induction | Proof Techniques (6) | No |
| Boolean algebra | Propositional Logic (Boolean Algebra and Digital Logic) | No |
| **Counting: sum and product rules, permutations, combinations** | **Not in this plan.** BSCS places counting in Discrete Structures II (Year 2, not authored). Mathematics in the Modern World has probability and basic statistics but not counting techniques | **Yes, likely** |
| Graphs and trees | Graphs and Networks (Mathematics in the Modern World): paths, circuits, weighted graphs, coloring. No Note on trees | **Possible, minor** |
| Recurrence relations | None | Possible; uncertain for IT |
| Discrete probability | Probability and Basic Statistics (Mathematics in the Modern World) | No, if treated as covered there |

## 5. Recommendation (not applied)
- **Recommended targeted addition, one New Note:** "Counting: Sum and Product Rules, Permutations and Combinations" (COMPUTING, Discrete Mathematics, `COLLEGE`, Applicable Programs Information Technology, Computer Science). Reason: a stand-alone Discrete Mathematics course without counting is the likeliest gap, and BSIT has no second Discrete Structures course to absorb it. **Confidence is moderate because it rests on typical course content, not a BSIT syllabus or the CHED outcomes.**
- **Optional:** a Note on trees and basic graph terminology; recurrence relations only if the CHED MS101 outcomes confirm them.
- **Not added to the TSV.** You asked for a recommendation, not an insertion. If approved, it becomes New rows and the Reuse percentage by status drops slightly; that is the right trade.
- The decisive evidence is cheap to get: the official CHED text of MS101's outcomes, or one BSIT Discrete Mathematics syllabus.

## 6. Metadata consequences (no writes made)
- The 31 retained Notes would gain **Information Technology** only. Computer Science and Computer Engineering are untouched; Domain Context stays `COMPUTING`.
- Authorization was conditional on content-level verification. The two fully read Notes pass; the 29 screened Notes are listed as **Conditional**. **No tag should be added to the 29 until a curator confirms their bodies.** The metadata change for them is not drafted as SQL yet for that reason.
- The 3 Excluded Notes receive no tag.
