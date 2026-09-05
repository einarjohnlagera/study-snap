# Canonical Note Title Generation Integrity — Stage 1 Audit

**Status:** STAGE 1 ONLY — no implementation, no migration, no renames, no regeneration.
**Date:** 2026-09-05. Every claim carries a `file:line` anchor.
**Observed defect:** curator entered `Site Grading Principles` in Bulk Generate; the persisted Note
title became `Site Grading Principles in Civil Engineering`, with Civil Engineering **not** among the
four selected Applicable Programs.

---

## 1. Executive judgment — the cause is neither of the two suspected ones

**It is not context contamination, and it is not a missing prompt rule. Both of those are already
correct in the repo.** The defect is a third thing, and it is narrower and more fixable than either.

> **Bulk Generate creates the Note with the curator's topic as its title, then overwrites that title
> with the STUDY PACK's generated title. Single-note generation never does this.**

```java
private void applyBulkGeneratedMetadataToNote(NoteEntity note, GeneratedStudyPackContent generated,
                                              String preservedSubject) {
    note.setTitle(normalizeEditableTitle(generated.title()));   // ⚠️ unconditional overwrite
    note.setTags(resolveTags(generated.tags(), generated.title()));
    note.setSubject(normalizeUserSuppliedSubject(preservedSubject));
```
`StudyPackService.applyBulkGeneratedMetadataToNote`

Its sibling on the ordinary path, `applyGeneratedMetadataToNote`, **never touches the title** — it
fills only a blank subject and blank tags. So the two paths disagree, and **bulk is the anomaly.**

**And this fires on every bulk item, unconditionally.** `processItem` always passes
`preservedSubject = batch.subject()`, and subject is validated non-blank, so
`StudyPackService:718` always takes the bulk branch and never the conditional one.

**Consequence:** a curator's canonical title is discarded on every bulk-generated Note, and replaced
by a title generated for a different artifact under a different job description. `Site Grading
Principles` was never wrong — it was overwritten.

---

## 2. §25.D — Where "Civil Engineering" came from

**No context field contained it. The model inferred it.** Proven by elimination against the actual
resolver, not by speculation:

| Candidate source | Verdict |
|---|---|
| Selected Applicable Programs | **Not present** — the four selected were Architecture, Environmental Planning, Landscape Architecture, Urban and Regional Planning |
| Resolver `courseProgram` | **NULL for this Note.** `resolveBulkCourseProgram` returns a catalog name **only when `ids.size() == 1`** (four were selected), and `profileCourseProgram` is explicitly `null` for a curator (`!CuratorAuthoringPredicate.isCurator(user)`), so `firstNonBlank(null, null)` → null |
| Curator profile `courseProgram` | **Cannot reach the backend.** The bulk request is exclusive: curators send `courseProgramIds` **only**; `courseProgramText` is sent solely on the non-curator branch (`bulk-generation-page-client.tsx:406-419`) |
| Domain Context label | **"Engineering Sciences"** — `effectiveAuthoringDomain` returns `domainContext.getLabel()` when set, never reaching `courseProgram` |
| Subject | `Site Planning` |
| Tags | Not passed — `resolveForBulkGeneration` hardcodes `List.of()` for tags |
| Review Set / Subject Plan | Never in `StudyPackGenerationContext` at all |
| Prompt examples | **No offending example exists** — see §4 |

**So the model saw `Domain: Engineering Sciences`, `Subject: Site Planning`, topic `Site Grading
Principles`, and produced a plausible-sounding specialization the input never named.**

**⚠️ This matters for the fix.** Tightening what reaches the context would not have prevented it —
the context was already clean. **Only the overwrite made it reach the Note.**

---

## 3. §25.E — Profile fallback audit: closed, on both ends

**Answer: no, the curator profile's Course/Program cannot leak into canonical title generation.**
Two independent guards:

1. **Backend** — `resolveBulkCourseProgram` sets `profileCourseProgram = null` for any curator.
2. **Frontend** — the curator branch of the request omits `courseProgramText` entirely.

**⚠️ One latent hazard worth recording, since it is one line from being live:**
`bulk-generation-page-client.tsx:182` pre-fills the visible Course/Program field from the curator's
own profile (`setCourseProgram((current) => current || meResult.value.courseProgram || "")`),
**ungated by curator status.** It is inert today only because the curator branch never sends that
field. **⚠️ If that request branch is ever unified, the profile would silently become title context.**
Guard it with a test rather than a comment.

---

## 4. §25.B — The prompt already carries the correct doctrine, and it still failed

`backend/src/main/resources/prompts/study-pack-v1/developer.txt:32-38`:

> - prefer the most specific standalone knowledge title; **never make a broad title specific by
>   appending audience or curriculum metadata**
> - the title must stay meaningful when the same knowledge is used in **another applicable
>   Course/Program**
> - this is a judgment about meaning, not about wording: a discipline name belongs in the title when
>   it is **part of the knowledge**, and does not when it only names the container

**That is already the semantic qualifier test §3 of the brief asks for, in the right words.** It was
added by `v0.96.0`. **No conflicting example or instruction exists** — a semantic sweep of the
authoring prompts found no `[Topic] in [Discipline]` pattern being modelled.

**⚠️ So prompt tightening is the weaker lever.** The rule is correct and was violated anyway, which
is what LLM instructions do at scale. **A structural fix that removes the model's authority over
canonical titles is strictly stronger than a better-worded instruction.**

---

## 5. §25.A/F — Call chains, and where they diverge

**Bulk Generate**
```
BulkGenerateNotesRequest(topics, subject, courseProgramIds, domainContext, learnerLevel, …)
  → NoteBulkGenerationService.queueBatch → processBatch
      → ONE context: resolveForBulkGeneration(...)            (batch-level, tags = List.of())
      → per item: generateFromTopic(topic, ctx) | generateAdminContent(topic, ctx)   [note BODY]
      → noteService.create(UpsertNoteRequest(item.topic(), …))   ← title = CURATOR TOPIC ✅
      → startAsyncGenerationFromNote(…, autoApplyGeneratedMetadata=false,
                                      generationContextOverride=ctx,
                                      preservedSubject=batch.subject())
          → LLM generateStudyPack(...)  → GeneratedStudyPackContent.title()
          → applyBulkGeneratedMetadataToNote(...)  ← title OVERWRITTEN ❌
```

**Single Note-from-topic**
```
POST /notes/generate → NoteGenerationService.generateFromTopic → returns CONTENT ONLY (no Note)
  → user saves via the ordinary editor  → title = whatever the user typed ✅
  → POST /notes/{id}/generate → startAsyncGenerationFromNote(autoApplyMetadata flag)
      → applyGeneratedMetadataToNote(...)  ← NEVER sets title ✅
```

**§25.F answer: the two paths share the body-generation pipeline and the prompt, but NOT the title
pipeline.** Only bulk writes a generated title onto a Note. So this is **not** a shared-code defect,
and fixing bulk is not a "Bulk-Generate-only workaround" — it is fixing the path that diverged.

**§5.7 answer: yes** — the Study Pack's generated title overwrites the original topic.
**§5.6:** the final persisted title is chosen **after** Study Pack generation, in the async
transaction, long after the curator's input.

---

## 6. §25.C / §9 — What actually reaches title generation

`StudyPackGenerationContext(learnerLevel, courseProgram, subject, tags, domainContext,
noteLearnerLevel)` is passed **whole** to Study Pack generation, and the title is one field of that
one call. There is **no separate title-suggestion prompt or pass.**

| Field | Reaches the title-producing call | On this Note |
|---|---|---|
| `subject` | yes | `Site Planning` |
| `domainContext` | yes, as `Domain: <label>` | `Engineering Sciences` |
| `courseProgram` | yes when non-null | **null** |
| `tags` | **no** — bulk hardcodes `List.of()` | — |
| `learnerLevel` / `noteLearnerLevel` | yes | College |
| Applicable Programs (as a list) | **never** — the context has no list field | — |

**§9's suspicion is correct in principle and not the cause here:** the same object *is* reused for
body and title, and a reusable object is not automatically the right semantic input for both. **But
this Note's context was already clean** — so context narrowing would not have prevented the defect
and is **not** the minimum fix (§7).

---

## 7. §25.G/H — Recommendation

### G. Title authority — **the curator-supplied topic wins by default**

**Recommendation: adopt §11's doctrine.** On the bulk path, the curator's topic **is** the canonical
Note title; the Study Pack's generated title stays on the Study Pack, where it belongs.

Repo evidence supports this strongly rather than as a preference:

- the ordinary path **already behaves this way** — this restores consistency rather than inventing a rule;
- the Note is **already created with the correct title**; the fix deletes a later mutation;
- the prompt rule is already correct and still failed (§4), so removing the model's authority is the
  stronger lever;
- **`v0.118.0` regeneration makes bad titles self-propagating** — its locked contract is *"the Note's
  current title is the topic"*, so a contaminated title becomes the seed for regenerated content. See §9.

### H. Minimum fix

**One change: `applyBulkGeneratedMetadataToNote` stops setting `title`.**

It should keep setting `subject` (from `preservedSubject`, the curator's own batch value) and **may**
keep filling tags. Everything else stays.

**⚠️ Do not delete the method or merge it with `applyGeneratedMetadataToNote`** — the two have
genuinely different jobs (bulk stamps the curator's batch subject authoritatively; the ordinary path
fills only blanks).

**⚠️ Tags need a decision, not an assumption:** `resolveTags(generated.tags(), generated.title())`
currently derives tags partly **from the generated title**. If the title is no longer applied, tags
derived from it become inconsistent with the persisted title. **Recommendation: keep LLM tags** (the
curator supplies none in bulk — `UpsertNoteRequest` passes `List.of()`), but derive them from the
**curator's topic**, not the discarded generated title.

**Optional, second order:** a narrowed title contract (§19) that passes only topic + Subject. **Not
recommended now** — it is a larger prompt-architecture change that this defect does not justify, and
§6 shows the context was already clean.

---

## 8. §23/§24 — Regression tests and the title-proof table

**The tests must encode the doctrine, not a banned-string list** — otherwise they would fail the
legitimate cases below.

**Discriminating cases (each fails under the current behaviour):**

1. **The observed case.** Topic `Site Grading Principles`; four non-Civil programs; Domain Context
   `ENGINEERING_SCIENCES`; Subject `Site Planning`. **Persisted title must be exactly the topic.**
   ⚠️ Assert the **persisted** value after generation completes, not the create call — the overwrite
   happens later, in the async transaction.
2. **Shared engineering knowledge.** Topic `Fluid Mechanics`, several engineering programs →
   `Fluid Mechanics`.
3. **Legitimate qualifier survives.** Topic `Nursing Management of Acute Asthma` → **unchanged**.
   This proves the fix preserves rather than strips discipline terms — under the recommended fix it
   passes trivially, which is the point: the curator's words are kept either way.
4. **Path parity.** The same topic through single-note generation and through bulk must persist the
   **same** title.
5. **Profile isolation (§3's latent hazard).** A curator whose profile Course/Program is
   `Civil Engineering`, generating with four unrelated programs, must produce a context whose
   `courseProgram` is **null** — pinning the guard that is currently one refactor from failing.

### Title-proof table (§24)

| Family | Omit the qualifier | Keep the qualifier |
|---|---|---|
| Engineering Mathematics | `Differential Equations` | `Applications of Differential Equations in Structural Engineering` |
| Engineering Sciences | `Fluid Mechanics` | `Thermodynamics for Refrigeration Systems` |
| Civil Engineering | `Highway Drainage Systems` · `Construction Materials and Testing` | `Civil Engineering Law and Ethics` |
| Architectural Design | `Architectural Programming` | `Architectural Acoustics` |
| Site Planning | **`Site Grading Principles`** | `Site Planning for Hillside Development` |
| Nursing | `Acute Asthma` (pathophysiology) | `Nursing Management of Acute Asthma` · `Statistics for Nursing Research` |
| Accountancy | `Revenue Recognition` | `Accounting for Long-Term Construction Contracts` |
| Professional Practice / Regulation | `Contract Documents` | `Professional Ethics for Architects` |
| Education | `Assessment Design` | `Classroom Applications of Gagné's Nine Events` |

**The right-hand column is the proof the doctrine is semantic:** in each case the qualifier changes
the knowledge promised, not the container it was created in.

---

## 9. §21 — Interaction with `v0.118.0` regeneration

**Verified: the regeneration plan already guarantees the needed behaviour, for two independent
reasons.**

1. Its metadata contract is explicit that regeneration **consumes** metadata and never rewrites it;
   its anti-drift says *"Do NOT silently rewrite Note metadata — title, subject, domain context,
   depth and programs are inputs"*, and *"do not auto-persist any LLM-suggested metadata."*
2. Structurally, the regeneration path passes `autoApplyGeneratedMetadata = false`, so it reaches
   `applyGeneratedMetadataToNote` — **which never sets a title.**

**⚠️ But the coupling runs the other way and is the reason this fix is urgent.** Regeneration's
locked contract is *"the Note's current title is the topic."* A title contaminated by bulk becomes
the **seed** for regenerated content, so `…in Civil Engineering` would shape the body of every future
regeneration of that Note. **Fix titles before regeneration ships against the canonical library.**

---

## 10. §25.J — Existing debt

**Not measured, and deliberately not fixed.** Estimating it needs a production read (a `LIKE '% in %'`
scan over curator-owned notes plus a hand-check, since many matches would be legitimate). **⚠️ The
production database is read-only for Claude — that query is the owner's to run**, and a `.sql` file
can be prepared on request.

**⚠️ Do NOT mass-rename.** Doctrine stays *normalize on meaningful touch* (§22). This audit stops
**new** debt; a cleanup workflow is a separate decision. Note that every bulk-generated Note created
since the bulk path shipped is a candidate, so the population is plausibly large — an argument for
fixing forward, not for a sweep.

---

## 11. §20 — Curator vs learner

**No learner-facing behaviour changes.** The overwrite is bulk-only, and bulk is curator/admin-gated.
Learner-authored titles are untouched today and stay untouched — §20's caution is satisfied by the
fix's scope, not by an added branch. **⚠️ Do not add a curator-vs-learner title branch** — there is
nothing to differentiate once the overwrite is gone.

---

## 12. §26 — Genuine owner decisions

1. **Should the curator topic always win, or should an explicit "suggest a better title" action
   exist?** **Recommendation: topic always wins; no suggestion action in this fix.** A curator who
   wants a different title can type it, and the note is editable immediately. Adding an opt-in
   suggestion is a feature, not part of stopping the regression.
2. **What happens to LLM tags once the title is not applied?** (§7.H) **Recommendation: keep the
   tags, derive them from the curator's topic** rather than the discarded generated title.
3. **Is the narrowed title-input contract (§19) wanted later?** **Recommendation: defer** — the
   context was already clean here, so it would not have prevented this defect.

**Settled by the audit, not owner questions:** the profile fallback is closed (§3); the prompt rule is
already correct (§4); bulk and single-note do **not** share a title pipeline (§5); regeneration
already preserves titles (§9).

---

## 13. §27 — Anti-drift

- **⚠️ Do NOT add Course/Program suffixes by default**, or infer a title from Applicable Programs,
  Review Set, Subject Plan, or Domain Context.
- **⚠️ Do NOT let profile Course/Program become canonical-title authority** — closed today; pin it
  with the §8.5 test, since `bulk-generation-page-client.tsx:182` keeps the value one refactor away.
- **⚠️ Do NOT remove legitimate disciplinary qualifiers** — the fix keeps the curator's words, whatever
  they are; a banned-substring filter would break every right-hand case in §8's table.
- **⚠️ Do NOT weaken `developer.txt:32-38`** — it is correct and still governs Study Pack titles.
- **⚠️ Do NOT mass-rename existing Notes**, and do not couple this to curation.
- **⚠️ Do NOT regenerate Study Packs** to "repair" titles.
- **⚠️ Do NOT change Applicable Programs architecture, the Domain Context taxonomy, or Review Set
  structure.**
- **⚠️ Do NOT delete `applyBulkGeneratedMetadataToNote` or merge it with its sibling** — it still owns
  the curator's batch subject.
- **⚠️ No migration.** This is one method's behaviour.

---

## 14. Verification tier

**A single `advisor()` call on the diff.** One method, one removed mutation, no permission substrate,
no cross-user read, no money semantics, no migration.

**⚠️ Escalate to one scoped cold agent if** the tags decision (§12.2) turns into a change to
`resolveTags` shared with other callers.

**Routing: Claude Code inline** — one backend method plus tests.
