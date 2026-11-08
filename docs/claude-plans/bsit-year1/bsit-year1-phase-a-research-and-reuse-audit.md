Status: Phase A draft, 2026-11-03. SUPERSEDED where it differs from the Phase B documents (owner decisions Q1 to Q7 applied 2026-11-03; see bsit-year1-owner-handoff.md). NOT implementation-ready.

# BS Information Technology — 1st Year: Phase A Research and Canonical Reuse Audit

Labels: **FACT** (read in a source or in production), **INFERENCE** (my reading), **PROPOSAL** (recommended NoteLib decision). Nothing was written to production, no Note was touched, and no collection was created.

---

## 1. Headline

1. **BSIT Year 1 is, in substance, the BSCS Year 1 computing core plus the same GE courses.** (INFERENCE from sections 2 and 3.) CHED makes Introduction to Computing, Computer Programming 1 and 2 *common* to BSCS, BSIS and BSIT. Four of five BSIT curricula read place Discrete Mathematics in Year 1. The GE courses are identical. BSIT's real divergence (Networking, Platform Technologies, Information Management, Web Systems, Systems Administration, Integration) starts in Year 2 at every school read.
2. **Reuse is very high and the authoring cost is near zero**, but it depends on three owner decisions: the reference language, adding Information Technology to 37 Notes, and whether BSIT gets the full BSCS Discrete Structures treatment (section 9).
3. **Production correction (FACT, read 2026-11-03):** the BSCS Year is built, and it is `PUBLIC`. All 163 Notes marked New in the BSCS TSV exist in production, one each. This contradicts the older line "exactly one BSCS Year exists, unpublished".

---

## 2. CHED evidence

| Claim | Label | Source and limit |
|---|---|---|
| CMO 25 s.2015 sets the BSCS, BSIS and BSIT standard; 146 units minimum (GE 54, Common 18, Professional 48, Electives 12 for BSIT, PE 8, NSTP 6; 134 for COEs) | FACT | OCR of the CHED PDF (archive.org mirror, 57 pages), re-extracted locally this session. The official `ched.gov.ph` PDF was unreachable. Figures are OCR, spot-check before publishing |
| Six **common** courses: Introduction to Computing; Computer Programming 1; Computer Programming 2; Data Structures and Algorithms; Information Management; Applications Development and Emerging Technologies | FACT | Same OCR |
| BSIT required professional list: Introduction to HCI; Information Assurance and Security 1 and 2; Fundamentals of Database Systems; Integrative Programming and Technologies 1; **Discrete Mathematics (MS101)**; Quantitative Methods; Networking 1 and 2; Systems Administration and Maintenance; Systems Integration and Architecture 1; Social and Professional Issues; Practicum; Capstone 1 and 2 | FACT | Same OCR, sample curriculum list |
| Recommended BSIT electives: Integrative Programming 2, Platform Technologies, Web Systems and Technologies, Object-Oriented Programming, Systems Integration 2, HCI 2 | FACT | Same OCR. Note Platform Technologies and Web Systems are *electives* in CHED's sample, although the curriculum map places them as knowledge areas |
| BSIT knowledge areas follow ACM IT 2008: IT Fundamentals, HCI, IAS, Information Management, Integrative Programming and Technologies, Networking, Platform Technologies, Systems Administration, Systems Integration, Social and Professional Issues, Web Systems | FACT | Same OCR |
| BSIT requires **Discrete Mathematics**, not Discrete Structures 1 and 2. BSCS requires DS1 and DS2 plus an unnamed 3-unit math elective | FACT | Same OCR unit table |
| The learning outcomes and prerequisites of Discrete Mathematics (MS101) could not be read in the OCR | FACT (a gap) | Treat its content scope as institution-defined until a better source is found |
| The sample Year 1 program-of-study chart for BSIT is mostly illegible (totals only: 21.5 units a semester) | FACT (a gap) | Do not treat it as a term mandate |
| CHED specifies no semester placement as a rule | INFERENCE | Consistent with the BSCS finding |
| CMO 25 is still the operative BSIT standard. No 2024 or 2025 replacement was found | INFERENCE | A 2025 CHED monitoring visit cites it; searches found no superseding CMO. A full CHED CMO list for 2024 to 2026 was not read, so this stays unverified |
| GE: CMO 20 s.2013, 36 units, same eight core courses as BSCS | FACT | Unchanged from the BSCS research; secondary sources only |

---

## 3. University evidence (Year 1)

Read in full this session (PDF text extracted locally): **University of Cebu BSIT 2024 to 2025** (cites CMO 25), **Ateneo de Zamboanga BSIT 2024 to 2025**, **Silliman University BSIT SY 2018 to 2019** (updated Oct 2024), **EVSU Tanauan BSIT** (EVSU Burauen carries the same checklist, so it counts as one), **MSU Maguindanao BSIT**. Not readable: BatStateU (bot-check page), LPU Batangas (403), Ateneo BS ITE (403), PCU (500), UMindanao (404), BukSU (410), EVSU Ormoc (404). **Five curricula is a thin sample; it is a consensus of those five, not of the country.**

| Course | UC | ADZU | Silliman | EVSU | MSU Maguindanao | Reading |
|---|---|---|---|---|---|---|
| Introduction to Computing | 1.1 | 1.1 | 1.1 | 1.1 | 1.1 (Fundamentals of Computing) | 5/5 Year 1, first semester |
| Computer Programming 1 | 1.1 | 1.1 | 1.1 | 1.1 | 1.1 | 5/5 Year 1, first semester |
| Computer Programming 2 | 1.2 | 1.2 | 1.2 | 1.2 | 1.2 | 5/5 Year 1, second semester |
| Discrete Mathematics | 1.2 | summer after Year 1 | 2.1 | 1.2 | 1.1 | 4/5 in Year 1; term varies |
| Web design / programming | 1.1 | 1.1 | 3.1 (Web Systems) | 2.1 (Web Systems 1) | 4.1 | 2/5 in Year 1 |
| HCI (introduction) | 3.1 | 3.1 | 3.2 | 1.2 | 2.2 | 1/5 in Year 1 |
| Networking | 2.2 (Data Comm), 3.1 | 4.2 (Data Comm) | 2.1 (Networking 1) | 2.1 (Intro to Networks) | 2.2 | 0/5 in Year 1 |
| Data Structures and Algorithms | 2.2 | 2.1 | 2.1 | 2.1 | 2.1 | 0/5 in Year 1 |
| Information Management / Database | 3.1 | 2.2 | 2.2 | 2.2 | 2.2 | 0/5 in Year 1 |
| Platform Technologies / Architecture | 2.2 | none printed | 2.2 | 2.1 | 2.1 | 0/5 in Year 1 |
| Digital logic | 2.1 | none | none | none | none | 1/5, outside Year 1 |
| Math in the Modern World | 1.1 | 1.1 | 1.1 | 1.1 | 1.1 | 5/5 Year 1, first |
| Understanding the Self | 1.2 | 1.2 | 1.2 | 1.1 | 1.1 | 3/5 second semester |
| Readings in Philippine History | 1.2 | 1.2 | 2.1 | 1.2 | 1.2 | 4/5 second |
| Purposive Communication | 1.2 | 1.2 | 2.1 | 1.1 | 1.1 | split |
| The Contemporary World | 1.1 | 3.2 | 1.2 | 1.2 | 1.2 | 3/5 first-year second semester |
| Art Appreciation | 1.2 | 2.1 | 1.1 | 2.2 | 1.2 | split (2 of 5 in 1.2) |
| Precalculus / College Algebra | none | none | none | 1.1 | none (Calculus I in 1.2) | 1/5 |
| Ethics, STS, Rizal | Year 2 or later | Year 2 or later | Year 1 to 2 (STS 1.2) | Ethics 1.2, STS 2.2 | Year 2 | not Year 1 by majority |
| PE and NSTP | Year 1 | Year 1 | Year 1 | Year 1 | Year 1 | present everywhere |

Every cell is read from the printed checklist of that school. "None printed" means the course does not appear in that checklist.

**Programming language (FACT, a gap):** none of the five checklists names the Year 1 language. UC lists PHP, .NET, Android and Python as *electives*, which says nothing about CP1. **No evidence settles the BSIT reference language.**

**Consistent (INFERENCE):** Introduction to Computing, CP1 (first semester), CP2 (second semester), Math in the Modern World (first), Readings in Philippine History (second, 4/5). **Leaning:** Understanding the Self and The Contemporary World lean second semester (3/5 each). **Split:** Purposive Communication, Art Appreciation, Discrete Mathematics's term, and Web in Year 1.

---

## 4. Proposed BSIT Year 1 reference (PROPOSAL, preliminary)

Ten Subject Plans, mirroring the BSCS Year, with Discrete Mathematics in place of Discrete Structures I. Same term rule as BSCS (≥3 of 4 majority where one exists; otherwise the BSCS placement, stated as a choice and not as evidence).

| Term | Subject Plan | Why in Year 1 | CHED / common / institution | Overlap with BSCS Year 1 |
|---|---|---|---|---|
| First Semester | Introduction to Computing | 5/5 | CHED common | Identical outcomes; same plan content |
| First Semester | Computer Programming 1 | 5/5 | CHED common | Identical outcomes; language decision (section 8) |
| First Semester | Mathematics in the Modern World | 5/5 | CHED GE core | Identical |
| First Semester | The Contemporary World | GE core; **BSIT schools lean second semester (3/5)** | CHED GE core, institution-dependent term | Identical content. **BSCS placed it first; see Q7** |
| First Semester | Art Appreciation | GE core; split | CHED GE core, institution-dependent term | Identical; BSCS placement kept (no majority) |
| Second Semester | Computer Programming 2 | 5/5 | CHED common | Identical outcomes; language decision |
| Second Semester | Discrete Mathematics | 4/5 in Year 1 | **CHED BSIT required (MS101)**; term varies | **Overlaps DS I, but the treatment may differ** (section 7.3) |
| Second Semester | Understanding the Self | GE core; 3/5 second | CHED GE core | Identical |
| Second Semester | Readings in Philippine History | 4/5 second | CHED GE core | Identical |
| Second Semester | Purposive Communication | GE core; split | CHED GE core | Identical; BSCS placement kept (no majority) |

**Considered, not in the default list:**
- **Web fundamentals as a Year 1 plan:** 2 of 5 only (UC, ADZU), and CHED lists Web Systems as an elective. BSCS Introduction to Computing already holds 4 web Notes (HTML structure, HTML elements, CSS, Web basics). **Owner question Q4.**
- **Networking, HCI, Information Management, Platform Technologies, Data Structures and Algorithms:** Year 2 at every school read. Not Year 1. (BSCS CP2 already holds a 6-Note DSA piece for the CHED CP2 outcomes.)
- **Precalculus / College Algebra:** 1 of 5, and BSIT Discrete Mathematics shows no ALTRIG prerequisite at the schools that print one (none). The existing optional Precalculus preparatory root is not needed. It stays available as an optional pointer only.
- **Science, Technology and Society, Ethics, Life and Works of Rizal, Filipino, Entrepreneurship, Disaster Risk, Peace Education, Calculus (MSU):** institution-specific or Year 2+; not Year 1 by majority.
- **Excluded by scope (documented):** PE and NSTP (activity or service based, same as BSCS D-09), and the same four foundation-mathematics Notes and one teacher-education Note that BSCS held out.

---

## 5. BSCS versus BSIT

**Shared academic foundation (FACT / INFERENCE):** the three CHED common courses of Year 1; the six GE courses; the CHED common programming outcomes (CHED lists the same CP1 and CP2 learning outcomes for all three programs).

**Different learning outcomes:**
- **Mathematics:** BSCS requires Discrete Structures 1 and 2 plus an additional math course; BSIT requires one Discrete Mathematics course and Quantitative Methods. BSIT does not need proofs-heavy treatment as a *stated* requirement, but the CHED outcome text for MS101 was not readable (section 2).
- **Orientation:** BSCS Year 1 aims at theory (algorithms, correctness); BSIT's program emphasis is deploying, integrating and administering infrastructure. That emphasis shows in Year 2+ (Networking 1 and 2, Platform Technologies, Systems Administration), not in Year 1.

**Courses not to copy:** Discrete Structures II, Algorithms and Complexity, Automata, Programming Languages (BSCS-only); Quantitative Methods, Networking, Platform Technologies (BSIT, but Year 2).

**BSIT-specific knowledge in Year 1:** very little. The honest finding is that a Year 1 BSIT journey is largely a re-shelving of existing Notes.

---

## 6. Production state (read-only, 2026-11-03)

All reads were `SELECT`s with `LIMIT` on `notelib-db-prod` (database `notelib_db_prod`). No full pack was run. Catalog names confirmed: `Information Technology`, `Computer Science`, `Information Systems`.

| Finding | Detail |
|---|---|
| The BSCS Year exists and is PUBLIC | Root titled **"🌱 BSCS Year 1 — Computing & College Foundations"** (`course_program` Computer Science), not "🖥️ BS Computer Science — 1st Year" as in the TSV |
| That root also contains a legacy plan of the same name | 25 items. It is the old single-plan draft living beside the ten new Subject Plans. Owner decision, not mine to change |
| The ten Subject Plans match the TSV item counts exactly | Intro 39, CP1 42, CP2 31, DS I 34, MMW 21, Art 7, TCW 9, UtS 8, RPH 10, PurCom 12 = **213** |
| All 163 BSCS "New" Notes exist, one Note per title, `COLLEGE` depth | 140 `COMPUTING`, 23 `GENERAL_EDUCATION` |
| The 50 BSCS "Reuse" titles each have **8 copies** in production | One copy per title is the one in the BSCS Year. Which copy is canonical must be pinned by id at curator time. The in-Year copies carry the IT tag in most cases |
| **Information Technology is already in Applicable Programs on 176 of the 213 Notes in the Year** | Art 7/7, CP1 42/42, CP2 31/31, Intro 38/39, MMW 21/21, PurCom 12/12, RPH 8/10, TCW 9/9, UtS 8/8 |
| **37 Notes lack it:** all **34 Discrete Structures I** (tagged Computer Engineering and Computer Science only), plus **3 reused GE Notes** | "Spanish Colonization and Transformation of Philippine Society", "Philippine Revolution and the First Philippine Republic", "Emerging Technologies and Their Social Impacts": they carry only the six Education programs and **not even Computer Science** |
| IT-tagged legacy Notes outside the Year | Project Management 92 (`ENGINEERING_SCIENCES`), Mathematics 45 more, Database Systems 17, Computer Networking 17, System Integration and Architecture 10, Application Development 8, and a few Web, OOP and Advanced Programming Notes. **The non-math ones have no Domain Context and no Authored Depth set** |
| Content sample | "Cryptography and Data Security" and "Mathematical Systems and Modular Arithmetic" read as program-neutral. Two Notes only; this is not an inspection |
| Not verified | Academic Term and Collection-level Applicable Programs: `note_collections` has no `academic_term` column, so I did not locate where the term is stored. Study Pack existence per Note was not read |

---

## 7. Canonical reuse audit (draft)

### 7.1 Tiers used (no new status)
- **Confirmed Reuse (metadata-ready):** the Note exists, is `COLLEGE` depth, has an explicit Domain Context, already lists Information Technology in Applicable Programs, and was authored or reviewed as shared (BSCS two-test rule, D-13, D-21). Content inspection is still a curator step, but nothing blocks the placement.
- **Candidate Reuse:** needs a decision or an inspection before it can be confirmed.
- **New / Excluded:** as in the TSV spec.

### 7.2 Preliminary counts for the mirrored default Year (213 placements)

| Bucket | Notes | Basis |
|---|---|---|
| **Confirmed Reuse** | **134** | 176 IT-tagged Notes minus the 42 language-specific ones |
| **Candidate Reuse: language decision** | 42 | The C++-named Notes in CP1 (22) and CP2 (20). Confirmed only if the reference language is C++ |
| **Candidate Reuse: add Information Technology** | 37 | 34 Discrete Structures I (also needs the treatment decision) and 3 GE Notes |
| **New** | 0 so far | Phase B may add a few if the owner wants Web fundamentals or a lighter Discrete Mathematics |
| **Excluded** | 5 | Same as BSCS |
| Total placements (excluding Excluded) | 213 | |

**Reuse percentage:** 63% confirmed now (134/213). **100% potential** if the three decisions below go the way the evidence points. I will not optimise for the percentage: academic completeness comes first.

**Where the Notes come from:** 163 authored for BSCS (140 computing, 23 GE), 5 pilot computing Notes recorded as Reuse in BSCS (D-40), and 45 legacy GE Notes. **Duplicates avoided:** up to 213 Notes that BSIT would otherwise have to author.

### 7.3 The Discrete Mathematics question (largest metadata and treatment issue)
- BSCS D-13 deliberately kept the 34 DS I Notes at **Computer Science only**; production now also shows Computer Engineering. Adding Information Technology is a change to Notes the owner scoped narrowly on purpose (an "owner correction to the ratifying package").
- Content that is BSCS-flavoured and may not suit a BSIT Discrete Mathematics course: *Mathematical Induction*, *Strong Induction*, *Proving Program Correctness: Assertions and Loop Invariants*, *Boolean Algebra and Digital Logic*, *Logic in Programming and Software Specifications*. The other 29 are pure logic, sets, relations, functions and integers. **I have not read the 34 Note bodies.**
- Options (Q2): (A) all 34 shared, Applicable Programs gains IT; (B) share 29 and let BSIT hold the 5 out or in a lighter plan; (C) BSIT Discrete Mathematics gets its own authored Notes. My recommendation is **(B)**, pending a curator read of those 5.

### 7.4 Metadata consequences (no writes made)
- Adding Information Technology to Applicable Programs changes **discovery only**. It never reaches a prompt (ADR-001), so no Study Pack changes.
- The 3 GE Notes lacking Computer Science are a **BSCS-side gap too**: they are in the BSCS Year with no CS tag. Flag, do not fix here.
- Domain Context stays as is on every Note. `COMPUTING` Notes are fine as multi-program Notes; nothing needs an unset Domain Context change.

### 7.5 Legacy IT-tagged Notes
Seventeen Computer Networking Notes (OSI model, TCP/IP, DNS, DHCP, IPv4 and IPv6, NAT, VLANs, routing, VPNs and others) are tagged **Information Technology only**, but have no Domain Context and no Authored Depth, so they are not generation-ready and are not Year 1 (Networking is Year 2 at every school read). **Held for a future BSIT Year 2 plan**, not reused here. The BSCS held-candidates decision (D-20) remains in force.

---

## 8. Programming language

The BSCS programming Notes split as follows (from the approved N/S classification and the TSV):

| Class | Notes | Reuse independent of language |
|---|---|---|
| Language-neutral (N) titles | 31 | Yes, if the content really is neutral (D-35: drift is a curator check) |
| C++-named (S) titles | 42 (CP1 22, CP2 20) | Only if the reference language is C++ |

Cost by option:

| Option | Reuse | New authoring |
|---|---|---|
| **A. C++17 restricted (same as BSCS)** | all 73 programming Notes | 0 |
| **B. Neutral only, no reference language** | 31 | the language-specific half does not exist |
| **C. Another language (Python or Java)** | 31 neutral | about 42 new S Notes, plus risk of drift and a "no reference-language field" constraint |

No evidence in the five curricula settles this (section 3). I do not decide it. **Q1.**

---

## 9. Owner decisions needed (blocking Phase B)

| # | Question | Recommendation |
|---|---|---|
| **Q1** | BSIT reference language: A (C++17 restricted), B (neutral only), or C (other language)? | A, because it costs nothing and the evidence cannot distinguish BSIT from BSCS. It is your call |
| **Q2** | Discrete Mathematics: A (all 34 DS I Notes shared), B (share 29, curator reads 5), C (separate authored set)? And may Information Technology be added to the DS I Notes' Applicable Programs? | B |
| **Q3** | May I plan to add Information Technology to the 3 GE Notes that lack it (and note that Computer Science is missing from them too)? | Yes; metadata handoff as a `.sql`, which you run |
| **Q4** | Include a Web fundamentals plan in Year 1 (2 of 5 schools), or leave Web to a Year 2 plan? | Leave to Year 2; BSCS Intro already has 4 web Notes |
| **Q5** | Is a separate BSIT Year 1 root right, given it is nearly the same journey? Alternative: one shared root cross-listed to both programs | Separate root, as you directed. The cost is a second set of collections with no extra authoring |
| **Q7** | The Contemporary World: BSCS placed it in the first semester, but 3 of the 5 BSIT curricula read place it in 1.2. Follow the BSIT majority (second semester, giving 4 plus 6 plans) or keep the BSCS arrangement (5 plus 5)? Terms become permanent once a Subject Plan is published (ADR-003) | Keep 5 plus 5 for symmetry with BSCS and state it as a choice, not evidence. Your call |
| **Q6** | The production BSCS root is titled "🌱 BSCS Year 1 — Computing & College Foundations" and holds a 25-item legacy plan. Should the BSIT root follow the TSV title convention ("🖥️ BS Information Technology — 1st Year")? | Yes. The BSCS rename is yours |

## 10. Curator verification tasks (not done by me)
1. Pin the canonical id for each of the 50 reused GE titles (8 copies each).
2. Read the five BSCS-flavoured DS I Notes (section 7.3).
3. Inspect the Study Pack state of the Notes to be reused.
4. Confirm the Academic Term storage location and the Year-level Applicable Programs for the BSCS Year.
5. Spot-check CHED text against the official PDF (OCR limit).
6. Read 20 to 30 of the 213 Note bodies for program-neutral framing; I read two.

## 11. Phase B (after your answers)
TSV in `docs/curriculum/bsit-year1-shaping.tsv` with `applicable_programs` on every row, workbook from the builder, Study Journey document, decision log and owner handoff, all under `docs/claude-plans/bsit-year1/` with Status headers. No ROADMAP row: the Release Implementor indexes it.

## 12. Sources
- CHED CMO 25 s.2015 (OCR mirror): https://archive.org/download/ched-bsis-bsit-bscs/CHED%20-%20BSIS%20BSIT%20BSCS_text.pdf
- University of Cebu BSIT 2024 to 2025: https://enrollment.uc.edu.ph/college/PROS/ITCS/(BSIT)2024-2025.pdf
- Ateneo de Zamboanga BSIT 2024 to 2025: https://adzu.edu.ph/wp-content/uploads/2025/10/BSIT-24-25.pdf
- Silliman University BSIT: https://su.edu.ph/wp-content/uploads/2026/06/BSIT-updated.pdf
- EVSU Tanauan BSIT: https://tanauan.evsu.edu.ph/wp-content/uploads/2024/10/BSIT-Curriculum-Revision-2021-2022.pdf
- MSU Maguindanao BSIT: https://msumaguindanao.edu.ph/wp-content/uploads/2022/09/bsit-prospectus-curriculum.xlsx-BSIT-Curriculum.pdf
- Compliance monitoring under CMO 25 (2025): https://cst.gsu.edu.ph/?p=3888
