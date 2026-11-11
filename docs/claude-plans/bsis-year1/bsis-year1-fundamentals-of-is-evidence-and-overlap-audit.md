Status: research gate before Phase B, 2026-11-06, for owner and Note Strategist review. NOT implementation-ready. No Note, Study Pack, metadata, collection or publication was created or changed; production was read with SELECTs only. Waiting for explicit owner approval before Phase B.

> **Update 2026-11-06 (later):** the 13-row inventory and the T1/T2 tiers in this file are **superseded** by `bsis-year1-fis-final-shaping-and-evidence.md` (12 Notes, S1 to S3 evidence from IS2020, N10 merged, the RA 10364 HOLD). This file remains the record of the first evidence pass.

# BSIS Year 1: Fundamentals of Information Systems, curriculum and canonical overlap audit

Labels: **FACT** (read in a source or in production), **INFERENCE** (my reading), **PROPOSAL** (recommended, the owner's to approve).

Follows `bsis-year1-phase-a-research-and-reuse-audit.md` and the owner's 2026-11-06 decisions (section 0).

---

## 0. Owner decisions recorded (2026-11-06)

| # | Decision | State |
|---|---|---|
| Q1 | Ten Subject Plans, five a semester (First: Introduction to Computing, Computer Programming 1, Mathematics in the Modern World, Understanding the Self, Art Appreciation. Second: Computer Programming 2, Fundamentals of Information Systems, Readings in Philippine History, Purposive Communication, The Contemporary World). Organization and Management Concepts, Business Process Management, Professional Issues in IS, IT Infrastructure, Systems Analysis and Design, Data Structures and Algorithms, Information Management and other advanced courses stay outside the default Year 1 | Approved |
| Q2 | Restricted C++17, a NoteLib authoring decision, not CHED-mandated; reuse existing programming Notes where canonical equivalence is established | Approved |
| Q3 | Understanding the Self first semester, The Contemporary World second semester; five plus five | Approved |
| Q4 | Discrete Mathematics excluded from the default BSIS Year 1, recorded as a curriculum decision | Approved |
| Q5 | Subject `Fundamentals of Information Systems` (not the program name), subject to checking the Subject taxonomy conventions; **Domain Context per Note** under ADR-001; no new Domain Context | Conditional; checked in section 5 |
| Q6 | This evidence pass | In this document |
| Q7 | Metadata handoff for the six untagged Notes, after body and Study Pack review | Conditional; reviewed in section 7 |
| Q8 | Separate root "BS Information Systems — 1st Year" | Approved |
| n/a | The 18 to 26 Note estimate is not approved; the inventory is to follow verified outcomes and overlap | Applied in section 8 |

---

## 1. Curriculum evidence table

Confidence: **High** = official CHED text read directly; **Medium** = an official school page or a school course guide with the content stated; **Low** = a summary written by a third party (here, Scribd's own "AI-enhanced" blurbs, truncated) or my inference; **Not usable** = read, but not evidence for this course.

| # | Source | What it says about Fundamentals of IS | Label | Confidence | Limit |
|---|---|---|---|---|---|
| E1 | CHED CMO 25 s.2015, **CHED-hosted PDF** (`legacy.ched.gov.ph/.../CMO-no.-25-s.-2015.pdf`), curriculum map, page 26 (`evidence/ched-hosted-cmo25-page26-is101-prereq-cc101.png`) | IS101 Fundamentals of IS, 3 units, **prerequisite CC101**, one learning outcome: *Compare and identify the major technologies and applications of information systems in driving development and changes in enterprises* | FACT | **High** | Scanned image PDF with no text layer; I rendered and read the page myself. **This verifies the strategist's CC101 point directly from the CHED-hosted copy**, and replaces the earlier degraded reading (`CC10?`) from the archive.org mirror |
| E2 | Same PDF and the archive.org copy, BSIS sample program of study (pages 19 and 20) | Fundamentals of IS is a freshman 2nd-semester course (6 IS units with Computer Programming 2); every other BSIS-specific course is sophomore or later | FACT | High | A sample, not a mandate |
| E3 | Same map, CC101 Introduction to Computing | Its outcomes include *analyze solutions employed by organizations to address different computing issues* (LO3). That is CHED's Introduction to Computing outcome, not a Fundamentals of IS outcome | FACT | High | Read from the BSIT-chart page image in the earlier pass and the CC101 course text |
| E4 | CIIT BSIS program page, "Fundamentals of Information Systems" under 1st Year | Verbatim: *"provides context on Information Systems practice and use viewed through a range of roles that interact with various entities within and beyond an organization, including those of system developers, users, business managers, IT managers, and vendors. It provides students with a foundation that is further built on in other information systems courses. The course allows students to understand the complexity of real-world applications of information systems within a range of industries. It challenges students to integrate concepts, theories and frameworks with case studies and examples drawn from industry."* | FACT | **Medium** | Course description only; no outline, outcomes, units or textbook. Trimester school |
| E5 | Isabela State University IS 121 course guide (Scribd "IS 121 Course Guide: Info Systems", 2020-2021) | Six topics over 14 weeks: Fundamentals of Information Systems; Information Technologies; Information and its Role in Business; Types of Information Systems; Database Information System and Design; Information System Engineering | FACT that the blurb says so; INFERENCE that it reflects the guide | **Low** | The blurb is Scribd's AI-written summary and the document body was truncated, so topic labels only; no content under any label |
| E6 | University of Cebu IT Course Guide 2019-2020 (Scribd) | Fundamentals of IS introduces *the basic concepts of information systems and their role in business*; assessed by exams, group activities, case studies and a research paper | FACT that the blurb says so | **Low** | AI-written blurb, truncated; no outline |
| E7 | Placement evidence: CSPC, ISU, Silliman, NST (Year 1 second semester), UE (first semester), PCU INFS1203 (first year, second semester), Benilde term 2, Malayan Colleges Mindanao IS100 (Year 1, term not resolved), CIIT (Year 1) | Fundamentals of IS is a Year 1 course at every curriculum read | FACT | Medium to High | New this pass: PCU and Malayan Colleges Mindanao (CMO 25-based, SY 2020-21). Silliman's terms are layout-derived |
| E8 | **Northwest Samar State University** "BSIS 1B - IS 101 Fundamentals of Information System" (the page the strategist suggested) | Its description reads: *"introduces students to the fundamental concepts, principles, and evolution of computing systems and their applications across various fields. It examines recent developments in computing knowledge areas, explores emerging technologies, and analyzes practical solutions implemented by organizations to address computing-related issues..."* This is a close paraphrase of CHED's **Introduction to Computing** outcomes (CC101 LO1 to LO3), not an information systems syllabus | FACT (the text); INFERENCE (it is a mislabelled or differently numbered course) | **Not usable** as Fundamentals of IS evidence | Useful only as a reminder of how easily the two courses blur, which is exactly the overlap risk this audit exists to manage |
| E9 | ACM/AIS IS 2010 curriculum (core course *Foundations of Information Systems*) and the 2015 CAIS review of that course | **Not read.** acm.org and core.ac.uk returned HTTP 403 and I did not work around it | n/a | n/a | **Evidence limitation.** The owner can download the IS 2010 PDF and hand it over, or authorise another route |
| E10 | The strategist's nine "candidate areas" (socio-technical systems, information needs, categories, business functions, value and decision support, technology trends, acquisition and development) | Offered as candidates for verification, not requirements | PROPOSAL (strategist's) | **Low** until a source supports an area | Each area is tested against E1 to E6 in section 2 |
| E11 | Other readable BSIS curricula already tabulated in the Phase A report | Titles and placements only | FACT | High for placement | No content |

**Distinguishing the three kinds of claim (as asked):**
- **Official CHED requirement (FACT):** the course exists in the BSIS curriculum, 3 units, prerequisite CC101, freshman second semester in the sample, with one outcome (E1, E2).
- **Institution-specific coverage (FACT, Medium or Low):** what CIIT, ISU and UC say they teach (E4 to E6).
- **Inferred academic expectation (INFERENCE):** anything beyond those, including the strategist's candidate areas where no source speaks to them.

**What this evidence cannot do:** it cannot support a Note-by-Note inventory. **No source I could read gives an outline of what Fundamentals of IS teaches**, only a course description (CIIT), one CHED outcome, and a topic-label list from a truncated third-party blurb. The inventory in section 6 is therefore a **provisional, tiered** list, not a verified syllabus.

---

## 2. Fundamentals of IS coverage matrix

Tier **T1** = at least one official or school source (E1, E4) or a school topic label (E5, E6) names the area. Tier **T2** = the area comes from the strategist's candidate list or my inference, with no source for it yet. "Existing coverage" is from the Note bodies and Study Packs I read in full today (section 4).

| # | Area | Evidence | Tier | Existing canonical coverage | Gap status | Recommended treatment |
|---|---|---|---|---|---|---|
| A1 | Information systems as socio-technical systems: people, processes, data, technology | E4 ("roles that interact with various entities"), E5 topic 1, E6, E2 title | T1 | **One bullet** in *The Computing Disciplines…* ("aligns information, people, processes, and technology with organizational goals through requirements analysis, process modeling, and system selection") and one quiz item | Gap: definition and components are not taught as a topic | New |
| A2 | Data, information, knowledge; information quality and value | E5 topic 3 ("Information and its Role in Business") | T1 for the topic, T2 for quality criteria | None | Gap | New (one Note, quality and value folded in) |
| A3 | Stakeholder roles in IS practice: users, developers, business managers, IT managers, vendors | **E4 verbatim** | T1 | *The Computing Industry, Professions and Careers* covers professional roles (software, infrastructure, data; *systems analyst* in Quick Recall) from a **careers** view, not stakeholders around a system | Partial adjacency; the stakeholder framing is not taught | New, kept distinct from careers |
| A4 | Operational, managerial and strategic information needs; IS in organizations | Strategist (E10); E6 ("role of IS in business") loosely | T2 | None | Gap | New, conditional on a source |
| A5 | Major categories and applications of information systems | **E1** (outcome names "applications of information systems"), E5 topic 4 ("Types of Information Systems") | T1 | None (the *Cloud, IoT and Big Data* and *Software* Notes describe technologies, not organizational systems) | Gap | New: transaction processing, management information and reporting, decision support and business intelligence |
| A6 | Enterprise-wide systems (ERP, CRM, supply chain) | Strategist; CHED lists Enterprise Systems, ERP, CRM, SCM as **electives** (so beyond Year 1) | T2 | None | Gap, but CHED treats it as later-year | Conditional; at most one overview Note |
| A7 | Information systems across business functions (accounting, marketing, human resources, operations) | Strategist; E4 "range of industries" weakly | T2 | None | Gap | Conditional |
| A8 | Technology trends and business implications; technology-enabled change in enterprises | **E1 outcome** ("driving development and changes in enterprises") | T1 | Technology described in *Cloud Computing, the Internet of Things and Big Data* (service models, edge, the three Vs, shared responsibility), *Emerging Technologies and Their Social Impacts* (social effects), *Artificial Intelligence and Human Judgment* | **Partial**: the technologies are taught; their **organizational and business implications** are not | One New Note on the organizational side; reuse the three existing Notes as background |
| A9 | Information systems and organizational strategy / competitive advantage | Strategist; E4 weakly | T2 | None | Gap, close to Year 4 IS Strategy, Management and Acquisition | Conditional; overview only |
| A10 | Introductory acquisition: requirements, fit-gap, feasibility, total cost of ownership, risk, weighted selection | Strategist | T2 | ***Analyzing Organizational Computing Solutions*** teaches exactly this (requirements, fit-gap, feasibility, TCO, risk assessment, weighted scoring, pilots, sensitivity analysis) | **Covered** | **Reuse unchanged; no New Note** |
| A11 | Introductory development life cycle and organizational change / adoption | E5 topic 6 ("Information System Engineering", content unknown); strategist | T2 | None | Gap; **must stay overview**, since Systems Analysis and Design is Year 2 | Conditional; development overview and adoption at most one Note each |
| A12 | Database information systems and design | E5 topic 5 | T1 for ISU only | None in Year 1 | Belongs to Information Management (Year 2) | **Exclude** from this plan |
| A13 | Information technologies (hardware, software, networks) | E5 topic 2 | T1 for ISU only | Covered by Introduction to Computing: system and application software and operating systems read in full; the hardware, memory, network and web Notes judged by title and section (not read) | Covered | **Exclude**; no New Note |
| A14 | Professional ethics, privacy, law | CHED Professional Issues in IS is a separate sophomore course | n/a | *Ethical, Legal and Social Issues in Computing*, *Philippine Computing Laws…*, the security and privacy Notes already in Introduction to Computing | Covered at Year 1 depth | **Exclude**; Professional Issues in IS builds on these later |

---

## 3. Proposed Subject Plan sections (PROPOSAL)

Logical progression: what an information system is → what information and decisions it supports → what kinds exist → how technology, strategy and change act on organizations.

| # | Section | Rationale | Areas |
|---|---|---|---|
| 1 | **Information Systems and Organizations** | Establishes the socio-technical definition and the people around a system before any category | A1, A2, A3 |
| 2 | **Information Needs and Decision Making** | Moves from "what is an information system" to "what do organizations need from one" | A4, decision-support background |
| 3 | **Kinds of Information Systems** | The applications CHED's one outcome names | A5, A6, A7 |
| 4 | **Technology, Strategy and Change** | Where the existing Introduction to Computing Notes on technology and on solution analysis plug in, plus the organizational side | A8, A9, A10 (reuse), A11 |

**Framing check (as asked):** the proposed split holds against the evidence. Introduction to Computing teaches computing foundations (E3 and every Note read in section 4); Fundamentals of IS is the organizational view (E1 outcome, E4). **The one caution from the evidence is E8:** a school can label an Introduction to Computing description "IS 101", and the two courses share outcomes about organizations (CC101 LO3). So the boundary is real but thin, which is why section 4 matters.

---

## 4. Overlap audit with Introduction to Computing (bodies and Study Packs read in full)

I read the full body, Study Pack summary, key concepts and all quiz items (questions, keys and explanations) of every Note below, from the Notes in the production BSIT Year's Introduction to Computing plan. Classes: **1** reusable unchanged in Fundamentals of IS; **2** introductory background, does not replace an IS treatment; **3** overlaps enough that a New Note on the same ground would be redundant; **4** needs a separate content review before a decision.

| Existing Note (id) | What it actually teaches (FACT, from the body) | Class | Finding |
|---|---|---|---|
| **Analyzing Organizational Computing Solutions** (`b144ae51-…`) | Requirements analysis (functional and nonfunctional), fit-gap analysis, feasibility analysis, total cost of ownership, risk assessment, weighted scoring with a formula, mandatory versus preferred requirements, pilots, sensitivity analysis. Quiz (5 items) is consistent with the body | **1 and 3** | **The strongest overlap found.** It already teaches introductory IS acquisition and evaluation. A New Note on that ground would be redundant. Reusable unchanged in the Fundamentals of IS plan, and it is CHED's CC101 LO3 Note, so it also stays in Introduction to Computing |
| **The Computing Disciplines: CS, IT, IS, SE and CE** (`9b81844a-…`) | Distinguishes the five disciplines. IS is **one bullet**: aligns information, people, processes and technology with organizational goals through requirements analysis, process modeling and system selection. One quiz item on IS | **2** | Orientation only. Does not teach what an information system is. Useful as an optional opening Note in the new plan |
| **The Computing Industry, Professions and Careers** (`55ad85d4-…`) | Industry segments, software, infrastructure and data roles, entry paths, professional practice. *Systems analyst* appears once in Quick Recall | **2, with a boundary** | A careers view. The new stakeholder-roles Note (A3) must be about the roles around a system (users, managers, vendors), not careers, or it duplicates this |
| **System Software and Application Software** (`dd33d5f6-…`) | Software classes, operating systems, drivers, utilities | **3** for A13 | Any "technology in information systems" Note would repeat it; excluded |
| **Functions of an Operating System** (`ae90b1d8-…`) | Operating system functions, kernel, scheduling | **3** for A13 | Same. (The other hardware and network Notes in that plan, such as components of a computer system and how the Internet works, were checked by title and section only, not read, and are not classified here) |
| **Cloud Computing, the Internet of Things and Big Data** (`1659b866-…`) | Cloud service models, IoT pipelines, edge, the three Vs, batch and stream, shared responsibility. Technology description, no business or organizational treatment | **2** | The technical base for A8. Reuse unchanged as background; the organizational implications are the gap |
| **Emerging Technologies and Their Social Impacts** (`5e2a6baa-…`, GENERAL_EDUCATION) | Digital divide, workforce displacement, algorithmic bias, privacy, governance. Social, not organizational | **2** | Optional background to A8; not an organizational treatment |
| **Artificial Intelligence and Human Judgment** (`4325d971-…`) | Decision support versus automated decision-making, automation bias, distribution shift, oversight | **2** | Defines "decision support" in an AI context. Useful background to the decision-support Note, which must not repeat it |
| **Ethical, Legal and Social Issues in Computing**, **Philippine Computing Laws…** | Ethical reasoning, privacy governance, Philippine statutes | **3** for A14 | Professional Issues in IS (Year 2) builds on these; not part of this plan |
| **Computer Security Fundamentals…**, **Privacy, Data Protection and Safe Computing Practices** | CIA triad, controls, privacy principles, recovery | **3** for any IS security topic | Organizational security management is later-year; not in this plan |
| **Computing in Other Fields…**, **Professional Societies and Standards…** | Domain applications; societies and standards | **3** | No IS-specific content; no use here |

**Summary (FACT):** of the topics the strategist listed, **acquisition and solution evaluation (A10) is already covered**, **information technologies (A13) and ethics and security (A14) are fully covered**, and **technology trends (A8) are covered on the technology side only**. The rest of the plan's content (A1 to A5, the organizational half of A8) is **not** covered anywhere in production. No class 4 Note arose: every Note was clear on read.

**BSIS-specific content risks in the shared 182 (prioritised):**
1. **Boundary Notes above:** read in full; no BSIS blocker.
2. **Program-audience framing:** a text screen of all 176 metadata-ready bodies for `computer science student/major/program`, `BSCS`, `information technology student/program`, `BSIT`, `information systems student/program`, `BSIS` and teacher or pupil framing found **no hits**.
3. **Programming Notes:** no C++ in the language-neutral Notes; other languages appear only as illustrative mentions in six Notes (earlier spot-check, re-screened). The restricted C++17 convention stands (Q2). The Theta definition was added on 2026-11-05.
4. **Computing-theory dependence:** one Note (*Foundations of Modern Computing: Turing and von Neumann*) uses computability terms, as expected for an Introduction to Computing topic; two Mathematics in the Modern World Notes mention "advanced study" or discrete mathematics only as a pointer, not a prerequisite.

### 4.1 Evidence level of each reuse decision (182 shared placements)

| Level | Notes | Basis | Weight |
|---|---|---|---|
| **L1: full read plus Study Pack check** | **6** | The 3 legacy GE Notes (Emerging Technologies, Spanish Colonization, Philippine Revolution), read in full in the BSIT work and **again today with every quiz item**; the 3 GE Notes authored for the first-semester gap (Film and Moving-Image Art, Art Popular Culture and Media, Global Citizenship), reviewed with 15 Study Pack items in the earlier pass and re-read today | Strong |
| **L2: independent blind spot-check, owner-approved** | **35** | Risk-weighted sample of the 176 metadata-ready Notes (33 PASS, 2 HOLD, both resolved by the Theta edit); includes 7 Introduction to Computing Notes (*Analyzing Organizational Computing Solutions*, *The Computing Industry, Professions and Careers*, *Computing in Other Fields…*, *Professional Societies and Standards…*, *Philippine Computing Laws…*, *Artificial Intelligence and Human Judgment*, *Foundations of Modern Computing…*). *The Computing Disciplines…* was not in the sample; it was first read in full today. The prior notes record that the GPT reasoning was weak on the 22 non-notation rows | Moderate |
| **L2+: BSIS full read today** | 14 (overlapping the bands above and below) | The 10 Introduction to Computing Notes in the two sections that touch IS, plus *System Software…*, *Functions of an Operating System*, *Computer Security Fundamentals…* and *Privacy, Data Protection…* (bodies and Study Packs), read for BSIS suitability | Strong for those 14 |
| **L3: text screen only** | **141** | Program-audience and language screen over all 176 bodies (0 audience hits, language-neutral Notes C++-free); bodies not individually read | Weak but screened |

**Unchanged-since-review check (SELECT 2026-11-06, as the owner's condition requires):** of the 182 placed Notes and their Study Packs, **only four have an `updated_at` since 2026-11-04**:
- **Algorithms and Their Properties** (Computer Programming 1): the Theta edit of 2026-11-05 09:49:12Z changed the body; its Study Pack was last updated 2026-10-29 and is unchanged. Its spot-check PASS therefore predates the one-bullet edit; the edit only adds a definition.
- **Film and Moving-Image Art**, **Art, Popular Culture and Media in Contemporary Society** and **Global Citizenship and Global Civil Society**: created 2026-11-05 12:43 to 12:47Z; each Note's `updated_at` is minutes after its Study Pack's creation (consistent with the metadata and visibility being set after generation). **I re-read all three bodies and every quiz item today, in their current state, and the keys match the bodies**, so the L1 read stands on today's evidence, not on the earlier review alone.
- **No other Note or Study Pack among the 182 changed since 2026-11-04**, so the L2 spot-check (built 2026-11-05) and the earlier text screens still describe the current text for the other 178. **CORRECTION (2026-11-06, later):** I first wrote that the text screen's snapshot (`bodies176.json`, taken 2026-11-05 16:52 local time, which is UTC+8) came *after* the Theta edit. It did not: 16:52 +08:00 is 08:52Z and the Theta edit was 09:49:12Z, and the snapshot's copy of *Algorithms and Their Properties* has no Theta bullet. The other 175 Notes in that snapshot are unchanged since it was taken. The edited body (2465 characters, md5 `db58e2b0…`) was re-screened against production on 2026-11-06 and has no program-audience hits.

**Metadata compatibility is not canonical equivalence (as the owner states):** every Note is PUBLIC and carries Information Systems already (except the six), but that says where it *appears*, not that its content suits a BSIS learner. The L3 band is the open risk. **PROPOSAL:** carry it as a curator spot-check at publication (about 15 more, weighted to the Mathematics in the Modern World Notes that mention computing, and the Purposive Communication Notes on workplace writing), not a full re-read.

---

## 5. Taxonomy conventions checked (Q5)

- **Subject:** `notes.subject` is free text; **there is no Subject catalog table** in production (the taxonomy tables are the program catalog, program families and the Note-to-program link only). The LLM suggestion guidance asks for "the specific academic subject or professional discipline ... label only, no topic suffix" and not the domain name. **Existing practice in these journeys is the course title as Subject** (`Understanding the Self`, `The Contemporary World`, `Art Appreciation`, `Discrete Mathematics`, `Programming Fundamentals`), so `Fundamentals of Information Systems` fits the convention and is not a broad program name. It is a new value but needs no taxonomy change. (FACT)
- **Domain Context (ADR-001, FACT):** decided **per Note**; an unset value is legal only with exactly one Applicable Program; Information Systems business-process treatment stays on its program fallback and is **not** `COMPUTING`; plan membership never decides it; no new value without separate owner approval.
- **Bulk Generate** applies one Subject and one Domain Context per run, so a plan with mixed Domain Contexts is authored in more than one run. (FACT, from the workbook spec)
- **Authored Depth** `COLLEGE`. **Applicable Programs** `Information Systems` for New Notes (see below for a wider tag).
- **Cross-listing (permitted, but unproven for a Year journey):** the database enforces uniqueness only on (plan, Note), so a Note can sit in two plans of one root. The 84 Notes that already do so are all in **review sets** (ALE 64, LET 16, Architect Licensure 3, CPALE 1), **not** in any Year journey, so this is not a precedent for a Year. The shaping builder and spec accept it and flag it ("same canonical note in another plan, do not duplicate"). **Unverified:** how Year-level progress and readiness roll-ups treat a Note in two plans (the concept roll-up keys concepts by name within one aggregate, but I did not trace the per-plan and item tallies). (FACT for the database and builder; the product behaviour is not settled)

---

## 6. Candidate Note inventory (provisional, PROPOSAL)

**Read this as a tiered shortlist, not a final inventory.** Titles are proposed topic titles for the owner's and strategist's review; **nothing is authored**. T1 rows have at least one source naming the area; T2 rows are conditional on a source (section 1, E9). Subject is `Fundamentals of Information Systems`, Authored Depth `COLLEGE` throughout.

### 6.1 New

"Source confidence" is the strongest source behind the row (section 1): **High** = CHED text; **Medium** = an official school course description; **Low** = a third-party topic label only, or no source (strategist or inference).

| # | Section | Proposed title (knowledge-first) | Tier | Source confidence | Domain Context (per Note) | Applicable Programs |
|---|---|---|---|---|---|---|
| N1 | 1 | Information Systems as Socio-Technical Systems: People, Processes, Data and Technology | T1 | **Medium** (CIIT roles and entities; CHED title) | **Unset** (IS fallback): the organizational view of a system, which ADR-001 keeps off `COMPUTING` | Information Systems |
| N2 | 1 | Data, Information and Knowledge in Organizations | T1 | **Low** (ISU topic label only, third-party blurb) | **Decide at authoring.** Argument for `COMPUTING`: data versus information versus knowledge is transferable to any computing learner. Argument for unset: the Note's point here is the role and value of information in an organization (information quality, business use). The body decides | Information Systems |
| N3 | 1 | Roles in Information Systems Practice: Users, Developers, Managers and Vendors | T1 | **Medium** (CIIT description names these roles) | Unset (IS fallback): stakeholder roles around an organizational system | Information Systems |
| N4 | 3 | Transaction Processing Systems | T1 | **Low** (ISU "Types of Information Systems" label; CHED outcome says "applications") | Unset (IS fallback): business-process treatment | Information Systems |
| N5 | 3 | Management Information and Reporting Systems | T1 | **Low** (same) | Unset (IS fallback) | Information Systems |
| N6 | 3 | Decision Support and Business Intelligence Systems | T1 | **Low** (same) | **Decide at authoring.** Argument for unset: organizational decision support (managerial decisions, reporting) is IS treatment. Argument for `COMPUTING`: business intelligence and analytics are shared with data work. It must also not repeat *Artificial Intelligence and Human Judgment* (`COMPUTING`), which already defines decision support | Information Systems |
| N7 | 4 | Information Systems and Technology-Enabled Change in Enterprises | T1 | **High** (CHED outcome: "driving development and changes in enterprises") | Unset (IS fallback): the organizational implications, not the technologies | Information Systems |
| N8 | 2 | Operational, Managerial and Strategic Information Needs | **T2** | **Low** (strategist; no source) | Unset (IS fallback) | Information Systems |
| N9 | 3 | Enterprise-Wide Systems: ERP, CRM and Supply Chain Systems | **T2** | **Low** (strategist; CHED lists these as electives) | Unset (IS fallback). Scope: overview only, in the Note itself | Information Systems |
| N10 | 3 | Information Systems Across Business Functions | **T2** | **Low** (strategist; CIIT "range of industries" weakly) | Unset (IS fallback) | Information Systems |
| N11 | 4 | Information Systems and Organizational Strategy | **T2** | **Low** (strategist; close to Year 4 IS Strategy) | Unset (IS fallback) | Information Systems |
| N12 | 4 | The Development Life Cycle of an Information System | **T2** | **Low** (ISU topic label "Information System Engineering", content unknown) | **Decide at authoring.** Candidate `COMPUTING`: the life cycle is transferable software and systems knowledge; if the body is organization-specific it stays unset. A `COMPUTING` Note with one program is legal. Scope: overview only, since Systems Analysis and Design is Year 2 | Information Systems (a wider tag is a separate later decision) |
| N13 | 4 | Organizational Change and Information System Adoption | **T2** | **Low** (strategist; no source) | Unset (IS fallback) | Information Systems |

**Read the confidence column before the count.** Only N7 is High and N1 and N3 Medium; the other four T1 rows rest on a third-party topic label. "Seven T1 rows" does not mean seven equally supported Notes.

An unset Domain Context needs Information Systems as the **only** program on each such Note. If any is ever reused by another program, every affected Note then needs an explicit Domain Context first (hard rule); I propose nothing that triggers that.

### 6.2 Reuse (cross-listed from Introduction to Computing, unchanged, Status Reuse; existing metadata read from production 2026-11-06)

| Existing Note | Proposed section | Class | Subject | Domain Context | Depth | Applicable Programs now | Visibility |
|---|---|---|---|---|---|---|---|
| Analyzing Organizational Computing Solutions (`b144ae51-…`) | 4 | 1 | Computer Science | COMPUTING | COLLEGE | Computer Science, Information Systems, Information Technology | PUBLIC |
| Cloud Computing, the Internet of Things and Big Data (`1659b866-…`) | 4 | 2 | Computer Science | COMPUTING | COLLEGE | Computer Science, Information Systems, Information Technology | PUBLIC |
| The Computing Disciplines: CS, IT, IS, SE and CE (`9b81844a-…`), optional | 1 | 2 | Computer Science | COMPUTING | COLLEGE | Computer Science, Information Systems, Information Technology | PUBLIC |
| Artificial Intelligence and Human Judgment (`4325d971-…`), optional | 2 | 2 | Computer Science | COMPUTING | COLLEGE | Computer Science, Information Systems, Information Technology | PUBLIC |
| Emerging Technologies and Their Social Impacts (`5e2a6baa-…`), optional | 4 | 2 | Science and Technology | GENERAL_EDUCATION | COLLEGE | Computer Science, Information Technology and six Education programs (nine with Information Systems, if the Q7 tag is approved) | PUBLIC |

Not cross-listed: the software, operating system, networking, security and ethics Notes (class 3: already taught in Introduction to Computing; excluded to avoid duplication).

---|---|---|---|
| Analyzing Organizational Computing Solutions | 4 | 1 | **Cross-list** (the acquisition and evaluation content A10) |
| Cloud Computing, the Internet of Things and Big Data | 4 | 2 | Cross-list as the technology base for N7 |
| The Computing Disciplines: CS, IT, IS, SE and CE | 1 | 2 | Optional opener |
| Artificial Intelligence and Human Judgment | 2 | 2 | Optional background to N6 |
| Emerging Technologies and Their Social Impacts | 4 | 2 | Optional background to N7 |

Not cross-listed: the software, operating system, networking, security and ethics Notes (class 3: already taught in Introduction to Computing; excluded to avoid duplication).

---

## 7. Six-Note metadata eligibility review (PASS or HOLD)

All six: full body and Study Pack (summary, key concepts, all five quiz items with keys and explanations) read today. All six are PUBLIC, `GENERAL_EDUCATION`, `COLLEGE`, with an explicit Domain Context, so **adding Information Systems creates no Domain Context binding conflict** (the unset-needs-one-program rule does not apply). Applicable Programs is discovery only and never reaches a prompt (ADR-001). Production was not changed.

| # | Note (id) | Verdict | Justification |
|---|---|---|---|
| 1 | Emerging Technologies and Their Social Impacts (`5e2a6baa-…`) | **PASS** | Program-neutral social and governance content; keys correct and supported by the body; currently CS, IT and six Education programs |
| 2 | Spanish Colonization and Transformation of Philippine Society (`556e7dbe-…`) | **PASS** | Quiz keys supported by the body, including the encomienda "not a land grant" item; the dates and terms are standard (1565, 1571, galleon trade 1565-1815). Historical facts were checked for internal consistency and against my own knowledge, **not** against a source this pass (INFERENCE) |
| 3 | Philippine Revolution and the First Philippine Republic (`769cd008-…`) | **PASS** | Keys correct; the Katipunan, Tejeros, Biak-na-Bato, Treaty of Paris, Malolos Constitution (21 January 1899) and inauguration (23 January 1899) dates agree with each other and with my knowledge (INFERENCE, not source-checked) |
| 4 | Global Citizenship and Global Civil Society (`45133a32-…`) | **PASS, with a flagged wording** | Quiz keys correct. The body says the UN trafficking protocol was "adopted in 2000" and Republic Act No. 10364 was passed "in 2012". I believe the first is right (the UN General Assembly adopted it in November 2000) and that "2012" is the Act's year in its title (*Expanded Anti-Trafficking in Persons Act of 2012*) while its enactment date is 2013 (INFERENCE from memory; **verify against a primary source**). No quiz item tests it, and it is not an Information Systems matter, so it does not gate this tag |
| 5 | Film and Moving-Image Art (`3b53ce07-…`) | **PASS** | Appropriate GE art content for any learner; keys correct; the Cinemalaya (2005) and Brocka (1975) facts are standard (INFERENCE) |
| 6 | Art, Popular Culture and Media in Contemporary Society (`6256e54b-…`) | **PASS** | Keys correct; relevant to media and digital platforms, so it suits a BSIS learner; no program framing |

**Result: six PASS, no HOLD.** On approval I can prepare the metadata handoff `.sql` (idempotent, STEP 0 guards re-read against production, rollback included, exact ids and titles) for the owner to run, dry run then `COMMIT`. I have not prepared it, because the owner said this review comes first.

---

## 8. Updated estimate of New Notes (PROPOSAL)

| Basis | New Notes |
|---|---|
| **T1 rows with at least one source naming the area** (N1 to N7) | **7** |
| **T2 rows conditional on a source** (N8 to N13) | **up to 6** |
| **Estimate** | **7 to 13**, replacing the earlier 18 to 26 |
| Reuse cross-listed into the plan | 0 by default (R2); up to 5 if approved, one of them strong |

The reduction comes from three findings: acquisition and evaluation is already taught (A10), information technologies and ethics are already taught (A13, A14), and the organizational areas the evidence names are fewer than the strategist's nine. **Even the seven T1 rows rest on course descriptions and topic labels, not an outline**, so treat 7 as a floor that could still fall or rise once a real syllabus is read.

---

## 9. Remaining owner decisions (only those that need approval)

| # | Decision | Recommendation |
|---|---|---|
| R1 | **Evidence route for the final inventory.** IS 2010 (ACM/AIS) and the CAIS review are blocked to me (HTTP 403). Download the IS 2010 PDF (`is-2010-acm-final.pdf`, ACM) and hand me the file, or authorise another route (for example, two or three Philippine syllabus PDFs you can obtain). Or proceed to Phase B on T1 only | **Provide IS 2010.** It is the one independent benchmark for what a foundations course covers, and it settles the T2 rows. Proceed on T1 only if you would rather not wait, with the T2 rows left out |
| R2 | **Cross-listing existing Introduction to Computing Notes** inside the Fundamentals of IS plan (section 6.2). The database permits it and the builder flags it, but there is **no Year precedent and Year progress roll-up behaviour is unverified** | **Default: do not cross-list yet.** If you want it, the one strong candidate is *Analyzing Organizational Computing Solutions* (the acquisition content, A10), and I would first check how Year readiness counts a Note in two plans. Without cross-listing, the plan simply omits A10 and the Introduction to Computing plan already teaches it |
| R3 | **Approve preparing the metadata handoff** for the six Notes now that all six are PASS | Yes |
| R4 | **Approve section 3's four-section structure** as the shaping starting point (it will move if R1 brings new evidence) | Yes |

Not an owner decision: the per-Note Domain Context (decided at authoring under ADR-001), and the plan-level Subject (settled in section 5).

---

## 10. New evidence that touches the earlier Phase A report

- **C7 is resolved:** IS101's prerequisite is **CC101**, read directly from the CHED-hosted PDF (E1). The earlier "probably CC101" is now a FACT.
- **Six semester curricula now (PCU added):** Understanding the Self first semester is 4 of 6 (CSPC, ISU, UE, NST; Silliman and PCU second), still a majority. Fundamentals of IS is second semester at 5 of 6 (UE first). **Organization and Management Concepts is Year 1 first semester at 2 of 6 (CSPC and PCU)** plus Benilde's term 1, up from 1 of 5. It is still not a majority and CHED's sample places it in the sophomore year, so Q1's exclusion stands, but the owner's note that its placement "remains subject to future research" is borne out: it is the one excluded subject with growing Year 1 evidence.
- **Incidental, BSIT:** PCU's BSIS puts Human Computer Interaction in Year 1 second semester and Malayan Colleges Mindanao's BSIS has it in Year 1, which adds to the earlier note that CHED's BSIT sample places Introduction to HCI in the freshman second semester. Not acted on; BSIT is not being modified.

---

## 11. Sources

- CHED CMO 25 s.2015, CHED-hosted PDF (scanned, read as page images): https://legacy.ched.gov.ph/wp-content/uploads/2017/10/CMO-no.-25-s.-2015.pdf
- CHED CMO 25 s.2015, archive.org copy used in the earlier pass: https://archive.org/download/ched-bsis-bsit-bscs/CHED%20-%20BSIS%20BSIT%20BSCS_text.pdf
- CIIT BSIS program page: https://www.ciit.edu.ph/course/college-information-systems/
- ISU IS 121 course guide (Scribd summary, truncated): https://www.scribd.com/document/497952435/COURSE-GUIDE
- University of Cebu IT Course Guide 2019-2020 (Scribd summary, truncated): https://www.scribd.com/document/488460001/ISFund-CourseGuide-OFLS
- NwSSU IS 101 page (not usable as Fundamentals of IS evidence): https://lms.nwssu.edu.ph/course/info.php?id=209
- PCU BSIS curriculum: https://pcu.edu.ph/undergraduate-programs/college-of-informatics/coi-summary-of-course-and-units-bs-in-information-system/
- UE BSIS curriculum (2023 page, same table as the 2019 page): https://apps.ue.edu.ph/p/curriculum.php?c=CBSIS2023
- Malayan Colleges Mindanao BSIS (CMO 25-based, SY 2020-21): https://mcm.edu.ph/wp-content/uploads/2020/11/IS-2020.pdf
- Blocked to me, not read (HTTP 403): ACM IS 2010 PDF https://www.acm.org/binaries/content/assets/education/curricula-recommendations/is-2010-acm-final.pdf; the CAIS 2015 review https://core.ac.uk/download/301377535.pdf; also not retrievable: Stair and Reynolds table of contents (no readable catalog page).
- Repo: `docs/architecture/ADR-001-canonical-knowledge-architecture.md`; `docs/curriculum/review-set-workbook-spec.md`; `docs/claude-plans/bsit-year1/` (the spot-check findings are on branch `docs/bsit-year1-spot-check-pack`, PR #1562).

## 12. Limits of this pass

- No Fundamentals of IS outline or syllabus was readable; the inventory is a tiered shortlist (sections 1 and 6).
- The Scribd summaries (E5, E6) are third-party AI blurbs, truncated.
- ACM IS 2010 and the CAIS review were blocked (403) and not worked around.
- Historical and film facts in the six Notes were checked for internal consistency and against my own knowledge, not against sources.
- The 141 Notes in the L3 band were screened by text, not read.
- Production reads were SELECTs on `notelib_db_prod`; no body search beyond the keyword screens above.
