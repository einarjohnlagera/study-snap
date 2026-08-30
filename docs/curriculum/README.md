# docs/curriculum/

Artifacts for designing and rebuilding **Review Sets** — the board-exam curricula (CE, ALE, LET,
CPALE, PNLE) that the curator builds and publishes.

| File | Role |
|---|---|
| `review-set-workbook-spec.md` | how a strategist's proposal becomes a working workbook; the sheet contract; the layout rules and why they are not cosmetic |
| `review-set-reshape-read.sql` | read-only production queries that gather the inputs a strategist needs: current shape, the benchmark set, the ready-to-add pool, and the overlap map. Parameterised by collection id — works for any Review Set |
| `build_review_set_workbook.py` | the builder — takes a TSV of rows, emits the .xlsx. No per-set logic |
| `<set>.tsv` | the source rows for one Review Set. **Edit this, then regenerate** — never hand-edit the workbook |
| `<set>-target-shape.xlsx` | the generated deliverable the curator builds from |

The strategist-facing half is a paste-ready GPT module at
`docs/gpt-contexts/REVIEW_SET_SHAPING_CONTEXT.md`. It and the spec here are two halves of one
contract — the TSV columns are the seam between them, so a change to either needs a check of the
other.

**⚠️ This directory is not covered by the kickoff step-8 scan**, which walks `docs/claude-plans/`
and `docs/claude-prompt/*-out/` only. Nothing here needs a Backlog Index row today, because these
are durable working tools rather than planning documents with open loops. **If a file lands here
that carries an unrun query or an open decision, it needs a row like any plan file** — index it
rather than relying on this note.
