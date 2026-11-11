Status: Fundamentals of IS Note creation checklist (Phase 2 and 3), DRAFT for the owner, 2026-11-06. NOTHING here has been done. Document-only: no production write, no Note or Study Pack created, no SQL run, nothing committed.

# BSIS Year 1: Fundamentals of Information Systems, Note creation checklist

**For the owner to execute in the production UI. Claude creates nothing and generates no Study Pack** (`CLAUDE.md`: production writes are owner-only; the versioning rule: Study Packs are never auto-regenerated and need explicit confirmation). Claude drafts, reads back with `SELECT` and reviews. Source of truth for the bodies: `authored-bodies/` (academically approved for pre-production preparation, owner decision 2026-11-06). Source of truth for hashes and lengths: `bsis-year1-fis-body-manifest.tsv`. Read-only checks: `bsis-year1-collection-verify-read.sql`, queries F0, F1, F2.

## 0. Preconditions

- [x] **No title collision (FACT, read 2026-11-06):** no existing Note carries any of the twelve titles or the Subject `Fundamentals of Information Systems`, and no collection mentioning Information Systems or BSIS exists. Re-run F1 immediately before creating; a Note created twice cannot be told apart by title.
- [x] **The Information Systems program row exists (FACT):** `338d3e66-fee4-4400-afa7-0af0931444c8`, active (F0).
- [ ] The twelve bodies are the approved text. If anyone edits a body, regenerate the manifest (`tools/bsis_phase0_generate.py`) so the hash changes with it.
- [ ] **Creation does not wait for Phase 1** (the GE metadata scripts): the twelve are independent of the six shared Notes. Phase 1 must finish before the collection is verified (V5).
- [ ] Domain Context is **unset** on all twelve, which is legal only because Information Systems is the **only** program. Do not add a second program to any of them at creation. **N12 is the priority taxonomy review (candidate, moderate); N1, N2 and N3 are candidate (low).** Creating them PRIVATE and IS-only is safe; publishing IS-only before that review is not.

## 1. Metadata that is identical for all twelve (set exactly)

| Field | Value |
|---|---|
| Subject | `Fundamentals of Information Systems` (new value; the Subject is free text, so copy it exactly) |
| Course / program (Applicable Programs) | `Information Systems` only |
| Learner / authored level | `College` |
| Domain Context | **leave unset** |
| Visibility | **Private** at creation; PUBLIC only after the Study Pack review passes (section 4) |
| Title | exactly as in the table below; the body's first line repeats it (the stored `content` starts with the title line, as in the Counting Note) |

Field labels in the app may differ slightly from these names; the values are what matter.

## 2. Order (calibration pair first, then in the section order)

| Batch | Order | Code | Section | Title | Chars | Source |
|---|---|---|---|---|---|---|
| A (calibration) | 1 | N1 | Information Systems and Organizations | Information Systems as Socio-Technical Systems: People, Processes, Data and Technology | 4,440 | `authored-bodies/fis-n1-information-systems-as-socio-technical-systems.md` |
| A (calibration) | 2 | N2 | Information Systems and Organizations | Data, Information and Knowledge in Organizations | 5,498 | `authored-bodies/fis-n2-data-information-and-knowledge-in-organizations.md` |
| B | 3 | N3 | Information Systems and Organizations | Roles in Information Systems Practice: Users, Developers, Managers and Vendors | 4,479 | `authored-bodies/fis-n3-roles-in-information-systems-practice.md` |
| B | 4 | N8 | Information Needs and Decision Making | Operational, Managerial and Strategic Information Needs | 4,668 | `authored-bodies/fis-n8-operational-managerial-and-strategic-information-needs.md` |
| B | 5 | N4 | Kinds of Information Systems | Transaction Processing Systems | 4,598 | `authored-bodies/fis-n4-transaction-processing-systems.md` |
| C | 6 | N5 | Kinds of Information Systems | Management Information and Reporting Systems | 3,526 | `authored-bodies/fis-n5-management-information-and-reporting-systems.md` |
| C | 7 | N6 | Kinds of Information Systems | Decision Support and Business Intelligence Systems | 4,266 | `authored-bodies/fis-n6-decision-support-and-business-intelligence-systems.md` |
| C | 8 | N9 | Kinds of Information Systems | Enterprise-Wide Systems: ERP, CRM and Supply Chain Systems | 4,025 | `authored-bodies/fis-n9-enterprise-wide-systems-erp-crm-and-supply-chain.md` |
| D | 9 | N11 | Technology, Strategy and Change | Information Systems and Organizational Strategy | 4,089 | `authored-bodies/fis-n11-information-systems-and-organizational-strategy.md` |
| D | 10 | N7 | Technology, Strategy and Change | Information Systems and Technology-Enabled Change in Enterprises | 3,403 | `authored-bodies/fis-n7-information-systems-and-technology-enabled-change.md` |
| D | 11 | N12 | Technology, Strategy and Change | The Development Life Cycle of an Information System | 4,670 | `authored-bodies/fis-n12-the-development-life-cycle-of-an-information-system.md` |
| D | 12 | N13 | Technology, Strategy and Change | Organizational Change and Information System Adoption | 3,539 | `authored-bodies/fis-n13-organizational-change-and-information-system-adoption.md` |

**Gate between batches.** Do not start the next batch until every Note in the current one has passed section 3 and section 4. N1 and N2 are the calibration pair: if either Study Pack shows a defect class (a key the body does not support, an item testing untaught material), stop and report before creating anything else, so the defect is not repeated twelve times.

## 3. Per Note: create, verify, generate, review

1. [ ] Copy the **contents of the fenced `text` block** (without the ``` lines) into a new Note. The first line is the title. Set the metadata in section 1. Save as **Private**.
2. [ ] Tell Claude the Note exists. Claude runs F1 and F2 (read-only). **Every flag must read `OK`**: subject, Domain Context unset, level, programs = Information Systems only, normalized body. `visibility` should read PRIVATE; `content_len` may differ from `expected_len` only if the app changed line endings, in which case the normalized body flag is the test. `BODY DIFFERS (inspect)` means stop: Claude shows the first differing line; do not generate.
3. [ ] **Do not use "Regenerate note"** on these Notes. It regenerates the *body* from the title and would overwrite the approved text. Study Pack generation is the separate action.
4. [ ] Generate the Study Pack **with explicit confirmation**, one Note at a time. Do not bulk-generate until the calibration pair has passed review.
5. [ ] Claude reads the pack (`SELECT` on `study_packs`) and completes `bsis-year1-fis-study-pack-review-template.md` for that Note. Outcome: PASS, or a defect list.
6. [ ] On a defect: nothing is regenerated, edited or deleted by Claude. Claude reports the item and the evidence; the owner decides (regenerate with confirmation, a single-item edit if the product allows one, or accept as a recorded limitation).

**Generation context (FACT, from `CLAUDE.md`).** The authoring domain resolves note Domain Context, then exactly one joined catalog program, then the note course program, then the profile. With Domain Context unset and Information Systems as the only joined program, generation uses the Information Systems program fallback. This is why a second program must not be added before generation.

## 4. After all twelve pass: make them ready for the plan

- [x] N12 (and N1, N2, N3) taxonomy review recorded as a decision (closed 2026-11-06, D-52: Option A for all four). Any change of Domain Context or programs goes through a separately prepared and approved metadata script, and the shaping TSV is edited and the workbook regenerated (never edited by hand).
- [ ] Set each Note **PUBLIC** (the Counting Note precedent: created Private, set Public before joining a plan). Publishing a plan refuses unless every Note in it is PUBLIC.
- [ ] Claude runs F2 again: visibility PUBLIC, `status` GENERATED, `packs` = 1 on all twelve.
- [ ] **Record the ids without typing them:** Claude reads them from F2 and writes `bsis-year1-fis-created-ids.tsv` (columns `title`, `note_id`), then re-runs `tools/bsis_phase0_generate.py`. The manifest, the run sheet and the verification SQL are regenerated with the real ids. Nobody hand-edits an id.

## 5. Stop conditions (stop and report; do not continue)

- F1 shows `found` is not 1 for a title (a duplicate or a missing Note).
- F2 shows any flag other than `OK` (other than the expected PRIVATE/DRAFT before generation), or `BODY DIFFERS (inspect)`.
- A Study Pack fails the review template on a hard check (section 2 of the template). The absence of individual distractor explanations is **not** a failure (owner-approved standard, 2026-11-06); a misleading or ambiguous distractor, an unsupported key or an item testing untaught material is.
- Any second program, or any Domain Context, appears on a Note before the taxonomy review.

## 6. Per-Note review focus (from the approved specifications, "Quiz coverage")

| Code | Title | Expected quiz coverage |
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

## 7. Safety

- Everything here is private and reversible until the Notes are set PUBLIC and the plan is published.
- Production writes are the owner's at every step; Claude only reads.
- This checklist was written from the approved bodies and read-only production reads on 2026-11-06. Re-read production before relying on any claim in it.
