# Question Quality (Tier 3 / single-best-answer) audit — v0.162.0 Phase D

Owner decision document. Ships no code — this is the deliverable, per this release's own scope constraint
(`RELEASES.md` v0.162.0 Planned Scope item 5: *"produces an owner decision document: is this worth building, and
if so, at which tier... do not let this phase drift into an implementation mid-release"*). Updates the existing
Backlog Index row ("Question Quality — single-best-answer / ambiguity verification",
`docs/product/ROADMAP.md`) rather than adding a new one.

## What this audits, and what it explicitly does not

`docs/claude-findings/2026-09-19-quick-review-percentage-increase-correctness-incident.md` (§H) established a
three-tier discipline for quiz correctness and was explicit that only Tiers 1–2 were in scope for that incident:

- **Tier 1 — STRUCTURAL.** Does the answer key satisfy its own schema (exactly one correct index, valid enum,
  MATCHING block integrity)? Shipped in that incident (H1–H3b).
- **Tier 2 — INTERNAL-CONSISTENCY.** Does the stored explanation agree with the stored answer key? Shipped as H4
  (`v0.155.0`) and relaxed by H5 (`v0.162.0`, this same release, PR #1455) so explanations can state numeric
  values without contradicting themselves.
- **Tier 3 — SEMANTIC.** Does the question have a single, genuinely defensible best answer at all — independent of
  whether the stored key and explanation agree with each other? **This is the tier this document audits.** The
  incident doc is explicit that a question can pass Tiers 1 and 2 perfectly while still being semantically
  indefensible: *"An explanation that merely rationalises a wrongly-keyed answer would pass H4 cleanly"* (§H). The
  one confirmed historical instance on record is the *"square rotated 90° CW then reflected vertically"* geometry
  item (incident doc line ~1075, exam-pool item `c916ba22-…`) — found during that incident's own corpus scan,
  explicitly flagged there as *"likely an instance of the §24 ambiguity class... flag for the follow-up Question
  Quality audit"* rather than actioned.

**Confirmed unchanged today** (re-checked directly against the current prompt files, not assumed): `developer.txt:91`
still reads only *"exactly one choice must be correct"* — a uniqueness/structural instruction, not a defensibility
instruction — across all six quiz-prompt files. No prompt requires the correct answer to be unambiguous, and none
forbids a stem where more than one option is defensible under a different reading. H5 (this release) did not touch
this, correctly — it relaxed what an explanation is allowed to *say*, not what a stem is required to *be*.

**§24 of `docs/product/SPEC.md`, which the incident doc cites repeatedly, could not be located in the current
`SPEC.md`** — either the section was renumbered or removed since the incident doc was written, or the citation was
informal. Noted rather than silently assumed; does not change this audit's conclusions, since the incident doc's
own §H reproduces the substance of what §24 is cited for.

## Method

Two limitations, stated before the method rather than after: **Tier 3 has no deterministic predicate** — unlike
Tiers 1–2, which a SQL check settled at zero model cost across the full 115,333-question corpus, "is this stem
genuinely ambiguous" is a judgment call with no cheaper substitute than reading real questions and judging each
one. And **the reviewer here (Claude) is a language model judging content another language model wrote** — the
same class of confident-but-wrong failure this whole incident chain is about could in principle affect the review
itself, not just the content being reviewed. Neither limitation was resolved; both are named so the finding below
is read at the right confidence level.

Three stratified samples pulled read-only from production, 2026-09-27 (exact queries, with one real bug found and
fixed mid-audit, saved verbatim in `docs/claude-plans/2026-09-27-question-quality-tier3-sample.sql`):

1. **25 questions, one per subject, from the 20 highest-volume subjects generated since 2026-09-20**
   (`study_packs.quiz`) — diverse coverage across engineering, education, accountancy, humanities, and science.
2. **20 assertion-style ("Statement 1: ... Statement 2: ... Which is correct?") questions since 2026-09-15**
   (`study_packs.quiz`) — deliberately oversampled as the single highest-risk format: it bundles two
   independently-gradable sub-claims into one answer, which is structurally the shape most likely to hide a
   "well, actually, that depends" dispute over just one of the two claims.
3. **20 exam-pool questions (10 Board Exam + 10 Long Exam, READY pools only)** — the harder, situational,
   multi-step-reasoning prompt tier, and specifically the STORE the one confirmed historical defect (item 1 above)
   was found in. Sample 1 and 2 draw only from `study_packs.quiz`; this sample exists so the audit isn't blind to
   the one store with a confirmed prior incident.

**65 questions total.** `challenge_quiz_question_bank` and `generated_quizzes` were NOT sampled — stated
explicitly rather than silently omitted. Both share the same core Quiz rules (the same structural "exactly one
choice" bar, the same H4/H5 explanation contract) as the two stores sampled, and the harder Board/Long Exam tier
(sample 3) is the more distinct risk surface already covered. **This audit rests on 2 of 4 quiz stores, not a
full-corpus census** — Tier 3 cannot be scanned deterministically the way Tier 1/2 checks scanned all four.

Each question was read in full (stem, all choices, keyed answer, explanation) and judged against one question:
**given the stem and the four choices as written, does a reasonable, subject-competent reader have only one
defensible best answer, or could a competent reader reasonably pick a different one?** This was NOT a
semantic-correctness check of the explanation's factual content (a different, larger audit) — only whether the
KEYED answer is the unique defensible choice among the four given.

## Findings

**0 of 65 questions were judged genuinely ambiguous or indefensible.** Full item list in the Appendix below —
every row lists what was actually read, not a summary invented after the fact.

**Two illustrative near-misses, not counted as defects, both flagged because they are the closest calls found and
because they mark a real PATTERN worth naming rather than because either is wrong as written:**

- A Maternal/Child Nursing item (sample 2, "Statement 1: Oral electrolyte replacement is preferred over IV when
  possible. Statement 2: IV electrolyte replacement is safer than oral supplements.", keyed "Only Statement 1
  correct"). In isolation a clinician could contest the general framing (severe deficits genuinely require IV),
  but Statement 1's own "when possible" qualifier already scopes the claim correctly, and Statement 2's claim ("IV
  is safer than oral") is a clear overreach that is straightforwardly false. Resolves cleanly on the text as
  written.
- Three items in sample 3 use a "MOST important / BEST demonstrates success / BEST supports" framing among several
  plausible-sounding options (conductor-size selection factors; LEA 2027 personalized-learning success criteria;
  clay-soil drainage design). Each resolved cleanly in this sample — the given answer was clearly better-supported
  than its distractors — but this STYLE of question (ranking several genuinely-relevant factors rather than
  identifying one clearly true statement among clearly false ones) is structurally more contestable than factual
  recall, independent of whether any specific instance is actually wrong. Worth naming as a higher-risk class for
  any future targeted review, separate from the zero-defect count above.

**No learner-facing "report/flag this question" mechanism exists anywhere in the product today** (checked
`frontend/app` and `frontend/components` directly for any flag/report affordance on the quiz-taking pages — none
found). This matters for the recommendation below: the cheapest possible source of REAL Tier-3 signal — a learner
disputing an answer in the moment — currently has no channel to reach anyone. The single confirmed historical
defect this whole three-tier framework traces back to was itself found through an informal report, not a product
feature.

## Statistical honesty, corrected

Zero defects in 65 questions is evidence of a rate below a certain threshold, not evidence of a zero rate. By the
rule of three (a standard rough bound for "0 events observed in n independent trials"), 0/65 is consistent with a
true defect rate as high as roughly **1 in 22 questions (~4.5%) at 95% confidence** — this sample cannot rule out
a rate at or below that, only rates clearly above it.

**Applied to the corpus, this is not reassuring at face value: ~4.5% of 115,333 questions is over 5,000
questions.** Even at the low end of what a 65-item sample cannot exclude, this audit's finding does not support a
claim that the corpus is clean, and an earlier draft of this document overstated the conclusion as "no evidence of
an actionable rate" — that was wrong and has been corrected here. **What this sample DOES support: it rules out a
COMMON defect** (something present in, say, 1 in 10 or more questions would have been very likely to show up at
least once in 65 draws, and none did) **— it does not and cannot rule out a RARE, individually costly one**, which
is exactly the shape the one confirmed historical defect actually had (one report, out of a corpus this size).

**This reframes the recommendation below rather than undermining it: a rare-event problem is precisely the class
that a one-time sample can never resolve, and precisely the class a standing, low-cost signal channel (a learner
flag) is suited to** — it doesn't need to catch every instance in one pass, it accumulates signal over real usage
at whatever the true (unknown) rate actually is, which a fixed-size audit structurally cannot do.

## Recommendation

**Do not build an automated Tier 3 semantic gate in this release, or speculatively in a near-term one — but not
because the corpus is proven clean; because a gate is the wrong instrument for the actual shape of this risk
(rare, not absent).** Three reasons:

1. **No sample-based evidence justifies acting NOW, but the honest reading above means "acting never" isn't
   justified either** — this is a call to sequence correctly, not to close the question.
2. **The tier is expensive by its own nature.** Per the incident doc's own architecture note, a real Tier 3 gate
   needs either a model call per question (recurring cost, at generation-time volume or as an async sweep over the
   115,333-question corpus) or human curation (an ongoing review burden) — neither is a one-time cost like Tiers
   1–2 were, and a rare-event problem is a bad fit for a cost that scales with volume rather than with actual
   incidence.
3. **A cheaper, higher-signal step is available and currently missing entirely**, and it is the only instrument
   here that actually scales to a rare-event rate: a way for a real learner to report a real disputed question,
   which the product does not have at all today.

**⚠️ Before scoping that flag affordance, weigh it against a decision the owner already made on a related
question.** The incident doc (§ "Public announcement decision", ~line 571) explicitly rejected telling learners
quiz answers might be wrong, on exactly this reasoning: *"A general announcement would tell thousands of learners
that quiz answers may be wrong, on the evidence of one report and six questions with a single known victim — which
damages trust far more than it protects it."* A visible "flag this question" icon on every quiz session raises a
related, not identical, question: it's an always-available affordance rather than a one-time announcement, but it
does put a permanent, ambient "this might be wrong" signal in front of every learner, on every question, forever —
worth the owner weighing explicitly rather than assuming a quiet, neutrally-worded affordance ("give feedback on
this question," not "report an error") avoids the same trust concern the announcement decision was about. Two
narrower alternatives that raise less of that tension, for the owner to weigh against the full-surface version:
scope the flag to `PUBLIC`/Official-authored material only (matching the incident doc's own §H architecture note
that Tier 3 review belongs on curator/Official content, not every learner's private practice), or route it through
an existing lower-visibility surface (e.g. a session-end feedback prompt) rather than a persistent per-question
icon.

**If the owner wants to act on this document**, the recommended next step is scoping whichever shape of that
flag/report affordance the owner picks as its own small release item (Claude-direct or Codex depending on final
shape and whether it needs new backend storage — likely small enough for Claude-direct per the task-routing table
if it's frontend-plus-an-existing-admin-surface, Codex if it needs a new table), NOT a Tier 3 semantic verifier.
Revisit whether an automated gate is justified once that channel has run long enough to produce a measured report
rate.

**If the owner disagrees and wants Tier 3 built anyway**, on the strength of the one historical incident and the
rare-event reasoning above rather than waiting for the flag channel: the incident doc's own architecture note (§H,
"Where the future single-best-answer / ambiguity gate belongs") already scoped the right shape — an asynchronous
post-generation review queue over high-value (public/curator/Official) material only, feeding a curator surface,
explicitly OUT of the synchronous generation path, and explicitly not something that should make an
internally-consistent-but-ambiguous question look "verified" the way passing H4 might be mistaken for. Rough shape
if scoped: one model call per newly-generated public/Official question (not the full historical corpus — that
volume × cost trade-off is a separate decision), reviewed asynchronously after generation, surfaced to curators
rather than blocking or auto-correcting. This scoping stands as written in the incident doc; this document does
not re-derive it, only confirms via fresh sampling that a corpus-wide urgency case has not been made.

## What this document is not

Not a corpus-wide scan (Tier 3 cannot be scanned deterministically, per above — 65 questions across 2 of 4 stores
is a sample, not a census, and does not rule out a rare defect at real cost to the corpus's few thousand affected
questions if the true rate sits where this sample can't see). Not a claim that H4/H5 (Tier 2) are sufficient for
correctness — the incident doc's own warning stands: *"A question passing H4 is NOT verified correct."* Not a
decision — the recommendation above is Claude's read of the evidence; the actual "build it now / build the
cheaper thing first / do neither yet" call is the owner's, per this phase's own scope.

## Appendix: full 65-item list

Format: **store/mode** | subject | question (truncated to ~90 chars where long) | keyed answer | verdict. Full
stem/choices/explanation text for every row was read during the audit; this table is the durable, checkable record
of the judgment made on each, not a re-summary — see the companion `.sql` file to re-run the sampling queries
(a fresh draw, since `ORDER BY random()` is not reproducible row-for-row).

### Sample 1 — 25 questions, one per top-20 subject, `study_packs.quiz`, since 2026-09-20

| Subject | Question (truncated) | Keyed answer | Verdict |
|---|---|---|---|
| Project Management | Primary goal of risk identification | To recognize potential events that could affect project objectives | Clean |
| The Life and Works of Rizal | Primary social issue Noli Me Tangere exposes | Colonial oppression by Spanish authorities | Clean |
| Field Study | Ways educational theories influence practice, EXCEPT | Providing fixed rules with no adaptation | Clean |
| Malayuning Komunikasyon sa Wikang Filipino | Hindi kabilang sa anyo ng akademikong komunikasyon | Personal na liham | Clean |
| Understanding the Self | NOT a common barrier to self-regulation | Growth mindset | Clean |
| Philippine Green Building Code | Mechanisms ensuring PGBC compliance | Permits and inspections | Clean |
| Site Planning | Statement pair: universal design / traffic calming | Only Statement 1 is correct | Clean |
| Architecture | Primary load type a beam resists | Bending | Clean |
| Taxation | Administrative appeal officer can do all EXCEPT | File a criminal case against the taxpayer | Clean |
| Urban Planning | Spatial pattern with streets radiating from a center | Radial pattern | Clean |
| Regulatory Framework for Business Transactions | Statement pair: corporate ultra vires powers | Only Statement 1 is correct | Clean |
| Psychology | Continuity vs. Discontinuity debate concerns | Gradual or occurs in distinct stages | Clean |
| Fire Code of the Philippines | Penalties under the Fire Code may include | Fines, suspension of operations, criminal liability | Clean |
| Educational Laws and Reforms | Total years in K–12 including Kindergarten | 13 years | Clean |
| Building Technology | Glazing type with better insulation performance | Double glazing | Clean |
| Purposive Communication | Best defines verbal communication | Use of spoken or written words to express messages | Clean |
| Mathematics | Strategies that support arithmetic fluency | Estimation and mental math | Clean |
| Teaching Profession | Primary purpose of RA 6713 | To establish ethical standards for public officials | Clean |
| Educational Research | Primary purpose of informed consent | To ensure voluntary agreement after understanding the study | Clean |
| Building Laws | Statement pair: exit routes / fire safety systems | Only Statement 1 is correct | Clean |
| Construction Materials and Testing | Hooke's Law constant relating stress and strain | Young's modulus | Clean |
| Building Utilities | Statement pair: BMS automation vs. manual override | Only Statement 1 is correct | Clean |
| Curriculum Development | Best describes instructional alignment | Matching teaching with standards and assessments | Clean |
| BP 334 | Statement pair: compliance audits / accessibility scope | Only Statement 1 is correct | Clean |
| Structural Components | Statement pair: dynamic vs. static wind load | Only Statement 1 is correct | Clean |

### Sample 2 — 20 assertion-style questions, `study_packs.quiz`, since 2026-09-15

| Subject | Question (truncated) | Keyed answer | Verdict |
|---|---|---|---|
| Pediatric Nursing | Early recognition of delays / milestone timing exactness | Only Statement 1 is correct | Clean |
| Professional Education | Functionalism (stability) / Conflict Theory (inequality) | Both statements are correct | Clean |
| Management Services | Sales Volume Variance / Yield Variance definitions | Both statements are correct | Clean |
| Maternal and Child Nursing | Epidural blocks nerve impulses / is nonpharmacologic | Only Statement 1 is correct | Near-miss (see Findings) |
| Professional Education | Reflective practice connects theory-practice / decreases understanding | Only Statement 1 is correct | Clean |
| Management Services | High-low method fixed-cost calc / must use data outside relevant range | Only Statement 1 is correct | Clean |
| National Building Code | Permits only for new construction / include zoning check | Only Statement 2 is correct | Clean |
| Theory of Architecture | Symmetry as order principle / order means color scheme | Only Statement 1 is correct | Clean |
| Building Utilities | Copper pipe durability / backflow devices optional | Only Statement 1 is correct | Clean |
| Educational Laws and Reforms | RA 9155 community participation / centralizes authority | Only Statement 1 is correct | Clean |
| Curriculum Development | NESC lifelong learning / curriculum ignores stakeholders | Only Statement 1 is correct | Clean |
| Urban Planning | Connectivity as linked streets / isolating zones | Only Statement 1 is correct | Clean |
| The Contemporary World | Economic interdependence promotes peace / eliminates crisis risk | Only Statement 1 is correct | Clean |
| Understanding the Self | Sexual self includes identity/desires / political self excludes civic engagement | Only Statement 1 is correct | Clean |
| Educational Technology | Behaviorism observable behavior / multimedia is text-only | Only Statement 1 is correct | Clean |
| Fluid, Electrolyte, and Acid–Base Balance | Oral preferred when possible / IV safer than oral | Only Statement 1 is correct | Near-miss (see Findings) |
| Management Services | High fixed costs raise operating leverage / higher financial leverage = lower risk | Only Statement 1 is correct | Clean |
| Professional Education | Positive discipline improves relationships / emphasizes social-emotional skills | Both statements are correct | Clean |
| Educational Psychology | Motivation includes intrinsic/extrinsic / unrelated to persistence | Only Statement 1 is correct | Clean |
| Purposive Communication | Tone/register matching helps / misalignment has no impact | Only Statement 1 is correct | Clean |

### Sample 3 — 20 exam-pool questions (10 Board Exam + 10 Long Exam, READY pools)

| Mode | Question (truncated) | Keyed answer | Verdict |
|---|---|---|---|
| BOARD_EXAM | Factor that LEAST influences career-readiness curriculum design | Primary emphasis on theoretical research | Clean |
| BOARD_EXAM | MOST important factor in conductor size selection | The load current and length of the conductor run | Near-miss pattern (see Findings) |
| BOARD_EXAM | Why auditor doesn't eliminate substantive testing after 0 deviations | Because residual risk remains despite effective controls | Clean |
| BOARD_EXAM | Assertion tested by physically inspecting fixed assets | Existence | Clean |
| BOARD_EXAM | Consequence of exceeding max travel distance to exit | Increased risk to occupant safety due to delayed egress | Clean |
| BOARD_EXAM | Outcome that BEST demonstrates LEA 2027 personalized-learning success | Students receive learning activities adapted to their skill level | Near-miss pattern (see Findings) |
| BOARD_EXAM | Coagulation factors inhibited by heparin/antithrombin III | Thrombin and factor Xa | Clean |
| BOARD_EXAM | Runoff volume from 1-acre roof, 2-inch rainstorm | 7,260 cu ft | Clean (arithmetic verified) |
| BOARD_EXAM | Why continuous reassessment matters for stable patients | To detect subtle changes in patient condition promptly | Clean |
| BOARD_EXAM | Discrimination index for 80%/40% top/bottom scorers | 0.40 | Clean (arithmetic verified) |
| LONG_EXAM | Economic concept for trade-off under scarcity | Opportunity cost | Clean |
| LONG_EXAM | BEST drainage combo for clay soil / frequent storms | Retention basins and swales | Near-miss pattern (see Findings) |
| LONG_EXAM | Main reason teachers keep student records confidential | To protect privacy | Clean |
| LONG_EXAM | Plasma component transporting lipids/fat-soluble vitamins | Globulins | Clean |
| LONG_EXAM | Final electron acceptor in electron transport chain | Oxygen | Clean |
| LONG_EXAM | Learner-centered strategy building communication/cooperation | Collaborative Learning | Clean |
| LONG_EXAM | Combo giving highest permissible building height | Commercial, Type I, sprinklers | Clean |
| LONG_EXAM | Best describes the residual income metric | An absolute dollar measure of value created beyond capital cost | Clean |
| LONG_EXAM | "Five rights" of medication administration | Right patient, right medication, right dose, right route, right time | Clean |
| LONG_EXAM | Best supports risk assessment under the CECL model | Using lifetime expected losses | Clean |
