Status: ACADEMICALLY APPROVED FOR PRE-PRODUCTION PREPARATION (owner decision 2026-11-06, combined review of the twelve FIS Notes; targeted corrections applied where listed in `../bsis-year1-fis-preproduction-readiness.md`). Document-only. NOTHING here is in production. Creating the Note, setting its metadata and generating its Study Pack are owner actions after separate approvals. Domain Context is provisional and reuse flags remain subject to the final taxonomy review.

# N9: Enterprise-Wide Systems: ERP, CRM and Supply Chain Systems: authored body

Approved specification: `../bsis-year1-fis-authoring-specifications.md`. Batch 2 quality assessment: `../bsis-year1-fis-batch2-review.md`. Authoring standard: N1 and N2 (approved references).

## Metadata (provisional; Domain Context under per-Note review)

| Field | Value |
|---|---|
| Title | Enterprise-Wide Systems: ERP, CRM and Supply Chain Systems |
| Plan / section / order | 🏢 Fundamentals of Information Systems (Second Semester) / 3 Kinds of Information Systems / 8 |
| Subject | Fundamentals of Information Systems |
| Domain Context | **unset, provisionally** (assessment in the Batch 2 review) |
| Authored Depth | COLLEGE |
| Applicable Programs | Information Systems (the only program, which is what makes an unset Domain Context legal) |
| Reuse flag | `none` |
| Status | New |

## Body (copy the contents of the fenced block, without the ``` lines)

```text
Enterprise-Wide Systems: ERP, CRM and Supply Chain Systems

📘 Overview
Enterprise-wide systems support processes that cross departments, using shared data so that sales, production, purchasing, finance and other functions work from one consistent picture. Enterprise resource planning (ERP) systems are the central example; customer relationship management (CRM) and supply chain systems extend the same idea to customers and suppliers.

🧠 Key Idea
Separate departmental systems often hold their own copies of the same data and can disagree. Enterprise-wide systems connect departments through shared data and coordinated processes, so that one event, such as a customer order, is visible to everyone who needs to act on it.

⚔️ Core Details:
- Departments that keep their own systems often store the same facts separately, such as a customer's address or the stock of a product. This can lead to duplicate entry, conflicting records and delays when one department needs another department's information.
- An ERP system integrates the main functions of an organization, such as sales, purchasing, production, inventory, finance and human resources, in modules that share a common set of data. When one module records an event, the others can use it.
- Consider a fictional bicycle manufacturer. When a customer order is entered, the sales module records it, production planning checks the parts needed, the purchasing module orders any missing parts, inventory is updated when they arrive and finance issues the invoice, all using the same order and product data.
- Benefits of an ERP system include consistent information across departments, less duplicate entry and better coordination of work. Challenges include its cost, the effort of fitting the organization's procedures to the system and the training that staff need.
- Customer relationship management (CRM) systems collect and organize information about customers and their interactions, such as inquiries, purchases and service requests, so that sales, marketing and service staff can deal with each customer consistently.
- Supply chain systems coordinate the flow of goods, information and payments among suppliers, the organization and its customers, for example by sharing expected demand and delivery schedules so that parts arrive when needed.
- In the bicycle manufacturer, the CRM system records a customer's earlier purchases and service requests so that staff can respond consistently, and the supply chain system shares expected demand with the suppliers of frames and wheels.
- Enterprise-wide systems build on transaction processing: they record the daily events of each function and also connect those records across the organization.

🎯 Why It Matters:
- A shared view of customers, orders and stock helps departments act on the same information instead of working from separate and sometimes conflicting records.
- Coordinated processes reduce delays between departments, such as an order waiting because production has not been told about it.
- Understanding the purpose of these systems helps explain why organizations connect functions rather than keeping each department's records separate.
- Enterprise-wide systems connect to the later topics on strategy and on how organizations change as they adopt new systems.

🧠 Quick Recall:
- Enterprise-wide system — an information system that supports processes across departments using shared data.
- ERP (enterprise resource planning) — an enterprise-wide system that integrates functions such as sales, purchasing, production, inventory and finance in modules sharing common data.
- Module — a part of an ERP system that supports one function and shares data with the other parts.
- CRM (customer relationship management) — a system that collects and organizes customer information and interactions so staff can deal with each customer consistently.
- Supply chain system — a system that coordinates the flow of goods, information and payments among suppliers, the organization and its customers.
```

Length: **4,025 characters**, 607 words (soft guideline; approved N1 is 4,440 and N2 5,498).

## Coverage of the approved scope

| Requirement | Where it is met |
|---|---|
| Organizational purpose of enterprise-wide systems | Overview; Key Idea; Core Details 1 |
| ERP at proper depth | Core Details 2 to 4 (modules, shared data, benefits, challenges) |
| CRM and supply chain as introductory examples | Core Details 5 to 7 (one short passage each) |
| One consistent example | Fictional bicycle manufacturer |
| Not software configuration or enterprise architecture | None present |
| Not process modelling, implementation projects or BI techniques | None present |
| Link to N4 | Core Details 8 |

## Defect-class self-check (FACT, run on the text above)
- All five section headers present; 17 bullets in total, five in Quick Recall; no sentence over 45 words.
- No scope-disclaimer sentence (screened: "this note does not", "beyond the scope", "not covered", "is outside", "out of scope", "not discussed", "will not cover", "excluded").
- No LaTeX or `$`; no real-company name or statistic; one fictional example used consistently.
- No configuration, architecture, process-modelling or BI content (screened).
