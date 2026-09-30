# Domain Context Catalog — independent architectural assessment

**Written:** 2026-08-17 · **Author:** cold-context review agent, reading the real code
**Subject:** the owner's proposal to replace the `DomainContext` enum with an admin-managed **Domain Context Catalog**, plus a second suggestion to add **Domain Categories** mirroring Program Families
**Repo state read:** branch `feat/target-audience-removal-phase-2`, `v0.83.0` in progress
**Method:** every claim below is anchored to `file:line` in the code as it stands. Nothing here is taken from a summary, a feature doc, or the proposal's own characterisation of the codebase. Where the repo contradicts itself, the code wins and the contradiction is named.

> **This file needs a `ROADMAP.md` Backlog Index row** at the next `/kickoff`, per checklist step 8 (every `docs/claude-plans/` file must be indexed). It is an assessment, not a finished release artifact, so the *release artifact* exemption does not cover it.

---

## 0. Headline

**Recommendation: do not build the Domain Context Catalog now.** Build **Option A** (§7) instead — a documentation-and-copy release that gives curators the covered-subject guidance the vocabulary has always lacked, plus the empirical check that decides the Engineering Economics case. Keep the enum.

The curator hit a real wall. Engineering Economics genuinely does not announce which of the eight values it belongs to at the moment of authoring. **The wall is real; the diagnosis differs.** What is missing is not *extensibility* — it is *legibility*: each Domain Context's covered-subject list exists, in `docs/claude-prompt/canonical-knowledge-architecture-out/08-taxonomy-validation-and-final-recommendation.md:56-67`, and appears **nowhere** in the ADR's ratified section, nowhere in the product, and nowhere a curator would look mid-session. The owner's own proposed **Description** field ("what belongs in this domain") is exactly the right instinct — and it can ship as copy beside the existing `<select>`, with no table, no migration, and no change to how the vocabulary is governed.

Answering the owner's own framing directly: *"My goal is not to make Domain Context flexible. My goal is to make it scalable while keeping it curated."* **There is no scaling problem yet.** The set has stood at 8 values with **zero additions** since ratification on 2026-08-03 — verifiable from `backend/src/test/java/com/studysnap/backend/entity/DomainContextTest.java:22-31`, which still asserts exactly the eight ratified labels, across roughly fourteen releases. And the property that *keeps it curated* is precisely the code-change friction the catalog removes. A `POST /domain-context-catalog` has no reviewer, no revision log, and no deploy gate. **Today the pull request is the enforcement mechanism.** Replacing it with an endpoint makes governance strictly weaker and then requires extra process to climb back to where it started.

---

## 1. Direct verdicts on the five questions, plus Domain Categories

### Q1 — Is Domain Context mature enough for the same admin-managed treatment as Course / Program?

**No. Not on maturity, and not on the analogy.**

Two independent reasons.

**(a) The growth evidence does not exist.** Course / Program earned its catalog on measured pressure: 27+ live values, free-typed vocabulary drifting from the canonical set (`v0.79.0` measured 31 hardcoded suggestions against a 21-row catalog with only 16 overlapping), and real learner-visible consequences. Domain Context has **8 values, zero additions in two weeks and ~14 releases, and one candidate under discussion**. A catalog built for a taxonomy that has not yet grown once is speculative infrastructure.

**(b) The precedent is weaker than the proposal assumes — verified, not inferred.** `CourseProgramCatalogController.java:27-43` exposes exactly three operations: `GET`, `GET /similar`, `POST`. There is **no PUT, no rename, no archive, no delete, no active flag, and no usage count**. The table (`V106__course_program_catalog.sql:8-19`) has five columns: `id, name, program_family_id, exam_goal_slug, created_at`. So the rename/archive semantics the proposal wants to "mirror" **do not exist to mirror**. Adopting "the same philosophy as Course / Program, with stricter governance" would mean *inventing* archive-and-rename for a taxonomy of 8, while the 21-row taxonomy that actually grows still lacks them.

Three further precedent details worth having on the record:

- **Rename in `course_programs` would silently rewrite discovery.** Every library and public read joins `course_programs cp on cp.id = ncp.course_program_id` and filters/aggregates on `cp.name` (`NoteLibraryRepositoryImpl.java:97,228,334`; `PublicLibraryRepositoryImpl.java:65`). Nobody has had to solve the rename-safety problem there **because renaming is impossible**. That absence is a design choice worth copying, not a gap worth filling.
- **`V106` left dead schema.** `notes.course_program_id` and `users.course_program_id` were added and backfilled by `V106:48-70`; a grep of `src/main` finds **no reader** for either. The actual precedent for "introduce the catalog architecture now, wire it later" is two unread FK columns.
- **Program Families do real work; Domain Categories would not.** Families are a save-time authoring pre-fill that expands to explicit rows (`ADR-001` rule 5, ratified in full 2026-08-05). The owner is explicit that Domain Categories would be *"purely to keep the admin experience manageable."* See the Domain Categories verdict below.

### Q2 — Hidden responsibilities tied to the enum that would make a catalog dangerous?

**Yes, four, and two of them change behaviour silently.** Full detail with `file:line` in §4. Summary:

| # | Responsibility | Class |
|---|---|---|
| 1 | The enum is a **read-side hard constraint**, not just a write-side validator: three separate `Enum.valueOf` call paths throw on an unknown stored value and take a whole page query with them | **would break** |
| 2 | `isQuantitativeContext` **substring-matches the domain label** against a keyword list to decide whether computation guidance enters the prompt, at 7 call sites | **silently changes behaviour** |
| 3 | `effectiveAuthoringDomain` returns `getLabel()` — **the display label *is* the prompt payload**, so a rename retroactively changes every future generation and destroys reproducibility of past ones | **silently changes behaviour** |
| 4 | Four independent multi-program guards check only `domainContext == null`, so **archiving has no runtime effect whatsoever** beyond hiding an option | design constraint, must be stated |

### Q3 — Rename/archive rather than deletion once referenced by notes?

**Partly right, and the ordering of danger is inverted from intuition.**

- **Delete: correct to forbid, and the cheapest protection is to never build the endpoint.** `course_programs` has no DELETE and needs none. Add a real FK (§5) and the database enforces it without a service-layer check.
- **Archive: safe, and nearly free — but understand that it does almost nothing.** Archiving must affect *only* which options the authoring `<select>` offers. It must never affect label resolution on the generation path, and it cannot enforce "stop authoring in this domain," because every existing note keeps its value and keeps sending the archived label to the model. The four multi-program guards (`NoteService.java:1437-1440`, `NoteApplicableProgramsService.java:74-76`, `NoteBulkGenerationService.java:424`, `NoteGenerationService.java:74`) test `== null` only; an archived reference is non-null, so the invariant continues to hold. That is the *right* outcome — no note breaks — but it means "archived" is a curator-UI concept with zero enforcement power. State it, or someone will later assume otherwise.
- **Rename: the most dangerous of the three, despite feeling like the safest.** `StudyPackGenerationContextResolver.java:167-175` returns `context.domainContext().getLabel()`, and `OpenAiLlmStudyPackService.java:1569-1573` interpolates that string directly into the `Domain:` prompt line ahead of `DOMAIN_CONSTRAINT`. The label is not a display concern; it is model input. Renaming `Civil Engineering` → `Civil Engineering & Construction` silently changes future generation for every note carrying that value, with no note edited and no diff anywhere.

  **A second, independent reason rename must be append-only:** today `domain_context = 'CIVIL_ENGINEERING'` deterministically implies the prompt string. Move labels into a table and prompt behaviour is **no longer reproducible from `notes` alone** — reproducing a past generation requires the label *as of that moment*. This repo runs R4-style generate-and-diff verification (`ADR-001` "R4 verification") and treats recorded evidence as load-bearing. Making the prompt payload mutable and unversioned is an auditability regression in exactly the practice the ADR depends on.

  **Verdict: if a catalog ever ships, ship it without a rename endpoint.** Treat `label` as append-only in practice; a label correction is an owner decision with an ADR revision-log entry and a migration, exactly like today.

### Q4 — Migration strategy from enum to catalog without affecting existing notes or prompt behaviour?

A strategy exists and is given in full in §5. **But its cost is roughly an order of magnitude above what the proposal assumes**, and that gap is itself an argument against proceeding now — see §5's surface list. Headline: keep storing the **code** (`ENGINEERING_MATHEMATICS`), never switch to a UUID FK, and understand that the table is the small part and de-enumming nine read paths plus caching label resolution on the generation hot path is the large part.

### Q5 — Governance rules worth enforcing?

**Yes** — §6 separates the ones code can enforce from the one that actually matters and cannot be enforced by code. The short version: the ADR revision-log requirement is the load-bearing rule, and it is process. Every code-enforceable rule below it is secondary.

### Bonus — Domain Categories (the GPT session's suggestion)

**No. Not now, and probably not ever in schema form.**

Four reasons, in descending strength.

1. **Its justification is the arrival of the state `ADR-001` defines as failure.** `ADR-001`'s governance block sets a failure condition reviewed at every `/kickoff`: *"if the number of Domain Context values ever approaches the number of course programs, the taxonomy has failed and has collapsed back into the free-text field it replaced. Baseline at ratification: 8 contexts against 27+ programs."* The Domain Categories case is *"if the catalog grows from 8 to 20–30 entries."* **20–30 domains against 27+ programs is the ratio the ADR says means the taxonomy has failed.** Building administrative ergonomics for that state is preparing for the failure mode instead of preventing it.
2. **There is no function to hold.** Program Families expand to rows at save time — a measurable productivity feature with three binding constraints. Domain Categories are declared to have no runtime consumer, no learner exposure, and no LLM role. A grouping with no consumer is a `<optgroup>`, and an `<optgroup>` needs a table only if the grouping is data someone edits. At 8 values, and even at 12, a static map in `frontend/lib/domain-context.ts` is free and reversible.
3. **"Not used by the LLM" does not survive the deferred prompt-hint field.** The owner defers authoring guidance to a later release. The moment a category carries anything — even a shared default hint — the categories become prompt input, and the pressure to put one there will come from the same place the categories came from. The safest sequencing is: no categories until there is a prompt-hint decision, and no prompt-hint decision without an R4-style generate-and-diff read per hint.
4. **Its ergonomic premise is, today, one person's dropdown.** `ADR-001`'s own production sizing table (query C, run 2026-08-11) records *"Every curated note in production is admin-owned"*, with 885 admin-owned notes on the curator page. There is currently one curator.

**If admin ergonomics genuinely bite** at some future count, add `optgroup` from a frontend constant. That is a ~20-line change and needs no decision from anyone.

---

## 2. Engineering Economics — the concrete answer

### What the repo actually says (and how strong that is)

Two in-repo planning documents place Engineering Economics inside an existing domain:

- `docs/claude-prompt/canonical-knowledge-architecture-out/06-domain-context-taxonomy.md:56` — `Engineering Mathematics` covers *"Algebra, Trigonometry, Analytic Geometry, Calculus, Differential Equations, Probability & Statistics, **Engineering Economics**"*
- `docs/claude-prompt/canonical-knowledge-architecture-out/08-taxonomy-validation-and-final-recommendation.md:57` — the revised 8-value set repeats it verbatim in item 1

**Do not read this as settled.** Both documents disclaim authority over exactly this kind of claim: `06:64` says the subject lists are *"my grouping from your data and general knowledge of Philippine engineering curricula — I have not verified them against the current PRC board syllabi, and you should not treat them as authoritative"*, and `08:70` repeats it. And `ADR-001:291` ratified the **value set** (eight names) — it did **not** reproduce or ratify the membership lists, and `ADR-001:299` explicitly flags applicability groupings as unverified. So the correct status is: **proposed, never contradicted, never verified.**

What raises it above a guess is that `ADR-001`'s own binary test points the same way independently:

> **Test (binary):** would a student in a sibling program be served by this exact note, unchanged? Yes → the shared bundle. No → the program name. (`ADR-001:279`)

Engineering economy — present worth, annuities, depreciation, rate of return, benefit-cost — is essentially identical across the PRC engineering boards. A Mechanical Engineering student is served by an unchanged Civil-authored Engineering Economics note. The test therefore says **the shared bundle**: `Engineering Mathematics`. Two weak but convergent signals.

And the governance floor is not met for a new value. `ADR-001` requires *"(a) a sustained body of canonical knowledge — ~10 or more notes already authored or firmly planned — **whose treatment cannot be accurately represented by an existing value**"*. Engineering Economics may well clear the note count; it fails the second clause, which is the clause that does the work.

### Today, under the current architecture

**Set Domain Context = `Engineering Mathematics`, and run the ADR's own empirical tie-break before committing the whole subject plan to it.**

`ADR-001:283`: *"because this value is substituted into the generation prompt, generate the note under both candidate values and compare the output. For a small team this is faster and more decisive than adjudicating definitions."*

Concretely: author one Engineering Economics note, generate under `Engineering Mathematics`, regenerate under the next-best candidate (`Engineering Sciences`, or the `Civil Engineering` program-name fallback with `domain_context` left NULL), and check whether the `Engineering Mathematics` output produces interest-formula / present-worth / cash-flow-diagram framing or drifts toward generic algebra. This is the same procedure that resolved R4 on 2026-08-04 and it is a ~20-minute check. **It is the single thing that decides between Option A and Option B**, and it should happen before either is scheduled.

If it passes: the answer is `Engineering Mathematics`, permanently, and the fix owed to curators is that this was not discoverable — Option A.

If it fails: add **one** enum value by the mechanism that already exists — four files, one line each, and an ADR revision-log entry (Option B; itemised in §7). Note that `General Engineering`, the name the owner floats, would be an **invented** name, and `ADR-001:287` records that the two previously-invented candidates (`Health Sciences Foundation`, `Computing`) failed both the learner-comprehension test and the governance rule: *"an invented name usually signals there is no real shared body of knowledge behind it."* Prefer a borrowed board-syllabus name — `Engineering Economics` itself is a real PRC subject-area label and would be the honest choice if a new value is genuinely needed.

### Under a catalog (if one existed)

**Identical.** The curator would still choose `Engineering Mathematics`, and the owner would still have to decide whether a new value is warranted. A catalog changes who types the row, not who decides it — and the owner's proposal explicitly keeps the decision with the owner. **This is the sharpest test of the proposal: it does not change the answer to the problem that triggered it.** What *would* change the answer is a description string beside the option reading *"Engineering-wide mathematical and quantitative foundations: algebra, trigonometry, calculus, differential equations, probability & statistics, and engineering economics."* That is Option A.

---

## 3. Contradiction with `ADR-001`'s ratified language

**One contradiction, narrow, and the recommendation is to narrow the proposal rather than amend the ADR.**

The ratified **Domain Context governance** block says:

> *"Introducing a new one is an **architectural decision, not routine curriculum authoring** — it changes how the LLM is instructed to author an entire class of content… Treat it with the weight of a schema change: it needs an owner decision and a recorded rationale, **not a curator's judgment call mid-authoring-session**. **Adding notes is authoring. Adding a Domain Context is architecture.** This distinction is the primary defence against taxonomy explosion, and it is the reason the field is a curated closed set rather than free text."*

An `ADMIN`-only `POST /domain-context-catalog` makes adding a value **exactly** a mid-authoring-session judgment call for the only person who is both curator and admin. That is a direct contradiction of the ratified sentence, and it is the *only* one — the proposal does not touch the sole-LLM-domain-constraint rule, does not make the axis multi-valued, and does not reach discovery.

**The proposal's own language shows the owner does not intend the contradiction** — *"MUST remain curated… MUST remain closed… much stricter governance."* So this resolves by narrowing:

- **Do not amend the governance block.** The two sentences that must survive verbatim are *"Adding notes is authoring. Adding a Domain Context is architecture"* and the failure-condition ratio. The entire design rests on them.
- **If a catalog ever ships**, `ADR-001` needs *one additive sentence*: that the ratified value set now lives in `domain_contexts`, and that **this ADR's revision log remains the authorization record** — a row may only be inserted after a dated entry is recorded here. The rule is not relaxed; only its storage moves.
- **Do not weaken the failure condition.** If the vocabulary becomes cheap to add to, the ratio must become expensive to cross (see §6).

### Two factual corrections `ADR-001` needs regardless of this proposal

These are independent of the catalog decision and should be fixed on their own merits, since an ADR outranks a feature doc and stale ADR text has already cost this project four releases once (`ADR-001`'s own "Sequencing — BOTH GATES CLEARED" section documents that lesson at `:431`).

1. **`ADR-001` Consequences: *"Note cards display Domain Context as their single badge."* This is not implemented.** `frontend/lib/domain-context.ts:14-16` defines `getDomainContextLabel`, and a repo-wide grep finds **exactly one call site — line 33 of the same file**, inside the subject-collision helper. No card, page, or component renders a Domain Context label. A sweep for raw `domainContext` interpolation in JSX finds none; the only non-authoring frontend references are retry-stash plumbing (`app/library/page.tsx:145,1369`, carrying the value through a bulk-generation failure banner) and two `null` initialisers (`app/study/study-page-client.tsx:79`, `app/onboarding/page.tsx:1336`).
2. **The governance claim that adding a value *"permanently widens a vocabulary that learners see."* Learners never see it.** All three surfaces rendering `DOMAIN_CONTEXT_OPTIONS` are curator-gated: `components/notes/note-editor-form.tsx:474-481` behind `showAuthoringMetadataFields`, `components/notes/private-note-detail-page-client.tsx:2206-2216` behind `canEditAuthoringMetadata`, `components/notes/bulk-generation-page-client.tsx:544-552` behind `isTeacherOrAdmin`. There is no `?domain=` filter parameter and no `domain_context` predicate in either library repository — both only *project* the column.

**Both corrections cut in the proposal's favour and are recorded honestly for that reason.** Domain Context is entirely curator-facing, so (a) adding a value is *cheaper* than the ADR claims — no learner-visible vocabulary widens — and (b) unlike `v0.83.0`'s `?audience=` work, **there is no public link contract to break**. The surviving governance argument is the 8:27 ratio and prompt-behaviour stability. That argument is untouched by these corrections and is the real one.

---

## 4. Hidden responsibilities tied to the enum

Ordered worst-first *within* each class. The "silently changes behaviour" class is the dangerous one, exactly as the brief anticipated.

### Class A — would silently change behaviour

**A1. `isQuantitativeContext` substring-matches the domain label to decide prompt content.**
`backend/src/main/java/com/studysnap/backend/service/impl/OpenAiLlmStudyPackService.java:1633-1661` lowercases a haystack that includes `effectiveAuthoringDomain(context)` and tests it against `QUANTITATIVE_KEYWORDS` (`:147-155`, 48 keywords including `engineering`, `math`, `mathematics`, `mechanics`, `physics`, `accounting`, `finance`, `ratio`, `formula`). The boolean drives `buildComputationGuidance` at **seven** call sites: `:272, 731, 761, 806, 840, 890, 920`.

Today's coupling, verified against the eight labels: `Engineering Mathematics`, `Engineering Sciences`, `Civil Engineering` all match (`engineering`); `Professional Practice & Regulation`, `General Education`, `Professional Education`, `Nursing`, `Accountancy` match none — note that `Accountancy` does **not** contain `accounting`.

**The realistic failure is a false negative.** Plausible future domain names — `Hydraulics & Water Resources`, `Geotechnical Engineering` (matches, via `engineering`), `Surveying`, `Structural Design`, `Technical Economics` — include several that match **no** keyword while covering heavily computational content, silently turning computation guidance **off**. Conversely `General Engineering` would turn it **on** for content that may be qualitative.

**Why this is real rather than theoretical: `:272`.** That call passes `List.of()` for concept hints and `null` for the summary, so the haystack is *the domain label, the subject, and the tags only*. On a first Quick Review generation the domain name is nearly the whole signal. Elsewhere the haystack is wider (subject + tags + concepts + summary), which softens but does not remove the coupling.

Under an enum, whoever edits `DomainContext.java` is a reviewer on a PR that a keyword-coupling test could guard. Under curator-typed catalog names, **a text field in an admin form silently decides prompt content**, with no reviewer and no diff. This is the strongest single technical argument in this assessment, and it is also the strongest argument for holding the prompt-hint deferral: the label already behaves like a prompt hint, invisibly.

**A2. The label *is* the prompt payload, so rename is a content change.**
`StudyPackGenerationContextResolver.java:167-175` → `getLabel()`; `OpenAiLlmStudyPackService.java:1569-1573` emits `"Domain: " + promptValue(authoringDomain)` followed by `DOMAIN_CONSTRAINT` (`:101-102`). Also interpolated into subject-suggestion guidance at `:1617-1625`, which additionally appends a K-12-strand caveat conditioned on the label's text. Consequences: rename changes all future generation for every note carrying the value; and once labels live in a mutable table, a past generation is no longer reproducible from `notes` alone.

**A3. Label resolution must never fall back to null.**
If a catalog lookup misses (archived-then-deleted row, cache miss, race), and the code returns `null`, then `hasDomain` at `OpenAiLlmStudyPackService.java:1570` goes false and **both the `Domain:` line and `DOMAIN_CONSTRAINT` vanish from the prompt**, while `resolveStaticContentCalibration` (`:1663-1672`) silently swaps to the domain-less variant. A note would generate with no domain constraint at all and nothing would log. Any catalog implementation must fall back to the **stored code** as the label, never to null.

### Class B — would break (loudly, which is better)

**B1. The read path is intolerant. `Enum.valueOf` throws; it does not return null.**

**The broadest surface is the entity mapping itself, not the projections.** `NoteEntity.java:41-43` carries `@Enumerated(EnumType.STRING)` on `domainContext`, so **any `NoteEntity` load of a row holding an unknown value throws** — note detail, note edit, copy, generation-context resolution (`StudyPackGenerationContextResolver.java:43,51`), the admin curator page (`NoteApplicableProgramsService.java:83`), account export (`AccountDataExportService.java:90`). `BulkGenerationResultEntity.java:37-39` is the same, read back through `BulkGenerationResultService.java:77`.

The narrower projection paths break independently:
- `NoteLibraryRepositoryImpl.java:588-590` — `enumValue` = `value == null ? null : Enum.valueOf(enumType, value.toUpperCase(...))`. Called for Domain Context at `:443-445` (every row of the **private Library page** projection) and `:467` (the candidate projection).
- `PublicLibraryRepositoryImpl.java:528-530`, called at `:396-398` — every row of the **Public Library / Explore** projection.
- `NoteRepository.java:21-33` — the JPQL constructor projection `COLLECTION_NOTE_PROJECTION` selects `n.domainContext` into `NoteCollectionNoteProjection`; Hibernate's `EnumType.STRING` conversion of an unknown stored string throws as well.

So one `notes.domain_context` value outside the deployed enum **fails the whole paginated query** — not just that note's badge — *and* fails opening the note at all. It is asymmetric across deploys: after a curator adds `GENERAL_ENGINEERING` via a catalog, a rollback to the previous backend breaks Library, Explore, **and note detail** for anyone touching such a note.

**Important framing correction: this is not a latent bug today.** Every write path funnels through `NoteAuthoringMetadataParser.parseDomainContextOrThrow` (`:12-21`), so the column can only hold one of the eight names. The column itself is permissive — `V102__note_domain_context_and_learner_level.sql:2` is a bare `VARCHAR(64)` with **no CHECK constraint** — but nothing can put a bad value there. **The intolerance becomes reachable only if the catalog ships** (or a constant is removed). It is therefore not a fix to schedule independently; it is **part of the catalog's cost**, and it is the part the proposal omits.

**B2. `InvalidDomainContextException`'s message is compile-time derived.**
`backend/src/main/java/com/studysnap/backend/exception/InvalidDomainContextException.java:11-15` builds `"Invalid domainContext. Valid values: …"` from `DomainContext.values()` in a `static final` initialiser. With a catalog this must be built from a live read, or the API returns a 400 listing the wrong set of valid values — a wrong error message being the worst kind, since it teaches the caller something false.

### Class C — design constraints, must be stated or someone will assume otherwise

**C1. Archive has no runtime effect.** The four multi-program guards test `domainContext == null` only: `NoteService.java:1437-1440`, `NoteApplicableProgramsService.java:74-76`, `NoteBulkGenerationService.java:424`, `NoteGenerationService.java:74`. An archived reference is non-null, so `MultiProgramDomainContextRequiredException` stays satisfied and the archived label keeps reaching prompts. Correct behaviour (nothing breaks), zero enforcement power.

**C2. The write-side tolerance protects a different thing than people assume.** `DomainContext.fromString` (`DomainContext.java:27-36`) returns `null` on unknown input; `parseDomainContextOrThrow` converts that null into a 400. So the tolerant `fromString` exists to distinguish *"absent"* (blank → null, legitimate) from *"garbage"* (unknown → 400) on the **input** boundary. It does nothing for the read path. Under a catalog the replacement is a DB existence check on insert/update plus a real FK (§5) — and the read path must become genuinely tolerant, which it currently is not.

**C3. Frontend and stash surfaces carry the code as a typed union.** `frontend/lib/api.ts:460-467` is an 8-member string-literal union; `frontend/lib/bulk-generation-flash.ts:103` and `frontend/lib/note-upgrade-draft.ts:54` restore a stashed value from `localStorage` and cast it. A retired or renamed code sitting in a stash yields a `<select>` with a value matching no option (silently renders blank). Minor, but it is on the catalog's bill.

**C4. Soft coupling in prompt resources — no action needed, worth knowing.** `backend/src/main/resources/prompts/study-pack-v1/developer.txt:28` and `note-generation-developer.txt:60` name `Engineering Mathematics, Nursing, or Accountancy` as *examples* of a Domain. They are illustrative, not a closed list, so they need not change with the vocabulary — but they will read as stale if a value is renamed.

---

## 5. Migration strategy (if the owner proceeds anyway)

Only one shape preserves prompt behaviour byte-for-byte.

### What is stored: keep the **code**, not a UUID

Do **not** convert `notes.domain_context` to a UUID FK. The wire contract is the enum *name* — `.name()` is emitted at six sites (`NoteService.java:1501,1556`; `NoteApplicableProgramsService.java:100`; `DashboardService.java:1000`; `PublicProfileService.java:129`; `BulkGenerationResultService.java:77`) and the frontend type is a union of exactly those names (`api.ts:460-467`). A UUID churns every DTO, the frontend union, two `localStorage` stashes, and the account-data export payload, for **zero** behavioural gain.

### `V118` — additive, reversible, zero rows changed

```
CREATE TABLE domain_contexts (
    code        VARCHAR(64) PRIMARY KEY,
    label       VARCHAR(120) NOT NULL,
    description TEXT,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_domain_contexts_label UNIQUE (label)
);
-- seed the 8 rows with today's EXACT codes and labels
-- 'Professional Practice & Regulation' contains an ampersand: copy it byte-for-byte, it is prompt payload
ALTER TABLE notes ADD CONSTRAINT fk_notes_domain_context
    FOREIGN KEY (domain_context) REFERENCES domain_contexts (code);
ALTER TABLE bulk_generation_result ADD CONSTRAINT fk_bgr_domain_context
    FOREIGN KEY (domain_context) REFERENCES domain_contexts (code);
```

Both columns stay **nullable** — see §6 on why that is non-negotiable. The FK gives referential integrity for free and makes DELETE impossible without a service-layer check.

### What has to change to read it — this is the real work

The table is the small part. Each of these carries the value as `DomainContext` today and must carry `String`:

| Surface | Change |
|---|---|
| `NoteEntity.java:41-43` | **the broad one** — drop `@Enumerated`, field → `String`. Every entity load of the column depends on this; the projections below follow from it |
| `BulkGenerationResultEntity.java:37-39` | same |
| `NoteListItemView.java:20` | `DomainContext getDomainContext()` → `String` |
| `NoteLibraryCandidateProjection.java` | record component → `String` |
| `NoteCollectionNoteProjection.java` | record component → `String` |
| `NoteRepository.java:21-33` | JPQL `COLLECTION_NOTE_PROJECTION` constructor arg type |
| `NoteLibraryRepositoryImpl.java:443-445, 467` | drop `enumValue(DomainContext.class, …)`, carry the raw string |
| `PublicLibraryRepositoryImpl.java:396-398` | same |
| `DataExportResponse.java:49` | `DomainContext` → `String` |
| `StudyPackGenerationContext.java:13` | `DomainContext` → `String` code, with label resolved separately |
| `StudyPackGenerationContextResolver.java:167-175` | `effectiveAuthoringDomain` resolves the label from the catalog, **cached**, falling back to the stored code — never to null (see A3) |
| `NoteAuthoringMetadataParser.java:12-21` | validate against the catalog instead of the enum |
| `InvalidDomainContextException.java:11-15` | message from a live read, not a static initialiser |
| `frontend/lib/api.ts:460-467` | union → `string` |
| `frontend/lib/domain-context.ts:3-16` | constant → fetched catalog, with label map |
| three `<select>` surfaces | loading + error states — reuse the `CourseProgramCombobox` pattern `v0.79.0` established |
| two `localStorage` stashes | tolerate an unknown stashed code |

Plus a **cached** label lookup on the generation hot path (a `Map` refreshed on write, or `@Cacheable`) — a per-generation query is not acceptable there.

**This is precisely `CLAUDE.md`'s full-pressure-test shape**: a single concept touching 3+ surfaces, backend plus several frontend consumers. "One table plus an admin page" understates it by roughly an order of magnitude, and that understatement is itself a reason to defer.

### Rollback

The `V118` half is reversible (drop two constraints and a table). The **de-enumming half is not cheaply reversible**, and the deploy-ordering hazard in B1 is real: once any row holds a code outside the previous deploy's enum, rolling the backend back breaks Library, Explore, **and opening the note itself** — because the failure is in the entity mapping, not only in the list projections. If this ships, ship the read-path tolerance **in a strictly earlier deploy** than the first write of a non-enum value, and do not add a ninth value in the same release as the catalog.

---

## 6. Governance rules

### Code can enforce these

1. **Unique normalized `code` and unique normalized `label`.** Reuse `CourseProgramNormalizationUtils.normalizeForLookup`, exactly as `CourseProgramCatalogService.java:47-51` does, and keep the `DataIntegrityViolationException` → conflict-exception recovery at `:60-69` (it handles the concurrent-insert race correctly and is worth copying verbatim).
2. **Near-duplicate detection before create**, mirroring `GET /course-program-catalog/similar` (`CourseProgramCatalogController.java:33-37`).
3. **`ADMIN`-only write; `USER`-readable list.** Match the `course_program_catalog` split.
4. **No DELETE endpoint at all.** Absence beats a guard. The FK in §5 makes deletion of a referenced row impossible even by hand.
5. **No rename/PUT endpoint in the first release** — §1 Q3.
6. **`active = false` filters the authoring `<select>` only.** It must never filter label resolution on the generation path (A3).
7. **`domain_context` stays nullable, and the `<select>` keeps its empty option.** The existing option text is `"Automatic — based on the program"` (`note-editor-form.tsx:479`, `bulk-generation-page-client.tsx:549`). A catalog invites two mistakes here — a `NOT NULL` column with an `Unclassified` seed row, or an `active` default that makes the field un-emptyable. Either destroys the promotion-backlog marker (§8).
8. **A regression test asserting `isQuantitativeContext` for every catalog label.** Without it, adding a row silently flips computation guidance at seven call sites (A1). This is the non-obvious rule and the one most likely to be skipped.
9. **A count guard mechanising `ADR-001`'s own failure condition.** A test that fails when `domain_contexts` exceeds ~15 rows. Today the ratio is reviewed by a human reading a doc at `/kickoff`; if adding a value becomes cheap, crossing the failure line must become expensive. Otherwise the catalog removes the friction *and* the tripwire in one release.
10. **Usage count as a `GET`-only aggregate** (`count(*) from notes where domain_context = ?`). Do not denormalise it onto the row; a stale counter is worse than a query.

### Only process can enforce this — and it is the one that matters

**A row may only be inserted after a dated owner decision is recorded in `ADR-001`'s ratified value set.** No endpoint can check this. It is the entire content of *"Adding notes is authoring. Adding a Domain Context is architecture."*

**Which is the crux of the whole assessment:** today that rule is enforced by the fact that adding a value requires a code change, a PR, and a deploy. A catalog moves it to the honour system and then asks for "much stricter governance" to compensate. The enum is not overhead standing in the way of the governance the owner wants — **the enum is that governance, already implemented.**

---

## 7. Recommended release shape

### Option A — recommended. *Domain Context vocabulary guidance.* Docs + copy + 0 schema.

Small enough to ship inline (`CLAUDE.md` task routing: frontend-only additions ≤ ~50 LOC and doc writing are Claude Code's, not Codex's).

1. **Amend `ADR-001`'s "Ratified value set (8, as of Release A)" section to carry each value's covered-subject list**, lifted from `08-taxonomy-validation-and-final-recommendation.md:56-67`, citing that document and marking the lists **curator-verified or curator-pending** rather than ratified (`06:64` and `08:70` disclaim them; do not silently promote them). This is the actual fix for the trigger: the mapping existed and was unreachable from the ADR and from the product.
2. **Add a short description under each `<select>`** — the owner's own Description field, delivered as copy. Three surfaces: `note-editor-form.tsx:474-481`, `private-note-detail-page-client.tsx:2206-2216`, `bulk-generation-page-client.tsx:544-552`. Cheapest correct form: extend `DOMAIN_CONTEXT_OPTIONS` in `frontend/lib/domain-context.ts:3-12` with a `description` field and render the selected option's description beneath the control. ~40 LOC total, one shared source of truth, no API change.
3. **Run the empirical tie-break for Engineering Economics** (§2) and record the result — in `ADR-001`'s revision log if it changes the value set, in `RELEASES.md` otherwise.
4. **Fix the two `ADR-001` factual errors** in §3 (the note-card badge claim; the "vocabulary learners see" claim).

**Explicitly out of scope of Option A:** any table, any migration, any new enum value, any change to how the vocabulary is governed, Domain Categories.

### Option B — only if the tie-break in §2 fails. *Add one value.*

**Four files** and a decision: `DomainContext.java` (one constant), `frontend/lib/domain-context.ts:3-12` (one option), `frontend/lib/api.ts:460-467` (one union member), and `DomainContextTest.java:22-31` (one `containsExactly` entry — plus renaming the method, which is literally called `valuesExposeTheEightRatifiedLabels`) — plus the ADR revision-log entry that is the actual authorization. Verified that nothing else pins the set: `frontend/lib/domain-context.test.ts` asserts only the subject-collision helper and does not enumerate the options. No migration, no data change, no backfill, no read-path risk. Prefer a borrowed board-syllabus name over an invented one (`ADR-001:287`).

### Option C — the catalog. Not now. Here is the trigger to watch.

The trigger is **not** "a subject that doesn't obviously fit" — that will recur forever and is what Option A's descriptions answer. The trigger is:

> **The owner has recorded three or more new Domain Contexts in `ADR-001`'s revision log, across three or more separate releases.**

That is the point at which the code change is demonstrably recurring rather than hypothetical, and at which the four-line cost has been paid enough times to compare against the migration in §5. At 8 values with zero additions since 2026-08-03, the recurrence is unevidenced.

**Explicitly out of scope of Option C whenever it comes:**
- the prompt-hint / authoring-guidance field — the owner already defers it; **hold that line**, because A1 shows the label already behaves as an invisible prompt hint, and an explicit one needs an R4-style generate-and-diff read per hint before shipping;
- Domain Categories (§1);
- rename and delete endpoints (§1 Q3);
- any learner-facing exposure of Domain Context, and any `?domain=` filter — there is no such contract today (§3), and creating one converts a curator-only axis into a public link contract of exactly the kind `v0.83.0` spent a release carefully retiring;
- adding a ninth value in the same release as the catalog (§5, rollback).

If Option C ships, `CLAUDE.md`'s **full pressure test** is mandatory, not discretionary — §5's surface table is the "single concept touching 3+ surfaces" case verbatim.

---

## 8. What I could not determine, and what would settle it

**1. Production adoption of the eight values — the single most decision-relevant unknown, and it can move the verdict either way.**
I could not query production. The only database reachable from this session is the local dev container `notelib-postgres` (host port 5435), which holds **123 notes: 121 with `domain_context` NULL and 2 `ENGINEERING_MATHEMATICS`** — a dev fixture, not evidence; `ADR-001` records production at ~5,550 affected notes and 945 public notes.

- If curator-owned public notes are still overwhelmingly null-context, **the vocabulary is barely in use** and the catalog is definitively premature — Option A, and not even Option B until adoption exists.
- If all eight values are in heavy use *and* several thin programs are still stuck on the program-name fallback past the ~10-note floor, **promotion pressure is real** and Option C's trigger is closer than this assessment otherwise concludes.

Settled by two queries: `select domain_context, count(*) from notes where visibility = 'PUBLIC' group by 1 order by 2 desc;` and `select course_program, count(*) from notes where domain_context is null and visibility = 'PUBLIC' group by 1 having count(*) >= 10 order by 2 desc;`

**2. Whether `Engineering Mathematics` actually authors Engineering Economics well.** Settled by the tie-break in §2 — one note, two generations, ~20 minutes. This decides Option A vs Option B and should run before either is scheduled.

**3. How many Engineering Economics notes are planned.** The governance floor is ~10 *notes whose treatment cannot be represented by an existing value*. Settled by the owner's own CE Review Set subject plan. Note that clearing 10 notes is necessary but not sufficient — the second clause is the one that decides.

**4. Whether the `domain_context IS NULL` promotion backlog is still a meaningful signal.** Verified as doctrine, and verified as **not implemented anywhere in code**: the only `domain_context IS NULL` predicates in `src/main` are inside `V104__backfill_level_in_program_notes.sql:30,37,46` and `V105__backfill_high_school_and_strand_notes.sql:32,39,75`. It is a manual query, not a product surface. And it is already overloaded four ways — not-yet-promoted thin program; *deliberately declined* classification (`ADR-001` is explicit that the `High School` and Senior High strand rows appear in that query and must **not** be promoted); learner personal notes (4,645 per `ADR-001`'s sizing, which must never carry a value); and single-program curator notes where the joined catalog name already suffices.

**A catalog is neutral to this marker**, provided §6 rules 7 stays intact — it changes where the vocabulary comes from, not the column's nullability. What destroys it is a server-side default (already forbidden by `ADR-001` constraint 2) or a `NOT NULL` + `Unclassified` seed row (a new hazard a catalog invites). Settled by query 1 above, which would show for the first time how much of that NULL population is genuinely promotable.

**5. Whether "keeping the admin experience manageable" is a multi-user constraint.** `ADR-001`'s query C (2026-08-11) records *"Every curated note in production is admin-owned"* with 885 admin-owned notes. On that evidence there is one curator, and Domain Categories' entire justification is one person's dropdown. Settled by `select count(distinct owner_user_id) from notes where visibility = 'PUBLIC';` and a check of how many accounts hold `TEACHER` or `ADMIN`.

---

# PRODUCTION READ RESULT — 2026-08-17. The verdict holds, and the reason is stronger than assessed.

Ran `domain-context-adoption-read.sql` against production. **§8's decision-relevant unknown is settled, decisively toward "do not build the catalog"** — but the numbers also reframe *why*, in a way this assessment did not anticipate.

## The numbers

| | |
|---|---|
| Curator-owned public notes | **956** |
| …carrying a Domain Context | **121 (12.7%)** |
| Enum values ever used | **4 of 8** |
| Values used ZERO times | `ACCOUNTANCY`, `NURSING`, `PROFESSIONAL_EDUCATION`, `PROFESSIONAL_PRACTICE_AND_REGULATION` |
| Distinct curators | **1** (one `ADMIN`; the one `TEACHER` account owns 4 public notes) |

Values in use: `CIVIL_ENGINEERING` 54, `GENERAL_EDUCATION` 31, `ENGINEERING_MATHEMATICS` 20, `ENGINEERING_SCIENCES` 16.

## ⚠️ The finding that reframes the proposal

**Accountancy holds 154 unclassified public notes while `ACCOUNTANCY` is used zero times. Nursing holds 132 while `NURSING` is used zero times.** That is **286 notes across two programs whose purpose-built Domain Context already exists and has never once been applied.**

**So the taxonomy is not too small. It is unused.** The proposal's premise — *"we will naturally discover subjects that don't fit perfectly into today's fixed list"* — is not what the data shows. The curator has not reached the vocabulary's ceiling; they have not reached the vocabulary at all for half the programs it was designed to serve.

That is the legibility diagnosis in §1, confirmed with far stronger evidence than the covered-subject-lists argument alone provided. **Adding values to a vocabulary with 12.7% adoption and four dead entries would not have helped.** It would have added more unused rows.

Consistent with a curator mid-build rather than a curator blocked: **Civil Engineering is 23% classified** (54 of 232) — the program actively being worked — while Accountancy, Nursing, Architecture and Information Technology are at zero.

## Engineering Economics: the governance floor is not close to met

Query 5 returned **seven** Economics notes, all NULL-context — and **three of them are `Senior High – ABM`**, which is high-school economics, a different subject from engineering economics entirely. The remaining four carry no program at all. There is also one note whose *subject* is `Engineering Mathematics` under Civil Engineering, also unclassified.

**There are effectively zero engineering-economics notes in production today.** `ADR-001`'s floor is ~10 notes whose treatment cannot be represented by an existing value; the count is 0. The question was raised while *planning* the material, not while hitting a wall in it.

## Revised recommendation

1. **Do not build the Domain Context Catalog.** Not "later" — the read shows the problem it solves is not the problem that exists.
2. **Ship descriptions beside the existing `<select>`** (~40 LOC, zero schema). Now justified by adoption evidence rather than by inference: values go unused partly because nothing states what belongs in them.
3. **Classify the existing backlog before extending the vocabulary.** 286 notes have an obvious home already. If `ACCOUNTANCY` and `NURSING` are still at zero after descriptions ship, the constraint is curator time, not taxonomy — and no schema change fixes that.
4. **Engineering Economics → `Engineering Mathematics`** per `08:57`, confirmed by `ADR-001:279`'s binary test. Revisit only if a real body of notes accumulates whose treatment demonstrably cannot be represented.
5. **Domain Categories: declined, now on evidence.** One curator. The feature organises one person's dropdown.
6. **The two code-level findings still bind any future catalog**, and remain unsettled by data: the `isQuantitativeContext` label substring-match, and the label-is-prompt-payload coupling. Neither is about adoption.


---

# SECOND PRODUCTION READ — quantitative-guidance coverage, 2026-08-17

Ran `quantitative-context-coverage-read.sql`. **This found a larger, live defect than the proposal that surfaced it.**

**463 of 956 curator-owned public notes (48.4%) receive no computation guidance in their generation prompt.**

The mechanism is the point. `isQuantitativeContext` substring-matches against 49 keywords, one of which is `engineering` — so coverage tracks the **program's name**, not its content:

| | notes | missing guidance |
|---|---|---|
| Programs with "Engineering" in the name | 214 | **0 (0%)** |
| Every other named program | 670 | **463 (69%)** |

`Civil Engineering` 197/0 missing. `Education` 146/136. `Architecture` 90/75. `Nursing` 130/106. `Accountancy` 154/60.

**The failing subjects are not marginal:** Nursing `Pharmacology` (14 notes — dosage calculation, high-stakes on the NLE); Accountancy `Income Tax`, `Business Tax`, `Basic`/`Advanced Taxation`, `Budgeting`, `Cash and Receivables`, `Investments`, `Financial Management`, `PPE`; Architecture `Structural Components`, `Building Utilities`.

**And the "subjects rescue it" defence does not hold.** Of ~117 rescued notes in Accountancy and Nursing, **74 (63%) are rescued only by tags** — free text authored per note. So notes that currently pass do so accidentally, and identically-subjected notes diverge on whether a curator happened to type a matching word.

## This reorders the release

The descriptions fix remains correct but is no longer the headline. **Replacing the substring guess with a declared per-value property is**, because it is a live quality defect on roughly half the public catalog, concentrated in every program not named "Engineering" — including both licensure catalogs where arithmetic is most of the exam.

Note what this also says about the original proposal: had the catalog shipped and a curator typed a new domain name, it would have inherited this defect silently. The proposal's own instinct that Domain Context deserves explicit per-value authoring guidance was right — it just belongs as a *declared quantitative property* first, not a free-text prompt hint.
