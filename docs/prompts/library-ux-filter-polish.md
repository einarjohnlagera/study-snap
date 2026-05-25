Prompt mode: Long

Use the following docs as the source of truth:
- AGENTS.md
- docs/product/ROADMAP.md
- docs/features/private-library.md
- docs/features/public-library.md

---

## TASK

Fix three library UX bugs (clipped "Browse all" buttons, Course/Program filter clutter in the public library, and a broken bottom sheet on mobile) and apply consistent polish across both libraries.

## GOAL

Users should be able to browse and filter both libraries without filters getting clipped by scroll overflow, without the filter panel being pushed off-screen when the mobile keyboard opens, and without the public library filter sidebar showing four chip-row sections that overwhelm the panel.

## CONTEXT

These are frontend-only polish changes. No backend changes, no new API calls, no new DB migrations. All changes are in:
- `frontend/components/ui/app-modal.tsx`
- `frontend/components/notes/library-sheet-modal.tsx`
- `frontend/app/library/page.tsx`
- `frontend/components/notes/public-library-page-client.tsx`

**Do not:**
- Add new state or backend calls
- Change quiz, study pack, or note generation flows
- Modify filter logic (which notes are matched) — only presentation

**Anti-drift notes from AGENTS.md:**
- Do not hardcode upgrade copy — always use `getUpgradeCtas`. (Not applicable here, but do not add any upgrade CTAs.)
- No new analytics events unless the AnalyticsEventType enum is updated first.

---

## REQUIRED CHANGES

### 1. `AppModal` — add `variant="sheet"` for bottom-sheet layout

Add a `variant?: "default" | "sheet"` prop (default: `"default"`).

When `variant === "sheet"`:
- Backdrop class: `"motion-fade-enter fixed inset-0 z-50 flex items-end justify-center sm:items-center bg-black/55 px-0 sm:px-4"` (note: `items-end` on mobile, `items-center` on desktop; `justify-center` on both so panel is horizontally centered when narrower than viewport; NO horizontal padding on mobile so full-width panel can span the edges)
- Panel base class replaces the default entirely (do not concatenate with the default):
  ```
  motion-modal-enter flex w-full max-w-full flex-col overflow-hidden max-h-[85dvh] rounded-t-2xl rounded-b-none border border-border bg-background p-4 shadow-xl transition-transform duration-200 dark:bg-zinc-900 sm:max-h-[90dvh] sm:w-[90%] sm:max-w-[420px] sm:rounded-xl sm:p-5
  ```
  Then append `panelClassName` on top, as today.

When `variant === "default"` (or omitted), behavior is IDENTICAL to the current code — no changes for default callers.

### 2. `LibrarySheetModal` — use `variant="sheet"`, fix panelClassName

- Pass `variant="sheet"` to `AppModal`.
- Update `panelClassName` to only the desktop overrides: `"sm:max-w-xl"` (the sheet variant's base class already handles mobile; this override just makes the desktop panel a bit wider than the default `max-w-[420px]`).
- Update the content scroll div: change `max-h-[58vh]` → `max-h-[58dvh]` and `sm:max-h-[55vh]` → `sm:max-h-[55dvh]`.

### 3. Private library (`frontend/app/library/page.tsx`) — move "Browse all" to section headers

**Subjects section:**
- Remove the "Browse all" button from inside the scroll rail (it's currently the last child of the `getFadedScrollRailClassName()` div, after the subject chips).
- In the section header row (`flex items-center justify-between gap-3`), add "Browse all" as a right-side link button using `TEXT_LINK_CLASS_NAME`. It should appear alongside the existing "Reset" button when a subject is selected:
  - No subject selected: right side shows only "Browse all"
  - Subject selected: right side shows `<div className="flex items-center gap-3">` with "Reset" then "Browse all"

**Popular Tags section:**
- Remove the "Browse all" button from inside the scroll rail (currently conditional on `remainingTagCount > 0`).
- In the section header row, add "Browse all" to the right — always visible (opening the full tag selector is always useful). Keep "Clear tags" when `selectedTags.length > 0`. Layout: `<div className="flex items-center gap-3">` with "Clear tags" (conditional) then "Browse all".

Do NOT move the "Browse all" button from the Filter (readiness) rail — that section does not open a modal selector, so there is nothing to browse.

### 4. Public library (`frontend/components/notes/public-library-page-client.tsx`)

#### 4a. Remove inline Course/Program chip row

Remove the entire inline Course/Program chip section from the filter sidebar — this is the block guarded by `{availableCoursePrograms.length > 0 ? (...)  : null}` that renders the `"Course / Program"` label and its horizontal scroll rail with chips.

Also remove:
- The `courseProgramSelectorOpen` state (`useState` at the line that declares `const [courseProgramSelectorOpen, setCourseProgramSelectorOpen]`)
- The `useEffect` that syncs `courseProgramDraft` when `courseProgramSelectorOpen` opens (the effect that checks `if (courseProgramSelectorOpen) { setCourseProgramDraft(...) }`)
- The `LibrarySheetModal` for `courseProgramSelectorOpen` (the one titled `COURSE_PROGRAM_SELECTOR_TITLE` near the bottom of the file)
- The `visibleCourseProgramChips` and `remainingCourseProgramCount` computed values that were only used by the inline chip row

Keep: `selectedCourseProgram`, `courseProgramDraft`, `setCourseProgramDraft`, `availableCoursePrograms`, `courseProgramCounts`, `courseProgramPriorityComparator`, `displayedCoursePrograms`, `filteredModalCoursePrograms`, `courseProgramSearchQuery`, `setCourseProgramSearchQuery`. These are all used by the existing `courseProgramSelectorOpen` modal content — after this change, that content moves into More Filters (see 4b).

#### 4b. Add Course/Program to More Filters modal

Inside the `filterSheetOpen` `LibrarySheetModal` body, add a Course/Program section ABOVE the existing Source section. Pattern: search field + selected chip indicator + scrollable chip list, identical to how the private library does it in its More Filters modal.

Clicking a course program chip inside More Filters applies immediately (sets `selectedCourseProgram` + calls `replacePublicLibraryFilters` with the new value + calls `setRecentCoursePrograms`). No Apply button needed — consistent with how Source checkboxes apply immediately in the same modal.

Add a sync effect: when `filterSheetOpen` opens, reset `courseProgramDraft` to `selectedCourseProgram` and clear `courseProgramSearchQuery`. (Same pattern as the removed `courseProgramSelectorOpen` effect.)

#### 4c. Move "Browse all" in Subjects to section header

- Remove "Browse all" from inside the Subjects scroll rail (currently conditional on `remainingSubjectCount > 0`).
- Add to the Subjects section header row, same as the private library change: "Reset" (when subject selected) + "Browse all" in a flex div on the right.

#### 4d. Update More Filters badge dot

The badge dot on the "More Filters" button (`hasActiveSourceFilters`) should also activate when Course/Program is set:
```tsx
{(hasActiveSourceFilters || selectedCourseProgram !== ALL_COURSE_PROGRAMS) ? (
  <span className="absolute right-2 top-2 h-2 w-2 rounded-full bg-blue-600 dark:bg-blue-400" aria-hidden="true" />
) : null}
```

---

## ERROR STATES

These changes are purely presentational — no async operations, no API calls. The only failure modes are:

- `variant="sheet"` applied to a non-sheet caller — impossible if default remains unchanged
- `courseProgramSelectorOpen` still referenced after removal — TypeScript will catch; verify no remaining references compile
- `visibleCourseProgramChips` / `remainingCourseProgramCount` still referenced after removal — TypeScript will catch

---

## TESTING

- After changes, the private library filter sidebar "Browse all" for Subjects and Tags appears in the section header row, not clipped inside the scroll rail
- After changes, the public library filter sidebar has NO inline Course/Program chip row — only For, Subjects, Popular Tags inline sections
- More Filters in public library contains Course/Program section above Source
- Clicking a Course/Program chip inside More Filters immediately filters notes and URL updates
- More Filters badge dot shows when Course/Program is active
- Opening a `LibrarySheetModal` on mobile (simulated narrow viewport) shows the modal as a bottom sheet anchored to the bottom of the screen, not centered
- Mobile keyboard open while modal is open does not push the modal off-screen (test by opening tag selector modal, then tapping the search field inside)
- Desktop: `LibrarySheetModal` still appears as a centered modal (sm:items-center)
- All other `AppModal` usages (not `LibrarySheetModal`) are unaffected — `variant` defaults to `"default"` and their behavior is unchanged
- `clearFilters()` in public library still resets Course/Program (it already calls `setSelectedCourseProgram(ALL_COURSE_PROGRAMS)` — verify this still compiles)

---

## DOCUMENTATION

- Update `RELEASES.md` under `v0.16.0` with a bullet: "Library UX: moved 'Browse all' to section headers, fixed bottom-sheet modal on mobile, moved Course/Program filter to More Filters in public library"
- No feature doc changes needed (presentational polish only)

---

## CLEANUP

- Remove dead `courseProgramSelectorOpen` state, its open effect, and its `LibrarySheetModal` from `public-library-page-client.tsx`
- Remove dead `visibleCourseProgramChips` and `remainingCourseProgramCount` computed values if they were only used by the removed inline section
- Remove `COURSE_PROGRAM_SELECTOR_TITLE` constant from public library if no longer referenced

---

## ACCEPTANCE CRITERIA

- [ ] In private library, "Browse all" for Subjects appears in the section header row; it is NOT inside the horizontal scroll rail
- [ ] In private library, "Browse all" for Popular Tags appears in the section header row; it is NOT inside the horizontal scroll rail
- [ ] In public library, there is NO inline "Course / Program" chip row in the filter sidebar
- [ ] In public library More Filters modal, Course/Program section is present and functional (chips, search, immediate-apply)
- [ ] More Filters badge dot activates when Course/Program is selected (in addition to Source)
- [ ] In public library, "Browse all" for Subjects appears in the section header row; it is NOT inside the horizontal scroll rail
- [ ] `LibrarySheetModal` on mobile viewport anchors to the bottom of the screen (bottom sheet)
- [ ] `LibrarySheetModal` content is not pushed off-screen when the iOS/Android soft keyboard opens (dvh fix)
- [ ] All other `AppModal` usages remain visually unchanged (default variant)
- [ ] TypeScript compiles with no errors
- [ ] RELEASES.md updated

## OUTPUT

Return:
1. All changed files
2. Summary of what changed and why
3. Suggested commit message (format from AGENTS.md)
