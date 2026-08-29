# Canonical Curated Note Title Policy — Stage 1 audit + Stage 2 plan

**Status: AUDIT ONLY. Nothing implemented.** Audited against real code on 2026-08-29.

**Headline: the root cause is NOT what the policy document assumes, and one of its own
premises is wrong.** The policy's §1 attributes the pattern to title *generation*, which is
correct — but only after a write-back that is easy to miss. An earlier reading of this same
code concluded the opposite (that titles are the curator's typed topic, verbatim). That
reading was wrong, and **what caught it was `docs/features/bulk-generation.md`, not the
code.** Recording that because it is the reusable lesson: the feature doc was accurate and
more legible than the four-hop code path it describes.

---

## Stage 1 — Audit

### 1. How a curated note actually gets its title (the full chain)

```
curator types a topic  ("Fundamentals of Railway Operations in Civil Engineering"?)
   │
   ├─ NoteBulkGenerationService:322  noteService.create(UpsertNoteRequest(item.topic(), …))
   │     → notes.title = the typed topic          ← TEMPORARY, overwritten below
   │
   ├─ noteGenerationService.generateFromTopic(…)  → note-generation-developer.txt
   │     → LLM writes a `title`, which buildGeneratedNoteContent puts in the note BODY
   │       as line 1. It never reaches notes.title.
   │
   ├─ startAsyncGenerationFromNote(…, context, batch.subject())   [:351-358]
   │     → Study Pack generation, prompted with developer.txt + the note body as input
   │
   └─ StudyPackService.applyBulkGeneratedMetadataToNote(…)
         note.setTitle(normalizeEditableTitle(generated.title()))   ← ★ THE REAL SOURCE
```

**★ The final curated note title is the Study Pack LLM's `title`, from `developer.txt`.**
The branch always fires on the bulk path: `preservedSubject` is `batch.subject()`, and
subject is **required** (`NoteBulkGenerationService:423`, `SUBJECT_REQUIRED_MESSAGE`), so it
is never null. `docs/features/bulk-generation.md:5,29` already documents this correctly —
*"Topics… are not note titles… the Study Pack write-back supplies the AI-refined title and
tags."* **That doc is accurate. Do not rewrite it; build on it.**

### 2. Why the suffix appears — §15 answered

**It is emergent, not instructed — with two amplifiers and one probable feedback loop.**

1. **`developer.txt` has NO title style rule at all.** The word `title` appears twice: once as
   the schema slot (`"title": string`, line 6) and once in an unrelated quiz rule (*"do not
   repeat the study pack title"*, line 63). Nothing constrains title shape. **This is the
   root cause: an unconstrained field, not a bad instruction.**
2. **Amplifier A — the Domain is in the same prompt.** The user message is
   `buildContentContextBlock(context)` + the note body, and `developer.txt` says *"Use Domain
   to set the Study Pack's subject matter, terminology, examples, and framing (for example,
   Engineering Mathematics, Nursing, or Accountancy)."* `effectiveAuthoringDomain` resolves
   that to `Civil Engineering` for this catalog.
3. **Amplifier B — `buildSubjectSuggestionGuidanceBlock` enumerates programs in the same
   prompt.** It is concatenated into the Study Pack user prompt and lists *"Civil Engineering,
   Electrical Engineering, Mechanical Engineering, Nursing, Anatomy, Pharmacology…"* as
   example subject values. Program vocabulary sits directly beside an unconstrained title
   field.
4. **Probable feedback loop — UNVERIFIED, see §5 below.** `note-generation-developer.txt`
   *does* have a title rule (*"specific, academic, and anchored to the topic / not generic"*),
   also generated with the Domain in context. Its output becomes note-body line 1, and the
   note body is the Study Pack prompt's input. So a contextualised body heading may be
   echoed into the Study Pack title. **If real, fixing only `developer.txt` leaves the loop
   feeding the old pattern back on every regeneration.**

### 3. Doctrine conflict check — §20.4

**No Accepted ADR conflicts with this policy.** `ADR-001` governs Subject, Domain Context,
Note Learner Level and Applicable Programs; **it does not govern Note Title.** Adding Title
as a named axis (policy §7) *extends* the model rather than contradicting it.

**One adjacent precedent worth citing rather than re-deriving** — `ADR-001` §"Subject /
Domain Context collision": ~334 notes carry a program name as their *subject*
(`Professional Education` 250, `Nursing` 62, …), which it calls a violation of
`buildSubjectSuggestionGuidanceBlock`'s existing rule *"against overly broad subjects and
echoing the program name."* **That is this same disease in a different field, already
diagnosed.** The title policy is the consistent extension of a rule the ADR already applies
to Subject. Cite it; do not restate it.

Its prescribed remedy is also a useful precedent for tone: *"surface an admin-side warning…
**a nudge, never a hard validation error**."* That matches policy §16's ban on
post-processing. **Worth checking whether that nudge was ever built** — it is referenced as
specified, and a specified-but-unbuilt guard recorded as present is drift.

### 4. Shared-path risk — §20.5, and the apparent §12 conflict

**`developer.txt` is shared.** Callers of the Study Pack prompt: bulk curated generation,
`/study` paste-text, image upload, confirm-text, regeneration, and share-remix. Of these,
**`createGeneratedNote` also sets a note title from the LLM** (`note.setTitle(generated.title())`)
— and that is a predominantly *learner* path.

So an unconditional title rule in `developer.txt` changes learner-facing generation too,
which looks like it violates policy §12 (*"Do not enforce canonical curator naming
conventions on learner-authored Notes"*).

**It does not, and this is the distinction the implementing session is most likely to get
wrong:** the rule governs **what the AI proposes**, not **what a learner may name**. A
learner remains free to rename anything to *"Fluid Mechanics for my CE Finals"*; nothing
constrains the editable field. §12 protects naming freedom, not the AI's default suggestion —
and a knowledge-first suggestion is neutral-to-better for a learner too.

**Get an explicit owner acknowledgement of that reading before Stage 2**, because the blast
radius is real and it is a shared-prompt change.

### 5. ⚠️ The one unverified link — run this before choosing the fix's shape

The feedback loop in §2.4 is inference, not evidence, and it is **the entire argument for
changing two prompts instead of one.** Read (production, read-only):

```sql
-- Do curated note titles echo the note body's first line?
SELECT n.title,
       split_part(n.content, E'\n', 1) AS body_heading,
       n.subject
FROM notes n
JOIN users u ON u.id = n.owner_user_id
WHERE u.role = 'ADMIN'
  AND n.visibility = 'PUBLIC'
  AND (n.title ILIKE '% in %' OR n.title ILIKE '% for %')
ORDER BY n.subject, n.title
LIMIT 60;

-- Sizes the rename-on-touch backlog (policy §10-11). Not a migration list.
SELECT count(*) FILTER (WHERE n.title ILIKE '% in %' OR n.title ILIKE '% for %') AS suffixed,
       count(*)                                                                  AS curated_public
FROM notes n
JOIN users u ON u.id = n.owner_user_id
WHERE u.role = 'ADMIN' AND n.visibility = 'PUBLIC';
```

- **Headings and titles closely match** → loop confirmed → both prompts need the rule.
- **They differ materially** → loop is weak → `developer.txt` alone suffices, and
  `note-generation-developer.txt` is left untouched (smaller change, fewer surfaces).

---

## Stage 2 — Plan (NOT implemented; needs the §5 read and the §4 acknowledgement first)

### Recommendation on the narrowest source of truth — §20.6

**Recommend: one unconditional title rule in `developer.txt`** (plus
`note-generation-developer.txt` only if §5 confirms the loop). **Not** a curated-only
templated block.

*Why unconditional:* the policy's own closing section insists the doctrine is universal, not
per-program and not per-population; the rule improves learner titles as much as curated ones;
and a conditional block creates two title doctrines to keep in sync — the exact prompt-doctrine
duplication the policy warns against in §14.

*The alternative, if the owner wants strict §12 isolation:* the mechanism already exists —
`{COMPUTATION_GUIDANCE}` and the level-conditional `buildSubjectSuggestionGuidanceBlock` are
both precedents for a block injected on some paths only. Scope it to the bulk (curated) path.
**This is the one real design decision in Stage 2 and it is the owner's call.**

### Work items

| # | Change | File | Notes |
|---|---|---|---|
| 1 | Add a `title` style rule to the Study Pack prompt | `prompts/study-pack-v1/developer.txt` | The rule currently does not exist. Wording from policy §14. **Semantic, not string-removal** (§16) |
| 2 | *Conditional on §5* — align the note-draft title rule | `prompts/study-pack-v1/note-generation-developer.txt` | Only if the body-heading loop is confirmed |
| 3 | Prompt-content assertions | `OpenAiLlmStudyPackServiceTest` | This class **already asserts prompt text** (e.g. the subject-guidance block), so §20.4's precondition is met |
| 4 | Record the doctrine | `docs/features/bulk-generation.md` + `docs/features/notes.md` | Build on `bulk-generation.md:29`, which is already correct |
| 5 | Record rename-on-touch | wherever curriculum work is picked up | Policy §10-11. Needs a home the curator actually reads |
| 6 | Cite, do not restate | `ADR-001` §"Subject / Domain Context collision" | Title policy is its consistent extension |

### Explicitly out

No migration. No bulk update. No existing title touched. No post-processing or suffix
stripping (§16). No metadata-architecture change. No learner-owned or copied note touched
(§12, §13). No title synchronisation into copies.

### Release placement

**`v0.96.0`, alongside the Domain Context taxonomy doctrine work — not `v0.95.0`.**
`v0.95.0` is capped at four items by its own kickoff and its release notes are written. This
item is prompt + docs + tests, but a **shared-prompt change is one of `CLAUDE.md`'s named
release-size triggers** (two or more surfaces touching the same shared method), so it is not
the doc-only carve-out either. Two doctrine items from the same conversation landing together
in `v0.96.0` is coherent; folding either into a closed release is not.

### Open questions for the owner

1. **Unconditional prompt rule, or curated-path-only block?** (recommendation above)
2. **Does the §4 reading of §12 hold** — the rule governs the AI's proposal, not the learner's
   naming freedom?
3. **Was the `ADR-001` subject-collision nudge ever built?** Verify before assuming.
