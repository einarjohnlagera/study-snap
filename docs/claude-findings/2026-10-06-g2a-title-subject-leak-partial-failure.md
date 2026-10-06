# G2(a) prompt-only Study Pack title fix: confirmed partial failure, n=3

**Status: finding, not yet scoped. Owner decision owed (below), no production write by Claude, no
code change by Claude.**

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

## Owner decision owed (not Claude's to make)

Two options, explicitly left open by the reporting session and not resolved here:

1. **Add a deterministic strip for Study Pack titles**, mirroring G2(b)'s approach for the
   generated-note body — e.g. detect and strip a trailing `" in {Subject}"` from the model's title
   field before it's stored, the same way `NoteBulkGenerationService` already overrides the body
   heading with the known topic. Scope note: unlike G2(b), this would need to run somewhere in the
   Study Pack generation path (`OpenAiLlmStudyPackService`), not `NoteBulkGenerationService`, since
   Study Pack titles are generated for every path (bulk and interactive), not just Bulk Generate —
   the single-note-vs-bulk distinction that drove G2(b)'s scoping does not obviously apply the same
   way here and needs its own look before scoping.
2. **Leave G2(a) as the only mitigation and rely on curator review** — the R4 pilot's own curation
   convention already includes "check pack titles for a Subject suffix and correct them at review"
   as the stated fallback for exactly this case.

No action taken on either option. No production write made. No prompt or code change made.

## Verification tier

Read-only production `SELECT` only (3 pack ids, by primary key, confirmed as described above). No
code change, so no test suite run. If option 1 above is scoped into a future release, it needs the
same contract-test discipline `v0.165.0`'s G2(b) fix used (pin the real generation output's shape
before depending on it, don't trust a hand-built fixture).
