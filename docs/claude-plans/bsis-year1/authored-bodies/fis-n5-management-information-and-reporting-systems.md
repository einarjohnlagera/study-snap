Status: ACADEMICALLY APPROVED FOR PRE-PRODUCTION PREPARATION (owner decision 2026-11-06, combined review of the twelve FIS Notes; targeted corrections applied where listed in `../bsis-year1-fis-preproduction-readiness.md`). Document-only. NOTHING here is in production. Creating the Note, setting its metadata and generating its Study Pack are owner actions after separate approvals. Domain Context is provisional and reuse flags remain subject to the final taxonomy review.

# N5: Management Information and Reporting Systems: authored body

Approved specification: `../bsis-year1-fis-authoring-specifications.md`. Batch 3 quality assessment and set-level consistency check: `../bsis-year1-fis-batch3-review.md`. Authoring standard: N1 and N2 (approved references).

## Metadata (provisional; Domain Context under per-Note review)

| Field | Value |
|---|---|
| Title | Management Information and Reporting Systems |
| Plan / section / order | 🏢 Fundamentals of Information Systems (Second Semester) / 3 Kinds of Information Systems / 6 |
| Subject | Fundamentals of Information Systems |
| Domain Context | **unset, provisionally** (assessment in the Batch 3 review) |
| Authored Depth | COLLEGE |
| Applicable Programs | Information Systems (the only program, which is what makes an unset Domain Context legal) |
| Reuse flag | `none` |
| Status | New |

## Body (copy the contents of the fenced block, without the ``` lines)

```text
Management Information and Reporting Systems

📘 Overview
A management information system turns the data recorded by transaction processing into summaries that managers use to monitor operations and keep them on track. Its main products are reports, which are prepared on a schedule, when something unusual occurs or when a manager asks for them. The term is also the name of a field of study, but here it means this type of system.

🧠 Key Idea
Management reports answer routine questions about how operations are performing compared with plans and expectations. They summarize what has happened so that managers can notice problems and decide where to act. Their usefulness depends on the quality of the underlying transaction data and on whether each report fits the manager who reads it.

⚔️ Core Details:
- The purpose of a management information system is monitoring and control: comparing actual performance with plans, targets or limits and showing where attention is needed.
- Its typical users are middle managers, such as branch managers, department heads and supervisors, who oversee operations rather than carry out each transaction.
- Its inputs are data already recorded by transaction processing systems, such as sales, stock levels, attendance and payments, sometimes combined with plans such as budgets or sales targets.
- Its processing is mostly summarizing, sorting, totaling and comparing, and its outputs are reports. Reports answer questions that were defined in advance, so their format is usually standard.
- A periodic report is produced on a schedule, such as a weekly sales summary by department. An exception report lists only the items that fall outside a set limit, such as products below their reorder level. An on-demand report is produced when a manager asks for it, such as one department's sales for a chosen date range.
- Consider a fictional supermarket chain with several branches. Each branch manager receives a weekly summary of sales by department compared with targets, an exception report of products below their reorder level and, when needed, an on-demand report of one department's sales for a chosen period.
- Reports depend on the records behind them. A wrong stock count in the transaction records appears in the reports built from it, so a manager can act on a wrong picture even when the report itself is neatly prepared.
- A useful report fits its reader. It shows the level of detail that the manager needs, arrives in time to act on it and is easy to read.

🎯 Why It Matters:
- Managers rarely have time to review each transaction, so summaries let them see how operations are going without checking them one by one.
- Exception reports focus attention on what needs action instead of requiring managers to search through routine results.
- Because reports inherit the quality of the transaction data, improving data entry and checks improves everything built on it.
- Understanding routine reporting prepares for the systems that support less routine decisions.

🧠 Quick Recall:
- Management information system — an information system that summarizes transaction data into reports that managers use to monitor and control operations.
- Periodic report — a report produced on a regular schedule, such as weekly.
- Exception report — a report that lists only the items falling outside a set limit.
- On-demand report — a report produced when a manager requests it.
- Monitoring and control — comparing actual performance with plans or limits to show where action is needed.
```

Length: **3,526 characters**, 576 words (soft guideline; approved N1 is 4,440 and N2 5,498).

## Coverage of the approved scope

| Requirement | Where it is met |
|---|---|
| Purpose, users, inputs, outputs, information need, examples | Core Details 1 to 6 (monitoring and control; middle managers; data from transaction processing plus targets; reports in standard formats; supermarket branch example) |
| Periodic, exception and on-demand reports | Core Details 5 and 6; Quick Recall |
| Reports inherit the quality of the transaction data | Core Details 7; Why It Matters 3 |
| The two meanings of "MIS" | Overview |
| One consistent example | Fictional supermarket chain |
| Distinct from N4 | Records and processes nothing; summarizes already recorded data for managers |
| Distinct from N6 | No interactive dashboards, what-if analysis or exploration; reports answer questions defined in advance (Core Details 4) |
| Examples across business functions (merged N10) | Sales, stock, attendance and payments named as inputs (Core Details 3) |

## Defect-class self-check (FACT, run on the text above)
- All five section headers present; 17 bullets in total, five in Quick Recall; no sentence over 45 words.
- No scope-disclaimer sentence (screened: "this note does not", "beyond the scope", "not covered", "is outside", "out of scope", "not discussed", "will not cover", "excluded").
- No LaTeX or `$`; no real-company name or statistic; one fictional example used consistently.
- No interactive-dashboard, what-if, ERP, data-warehouse or decision-support content (screened).
