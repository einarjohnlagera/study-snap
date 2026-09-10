# 2026-09-09 — Note + Study Pack regeneration: deterministic `LLM_INVALID_OUTPUT` on the generated title

## Status: diagnosis only. No code, config or prompt changed. No release opened.

**Reported** by the owner 2026-09-09: some notes failed to regenerate in production. **Window read:**
`2026-09-09T14:06Z–14:27Z` on `srv-d6u0jkvgi27c73dvl9k0` (`notelib-backend-prod`), backend
**`v0.136.0`**, instance `…-47kfg`. Code read at the same version.

Claims below are labelled **VERIFIED** (a log line, a metric, a query result, or code I opened) or
**INFERRED**. ⚠️ **The single most important thing in this file is §4: the failing branch is NOT
confirmed, and it cannot be confirmed from the logs as they are written today.**

---

## 1. What happened

Three bulk batches, one owner (`dee4225c-e460-4f89-a6e5-cd43f6dd1972`). **VERIFIED** from
`NoteBulkRegenerationService` `action=bulk_regenerate_batch` lines:

| Time (UTC) | batchId | requested | regenerated | blocked | failed |
|---|---|---|---|---|---|
| 14:08:41 | `eb217337` | 1 | 0 | 0 | 1 |
| 14:24:46 | `f5c690f5` | 20 | **17** | 0 | 3 |
| 14:25:38 | `6a7a8fa9` | 3 | 0 | 0 | **3** |

**7 failure events across 4 distinct notes.** ⚠️ **Batch `6a7a8fa9` is a retry of the three that
failed in `f5c690f5`, and all three failed again with the same code — so this is DETERMINISTIC, not a
transient LLM or network flake.** That is the property that makes it worth fixing rather than retrying.

The four notes, **VERIFIED** by read-only query (all now `status = 'FAILED'`, all
`learner_level = BOARD_EXAM_REVIEW`):

| noteId | title | words | chars |
|---|---|---|---|
| `7d459348` | The Thomasites and Their Contributions to Philippine Education | 8 | 62 |
| `983dbb56` | Code of Ethics for Professional Teachers: Principles and Implementation | 9 | 71 |
| `bee3ddb4` | Magna Carta for Public School Teachers (RA 4670) | 8 | 48 |
| `76a4bac6` | Code of Conduct for Public Officials under RA 6713 | 9 | 50 |

Failure latencies 8.2 s – 13.8 s, consistent with two LLM round trips (initial + one retry).

## 2. The mechanism

**VERIFIED.** Every failure is `failureCode=LLM_INVALID_OUTPUT`, logged by
`StudyPackService:884-897` via `lambda$startAsyncNoteAndStudyPackRegeneration$2(StudyPackService.java:333)`.
Each is preceded ~5 s earlier by:

```
WARN c.s.b.s.impl.OpenAiLlmStudyPackService : Retrying OpenAI response validation after
  invalid output on attempt 1: The note generation service returned an invalid title. Please try again.
```

That message is raised in exactly one place — `buildGeneratedNoteContent`
(`OpenAiLlmStudyPackService:2373-2378`), validating the model's **title**:

```java
String title = normalizeGeneratedNoteText(
        generatedNote.title(), 1, MAX_GENERATED_NOTE_TITLE_WORDS,
        "The note generation service returned an invalid title. Please try again.");
```

`retryOnceOnInvalidOutput` retries a single time (`MAX_INVALID_OUTPUT_ATTEMPTS`), the retry fails
identically, and the exception propagates to `StudyPackService`, which marks the note `FAILED`.

⚠️ **The generated title is NOT discarded, so this is not "a thrown-away value aborting the run."** I
initially suspected that, given `v0.120.0`'s canonical-title rule, and **it is wrong**:
`buildGeneratedNoteContent` assembles the title into the note **body** as its first line, and the
regeneration path consumes `generateFromTopic(...).content()`. The title is genuinely used.

## 3. The published contract does not include the bound that is enforced

**VERIFIED**, and this is verifiable from code alone, independent of whether it caused these seven
failures.

**Enforced:** `MAX_GENERATED_NOTE_TITLE_WORDS = 12` (`OpenAiLlmStudyPackService:75`), checked by
`StringNormalizationUtils.hasWordCountBetween` → `countWords`, which is `trim().split("\\s+").length`.

**What the model is actually told:**

| Field | Published to the model | Enforced in code |
|---|---|---|
| bullets | `Keep each bullet at or under {MAX_ITEM_CHARS} characters` (3×, `note-generation-developer.txt:46,53,59`) | characters |
| note body | `Keep the note under {MAX_WORDS} words` (`:71`) | words |
| **title** | **nothing numeric** — schema `maxLength: 160` **characters** (`:1132-1134`), prose rules at `:18-30` that are meaning-and-judgment plus "concise and specific" | **12 WORDS** |
| | `note-generation-system.txt`: **zero** matches for word / length / concise / any number | |

⚠️ **The title is the only field carrying an enforced numeric bound that is never stated to the
model** — and the file already knows how to state one, because it templates `{MAX_ITEM_CHARS}` and
`{MAX_WORDS}` into the same prompt.

⚠️⚠️ **The codebase documents this exact anti-pattern and instructs against it.**
`OpenAiLlmStudyPackService:2553-2561`, on why a word ceiling was removed from *bullets*:

> "A prose word ceiling used to sit here as a second, unpublished bound, and it rejected exactly the
> content the prompt asks for — a formula plus its variable definitions is word-dense and
> character-light… **Keep validation on the published contract; do not reintroduce a bound the model
> cannot see.**"

That lesson was applied to bullets. **The title kept its word ceiling.** A model told "be concise and
specific", "anchored to the topic", and "≤160 characters" can satisfy every instruction it was given
and still be rejected — twice — killing the whole regeneration.

## 4. ⚠️ WHAT IS NOT ESTABLISHED — and why the logs cannot settle it

`normalizeGeneratedNoteText` throws on **either** branch: the normalized value is `null` (blank or
whitespace-only after `repairJsonEatenLatexCommands` + whitespace collapse), **or** the word count
falls outside `1..12`.

- **>12 words is the PROBABLE branch, not the confirmed one** (**INFERRED**). The strict JSON schema
  already enforces `minLength: 1`, which makes a blank title unlikely but not impossible.
- ⚠️ **I have ZERO observations of an actual rejected title.** The log records the failure code and a
  human message; it never records **the title that was rejected, its word count, or which bound
  failed.** Four notes failed seven times and produced no evidence of what was wrong.

⚠️ **The source-title length correlation is NOT discriminating and must not be cited as support.**
Measured over that owner's notes updated in the last 30 minutes (**VERIFIED**):

| status | notes | avg title words | min–max | avg chars |
|---|---|---|---|---|
| FAILED | 4 | 8.50 | 8–9 | 57.8 |
| GENERATED | 48 | 6.67 | 3–11 | 52.7 |

Successes reach **11** source words and the ranges overlap; 4 rows against 48 is not a signal. It was
checked expecting support and provides none. **The bound applies to the GENERATED title, and no
observation of one exists.**

## 5. Ruled out

- **The live Vercel/Render deploy skew is NOT involved.** This is backend-only validation of an LLM
  response on `v0.136.0`; no frontend request shape reaches it. (Recorded because `CLAUDE.md` flags a
  live skew, and a reader would otherwise reasonably suspect it.)
- **Not the connection pool.** No `HikariPool` timeout, no `DataSourceHealthIndicator` failure, no
  restart in the window — and both LLM calls on this path deliberately run with **no** transaction and
  no JDBC connection held (`v0.112.0`, documented at `StudyPackService:795-803`).
- **Not quota, rate limit or a missing Study Pack.** Those reject synchronously before `GENERATING` is
  set and are reported as `blocked`; every batch line reads `blocked=0`.

## 6. Blast radius

- **17 of 20 succeeded in the main batch**, so this is a ~15 % failure rate on that batch rather than a
  broken feature. But it is **deterministic per note**: the affected notes cannot be regenerated by
  retrying, which is exactly what the owner tried at 14:25.
- All four are **Philippine board-exam legal/professional topics**, where a faithful, specific title is
  word-dense and character-light — the same shape as the Engineering Economics content that motivated
  removing the bullet word ceiling. **INFERRED** that this content class is disproportionately exposed;
  4 notes is not enough to assert it.
- Each failure burns two LLM calls and, on the combined scope, an LLM note-generation call before it.
  ⚠️ Both meters stay untouched on failure by design (`recordUsage` deferred to the commit
  transaction), so the cost is latency and spend, not a wrongly-charged learner.

## 7. Unrelated observations from the same window — flagged, not investigated

- `OfficialChallengeQuizTemplateService : Official Challenge template seed failed` for
  `noteId=e5232c7f…` and `noteId=42a789a8…`.
- Repeated `field=subject value='Education' reason='overly broad ai suggestion ignored'` (6×) — this is
  a working guard reporting normal operation, not an error.

## 8. Obligations

- This file needs a **Backlog Index row** (kickoff step 8 names `docs/claude-findings/` because
  incident files have gone unindexed there before).
- Fix plan: `docs/claude-plans/2026-09-09-generated-title-bound-and-failure-observability-plan.md`.
- ⚠️ **`v0.137.0 — Deploy Integrity` is open and this is outside its scope.** See §8 of the plan.
