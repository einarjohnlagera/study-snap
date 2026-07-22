# User Interview Script — Interim Window (2026-07-15 strategy checkpoint)

Purpose: cheap, non-confounding, zero-code — the one thing nobody has done yet in this whole diagnosis. Two short lists, not a formal research study. Keep each conversation to 10-15 minutes; these are open questions to get people talking, not a survey to read verbatim.

## Who to contact

- **3 retained users** (returned in week 2, from the W1→W2 cohort read).
- **10-15 churned exam-dated users** (had `examDate` set, did not return in the week-2 window) — prioritize this list; it's the one carrying the most open questions.

## Retained users — why do you come back?

1. What made you come back after your first session? Was there a specific reason, or did it just happen?
2. What are you actually using NoteLib for right now — cramming before something specific, or ongoing review?
3. Is there anything that almost made you stop using it?

## Churned exam-dated users — why didn't you return?

1. You had \[exam name/date] coming up when you signed up — what happened after your first session? Did you plan to come back, or was it more of a one-time thing?
2. **Content-gap probe (Smart Review Planning check):** Did you look for ready-made review materials for your exam inside the product? Did you find any? What did you do when you couldn't?
3. What did you end up using to review instead? (Listen for: photocopied reviewers, Facebook groups, Quipper, a specific competitor, or "nothing yet.")
4. **Accountability probe (Idea 4/11 check):** Does anyone else check in on your review progress — a study group, a parent, a review-center classmate? Would that matter to you, or is studying something you do alone?
5. **Connectivity probe (Idea 9 check):** Did data cost or spotty connection (e.g. during a commute) ever get in the way of opening the app?
6. **Language probe (Idea 10 check) — don't ask directly, just notice:** conduct this conversation in Taglish if that's natural for the person. Did they stumble on any English UI copy, or was it a non-issue?
7. **Social probe (Idea 11 check):** Do you study in a group, formally or informally — with classmates, in a review center, or online? Would that have changed how you used NoteLib?
8. **Offline/export-value probe (added 2026-07-15, PDF export check):** Do you ever want your review material outside the app — printed, offline, shared with a classmate? This arbitrates whether PDF export's near-zero usage is a value problem (nobody wants this) or a discovery problem (nobody found it) — see `retention-diagnosis-session-plan.md`'s export note. Same connectivity signal doubles as evidence for Idea 9.

## After the interviews

Fold what you hear back into `docs/claude-prompt/retention-diagnosis-session-plan.md`'s "Strategy checkpoint" section — this is meant to arbitrate the still-open question there (value problem vs. trigger problem for exam-dated users), not to generate a new backlog on its own. If a strong, unprompted theme shows up that isn't on this list, write it down verbatim before generalizing it.
