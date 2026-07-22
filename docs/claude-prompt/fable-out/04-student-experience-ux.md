# 04 — Student Experience UX (Learning Assistant, learner-facing)

## Decisions carried forward

**Feature name: "Plan My Review"** (canonical/internal name; BOARD_EXAM-facing string). Learner-facing labels resolve through a new `CollectionLabels.guidedSetupCta` field in `frontend/lib/collection-labels.ts` — never hardcoded: STUDENT `Plan my studies`, BOARD_EXAM `Plan my review`, PROFESSIONAL `Plan my prep`, PARENT/default `Plan my learning`, TEACHER hidden (no learner journey, same as the existing no-adoption-path rule). No "AI", "Smart", or "Auto" in any label. Rejected: "Build …" (collides with the Builder canvas), "Create …" (collides with the create modal), "Set up my {singular}" ("Set up my Review Set" repetition).

**Route: `/collections/setup`** — a sub-page of the collections surface, not a new nav item. The Review-Set-Centric nav reshape stays deferred and gated exactly as ROADMAP.md states; nothing here advances it.

**Experience flow (4 steps, ends on an existing surface):**
1. **Goal** — "What are you preparing for?": Course/Program via `CourseProgramCombobox` (`allowCustom={false}`, locked buckets) + optional target date. "Not listed?" files a freetext `curator_generation_request` (source `LEARNER_REQUEST`) and shows an honest "we'll prepare it" state.
2. **Coverage map** — one screen, four states:
   - **Official set exists** (short-circuit, reuse-order step 0): hero = the Official {singular}, `Start this {singular}` → existing `adopt-goal`. Done.
   - **Partial** (template + some CONFIRMED public fulfillments): coverage bar ("31 of 42 topics have study material"), subject sections with per-topic rows — `In your library` / `Ready for you` / `Being prepared` / `Requested` / `Not available yet` — CTA `Build my {singular} from what's ready`.
   - **Empty** (template, zero fulfillments) / **No template**: honest "we're still preparing material for {program}", one-tap request (demandCount++), fallbacks to Public Library browse and build-your-own. Never fabricate coverage.
3. **Confirm & assemble** (partial path only): learner reviews subjects → child Subject plans, notes to copy (`copyNote includeStudyPack=true`), own matched notes to add, missing topics to request — then the wizard orchestrates **existing endpoints client-side** (create Goal, create/nest children, copy notes, add items, labels from `moduleLabel`, target date). Per-item skip isolation; skipped-notice pattern reused. Zero new write infrastructure beyond the request-filing endpoint.
4. **Land on `/collections/{goalId}`** — the existing Goal detail (Identity → Today's Focus Coach → Progress → Companion → Subjects). The wizard has no destination surface of its own; primary auto-set and the post-adopt target-date GuidanceTip fire as already shipped.

**Voice rule:** every string says what the *learner gets*, never how it's made — "the app knows what I should study next," not "the app has AI." Requests read "our team prepares and reviews new material," mirroring learn-page.md's no-AI/OCR/LLM messaging rule. Internal curator statuses collapse to two learner words: `Requested` and `Being prepared`; REJECTED renders as `Not available yet`.

**Coverage vs readiness stay separated:** the coverage map answers "does reviewed material exist" (curriculum-derived, never persisted per learner); mastery stays on Goal detail/Progress via ConceptHealth. "Detect existing knowledge" = owned-note lineage match (degrade gracefully where lineage is absent) + mastery chips, informing prioritization only — never auto-transferred.

**Entry points (all existing surfaces, no new section):** `DashboardEmpty` secondary link (S/BE/P), the `browseWhenEmpty` guidance card in `DashboardStudyPlanSection`, a secondary header action + one `pickActiveGuidance` tip on `/collections`, and the Recommended empty state on `/collections/published`. When a primary Goal already exists, Dashboard does not promote the wizard; a coverage re-check on Goal detail is a named phase-2 follow-up.

---

# Full detail

## 1. Naming (Q4)

### 1.1 The choice

The feature surface is canonically named **Plan My Review**. It is outcome-first (the outcome is a plan for your review), verb-led, and contains no technology words. It deliberately borrows Philippine exam-prep vernacular — "review" is what PNLE/LET/ALE takers call the entire preparation period — which makes the BOARD_EXAM string read as native, and BOARD_EXAM is the flagship persona for this feature.

### 1.2 Resolution through `getCollectionLabels`

Add one field to `CollectionLabels` in `frontend/lib/collection-labels.ts` (same pattern as `primarySingular`, empty-state copy, and CTA copy — explicit per-profile strings, not mechanical composition, because composition produces clunkers like "Set up my Review Set"):

| Profile | `guidedSetupCta` | Notes |
|---|---|---|
| `STUDENT` | `Plan my studies` | |
| `BOARD_EXAM` | `Plan my review` | canonical string |
| `PROFESSIONAL` | `Plan my prep` | |
| `PARENT` / default | `Plan my learning` | PARENT has no feature implementation yet; default keeps the resolver total |
| `TEACHER` | *(entry points not rendered)* | Teachers curate, they don't follow a learner journey — same exclusion the Dashboard adoption link already applies |

Everything else on the wizard reuses existing label fields: the artifact being created is referred to by `singular` / `goalSingular` / `subjectSingular` ("your Review Set is ready", "3 Subject plans"), and the landing surface is the normal Goal detail, so no other new copy fields are needed. The wizard's page title is universal and label-independent: **"What are you preparing for?"** — the goal question itself, which is the strongest possible "the app knows what to study" framing.

### 1.3 Rejected names and why (recorded so they aren't relitigated)

- **"Build my {singular}" / "Build Study Journey"** — `Build` is the established verb for the Builder canvas (`/collections/{id}/builder`); reusing it would make two different surfaces answer to the same word.
- **"Create Review Plan"** — `Create {singular}` is the existing manual create-modal path (Library selection mode and `/collections` header); the wizard must be distinguishable from blank-slate creation.
- **"Set up my {singular}"** — mechanical composition yields "Set up my Review Set" for the flagship profile.
- **"Smart Plan" / "Study Coach" / anything with AI** — violates hard constraint and learn-page.md's messaging rule (never lead with AI/OCR/LLM).
- **"My Reviews" nav-item framing** — that belongs to the deferred Review-Set-Centric Navigation direction, which is explicitly gated and out of scope here.

## 2. Entry points — where the journey starts

No new nav item, no new Dashboard section. Every entry reuses a slot that already exists, and every entry is gated to STUDENT / BOARD_EXAM / PROFESSIONAL (TEACHER excluded per §1.2).

| Surface | Today | Change |
|---|---|---|
| `DashboardEmpty` (zero notes, cold start) | Secondary link "Or start from a ready-made {singular}" → `/collections/published` | Point the secondary link at `/collections/setup` with the `guidedSetupCta` label. The wizard is strictly better for cold start: it asks for course/program itself (handles an unset profile), short-circuits to the same official set the browse page would show, and handles the no-content case honestly. `/collections/published` stays reachable from the wizard's empty states, so nothing is lost. |
| `DashboardStudyPlanSection` `browseWhenEmpty` card ("No curated {plural} for {program} yet…") | "Check back soon, or build your own" | Add the `guidedSetupCta` action to the same card. This is the exact moment a learner has signaled a track and found no curated plan — the wizard converts "check back soon" into "tell us, and study what *is* ready meanwhile." |
| `/collections` list page header | Primary `New {singular}` | Add `guidedSetupCta` as the secondary header action. Manual create stays primary for users who know what they want. |
| `/collections` GuidanceTip | Library/collections tips run through `pickActiveGuidance` | One new one-time tip, id `collections-guided-setup`: condition = course/program set AND no primary collection AND no owned collection with matching `courseProgram`; message "Preparing for {program}? {guidedSetupCta} — we'll map the topics and what's ready to study."; CTA routes to `/collections/setup`. Registered in the existing rule set so only one tip shows at a time. Discovery-grade, correctly a dismissible tip per guidance.md's reference-vs-discovery rule. |
| `/collections/published` Recommended empty state | "We don't have an official {singular} for {program} yet" + Browse all | Add the `guidedSetupCta` link. Browse answers "what exists"; the wizard answers "what should I do about my goal" — siblings, not rivals. |
| Onboarding | Completion step reuses `DashboardStudyPlanSection` | **No change.** The onboarding flow is locked (v0.39.1 decision: fixes live downstream on Dashboard). The wizard is reached from Dashboard the moment onboarding ends. |

**Idempotent re-entry:** if the learner already owns a Goal adopted from the official set for their program (existing `sourcePlanId` join), entry CTAs resolve to `Open my {singular}` → `/collections/{id}` instead of relaunching the wizard — same already-owned pattern `DashboardStudyPlanSection` ships today.

**Back navigation:** `/collections/setup` is a sub-page; per navigation.md it renders `BackLink` with explicit routing — default destination `/collections` with the profile-aware plural label; entries from Dashboard pass `ref=/dashboard` (the existing `ref` pattern Note Detail uses) so the back link returns where the learner came from.

## 3. The wizard, screen by screen

### Screen 1 — Goal ("What are you preparing for?")

One card, two inputs, zero jargon:

- **Course/Program** — `CourseProgramCombobox` with `allowCustom={false}` and `inlineDropdown`, exactly like the publish modal. Locked to known buckets because curriculum templates are keyed by courseProgram; freetext here would fork the taxonomy (standing rule: taxonomy fields are combobox/dropdown, never freetext). Prefilled from profile `courseProgram` when set; a pre-checked "Save this to my learner profile" checkbox appears only when the profile value is unset or different, so the wizard doubles as the courseProgram-capture moment for learners who skipped it.
- **Target date (optional)** — "When is your exam / deadline?" Feeds the existing `targetCompletionDate` on the Goal created at the end (Goal-only field, weekly countdown derivation already shipped). Skippable; the post-adopt target-date GuidanceTip already covers the skip case on landing.

**"My exam isn't listed"** — a quiet link under the combobox opens a one-field modal: "Tell us what you're preparing for." Submits a freetext `curator_generation_request` (source `LEARNER_REQUEST`, freetext concept + typed program name, deduped server-side with `demandCount++`) and shows the honest terminal state: *"Thanks — our team reviews requests and prepares new material regularly. {program} will appear here once it's ready."* The wizard ends there for that learner (with links to Public Library and Create a note); it never fakes a curriculum. The typed name never enters the course/program taxonomy — it lives only on the request row.

Primary CTA: **"Show me what to study"** → Screen 2.

### Screen 2 — Coverage map ("Here's what studying for {program} looks like")

One screen, four mutually exclusive states, resolved in the reuse-search order from session 01 (learner-side view of it):

#### State A — Complete: an ACTIVE Official {singular} exists (short-circuit, step 0)

- Hero card: the Official {singular} (reusing `PublicStudyPlanCard` composition — title, `{childCount} Subject plans · {itemCount} notes`, `estimatedStudyHours`), framed as *"We have a ready-made {singular} for {program}."*
- Primary CTA **`Start this {singular}`** → existing `POST /collections/{id}/adopt-goal` → route to the personal Goal with the just-adopted flag. Already-adopted resolves to `Continue this {singular}` (existing behavior).
- If the linked curriculum has objectives beyond what the set covers (template grew since publish), one honest line under the hero: *"Covers 42 exam topics · 6 more are being prepared."* The learner never sees version numbers or the ADMIN `mayBeOutdated` signal — just the count of topics on the way.
- A collapsed **"See all topics"** disclosure expands the same subject/topic map as State B beneath the hero, so a learner who wants to inspect before adopting can. Adoption is never blocked on reading it.
- Empty/partial variants inside State A: none needed — publish rules guarantee every item note in an official set is PUBLIC and studyable.

#### State B — Partial: a curriculum template exists, some topics have CONFIRMED public fulfillments, no official set (or the learner declined adoption and expanded the map)

The heart of the feature. Layout top to bottom:

1. **Coverage bar** — *"31 of 42 topics have study material ready."* A single plain progress bar with the fraction as text. This is *coverage* (does reviewed material exist), and the copy never uses mastery words — no %, no "readiness", which stays ConceptHealth vocabulary on Progress.
2. **Subject sections** — objectives grouped by `subjectLabel` (collapsible cards, same interaction grammar as plan-detail sections: header + count + chevron, title peek when collapsed). Within a section, topics in `position` order; `moduleLabel` renders as a muted sub-grouping line when present.
3. **Topic rows** — `conceptTitle` + one status chip:

| Chip | Meaning | Data behind it |
|---|---|---|
| `In your library` | The learner already owns matching material | Owned note whose public-source lineage matches a confirmed fulfillment (see §5); shown with the note title |
| `Ready for you` | A PUBLIC note with a CONFIRMED fulfillment exists and will be copied | Fulfillment join; "with Study Pack" implied — the copy spine carries the pack |
| `Being prepared` | A curator request for this topic is in flight | Request status DRAFT_GENERATED / IN_REVIEW, collapsed to one honest phrase; internal statuses never leak |
| `Requested` | The team has been asked, work not started | Request status REQUESTED |
| `Not available yet` | Nothing exists and nothing is pending | No fulfillment, no open request (REJECTED collapses here too — a learner never sees "rejected") |

4. **Primary CTA** — **`Build my {singular} from what's ready (31 topics)`** → Screen 3.
5. **Secondary CTA** — **`Ask us to prepare the missing 11`** — files deduped `curator_generation_request` rows per unfulfilled objective (`demandCount++` on existing ones). Also offered inside Screen 3 so one tap can do both. Optional light social proof on rows where `demandCount` is meaningful: *"Requested by 12 reviewers"* — honest demand, no fabrication; include only if the count is ≥ 2.

#### State C — Empty: template exists, zero confirmed public fulfillments

- The subject/topic skeleton still renders (the map itself is real and useful — "here's the shape of the exam"), with every row `Not available yet` / `Being prepared`.
- Honest headline: *"We've mapped {program}'s 42 topics, but study material is still being prepared."*
- Primary CTA: **`Ask us to prepare {program} material`** — one tap, files/increments requests across the template (server-side this is one learner action; dedupe keeps it cheap).
- Fallbacks, always visible: `Browse the Public Library` (courseProgram-filtered — the existing Community Notes path), `Create a note` / `Import files` (the standard on-ramps). The learner is never left with only a dead end.

#### State D — No curriculum template for the program

- Same honest framing minus the topic skeleton: *"We haven't mapped {program}'s topics yet."*
- Same request CTA (freetext/courseProgram-level request, as in Screen 1's not-listed path) and the same fallbacks.
- C and D deliberately share one component with the skeleton section conditional, so there is exactly one "we don't have it yet" voice in the product.

### Screen 3 — Confirm & assemble ("Your {singular}, built from what's ready")

Only reachable from State B. A confirmation manifest, not a second editor:

- **Name** — prefilled `"{program} Review"` (or profile-appropriate via `singular`), editable inline. Target date carried from Screen 1, editable.
- **Structure preview** — read-only tree: each covered subject becomes a child **{subjectSingular}**; under it, the notes that will be copied (`Ready for you` rows, copied via `copyNote(noteId, userId, includeStudyPack=true)` — Study Packs ride along, zero LLM cost, no quota) and the learner's own matched notes (`In your library` rows) pre-checked to be **added, not copied** — unchecking any is one tap. `moduleLabel`s land as item `label`s, so plan-detail sections appear for free.
- **Missing topics** — one pre-checked line: *"Ask our team to prepare the 11 missing topics"* with the topic names collapsed behind a disclosure. Unchecking skips the requests.
- Primary CTA: **`Create my {singular}`**.

**Assembly is client-side orchestration over existing endpoints, exactly like the Goal Builder:** `POST /collections` (Goal) → per subject `POST /collections` + `PATCH /collections/{childId}/parent` → per note `copyNote` then `POST /collections/{subjectId}/items` (own notes: items-add only) → `PUT .../items/order` with labels → `PATCH /collections/{goalId}` with `targetCompletionDate` → request-filing call for checked missing topics. Per-item failures are isolated and counted, mirroring adopt's skip semantics; the skipped count travels via the shared `study-plan-skipped-notice` pattern. The primary-collection auto-set invariant fires on its own (first top-level Goal). No new write endpoint is needed beyond request filing; the only new read is the coverage endpoint (§7).

A progress state ("Setting up your {singular}… copying 31 notes") covers the multi-call sequence; on partial failure the learner still lands on a usable Goal with a non-blocking notice, never a dead wizard.

### Screen 4 — Landing: the existing Goal detail

`router.push("/collections/{goalId}")` with the just-adopted-style flag. Everything downstream is already shipped and needs nothing new:

- **Today's Focus / Coach** resolves the first action (`Generate Study Pack` never fires here — packs came with the copies — so it lands on `Study this note` for the first item).
- **Progress tier** shows readiness from zero, honestly: coverage said "material exists"; mastery starts when practice starts.
- **Companion** appears if the adopted official set carried one.
- The **target-date GuidanceTip** fires if no date was set.
- One new one-time GuidanceTip, id `goal-requested-topics`, shown once when the assembly filed requests: *"We're preparing {K} more topics for {program}. They'll appear in the official {plural} once ready."* — sets expectations, then gets out of the way. Fire-and-forget is the deliberate v1 contract (§6).

## 4. Voice and copy rules ("the app knows what I should study next")

- **Every headline is about the learner's goal or next action**, never about the system: "What are you preparing for?", "Here's what studying for PNLE looks like", "31 of 42 topics have study material." Compare learn-page.md's messaging rule: outcome-driven language, never lead with AI/OCR/LLM.
- **The curator is "our team" / "our reviewers."** All preparation language is human-agent: "our team reviews requests and prepares new material." This is also literally true — every generated artifact passes mandatory human review before publish, so the copy isn't a euphemism.
- **Two learner-visible pending words only:** `Requested` and `Being prepared`. The four internal statuses, draft note ids, match confidence, and gap-scan mechanics never appear in learner UI.
- **Never fabricate:** no invented coverage, no placeholder topics, no "coming soon" without a filed request behind it, no mastery numbers on the coverage screen. Empty states say "not yet" and offer a real alternative.
- **Coverage ≠ readiness in vocabulary:** coverage screen says "material ready to study"; Progress/Goal detail own "mastered / due / not started." The two never share a widget.

## 5. Detecting existing knowledge (honest version)

Two derived signals, both read-only, neither persisted per learner (architecture rule: coverage is never persisted per learner):

1. **Content match (`In your library`)** — the learner owns a note whose public-source lineage points at a note with a CONFIRMED fulfillment for an objective (the copy flow already records `copiedFromPublic`; where a source-note reference exists, use it). **Degrade rule:** where lineage is absent or ambiguous, the row simply shows `Ready for you` — a duplicate copy is a mild cost, a wrong "you already have this" is a broken promise. No fuzzy title matching in v1.
2. **Mastery chips (informational only)** — for matched owned notes with Study Packs, the existing ConceptHealth counts (the Free-tier counts, same entitlement shape as `note-concept-counts`) can add a small `Already strong here` chip. It changes nothing about assembly; it tells the learner where to start. Due-timing detail stays Plus/Pro-gated as everywhere else.

What this deliberately is **not**: no automatic mastery transfer onto newly copied packs, no skipping topics on the learner's behalf, no "we detected you know this, so we removed it." Detection informs; the learner decides. That is the curation-not-generation principle applied to the learner's own knowledge.

## 6. The request path (learner → curator queue) end to end

1. Learner taps a request CTA (per-topic, missing-set, whole-program, or not-listed).
2. Frontend calls the request-filing endpoint; backend dedupes on objective (or freetext+program), increments `demandCount`, source `LEARNER_REQUEST`.
3. Immediate honest confirmation, always the same sentence shape: *"Requested — our team prepares and reviews new material regularly. It'll appear in the official {plural} for {program} once ready."*
4. The learner's coverage map reflects pending state on any revisit (`Requested` / `Being prepared`), derived live from the queue — no learner-side tracking entity.
5. When the curator publishes, the material surfaces through the channels that already exist: the official set (State A), `Ready for you` rows on a coverage revisit, and the Dashboard Recommended section.

**Known limitation, deliberate for v1:** there is no push notification or "my requests" page — requests are fire-and-forget from the learner's side, softened by the `goal-requested-topics` tip and live status on revisit. A "new topics are ready for your {singular}" nudge (Dashboard card or coverage re-check on Goal detail: *"3 new topics ready since you built this — add them?"*, copy + add-items into the matching subject) is the natural **phase 2** and needs no schema change — it's a derived diff between the plan's topics and current fulfillments. Record it in ROADMAP when scoping, not here.

## 7. Touchpoint summary (planning-level, for the implementation session)

**New frontend:**
- `/collections/setup` wizard (3 steps + landing redirect), coverage-map components (bar, subject sections, topic rows/chips), confirm manifest, orchestrated assembly with progress + skip isolation.
- `CollectionLabels.guidedSetupCta`; entry-point wiring on `DashboardEmpty`, `DashboardStudyPlanSection` (browseWhenEmpty card), `/collections` header + tip, `/collections/published` empty state.
- Two GuidanceTips (`collections-guided-setup`, `goal-requested-topics`) through `pickActiveGuidance`; a `HelpLink` on the coverage screen ("How topic coverage works" → `/help#study-plans`); extend the Study Plans & Collections Help guide with a Plan-My-Review section in the same change set (guidance.md maintenance rule).

**New backend (small, read-heavy — details owned by the architecture session):**
- Learner-facing coverage read: template + objectives + per-objective learner-visible state (available / in-flight request status collapsed / none) + linked ACTIVE official set for a courseProgram. Derived on request, never persisted per learner.
- Request filing: create-or-increment `curator_generation_request` (objective-level, batch, and freetext/program-level variants), auth-required, deduped.
- Analytics: new `AnalyticsEventType` values (e.g. `GUIDED_SETUP_STARTED`, `CURRICULUM_COVERAGE_VIEWED`, `MATERIAL_REQUESTED`, `GUIDED_SETUP_COMPLETED`) — enum first, then fire.

**Explicitly reused, unchanged:** `adopt-goal` / `adopt`, `copyNote(includeStudyPack=true)`, collection/parent/items/order CRUD, `targetCompletionDate` + countdown, primary auto-set, `PublicStudyPlanCard`, `CourseProgramCombobox`, skipped-note notice, just-adopted flag, Goal detail page, ConceptHealth reads, `getUpgradeCtas` (untouched — nothing here is plan-gated).

## 8. What this deliberately does not do

- **No nav reshape.** Dashboard / My Reviews / Library / Explore stays deferred and gated on Primary Review Set usage evidence; this feature adds zero nav items and would slot cleanly under a future Explore without rework.
- **No onboarding restructure.** The locked flow is untouched; the wizard lives downstream on Dashboard, consistent with the v0.39.1 pattern.
- **No learner-visible generation, ever.** The learner sees only PUBLIC notes with CONFIRMED fulfillments and ACTIVE official sets; the structural guarantee (publish requires all-PUBLIC items) backstops the UX. Requests go to the queue; drafts never render.
- **No per-learner persisted coverage, no new mastery signal, no auto-regeneration, no quota consumption** — assembly is DB copies on the existing spine; reuse is free by design.
- **Coverage UI lives only inside the wizard.** No coverage chips, bars, or percentages on `/collections` list cards, `PublicStudyPlanCard`, or the Goal detail hero — per the badge-classification rule (identity / state / metadata tiers) those cards must not grow a fourth "coverage" tier, and a coverage number on a card would read as learner progress, which it is not.
- **No freetext into taxonomy** — course/program stays a locked combobox; unlisted programs live only on request rows until an admin creates the bucket.
