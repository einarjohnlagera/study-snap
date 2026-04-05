# quiz.md - Testing Notes

Verify these cases for quiz surfaces:

- Quick Review, Challenge Quiz, and Adaptive Practice each use distinct icons
- Quick Review questions stay lightweight and aligned with the learner level
- Challenge Quiz feels exam-style and does not repeat the base Quick Review question set
- Adaptive Practice stays focused on weak concepts instead of drifting into unrelated topics
- quantitative notes can produce computation questions with useful step-based explanations
- displayed `A` / `B` / `C` / `D` labels match the displayed choice order only and are not embedded in stored choice strings
- choice shuffling preserves correctness for Quick Review, Challenge Quiz, and Adaptive Practice
- the same in-progress session does not reshuffle choices differently on re-render
- result and review states still map the selected displayed answer back to the canonical correct choice
- legacy payloads that still expose answer text or `answerIndex` normalize correctly on load
- Note Detail `Summary` / `Quiz` controls render as tabs, not buttons
- active Note Detail tab updates with underline state and `aria-selected`
- `?tab=quiz` opens the quiz view directly
- switching tabs preserves the same note route and updates query state without full reload
- desktop quiz actions show icon + text
- mobile quiz actions keep accessible labels
- paywall/plan gating still applies to Premium-only quiz flows where configured
