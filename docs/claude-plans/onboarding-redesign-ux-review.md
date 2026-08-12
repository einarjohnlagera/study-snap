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

## Second pass, 2026-08-11 — "conversation, not configuration"

**This section supersedes parts of §4–5 below.** The owner read the first pass and pushed back on its framing: it was optimising the existing form rather than redesigning the experience. The governing principle is now:

> **The learner is telling NoteLib their learning story. They are not configuring settings.**
> Every screen answers one question. No screen presents a list of fields.

**Two open questions from the first pass are now settled by owner decision, and should not be re-asked:**
- **Step 1 loses the Continue button.** Selecting a profile advances. (The first pass proposed this; the owner arrived at it independently.)
- **Step 2 is NOT sentence-form.** Rejected for the exact reasons the first pass flagged as its cost — awkward wrapping on mobile, harder validation. It becomes **question-as-heading with placeholder-carried meaning, in a normal stacked mobile layout.** The first pass's "middle option" is the ratified one; its full sentence-form proposal is withdrawn.

### The one change that shortens onboarding on evidence rather than taste

**Remove the exam-date question from onboarding entirely.**

`v0.72.0` shipped a post-session commitment prompt that already asks for the exam date. Verified in `review-commitment-prompt.tsx`: it shows the field when `me.examDate !== null || me.profileType === "BOARD_EXAM"` (`:57`), **prefills** from any existing value (`:58`), and **requires** a date before a review plan can be set (`:86-87`).

So for the population that actually needs an exam date — `BOARD_EXAM` learners — it is already collected, required, at a strictly better moment: after their first completed session, when the date means something, rather than before they have used the product at all.

**This is a field deleted, not restyled.** It is the only proposal here that makes onboarding shorter without trading anything away, and it resolves a duplicate ask that currently exists across two surfaces.

### The contradiction in the brief, and how to resolve it

The brief says **"every screen should ask ONE question"** and then mocks a step 2 asking three (*what are you studying / what level / when's your exam*). Both can't hold.

**The resolution turns on a distinction the brief hasn't drawn: auto-advance only works for _choice_ inputs.**

- **Profile type** and **learner level** are closed sets — tapping an option is an unambiguous "I'm done," so the screen can advance itself. These split cleanly into one-question screens.
- **Course / Program** is a typed, searchable combobox against a 21-row catalog. The system **cannot know when the learner has finished typing**, so it needs an explicit confirm. It cannot auto-advance.
- **A date picker** has the same problem.

With the exam date removed, step 2 is exactly **one auto-advanceable choice (level) plus one typed field (program)** — a far cleaner split than it looks today.

**The tradeoff to decide, stated plainly rather than chosen quietly:** true one-question-per-screen means *more screens with fewer decisions each*. Once selection auto-advances, a screen costs roughly one tap, so splitting is nearly free in real time and usually **feels** faster — momentum is visible, and each screen is a single decision instead of a form to audit. But it is more screens, which sits against the brief's "don't make onboarding longer." The honest framing: **actual step count rises, perceived effort falls.** Duolingo and Typeform both make this trade deliberately.

### The split, decided 2026-08-11 — 5 screens becomes 8

Owner ruling: split one question per screen. Concrete target flow, with what each screen can and cannot do:

| # | screen | question | advance |
|---|---|---|---|
| 1 | Profile type | *"How will you use NoteLib?"* | **auto** — closed set |
| 2 | Course / Program | *"What are you studying?"* | **Continue** — typed combobox |
| 3 | Learner level | *"What level are you studying at?"* | **auto** — closed set |
| 4 | First intent | *"How would you like to start?"* | **auto** — closed set |
| 5 | Input method | *"How do you want to begin your first note?"* | **auto** — closed set |
| 6 | The note | topic, or paste your own | **Continue** — free text |
| 7 | Generating | *(no question)* | automatic |
| 8 | Done | *(kept as-is — the three-CTA screen stays)* | learner chooses |

**What this actually changes in code:** today's step 2 carries three questions (level, program, exam date — the third now deleted), and today's step 3 carries two after the intent choice (input method, then the note text on the same screen). Screens 1, 4 and 8 already exist as single-question screens and are being restyled, not split.

**Ordering note.** Program before level follows the owner's mock and reads naturally as conversation. The alternative — level first — would put two auto-advancing screens back to back before the first typed field, building momentum before the first Continue button appears. Recorded as the runner-up, not chosen.

**A migration hazard the split creates.** Onboarding persists `currentStep` in a per-user localStorage draft and reads it on resume. Renumbering 5 screens into 8 means a learner mid-onboarding at deploy time resumes on the wrong screen, or on one that no longer means what their draft assumes. This must be handled explicitly — it is the kind of defect that only appears for users who were mid-flow during a deploy, which is precisely the population this release is trying not to lose.

### Motion — you already have a vocabulary; use it rather than adding a dependency

**Do not add `framer-motion` or any animation library.** `globals.css` already defines a motion system: `--motion-duration-fast` / `-base`, `--motion-ease-standard` / `-emphasized`, `--motion-press-scale`, plus `.motion-pressable`, `.motion-lift`, `.motion-fade-enter` and others. `prefers-reduced-motion` is already handled.

**Onboarding already animates.** `.motion-onboarding-step` exists (a 200ms fade with an 8px rise) and is already applied three times in the flow.

**So the "feels alive" gap is not missing animation — it is that motion exists only on step *entry*, and nothing marks the moment of *choosing*.** That is precisely the beat tap-to-advance makes load-bearing: without feedback, the screen appears to be yanked away the instant a card is touched.

**Recommendation, in existing vocabulary:** on selection, the chosen card confirms (press-scale via `.motion-pressable`, then a brief settle) and the unchosen cards recede, *then* the step transition runs. Roughly 120–160ms of confirmation before the existing 200ms transition. The learner sees their choice register before the screen changes — which is what makes auto-advance feel responsive instead of twitchy.

### Typography, spacing, rhythm

- **Smaller headings.** The current step headings compete with the cards, which are the actual content. A step heading is a question, and questions don't need to be loud — one step down in size, normal weight rather than bold.
- **Delete helper text; keep placeholders.** Helper paragraphs prevent errors the learner hasn't had a chance to make yet, and they are the single largest contributor to the form-heavy feel.
- **Give cards internal breathing room before adding gaps between them.** Density reads as cramped from tight padding more than from tight stacking.
- **One idea per vertical rhythm unit.** Heading → answer → nothing else. No secondary explanation, no reassurance line, no "you can change this later" footnote on every screen.

### Mobile-first specifics

- **Design at 360px, let desktop expand.** The four profile cards are a single-column stack on mobile; they may become a 2×2 grid on wide screens, never the reverse.
- **Card copy must survive two lines at 360px.** The brief's Step 1 copy (title + benefit + "Best for…") is three lines per card — check it at the narrow width before committing to it.
- **No fixed CTA needed on auto-advancing screens**, which is a real mobile win: the tap target is the content itself, in the middle of the screen, not a button at the bottom edge.
- **Never more than one scroll per screen.** Four cards with three lines each will approach that limit — a reason to keep the benefit line to one line.

### Two constraints that still bind

- **Course / Program must stay a picker.** The brief's mock shows `[ BS Civil Engineering ]`, which reads as free text. It is a combobox typed against a catalog. This rule has been broken repeatedly and the visual redesign is exactly where it gets broken again.
- **Do not grow the Step 1 card copy into a capability list.** The brief's Professional line (*"Keep learning throughout your career"*) is safe. A list of four concrete promises per profile would either overstate `PROFESSIONAL` — largely relabeled student functionality — or expose that it is thin.

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
