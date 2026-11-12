Status: Study Pack review template for the twelve FIS Notes, DRAFT for the owner, 2026-11-06. NOTHING here has been done. Document-only: no production write, no Note or Study Pack created, no SQL run, nothing committed.

# BSIS Year 1: Fundamentals of IS Study Pack review template

**Review standard (owner-approved Product UX direction, 2026-11-06 (decision log D-43)):** "Each quiz question must have one unambiguous correct answer supported by the source Note, a clear explanation of the correct reasoning, and plausible but clearly incorrect distractors. The absence of individual distractor explanations is a known limitation of the current generator, not a pack-specific defect. However, misleading or ambiguous distractors remain content defects and must be corrected."

Use one copy per Note, after the owner has generated its Study Pack with explicit confirmation. **Claude completes it from a read-only `SELECT`; nothing is regenerated, edited or deleted by Claude.** The standard is the one used for the Counting Note and the six General Education Notes: every key verified against the body, every explanation explains the distractors, no item tests untaught material.

## 0. How Claude reads the pack (SELECT only)

```sql
SELECT sp.id, sp.status, sp.model_used, sp.summary, sp.key_concepts, sp.quiz
FROM study_packs sp JOIN notes n ON n.id = sp.note_id
WHERE n.title = '<exact title>' AND n.subject = 'Fundamentals of Information Systems';
```

## 1. Identity (all must be true)

| Check | Result |
|---|---|
| Exactly one Study Pack for the Note; status `GENERATED`; Note status `GENERATED` | |
| Pack linked to the right Note id (the id from F2) | |
| Five quiz items | |
| Summary and key concepts contain no claim absent from the body | |

## 2. Hard checks (any failure = FAIL; report, do not fix)

| # | Check | Result |
|---|---|---|
| H1 | **Every key is supported by the body.** For each item, quote the body sentence that makes the key correct | |
| H2 | **No item tests material the body does not teach** (no outside fact, no later-Note concept, no company or statistic) | |
| H3 | **Exactly one defensible key per single-answer item**, supported by the body; "NOT correct" and two-statement items are unambiguous against the body; **every distractor is plausible but clearly incorrect** against the body, and **none is misleading or ambiguous** (a misleading or ambiguous distractor is a content defect and must be corrected) | |
| H4 | **Every explanation gives a clear explanation of the correct reasoning**, correct, supported by the body and consistent with the key. *Individual distractor explanations are not required:* their absence is a known limitation of the current generator (the generation prompt forbids restating choice text), recorded once for the whole catalog, **not a pack-specific defect**. Structured distractor feedback is a separate future feature (see the Feature Planner handoff) | |
| H5 | **No item contradicts the body or another item** | |
| H6 | **Terminology matches the body** (the body's definitions, not a textbook variant; e.g. data / information / knowledge as defined in N2) | |

## 3. Set-level checks

| # | Check | Result |
|---|---|---|
| S1 | **Key positions are not all the same** (list the stored positions A to D per item; an all-same pack is a recorded defect, as in *Probability and Basic Statistics*). **Expect the catalog regularity** (item 1 keyed B in ~71% of packs, never D; see the spot-check results O5): it is a stored-data pattern that the app's display shuffle masks, so record it, do not fail a pack for it alone | |
| S2 | **At least one short scenario item** | |
| S3 | **The mix covers the Note's expected quiz coverage** (section 6 of the creation checklist, and the table below) | |
| S4 | **Distribution of item styles** includes a "NOT correct" item where the spec calls for one; multi-select items state how many to choose | |
| S5 | **Difficulty is introductory** (Year 1, Understand and Apply), not recall of Quick Recall wording only | |
| S6 | **No overlap with sibling Notes' items** (a TPS item in N5, a DSS item in N5 and so on); note any near-duplicate | |

## 4. Per-item table (copy one row per item)

| Item | Style | Key (A to D) | Body sentence that supports the key (quote) | Distractors explained? | Tests untaught material? | Result |
|---|---|---|---|---|---|---|
| 1 | | | | | | |
| 2 | | | | | | |
| 3 | | | | | | |
| 4 | | | | | | |
| 5 | | | | | | |

## 5. Verdict

- **PASS** / **FAIL** (circle one). Defects, with item number and evidence:
- Recommended handling for each defect (owner decides): regenerate with explicit confirmation / single-item edit if the product allows it / record as a known limitation.
- Does the defect class repeat from an earlier Note? (If yes, stop the batch.)

## 6. Expected quiz coverage per Note (from the approved specifications)

| Code | Title | Expected coverage |
|---|---|---|
| N1 | Information Systems as Socio-Technical Systems: People, Processes, Data and Technology | Classify an element as people, process, data or technology; identify the stage in the cycle; "NOT correct" on IS versus IT; a scenario in which a system fails because a non-technology component was ignored; a multi-select of the five cycle stages. |
| N2 | Data, Information and Knowledge in Organizations | Classify an item as data, information or knowledge; match a flaw to a quality dimension; "NOT correct" on information value; a scenario applying the dimensions; a two-statement item. |
| N3 | Roles in Information Systems Practice: Users, Developers, Managers and Vendors | Match a responsibility to a role; "NOT correct" on roles; a scenario of a failed project traced to a missing role; a multi-select of IS professional characteristics. |
| N8 | Operational, Managerial and Strategic Information Needs | Match a decision to its level; match information needs to a level; "NOT correct" on decision types; a scenario assigning a decision to a stage. |
| N4 | Transaction Processing Systems | Identify an example as a TPS function; "NOT correct" on TPS characteristics; a scenario choosing batch or real-time; a two-statement item on outputs. |
| N5 | Management Information and Reporting Systems | Match a report to its type; match a need to MIS rather than TPS; "NOT correct" on MIS users; a scenario. |
| N6 | Decision Support and Business Intelligence Systems | Match a situation to DSS rather than MIS; "NOT correct" on BI; a scenario; a two-statement item. |
| N9 | Enterprise-Wide Systems: ERP, CRM and Supply Chain Systems | Identify the benefit of integration; "NOT correct" on ERP; a scenario of duplicate data in separate systems; a match of CRM versus supply chain. |
| N11 | Information Systems and Organizational Strategy | Match a system to a source of value; "NOT correct" on advantage; a scenario of misalignment; a two-statement item. |
| N7 | Information Systems and Technology-Enabled Change in Enterprises | Classify a change by area; "NOT correct" on technology-enabled change; a scenario; a multi-select of the four areas. |
| N12 | The Development Life Cycle of an Information System | Put stages in order; match an approach to a situation; "NOT correct" on user involvement; a scenario. |
| N13 | Organizational Change and Information System Adoption | Match a cause to resistance; "NOT correct" on adoption; a scenario choosing a response; a two-statement item. |

## 7. Log (one line per reviewed Note)

| Date | Code | Verdict | Defects | Handling |
|---|---|---|---|---|
| 2026-11-06 | N1 | **PASS** (content PASS; H4 met under the approved standard) | None. All five keys supported by the body; distractors plausible but clearly incorrect; no untaught material; scenario items present; stored key positions B C A D A. Coverage note: no IS-versus-IT "NOT" item and no stage multi-select (five items cannot cover every suggested style); recorded, not a defect | None. Pack `c2f27735-2071-453a-9e05-79dd18ef91b6` accepted |
| 2026-11-06 | N2 | **PASS** | None. All five keys supported by the body (knowledge scenario; contextual attributes multi-select [relevance, timeliness]; "NOT correct" on information value; synthesize; both accessibility statements true); every distractor plausible but clearly incorrect; no untaught material; summary table and key concepts match the body. Coverage matches the specification (classify, quality dimension, NOT on value, scenario, two-statement). Stored key positions B [B C] D C A. Observation: the body's own line between timeliness (contextual) and "when needed" (accessibility) is subtle; accurate to Wang and Strong, not a defect | None. Pack `5ea546f1-9dd0-4b38-92a5-2c2e0e43d489` accepted |
| 2026-11-06 | N3 | **PASS** | None. Library-project scenario throughout; all five keys supported (librarians test task fit; developers turn needs into working technology; sponsor aligns and authorizes; vendor does not transfer accountability, but checking corrections is right; sharing confidential records is the inconsistent practice); distractors plausible but clearly incorrect; no untaught material; summary and key concepts match the body. Coverage partial against the specification (match-responsibility-to-role covered; no missing-role failure scenario and no multi-select of professional characteristics). Stored key positions B D A C B | None. Pack `1dcfd89d-6116-4698-84a8-a52ec69a228a` accepted |
| 2026-11-06 | N8 | **PASS** | None. All five keys supported (dispatcher needs current internal data; "restrict to internal records" is the statement inconsistent with strategic needs; managerial and semi-structured; Statement 1 true and Statement 2 false; design stage); the bus-company example is the body's own; Simon cited cautiously as in the body. Coverage matches the specification (level, information needs, "NOT" on decision types, stage scenario). Stored key positions B D C B D | None. Pack `b0f746cd-f139-43df-a720-f539eedf61c4` accepted |
| 2026-11-06 | N4 | **PASS** | None. All five keys supported (purpose; batch processing; authorization, not validation, decides who may cancel; adjustment linked to the original preserves the audit trail; both statements true); grocery example is the body's own; coverage matches the specification (TPS function, "NOT" on controls, batch scenario, two-statement). Stored key positions B C D A A | None. Pack `2627964e-28e4-4ae3-9060-b874a6213660` accepted |
| 2026-11-06 | N5 | **PASS** | None. All five keys supported (MIS purpose is monitoring and control; exception report; periodic and on-demand statements both true; an untimely report is the non-characteristic; a neat report can mislead when the data are wrong); distractors plausible but clearly incorrect (the TPS and automatic-decision options are the body's contrasts); summary and key concepts match the body. Coverage largely matches the specification (report types, MIS versus TPS, scenarios; the "NOT" item is on report characteristics rather than MIS users). Stored key positions B D A C B | None. Pack `7703d512-dae4-4283-8442-ec5bf67d0dd0` accepted |
| 2026-11-06 | N6 | **PASS** | None. All five keys supported (DSS = data, models, interaction, multi-select [A B C]; what-if testing; interactive dashboard; treating model output as sufficient is the inappropriate practice; Statement 1 true, Statement 2 false). **Consistent with the owner-corrected N6 text:** the pack states that business intelligence supports both routine monitoring and less routine questions and that the approaches can overlap, and no item treats them as mutually exclusive. Coverage matches the specification. Stored key positions [A B C] B D C B | None. Pack `a0ffa46c-2b6d-472e-b306-5640b542bcb8` accepted |
| 2026-11-06 | N9 | **PASS** | None. All five keys supported (ERP shares common data; CRM for customer history; supply chain shares expected demand; "reducing duplicate entry" is a benefit, not a challenge; Statement 1 true, Statement 2 false); bicycle-manufacturer scenario is the body's own; distractors plausible but clearly incorrect; summary table matches the body. Coverage matches the specification (integration benefit, "NOT" on ERP, CRM versus supply chain). Stored key positions B D A C B | None. Pack `190b4cb5-0c23-4fe1-abc5-f70c0e08af82` accepted |
| 2026-11-06 | N11 | **PASS** | None. All five keys supported (efficiency; **IT-business alignment as mutual, matching the owner-corrected text**; focus strategy; the "remains distinctive after every competitor offers it" statement is the incorrect one; both planning statements true); distractors plausible but clearly incorrect, and the alignment distractors contradict the corrected wording rather than echo the old one-way wording; summary matches the body including Porter's three strategies and the courier example. Coverage largely matches the specification (value source, "NOT" on advantage, two-statement; no misalignment scenario). Stored key positions C A D B A | None. Pack `80ab8376-90b0-441f-b879-0864061d7af2` accepted |
| 2026-11-06 | N7 | **PASS** (one closest-call item recorded) | None requiring correction. All five keys supported (automation; mobile wallet is a product; digital business; "installation is sufficient" is inconsistent with planned change; both statements true). **Closest call, item 2:** the mobile wallet is the body's own example of the *products* area, but "Services" is a defensible-sounding distractor in everyday usage. The Note's taxonomy resolves it (products = what is offered; services = how it is delivered and reached) and the explanation says so, so it is not ambiguous within the course; recorded for the owner, no change recommended. Stored key positions B D A C A | None. Pack `d87221f1-66d6-460a-b171-3924595afb19` accepted |
| 2026-11-06 | N12 | **PASS** | None. All five keys supported (analysis versus design; implementation stage; iterative approach for changing requirements; prototyping is **not** limited to iterative development; Statement 1 true and Statement 2 false). **Consistent with the owner-corrected text:** the summary states the build, buy and vendor evaluation in planning and analysis with the selected option carried out in construction or acquisition, defines plan-driven as upfront planning with the freedom to revisit earlier work, and no item treats the stages as strictly sequential. Stays inside its boundary: no item re-teaches requirements elicitation, feasibility, fit-gap, total cost of ownership or weighted evaluation. Stored key positions B D A C B | None. Pack `2d910f38-952f-4053-a2c4-507a3a468c93` accepted |
| 2026-11-06 | N13 | **PASS** | None. All five keys supported (adoption means use as intended; dismissing resistance without investigating is the inconsistent response; multi-select [involve users early, managers demonstrate use] matches the body; sustaining adoption means continued feedback and checking paper habits; Statement 1 true, Statement 2 false); the permits-office scenario is the body's own; distractors plausible but clearly incorrect. Stored key positions B D [A C] C B | None. Pack `d1ee879c-9c18-47c9-bd40-5db8b84a3ec2` accepted |
| | | | | |
