# guidance.md - NoteLib Feature Context

## Goal

Keep product guidance contextual, lightweight, and non-blocking.

NoteLib guidance currently uses three layers:

1. always-visible micro copy
2. one-time dismissible tips
3. Help Center guide cards + modals

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

Current active one-time tips:

| tipId | Surface | Trigger | Message |
|---|---|---|---|
| `note-detail-generate-study-pack` | Note Detail draft state | always | `Generate a Study Pack to unlock summary, key concepts, and quiz questions from this note.` |
| `copied-study-pack-regenerate-hint` | Note Detail copied ready state | `copiedFromPublic === true` and `studyPackStatus === STUDY_PACK_READY` | `This Study Pack was copied. If the difficulty doesn't match your level, regenerate it to get a version tailored to you.` |
| `note-detail-try-quiz` | Note Detail performance section | always | `Try Quick Review or Challenge Quiz to start tracking your performance on this note.` |
| `sessions-export-hint` | Session History empty state | always | `Complete a quiz session to unlock session review and export — download your results as a PDF for study or sharing.` |
| `public-library-intro` | Public Library | always | `Browse notes created by others. Copy any note into your library to study it in your own workspace — full Study Pack included.` |
| `library-first-note-organization` | Library | notes 1–3 | `Add a subject and tags when editing a note — it makes filtering your library much easier as it grows.` |
| `library-organization-habits` | Library | notes ≥ 5 | `You're building a solid library. Try filtering by subject to find related notes quickly.` |

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
3. `Study Packs & Quizzes`
4. `Export & Sharing`
5. `Student Guide`
6. `Board Exam Guide`
7. `Teacher Guide`
8. `Professional Guide`

Profile-specific guide footers use a shared convention:

- primary CTA stays workflow-specific, usually `Create Note`
- secondary CTA is `Switch Profile` and deep-links to `/profile#profile-type`
- hide `Switch Profile` when the viewer's current profile type already matches the guide

## Maintenance rule

If a workflow, CTA, field helper, or plan gate changes, update the matching micro copy, tip text, and Help guide content in the same change set so guidance does not drift from the product.
