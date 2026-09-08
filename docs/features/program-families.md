# program-families.md - NoteLib Feature Context

## Goal

Program Families reduce repetitive Course / Program(s) curation. They are a productivity shortcut, not a curriculum model: an author can add a family's programs in one action, inspect the resulting explicit selection, and remove any programs that do not apply.

## Authoring behavior

- The shared Course / Program(s) curator control derives families from the existing course-program catalog. `course_programs.program_family_id` is the entire expansion preset; there is no subset table or separate family configuration.
- Selecting a family adds every catalog member of that family. Expansion is a union into the current selection: it preserves hand-picked programs and their order, adds no duplicate ids, and removes nothing.
- Expansion is unconditional. The same family always produces the same member set regardless of the note's Subject, Domain Context, learner level, or any other metadata.
- Added members appear immediately as the control's normal removable chips. Authors may trim the over-selected set before using the surface's existing save action.
- A fully selected family offers no clickable no-op. Catalogs with no families show no family affordance, while programs without a family remain individually selectable.
- The shortcut is available anywhere the shared control is used: Note Editor, Note Detail's inline metadata panel, the Admin Dashboard curation modal, and **Bulk Generate** (`bulk-generation-page-client.tsx:594`, inside its `isTeacherOrAdmin` branch). ⚠️ This list read as three surfaces until `v0.133.0`; Bulk Generate was missing. It was found by anchoring the claim to the four `ApplicableProgramsCombobox` consumers, not by reading a diff — the doc had not changed when the fourth consumer was added.

## Persistence and reads

Family expansion is a save-time authoring pre-fill only. It changes the local explicit program-id selection and relies on each surface's existing full-set `PUT /notes/{id}/applicable-programs` save path. It adds no request, endpoint, schema, or persisted "used family" state.

Only explicit `note_course_program` rows are applicability truth. A note with every member selected is indistinguishable from a note whose programs were selected one by one. Filters, facets, badges, search, and every other read path must never resolve a family into programs.

Families are deliberately allowed to over-select because the author sees and can correct the explicit result before saving. The Course / Program(s) axis means valid applicability, not curriculum coverage; Review Sets communicate completeness.

## Catalog growth

Catalog growth is incremental and authoring-driven. A program is added when real canonical notes are legitimately applicable to it, not to pre-seed a possible curriculum. Programs without a family are normal, and adding or changing family membership is the only way to change an expansion preset.

**Families themselves are created from the admin Course / Program surface (v0.133.0).** Before that, only a program could be added in-app — a *family* needed a database migration, which is why `V106` seeded Engineering and `V142` seeded Education. A family is created **empty**: membership is set on the program, through the existing family field on program create. Duplicate names are rejected case- and whitespace-insensitively, because two identical-looking families would produce two identical-looking expansion shortcuts.

⚠️ **The admin family picker reads the families endpoint; the authoring combobox derives families from the catalog.** Both are correct and the difference is deliberate: the combobox only cares about families that have members, while the admin surface must show a family created moments ago that has none yet. Do not "unify" these by deriving the admin list from the catalog — a newly created family would vanish on refresh, and it is the one the curator is about to assign a program to.

## Anti-drift tripwire

If Program Families acquire subject rules, context rules, learner-level rules, curated subsets, read-time inference, or other curriculum intelligence, the feature has exceeded its responsibility. Remove those rules rather than turning the shortcut into a curriculum engine.
