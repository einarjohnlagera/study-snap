# ROADMAP.md - NoteLib

Rebrand note: StudySnap has been rebranded to NoteLib while preserving current database schema naming unless explicitly changed.

Goal: evolve NoteLib from a one-shot generator into a reusable note-first study workspace.

## Current Release Baseline

`v0.15.0 - Premium Mode Uplift + Cost-Control Quota Refactor` is the current in-progress release.

`v0.14.0 - Grow the Surface, Deepen the Practice` is complete and is the previous documentation baseline.

Older milestone labels below are preserved as planning history only. They are not the current in-progress release.

## v0.15.0 - Premium Mode Uplift + Cost-Control Quota Refactor

**Status: In Progress**

Theme: make Long Exam and Board Exam feel premium, not just gated behind a paywall, and close the unbounded-LLM-cost gap on uncapped modes. This is a margin fix framed as a UX uplift, not a feature add.

Primary focus:

1. **Premium feel for Long Exam and Board Exam** — improve the paid-mode experience without adding AI coaching or changing the locked simulation identity.

   - Stronger pre-session framing: pre-flight presentation, expected duration, and "this is not a quiz" cues
   - Stronger post-session presentation: score report layout polish, domain-coverage visualization, and suggested-next-step framing
   - Possible visual differentiation: distinct top-bar treatment, calm color palette, and larger result-page typography
   - Constraint: Board Exam stays feedback-free during the session; Long Exam stays forfeit-only with no mid-exam coaching, as locked in `docs/product/EXAM_MODES.md`

2. **Cost-control quota refactor** — replace the current "Pro = effectively unlimited" Long Exam and Board Exam state with explicit per-mode caps.

   | Mode | Current Pro state | Proposed v0.15.0 cap |
   |---|---|---|
   | Challenge Quiz | 50/mo | 50/mo (unchanged — already cheap per session, do not trim) |
   | Adaptive Practice | 30/mo | 30/mo (unchanged) |
   | Long Exam | uncapped (gated by Pro plan only) | 10/mo |
   | Board Exam | uncapped (gated by Pro plan only) | 5/mo (highest LLM cost per session) |
   | Interview Practice | 10/mo | 10/mo (unchanged) |

   Specific numbers are runtime config and must be tuned against actual usage data from v0.14.0 once captured. Do not lower Pro Study Pack quota (100/mo) or Pro Challenge Quiz quota (50/mo) without usage evidence — those are existing value the user is paying for.

3. **Interview Practice evolution post-v0.14.0** — review what to do next only after Interview Practice v1 has run for at least one billing cycle.

   - **Multi-note Interview Practice (smart context aggregation)** — generate from the base note plus related notes that share `courseProgram` and at least one tag; cap at 2–3 sibling notes to manage prompt size and per-session cost
   - **Structured interview templates by role/job family** — consider opinionated section breakdowns such as Backend Engineer = PL fundamentals + DB + Behavioral only if v1 usage data shows demand
   - **Open-ended / conversational evaluation** — only consider if MC + critique format hits its ceiling and Pro users explicitly ask for it
   - **Profile / role enrichment** — design separately before capturing target role on the user profile
   - **Interview Practice tier promotion to Plus** — only if v0.14.0 usage data justifies the LLM cost; current `gpt-4.1` generation + `gpt-4.1-mini` critique split is what makes Pro-only economically viable

4. **Professional profile surface updates** — Interview Practice shipped in v0.14.0 but was not surfaced on the landing page, learn page, or help center. Close the gap so the feature is discoverable to the audience it was built for.

   - **Landing page** — add "Professionals" to the `targetUsers` section alongside Students, Exam Reviewers, and Teachers; update the "how it works" step copy to acknowledge interview simulation as a distinct mode
   - **Help center** — add a "Professional Guide" help card that explains the Interview Practice workflow: note → scenario MCQs → AI critique → Interview Readiness Report
   - **Learn page** — add a "professionals" category with 2–3 guides: how to use NoteLib for interview prep, how to practice with scenario-based questions, how to read the Interview Readiness Report

6. **Authenticated user redirect on public/marketing pages** — when a signed-in user navigates to the landing page (`/`), pricing page, or other public marketing pages, the app currently renders those pages as if the user were anonymous (no auth-aware nav state, no redirect). The expected behavior is: redirect authenticated users from pure marketing pages directly to `/dashboard`, so they are never dropped back into a conversion funnel they have already passed through. Public content pages (`/public/library`, `/public/library/[subject]/[slug]`) are exempt — they are genuinely useful to signed-in users and should not redirect. Implementation approach: server component auth check on the landing page and pricing page routes; if a valid session cookie is present, return `redirect('/dashboard')` before rendering; no client-side redirect to avoid flash of landing content.

5. **Teacher flow polish** — make the teacher Generate → View → Export loop feel like a first-class product, not a functional prototype. Target audience: Filipino teachers who need a practical, affordable tool for quiz and exam preparation.

   - **Exam Builder UX audit and polish** — the current Exam Builder (note selection, section management, balance controls) works but is dense; identify and fix the specific friction points without a full rebuild; improve the note selection flow, make section reordering more intuitive, and reduce cognitive load on the balance step
   - **Quiz Preview layout** — stronger question display, correct answer and explanation more clearly distinguished, Export CTA as the dominant action in the header (not buried); read-only feel should communicate "this is your exam, ready to hand out"
   - **Teacher dashboard emphasis** — "Ready to Export" and "Recently Generated Quizzes" should be the first thing a teacher sees, not secondary cards below Continue Studying; Create Teaching Material CTA should be prominent and direct
   - **Teacher-specific empty states and guided first run** — new Teacher users land in a blank library with no guidance; add a first-run banner that explains the Generate → View → Export loop in plain language, linking directly to "Create a note" so teachers aren't lost
   - **Teacher plan accessibility** — exports are the terminal action for teachers, not quiz sessions; evaluate giving Teacher-profile Plus users higher or unlimited DOCX export limits, since capping exports at 15/mo directly blocks their primary workflow; the goal is to be genuinely useful to teachers who cannot afford the Pro tier, especially in the Philippine context where Pro pricing is proportionally high relative to teacher salaries; this requires a plan rules change and a `docs/product/PLANS.md` update before implementation

### Implementation stances

- New explicit per-mode quotas should live on `UserUsageEntity` and `StudySnapProperties`; reset by `BillingUsageResetJob`
- Existing uncapped Long Exam and Board Exam behavior is a margin risk; caps are the fix, not coaching
- Use honest user-facing framing: "Each mode now has its own monthly cap so you can see exactly what your plan includes"; avoid framing as a quota reduction
- Surface per-mode usage in Settings -> Plan & Billing alongside the existing counters
- Keep Board Exam feedback-free during the session and keep Long Exam forfeit-only with no mid-exam coaching
- Re-validate cost math against actual rates and usage before finalizing cap values
- Teacher flow polish must not change the `generatedQuiz` ownership model or route teachers into student session logic; all teacher preview uses `generatedQuiz` only
- Teacher plan accessibility decision must be made before any billing rule changes are implemented; do not change plan limits without a reviewed `PLANS.md` update
- Authenticated redirect must be server-side only (no client-side flash); public content pages (`/public/library/**`) are explicitly excluded from redirect behavior — only pure marketing pages (`/`, `/pricing`, `/learn`) redirect to `/dashboard`

### Cost math reference

- Pro revenue: roughly $4.50 blended (PH ₱249 + USD $4.99)
- Worst-case current LLM cost per Pro user/mo when every quota is maxed and Long/Board remain uncapped: roughly $4.83, creating negative margin on heavy users
- Worst-case post-cap: roughly $0.94 saved on Long/Board, restoring healthier margin
- Realistic-usage cost: roughly $1.50/mo, with caps protecting the worst case without affecting most users

---

## v0.14.0 - Grow the Surface, Deepen the Practice

**Status: Released**

Theme: expand organic reach through subject SEO pages, unlock professional-audience depth with Interview Practice, extend Long Exam to span multiple notes, and close out the quiz generation performance work deferred from v0.13.0.

Primary focus:

1. ~~**Subject landing pages (SEO)**~~ ✅ — server-rendered `/public/library/[subject]` pages with per-subject metadata, decay-ranked sections, and static generation shipped in v0.14.0.

2. ~~**Faster quiz generation**~~ ✅ — Board Exam dedicated simulation prompts, async Long Exam generation, and parallel LLM calls with sequential fallback shipped in v0.14.0.

3. ~~**Interview Practice Mode (Professional Profile)**~~ ✅ — shipped in v0.14.0 as an Adaptive Practice sub-mode (`ADAPTIVE` discriminator, `subMode: "INTERVIEW"` in session JSONB); 5-mode contract preserved; Pro-only, 10/month dedicated quota; `gpt-4.1-mini` critique + `gpt-4.1` generation; Interview Readiness Report result. Full spec in `docs/features/professional-profile.md`.

4. ~~**Multi-note Long Exam**~~ ✅ — shipped in v0.14.0; Pro users can add up to 3 same-subject notes to one Long Exam, with source refs stored in session JSONB and questions split proportionally by source.

5. ~~**Stale docs cleanup**~~ ✅ — removed 17 stale/legacy files; merged AI generation spec and overflow menu rules into active docs shipped in v0.14.0.

### Implementation stances

- Subject landing pages must be server-rendered; do not implement as a client-rendered filter redirect
- Interview Practice must reuse the `ADAPTIVE` engine discriminator and carry sub-mode identity in session JSONB (`subMode: "INTERVIEW"`). Do not introduce a new `QuickReviewSessionMode` enum value. Do not add a 6th mode. Do not introduce new persistence aggregates.
- Interview Practice must use `gpt-4.1-mini` for per-answer critique calls and `gpt-4.1` for generation. Do not unify on the premium model — the cost split is the launch viability case.
- Interview Practice quota is dedicated (10/month Pro-only) and tracked separately from Adaptive Practice / Challenge Quiz quotas. Do not double-charge other quotas.
- Multi-note Long Exam must reuse the existing session lifecycle; no new persistence aggregate
- Faster generation changes must be gated behind findings; do not optimize speculatively

---

## v0.13.0 - Complete the Promise, Reach New Audiences

**Status: Released**

Theme: ship the modes that were already promised (Long Exam), open NoteLib to a second audience (Professional Profile), improve organic discovery through SEO, and close out infrastructure research items deferred from v0.12.0.

Primary focus:

1. **Long Exam Mode v1 (Student-facing, Pro-only)** — backend session support, fixed long-form generation (not progressive), forfeit-only leave, mastery report result screen; single-note at launch; shared Advanced Exam quota bucket with Board Exam Mode

   - Backend: `LONG_EXAM` discriminator on `QuickReviewSessionMode`; question set generated and committed in full before the session starts; mastery report data stored in session state JSONB; reuses existing session lifecycle (`GENERATING → IN_PROGRESS → COMPLETED / FORFEITED / FAILED`) and generation lock
   - Frontend: setup confirmation screen with expected duration; fixed progress indicator (no `+5 Questions` control); Board Exam-style top bar with server-anchored countdown timer (90s/question); leave = forfeit — no pause/resume option exposed to the user (anti-procrastination principle, matches Board Exam behavior); mastery report result screen (coverage, weak domains, suggested next step, inline learner-level pill allowed)
   - Access: Pro-only at launch; single note; multi-note deferred to v0.14.0+
   - Profile visibility: Student profile (primary emphasis), Board Taker profile (secondary, less ceremony than Board Exam); hidden from Professional profile

2. **Professional Profile activation** — `PROFESSIONAL` profile type is no longer `Coming Soon`; users can select it in onboarding and profile settings; profile-aware mode label overrides and professional-framed dashboard

   - Backend: no new entities; `PROFESSIONAL` enum already existed
   - Frontend: `lib/exam-mode-visibility.ts` updated so Professional profile shows `Certification Review` (Challenge Quiz) and `Full Practice Exam` (Long Exam); Board Exam hidden; professional dashboard framing; Professional option in onboarding with profile icon; learner level grouped picker shows "Recommended for Professionals"; labels are display-only — engine discriminators (`CHALLENGE`, `LONG_EXAM`) unchanged
   - Access: All plans (same access rules as Student)
   - **Interview Practice Mode deferred to v0.14.0+** — requires a conversational AI evaluation engine not present in the current quiz architecture; see `docs/features/professional-profile.md`

3. **Faster quiz generation** — promote from research-only (v0.12.0 deferred) to research → implement; profile current LLM latency end-to-end (prompt build, API call, JSON parse, DB write); evaluate streaming responses to unblock frontend earlier, model selection (`gpt-4.1-mini` for quiz generation), and early session creation; implement the approach that findings support; frontend may gain a generation progress indicator if streaming is adopted

4. **Subject landing pages (SEO)** — proper server-rendered `/public/library/[subject]` landing pages replacing the current redirect to the filtered library; static `<title>` and `<meta description>` per subject; server-rendered note cards ranked by decay scoring; sitemap update to include subject pages; deferred from v0.12.0 (ROADMAP item J)

5. **Proration / recomputation design doc** — design how mid-cycle plan changes (upgrade and downgrade) recompute Study Pack and quiz quotas; output: a design doc under `docs/product/`; no implementation until the design is reviewed; deferred from v0.12.0

6. **Stale docs cleanup** — audit `docs/` for files still referencing v0.11.0 or earlier resolved items; update or remove

### Implementation stances

- Professional Profile must not fork entity tables — all profiles share the same Note/StudyPack/Session model
- Long Exam backend must reuse the existing session lifecycle and generation lock; no new persistence aggregate
- Subject landing pages must be server-rendered; do not implement as a client-rendered filter redirect
- No proration implementation until the design doc is reviewed and approved
- Exactly five quiz-flavored modes exist: Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, Board Exam; adding a sixth requires updating `docs/product/EXAM_MODES.md` and this roadmap together

---

## v0.12.0 - Learning Experience, Discovery, and Retention

**Status: Released**

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
6. **Backend Public Library filtering + shareable URLs** — move subject, tags, course/program, search, and audience filters onto the canonical `/public/library` query-param model so students can bookmark and share collections without duplicate public-library routes
7. **Library organization guidance for students** — in-app guidance explains how subjects and Course/Program organize the private Library as it grows; reuse the existing `GuidanceTip` system and add one-time contextual tips at natural growth milestones
8. **Social login — Google first** — add Google OAuth as an alternative to email-and-password login and signup; no other providers until Google is stable
9. **Faster quiz generation investigation** — profile current LLM latency end-to-end for quiz generation; prototype streaming or early session-creation patterns; document findings and a recommended approach in `docs/architecture/` before any implementation
10. **Profile-aware mode selection + Long Exam coming-soon** — mode-selection screen now profile-aware (Students see Challenge Quiz + Long Exam; Board Takers see Challenge Quiz + Board Exam; Teachers skip to challenge setup); Long Exam card and setup screen live as a coming-soon placeholder so mode identity is established; `lib/exam-mode-visibility.ts` added as the single source of truth; accelerated from Medium Priority after doc planning landed
11. **Board Exam premium UX polish (presentation-only)** — pre-flight setup, score-report-style result framing, fullscreen behavior, and removal of the inline learner-level pill on the result screen so Board Exam Mode reads as a simulation and not a "longer Challenge Quiz"; no engine changes; details in `docs/product/EXAM_MODES.md`
12. **Adaptive Practice tier reconciliation** — `PLANS.md` is the canonical source (Plus = 10 / mo, Pro = 30 / mo); align `docs/features/adaptive-practice.md`, `docs/features/quiz.md`, `docs/PROJECT_CONTEXT.md`, and runtime gating to match before any Long Exam monetization work begins

### High Priority (Current Phase)

- **Public creator identity disambiguation** — stop relying on `displayName` alone on Public Library cards and public note detail; use or introduce a stable public creator identifier (username / handle when available, otherwise a generated public slug), keep `displayName` for readability, show handle/slug when disambiguation is needed, and preserve existing public links through compatibility or redirect handling

### Medium Priority (Next Phase)

- **Board Exam Mode optimization** — improve generation speed, explore partial or progressive loading only if it preserves the exam-like experience, and keep progressive generation out of Board Exam Mode for now; identity contract is locked in `docs/product/EXAM_MODES.md`
- **Long Exam Mode v1 (Student-facing, Pro-only)** — backend session support, fixed long-form generation, pause/resume, mastery report result screen; Pro gating and shared Advanced Exam quota
- ~~**Onboarding/profile type icon polish**~~ ✅ shipped in v0.13.0 — emoji icons added to all four active profile type cards in onboarding

### Future Guidance System Expansion (Post-v0.12.0)

The guidance engine introduced in v0.12.0 is intentionally minimal. Future iterations can extend it without changing the `GuidanceTip` component or `guidance.ts` persistence layer:

- **Note editor inline guidance** — contextual tips inside the note editor when `subject` or tags are blank after the first save; use the engine's `condition()` callback to check field state at render time
- **Cooldown-aware rules** — add an optional `cooldownMs` field to `GuidanceRule`; `pickActiveGuidance()` can skip rules shown within the cooldown window using a separate last-shown timestamp key in localStorage
- **Dashboard contextual tips** — tips tied to study-gap detection (e.g., user hasn't quizzed in 7 days) using the same engine pattern; conditions read from dashboard data already loaded on the page
- **Profile completion nudge** — tip on the Dashboard or Profile page when `courseProgram` is unset after the first Study Pack is generated

### Product Direction Note

Board Exam Mode is intentionally kept as a fixed, exam-style experience. Optimization will be handled separately after core quiz flow and conversion improvements are stabilized.

Implementation stances:

- public note pages must teach first, then convert — do not hard-gate visitors before they see value; mini quiz preview is a lightweight surface, not a full session; no anonymous session state is persisted
- generated note formatting should prioritize scannability and study usefulness; prefer short sections, clear headings, key-fact blocks, and exam-friendly wording over long paragraph dumps
- keep Learner Level and Course/Program as separate concerns — Learner Level controls difficulty/style; Course/Program controls domain context — do not merge them
- ~~do not add learner level to onboarding~~ — **reversed in v0.13.0**: onboarding step 2 now collects learner level and course/program directly; Dashboard prompt remains for users who skip onboarding step 2 or completed onboarding before this change
- social login must be an alternative, not a replacement; existing email accounts must continue to work
- quiz latency investigation is research-only in v0.12.0; no production latency changes without findings
- Long Exam Mode is design-only in v0.12.0; canonical mode-hierarchy and identity contract live in `docs/product/EXAM_MODES.md`; no implementation until the spec is reviewed
- exactly five quiz-flavored modes exist: Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, Board Exam; adding a sixth requires updating `docs/product/EXAM_MODES.md` and this roadmap together

### Completed in v0.12.0 so far

- **Public Note Quick Check — multi-question preview** — evolved the single-question Quick Check into a sequential multi-question experience (up to 3 preview questions drawn from the Study Pack quiz); added a progress indicator (`1 / 3`) so visitors know where they are; after submitting each answer, improved feedback microcopy (✅ Correct!, 🧠 Nice work!, Almost there.) and a "Next Question →" button appear before advancing; the final question transitions to a lightweight completion state ("🎉 Quick Check Complete") with CTAs to copy and start practicing; no backend changes, no new AI generation, fallback-safe when fewer than 3 questions exist; notes-first layout preserved — Quick Check remains below Summary and Key Concepts
- **Public note detail engagement polish** — refined the public-note learning hook with a safe fallback; updated Quick Check to feel like a lightweight learning prompt instead of a demo widget; added a post-answer CTA that nudges visitors toward creating or copying their own Study Pack only after value is shown; tightened public-note CTA wording and Full Notes readability without changing quiz/session logic
- **Public Library canonical routing + shareable filters** — consolidated public browsing around `/public/library`; turned `/library/public` and `/public/library/{subject}` into compatibility redirects; synced subject, tag, search, course/program, audience, and sort filters to shareable query params so direct filtered URLs restore the same UI state
- **Public Library URL-filter UX polish** — stabilized the main search with debounced URL sync, preserved scroll position on filter changes, kept tag browsing reachable through a dedicated `Browse all` action, and fixed selector-modal search focus so typing no longer jumps to the close button
- **Public Creator Identity / Attribution** — added unique public usernames as stable handles; public attribution now keeps `displayName` for readability while using `@username` and `/public/creator/{username}` for disambiguation and future creator pages; legacy `/public/profile/{userId}` links remain compatible
- **Social login — Google first** — added Google OAuth login/signup as an alternative to email/password; verified Google emails link to existing accounts instead of creating duplicates; Profile shows connected sign-in methods; Apple/Facebook/GitHub remain out of scope
- **Study Pack metadata correctness** — locked note-level `courseProgram` as the Study Pack generation source of truth with profile fallback only when the note has no course/program saved; fixed normal note-owned generation so AI metadata suggestions stay transient until apply; removed duplicate AI tag suggestions when user tags already overlap; kept onboarding's explicit auto-apply exception for empty metadata fields
- **Quiz metadata context consistency** — Challenge Quiz, Board Exam, and Adaptive Practice now use the same generation-context resolver as Study Pack generation: note-level `courseProgram` first, profile `courseProgram` fallback, and user-level `learnerLevel` for difficulty/style
- **Generate from Topic Course/Program source-of-truth fix** — first generation now reads the current Create Note Course / Program at submit time and sends it immediately; profile Course / Program remains fallback only when no draft value is selected
- **Quiz Ready badge accuracy** — made private Library `Quiz Ready` badges and filters profile-aware: Teacher users keep them for exam-export workflows, while Student and Board Taker users see learner-facing Study Pack readiness only
- **Progressive Challenge Quiz generation** — Challenge mode starts with 5 questions; users generate +5 more from the last question, up to 20 per session; `POST /challenge-quiz/sessions/{sessionId}/generate-more` endpoint; `GenerateMoreChallengeQuizResponse` DTO; `NotEnoughNewQuestionsException` with `NOT_ENOUGH_NEW_QUESTIONS` code; `QuizDeduplicationUtils.uniqueQuestions()` post-generation dedup; `QuizSessionStateUtils.appendQuizItems()` JSONB append; Board Exam Mode is exempt
- **Progressive quiz scoring** — score computed from answered questions (`selectedChoices.size()`) instead of fixed total; result screen shows `{correct} of {answered} answered correctly`; Score Summary column labeled `Answered`
- **Challenge Quiz UX refinements** — `Complete Quiz` replaces `Submit Challenge Quiz`; `+5 Questions` / `Adding...` button at last question; microcopy banner at quiz top; progression-aware hint at last question (`"Good start — want to keep going?"` at 5 q, `"10 questions in — push to 15?"` at 10 q, `"Almost there — finish with all 20?"` at 15 q, `"You've answered all {n} questions — ready to submit?"` at cap); generate-more toast updated to `"Challenge extended to {n} questions"` / `"Full challenge unlocked: 20 questions"`; `noMoreQuestions` state hides `+5 Questions` silently
- **Leave Quiz modal stability fix** — `onBeforeRouteLeave` and `onConfirmLeave` memoized via `useCallback` in `page.tsx`; `onConfirmLeave` reads from `challengeSessionRef.current` to avoid stale closures; prevents `LeaveQuizModal` from unmounting/remounting on every timer tick
- **Analytics enum completeness fix** — added missing `QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, `ADAPTIVE_PRACTICE_COMPLETED`, and `ONBOARDING_V2_CTA_GO_TO_SAVED_NOTE` to `AnalyticsEventType` Java enum to resolve `HttpMessageNotReadableException` on quiz completion events
- **Conversion funnel + quiz UX refinement pass** — `PaywallModal` plan cards selectable with ring highlight, single `Continue with [Plan]` footer CTA, PRO-user calm message instead of disabled cards; `StudyPackLimitModal` trimmed to primary CTA + `Maybe Later` for FREE/PLUS and a single `Got It` for PRO; `getUpgradeCtas` extended with optional `UpgradeCtaContext` for context-aware copy (`"Get More Study Packs"`, `"Unlock Adaptive Practice"`); Quick Review result adds guidance text and renames `Practice Again` → `Retry Quick Review`; Dashboard and Library empty states updated to guided copy
- **Retention loop — continue studying + focus areas** — Continue Studying session priority reordered to Challenge Quiz → Adaptive Practice → Quick Review; Continue Studying body copy is mode-aware (`"You left off on Question 4 of 10 in your Challenge Quiz."`); Focus Areas free-tier fallback: Free/Plus users see `"Revisit Note"` when weak concepts exist but Adaptive Practice is locked, instead of only an upgrade prompt; `MEANINGFUL_STUDY_ACTIVITIES` constant deduplicated to `ActivityType.MEANINGFUL_STUDY_ACTIVITIES`
- **Guidance Foundation System** — minimal guidance engine (`lib/guidance-engine.ts`) with `GuidanceRule` type and `pickActiveGuidance()` function; two contextual library tips at natural growth milestones (notes 1–3 and notes ≥ 5); Dashboard personalization prompt bug fixed (suppressed when learner level already set); prompt repositioned after primary study action for all three profile types
- **Profile-aware mode selection + Long Exam coming-soon** — `lib/exam-mode-visibility.ts` is the single source of truth for which modes appear per profile; Students see Challenge Quiz + Long Exam (coming-soon); Board Takers see Challenge Quiz + Board Exam; Teachers skip to challenge setup directly; cross-profile escape hatch guides Students toward Board Exam via profile switch; Long Exam mode card and coming-soon setup screen live with disabled CTA; backend session logic ships in v0.13.0
- **Board Exam premium UX polish (presentation-only)** — pre-flight setup screen replaced with a simulation-framing checklist ("Begin Board Exam", 5 pre-flight items); result screen subtitle changed to "Score Report"; inline learner-level pill hidden on Board Exam result (`!isBoardExamMode` guard); `PostSuccessUpgradeNudge` hidden on Board Exam result; no engine changes
- **Adaptive Practice tier reconciliation** — `StudySnapProperties` defaults corrected (`adaptivePracticeProOnly=false`, `plusMonthlyAdaptivePracticeLimit=10`); `application.yaml` default updated; `docs/features/adaptive-practice.md`, `docs/features/quiz.md`, and `docs/PROJECT_CONTEXT.md` now all reflect Plus = 10 / mo, Pro = 30 / mo; Open Discrepancy #1 in `EXAM_MODES.md` closed
- **Learner Level grouped picker on quiz result screens** — Quick Review and Challenge Quiz result screens now render learner level chips in two profile-aware groups (Recommended / Other Learning Styles) via `getGroupedLearnerLevels(viewerProfileType)`; `viewerProfileType` state added to Quick Review and synced via auth listener; profile page combobox (already grouped) unchanged
- **Public Library conversion funnel polish (recommendations A–G)** — related-notes block in quiz completion card ("More from {Subject}", up to 3 engagement-ranked notes from same subject, server-side fetch via shared 5-min cache); auth-prompt consolidation into `AppModal` pattern with copy-intent redirect URLs (`guestAuthMode` prop removed from all callers); dead tabbed-content component deleted; `PublicPracticeModeTeaser` placed after Full Notes on public note detail (Challenge Quiz + Adaptive Practice free, Board Exam Mode Pro chip, gated on `!isDraft`); time-decayed Featured score (30-day half-life, 10% floor) applied in both `computeDiscoveryScore` (frontend) and `computeScore` (backend) with synchronized formulas and testable `now` parameter; recommendation H blocked pending backend windowed count fields; recommendation J (subject landing pages) deferred

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
- `/public/library`
- `/notes/{id}`
- `/notes/{id}/sessions/{sessionId}` for session review
- `/public/library/{subject}`
- `/public/library/{subject}/{slug}`
- `/public/profile/{userId}`

Current session-review UX:

- desktop and mobile both open the same dedicated session-review page from `Recent Sessions`
- Note Detail stays the entry point for history, while the dedicated review page owns focused answer review

## Future Directions

### Exam-mode work (planned)

- **Multi-note Long Exam** — shipped in v0.14.0
- **Board Exam advanced result analytics** — promoted into the active v0.15.0 premium-mode result presentation scope
- **Multi-note Board Exam** — allow Pro users to span a Board Exam across up to 3 same-subject notes, mirroring the multi-note Long Exam feature; pre-generated question pools would need to be scoped per source-note combination or generated live for multi-note sessions; do not implement until usage data from single-note Board Exam pools (shipped v0.15.0) shows demand for cross-note simulation
- **Long Exam tier promotion to Plus** — only if usage data justifies the LLM cost; not part of the current v0.15.0 cap refactor unless the cost review supports it
- **Planning-only** — cross-profile mode unlock (Students opting into Board Exam without changing profile); curated exam decks / cohort content (Pro+); cross-profile journey (Student → Board Taker upgrade flow with continuity)

### Premium mode uplift + cost-control quota refactor

Promoted to the active `v0.15.0 - Premium Mode Uplift + Cost-Control Quota Refactor` section above.

### Interview Practice evolution

Initial evaluation is promoted to the active v0.15.0 section. The following remain unsequenced follow-up directions and should not be committed until Interview Practice v1 has usage data from at least one billing cycle.

- **Multi-note Interview Practice (smart context aggregation)** — generate from the base note plus related notes that share `courseProgram` and at least one tag; cap at 2–3 sibling notes to manage prompt size and per-session cost; the dashboard entry already selects the most relevant note, so multi-note adds breadth across a topic, not a replacement for that smart selection; do not implement until v1 usage data confirms users want wider scenario coverage than a single note provides
- **Structured interview templates by role/job family** — opinionated section breakdowns (e.g. Backend Engineer = PL fundamentals + DB + Behavioral); requires either a curated role taxonomy or a user-defined template builder; do not build until v1 usage data shows real demand and the section-aware generation prompt's limitations are observed
- **Open-ended / conversational evaluation** — replace MC structure with free-text answers and AI rubric scoring; architecturally heavy (new session schema, new evaluation pipeline, new result model); only consider if MC + critique format hits its ceiling and Pro users explicitly ask for it
- **Profile / role enrichment** — capture target role explicitly on the user profile (instead of inferring from notes) to drive better generation context; bigger architectural decision; do not bundle with any of the above — design separately
- **Interview Practice tier promotion to Plus** — only if v0.14.0 usage data justifies the LLM cost; current model split (gpt-4.1 generation + gpt-4.1-mini critique) is what makes Pro-only economically viable, and lowering the tier requires re-running that math

### Public Library Discovery — Future Items

- **Trending this week section (H)** — a new discovery section above Featured showing notes gaining traction in the last 7 days; blocked on backend: `NoteListItemResponse` has no windowed engagement fields; requires `recentCopyCount` / `recentLikeCount` or a precomputed rolling 7-day aggregate before this section can be built correctly; do not implement under a "Trending" label using lifetime totals — the signal would be misleading
- **Subject landing pages (J)** — moved to v0.14.0 scope; `/public/library/[subject]` proper server-rendered landing pages with per-subject metadata and decay-ranked note cards

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

### Account Management (Future)

- forgot password flow
- change password from Settings
- email verification and account-security improvements beyond the current launch flow

### Connected Account Management / Auth Provider Management (Future)

Future improvements for users with multiple sign-in methods. The Google OAuth foundation (account linking, `email_verified` guard, Profile sign-in method status) is in place; these are follow-on improvements that require their own design pass before implementation.

Potential scope:

- unlink Google account safely — must prevent lockout when Google is the only sign-in method
- add/change password for Google-only users — allow switching to or adding email/password without forcing a full re-signup
- multiple provider support — Apple, Microsoft, etc.; do not add until Google is stable and provider abstraction is reviewed
- connected account security UX — notify users by email when a new provider is linked to their account
- account recovery flows for social-login users — what happens when Google revokes access or the associated email changes
- provider conflict resolution UI — surface a clear choice when a Google email matches an existing email/password account
- recent login/session visibility — show sign-in history and active sessions in Settings for security-aware users
- email change flow with connected providers — changing the NoteLib email when a Google account is linked needs careful sequencing

Implementation notes for future reference:

- the provider abstraction should live in a shared auth provider layer, not scattered across login and signup flows
- lockout prevention: never allow unlinking the only auth method unless an alternative is confirmed first
- Google-only users have no password; the "add password" flow must go through a secure email-based credential creation path
- do not add more providers until the connected-account management UX is designed; adding providers without management UX creates a support burden

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
