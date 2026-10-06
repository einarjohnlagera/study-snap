# G2(a) prompt-only Study Pack title fix: confirmed partial failure, n=3

**Status: measured. A deterministic title strip (the obvious fix) was scoped, measured against
production, and found to NOT be safely buildable — see "Why the deterministic strip doesn't work"
below. Owner decision owed on the remaining option. No production write by Claude, no code change
by Claude.**

## What happened

`v0.165.0`'s G2(a) fix extended the existing Title rule's Course/Program-omission bullet to also
name Subject, in both title-emitting prompts (`developer.txt`, `note-generation-developer.txt`),
specifically to stop Study Pack titles reading "{Title} in {Subject}". At signoff this was
explicitly flagged as unverified: *"G1 and G2(a) are prompt text; automated tests pin file content,
not model behavior... real verification is the owner regenerating a pilot Note's Study Pack"*
(`RELEASES.md`, v0.165.0 Shipped section).

The owner has now done exactly that. Reported by a peer session ("Note Strategist") 2026-10-06,
independently confirmed here via read-only production `SELECT` before acting on it.

## Evidence

Three Notes under `Computing` (Subject: Programming Fundamentals) had their bodies replaced with
the authored canonical text and their Study Packs regenerated in place (same pack ids), all after
`v0.165.0`'s production deploy (2026-10-05T15:21–15:24Z):

| Note | Pack id | `study_packs.title` | `updated_at` | Result |
|---|---|---|---|---|
| Algorithms and Their Properties | `e611a789-50fb-4cb3-b048-7b273013b20e` | **"Algorithms and Their Properties in Programming Fundamentals"** | 2026-10-06T01:10:41Z | **LEAKED** |
| Pointers and References in C++ | `c28b601f-ed39-4fb1-a498-1057e3f542c1` | "Pointers and References in C++" | 2026-10-06T01:10:22Z | Clean (was `"... in C++ Programming"` before the fix — a partial Subject leak) |
| Building and Running a C++ Program | `4b0dd898-c930-4aa6-884d-d0e954805528` | "Building and Running C++ Programs" | 2026-10-06T01:10:32Z | Clean (was clean before too) |

Verified directly against `study_packs` via read-only `SELECT ... WHERE id IN (...)` — matches the
peer session's report exactly, including the `updated_at` timestamps landing after deploy.

A fourth pilot Note ("Propositions and Logical Connectives", Subject: Discrete Mathematics) was not
regenerated and still shows the pre-fix title `"Propositions and Logical Connectives in Discrete
Mathematics"` — expected, not evidence either way.

## What this means

- **The prompt-only fix is not reliable for the title field.** n=3 is evidence, not a rate — do not
  treat 1/3 as "33% failure rate" — but it is a real, reproduced miss, not noise: the leaking Note's
  own body first line is the clean title "Algorithms and Their Properties" with no Subject suffix,
  so the model is diverging from a clean input it was given, in both the body-generation path (fixed
  deterministically by G2(b)) and the title field it's asked to emit directly (not deterministically
  fixed — G2(b) only ever covered the generated-note BODY, never the Study Pack title).
- **G1 is confirmed working on this same batch**: the C++ build commands in the regenerated packs
  render as code, not as `$...$` math (one real sample, same caveat about n=1 applying).
- **G3 did not recur** once the source Note was authored (the unsupported "randomness" claim was
  specific to the generated-outline content G3 was originally found in).
- **G4 remains latent, unchanged** — not in scope, matches the pilot's own prior reading.

## Why the deterministic strip doesn't work — measured 2026-10-06

The obvious fix (mirroring G2(b)): detect and strip a trailing `" in {Subject}"` from
`OpenAiLlmStudyPackService`'s model-generated title before it's stored — a single fix point
(`toGeneratedStudyPackContent`, the only caller of `GeneratedStudyPackContent`'s real constructor
besides the local-dev stub) that would transitively cover every consumer (`StudyPackEntity.title`,
and a brand-new Note's own title in the `createFromText`/OCR-paste flow via `createGeneratedNote` —
confirmed both read the same field, confirmed `applyGeneratedMetadataToNote` never overwrites an
*existing* note's title so that path isn't a second consumer to worry about).

**Measured before recommending it**, the same way G1 was measured at `v0.165.0` kickoff:

```sql
SELECT count(*) FROM study_packs sp JOIN notes n ON n.id = sp.note_id
WHERE n.subject IS NOT NULL AND trim(n.subject) <> ''
AND lower(trim(sp.title)) LIKE '% in ' || lower(trim(n.subject));
-- 3390
```

**3,390 existing Study Pack titles end in `" in {the note's own Subject}"`.** A random 40-row sample
of those is, without exception, legitimate: *"Torsion in Strength of Materials"*, *"Infection
Control and Isolation Precautions in Nursing"*, *"Bloom's Taxonomy in Educational Psychology"* — this
is simply how educational content titles itself; a stripped version of any of these would read
worse, not better. **A blind `endsWith` strip would mangle on the order of 3,390 legitimate titles
to catch however many of the (unknown, almost certainly small) genuine leaks are mixed into that same
population** — this is exactly the Title rule's own "judgment about meaning, not about wording"
distinction (`developer.txt`'s Title section), enforced as a mechanical wording ban, which is the
precise anti-pattern the `v0.96.0` anti-drift rule exists to prevent and that
`bothTitleEmittingPromptsTeachTitleSemanticsRatherThanAWordingBan`'s own doc comment names by name.
**No narrower mechanical rule was found that distinguishes the one confirmed leak** (Subject =
`Programming Fundamentals`, a broad curriculum-container label) **from the 40 sampled legitimates**
(Subject = a specific discipline the title's topic genuinely belongs to, e.g. `Strength of
Materials`, `Nursing`) **using the title string alone** — the distinction is exactly the
container-vs-knowledge judgment the prompt rule already relies on a model to make, not something a
regex can make for it.

**Also, even restricted to an exact-suffix match, this backstop would only ever catch the exact
defect class seen once**: the pre-fix "Pointers and References in C++ **Programming**" (a *partial*
leak — Subject appended with extra words, not the exact Subject string) would not have matched an
exact-suffix check at all. So the strip's real-world catch rate, even ignoring the false-positive
problem, is narrower than "catches G2(a) misses" implies.

**Conclusion: do not build this.** Recorded here as the measurement, not a recommendation — the
Codex prompt this would have needed was never written.

## Owner decision owed (not Claude's to make)

With option 1 above ruled out by measurement, the remaining options are:

1. **Accept G2(a) as the only mitigation and rely on curator review** — the R4 pilot's own curation
   convention already includes "check pack titles for a Subject suffix and correct them at review"
   as the stated fallback for exactly this case. Recommended by elimination, not independently
   argued for here — it's what's left once the deterministic option is ruled out.
2. **A narrower, more targeted heuristic** that specifically detects the broad-curriculum-container
   pattern (e.g. only strip when the Subject matches a known generic/catalog-level label rather than
   a specific discipline) — not scoped here; no obvious mechanical signal for "this Subject is a
   broad container" was identified, and building one is a meaningfully larger effort than this
   finding's own scope.

## Verification tier

Read-only production `SELECT`s only (3 pack ids by primary key for the original finding; one
aggregate count plus a 40-row random sample for the strip measurement above). No code change, no
Codex prompt written, no test suite run.
