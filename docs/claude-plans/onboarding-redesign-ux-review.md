# Onboarding redesign — UX review and design direction

**Status:** design direction, not scoped work. No release is open against this.
**Companion:** `docs/claude-plans/onboarding-redesign-product-ux-consultation-prompt.md` (the brief this answers),
`docs/gpt-contexts/GPT_CONTEXT.md` (funnel numbers, positioning).
**Indexing:** this file and the consultation prompt share one Backlog Index row.

---

## First: a correction to the brief's own premise

The brief says the post-generation behaviour is **fixed** — *"generate first Study Pack → redirect directly to the Study Pack → let the learner decide."* It asks only what transition should wrap it.

**That is not what happens today.** Onboarding does not auto-start Quick Review (so that decision costs nothing — it was never the behaviour), but it also does not land the learner on their Study Pack. It ends on a **completion screen with three CTAs**: *Continue studying*, *Go to dashboard*, *Go to saved note*. The first already resolves to `/study-packs/{id}` — the destination exists and works.

So the thing standing between the learner and their first Study Pack is **a three-way decision at the single highest-arousal moment in the entire funnel.**

This reframes deliverable #6. The question is not *"what celebration should we add?"* It is **"what interruption should we delete?"** That answer is also strictly better for the brief's own principle — *shorter without removing steps* — because it removes a screen without removing a step.

---

## 1. Overall critique

The current onboarding is **a well-built questionnaire attached to a product demo**, in that order. Five screens: four of them take from the learner, one gives. The give happens last, and is then gated behind a menu.

That ordering is the whole problem, and it is not a visual one. Restyling a form that asks four times before giving once produces a prettier form.

The positioning is *"your notes become your study system."* The flow currently demonstrates *"answer these questions and we'll let you in."* A learner who abandons at step 2 has seen nothing that distinguishes NoteLib from any app's settings page — because at step 2, there is nothing.

**What is already good, and worth protecting:** the flow generates a real artifact from the learner's own input. Most onboarding is a tour or a checklist; this one *makes something*. That is a genuine asset and almost every recommendation below is about surfacing it earlier and more loudly.

---

## 2. The biggest UX problems, ranked

**1. The payoff is buried, then interrupted.** Four screens of input before any reciprocity, and then a decision screen between the learner and the thing they just made.

**2. The destination is never stated.** A wizard feels endless when you don't know what you're buying. Nothing on screen 1 says *"in about a minute, you'll have your first Study Pack."* Perceived length is a function of unknown remaining effort, not actual steps.

**3. Every screen asks; no screen gives.** There is no moment of reciprocity until generation. The learner is spending trust with no evidence.

**4. Form density signals "admin," not "learning."** Stacked label + helper text + input reads as a tax form. The helper text is the worst offender: it is long, it appears before the user has tried anything, and it exists to prevent errors the learner has not yet had a chance to make.

**5. Profile type is asked before the learner knows what it changes.** It is the first question and the least motivated one.

**6. The empty-state fallback describes an absence.** *"We're still building an Official set for X"* is a sentence about what the product lacks, delivered to someone who has just told you what they need.

---

## 3. The redesigned emotional journey

The arc that should run from first screen to Study Pack:

> **Recognition → Specificity → Agency → Anticipation → Ownership**

| screen | the learner should feel | failure mode today |
|---|---|---|
| Profile type | *"this is for someone like me"* | "which radio button is right?" |
| Learning context | *"it understands what I'm studying"* | "I'm filling in a form" |
| How to begin | *"I get to choose my way in"* | "another question" |
| Generating | *"something is being made for me"* | dead time |
| Study Pack | *"this is mine, and it's better than what I gave it"* | "which of these three buttons?" |

**The one rule:** the arc must never dip into *obligation*. The moment a screen feels like a requirement rather than a step toward something, the learner is doing paperwork. Every recommendation below is in service of that.

---

## 4–5. Screen by screen

### Step 1 — Profile type

**Yes, your instinct is right, and I'd go further than agreeing.** Master-detail is wrong here for a reason worth stating: **step 1 is where teaching costs the most.** The learner has invested nothing, so every additional thing to read is an exit opportunity. A detail panel is a reading requirement placed at the point of minimum commitment.

The deeper point: if a card needs an explanation panel to be understood, **the card's label is too weak**. The fix isn't a better explanation — it's a card that needs none.

**Recommendation:**
- Four cards, one line each. Make the line a **first-person outcome**, not a role description.
  - *"Student"* → **"Keep every subject in one place."**
  - *"Exam reviewer"* → **"Build toward exam day, one session at a time."**
  - *"Teacher"* → **"Turn your materials into ready-to-use practice."**
  - *"Professional"* → **"Stay sharp on what your work demands."**
- **Tap the card to advance. No Continue button.** This is the single largest friction cut available in the entire flow: it removes a tap, removes the reach to a bottom button, and removes the "did I do that right?" pause between choosing and confirming.
- Add the destination line above the cards: **"About a minute from now, you'll have your first Study Pack."**

**One constraint this design also solves.** A per-profile *"You'll be able to: ✓ … ✓ … ✓ …"* panel would either overstate **Professional** or expose that it is thin — it is largely relabeled student functionality (Interview Practice, a renamed Challenge Quiz, long exam) with no distinct feature set. One outcome line per card sidesteps that honestly. Do not build a design that requires four concrete promises per profile.

### Step 2 — Learning context

The complaint is that it feels like a form. It feels like a form because it **is** three labelled fields stacked vertically, which is the visual grammar of data entry.

**Recommendation: make the screen a sentence the learner completes.**

> **I'm studying** ⟨Course / Program⟩ **at** ⟨Learner level⟩ **level.**
> *(Exam reviewers only)* **My exam is on** ⟨date⟩**.**

Same data, same validation, same pickers — but one visual object instead of three, and it reads as conversation rather than intake. Perceived effort drops without a single field being removed.

- The inline controls **must still be pickers, not free text** — Course / Program and Learner Level are catalog-backed and this is a rule the project has broken repeatedly. A sentence-form with combobox triggers satisfies it fine.
- **Delete the helper paragraphs.** Replace with a placeholder example inside the control (*"e.g. BS Civil Engineering"*). Examples teach; helper text lectures.
- Heading: **"Tell us what you're studying"** — as the brief suggests. It is a better sentence than "Set up your learning profile" for exactly the reason the brief gives.

### Step 3 — How to begin

The mechanism already works. The copy is doing none of the selling.

**Recommendation: two cards, outcome first, and prove it with specifics.**

> **Start with a ready-made review set**
> *63 notes for Nursing, built for the PNLE. Start practising in one tap.*

> **Build from your own notes**
> *Paste or write a note. You'll get a summary, key concepts and a practice quiz from it.*

The critical move is the **number**. The system already resolves the learner's program to a specific Official set, so it can name the real note count. *"Structured learning created by NoteLib"* is an adjective; *"63 notes, built for the PNLE"* is evidence. Specificity is what makes a first-run screen feel trustworthy rather than marketed-at.

### Empty-state fallback

Reframe from **absence** to **invitation**. The current framing leads with what the product lacks, to someone who has just told you exactly what they need — the worst possible moment for a shortfall.

**Recommendation — three beats, in this order:**
1. **What works today, first.** *"Your own notes work right now — that's how most learners here start."*
2. **The gap, second, stated as in-progress rather than absent.** *"We're still building the Civil Engineering set."*
3. **A role in the future, third.** *"Want to know when it's ready?"*

The emotional fix isn't softer wording — it's **agency plus future inclusion**. A learner told "you can start now, and we'll tell you when the rest arrives" has been given something. A learner told "we don't have that yet" has been given an apology.

### The generation wait — your most underused asset

This is currently dead time, and it is the best opportunity in the whole flow.

**Recommendation: let the learner watch the Study Pack being built.** Reveal each part as it completes:

> Reading your note… ✓
> Writing the summary… ✓
> Pulling out key concepts… ✓
> Building your practice quiz…

Three things happen at once: the wait becomes **construction rather than loading**; anticipation builds toward a known object; and — the part that matters most for your comprehension goal — **it teaches what a Study Pack is** without a single explanatory screen. The learner learns the product's core artifact by watching it assemble.

This is the answer to *"should we celebrate?"* **The wait is the celebration.** Do it here, where the learner is already waiting, rather than adding a screen after.

### The transition into the Study Pack

**Delete the three-CTA completion screen. Land directly on the Study Pack.**

No confetti, no interstitial, no "You're all set." A celebration screen celebrates *the app*; landing on the artifact celebrates *the learner's material*. The second is the positioning.

**On arrival:** one lightweight, dismissible banner at the top of the real Study Pack —

> **This is your first Study Pack, made from your note.** *Try a quick review when you're ready.*

One suggested action, not three. The dashboard and library are in the nav; a learner who wants them will find them. Offering all three at the peak moment is what decision paralysis looks like.

*(The two dropped CTAs still have analytics events. If you want to keep measuring intent, keep the events on the banner's action and on its dismissal.)*

---

## 6. Modern patterns that fit NoteLib

- **Sentence-form input** (step 2) — conversational data capture without extra steps.
- **Progressive artifact reveal** (generation) — the wait teaches the product.
- **Tap-to-advance selection** (step 1) — standard in mobile onboarding, removes a confirm action per screen.
- **Destination-stated progress** — *"About a minute from now…"* rather than a bare 1-of-5 dot row. Progress indicators reduce anxiety only when the endpoint is known.
- **Land-on-artifact** rather than land-on-confirmation — the pattern good editors and design tools use: you end up inside the thing you made.

**Patterns I'd avoid:** a product tour or coach marks (postpones the payoff further), an interstitial celebration screen, and any "skip for now" escape hatch — the flow is short enough that an escape hatch mostly harvests people who would have finished.

---

## 7. What I would remove or redesign entirely

**Remove:**
1. **The completion screen.** Highest-value single deletion in the flow.
2. **The Continue button on step 1.**
3. **Helper paragraphs under form fields** → placeholder examples.
4. **The word "AI"** everywhere a learner can read it. It names the mechanism; your positioning names the artifact. Keep it in internal docs.

**Redesign entirely:** the generation wait (dead time → the flow's best moment) and the empty-state fallback (apology → invitation).

**Explicitly keep:** the five steps, the generation-during-onboarding model, and the Intent Router mechanism. **The brief's "shorter, even if the step count stays the same" is achievable without cutting a step** — via tap-to-advance, sentence-form, a stated destination, and a wait that does work. Perceived length is about unknown remaining effort and reciprocity, not screen count.

---

## Mobile-first critique

Most learners are on phones, and several recommendations above are mobile-motivated:

- **Master-detail is disqualified on mobile alone** — it becomes either a cramped split or a two-tap drill-down, which is worse than the radio buttons it replaces.
- **One screen, one scroll, maximum.** If a step needs two scrolls on a mid-size phone, it has too much on it. Step 3's two cards must both be reachable without scrolling past the fold.
- **Primary CTA in the thumb zone** — fixed to the bottom, not stranded after content. Tap-to-advance on step 1 removes the problem there entirely.
- **Cards at least ~56px tall, full-width, single column.** Never two-up on phone.
- **The generation screen must not scroll.** It is the one screen where the learner has nothing to do; making them scroll to see progress is pure friction.
- **Perceived effort is driven by visible field count.** Sentence-form step 2 shows one paragraph-shaped object instead of three stacked field groups — the single biggest perceived-density win available.

**On the existing design system:** onboarding sits outside the `PageHeader` / `BackLink` conventions in `docs/ui-standards.md`, and that is correct — it is a full-screen flow, not an app page. Don't retrofit those patterns here; do reuse the existing card, spacing and color tokens so the flow doesn't read as a separate product from the app it opens into.

---

## What this review cannot tell you

**Which step the 132 abandon on.** Profile type only persists at the *final* step, so the database cannot distinguish a step-1 abandon from a step-4 one, and clean step-level analytics only exist from 2026-07-28 (n≈5 so far). Everything above is reasoned from flow structure, not from where learners actually leave.

That has one practical consequence: **this is a whole-flow redesign, and it should be justified as one.** Nobody should claim a specific screen is the leak. If you want to know before building, the step events are already instrumented and clean — the denominator is just small, and it grows on its own.

**The honest expected value.** A previously-run analysis showed activation is already 52.2%, which structurally caps activation-volume work as a lever on *returning* learners. This redesign should be justified on **comprehension and first impression** — 132 people meet the product and leave before understanding it — not on a projected retention lift. That is a real reason. It is just a different one.
