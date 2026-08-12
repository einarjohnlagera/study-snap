# Next-release candidates — consultation prompt for product UX

**How to use this:** paste everything below the horizontal rule into a fresh product-UX session. It is
self-contained — it carries the product context, the three candidates, and the hard constraints verified against
real code and the roadmap, so the consultant does not recommend something that is already blocked or that
breaks a measurement in flight.

**Status:** planning only. Nothing is implemented, no version is open. Everything in "Constraints" was verified
against code or roadmap documents on **2026-08-12**.

**Indexing:** needs a Backlog Index row in `ROADMAP.md` before it is committed.

---

# NoteLib — choosing the next release

I need product-UX help picking and shaping the next release of **NoteLib**, a study app for Philippine learners
(board-exam reviewers, college students, teachers, professionals).

## Where the product just got to

The last release redesigned onboarding, aimed at the funnel's largest leak: **132 learners — 35.2% of all
signups — verify their email and never finish onboarding.** It shipped as eight single-question screens and is
being judged by a dated checkpoint on **2026-09-11** (onboarding completion against a **62.4%** baseline).

**Retention context, because it shapes what is worth building.** The oldest constraint was "2.4% W1→W2
retention." A read found that figure counted only days 7–14 after a learner's first Study Pack and saw **3 of
the 11 learners who actually returned**; the wider reading is **~7.2%**. Both halves matter: the number
understated reality ~3.7×, *and* 141 of 152 activated learners never came back at all. Also measured:
**activation is 52.2%**, which structurally caps "get more users activated" as a lever on returning-user
counts, because returning = activated × rate and the comparison is rate-independent.

## The core learning loop, for context

A learner writes or generates a **note**, generates a **Study Pack** from it (summary, key concepts, quiz), then
practises. Practice modes, in intended order of depth: **Quick Review** (light recall) → **Challenge Quiz**
(harder, progressive) → deeper modes. The Study Pack page has tabs, one of which is the **Quiz** tab.

---

# Candidate 1 — Gate the Quiz tab behind Quick Review

## The owner's proposal

> The Quiz tab is shown upfront, which downplays Quick Review and arguably makes it useless — nothing requires
> a learner to do the lighter step first. Hide the Quiz tab until the learner has mastered Quick Review, then
> reveal it with something like *"You've unlocked the Quiz tab in this Study Pack."*
>
> Related: today we surface Challenge Quiz when a learner has *almost* mastered Quick Review. Change that so
> Challenge only surfaces on actual mastery.
>
> Also: when a learner has not answered all Quick Review questions, the end-of-session options are **"retry the
> incorrect"** and **"finish the review."** Keep retry, but replace "finish the review" with something like
> **"Review first"** that sends them back to the *note* to study before returning to Quick Review.

## The complication the owner then raised

The owner uses the Quiz tab in their own **marketing workflow** — they pull quizzes to post as daily
challenges — and does not want to master a Study Pack first in order to reach it.

**This is a curator-workflow need, not a learner need**, which suggests an exemption rather than abandoning the
gate. But it is unresolved, and it is the first open question below, because **if the tab is not gated at all,
this candidate loses most of its substance**: the Challenge-promotion half is blocked until 2026-09-30 (see
constraints), leaving only the "Review first" CTA — a small fix, not a release.

## Constraints — verified, do not design around these

**1. The Challenge-promotion change is blocked until 2026-09-30, and this is hard.**
`PostSessionNextStepService.java:56` promotes Challenge to the primary next step at **>= 4/5** on Quick Review.
That threshold is *exactly* what an open `[CHECKPOINT — due 2026-09-30]` measures, with a named kill criterion:
if Quick Review → Challenge conversion has not improved against the pre-June-2026 baseline, the 5/5 → 4/5
promotion is judged ineffective and the underlying *"motivation, not placement"* hypothesis reverts to
**unconfirmed**. Changing the threshold before that read destroys the ability to read it — we would be
measuring a rule already replaced. **The tab gating and the promotion threshold are separable; treat them as
separate decisions with different timing.**

**2. "Mastered" has no definition yet, and it becomes a gate.**
Candidate definitions: 5/5 in a single session; all concepts non-due via the app's `ConceptHealth` recency
model; sustained across sessions. Quick Review length varies by pack, so "5/5" means different things on a
5-question and a 20-question pack. A definition that is too strict traps learners with **no path to quizzes at
all**.

**3. "Review first" has a metrics dependency.**
The current *finish* action completes the session (`completeSessionIfNeeded(0)`). If "Review first" instead
navigates to the note, someone must decide whether the session **completes, pauses, or is discarded** — that
changes when `QUICK_REVIEW_COMPLETED` fires, and that event feeds the value-loop metric on the admin funnel.

**4. There is an existing curator predicate** (`profileType == TEACHER || role == ADMIN`) already used for
authoring permissions, if an exemption is the answer.

## What I want your judgement on

1. **The marketing tension.** Is a role-based exemption (curators always see the tab) the right resolution, or
   does an exemption that only the owner benefits from signal the gate is wrong for everyone? If you would drop
   the gate entirely, say what — if anything — should then make Quick Review feel like a real first step.
2. **Hide, or lock-with-reason?** An argument on the table: you cannot feel you have *unlocked* something you
   never knew existed, so a hidden tab that later materialises reads as a UI glitch rather than progress, while
   a visible-but-locked tab creates anticipation and teaches the progression upfront. Which is better here?
3. **How should "mastered" be defined**, given constraint 2 and that it gates access?
4. **Is a gate even the right instrument?** The stated problem is that Quick Review feels pointless because
   quizzes are always available. Gating is one fix; making Quick Review *more obviously valuable* is another.
   Which addresses the real problem?
5. **"Review first"** — is sending a learner back to the source note mid-session good practice, or does it
   break the session's momentum in a way that costs more than it gains?

---

# Candidate 2 — Make the Explore page public

## The owner's proposal

> Explore should be public and reachable by signed-out visitors, since the direction is for it to replace the
> Public Library. `/public/library` and `/collections/published#browse-all` are used in previous marketing
> posts — should we retain those pages, or redirect learners to Explore? And the landing page should probably
> say "Explore" instead of "Public Library" for consistency.

## Constraints — verified, and several of these already answer parts of the question

**1. This is already on the roadmap and already analysed.** It is the *"Discovery System — Public Front Door"*
Backlog Index row, plus a **dated amendment (2026-07-31) that is explicitly NOT ratified** in the repo's
engineering rules. So the gap is not that it was forgotten; it is blocked and awaiting decisions.

**2. The redirect question is partly settled already, and more strictly than expected.** Under the pending
amendment: subject-listing pages (`/public/library/{subject}`) and note-detail pages
(`/public/library/{subject}/{slug}`) are **never redirected, full stop** — that is where the SEO investment
lives. Only the **bare list page** (`/public/library`) is ever a legitimate redirect target, and only once
Explore has real anonymous rendering, canonical metadata and structured data, **and** a concrete SEO-parity
evidence bar clears. `/collections/published` is **not covered** by that text — genuinely open.

**3. Explore is authenticated-only today.** Making it public is a real chunk of work ("Stage 0"): backend
permit changes, a discovery-intent cookie, canonical and structured-data work. Every Discovery System source
route is already anonymous with real SEO investment; Explore is the exception.

**4. Sequencing collides with an open checkpoint.** There is a `[CHECKPOINT — due 2026-09-13]` on
Explore-driven engagement against a pre-launch baseline. The roadmap row flags, as an unresolved owner
decision, whether Stage 0 should wait for that checkpoint to close or add a viewer-type dimension to its
analysis plan first.

**5. The landing-page rename has a sequencing trap.** Renaming "Public Library" → "Explore" *before* Explore is
anonymous would point signed-out visitors at a login wall — strictly worse than today.

**6. A question has been waiting on the owner since 2026-07-31.** The amendment states that the owner should
explicitly confirm that this **narrows** rather than **reverses** an earlier recorded direction that "Public
Library is not absorbed or removed" — "rather than have it asserted silently." That confirmation gates the
rest.

## What I want your judgement on

1. **Is Explore-as-public-front-door still the right direction at all**, given the product's Trust → Habit →
   Community ordering puts community content last, and given the retention numbers above?
2. **The bare `/public/library` list page**: redirect to Explore once parity clears, or keep both indefinitely?
   What would a real "SEO parity" bar look like in practice?
3. **`/collections/published`** — genuinely undecided. Retain, redirect, or fold into Explore?
4. **Sequencing against the 2026-09-13 checkpoint** — wait for it, or proceed and add a viewer-type dimension
   to its analysis?

---

# Candidate 3 — Move the verification wall from the door to the payoff

## The owner's proposal

> Onboarding currently runs only after email verification. Move it earlier so a learner meets the product
> before meeting a wall. The owner adds: *"I don't mind if it costs us upfront — we could remove it once we
> have several paying users. Right now the most important thing is conversion."*

## Two variants, and they differ enormously in cost

**Variant A — reorder only. Costs nothing.** Run Screens 1–4 (profile, course/program, learner level, first
intent) before verification, and put the verification ask at the **generating** step. The learner then meets
the wall having chosen a profile, said what they study, picked a path, and standing one click from their first
Study Pack — rather than cold, immediately after signup. **No backend change**: the generation endpoints keep
`requireEmailVerified` exactly as-is, so no unverified account can trigger an LLM call.

**Variant B — full universal onboarding. Costs real money and opens a real hole.** Let unverified accounts
complete onboarding *including* generation, and require verification later, at some other gated action. This
needs the guard relaxed on `/notes/generate` and `/notes/{id}/generate`.

## Constraints — verified 2026-08-12

**1. Verification is currently the only thing between an anonymous person and free LLM generation.**
`AiRateLimitService` keys its bucket on `userId`, and the FREE plan quota is per-user — a throwaway account
resets both. Neither mechanism protects against account-farming. The verification gate is doing that job alone,
and Variant B removes it without a replacement.

**2. The upside is bounded and small.** Of 375 all-time signups, **366 verify — 97.6%**. The verification wall
costs **9 learners in total**. So the direct ceiling on this candidate is ~9 recovered learners, against
unbounded LLM exposure under Variant B. Any argument for B has to rest on an *indirect* effect — better
momentum, better completion downstream — not on recovering the 9.

**3. Variant A's benefit is behavioural, not mechanical**, and therefore unproven: the claim is that a learner
who has invested four screens is likelier to go verify than one asked cold. Plausible, unmeasured.

**4. The original framing of this idea was factually wrong**, which is worth knowing before re-deriving it: it
was proposed as *"there were LLM costs during onboarding, that is no longer true."* There still are — the
generating screen calls two LLM endpoints. Variant A works *because* it leaves that fact intact.

## What I want your judgement on

1. **Is Variant A worth doing at all**, given the measured ceiling is 9 learners? Or is the behavioural
   argument (invested learners verify more readily; the product proves itself before asking for anything)
   strong enough to justify it independent of that number?
2. **Is Variant B defensible?** The owner has said they will accept the cost for conversion. Name what would
   have to be true for that to be a good trade — and what abuse controls would have to exist first, given that
   per-user rate limiting and per-user quota both reset on a new throwaway account.
3. **Is "conversion" even the right frame here?** This lever moves signup → activated, not free → paid. If the
   underlying goal is paying users, say plainly whether this is the wrong place to spend a release.
4. **Sequencing:** Variant A touches the same eight screens `v0.73.0` just rebuilt, whose completion checkpoint
   reads on **2026-09-11**. Changing the flow before that date muddies the read — the checkpoint could not
   distinguish the redesign's effect from the reordering's. Should this wait for 09-11?

---

# The sequencing question, across all three

Four dated checkpoints fall between **2026-09-10 and 2026-09-30** — H1+H5 (with a kill criterion), the
onboarding redesign, Knowledge Impact, Explore engagement, and Challenge Quiz adoption. Two of them can retire
a hypothesis outright.

The internal lean is: **take candidate 1 now** (self-contained, does not touch the 09-30 threshold), and **hold
candidate 2 until after 09-13**, when the Explore checkpoint has reported. But candidate 1 may be much smaller
than it looks once the marketing tension is resolved, which could argue for a deliberate gap instead, or for
pairing it with carried backlog work.

**Candidate 3 has its own timing problem:** it modifies the flow whose checkpoint reads on **2026-09-11**, so
shipping it first would make that read unattributable — you could not tell the redesign's effect from the
reordering's. That argues for after 09-11 regardless of its merits.

**All three candidates collide with a checkpoint in some way**, which is itself worth a verdict: is the right
answer a deliberately small release, or no release at all until the September reads land?

**Please challenge this sequencing**, and say plainly if you think neither candidate is the best use of the
next release given what the numbers say.
