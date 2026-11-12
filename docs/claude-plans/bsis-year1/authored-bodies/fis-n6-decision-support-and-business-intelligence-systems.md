Status: ACADEMICALLY APPROVED FOR PRE-PRODUCTION PREPARATION (owner decision 2026-11-06, combined review of the twelve FIS Notes; targeted corrections applied where listed in `../bsis-year1-fis-preproduction-readiness.md`). Document-only. NOTHING here is in production. Creating the Note, setting its metadata and generating its Study Pack are owner actions after separate approvals. Domain Context is provisional and reuse flags remain subject to the final taxonomy review.

# N6: Decision Support and Business Intelligence Systems: authored body

Approved specification: `../bsis-year1-fis-authoring-specifications.md`. Batch 2 quality assessment: `../bsis-year1-fis-batch2-review.md`. Authoring standard: N1 and N2 (approved references).

## Metadata (provisional; Domain Context under per-Note review)

| Field | Value |
|---|---|
| Title | Decision Support and Business Intelligence Systems |
| Plan / section / order | 🏢 Fundamentals of Information Systems (Second Semester) / 3 Kinds of Information Systems / 7 |
| Subject | Fundamentals of Information Systems |
| Domain Context | **unset, provisionally** (assessment in the Batch 2 review) |
| Authored Depth | COLLEGE |
| Applicable Programs | Information Systems (the only program, which is what makes an unset Domain Context legal) |
| Reuse flag | `none` |
| Status | New |

## Body (copy the contents of the fenced block, without the ``` lines)

```text
Decision Support and Business Intelligence Systems

📘 Overview
Decision support systems help people explore data and compare options for decisions that are not fully routine. Business intelligence tools help organizations analyze and monitor performance, supporting both routine and non-routine decisions. Both bring together data from inside and outside the organization, apply analytical models and present the results in forms that people can explore, so that people remain the decision makers.

🧠 Key Idea
Decision support and business intelligence systems support judgment; they do not replace it. Decision support systems are especially useful when decisions require judgment and comparison of alternatives, while business intelligence can also support routine performance monitoring and reporting.

⚔️ Core Details:
- A decision support system helps people make decisions that are not fully routine. It organizes relevant data and analysis for the decision maker, and the decision maker chooses.
- Such a system usually combines data (from internal records and sometimes external sources), analytical models (such as a forecast or a cost calculation) and a way for users to interact with both, for example by changing an assumption and seeing the result.
- Business intelligence refers to the tools and practices that collect, combine, analyze and present business data so that people can understand performance and decide better. A common form is an interactive dashboard that lets users compare results and look more closely at a figure that stands out. Business intelligence can support routine monitoring of performance as well as less routine questions, and the two kinds of system overlap: one dashboard may track routine measures and also let a manager test a scenario.
- Consider a fictional furniture retail chain deciding how large a discount to offer before a seasonal sale. A manager compares past sales by region and product, tests several discount levels with a forecasting model (a what-if analysis) and looks more closely at the regions where sales fell short.
- Typical users are managers and analysts. Their questions are often new or specific, such as what would happen if a price changed. A standard periodic report answers questions that were defined in advance; these systems let users go further by exploring the data and comparing options.
- The same ideas serve many functions: a finance team can test how a change in interest rates would affect loan repayments, a marketing team can compare customer groups, a human resources team can examine staffing levels and an operations team can compare supplier delivery times.
- The decision maker stays responsible. The results depend on the quality of the data and the assumptions in the models, so people check whether the results make sense before acting on them.
- Decision support helps a person decide; automated decision-making, in contrast, carries out choices without case-by-case human approval.

🎯 Why It Matters:
- Many decisions are not routine, so people need to explore data and test options rather than read a fixed result.
- What-if analysis lets decision makers see the likely consequences of choices before committing resources.
- Keeping the person at the center explains why data quality and sensible assumptions matter, and why these systems support managers rather than replace them.
- Understanding this kind of system prepares for the enterprise-wide systems and the strategic uses of information that follow.

🧠 Quick Recall:
- Decision support system — an information system that helps people make non-routine decisions by combining data, analytical models and interactive tools, while the person decides.
- Business intelligence — the tools and practices that collect, combine, analyze and present business data to help organizations analyze and monitor performance and decide.
- Dashboard — an interactive display of key business measures that users can compare and explore in more detail.
- What-if analysis — testing how a result would change if an assumption or a decision changed.
- Decision support versus automated decision-making — decision support helps a person decide; automated decision-making carries out choices without case-by-case human approval.
```

Length: **4,266 characters**, 658 words (soft guideline; approved N1 is 4,440 and N2 5,498).

## Coverage of the approved scope

| Requirement | Where it is met |
|---|---|
| Purpose, users, inputs, outputs, information need, examples | Core Details 1 to 6 (managers and analysts; internal and external data plus models; analyses, what-if results, dashboards; non-routine questions; examples across functions) |
| Business intelligence | Core Details 3; Quick Recall |
| The person decides | Key Idea; Core Details 1, 7 and 8 |
| Same definition of decision support as the existing AI Note | Core Details 8 (decision support helps a person decide; automated decision-making acts without case-by-case human approval) |
| One consistent example | Fictional furniture retail chain |
| Examples across business functions (merged N10) | Core Details 6 (finance, marketing, human resources, operations) |
| Distinct from N8 and N5 | No decision types or stages taught; routine transaction answers and standard periodic reports appear only as a contrast in Core Details 5 |
| No AI oversight, data-science methods or data warehouse design | None present |

## Defect-class self-check (FACT, run on the text above)
- All five section headers present; 17 bullets in total, five in Quick Recall; no sentence over 45 words.
- No scope-disclaimer sentence (screened: "this note does not", "beyond the scope", "not covered", "is outside", "out of scope", "not discussed", "will not cover", "excluded").
- No LaTeX or `$`; no real-company name or statistic; one fictional example used consistently.
- No AI-oversight, machine-learning, data-science, data-warehouse or report-type content (screened); the single mention of a standard periodic report is a contrast, not teaching.
