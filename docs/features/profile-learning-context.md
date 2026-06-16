# Profile Learning Context

NoteLib uses two separate profile fields to personalise the learning experience: **Learner Level** and **Course / Program**. These are distinct concerns and must never be merged or conflated.

Account identity fields are documented separately in `docs/features/account-profile.md`. `displayName` is presentation-only, while `username` is the stable public identity used for attribution and public creator links.

Teacher Profile also owns one export-specific default outside the learning context fields:

- `users.school_name` is an optional Teacher-facing `Teaching Info` field in Profile.
- It supplies the school-name line for Teacher DOCX export headers when set.
- It stays out of onboarding and does not affect learner-level or course/program generation context.

## Learner Level

**What it controls:** taker-specific quiz/exam difficulty, explanation depth, vocabulary, and question complexity. It does not level static note or Study Pack content.

**Values:** a fixed set of options (e.g. High School, College, Graduate, Professional). The combobox snaps back to the last valid saved value if the user types an unsupported option.

**Where it lives:**
- `users.learner_level` (backend)
- `Profile > Learning Profile` card — editable with its own `Save Learning Profile` action
- `GET /auth/me` response — returned as part of the user object so the frontend can gate or personalise UI without an extra call

**Where it is used in generation:**
- All taker-specific LLM quiz/exam prompts (`buildLearnerContextBlock`) receive the resolved learner level.
- The exam-question pool keeps learner level for pre-warm and `sameLearnerLevel` serving checks. A missing level is allowed in best-effort generation context because the pool self-corrects for each taker.
- Note-from-topic and Study Pack content prompts deliberately omit learner level.
- Completed accounts are guaranteed to have a saved profile learner level because onboarding step 2 requires it. Legacy accounts with a null level are gated the next time they save their Profile or, for teachers, the next time they open the Generate Quiz modal — no global backfill prompt exists by design.
- Learner level is passed through the backend generation context; it is never derived client-side.

**How it is collected:**
- Onboarding step 2 requires learner level before the first Study Pack flow can continue.
- The `Adjust level` CTA navigates to `/profile?from=dashboard#learning-profile`, which auto-scrolls to the Learning Profile card and enables context-aware back navigation to Dashboard.
- Users can change their learner level at any time in `Profile > Learning Profile`.

**UX rules:**
- Teacher-facing copy reframes the profile value as the default quiz difficulty for material the teacher generates, while non-teacher copy stays in personal learning terms.
- The Learning Profile card must carry `id="learning-profile"` so hash navigation works.
- `/profile` should keep using the shared App Router hash-navigation pattern: native target id plus `HashScrollListener` so direct deep links still scroll after mount.
- After saving, show a toast that frames learner level around future quizzes and exams, not static Study Pack content.
- Inline pill selectors on Quick Review and Challenge Quiz result screens let users adjust learner level without leaving the review flow.

---

## Course / Program

**What it controls:** the depth, vocabulary, terminology, and examples of static note/Study Pack content, plus domain context for quiz/exam scenarios.

**Values:** open text with autocomplete from saved note/profile values. Custom values are saved and fed back into future autocomplete suggestions.

**Where it lives:**
- `users.course_program` (backend, profile default)
- `notes.course_program` (backend, per-note override)
- `Profile > Learning Profile` card — required when saving Learning Profile
- Note Editor — required per-note field; pre-filled from the profile value, validated before save/generate

**Where it is used in generation:**
- Static note and Study Pack prompts receive `courseProgram` through the content-context block without learner level.
- Quiz/exam prompts receive `courseProgram` alongside `learnerLevel` through `buildLearnerContextBlock()`.
- Per-note `courseProgram` takes precedence over the profile default when set.
- Challenge Quiz, Board Exam, and Adaptive Practice must use the same note-first `courseProgram` resolution as Study Pack generation.
- Generate from Topic accepts an optional `courseProgram` in `GenerateNoteFromTopicRequest`; the current draft Course / Program selected in Create Note must be read at submit time, sent on the first generation request, and used as the domain for the generated note. Fall back to the profile value only when the draft value is blank.
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
- Required when saving or generating a Study Pack from a Note; pre-filled from the profile value so the gate rarely blocks users who completed onboarding.

---

## Separation Rule

| | Learner Level | Course / Program |
|---|---|---|
| Controls | Quiz/exam difficulty and explanation depth | Static content depth, vocabulary, terminology; domain examples and scenarios |
| Required in | Onboarding, Learning Profile save | Onboarding, Learning Profile save, Note Editor |
| Optional in | — | — |
| Onboarding | Required | Required |
| LLM use | Quiz/exam context and exam pool | Content context plus quiz/exam domain context |
| Merge? | **Never** | **Never** |

These two fields are passed separately to generation context. Do not combine them into a single field, a single prompt variable, or a single UI input.

---

## LLM Context Builder Rule

All LLM calls must use `StudyPackGenerationContextResolver` instead of inline profile lookups so note-level Course/Program overrides are preserved. Static note and Study Pack prompts use the content-context builder; quiz/exam prompts use `buildLearnerContextBlock()`.

The content context structure (conceptual):

```
Course / Program: {courseProgram | omitted}
Domain constraint: treat the course/program above as the authoritative academic domain.
Content calibration: use Course / Program to set depth, vocabulary, terminology, and examples.
```

The quiz/exam context structure (conceptual):

```
Learner level: {learnerLevel}
Course / Program: {courseProgram | omitted}
Domain constraint: treat the course/program above as the authoritative academic domain. All content, terminology, examples, and question framing must belong to that domain. Do not blend in material from unrelated disciplines.
```

Course/program lines are emitted only when `courseProgram` is set. If it is absent, omit them rather than passing empty strings to the LLM. Content generation must still tolerate a null learner level because learner level is not part of the content block.

---

## v0.12.0 Planned Refinements

- Quiz prompts use `learnerLevel` to select question complexity tier (vocabulary, number of inferential steps, explanation length).
- Course/Program autocomplete suggestions are narrowed by the active note subject so the options feel relevant to what the student is studying right now.
- Helper text on the Learning Profile card adapts dynamically as the learner level combobox selection changes (not only on save).
