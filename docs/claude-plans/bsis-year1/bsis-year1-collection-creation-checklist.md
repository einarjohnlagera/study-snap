Status: Collection creation checklist, DRAFT for the owner, 2026-11-06. NOTHING here has been done. Document-only: no production write, no Note or Study Pack created, no SQL run, nothing committed.

# BSIS Year 1: collection creation checklist

**Unlike BSIT, twelve Notes have to be created first** (the Fundamentals of Information Systems plan; see `bsis-year1-fis-note-creation-checklist.md`). The other 182 placements are existing production Notes. The work is: create one root and ten Subject Plans, then add existing Notes by id. **Do not create or copy any shared Note**; membership is a join, so the same canonical Note sits in the BSCS Year, the BSIT Year and this one. **For the owner to execute in the production UI. Claude does not create collections** (`CLAUDE.md`). Claude can run the read-only verification in section 8 after each step. Source of truth: `docs/curriculum/bsis-year1-shaping.tsv`, the exact-ID manifest `bsis-year1-reuse-id-manifest.tsv`, and `bsis-year1-collection-run-sheet.md`.

## 0. Preconditions (do not start before these)

- [ ] **Phase 1 done** (owner, each as dry run, inspect, approve, apply, verify): the RA 10364 correction in *Global Citizenship and Global Civil Society*; the five-Note Information Systems tag script; the held sixth tag script. Read-only check S1/S2/V5. **Before Phase 1, V5 reports exactly 6 Notes missing Information Systems; that is expected.**
- [ ] **The twelve FIS Notes exist, passed the Study Pack review, and are PUBLIC**, their ids recorded in `bsis-year1-fis-created-ids.tsv` and the run sheet regenerated so every id cell is filled (no "create first" cell remains).
- [ ] **Every shared Note is PUBLIC (FACT, read 2026-11-06: all 182).** Publishing a plan refuses unless every Note in it is PUBLIC.
- [x] **Root title emoji decided: 🗄️ (file cabinet), owner choice 2026-11-06 (D-53).** The read-only queries match on the title tail, so they work with the prefix.
- [x] **Root course/program decided: `Information Systems`** (owner, 2026-11-06, D-53).
- [ ] **Terms: 5 plus 5 as in the table (section 2). They freeze at publication and cannot be changed afterwards.** All ten or none.
- [ ] **Convention (FACT, read from the BSCS and BSIT builds 2026-11-06):** root `learner_level` COLLEGE; plans `learner_level` NULL; plan and root `course_program` set; terms set; `estimated_study_hours` NULL. Follow it; verify with V1 and V6 after creating (the BSIT build once left the program empty and a stray quote in the description; check for both).
- [ ] **The BSCS and BSIT Years are not touched.** V9 shows them; compare with the previous reading.
- [ ] **Test the Study Plan Builder with one Note first** (add by id; confirm the picker shows the id/title you expect before adding the rest).
- [ ] Known limitations carried: N12 (candidate, moderate) and N1/N2/N3 (candidate, low) have a pending taxonomy review; IS 2010.1 unread; see the readiness report.

## 1. Create the Year root (private)

| Field | Value |
|---|---|
| Title | `🗄️ BS Information Systems — 1st Year` (owner-chosen file-cabinet prefix, D-53; **copy it, do not retype it**: `python3 tools/copy_plan_field.py root-title \| pbcopy`) |
| Visibility | **Private** until section 7 |
| Course / program | `Information Systems` (**owner to confirm**) |
| Authored/learner level | `College` |
| Term | none (a root cannot carry a term) |
| Description | three paragraphs, below |

**Root description (owner-approved, D-17), pasted as three separate paragraphs:**

> Build your first-year foundations in computing, information systems, mathematics, and General Education through a curated Study Journey based on selected subjects in a Philippine BS Information Systems degree.
>
> It follows a NoteLib reference curriculum rather than any single university's prospectus. Course names, semester placement, and sequence may differ across institutions.
>
> Physical Education and NSTP, while included in many degree programs, fall outside this Notes-based journey because they primarily involve physical activities, community service, or practical participation.

- [ ] Root created, private, description and course/program saved. Claude runs V1 and V1b: one root; description has no stray characters.

## 2. Create the 10 Subject Plans as children of the Year (private)

Create them in this order; each child takes the next sibling position. **Set a term on all ten or on none.** Set each plan's course/program to `Information Systems`; leave the level empty.

| # | Subject Plan (title, copy exactly) | Term label | Term order | Placements (Reuse + New / Excluded) |
|---|---|---|---|---|
| 1 | `💻 Introduction to Computing` | First Semester | 1 | 39 + 0 / 0 |
| 2 | `⌨️ Computer Programming 1` | First Semester | 1 | 42 + 0 / 0 |
| 3 | `📐 Mathematics in the Modern World` | First Semester | 1 | 21 + 0 / 4 |
| 4 | `🪞 Understanding the Self` | First Semester | 1 | 8 + 0 / 1 |
| 5 | `🎨 Art Appreciation` | First Semester | 1 | 9 + 0 / 0 |
| 6 | `🧠 Computer Programming 2` | Second Semester | 2 | 31 + 0 / 0 |
| 7 | `🏢 Fundamentals of Information Systems` | Second Semester | 2 | 0 + 12 / 0 |
| 8 | `📜 Readings in Philippine History` | Second Semester | 2 | 10 + 0 / 0 |
| 9 | `💬 Purposive Communication` | Second Semester | 2 | 12 + 0 / 0 |
| 10 | `🌏 The Contemporary World` | Second Semester | 2 | 10 + 0 / 0 |

- [ ] All 10 Subject Plans created under the Year, in this order, private. - [ ] Terms set on all 10. Claude runs V1 and V6.

## 3. Subject Plan descriptions (paste verbatim) and section order

### 1. 💻 Introduction to Computing: First Semester

**Description:** An orientation to the computing disciplines and profession, the history and components of computer systems, how data is represented, how the Internet and the Web work, and the security, ethical and social issues of computing.

| # | Section (label, copy exactly) | Placements |
|---|---|---|
| 1 | The Computing Disciplines and Profession | 5 |
| 2 | History and Evolution of Computing | 4 |
| 3 | Information Representation | 6 |
| 4 | Computer Systems: Organization and Architecture | 7 |
| 5 | Networks, the Internet and the Web | 7 |
| 6 | Computer Security and Malware | 5 |
| 7 | Ethics, Society and Emerging Trends | 5 |

### 2. ⌨️ Computer Programming 1: First Semester

**Description:** Fundamentals of programming: designing, implementing, testing and debugging programs with primitive data types, computation, input and output, selection, iteration, functions and recursion, and reviewing code for style and clarity. Concepts are taught language-neutrally where the knowledge is portable; C++ is the reference language for executable examples.

| # | Section (label, copy exactly) | Placements |
|---|---|---|
| 1 | Problem Solving and Algorithms | 5 |
| 2 | Program Structure, Variables and Data Types | 6 |
| 3 | Expressions and Operators | 4 |
| 4 | Input and Output | 2 |
| 5 | Selection | 3 |
| 6 | Iteration | 4 |
| 7 | Input Validation | 2 |
| 8 | Functions and Procedural Abstraction | 6 |
| 9 | Recursion | 2 |
| 10 | Testing, Debugging and Code Quality | 8 |

### 3. 📐 Mathematics in the Modern World: First Semester

**Description:** The nature and language of mathematics and its use in data, geometric design, codes, optimisation, finance, social choice, graphs and mathematical systems; placement varies by university.

| # | Section (label, copy exactly) | Placements |
|---|---|---|
| 1 | The Nature of Mathematics | 5 |
| 2 | Data Management | 5 |
| 3 | Logic | 1 |
| 4 | Geometric Designs | 1 |
| 5 | Codes and Data Transmission | 3 |
| 6 | Optimization | 1 |
| 7 | Mathematics of Finance | 1 |
| 8 | Social Choice and Fair Division | 2 |
| 9 | Graphs and Mathematical Systems | 2 |

### 4. 🪞 Understanding the Self: First Semester

**Description:** How the self is understood from philosophical, psychological, biological, social and cultural perspectives, its physical, material, digital and political dimensions, and how to manage and care for oneself; placement varies by university.

| # | Section (label, copy exactly) | Placements |
|---|---|---|
| 1 | The Self from Various Perspectives | 3 |
| 2 | Dimensions of the Self | 2 |
| 3 | Managing and Caring for the Self | 3 |

### 5. 🎨 Art Appreciation: First Semester

**Description:** The elements, forms and meanings of art, approaches to interpretation and criticism, major art movements, and Philippine art and cultural heritage; placement varies by university.

| # | Section (label, copy exactly) | Placements |
|---|---|---|
| 1 | Foundations of Art | 3 |
| 2 | Forms and Media | 2 |
| 3 | Interpretation and Criticism | 1 |
| 4 | Art in Contemporary Society | 1 |
| 5 | History and Heritage | 2 |

### 6. 🧠 Computer Programming 2: Second Semester

**Description:** Intermediate programming with arrays, strings, structures, pointers and references, linked lists, files and standard libraries, building on Computer Programming 1 with attention to static versus dynamic data structures and program quality. Memory, pointers and linked structures are taught as an instrument for understanding the computational model, with the safer standard-library alternatives shown alongside; C++ is the reference language.

| # | Section (label, copy exactly) | Placements |
|---|---|---|
| 1 | Arrays | 4 |
| 2 | Searching and Sorting Basics | 3 |
| 3 | Strings and Text Processing | 3 |
| 4 | Structures and Records | 2 |
| 5 | Memory, Pointers and References | 4 |
| 6 | Linked Structures | 3 |
| 7 | Recursion Revisited | 2 |
| 8 | Files and Streams | 2 |
| 9 | Exceptions and Robust Programming | 3 |
| 10 | Standard Libraries and Modules | 2 |
| 11 | Program Quality | 3 |

### 7. 🏢 Fundamentals of Information Systems: Second Semester

**Description:** An introduction to how information systems support organizations: the people, processes, data and technology that make up a system, the information organizations need to operate, manage and decide, the main kinds of information systems and enterprise applications, and how systems create value, change organizations, and are developed, acquired and adopted.

| # | Section (label, copy exactly) | Placements |
|---|---|---|
| 1 | Information Systems and Organizations | 3 |
| 2 | Information Needs and Decision Making | 1 |
| 3 | Kinds of Information Systems | 4 |
| 4 | Technology, Strategy and Change | 4 |

### 8. 📜 Readings in Philippine History: Second Semester

**Description:** Philippine history read through primary sources and historical interpretation, from precolonial society through Spanish and American rule, the Revolution, the Japanese occupation and the postwar republic to Martial Law and contemporary legacies; placement varies by university.

| # | Section (label, copy exactly) | Placements |
|---|---|---|
| 1 | Sources and Interpretation | 1 |
| 2 | Precolonial and Spanish Periods | 2 |
| 3 | Reform, Revolution and the First Republic | 2 |
| 4 | American, Japanese and Postwar Periods | 3 |
| 5 | Contemporary Philippines | 2 |

### 9. 💬 Purposive Communication: Second Semester

**Description:** Effective, audience-aware communication in academic, professional, global and digital settings, with critical reading, academic and workplace writing, and oral presentation; placement varies by university.

| # | Section (label, copy exactly) | Placements |
|---|---|---|
| 1 | Foundations of Communication | 4 |
| 2 | Academic and Professional Communication | 3 |
| 3 | Communication in a Global and Digital World | 2 |
| 4 | Presentation and Workplace Writing | 3 |

### 10. 🌏 The Contemporary World: Second Semester

**Description:** Globalization and its economic, political, social and environmental dimensions, including global governance, inequality, migration, media and technology, climate change, and the place of the Philippines in the world; placement varies by university.

| # | Section (label, copy exactly) | Placements |
|---|---|---|
| 1 | Globalization | 3 |
| 2 | Global Challenges | 3 |
| 3 | Global Citizenship and Civil Society | 1 |
| 4 | Technology and Society | 1 |
| 5 | The Philippines and the World | 2 |

**Excluded (do not add to any plan):**

- Fundamental Arithmetic and Number Sense (📐 Mathematics in the Modern World)
- Fractions, Decimals, Percentages, and Ratios (📐 Mathematics in the Modern World)
- Algebraic Expressions and Equations (📐 Mathematics in the Modern World)
- Geometry and Measurement (📐 Mathematics in the Modern World)
- Self-Understanding and the Development of the Professional Teacher (🪞 Understanding the Self)

## 4. Put Notes into each plan, in teaching order

**Build from the workbook:** `bsis-year1-collection-build-workbook.xlsx` has one sheet per plan (sections, exact labels, Note titles and Production Note IDs in teaching order, an Added column) plus a Build Tracker of all 194 placements. It is generated from the same manifest as the run sheet; use whichever is easier.

1. **Add the Notes by id** from `bsis-year1-collection-run-sheet.md`, plan by plan, with the section label exactly as written. Do **not** copy a Note or create a new one while building the collection. 182 are shared Notes; 12 are the Fundamentals of IS Notes created earlier.
2. **Order:** add Notes in the run sheet's row order; sections follow the lowest Note position.
3. **Section labels are free text and must match exactly**; a typo creates a second section (V2b catches case/space variants). The reserved name "Not in a section" is refused. Section labels carry **no numbers** (the TSV labels are exactly as listed; ignore any "1, 2, 3" ordering index used in the readiness report).
4. **A section cannot be empty**; it exists because Notes carry its label.
5. **Reuse by id, never by title.** Title-matching copies exist for reused titles; only the id in the run sheet is the canonical one (the copy already in the BSCS and BSIT plans).

## 5. (No optional root)
BSIS has no preparatory Precalculus root. The BSCS one is unrelated and is not adopted here.

## 6. Pre-publication gates

- [ ] **Every Note in the Year is PUBLIC** (V4, V4b clean), including the twelve FIS Notes.
- [ ] **Information Systems on all 194** (V5: `missing_is` = 0; V5b empty).
- [ ] **Every FIS Note has one Study Pack, status GENERATED** (V7, V7b clean).
- [ ] **Placements exactly match the TSV** (V3 empty, V3c empty, V3d empty; 194 placements).
- [ ] Terms coherent and final (V6): they freeze at publication.
- [x] The N12 taxonomy decision (and N1/N2/N3) is recorded: **closed 2026-11-06 (D-52), all four Option A, Information Systems only, Domain Context unset**; nothing is published against an unresolved review.
- [x] The shared-Note spot-checks are complete (15 of 15) and D1/D2 are applied and verified (readiness report sections 9 and 10).

## 7. Publish (last, and the point of no easy return)

- Publishing the Year requires at least one child, every child non-empty, and every Note PUBLIC; it then **cascades `PUBLIC` to every child**.
- After publication a Subject Plan's **term cannot change**, and later additions reach existing adopters only through an additive Official update. Unpublishing is allowed and does not cascade to children.
- **Each deploy and publication needs separate explicit owner approval; no artificial traffic against production.** Verify the live site afterwards (`scripts/check-deploys.sh` after any merge).
- [ ] Publish the Year. [ ] Claude runs V8 and V9.

## 8. Read-only verification (Claude runs these on request, after each step)

`bsis-year1-collection-verify-read.sql` (SELECT only; the header lists which phase each query belongs to). In order:
1. **Before anything:** F0, S1.
2. **After each FIS Note:** F1, F2.
3. **After the root and plans:** V1, V1b, V6.
4. **After each plan:** V2, V2b, V3, V3c, V3d.
5. **Before publication:** V4, V4b, V5, V5b, V7, V7b.
6. **After publication:** V8, V9.

## 9. Safety notes

- Everything before section 7 is private and reversible. Deleting a Year **orphans its children as standalone plans**; do not delete a Year to start over without checking.
- Production writes are the owner's at every step above.
- This checklist was written from the shaping TSV and read-only production reads on 2026-11-06. Re-read production state before relying on any claim in it.
