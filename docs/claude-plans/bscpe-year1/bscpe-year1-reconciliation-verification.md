Status: DRAFT academic reconciliation and targeted verification, 2026-11-12. **Read-only: no Note body, metadata, collection, Study Pack or production data was written or changed; the XLSX was generated afterwards from the signed-off shape (section 5).** Written by the Claude Note Strategist in answer to the GPT Note Strategist's reconciliation brief. Companion files: `bscpe-year1-reconciled-target-shape.tsv` (188 rows, the requested columns), `bscpe-year1-canonical-audit.md` and `.tsv`, `bscpe-year1-audit-read.sql`.

# BSCpE Year 1: reconciled target shape and targeted verification

## 0. A correction to the earlier audit (read this first)

The earlier audit said **13** Computer Programming 1 Notes were language-neutral and **29** were C++-specific. **That was wrong.** The server-side pattern check had two defects: it matched `cin` and `cout` as bare substrings (so words such as "machine" tripped it) and it required a word boundary after `C++` (so real mentions were missed). Re-run with a correct pattern on 2026-11-12: **22 Notes mention C++ in the body, exactly the 22 whose titles say C++; the other 20 are language-neutral, and all 20 were read in full and found accurate.** The 13 you proposed were therefore a subset of the 20. The target shape uses 19 of the 20 (one is excluded as optional style detail). `bscpe-year1-canonical-audit.md/.tsv` and `bscpe-year1-audit-read.sql` were corrected.

## 1. Result in one table

| Plan | Term | Reuse verified | Reuse pending | New | Excluded |
|---|---|---|---|---|---|
| 1. Computer Engineering as a Discipline | S1 | 6 | 0 | 4 | 0 |
| 2. Programming Logic and Design | S1 | 19 | 0 | 1 | 1 |
| 3. Calculus 1 | S1 | 7 | 2 | 3 | 4 |
| 4. Chemistry for Engineers | S1 | 0 | 0 | 12 | 0 |
| 5. Understanding the Self | S1 | 8 | 0 | 0 | 1 |
| 6. Calculus 2 | S2 | 7 | 0 | 2 | 4 |
| 7. Physics for Engineers | S2 | 7 | 0 | 12 | 5 |
| 8. Discrete Mathematics | S2 | 32 | 0 | 0 | 3 |
| 9. Fundamentals of Electrical Circuits | S2 | 0 | 0 | 12 | 1 |
| 10. Data Structures and Algorithms | S2 | 5 | 1 | 4 | 0 |
| 11. Mathematics in the Modern World | S2 | 21 | 0 | 0 | 4 |
| **Total** | | **112** | **3** | **50** | **23** |

`REUSE_VERIFIED` means either the body was read in full on 2026-11-12 (the groups in section 2) or, for the shared General Education and Discrete Mathematics plans, the metadata was verified live and the Note is reused unchanged by BSCS, BSIT and BSIS (bodies not re-read, as the brief asked to preserve them). The `verification_status` column says which for each row.

## 2. Targeted verification: Notes whose full bodies were read (exact ids)

Verdicts: **V** reuse verified, **P** reuse pending (a defect or condition), **X** excluded, with a brief reason.

### Computer Engineering as a Discipline

| id | Note | Verdict | Reason |
|---|---|---|---|
| `9b81844a-209c-426a-89e9-0f110620b9f8` | The Computing Disciplines: Computer Science, Information Technology, Information Systems, Software Engineering and Computer Engineering | V | body read in full 2026-11-12; accurate and college-appropriate; frames CE against four sibling disciplines (body title differs slightly from the Note title) |
| `9d58c62c-d29d-468d-8904-267ff567c2a6` | Components of a Computer System | V | body read in full 2026-11-12; accurate and college-appropriate |
| `ad0a702e-5e35-47eb-9438-5f63ae3e6d3d` | The Central Processing Unit and the Fetch-Decode-Execute Cycle | V | body read in full 2026-11-12; accurate and college-appropriate |
| `1dc5f924-0220-4e72-a21d-f6e50715c510` | Number Systems: Decimal, Binary, Octal and Hexadecimal | V | body read in full 2026-11-12; accurate and college-appropriate |
| `745a68ee-75ed-43ea-bdcb-b4371fedda5a` | Converting Between Number Systems | V | body read in full 2026-11-12; accurate and college-appropriate |
| `57b07db4-b223-41b3-9547-836207e3efec` | Binary Arithmetic and Signed Numbers | V | body read in full 2026-11-12; accurate and college-appropriate |

### Programming Logic and Design

| id | Note | Verdict | Reason |
|---|---|---|---|
| `9b5165d7-a46a-4344-bedd-8ca50b3ea683` | What Programming Is and How Programs Run | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `1d31b1da-43fa-419f-93d0-cb0b995e6419` | Problem-Solving Steps in Programming | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `583c82dc-fc5b-4088-ae21-dd474a787be3` | Algorithms and Their Properties | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `f1512c68-120d-431a-afcb-19f5993cd41c` | Pseudocode and Flowcharts | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `f4027d43-ff5f-4049-86d3-85cae899e50c` | Variables, Assignment and Program State | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `07d38c32-9cde-41a0-93a6-bfead8d9a030` | Tracing and Dry Runs | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `7feec18f-6f30-421f-95e4-a477ccdad45e` | Boolean Conditions and Short-Circuit Evaluation | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `b7a7b8d2-dc05-4d9c-b893-17a6104a0856` | Loop Control and Common Loop Patterns | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `d8d3ea8d-db2c-4ff4-9b40-28866eb58b31` | Nested Loops | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `3e24314a-6724-4a37-ab67-d5e63e8a54b8` | Functions and Procedural Abstraction | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `9b17464f-b9a8-4014-994f-e68e8c99c503` | Modular Program Design | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `7d1e8151-c569-4189-bda5-5d88c8bb0354` | Values, References and Aliasing | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `20cc025e-0626-489a-b970-4d6c089a08f7` | Recursion: Base Case and Recursive Case | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `d59b2cd2-be16-4e66-8cf3-d6d116431a1e` | Tracing Recursive Calls | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `b0722e88-ba9e-47cb-b442-d1e9a6feb03b` | Syntax, Runtime and Logic Errors | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `8fa3df9c-b787-4a25-86c7-77bd422e03d4` | Debugging Techniques | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `ed93ebce-a44e-423d-9ecd-4a03aeb294f7` | Designing Test Cases | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `09c1473b-8e64-4c25-8692-79611284dfd7` | Input Validation: Checking Before Trusting Data | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `196b73ab-2f63-40a7-a004-3916c864ba94` | Reviewing Another Programmer's Code | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral (no C++ marker, verified 2026-11-12) |
| `96406436-b3e0-4d5a-84bb-760e5b92627b` | Documentation, Comments and Coding Style | X | body read in full; language-neutral but style and tooling detail beyond a 2-unit logic and design course, and its docstring example is Python-specific; optional, not selected |

### Calculus 1

| id | Note | Verdict | Reason |
|---|---|---|---|
| `a2a227b7-6a82-40b2-93e8-36a8397398ee` | Functions and Limits in Differential Calculus | V | body read in full 2026-11-12; accurate and college-appropriate; authored depth unset (preserved) |
| `6e5a8d7f-c113-4153-b8df-5a342a59949e` | Limits and Continuity in Differential Calculus | P | body read; content correct and complementary to the limits Note, BUT the math is broken: backslashes are doubled (for example \\lim) so formulas will render wrongly; needs an owner-run content fix or replacement before reuse; authored depth BOARD_EXAM_REVIEW (preserved) |
| `7b0df43e-e7bb-4737-a99d-1a08c0da5465` | Derivatives and Differentiation Rules | V | body read in full 2026-11-12; accurate and college-appropriate; rules only, no worked examples; authored depth unset (preserved) |
| `8cab64e3-1c12-42ee-9899-115354d13253` | Implicit Differentiation in Differential Calculus | P | body read; content correct with a worked example, BUT the Key Idea has a lost backslash (renders as 'rac{dy}{dx}'); needs an owner-run content fix before reuse; authored depth unset (preserved) |
| `2cf5cdd3-2174-4f92-a142-ea048d2803d0` | Higher-Order Derivatives in Differential Calculus | V | body read in full 2026-11-12; accurate and college-appropriate; authored depth unset (preserved) |
| `9714a5bd-8a38-4ea0-897a-e21ad4276df1` | Related Rates in Differential Calculus | V | body read in full 2026-11-12; accurate and college-appropriate; depends on implicit differentiation |
| `b57a7ec2-b328-4c7a-9a17-cdaba88cffa1` | Maxima and Minima in Differential Calculus | V | body read in full 2026-11-12; accurate and college-appropriate; partial overlap with Curve Sketching (critical points, second derivative test) |
| `24e77b20-439e-4b95-bf91-a7f70ba51f4f` | Curve Sketching Using Differential Calculus | V | body read in full 2026-11-12; accurate and college-appropriate; body title is longer than the Note title |
| `5768ba6b-2418-4338-81fa-31e33020565f` | Engineering Applications of Derivatives in Differential Calculus | V | body read in full 2026-11-12; accurate and college-appropriate; summarizes optimization and related rates already covered by dedicated Notes (mild overlap), adds velocity, acceleration and circuit rates |
| `bde223c9-00d2-407e-821a-ec034f88ba14` | Fundamentals of Derivatives in Differential Calculus | X | duplicates Derivatives and Differentiation Rules (same definition and rules); board-review depth |
| `7bc1214d-2ee3-4d67-81b1-0f29680d8947` | Applications of Derivatives in Engineering Mathematics | X | duplicates the dedicated maxima, curve-sketching and related-rates Notes; board-review depth |

### Calculus 2

| id | Note | Verdict | Reason |
|---|---|---|---|
| `dc08be8d-e6c7-4ab7-b222-7152e45cdc7b` | Indefinite Integrals in Integral Calculus | V | body read in full 2026-11-12; accurate and college-appropriate; authored depth BOARD_EXAM_REVIEW, included unchanged (not relabelled) |
| `d8042c2f-022d-4a9f-806e-75e21c27079e` | Definite Integrals in Integral Calculus | V | body read in full 2026-11-12; accurate and college-appropriate; authored depth BOARD_EXAM_REVIEW, included unchanged; overlaps the Fundamental Theorem Note on evaluation |
| `9e14aecd-b97c-4215-b56f-43baebaed7c4` | Fundamental Theorem of Calculus in Engineering Mathematics | V | body read in full 2026-11-12; accurate and college-appropriate; authored depth unset (preserved) |
| `dac28cb9-203d-464e-bffd-2337b8cdddc8` | Techniques of Integration in Engineering Mathematics | V | body read in full 2026-11-12; accurate and college-appropriate; covers substitution, parts, partial fractions, trigonometric integrals and substitution, reduction formulas |
| `65b723f6-1559-4acf-ae5b-18342b597617` | Calculating Areas Under and Between Curves Using Integral Calculus | V | body read in full 2026-11-12; accurate and college-appropriate |
| `124f7cd6-d392-48c1-9b51-7a333dd50229` | Calculating Volumes of Solids Using Integral Calculus | V | body read in full 2026-11-12; accurate and college-appropriate |
| `0f37e7ca-9baf-4a6f-a318-14f9c59bb906` | Engineering Applications of Integration in Calculus | V | body read in full 2026-11-12; accurate and college-appropriate; work, fluid force and charge applications |
| `1074d3cb-788d-4033-b06b-586e35769e23` | Applications of Integration in Engineering Mathematics | X | duplicates Engineering Applications of Integration; its body title ('...in Engineering Sciences') differs from the Note title; board-review depth |

### Physics for Engineers

| id | Note | Verdict | Reason |
|---|---|---|---|
| `0fdee8ee-b2ef-4194-b440-0955db571b1e` | Dot Product in Engineering Mechanics | V | body read in full 2026-11-12; accurate and college-appropriate; neutral framing; authored depth unset (preserved) |
| `435afd7e-a98a-49b5-8a7f-3e9303cc7feb` | Cross Product in Engineering Mechanics | V | body read in full 2026-11-12; accurate and college-appropriate; neutral framing; authored depth unset (preserved) |
| `99648408-c2ad-404e-bd7d-0740030aba3d` | Fundamentals of Free-Body Diagrams in Engineering Mechanics | V | body read in full 2026-11-12; accurate and college-appropriate; generic framing; authored depth unset (preserved) |
| `50ee257b-56e9-4970-b86b-41194983a2e4` | Static and Kinetic Friction in Engineering Mechanics | V | body read in full 2026-11-12; accurate and college-appropriate; authored depth unset (preserved) |
| `e6b8bb57-e1fb-486a-ae80-bf931a525220` | Fundamental Concepts of Work and Energy in Engineering Mechanics | V | body read in full 2026-11-12; accurate and college-appropriate; authored depth unset (preserved) |
| `ce9ad9be-a936-4d02-a678-02cf3d80b488` | Impulse and Momentum in Engineering Mechanics | V | body read in full 2026-11-12; accurate and college-appropriate; authored depth unset (preserved) |
| `fc0f8a2d-29c7-4611-9a23-b5caf3909f10` | Mass Moments of Inertia in Engineering Mechanics | V | body read in full 2026-11-12; accurate and college-appropriate; authored depth unset (preserved) |
| `45e55bac-0d24-4d07-8825-31d003797bdb` | Fundamental Concepts of Scalars and Vectors in Engineering Mechanics | X | body read: framed for civil engineering structures (not neutral for Computer Engineering); replaced by a NEW vectors Note |
| `a1fb1d72-fd3e-452d-af71-408deabd1a22` | Equilibrium of Particles in Engineering Mechanics | X | body read: civil-structures framing; covered by the NEW Newton's laws Note |
| `4239bdee-d2a0-4837-a910-30612f1cc2b7` | Vector Addition and Subtraction in Engineering Mechanics | X | body read: a Core Details bullet is cut off mid-sentence (content defect); replaced by a NEW vectors Note |
| `9f8f6767-b815-41cd-aa61-3881007652d9` | Fundamentals of Rigid Body Dynamics in Engineering Mechanics | X | body read: Euler's equations with the inertia tensor, beyond first-year physics; replaced by a NEW rotational-motion Note |

### Fundamentals of Electrical Circuits

| id | Note | Verdict | Reason |
|---|---|---|---|
| `acd1073a-0ba3-4cef-b039-ddb10efa5757` | Ohm’s Law and Basic Electrical Concepts | X | body read: correct but a 605-character formula sheet outside the standard Note structure, board-review depth, no Domain Context; superseded by the NEW Resistance and Ohm's Law Note (the Strategist may override) |

### Data Structures and Algorithms

| id | Note | Verdict | Reason |
|---|---|---|---|
| `c7ae7a94-9f9f-4542-ab11-264691a3d97a` | Comparing Algorithm Efficiency Informally | V | body read in full 2026-11-12; accurate and college-appropriate; mild overlap with Algorithms and Their Properties (Big-O in the logic plan) |
| `2eefb012-b391-45b6-82b5-b895c621e455` | Linear and Binary Search | V | body read in full 2026-11-12; accurate and college-appropriate |
| `d6f2e0b4-c434-4b77-bc40-b76e69d60faa` | Elementary Sorting: Bubble, Selection and Insertion | V | body read in full 2026-11-12; accurate and college-appropriate |
| `02e8d4af-54b4-4b0d-8efc-955af0ea95ab` | Linked Lists: Nodes and Links | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral |
| `5240ceb2-02b1-49e2-8dae-f30bdef27863` | Static versus Dynamic Data Structure Implementations | V | body read in full 2026-11-12; accurate and college-appropriate; language-neutral |
| `e1828d0c-4bf7-4ed0-8f3e-8329d36fbc66` | Linked List Operations in C++ | P | body read: C++ pointer code; keep only if the Year uses C++, otherwise exclude |

### Not read in full (stated plainly)
- **General Education plans (Understanding the Self, Mathematics in the Modern World) and Discrete Mathematics:** metadata only; preserved per the brief.
- **Excluded without reading:** `02b797e0` Centroids and `80e2277d` Moments of Inertia (integral calculus applications covered by the physics plan), `11b0d8ef`, `cf662729`, `5e0fb264` (scope or depth by title and metadata).

## 3. Material unresolved issues (only those that prevent approval)

1. **The CHED CMOs remain unread.** Both PDFs returned HTTP 403 and the PCU page returned only a verification page. The Year 1 course list, semester split and any prerequisite is **not verified against the CMO**; only the University of the East page was read (unit totals, no prerequisites). Please state whether sign-off can rest on that, or whether the owner supplies the PDFs.
2. **Two shared Notes have rendering defects** and are `REUSE_PENDING`: `6e5a8d7f` *Limits and Continuity in Differential Calculus* (backslashes doubled, so formulas will not render) and `8cab64e3` *Implicit Differentiation in Differential Calculus* (a lost backslash leaves `rac{dy}{dx}` in the Key Idea). Both are held by the public CELE Comprehensive Review, so a content correction is an owner-run SQL that also changes that set. Alternatives: replace them with NEW Notes, or accept them as they are. One further defect, a Core Details bullet cut off mid-sentence, is in the excluded `4239bdee`.
3. **Calculus 2 includes two BOARD_EXAM_REVIEW Notes unchanged** (`dc08be8d` Indefinite Integrals, `d8042c2f` Definite Integrals) because no unset-depth Note covers those basics. Depth is mixed within one plan, as the brief's "do not relabel" requires; please confirm that is acceptable.
4. **23 of the 50 NEW Notes are flagged NEEDS_DECISION** (circuits 10, chemistry 7, physics 5, calculus 1): their value depends on worked methods and calculations that a title-only generator will not carry. Reading the existing calculus Notes shows what the generator produces: correct rules in about 2,000 characters with no worked examples. **The generation-depth pilot the playbook recommends should test one Note of each type (a circuit-analysis Note, a stoichiometry Note, a kinematics Note) before the owner authorizes these batches.** Without it the sign-off cannot say these Notes meet an approved specification.
5. **Scope-dependent Notes:** *Infinite Sequences and Series* (depends on the CMO course description, may belong to a later course) and *Sinusoidal Signals and Phasors* (placement varies).
6. **Overlaps accepted but worth a glance:** `583c82dc` Algorithms and Their Properties (Big-O and Theta) versus `c7ae7a94` Comparing Algorithm Efficiency Informally; `2ed3f9cd` MMW Logic versus the Discrete Mathematics logic Notes (preserved per the brief); `5768ba6b` derivative applications overview versus the dedicated related-rates and maxima Notes; `d8042c2f` Definite Integrals versus `9e14aecd` the Fundamental Theorem.
7. **Metadata that is not an issue but must be recorded:** NEW Notes carry `ENGINEERING_SCIENCES`, `ENGINEERING_MATHEMATICS` or `COMPUTING` only, with `Computer Engineering` as the single proposed program; no new Domain Context. **47 of the 115 reuse rows do not carry the Computer Engineering tag** (Programming Logic 19, Computer Engineering orientation 6, Understanding the Self 6, Mathematics in the Modern World 7, Physics 7, Discrete Mathematics 2); tagging stays a separate owner-authorized decision. Every selected reuse Note has an explicit Domain Context, so adding a second program does not trigger the ADR-001 precondition.
8. **The RA 8981 Note (`d50a303f`) is not in the target shape.** It was not reused; verification of the statute remains open if it is ever considered.

## 4. What was not done

No Note body generated, no Study Pack, no write, no metadata change, no collection, no publication, no commit, no XLSX. Production reads for this pass: the queries in `bscpe-year1-audit-read.sql` plus body reads of 59 Notes, all `SELECT` through the Render MCP.

## 5. Sign-off outcome (GPT Note Strategist, 2026-11-12) and what changed

**APPROVED WITH THREE ROW EDITS**, applied: `Infinite Sequences and Series: Convergence Tests` and `Sinusoidal Signals and Phasors` moved from NEW to EXCLUDED (deferred), and `Linked List Operations in C++` (`e1828d0c`) moved from REUSE_PENDING to EXCLUDED. **Final totals: 112 REUSE_VERIFIED, 2 REUSE_PENDING, 48 NEW, 26 EXCLUDED, 188 rows, 11 Subject Plans.** The generation-depth concerns recalculated from the final TSV: **21** (Electrical Circuits 9, Chemistry 7, Physics 5), not 23, because the deferred series and phasor Notes are no longer generation candidates. The sign-off supplied no replacement rows, so the three status changes and their reason text were applied here. `bscpe-year1-reconciled-target-shape-compact.tsv` and the sign-off prompt predate these edits and are consumed.
