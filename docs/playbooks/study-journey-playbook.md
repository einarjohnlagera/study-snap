Status: ACTIVE playbook, corrected 2026-11-11. Supersedes the 2026-11-07 draft, which had drifted into manual Note authoring and repeated review (owner correction, 2026-11-11). **Process only: it changes no product behavior, no production data and no existing Study Journey or Review Set.** The tooling named in section 12 is a plan, not an implementation.

# Study Journey and Review Set playbook

One document for how NoteLib creates and maintains **Study Journeys** (curriculum-based degree Years, such as BSCS, BSIT and BSIS Year 1) and **Review Sets** (exam-focused, such as CE, ALE, LET, CPALE and PNLE). **It never overrides** `CLAUDE.md`, `AGENTS.md`, `docs/architecture/ADR-001`, `ADR-002`, `ADR-003`, `docs/product/SPEC.md`, `docs/product/EXAM_MODES.md` or `docs/features/*.md`; where they disagree, they win and this playbook is stale.

**The process, in one line: Research → Canonical Audit → Academic Reconciliation → XLSX → Approval → Product Generation → QA → Publication.** It is spreadsheet-first: the workbook is the owner-facing planning artifact, NoteLib generates the missing Notes, and manual authoring is an exception. **Do not redesign it into a manual authoring pipeline.**

## 1. Rules that apply everywhere

1. **A closed decision stays closed** unless new material evidence contradicts it. **Exception:** a *decaying fact* (production state, row counts, "Note X is tagged Y", code behavior) is a snapshot, not a decision: re-read it before it reaches a plan or a prompt (`CLAUDE.md`).
2. **One reviewer per task** unless a second adds value.
3. **Severity.** **BLOCKER**: must be resolved before proceeding. **EDITORIAL**: resolved by the Claude Note Strategist inside an authorized change. **OPTIONAL**: recorded, never delays.
4. **Owner-only, each needing explicit authorization:** every production write (through any route), SQL execution, Study Pack generation, any regeneration, Note or collection creation and publication, environment variables and deploys. Approval of a plan is not approval of its writes. **Claude never writes to production; it prepares, the owner executes (section 10).**
5. **No owner question lists.** One decision-ready recommendation per material decision.
6. **Evidence sufficiency:** one authoritative reference plus one cross-check is enough to proceed. University-specific variation is recorded as "a NoteLib reference arrangement, not any institution's prospectus" and is not re-researched.
7. **Generation is the default way to create missing Notes; manual authoring is exceptional** and needs a documented reason and the owner's approval (section 5).
8. **Never silently replace the product workflow.** If generation cannot meet an approved specification, name the exact gap and recommend a solution (section 6).

## 2. Roles

| Role | Owns | Does not |
|---|---|---|
| **Owner** | Approves material curriculum and target-shape decisions; authorizes generation, production writes and publication **separately**; runs every production write | n/a |
| **GPT Note Strategist** (ChatGPT; standing context `docs/agents/gpt/NoteLib Note Strategist.md`) | Researches the curriculum or exam coverage; proposes subjects, learning objectives and the Note list; evaluates canonical audit findings; tightens coverage and sequencing; resolves material curriculum and reuse questions; risk-based academic QA review | SQL, collections, generation, repository changes |
| **Claude Note Strategist** (Claude; charter `docs/agents/claude/note-strategist.md`) | Audits the proposed list against the canonical library; reports reuse, missing Notes, metadata conflicts and feasibility; maintains the structured planning data; generates and validates the XLSX; prepares execution plans, guarded SQL, verification and QA reports | Any production write; routine Note authoring; commits without instruction; pushing or opening PRs; product code |
| **NoteLib generation workflow** | Generates the missing canonical Notes and Study Packs from approved specifications and metadata, subject to quality validation | n/a |
| **Release Implementor** (Release Manager session), **Feature Planner, Prod Investigator** | As in their charters. Release Implementor is the **only** route to Codex briefs and the sole writer of `RELEASES.md` and the Backlog Index; it pushes and opens the PR | n/a |
| **Codex** | Tooling and implementation, briefed by Release Implementor | Direct interaction with the Claude Note Strategist |

A role may read another role's artifact and never edits it; change requests go back to its owner through the owner.

## 3. The phases

| # | Phase | Who | Output | Exit, then next authorized action |
|---|---|---|---|---|
| 1 | **Research** | GPT Note Strategist | Subjects, learning objectives and a proposed Note list, as the shaping TSV block plus an objectives sidecar (section 7). A sibling program sharing the same CHED memorandum inherits shared plans and researches only the delta | Proposal delivered to the owner relay |
| 2 | **Canonical audit** | Claude Note Strategist | Read-only audit against the real library: existing Notes (by id), reusable coverage, missing Notes, metadata conflicts (Subject, Domain Context, programs, level, visibility, pack count), feasibility and constraints. Reports; **does not decide academic questions** | Audit report handed back |
| 3 | **Academic reconciliation** | GPT Note Strategist | The tightened target shape: material reuse and coverage questions resolved, sequencing final | No open BLOCKER |
| 4 | **XLSX** | Claude Note Strategist | The complete target-shape workbook, generated from the TSV and structured data (section 7). Markdown is supporting documentation only | Workbook validated |
| 5 | **Approval** | Owner | The workbook is approved. **No routine manual drafting and no Strategist review sit between this and generation** | Owner approval, then authorization of generation per batch |
| 6 | **Product generation** | NoteLib (owner-authorized per batch) | Missing Notes and their Study Packs, one Subject, one Domain Context and one program set per batch (the workbook's *By Subject (bulk generate)* sheet is one run's setup each) | Batch complete |
| 7 | **QA gate** | Claude Note Strategist, then GPT Note Strategist | Automated structural validation of every Note and Study Pack; risk-based academic review (section 4); material defects escalated, systemic defects investigated as generator problems | Zero BLOCKERs |
| 8 | **Assembly and publication** | Claude prepares, **owner executes** | Guarded SQL files, checklists and read-only verification, in the handoff format of section 10; the owner runs each write and publishes | Verified read-only, then archive the working set |
| 9 | **Maintenance** | Per change type | Section 11 | Per section 11 |

## 4. Risk tiers and the QA gate

**Academic QA is a gate, not a drafting cycle.** The Claude Note Strategist does not tier its own work by feel (in BSIS a Batch 2 draft was rated "Level 1, approve, none needed" and the external review then corrected its framing). A tier comes from **objective triggers**:

- **Moderate or high** if *any* of: a named framework or model; a note that "definitions vary across texts"; a technical distinction between near-synonyms; a legal, regulatory, medical or safety claim; the first Note of a subject family (**PROPOSED definition, pending the GPT Note Strategist:** a *subject family* is a set of Notes sharing a Subject and Domain Context that no existing journey already covers); thin or no canonical coverage; conflicting sources.
- **Routine** only if none applies.

**Gate, after generation and before publication:**

1. **Structural validation of every Note and Study Pack** (sections 8 and 9), including that the approved objectives are covered.
2. **Moderate and high Notes: closer review** by the GPT Note Strategist (and targeted source verification).
3. **Routine Notes: sampled.** **PROPOSED starting values, not yet confirmed by the GPT Note Strategist or the owner, to be tuned from the pilot (section 6):** at least one in five and never fewer than three per Subject; any defect found in the sample widens review to the whole batch and starts a generator investigation.
4. **One combined review before publication**, because a fix after publication never reaches existing copies (Official updates are additive only, `ADR-003`; public-Note copies are snapshots). The per-review maximum and the large-catalog sampling rule are for the GPT Note Strategist to set; BSIS used 12.
5. **Calibration** validates the generation specification and the output quality (a small first batch of a new subject family), never repeated approval of drafts.

## 5. Authoring route

**Default: NoteLib generation** (Bulk Generate, or Create from topic) from the approved workbook row. **Manual authoring is an exception** that needs (a) a documented reason, for example a scope-sensitive Note the generator provably cannot carry (section 6), and (b) the owner's approval, recorded in the decision log. BSIS Year 1's hand-authored bodies (3,400 to 5,500 characters) were such an exception, not the model for the next journey. Outline-depth output (about 2,000 characters) suits definitional and overview Notes and is decided by the generation pilot's measurements, not by assumption.

## 6. Generation capability: facts, gap and recommendation

**Read 2026-11-11. Facts decay; re-read before relying on them.**

| Need | Today | Anchor |
|---|---|---|
| Approved learning objectives or scope constraints | **Not supported.** Bulk, Create from topic and bulk regeneration take no objective or scope field; the request carries topic (at most 160 characters), programs, Domain Context and Subject (bulk adds Authored Depth, collection and one section label). The typed topic is the final Note title (`v0.120.0`), so scope cannot ride in on it | `GenerateNoteFromTopicRequest.java`; `NoteBulkGenerationService.java:50`; `docs/features/bulk-generation.md` |
| Required academic depth | **Structurally bounded.** Fixed schema: overview, key idea, 3 to 6 core details, 2 to 4 why-it-matters, 3 to 5 quick-recall bullets; items at most 240 characters; whole Note under 700 words. By arithmetic that is a ceiling of about 4,000 to 4,500 characters, below the top of the BSIS authored band | `OpenAiLlmStudyPackService.java:77,106`; `prompts/study-pack-v1/note-generation-developer.txt` |
| Subject, Domain Context, Authored Depth, programs | Supported; one set per batch | `docs/features/bulk-generation.md` |
| Consistent Note structure | Supported (fixed schema) | same |
| Safe batch generation | Supported (50 topics, throttled sequential, receipt, retry) | same |
| Post-generation validation | Study Packs: existing server validators. **Notes: the server enforces only the JSON shape, the per-item cap and the 700-word cap (`OpenAiLlmStudyPackService.java:1385-1387,3147`); nothing checks depth or coverage of any objective** | `QuizValidationUtils`; the file just cited |
| Reliable Study Pack creation | Automatic after each Note; quality per section 9 | `docs/features/study-pack-generation.md` |
| Model | Admin bulk runs route to `LLM_MODEL_PREMIUM` (GPT-6 family, live since 2026-10-25). The "about 2,000-character outline" evidence is the BSCS pilot of 2026-10-06, **before** that switch, and **no depth measurement since was found**. The live environment value was not re-read | `docs/claude-plans/bscs-year1/bscs-year1-authoring-batch-plan.md` section 1; v0.174.0 |

**Verdict:** generation **cannot yet be shown to satisfy an approved specification** for scope-sensitive Notes. The input gap is certain, the depth ceiling is structural, and whether GPT-6 removed the outline limitation is **unmeasured, not resolved**.

**Recommended path (decisions are the owner's):**
1. **Measure first.** A pilot of about five topics of mixed risk, including one scope-sensitive Note, generated by the owner-authorized product workflow; the Claude Note Strategist then measures length, structure and objective coverage **read-only**. The result sets the sampling values in section 4 and the outline-depth rule below.
2. **Interim use.** Until a scope field exists, title-only generation suits definitional and overview Notes (a Note whose title alone fixes its scope). A Note whose correctness depends on a scope constraint is marked **NEEDS DECISION** in the workbook; it is not silently hand-authored.
3. **If the pilot shows a gap:** an optional per-topic objectives field feeding the Note prompt (and a Note-body validator) is a backend change, briefed through Release Implementor to Codex in a later release, never by this role.
4. **A generator defect is a product finding.** Hand it to Feature Planner or Release Implementor; do not work around it with manual authoring.

## 7. The workbook contract

The XLSX is **generated** by `docs/curriculum/build_review_set_workbook.py` from a shaping TSV (never hand-transcribed). The BSCS target-shape layout is preserved. It must carry:

- the Study Journey or Review Set title and description; Subject Plans and descriptions; sections and Note ordering; Academic Terms where applicable (all plans or none, frozen at publication);
- existing canonical Note ids; **Reuse / New / Excluded** classification;
- Note Subject, Domain Context, Applicable Programs (required per Note subject; the builder refuses a file without it) and **Authored Depth**;
- **generation readiness** and **unresolved metadata or coverage issues**;
- concise **learning objectives or scope** for each New Note, directly or through a linked structured file.

**Current builder input (10 TSV columns):** `plan_no, subject_plan, plan_description, section, note_title, note_subject, domain_context, applicable_programs, status, academic_term`. **Not yet carried:** canonical Note id, Authored Depth, generation readiness, issues, objectives. **Interim, with no builder change:** the id manifest and an objectives sidecar (`note_title<TAB>objectives`, one to three concise lines) generated by documentation-only generators under `docs/`, referenced from the workbook description. **Target (via Release Implementor to Codex):** optional builder columns for the five items. They must be optional, because the legacy `let` and `civil-engineering` TSVs still owe their `applicable_programs` backfill.

**Generation readiness**, per New row: **READY** when Subject, Domain Context (or exactly one program), Authored Depth and Applicable Programs are set, objectives are written, and the topic is at most 160 characters and unique in the canonical library; **NEEDS SPEC** when objectives or a field are missing; **NEEDS DECISION** when scope cannot be carried by the generator (section 6) or a metadata conflict is open. Reuse and Excluded rows carry no readiness.

## 8. Note validation (automatable, after generation)

Per generated Note: the schema's five parts are present; quick-recall has **3 to 5** bullets (the generator's range; the old "exactly five" validator rule was wrong for generated Notes); length and item caps; long sentences and near-duplicate sentences; math delimiters balanced; every approved objective is covered by the body; Subject, Domain Context, Authored Depth and programs equal the workbook row; no duplicate canonical title. The validator is a documentation-only tool (section 12); until it exists these are checked by script or read-only SQL and reported.

## 9. Study Pack standard and handling

**Review standard (owner-approved, D-43):** *"Each quiz question must have one unambiguous correct answer supported by the source Note, a clear explanation of the correct reasoning, and plausible but clearly incorrect distractors. The absence of individual distractor explanations is a known limitation of the current generator, not a pack-specific defect. However, misleading or ambiguous distractors remain content defects and must be corrected."* (Structured distractor feedback is a separate Feature Planner workstream.)

- **Before generation:** metadata exact; Domain Context unset only with exactly one program.
- **After generation (automatable):** five items; keys inside the choice range; multi-select counts match the stem; two-statement items use the four fixed choices; summary and key concepts present; math delimiters balanced; no choice letters in explanations; the longest-key cue; each key concept appears in the body. **Existing server validators already cover statement pairs, format and stem, multi-select counts and answer-explanation consistency** (`QuizValidationUtils`); reuse them. The stored key-position pattern is not learner-facing (the live quiz shuffles choices), so it is recorded, not failed.
- **Study Pack QA is its own gate**, independent of the Note review. Human review covers keys supported by the body, distractor quality, and any flagged item; moderate and high Notes get their keys checked. A blind key check follows the existing Codex blind-checker design (dual-AI plan D11; `BlindExport`).
- **A single defective item:** a wording-only fix by guarded, owner-run SQL on a pack with zero sessions, leaving `quiz_stamp` and the pack `updated_at` unchanged (the D2 pattern).
- **Regeneration is always an explicit per-pack owner decision.** It replaces the whole quiz and bumps `quiz_stamp`, forfeiting learners' mastery. Thresholds only *recommend* it.
- **Copies:** a fix reaches the canonical pack only; existing copies stay as they are by design.

## 10. Production handoff: Claude prepares, the owner executes

**Reuse of canonical Notes:**

| Situation | Action | Gate |
|---|---|---|
| Existing Note matches the objective, correct metadata | Reuse as is | none |
| Matches but lacks a program tag | Metadata-only SQL file, owner-run | the owner's authorization |
| No equivalent | New Note, by generation (section 5) | owner authorizes the batch |
| A new Note "could be reused elsewhere" | Record `reuse_flag` and `reuse_trigger` in the manifest; **no review gate** | none |

**Trigger rule (D-52):** *revisit a flagged canonical Note when a future Study Journey or Review Set has a specific learning objective the existing Note might satisfy unchanged.* Compare the full body to the objective, check the pack, decide, and **if a second Applicable Program is approved, first establish an explicit Domain Context (ADR-001).** ADR-001 enforces that rule only **at generation time**, so every metadata SQL file must assert it itself. Automated matching only proposes candidates; title and Subject matching gives false positives.

**Checklist for every journey:**

1. **Preflight (read-only):** ids exist; titles, Subject, Domain Context, level, visibility, pack counts and programs equal the manifest; no duplicate title; the target program row exists; no collection with the target title exists.
2. **Guarded SQL pattern:** one psql session per file; dry run by default; commit needs both `-v mode=commit` and `-v confirm=owner-approved`; hard preconditions that `RAISE EXCEPTION`; `INSERT ... RETURNING` audit; post-write assertions; an **id-exact rollback**; tested against a throwaway local PostgreSQL before handoff; never a separate `-c "COMMIT;"`; never an IDE console.
3. **Owner handoff message** (the format the owner has run every time): the working directory, then ordered steps, each with its dependency stated, a dry-run command teeing to `<name>-dryrun.log`, the apply command with `-v mode=commit -v confirm=owner-approved` teeing to `<name>-apply.log`, and a verification `SELECT` with the **exact expected values**. Ids in verification queries are copied from the file, never retyped. The charter has the template.
4. **Build order:** root, then plans (course/program, term and level exactly as the convention), then Notes by id, **one plan at a time**, verifying read-only after each.
5. **Visibility:** Notes go PUBLIC as the **last gate before publishing** unless the owner chooses otherwise (publishing refuses unless every Note is public); they are publicly visible the moment they are set.
6. **Publish** is the owner's point of no easy return. Terms freeze at publication, and publishing cascades PUBLIC to every plan. **Publish only after the per-plan placement check (expected versus actual by id, section and order; Excluded Notes absent; duplicates none) and the program, visibility and pack checks all read clean.** BSIS was published before that check and went live with a look-alike Note and an Excluded Note (D-58).
7. **Post-publication:** read-only `SELECT` verification (visibility, terms, membership) plus a few pages opened by the owner. **No automated page walk against production** (anti-drift: no artificial traffic; the database kills followed catalog-listing bursts).
8. **Partial failure:** everything before publication is private and reversible. Deleting a root orphans its children; check before deleting. Unpublishing is allowed and does not cascade to children.

## 11. Maintenance

- **Content update to a shared Note** changes every journey that contains it, so it is a guarded, owner-run SQL with the Study Pack untouched, never a regeneration by default.
- **Structural update** (add or reorder Notes) reaches adopters only through an **additive Official update**; it never edits adopters' copies.
- **Metadata correction** never requires Study Pack regeneration.
- After any expansion, rerun the read-only verification and the drift check (programs, visibility, pack present).

## 12. Automation plan (a plan; nothing implemented)

| # | Tool | Value | Effort | Risk | Where |
|---|---|---|---|---|---|
| 1 | **Workbook builder: optional columns** (Note id, Authored Depth, readiness, issues, objectives) plus its test | high | low | low | Release Implementor to Codex (shared builder) |
| 2 | Journey preflight and canonical-audit report, read-only | high | low | low | documentation-only tool |
| 3 | Journey-agnostic artifact generator (manifest, run sheet, objectives sidecar, verification SQL); the BSIS generators are the prototype | high | low | low | documentation-only tool |
| 4 | **Generated-Note validator** (section 8) and a trimmed Study Pack validator reusing the server validators | high | low | low | documentation-only tool |
| 5 | Guarded-SQL template with a local test harness and an ADR-001 precondition assertion | medium | medium | low | via Release Implementor to Codex |
| 6 | Candidate matcher (proposes only) | medium | medium | low | documentation-only tool |
| 7 | Post-publish verification, **SELECT-only** | medium | low | low | documentation-only tool |
| 8 | **Generation scope field** (per-topic objectives into the Note prompt) and generator quality work (distractor feedback, key balancing); only if the section 6 pilot shows the gap | high | high | medium | Feature Planner, then Release Implementor to Codex |
| 9 | Collection import from the workbook (removes about 190 manual UI adds) | high | high | medium | candidate for Feature Planner |

Tooling authority: the Claude Note Strategist may write documentation-only generators that read the TSV and manifest and write files under `docs/`. Anything touching `backend/`, `frontend/` or the shared builder goes through Release Implementor to Codex.

## 13. Document map

- **Authoritative:** ADR-001, ADR-002, ADR-003; `docs/curriculum/` (`README.md`, `review-set-workbook-spec.md`, `review-set-reshape-read.sql`, `build_review_set_workbook.py`); this playbook; `docs/agents/gpt/NoteLib Note Strategist.md`; `docs/agents/claude/note-strategist.md`.
- **Per journey (`docs/claude-plans/<journey>/`):** the shaping TSV and workbook, one decision log, the id manifest and build workbook, SQL files and the logs of those the owner ran, one review log. Evidence the owner required kept is never deleted.
- **Retire:** one document per review batch (fold into the review log); one prompt per Strategist round (replace with the one-page brief in the GPT context); overlapping audit, evidence and readiness documents (keep one evidence appendix).
- **Backlog Index:** `docs/playbooks/` is outside the kickoff step-8 scan, which is acceptable for a standing document; per-journey folders still need their row from Release Implementor.
- **No rename sweep is owed.** Other charters and the dual-AI plan still call the Claude role "Note Strategist", which is correct again; the ChatGPT role is the "GPT Note Strategist".

## 14. Evidence and caveats

- Row counts: BSCS 218 (50 Reuse, 163 New, 5 Excluded); BSIT 222 (214 Reuse, 0 New, 8 Excluded); BSIS 199 (182, 12, 5). BSCS has 56 decision-table rows but 40 unique ids. The three journeys are not comparable (163, 0 and 12 new Notes).
- BSIS was fast partly because it inherited BSCS and BSIT reuse; do not extrapolate to a non-sibling program. BSIS's 12 new Notes were hand-authored as an exception; the BSCS pilot of 2026-10-06 is the generator evidence, and it predates GPT-6 routing.
- Low-yield reviews were not inert: the 12 BSIS pack reviews found no pack defect but produced the standard (D-43) and the generator regularities (D-46, D-48). D-nn numbers refer to the BSIS decision log.

## 15. Approved decisions

**Owner, 2026-11-07:** (1) closed decisions with the decaying-facts exception; (2) objective risk triggers instead of self-tiering; (3) reuse flags recorded with a trigger, no review gate; (4) the tooling is approved as a plan.

**Owner, 2026-11-11, correcting the 2026-11-07 draft:**
1. The operating model is Research → Canonical Audit → Academic Reconciliation → XLSX → Approval → Product Generation → QA → Publication. **The 2026-11-07 "no default authoring path" decision is reversed: generation is the default and manual authoring is exceptional.**
2. Roles: **GPT Note Strategist** (ChatGPT) and **Claude Note Strategist** (this repository's session, charter `docs/agents/claude/note-strategist.md`). The "Content Operations Lead" name is retired.
3. The XLSX is the mandatory owner-facing artifact.
4. Claude prepares SQL files and the psql handoff steps; **the owner runs every production write**, never Claude.
5. Everything created in this workstream is committed on a branch cut from the active release branch, in the worktree; **the Release Manager session pushes it and opens the PR** so it joins the active release.
