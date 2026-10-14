# BSCS Year 1 — Collection Creation Checklist (2026-10-01)

> **For the owner to execute in the production UI. Claude does not create collections** (`CLAUDE.md`: production writes are owner-only). Nothing here has been done. Claude can run the read-only verification in section 8 after each step. Source of truth: `bscs-year1-shaping.tsv` and `bscs-precalculus-preparatory-shaping.tsv`.

## 0. Preconditions (do not start before these)

- [ ] The R4 pilot is closed: the five pilot Notes' Applicable Programs are fixed, the content path is decided, and the read-back passes (`claude-findings/r4-pilot/r4-pilot-fix-plan.md`).
- [ ] The Year description wording is confirmed (D-29, text in section 1).
- [ ] You know which batching option you are using (`bscs-year1-authoring-batch-plan.md` section 4). **If Option 2, do sections 1 to 3 BEFORE generating notes**, because each batch needs the Subject Plan's id.
- [ ] The old private "BS Computer Science" collection is left untouched (preserved, not deleted or migrated; D-01).

## 1. Create the Year root (private)

| Field | Value |
|---|---|
| Title | `🖥️ BS Computer Science — 1st Year` |
| Visibility | **Private** until section 7 |
| Course / program | `Computer Science` (**owner to confirm**: this puts the Year in the BSCS recommendation slot; the Precalculus root is deliberately left unset so it cannot displace it, D-26) |
| Term | none (a root cannot carry a term; setting one is refused) |
| Description | A knowledge-focused reference journey for the first year of a Philippine BS Computer Science degree. It covers selected academic courses whose knowledge NoteLib can teach through Notes: computing, mathematics and General Education. It is not a replacement for any institution's complete degree prospectus. Courses such as Physical Education and NSTP are part of many BS Computer Science programs, but they are activity- or service-based and fall outside this Note-based journey. Course names, terms and sequence follow a NoteLib reference arrangement; your university's may differ. |

- [ ] Root created, private, description and course/program saved. (Saving metadata is separate from publishing.)

## 2. Create the 10 Subject Plans as children of the Year (private)

Create them in this order; each child receives the next sibling position automatically. Term label and order are set per plan (label up to 60 characters; order 1 to 32767). **Set a term on all ten or on none**: a partly-termed Year cannot be published, and **terms freeze at publication and cannot be added or renamed afterwards**.

| # | Subject Plan | Term label | Term order | Notes expected (Reuse / New / Excluded) |
|---|---|---|---|---|
| 1 | 💻 Introduction to Computing | First Semester | 1 | 2 / 37 / 0 (total placed 39) |
| 2 | ⌨️ Computer Programming 1 | First Semester | 1 | 2 / 40 / 0 (total placed 42) |
| 3 | 📐 Mathematics in the Modern World | First Semester | 1 | 5 / 16 / 4 (total placed 21) |
| 4 | 🌏 The Contemporary World | First Semester | 1 | 8 / 1 / 0 (total placed 9) |
| 5 | 🎨 Art Appreciation | First Semester | 1 | 7 / 0 / 0 (total placed 7) |
| 6 | 🧠 Computer Programming 2 | Second Semester | 2 | 1 / 30 / 0 (total placed 31) |
| 7 | 🧮 Discrete Structures I | Second Semester | 2 | 1 / 33 / 0 (total placed 34) |
| 8 | 🪞 Understanding the Self | Second Semester | 2 | 6 / 2 / 1 (total placed 8) |
| 9 | 📜 Readings in Philippine History | Second Semester | 2 | 10 / 0 / 0 (total placed 10) |
| 10 | 💬 Purposive Communication | Second Semester | 2 | 8 / 4 / 0 (total placed 12) |

- [ ] All 10 Subject Plans created under the Year, in order, private.
- [ ] Terms set on all 10 (First Semester = 1, Second Semester = 2).

## 3. Subject Plan descriptions (paste verbatim) and section order

### 1. 💻 Introduction to Computing — First Semester

**Description:** An orientation to the computing disciplines and profession, the history and components of computer systems, how data is represented, how the Internet and the Web work, and the security, ethical and social issues of computing.

| # | Section (label) | Reuse | New | Excluded |
|---|---|---|---|---|
| 1 | The Computing Disciplines and Profession | 1 | 4 | 0 |
| 2 | History and Evolution of Computing | 0 | 4 | 0 |
| 3 | Information Representation | 0 | 6 | 0 |
| 4 | Computer Systems: Organization and Architecture | 0 | 7 | 0 |
| 5 | Networks, the Internet and the Web | 0 | 7 | 0 |
| 6 | Computer Security and Malware | 0 | 5 | 0 |
| 7 | Ethics, Society and Emerging Trends | 1 | 4 | 0 |

### 2. ⌨️ Computer Programming 1 — First Semester

**Description:** Fundamentals of programming: designing, implementing, testing and debugging programs with primitive data types, computation, input and output, selection, iteration, functions and recursion, and reviewing code for style and clarity. Concepts are taught language-neutrally where the knowledge is portable; C++ is the reference language for executable examples.

| # | Section (label) | Reuse | New | Excluded |
|---|---|---|---|---|
| 1 | Problem Solving and Algorithms | 2 | 3 | 0 |
| 2 | Program Structure, Variables and Data Types | 0 | 6 | 0 |
| 3 | Expressions and Operators | 0 | 4 | 0 |
| 4 | Input and Output | 0 | 2 | 0 |
| 5 | Selection | 0 | 3 | 0 |
| 6 | Iteration | 0 | 4 | 0 |
| 7 | Input Validation | 0 | 2 | 0 |
| 8 | Functions and Procedural Abstraction | 0 | 6 | 0 |
| 9 | Recursion | 0 | 2 | 0 |
| 10 | Testing, Debugging and Code Quality | 0 | 8 | 0 |

### 3. 📐 Mathematics in the Modern World — First Semester

**Description:** The nature and language of mathematics and its use in data, geometric design, codes, optimisation, finance, social choice, graphs and mathematical systems; placement varies by university.

| # | Section (label) | Reuse | New | Excluded |
|---|---|---|---|---|
| 1 | The Nature of Mathematics | 2 | 3 | 0 |
| 2 | Data Management | 2 | 3 | 0 |
| 3 | Logic | 0 | 1 | 0 |
| 4 | Geometric Designs | 0 | 1 | 0 |
| 5 | Codes and Data Transmission | 0 | 3 | 0 |
| 6 | Optimization | 0 | 1 | 0 |
| 7 | Mathematics of Finance | 1 | 0 | 0 |
| 8 | Social Choice and Fair Division | 0 | 2 | 0 |
| 9 | Graphs and Mathematical Systems | 0 | 2 | 0 |
| 10 | Held out of this journey (Excluded) | 0 | 0 | 4 |

### 4. 🌏 The Contemporary World — First Semester

**Description:** Globalization and its economic, political, social and environmental dimensions, including global governance, inequality, migration, media and technology, climate change, and the place of the Philippines in the world; placement varies by university.

| # | Section (label) | Reuse | New | Excluded |
|---|---|---|---|---|
| 1 | Globalization | 3 | 0 | 0 |
| 2 | Global Challenges | 3 | 0 | 0 |
| 3 | Technology and Society | 1 | 0 | 0 |
| 4 | The Philippines and the World | 1 | 1 | 0 |

### 5. 🎨 Art Appreciation — First Semester

**Description:** The elements, forms and meanings of art, approaches to interpretation and criticism, major art movements, and Philippine art and cultural heritage; placement varies by university.

| # | Section (label) | Reuse | New | Excluded |
|---|---|---|---|---|
| 1 | Foundations of Art | 3 | 0 | 0 |
| 2 | Forms and Media | 1 | 0 | 0 |
| 3 | Interpretation and Criticism | 1 | 0 | 0 |
| 4 | History and Heritage | 2 | 0 | 0 |

### 6. 🧠 Computer Programming 2 — Second Semester

**Description:** Intermediate programming with arrays, strings, structures, pointers and references, linked lists, files and standard libraries, building on Computer Programming 1 with attention to static versus dynamic data structures and program quality. Memory, pointers and linked structures are taught as an instrument for understanding the computational model, with the safer standard-library alternatives shown alongside; C++ is the reference language.

| # | Section (label) | Reuse | New | Excluded |
|---|---|---|---|---|
| 1 | Arrays | 0 | 4 | 0 |
| 2 | Searching and Sorting Basics | 0 | 3 | 0 |
| 3 | Strings and Text Processing | 0 | 3 | 0 |
| 4 | Structures and Records | 0 | 2 | 0 |
| 5 | Memory, Pointers and References | 1 | 3 | 0 |
| 6 | Linked Structures | 0 | 3 | 0 |
| 7 | Recursion Revisited | 0 | 2 | 0 |
| 8 | Files and Streams | 0 | 2 | 0 |
| 9 | Exceptions and Robust Programming | 0 | 3 | 0 |
| 10 | Standard Libraries and Modules | 0 | 2 | 0 |
| 11 | Program Quality | 0 | 3 | 0 |

### 7. 🧮 Discrete Structures I — Second Semester

**Description:** The mathematical foundations of computing: propositional and predicate logic with rules of inference, sets, relations and functions, integers and divisibility, and proof techniques including induction and program correctness. Assumes competence in algebra and trigonometry (the CHED prerequisite); learners who need preparation can adopt the separate optional plan 'Precalculus (Algebra and Trigonometry)'.

| # | Section (label) | Reuse | New | Excluded |
|---|---|---|---|---|
| 1 | Propositional Logic | 1 | 5 | 0 |
| 2 | Predicate Logic | 0 | 3 | 0 |
| 3 | Rules of Inference and Arguments | 0 | 3 | 0 |
| 4 | Sets | 0 | 4 | 0 |
| 5 | Relations | 0 | 4 | 0 |
| 6 | Functions and Sequences | 0 | 4 | 0 |
| 7 | Integers and Divisibility | 0 | 2 | 0 |
| 8 | Proof Techniques | 0 | 8 | 0 |

### 8. 🪞 Understanding the Self — Second Semester

**Description:** How the self is understood from philosophical, psychological, biological, social and cultural perspectives, its physical, material, digital and political dimensions, and how to manage and care for oneself; placement varies by university.

| # | Section (label) | Reuse | New | Excluded |
|---|---|---|---|---|
| 1 | The Self from Various Perspectives | 3 | 0 | 0 |
| 2 | Dimensions of the Self | 1 | 1 | 0 |
| 3 | Managing and Caring for the Self | 2 | 1 | 0 |
| 4 | Held out of this journey (Excluded) | 0 | 0 | 1 |

### 9. 📜 Readings in Philippine History — Second Semester

**Description:** Philippine history read through primary sources and historical interpretation, from precolonial society through Spanish and American rule, the Revolution, the Japanese occupation and the postwar republic to Martial Law and contemporary legacies; placement varies by university.

| # | Section (label) | Reuse | New | Excluded |
|---|---|---|---|---|
| 1 | Sources and Interpretation | 1 | 0 | 0 |
| 2 | Precolonial and Spanish Periods | 2 | 0 | 0 |
| 3 | Reform, Revolution and the First Republic | 2 | 0 | 0 |
| 4 | American, Japanese and Postwar Periods | 3 | 0 | 0 |
| 5 | Contemporary Philippines | 2 | 0 | 0 |

### 10. 💬 Purposive Communication — Second Semester

**Description:** Effective, audience-aware communication in academic, professional, global and digital settings, with critical reading, academic and workplace writing, and oral presentation; placement varies by university.

| # | Section (label) | Reuse | New | Excluded |
|---|---|---|---|---|
| 1 | Foundations of Communication | 4 | 0 | 0 |
| 2 | Academic and Professional Communication | 3 | 0 | 0 |
| 3 | Communication in a Global and Digital World | 0 | 2 | 0 |
| 4 | Presentation and Workplace Writing | 1 | 2 | 0 |

**The section named "Held out of this journey (Excluded)" is not a real section.** It only holds the 5 Excluded rows. **Do not add those 5 notes to any plan**: "Fundamental Arithmetic and Number Sense" (Mathematics in the Modern World); "Fractions, Decimals, Percentages, and Ratios" (Mathematics in the Modern World); "Algebraic Expressions and Equations" (Mathematics in the Modern World); "Geometry and Measurement" (Mathematics in the Modern World); "Self-Understanding and the Development of the Professional Teacher" (Understanding the Self).

## 4. Put notes into each plan, in teaching order

1. **Reuse notes (50):** add the existing production notes by id from the `Reuse and excluded` sheet of `bscs-year1-authoring-plan.xlsx`, with the Section label from the table above. Do **not** copy them; membership is a join, so one canonical note can sit in several collections.
2. **New notes (163 in the Year):** with Option 2 they arrive in the right plan and section from the batch; with Option 1 add them in the Builder using the label.
3. **Order:** section order follows the lowest note position in each section, and notes follow their position. Add notes in the TSV row order so the teaching order is right the first time; the title list per section is in `bscs-year1-proposed-study-journey.md`.
4. **Section labels are free text** (up to 120 characters, whitespace collapsed, case preserved) and must be spelled exactly as in section 3; a typo creates a second section. The reserved name "Not in a section" is refused. **Bulk Generate pre-fills its Section field from the free-text Subject, so in every Option 2 batch overwrite the Section field with the exact section label from section 3; leaving the pre-fill would file every note under a section named after the Subject.**
5. **A section cannot be empty**: it exists only because notes carry its label, so there is nothing to create ahead of the notes.

## 5. Create the optional Precalculus root (separate, private)

| Field | Value |
|---|---|
| Title | `📈 Precalculus (Algebra and Trigonometry)` |
| Shape | a **standalone root that holds its notes directly** (no child plans, no Year around it) |
| Course / program | **leave unset** (D-26; otherwise it could displace the Year in the recommendation slot) |
| Term | none |
| Visibility | Private until section 7 |
| Description | Optional preparatory course in algebra, functions, trigonometry, analytic geometry and sequences and series, covering the algebra and trigonometry preparation that CHED lists as the prerequisite of Discrete Structures 1. It is a separate, standalone plan for learners who need this preparation before BS Computer Science Year 1; many programs do not teach it as a course. |

| # | Section | New |
|---|---|---|
| 1 | Algebraic Foundations | 8 |
| 2 | Functions and Graphs | 7 |
| 3 | Trigonometry | 8 |
| 4 | Analytic Geometry | 5 |
| 5 | Sequences and Series | 3 |

Consequences already accepted (D-25, D-26): the first top-level plan a learner adopts becomes their primary plan, so a learner who adopts this first sees it as their dashboard primary; it is discoverable in Browse All and named in the Year and Discrete Structures I descriptions. Its notes carry Domain Context `(unset)` with exactly one program; **if any is later reused across a second program, set a Domain Context first** (D-33).

- [ ] Precalculus root created, unset course/program, 5 sections in order.

## 6. Pre-publication gates

- [ ] **Every note in the Year and in the Precalculus root is PUBLIC.** New notes start private; publishing a plan validates this and refuses otherwise.
- [ ] The 40 reused notes carry the Computer Science tag; D-08 confirmed by a curator.
- [ ] The 7 inspection candidates are either read by a curator (and moved to Reuse if confirmed) or remain authored New.
- [ ] Every Study Pack reviewed (batch plan section 5); N notes pass the acceptance check; memory notes pass the D-27 guardrail.
- [ ] R4 sample checks passed for each phase.
- [ ] **Terms coherent and final** (all ten set, correct): they freeze at publication.

## 7. Publish (last, and the point of no easy return)

- Publishing the Year requires **at least one child, every child non-empty, and every note PUBLIC**; it then **cascades `PUBLIC` to every child**. A leaf (the Precalculus root) requires every note PUBLIC and at least one note.
- After publication a Subject Plan's **term cannot change**, and later additions reach existing adopters only through an additive Official update. Unpublishing to private is allowed and does not cascade to children.
- [ ] Publish the Year. [ ] Publish the Precalculus root. [ ] Confirm both appear on Browse All and the Year is the Computer Science recommendation.

## 8. Read-only verification (Claude runs these on request, after each step)

1. **After sections 1 and 2:** the Year root and its 10 children exist, titles exact, in order, terms set on all ten, all private, root course/program as decided.
2. **After each batch:** note count per Subject Plan and Section equals the TSV; titles exact; no duplicate titles; Applicable Programs, Domain Context, Subject and depth per note equal the TSV.
3. **Before publication:** every note PUBLIC; no note outside the TSV in any plan; the 5 Excluded notes absent; reuse ids equal the audit ids.
4. **After publication:** both collections public; terms locked; recommendation slot as intended.

## 9. Safety notes

- Everything before section 7 is private and reversible. Deleting a Year **orphans its children as standalone plans** (they are not cascade-deleted), so do not delete a Year to "start over" without checking.
- The pilot's five private notes (Computing Disciplines, Algorithms, Building and Running, Pointers and References, Propositions) are the same canonical notes the plans use; **do not create duplicates**, reuse them after their fixes.
- Production writes are the owner's at every step above.
