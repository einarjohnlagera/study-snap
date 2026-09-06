# guidance.md - NoteLib Feature Context

## Goal

Keep product guidance contextual, lightweight, and non-blocking.

NoteLib guidance currently uses four layers:

1. always-visible micro copy
2. one-time dismissible tips
3. Help Center guide cards + modals
4. persistent inline reference links (`HelpLink`) that deep-link into a specific Help guide

Reference vs. discovery: layers 1 and 4 are **reference-grade** — always present, re-readable (e.g. a user re-learning Milestones next term). Layer 2 (`GuidanceTip`) is **discovery-grade** — a one-time "this feature exists" nudge that never returns once dismissed. Use a dismissible tip only for discovery, never for material users need to re-read.

## Rules

- never block a primary user action with guidance
- dismissed tips should not reappear for the same user/device
- guidance should clarify context, not replace the main UI
- result screens and note-detail surfaces should stay focused even when tips are present

## Layer 1 — Micro guidance

Current always-visible helper copy includes:

| Surface | Field / area | Current purpose |
|---|---|---|
| Note editor | `Subject` | explain organization / Library filtering |
| Note editor | `Course / Program` | explain personalization context |
| Profile | `Learner Level` | explain difficulty / explanation depth |
| Profile | `Course / Program` | explain domain relevance |
| Note detail | quiz actions | explain Quick Review vs Challenge Quiz |

## Guidance Engine

Engine:

- `frontend/lib/guidance-engine.ts`

Types:

- `GuidanceRule` — `{ id: string; priority: number; condition: () => boolean; message: string }`

Functions:

- `pickActiveGuidance(rules: GuidanceRule[]): GuidanceRule | null` — returns the first unseen, condition-passing rule sorted by priority ascending; does not mutate the input array

Rules:

- lower `priority` number = shown first
- a rule is skipped if `hasSeenTip(rule.id)` returns true or `condition()` returns false
- callers are responsible for rendering the returned rule with `GuidanceTip`

## Layer 2 — GuidanceTip

Component:

- `frontend/components/ui/guidance-tip.tsx`

Persistence:

- `frontend/lib/guidance.ts`
- localStorage key prefix: `notelib-guidance-dismissed-`

Analytics (opt-in):

- pass `trackAnalytics` to `GuidanceTip` to fire `GUIDANCE_TIP_SHOWN` once on first impression and `GUIDANCE_TIP_CTA_CLICKED` when the tip's action is used (both carry `{ tipId }` metadata)
- opt-in by design so existing tips emit no analytics noise; used for the v0.28.0 activation funnel (tip impression → CTA click → feature use, where feature use is an existing `*_STARTED` event or `QUIZ_REVIEW_EXPORTED`)

Current active one-time tips. **⚠️ THIS TABLE IS THE FULL SET — it is verified against code, and a tip
added without a row here is drift.** Tips sharing a `pickActiveGuidance` rule set are grouped and carry
their `priority`, because in a rule set **exactly one tip shows** and priority is the only thing ordering
them.

**Dashboard** — one rule set (`dashboardGuidanceRules`, `app/dashboard/page.tsx`):

| tipId | Priority | Trigger | Message |
|---|---|---|---|
| `dashboard-post-completion` | 1 | non-teacher, and a recent ready note has `quizCount > 0` | `Nice work with {topic}. Come back to review it again later — spaced review helps it stick.` (topic is the note's subject, falling back to its title) |
| `teacher-dashboard-intro` | 2 | teacher profile | `NoteLib turns your lesson notes into ready-to-use quiz drafts. Start by creating a note with your lesson content.` |
| `dashboard-review-rhythm` | 3 | non-teacher, has completed a session | `A quick return visit matters: reviewing concepts over time makes recall stronger than one long study session.` |

**Library** — one rule set (`libraryGuidanceRules`, `app/library/page.tsx`):

| tipId | Priority | Trigger | Message |
|---|---|---|---|
| `teacher-library-multi-note-select` | 1 | teacher, not in selection mode, notes ≥ 1 | `Select multiple notes with the checkboxes, then add them to a lesson plan or build an exam from quiz-ready notes.` |
| `library-study-plan-grouping` | 2 | non-teacher, **not in selection mode**, notes ≥ 3 | `Group related notes into a {Study Plan} you can study as one set.` (CTA: `Create {Study Plan}`, trackAnalytics) |
| `library-organization-habits` | 3 | notes ≥ 5 | `You're building a solid library. Try filtering by subject to find related notes quickly.` |
| `library-first-note-organization` | 4 | notes 1–3 | `Add a subject and tags when editing a note — it makes filtering your library much easier as it grows.` |

**Collection detail** — one rule set (`postAdoptGuidanceRules`, `app/collections/[id]/collection-detail-page-client.tsx`):

| tipId | Priority | Trigger | Message |
|---|---|---|---|
| `post-adopt-target-date` | 1 | just adopted, Goal view, no `targetCompletionDate` | `Set a target completion date to see your weekly countdown and daily study budget.` |
| `assessment-covers-whole-plan` | 2 | **not** just adopted, and ≥ **2** quiz-ready notes | `Practice and exams now cover this whole plan, not one note at a time — weak areas are drawn from across it.` |

**Note detail** — `copied-study-pack-regenerate-hint` and `note-detail-quiz-for-someone` share **one** rule set (`noteDetailGuidance`) and one render slot; the other two are independent:

| tipId | Priority | Trigger | Message |
|---|---|---|---|
| `copied-study-pack-regenerate-hint` | 10 | `copiedFromPublic === true` and `studyPackStatus === STUDY_PACK_READY` | `This Study Pack was copied. If the difficulty doesn't match your level, regenerate it to get a version tailored to you.` (CTA: `Regenerate`) |
| `note-detail-quiz-for-someone` | 20 | `studyPackStatus === STUDY_PACK_READY` and no `generatedQuiz` | `You can turn this note into a quiz for someone else — a link anyone can open and answer without an account.` (no action) |
| `note-detail-generate-study-pack` | — | draft state, not generating, not failed, not editing metadata inline | `Generate a Study Pack to unlock summary, key concepts, and quiz questions from this note.` |
| `quiz-tab-full-notes-nudge` | — | Quiz tab active, Study Pack ready, full notes not yet viewed | `Haven't reviewed the full notes yet? Skim the source material before testing yourself.` (CTA: `View Full Notes`) |
| `note-detail-try-quiz` | — | Performance Overview **expanded**, and **zero** Quick Review *and* zero Challenge Quiz attempts | `Try Quick Review or Challenge Quiz to start tracking your performance on this note.` |

**Everywhere else** — standalone tips, no rule set:

| tipId | Surface | Trigger | Message |
|---|---|---|---|
| `generate-quiz-combined-multi-note` | Generate Quiz modal (Note Detail) | always | `Building a quiz for a whole unit? In your Library, choose Combined quiz to pick several notes and share one quiz.` (trackAnalytics) |
| `teacher-docx-export` | Generated Quiz preview | `ADMIN` or `TEACHER` | `Download as DOCX and open in Word or Google Docs — format it your way before distributing to students.` |
| `teacher-note-content-quality` | Note editor, Content field | teacher create mode | `The more detail in your notes, the better the quiz questions. Paste a full lesson outline, not just bullet headers.` |
| `sessions-export-hint` | Session History empty state | always | `Complete a quiz session to unlock session review and export — download your results as a PDF for study or sharing.` |
| `quiz-review-export` | Session Review screen | review loaded | `Export this review as a PDF to study offline or share it — use the Export button on this page.` (trackAnalytics) |
| `public-library-intro` | Public Library | always | `Browse notes created by others. Copy any note into your library to study it in your own workspace — full Study Pack included.` |

**⚠️ TWO ROWS WERE CORRECTED IN `v0.122.0` AND THE CORRECTIONS ARE RECORDED RATHER THAN QUIETLY SWAPPED,
because both overstated a tip's reach.** `note-detail-try-quiz` was documented as firing **always**; it in
fact requires the Performance Overview to be **expanded** *and* **zero attempts on both** Quick Review and
Challenge Quiz — so it is a first-run nudge, not a permanent fixture, and anyone reasoning about it from
this table would have had it backwards. `library-study-plan-grouping` omitted its `!selectionMode` clause.

**⚠️ `generate-quiz-combined-multi-note` REPLACED `teacher-generate-quiz-multi-note` in `v0.110.0`, and the
id change is deliberate.** The old tip rendered unconditionally while naming a `TEACHER`-gated CTA, so most
of its readers were told to do something they could not. Changing the id re-shows the corrected tip once to
everyone who dismissed the false one. The `teacher-` prefix was dropped because the path is not
teacher-gated. **⚠️ Its copy names the Library Create-menu item by its exact label, "Combined quiz" — rename
that control and this tip becomes false.** A test pins the pairing, and `trackAnalytics` is on so a dated
read can tell "nobody was told" from "nobody wanted it".


**⚠️ `note-detail-quiz-for-someone` (v0.122.0) SHARES ITS `pickActiveGuidance` RULE SET AND ITS RENDER
SLOT WITH `copied-study-pack-regenerate-hint`, and both halves of that are load-bearing.** One rule set is
what lets `priority` order them — split into two arrays and both render at once on a copied, quiz-ready
note, a state that genuinely occurs. The shared slot is why the **Regenerate action is gated on the rule
id**: passed unconditionally it would give the announcement a second click target wired to the wrong
handler. **⚠️ It is an ANNOUNCEMENT and must never gain an action button** — the entry point already
exists on the page, and a second one would confound `[CHECKPOINT — due 2026-10-07]`, the read it exists to
make answerable. **⚠️ Its copy deliberately does NOT name the control**, because the two populations reach
the capability through different affordances (the *Quiz for someone* menu item for learners, the *Generate
Quiz* button for teachers), so naming either makes the tip false for the other half of its audience.
**⚠️ Do NOT add a claim about seeing the recipient's score — shared quiz results are graded in memory and
never recorded.**

Within the Library rule set, Study Plan grouping is prioritized for non-teachers as the v0.28.0 activation lever. Its `{Study Plan}` label is profile-aware via `getCollectionLabels` (STUDENT → Study Plan, BOARD_EXAM → Review Set, PROFESSIONAL → Collection).

## Layer 3 — Help Center

Route:

- `/help`

Access:

- authenticated app surfaces
- linked from Settings

Current Help Center structure:

- card grid on the page
- each card opens an `AppModal`
- guide content is rendered as dedicated components, not accordion Q&A lists

Current guide cards:

1. `Getting Started`
2. `Creating Notes`
3. `Bulk Generation`
4. `Study Plans & Collections`
5. `Learning Companion`
6. `Study Packs`
7. `Quiz Modes`
8. `Progress & Study Focus`
9. `Exam Hubs`
10. `Export & Sharing`
11. `Student Guide`
12. `Board Exam Guide`
13. `Teacher Guide`
14. `Professional Guide`

The `Study Plans & Collections` guide (`study-plans` card / `/help#study-plans`) is profile-aware: its labels resolve through `getCollectionLabels` (Study Plan / Review Set / Lesson Plan / Collection) and the terminal-action copy branches for Teacher (build exam — DOCX + shareable links) vs. other profiles (study / generate per note). It is universal — shown for all profile types, not gated.

The `Study Plans & Collections` guide also documents Primary Review Set behavior and the Weekly Countdown / target-date system, including where to set a target completion date and how This Week pacing is derived.

Deep-linking:

- a guide opens directly from the URL hash, e.g. `/help#progress-focus` opens the Progress & Study Focus guide
- the Help page reads `location.hash` on mount and on `hashchange`; opening/closing a card syncs the hash via `history.replaceState` (no scroll jump, no `hashchange` loop)
- hash (not a query param) is deliberate — it avoids the Next.js `useSearchParams` Suspense build de-opt and keeps `/help` statically prerendered
- card ids are the hash targets; the `progress-focus` and `exam-hubs` guides are the deep-link destinations for inline `HelpLink`s

Profile-specific guide footers use a shared convention:

- primary CTA stays workflow-specific, usually `Create Note`
- secondary CTA is `Switch Profile` and deep-links to `/profile#profile-type`
- hide `Switch Profile` when the viewer's current profile type already matches the guide

## Layer 4 — HelpLink (inline reference links)

Component:

- `frontend/components/ui/help-link.tsx`

Behavior:

- renders a small persistent "How this works →" link that deep-links to `/help#{guideId}`
- co-located with a complex feature, paired with a one-sentence inline gist on the surface itself — the link is the depth path, not the only explanation
- never dismissible; reference-grade

Current `HelpLink` placements:

| Surface | guideId | Label |
|---|---|---|
| Progress — Goal Milestones card | `progress-focus` | `How milestones work` |
| Profile — Study Focus / Exam Focus section | `progress-focus` | `How this works` (default) |

The Progress & Study Focus guide documents the three concept-mastery states (mastered / due for review / not started), the six goal milestones, and the honest new-term answer (no reset button; new subjects start fresh, kept subjects carry mastery forward). Mastery is not permanent — a mastered concept decays to "due for review" via spaced repetition, so the mastery % can dip without practice. Keep the guide consistent with the `MILESTONES` predicates in `app/progress/progress-report-client.tsx` and the state logic in `ProgressReportService.resolveConceptState`.

## Maintenance rule

If a workflow, CTA, field helper, or plan gate changes, update the matching micro copy, tip text, and Help guide content in the same change set so guidance does not drift from the product.
