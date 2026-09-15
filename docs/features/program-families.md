# program-families.md - NoteLib Feature Context

## Goal

Program Families reduce repetitive Course / Program(s) curation. They are a productivity shortcut, not a curriculum model: an author can add a family's programs in one action, inspect the resulting explicit selection, and remove any programs that do not apply.

## Authoring behavior

- The shared Course / Program(s) curator control derives families from the existing course-program catalog. `course_programs.program_family_id` is the entire expansion preset; there is no subset table or separate family configuration.
- Selecting a family adds every catalog member of that family. Expansion is a union into the current selection: it preserves hand-picked programs and their order, adds no duplicate ids, and removes nothing.
- Expansion is unconditional. The same family always produces the same member set regardless of the note's Subject, Domain Context, learner level, or any other metadata.
- Added members appear immediately as the control's normal removable chips. Authors may trim the over-selected set before using the surface's existing save action.
- A family shortcut has three derived states: `Family · N` when none of its members are selected, `Family · N remaining` when partially selected, and a visible, inert `✓ Family · N` when all members are selected. Removing a member immediately returns the shortcut to its partial state. The full state is status text, not a persisted family selection or an ARIA toggle.
- **Every selected program renders unconditionally, at any viewport width — there is no collapse or "show more" limit.** A `v0.149.0` pass shipped and then removed one: an 18-selected-program acceptance check from the plan was never actually run, and an 8-chip mobile collapse was built on an unverified "measured browser check" claim in its place. Reading the real layout instead: all four consumers (see below) either sit in normal page flow, where scrolling to a Save button below a tall chip row is ordinary mobile behavior, or render inside `AppModal`, whose `flex-1 overflow-y-auto` content region plus `shrink-0` actions row already guarantees the actions stay visible regardless of how much content renders above them (`components/ui/app-modal.tsx`). There is no viewport where hiding a chip is necessary to keep Save reachable, so the collapse was removed rather than kept as unverified insurance.
- The shortcut is available anywhere the shared control is used: Note Editor, Note Detail's inline metadata panel, the Admin Dashboard curation modal, and **Bulk Generate** (`bulk-generation-page-client.tsx:594`, inside its `isTeacherOrAdmin` branch). ⚠️ This list read as three surfaces until `v0.133.0`; Bulk Generate was missing. It was found by anchoring the claim to the four `ApplicableProgramsCombobox` consumers, not by reading a diff — the doc had not changed when the fourth consumer was added.

## Persistence and reads

Family expansion is a save-time authoring pre-fill only. It changes the local explicit program-id selection and relies on each surface's existing full-set `PUT /notes/{id}/applicable-programs` save path. It adds no request, endpoint, schema, or persisted "used family" state.

Only explicit `note_course_program` rows are applicability truth. A note with every member selected is indistinguishable from a note whose programs were selected one by one. Filters, facets, badges, search, and every other read path must never resolve a family into programs.

Families are deliberately allowed to over-select because the author sees and can correct the explicit result before saving. The Course / Program(s) axis means valid applicability, not curriculum coverage; Review Sets communicate completeness.

## Catalog growth

Catalog growth is incremental and authoring-driven. A program is added when real canonical notes are legitimately applicable to it, not to pre-seed a possible curriculum. Programs without a family are normal, and adding or changing family membership is the only way to change an expansion preset.

**Families themselves are created from the admin Course / Program surface (v0.133.0).** Before that, only a program could be added in-app — a *family* needed a database migration, which is why `V106` seeded Engineering and `V142` seeded Education. A family is created **empty**: membership is set on the program, through the family field on program create, or assigned, changed, and cleared later through the ADMIN-only `PATCH /course-program-catalog/{id}` endpoint added in v0.149.0. Duplicate names are rejected case- and whitespace-insensitively, because two identical-looking families would produce two identical-looking expansion shortcuts.

The Health Sciences and Accounting families are populated through this generic create-and-reassign mechanism as a post-deploy owner operation. Their production membership is data, not seed logic in the application or migration.

`course_programs.is_active` controls whether a catalog program can be newly selected for authoring **on the Applicable Programs axis specifically** — `ApplicableProgramsCombobox` (`applicable-programs-combobox.tsx`) is the only surface this filters. New programs are active by default. Setting a program inactive retires it from new Applicable-Programs authoring without deleting the catalog row or changing existing Notes: an inactive program already present in a Note's explicit selection continues to resolve as a normal removable chip, and does not count as a family member for expansion. **⚠️ It does NOT reach the separate, legacy singular `courseProgram` free-text field** (`use-course-program-catalog.ts`'s `useCourseProgramCatalogNames`, feeding `course-program-combobox.tsx`'s suggestion list on onboarding, profile, and both note surfaces) — a `v0.149.0` cold-agent pressure test found that surface still offers a retired program's name as a suggestion. That field already accepted arbitrary free text before this release (`allowCustom: true`), so catalog membership was never enforced there and this is a pre-existing gap this release did not widen — but do not read the sentence above as covering it. Filtering that list too is a separate product decision, not assumed here.

⚠️ **The admin family picker reads the families endpoint; the authoring combobox derives families from the catalog.** Both are correct and the difference is deliberate: the combobox only cares about families that have members, while the admin surface must show a family created moments ago that has none yet. Do not "unify" these by deriving the admin list from the catalog — a newly created family would vanish on refresh, and it is the one the curator is about to assign a program to.

⚠️ **The shared `GET /course-program-catalog` response is deliberately unfiltered.** Both the admin catalog-management table and the authoring combobox's raw fetch receive active and inactive rows. Active-only filtering belongs exclusively to `applicable-programs-combobox.tsx` at the points where it offers a new individual selection or derives a family expansion. This parallels the families-endpoint asymmetry above: the admin table needs the full lifecycle view, while only the authoring candidate control needs to exclude retired choices. Do not move this filter into the repository or shared endpoint.

## Known limitations

- **A fully-selected family's chip count can shrink silently if a member is later retired.** The inert `✓ Family · N` count is `memberIds.length` recomputed from the live, active catalog on every render — if a family that was full (`✓ Health Sciences · 3`) later has one member set `is_active = false`, the same chip re-renders as `✓ Health Sciences · 2` on next load, with no signal that a member was dropped rather than that the family shrank by design. Unreachable for this release's actual data (neither fused row is a family member; Engineering and Education have no inactive members) and untested — noted so a future retirement doesn't reintroduce it as a surprise.
- **The "no artificial chip limit" conclusion above is derived from reading `AppModal`'s CSS, not from an actual device/browser render.** The reasoning is a deterministic property of CSS flexbox (`flex-1 overflow-y-auto` + a `shrink-0` sibling cannot let the sibling be pushed off-screen by the flex-1 child's content), not a guess, but it has not been eyeballed on a real phone. If a future layout change touches `AppModal` or any of the four consumers' surrounding structure, re-verify this holds rather than assuming it still does.

## Anti-drift tripwire

If Program Families acquire subject rules, context rules, learner-level rules, curated subsets, read-time inference, or other curriculum intelligence, the feature has exceeded its responsibility. Remove those rules rather than turning the shortcut into a curriculum engine.
