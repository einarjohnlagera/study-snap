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
| Interview Practice (sub-mode) | ✓ Pro only | **"Interview Practice"** | Sub-mode of Adaptive Practice. Surfaced as a dedicated dashboard card and a Professional mode-selection tile. See "Interview Practice Mode (v0.14.0)" below. |

Label overrides are display-only. Engine discriminators (`CHALLENGE`, `LONG_EXAM`, `ADAPTIVE`) and all backend mechanics are unchanged. `exam-mode-visibility.ts` remains the source of truth for primary mode-selection visibility and label decisions. Interview Practice is rendered as a Professional-only sub-mode tile in the mode-selection UI while continuing to run through `ADAPTIVE` with `subMode: "INTERVIEW"`.

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

## Interview Practice Mode (v0.14.0)

Interview Practice is a **sub-mode of Adaptive Practice**, not a standalone quiz mode. It reuses the `ADAPTIVE` engine discriminator and carries its variant identity in session state JSONB (`subMode: "INTERVIEW"`). The locked 5-mode contract in `EXAM_MODES.md` is preserved.

### Identity

- **Audience**: Professional profile, Pro plan only
- **Vibe**: Coached interview prep — scenario-based questions with per-answer AI critique, framed like a senior interviewer following up
- **Surface**: Dedicated dashboard card on the Professional dashboard plus a Professional mode-selection tile. The mode-selection tile is the canonical per-note entry; the dashboard card auto-picks the most-recent ready note for quick re-entry.
- **Discovery surfaces**: Landing page target users, Help Center Professional Guide, and Learn page Professional guides should all mention Interview Practice so Professional-profile users can find the workflow before they enter the app.
- **Differentiator from Adaptive Practice**: scenario-style prompts, per-answer AI critique mid-session, Interview Readiness Report instead of standard score result

### Setup

- Source: up to 3 notes (primary + 2 additional); no subject constraint required
- Question count: **5 or 10** (no other lengths). 5 = warm-up (~10 min), 10 = full session (~20 min)
- Difficulty: locked at senior framing for Pro
- Disclaimer: "AI will provide feedback after each question. Sessions count toward your monthly Interview Practice quota."

### In-session

- **Each question**:
  - Longer scenario prompt (3–5 sentences vs. 1 for standard MCQ)
  - 4 multiple-choice options reframed as approaches ("How would you handle this?") rather than recall ("What is the answer?")
  - Soft 2-min per-question timer (visible countdown, **non-enforcing** — at expiry shows a gentle "Try to wrap up" cue, never auto-submits)
- **After each answer (per-answer AI critique, gpt-4.1-mini)**:
  - Verdict line: ✅ Strong / ⚠️ Workable / ❌ Reconsider
  - Rationale (~2 sentences): why this approach works in practice; what a senior interviewer would probe next
  - Follow-up prompt: one sentence — "In a real interview, you might be asked: …"
- "Next Question →" advances to the next scenario

### Result — Interview Readiness Report

- Overall readiness band (not a percentage): "Ready" / "Almost ready" / "Needs more practice"
- **Strengths**: 2–3 concept areas where reasoning was strong
- **Gaps**: 2–3 areas to drill, each linked to Adaptive Practice on those concepts
- **Talking points**: 3 concepts the user should be able to articulate cleanly in an interview
- **Pacing notes**: questions where the user exceeded the 2-min soft timer, surfaced as gentle coaching feedback
- CTAs: `Practice Again` (primary) / `Drill Weak Areas` (secondary)
- No inline learner-level pill (this is a senior-framed mode)

### Generation strategy

- New prompt pair: `interview-practice-developer.txt` + `interview-practice-system.txt`
- Generation prompt is **section-aware**: scans the note's Key Concepts and tries to spread questions across multiple facets (technical / applied / behavioral when content supports it). No formal template object — the note itself carries role context via title, courseProgram, and content.
- New critique prompt pair: `interview-critique-developer.txt` + `interview-critique-system.txt`
- **Model split** (cost optimization):
  - Generation call: `gpt-4.1` (premium, scenario quality matters)
  - Critique calls: `gpt-4.1-mini` (cheaper, scoped output)

### Quota and gating

- **Separate Interview Practice quota** — does NOT consume Adaptive Practice or Challenge Quiz quotas
- Pro plan only at launch: **10 sessions/month**
- New `Feature.INTERVIEW_PRACTICE` value gated by `FeatureGateService`
- Quota tracked in `UserUsageEntity.interviewPracticeUsedThisMonth` (reset by `BillingUsageResetJob`)
- Free/Plus users see a Pro upsell paywall (`getUpgradeCtas` with `"interview-practice"` context)

### What Interview Practice must never become

- A 6th quiz mode. It is a sub-mode of Adaptive — engine discriminator stays `ADAPTIVE`.
- A timed exam. The 2-min per-question timer is a soft coaching cue and must never auto-submit.
- A free or Plus feature at launch. Per-answer LLM critique cost only makes sense at Pro pricing.
- An open-ended free-text mode. v1 keeps MC structure with scenario reframing; conversational evaluation is a v0.16+ consideration if usage justifies it.
- A mode that consumes Adaptive Practice or Challenge Quiz quotas. The dedicated quota is the value-clarity choice.

### Future direction

- **Structured interview templates** (e.g. "Backend Engineer = PL + DB + Behavioral"): would need a role taxonomy or user-defined template system. Out of scope until v1 usage data shows demand and v1 limitations are real.
- **Open-ended / conversational evaluation**: free-text answers with AI rubric scoring instead of MC. Architecturally different — would need new session schema, new evaluation pipeline, new result model. Consider only if v1 usage proves the demand and the MC-with-critique format hits its ceiling.
- **Profile / role enrichment**: capturing target role explicitly on the user profile (not on the note) to drive better generation context. Bigger architectural decision; do not bundle with Interview Practice work.

---

## Cross-Reference

- Mode visibility and label overrides: `frontend/lib/exam-mode-visibility.ts` for primary modes; Interview Practice is a Professional-only sub-mode tile rendered in the mode-selection UI
- Exam mode hierarchy and 5-mode contract: `docs/product/EXAM_MODES.md`
- Adaptive Practice (parent mode): `docs/features/adaptive-practice.md`
- Profile settings surface: `docs/features/profile.md`
- Plan gating (quotas, feature access): `docs/product/PLANS.md`
