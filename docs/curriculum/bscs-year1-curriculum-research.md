# BS Computer Science — 1st Year: Curriculum Research and Decisions

Date: 2026-09-29 · Status: research and proposal, no production changes · Companion files: `bscs-year1-proposed-study-journey.md`, `bscs-year1-curriculum-coverage.xlsx`, `bscs-year1-shaping.tsv`

> **Updated 2026-09-30** for the owner decisions on the 2026-09-29 proposal (default Year now 10 Subject Plans; Precalculus optional and separate; PE and NSTP explicitly out of scope). Sections 1–3 (evidence) are unchanged; sections 4 and 5 are revised in place.

Every statement is labelled **CHED** (regulatory text), **UNIVERSITY** (a named school's implementation), **INFERENCE** (my reading of the evidence) or **PROPOSAL** (a recommended NoteLib decision).

---

## 1. Evidence quality — read this first

| Source | What I could read | Limitation |
|---|---|---|
| CHED CMO 25 s.2015 (BSCS/BSIT/BSIS PSG) | Full 57-page OCR text from an archive.org mirror stamped "Official Release, CHED Central Office, Records Section" | The official PDF (`legacy.ched.gov.ph`, `ched.gov.ph`) was unreachable (connection refused / 403) and the Wayback Machine is blocked here. OCR is noisy: unit counts and prerequisites for some courses are unreliable, and the Section 9.2 sample program-of-study chart is only partly legible. |
| CHED CMO 20 s.2013 (General Education) | Secondary sources only: a CHED Region I slide deck, a school syllabus quoting the CMO, a Jesuit blog, Rappler on the Supreme Court ruling | Full CMO text not obtained. The GE-elective units conflict between sources (9 units per the CHED slides; 6 in a blog). The Science, Technology and Society description is a search snippet only. |
| University curricula | Read in full: University of Antique, Asian Institute of Computer Studies (AICS), University of Cebu, Palawan State University. Read but weaker: Ateneo de Manila (two-column PDF interleaved), UP Los Baños (flattened mirror, units unreliable), UP Tacloban (no term table) | LPU Batangas returned 403 (all three URLs). VSU and Quezon City University are scanned PDFs with no text layer. No BSCS housed under Engineering/CET with a published term table was found. |

**Consequence (INFERENCE):** the term placements below are a consensus of *four* CMO-25-based curricula, not a survey of the country. CHED figures should be spot-checked against the official PDF before anything is published.

Is CMO 25 still in force? **NOT FOUND superseded.** Searches of ched.gov.ph, the mirrors, and PIDS/CHED 2025 CMO listings found no replacement; the only 2025 CMO the search surfaced (No. 16 s.2025) covers BS Manufacturing Engineering. University of Cebu's BSCS curriculum (effective SY 2024–2025) still states it is "Based on CMO No. 25 series of 2015". A complete 2024–2025 CMO list was not read, so treat CMO 25 as the presumed operative standard, **unverified**.

---

## 2. What CHED requires

### 2.1 CMO 25 s.2015 (BSCS/BSIS/BSIT) — CHED
- Units are a **minimum**; HEIs may design their own curricula and show the outcomes are attained "albeit by a different route" (Sec. 4).
- BSCS minimum total **146 units** (pre-K12): General Education 54, Common Courses 18, Professional Courses 48, Professional Electives 9, Additional Math Requirement 3, PE 8, NSTP 6. After K-12 the GE component is 36 units (CMO 20) and the remaining 18 units become professional/domain courses, keeping 146 (this last point is from a secondary CHED slide deck).
- **Six common courses** (shared by BSCS, BSIT and BSIS): Introduction to Computing; Computer Programming 1 (Fundamentals of Programming); Computer Programming 2 (Intermediate Programming); Data Structures and Algorithms; Information Management; Applications Development and Emerging Technologies.
- BSCS required professional list adds: Object-oriented Programming; Discrete Structures 1 and 2; Algorithms and Complexity; Automata Theory and Formal Languages; Architecture and Organization; Networks and Communications; Operating Systems; Programming Languages; Information Assurance and Security; Human Computer Interaction; Software Engineering 1 and 2; Social Issues and Professional Practice; Practicum; CS Thesis Writing 1 and 2.
- **Mathematics:** Discrete Structures 1 and 2 are required. One 3-unit "Additional Math Requirement" (Math Elective) is required but unnamed. **Calculus, Linear Algebra and Probability/Statistics are not named as required BSCS courses** (CHED). GE mathematics is listed as "Algebra, Statistics" in the pre-K12 GE table.
- **Semester placement is not prescribed as a rule.** Section 9.2 gives a "Sample Program of Study" (Freshman semesters total 18.5 units each). The chart is only partly legible and its column alignment is unreliable, so **the CMO's own term placement of Discrete Structures 1 cannot be established from it** (its prerequisite map, ALTRIG only, would permit First Semester; all four CMO-25 schools nonetheless use Second). The chart lists only computing courses and unit totals, **not GE courses by term**. Treat it as a sample, not a mandate.
- **Prerequisites (CHED curriculum map):** Introduction to Computing none · Programming 1 none · Programming 2 ← Programming 1 · Data Structures and Algorithms ← Programming 2 · Applications Development ← Programming 2 · **Discrete Structures 1 ← "ALTRIG" (Algebra and Trigonometry)** · Discrete Structures 2 ← Discrete Structures 1. Prerequisites for OOP, Programming Languages, Architecture and Organization and HCI could not be read reliably.
- **Learning outcomes (CHED)** used as the spine of these Subject Plans: Introduction to Computing LO1–LO3; Fundamentals of Programming LO1 (which explicitly lists functions with parameter passing **and recursion**) and LO2 (code review on documentation, style, constructs, procedural abstraction); Intermediate Programming LO1–LO3 (arrays, strings, structures, linked lists, files, standard libraries; **static versus dynamic implementations**); Discrete Structures 1 LO1–LO3 (sets/functions/relations; propositional and predicate logic with rules of inference; proofs by induction, direct proof, contradiction); Discrete Structures 2 (legible in the OCR: LO1 permutations, combinations and modular arithmetic; LO2 probability using counting and Bayes theorem; LO3 recurrences related to recursive algorithms). Discrete Structures 1 LO1 also says to relate set/function/relation operations to programming, and LO3 says proofs are used to simplify programs and prove program correctness.
- CMO 25 has **no course description** for Discrete Structures or Calculus; Discrete Structures appears only as knowledge-area wording ("Logic, Sets, Relations, Functions, and Proof Techniques; Graphs, Trees, Matrices, Combinatorics and Recurrences") and the outcomes above.
- The Introduction to Computing course description (CHED): overview of the computing industry and profession; appreciation of computing in fields such as biology, sociology, environment and gaming; ACM requirements; history of computing; key components of computer systems (organization and architecture); malware; computer security; Internet and Internet protocols; HTML and CSS. The CMO sample syllabus is a 14-week plan (weeks 1–3 industry/profession, 4 evolution, 5–6 components and operating systems, 7 malware, 8 security, 9–10 networks/Internet/protocols, 11–14 computer systems).

### 2.2 CMO 20 s.2013 (General Education) — CHED (via secondary sources)
- **36 units:** 24 units of eight core courses, 9 units of GE electives (HEI-designed, interdisciplinary), and the 3-unit mandated Rizal course (RA 1425).
- **Core courses:** Understanding the Self; Readings in Philippine History; The Contemporary World; Mathematics in the Modern World; Purposive Communication; Art Appreciation; Science, Technology and Society; Ethics.
- **Semester placement is not prescribed** in anything read. The CMO sets units only.
- **Mathematics in the Modern World (description as quoted by a school syllabus citing the CMO):** nature of mathematics; appreciation of its practical, intellectual and aesthetic dimensions; "managing personal finances, making social choices, appreciating geometric designs, understanding codes used in data transmission and security, and dividing limited resources fairly". Outline: *The Nature of Mathematics* (Mathematics in our World; Mathematical Language and Symbols; Problem Solving and Reasoning) and *Mathematics as a Tool* (Data Management; Geometric Designs; Codes; Linear Programming; Mathematics of Finance; Apportionment and Voting; Logic; Mathematics of Graphs; Mathematical Systems).
- **Filipino / Panitikan:** CMO 20 dropped them from the core (CHED). The Supreme Court held CMO 20 a "minimum standards" issuance that does not stop schools requiring Filipino, Panitikan and the Constitution (per Rappler/PNA summaries). CMO 57 s.2017 reportedly urged HEIs to keep 3–9 units of Filipino; its binding force after the 2018 ruling is **not verified**.

---

## 3. What universities do — Year 1

Units as printed. Terms: 1.1 = Year 1 First Semester.

| Course | Antique | AICS | Cebu | Palawan | Ateneo (autonomous) | UPLB (autonomous) |
|---|---|---|---|---|---|---|
| Introduction to Computing | 1.1 | 1.1 | 1.1 | 1.1 | 1.1 | 1.1 (Foundations of CS) |
| Programming 1 | 1.1 | 1.1 | 1.1 | 1.1 | 1.1 | 1.2 (only programming course in Year 1) |
| Programming 2 | 1.2 | 1.2 | 1.2 | 1.2 | 1.2 | not Year 1 |
| Discrete Structures 1 | 1.2 | 1.2 | 1.2 | 1.2 | 2.1 (Math for CS I) | 1.1 |
| Math in the Modern World | 1.1 | 1.2 | 1.1 | 1.1 | 1.1 | not required |
| Understanding the Self | 1.2 | 1.1 | 1.2 | 1.2 | 1.2 | not required |
| Readings in Philippine History | 1.2 | 1.1 | 1.2 | 1.1 | 2.2 (HISTO 12) | 1.2 |
| Purposive Communication | 1.2 | 2.1 | 1.2 | 1.2 | 1.1 | not required |
| Art Appreciation | 1.1 | 2.1 | 1.1 | 1.1 | 2.1 | 1.2 |
| The Contemporary World | 1.1 | 1.2 | 1.1 | 1.2 | 2.1 (intersession) | not required |
| Science, Technology and Society | 2.2 | 2.2 | 2.1 | 1.2 | Year 4 | 1.1 |
| Ethics | 2.2 | 2.2 | 2.2 | 2.1 | Year 3 | 1.1 |
| Life and Works of Rizal | 3.1 | 4.2 | 2.1 | 2.2 | 1.2 | 2.1 |
| Filipino | none | 1.1 and 1.2 | none | none | 1.1 and 1.2 | none |
| Precalculus / Algebra / Trigonometry | 1.1 Pre-Calculus Review; 1.2 Spherical Trigonometry with Solid Mensuration | ALTRIG prerequisite listed, no course | none | none | 1.1 Precalculus | admission prerequisite |
| Calculus | 2.2 (Calculus 1) | 2.2 (elective) | 2.1 and 2.2 | none named (unnamed Math Elective 2.2) | 1.2 and 2.1 | 1.1 and 1.2 |
| Probability and Statistics | 2.1 | none | 2.1 | 3.1 (Math for Data Science; inferred from the course title) | 2.1 intersession | 2.2 |
| Data Structures and Algorithms | 2.1 | 2.1 | 2.1 | 2.1 | 2.1 | 2.1 |

Sources: University of Antique (`antiquespride.edu.ph/ccis/`, curriculum version not stated, 163 units); AICS (`aics.edu.ph/.../Approved_OBE_BSCS_Curriculum.pdf`, 2018, 148 units); University of Cebu (`enrollment.uc.edu.ph/college/PROS/ITCS/(BSCS)2024-2025.pdf`, 158 units; PDF header carries a BSIT template artifact but the content is clearly BSCS); Palawan State University (`psu.palawan.edu.ph/.../ComSci-for-student-new-version.pdf`, effective SY 2018–2019); Ateneo (`ateneo.edu/.../BS CS Program of Study.pdf`; FILI 11 in 1.2, FILI 12 in 1.1 per an independent re-read); UPLB (2018 curriculum, mirror).

### 3.1 What is consistent (UNIVERSITY / INFERENCE)
Among the four CMO-25 curricula:
- Introduction to Computing and Programming 1 → **Year 1 First Semester (4/4)**.
- Programming 2 and Discrete Structures 1 → **Year 1 Second Semester (4/4)**.
- Data Structures and Algorithms → Year 2 First Semester (4/4).
- Every school places PE and NSTP in Year 1.
- Calculus is in Year 1 at **0 of 4**; Science, Technology and Society at 1 of 4; Ethics 0 of 4; Rizal 0 of 4.

### 3.2 What varies
- Semester of the Year 1 GE courses; where Calculus sits; whether Precalculus is taught (1 of 4); whether Filipino appears (1 of 4); Probability and Statistics (Year 2 or 3).
- The two autonomous schools (Ateneo, UPLB) depart from the CMO template (Ateneo: discrete mathematics in Year 2; UPLB: calculus and discrete structures in Year 1) and are treated as evidence of **variability**, not as the reference.

---

## 4. Key academic decisions (PROPOSAL)

### 4.1 Year 1 subject list — 10 default Subject Plans plus one optional preparatory plan *(revised 2026-09-30; was 11 plans in one Year)*
**First Semester (5):** Introduction to Computing · Computer Programming 1 · Mathematics in the Modern World · The Contemporary World · Art Appreciation.
**Second Semester (5):** Computer Programming 2 · Discrete Structures I · Understanding the Self · Readings in Philippine History · Purposive Communication.
**Optional preparatory (separate standalone root, not a child of the Year):** Precalculus (Algebra and Trigonometry).

Not in Year 1: Calculus, Linear Algebra, Probability and Statistics, Science, Technology and Society, Ethics, Life and Works of Rizal, Filipino. **PE and NSTP are an explicit scope exclusion** (owner decision D-09): they are part of many BSCS programs but are activity- or service-based, not knowledge Notes, and the Year description says so without implying they are unimportant or universally absent.

### 4.2 Term rule
1. Computing courses follow the 4/4 consensus and the CMO prerequisite chain.
2. A GE course goes to the semester chosen by a majority (≥3 of 4) of the CMO-25 schools: Mathematics in the Modern World → First (3/4), Art Appreciation → First (3/4), Understanding the Self → Second (3/4), Purposive Communication → Second (3/4).
3. Where the four split 2–2 (Readings in Philippine History, The Contemporary World) I chose to follow Antique and Cebu (who agree on every GE cell; AICS and Palawan agree on the opposite placement) — **a choice, not evidence**: Philippine History → Second, The Contemporary World → First. With Precalculus moved out of the default Year, this gives First Semester 5 plans and Second Semester 5. The owner accepted the arrangement on 2026-09-30 as a NoteLib reference, not a claim of universal Philippine placement.
4. **No Summer term** — none of the CMO-25 curricula has one in Year 1.
5. Terms become permanent once a Subject Plan is published (ADR-003), so every GE `plan_description` says placement varies by university.

### 4.3 Mathematics policy
- **General Education Mathematics** = Mathematics in the Modern World only, built from the CHED outline, not from whatever mathematics notes exist. It is not a substitute for algebra, calculus or discrete mathematics.
- **Technical foundations:** Precalculus (Algebra and Trigonometry) is kept because the CMO lists ALTRIG as the Discrete Structures 1 prerequisite — but only 1 of 4 CMO-25 schools teaches such a course, so it is CURRICULUM_DEPENDENT. **Owner decision D-04 (2026-09-30): it is an optional preparatory plan delivered as a separate standalone root, not a child of the default Year** (see `bscs-year1-precalculus-preparatory-proposal.md`). Discrete Structures I stands alone and points to it. Calculus, Linear Algebra and Probability/Statistics are **not** Year 1: CMO 25 does not require them and no CMO-25 school places them in Year 1.
- **CS mathematical foundations:** Discrete Structures I covers the CMO LO1–LO3 (logic, proofs, sets, relations, functions) plus integers and divisibility (divisibility, the division algorithm, gcd and the Euclidean algorithm). Counting, modular arithmetic, probability with Bayes theorem, recurrences, graphs and trees belong to Discrete Structures II (Year 2) per the CMO DS2 outcomes; an earlier proposal had counting in Year 1.
- **No split by college** (D-10): no evidence of a difference; no CET-housed term table was found.

### 4.4 Institution-dependent subjects
Programming language (CMO says only "a general purpose programming language"); Precalculus; Calculus; Probability and Statistics; Filipino; term placement of every GE course; whether autonomous schools (Ateneo, UPLB) follow the CMO template at all.

---

## 5. Outstanding questions
*Resolved by the owner 2026-09-30:* (1) fresh Year root `BS Computer Science — 1st Year`, old collection preserved; (3) Precalculus kept as an optional preparatory standalone plan; (2) language-neutral concepts plus one reference language, **the language itself still open** (assessment recommends C++).

*Further resolved 2026-09-30 (final decision pass):* Filipino is not in the default reference journey (D-07); the three Subject shelves and the per-note GE applicability rule are approved (D-14, D-21); Workstream A is closed and `COMPUTING` shipped in v0.164.0.

Still open:
1. Approve the reference language (D-27, recommendation C++17 restricted subset) and how it reaches generation (D-35), plus the Programming note-count change (D-36): `bscs-year1-programming-language-assessment.md`.
2. Confirm CHED text against the official PDFs (OCR limits above).
3. Obtain the CHED syllabus outlines for the five GE courses whose outlines were not retrieved.
4. Cebu's Java and Python electives were read from its 2024–2025 PDF; Antique's page was summarised, not extracted, so its silence on a Year 1 language is weaker evidence.

## 6. Sources
- CMO 25 s.2015 (OCR): https://archive.org/download/ched-bsis-bsit-bscs/CHED%20-%20BSIS%20BSIT%20BSCS_text.pdf · official (unreachable): https://legacy.ched.gov.ph/wp-content/uploads/2017/10/CMO-no.-25-s.-2015.pdf
- CMO 20 s.2013 (official, unreachable): https://ched.gov.ph/wp-content/uploads/2017/10/CMO-No.20-s2013.pdf · secondary sources as listed in §1.
- University of Antique: https://www.antiquespride.edu.ph/ccis/
- AICS: https://aics.edu.ph/wp-content/uploads/2018/11/Approved_OBE_BSCS_Curriculum.pdf
- University of Cebu: https://enrollment.uc.edu.ph/college/PROS/ITCS/(BSCS)2024-2025.pdf
- Palawan State University: https://psu.palawan.edu.ph/wp-content/uploads/prospectus/cs/ComSci-for-student-new-version.pdf
- Ateneo de Manila: https://www.ateneo.edu/sites/default/files/2023-05/BS%20CS%20Program%20of%20Study.pdf
- UPLB (mirror): https://pdfcoffee.com/the-uplb-2018-bscs-curriculum-pdf-free.html
- UP Tacloban: https://www.uptacloban.edu.ph/bscs-curriculum/
- Not readable: LPU Batangas (403), VSU (scanned), QCU (scanned).
