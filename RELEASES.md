# RELEASES.md - NoteLib

## v0.14.0 - Grow the Surface, Deepen the Practice

**Status: In Progress**

Theme: expand organic reach through subject SEO pages, unlock professional-audience depth with Interview Practice, extend Long Exam to span multiple notes, and close out the quiz generation performance work deferred from v0.13.0.

### Planned Scope

- **Subject landing pages (SEO)** — proper server-rendered `/public/library/[subject]` landing pages with per-subject `<title>`/`<meta description>`, decay-ranked note cards, and sitemap update; deferred from v0.12.0 and v0.13.0
- **Faster quiz generation** — implement findings from the latency investigation: streaming responses, model selection (`gpt-4.1-mini` for quiz generation), and/or early session creation; deferred from v0.13.0
- **Interview Practice Mode (Professional Profile)** — conversational AI evaluation variant for mock interviews and applied learning scenarios; deferred from v0.13.0 (requires evaluation engine not present in current quiz architecture)
- **Multi-note Long Exam** — extend Long Exam Mode to span multiple notes; requires backend multi-source generation context; deferred from v0.13.0
- **Stale docs cleanup** — audit and update or remove `docs/` files still referencing v0.11.0 or earlier resolved items; deferred from v0.13.0

### ✅ Shipped

_(none yet)_

---

## v0.13.0 - Complete the Promise, Reach New Audiences

**Status: Released**

Theme: ship the modes that were already promised (Long Exam), open NoteLib to a new professional audience, improve organic discovery through subject SEO pages, and close out infrastructure research items deferred from v0.12.0.

### Planned Scope

- ~~**Long Exam Mode v1 (Pro-only)**~~ ✅ — see Shipped below
- ~~**Professional Profile activation**~~ ✅ — see Shipped below
- **Faster quiz generation** — profile LLM latency end-to-end; evaluate streaming, model selection (`gpt-4.1-mini`), and early session creation; implement based on findings
- **Subject landing pages (SEO)** — proper server-rendered `/public/library/[subject]` pages with per-subject `<title>`/`<meta>`; decay-ranked note cards; sitemap update; deferred from v0.12.0
- ~~**Proration / recomputation design doc**~~ ✅ — see Shipped below
- **Stale docs cleanup** — audit and update or remove `docs/` files still referencing v0.11.0 or earlier resolved items

### ✅ Shipped

- **Long Exam Mode v1 — backend** — added Pro-only `/long-exam` endpoints backed by the shared `quick_review_sessions` lifecycle: fixed question generation is committed before session start, `LONG_EXAM` sessions support `PAUSED` pause/resume state, completion returns a mastery report with domain breakdown / weak domains / suggested next step, FeatureGateService owns access control, and Flyway V55 adds the active-or-paused uniqueness guard
- **Long Exam Mode v1 frontend** — added the `/notes/[id]/long-exam` page with prestart, generating, paused-recovery, running, and complete phases; wired Pro paywall gating from Challenge Quiz mode selection; removed the Long Exam `Coming Soon` placeholder; added the legacy `/study-packs/[id]/long-exam` redirect shim
- **Timer fix** — Challenge Quiz and Board Exam time limits are now computed per question (`90s` per Challenge question, `60s` per Board Exam question); generate-more extends the deadline correctly; Long Exam now uses the same server-anchored deadline mechanism
- **Long Exam UI consistency** — running Long Exams now use the Board Exam-style focused top bar, leave/forfeit modal with navigation guard, sticky Previous/Next/Submit footer, beforeunload warning, and one-time focus guidance instead of inline pause/forfeit controls
- **Navigation footer alignment fix** — Challenge Quiz, Board Exam, and Long Exam sticky footers now keep `Previous` left-aligned and Next / Submit / add-question actions right-aligned within the assessment column
- **Professional Profile activated** — `PROFESSIONAL` profile type selectable in profile settings and onboarding (no longer `Coming Soon`); Challenge Quiz shown as `Certification Review` and Long Exam as `Full Practice Exam` for Professional users; learner level grouped picker shows "Recommended for Professionals"; professional dashboard emphasis for certification and career learning; Interview Practice Mode deferred to v0.14.0+
- **Onboarding overhaul** — profile type cards redesigned as a 2×2 grid with emoji icons for all four active profile types (🎓 Student, 📋 Board Taker, 🏫 Teacher, 💼 Professional); step 2 "What's your goal?" replaced with Learner Level + Course/Program step that feeds AI generation context directly; step 2 pre-populates from the user's saved profile on re-entry; step 4 Back button removed post-generation to prevent study pack quota exhaustion; learner level now collected during onboarding instead of deferred to dashboard prompt
- **Board Taker exam date editable in profile settings** — Board Taker users can update their exam date after onboarding via the Profile Type card in profile settings; supports retakes and reschedules; new `PUT /users/profile/exam-date` endpoint; dashboard countdown reflects the updated date
- **Proration / recomputation design doc** — `docs/product/PRORATION.md`; defines upgrade (fresh 30-day cycle, no credit), downgrade (deferred to post auto-renewal), same-plan renewal (stack period, no reset), cancellation (end-of-period, manual refund via support), and quota recomputation rules per scenario; four open questions identified for pre-implementation resolution

---

## v0.12.0 - Learning Experience, Discovery, and Retention

**Status: Released**

Theme: deepen the learning experience, make the product easier to discover and navigate, and improve retention signals that bring users back to study.

### Planned Scope

- **Public Library conversion optimization** *(top priority)* — make public note pages useful as shareable learning pages and top-of-funnel acquisition surfaces; add a learning hook near the top of the public note page so visitors understand the topic before being asked to act; surface a mini quiz preview that lets public visitors answer 1–2 questions before requiring signup; gate full quiz access and progress tracking behind login; add a CTA that encourages visitors to create their own Study Pack from their own notes; reorder CTAs so value is shown before any conversion ask; improve generated note formatting for scannability with shorter sections, clearer headings, key-fact blocks, and exam-friendly paragraph density
- **Public note detail engagement polish** — refine the learning hook fallback and summary-led framing, update Quick Check copy so it feels like a lightweight study prompt, add a post-answer CTA that appears only after the visitor has engaged, tighten CTA wording to `Create your own Study Pack` / `Copy to My Library` / `Share this note`, and improve Full Notes readability without changing quiz/session logic
- **Public creator identity / creator display safety** — public note cards and public note detail should stop relying on `displayName` alone; show `displayName` as the readable label, add a stable public handle or slug when disambiguation is needed, keep profile links tied to a stable public identifier, and preserve existing public links without exposing raw user IDs or emails
- **Learner Level + Course/Program UX refinement** — quiz prompts and metadata suggestions are influenced by the user's saved learner level; Course/Program suggestions are narrowed by the active subject and learner context so recommendations feel personalised rather than generic
- **Conversion funnel optimization** — plan-aware upgrade CTAs (`getUpgradeCtas`) on all paywall and limit surfaces; post-quiz `PostSuccessUpgradeNudge` on Quick Review and Challenge Quiz result screens; analytics events (`QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, `ADAPTIVE_PRACTICE_COMPLETED`, `UPGRADE_CLICKED`) tracked and queryable via admin dashboard
- **Proration / recomputation design** — design how mid-cycle plan changes (upgrade or downgrade) recompute Study Pack and quiz quotas; do not implement until the design is approved
- **Retention loops** — continue-studying prompts on Dashboard; weak-concept reminder emails on a backend schedule; near-limit banners surface reset dates and upgrade CTAs
- **Backend Public Library filtering + shareable URLs** — subject, tags, learner level, and profile-type filters moved to backend query params; each filtered state maps to a shareable URL so students can bookmark or share specific topic collections
- **Library organization guidance** — in-app guidance tells students how to use subjects and Course/Program to keep their private Library organized as it grows
- **Social login (Google first)** — Google OAuth login/signup alongside the existing email-and-password flow; no other providers until Google is shipped and stable
- **Faster quiz generation investigation** — profile current LLM latency for quiz generation; prototype streaming or early-session creation patterns; write findings and a recommended approach before any implementation
- ~~**Profile-aware mode selection UX**~~ ✅ — see Shipped below
- ~~**Long Exam Mode coming-soon foundation**~~ ✅ — see Shipped below
- **Learner Level helper text** — updated inline helper text from generic "Controls difficulty, explanation depth, and quiz complexity." to "Quiz questions and explanations will better match your learning stage."; `getGroupedLearnerLevels()` added to `lib/learning-profile.ts` for future grouped picker UI (Student / Board Taker / Teacher recommendations)
- ~~**Board Exam premium UX polish (presentation-only)**~~ ✅ — see Shipped below
- ~~**Adaptive Practice tier reconciliation**~~ ✅ — aligned `docs/features/adaptive-practice.md`, `docs/features/quiz.md`, `docs/PROJECT_CONTEXT.md`, and runtime gating (`StudySnapProperties`, `application.yaml`) with `PLANS.md`: Plus = 10 sessions / mo, Pro = 30 sessions / mo; `adaptivePracticeProOnly` default corrected to `false`; open discrepancy in `EXAM_MODES.md` closed

### ✅ Shipped

- **Profile-aware mode selection UX** — mode-selection screen shows the right modes per profile: Students see Challenge Quiz + Long Exam (coming-soon); Board Takers see Challenge Quiz + Board Exam; Teachers skip mode-selection and go directly to Challenge Quiz setup; cross-profile escape hatch (`"Preparing for boards? Switch your profile in Settings"`) guides Students who want Board Exam Mode; `lib/exam-mode-visibility.ts` is the single source of truth and is fully unit-tested
- **Long Exam Mode coming-soon foundation** — Long Exam Mode card is live in mode selection for Students with a `Coming Soon` badge; clicking it opens a graceful coming-soon setup screen with a disabled `"Long Exam — Coming Soon"` CTA and a link back to mode selection; mode identity is established in the UI without a backend session; `LONG_EXAM` engine discriminator and session logic ship in v0.13.0
- **Board Exam premium UX polish (presentation-only)** — Board Exam Mode now reads as a higher-ceremony simulation without changing quiz engine behavior:
  - setup screen uses `Begin Board Exam` framing with a five-item pre-flight checklist
  - primary setup CTA is now `Begin Board Exam` while preserving existing Pro gating and mode-switching controls
  - completed Board Exams render a `Score Report` subtitle and formal score-report guidance copy
  - Board Exam results hide the inline learner-level adjustment and post-success upgrade nudge while preserving Challenge Quiz result behavior
- **Learner Level grouped picker on quiz result screens** — Quick Review and Challenge Quiz results now split inline learner-level chips into profile-aware recommended levels and `Other Learning Styles`, preserving the existing chip save behavior while keeping Board Exam results free of learner-level controls

- **Public Library conversion funnel polish (recommendations A–G)** — multi-part audit pass that hardens the public note detail page as a top-of-funnel acquisition surface:
  - **Related notes in quiz completion card (C)** — after finishing the Quick Check, visitors see a "More from {Subject}" section with up to 3 engagement-ranked notes from the same subject; fetched server-side via the existing 5-min cached `getServerPublicNotesBySubjectSlug` call at no extra network cost; linked via canonical public note URLs
  - **Auth-prompt consolidation (D)** — all public copy CTAs (`PublicSeoCopyCta`) now use a single `AppModal` guest-auth surface with Log In + Sign Up buttons and copy-intent redirect URLs so the user lands back in copy flow after authentication; `guestAuthMode` prop removed from all callers; dead tabbed-content component (`public-note-detail-tabbed-content.tsx`) deleted (superseded by the stacked-card SEO layout); legacy redirect routes preserved for external link compatibility
  - **Practice-mode preview teaser (I)** — new `PublicPracticeModeTeaser` static server component placed after Full Notes and before Ownership Actions on the public note detail page, gated on `!isDraft`; shows Challenge Quiz and Adaptive Practice (both free) and Board Exam Mode (Pro chip) as teaser cards to anchor platform value before the copy decision
  - **Time-decayed Featured score (G)** — `computeDiscoveryScore` (frontend `public-library-discovery.ts`) and `computeScore` (backend `PublicNotesScoringUtils`) now apply an age-decay factor: `score = baseScore × max(0.1, 1 / (1 + daysSince / 30))`; notes halve in ranking weight every 30 days; floor of 10% prevents high-engagement legacy notes from permanently outranking fresh content; both implementations accept a `now` parameter for deterministic testing; frontend and backend formulas kept in sync

- **Public Note Quick Check — multi-question preview** — the Quick Check section on public note detail pages now shows up to 3 sequential preview questions (drawn from existing Study Pack quiz data, no new AI generation); a progress indicator (`1 / 3`) tracks where the visitor is; after submitting each answer, feedback microcopy (✅ Correct!, 🧠 Nice work!, Almost there.) and a "Next Question →" button appear before advancing; the final question transitions to a lightweight completion state with CTAs to copy and start practicing; gracefully falls back when fewer than 3 questions exist; notes-first layout is preserved — Quick Check stays below Summary and Key Concepts
- **Google social login** — added Google OAuth as an alternative to email/password login and signup; verified Google emails create or link accounts without duplicating existing users; Google-only users skip separate email verification; Profile now shows simple sign-in method status and a `Connect Google` action for matching account emails; existing email/password login remains supported; foundational connected-account architecture (provider linking, `email_verified` guard, sign-in method tracking) is in place to support future provider management features (unlinking, add-password for Google-only users, multi-provider support) tracked in ROADMAP.md
- **Conversion funnel + quiz UX refinement pass** — focused pass across paywall surfaces, quiz flows, and empty states:
  - `PaywallModal` plan cards are now clickable/selectable with a ring highlight; the action area collapses to a single `Continue with [Plan]` footer CTA that updates as the user switches cards; PRO users see a calm "you're already on Pro" message with no upgrade cards; the redundant per-card PLUS/PRO buttons are removed
  - `StudyPackLimitModal` trimmed to two buttons maximum — primary upgrade CTA + `Maybe Later` for FREE/PLUS; PRO users see a single `Got It` dismiss with no upgrade options
  - `getUpgradeCtas` extended with an optional `UpgradeCtaContext` parameter (`"study-pack-limit"`, `"adaptive-practice"`, `"general"`) for context-aware copy: `Get More Study Packs` when triggered from the study-pack limit surface, `Unlock Adaptive Practice` when triggered from an adaptive-practice gate
  - Challenge Quiz progression microcopy at the last question is now progression-aware: `"Good start — want to keep going?"` at 5 questions, `"10 questions in — push to 15?"` at 10, `"Almost there — finish with all 20?"` at 15, `"You've answered all {n} questions — ready to submit?"` when `noMoreQuestions` or at the 20-question cap
  - Challenge extension toast updated from `+5 questions added` to `"Challenge extended to {n} questions"` or `"Full challenge unlocked: 20 questions"` when the session reaches the cap
  - Quick Review result screen now shows a guidance line `"Review your results, then choose your next study step."` below the result heading; the fallback retry CTA is renamed from `Practice Again` to `Retry Quick Review` for clarity
  - Empty state copy polished: Dashboard `"Start studying smarter"` / `"Add your first note, generate a Study Pack, and start quizzing in minutes."`; Library `"Your note library is empty"` / `"Create a note to get started — generate a Study Pack and quiz yourself in minutes."`
- **Guidance Foundation System** — introduced a minimal guidance engine (`lib/guidance-engine.ts`) with a `GuidanceRule` type and `pickActiveGuidance()` function for priority-ordered, dismiss-aware tip selection; added two contextual one-time tips to the Library: `library-first-note-organization` (notes 1–3) prompts users to add subject and tags, `library-organization-habits` (notes ≥ 5) encourages subject filtering; Dashboard personalization prompt (`"Too easy or too hard?"`) now suppressed for users who already have a learner level set, fixing a bug where it showed even after configuration; prompt moved to appear after the primary study action (Continue Studying / Start Board Exam / Create Teaching Material) for all three profile types so it reads as a secondary refinement rather than a roadblock
- **Retention loop — continue studying + focus areas** — targeted fixes across `DashboardService`, `ContinueSpotlight`, and `DashboardFocusAreasCard` to close the three highest-value retention gaps:
  - Continue Studying session priority reordered to Challenge Quiz → Adaptive Practice → Quick Review — a Challenge Quiz in-progress now always surfaces over a more recently created Quick Review session, matching the learning priority of the more structured mode
  - Continue Studying body copy is now mode-aware: `"You left off on Question 4 of 10 in your Challenge Quiz."` / `"…in your Adaptive Practice."` instead of the generic `"You left off on Question 4 of 10."`
  - Focus Areas action now has a free-tier fallback: when weak concepts exist but Adaptive Practice is locked, Free and Plus users see a `"Revisit Note"` link to the source note instead of only an upgrade prompt; the paywall button is shown only when no note is resolvable
  - `MEANINGFUL_STUDY_ACTIVITIES` constant deduplicated — moved to `ActivityType.MEANINGFUL_STUDY_ACTIVITIES` as a single `public static final` field; `DashboardService` and `RetentionService` both reference the shared constant

### 🐛 Fixes

- **AI subject suggestion resilience** — broad or invalid AI-suggested subjects no longer fail Study Pack generation; invalid suggestions are safely ignored while core summary, key concept, and quiz generation continues. Optional tag metadata issues such as duplicates are filtered without rolling back a valid Study Pack, and valid specific subject suggestions still flow through the normal review/apply path.
- Replaced Google-rendered personalized button with a NoteLib-styled button (outline, Google G icon, "Continue with Google") — eliminates the misleading "Continue as {name}" from appearing inside NoteLib UI; switched to the `google.accounts.oauth2.initCodeClient` authorization code popup flow so no hidden programmatic click is needed; backend now exchanges the authorization code at `https://oauth2.googleapis.com/token` (`redirect_uri: "postmessage"`) and verifies the returned `id_token` JWT, keeping the rest of the auth path unchanged
- Added unique public usernames for stable creator attribution and future creator profile links.
- Public notes now disambiguate creators with `displayName` plus `@username` while keeping display names as readable presentation.
- Login now accepts email or username without breaking existing email login.
- Profile identity settings now allow users to edit their public username.
- Fixed TypeScript type errors in test fixtures (`NoteListItemResponse`, `NotePerformanceSummaryResponse`, `PublicProfileResponse`) to align test data with updated type definitions
- **Quiz Ready badge accuracy** — Quiz Ready indicators are now profile-aware and only appear where they support Teacher/exam-export workflows; Student and Board Taker Library browsing keeps Study Pack Ready as the learner-facing readiness signal
- Fixed Study Pack generation metadata flow so note-level `courseProgram` remains the source of truth and user profile `courseProgram` is used only as a fallback
- Fixed Quiz metadata context consistency — Challenge Quiz and Board Exam now honor note-level Course/Program before falling back to profile context; Note Creation copy clarified Course/Program as domain context
- Fixed Generate from Topic first-generation Course / Program handling so the current Create Note selection is read at submit time and sent immediately, with user profile Course / Program used only when no draft value is selected; `GenerateNoteFromTopicRequest` accepts the optional `courseProgram` field
- Strengthened LLM domain binding — `buildLearnerContextBlock` now emits a domain-constraint directive when Course / Program is set, instructing the model to stay within that academic domain and avoid blending unrelated disciplines
- Added "Tailored for: [Level] · [Course / Program]" visibility line to the note editor floating footer so users can see which context will be applied before generating; an "Adjust" affordance links directly to the optional details section
- Added helper text near the Generate from Topic input: "Your Learner Level and Course / Program help tailor the generated note's depth, terminology, and examples."
- Fixed normal note-owned Study Pack generation so AI `title` / `subject` / `tags` suggestions stay transient until the user applies them
- Fixed AI Suggestions tag comparisons so overlapping user tags are not shown as duplicate new suggestions
- Onboarding keeps its explicit zero-friction metadata auto-apply behavior for empty `subject` / `tags`
- Added shareable URL-based Public Library filters on the canonical `/public/library` route, including a list-page `Share this list` action that copies the current filtered URL
- Consolidated Public Library browsing around `/public/library` and cleaned up the duplicate `/library/public` / `/public/library/{subject}` route wrappers into compatibility redirects
- Polished Public Library URL filter UX with debounced search sync, scroll-preserving filter updates, always-available tag browsing, and stable selector-modal input focus

---

## v0.11.0 - Learning Flow Foundation

### Learning Personalization Polish

- added inline learner level pill-selector to Quick Review and Challenge Quiz result screens so users can adjust their level immediately after a quiz without leaving the review flow; saving shows a toast `Learner level updated. Future Study Packs and quizzes will match this level.`
- restructured Quick Review result screen to show exactly one primary CTA: `Practice Weak Areas` when struggling and Adaptive Practice is available, `Take Another Challenge` after a strong or perfect result, `Practice Again` otherwise
- moved confidence feedback to a secondary collapsed section below the primary CTAs on Quick Review results; selecting a level replaces the option buttons with a badge — `🟢 Confident`, `🟡 Improving`, `🔴 Needs Practice`
- updated Dashboard personalization prompt to `Too easy or too hard?` / `Set your learner level so future quizzes match your study stage.` with a `Adjust level` CTA that navigates directly to the Learning Profile section of `/profile`
- Profile Settings now shows `← Dashboard` back link when reached from the Dashboard "Adjust Level" button, instead of the default public-profile back link
- "Adjust Level" CTA navigates to `/profile?from=dashboard#learning-profile` and auto-scrolls to the Learning Profile card on arrival

### Onboarding Safety

- onboarding Study Pack generation step is now idempotent: `handleStartStudyPack()` checks `draft.noteId` before creating a note and routes to step 4 if a note already exists, preventing duplicate notes from back/forward/refresh behavior
- back button is hidden while Study Pack generation is active during onboarding and the notice copy is replaced with `Your Study Pack is being created. This step can't be undone.`

### Study Pack Metadata Sync

- after Study Pack generation from an existing note, the backend now automatically applies AI-generated `subject` and `tags` back to the source note if those fields are empty — zero-friction, non-destructive, no user prompt required

### Improvements

- repositioned NoteLib as a structured learning system built around the study loop from input to mastery
- updated the landing page hero and learning-loop section to explain the flow: Create -> Understand -> Practice -> Challenge -> Improve
- added Generate Note from topic so users can draft editable notes before saving or generating a Study Pack
- improved Create Note UX with dual entry options: write your own note or generate from topic
- upgraded topic note generation so drafts are more study-ready and structured instead of stub-like filler
- added monthly note-generation limits with paid-plan-aware gating so topic drafting follows the same protection pattern as other credit-based AI actions
- refined the first-study onboarding flow so topic generation stays guided and single-use there, while the standalone New Note page keeps iterative `Generate Again` behavior
- redesigned the New Note page to focus on content creation first and moved `Title`, `Subject`, `Course / Program`, `Tags`, and teacher/admin audience selection into collapsed `Add details (optional)`
- kept Create Note and Study Pack generation low-friction by preserving profile-based defaults and allowing save/generate actions without opening optional metadata
- polished onboarding and generated-note transitions with lighter motion and better scroll-to-content behavior
- added a post-onboarding Dashboard prompt that encourages users to adjust learner level from Profile
- aligned create-note action copy around `Generate Study Pack`
- expanded manual Xendit checkout to support Plus / Pro monthly checkout and Pro yearly checkout using config-driven pricing
- fixed intro-offer voucher application so eligible first checkouts use discounted pricing and successful payments record voucher redemption history
- hardened pending checkout reuse so billing cycle, final amount, and voucher state must still match before an existing Xendit invoice is reused
- replaced legacy single-tier Premium billing with Free / Plus / Pro multi-plan model; plan state is now owned by the `subscriptions` table with one active row per user
- redesigned Settings Plan & Billing with a billing cycle toggle (Monthly / Annual) and three plan cards (Free, Plus, Pro) in a responsive side-by-side layout
- defaulted region to `PH` when the `CF-IPCountry` header is absent so checkout amounts and currency display correctly for local testing and non-Cloudflare environments
- added a cancel plan entry point in Settings for active paid subscribers
- updated product context, roadmap, spec, and release documentation for the current onboarding, billing, and plan model

## v0.10.1 - Landing & Pricing Conversion Polish

### Improvements

- **Landing page conversion polish** — refined the public homepage to reduce hesitation and drive signups:
  - hero headline updated to `Turn your notes into real study tools`
  - hero subheadline updated to `Write or upload your notes, then turn them into summaries, key concepts, and quizzes when it's time to review.`
  - hero secondary CTA changed from `Browse Public Library` to `Try Demo` (proper outline button) so the no-commitment path is front and center
  - hero trust line changed to `Free to start · No credit card required` to directly address signup hesitation
  - added a demo nudge beneath the How It Works steps: `Not ready to sign up? Try the demo first — no account needed.`
  - How It Works step copy simplified to be more direct and scannable
  - Why NoteLib differentiation rows reframed around workflow clarity rather than AI comparison
  - target user descriptions shortened; `Board exam reviewees` renamed to `Board Exam Takers`, `Teachers and tutors` renamed to `Teachers`
  - Public Library body text updated to `Browse notes shared by others. Copy them into your library and turn them into summaries, key concepts, and quizzes.`
  - Pricing Preview heading updated to `Simple pricing. Start free.` with supporting body copy
  - removed redundant `See full pricing` link from the landing pricing preview (covered by the `View Pricing` button on the Premium card)
  - removed the standalone regional pricing block from the landing pricing preview to keep the page clean

- **Pricing page refinements** — improved clarity and compliance on the full pricing page:
  - hero description simplified to `Start free for core features. Upgrade when you need more quizzes, deeper practice, and higher limits.`
  - plan subtitles updated: Free → `For everyday study`, Premium → `For focused exam preparation`
  - feature label normalised: `AI Summary + Key Concepts` → `Summary + Key Concepts`
  - added a static **Regional Pricing** block showing both currencies explicitly without geo-detection:
    - Philippines: `₱249/month` · `₱2,499/year`
    - International: `$4.99/month` · `$39.99/year`
  - added an **FAQ** section covering free access, upgrade timing, regional pricing, and Board Exam Mode
  - comparison table Adaptive Practice row now shows a checkmark instead of session count for consistency

- **PHP pricing compliance (Xendit)** — PHP pricing is now statically visible on the pricing page regardless of the visitor's region:
  - the Regional Pricing block in `PricingPlansSection` is hardcoded and does not rely on Cloudflare geo-detection headers
  - any reviewer accessing `/pricing` from outside the Philippines will still see the PHP pricing block
  - this replaces the previous approach where PHP pricing was only shown if the request region resolved to `PH`
  - intro offer line now shows both currencies statically: `Intro offer: first month ₱199 / $3.99` — previously only the visitor's regional currency was shown, causing Xendit reviewers outside PH to see USD only

- **Mobile conversion polish** — added a sticky `Start for Free` CTA on the pricing page for mobile visitors

## v0.10.0 - Profile Type System & Teacher Flow Phase 1 (In Progress)

### New Features

- **Challenge Quiz entry flow fix** — restored the shared entry screen and premium gating behavior:
  - free users who choose premium-only `Board Exam Mode` now see the premium upsell modal instead of falling into a confusing setup flow
  - free users who exhaust credit-gated `Challenge Quiz` usage now see the premium/upgrade modal immediately instead of entering quiz flow or landing on the monthly-limit page
  - premium users who exhaust `Challenge Quiz` usage still see the real monthly-limit state
  - monthly quiz-limit handling remains separate from premium-feature upsell handling
  - `Student` and `Board Taker` now both enter through the same mode-selection screen again
  - mode selection now uses persona-based default emphasis: `Student` highlights `Challenge Quiz`, while `Board Taker` highlights `Board Exam Mode`
  - the `Challenge Quiz` action on Note Detail now always routes into the shared mode-selection entry instead of dropping users into a setup screen
  - `Board Exam Mode` remains visible to free users inside mode selection and opens the shared paywall modal on click

- **Teacher Dashboard** — added a teacher-first dashboard experience without splitting Teacher into a separate product:
  - keeps the shared Study Pack / note workspace visible through `Recent Notes`
  - replaces student analytics sections with teacher-focused sections: `Create Teaching Material`, `Recently Generated Quizzes`, `Ready to Export`, and `Teacher Help / Tips`
  - links generated quizzes directly into Quiz Preview so export stays inside the teacher review flow
  - refined post-audit teacher guidance so the dashboard welcome copy is teacher-specific and the generated-quiz empty state now routes teachers to a recent ready note when no quiz preview exists yet

- **Persona-based quiz defaults** — quiz entry now emphasizes the right mode per learner intent while keeping the alternate mode reachable:
  - `Student` defaults to `Challenge Quiz` emphasis on the shared mode-selection screen
  - `Board Taker` defaults to `Board Exam Mode` emphasis on the same shared mode-selection screen
  - both personas still start from the same entry screen instead of branching into different first screens
  - `Board Exam Mode` is reinforced as Premium-only at quiz entry through the shared paywall pattern

- **Free-plan credit gating cleanup** — free users now stop at the upgrade modal for credit-gated study actions:
  - `Generate Study Pack` shows the premium/upgrade modal for Free users at `0` remaining
  - `Challenge Quiz`, `Board Exam Mode`, and `Adaptive Practice` use the premium/upgrade modal for Free users when credits or premium access block entry
  - Premium users with genuine monthly exhaustion still see the dedicated limit state instead of the Free-plan upsell flow
  - refined paywall modal messaging so quiz-limit copy now adapts to `Student`, `Board Taker`, and `Teacher` context without creating separate modal implementations
  - standardized broad plan and marketing terminology from `Challenge Quiz` to `Quiz` across pricing, landing, settings billing, and plan comparison surfaces

- **Onboarding / profile wording alignment** — persona naming is now consistent across setup and profile surfaces:
  - onboarding now uses `Board Taker` instead of `Board Exam`
  - onboarding continues to show only the active selectable personas: `Student`, `Board Taker`, and `Teacher`

- **Auth status messaging cleanup** — login messaging now stays reason-based and avoids misleading logout copy:
  - manual logout shows no status banner
  - expired sessions show `Your session expired. Please log in again.`
  - specific sign-out reasons should only be shown when they are reliably detectable; otherwise auth falls back to the session-expired message

- **Post-quiz feedback cleanup** — simplified the result-screen feedback actions:
  - keeps `Yes` and `Give Feedback`
  - removes the duplicate `Send Feedback` button from the quiz-results card
  - aligns icon / label spacing with the shared button system

- **Profile Type System** — formalises the three active profile types (Student, Board Taker, Teacher) with a controlled availability model:
  - `PROFESSIONAL` and `PARENT` are now visible in the Profile Type card but not selectable — they show a "Coming Soon" badge and remain disabled until the personas are ready
  - active types (Student, Board Taker, Teacher) use a visual card-list selector with radio indicator and one-line description instead of a plain `<select>`

- **Profile switching confirmation modal** — switching to a different profile type now requires a confirmation step:
  - modal copy is mode-specific (not generic) and explains what changes with the new mode
  - modal always includes "You can switch back anytime."
  - on confirm: saves the new type and shows a post-switch toast that auto-dismisses after 4 seconds
  - on cancel: closes without saving — selected UI state stays but no API call is made
  - toast copy is mode-specific (e.g. "You're now in Board Taker mode — focused for exam prep.")

- **Mode system (`frontend/lib/profile-mode.ts`)** — introduces a clean mode layer above profile types so shared components branch on mode, not on profile name:
  - `ProfileMode`: `"LEARNING"` (Student, Board Taker) or `"TEACHING"` (Teacher)
  - `resolveProfileMode()` is the canonical resolution function
  - `ACTIVE_PROFILE_TYPES` and `DISABLED_PROFILE_TYPES` constants centralise availability rules
  - `getProfileTypeSwitchContent()` returns mode-specific confirmation copy for each active type

- **Teacher Flow v1**
  - Added quiz generation for teachers with a note-owned `generatedQuiz` model instead of quiz sessions
  - Introduced Quiz Preview with answers and explanations visible by default
  - Moved Export into the dedicated quiz view for better context
  - added Teacher DOCX export from Quiz Preview using stored `generatedQuiz` data only — no LLM calls, no quiz session reuse
  - teacher export now supports `Quiz Only` and `Quiz + Answers` as downloadable `.docx` files
  - added teacher Library `Select` mode and `Exam Builder` for combining multiple quiz-ready notes into one ordered DOCX exam export
  - `Exam Builder` now supports handle-based drag-and-drop reordering with `@dnd-kit`, while keeping `Move up` / `Move down` controls as the accessibility fallback
  - `Exam Builder` now adds teacher-defined sections with inline titles, drag-reorderable section groups, and note movement across sections before export
  - combined exam export supports note reordering plus optional `Answer Key` and `Explanations` sections in the generated document
  - combined exam DOCX export now preserves section order and section headings from the teacher builder instead of flattening notes into one unnamed list
  - `Exam Builder` now supports both `Even Balance` and `Smart Balance` for section redistribution:
    - `Even Balance` keeps the original deterministic equal-slice behavior
    - `Smart Balance` keeps counts even while spreading note coverage, concept coverage, and soft template intent where metadata exists
  - combined exam export now uses question-level section assignments, so balanced sections export exactly the same grouped question order shown in the builder
  - finalized v0.10.0 limit-state wording so paywalls, premium exhausted states, and teacher quiz generation use consistent `limit` terminology instead of mixed `usage` copy
  - fixed teacher quiz preview regeneration to use the dedicated quiz-generation paywall context instead of the student quiz-limit context
  - refined the Send Feedback modal for mobile with a roomier bottom-sheet-style layout and safer textarea spacing
  - unified teacher export entry points so Quiz Preview and Exam Builder both open the same two-option export chooser: `Quiz Only` or `Quiz + Answers`
  - reduced teacher Quiz Preview `Regenerate` to a lighter secondary action so `Export` stays the primary CTA, especially on mobile
  - Added regeneration with credit usage and confirmation
  - Removed student-only quiz actions, performance UI, recent sessions, and Board Exam references from Teacher mode note detail

- **Final UX polish pass** — tightened the last release-blocking workflow details without changing core behavior:
  - Create/Edit Note now uses one shared sticky footer across profiles with `Save Note` and `Generate`, replacing duplicated top and bottom action clusters
  - Library now includes local readiness chips for `All`, `Quiz Ready`, and `Study Pack Ready` to make teacher quiz-ready notes easier to scan
  - readiness badges are visually separated more clearly: `Study Pack Ready` uses a neutral/blue treatment while `Quiz Ready` uses green
  - Exam Builder now uses the same export-choice wording as Quiz Preview, removes the old answer/explanation toggle combinations, and adds enough bottom spacing so the sticky footer no longer covers content on mobile
  - Exam Builder note cards now keep better mobile readability with two-line title clamping and clearer drag-state feedback on the handle and active item

- **Note target profile type system** — notes now store who they are written for separately from the creator's profile:
  - added required `notes.target_profile_type` with `STUDENT` and `BOARD_TAKER`
  - cleaned up incorrectly assigned teacher-target notes by falling back `TEACHER` -> `STUDENT` through a follow-up migration
  - `Student` and `Board Taker` note creation now auto-assign the note target profile from the current user profile without showing extra UI
  - `Teacher` and `Admin` note creation/editing now require `Who is this note for?` with `Student` and `Board Taker` options
  - post-generation metadata editing now lets `Teacher` and `Admin` change note audience without triggering regeneration; the change only affects future quiz generation
  - Public Library filtering now uses `note.targetProfileType` instead of creator profile type and offers `All`, `Student`, and `Board Taker`
  - category-empty Public Library states now guide users to `View all notes` when no notes exist yet for the selected audience

- **Loading-state system** — standardized the app’s loading feedback for async actions, delayed redirects, and fetched sections:
  - shared `Button` loading state now shows one consistent spinner treatment and disables duplicate clicks while requests are pending
  - mounted a subtle top route-progress indicator so delayed programmatic navigation no longer feels unresponsive
  - applied the shared pending pattern to auth submit, profile/settings saves, sign-out, teacher quiz generation/regeneration, export actions, and waitlist/paywall actions
  - standardized high-visibility skeletons across dashboard, profile, settings, generated-quiz preview, strongest-notes, and public-library loading states
  - tightened duplicate-action protection so repeat taps during in-flight async work do not create confusing extra requests

- **Challenge Quiz Note Detail entry hardening** — fixed the recurring routing drift from Note Detail into quiz setup:
  - the Note Detail `Challenge Quiz` button now always enters through the shared initial mode-selection screen for both `Student` and `Board Taker`
  - `Student` still defaults to `Challenge Quiz` emphasis there, while `Board Taker` still defaults to `Board Exam Mode`
  - the challenge-quiz page now treats the shared mode-selection entry as the single source of truth and no longer lets session-recovery logic bypass it into setup or running state

- **Action-aware paywall and exhausted messaging system** — unified contextual messaging for free-user gating and premium-user limit states across all supported actions:
  - new shared `frontend/lib/paywall-content.ts` centralises copy for five action contexts: Study Pack, Quiz, Board Exam, Quiz Generation, and Adaptive Practice
  - `FREE_PAYWALL_CONTENT` maps each `PaywallAction` to title, body, analytics feature string, and dismiss label — imported by `PaywallModal` so all free-user gating uses the same content rules
  - `PREMIUM_EXHAUSTED_CONTENT` maps each `PaywallAction` to title and body — imported by premium limit-reached states so exhausted messaging is action-specific and not generic "Monthly limit reached"
  - **PaywallModal** (`components/billing/paywall-modal.tsx`): removed inline per-variant content constants and the profileType-based `resolveQuizLimitMessage()`; now resolves copy through `resolvePaywallAction(variant)` → `FREE_PAYWALL_CONTENT[action]`; keeps inline fallback content for `difficulty-selection` and `ocr-limit` which are out of scope for action-aware copy
  - **New variant `quiz-generation-limit`** added to `PaywallModalVariant` — maps to `QUIZ_GENERATION` action; used when Teacher (Creator mode) hits the quiz generation limit, giving a distinct title ("You've reached your quiz generation limit") separate from the student quiz limit ("You've reached your quiz limit")
  - **Teacher quiz generation gating** in Note Detail now uses `"quiz-generation-limit"` instead of `"challenge-quiz-limit"` for accurate action context; analytics source strings updated to `private_note_detail_teacher_quiz_generation_limit`
  - **Challenge Quiz page `limit-reached` card**: heading updated from generic "Monthly limit reached" to "You've used all your quiz credits for this month" with reset cycle body copy
  - **Adaptive Practice page `limit-reached` card**: heading updated from "Monthly limit reached" to "You've used all your quiz credits for this month" with Adaptive Practice-specific body copy
  - **`StudyPackLimitModal`**: free plan and premium plan titles and body copy updated to match `FREE_PAYWALL_CONTENT.STUDY_PACK` and `PREMIUM_EXHAUSTED_CONTENT.STUDY_PACK` respectively; reset date is still surfaced when available
  - `resolvePaywallFeature()` in Note Detail updated to handle all seven current `PaywallModalVariant` values including the new `quiz-generation-limit` and `ocr-limit`
  - back navigation in limit-reached cards uses short destination label `Note` per Back Navigation Rule

### Documentation

- `docs/product/SPEC.md`: updated teacher dashboard purpose, teacher quiz separation, persona-based quiz defaults, and auth/login message rules
- `docs/features/dashboard.md`: documented the teacher-first dashboard sections and profile-specific priorities
- `docs/features/authentication.md`: documented session-expired vs manual-logout messaging rules
- `docs/features/teacher-flow.md`: clarified how Teacher Dashboard feeds the Generate -> View -> Export teacher lifecycle
- `docs/product/ROADMAP.md`: added Public Library persona filtering as a future direction
- `docs/product/SPEC.md`: documented note target profile ownership, teacher audience selection, and Public Library audience filtering defaults
- `docs/features/public-library.md`: documented note-audience rails and category-empty-state behavior
- `docs/features/study-library.md`: documented note target audience assignment during note creation and copy behavior

---

## v0.9.0 - Learning Experience & Product Polish (In Progress)

### New Features

- **In-App Guidance System** — lightweight contextual guidance helps users understand features without blocking or overwhelming:
  - micro-guidance text added to key form fields: Subject and Course / Program on the note editor explain what each field does; Course / Program on the Profile page explains how it affects recommendations
  - quiz mode description line added below the Study Pack action buttons on Note Detail explaining the difference between Quick Review and Challenge Quiz
  - `GuidanceTip` component: a subtle, dismissible one-time tip strip backed by `localStorage` — fades in on first visit, dismissed permanently with a single click
  - first-time-quiz nudge on Note Detail Performance Overview: when a Study Pack is ready but no quiz sessions exist, a tip prompts the user to try Quick Review or Challenge Quiz
  - Help Center page at `/help` refactored into a card-based layout matching the Learn page design: six topic cards (Getting Started, Creating Notes, Study Packs & Quizzes, Performance & Insights, Export & Sharing, Profile & Settings) plus a Student Guide card linking to `/learn` and a support footer; clicking any topic opens a modal with detailed Q&A without leaving the page
  - Help is accessible from the avatar dropdown menu (next to Settings) and from a "Help Center" link in the Settings page header
  - `"help"` added as a new `ActionIconName` using the `HelpCircle` icon from Lucide
  - **Design token system** — established a lightweight, semantic token layer on top of the existing Tailwind v4 + CSS custom properties infrastructure; added four new tokens: `--primary` / `--primary-hover` / `--primary-active` (brand color with correct light/dark values) and `--surface-alt` (card surface background); mapped to Tailwind utilities via `@theme inline` so `bg-primary`, `hover:bg-primary-hover`, `active:bg-primary-active`, `bg-surface-alt` work without `dark:` prefixes — dark mode is handled entirely by CSS variable substitution, making the system theme-ready; updated `button.tsx` default variant (7 hardcoded blue classes → 3 semantic tokens), updated `card.tsx` surface background, updated step circles in all three guide modals; documented full token table, radius/spacing/shadow conventions, and token usage rules in `docs/product/SPEC.md`
  - **UI system — card hierarchy and icon standardization** — audited card and icon usage across Dashboard, Help, Library, Note Detail, and guide modals; result: card hierarchy is now documented and intentional (Primary Action / Secondary Info / Content / Inner Utility levels); icon container system standardized across all six guide modal components — section icon containers (`h-7 w-7 rounded-lg border border-border bg-muted/40`) now consistently use `h-4 w-4` icons instead of the prior mixed `h-3.5 / h-4` split; page-level Help card icons (`h-8 w-8` container) remain intentionally larger to maintain hierarchy between page cards and modal inner sections; card padding, border, and hover patterns confirmed consistent across the app; card hierarchy and icon rules documented in `docs/product/SPEC.md`
  - **Help modal content refactor** — all Help Center modals now use structured, scannable layouts instead of plain Q&A text blocks: Creating Notes (4 sections: note content, Subject/Course fields, post-generation editing, Make a Copy), Study Packs & Quizzes (6 sections: Summary, Key Concepts, Quick Review, Challenge Quiz, Adaptive Practice with Premium badge, Weak Concepts), Export & Sharing (3 sections: export options with export type bullets, file format, public sharing); each section uses an icon badge, title, 1–2 sentence description, optional bullet list, and optional CTA link; `HelpCard` type simplified — `items` array removed, `modalDescription` added as optional field; modal render delegates to a `GuideContent` switch component instead of a generic Q&A fallback
  - **Help page card cleanup** — reduced Help Center from 8 cards to 6 by removing Performance & Insights and Profile & Settings (not primary help needs); remaining cards are Getting Started, Creating Notes, Study Packs & Quizzes, Export & Sharing, Student Guide, and Teacher Guide; 6 cards now fill a clean 2×3 grid on desktop with no orphaned rows
  - **Modal step alignment fix** — connector lines in step-based guide modals (Getting Started, Student Guide, Teacher Guide) now use `flex-1` instead of `h-full` so the line reliably extends to the bottom of each step card; gap between circle and connector adjusted to `mt-1.5` for consistent vertical rhythm across all screen sizes
  - **Teacher Guide** added to Help Center: a 4-step workflow (Add Lesson Material → Generate Study Pack → Review the Output → Export for Reuse) explaining how teachers can use NoteLib today to turn lesson notes into quiz-ready study packs and exportable review PDFs; includes an honest "where NoteLib fits today" note scoped to current capabilities, plus practical tips; uses the same step-card modal pattern as the Student Guide and Getting Started guide
  - **Progressive in-app hints** — three new one-time `GuidanceTip` placements using the existing localStorage-backed dismissal system: (1) Note Detail draft state — shows when a note has no Study Pack and is ready to generate, message explains what unlocks after generation; (2) Session History empty state — shows when no quiz sessions exist, message explains that completing a session unlocks review and PDF export; (3) Public Library — shows on first visit, message explains how to copy notes into your own library; all three are automatically hidden after dismissal and never shown again
  - **AppModal scroll and close usability** — modal panels that contain long content (Student Guide, Getting Started) are now fully accessible without scrolling the page: panel is capped at `90dvh` with `overflow-hidden flex-col`, the content area scrolls independently with `overflow-y-auto`, and header/actions stay fixed; an always-visible X close button is rendered in the top-right corner of every modal, regardless of whether `headerActions` are provided, making it easy to dismiss on both desktop and mobile

### Bug Fixes

- **Manual logout redirect no longer polluted by route guard** — fixed a race condition where logging out from Note Detail could land on `/login?redirect=...&reason=auth_required` instead of the clean `/login?reason=logged_out`: after `clearAuthUser()` emits `studysnap-auth-change`, Note Detail's auth re-check called `redirectToLoginWithCurrentDestination` before the logout navigation completed, overwriting the logout-initiated redirect; fix: `redirectToLoginWithCurrentDestination` in `route-guards.ts` now checks `isManualLogoutInProgress()` and returns early when a manual logout is in progress — the logout handler remains the sole owner of that navigation; new test added to `route-guards.test.ts` verifying the route guard does not redirect during manual logout

- **Note Detail context menu no longer floats into the top bar** — the three-dot menu trigger was positioned with `absolute right-4 top-4 z-10` inside a `relative` Card, causing it to visually overlap the sticky page header during scroll and appear disconnected from the title row; restructured to inline: trigger now lives inside a `flex items-start gap-3` row alongside the title div, using `relative shrink-0 self-start` — no more `absolute` outer wrapper or `pr-14` title padding; the dropdown panel remains `absolute right-0 top-12 z-20` relative to the trigger container, which is correct

- **Auth redirect safety after session expiry on shared devices** — fixed a cross-account redirect vulnerability where a different user logging in after a session expiry was redirected to a protected resource owned by the previous user: `handleUnauthorizedSession` now stores the expired user's ID in `sessionStorage` before clearing auth; `resolvePostLoginDestination` for `reason=session_expired` now only follows the saved redirect when the newly logged-in user ID matches the stored expired user ID — any other user lands on the dashboard; the stored ID is cleared on every successful `setAuthUser` call; logout behavior (`reason=logged_out`) is unchanged — it never includes a redirect param and always returns to the dashboard; 5 new tests added covering same-user restore, different-user block, no-stored-ID fallback, setAuthUser clearing, and handleUnauthorizedSession storage

- **Quiz session history + review** — Note Detail now keeps completed quiz history tied to the note so past practice is reviewable instead of disposable:
  - adds a `Recent Sessions` section below `Performance Overview` on Study Pack-ready notes, combining Quick Review and Challenge Quiz attempts in reverse-chronological order
  - `Review session` now opens one dedicated session-review page on both desktop and mobile for a clearer and more stable interaction model
  - removes the fragile desktop inline review and auto-scroll behavior in favor of one consistent route from `Recent Sessions`
  - the dedicated review page gives score summary, weak concepts, questions, and explanations enough width to stay readable across screen sizes
  - Session Review now includes a structured `Export` action with three options grouped under `Review Materials`:
    - `Full Review` — all questions with answers, explanations, and score summary; filename `notelib-quiz-[title]-[date].pdf`
    - `Mistakes` — incorrect answers only with focused `Mistakes Review` section, mistake count, accuracy, and weak concepts; handles perfect-score edge case with `Perfect Score!` message; filename `notelib-mistakes-[title]-[date].pdf`
    - `Weak Concepts` — questions from identified weak concept areas with `Weak Concepts Review` section and `Questions from Weak Areas` list; handles no-weak-concepts edge case gracefully; filename `notelib-weak-concepts-[title]-[date].pdf`
  - desktop Export shows a compact grouped dropdown; mobile shows a bottom sheet with title, subtitle, large tap targets, and Cancel
  - the bottom sheet uses a slide-up entry animation (`motion-export-panel`) that switches to the standard dropdown animation on `sm+` via CSS media query
  - exported PDFs use note title, quiz type, generated date, and subtle `Generated by NoteLib` footer — all built from stored session data without LLM calls
  - lets users open a completed session using stored session data only for question-by-question answers, explanations, and correctness
  - derives concept breakdown and weak concepts from persisted quiz/session state, keeping the weak-concept threshold aligned at `< 60%`
  - older sessions without enough stored quiz detail degrade gracefully with concept summary and weak-concept feedback instead of failing the page

- **Library filtering and search upgrade** — the private Library now behaves more like a structured study workspace:
  - keeps search as the primary entry, filtering Library notes in real time by title and tags
  - uses subject-first horizontal scroll chips with single-select `All` default so the main filter stays fast and lightweight
  - limits tags to a compact `Popular Tags` rail with `+ More` progressive disclosure instead of exposing the full tag list by default
  - opens searchable subject and tag selectors in the shared bottom-sheet/modal pattern, with sticky search, `Apply`, and `Clear` actions
  - tag selector now shows selected tags in a dedicated top section so users can quickly deselect without rescanning the full list
  - Library multi-select tags now use OR logic by default so combining tags from different notes broadens results instead of creating false empty states
  - notes missing an explicit subject still derive a temporary fallback subject from existing metadata so subject grouping works consistently

- **Landing page Public Library preview** — the homepage now visually demonstrates the Public Library experience instead of relying on copy alone:
  - refined the section into a responsive text-and-preview layout so the screenshot supports the message instead of dominating the page
  - uses `public/landing/feature-public-library.jpg` inside a framed product-preview container with constrained height, rounded corners, and subtle depth
  - keeps the preview balanced across desktop and mobile with text-first stacking on small screens

- **Performance by Note on Profile and Dashboard** — replaced the flat "Best Sessions" list with a note-grouped performance view that shows how well the user knows each note:
  - Profile "Top Performance by Note" card groups all QUICK_REVIEW and CHALLENGE sessions by note, computing best score, average score, attempt count, and last attempted date per note; sorted by best score DESC
  - each row shows `⭐ Perfect` (100%) or `Top Score` (≥80%) badge, note title, best/average percentages, attempt count, and last attempted date
  - clicking any note opens the Session Review page for the best session on that note, with back navigation returning to the Profile page
  - Dashboard "Strongest Notes" section shows the top 3 notes by best score with a "View all" link to the Profile page; back navigation from those sessions returns to the Dashboard
  - Session Review back link is now source-aware: navigating from Profile shows "← Profile", from Dashboard shows "← Dashboard", from the note page shows "← Note"
  - backend: `GET /dashboard/note-performance?limit=N` replaces `/dashboard/best-sessions`; groups sessions by noteId in service layer, returns `NotePerformanceSummaryResponse` with bestScore, averageScore, attemptCount, lastAttemptedAt, bestSessionId, bestSessionMode, and noteTitle

- **Public Library evaluation signals** — public notes now expose a lightweight like system so note quality is easier to judge without turning discovery into a social feed:
  - authenticated users can toggle one like per public note, with likes stored per user-note pair and duplicate likes prevented server-side
  - Public Library cards now show a subtle heart count beside the existing view/copy signals, and guests who tap like see an auth prompt modal instead of a silent failure
  - Featured ranking now uses `viewCount + (copyCount * 3) + (likeCount * 2)` while Most Popular keeps copies first, views second, and uses likes as the next tie-breaker
  - public note cards can now show a lightweight `❤️ Well liked` badge when a note reaches the like threshold, keeping the evaluation model simple and student-facing

- **Dedicated How it Works page** — product walkthrough content now has its own public route at `/how-it-works`:
  - the new page explains the full NoteLib flow with real screenshots for note editing, Study Pack generation, quiz practice, and results review
  - `/how-it-works` also includes the simple 3-step overview, Board Exam Mode highlight, and a closing signup CTA
  - the walkthrough reuses the shared optimized screenshots from `public/landing`
- **Landing page screenshot integration** — the public homepage now shows real product UI instead of abstract product illustrations:
  - hero now uses the real note-editor screenshot to ground the product story immediately
  - the homepage walkthrough has been simplified so the detailed multi-screenshot explanation now lives on `/how-it-works`
  - landing screenshots now share one polished treatment: rounded corners, soft shadows, preserved aspect ratios, and subtle hover scale
  - the public navbar and footer now surface `How it Works` so deeper product guidance is easier to find
- **Theme system refresh** — NoteLib now supports `Light`, `Dark`, and `System` theme modes as the first polish feature of `v0.9.0`:
  - `Settings > Preferences` now includes a dedicated theme selector instead of relying only on a utility toggle
  - `Settings` keeps the always-visible inline `Light` / `Dark` / `System` segmented selector for fast preference changes
  - the shared top-bar theme control now uses a simpler responsive inline pattern:
    - desktop shows a compact always-visible icon-only theme group with tooltips
    - mobile keeps a compact trigger that expands an inline theme panel
  - the top-bar `System` option now uses a monitor-style icon on desktop and a phone-style icon on mobile
  - the mobile top-bar theme control no longer relies on the unstable popup rendering path and now expands cleanly without clipping into the header area
  - the desktop top-bar theme control is now more compact, with tighter icon sizing and spacing while keeping the same theme actions
  - the public header now uses a subtle separator between the theme utility group and the `Login` / `Get Started` actions
  - `System` follows the device `prefers-color-scheme` setting and updates while the app is open
  - theme choice persists locally and also syncs through the existing authenticated theme-preference API when available
  - initial theme classes are applied before the main UI renders to avoid flashing the wrong theme on load
  - theme changes now use subtle color-only transitions for background, text, and borders
- **Motion system foundation** — NoteLib now uses a tighter shared motion language for polish without slowing study flows:
  - shared motion tokens now live in the global CSS layer so durations and easing stay consistent
  - cards and shared buttons now use lightweight surface and pressed-state motion utilities instead of one-off transition values
  - the Challenge Quiz Question Navigator now uses a shared collapse/expand motion pattern instead of abrupt mount/unmount behavior
  - Quick Review and Challenge / Board Exam result-review surfaces now use gentle fade-in entry motion for calmer state changes
  - quiz-critical interactions such as answer selection, timer updates, and question progression intentionally avoid extra motion so focus and responsiveness stay intact
- **Review Answers UX polish** — Challenge Quiz review is now clearer and more learning-focused without changing scoring or quiz generation:
  - Review Answers now starts with a compact summary for correct count, total questions, percentage, performance level, and weak concepts
  - each reviewed question now shows explicit `Correct` / `Incorrect` state plus `Your Answer` and `Correct Answer` summaries before the choice list
  - answer review now supports `All Questions` / `Incorrect Only` filtering alongside per-question explanation toggles and shared `Expand All` / `Collapse All` controls
  - explanation disclosure uses the shared lightweight motion system to avoid abrupt layout jumps
  - Challenge Quiz review now ends with clearer next-step actions such as `Practice Weak Concepts` and `Review Study Pack`
- **Local quiz-generation mock mode** — local development can now exercise quiz UIs without burning real LLM tokens:
  - `QUIZ_GENERATION_MODE=mock` stubs only Challenge Quiz, Adaptive Practice, and Board Exam generation while leaving Study Pack generation on the normal provider path
  - mock mode still preserves normal quiz session creation, `GENERATING` / `IN_PROGRESS` flow, idempotent reuse, result handling, and review-answer compatibility
  - `QUIZ_GENERATION_MOCK_DELAY_MS` can add a small local-only delay to test generation overlays and loading states more realistically
  - production remains on the real quiz-generation path unless the quiz-specific mock flag is explicitly overridden

### Fixes

- **Public Library evaluation system audit + trust signal refinements** — audited all evaluation signals, ranking logic, and metric display against the intended philosophy; preserved all working logic and refined only what was inconsistent:
  - audited views/copies/likes display, badge system, discovery ranking, and zero-value handling — all core logic preserved with no threshold or formula changes
  - confirmed zero-value rules: views and copies already hidden at 0 on cards; like count now also hidden at 0 so the heart button shows only when engagement exists, aligning all three metrics under one rule
  - resolved emoji ambiguity: Featured Notes section uses ⭐ (quality signal, aligns with High Quality badge) and Most Popular section uses 🔥 (social proof signal, aligns with Popular badge) — previously both used 🔥
  - Featured Notes now rank only study-ready public notes with meaningful summary, quiz content, and note preview using `views + (copies * 3) + (likes * 2)`
  - Most Popular now requires real social proof (`copies >= 3` or `views >= 20`) and the Popular badge threshold now matches that rule
  - Recently Added remains freshness-based with `createdAt DESC`
  - badge priority rules (High Quality > Well liked > Popular, max 2 per card) confirmed correct and unchanged
- **Public Library copy-flow cleanup** — public note copying now behaves consistently across discovery and detail surfaces:
  - Public Library now keeps search first, moves subject and tag browsing into compact horizontal rails, and uses searchable `+ More` selectors so filtering scales without vertical clutter
  - Public Library multi-select tags now use OR logic by default so combining tags broadens results instead of creating false empty states
  - Public Library cards now use a smaller inline `Save` action with iconography, guest auth prompt modal, and muted `Saved` state instead of the old full-width copy CTA
  - mobile card presentation is tighter and more scan-friendly, with compact preview spacing, limited tags, and metadata plus action aligned in one footer row
  - repeated copies of the same public note by the same user now reuse the existing copied note instead of creating duplicate drafts
  - successful public copies now use a more polished success surface with stronger title hierarchy, subtle success iconography, right-aligned desktop actions, and cleaner mobile sheet spacing
  - copied private notes now show `Copied from {title} in Public Library.` attribution when source metadata exists
- **Private Note Detail action cleanup** — secondary note-management actions no longer compete with study actions inside the note header:
  - inline `Edit`, `Delete`, `Make a Copy`, and `Share` controls were consolidated into a single top-right `⋯` context menu
  - the `⋯` trigger is now anchored to the card corner on both mobile and desktop instead of sitting inside the metadata flow
  - the note detail card now keeps primary study actions visually dominant while still exposing full note management
  - the shared menu works on both mobile and desktop, closes on outside click, and avoids the old multi-row utility-button clutter
- **Feedback launcher sticky-CTA conflict fix** — the floating `Send Feedback` button is now hidden on routes with sticky or fixed bottom primary actions, including Note Editor and in-progress quiz flows, so it no longer overlaps `Generate`, `Next`, `Submit`, or similar bottom CTAs on mobile.
- **Feedback UX cleanup for core study flows** — feedback entry is now more intentional across learning surfaces:
  - core authenticated learning routes now use a subtle header feedback icon instead of the floating launcher
  - the floating launcher remains only on safe non-critical authenticated pages such as Dashboard, Library, Public Library, and Settings
  - quiz result screens now ask `Was this quiz helpful?` with lightweight `Yes` and `Give Feedback` actions before the deeper review feedback panel
- **Unified animation and interaction system** — all UI interactions now follow one consistent timing, easing, and feedback language across the app:
  - `--motion-duration-fast` tuned to 150ms so all quick interactions stay within the 120–180ms spec
  - new `motion-dropdown-panel` CSS utility animates all dropdown and context menus with a fade-in + 6px slide-down entry (150ms ease-emphasized) — applied to the note actions menu, visibility menus, export menu, avatar menu, combobox listbox, and mobile nav panel
  - new `motion-lift` CSS utility adds a subtle 1px hover lift (`translateY(-1px)`) to small interactive elements, correctly suppressed during press and for disabled elements — applied to all dropdown/context menu items across the app
  - `motion-pressable motion-lift` applied to filter chips (Public Library) and the like badge for press scale + hover lift on pill-shaped interactive elements
  - theme selector unselected buttons gain `motion-lift` hover lift for consistent feel within the control
  - `prefers-reduced-motion` block covers all new utilities so users with motion sensitivity see zero animation
- **Theme-aware highlight and interaction system** — all hover, active, and selected states now use primary-color-tinted tokens instead of opaque grey values, giving a consistent blue-tinted interaction language that adapts cleanly to light and dark themes:
  - new `--highlight` token (`rgb(37 99 235 / 0.08)` light, `rgb(59 130 246 / 0.08)` dark) drives all hover highlight states; `--highlight-strong` (`0.15` opacity) drives active/pressed states and selected chip fills
  - new `--muted` token (`#e5e7eb` light, `#374151` dark) registered in `@theme inline` so `bg-muted/*` utilities now generate CSS where previously they were silently invisible
  - all `hover:bg-muted/*` and `active:bg-muted/*` classes across every component replaced with `hover:bg-highlight` / `active:bg-highlight-strong`; replaced in 17 files covering sidebar, avatar menu, navbar, filter chips, export menus, context menus, dropdown lists, session history, card hovers, visibility menus, theme toggles, combobox options, and the feedback widget
  - `Button` `outline` and `ghost` variants now use `hover:bg-highlight` / `active:bg-highlight-strong` and drop the explicit `dark:hover:bg-gray-*` overrides since the token already handles dark mode
  - active sidebar nav links use `bg-highlight-strong` instead of a hardcoded `bg-blue-600/15 dark:bg-blue-500/20`; active public navbar links use `bg-highlight` instead of `bg-blue-600/10 dark:bg-blue-500/15`
- **Interactive element feedback polish** — tap and hover feedback is now consistent across all interactive surfaces so nothing feels unresponsive on touch or desktop:
  - all three `Button` variants (`default`, `outline`, `ghost`) now carry explicit `transition-colors` and `active:` pressed states
  - destructive confirm button in Delete modal gets a red `active:` state consistent with the danger intent
  - AI Suggestion modal radio labels now show a highlight `active:` press state alongside the existing hover
  - Public Library Like badge (`unlike` state) has a tap state matching other badge interactions
  - Theme selector, filter chips, source filter labels, sort sheet options, and mobile nav links all carry consistent tap feedback
- **Mobile note header overflow fix** — long private note titles no longer push `Edit` / `Delete` outside the header card on small screens:
  - mobile Note Detail now stacks the title above the action row
  - `Edit` and `Delete` stay inline again from `sm` upward so desktop layout remains unchanged

## v0.8.0 – Board Exam Mode + Public Library Discovery System (In Progress)

### New Features

- **Pricing page + Premium positioning** — `/pricing` now presents a cleaner two-plan comparison for the current pre-launch stage:
  - Free and Premium are the only visible plans
  - Free highlights `10` Study Packs/month, `5` Challenge Quizzes/month, AI Summary + Key Concepts, Weak Concepts tracking, and `Board Exam Mode (Free for limited time)`
  - Premium highlights higher limits, Adaptive Practice, Difficulty selection, and Board Exam Mode
  - the comparison table now focuses on the core study features users actually choose between
  - upgrade CTAs still route into the Premium waitlist flow instead of payment
- **Post-Quiz UX Polish** — Unified quiz result UX across Quick Review, Challenge Quiz, and Adaptive Practice:
  - Removed all "Note" buttons from quiz screens; replaced with `← Back to Note` text link placed **below** action buttons (not grouped with them)
  - Quick Review confidence feedback: selecting a confidence level now replaces the option buttons with a styled badge — `🟢 Confident` (HIGH), `🟡 Improving` (MEDIUM), `🔴 Needs Practice` (LOW); "Thanks for the feedback." text removed
  - Adaptive Practice result screen: "Generate New Set" is now the primary action; "Note" button removed
  - Challenge Quiz result screen: "Practice Weak Concepts" is now primary (when present); "Start Another Challenge" and "Review Answers" are secondary; "Note" button removed
  - Review Answers now uses a shared learning-focused layout across Quick Review, Challenge Quiz, and Adaptive Practice with selected/correct answer badges, concept chips, visible explanations, Previous/Next navigation, and an `Incorrect only` filter for missed questions
  - Final result-flow alignment pass: Quick Review now promotes `Practice Again` when weak practice is locked, Challenge Quiz promotes `Start Another Challenge` when no weak concepts exist, Adaptive Practice shows a clear empty targeted-weak-areas state, and quiz edge states use text-link `← Back to Note` navigation instead of navigation buttons
  - Adaptive Practice `completionMessage` upgraded to use `mapPerformanceLevel` 4-tier thresholds (Excellent / Good / Fair / Needs Improvement) instead of a 2-tier check
  - Error cards in Adaptive Practice (error, premiumLocked, prestart) no longer have a redundant "Note" button — the persistent `← Note` BackLink at the page header handles navigation
- **Board Exam Mode (Phase 1)** — Challenge Quiz is now a true exam experience:
  - Challenge Quiz now opens with explicit mode selection between `Challenge Quiz` and `Board Exam Mode` instead of auto-starting or inferring exam mode from Premium-only capabilities
  - Challenge Quiz entry is now split into `Mode Selection` then `Prescreen`, so both `Challenge Quiz` and `Board Exam Mode` explain their setup before generation starts
  - `Challenge Quiz Setup` now shows timer, question-count, and attempt-usage summaries for all users; Premium users get live difficulty controls, while Free users see a recommended `Medium` difficulty plus subtle Premium upsell copy
  - Board Exam Mode is available on both Free and Premium plans and uses the same Challenge Quiz credit/quota rules during the current rollout stage
  - Board Exam Mode now has a formal `Board Exam Setup` prescreen with exam description, strict-timer summary, rule summary, `Cancel`, `Start Exam`, and best-effort fullscreen focus entry
  - Board Exam Mode now explains its distraction-free restrictions before the exam starts, confirms start explicitly, reinforces `Exam in progress` during the session, and uses more formal result framing so hidden navigation does not feel like a bug
  - Board Exam Mode no longer shows difficulty selection in the UI and now always uses mixed difficulty with the fixed exam question count
  - No correctness feedback during answering — answer first, see results later
  - Board Exam answering UI now uses a more neutral, formal presentation than the standard Challenge Quiz screen
  - Board Exam timer is now hardened around persisted session timing, low-time warning states, refresh-safe recovery, and one-shot timeout submission
  - Timer resumes from persisted session state after refresh and auto-submits when time runs out; manual submit remains available from the last question
  - Neutral question-number navigation lets users move through the exam without revealing correctness
  - Result screen keeps shared recovery actions and Review Answers, but now uses more formal Board Exam framing plus `Take Another Board Exam`
  - "Practice Weak Concepts" CTA (→ Adaptive Practice) shown only when weak concepts exist
  - All result statistics are derived from session data only — no LLM calls
- Public note cards in the Public Library, Public Profile, and public subject pages now show **quality indicator badges** (at most 2 per card) to help users quickly identify strong notes:
  - ⭐ **High Quality** — `copyCount >= 5 AND viewCount >= 10`
  - 🔥 **Popular** — `copyCount >= 10 OR viewCount >= 20` (shown only when High Quality is not already displayed)
  - Badges appear below the title; layout is mobile-safe and capped at 2 to avoid clutter. Private Library cards never show quality badges. The "New" badge has been removed.
- **Note card badge layout standardized** across all note-list surfaces (Library, Public Library, Public Profile, subject pages):
  - TOP ROW (above title): Subject badge (blue) + Course/Program badge (neutral/gray)
  - TITLE
  - BELOW TITLE: Study Pack Ready badge (green, only when applicable) + quality badges
  - `SharedNoteCard` props updated: `metaLine` removed, `courseProgram` (string) and `stateBadge` (ReactNode) added
- Public Library now has a **discovery mode** that replaces the flat note list with curated sections when no search or filters are active:
  - 🔥 **Featured Notes** — top 6 notes ranked by a weighted engagement score: `(views × 0.4) + (copies × 0.5) + (shares × 0.1)`. Tiebreak by newest first.
  - 📈 **Most Popular** — top 6 notes by copy count then view count, excluding notes already in Featured.
  - 🆕 **Recently Added** — top 6 newest notes, excluding notes already in Featured or Most Popular.
  - 📚 **Browse by Subject** — clickable chips of all unique subjects sorted by note count. Clicking a chip applies the subject filter and switches to filter mode.
- Sections are deduplication-safe: each note appears in at most one section per page load.
- Discovery mode is automatically hidden when the user types a search query, changes any filter, or selects a non-default sort option — switching seamlessly to the existing filter/sort list.

### Technical Changes

- Quick Review, Challenge Quiz, and Adaptive Practice test suites extended with post-quiz UX tests: no "Note" button on result screens, "← Back to Note" link present, confidence badge rendering (HIGH/MEDIUM/LOW), confidence option buttons hidden after selection, "Generate New Set" as primary on Adaptive Practice result, "Start Challenge Quiz" CTA on perfect Quick Review score, and Review Answers coverage across all quiz modes.
- Added `frontend/components/study-pack/quiz-answer-review.tsx` as the shared Review Answers surface for selected-vs-correct answer states, concept linking, visible explanations, and sequential review navigation. Covered by component tests plus Quick Review, Challenge Quiz, and Adaptive Practice page integration tests.
- Added a shared active quiz session guard for Quick Review, Challenge Quiz, and Adaptive Practice. Active sessions now block app route clicks, browser back navigation, and refresh/reload attempts with a shared `Leave quiz?` confirmation before users can forfeit and leave.
- Added explicit quiz session forfeits with `FORFEITED` status for Quick Review, Challenge Quiz, and Adaptive Practice. Challenge Quiz and Adaptive Practice forfeits do not refund consumed quiz credits and are not marked completed.
- Centralized quiz choice prefix cleanup so generated and legacy payloads strip hardcoded leading labels such as `A. ` and `B) ` before validation/storage; the frontend also strips legacy prefixes defensively before rendering dynamic choice letters.
- Study Pack generation from notes now runs asynchronously: Note Editor saves first and redirects immediately to Note Detail, which shows `GENERATING`, polls lightly until `STUDY_PACK_READY` or `FAILED`, and exposes `Retry Generate` without consuming quota on failed attempts.
- Challenge Quiz start now locks difficulty controls and the Start button immediately to prevent duplicate start requests or difficulty changes while initialization is in flight.
- Quick Review reload/start is now guarded against repeated fetch/redirect initialization loops without adding any LLM-specific lock behavior.
- Challenge Quiz and Adaptive Practice now reserve `GENERATING` sessions before LLM execution, return existing `GENERATING`/`IN_PROGRESS` sessions idempotently, and allow retry after `FAILED` without duplicate LLM calls from double-clicks, refreshes, or multiple tabs.
- Challenge Quiz and Adaptive Practice now show a full-screen `Generating your quiz...` lock during LLM generation, blocking app links, sidebar/header navigation, browser back, and refresh/reload until the session becomes `IN_PROGRESS` or `FAILED`.
- Enhanced quiz generation loading UX for Challenge Quiz and Adaptive Practice with pulsing AI-style dots, clearer personalized-question copy, and calm rotating progress messages while preserving the strict interaction lock.
- Adaptive Practice now checks existing session state on page load and starts new LLM generation only from the visible `Start Adaptive Practice` / `Generate New Set` actions.
- Added `frontend/lib/challenge-quiz-results.ts` with pure result computation utilities: `computeScore`, `mapPerformanceLevel`, `computeConceptBreakdown`, `computeWeakConcepts`, and exported `WEAK_CONCEPT_THRESHOLD = 60`. Challenge Quiz page now uses `computeScore` in `handleSubmit` instead of an inline reduce. Covered by 31 unit tests in `challenge-quiz-results.test.ts` (all-correct, all-wrong, mixed, unanswered, single-question, empty quiz, all 8 performance level boundary values, concept grouping, Unknown fallback, alphabetical sort, weak concept threshold edge cases, end-to-end integration scenarios).
- Added `frontend/lib/note-quality-badges.ts` with `computeQualityBadges` and exported `QUALITY_THRESHOLDS` constants. Added `frontend/components/notes/note-quality-badge.tsx` with the `NoteQualityBadges` component. Covered by 12 unit tests in `note-quality-badges.test.ts` (zero counts, null/undefined, threshold boundaries, High Quality suppresses Popular, label correctness). "New" badge removed — `createdAt` param and `NEW_WITHIN_DAYS` constant removed.
- Refactored `SharedNoteCard`: replaced `metaLine?: ReactNode` with `courseProgram?: string | null` (renders neutral gray badge above title) and added `stateBadge?: ReactNode` (renders Study Pack Ready badge below title). Updated all 4 callers: `public-library-page-client.tsx`, `app/library/page.tsx`, `public-profile-page-client.tsx`, `app/public/library/[subject]/page.tsx`.
- Added `frontend/lib/public-library-discovery.ts` with pure utility functions: `computeDiscoveryScore`, `getFeaturedNotes`, `getPopularNotes`, `getRecentNotes`, `getBrowseSubjects`, `excludeById`. All ranking is client-side using existing data from `listPublicNotes()` — no new backend endpoints required.
- Extracted `PublicNoteCard` sub-component inside `public-library-page-client.tsx` to share card rendering between discovery sections and the main filtered list.
- Added 28 unit tests for discovery utility functions and 5 integration tests for discovery UI behavior in `PublicLibraryPageClient`.
- Added backend scoring support via `GET /notes/public?sort=featured|popular|recent`. Sort is computed dynamically from existing engagement signals — no DB persistence. `featured` uses score `(copies × 0.6) + (views × 0.4)` with newest-first tiebreak; `popular` uses copy count → view count → newest-first; `recent` uses `createdAt` desc. Unknown or missing sort values fall through to the default DB order.
- Added `backend/util/PublicNotesScoringUtils.java` with `computeScore`, `sortByFeatured`, `sortByPopular`, and `sortByRecent`. Covered by 16 unit tests including scoring formula, sort ordering, null-count handling, tiebreaks, and empty dataset cases. Added 7 sort-specific tests to `NoteServiceTest`.
- Added backend subject filtering: `GET /notes/public?subject=<value>` returns only notes whose normalized subject matches (case-insensitive). Applied after fetch, before sort. Frontend `listPublicNotes()` accepts optional `{ subject }` param. Covered by `NoteServiceTest.listPublic_withSubjectFilter_returnsOnlyMatchingNotes`.
- Refactored LLM subject and key-concept sanitization into dedicated utility classes: `SubjectSanitizer` (max 6 words, overly-broad detection, course-program echo detection) and `KeyConceptSanitizer` (max 4 words per concept, filler-prefix stripping). Removed inline private methods from `OpenAiLlmStudyPackService`. Covered by new `SubjectSanitizerTest` and `KeyConceptSanitizerTest` unit test classes.
- Key-concept sanitization now applies to the `keyConcepts` list (not just quiz `concept` fields): overlong concepts are repaired in-place and hard-truncated as a last resort — study pack creation is never blocked by word-count alone.
- Updated LLM subject prompt to use the 3-tier subject strategy: Specific Subject (preferred) > Primary–Subtopic (when context needed) > General Subject (fallback).
- Browse by Subject section repositioned to appear ABOVE Featured/Popular/Recently Added sections in discovery mode. Limit reduced from 12 to 8 subjects.
- **Subject metadata cleanup**: subjects are now domain-only (e.g., `Biology`, `Physics`, `Engineering`). Any combined domain-topic value (`Biology – Cell Division`, `Physics: Ohm's Law`) is automatically stripped to the domain part before saving. Broad single-word domains (`Engineering`, `Medicine`, `Law`, `Business`, `Education`) are now valid and no longer trigger a retry. LLM prompt and subject guidance block updated to request domain-only subjects. Added `SubjectSanitizer.stripSubtopicSuffix` utility method. Covered by new `stripSubtopicSuffix` tests in `SubjectSanitizerTest` and updated `OpenAiLlmStudyPackServiceTest` subject edge cases.

## v0.7.0 - Learning & Metadata Foundation (In Progress)

### New Features

- User profiles now support `Learner Level` plus required `Course / Program` on Learning Profile saves as part of the learning-profile foundation.
- Onboarding now includes a dedicated `Learning Profile` step that collects required learner level, required course/program, and optional bio.
- Notes now support optional per-note `Course / Program`, defaulted from the user's profile and editable per note.
- Note metadata suggestions now use a shared field-level AI review modal for `title`, `subject`, and `tags`.

### Improvements

- Public Profile and Private Profile are now clearly separated in both navigation and purpose. Public Profile (`/public/profile/{userId}`) is the user's shareable learning-portfolio surface. Profile Settings (`/profile`) is the private account editing surface, accessed via `Edit Profile`.
- The avatar dropdown now uses clear, consistent labels: `My Profile` (→ public profile), `Settings` (→ `/settings`), and `Sign Out`. The sidebar Account section uses the same model: `Profile` (→ public profile) and `Settings` (→ `/settings`).
- Terminology is now consistent: **Profile = public identity page. Settings = account/app settings.**
- Share Profile now uses the same modal pattern as note sharing: a modal with title `Share this profile`, a labeled `Shareable URL` field, and `Copy Link` + `Close` buttons. The previous toast/inline-text-only behavior is replaced.
- If a profile is private, clicking Share Profile opens a confirm modal (`This profile is private`) that offers `Make Public & Share` — the same gate used for private note sharing.
- Share behavior is now consistent across notes and profiles: public content opens the share modal directly; private content requires owner confirmation before the share modal appears.
- Private profile confirm message updated to include `and notes` so users understand what becomes visible.
- Private Profile now separates `Identity`, `Learning Profile`, and `Profile Type` into distinct saveable cards.
- Public Profile can now show learner level and course/program when the owner chooses to provide them.
- Public Profile now feels more like a learning portfolio, with compact real metrics for public notes, copies, shares, and views when available.
- Public Profile now derives lightweight learning-focus text from real public-note metadata and can highlight a featured note when usage data exists.
- Learner-level and course/program inputs now reuse the same subject-style combobox UX as the Note Editor `Subject` field.
- Fixed-option learner-level comboboxes now snap back to the last valid saved value if a user types an unsupported option and closes the field.
- Note Editor now includes `Course / Program`, subject autocomplete, optional tags guidance, and the same metadata shape in both create and edit modes.
- Course / Program now behaves like a reusable top-level taxonomy shelf with stronger default suggestions, normalized saved-value reuse, and shared autocomplete across Note Editor, Note Detail metadata edit, Profile, and Onboarding.
- Course / Program autocomplete now filters in real time, ranks exact/prefix/contains matches more cleanly, keeps existing suggestions ahead of the custom action, and reuses existing display labels for exact case-insensitive matches.
- Course / Program helper text now adapts to `Learner Level`, and Profile learning-profile saves now show inline validation when either required field is missing.
- Saved custom subjects now feed future autocomplete suggestions through the existing distinct-subject backend source.
- Subject reuse now normalizes whitespace, dash formatting, and case-insensitive matches so equivalent custom subjects collapse into a cleaner autocomplete/filter catalog without adding a new subjects table.
- AI-generated subjects now use stronger library-specific guidance plus backend validation so overly broad labels like `Engineering` or `Business` are retried before being accepted.
- The AI Suggestions modal now uses a compact review layout with field-by-field comparisons, tag chips, a live preview, and a sticky mobile footer.
- Quiz generation now uses learner-level-aware prompt guidance across Quick Review, Challenge Quiz, and Adaptive Practice, defaulting to college-level when the user has no saved learner level.
- Quantitative notes can now produce computation and problem-solving questions with step-based explanations when the note context supports it.
- Challenge Quiz and Adaptive Practice now require richer explanations and concept labels in their generated quiz payloads.
- Library and Public Library now use richer metadata-driven filtering with course/program support, Public Library learner-level/source filters, and subtler note-card metadata hierarchy with visibility icons instead of extra badges.
- Public Library cards now emphasize the original note preview first and use subtle `views` / `copies` metrics plus `Most Viewed` sorting to help users spot strong notes faster.
- Dashboard `Continue Studying` now shows the actual note title plus subject/course metadata and uses the correct resume label for Quick Review, Challenge Quiz, or Adaptive Practice.
- Private and public note detail now include a `Full Notes` tab so users can inspect the complete original note alongside `Summary`, `Key Concepts`, and `Quiz`.
- The `Summary` view on private and public note detail now includes a subtle `View Full Notes →` CTA so users can jump from AI preview to the original note without losing context.
- Back navigation across all sub-pages now uses a shared `BackLink` component that renders `← {label}` with an arrow icon — small, muted, and link-styled rather than a button. Replaces all previous blue "Back to Library" / "Back to Note" link text and large Back buttons.
- My Profile (owner view) has no back link — it is a main navigation page reachable from the sidebar. Non-owners viewing another user's public profile see `← Public Library` linking explicitly to `/public/library`.
- Note Detail shows `← Library`, quiz pages show `← Note`, Create Note shows `← Library`, Edit Note shows `← Note`, Edit Profile shows `← Profile`, learn articles show `← Learn`. Inline card action buttons use short labels (`Note`, `Library`) without "Back to" prefix.

### Technical Changes

- Added `users.learner_level` and `users.course_program` with backward-compatible nullable storage for existing users.
- Backend Study Pack generation now prepares learner-level and course/program metadata in generation context for future prompt tuning, alongside note subject and tags.
- Refactored the OpenAI Study Pack service to share request/response/error handling across Study Pack, study-tip, and quiz generation flows, and added direct unit coverage for the refactored service.
- Added `notes.course_program` plus note-service create/update/copy handling so note metadata can diverge from the profile default when needed.
- Added normalized `GET /api/course-programs?scope=mine|public` suggestions backed by saved note/profile course-program values without adding a separate taxonomy table.
- Unified backend quiz-generation contracts onto strict JSON with required `answer`, `explanation`, and `concept` fields for more reliable parsing.

### Fixes

- Manual sign-out no longer reuses a stale protected-page `redirect` on the next login, so same-account and cross-account relogin now return to `Dashboard` instead of leaking back into the previous protected route.
- Restored distinct Note Editor create vs edit behavior so existing notes now render `Edit Note` copy, correct edit-mode actions, and the generated-note content lock without falling back to create-note messaging.
- Quiz validation now uses math-safe choice normalization, catches real blank/duplicate/invalid-choice payloads more accurately, and retries LLM invalid quiz output only once before failing.
- Quiz choice shuffling now preserves answer correctness by normalizing runtime data to canonical `choices + correctIndex`, keeping `A` / `B` / `C` / `D` as UI-only labels, and accepting legacy answer-text session payloads during load.
- Study Pack generation is now significantly more reliable for technical notes (Ohm's Law, electrical engineering, math formulas, science notes). The backend now safely logs failing field values for debugging (`requestId`, `field`, `value` truncated to 80 chars, `reason`) without logging full note content, prompts, or raw LLM output. Before failing on an invalid `quiz[].concept` or `subject`, the backend attempts a repair pass: concepts exceeding 4 words have leading filler phrases stripped (`Relationship between`, `Using the`, etc.) and are truncated to at most 4 words; subjects exceeding 6 words have their subtopic portion truncated to fit. The prompt rules for concept (now explicitly 1–3 words with counter-examples) and subject (now explicitly max 6 words with counter-examples) are tightened to reduce LLM drift. Covered by 10 new unit tests including an Ohm's Law regression scenario.

## v0.6.0 - Landing Revamp & Positioning (In Progress)

### New Features

- Landing page now frames NoteLib as a notes library and study workspace, not just a one-time quiz generator.
- Public marketing navigation now exposes `Home`, `Public Library`, `Learn`, `Pricing`, `Login`, and `Get Started`.
- NoteLib now has a standardized favicon and app-icon set based on the NL monogram for desktop, mobile, and home-screen usage.
- Demo page rewritten as a 5-step interactive flow (choose start → topic/paste input → generated note → Study Pack CTA → Study Pack results) using static Photosynthesis content only — no backend or LLM calls.

### Improvements

- Landing hero repositioned around exam-readiness: headline changed to `Turn your notes into exam-ready study materials in seconds`; `Try Demo` promoted to primary CTA with `Start for Free` as secondary.
- `Why NoteLib` feature section updated with three benefit cards framed as learning outcomes: Built for studying, Learn from your weak points, From notes to mastery.
- Demo quiz made interactive: users select an answer before seeing correct/incorrect feedback, simulating real exam conditions; post-quiz CTA (`Ready to create your own Study Pack?`) drives conversion after the demo experience.
- Landing pricing section updated to Free / Plus / Pro cards with plan descriptions tied to learner stage, intro pricing display, export tooltip (`PDF/DOCX for offline or classroom use`), and Plus Adaptive Practice (10 sessions/month).
- `Plus` plan pricing config gains `adaptivePracticePerMonth: 10` so Adaptive Practice is properly reflected in plan comparison surfaces.
- Product positioning principles added to AGENTS.md: learning-outcome framing, demo as conversion driver, clear plan progression rules.
- Landing page now uses a tighter high-conversion structure built around:
  - a faster product headline focused on summaries, quizzes, and exam simulations
  - a 3-step `Add notes -> Generate study pack -> Test yourself` explanation
  - dedicated feature coverage for Study Packs, Challenge Quiz, Adaptive Practice, and Board Exam Mode
  - clearer comparison against generic AI tools plus target-user guidance for students, board exam reviewees, and teachers
  - stronger CTA flow with `Start for Free`, `See how it works`, pricing preview, and a clearer closing section
- Challenge Quiz and Board Exam Mode now use a collapsible Question Navigator so mobile quiz screens stay less cluttered:
  - Challenge Quiz defaults to an expanded navigator on desktop and a collapsed summary on mobile
  - Board Exam Mode defaults to a collapsed navigator on both desktop and mobile to keep the exam view more focused
  - the collapsed summary still shows current question position and answered count, and expanding it keeps direct jump navigation intact
- Major action buttons now keep icon + text labels on mobile across the app’s shared action surfaces instead of collapsing to icon-only.
- Profile now supports a short bio on the private identity page, and Public Profile now renders that bio with avatar/initial styling and derived subject chips.
- Public Profile now uses a page-level `Back` action above the header card, based on navigation history instead of a hardcoded return link to Public Library.
- Private Library and Public Library now share the same `Search`, `Filter`, `Sort`, notes-list structure, with mobile-friendly filter/sort sheets instead of always-visible controls.
- Library, Public Library, and Public Profile note cards now stay action-free preview surfaces so note management happens consistently in Note Detail.
- Private Note Detail `Summary` and `Quiz` tabs now keep text labels on mobile for clearer view switching.
- Landing page now positions NoteLib as a notes library and study workspace first, with stronger Public Library and active-recall messaging.
- Public Library is now promoted directly from the landing page as a discovery surface that stays accessible without login.
- The landing page now integrates the Learn / active-recall message so new users understand the study method, not only the generation workflow.
- Learn article pages now use a consistent content-marketing structure with introduction, summary, key concepts, sample practice questions, and a bottom account-creation CTA.
- Landing page SEO title, meta description, and Open Graph metadata now align with the notes-library positioning update.
- Pricing page messaging now frames NoteLib as a notes library plus review workflow, with Free/Premium copy aligned around core note creation and heavier exam review periods.
- Pricing page now includes a `Why Go Premium` section that explains Premium in terms of serious review, practice, and exam preparation rather than only limits.
- Pricing no longer treats Public Library as a paid-plan feature.
- Theme toggle is now available on the shared public navbar and syncs with a persisted user theme preference for authenticated users.
- Navbar and app-shell logos now use the NL monogram, while marketing headers and the public footer use the full NoteLib wordmark.
- The Open Graph image now uses the standardized NoteLib branding, notes-and-lightning illustration, and notes-library messaging.
- Study Pack generation surfaces now use student-friendly monthly-limit banners and plan-specific limit modals for both Free and Premium instead of relying on disabled generate actions.
- Public Library now supports discovery sorting by newest, most copied, most shared, and most viewed.
- Public note detail now uses a stronger copy-first growth CTA for non-owners, including a handoff into their own Library note for generation.

### Fixes

- Auth redirect logic now returns users to interrupted protected pages through explicit redirect intent while sending manual public-page logins to `Dashboard`.
- Login-page auth messaging now distinguishes `session_expired`, `logged_out`, and `auth_required` so manual logout no longer shows the expired-session warning.
- Manual logout now suppresses late expired-session redirects from in-flight protected requests so logout messaging stays neutral.
- Shared public navbar no longer duplicates the theme toggle inside the mobile menu, and public CTA hierarchy now keeps `Get Started` primary, `Login` secondary, and theme as a utility control.
- Study Pack limit enforcement and usage warnings now use the same effective usage calculation so users are no longer told they have credit left while generation is already blocked.
- Free-plan near-limit messaging now shows the actual remaining Study Pack count instead of a generic warning.
- Note Detail generation now applies the same title/subject/tag suggestion flow as Create Note.
- Note Detail tab switching no longer refetches the note or snaps long pages back to the top when `?tab=` changes.
- Mobile Note Editor no longer lets the global `Send Feedback` launcher overlap the primary Generate CTA.
- Library-style note cards no longer mix management menus into preview surfaces, avoiding conflicting card-navigation behavior.

### Technical Changes

- Shared responsive action components now default to mobile icon + text labels, with explicit opt-out reserved for true icon-only utility controls.
- Added shared library toolbar and sheet components so private/public library controls stay consistent across desktop and mobile.
- Added shared brand-asset components for the monogram, full logo, and product icon, plus a local OG-image render pipeline and web manifest for the public icon set.
- Added a shared backend Study Pack usage resolver so plan summary and generation-limit enforcement stay synchronized across services.

## v0.5.0 - Public Profiles & Public Notes

Public Profile:

- Public profile page at `/public/profile/{userId}`
- Public identity uses `displayName`; public pages never show email
- Public profile shows `Profile Type`, public-note stats, and total copies
- Public profile visibility can be turned `On` or `Off`
- Owner-only public-page controls live on Public Profile:
  - `Edit Profile`
  - `Share Profile`
  - Public visibility badge/dropdown
- Non-owners can view/share public profiles only when the profile is public

Public Notes:

- Public notes appear in Public Library and Public Profile
- Public author labels are viewer-relative:
  - `By You`
  - `By NoteLib` with `Official`
  - `By {Display Name}`
- Public author labels link to Public Profile
- Public note detail remains read/copy/share only
- Public note copying preserves attribution to the source note and creator

UI and UX:

- Shared note-card layout across Library, Public Library, Public Profile, and public subject pages
- Whole-card click behavior across library-style note cards
- Removed redundant `Open Note` buttons from public showcase/discovery cards
- Shared cards now show clamped `Note Preview` plus `Summary Preview`
- Private Note Detail now uses underline tabs for `Summary` and `Quiz`
- Icon usage is standardized across navigation and common actions
- Quick Review, Challenge Quiz, and Adaptive Practice use distinct icons
- Action buttons now follow a shared responsive desktop/mobile pattern
- Dark-mode outline buttons use higher-contrast borders, lighter text, and clearer hover states
- Profile page is split into Display Name, Identity, and Profile Type cards with per-section save actions
- Public profile controls were moved off `/profile` and onto the Public Profile page
- Auth recovery now returns users to their interrupted or last visited page after login instead of always forcing `Dashboard`

Documentation baseline:

- `v0.5.0` is the documentation lock point for Public Profiles and Public Notes
- next planned milestone is `v0.6.0 - Landing Revamp & Positioning`

## v0.4.0 - Profile-Based Experience & UX

- Profile identity management
- Email change verification
- Onboarding per profile type
- Personalized dashboards
- Teacher workflow and quiz-first note creation
- Note editor UX improvements across desktop and mobile
- First-time activation flow from verification through first Study Pack and first quiz guidance
