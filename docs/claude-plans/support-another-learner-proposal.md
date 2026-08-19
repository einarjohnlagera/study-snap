# Proposal: Support Another Learner

**Status: DIRECTION RATIFIED 2026-08-14 — scope NOT ratified, no version opened, nothing authorized for implementation.**
**Self-contained; written for a product/architecture second opinion. No repo access needed.**

**Supersedes an earlier "Guardian profile" draft from the same day.** That draft modelled this as a new `PARENT`/Guardian **Profile Type**. The owner rejected that framing — see §3 — and the earlier draft was discarded rather than kept, because its central abstraction was wrong.

---

## 1. Context for an outside reader

NoteLib is a notes-first study workspace used mainly by Philippine board-exam takers. A learner captures notes, generates an AI "Study Pack" from them (summary, key concepts, flashcards, quiz), and practises with five quiz modes. Curators publish **Official Review Sets** learners can adopt.

**Audience:** of accounts with a profile type set, **70.9% are exam-takers, 27.1% students**. 375 all-time signups.

**The binding constraint is retention, and it is severe:** of 152 activated learners, **141 never came back at all**. Activation is 52.2%, and a production read established that adding more activated users is *not* a lever on returning-user counts.

**Plans:** Free / Plus / Pro, manual hosted checkout, **no auto-renewal**.

**Existing shape that matters here:**
- `ProfileType` (`STUDENT`, `BOARD_EXAM`, `TEACHER`, `PARENT`, `PROFESSIONAL`) drives dashboard emphasis, quiz-mode availability and some generation behavior. It does **not** fork entity tables. **`PARENT` exists as an enum value with zero implementation and zero users.**
- **Every read path is scoped by `ownerUserId`** — notes, sessions, `ConceptHealth` (the mastery signal), Progress.
- **Teachers already generate quizzes for other people**, via a separate `generatedQuiz` entity, deliberately never using student quiz sessions.
- **Shareable quiz links already exist** (`/quiz/{token}`).
- Subscriptions and monthly quotas are strictly per-user.

---

## 2. The job

> **Help someone else learn.**

The earlier draft framed this as *parents*. That was too narrow. The same job describes a **grandparent, an aunt or uncle, an older sibling, a tutor, a mentor, a review coach.** Building for one relationship when the opportunity is the whole job would bake an unnecessary constraint into the model.

**Working title: "Support Another Learner."**

> **Naming note, raised for the second opinion.** An alternative title, *"Learn Together"*, was considered and is probably wrong: it promises **peer, symmetric co-study**, whereas every relationship listed above is **asymmetric** — one person helps, one learns. "Learn Together" also collides with a separately parked "Study Buddy" idea. Recommend keeping the asymmetric name.

---

## 3. Ratified: this is NOT a new Profile Type

**Profile Type answers "how do *YOU* learn?"** Supporting someone answers **"who else are you helping?"** Those are independent axes and must stay independent.

This is the same correction `ADR-001` made when it stopped the single `course_program` field from meaning four different things and split it into four axes. Overloading `ProfileType` with a relationship would repeat a mistake this codebase spent three releases undoing.

**Two consequences worth stating, because they are easy to miss:**

1. **A supporter can also be a learner.** A parent revising for their own board exam is a real person. The enum model forbids that by construction; a separate axis permits it for free.
2. **The earlier "it can't be switched, unlike other profile types" question dissolves.** It was never a switch to disable — it is a different axis. (Profile type *is* switchable today, so a one-way `PARENT` value would have been a special case begging for exactly this split.)

---

## 4. Ratified: incremental rollout, not one relationship model

Deliver value before building authorization.

### Phase 1 — Help another learner
Reduce friction for someone creating learning material **for another person**.
**Explicitly NOT in Phase 1:** shared subscriptions · shared quotas · progress dashboards · any permissions model.
Smallest possible surface; immediate value.

### Phase 2 — Linked learners
Invitation, acceptance, relationship management.

### Phase 3 — Progress sharing
Readiness, `ConceptHealth`, cross-user dashboards. **Only once the relationship model exists.**

**Why this ordering is right:** Phase 3 is where the product's first-ever *cross-user read* lives. Every read path today is owner-scoped, so a supporter seeing a learner's `ConceptHealth` is an **authorization model**, not a feature. Deferring it means Phases 1–2 ship without touching authorization at all.

---

## 5. Ratified: notes stay private

If linked learners are introduced, a supporter sees:

- readiness
- progress
- quiz performance

A supporter does **NOT** automatically gain access to the learner's **personal notes**.

> **Notes are personal thinking. Progress is educational.** That distinction is deliberate and should stay deliberate.

**One reinforcement the ratification did not state, and it is the stronger argument: this protects the *product*, not only the learner.** If learners suspect a supporter can read their notes, they write less honestly — or stop writing. **Note capture is the foundation the entire system sits on.** A privacy line drawn here defends the core loop, not just a trust boundary.

---

## 6. Open questions and challenges

Each needs an answer before scoping. None is settled.

**Q1 — Does Phase 1 already largely exist?**
Teachers already generate quizzes for other people (`generatedQuiz`), and shareable quiz links already work. So "help someone make material for another person" may be a **packaging and discoverability** problem rather than new capability — which fits the owner's own framing of *"I don't want to keep adding features."*
*Recommendation: audit what a tutor can already do today before scoping Phase 1 as a build.* The gap may be that the good parts are gated behind the `TEACHER` profile and that nothing frames any of it as helping someone else.

**Q2 — Phase 1's value accrues to the LEARNER, not the supporter.**
A supporter who creates a quiz and then sees nothing has no reason to return; their retention loop **is** the progress view, which is Phase 3.
*Consequence: do not judge Phase 1 by supporter return rate*, or a working feature gets killed by the wrong metric. Phase 1 should be judged on whether material gets created and used at all.

**Q3 — Whose quota does a supporter-initiated generation consume?**
The *link* is free — it is metadata and costs nothing. **But generating a quiz costs real LLM tokens.** The owner has ratified that each learner keeps their own account and quota, so this is the one live cost question.
*Recommendation: the supporter's own quota.* They initiated it; it prevents a supporter draining a learner's small Free allowance; and it gives the supporter's own plan a coherent reason to exist. **Under this rule the pricing question dissolves entirely** — no new SKU, no per-learner charge, no proration.

**Q4 — How is a link established and broken? (Phase 2) — ✅ RATIFIED 2026-08-19: invite + accept, both directions, revocable from either side.**

Either party may send an invitation; **the other must explicitly accept before any link exists**, and either may revoke at any time. **Acceptance is the load-bearing half** — it is what closes the hole below. An opt-out notification would NOT satisfy this: under opt-out the relationship exists, and progress may be visible, before the learner has agreed to anything.

*Original recommendation, now ratified: mutual consent, revocable from both sides.* **Without mutual consent, anyone could claim a supporting relationship over any account by knowing an email address** — a serious privacy hole.

⚠️ **Learner-initiated-code-only was considered and rejected**, though it is the strictest option: it is structurally immune to the email-claim hole, but it requires the learner to act first, which fails exactly where the parent→young-child case needs it most.

**Q5 — Minors and the Data Privacy Act. (Phase 2/3) — ✅ RATIFIED 2026-08-19: age is collected AT LINK TIME, and a guardian consent step is required below the threshold.**

- **Age is collected when a link is formed, never at signup.** This keeps onboarding untouched, which matters concretely: onboarding is under measurement against a 62.4% baseline until `[CHECKPOINT — due 2026-09-11]`, and adding a signup field would confound that read.
- **The obligation attaches where the relationship is formed**, which is also where it is legally relevant. Minimal collection: a learner who never links is never asked.
- ⚠️ **`learnerLevel` was considered as a proxy and REJECTED.** `GRADE_SCHOOL` / `JUNIOR_HIGH` / `SENIOR_HIGH` do imply minors and cost nothing to read, but the mapping breaks in both directions — `PERSONAL_LEARNING` and adult re-takers are the obvious cases — and a proxy is weaker ground than a declared age if the obligation is ever tested.
- ⚠️ **Adults-only-first was considered and rejected** because it excludes the parent→child case, which is the motivating one.

**⚠️ ONE PIECE IS DELIBERATELY NOT DECIDED HERE, AND MUST NOT BE GUESSED IN SCOPING: the age threshold itself.** The mechanism is ratified; the number is a legal question under the Philippine Data Privacy Act and is **owner-owned, pending counsel**. Scope the consent flow so the threshold is configuration, not a literal, and do not ship a default that reads as a legal position.

*Original note: this is the item most likely to be discovered late and most expensive when it is.*

**Q6 — What does a supporter's own home surface look like?**
If someone is purely a supporter and not a learner, their Dashboard has nothing on it by construction, and will read as broken.

---

## 7. What this proposal does NOT change

- **No new Profile Type**, and no change to how existing profile types behave.
- **No shared subscription, no pooled quota, no per-learner charge.**
- **No sub-accounts.** A supported learner is a full, ordinary account with its own login and plan.
- **No change to the five-mode quiz contract** (locked).
- **No change to `ConceptHealth` integrity** — it moves only from genuine assessment, locked since `v0.37.0`. A supporter *viewing* readiness must never write to it.

---

## 8. The evidence problem, stated fairly

**There are zero users on the unimplemented `PARENT` profile**, and the roadmap already gates a much smaller Parent item — a "Readiness Digest" — behind `[EVIDENCE, then DECISION]`.

**The honest case FOR:** a supporter is a stakeholder with a *recurring* reason to return — checking someone's progress is a durable habit loop. Against a product where 141 of 152 activated learners never came back, that is a real strategic argument.

**The honest case AGAINST:** it does not fix the core loop. It adds a relationship model and a new audience on top of a loop that has not been proven to hold anyone. The ratified vision orders work **Trust → Habit → Community**.

**Note the tension with Q2:** the retention argument depends on the *progress view*, which is **Phase 3** — the most expensive phase. So the strategic case for this work does not pay out until the end of the rollout. That is not an argument against the phasing; it is an argument against justifying Phase 1 on retention grounds.

---

## 9. Demand signal — buy evidence before building

**Precedent in-product:** onboarding already records demand for something that does not exist — its "no plan yet" branch lets a learner request an Official Review Set NoteLib has not built, recording the request without promising anything.

**Design:**
1. An entry point that records interest and shows an honest **"not available yet"** — no waitlist theatre, no implied date.
2. It **must not set `profileType = PARENT`**, which would strand the account in a profile with no implementation.
3. One analytics event, so the rate is measurable against signup volume (~120/month).
4. Optionally capture how many learners they would support — that number sizes the feature if it is built.

**⚠️ Placement constraint — do NOT put this in onboarding before 2026-09-11.** Onboarding is under active measurement until then against a 62.4% completion baseline following its `v0.73.0` redesign, and adding an option there would contaminate that read.
*Recommendation: `/pricing` and/or the Dashboard*, neither of which is under measurement. Onboarding becomes available after 2026-09-11 if a stronger signal is wanted.

**Pre-commit the decision rule before looking at the number.** A threshold chosen after seeing the data is not a threshold. Form: *"if fewer than N% of signups over 30 days express supporter intent, do not build — and revisit only on new evidence, not enthusiasm."* **The owner sets N before the instrument ships.**

---

## 10. What we want from a second opinion

1. **Is the axis split right** — supporting-someone as a capability rather than a Profile Type? What breaks under each choice?
2. **Q1** — is Phase 1 mostly a packaging problem? If so, is it still worth a release, or is it a discoverability pass?
3. **Q3** — is charging generation to the supporter correct, or does it create a perverse incentive?
4. **Is §5's line right** — progress and results, never notes? Too restrictive to be useful, or exactly right?
5. **Is the phasing correct**, given §8's observation that the strategic payoff only arrives in Phase 3?
6. **Sequencing:** with retention as the binding constraint and 141 of 152 activated learners never returning, is a new audience defensible now — or is this what you do *after* the core loop holds?

**Please push back hardest on §8.** The retention argument for supporters is the one we are most likely to be telling ourselves because we want to build this.
