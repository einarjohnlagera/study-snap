Status: authoring specifications for the twelve Fundamentals of IS Notes, 2026-11-06. SPECIFICATIONS ONLY: no Note body, Study Pack, metadata change or production write exists or is authorized by this file. Generation of the Notes needs the owner's next approval. Nothing is committed.

> **Update 2026-11-06 (owner decisions D-16 to D-19, see `bsis-year1-decision-log.md`):** the twelve specifications are **approved**; the character range below is a **soft guideline**, not a target; **N2 uses Wang and Strong's four quality categories** (intrinsic, contextual, representational, accessibility) and **Rayport and Sviokla's five activities** instead of the example dimension set shown in N2 below; and the **calibration pair is N1 and N2** (not N1 and N4), with N3, N8, N4, then Batch 2 and Batch 3 not authorized until the calibration drafts are approved. Where this file differs, the decision log wins.

# BSIS Year 1: Fundamentals of IS, authoring specifications

Labels: **FACT** (read in a source or in production), **INFERENCE**, **PROPOSAL**. Companion files: `bsis-year1-fis-final-shaping-and-evidence.md` (evidence, overlap, Domain Context log), `bsis-year1-phase-b-handoff.md` (QA checklist, holds, readiness), `bsis-year1-fis-plan-draft.tsv` and `docs/curriculum/bsis-year1-shaping.tsv`.

## 0. Common requirements (apply to every Note)

**Metadata.** Subject `Fundamentals of Information Systems`; Authored Depth `COLLEGE`; Applicable Programs `Information Systems` only; Status New. **Domain Context: provisionally unset** (section 2 sets the review criteria; it is not a classification rule and not decided by plan membership). No new Domain Context value. A Note that proves suitable unchanged for BSCS or BSIT is flagged for canonical-reuse and taxonomy review before publication (section 2.3).

**Body format (FACT, from the existing Notes).** Title line, then `📘 Overview`, `🧠 Key Idea`, `⚔️ Core Details:` (bullets), `🎯 Why It Matters:` (bullets), `🧠 Quick Recall:` (five term-and-definition bullets, `—` separator). Existing Notes run about 2,300 to 3,500 characters, well under the 12,000-character Study Pack cap. **Soft guideline 2,200 to 3,200 characters (not a limit; do not omit essential explanation to meet it).** Mathematics in `$...$` (these Notes should need little or none).

**Knowledge-first titles** exactly as approved; no BSIS, year or semester branding in a title.

**Quality bar (the owner's list).** Understandable to a Year 1 learner; introductory depth (IS2020 Foundations targets Understand and Apply); no unsupported claims; no excessive advanced terminology; **one named framework per concept, stated as such** (frameworks differ across textbooks, so a Note must not blend two); examples generic or fictional, never real-company statistics or dated market claims; Philippine-context examples welcome where they are generic (a school enrollment office, a retail point of sale, a bank branch, a hospital admissions desk, a BPO operation); **no scope disclaimer sentence naming topics the Note excludes** (an earlier defect class: a disclaimer in a body gives a generated quiz something to ask about).

**Study Pack quiz requirements.** Five items per Note in the existing styles (single answer; "NOT correct"; two-statement; multi-select). Every key must be **answerable from the Note's own text**, every explanation must clearly explain the correct reasoning (individual distractor explanations are NOT required: absent from the current generator, a known limitation and not a pack defect, per the owner-approved Product UX direction of 2026-11-06; misleading or ambiguous distractors remain content defects), no item may test material the body does not teach, and at least one item per Note must be a short scenario. Review every key against the body before approval, as was done for the Counting Note and the six GE Notes. Regeneration of an existing Study Pack requires explicit owner confirmation; this plan creates new packs for new Notes only.

**Evidence labels in each spec** use S1/S2/S3 from the shaping document: S1 corroborated (a CHED outcome or explicit IS2020 element plus a school source); S2 an explicit IS2020 Foundations knowledge element; S3 adjacent or inferred. IS2020 competencies are cited as C1 to C8 (A3.1.1, printed pp. 97 to 101) with Bloom levels (2 Understand, 3 Apply). **IS2020 is a competency model, not a CHED requirement, and IS 2010.1 was not read; no spec attributes wording to IS 2010.1.**

---

## 1. The twelve specifications

### 1. N1 — Information Systems as Socio-Technical Systems: People, Processes, Data and Technology
**Section 1, order 1. Evidence S1.**
- **Distinct learning objective.** Classify the components of an information system (people, processes, data, technology) and explain, using the input, processing, storage, output and control cycle, how they interact to serve an organization.
- **Basis.** IS2020 C1: components (technology, data, people, procedures/processes) 2; operations, the processing cycle of input, processing, storage, output, control 3; how IS helps deal with information 3; functions and impact 3; CHED title; CIIT description.
- **Must include (owner gate).** The **input, processing, storage, output, control (feedback) cycle**, each stage defined with one organizational example; **how people, processes, data and technology interact** (a change in one forces change in the others); what makes the system **socio-technical**; one worked organizational example traced through the cycle (for instance a school enrollment office: enrollment forms in, validation and assignment, student records stored, class lists out, a registrar's check against the capacity limit as control).
- **Must not include.** Re-teaching hardware, software, networks or operating systems (Introduction to Computing). The kinds of information system (N4 to N9). Information quality (N2).
- **Neighbours.** *The Computing Disciplines…* gives IS one bullet; N1 must build on it, not restate the disciplines. State the difference between an information system and the information technology it uses (technology is one component).
- **Quick Recall terms.** Information system; socio-technical system; the processing cycle; control (feedback); procedures/processes.
- **Quiz coverage.** Classify an element as people, process, data or technology; identify the stage in the cycle; "NOT correct" on IS versus IT; a scenario in which a system fails because a non-technology component was ignored; a multi-select of the five cycle stages.
- **Domain Context review.** Provisional unset. Expect IS-specific because the point is the organizational use of technology; reconsider only if the body reads as generic "what is a computer system" material.

### 2. N2 — Data, Information and Knowledge in Organizations
**Section 1, order 2. Evidence S2.**
- **Distinct learning objective.** Distinguish data, information and knowledge; apply a stated set of information-quality dimensions to evaluate information; and explain how organizations derive value from information, including the virtual value chain idea.
- **Basis.** IS2020 C2: dimensions of information quality and evaluation methods 3; **virtual value chain 2**; C4: IS and the value of information 2.
- **Must include (owner gate).** Data versus information versus knowledge (with one consistent example set); **information quality** using **Wang and Strong's four categories** (intrinsic, contextual, representational, accessibility; owner-approved; verified against the paper), with accessible examples; how organizations use information (operations, control, decisions); **organizational information value** (value comes from use in a decision or process, not from volume; information has costs); the **virtual value chain** at foundational depth: information gathered, organized, selected, synthesized and distributed can itself create value alongside physical activities. **Authoring check:** verify the virtual value chain stage names against a cited source before writing; if sources differ, state only the shared idea.
- **Must not include.** **Database design, data models, storage methods or normalization** (Information Management, Year 2; IS2020 lists "methods for storing and organizing information" at C4 and the plan deliberately omits it). Digital representation (bits, encoding: Introduction to Computing).
- **Neighbours.** *Bits, Bytes and Units of Information* is about representation, a different topic. N8 owns "what each organizational level needs".
- **Quick Recall terms.** Data; information; knowledge; information quality; virtual value chain.
- **Quiz coverage.** Classify an item as data, information or knowledge; match a flaw to a quality dimension; "NOT correct" on information value; a scenario applying the dimensions; a two-statement item.
- **Domain Context review (flagged for scrutiny).** Provisional unset. **Test:** the body's weight is organizational (quality for decisions, organizational value, value chain). If the written body is mostly generic definitions of data, information and knowledge that a CS learner would use unchanged, record that and reconsider `COMPUTING`.

### 3. N3 — Roles in Information Systems Practice: Users, Developers, Managers and Vendors
**Section 1, order 3. Evidence S1.**
- **Distinct learning objective.** Explain the roles and responsibilities of the people around an information system (users, developers and analysts, business and IT managers, vendors) and the characteristics expected of an IS professional.
- **Basis.** IS2020 C3: roles and responsibilities of the IS professional 3; characteristics 3; ethical standards 3. CIIT description names these roles.
- **Must include.** Each role's responsibility and typical interaction with the others (who specifies, builds, funds, supplies, uses, supports); IS professional characteristics (communication across technical and business groups, ethical practice, continuous learning); what goes wrong when a role is missing (for example no user involvement).
- **Must not include.** Career pathways and job titles (*The Computing Industry, Professions and Careers*). A full ethics treatment (Professional Issues in IS, Year 2; Introduction to Computing's ethics and society Notes). IS2020 lists "ethical standards for the IS professional" in C3: give **one or two sentences pointing to the professional codes already taught**, not a new ethics section.
- **Neighbours.** *The Computing Industry, Professions and Careers* lists roles from a careers view with *systems analyst* in Quick Recall; N3 frames roles around one system.
- **Quick Recall terms.** End user; systems analyst; IT manager; vendor; IS professional.
- **Quiz coverage.** Match a responsibility to a role; "NOT correct" on roles; a scenario of a failed project traced to a missing role; a multi-select of IS professional characteristics.
- **Domain Context review.** Provisional unset (stakeholder roles around an organizational system). Reconsider only if the body becomes a generic software-team description.

### 4. N8 — Operational, Managerial and Strategic Information Needs
**Section 2, order 4. Evidence S2 for decision making; the three-level framing is INFERENCE.**
- **Distinct learning objective.** Relate the types and stages of decision making to the information that operational, managerial and strategic levels of an organization need.
- **Basis.** IS2020 C4: role of information in decision making 2; **types of decisions and techniques 3; stages of decision making and appropriate IS tools 3**. The three-level framing is the standard textbook structure, **not named in IS2020**; the Note presents it as a framework.
- **Must include.** The three levels with typical decisions; **one named decision-type framework** (structured, semi-structured, unstructured) and **one named stage framework** (for example intelligence, design, choice, implementation), each used consistently; how information needs differ by level (detail, time horizon, frequency, internal or external source); a table-like comparison inside the body in plain bullets.
- **Must not include.** The quality dimensions (N2); the system categories that serve each level (N4 to N6; a one-line pointer only).
- **Neighbours.** *Analyzing Organizational Computing Solutions* covers requirements for a solution, not managerial information needs. **Merge gate with N2:** if both bodies spend most of their length on "the role of information in decisions", merge N8 into N2.
- **Quick Recall terms.** Operational level; managerial level; strategic level; structured decision; decision stage.
- **Quiz coverage.** Match a decision to its level; match information needs to a level; "NOT correct" on decision types; a scenario assigning a decision to a stage.
- **Domain Context review.** Provisional unset (organizational levels and decisions).

### 5. N4 — Transaction Processing Systems
**Section 3, order 5. Evidence S2 (IS2020 C1 names Transaction Processing Systems).**
- **Distinct learning objective.** Explain the purpose, users, inputs and outputs of transaction processing systems, and why accuracy and reliability matter for routine business events.
- **Differentiate by (owner gate).** **Purpose:** record and process routine business events. **Users:** operational staff (and customers at the point of use). **Inputs:** transactions. **Outputs:** updated records, receipts, confirmations. **Information need:** accurate, current record of what happened. **Examples across business functions** (the merged N10 coverage): sales order entry and point of sale (sales), payroll and invoicing (accounting), attendance and leave (human resources), goods receipt and stock updates (operations).
- **Must include.** Batch versus real-time (online) processing at a conceptual level; the characteristics of a transaction system (high volume, repetitive, accuracy, audit trail) stated plainly.
- **Must not include.** Database transaction internals (ACID properties, locking, concurrency control, recovery) and anything that reads as Information Management content; management reporting (N5); decision support (N6).
- **Neighbours.** None in production for this topic.
- **Quick Recall terms.** Transaction; transaction processing system; batch processing; real-time (online) processing; audit trail.
- **Quiz coverage.** Identify an example as a TPS function; "NOT correct" on TPS characteristics; a scenario choosing batch or real-time; a two-statement item on outputs.
- **Domain Context review (flagged for scrutiny).** Provisional unset. **Test:** if the written body leans on database-transaction mechanics, cut that content (it is out of scope). If removing it leaves a generic "how transactions are stored" Note, HOLD rather than classify it. The business-event treatment above is the IS-specific core.

### 6. N5 — Management Information and Reporting Systems
**Section 3, order 6. Evidence S3 (IS2020 does not name an MIS knowledge element; the support is the broader types-and-utility element).**
- **Distinct learning objective.** Explain how management information and reporting systems turn processed transaction data into summaries that managers use to monitor and control operations.
- **Differentiate by.** **Purpose:** monitoring and control. **Users:** middle managers. **Inputs:** data already processed by transaction systems. **Outputs:** periodic (scheduled), exception and on-demand reports. **Information need:** summaries and variances against plan. **Examples:** a monthly sales summary, an inventory reorder exception report, a weekly attendance summary.
- **Must include.** The three report types; how reports depend on the quality of the underlying transactions (a pointer to N4 and N2); the ambiguity of "MIS" (the system type versus the field of study), so a learner is not confused by the course name.
- **Must not include.** Ad hoc analysis and what-if modelling (N6); ERP integration (N9); report-writing tools or SQL.
- **Evidence honesty.** The Note must **not** claim that IS2020 or CHED names "management information systems" as a Foundations topic; it is taught as a standard category within "types and utility of information systems".
- **Gate (S3).** If the authored body cannot be distinguished from N4 and N6 by purpose, users and outputs, **merge N5 into N4** as "Transaction Processing and Management Reporting Systems" before publication.
- **Quick Recall terms.** Management information system; periodic report; exception report; on-demand report; monitoring and control.
- **Quiz coverage.** Match a report to its type; match a need to MIS rather than TPS; "NOT correct" on MIS users; a scenario.
- **Domain Context review (flagged for scrutiny).** Provisional unset. **Test:** the body is about organizational reporting for managers. If it reduces to generic report generation from a database, that is not IS-specific; HOLD and reconsider scope.

### 7. N6 — Decision Support and Business Intelligence Systems
**Section 3, order 7. Evidence S2 (IS2020 C4 decision support systems; C6 BI as part of enterprise systems).**
- **Distinct learning objective.** Explain how decision support and business intelligence systems help managers analyze data for semi-structured and unstructured decisions, and distinguish them from reporting and transaction systems.
- **Differentiate by.** **Purpose:** support analysis and non-routine decisions. **Users:** managers and analysts. **Inputs:** internal and external data plus analytical models. **Outputs:** analyses, scenarios, dashboards. **Information need:** insight for choices that have no fixed answer. **Examples:** what-if analysis of a price change, a sales dashboard comparing regions.
- **Must include.** The relationship to the decision types in N8; what BI adds (integrating data and presenting analysis); the human remains the decision maker.
- **Must not include.** AI model oversight, automation bias and automated decision-making (*Artificial Intelligence and Human Judgment*); data-science and machine-learning techniques; data warehouse design.
- **Overlap rule.** *Artificial Intelligence and Human Judgment* defines "decision support" as recommending options to a human decision maker. **Use the same definition** and cite the idea rather than re-teaching it; do not contradict it.
- **Quick Recall terms.** Decision support system; business intelligence; dashboard; what-if analysis; semi-structured decision.
- **Quiz coverage.** Match a situation to DSS rather than MIS; "NOT correct" on BI; a scenario; a two-statement item.
- **Domain Context review (flagged for scrutiny).** Provisional unset. **Test:** the body treats organizational decision support for managers. If it drifts into analytics methods a CS or data learner would use unchanged, cut it or reclassify, and record the reason.

### 8. N9 — Enterprise-Wide Systems: ERP, CRM and Supply Chain Systems
**Section 3, order 8. Evidence S2 for ERP and enterprise systems; S3 for CRM and supply chain.**
- **Distinct learning objective.** Explain how enterprise systems integrate data and processes across functions, with ERP as the central example and CRM and supply chain systems as introductory examples.
- **Differentiate by.** **Purpose:** integrate processes and shared data across the organization. **Users:** staff in many functions. **Inputs:** shared enterprise data. **Outputs:** integrated processes and consolidated information. **Information need:** one consistent view across functions. **Examples:** an order flowing through sales, inventory, finance and delivery in one system.
- **Must include (owner gate).** ERP at proper depth (modules sharing one data store, process integration, benefits and challenges such as cost, customization and change); **CRM and supply chain systems kept as introductory examples** (one short passage each, what each coordinates); the contrast with separate departmental systems.
- **Must not include.** Business process modelling notation or redesign (Business Process Management, a later course; IS2020 lists modelling and optimization at Apply, so keep to a one-line overview); implementation project management; BI techniques (N6).
- **Quick Recall terms.** Enterprise system; ERP; module; CRM; supply chain system.
- **Quiz coverage.** Identify the benefit of integration; "NOT correct" on ERP; a scenario of duplicate data in separate systems; a match of CRM versus supply chain.
- **Domain Context review.** Provisional unset (enterprise business applications).

### 9. N11 — Information Systems and Organizational Strategy
**Section 4, order 9. Evidence S2.**
- **Distinct learning objective.** Explain **why** information systems create organizational value, support organizational strategy and can contribute to competitive advantage.
- **Basis.** IS2020 C4: key IT issues, trends and alignment of IT with business 2; **IS strategic planning process 2**; information and knowledge for strategic value 2. The advanced management and strategy competencies (A3.5.2) sit beyond Year 1.
- **Must include.** Sources of value (efficiency, effectiveness, new capabilities); **IT-business alignment**; how an IS plan follows from organizational strategy (the planning process at overview); **one named introductory strategy framework** (for example cost leadership, differentiation and focus) used consistently; a caution that technology alone does not guarantee advantage.
- **Must not include.** How systems transform operations, products and services (N7); governance and service-management frameworks (COBIT, ITIL, TOGAF, CMMI); IS project management; acquisition economics (the Analyzing Note).
- **Distinct from N7 (owner gate).** N11 answers *why value and advantage*; N7 answers *how work and offerings change*. **Merge gate:** if more than about half of either authored body restates the other, merge into "Information Systems, Strategy and Technology-Enabled Change" before publication.
- **Quick Recall terms.** IT-business alignment; competitive advantage; IS strategic plan; cost leadership; differentiation.
- **Quiz coverage.** Match a system to a source of value; "NOT correct" on advantage; a scenario of misalignment; a two-statement item.
- **Domain Context review.** Provisional unset (organizational value and strategy).

### 10. N7 — Information Systems and Technology-Enabled Change in Enterprises
**Section 4, order 10. Evidence S1 (the CHED outcome plus IS2020 C1 impact on organizational change).**
- **Distinct learning objective.** Explain **how** information systems and technologies change enterprise operations, products, services and ways of working.
- **Basis.** CHED IS101 outcome; IS2020 C1: functions of IS and impact on facilitating organizational change 3.
- **Must include.** Four change areas with one generic example each: **operations** (automation, integration), **products** (digitally enabled offerings), **services** (online and mobile delivery), **ways of working** (remote and collaborative work); digital business at overview; the organizational side of the change, not the technologies.
- **Must not include.** Re-describing cloud, IoT, big data or AI (*Cloud Computing, the Internet of Things and Big Data*, *Artificial Intelligence and Human Judgment*); social effects (*Emerging Technologies and Their Social Impacts*); innovation methods and digital business models (IS2020 A3.5.3, an advanced area); the reasons systems create value (N11).
- **Quick Recall terms.** Technology-enabled change; digital business; process automation; digital service delivery; ways of working.
- **Quiz coverage.** Classify a change by area; "NOT correct" on technology-enabled change; a scenario; a multi-select of the four areas.
- **Domain Context review.** Provisional unset (organizational transformation).

### 11. N12 — The Development Life Cycle of an Information System
**Section 4, order 11. Evidence S2.**
- **Distinct learning objective.** Describe the high-level stages of developing or acquiring an information system, the plan-driven and iterative approaches, and how users and managers take part at each stage.
- **Basis.** IS2020 C8: systems development lifecycle 2; software development methodologies 2; buy versus build 2; outsourcing possibilities and models 2.
- **Must include (owner gate).** The stages at a high level (planning, analysis, design, construction or acquisition, testing, implementation, operation and maintenance) with **one named model** and a note that models vary; **development approaches** at overview (plan-driven versus iterative, with prototyping); **organizational participation** (sponsors, users, managers, IT staff at each stage). **Build-versus-buy and outsourcing appear only as brief context** at the construction or acquisition stage, in a sentence or two.
- **Must not include.** **Requirements analysis technique, feasibility analysis, fit-gap analysis, total cost of ownership, weighted evaluation or solution selection**: *Analyzing Organizational Computing Solutions* already teaches them in detail; link by idea, do not repeat. Detailed systems analysis and design (Year 2); project management.
- **Overlap rule (FACT).** The Analyzing Note's overview compares custom development, commercial software and cloud services, so any sourcing sentence in N12 must stay at the level of "this is a stage-level choice, evaluated as described in that Note".
- **Gate.** If the authored body re-teaches evaluation or requirements analysis, HOLD or reduce it to reuse of the Analyzing Note.
- **Quick Recall terms.** Systems development life cycle; plan-driven approach; iterative approach; prototype; user involvement.
- **Quiz coverage.** Put stages in order; match an approach to a situation; "NOT correct" on user involvement; a scenario.
- **Domain Context review (flagged for scrutiny).** Provisional unset. **Test:** the planned scope is developing **or acquiring** a system with organizational participation and sourcing context. If the written body is a generic software life cycle that a CS or IT learner would use unchanged, it is `COMPUTING` knowledge, and the Note should be flagged for BSCS/BSIT reuse review (section 2.3) instead of staying IS-only.

### 12. N13 — Organizational Change and Information System Adoption
**Section 4, order 12. Evidence S3 (organizational change is supported; adoption, resistance and training are instructional applications, not separate Foundations topics).**
- **Distinct learning objective.** Explain how stakeholder acceptance, resistance, training and organizational effects determine whether an introduced information system is actually used.
- **Basis.** IS2020 C1: impact on facilitating organizational change 3; C8: methods for improving processes and organizational change. Adoption, resistance and training are **not** separately named in Foundations (the workforce-training competency in A3.5.2 is advanced).
- **Must include (owner gate).** Adoption versus mere installation; common sources of resistance (habit, fear of changed roles, poor fit, inadequate training) in plain terms; training and support; communication and user involvement; organizational effects (changed roles, workflows, information flow, a temporary productivity dip) — **at introductory depth**.
- **Must not include.** Named advanced change-management frameworks, change-management certification content, workforce planning, project management; technology descriptions (N7).
- **Quick Recall terms.** Adoption; resistance to change; user training; stakeholder acceptance; organizational impact.
- **Quiz coverage.** Match a cause to resistance; "NOT correct" on adoption; a scenario choosing a response; a two-statement item.
- **Domain Context review.** Provisional unset (organizational adoption).
- **Evidence note.** Because the support is partial, the Note must teach adoption as an application of organizational impact and must not attribute adoption, resistance or training to IS2020.

---

## 2. Domain Context review criteria

### 2.1 The common test (apply to every authored Note)

Ask in order, and record the answer in the review log:
1. **Is the treatment organizational or business-process knowledge** (an information system serving an organization, its people, its decisions)? If yes, the Information Systems single-program fallback fits (unset with Information Systems as the only Applicable Program).
2. **Is the content broadly transferable computing knowledge** that a BSCS or BSIT learner would use unchanged (a generic life cycle, generic data definitions, database or transaction mechanics)? If yes for most of the body, `COMPUTING` is the candidate; do not leave it unset merely because it sits in the FIS plan.
3. **If it is mixed**, split the transferable part into a separate decision (do not smuggle it into this Note) or cut it, and record which.
4. **Never decide from plan membership**, and never to simplify Bulk Generate (one Subject and one Domain Context per run). If Notes end up with different Domain Contexts, they are generated in separate runs; that is acceptable.
5. **No new Domain Context value.**

### 2.2 Per-Note criteria (the flagged cases first)

| Note | Treated as IS-specific (unset) when | Reconsider `COMPUTING` or HOLD when |
|---|---|---|
| **N4** Transaction Processing | The body is about business events, operational users and organizational examples | It leans on ACID, locking or concurrency; cut that content. If nothing organizational remains, HOLD |
| **N5** Management Information and Reporting | The body is about managers' periodic, exception and on-demand reports and what they monitor | It reduces to generic report generation from a database; HOLD and reconsider scope; or it cannot be told apart from N4 and N6 (merge gate) |
| **N12** Development Life Cycle | The body covers developing **or acquiring** a system, organizational participation and stage-level sourcing | It is a generic software life cycle; classify `COMPUTING` and flag for BSCS/BSIT reuse and taxonomy review |
| **N2** Data, Information and Knowledge | The weight is information quality, organizational value and the virtual value chain | It is mostly generic definitions; reconsider `COMPUTING` |
| **N6** Decision Support and BI | The body is organizational decision support for managers | It becomes generic analytics method; cut or reclassify. Must not contradict the AI Note's definition of decision support |
| N1, N3, N8, N9, N11, N7, N13 | Organizational framing is the point (system in an organization, roles, levels, enterprise systems, value and strategy, change, adoption) | The body turns generic; record the reason |

### 2.3 Reuse flag (owner requirement)

If an authored Note is suitable **unchanged** for BSCS or BSIT, set `reuse_flag = candidate` in the review log and **do not publish it IS-only**: it goes to canonical-reuse and taxonomy review first (it would need an explicit Domain Context before gaining a second program, by the hard rule that an unset Domain Context is legal only with exactly one program).

### 2.4 What the shaping workbook already enforces

The workbook's Domain Context sheet shows the FIS Subject as `(unset)` with Applicable Programs `Information Systems` (one program, legal) and flags any unset row that gains a second program. If a Note's Domain Context changes at review, update its TSV row (`domain_context`) and regenerate; never edit the workbook.

---

## 3. Authoring workflow (PROPOSAL, for the owner's next approval)

1. Owner approves these specifications (and the plan title and emoji).
2. Claude drafts each Note body as an authored-body document under `docs/claude-plans/bsis-year1/authored-bodies/` (the Counting Note precedent), **not** in production; the owner creates the Note and sets its metadata, then generates its Study Pack with the usual confirmation.
3. Each draft is reviewed against section 0, the Note's spec, section 2 and the QA checklist in `bsis-year1-phase-b-handoff.md` before the owner creates it.
4. Batches (**superseded by D-18: the calibration pair is N1 and N2, and later batches await approval of those drafts**): **Batch 1** N1, N2, N3, N4, N8 (evidence S1 and S2, no merge gates); **Batch 2** N6, N9, N11, N7, N12 (S2, with the N11/N7 and N12 gates); **Batch 3** N5 and N13 (S3, merge and scope gates). A calibration draft of N1 and N4 first is recommended so the quality bar is checked on two Notes before the rest.
5. The owner's Study Pack review (keys against the body) happens before a Note joins the plan.
