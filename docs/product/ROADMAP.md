# ROADMAP.md - NoteLib

Rebrand note: StudySnap has been rebranded to NoteLib while preserving current database schema naming unless explicitly changed.

Goal: evolve NoteLib from a one-shot generator into a reusable note-first study workspace.

## Current Release Baseline

`v0.12.0 - Learning Experience, Discovery, and Retention` is the current in-progress release.

`v0.11.0 - Learning Flow Foundation` is complete and is the previous documentation baseline.

Older milestone labels below are preserved as planning history only. They are not the current in-progress release.

## v0.12.0 - Learning Experience, Discovery, and Retention

**Status: In Progress**

Current phase emphasis:

- improve user conversion and the first-study / first-quiz experience before expanding monetization work
- keep Progressive Challenge Quiz generation as the active quiz-flow optimization path
- treat Board Exam Mode optimization as a separate follow-up after the core quiz flow is more stable

Primary focus:

1. **Public Library public note conversion** *(top priority)* — public notes are shareable but currently function as app detail screens rather than learning pages; a visitor who arrives from a Facebook or social link should immediately understand the topic, see why NoteLib helps them study, interact lightly with the content, and know what to do next without being hard-gated before value is shown

   - add a short topic hook below the note title that anchors the learning angle for visitors
   - add a Quick Check / mini quiz preview section: expose 1–2 questions to public users without requiring login
   - gate continuation of the full quiz, score persistence, and Study Pack generation behind signup/login
   - after signup, route the user toward creating or copying a Study Pack so they land in the product with a clear goal
   - add a soft conversion CTA: `Turn your own notes into something like this`
   - reorder primary CTAs so copy/generate actions appear after the visitor has seen learning value
   - keep `Share` always visible; keep `Copy to My Library` available for signed-in users
   - improve generated note formatting: shorter sections, clearer headings, key-fact blocks, quick recall blocks, less dense paragraphs — so public pages read like a study reviewer, not a raw LLM dump
   - public mini quiz answers must not be persisted for anonymous users; no session is created until the user is authenticated

   Acceptance criteria:
   - a visitor without an account can open a public note, understand the topic, and answer 1–2 questions
   - signup gate appears only after value is shown — not on page load
   - CTA does not feel aggressive or interrupt the reading experience
   - the page works well for Facebook/social sharing use cases
   - no implementation changes to the core Study Pack generation or session flows for authenticated users

2. **Learner Level + Course/Program UX refinement** — quiz generation prompts use saved learner level for difficulty and explanation depth; Course/Program autocomplete suggestions are narrowed by the active subject context; helper text on Learning Profile adapts to the selected learner level; no new onboarding steps
3. **Conversion funnel optimization** — plan-aware CTAs via `getUpgradeCtas(currentPlan)` on all paywall and limit surfaces; post-quiz upgrade nudge on Quick Review and Challenge Quiz result screens; analytics funnel events queryable from the admin dashboard
4. **Proration / recomputation design** — design mid-cycle plan changes (upgrade and downgrade) so quota is recalculated correctly; do not implement until design is approved; document in `docs/architecture/ARCHITECTURE.md`
5. **Retention loops** — continue-studying prompts on Dashboard for users who have recent unfinished sessions; weak-concept reminder emails on a backend schedule; near-limit banners surface reset date and upgrade CTA
6. **Backend Public Library filtering + shareable URLs** — move subject, tags, learner level, and profile-type filters from frontend to backend query params; each filtered state maps to a stable shareable URL so students can bookmark and share collections
7. **Library organization guidance for students** — in-app guidance explains how subjects and Course/Program organize the private Library as it grows; reuse the existing `GuidanceTip` system and add one-time contextual tips at natural growth milestones
8. **Social login — Google first** — add Google OAuth as an alternative to email-and-password login and signup; no other providers until Google is stable
9. **Faster quiz generation investigation** — profile current LLM latency end-to-end for quiz generation; prototype streaming or early session-creation patterns; document findings and a recommended approach in `docs/architecture/` before any implementation
10. **Multi-topic exam / Long Exam mode planning** — design a Board Exam session that spans multiple notes or topics; produce a written spec and UX sketch before any implementation

### High Priority (Current Phase)

- **Quiz Ready badge accuracy** — fix incorrect `Quiz Ready` badge visibility in Student mode and Board Exam mode so the badge reflects actual quiz availability and readiness
- **Public creator identity disambiguation** — stop relying on `displayName` alone on Public Library cards and public note detail; use or introduce a stable public creator identifier (username / handle when available, otherwise a generated public slug), keep `displayName` for readability, show handle/slug when disambiguation is needed, and preserve existing public links through compatibility or redirect handling

### Medium Priority (Next Phase)

- **Board Exam Mode optimization** — improve generation speed, explore partial or progressive loading only if it preserves the exam-like experience, and keep progressive generation out of Board Exam Mode for now

### Product Direction Note

Board Exam Mode is intentionally kept as a fixed, exam-style experience. Optimization will be handled separately after core quiz flow and conversion improvements are stabilized.

Implementation stances:

- public note pages must teach first, then convert — do not hard-gate visitors before they see value; mini quiz preview is a lightweight surface, not a full session; no anonymous session state is persisted
- generated note formatting should prioritize scannability and study usefulness; prefer short sections, clear headings, key-fact blocks, and exam-friendly wording over long paragraph dumps
- keep Learner Level and Course/Program as separate concerns — Learner Level controls difficulty/style; Course/Program controls domain context — do not merge them
- do not add learner level to onboarding; deferred collection via Dashboard prompt is the settled pattern
- social login must be an alternative, not a replacement; existing email accounts must continue to work
- quiz latency investigation is research-only in v0.12.0; no production latency changes without findings
- Long Exam mode is design-only in v0.12.0; no implementation until the spec is reviewed

### Completed in v0.12.0 so far

- **Public note detail engagement polish** — refined the public-note learning hook with a safe fallback; updated Quick Check to feel like a lightweight learning prompt instead of a demo widget; added a post-answer CTA that nudges visitors toward creating or copying their own Study Pack only after value is shown; tightened public-note CTA wording and Full Notes readability without changing quiz/session logic
- **Progressive Challenge Quiz generation** — Challenge mode starts with 5 questions; users generate +5 more from the last question, up to 20 per session; `POST /challenge-quiz/sessions/{sessionId}/generate-more` endpoint; `GenerateMoreChallengeQuizResponse` DTO; `NotEnoughNewQuestionsException` with `NOT_ENOUGH_NEW_QUESTIONS` code; `QuizDeduplicationUtils.uniqueQuestions()` post-generation dedup; `QuizSessionStateUtils.appendQuizItems()` JSONB append; Board Exam Mode is exempt
- **Progressive quiz scoring** — score computed from answered questions (`selectedChoices.size()`) instead of fixed total; result screen shows `{correct} of {answered} answered correctly`; Score Summary column labeled `Answered`
- **Challenge Quiz UX refinements** — `Complete Quiz` replaces `Submit Challenge Quiz`; `+5 Questions` / `Adding...` button at last question; microcopy banner at quiz top; "finish anytime" guidance and "What would you like to do next?" hint at last question; generate-more toast (auto-clears after 3 s); `noMoreQuestions` state hides `+5 Questions` silently
- **Leave Quiz modal stability fix** — `onBeforeRouteLeave` and `onConfirmLeave` memoized via `useCallback` in `page.tsx`; `onConfirmLeave` reads from `challengeSessionRef.current` to avoid stale closures; prevents `LeaveQuizModal` from unmounting/remounting on every timer tick
- **Analytics enum completeness fix** — added missing `QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, `ADAPTIVE_PRACTICE_COMPLETED`, and `ONBOARDING_V2_CTA_GO_TO_SAVED_NOTE` to `AnalyticsEventType` Java enum to resolve `HttpMessageNotReadableException` on quiz completion events

## v0.11.0 — Completed

Completed in `v0.11.0`:

- learning loop positioning across the landing page and product messaging
- onboarding flow redesign: experience-first 5-step flow that ends with a generated Study Pack
- Generate Note from topic available in both onboarding and Create Note
- Create Note UX improvements with write vs generate entry options
- Xendit payment integration with hosted checkout and webhook-confirmed activation
- Xendit payment hardening:
  - correct PHP invoice amount handling
  - pending checkout reuse instead of duplicate pending payments
  - config-driven Monthly and Annual manual checkout amounts
  - automatic intro-offer and voucher application during checkout
  - voucher redemption persistence only after successful `PAID` webhook
  - safe internal `returnUrl` support back to the interrupted page
  - success-page routing that returns Settings/Billing upgrades to Dashboard and paywall upgrades to the interrupted flow
  - polished billing success and failed result pages
  - manual-renewal expiry windows after Monthly (`30` days) and Annual (`365` days) payments
  - subscriptions-table source of truth for plan state, active-subscription history preservation, and webhook-driven renewal extension
- Free / Plus / Pro multi-plan billing model replacing the legacy single-tier paid plan
- Settings Plan & Billing redesign: billing cycle toggle + 3-column plan cards (Free, Plus, Pro)
- pricing system unification through a shared frontend plan config used by landing, pricing, and settings surfaces
- conversion-focused paywall redesign with context-aware copy, autosave-before-checkout, and resume-after-upgrade flow restoration
- documentation context cleanup so product, architecture, and feature docs match the current Free / Plus / Pro, Xendit, onboarding, and paywall behavior
- legacy billing-provider runtime removal and local ngrok-based webhook testing support
- copy alignment around `Generate Study Pack`
- activation improvement: users leave onboarding with real content, not an empty dashboard
- content moderation: `ContentModerationService` with token-based dictionary matching at note title, Study Pack topic, and note content creation boundaries; English and Filipino banned-word dictionaries; 52 tests
- plan-aware upgrade CTAs: `getUpgradeCtas(currentPlan)` helper in `frontend/src/config/plans.ts`; upgrade surfaces route to `/settings?section=plans` instead of `/pricing`
- Settings `?section=plans` auto-scroll and highlight ring on the Plan & Billing card
- post-quiz `PostSuccessUpgradeNudge` on Quick Review and Challenge Quiz result screens with plan-aware CTAs and sessionStorage dismissal
- analytics funnel events: `QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, `ADAPTIVE_PRACTICE_COMPLETED`, `ONBOARDING_V2_CTA_GO_TO_SAVED_NOTE` added to `AnalyticsEventType`
- onboarding Study Pack limit handling: bumps to Step 5 with `studyPackLimitReached` flag; shows `NearLimitBanner` and note-navigation CTAs; fires `completeOnboarding` via existing useEffect

### v0.6.0 - Landing Revamp & Positioning

Primary focus:

- Landing-page messaging revamp that positions NoteLib as a notes library and study workspace first
- Public Library promotion as a top-level public discovery route
- Learn-page integration for the active-recall study method
- Public navbar alignment across landing, learn, pricing, login, and Public Library
- SEO title, meta description, and Open Graph metadata alignment with the new positioning
- Open Graph image refresh to match the new messaging before the release is cut
- Landing pricing section updated to Free / Plus / Pro cards with intro offer pricing and "Manual renewal. No automatic charges." footer
- Demo page redesigned as a 5-step interactive flow (choose start → input → generated note → Study Pack CTA → Study Pack results) using static prebuilt content only — no backend or LLM calls
- Landing hero repositioned around exam-readiness: "Turn your notes into exam-ready study materials in seconds"
- "Why NoteLib" section updated with 3 benefit cards (Built for studying, Learn from your weak points, From notes to mastery)
- Demo enhanced with interactive per-question quiz (select before reveal), exam context copy, and post-quiz conversion CTA
- Pricing cards updated with plan descriptions tied to learner stage; export feature description added; Plus includes Adaptive Practice (10/month)
- Product positioning principles added to AGENTS.md: learning-outcome framing, demo as conversion driver, clear plan progression

Implementation stance:

- position NoteLib as an exam-focused study tool, not a generic AI utility
- hero and pricing copy must frame features in terms of learning outcomes
- demo must feel like a guided experience that creates an "aha moment" before the CTA
- Free → Plus → Pro should feel like natural progression for a growing student
- treat Public Library as a public growth and discovery feature, not a paid feature
- keep public marketing pages accessible without login
- align landing, SEO, and README messaging around the same product identity before `v0.6.0` is tagged

### v0.7.0 - Learning & Metadata Foundation

Primary focus:

- Learner Level on the user profile and onboarding
- required Learning Profile `Course / Program` plus optional per-note `Course / Program` metadata
- note-level `courseProgram` metadata with profile-defaulted note creation
- stronger note metadata quality through subject autocomplete, saved custom subjects, and tag guidance
- field-level AI metadata suggestions so users keep final control of title, subject, and tags
- a dedicated `Learning Profile` card on private Profile
- richer Public Profile identity with learner-level/course context when provided
- generation-context plumbing so future quiz prompts can use learner metadata safely

Implementation stance:

- keep learner metadata on the existing `users` aggregate instead of creating profile-type-specific tables
- keep note-level `Course / Program` optional while requiring it for onboarding and later Learning Profile saves
- prepare smarter quiz generation by passing learner metadata through backend generation context before prompt behavior changes
- improve library/public-profile structure over time without changing note ownership or page responsibilities

### v0.8.0 - Board Exam Mode

Primary focus:

- Async Study Pack generation handoff from Note Editor to Note Detail
- Graceful Study Pack generation failure and retry recovery
- Quiz start integrity locks for exam-like Challenge Quiz starts
- Exam Countdown
- Exam Readiness Score
- Study Plan
- Mock Exam Mode
- Performance Analytics

Implementation stance:

- keep Board Exam Mode on the same shared note-first engine
- do not fork entities or tables by profile type
- use the existing `Note -> Study Pack -> Quiz -> Activity -> Weak Concepts` pipeline
- emphasize exam-prep presentation, recommendations, and analytics without merging page responsibilities

## Current Product Shape

Navigation:

- Main:
  - Dashboard
  - Library
  - Public Library
- Account:
  - Profile
  - Settings
  - Admin (admins only)

Core routes:

- `/dashboard`
- `/library`
- `/library/public`
- `/notes/{id}`
- `/notes/{id}/sessions/{sessionId}` for session review
- `/public/library/{subject}`
- `/public/library/{subject}/{slug}`
- `/public/profile/{userId}`

Current session-review UX:

- desktop and mobile both open the same dedicated session-review page from `Recent Sessions`
- Note Detail stays the entry point for history, while the dedicated review page owns focused answer review

## Future Directions

Potential expansion areas after `v0.8.0`:

- richer note workspace
- deeper progress insights from quiz history
- board-exam-specific recommendations and weak-area planning
- optional public-profile enhancements such as followers, likes, and creator bios
- optional snapshot/history tables if product value is proven

### Billing Improvements (Future)

- recurring subscription support
- coupon-code entry UI
- cancel subscription flow
- billing portal / self-serve billing management
- automatic renewal
- invoices / receipts UI
- billing history UI improvements
- plan switching and downgrade flows
- provider-managed recurring billing via `provider_subscription_id`

### Public Library persona filtering (roadmap)

Planned for a future release after the mode system matures:

- persona-based note recommendations in Public Library discovery (same-profile notes ranked higher)
- cross-profile discovery still allowed so learners can find materials outside their profile type
- filtering UI: optional "Relevant to me" toggle that uses the current user's profile mode for ranking
- implementation must remain additive — no ranking change without the toggle enabled
- do not build until there are enough public notes per profile type to make filtering meaningful

## Product Learning Loop

Capture -> Generate -> Review -> Improve -> Copy -> Repeat

Roadmap decisions should reinforce this loop rather than one-time output generation.

## Legacy planning context

Older phase-by-phase roadmap details are preserved in `/docs/legacy/ROADMAP.md`.
