# Assessment Architecture — Stage 1 Audit

**Status: AUDIT COMPLETE. ARCHITECTURE AND SEQUENCE APPROVED 2026-09-02. NOTHING IMPLEMENTED YET.**
Written 2026-09-02 against `feat/v0.103.0-mixed-retrieval`, from the owner + Product UX brief of the same
date, then **revised on an owner tightening pass the same day** — §0 (gating taxonomy), §7 (sampling),
§9 (formats), §8 (Board Exam count), §12.1 (bank bypass), §13 (sequence) and §14 (decisions) all carry that
revision. **The Stage 1 repo findings are ACCEPTED and are not re-audited.**

**⚠️ This document is the basis for several releases. A confident wrong claim here is far more expensive
than an admitted gap, so every finding below is anchored to code that was read, with `file:line`. Where
something could not be verified it says so.**

**Method.** One cold agent performed the repo audit with no inherited context, explicitly instructed to
distrust `docs/`, `RELEASES.md` and code comments — because in the two preceding releases a security check
shipped with zero executed coverage while comments asserted otherwise, a test asserted a defective value as
correct, and a feature shipped that no user could reach. **Its highest-stakes claims were then independently
re-verified in the main session; one was corrected (§13.5) and is recorded here as corrected.**

---

## 0. What is and is not gated — three different things, kept apart

**Owner ruling, 2026-09-02.** These are routinely conflated, and conflating them here would either stall the
work or quietly change what customers pay for.

| | Status for this initiative |
|---|---|
| **Product / checkpoint gating** | **NONE.** This initiative is **not evidence-gated and not checkpoint-gated.** Every item below concerns architectural correctness, integrity, or an already-demonstrated defect. Observational checkpoints may continue to measure outcomes, but **they do not block any assessment slice.** |
| **Engineering pre-signoff verification** | **PRESERVED IN FULL.** Cold agents, mutation testing, executed-test counts, `advisor()` before the prompt and on the diff. **These are quality controls, not product gates**, and "nothing is gated" does not relax any of them. |
| **Plan-tier entitlements** | **UNCHANGED.** Free / Plus / Pro is a **separate monetization contract.** Nothing here removes, widens or adds a subscription gate. **⚠️ Do NOT read "nothing is gated" as licence to change entitlements** — that needs its own owner monetization decision. |

**⚠️ And do not silently ADD a plan gate either.** If a slice appears to need one, stop and raise it.

---

## 1. Executive judgment

**Evolve the existing engine. Do not rebuild it.**

Three of the pieces a curriculum-scale exam engine needs already exist and are sound:

- **A plan → note-set seam.** `PlanSourcedExamVerifier` already resolves a collection claim into a verified
  member set, ownership-checked, with the primary's membership required.
- **Bounded batch generation with deduplication.** `OpenAiLlmStudyPackService.generateLongExamParallel`
  issues exactly **two** parallel LLM calls per source, with salvage and sequential fallbacks; the prompt
  carries a Study Pack **summary plus key concepts**, never note bodies.
- **A pre-generated pool with serve-tracking.** `ExamQuestionPoolService` already demonstrates
  *pre-generate → sample → track served keys → refresh on depletion*, which is the mechanism a large-pool
  exam needs, at a different granularity.

What is missing is **one new stage** (coverage blueprint + source sampling) and **one deferred schema
decision** (session anchoring). No new engine. No new quiz mode.

---

## 2. THE FINDING THAT REFRAMES THE BRIEF: the scope/count chain runs backwards

The brief (§5) asks whether three quantities are conflated. They are worse than conflated — **they are
chained in reverse.**

| Quantity | Where it is decided | Value |
|---|---|---|
| **C** — item count | `StudySnapProperties:177-183` | 20 / 25 / 30, by **learner level** — never by scope |
| **B** — sampled sources | `ExamSourceLimitResolver:16-18` | **derived from C**: `questionCount / 3` |
| **A** — eligible pool | — | **does not exist as a concept anywhere** |

So the product reads **A ← B ← C**, exactly inverted from the intended model. `MIN_QUESTIONS_PER_SOURCE = 3`
gives 10 sources at `BOARD_EXAM_REVIEW` and **8 at `COLLEGE`, the default level**.

Allocation is then a flat division — `LongExamService:935-946` computes `questionCount / sourceCount`,
remainder to the primary, and throws below the floor. **There is no coverage weighting, no sampling and no
blueprint. Equal split is the entire policy.**

**⚠️ And the artifact is user-visible.** The derived cap is returned to the browser on
`LongExamStartResponse`, so the UI faithfully renders a number that is an accident of arithmetic rather than
a statement about curriculum. This is the same arithmetic that produced the Plus "4 sources instead of ~10"
in `v0.103.0` — **the pricing ladder inherited a division, not a decision.**

**The fix is small and precisely located.** `ExamSourceLimitResolver` is the single place the formula lives
and both exam services call through it. What changes is its **meaning**: from *"how many notes you may
pick"* to *"how many we sample from your curriculum."* Pool A becomes uncapped.

---

## 3. Board Exam is, today, exactly what the brief forbids

**It is not a session mode.** `QuickReviewSessionMode` has four values —
`QUICK_REVIEW, CHALLENGE, ADAPTIVE, LONG_EXAM`. Board Exam is `ChallengeQuizService` with
`MODE_BOARD_EXAM = "board_exam"` in session-state JSON, re-derived downstream by string-sniffing.

- **Hard cap of 2 additional sources** (`MAX_ADDITIONAL_BOARD_EXAM_SOURCE_COUNT`), unaffected by plan scope.
- **`min(12 × sourceCount, 30)` questions.**
- **So the largest possible Board Exam is 3 notes and 30 questions.** Against a ~550-note Review Set that is
  a rounding error, and the mode does not do the job it is named for.
- **Generation runs synchronously inside `@Transactional`**, holding a DB transaction and a row lock for the
  full duration of the LLM calls — where Long Exam correctly dispatches after commit. **Moving this is a
  prerequisite for any larger Board Exam, not an optimisation.**
- **Entitlement is quota-only** — there is no `Feature` enum entry; the gate is
  `resolveMonthlyBoardExamLimit` returning 0 for non-PRO. That works, but it sits outside
  `FeatureGateService`, which is documented as the single source of truth for plan-based access.

It is, literally, *"Challenge Quiz with more questions"* — the thing §53 prohibits building.

---

## 4. Mixed question formats ALREADY SHIP — §13–24 is largely already answered

**Verified directly in the main session, not taken on report.** `dto/QuizItem.java` carries
`questionFormat` (`:24`), `correctIndices` (`:21`), `acceptableAnswers` (`:29`) and `workingSolution`
(`:26`). The domain model is **explicitly typed and already generalized**.

| Surface | MCQ | TRUE_FALSE | MULTI_SELECT | MATCHING | IDENTIFICATION | ENUMERATION |
|---|---|---|---|---|---|---|
| Model / validation / session state / grading | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Challenge Quiz prompt** | ✅ | ✅ | ✅ | ✅ | ✅ (**9 references**) | ✅ |
| **Long Exam prompt** | ✅ | ✅ | ✅ | ✅ | ❌ (**0 references**) | ❌ |
| **Board Exam prompt** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Shared quiz link (`PublicQuizItem`) | ✅ | partial | ❌ | ❌ | ❌ | ❌ |
| DOCX export | ✅ | partial | ❌ | ❌ | ❌ | ❌ |

**⚠️ THE LONG EXAM GAP IS PLUMBING, NOT ARCHITECTURE.** `LongExamProgressRequest` carries only
`questionIndex, selectedChoiceIndex, selectedMultiChoiceIndices`; `computeStatistics` calls the 4-arg
`isAnswerCorrect` when the **6-arg overload already exists and Challenge Quiz already uses it**. Adding
Identification to Long Exam is: one DTO field, two session-state keys that are **already defined**, one
overload swap, and prompt rules that exist **verbatim** in `challenge-quiz-developer.txt`.

**So deliverables G and H collapse from "architecture change" to "wire Long Exam into machinery that already
works."** Board Exam being the format-conservative one is fortunate rather than unfortunate — see §9.

---

## 5. BLOCKING: ConceptHealth provenance is broken for multi-source exams

**Independently confirmed in the main session.** `LongExamService.recordConceptsForSourcePacks:502-521`
loops over every source Study Pack and writes **the same concept list to each one**, filtered only by
whether that pack's own `getKeyConcepts()` contains the string. **`QuizItem` carries no source-pack field.**

So a concept missed on Note A is recorded as missed on Note B whenever B's key-concept list happens to
contain the same string — and sources drawn from one Subject Plan share vocabulary **by construction**. This
is not a corner case; it is the normal case for the exact feature being built.

**⚠️ CONSEQUENCE: the brief's §32 goal — *"Structural Engineering needs work, Transportation Engineering is
stronger"* — IS NOT SUPPORTABLE TODAY,** and the reason is citable rather than speculative. There is no
cross-pack concept identity either: `ConceptHealthEntity` is keyed `(user_id, study_pack_id, concept)` with
`concept` free text scoped per pack.

**ConceptHealth is also format-blind** — no format, weight or evidence-quality column. A missed True/False
and a missed Identification write identical rows, while `incorrect_streak` drives the `v0.77.0`
recommendation at `TWICE_MISSED_STREAK_THRESHOLD = 2`. **⚠️ Adding True/False without weighting means two
coin flips can trigger a remediation.**

**⚠️ FOUND 2026-09-02, AFTER `v0.103.0` SHIPPED — THERE ARE TWO INSTANCES, AND THEY FAIL IN OPPOSITE
DIRECTIONS.** The audit above describes `LongExamService`, which **over-attributes**: the same concept list
goes to every source pack, filtered by that pack's own key concepts. But `ChallengeQuizService.completeSession`
writes to **`saved.getStudyPackId()`** — the PRIMARY pack only (`:674`, `:680`) — so a multi-note Challenge
Quiz **under-attributes**: every concept from all six sources lands on the note the learner happened to start
from, and the other five receive nothing.

**⚠️ That path is NEW as of `v0.103.0`, so this release created a second instance of the very defect slice 1
exists to fix.** Slice 1 therefore covers **both services**, not just `LongExamService`. Verified by reading
both call sites; `ChallengeQuizService` contains no `recordConceptsForSourcePacks` equivalent at all.

**Minimum fix:** attach the source Study Pack id to each generated item (or a parallel per-index array in
session state). That one change unblocks Subject-Plan-scoped and Review-Set-scoped remediation later,
because per-source accuracy becomes real rather than inferred.

---

## 6. Exam-construction architecture

Current Long Exam pipeline:

```
Scope (client-supplied ids) → [nothing] → [nothing] → equal split → per-source 2-call fan-out
    → dedup → concat → score → ConceptHealth
```

The two empty stages are exactly the missing ones. Target:

| Stage | Status |
|---|---|
| Scope resolution | **EXISTS** — `PlanSourcedExamVerifier`; extend to return items, not just ids |
| **Coverage blueprint** | **NEW** — allocate C across coverage buckets |
| **Source / concept sampling** | **NEW** — pick B from pool A |
| Item blueprint | **NEW (thin)** — per-source (count, formats, difficulty) |
| Generation | **EXISTS** — already 2 bounded calls per source |
| Validation / dedup | **EXISTS** — `QuizDeduplicationUtils`; needs a cross-session store |
| Assembly | **EXISTS** (concat) — needs partial-failure tolerance |
| Scoring | **EXISTS** — needs source provenance (§5) |
| ConceptHealth | **EXISTS** — needs per-source attribution (§5) |

---

## 7. Long Exam v1

**Source: Subject Plan AND Student Study Plan — "both" costs nothing, because they are the same entity**
(`NoteCollectionEntity`). There is no code distinction to honour.

1. Scope = every plan member with a ready Study Pack. **Pool A is uncapped.**
2. Item count C stays level-derived (20/25/30). Defensible: it reflects how deep the learner works, not scope.
3. **Sample B sources from A**, `B = min(|A|, C / 3)`, by **coverage buckets → representative spread across
   buckets → sample within buckets → deterministic per-session randomization.**
   **⚠️ NOT `unpracticed-first`. That was proposed by the Stage 1 audit and is REJECTED (owner,
   2026-09-02), and the repo does not require it** — verified: neither `LongExamService` nor
   `ChallengeQuizService` reads `lastSessionCompletedAt`, so no binding contract exists.
   **Long Exam is an ASSESSMENT, not a recommendation engine.** Biasing a Midterm-style exam toward
   material the learner has not practised makes the score mean something other than "can I perform across
   this subject." **Weakness- and history-driven selection belongs to Adaptive Practice.**
   **⚠️ Sections may help SPREAD coverage; they are never curriculum WEIGHTS.**
4. **Keep the even split within the sampled set.** It is only indefensible when the sample is
   learner-chosen; once the sample is representative, equal division is honest.
5. Anchor the session on the **sampled** primary rather than `position == 0`, which also reduces a real
   collision: a unique index on `(user_id, study_pack_id)` for active `LONG_EXAM` sessions means **two
   Subject Plans sharing a first note cannot both have an active Long Exam today.**

**One new sampling function, one changed call site, one inverted cap semantic. No schema change, no new mode.**

---

## 8. Board Exam v1

**Source: the whole Review Set.** **⚠️ Claim REPRESENTATIVE COVERAGE, never official fidelity** — the repo
holds no board blueprint metadata of any kind, and the "domain" groupings in scoring are free-text concept
strings, not curriculum domains.

Possible now: lift the source constant to a sampled set (§7's mechanism); **stratify across the Review
Set's Subject Plans**; and move generation off the transaction (prerequisite, §3).

**⚠️ THE QUESTION COUNT IS CONFIGURATION, NOT A PERMANENT OWNER CONSTANT (owner, 2026-09-02).** Do not block
the architecture on choosing one universal number such as 50 / 75 / 100. What the architecture must separate
is: **whole Review Set = eligible syllabus → Subject Plans = coverage strata → sampled sources/concepts →
configured target item count.** Ship a **configurable initial practice-exam target**; official
board-specific counts, weighting and timing belong to trusted blueprint metadata later, and **product copy
stays "representative board-review exam" until that metadata exists.**

**⚠️ CORRECTED FROM THE AUDIT — see §13.5. Review Sets ARE hierarchical**, so the Subject Plan stratum
exists and Board Exam sampling has a real layer to stratify over.

Deferred to authoritative metadata: true domain weighting, per-board format mix, time-per-item norms.
**Do not invent these.**

---

## 9. Question formats

| Format | Value in Long Exam | Cost | Grading reliability | Verdict |
|---|---|---|---|---|
| MCQ | baseline | zero | high | ships |
| **True/False** | low alone | zero — already in the prompt | **low as evidence — 50% chance floor** | **DEFERRED (owner, 2026-09-02)** |
| **Identification** | **high** — genuine free recall, the strongest mastery evidence available | **small** — 1 DTO field, 2 existing session keys, 1 overload swap, prompt rules copied | high, when guardrails hold | **v1, and NOT coupled to any ConceptHealth redesign** |
| Multi-select / Matching | already in the Long Exam prompt | zero | high | already shipping |
| Enumeration | high but brittle | moderate | medium | **later** |

**⚠️ LONG EXAM v1 = MCQ + the already-shipping MULTI-SELECT and MATCHING + IDENTIFICATION. True/False is
DEFERRED (owner, 2026-09-02)** — technically free, but its 50% chance floor raises evidence-quality
questions immediately, and there is no reason to answer them yet. **⚠️ Do NOT redesign `ConceptHealth`
merely to make True/False fit, and do NOT couple Identification to a format-weighted evidence model** —
Identification already has deterministic normalized grading and acceptable-answer machinery. Wire Long Exam
into the existing mechanism, **carrying every existing notation-generation guard** (§10).

**⚠️ Board Exam stays MCQ-only**, for two independent reasons: real licensure exams are MCQ, and this is the
one place format fidelity is a product *claim* rather than a preference. Its prompt is already MCQ-only, so
this is the status quo, not new work.

---

## 10. Identification grading — build nothing new

**The mechanism exists and its failure mode is already documented from production.**
`challenge-quiz-developer.txt` states the contract: graded by **exact text match after trimming, lowercasing
and collapsing whitespace**, against curated `acceptableAnswers` generated *with the item*.

- **Normalized match + curated aliases** — the shipped design, and the right one: deterministic, zero
  latency, zero marginal cost, reproducible.
- **Deterministic fuzzy / edit distance** — rejected: it silently accepts near-misses on technical terms
  where one character *is* the distinction.
- **⚠️ LLM semantic grading — REJECTED.** Nondeterministic scoring feeding `ConceptHealth` means the same
  answer is right one session and wrong the next; it adds an LLM call on the submit critical path; and the
  mastery signal has been locked since `v0.37.0` to move only from genuine assessment.

**⚠️ The real risk is item GENERATION, not grading, and the repo already fixed it once.**
`QuizValidationUtils:31-51` records a reproduced production failure — an Identification item asking for an
algebraic expression where the requested notation was marked wrong and restating the stem was marked right,
produced by two independent generations. The mitigations are `NOTATION_ANSWER_STEM_PATTERN` plus specific
prompt rules. **If Long Exam gets Identification via a NEW prompt file instead of copying those rules, it
will reproduce a bug this repo has already paid for.**

---

## 11. Large-pool stress test

Per source: 2 parallel LLM calls, 240s timeout. **The loop across sources is sequential.**

| Pool | Long Exam today | Board Exam today |
|---|---|---|
| **3 notes** | Works — 6 calls, 3 rounds | Works; its design point — but inside a transaction |
| **20 notes** | Learner may pick 8 (College). **12 of 20 unreachable** | Picks 3. **17 of 20 unreachable** |
| **77 notes** | **~69 of 77 cannot appear.** The learner hand-picks 8 from a 77-item list — the picker itself is the failure. ~10 sequential rounds ≈ **3–7 minutes**, with nothing bounding total latency | 3 of 77. Meaningless |
| **550 notes** | **~540 unreachable.** Loads 550 rows to pick 10 | 3 of 550 |

**⚠️ NOTHING BREAKS AT 550 — AND THAT IS THE PROBLEM.** There is no prompt explosion and no timeout, because
the cap silently discards the curriculum before generation ever sees it. **The architecture degrades by
ignoring the syllabus, not by failing loudly** — which is why this was invisible until the brief asked.

Three further risks at scale:

- **⚠️ Partial failure is total failure.** All sources must return the exact expected count or the whole exam
  throws — **and quota is charged before generation runs.** With ~20 LLM calls, at least one shortfall is
  likely, and the learner pays for nothing.
- Board Exam holds a transaction and row lock across its LLM calls.
- **No cross-session dedup on the multi-source path**, so retakes can and will repeat questions.

---

## 12. Entitlements — the ladder is coherent in intent, not in code

| | Intent | Code | Gap |
|---|---|---|---|
| Free | limited custom Challenge | 3 sources, 2/mo | matches |
| **Plus** | **~10 notes** | **4 sources**, 10/mo | **~10 vs 4** |
| Pro | curriculum-level Long + Board | Long 12/mo, Board 10/mo | matches in kind |

**⚠️ The Plus cap of 4 is not a pricing decision — it is `MULTI_NOTE_CHALLENGE_QUESTION_COUNT (12) / 3`.**
The §2 conflation surfacing as a pricing artifact.

The qualitative split — Free/Plus *"I choose what to challenge myself on"*, Pro *"NoteLib assesses me against
my curriculum"* — is the right frame, and **the sampling change is what makes it true rather than
aspirational.** Today Pro's Long Exam is *also* learner-chosen sources, so the tiers differ only by count.

**⚠️ Multi-note Challenge Quiz survives Long Exam and should** — different economics for different jobs:
Challenge draws from the **question bank + official templates** (banked, repeatable, claim/release), Long
Exam always generates fresh against a 12/month quota.

### 12.1 INTEGRITY ITEM — multi-note Challenge bypasses the question bank

**⚠️ Recorded as a concrete backlog item, NOT a caveat (owner, 2026-09-02).** Multi-note Challenge goes
straight to generation and **neither reads nor writes the Challenge question bank**, so it loses exactly the
property that distinguishes Challenge from Long Exam. **The product distinction between repeatable custom
practice and freshly generated assessment is weaker for as long as this holds.**

**It does not block the curriculum-exam architecture** and is not in slices 1–4. It is tracked so it does not
survive as a footnote in a superseded document.

---

## 13. Implementation slices — REVISED (owner, 2026-09-02)

**⚠️ THIS SUPERSEDES THE STAGE 1 ORDERING.** Two changes, each with a stated reason:
**provenance moves AHEAD of curriculum-scale assessment**, and **generation resilience is COUPLED to the
sampler rather than following it.**

**0. Finish `v0.103.0`'s surface fix** (already in progress). Read the multi-note cap at prestart from an
available source instead of the null `challengeSession`; fix the PRO+collection routing. **One file. The
server side is correct and reachable via API.**

**1. Assessment source provenance.** Attach the source Study Pack to each generated item; correct
`ConceptHealth` attribution; preserve provenance for reporting.
**⚠️ MOVED AHEAD OF CURRICULUM-SCALE WORK, AND THIS IS THE ORDERING THAT MATTERS MOST.** Before
curriculum-level results can support subject-level weakness, readiness or broader remediation, **every item
must be attributable to the correct source pack** (§5). Shipping representative sampling first would
generate far more cross-source evidence through a mis-attributing writer — **it makes a known defect worse
at exactly the moment more data flows through it.**

**2. Curriculum-pool exam foundation.** Introduce an explicit eligible pool **A**; sample **B**
independently; generate **C** independently; add the coverage blueprint and sampler — **and enough
generation resilience that curriculum-scale sampling is safe.**
**⚠️ THE RESILIENCE IS PART OF THIS SLICE, NOT A FOLLOW-UP (owner, 2026-09-02).** Today the path is
sequential source rounds, **partial failure = total exam failure**, and **quota charged before successful
assembly** (§11). Broadening the sampled workload while those hold would make a known total-failure path
*normal* rather than rare. The A/B/C separation may exist as its own conceptual foundation, but **the
learner-facing curriculum-scale Long Exam must not ship until generation is resilient enough for the
workload it creates.**

**3. Long Exam academic identity.** Subject Plan **and** Student Study Plan sources; representative
curriculum coverage; **Identification via the existing deterministic grading**, carrying every notation
guard. **No new mode. No True/False. No ConceptHealth evidence redesign.**

**4. Board Exam Review Set identity.** Whole Review Set as eligible syllabus; stratify across Subject Plans;
generation **outside** the transaction; **configurable** target item count; representative coverage only,
with no invented official weighting.

**5. Broader Adaptive Practice.** Use the now-trustworthy per-source evidence for Subject-Plan- and
Review-Set-scoped remediation. **Existing Note-scoped Adaptive Practice is unchanged.**

**6. Supporter combined quiz.** Selected-note Challenge/custom quiz, shareable **without requiring Learning
Connections**.

**Later:** cross-session Long Exam dedup; official Board Exam blueprint metadata; exam templates; richer
teacher exam configuration; and **True/False mastery weighting if it is ever still justified.**

**⚠️ This is at least four releases. Slices 1, 2 and 4 each independently meet the pre-signoff engineering
gate** for changing production-data semantics or touching a shared method across PRs — **which is an
engineering control, not a product gate (§0).**

### 13.5 Correction to the audit — recorded so it is not re-derived

The audit listed *"Is a Review Set a collection-of-collections?"* as an owner decision it could not verify.
**The repo answers it:** `NoteCollectionEntity.parentCollectionId` exists (`:68-69`), with
`findByParentCollectionIdIn` and `countByParentCollectionId` on the repository. **Review Sets ARE
hierarchical**, so Board Exam sampling has a genuine Subject Plan stratum. This removes a decision from the
owner and materially de-risks slice 6.

---

## 14. Owner decisions — ANSWERED 2026-09-02

All four are settled. Recorded with reasoning so they are not re-opened.

1. **Plus multi-note source cap — 4 is REJECTED as arithmetic leakage, not product design.** It is
   `MULTI_NOTE_CHALLENGE_QUESTION_COUNT (12) / 3`, the §2 conflation surfacing in the pricing ladder.
   **Preserve the intended, materially larger custom mixed-retrieval experience; the exact figure stays
   tunable configuration.**
2. **Board Exam question count — a CONFIGURABLE initial target, never a permanent universal constant.**
   Do not hard-code an exam length as architecture (§8).
3. **Partial generation failure — a shorter valid exam ONLY when it clears a defined minimum
   assembly/coverage threshold; otherwise fail WITHOUT consuming the learner's exam quota.**
   **⚠️ Never silently treat a severely incomplete exam as equivalent to a complete one** — a score has to
   mean something. This also fixes today's behaviour, where quota is charged before assembly succeeds.
4. **True/False in Long Exam — DEFERRED.** Identification ships in v1; **no ConceptHealth weighting model is
   built solely to accommodate True/False**, and Identification must not be coupled to one.

---

## 15. Explicitly deferred

Enumeration in Long Exam; mixed formats in Board Exam; cross-pack canonical concept identity (ADR-sized);
authoritative licensure blueprint weighting; the session-anchoring migration (nullable `study_pack_id` plus
`source_collection_id` — **not needed for slices 1–5**, defer until slice 6 forces it); exam templates;
teacher exam authoring inside learner Long Exam; mastery comparison between people; and non-MCQ support in
`PublicQuizItem` / DOCX export — a real gap, but it constrains **sharing**, not assessment.

---

## 15.1 The identities these slices exist to protect

**Challenge Quiz** — learner-selected custom retrieval. **Long Exam** — academic comprehensive assessment
across a Subject Plan or Student Study Plan. **Board Exam** — whole-Review-Set licensure-readiness
assessment. **Adaptive Practice** — weakness-driven remediation.

**⚠️ They are never differentiated primarily by question count.** If a proposed change makes two of them
differ only in how many questions they ask, the change is wrong.

---

## 16. Anti-drift — binding on every slice above

No new quiz mode and no new sub-mode; Long Exam is not a bigger Challenge Quiz and Board Exam is not a bigger
Long Exam; modes are never differentiated primarily by question count; **curriculum-level exams are never
capped at ~10 eligible notes**; never send 77 or 550 full notes into one prompt; never one question per note
merely because a note exists; **note count ≠ curriculum weight and section size ≠ exam weight**; no invented
official board weighting; no formats added for variety; no format forced on every subject; **no automatic LLM
grading of free responses**; no scoring that corrupts mastery evidence; no plan completion required before
exam access; standalone Notes stay first-class with no plan required to practise; Learning Connections are
never required for shared quizzes and supporter quizzes use **Challenge, never Long Exam**; the Review Set
hierarchy is not redesigned; **Sections never become a persisted collection entity** and are a tie-breaker
for spread, never a weight; no teacher exam-authoring complexity in learner Long Exam v1; no pricing change
before the architecture settles; **Challenge Quiz is not shortened without abandonment evidence**; and no
fake official-exam fidelity.

**Learner freedom is preserved: recommendation ≠ availability.** A learner may take a Long or Board Exam at
0% readiness as a baseline.
