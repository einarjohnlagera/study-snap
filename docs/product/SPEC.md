# Study Snap Product Specification

## Product Overview

Study Snap is an AI-powered study assistant that transforms notes into structured Study Packs containing summaries, key concepts, and quiz questions.

The goal of Study Snap is to help students quickly convert raw learning material into a format that supports active recall and repeated practice.

Study Snap focuses on simplicity and fast study loops rather than complex study planning tools.

---

# Core Learning Loop

Study Snap is designed around a lightweight learning cycle:

Notes → Study Pack → Quick Review → Retry mistakes → Dashboard recommendation → Repeat

Users upload or capture notes which are converted into Study Packs.
They review the material, test themselves through Quick Review, and the dashboard recommends what to study next.

This loop encourages consistent practice and reinforcement.

---

# Key Features

## Feature Documentation

Detailed feature behavior is documented in:

- docs/features/*

## Public Landing Page

Study Snap includes a public landing page at `/` for unauthenticated users.

Purpose:

* communicate core product value quickly
* explain how Study Snap works
* drive signup and demo exploration

Landing page sections:

* hero (headline, supporting subheadline, primary + secondary CTAs)
* Study Pack output preview (summary, key concepts, quick review)
* how-it-works (3 steps)
* feature highlights
* pricing teaser (Free vs Premium overview)
* final CTA section
* FAQ section before the footer, using a lightweight accordion layout

Demo mode:

* public route `/demo` provides a prebuilt Study Pack walkthrough
* demo does not create a real user session
* demo does not trigger new LLM generation cost

## Study Pack Generation

Study Packs are generated from user-provided notes using AI.

Each Study Pack contains:

* Title
* Summary
* Key Concepts
* Quiz Questions

Study Packs act as the main unit of learning inside the system.

---

## Study Library

The Study Library allows users to access and manage all generated Study Packs.

Users can:

* view saved Study Packs
* search Study Packs by title or tags
* filter by one subject at a time (`All subjects` default)
* filter by multiple tags using a scalable multi-select dropdown with OR matching (a pack matches if any selected tag is present)
* combine search, subject filter, and tag filters together
* sort Study Packs (recently created, recently reviewed, title)
* open a Study Pack by clicking the card or title
* start Quick Review from a Study Pack card
* read summaries and key concepts
* view tag chips for quick scanning
* load Study Packs in paginated batches (default 20) with a `Load More` control
* start Quick Review sessions
* delete Study Packs

The Study Library acts as the central location for accessing learning material.

Library filter implementation note:

* subject and tag filters are frontend-only on currently loaded Study Packs
* selected tags are shown as removable active chips
* filtering safely handles missing/empty `subject` and `tags` values on older Study Packs
* sorting is applied after search/filtering on the visible set

Dashboard behavior note:

* Dashboard is guidance-first and non-destructive
* Study Pack deletion is handled in the Library page, not the Dashboard
* Dashboard Study Pack previews open by clicking the card/title (no explicit `Open` button)
* Study Pack deletion from the Library must require an explicit confirmation step

## Authentication Session Handling

Protected app routes require authentication.

Behavior:

* when protected API requests return `401 Unauthorized`, Study Snap clears local auth state and redirects to `/login`
* login redirect preserves destination using `redirect` query parameter (example: `/login?redirect=/study-packs/{id}`)
* session-expired redirects include a user-friendly login hint (`Your session has expired. Please log in again.`)
* after successful login, verified/onboarded users are returned to the preserved destination when available
* unauthenticated access to protected routes is redirected to login with destination preservation

---

## Profile And Settings Responsibilities

Profile page scope:

* focuses on user identity information and profile type
* includes editable identity fields (`Name`, `Email`)
* includes editable `Profile Type`
* includes read-only `Account Information`:
  * `Member since` (readable month/year format)
  * `Plan`
  * `Study Packs created`
* does not include a separate `Actions` section

Settings page scope:

* owns account configuration and behavior controls
* owns plan management under `Plan & Billing`
* upgrade and billing-related actions should stay in Settings, not Profile

---

## Shareable Study Packs

Study Packs can be shared publicly using token links.

Share behavior:

* owners can generate or reuse a tokenized share link for a Study Pack
* public share route is `/p/{token}`
* shared page layout is auth-aware:
  * unauthenticated viewers use the public minimal navbar
  * authenticated viewers use the app shell/sidebar layout
* shared Study Pack pages are read-only (no direct editing)
* shared pages show title, summary, key concepts, and quiz preview
* Study Pack detail includes an in-product share action (`Copy Link`)
* copying a share link confirms with `Share link copied`

Remix behavior:

* shared pages support `Copy to my Study Library` for authenticated users
* remix duplicates the Study Pack under the current user
* copied Study Packs auto-resolve duplicate titles per user:
  * first duplicate: `{Title} (Copy)`
  * next duplicates: `{Title} (Copy 2)`, `{Title} (Copy 3)`, ...
* remix does not trigger a new LLM generation request
* successful remix shows `Study Pack copied to your library.` after redirect
* unauthenticated users see a signup/login CTA to copy
* original shared Study Pack remains immutable

---

## Quick Review

Quick Review allows users to actively practice a Study Pack through an interactive quiz.

Users answer questions one at a time and receive immediate feedback. After the first pass, incorrectly answered questions may appear again in a retry round to reinforce learning.

Answer feedback semantics (Quick Review and Adaptive Practice):

* correct answers are highlighted in green with `✓ Correct`
* selected incorrect answers are highlighted in red with `✗ Incorrect`
* when a user answers incorrectly, both the wrong selected option (red) and the correct option (green) are shown
* non-selected, non-correct options remain neutral
* blue is not used for correct/incorrect answer states

Quick Review sessions track:

* correct answers
* total questions
* score percentage
* session history
* optional post-review confidence feedback (`HIGH`, `MEDIUM`, `LOW`)

Confidence feedback behavior:

* results screen includes `How confident did you feel about this topic?`
* options: `Very confident`, `Somewhat confident`, `Not confident`
* confidence is optional and does not block review completion
* saved confidence supports future learning analytics and adaptive recommendations

Users can leave a review session and resume it later if it remains unfinished.

For detailed behavior including retry logic, scoring rules, and session states, see:

docs/features/quick-review.md

---

## Study Pack AI Study Coach

The Study Pack detail page includes a compact `AI Study Coach` panel.

The panel uses existing Quick Review context (latest completed session and weak concepts) to show:

* focus areas (when weak concepts exist)
* a suggested next step

If no completed Quick Review exists yet, the panel shows a supportive prompt to start the first review.

This guidance layer is data-driven and does not require an additional LLM call.

---

## Smart Continue Studying

The dashboard includes a recommendation card that suggests the most useful next study action.

The system analyzes recent activity and may recommend:

* resuming an unfinished Quick Review
* revisiting a Study Pack with a low recent score
* continuing a recently opened Study Pack
* starting review of a newly created Study Pack

The messaging adapts to the user’s learning progress to encourage continued study.

For detailed recommendation logic and messaging rules, see:

docs/features/dashboard-recommendation.md

---

## Study Engagement Modes

Study Snap uses a user-controlled engagement model so motivation stays supportive and flexible.

Available engagement modes:

* FOCUSED (default)
* CONSISTENCY
* STREAK

Behavior:

* FOCUSED: no streak/consistency card, keep a calm guidance-first dashboard
* CONSISTENCY: show a lightweight weekly consistency summary
* STREAK: show consecutive-day streak progress

Today’s Focus remains the primary dashboard guidance card in all modes.

---

## Mastery Snapshot (Dashboard)

The dashboard includes a compact `Mastery Snapshot` card that summarizes recent learning performance using existing completed Quick Review session data.

Displayed metrics:

* average recent score
* best recent score
* Study Packs reviewed (distinct packs in the recent session window)

Behavior:

* metrics use lightweight existing session data only (no heavy analytics pipeline)
* if no completed Quick Review sessions exist, show a supportive empty-state prompt to complete the first review
* placement is below `Today's Focus` and below Study Consistency/Streak (when shown), and above `Continue studying`

---

# Activity Tracking

Study Snap records key learning actions as activity events.

These events help support:

* future analytics
* learning insights
* recommendation improvements

Examples of tracked events include:

* CREATED_STUDY_PACK
* STARTED_QUICK_REVIEW
* COMPLETED_QUICK_REVIEW
* COMPLETED_ADAPTIVE_QUIZ

Activity events store the related user, Study Pack, and timestamp.

---

# Pricing Model (Initial)

Study Snap will launch with a simple usage-based model.

Free Plan:

* up to 5 Study Packs generated per month
* includes Study Pack generation, summaries, key concepts, Quick Review, retry, Library, Today's Focus, and AI Study Coach
* does not include Weak Concept Detection
* does not include Adaptive Quiz Generation

Premium Plan:

* up to 100 Study Packs generated per month
* includes everything in Free
* includes Weak Concept Detection
* includes Adaptive Quiz Generation
* includes advanced review tools as Premium capabilities expand

Feature-gating behavior:

* premium-only features should show a clear upgrade path when accessed on Free
* gating should not break core review or study flows
* upgrade prompts for Premium-only flows should direct users to Settings `Plan & Billing` (`/settings#plan-billing`)

---

## Plan & Billing (Settings)

Settings includes a dedicated `Plan & Billing` section.

Users can:

* view current plan (`FREE` or `PREMIUM`)
* view monthly Study Pack usage (`used / limit`) with a simple progress indicator
* review Premium feature highlights (100 Study Packs/month, Weak Concept Detection, Adaptive Quiz Generation)
* upgrade with Stripe Checkout from `Upgrade to Premium`

Stripe billing behavior:

* upgrading creates/uses a Stripe customer linked to the user
* checkout completion and recurring payment events are processed via Stripe webhooks
* confirmed active subscription sets plan to `PREMIUM`
* canceled/ended/failed subscription reverts plan to `FREE`

Dashboard usage indicator:

* Free users see a monthly plan usage card (`Free Plan`, `used / 5`)
* card includes a subtle upgrade action linking to Settings `Plan & Billing`
* when limit is reached, show a supportive quota-reached message and upgrade CTA

---

# Non-Goals (Initial Version)

The first version of Study Snap intentionally avoids complex learning management features.

Not included in the initial release:

* spaced repetition scheduling
* full exam simulation modes
* advanced learning analytics dashboards
* classroom or teacher management tools
* collaborative study features

These may be explored in future versions.
