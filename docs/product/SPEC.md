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
* sort Study Packs (recently created, recently reviewed, title)
* open a Study Pack by clicking the card or title
* start Quick Review from a Study Pack card
* read summaries and key concepts
* view tag chips for quick scanning
* load Study Packs in paginated batches (default 20) with a `Load More` control
* start Quick Review sessions
* delete Study Packs

The Study Library acts as the central location for accessing learning material.

Dashboard behavior note:

* Dashboard is guidance-first and non-destructive
* Study Pack deletion is handled in the Library page, not the Dashboard
* Dashboard Study Pack previews open by clicking the card/title (no explicit `Open` button)
* Study Pack deletion from the Library must require an explicit confirmation step

---

## Quick Review

Quick Review allows users to actively practice a Study Pack through an interactive quiz.

Users answer questions one at a time and receive immediate feedback. After the first pass, incorrectly answered questions may appear again in a retry round to reinforce learning.

Quick Review sessions track:

* correct answers
* total questions
* score percentage
* session history

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

* up to 3 Study Packs generated per day
* unlimited review of generated Study Packs
* Quick Review available for generated Study Packs

Future plans may include premium tiers with expanded limits.

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
