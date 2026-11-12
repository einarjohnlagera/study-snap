Status: ACADEMICALLY APPROVED FOR PRE-PRODUCTION PREPARATION (owner decision 2026-11-06, combined review of the twelve FIS Notes; targeted corrections applied where listed in `../bsis-year1-fis-preproduction-readiness.md`). Document-only. NOTHING here is in production. Creating the Note, setting its metadata and generating its Study Pack are owner actions after separate approvals. Domain Context is provisional and reuse flags remain subject to the final taxonomy review.

# N2: Data, Information and Knowledge in Organizations: authored body (revision 2)

Approved specification: `../bsis-year1-fis-authoring-specifications.md`. Change log, consistency check, Domain Context and reuse assessment, Study Pack readiness: `../bsis-year1-fis-calibration-revision-log.md`. Earlier source notes: `../bsis-year1-fis-calibration-review.md`.

## Metadata (provisional; Domain Context under per-Note review)

| Field | Value |
|---|---|
| Title | Data, Information and Knowledge in Organizations |
| Plan / section / order | 🏢 Fundamentals of Information Systems (Second Semester) / 1 Information Systems and Organizations / 2 |
| Subject | Fundamentals of Information Systems |
| Domain Context | **unset, provisionally approved** (ADR-001; reviewed in the revision log) |
| Authored Depth | COLLEGE |
| Applicable Programs | Information Systems (the only program, which is what makes an unset Domain Context legal) |
| Reuse flag | `candidate (low), not approved`; the Note is not split and no BSCS or BSIT program is added |
| Status | New |

## Body (copy the contents of the fenced block, without the ``` lines)

```text
Data, Information and Knowledge in Organizations

📘 Overview
Organizations run on data, information and knowledge, which are related but different. Data are recorded facts, observations or symbols; information is data organized or processed so that it is meaningful for a purpose; and knowledge is understanding developed from information, experience and context that helps people interpret situations and make decisions. Information creates the most value when it is good enough to use, which is why its quality matters.

🧠 Key Idea
Organizations gain value not from holding more data but from turning data into information that decision makers can trust and use, and from the knowledge people develop to guide their actions. Information quality is judged by the needs of its users, not only by whether the facts are correct.

⚔️ Core Details:
- Data are recorded facts, observations or symbols that can be processed or interpreted. In a fictional pharmacy chain, the individual sales records of each medicine at each branch are data.
- Information is data organized or processed so that it is meaningful for a purpose. Weekly sales of each medicine by branch answer a question a manager has, so they are information.
- Knowledge is understanding developed from information, experience and context that helps people interpret situations and make decisions. In the pharmacy, a branch manager notices from previous sales records that demand for a particular product rises during a recurring local event, and uses that experience to plan the next order.
- The same data can become different information for different users: buyers, branch managers and finance staff each need a different summary of the same sales records.
- Information quality means being fit for the purposes of the people who use it, not only being correct. Wang and Strong developed a consumer-centered framework for data quality that also helps explain the quality of information used in organizations. It groups quality into four categories: intrinsic, contextual, representational and accessibility.
- Intrinsic quality means information is correct and believable in its own right, such as accuracy. Contextual quality means it suits the task, such as relevance and timeliness. Representational quality means it is presented so users can interpret and understand it, such as clear units and labels.
- Accessibility concerns whether intended users can obtain information when needed, while access security helps prevent unauthorized use. Both belong to the accessibility category.
- In the pharmacy, a stock report is accurate if its counts match the shelves (intrinsic). It is relevant if it covers the medicines the buyer orders and timely if it arrives before the order cutoff (contextual), and understandable if its units and labels are clear (representational). It is accessible if authorized staff can open it when they need it, and access security keeps unauthorized people out (accessibility).
- Poor quality has costs: wrong or late information leads to wrong decisions, rework and lost trust, and information that is accurate but irrelevant or hard to understand still fails its users.
- Information has value when using it improves a decision or an activity. Collecting, storing and reviewing it also take time and money, so more information is not automatically better.
- Rayport and Sviokla's virtual value chain describes how an organization can add value with information itself. It has five activities: gather (collect raw information), organize (arrange it so it can be used), select (pick what is relevant), synthesize (combine and analyze it to produce new insight) and distribute (deliver it to the people who need it). The activities are not a rigid pipeline: organizations may repeat or combine them. The chain runs alongside the physical value chain of materials and goods.
- In the pharmacy, the chain gathers sales and stock records from each branch, organizes them by medicine and branch, selects the medicines running low, synthesizes a forecast of what each branch will need, and distributes a reorder list to branch managers before the order cutoff.

🎯 Why It Matters:
- Separating data, information and knowledge shows where an organization is stuck: it may hold plenty of data but little usable information.
- The quality categories let users judge information before depending on it, and show why fixing accuracy alone leaves other problems such as late or confusing reports.
- Because information has costs as well as value, organizations invest in information that supports decisions and activities rather than collecting everything.
- The virtual value chain shows how information itself can become a product or service, which connects to the later topics on decision making, kinds of information systems and strategy.

🧠 Quick Recall:
- Data — recorded facts, observations or symbols that can be processed or interpreted.
- Information — data organized or processed so that it is meaningful for a purpose.
- Knowledge — understanding developed from information, experience and context that helps people interpret situations and make decisions.
- Information quality — fitness for the purposes of the people who use it; Wang and Strong's data-quality framework groups it into four categories: intrinsic, contextual, representational and accessibility.
- Virtual value chain — Rayport and Sviokla's information-based set of five activities: gather, organize, select, synthesize and distribute.
```

Length: **5,498 characters**, 855 words. This is above the soft 2,200 to 3,200 guideline, which the owner has said is not a limit; no required definition, example or framework was cut to meet it, and long sentences were split for readability. Well under the 12,000-character Study Pack cap.

## Coverage of the owner's requirements

| Requirement | Where it is met |
|---|---|
| Data versus information versus knowledge | Overview; Core Details 1 to 3 (one pharmacy example set); Quick Recall |
| Information quality and its organizational importance | Core Details 5 to 9; Why It Matters 2 |
| Wang and Strong, attributed as a data-quality framework applied to information | Core Details 5 (attribution sentence); Quick Recall |
| The four categories with accessible examples | Core Details 6 and 8 (accuracy; relevance and timeliness; understandability, units and labels; authorized availability) |
| Accessibility versus access security | Core Details 7; Core Details 8 |
| How information creates value, and its cost | Core Details 10; Why It Matters 3 |
| The virtual value chain with Rayport and Sviokla's five activities, not a rigid pipeline | Core Details 11; Quick Recall |
| One simple organizational example for the stages | The fictional pharmacy chain, Core Details 12 |
| No database design or advanced data management | None present |

## Defect-class self-check (FACT, run on the text above)
- All five section headers present; 21 bullets in total, five in Quick Recall.
- No scope-disclaimer sentence: screened for the exact terms "this note does not", "beyond the scope", "not covered", "is outside", "out of scope", "not discussed", "will not cover", "excluded"; none found.
- No hardware, memory, operating-system or database terms beyond the single phrase "hardware, software and networks" (N1 only); no program-audience wording; no LaTeX or `$`.
- No real-company name or statistic; one fictional example per Note, used consistently.
- The pharmacy chain is fictional; the earlier seasonal-demand claim was replaced with a neutral recurring-local-event example.
