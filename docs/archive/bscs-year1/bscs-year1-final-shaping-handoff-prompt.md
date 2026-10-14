# NoteLib Note Strategist — BS Computer Science Year 1: Final Shaping Pass

> **Status update 2026-09-30 (final decision pass):** Workstream A is CLOSED (v0.164.0); the default TSV now carries `COMPUTING` on 136 rows and the Precalculus TSV is `(unset)` (D-33). D-07, D-08, D-11, D-14, D-17, D-21, D-26 are owner-approved. The programming reference-language recommendation is restated against the final corpus in `bscs-year1-programming-language-assessment.md` and `bscs-year1-programming-nsm-classification.md`; this prompt has been executed and is superseded by that pass.

Paste this whole file as the prompt, with the attachments listed in section 8.

## 1. Your role and this task

You are NoteLib's **Note Strategist**. The BSCS Year 1 curriculum is academically accepted; Workstream A (the computing Domain Context) is **closed and shipped**. Your job is the **final shaping pass**: turn the two preliminary TSVs into the most implementation-ready state that the still-open owner decisions allow, and say precisely what remains.

This is a shaping task. **Do not modify production, generate Notes, create collections, or write code.** Do not restart the research or redesign the curriculum.

## 2. What is now true (FACT, verified against the codebase on 2026-09-30, `main` at `edc91fb4`)

- **`Computing` is ratified and shipped in `v0.164.0`**: enum `COMPUTING`, label `Computing`, `quantitative = false` (`backend/.../entity/DomainContext.java`, `frontend/lib/domain-context.ts`). The ADR-001 amendment is written. **`REVIEW_SET_SHAPING_CONTEXT.md` now lists thirteen values, `COMPUTING` last** — use the attached (updated) copy.
- **Two-test rule (binding, per note):** (1) is computation, information, algorithms, software or computational systems itself the object of study? (2) could this exact canonical note, unchanged, serve at least one sibling computing program (CS, IT, IS, SE)? Yes to both → `COMPUTING`. **Multi-program tagging alone never implies `COMPUTING`, and Subject Plan or course membership never determines Domain Context.**
- **Owner corrections to the ratifying package (they override its body text):**
  1. **136 of 136** pending default-Year rows classify `COMPUTING_SHARED` (not 133/136). The 3 rows the package left ambiguous were resolved to `COMPUTING` by the owner.
  2. The **34 Discrete Structures I rows keep `applicable_programs = Computer Science` only.** They are NOT extended to IT/IS/SE. Domain Context and Applicable Programs are independent axes.
- **Precalculus is explicitly NOT `Computing`** (the binding negative case). Its Domain Context is not resolved by this ratification.
- **Not shipped:** the TSV edits (yours) and the R4 generation-quality pilot (its notes do not exist yet). A `[CHECKPOINT]` row exists for after Computing authoring reaches 10+ classified notes.

## 3. Current state of the two TSVs

- `bscs-year1-shaping.tsv`: 10 Subject Plans, **209 rows** (45 Reuse, 159 New, 5 Excluded). **136 rows carry `PENDING_PRODUCT_UX_DECISION`**: Introduction to Computing 38, Computer Programming 1 35, Computer Programming 2 29 (all three programs: Computer Science, Information Technology, Information Systems) and Discrete Structures I 34 (Computer Science only). The other 73 rows are `GENERAL_EDUCATION`.
- `bscs-precalculus-preparatory-shaping.tsv`: 1 standalone plan, **31 rows**, all `PENDING_PRODUCT_UX_DECISION`, Computer Science only, **no `academic_term` column** (a root has no placement).
- Both pass `build_review_set_workbook.py`. That run does **not** validate Domain Context legality.

## 4. Tasks

**A. Default Year TSV — replace the placeholder**
1. Replace `PENDING_PRODUCT_UX_DECISION` with `COMPUTING` on the 136 rows, **but apply the two-test rule to each row as you go and report any row that you believe fails it**, rather than assuming the owner's 136/136 covers your reading. Do not do a blind find-and-replace. Do not touch the 73 `GENERAL_EDUCATION` rows.
2. Leave `applicable_programs` unchanged on all rows, including the 34 Discrete Structures I rows.
3. Confirm every multi-program row now carries a real Domain Context (the server requires one at generation).
4. Re-run the validation battery: header order; `academic_term` constant per plan; sections contiguous and non-empty; no duplicate titles across both TSVs; every Reuse/Excluded row matches a production note by exact title and Subject; Applicable Programs catalog-exact; no Year/Semester/BSCS branding in titles; parity between the TSV and `bscs-year1-curriculum-coverage.xlsx`. Report counts from the script, not arithmetic.
5. Rename the file to drop `NOT-IMPLEMENTATION-READY` **only if** every blocker in section 6 that gates it is closed; otherwise keep the name and list what still blocks.

**B. Precalculus TSV — do not resolve by assertion**
Decision D-33 is open. Precalculus is pure mathematics, not `Computing`. Its rows carry exactly one program (Computer Science), so **leaving Domain Context unset is a legal fallback**: the authoring domain falls back to the program name, and the computation-guidance scan still fires. Cost: no classified tradition. Trap: tagging a second program later makes each row illegal until a Domain Context is set. Present the owner with the fallback versus a request for a further value (not yours to invent; the closed vocabulary is an architecture decision). Do not choose for them.

**C. Close and update the decision log and workbook**
Close D-13 and D-34 citing the final decision package. Keep D-33 open. Update every sentence in the attached BSCS documents that still says Workstream A is open (the refinement handoff, the reuse and metadata assessment, the Precalculus proposal and the decision log all say so, because they were written before ratification). Regenerate the workbook rather than hand-editing it.

**D. Flag, do not fix: the computation-guidance nuance**
`COMPUTING` is `quantitative = false`. The generator scans the authoring domain, Subject, tags and summary for keywords such as `compute`, `algebra`, `algorithm` and `math`. The label "Computing" does **not** contain `compute`. So Subjects with no keyword of their own can lose computation guidance once classified: from the TSV, "Programming Fundamentals", "Computer Science", "Computer Networking", "Web Development" and "Information Assurance and Security" carry none; "Discrete Mathematics" (`math`) and "Data Structures and Algorithms" (`algorithms`) do. This is **recorded in code as a verified but unmeasured nuance**, not a defect to fix here. Do not add computing prompt instructions or propose a keyword change. Record it as a risk for the R4 pilot.

## 5. Constraints (unchanged)

- Do not invent a Domain Context, Subject, program or catalog value. Any new one is a proposal requiring approval.
- Reuse exact production titles and Subjects. Never invent Note IDs. No candidate is promoted without a full-content read (0 of 19 are).
- No third collection level; Academic Term is Subject Plan metadata; Section is a Note membership label; no Year or Semester branding in Note titles.
- Keep both TSVs marked NOT IMPLEMENTATION READY until section 6 closes.

## 6. Still open (owner decisions and dependencies; do not close by assertion)

**Owner approvals:**
- **D-27 Reference language** (recommendation C++, fallbacks C then Java). **D-35 How the language reaches generation:** bulk generation has no language field; the language reaches a note only through its title, Subject or content. Four options are in the assessment; no recommendation has been made. **This gates the 44 programming notes classed S or M**, and the 20 neutral notes are not automatically free to author first either.
- **D-14** the 3 Subject shelves (Programming Fundamentals, Discrete Mathematics, Data Structures and Algorithms).
- **D-21** the GE applicability rule; **D-26** leaving the Precalculus root's course/program unset.
- **D-07** Filipino, **D-11** note granularity, **D-17** the logic overlap between Mathematics in the Modern World and Discrete Structures I, **D-08** promoting "Financial Mathematics in Everyday Life".
- **D-29** Year scope statement wording.

**Curator actions (not yours):** tag the 40 reused notes with Computer Science; read the 7 inspection candidates in full (2 default, 5 Precalculus).

**Sequencing after shaping (context only):** the R4 pilot on five varied Computing notes runs after `COMPUTING` is live and before bulk-generating the shared-computing corpus.

## 7. Deliverables

1. Revised default Year TSV (and the rename decision, with reasons).
2. Precalculus TSV unchanged unless the owner decides D-33, plus a one-page D-33 decision brief.
3. Updated decision log (`.md`) and regenerated coverage workbook.
4. A per-row exception report for the two-test rule (empty is a valid result, stated as such).
5. A short **readiness statement**: what is implementation-ready, what is blocked on which owner decision, in order of what unblocks the most notes.
6. A concise handoff back to the owner, labelling every statement **FACT**, **INFERENCE** or **PROPOSAL**.

## 8. Attachments

**Required**
1. `docs/curriculum/bscs-year1-shaping.tsv`
2. `docs/curriculum/bscs-precalculus-preparatory-shaping.tsv`
3. `docs/claude-plans/computing-domain-context-final-decision-package.md` (read the correction block at its top first; it overrides §4–§6 and §9–§10)
4. `docs/gpt-contexts/REVIEW_SET_SHAPING_CONTEXT.md` (updated: 13 values, two-test rule)
5. `docs/curriculum/bscs-year1-decision-log.md`
6. `docs/curriculum/bscs-year1-curriculum-coverage.xlsx`
7. `docs/curriculum/bscs-year1-refinement-chatgpt-handoff.md`

**Recommended**
8. `docs/curriculum/bscs-year1-proposed-study-journey.md`
9. `docs/curriculum/bscs-year1-programming-language-assessment.md` (D-27, D-35)
10. `docs/curriculum/bscs-year1-reuse-and-metadata-assessment.md`
11. `docs/curriculum/bscs-year1-precalculus-preparatory-proposal.md` (D-33)

**Optional**
12. `docs/curriculum/bscs-year1-curriculum-research.md` (evidence; unchanged)
13. `docs/curriculum/bscs-year1-audit-chatgpt-handoff.md` and `bscs-year1-production-discovery-audit.xlsx`
14. `docs/gpt-contexts/NOTES_AND_COLLECTIONS_CONTEXT.md`

**Do not attach:** `computing-domain-context-decision.md` and `computing-domain-context-product-ux-tightening-prompt.md` (superseded for action by the package); `bscs-year1-curriculum-chatgpt-handoff.md` (superseded); the old `bscs-year1-target-shape.*` files.

Note: `docs/architecture/ADR-001-canonical-knowledge-architecture.md` is very large; if the strategist needs the amendment, attach only the "Computing" amendment section (about lines 425–441).
