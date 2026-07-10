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

Current active one-time tips:

| tipId | Surface | Trigger | Message |
|---|---|---|---|
| `note-detail-generate-study-pack` | Note Detail draft state | always | `Generate a Study Pack to unlock summary, key concepts, and quiz questions from this note.` |
| `copied-study-pack-regenerate-hint` | Note Detail copied ready state | `copiedFromPublic === true` and `studyPackStatus === STUDY_PACK_READY` | `This Study Pack was copied. If the difficulty doesn't match your level, regenerate it to get a version tailored to you.` |
| `note-detail-try-quiz` | Note Detail performance section | always | `Try Quick Review or Challenge Quiz to start tracking your performance on this note.` |
| `sessions-export-hint` | Session History empty state | always | `Complete a quiz session to unlock session review and export — download your results as a PDF for study or sharing.` |
| `quiz-review-export` | Session Review screen (review loaded) | always | `Export this review as a PDF to study offline or share it — use the Export button on this page.` (trackAnalytics) |
| `public-library-intro` | Public Library | always | `Browse notes created by others. Copy any note into your library to study it in your own workspace — full Study Pack included.` |
| `library-study-plan-grouping` | Library | non-teacher, notes ≥ 3 | `Group related notes into a {Study Plan} you can study as one set.` (CTA: `Create {Study Plan}`, trackAnalytics) |
| `library-first-note-organization` | Library | notes 1–3 | `Add a subject and tags when editing a note — it makes filtering your library much easier as it grows.` |
| `library-organization-habits` | Library | notes ≥ 5 | `You're building a solid library. Try filtering by subject to find related notes quickly.` |
| `teacher-library-multi-note-select` | Library | teacher, not in selection mode, notes ≥ 1 | `Select multiple notes with the checkboxes, then add them to a lesson plan or build an exam from quiz-ready notes.` |

The Library tips (`teacher-library-multi-note-select`, `library-study-plan-grouping`, `library-organization-habits`, `library-first-note-organization`) are selected by a single `pickActiveGuidance` rule set so only one shows at a time; Study Plan grouping is prioritized for non-teachers as the v0.28.0 activation lever. The `{Study Plan}` label is profile-aware via `getCollectionLabels` (STUDENT → Study Plan, BOARD_EXAM → Review Set, PROFESSIONAL → Collection).

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
