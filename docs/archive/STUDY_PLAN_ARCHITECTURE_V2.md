# STUDY_PLAN_ARCHITECTURE_V2.md

# Study Plan Architecture v2

## Background

While planning the Guided Learning feature, we realized that we were attempting to build the "journey" before fully defining what users would actually be progressing through.

After discussion, we decided to **curate the Study Plans first**, then build the Guided Journey on top of them.

The Study Plan becomes the curriculum.

The Journey becomes how users move through that curriculum.

---

# Core Philosophy

Study Plans should not simply be collections of notes.

Study Plans should represent meaningful learning goals.

Instead of saying:

> Here are 35 notes.

We want users to feel:

> I'm preparing for the Professional Education part of the LET.

This subtle shift changes Study Plans from folders into structured learning experiences.

---

# Hierarchical Study Plans

Instead of having only one Study Plan per audience, Study Plans should be hierarchical.

Example:

LET Mastery

├── Professional Education Mastery

├── General Education Mastery

└── Major Specialization Mastery

The same pattern applies across all supported audiences.

---

# Level 1 — Goal

Level 1 represents the user's ultimate objective.

Examples:

* LET Mastery
* PNLE Mastery
* CPALE Mastery
* ALE Mastery

These are umbrella Study Plans.

They answer:

> What exam am I preparing for?

---

# Level 2 — Subject Mastery Plans

Each Goal is composed of multiple Subject Mastery Plans.

Example

LET Mastery

↓

Professional Education Mastery

↓

General Education Mastery

↓

Major Specialization Mastery

Each Subject Mastery Plan has its own progress, completion percentage, and readiness.

This allows users to immediately identify weak areas.

---

# Level 3 — Modules

Each Subject Mastery Plan is divided into logical modules.

Example

Professional Education Mastery

↓

Educational Psychology

↓

Assessment of Learning

↓

Curriculum Development

↓

Professional Education

Modules are organizational sections only.

They are not separate Study Plans.

---

# Level 4 — Notes

Modules contain Notes.

Example

Educational Psychology

↓

Bandura

↓

Piaget

↓

Vygotsky

↓

Skinner

↓

Bruner

↓

...

Notes remain the center of NoteLib.

Everything ultimately leads to Notes.

---

# Level 5 — Study Pack

Every Note generates a Study Pack.

Examples

* Summary
* Key Concepts
* Quick Review
* Practice Quiz
* Deep Explanation

Study Packs remain unchanged.

---

# Level 6 — Practice

Users strengthen mastery through existing practice modes.

Examples

* Quick Review
* Challenge Quiz
* Adaptive Practice
* Long Exam
* Board Exam Mode
* Interview Practice

Practice updates Concept Health.

Concept Health updates Progress.

Progress updates Study Plan readiness.

---

# Overall Information Architecture

Goal

↓

Subject Mastery Plan

↓

Module

↓

Note

↓

Study Pack

↓

Practice

This hierarchy should become the foundation for Guided Learning.

---

# Progress Hierarchy

Progress should exist naturally at every level.

Bandura

100%

↓

Educational Psychology

80%

↓

Professional Education Mastery

65%

↓

LET Mastery

45%

↓

Overall Readiness

35%

The same concept repeats recursively.

Users always understand where they are regardless of hierarchy level.

---

# Study Plan Metadata

Every Study Plan should expose useful metadata before users start.

Examples

Professional Education Mastery

Goal

LET Professional Education

Modules

4

Notes

35

Estimated Study Time

12–15 hours

Difficulty

Intermediate

Current Readiness

65%

This makes Study Plans feel like curated learning paths rather than folders.

---

# Example Study Plans

## LET

Goal

LET Mastery

Subject Mastery Plans

* Professional Education Mastery
* General Education Mastery
* Major Specialization Mastery

---

Professional Education Mastery

Modules

* Educational Psychology
* Assessment of Learning
* Curriculum Development
* Professional Education

---

## PNLE

Goal

PNLE Mastery

Subject Mastery Plans

* Fundamentals of Nursing Mastery
* Medical-Surgical Nursing Mastery
* Pharmacology Mastery
* Fluid, Electrolyte & Acid-Base Balance Mastery
* Maternal and Child Nursing Mastery
* Pediatric Nursing Mastery
* Psychiatric Nursing Mastery

---

## CPALE

Goal

CPALE Mastery

Subject Mastery Plans

* Fundamentals of Accounting Mastery
* Financial Accounting & Reporting Mastery
* Auditing Mastery
* Taxation Mastery
* Management Advisory Services Mastery
* Financial Management Mastery
* Regulatory Framework & Business Law Mastery

---

## ALE

Goal

ALE Mastery

Subject Mastery Plans

* Architectural Design Mastery
* Building Technology Mastery
* Building Utilities Mastery
* National Building Code Mastery
* Site Planning Mastery

---

# Relationship with Progress

Progress should not be removed.

Instead, Progress becomes the measurement system used by Study Plans.

Progress answers:

> How am I doing?

Study Plans answer:

> What should I study next?

The two features should complement each other rather than overlap.

---

# Relationship with Guided Learning

This document intentionally stops at Study Plans.

Guided Learning (Journey) will be designed later.

Once Study Plans are fully curated, Guided Learning will simply determine:

* what should be studied next,
* when reviews should happen,
* which modules need reinforcement,
* and how users progress through the existing hierarchy.

This separation keeps implementation incremental and avoids scope creep.

---

# Design Principles

* Notes remain the center of NoteLib.
* Study Plans organize Notes.
* Progress measures mastery.
* Guided Learning recommends the next step.
* Every layer should work independently but become more powerful when combined.
* The architecture must remain reusable across Student, Exam Taker, Teacher, and Professional profiles.
