Status: Collection creation checklist, DRAFT for the owner, 2026-11-05. NOTHING here has been done. The specification it builds from is complete (shaping files renamed without the NOT-IMPLEMENTATION-READY marker on 2026-11-05). **For the owner to execute in the production UI. Claude does not create collections** (CLAUDE.md: production writes are owner-only). Claude can run the read-only verification in section 8 after each step. Source of truth for placements: `docs/curriculum/bsit-year1-shaping.tsv`; the exact note ids are in `bsit-year1-reuse-id-manifest.tsv` and in the run sheet.

# BSIT Year 1: collection creation checklist

**Unlike BSCS, no Note has to be authored or generated.** All 211 placements are existing production Notes (the last one, the Counting Note, was created 2026-11-05). The work is: create one root and ten Subject Plans, then add existing Notes to them by id. **Do not create or copy any Note**; membership is a join, so the same canonical Note can sit in the BSCS Year and in this one.

## 0. Preconditions (do not start before these)

- [ ] **The Counting Note is PUBLIC.** Read 2026-11-05: 211 of the 211 placements are existing Notes, 210 are PUBLIC, and the Counting Note (`7564b66c-9dd2-424f-b569-274ec6dd6d20`) was **PRIVATE** at creation. Publishing a plan refuses unless every note in it is PUBLIC.
- [ ] **Decide the root's course/program.** The BSCS Year carries `Computer Science`. Setting `Information Technology` on this root makes it the candidate for the Information Technology recommendation slot (and see the existing root below). Recommendation: `Information Technology`. **Owner to confirm.**
- [ ] **Decide the terms** (section 2): set all ten (First Semester = 1, Second Semester = 2) or none. **Terms freeze at publication and cannot be added or renamed afterwards.** The BSCS plans currently carry NO terms (read 2026-11-05), so if you want BSCS and BSIT consistent, set the BSCS terms **before** BSCS is published again. Recommendation: set terms on BSIT.
- [ ] **The old private root (id `5d4cc820-ad2a-4ee2-9083-fe863bd3dc18`) is NOT this journey's root.** Read 2026-11-05: it was created 2026-07-03 as "💻 BSIT Semester Review", has 4 legacy plans (Advanced Database Systems 14 Notes, Event-Driven Programming 10, Networking II 15, System Integration and Architecture 17), course/program Information Technology, PRIVATE, and was renamed at 10:20Z that day to "🖥 BS Information Technology — 1st Year" (no variation selector) while keeping its old description. **Owner decision (Option A): create a fresh root and rename this one back to "💻 BSIT Semester Review".** Publishing a Year cascades PUBLIC to every child, so the four legacy plans must never ride under a 1st-Year root.
- [ ] Old root renamed back to "💻 BSIT Semester Review". Check read-only (section 8, V6) that it again has exactly that title and still has 4 children.
- [x] Fresh root created 2026-11-05 10:28:42Z: id `94a89870-5522-47d5-b121-5b99b110adea`, title exact, private. Read-only check 2026-11-05: the 10 plans exist in the right order with exact titles, descriptions and terms (First Semester = 1 for plans 1 to 5, Second Semester = 2 for plans 6 to 10), all private, 0 Notes. **Still to fix at that check:** the root description ends with a stray `"` character; course/program and level are empty on the root and all ten plans; the Counting Note is still PRIVATE.
- [ ] (superseded wording follows) Fresh root created; send me its id. Its title must be exactly `🖥️ BS Information Technology — 1st Year` **with the emoji variation selector** (copy from this document, not from the old root's title).
- [ ] **The BSCS Year is not touched.** Read 2026-11-05: its root "🌱 BSCS Year 1 — Computing & College Foundations" is **PRIVATE** with ten PUBLIC children and a legacy 25-item plan of the same name (on 2026-11-03 the same root read PUBLIC, so its state has changed since; this checklist does not rely on it).
- [ ] The two metadata runs are in place (they were run 2026-11-05): the 3 GE Notes carry Computer Science and Information Technology, and 31 Discrete Mathematics Notes carry Information Technology.
- [ ] **Verify the Study Plan Builder lets you add a Note by program or id before you rely on it.** I did not check whether the add-notes picker filters by the collection's course/program. Every Note here carries Information Technology, so a filter would not hide them, but test with one Note first.
- [ ] Known limitations are accepted (recorded, not blocking): Notes 14 Power Sets, Cartesian Products and Cardinality and 18 Partial Orders carry intro-plus depth; Note 8 (resolution), 9 (natural-deduction rules) and 24 (extended Euclid) carry moderate extra depth.

## 1. Create the Year root (private)

| Field | Value |
|---|---|
| Title | `🖥️ BS Information Technology — 1st Year` (copy exactly, including the space after the emoji) |
| Visibility | **Private** until section 7 |
| Course / program | `Information Technology` (**owner to confirm**) |
| Authored/learner level | `College` (the BSCS root is College) |
| Term | none (a root cannot carry a term; setting one is refused) |
| Description | A knowledge-focused Study Journey covering selected first-year subjects in a Philippine BS Information Technology degree, including computing, mathematics, and General Education. ¶ It follows a NoteLib reference curriculum rather than any single university's prospectus. Course names, semester placement, and sequence may differ across institutions. ¶ Physical Education and NSTP, while included in many degree programs, fall outside this Notes-based journey because they primarily involve physical activities, community service, or practical participation. |

(The description has three paragraphs; paste them as separate paragraphs.)

- [ ] Root created, private, description and course/program saved. (Saving metadata is separate from publishing.)

## 2. Create the 10 Subject Plans as children of the Year (private)

Create them in this order; each child gets the next sibling position automatically. **Set a term on all ten or on none**; a partly-termed Year cannot be published. Set each plan's course/program to `Information Technology` as well (the BSCS plans each carry their root's program).

| # | Subject Plan (title, copy exactly) | Term label | Term order | Placements (Reuse / Excluded) |
|---|---|---|---|---|
| 1 | `💻 Introduction to Computing` | First Semester | 1 | 39 / 0 |
| 2 | `⌨️ Computer Programming 1` | First Semester | 1 | 42 / 0 |
| 3 | `📐 Mathematics in the Modern World` | First Semester | 1 | 21 / 4 |
| 4 | `🌏 The Contemporary World` | First Semester | 1 | 9 / 0 |
| 5 | `🎨 Art Appreciation` | First Semester | 1 | 7 / 0 |
| 6 | `🧠 Computer Programming 2` | Second Semester | 2 | 31 / 0 |
| 7 | `🧮 Discrete Mathematics` | Second Semester | 2 | 32 / 3 |
| 8 | `🪞 Understanding the Self` | Second Semester | 2 | 8 / 1 |
| 9 | `📜 Readings in Philippine History` | Second Semester | 2 | 10 / 0 |
| 10 | `💬 Purposive Communication` | Second Semester | 2 | 12 / 0 |

- [ ] All 10 Subject Plans created under the Year, in this order, private.
- [ ] Terms set on all 10 (or deliberately on none).

## 3. Subject Plan descriptions (paste verbatim) and section order

### 1. 💻 Introduction to Computing: First Semester

**Description:** An orientation to the computing disciplines and profession, the history and components of computer systems, how data is represented, how the Internet and the Web work, and the security, ethical and social issues of computing.

| # | Section (label, copy exactly) | Placements | Excluded |
|---|---|---|---|
| 1 | The Computing Disciplines and Profession | 5 | 0 |
| 2 | History and Evolution of Computing | 4 | 0 |
| 3 | Information Representation | 6 | 0 |
| 4 | Computer Systems: Organization and Architecture | 7 | 0 |
| 5 | Networks, the Internet and the Web | 7 | 0 |
| 6 | Computer Security and Malware | 5 | 0 |
| 7 | Ethics, Society and Emerging Trends | 5 | 0 |

### 2. ⌨️ Computer Programming 1: First Semester

**Description:** Fundamentals of programming: designing, implementing, testing and debugging programs with primitive data types, computation, input and output, selection, iteration, functions and recursion, and reviewing code for style and clarity. Concepts are taught language-neutrally where the knowledge is portable; C++ is the reference language for executable examples.

| # | Section (label, copy exactly) | Placements | Excluded |
|---|---|---|---|
| 1 | Problem Solving and Algorithms | 5 | 0 |
| 2 | Program Structure, Variables and Data Types | 6 | 0 |
| 3 | Expressions and Operators | 4 | 0 |
| 4 | Input and Output | 2 | 0 |
| 5 | Selection | 3 | 0 |
| 6 | Iteration | 4 | 0 |
| 7 | Input Validation | 2 | 0 |
| 8 | Functions and Procedural Abstraction | 6 | 0 |
| 9 | Recursion | 2 | 0 |
| 10 | Testing, Debugging and Code Quality | 8 | 0 |

### 3. 📐 Mathematics in the Modern World: First Semester

**Description:** The nature and language of mathematics and its use in data, geometric design, codes, optimisation, finance, social choice, graphs and mathematical systems; placement varies by university.

| # | Section (label, copy exactly) | Placements | Excluded |
|---|---|---|---|
| 1 | The Nature of Mathematics | 5 | 0 |
| 2 | Data Management | 5 | 0 |
| 3 | Logic | 1 | 0 |
| 4 | Geometric Designs | 1 | 0 |
| 5 | Codes and Data Transmission | 3 | 0 |
| 6 | Optimization | 1 | 0 |
| 7 | Mathematics of Finance | 1 | 0 |
| 8 | Social Choice and Fair Division | 2 | 0 |
| 9 | Graphs and Mathematical Systems | 2 | 0 |
| - | Held out of this journey (Excluded) (not a real section) | 0 | 4 |

### 4. 🌏 The Contemporary World: First Semester

**Description:** Globalization and its economic, political, social and environmental dimensions, including global governance, inequality, migration, media and technology, climate change, and the place of the Philippines in the world; placement varies by university.

| # | Section (label, copy exactly) | Placements | Excluded |
|---|---|---|---|
| 1 | Globalization | 3 | 0 |
| 2 | Global Challenges | 3 | 0 |
| 3 | Technology and Society | 1 | 0 |
| 4 | The Philippines and the World | 2 | 0 |

### 5. 🎨 Art Appreciation: First Semester

**Description:** The elements, forms and meanings of art, approaches to interpretation and criticism, major art movements, and Philippine art and cultural heritage; placement varies by university.

| # | Section (label, copy exactly) | Placements | Excluded |
|---|---|---|---|
| 1 | Foundations of Art | 3 | 0 |
| 2 | Forms and Media | 1 | 0 |
| 3 | Interpretation and Criticism | 1 | 0 |
| 4 | History and Heritage | 2 | 0 |

### 6. 🧠 Computer Programming 2: Second Semester

**Description:** Intermediate programming with arrays, strings, structures, pointers and references, linked lists, files and standard libraries, building on Computer Programming 1 with attention to static versus dynamic data structures and program quality. Memory, pointers and linked structures are taught as an instrument for understanding the computational model, with the safer standard-library alternatives shown alongside; C++ is the reference language.

| # | Section (label, copy exactly) | Placements | Excluded |
|---|---|---|---|
| 1 | Arrays | 4 | 0 |
| 2 | Searching and Sorting Basics | 3 | 0 |
| 3 | Strings and Text Processing | 3 | 0 |
| 4 | Structures and Records | 2 | 0 |
| 5 | Memory, Pointers and References | 4 | 0 |
| 6 | Linked Structures | 3 | 0 |
| 7 | Recursion Revisited | 2 | 0 |
| 8 | Files and Streams | 2 | 0 |
| 9 | Exceptions and Robust Programming | 3 | 0 |
| 10 | Standard Libraries and Modules | 2 | 0 |
| 11 | Program Quality | 3 | 0 |

### 7. 🧮 Discrete Mathematics: Second Semester

**Description:** Build the mathematical foundations of computing through propositional and predicate logic, rules of inference, Boolean algebra, sets, relations, functions, sequences, integers and divisibility, elementary counting principles, and introductory proof techniques including mathematical induction. The emphasis is on understanding discrete mathematical concepts and reasoning that support further study in information technology.

| # | Section (label, copy exactly) | Placements | Excluded |
|---|---|---|---|
| 1 | Propositional Logic | 5 | 0 |
| 2 | Predicate Logic | 3 | 0 |
| 3 | Rules of Inference and Arguments | 3 | 0 |
| 4 | Sets | 4 | 0 |
| 5 | Relations | 4 | 0 |
| 6 | Functions and Sequences | 4 | 0 |
| 7 | Counting Principles | 1 | 0 |
| 8 | Integers and Divisibility | 2 | 0 |
| 9 | Proof Techniques | 6 | 0 |
| - | Held out of this journey (Excluded) (not a real section) | 0 | 3 |

### 8. 🪞 Understanding the Self: Second Semester

**Description:** How the self is understood from philosophical, psychological, biological, social and cultural perspectives, its physical, material, digital and political dimensions, and how to manage and care for oneself; placement varies by university.

| # | Section (label, copy exactly) | Placements | Excluded |
|---|---|---|---|
| 1 | The Self from Various Perspectives | 3 | 0 |
| 2 | Dimensions of the Self | 2 | 0 |
| 3 | Managing and Caring for the Self | 3 | 0 |
| - | Held out of this journey (Excluded) (not a real section) | 0 | 1 |

### 9. 📜 Readings in Philippine History: Second Semester

**Description:** Philippine history read through primary sources and historical interpretation, from precolonial society through Spanish and American rule, the Revolution, the Japanese occupation and the postwar republic to Martial Law and contemporary legacies; placement varies by university.

| # | Section (label, copy exactly) | Placements | Excluded |
|---|---|---|---|
| 1 | Sources and Interpretation | 1 | 0 |
| 2 | Precolonial and Spanish Periods | 2 | 0 |
| 3 | Reform, Revolution and the First Republic | 2 | 0 |
| 4 | American, Japanese and Postwar Periods | 3 | 0 |
| 5 | Contemporary Philippines | 2 | 0 |

### 10. 💬 Purposive Communication: Second Semester

**Description:** Effective, audience-aware communication in academic, professional, global and digital settings, with critical reading, academic and workplace writing, and oral presentation; placement varies by university.

| # | Section (label, copy exactly) | Placements | Excluded |
|---|---|---|---|
| 1 | Foundations of Communication | 4 | 0 |
| 2 | Academic and Professional Communication | 3 | 0 |
| 3 | Communication in a Global and Digital World | 2 | 0 |
| 4 | Presentation and Workplace Writing | 3 | 0 |

**"Held out of this journey (Excluded)" is not a real section.** It lists Notes that must NOT be added to any plan:

- Fundamental Arithmetic and Number Sense (📐 Mathematics in the Modern World)
- Fractions, Decimals, Percentages, and Ratios (📐 Mathematics in the Modern World)
- Algebraic Expressions and Equations (📐 Mathematics in the Modern World)
- Geometry and Measurement (📐 Mathematics in the Modern World)
- Logic in Programming and Software Specifications (🧮 Discrete Mathematics)
- Strong Induction (🧮 Discrete Mathematics)
- Proving Program Correctness: Assertions and Loop Invariants (🧮 Discrete Mathematics)
- Self-Understanding and the Development of the Professional Teacher (🪞 Understanding the Self)

## 4. Put Notes into each plan, in teaching order

1. **Add the existing Notes by id** from `bsit-year1-collection-run-sheet.md`, plan by plan, with the section label exactly as written. Do **not** copy a Note or create a new one. All 211 are reuse.
2. **Order:** section order follows the lowest note position in each section, and notes follow their position. Add Notes in the run sheet's row order so the teaching order is right the first time.
3. **Section labels are free text** (up to 120 characters, whitespace collapsed, case preserved) and must match exactly; a typo creates a second section. The reserved name "Not in a section" is refused.
4. **A section cannot be empty**: it exists only because notes carry its label.
5. **Eight Notes are not added** (the Excluded list above). Do not add the three held-out Discrete Structures I Notes: Strong Induction; Proving Program Correctness: Assertions and Loop Invariants; Logic in Programming and Software Specifications.
6. **Reuse by id, never by title.** Eight title-matching copies exist for each reused GE title; only the id in the run sheet is the canonical one (the copy already in the BSCS plans).

## 5. (No optional root)
BSIT has no preparatory Precalculus root. The BSCS one is unrelated and is not adopted here.

## 6. Pre-publication gates

- [ ] **Every note in the Year is PUBLIC**, including the Counting Note. Publishing refuses otherwise.
- [ ] The IT tag is present on all 211 Notes (the read-only check in section 8 confirms it).
- [ ] Terms coherent and final (all ten set, correct): they freeze at publication.
- [ ] The root's course/program and the existing private "BSIT Semester Review" root are what you intend (no accidental second recommendation).
- [ ] Optional but recommended: the 20-Note spot-check of the metadata-ready Notes is already done (35 sampled; Theta defined); nothing outstanding.

## 7. Publish (last, and the point of no easy return)

- Publishing the Year requires at least one child, every child non-empty, and every note PUBLIC; it then **cascades `PUBLIC` to every child**.
- After publication a Subject Plan's **term cannot change**, and later additions reach existing adopters only through an additive Official update. Unpublishing to private is allowed and does not cascade to children.
- [ ] Publish the Year. [ ] Confirm it appears on Browse All and as the Information Technology recommendation (if that is the intent).

## 8. Read-only verification (Claude runs these on request, after each step)

`bsit-year1-collection-verify-read.sql` holds the queries (SELECT only). In order:
1. **After sections 1 and 2:** the root and its 10 children exist, titles exact, in order, terms set on all ten, all private, course/program as decided.
2. **After each plan:** placements per plan and section equal the table in section 3; note ids equal the run sheet; no duplicate titles; no Excluded Note present.
3. **Before publication:** every note PUBLIC; every note tagged Information Technology; no Note outside the TSV in any plan.
4. **After publication:** the Year and children public; terms locked.

## 9. Safety notes

- Everything before section 7 is private and reversible. Deleting a Year **orphans its children as standalone plans**; do not delete a Year to start over without checking.
- Production writes are the owner's at every step above.
- This checklist is a draft: it was written from the shaping TSV and read-only production reads on 2026-11-05. Re-read production state before relying on any claim in it.
