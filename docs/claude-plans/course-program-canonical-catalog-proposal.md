# Proposed ADR-001 amendment — one canonical Course / Program catalog for learners and curators

**Status: PROPOSED, not ratified. Raised by the owner 2026-08-11.** This document exists so the proposal, the audit behind it, and the counter-argument survive the conversation that produced them. Nothing here is decided, and `v0.71.2` deliberately does not pre-empt it in either direction.

**What it would amend:** `ADR-001`'s two-mode model — *"Learners use exactly one free-text personal program in `notes.course_program`; Teacher/Admin curators choose one or many catalog programs in `note_course_program`"* — and the ruling that *"a learner-authored note using the personal-string fallback is a canonical, fully supported shape, not a degraded one."*

## The owner's proposal

One canonical catalog shared by learners and curators. Learners select from it rather than typing. Where their program does not exist, an explicit **"Can't find your program? Request Program"** action, which grows the catalog intentionally and yields demand analytics for future Official Study Plans. Curators additionally get inline catalog creation (shipping in `v0.71.2`).

**The stated rationale:** `Course / Program` began as descriptive learner metadata and has become a core dimension — powering onboarding routing, Official Study Plans and Review Sets, Applicable Programs, library discovery and filtering, recommendations, and curriculum organisation. Free text now fragments a load-bearing axis into spelling variants (`BS Civil Engg`, `Civil Eng.`, `BSCE`, `Bachelor of Science in Civil Engineering`).

**Open sub-question the owner raised:** how to represent genuinely non-academic notes (meeting notes, work documentation, hobby notes). Owner leans toward allowing blank rather than a `General / Personal` pseudo-value.

## Audit findings, verified in code 2026-08-11 — do not re-derive

1. **Four of the six "core dimensions" are already catalog-only.** Applicable Programs (`note_course_program`, curator-gated), Official Review Set labels (collection `courseProgram`, admin-set), curriculum organisation (the catalog itself) and Exam Hub (`ExamGoalConfig`'s fixed slug map). What remains free text is narrower than "Course / Program": it is the learner's **personal** label, in `users.course_program` and `notes.course_program`.
2. **There is a real leak, and it is better evidence than the original argument.** `NoteService.listPublicCoursePrograms:1213-1221` **unions** legacy free-text values from public notes with catalog names. A learner who makes a note public with `BS Civil Engg` genuinely creates a public filter value. **This is the concrete harm; it enters through public learner notes, not through learners typing.**
3. **Scale, measured:** **4,480** private learner notes rely on the free-text string against a **21-row** catalog, while production holds **4** non-admin public notes. The fragmentation surface that can currently reach a public shelf is four notes.
4. **`resolveRequestedCourseProgram` throws when both the note value and the profile program are null**, so blank is not currently a valid learner state. Allowing it is a real change, and it interacts with `v0.71.1`'s shadowing work.
5. **Onboarding and `/profile` offer 31 hardcoded suggestions against the 21-row catalog, only 16 overlapping** (C8/C9, already logged). This is a genuine source of junk vocabulary and is independent of whether the field is locked.

## The counter-argument, recorded in full

**Free text is already the demand mechanism, and a better one than requests.** `v0.70.0` built the 21-program catalog from *"a two-stage production vocabulary read"* of saved values. Forty learners typing `BS Civil Engg` is a high-volume passive signal; a Request button converts that into a low-volume active one, because most users pick the nearest wrong option or abandon rather than file a request. The proposal's own stated benefit — demand analytics — may be *worse* served by requests than by what exists.

**At a curator headcount of one, the owner is the queue.** A blocking request makes note creation depend on the owner's response time — the shape of the B0 activation bug, slower. A non-blocking request means blank is the fallback, which yields less signal than free text did.

**The migration trap is concrete.** With the field catalog-only, every learner among the 4,480 whose value is not one of the 21 cannot save their next edit, because the current code path requires a resolvable program. That is a mass edit-blocking regression needing a designed grandfather rule.

**Ratio.** Constraining 4,480 notes to clean a surface whose present footprint is 4 notes is a bet on future community content — legitimate, but it should be named as a bet rather than as fixing a present mess, especially since the Trust → Habit → Community ordering puts community content last deliberately.

## Counter-proposal — constrain public discovery, not learner input

**Make `listPublicCoursePrograms` return catalog names only.** A learner's off-catalog value continues to file their own note in their own library, but stops minting a public shelf. One method, no migration, no request queue, no ADR reversal, and it targets the verified harm exactly.

**Pair it with catalog-first suggestions:** replace the 31 hardcoded onboarding/profile strings with the live catalog, ordered first, free text still allowed. Most learners pick the canonical name because it is offered first — convergence by default rather than by enforcement, and it closes C8/C9 at the same time.

**If that fails to produce clean-enough vocabulary within a release or two, lock it down then** — with evidence the softer fix failed, and with the 4,480-note migration designed rather than discovered.

## On the non-academic sub-question

**Blank, not `General / Personal`** — the owner's lean is right. A pseudo-program is a non-curriculum value inside a curriculum catalog and, given finding 2, would itself become a public shelf. The caveat is finding 4: blank is not currently valid, so this is a code change rather than a policy note, and it touches the same required-field logic `v0.71.1` just reworked.

## What would settle it

- **A production read of distinct `notes.course_program` values on public notes**, and the same for `users.course_program` across all accounts — how much genuine fragmentation exists versus how much is assumed.
- **Whether any Official Study Plan decision was ever blocked by not knowing demand.** If free text already answered it once (it did, at `v0.70.0`), the analytics argument for requests is weak.
- **An owner call on the bet:** is community-authored public content close enough that prospective fragmentation is worth a migration now?

## Sequencing

**`v0.71.2` ships catalog CRUD plus inline curator creation regardless of how this resolves** — that work is correct under either outcome and unblocks the live authoring blocker. This amendment is `v0.72.0`-shaped at the earliest, and it reverses a ratified decision, which by this repo's own standard needs more than a strong argument.
