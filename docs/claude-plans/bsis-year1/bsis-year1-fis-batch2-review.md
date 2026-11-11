Status: Batch 2 quality assessment (N6, N9, N11, N7, N12) and Batch 1 closure, 2026-11-06. Document-only: no Note or Study Pack created, no production write, no SQL executed, nothing committed. Batch 3 (N5, N13) is NOT drafted.

# BSIS Year 1: Fundamentals of IS, Batch 2 quality assessment

Drafts: `authored-bodies/fis-n6-…`, `fis-n9-…`, `fis-n11-…`, `fis-n7-…`, `fis-n12-…`. Standard: N1 and N2 (approved references). Review workflow: proportional to academic risk (Level 1 routine, Level 2 named frameworks, Level 3 high-risk). Labels: **FACT**, **INFERENCE**, **PROPOSAL**.

## 0. Batch 1 closure

- **N3 and N8: approved, no revisions.** The cautious wording "A model based on Herbert Simon's work describes decision making in four stages…" is retained unchanged.
- **N4: both corrections applied** (`authored-bodies/fis-n4-transaction-processing-systems.md`, now 4,598 characters).
  - **A (completion):** the completion bullet now reads as the owner specified; receipt issuance is no longer presented as universal ("the system **may** finalize a sale after payment is accepted and the sale is recorded, then issue a receipt as confirmation… staff follow the store's correction procedure rather than silently removing the record").
  - **B (online versus batch):** the Key Idea now reads "captures business events as they occur or collects them for later processing, checks and records them according to business rules, and provides a reliable record of daily operations."
  - **Consistency edits (editorial):** the grocery example now has the cashier take payment as input and records the sale once payment is accepted, then prints the receipt, matching correction A; the correction bullet was shortened to avoid repeating correction A and now refers to "a correction procedure"; the Quick Recall definition of a transaction processing system now matches the revised Key Idea. Online and batch processing remain explained in Core Details 6.
  - **Re-checked:** five Quick Recall bullets; no database-mechanics, report or dashboard terms; no sentence over 45 words; the receipt is nowhere a universal condition.
- **Batch 1 is closed.**

## 1. Summary

| Note | Chars | Words | Review level of key claims | Recommendation |
|---|---|---|---|---|
| **N6** Decision Support and Business Intelligence Systems | 3,853 | 605 | Level 1 | **APPROVE** |
| **N9** Enterprise-Wide Systems: ERP, CRM and Supply Chain Systems | 4,025 | 607 | Level 1 | **APPROVE** |
| **N11** Information Systems and Organizational Strategy | 3,814 | 578 | **Level 2** (Porter's generic strategies) | **APPROVE** |
| **N7** Information Systems and Technology-Enabled Change in Enterprises | 3,403 | 536 | Level 1 | **APPROVE** |
| **N12** The Development Life Cycle of an Information System | 4,391 | 713 | **Level 2** (life-cycle stage models, development approaches) | **APPROVE**, with one recorded taxonomy flag (not a blocker) |

All five keep one fictional example each (furniture retail chain, bicycle manufacturer, courier company, regional bank, community water cooperative), five Quick Recall bullets, the five-section format, no sentence over 45 words, no scope-disclaimer sentence, no statistics and no real-company names. **No BLOCKER was found.** No Level 3 issue arose (no legal, medical, statistical or disputed-fact content).

## 2. Quality assessment per Note

### N6: Decision Support and Business Intelligence Systems
- **Learning objective met:** decision support and BI help people analyze data for non-routine decisions; purpose, users, inputs, outputs, examples across functions (the merged N10 coverage), and the person stays the decision maker.
- **Basis:** IS2020 C4 decision support systems (Understand) and C6 business intelligence within enterprise systems; **Level 1.** Definitions of "business intelligence" vary across texts, so the Note uses a deliberately broad one ("the tools and practices that collect, combine, analyze and present business data…"). Recorded, not blocking.
- **Distinctiveness:** from **N8**, the Note teaches the system and never re-teaches decision types or stages; from **N5**, reports appear only as a contrast ("unlike … a standard periodic report"). The Note's decision-support definition matches the existing *Artificial Intelligence and Human Judgment* Note ("recommends options to a human decision-maker; automated decision-making executes choices without case-by-case human approval") and teaches no AI oversight.
- **Editorial fixes made while drafting:** none needed after the first screen.
- **Watch item for Batch 3:** N6 defines a *dashboard* as an interactive display for exploration. **N5 must keep to periodic, exception and on-demand reports and avoid interactive dashboards**, so the two stay distinct.

### N9: Enterprise-Wide Systems: ERP, CRM and Supply Chain Systems
- **Learning objective met:** the organizational purpose of integrated, shared-data systems; ERP at proper depth, CRM and supply chain as short introductory examples; benefits and challenges; link to transaction processing.
- **Basis:** IS2020 C6 (enterprise systems, ERP) and standard definitions; CHED lists these as electives, so the Note stays introductory. **Level 1.**
- **Distinctiveness:** no software configuration, enterprise architecture, process modelling notation, implementation project or BI technique (screened). The challenges sentence mentions fitting procedures and training once; **N13** owns adoption in depth.
- **Editorial fixes:** none needed.

### N11: Information Systems and Organizational Strategy
- **Learning objective met:** *why* information systems create value and support strategy; efficiency, effectiveness and new capabilities; IT-business alignment; a planning cycle at overview; competitive advantage with one named framework; technology alone does not create advantage.
- **Level 2 verification (done, proportionate):** Porter's three generic strategies, **cost leadership, differentiation and focus**, are consistently described across the sources found (usually dated 1980; one course source says 1979, so **the Note gives no year**). The Note attributes only "Michael Porter described three generic ways to compete" and does **not** claim the strategies are mutually exclusive (the sources note Porter argued they conflict while later research is mixed). **Evidence limit:** web summaries and a university course PDF, not the original text; the framework is stable enough for an introductory statement.
- **Distinctiveness from N7 (the owner's gate), held:** N11 describes no change in operations, products, services or ways of working; its examples show systems *supporting a strategy*. The "strategic planning for information systems can be seen as a cycle" bullet is a simplification and is worded as such.
- **Editorial fix made:** a 49-word sentence on the three sources of value was split into short sentences.

### N7: Information Systems and Technology-Enabled Change in Enterprises
- **Learning objective met:** *how* information systems enable change in four areas (operations, products, services, ways of working), with digital business at overview and the links among the areas.
- **Basis:** the CHED IS101 outcome ("driving development and changes in enterprises") and IS2020 C1 (impact on organizational change); **Level 1.** "Digital business" is defined generically because definitions vary.
- **Distinctiveness from N11 (held):** no value, strategy or advantage content (screened: "strategy" and "competitive advantage" do not appear). It re-describes no technology (no cloud, IoT, AI or big data) and no social effects; no innovation method or business model.
- **Editorial fixes:** none needed.

### N12: The Development Life Cycle of an Information System
- **Learning objective met:** the high-level stages, plan-driven versus iterative approaches, and organizational participation at particular stages, at introductory depth.
- **Level 2 verification (done, proportionate):** life-cycle models differ in how many stages they name, and the sources found agree that planning, analysis, design, implementation and maintenance are shared across models while waterfall (plan-driven) and agile (iterative) differ in how they arrange them. The Note therefore says "life-cycle models differ in how many stages they name" and presents **one common version of seven stages**, never as *the* life cycle. **Evidence limit:** the sources found were practitioner pages, not textbooks; IS2020 C8 lists the systems development life cycle, methodologies, buy versus build and outsourcing at Understand level, which the Note respects. The stage name "construction or acquisition" is the Note's own wording.
- **The boundary with *Analyzing Organizational Computing Solutions* (held):** the Note names build, configure and vendor-supplied options and says they "are weighed before one is chosen", and **teaches no requirements technique, feasibility, fit-gap, total cost of ownership, risk assessment or weighted scoring** (screened). It points to no other Note by name. *The Computing Industry, Professions and Careers* lists software work phases in a roles context; N12 is consistent with it.
- **Boundary with N3 and N13:** participation is described **by stage** (managers approve and decide go-live; users describe work, try the system; technical staff build and support), not as role definitions, so N3 is not repeated; "implementation … training users" is the single contact with N13's scope.
- **Editorial choice made:** the acquisition bullet is worded so the Note does not refer to another Note by name; it says only that the options "are weighed before one is chosen".

## 3. Material factual concerns

**None.** Recorded without blocking: BI definitions vary (N6 uses a broad one); the life-cycle stage count varies across texts (N12 says so); Porter's framework was checked against web summaries and a course PDF, not the original; no single course textbook was read for the standard definitions of ERP, CRM, supply chain systems and the stage descriptions (INFERENCE: standard introductory content).

## 4. Canonical overlap and Domain Context and reuse

**Overlap:** none of the five makes an existing canonical Note redundant. Closest existing Notes (read in full or in Overview and Core Details): *Artificial Intelligence and Human Judgment* (N6, consistent definition), *Analyzing Organizational Computing Solutions* (N12, held distinct), *The Computing Industry, Professions and Careers* (N12 vocabulary), *Emerging Technologies and Their Social Impacts* and *Cloud, IoT and Big Data* (N7, no technology or social content repeated). Sibling boundaries held: **N6 versus N8 and N5; N9 versus N4; N11 versus N7** (including the spec's merge gate: neither body restates the other); N12 versus N3 and N13.

| | N6 | N9 | N11 | N7 | N12 |
|---|---|---|---|---|---|
| Domain Context | unset | unset | unset | unset | unset, provisionally |
| Reuse flag | `none` | `none` | `none` | `none` | **`candidate (moderate)`** |
| Ambiguity | Low | Low | Low | Low | **Moderate** |

- **N6 to N11 and N7:** organizational treatment (managerial decision support, business applications, strategy and advantage, enterprise change). A BSCS or BSIT learner would not use them unchanged. Unset holds.
- **N12 (the flagged case): unset holds provisionally, flagged for taxonomy and reuse review before publication.** The stage descriptions and the plan-driven versus iterative comparison are broadly transferable computing knowledge that a BSIT or BSCS learner could use unchanged, which is the reason for the flag. The organizational participation by stage, the acquisition and vendor context and the water-cooperative example keep the authored Note IS-leaning. Per the owner's rule it is not published IS-only until reviewed; **any change (an explicit `COMPUTING` Domain Context, more programs, or a split) is an owner decision and is not needed now.** No new Domain Context value is proposed.

## 5. Study Pack readiness (nothing generated)

Mix per Note: two conceptual, two application or scenario with a **new** equivalent example, one distinction or misconception; one best answer; varied key positions. Slots:

- **N6:** (1) what a decision support system does for a decision maker; (2) what business intelligence is, and what a dashboard allows; (3) a new scenario (a fictional hotel or bakery) classifying a need as decision support rather than a routine transaction or report; (4) a new what-if scenario; (5) misconception: "decision support makes the decision" or confusing it with automated decision-making. *Avoid asking:* analytics or AI techniques.
- **N9:** (1) the purpose of enterprise-wide systems and the problem of separate departmental data; (2) what an ERP module is; (3) a new scenario of duplicate or conflicting records and the benefit of integration; (4) match a described need to ERP, CRM or a supply chain system; (5) misconception: "an ERP system is only a finance system" or "each department should keep its own copy". *Avoid asking:* configuration or architecture.
- **N11:** (1) the three ways systems create value; (2) what IT-business alignment means; (3) a new scenario classifying a system as supporting cost leadership, differentiation or focus (clear cases); (4) a new scenario on why a widely copied feature stops giving advantage; (5) misconception: "buying the newest technology guarantees advantage". *Avoid asking:* Porter's dates or other Porter frameworks.
- **N7:** (1) the four areas of change; (2) what digital business means; (3) a new scenario classifying a change by area (one clear area each); (4) a new scenario showing how a change in one area prompts changes in another; (5) misconception: "installing a system is the same as technology-enabled change". *Avoid asking:* any specific technology.
- **N12:** (1) the stages in order and what one stage does; (2) plan-driven versus iterative; (3) a new scenario placing an activity in the right stage; (4) a new scenario choosing the approach that fits clear and stable needs versus changing needs; (5) misconception: "the life cycle ends at go-live" or "one approach is always better". *Avoid asking:* the number of stages in other models, requirements techniques or evaluation methods.

## 6. Recommendation

**APPROVE all five** (N12 with its recorded taxonomy flag). Nothing needs owner approval to proceed. **The next decision** is whether to authorize **Batch 3 (N5 and N13)**. Notes for that batch: N5 must stay on periodic, exception and on-demand reports and avoid the interactive dashboards and what-if analysis taught in N6, and must remain distinguishable from N4 and N6 or be merged into N4 (the spec's gate); N13 must keep adoption, resistance and training at introductory depth, treating N9's one challenges sentence and N12's implementation sentence as contact points, not repeating them.

> **Editorial note (Batch 3 pass):** N7's closing Why It Matters bullet was reworded to differentiate it from N11's near-identical closing bullet ("Seeing change as planned and linked across areas prepares for the later topics on developing systems and on helping people adopt them."). No other Batch 2 change.
