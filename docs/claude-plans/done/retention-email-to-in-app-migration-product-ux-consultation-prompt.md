# Consultation: should retention emails move to in-app notification instead of email?

**For a product-UX reviewer. Self-contained — no repo access needed.**
**Written 2026-09-23. Reopens a decision made 2026-09-08; decision needed before implementation starts.**

---

## 1. What NoteLib is, in one paragraph

NoteLib is a notes-first study workspace used mainly by Philippine board-exam takers. A learner captures notes, generates an AI "Study Pack" from a note (summary, key concepts, quiz), and practises with several quiz modes. Retention is the product's binding constraint — of 152 activated learners measured earlier this year, 141 never returned. Most retention mechanics exist to fight that number.

## 2. What exists today

**Email side.** `RetentionEmailScheduler` sends four behavior-triggered email types through Resend: `INACTIVITY` (no study activity for 3 days, 3-day cooldown), `WEAK_CONCEPT` (a weak quiz result untouched for 3 days, 5-day cooldown), `WEEKLY_SUMMARY` (opt-in, Sunday), and `DUE_CONCEPTS_DIGEST` (due spaced-repetition concepts, daily eligibility check, 1–6 day cooldown depending on whether the learner picked review days). `INACTIVITY` alone accounts for **1,639 sends in the last 30 days to 393 enrolled users.** All four are gated by per-user preference toggles under Settings → Email Preferences, default-on for new signups except `WEEKLY_SUMMARY`.

**⚠️ There is no open/click tracking on any of it.** Verified by reading the code: the Resend webhook handler (`ResendWebhookController` / `ResendWebhookService`) processes exactly three event types — `email.bounced`, `email.complained`, `email.suppressed` — and never `email.opened` or `email.clicked`. `email_log`, the table that records every send, stores only `id`, `user_id`, `email_type`, `sent_at`. **We know these emails are sent. We have never measured whether anyone reads them.**

**In-app side.** A notification substrate already exists (`notifications` table, bell icon, unread badge, inbox panel, polling) with one live producer today: Official Review Set update notifications. It is real, tested, production-proven infrastructure — not something that needs to be built from scratch.

## 3. The decision this reopens, and exactly what it rested on

An earlier audit (`docs/claude-plans/attention-notifications-email-expansion-stage1.md`, written 2026-09-07/08) evaluated building an in-app version of the inactivity nudge — its own "Stage H" — and rejected it:

> "Rejected on measured evidence, not taste. The audit found `INACTIVITY` email already fills the role — 1,639 sends in 30 days to 393 enrolled users, 3-day cooldown, unsubscribable. A second channel saying the same thing is the cross-channel duplication [the source brief] warns about."

**That conclusion is entirely about send volume. It contains no measurement of whether the email is ever opened.** "Duplication" is only the right frame if the first channel is landing — if it isn't, in-app wouldn't be duplicating a working channel, it would be the first time the message actually reaches anyone.

## 4. What's prompting a second look

The owner's own observation, discussed informally with a product-thinking GPT session already, not yet validated against data: **a growing sense that most learners — students on their phones — simply don't check email the way an office worker would, and may perceive email as "a work thing."** This is a plausible generational/behavioral pattern, not obviously wrong, but it is currently **anecdotal** — we have no NoteLib-specific number for it either. Both the original rejection and this new instinct are, right now, arguments without data on the one variable that actually decides this: real open/click rates.

## 5. The options on the table

**A — Instrument first, decide after.** Turn on Resend's open/click tracking, let it run for a few weeks against the existing email channels, then make this call from real numbers instead of either a volume proxy or an intuition. Slowest, but it's the only option that actually answers the question being asked.

**B — Move immediately, fully.** Replace one or more of the four email types with in-app-only equivalents now, on the strength of the behavioral instinct. Fast, but permanently forecloses ever knowing what the email channel's real ceiling was — and note the earlier audit's own home turf: pure send volume is *already known to be a poor proxy here* (see §6's cross-channel budget note).

**C — Move the highest-suspicion channel only, keep the rest as a control.** E.g. move `DUE_CONCEPTS_DIGEST` (the most "actionable, expected to be time-sensitive" type) to in-app-only while leaving `INACTIVITY` on email, so a future read of in-app engagement vs. `INACTIVITY`'s open-rate-once-instrumented gives an actual comparison rather than a single before/after with no baseline.

**D — Dual-channel: add in-app, don't remove email.** Send both. Cheapest to reverse, but this is exactly the shape the original audit called duplication — worth naming plainly rather than sliding past it, and it reintroduces the cross-channel volume problem in §6 below.

**E — Something else.** We'd rather hear this than have you pick between our four.

## 6. Constraints — please design within these

- **A global cross-channel email budget does not exist yet.** `EMAIL_DAILY_LIMIT` (100/day, 40 reserve) currently gates `INACTIVITY` only — the other three channels are unbudgeted, and observed peak days have already hit 156–159 total sends against the notional 100 cap. Any option that *keeps* email must not worsen this; any option that *removes* email from a channel mechanically improves it.
- **The in-app substrate has exactly one producer today, at low volume** (Official Review Set updates, ≤30 recipients per fire). Retention-scale volume (hundreds of daily-eligible learners) would be a different order of magnitude through that pipe than anything it has carried so far.
- **Per-channel preference toggles are real and load-bearing** (`inactivityRemindersEnabled`, `dueConceptsDigestRemindersEnabled`, etc.) — 252 of 396 accounts had the due-concepts preference off at last measurement. Any redesign must decide what an existing *email-off* preference means for an in-app equivalent — does turning email off also turn off the in-app version, or are they independent settings? Say which, and why.
- **The bell has no reach beyond someone who opens the app.** A learner who has genuinely stopped opening NoteLib gets nothing from an in-app notification, ever — that's the one thing email can do that in-app structurally cannot (reach someone who isn't currently using the product). Please weigh this explicitly; it's the strongest argument against "just move everything."
- **Do not propose deleting the `email_log` — table or its cooldown/dedup role**, whichever channel wins; cooldown logic depends on it regardless of transport.

## 7. What we are asking you for

1. **A, B, C, D, or E** — and the reasoning, not just the pick.
2. **Is send-volume-without-open-data a sufficient basis to decide this either direction?** If you think there's a way to reason to a confident answer without instrumenting first, say what it is.
3. **If you pick anything other than A, how would you bound the risk of being wrong** — i.e. what would tell us later that the move was a mistake, given we still won't have had open-rate data to check the premise against?
4. **Does the "in-app can't reach someone who's stopped opening the app" asymmetry change your answer for any specific channel** — e.g. is `WEAK_CONCEPT` (someone recently active) a different case from `INACTIVITY` (someone who's already drifting)?

## 8. Practical notes

- Answer in prose. No mockups needed.
- **Disagreeing with us is the point.** Both the original audit's rejection and the owner's newer instinct are argued positions, not settled facts — the value of this consultation is stress-testing whichever one you find weaker, not confirming either.
- If you need a fact about the product that isn't here, say what it is and why it changes your answer rather than assuming.
