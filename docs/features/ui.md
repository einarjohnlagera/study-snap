# ui.md - NoteLib UI Patterns

## Top Bar Utility Grouping

Top-bar utility controls should stay compact and visually separate from primary navigation or CTA actions.

Current theme-control rule:

- desktop top bars use an icon-only inline theme group for `Light`, `Dark`, and `System`
- each icon-only theme button should keep a tooltip so the mode remains clear without visible labels
- mobile top bars use a compact collapsed trigger that expands the same theme options inline
- the expanded mobile theme control should stay anchored to the trigger and render fully on screen

## Public Header Separation

In the public header, utility controls and action buttons should not blur together.

Current grouping:

- navigation links
- theme utility control
- subtle vertical separator
- `Login` and `Get Started`

The separator should be visually subtle and should create spacing without drawing more attention than the actions themselves.

## Motion Principles

NoteLib motion should stay:

- subtle
- fast
- purposeful
- non-blocking

Default motion guidance:

- prefer `150ms` to `250ms`
- prefer ease-out or ease-in-out timing
- animate color, opacity, and small transform shifts first
- avoid large movement, springy motion, or anything that slows study actions

## Shared Motion Tokens

Shared motion tokens live in `frontend/app/globals.css`.

Current shared patterns:

- `motion-surface` for calm surface transitions on cards and similar containers
- `motion-pressable` for lightweight pressed-state feedback on shared controls
- `motion-fade-enter` for non-critical section entry such as result and review surfaces
- `motion-collapse` plus `motion-collapse-inner` for disclosure sections such as the quiz Question Navigator

Use these shared classes instead of introducing one-off durations and easing values in feature components.

## Intentional Motion Use

Current high-value motion use includes:

- theme color transitions
- Question Navigator expand/collapse
- shared button/control press feedback
- non-critical quiz result and answer-review entry

## Intentional Motion Avoidance

Protect focused study flows by avoiding noticeable motion on:

- answer selection when a delay would affect pacing
- moving between quiz questions
- quiz timers or countdown emphasis
- any interaction where animation would compete with reading or recall
