# H5 before/after sample review (v0.162.0)

Run 2026-09-27, per the incident doc's own locked requirement (§Q.1 item 3: *"Ships as its own slice with a
before/after sample review, per the original recommendation"*) and the gate written into `RELEASES.md`'s H5
Measurement paragraph. This is a merge gate, run against real OpenAI output (model `gpt-4.1-mini`, the
`LLM_MODEL_FREE` default), not simulated or fabricated by Codex.

**Method:** replicated `OpenAiLlmStudyPackService.buildInputMessages` exactly — same `system`/`developer`/`user`
roles, same `schema.json` in strict `json_schema` mode, same `/responses` endpoint — calling it directly via script
rather than through the full app, so no note/session/DB machinery was needed. Two source notes (one numeric-heavy:
Ohm's Law and series-circuit arithmetic; one prose-heavy: Philippine constitutional due process), each generated
once under the OLD `developer.txt` (`git show HEAD~1:...`, pre-H5) and once under the NEW (post-H5, current
working tree) — 4 calls, 20 questions total. Full output saved at
`/private/tmp/claude-501/-Users-einarlagera-IdeaProjects-study-snap/00190c6f-754e-48f3-a908-b5af81635040/scratchpad/h5_sample_review_output.txt`
(session scratchpad, not committed — the findings below are the durable record).

## Findings

1. **Numeric case works as intended.** NEW-prompt explanations for genuinely numeric MCQs state the correct
   choice's value verbatim and exactly as written in `choices` (`"...= 30 ohms"`, `"...= 240 watts"`), matching the
   coverage-ratio metric's own substring-match assumption. No letter reference ("option C", "choice A") appeared in
   any of the 20 questions across either prompt version — the letter ban held in both.

2. **No masking observed in this sample.** None of the numeric NEW-prompt explanations mentioned a distractor's
   value alongside the correct one — the pre-existing masking risk noted in `RELEASES.md`'s Measurement paragraph
   (`QuizValidationUtils:207-209`'s short-circuit) did not manifest here. A 4-item sample cannot rule it out at
   scale; the coverage-ratio companion metric remains the right instrument for that at signoff.

3. **The "discuss the other choices" ban is imperfectly followed under BOTH prompts, not newly broken by H5.** One
   prose question, in both the OLD and NEW runs, has its explanation walk through all four choices rather than only
   the correct one (OLD: *"Procedural due process requisites include an impartial court, notice, opportunity to be
   heard, and judgment following a lawful hearing. 'A fair law' relates to substantive due process, not
   procedural."*; NEW: *"...procedural due process requires notice, opportunity to be heard, an impartial court with
   jurisdiction, and lawful judgment after hearing."*). Same behavior in both versions — pre-existing, not a
   regression this release introduces.

4. **Formula-shaped choices (no digit, e.g. `"V = IR"`) correctly fall to the prose branch** under the
   numeric-conditional wording (they contain no digit, so `isNumericUnitLiteral`-style logic excludes them) — but
   the model restates that formula verbatim in the explanation anyway, in BOTH the OLD and NEW run
   (`"...hence $V = IR$"` / `"...expressed as V = IR"`). This is the prose branch's own long-standing "do not
   state...any choice's text" rule being imperfectly followed, identically under both prompt versions — not
   something H5 introduced or worsened; formulas that double as an MCQ choice were already being echoed before this
   change.

5. **Prose-branch paraphrase level is comparable between OLD and NEW**, both producing explanations that closely
   paraphrase the correct choice's wording when the choice itself already reads as an explanation (a known
   characteristic of these five choices being long explanatory phrases, not literals) — no visible widening of this
   pattern under the new wording.

## Verdict

No new failure mode surfaced. The intended behavior (state the numeric value, still ban letters) works correctly
on this sample; the two imperfect-compliance patterns found (distractor discussion, formula-text echoing) are
pre-existing under the OLD prompt and unaffected in kind or apparent frequency by H5's change. This clears the
locked "before/after sample review" gate for merge. The coverage-ratio checkpoint (already scoped in `RELEASES.md`
and `docs/claude-plans/2026-09-27-h5-coverage-ratio-baseline.sql`) remains the right instrument for a
production-scale read post-deploy — this 4-sample review is a qualitative gate, not a substitute for that.
