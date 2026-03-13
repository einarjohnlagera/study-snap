# Dashboard Recommendation

The dashboard recommendation system helps users decide what to study next.

Its purpose is to make the dashboard feel useful, personalized, and learning-oriented instead of acting only as a static Study Pack library.

---

## Purpose

The dashboard should guide users toward the most valuable next action.

This is primarily done through the recommendation card shown near the top of the dashboard.

The recommendation system should feel:

- helpful
- simple
- personalized
- explainable

---

## Today's Focus

The dashboard includes a dedicated `Today's Focus` card that highlights one high-value learning action for the current day.

Endpoint:

- `GET /dashboard/today-focus`

Response shape:

```json
{
  "type": "RESUME_REVIEW | RETRY_REVIEW | PRACTICE_WEAK_CONCEPT | REVIEW_PACK | STUDY_SUGGESTION",
  "studyPackId": "uuid-or-null",
  "title": "string",
  "message": "string",
  "actionLabel": "string"
}
```

Today's Focus priority:

1. Resume unfinished review (`resumeState = QUESTION_IN_PROGRESS`)
2. Retry incorrect questions (`resumeState = RETRY_IN_PROGRESS`, including retry transition handling)
3. Practice weak concepts from the latest completed Quick Review session
4. Review a specific Study Pack (fallback priority: last opened -> most recently created -> most recently reviewed)
5. Study suggestion only if no Study Packs exist

Action routing guidance:

- `RESUME_REVIEW` -> Study Pack Quick Review route
- `RETRY_REVIEW` -> Study Pack Quick Review route
- `PRACTICE_WEAK_CONCEPT` -> Study Pack adaptive practice route
- `REVIEW_PACK` -> Study Pack Quick Review route
- `STUDY_SUGGESTION` -> Study route (no packs yet)

---

## Recommendation Priority

The dashboard recommendation follows this priority order:

1. Resume unfinished Quick Review session
2. Weakest recently reviewed Study Pack
3. Recently opened Study Pack
4. Recently created Study Pack
5. No recommendation if no Study Packs exist

---

## Resume Quick Review Priority

If the user has an IN_PROGRESS Quick Review session:

- the dashboard should prioritize that session
- the recommendation card should prompt the user to resume it

Example:

Title: Resume Quick Review  
Message: You left off on Question 3 of 5. Continue your Quick Review.  
CTA: Resume Review

This has higher priority than all other recommendation types.

---

## Weakest Recent Study Pack

If there is no unfinished session, the dashboard should recommend the weakest recently reviewed Study Pack.

Rules:

- use the latest completed Quick Review session for each Study Pack
- ignore historical best score for recommendation ranking
- prefer lower latest scorePercentage
- if scores tie, prefer the more recently reviewed Study Pack

This helps users return to topics where their understanding is weaker.

Example:

Pack A latest score: 80%  
Pack B latest score: 40%

Recommend Pack B.

---

## Recently Opened Study Pack

If no unfinished session exists and no weak reviewed pack is available:

- recommend the most recently opened Study Pack

This gives users a simple continuation path even when they have not taken Quick Review recently.

---

## Recently Created Study Pack

If no unfinished session exists, no weak reviewed pack exists, and no recently opened pack exists:

- recommend the most recently created Study Pack

This helps users start using newly generated Study Packs.

---

## Messaging Rules

The dashboard card should adapt its title, message, and CTA based on the learning state.

### Case A — Latest score < 100

Title: Continue studying  
Message: You recently scored {score}% on this Study Pack. Review it again to improve your score.  
CTA: Continue Review

### Case B — Latest score = 100

Title: Nice work on this pack  
Message: You scored 100% on your latest Quick Review. Practice again anytime to keep it sharp.  
CTA: Practice Again

### Case C — No Quick Review yet

Title: Start studying  
Message: You created this Study Pack recently. Start your first Quick Review.  
CTA: Start Review

### Case D — Resume Quick Review

Title: Resume Quick Review  
Message: You left off on Question {n} of {total}. Continue your Quick Review.  
CTA: Resume Review

---

## Recommendation Response Data

The dashboard recommendation should return enough information for the frontend to render context-aware messaging.

Typical fields include:

- studyPackId
- title
- summaryPreview
- reason
- lastScorePercentage
- lastReviewedAt
- lastOpenedAt
- createdAt
- currentQuestionIndex (for resume)
- totalQuestions (for resume)

Reason values may include:

- RESUME_REVIEW
- LOW_SCORE_RECENT
- RECENTLY_OPENED
- RECENTLY_CREATED

---

## UX Principles

The dashboard recommendation card should:

- make the next action obvious
- avoid redundant calls to action
- be easy to scan
- reflect actual recent learning state
- acknowledge user progress

Examples:

- weak score → encourage improvement
- perfect score → encourage reinforcement
- unfinished session → encourage resumption

---

## Non-Goals

The dashboard recommendation system does not currently include:

- AI-generated coaching text
- full spaced repetition scheduling
- multiple recommendations at once
- advanced confidence scoring
- topic mastery modeling

These may be added later.
