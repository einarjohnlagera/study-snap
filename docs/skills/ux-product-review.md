# UX / Product Review

Consistent structure for reviewing NoteLib UX decisions against product philosophy.

Run this review before shipping any surface that touches conversion, retention, or the public-facing product — or whenever a feature feels "off" and you need to articulate why.

---

## NoteLib UX Philosophy (Non-Negotiable)

Before reviewing any specific surface, these principles must hold:

**Notes first.** The note is the primary entity. Every product surface should reinforce that users are building a reusable library, not just consuming AI-generated content. Quick wins should feel like steps in the learning loop, not one-shot outputs.

**Learning loop over feature density.** `Create → Understand → Practice → Challenge → Improve` is the product. Every screen should be answerable with "where in this loop does this screen live?" If it doesn't clearly fit, it's probably doing too much.

**Teach before you convert.** On public-facing surfaces, show value before asking for anything. The signup gate belongs after a visitor has experienced the product, not before.

**Soft conversion, not aggressive monetization.** Upgrade CTAs are plan-aware, contextual, and appear after the user has encountered a meaningful limit. Never hard-block with a paywall modal on page load. Never use generic "Go Pro" as the universal CTA.

**NoteLib is a study tool, not a quiz app.** Quizzes are one mechanism inside the learning loop. They exist to reinforce note-based studying, not to replace it. If a feature makes the product feel like a generic quiz platform, it's drifted.

---

## Review Categories

Review any UX change across the following dimensions. You don't need to write paragraphs — one to three sentences per category is enough to catch problems before they ship.

### Acquisition

Does this surface help a visitor understand NoteLib and take a meaningful first step without being hard-gated?

- Does the page teach first and convert second?
- Is the signup gate after value is shown, not before?
- Does the CTA match where the visitor is in their understanding — not assume they already want to sign up?
- Is the messaging anchored to study outcomes (exam readiness, retention, weakness improvement) — not generic AI tool positioning?

Anti-patterns:
- Sign-up modal on page load before any value is shown
- "Try NoteLib for free" as the only CTA before the visitor has seen what NoteLib does
- Public note pages that feel like app login screens

### Activation

Does a new user reach their first meaningful win without friction?

- Does the onboarding flow end with a generated Study Pack (the first win)?
- Is the empty dashboard instructional, not generic?
- Is the first-study-pack prompt clear and reachable from the empty state?
- Does the flow place the user at the `Understand` stage before they encounter an empty dashboard?

Anti-patterns:
- Onboarding that ends at a blank library
- First-time users facing a generic "No notes yet" state with no guided next step
- Learner level or course/program forced during onboarding (these are deferred to Profile)

### Retention

Does the product bring users back to study?

- Is there a clear continuation prompt on Dashboard for users with recent unfinished sessions?
- Are weak concepts surfaced where users will see them after a quiz?
- Do retention emails honor `inactivityRemindersEnabled` and `weakConceptRemindersEnabled`?
- Does the near-limit banner show reset date and an upgrade path — not just a warning?

Anti-patterns:
- Retention emails that don't reference the user's actual content or weak areas
- Dashboard with no signal of what to do after completing a quiz session
- Weak concepts buried in a tab that isn't the default view

### Clarity

Is the hierarchy of information obvious? Does the user always know what to do next?

- Is there exactly one primary CTA per section?
- Are secondary actions (share, copy, navigate) visually lighter than the primary?
- Is the page scannable on mobile in under 5 seconds?
- Does copy use student language (retain, practice, review) rather than feature language (generate, process, analyze)?

Anti-patterns:
- Two equal-weight primary buttons on a result screen
- CTA label that describes what the system does ("Generate Study Pack") without connecting to the user's goal ("Start practicing from your notes")
- Copy that could apply to any AI tool ("Powered by AI" as a headline)

### Emotional Hierarchy

Does the design feel like it's on the student's side?

- Does the result screen reinforce progress, not just show a score?
- Does the empty state explain what's possible, not just show absence?
- Does the paywall feel contextual and respectful — or does it feel like a hard stop?
- Are upgrade prompts specific to what the user tried to do, not generic monetization messages?

Anti-patterns:
- Paywall copy that doesn't name the blocked action
- Score-only result screens with no clear next step
- "You've reached your limit" without showing when the limit resets or what upgrading would unlock

### Mobile Usability

Does the surface work on a small screen without layout breakage?

- Do buttons have icon + text (not icon-only) for primary actions?
- Do modals and sheets fit within the viewport?
- Is the CTA reachable without scrolling on common mobile heights?
- Does the input flow (note creation, quiz answering) work without keyboard overlap blocking key elements?

Anti-patterns:
- Full-width CTAs that wrap onto two lines on narrow screens
- Modal content that requires horizontal scrolling
- Icon-only buttons for actions the user might not immediately recognize

### Public Library Strategy

Does the surface reinforce Public Library as a discovery and acquisition channel?

- Does the public note detail page feel like a study reviewer, not a raw content dump?
- Is the mini quiz preview lightweight and clearly optional — not a mandatory gate?
- Does the CTA after engaging with public content invite creation, not just consumption?
- Is `Share` always visible regardless of auth state?
- Does the creator identity show `displayName` for readability and `@username` for trust — not a raw user ID?

Anti-patterns:
- Public note pages that lead with a "Login to view" wall
- `Copy to My Library` as the first CTA before the visitor has seen any content
- Relying on `displayName` alone for creator attribution when duplicates exist
- Public pages that feel identical to authenticated note detail — missing the "this could be yours" angle

### Note-Centric Design

Does the feature reinforce the note as the primary entity, or does it drift toward quiz-app territory?

- Is the note visible and prominent on surfaces that show quiz results or study data?
- Does the study history trace back to the note, not just the session?
- Does the generation flow make clear that the quiz came FROM the note?
- Is `Make a Copy` the versioning action — not `Regenerate` or `Overwrite`?

Anti-patterns:
- Quiz result screens with no clear path back to the source note
- Study Pack treated as the primary entity instead of the generated enhancement of the note
- Library organized by quiz session instead of by note
- Surfaces that make NoteLib feel like a quiz generator, not a study workspace

---

## Review Output Format

A complete UX review should be brief and actionable. Target format:

```
## UX Review: [Feature or Surface Name]

**Verdict**: Ship / Ship with minor fixes / Needs rework

**Passes**:
- [What works and why]
- [What works and why]

**Issues**:
- [What's wrong] → [Suggested fix]
- [What's wrong] → [Suggested fix]

**Anti-patterns caught**:
- [Anti-pattern] → [Why it violates NoteLib philosophy]

**Open questions** (if any):
- [Question that needs a product decision before shipping]
```

---

## Common Anti-Patterns Quick Reference

| Anti-pattern | Why it's wrong |
|---|---|
| Signup gate on public page load | Teach before converting — visitors need to see value first |
| Generic "Go Pro" CTA | Upgrade copy is plan-aware; use `getUpgradeCtas(currentPlan)` |
| Two equal-weight primary buttons on one screen | One primary action per screen — hierarchy matters |
| `displayName`-only creator attribution | `displayName` is not unique; `@username` is the stable identity |
| Quiz session as the primary entity | Notes are primary; quizzes derive from notes |
| Paywall without naming the blocked action | Context-aware paywalls only — users must know what they hit the limit on |
| Icon-only buttons on mobile for major actions | Icon + text for major CTAs on all screen sizes |
| Aggressive upgrade nudge on page load | Upgrade prompts appear after limits are encountered, not preemptively |
| Public note pages that feel like locked app screens | Public surfaces are acquisition surfaces — teach first |
| Learning loop broken by a dead end | Every dead end (empty state, limit, error) needs a clear next step |
