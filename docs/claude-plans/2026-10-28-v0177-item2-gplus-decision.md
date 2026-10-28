# v0.177.0 item 2 decision: Variant G+ (Product UX / owner, relayed)

Status: **RECORD, verbatim.** Pasted by the owner into the Release Implementor session on 2026-10-28, in answer to `2026-10-28-giveaway-only-prompt-wording-draft.md`. I have the owner's relay, not the original message. It authorizes no paid call, no production prompt change, no merge and no deployment.

Product UX / Owner decision for v0.177.0 Item 2:

**Choose Variant G+ — APPROVED IN PRINCIPLE.**

Use all five previously approved Change B choice-quality bullets, verbatim and in their original order, without Change A (cognitive-demand wording) or Change C (explanation wording).

Reason: The primary learner problem is implausible, easily eliminated distractors, not merely formatting giveaways. Variant G addresses structural giveaways but is unlikely to meet Luna's G2 distractor-plausibility gate. G+ directly targets both semantic plausibility and structural answer giveaways.

The evaluation should answer whether Change B alone improves quality relative to the current production prompt. It does not need to attribute improvement to individual bullets.

**Implementation boundaries:**

1. Create only the separate test resource file for G+. Do not edit the production prompt or the pinned v0.176.0 proposed prompt.
2. Preserve all other production prompt wording exactly, including the existing `{DIFFICULTY}` line, for controlled comparison. This is an evaluation baseline, not renewed product approval of hidden difficulty behavior.
3. Use the five Change B bullets exactly as previously owner-approved. No silent rewording.
4. Confirm the added guidance does not change MULTI_SELECT correct-answer cardinality, assertion-style fixed choices, MATCHING behavior, or other existing question formats.
5. Add the exact-text drift guard and production-prompt hash verification described in the proposal.
6. Freeze and pre-register the test resource hash before any evaluation.
7. Do not run paid calls, change production prompts, merge, or deploy without separate approval.

**Housekeeping:** You may push the three existing release-branch commits together with the next commit, after verifying their contents and confirming they contain no secrets, private learner material, or unintended production changes. Pushing is not authorization to merge or deploy.

Please prepare the Codex implementation brief for Variant G+ and report the resulting test-resource hash and guard-test status. Do not reopen the G versus G+ decision unless a genuine conflict with the frozen evaluation design is discovered.
