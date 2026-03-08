# Study Snap – Project Context

Study Snap is an AI-powered SaaS application that converts user notes into structured study materials.

The application processes either text notes or image-based notes and generates:
- Summary
- Key Concepts
- Practice Quiz

The goal is to create reusable study packs that users can review later.

---

## Core Product Idea

Study Snap helps users turn messy notes into a clear study pack.

A study pack includes:
- concise summary
- key concepts
- practice quiz

Users can save generated study packs and revisit them in their Study Library.

---

## Target Users

Study Snap is designed for:
- students preparing for exams
- learners reviewing lecture notes
- professionals preparing for interviews
- developers reviewing technical concepts

---

## Technology Stack

Frontend
- Next.js
- React
- Tailwind CSS

Backend
- Java
- Spring Boot
- REST APIs

Database
- PostgreSQL

AI Services
- OpenAI LLM (`gpt-4.1-mini` for MVP)

OCR
- Google Cloud Vision

---

## Core Architecture

### Pipeline for text notes
User Notes
→ Backend API
→ LLM Prompt
→ Structured JSON Output
→ Saved Review
→ Displayed Study Pack

### Pipeline for image notes
Image Upload
→ OCR Service
→ Text Normalization
→ LLM Prompt
→ Structured JSON Output
→ Saved Review

---

## OCR Processing Strategy

Study Snap uses Google Cloud Vision OCR for image-based notes.

### OCR pipeline
Image upload
↓
Image validation
↓
Quick text detection
↓
If text detected → DOCUMENT_TEXT_DETECTION
↓
Extract text
↓
Normalize OCR text
↓
Send text to LLM

### Guardrails
- max image size
- supported formats (jpg/png/webp if supported)
- reject images without readable text

---

## OCR Text Normalization

OCR text often contains artifacts such as:
- broken line breaks
- irregular spacing
- hyphenated words
- empty lines

### Normalization steps
- trim whitespace
- collapse multiple spaces
- replace single line breaks with spaces
- preserve paragraph breaks
- remove OCR artifacts where possible

Pipeline:
OCR → normalize text → LLM prompt

---

## Demo Mode

Study Snap includes a demo mode to showcase functionality without consuming LLM usage.

Demo mode is triggered via:

`/study?demo=true`

### Demo behavior
- prefill example notes
- simulate generation delay
- return static placeholder review
- do NOT call backend API
- do NOT call OpenAI
- do NOT write to database

### Purpose
- show product value instantly
- prevent abuse of paid LLM calls

---

## Study Library

Generated reviews are saved as study packs.

Users can revisit study packs from a dashboard.

### Dashboard features (MVP)
- list saved study packs
- open a study pack
- delete a study pack

### Future improvements
- rename study pack
- search
- filter
- folders
- analytics

---

## Tags (Planned)

Study packs may include tags for organization.

### Example tags
- Biology
- Chemistry
- Java
- Spring Boot
- Interview Prep

### Purpose
- filtering study packs
- subject organization
- future analytics

### Recommended MVP storage
`tags: string[]`

Example:
`["Biology", "Photosynthesis"]`

---

## Quiz Generation Strategy

Practice quizzes should include mixed difficulty levels.

### Question types
- Recall (facts, definitions)
- Understanding (concept explanation)
- Application (simple scenario)

This improves perceived quiz quality and usefulness for study and interview preparation.

---

## LLM Model Strategy

### Current model
`gpt-4.1-mini`

Used for:
- summary generation
- key concept extraction
- quiz generation

### Reason
- good cost/performance
- suitable for MVP scale

### Future possibility
- stronger models for premium features

---

## Cost Guardrails

To prevent abuse:
- demo mode does not call LLM
- review API rate limits will be applied
- OCR only runs if text is detected
- image upload size limits enforced

---

## Current Development Status

### Already implemented
- frontend landing page
- study page
- Spring Boot backend
- review generation pipeline
- OpenAI LLM integration
- review persistence to database

### Currently implementing
- OCR pipeline
- demo mode
- quiz difficulty improvements
- study library

### Upcoming features
- Study Library dashboard
- tagging system
- usage limits
- authentication
