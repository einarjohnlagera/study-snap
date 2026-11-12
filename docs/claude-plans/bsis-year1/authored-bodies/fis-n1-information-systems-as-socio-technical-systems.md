Status: ACADEMICALLY APPROVED FOR PRE-PRODUCTION PREPARATION (owner decision 2026-11-06, combined review of the twelve FIS Notes; targeted corrections applied where listed in `../bsis-year1-fis-preproduction-readiness.md`). Document-only. NOTHING here is in production. Creating the Note, setting its metadata and generating its Study Pack are owner actions after separate approvals. Domain Context is provisional and reuse flags remain subject to the final taxonomy review.

# N1: Information Systems as Socio-Technical Systems: People, Processes, Data and Technology: authored body (revision 2)

Approved specification: `../bsis-year1-fis-authoring-specifications.md`. Change log, consistency check, Domain Context and reuse assessment, Study Pack readiness: `../bsis-year1-fis-calibration-revision-log.md`. Earlier source notes: `../bsis-year1-fis-calibration-review.md`.

## Metadata (provisional; Domain Context under per-Note review)

| Field | Value |
|---|---|
| Title | Information Systems as Socio-Technical Systems: People, Processes, Data and Technology |
| Plan / section / order | 🏢 Fundamentals of Information Systems (Second Semester) / 1 Information Systems and Organizations / 1 |
| Subject | Fundamentals of Information Systems |
| Domain Context | **unset, provisionally approved** (ADR-001; reviewed in the revision log) |
| Authored Depth | COLLEGE |
| Applicable Programs | Information Systems (the only program, which is what makes an unset Domain Context legal) |
| Reuse flag | `candidate (low)`; future BSIT reuse to be considered separately |
| Status | New |

## Body (copy the contents of the fenced block, without the ``` lines)

```text
Information Systems as Socio-Technical Systems: People, Processes, Data and Technology

📘 Overview
An information system is an organized combination of people, processes, data and technology that collects, processes, stores and distributes information to support an organization's activities. It is called socio-technical because its results depend on social elements and technical elements working together, not on technology alone.

🧠 Key Idea
An information system is most effective when its people, processes, data and technology work together, and its results are checked against organizational needs. Technology is one component: a system with excellent technology can still fail to serve an organization if people do not use it, procedures do not match it, or its data are wrong.

⚔️ Core Details:
- The four components are people (users, staff and managers who operate or depend on the system), processes (the procedures and rules that define how work is done), data (the facts the system records and uses) and technology (the hardware, software and networks that carry out processing).
- These four components describe an information system at the organizational level. A computer system focuses more narrowly on the computing technology that may support it. The technology component is what is usually called information technology; an information system includes it together with the people, processes and data that make it useful to an organization.
- The processing cycle has five stages: input (capturing data), processing (transforming data by checking, calculating or sorting), storage (keeping data for later use), output (delivering results to people or other systems) and control.
- Control uses rules, checks and performance measures to keep a system operating as intended. Feedback about its results helps people identify problems and take corrective action, such as correcting data, changing a procedure or adding resources. Controls can act at any stage, such as checking data as it is entered or reviewing results afterward.
- Consider the enrollment office of a fictional community college. Students submit enrollment details and payment records (input); the system checks prerequisites and section capacity and computes fees (processing); student and section records are updated (storage); schedules, fee statements and class lists are produced (output). Control appears at several points: the prerequisite and capacity checks, and the registrar's review of a report of full sections and unpaid balances. When that feedback shows a section is full, staff open another one, and when enrollment details were entered wrongly, they correct them (corrective action).
- In that example the students and staff are the people, the enrollment procedure and its rules are the processes, the student and section records are the data, and the enrollment software and network are the technology.
- The components depend on one another. Moving enrollment online (a technology change) requires new procedures, staff training and new data such as each student's email address; changing one component without the others weakens the system.

🎯 Why It Matters:
- Looking at all four components explains why systems can fail for non-technical reasons such as untrained users, unclear procedures or poor data.
- The processing cycle helps locate a problem: wrong results can come from faulty input, flawed processing, outdated storage, unclear output or missing control.
- Feedback and corrective action keep a system useful as conditions change, for example when more students enroll than planned.
- Seeing an information system as more than technology is the starting point for the later topics in this subject: information, decision making and the kinds of information systems.

🧠 Quick Recall:
- Information system — an organized combination of people, processes, data and technology that supports an organization's activities by providing information.
- Socio-technical system — a system whose performance depends on how its social elements and its technical elements work together.
- Processing cycle — input, processing, storage, output and control.
- Control — the rules, checks and performance measures that keep a system operating as intended; feedback about results helps people identify problems and take corrective action.
- Process — a defined procedure or set of rules that describes how work is done in an organization.
```

Length: **4,440 characters**, 674 words. This is above the soft 2,200 to 3,200 guideline, which the owner has said is not a limit; no required definition, example or framework was cut to meet it, and long sentences were split for readability. Well under the 12,000-character Study Pack cap.

## Coverage of the owner's requirements

| Requirement | Where it is met |
|---|---|
| Information systems as socio-technical systems | Overview; Key Idea; Quick Recall (socio-technical system) |
| People, processes, data and technology as interacting components | Core Details 1, 6 and 7 |
| Input, processing, storage, output and control | Core Details 3; the enrollment example in 5 |
| The role of feedback and corrective action | Core Details 4 and 5; Why It Matters 3; Quick Recall (control) |
| How the components support an organizational activity | The enrollment-office example (5 and 6) and interdependence (7) |
| One consistent organizational example | The fictional community college enrollment office |
| Do not repeat the hardware and software lessons | Technology is described in one phrase; Core Details 2 distinguishes the organizational-level view from the narrower computing-technology focus of a computer system |
| Control does not occur only after output | Core Details 4 ("Controls can act at any stage…") and 5 (prerequisite and capacity checks are controls) |

## Defect-class self-check (FACT, run on the text above)
- All five section headers present; 16 bullets in total, five in Quick Recall.
- No scope-disclaimer sentence: screened for the exact terms "this note does not", "beyond the scope", "not covered", "is outside", "out of scope", "not discussed", "will not cover", "excluded"; none found.
- No hardware, memory, operating-system or database terms beyond the single phrase "hardware, software and networks" (N1 only); no program-audience wording; no LaTeX or `$`.
- No real-company name or statistic; one fictional example per Note, used consistently.
- The enrollment example is fictional and names no institution; no statistic appears.
