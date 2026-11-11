Status: calibration review for N1 and N2, 2026-11-06, for owner and Note Strategist review. Document-only: no Note or Study Pack created, no production write, no SQL executed, nothing committed. N3 to N13 are NOT drafted.

> **Update 2026-11-06 (revision 2):** the owner approved N1 and N2 with targeted edits. The drafts changed (N1 4,440 characters, N2 5,498), the N2 reuse flag is now `candidate (low), not approved`, the definitions of data and knowledge and the seasonal example changed, and the global-value-chain and bits-bridge options were decided (not added). **Where this file differs, `bsis-year1-fis-calibration-revision-log.md` wins.** The source notes in section 1 and the overlap findings in section 2 remain valid.

# BSIS Year 1: Fundamentals of IS, calibration review (N1 and N2)

Labels: **FACT** (read in a source or production, or tested), **INFERENCE** (my reading), **PROPOSAL**.

Drafts: `authored-bodies/fis-n1-information-systems-as-socio-technical-systems.md` and `authored-bodies/fis-n2-data-information-and-knowledge-in-organizations.md`.

---

## 1. Academic source notes

### 1.1 Named frameworks (verified against the primary text, not summaries)

| Framework | What the Note uses | Verification |
|---|---|---|
| **Rayport and Sviokla, *Exploiting the Virtual Value Chain*** (Harvard Business Review, November-December 1995; the HBR store lists publication date 1 November 1995, item 95610, 11 pages; library catalogs give volume 73, issue 6, pages 75 to 85) | Five activities: gather, organize, select, synthesize, distribute | **FACT.** The HBR product page's own description says: *"Creating value in any stage of a virtual value chain involves a sequence of five activities: gathering, organizing, selecting, synthesizing, and distributing information."* It also frames "a physical world of resources that managers can see and touch and a virtual world made of information", which the Note's "runs alongside the physical value chain" reflects. **Limits:** I read the abstract, not the article. The volume, issue and page numbers come from library-catalog search results, not the HBR page. Some secondary summaries use "analysis" for "synthesize"; the primary abstract says synthesizing. The one-line glosses for each activity ("collect raw information", "arrange it so it can be used", and so on) are **my plain-language wording**, not quotations |
| **Wang and Strong, *Beyond Accuracy: What Data Quality Means to Data Consumers*** (*Journal of Management Information Systems* 12(4), Spring 1996, starting page 5) | Four categories: intrinsic, contextual, representational, accessibility | **FACT, read in full text** (MIT TDQM copy; the first fetch gave the text verbatim to character 39,381 and I read the remainder in a second fetch; tables and figures are images and were not readable). The Summary and Conclusions state: *"(1) intrinsic DQ consists of accuracy, objectivity, believability, and reputation; (2) contextual DQ consists of value-added, relevancy, timeliness, completeness, and appropriate amount of data; (3) representational DQ consists of interpretability, ease of understanding, representational consistency, and concise representation; and (4) accessibility DQ consists of accessibility and access security."* That is 15 dimensions in four categories, though the same Summary says 118 attributes were first consolidated into **twenty** dimensions; the Note therefore **states no count of dimensions**. The paper says representational DQ covers format and meaning, and that "the system must be accessible but secure", which the Note's "obtainable by authorized users and not by others" reflects. **Limits:** the end page (I recall 33) is **not shown on the page and is from memory**; the paper's own wording is **"data quality" and "data consumers"**, while the Note and IS2020 speak of **information quality** (see 1.3) |

**A reference correction (FACT).** Your brief cites the 1996 paper as *Beyond Accuracy: What Data Quality Means to **Information** Consumers*. The paper's title is *...to **Data** Consumers* (search results and the page's own metadata agree). The drafts cite the authors and framework by name only, so no learner-facing text is affected; correct the reference in the decision log and any citation list.

### 1.2 Curriculum references

| Claim in the drafts | Source | Label |
|---|---|---|
| An information system's components are people, processes, data and technology; the processing cycle is input, processing, storage, output, control; IS functions facilitate organizational change | IS2020 A3.1.1 C1 (printed p. 98): components of IS, technology (hardware, software, communication media), data, people, procedures/processes (Understand); operations of IS, the processing cycle of input, processing, storage, output, control (Apply) | FACT (read from the PDF) |
| Information quality has dimensions that can be applied; the virtual value chain; the value of information | IS2020 C2 (p. 98): dimensions of information quality and evaluation methods (Apply); the virtual value chain (Understand); C4 (p. 99): IS and the value of information (Understand) | FACT |
| The IS course is about organizations' use of technology | CHED CMO 25 s.2015 IS101 outcome and BSIS description | FACT (read from the CHED-hosted PDF) |
| **IS 2010.1** | Not read (HTTP 403); **nothing in either draft or in these notes is attributed to it** | FACT (a limitation) |

### 1.3 Other statements in the drafts (no single primary source was read; standard IS-textbook content)

| Statement | Basis | Label and action |
|---|---|---|
| Definitions of data, information and knowledge | The widely used textbook formulations (data as recorded facts; information as data organized for a purpose; knowledge as information combined with experience and understanding so people can act or judge). **IS2020 does not define them**, and I read no textbook this session | **INFERENCE.** Owner review item: confirm against the course textbook or a cited source (for knowledge, Davenport and Prusak, *Working Knowledge*, 1998, is the usual reference; **not read here**) |
| "Socio-technical" | A standard systems term whose origin is in work at the Tavistock Institute | **INFERENCE** (origin from memory; **the Note defines the term in plain words and cites no originator**). **The draft does not sort the four components into social and technical sides:** IS2020 lists the components without assigning sides, authors differ on the split (from memory and **unverified**, Bostrom and Heinen, 1977, place tasks in the technical subsystem), and the specification asks for one named framework per concept, so an unsourced assignment would be an unattributed choice and a likely quiz trap. The Overview and Quick Recall say only that results depend on social and technical elements working together |
| Control compares output with a standard and uses feedback to trigger corrective action | Standard systems-and-control framing; IS2020 lists "control" as the fifth stage of the processing cycle but does not elaborate | **INFERENCE** for the elaboration; the stage list is FACT |
| Information has costs, so more is not automatically better | Standard point; IS2020 C4 lists "IS and the value of information" without stating this | **INFERENCE** |
| *(Removed from the default draft)* "…is not the same as a global value chain of production across countries" | I first added this to prevent confusion with the *Global value chain* defined in *Global Economy and International Interdependence* (The Contemporary World). **It names a concept the Note does not teach, which is the scope-disclaimer pattern the specification bans** (and a quiz could pick it up), so it is out of the default draft | **PROPOSAL, optional addition** if the owner wants the clarification (it would add about 120 characters) |

**Where the drafts keep citations (as the owner asked):** learner-facing text names Wang and Strong and Rayport and Sviokla once each as the source of a framework and cites nothing else; all other source detail lives here.

---

## 2. Canonical overlap assessment

Existing Notes below were **read in full today** from production bodies (the Introduction to Computing Notes in the earlier pass; the Notes marked * in this pass).

### 2.1 N1

| Existing Note | What it teaches | Overlap with N1 | Finding |
|---|---|---|---|
| ***Components of a Computer System*** (Introduction to Computing) | "A computer system combines hardware and software to accept input, process data, store information, and produce output"; the components are processor, memory, storage, I/O devices, interconnections, system software | **Real vocabulary overlap.** The input, process, store, output idea appears here at the **computer-system** level, and "components" means hardware here | N1 applies the cycle at the level of an **organizational activity**, adds **control and feedback** (absent from that Note), and says in plain words that its four components belong to an information system and **not** to a computer system. Distinct; **reuse insufficient**. **Owner review:** confirm the explicit contrast sentence reads clearly for a learner who just studied "components of a computer system" |
| *The Computing Disciplines…* | IS is one bullet ("aligns information, people, processes, and technology with organizational goals through requirements analysis, process modeling, and system selection") | N1 expands the people, processes, data and technology view the bullet names | Consistent; N1 builds on it and does not contradict it |
| *System Software and Application Software*, *Input, Output Devices and Buses* | Software classes; devices | None beyond the single phrase "hardware, software and networks" | None |
| *Analyzing Organizational Computing Solutions* | Evaluating and selecting a solution | None (N1 does not evaluate solutions) | None |
| *The Computing Industry, Professions and Careers* | Careers | None (stakeholder roles are N3) | None |

**N1 verdict (INFERENCE):** distinct, with one managed vocabulary overlap. No existing canonical Note provides equivalent coverage.

### 2.2 N2

| Existing Note | What it teaches | Overlap with N2 | Finding |
|---|---|---|---|
| ***Critical Reading and Evaluating Sources*** (Purposive Communication) | Source credibility; evidence evaluated for "relevance, sufficiency, accuracy, and representativeness"; "Currency — the timeliness of information relative to the claim and its intended use" | **Shared vocabulary** (relevance, accuracy, timeliness) in a different setting: judging **sources and claims** for communication | N2's meaning of timeliness (suitable for the use, for example arriving before an order cutoff) is **consistent** with that Note's "relative to the claim and its intended use". Different framework and purpose (organizational fitness for use); distinct; no contradiction |
| ***Bits, Bytes and Units of Information*** (Introduction to Computing) | "Information quantities are measured by counting bits"; bit patterns acquire meaning through an encoding | **Terminology tension:** that Note uses "information" for a quantity measured in bits; N2 defines information as **data organized to be meaningful for a purpose** | Not a defect (different fields use the word differently), but a learner who has read both could be confused. **Owner review item** with an optional bridge (section 4, item 8). Not added by default, to keep N2 on organizational information |
| ***Input, Output Devices and Buses*** | "Input devices convert external information into computer-readable data" | Loose use of "information" for pre-digital content | Minor; same note as above |
| ***Data Interpretation and Graphical Reasoning*** (Mathematics in the Modern World) | Extracting "meaningful information" from graphical data | Uses data and information in the same sense as N2 | Consistent |
| ***Global Economy and International Interdependence*** (The Contemporary World) | **Global value chain**: a production network with value-adding activities across countries | Same words "value chain", **different concept** | The default N2 draft says nothing about it (a one-clause contrast is offered as an optional addition, section 1.3); the two concepts are distinct and nothing in N2 contradicts the Contemporary World Note |
| *The Computing Disciplines…*, *Cloud, IoT and Big Data*, *Analyzing Organizational Computing Solutions* | IS as a discipline; technologies; solution evaluation | None | None |

**N2 verdict (INFERENCE):** distinct. No existing canonical Note teaches data versus information versus knowledge, an information-quality framework, information value, or the virtual value chain. No database or data-management Note exists in this plan to conflict with ("organize" is explained in plain words).

---

## 3. Per-Note Domain Context assessment (ADR-001)

Rule applied (FACT): unset (Information Systems fallback) for organizational or business-process treatment; `COMPUTING` only for broadly transferable computing knowledge; plan membership never decides; unset is legal only with exactly one Applicable Program; no new value; not chosen for Bulk Generate convenience.

### N1: provisional **unset**, holds

- **Why the authored treatment belongs in FIS.** It teaches the *information system* as an organizational, socio-technical system (people, procedures, data, technology serving an organization's activity, checked by control) and the **distinction between an information system and the technology within it**. That framing is the Information Systems discipline's own (CHED describes BSIS as the application and effect of information technology on organizations).
- **Could BSCS or BSIT learners reuse the exact content unchanged?** *Mostly plausible for BSIT* (the IS-versus-IT point and the organizational example are relevant to it); *marginal for BSCS*. The one sentence that **contrasts an information system with a computer system** and the organizational example are IS-framed, so the Note is **not** broadly transferable computing knowledge as written.
- **Does the proposed Domain Context remain appropriate?** **Yes: unset with Information Systems as the only program.**
- **Equivalent canonical coverage?** None (section 2.1).
- **Reuse flag (corrected): `reuse_flag = candidate (low)`.** The owner's rule is that a Note suitable unchanged for BSCS or BSIT is flagged for reuse and taxonomy review. N1 is **plausibly** suitable unchanged for BSIT (the information-system-versus-IT point and the organizational example both fit an IT learner), so it is flagged. It is **not** recommended for reuse now: the sentence contrasting an information system with a computer system and the organizational framing are IS-framed. **Domain Context stays unset pending the owner's decision.** If the owner later wants N1 in a BSIT plan, it needs an **explicit Domain Context first** (the hard rule that an unset value is legal only with one program), a separate decision.

### N2: provisional **unset**, holds, with a recorded ambiguity

- **Why the authored treatment belongs in FIS.** The weight is **organizational**: information quality as fitness for users' purposes, the organizational cost and value of information, and the virtual value chain (a business-strategy concept). The examples are an organization's stock report and reorder list.
- **Could BSCS or BSIT learners reuse the exact content unchanged?** **The first half could** (data, information and knowledge definitions, and the quality categories are generic enough for any computing learner). **The second half could not** (the value discussion and the virtual value chain are organizational). **The Note as a unit fails the "unchanged for a CS or IT learner" test** because of the second half and the organizational examples.
- **Does the proposed Domain Context remain appropriate?** **Yes: unset with Information Systems as the only program.** This is the flagged case: the definitions half is the part most likely to prompt a `COMPUTING` argument.
- **Equivalent canonical coverage?** None (section 2.2).
- **Ambiguity flagged for the owner (moderate).** If the owner wants the generic definitions reusable elsewhere, the correct response is a **separate, smaller Note** (an explicit decision), not reclassifying N2. `reuse_flag = none`.
- **No new Domain Context value is needed or proposed.**

---

## 4. Calibration quality checklist

**PASS** = verified by me now; **OWNER REVIEW** = needs the owner's judgment before generation. Nothing below is a blocker I found, but items 1 to 4 should be decided before the Study Pack step.

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | **Length** (soft guideline 2,200 to 3,200; existing Notes about 2,300 to 3,500) | **OWNER REVIEW.** N1 **3,966** characters (609 words); N2 **4,701** (760 words). Both exceed the guideline and the existing Notes; both are far under the 12,000 cap | The required content is dense. I tightened wording once and kept every required element. **Trim candidates (not applied):** N1: the "In that example..." mapping bullet (about 240 characters) and one Why It Matters bullet; N2: the "Poor quality has costs" bullet (about 240) or the same-data-different-information bullet (about 200). Trimming these would bring N1 to roughly 3,700 and N2 to roughly 4,300, still above the guideline |
| 2 | Structure: Overview, Key Idea, Core Details, Why It Matters, Quick Recall; five Quick Recall bullets | **PASS** | Screened on the text |
| 3 | Every owner requirement for N1 and N2 present | **PASS** | Coverage tables in each draft |
| 4 | **Named frameworks match the primary sources** | **PASS** for the five Rayport and Sviokla activities and the four Wang and Strong categories (section 1.1). **OWNER REVIEW:** the Wang and Strong paper speaks of *data* quality and *data consumers*; the Note says "information quality" with their categories (aligned to IS2020's wording) | Section 1.1 |
| 5 | Examples fictional; no real-company names or statistics | **PASS.** **OWNER REVIEW (minor):** N2 says a manager "knows from experience that cough medicine sells faster at the start of the rainy season". It is attributed to a fictional manager but reads as a factual claim about demand. **Option:** replace with a neutral example such as demand rising whenever a nearby clinic holds an outreach day | Draft N2 bullet 3 |
| 6 | No database design or advanced data management (N2); no hardware or software lesson repeated (N1) | **PASS** | Term screens; N1 mentions technology in one phrase |
| 7 | No scope-disclaimer sentence; no program-audience wording; no raw LaTeX | **PASS** | Screens |
| 8 | **Terminology bridge** to *Bits, Bytes and Units of Information* ("information quantities are measured by counting bits") | **OWNER REVIEW (optional).** **Proposed bridge, not included:** add to the Data bullet "(a computer stores them as bits)" and, if wanted, one sentence "A count of bits measures how much data there is, not how meaningful it is" | Section 2.2 |
| 9 | N1 versus *Components of a Computer System* vocabulary | **OWNER REVIEW (low).** N1 says explicitly that its components belong to an information system, not a computer system; confirm it reads clearly | Section 2.1 |
| 10 | Definitions of data, information and knowledge | **OWNER REVIEW.** Standard textbook formulations; **not verified against a specific source this session** (IS2020 does not define them). Confirm against the course textbook | Section 1.3 |
| 11 | Optional clause contrasting the virtual value chain with a global value chain | **OWNER REVIEW (optional addition, NOT in the default draft).** Removed because it names an untaught concept (scope-disclaimer pattern); the owner may add it back if confusion with the Contemporary World Note is a concern | Section 1.3 |
| 12 | Domain Context and reuse flag for N1 and N2 | **OWNER REVIEW.** N1: unset, `reuse_flag = candidate (low)`. N2: unset, `reuse_flag = none`, moderate ambiguity (the definitions half is generic; the Note as a unit is not). Both decisions are the owner's | Section 3 |
| 13 | IS 2010.1 | Not read; **nothing is attributed to it** | Section 1.2 |
| 15 | N1 does not assign people, processes, data and technology to social or technical sides | **PASS (by design).** An unsourced assignment would break the one-named-framework rule | Section 1.3 |
| 14 | Wang and Strong end page (5 to 33) | **OWNER REVIEW (trivial).** Start page 5 is on the source page; the end page is from memory; verify before it appears in any citation list | Section 1.1 |

### Study Pack readiness (nothing generated)

The drafts are written so that a Study Pack of five items can be generated accurately. **Statements each Note supports (candidate quiz facts, not questions):**

- **N1:** the four components and what each means; an information system is not the same as the technology component; the five stages of the processing cycle in order; control compares output with a standard and feedback triggers corrective action; the enrollment office mapped to components and stages; a technology change requires matching changes in procedures, training and data; why a system with good technology can still fail.
- **N2:** the definitions of data, information and knowledge with the pharmacy examples; the same data can become different information for different users; information quality is fitness for the users' purposes; the four Wang and Strong categories and the pharmacy example for each; wrong, late or irrelevant information has costs; information has costs as well as value; the five virtual value chain activities in order with the pharmacy example.

**Avoid asking (not supported, or ambiguous):** the number of Wang and Strong dimensions or the full list of dimensions (the Note names four categories and a few examples only); how a bit count relates to information quality; any statistic about demand or enrollment; which component is "social" and which is "technical" (the draft does not assign them to sides); who first proposed "socio-technical". **Scenario items available:** identify the component that failed in a described breakdown; place a described activity in the processing cycle or the control stage; classify a described flaw in a report as a quality category; place a described pharmacy activity in the virtual value chain. **Key-position guidance:** with five items, vary the correct option across positions, avoid a fixed key position, and avoid "all of the above"; the earlier *Probability and Basic Statistics* pattern (all keys in position A) is the thing to avoid.

---

## 5. What I changed in local preparation artifacts

- **Workbook:** regenerated with the owner-approved root description and checked for parity with the TSV (section 6). The TSV is unchanged (it carries the Subject Plan title "🏢 Fundamentals of Information Systems", which the owner approved).
- **Decision log:** D-16 to D-21 added.
- **Not touched:** production, N3 to N13, the SQL scripts (queued below), existing Notes, Study Packs.

## 6. Queued for the next approval (approved in principle, not done in this task)

Preparation of the guarded D1 correction (*Probability and Basic Statistics*, after verifying NoteLib's supported math-delimiter syntax; the Study Pack is **not** regenerated and the answer-position pattern is recorded for later quiz-quality maintenance), the D2 wording check (whether a single quiz item can be edited without regenerating the pack), the SQL dry-run inspection, and the 11 pending shared-Note spot-checks. None was started here because this task's deliverables and stop conditions are N1 and N2 only.
