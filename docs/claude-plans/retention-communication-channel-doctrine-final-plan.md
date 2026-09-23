# Retention communication: channel doctrine and staged plan (FINAL)

**Status: Resolved doctrine + staged plan. Stage 1a is scope-ready now; Stage 1b needs one owner
decision first (§I); Stages 2–3 depend on evidence Stage 1a's instrumentation produces. No
implementation in this document — do not dispatch a Codex prompt from this file without a fresh
go-ahead per stage.**

**Supersedes the Option-A–D framing in
`retention-email-to-in-app-migration-product-ux-consultation-prompt.md`** (that file is kept, unedited,
as the record of what was asked — this file is the resolved Option E). Written 2026-09-23, owner
decision same day, resolving the 2026-09-08 "Stage H" rejection in
`attention-notifications-email-expansion-stage1.md` with a doctrine rather than a single yes/no.

---

## A. Current architecture map

### Email pipeline

`RetentionEmailScheduler` runs three cron entry points, all pinned to `Asia/Manila`
(`DISPATCH_ZONE` — deliberately matches `RetentionService.EMAIL_BUDGET_ZONE`; an earlier unpinned
version disagreed about which weekday it was, since cron used the host's default zone):

| Cron | Fires | Dispatches |
|---|---|---|
| `0 45 2 * * *` (daily, 02:45) | `runDaily()` | `sendDailyEmails()` → `INACTIVITY` + `WEAK_CONCEPT`, bundled in one summary; then `sendDueConceptsDigestEmails()` → `DUE_CONCEPTS_DIGEST`, a separate call |
| `0 0 18 * * SUN` (Sunday 18:00) | `runWeekly()` | `WEEKLY_SUMMARY` |
| `0 0 9 1 * *` (1st of month, 09:00) | `runMonthly()` | `KNOWLEDGE_IMPACT_DIGEST` |

**⚠️ The type inventory needed one more verification pass, and it changed the count.** `RetentionEmailType`
(`backend/src/main/java/com/studysnap/backend/entity/RetentionEmailType.java`) has **13 values total**;
narrowing to the ones the retention scheduler could plausibly govern (excluding auth/billing
transactional types — `EMAIL_VERIFICATION`, `PASSWORD_RESET`, `SUBSCRIPTION_EXPIRY_7_DAY`,
`SUBSCRIPTION_EXPIRY_1_DAY`, `SUBSCRIPTION_EXPIRED` — and the admin campaign type,
`RE_ENGAGEMENT_2025`, none of which this plan's doctrine addresses) leaves **six**, not five. **A
seventh value, `WELCOME`, is worth flagging rather than silently dropping**: it matched none of the
writer searches in §A's grep either, the same signature as `UNFINISHED_NOTE`'s dead-code finding below
— not confirmed dead here (out of this plan's scope to chase down), but a candidate for the same
question, not silently assumed to be fine because it's excluded as "transactional."

| Type | Dispatch site | Status |
|---|---|---|
| `INACTIVITY` | `RetentionService`, via `RetentionEmailScheduler.runDaily()` | Active |
| `WEAK_CONCEPT` | same | Active |
| `WEEKLY_SUMMARY` | `RetentionEmailScheduler.runWeekly()` | Active |
| `DUE_CONCEPTS_DIGEST` | `RetentionEmailScheduler.runDaily()`, separate call | Active |
| `KNOWLEDGE_IMPACT_DIGEST` | `RetentionEmailScheduler.runMonthly()` | Dispatched, but **0 opted-in users** as of the last production read cited in `ROADMAP.md` — dormant, not documented in `docs/features/retention-emails.md` either |
| `UNFINISHED_NOTE` | **none** — `grep -rln "RetentionEmailType\.UNFINISHED_NOTE"` across `backend/src/main/java` returns zero matches outside the enum declaration itself | **Dead configuration**, confirmed here independently and consistent with the exact finding already on record in `ROADMAP.md`'s Backlog Index: "a configured, documented, tunable email with no dispatcher, no template and no test. The enum is not a reliable inventory of what the system does." |

**⚠️ This also explains a discrepancy in the Settings copy quoted below:** the "Study reminders" toggle's
copy promises nudges "when you've been inactive **or left a note unfinished**" — but only the inactivity
half ever fires. Not this plan's to fix (the dead-code/stale-copy pair is a separate, already-tracked
finding), but worth naming so nobody re-derives "five types" or "the copy is accurate" from this
document later.

**So this plan's doctrine and Stage 1 apply to four active types plus one dormant one (five that
actually dispatch); `UNFINISHED_NOTE` is excluded as dead code, not silently rounded into any count.**

### The email budget — narrower than "100/day" suggests, verified by reading the actual query

`EMAIL_DAILY_LIMIT` (env-configurable, default **100**, `application.yaml:544`) and a
**transactional reserve** are read into `resolveReengagementBudget()`. The count it budgets against —
`countEmailsSentToday()` — runs `emailLogRepository.countBySentAtGreaterThanEqual(startOfDay)`, which
is **not filtered by email type at all**: `email_log` is written not just by `RetentionService` and
`ReEngagementCampaignService` but also by `EmailVerificationService`, `PasswordResetService`, and
`SubscriptionExpiryEmailService` (found by searching for every writer to `email_log`
— `grep -rln "emailLogRepository.save\|EmailLogEntity\|new EmailLog"` — not assumed; this search
would miss a writer using a differently-named local variable or an unenumerated repository method, so
treat this as strong evidence, not a closed inventory) — so the count this
one budget check reads is every logged email of any kind sent since midnight `Asia/Manila`, retention
and transactional alike, not a retention-scoped number.

**But only the `INACTIVITY` dispatch path calls this at all.** `WEAK_CONCEPT` (same job, same method,
no separate budget check), `DUE_CONCEPTS_DIGEST` (separate method, no budget check), `WEEKLY_SUMMARY`
and `KNOWLEDGE_IMPACT_DIGEST` (separate jobs entirely) all send unconditionally. So the count itself
would reflect cross-type volume if checked, but only one of five types ever checks it — the other four
can push the real daily total arbitrarily far past 100 and nothing in the dispatch path notices. This
is consistent with the observed peaks of 156–159 sends/day cited in `ROADMAP.md`'s existing findings.

### Preferences

Exact current Settings → Email Preferences copy (`frontend/app/settings/page.tsx:895-975`), under a
section explicitly introduced as **"Choose which optional emails you get"**:

| Toggle label | Copy | Backend field |
|---|---|---|
| Study reminders | "Nudges when you've been inactive or left a note unfinished." | `inactivityRemindersEnabled` |
| Weak-concept nudges | "A reminder to revisit concepts you missed on a quiz." | `weakConceptRemindersEnabled` |
| Weekly summary | "A Sunday recap of your week's study progress." | `weeklySummaryRemindersEnabled` |
| Due-concepts digest | "A reminder when concepts are due for review in your Study Packs. Weekly by default, or on each of your review days below once you pick some." | `dueConceptsDigestRemindersEnabled` |

**Every one of these is scoped by its own copy and by the section's own framing to email specifically**
— "nudges", "reminder", under "which optional *emails* you get." None reads as a general
"tell me about this" preference. This is the textual basis for §E below: these preferences mean
"send me this by email," not "communicate this to me at all."

### Persistence and dedup

`email_log` (`id`, `user_id`, `email_type`, `sent_at`) is the sole send record. Cooldown is enforced by
querying the most recent `sent_at` per `(user_id, email_type)` and comparing against each type's
cooldown window (3/5/7/1–6 days respectively). No unique constraint exists on `email_log` — the
existing findings note this is safe today only because every producer is a single-instance
`@Scheduled` job (a documented, pre-existing risk unrelated to this plan).

### Resend webhook coverage — confirmed gap, and confirmed available fix

`ResendWebhookController` / `ResendWebhookService` process exactly three event types today:
`email.bounced`, `email.complained`, `email.suppressed`. **Resend's API does support `email.opened`
and `email.clicked` webhook events** (confirmed against Resend's own webhook documentation) — this app
simply never subscribed to or handled them. No code change is needed to make them *available*; the gap
is in what this webhook handler does with them, and (separately, to be confirmed against the Resend
dashboard, not assumed here) whether open/click tracking is toggled on for this sending domain at all.

**Known caveat worth stating plainly, not verified against Resend specifically but well-established for
pixel-based open tracking industry-wide:** mail clients that prefetch images for privacy (Apple Mail
Privacy Protection being the best-known) inflate open counts, so **click-through is the more trustworthy
signal of the two** — see §D.

### Notification (in-app) pipeline

`notifications` table, bell + unread badge + inbox panel, polling every 60s
(`app-shell.tsx`, per `docs/features/notifications.md`). One live producer today: Official Review Set
update (`REVIEW_SET_UPDATE`, ≤30 recipients per fire, deduped on `announcementId`/collection id via a
unique index). `NotificationCleanupJob` runs hourly and — deliberately — only ever deletes
**read or dismissed** rows; unread rows are immortal by design (an explicit `v0.134.0` fix, not an
oversight). Retention-scale volume (hundreds of daily-eligible learners, potentially daily-cadence for
`DUE_CONCEPTS_DIGEST`) has never been exercised through this pipe.

### Dashboard/Coach — already carries current state for two of the four intents

**This is the most load-bearing finding in this audit and changes the shape of §C below.**
`frontend/app/dashboard/today-focus-card.tsx` already renders **due concepts as current state** —
a live list of concepts due for review right now, each linking straight to its source note — not a
history of past due-counts. `frontend/app/dashboard/dashboard-focus-areas-card.tsx` already renders
**weak concepts as current state** — concept name, accuracy percentage, a visual bar — sourced from
recent Challenge Quiz results. **Both intents this plan is asked to re-evaluate already have a live,
shipped, current-state Dashboard representation.** Neither is a gap needing UI work; both already avoid
exactly the "stale notification history" anti-pattern this plan's doctrine warns against, because
neither was ever built as a notification in the first place.

### Return-measurement feasibility

`users.last_login_at` exists (a single mutable column, overwritten on every login) — a real login
occurred if it falls after a given `email_log.sent_at`. **This is a coarse proxy, not a precise one:**
because the column only ever holds the *most recent* login, it can reliably answer "did this user come
back after their most recent email" but cannot cleanly attribute an older send to a later return when
multiple sends and logins interleave. No dedicated login/session-start `AnalyticsEventType` exists
(checked — no match), so a fully precise per-send attribution chain does not exist today without new
instrumentation. §D treats this as "good enough for a first-pass signal," not as a solved problem.

---

## B. Channel-role doctrine

Adopted verbatim as owner-set doctrine, binding on this plan and on future retention/communication work
until explicitly revised:

- **EMAIL = primarily re-engagement outside NoteLib.** Use email when the communication needs to reach
  a learner who may not currently be using NoteLib. Email is the only channel that can reach someone who
  has stopped opening the app — this is its structural advantage and the reason it cannot simply be
  replaced wherever a bell notification "could" theoretically cover the same fact.
- **IN-APP NOTIFICATION = awareness of meaningful changes or actionable events inside NoteLib.** Use a
  persisted notification when something meaningfully *changed* and the learner benefits from knowing
  about it while using or returning to NoteLib. A notification is for a discrete event, not a standing
  state.
- **DASHBOARD / COACH / PRODUCT STATE = "what should I learn or do now?"** Do not create historical
  notifications for information that is better represented as current product state. If current state
  supersedes old state (due concepts, weak concepts), prefer rendering current state over accumulating
  a history of notifications about past states.

A communication intent may end up using one channel, both, or neither. **Channel choice must follow the
nature of the communication, not the fact that a transport already exists for something else.**

---

## C. Intent-by-intent decision table

| Intent | Classification | Reasoning |
|---|---|---|
| **`INACTIVITY`** | **EMAIL** (unchanged) | The learner is, by definition, not currently using NoteLib — the one case only email can structurally reach. An in-app notification is invisible until they've already returned, at which point it is either redundant with Dashboard/Coach guidance or arrives too late to have caused the return. No in-app equivalent proposed. |
| **`WEEKLY_SUMMARY`** | **EMAIL** (unchanged) | A digest of the past week, opt-in by design, not an urgent in-product state change. Fits the "reach outside the app" role directly; nothing about it is better served as a standing notification or as current state (a week is already over by the time it's read). |
| **`WEAK_CONCEPT`** | **EMAIL retained** + **DASHBOARD_STATE already exists, unchanged** — no new in-app notification *recommended for this stage* | The in-product half of this intent is **already solved as standing state**: `dashboard-focus-areas-card.tsx` renders current weak-concept state today, satisfying the "what should I do now" half of the brief's own option list. Email remains the only channel reaching a learner who hasn't returned to see that card. **Two of the brief's five options were not evaluated here and are said so plainly, not foreclosed:** *Progress* — not examined, no finding either way; and *"some combination based on recency/context"* — a weak concept surfaced **minutes** after the quiz that produced it is arguably a discrete event ("this just happened") in a way a standing list is not, and that argument survives the Dashboard finding rather than being answered by it. **NEEDS_EVIDENCE applies to email effectiveness (§D) and to the recency/context question — revisit both at Stage 3 once real data exists, not decided here for lack of evidence either way.** |
| **`DUE_CONCEPTS_DIGEST`** | **EMAIL retained** + **DASHBOARD_STATE already exists, unchanged** — no new in-app notification *recommended for this stage* | Same shape as `WEAK_CONCEPT`. `today-focus-card.tsx` already renders due concepts as current state, already avoiding the stale-history problem (§4 of the owner's brief) by construction — a "21 concepts are ready to review" surface, not an accumulating "18 concepts were due" / "21 concepts were due" notification trail. **Same two unevaluated axes as `WEAK_CONCEPT`: Progress (not examined) and recency/context (a due-concept surfaced the moment a spaced-repetition interval elapses is arguably more event-shaped than the standing digest framing suggests — not evaluated, not foreclosed).** **NEEDS_EVIDENCE applies to email effectiveness (§D) and to the recency/context question, both left open for Stage 3.** |

**No intent is classified `IN_APP` or `MULTI_CHANNEL` in THIS pass.** Not because in-app is wrong in
general — the doctrine explicitly allows it — and not a closed question for `WEAK_CONCEPT` /
`DUE_CONCEPTS_DIGEST`, where two of the brief's own evaluation axes (Progress; a recency/context-gated
discrete event) are explicitly left open above rather than answered. For `INACTIVITY` and
`WEEKLY_SUMMARY` the answer is closed on structural grounds (email is the only channel that can reach
someone who's already left, and a week-in-review isn't a discrete event). This is the direct,
evidence-based answer to "should we move retention email to in-app notification" **as things stand
today**: no new in-app work now — for two intents because Dashboard already covers the in-product half
and the remaining recency/context question needs real data before it's worth deciding; for the other
two because the structural case against in-app doesn't depend on evidence at all.

---

## D. Measurement plan

Goal: determine whether existing retention emails contribute to learner return and study behavior,
without building a general analytics platform. Proposed chain, weakest signal to strongest:

1. **Delivery** — already measured. `email_log.sent_at`, unconditionally reliable, zero new work.
2. **Open** — available via Resend's `email.opened` webhook, not currently handled. **Recommend
   enabling and logging it, but treating it as directional only** given the industry-wide
   prefetch-inflation caveat (§A) — confirm with Resend's dashboard whether this sending domain shows
   the caveat's signature (an open rate implausibly close to 100%, or clustered at send time
   specifically from Apple Mail infrastructure) before trusting the raw number.
3. **Click** — available via Resend's `email.clicked` webhook, not currently handled. **This is the
   strongest of the two Resend signals and the one to weight more heavily** — it requires actually
   interacting with the email, which prefetching does not simulate. Requires the email templates' CTA
   links to be individually identifiable (per-type, ideally per-send) rather than a single shared URL,
   so a click can be attributed back to `email_type`.
4. **Return** — feasible today via `users.last_login_at` vs. `email_log.sent_at`, with the precision
   caveat in §A (coarse, not exact attribution). Sufficient for a first-pass "did sending this
   correlate with a return within N days" read; not sufficient for rigorous causal attribution.
5. **Meaningful learning action** — a completed quiz, generated pack, or practice session within a
   window after a measured return. Achievable by joining existing activity tables
   (`quick_review_sessions`, `study_packs`, etc.) against the return window from step 4 — no new schema.

**⚠️ CORRECTED — "no new infrastructure" below was wrong, found while drafting Stage 1a's Codex prompt
(2026-09-23).** `EmailService.sendEmail()` returns a bare `boolean` and discards Resend's response body
entirely; `email_log` has no column for Resend's own message id. **There is currently no way to
correlate a later open/click webhook event back to a specific `email_log` row** — Resend's webhook
payload identifies the email by Resend's own id, which this codebase never captures at send time.
Closing that gap for BOTH signals would mean changing `EmailService`'s return shape and touching every
caller (`EmailVerificationService`, `PasswordResetService`, `SubscriptionExpiryEmailService`,
`RetentionService`'s several dispatch paths, `ReEngagementCampaignService`) — a real, cross-cutting
change, not the "no new infrastructure" this section originally claimed.

**Resolution: scope per-row correlation to clicks only, for now.** Step 3 below already adds a
per-type identifiable CTA link; widening that one step to also carry the `email_log` row's own id
(`?source=due-concepts-digest&e=<email_log id>`) gives exact per-send click correlation from the
`email.clicked` webhook's own URL — zero `EmailService` changes, zero callers touched, zero new
columns. **Opens do not get per-row correlation in Stage 1a** — handle the `email.opened` webhook and
log an aggregate count, but do not claim it is attributable to a specific send. This matches what §A
already concluded about opens being the weaker, prefetch-inflated signal, so the interface-wide change
is not worth spending on the signal already documented as directional-only. **The `EmailService`
message-id plumbing is a named, deferred sub-item** — the prerequisite for per-row open correlation, if
Stage 2's click data ever makes that worth building.

**Smallest useful instrumentation, in order:**
1. Handle `email.clicked` in `ResendWebhookService` (mirroring the existing bounce/complaint/suppression
   handling shape). Correlate to a specific `email_log` row via the `e=<id>` URL parameter on the
   clicked link — **do not guess Resend's exact payload field name for the URL** (verify against
   Resend's own webhook documentation, not assumed). Persist to `email_log` or a small companion table —
   still no general event-tracking system, just enough to answer step 3 below.
2. Handle `email.opened` too, but only as an aggregate count (e.g. a counter, or unattributed rows in a
   companion table) — explicitly not correlated to a specific `email_log` row in this stage.
3. Confirm/enable open and click tracking in the Resend dashboard for this sending domain (owner action,
   not code — Resend account configuration, not something this session can read or set).
4. Make each retention email's CTA link identifiable by type **and carry the sending row's own id**.
   **`DUE_CONCEPTS_DIGEST` already has the type param** (`?source=due-concepts-digest`, shipped
   `v0.72.0`, per `docs/features/retention-emails.md`) — every active type needs the row-id parameter
   added, and the three without a type param yet (`INACTIVITY`, `WEAK_CONCEPT`, `WEEKLY_SUMMARY`) need
   both. **The correlation token must be inert** — a read-only analytics marker, never something that
   grants access or identifies more than which send it was; it appears in a URL that's necessarily
   readable by anyone who receives the email. Template-by-template work, not a single shared change.
5. Once a few weeks of click data exist, run the `last_login_at`/activity join described in steps 4–5 as
   a one-off read-only query, not a new scheduled report.

**Explicitly not proposed:** a general analytics/attribution platform, a new event-sourcing system,
per-send unique tracking pixels beyond what Resend already provides, or the `EmailService` message-id
plumbing needed for per-row open correlation (named above as a deferred sub-item, not built now).

---

## E. Preference semantics

**Unchanged in this stage, and explicitly documented as unchanged so a later session doesn't
"helpfully" reinterpret them.** All four toggles in §A's table mean exactly what their copy says today:
"send me this by email." None becomes a universal communication preference. Concretely:

- `inactivityRemindersEnabled = false` means "do not email me this" — it does **not** suppress Dashboard
  Today Focus content, which isn't gated by this flag today and shouldn't become gated by it as a side
  effect of this plan.
- `dueConceptsDigestRemindersEnabled = false` likewise does not and should not suppress the Today Focus
  due-concepts display — that display is not email, was never described as email in its own copy
  ("Choose which optional *emails* you get"), and this plan does not touch it.
- **No new Notification Preferences system is introduced.** If a future stage ever does classify an
  intent as `IN_APP` or `MULTI_CHANNEL`, that is the point at which a channel-specific preference
  question becomes real — flagged here as a future consideration, not designed now.

---

## F. Cross-channel deduplication

**Not needed now.** No intent in §C is classified for delivery through more than one channel, so there
is no dual-send to deduplicate against. If a future stage classifies something `MULTI_CHANNEL`, a dedup
key at that time can very likely extend the existing pattern already proven for in-app notifications
(`dedup_key` on the `notifications` table) rather than needing new machinery — but that design is
deferred until an actual `MULTI_CHANNEL` case exists, per the same reasoning as §H below: don't build
for a case that isn't approved yet.

---

## G. Email budget

**Real, separate from the channel-doctrine decision, and should ship as its own implementation slice.**
The smallest correction that makes the stated 100/day limit actually mean what it says: (a) make every
active retention dispatch path consult `resolveReengagementBudget()` before sending, not just
`INACTIVITY`'s; (b) if some types are judged higher-priority than others, define an explicit per-type
or priority-ordered allocation within the shared 100/day pool rather than the current implicit
"whoever's job ran first this happened not to collide" behavior; or **(c) — worth naming because §A's
finding makes it newly relevant — narrow the count itself to retention email types
(`WHERE email_type IN (...)`) rather than the current unscoped count across every writer to
`email_log`, including `EmailVerificationService`, `PasswordResetService`, and
`SubscriptionExpiryEmailService`.** As it stands, a spike in password resets or subscription-expiry
mail eats into the same 100/day budget `INACTIVITY` is enforced against, with only
`transactionalReserve` standing between the two classes of mail — which is presumably what that reserve
exists for.

**DECIDED 2026-09-23 — (c) then (a), together, not a three-way pick.** (a) and (b) were framed above as
alternatives to (c); they are not. (c) is the correction that makes (a) safe, not a competitor to it.
The load-bearing fact: transactional mail (`EmailVerificationService`, `PasswordResetService`,
`SubscriptionExpiryEmailService`) already sends unconditionally today, and should — a user resetting
their password must never be throttled by a marketing-email budget. That mail was never meant to be a
budget participant; it has only ever been polluting the count `INACTIVITY` checks against. So the fix
is: (c) first — narrow `countEmailsSentToday()` to `WHERE email_type IN (...)` the five active
retention types (`INACTIVITY`, `WEAK_CONCEPT`, `WEEKLY_SUMMARY`, `DUE_CONCEPTS_DIGEST`,
`KNOWLEDGE_IMPACT_DIGEST`) — named as an explicit list, not an exclusion rule, since the enum has 13
values and §A already found two more (`UNFINISHED_NOTE`, `WELCOME`) whose classification is unresolved;
an exclusion rule would silently absorb any future addition without a decision. Then (a) — every one of
those five dispatch paths consults `resolveReengagementBudget()` before sending, not just `INACTIVITY`'s.
**(b) is rejected**, not deferred: there is no evidence in this audit that any retention type needs
priority over another under contention, and building an allocation scheme without that evidence is
exactly the kind of speculative infrastructure this repo's own conventions reject.

`transactionalReserve` **keeps its current value and meaning, unchanged** — once the count is narrowed
to retention types, transactional mail is no longer counted at all, so the reserve stops being a literal
reservation and becomes a safety margin against transactional volume the budget no longer sees directly.
That is still worth keeping as-is; it is not now-redundant plumbing to "clean up."

**Stated consequence, not just a mechanism change:** narrowing the count (c) *raises* the effective
retention budget — today's count is inflated by transactional volume, so retention mail is being
throttled earlier than the stated 100/day intends. Extending enforcement to four previously-unbudgeted
types (a) pulls the other way. Net daily retention-email volume can move in either direction; the
release note for this must say so rather than implying this is purely a tightening. **Do not solve this
by moving messages to in-app to reduce Resend volume** — channel choice follows communication purpose
(§B), not cost avoidance; if the budget needs raising instead of enforcing, that's a legitimate
alternative fix, also out of scope for this document to pick.

---

## H. In-app notification capacity

**Not audited in depth here, deliberately.** §C's conclusion is that zero retention intents move to
in-app in this pass, so a capacity audit (persistence volume, polling load, unread-count correctness,
indexing, cleanup interaction, duplicate prevention, fan-out behavior, mobile rendering, staleness,
accumulation) would be auditing infrastructure for a use case this plan does not recommend building.
**Revisit if and when Stage 3 (§I) or a later re-evaluation actually classifies a retention intent as
`IN_APP` or `MULTI_CHANNEL`** — at that point, audit against the specific intent's real volume and
cadence, not speculatively.

---

## I. Implementation recommendation — staged, not implemented here

**Stage 1a — Instrumentation. Scope-ready now, no blocking owner decision.**
- Handle `email.opened`/`email.clicked` Resend webhooks (§D).
- Make CTA links per-type identifiable — `DUE_CONCEPTS_DIGEST` already has this, the other active types
  don't (§D step 3).
- Routing: Codex (new webhook handling, a schema addition for open/click persistence, per-type template
  edits) — this is genuine backend work, not a doc change.

**Stage 1b — Email budget governance. Decided 2026-09-23 (§G) — scope-ready, no longer blocked.**
- Narrow `countEmailsSentToday()` to the five active retention types (option c), then make every one of
  those five dispatch paths consult `resolveReengagementBudget()` before sending (option a).
  `transactionalReserve` stays unchanged. Option b rejected — no evidence any type needs priority over
  another (§G).
- Can ship before, after, or alongside Stage 1a — the two slices don't depend on each other's code.
- Routing: Codex (touches the shared dispatch path `RetentionService` already owns).

**Stage 2 — Collect evidence. No code; a waiting/reading period.**
- Let Stage 1a's instrumentation run long enough to produce a real open/click/return sample per email
  type. No fixed duration recommended here — tie it to a minimum sample size per type (e.g. enough
  `INACTIVITY` sends to read a stable click-through rate) rather than a calendar guess, and state that
  bound explicitly when Stage 1a ships, the same discipline this repo's other checkpoints already use
  for small-denominator reads. Independent of whether Stage 1b has shipped yet.

**Stage 3 — Revisit intents against doctrine + measured data.**
- Re-open §C's table with real numbers. The two intents already resolved to "no in-app version, ever"
  (`WEAK_CONCEPT`, `DUE_CONCEPTS_DIGEST` — because Dashboard already covers the in-product half) don't
  need re-opening on THAT axis, but the email-effectiveness question for all four types gets answered
  for the first time.
- Only at this stage does §H's in-app capacity audit become relevant, and only for whatever this stage
  actually proposes moving.

This sequencing matches the owner's own expected direction. Nothing found in this audit argues for a
different order.

---

## Explicitly out of scope (per owner instruction, not an oversight)

**Async background-work-completion notifications** (e.g. a Study Pack finishing generation after the
learner has left the page) are a plausible, doctrine-aligned use of the in-app channel — but are
explicitly **not** part of this plan and must not be folded in here. To be audited separately.

---

## RETENTION COMMUNICATION DECISION

**Overall strategy:** Adopt the three-channel doctrine in §B. Do not move any existing retention email
to in-app notification now. Instrument email effectiveness first; let the two intents whose in-product
half is already solved by Dashboard stay solved; revisit with real data once it exists.

**INACTIVITY:** Email (unchanged). No in-app equivalent — cannot reach a learner who's already left.

**WEAK_CONCEPT:** Email (unchanged) for outside-app reach. In-product state already live via
`dashboard-focus-areas-card.tsx` — no new work, no bell notification.

**WEEKLY_SUMMARY:** Email (unchanged). Digest/re-engagement communication; no in-product state or
notification equivalent proposed.

**DUE_CONCEPTS_DIGEST:** Email (unchanged) for outside-app reach. In-product state already live via
`today-focus-card.tsx` — no new work, no bell notification, no accumulating history.

**Existing emails removed now:** NO — none.

**New in-app retention notifications now:** NO — none.

**Dashboard/Coach changes recommended:** None required by this plan — both relevant current-state
surfaces already exist and already meet the doctrine's bar.

**Email measurement required:** YES — open/click via Resend webhooks (directional on open, weighted
more on click), return via `last_login_at` join (coarse), meaningful-action via existing activity
tables. See §D.

**Primary success signal:** Click-through leading to a measured return and a subsequent learning
action — not raw open rate, and not raw send volume.

**Email budget change:** YES, real and separate from the channel decision — DECIDED 2026-09-23: narrow
the budget count to retention email types only (c), then extend enforcement to all five active retention
dispatch paths (a). See §G.

**Preference changes:** None. Existing four toggles keep their current, email-scoped meaning exactly as
worded today.

**Cross-channel dedup architecture needed now:** NO.

**Notification infrastructure changes needed now:** NO — deferred until a future stage actually
proposes an `IN_APP` or `MULTI_CHANNEL` intent.

**Implementation stages:** Stage 1a (instrumentation, Codex-routed, scope-ready now) + Stage 1b (budget
fix, Codex-routed, DECIDED 2026-09-23 — scope-ready, independent of 1a, may ship before/after/alongside
it) → Stage 2 (evidence collection, no code, sample-size-bound not calendar-bound) → Stage 3 (revisit
with data).

**OWNER DECISIONS STILL REQUIRED:**
1. ~~§G — budget governance shape~~ **DECIDED 2026-09-23: (c) then (a), together — see §G.** Stage 1b
   is unblocked.
2. **§D's sample-size bound for Stage 2:** what minimum per-type sample (or calendar floor as a
   backstop) should gate reading the results, so Stage 3 isn't scoped on an underpowered read? Not
   decided here — flag as the exact kind of small-denominator question this repo's own checkpoint
   discipline requires stating before the read, not after.

**Stage 1a and Stage 1b are both scope-ready. Stage 2/3 remain not-implemented pending Stage 1
evidence.**
