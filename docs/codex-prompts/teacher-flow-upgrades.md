# Teacher Flow Upgrades — Codex Prompts

Five sequenced Codex prompts. Run them as separate PRs in this order; each is independently shippable.

## Scope decision (locked)

Decided **Option B** on 2026-05-21:
- **v0.15.0** ships **Prompts 1–2** (Teacher Plan Accessibility + Exam Builder UX Polish). Both already fit the documented v0.15.0 scope in `docs/product/ROADMAP.md`.
- **v0.15.1 — Teacher Power Features** ships **Prompts 3–5** (Question Count Control + Custom DOCX Header + Multiple Exam Versions). All three are net-new features that would expand v0.15.0 if bundled in. Planned section added to `docs/product/ROADMAP.md`.

Prompts 3, 4, and 5 below already point their `DOCUMENTATION` sections at v0.15.1; do not retarget them at v0.15.0.

**Recommended execution for v0.15.0 (Prompts 1 and 2):**
- Two parallel Codex sessions, two separate PRs. They are mostly orthogonal — only `RELEASES.md` v0.15.0 and `docs/features/teacher-flow.md` are shared touchpoints, and both are trivial markdown merge conflicts.
- Do NOT combine them into one mega-prompt; each prompt is sized for a focused review.

**Recommended order (independent of scope split):**
1. Teacher Plan Accessibility (foundation — unlocks the "unlimited exports on Plus" copy that later prompts can lean on)
2. Exam Builder UX Polish (pure UX, low risk)
3. Question Count Control on Generate Quiz
4. Custom DOCX Header
5. Multiple Exam Versions (A/B/C)

---

## PROMPT 1 — Teacher Plan Accessibility

```
Prompt mode: Long

Use the following docs as the source of truth:
- AGENTS.md
- docs/product/PLANS.md (section: "Teacher Profile — Export Override")
- docs/product/ROADMAP.md (v0.15.0)
- docs/features/teacher-flow.md

---

## TASK

Implement the Teacher Profile DOCX Export Override that is already documented in PLANS.md but not yet enforced in code.

## GOAL

A Filipino teacher on the Plus plan (₱179/mo) should be able to export DOCX quizzes without hitting a 15/mo wall, since exporting is the terminal action of their workflow. DOCX exports cost zero LLM, so the override has no margin impact and removes the single biggest accessibility blocker for the Teacher audience.

## CONTEXT

PLANS.md section "Teacher Profile — Export Override" is the locked design. Honor it exactly:

| Plan | Standard export | Teacher export |
| --- | --- | --- |
| Free | 2 / month | 10 / month |
| Plus | 15 / month | Unlimited |
| Pro | Unlimited | Unlimited (unchanged) |

This override applies to **DOCX exports only**. PDF exports continue to use standard plan limits — PDF is the student-facing format and does not need an override.

Teacher status is user-declared via `profileType == TEACHER` (honor system, no external verification per PLANS.md).

Anti-drift rules to honor:
- **Upgrade CTA Rule** (AGENTS.md): use `getUpgradeCtas(currentPlan)` from `frontend/src/config/plans.ts`. Add a Teacher-context variant for the Teacher Plus user so they do NOT see "Upgrade to Pro for unlimited exports" (that message is no longer true for them — Teacher Plus already has unlimited DOCX exports).
- **Paid Upgrade Prompt Rule**: paywall messaging must explain the specific blocked action.
- **Pricing Rule**: backend pricing API drives pricing display; this override is enforced backend-side.

## REQUIRED CHANGES

[Backend]
- **Counter split (required, design-locked):** Today, `UserUsageEntity` has a single `exports_count` column. Since the Teacher override applies to DOCX only (per PLANS.md), split into `docx_exports_count` and `pdf_exports_count`. Flyway migration: add both columns (NOT NULL, default 0), backfill by profile type — per `docs/features/teacher-flow.md` "DOCX export is shown only for Teacher and Admin", so existing `exports_count` represents DOCX for `profile_type = 'TEACHER'` or `role = 'ADMIN'` users, and PDF for everyone else. Backfill SQL must join `user_usage` with `users` and route the count accordingly. After backfill verified, drop the old column. Update `UserUsageEntity` and `BillingUsageResetJob` to reset both new counters.
- `StudySnapProperties`: add `freeTeacherMonthlyDocxExportLimit = 10`, `plusTeacherMonthlyDocxExportLimit = -1` (unlimited sentinel), `proTeacherMonthlyDocxExportLimit = -1` (kept separate for clarity even though identical to current Pro). Rename existing export-limit fields to be DOCX-explicit if they currently mix formats.
- Replace `resolveMonthlyExportLimit(PlanType)` with two functions: `resolveMonthlyDocxExportLimit(PlanType, ProfileType)` and `resolveMonthlyPdfExportLimit(PlanType)`. DOCX branches on Teacher profile; PDF does NOT (standard limits for everyone). Both return `Integer` (`null` for unlimited).
- Update all callers and the export endpoints to (a) consult the right limit function based on format, (b) increment the right counter on success. PDF endpoints touch `pdf_exports_count`; DOCX endpoints touch `docx_exports_count`.
- Update Plan & Billing API response to surface both resolved DOCX and PDF limits + remaining counts as separate fields.

[Frontend]
- `frontend/lib/pricing-config.ts`: add the teacher-specific DOCX export display values, gated on profile type. PDF export display values stay shared.
- `frontend/src/config/plans.ts`: `getUpgradeCtas(currentPlan, { profileType })` overload (or a parallel `getTeacherUpgradeCtas`). Teacher-Free → "Unlock more exports — get Plus". Teacher-Plus → focus on quiz generation volume (e.g., "Get more Study Packs & quiz generations with Pro"), NOT exports.
- Plus plan card (landing page + Settings → Plan & Billing): when the viewing user is Teacher OR when Teacher is the marketed audience, surface a callout strip: *"Teachers get unlimited quiz exports on Plus."*
- Settings → Plan & Billing: read and display BOTH the profile-aware DOCX export limit + remaining count AND the standard PDF export limit + remaining count from the backend response (do not compute client-side from raw plan). Label them clearly as DOCX vs PDF.
- Update the export-limit `PaywallModal` source/copy when triggered from teacher DOCX export to lead with the teacher-framed upgrade CTA. PDF-triggered paywalls keep their existing copy.

## TESTING

- Backend unit: `resolveMonthlyDocxExportLimit(PLUS, TEACHER)` returns `null`; `(PLUS, STUDENT)` returns 15; `(FREE, TEACHER)` returns 10; `(PRO, *)` returns null. `resolveMonthlyPdfExportLimit(PLUS)` returns 15 for both Teacher and non-Teacher.
- Backend integration: Teacher Free user attempts 11th DOCX export → 402 with upgrade CTA payload. Teacher Plus user → no DOCX cap. Non-Teacher Plus user → still capped at 15 DOCX. Teacher Plus PDF export → still capped at 15.
- Migration: existing user `exports_count` correctly backfilled into both new counters (treat existing as DOCX bucket; PDF starts at 0).
- PDF export quotas unchanged for all profile types after migration.
- Frontend: `getUpgradeCtas` returns teacher-framed copy for Teacher profile. Plus card surfaces the teacher callout. Settings reflects the resolved limit.
- Snapshot/render test: Teacher Plus does NOT see any "upgrade to Pro for unlimited exports" copy anywhere it previously appeared.

## DOCUMENTATION

- Update `RELEASES.md` v0.15.0 with a "Teacher plan accessibility" bullet.
- Update `docs/features/teacher-flow.md` to document the export override under a new "Plan Accessibility" subsection (link to PLANS.md as source of truth).
- Update `docs/product/PLANS.md`: change the section header status from "v0.15.0+" / proposed to "Implemented" and add an implementation note pointing at `FeatureGateService` and the resolved-limit API response.

## CLEANUP

- Remove any hardcoded "15 DOCX exports / month" or "Upgrade to Pro for unlimited exports" strings that contradict the new Teacher-Plus reality. Search and replace, but keep them for non-Teacher profiles.
- No deprecated patterns to remove.

## ACCEPTANCE CRITERIA

- [ ] Free Teacher: 10 DOCX exports/mo, 11th blocked with Plus-targeted paywall
- [ ] Plus Teacher: unlimited DOCX exports
- [ ] Pro Teacher: unlimited DOCX exports (unchanged)
- [ ] Non-Teacher Plus: 15 DOCX exports/mo (unchanged)
- [ ] PDF export quotas unchanged for all profiles
- [ ] Plus plan card shows teacher callout
- [ ] Teacher Plus never sees "upgrade to Pro for unlimited exports"
- [ ] Backend + frontend tests pass
- [ ] PLANS.md, teacher-flow.md, RELEASES.md updated

## OUTPUT

Return:
1. All changed files
2. Summary of what changed and why
3. Suggested commit message (format from AGENTS.md)
```

---

## PROMPT 2 — Exam Builder UX Polish

```
Prompt mode: Long

Use the following docs as the source of truth:
- AGENTS.md
- docs/features/teacher-flow.md (section: "Generate -> View -> Export", subsection "Exam Builder")
- docs/product/ROADMAP.md (v0.15.0 teacher flow polish)

---

## TASK

Address four concrete friction points in the Exam Builder (`frontend/app/library/exam-builder/page.tsx`) without restructuring the page.

## GOAL

Reduce the cognitive load and workflow breaks that teachers hit when building a combined exam. Teachers should be able to add forgotten notes without leaving the builder, find the balance controls where balancing logically happens, and scan section composition at a glance.

## CONTEXT

This is pure UX polish. No backend changes, no schema changes, no template changes. The four issues:

1. **No way to add notes from inside the builder.** Teachers currently have to cancel, return to Library, re-enter Select mode, lose their work-in-progress sections. Adds the biggest workflow break.
2. **Balance Sections card is in the wrong place.** It sits in the top-right of the "Selected Notes" header — above the sections, where balancing logically happens *after* sections are organized. The card also has 4 lines of explainer prose under the buttons that duplicate the button labels.
3. **Even and Smart Balance use the same `Shuffle` icon.** No visual distinction.
4. **Section breakdown footer is pipe-separated text** ("Section A: 17 questions | Section B: 17 questions | ..."). Hard to scan, doesn't wrap well on mobile. Note "NOTE 1 / NOTE 2" eyebrows inside sections drift on reorder and add visual noise.

Anti-drift rules:
- **Library Rule** (AGENTS.md): `Quiz Ready` is a Teacher/exam-export indicator. Already respected; do not regress.
- **Mobile Button Rule**: no icon-only major actions.
- **Card Interaction Rule**, **Design System — Icons and Buttons**.
- **Shared Share Behavior Rule** does not apply here (no share surface).
- Keep the accessibility fallback: note-level up/down move buttons must remain alongside drag handles per `docs/features/teacher-flow.md`.

## REQUIRED CHANGES

[Frontend, single page: `frontend/app/library/exam-builder/page.tsx`]

A. **Add Notes from inside the builder**
- New `Add Notes` button placed visibly inside the "Selected Notes" header area (or as a sticky tertiary action near the section list).
- Opens a modal listing the user's quiz-ready notes that are not already in the exam. Reuse `listNotes()` + the existing `canIncludeInExam(item)` filter, subtract `selectedNoteIds`.
- Multi-select with quiz-ready note titles, subjects, and question counts.
- **Target section selector inside the modal**: a single-select pulldown showing the current section titles (e.g., "Section A — Core Concepts"). Default to the last section. Teachers building structured exams want to drop notes into the right section without re-dragging afterward — this is the intentional default behavior, not a stub.
- On confirm, fetch the new notes' generated quizzes (`getGeneratedQuiz`), append them as entries on the chosen target section (mirror the existing URL-driven hydration pattern), and update the URL query (`?notes=...`) via `router.replace` so reloads stay consistent.
- Empty state for the modal: "All your quiz-ready notes are already in this exam. Create or generate a new note to add more."

B. **Relocate + simplify Balance Sections**
- Move the Balance Sections card from the top-right of "Selected Notes" header to *below* the section list, just above the sticky export footer.
- Replace the 4 lines of prose under the buttons with a single one-line helper sentence ("Reorganize existing questions across sections without changing their content.") and put the per-mode descriptions in `title=` tooltips (or a small "What's the difference?" disclosure).
- Keep the existing Smart Balance confirmation modal unchanged.

C. **Distinct icons for Even vs Smart Balance**
- Even Balance: `Scale` icon (lucide-react `Scale`) — communicates equal weight.
- Smart Balance: `LayoutGrid` icon (lucide-react `LayoutGrid`) — communicates organized layout / structural balancing. Do NOT use `Sparkles` here — Smart Balance is a deterministic algorithm, not AI generation, and `Sparkles` would misleadingly suggest an LLM call.
- Keep both as outline-variant Buttons; do not change the disabled-state logic.

D. **Polish section breakdown + note labels**
- Footer breakdown: render as a flex-wrapped row of chips, one chip per section ("Section A · 17 Qs"), replacing the pipe-separated text. Use the existing border/bg-muted chip pattern from the codebase.
- Inside section cards, replace the per-note "NOTE 1 / NOTE 2" eyebrow with the note's subject only. Drag order is already visible from the visual position; the numbered eyebrow becomes stale on reorder.
- Keep the question-count chip ("4 assigned") next to each note.

[Out of scope]
- No backend changes
- No template/preset changes
- No DOCX export changes
- No quiz Q&A model changes
- No new analytics events (the existing exam-builder analytics — if any — stay as-is; do not add)

## TESTING

- Add Notes modal: opens, lists only quiz-ready notes not already in exam, confirms selection appends them to the last section, URL `notes=` reflects the new full set.
- Add Notes modal empty state when no remaining quiz-ready notes exist.
- Balance Sections card now rendered below the section list, with prose collapsed and tooltips present on each balance button.
- Even and Smart Balance buttons render with `Scale` and `Sparkles` icons respectively; both reachable via keyboard.
- Footer breakdown renders chips, wraps on viewport `< 640px`.
- Section note items no longer show "NOTE N" eyebrow; subject still visible.
- Mobile (`< 640px`) layout does not break.

## DOCUMENTATION

- Update `RELEASES.md` v0.15.0 with an "Exam Builder UX polish" bullet listing the four changes.
- Update `docs/features/teacher-flow.md`: the "Exam Builder" bullet list under Export should mention the in-builder Add Notes action.

## CLEANUP

- Remove the pipe-joined footer string builder.
- Remove the per-note "NOTE N" eyebrow markup.
- Remove the prose explanation block under the balance buttons (now replaced by single-line helper + tooltips).

## ACCEPTANCE CRITERIA

- [ ] Teachers can add notes to a work-in-progress exam without leaving the builder
- [ ] Balance Sections card sits below the section list, with prose simplified
- [ ] Even and Smart Balance icons are visually distinct
- [ ] Section breakdown renders as chips
- [ ] Per-note "NOTE N" eyebrows removed
- [ ] No backend or DOCX export changes
- [ ] Mobile layout regression-free
- [ ] Frontend test coverage for the Add Notes modal flow
- [ ] RELEASES.md + teacher-flow.md updated

## OUTPUT

Return:
1. All changed files
2. Summary of what changed and why
3. Suggested commit message (format from AGENTS.md)
```

---

## PROMPT 3 — Question Count Control on Generate Quiz

```
Prompt mode: Long

Use the following docs as the source of truth:
- AGENTS.md
- docs/features/teacher-flow.md (section: "Generate")
- docs/product/PLANS.md
- docs/product/ROADMAP.md (v0.15.1 Teacher Power Features)

---

## TASK

Let teachers choose the question count (10 / 20 / 30) when generating a quiz from a note. Plus+ Teacher unlocks 20 and 30; Free Teacher is fixed at 10.

## GOAL

Give teachers control over quiz length so they can match the format expected for short formative quizzes (10), standard chapter quizzes (20), and longer unit assessments (30). Gating 20/30 behind Plus is an honest upsell because higher counts directly increase LLM token cost.

## CONTEXT

Current state confirmed by inspection:
- `GeneratedQuizService` line ~45 hardcodes `TEACHER_QUIZ_QUESTION_COUNT = 10` and uses it at line ~90 (call site) and line ~97 (post-generation validation: `uniqueQuestions.size() != TEACHER_QUIZ_QUESTION_COUNT`).
- The teacher prompt template at `backend/src/main/resources/prompts/study-pack-v1/teacher-quiz-developer.txt` line 2 already uses `{QUESTION_COUNT}` ("Generate exactly {QUESTION_COUNT} NEW multiple-choice questions...") — the placeholder is wired but the value is fixed.

This task removes the constant, plumbs the requested count from the API request through to the LLM call, and updates the post-generation validation to compare against the requested count.

Gating:
- Free Teacher: questionCount must equal 10. Sending 20 or 30 → 402 with Plus-targeted upgrade CTA.
- Plus Teacher and Pro Teacher: 10, 20, or 30 are all allowed.
- Non-Teacher profiles: this control does not appear (teacher flow only).

Anti-drift rules:
- **Paid Upgrade Prompt Rule** (AGENTS.md): the paywall must explain "Plus unlocks 20- and 30-question quizzes" specifically, not generic copy.
- **Upgrade CTA Rule**: use `getUpgradeCtas` with the Teacher variant added in PROMPT 1.
- **Analytics Rule**: if a `teacher_quiz_generated` event already exists, extend it with `questionCount`. Do not add new events that aren't backed by the enum.

This must NOT affect:
- Challenge Quiz, Adaptive Practice, Board Exam, or Long Exam question counts (those are governed by their own configs)
- Study Pack quiz question count (different generation path)

## REQUIRED CHANGES

[Backend]
- Generate-quiz request DTO (the one that drives `generatedQuiz` creation): add `questionCount` (Integer, optional, default 10). Validate `questionCount ∈ {10, 20, 30}`.
- `GeneratedQuizService`: **remove the `TEACHER_QUIZ_QUESTION_COUNT = 10` constant** (line ~45) entirely. Replace all references (line ~90 LLM call, line ~97 unique-questions validation) with the requested `questionCount` value. The post-generation validation must compare `uniqueQuestions.size()` against the requested count, not against 10.
- `GeneratedQuizService.generate(...)` (or the equivalent entry point): accept `questionCount` parameter and pass it through to the LLM prompt builder so the `{QUESTION_COUNT}` placeholder substitutes correctly.
- New exception `QuestionCountNotAllowedForPlanException` (extends the existing exception base used for plan-gated actions), error code `QUESTION_COUNT_NOT_ALLOWED`.
- Plan gating in service: if profile is TEACHER and plan is FREE and `questionCount != 10`, throw the new exception with the upgrade CTA payload. Apply the gate BEFORE the LLM call to avoid wasted tokens.
- `teacher-quiz-developer.txt` already uses `{QUESTION_COUNT}` — no template change required. Verify `teacher-quiz-system.txt` does not contain a conflicting hardcoded "10" reference; update if it does.
- Schema validator (if any) for the LLM JSON response must accept any of the allowed counts (10, 20, 30), not fixed at 10.

[Frontend]
- Teacher mode Note Detail "Generate Quiz" CTA: add a 10 / 20 / 30 selector immediately adjacent (e.g., a small segmented control or select). Default 10.
- For Free Teacher, 20 and 30 are visibly locked (small Plus chip overlay or "Plus" label). Clicking them opens a PaywallModal with the specific blocked-action copy ("Plus unlocks 20- and 30-question quizzes").
- For Plus+ Teacher, all three are selectable.
- Pass `questionCount` to the generate-quiz API call.
- Update the Regenerate confirmation copy if it references count.
- Helper text under selector: "Choose how many questions to generate. Higher counts cover more material."

## TESTING

- Backend unit: validation rejects `questionCount = 5` or `25` with 400; accepts 10/20/30.
- Backend integration: Free Teacher requesting 20 → 402 with `QUESTION_COUNT_NOT_ALLOWED` code and Plus CTA. Plus Teacher requesting 30 → 200, generated quiz has 30 questions.
- Backend: LLM prompt receives correct `{QUESTION_COUNT}` substitution.
- Frontend: selector renders for Teacher profiles only. Free Teacher locked 20/30 click triggers PaywallModal. Plus Teacher generates with the selected count.
- Regression: non-Teacher profiles do not see the selector. Challenge Quiz / other modes unchanged.

## DOCUMENTATION

- Update `RELEASES.md` v0.15.1 with a "Question count control on teacher Generate Quiz" bullet (if no v0.15.1 section exists yet, create one — its planned scope is documented in `docs/product/ROADMAP.md` v0.15.1 "Teacher Power Features").
- Update `docs/product/ROADMAP.md` v0.15.1 section: mark the "Question count control" item with a shipped indicator (e.g., strikethrough + ✅).
- Update `docs/features/teacher-flow.md` Generate section to describe the 10/20/30 selector and the Plus gate.
- Update `docs/product/PLANS.md` Upgrade Ladder — Teacher Profile Variant if the Plus-vs-Free differentiation list needs to mention this.

## CLEANUP

- Remove any hardcoded question count constant in the teacher-quiz generation path that conflicts with the new parameter.

## ACCEPTANCE CRITERIA

- [ ] Free Teacher: only 10 questions allowed (UI shows lock on 20/30, backend rejects)
- [ ] Plus Teacher: 10/20/30 all generate the corresponding count
- [ ] Pro Teacher: same as Plus
- [ ] Non-Teacher: selector does not appear; backend defaults to existing behavior
- [ ] LLM prompt receives the correct count; generated quiz length matches
- [ ] Paywall copy explains the specific blocked action
- [ ] Backend + frontend tests pass
- [ ] RELEASES.md + teacher-flow.md updated

## OUTPUT

Return:
1. All changed files
2. Summary of what changed and why
3. Suggested commit message (format from AGENTS.md)
```

---

## PROMPT 4 — Custom DOCX Header

```
Prompt mode: Long

Use the following docs as the source of truth:
- AGENTS.md
- docs/features/teacher-flow.md (section: "Export")
- docs/features/profile-learning-context.md (profile fields reference)
- docs/features/onboarding.md (profile schema reference)
- docs/product/ROADMAP.md (v0.15.1 Teacher Power Features)

---

## TASK

Let teachers configure a custom header that appears at the top of every DOCX export. Default school name lives on the Teacher profile; per-export the teacher can add a class/section name and toggle date inclusion.

## GOAL

Make the exported DOCX feel like a real teacher artifact — with school name, class/section, and date in the header — so it can be handed out or filed without manual editing. Removes a friction point that pushes teachers to re-edit the DOCX every time before printing.

## CONTEXT

Configured in two places:
1. **Teacher profile (Settings → Profile → Teaching Info, Teacher-only)**: `schoolName` (optional String). Saved via the existing profile-update endpoint. Used as the default header line on every DOCX export.
2. **Per-export override (in `QuizExportModal` and the combined Exam Builder export modal)**: optional `className` input + `includeDate` toggle (default `true`). The `schoolName` from the profile is shown as read-only in this modal (edit on profile).

Rendered header (when present) at the top of the DOCX:
- Line 1: `schoolName` (bold, centered) — if blank, skip the line.
- Line 2: exam/quiz title + optional ` — ` + `className` if provided — bold, centered.
- Line 3: formatted date (locale-aware, e.g., `May 21, 2026`) — only if `includeDate` is true.

Anti-drift rules:
- **Profile Rule** (AGENTS.md): profile changes go through the existing profile update endpoint; do not invent a new schema aggregate.
- **Onboarding Rule**: do not add `schoolName` to the onboarding flow itself — it lives in Settings → Profile, surfaced after onboarding.
- DOCX export must use stored `generatedQuiz` data only — no LLM call (header is local rendering).
- Non-Teacher profiles must not see the `schoolName` field or the header-override modal section.

## REQUIRED CHANGES

[Backend]
- `User` entity (or whichever entity holds profile fields): add `schoolName` (nullable String, max length 120). Add a Flyway migration.
- Profile update DTO + endpoint: accept `schoolName`. Validate length and trim whitespace.
- Single-note DOCX export DTO and combined export DTO (`MultiNoteQuizDocxExportRequest`): add optional `headerOverride` object with fields `className?` (String, max 120) and `includeDate?` (boolean, default true). Server resolves `schoolName` from the user's profile; the client never sends `schoolName` in the override.
- `QuizDocxExportService` (both single and combined paths): when rendering, build a header block at the top of the first page using the resolved fields. Skip lines that are blank or disabled.
- Date format: ISO locale-aware using the user's locale if available, else default to `en-PH` long form (`May 21, 2026`).

[Frontend]
- Settings → Profile: add a "Teaching Info" section, conditionally rendered for Teacher profile only. School name input wired to the existing profile update flow. Helper text: "Shown in the header of every exported DOCX."
- `QuizExportModal` and the combined Exam Builder export modal: when the user is a Teacher, add an "Export details" disclosure section with:
  - read-only `schoolName` preview ("From your profile: [school name]" with an inline "Edit" link to Settings → Profile)
  - `className` text input ("Class or section (optional)")
  - `includeDate` toggle (default on, label "Include today's date in the header")
- Persist the per-export class/date toggle in component state only — no need to save across exports.
- When the teacher has no `schoolName` set yet, show a subtle prompt: "Add your school name in Settings → Profile to include it in headers."

[Out of scope]
- No PDF header (PDF is student-facing)
- No multi-line school address / school logo / branding (single line only, v1)
- No history of past exports

## TESTING

- Backend: profile update sets `schoolName`; whitespace trimmed; > 120 chars rejected.
- Backend: DOCX export with header — produced document contains the school name, class name, and date in the order specified. Re-rendered without each field (school blank, className null, date toggled off) — corresponding lines absent.
- Backend: non-Teacher profile attempting to set `schoolName` — verify whether this is allowed (recommended: allow set, but UI hides; backend stays permissive so future profile-type changes don't lose data).
- Frontend: Teaching Info section visible only for Teacher profile. Save round-trips. Export modal renders the header override section for Teacher; not for other profiles.
- Frontend: clicking "Edit" in the read-only profile preview takes the user to Settings → Profile.
- Snapshot test: exported DOCX header structure for: (a) full triple line, (b) school only, (c) no header at all.

## DOCUMENTATION

- Update `RELEASES.md` v0.15.1 with a "Custom DOCX header for teacher exports" bullet (create the v0.15.1 section if it does not yet exist — its planned scope is documented in `docs/product/ROADMAP.md` v0.15.1 "Teacher Power Features").
- Update `docs/product/ROADMAP.md` v0.15.1 section: mark the "Custom DOCX header" item with a shipped indicator.
- Update `docs/features/teacher-flow.md` Export section: document the schoolName profile field + per-export override.
- Update `docs/features/profile-learning-context.md` (or whichever profile feature doc tracks profile schema) to mention `schoolName` as a Teacher-only optional field.

## CLEANUP

- No deprecated patterns to remove.

## ACCEPTANCE CRITERIA

- [ ] Teacher can set school name in Settings → Profile; persisted across sessions
- [ ] DOCX export renders header with school name (when set), class name (when entered), and date (when toggle on)
- [ ] Non-Teacher profiles do not see the Teaching Info field or the export-modal header section
- [ ] PDF exports unaffected
- [ ] Backend + frontend tests pass
- [ ] Migration added for `schoolName`
- [ ] RELEASES.md + teacher-flow.md updated

## OUTPUT

Return:
1. All changed files
2. Summary of what changed and why
3. Suggested commit message (format from AGENTS.md)
```

---

## PROMPT 5 — Multiple Exam Versions (A/B/C)

```
Prompt mode: Long

Use the following docs as the source of truth:
- AGENTS.md
- docs/features/teacher-flow.md (section: "Export", subsection "Exam Builder")
- docs/product/PLANS.md (Teacher Profile section + Upgrade Ladder)
- docs/product/ROADMAP.md (v0.15.1 Teacher Power Features)

---

## TASK

Add a "Versions" selector (1 / 2 / 3) to the teacher DOCX export modal. When > 1 is chosen, the export contains all versions in a single DOCX, separated by page break, with shuffled question order and shuffled choice order per version. Plus+ Teacher only.

## GOAL

Give teachers a deterministic anti-cheating tool: the same exam content rendered as multiple shuffled versions in one printable packet. Removes the need to manually re-shuffle questions in Word before printing alternate seating versions.

## CONTEXT

Output structure (single DOCX):
- Pages 1..n: Version A — questions in shuffled order, each question's choices also shuffled
- Page break, Pages n+1..2n: Version B — different shuffle of same questions
- Page break, Pages 2n+1..3n: Version C (if 3 versions selected)
- If "With Answers" mode is on, each version's answer key follows immediately after that version's questions (also page-broken).
- Shuffles must be **deterministic**: same generated quiz + same version letter → same shuffle. Use per-version seeds like `"A"`, `"B"`, `"C"` combined with the quiz id (or a stable derivation) so re-exporting produces the same versions.

Plan gating:
- Free Teacher: versionCount must equal 1. Sending 2 or 3 → 402 with Plus-targeted upgrade CTA.
- Plus Teacher and Pro Teacher: 1, 2, or 3 allowed.
- Non-Teacher profiles: selector not shown; backend defaults to 1.

Applies to both:
- Single-note generated-quiz DOCX export
- Combined Exam Builder DOCX export

Anti-drift rules:
- **Paid Upgrade Prompt Rule** (AGENTS.md): paywall copy must say "Plus unlocks multiple exam versions for anti-cheating," not generic upgrade language.
- **Upgrade CTA Rule**: use the Teacher-variant `getUpgradeCtas` from PROMPT 1.
- DOCX export must continue to use stored `generatedQuiz` data only — shuffling is deterministic local logic, no LLM call.
- No new analytics events unless the `AnalyticsEventType` enum is extended; if extended, name and document the event.

## REQUIRED CHANGES

[Backend]
- DOCX export DTOs (single + combined): add optional `versionCount` (Integer, default 1, validation `∈ {1, 2, 3}`).
- New utility / helper for deterministic per-version shuffle: given (questions, versionLetter, quizIdSeed) returns the shuffled question order and the shuffled choice order per question. Choice answer indices must remain consistent (i.e., the answer key must reflect the shuffled positions, not the original).
- `QuizDocxExportService`: when `versionCount > 1`, render each version as a top-level section with `Version A`, `Version B`, `Version C` titles, page break between versions, and shuffled questions/choices using the helper. Answer key in `With Answers` mode follows each version (or all answer keys appear at the end — pick whichever is simpler to implement, but be consistent).
- Plan gating: if `versionCount > 1` and the user is not (Teacher Plus or Teacher Pro), throw `MultipleExamVersionsNotAllowedForPlanException` with the upgrade CTA payload.

[Frontend]
- `QuizExportModal`: add a "Versions" selector (1 / 2 / 3) with default 1. Free Teacher sees 2 and 3 as locked (Plus chip overlay) — clicking either opens a PaywallModal with the specific Plus-targeted copy. Plus+ Teacher: all three selectable.
- Same selector in the combined Exam Builder export modal.
- Helper text: "Generates A, B, C versions with shuffled question and choice order — useful for anti-cheating."
- Pass `versionCount` in the export request.

[Out of scope]
- No PDF version support
- No more than 3 versions (UX simplicity; revisit if data justifies)
- No "preview a version before exporting" — versions are committed at export time

## TESTING

- Backend unit: shuffle helper is deterministic — same inputs produce same output, different version letters produce different shuffles. Choice answer key reflects shuffled positions.
- Backend integration: versionCount = 1 → unchanged single-version DOCX. versionCount = 2 → two version sections with page break. versionCount = 3 → three. Same exam exported twice produces identical DOCX bytes (modulo timestamps).
- Backend: Free Teacher versionCount = 2 → 402. Plus Teacher versionCount = 3 → 200.
- Backend: validation rejects versionCount = 0, 4, negative.
- Frontend: selector renders, Free Teacher locked 2/3 triggers PaywallModal with correct copy.
- Snapshot test: 2-version "With Answers" DOCX has expected structure (Version A questions, Version A answer key, page break, Version B questions, Version B answer key).

## DOCUMENTATION

- Update `RELEASES.md` v0.15.1 with a "Multiple exam versions (A/B/C) for teacher DOCX export" bullet (create the v0.15.1 section if it does not yet exist — its planned scope is documented in `docs/product/ROADMAP.md` v0.15.1 "Teacher Power Features").
- Update `docs/product/ROADMAP.md` v0.15.1 section: mark the "Multiple exam versions" item with a shipped indicator.
- Update `docs/features/teacher-flow.md` Export section: document the Versions selector, the Plus gate, and the deterministic shuffle behavior.
- Update `docs/product/PLANS.md` Upgrade Ladder — Teacher Profile Variant to list multiple exam versions as a Plus+ feature.

## CLEANUP

- No deprecated patterns to remove.

## ACCEPTANCE CRITERIA

- [ ] Free Teacher: versionCount limited to 1 (2/3 locked, paywall on click)
- [ ] Plus Teacher: can generate 2 or 3 version DOCX
- [ ] Same exam + same versionCount → identical shuffle output (deterministic)
- [ ] Each version's choices shuffled; answer key reflects shuffled positions
- [ ] Versions separated by page break in DOCX
- [ ] Both single-note and combined Exam Builder exports support versions
- [ ] PDF export unaffected
- [ ] Backend + frontend tests pass
- [ ] RELEASES.md + teacher-flow.md + PLANS.md updated

## OUTPUT

Return:
1. All changed files
2. Summary of what changed and why
3. Suggested commit message (format from AGENTS.md)
```