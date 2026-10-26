# NoteLib voice and tone

**Status:** owner-approved direction, 2026-10-22. Adopted through the v0.175.0 Product UX copy review
(`docs/claude-plans/2026-10-22-v0175-copy-review-product-ux-prompt.md`). **It applies to every learner-facing
string in every feature:** Study Packs, Quick Review, Challenge Quiz, Review Sets, the Public Library and
everything after. Read it before writing or reviewing any copy, in a brief or in code.

## The voice in one line

**A helpful study companion: "Ready when you are."** Not a formal examination portal, and not an overly
enthusiastic game. NoteLib's personality comes from making studying feel approachable and rewarding, **not from
gamification.**

## The owner's direction (verbatim, 2026-10-22)

> I want NoteLib to have a consistent brand voice that is friendly, warm, encouraging, and lightly playful while remaining professional and trustworthy.
>
> Our primary audience includes students and exam takers, but the tone must also work for teachers and professionals.
>
> 1. Use natural, conversational language rather than technical or corporate wording.
> 2. Use sentence case for buttons and action labels throughout the quiz flow, while retaining capitalization for formal feature names where appropriate.
> 3. Use light encouragement for successful actions and milestones.
> 4. Keep loading messages calm and informative, without promising completion times.
> 5. Keep errors and quota messages clear, actionable, and reassuring rather than humorous.
> 6. Avoid excessive emojis, slang, forced jokes, exaggerated praise, or gamified pressure.
> 7. Never imply a quiz has started when it is only prepared.
> 8. Preserve all approved functional behavior, quota rules, accessibility requirements, and in-app notification limitations.
>
> Treat this as a copy-only refinement. Do not alter quiz behavior, business rules, or production configuration.
>
> For future NoteLib features, apply the same voice consistently across Study Packs, Quick Review, Challenge Quiz, Review Sets, and the Public Library.

> One last thought: I wouldn't turn NoteLib into a gamified app just to make it exciting. Its personality should come from making studying feel approachable and rewarding.
> Imagine a learner opening NoteLib after a tiring day. The app should feel like a helpful study companion saying, "Ready when you are." Not a formal examination portal, and not an overly enthusiastic game.
> — Product UX

## Working rules (how the direction is applied)

- **Buttons and action labels use sentence case:** "Start quiz", "Try again", "Continue reviewing".
- **Formal feature names keep their capitals:** Challenge Quiz, Quick Review, Redo Missed Questions, Board Exam
  Mode, Long Exam, Study Pack, Review Set, Public Library.
- **Address the learner as "you", and NoteLib as "we"** when something is NoteLib's doing ("We couldn't…").
  Avoid internal words such as request, session, batch, token, generation or forfeit.
- **Loading text says what's happening, never when it will finish.** No "in a minute", "almost done" or
  "shortly".
- **Errors say what happened, that the learner's work is safe when that's true, and the next step.** No humour in
  errors or quota messages.
- **Encouragement is light and only for a real milestone.** For example "Added 5 more questions." No streak
  pressure, no countdowns, no "Amazing!!".
- **Truth first.** Never promise more than the product guarantees:
  - no refunds;
  - no question reuse beyond what is shipped;
  - no email or push wording, because notifications are in-app only;
  - never call a prepared quiz "started";
  - never name an AI model, and never imply a FREE/paid quality difference (`CLAUDE.md` anti-drift).
- **Accessibility labels** are plain and descriptive ("Preparing your quiz"), never decorative.
- **Emoji:** none in product copy, unless a feature's established design already uses one.
