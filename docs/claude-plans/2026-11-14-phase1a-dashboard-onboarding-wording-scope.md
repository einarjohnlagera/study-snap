# Phase 1a addendum: dashboard and onboarding recommendation card (FINAL copy and scope)

**Status: PROPOSAL, planning only, 2026-11-14. Product UX decisions FINAL (two replies, 2026-11-14). No implementation, commit, push, merge, deploy, data change or version number is authorized.** This addendum **replaces the dashboard bullet marked "To verify" in Phase 1a** of `docs/claude-plans/2026-11-10-study-plan-discovery-and-community-collections-plan.md`; everything else in that Phase 1a stands. Code read at `origin/main` `a928029b`; line numbers are for `frontend/app/dashboard/dashboard-study-plan-section.tsx` ("Section") unless stated. A cold Opus review (2026-11-14) corrected several statements below, marked "(cold review)".

**Principle:** public recommendations use the collection's purpose; personal copies use the learner's vocabulary; a button's label must describe what clicking it does.

## 1. Verified facts

- **One shared card** serves the dashboard (`dashboard/page.tsx:932`, `:1016`, `discoveryPresentation="recommendation"`) and onboarding twice: the **practice-first** screen (`onboarding/page.tsx:1850`, `context="practice-first"`, `resolvedPlan`, `onPlanStarted`) and the **completion step** (`:2197`, `context="onboarding"`, no `resolvedPlan`). Label copy is derived from state inside the component; **no new prop is needed for the wording**.
- **Every button ends on the learner's own collection overview** (`router.push('/collections/{id}')`: `finishStart` Section:374-380; onboarding `handlePracticeFirstPlanStarted`, `onboarding/page.tsx:1251-1272`, which also completes onboarding first). Hence "Open my …", not "Continue studying".
- **States that reach the button:** unadopted public plan; the learner's primary copy (always "owns source"); an adopted copy of the shown plan (only in onboarding's non-recommendation presentation, because on the dashboard an adopted non-primary plan returns the Explore pointer first, Section:350-355). "Open this plan" also shows when the viewer owns the **source** itself, for example a curator (cold review).
- **Purpose data needs no new request:** the recommended plan is a `NoteCollectionSummary` carrying `learnerLevel` and `resolvedExamGoalSlug` (`api.ts:2172-2175`; `toSummaryResponse` Svc:3557-3568).
- **Ownership availability (Product UX check 3).** *Completion-step card:* ownership **is already available**: with no `resolvedPlan` the Section's effect fetches the public plans and `listCollections()` and sets `adoptedPlan` (its own comment counts those two calls), so the adopted state already resolves there. *Practice-first card:* **not available**: `resolvedPlan` is supplied, the effect forces `setAdoptedPlan(null)` (Section:231), and the onboarding page holds no ownership data (no `listCollections` or `sourcePlanId` in `onboarding/page.tsx`). Getting it would need a new `listCollections()` request on a brand-new learner's first screen.
- **Post-adoption feedback today:** `setJustAdoptedNotice` is set after Goal adoption only, by three callers (the Section :419, `discovery-intent-consumer.tsx:59`, `public-study-plan-card.tsx:174`); on the destination page it drives the target-date tip (`isGoalView` only) and `justAdopted` also **suppresses** the "assessment covers the whole plan" tip (`collection-detail-page-client.tsx:3772`). No text says "added to your library" after the click (Explore cards have a pre-click caption, `public-study-plan-card.tsx:269`).
- **App shell redirect (Product UX check 4).** `/explore` is a protected app route: a user with `needsOnboarding && !hasPendingLightweightProfileCompletion` who opens it is redirected to `/onboarding` (`app-shell.tsx:400-406`). **On the completion step the onboarding completion call fires automatically on entry** (`onboarding/page.tsx:692-716`; on failure a deferred-completion marker lets protected routes through), so a link there behaves like the existing "Go to Dashboard" (`handleGoToDashboard`, :1478-1485: clear draft, stop abandonment tracking, push). **On the practice-first screen onboarding is NOT complete until a plan is adopted**, so a link there would bounce straight back to `/onboarding` unless the click first completed onboarding.
- **Explore needs no new routing contract:** the Official tab is the default (`resolveExploreTab`, `explore-url.ts:13-14`; `buildExploreUrl()` omits `tab` for it), and `source` is a free-form analytics string with no allowlist (`explore-page-client.tsx:31-39`, recorded as `source` / `pointerSource`).
- **No backend, schema or API change is required by anything below.** `DashboardPrimaryCollectionHero` ("Open this plan to continue your review journey.", `dashboard-primary-collection-hero.tsx:50`) is the learner's own hero and is untouched.

## 2. Final copy

| State / element | Today | Final |
|---|---|---|
| Heading, public plan not adopted | "Recommended {Study Plan / Review Set / Lesson Plan}" by viewer (:460) | "Recommended study plan" / "Recommended review set" by **purpose** |
| Button, not adopted | "Start this Goal / Course / Collection" or "Start this {noun}" (:360-362) | "Add study plan" / "Add review set" by purpose |
| Button, primary copy (dashboard) and adopted copy (onboarding completion step) | "Open this plan" / "Continue this …" | "Open my study plan" / "Open my review set" / "Open my lesson plan" / "Open my collection" by the **learner's profile** |
| Heading, learner's primary copy | "Primary Study Plan" etc. (`labels.primarySingular`, :460) | "Primary study plan" / "Primary review set" / "Primary lesson plan" / "Primary collection" by profile, **derived locally** from `labels.singular` (do not change `getCollectionLabels.primarySingular`; other surfaces use it) |
| Failed adoption fallback (:450; a server message still passes through) | "Could not start this …" | "Couldn't add this study plan. Please try again." / "Couldn't add this review set. Please try again." |
| No notes ready (:434-437) | "This … has no notes ready to study yet. Try another one, or start from your own notes." | "This study plan has no notes ready to review yet. Try another one, or start from your own notes." (and the review-set version; second sentence **kept**) |
| Button loading text (:483) | "Starting..." | "Adding..." while creating a private copy; "Opening..." when only navigating to an existing copy; the Subject Plan progress text (:415) unchanged |
| Onboarding intro (:467) | "Optional: explore an official {viewer noun} alongside the Study Pack you just created." | The same sentence with the **purpose** noun: "...official study plan..." or "...official review set..." |
| After a successful add, on the destination page | nothing (Goal: a target-date tip) | "Added to your library." (section 3) |
| Secondary link, onboarding completion step only | none | "Browse study plans" / "Browse review sets" (section 4) |

Unchanged on purpose: the "no program set" card and `ExplorePointerCard` (viewer vocabulary; apply the sentence-case tail), the "Adopted" badge (:475), the learner's own library, and any exam prefix in verbs (no "Add CELE review set").

## 3. Confirmation after adding (Product UX 1: final copy "Added to your library.")

Requirements: one-time; single plans **and** multi-section sets; only after a **successful adoption**, never when opening an existing copy; shown **separately** from the target-date tip; no modal; keep practice and exam guidance.
- **Design (PROPOSAL, frontend only):** a **new, separate** one-time flag with its own `sessionStorage` key (helpers beside `just-adopted-notice.ts`), set on adoption success and consumed once on the destination page, rendered as a small inline status line (`role="status"`) **outside** `pickActiveGuidance`. This keeps `justAdopted` untouched, so the target-date tip still shows for Goals and the "assessment covers the whole plan" tip is **not** newly suppressed for single plans (cold review: reusing `justAdopted` would have displaced it).
- **Set it at all three adoption success sites** so the confirmation does not depend on the entry point: the Section (both the Goal and leaf branches), `public-study-plan-card.tsx` (after its adopt) and `discovery-intent-consumer.tsx` (after its adopt).
- **Do not set it** when the response is `alreadyAdopted` (nothing was added), and never on the open-my-copy path (`finishStart` with a `continuePlan`).
- Files: the three callers above, `collection-detail-page-client.tsx` (one inline line), `just-adopted-notice.ts` (helpers) and their tests. No new request.

## 4. Already-owned plan during onboarding (Product UX 4) and the secondary link (Product UX 5)

**Already-owned, practice-first: DEFERRED as a documented known limitation.** Ownership exists only on the completion-step card (already shows "Open my …"). The practice-first card has no ownership data, and supplying it needs a new `listCollections()` request, which Product UX said not to add for this rare edge. Behavior stays as today: an already-owned plan shows "Add study plan" and the click takes the idempotent `alreadyAdopted` path to the existing copy (nothing is duplicated, no "Added to your library." line because `alreadyAdopted`). Record it in `docs/features/onboarding.md`.

**Secondary "Browse study plans / Browse review sets" link: FITS Phase 1a, on the completion step only (size S).**
- **Where:** below the recommendation card in `onboarding/page.tsx` around :2197 (`context="onboarding"`). It is visually subordinate (text link, like `ExplorePointerCard`'s "Browse in Explore"), optional, not a new step.
- **Behavior:** the same bookkeeping as "Go to Dashboard" (clear the draft, stop abandonment tracking), then `router.push(buildExploreUrl({ source: "onboarding" }))`. The Official tab is the default, so no tab parameter and no new routing contract. Onboarding is already complete (or deferred-complete) at that step, so Explore is not redirected back and returning goes to the dashboard; no completed step repeats.
- **Label:** the recommended plan's purpose noun when a plan is shown ("Browse study plans" / "Browse review sets"); the viewer's vocabulary when the card shows no recommendation (`labels.plural`: "Browse study plans" / "Browse review sets" / "Browse lesson plans" / "Browse collections"). Render nothing while the card is still resolving (no flash). Because only the Section knows the resolved plan, add **one optional prop** (for example `onBrowseOfficial`) that the onboarding page passes to supply the bookkeeping; the dashboard passes nothing and is unchanged.
- **Analytics:** reuse `STUDY_PLAN_RECOMMENDATION_CLICKED` with a distinct metadata value (as `ExplorePointerCard` does); **no new event type** (a new type would need the backend enum).
- **Not in Phase 1a, follow-up:** the same link on the practice-first screen. Onboarding is incomplete there, `/explore` would redirect back to `/onboarding` (`app-shell.tsx:400-406`), and making it work means completing onboarding on click (`completeOnboardingAndLeave`) with new completion and analytics semantics: real state complexity, so it stays a follow-up per Product UX.

## 5. Tests to update or add (re-verified at `a928029b`; names only)

- `dashboard-study-plan-section.test.tsx`: headings :78, :112, :188, :338, :580 (sentence case, purpose-based; give Review Set fixtures `learnerLevel` or `resolvedExamGoalSlug`); "Start this Study Plan" :79, :115, :137, :141, :168, :170, :197, :631 → "Add study plan"; "Start this Review Set" :189 → "Add review set"; "Start this Goal" :242, :263, :267 → "Add study plan" or "Add review set" by fixture; "Continue this Study Plan" :212 and "Continue this Goal" :289 → "Open my study plan"; "Open this plan" :304, :491 → "Open my {noun}" by fixture profile; "Primary Review Set" headings :426, :444, :471, :487, :565 → "Primary review set"; :81 → `/no notes ready to review yet/i` and the new second sentence; intro text :196 (and the negative at :114); loading text "Adding..." / "Opening...".
- `onboarding/page.test.tsx`: :558 → "Add review set"; :614 → "Add study plan" or "Add review set" by fixture; :867 intro text; :869 → "Add study plan"; **new:** the Browse link renders on the completion step with the purpose or viewer noun, not on the practice-first screen, clears the draft and pushes `/explore?source=onboarding`; not rendered while the card resolves.
- New: purpose resolver rows for this card; a teacher shown a board-exam set sees "Recommended review set" and "Add review set", and a teacher's primary copy reads "Primary lesson plan" / "Open my lesson plan"; the confirmation flag is set on Goal and leaf adoption at all three sites and **not** on `alreadyAdopted` or the open path; `collection-detail` shows "Added to your library." once and the target-date and assessment tips behave exactly as before (`app/collections/[id]/page.test.tsx`); helper tests in `just-adopted-notice.test.ts`; `public-study-plan-card.test.tsx` and the `discovery-intent-consumer` test, if present, for the new flag.
- No test may assert a new network call: none is added.

## 6. Docs to amend in the same change

`docs/features/collections.md` :164, :1157, :1158, :1165; `docs/features/onboarding.md` :343 (wording, the Browse link, the practice-first limitation); `docs/features/explore.md` if it lists entry points. **Contradiction found:** `collections.md:1165` says the section shows an "In your library" badge, but the code renders "Adopted" (Section:475); correct the doc.

## 7. Acceptance criteria

- No state of this card says "Start this …" for adoption, "Goal", "Course" or "Collection" in an adoption action, or "Continue" for a navigate-only action; "Adding..." and "Opening..." match behavior.
- Public recommendations name the collection's purpose for every viewer profile, including the onboarding intro; the learner's own copy uses profile vocabulary in sentence case; both surfaces use identical copy per state.
- Adoption success shows "Added to your library." once for single plans and sets, never for `alreadyAdopted` or opening a copy, and the target-date and assessment tips are unchanged.
- The completion step shows a subordinate Browse link; it does not appear on the practice-first screen; Explore opens on the Official tab; no onboarding step repeats.
- Navigation, adoption, onboarding completion, analytics event types and permissions are unchanged; no new request, DTO field, schema or backend file.

## 8. Readiness and constraints

**Phase 1a (card copy, confirmation, Browse link, Explore card changes already in its scope) is ready for kickoff once the owner approves it and a release slot exists.** Remaining constraints, none open: frontend only; independent of Phase 0 and the public page (no link to `/explore/collections/{id}`); cannot land in `v0.181.0` or `v0.182.0` (anti-drift and backend LLM release); its Vercel deploy must not be combined with a price change; verification tier `advisor()` plus the updated tests; the practice-first ownership edge and Browse link are recorded follow-ups, not blockers.

## 9. Edits to apply to the main plan at the next docs commit (the plan is committed on `main`; not edited here)

Phase 1a: replace the "To verify" dashboard bullet with a pointer to this addendum; add the confirmation, the completion-step Browse link and the practice-first deferral to Scope and Acceptance; add the new test and doc lines; and update the final "Settled / To verify / Smallest next action" section (remove the dashboard and "Open my copy" "To verify" items; add this addendum and the Phase 0 verification addendum as artifacts).
