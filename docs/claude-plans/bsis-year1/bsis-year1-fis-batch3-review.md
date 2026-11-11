Status: Batch 3 quality assessment (N5, N13) and set-level consistency check of all twelve FIS Notes, 2026-11-06. Document-only: no Note or Study Pack created, no production write, no SQL executed, nothing committed. All twelve Notes are now drafted.

# BSIS Year 1: Fundamentals of IS, Batch 3 quality assessment and set-level check

Drafts: `authored-bodies/fis-n5-management-information-and-reporting-systems.md` and `authored-bodies/fis-n13-organizational-change-and-information-system-adoption.md`. Standard: N1 and N2. Workflow: proportional review; issues classified BLOCKER, EDITORIAL, OPTIONAL. Labels: **FACT**, **INFERENCE**, **PROPOSAL**.

## 1. Summary

| Note | Chars | Words | Review level | Recommendation |
|---|---|---|---|---|
| **N5** Management Information and Reporting Systems | 3,526 | 574 | 1 | **APPROVE** |
| **N13** Organizational Change and Information System Adoption | 3,539 | 584 | 1 | **APPROVE** |

Both: five Quick Recall bullets, the five-section format, no sentence over 45 words, no scope-disclaimer sentence, no statistics, no real-company name, one fictional example each (a supermarket chain; a municipal permits office). **No BLOCKER; no Level 2 or Level 3 item.**

## 2. Quality assessment

### N5: Management Information and Reporting Systems
- **Learning objective met:** how management information and reporting systems turn recorded transaction data into reports for monitoring and control; periodic, exception and on-demand reports; users, inputs, outputs; dependence on data quality; the two meanings of "MIS".
- **Evidence honesty (FACT):** IS2020 does not name an MIS knowledge element (the support is the broader types-and-utility element, C5), and the Note **claims no IS2020 or CHED source**. The content is standard introductory IS material (**INFERENCE**, Level 1). Recorded in the decision log since the approved baseline.
- **The merge gate (N5 into N4 if indistinguishable) is HELD, so no merge.** The two Notes differ on every axis the specification names: *purpose* (N4 records and processes events; N5 summarizes recorded data for monitoring and control), *users* (operational staff versus middle managers), *inputs* (events versus already-recorded data plus targets), *outputs* (updated records, receipts, confirmations versus reports), and the body shares no sentence. **Versus N6:** N5 uses no interactive dashboard, what-if or exploration language (screened) and says reports answer questions defined in advance, which is the line N6 draws the other way.
- **Editorial fixes made while drafting (no approval cycle):** "Managers cannot watch every transaction" softened to "rarely have time to review each transaction"; "appears in every report" to "appears in the reports built from it"; a stray "every event" removed.

### N13: Organizational Change and Information System Adoption
- **Learning objective met:** adoption versus installation; stakeholder acceptance; common sources of resistance (and resistance as information); training and support; organizational effects (roles, workflows, information flow, a temporary productivity dip); adoption continuing after go-live, at introductory depth.
- **Evidence honesty (FACT):** IS2020 C1 supports organizational change; **adoption, resistance and training are instructional applications, not separately mandated Foundations topics**, and the Note attributes them to no source. "People tend to accept a system more readily when it helps their work, is reasonably easy to use and fits how they work" is a widely taught tendency stated cautiously, with no framework named (**Level 1**).
- **No advanced framework** (screened for Kotter, Lewin, ADKAR, Prosci, technology acceptance model, "framework", certification, workforce planning).
- **Contact points handled:** N3 ("trains staff"), N9 (one challenges sentence about procedures and training), N12 ("implementation … training users") and N7 ("staff need new skills and routines") are each a passing mention; N13 owns the depth and repeats none of their sentences (no near-duplicate sentence found, section 4).
- **Editorial fixes made:** "a system succeeds only when people actually use it as intended" to "delivers its benefits when people actually use it as intended"; "Many system problems come from…" to "System problems can come from…".

## 3. Canonical overlap, Domain Context and reuse

| | N5 | N13 |
|---|---|---|
| Closest existing Notes | None relevant: no existing Note teaches management reporting | *Emerging Technologies and Their Social Impacts* (workforce displacement) and *Ethical, Legal and Social Issues in Computing* (workplace surveillance): different topics |
| Equivalent canonical coverage | None | None |
| Domain Context | unset, provisionally | unset, provisionally |
| Reuse flag | `none` | `none` |
| Ambiguity | Low: management reporting from transaction data is IS content | Low: organizational adoption of a system |

## 4. Set-level consistency check, all twelve Notes (FACT, run on the final text)

| Check | Result |
|---|---|
| Structure | All 12 have the five headers and exactly five Quick Recall bullets |
| Length | 3,403 to 5,498 characters; total 50,234 (N1 4,440; N2 5,498; N3 4,479; N8 4,668; N4 4,598; N5 3,526; N6 3,853; N9 4,025; N11 3,814; N7 3,403; N12 4,391; N13 3,539) |
| Sentence length | No sentence over 45 words in any Note |
| Scope disclaimers | None in any Note |
| Fictional examples | **Twelve distinct examples**, one per Note (community college, pharmacy chain, city library, regional bus company, grocery store, supermarket chain, furniture retail chain, bicycle manufacturer, regional courier company, regional bank, community water cooperative, municipal permits office); no example is reused, which keeps each Note's future scenario quiz items independent |
| Duplicate or near-duplicate sentences across Notes | **One pair found (N11 and N7 closing Why It Matters bullets, similarity 0.65) and fixed** (N7 reworded) |
| Quick Recall terms repeated across Notes | None |
| Cross-references by name to other Notes | None ("previous Note", "see the Note", "covered elsewhere" absent) |
| "later topics" phrasing | Appears as a bridging phrase in N1, N2, N3, N9, N11 and N7. **OPTIONAL, no action:** N1 and N2 are approved with it; it names no Note |
| Terminology: **control** | N1 defines control as rules, checks and performance measures that keep a system operating as intended; N4 applies it (validation, authorization, reconciliation); N5's "monitoring and control" compares actual performance with plans, which is a consistent use |
| Terminology: **transaction processing system** | Same name in N4, N5, N9 |
| Terminology: **decision support** | N6 matches the existing *Artificial Intelligence and Human Judgment* definition; N8 does not define it |
| Terminology: **information quality** | Framework only in N2; N8, N4 and N5 use plain words |
| Boundaries held | N3/Introduction to Computing, N8/N2, N4/N1, N4/N5, N5/N6, N6/N8, N9/configuration and architecture, N11/N7, N12/*Analyzing Organizational Computing Solutions*, N13/N3, N9 and N12 |

**Domain Context and reuse across the set (FACT, from the individual assessments):** all twelve are **unset, provisionally**, Information Systems the only program, Authored Depth COLLEGE, Subject `Fundamentals of Information Systems`. Reuse flags: **N1 `candidate (low)`; N2 `candidate (low), not approved`; N3 `candidate (low)`; N12 `candidate (moderate)`; the other eight `none`.** The four flagged Notes go to taxonomy and reuse review **before publication** and are not published IS-only until reviewed; any change is an owner decision. No new Domain Context value and no added program is proposed. Because all twelve share the unset value and one program, Bulk Generate needs one run, a consequence of the per-Note decisions and not their reason.

## 5. Study Pack readiness

Mix: two conceptual, two scenario with a **new** equivalent example, one distinction or misconception; one best answer; varied key positions.
- **N5:** (1) purpose and users of a management information system; (2) the three report types and what each shows; (3) a new scenario choosing the report type that fits (a fictional school canteen or hotel); (4) a new scenario in which wrong transaction data produces a wrong report; (5) misconception: "a management report records transactions" or "managers need every transaction detail". *Avoid asking:* interactive analysis or dashboards.
- **N13:** (1) adoption versus installation; (2) common sources of resistance; (3) a new scenario (a fictional school office or warehouse) choosing a response that supports adoption; (4) a new scenario on an organizational effect (a role, workflow or information-flow change); (5) misconception: "once a system is installed, the project is finished" or "resistance means the people are the problem". *Avoid asking:* named change-management models.

## 6. Material concerns

**None.** Recorded without blocking: N5 rests on standard introductory content with no named source (the baseline already says so); N13's acceptance statement is a widely taught tendency stated cautiously; both checked at Level 1.

## 7. Recommendation

**APPROVE both Batch 3 Notes.** The full set of twelve passes the set-level consistency check. **The next step is a single combined Note Strategist review of all twelve** (prompt prepared: `bsis-year1-fis-combined-review-strategist-prompt.md`), after which the remaining pre-production work is the owner's and mine, not drafting: the metadata operations, the shared-Note fixes and spot-checks, the reuse-ID manifest and the collection build.
