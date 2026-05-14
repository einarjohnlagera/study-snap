# professional-profile.md - NoteLib Feature Context

## Goal

Professional Profile targets users studying for career advancement — certifications, upskilling, and professional development. It reuses the full quiz engine with applied-learning framing rather than academic recall.

At v1 launch, Professional Profile is a **presentation and generation-context change only** — no new backend complexity, no new quiz engine, no new persistence model.

---

## Identity

- **Profile type enum**: `PROFESSIONAL`
- **Vibe**: applied learning, practical scenarios, certification readiness
- **No exam date field** — professionals prepare on their own timeline, not against a fixed test date
- **Dashboard emphasis**: weak area coverage and certification readiness, not study streaks or academic calendar framing

---

## Mode Visibility

Professional Profile uses the full exam mode set with profile-aware label overrides. Board Exam is hidden (Board Taker profile only).

| Mode | Shown | Label | Notes |
|---|---|---|---|
| Quick Review | ✓ | "Quick Review" | Same as Student |
| Challenge Quiz | ✓ | **"Certification Review"** | Same `CHALLENGE` engine; label override only |
| Adaptive Practice | ✓ Plus / Pro | "Adaptive Practice" | Same engine; weak concept targeting is central for cert prep |
| Long Exam | ✓ Pro only | **"Full Practice Exam"** | Same `LONG_EXAM` engine; label override only |
| Board Exam | Hidden | — | Board Taker profile only |

Label overrides are display-only. Engine discriminators (`CHALLENGE`, `LONG_EXAM`) and all backend mechanics are unchanged. `exam-mode-visibility.ts` is the single source of truth for all visibility and label decisions.

Labels must **not** appear in backend APIs, session storage, or analytics event names — only in `exam-mode-visibility.ts` and the UI rendering layer.

---

## Dashboard Emphasis

- **Primary CTA**: "Review your study material" — less academic-sounding than Student copy
- **Resume card label**: "Continue Certification Review" or "Continue Practice" (not "Continue Challenge Quiz")
- **Progress framing**: weak areas and coverage gaps (Adaptive Practice surfaces these) over raw quiz scores and streaks

---

## Generation Framing

Study Pack and quiz generation for Professional Profile users applies applied-learning framing:

- Questions are scenario-based and practical, not pure recall
- Explanations reference real-world application, not academic theory
- The `courseProgram` field drives domain context: the AI frames examples and questions in the user's professional field

Prompt hints for Professional Profile already exist in `OpenAiLlmStudyPackService`. Extending them when Professional Profile is activated does not require new prompt files — it is a generation-context parameter passed alongside learner level and course/program.

---

## What Professional Profile must never become

- A board exam simulation (that is Board Exam Mode, Board Taker profile only)
- A profile that introduces new quiz engine logic or new persistence models
- A profile with different plan gating rules than Student (same quotas, same feature access thresholds)
- A profile where Challenge Quiz or Long Exam are hidden without parity justification

---

## Interview Practice Mode — deferred (v0.14+)

Interview Practice was considered as a Professional-specific quiz mode at v0.13.0 design time. It was deferred because:

1. True interview practice requires a conversational, open-ended AI evaluation loop — architecturally different from the current multiple-choice quiz engine.
2. Adding it as a sixth mode breaks the locked 5-mode contract in `EXAM_MODES.md`.
3. Adaptive Practice with Professional-framed prompts covers the core use case at v1.

Design direction for v0.14+:
- Interview Practice is a mode variant of Adaptive Practice, not a standalone engine
- Generates scenario-based questions without a single correct answer
- AI evaluates quality of reasoning, not answer selection match
- Needs its own design doc before implementation

---

## Cross-Reference

- Mode visibility and label overrides: `frontend/lib/exam-mode-visibility.ts` (source of truth)
- Exam mode hierarchy and 5-mode contract: `docs/product/EXAM_MODES.md`
- Profile settings surface: `docs/features/profile.md`
- Plan gating (quotas, feature access): `docs/product/PLANS.md`
