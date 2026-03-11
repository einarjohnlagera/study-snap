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
* open a Study Pack
* read summaries and key concepts
* start Quick Review sessions
* delete Study Packs

The Study Library acts as the central location for accessing learning material.

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

# Activity Tracking

Study Snap records key learning actions as activity events.

These events help support:

* future analytics
* learning insights
* recommendation improvements

Examples of tracked events include:

* STARTED_QUICK_REVIEW
* COMPLETED_QUICK_REVIEW

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
