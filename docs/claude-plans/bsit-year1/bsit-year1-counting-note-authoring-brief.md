Status: Phase B final shaping, 2026-11-03; updated 2026-11-05: the Note was CREATED by the owner on 2026-11-05 (id 7564b66c-9dd2-424f-b569-274ec6dd6d20) from the draft in authored-bodies/. This file remains the specification. NOT implementation-ready.

# Authoring brief: Counting: Sum and Product Rules, Permutations and Combinations

Approved by the owner as Q8 (2026-11-03). It is a shaping entry plus this specification; do not generate or create the Note until a later authorized step.

## Metadata (as in the TSV row)

| Field | Value |
|---|---|
| Canonical title | Counting: Sum and Product Rules, Permutations and Combinations |
| Subject | `Discrete Mathematics` |
| Domain Context | `COMPUTING` |
| Authored Depth | `COLLEGE` (not a TSV column; set at authoring) |
| Applicable Programs | `Computer Science, Information Technology` (the TSV's alphabetical serialization) |
| Status | `New` |
| Subject Plan / Term | 🧮 Discrete Mathematics, Second Semester |
| Section | `Counting Principles`, placed after `Functions and Sequences` and before `Integers and Divisibility` and `Proof Techniques` |

**This is shared computing knowledge, not a BSIT-specific Note.** The title carries no BSIT, year or semester branding. Computer Science in Applicable Programs makes it discoverable to CS learners. **It does not add the Note to the existing BSCS Year 1 collection**, and no BSCS Subject Plan is changed.

## Why this section position
It follows Functions and Sequences (it uses sequences of choices, products and factorials) and precedes Integers and Divisibility and Proof Techniques, which it does not depend on. Integers and Divisibility stays directly before Proof Techniques because the proof Notes use divisibility and parity as examples. No prerequisite analysis found a concrete reason to place it elsewhere.

## Academic rationale and evidence limit
The Discrete Mathematics plan covers logic, sets, relations, functions, sequences, divisibility and introductory proofs, but not elementary counting. That is a reasonable gap to close in a standalone first-year Discrete Mathematics course. **The CHED MS101 learning outcomes were not legible in the available OCR source and no BSIT Discrete Mathematics syllabus was inspected.** This is an approved NoteLib curriculum choice, not a claim that CHED requires this Note.

## Content specification

**Overview.** Introduce systematic counting as a way to determine the number of possible outcomes without listing every possibility.

**Key ideas, in order**
1. Sum rule, for mutually exclusive alternatives.
2. Product rule, for successive choices.
3. Factorials.
4. Permutations, and why order matters.
5. Combinations: choosing without regard to order.
6. Counting with and without repetition.
7. Recognizing which of the sum rule, product rule, permutations or combinations a problem calls for.
8. Introductory computing examples: passwords, configurations, test cases.

**Scope boundary.** Elementary combinatorics only. It must not become a probability, algorithm-analysis or data-structures Note.

**Must not include**
- advanced combinatorial identities
- generating functions
- recurrence-relation solving
- full discrete probability distributions
- advanced graph theory
- tree data structures
- detailed algorithm complexity analysis

**Neighbours to respect.** Probability and Basic Statistics (Mathematics in the Modern World) owns introductory probability. Graphs and Networks (Mathematics in the Modern World) owns introductory graphs. Comparing Algorithm Efficiency Informally (Computer Programming 2) owns informal efficiency. Do not duplicate them.

**Authoring conventions.** Follow the existing Discrete Mathematics Notes: Overview, Key Idea, Core Details, Why It Matters, Quick Recall; program-neutral framing; computing examples illustrative, not required knowledge; knowledge-first, no BSIT branding.

## Not added (coverage considerations, not missing required Notes)
Trees and basic graph terminology (introductory graphs are in Mathematics in the Modern World; trees fit later Data Structures and Algorithms coverage), recurrence relations (insufficient Year 1 evidence), and discrete probability (Probability and Basic Statistics covers introductory probability). Revisit only on stronger curriculum evidence.
