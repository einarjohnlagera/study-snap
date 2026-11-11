Status: DRAFT authored body for owner/curator review, 2026-11-05. NOTHING here is in production. Creating the Note, setting its metadata and generating its Study Pack are owner actions (CLAUDE.md: production writes are owner-only). NOT implementation-ready.

# Counting: Sum and Product Rules, Permutations and Combinations: authored body

Approved as BSIT Year 1 decision Q8. Specification: `../bsit-year1-counting-note-authoring-brief.md` (removed 2026-11-11; recoverable from commit `d5eaf383`). The body below follows the structure of the neighbouring Discrete Mathematics Notes (Overview, Key Idea, Core Details, Why It Matters, Quick Recall; `$...$` for mathematics).

## Metadata (from the shaping TSV, row "Counting Principles")

| Field | Value |
|---|---|
| Title | Counting: Sum and Product Rules, Permutations and Combinations |
| Subject | Discrete Mathematics |
| Domain Context | COMPUTING |
| Authored Depth | COLLEGE |
| Applicable Programs | Computer Science, Information Technology |
| Subject Plan / Term | 🧮 Discrete Mathematics, Second Semester |
| Section | Counting Principles (after Functions and Sequences, before Integers and Divisibility) |
| Status | New |

## Body (copy the contents of the fenced block below, without the ``` lines)

```text
Counting: Sum and Product Rules, Permutations and Combinations

📘 Overview
Counting determines how many outcomes a situation has without listing them one by one. The sum rule and product rule combine counts for alternatives and for successive choices, while permutations and combinations count ordered and unordered selections from a set of distinct objects.

🧠 Key Idea
Most elementary counting problems come down to two questions: are the choices alternatives (add) or successive steps (multiply), and does the order of the chosen objects matter (permutation) or not (combination)?

⚔️ Core Details:
- Sum rule: if a task can be done in one of $m$ ways or in one of $n$ ways, and no way appears in both lists, it can be done in $m+n$ ways. Example: a project topic chosen from 3 networking topics or 5 database topics gives $3+5=8$ choices. If the lists overlap, the shared ways must be subtracted once so they are not counted twice.
- Product rule: if a task is done in $k$ successive steps with $n_1, n_2, \dots, n_k$ ways, and the number of ways at each step does not depend on which way was chosen earlier, it can be done in $n_1 n_2 \cdots n_k$ ways. Example: 3 main dishes and 4 drinks give $3 \cdot 4 = 12$ meals.
- Factorial: $n! = n(n-1)\cdots 2 \cdot 1$, with $0! = 1$. It counts the ways to arrange $n$ distinct objects in a row.
- Permutations count ordered selections of $r$ distinct objects from $n$ distinct objects, with no object used twice: $P(n,r) = \frac{n!}{(n-r)!}$. Example: ordered 3-letter codes from the 5 distinct letters A to E number $P(5,3) = 60$.
- Combinations count unordered selections of $r$ objects from $n$ distinct objects: $C(n,r) = \frac{n!}{r!\,(n-r)!}$. Each unordered selection can be arranged in $r!$ ways, so $C(n,r) = \frac{P(n,r)}{r!}$. Example: choosing 3 of the 5 letters without regard to order gives $C(5,3) = \frac{60}{3!} = 10$.
- Repetition: when each of $r$ ordered positions may use any of $n$ symbols, with repetition allowed, there are $n^r$ sequences. Without repetition, the count of ordered selections is $P(n,r)$.
- Choosing the tool: mutually exclusive alternatives suggest the sum rule; successive steps suggest the product rule; if order matters and nothing repeats, use $P(n,r)$; if order does not matter and nothing repeats, use $C(n,r)$.

🎯 Why It Matters:
- A 4-digit PIN allows $10^4 = 10{,}000$ possibilities, and an 8-character password drawn from 62 symbols allows $62^8$ (about $2.18 \times 10^{14}$), which shows how length drives the cost of guessing.
- A system with 5 on/off settings, each chosen freely, has $2^5 = 32$ configurations, so the number of configurations doubles with each added setting.
- Choosing 3 of 10 modules to test together can be done in $C(10,3) = 120$ ways, but testing every ordered selection of 3 of the 10 modules would be $P(10,3) = 720$ cases, so deciding whether order matters changes the workload.
- Counting shows when exhaustive checking of every case is feasible and when it is not.

🧠 Quick Recall:
- Sum rule — $m+n$ ways for mutually exclusive alternatives; subtract any overlap.
- Product rule — $n_1 n_2 \cdots n_k$ ways for successive steps whose option counts do not depend on earlier choices.
- Factorial — $n!$ counts arrangements of $n$ distinct objects; $0! = 1$.
- Permutation — $P(n,r) = \frac{n!}{(n-r)!}$, order matters, no repetition.
- Combination — $C(n,r) = \frac{n!}{r!\,(n-r)!}$, order ignored, no repetition; $C(n,r) = C(n,n-r)$.
- Sequences with repetition — $n^r$ ordered selections of length $r$ from $n$ symbols.
```

## Verification (FACT, computed 2026-11-05)

Every number in the body was recomputed: $P(5,3)=60$, $C(5,3)=10$ and $60/3!=10$; $3+5=8$; $3\cdot4=12$; $10^4=10{,}000$; $2^5=32$; $C(10,3)=120$; $P(10,3)=720$; $62^8=218{,}340{,}105{,}584{,}896\approx2.18\times10^{14}$. The statement $C(n,r)=C(n,n-r)$ and $C(n,r)=P(n,r)/r!$ are standard.

## Scope checks against the brief
- Covers all eight key ideas: sum rule, product rule, factorials, permutations, combinations, with and without repetition (ordered only), choosing the tool, and computing examples (PINs, passwords, configurations, test cases).
- **Excluded as specified:** combinatorial identities (only $C(n,r)=C(n,n-r)$ is stated, as a symmetry fact), generating functions, recurrence solving, probability distributions, graph theory, trees, complexity analysis. Unordered selection with repetition ("stars and bars") is deliberately left out of the body and is NOT mentioned in it, so a generated quiz has no cue to ask about it.
- **Neighbours:** the power-set count $2^n$ appears in a Sets Note; the 5-settings example here reuses it only as an application, not a definition. Probability and Basic Statistics (Mathematics in the Modern World) owns probability; this Note never computes a probability.
- Length: about 3,600 characters, well under the 12,000-character Study Pack cap but roughly 45% longer than the neighbouring Discrete Mathematics Notes (about 2,000 to 2,500 characters), because the brief asks for eight ideas. The curator may trim it.
- **The product-rule condition** is stated as "the number of ways at each step does not depend on which way was chosen earlier". That is the form that makes $P(n,r)$ follow from the product rule ($n$, then $n-1$, then $n-2$): the options differ but their number does not. The curator may prefer a sentence that spells this out.

## Defect-class self-check (FACT, 2026-11-05)
- Sum rule and "mutually exclusive" are used consistently in Core Details, Choosing the tool and Quick Recall. ("Independent" was removed; it is a probability term.)
- The body contains no scope disclaimer naming a topic it excludes.
- Every number in a quiz-able sentence is verified above.

## After the owner creates the Note (not done here)
1. Create the Note with the metadata above and paste the body. **Check the new-note form exposes Domain Context and Authored Depth** (the Path C feasibility notes never confirmed it); if either cannot be set at creation, tell me, because the Note would not match the shaping TSV and a metadata follow-up would be needed. Do not use "Regenerate note", which replaces the body.
2. Generate its Study Pack (needs your explicit confirmation as usual). Curator review of the five quiz items against the body, including the Quick Recall.
3. Put the new Note into the proposed BSIT plan: 🧮 Discrete Mathematics, section Counting Principles. Send me the Note id so the shaping TSV, manifest and workbook record the exact id (the row is currently `New`, with no id).
4. Add Information Technology and Computer Science at creation (both are in the metadata above). Nothing to tag afterwards.
5. Do **not** add it to the BSCS Year 1 collection.
