# /feature-doc — Feature Doc Update

Update `docs/features/<feature>.md` after shipping a behavioral change.

This is the most commonly skipped doc step. RELEASES.md tracks *what shipped*; feature docs track *how the feature works* — they are the source of truth Codex reads at the start of the next prompt for that feature. Skipping this update causes the next Codex prompt to work from stale rules.

---

## When to Run

Run after any shipped change that:
- Adds, removes, or changes a user-visible behavior
- Changes an API contract (new field, new endpoint, removed param)
- Changes a business rule (new gate, new default, new restriction)
- Changes a UI flow (new route, new modal, new redirect)

Skip if the change is purely internal (refactor, test, logging) with no behavioral effect.

---

## Step 1 — Identify the Feature Doc

```bash
ls docs/features/
```

Feature docs are named after the domain: `collections.md`, `quiz-sessions.md`, `notes.md`, etc. A change may touch more than one.

If no doc exists for the feature you shipped, create one (see template at the bottom).

---

## Step 2 — Read Before Writing

Read the current doc before editing. Look for:
- Rules that your change superseded (mark them updated, not just appended)
- Sections that conflict with the new behavior
- Acceptance criteria that are now wrong

Do not just append a new section. Update the existing rules in place so the doc stays coherent.

---

## Step 3 — What to Update

| What changed | What to update in the doc |
|---|---|
| New UI behavior | Add/update the relevant behavior section |
| New endpoint | Update the API section with the new route, params, response shape |
| Removed feature / button | Strike or remove the old rule |
| Changed redirect | Update the flow description |
| New business rule | Add as a named rule with rationale |
| Changed default | Update the default value and note the change |

---

## Step 4 — Also Check RELEASES.md

Confirm the shipped bullet in RELEASES.md matches what you wrote in the feature doc. They should tell the same story at different levels of detail:
- RELEASES.md: one-sentence "what shipped"
- Feature doc: "how it works, what the rules are"

---

## Step 5 — Commit

Feature doc updates can be included in the same commit as the code change, or as a follow-up commit on the same branch. Do not commit directly to `main`.

```bash
git add docs/features/<feature>.md
git commit -m "docs: update <feature> doc after [what shipped]"
```

---

## New Feature Doc Template

Use this when a feature has no existing doc:

```markdown
# [Feature Name]

[One paragraph: what this feature is, who it's for, and where it lives in the product.]

---

## Behavior

### [Sub-feature or Flow Name]

[Rules in plain language. Use bullet lists for constraints; use prose for flows.]

---

## API

### `GET /path`

[Description, params, response shape, error cases]

### `POST /path`

[Description, params, response shape, error cases]

---

## Anti-Drift Rules

[Rules that must not be violated in future changes to this feature.]

- [Rule 1]
- [Rule 2]

---

## Changelog

- **vX.Y.Z** — [What changed]
```
