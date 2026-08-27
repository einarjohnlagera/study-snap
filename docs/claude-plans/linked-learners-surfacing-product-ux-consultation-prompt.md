# Linked Learners — surfacing, discovery and the supporter loop: consultation prompt for product UX

**How to use this:** paste everything below the horizontal rule into a fresh product-UX session. It is
self-contained — it carries the product context, the owner's direction, the real production state, and the hard
technical constraints, so the consultant does not design something we cannot build.

**Why it is shaped this way.** The owner arrived with four proposals. An audit against real code on
**2026-08-27** found that **two of them describe behaviour that already ships**, one would corrupt a
pre-committed measurement, and the fourth is blocked by a data-comparability problem rather than a privacy one.
The same audit found a live contradiction on the public landing page that nobody had noticed. The "Constraints"
section exists to stop a well-reasoned proposal from being built on any of those wrong premises.

**Companion documents** (do not paste; reference if the consultant asks for depth):
`docs/claude-plans/support-another-learner-proposal.md` (the original direction),
`docs/features/linked-learners.md`, `docs/features/shareable-quiz-links.md`,
`ROADMAP.md` Backlog Index rows `[CHECKPOINT — due 2026-09-19]` and `[CHECKPOINT — due 2026-10-13]`.

**Indexing note:** per the Backlog Index invariant, planning documents need an index row. Finished one-off
consultation prompts fall under the narrow *release artifact* exemption, so this file does not need its own row —
attach any resulting decision to the existing *Support Another Learner* row instead.

---

# NoteLib — why has nobody formed a learning connection, and where should we surface it?

I need product-UX judgment on a feature that shipped completely and has **zero adoption**, and on the owner's
plan to fix that through marketing.

## What NoteLib is

A notes-first study workspace for Philippine learners — board-exam reviewers, college students, teachers,
professionals. Learners capture notes, generate AI Study Packs from them, and practise with quizzes.

> **Positioning: your notes become your study system.**
> We sell the learning system. Features simply support that promise.

## What shipped

Across three releases (2026-08-19 to 2026-08-26) we built **Linked Learners** — a supporter → learner
relationship, so a parent, tutor or friend can help someone else study:

- **Phase 1 — Quiz for someone.** Any onboarded user can generate a quiz from their own note and share it as a
  public link. The recipient needs **no account and no relationship**; they answer in-browser and see a score.
- **Phase 2 — the connection.** Invite by email, the other party accepts. Either side can initiate; either side
  can revoke. Guardian consent is required below a configured age threshold.
- **Phase 3 — the supporter progress read.** Once a connection is `ACCEPTED`, the supporter sees a
  privacy-safe progress page for that learner: readiness %, quiz performance, streak and study days, plan
  progress. **Never the learner's notes or study material.** This is the product's first cross-user read.

**Production state, read 2026-08-26, seven days after Phase 3 released: `linked_learner_relationships` is
completely empty. Zero rows of any status.** Nobody has invited anybody. On ~381 total accounts, and needing
two people to act in concert, that is weak evidence rather than proof of no demand — but it is the whole reason
for this consultation.

A pre-committed checkpoint reads that table on **2026-09-19** with a kill criterion attached: *if zero
relationships have reached `ACCEPTED`, the demand hypothesis is unsupported — stop investing in this direction.*

## What the owner wants

The owner raised four ideas, then a fifth (marketing). Verbatim, lightly trimmed:

1. *"I think we should use quiz quota if a user generates a quiz for someone. This will ensure fairness across
   users."*
2. *"When I view a quiz for someone, I think it is still best to show the linked learner companions. Having this
   UI same as with teachers definitely defeats the purpose of the Teacher profile type."*
3. *"I'm still thinking whether we surface 'Quiz for someone' even though an account doesn't have a linked
   learner companion or not. Surfacing only when there's a linked learner companion is a good reason to get back
   the button we removed from the previous release."*
4. *"When again are we going to see the dashboard for our linked learner companion? I actually think this
   learner companion is like how Apple Health is being shared across. With that, learners can compete on
   themselves."*
5. *"I think this also needs a place in our landing page so it is also visible to other users. And possibly in
   our help page as well. Then I'll just market this through our FB page to market it more to our followers."*

---

# Constraints — verified against real code and production data, 2026-08-27

Everything below was checked against the repository or a production read. Four items contradict something the
owner believed was true.

## 1. Idea 1 already ships — generating a quiz for someone already spends quiz quota

`GeneratedQuizService.assertQuizCreditAvailable` reads `user_usage.challenge_quiz_generations` against the
monthly Challenge Quiz limit (**Free 20 / Plus 100 / Pro 200**) and increments the same counter on success.
Helping someone has always drawn down the user's own allowance. There is no fairness gap to close.

**But nobody is told.** The counter is labelled *Challenge Quiz* on every surface — the usage card, the plan
API, pricing. A parent who never takes a Challenge Quiz cannot tell what they are spending.

**And the two meters are ordered against the user.** Generation is metered as above; creating the *share link*
is metered separately at **Free 3 / Plus 10 / Pro unlimited**. The share-link assertion
(`assertShareLinkQuotaNotExceeded`) has exactly **one call site — link creation** — and is not consulted on the
generation path, so a Free user can pay the LLM cost for a 4th, 5th and 6th quiz and only then discover they
cannot share any of them. The cheaper limit is the one enforced last. (Verified by call-site search, not
inferred from the limits.)

Separately, generating a Study Pack also produces a practice quiz the supporter will never take. The owner has
considered and accepted that as part of the Study Pack cost. Not in scope.

## 2. Idea 2's "teacher UI" is mostly an artifact of the owner's own admin account

On the quiz preview page:

- `canExportDocx = authUser?.role === "ADMIN" || authUser?.profileType === "TEACHER"` gates **both** the Export
  button **and** the guidance tip reading *"format it your way before distributing to students."*
- `canShareQuiz = Boolean(authUser)` — no profile or plan component whatsoever.

The owner is an ADMIN, so they see the teacher surface. For an ordinary student or parent account, that page
carries **no** classroom vocabulary at all: the header, the regenerate menu and the "Share with Someone" card
are already recipient-neutral. The Teacher profile type is not being diluted. DOCX export and multi-version
exports remain deliberately teacher-gated; only the share link is open to everyone.

## 3. The real gap under idea 2: the supporter loop never closes

`POST /quiz/share/{token}/results` takes **no authenticated principal and persists nothing**. Even when the
recipient is a signed-in linked learner, the play leaves no trace — not in the learner's own history, not in the
supporter's progress view, nowhere. A supporter generates a quiz, sends it, and never learns whether it was
opened, let alone how it went.

Two constraints on any fix:

- **Concept identity is per Study Pack.** The learner does not own the supporter's note or pack, so shared-quiz
  results can never feed the learner's readiness (`ConceptHealth`). Recording results needs its own record type.
- The house rule *"no session is created until the user is authenticated"* is scoped to the **anonymous mini
  quiz on public note pages**, not to shared quiz links. An **authenticated-recipient-only** result record is
  therefore not blocked by doctrine. Anonymous plays would stay anonymous.

## 4. ⚠️ The landing page currently tells the public this feature does not exist yet

This is the finding that most likely explains the zero.

`frontend/app/page.tsx` renders a **"For Parents & Guardians"** section carrying a **"Coming Soon"** badge and
an **"I'm interested"** waitlist button:

> *"Help your child stay on top of their study schedule — track progress, see weak areas, and keep the review
> loop going between sessions."*

That describes the shipped Phase 3 progress view almost exactly. The only public surface that mentions
supporting a learner is actively telling visitors it is not available, and harvesting an email-less interest
click instead of a signup. It has not been updated since the three releases landed.

Two consequences worth designing around:

- Any Facebook campaign the owner runs lands traffic on this page. **Marketing the feature before this section
  is corrected would send interested parents to a "Coming Soon" notice.**
- The click fires a `GUARDIAN_INTEREST` analytics event, which has been collecting for some time. **Read that
  count before deciding anything here** — it is the cheapest evidence available. But read it for what it is: a
  loose upper bound on curiosity, not a demand signal. It fires from a section labelled *Coming Soon*, so a
  click means *"I would want this"*, not *"I would use what shipped"*; the interested state is local component
  state, so the same person on two visits is two events; and it collects no address, so nobody can be contacted.
  (The 2026-09-19 checkpoint's remark about a demand signal never being built refers to one specific proposal in
  the original direction doc, not to this event.)

## 5. `/help` is behind authentication — it is not a marketing surface

The Help Center calls `requireAuthenticatedOnboardedUser`. Facebook followers cannot reach it; only signed-in,
onboarded users can. It is still worth adding to, but for a different job: telling **existing** users the feature
exists. It is not part of the acquisition path.

Help currently has 14 sections, including profile guides for Student, Board Exam, Teacher and Professional.
**There is no supporter or parent guide, and no section explains "Quiz for someone" at all** — the
*Export & Sharing* section covers exporting a quiz session as a PDF and never mentions shareable quiz links.

So the discovery picture is: **public surface says "coming soon", in-app surface says nothing.**

## 6. Idea 4's dashboard already ships

The supporter progress view exists at `/linked-learners/{relationshipId}/progress` — four cards: readiness %
with mastered/due/not-started counts; recent average and best quiz score plus Study Packs reviewed; current and
longest streak with study days this week; and practised/total plan items. It is reachable from a **"People you
support"** section on the Dashboard and from the Learning Connections page.

The owner has not seen it because it renders only for accounts with a live supporter-side relationship, and
there are none. With zero rows in production, that Dashboard section has never rendered for anyone.

## 7. The Apple Health analogy is structurally permitted; the "compete" half is not comparable

Mutual visibility is permitted at every layer that could have blocked it, and all four were read: the `V120`
table constraints (the only check blocks self-linking; the live-row uniqueness index is keyed **per direction**
on `supporter_user_id, learner_user_id`); the invitation path (which deliberately performs no relationship or
account lookup at all, because that lookup was the account-existence oracle we closed); the acceptance path
(which guards self-linking only); and the insert itself, whose `ON CONFLICT` target is that same per-direction
index. Two people can therefore invite each other and each end up supporter of the other, seeing the other's
progress. **Nothing forbids it and nothing surfaces it — no copy, affordance or flow suggests reciprocal
linking, and it has not been exercised end to end** (production has zero relationships of any kind).

**Competition is the problem, and the objection is metric validity rather than privacy.** Every aggregate in the
progress view is computed over the learner's **own** notes and packs. Two people with different libraries have
non-comparable readiness percentages and averages — there is no shared denominator, so a leaderboard would rank
library composition, not effort. A learner with three easy notes would outrank one grinding a 77-note board
review. Secondarily, guardian consent exists because some of these learners are minors, and social comparison is
a different risk surface than a guardian reading a progress card.

Self-comparison over time is comparable and safe — but that is the existing My Progress surface, not a
connection feature.

## 8. Idea 3 would corrupt the 2026-09-19 measurement

Gating "Quiz for someone" on having a connection makes forming a connection the **price** of sharing a quiz.
Invitations that then appear would be instrumental, not demand — manufacturing a false pass on a kill criterion
the owner pre-committed to, three weeks before it is read. This is not "wait for measurement before shipping";
it is "do not break the instrument."

It also gates on a state **no user has ever reached**, so the feature's reach would drop to zero on deploy.

The recorded reason for keeping it ungated stands independently of adoption: a shared-quiz recipient needs no
account and no relationship, so sharing must not depend on one existing.

Note that the menu item is not literally ungated — it appears for non-teacher accounts on notes with a ready
Study Pack, and is disabled until the user's email is verified. "Ungated" refers specifically to *relationship*
gating.

If what the owner actually wants is the removed button back, that is a **placement** question and is separable.
The argument that moved it was that beside *Start Quick Review* it read as an alternative to studying — an
avoidance path on the surface where retention is decided. That argument says nothing about who should see it.

## 9. Binding rules any proposal must respect

- **The privacy line is absolute.** A supporter sees readiness, progress and quiz performance — **never** the
  learner's notes, note titles, Study Pack prose, concept names, subjects or collection titles. Aggregate counts
  and states only. Exposing free text is a new privacy decision, not a DTO convenience.
- **Every cross-user read re-verifies an `ACCEPTED` relationship.** Revocation cuts access immediately.
- **Viewing must never write `ConceptHealth`**, which has moved only from genuine assessment since v0.37.0.
- **Invitations stay one-at-a-time by principle.** The quiz *link* is the many-recipient mechanism.
- **Learning Connections is a capability, not a profile mode.** Nothing is gated on `ProfileType`, and no
  supporter profile type exists. (`PARENT` exists as an unimplemented enum value with zero users; do not wire it
  up as the mechanism.)
- **No sub-accounts, no shared quota or subscription.** A supported learner is a full ordinary account.
- Upgrade CTAs go through the shared plan config; taxonomy fields use comboboxes, never free text.

---

# Open questions — what we most want your judgment on

1. **Is this a discovery problem or a demand problem?** The public surface says "coming soon" and the in-app
   surface says nothing. Is zero adoption sufficiently explained by that, or is there a real demand question
   underneath that marketing would only mask?
2. **The landing page section.** It has a working interest-capture mechanism that has been collecting clicks.
   Does it become a live feature section, or does the waitlist framing still earn its place while adoption is
   unproven? If it becomes live, what does a parent need to see to believe it is for them — the invite flow, the
   progress view, or the quiz-sharing capability that needs no account on the other end?
3. **What is the acquisition unit?** A connection needs **two** people to act. The Facebook audience is
   presumably one of them. Does the landing page sell to the supporter (*"see how your child is doing"*) or to
   the learner (*"let someone help you"*)? Does the shareable quiz link — which needs no account at all — work
   as the wedge that gets the second person into the product?
4. **Where does "Quiz for someone" belong**, given it is a support/share action rather than learner practice,
   and that the practice row was judged the wrong home for it? Is there a placement that gives it visibility
   without putting an avoidance path beside *Start Quick Review*?
5. **Closing the loop — we are asking about the surface, not the mechanism.** Constraint 3 already fixes the
   shape (authenticated recipients only, its own record type, `ConceptHealth` cannot take it). Given that: is
   this the thing that makes a connection worth forming, and what does the supporter see afterwards that stays
   inside the privacy line? If you think the mechanism itself is wrong, say so — but it is a technical
   constraint, not a preference.
6. **Quota legibility.** How should we name a meter that is spent by two different jobs — the user's own
   Challenge Quiz and a quiz made for someone else — and where should the share-link cap be disclosed so it is
   not discovered after the LLM cost is already paid?
7. **Help Center.** Given it reaches only signed-in users: a supporter guide of its own, or coverage folded into
   the existing Export & Sharing section?

# Out of scope

- Any leaderboard or cross-learner comparison built on the current aggregates (see constraint 7).
- Gating quiz sharing on a relationship (see constraint 8).
- A supporter `ProfileType`, sub-accounts, shared quota, or multi-recipient invitations.
- Exposing any learner free text to a supporter.
- Changing what `linked_learner_relationships` means — two dated checkpoints read that table.
