Status: final Fundamentals of IS shaping revision, Phase B preparation, 2026-11-06, for owner and Note Strategist review. NOT implementation-ready: no Note, Study Pack, metadata, collection or publication was created or changed; production was read with SELECTs only; no SQL was executed. Waiting for explicit owner approval before any implementation.

# BSIS Year 1: Fundamentals of IS, final shaping revision

Labels: **FACT** (read in a source or in production), **INFERENCE** (my reading), **PROPOSAL** (recommended, the owner's to approve).

This supersedes the 13-row provisional inventory and the T1/T2 tiers in `bsis-year1-fundamentals-of-is-evidence-and-overlap-audit.md`. Companion files: `bsis-year1-is-applicability.sql` (reviewable metadata handoff, not run), `bsis-year1-fis-plan-draft.tsv` (the new plan's rows in the shaping-TSV column layout; **not** the BSIS shaping TSV or workbook, which are not generated), `evidence/is2020-foundations-excerpts.txt`.

---

## 0. Decisions preserved and applied

Unchanged and not reopened: separate root `BS Information Systems — 1st Year`; ten Subject Plans, five a semester (First: Introduction to Computing, Computer Programming 1, Mathematics in the Modern World, Understanding the Self, Art Appreciation; Second: Computer Programming 2, Fundamentals of Information Systems, Readings in Philippine History, Purposive Communication, The Contemporary World); restricted C++17; Discrete Mathematics excluded; advanced BSIS professional subjects outside Year 1; Domain Context per Note; no new Domain Context value.

Applied from this round: the **12-Note baseline in four sections** (section 3); **N10 merged**; **no cross-listing** of existing Notes into the FIS plan until progress and roll-up behaviour is verified; per-Note metadata resolved where the planned content allows (section 5); the six-Note recheck with the RA 10364 verification (section 6); a reviewable SQL handoff (section 8).

---

## 1. Curriculum evidence matrix

CHED remains the Philippine curriculum reference. **ACM/AIS material is academic guidance, not a CHED requirement, and nothing below claims it is mandated.**

| # | Source | What it supports for Fundamentals of IS | Label | Read how |
|---|---|---|---|---|
| E1 | **CHED CMO 25 s.2015** (CHED-hosted scanned PDF, page 26, rendered and read as an image) | IS101 Fundamentals of IS: 3 units, **prerequisite CC101**, one outcome: *compare and identify the major technologies and applications of information systems in driving development and changes in enterprises* | FACT | Read directly |
| E2 | CHED BSIS sample program of study (pages 19 to 20, images) | Freshman second semester, 6 IS units with Computer Programming 2; other BSIS-specific courses sophomore or later | FACT (a sample, not a mandate) | Read directly |
| E3 | **ACM/AIS IS2020**, Appendix 3, A3.1.1 *Foundations of Information Systems*, printed pp. 97 to 101; §4.2.1 p. 52 (`evidence/is2020-foundations-excerpts.txt`) | The only substantive **competency specification** for an introductory IS course that I could read. Eight competencies with knowledge elements and Bloom levels (section 2). §4.2.1: foundational IS competencies are "typically first introduced as part of an 'Introductory Course,' sometimes also referred to as an 'MIS Course' or 'Foundations Course'", which supports a stand-alone FIS plan | FACT | Read directly from the PDF text layer |
| E4 | IS2020 Foreword and Executive Summary (pp. 8, 10 to 11) | IS2020 "does not offer a prescriptive set of core and elective courses"; it "does not invalidate any core requirements or recommendations from the IS2010 report" | FACT | Read directly |
| E5 | **ACM/AIS IS 2010**, *IS 2010.1 Foundations of Information Systems* (the owner cites printed p. 36) | **Not read by me.** `acm.org` and the AIS eLibrary PDF return HTTP 403; the USF repository copy is withdrawn; Scribd gives only a landing page. I did not work around the blocks and did not rely on the strategist's summary | n/a | **Evidence limitation.** E4 supplies the only direct, read link between IS2010 and IS2020 |
| E6 | IS2020 A3.5.2 *IS Management and Strategy* and A3.5.3 *Digital Innovation* (pp. 159, 164) | Context only: the **advanced** areas (competencies at Evaluate and Create) that sit beyond Year 1. They show where Understand-level Year 1 Notes must stop | FACT | Read directly |
| E7 | CIIT FIS course description (verbatim in the earlier audit) | Roles around a system (developers, users, business managers, IT managers, vendors); real-world applications; case studies | FACT | Official school page |
| E8 | ISU and UC course-guide topic labels (Scribd, AI blurbs, truncated); placement at CSPC, ISU, Silliman, NST, PCU, UE, Benilde, Malayan Colleges Mindanao, CIIT | Fundamentals of IS is a Year 1 course at every curriculum read | FACT for placement; topic labels Low confidence | Supporting only |
| E9 | NwSSU "IS 101" page | **Not usable**: its description paraphrases CHED's Introduction to Computing outcomes | FACT | Excluded |

**New evidence classification (replaces T1/T2):**
- **S1, corroborated:** a CHED outcome or an explicit IS2020 Foundations knowledge element, **and** at least one school source.
- **S2, competency-model explicit:** an explicit IS2020 Foundations knowledge element; no CHED text or school corroboration beyond titles.
- **S3, partial or inferred:** an adjacent IS2020 element, or the strategist or school labels only.

**Why this is stronger than before (FACT):** IS2020 gives knowledge elements and skill levels for the introductory course itself, so most rows now rest on a named competency rather than a third-party topic label. **Why it is still limited:** IS2020 is a US-originated competency model for degree programs, not a Philippine Year 1 syllabus, and I could not read IS 2010.1.

---

## 2. The ten areas, verified against the evidence

IS2020 A3.1.1 competencies (printed pp. 97 to 101): **C1** classify the components, elements, operations and impact of IS; **C2** interpret the dimensions, characteristics and value of quality information; **C3** explain the roles, responsibilities and characteristics of the IS professional; **C4** recommend techniques for using information and knowledge for business decision making and strategic value; **C5** analyze a business case and critique appropriate IS solutions; **C6** critique and recommend Enterprise Systems; **C7** identify techniques for transmitting and securing information in an organization; **C8** demonstrate basic computational and design problems using IS development, with methodologies, tools and methods for improving processes and organizational change. Bloom: 2 = Understand, 3 = Apply.

| # | Area (owner's list) | IS2020 knowledge elements (level) | CHED / school | Existing canonical coverage | Treatment |
|---|---|---|---|---|---|
| 1 | IS as organizational and socio-technical systems | C1: components (technology, data, people, procedures) (2); operations, the input, processing, storage, output, control cycle (3); how IS helps deal with information (3); common types (2). C5: elements (3) | CHED title; CIIT roles and entities | One bullet in *The Computing Disciplines…* | **N1** |
| 2 | Information quality and organizational value | C2: dimensions of information quality and evaluation methods (3); **the virtual value chain** (2). C4: IS and the value of information (2) | ISU topic label ("Information and its Role in Business") | None | **N2** |
| 3 | Organizational information needs and decision-making | C4: role of information in decision making (2); **types of decisions and techniques (3); stages of decision making and appropriate IS tools (3)** | none beyond labels | None | **N8** |
| 4 | Operational and managerial information systems | C1/C5: common types, **Transaction Processing Systems named explicitly** (2); types and utility of IS (3) | CHED outcome says "applications of information systems" | None | **N4, N5** (MIS reporting is not named in IS2020; **INFERENCE** that it is the standard managerial category) |
| 5 | Decision support and business intelligence | C4: **decision support systems** (2); C6: BI within enterprise systems (3) | n/a | *Artificial Intelligence and Human Judgment* defines decision support only in an AI context | **N6** |
| 6 | Enterprise applications | C6: components of an enterprise system (3); **types and functions of enterprise systems, including ERP and BI** (3); business process modelling and optimization (3) | CHED lists Enterprise Systems, ERP, CRM, SCM as **electives** | None | **N9**, ERP and BI well supported; CRM and supply chain at overview; **business process modelling kept to an overview** because Business Process Management is a later CHED course |
| 7 | IS strategy and competitive advantage | C4: key IT issues, trends and alignment of IT with business (2); **IS strategic planning process** (2); information and knowledge for strategic value (2) | n/a | None | **N11**, at Understand level only (the advanced management and strategy area is E6) |
| 8 | Technology-enabled organizational change | C1: functions of IS and **impact on facilitating organizational change** (3); C8: innovative methods for improving processes and organizational change | **CHED outcome** ("driving development and changes in enterprises") | Technologies in *Cloud, IoT and Big Data*; social effects in *Emerging Technologies…* | **N7**, organizational side only |
| 9 | Acquisition and development | C8: **systems development lifecycle (2); software development methodologies (2); buy versus build (2); outsourcing possibilities and models (2)**; methods and tools (3) | ISU label ("Information System Engineering", content unknown) | **Acquisition and solution evaluation: covered by *Analyzing Organizational Computing Solutions*** | **N12** for the life cycle, methodologies and sourcing options; **REUSE** for evaluation |
| 10 | Organizational adoption, security, ethics, risk | C7: transmitting and securing information, privacy threats, regulations (all 2); C3: ethical standards for the IS professional (3); C1 org change (partial) | n/a | Security, privacy, networks, ethics and law are taught in Introduction to Computing; risk assessment appears in *Analyzing…* and *Computer Security Fundamentals…* | **Adoption: N13.** Security, privacy, ethics and risk: **REUSE, no new Note** (not cross-listed) |

**What IS2020 puts in Foundations but this plan deliberately leaves out (INFERENCE, with reasons):** "methods for storing and organizing information" (Information Management, Year 2); "business process modelling and optimization" beyond overview (Business Process Management); "computational methods and software tools" (the Computer Programming plans); hardware, software and communication-media components and securing information (Introduction to Computing). The plan does not pretend to cover all of A3.1.1, only its organizational half.

---

## 3. Final FIS shaping inventory (PROPOSAL, 12 Notes)

Subject `Fundamentals of Information Systems`, Authored Depth `COLLEGE`, Applicable Programs `Information Systems`, Status New. Target Bloom level for every Note is **Understand to Apply** (IS2020's Foundations levels). Evidence uses the S1 to S3 scale.

| Order | Section | Note title | ID | Evidence | Domain Context | Scope: includes | Scope: excludes |
|---|---|---|---|---|---|---|---|
| 1 | 1 Information Systems and Organizations | Information Systems as Socio-Technical Systems: People, Processes, Data and Technology | N1 | **S1** | (unset) | Components (technology, data, people, procedures); the input, processing, storage, output, control cycle; what an IS does for an organization; how it differs from IT infrastructure | Re-teaching hardware, software or networks; system types (N4 to N9) |
| 2 | 1 | Data, Information and Knowledge in Organizations | N2 | **S2** | (unset) | Data versus information versus knowledge; dimensions of information quality; how organizations use information; the virtual value chain | **Database design and storage methods (Information Management)**; digital representation (Introduction to Computing) |
| 3 | 1 | Roles in Information Systems Practice: Users, Developers, Managers and Vendors | N3 | **S1** | (unset) | Stakeholder roles and responsibilities around a system; characteristics of the IS professional; a pointer to professional ethics | Career pathways (*The Computing Industry…*); a full ethics treatment (Professional Issues in IS, Year 2) |
| 4 | 2 Information Needs and Decision Making | Operational, Managerial and Strategic Information Needs | N8 | **S2** for decision making; the three-level framing is **INFERENCE** | (unset) | Types of decisions; the stages of decision making; which information each organizational level needs | Quality dimensions (N2); system categories (N4 to N6) |
| 5 | 3 Kinds of Information Systems | Transaction Processing Systems | N4 | **S2** | (unset) | Purpose, users, inputs, outputs; examples across business functions | Database and transaction internals |
| 6 | 3 | Management Information and Reporting Systems | N5 | **S3** | (unset) | Purpose, users, inputs, outputs; periodic and exception reporting; examples across business functions | Decision modelling (N6); dashboards beyond overview |
| 7 | 3 | Decision Support and Business Intelligence Systems | N6 | **S2** | (unset) | Purpose, users, inputs, outputs; organizational decision support and BI; examples across business functions | AI oversight and automation bias (*Artificial Intelligence and Human Judgment*); data science techniques |
| 8 | 3 | Enterprise-Wide Systems: ERP, CRM and Supply Chain Systems | N9 | **S2** for ERP and BI; **S3** for CRM and supply chain | (unset) | Integrated enterprise systems, components and functions; the sharing of data across functions; overview of CRM and supply chain; process integration at overview | Business process modelling and redesign (Business Process Management); implementation projects |
| 9 | 4 Technology, Strategy and Change | Information Systems and Organizational Strategy | N11 | **S2** | (unset) | **Why** information systems create organizational value; IT-business alignment; the strategic planning process; competitive advantage | Strategic management and governance frameworks (IS2020 A3.5.2, later) |
| 10 | 4 | Information Systems and Technology-Enabled Change in Enterprises | N7 | **S1** | (unset) | **How** information systems and technologies transform activities, products, services and ways of working; digital business at overview | Re-describing the technologies (Cloud, IoT, AI Notes); innovation methods (IS2020 A3.5.3, later) |
| 11 | 4 | The Development Life Cycle of an Information System | N12 | **S2** | (unset) | High-level stages of developing or acquiring a system; methodologies at overview (plan-driven versus iterative); sourcing options (build, buy, outsource) at the stage level | **Evaluating and selecting a solution (Analyzing Organizational Computing Solutions)**; detailed systems analysis and design (Year 2) |
| 12 | 4 | Organizational Change and Information System Adoption | N13 | **S3** | (unset) | Stakeholder acceptance, resistance, training, organizational effects of introducing a system | Advanced change-management frameworks; workforce planning (IS2020 A3.5.2, later) |

**N10 (Information Systems Across Business Functions) is not a standalone Note.** Its coverage is merged into N4, N5, N6 and N9 as examples across accounting, marketing, human resources and operations. IS2020's C5 (apply IS to business cases across contexts) supports teaching by example. Standalone N10 would need a distinct learning outcome the four Notes cannot cover; I found none.

**Category distinctions (N4 to N9), to prevent repetition:**

| Note | Purpose | Main users | Inputs | Outputs |
|---|---|---|---|---|
| N4 Transaction Processing | Record and process routine business events | Operational staff | Transactions | Updated records, receipts, confirmations |
| N5 Management Information and Reporting | Summarize operations for monitoring and control | Middle managers | Processed transaction data | Periodic and exception reports |
| N6 Decision Support and BI | Support analysis and non-routine decisions | Managers, analysts | Internal and external data, models | Analyses, dashboards, scenarios |
| N9 Enterprise-Wide Systems | Integrate processes and shared data across functions | Whole organization | Shared enterprise data | Integrated processes and consolidated information |

**N11 versus N7.** N11: why information systems create value, support strategy and give advantage. N7: how they change activities, products, services and ways of working. **PROPOSAL:** keep separate. **Merge gate:** at authored-body review, if more than about half of either body restates the other, merge into one Note, "Information Systems, Strategy and Technology-Enabled Change". That test needs the authored text, so it is a gate, not a decision today.

**N5 gate (S3):** if the authored N5 cannot be distinguished from N4 and N6 by purpose, users and outputs, merge into N4 as "Transaction Processing and Management Reporting Systems".

---

## 4. Canonical overlap matrix

Existing Notes are from the Introduction to Computing plan; **bodies and Study Packs were read in full** in the earlier pass (section 7). "Unchanged reuse sufficient?" asks whether an existing Note could stand in for the new one. Actions: **KEEP** (author a New Note), **MERGE**, **REUSE** (an existing Note already covers it; no New Note), **HOLD**.

| New Note | Distinct learning objective | Closest existing Note | Overlap | Unchanged reuse sufficient? | Action |
|---|---|---|---|---|---|
| N1 | Classify the components and operations of an IS and explain its organizational impact | *The Computing Disciplines…* (IS is one bullet); the hardware and software Notes (title only, not read) | Low to moderate: N1 must treat hardware and software only as IS components | No: the bullet defines IS in one line and teaches none of the above | **KEEP** |
| N2 | Distinguish data, information and knowledge; apply information-quality dimensions; explain organizational value | none in this area (*Bits, Bytes and Units of Information* is about representation, title only) | Low | No | **KEEP** |
| N3 | Explain stakeholder roles and responsibilities around an IS and the IS professional's responsibilities | *The Computing Industry, Professions and Careers* | Moderate: both list roles (*systems analyst* once) | No: that Note is a careers view | **KEEP**, with the stakeholder boundary |
| N8 | Relate decision types and stages to the information each organizational level needs | *Analyzing Organizational Computing Solutions* (stakeholder needs for a solution) | Low | No | **KEEP**; gate: if N2 and N8 repeat "role of information in decisions", merge N8 into N2 |
| N4 | Explain the purpose, users, inputs and outputs of transaction processing systems | none | None | n/a | **KEEP** |
| N5 | Explain management information and reporting systems | none | None | n/a | **KEEP** (S3; merge gate above) |
| N6 | Explain organizational decision support and BI systems | *Artificial Intelligence and Human Judgment* | Low to moderate: both define "decision support" | No: that Note is about AI oversight and automation bias, not organizational DSS and BI | **KEEP**, must not repeat that definition |
| N9 | Explain enterprise-wide systems (ERP, CRM, supply chain) and the data and process integration they provide | *Cloud Computing, the Internet of Things and Big Data* (delivery models only) | Low | No | **KEEP** |
| N11 | Explain how IS create value and support strategy | *Analyzing Organizational Computing Solutions* (cost and risk of one solution) | Low | No | **KEEP**; merge gate with N7 |
| N7 | Explain how IS and technologies transform organizational activities, products, services and work | *Emerging Technologies and Their Social Impacts* (society, not organization); *Cloud, IoT and Big Data* (technology, not organization) | Low to moderate | No | **KEEP**; merge gate with N11 |
| N12 | Describe the stages and methodologies of developing or acquiring an IS and the sourcing options | **Analyzing Organizational Computing Solutions** (requirements, fit-gap, feasibility, TCO, risk, weighted selection; its overview compares custom development, commercial software and cloud services) | **Moderate on "buy versus build" and on requirements** | **Partly**: evaluation and selection are covered | **KEEP**, narrowed to the life cycle, methodologies and sourcing at stage level; **gate:** if the authored body re-teaches evaluation or requirements analysis, HOLD or merge into reuse |
| N13 | Explain adoption, acceptance, resistance and training when an IS is introduced | *Emerging Technologies…* (workforce displacement); *Ethical, Legal and Social Issues…* (workplace surveillance) | Low | No | **KEEP** (S3) |
| N10 | n/a | n/a | n/a | n/a | **MERGE** into N4, N5, N6, N9 |
| (no New Note) | Evaluate and select a computing solution; requirements, fit-gap, feasibility, TCO | **Analyzing Organizational Computing Solutions** | Covered | **Yes, unchanged** | **REUSE**; not cross-listed (deferred) |
| (no New Note) | Networks, security, privacy, ethics, law | *How the Internet Works*, *Computer Security Fundamentals…*, *Privacy, Data Protection…*, *Ethical, Legal and Social Issues…*, *Philippine Computing Laws…* | Covered at Year 1 depth | Yes, in Introduction to Computing | **REUSE**; not cross-listed |
| (no New Note) | Technologies and their social effects | *Cloud, IoT and Big Data*, *Emerging Technologies…*, *Artificial Intelligence and Human Judgment* | Covered | Yes | **REUSE**; not cross-listed |

**Duplication check across the twelve (as asked):** I found no existing Introduction to Computing Note that makes any of N1 to N9, N11 or N13 redundant. **N12 is the closest call and the only row with a structural gate.**

---

## 5. Domain Context decision log (ADR-001, per Note)

**Rule applied (FACT):** `COMPUTING` only for broadly transferable computing knowledge that a CS or IT learner would use unchanged; Information Systems business-process or organizational treatment stays on the Information Systems program fallback (ADR-001, binding case at the Domain Context governance section); plan membership never decides; unset is legal only with exactly one Applicable Program (**Information Systems**, one program, on all twelve). An unset value makes the authoring domain the program name, "Information Systems"; for conceptual Notes that is appropriate, and quantitative-computation guidance stays off, which fits these Notes.

| Note | Decision | Reason from the planned content | State |
|---|---|---|---|
| N1 | **(unset)** | The IS view of people, processes, data and technology in an organization; the organizational framing is the point | Resolved |
| N2 | **(unset)** | The scope is information quality, organizational value and the virtual value chain, not generic definitions; a generic data, information and knowledge Note would be a different Note | **Resolved** (was "decide at authoring") |
| N3 | **(unset)** | Stakeholder roles around an organizational system | Resolved |
| N8 | **(unset)** | Organizational levels and decision types | Resolved |
| N4, N5 | **(unset)** | Business-process systems | Resolved |
| N6 | **(unset)** | Organizational decision support and BI for managers; AI oversight stays in *Artificial Intelligence and Human Judgment* (`COMPUTING`) | **Resolved** (was "decide at authoring") |
| N9 | **(unset)** | Enterprise business applications | Resolved |
| N11, N7, N13 | **(unset)** | Organizational value, change and adoption | Resolved |
| N12 | **(unset)** | The planned scope is developing **or acquiring** a system with buy, build and outsource options and organizational roles, which is IS treatment. A generic software life-cycle Note shared with CS and IT is **not** proposed and would be a different Note | **Resolved**, with a gate below |

**Gate (not a HOLD):** at authored-body review, apply one test to every Note: *would a CS or IT learner use this text unchanged?* If yes for most of a body, reconsider `COMPUTING` (which then permits more programs). I expect none to fail it; N12 and N2 are the most likely.

**Reuse consequence (hard rule):** because all twelve are unset with one program, **adding any second program later requires an explicit Domain Context first.** I propose no such addition.

**Bulk Generate (FACT):** one Subject and one Domain Context per run. All twelve share Subject `Fundamentals of Information Systems` and `(unset)`, so one run covers them. That is a consequence of the per-Note decisions, not a reason for them; a different decision for any Note would simply need a separate run.

**No new Domain Context value is proposed.**

---

## 6. Six-Note eligibility report (PASS or HOLD)

All six re-read today from production (bodies, Study Pack summaries, key concepts and all five quiz items). All PUBLIC, `GENERAL_EDUCATION`, `COLLEGE`, exactly one Study Pack, **Information Systems not yet present**. All carry an explicit Domain Context, so adding a program creates **no binding conflict**.

| # | Note | Id | Verdict | Justification |
|---|---|---|---|---|
| 1 | Emerging Technologies and Their Social Impacts | `5e2a6baa-…` | **PASS** | Program-neutral; keys correct and supported |
| 2 | Spanish Colonization and Transformation of Philippine Society | `556e7dbe-…` | **PASS** | Keys supported; dates internally consistent (INFERENCE: not source-checked this pass) |
| 3 | Philippine Revolution and the First Philippine Republic | `769cd008-…` | **PASS** | Keys supported; dates agree with each other (INFERENCE: not source-checked this pass) |
| 4 | **Global Citizenship and Global Civil Society** | `45133a32-…` | **HOLD** | Wording error, below |
| 5 | Film and Moving-Image Art | `3b53ce07-…` | **PASS** | Keys correct |
| 6 | Art, Popular Culture and Media in Contemporary Society | `6256e54b-…` | **PASS** | Keys correct |

### RA 10364 verification (Global Citizenship and Global Civil Society)

- **Exact sentence (FACT, production):** the body's fourth Core Details bullet reads, in part, *"...and national laws such as the Philippines' Anti-Trafficking in Persons Act of 2003 (Republic Act No. 9208), expanded by Republic Act No. 10364 in 2012; civil society groups assist survivors and monitor enforcement."*
- **What the statute says (FACT):** the Lawphil page for RA 10364 gives the short title *Expanded Anti-Trafficking in Persons Act of 2012*, the session as "Begun and held in Metro Manila, on Monday, the twenty-third day of July, two thousand twelve", and the line **"Approved: FEB 06 2013"**. **Limit:** the approval line and the passage dates came from the part of the page that my fetch tool summarised rather than quoted verbatim, because the page was truncated; the title line and session line were verbatim. A web search found the Senate's legislative record listing the approval as 2/6/2013, and one source giving 28 January 2013 for the Act itself (so a second date exists for a different stage); **every source agrees on 2013, none on 2012.**
- **Verdict:** "in 2012" is correct only if read as the year in the Act's short title; read as the year it was passed or enacted it is wrong. The Note presents it as when the law expanded RA 9208, so a learner would take 2012 as the enactment year. **HOLD, as instructed.**
- **Smallest correction (PROPOSAL):** change `in 2012` to `in 2013` (one token), giving *"...expanded by Republic Act No. 10364 in 2013;"*. If the owner prefers to keep the short title, the alternative is *"...expanded by the Expanded Anti-Trafficking in Persons Act of 2012 (Republic Act No. 10364), approved in 2013;"*.
- **Study Pack affected? No (FACT, SELECT):** neither "10364" nor "2012" appears in the pack's summary, key concepts or quiz (the pack mentions trafficking elsewhere, not the date), so no regeneration or Study Pack edit is needed. The correction touches the Note body only.
- **Other trafficking-protocol claim:** the body says the UN protocol was "adopted in 2000". **I did not verify this against a source this pass** (INFERENCE from memory that it is correct, the General Assembly having adopted it in November 2000). Verify it in the same review.
- **Not silently edited, and no correction SQL is prepared.** On approval I can prepare a guarded, md5-checked body edit for the owner to run, in the pattern of the Theta edit.

---

## 7. Reuse evidence for the 182 shared placements (preserved; not all content-verified)

| Level | Notes | Basis |
|---|---|---|
| **Fully content-reviewed** | **6** (the six GE Notes above) | Full body and Study Pack read, the three newest created 2026-11-05 and re-read today in their current state |
| **Previously reviewed, unchanged** | **35** blind spot-checked (33 PASS, 2 HOLD resolved by the Theta edit) | Plus 14 Introduction to Computing and related Notes read in full today for BSIS (some overlap with the 35). Changed since review: only *Algorithms and Their Properties* (the Theta bullet; its Study Pack unchanged) |
| **Metadata-ready** | 176 already tagged Information Systems | Metadata compatibility is not canonical equivalence |
| **Text-screened only** | **141** | Program-audience and language screen over all 176 bodies (0 hits) on a snapshot taken 2026-11-05 08:52Z. **One Note changed after it** (*Algorithms and Their Properties*, the Theta bullet at 09:49Z); that body was re-screened against production on 2026-11-06 (no hits). The other 175 are unchanged since the snapshot |
| **Needing additional review** | none identified; **PROPOSAL:** about 15 more targeted checks at publication (computing-flavoured Mathematics in the Modern World Notes; workplace-writing Notes in Purposive Communication) | Risk-based, not a full re-read |

Only four of the 182 changed since 2026-11-04 (the three new GE Notes and *Algorithms and Their Properties*). Restricted C++17 compatibility is unchanged: 42 C++-named Notes, 134 language-neutral and metadata-ready.

---

## 8. Metadata SQL handoff (reviewable, not run)

**Superseded by revision 2; see `bsis-year1-phase-b-handoff.md` section 3 for the design, the tests and the owner instructions.** Three scripts, none run against production: `bsis-year1-is-applicability.sql` (five PASS Notes, five rows), `bsis-year1-global-citizenship-ra10364-correction.sql` (one-token body correction), and `bsis-year1-is-applicability-global-citizenship-held.sql` (the sixth Note's tag, which refuses until the correction is in place). All use **one psql session** (`BEGIN`, hard preconditions that raise, `INSERT … RETURNING`, post-write assertions, and `COMMIT` only with `-v mode=commit -v confirm=owner-approved`; default is a dry run that rolls back), print an **id-exact rollback statement**, and were exercised on a throwaway local PostgreSQL 16 (not production). The first draft's separate `-c "COMMIT;"` was unsafe across connections and has been replaced.

---

## 9. Remaining risks and owner decisions

| # | Item | Recommendation |
|---|---|---|
| D1 | **RA 10364 wording** in *Global Citizenship…*: approve the one-token correction (`in 2012` to `in 2013`), an owner-run guarded edit | Approve. I can prepare the guarded edit SQL |
| D2 | **Metadata SQL:** approve the owner to run the five-Note handoff | Approve after inspection |
| D3 | **IS 2010.1:** I could not read it. Optionally download the ACM PDF into the repo or share the section; otherwise it stays a documented limitation | Optional. IS2020, CHED and the school sources already support the baseline |
| D4 | **Plan title and emoji** for the new plan (draft uses "🏢 Fundamentals of Information Systems") | Your choice |

**Risks carried, not decisions:** N5 and N13 are S3 and N9's CRM and supply-chain half is S3, so each is gated at authored-body review; N11/N7 and N2/N8 merge gates need authored text; N12 overlaps *Analyzing Organizational Computing Solutions* on sourcing; cross-listing is deferred until Year roll-up behaviour is verified; the 141 text-screened Notes are screened, not read; the IS 2010.1 limitation; CHED's own outline of the course is a single outcome.

---

## 10. Phase B readiness: **READY WITH HOLDS**

**Why ready:** the structure, 12-Note baseline, per-Note metadata, overlap findings and SQL handoff are complete, and the evidence is materially stronger (IS2020 Foundations gives named competencies and levels for ten of the twelve rows). **Holds:** (1) *Global Citizenship…* excluded from the metadata run until D1; (2) the authored-body gates above (N5, N11/N7, N12, N2/N8, N13); (3) IS 2010.1 unread (D3, non-blocking); (4) cross-listing deferred; (5) about 15 publication-time spot-checks. **Not a blocker for building the shaping rows**, only for authoring the affected Notes and running the held statement.

**Compatibility check (run, output discarded):** the draft TSV has exactly the shaping-TSV columns, and the repo's `build_review_set_workbook.py` accepted it (1 plan, 12 rows, all New, **1 bulk-generate batch**, which confirms the single Subject and Domain Context run). The workbook was written to a scratch directory and is not a deliverable.

On approval, Phase B would: add the FIS plan's twelve rows (`bsis-year1-fis-plan-draft.tsv` is already in the shaping-TSV column layout) to the BSIS shaping TSV with the nine shared plans' Reuse rows, regenerate the workbook with the builder, and write the journey, decision log and handoff. **No part of that has been started.**

---

## 11. Sources and limits

- CHED CMO 25 s.2015, CHED-hosted PDF: https://legacy.ched.gov.ph/wp-content/uploads/2017/10/CMO-no.-25-s.-2015.pdf
- ACM/AIS IS2020 (read): https://is2020.hosting2.acm.org/wp-content/uploads/2021/06/is2020.pdf
- ACM/AIS IS 2010 (**not read**; HTTP 403 at `acm.org` and the AIS eLibrary PDF, withdrawn at USF): https://www.acm.org/binaries/content/assets/education/curricula-recommendations/is-2010-acm-final.pdf
- RA 10364 (Lawphil; part read through a summary): https://lawphil.net/statutes/repacts/ra2013/ra_10364_2013.html
- Earlier evidence (CIIT, ISU, UC, PCU, UE, Malayan Colleges Mindanao, NwSSU): see the earlier audit.
- **Limits:** IS 2010.1 not read; the RA 10364 approval line was not read verbatim; the two historical-fact checks in the other five Notes and the trafficking-protocol date were not source-checked; the 141 text-screened Notes were not read; evidence class S3 rows rest partly on the strategist's candidate list; IS2020 is not a Philippine syllabus.
