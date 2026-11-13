# v0.182.0 class-B sampling counts and scoring process: PROPOSAL for owner approval before freeze

Status: **PROPOSED, 2026-11-13, nothing frozen.** Written by the release session in response to the owner's class-B decisions of 2026-11-13. It changes no criterion of `2026-11-12-v0182-llm-migration-preregistration.md` until the owner approves it and the release session records it there as a numbered pre-freeze revision. No paid call, benchmark run, environment change, merge or deployment is authorized by this document. Every number is computed with its method, sourced, or labelled `PROPOSED`.

## 1. Owner decisions recorded (2026-11-13)

- **B1:** class-B inputs are **controlled, representative synthetic fixtures with fixed sampling and a preregistered rubric**. Real anonymized cases only if available and separately approved. Scoring is **deterministic schema checks plus human validation of semantic quality**. **The Quick Review study tip stays in the stronger evaluation tier**, given its learner impact. The owner asked for the exact sampling counts and the scoring process before freezing; this is that proposal.
- **B2:** the production read timeout is not set in Render; the code default (**180 s read, 10 s connect**) is preserved for the baseline and not modified without separate approval (preregistration Addendum 2).
- **B3:** provider documentation is verified against official OpenAI pages; the **$12 Stage 1 cap stays provisional until the freeze** (preregistration Addendum 2).

**Interpretation to confirm:** "stronger evaluation tier" for the study tip is read as **primary N = 30 (the class-A size), the full four-dimension rubric, and a 30-pair confirmation reserve**, instead of the lighter class-B N = 12. If the owner meant something else, only section 2's study-tip row changes.

**A fact that bears on the collection companion's risk:** `generateCompanion` is admin-only (`NoteCollectionService.java:1018-1019`, `assertAdmin(user)`) and returns a **draft** (`GeneratedCompanionContentResponse`), so its output reaches learners only after a curator publishes it; this is why N = 12 remains proportionate for it. Ask Companion and Interview critique are learner-facing but have zero recorded recent use (preregistration section 3, U1/U2).

## 2. Exact sampling counts (PROPOSED)

| Workflow (route) | Primary N | Confirmation reserve (used only on a T2 trigger) | Arms in Stage 1 | Primary outputs in Stage 1 |
|---|---:|---:|---|---:|
| Quick Review study tip (`INTERACTIVE_QUIZ`) | **30** (was 12) | 30 | baseline, Luna | 60 |
| Collection companion (`PAID_INTERACTIVE`) | 12 | 30 | baseline, Luna | 24 |
| Ask Companion (`CRITIQUE`) | 12 | 30 | baseline, Luna | 24 |
| Interview critique (`CRITIQUE`) | 12 | 30 | baseline, Luna | 24 |
| **Class B total** | **66 pairs** | 120 reserve fixtures, frozen now, consumed only on a trigger | | **132 outputs** |

Class-A sizes are unchanged (6 workflows × 30 = 180). **Total primary cases in Stage 1 become 246** (was 228); the spend effect is negligible because the study tip is the cheapest call in the plan (about 250 input and 30 output tokens, `docs/claude-plans/2026-11-10-next-releases-sequence-recommendation.md:113`), and the release session will recompute section 5 before freeze.

### Effect on the `INTERACTIVE_QUIZ` route statistics (computed, must be adopted with the study-tip change)

The route pools **180** primary cases instead of 162. With the unchanged margin **D ≤ 10** the equivalent-candidate false-FAIL **rises above the owner's 20% target at a 5% parse-failure rate**. Computed with the preregistration's own exact method (section 1: T3 convolution at `p=.5, ρ=0`; T2 confirmation `F_n`; the all-tier joint formula `a^n[(1−t_n)+t_n B a^30]`; independent families), validated first by reproducing the published 162-case figures exactly (T3 `.12165`; all-tier no-FAIL `.834915` at `r=0` and `.811979` at `r=5%`; Paid `.873081`):

| Interactive route, 180 cases | T3 false-FAIL | all-tier equivalent false-FAIL at `r` = 0 / 0.5% / 1% / 2% / 5% / 6% | critical +5 points no-FAIL at `r=1%` | critical +10 points no-FAIL at `r=1%` |
|---|---:|---|---:|---:|
| margin **D ≤ 10** (unchanged) | .13418 | .1810 / .1810 / .1812 / .1827 / **.2059** / .2231 | .1025 | .0008 |
| margin **D ≤ 11** (PROPOSED) | .11269 | .1607 / .1607 / .1609 / .1624 / **.1862** / .2038 | .1051 | .0009 |
| margin D ≤ 12 | .09378 | not tabulated | | |
| for reference, 162 cases at D ≤ 10 (published) | .12165 | .1651 at `r=0`, .1880 at 5%, .2039 at 6% | .1195 at +5 | .0012 |

**PROPOSED:** adopt **D ≤ 11** for `INTERACTIVE_QUIZ` when the study tip is N = 30. It restores the 20% target through the 0–5% parse grid (18.62% at 5%; the target fails at 6% exactly as before, 20.38%) and costs essentially no power against a +5 or +10-point critical increase (the T2 rule is per workflow and unchanged). The **PAID** (D ≤ 5) and **CRITIQUE** (D ≤ 4) routes are unchanged (42 and 24 cases). The owner approves the margin change as a threshold edit before freeze, never after seeing output. The per-workflow T2 worst-case false-FAIL for an N = 30 workflow is `.00923` at `p_b=.02`.

## 3. Fixtures: synthetic, representative, fixed, frozen before any run (PROPOSED)

**Principles.** No learner text, answer, conversation, account identifier or private manifest is used or stored. Source content is the official public notes already used for class A (`sample-query-quality.sql:8-24`; preregistration section 6), so the grounding material is real public text. The synthetic part is the learner interaction wrapped around it. **Fixtures are authored by Codex outside API spend under a frozen authoring instruction, hashed and frozen before any generation. Disclosure: this project's Codex runs on `gpt-6-sol` (`~/.codex/config.toml`, and the run header of the 2026-11-13 harness round), a model of the SAME family as the Luna and Sol candidates, so the fixtures carry a self-preference risk (they may be easier or more natural for a GPT-6 model than for the GPT-4.1 baseline). The mitigations are: the owner spot-checks at least 10% of each workflow's fixtures (at least 3) for realism and flags unrealistic ones for replacement before the freeze, not after; the fixtures are plain scenario data, not model outputs to be graded; and the same fixture is given to every arm. This risk cannot be removed, only bounded, and the report states it.** The case order is fixed by a deterministic salted hash; the reserve fixtures are generated in the same pass, disjoint from the primary ones, with their own hash.

Stratification cells (every cell filled equally; the final cell dimensions are fixed in the class-B adapter brief after reading each service's real input, and any cell the real input cannot express is stopped and amended, never silently replaced):

| Workflow | Strata (primary cells × cases per cell = N) |
|---|---|
| Study tip (N=30) | missed-question count `{1, 2–3, 4–6}` × source level `{COLLEGE, BOARD_EXAM_REVIEW}` = 6 cells × 5. Missed items are chosen from the real Study Pack quiz items of the public note by a seeded deterministic rule, so the "mistake" is synthetic but the question and key are real. |
| Collection companion (N=12) | requested companion sections `{one section, two sections, all sections}` × collection size `{small, large}` = 6 cells × 2. Collections are built from public notes (admin-only generation, `NoteCollectionService.java:1018-1028`). |
| Ask Companion (N=12) | prior turns `{0, 1–2, 3+}` × question type `{answerable from the authored companion, not answerable from it}` = 6 cells × 2. The not-answerable half tests the "invents information beyond its authored companion" critical dimension (`docs/features/ask-companion.md:79-86`). |
| Interview critique (N=12) | selected answer `{correct, incorrect}` × question type `{three types from the real Interview Practice generator}` = 6 cells × 2. The incorrect half tests "advice contradicting the supplied answer/key". |

## 4. Scoring process (PROPOSED)

**Step 1, deterministic checks (automated, no human, no model):** the production parser and its validators run on every output; strict schema validity; every required field nonblank; **no residue of provider control text** (for example a stray `</assistant:commentary>` token or an unparsed second message item, because of the Luna report in preregistration Addendum 2); the output language and length bounds as recorded fields only (a length change is a reported tier-3 component, never a failure by itself). These produce the tier-1 and tier-3 structural events and are never replaced by a human or model judgement.

**Step 2, blind pairing:** for each case the baseline and candidate outputs are placed in a randomized order (fixed seed recorded in the frozen manifest; the arm key is stored separately, never shown to any scorer). No arm, model name or token count appears in what a scorer sees.

**Step 3, semantic scoring on the approved four-dimension rubric (preregistration section 7, OWNER DECISION D3):** *task fulfilled*, *grounded in supplied context*, *actionable and helpful without unsupported claim*, *no critical misleading feedback*; a case passes quality only if all four pass; a critical misleading verdict, fabricated source claim, advice contradicting the supplied answer or key, or (Ask Companion) invented information beyond its authored companion fails the last dimension and is a **T2 critical** event, any other failed dimension is a **T3** quality deficit.

**Who scores (the owner decides, with a recommendation):**

- **Option P1, human scores everything.** 66 blind pairs in Stage 1. At a PROPOSED planning figure of 3 minutes per pair (not measured), about **3.3 hours**; up to 30 further pairs per triggered workflow for a reserve.
- **Option P2, two independent blind Codex scorers, human validation (RECOMMENDED).** Two independent blind model scorers score all 66 pairs (outside API spend; never asked to judge themselves; the same blind packet as the human). **Disclosure and recommendation:** Codex here is `gpt-6-sol`, the same family as the candidates, so a Codex-only scorer pair shares the candidates' blind spots and may favour GPT-6-style output; **the recommended pair is one Codex scorer plus one scorer from a different model family** (a cold Claude agent run by the release session, which is the release session's to run and sits outside API spend), with the human review below as the mitigation of last resort. The owner may instead choose two Codex scorers; then the escalation rule below is the only protection. **The human reviews: every pair either scorer marks as failing any dimension or critical; every pair where the two scorers disagree; and a random audit sample of 20% of the remaining pairs, at least 3 per workflow (seed fixed in advance).** The human's verdict controls wherever it differs. **Escalation rule:** if the human changes more than 1 in 5 of the audited items for a workflow (a model-scorer reliability failure), the human scores every pair of that workflow. Planning estimate, not measured: about 25 to 30 pairs reviewed, about 1.5 hours.
- Either way the scorer identities, the human reviewer's identity (a role label, not a name, in anything committed), the agreement rate between the two model scorers and the human override rate are recorded and reported; a disagreement left unresolved makes that pair INCONCLUSIVE, never a pass.

**Step 4, score file shape and validator (a build item, not yet built):** one JSON line per scored output with a blind item id, the four binary dimensions, a critical flag with a short reason code (no free text copied from the source), scorer id and round, validated by a new `ClassBScoreFiles.validate` modelled on `QualityScoreFiles.validate` (it rejects missing dimensions, an unknown scorer, a duplicate item, a critical flag without a failed fourth dimension, and any row that does not map to the frozen manifest). Without it every class-B workflow stays INCONCLUSIVE (preregistration section 7). The receipts (counts, SHA-256 of the manifest, the score files and the arm key) are appended as a dated addendum before any decision, mirroring the Challenge Quiz preregistration.

**Privacy and storage:** fixtures, outputs, score files and the arm key stay outside git (under `backend/target` or an external private directory); only counts, strata and SHA-256 digests enter the repository.

## 5. What the owner approves, and what changes if approved

1. The study-tip tier reading (N = 30, full rubric, 30-pair reserve) and the Interactive margin **D ≤ 11** (section 2).
2. The cell design (section 3), the fixture authoring and the 10% realism spot-check.
3. Scoring Option P1 or P2 (section 4), and the audit and escalation numbers.
4. If approved, Revision 2.2 must ALSO update the calculator's frozen parameter file (`frozen-route-gates.json`: `STUDY_TIP` `primarySize` 30, `INTERACTIVE_QUIZ` margin 11, and a new SHA-256 in the detached `.sha256` file) and add an oracle test for the 180-case figures above, otherwise the calculator and the frozen document disagree.
5. If approved, the release session records them in the preregistration as **Revision 2.2** (a pre-freeze revision, with the recomputed section-1 and section-5 figures), updates the response document, and the class-B adapter brief is written; nothing is frozen until the owner approves the exact document at a pinned commit.
