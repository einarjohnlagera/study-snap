# Profile Learning Context

NoteLib uses two separate profile fields to personalise the learning experience: **Learner Level** and **Course / Program**. These are distinct concerns and must never be merged or conflated.

Account identity fields are documented separately in `docs/features/account-profile.md`. `displayName` is presentation-only, while `username` is the stable public identity used for attribution and public creator links.

## Learner Level

**What it controls:** quiz difficulty, explanation depth, vocabulary, and question complexity in all AI-generated outputs (Study Pack quizzes, Quick Review, Challenge Quiz, Adaptive Practice).

**Values:** a fixed set of options (e.g. High School, College, Graduate, Professional). The combobox snaps back to the last valid saved value if the user types an unsupported option.

**Where it lives:**
- `users.learner_level` (backend)
- `Profile > Learning Profile` card — editable with its own `Save Learning Profile` action
- `GET /auth/me` response — returned as part of the user object so the frontend can gate or personalise UI without an extra call

**Where it is used in generation:**
- All LLM quiz prompts (`buildLearnerContextBlock`) receive the resolved learner level.
- When no learner level is saved, prompts default to college-level complexity.
- Learner level is passed through the backend generation context; it is never derived client-side.

**How it is collected:**
- Deferred from onboarding intentionally. The Dashboard personalization prompt (`Too easy or too hard?` / `Adjust level` CTA) collects learner level after the first Study Pack is generated.
- The `Adjust level` CTA navigates to `/profile?from=dashboard#learning-profile`, which auto-scrolls to the Learning Profile card and enables context-aware back navigation to Dashboard.
- Users can change their learner level at any time in `Profile > Learning Profile`.

**UX rules:**
- Do not add a learner level step to onboarding — this is the settled pattern.
- The Learning Profile card must carry `id="learning-profile"` so hash navigation works.
- `/profile` should keep using the shared App Router hash-navigation pattern: native target id plus `HashScrollListener` so direct deep links still scroll after mount.
- After saving, show a toast: `Learner level updated. Future Study Packs and quizzes will match this level.`
- Inline pill selectors on Quick Review and Challenge Quiz result screens let users adjust learner level without leaving the review flow.

---

## Course / Program

**What it controls:** domain context — examples, terminology, and question scenarios in AI-generated outputs are made relevant to the student's field of study.

**Values:** open text with autocomplete from saved note/profile values. Custom values are saved and fed back into future autocomplete suggestions.

**Where it lives:**
- `users.course_program` (backend, profile default)
- `notes.course_program` (backend, per-note override)
- `Profile > Learning Profile` card — required when saving Learning Profile
- Note Editor — optional per-note field, defaults from the profile value

**Where it is used in generation:**
- Study Pack and quiz generation context blocks include `courseProgram` alongside `learnerLevel`.
- Per-note `courseProgram` takes precedence over the profile default when set.
- Challenge Quiz, Board Exam, and Adaptive Practice must use the same note-first `courseProgram` resolution as Study Pack generation.
- When neither is set, the prompt falls back to subject-only context.

**Autocomplete rules:**
- Suggestions come from `GET /api/course-programs?scope=mine|public`.
- Autocomplete filters in real time: exact/prefix/contains matches are ranked in that order.
- Existing display labels are reused for exact case-insensitive matches to avoid duplicates in the suggestion catalog.
- Custom entries are normalized (whitespace, dash formatting, case) before saving.

**UX rules:**
- `Course / Program` helper text adapts to the selected `Learner Level`:
  - High School → examples: `Grade 12 STEM Track`, `Grade 11 Accountancy`
  - College → examples: `BS Computer Science`, `BS Nursing`, `BS Business Administration`
  - Graduate → examples: `MS Computer Science`, `MBA`, `MD`
- Required when saving `Learning Profile` (alongside `Learner Level`). Shows inline validation: `Please select or enter your course / program.`
- Optional when creating or editing a Note; shown in the `Add details (optional)` collapsed section.

---

## Separation Rule

| | Learner Level | Course / Program |
|---|---|---|
| Controls | Difficulty, depth, vocabulary | Domain, examples, scenarios |
| Required in | Learning Profile save | Learning Profile save |
| Optional in | — | Note Editor |
| Onboarding | Deferred | Deferred |
| LLM use | `learnerLevel` field in context block | `courseProgram` field in context block |
| Merge? | **Never** | **Never** |

These two fields are passed separately to generation context. Do not combine them into a single field, a single prompt variable, or a single UI input.

---

## LLM Context Builder Rule

All LLM calls that personalise output must use `buildLearnerContextBlock(userId)` (or its backend equivalent) to resolve the current `learnerLevel` and `courseProgram` before constructing the prompt. Backend generation paths should use the shared `StudyPackGenerationContextResolver` instead of inline profile lookups so note-level Course/Program overrides are preserved.

The context block structure (conceptual):

```
Learner level: {learnerLevel | "College (default)"}
Course / Program: {courseProgram | omitted}
```

If `courseProgram` is absent, omit the line entirely rather than passing an empty string to the LLM.

---

## v0.12.0 Planned Refinements

- Quiz prompts use `learnerLevel` to select question complexity tier (vocabulary, number of inferential steps, explanation length).
- Course/Program autocomplete suggestions are narrowed by the active note subject so the options feel relevant to what the student is studying right now.
- Helper text on the Learning Profile card adapts dynamically as the learner level combobox selection changes (not only on save).
