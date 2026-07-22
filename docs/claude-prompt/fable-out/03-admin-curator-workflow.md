# 03 — Internal Curator Workflow (Deliverable 5 + Q6)

## Decisions carried forward

**The redesigned curator workflow (replaces the manual create → find → generate → publish loop):**

1. **Kick off Review Set** — admin names an Official Review Set, picks Course/Program, and links or creates a `curriculum_template`. If the template is new, AI drafts the objective list (subjects → modules → note-sized concepts) via the v0.42.0 per-section AI-assist pattern; **admin edits and explicitly saves** before anything else runs. An ACTIVE Official Review Set already linked to this curriculum short-circuits to "update the existing one instead."
2. **System proposes Subject Plans** — objectives grouped by `subjectLabel` become proposed Subject Plan sections (modules → item `label` free text). Proposal only; admin can rename, merge, reorder, or discard sections.
3. **Match scan auto-attaches existing notes** — the reuse search order (steps 1–4: public notes with packs → admin internal drafts → cross-curriculum confirmed fulfillments → prior-Review-Set co-occurrence) writes `curriculum_objective_fulfillments` rows at status **SUGGESTED**, never CONFIRMED. Zero LLM cost; deterministic + embedding match with opaque `matchSource`/`matchConfidence`.
4. **Coverage Board highlights gaps** — one screen per template: every objective is Confirmed / Suggested / Gap. Admin confirms or rejects each suggestion (SUGGESTED → CONFIRMED/REJECTED, `confirmedBy` stamped). Only CONFIRMED-to-PUBLIC counts toward coverage.
5. **Generate only missing** — admin multi-selects Gap objectives → one batch of `curator_generation_requests` (source CURATOR_GAP_SCAN, REQUESTED). Generation reuses the bulk-generation pipeline internals (ADMIN bypass, throttled sequential fan-out, note-from-topic + async Study Pack) with objective `conceptTitle` + `description` as the topic seed, subject = `subjectLabel`, courseProgram from the template, PREMIUM tier — and **always PRIVATE**; the bulk Public toggle is never exposed here. Drafts land as ordinary admin-owned notes; requests flip to DRAFT_GENERATED.
6. **Review queue** — every generated draft passes mandatory human review: note editor + Study Pack preview + the AI Suggestions review-and-decision pattern for metadata. Actions: Approve & Publish note (note → PUBLIC, fulfillment → CONFIRMED, request → PUBLISHED), Edit-then-approve, Regenerate (explicit, new draft, stays IN_REVIEW), Reject (request → REJECTED, draft stays private).
7. **Assemble & publish Review Set** — confirmed notes auto-slot into Subject Plans in objective `position` order (proposed layout, admin reorders). The existing publish gate — every item note must be PUBLIC — is unchanged; publish stamps `curriculum_template_version_at_publish`.

**Where the review gate sits — three human gates, one structural backstop:**
- **Gate A (template):** AI-drafted objectives are never ACTIVE until the admin saves them (step 1).
- **Gate B (matches):** SUGGESTED fulfillments never count as coverage until a human confirms (step 4).
- **Gate C (generated content):** no generated note becomes PUBLIC except through per-note approval in the review queue (step 6). There is no batch-approve of unopened drafts and no auto-publish path anywhere.
- **Structural backstop (existing, unchanged):** Review Set publish requires every item note PUBLIC — an unreviewed draft cannot reach a learner through a published plan even by bug.

**Low-volume stance (Q6):** the workflow is a wizard overlay on existing screens, not new infrastructure — every step degrades to today's manual path; the only always-on new surface is the Coverage Board + review queue. Value at N=3 Review Sets comes from the template as a durable asset: coverage %, staleness signal, the learner-request demand queue reusing the same `curator_generation_requests` table, and cross-curriculum fulfillment reuse driving the Nth curriculum's marginal cost toward zero.

---

# Full detail

## 1. What this replaces, and why the shape changed

### 1.1 Today's manual loop

The current Internal Curator workflow is entirely manual and entirely in the admin's head:

1. Admin creates an Official top-level Review Set in the Study Plan Builder.
2. Admin creates Subject Plans (child collections) under it, inventing the subject/module structure from memory or an external syllabus document.
3. Admin searches the Library and Public Library by hand for notes that cover each concept.
4. For concepts with no existing note, admin runs Bulk Generate (with the ADMIN quota bypass) using a hand-typed topic list, waits for the batch, then hand-checks each result.
5. Admin attaches everything, flips notes PUBLIC, and publishes.

Three structural problems: (a) the requirements list — "what must this Review Set cover" — never exists as data, so nothing can be checked against it, gap detection is human memory, and staleness is invisible; (b) reuse is accidental — an existing perfect public note is only attached if the admin happens to remember it; (c) generation is unscoped — bulk topic lists are typed fresh, so admins re-generate content that already exists (real LLM cost) or miss concepts silently.

### 1.2 The design move

The single move that fixes all three: **make the requirements list a first-class entity (`curriculum_template` + `curriculum_objectives`) and derive everything else from it.** Subject Plan structure is derived (objectives grouped by subject/module). Attachment is derived (match scan against objectives). Gaps are derived (objectives minus confirmed fulfillments). Generation scope is derived (exactly the gap set, nothing more). Staleness is derived (template version vs. version-at-publish). The admin's job shifts from *doing* each step to *confirming* each step — which is precisely the "curation, never generation" posture: the system proposes at every stage, the human disposes at every gate.

## 2. The workflow, screen by screen

All curator screens live under the existing ADMIN-gated `/admin` surface (route family `/admin/curation/...`), behind the same role check as the Admin Dashboard (`ADMIN` role; non-admins redirected). Per the admin-dashboard doc's v1 philosophy, these screens stay simple, desktop-oriented, and dark-mode-capable — no charts, no complex filters.

### Screen 1 — Review Set kickoff (`/admin/curation/new`, extends the Study Plan Builder entry point)

**Admin provides:** Review Set title, Course/Program (shared combobox — never freetext, per taxonomy rule), and one of:
- **Link existing template** — pick an ACTIVE `curriculum_template` for this course/program.
- **Create new template** — opens the template editor inline.

**System proposes:**
- If an ACTIVE Official Review Set is already linked to the chosen template (reuse search order step 0, whole-request short-circuit), the screen says so and offers "Open the existing Review Set" as the primary action. Building a duplicate requires an explicit "create another anyway" — this prevents the highest-cost mistake (parallel curation of the same curriculum) before any work starts.
- For a new template: an **AI-drafted objective list** — subjects → optional modules → note-sized concept titles with one-line descriptions and positions. This reuses the v0.42.0 Companion per-section AI-assist pattern verbatim: the draft renders in an editable staging area, section by section (one subject = one section); the admin can regenerate a single section, hand-edit rows, add/remove/reorder, and nothing exists in the database as ACTIVE until the admin clicks **Save curriculum**. The LLM call is calibrated by courseProgram only — never learnerLevel — consistent with the foundation decision that static curated content is program-scoped.

**Human confirms (Gate A):** the objective set. Saving sets the template ACTIVE at `version = 1`. Later edits to the objective set bump `version` — which is what later drives the `mayBeOutdated` signal on published Review Sets.

**Reuse note:** the AI-assist here is the *same interaction contract* as Companion section authoring — propose → edit → explicit apply — not a new pattern. Prompt assets are new (objective-list drafting), the UX and review discipline are not.

### Screen 2 — Coverage Board (`/admin/curation/templates/{id}`) — the workflow's center of gravity

One screen answering one question: **for each objective, does published content exist, and if not, why not?**

Layout: objectives grouped by `subjectLabel` (collapsible), then `moduleLabel`, in `position` order. Each objective row shows exactly one of three states:

| State | Meaning | Row shows | Row actions |
|---|---|---|---|
| **Confirmed** | CONFIRMED fulfillment to a note | note title, PUBLIC/PRIVATE badge, Study Pack badge | swap note, unconfirm |
| **Suggested** | SUGGESTED fulfillment(s) from the match scan | best match + confidence band (high/medium/low — the opaque `matchConfidence` bucketed for display, never a raw score), `matchSource` chip ("Public note", "Internal draft", "Used in {other curriculum}", "Frequently paired") | **Confirm**, **Reject**, view note, see other candidates |
| **Gap** | no non-rejected fulfillment | — | **Queue for generation**, attach manually (note search) |

Header: coverage bar — **CONFIRMED fulfillments to PUBLIC notes ÷ total objectives** (the only number that counts, per the foundation decision; suggested and private-confirmed shown as secondary segments, not in the headline figure). Plus two buttons:

- **Run match scan** — executes reuse search order steps 1–4 against all unfulfilled objectives and writes SUGGESTED fulfillment rows. Idempotent: re-running refreshes suggestions for still-open objectives, never touches CONFIRMED or REJECTED rows (REJECTED is a durable human decision — the scan must not resurrect a rejected match as a fresh suggestion). Zero LLM generation cost; this is retrieval (title/subject/tag matching plus embedding similarity), and its provenance stays opaque (`matchSource`/`matchConfidence`) so match internals can improve without schema churn.
- **Generate missing (N)** — enabled when Gap objectives are selected; hands off to Screen 3.

**System proposes:** matches, ranked. **Human confirms (Gate B):** every attachment. A SUGGESTED row contributes nothing to coverage and can never flow into a published Review Set — confirmation is the only path in. This is deliberate even for high-confidence matches: a wrong attachment in an Official Review Set is a curation error learners inherit on adopt, so the cost asymmetry favors a mandatory click over auto-confirm. At current volume (tens of objectives per template, not thousands) the click cost is trivial.

Cross-curriculum reuse surfaces here concretely: when a match's `matchSource` is a confirmed fulfillment from another template ("Used in PNLE Review — confirmed"), the admin is reusing prior *curation judgment*, not just content — the second and Nth curricula get progressively cheaper, which is the core of the cost model.

### Screen 3 — Gap-fill generation (modal from the Coverage Board)

**Admin provides:** the selection of Gap objectives (checkboxes; "select all gaps" honors subject grouping).

**System proposes:** the batch manifest — one row per objective showing the topic seed it will generate from (`conceptTitle` + `description`), the subject it inherits (`subjectLabel`), and the shared context (courseProgram from the template, PREMIUM LLM tier, target audience derived from the template's course/program). A count line: "This will generate N new notes. M other gaps have existing suggestions you haven't reviewed" — a nudge to exhaust free reuse before paying for generation.

**Human confirms:** clicking **Generate N draft notes** creates one `curator_generation_request` per objective (source CURATOR_GAP_SCAN, status REQUESTED) and queues one batch.

**Pipeline reuse — this is bulk-generation's engine with three deltas:**

The batch runs the existing bulk-generation internals unchanged: ADMIN bypass of note-generation quota, Study Pack quota, and rate counters (already scoped locally to bulk orchestration, exactly as this needs); throttled sequential fan-out on `studyPackGenerationTaskExecutor`; per-topic isolation (one failure never aborts the rest); `generateFromTopic` for note content; `NoteService.create`; async Study Pack start; the batch-subject-wins metadata rule (the objective's `subjectLabel` is authoritative — AI title/tag refinement lands, AI subject cannot displace the curriculum's subject).

The three deltas:
1. **Seed:** topic = objective `conceptTitle` + `description`, keyed by `objectiveId` — not a hand-typed topic list.
2. **Visibility:** always PRIVATE. Bulk's Public toggle is not exposed on this path and cannot be reached — visibility is not a parameter here, it is a consequence of review. This is the load-bearing difference between "bulk generate public notes" and "draft into a review queue."
3. **Receipt → request rows:** bulk's read-once terminal receipt exists because bulk has no per-item entity. This flow *has* one — `curator_generation_requests`. On per-topic completion the worker sets `draftNoteId` and flips the request to DRAFT_GENERATED; on per-topic failure the request stays REQUESTED with a visible retry affordance. No read-once receipt row is written for curator batches, and no new progress infrastructure is added: the Coverage Board and review queue simply re-read request status on load/refresh (the same load-on-refresh posture as bulk's Library poller — status materializes when you look, nothing streams).

Under the two-system split, this same table is where the Learning Assistant files LEARNER_REQUEST rows (deduped, `demandCount++`). Both sources drain through the identical review gate — there is exactly one queue and one gate, not a curator path and a separate learner-demand path.

### Screen 4 — Review queue (`/admin/curation/review`) — Gate C, the mandatory one

Lists `curator_generation_requests` in DRAFT_GENERATED / IN_REVIEW, default-sorted by `demandCount` desc then age (learner-demanded gaps first — the queue is shared, so demand signal prioritizes curator attention automatically). Each row: concept title, subject, source chip (Gap scan / Learner request ×N), template link, draft age. Opening a row moves it to IN_REVIEW and shows the review screen:

- **Note content** in the standard note editor — the draft is an ordinary admin-owned `NoteEntity`, so this is the existing editor, not a new one. Edits save normally.
- **Metadata review** using the AI Suggestions review-and-decision contract from `ai-suggestions.md`: the AI-refined title and tags from Study Pack write-back render side-by-side with the objective-derived originals, radio-button decisions per field, chips for tags with case-insensitive dedup, live preview, `Apply Changes` / `Skip`. Subject is pinned display-only — the curriculum's `subjectLabel` is authoritative and not offered for AI replacement (stronger than the normal flow's subject-resilience rule, same spirit). Decisions are transient until applied, exactly as the normal note flow requires.
- **Study Pack preview** read-only, with pack status; a FAILED pack shows the existing retry affordance. Approval does not require a ready pack (publish rules for notes don't), but the screen surfaces it so the curator publishes knowingly.
- **Objective context** panel: the objective's description and the template it belongs to, so the reviewer judges *fit to requirement*, not just standalone quality.

**Actions (each one human, each one explicit):**

| Action | Effect |
|---|---|
| **Approve & Publish** | Note → PUBLIC; fulfillment for `objectiveId` → CONFIRMED (`confirmedBy` = this admin); request → PUBLISHED. Coverage Board updates on next load. |
| **Save edits** (then approve later) | Ordinary note save; request stays IN_REVIEW. |
| **Regenerate** | Explicit re-run of the single-topic generation for this objective; replaces draft content, request stays IN_REVIEW. Never automatic — consistent with the global never-auto-regenerate rule. |
| **Reject** | Request → REJECTED; the draft note remains an admin-private note (it may still surface later via match-scan step 2 as an internal draft — rejection of *this* draft is not deletion of the content). Objective returns to Gap. |

**There is no bulk-approve.** Approve operates on the open, rendered draft only. This is the review gate's teeth: at expected volumes (single-digit to low-double-digit drafts per batch) per-note review is cheap; the moment it isn't cheap, the correct response is smaller batches, not a weaker gate.

### Screen 5 — Assemble & publish (back in the Study Plan Builder, extended)

**System proposes:** the full Review Set layout — one Subject Plan per `subjectLabel`, items in objective `position` order, `moduleLabel`s materialized into existing item `label` free text (the backend continues not to interpret labels — this is display structure only, per the foundation decision). Every item is a CONFIRMED-fulfillment note. Objectives still unfulfilled render as a visible "not covered" list beside the layout — the admin publishes with open gaps knowingly or goes back to close them; partial coverage is a legitimate, honest state, not a blocker.

**Human confirms:** section names, ordering, item placement — all editable in the existing Builder before publish.

**Publish (structural backstop):** the existing gate — every item note must be PUBLIC — runs unchanged and is what makes the whole design safe-by-construction: even if a bug confirmed a private draft into the layout, publish refuses. On success, `curriculum_template_id` and `curriculum_template_version_at_publish` are stamped on the collection. Later objective edits bump the template version and light the ADMIN-only `mayBeOutdated` signal on this Review Set (same pattern as `companionMayBeOutdated`); refreshing it is a deliberate re-run of the Coverage Board → re-assemble loop, never an auto-republish, and adopted learner copies remain snapshots that never auto-update.

## 3. Proposes vs. confirms — the full ledger

| Step | System proposes | Human confirms | Gate |
|---|---|---|---|
| 1. Kickoff | AI-drafted objective list; duplicate-curriculum warning | Objective set (edit + Save); proceed-anyway on duplicates | **A** |
| 2. Subject Plans | Sections derived from subject/module grouping | Names, merges, ordering | (folded into 5) |
| 3. Match scan | SUGGESTED fulfillments with confidence + provenance | — (scan writes suggestions only) | — |
| 4. Coverage Board | Ranked match candidates per objective | Every Confirm/Reject, one by one | **B** |
| 5. Gap-fill | Batch manifest (seeds, count, context) | Generate N — the only LLM-spend decision in the flow | — |
| 6. Review queue | Draft note + AI metadata suggestions | Per-note Approve & Publish / edit / regenerate / reject | **C** |
| 7. Publish | Assembled layout + uncovered-objectives list | Layout edits; Publish | structural |

Reading the ledger top to bottom: the system never *decides* anything — it narrows the decision to a confirmation, and the three decisions that create learner-visible or costly artifacts (objective set, coverage attachments, published notes) each have a named, mandatory human gate. No path exists from "LLM output" to "learner-visible" that does not pass Gate C plus the structural publish backstop.

## 4. Q6 — the low-volume caveat, designed for honestly

Dev data shows very few Official PUBLIC top-level Review Sets today. A workflow engine that only pays off at 50 curricula would be over-build. Design responses:

**1. Wizard over screens, not a new subsystem.** Steps 1, 2, 5, and 7 are the existing Study Plan Builder and note editor with proposal overlays; step 6 reuses the note editor plus the existing AI Suggestions contract. Net-new UI is two focused screens (Coverage Board, review queue) and one modal (gap-fill manifest) — all thin reads over the four foundation tables. No dashboards, no charts, no analytics events, no live-progress infra (explicitly matching bulk-generation's out-of-scope list). If the curation program stalls at three Review Sets, the sunk UI cost is small.

**2. Every step degrades to the manual path.** Skip the AI objective draft and type objectives by hand. Ignore the match scan and attach notes via manual search. Never touch gap-fill and run Bulk Generate the old way, then attach results manually on the Coverage Board. The workflow accelerates the existing path rather than replacing it, so partial adoption is stable — there is no cliff where half-using the new flow is worse than the old flow.

**3. The template is the asset; the workflow is just how you exercise it.** Even at N=1, once a curriculum template exists it keeps paying:
- **Coverage as a standing answer** to "is our {exam} Review Set complete?" — previously unanswerable without a manual audit.
- **Staleness detection** — the version-stamp mechanism turns "the syllabus changed" from silent rot into an ADMIN-visible signal.
- **The learner-demand queue** — `curator_generation_requests` from LEARNER_REQUEST accumulates real demand (`demandCount`) against real objectives with zero curator effort; at low volume this is the *discovery* mechanism for which curriculum to invest in next. The workflow's most valuable screen at N=1 is arguably the review queue sorted by demand.
- **Cross-curriculum fulfillment reuse** — the first template is the most expensive; every subsequent one starts with match-scan hits against prior confirmed curation.

**4. Low volume makes the strict gates cheap, and cheap gates are the point.** Mandatory per-suggestion confirmation and per-note review are only tenable because batches are small — which they are, and will be for the foreseeable roadmap. The design deliberately spends the low-volume dividend on review rigor instead of throughput features (bulk-approve, auto-confirm-above-threshold) that would erode the curation guarantee precisely when the catalog starts to matter. If volume ever demands throughput, the pressure should be resolved by adding curator capacity or narrowing batch size — the gates are the product's trust boundary, not a scaling bottleneck to optimize away.

**5. Admin Dashboard touch is one card, later, optional.** At most: a "Curation" summary card (open requests, drafts awaiting review, coverage of the flagship template) linking to the review queue — consistent with the dashboard's read-mostly card philosophy. Not required for the workflow to function; explicitly deferrable.

## 5. Reuse map (what is extended vs. net-new)

| Shipped capability | Role here | Delta |
|---|---|---|
| Bulk-generation pipeline (ADMIN bypass, sequential fan-out, `generateFromTopic`, per-topic isolation, subject-wins write-back) | Gap-fill engine (Screen 3) | Objective-keyed seeds; forced PRIVATE; request rows instead of read-once receipt |
| AI Suggestions modal contract (side-by-side, radios, tag chips + dedup, live preview, transient-until-applied) | Draft metadata review (Screen 4) | Subject pinned to curriculum `subjectLabel` (display-only) |
| Study Plan Builder + Official Review Set publish gate (all items PUBLIC) | Kickoff (Screen 1) and Assemble/Publish (Screen 5); the structural backstop | Two nullable FK/int columns on `note_collections`; proposed-layout overlay |
| v0.42.0 per-section AI-assist (propose → edit → explicit apply) | Objective drafting (Screen 1) | New prompt assets; same interaction contract |
| Companion `mayBeOutdated` staleness pattern | Template-version drift signal on published Review Sets | Same pattern, curriculum-version trigger |
| Note editor + Study Pack preview/retry | Draft review surface (Screen 4) | None — drafts are ordinary notes |
| Admin role gating + `/admin` surface conventions | All curator screens | New routes under the existing guard |

Net-new: the four foundation tables, the Coverage Board, the review queue, the gap-fill modal, match-scan retrieval, and objective-drafting prompts. Everything else is the shipped spine.
