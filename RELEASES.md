# RELEASES.md - NoteLib

## v0.138.0 - Stated and Enforced

**Status: In Progress** (kicked off 2026-09-09, base branch `releases/v0.138.0`, cut from `main` after `v0.137.0` merged as #1360 and tagged)

Sources: `docs/claude-findings/2026-09-09-regeneration-invalid-title-failures.md` (the diagnosis) and `docs/claude-plans/2026-09-09-generated-title-bound-and-failure-observability-plan.md` (the fix plan). **Read the findings first — Leg A looks like a nice-to-have until you have seen its §4.**

Theme: make what is *enforced* match what is *stated* — in the prompt contract, and in the backlog.

### ⚠️⚠️ A LIVE PRODUCTION DEFECT, DETERMINISTIC, WITH FOUR NOTES STUCK `FAILED` RIGHT NOW

Owner-reported 2026-09-09. Seven `LLM_INVALID_OUTPUT` failures across four notes, all `BOARD_EXAM_REVIEW`. **⚠️ Batch `6a7a8fa9` is a RETRY of the three that failed in `f5c690f5`, and all three failed identically — so this is DETERMINISTIC and retrying is not a workaround.**

**The probable cause is a bound the model is never told.** The title is validated at **12 WORDS** (`MAX_GENERATED_NOTE_TITLE_WORDS`, `OpenAiLlmStudyPackService:75`), while the published contract carries **no numeric bound at all** — schema `maxLength: 160` **characters**, plus prose "concise and specific". The same prompt file already templates `{MAX_ITEM_CHARS}` (3×) and `{MAX_WORDS}`. **⚠️ AND THE CODEBASE DOCUMENTS THIS EXACT ANTI-PATTERN AGAINST ITSELF:** `OpenAiLlmStudyPackService:2553-2561` records removing an unpublished word ceiling from *bullets* and instructs *"do not reintroduce a bound the model cannot see."* **The title kept its.**

**⚠️⚠️ BUT THE FAILING BRANCH IS INFERRED, NOT CONFIRMED — AND THAT IS WHY LEG A COMES FIRST.** `normalizeGeneratedNoteText` throws on *either* a blank value or a word count outside `1..12`, and **the log records neither the rejected title, its word count, nor which bound failed.** Four notes failed seven times and produced zero evidence of what was wrong. **⚠️ The source-title length correlation is NOT discriminating and must not be cited as support** — the FAILED titles average 8.5 words, well inside the cap.

### Planned Scope

1. **Leg A — log what was actually rejected.** When `normalizeGeneratedNoteText` rejects: the field, which bound failed (`blank` vs `wordCount`), the measured count, the limits, and **the offending value TRUNCATED** (owner decision, 2026-09-09). **⚠️ Independently shippable and ships even if Leg B slips** — without it the next occurrence is equally unresolvable. **This is the `v0.87.0` lesson one layer down:** that release added `failed_topic_reasons` because "which topic failed" without "why" had already cost two investigations.
2. **Leg B1 — publish the bound** (owner decision, 2026-09-09). Template a `{MAX_TITLE_WORDS}` line into `note-generation-developer.txt`, exactly as `{MAX_ITEM_CHARS}` and `{MAX_WORDS}` already are. Smallest change, output shape unchanged, contract made honest.
3. **Backlog Index verification pass over the 16 scope-eligible rows** — every row claiming OPEN or NOT SHIPPED, checked against real code or a read-only query, stale ones corrected.
4. **Extend the `v0.137.0` rule to cover SHIPPED-CODE claims, not just production state**, and index the two artifacts above.

### ⚠️ Why item 3 exists: three stale rows surfaced by accident in ONE day

`v0.137.0` added the rule that a claim about *production state* is a snapshot. **That rule does not reach a claim about shipped CODE, and the third failure was exactly that.**

| Claim | Reality |
|---|---|
| `V141`/`V142` never run | Ran 2026-09-08 |
| Profile-string write pending | Already applied |
| **Study Plan Builder reorder NOT SHIPPED** | **Shipped in `v0.96.0` (`185e0cc7`, 2026-08-29) — 41 releases ago** |

**⚠️ THE THIRD ONE WAS OFFERED TO THE OWNER AS A RELEASE CANDIDATE AT THE `v0.137.0` KICKOFF AND IS RECORDED IN THAT RELEASE'S NARRATIVE AS AN OPTION THEY CHOSE AGAINST.** Had it been picked, a Codex prompt would have been written to build something that already exists — the `Save order` button is at `study-plan-builder-page-client.tsx:2513`, and two of that row's three "verified traps" were also already addressed. **A stale Backlog row costs a whole release, not a paragraph**, because the Index is the input to every kickoff's scope decision.

### ⚠️ Anti-drift

- ❌ **Do NOT do B1 and B2 together.** Two bounds on one field is how this defect was created. **B1 is authoritative; B2 and B3 are not in scope.**
- ❌ **Do NOT increase `MAX_INVALID_OUTPUT_ATTEMPTS`.** The failure is deterministic — the retry already ran and failed identically. More retries buy nothing and cost an LLM call each.
- ❌ **Do NOT skip or soften title validation on the regeneration path only.** The generated title becomes the note body's **first line**; a divergent rule between first generation and regeneration is a new defect.
- ❌ **Do NOT rename or retitle the four failed notes to work around it.** `v0.120.0` established the typed title as canonical. Their titles are correct; the validator rejected the model's *output*.
- ❌ **Do NOT bundle the `OfficialChallengeQuizTemplateService` seed failures** (findings §7) — separate symptom, unproven relation, and folding it moves the verification tier.
- ❌ **Do NOT touch the `subject value='Education' … overly broad ai suggestion ignored` path** — it appeared 6× in the window and is a working guard reporting normal operation.
- ⚠️ **Nothing here may make `generateStudyPackFromExistingNoteAsync` public or route it through the `@Transactional` proxy** — both LLM calls run with no transaction and no JDBC connection held (`v0.112.0`), and that must survive.
- ❌ **NO `frontend/app/onboarding` work before the `2026-09-11` read** (62.4% baseline, cannot be re-run). **NO Learning Connections work** — `[CHECKPOINT — due 2026-09-19]`, denominator ONE.
- ⚠️ **The four failed notes are NOT repaired by this release.** They stay `FAILED` until re-run, and **that re-run is the OWNER's** — it is also the only real end-to-end confirmation, since the failing branch could not be confirmed from logs.

### ⚠️ Pre-declared guards — written so a fixture cannot pass under both the defect and the fix

- **Leg A:** assert the emitted payload **names the bound and the measured count**. ⚠️ A test asserting only that generation fails **passes under both the defect and the fix** — it already fails today.
- **Leg B1:** assert the **RENDERED** prompt contains the numeric bound. ⚠️ Not the template file — the placeholder is substituted at build time, and a test that greps the raw template passes even if substitution is broken.
- **Regression, all legs:** the four real titles from findings §1 must round-trip. ⚠️ **Use those exact strings, not invented ones** — they are the only known-failing inputs, and an invented "long title" fixture is a guess about a failure mode the logs never confirmed.
- ⚠️ **Reach the validator the way production does** — through the real response-parsing path. A fixture that hand-builds a `PromptGeneratedNote` skips `repairJsonEatenLatexCommands` and the whitespace normaliser, **either of which could be the branch that actually fired.**

### ⚠️ Size, and what the fold does to the verification tier

**The owner took both the title fix and the backlog pass in one release, after being told the repo's rule that release size is the biggest lever on verification cost. Recorded rather than smoothed over.** **The honest consequence here is mild, and only because the second half changes NO code:** items 3–4 are docs, so the code diff stays at two files with no migration and no endpoint. **Tier: one `advisor()` call on the code diff**, per the plan's §7, plus the rendered-prompt guard. **⚠️ It would NOT stay there if Leg B2 were taken** — that changes what is accepted into a note body on a shipped generation path and would need a scoped cold agent. B2 is explicitly out of scope.

**⚠️ The transport lesson does not apply: no new endpoint is added, so nothing here owes a real-request `MockMvc` test.**

### Routing

**CLAUDE CODE inline** for Legs A and B1 — two files, no migration, no endpoint, per the plan's §7. Items 3–4 are docs and are Claude Code by definition.

### Shipped

- **Leg A — every generated-note text rejection now names what failed.** `normalizeGeneratedNoteText` threw a bare `LLM_INVALID_OUTPUT` on either branch; it now emits `generated_note_text_rejected field=… bound=blank|wordCount words=… min=… max=… chars=… value="…"` before throwing, and the two branches are separated so the bound is reported rather than inferred. Applies to `title`, `overview` and `keyIdea` alike. **On the blank branch the RAW value is logged, not the normalized one** — what is diagnostic there is what arrived and collapsed to nothing, which a `null` cannot show.
- **Leg B1 — the title bound is published.** `note-generation-developer.txt` now states *"keep the title at or under `{MAX_TITLE_WORDS}` words"*, templated beside the existing `{MAX_ITEM_CHARS}` and `{MAX_WORDS}`. **The bound was NOT raised** — 12 words is unchanged; the model is simply told the number it is judged against.
- **Reused the class's existing `truncateForLog` rather than adding a second truncation rule.** The first attempt defined a duplicate helper, which failed the build; the existing one already backs thirteen other truncated-value logs and caps at `MAX_LOG_VALUE_LENGTH = 80` — comfortably above the 48–71 characters of the four titles that actually failed.
- **Corrected the `:68` comment that predicted this defect.** It said all three word bounds were unpublished and "surviving deliberately", ending with a standing instruction: *"if one starts rejecting valid content, publish the bound in the prompt rather than raising it."* **That prediction came true and the instruction was followed.** The comment now records which bound was published and why the other two deliberately were not — the instruction is evidence-gated (*"if ONE starts rejecting valid content"*), and neither `overview` nor `keyIdea` has produced a single observed rejection.
- **Corrected `docs/features/study-pack-generation.md`, which claimed the two title-rule blocks are "byte-identical today".** That stopped being true with Leg B1. The divergence is deliberate and one line: only the note path enforces a title word bound, so publishing one in `developer.txt` would state a rule nothing enforces. The doc now says so and warns against "restoring" byte-equality.
- **Four guards, each mutation-verified with the killing test named.** Deleting the published bound from the real prompt kills `noteGenerationPromptResourceDeclaresTheTitleWordPlaceholder`; breaking the substitution kills `noteGenerationPromptStatesTheTitleWordBound`; silencing the rejection log kills `rejectedGeneratedTitleLogsWhichBoundFailedAndTheMeasuredCount`. The fourth, `theFourTitlesThatFailedInProductionRoundTripThroughTheRealParsingPath`, uses the four real production titles rather than invented ones and drives the real response-parsing path.
- Backend suite: **2349 tests, 0 failures, 0 errors, 0 skipped**, PostgreSQL container included.

## v0.137.0 - Deploy Integrity

**Status: Released** (kicked off 2026-09-09, signed off 2026-09-09, base branch `releases/v0.137.0`, cut from `main` after `v0.136.0` merged as #1359. **⚠️ RE-SCOPED the same day — see below.** No feature or fix PRs: every commit is release-management or docs, plus one script.)

Theme: the deploy pipeline failed silently and the record of what was deployed was wrong. Make both true again.

### ⚠️⚠️ THIS RELEASE WAS SCOPED ON A FALSE PREMISE AND RE-SCOPED WITHIN THE HOUR — THE ORIGINAL IS RECORDED, NOT OVERWRITTEN

It opened as **Deploy and Read**, justified by *"`v0.132.0` through `v0.136.0` are merged and none is deployed"*, taken from `ROADMAP.md`. **The owner chose that scope over two feature candidates on the strength of it. Read-only production queries then disproved it:**

| Claim as written | Reality, read 2026-09-09 |
|---|---|
| `V141` has never been run | **RAN 2026-09-08T12:23:16Z**, `success = true`, on `v0.132.0`'s own auto-deploy |
| `V142` has never been run | **RAN 2026-09-08T15:47:36Z**, on `v0.133.0`'s |
| The profile-string write is still pending | **ALREADY APPLIED** — the read returns `0 old / 1 new` |
| Five releases are undeployed | **The BACKEND deployed automatically every time.** Only `v0.136.0`'s FRONTEND is missing |

`notifications` and `announcements` really are at zero rows, so the notification checkpoints genuinely remain unreadable. That half survived; the rest did not.

**⚠️ THE CHEAPEST DETECTOR EXISTED AND NOBODY USED IT: the `v0.133.0` row said `V142` HAS NOT BEEN RUN in its Gate cell while its own Status cell said the read was *"READ AND CLOSED 2026-09-09, one day after the deploy, exactly on its date"* — which is only possible if it HAD deployed.** The Status was updated when the read closed; the Gate was not. **A row that contradicts itself is free to detect and nobody read the two cells against each other.**

### ⚠️ The live defect this investigation actually found

**`v0.136.0` did not auto-deploy on EITHER platform, and the resulting version skew broke Your Impact in production.**

Render's config is correct and untouched (`autoDeploy: yes`, `autoDeployTrigger: commit`, `branch: main`) and every prior release fired in **2–3 seconds** with `trigger: new_commit`. For `4ee2c752` it never fired; the owner deployed manually 5.5 minutes later (`trigger: manual`). Vercel matches exactly — GitHub records a `vercel[bot]` Production deployment for every prior release merge and **ZERO** for this one. There are no repo-level webhooks and no CI workflows, so both platforms are GitHub App integrations consuming **the same push event**, and neither received it. GitHub declared no incident that day.

**The consequence, verified against the live commit rather than assumed:** Vercel's newest Production deployment is still `7f371e65` (`v0.135.0`) while Render runs `v0.136.0`. `v0.135.0`'s `api.ts:5990` calls `/creator-impact/me` with **no** parameters, from `public-profile-page-client.tsx:225` and `settings/page.tsx:281` — and `v0.136.0` made `impacted` **required**, so both calls now 400. The owner sees *"Could not load your impact."* with a Retry that can never succeed, and the Knowledge Impact digest toggle hidden in Settings. Both degrade rather than crash, which was deliberate, but the feature is dead until Vercel ships.

**⚠️ AND THE CHANGE WAS BREAKING IN BOTH DIRECTIONS — frontend-first would have 404'd on `/creator-impact/me/summary`. `v0.136.0` recorded no deploy-ordering requirement at all.**

### Planned Scope

1. **Restore the frontend deploy — OWNER ACTION.** Deploy `4ee2c752` (or later) on Vercel. Until then the skew above is live.
2. **A detector for a merge that produced no deploy.** Nothing in the repo or the process noticed; it was found only because the owner happened to look. The cheapest form is a signoff-time check that the deployed commit matches `main` on both platforms — decide detector shape before building anything.
3. **Two standing rules, written where they will be read.** (a) A release that removes, renames or makes-required an existing API form is **breaking in both directions** and must state its deploy ordering or that both sides ship together. (b) **A `ROADMAP.md` claim about PRODUCTION STATE is a snapshot, not a fact** — re-read it read-only before repeating it, and treat a row whose Gate and Status cells disagree as a defect in itself.
4. **The reads that are now live**, which the false premise had wrongly deferred: `2026-09-10` (`v0.72.0` proximal retention, `v0.134.0` `deploy + 1 day`) and `2026-09-11` (onboarding funnel, `v0.62.0` Knowledge Impact).

### ⚠️ Anti-drift

- ❌ **NO `frontend/app/onboarding` work before the `2026-09-11` read.** It measures signup-funnel completion against a **62.4%** baseline (234/375) that cannot be re-run.
- ❌ **NO Learning Connections work** — `[CHECKPOINT — due 2026-09-19]`, denominator ONE. Stage D2's connection-request notification is exactly the nudge that converts `PENDING` into `ACCEPTED`, the metric that checkpoint kills the arc on.
- ❌ **Do NOT change any analytics event, fire site or gating condition a live checkpoint reads.** `v0.136.0` moved and re-gated `KNOWLEDGE_IMPACT_DASHBOARD_VIEWED` two days before the `2026-09-11` read that depends on it; a second such change would corrupt another read.
- ❌ **Do NOT add a second entry point to `/impact`** before its own `[CHECKPOINT — due deploy + 30 days]` reads — adding one destroys the measurement.
- ❌ **Do NOT add CI infrastructure as a reflex.** The repo has **no** `.github/workflows/`, and item 2 is a detection problem, not automatically a CI problem. State the shape before building.
- ⚠️ **A quiet read is NOT a pass.** On rows with a denominator clause (`notifications` at zero, one curator, zero digest opt-ins), `no data` means **NOT YET MEASURABLE → RE-DATE**.
- ⚠️ **⚠️ THE PRODUCTION DATABASE IS READ-ONLY FOR CLAUDE, ALWAYS** — every diagnostic in this section came from `SELECT`s. The deploy is the owner's.

### ⚠️ What is actually deployed, as of 2026-09-09

- **Backend (Render):** `4ee2c752` = `v0.136.0`, live, deployed manually 12:04:31Z.
- **Frontend (Vercel):** `7f371e65` = `v0.135.0`. **One release behind, and that is the live defect.**
- **Database:** migrations current at **`V143`**, matching the repo's highest. Nothing pending.

### Scope completeness

| Planned item | Outcome |
|---|---|
| 1. Restore the frontend deploy | **✅ SHIPPED (owner).** Vercel Production now carries `4ee2c752` at 2026-09-09T14:00:53Z, `state=success`; Render and `main` agree. Skew resolved, verified read-only rather than assumed. |
| 2. Detector for a merge that produced no deploy | **✅ SHIPPED.** `scripts/check-deploys.sh`, wired into `/signoff`. Shape was decided before building, per the anti-drift, and CI was deliberately not introduced. |
| 3. Two standing rules | **✅ SHIPPED** in `CLAUDE.md` — the production-state-is-a-snapshot rule and the API-form-is-breaking-both-ways rule. |
| 4. The `2026-09-10` and `2026-09-11` reads | **❌ NOT SHIPPED — the dates had not arrived, and reading early is the failure those dates exist to prevent.** Nothing about them is blocked by this release; they are unchanged in the Backlog Index and land in the next cycle. Recorded here so a reader does not mistake the release's own scope list for work that silently vanished. |

### Known limitations

- **The Render half of `check-deploys.sh` has not been run against the live Render API.** No `RENDER_API_KEY` was available in the session that wrote it, so the request, auth and error paths are unexercised; only the response *parsing* is covered, by fixtures. **The first real run is the test** — if it fails, the fault is in the request/auth path, not the comparison. The Vercel half ran end-to-end against the live API and is confirmed working.
- **The detector is manual.** It runs when `/signoff` says to run it, so a release that never reaches signoff is not covered, and neither is a deploy that goes missing between releases. Making it automatic requires a `schedule:`-triggered workflow — **not `on: push`, which cannot observe the absence of the event that triggers it** — and that was deliberately deferred rather than introduce CI to a repo with no workflows.

### Routing

**CLAUDE CODE** for the doc corrections, the rules and the reads. Item 2 is re-routed once its shape is decided.

### Shipped

- Added `scripts/check-deploys.sh`, a detector for a merge that produced no deploy, and wired it into `/signoff` after the release PR merges. It compares `origin/main` against Vercel's newest successful Production deployment (read through GitHub's deployments API, so it needs no new secret) and Render's newest live deploy. **It tests for ABSENCE rather than failure**, because Render's `notifyOnFail` had nothing to fire on — nothing failed, nothing was queued — and **it exits 2 rather than 0 when it cannot check**, since "I could not look" reported as "all clear" is the same failure class it exists to catch.
- **Verified the detector against the defect itself rather than asserting it:** replayed today's 12:04 state (`main` at `4ee2c752`, Vercel newest `7f371e65`) through the comparison and confirmed it reports drift and exits 1. The Render response parser is covered by fixtures for the wrapped and unwrapped API shapes, a no-live-deploy list and an empty list.
- Added two standing rules to `CLAUDE.md`: a claim about production state is a snapshot that must be re-read before it is repeated (with internal Gate/Status contradiction named as the free detector), and removing/renaming/making-required an API form is breaking in both directions and owes a deploy-ordering statement.
- Corrected four false production-state claims in `ROADMAP.md` — the `V141`/`V142` gate cells and the profile-string write — each against the read-only query that disproves it, and recorded the self-contradicting row as the detector that was available and unused.

## v0.136.0 - Contribution Surface

**Status: Released** (kicked off 2026-09-09, signed off 2026-09-09, base branch `releases/v0.136.0`, cut from `main` after `v0.135.0` merged as #1356 and tagged `7f371e65`. Shipped as PRs #1357 and #1358.)

Source: `docs/claude-plans/your-impact-private-contribution-surface-stage1.md`.

Theme: make Your Impact a real destination — and stop it costing every Settings page load a 1,587-record read.

### ⚠️⚠️ THE HEADLINE IS A LIVE DEFECT, NOT THE IA MOVE

`frontend/app/settings/page.tsx:276-296` calls `getCreatorImpact()` in a page-load `Promise.all` and uses the result for **exactly one thing**:

```ts
setHasPublicNotes(Boolean(impact.value?.notes.length));
```

**To evaluate a boolean it pulls the entire creator-impact payload — 1,587 note records on the owner's account** — via three `IN (:noteIds)` grouped queries over a 1,587-element list plus an aggregate, **on the 256 MB Postgres behind the 20-connection pool that has already failed twice from unbounded reads** (2026-09-04, 2026-09-05, whose recorded lesson was *bound the work instead*). Payload precedent too: `docs/claude-findings/2026-09-01-prod-frontend-build-failure-public-notes-2mb.md`.

**⚠️ This is live on the owner's account today, and it is independent of whether the page ever moves.**

### ❌ THE BRIEF'S PERFORMANCE PREMISE IS FALSE — DO NOT REPEAT IT

**Impact was never part of the Public Profile payload.** It is a separate `GET /creator-impact/me`, fetched client-side in an effect gated on `isOwner`, and the controller takes **no `userId` parameter at all**. A public visitor triggers no impact query. **So moving the page saves Public Profile NOTHING** — the IA move is a discoverability fix (7 lifetime dashboard views), not a performance one. **Do not write a test asserting a Public Profile improvement that cannot occur.**

### Planned Scope — all 7 items, by owner decision

1. **Bound the impact query** — server-side sort by impact, pagination or a cap, and a filter for the zero-impact set.
2. **Lightweight summary endpoint** (`{distinctLearnersHelped, publicNoteCount}`) and **switch Settings to it**.
3. **Dedicated `/impact` route**, reusing `CreatorImpactService` — **no duplicated analytics logic**.
4. **Public Profile:** remove the full dashboard, add an owner-only `View impact →`.
5. **Per-note hierarchy:** impacted first, zero-impact collapsed, deterministic tie-break.
6. **Zero states** per the audit's F.2.
7. **Preserve mobile;** the collapse control must be keyboard-reachable and not hover-dependent.

### ⚠️ Anti-drift

- ❌ **NEVER add a `userId` parameter to the impact endpoint. Its ABSENCE is the privacy control** — `/impact?userId=…` must stay *unrepresentable*, not merely blocked.
- ❌ No public impact metrics: no rankings, leaderboards, top-contributor badges, follower counts, XP, points, levels, trophies or streaks.
- ❌ Do not merge Impact into Progress or into Learning Connections. **Progress = how I am learning. Your Impact = how my shared knowledge is helping other learners.**
- ❌ Do not add Impact to the permanent sidebar — a first-class page does not require first-class navigation, and the denominator is one.
- ❌ Do not expose learner identities, scores, weak concepts, or who copied/studied.
- ❌ Do not duplicate the analytics logic — move and reuse `CreatorImpactService`.
- ❌ Do not load full impact analytics from the app shell, notification polling, or Dashboard.
- ❌ **`/impact?note=<id>` is DEFERRED, not optional** — no shipped consumer needs it, and building it now creates an unused deep-link contract.
- ⚠️ **The tie-breaker is `noteId ASC` ALONE — NOT `copyCount DESC`.** `copyCount` is not in the ranking query, and it would promote a distribution metric into semantic ranking. `noteId` is immutable and unique: the only tie-break that guarantees stable pagination.
- ⚠️ **Label views as "page views".** `PUBLIC_NOTE_VIEWED` has 25,262 rows and **ZERO** with a `user_id` — all anonymous traffic — so views can never be deduplicated per person. Not taste; the metric cannot support it.
- ⚠️ **Do NOT put a prominent "Awaiting first study · 1,526" counter on the page.** It turns a contribution surface into a deficit scoreboard and would be the largest number on it. Show the impacted count; do not promote the zero-impact one.
- ✅ **Preserve:** the `PRIVATE TO YOU` labelling; the headline-dedup explanatory copy (**it is accurate — verified against the queries**); the self-copy exclusion; the `impactRequestIdRef` out-of-order guard; and `public-profile-page-client.test.tsx:277` (a visitor issues no impact request).
- ❌ No Learning Connections work — `[CHECKPOINT — due 2026-09-19]`, denominator ONE. No `frontend/app/onboarding` work — `[CHECKPOINT — due 2026-09-11]` is two days out.

### ⚠️ Denominator, and why it does NOT justify deferral here

**Exactly one account in production has any impact, and it is the owner's own ADMIN account** (1,587 public notes, 56 learners helped); three other accounts have 1–2 public notes and zero learners. **The denominator-based deferral used for note likes and Learning Connections does NOT transfer:** the single user is the owner, who is asking for the feature, and 56 learners helped is *measured* impact rather than a speculative event stream.

### ⚠️ Size and verification tier — the owner took both halves against the audit's advice

**The audit recommended splitting this into two releases** (items 1–2, then 3–7) precisely so R2 never consumes an unbounded endpoint, and because **7 items is well past the 3–4 band where verification stays at a single `advisor()` call.** The owner elected to take both halves together. **That is recorded rather than smoothed over, and the consequence is stated up front: verification is AT LEAST one scoped cold agent, and the tier must be re-decided if the diff grows** — this touches an owner-only privacy surface, and four of the last four releases each shipped a test that passed for a reason unrelated to its change.

**⚠️ Ordering still binds inside the release: bound the query (item 1) BEFORE the new page consumes it (item 3), so the new surface is never briefly worse than the old one.**

### Cross-reference to settle when this ships

`/impact` becomes the canonical `IMPACT_MILESTONE` destination. `attention-notifications-email-expansion-stage1.md` §15 currently points at `/progress` and must be updated — the tightening addendum already forbids contracting it to `/progress`.

### Routing

**CODEX** — a bounded/paginated endpoint, a new summary endpoint, a new route and a Public Profile change span backend service, controller, DTO and several frontend surfaces.

### Shipped

- Bounded creator-impact reads with required impacted/zero-impact pagination, stable database ordering, server-capped page sizes, page-sized views/copies aggregates, and section totals. Removed the parameterless full-payload response and its DTO.
- Added `GET /creator-impact/me/summary` with exactly `distinctLearnersHelped` and `publicNoteCount`. Settings now uses this fixed-size response and preserves its retryable fallback when the check fails.
- Added the authenticated top-level `/impact` destination. Its headline comes only from the summary endpoint; impacted notes load first, and zero-impact notes stay collapsed and unfetched until expanded.
- Replaced the owner Public Profile dashboard with a compact owner-only `View impact →` card. Visitors remain unchanged and issue no impact request.
- Added the two contribution zero states, retry handling for the page and expanded section, page-view labelling, mobile card layout, and an accessible keyboard-operable disclosure.
- Added regression coverage for bounded metric ID lists, the required `impacted` parameter, absent user identifiers, summary response shape, deterministic impact ordering, headline deduplication, structural self-copy exclusion, Settings endpoint selection, Public Profile privacy, and lazy zero-impact loading.
- **Repointed the Knowledge Impact digest email at `/impact`.** Its `View Your Impact` button linked to `/public/creator/{username}#your-impact-heading`, and that anchor now resolves to the owner-only link card rather than a dashboard — so the button promised the dashboard and would have delivered another link. Found by a surface sweep, not by the diff: `RetentionService` was not otherwise touched by this release, and the anchor id still exists, so it would have degraded silently in an email that cannot be corrected once sent.
- Gave the impacted-notes `Load more` a visible failure state and a retry. It had a `finally` with no `catch`, so a failed second page produced an unhandled rejection and no user-visible change; the owner's 61 impacted notes at a page size of 20 make that the first-visit path, not an edge case.
- Clamped paging inputs in `CreatorImpactService` (`Math.clamp`, repo convention per Sonar S6877) so a degenerate `size` or negative `page` cannot reach `PageRequest.of` and 500. The controller's `@Min` annotations are inert under `standaloneSetup`, so the guard now lives where it is actually exercised.
- Restored `@Transactional(readOnly = true)` on `CreatorImpactService`, removed during delivery. Behaviourally near-neutral — OSIV plus `DELAYED_ACQUISITION_AND_HOLD` (pinned by `ConnectionHandlingModeContractTest`) holds one connection per request either way — but it is a defensive annotation on the release whose subject is pool pressure.
- Stopped discarding the fresh section totals that every impact page response already carries. `totalImpacted` / `totalZeroImpact` were read once at initial load, so a note crossing from zero-impact to impacted while the page was open left `Load more` comparing against a stale total — the button kept requesting pages that came back empty and never resolved. Both loaders now refresh the pair from the response in hand, at no extra query cost. Found by the pre-signoff cold agent.
- Finished the `IMPACT_MILESTONE` cross-reference in the notification audit. Delivery updated Appendix A's milestone row; §15's deep-link matrix, §7.1's verified-routes table, and Appendix A's note-copy row still pointed at `/progress`.

## v0.135.0 - Update Signal

**Status: Released** (kicked off 2026-09-09, signed off 2026-09-09, base branch `releases/v0.135.0`, cut from `main` after `v0.134.0` merged as #1353 and tagged `eac429a4`. Shipped as PRs #1354 and #1355.)

Source: `docs/claude-plans/attention-notifications-email-expansion-stage1.md` (Stage D) **as corrected by `docs/claude-plans/attention-notifications-stage1-tightening-addendum.md` §2, which is the binding design.**

Theme: give the notification substrate its first real producer — when a curator publishes an Official Review Set update, the learners who adopted it are told.

### ⚠️⚠️ READ THE ADDENDUM'S §2, NOT THE AUDIT'S §17.2 — THE AUDIT'S DEDUP KEY IS A PERMANENT SUPPRESSION BUG

The audit proposes `REVIEW_SET_UPDATE:<adoptedCollectionId>`. Under the permanent unique index on `(recipient_user_id, dedup_key)` **that key can deliver exactly ONE notification per learner per set, ever.** `NotificationService.deliver` catches the constraint violation and **returns the OLD row** — no exception, no log line, publish reports success. `dismissed_at` is not in the index, so dismissing never frees the key, and retention only frees it after 90 days *and* a read/dismiss — so **a learner who never opens the bell is suppressed forever.**

**The corrected key is source-side:**

```
REVIEW_SET_UPDATE:<sourceCollectionId>:<lastUpdatePublishedAt as epochMilli>
```

`lastUpdatePublishedAt` already advances **only** when `unpublishedChanges` is true (`NoteCollectionService:754-760`), so **re-press idempotency is inherited for free** and no migration is needed.

### Planned Scope

1. **`NotificationType.REVIEW_SET_UPDATE` + `NotificationCategory.LEARNING_SYSTEM` (backend).** The first type with a real producer.
2. **The producer (backend).** Fires from the `publishReviewSetUpdate` **call site**, delivering to adopters of that source root, deep-linked to `/collections/{adoptedCollectionId}`.
3. **Episode suppression (backend).** A batch filter in the producer, between audience resolution and fan-out, dropping recipients who already hold an **undismissed** `REVIEW_SET_UPDATE` row for that source. **Settled by owner decision A2 — ships WITH the producer, not later.**
4. **Retire the transitional `ACTION_REQUIRED`.** Delete both the type and its producerless category;
   the settled taxonomy decision is recorded below.

### ⚠️ Anti-drift

- ❌ **NEVER fire on raw source drift.** Only `publishReviewSetUpdate`. The publication boundary exists to forbid exactly this.
- ⚠️ **THE TRIGGER IS THE CALL SITE, NOT THE STAMP.** `markReviewSetUpdatePublished` has **two** call sites — `publishReviewSetUpdate:758` and `publishInitialCurriculum:1849`. Wiring the producer to "the stamp advanced" would also fire on a set's first-ever publication.
- ❌ **NO COUNT IN THE COPY.** Under episode suppression the single open row is the learner's only signal across N publishes, so *"3 new notes"* is false by the second one. Generic copy only — this also preserves R10, the inbox's zero-N+1-by-construction property.
- ❌ **Do NOT call `deliver()` inside a transaction (R9).** It is deliberately non-transactional so a dedup-index violation cannot mark a whole fan-out rollback-only. `FanOutTransactionBoundaryTest` guards this — **do not delete it, and do not delete the open-in-view runtime test either; they cover different angles.**
- ❌ **Do NOT put suppression in `AnnouncementAudienceResolver`** — that class is editorial, never authorization, and a test guards it.
- ❌ **NO email** (decision D3; R1 unresolved — the 100/day cap is breached at 156–159 observed).
- ❌ No new endpoint unless the producer genuinely needs one; no admin surface; no preferences centre.
- ❌ **NO Learning Connections work** — `[CHECKPOINT — due 2026-09-19]` is ten days out and its kill criterion keys on `ACCEPTED`, denominator ONE.
- ✅ **Reuse `notificationFanOutExecutor`** (core 1 / max 2) — do not add a second executor or resize it (R5/R13).
- ✅ Dismiss must change no curriculum truth; applying an update stays authoritative in Review Set state.

### ⚠️ Corrections to carry — the audit's own numbers are stale

- **The audit says "≤30 recipients". `LET Comprehensive Review` now has 42 adopters** (ALE 30, PNLE 15, CPALE 8, Civil Engineering 1). D2's justification cited the old figure.
- **Exactly ONE update publish exists in the product's history** — 2026-09-09 at 01:50:47, on LET, forty seconds after the Education tagging run. Four of five public source roots have never published an update at all.
- **⚠️ That read WEAKENED the case for shipping suppression now, and the owner took the recommendation anyway.** Record it as a judgement call made with the counter-evidence in view, **not** as "suppression was obviously necessary."

### Settled owner decisions (do not re-litigate)

- **A1 — dismiss without applying → the next publish DOES re-notify.** Suppress-until-applied would reintroduce permanent silence through a different trigger.
- **A2 — episode suppression ships WITH the first producer.**

### Settled taxonomy decision

**This release deletes both transitional `ACTION_REQUIRED` values.** `REVIEW_SET_UPDATE` maps to
`LEARNING_SYSTEM`, so every shipped type and category has a real producer. A later connection-request
release can add its category together with its producer instead of preserving a producerless slot.

### ⚠️ Inherited checkpoint — `v0.134.0` is DEPLOYED and its clock has started

`V143` ran **2026-09-09 03:53:07**, so `[CHECKPOINT — due deploy + 1 day]` is **2026-09-10**. **Proximal (a) is already ANSWERED: `idx_notifications_inbox` is live in production with the exact expected definition.** Proximal (b) — `/actuator/metrics/announcement.fanout.duration` resolves — is still owed. **⚠️ Its distal tier is gated on a first announcement publish, and ZERO announcements have ever been published**, so a quiet read is *not yet measurable* → re-date, never a pass.

### Verification tier

**To be decided when the shape is known**, but the prior is **one scoped cold agent**: this adds a producer to a fan-out path that has never run in production, and the last two releases each shipped a delivered test that passed for a reason unrelated to its change. **Routing: CODEX** — new enum values, a producer, a batch suppression query and its tests span backend service, repository and entity layers.

### ⚠️ Audit note — Codex hit its session limit mid-delivery, and the completion gap was ONE test

**The delivery was further along than the interruption implied: it compiled, the full backend suite passed, and 7 of the prompt's 12 required tests were present — including the discriminating one.** Auditing against the prompt's list rather than against the delivery's own shape found the real state:

| Required test | Status |
|---|---|
| Dismiss → later publish → NEW row (**the discriminating test**) | ✅ delivered |
| Re-press idempotency; episode suppression | ✅ delivered |
| R9 through the real Spring-managed path | ✅ delivered, **plus the annotation guard extended to the new service and listener** |
| Read ≠ resolved; no-count copy; learner's-own deep link | ✅ delivered |
| Real `MockMvc` request | ✅ **already existed** from `v0.132.0` |
| Taxonomy + badge counter | ✅ delivered |
| First-ever publication notifies nobody (**two-writers trap**) | ✅ **already covered** — see the correction below |
| **Editing without publishing reaches nobody** | ❌ **MISSING — added by this audit** |

**⚠️ A CORRECTION WORTH RECORDING, BECAUSE THE FIRST READ WAS WRONG.** This audit initially judged the two-writers trap uncovered and wrote a duplicate test for it. It **is** covered: `NoteCollectionServiceTest.updateVisibility_publishesWhenEveryItemIsPublic` asserts `markReviewSetUpdatePublished` fires **and** `verifyNoInteractions(applicationEventPublisher)` — the stamp advances, no event does. **The grep missed it because the test is named for the visibility change, not for the private method it exercises.** The duplicate was removed. *Searching for a behaviour by the name of the method that implements it will keep missing tests named for the user action.*

**The one genuine gap — `curatorEditsWithoutPublishingReachNoAdopter` — is the anti-drift line this release leans on hardest** (*editing is not publishing*), and it covers a path nothing else did: items added with **no publish call at all**. The existing no-op test covers publishing with nothing to publish, which is a different thing.

**Two mutants run, both killed:**

| Mutant | Killed by |
|---|---|
| **Dedup key reverted to the audit's `REVIEW_SET_UPDATE:<adoptedCollectionId>`** — the permanent suppression bug | **`dismissingOneRevisionAllowsTheNextPublishedRevisionToCreateANewRow`** — the design's whole reason for existing, caught |
| `@Transactional` added to the new producer's `fanOut` (R9 on the new path) | `FanOutTransactionBoundaryTest.reviewSetUpdateFanOutIsNotTransactional` |

**Verification run:** backend 2,335 tests + the 102-query PostgreSQL harness against a real container; frontend 2,348 across 212 suites; `tsc --noEmit` clean; `npm run lint` 0 errors.

### ✅ Pre-signoff cold agent — ONE CLAIM REFUTED, TWO INERT TESTS FOUND, ALL FIXED

The scoped cold agent ran against the merged state and **refuted C6** while upholding the other seven.

**⚠️ C6 REFUTED — a curator is notified of their own publish.** `adopt()` carries **no owner guard**, so a curator can self-adopt their own PUBLIC Review Set; `findReviewSetUpdateRecipients` had no self-copy exclusion, so publishing an update then told them about their own action. **The fix was already sitting eight lines above in the same file:** `countAdoptionsByCollectionIds` excludes self-copies with `adoption.ownerUserId <> source.ownerUserId` for the adoption COUNT. The two queries now agree on what an adoption is, and `aCuratorWhoAdoptedTheirOwnSetIsNotNotifiedOfTheirOwnPublish` fails if they diverge again. Cosmetic in severity, real as a gap.

**⚠️⚠️ TWO TESTS PASSED FOR REASONS UNRELATED TO WHAT THEY CLAIMED — AND ONE OF THEM WAS WRITTEN BY THE AUDIT THAT WENT LOOKING FOR EXACTLY THIS SHAPE.** That is the finding worth keeping:

1. **`curatorEditsWithoutPublishingReachNoAdopter`** — added by this release's own audit to guard *"editing is not publishing"*. Its act phase inserted rows with `jdbcTemplate` and **called no service method**, so a producer wired to fire on a WRITE rather than on the publish call would have sailed straight through it. **The test that existed to catch the release's boldest anti-drift claim could not catch it.** Renamed to `unpublishedRowsAloneDispatchNoFanOut` — which is what it actually proves — and the real guard now lives on the real edit path as `NoteCollectionServiceTest.addItems_doesNotAnnounceAnUnpublishedEdit`, which drives `addItems` and asserts the publisher is untouched.
2. **The `taskTransactionActive` assertion** captured `isActualTransactionActive()` in a wrapper that runs **on the test thread after the publishing transaction already committed and unbound** — so it read `false` whether or not `fanOut` was transactional, and could not see the regression its own `.as(...)` named. The capture moved **inside `deliver`**, via a spy on the **Spring-managed** bean so the proxy is live. **Verified by mutation: `@Transactional` on `fanOut` now fails it, and previously did not.**

**Four mutants run across this release, all killed:**

| Mutant | Killed by |
|---|---|
| Dedup key reverted to the audit's `<adoptedCollectionId>` | `dismissingOneRevisionAllowsTheNextPublishedRevisionToCreateANewRow` |
| `@Transactional` on the producer's `fanOut` | `FanOutTransactionBoundaryTest` — and **now also** the corrected runtime assertion |
| **Self-copy exclusion removed** | **`aCuratorWhoAdoptedTheirOwnSetIsNotNotifiedOfTheirOwnPublish`** |

**Upheld and worth recording:** the `LIKE` prefix cannot collide across sources (UUIDs are fixed-length and contain no wildcards); a same-millisecond key collision additionally requires a dismiss and a lock-serialized round trip inside 1 ms; adopted CHILD collections point at their own child source, not the root, so a Goal adoption cannot multi-notify. **`ACTION_REQUIRED` has zero references repo-wide, and production still holds zero notification rows of any type — re-verified read-only at this audit, not inherited.**

**Known limitation, recorded not fixed:** no test exercises delivery on the real `ThreadPoolTaskExecutor` thread, and the mid-fan-out unique-index conflict is covered only with a mocked `NotificationService`, never against a real database for two recipients in one fan-out.

**⚠️ Method note: the branch was NOT switched under the agent this time** — the `v0.134.0` lesson held.


### Shipped

- Replaced the producerless `ACTION_REQUIRED` type/category pair with the actionable
  `REVIEW_SET_UPDATE` / `LEARNING_SYSTEM` pair, backed by the first real feature producer.
- Publishing real changes to an Official Review Set now emits a plain-value event and queues adopter
  delivery only after the locked publication transaction commits, reusing the unchanged bounded
  `notificationFanOutExecutor`.
- Added source-revision identity as
  `REVIEW_SET_UPDATE:<sourceCollectionId>:<persistedPublishedAtEpochMilli>`, fixed count-free copy and
  deep links to each learner's adopted collection.
- Added one-query adopter resolution and one-query undismissed-episode suppression. Dismissal closes an
  episode, allowing the next genuinely published revision to create a new notification row.
- Added real Spring-transaction, revision-key, suppression, dismissal, no-op retry, queue-rejection,
  copy, deep-link and badge coverage while preserving both existing R9 guards.

## v0.134.0 - Notification Foundations

**Status: Released** (kicked off 2026-09-09, signed off 2026-09-09, base branch `releases/v0.134.0`, cut from `main` after `v0.133.0` merged as #1349 and tagged. Shipped as PRs #1350, #1351, #1352.)

Source: `docs/claude-plans/attention-notifications-email-expansion-stage1.md` (Stage A audit, 2026-09-08, every claim `file:line`-anchored and backed by read-only production `SELECT`s).

Theme: harden the notification substrate's taxonomy and dedup identity **while `notifications` still has zero rows in production**, so the first real producer lands on a shape that can carry it.

### ⚠️⚠️ THIS RELEASE SHIPS NO USER-VISIBLE CHANGE, DELIBERATELY — DO NOT READ IT AS A STALLED RELEASE

Every item here is a mechanism change behind an inbox that has **never rendered a numeric badge in production and could not have**. `countActionableUnread` filters `type IN actionableTypes()`, which resolves to `{ACTION_REQUIRED}`, and **zero code paths produce that type** — `AnnouncementService.deliverOne` is the only caller of `NotificationService.deliver` and it always passes `ANNOUNCEMENT`.

**The entire argument for doing this now is the row count: `notifications` is EMPTY in production — RE-VERIFIED READ-ONLY AT THIS KICKOFF, 2026-09-09**, not carried over from the audit: `notifications` **0 rows**, `announcements` **0 rows**, **0** distinct dedup keys, **0** `ACTION_REQUIRED`. **⚠️ The re-read is not a formality — it is the `v0.133.0` precedent, where the precondition read found EIGHTEEN catalog programs against the migration's THREE seeds and a repo-only audit would have been unsound. One admin publish between the audit and the prompt would put up to 396 rows in the table and turn item 2's key-format change from free into a live reconciliation that is not scoped.** Items 1–3 are pure Java, item 5 is one JPQL predicate. **Every one of them becomes a data migration with a backfill and a reconciliation the day the first real notification lands.** That window closes permanently and silently — nothing will announce it.

### Planned scope — Stage B items 1–3 + 5 only, DDL-FREE

1. **Taxonomy split.** `NotificationType` becomes **producer-level identity**; a new `NotificationCategory` carries **badge policy**; `actionableTypes()` derives its set from the category rather than from a boolean on the type.
2. **Widen `dedupKey` to accept a String discriminator.** Today `dedupKey(type, entityId)` returns `type.name() + ":" + entityId` (`NotificationService:104-106`) — so every future `ACTION_REQUIRED` producer shares one key space and two producers holding the same entity UUID collide silently under `idx_notifications_recipient_dedup`.
3. **The API response carries `actionable`/`category`, and the frontend stops comparing to the literal `"ANNOUNCEMENT"`.** `notification-inbox.tsx:110,117` is the frontend's private mirror of `NotificationType.actionable`; it mis-counts the badge the moment a third type exists.
5. **Retention expires non-actionable unread rows.** `deleteReadOrDismissedBefore` deletes only rows with `read_at` or `dismissed_at` set, so **an unread row is immortal** — an `EVERYONE` announcement to 396 users leaves 396 permanent rows per announcement, forever. **90 days, reusing the existing `retention-days` constant — no second knob** (decision D7).

### ⚠️⚠️ SUPERSEDED 2026-09-09 — ITEM 4 IS BACK IN SCOPE, BECAUSE THE PREMISE BELOW EXPIRED

**`V141` AND `V142` BOTH RAN IN PRODUCTION ON 2026-09-08** — verified read-only against
`flyway_schema_history` at 2026-09-09 (V141 12:23, V142 15:47, both `success = true`). **The single
stated reason item 4 was deferred no longer holds:** the queue is clean, `V142` is the tip, and a
`V143` is now an isolated one-statement migration against a table that is still empty (0 rows,
re-verified the same day).

**⚠️ The owner elected to complete ALL of Stage B** — items **4, 6 and 7** ship as PART 2, after
Part 1 merged as **PR #1350**. **So the DDL-free constraint below applied to Part 1 ONLY and is now
lifted.** It is kept verbatim rather than deleted because it records *why* the split happened, and
because the reasoning — do not add a migration to an unrun queue — is correct and will apply again.

**⚠️ The verification tier is RE-DECIDED for Part 2 — see the Verification tier section.**

### ⚠️ Item 4 (the `idx_notifications_inbox` index) WAS EXPLICITLY OUT OF PART 1, AND SO WAS EVERY DDL STATEMENT

Items 1–3 need no DDL — `type` is already `VARCHAR(64)` with `EnumType.STRING`, `dedup_key` is already `VARCHAR(255)` (`V139__notifications.sql:4-5`) — and item 5 is a predicate change. Item 4 would be the **only** reason this release carries a migration, and **`V141` and `V142` are both still UNRUN in production**. Adding a third migration to an unrun queue to fix a sort the audit measured as immaterial at 0 rows is a bad trade. **Let the index ride the next migration that exists for another reason.**

**⚠️ If a diff in this release adds a migration, the scope has drifted.**

### ⚠️ THE VERIFICATION TRAP: "behaviour must be identical after items 1–3" IS THE `v0.116.0`/`v0.117.0` SILENT-NO-OP SHAPE

`actionableTypes() == {ACTION_REQUIRED}` passes **before and after** the change, so a green run on it is evidence about nothing. **At least one assertion must be one that CANNOT pass against `main`** — the category **partition** test is the natural one, because no category enum exists today.

Stage B's verification list (**not** the audit's "Verification the first producer owes" section — that is a **Stage D** list and seven of its ten items presuppose a Review Set update producer that is not shipping here):

- `actionableTypes()` still resolves to exactly `{ACTION_REQUIRED}` — the regression guard for the derivation change.
- **Badge-eligible and retention-expirable PARTITION the categories** — one flag, two derived sets, with a test asserting the partition. Two hand-maintained lists is how they drift. ⚠️ **This is the assertion that cannot pass before the change.**
- A **non-actionable** unread row past 90 days is deleted; an **actionable** unread row past 90 days is **not**.
- The `GET /notifications` JSON actually carries `actionable`/`category` — extend `NotificationControllerTest`'s existing **real request** (`.contentType(MediaType.APPLICATION_JSON)`), never a direct handler call (`v0.119.0`).
- The frontend badge decrement reads the new field. **The existing tests asserting the `"ANNOUNCEMENT"` literal must MOVE WITH the change** — left as they are they pass for the wrong reason.

### ⚠️ `docs/features/notifications.md` IS A DELIVERABLE OF THIS RELEASE, NOT A SIGNOFF SCRAMBLE

All four items change behaviour that file **currently documents as true**, and it must move in the same PR — this is the failure CLAUDE.md flags hardest, and it has cost three consecutive releases:

| Line | Claim that becomes false | Item |
|---|---|---|
| `:24` | `dedup_key` is built by `NotificationService.dedupKey(type, entityId)` | 2 |
| `:35` | *"`NotificationType` carries an `actionable` flag, and `actionableTypes()` derives the set from it"* | 1 |
| `:166-167` | the announcement id is passed as **both** arguments to `dedupKey` | 2 |
| `:252-256` | retention deletes read/dismissed rows only, and *"Unread AND UNDISMISSED actionable notifications are RETAINED regardless of age"* | 5 |
| inbox response shape | must gain `actionable`/`category` | 3 |

**⚠️ `:252-256` is the sharp one: item 5 makes that sentence true only for the ACTIONABLE half.** Non-actionable unread rows start expiring at 90 days, and the doc currently states the opposite without qualification.

**✅ One correction to that file was made AT KICKOFF, because it was stale independently of this release:** its *"Stage 6 — blocked on the §8 drift-signature dedup decision"* line pointed at a design `v0.132.0` already ruled must **not** be implemented. Corrected there and in the superseded Backlog row, which read the same way.

### Anti-drift

- ❌ **NO new enum values without a producer.** `NotificationType` keeps **exactly** its two current values. Adding `REVIEW_SET_UPDATE` / `CONNECTION_REQUEST` / `NOTE_SHARED` now recreates the exact defect this audit opens with — a type with zero producers whose only tests hand-build a state no code path reaches.
- ⚠️ **`ACTION_REQUIRED` survives as an explicitly TRANSITIONAL placeholder for BACKWARD-COMPATIBLE TAXONOMY TRANSITION.** **Stage D replaces it** with `REVIEW_SET_UPDATE`. **Say this in the enum's javadoc**, or a future session reads it as permanent and builds on it. **⚠️ AMENDED 2026-09-09 by the owner's tightening addendum: do NOT justify it as "retained so badge behaviour stays exercised" — a domain-model value is not justified by the tests it keeps alive. Tests exercise the production model; they do not determine it.**
- ❌ **`NotificationCategory` stays DERIVED IN JAVA, never a stored column.** A stored category is a second source of truth that can disagree with the type, and no query needs to filter on it independently — `countActionableUnread` already takes its set as a parameter.
- ❌ **No DDL, no migration, no index** (see above).
- ❌ **Do not call `NotificationService.deliver()` from inside a transaction.** `deliver()` is deliberately non-transactional so a `DataIntegrityViolationException` on the dedup index does not mark an entire fan-out rollback-only. Item 2 edits the key that catch depends on.
- ❌ No `exists` check before a notification insert — the unique index is the mechanism.
- ✅ **`findVisibleInbox` and `countActionableUnread` predicates change TOGETHER.** The `dismissed_at IS NULL` leg on the count was a `v0.130.0` pressure-test fix and the repository javadoc records why.
- ✅ `read_at` = awareness, `dismissed_at` = inbox visibility. **No `resolved` column.**
- ✅ Opening the panel marks nothing read; poll the count endpoint only, never more often than 60 s; never render a literal `0` badge.
- ❌ **No Stage D producer, no Stage E email work, no executor change (item 6), no metrics (item 7).** Items 6 and 7 have **no closing window** — they cost the same in six months. That is the whole reason they are not here and item 1–3+5 are.
- ❌ **No Learning Connections work** — `[CHECKPOINT — due 2026-09-19]` is ten days out and its kill criterion keys on `ACCEPTED`, denominator **ONE**.
- ❌ **No `frontend/app/onboarding` work** — `[CHECKPOINT — due 2026-09-11]` is two days out.
- ❌ `AnnouncementAudienceResolver` must not touch `FeatureGateService` — editorial, never authorization.

### ⚠️ One thing to record while it is still true

**Item 2's key-format widening is free ONLY because there are no existing keys.** No stored dedup key has ever been written in production, so no format is a contract anyone can rely on. After the first producer ships, changing this format means reconciling live rows.

### ✅ Pre-signoff cold agent — RAN 2026-09-09, ALL EIGHT CLAIMS UPHELD, ONE GAP CLOSED

The scoped cold agent this release was re-tiered to **was run rather than waived**, framed as
falsification against eight named claims plus the two structural questions. It read the real code and
**re-ran the suites itself** rather than trusting this session's summary. **No claim was refuted** —
R9, the detached-entity hand-off, `V143`'s predicate match, the retention split, the contract removal,
the no-new-producer rule and the end-to-end badge all held.

**⚠️ IT FOUND ONE REAL GAP, AND IT IS THE KIND ONLY A COLD READER FINDS: the executor WIRING was
proven by nothing.** Every test in `AnnouncementServiceIntegrationTest` builds the service by hand
with a test-double executor, and `AppConfigTest` calls `new AppConfig()` directly — so neither proves
the **Spring-managed** service receives the **Spring-managed** fan-out executor. Context-load success
proves only that *some* `TaskExecutor` resolved.

**That gap was live, not theoretical.** Re-qualifying the constructor to `analyticsTaskExecutor`
**loads the context and passes every other test in the class**, while quietly putting announcement
fan-out on the pool that persists analytics — doubling pressure on the 20 connections production has
already exhausted twice (R5/R13). Closed with
`theSpringManagedServiceReceivesTheDedicatedFanOutExecutorAndNotTheAnalyticsPool`, which asserts bean
identity and thread-name prefix on the Spring-managed instance. It fails under that mutant.

**⚠️ It also credited a test this session had NOT: `aRetriedFanOutStillWorksWithAnOpenSessionInViewEntityManagerBoundToTheThread`**
binds an `EntityManagerHolder` exactly as `OpenEntityManagerInViewInterceptor` does and asserts the
persistence context is still readable after three caught constraint violations. That is the R9 angle
the reflection guard cannot see, and the two are complementary rather than redundant — worth recording
so neither is later deleted as duplicative.

**⚠️ Method note, recorded against the next time: this session switched the working branch while the
agent was running**, and the agent reported a file "changing under it" mid-review. It re-read and
self-corrected, and its test runs post-date the switch, so the conclusions stand — **but do not check
out another branch under a running cold agent.**

**Part 2 mutants — three run, three killed:**

| Mutant | Killed by |
|---|---|
| Fan-out reverted to synchronous | three async/rejection tests |
| `@Transactional` added to `fanOut` (R9) | `FanOutTransactionBoundaryTest` — *added by this audit* |
| **Fan-out re-qualified to `analyticsTaskExecutor`** | **`theSpringManagedServiceReceivesTheDedicatedFanOutExecutor…` — *added after the cold agent; it loaded the context and passed everything else*** |

### ⚠️ R1 IS A LIVE PRODUCTION FINDING THIS RELEASE DOES NOT FIX — recorded so it is not lost

**The email daily cap is already breached.** Configured `EMAIL_DAILY_LIMIT` is **100** with a **40** reserve; observed production peaks are **156, 158, 157, 156** sends/day over the last 90. The budget gates **`INACTIVITY` only** — weak-concept, weekly-summary, due-concepts and knowledge-impact dispatches are all unbudgeted, so the "100/day limit" describes one of five channels. **This blocks every future email producer (Stage E) and is indexed in `ROADMAP.md`'s Backlog Index, not scoped here.**

### ⚠️⚠️ Verification tier — RE-DECIDED 2026-09-09 FOR PART 2: ONE SCOPED COLD AGENT

**Part 1 was correctly tiered at a single `advisor()` call and shipped that way.** Part 2 changes the
shape, and `CLAUDE.md`'s gate now fires on **three** independent triggers rather than one:

1. **⚠️ Delivery introduced a defect this session that the session then fixed** — the measured
   blind-spot signal, and the gate names it explicitly. Part 1's frontend tests passed against the
   OLD implementation, and a backend mutant survived. That is not a hypothetical.
2. **The bug class is one the gate says is inherently hard to reason about serially** — async
   ordering. Fan-out moves off the request thread, and R9 (transaction poisoning) is an invariant that
   a green suite will not notice being broken.
3. **A second change touches the same shared methods** — `AnnouncementService.publish` / `fanOut` and
   `NotificationService.deliver`, both already edited by Part 1.

**Frame it as FALSIFICATION, not open-ended audit:** hand it a tight file list and the specific claims
this session made, and ask it to disprove each. `model: "sonnet"` is enough for claim-checking.

**⚠️ The claim most worth attacking: *"fan-out still runs outside any transaction."*** That is R9, it
is what keeps a single duplicate key from taking down an entire fan-out, and it is invisible to a
passing test suite.

**Superseded rationale, kept as the record:** *A single `advisor()` call.* No authorization or privacy boundary moves, no money/quota/production-data semantics change, and `notifications` has **zero rows** so item 5 has nothing to delete in production. Declared at kickoff so signoff does not re-derive it — **but re-decide if the shape changes**, per `v0.133.0`, which was tiered at one `advisor()` call, gained a write endpoint, ran one cold agent, and had **three of seven named claims refuted**.

**Routing: CODEX** — backend enum + service + JPQL + response DTO, plus the frontend component. Multi-system, so a prompt comes first. **Call `advisor()` before writing that prompt.**

### Shipped

- Added `NotificationCategory` as the single owner of numeric-badge policy, with badge-eligible and
  retention-expirable category sets derived as complements from one flag. `NotificationType` remains
  the two-value producer identity and now delegates policy to its category.
- Widened notification dedup discriminators from UUID to string while preserving the existing
  `ANNOUNCEMENT:<uuid>` keys and the unique-index catch-and-reread delivery contract.
- Added the server-derived `actionable` field to inbox responses and moved the frontend badge update
  and rollback branches to that field.
- Extended the existing 90-day cleanup to expire unread non-actionable rows while retaining unread
  actionable rows indefinitely; no new retention setting was added.
- Added category-partition, retention-direction, real-response-shape, dedup-format and frontend badge
  behavior coverage. Part 1 added no producer, notification type, endpoint, migration or index.
- Added V143's partial inbox index on recipient and descending creation time for visible notifications,
  closing the unindexed inbox sort now that the production migration queue is clear.
- Moved announcement delivery to the bounded `notificationFanOutExecutor` (core 1 / max 2). Publish now
  resolves the audience synchronously, reports queue acceptance immediately, and preserves retry/top-up
  semantics through the existing unique dedup index.
- Replaced synchronous publish delivery totals with `recipientCount` / `queued` and updated the admin
  feedback to describe background delivery or a recoverable queue rejection accurately.
- Added fresh-delivery, dedup-conflict, fan-out-duration and fan-out-rejection meters so asynchronous
  delivery remains observable without a per-user reporting surface.

### ⚠️ Audit finding — the frontend tests as delivered passed for the wrong reason, and mutation testing is what caught it

**Reverting `notification-inbox.tsx` to its exact pre-release implementation (`type !== "ANNOUNCEMENT"`)
left all 18 frontend tests GREEN.** Every fixture set `actionable` to *agree* with `type`, so the old
branch and the new one returned the same answer for all of them — the suite could not distinguish the
change it existed to verify. **⚠️ This is the `v0.116.0`/`v0.117.0` silent-no-op shape arriving from a
new direction: tests WERE added, and they still proved nothing about the change.**

Fixed by adding the one fixture where the two **disagree** — a non-actionable type whose name is not
`"ANNOUNCEMENT"` (`IMPACT_MILESTONE`), which is exactly the case the taxonomy split exists to fix and
the case the old code got wrong. That test fails against the old implementation and passes against the
new one.

**Four mutants were run and all four were killed, each by a named test:**

| Mutant | Killed by |
|---|---|
| `retentionExpirableCategories()` stops filtering (partition broken) | `NotificationCategoryTest.badgeEligibleAndRetentionExpirableCategoriesPartitionEveryCategory` |
| Every type made retention-expirable (unread actionable rows deleted) | `NotificationServiceIntegrationTest.retentionDeletesUnreadNonActionableAndReadRowsButKeepsUnreadActionableRowsPastTheWindow` |
| Badge branch never fires | `decrements the badge when an actionable notification is marked read` + `reverts the optimistic unread delta when marking read fails` |
| **Component reverted to the old `"ANNOUNCEMENT"` literal** | **`does not change the badge for a non-actionable type that is not ANNOUNCEMENT`** — *added by this audit; nothing killed this mutant before* |
| `retentionExpirableTypes()` returns the wrong side of the split | `NotificationCategoryTest.retentionExpirableTypesAreTheComplementOfActionableTypes` — *added by this audit* |

**⚠️ ONE MUTANT SURVIVES, KNOWINGLY, AND IT IS RECORDED RATHER THAN PAPERED OVER.** Hardcoding
`NotificationType.isActionable()` to `this == ACTION_REQUIRED` — bypassing the category delegation
entirely — **passes the whole suite.** That is not a fixable gap at this size: with two types mapped
one-to-one onto two like-named categories, delegation and hardcoding are **observationally identical**,
and no test can separate them without a third type, which this release forbids.

What was added instead is the guard that fires when it *starts* to matter: `everyTypeDerivesItsActionabilityFromItsCategory`
enumerates `values()`, so a third type that is misclassified fails immediately. A `category()` accessor
was added to make that invariant assertable. **The javadoc on that test states outright that it cannot
discriminate today**, so a later reader does not credit it with more than it proves.

### ⚠️ Part 2 audit finding — the R9 guard could not fail, and only mutation revealed it

**R9 is the invariant this release rests on:** `deliver()` catches the unique-index
`DataIntegrityViolationException`; under an ambient transaction that marks the whole transaction
rollback-only, so **one duplicate recipient would take an entire fan-out down**. Part 2 shipped a test
asserting `TransactionSynchronizationManager.isActualTransactionActive()` is false during delivery,
which reads exactly like the guard for it.

**It is not one. Adding `@Transactional` to `fanOut` left all 20 of that class's tests GREEN.** The
test constructs `AnnouncementService` with `new`, so there is **no Spring AOP proxy and the annotation
is inert** — the fixture cannot express the state it claims to forbid. **⚠️ This is the repo's
recurring "the guard must reach its subject the way production does" failure arriving from the
opposite end**, and it is the second release running where a delivered test passed for a reason
unrelated to the change.

Closed with `FanOutTransactionBoundaryTest`, a reflection check over the declared annotations on
`publish`, `fanOut`, `deliver` and both classes. It **cannot** be fooled by proxy absence, because the
annotation is exactly what production reads. It fails under the mutant.

**Also removed:** the single-argument `fanOut(AnnouncementEntity)` overload, left with **no callers in
main or test** once `publish` began resolving the audience itself — dead public API on a service whose
transaction boundary is load-bearing.

**Part 2 mutants — two run, both killed after the fix:**

| Mutant | Killed by |
|---|---|
| Fan-out reverted to synchronous | `publishReturnsAfterQueueAcceptanceBeforeAnyNotificationIsDelivered` + `rejectedDispatchReturnsZeroAndRepublish...` + `executorTaskRunsFanOutWithoutAnAmbientTransaction` |
| **`@Transactional` added to `fanOut` (R9 violated)** | **`FanOutTransactionBoundaryTest.announcementPublishAndFanOutAreNotTransactional` — *added by this audit; the whole suite passed before it*** |

**Verification run:** backend 2,312 tests + the 101-query PostgreSQL native harness against a real
container; frontend 2,346 tests across 211 suites; `tsc --noEmit` clean; `npm run lint` 0 errors.

## v0.133.0 - Education Family

**Status: Released** (kicked off 2026-09-08, signed off 2026-09-08, base branch `releases/v0.133.0`, cut from `main` after `v0.132.0` merged as #1347 and tagged)

Source: `docs/claude-plans/program-family-generalization-and-education-family.md` (audit complete 2026-09-08, every claim `file:line`-anchored).

### ⚠️⚠️ THE BRIEF'S CENTRAL PREMISE IS FALSE, AND THAT IS THE MOST IMPORTANT THING IN THIS SECTION

**There is no Engineering-specific authoring shortcut, so there is no generalization to build.** The button at `applicable-programs-combobox.tsx:290` interpolates the family name and count, and the families are derived **dynamically from the catalog** (`:88-111`) by mapping each program's `programFamilyId`/`programFamilyName`. **There is no Engineering literal anywhere in it.** It reads *"Add all 18 Engineering programs"* only because **Engineering is the only family that has members**.

**So this release is a DATA change plus one admin form field. Seed the Education family and the existing UI renders *"Add all 8 Education programs"* with no code change.** §6 of the brief is already satisfied too — all four authoring surfaces share that one component.

**⚠️ DO NOT MODIFY THE APPLICABLE-PROGRAMS COMBOBOX. If a diff touches it, the scope has drifted.**

### The two gaps the audit named — ⚠️ ONLY ONE OF THEM IS REAL (see below)

| Gap | Detail |
|---|---|
| Families can only be created by migration | Zero `ProgramFamilyEntity` construction or `save` anywhere in the backend |
| The admin UI cannot assign a family | `POST /course-programs` **already accepts and validates** `programFamilyId` (`CourseProgramCatalogService:54-56`, `UnknownProgramFamilyException`) — `app/admin/course-programs/page.tsx` just exposes no field for it |

### Planned scope

1. **One migration** — insert the `Education` family; assign the **existing** `Education` program row to it; **RENAME** `Special Needs Education – Generalist` → `Special Needs Education` keeping its `id`; insert the remaining programs from the audit's §4 (Elementary Education, Secondary Education, Early Childhood Education, Technical-Vocational Teacher Education, Physical Education, Teacher Certification).
2. **Admin family selector on create** — the endpoint already supports it. **This is what stops the release recurring**: without it, every future family member is another migration.
3. **Copy polish** on the Course/Program and Domain Context helper text. **⚠️ Drop resolver mechanics from it** — keep the conceptual separation, do not explain the backend.

### ⚠️⚠️ THE MIGRATION SEED IS NOT THE LIVE CATALOG — THE READ-ONLY AUDIT IS A PRECONDITION, NOT A FORMALITY

`V106` seeds **three** Engineering programs; production reportedly has **18**, and no later migration inserts any. **The catalog has been extended through `POST /course-programs` in production, so a duplicate audit from the repo alone is UNSOUND.** Run `docs/claude-plans/v0.133.0-education-family-precondition-read.sql` (read-only) **before writing the migration**. An inserted duplicate is visible to every curator immediately and is awkward to withdraw once Notes reference it.

**⚠️ `GET /course-programs/similar?name=` already exists** (ADMIN-only) — the duplicate check the brief asks for is already built; the admin create flow should use it per name.

### ⚠️ What the rename touches

**Safe:** `note_course_program` joins by `course_program_id` (`V107:4`), so every Note keeps its link through a rename — the row keeps its `id`, only `name` changes.

**⚠️ NOT safe automatically: five free-text `course_program` columns** (`notes`, `note_collections`, `users`, `bulk_generation_result`, `official_study_plan_wishlist` — the last has `normalized_course_program` too) may hold the literal old string and would silently keep it. No repository method resolves a catalog entry by name, so nothing breaks — but a stale string stops matching the catalog, which affects discovery and wishlist normalization. **The precondition read counts these.**

### ✅ PRECONDITION READ RUN 2026-09-08 — RESULTS, AND ONE CONDITION NOT MET

Run read-only against `notelib-db-prod`. **The audit's central warning is now VERIFIED, not assumed: `program_families` shows Engineering with EIGHTEEN programs against `V106`'s THREE seeds**, so the catalog was indeed extended through the API and a repo-only duplicate audit would have been unsound.

| Check | Result |
|---|---|
| Education-adjacent catalog rows | **Only two** — `Education` (`exam_goal_slug='let'`, no family) and `Special Needs Education – Generalist` (no slug, no family) |
| Families | **One** — `Engineering`, 18 programs. `Education` does not exist as a family |
| Collisions with the six proposed inserts | **None.** Elementary, Secondary, Early Childhood, Technical-Vocational Teacher, Physical Education and Teacher Certification are all clear |
| Notes linked to the rename target via `note_course_program` | **ZERO** |
| Free-text `course_program` hits | **ONE** — `users.course_program`, exactly `Special Needs Education – Generalist` (36 chars, en dash) |

**⚠️ OWNER DECISION 1'S CONDITION IS NOT MET.** The rename was settled *conditional on zero free-text hits*; there is one. **⚠️ NOTHING BREAKS** — `users.course_program` is consumed by `StudyPackGenerationContextResolver` as **free text**, never resolved against the catalog by name — but that one account's profile program would stop corresponding to a catalog entry. **Handed over as an owner-run write in `docs/claude-plans/v0.133.0-owner-profile-string-update.sql`** with the expected row count and before/after verification. Three options are stated there; the recommendation is to run it **in the same maintenance step as the migration, not before it**.

**⚠️ CONSEQUENCE FOR THE RENAME GUARD TEST: its production denominator is ZERO.** No Note is linked to the row being renamed, so the guard is a purely synthetic structural test. **Write it anyway — it is the regression guard for the migration — but do NOT record it as evidence that real data survived**, because there is no real data to survive.

**✅ DECISIONS 2 AND 3 SETTLED 2026-09-08 (owner):** new Education programs carry `exam_goal_slug = 'let'` **only where learners genuinely sit the LET**, never as a family proxy; and **the admin family selector ships in this release.**

### Owner decisions

1. ~~Reuse or rename `Special Needs Education – Generalist`?~~ **SETTLED 2026-09-08: RENAME**, keeping the row's `id`. **⚠️ THE CONDITION WAS NOT MET — the read returned ONE hit in `users.course_program`.** The catalog rename still proceeds; the one stale profile string is handed over as an owner-run write (`docs/claude-plans/v0.133.0-owner-profile-string-update.sql`), and option (c) there reopens this decision if the owner prefers.
2. **✅ SETTLED 2026-09-08 — `exam_goal_slug = 'let'` only where learners genuinely sit the LET.** Original recommendation, accepted: **only where learners genuinely sit the LET**; leave NULL otherwise rather than making the exam goal a family proxy. `Education` already carries `'let'`.
3. **✅ SETTLED 2026-09-08 — the admin family selector SHIPS IN THIS RELEASE.**

### Verification tier

**A single `advisor()` call**, per the audit's §9 — a data migration plus one admin form field. No permission substrate, no cross-user read, no money semantics, no learner-facing behaviour change. **⚠️ But it writes to a production catalog table, so the precondition read is the gate.** **⚠️ THE `v0.131.0`/`v0.132.0` LESSON STILL APPLIES: decide the tier from the SHAPE of the change, and re-decide if the shape changes** — `v0.132.0` was tiered at one cold agent, ran two, and they found four blocking defects including one that silently emptied a learner's adopted Goal.

### ⚠️ THE AUDIT'S SECOND "REAL GAP" IS ALSO FALSE — THE ADMIN FAMILY SELECTOR ALREADY SHIPPED

The audit named two real gaps. **One of them is not real.** It claimed *"the admin UI cannot assign a family — `frontend/app/admin/course-programs/page.tsx` exposes no family field."* That is literally true of `page.tsx`, which is a 40-line shell — **but the field lives in `admin-course-program-catalog-section.tsx`, which that page renders.** It has a `<select id="catalog-program-family">` listing every family derived from the catalog, `create()` sends `programFamilyId`, the table shows a Family column, and its helper text already says assigning a family makes the program participate in that family's expansion.

**It shipped 2026-08-11 in `9e77f412`, tagged `v0.100.0`** — verified as an ancestor of `main`, not merely present in a working tree.

**⚠️ THIS IS THE SAME ERROR CLASS THE AUDIT ITSELF CAUGHT IN THE BRIEF: concluding a capability is missing by reading the wrong file.** Both times the mistake was to check a shell rather than the component doing the work. **So step 3 of the implementation plan is NOT BUILT HERE — it was already done**, and the release shrinks accordingly. The other gap in that table — *families can only be created by migration* — is real, and remains the deferred admin create-family surface.

**⚠️ Do NOT re-add a family selector to the admin page. If a diff adds one, it is a duplicate.**

### Shipped

- **`V142__education_program_family.sql`** — seeds the `Education` family, assigns the EXISTING `Education` program to it, **renames `Special Needs Education – Generalist` to `Special Needs Education` keeping its `id`**, and adds Elementary, Secondary, Early Childhood, Technical-Vocational Teacher and Physical Education plus `Teacher Certification`. **The migration IS the feature** — the combobox is untouched and now renders *"Add all 8 Education programs"* on its own. **⚠️ The ID range was verified against production rather than assumed:** 44 programs exist, exactly 21 in `V106`'s seed range with `...021` highest, so `...022`+ cannot collide.
- **⚠️ The renamed row also gains `exam_goal_slug = 'let'` — a user-visible change beyond a rename, flagged rather than buried.** `findNamesByExamGoalSlug` returns a `List`, so the field is one-to-many by design; leaving this row NULL would have made seven of eight family members LET-discoverable and one silently not. It now appears under the LET exam goal on the public endpoint.
- **Helper-text copy polish.** The Applicable Programs hint no longer explains the resolver (*"only a single program can inform the writing domain, and Domain Context overrides it"*) — true, but backend mechanics a curator cannot act on, and it invited the reading that picking one program is how you steer the writing. It now states the conceptual separation and points at program families.
- **Three multi-family combobox tests, guarding a bug class that was previously invisible.** Every existing fixture held exactly ONE family, because until `V142` production did too — so a component that ignored which family was clicked and expanded them all would have shipped green. **Mutation-verified with an isolating mutant that typechecks and is identical to correct behaviour under a single family: it kills ONLY the two new tests, with all 14 pre-existing tests passing.**
- **A Program Family can now be CREATED from the admin surface, so a new family no longer needs a migration.** `POST /course-program-catalog/families` plus a create control beside the existing family picker. **⚠️ THIS IS THE GAP THE AUDIT'S STEP 3 WAS SUPPOSED TO CLOSE BUT COULD NOT, BECAUSE STEP 3 ALREADY EXISTED** — assigning a family was already possible; creating one was not, which is why `V106` seeded Engineering and `V142` seeded Education. A third family would have been a third migration.
- **⚠️ A families READ endpoint ships with it, and it is not garnish.** A family is created EMPTY, and the admin form previously derived its options from the catalog — so a family created today would have vanished from the picker on refresh, before any program could be assigned to it. **The authoring combobox still derives families from the catalog, deliberately: it only cares about families that have members.** Do not unify the two.
- **Duplicate family names are rejected case- and whitespace-insensitively**, matching the course-program check, because two identical-looking families would produce two identical-looking expansion shortcuts. A lost race on `uk_program_families_name` resolves to the winning row rather than surfacing a constraint violation.
- **⚠️ The new endpoint owes and has a REAL request test.** The pre-existing `CourseProgramCatalogControllerTest` only reflected on annotations — the exact `v0.119.0` shape — so a `MockMvc` POST with `.contentType(APPLICATION_JSON)` was added and **mutation-verified: deleting the header reproduces `HttpMediaTypeNotSupportedException` and a 415.** `lib/api-program-families.test.ts` pins the client's own request shape, since the component test mocks `@/lib/api` wholesale.
- **`EducationProgramFamilyMigrationTest`** — the rename-guard fixture is created BEFORE the migration runs, since a link inserted afterwards resolves to the new name trivially. It also carries the Engineering-untouched regression guard. **⚠️ Its production denominator is ZERO** (no note is linked to the renamed row), so it is a structural guard and must not be reported as evidence that real data survived; its javadoc says so.


### Scope completeness — the three planned items, reconciled

**⚠️ THE PLANNED SCOPE LIST ABOVE READS AS THREE DELIVERABLES AND ONLY TWO OF THEM WERE BUILT. That is
correct, and this table is why** — a later reader must not see a release that shipped 2 of 3.

| Planned item | Outcome | Evidence |
|---|---|---|
| **1.** One migration | **SHIPPED** | `V142__education_program_family.sql`; guarded by `EducationProgramFamilyMigrationTest` |
| **2.** Admin family **selector** on create | **⚠️ NOT BUILT — IT ALREADY EXISTED** | `<select id="catalog-program-family">` has been in `admin-course-program-catalog-section.tsx` since `9e77f412` (2026-08-11, `v0.100.0`), verified an ancestor of `main`. The audit concluded it was missing by reading `app/admin/course-programs/page.tsx`, a 40-line shell that renders it. |
| **2′.** Admin family **create** surface — *substituted for item 2* | **SHIPPED** | `GET`/`POST /course-program-catalog/families` plus the create control. **This is what item 2 was actually for:** its stated justification was *"this is what stops the release recurring"*, and a selector alone does not — a family still could not be created without a migration. The substitution moved the work to the half of the gap that was real. |
| **3a.** Copy polish, **Course / Program** helper text | **SHIPPED** | `applicable-programs-combobox.tsx:328-331`; the resolver sentence was replaced one-for-one, no paragraph added |
| **3b.** Copy polish, **Domain Context** helper text | **NO CHANGE NEEDED — verified, not assumed** | `note-editor-form.tsx:512-515` already reads *"it shapes how the note is written, while the programs decide who finds it."* It carried no resolver mechanics to drop. Checked against the code rather than inferred from the item's wording. |

**So: 2 of 3 planned items built, 1 found already built, plus 1 substitution and 1 verified no-op.** The
release grew by one item (2′) and shrank by one (2).

**⚠️ VERIFICATION TIER WAS RE-DECIDED WHEN THE SHAPE CHANGED, NOT WHEN THE COUNT DID.** Kickoff
pre-declared *a single `advisor()` call* for "a data migration plus one admin form field." Folding 2′
added **a new write endpoint** — a different shape, not merely a fourth item — and `V142` changes
production catalog semantics (the renamed row gains `exam_goal_slug='let'` and becomes publicly
LET-discoverable). Under the `v0.131.0` rule (*decide the tier from the SHAPE*), that fires the
production-data trigger, so **`advisor()` plus one scoped cold agent ran**, framed as falsification of
this session's own named claims.

### Pressure test — one scoped cold agent, framed as falsification

Tier was re-decided when the shape changed (see above), not when the item count did. One agent, a tight
file list, and seven of this session's own named claims to DISPROVE rather than an open-ended audit.
**Three claims were refuted. Four were confirmed, and the confirmations matter as much** — the
`MockMvc` POST really does issue a request with `Content-Type` (not a method call), `exam_goal_slug` really
is one-to-many at every consumer, the combobox really is family-generic, and the admin control really
does GET on mount.

- **Fixed — the new repository SQL was executed by NOTHING.** `CourseProgramCatalogRepository` is a plain
  `JdbcTemplate` class with hand-written SQL constants and **zero** `@Query(nativeQuery = true)` methods,
  and `NativeQueryPostgresIntegrationTest` finds its subjects by reflecting over `@Query` — so the class
  is **structurally invisible** to the PostgreSQL harness that `CLAUDE.md` describes as covering *"every
  native query."* The service test mocks the repository and the controller test mocks the service, so all
  three new statements were verified **by inspection only**. Added
  `CourseProgramCatalogRepositoryProgramFamilyIntegrationTest`, which runs them against a real database.
  **⚠️ It also covers `mapProgramFamily`'s alias→getter mapping — the exact gap `v0.132.0` named, since
  `PREPARE` validates syntax and types but never that `SELECT id, name` actually feeds
  `getObject("id", …)`.** Mutation-verified: deleting the `jdbcTemplate.update(...)` inside
  `insertProgramFamily` — which still typechecks and still returns a valid-looking response — is killed
  by the new read-back assertion at `:88`, **with all 14 pre-existing service tests still passing.**
- **Fixed — `InvalidProgramFamilyNameException` had zero test references.** An added file with no test
  that executes it. It looks redundant with `@Size(max = 120)` on the request record, which is exactly why
  it was skipped — but the annotation guards the CONTROLLER while the exception guards the SERVICE, which
  a direct call reaches without validation.
- **Fixed — `"nothing resolves a course_program by name"` IS FALSE, and it had already reached the owner.**
  `bulk-generation-page-client.tsx:290` does `catalog.find(p => p.name === courseProgram.trim())`, where
  `courseProgram` is seeded from the user's free-text profile field (`:182`). **So the ONE account holding
  the stale `Special Needs Education – Generalist` string loses Bulk Generate's auto-selection after the
  rename** — graceful degradation, not corruption, but a real regression rather than "a string that no
  longer matches." The claim was true of the path it was checked against (`StudyPackGenerationContext-
  Resolver` resolves by ID) and was over-generalized from there.
  `docs/claude-plans/v0.133.0-owner-profile-string-update.sql` now carries the correction and it
  **strengthens** the recommendation to run the write. **⚠️ `V142`'s inline comment still carries the
  original wording and is deliberately NOT edited — the migration is committed, and changing it would
  alter its Flyway checksum and break startup wherever it has already been applied.**
- **Fixed — the LET fallback lists went stale the moment `V142` landed.** `ExamGoalConfig.java` and
  `frontend/lib/exam-hub-config.ts` both hardcode `let → ["Education"]` as a **fail-open** fallback used
  when the live catalog read fails or returns empty. Every consumer of the *live* list correctly treats it
  as list-valued, so there is no single-value bug — but the fallback itself would have silently
  under-represented the exam goal by **seven programs**. Both now list all eight, with their tests updated.
  **⚠️ This is the "sweep by SURFACE, not by diff" rule paying out: neither file was in the diff, and a
  stale fail-open fallback fails silently by design.**

### Findings recorded and deliberately NOT fixed

- **`CourseProgramCatalogRepository.resolveIdForLegacyName()` resolves by exact name and has ZERO callers**
  anywhere in `backend/src`. Confirmed untouched by this release. Genuinely dead code — deleting it is a
  separate change, and it is recorded here so the next reader does not rediscover it as a live risk.
- **The family dedup SQL uses `lower(trim(name))` while the course-program dedup additionally collapses
  internal whitespace** via `regexp_replace`. Not exploitable today: `normalizeForLookup` already collapses
  whitespace in Java before the parameter is bound. Recorded because the two paths are described as
  matching and, at the SQL level, they do not.

### What the cold agent could NOT check

**The production-collision claim.** `V142`'s comment asserts 44 programs with exactly 21 in the seed range,
verified against production on 2026-09-08. **A repo-only reviewer cannot confirm or refute that** — which
is the migration's own stated reason for requiring a precondition read. This is why the release owes a
post-deploy read rather than treating the migration test as sufficient.

### Anti-drift

- **⚠️ Do NOT modify the applicable-programs combobox** — it is already family-generic.
- **⚠️ Do NOT create a new Domain Context** — `GENERAL_EDUCATION`, `PROFESSIONAL_EDUCATION` and `PROFESSIONAL_PRACTICE_AND_REGULATION` already cover LET.
- **⚠️ Do NOT let Program Family select or override Domain Context**, and do not infer the Education family from `GENERAL_EDUCATION`.
- **⚠️ Do NOT feed Program Family or an expanded program list to the LLM.** `StudyPackGenerationContext` has no field for either and `courseProgram` is a single resolved String, so this is **structurally impossible today — keep it that way.**
- **⚠️ Do NOT delete or migrate away the existing `Education` program** — assign it a family only.
- **⚠️ Do NOT mass-update existing Notes' applicable programs.** No backfill of `note_course_program`.
- **⚠️ Do NOT infer Applicable Programs from Review Set membership**, and do NOT make family membership dynamic inheritance — expansion writes explicit program IDs at authoring time and nothing more.
- **⚠️ Do NOT create duplicate catalog entries**, and **do NOT use a credential abbreviation (BEEd, BSEd, CPE/DPE) as a canonical name.** `Teacher Certification` is the endorsed canonical name because `Professional Education` already exists as a `DomainContext` value and as a Subject, so it would collide across two axes.
- **⚠️ Do NOT block mixed-family selections** or add warning UX for them.
- **⚠️ Do NOT redesign the program taxonomy, touch learner-owned Notes, or change pricing, entitlements or Review Set architecture.**
- **⚠️ NO Review Set publication work.** `v0.132.0` shipped that boundary and owes `[CHECKPOINT — due 2026-09-22, deploy-relative]`; its F5 gap (no publish surface in the Builder) is recorded and **is not this release's to fix.**
- **⚠️ NO Learning Connections work** — `[CHECKPOINT — due 2026-09-19]` has a denominator of ONE.

### Tests

The audit's §14 is mostly covered already by the generic component. The genuinely new cases: **Education family expands to its explicit member IDs**; expansion creates no duplicate selections **asserted with two families present**; **mixed-family selection survives — ⚠️ a single-family fixture proves nothing**; the generation context receives no family or expanded list; `GENERAL_EDUCATION` does not auto-select Education programs; Review Set membership does not alter Applicable Programs.

**⚠️ RENAME GUARD: a Note linked to `Special Needs Education – Generalist` BEFORE the rename must still be linked after it and render the NEW name. A fixture created after the rename passes trivially and proves nothing.**

**⚠️ The Engineering-still-expands test is the regression guard for the migration** — if assigning the existing `Education` row a family accidentally touched Engineering rows, that is what catches it.


### ✅ Deploy sequencing — RESOLVED 2026-09-08, both migrations ran (note kept as the record)

**✅ RAN 2026-09-08 — V141 at 12:23, V142 at 15:47, both `success = true`, verified read-only against `flyway_schema_history` on 2026-09-09. The Education family has EIGHT members in production, which is this release's headline claim.** What follows described the state before that deploy and is kept as the record.

`V141` (`v0.132.0`) and `V142` (this release) were **both unrun in production.** Flyway applies them in
order on the next deploy, which is correct — but three dated obligations hang off *when that deploy
happens*, and they are recorded here rather than left to be inferred:

1. **`v0.132.0`'s `[CHECKPOINT — due 2026-09-22]` is deploy-relative, not merge-relative.** If the
   deploy slips, that date must be re-dated — it does not start counting at merge.
2. **`docs/claude-plans/v0.133.0-owner-profile-string-update.sql` must run in the SAME maintenance step
   as `V142`, not before it.** Running it first points the one affected profile at a catalog name that
   does not exist yet.
3. **`docs/claude-plans/v0.130.0-owner-production-checks.sql` is still outstanding.**

**⚠️ All three are the owner's to run. Claude does not execute writes or migrations against production.**

### Known limitations

- **All FOUR authoring surfaces render their own helper paragraph above the combobox's.**
  `note-editor-form.tsx:437-441`, `private-note-detail-page-client.tsx:2704-2706`,
  `bulk-generation-page-client.tsx:591-593` and `admin-applicable-programs-section.tsx:204` each carry a
  discovery-vs-authoring sentence directly above the combobox's own *"they decide who finds it, never
  how it is written."* They overlap without contradicting. **This predates the release** — the copy
  polish replaced one paragraph with one paragraph and added none — and was found by sweeping the
  surface rather than the diff. Left alone deliberately: consolidating it is a copy change across two
  more files and would have made this a fifth item.
- **`Physical Education` is seeded as an Education-family program carrying `exam_goal_slug = 'let'`.**
  That is right for the teaching degree, and the name is also how the *school subject* is commonly
  written. No collision exists today (the read found no such catalog row), but a future curator adding
  a subject-flavoured entry should reuse this row rather than create a sibling.
- **The families `GET` is ADMIN-only, matching the create endpoint.** The authoring combobox still
  derives families from the catalog, deliberately — it only cares about families that have members.
  **Do not unify the two paths.**

