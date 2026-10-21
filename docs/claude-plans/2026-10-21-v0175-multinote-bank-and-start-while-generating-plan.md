# v0.175.0 — multi-note bank-ization and "Start Quiz while still generating" — plan

**Status:** OWNER-CONFIRMED, 2026-10-21 — planning only, no code changed, nothing committed, not staged.
All three original open questions are now answered (see each section's "Owner decision" line). Written
by Claude Feature Planner after the owner asked (1) to plan fixing multi-note Challenge Quiz to use the
question bank the way single-note already does, and (2) for a read on handling "Start Quiz" clicked
while a quiz is still generating.

**Update, 2026-10-21: Product UX (ChatGPT) reviewed this plan and returned a full pass** — approved A.1,
A.2, A.5–A.7 and B.2 as written (with elaborations folded in below), required A.4 as mandatory with an
expanded test list (folded in below), and asked for a feasibility investigation before finalizing A.3. That
investigation ran through several rounds of code verification (including two real corrections found only
by re-reading the actual repository/service code: R1 isn't in production yet, so the cutoff needs to be
config-driven rather than a literal timestamp; and a third claim path, Redo Missed Questions, was missed on
the first pass and needed its own fix). **The owner then answered all three resulting sub-questions via
`AskUserQuestion`, 2026-10-21, confirming every recommended option** — see the "Remaining open item —
CLOSED" section near the end for the full record. **This plan now has no open owner question and is ready
for Item 5's Codex brief.** Release Implementor should be told this unblocks `item2b` and item 3's brief,
which were held pending this sign-off.

---

## Part A: Make multi-note Challenge Quiz claim from the bank

### A.0 Current state, verified directly against `releases/v0.175.0` (`5c80f809`)

- Single-note Challenge (`prepareSingleNoteSession`) is bank-first: claim from the bank
  (`claimEligibleQuestions`), then Official templates (`copyTemplateQuestions`), then live-generate only
  the shortfall, and persist that shortfall back to the bank for future reuse.
- Multi-note Challenge's **initial assembly** (`generateChallengeQuizForSources`) does none of this. It
  calls `quizGenerationService.generateChallengeQuiz` once per source note, unconditionally, every time.
  The delivery log already records this plainly: `banked=0, template=0` on every multi-note start. Nothing
  generated at *initial assembly* is persisted to the bank either — a fresh multi-note start today leaves
  no trace for later reuse at that moment.
  - **⚠️ Correction, found on a later review pass: this is not true of the whole multi-note lifecycle.**
    `generateMoreQuestions` (the **`+5` endpoint**, `ChallengeQuizService.java:1296-1391+`) is called for
    *any* in-progress Challenge session, multi-note included, and it already calls
    `claimEligibleQuestions`/`copyTemplateQuestions`/`persistGeneratedQuestions` — exactly single-note's
    bank-first sequence — scoped to `session.getStudyPackId()`, the session's **primary** pack only (the
    code's own comment at that call site: *"even in a mixed session these new items genuinely originate
    from that primary source"*). So a multi-note session already has live bank-claim and bank-persist
    exposure today, via `+5`, for its primary source only — just not at initial assembly, and not for
    secondary sources at all. This matters for A.3's scope below: Option A's filter on
    `claimEligibleQuestions` already covers multi-note's existing `+5` exposure for free; Option B, scoped
    to "the new multi-note call site," would need to explicitly also flag `+5` calls on a multi-note
    session or it would leave this already-live exposure unprotected.
- Consequence already reported: discarding and re-preparing the same multi-note set pays full
  generation again, every time — up to 6 sources on Plus/Pro, at the latencies already measured
  (Sol median ≈216s for 6×3).

### A.1 Proposed mechanism — **Product UX: APPROVED as written**, no changes requested

For each source note in the multi-note set, mirror `prepareSingleNoteSession`'s own sequence instead of
calling the model unconditionally:

1. Claim from that source's own bank: `claimEligibleQuestions(userId, sourceStudyPackId,
   sourceCurriculumLevel, sessionId, disallowedKeysSoFar, sourceQuestionCount)`.
2. Copy from that source's Official template if the claim fell short:
   `copyTemplateQuestions(..., sourceCurriculumLevel, sourceGenerationStamp, sourceQuestionCount - claimed.size())`.
3. Live-generate only the remaining shortfall for that source, via the existing
   `quizGenerationService.generateChallengeQuiz` call — same call as today, just for a smaller count.
4. **Persist the freshly-generated shortfall back to that source's own bank** (`persistGeneratedQuestions`,
   claimed to this session) — multi-note today never does this, so it currently bank-izes nothing. This
   is what creates cross-pollination: a note already explored via single-note Challenge now feeds
   multi-note for free, and a note explored via multi-note now feeds a later single-note start on it too.
5. Carry the accumulating `disallowedQuestions` set across sources, exactly as today's method already
   does for the LLM call — but now also feed it into each source's **own** bank claim
   (`claimEligibleQuestions`'s `disallowedQuestionKeys` parameter), not only into the LLM prompt.

**Each source must use its own curriculum level and its own `generationStamp`, not the primary pack's.**
Today's `generateChallengeQuizForSources` already resolves a per-source `StudyPackGenerationContext`
(`sourceContext`) inside its per-source loop — that's where `sourceCurriculumLevel` above comes from, it
already exists, it's just not threaded into a bank call yet. `generationStamp` needs reading per source
too (`sourceStudyPack.getGenerationStamp()`), not inherited from the primary. **This matters for
correctness, not just tidiness:** `claimEligibleQuestions`/`persistQuestions` both take a learner-level
argument and `persistQuestions` also takes a stamp, and `findClaimableForUpdate`'s own stamp check is a
per-pack subselect. If source B's rows get persisted or claimed under the primary pack's level or stamp
instead of B's own, they will never match a later single-note claim on B — the cross-pollination this
fix exists to create would silently fail for every non-primary source, the same silent-drain failure
mode as A.4. **Add this to A.4's required test:** a multi-note start followed by a single-note Challenge
start on one of its non-primary sources should see that source's freshly-banked rows.

(A per-source `generationStamp` *check at Start Quiz* — detecting a non-primary source regenerating
mid-flight — is a related but separate problem. The code comment at the top of `startQuiz()` already
defers that specifically to item 5; this plan doesn't change that.)

### A.2 A gap found while re-reading the current code, independent of this fix — **Product UX: APPROVED**

Product UX's own framing of this item: apply the exclusion consistently to bank claims, Official template
selection where applicable, and live generation, while preserving existing cross-source duplicate
prevention — matches what's already written below with no changes requested.

`generateChallengeQuizForSources`'s *disallowed-keys* input today, from its one caller, is
`extractQuestionTexts(studyPack.getQuiz())` — **the primary source's own saved quiz only.** Nothing
excludes a *secondary* source's own saved Quick Review/Study Pack quiz from that source's own Challenge
content. This is a pre-existing answer-key exposure (the Quiz tab is an answer key, per the `v0.74.0`
reasoning this product already treats as settled) that exists in **today's live-only code**, not
something this fix introduces. Fixing it is cheap once each source's own bank-claim call already needs
its own per-source disallowed-keys set (step 1 above) — the same set should also exclude that source's
own saved quiz, not just the primary's. Recommend folding this fix in rather than filing it separately,
since the code it touches is the same code this change already rewrites.

### A.3 A real tradeoff this reintroduces — revised per Product UX review, CLOSED 2026-10-21 (owner-confirmed)

**R1 (the statement-pair answer-key guard) only applies to content generated after it shipped. R2 (the
production answer-key scan and repair) only ever covered Official/ADMIN-owned content** — confirmed
directly: `docs/claude-findings/2026-10-19-v0175-r2-statement-pair-answer-key-scan.md` states learner-owned
bank rows were "**counted only, never read**, because R2's approval covers Official content." Ordinary
learners' own private-note bank rows have never been scanned or repaired, and some predate R1.

**On today's `releases/v0.175.0` branch, once that work ships**, multi-note's *initial assembly* always
generates fresh under R1's guard — no bank-serving exposure there. (Its already-shipped `+5` path is a
narrower exception, scoped to the primary source only — see A.0's correction above; this A.3 analysis
applies equally to it.) **After this fix**, a repeat multi-note start could serve an old, pre-R1,
never-checked private bank row on a repeat prepare, across every source — the same unverified-content risk
single-note Challenge will carry once R1 ships, extended to multi-note's full source set for the first
time.

**Product UX's revision (2026-10-21):** approves bank reuse in principle, but does not accept the blanket
"any eligible row, old or new" framing for the specific sub-case of pre-R1 **statement-pair** rows —
these are the exact format R1's guard exists to catch, so serving an unverified one defeats the point of
having shipped R1 at all. Non-statement-pair rows and post-R1 statement-pair rows stay eligible under
today's rules; only pre-R1 statement-pair rows need a targeted exclusion. Product UX asked five feasibility
questions before accepting this as buildable cheaply. Answers, each verified directly against code:

**⚠️ Correction to the first draft of this investigation: R1 is not in production, so there is no
production deploy timestamp to look up.** `CLAUDE.md`'s own current-version block: v0.175.0 is *"In
Progress... cut from releases/v0.174.0... NOT yet on main. It merges only after v0.174.0's PR #1512 has
merged,"* and v0.174.0 itself is still *"Released"* but held, undeployed, behind two checkpoints. R1 ships
inside v0.175.0. **Every bank row in production right now, without exception, predates R1** — there is no
mixed pre-/post-R1 population to split by a historical timestamp today. The cutoff this policy needs is
*v0.175.0's own future deploy moment*, which doesn't exist yet and can't be hardcoded into a Codex brief
written before that deploy happens.

1. **Reliable identification — yes, two existing signals, no new column; the cutoff value itself needs a
   different mechanism than "look up a timestamp."**
   - *Format:* `QuizValidationUtils.isStatementPair(String question)`
     (`backend/src/main/java/com/studysnap/backend/util/QuizValidationUtils.java:50-52`) — the exact
     detector R1 itself uses at generation time (`PAIR_ONE`/`PAIR_TWO` regexes matching `"statement 1:"` /
     `"statement 2:"`). Directly reusable against any bank row's stored `question` text, old or new.
   - *Time:* `ChallengeQuizQuestionBankEntity.generatedAt` is a real, **non-nullable**
     `OffsetDateTime` column (`entity/ChallengeQuizQuestionBankEntity.java:53-54`,
     `@Column(name = "generated_at", nullable = false)`) — confirmed no null-handling edge case.
   - **The cutoff must be config-driven, not a literal in the code**, because the value (v0.175.0's actual
     production deploy time) won't exist until after this ships. Recommend a nullable config property
     (e.g. `challenge-quiz.statement-pair-r1-cutoff`), **conservative default when unset: treat every
     statement-pair row as excluded** (i.e. unset behaves as "cutoff is infinitely in the future," not "no
     cutoff applied") — that is the safe direction per Product UX's own instruction to protect learner
     trust over throughput. **Setting the real value is a post-deploy owner step** — this session never
     touches Render env vars or production config (`CLAUDE.md`'s standing rule), so the handoff is: ship
     the code with the conservative default, the owner sets the real cutoff once v0.175.0 is actually live.
   - Combined filter: `isStatementPair(question) AND (cutoff is unset OR generatedAt < cutoff)` reliably
     identifies the legacy set once a cutoff exists, reusing R1's own detector rather than a new,
     unverified heuristic.

2. **No migration, no backfill.** Both signals already exist on every row. This is a query-time /
   service-layer filter: fetch candidates via the existing `findClaimableForUpdate`
   (`repository/ChallengeQuizQuestionBankRepository.java:43-60`, unchanged), then in the service layer drop
   any row where the combined filter matches, **leaving it unclaimed** (not deleted, not modified) so it
   falls through to shortfall generation. Zero schema change. This also directly satisfies Product UX's "do
   not scan or modify learner-owned private question-bank content without separate owner approval" — the
   filter reads and skips, it writes nothing, and no fresh scan of learner content was run to produce this
   investigation (see point 3).

3. **Volume, correctly framed as a stale snapshot, not a fresh scan.** Per the R2 scan doc's own
   counted-not-read figures, dated **2026-10-19**: **4** learner-original bank rows and **22** learner bank
   copies of Official templates were statement-pair format at that time
   (`docs/claude-findings/2026-10-19-v0175-r2-statement-pair-answer-key-scan.md`, "What was scanned"
   table). **This investigation did not re-run that scan against current learner content** — Product UX's
   own instruction was not to, without separate owner approval — so treat 4+22 as a lower bound from
   almost a week before deploy, not a current count; today's live (pre-R1) code keeps generating more
   statement-pair rows every day, so the real number at actual deploy time will be higher. Order of
   magnitude still applies: this is nowhere near "a large portion of valid questions," but the plan should
   not cite 4+22 as current.
   - **Template-copy problem, found on a closer pass and real enough to need its own fix, not just a
     caveat.** `copyTemplateQuestions` sets each copy's `generatedAt` to `OffsetDateTime.now()` at copy
     time (`service/OfficialChallengeQuizTemplateService.java:225,242`), so a plain `generatedAt < cutoff`
     filter would exclude every pre-deploy copy of an Official template — **all 22 of them** — not a
     handful incidentally caught, but the entire copy population, since no copy made before deploy can
     ever be post-cutoff. (**The 22 are clean, not a soft spot**: R2's handoffs specifically looked up
     learner copies of every template row it fixed or deleted, and found exactly two — both individually
     corrected — with zero copies among the deletes
     (`docs/claude-findings/2026-10-19-v0175-r2-statement-pair-answer-key-scan.md`, "Totals across the
     three handoffs"). The real residual exposure is narrower and sits in two places instead: (a) a copy
     made between R2's 2026-10-19 copy count and the owner applying the repair handoffs on 2026-10-20, of a
     template that repair later fixed — a one-day window; and (b) a copy of one of the ~50 template rows
     R2 explicitly left "kept as keyed" after an ambiguous review, which were never confirmed correct, only
     not confirmed wrong. Both are genuinely small and named here rather than assumed away; folded into the
     owner-approved fresh-count offer in the closing "Remaining open item" section.) **And this is not just
     a one-time miss: the same
     method permanently closes the door on fixing it.** `copyTemplateQuestions` builds its own `excluded`
     set from every question key already present in the learner's bank for that pack
     (`findQuestionKeysByUserIdAndStudyPackId`, line 217) *before* deciding what to copy — so once a copy
     exists, the method will never copy that same template row again, whether or not the existing copy is
     currently claimable. An excluded-by-this-filter copy is excluded **forever**, not just until the next
     copy pass. Each one permanently shrinks that pack's available pool; once a pack's un-copied template
     rows run out, a start that today's bank+template combination could fill instantly flips to async live
     generation (`ChallengeQuizService.java:480`'s `claimed.size() == quizCount` branch). This is exactly
     the "recreating the expensive generation behavior we're trying to eliminate" risk Product UX's own
     closing note warned about, at a scale (all 22, growing) well past "a handful of rows."
   - **Fix: exempt template-derived rows from the time filter using the question key as a provenance
     proxy, verified sound.** A copy's `questionKey` is inherited unchanged from its source template row
     (`copyTemplateQuestions` line 227: `source.getQuestionKey()`) — the same normalized-question-text
     scheme (`QuizDeduplicationUtils.normalizeQuestion`) every bank row uses, learner-generated or
     Official. So *"this row's key currently matches a row in its pack's Official template"* — resolved
     the same way `copyTemplateQuestions` itself already resolves its template
     (`resolveTemplate(studyPackId)`, `OfficialChallengeQuizTemplateService.java:165-184`, then the
     template owner's own bank rows) — reliably identifies template-derived content with no new column.
     Checked for soundness: the only way a learner-*generated* row could false-positive on this proxy is
     an LLM generating text that normalizes identical to an existing Official template question for that
     same pack, which the existing per-source disallowed-keys/dedup logic already works against within a
     single prepare — negligible, and if it ever happened the row would be a verbatim duplicate of
     R2-checked content anyway, not a new risk. **Revised filter: exclude a statement-pair row only when
     it is pre-cutoff AND not template-derived.** This protects exactly the population Product UX is
     actually worried about (learner-original, never independently checked) without permanently draining
     already-R2-checked template copies.

4. **Effect of exclusion, with the template exemption applied, is close to today's status quo for initial
   assembly, not a regression.** An excluded row (now: pre-cutoff, learner-original, statement-pair only)
   is simply treated as unclaimed, falling through to live generation under R1's guard once R1 ships —
   close to multi-note *initial assembly's* current (pre-R1, zero-bank) behavior for those items. (The
   already-shipped `+5` path is a narrower case — see A.0's correction — and gets the same treatment:
   excluded rows there also just fall through to live generation, which is already what `+5` does for any
   shortfall today.) Template-derived copies no longer take
   this path at all, so the pool-shrinkage/async-flip risk above is closed. **This "no added cost" holds
   for multi-note specifically.** It does not hold for the already-shipped paths discussed in point 5 below
   if the owner extends the filter to them — see that point, and the post-deploy paragraph after it, for
   the cost that applies there instead.

5. **The real Q5 — how this applies consistently to single-note too — is the actual owner tradeoff here,
   and the first draft of this investigation answered the wrong question.** Product UX's fifth question
   was *"how the same policy could eventually apply consistently to single-note and multi-note
   Challenge,"* not whether a reported-question mechanism exists (that was a separate, satisfied
   constraint — covered at the end of this point). This matters structurally: `findClaimableForUpdate` and
   the service method that calls it, `claimEligibleQuestions`
   (`service/ChallengeQuizQuestionBankService.java:44-75`), are **shared** — single-note Challenge already
   calls this exact method today, in production, right now. There's also a **third, separate claim path**
   this investigation missed on its first pass and found on this review: **Redo Missed Questions**
   (`docs/features/challenge-quiz.md`'s actual name for the feature — not `CHALLENGE_QUIZ_ADOPTION.md`'s
   older "Retry Incorrect Questions" label, which this draft used first and is wrong). It claims via its
   own independent query, `findIncorrectClaimableForUpdate`
   (`repository/ChallengeQuizQuestionBankRepository.java:62-75`, `PESSIMISTIC_WRITE`, filtered on
   `lastKnownOutcome = INCORRECT`) through `claimIncorrectQuestions`
   (`service/ChallengeQuizQuestionBankService.java:180-214`), called from
   `ChallengeQuizService.java:885` — entirely bypassing `claimEligibleQuestions`. Three real options, not
   two, since Redo Missed is single-note-only by construction and "Option B" (multi-note call site only)
   can't reach it by definition:
   - **Option A — filter inside `claimEligibleQuestions` AND `claimIncorrectQuestions` (recommended).**
     Protects multi-note, single-note, and Redo Missed identically. Consequence to be explicit about: this
     changes **already-shipped** behavior on two paths — single-note would stop serving the small
     learner-original legacy set it currently does, and Redo Missed would stop offering legacy
     learner-original statement-pair rows as "missed" candidates (template copies stay served either way,
     per the exemption above). Closing an existing, live exposure is the point, but it's new scope beyond
     "fix multi-note," worth the owner's explicit yes rather than a silent bundle.
   - **Option B — filter only for multi-note.** Leaves single-note's and Redo Missed's existing exposure
     exactly as today. Narrowest, but leaves the two paths that have actually been live and accumulating
     exposure the longest unprotected.
   - **Option B+Redo — multi-note plus Redo Missed, single-note's main claim path left alone.** A possible
     middle ground given Redo Missed's specific risk profile (point below), without touching single-note's
     primary claim path.
   - **Implementation note that applies to all three options equally, not just A:** both
     `claimEligibleQuestions` and `claimIncorrectQuestions` claim rows *inside* their own candidate loop
     (`setClaimedSessionId`, stopping once `count`/`claimCount` is reached —
     `ChallengeQuizQuestionBankService.java:72-74` and `:196-205`) — the filter has to run as part of that
     loop's own eligibility check, not as a pass over the method's return value. A post-hoc filter at the
     call site would still count a since-excluded row against the claim target and couldn't un-claim it.
     So "Option B" is really the same single filter, parameterized by which caller turns it on, not a
     different code path — reword any brief written from this plan accordingly.
   - **Why Redo Missed is worth weighing on its own, separately from "more paths = more consistent":** it
     specifically re-serves questions recorded `INCORRECT`, which is exactly where a wrong-keyed legacy
     question would resurface for a learner who actually answered correctly — precisely the "known
     incorrect... silently reused" scenario Product UX's review named by name. R2's own scan recorded one
     wrong-key-recorded-`INCORRECT` case in production, `701891d3-…`, demonstrating the mechanism is real —
     though that specific row is now corrected and would be template-exempted either way, so it illustrates
     the risk class rather than being a currently-open instance. The genuinely open residual risk is the
     ~4 never-checked learner-original rows, plus the two narrow template-copy windows named in point 3's
     sub-bullet above (the one-day gap, and copies of the ~50 "kept as keyed" ambiguous templates).
   - **If Redo Missed is included, its count and its claim must move together, or the CTA lies.**
     `PostSessionNextStepService.java:203` calls a *separate* count method,
     `countEligibleIncorrectQuestions` → `countIncorrectEligibleQuestions`, with **no statement-pair
     filter**, to decide whether to show the "Redo Missed Questions" CTA at all
     (`docs/features/challenge-quiz.md` line 146: shown "when at least three eligible bank entries were
     last answered INCORRECT"). If the claim gets the filter but the count doesn't, the CTA can show, the
     learner clicks it, and the claim — which **has no LLM fallback and fails closed below the minimum**
     (`ChallengeQuizQuestionBankService.java:176-179`, confirmed: *"a bank failure or a claim race fails
     closed rather than starting a partial redo"*) — throws `NotEnoughMissedChallengeQuestionsException`.
     **This reframes point 4's "no added cost" for this specific path: the cost here is availability, not
     generation** — fewer Redo-eligible questions, possibly dropping a learner below the 3-question
     minimum and losing the CTA entirely, not a live-generation fallback (there is none on this path). If
     the owner includes Redo Missed in scope, the count method needs the identical filter applied, not
     just the claim — and precisely: `claimIncorrectQuestions` computes `claimCount` and checks it against
     the minimum **before** its per-candidate loop runs
     (`ChallengeQuizQuestionBankService.java:196-199`), so the filter must apply to the `candidates` list
     itself, not inside the loop as a `continue` — a `continue` added inside the loop would silently bypass
     the minimum-count check and could start a short redo instead of correctly failing closed. Recommend
     one shared predicate method for "is this row filter-excluded," used identically by the count query
     (as a fetch-and-filter, since the detector is a Java regex over JSON content, not expressible in
     JPQL) and the claim's candidate list — so they agree by construction, not by a test written to catch
     drift after the fact.
   - **This is the "resulting owner tradeoff" Product UX's review asked this investigation to bring back.**
     **Owner decision, 2026-10-21 (via `AskUserQuestion`): Option A confirmed** — all three paths
     (multi-note, single-note's main claim path including `+5`, and Redo Missed Questions) get the filter.
   - **One more thing Product UX's review said explicitly and this plan should not lose:** *"R1 consistency
     validation must not be represented as independent factual verification."* Whatever cutoff the owner
     sets, "post-cutoff" only means "passed R1's internal statement/key consistency check," not "confirmed
     factually correct." R1's own acceptance run found 3 of 32 statement-pair keys wrong on content that
     passed the consistency check cleanly — all factual-verdict errors, the exact class R1 cannot catch
     (git commit `ba3cb428`'s message: *"blind Opus check against source notes: 3 of 32 statement-pair
     keys wrong (2 clear, 1 arguable), all factual-verdict errors the check cannot catch"*). The cutoff
     policy reduces exposure to R2's already-identified defect class; it does not certify post-cutoff
     content as correct in general.
   - **A third residual for the template exemption, alongside the one-day gap and the ~50 kept-as-keyed
     rows named earlier: a template seeded *after* R2's 2026-10-19 scan and *before* v0.175.0 deploys is
     template-derived (exempt) but was never R2-checked and, since it predates R1's deploy, never
     R1-guarded either.** Verified the generation path is otherwise sound: `seedTemplateAsync`
     (`service/OfficialChallengeQuizTemplateService.java:277-327`) calls the same
     `quizGenerationService.generateChallengeQuiz` method single-note and `+5` both use, so once R1 ships,
     every writer to the bank — initial generation, `+5`, and template seeding — goes through the same
     guard; the gap is specifically pre-deploy seeding. R2's own doc names one source of this
     (`docs/claude-findings/2026-10-19-v0175-r2-statement-pair-answer-key-scan.md`'s "Reach" section: "B6
     batch A... on Sol (0/42 here)" — zero statement-pair items in that specific batch), so the volume
     looks small, but it isn't zero by construction the way the rest of the exemption is. Not a reason to
     change the exemption — template content gets the same future repair treatment as everything else if
     the owner later wants a check — just a residual to carry forward honestly rather than imply every
     template-derived row is already known-good.

   Separately, and re-verified fresh rather than carried over from the first draft: **no existing
   reported/flagged/moderation mechanism for individual quiz questions exists to key off instead.**
   Re-ran the search covering both backend and frontend for "report(ed)," "flag(ged)," "moderation," tied
   to quiz content — zero matches in either tree; the only hit anywhere in the codebase,
   `ContentModerationService`, governs note/content abuse moderation, unrelated to per-question
   answer-key correctness. `QuizItem` (`dto/QuizItem.java:18-31`) carries no such field either. Product
   UX's "existing verified exclusion mechanism" language does not describe something already built; the
   format+time filter above is new, if trivial.

6. **Required test, specific to A.3's own filter (separate from A.4's release-path test list):** the
   statement-pair+cutoff+template-derived filter applied consistently across every included claim path —
   **including `generateMoreQuestions`/`+5` on a multi-note session**, not just initial assembly, per
   A.0's correction above; the template-derived exemption correctly keeping a fresh post-cutoff template
   copy eligible; the conservative unset-cutoff default actually excluding (not silently passing through)
   when the config value is absent; and, if Redo Missed is in scope, a test asserting
   `countEligibleIncorrectQuestions` and `claimIncorrectQuestions` agree on the same filtered population
   (no CTA-shows-then-claim-throws gap), using one shared predicate for both.

**Confirmed concrete policy:** exclude a bank row from claim eligibility when
`isStatementPair(question) == true AND NOT template-derived AND (cutoff unset OR generatedAt < cutoff)`,
where `cutoff` is a config property defaulting to "always excluded" and `template-derived` is the
question-key-membership proxy verified above. Apply the filter inside `claimEligibleQuestions` (the main
bank-claim method, covers multi-note, single-note, and `+5`) **and** inside `claimIncorrectQuestions`
**and** its paired count method `countEligibleIncorrectQuestions` together (Redo Missed Questions — both
must move together or the CTA can show when the claim would fail closed) — per the owner's confirmed Option
A. No migration, no write to excluded rows — they stay in the bank, unclaimed, available for a future
review pass if the owner later wants them checked and repaired the way the Official templates were.

**The cutoff's post-deploy step needs an owner-visible home, not a silent default.** Add it to the
`RELEASES.md` v0.175.0 sign-off handoff as an explicit post-deploy action, the same pattern already used
for v0.174.0's Render model-env-var switch (`CLAUDE.md`'s "Previous release still undeployed" note) — a
named step the owner runs once v0.175.0 is actually live, not a config value left to be discovered unset
later. Until the owner sets it, the default holds: every **learner-original** statement-pair bank row
(template-derived rows are exempt per above, regardless of cutoff state) stays outside bank-claim
eligibility — a stricter starting position than "accept the tradeoff," and a safe one to ship with. Costs
of this default worth naming plainly, not hidden: while unset, multi-note has no added cost (point 4), but
if single-note's main path is included (Option A), it pays a small amount of extra live generation on
newly-generated statement-pair repeats until the owner sets the real value; if Redo Missed is included, its
cost is different in kind — fewer Redo-eligible questions, potentially losing the CTA below the 3-question
minimum, not generation cost, since that path has no LLM fallback.

**Status: CLOSED, 2026-10-21.** Feasibility investigated, concrete policy proposed, and the owner confirmed
all three sub-decisions (scope = Option A; cutoff = config property with conservative default and a
`RELEASES.md` handoff step; template-derived exemption = confirmed) via `AskUserQuestion`. This satisfies
Product UX's own gate ("before issuing the Item 5 Codex brief, return with the A.3 feasibility findings
and any resulting owner tradeoff") — the return happened, and the owner has now signed off on it. Ready
for Item 5's Codex brief.

### A.4 The release-side bug this fix must not ship with (found this pass, would have been silent)

Every place that releases a claim or records an outcome today passes the session's **one** primary
`studyPackId` (`session.getStudyPackId()`): `forfeitPreparedSession`, the item 2b expiry and
stuck-generation sweeps, `updateOutcomesAndReleaseClaims` at completion, and the failure paths. If
multi-note starts claiming from N source packs, rows claimed from any **non-primary** source would never
be found by any of these calls (`findByUserIdAndStudyPackIdAndClaimedSessionId` and `releaseClaims` are
both scoped to one `studyPackId`) — they would stay permanently attached to a FORFEITED or COMPLETED
session, and `findClaimableForUpdate`'s own `claimedSessionId is null or = :sessionId` guard means they
can never be claimed again by anyone. **That silently drains exactly the pool this fix exists to grow.**

Fix: add one repository method that releases claims / records outcomes by `(userId, claimedSessionId)`
alone, with no `studyPackId` filter, and route every release/outcome-update site through it instead of
the per-pack one. (Each `QuizItem` is already stamped with `sourceStudyPackId` via
`stampQuestionsWithSourceStudyPack`, so outcome-matching by question key still works per source if that
granularity is ever needed — but release itself needs no pack filter at all.)

**Product UX called this item MANDATORY — "the new multi-note bank flow must not ship unless claims from
every source pack are safely released or updated across all relevant lifecycle paths"** — and expanded
the required test list beyond the one this plan originally named. Full required set, folding both
together:
- Successful completion.
- Discard before starting.
- Prepared-session expiration.
- Failed preparation.
- Stuck-generation recovery.
- **Non-primary source reuse in a subsequent single-note Challenge** (this plan's original test: start a
  multi-note session, discard it, confirm bank rows from a non-primary source are claimable again by a
  later single-note start on that same source).
- Concurrent or overlapping preparation requests.
- Idempotent release and outcome updates (releasing/updating twice is a no-op, not an error or a
  double-write).
- **Confirm releasing one session cannot affect claims belonging to another session** — i.e. the new
  `(userId, claimedSessionId)`-scoped method's `claimedSessionId` equality check is itself tested, not
  just assumed from the query shape.

This is exactly the class of defect `CLAUDE.md`'s own "a diff that changes behaviour with no test
exercising it" rule warns about, and several of the call sites that need this fix live only in Release
Implementor's uncommitted `item2b` work, so a missed one leaves no trace.

### A.5–A.7 — **Product UX: APPROVED**, fold in one explicit sequencing note

Product UX's own phrasing: perform bank claims before deciding which sources require generation (already
A.6's shape), parallelize only the remaining shortfalls with bounded concurrency (already A.6), use stable
source ordering when acquiring bank locks (already A.7), and **coordinate the claim-release changes with
item 2b's existing work, explicitly assessing the shared-code changes under the appropriate release review
tier** — this is what A.9 below already names as a Tier-H trigger; Product UX's review independently
arrived at the same flag.

### A.5 Where this plugs in

Multi-note generation today runs **synchronously inside `startSession`**, not through
`prepareSingleNoteSession`'s async-dispatch-on-shortfall path. This fix belongs in whatever replaces that
inline path once multi-note moves onto the same prepared/async flow — i.e., it's naturally sequenced with
**item 5 (multi-note parallel with a floor)**, not a bolt-on to the current synchronous code.

**Owner decision, 2026-10-21: fold into item 5.** It touches the same synchronous multi-note code item 5
is already rewriting to add parallelization — building it twice would be wasted work.

### A.6 Interaction with item 5's own parallelization idea

Complementary, not competing: claim bank-first per source (cheap, and it must run before dispatch
decisions are made anyway), **then** parallelize only the sources that still have a shortfall across
`llmParallelTaskExecutor` — the same "H-fan" shape already proposed for item 5. Bank-izing shrinks how
much there is to parallelize; it doesn't replace the need to parallelize the remainder.

### A.7 Locking, corrected

`findClaimableForUpdate` takes a `PESSIMISTIC_WRITE` lock scoped to one `(userId, studyPackId, ...)`.
Claiming from N source packs in one multi-note prepare takes N such locks in sequence. Since bank rows
are scoped per user, this can only create a lock-ordering risk between **the same user's own** concurrent
multi-note prepares on overlapping note sets — not a cross-user risk. Recommend claiming sources in a
stable sorted order (e.g. by `studyPackId`) to avoid that one narrow case; this is a small, local
implementation note, not a design blocker.

### A.8 What this buys, stated honestly

- Free (no quota, no generation cost) repeat discard-and-reprepare of the **same** multi-note set, once
  it's been generated once.
- Cross-pollination: a note already explored via single-note Challenge now contributes to multi-note for
  free, and vice versa.
- **Does not** reduce the cost of the very first multi-note attempt on an all-fresh set of private notes —
  nothing is banked yet, so that attempt still pays full live generation across every source, same as
  today.

### A.9 Collision with `item2b` — RESOLVED, sequencing settled 2026-10-21

**Update:** `item2b` is now committed (`76abf7aa`) and open as PR #1519 into `releases/v0.175.0` (owner
said "commit"). Release Implementor confirmed the shared surface and the order: `releaseClaims(userId,
studyPackId, sessionId)` is still scoped to one pack, at roughly 10 call sites — three of them 2b-only
(`forfeitPreparedSession` and the two `GenerationRecoveryRowWriter` sweeps). **Item 5 will replace all of
those with the session-scoped release this plan's A.4 specifies. Sequencing is settled: 2b lands first,
and item 5 plus Part B build on top of it** — not a simultaneous-branch risk. Release Implementor also
confirmed this (Option A touching `claimEligibleQuestions`, shared with already-shipped single-note and
`+5`, plus Redo Missed's count and claim) is two PRs on one shared method, so it **does trigger the scoped
cold falsification review at signoff** per `CLAUDE.md`'s own gate — item 5's `RELEASES.md` entry will
reflect the larger scope. `docs/features/challenge-quiz.md` will be updated in the same PR as the code
(not a separate step), and the cutoff property's post-deploy step goes into the v0.175.0 sign-off handoff.
**Not briefing yet** — item 5 waits for the owner's go-ahead, with Part B and item 3 ahead of it in
sequence.

Original finding, kept for context: this plan and `item2b` both touch the **same release/claim path**:
A.4's new `(userId, claimedSessionId)`-scoped release method needed to replace the calls inside
`forfeitPreparedSession` and the 2b expiry/stuck-generation sweeps. B.2's exception-handling changes also
edit the same `startQuiz()` lines 2b modifies. **Two PRs touching the same shared method is one of this
repo's own standing Tier-H triggers** — confirmed above, not just flagged.

---

## Part B: "Start Quiz" clicked while the quiz is still generating

### B.0 Scope, confirmed by the owner, 2026-10-21

Three mechanisms could fit the owner's words; the owner confirmed only the first is what they meant.

1. **Challenge Quiz's own async preparation is still running** (the learner clicked "Prepare Quiz"
   moments ago, or deep-linked/raced into "Start Quiz" before backend generation finished). **Confirmed —
   this is the scope of Part B.** Verified in code (§B.1 below): today this throws an exception with a
   misleading message, it does not return a graceful "still preparing" state.
2. Study Pack regeneration racing a prepared Challenge Quiz — **not separately in scope.** Noted for the
   record: this shares the exact same underlying bug as #1 (the single
   `ChallengeQuizSessionNotInProgressException` can't tell "still generating" apart from "stale"), so
   fixing #1's error-code split (§B.2) incidentally gives the stale-pack branch a correct, distinct
   response too — a free side benefit, not something separately scoped or tested for this plan.
3. Seeding private notes at Study-Pack-generation time — **not pursued.** Out of scope for this plan.

### B.1 What's confirmed in code regardless of which reading is meant

`ChallengeQuizService.startQuiz()`:
```
if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
    throw new ChallengeQuizSessionNotInProgressException();
}
```
and, further down, the stale-pack branch throws the same exception. `ChallengeQuizSessionNotInProgressException`
is a single error code (`SESSION_NOT_IN_PROGRESS`) with one hardcoded message: *"Challenge Quiz session
is already completed."* That message is wrong for both the still-generating case and the stale case — only
one of the (at least) three situations that throw it is actually "already completed."

By contrast, the **existing prepare-time collision path already treats "an in-flight session is still
GENERATING" as a normal, expected state** — it returns the in-flight session's own state so the caller
can keep showing it, rather than erroring. `startQuiz()` doesn't follow that same spirit for the
identical underlying status today; §B.2 below gives it a distinguishable error instead, which the
frontend can treat just as routinely.

### B.2 Recommendation (agrees with the owner's instinct) — **Product UX: APPROVED**, with explicit invariants added

Product UX's review restated the required behavior as a checklist; folding it in explicitly since it
sharpens what was previously implied rather than stated:

- Do not start a partial quiz.
- Do not start the timer.
- Do not consume a monthly quiz attempt.
- Return a distinguishable `409 PREPARATION_IN_PROGRESS` response (already this plan's recommendation).
- Show the existing preparation overlay; allow the learner to stay or continue reviewing.
- Automatically transition to the ready pre-screen when preparation completes.
- **Require a fresh, explicit Start Quiz action before the timed attempt begins** — auto-transitioning to
  the ready pre-screen must not itself start the timer; that only happens on a second, deliberate click.
- **Duplicate clicks, multiple tabs, refreshes, and notification deep links must not be able to start the
  timer or charge quota twice** — this is an idempotency invariant on top of the per-status error mapping
  below, not satisfied by the error mapping alone. The existing `quotaExempt`-flip-at-successful-start
  mechanism (confirmed earlier in this work: quota only charges inside a successful `startQuiz()`, never
  on prepare/fail/discard/expiry) already gives this for free *if* every one of these paths actually
  resolves to one of the named branches below rather than falling through to a second real start — worth
  a dedicated test, not just an inference from the mechanism's existence elsewhere.

**Yes — reuse the same preparation overlay rather than building a new UI state.** Structurally this is
the same state (`GENERATING`, not ready yet) regardless of how the learner arrived at it: clicked
"Prepare Quiz" seconds ago, deep-linked from the readiness notification before it fired, raced in from a
second tab, or refreshed mid-generation. One state, one treatment.

Concrete shape, at the planning level (no code written). One instruction, not two — correcting a
self-contradiction in an earlier draft of this plan that said both "return a normal response" and "use a
409," which can't both be true:

- **GENERATING always returns an error the frontend can branch on, never a 200.** A silent-success
  response is easy for the client to mishandle and risks the "no timer starts before Start Quiz" rule if
  a bug ever reads it as a real start. Throw a new, dedicated exception → `409 PREPARATION_IN_PROGRESS`.
  The frontend's Start-Quiz handler catches that specific code and shows the same `QuizGenerationOverlay`
  ("Getting your quiz ready," progress indicator, "Continue reviewing") that "Prepare Quiz → still
  generating" already shows, auto-transitioning to the ready pre-screen when it completes.
- **Build on item 2b, don't re-propose it.** Release Implementor's uncommitted `item2b` work already adds
  `PreparedChallengeExpiredException` for the EXPIRED case. Give the other distinguishable cases their
  own sibling exception classes in the same family, rather than changing
  `ChallengeQuizSessionNotInProgressException`'s shared message — grep that exception's other call sites
  first, since changing its text could affect them too.
- **Watch `@Transactional(noRollbackFor = ...)`.** The STALE branch forfeits the session and releases its
  bank claims, *then* throws — that write only survives because the thrown type is listed in `startQuiz`'s
  `noRollbackFor`. Any new exception class used on a path that does a write-then-throw must be added to
  that list, or the write silently rolls back and the forfeit never happens.
- **The status guard throws the same generic "already completed" for every status except
  `IN_PROGRESS`, not only GENERATING — list all of them so Codex doesn't split some and leave the rest
  mislabeled:**
  - `GENERATING` → 409 `PREPARATION_IN_PROGRESS`, per above.
  - `FAILED` → B4's existing failure copy ("We couldn't finish preparing your quiz").
  - `FORFEITED` **while `preparedNotStarted` is still true** → B4's existing expired copy. This can
    happen two ways: the item 2b sweep expired it while its pre-screen tab stayed open, or the learner
    discarded it in one tab and pressed Start in another. It's the same "expired-or-forfeited" deep-link
    case already named elsewhere in this release's plan; the `preparedNotStarted` flag is what tells it
    apart from the next case.
  - `COMPLETED`, or `FORFEITED` **after a real start** (`preparedNotStarted` already false) → keep a
    genuine "already ended" message — this is the one case the current hardcoded text is actually
    correct for.
- This becomes the **fifth** deep-link/race terminal case, alongside the four already identified
  (still-ready / already-started-elsewhere / expired-or-forfeited / stale-from-regeneration) — add
  "still-generating" to that list.
- Frontend items 3 and 4 (the preparation screens, the readiness notification) aren't built yet — this is
  input for that brief, not a fix to something already shipped.

### B.3 Not pursued

Reading #3 (seed private notes at Study-Pack-generation time) was raised as a possible reading of the
owner's words but is not what was meant and is not pursued in this plan. If it comes up again on its own
merits, it needs its own cost estimate (upfront cost on every private pack, including the likely-majority
that never get a Challenge Quiz request, against removing the first-use wait) before sizing.

### B.4 Product UX's final cross-cutting requirements (apply to the whole plan, not just Part B)

Carried over verbatim in substance, since they're easy to lose track of once implementation starts:

1. Preserve requested question counts and the agreed shortfall recovery policy.
2. Do not generate questions when sufficient eligible bank/template questions are available.
3. Preserve source-note grounding and difficulty.
4. Do not charge quota for failed or unstarted preparation.
5. Keep learner-visible preparation and recovery behavior consistent.
6. **Do not expand this work into proactive generation of questions for every newly created Study Pack** —
   explicitly out of scope; this plan only ever claims/generates in response to an actual Challenge Quiz
   request, never ahead of one.

---

## Remaining open item — CLOSED, 2026-10-21

A.5/item-5 placement, B.0's scope, and now A.3 are all fully closed. **Owner decisions on A.3's three
sub-questions, all recommended options confirmed:**

1. **Cutoff mechanism: config property, conservative default.** Unset excludes every learner-original
   statement-pair row; owner sets the real deploy timestamp as a named `RELEASES.md` v0.175.0 sign-off
   step once v0.175.0 actually deploys.
2. **Template exemption: confirmed.** Template-derived rows (question-key proxy) are exempt from the
   cutoff filter regardless of copy date.
3. **Scope: Option A — all three claim paths.** `claimEligibleQuestions` (multi-note's new code, its
   already-shipped `+5` path, and single-note's main claim path) and `claimIncorrectQuestions` +
   `countEligibleIncorrectQuestions` (Redo Missed Questions, count and claim together) all get the filter.

**No open owner question remains on this plan.** It is ready for Item 5's Codex brief. Sub-decisions as
originally investigated, kept below for the brief's reference:

1. **Cutoff mechanism.** R1 isn't in production yet (v0.175.0 hasn't deployed), so there's no historical
   deploy moment to look up today. **Recommended:** a config property whose unset default excludes every
   learner-original statement-pair row, with the owner setting the real value via a named `RELEASES.md`
   sign-off handoff step once v0.175.0 actually deploys (the same pattern as v0.174.0's Render model
   switch). **Named cost of this default, not hidden:** while unset, this also excludes *new*,
   R1-guarded, learner-original statement-pair rows generated after deploy — so (under Option A) a small
   amount of extra live generation on single-note repeats until the owner sets the real value, on top of
   tightening legacy exposure. Alternative: unset means no filter applied at all (not recommended —
   nothing would be protected until the owner remembers to set it).
2. **Template-derived exemption.** Without it, the filter would permanently exclude all 22 existing
   template copies (and every future one made before the cutoff is set), since `copyTemplateQuestions`
   refuses to re-copy a key already present in the learner's bank. **Recommended:** exempt template-derived
   rows via the question-key-membership proxy (verified sound in §A.3 point 3) — note the 22 are clean per
   R2's own handoff accounting, with two narrow residual windows (a one-day gap between R2's count and the
   owner's repair, and copies of the ~50 templates R2 left "kept as keyed" after an ambiguous review, never
   confirmed correct). Alternative: no exemption, stating plainly it permanently drains every pre-cutoff
   copy and eventually flips some packs to async live generation.
3. **Scope: A / B / B+Redo, across three independent claim paths.** `claimEligibleQuestions`
   (single-note's main path, shared with multi-note) and `claimIncorrectQuestions` (**Redo Missed
   Questions** — its correct name per `docs/features/challenge-quiz.md`; an earlier draft of this plan used
   `CHALLENGE_QUIZ_ADOPTION.md`'s older "Retry Incorrect Questions" label in error) are two separate,
   already-shipped paths. **Recommended: Option A** — filter all three paths (multi-note, single-note,
   Redo Missed) consistently. **Option B** touches only the new multi-note call site, leaving both
   already-shipped paths as today. **Option B+Redo** adds Redo Missed without touching single-note's main
   path — worth weighing separately since Redo Missed's cost, if included, is **availability** (it has no
   LLM fallback and fails closed below its 3-question minimum — its CTA-gating count would need the
   identical filter too, or the CTA can show and the start then throws), not generation cost like the other
   two paths.

Two facts still worth carrying into the brief:
- **The 4+22 counts are a 2026-10-19 snapshot, not current.** Re-scanning learner content now wasn't
  authorized by Product UX's own instruction; treat these as a lower bound. A fresh count is available as
  an owner-approved read if wanted, and would also close the template exemption's residual windows.
- **The Redo Missed CTA's count and claim must move together**, confirmed in scope under Option A.

**Owner answered all three questions via `AskUserQuestion`, 2026-10-21 — all recommended options
confirmed** (see the top of this section). This plan is ready to brief.

### Implementation handoff notes (for Release Manager / Release Implementor, not an owner question)

- **Option A grew this item's real scope beyond "multi-note."** It now touches `claimEligibleQuestions`
  (shared with already-shipped single-note prepare and `+5`, and with item 2a's `5c80f809`), Redo Missed
  Questions' claim and count methods, and — per A.9 — the same release/claim surface Release Implementor's
  uncommitted `item2b` work is already modifying. `CLAUDE.md`'s pre-signoff pressure-test gate triggers on
  "two or more PRs touching the same shared method" — Release Manager should treat this as satisfied and
  size the eventual verification tier accordingly, and update item 5's `RELEASES.md` entry to reflect the
  grown scope.
- **`docs/features/challenge-quiz.md` needs updating alongside the code**, not after: its line ~142
  (claim-eligibility rules) and line ~146 (Redo Missed Questions' "at least three eligible" CTA condition)
  both change meaning once this filter ships — the per-PR feature-doc rule in `CLAUDE.md` applies directly.
- **The cutoff's post-deploy step belongs in the v0.175.0 sign-off handoff** (`RELEASES.md`), which
  Release Manager owns.
- **Sequencing against `item2b` is still Release Implementor's call** (A.9) — this plan doesn't presume an
  order, only names the overlap.
