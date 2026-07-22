# New Capability Ideation — Areas NoteLib Has No Version of Today

**Session type:** standalone open-ended capability ideation (planning only, no code)
**Baseline read:** SPEC.md §1–284, PLANS.md (full), ROADMAP.md §1–35, classification tiers from `docs/skills/roadmap-feature-audit.md`
**Deliberately excluded territory:** conversion/retention polish to existing surfaces (conversion-audit backlog), curriculum-driven Review Set auto-assembly (Smart Review Planning), and composition/app-shape work on existing pieces (App Shape backlog). Everything below is a capability the product has **no version of** today.

Classification uses the four tiers defined in `docs/skills/roadmap-feature-audit.md`:
- **Core Feature** — new user-facing capability that changes the product's capability surface (all of these are out of scope for v0.44.0, so per the audit skill they defer to a future roadmap section)
- **Polish** — quality improvement to an existing feature (mostly inapplicable here by design)
- **Future Enhancement** — valid and desirable but not the right time (scope, dependencies, or readiness)
- **Low-Priority Idea** — worth tracking, no clear timeline or strong signal

---

## 1. Ideas

### Idea 1 — Exam Date Countdown & Paced Review Plan

**What it is.** The learner sets a target exam date (Board Taker first; Student for finals). NoteLib then paces their **already-owned** material — notes, Study Packs, adopted Review Sets — across the remaining days: "12 concepts due this week, 4 subjects untouched for 10+ days, you're on track / behind for October 6." A countdown and a daily pacing target appear on the dashboard and inside Review Set detail.

This is explicitly **not** Smart Review Planning: no matching of public content to curriculum objectives, no auto-assembly, no generation. It only *schedules* what the learner already has. If they have nothing for a subject, the plan says so — it does not go find or make content.

**Problem it solves.** Board takers know their exam date months out but have no way to tell NoteLib about it, so the product can't answer the single question that dominates their study life: "am I going to be ready in time?" ConceptHealth's 3-day due-threshold already produces "what's due" — nothing converts that into "what's due *relative to a deadline*."

**Reuses.** `ConceptHealthService` (due-concept mechanic), TodayFocus (daily surface), Review Set detail's this-set dashboard (per-set readiness), profile onboarding (where the date is captured). Exam identity comes from the existing courseProgram config-map — a **combobox** of known exams (ALE, PNLE, LET, …) plus a date picker; never a freetext exam name.

**Genuinely new infrastructure.** A target-exam-date field (on profile, or per Review Set — design decision), and a small pacing-calculator service that spreads due/stale concepts across remaining days. No new content pipeline, no LLM calls.

**Extension note (Professional).** The same mechanic later covers certification-renewal / CPD exam dates for the `PROFESSIONAL` profile with no structural change — one concrete path to thickening that profile without inventing a separate system.

**Classification:** **Core Feature** (defer past v0.44.0). Tier direction: fits the PRO "personalization" rung — the countdown itself could be visible on all tiers (outcome-selling surface) with the paced plan as the PRO depth.

---

### Idea 2 — Shared-Quiz Results for Teachers

**What it is.** Teachers already send shareable quiz links. Today those links are fire-and-forget — the teacher never learns what happened. This adds a results roll-up per shared link: who attempted, score distribution, and which questions/concepts the group missed most.

**Problem it solves.** The teacher flow is Generate → View → Export, and the shareable link is the digital delivery path — but it's a one-way street. A teacher who shares a quiz gets zero signal back, so NoteLib is a worksheet printer, not a teaching instrument. Per-question miss rates are the thing a teacher actually reteaches from.

**Reuses.** Shareable quiz link entity, the shared `QuickReviewSessionEntity` model (students taking a shared link are real signed-in quiz sessions), teacher mode branching, `getQuizSessionModeLabel` for display.

**Genuinely new infrastructure.** Link-attempt attribution (session rows referencing the originating share link) and a teacher-facing results view with per-question aggregation. **Design constraint to respect:** public pages must not persist anonymous session state — so results only cover signed-in attempts, or the link flow gains an explicit teacher-issued named-respondent mechanism. That choice needs its own scoping pass; do not quietly relax the anonymous-session rule.

**Classification:** **Core Feature** (defer). Tier direction: natural extension of the Plus teacher story (PLANS.md already makes Plus the "complete teacher workflow" tier); attempt-volume depth could differentiate Pro. No quota numbers proposed.

---

### Idea 3 — Class Groups & Assigned Review Sets

**What it is.** A teacher creates a class group; students join with a code; the teacher assigns **already-published** Review Sets and sees an aggregate readiness board (per-student completion, class-level weak concepts). The bigger sibling of Idea 2.

**Problem it solves.** Teacher is a first-class profile with authoring and export workflows but no *relationship* to the students consuming their material. Every classroom use of NoteLib today is stitched together outside the product (links pasted into group chats, results never seen).

**Curation compliance.** Nothing new reaches students through a group: assignment is restricted to content the teacher has already published through the existing curation path. Groups are a *distribution and visibility* layer, not a content-creation layer.

**Reuses.** Review Sets, teacher/admin detection pattern, ConceptHealth aggregation (the class weak-concept board is per-student ConceptHealth rolled up), the same shared session model.

**Genuinely new infrastructure.** Three new entities (group, membership, assignment), a join-code flow, and an aggregate dashboard. This is the largest build in this document and is real new surface area — it should be gated on evidence of teacher traction (consistent with the earlier decision to gate bulk teacher work on teacher users).

**Classification:** **Future Enhancement** — Core Feature by nature, but the dependency (proven teacher demand, ideally via Idea 2 shipping first and showing link-results usage) makes it not the right time. Idea 2 is the cheap probe; this is the payoff.

---

### Idea 4 — Parent Readiness Digest (first PARENT capability)

**What it is.** A student (or board taker) invites a parent by email. The parent gets a read-only weekly view: study consistency (days active), subjects practiced, readiness trend, and — if Idea 1 ships — the exam countdown. **No access to note content, quiz answers, or scores on individual attempts.** Consent lives with the student: they initiate, they can revoke, and they see exactly what the parent sees.

**Problem it solves.** `PARENT` has existed as an enum value with zero implementation. In the Philippine market, parents frequently fund the subscription for a reviewee child — the payer currently has no product surface at all. A trust-respecting digest gives the payer a reason to keep paying without turning NoteLib into surveillance.

**Reuses.** Progress aggregation (already computed for the learner's own dashboard), `ProfileType.PARENT` (finally activated), the existing profile-switch UX pattern for the parent's own account setup.

**Genuinely new infrastructure.** An account-link entity with an invite/consent flow, a parent dashboard (read-only projection of existing aggregates), and a weekly email. *(Correction 2026-07-12: originally said this "depends on the notification channel from Idea 5" — that's wrong; a real retention-email system already ships today (`retention-emails.md`), so the weekly parent email can be a new email type on the existing `RetentionEmailScheduler` directly, with no dependency on Idea 5 landing first. See the correction note on Idea 5.)* Real new surface, but almost no new *computation*.

**Classification:** **Future Enhancement.** Right direction, wrong moment: it multiplies value only once learner retention itself is solid (the v0.44.0 thesis), and it should ride on the email infrastructure below rather than build its own.

---

### Idea 5 — Weekly Due-Concepts Email Digest (first out-of-app habit channel)

> **Correction (2026-07-12, Claude Code, verified against code and docs):** this idea's premise is
> false and its scope is significantly overstated. `docs/features/retention-emails.md` documents a
> real, already-shipped retention email system: `INACTIVITY`, `WEAK_CONCEPT`, and — the direct
> contradiction — a `WEEKLY_SUMMARY` email that already runs every Sunday 6pm with study-pack/quiz/
> adaptive-session/score content, plus a real scheduler (`RetentionEmailScheduler`, confirmed present
> in `backend/src/main/java/com/studysnap/backend/service/jobs/`), Resend delivery integration, daily
> send-budget management, opt-in/opt-out preference flags, unsubscribe flows, and bounce/suppression
> webhook handling. "No out-of-app re-engagement channel of any kind" is not true. Fable also cited
> `BillingUsageResetJob` as "the scheduled job pattern" for this — that job exists too, but
> `RetentionEmailScheduler` is the actually-relevant existing pattern and wasn't found/cited.
>
> **What's still real:** the *content* gap is genuine — the existing `WEAK_CONCEPT` email triggers on
> low accuracy after a completed Challenge Quiz, which is a different signal than ConceptHealth's
> due/stale-via-decay concepts (a correctly-answered concept that's now due again). A due-concepts-
> specific digest with a TodayFocus deep-link is still a legitimate, non-duplicate idea.
>
> **What changes:** this is no longer "build the first out-of-app habit channel" — it's "add one more
> email type to the already-shipped `RetentionEmailScheduler`/`EmailService` system, alongside
> `WEEKLY_SUMMARY` and `WEAK_CONCEPT`." Almost everything listed under "Genuinely new infrastructure"
> below already exists. This makes the idea **cheaper**, not weaker — but it also means Ideas 1 and 4's
> claim that they "depend on the email infrastructure below" is wrong in the same way: they can build
> on the *existing* retention-emails system directly and do not need this idea built first at all.

**What it is.** An opt-in weekly email: "You have 9 concepts due for review · Anatomy untouched for 12 days · 41 days to your exam," deep-linking into TodayFocus. NoteLib currently has **no out-of-app re-engagement channel of any kind** — every habit mechanic that exists (guidance tips, Companion mentor tips, TodayFocus) only fires if the user already showed up.

**Problem it solves.** The proven constraint is retention, and the product is structurally mute toward a user who hasn't opened it this week — exactly the user retention work needs to reach. The spaced-repetition-shaped due mechanic already computes the perfect email content; nothing sends it.

**Reuses.** `ConceptHealthService` due/stale computation, TodayFocus as the landing surface, the existing `AnalyticsEventType` pattern for open/click tracking (new enum values added first, per convention). *(Post-correction: also reuses the existing `RetentionEmailScheduler`, `EmailService`/Resend integration, `email_log` dedup/cooldown model, and Email Preferences flag pattern wholesale — this is a new email TYPE inside a shipped system, not a new system.)*

**Genuinely new infrastructure.** ~~A scheduled job (the `BillingUsageResetJob` pattern already exists for scheduled work), an email template + delivery integration, and opt-in/opt-out preference storage.~~ *(Corrected: the scheduler, delivery integration, and preference-flag storage all already exist. What's actually new is one email type/template — e.g. `DUE_CONCEPTS_DIGEST` alongside the existing three types — a trigger condition (due-concept count above some threshold), and a new Email Preferences flag. No new job infrastructure, no new delivery integration.)* ~~This channel is a **prerequisite investment** that Ideas 1 and 4 both want — building it once, digest-first, is the efficient order.~~ *(Corrected: Ideas 1 and 4 can build directly on the existing retention-emails system; this idea is not a prerequisite for either.)*

**Classification:** **Core Feature** (defer to the next retention-themed release) — *(downgrade candidate: given how much already exists, this may actually classify as **Polish** to the existing retention-emails feature rather than a new Core Feature; worth revisiting at scoping time against `roadmap-feature-audit.md`'s actual definitions rather than assuming Core Feature carries over from the original, incorrect framing)*. Tier direction: the digest itself should be free (it drives return visits, the thing the business needs); tier-specific depth can come later.

---

### Idea 6 — Photo Capture of Handwritten Notes

**What it is.** Snap photos of notebook pages inside Create Note; NoteLib turns them into an **editable note draft** the learner reviews and corrects before saving. Sold as the outcome: "Your paper notebook becomes your study library" — never as an OCR/AI feature.

**Problem it solves.** The core loop starts at Input, and SPEC's input options (write, paste, upload, topic-draft) all assume the material is already digital. For the actual primary audience — students in lectures, board reviewees in review centers — the canonical source is a paper notebook. Retyping is the single biggest tax at the top of the funnel, and no current backlog touches it.

**Curation/versioning compliance.** The extracted text is a private draft of the learner's *own* content, editable before save — exactly the existing topic-to-note contract ("generated note content is editable before save; no saved Note until the user chooses Save"). Nothing reaches other learners; nothing auto-generates a Study Pack.

**Reuses.** The Create Note draft-before-save flow, the existing note-status model, the existing async-generation UX pattern (Generating → Ready/Failed) for the extraction step, upload handling.

**Genuinely new infrastructure.** Image upload storage and a vision-extraction step in the LLM service layer (a new capability of `LlmStudyPackService`'s neighborhood, not a new pipeline). Per-photo cost is real, so it lands naturally as a metered capability in `FeatureGateService` — tier direction: a taste on Free, headroom on Plus/Pro, mirroring the topic-note-generation ladder. No quota numbers proposed.

**Classification:** **Core Feature** (defer). The strongest pure-acquisition idea here and the most differentiating against generic summarizers.

---

### Idea 7 — Listen Mode: Audio Review of Summaries & Key Concepts

**What it is.** A play button on a Study Pack: the summary and key concepts read aloud, with sequential playback across a Review Set. Sold as the outcome — "review on the commute, at the gym, with your eyes closed" — not as text-to-speech.

**Problem it solves.** Every review surface today requires eyes on a screen. Board reviewees commute; students walk between classes. Audio converts dead time into review time and doubles as an accessibility win (visual fatigue, low-vision users).

**Curation compliance.** Audio is a rendering of a pack the learner already has (own pack, or a public pack that already passed curation). No new content is created — same words, new medium.

**Reuses.** `StudyPackEntity` content as-is, Review Set ordering, the existing feature-gate pattern for metering.

**Genuinely new infrastructure.** A TTS pipeline with audio caching (regenerate only on Study Pack regeneration — the in-place versioning model makes cache invalidation clean), plus a player UI. Per-generation cost makes it a natural PLUS/PRO capability (interaction → personalization rungs).

**Classification:** **Future Enhancement.** Genuinely valuable, but it competes for the same investment slot as Idea 6 and loses: capture friction blocks the loop's entrance; audio improves a loop already running.

---

### Idea 8 — Concept Flashcards: Unscored Flip-Through + Printable Deck

**What it is.** A self-paced flip-card view over a Study Pack's key concepts (term → reveal explanation), plus a printable flashcard PDF export. **Explicitly not a quiz mode:** no session row, no timer, no scoring, no weak-concept writes, no entry via mode-selection. It is a Study Pack *reading surface* plus one more export template — the same category of thing as the summary tab, deliberately positioned outside the locked 5-mode contract.

**Problem it solves.** Key concepts exist as a static list; there is no active-recall way to use them that doesn't cost a metered quiz session. Flashcards are the study behavior half the audience already practices on paper.

**Reuses.** Key concepts data (no generation), the export pipeline and its existing plan limits (PDF path), Study Pack tab structure.

**Genuinely new infrastructure.** Nearly none — a flip UI and a PDF template. The cheapest idea in this document.

**Risk to manage.** Scope discipline: the moment anyone proposes scoring the flip-through or tracking "cards you got wrong," it has become a 6th quiz mode and violates the locked contract. The feature doc must state the no-scoring rule as a hard boundary at birth.

**Classification:** **Core Feature** (small; defer, but a strong candidate for the *next* release precisely because it's cheap and self-contained). Tier direction: on-screen flip free (static-content rung); printable deck rides the existing export limits untouched.

---

### Idea 9 — Offline Study Pack Access

**What it is.** Mark a Study Pack (or Review Set) for offline; summary and key concepts remain readable with no connection. Quizzes stay online-only — sessions, gating, and scoring all require the backend, and pretending otherwise creates sync nightmares.

**Problem it solves.** The core market pays for mobile data by the gigabyte and rides commutes with dead signal. PDF export is the current workaround, but it exits the product — offline in-app reading keeps the learner inside the loop (and pairs naturally with Idea 7's cached audio later).

**Reuses.** Existing pack read views; the in-place regeneration model gives a clean staleness signal (re-sync when the pack's updated-at changes).

**Genuinely new infrastructure.** Service worker + local cache + sync rules — meaningful frontend platform work, the first PWA-shaped investment in the codebase.

**Classification:** **Future Enhancement.** Real value, meaningful platform cost, and it should wait until there's evidence (analytics on mobile usage / export volume) that offline reading is the binding constraint rather than a plausible one.

---

### Idea 10 — Filipino Bilingual Interface (i18n Foundation)

**What it is.** UI chrome localization (navigation, buttons, empty states, guidance copy) starting with Filipino, behind a language preference. User content and generated content stay in the language they were authored in — this is *interface* i18n, not content translation.

**Problem it solves.** The product's stated market is Filipino learners and teachers; the product speaks only English. For the teacher and parent audiences especially (and the parent digest of Idea 4), interface language is a trust signal.

**Reuses.** Nothing structural — this is a horizontal concern.

**Genuinely new infrastructure.** An i18n framework in the Next.js app, string extraction across every surface, and a translation-maintenance obligation on every future PR. The ongoing tax is the real cost, not the initial build.

**Classification:** **Low-Priority Idea** for now — no user-reported signal yet that English UI blocks the current audience (instruction in PH higher-ed and board review is predominantly English). Track it; revisit if PARENT ships or analytics show drop-off patterns consistent with language friction.

---

### Idea 11 — Study Buddy Accountability Pairing

**What it is.** Two learners pair by invite code and see exactly one thing about each other: study-activity consistency (studied today / this week). No content sharing, no scores, no chat.

**Problem it solves.** Habit formation via light social accountability — the mechanic behind most successful streak products — with a shape so minimal it cannot violate curation-never-generation (no content ever crosses the pair).

**Reuses.** Activity aggregation already computed for Progress; the invite-code pattern Idea 3 would build.

**Genuinely new infrastructure.** A pairing entity and a tiny mutual-visibility surface.

**Classification:** **Low-Priority Idea.** Plausible, cheap, but no signal it's the right retention lever versus Idea 5's digest, which reaches the disengaged user directly. Record; don't build.

---

## 2. Explicitly Out of Scope (considered and rejected)

Named here so none of these get re-proposed later without knowing why they were declined.

- **Any 6th quiz mode, or scoring/tracking added to the flashcard flip-through (Idea 8).** The 5-mode contract in `EXAM_MODES.md` is locked. Flashcards survive only as an unscored reading surface; the moment they score, they die.
- **Learner-to-learner content exchange without curator review** — study-group shared note pools, peer annotations/comments on public notes, "share my pack directly to a friend" bypassing the Public Library curation path. All violate curation-never-generation. Ideas 2/3/11 are shaped specifically so that *no content* moves learner-to-learner outside the existing published-content path.
- **Any auto-assembled or gap-filling review plan** — "we noticed you're missing content for Pharmacology, here are matching public packs / we generated one." That is the Smart Review Planning bet (`docs/claude-prompt/fable-out/`), paused and owned elsewhere. Idea 1 schedules **owned content only** and reports gaps without filling them; that line is the whole reason it's proposable.
- **Conversational tutor / "ask questions about your notes" chat.** Reads as an AI feature (violates "sell outcomes, not AI"), and SPEC's positioning explicitly rejects chatbot framing. Also an ungated generation surface aimed at a learner — unreviewable by any curator by construction.
- **Auto-regeneration triggers** ("keep my Study Pack fresh when I edit," "refresh audio automatically"). The versioning model is explicit: regeneration is always user-confirmed. Idea 7's audio cache regenerates only on the user-confirmed pack regeneration event.
- **Freetext anywhere in taxonomy** — exam names (Idea 1), class subjects (Idea 3), professional roles (Idea 1's CPD extension). All must come from the existing config-map comboboxes; a new exam or role is a config change, never a user-typed string.
- **Public leaderboards / competitive rankings.** Social pressure mechanics with privacy exposure and no signal they fit a "calm, iterative" study product. The accountability shape that survives is Idea 11's private, symmetric, content-free pairing.
- **Prices, quota numbers, or conversion-lift claims for any idea above.** Tier *direction* only, per the FREE-static / PLUS-interaction / PRO-personalization philosophy. Every number is a scoping-time decision against `pricing-config.ts`.
- **Anonymous quiz attempts for shared-link results (Idea 2).** Public pages must not persist anonymous session state; that rule is not relaxed here. Idea 2's scoping pass must solve attribution *within* that rule (signed-in attempts, or an explicit teacher-issued respondent mechanism).

---

## 3. Recommended Next Step

Three ideas are strong enough to warrant a real scoping pass, in this order:

1. **Idea 5 — Due-Concepts Email Digest.** *(Correction 2026-07-12: the original reasoning here — "no out-of-app channel exists, this builds it for Ideas 1/4" — was factually wrong; a real retention-email system already ships, see the correction note under Idea 5. Corrected case: the proven constraint is retention, and a due-concepts-specific email reaches users who aren't opening the app, same as before — it just does it as a cheap new email type on an existing system rather than as new infrastructure. Still smallest scope of the three, arguably smaller than originally stated. It does NOT need to ship before Ideas 1 or 4 — they can build on the existing system independently.)*

2. **Idea 1 — Exam Date Countdown & Paced Review Plan.** Sharpest fit to the core Board Taker audience and the PRO personalization rung, built almost entirely on ConceptHealth/TodayFocus machinery that already exists. It also quietly becomes the first `PROFESSIONAL` thickening path (CPD dates) for free. Scoping must hold the "owned content only" line to stay out of Smart Review Planning's territory.

3. **Idea 6 — Photo Capture of Handwritten Notes.** The one acquisition-side capability with genuine differentiation: it attacks the top-of-loop friction (paper → digital) that every current backlog ignores, using the exact draft-before-save contract the topic-note flow already established. Scoping question to settle first: extraction cost per photo and where it sits in `FeatureGateService`.

Idea 8 (flashcards) is deliberately *not* in the top three despite being the cheapest — it's a strong filler item for whichever release scopes first, not a bet worth its own scoping pass. Ideas 2→3 (teacher results → class groups) form a coherent probe-then-invest sequence but should wait for a release themed on the teacher audience, consistent with the standing decision to gate teacher expansion on teacher traction.
