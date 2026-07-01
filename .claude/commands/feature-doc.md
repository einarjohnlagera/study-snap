---
name: feature-doc
description: Update docs/features/<feature>.md after shipping a behavioral change. The most commonly skipped doc step — feature docs are the source of truth Codex reads at the start of the next prompt. Use after any shipped change that adds, removes, or changes user-visible behavior, API contracts, or business rules.
argument-hint: <feature-name>
---

You are updating a NoteLib feature doc after a behavioral change. The argument is the feature name (matches a file in `docs/features/`).

## Step 1 — Find the File

```bash
ls docs/features/
```

If no doc exists for this feature, create one using the template at the bottom.

## Step 2 — Read Before Writing

Read the current doc in full. Look for:
- Rules that your change superseded — update them in place, don't just append
- Sections that now conflict with the new behavior
- API docs that are no longer accurate

Do not only append. Update existing rules so the doc stays coherent.

## Step 3 — What to Update

| What changed | What to update |
|---|---|
| New UI behavior | Add/update the behavior section |
| New endpoint | Update API section: route, params, response, errors |
| Removed button/feature | Remove or strike the old rule |
| Changed redirect | Update the flow description |
| New business rule | Add as a named rule with rationale |
| Changed default | Update the value and note the change |

## Step 4 — Cross-Check RELEASES.md

The RELEASES.md bullet and the feature doc should tell the same story at different levels:
- **RELEASES.md**: one-sentence "what shipped"
- **Feature doc**: "how it works, what the rules are"

If they conflict, the feature doc is authoritative for current behavior.

## Step 5 — Commit

```bash
git add docs/features/<feature>.md
git commit -m "docs: update <feature> doc after [what shipped]"
```

Do not commit directly to `main`.

---

## New Feature Doc Template

Use when no doc exists yet for this feature:

```markdown
# [Feature Name]

[One paragraph: what this feature is, who it's for, where it lives in the product.]

---

## Behavior

### [Sub-feature or Flow Name]

[Rules in plain language. Bullets for constraints; prose for flows.]

---

## API

### `GET /path`

[Description, params, response shape, error cases]

### `POST /path`

[Description, params, response shape, error cases]

---

## Anti-Drift Rules

- [Rule that must not be violated in future changes]
- [Rule]

---

## Changelog

- **vX.Y.Z** — [What changed]
```
