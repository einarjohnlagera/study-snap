# Fix plan — regeneration failures on the generated title (2026-09-09)

**Written by:** Prod Investigator session, 2026-09-09. **For:** the implementing session.
**Evidence:** `docs/claude-findings/2026-09-09-regeneration-invalid-title-failures.md`. Read it first —
this plan does not repeat the proof, and Leg A looks like a nice-to-have until you have seen §4.

**Status: NOT STARTED.** No code, config or prompt changed by the investigating session; its only
artefacts are that findings file and this plan.

---

## 1. Settled facts — do NOT re-derive

1. All 7 failures are `failureCode=LLM_INVALID_OUTPUT`, each preceded by
   `"The note generation service returned an invalid title."` — raised in exactly one place,
   `OpenAiLlmStudyPackService.buildGeneratedNoteContent:2373-2378`.
2. **Deterministic**: batch `6a7a8fa9` retried the three failures of `f5c690f5` and all three failed
   identically. Retrying is not a workaround.
3. `MAX_GENERATED_NOTE_TITLE_WORDS = 12` (`:75`), enforced via
   `StringNormalizationUtils.countWords` = `trim().split("\\s+").length`.
4. **Published contract for the title carries no numeric bound**: schema `maxLength: 160` characters
   (`:1132-1134`), prose rules `note-generation-developer.txt:18-30`, and
   `note-generation-system.txt` has **zero** matches for word/length/concise/any number. The same
   prompt file *does* publish `{MAX_ITEM_CHARS}` (3×) and `{MAX_WORDS}`.
5. `OpenAiLlmStudyPackService:2553-2561` documents the removal of an unpublished word ceiling from
   **bullets** and instructs: *"do not reintroduce a bound the model cannot see."* The title still has one.
6. **The generated title is USED**, not discarded — it becomes the first line of the note body.
   ⚠️ Do not "fix" this by skipping title validation on the regeneration path.
7. Both LLM calls run with **no transaction and no JDBC connection held** (`v0.112.0`,
   `StudyPackService:795-803`). ⚠️ Nothing here may make `generateStudyPackFromExistingNoteAsync`
   public or route it through the `@Transactional` proxy.

---

## 2. ⚠️ Leg A FIRST — the failing branch is unknown, and today it is unknowable

**This is the highest-leverage change in the plan and the smallest.** The log records a failure code
and a human sentence. It does **not** record the rejected title, its word count, or which bound
failed. Four notes failed seven times and produced no evidence of *what* was wrong.

**Change:** when `normalizeGeneratedNoteText` rejects, log the field name, the bound that failed
(`blank` vs `wordCount`), the measured count, the limits, and the offending value.

⚠️ **This is the `v0.87.0` lesson repeating.** That release added `failed_topic_reasons` (V119)
because "which topic failed" without "why" had already cost two investigations. The same gap now
exists one layer down, on the validator itself.

⚠️ **Decide the value-logging rule deliberately (§5.1).** A note title is user-adjacent content. The
count and the bound are the diagnostic payload; the raw string is what makes the *next* occurrence
resolvable in one read. Truncation or a length-only form is a legitimate answer — but choose it,
rather than arriving there by omission, which is how the current gap was created.

**Leg A is independently shippable and does not depend on any decision in Leg B.** ⚠️ **Ship it even
if Leg B is deferred** — without it, the next occurrence is equally unresolvable.

## 3. Leg B — reconcile the enforced bound with the published one

Three options. **The audit does not pick one; §5.2 is an owner decision because each changes what
learners see.**

- **B1 — publish the bound.** Template a `{MAX_TITLE_WORDS}` line into the `title` section of
  `note-generation-developer.txt`, exactly as `{MAX_ITEM_CHARS}` and `{MAX_WORDS}` already are.
  Smallest change; keeps the current output shape; makes the contract honest.
- **B2 — validate on characters**, matching the published `maxLength: 160` and the bullets' precedent.
  This is what `:2553-2561` did for bullets. ⚠️ It **widens** what is accepted: a 20-word title under
  160 chars would now pass, and that title is the note body's first line.
- **B3 — raise the word cap.** ⚠️ **Weakest option — it moves the threshold without closing the
  class**, which is the `v0.100.0` mistake recorded for the public-catalog fetches. Pick it only with
  a stated rationale for the new number.

⚠️ **Do NOT do B1 and B2 together without deciding which is authoritative.** Two bounds on one field
is how this defect was created.

## 4. Explicit non-fixes — do not re-propose

- ⚠️ **Do NOT increase `MAX_INVALID_OUTPUT_ATTEMPTS`.** The failure is deterministic — the retry
  already ran and failed identically. More retries buy nothing and cost an LLM call each.
- ⚠️ **Do NOT skip or soften title validation on the regeneration path only.** The title is the note
  body's first line (§1.6); a divergent rule between first generation and regeneration is a new defect.
- ⚠️ **Do NOT rename or retitle the four failed notes to work around it.** `v0.120.0 — Canonical Note
  Title Integrity` established that the typed title is canonical and that existing notes are not
  swept. Their titles are correct; the validator is what rejected the model's *output*.
- ⚠️ **Do NOT touch the `subject value='Education' … overly broad ai suggestion ignored'` path.** It
  appeared 6× in the window and is a working guard reporting normal operation.
- ⚠️ **Do NOT bundle the `OfficialChallengeQuizTemplateService` seed failures** (findings §7). Separate
  symptom, unproven relation, and folding it changes the verification tier.

---

## 5. Decisions owed BEFORE implementation

1. **Leg A — log the rejected title verbatim, truncated, or count-only?** Diagnostic value against
   logging user-adjacent content. Recommend: the bound, the measured count and the limits
   unconditionally; the value truncated.
2. **Leg B — B1, B2 or B3?** B2 changes what titles are accepted into note bodies, so it is a product
   decision, not a validation tidy-up. Recommend **B1**: smallest, output shape unchanged, and it is
   the option that makes the contract match what is enforced.
3. **The four failed notes.** They are `FAILED` and will stay so until regenerated again. Nothing in
   this plan repairs them; after the fix they need a re-run. ⚠️ **That re-run is the owner's** — and
   it is also the only real end-to-end confirmation the fix worked, since §4 of the findings could not
   confirm the branch.

---

## 6. Pre-declared guards

⚠️ Two consecutive silent no-ops (`v0.116.0` item 4, `v0.117.0` items 3-4) shared one tell: **a diff
changed behaviour while touching no test that runs it.** These are written so a fixture cannot pass
under both the defect and the fix.

- **Leg A, discriminating guard:** assert the emitted log/exception payload **names the bound and the
  measured count**. ⚠️ A test asserting only that generation fails **passes under both the defect and
  the fix** — it already fails today.
- **Leg B1:** assert the rendered prompt **contains the numeric bound**. ⚠️ Assert against the
  *rendered* prompt, not the template file — the placeholder is substituted at build time, and a test
  that greps the raw template passes even if substitution is broken.
- **Leg B2:** assert a title that is **≤160 chars but >12 words** is now ACCEPTED, and one over 160
  chars is still rejected. ⚠️ A fixture whose title is short in both dimensions cannot distinguish a
  word bound from a character bound and proves nothing — that is precisely the shape that let this
  bound survive the bullets fix.
- **Regression guard, all legs:** the four real titles in findings §1 must round-trip. ⚠️ Use those
  strings, not invented ones — they are the only known-failing inputs, and an invented "long title"
  fixture is a guess about the failure mode the logs never confirmed.
- ⚠️ **Reaching the validator the way production does:** exercise `buildGeneratedNoteContent` through
  the real response-parsing path. A fixture that hand-builds a `PromptGeneratedNote` skips
  `repairJsonEatenLatexCommands` and the whitespace normaliser, either of which could be the branch
  that actually fired.

---

## 7. Verification tier and routing (recommendation, not a ruling)

- **Leg A alone:** one file, log/exception payload only, no contract change → a single `advisor()` call
  on the diff. **Routing: Claude Code inline.**
- **Leg A + B1:** adds a prompt template change → still `advisor()` on the diff, plus the rendered-prompt
  guard. **Routing: Claude Code inline** — two files, no migration, no endpoint.
- **Leg B2:** changes what is accepted into a note body on a shipped generation path → **one scoped
  cold agent framed as falsification**, targeting the regression guard above.

⚠️ This does not reach the three-agent tier: no permission substrate, no cross-user read, no
money/quota semantics, no migration. ⚠️ **And the transport lesson does not apply** — no new endpoint
is added, so there is nothing here owing a real-request `MockMvc` test.

---

## 8. ⚠️ Version and branch — read before cutting anything

**`v0.137.0 — Deploy Integrity` is open** (kicked off 2026-09-09, base `releases/v0.137.0`,
routing CLAUDE CODE) and **this work is outside its scope.** Its anti-drift block is about deploy
detection and the pending reads; nothing here touches an analytics event or a gating condition a live
checkpoint reads, but that is *not* a licence to fold it in.

⚠️ **The investigating session wrote both artefacts while on `main`.** They are uncommitted docs. Per
`CLAUDE.md`, **all changes including docs-only go on a branch and merge via PR** — `main` is enforced
by ruleset `17016892`. Cut a docs branch for these two files before committing.

**Recommendation:** ship **Leg A** in `v0.137.0` if the owner accepts a small scope addition — it is
one file, it is observability rather than behaviour, and every day it is deferred is another
unresolvable occurrence. Otherwise open a **`v0.138.0`** carrying Legs A and B together. ⚠️ **Do not
fold Leg B2 into an open release** — it changes generation output and would move the verification tier
for the whole release, which is the compounding effect `CLAUDE.md` warns about.

Whichever is chosen, **kickoff precedes any code.**

## 9. Obligations at signoff

- **Add Backlog Index rows** for the findings file and this plan.
- **Update `docs/features/`** for whatever documents note generation / regeneration if Leg B changes
  what titles are accepted — B2 in particular is a behavioural change, not a fix.
- ⚠️ **If Leg B ships without Leg A**, record plainly in `RELEASES.md` that the causal branch was never
  confirmed and the fix is therefore **unverified against a real failure** — findings §4 could not
  establish whether the word bound or the blank check fired. Do not record it as confirmed.
