# companion.md - Learning Companion Feature Context

## Goal

The Learning Companion is curated guidance attached to an Official top-level Review Set. It gives a future frontend a static, authored layer for how to use the Review Set without adding runtime LLM cost or a new top-level entity.

This feature mirrors the top-level collection concepts documented in `docs/features/collections.md`, especially Primary Review Set and target completion date.

## Data Model

Companion content lives on the existing `note_collections` row:

- column: `note_collections.companion`
- type: nullable `JSONB`
- Java shape: `CompanionContent`

The JSON shape has exactly four sections:

```json
{
  "overview": "string or null",
  "studyStrategy": "string or null",
  "commonMistakes": "string or null",
  "faq": [
    {
      "question": "string or null",
      "answer": "string or null"
    }
  ]
}
```

All fields may be null, and `faq` may be empty. This lets official authors build guidance incrementally.

No Resources, Timeline, Checklist, status, progress, or batch fields belong to the v0.41.0 Companion. Timeline/Checklist work is deferred and must use the already-shipped live readiness/countdown features when it ships.

## Eligibility

Companion is 1:1 with a top-level collection only:

- eligible: `parentCollectionId == null`
- ineligible: child Subject plans with `parentCollectionId != null`

Eligibility is based on parent id only, not child count. A childless top-level Review Set is still eligible.

Setting Companion on a child collection returns `InvalidCollectionRequestException` / `400`.

If a top-level collection carrying Companion is later nested under a Goal, the Companion is cleared because child collections never own Companion content.

## Writes

Only ADMIN users may write or clear Companion content:

- `PUT /collections/{id}/companion`
- `DELETE /collections/{id}/companion`
- `POST /collections/{id}/companion/generate`

The write gate is role-based (`UserRole.ADMIN`). It does not reuse public-profile official-email display checks.

The collection detail page now exposes ADMIN-only authoring for eligible top-level collections through the overflow menu's `Manage Companion` action. The modal edits all four sections as one full replacement request and can clear the Companion back to null. The menu item is hidden for non-admin users and for child Subject plans; backend enforcement remains the security boundary.

AI-assisted generation exists for curator drafts only: ADMIN users can generate one Companion section or all four sections using the PREMIUM LLM tier. Generation never persists on its own and never writes `note_collections.companion`; the existing Save action remains the only write path. There is still no feature gate or quota check on either the manual or generated path. Learner-facing display is documented under "Reads" below.

All "Companion" copy on this page (menu action, modal title/description, remove button, error messages, display eyebrow/heading) resolves through `getCollectionLabels`'s `companionSingular` field, matching the `primarySingular` pattern — currently `"Companion"` identically across every profile, since (unlike "Study Plan"/"Review Set"/"Lesson Plan") Companion is a fixed feature name, not a synonym for the collection noun.

## Reads

Companion is surfaced on existing collection detail responses:

- `GET /collections/{id}`
- `GET /collections/{id}/goal`

There is no separate read endpoint.

The collection detail page renders Companion content in both top-level view branches when renderable content exists. Overview, Study Strategy, Common Mistakes, and FAQ answers use the shared `SummaryMarkdown` renderer. Empty individual sections are skipped, and the whole card is hidden when the Companion is null or only contains empty draft fields.

## Publish And Adopt

Publishing a top-level collection does not need special Companion cascade logic. Companion lives on the same parent row as `visibility`, so publishing the parent preserves and exposes that row's authored content. Child publish cascade does not copy Companion to children.

Adoption intentionally differs from target completion date:

- genuine cross-owner adopt (`source.ownerUserId != newOwnerId`): copy Companion unchanged to the new collection row
- same-owner self-copy/re-adopt (`source.ownerUserId == newOwnerId`): leave Companion null on the fresh row

This rule applies to both standalone top-level leaf Review Sets and Goal adoption.
