# v0.60.3 Challenge Quiz Shaping — Second Opinion Outcome

Run 2026-07-27 (GPT, via `01-second-opinion.txt`), reconciled same day with a follow-up Opus
(`advisor()`) pass on the two contested items. Raw GPT transcript was pasted directly into the
planning conversation, not saved as a separate file — this is the durable summary.

## Verdict per item (GPT)

- **Item 1 (adaptive question count):** Ship as planned. Reopening argument (pacing/click-friction,
  mode-wide) is a genuine distinction from the 2026-07-24 cost-based rejection, not repackaging.
  No strong evidence for 10/12/15 specifically over a simpler scheme, but `MID=12` already has a
  real functional role (existing no-prior-score fallback), so left as-is.
- **Item 2 (Redo Missed session fix):** Ship with changes. Reusing `quotaExempt` as a provenance
  signal conflates billing exemption with session identity — two unrelated concerns under one
  field name. Recommended a separate signal.
- **Item 3 (submit guard):** Ship as planned. No issues raised.
- **Item 4 (onboarding coverage-gap capture):** Ship with changes — sequencing. The Diagnostic
  Read's own methodology warns against onboarding changes mid-measurement; "different segment"
  is an assumption, not a confirmed non-overlap. Recommended postponing until the read closes.

## Resolution (Claude + Opus advisor(), same day)

**Item 2 — accepted GPT's critique, rejected the generic fix, used existing project idiom
instead.** Verified `quotaExempt` is genuinely named for its billing effect, not session origin —
GPT's concern holds. Rather than GPT's generic "add a separate field," reused this codebase's
existing `QuizSessionStateUtils.withPoolSourced`-style `sessionState` JSONB marker pattern (already
proven in `ChallengeQuizService.java` and `LongExamService.java`) — a `withRedoMissedSource`
marker. Opus's follow-up pass caught a gap in this resolution before it shipped: the write-up had
described the marker as *replacing* `quotaExempt`, which would silently remove redo sessions from
billing exemption. Corrected — both fields are now explicitly required, each read by a different,
named consumer (`quotaExempt` by `countChallengeQuizUsedThisMonth`; the marker by
`resolveExistingChallengeSession`'s new provenance check). Opus also flagged an unstated backfill
consequence (pre-existing redo sessions lack the marker and will be treated as ordinary on next
encounter) — now documented as an accepted, one-time, self-healing gap rather than a silent
surprise. See `## v0.60.3` → `### Item 2` in `docs/product/ROADMAP.md` for the final design.

**Item 4 — accepted GPT's postponement recommendation, replaced "wait unconditionally" with a
falsifiable check.** Rather than waiting on the calendar alone, proposed resolving the uncertainty
with a prod query on the signup-surge cohort's `profileType` composition. Opus's follow-up pass
added the missing pass/fail bar: the query must show *effectively zero* `STUDENT`-profile accounts
in the measurement window, not merely "skews Board Exam" — any meaningful `STUDENT` presence means
the overlap is real and this item waits until the read closes (~2026-08-06) regardless. Requires
prod `DB_USER` access this assistant does not have; the query and its result are the user's to run
before kickoff. See `### Item 4`'s sequencing-risk paragraph in `docs/product/ROADMAP.md`.

**Items 1 and 3 — no changes.** Both independently confirmed clean by GPT and Opus; not re-opened
for a third round.

## Status

Resolved. Both open design questions (Item 2's provenance signal, Item 4's sequencing gate) are
finalized and written into `docs/product/ROADMAP.md`. v0.60.3 remains a scoped candidate, not yet
kicked off — Item 4's kickoff is gated on the prod query above; Items 1-3 have no remaining
blockers.
