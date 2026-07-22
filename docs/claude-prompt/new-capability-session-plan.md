# Session Plan — New Capability Ideation

> **Purpose.** A genuinely open-ended "what's missing" pass through Fable — the one angle none of the
> three prior backlogs covered. Conversion Audit fixes *existing* surfaces. Smart Review Planning is
> one specific big feature bet (curriculum auto-assembly). App Shape deepens how *existing* pieces
> compose and reads as an app. None of them asked "what capability areas does this product not have
> any version of at all yet." This session does, with the same hard-constraint scaffolding as the
> others so it doesn't propose things that don't fit NoteLib.

---

## Why a single session, not a series

Deliberately one session, not a fan-out series like Smart Review Planning (7) or the Conversion Audit
(7) — the user flagged wanting to conserve remaining Fable budget for a separate project. One
broad-but-structured prompt, covering multiple angles internally (underserved profiles, habit
formation/retention-adjacent-but-new, collaboration, content modalities, teacher depth), classified
through the same `docs/skills/roadmap-feature-audit.md` four-tier framework the other two backlogs use.

## Known-stale input excluded on purpose

`docs/product/SPEC.md`'s "Non-Goals (Current Scope)" section (line 1686) lists "classroom/teacher
management" and "spaced repetition scheduling" as out of scope — both already exist (Teacher is a full
profile type; ConceptHealth's 3-day due-threshold is spaced-repetition-shaped, confirmed in the
conversion audit's A6 session). That section is not fed to Fable; the prompt below states the real,
current hard constraints directly instead of trusting a stale doc.

## Prompt

Full paste-ready prompt: `docs/claude-prompt/new-capability-prompts/01-new-capability-ideation.txt`

## Output

`docs/claude-prompt/new-capability-out/01-new-capability-ideation.md`

## Status

Run and verified 2026-07-12. Output: `docs/claude-prompt/new-capability-out/01-new-capability-ideation.md`
(11 ideas, 9 explicit rejections, top-3 recommendation).

**One serious correction made:** Idea 5 (originally "the first out-of-app habit channel," the #1
recommendation) claimed NoteLib has no existing re-engagement email system. False — a real, mature
retention-email system already ships (`docs/features/retention-emails.md`: `INACTIVITY`,
`WEAK_CONCEPT`, `WEEKLY_SUMMARY`, real scheduler, Resend delivery, opt-in flags, unsubscribe/webhook
handling). Corrected in place: the idea survives as a smaller, cheaper addition (one new email type on
the existing system, not new infrastructure), and Idea 4's claimed dependency on Idea 5 was also wrong
for the same reason (both corrected with dated notes at the point of error, same pattern as the
Smart Review Planning S1/S2 amendments and the App Shape citation fixes).

Other spot-checked claims (PARENT enum state, `getQuizSessionModeLabel`, `BillingUsageResetJob`/
`RetentionEmailScheduler` existence, PLANS.md teacher-tier claim, onboarding's draft-before-save
contract) held up as accurate.

Not yet wired into ROADMAP.md — ask before adding, per the established pattern of confirming first.

**Correction / re-prioritization, 2026-07-15:** a post-v0.48.0 Fable strategy checkpoint (`docs/claude-prompt/retention-diagnosis-session-plan.md`'s "Strategy checkpoint" section, Session B) re-evaluated the 8 unshipped ideas (2, 3, 4, 6, 7, 9, 10, 11 — Ideas 1 and 5 shipped in v0.46.0, Idea 8 was found already-existing) through the retention-first lens established after the retention diagnosis. Headline changes: **Idea 4 (Parent Digest) promoted to a conditional retention candidate**, gated on the v0.48.0/H1 read rather than persona-completeness reasoning; **Idea 9 (Offline Access)'s cost estimate was wrong** — a service worker and offline fallback (`frontend/public/sw.js`, `offline.html`) already exist in the codebase, so this is a content-layer job now, not the "first PWA-shaped investment" the original session assumed; **Ideas 2 and 3 folded into the Bulk Quiz Generation trigger condition** (ROADMAP.md's teacher-flow section) rather than tracked separately. Ideas 7, 10, 11 unchanged. Full reasoning, including three items flagged as needing a real product decision before they're scopeable (Idea 2's attribution mechanism, Idea 4's email-only-vs-dashboard shape, Idea 9's offline-routing-vs-snapshot choice), in the linked section — don't re-derive from scratch.
