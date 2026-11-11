Status: Phase A draft, 2026-11-06, for owner and Note Strategist review. NOT implementation-ready. No Note, Study Pack, metadata, collection or publication was created or changed. Production was read with SELECTs only. BSCS and BSIT decisions are untouched.

> **Update 2026-11-06 (owner decisions after the Note Strategist review).** Q1 to Q4 and Q8 approved as proposed; Q2 approved as restricted C++17; Q5 and Q7 conditionally approved; Q6 required; the 18 to 26 Note estimate is **not** approved. The follow-up is `bsis-year1-fundamentals-of-is-evidence-and-overlap-audit.md`, which also resolves C7 below (IS101's prerequisite is **CC101**, read from the CHED-hosted PDF) and supersedes this report's estimate of the new plan's size. The evidence counts in section 3 have grown to six semester-based curricula (PCU added); see section 10 of the follow-up. This report is otherwise unchanged and still NOT implementation-ready.

# BS Information Systems (BSIS), 1st Year: Phase A Research and Canonical Reuse Audit

Labels: **FACT** (read in a source or in production), **INFERENCE** (my reading of facts), **PROPOSAL** (recommended NoteLib decision, owner's to approve).

Working files in this folder: `cmo25-bsis-ocr-excerpt.txt` (OCR text, rough) and `evidence/` (four page images of the official CMO 25 PDF that settle the points the OCR could not).

---

## 1. Headline

1. **BSIS Year 1 is the BSCS/BSIT computing core plus exactly one BSIS-specific subject: Fundamentals of Information Systems.** (FACT from CHED's own sample program of study, section 2; INFERENCE that this is the whole difference.) CHED's BSIS sample puts only four IS courses in Year 1: Introduction to Computing and Computer Programming 1 in the first semester, Computer Programming 2 and Fundamentals of Information Systems in the second. Everything else that makes BSIS look like BSIS (Organization and Management Concepts, IT Infrastructure, Professional Issues in IS, Systems Analysis and Design, Business Process Management, Financial Management) starts in the sophomore year or later.
2. **BSIS is not BSIT with a different title.** CHED's required BSIS list has no Discrete Mathematics (BSIT has MS101 and CHED's BSIT sample puts it in the freshman year). It has Quantitative Methods instead, which is junior-year. The Discrete Mathematics plan built for BSIT should **not** be the default swap-in for BSIS.
3. **Reuse is high, but the one BSIS-specific subject has no canonical content at all.** 176 of 182 shared placements already carry Information Systems in Applicable Programs. Six do not (named in section 6). No production Note exists for Fundamentals of Information Systems, so this plan needs genuinely new Notes. Section 7 sizes it; it does not author it.
4. **Where the BSIS schools disagree with the BSCS/BSIT term placements, I have said so rather than copy.** Understanding the Self is first semester at 4 of 5 BSIS schools read (BSCS and BSIT put it in the second). That is an owner decision because terms freeze when a plan is published.
5. **One incidental finding for BSIT, not acted on:** the CHED BSIT sample program of study is readable in the page image (the BSIT audit called it illegible) and puts **Introduction to Human Computer Interaction in the freshman second semester**. See section 11.

---

## 2. CHED evidence (CMO 25 s.2015)

Source: the official-format PDF mirrored on archive.org (57 pages), the same copy used for the BSCS and BSIT audits. **Its OCR text layer garbles tables, so the key pages were rendered and read as images** (saved in `evidence/`). The official `ched.gov.ph` copy was not retrieved.

| # | Claim | Label | Basis and limit |
|---|---|---|---|
| C1 | CMO 25 sets one standard for BSCS, BSIS and BSIT: 146 units minimum (BSIS: GE 54, Common 18, Professional 48, Electives 12, PE 8, NSTP 6; Additional Math 0) | FACT | OCR, section 8.1 |
| C2 | Six courses are **common** to all three: Introduction to Computing, Computer Programming 1, Computer Programming 2, Data Structures and Algorithms, Information Management, Applications Development and Emerging Technologies | FACT | OCR |
| C3 | **BSIS professional courses (sample curriculum):** Fundamentals of Information Systems (IS101), Professional Issues in IS (IS102), IT Infrastructure and Network Technologies (IS103), Systems Analysis and Design (IS104), Enterprise Architecture (IS105), IS Project Management 1 (IS106), IS Strategy, Management and Acquisition (IS107); **business domain courses:** Organization and Management Concepts (DM101), Financial Management (DM102), Business Process Management (DM103), Evaluation of Business Performance (DM104); Quantitative Methods; Capstone 1 and 2; Practicum. **No Discrete Mathematics.** | FACT | OCR of the BSIS list, cross-checked against the program-of-study image |
| C4 | **BSIS sample program of study, freshman year.** 1st semester: Introduction to Computing, Computer Programming 1 (6 IS units, 12 GE, 2 PE, 1.5 NSTP). 2nd semester: Computer Programming 2, **Fundamentals of Information Systems** (6 IS units, 12 GE, 2 PE, 1.5 NSTP). Sophomore 1st: Data Structures and Algorithms, Professional Issues in IS, IT Infrastructure, Organization and Management Concepts (12 IS units). Sophomore 2nd: Systems Analysis and Design, Financial Management. Junior: Information Management, Enterprise Architecture, Business Process Management, Quantitative Methods, IS Project Management 1 | FACT | **Read from the page image** (`evidence/cmo25-page19-…`, `…page20-…`); the IS-unit subtotals reconcile with the course boxes I read in each column. The IS-unit subtotals of every column (6, 6, 12, 9, 15, 12, 12, 6 = 78) reconcile with the boxes I assigned to them |
| C5 | CHED allots **12 GE units in each freshman semester**, i.e. four GE courses a term | FACT | Same image |
| C6 | Prerequisites in the CHED curriculum map: Professional Issues in IS and IT Infrastructure require CC101; Organization and Management Concepts requires CC101; Financial Management requires DM101; Business Process Management requires DM102; Systems Analysis and Design requires second-year standing; Quantitative Methods requires third-year standing; IS Strategy, Management and Acquisition requires fourth-year standing | FACT | Professional Issues in IS and IT Infrastructure (CC101) are read from the map-page image (`evidence/cmo25-page26-…`); the Organization and Management, Financial Management, Business Process Management, Systems Analysis and Design, Quantitative Methods and IS Strategy prerequisites are **OCR-only** and rougher |
| C7 | **Fundamentals of IS prerequisite reads `CC10?`**: the last glyph is degraded to a small raised mark (`evidence/cmo25-is101-prerequisite-cell-zoom.png`). It is not a full-height digit, so it is probably CC101, which would agree with the sample placing it after Introduction to Computing | INFERENCE (probable, not confirmed) | Whatever the digit is, **CC104 (Data Structures and Algorithms) would contradict CHED's own sample**, which places Fundamentals of IS in the freshman second semester before DSA, so "it is not DSA" is settled even though the digit is not. Do not state a specific prerequisite until the official PDF is read |
| C8 | The only sample learning outcome CHED prints for Fundamentals of IS: *compare and identify the major technologies and applications of information systems in driving development and changes in enterprises* | FACT | Map page. It is one outcome; it does not define a syllabus |
| C9 | CHED names the BSIS nature of study as the application and effect of information technology on organizations, with graduates implementing an information system that weighs technological and organizational factors. Primary job roles: organizational process analyst, data analyst, solutions specialist, systems analyst, IS project management personnel | FACT | OCR, section 5.2.2 and the job-roles section |
| C10 | CHED fixes no programming language: Computer Programming 1 and 2 outcomes say "general purpose programming language" and "the assigned programming language" | FACT | OCR, course specifications |
| C11 | CMO 25 is still the BSIS standard: Gordon College's AY 2025-2026 BSIS curriculum cites it (BOR Res. 110-2024) | FACT | Gordon College page. No superseding CMO was searched for beyond that; treat "still current" as unverified for 2026 |
| C12 | GE: CMO 20 s.2013, same eight core courses as BSCS and BSIT | FACT | Unchanged from the earlier audits |
| C13 | CHED specifies no semester placement as a rule; its sample is a sample | INFERENCE | Consistent with BSCS and BSIT |

---

## 3. University evidence (Year 1)

**Full Year 1 reads, semester-based (n = 5):** Camarines Sur Polytechnic Colleges (CSPC), Isabela State University (ISU; BSIS with a BPO service-management track), Silliman University (curriculum year 2018-2019), University of the East (UE, CMO-based 2019 table, "Copyright 2021"), Nova Schola Tanauan (NST). **Trimester, kept apart:** De La Salle-College of Saint Benilde (curriculum AY 2023-2024). **Partial:** Gordon College (course list with no year or semester), CIIT (subject headings per year, no terms), Central Philippines University (states CMO 25, effective 2018-2019, no course list), CHMSU (links to PDFs not read).

**Unreadable, not used:** CEFI, CPAC and UNO-R (HTTP 404); JRMSU, USJR, NVSU and the fliphtml5 copy of CMO 25 (HTTP 403, not bypassed); Studocu (paywalled image); Scribd "Revised Curriculum for BSCS, BSIS, BSIT" (no text). A JHCSC search hit turned out to be an Industrial Security curriculum and was discarded. Search-result snippets (for example QCU) were never fetched and are not evidence. **Silliman's term assignment was derived from block coordinates in the PDF, not from printed labels; treat it as layout-derived.** Five semester-based curricula is a thin sample: a consensus of those five, not of the country.

Term codes: `1.1` = Year 1 first semester, `2.2` = Year 2 second semester, `T3` = Benilde term 3, `—` = not seen in Years 1 to 2 of that school's curriculum.

### 3.1 Computing and BSIS courses

| Course | CHED sample | CSPC | ISU | Silliman | UE | NST | Benilde | Reading |
|---|---|---|---|---|---|---|---|---|
| Introduction to Computing | Fr 1st | 1.1 | 1.1 | 1.1 | 1.1 | 1.1 | T1 | 5/5 first semester |
| Computer Programming 1 | Fr 1st | 1.1 | 1.1 | 1.1 | 1.1 | 1.1 | T1 | 5/5 first semester |
| Computer Programming 2 | Fr 2nd | 1.2 | 1.2 | 1.2 | 1.2 | 1.2 | T2 | 5/5 second semester |
| **Fundamentals of IS** | **Fr 2nd** | 1.2 | 1.2 | 1.2 | 1.1 | 1.2 | T2 | **5/5 Year 1; 4/5 second semester** |
| Organization and Management Concepts | Soph 1st | 1.1 | 2.1 | 2.2 | — | 2.2 | T1 | **1/5** Year 1 |
| Business Process Management | Junior | 1.2 | — | 2.2 | 2.1 | 2.2 | T3 | 1/5 Year 1 |
| Professional Issues in IS | Soph 1st | 1.2 | 2.1 | Y4 | (1.2, titled *Social and Professional Issues in Computing*) | — | T3 | 1/5, plus one differently titled |
| IT Infrastructure and Network Technologies | Soph 1st | 2.1 | 2.1 | 2.1 | — | 2.1 | T3 | 0/5 |
| Data Structures and Algorithms (CHED common) | Soph 1st | 2.1 | 2.1 | 2.1 | 1.2 | 1.2 | T3 | 2/5 |
| Financial Management | Soph 2nd | 2.2 | 2.2 | — | — | — | T2 | 0/5 |
| Information Management (CHED common) | Junior | 2.1 | — | 2.2 | 2.2 | 1.2 | T5 | 1/5 |
| Discrete Mathematics | **not in BSIS list** | 1.2 | — | — | 2.1 | — | — | 1/5; CHED does not require it |
| Web development | not listed | — | — | — | — | — | T4 | 0/5 |

### 3.2 General Education courses (the six NoteLib GE plans)

| Course | CSPC | ISU | Silliman | UE | NST | Benilde | Majority? |
|---|---|---|---|---|---|---|---|
| Mathematics in the Modern World | 1.1 | 1.1 | 1.1 | 1.2 | 1.1 | T2 | **First, 4/5** |
| Understanding the Self | 1.1 | 1.1 | 1.2 | 1.1 | 1.1 | T1 | **First, 4/5** (BSCS and BSIT placed it second) |
| Readings in Philippine History | 1.1 | 1.2 | 2.1 | 1.2 | 1.2 | T4 | **Second, 3/5** |
| Purposive Communication | 1.2 | 1.1 | 2.1 | 2.2 | 1.2 | T1 | None (2 in 1.2, 1 in 1.1, 2 in Year 2) |
| The Contemporary World | 1.2 | 1.2 | 2.2 | 1.1 | 2.1 | T5 | None (2 in 1.2, 1 in 1.1, 2 in Year 2) |
| Art Appreciation | — (not named in Year 1) | 2.2 | 1.1 | 1.2 | 2.1 | T4 | None (1 in 1.1, 1 in 1.2, 2 in Year 2) |

Science, Technology and Society, Ethics, Life and Works of Rizal, Filipino courses and electives are institution-specific or later-year at these schools and are out of scope, as in BSCS and BSIT.

### 3.3 What this says

- **Consistent (FACT/INFERENCE):** Introduction to Computing, CP1 (first), CP2 and Fundamentals of IS (second) agree between CHED's sample and every semester-based school except UE's FIS.
- **BSIS differs from BSCS and BSIT on the term of Understanding the Self.** This is the rule applied afresh (three of five or better), not copied.
- **No majority exists** for Purposive Communication, The Contemporary World or Art Appreciation. Any placement is a stated choice, not evidence.
- **The "business" courses are not Year 1 by majority or by CHED.** CSPC and Benilde are the two schools that pull Organization and Management Concepts (and, at CSPC, Business Process Management) into Year 1. That is institution-specific.
- **The programming language is not named by any school.** Silliman calls them *Fundamentals of Programming* and *Intermediate Programming*; the others use the CHED titles. Benilde's BSIS stresses web and mobile application development and has an *Enterprise Programming* course in term 3, which says nothing about the CP1 language.

---

## 4. Where BSIS legitimately differs from BSCS and BSIT

| Dimension | BSCS | BSIT | BSIS |
|---|---|---|---|
| Orientation (CHED 5.2) | Algorithms, theory, software design | Planning, integrating and administering infrastructure | **Application and effect of IT on organizations**: information systems, business processes, management |
| Year 1 mathematics | Discrete Structures 1 | Discrete Mathematics (MS101) | **None required**; Quantitative Methods in the junior year; GE mathematics only |
| Year 1 professional (non-common) | Discrete Structures 1 | Discrete Mathematics, Intro to HCI (CHED sample) | **Fundamentals of Information Systems** |
| Business domain courses | none | none | Organization and Management, Financial Management, Business Process Management, Evaluation of Business Performance (all sophomore or later) |
| GE units in freshman year | 18 (CHED sample) | 21 | **24** (12 per semester) |

**Honest reading (INFERENCE):** in Year 1 the genuine differences are (a) Fundamentals of IS replaces Discrete Mathematics and (b) the heavier GE load. Business-flavoured content is a Year 2 and Year 3 story. I have not forced it into Year 1.

---

## 5. Proposed BSIS Year 1 reference (PROPOSAL)

Ten Subject Plans, five a semester. Same term rule as before (three of five or better where a majority exists; otherwise the BSCS/BSIT placement, stated as a choice). **One stated exception:** The Contemporary World has no majority and BSCS and BSIT put it first, but it goes second here. The reason is load, not evidence: once Understanding the Self moves to the first semester, keeping The Contemporary World first would make a 4-and-2 GE split (Mathematics in the Modern World, Understanding the Self, Art Appreciation and The Contemporary World first; Readings in Philippine History and Purposive Communication second), while CHED's sample allots the same GE load to each freshman semester, so three and three among these six is the closer fit. Mirroring BSCS/BSIT is the alternative (Q3).

| Term | Subject Plan | Why here | CHED / common / institution | Content |
|---|---|---|---|---|
| First | Introduction to Computing | 5/5; CHED sample freshman 1st | CHED common | Reuse BSCS/BSIT plan (39 Notes) |
| First | Computer Programming 1 | 5/5; CHED sample | CHED common | Reuse (42 Notes), language decision Q2 |
| First | Mathematics in the Modern World | 4/5 first | CHED GE | Reuse (21) |
| First | **Understanding the Self** | **4/5 first**; BSCS/BSIT placed it second | CHED GE | Reuse (8). **Differs from BSCS/BSIT: Q3** |
| First | Art Appreciation | No majority; BSCS/BSIT placement kept; balances the load | CHED GE, term institution-dependent | Reuse (9) |
| Second | Computer Programming 2 | 5/5; CHED sample | CHED common | Reuse (31), language decision Q2 |
| Second | **Fundamentals of Information Systems** | 5/5 Year 1, 4/5 second; **CHED sample freshman 2nd** | **CHED BSIS professional (IS101)** | **NEW: no canonical content exists** |
| Second | Readings in Philippine History | 3/5 second | CHED GE | Reuse (10) |
| Second | Purposive Communication | No majority; BSCS/BSIT placement kept | CHED GE | Reuse (12) |
| Second | The Contemporary World | No majority (2/5 second vs 1/5 first); **placed second for GE load balance, see the exception above** | CHED GE | Reuse (10). **Differs from BSCS/BSIT: Q3** |

**Considered and left out of Year 1 (each with its reason):**
- **Organization and Management Concepts:** CHED sample sophomore 1st; Year 1 at 1 of 5 schools (CSPC) plus Benilde's trimester term 1. Held for a BSIS Year 2 journey. Its prerequisite (CC101) would allow Year 1, so this is a placement judgement, not a rule.
- **Business Process Management, Financial Management, Evaluation of Business Performance, Professional Issues in IS, IT Infrastructure, Systems Analysis and Design, Enterprise Architecture:** CHED sample sophomore or later, school majorities agree. Year 2 and beyond.
- **Data Structures and Algorithms:** CHED sample sophomore 1st; Year 1 at 2 of 5. The existing CP2 plan already carries linked structures and searching and sorting (see section 6). Year 2.
- **Discrete Mathematics:** not in CHED's BSIS list; Year 1 at 1 of 5 (CSPC). **Not the default.** Owner option, Q4.
- **Information Management, Web fundamentals, Quantitative Methods, Accounting:** junior-year or institution-specific. Not Year 1.
- **PE, NSTP, Filipino, Rizal, STS, Ethics, GE electives:** excluded as in BSCS and BSIT (activity or service based, or institution-specific).

---

## 6. Production state and canonical reuse audit (SELECTs, 2026-11-06)

All reads were `SELECT`s on `notelib_db_prod` through the Render MCP. **Searches matched Note titles and subjects, not Note bodies**, so "no Note exists" below means no Note *titled or subjected* for it, not that no body mentions it.

### 6.1 What is already tagged for Information Systems

| Finding | Detail |
|---|---|
| 231 Notes carry Information Systems in Applicable Programs, **all** of them also tagged Computer Science or Information Technology | They are the shared computing and GE Notes, so the tag already exists on most of what BSIS needs |
| Per shared plan in the BSIT Year (placements / carrying Information Systems) | Introduction to Computing 39 / 38; Computer Programming 1 42 / 42; Computer Programming 2 31 / 31; Mathematics in the Modern World 21 / 21; Understanding the Self 8 / 8; Readings in Philippine History 10 / 8; Purposive Communication 12 / 12; The Contemporary World 10 / 9; Art Appreciation 9 / 7. **182 placements, 176 tagged** |
| **The 6 that lack it** | *Emerging Technologies and Their Social Impacts* (Introduction to Computing); *Spanish Colonization and Transformation of Philippine Society* and *Philippine Revolution and the First Philippine Republic* (Readings in Philippine History); *Global Citizenship and Global Civil Society* (The Contemporary World); *Film and Moving-Image Art* and *Art, Popular Culture and Media in Contemporary Society* (Art Appreciation) |
| Discrete Mathematics plan | 0 of 32 carry Information Systems. Relevant only if Q4 is answered yes |
| All 182 are PUBLIC; the plans' Domain Context is set per Note, not per plan | Domain Context and depth were checked in the BSIT work and are not re-read here |

### 6.2 Reuse tiers for the default Year (182 placements of existing Notes)

| Tier | Notes | Basis |
|---|---|---|
| **Confirmed Reuse (metadata-ready, language-neutral)** | **134** | 176 tagged minus the 42 C++-named Notes. Content inspection remains a curator step |
| **Candidate: language decision** | 42 | The C++-named Notes in CP1 (22) and CP2 (20). Confirmed only if the reference language stays C++17 (Q2) |
| **Candidate: add Information Systems** | 6 | The six above. A metadata handoff `.sql` that the owner runs (Q7). Applicable Programs is discovery only and never reaches a prompt (ADR-001) |
| **New** | the Fundamentals of IS plan | Section 7 |
| **Excluded** | 5 | The same foundation-math and teacher-education Notes BSCS and BSIT held out |

**The BSIT verification tiers carry over unchanged and are not re-derived here, and most of these Notes are metadata-ready only**: in the BSIT work a handful were content-verified, 29 Discrete Mathematics Notes were reviewed, and the rest (bodies not re-inspected) were metadata-ready. Reading the other 182 for BSIS is a curator step.

**Existing Notes requiring further content review for BSIS (PROPOSAL):** read the bodies of **The Computing Disciplines: Computer Science, Information Technology, Information Systems, Software Engineering and Computer Engineering** and **Analyzing Organizational Computing Solutions** before the Fundamentals of Information Systems plan is scoped, because what they already teach decides what the new plan must not repeat. Also read **Emerging Technologies and Their Social Impacts** and **Cloud Computing, the Internet of Things and Big Data**, the other two Introduction to Computing Notes on the same boundary. The six untagged Notes (6.1) are inspected when the tag change is prepared.

### 6.3 Is anything for the one BSIS-specific subject already there?

- **Fundamentals of Information Systems, Organization and Management, Business Process Management, Systems Analysis and Design, enterprise systems, ERP, decision support:** **no Note exists** by title or subject. The only Note that touches Information Systems as a discipline is *The Computing Disciplines: Computer Science, Information Technology, Information Systems, Software Engineering and Computer Engineering*, already in the Introduction to Computing plan.
- **The business-domain material that does exist is not reusable unchanged:** Fundamentals of Accounting, Financial Management, Management Advisory Services and Managerial Decision Making are `ACCOUNTANCY`, unset Domain Context, or `BOARD_EXAM_REVIEW` depth (CPA and other review sets); Project Management is `ENGINEERING_SCIENCES`. All of it is Year 2 or later for BSIS anyway, so nothing is pulled in.
- **Duplicate and overlap risk for the new plan (FACT, from the Introduction to Computing plan):** *The Computing Disciplines…*, *The Computing Industry, Professions and Careers*, **Analyzing Organizational Computing Solutions**, *System Software and Application Software*, *Cloud Computing, the Internet of Things and Big Data*, *Emerging Technologies and Their Social Impacts*, the security Notes, and *Ethical, Legal and Social Issues in Computing*. A Fundamentals of IS plan must not re-teach these. Mathematics in the Modern World already has *Linear Programming* and *Financial Mathematics in Everyday Life*, which any later Quantitative Methods or Financial Management plan must not duplicate.
- **Legacy tagged Notes:** Computer Networking (17) and Database Systems (17) are `Information Technology`-tagged with no Domain Context or depth and no Information Systems tag. They are Year 2 material (IT Infrastructure and Information Management); not touched. The COMPUTING Notes that do carry the Information Systems tag under Computer Networking (3), Information Assurance and Security (5), Web Development (4) and Data Structures and Algorithms (6) were located: **all 18 already sit in the Introduction to Computing or Computer Programming 2 plans**, so they are part of the 182, not extra material.

---

## 7. New-Note requirements (sizing only; nothing authored)

**One plan: Fundamentals of Information Systems.** (PROPOSAL; the content scope is **not** yet evidence-backed, see below.)

- **Size (PROPOSAL):** about 18 to 26 Notes in four to six sections, for comparison Introduction to Computing holds 39 and Art Appreciation 9.
- **Candidate topic areas to evidence in Phase A2, not yet a Note list:** what an information system is (data, information, knowledge; components); information systems in organizations and their strategic value; the main kinds of information systems; information systems across business functions; the technologies changing enterprises (CHED's one printed outcome), without re-teaching the Introduction to Computing cloud, IoT and emerging-technology Notes; how organizations acquire and develop systems at overview level only, leaving Systems Analysis and Design for Year 2.
- **The evidence gap (FACT):** I read **no Fundamentals of IS course description or syllabus**, only course titles and CHED's single outcome. One candidate exists (an Isabela State course guide on Scribd, found in search, not opened). **Authoring scope should wait for a short evidence pass over two or three FIS course descriptions (PROPOSAL, Q6).**
- **Taxonomy for these Notes (binding rules, FACT):**
  - **Domain Context:** ADR-001 states that Information Systems business-process treatment "stays on its own program fallback, not `Computing`", and that plan or program membership never determines Domain Context, which is decided **per Note**. So the expected default is an **unset Domain Context** relying on the program fallback, confirmed Note by Note in Phase B; a Note that is really shared computing knowledge would be `COMPUTING` and multi-program instead.
  - That is legal only with **exactly one** Applicable Program, so **Information Systems alone** (the hard rule from `docs/curriculum/`). If any other program is ever added for reuse, every affected Note then needs an explicit Domain Context. No Domain Context value for IS treatment exists; creating one would be an ADR amendment, which I do not propose.
  - **Authored Depth:** `COLLEGE`.
  - **Subject:** no existing Subject value fits (*Computer Science* is too broad and carries COMPUTING treatment). A new value is needed, **and ADR-001's guard flags a Subject equal to a program name as too broad, so not "Information Systems"**. Candidate: `Fundamentals of Information Systems` (the course title, as `Discrete Mathematics` and `Programming Fundamentals` are). New Subject values broke the earlier TSV validation rule, so this is an owner decision (Q5).
- **Learning Goal / Preparation Goal:** the owner's prompt names these as conventions. In the repo they are a **parked workstream with no storage and no product field** (`study-journey-purpose-learner-goal-discovery-brief.md`, the BSCS readiness note). I have therefore proposed nothing for them.
- **Any new Notes beyond this plan: none proposed.** Year 2 business plans are a later phase.

---

## 8. Programming language

CHED is language-agnostic (C10) and none of the readable BSIS checklists names one, which is the same gap as BSIT. The BSCS and BSIT journeys use a restricted C++17 reference (BSIT decision Q1); I have not changed that.

| Option | Reuse | New authoring |
|---|---|---|
| **A. C++17 restricted, as BSCS and BSIT** | all 73 programming Notes | 0 |
| **B. Neutral only** | 31 language-neutral Notes | the 42 language-specific Notes do not exist |
| **C. Another language** | 31 neutral | about 42 new Notes plus drift risk |

(INFERENCE) BSIS programs lean toward business and web and mobile applications (Benilde), which may favour a different teaching language in practice, but **no evidence I could read says so**, and I do not infer a language from a program's orientation. **Q2.**

---

## 9. Uncertainties and owner decisions

| # | Question | Recommendation |
|---|---|---|
| **Q1** | Scope: ten plans, nine shared plus a new Fundamentals of Information Systems, with Organization and Management, Business Process Management, Data Structures and Algorithms and the other business or infrastructure courses held for Year 2? | **Yes.** CHED's own sample and the school majorities agree they are sophomore or later |
| **Q2** | Reference language: A (C++17 restricted), B (neutral only) or C (other)? | **A**, because it costs nothing and the evidence cannot distinguish BSIS from BSCS. Your call |
| **Q3** | Terms where BSIS evidence differs from BSCS/BSIT: Understanding the Self first semester (4/5) and The Contemporary World second (2/5, no majority). Follow the BSIS evidence (5 plus 5, as in section 5) or mirror BSCS/BSIT (Understanding the Self second, Contemporary World first, also 5 plus 5)? Terms freeze at publication | **Understanding the Self first** (a real majority). **Contemporary World: no evidence either way; mirroring BSCS/BSIT is equally defensible**, so this is a pure preference |
| **Q4** | Discrete Mathematics: leave out of BSIS Year 1 (CHED's BSIS list has none), or include the BSIT plan as an option (32 Notes, none tagged Information Systems, plus a treatment review)? | **Leave out** |
| **Q5** | Taxonomy for the new plan. Expected default: business-treatment Notes carry an unset Domain Context with Information Systems as the only program; **Domain Context is decided per Note in Phase B, never by plan**, so any Note judged shared computing knowledge would instead be `COMPUTING` and multi-program. Also a new Subject value `Fundamentals of Information Systems` | **Yes**, per ADR-001 |
| **Q6** | May I run a short evidence pass over two or three Fundamentals of IS course descriptions before the Phase B plan scopes the Notes? | **Yes.** I should not scope a plan from a course title |
| **Q7** | May I plan a metadata handoff `.sql` adding Information Systems to the 6 Notes in section 6.1 (you run it, dry run then `COMMIT`)? | **Yes** |
| **Q8** | Separate BSIS root, as with BSIT? Title following the convention, for example "BS Information Systems — 1st Year" (emoji your choice) | **Yes** |
| **Q9** | BSIT follow-up, section 11 | Informational |

**Unverified or limited:**
- Five semester curricula, one trimester, several unreadable (section 3).
- CHED's Fundamentals of IS prerequisite is not confirmed (C7).
- Silliman's terms are layout-derived.
- CMO 25 being current in 2026 rests on one college page (C11).
- No Fundamentals of IS course description was read (section 7).
- Note bodies were not searched (section 6).
- Content, depth and Study Pack state of the 182 reused Notes were **not** re-read for BSIS; the BSIT tiers (mostly metadata-ready, bodies not re-inspected) carry over (section 6.2).

---

## 10. Recommended target shape for approval (PROPOSAL)

**Root:** BS Information Systems, 1st Year (separate root, `course_program` Information Systems, learner level College).

| First Semester | Second Semester |
|---|---|
| Introduction to Computing (reuse) | Computer Programming 2 (reuse) |
| Computer Programming 1 (reuse) | **Fundamentals of Information Systems (new)** |
| Mathematics in the Modern World (reuse) | Readings in Philippine History (reuse) |
| Understanding the Self (reuse) | Purposive Communication (reuse) |
| Art Appreciation (reuse) | The Contemporary World (reuse) |

**Counts (PROPOSAL):** 10 plans; 182 reused placements of existing Notes (134 confirmed, 42 pending the language decision, 6 pending the tag change); one new plan of about 18 to 26 Notes. **Reuse by placement: about 88% to 91%** (182 of 200 to 208, depending on the new plan's final size).

**Not included:** every other BSIS professional or business course (all Year 2 or later), Discrete Mathematics, PE, NSTP and the institution-specific GE courses.

**Recommended next step after your answers:** the Fundamentals of IS evidence pass (Q6), then the BSIS Phase B files in the BSCS/BSIT pattern (shaping TSV with `applicable_programs` on every row, generated workbook, journey, decision log, handoff), then a plan to author the new Notes.

---

## 11. Incidental finding about BSIT (not acted on)

The BSIT audit recorded the CHED BSIT sample program of study as "mostly illegible (totals only)". The page image is legible at 130 dpi (the OCR text is what is garbled). It shows, for BSIT (CMO 25 page 21): freshman 1st **Introduction to Computing, Computer Programming 1**; freshman 2nd **Introduction to Human Computer Interaction, Discrete Mathematics, Computer Programming 2**; sophomore 1st Data Structures and Algorithms, Object-Oriented Programming. So CHED's own BSIT sample:
- **supports** the BSIT Discrete Mathematics decision (Year 1), and
- **places Introduction to HCI in Year 1**, which the school read (1 of 5) and the BSIT plan (no HCI plan) did not weigh.

I have not changed any BSIT document. Whether to revisit BSIT is the owner's and the Release Manager's call. A short BSIT note stating "the sample chart is legible in the image" would stop the earlier "illegible" line from being repeated.

---

## 12. Sources

- CHED CMO 25 s.2015 (archive.org mirror, page images and OCR): https://archive.org/download/ched-bsis-bsit-bscs/CHED%20-%20BSIS%20BSIT%20BSCS_text.pdf
- CSPC BSIS: https://ccs.cspc.edu.ph/information-systems/
- ISU BSIS (BPO track): https://isu.edu.ph/bs-in-information-systems/
- Silliman BSIS (curriculum year 2018-2019): https://su.edu.ph/wp-content/uploads/2026/06/BSIS-updated.pdf
- UE BSIS: https://apps.ue.edu.ph/p/curriculum.php?c=CBSIS2019
- Nova Schola Tanauan BSIS: http://nst.edu.ph/academics/college-programs/bs-information-systems/
- De La Salle-College of Saint Benilde BSIS (trimester): https://benilde.edu.ph/wp-content/uploads/2025/04/BS-IS-Program-Specification-SMIT-ACI-ISP.pdf
- Gordon College BSIS (AY 2025-2026, cites CMO 25; no terms): https://cst.gsu.edu.ph/bsis-curriculum-effective-1st-sem-ay-2025-2026/
- CIIT BSIS (subject headings, trimester): https://www.ciit.edu.ph/course/college-information-systems/
- Central Philippines University BSIS (states CMO 25, effective 2018-2019, no course list): https://ccs.cpu.edu.ph/information-systems/
- Repo: `docs/architecture/ADR-001-canonical-knowledge-architecture.md`; `docs/claude-plans/bsit-year1/bsit-year1-phase-a-research-and-reuse-audit.md`; `docs/claude-plans/study-journey-purpose-learner-goal-discovery-brief.md`.
