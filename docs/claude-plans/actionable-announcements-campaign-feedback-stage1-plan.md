# Actionable Announcements + Campaign Feedback — Stage 1 Audit & Implementation Plan

**Status:** Stage 1 — audit and plan only. **NOTHING IMPLEMENTED.** No Codex prompt written (that is a later pass, after owner approval).
**Written:** 2026-09-21.
**Feature A** of the communication initiative. **Feature B (user communication & notifications architecture) is explicitly out of scope** and is already partly planned elsewhere — see §L.

**Version targeting:** `v0.155.0` (*Say What You Checked*) is **In Progress** on `releases/v0.155.0` as of this writing. This work targets **the release after it**; the number is deliberately not guessed here. Nothing in this plan may be committed onto the `v0.155.0` branch.

**Every claim below was checked against current source.** Where the owner specification asserted something that turned out to be false, it is corrected explicitly and marked **⚠️ SPEC PREMISE CORRECTED**.

---

## Production reads performed for this audit

All read-only `SELECT`s against the Render production instance (`dpg-d6tvb8fkijhs73fda4m0-a`), 2026-09-21. Per this repo's own rule that *a claim about production state is a snapshot, not a fact*, these were re-read rather than taken from any doc.

| Metric | Value |
|---|---|
| Active users (`users.status = 'ACTIVE'`) | **405** |
| `announcements` rows, all time | **0** |
| `announcements` rows `PUBLISHED` | **0** |
| `notifications` rows, all time | **57** |
| …of type `ANNOUNCEMENT` | **0** |
| …of type `REVIEW_SET_UPDATE` | **57** (57 distinct recipients, delivered 2026-09-10 → 2026-09-14) |
| …with `read_at` set | **1** |
| …with `dismissed_at` set | **0** |
| …carrying both `cta_path` **and** `cta_label` | **57 / 57** |
| `feedback` rows, all time | **3** |
| `feedback` rows in last 90 days | **2** |
| `feedback_image` rows | **0** |
| Highest applied migration (`flyway_schema_history`) | **V147** (`program_family_initial_membership`, 2026-09-17) |

Highest migration in the repo is also **V147**, so local and production are in sync and the next free number is **V148**. **Confirmed free as of this plan**: `v0.155.0` (currently in flight, `releases/v0.155.0`) adds no migration of its own — its diff against `main` touches zero files under `backend/src/main/resources/db/migration/`. Still worth a one-line re-check immediately before writing V148's file, since a second in-flight release could claim it between now and implementation, but this is not an open question requiring owner input.

**Four of these numbers change the shape of this feature and are used throughout:**

1. **`announcements` has never had a row in production.** Admin → What's New has never been used against live users.
2. **`ctaLabel` is populated on 57 of 57 delivered rows and rendered on none of them.**
3. **1 of 57 delivered notifications has ever been read (1.8%), and 0 dismissed — and this is a click-through floor, not an engagement rate (see A3).**
4. **3 feedback rows exist in total; two say literally `Test` and `Testing`.** Exactly **one** is a genuine learner submission — the 2026-09-16 Quick Review report that became the `v0.155.0` correctness incident.

---

## A. Executive verdict

**The product direction is CONFIRMED in full. All five locks hold:**

| Lock | Verdict |
|---|---|
| Keep permanent user-initiated Feedback separate | ✅ **CONFIRM** |
| Improve linked announcements generically | ✅ **CONFIRM** — and it is far cheaper than the spec assumes |
| Concise notification invitation → lightweight campaign `/feedback` | ✅ **CONFIRM** |
| Do not build a survey platform | ✅ **CONFIRM** |
| Do not build `/notifications` yet | ✅ **CONFIRM** |

**Three evidence-based modifications, none of which changes the direction:**

### A1. The single highest-value fix is smaller than the spec thinks — `ctaLabel` is dead data, end to end

`AnnouncementEntity.ctaLabel` (`AnnouncementEntity.java:32-33`) is authored in Admin, validated at the API boundary (`UpsertAnnouncementRequest.java:24-25`), copied onto every notification row at fan-out (`NotificationEntity.java:44-45`), and returned to the client in `NotificationResponse` (`NotificationResponse.java:12`).

**`frontend/components/notifications/notification-inbox.tsx` never reads it.** The rendered body (`:190-210`) is title + body + timestamp. There is no CTA element of any kind. 57 production rows carry a `cta_label` nobody has ever seen.

**So §4 of the spec — "expose the configured action clearly" — is a render-only change.** No migration, no API change, no DTO change, no admin change. That is the cheapest high-value item in this entire plan and it should ship first, independently.

### A2. ⚠️ SPEC PREMISE CORRECTED — §3's ask is ALREADY SHIPPED, and the tested long announcement was never in production

The spec asks for whole-card navigation as if it were new. It is not:

- `notification-inbox.tsx:220-231` — when `toSafeRelativePath(notification.ctaPath)` returns a safe path, the **body is a real `next/link` `<Link>`** whose `onClick` calls `markRead(notification)` **and** `setIsOpen(false)`, then navigates. Mark-read → close → navigate, exactly the sequence §3 specifies.
- `notification-inbox.tsx:242-249` — the dismiss `X` is a **sibling `<button>` outside the Link's hit area**. It cannot navigate. §6's "accidental navigation when dismissing" is already impossible.
- `notifications.md:121-126` documents this as settled doctrine: *"The notification body is the primary activation target… There is no separate CTA link or Mark read button."*
- 28 tests in `notification-inbox.test.tsx` already pin it, including `"uses the destination card body as the only CTA and marks read before closing"` (`:382`), `"keeps dismiss separate from reading and navigation"` (`:409`), and `"uses a keyboard-reachable native link for destination card activation"` (`:441`).

**One genuine (small) gap remains:** the tappable object is the body `<Link>`, not the `<article>`. The article's `px-4 py-3` padding (`:216`) is outside the hit area. Worth closing; it is a class change, not a redesign.

**And the tested announcement never reached production.** `announcements` has 0 rows, ever. The "wall of text" the owner observed was a local/staging test. **This does not invalidate the problem** — the 1000-character body column is real, the absence of any clamp is real, and the problem would occur on first production use. It does mean there is **no production data to fix and no user has seen it**, which lowers the urgency and removes any migration/backfill concern.

### A3. ⚠️ THE REACH PROBLEM IS THE REAL RISK, AND THE SPEC DOES NOT MENTION IT

This is the finding the owner most needs before approving.

**Announcements can never produce a bell badge.** `notifications.md:48` — `ANNOUNCEMENT` → category `ANNOUNCEMENT(false)`, `badgeEligible = false`. Deliberate, and for a good reason (`:56-60`): an announcement nobody acts on would leave a permanent count on the bell. So the campaign invitation is visible **only to learners who voluntarily open the bell**.

**The one measurement we have is not encouraging — and it is also not an engagement rate.** `REVIEW_SET_UPDATE` **is** badge-eligible. 57 rows were delivered to 57 distinct learners between 2026-09-10 and 2026-09-14. **One has been read. Zero dismissed.**

**⚠️ 1.8% is a click-through floor, not an impression rate, and the two are not interchangeable.** `read_at` is set **only on explicit body activation** — the panel never marks-all-read on open (`notifications.md:171-184`, confirmed at B.1). There is no inbox-open or row-impression event anywhere in the codebase (checked: no analytics event type, no logged panel-open, nothing on `notification-inbox.tsx` records a view). So "1 read" measures *how many learners clicked through*, not *how many saw the row and passed on it*. A learner who opened the bell, read the title, and closed it without clicking is indistinguishable in this data from a learner who never opened the bell at all. **The true "how many people saw this" number could be materially higher than 1.8% — there is no way to know from data that exists today.**

This makes 1.8% a **conservative floor on click-through, with no impression denominator** — worse framing for planning purposes than an honest "unknown," because it invites reading it as a full engagement rate when it structurally cannot measure one. If that click-through floor carries, an `EVERYONE` announcement to 405 active users yields a **single-digit** number of campaign responses; if the true view-through rate is higher (plausible, unmeasured), responses could be somewhat higher too. Either way the order of magnitude — single-to-low-double-digit — is the actionable takeaway, and §Q's owner decision #1 should be read as "confirm we're comfortable building for that order of magnitude," not as a precise projection.

**Owner decision (§Q.1, 2026-09-22): SHIP AS PLANNED.** Approved on this reasoning, not the "infinitely more than zero" framing an earlier pass used:

- **Implementation cost is relatively low.** The campaign is one screen, one table, one endpoint pair — the generic Slice 1 fixes are the expensive-relative-to-value item's actual cost driver, and those ship regardless of campaign volume.
- **Structured qualitative feedback has high information value at NoteLib's current scale.** At 405 active users and ~1 genuine unsolicited feedback row in the product's entire history, even a handful of structured, categorized responses is a meaningfully denser signal than what exists today — the value case does not depend on a large N.
- **The generic actionable-announcement improvements remain valuable on their own even if campaign response volume is small** — they are cheap, they fix real defects (A1's dead `ctaLabel`, the density/clamping gap), and they are prerequisites for *any* future announcement being usable, independent of this specific campaign's outcome.
- Explicitly ruled out per owner instruction: do not make `ANNOUNCEMENT` badge-eligible for this feature, do not add notification impression/open analytics to inflate the denominator, do not pull email/Resend into Feature A, do not unblock or modify Feature B/Stage E.
- **Do not size the admin review experience for a response volume that the evidence does not support** — this is what drives §I to outcome D.
- **Do NOT "fix" reach by making `ANNOUNCEMENT` badge-eligible.** That resurrects the exact defect `v0.134.0` removed (`notifications.md:340-351`: an `EVERYONE` announcement left 396 permanent immortal rows). The evidence also says it would not obviously help — the badge-eligible type's click-through floor was already only 1.8%, and there's no reason to believe a badge alone converts unseen impressions into reads.
- **Real reach requires email, which is Stage E and is BLOCKED** (`attention-notifications-email-expansion-stage1.md:1041-1043`: blocked on a global cross-channel daily budget; the configured 100/day cap is *already breached* at 156–159/day observed). That is Feature B. Surfaced as an owner decision in §Q, not smuggled in here.

**Net verdict: proceed, with a documented reach expectation and a checkpoint.**

---

## B. Current architecture map

### B.1 Admin What's New → announcement → notification → bell

```
frontend/app/admin/announcements/page.tsx  (471 LOC, hand-rolled header + back link)
   │  form fields: Title(255) · Body(1000) · "Link label"(64) · "Link path"(512)
   │               · Audience · {Audience} value · Expires (optional)
   │  NO preview of the resulting notification card.   NO body character counter.
   ▼  lib/api.ts:3148 createAnnouncement / :3161 updateAnnouncement / :3177 publishAnnouncement
AdminAnnouncementController        (/admin/announcements, class-level @PreAuthorize("hasRole('ADMIN')") :32-34)
   ▼
AnnouncementService
   ├ create()  :87-96    → always DRAFT
   ├ update()  :103-111  → DRAFTS ONLY; else AnnouncementNotEditableException (409)
   ├ publish() :139-159  → refuses ENDED; stamps PUBLISHED/published_at once;
   │                       resolves audience SYNCHRONOUSLY at call time (:152);
   │                       queues fan-out (:153).  ⚠️ deliberately NOT @Transactional (:134-137)
   ├ end()     :179-188  → PUBLISHED only; delivered rows are NOT deleted
   └ ctaPath validated on write via AnnouncementCtaPathValidator (:255)
   ▼  notificationFanOutExecutor (AppConfig.java:65-76, core 1 / max 2, bounded, AbortPolicy)
AnnouncementService.fanOut() :201-225
   └ chunks of 500; ONE INSERT PER RECIPIENT, never bulk (:48-53 — a bulk insert
     would fail the whole chunk on the first duplicate)
   ▼
NotificationService.deliver()
   ├ re-validates ctaPath (:46) — second chokepoint, added after a v0.130.0 pressure test
   ├ dedupKey = "ANNOUNCEMENT:<announcementId>"  (:112)
   └ inserts; catches DataIntegrityViolationException → returns existing row.
     ⚠️ NO existsBy pre-check. The UNIQUE (recipient_user_id, dedup_key) index IS the guarantee.
   ▼  notifications table (V139) — title/body/ctaLabel/ctaPath COPIED, never re-read
NotificationController  →  lib/api.ts:2746 listNotifications / :2758 unreadCount
                                        / :2781 markRead / :2794 dismiss
   ▼
frontend/components/notifications/notification-inbox.tsx   ← mounted in app-shell header
   ├ desktop: absolute dropdown  (:277-284, w-96, max-h-[32rem])
   ├ mobile:  AppModal variant="sheet"  (:286-295)
   ├ body is a <Link> when ctaPath is safe (:220-231) → markRead + close + navigate
   ├ body is a <button> when not (:232-241)          → markRead only, panel stays open
   ├ dismiss X is a SIBLING button (:242-249)        → never navigates, never marks read
   ├ ⚠️ ctaLabel is NEVER rendered
   └ ⚠️ body has NO line-clamp  (:207)
```

**Read/dismiss/navigation semantics** (`notifications.md:171-184`): `read_at` is awareness, `dismissed_at` is inbox visibility, both idempotent, fully independent. Rows are marked read **individually, only on explicit body activation**; the panel **never** marks-all-read on open.

**Visibility/expiry is decided ON READ** (`notifications.md:281-292`): `NotificationRepository.findVisibleInbox` excludes rows whose announcement is `ENDED` or past `expires_at`, as a correlated subquery. No `@Scheduled` job. The predicate is **NOT-EXISTS-ended, not EXISTS-live**, so a missing announcement leaves the delivered row visible.

### B.2 Existing Send Feedback → persistence → admin review

```
Trigger surfaces (components/feedback/send-feedback-widget.tsx)
   ├ variant "icon"     → app-shell.tsx:648  (the ONLY global trigger; 1 call site)
   ├ variant "inline"   → 4 contextual prompt components
   │    welcome-back-feedback-prompt · public-library-discovery-feedback-prompt
   │    study-pack-generated-feedback-prompt · quiz-feedback-panel
   └ variant "floating" → ZERO production call sites (test-only)
   ▼  AppModal (default variant + hand-rolled sheet classes, :290)
lib/api.ts:3216 submitFeedback(request, pageUrl)
   ├ POST /feedback, JSON body = { message } only
   ├ page URL travels as the X-Page-Url REQUEST HEADER (:3221-3223)
   └ new id returned in the X-Feedback-Id RESPONSE header (:3237), not the body
   ▼
FeedbackController (/feedback, @PreAuthorize("hasAnyRole('USER','ADMIN')"))
   ├ GET  /feedback/context          :33-39
   ├ POST /feedback                  :41-56
   └ POST /feedback/{id}/image       :58-67  (separate call, multipart)
   ▼
FeedbackService.submitFeedback :70-95
   ├ ⚠️ NO rate limit, NO cap, NO dedup — every POST inserts a row and emails support
   ├ status hardcoded NEW (:86); REVIEWED/CLOSED are never written by any code
   └ support email sent unconditionally BEFORE any image exists (:90-93)
   ▼
feedback table (V31): id · user_id · email · message(text) · page_url(text) · status · created_at
feedback_image (V95): 1:1 side table, bytea, ≤2MB, PNG/JPEG/WebP, magic-byte checked

Admin review:
   ⚠️ THERE IS NO ADMIN FEEDBACK LIST ENDPOINT.
   AdminFeedbackController exposes exactly ONE handler: GET /admin/feedback/{id}/image.
   The only way an admin reads feedback text is AdminDashboardService.getRecentEvents()
   → findAllByOrderByCreatedAtDesc(PageRequest.of(0, RECENT_EVENT_LIMIT)) with
     RECENT_EVENT_LIMIT = 5 (:55, :210-211).  Hard-capped at the 5 newest. No pagination anywhere.
```

**The structural gap, confirmed against the one real production row.** `FeedbackEntity` has no structured context columns. Quiz type, context and Note title are a newline-prefixed preamble *inside* `message`. The single genuine production row reads:

```
Feedback type: Quiz Feedback | Quiz: Quick Review | Context: Quiz Results | Note: Fraction…
```

…with `note_id` and `session_id` recoverable only from `page_url`'s path/query. This is exactly what `docs/claude-findings/2026-09-19-…-incident.md` §R records — and §R itself says these improvements are **"not the Campaign Feedback feature"** and **"should be scoped separately"**. They are therefore **out of scope here** (§L), but they are the direct reason §G does not extend this table.

---

## C. Screenshot / current UX diagnosis

The long-announcement failure decomposes into **four generic system problems and two campaign-copy problems**. Only the generic ones are defects; the copy ones are answered by the invitation/interaction split.

### Generic system problems (fix these; they recur for every future announcement)

| # | Problem | Evidence | Severity |
|---|---|---|---|
| **G1** | **Body is rendered unclamped.** `notification-inbox.tsx:207` renders `{notification.body}` in a bare `<span className="mt-1 block text-sm text-muted-foreground">`. The column allows **1000 characters** (`V140`, `UpsertAnnouncementRequest.java:20-21`). A 1000-char body renders in full inside a `w-96` desktop dropdown capped at `max-h-[32rem]` — one announcement can fill and scroll the entire inbox. | `:207`, `:278` | **High** |
| **G2** | **The configured action is invisible.** `ctaLabel` is stored, copied, transmitted and never rendered. The card gives no indication it is tappable or where it leads. The only affordance is a `hover:bg-highlight` (`:211`), which does not exist on touch. | `:190-211` vs `NotificationResponse.java:12` | **High** |
| **G3** | **No authoring feedback in Admin.** The body textarea (`app/admin/announcements/page.tsx:261-270`) is `rows={3}` with a bare `maxLength={1000}`, **no counter and no helper text** — while the sibling `ctaPath` and `expiresAt` fields both have helpers (`:293-295`, `:342-344`). There is **no preview** of the resulting card anywhere in the form; the closest thing is a post-save list row printing `{ctaLabel} → {ctaPath}` (`:380-384`). The curator cannot see what they are about to ship. | `:261-270` | **Medium** |
| **G4** | **Hit area stops short of the card.** The `<article>` carries `px-4 py-3` (`:216`) but the `<Link>` is an inner sibling of the dismiss button inside a `flex` row (`:219`), so the card's padding is not tappable. Minor on desktop, more noticeable at 390px. | `:213-251` | **Low** |

### Campaign-copy problems (not system defects — answered by the invitation/interaction split)

| # | Problem |
|---|---|
| **C1** | **The hypotheses were in the announcement.** Nine framings of what might be wrong, in a surface that is an inbox line. The spec's own lock resolves it: *notification = invitation, destination = interaction.* |
| **C2** | **Priming.** Listing hypotheses in the invitation biases the response before the learner reaches the instrument. Moving them to `/feedback` does not remove priming (they are still options), but it removes it from the *invitation*, so a learner who declines is not primed, and a learner who accepts meets the options once rather than twice. |

**G1–G4 are all in scope as generic work. C1/C2 are resolved by design, not by code.**

---

## D. Generic linked-announcement UX

Applies to **every** destination-bearing notification, present and future. **No feedback-specific assumption may enter this layer** (§26).

### D.1 Card behavior

- **Keep the existing model** — the body `<Link>` is the activation target. Already shipped, already tested, already documented doctrine. Do not redesign.
- **Close G4:** move the row padding onto the `<Link>` and the dismiss button rather than the `<article>`, so the full card width minus the dismiss target is tappable. Pure class movement; no structural change.
- **A card with no destination keeps today's behavior** — `<button>`, marks read, panel stays open, **no CTA affordance rendered**.

### D.2 CTA behavior — the one trap that will produce a defect

**⚠️ THE CTA MUST NOT BE A LINK OR A BUTTON.** The body is already an `<a>`. Putting an `<a>` (or `<Link>`) inside it is **nested interactive content** — invalid HTML, undefined activation behavior, and precisely the *"competing nested interaction model"* §3 forbids.

**Render the CTA as a non-interactive `<span>` inside the existing `<Link>`:**

```
<span className="mt-2 inline-flex items-center gap-1 text-sm font-medium text-blue-600 dark:text-blue-400">
  {notification.ctaLabel} <span aria-hidden="true">→</span>
</span>
```

- Rendered **only** when `ctaPath` is safe **and** `ctaLabel` is non-blank. `ctaLabel`/`ctaPath` are already **both-or-neither** server-side (`AnnouncementService.java:264-283`), but the client must still tolerate a label without a path (render nothing) — a stored row is untrusted at render time, the same principle `:184-187` already applies to the path.
- **Blue, not a filled button.** `ui-standards.md:98-104` — *"Always use blue, never muted gray"* for secondary navigation links; `text-blue-600 dark:text-blue-400` is the established token, used by `BackLink` and every "View all" link. A filled primary button in every inbox row would fight the dismiss control and overweight a list surface. This directly answers §4's *"sufficient visual affordance without becoming a giant primary button."*
- The `→` is decorative and **must** carry `aria-hidden="true"` (the repo's existing convention, e.g. `Checkbox.tsx:35`).

### D.3 Accessible name — required, and easy to miss

The `<Link>` currently takes its accessible name from `aria-labelledby={titleId}` alone (`:222`). Once a **visible** CTA label exists inside the link, that violates **WCAG 2.5.3 Label in Name**, which `ui-standards.md:169` states as a hard rule: *"The accessible name must contain the visible label."*

**Fix:** give the CTA span an id and extend the reference — `aria-labelledby={ctaLabel ? `${titleId} ${ctaId}` : titleId}`. The link then announces *"Help us improve NoteLib, Share feedback"*, which contains both visible strings.

### D.4 Read behavior

**Unchanged.** Activating the body marks that row read, individually, optimistically, with rollback on failure (`:137-157`), and decrements the badge only when the server-provided `actionable` field is true (`:146`). **Do not touch the badge logic** — `notifications.md:185-197` documents that branching on `type !== "ANNOUNCEMENT"` was a defect fixed in `v0.134.0`, and a test at `:340` pins the disagreeing-fixture case.

### D.5 Dismiss behavior

**Unchanged.** Sibling button, outside the Link, `aria-label={`Dismiss ${notification.title}`}`, removes the row optimistically with rollback (`:159-167`). It must never navigate, never mark read, and never alter product state (`notifications.md:15-16`).

**One accessibility improvement in scope:** the dismiss button is `h-8 w-8` (32px, `:244`) — below the 44px minimum. The repo already has the fix pattern: `catalog-multi-select.tsx` uses `min-h-11 min-w-11` on its chip remove button. Apply the same.

### D.6 Density / clamp behavior — §5 answer is **C (both)**

**Display clamp.** `line-clamp-3` on the body span. `line-clamp` is an established convention — **21 usages**, no shared abstraction, `line-clamp-2` dominant for titles/descriptions and `line-clamp-3` for body/preview blocks (`shared-note-card.tsx:159`, `collection-detail-page-client.tsx:509`). Three lines matches the spec's "2–3 visible body lines" and the existing body-block precedent.

**Authoring guidance.** Add a live character counter plus helper text under the Admin body textarea, following the existing hand-rolled counter at `send-feedback-widget.tsx:340-352` (`aria-live="polite"`, `tabular-nums`, three-tier color). **Recommend a soft target of ~160 characters** — roughly three lines at `text-sm` in a `w-96` panel — surfaced as guidance, **not** as a new hard limit. Do **not** reduce the 1000-char column or the `@Size(max = 1000)` validator: that would be a breaking API tightening (CLAUDE.md's *"making-required an existing API form is breaking in both directions"*), and with 0 announcement rows there is nothing to migrate but also nothing forcing the change.

**Document the stored-vs-displayed split explicitly** in `notifications.md`, in the vocabulary that file already uses for copied content: **the full body is stored and delivered; the inbox displays a clamped view.** There is no "Read more" and **no announcement detail page** (§5, §28).

**⚠️ `line-clamp-3` needs `min-w-0` on the flex child to actually clamp.** The body `<Link>` already carries `min-w-0 flex-1` (`:211`), so this works — but it is load-bearing and must not be removed.

### D.7 Mobile behavior

The desktop dropdown (`:277-284`) and the mobile `AppModal variant="sheet"` (`:286-295`) render the **same `rows` block** (`:169-255`), so every change above lands on both automatically. That is the existing design and it should be preserved.

`AppModal variant="sheet"` gives: `items-end` bottom-sheet at `<sm`, `px-0` edge-to-edge backdrop, `max-h-[85dvh]`, `rounded-t-2xl`, promoting to a centered `sm:max-w-[420px]` dialog above `sm` (`app-modal.tsx:160,163`). The inbox uses the **real** sheet variant — note that `send-feedback-widget.tsx:290` does **not** (it uses the default variant with hand-rolled sheet classes, so it is not edge-to-edge). **Do not copy the widget's approach; the inbox is already correct.**

Required at 390px: `line-clamp-3` (D.6) is what makes the card bounded; the CTA span wraps naturally within the clamped block; dismiss reaches 44px (D.5). No horizontal scroll — the row is `flex items-start gap-3` with a `min-w-0 flex-1` body, which is already correct.

### D.8 Accessibility behavior

| Concern | State | Action |
|---|---|---|
| Nested interactive elements | Would break with a link/button CTA | **Span only** (D.2) |
| Label in Name (WCAG 2.5.3) | Breaks once CTA is visible | Extend `aria-labelledby` (D.3) |
| Keyboard | ✅ Native `<Link>`/`<button>`, already pinned by `notification-inbox.test.tsx:441` | None |
| Focus ring | ✅ `focus-visible:ring-2 ring-blue-600 ring-offset-2` (`:211`) | None |
| Trigger state | ✅ Stable `aria-label` + `aria-expanded` (`:265-266`), per `notifications.md:142-144` | None |
| Escape / outside click | ✅ Desktop handler gated `!isMobile` (`:88-111`); mobile handled by AppModal | None |
| Focus trap / restore | ✅ `app-modal.tsx:87-150` | None |
| Touch target | ❌ dismiss is 32px | `min-h-11 min-w-11` (D.5) |
| Decorative arrow | New | `aria-hidden="true"` |
| Badge at zero | ✅ No element at all (`:270`), test-pinned at `:58` | None |

---

## E. Campaign feedback UX — complete interaction

**Route: `/feedback`.** Confirmed available — `frontend/app/` has 36 top-level route directories and **none is `feedback`**. The only `"/feedback"` string in the frontend is `lib/api.ts:3226`, the **API** path. No dangling links.

**`/feedback` passes both CTA validators** — verified, so no validator may be "fixed" to accommodate it:
- Backend `AnnouncementCtaPathValidator.java:30` — `^/[A-Za-z0-9\-._~/]*$` → `/feedback` matches.
- Frontend `lib/safe-relative-path.ts` — same rule, re-checked at render (`notification-inbox.tsx:188`).

```
1. INVITATION  — bell → inbox row
   Title + 1–2 line body + "Share feedback →" + timestamp.
   Tap anywhere on the card body → mark read → close panel → navigate to /feedback.
   Dismiss X → row hidden, never read, never navigated.

2. ARRIVAL — GET /feedback  (authenticated only)
   ├ Unauthenticated → the app's existing protected-route redirect to /login
   │                   with neutral messaging (AGENTS.md Auth Messaging Rule).
   ├ Already responded          → THANK-YOU STATE directly (step 7). Never the form.
   ├ Campaign CLOSED, not yet responded → CLOSED STATE (§F). Never the form.
   └ Otherwise (OPEN, not yet responded) → the form.

   This is a BLOCKING status fetch (GET /feedback/campaign — no path param; there is
   exactly one fixed instrument, see §G's "campaign identity" note) that decides
   which of the three states above to render before anything shows — the page's
   whole purpose is a fast, low-friction interaction, so its failure behavior must
   be decided now, not left to Codex to invent:
     ├ Loading                 → skeleton/spinner matching the form's shape, not a
     │                            blank page.
     ├ Call FAILS (network/5xx) → FAIL-SOFT TO THE FORM, never an error screen.
     │              The unique index on campaign_feedback_responses (§G, H) makes a
     │              duplicate submission from a learner who HAD already responded a
     │              benign no-op — caught and swallowed exactly like the existing
     │              dedup pattern in notifications.md:18-36. ⚠️ If the campaign has
     │              ACTUALLY closed and the status check merely failed to report it,
     │              the learner sees the form and can attempt to submit — that
     │              submission is not cosmetic-only, because POST /feedback/campaign
     │              independently re-checks the close boundary server-side (§G,
     │              owner decision #4) and rejects a late submission with a response
     │              the frontend renders as the CLOSED STATE copy in place, not a
     │              generic error toast. So the failure mode of a failed status
     │              check is at worst "one extra round trip before the learner sees
     │              CLOSED," never a submission that bypasses the close boundary.
     └ Call SUCCEEDS → branch on { submitted, campaignOpen } as specified above.
   Page chrome:  <BackLink href="/dashboard" label="Dashboard" />   ← sub-page, ui-standards.md:61
                 <PageHeader eyebrow="FEEDBACK" title="Help us improve NoteLib" description=… />

3. SELECTION — ONE adaptive screen. No wizard, no steps, no progress bar.
   role="group" aria-labelledby={primaryQuestionId}
   9 full-width option rows, each ONE <button role="checkbox" aria-checked>.
   ⚠️ Mutual exclusivity:
      selecting "Nothing major" → clears all 8 blockers (and their conditionals)
      selecting any blocker     → clears "Nothing major"
      free text is NEVER cleared by either (§11)

4. CONDITIONAL FOLLOW-UPS — appear inline, immediately under their parent row.
   ├ "The quiz questions could be better" → multi-select, 7 options (owner-added
   │      "Some questions or answers seem incorrect" as the FIRST option — real
   │      production evidence now exists, see F's revised list)
   ├ "The paid plans aren't right for me" → SINGLE-select radiogroup, 5 options
   ├ "A feature I need is missing"        → short text (200)
   └ "I can't find enough content…"       → short text (200)
   All OPTIONAL. Selecting the parent alone is a valid submission.
   ⚠️ Deselecting a parent HIDES its follow-up and its answers are STRIPPED at submit.
      Kept in local state so re-selecting restores them; never persisted orphaned.
      The SERVER independently rejects conditional answers whose parent is not selected.

5. FREE TEXT — one textarea, optional, max 2000. Always visible, never conditional.
   "Something else" relies on THIS field (§16) — no second inline textarea.

6. SUBMIT — POST /feedback/campaign
   Enabled when: ≥1 primary option selected OR non-blank free text.
   Completely empty submission is BLOCKED client-side and 400s server-side.
   Button shows loading state via Button's loading/loadingText/aria-busy (button.tsx).
   ⚠️ CLOSED-AT-SUBMIT-TIME: the backend re-checks the close boundary on every POST,
   independent of what the arrival GET reported (owner decision #4 — "the backend
   must enforce the close boundary as the authority; frontend-only closing is
   insufficient"). A submission that arrives after close (a learner who had the form
   open across the close moment, or a direct POST replay) is rejected and the
   response is rendered as the CLOSED STATE (step 7b) in place of the form — never
   a generic error toast, since this is an expected boundary condition, not a fault.
   ⚠️ PRECEDENCE: if this learner had ALSO already responded, the duplicate check
   wins over the close check — they see THANK-YOU (their own prior response), not
   CLOSED. The service checks the unique-constraint outcome before the isOpen()
   rejection (§H).

7. THANK-YOU — replaces the form IN PLACE on the same route. Not a new route, not a modal.
   "Thanks for helping us improve NoteLib 💙"
   "Your feedback has been sent. We read these responses when deciding what to improve next."
   Primary action: [ Back to studying ] → href="/dashboard"
   ⚠️ NO second ask, NO rating, NO NPS, NO upgrade prompt, NO email signup. (§19)

7b. CLOSED STATE — replaces the form IN PLACE, reached either from arrival (GET
    reports campaignOpen: false) or from a late submit attempt (POST rejects for
    the same reason). Distinct copy from THANK-YOU, because "you responded" and
    "the window closed" are different facts and conflating them would make a
    learner who never got the chance feel like they had:
   "This feedback campaign has ended."
   "Thanks to everyone who shared feedback. If there's something you'd like us to
   know, you can still use Send Feedback."
   Primary action: [ Back to studying ] → href="/dashboard"
   Points at the SAME permanent Send Feedback affordance the already-responded
   state does (§F) — no second feedback route is invented for this state.

8. RETURN — "Back to studying" is a Button with an explicit href="/dashboard".
   ⚠️ router.back() is FORBIDDEN by convention — AGENTS.md:1033 and ui-standards.md:63,
      both state back navigation is ALWAYS an explicit href. So §19's "return to prior
      location" resolves to the stable fallback the spec itself allows. This is a
      convention decision, not a capability limit.
```

### E.1 Option row — the second nested-interactive trap

The shared `Checkbox` (`components/ui/checkbox.tsx`) is a `<button role="checkbox">` that is **20×20px** with its own `aria-label`. **Nesting it inside a full-width row button is the same nested-interactive defect as D.2**, and leaving it as a bare 20px target fails the touch-target minimum with 9 options at 390px.

**Recommendation: one `<button role="checkbox" aria-checked>` spanning the whole row**, containing a non-interactive check-glyph `<span>` and the option text. Accessible name = the visible option text (satisfies Label in Name), one interactive element, full-width ≥44px target.

This **deliberately does not reuse `Checkbox` as a component**, while reusing its exact visual language — the `Check` icon, `border-blue-600 bg-blue-600 text-white` when checked, `border-border bg-background` when not (`checkbox.tsx:29-35`). Flagged explicitly because `ui-standards` records "a raw checkbox where the shared component is used elsewhere" as a past defect: **the reason here is nesting, not convenience**, and it should be stated in the feature doc so a later reader does not "correct" it.

For the single-select paid-plan follow-up, use a real `role="radiogroup"` with roving tabindex, following `components/notes/regenerate-scope-modal.tsx:151-197` (which documents *"the repo has no radiogroup to copy"* and conveys selection by **border + check icon, never colour alone**, `:179-190`). The modal's `tabIndex={0}`-from-first-render caveat is an `AppModal` focus-trap constraint and does **not** apply on a page, but roving tabindex is still the correct radiogroup pattern.

Card-style selection may reuse `getSelectionCardClassName` from `lib/clickable-card.ts:8-29` for the selected/unselected visual treatment.

---

## F. Final question wording

Exact learner-facing copy. NoteLib-style: plain, second person, no marketing voice, no survey-research register.

### Campaign notification (Admin → What's New)

| Field | Value | Limit |
|---|---|---|
| **Title** | `Help us improve NoteLib` | 255 ✅ |
| **Body** | `What gets in the way when you study? Tell us what we should improve — it takes about a minute.` | 96 chars — within the ~160 soft target ✅ |
| **Link label** | `Share feedback` | 64 ✅ |
| **Link path** | `/feedback` | 512 ✅, passes both validators |
| **Audience** | `EVERYONE` |
| **Expires** | Optional — see §25 |

> 💙 is **not** in the notification title. The spec's example shows it, but the inbox row has no emoji convention and `ui-standards.md:22` bans emoji in eyebrows. Keep the emoji for the thank-you state, where it reads as warmth rather than decoration.

### Campaign page

| Element | Copy |
|---|---|
| **Eyebrow** | `FEEDBACK` |
| **Page title** | `Help us improve NoteLib` |
| **Page description** | `Tell us what gets in the way, and we'll use your feedback to decide what to improve next.` |
| **Primary question** | `What gets in the way when you study with NoteLib?` |
| **Instruction** | `Choose any that apply.` |

**"Build" → "improve" (owner decision, 2026-09-22).** Not every response implies building a new feature — the right response to a given answer may be fixing quiz quality, simplifying a workflow, improving guidance or content, clarifying pricing, or genuinely building something new. "Improve" covers all of those without pre-committing to which; it also now matches the thank-you copy's existing wording ("deciding what to improve next"), so the page description and the thank-you state no longer use two different verbs for the same idea.

### Primary choices (order as shown)

1. `It's hard to know what to study or do next`
2. `Studying takes too many steps`
3. `The quiz questions could be better`
4. `I want more practice questions`
5. `I can't find enough content for what I'm studying`
6. `A feature I need is missing`
7. `The paid plans aren't right for me`
8. `Something else`
9. `Nothing major — NoteLib works well for me`

**✅ Owner-approved (2026-09-22) as ordered.** Keep `Something else` at 8 and `Nothing major` last at 9 — inverting the spec's original 8/9 listing, per the reasoning below. `Nothing major` stays mutually exclusive with every blocker (§3, enforced in E's SELECTION step); free text keeps surviving selection/deselection of either, unchanged.

**Two changes from the spec's ordering, both deliberate:**

- **`Something else` moves from 9th to 8th, and `Nothing major` moves to last.** The spec lists them 8/9 respectively. The exclusive "no blocker" option should be the terminal item: it is the semantic opposite of everything above it, and placing a *blocker* option after it reads as an afterthought. Putting the exclusive option last is also the standard convention for mutually-exclusive escapes.
- **No other wording changes.** Audited against the spec's §10 criteria:

| Check | Finding |
|---|---|
| Overlap | 1 (direction) vs 2 (workflow) vs 3 (quality) vs 4 (quantity) vs 5 (availability) are genuinely distinct axes, as §10 intends. **The one real risk is 4 vs 5** — "I want more practice questions" vs "I can't find enough content". Judged acceptable: 4 is about *generated questions on content you already have*, 5 is about *finding source material at all*. The conditional on 5 ("What are you studying?") disambiguates in the data. |
| Leading language | None. Each names a learner-side experience, not a product verdict. |
| Response bias | Acceptable-response bias is handled by option 9 (§11). |
| Comprehension | No internal product vocabulary — no "Study Pack", "Adaptive Practice", "Review Set". ✅ §10's lock holds. |
| Mobile scanability | Longest option is 49 chars → two lines at 390px in a full-width row. Acceptable. |
| Diagnostic usefulness | Each maps to a distinct team response (guidance / workflow / generation quality / generation quantity / catalog / roadmap / pricing). |

### Conditional follow-ups

**If `The quiz questions could be better`** — *multi-select*

> `What's not working with the questions?`
> `Choose any that apply.`
>
> - `Some questions or answers seem incorrect`
> - `Too easy`
> - `Too difficult`
> - `Not relevant enough`
> - `Too repetitive`
> - `The explanations aren't helpful enough`
> - `Something else`

✅ Confirms §12's instruction to avoid "random" — "Not relevant enough" is the diagnostically precise form. Note this follow-up is **directly validated by production**: the one genuine feedback row NoteLib has ever received was a Quick Review question-quality report.

**⚠️ `Some questions or answers seem incorrect` added first, owner decision (2026-09-22), on new evidence.** At the time of the original audit this list only covered subjective quality axes (easy/hard/relevant/repetitive/explanations). Since then, `v0.155.0`'s Quick Review correctness incident (`docs/claude-findings/2026-09-19-quick-review-percentage-increase-correctness-incident.md`) confirmed a real, production-verified answer-key defect class — a distinct failure mode from "the explanations aren't helpful," and the most important one this list was missing. Ordered first because it is the most actionable finding this campaign could plausibly surface: a report that maps directly to the H4 validator's rejection-rate work, not a subjective quality complaint. **This stays a conditional, sub-level diagnostic option, never a top-level blocker** — the top-level list (§F primary choices) is unchanged; a learner only reaches this option after already selecting "The quiz questions could be better."

**If `The paid plans aren't right for me`** — *single-select*

> `What best describes it?`
>
> - `I can't comfortably afford it`
> - `I don't see enough value to pay for it`
> - `I'm happy with the free version`
> - `I don't understand what I'd get by upgrading`
> - `Something else`

✅ **Single-select CONFIRMED**, as §13 prefers. "Best describes it" is explicitly a *choose-one* framing, and the four situations map to four different team responses (pricing/access · value proposition · healthy free behaviour · packaging clarity). Multi-select would destroy exactly the distinction §13 exists to preserve.

**If `A feature I need is missing`**

> `What are you looking for?` — short text, optional, max 200.

**If `I can't find enough content for what I'm studying`**

> `What are you studying?` — short text, optional, max 200.
>
> **Recommend plain short text, NOT a Course/Program selector.** §15 asks whether catalog metadata could be offered. It could — `catalog-multi-select.tsx` exists — but it is an **admin** control (its only three consumers are `components/admin/*`), it requires a catalog fetch on page load, and §15's own lock says the learner must not be made to understand NoteLib's taxonomy. Free text is lighter, matches what we are measuring ("I can't find enough study content"), and the answers are readable at the response volumes §30/§A3 predict.

### Free text

> `Anything else you'd like us to know?`
> `What would make NoteLib more useful for you?`
> `Optional`

Max **2000** characters, with the counter pattern from `send-feedback-widget.tsx:340-352`.

### Submit button

> `Send feedback`

Two words ✅ (`ui-standards.md:127`, four-word hard cap). Matches the existing Send Feedback vocabulary without colliding — the modal's trigger is `Send Feedback` (title case).

### Thank-you state

> ### `Thanks for helping us improve NoteLib 💙`
> `Your feedback has been sent. We read these responses when deciding what to improve next.`
>
> **[ Back to studying ]** → `/dashboard`

Three words ✅. It is a `Button`, not a `BackLink`, so the "no Back to prefix" rule (which governs `BackLink` labels specifically, `ui-standards.md:58`) does not apply.

### Already-responded state (revisit)

> ### `You've already shared your feedback 💙`
> `Thanks — we've got your response. If something else comes up, you can always use Send Feedback.`
>
> **[ Back to studying ]** → `/dashboard`

**This copy is load-bearing for §1's lock**: it is the one place the campaign points the learner back at the permanent open-door channel, which keeps the two jobs visibly separate rather than making the learner feel the door is now closed.

### Closed state (new, owner decision #4, 2026-09-22)

> ### `This feedback campaign has ended.`
> `Thanks to everyone who shared feedback. If there's something you'd like us to know, you can still use Send Feedback.`
>
> **[ Back to studying ]** → `/dashboard`

Reached from arrival (GET reports `campaignOpen: false`, learner never responded) or from a rejected late submit (E, step 6). **Deliberately distinct copy from the already-responded state**, even though both end in "use Send Feedback": one is addressed to a learner who *did* respond ("we've got your response"), the other to a learner who *never got the chance* ("thanks to everyone who shared feedback" — inclusive, not personal). Reusing the already-responded copy for both would misrepresent a learner who never submitted as having done so.

**Both closed-adjacent states point at the same permanent Send Feedback affordance** — no second feedback route is invented for either, matching the "provide the existing affordance, don't build a second route" instruction.

---

## G. Data model recommendation

# ➡️ CREATE A CAMPAIGN RESPONSE MODEL

**New table `campaign_feedback_responses` (V148 — confirmed free, see the production-reads table above).**

### Campaign identity — tightened (owner decision #3, 2026-09-22)

**No `CampaignId` Java enum.** The original audit proposed one alongside `PrimaryBlocker`/`QuizIssue`/`PlanIssue`, on the reasoning that all four are "closed sets of valid values." That reasoning does not actually hold for `CampaignId` the way it holds for the other three: `PrimaryBlocker` etc. each have 5-9 real members that the UI renders as distinct options and that a service must switch on — an enum earns its keep there. A `CampaignId` enum in v1 would have **exactly one member.** An enum with one member is not type safety, it is a constant wearing a registry's clothes — and the shape it implies (a `switch`, a lookup, room for a second case to slot into) is precisely the campaign-registry/multi-campaign-schema architecture the owner does not want implied.

**Recommendation: a named `String` constant, not an enum.**

```java
// CampaignFeedbackService.java
private static final String CAMPAIGN_ID = "STUDY_FRICTION_2026_09";
```

Validation is scoped to this one instrument, not generalized: the service/controller compare the incoming or stored value against this single constant (there is no incoming campaign identifier from the client at all once the route drops its path param — see below), and there is nothing else to validate against because nothing else exists. If a second campaign is ever built, that is the moment to introduce whatever registry shape it actually needs — not before, on a hypothetical.

**The `campaign_id VARCHAR(64)` column stays** (owner decision #3 explicitly asks for a "stable internal campaign identifier… for provenance and the unique `(user_id, campaign_id)` constraint"). It holds the one constant value on every row. This is not inconsistent with dropping the enum: the column is data (what the unique index and any future aggregation key on), the enum would have been code structure implying extensibility that does not exist yet.

**Route simplified to match: no `{campaignId}` path param.** `GET /feedback/campaign/{campaignId}` implies a client can address different campaigns — false for v1, and exactly the "campaign registry" shape being avoided. **Both endpoints drop the path segment**: `GET /feedback/campaign` and `POST /feedback/campaign`. The one campaign identity lives server-side as `CAMPAIGN_ID` and is never client-supplied. (This also closes off a class of bug for free: a client cannot probe for or guess at other campaign IDs, because there is no field through which to supply one.)

### Campaign close — bounded research, backend-enforced (owner decision #4, 2026-09-22)

**No campaign table, no status column, no scheduler, no admin lifecycle controls** — all explicitly ruled out. The close boundary still needs *some* representation, and the owner asked for "the simplest repo-consistent representation."

**Recommendation: a configured application property, not a database value.** This repo already has a real precedent for exactly this shape — config-driven values with no dedicated table, read via `@Value` (the `LLM_MODEL_FREE`/`LLM_MODEL_PREMIUM`/`LLM_MODEL_CRITIQUE` triple documented in this file's own Development Commands section). Add one property:

```yaml
# application.yaml
notelib:
  campaign:
    study-friction-2026-09:
      closes-at: 2026-10-13T00:00:00Z   # placeholder — owner sets the real date
```

```java
// ⚠️ @Value cannot bind a plain config string directly to java.time.Instant —
// there is no Spring converter for it out of the box, and it fails at STARTUP
// (ConversionFailedException), not silently. Verified: grepping this codebase's
// existing @Value usages (NoteBulkImportService, NoteBulkRegenerationService,
// NoteBulkGenerationService, GlobalExceptionHandler) turns up only String, int,
// long and DataSize bindings — no temporal type precedent to lean on. Inject as
// String and parse explicitly:
@Value("${notelib.campaign.study-friction-2026-09.closes-at}")
private String closesAtRaw;

private Instant closesAt;

@PostConstruct
void init() {
    closesAt = Instant.parse(closesAtRaw);
}

private boolean isOpen() {
    return Instant.now().isBefore(closesAt);
}
```

**No default value on the `@Value` placeholder.** A missing property should fail application startup, not silently default to "always open" or "always closed" — this is the correct fail-closed-on-misconfiguration behavior for a research instrument's boundary, and it should be stated as deliberate in the Codex prompt rather than left to be discovered as a missing default.

**Tests must override this property, never depend on the committed value or wall-clock time.** The committed `application.yaml` value is a real date and will eventually be in the past relative to CI — a test asserting "submit after close is rejected" that relies on the committed value rots the moment that date passes. Use `@TestPropertySource(properties = "notelib.campaign.study-friction-2026-09.closes-at=2020-01-01T00:00:00Z")` for the "always closed" test class/method and a far-future value for "always open," rather than asserting anything against `Instant.now()` at test-run time.

**Why this beats the two alternatives:**
- **A hardcoded Java `Instant` constant** would work but requires a code change + redeploy to adjust the date, for a decision (when to stop collecting responses for a live research instrument) that is squarely a product call the owner may want to change without touching code.
- **A DB column** (on a `campaigns` table, or a stray column somewhere) is explicitly what's ruled out — it implies the campaign-lifecycle infrastructure (status transitions, an admin control to flip it) the owner does not want built for one fixed instrument.
- A config property is genuinely the smallest thing that (a) has one clear owner-editable value, (b) requires no migration, no table, no admin UI, and (c) the owner can change via a Render environment variable and a restart — the same class of action already reserved to the owner for env vars and deploys (`CLAUDE.md`'s production-database section lists this explicitly as an owner action, not Claude's).

**Backend is the enforcement authority, not a UI-only gate.** `isOpen()` above is checked in exactly two places, both server-side:
1. `GET /feedback/campaign` — returns `campaignOpen: false` once past `closesAt`, driving the CLOSED STATE (§E, §F).
2. `POST /feedback/campaign` — attempts the insert, catches `DataIntegrityViolationException` from the unique index FIRST (→ 200 already-submitted, §H), and only checks `isOpen()` for a genuinely new insert attempt, rejecting a late one with a distinguishable response (not a generic 400) if closed. **Ordering matters** (§H's precedence note): a learner who already responded must never see "campaign closed" instead of their own thank-you state, so the duplicate check is not merely present but checked *before* the close check. This is the owner's explicit concern: *"otherwise someone could POST directly after the UI says the campaign ended, which would make our 'bounded research' semantics cosmetic."* Both checks together are a couple of `if`s in the service around the insert; no new infrastructure.

**Announcement expiry and campaign close are two independent boundaries, not one.** `AnnouncementEntity.expiresAt` (existing, `notifications.md`) controls whether the *invitation* keeps showing in the bell. `closesAt` controls whether the *research instrument* keeps accepting responses. A learner can reach `/feedback` by direct URL after the announcement itself has expired (§L, out of scope for this feature to change) — closing the campaign is what actually stops new responses, not letting the announcement expire. Do not conflate the two or try to derive one from the other.

### Why not REUSE the existing feedback model

**The discriminating constraint is §23, not tidiness.** §23 requires *"count per primary blocker"* and *"conditional breakdowns."* `feedback.message` is an unstructured `text` column. You cannot `GROUP BY` a newline-prefixed preamble.

This is not hypothetical — it is **exactly the failure the one real production row demonstrates**. `Feedback type: Quiz Feedback | Quiz: Quick Review | Context: Quiz Results | Note: …` is structured data that was stringified into prose, and the `v0.155.0` incident audit had to recover `note_id`/`session_id` by parsing `page_url` and infer the question index from the learner's own wording. Reusing that model would reproduce the defect deliberately, in a feature whose entire purpose is aggregation.

Three further blockers:
- `SubmitFeedbackRequest` carries **one field** (`message`). Adding array fields to it changes the shape of the one endpoint every existing trigger uses — a regression surface across 5 components for zero benefit.
- Campaign responses need **one-per-user-per-campaign** (§H). `feedback` is deliberately unbounded and unthrottled — every submission inserts a row and emails support. Adding a unique constraint there would break the open-door channel.
- Campaign responses should **not** fire the support notification email. `FeedbackService.submitFeedback:90-93` sends unconditionally.

### Why not EXTEND the existing model

Adding nullable array columns to `feedback` merges two different lifecycles into one table: one is unbounded user-initiated prose with screenshots and a support-email side effect, the other is bounded, structured, deduplicated, silent aggregation. Every future read of `feedback` would then need a `campaign_id IS NULL` predicate, and forgetting it once silently corrupts both the admin dashboard's 5-row recent list and any campaign count.

It also **collides with §R of the incident audit**, which recommends adding `note_id` / `session_id` / `question_index` / enum context columns to `feedback` — explicitly scoped as separate work. Two independent expansions of the same table, designed by two efforts that do not know about each other, is how a table becomes unreadable. **Keeping them separate lets §R proceed untouched.**

### Recommended shape

```sql
-- V148__campaign_feedback_responses.sql
CREATE TABLE IF NOT EXISTS campaign_feedback_responses (
    id                      UUID PRIMARY KEY,
    user_id                 UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    campaign_id             VARCHAR(64) NOT NULL,
    primary_blockers        TEXT[]      NOT NULL,
    quiz_issues             TEXT[],
    plan_issue              VARCHAR(64),
    missing_feature_text    VARCHAR(200),
    content_subject_text    VARCHAR(200),
    free_text               TEXT,
    created_at              TIMESTAMPTZ NOT NULL
);

-- ⚠️ THIS INDEX IS THE DUPLICATE-SUBMISSION GUARANTEE (§H). Not a service check.
CREATE UNIQUE INDEX idx_campaign_feedback_user_campaign
    ON campaign_feedback_responses(user_id, campaign_id);

CREATE INDEX idx_campaign_feedback_campaign_created
    ON campaign_feedback_responses(campaign_id, created_at DESC);
```

**`TEXT[]`, not a child table and not JSONB.** Justified by repo precedent, not preference:

- **There is no join-table precedent anywhere in the backend** — zero `@ElementCollection`, zero `@CollectionTable`, zero `AttributeConverter`. Introducing one for a table expected to hold tens of rows adds an unprecedented pattern.
- **`text[]` is the established flat-string-list pattern**, 4 instances, all identical: `@JdbcTypeCode(SqlTypes.ARRAY)` + `@Column(columnDefinition = "text[]")` + `String[]` — `NoteEntity.java:49-51` (tags), `StudyPackEntity.java:103-105` (tags), `UserEntity.java:63-65` (focus_subjects), `UserEntity.java:107-109` (review_days).
- **JSONB is the repo's pattern for *structured* data**, not flat enumerations (`AnalyticsEventEntity.java:38-40`, `StudyPackEntity.java:57-59`). These are flat name lists.
- **Aggregation stays trivial**: `SELECT unnest(primary_blockers) AS blocker, count(*) FROM campaign_feedback_responses WHERE campaign_id = ? GROUP BY 1 ORDER BY 2 DESC;` — one line, which is the whole of §I's v1.

**`primary_blockers`/`quiz_issues`/`plan_issue` values are validated against Java enums** (`PrimaryBlocker`/`QuizIssue`/`PlanIssue` — these three retain their enums; only `CampaignId` was dropped, see above) **at the API boundary and stored as enum `name()` strings.** No DB `CHECK` constraint — consistent with `feedback.status varchar(32)`, which also has none (`V31:7`). Unknown values are rejected with a 400 naming the field. `campaign_id` is not client-supplied at all (see above) so it needs no request-side validation, only the server-side equality check against `CAMPAIGN_ID`.

**Migration house rules confirmed:** plain `CREATE INDEX`, **never `CONCURRENTLY`** (Flyway wraps migrations in a transaction — the rule is stated in `V140__announcements.sql:17-18`).

### Deliberately NOT stored (§21)

**No behavioural snapshot columns.** No profile type, no plan, no account age, no has-generated-a-Study-Pack, no has-completed-a-quiz, no has-adopted-a-plan. §21's own principle — *survey = what the learner says; product behavior = what the learner does* — plus the practical point: `user_id` is stored, so **every one of those can be joined at read time** from live data, and a snapshot would go stale and disagree with the source. Storing them would be the "hidden telemetry collection" §21 forbids.

**No screenshot support** (§22) — confirmed. `feedback_image` has **0 rows in production** across the entire life of the feature, so the existing upload path has never once been used. Duplicating a 2MB multipart pipeline with magic-byte sniffing for a campaign that does not need it is unjustifiable.

**Account deletion:** `AccountPurgeService.deletePersonalRows` must gain a `deleteByUserId` call for the new repository, mirroring `feedback` (`AccountPurgeService.java:202`). ⚠️ The `ON DELETE CASCADE` on `user_id` covers the DB, but the repo's convention is an explicit purge call — and `notifications.md:370-373` records that exactly this step shipped **missing** in `v0.130.0`. **Do not omit it.**

---

## H. Duplicate / revisit semantics

**Policy: B — ONE RESPONSE PER USER PER CAMPAIGN.**

Chosen over C (latest replaces) because a replacement policy invites a learner to overwrite considered input by accident, and over A (multiple) because §20's stated preference is to *"avoid accidental duplicate responses."*

**Enforced by the UNIQUE index, never by a service pre-check.** This is settled repo doctrine, not a new decision — `notifications.md:18-36`:

> **⚠️ Do NOT add an `existsBy…` check before the insert.** Two concurrent deliveries can both pass such a check and still create duplicates… instead attempt the insert and **catch `DataIntegrityViolationException`**, returning the existing row — the same shape `NoteCollectionService:952` uses for the adoption unique index.

**One mechanism answers four of §20's five scenarios at once:**

| Scenario | Behavior |
|---|---|
| Opens the campaign twice | Second load returns `{ submitted: true }` → thank-you state, never an empty form |
| Submits twice (double-click, retry) | Unique index rejects; service catches `DataIntegrityViolationException` → **200 with the already-submitted state, never a 500 and never an error toast** |
| Revisits after submitting | Already-responded state (§F copy), pointing at Send Feedback |
| Clicks the announcement after responding | Same — the notification is a dumb link; the route decides |
| Multiple tabs | Both tabs POST; one wins, the other gets the same benign already-submitted 200 |

**⚠️ A duplicate submission is a successful no-op, never an error surfaced to the caller** — verbatim the rule `notifications.md:35-36` already states for deliveries.

**⚠️ PRECEDENCE, made explicit (closes a gap the plan otherwise leaves implicit): a learner who already responded AND submits again after the campaign has closed hits both the duplicate check and the close check. ALREADY-RESPONDED WINS — 200 with the already-submitted state, never the 409 closed rejection.** Reasoning: by the time either check runs, the row already exists — the unique-index catch is the earlier, narrower guard (did *this user* already respond, checked on every submit regardless of campaign state) and the close check is the later, broader one (is the campaign still accepting *anyone's* first response). A learner who already contributed should never see "this campaign has ended" as if their own submission hadn't counted; they should see their own already-submitted state, exactly as they would before the campaign closed. Concretely: **check the unique-constraint outcome first (catch `DataIntegrityViolationException` → 200 already-submitted), and only reach the `isOpen()` rejection on a genuinely new insert attempt.** State this ordering explicitly in the Codex prompt — left implicit, Codex could plausibly check `isOpen()` first and produce the wrong result for exactly the learner most likely to hit this path (someone revisiting the campaign near its close date).

**Direct URL behavior:** any authenticated user may submit while the campaign is OPEN, whether or not they received the announcement (§24 — owner decision #5, 2026-09-22: *"Announcement audience determines WHO IS INVITED, not WHO IS AUTHORIZED"*). Unauthenticated users hit the app's existing protected-route redirect. **Once the campaign closes, nobody may create a new response, regardless of how they reached the route** — enforced server-side per §G's close boundary, not by a separate eligibility system. This is deliberately the *only* gate: there is no second audience/eligibility check layered on top of "authenticated + campaign open," and none should be added.

**Rate limiting: NOT required, and that is a decision rather than an omission.** The unique index caps a user at one row per campaign, which is a stronger bound than any rate limiter. Four in-memory limiters exist in `backend/src/main/java/com/studysnap/backend/security/` if one is ever needed, but the established recipe (a service, an `assertAllowed`, a `@Scheduled` purge, config-backed limits — 4 files) would add real surface for no gain here. **Record this explicitly in the feature doc so it is not read as an oversight.** Note separately that `POST /feedback` itself has **no** rate limit today — pre-existing, out of scope, worth a Backlog row.

---

## I. Admin response-review experience

# ➡️ OUTCOME D for v1 — no new admin UI

**Recommended v1: a committed read-only SQL file the owner (or Claude, read-only) runs — `docs/claude-plans/campaign-feedback-read.sql`.**

### Why this is the right size, on evidence

| Fact | Consequence |
|---|---|
| 405 active users | The ceiling on the audience |
| 1 of 57 notifications ever read (1.8%) | Expected responses are **single to low double digits** (§A3) |
| 3 feedback rows in total, ever | The existing admin surface has never been under any pressure |
| **There is no admin feedback list endpoint at all** | A campaign list UI would be the *first* such surface — new scope, not an extension |
| Admin has **no pagination anywhere** | `AdminDashboardService` uses `PageRequest.of(0, LIMIT)` slices only (`:152, :198, :211`) |

At N ≈ 20, a `GROUP BY` returns the complete picture in one query, and reading ~20 free-text answers directly is *better* than any chart. **§23 explicitly lists D as a legitimate outcome** — *"no new UI yet if responses can safely be inspected through an existing internal workflow"* — and this repo has exactly such a workflow: committed read-only SQL, the pattern `docs/curriculum/review-set-reshape-read.sql` already established.

It also honours `notifications.md:331-336`, which records that announcement **funnels, CTR dashboards, attribution and campaign analytics are deliberately not built.** A campaign analytics UI would quietly reverse that.

### What the SQL file contains

1. Response count, and count as a share of active users.
2. `unnest(primary_blockers)` → count per blocker, descending. **The headline read.**
3. `unnest(quiz_issues)` → count per quiz issue.
4. `plan_issue` → count per value.
5. All non-null `missing_feature_text`.
6. All non-null `content_subject_text`.
7. All non-blank `free_text`, newest first.
8. Optional segmentation joined live from `users` / subscriptions by `user_id` (§21) — **one query, only if the response count makes it meaningful.**

### Explicitly deferred

No charts, no aggregate dashboard card, no campaign response table in Admin, no filtering UI, no status workflow. **A `[CHECKPOINT]` row (§M slice 4) revisits this if response volume or repeated campaign usage makes SQL/manual review materially inefficient** — volume (e.g. responses climbing past ~50) is evidence that this may be happening, not a product requirement to build an Admin UI by itself; a small, easily-read N stays on the SQL path regardless of how "old" the campaign gets.

**Related pre-existing gap, NOT fixed here:** `FeedbackStatus.REVIEWED` and `CLOSED` are defined but **never written by any code** — `submitFeedback` hardcodes `NEW` (`FeedbackService.java:86`) and no setter exists anywhere. Out of scope; worth a Backlog row.

---

## J. Mobile / accessibility plan

### Narrow screen (390px reference, per `ui-standards.md:141`)

**Notification inbox** — see §D.7. `AppModal variant="sheet"`, edge-to-edge, `max-h-[85dvh]`; `line-clamp-3` bounds the card; dismiss to 44px; CTA wraps inside the clamped block; no horizontal scroll.

**Campaign page:**
- Single column throughout. **Do not use a two-column layout** — `ui-standards.md:119-121` gates that on comparable content weight; there is one column of content here.
- Option rows are **full-width, ≥44px tall**, stacked with `gap-2`. Nine rows is a long scroll at 390px — acceptable for a single adaptive screen, and the alternative (a wizard) is explicitly rejected by §17.
- Conditional follow-ups render **directly beneath their parent row**, visually indented, so the relationship survives at narrow width where an adjacent column would not.
- Submit is a **full-width button** at narrow width (`mobile-ui.md:78` — *"use full-width buttons for primary mobile actions"*).
- Textarea `min-h-[140px]` at mobile (the feedback widget uses `min-h-[180px] sm:min-h-[220px]`; smaller is right here since free text is secondary).
- The page **must not** be added to the mobile bottom tab bar (§7, §28).

### Keyboard

| Element | Behavior |
|---|---|
| Option rows | Native `<button>` → Tab reachable, Space/Enter toggles |
| Multi-select groups | Each row independently tabbable — correct for `role="checkbox"` |
| Paid-plan radiogroup | Roving tabindex; ↑/↓/←/→ move and select; one tab stop for the group (`regenerate-scope-modal.tsx:131-149`) |
| Conditional reveal | Appears **after** its parent in DOM order, so Tab reaches it next — no focus jump, no focus steal |
| Submit | Standard; `aria-busy` while in flight via `Button`'s `loading` prop |
| Notification CTA | Unchanged — body is a native `<Link>`, already pinned by test `:441` |

### Screen reader

- **Primary question:** `<h2 id={primaryQuestionId}>`, with the option container as `role="group" aria-labelledby={primaryQuestionId}`. Each `role="checkbox" aria-checked` then announces inside a named group.
- **Conditional reveal** is an `aria-live="polite"` region, so a newly-revealed follow-up is announced without stealing focus. ⚠️ Toggle with the `hidden` property / conditional render, **not** `style.display` — the artifact-independent repo rule is `globalThis` and semantic attributes; `[hidden]` is the correct mechanism.
- **Character counter:** `aria-live="polite"` + `tabular-nums`, exactly `send-feedback-widget.tsx:340-352`.
- **Validation errors:** `<p role="alert" className="text-sm text-red-600 dark:text-red-400">`. ⚠️ The repo applies `role="alert"` **inconsistently** — present in `bulk-generation-page-client.tsx:525`, absent in `app/admin/announcements/page.tsx:348` and `send-feedback-widget.tsx:334`. **Use it here**; the inconsistency is a known wart, not a convention to copy.
- **Thank-you state:** rendered as an `aria-live="polite"` region so the confirmation is announced on in-place replacement, matching the success pattern at `send-feedback-widget.tsx:293-301`.
- **Selection is never conveyed by colour alone** — the check glyph carries it, per `regenerate-scope-modal.tsx:179-190`.
- **Disabled submit:** use **native `disabled`**, not `aria-disabled`. `ui-standards.md:190-195` — native is correct when unavailability is self-evident and carries no information the user needs. Pair it with visible helper text stating the minimum (`Choose at least one option, or tell us in your own words.`) so the reason is available without focusing the button.

---

## K. Generic vs campaign-specific changes

**§26 requires these be separable. They are — the two lists share no file.**

### GENERIC — announcement / notification system

Applies to every destination-bearing notification. **No feedback-specific assumption enters this layer.**

| # | Change | File |
|---|---|---|
| GEN-1 | Render `ctaLabel` as a non-interactive `<span>` affordance inside the existing body `<Link>`, blue, with a decorative `→`. Rendered only when a safe `ctaPath` **and** a non-blank `ctaLabel` exist. | `notification-inbox.tsx` |
| GEN-2 | Extend the Link's accessible name to include the visible CTA (`aria-labelledby={`${titleId} ${ctaId}`}`) — WCAG 2.5.3. | `notification-inbox.tsx` |
| GEN-3 | `line-clamp-3` on the body span. | `notification-inbox.tsx` |
| GEN-4 | Dismiss button to `min-h-11 min-w-11` (44px). | `notification-inbox.tsx` |
| GEN-5 | Move row padding onto the Link/dismiss so the card's full width is tappable (closes G4). | `notification-inbox.tsx` |
| GEN-6 | Admin body character counter + soft-target helper text (~160 chars). | `app/admin/announcements/page.tsx` |
| GEN-7 | Admin helper text under **Link label** clarifying it is the visible action text learners tap. **Labels stay "Link label" / "Link path"** — see below. | `app/admin/announcements/page.tsx` |
| GEN-8 | Document the stored-vs-displayed body split, the CTA affordance contract, and the span-not-link rule. | `docs/features/notifications.md` |

**§4's rename question — answered NO.** Do **not** rename "Link label"/"Link path" to "Action label"/"Destination".

- The spec itself says *"do not rename fields blindly if this would create unnecessary migration/API churn"* and to separate storage naming from admin labels.
- The admin labels are **already** curator-friendly plain English (`page.tsx:273,284`), and `Link path` is paired with a helper that explains the constraint precisely (`:293-295`).
- "Destination" is **less** precise than "Link path": the field is a path, and the helper's rule (*must start with `/`*) reads naturally against "path" and awkwardly against "destination".
- A rename changes only a label while costing test churn and a doc sweep, against a surface with **zero production usage**.

**The real gap is not the names — it is that the curator cannot see the result (G3).** GEN-6/GEN-7 fix that directly. **Revisit the naming only if a real curator reports confusion after real usage.**

### CAMPAIGN-SPECIFIC — feedback campaign only

| # | Change |
|---|---|
| CAM-1 | `V148__campaign_feedback_responses.sql` + unique index |
| CAM-2 | `CampaignFeedbackResponseEntity`, `CampaignFeedbackResponseRepository` |
| CAM-3 | `PrimaryBlocker` / `QuizIssue` / `PlanIssue` enums. **No `CampaignId` enum** — a named `String` constant `CAMPAIGN_ID` instead (§G, owner decision #3: one instrument doesn't earn a registry-shaped type). |
| CAM-4 | `CampaignFeedbackService` — submit (catching `DataIntegrityViolationException`), status read (open/closed/already-responded), close-boundary check (`isOpen()` against the configured `closesAt`, §G), cross-field validation, conditional stripping |
| CAM-5 | `CampaignFeedbackController` — `GET /feedback/campaign`, `POST /feedback/campaign` (no `{campaignId}` path param — the one campaign identity is server-side only, §G) |
| CAM-6 | DTOs: `SubmitCampaignFeedbackRequest`, `CampaignFeedbackStatusResponse` |
| CAM-7 | `AccountPurgeService` purge call |
| CAM-8 | `app/feedback/page.tsx` + client component + option-row component |
| CAM-9 | `lib/api.ts` — `getCampaignFeedbackStatus`, `submitCampaignFeedback` |
| CAM-10 | `docs/features/feedback-system.md`, `DATA_MODEL.md`, `docs/claude-plans/campaign-feedback-read.sql` |

**The seam:** the generic layer knows only *"this notification has a label and a destination."* It never knows the destination is `/feedback`. The campaign layer never touches notification rendering. **GEN-1…GEN-8 ship and are valuable even if the campaign is cancelled.**

---

## L. Scope exclusions — confirmed

**Feature B and every §27 non-goal remain excluded.** Confirmed NOT in this work:

❌ Generic `CommunicationService` · ❌ Resend refactor · ❌ retention-email migration · ❌ channel abstractions · ❌ email preference redesign · ❌ `/notifications` page · ❌ "View all notifications" · ❌ communication event buses · ❌ unified email/in-app delivery · ❌ `/announcements` page or sidebar item · ❌ generic survey builder / question schemas / form builder / branching engine / campaign CMS / template system / NPS / analytics platform / form DSL · ❌ second audience-targeting engine · ❌ scheduled publishing · ❌ announcement CTR/funnel/attribution analytics · ❌ external CTA URLs · ❌ screenshot upload for campaign feedback · ❌ making `ANNOUNCEMENT` badge-eligible (owner decision #1) · ❌ notification impression/inbox-open analytics for this feature (owner decision #1 — the click-through-floor gap in A3 is *named*, not *closed*, by this work) · ❌ a `CampaignId` enum or any per-campaign registry shape (owner decision #3) · ❌ a campaign table, status column, or scheduler for the close boundary (owner decision #4 — a config property enforces it instead, §G) · ❌ a second audience/eligibility system gating who may submit while open (owner decision #5).

**⚠️ `ReEngagementCampaignService` MUST NOT BE TOUCHED.** `notifications.md:334-336` is explicit: 161 lines, hardcoded audience, fixed template, email-only, no campaign entity. **Do not refactor, merge, extend or delete it.** Its name is the trap — it is unrelated to this campaign despite the word.

### Adjacent planned work this must not collide with

**There is an existing multi-stage plan for this area.** Feature A must be read against it:

| Stage | Status |
|---|---|
| **Stage A** — current-state audit | ✅ `docs/claude-plans/attention-notifications-email-expansion-stage1.md` |
| **Stage B** — foundation hardening | ✅ Shipped as `v0.134.0`. **Backlog row says: NOT a candidate again; do not re-propose.** |
| **Stage C** — deep-link polish | *"Skipped / merged into D… Stage C as the brief describes it has an empty backlog"* (`:1026-1028`) |
| **Stage D** — first safe direct producer | ✅ Shipped as `v0.135.0` (Review Set update). **Blocked for further work** by the tightening addendum — the proposed dedup key is a permanent silent suppression bug. |
| **Stage E** — email bridge | ❌ **BLOCKED on R1** — needs a global cross-channel daily budget; the 100/day cap is already breached at 156–159/day observed. |
| **Stage F/G** — impact milestones, discovery digest | `future`, gated on evidence that does not exist |

**⚠️ Feature A effectively REOPENS Stage C with a real backlog.** Stage C was closed as empty *because* the only producer was announcements with an admin-authored CTA — which is precisely the gap GEN-1 fixes. The plan should say so rather than let a future reader think Stage C was skipped twice. **Feature A does not touch Stage D's blocked dedup-key issue and does not unblock Stage E.**

**Also explicitly out of scope** (recorded so it is not lost):
- `docs/claude-findings/2026-09-19-…-incident.md` **§R** — adding `note_id`/`session_id`/`question_index`/enum-context columns to `feedback`. §R itself says *"not the Campaign Feedback feature"* and *"should be scoped separately."* §G's separate-table decision **preserves** this as independent work.
- `POST /feedback` has **no rate limit** (pre-existing).
- `FeedbackStatus.REVIEWED`/`CLOSED` are **never written** (pre-existing).
- `send-feedback-widget.tsx` can be closed by Escape mid-submit (`:264` has no busy guard, unlike `app/admin/announcements/page.tsx:433`) — pre-existing.
- Two divergent bottom-sheet implementations exist (`AppModal variant="sheet"` vs the widget's hand-rolled classes). **Do not add a third**; the inbox already uses the real one.

---

## M. Implementation plan — 4 slices, 2 releases (owner decision #2, 2026-09-22)

**Split approved, and it is a release split, not just a slice-numbering convenience.** Slice 1 ships as its own release (**Release A — Actionable Announcements**); slices 2-4 ship together as a second, later release (**Release B — Campaign Feedback**). Do not combine them merely because both are ready to prompt — the owner's instruction is explicit that Release A should be able to ship, be verified, and sit in production **on its own**, frontend-only, with no backend/API/migration change, before Release B's persistence/API/page work begins. This also means Release A gets kicked off, built, verified and signed off as a complete release cycle in its own right (its own `RELEASES.md` section, its own signoff), not folded into whatever release happens to be open when this plan is approved.

**Provisional version numbers** (not owner-assigned, since kickoff hasn't happened for either): the next two sequential versions after whichever release is open at approval time — as of this writing that is `v0.155.0` (In Progress), so Release A would be **v0.156.0** and Release B **v0.157.0**, assuming nothing else is kicked off between now and then. This plan does not commit to those numbers; whoever runs kickoff confirms the actually-next-free number at that time, per this repo's own "a claim about production/repo state is a snapshot, not a fact" rule.

Deliberately few, coherent slices within that split. **Slice 1 is independent of everything else and should ship first regardless of whether the campaign proceeds.**

### RELEASE A — Actionable Announcements

### Slice 1 — Generic linked-announcement presentation `[FRONTEND ONLY, NO BACKEND, NO MIGRATION]`

**Delivers GEN-1 … GEN-8.** The whole of §D except the campaign.

| | |
|---|---|
| **Backend** | **None.** `ctaLabel` already exists on the entity, the notification row, and the DTO. |
| **Persistence** | **None.** No migration. |
| **Frontend** | `components/notifications/notification-inbox.tsx` — CTA span, accessible-name extension, `line-clamp-3`, 44px dismiss, padding move. `app/admin/announcements/page.tsx` — body counter + helper, Link label helper. |
| **Tests** | `notification-inbox.test.tsx` (28 existing tests must stay green) + **new**: CTA renders when label+safe path present; **CTA absent when path is unsafe even though label is present**; CTA absent when label blank; accessible name contains both title and CTA; body carries `line-clamp-3`; **the CTA is not an anchor/button** (assert exactly one interactive element inside the row body). `app/admin/announcements/page.test.tsx` — counter renders and updates. |
| **Docs** | `docs/features/notifications.md` — CTA affordance contract, span-not-link rule, stored-vs-displayed split, the 160-char soft target. |
| **Rollout** | Zero risk. No API change, no data change, frontend-only, and **0 announcement rows exist in production** so nothing currently renders through this path. Deploy order irrelevant. |

**⚠️ The must-not-regress test is the nesting one.** A future contributor's instinct will be to make the CTA a `<Link>`. Write the test that fails if they do.

### RELEASE B — Campaign Feedback (ships after Release A closes)

### Slice 2 — Campaign persistence + API `[BACKEND, MIGRATION]`

| | |
|---|---|
| **Backend** | `CampaignFeedbackResponseEntity` (`@JdbcTypeCode(SqlTypes.ARRAY)` + `columnDefinition = "text[]"`, per `NoteEntity.java:49-51`); repository; `PrimaryBlocker`/`QuizIssue`/`PlanIssue` enums (**no `CampaignId` enum** — a `CAMPAIGN_ID` string constant, §G); `CampaignFeedbackService` (submit, status read, **`isOpen()` close-boundary check against the configured `closesAt`**, §G); `CampaignFeedbackController` (`GET /feedback/campaign`, `POST /feedback/campaign` — **no `{campaignId}` path param**, `@PreAuthorize("hasAnyRole('USER','ADMIN')")` matching `FeedbackController`); DTOs (`CampaignFeedbackStatusResponse` now carries `campaignOpen: boolean` alongside `submitted: boolean`); a named exception subclass per convention (e.g. `CampaignClosedException` → 409, distinguishable from generic validation 400s so the frontend can render the CLOSED STATE rather than an error toast). `AccountPurgeService` purge call. **`application.yaml`** — `notelib.campaign.study-friction-2026-09.closes-at` property (§G). |
| **Persistence** | `V148__campaign_feedback_responses.sql` — **confirmed free as of this plan** (v0.155.0 adds no migration; re-check immediately before writing the file since a second in-flight release could still claim it). Plain `CREATE INDEX`, never `CONCURRENTLY`. |
| **⚠️ Empty array, never null** | `primary_blockers` is `TEXT[] NOT NULL`, but §E/§17 explicitly permit a valid **free-text-only** submission with zero blockers selected. `NOT NULL` accepts an empty array, so the DDL is correct — but **the service must write `new String[0]`, not `null`**, on that path, or the one submission shape the minimum-validity rule allows fails with a constraint violation. Precedent: `UserEntity.java:63-65` (`focus_subjects`, NOT NULL, defaulted `new String[0]`). |
| **Tests** | **⚠️ At least one `MockMvc` test issuing a REAL request with `.contentType(MediaType.APPLICATION_JSON)` and a body** — CLAUDE.md is emphatic, and `v0.119.0` shipped a feature that could not make one successful request while 2,182 tests passed, because controller tests called handlers **as methods**. Pattern exists in `NoteControllerTest`. Plus: **a free-text-only submission persists an empty blocker array, not null**; duplicate submit returns 200 not 500; empty submission 400; unknown enum value 400; conditional answer with unselected parent rejected; free text over 2000 rejected; cross-user isolation; **submit after `closesAt` is rejected distinguishably (409, not a generic 400/500) and does not insert a row**; **status read after `closesAt` reports `campaignOpen: false`**; **a user who already submitted, then submits again after `closesAt`, still gets 200 already-submitted, never 409** (the precedence case, §H); **all `closesAt`-dependent tests override the property via `@TestPropertySource`/`@DynamicPropertySource`, never assert against real wall-clock time**; the migration is exercised by `NativeQueryPostgresIntegrationTest` (real PostgreSQL, real Flyway). |
| **Docs** | `DATA_MODEL.md` — new section. ⚠️ **`DATA_MODEL.md` currently documents neither `notifications`, `announcements`, nor `feedback` itself** (only `feedback_image`, `:280`). Adding the campaign table without at least noting that gap continues the drift. |
| **Rollout** | **Backend must deploy before or with the frontend.** New endpoints only — no existing form removed, renamed, or made required — so an old frontend is unaffected. **This is additive, so the two-direction breakage rule does not bite**; state that explicitly in the release notes rather than leaving it unsaid. |

### Slice 3 — Campaign page `[FRONTEND]`

| | |
|---|---|
| **Backend** | None. |
| **Frontend** | `app/feedback/page.tsx` (auth-gated); `components/feedback/campaign-feedback-page-client.tsx`; `components/feedback/campaign-option-row.tsx` (the `role="checkbox"` full-row control, §E.1); `lib/api.ts` additions. Reuses `PageHeader`, `BackLink`, `Button`, `Card`, `getSelectionCardClassName`. **Not** added to sidebar, tab bar, Settings, Help or Dashboard nav. |
| **Tests** | `lib/api-feedback.test.ts` — **pins the request shape** (method, path, `Content-Type`, body). ⚠️ A component test that mocks `lib/api` proves nothing about the request; `v0.119.0`'s defect was a missing `Content-Type` invisible to 2,182 passing tests. Component tests: mutual exclusivity both directions; conditional reveal and strip-on-deselect; minimum valid submission; empty blocked; thank-you replaces form; already-responded renders on load; **closed state renders on load when `campaignOpen: false`**; **closed state renders in place of the form when a submit is rejected 409 mid-session**; **closed-state copy differs from already-responded copy** (distinct strings, not a shared component reused blindly); free text survives a "Nothing major" click. |
| **Docs** | `docs/features/feedback-system.md` — a **new, clearly separated** campaign section. ⚠️ Must state the §1 lock: the existing modal is unchanged and remains the user-initiated channel. |
| **Rollout** | Ships after slice 2. **The route is live but unreachable until an announcement is published** — which is the intended soft launch: verify `/feedback` by direct URL before anyone is invited. |

### Slice 4 — Admin read path, docs sweep, checkpoint `[DOCS + SQL]`

| | |
|---|---|
| **Backend/Frontend** | **None.** |
| **Deliverables** | `docs/claude-plans/campaign-feedback-read.sql` (the 8 read-only queries, §I). Backlog Index rows for: this plan file (**required** — CLAUDE.md kickoff step 8 demands every `docs/claude-plans/` file carry a row), the deferred admin UI, `POST /feedback` rate limiting, unwritten `FeedbackStatus` values. A `[CHECKPOINT — due deploy + 21 days]` row asking: **how many responses did the campaign actually collect, against 405 active users and a 1.8% observed inbox click-through floor (not an engagement rate — see A3)?** — with the tightened conclusion (owner decision, 2026-09-22): **if it is ≤5, bell-only campaign invitations should not be treated as a sufficient research channel when broader learner reach is required.** A small number of thoughtful responses may still make the channel useful for lightweight qualitative research — ≤5 is not itself a verdict that the channel is worthless, only that it cannot carry a reach-dependent question. The Stage E email question becomes the real decision only if the *reach* need, not the raw count, requires it. |
| **Rollout** | Docs only. Lands in the same release. |

**⚠️ Slice 4 is not optional bookkeeping.** §A3 ships this feature ahead of its own reach evidence, which is exactly the condition CLAUDE.md's signoff gate requires a `[CHECKPOINT]` for.

### Analytics

**No new analytics events.** Stated explicitly rather than silently omitted. `AnalyticsEventType` has no announcement/notification/feedback-submission events today, and `notifications.md:331-334` records that announcement funnels, CTR dashboards and campaign attribution are **deliberately not built**. The response count is itself the measure. If an event is ever wanted, it must be added to the Java enum first.

---

## N. Test plan

**Existing baseline: 28 tests in `notification-inbox.test.tsx` already cover most of the generic matrix.** Listed below so new work is not duplicated and existing coverage is not mistaken for a gap.

### Generic notification

| Case | Status |
|---|---|
| Informational announcement, **no destination** | ✅ `:395` *"marks a CTA-less notification read from its card body without navigation"* |
| Actionable announcement **with destination** | ✅ `:382` |
| Whole-card click → mark read → close → navigate | ✅ `:382`, `:178`, `:194` (mobile) |
| **CTA click** | **NEW** — the CTA is inside the Link; assert it is not separately interactive |
| Dismiss X → no navigate, no mark read | ✅ `:409` |
| Mark-read behavior + badge delta | ✅ `:306`, `:318`, `:340`, `:282`, `:356` |
| Keyboard navigation | ✅ `:441` |
| **CTA renders when label + safe path** | **NEW** |
| **CTA absent when path unsafe but label present** | **NEW** ⚠️ the defence-in-depth case |
| **CTA absent when label blank** | **NEW** |
| **Accessible name contains title AND CTA (WCAG 2.5.3)** | **NEW** |
| **Body carries `line-clamp-3`** | **NEW** |
| **Exactly one interactive element in the row body** | **NEW** ⚠️ the anti-nesting guard |
| **Dismiss meets 44px** | **NEW** |
| Mobile rendering (sheet path) | ✅ `:194` sets `matchMedia` `matches: true` |
| Unsafe CTA renders no link | ✅ `:113` |
| Admin body counter | **NEW** (`announcements/page.test.tsx`) |

### Campaign — frontend

Direct `/feedback` navigation (authenticated) renders the form · unauthenticated redirects · primary blocker selection toggles · **"Nothing major" clears all blockers** · **selecting a blocker clears "Nothing major"** · **free text survives both** · quiz conditional reveals/hides, including the new "Some questions or answers seem incorrect" option · plan conditional is single-select (selecting a second replaces the first) · content conditional reveals · missing-feature conditional reveals · **deselecting a parent strips its conditional answers from the payload** · optional free text alone is a valid submission · one blocker alone is a valid submission · **empty submission blocked with helper text** · thank-you replaces the form in place · already-responded state renders on load · **closed state renders on load when the campaign has closed** · **closed state renders in place of the form on a 409 from a late submit** · **status-fetch failure fails soft to the empty form, not an error screen** · "Back to studying" href is `/dashboard` · **request shape pinned in `lib/api-feedback.test.ts`** · option rows are ≥44px and keyboard-operable · radiogroup arrow-key navigation.

### Campaign — backend

**⚠️ A real `MockMvc` request with `.contentType(MediaType.APPLICATION_JSON)` and a body** (not a direct handler call) · duplicate submission returns 200 with already-submitted, **never 500** · concurrent duplicate hits the unique index and is caught · unauthenticated 401 · cross-user isolation on the status read · malformed payload 400 · unknown enum value 400 naming the field · free text over 2000 rejected · conditional answers with unselected parent rejected · short text over 200 rejected · `null` vs empty array handled · **submit after `closesAt` returns a distinguishable rejection (409), never a 500, and inserts no row** · **an already-responded user who submits again after `closesAt` gets 200 already-submitted, NOT 409** (duplicate check precedes the close check, §H) · **status read after `closesAt` reports `campaignOpen: false` regardless of whether the caller already responded** (GET's `submitted`/`campaignOpen` are independent fields; the frontend's own precedence, §E, checks `submitted` before `campaignOpen`) · **an authenticated user who never received the announcement can still submit while open** (owner decision #5) · **`closesAt`-dependent tests use `@TestPropertySource`/`@DynamicPropertySource`, never real wall-clock time** · migration applied and queries `PREPARE`d by `NativeQueryPostgresIntegrationTest` · account purge deletes campaign rows.

### Regression — the existing Send Feedback modal

**⚠️ §1's lock needs a test, not just a promise.** `POST /feedback` unchanged (body still `{ message }` only, `X-Page-Url` header still honoured, `X-Feedback-Id` response header still set) · `send-feedback-widget.test.tsx` green · all four inline prompt components green · image upload path untouched · `AdminDashboardService.getRecentEvents()` still returns only `feedback` rows and **never** campaign rows.

---

## O. Documentation impact

| Doc | Change | Why |
|---|---|---|
| `docs/features/notifications.md` | **Required.** CTA affordance contract; **span-not-link** rule with the nesting rationale; stored-vs-displayed body split; 160-char soft target; the 44px dismiss. | Generic behavioral change; this file is the source of truth for inbox doctrine |
| `docs/features/feedback-system.md` | **Required.** New campaign section, clearly separated. Must restate the §1 lock and the *no rate limit because the unique index is stronger* decision. | New user-visible behavior |
| `docs/architecture/DATA_MODEL.md` | **Required.** New `campaign_feedback_responses` section. ⚠️ Note that `notifications`, `announcements` and `feedback` are **all currently undocumented** there (only `feedback_image`, `:280`) — flag the gap even if this release only closes its own part. | New table |
| `docs/product/ROADMAP.md` | **Required.** Backlog row for this plan file (kickoff step 8), the `[CHECKPOINT]` row, and rows for the deferred admin UI / rate limit / unwritten statuses. | Process gate |
| `RELEASES.md` | **Required.** Must state the additive-only API posture and the deploy-order note. | Always |
| `docs/releases/vX.Y.Z.md` | **Required** at signoff. | Always |
| `docs/features/navigation.md` | **Check, likely no change.** Only if it enumerates routes — `/feedback` is deliberately **not** a nav destination (§7, §28). | Verify, don't assume |
| `docs/ui-standards.md` | **Probably none.** The full-row `role="checkbox"` pattern is a new primitive shape; add a short note **only if** it is reused beyond this page. | Do not over-generalize from one use |
| `docs/features/admin-dashboard.md` | **Check.** Slice 1 changes the announcements admin form. | Verify |
| `docs/claude-plans/attention-notifications-email-expansion-stage1.md` | **Add one line** noting Stage C is reopened with a real backlog (§L). | Prevents a future reader concluding Stage C was empty twice |

### ADR required? — **NO**

Nothing here is an architectural boundary decision. Measured against the two existing ADRs:
- `ADR-001` governs the Note metadata axes — a cross-cutting taxonomy read by multiple resolvers.
- `ADR-002` governs quiz answer identity — a contract spanning the LLM boundary, four question stores and 115,333 rows.

This feature adds **one table with one unique index, one page, two endpoints, and a render change to one component.** The duplicate-response policy reuses doctrine already written down in `notifications.md`; the separate-table decision is a normal data-modelling judgment with its reasoning recorded here and in the feature doc. The close-boundary decision (§G) is a config property and a service-level `if` check — a data-modelling/config judgment, not an architectural contract. **Writing an ADR for it would dilute what an ADR means in this repo.** The invitation/interaction doctrine belongs in `notifications.md`, where the rest of the inbox doctrine already lives.

**Owner-confirmed, 2026-09-22: no new ADR, unless the tightened implementation unexpectedly introduces a genuinely cross-cutting architectural contract.** Nothing in this pass's tightening (dropping the `CampaignId` enum, adding the close boundary as config) rises to that bar — both are narrower than what was already judged sub-ADR. If implementation surfaces something that does cross that line, say so explicitly rather than silently writing the ADR or silently skipping it.

---

## P. Implementation routing

**Applying the CURRENT `CLAUDE.md` routing table honestly gives TWO answers, not one — and per owner decision #2, those two answers now also correspond to two separate releases, not two slices of one.**

### RELEASE A (Slice 1) → **CLAUDE CODE, implement inline**

> *"Frontend-only additions ≤ ~50 LOC, no new infrastructure (e.g. add a `GuidanceTip`, fix a prop, add a CSS class) → **Claude Code** — implement directly. Too small to justify a Codex prompt."*

Slice 1 is frontend-only, adds no infrastructure, touches **2 source files + 2 test files**, and is materially: one `<span>`, one `aria-labelledby` change, three Tailwind classes, and a character counter copied from an existing implementation. The tiebreaker confirms it — this is not a case requiring `AGENTS.md` anti-drift rules applied across many files.

### RELEASE B (Slices 2 + 3) → **CODEX, Long mode, one prompt**

> *"New features touching backend (new endpoint, migration, service logic) → **Codex** — write prompt first."*
> *"Multi-system changes (frontend + backend together) → **Codex**."*
> *"Refactors or additions touching > 5 files or > ~100 LOC → **Codex**."*

All three trigger. Slices 2+3 span a migration, ~8 new backend files, a new page with three components, API-layer additions, and two test suites — comfortably >5 files and >100 LOC.

**Ship them as ONE Codex prompt, not two.** The frontend cannot be written without the DTO shape the backend defines, and splitting would produce two prompts that must agree on an enum vocabulary — exactly the drift `AGENTS.md` exists to prevent.

### RELEASE B (Slice 4) → **CLAUDE CODE** (docs + SQL only)

### ⚠️ STAGE 1 STOP — DISCHARGED FOR RELEASE A, STILL HOLDS FOR RELEASE B'S ACTUAL BUILD

The original Stage-1-stop ("no Codex prompt written, nothing implemented") is **discharged by owner approval, 2026-09-22** to the extent the owner's own required-next-steps (§12) ask for: Release A's implementation-ready file/test/doc list is presented below for a final approval checkpoint, and Release B's Codex prompt is written and handed over ready-to-copy. **Neither is executed in this pass** — Release A still stops for an explicit owner go-ahead immediately before implementation (§12.B), and the Codex prompt is prepared but **not dispatched** (§12.C). "Do not implement yet" (owner's own words) governs both releases equally.

### Verification tier (pre-declared, per CLAUDE.md's release gate) — now per-release, since M/P split into two releases

**Release A: one `advisor()` call on the diff.** Frontend-only, no new infrastructure, no persistence, no permission/money/quota semantics touched. This is the whole gate.

**Release B: escalate to ONE scoped cold agent, falsification-framed.** Trigger: *"money, quota or production-data semantics changed"* is **not** literally met, but **a new endpoint plus a new table storing user-submitted content is a new persistence surface**, this release adds the **first** admin-readable structured user input, AND it is now also the first release to enforce a time-bound business rule (the close boundary) at two independent call sites that must agree — exactly the class of "an invariant that must hold across two places" CLAUDE.md's gate is built to catch. Hand the cold agent a tight file list and the specific claims this plan makes to falsify: (1) `POST /feedback/campaign` actually re-checks `isOpen()` and does not rely on the frontend; (2) the unique index, not a pre-check, is what prevents duplicates; (3) a free-text-only submission persists `String[0]`, not `null`; (4) `campaign_id` is never client-suppliable.

**The full three-agent pressure test is NOT warranted for either release** — no permission substrate, no cross-user read, no money/quota semantics. **⚠️ And per CLAUDE.md, no tier exercises transport: the `MockMvc` real-request test and the `lib/api-feedback.test.ts` request-shape test are owed at EVERY tier, including the cheapest.**

**Release size:** Release A is 1 slice (minimal, cheap to verify). Release B is 3 slices across 1 routing path (one Codex prompt) plus 1 Claude-Code docs/SQL slice — smaller than the originally-folded 4-slice/2-path release this table was written against, precisely because the split removes Release A's verification cost from Release B's critical path. **Do not fold additional items into either release.**

---

## Q. Owner checkpoint — RESOLVED 2026-09-22, except item marked OPEN at the end

```
ACTIONABLE ANNOUNCEMENTS + CAMPAIGN FEEDBACK
STATUS: Stage 1 audit approved with refinements, 2026-09-22. All items below are
RESOLVED unless marked OPEN. This plan is not re-audited from scratch — repo
findings and the architecture map (§A-D) are accepted as-is; only the sections
these decisions touch were changed.

Existing Send Feedback:
  KEEP, entirely unchanged. No survey questions added, no new required fields,
  no change to POST /feedback or its one-field body. A regression test pins this.
  The campaign's already-responded state points learners BACK to it, which is
  what keeps the two jobs visibly separate.

Linked notification CTA:
  RENDER ctaLabel — it is already stored, already copied onto every notification
  row, already in NotificationResponse, and rendered NOWHERE. 57 of 57 production
  rows carry a cta_label no one has ever seen. Render-only fix: no migration,
  no API change, no DTO change.
  ⚠️ As a NON-INTERACTIVE <span> inside the existing body <Link>, never a nested
  link or button. Blue text + "→", not a filled button. Extend aria-labelledby
  to include it (WCAG 2.5.3, ui-standards.md:169).

Whole-card navigation:
  ALREADY SHIPPED — notification-inbox.tsx:220-231 already marks read, closes the
  panel and navigates; dismiss is already a sibling outside the hit area; 28 tests
  already pin it. Only remaining gap: the card's px-4 py-3 padding is outside the
  Link's hit area. One class move.

Notification body density:
  BOTH (spec option C). line-clamp-3 at render (21 existing line-clamp usages;
  line-clamp-3 is the body/preview convention) + a character counter and a ~160-char
  SOFT target in Admin. Keep the 1000-char column and validator — tightening an
  existing API constraint is breaking in both directions. Document the
  stored-vs-displayed split. No "Read more", no detail page.

Campaign route:
  /feedback — CONFIRMED AVAILABLE (36 top-level app routes, none is feedback; the
  only "/feedback" string in the frontend is the API path at lib/api.ts:3226).
  Passes both CTA validators unchanged. Single route, NO {campaignId} path param
  (tightened per owner decision #3 — see below); campaign id is a backend STRING
  CONSTANT, not an enum, stored on every response row for the unique index. NO
  campaign table, NO campaign entity, NO campaign registry.

Permanent navigation entry:
  NO. Not in sidebar, not in the mobile tab bar, not in Settings, Help or Dashboard nav.

Primary campaign question:
  "What gets in the way when you study with NoteLib?"
  Supporting instruction: "Choose any that apply."

Survey structure:
  ONE SCREEN, adaptive. No wizard, no steps, no progress bar. Conditional
  follow-ups appear inline beneath their parent option.

Conditional follow-ups:
  FOUR, all optional. Quiz issues (multi-select, 7 options — owner ADDED "Some
  questions or answers seem incorrect" as the first option, 2026-09-22, on real
  production evidence from the v0.155.0 correctness incident; stays a CONDITIONAL
  diagnostic, not a new top-level blocker) · Paid plan (SINGLE-select radiogroup,
  5 options — "best describes it" is choose-one, and multi-select would destroy
  the exact distinction §13 exists to preserve) · Missing feature (short text
  200) · Content subject (short text 200, plain text NOT a catalog selector).
  "Something else" relies on the final free-text field — no duplicate inline
  textarea. Deselecting a parent strips its answers, enforced client-side AND
  server-side.

Screenshot upload:
  NO. feedback_image has 0 rows in production across the entire life of the
  feature — the existing upload path has never once been used. The Send Feedback
  modal keeps its support.

Campaign response persistence:
  CREATE A NEW MODEL — campaign_feedback_responses (V148 — confirmed free: v0.155.0
  in flight adds no migration; re-check immediately before writing the file since a
  second in-flight release could still claim it). Reuse is impossible because §23 requires GROUP BY over
  blockers and feedback.message is unstructured text — the exact failure the one
  real production feedback row demonstrates, where structured context was
  stringified into prose. Extension is wrong because it merges two lifecycles and
  collides with incident §R's separately-scoped plan for that same table.
  Selections stored as TEXT[] (4 existing precedents; the repo has ZERO join-table
  or @ElementCollection precedent anywhere). No behavioural snapshot columns —
  user_id makes every one of them joinable live.

  CAMPAIGN IDENTITY TIGHTENED (owner decision #3, 2026-09-22): NO CampaignId Java
  enum. A one-member enum is a constant wearing a registry's clothes, and implies
  the exact multi-campaign/schema/CMS shape being explicitly avoided. Use a named
  String constant instead (CAMPAIGN_ID = "STUDY_FRICTION_2026_09"), validated only
  against itself since nothing else exists to validate against. The campaign_id
  DB column stays (needed for the unique index and provenance) — dropping the
  column was never proposed, only the Java enum wrapping it. PrimaryBlocker /
  QuizIssue / PlanIssue KEEP their enums — those have 5-9 real members the UI
  renders and a service switches on, which is what actually earns an enum.

  CAMPAIGN CLOSE (owner decision #4, 2026-09-22): the campaign is BOUNDED, not
  perpetually open, reversing the earlier "campaign availability stays open
  indefinitely" recommendation. Represented as a single configured application
  property (notelib.campaign.study-friction-2026-09.closes-at, read via @Value
  into an Instant, same shape as the existing LLM_MODEL_* config values) — NOT a
  campaign table, NOT a status column, NOT a scheduler, NOT an admin control.
  BACKEND IS THE ENFORCEMENT AUTHORITY: GET /feedback/campaign reports
  campaignOpen:false once past closesAt, driving a CLOSED STATE; POST
  /feedback/campaign independently re-checks isOpen() before every insert and
  rejects a late submission distinguishably (409), so a direct POST after the
  UI's own close check cannot create a row — this was the owner's explicit
  concern, stated verbatim: "otherwise someone could POST directly after the UI
  says the campaign ended, which would make our 'bounded research' semantics
  cosmetic." Announcement expiry (stops the INVITATION showing) and campaign
  close (stops NEW RESPONSES) remain two independent boundaries — closing the
  campaign is what actually matters; letting the announcement expire does not by
  itself stop responses.

Duplicate response policy:
  ONE RESPONSE PER USER PER CAMPAIGN, enforced by a UNIQUE (user_id, campaign_id)
  index and DataIntegrityViolationException caught as a SUCCESSFUL NO-OP — never
  an existsBy pre-check, per notifications.md:18-36 and NoteCollectionService:952.
  That one mechanism answers double-submit, multiple tabs, revisit, and repeated
  notification clicks together. No rate limiter needed; the index is a stronger bound.

Admin response review:
  OUTCOME D — no new admin UI in v1. A committed read-only SQL file
  (docs/claude-plans/campaign-feedback-read.sql, 8 queries) following the
  established docs/curriculum/review-set-reshape-read.sql pattern.
  Sized on evidence: 405 active users, a 1-of-57 CLICK-THROUGH FLOOR (not an
  engagement rate — see reach, below), 3 feedback rows ever, no admin feedback
  LIST endpoint exists at all, and no pagination anywhere in the admin surface.
  §23 explicitly allows D. CHECKPOINT SEMANTICS TIGHTENED (owner decision #9,
  2026-09-22): revisit is triggered by "response volume OR repeated campaign
  usage making SQL/manual review materially inefficient" — NOT an automatic ">50
  responses ⇒ build an Admin UI" rule. Volume past ~50 is evidence this may be
  happening, not a product requirement to build an Admin UI by itself.

/announcements page:
  NO

/notifications page:
  NOT IN THIS FEATURE

Email/Resend refactor:
  NOT IN THIS FEATURE

Communication-service architecture:
  NOT IN THIS FEATURE

ADR required:
  NO. One table, one index, one page, two endpoints, one render change, one
  config property, one service-level close check. The duplicate-response
  doctrine is already written in notifications.md. Measured against ADR-001
  (cross-cutting taxonomy) and ADR-002 (a contract spanning four question stores
  and 115,333 rows), an ADR here would dilute what an ADR means. OWNER-CONFIRMED
  2026-09-22, with the standing caveat: unless the tightened implementation
  unexpectedly introduces a genuinely cross-cutting architectural contract —
  nothing in this tightening pass (dropping the enum, adding config-driven
  close) crosses that line.

Implementation routing:
  SPLIT INTO TWO RELEASES (owner decision #2, 2026-09-22 — not just two slices
  of one release; a real release boundary, each independently kicked off/
  verified/signed off):
    RELEASE A — Actionable Announcements (Slice 1: CTA + clamp + a11y + admin
            counter) → CLAUDE CODE inline (frontend-only, no new infrastructure,
            no backend/API/migration change, 2 source + 2 test files).
            Provisional version: v0.156.0 (next-free at approval time; confirm
            at actual kickoff).
    RELEASE B — Campaign Feedback (Slices 2+3: campaign backend + page) →
            CODEX, Long mode, ONE prompt (migration + new endpoints +
            multi-system + >5 files); Slice 4 (docs + read SQL) → CLAUDE CODE.
            Ships AFTER Release A closes. Provisional version: v0.157.0.
  ⚠️ STAGE 1 STOP DISCHARGED TO THE EXTENT OWNER APPROVAL COVERS: Release A's
  implementation-ready file/test/doc list is presented for a final approval
  checkpoint (§12.B of the owner's decision message); Release B's Codex prompt
  is written and handed over ready-to-copy (§12.C) but NOT dispatched. Neither
  release is actually implemented in this pass.
  Pre-declared verification, now per-release: Release A gets one advisor() call
  on the diff. Release B escalates to ONE scoped cold agent (falsification-
  framed) — trigger: new persistence surface + first admin-readable structured
  user input + a time-bound invariant enforced at two call sites that must
  agree. NEITHER release warrants the three-agent test.
  ⚠️ A real MockMvc request test and a lib/api-feedback.test.ts request-shape
  test are owed at EVERY tier — no tier exercises transport (v0.119.0).

OWNER DECISIONS — RESOLVED 2026-09-22:

  1. REACH — RESOLVED: SHIP AS PLANNED (option a). Rationale is NOT "one
     response is infinitely more than zero" (that framing is removed) — it is:
     implementation cost is relatively low; structured qualitative feedback has
     high information value at NoteLib's current scale; the generic Slice 1
     improvements remain valuable even if campaign volume is small. Keep
     ANNOUNCEMENT non-badge-eligible. Explicitly ruled out: making announcements
     badge-eligible; adding notification impression/open analytics for this
     feature; pulling email/Resend into Feature A; unblocking/modifying Feature
     B/Stage E. The +21-day checkpoint's conclusion is TIGHTENED: not "≤5 means
     in-app-only is not a viable research channel" but "≤5 means bell-only
     campaign invitations should not be treated as a SUFFICIENT research
     channel WHEN BROADER LEARNER REACH IS REQUIRED" — a small number of
     thoughtful responses may still make the channel useful for lightweight
     qualitative research; ≤5 is not on its own a verdict that the channel is
     worthless.

  2. RELEASE SHAPE — RESOLVED: SPLIT, as two real releases (Release A / Release
     B above), not merely for convenience. Release A ships frontend-only, no
     backend/API/migration change (confirmed still true of current repo state).

  3. CAMPAIGN IDENTITY — RESOLVED: TIGHTENED. No CampaignId enum, no campaign
     registry/CMS/dynamic-schema shape implied. A named String constant
     (CAMPAIGN_ID) plus the existing campaign_id DB column for provenance and
     the unique constraint. See "Campaign response persistence" above for full
     reasoning.

  4. CAMPAIGN LIFECYCLE — RESOLVED: MODIFIED FROM THE ORIGINAL RECOMMENDATION.
     The campaign IS bounded (reversing "stays open indefinitely"). A single
     configured closes-at property, backend-enforced at both GET and POST, no
     campaign table/status column/scheduler/admin control. See "Campaign
     response persistence" above for the full closes_at design and the
     distinct CLOSED STATE copy (§E step 7b, §F).

  5. UNTARGETED SUBMISSIONS — RESOLVED: APPROVED AS RECOMMENDED, with the OPEN
     qualifier made explicit. Any authenticated user may submit WHILE THE
     CAMPAIGN IS OPEN, whether or not they were in the announcement audience.
     Once closed, nobody may create a new response regardless of path. No
     second audience/eligibility system.

  6. CHOICE ORDERING — RESOLVED: APPROVED AS RECOMMENDED. "Something else" at
     8, "Nothing major — NoteLib works well for me" at 9. No change needed —
     this plan already had them in this order.

  7. QUIZ-QUALITY FOLLOW-UP — RESOLVED: ADDED. "Some questions or answers seem
     incorrect" is now the first option under "What's not working with the
     questions?" (now 7 options), on real evidence from the v0.155.0 incident.
     Stays a conditional diagnostic, not a new top-level blocker.

  8. PAGE COPY — RESOLVED: CHANGED. Page description now reads "...we'll use
     your feedback to decide what to improve next," matching the thank-you
     copy's existing "improve" verb.

  9. ADMIN RESPONSE REVIEW — RESOLVED: OUTCOME D CONFIRMED, checkpoint
     semantics tightened from ">50 responses ⇒ build Admin UI" to "volume or
     repeated usage making SQL/manual review materially inefficient ⇒
     reassess." Volume is evidence, not a requirement, by itself.

  10. MAJOR LOCKS — RESOLVED: ALL CONFIRMED UNCHANGED (see the full list in the
      owner's decision message; every item was already this plan's position).

  11. ACTIONABLE ANNOUNCEMENT FINDINGS — RESOLVED: APPROVED AS FOUND. Not
      re-litigated.

  ⚠️ OPEN — THE ONE GENUINELY NEW IMPLEMENTATION-LEVEL QUESTION THESE DECISIONS
  CREATE: decision #4 requires an actual closes-at TIMESTAMP, and none has been
  set. "Please determine the simplest repo-consistent representation" (answered
  — a config property, see above) is a different question from "what date."
  This plan does not pick one — a research-campaign collection window is a
  product/timing call (how long to leave it open before reading results),
  not a technical one. Needs an explicit value (e.g. "N weeks after Release B
  ships," or a fixed calendar date) before the Codex prompt's application.yaml
  entry can carry a real value instead of a placeholder.

DO NOT IMPLEMENT YET.
```

---

## Appendix — audit questions (§31), answered from current source

| # | Question | Answer |
|---|---|---|
| 1 | Announcement persistence model | `announcements` (`V140`) via `AnnouncementEntity`. `DRAFT→PUBLISHED→ENDED`. Title 255, body 1000, ctaLabel 64, ctaPath 512, audience, audienceValue, publishedAt, expiresAt. One index on `(status, created_at DESC)`. **0 rows in production.** |
| 2 | Notification persistence model | `notifications` (`V139`). UNIQUE `(recipient_user_id, dedup_key)` **is** the idempotency guarantee; plus a recipient/unread index and the `V143` partial inbox index. No FKs declared. **57 rows, all `REVIEW_SET_UPDATE`.** |
| 3 | Announcement → notification delivery | `publish()` resolves the audience **synchronously**, then fans out on `notificationFanOutExecutor` (core 1/max 2, bounded, AbortPolicy) in chunks of 500, **one insert per recipient**. Title/body/CTA are **COPIED and never re-read**. Not `@Transactional`, deliberately. |
| 4 | Destination representation | `cta_path`, a same-origin relative path. Validated on **write**, again on **deliver**, re-checked on **render**. |
| 5 | Link label representation | `cta_label` VARCHAR(64), both-or-neither with `cta_path`. **Stored, copied, transmitted — and never rendered.** |
| 6 | Does tapping mark read? | **Yes** — `markRead` on body activation (`:225-228`), individually, optimistic with rollback. Never mark-all-read on open. |
| 7 | Does tapping navigate? | **Yes**, when `ctaPath` passes `toSafeRelativePath`: marks read → closes the panel → navigates. Otherwise a `<button>` that only marks read. |
| 8 | Dismiss vs mark-read | Fully independent. `read_at` = awareness; `dismissed_at` = inbox visibility. Both idempotent. Dismiss never reads, reads never dismiss, and dismissal alters no product state. |
| 9 | Which types use whole-card navigation | **Both and all** — the behavior is a property of having a safe `ctaPath`, not of the type. Both live types (`ANNOUNCEMENT`, `REVIEW_SET_UPDATE`) use it. |
| 10 | Bell dropdown primitive | Desktop: a hand-rolled absolutely-positioned `<section>` (`w-96`, `max-h-[32rem]`). Mobile: `AppModal variant="sheet"`. **Both render the same shared `rows` block.** |
| 11 | Mobile behavior | Bottom sheet, edge-to-edge (`px-0`), `max-h-[85dvh]`, `rounded-t-2xl`, promoting to a centered `sm:max-w-[420px]` dialog above `sm`. Focus trapped and restored; Escape and backdrop handled by `AppModal`. |
| 12 | Existing body clamps / overflow rules | **None on the notification body.** `line-clamp` is an established convention elsewhere (21 usages) but is absent here. |
| 13 | Does Admin What's New preview the notification? | **No.** No form-side preview. The only CTA rendering is a post-save list row printing `{ctaLabel} → {ctaPath}` (`:380-384`). |
| 14 | Are Link label and Link path consumed by learner rendering? | **Path: YES. Label: NO.** The headline finding. |
| 15 | Is there a `/feedback` route? | **No.** 36 top-level route dirs, none is `feedback`. The only `"/feedback"` string is `lib/api.ts:3226`, the API path. |
| 16 | How is Send Feedback invoked? | Three variants. `"icon"` — one global mount in the app-shell header. `"inline"` — four contextual prompts gated on localStorage seen-tips + a session cap. `"floating"` — **zero production call sites**. No route allow/deny list; placement is per-mount-site. |
| 17 | Where is free-text feedback persisted? | `feedback` (`V31`): `id, user_id, email, message(text), page_url(text), status, created_at`. Screenshots in the 1:1 `feedback_image` (`V95`). **3 rows; 0 images.** |
| 18 | How do admins inspect feedback? | **There is no admin feedback list endpoint.** `AdminFeedbackController` exposes only `GET /admin/feedback/{id}/image`. Text is visible solely through `AdminDashboardService.getRecentEvents()`, hard-capped at the **5 newest** rows, unpaginated. |
| 19 | Can existing persistence support structured campaign responses? | **No.** `message` is unstructured `text`; §23's per-blocker counts cannot be produced from it. Demonstrated by the one real production row, whose structured context was stringified into prose. |
| 20 | Is a separate campaign model cleaner? | **Yes** — §G. It also preserves incident §R's separately-scoped plan for `feedback`. |
| 21 | Which selection primitives to reuse | `Checkbox`'s **visual language** (not the component — nesting), `getSelectionCardClassName` (`lib/clickable-card.ts:8-29`), the `role="radiogroup"` + roving-tabindex pattern from `regenerate-scope-modal.tsx:151-197`, `Button`, `Card`, `PageHeader`, `BackLink`. **No textarea, radio, or form-field primitive exists** — all inline by convention. |
| 22 | Which success pattern to use | In-place replacement of the form body with a success panel, as `send-feedback-widget.tsx:293-301` does (`border-emerald-500/20 bg-emerald-500/10`). `ToastMessage` exists but is used for admin-style transient confirmations. |
| 23 | Anti-spam / rate-limit patterns | Four in-memory fixed-window limiters in `security/` (auth, AI, OCR, invitation) — `ConcurrentHashMap` + `synchronized`, **no Bucket4j/Redis**, so limits multiply per instance. **`FeedbackService` has none.** Not needed here: the unique index is a stronger bound. |
| 24 | CSRF / auth / security patterns | **CSRF disabled**; stateless JWT; `@AuthenticationPrincipal AuthenticatedUser` → `user.userId()`. ⚠️ **`/admin/**` is NOT path-matched in `SecurityConfig`** — it falls through to `.anyRequest().authenticated()`, and per-class `@PreAuthorize` is the *only* thing gating admin endpoints. |
| 25 | Is the campaign authenticated-only? | **Yes.** `.anyRequest().authenticated()` covers it by default; the controller should carry `@PreAuthorize("hasAnyRole('USER','ADMIN')")` matching `FeedbackController`. |
| 26 | What happens on direct `/feedback` navigation? | Authenticated → form, the already-responded state, or the CLOSED state (⚠️ **UPDATED 2026-09-22**, owner decision #4 — the campaign is now bounded, see §G/§Q). Unauthenticated → the existing protected-route redirect with neutral login messaging. Any authenticated user may submit **while the campaign is open** (§24, owner decision #5). |
| 27 | Does announcement expiry need changing? | **No.** Expiry is decided on read inside `findVisibleInbox` and already works, unchanged. ⚠️ **UPDATED 2026-09-22:** campaign availability is NOT kept indefinitely open as originally recommended — owner decision #4 bounds it with a separate, backend-enforced `closesAt` config value (§G). Announcement expiry and campaign close remain two independent boundaries; neither derives from the other. |
| 28 | Which generic changes are safe without touching Feature B? | **All of GEN-1…GEN-8.** All are frontend render/authoring changes. None touches `NotificationService`, the category/type taxonomy, dedup keys, retention, the badge policy, email, or any producer. |
| 29 | Blast radius | **Slice 1:** `notification-inbox.tsx`, `notification-inbox.test.tsx`, `app/admin/announcements/page.tsx`(+test), `docs/features/notifications.md`. **Slices 2+3:** new migration; ~8 new backend files; `AccountPurgeService`; `app/feedback/*`; 2–3 new components; `lib/api.ts`; `lib/api-feedback.test.ts`; `docs/features/feedback-system.md`; `DATA_MODEL.md`. **Nothing existing is modified except `lib/api.ts` (additive) and `AccountPurgeService` (one line).** |
| 30 | Does CLAUDE.md route this to Claude or Codex? | **Both** — see §P. Release A (Slice 1) → Claude Code inline. Release B: Slices 2+3 → Codex, one Long-mode prompt; Slice 4 → Claude Code. |

---

## §30 — Research limitation (required, verbatim intent)

**An in-app feedback campaign reaches only learners who return and open NoteLib often enough to see the invitation — and, for announcements specifically, who voluntarily open the bell, since announcements never produce a badge.**

Results must therefore be interpreted as:

> **Reported friction from reachable, current, returning learners.**

**Not** as:

> Complete explanation of why every churned learner left.

**This limitation is unusually sharp here, and the numbers should be stated alongside any result.** The only in-app measurement NoteLib has is 1 click-through out of 57 delivered notifications (1.8%, a click-through floor with no impression denominator — see A3) — and that was for a **badge-eligible** type, which announcements are not. A fully churned learner has, by construction, a near-zero probability of seeing the invitation at all.

**Do not present campaign results as churn diagnosis.** If the owner needs to reach churned learners, that requires email, which is Stage E, which is blocked (§L) — a Feature B decision, not something this feature can or should work around.
