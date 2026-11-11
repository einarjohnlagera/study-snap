Status: pre-production readiness report for the twelve Fundamentals of IS Notes, 2026-11-06. **Planning and documentation only**: no production Note created or modified, no Study Pack generated, no SQL executed, no metadata changed, no collection or Subject Plan created, nothing committed. For owner review.

# BSIS Year 1: FIS pre-production readiness report

All twelve authored bodies are marked **academically approved for pre-production preparation** (owner decision, combined review). Labels: **FACT**, **INFERENCE**, **PROPOSAL**.

## 0. Corrections applied from the combined review

| Note | Correction applied (owner's wording) | Consistency adjustments made so the Note stays coherent |
|---|---|---|
| **N6** | Overview opening: "Decision support systems help people explore data and compare options for decisions that are not fully routine. Business intelligence tools help organizations analyze and monitor performance, supporting both routine and non-routine decisions." Key Idea: "Decision support systems are especially useful when decisions require judgment and comparison of alternatives, while business intelligence can also support routine performance monitoring and reporting." (the sentence that people remain the decision makers is preserved) | "They" became "Both" in the Overview; the business intelligence bullet now says BI supports routine monitoring as well as less routine questions and that the two **overlap** (one dashboard may track routine measures and also let a manager test a scenario); the users bullet now contrasts with a standard periodic report as "answers questions that were defined in advance; these systems let users go further by exploring the data and comparing options", which keeps N6 distinct from N5 while no longer implying BI is non-routine; the business intelligence Quick Recall matches the Overview |
| **N11** | IT-business alignment: "IT-business alignment means coordinating the organization's strategy, information systems and technology capabilities so they support one another. Business goals guide system priorities, while new technological capabilities may also create strategic opportunities." Quick Recall: "IT-business alignment — coordinating business goals, information systems and technology capabilities so they support one another." | Key Idea first sentence now says systems are chosen and used "in ways that support its strategy, and new technological capabilities can in turn suggest strategic opportunities"; the planning-cycle bullet adds that the review "can also show new technological capabilities that open strategic opportunities"; one absolute softened ("A system **tends to** give lasting advantage when…", was "only when") |
| **N12 (plan-driven)** | "A plan-driven approach emphasizes upfront planning, documented requirements and organized stages, although teams may revisit earlier work when necessary." Quick Recall updated to match | The Key Idea no longer says stages "run once in order": "whether they are organized as planned stages or repeated in shorter cycles depends on the approach" |
| **N12 (construction or acquisition)** | "The organization may evaluate build, buy or vendor options during planning and analysis, then carry out the selected approach during construction or acquisition." | The stage bullet now opens "The system is made or obtained at the construction or acquisition stage…"; the water-cooperative example now makes the packaged-product decision during analysis and the configuration during acquisition |

No other Note was changed. N1 to N5, N7 to N9 and N13 are untouched by this round.

## 1. Final twelve-Note inventory and section order

Subject Plan **🏢 Fundamentals of Information Systems**, Second Semester. Subject `Fundamentals of Information Systems`; Authored Depth `COLLEGE`; Applicable Programs `Information Systems` only; Domain Context **provisionally unset** on all twelve.

| Order | Section | Note | Title | Chars | Words | Reuse flag |
|---|---|---|---|---|---|---|
| 1 | Information Systems and Organizations | N1 | Information Systems as Socio-Technical Systems: People, Processes, Data and Technology | 4,440 | 674 | `candidate (low)` |
| 2 | Information Systems and Organizations | N2 | Data, Information and Knowledge in Organizations | 5,498 | 855 | `candidate (low), not approved` |
| 3 | Information Systems and Organizations | N3 | Roles in Information Systems Practice: Users, Developers, Managers and Vendors | 4,479 | 709 | `candidate (low)` |
| 4 | Information Needs and Decision Making | N8 | Operational, Managerial and Strategic Information Needs | 4,668 | 707 | `none` |
| 5 | Kinds of Information Systems | N4 | Transaction Processing Systems | 4,598 | 733 | `none` |
| 6 | Kinds of Information Systems | N5 | Management Information and Reporting Systems | 3,526 | 576 | `none` |
| 7 | Kinds of Information Systems | N6 | Decision Support and Business Intelligence Systems | 4,266 | 658 | `none` |
| 8 | Kinds of Information Systems | N9 | Enterprise-Wide Systems: ERP, CRM and Supply Chain Systems | 4,025 | 607 | `none` |
| 9 | Technology, Strategy and Change | N11 | Information Systems and Organizational Strategy | 4,089 | 610 | `none` |
| 10 | Technology, Strategy and Change | N7 | Information Systems and Technology-Enabled Change in Enterprises | 3,403 | 538 | `none` |
| 11 | Technology, Strategy and Change | N12 | The Development Life Cycle of an Information System | 4,670 | 743 | `candidate (moderate)` |
| 12 | Technology, Strategy and Change | N13 | Organizational Change and Information System Adoption | 3,539 | 584 | `none` |

Total 51,201 characters. N10 remains merged into the system-category Notes. The order matches the shaping TSV (`docs/curriculum/bsis-year1-shaping.tsv`, plan 7).

## 2. Character counts and formatting validation (FACT, run on the final text)

- **Length:** 3,403 to 5,498 characters (soft guideline 2,200 to 3,200; the owner has said clarity and completeness come first). All far under the 12,000-character Study Pack cap.
- **Structure:** all twelve have the five standard headers and **exactly five Quick Recall definitions**.
- **Readability:** no sentence over 45 words (the screen flags N1's 49-word component-definition list and N3's Overview opener, both previously accepted).
- **Scope and style:** no scope-disclaimer sentence; no LaTeX or `$` in any body; no real-company name or statistic; twelve distinct fictional examples; no repeated Quick Recall term; no near-duplicate sentence across Notes; no cross-reference to another Note by name.
- **Corrections verified:** N6 distinguishes decision support and business intelligence without making them mutually exclusive, and stays distinct from N5's routine reporting (N6 teaches no report types; N5 says "Reports answer questions that were defined in advance"); N11 describes alignment as mutually supportive in the body and Quick Recall, with the one-way wording gone; N12's body and Quick Recall agree on plan-driven development and the strict wording is gone; N12's build, buy or vendor evaluation is placed in planning and analysis and the example agrees.
- **No new overlap or misleading claim** was introduced (set-level check re-run after the corrections).

## 3. Domain Context and canonical reuse decisions: CLOSED (2026-11-06, D-52)

The taxonomy and canonical-reuse review is **closed: taxonomy ready to publish as proposed.** All four flagged Notes take **Option A (keep Domain Context unset and Information Systems only)**; no Option B, C or D. The flags are kept as documented future candidates.

| Note | Final decision | Reuse flag kept |
|---|---|---|
| **N12** | A, unset and Information Systems only. The strongest future reuse candidate: its life-cycle concepts are transferable, but its approved organizational treatment, acquisition and vendor context and examples support IS-only. `COMPUTING` stays a possible future value only if a future review shows the complete authored treatment is genuinely transferable | `candidate (moderate)` |
| N1 | A | `candidate (low)` |
| N2 | A | `candidate (low), not approved` |
| N3 | A | `candidate (low)` |
| N4, N5, N6, N7, N8, N9, N11, N13 | `none` retained; no further candidates | `none` |

**Future reuse trigger (recorded rule):** *"Revisit a flagged canonical Note when a future BSIT, BSCS or other Study Journey or Review Set has a specific learning objective that the existing Note might satisfy unchanged."* At that time: (1) compare the complete authored body against the target learning objective; (2) confirm the academic depth and organizational framing remain appropriate; (3) check whether the existing Study Pack remains suitable; (4) decide whether reuse is justified; (5) if a second Applicable Program is approved, first establish an explicit Domain Context under ADR-001. **Do not broaden metadata solely for speculative discoverability.**

**Preserved metadata (unchanged, no migration):** Subject `Fundamentals of Information Systems`; Authored Depth `COLLEGE`; Domain Context unset; Applicable Programs `Information Systems` only. The shaping TSV already carries exactly this (`(unset)` / `Information Systems`), so **no TSV edit and no workbook regeneration are needed.** Bodies and Study Packs unchanged.

**Authorization boundary (owner-stated):** the decision closes the taxonomy review only; it does not authorize publishing Notes or collections, executing SQL or metadata scripts, regenerating Study Packs, modifying canonical content, adding Applicable Programs, changing Domain Context, or committing. No further taxonomy review unless a material contradiction is found.

## 4. Existing shared-Note QA and metadata dependencies

| Dependency | State | Gate |
|---|---|---|
| **RA 10364 correction** in *Global Citizenship and Global Civil Society* | **APPLIED by the owner and VERIFIED read-only 2026-11-06** (`bsis-year1-global-citizenship-ra10364-correction.sql`): md5 `9e3e316d...`, 3516 characters, "in 2013;" present | **Done.** H1 discharged |
| **Information Systems tag on five GE Notes** | **APPLIED by the owner and VERIFIED read-only 2026-11-06** (`bsis-year1-is-applicability.sql`): 5 rows, one per Note | **Done** |
| **Sixth Note's tag** | **APPLIED by the owner and VERIFIED read-only 2026-11-06** (`bsis-year1-is-applicability-global-citizenship-held.sql`), after the correction | **Done** |
| **D1** *Probability and Basic Statistics* raw LaTeX | Approved for a guarded fix; **RUN by the owner 2026-11-06 (09:04:40Z) and VERIFIED read-only:** `bsis-year1-d1-probability-statistics-latex-fix.sql` (three Quick Recall lines, 12 dollar signs added, nothing else; the Note is shared, in 3 collection items). **FACT:** NoteLib's frontend renders math with `react-markdown` + `remark-math` + `katex`, and the existing Notes use `$…$`, so the fix wraps the Quick Recall formulas in `$…$`. The Study Pack is **not** regenerated; the all-keys-position-A pattern is recorded for later quiz-quality maintenance | **DONE.** Owner to look at the Note once in the app (formulas should render). Not a blocker for FIS creation |
| **D2** *Financial Mathematics* quiz item 3 wording | **CHECKED 2026-11-06** (`bsis-year1-d2-financial-mathematics-quiz-item-check.md`): the product has no single-item edit, but SQL can replace one stem in the `jsonb` quiz with no regeneration and no review record invalidated (leave `quiz_stamp` alone; 0 sessions on the canonical pack). It does not reach 7 private copies owned by other users (unchanged by design). Proposed stem: "Which of the following is NOT part of effective budgeting?" | **Option A: RUN by the owner and VERIFIED read-only 2026-11-06:** `bsis-year1-d2-financial-mathematics-quiz-item-fix.sql` (one stem; `quiz_stamp` and the pack `updated_at` deliberately unchanged; the 7 private copies untouched). **DONE.** Owner to open the Note's Quiz once in the app. Not a blocker |
| **Eleven shared-Note spot-checks** | **DONE 2026-11-06: 11 of 11 PASS** (`bsis-year1-shared-note-spot-check-results.md`); with the 4 earlier checks all 15 targeted checks are complete. No defect needing a change; five low-severity observations recorded | None outstanding; hold H6 can be discharged for the targeted checks |
| **Reuse-ID manifest, run sheet, verification queries** for the BSIS build | **PRODUCED 2026-11-06 (Phase 0).** `bsis-year1-reuse-id-manifest.tsv` (199 rows), `bsis-year1-collection-run-sheet.md`, `bsis-year1-collection-verify-read.sql`, generated by `tools/bsis_phase0_generate.py`; the 182 shared ids were read-only verified against production (section 7). The twelve New rows have no ids until created | Regenerated with the real ids after creation (`bsis-year1-fis-created-ids.tsv`, never hand-typed) |
| **Build conventions (FACT, read from production):** both the BSCS and BSIT roots have `learner_level` COLLEGE and the plans have `learner_level` NULL, a `course_program` set, terms set and no study hours | BSIS follows the same: root Information Systems and College; plans `course_program` Information Systems, level NULL, 5 + 5 terms | Confirms the earlier BSIT "plans have no learner level" observation was the convention, not a gap |

## 5. Proposed safe sequencing (PROPOSAL; every production step is the owner's and needs separate approval)

**Phase 0: preparation (me, no production). DONE 2026-11-06 except the D1 SQL, which was then prepared on request (section 8).** Reuse-ID manifest and run sheet for the 182 shared placements; read-only verification queries; a creation checklist for the twelve Notes (title uniqueness check, metadata, body length equals the approved draft); a Study Pack review template (five items, the target mix, key-position check, answers supported by the body); the D1 fix SQL on request.

**Phase 1: metadata prerequisites (owner).** RA 10364 correction (dry run, inspect, approve, apply, verify); then the five-Note and sixth-Note Information Systems tag scripts (each dry run, inspect, approve, apply, verify). These are independent of Note creation but resolve the factual issue first, as agreed.

**Phase 2: calibration creation (owner).** Create **N1 and N2 only**, as **PRIVATE** Notes with the metadata above and the approved body; verify read-only (one Note per title, metadata, body length and hash equal the draft). Generate their Study Packs **one at a time with explicit confirmation**; review each against the template. Proceed only if both pass.

**Phase 3: remaining Notes in the approved order.** N3, N8, N4; then N5, N6, N9; then N11, N7, N12, N13 (the section order), each created PRIVATE, verified, its Study Pack generated with confirmation and reviewed. **Do not use "Regenerate note" or "Bulk regenerate" on a Note whose body you want to keep** (it regenerates the body from the title). Bulk Generate may be used only if one Subject and one Domain Context per run fit the final taxonomy decisions.

**Phase 4: taxonomy and reuse review (before publication).** N12 first, then N1, N2 and N3; record each decision; any change goes through a separate prepared and approved metadata script, and the TSV and workbook are regenerated.

**Phase 5: QA completion.** The eleven spot-checks documented; D1 and D2 decided and, if approved, applied; the Study Pack reviews complete for all twelve.

**Phase 6: Study Journey assembly (owner).** Create the root `BS Information Systems — 1st Year` (Information Systems, College) with the approved description, then the ten Subject Plans with the approved titles, terms (5 + 5; they freeze on publish) and descriptions; add the 182 shared Notes and the twelve new Notes by id in section order; set the twelve new Notes **PUBLIC before they join a public plan** (the Counting Note precedent); run the read-only verification queries (ids, order, visibility, programs, terms); publish only after the plan-level checks pass. Verify the deploy afterwards.

## 6. Genuine remaining blockers

**None** for pre-production preparation. Open, not blocking: IS 2010.1 unread and nothing attributed to it; N8's terminology rests on plain definitions because the primary papers could not be read; Porter (N11) and the life-cycle stages (N12) were checked against secondary sources and are stated cautiously; the standard definitions of TPS, MIS, ERP, CRM, supply chain systems, roles and adoption are not tied to a single textbook; N12's taxonomy decision and the other three reuse flags are the owner's before publication.

**The FIS authoring phase is complete.** The next milestone is Phase 0 and Phase 1 above.

## 7. Phase 0 delivered (2026-11-06)

All documentation only. No production write, no SQL run against production (production was read with `SELECT` through the read-only tool), no Note or Study Pack created, nothing committed.

| Artifact | File |
|---|---|
| Exact-ID manifest (199 rows: 182 Reuse with ids, 12 New, 5 Excluded; equals the shaping TSV row for row) | `bsis-year1-reuse-id-manifest.tsv` |
| Twelve-body manifest (title, chars, LF-and-trailing-space-normalized md5) | `bsis-year1-fis-body-manifest.tsv` |
| Run sheet (add Notes by id, plan by plan) | `bsis-year1-collection-run-sheet.md` |
| Read-only verification queries (phase-tagged) | `bsis-year1-collection-verify-read.sql` |
| FIS Note creation checklist (batches, gates, stop conditions) | `bsis-year1-fis-note-creation-checklist.md` |
| Study Pack review template | `bsis-year1-fis-study-pack-review-template.md` |
| Collection creation checklist (root, plans, publish) | `bsis-year1-collection-creation-checklist.md` |
| Generator (ids and hashes are computed, not typed) | `tools/bsis_phase0_generate.py` |

**Production read, 2026-11-06 (FACT).** The 182 manifest ids all exist, one owner; each title, Subject and Domain Context equals the TSV exactly; all are `COLLEGE`, `PUBLIC`, status `GENERATED`, with exactly one Study Pack. Programs equal the TSV for 176; the six that differ are exactly the six awaiting Phase 1 (five in the tag script, plus Global Citizenship held). No Note carries any of the twelve titles or the Subject, and no collection mentions Information Systems or BSIS.

**Tested (FACT) and the limits.** The verification SQL was run against a throwaway local PostgreSQL with a schema built from production's column lists, loaded with all 194 placements: a deliberately missing placement and a deliberately placed Excluded Note were reported by V3, V3d and V5b; a CRLF-plus-trailing-whitespace body passed the normalized hash and a one-character difference was flagged `BODY DIFFERS`; with ids supplied the placeholders disappear. **Not exercised against production:** the collection queries return nothing until the root exists, and the local schema had only the columns the queries read (`notes.updated_at` was not in the local schema, S2 reads it in production where it exists).

**Open owner choices surfaced by Phase 0 (none blocks creating the Notes):** the root title's emoji prefix (none approved; BSIT used a prefix); the root's course/program (recommended Information Systems); the N12 taxonomy review.

## 8. D1 fix SQL prepared (2026-11-06, on request)

`bsis-year1-d1-probability-statistics-latex-fix.sql` (owner-run, not run). Wraps the math tokens in the three raw-LaTeX Quick Recall lines of *Probability and Basic Statistics* (`404ee436-ff9e-4963-bcc2-7f1c395e19dd`) in `$...$`, the delimiter the other Notes use. Body 1,916 to 1,928 characters; md5 `3105b510...` to `ada1a363...`; the change is purely additive (the post-check removes the 12 dollar signs and requires the original md5 back); the addition rule line has no LaTeX and is untouched. The Study Pack (id `d218080e...`, status DONE) is asserted unchanged and is not regenerated; the all-keys-A answer pattern is a separate quality item and is deliberately not fixed here. Same single-session, dry-run-by-default design as the RA 10364 script, tested locally with the real body (dry run, hold, commit, re-run, rollback restore, bad mode, unconfirmed commit, and ten deliberate precondition failures). **The Note is shared by other journeys; the visible text of a PUBLIC Note changes, so after applying, look at it once in the app.**

## 9. D1 and D2 applied and verified (2026-11-06)

The owner ran both scripts; Claude re-read production with `SELECT` only. **D1** (*Probability and Basic Statistics*): body md5 `ada1a363...`, 1,928 characters, 12 dollar signs, 7 backslashes; removing the dollar signs gives the original md5 `3105b510...` (the change is purely additive); the three lines read as intended; `updated_at` 2026-11-06 09:04:40Z; title, Domain Context, visibility, 9 programs, 3 collection items and the Study Pack (digest `6d8b7a18...`, `updated_at` 2026-09-09) unchanged. **D2** (*Financial Mathematics in Everyday Life*, item 3): the stem is "Which of the following is NOT part of effective budgeting?"; choices and key index 2 unchanged; the other four items unchanged (digest `5e7e4d3a...`); `quiz_stamp` 0 and pack `updated_at` 2026-09-09 01:37:12Z unchanged; putting the old stem back reproduces the original quiz digest `e7f206b0...` exactly; the 7 private copies still carry the old stem; 0 sessions. Across the whole database, no Study Pack was updated today and the only Note updated today is the D1 Note. **Still for the owner:** look at both Notes once in the app (D1 formulas render; D2 item 3 shows the new stem). **Do not regenerate either Note.** The answer-position pattern (all five keys option A) in D1's pack remains recorded for later quiz-quality maintenance.

## 10. Spot-checks complete; one correction (2026-11-06)

All 11 pending spot-checks passed (`bsis-year1-shared-note-spot-check-results.md`). Two things surfaced that adjust earlier statements: (1) **the live quiz shuffles choice order from a hash of the question text and the choices**, so the D2 stem edit changed item 3's displayed choice order once (harmless: 0 sessions; D2 check section 9 corrects the earlier "no effect"); (2) **the stored key positions follow a template across the catalog** (item 1 keyed B in 153 of 214 packs, never D), which the same display shuffle masks for learners. That lowers the practical weight of D1's all-keys-A finding, which remains recorded for quiz-generation maintenance but is not a learner-facing guessable pattern in the app.

## 11. Phase 1 applied and verified (2026-11-06)

The owner ran, in order, the RA 10364 correction, the five-Note Information Systems tag, and the Global Citizenship tag; Claude re-read production with `SELECT` only and read the three apply logs (each shows its preconditions and post-checks passed and `COMMITTED`).

| Check | Result (FACT) |
|---|---|
| Global Citizenship body | md5 `9e3e316db280f70073ca1ce485abd01b`, 3,516 characters, "in 2013;" present, "in 2012" gone; `updated_at` 2026-11-06 10:20:08Z; its Study Pack unchanged (`updated_at` 2026-11-05 12:46:46Z) |
| Information Systems on the six Notes | 1 row each; each Note's programs equal its previous programs plus Information Systems (the two Art Notes and Film: Computer Science, Information Systems, Information Technology; the three history, technology and Global Citizenship Notes keep their other programs) |
| Associations created today | exactly **6**, all on the six target Notes; **0** on any other Note; no Note has a duplicate Information Systems row; the global Information Systems count is 237 (231 before) |
| Bodies, visibility, Domain Context, Study Packs of the other five | unchanged (same md5 and `updated_at` as before; Study Pack `updated_at` unchanged) |
| Whole database | the only Notes updated today are *Probability and Basic Statistics* (D1) and Global Citizenship (the correction); no Study Pack was updated today |
| BSIS shared Notes missing Information Systems | **0** (V5 equivalent: every member of the BSIT Year outside Discrete Mathematics carries it, a superset of the 182 reused ids) |

**Consequence:** the shared-Note metadata prerequisites of the BSIS build are complete. The TSV and workbook already showed the six Notes with `+Information Systems`, so production now matches them and **no regeneration is needed**. Hold **H1** (Global Citizenship wording) is discharged. Remaining before publication: creating and reviewing the twelve Fundamentals of IS Notes, the taxonomy review (N12 first), then the collection build. **Do not use "Regenerate note" on Global Citizenship.**

## 12. FIS creation log (owner creates; Claude verifies read-only)

| Date | Code | Note id | Check | Result |
|---|---|---|---|---|
| 2026-11-06 10:28:44Z | **N1** | `a96f4be3-01d8-4ab5-aec7-884a7cb9b786` | F1/F2 equivalent | **PASS.** Exactly one Note with the title or the Subject. Title exact; Subject `Fundamentals of Information Systems`; Domain Context unset; level COLLEGE; programs = Information Systems only; PRIVATE; status DRAFT; 4,440 characters; raw md5 equals the normalized md5 equals the manifest (`65313cd7...`), so the body is byte-identical to the approved text; no carriage returns; first line equals the title; 0 Study Packs; 0 collection items; no generation enqueued |
| 2026-11-06 10:32:14Z | **N2** | `64f0e4fc-a03b-4413-bffd-922de59efdbd` | F1/F2 equivalent | **PASS.** One Note with the title; Subject, Domain Context unset, COLLEGE, programs = Information Systems only, PRIVATE, DRAFT; 5,498 characters; raw md5 = normalized md5 = manifest (`85681abc...`), byte-identical, no carriage returns, first line equals the title; 0 Study Packs, 0 collection items, no generation enqueued; same owner and the same empty `course_program` / `BOARD_TAKER` convention as N1 |

**Convention check (FACT).** `course_program` and `course_program_id` are NULL on N1, and `target_profile_type` is `BOARD_TAKER`; that is true of **all 214** existing Notes in the BSIT Year (programs live in the join table; the owner's profile supplies the target type), so N1 matches the convention. **Generation readiness (FACT, code):** `StudyPackGenerationContextResolver.assertGenerationReady` throws only when the Domain Context is unset *and more than one* program is joined; N1 has exactly one, so it passes, and the authoring domain resolves to the single joined program, "Information Systems" (`resolveCourseProgram`), with Subject and the note's College level. Next: N2, then the two Study Packs one at a time with explicit confirmation.

**Whole-database check after N2 (FACT):** the only Notes created today are N1 and N2; 0 Study Packs and 0 collections created today; the only new `note_course_program` rows since N1 are their two Information Systems rows. Nothing else moved. **Calibration gate:** both Notes passed the creation checks; next, the owner generates the two Study Packs one at a time with explicit confirmation, and Claude reviews each against `bsis-year1-fis-study-pack-review-template.md` before any further Note is created.

## 13. N1 Study Pack review and the open distractor-feedback question (2026-11-06)

N1's pack (`c2f27735-2071-453a-9e05-79dd18ef91b6`, DONE, 5 items, `gpt-6.1-sol`, PREMIUM; Note body unchanged): **content PASS** (all five keys supported by the body; no untaught material; summary and key concepts contain no claim absent from the body; scenario items present; stored key positions B C A D A). **Template H4 (explain every wrong option) is PARTIAL**, and the owner referred the question to Product UX rather than accept it as a limitation. Finding that makes it a product question: the generation prompt **tells the model not to restate or list choice text** (and the schema has one `explanation` string), because choices are shuffled at display time and the model once leaked choice letters. Coverage was also partial (no IS-versus-IT "NOT" item, no stage multi-select). **N2's pack is on hold until the Product UX answer.** The prompt is `bsis-year1-quiz-explanation-product-ux-prompt.md`.

**Resolved (D-43, 2026-11-06):** Product UX approved structured distractor feedback (Option C) as a separate future enhancement and told BSIS to continue now under the existing generator. N1's pack is accepted (PASS); N2's pack and the remaining packs are unblocked; the review standard was updated as quoted in D-43.

## 14. Calibration pair complete (2026-11-06)

N1 pack `c2f27735...` and N2 pack `5ea546f1-9dd0-4b38-92a5-2c2e0e43d489` (DONE, 5 items, `gpt-6.1-sol`, PREMIUM, 4,353 in / 1,489 out tokens; Note body unchanged, md5 `85681abc...`) both **PASS** the review under the owner-approved standard (D-43). **The calibration gate is cleared.** Both Notes remain PRIVATE until the taxonomy review and the plan build. Next: batch B (N3, N8, N4), created together and verified together, then their packs generated and reviewed together.

## 15. Batch B created and verified (2026-11-06)

| Created | Code | Note id | Result |
|---|---|---|---|
| 11:07:58Z | **N3** | `32cf4d66-1db2-49ab-bbab-5593a2098863` | **PASS.** 4,479 characters; raw md5 `8602193d...` equals the manifest; one Note with the title |
| 11:08:33Z | **N8** | `d1856b6e-06fa-4f5d-bc37-e057a92693a6` | **PASS.** 4,668 characters; raw md5 `70c2290f...` equals the manifest; one Note with the title |
| 11:09:01Z | **N4** | `c5d95c42-7ccb-4261-a50f-e7135e878777` | **PASS.** 4,598 characters; raw md5 `f5175ff2...` equals the manifest; one Note with the title |

All three: Subject `Fundamentals of Information Systems`; Domain Context unset; COLLEGE; programs = Information Systems only; PRIVATE; DRAFT; same owner and `course_program` NULL / `BOARD_TAKER` convention; no carriage returns; first line equals the title; 0 Study Packs; 0 collection items; no generation started. Whole-database: 5 Fundamentals of IS Notes now exist; the only new Notes since 10:50Z are these three; 3 new program rows, 0 Study Packs, 0 collections. Next: the three packs, generated one after another with explicit confirmation, reviewed together.

## 16. Batch B Study Packs reviewed (2026-11-06)

| Note | Pack id | Tokens in/out | Verdict |
|---|---|---|---|
| N3 | `1dcfd89d-6116-4698-84a8-a52ec69a228a` | 4,166 / 1,497 | **PASS** |
| N8 | `b0f746cd-f139-43df-a720-f539eedf61c4` | 4,188 / 1,498 | **PASS** |
| N4 | `2627964e-28e4-4ae3-9060-b874a6213660` | 4,187 / 1,464 | **PASS** |

All three: DONE, 5 items, `gpt-6.1-sol` PREMIUM, one pack per Note, Notes GENERATED and unchanged (body md5 equals the manifest), no failure code. Every key was checked against the body; every distractor is plausible but clearly incorrect; no item tests untaught material; summaries and key concepts contain no claim absent from the body. Under the D-43 standard no pack has a defect. **Coverage** against the specification: N8 and N4 match; N3 is partial (no failure-scenario or multi-select item), which five items cannot always cover, and is recorded, not a defect. **Stored key positions:** N3 B D A C B; N8 B D C B D; N4 B C D A A (item 1 is B in all five FIS packs so far, the catalog regularity the display shuffle masks).

**New catalog-wide observation (FACT, read-only query).** The correct choice is the **strictly longest** option in **40.9%** of single-answer four-choice items across the 214 BSIT-Year packs (817 items; 25% would be chance), and in **36.8%** of the FIS packs so far (19 items). The new packs are at or below the baseline, so this is a generator regularity, not a flaw of these packs. Unlike the key-position template it is **not masked by the display shuffle**; it is a test-taking cue relevant to exam takers. Recorded for quiz-generation maintenance, alongside the key-position template, outside this journey; it could be added to the distractor-feedback feature's quality scope if the owner wants one generation-quality workstream.

## 17. Batch C created and verified (2026-11-06)

| Created | Code | Note id | Result |
|---|---|---|---|
| 11:18:14Z | **N5** | `6dc50c6b-ca95-441b-ba8c-e2cd638ca292` | **PASS.** 3,526 characters; raw md5 `5dc75260...` equals the manifest; one Note with the title |
| 11:25:11Z | **N6** | `fc98044d-3135-4bfa-9d86-cc490137a1d2` | **PASS.** 4,266 characters; raw md5 `a2c11b94...` equals the manifest (this is the corrected body from the combined review); one Note with the title |
| 11:25:44Z | **N9** | `103af745-bda8-4772-801c-13a8fc148b27` | **PASS.** 4,025 characters; raw md5 `52c2a8d2...` equals the manifest; one Note with the title |

All three: Subject `Fundamentals of Information Systems`; Domain Context unset; COLLEGE; programs = Information Systems only; PRIVATE; DRAFT; same owner and `course_program` NULL / `BOARD_TAKER` convention; no carriage returns; first line equals the title; 0 Study Packs; 0 collection items; no generation started. Whole-database: 8 Fundamentals of IS Notes exist; every one has exactly Information Systems as its program; Study Packs exist on exactly the five accepted Notes (N1, N2, N3, N8, N4); the only new Notes since 11:10Z are these three, with 3 new program rows, 0 collections and 0 Study Packs on any other Note. Next: the three packs, then batch D (N11, N7, N12, N13). **Review emphasis for these three packs:** the N5 / N6 / N9 boundaries (routine reports versus decision support and business intelligence versus enterprise-wide systems), so no item blurs them.

## 18. Batch C Study Packs reviewed (2026-11-06)

| Note | Pack id | Tokens in/out | Verdict |
|---|---|---|---|
| N5 | `7703d512-dae4-4283-8442-ec5bf67d0dd0` | 3,996 / 1,396 | **PASS** |
| N6 | `a0ffa46c-2b6d-472e-b306-5640b542bcb8` | 4,093 / 1,411 | **PASS** |
| N9 | `190b4cb5-0c23-4fe1-abc5-f70c0e08af82` | 4,053 / 1,407 | **PASS** |

All three: DONE, 5 items, `gpt-6.1-sol` PREMIUM, one pack per Note, Notes GENERATED and unchanged (body md5 equals the manifest), no failure code. Every key was checked against the body; every distractor plausible but clearly incorrect; no untaught material; summaries and key concepts contain no claim absent from the body. **Boundary check (the stated review emphasis):** no item blurs routine reports (N5), decision support and business intelligence (N6) and enterprise-wide systems (N9); N5's and N6's distractors use each other's concepts only as clear contrasts. **N6** is consistent with the owner's corrected wording (business intelligence supports routine monitoring and less routine questions; the approaches overlap; nothing is presented as mutually exclusive). Eight Study Packs are now accepted (N1, N2, N3, N8, N4, N5, N6, N9).

**Catalog-regularity update (FACT, read-only).** Three of the eight FIS packs (N3, N5, N9) have the identical stored key sequence **B D A C B**. That is the generator, not these packs: across the 214 BSIT-Year packs, five sequences account for 63 packs (BCDAB 19, BDACB 13, BDACA 13, BDABC 10, BDAAC 8; 29%) out of 54 distinct sequences, against 1,024 possible. The display shuffle hides stored order from learners. The correct choice is also the strictly longest option in 11 of 30 single-answer FIS items (36.7%), in line with the 40.9% catalog baseline. Both stay recorded for quiz-generation maintenance (decision log D-46).

## 19. Batch D created and verified; all twelve FIS Notes exist (2026-11-06)

| Created | Code | Note id | Result |
|---|---|---|---|
| 13:35:29Z | **N11** | `5114b21f-ad74-4877-b312-9bcdf9ef6130` | **PASS.** 4,089 characters; raw md5 `518516b9...` equals the manifest (the corrected combined-review text) |
| 13:38:01Z | **N7** | `8042c270-5e5f-4770-af87-155dee91e837` | **PASS.** 3,403 characters; raw md5 `14a18288...` equals the manifest |
| 13:38:33Z | **N12** | `d051992d-6519-46ca-ae0a-1589c7e57882` | **PASS.** 4,670 characters; raw md5 `fa71e016...` equals the manifest (the corrected plan-driven and build-or-buy text) |
| 13:39:06Z | **N13** | `f63e5714-5e60-4abb-96ce-2165210d65dc` | **PASS.** 3,539 characters; raw md5 `8fdc0c62...` equals the manifest |

All four: one Note with the title; Subject `Fundamentals of Information Systems`; Domain Context unset; COLLEGE; programs = Information Systems only; PRIVATE; DRAFT; same owner and `course_program` NULL / `BOARD_TAKER` convention; no carriage returns; first line equals the title; 0 Study Packs; 0 collection items; no generation started.

**All twelve now exist and are consistent (FACT).** The generated verification query **F2 was run against production for the first time** (it executed without error on the real schema): all twelve rows read `OK` on Subject, Domain Context unset, level, programs = Information Systems only, and the normalized body hash; all twelve are PRIVATE; eight have status GENERATED with one pack (N1, N2, N3, N8, N4, N5, N6, N9) and four are DRAFT with none (N11, N7, N12, N13). Whole database: every FIS Note has exactly Information Systems as its program; no FIS Note has a Domain Context; no Study Pack exists on any non-FIS Note created today; 0 collections today. `bsis-year1-fis-created-ids.tsv` holds all twelve ids and the manifest, run sheet and verification SQL were regenerated: **no placeholder remains** (`NULL::uuid` count 0; no "create first" cell in the run sheet). Next: the four remaining Study Packs, then the taxonomy review (N12 first), setting the twelve PUBLIC, and the collection build.

## 20. Batch D Study Packs reviewed; ALL TWELVE packs accepted (2026-11-06)

| Note | Pack id | Tokens in/out | Verdict |
|---|---|---|---|
| N11 | `80ab8376-90b0-441f-b879-0864061d7af2` | 4,036 / 1,520 | **PASS** |
| N7 | `d87221f1-66d6-460a-b171-3924595afb19` | 3,962 / 1,444 | **PASS** (one closest-call item recorded) |
| N12 | `2d910f38-952f-4053-a2c4-507a3a468c93` | 4,193 / 1,514 | **PASS** |
| N13 | `d1ee879c-9c18-47c9-bd40-5db8b84a3ec2` | 4,011 / 1,534 | **PASS** |

All four: DONE, 5 items, `gpt-6.1-sol` PREMIUM, one pack per Note, Notes GENERATED and unchanged (body md5 equals the manifest), no failure code. Under the D-43 standard no pack has a defect. **The review emphasis held:** N11 and N7 do not blur strategy with technology-enabled change; N12 stays out of requirements, feasibility, fit-gap, total cost of ownership and weighted evaluation; the owner's corrections to N11 (mutual alignment) and N12 (plan-driven wording, build or buy in planning and analysis) are reflected in the packs and no item contradicts them. **Closest call (N7 item 2):** the mobile wallet (a product in the Note's taxonomy) against the distractor "Services"; resolved by the Note's own definitions, recorded, not a defect.

**Journey totals (FACT).** Twelve Study Packs accepted, 60 quiz items, all `gpt-6.1-sol` PREMIUM, about 49,400 input and 17,700 output tokens in total on the owner's ADMIN account. The same catalog regularities appear in the new packs as in the 214 existing ones (stored key sequence B D A C B in four of the twelve; the key as the strictly longest option in about 37% of single-answer items); they are generator-level and recorded with D-46 and D-48.

**What remains before publication (none is a content defect):**
1. **Taxonomy and reuse review**, with the four flags: N12 (`candidate, moderate`, first), N1, N2 and N3 (`candidate, low`). Any change goes through a separate metadata script and a TSV edit with a workbook regeneration, never a hand-edited workbook.
2. **Set the twelve Notes PUBLIC** (after the review; publishing a plan refuses otherwise).
3. **Build the collection** from the run sheet: the root and ten Subject Plans (owner choices outstanding: the root title's emoji prefix and the root's course/program), the 194 placements, then the verification queries V1 to V7.
4. **Publish**, then V8 and V9.
5. **Housekeeping:** a Backlog Index row for `docs/claude-plans/bsis-year1/` (and the Feature Planner handoff), the docs branch PR, and removing or ignoring the untracked run logs. Nothing here is committed.

## 21. Taxonomy review closed (2026-11-06)

Closed as decision D-52 (see section 3). The remaining publication checklist is: (1) the owner's two collection choices (the root title's emoji prefix and the root's course/program); (2) build the collection from the run sheet; (3) set the twelve Notes PUBLIC (recommended as the last gate before publishing, so they are not publicly visible while the journey is being built and verified); (4) run V4 to V7; (5) publish; (6) V8 and V9; (7) housekeeping (Backlog Index row, docs PR). Each production step is the owner's and needs explicit authorization.

## 22. Root and ten plans created; structure verified (2026-11-06)

The owner created the root (14:49:03Z) and the ten Subject Plans (14:51:42Z to 14:54:53Z). Read-only check (V1, V1b, V6 equivalents):

| Check | Result (FACT) |
|---|---|
| Root | id `27788999-e07f-469b-9c4c-8ddb6487a6cb`; title exactly `🗄️ BS Information Systems — 1st Year` (with the variation selector); PRIVATE; course/program `Information Systems`; level `COLLEGE`; no term; description 586 characters with md5 `d83b1e9e...` equal to the approved text; 0 items; exactly one root with this title |
| Ten plans | in sibling positions 0 to 9 in the approved order; every title exact; every description byte-identical to the shaping TSV (all ten md5 equal); terms First Semester = 1 for plans 1 to 5 and Second Semester = 2 for plans 6 to 10 (all ten set); all PRIVATE; level empty (the convention); estimated hours and target date empty; 0 items; same owner; no source plan |
| **Deviation** | **The ten plans' `course_program` is EMPTY.** The BSCS and BSIT builds carry their root's program on all ten plans (read the same day: BSCS 10 of 10 `Computer Science`; BSIT 10 of 10 `Information Technology`; both Years are PUBLIC with terms on all ten and level empty on all ten). The checklist asked for it on each plan |

**Fix (owner, 10 small edits):** set each plan's course/program to `Information Systems`, leaving the level empty. Re-check with V6 (`plans_with_cp` must read 10). No Notes have been added yet.

## 23. Collection build workbook added (2026-11-06)

`bsis-year1-collection-build-workbook.xlsx` (generated by `tools/build_collection_workbook.py`; needs openpyxl): README, Subject Plans, **Build Tracker** (all 194 placements with the exact Production Note ID and an Added column, plus the 5 Excluded rows flagged DO NOT ADD), **one sheet per Subject Plan** (section banding, exact labels, titles, ids in teaching order), FIS Notes (12 new; ids, pack ids, review results, reuse flags), Excluded, Decision Log. **Checked (FACT):** 194 placements, 194 unique ids, all in the verified manifest; the ten per-plan sheets hold 39, 42, 21, 8, 9, 31, 12, 10, 12 and 10 Notes (194) and equal the tracker row for row and in order; 12 packs, all PASS; 5 excluded; 54 decisions. This fills the gap that BSCS covered with its mapping workbook ("add the existing notes by id from the sheet"): the target-shape workbook (`docs/curriculum/bsis-year1-study-plan-target-shape.xlsx`) is the design view and carries no ids, so the BSIS build had only Markdown and TSV until now.

## 24. Build verified after publication; three defects found (2026-11-09)

The owner built and **published** the journey (root and ten plans PUBLIC, `published_at` 2026-11-09 13:09:04Z) and asked for a check. Read-only results:

**Correct (FACT).** Root and plans exist with exact titles, terms (5 + 5), root program Information Systems and level College. **Nine of ten plans match the manifest exactly** (one hash per plan over section label, Note id and order): Introduction to Computing 39, Computer Programming 1 42, Understanding the Self 8, Art Appreciation 9, Computer Programming 2 31, Fundamentals of IS 12, Readings in Philippine History 10, Purposive Communication 12, The Contemporary World 10. All 195 placed Notes are PUBLIC, GENERATED and have exactly one Study Pack; no duplicate placements; no section-spelling variants. BSCS (216 items), BSIT (214) and the private BSIT Semester Review root are unchanged; no BSCS or BSIT plan was updated since 2026-11-06. **No collection has adopted any BSIS plan** (`source_plan_id` count 0).

**Defects (all introduced at build time; the run sheet and workbook carried the right ids):**
1. **Wrong Note in Mathematics in the Modern World (position 3).** Production holds `85566327-9c2e-431a-9cf5-8bd84cf8513d` *Functions: Definition and Notation* (Subject Discrete Mathematics, Domain Context `COMPUTING`, programs Computer Engineering, Computer Science, Information Technology; **no Information Systems tag**) where the shaped journey has `93eda784-be61-4ec7-9d95-0411647fdb98` *Functions, Patterns, and Sequences* (General Education, includes Information Systems). A look-alike title; the plan therefore also fails "Information Systems on all 194" (one Note short).
2. **An Excluded Note was placed.** Understanding the Self holds *Self-Understanding and the Development of the Professional Teacher* (`6387a67a`, teacher-education framed, held out in BSCS and BSIT) under a learner-visible section label **"Held out of this journey (Excluded)"**.
3. **Plans' `course_program` is empty on all ten** (BSCS and BSIT carry their root's program on 10 of 10); the fix requested on 2026-11-06 was not applied.

**Fix (owner; the app's rules for editing a published plan are unverified):** (1) remove `85566327`, add `93eda784` as the third Note of "The Nature of Mathematics"; (2) remove `6387a67a` (the "Held out" section disappears with its last Note); (3) set course/program Information Systems on the ten plans. With zero adopters, edits now cannot diverge from anyone's copy. If the app refuses edits to a published plan, unpublish the Year (allowed; does not cascade to children), fix, and republish. Re-verify with the per-plan hashes and V5/V6.

## 25. BSIS Year 1 build verified complete (2026-11-09)

After the owner removed the look-alike Note (added the right one), removed the Excluded teacher Note, and ran the course/program repair (committed 14:20:10Z), a final read-only check found **no remaining defect**:

- **Placements:** 194 across the ten plans, 194 distinct Notes; every plan's hash over section label, Note id and order equals the manifest; the ten per-plan item counts are 39, 42, 21, 8, 9, 31, 12, 10, 12, 10. No Excluded Note and no "Held out" section label present.
- **Notes:** all 194 PUBLIC, GENERATED, exactly one Study Pack, and carrying Information Systems; the 12 Fundamentals of IS Notes are all in plan 7.
- **Collections:** the root and all ten plans PUBLIC (published 2026-11-09 13:09:04Z), the root and all ten plans read course/program **Information Systems** (the same convention as BSCS and BSIT), root level COLLEGE, plans' level empty, terms First Semester for plans 1 to 5 and Second Semester for plans 6 to 10, all descriptions byte-identical to the shaping TSV.
- **Elsewhere:** BSCS, BSIT and the private BSIT Semester Review root unchanged; **zero adopters** of any BSIS collection.

**Record of what the build taught (also in the playbook):** the first verification ran after publication and found a look-alike Note, an Excluded Note under a learner-visible section label, and empty plan course/program; a root re-save then cascaded a temporary value onto eight plans, which a guarded script corrected. Publishing cascades PUBLIC to every plan and sets only visibility, not course/program; a root course/program save fills only blank plans.

**Not verified:** how the journey renders for learners, whether it appears in the Information Systems recommendation slot, and the app's behavior on editing a published plan beyond what happened here.

**Housekeeping still open (owner/other roles):** a Backlog Index row for `docs/claude-plans/bsis-year1/` and the Feature Planner handoff (Release Implementor writes the Index); the docs PRs (BSIS journey files, and the process documents in their own PR); disposal of the untracked run logs; the reference sweep in other roles' charters.

