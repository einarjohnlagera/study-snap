# AGENTS.md - NoteLib

**v0.105.0 implementation note:** `ExamSourceLimitResolver` still owns the sole `questionCount / 3`
formula, but for Long Exam it now means **how many sources we sample from the eligible curriculum pool**,
not how many notes a learner may pick. A plan-sourced **Long Exam** remains anchored on the caller-supplied
primary Study Pack: the primary is force-included in the sample at index 0. `v0.113.0` changes only
plan-scoped Adaptive Practice anchoring; it does not move Long Exam or Board Exam anchors.

**v0.113.0 implementation note:** a `quick_review_sessions` row has either a non-null
`(study_pack_id, note_id)` pair or a `source_collection_id`. Plan-scoped Adaptive Practice writes the
collection column and the existing `session_state.sourceCollectionId` key. Every reader must use the one
column-first, JSONB-fallback resolver; the JSONB leg keeps pre-migration in-flight sessions resumable and must
not be removed until no such row can remain active.

Long Exam's quota charge and failure reversal must both derive from `LongExamService.QUOTA_UNITS_PER_SESSION`;
the reservation and reversal session-state keys are public constants on that same class so the recovery sweeper
cannot drift from the start path.

Board Exam reserves and reverses its Challenge Quiz and Board Exam meters together using
`ChallengeQuizService.BOARD_EXAM_QUOTA_UNITS_PER_SESSION` and one idempotency stamp. Both meter decrements
must clamp in SQL; never rely on the Challenge-meter CHECK constraint to make a partial refund safe.

`combined_quizzes` rows are immutable snapshots: never update, re-assemble, append, or delete their
questions in place. `quiz_share_links` is one exclusive target arc — exactly one of `generated_quiz_id` and
`combined_quiz_id` must be populated — so do not split the token space or share-link counter into a second
table.

**⚠️ ASK WHAT IN THE FIXTURE WOULD HAVE TO CHANGE FOR THIS TEST TO FAIL — three fixtures in one session were
unfaithful in the same direction, each caught by MUTATION rather than review.** A mocked repository whose
derived query never executes (so a dropped owner predicate passes the suite); a `generateGeneratedQuiz` mock
returning a DIFFERENT quiz id when production reuses the row (so an id-keyed effect re-fetched on its own and
the assertion passed without the fix); and a round-trip fixture whose choice text did not actually look
labelled (so it survived the defect and the fix alike). **The common shape: the fixture differs from
production on exactly the property under test.** Naming the mutant you expect to kill, before writing the
assertion, is what catches it.

You are an AI coding agent helping implement NoteLib.
Follow these rules to keep the codebase consistent and shippable.

Rebrand note: StudySnap has been renamed to NoteLib. Keep existing database schema/table names unless explicitly requested.

Current documentation baseline:

- `v0.115.0 - Learner Publication Authority` (In Progress); previous: `v0.114.0 - Connection Evidence` (Released); previous: `v0.113.1 - Anchoring Hardening` (Released); previous: `v0.113.0 - Session Anchoring` (Released, deployed); previous: `v0.112.0 - Connection Pool Integrity` (Released, deployed); previous: `v0.111.0 - Multidisciplinary Domain Context` (Released); previous: `v0.110.2 - Shared Link Integrity` (Released); previous: `v0.110.1 - Quiz Text Integrity` (Released, deployed); previous: `v0.110.0 - Supporter Combined Quiz` (Released, deployed); previous: `v0.109.0 - Assessment Discoverability` (Released, deployed); previous: `v0.108.0 - Session Identity` (Released, deployed); previous: `v0.107.0 - Curriculum-Scale Remediation` (Released, deployed); previous: `v0.106.0 - Board Exam Review Set Identity` (Released, deployed); previous: `v0.105.0 - Curriculum-Scale Exams` (Released, deployed)

Implementation status: Phases 1-4 are **Released** (`v0.91.0`-`v0.94.0`), with **Phase 4 PARTIAL**: shareable invitation links and connection management shipped in `v0.94.0`; **supporter onboarding did NOT**. **⚠️ The reason it did not is an ASSUMPTION nobody has checked, found at the `v0.95.0` kickoff (2026-08-29):** `v0.94.0` blocked it on the onboarding freeze, but `[CHECKPOINT — due 2026-09-11]` is the **signup funnel read alone** (375 signups against a 62.4% completion baseline, measuring `app/onboarding/page.tsx`), **"supporter onboarding" has no definition anywhere in the plan**, and the redemption page already treats `/onboarding` as a waypoint it carries a token through rather than a surface it edits. **It is NOT claimed unblocked — it is claimed unchecked.** The discriminating test is whether the work edits the signup → verify-email → onboarding path; it needs a definition step, which is **`v0.97.0` item 3 — docs only, no code on the frozen path**. **No public people search is in Phase 4 at all.** **Phase 5 remains uncommitted and must not be stubbed.** **⚠️ An `ACCEPTED` relationship implies no access of any kind** — material, activity and progress each need their own live grant, and streaks/study days are reachable only through `ACTIVITY`.

**⚠️ CARRIED INTO THE NEXT RELEASE, NOT GATED — Domain Context curator copy blocks the ALE Review Set build (owner, 2026-08-31).** A Stage 1 calibration audit ran 2026-08-31 (two cold agents; full findings and unrun production SQL: `docs/claude-plans/domain-context-taxonomy-calibration-audit.md`). **VERDICT: NO TAXONOMY CHANGE — `ARCHITECTURE` is NOT added, and NOT because it failed the bar. It PASSED the governance floor and the exclusion trap and is still a PROVABLE NO-OP:** a Domain Context value's entire generation payload is its **label string plus one `quantitative` boolean**, and the resolver already falls back to the single joined catalog program name — so a single-program Architecture note **already sends `Domain: Architecture`**. A new value would emit a byte-identical line. **⚠️ Reading this as "Architecture didn't qualify" is the wrong takeaway and will re-open it; the counter-argument is recorded at full strength in the audit.** **What DOES block the owner is copy, not vocabulary:** ~41 Building Utilities notes generate with **computation guidance silently OFF** (no `architecture` in `QUANTITATIVE_KEYWORDS`), the fix is classifying them `ENGINEERING_SCIENCES` (`quantitative=true`), and the curator cannot discover that because `frontend/lib/domain-context.ts` describes that value as a **Civil-flavoured list omitting plumbing, HVAC, lighting, acoustics and fire protection**. **⚠️ Ship: widen that description, correct `CIVIL_ENGINEERING`'s (it wrongly claims Surveying and Construction Management), and re-word `docs/gpt-contexts/REVIEW_SET_SHAPING_CONTEXT.md`, which pre-commits the strategist with *"Architecture, notably, deliberately has no Domain Context"* — the reason 215 ALE rows came back `(unset)` and a pre-emption of the very question `[CHECKPOINT — due 2026-09-28]` asks.** **⚠️ EXPLICITLY NOT GATED on that checkpoint, and it does not pre-empt it** — classifying under existing values is not a taxonomy action, and `ADR-001` records the multi-program rule as itself the forcing function generating the calibration evidence. **⚠️ Do NOT add `ARCHITECTURE` or `GENERAL_ENGINEERING`, do NOT extend `QUANTITATIVE_KEYWORDS`, do NOT remove the zero-usage values, do NOT resolve the zero-usage question by reasoning, and do NOT write a second rubric — R4's exists.** **⚠️ `v0.99.0` — Connection Completeness.** Three items, all closing `v0.98.0` Known limitations. **(1) AN EXPIRY STAYS VISIBLE AFTER A PAUSED OR BACKLOGGED SWEEP.** Retention is keyed on the DEADLINE, but `v0.98.0` added a 500-row batch bound and a pause hook, and both create backlogs — a request swept more than `request-ttl-days` after its deadline becomes `EXPIRED` with a timestamp already outside the window, so it **vanishes instantly and is never shown as expired**. **⚠️ OWNER DECISION 2026-08-31: A SEPARATE `expired_at` COLUMN**, over re-keying retention. `expires_at` therefore keeps meaning **the deadline for every status**; the table already carries a distinct terminal timestamp per status (`accepted_at`, `revoked_at`). **⚠️ THIS NEEDS THE THIRD AMENDMENT to the `v0.95.0` column prohibition** — raised explicitly, not reasoned past: it **adds a new fact rather than reinterpreting an existing one**, and none of `[2026-09-19]`, `[2026-09-26]`, `[2026-10-13]` is affected. **⚠️ V130'S BACKFILL IS THE TRAP: `expired_at` FROM `expires_at`, FOR `EXPIRED` ROWS ONLY, and nothing for any other status.** `v0.97.0` got this shape wrong TWICE — once expiring inherited consent pauses immediately, once merely delaying it 30 days — and both times by writing a timestamp onto rows that should have had none. **⚠️ `expires_at` MUST NOT BE OVERWRITTEN, RE-PURPOSED OR BACKFILLED; a NULL there is the entire mechanism protecting a consent-paused relationship.** **(2) THE ONBOARDING BAR REACHES `invite()` AND `accept()`.** Both still gate on `assertProfileComplete` alone, **which passes for a brand-new account**, so a verified-but-not-onboarded user can invite as SUPPORTER and reach an `ACCEPTED` relationship with cross-user read capacity. **⚠️ The property is currently HALF TRUE, which reads as enforced and is not.** **⚠️ It rejects the same two live cohorts as `v0.97.0`/`v0.98.0` — a failed `completeOnboarding` POST and the copy-on-signup cohort — so the `/onboarding` self-heal ships WITH the gates.** **⚠️ `accept()` is called internally by `acceptInvitation`, so check for double-gating**, and it is the guardian-consent path a supporter uses. **⚠️ Do NOT touch `assertProfileComplete`.** **(3) A TEST PINS THE THREE CONFIGURABLE CRONS to their production defaults**, which nothing in the build currently does. **⚠️ Do NOT add, remove or reorder an onboarding FLOW step and no code under `frontend/app/onboarding`** — `[CHECKPOINT — due 2026-09-11]` is 11 days out. **⚠️ Every standing connection rule is unchanged.** **Pre-declared: ONE SCOPED COLD AGENT framed as falsification** (item 1 changes production-data semantics via a migration; item 2 moves an authorization boundary — both one-agent triggers). **⚠️ Item 2 gets its `advisor()` call BEFORE implementation**, third gate tightening in three releases. **⚠️ The cold agent must be asked to COUNT EXECUTED TESTS, not read them** — `v0.98.0`'s worst finding was a test that had silently stopped running while the build stayed green. Previous release scope: **⚠️ `v0.98.0` — Connection Consistency.** Five items, all `v0.97.0` Known limitations or Backlog rows; the ceiling is deliberate, since nine items cost `v0.97.0` the full three-agent test plus a re-audit. **(1) THE EMAIL INVITATION PATH REQUIRES FINISHED ONBOARDING TOO.** `acceptInvitation` forms the IDENTICAL relationship as a link redemption — same guardian-consent handling, same cross-user capacity — but gates on `requireEmailVerified` + `assertProfileComplete`, whose own comment records it *"passes for a brand-new account."* **⚠️ OWNER RULED 2026-08-30 to tighten rather than document the asymmetry.** **⚠️ IT WILL NEWLY REJECT LIVE ACCOUNTS AND THAT IS KNOWN IN ADVANCE THIS TIME:** the frontend gates on `needsOnboarding()`, NOT `onboardingCompletedAt != null`, and two cohorts sit in the gap — a failed `completeOnboarding` POST (marker never retries) and the copy-on-signup cohort (dismissible prompt). **⚠️ SO THE `/onboarding` SELF-HEAL SHIPS WITH THE ITEM**, routing a `COMPLETE_ONBOARDING` remedy as `v0.97.0` taught the invite page to; without it this is a dead end. **⚠️ Do NOT touch `assertProfileComplete`** — deliberate exemption, protects the activation funnel, ~20 call sites. **⚠️ The gate runs BEFORE any invitation lookup** or it becomes an oracle beside `v0.90.0`'s single not-found contract. **(2) TERMINAL CARDS BECOME DISMISSIBLE.** **⚠️ Whether dismissal is per-viewer state or a PERSISTED FIELD is a data-model decision OWED BEFORE ANY CODE** — a server field means a migration and a column three dated checkpoints would read. **⚠️ Dismissal never deletes the relationship row.** **(3) A MIGRATION HEALS GRANT ROWS on relationships terminated before `v0.97.0`** — **⚠️ DISPLAY-ONLY, no access was ever open**, and **⚠️ TERMINAL STATUSES ONLY, NEVER the `ACCEPTED → PENDING` consent pause**, where `v0.93.0` made the row survive by design. **(4) THE SWEEP'S OPERATIONAL GAPS** — no `LIMIT` on `findDuePendingIds`, no `StudySnapProperties` field or `application.yaml` entry for `request-expiry-cron`, a bare `NoSuchElementException` on a benign delete race. **⚠️ No change to what expires or when.** **(5) `DATA_MODEL.md`'s LINKED-LEARNER SECTION**, frozen at `v0.89.1`: three statuses where there are four, *"Only ACCEPTED authorizes the progress read"* which has been false since `v0.93.0`, and four tables missing. **⚠️ Do NOT add, remove or reorder an onboarding FLOW step and no code under `frontend/app/onboarding`** — `[CHECKPOINT — due 2026-09-11]` is 12 days out. **⚠️ Do NOT change what expires, when, or from which clock**, and **⚠️ do NOT backfill `expires_at` onto inherited rows** — `v0.97.0` established after two wrong attempts that a NULL deadline IS the protection for a consent pause. **⚠️ Every standing connection rule is unchanged.** **Pre-declared: ONE SCOPED COLD AGENT framed as falsification** (item 1 moves an authorization boundary; item 3 changes production-data semantics — both one-cold-agent triggers, neither a permission substrate). **⚠️ Item 1 gets its `advisor()` call BEFORE implementation**, being the same gate-tightening class as `v0.97.0` item 5 and `v0.71.0`'s ADMIN lockout. Previous release scope: **⚠️ `v0.97.0` — Connection Lifecycle.** Seven items: **everything un-gated on the Learning Connections surface.** **(1) UNCONFIRMED REQUESTS EXPIRE — a new `EXPIRED` terminal status** on `linked_learner_relationships`, plus `expires_at` and a sweep. **⚠️ THE TTL MUST BE ≥30 DAYS AND DATED FROM RELATIONSHIP `created_at`** — that is what keeps the earliest possible expiry (2026-09-28) after `[CHECKPOINT — due 2026-09-26]`, whose read is a `COUNT(*)` on the rows this sweep deletes and whose **kill criterion names expiry as its own prescribed response**. A shorter TTL or a different clock destroys that read. **⚠️ `ACCEPTED` relationships NEVER expire**; only unconfirmed `PENDING`. **⚠️ `EXPIRED` is TERMINAL** — no un-expire, and re-inviting mints a NEW relationship exactly as revoke does. **⚠️ Conditional updates, never read-modify-save** (`linked-learners.md`), matching `markAcceptedIfPending` / `markRevokedIfLive`. **⚠️ The provisional row is DELETED on expiry**, per `v0.89.1`'s rule that declared-value history is a minor's personal data and is not retained. **⚠️ This is the REQUEST's clock, not the CARRIER's** — do not touch `invitation-ttl-days`, which `[CHECKPOINT — due 2026-10-13]` reads. **⚠️ Ship the TTL as CONFIGURATION, not a literal.** **(2) THE PROVISIONAL BIRTH YEAR APPEARS IN THE ACCOUNT DATA EXPORT** — owner ruled INCLUDE IT 2026-08-29, on **completeness, not user value**; the export is a compliance surface. **⚠️ Export it as a DISTINCT PROVISIONAL field, never merged into `birthYear`** — merging would falsify `users.birth_year`'s write-once account-global meaning. **⚠️ Export only rows where the CALLER is the learner** — the table is keyed by relationship, so a naive join exports another person's declaration. **(3) SUPPORTER ONBOARDING — THE DEFINITION STEP, DOCS ONLY, NO CODE.** It is **UNDEFINED, not gate-blocked**; lifting the freeze does not scope it. **⚠️ Do NOT add, remove or reorder an onboarding FLOW step** — `[CHECKPOINT — due 2026-09-11]` is still owed against a 62.4% completion baseline. Implementation waits on BOTH the definition and that read. **(4) TERMINAL TRANSITIONS CUT GRANT ROWS — ONE RULE for revoke and expiry.** **⚠️ Today's gap is INERT and must stay described as inert** — `requireGrant` demands `ACCEPTED`, so a live grant row on a dead relationship grants nothing; this is defence-in-depth plus DTO honesty (`*SharedByMe: true` on a terminated relationship), NOT a live privilege escalation. **⚠️ A PAUSE IS NOT A TERMINATION — this is the sharpest trap:** `v0.93.0` made the grant row survive an `ACCEPTED → PENDING` consent pause **by design**, so the learner's own toggle does not read OFF and sharing resumes on re-acceptance. Cut grants on TERMINAL statuses only (`REVOKED`, `EXPIRED`) and never on the pause. **⚠️ `requireGrant` keeps demanding `ACCEPTED`** — do not relax it because the rows are now cleaned up. **(5) `requireVerifiedOnboarded` ACTUALLY REQUIRES ONBOARDING** — `assertProfileComplete` passes when `onboardingCompletedAt` is null, so the stated property is false today. **⚠️ IT TIGHTENS A GATE, so check who it locks out BEFORE shipping** — `v0.71.0`'s lesson is that an onboarding guard made onboarding uncompletable for every ADMIN account. **⚠️ The redemption page carries a token THROUGH `/onboarding`** (`app/linked-learners/invite/[token]/page.tsx:45,56`), so a mid-onboarding caller on that path must still be able to finish the flow. **⚠️ Nothing downstream breaks today** — this fixes a false stated property, not an exploited hole. **(6) A TWO-THREAD REAL-ROW HARNESS for the provisional-row invariant** — *no `ACCEPTED` relationship may exist whose learner has a provisional row but a null `users.birth_year`*, because `LinkedLearnerGrantAuthorizationService` would then deny access with **no remediation path**. `v0.95.0` recorded this as *"the natural first item for any future release that touches this path"* and **item 1 touches it**: the sweep is a THIRD writer on that lock. **⚠️ `LinkedLearnerConcurrencyTest` MOCKS `LinkedLearnerProvisionalBirthYearRepository`**, and the real-PostgreSQL tests run single-threaded, so the provisional-row consequence has never been exercised under real concurrency. **(7) INVITATION-LINK ENDPOINT SECURITY COVERAGE, 1 OF 5 → 5 OF 5.** `.anyRequest().authenticated()` makes the rest safe today, but a single canary is the whole guard, and item 1 adds surface. **⚠️ Standing rules unchanged:** acceptance stays LOAD-BEARING; guardian consent is not bypassable and stays re-asserted inside the grant check, fail-closed on unknown age; **no account-existence oracle** and the single not-found contract for unknown/revoked/expired/redeemed tokens is preserved — **`EXPIRED` must not become a distinguishable failure**; absence of a live grant means NO ACCESS; the progress read stays UNIDIRECTIONAL with its explicit caller-is-supporter assertion; `PROGRESS` stays learner-issued only while activity stays mutual; every read re-verifies `ACCEPTED`, no cache, no grace; invitations stay ONE-AT-A-TIME and the quiz share link stays the many-recipient mechanism; **no relationship-type column, no new profile type, nothing gated on `ProfileType`**; `NoteVisibility` stays `PRIVATE | PUBLIC`; **no endpoint accepts a learner user id**; **no public people search**. **⚠️ Any predicate deciding whether a relationship expires, or whether a provisional year is promoted, read, exported or discarded, needs a REAL-ROW test** in `NativeQueryPostgresIntegrationTest`, mutation-verified with the killing test named — a mocked repository cannot test a predicate, and that class has now recurred four times. **⚠️ AMENDED AFTER KICKOFF 2026-08-29 — ALL GATES LIFTED BY THE OWNER. NINE ITEMS; TIER IS THE FULL THREE-AGENT COLD-CONTEXT PRESSURE TEST.** **(9) THE `v0.96.0` TITLE RULE ALSO LANDS IN `note-generation-developer.txt`** — **⚠️ THE SAME RULE, NEVER A SECOND FORMULATION**, because two wordings of one semantic idea is exactly how it degrades into the forbidden *"'in X' is bad"* form; *"Nursing Management of Acute Asthma"* stays CORRECT. The §5 read stops gating but is **not** deleted. **ITEM 3 IS NOW DEFINITION PLUS IMPLEMENTATION.** **⚠️ BUT `app/onboarding/page.tsx` AND THE SIGNUP → VERIFY-EMAIL → ONBOARDING FLOW STAY UNTOUCHED** — `[CHECKPOINT — due 2026-09-11]` is a LIVE MEASUREMENT WINDOW, so editing the flow **destroys** the read rather than confounding it, and the redemption page only **traverses** `/onboarding` via a cookie. Building on the connection and redemption surfaces keeps both. **⚠️ If the definition concludes the flow must be edited, bring it back as a NEW decision.** **⚠️ STILL OUT OF SCOPE:** `REVOKED`/`EXPIRED` card dismissal, and every Phase 5 item. Previous release scope: **⚠️ `v0.96.0` — Authoring Integrity.** Four items: **what the product generates, names and shows.** **(1) DEFERRED *SAVE ORDER* MODEL** in the Study Plan Builder — dragging performs NO network call; an explicit Save commits the whole plan once. **⚠️ THREE VERIFIED TRAPS:** `refreshBuilder` calls `setLeafItems` from **13 call sites** so non-drag mutations must **flush pending order, never discard**; there is no "section order", only per-item positions, so the dirty check needs a **last-saved baseline**; and a **500ms debounced combobox writer** at `study-plan-builder-page-client.tsx:443` fires on a timer a leave guard never sees. **(2) SUMMARY RENDERS MATHS** — **⚠️ the naive fix is WRONG**: `_` is markdown emphasis, so `$x_1 + x_2$` becomes `<em>` before post-processing could find the math. Add `remark-math` for **tokenization only** and render through the **existing KaTeX setup**, not `rehype-katex` — one math configuration, not two. **(3) CURATED TITLES NAME THE KNOWLEDGE, NOT THE CURRICULUM CONTAINER** — one unconditional rule in `developer.txt`. **⚠️ It changes the AI's default on learner-facing paths too and that is ACCEPTED.** **⚠️ POSITIVE, SEMANTIC wording only** — never *"'in X' is bad"*; *"Nursing Management of Acute Asthma"* is a CORRECT title. **⚠️ `note-generation-developer.txt` is OUT** until the unrun §5 feedback-loop read runs. **(4) DOMAIN CONTEXT DOCTRINE (docs only)** — **⚠️ `ADR-001`'s COARSEST-context rule is NOT amended**; correct the *"narrowest tradition"* wording wherever it appears; record only the four new items and LINK the rest. **⚠️ Do NOT add, remove or reorder an onboarding FLOW step** — `[CHECKPOINT — due 2026-09-11]` is still owed; rendering maths inside an existing preview is in scope, touching the flow is not. **⚠️ No title post-processing, no mass rename, no migration, no new analytics event, no backend change beyond the prompt file.** **⚠️ OUT OF SCOPE, held for `v0.97.0`:** the provisional birth year in the data export, unconfirmed-request expiry, and supporter onboarding — which is **undefined**, not gate-blocked. Previous release scope: **`v0.95.1` — Rendering and Reorder Fixes (PATCH).** Two items, both frontend, both from owner reports on 2026-08-29. **(1) RENDER MATH ON THE PLAIN-TEXT NOTE SURFACES** — the Full Notes body in `private-note-detail-page-client.tsx:2884` and the public library note page render in a bare `<p className="whitespace-pre-wrap">` with **no markdown and no math**, so `$Q = \frac{2}{3}…$` shows as source. Apply the existing `renderMathText`; `katex` is already a dependency and `normalizeBareMath` also repairs older undelimited notes. **⚠️ This is a RENDERING gap, NOT the corrupted-escape item in `v0.86.0-note-item-limit-mismatch.md`** — do not close that with this. **(2) MAKE DRAGGING RESPONSIVE AND STOP IT RACING ITS OWN SAVE** — add an `activationConstraint` to BOTH `PointerSensor` and `TouchSensor`, disable dragging visibly while a save is in flight, and stop refetching `listNotes()` on the reorder path. **⚠️ MITIGATION ONLY** — the owner chose a deferred *Save order* model (2026-08-29) for `v0.96.0`; only work that survives that rework is in scope here. **⚠️ DO NOT TOUCH `components/ui/summary-markdown.tsx`** — Summary math needs `remark-math` tokenization (`_` is markdown emphasis, so `$x_1 + x_2$` is mangled first), and **`app/onboarding/page.tsx` renders `SummaryMarkdown`**, putting it on the signup path `[CHECKPOINT — due 2026-09-11]` measures against a 62.4% baseline. **⚠️ Do NOT build the deferred save model here**, do not change what `setCollectionItemOrder` means, keep `UNGROUPED_SECTION_NAME` excluded from section drags, do NOT remove the 500ms debounced combobox writer at `study-plan-builder-page-client.tsx:443` or its comment, and do not wholesale-refactor that 2282-line file. **⚠️ No backend change, no migration, no analytics event.** Previous release scope: **`v0.95.0` — Redemption Integrity.** Four items. **(1) DEFER THE BIRTH-YEAR WRITE UNTIL THE RELATIONSHIP IS CONFIRMED** — **owner ruling 2026-08-29.** A link redemption must stop writing `users.birth_year`; the redeemer's declared year is held **provisionally** and promoted to the account-global column only when the creator confirms. **⚠️ It exists because a single UNCONFIRMED redemption is irreversible in two ways** — it writes that write-once year permanently, and it hands the creator the redeemer's display name **and whether they are a minor**, while the creator need never accept. **⚠️ THE NAIVE FIX IS KNOWN TO BE WRONG — audited at kickoff, do NOT re-derive it:** `recordGuardianConsent` (`LinkedLearnerService:427-430`) throws `LinkedLearnerBirthYearRequiredException` on a null birth year **and requires `PENDING`**, so simply removing the `captureLearnerBirthYearIfMissing` call at `LinkedLearnerInvitationLinkService:122` leaves a link-redeemed minor `PENDING` with no year and **their supporter can NEVER record guardian consent — the path becomes unreachable for exactly the population it protects.** `accept()` (`:364-373`), `toResponse`'s `consentRequired` / `learnerAgeUnknown` (`:612-613`, `:637`, `:661`) and the separate `recordBirthYear` writer (`:397`) all read the same field. **⚠️ THE STORAGE SHAPE IS DECIDED AT KICKOFF, NOT BY DELIVERY, and the obvious answer is UNBUILDABLE — recorded so it is not re-proposed:** `redeem()` sets `redeemed_at`/`redeemed_by_user_id`, so the link row is **terminal** the moment it is used, and **`linked_learner_relationships` carries NO link id**, so `recordGuardianConsent(relationshipId, …)` and `accept()` have **no path back to the link**; reconstructing one from `(redeemed_by_user_id, creator_user_id)` is not unique, since revoke + re-invite mints a new relationship for the same pair. **⚠️ SO: a NEW SIDE TABLE KEYED BY RELATIONSHIP ID** — provisional year plus declared-at, one row per pending relationship, **deleted on promotion and on revoke**, which also honours `v0.89.1`'s rule that declared-value history is minor's personal data and is not retained. **⚠️ Do NOT add a column to `linked_learner_relationships`, `_invitations` or `_guardian_consents`**, whose meaning `[CHECKPOINT — due 2026-09-19]`, `[CHECKPOINT — due 2026-09-26]` and `[CHECKPOINT — due 2026-10-13]` read. **⚠️ AMENDED AT THE `v0.97.0` KICKOFF (owner, 2026-08-29), narrowly and with the reason recorded — raised as a letter-versus-reason conflict rather than reasoned past, per the `v0.96.0` precedent: `v0.97.0` item 1 adds `expires_at` and an `EXPIRED` status to `linked_learner_relationships`. The prohibition exists to keep a row's **MEANING** stable for three dated reads; item 1 changes **LIFECYCLE, not meaning** — `PENDING`/`ACCEPTED`/`REVOKED` keep their definitions and `EXPIRED` names a timed-out `PENDING` rather than reinterpreting any existing value — and both affected reads carry **re-specified queries recorded before they run** (`2026-09-26`, `2026-09-19`), while `2026-10-13` reads the **carrier** clock, untouched. **⚠️ OTHERWISE THE PROHIBITION STANDS IN FULL:** nothing may be added to `_invitations` or `_guardian_consents`, and no further column to `linked_learner_relationships`, without the same explicit amendment.** **⚠️ AND THE DEFERRAL IS FROM `users.birth_year` ONLY — NOT from the consent machinery, which would DEADLOCK:** `accept()` (`:364-370`) throws `LinkedLearnerBirthYearRequiredException` when the learner's year is null and **the caller is not the learner**, so on a supporter-created link redeemed by a learner, deferring the year out of reach leaves **the supporter permanently unable to confirm the relationship only they can confirm.** The provisional year feeds `consentRequired`, `learnerAgeUnknown` and `accept()` exactly as a persisted year does today; what changes is that **nothing reaches the account-global write-once column until the creator confirms**, so an unconfirmed redemption leaves no trace on the redeemer's account. **⚠️ Guardian consent working end to end for a link-redeemed minor while `PENDING` is the ACCEPTANCE TEST for this item, not a side condition.** **⚠️ `users.birth_year` stays account-global and WRITE-ONCE** — this changes *when* it is written, never that it is written once, and `v0.89.1`'s correction path is untouched. **⚠️ Birth year is still collected from the LEARNER, never from the inviter** — this file states it and a test pins it. **⚠️ The email-keyed path is NOT changed.** **(2) THE LINK SURFACES STOP REPORTING STATE THEY CANNOT SEE** — the live-links list never refetches, so **Copy and Revoke can act on an already-dead link** and Copy hands out a token the server will reject; reloading after a successful redemption reads as a dead link; the paused banner claims sharing will *"resume"* on never-accepted `PENDING` rows that link redemption newly creates, where it never started. **⚠️ The frontend genuinely CANNOT distinguish "granted, now paused" from "never granted"** — the DTO zeroes `*SharedWithMe` on a non-`ACCEPTED` row — so copy must describe the **status** and must not guess at access, the same rule `v0.94.0` applied to `linked-learner-status.ts` and `SharingPanel`. **(3) THE CONNECTION ACCESSIBILITY SET** — `birth-year-input` steppers are nested inside `<label>` in three of four call sites; a raw checkbox is used where the shared `Checkbox` component is used elsewhere **on the same page**; disabled toggles are left in the tab order. **(4) THE "QUIZ FOR SOMEONE" MODAL DISCLOSES THAT QUIZZES ARE AI-GENERATED** — **⚠️ copy only: no quota, limit, counter or metering change**, and the Challenge Quiz **mode name** stays a different string from the quota **label**, which a regression test pins. **⚠️ DO NOT TOUCH `app/onboarding/page.tsx` or the signup → verify-email → onboarding path** — `[CHECKPOINT — due 2026-09-11]`'s 62.4% completion baseline cannot be re-run. **⚠️ Standing rules unchanged:** acceptance stays LOAD-BEARING and redemption must become *less* consequential, never more; guardian consent is not bypassable by link and stays re-asserted inside the grant check, and `v0.94.0`'s fail-closed unknown-age behaviour stays fail-closed; **no account-existence oracle** and the single not-found contract for unknown, revoked, expired and redeemed tokens is preserved; absence of a live grant means NO ACCESS; the progress read stays UNIDIRECTIONAL with its explicit caller-is-supporter assertion; `PROGRESS` stays learner-issued only while activity stays mutual; every read re-verifies `ACCEPTED`, no cache, no grace; invitations stay ONE-AT-A-TIME and the quiz share link stays the many-recipient mechanism; no relationship-type column, no new profile type, nothing gated on `ProfileType`; `NoteVisibility` stays `PRIVATE | PUBLIC`; no endpoint accepts a learner user id; no public people search. **⚠️ Any predicate deciding whether a provisional year is promoted, read or discarded needs a REAL-ROW test** in `NativeQueryPostgresIntegrationTest`, mutation-verified with the killing test named — **a mocked repository cannot test a predicate**, and that class has recurred three times, twice inside the releases that recorded it. Plan: `docs/claude-plans/learning-connections-phase-plan.md`.

**⚠️ `v0.93.0` — Progress Refinement (Phase 3 of Learning Connections).** Uses the `PROGRESS` scope `V125` already ships: `PUT /linked-learners/{relationshipId}/grants/progress` with `LinkedLearnerGrantService` scope-parameterized behind both grant paths (**`PUT /grants/activity` is a live contract and keeps working**), `LinkedLearnerResponse` gaining `progressSharedByMe` / `progressSharedWithMe`, `LinkedLearnerReadAuthorizationService.requireAcceptedLearnerId` reimplemented over `requireGrant(caller, relationshipId, PROGRESS)`, the per-scope permission UI, and three `CONNECTION_PROGRESS_*` funnel events (enum first). **⚠️ DECIDED AT KICKOFF: the progress toggle renders on the LEARNER side only and the endpoint enforces it** — the read is unidirectional, so a `PROGRESS` grant written `supporter → learner` is a row nothing can ever consume, rendering a toggle that shares nothing and making the learner's DTO advertise a link that 404s. **Granting `PROGRESS` requires the caller to be the relationship's learner**, which makes `progressSharedWithMe` true only for a supporter, so ***View progress* gates on `progressSharedWithMe` alone** with no residual `callerRole` clause. *Activity is mutual; progress runs learner → supporter.* **⚠️ It exists because `LinkedLearnerProgressService.getProgress` gates on `requireAcceptedLearnerId` alone — no grant check — and returns `getStudyEngagement(learnerUserId)` verbatim, the identical four fields Phase 2 put behind a grant plus a superset**, so the Phase 2 activity toggle does not bound what a supporter sees. **⚠️ THE READ STAYS UNIDIRECTIONAL — this is the trap.** `requireGrant` returns the *other party*, which is the **supporter** when the caller is a learner, so a thin passthrough would silently let a learner read their supporter's mastery with `resolveDisplayName(learner)` mislabelling the payload. §4's per-direction table describes the grant **row**, not the endpoint. **Keep the caller-is-supporter assertion explicit in the helper**; the pinning test is that a learner calling `/progress` gets 404, grant or no grant. **⚠️ THE BREAKING SEMANTIC: an `ACCEPTED` relationship no longer implies progress access** — find and correct every surface, doc and test assuming acceptance is sufficient. **⚠️ NO BACKFILL granting `PROGRESS` to existing `ACCEPTED` relationships** — it would be free (production was empty 2026-08-26) and is forbidden anyway, because it converts an implicit rule into explicit consent nobody gave. **⚠️ NO MIGRATION AT ALL and no new scope value.** **⚠️ PROGRESS CONTENT IS UNCHANGED** — mastery, readiness, quiz performance and collection progress stay exactly as `v0.89.0` shipped them; this permissions an existing payload and adds no field. **⚠️ Preserve the error contract**: `requireGrant` throws `LINKED_LEARNER_NOT_FOUND` while the progress path throws `LINKED_LEARNER_PROGRESS_NOT_FOUND` — both 404, different codes. **⚠️ Keep `getProgress`'s outer `requireEmailVerified` AND its comment** — that call carries a documented defence-in-depth rationale, not duplication. **⚠️ Guardian consent stays re-asserted inside the grant check** and is asymmetric by design: it gates the learner's data, and a consent lapse must cut progress as well as activity. **⚠️ Re-verify `ACCEPTED` on every read** — a relationship revoke, a grant revoke and a `v0.89.1` birth-year correction each cut access immediately. **⚠️ Absence of a live grant means NO ACCESS.** **⚠️ Sharing is DIRECTIONAL and never reciprocal**: activity ON does not imply progress ON, and a grant never implies its mirror; **activity is mutual while progress is learner → supporter only.** **⚠️ `toResponse`'s grant fields must filter on relationship status** — the reachable stale case is `PENDING`, not terminal `REVOKED`, because a birth-year correction pauses the relationship while the live grant row survives by design; **⚠️ and the filter has a DIRECTION — adding it blindly inverts the lie.** The row surviving a pause is load-bearing and documented (withdrawal must never require a status the learner does not control), so filtering both fields would make a learner's own toggle read OFF while the row is live. **`*SharedByMe` reflects the ROW; `*SharedWithMe` reflects ACTUAL ACCESS and filters on `ACCEPTED`.** A paused relationship renders as paused, not as off. **⚠️ Close the grant-write race with the CONDITIONAL-WRITE idiom, not a lock:** `accept()` locks the *learner user row*, not the relationship, and guards its transition with `markAcceptedIfPending` — **no relationship-row lock exists in this codebase**, and the rule is *"status transitions are conditional updates, never read-modify-save."* Make the live-grant insert conditional on the relationship still being `ACCEPTED`; withdrawal stays unconditional on status. **⚠️ THE PRIVACY LINE IS UNCHANGED AND ABSOLUTE: a supporter sees readiness, progress and quiz performance, NEVER the learner's notes.** **⚠️ No relationship-type column; no new profile type; nothing gated on `ProfileType`; `NoteVisibility` stays `PRIVATE | PUBLIC`; no endpoint accepts a learner user id.** **⚠️ OUT OF SCOPE, each needing its own decision (Phase 5): mastery comparison between people, scores, leaderboards, activity rings, social feed, reactions, public people search.** Phase 4 is the next release. Plan: `docs/claude-plans/learning-connections-phase-plan.md`.

**⚠️ `v0.92.0` — Activity Sharing (Phase 2 of Learning Connections), plus quota legibility.** Adds the permission substrate: a `linked_learner_grants` table (`relationship_id, from_user_id, to_user_id, scope, granted_at, revoked_at`), `scope IN ('ACTIVITY','PROGRESS')`, `CHECK (from_user_id <> to_user_id)`, live-row unique index on `(relationship_id, from_user_id, scope)`; a `LinkedLearnerGrantAuthorizationService.requireGrant(caller, relationshipId, scope)` check; directional opt-in UI; and a momentum view. **⚠️ Ship the table with BOTH scopes and use only `ACTIVITY`** — Phase 3 uses `PROGRESS`, and this is the plan's only cross-phase coupling, so it is one migration, not two. **⚠️ Absence of a live grant means NO ACCESS** — accepting a connection grants nothing, it creates the capacity to grant. **⚠️ Sharing is DIRECTIONAL and never reciprocal by default**: A→B activity ON with B→A OFF must be representable and render correctly on both sides. **⚠️ NO NEW MEASUREMENT** — `ActivityType.MEANINGFUL_STUDY_ACTIVITIES` already defines what *studied* means and deliberately excludes `OPENED_STUDY_PACK`; `UserActivityEventEntity`, streaks and `countStudyDaysThisWeek` already exist, so this is a **permissioned projection of data already written**. Do not add an activity type. **⚠️ Do NOT touch `LinkedLearnerReadAuthorizationService.requireAcceptedLearnerId`** — it hardcodes caller-is-supporter and Phase 3 reimplements it over `requireGrant(..., PROGRESS)`; rewriting it here changes who can read progress in a release that is not about progress. **⚠️ Guardian consent must be re-asserted INSIDE the grant check**, or `v0.89.1`'s gate quietly reopens — a consent lapse must cut activity as well as progress. **⚠️ Re-verify `ACCEPTED` on every read** — no cache, no grace; a revoke and a birth-year correction both cut access at once. **⚠️ No relationship-type column** (`GUARDIAN | TUTOR | PARTNER`) — permissions define the relationship, and a type column invites gating on it. **⚠️ No new profile type; nothing gated on `ProfileType`.** **⚠️ Do not change what `linked_learner_relationships`, `_invitations` or `_guardian_consents` mean.** **⚠️ OUT OF SCOPE, each needing its own decision (Phase 5): mastery, scores, leaderboards, comparison between people, activity rings, social feed, reactions, public people search.** **Quota legibility — DISCLOSURE ONLY.** `GeneratedQuizService.assertQuizCreditAvailable` spends `user_usage.challenge_quiz_generations` for a quiz made **for someone else**; the user-facing meter is **“AI quizzes”**, described as *“Challenge Quiz sessions and quizzes you make for someone.”* `QuizShareLimitService.assertShareLinkQuotaNotExceeded` has **exactly one call site — link creation** — so the cheaper limit is enforced last. **⚠️ No limit, counter or metering change, and NO second counter.** **⚠️ Do NOT move the share-link check into the generation path** — generating without sharing is legitimate; surface the cap earlier, do not apply it earlier. **⚠️ The shared quota label `AI quizzes` and the product-mode name `Challenge Quiz` are deliberately different strings** — all usage-meter and pricing surfaces import the quota label from one definition, while mode surfaces keep `Challenge Quiz`; a global replacement would destroy the distinction. `MePlanResponse` carries the existing share-link limit, used count and remaining count for disclosure. Plan: `docs/claude-plans/learning-connections-phase-plan.md`.

**⚠️ `v0.91.0` — Shared Learning Material (Phase 1 of Learning Connections).** Introduces controlled note sharing with accepted connections, because **sharing a note today means making it PUBLIC** — the Share action on a private note tells the owner to publish it first. **⚠️ `NoteVisibility` STAYS `PRIVATE | PUBLIC`; do NOT add a `SHARED` value.** Every note and Study Pack read is `findByIdAndOwnerUserId`, so an enum value grants nobody anything — access comes from the new `note_shares` grant table. `AccountPurgeService.deletePrivateArtifacts` retains `PUBLIC` and deletes `PRIVATE`, so a `SHARED` note would match neither branch and **survive the purge of a deleted account while staying readable by its recipients**. The enum has 42 usages across 24 files. A shared note stays `PRIVATE` and is excluded from every public query, all of which match `= 'PUBLIC'` positively. **⚠️ The three-option visibility control is DERIVED, never stored.** **⚠️ Selecting *Private* revokes every live share** (owner decision 2026-08-27); *Public* revokes none. **⚠️ Connecting shares NOTHING, and nothing is reciprocal by default.** **⚠️ Never expose the owner's mastery, weak concepts, attempts, scores or practice history with shared material** — assert the recipient DTO's shape in tests. **⚠️ The recipient Study Pack read must not call `recordActivity` with the owner's id.** **⚠️ Re-verify `ACCEPTED` on every read** — no cache, no grace; a revoke and a birth-year correction both cut access at once. **⚠️ No endpoint accepts a learner user id.** **⚠️ No new profile type; nothing gated on `ProfileType`.** **⚠️ Shared notes are not mixed into the owned Library and cannot join a Study Plan without being copied.** **⚠️ Do not move "Quiz for someone" back beside Quick Review**, and do not remove or merge either lightweight sharing path. Plan: `docs/claude-plans/learning-connections-phase-plan.md`.

**⚠️ `v0.90.0` — Invitation Integrity.** Moves *"Quiz for someone"* out of the Study Pack practice row into the note-actions menu — it is a support/share action, and beside *Start Quick Review* it risks becoming an avoidance path. **⚠️ It stays available to EVERYONE, gated on nothing: a shared-quiz recipient needs no account and no relationship**, and a proposal coupling sharing to a connection was overturned 2026-08-20. Also ships **email-keyed invitations** — store the invite against the typed address, not a resolved user id — which closes the account-existence oracle and lets someone invite a person who has not signed up. **⚠️ Invitations stay ONE-AT-A-TIME by principle; the quiz LINK is the many-recipient mechanism.** **⚠️ Learning Connections stays a CAPABILITY — no profile mode, no opt-in toggle, nothing on `ProfileType`.** **⚠️ Do not change what `linked_learner_relationships` means** — `[CHECKPOINT — due 2026-09-19]` reads it.

**⚠️ `v0.89.1` — Birth Year Correction. `users.birth_year` is account-global and WRITE-ONCE**, so a learner who declares an adult year permanently disables guardian consent for **every future supporter link**. **⚠️ The load-bearing half is re-evaluating EXISTING links on a downward correction** — an `ACCEPTED` link without consent on a learner who turns out to be a minor is the state the gate exists to prevent. **⚠️ Revert to `PENDING`, never `REVOKED`.** **⚠️ Do NOT move birth year into signup, onboarding or profile editing** — collected at link time only, and a `v0.89.0` test asserts signup and profile leave it null. **⚠️ Only the learner may correct their own year.** **⚠️ No history of declared values** — minor's personal data. No change to the authorization model, the privacy line, or what `linked_learner_relationships` means.

**⚠️ `v0.89.0` — Support Another Learner (Phase 1). Rescoped from *Regeneration Integrity* on kickoff day, because that release's item 1 was a live-probe gate needing owner time that was not available — the regeneration hypothesis was NOT tested and NOT killed, and must not be recorded as cleared.** **Phase 1 is already built; one gate on the wrong axis hides it.** `QuizShareLinkService.requireTeacherOrAdmin:160-166` restricts share-link creation to `TEACHER`/`ADMIN`, while generating a quiz for someone else is ungated and `/quiz/share/**` is `permitAll`, so the recipient needs no account. **⚠️ This is an axis error: `ProfileType` answers "how do YOU learn?", not "may you help someone?"** — a parent helping a child is a `STUDENT`/`BOARD_EXAM` profile, so the only workaround is to misrepresent their own profile, which changes dashboard emphasis, quiz-mode visibility and generation behaviour. **⚠️ Phase 1 records NO relationship** — no linking, invitation, acceptance, permissions model or cross-user read; those are Phases 2–3, and Phase 3 is the product's first cross-user authorization. **⚠️ Do NOT introduce a supporter `ProfileType` or gate anything new on `ProfileType`** — `PARENT` exists unimplemented with zero users; leave it. **⚠️ Consent, minors and DPA do not apply to Phase 1** (nothing about the recipient is stored) and must not be built for it. **Quota is already answered: the supporter's own account, own quota, own share limit — no shared or transferred quota.** **Keep DOCX export and multi-version `TEACHER`-gated**; only the share link opens. **⚠️ Do NOT justify this on retention** — the supporter's return loop is the Phase 3 progress view, and Phase 1's value accrues to the learner receiving the material. **No demand-signal capture in onboarding before 2026-09-11.** Also carries the `generation_status_at` sweep-stamp fix, which makes the bounds half of `[CHECKPOINT — due 2026-09-01]` answerable. **⚠️ WIDENED 2026-08-19 TO PHASES 2 AND 3 at owner request** — Phase 2 alone would have ended the release with still no progress view, repeating the gap Phase 1 left. **⚠️ Phase 3 is the product's FIRST cross-user read; every read path today is owner-scoped, so this is an authorization model, not charts.** **⚠️ Linking is invite + accept in BOTH directions, revocable either side — acceptance is load-bearing**, since without it anyone could claim a relationship by knowing an email address. **⚠️ Age is collected AT LINK TIME, never at signup** (onboarding is under measurement until `[CHECKPOINT — due 2026-09-11]`), with guardian consent below a threshold that is **NOT decided** — the number is a legal question pending counsel, so build it as **configuration, not a literal**, and if a default must ship make it the most protective value. **⚠️ THE PRIVACY LINE IS ABSOLUTE: a supporter sees readiness, progress and quiz performance, NEVER the learner's notes** — this protects the core loop, because learners who suspect notes are visible write less honestly. **Phase 3 exposes counts and states only: never concept names, subjects, note/Study Pack/collection titles or other learner-derived free text.** **⚠️ Every cross-user read must verify an `ACCEPTED` relationship, not merely that a row exists; revocation cuts the read immediately.** **⚠️ Viewing must never write `ConceptHealth`** — it has moved only from genuine assessment since `v0.37.0`, and a view that touches it would corrupt the learner's signal with someone else's activity. **No sub-accounts, no shared quota or subscription, no supporter `ProfileType`, no signup/onboarding fields.**

**⚠️ `v0.88.0` — Section Authoring. Sections ALREADY SHIP — do not build them.** They are derived client-side from the existing per-item `note_collection_items.label`; the learner and Builder surfaces both render them today. **This release is a create control, a guard, and a section-aware inflow — NOT a new model.** **⚠️ No migration, no `note_collection_sections` table, no new entity, no third collection level.** Note Collections stay exactly two levels (Goal → Subject Plan); a section is not a `NoteCollection`. **⚠️ `Ungrouped` is a synthetic sentinel, not data** — its Builder header must be non-editable and the name must be rejected when typed, because `handleRenameLeafSection` matching `oldName = "Ungrouped"` relabels every unlabeled item in one `PUT` (this is the incident that produced the degenerate `Algebra · 77` state). **⚠️ The ported section combobox and the rename-merge confirmation are each correct alone and WRONG TOGETHER:** the combobox carries local state plus a debounced auto-save and `LeafSortableNoteCard` is keyed by note id, which survives `refreshBuilder`, so after a rename every card writes back its stale value and silently reverts the rename one note at a time. **Key the card `${noteId}:${item.label ?? ""}`.** **⚠️ Case-variants must snap in the combobox, never by case-folding `buildLeafSections`.** **⚠️ Bulk generation's section is an EDITABLE field pre-filled from the batch subject — never a hidden coupling.** **⚠️ The unsectioned bucket is named "Not in a section" and must NOT be renamed to "Other notes"** — the display string doubles as the reserved sentinel, so a second string needs a two-string guard. **⚠️ Do NOT re-add "section destination in Add Notes"** (cut on evidence: `handleAddLeafNotes` makes no `setOrder` call). No new mastery signal — `ConceptHealth` stays the readiness source and section readiness is client-side summation. No runtime LLM for Sections, no automatic taxonomy inference. **Never surface the word "label" in UI copy.**

**⚠️ `v0.87.0` — Failure Attribution.** **When a bulk-generated topic fails, the curator is told only WHICH topic — never why.** `BulkGenerationResultEntity.failedTopics` is a `List<String>` of names; `NoteBulkGenerationService.processBatch` logs the exception server-side and discards it from the receipt. `buildGeneratedNoteContent` alone raises **six distinct** rejections (title, overview, key idea, core details, why-it-matters, quick recall), plus the whole-note bound and the filler guard. **That gap has now cost two investigations** — `v0.86.0` fixed `invalid core details` and the owner's other-subject failures persisted; `v0.87.0` opened against three unpublished word bounds and **a live 14-topic probe across all eight domains came back 14/14 clean, nothing within 15% of a ceiling**, killing that hypothesis too. **⚠️ This release makes failures LEGIBLE and changes no validation** — no bound is altered, published, raised or removed, because changing both at once would destroy the read it exists to produce. **⚠️ `failed_topics` keeps its shape** (the retry path reads it, and every existing receipt is a plain string list); the reason goes in a parallel nullable column. **⚠️ Never surface raw exception text** for non-`AppException` failures.

**⚠️ `v0.86.0` recovers rows stranded in a non-terminal generation status with an age-threshold sweeper, NOT a shutdown drain.** The production damage was ****37** stuck `exam_question_pool` rows (18 `GENERATING` + 19 `PENDING`, corrected 2026-08-18) and 1 Long Exam session**, not notes — the note half is **prospective** (zero stuck notes at sizing) and must not be described as a backlog. Copying `analyticsTaskExecutor`'s `waitForTasksToCompleteOnShutdown(true)` onto the generation executors is a regression: it runs the entire queue uninterrupted, billing up to 100 LLM calls against a closing datasource. The sweeper marks `FAILED` and never re-dispatches — Study Packs never auto-regenerate.

**⚠️ `v0.83.0` removes Target Audience from authoring and Public Library discovery but does NOT drop `notes.target_profile_type`.** The column is `V117`'s input and `[CHECKPOINT — due 2026-09-16]` cannot run without it; the drop waits on that report. No migration ships in `v0.83.0`. Target Audience must never become a runtime depth fallback.

**⚠️ In a NATIVE query, never test a named parameter for null without casting it.** PostgreSQL types native-query parameters itself, and a bare `:param is null` gives it nothing to infer from — it fails the entire statement at **parse** time with `could not determine data type of parameter $n`, so the endpoint 500s on every call, not just a paged one. Write `cast(:param as timestamptz) is null`. **JPQL is unaffected** (Hibernate types those parameters), which is why the same shape is safe in JPQL repositories. **⚠️ H2 accepts the uncast form, so the test suite cannot catch this** — it shipped undetected in `v0.91.0` and 500'd the *Shared with you* Library section against real PostgreSQL until a user reported it. `NativeQueryParameterTypingTest` remains the Docker-free source tripwire; do not weaken it. `NativeQueryPostgresIntegrationTest` automatically reflects over every repository `@Query(nativeQuery = true)`, `PREPARE`s it on PostgreSQL 16, runs the full Flyway migration set and executes both PostgreSQL-only Library implementations. New annotated native queries require no inventory edit. The normal backend `./mvnw clean install` therefore requires Docker; the only opt-out is the explicit `-Dnativequery.pg.skip=true`, which must be treated as leaving PostgreSQL SQL and migrations unverified. Keep the shared H2 test `application.yaml` unchanged.

When working on a feature, always check the corresponding document under `docs/features/`.

**⚠️ `v0.95.0` effective birth-year path:** Every relationship-scoped consent decision must use the single effective-birth-year resolver only after `lockAndReadBirthYear`. `users.birth_year` wins when non-null; otherwise only the provisional row keyed to that exact relationship id may supply the value. Do not read provisional years directly from another service path.

Active release guardrail:

- v0.29.1 consciously allows one narrow relaxation of the v0.29.0 no batch/progress infrastructure rule: a single terminal-outcome `bulk_generation_result` receipt for bulk generation, written once at batch completion, read once by the owner, then deleted or expired after 24h. This receipt may carry requested/created counts, generation-failed topic strings, quota-blocked topic strings, and retry context. It is not a batch-job entity, live progress table, per-item status row, or new status enum; the broader no batch/progress infrastructure rule still applies everywhere else.
- Bulk generation is available to authenticated, onboarded users in v0.29.1. Non-admin users must stay on the existing quota-enforcing path; ADMIN bypasses bulk note-generation and Study Pack quota inside the bulk orchestration only.

## Product Summary

NoteLib converts notes into structured study outputs and review workflows.

Core loop:

`Capture -> Generate -> Review -> Improve -> Make a Copy -> Repeat`

Teacher flow rule:

- Do not reuse student quiz session logic for teacher preview.
- Teacher flow uses `generatedQuiz` only.

## AI Skills System

Reusable workflow patterns for AI-assisted development are documented in `docs/skills/`.

- `docs/skills/README.md` — philosophy, Claude vs Codex guidance, model/effort recommendations
- `docs/skills/codex-prompt-generator.md` — how to write a structured Codex implementation prompt
- `docs/skills/ux-product-review.md` — NoteLib UX philosophy and review categories
- `docs/skills/release-doc-alignment.md` — checklist for keeping docs aligned after feature work
- `docs/skills/roadmap-feature-audit.md` — how to classify and scope new work before starting

Use these skills before writing prompts, before starting new features, and after shipping work.

## Implementation Workflow Rules

- **⚠️ PRODUCTION DATABASE IS READ-ONLY FOR CLAUDE, ALWAYS. Only the OWNER executes writes.** The rule,
  its definition of read-only, and the hand-it-over protocol live in **`CLAUDE.md`** — read it there
  rather than restating it here, because two wordings of one rule is how a rule degrades. It binds every
  route to production data, the Render MCP server included.

- After every completed prompt/task that results in code or doc changes, always include a suggested commit message in the final response.
- Format the suggested commit message as a copy-friendly plain-text block:
  - first line: `type: concise subject`
  - following lines: flat `- ` bullets with 3-5 high-signal changes when useful
- Example:
  - `polish: refine Library filters with subject-first UX`
  - `- replace wrapped tag chips with a compact popular-tags rail`
  - `- keep subjects in a single horizontal scroll lane above the note grid`
  - `- add + More tag selection via shared mobile sheet / desktop modal`
  - `- preserve real-time title-and-tag search plus existing note navigation`
  - `- update Library docs and release notes for progressive tag disclosure`

### Migration Execution Rule

**Never run a migration file by hand against a database you did not create for that purpose.** Verifying migration SQL against a real PostgreSQL instance is legitimate and encouraged — it has caught real defects — but it belongs in a throwaway database, and the throwaway must be unmistakable **at the point of every command**, not merely at creation.

**Why this is structural rather than a matter of care.** Applying a migration by hand produces a schema Flyway has no record of: the objects all exist, `flyway_schema_history` has no row for that version and no failed row either, and the next real startup dies with `relation "…" already exists`. The state is indistinguishable from a corrupted history, and the only clean repair is to drop what was created and let Flyway apply it properly.

**Reproduced 2026-08-26 (`v0.90.0`).** `linked_learner_invitations` existed complete — eight columns, three indexes, three CHECK constraints, the FK and PK — with no `V122` history row, and the backend would not start. Every probe command in that session named a scratch database explicitly and the cause was still not attributable afterwards, which is the point: **a convention that depends on reading each command correctly is not a guard.** The repair was a `DROP` (the table was empty and unreferenced, verified before acting) rather than hand-inserting a history row, because a fabricated history entry is only safe if the schema happens to match and is invisible when it does not.

- Confirm the target database in the same command that does the work, and prefer a name that cannot be mistaken for a real one.
- Drop the scratch database when finished.
- **Never** hand-apply a migration to "unblock" a failing startup — that recreates the same mismatch one version further along.
- Production applies migrations solely through the application, so it is not exposed to this; the risk is entirely to local and shared development databases.

## Backend Code Quality Rules

- Avoid hardcoding domain-significant string values in implementation code.
- When a string value is used for codes, messages, metadata keys, session keys, analytics names, action labels, query params, or other logic-bearing behavior, promote it to a constant where it belongs.
- Prefer `private static final` constants inside the owning class when the value is local to that class.
- If the same value is shared across multiple classes, move it to an appropriate shared constants/helper type instead of duplicating the literal.
- Reuse existing constants before introducing new ones.
- If helper logic is generic enough to be reused across methods/classes, move it into an existing utility class or create a new utility class in the appropriate package.
- Reuse existing utility classes before creating new ones, and do not create duplicate utility types with overlapping responsibilities.
- Reuse existing exception classes before creating new ones.
- When throwing application-level exceptions, prefer a dedicated exception type that extends `AppException` instead of scattering inline `new AppException(...)` calls.
- If no suitable exception type exists yet, create a new exception class that extends `AppException` and keep its code/status/message ownership there.
- New exception classes should stay close to the domain they represent and should not duplicate an existing `AppException` subclass with the same meaning.

### Sonar / Code Smell Rules (Backend)

- **`assertThatThrownBy` — one invocation only (S5778)**: the lambda passed to `assertThatThrownBy` must contain exactly one method invocation — the call expected to throw. Move all setup and preceding calls outside the lambda. Wrong: `assertThatThrownBy(() -> { setup(); service.call(); })`. Right: `setup(); assertThatThrownBy(() -> service.call())`. Apply this fix whenever modifying a test class that contains this violation.
- **Custom exceptions over raw `AppException`**: throw a named exception subclass (`NoteNotFoundException`, `StudyPackNotFoundException`, etc.) rather than `new AppException(ErrorCode.SOMETHING, "message")` inline. Named exceptions own their code, status, and message — no repeated string literals at throw sites. If no matching subclass exists, create one before throwing.
- **String literal duplication**: a string literal that appears two or more times in the same class must be extracted to a `private static final String` constant in that class. If the same literal appears in multiple classes, move it to an appropriate shared constants class. Apply this fix whenever modifying a class that already has the duplication — do not leave the violation in place.
- **Use `Math.clamp` for range-clamping**: the project targets Java 21. Prefer `Math.clamp(value, min, max)` over `Math.max(min, Math.min(value, max))` — the clamp form is cleaner and Sonar S6877 flags the nested min/max pattern.

## Required Product Architecture (Current)

- Note is the primary entity.
- Study Pack is generated content attached to a Note.
- A Note has state:
  - `DRAFT`
  - `GENERATING`
  - `FAILED`
  - `STUDY_PACK_READY`
- A Note also has visibility:
  - `PRIVATE`
  - `PUBLIC`

### Versioning Rule

- Never auto-regenerate generated content.
- Regeneration is allowed only as an explicit user-confirmed action on an owned note.
- Regeneration updates the existing Study Pack in-place so quiz/session history stays linked to the same Study Pack id.
- Copy includes: `title`, `courseProgram`, `subject`, `tags`, `content`.
- Owner self-copy does not include: generated `summary`, `key concepts`, `quiz`, session history, or performance history.
- Public-note copy is the documented exception: when the public source has a Study Pack, the copy includes the linked Study Pack and arrives as `STUDY_PACK_READY`.
- Public-note copies without a linked Study Pack and owner self-copies remain new `DRAFT` notes.

### Paid Plan Cancellation Rule

- Paid-plan cancellation must be confirmed in Settings before submission.
- Cancellation is scheduled at the end of the current billing period, not immediate.
- Paid access remains active until that period ends.
- Downgrade to Free happens through subscription lifecycle logic at period end.
- Canceling a paid plan must not remove notes or generated Study Packs from the user library.
- Settings billing should show scheduled end-of-period cancellation clearly in the subscription summary and must not imply immediate loss of access.

### Paid Upgrade Prompt Rule

- Free users should see a soft paywall modal before any paid-plan quiz feature or Study Pack limit block attempts a paid conversion flow.
- All paywalls must be context-aware. Never use generic upgrade prompts when the blocked action is known.
- Premium exam paywalls for Long Exam, Board Exam Mode, and Interview Practice must fire from the Start CTA after the user can view the mode setup/prescreen, not from the mode-selection card click.
- Study Plan premium-exam launch must route with `collectionId`, resolve profile-to-mode through `resolvePlanPremiumExamMode`, and scope additional-note pickers to quiz-ready notes from that plan only.
- Shared paywalls must explain the specific blocked action, the upgrade value, and the strongest next plan path for that action.
- Verified users who choose to upgrade should start the hosted checkout flow via `POST /api/payments/create`.
- Frontend upgrade actions should redirect only to the backend-returned Xendit checkout URL.
- Paywall upgrade attempts must preserve a safe internal return path and resume the interrupted flow after successful payment.
- Note-creation paywalls must save the current note or preserve a local draft before redirecting to checkout.
- When a user has `2` or `1` Study Packs remaining, show a non-blocking monthly-limit banner on Dashboard, Note Detail, and Study Pack generation surfaces.
- When Study Pack remaining reaches `0`, keep `Generate Study Pack` enabled and show a student-friendly monthly-limit modal on click instead of disabling the action.
- Upgrade messaging should position Plus as the practical step-up for consistent, guided study and Pro as the complete learning system for serious, sustained study (re-messaged in v0.68.0 — see `docs/product/PLANS.md` and the Messaging Architecture Backlog Index row; Board Exam Mode remains a Pro feature, but exam prep is no longer the tier's framing).
- Dashboard should show a Free-only upgrade card highlighting Challenge Quiz, Adaptive Practice, Board Exam Mode, and the `100` Study Pack Pro limit.
- Pricing page should clearly compare Free vs Plus vs Pro with localized backend pricing and student-oriented value messaging.

### Study Pack Usage Rule

- Study Pack enforcement, warning banners, and remaining-credit UI must use the same backend-resolved usage calculation.
- Allow Study Pack generation only when `used < limit`; block when `used >= limit`.
- Study Pack usage increments only after a successful Study Pack is persisted.
- Saving a note, opening generation surfaces, failed generations, and failed retries must not consume Study Pack quota.
- Frontend warning/blocking surfaces should use `GET /api/me/plan` remaining values and must not recalculate quota from local note lists.

### Study Plan Readiness Rule

- Plan readiness is rendered in the canonical `/progress?collectionId={id}` frontend view, still backed by owner-scoped `GET /collections/{id}/readiness`.
- The endpoint must resolve the collection exactly like `NoteCollectionService.get(collectionId, userId)`: missing, malformed, public-source, or not-owned plans return `CollectionNotFoundException` / `404`.
- Plan readiness must reuse `ProgressReportService` ConceptHealth classification and `masteryPercentage`; do not invent thresholds, persist readiness fields, add generated content, or call AI/LLM.
- Quick Review must not write to `ConceptHealth` and must not move mastery, due-state, Note readiness, Plan readiness, or Overall Readiness. It is a refresh-only mechanic; its own retry/missed-concept feedback must come from session metadata, not ConceptHealth writes.
- Collection detail execution rows, collection list cards, published-plan cards, and public source plans must keep the no-mastery rule: no subject mastery percentages, milestones, goals, streaks, or weakest-subject routing there. **Second named exception (formalized v0.66.1, shipped since the original Goal → Subject hierarchy feature):** a Goal's own detail page may show each child Subject plan's `overallReadinessPercentage` and a readiness progress bar on that child's card, plus a `mastered · due · not started` concept-count line (the due segment may use a warning color when `dueConcepts > 0`, presence-based only, no magnitude threshold) — this is the Goal owner reviewing their own curriculum's readiness, not a list/browse surface. Collection list cards, published-plan cards, public source plans, and per-note execution rows are unaffected and keep the plain no-mastery rule.
- Frontend readiness displays should reuse the shared `ReadinessSummary` component and vocabulary: `ready`, `mastered`, `due`, `not started`.
- The `/progress?collectionId={id}` plan-scoped view fires `PLAN_READINESS_VIEWED` once per distinct plan selected in a session (keyed by `collectionId`, not a fire-once boolean — switching plans without a remount must fire again for the newly selected plan).

### Study Plan Hierarchy Rule

- Study Plan nesting is constrained to exactly two collection levels: top-level Goal collections may contain child Subject plans, and Subject plans may contain note items and label-derived sections.
- The only hierarchy storage is nullable `note_collections.parent_collection_id`; deleting a Goal must set child `parent_collection_id` to null rather than cascading child collections.
- Backend hierarchy logic must stay profile-neutral. Goal/Subject wording is frontend-only through `getCollectionLabels`; services and API contracts must not branch on `ProfileType`.
- Set/clear parent must be owner-scoped and enforce: parent exists and is owned by the caller, parent is top-level, child is not self, and child has no children.
- Goal readiness is derived from child readiness counts only: `round(100 × Σ child.masteredConcepts / Σ child.totalConcepts)`, or `0` when total is `0`. Do not re-run concept classification over merged Goal notes, persist readiness, add thresholds, or call AI.
- Deeper nesting, recursive Goal adoption, direct note items on Goals, and per-module readiness remain out of scope unless explicitly introduced by a future release rule.

### Post-Mastery Next-Item Rule

- The mastered Quick Review branch keeps `Take a Challenge` as its primary action and may offer `Next in your plan` as a secondary action.
- The suggestion must reuse `NoteCollectionService.toProgressResponse`'s definition of practiced: `lastSessionCompletedAt != null`, resolved through `QuizSessionHistoryService.findLatestSessionCompletedAtByNoteIds`. Do not introduce a second definition of done.
- **Concretely: do not add a `not exists (… session.noteId = …)` practice filter to the candidate query.** It looks equivalent and is not — `findLatestSessionCompletedAtByNoteIds` also credits **multi-note sessions** (Board/Long Exam) by reading each session's participating note ids, which no per-note session predicate can see. A SQL-side filter is therefore a second, narrower definition; it shipped once in `v0.78.0` review and was removed before commit. The candidate query orders and excludes only; practice state is resolved in one service-side lookup per plan, which also keeps that multi-note scan off a per-page loop.
- Within the resolved directly containing collection, select the lowest-`position` readable item with no completed practice, explicitly excluding the note just completed.
- Prefer `users.primary_collection_id` only when it directly contains the completed note; otherwise use the most recently updated directly containing collection. Do not traverse collection parents or children.
- No containing collection or no remaining candidate yields a null secondary action with silent frontend absence; do not add a placeholder, completion state, persisted recommendation, or analytics event for this branch.

### Note Readiness Signal Rule

- Private Note Detail may show a compact per-note readiness signal for owned notes with a ready Study Pack and key concepts.
- The note signal must reuse the shared `ReadinessSummary` component and the same readiness vocabulary as Plan Readiness and My Progress.
- The note readiness signal is available to Free users: `% ready`, `X/Y mastered`, due count, not-started count, and per-concept readiness status.
- Free users receive the minimum `lastCorrectAt` signal needed to render accurate `Due`, `Mastered`, and `Not started` statuses. Detailed review timing (`daysSinceReview`, `Due - Nd ago` copy), incorrect-answer history, and `Needs work` remain PLUS/PRO only.
- This visibility split must not change prices, quotas, pass durations, checkout behavior, Adaptive Practice access, generated content, AI calls, or persisted readiness fields.
- Concept-health load failures must not hide or wipe note content; show a neutral readiness-unavailable state instead.

### Retained Target Audience Storage Rule

- **Axis boundary (`docs/architecture/ADR-001-canonical-knowledge-architecture.md`):** Target Audience is removed from product requests, responses, authoring, display, and Public Library discovery. `notes.target_profile_type` remains mapped, populated, constrained, indexed, and SQL-readable solely as retained migration evidence pending `[CHECKPOINT — due 2026-09-16]`. It must never influence generated depth or reach a prompt.
- `V117` is the one-time historical exception that derives Authored Depth for NULL-depth, `ADMIN`-owned curator notes only: `BOARD_TAKER → BOARD_EXAM_REVIEW` and `PROFESSIONAL → PROFESSIONAL`; `STUDENT`, learner-owned notes, and already-authored depths remain untouched. This migration evidence must never become a runtime fallback or default write.
- **`BOARD_TAKER` is not self-certifying, and it has now failed in two separate programs.** `V117` excludes a **denylist of program values known not to be licensure programs** — `Information Technology`, plus the academic-level values `Grade School` / `Junior High` / `High School` / `Senior High …` — checked against both `note_course_program` → `course_programs.name` and the free-text `notes.course_program`; a note with several programs is excluded when **any one** is on the denylist. The IT entry came from an audit finding all nine of its `BOARD_TAKER` notes to be ordinary coursework; the academic-level entries came from a public curator note tagged `BOARD_TAKER` whose program is `High School` and whose depth the same curator authored as `JUNIOR_HIGH`. **Treat "this audience tag implies that depth" as an assumption needing evidence per program, not a rule.** Do not use `course_programs.exam_goal_slug` as a licensure test — it identifies Exam Hubs only and omits legitimate board programs including Civil Engineering.
- `NoteEntity.targetProfileType` and `NoteTargetProfileType` must remain. No migration, default, constraint, index, or enum change ships in `v0.83.0`.
- Note creation derives the stored value through `mapOwnerProfileTypeToNoteTarget`: `BOARD_EXAM -> BOARD_TAKER`, `PROFESSIONAL -> PROFESSIONAL`, every other profile (including Teacher/Parent/Student) -> `STUDENT`. Do not accept a client override or hardcode a replacement constant.
- Note update preserves the stored value; it falls back to owner-profile derivation only for a defensive legacy-null row. Do not make create and update symmetric.
- Note copy carries the source note's stored value. Bulk generation derives once from the owner and still persists it on `bulk_generation_result`, but does not expose it in the receipt response.
- Public Library replaces the retired audience facet with an Authored Depth equality filter on `notes.learner_level`. `?level=` must parse through tolerant `LearnerLevel.fromString`; invalid values are ignored, valid values with no matches use the standard empty state, and NULL-depth notes are excluded. Populate chips only from distinct non-null depths present on public notes, never from the full enum.

### Async Study Pack Generation Rule

- Note-owned Study Pack generation must save the note first, mark it `GENERATING`, and redirect the user to Note Detail immediately.
- Note Detail owns generation observation: show a clear `GENERATING` state, friendly loading copy, and light polling until `STUDY_PACK_READY` or `FAILED`.
- `FAILED` must keep note content safe, show a friendly recovery message, and expose `Retry Generation`.
- Retry generation must reuse the saved note content and must not consume Study Pack quota unless a Study Pack is successfully persisted.
- Create/Edit Note should not keep users blocked on the editor while the LLM request runs.

### Marketing Landing Page Rule

- The landing page must explain NoteLib in student terms: notes -> summaries -> quizzes -> review.
- Position NoteLib as a notes library and long-term study workspace first, and as an AI-powered generator second.
- The homepage should make it clear that users build a reusable library of notes before turning those notes into Study Packs for review.
- Public marketing navigation should expose:
  - `Home`
  - `Public Library`
  - `Learn`
  - `Pricing`
  - `Login`
  - `Get Started`
- Public navbar hierarchy must stay clear:
  - navigation links grouped together
  - theme toggle treated as a utility control, not a CTA
  - `Login` as the secondary action
  - `Get Started` as the primary action
- On mobile public nav, keep the theme toggle in the top-header utility cluster and keep the opened menu focused on navigation links plus `Login` and `Get Started`.
- Do not duplicate the theme toggle or primary CTA between the public header and the opened mobile menu.
- Keep the home page focused on hero, how-it-works, features, Free vs Plus vs Pro pricing, demo access, and signup CTA.
- Demo access must be available without signup.
- Public Library should be treated as a public discovery feature and must remain accessible without login.
- The landing page Public Library feature section should pair discovery copy with a framed screenshot preview using `public/landing/feature-public-library.jpg` in a responsive text-left / preview-right layout; keep the screenshot constrained so it supports the section instead of dominating it.
- Pricing shown on marketing surfaces must still come from backend-owned pricing APIs or shared pricing components.
- Landing page metadata should position NoteLib as a note-to-study-pack product, not a generic AI assistant.
- Landing page title, meta description, and Open Graph metadata must stay aligned with the notes-library-first positioning.
- Public marketing/auth surfaces should expose footer links to:
  - `Privacy Policy`
  - `Terms of Service`
  - `Contact`

### Branding Rule

- `notelib-logo-monogram.png` is the primary small-logo mark.
- Use the monogram for:
  - public navbar
  - authenticated app shell
  - mobile headers
  - favicon
  - apple-touch icon
- `notelib-logo-full-light.svg` and `notelib-logo-full-dark.svg` are the public/marketing wordmarks.
- Use the full logo for:
  - landing hero
  - public footer
  - Learn header
  - Pricing header
  - other public marketing headers
  - Open Graph branding
- `notelib-logo-icon.svg` is a product illustration only.
- Do not use the product icon as the navbar logo or favicon.
- Keep favicon and home-screen assets aligned to the NL monogram set.

### Legal Pages Rule

- `Privacy Policy` and `Terms of Service` must remain public and accessible without login.
- Public routes are:
  - `/privacy`
  - `/terms`
- Legal copy should stay simple, readable, and professional rather than highly styled.
- Contact email for launch/legal pages is `support@mail.notelib.app`.

### Onboarding Rule

- Onboarding is active again for all verified users, not only paid-plan users.
- Onboarding should happen once after email verification / first verified entry into the app.
- Onboarding must stay short and reuse the existing step flow.
- Current `/onboarding` flow order is:
  - `Profile Type`
  - `Study Goal`
  - `Input Method`
  - `Study Pack Generation`
  - `Completion`
- `Exam Date` is optional and shown inline on the Study Goal step for `BOARD_EXAM`.
- After `BOARD_EXAM` Screen 3 selects the learner level, if the collected course/program's top published Official Review Set has `itemCount > 0` and `readyCount > 0`, choosing ready-made materials on Screen 4 opens Confirm & Practice: adopt the existing set, persist onboarding completion from `Start this plan`, and land on the adopted Review Set's detail page (not directly inside a quiz — Today's Focus / Continue Studying is one tap away from there). This branch must not author notes, invoke AI generation, launch another quiz mode, or render Screen 8. Lookup failures and zero-depth sets fail open to the unchanged create-first flow; `STUDENT`, `TEACHER`, and `PROFESSIONAL` always retain that flow unchanged.
- Onboarding persists `profileType`, optional `examDate`, and `onboardingCompletedAt`.
- Profile Type is required before creating or generating study content. Client guards are UX only; backend content-creating mutations (note create, note-from-topic, Study Pack generation, note copy, bulk generation, batch import) must enforce this server-side through `ProfileSetupRequiredException` (`ONBOARDING_REQUIRED`) rather than silently defaulting null `profileType`. The guard (`OnboardingGuardService.assertProfileComplete`) fires only for the legacy completed-but-null cohort — `profileType == null && onboardingCompletedAt != null`. Do not narrow it to bare `profileType == null`: users mid-onboarding persist `profileType` only at the final step (after generating), and copy-on-signup runs pre-onboarding, so both are `onboardingCompletedAt`-null and must stay exempt or the activation funnel breaks.
- Users with `onboardingCompletedAt != null` but `profileType == null` must be re-prompted only for Profile Type. Do not force them through learner level, course/program, exam-date, note creation, or Study Pack generation again.
- Onboarding step 2 collects required `learnerLevel` and required `courseProgram` before the first Study Pack flow can continue.
- `bio`, `Learning Style`, and reminder preferences are deferred to `/profile` and `/settings`.
- Profile Type can be edited later in `Profile`.
- Learning Style can be edited later in `Settings > Preferences`.
- Study Reminder Frequency can be edited later in `Settings > Preferences`.
- Public pages and anonymous flows must not be blocked by onboarding.
- NoteLib also has a separate product-onboarding tracker for brand-new users with `studyPackCount == 0`.
- After email verification, first-time users should see a welcome CTA before an empty dashboard so they know to create their first note immediately.
- Empty dashboard states for first-time users must be instructional, not generic.
- After the first Study Pack is generated, Note Detail should point users to Challenge Quiz as the next action.
- After the first Challenge Quiz is completed, surface weak-concept guidance before returning users to normal study flows.
- Product onboarding completion is tracked separately from activation onboarding and should not reuse `onboardingCompletedAt`.
- **Onboarding Study Pack generation (Screen 7) must be idempotent**: `handleStartStudyPack()` must check `draft.noteId` before creating a note; if a note already exists, navigate to Screen 7 instead of creating another. This prevents duplicate notes and study packs from back/forward/refresh behavior.
- **Back button lock during Study Pack generation**: hide the Back button while generation is active (`studyPackGenerating || startingStudyPack`); replace the notice with `Your Study Pack is being created. This step can't be undone.`; restore the Back button on error or completion.
- **Onboarding-only metadata auto-apply**: onboarding may explicitly opt into backend auto-apply for empty `subject` and `tags` when it starts Study Pack generation from an existing note. Normal note generation must keep AI metadata suggestions transient until the user confirms them in the AI Suggestions modal.
- **Learner level is required from onboarding onward**: every completed account must keep a user/profile-level learner level. Teachers should see copy that frames it as the default quiz difficulty for material they teach, with per-generation Teacher quiz overrides remaining explicit.
- The unsupported-program Official Study Plan wishlist records learner demand only. It must remain idempotent per learner and normalized course/program, must not end onboarding or replace fallback routes, and sends no email, scheduler notification, or digest.

### Profile Rule

- `Profile` owns identity and account-related information only.
- `Profile` sections are:
  - `Identity`
  - `Learning Profile`
  - `Profile Type`
  - `Public Profile Link`
  - Teacher-only `Teaching Info` for DOCX export defaults
- Identity uses:
  - `firstName`
  - `lastName`
  - `displayName`
  - `username`
  - `email`
- `displayName` is presentation-only and must never be used as a unique identity.
- `username` is the stable public identity / handle and is used for public attribution and profile links.
- Usernames must be unique, URL-safe, and must not expose emails or raw private user IDs.
- Login accepts either email or username through the same credential field; keep email login working.
- Learning Profile uses:
  - `learnerLevel`
  - `courseProgram`
  - `bio`
- Do not collapse `firstName` and `lastName` into one `name` field in product UI or API contracts unless explicitly requested.
- `Profile Type` remains editable in `Profile` as a separate save action.
- `Profile` may link to `View Public Profile`, but Public Profile sharing and visibility controls do not belong on `/profile`.
- `/profile` layout should stay split into:
  - a top Display Name card with avatar, display name, email, and `View Public Page`
  - an `Identity` card with its own `Save Identity` action
  - a `Learning Profile` card with its own `Save Learning Profile` action
  - a `Profile Type` card with its own `Save Profile Type` action
- The Learning Profile card must carry `id={PROFILE_LEARNING_PROFILE_SECTION_ID}` (`"learning-profile"`) so it is reachable via hash navigation.
- The Dashboard "Adjust Level" CTA must navigate to `/profile?from=dashboard#learning-profile` — this scrolls directly to the Learning Profile card and enables context-aware back navigation back to Dashboard.
- Learning Profile combobox-style inputs should reuse the same input-plus-suggestions pattern as the Note Editor `Subject` field.
- Learning Profile `Course / Program` helper text should adapt to `learnerLevel` so examples match the learner's current study stage.
- Saving `Learning Profile` requires both fields and should show:
  - `Please select your learner level.`
  - `Please select or enter your course / program.`
- Profile save buttons must remain section-specific rather than global.
- Do not move `Learning Style` or study-reminder preferences into `Profile`.
- Email changes must write `pendingEmail` first and only update `email` after verification.

### Hash Navigation Rule

- When a page links to an in-page section with a hash target, the destination `id` must live on a native DOM element such as `section`, `div`, or a heading wrapper. Do not rely on fragment targets attached only to custom wrapper components.
- App Router pages that can be opened directly with a hash must mount the shared `HashScrollListener` (`frontend/components/navigation/hash-scroll-listener.tsx`) with the allowed target ids so direct URL loads and later `hashchange` events scroll correctly after content mounts.
- Prefer concrete route-plus-hash deep links for cross-surface navigation such as `/profile?from=dashboard#learning-profile` when the destination page is known.
- Use the same shared hash-navigation pattern for future `View Full Notes`, settings-section, and profile-section deep links instead of one-off fragment handling.

### Public vs Private Profile Separation Rule

- `Public Profile` (`/public/creator/{username}` canonical, `/public/profile/{userId}` legacy-compatible) is the user's public learning-portfolio surface.
  - Shareable, view-only to non-owners.
  - Shows `displayName`, `bio`, `learnerLevel`, `courseProgram`, `profileType`, public metrics, and public notes only.
  - Owner controls (`Edit Profile`, `Share Profile`, visibility toggle) are on the Public Profile page only.
- `Profile Settings` (`/profile`) is the private account editing surface.
  - Editable identity, learning profile, and profile type.
  - Accessed via the `Edit Profile` button on the Public Profile page.
  - Does not own public-profile visibility or sharing.
- The authenticated app shell avatar dropdown must always offer:
  - `My Profile` → `/public/creator/{username}` when available, otherwise `/public/profile/{userId}` (public identity page)
  - `Settings` → `/settings` (account and app settings)
  - `Sign Out`
- The sidebar Account section must use:
  - `Profile` → `/public/creator/{username}` when available, otherwise `/public/profile/{userId}` (same as `My Profile` in the avatar dropdown)
  - `Settings` → `/settings`
- Terminology rule: **Profile = public identity page. Settings = account/app settings.** Do not use "Account Settings" as a nav label — use plain "Settings".

### Shared Share Behavior Rule

- NoteLib uses one share pattern for all shareable content (notes and profiles).
- For public content: clicking Share opens a modal with title, `Shareable URL` field, `Copy Link`, and `Close` buttons.
- For private content: clicking Share opens a confirm modal first. The confirm offers `Cancel` and `Make Public & Share`. The share modal only opens after the owner confirms the visibility change.
- Share modal structure:
  - Note share modal title: `Share this note`
  - Profile share modal title: `Share this profile`
- Private note confirm: title `This note is private`, body `You need to make this note public before sharing. Anyone with the link will be able to view and copy this note.`
- Private profile confirm: title `This profile is private`, body `You need to make this profile public before sharing. Anyone with the link will be able to view your public profile and notes.`
- Do not implement content-specific share flows. Reuse `AppModal` with the same layout for all share actions.
- Do not use toast-only or inline-text-only share confirmation as the primary share feedback.

### Preferences Rule

- `Settings` should show `Preferences` before `Plan & Billing` and `Account`.
- `Preferences` currently includes `Learning Style` plus `Study Reminders`.
- `Learning Style` is stored as `engagementMode`.
- Reminder toggles are:
  - `inactivityRemindersEnabled`
  - `weakConceptRemindersEnabled`
- Preference values must persist in backend and be returned by `GET /auth/me`.
- Future reminder cadence should be guided by `Learning Style`, but scheduling logic is a separate task.

### Account Deletion Rule

- Account deletion starts as a reversible soft-delete: set `PENDING_DELETION` + `deleted_at`, revoke sessions, block normal login, and allow reactivation during the 30-day grace window.
- The irreversible purge reassigns public notes, their retained Study Packs, and financial records to the fixed deleted-user sentinel, removes private owned study data, and never deletes `analytics_events`.

### Data Export Rule

- Account data export must stay owner-only: resolve the requester from the authenticated principal, never accept a `userId` parameter, and query content through owner/user-scoped finders only.
- Data export returns one synchronous JSON attachment and must exclude secrets/tokens, analytics events, and financial/billing records.

### Upgrade CTA Rule

- Upgrade CTAs must be plan-aware. Never hardcode `Go Pro` as the universal upgrade CTA.
- Use `getUpgradeCtas(currentPlan)` from `frontend/src/config/plans.ts`:
  - Free → primary `Upgrade to Plus`, secondary `Go Pro`.
  - Plus → primary `Upgrade to Pro`, no secondary.
  - Pro → no CTAs (already top plan).
- Upgrade CTAs that drive in-app plan selection must navigate to `/settings?section=plans`. The Settings page reads the `section` query param, scrolls to the Plan & Billing card, and applies a temporary highlight ring.
- The `/pricing` page is the public marketing landing surface and stays linked from the navbar/landing only.
- Apply this rule on quiz result screens, the paywall modal, the Study Pack limit modal, the post-success upgrade nudge, and any near-limit banners.
- `PLANS` source of truth is `docs/product/PLANS.md`; runtime numbers live in `frontend/lib/pricing-config.ts` and feature lists in `frontend/src/config/plans.ts`. Keep all three in sync when limits or plan copy change.

### Analytics Rule

- Track product, growth, and upgrade events through the shared analytics event model.
- Analytics must be non-blocking and must never break the primary user action if persistence fails.
- Frontend analytics delivery may refresh the access token and retry once after a 401 only while the page is still live. Analytics refresh or retry failure must give up silently: never clear auth state, invoke `handleUnauthorizedSession`, redirect, throw, or block a product flow. Hidden/unloading documents keep the original best-effort `keepalive` request and must not start refresh work — **and the reason is load-bearing: `AuthService.refresh` revokes the presented refresh token and issues a new one, so a refresh whose response is lost to an unloading page leaves the client holding a dead token and logs the learner out on their next visit.** Do not remove this guard as a simplification.
- Backend analytics must publish after the surrounding transaction commits (`AFTER_COMMIT`) and persist off-request through `analyticsTaskExecutor`; never write analytics mid-transaction.
- Backend services should record server-truth events for note, Study Pack, review, auth, public-copy, and subscription flows.
- Frontend/browser-only funnel events may post through `/api/analytics/events`.
- Admin reporting should read from analytics events plus core entity counts via `/api/admin/analytics/summary`.
- Admin onboarding completion rate must remain `users.onboarding_completed_at IS NOT NULL` over all rows in `users`; never derive it from `ONBOARDING_V2_COMPLETED`, because the users-table definition is the checkpoint baseline.
- **Tracked completion events**: `QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, and `ADAPTIVE_PRACTICE_COMPLETED` are fired from the frontend in the `finally`/completion block of each quiz flow and must not block the primary action.
- **Tracked funnel events**: `FEATURE_LOCKED_CLICKED` and `UPGRADE_CLICKED` are fired from paywall surfaces and the `PostSuccessUpgradeNudge` component respectively.
- `AnalyticsEventType` in `frontend/lib/api.ts` is the canonical union of all allowed event names — add new event names there before using them.
- Entry-point attribution for route-launched sessions uses the `entry` query parameter convention. Backend event producers must validate entry values against a known set and normalize absent or unknown caller-controlled values to a stable fallback; never persist arbitrary query input in analytics metadata.

### Retention Email Rule

- Retention emails are scheduled backend jobs, not request-time actions.
- V1 email types are:
  - `WELCOME`
  - `INACTIVITY`
  - `WEAK_CONCEPT`
  - `UNFINISHED_NOTE`
- Retention emails must log sends in `email_log` and respect same-type cooldowns before sending again.
- `INACTIVITY` and `UNFINISHED_NOTE` should honor `inactivityRemindersEnabled`.
- `WEAK_CONCEPT` should honor `weakConceptRemindersEnabled`.
- `WEEKLY_SUMMARY` should honor `weeklySummaryRemindersEnabled`, which defaults off until the user opts in.
- `RE_ENGAGEMENT_2025` should honor `marketingEmailsEnabled`, which defaults off until the user opts in.
- `DUE_CONCEPTS_DIGEST` is enabled by default for new email/password and Google signups only; `AuthService` owns those explicit signup defaults while the database default remains false, and existing users' persisted preferences must never be backfilled or changed implicitly.
- A null or empty `users.review_days` means the due-concepts digest keeps its existing schedule; it must never mean "never send." **And the digest must dispatch DAILY, not on a weekly cron.** The day filter compares a user's chosen weekdays against the day the job runs, so a weekly dispatch makes exactly one weekday reachable and silently drops every learner who chose any other — the failure `v0.72.0` shipped and had to fix. Frequency is capped by `dueConceptsDigestCooldownDays`, not by the cron, so daily dispatch does not increase how much email anyone receives. Keep `@Scheduled(zone = "Asia/Manila")` pinned so the cron's weekday and the filter's weekday cannot disagree. Non-empty review days are matched in the retention email budget zone.
- Transactional account and billing emails are never gated by optional email preferences.
- Transactional email is never gated by the re-engagement daily budget; the budget only caps optional retention dispatch.
- Resend bounce/complaint suppressions apply to all email sends; suppressed addresses are skipped instead of retried.
- Optional emails must carry a tokenized one-click unsubscribe that maps category to the existing preference flag; transactional emails never carry unsubscribe links or headers, and unsubscribe tokens must not include PII beyond the opaque user id.
- Reminder cadence may later vary by `Learning Style`, but V1 stores the inputs and uses fixed thresholds.

### Verification Email Rule

- After a user successfully verifies their email, send a one-time welcome email.
- The welcome email should link to `Dashboard` and explain the first-study-pack flow.
- Welcome emails must only send once per user and should be guarded through `email_log`.
- User-facing email templates should greet recipients with:
  - `Hi {firstName},` when `firstName` exists
  - `Hi there,` when it does not
- User-facing email templates should end with the standard footer:
  - `— NoteLib`
  - `Turn Notes Into Quizzes`
  - `https://notelib.app`
- Welcome email copy must reflect the current Free / Plus / Pro plan:
  - Free includes `10` Study Packs/month, Quick Review, limited Challenge Quiz, and Public Library access
  - Plus messaging highlights higher monthly limits and exports for regular study
  - Pro messaging highlights Adaptive Practice, Weak Concept Training, Board Exam Mode, and the highest limits
- Do not describe Challenge Quiz as paid-only in onboarding, welcome, or reminder emails.

### Admin Dashboard Rule

- Admin Dashboard is internal and read-only in v1.
- Access must be restricted to `ADMIN` users.
- Reuse existing analytics, billing, subscription, payment, and library data before adding new reporting storage.
- Prefer summary cards and simple tables over filters, charts, or exports unless explicitly requested.
- Admin v1 should cover overview, billing, engagement, public-content growth, recent upgrades, recent failed payments, and recent feedback.
- The strict days 7–14 retention figure must never be reported or quoted on its own. It undercounts real returns by roughly 3.7× and is retained only for historical comparability; show it beside the wider retention windows and their separately labelled maturity denominator.

### Feedback Rule

- Authenticated app users should be able to submit in-app feedback during soft launch.
- Feedback should capture `message`, authenticated `userId`, `email`, and the current page URL.
- Feedback submission must persist to the `feedback` table and may send a best-effort support notification email.
- Admin Dashboard should expose recent feedback in a read-only table.

### Pricing Rule

- Backend owns subscription pricing, region detection, voucher eligibility, and Xendit checkout creation.
- Never hardcode backend checkout pricing; always load billable amounts from billing config or pricing services.
- Frontend must use the billing pricing API for pricing display in Settings, pricing surfaces, and upgrade prompts.
- Pricing UI copy, plan descriptions, CTA labels, and feature lists must come from the centralized frontend plan config.
- Never hardcode pricing-card features or plan CTA labels directly in UI components when the shared plan config already owns them.
- Shared pricing surfaces may keep the existing reviewer-safe PHP and USD display config, but checkout creation and upgrade eligibility stay backend-owned.
- Intro pricing and first-time promos must be implemented through the voucher/promotion system, not as a boolean on `User`.
- If pricing-surface messaging and runtime feature gating diverge, backend plan enforcement plus `GET /api/me/plan` remain the behavior source of truth until the product intentionally changes the gate.

### Payments Safety Rule

- Never grant paid access from frontend logic, success pages, or redirect callbacks.
- Only validated webhook-confirmed payments may update user paid-plan status.
- All plan and entitlement logic must use the `subscriptions` table as the source of truth.
- Preserve subscription history in `subscriptions`; do not collapse the table to one row per user.
- Only one `ACTIVE` subscription row should exist per user at a time.
- Do not introduce plan flags or paid-state fields on `users`.
- Always validate the Xendit `x-callback-token` before processing webhook payloads.
- Webhook handling must stay idempotent through persisted provider event records and payment transaction lookups.
- Voucher redemption history must be written only after a confirmed `PAID` webhook, never while checkout is still pending.
- Payment-flow doc updates are required whenever checkout, webhook, returnUrl, or paid-plan expiry behavior changes.

### Billing History Rule

- `Settings -> Plan & Billing` should include a read-only billing history section below the current plan and usage card.
- The billing summary card should show current plan, subscription status, billing cycle, and renewal or end date.
- If `cancelAtPeriodEnd=true`, show that the active paid plan will end on the stored date and will not renew.
- Payment history must come from `PaymentTransactionEntity` data via `GET /api/billing/history`.
- Billing history rows should stay user-friendly and must not expose raw webhook event names.

### Library Rule

- Library is note-based and contains the current user's notes (Draft + Study Pack Ready).
- `Study Pack Ready` is the learner-facing readiness indicator for normal Library browsing.
- `Quiz Ready` is a Teacher/exam-export workflow indicator. Show `Quiz Ready` badges and filters only for Teacher private-library browsing or explicit exam-builder/admin-content contexts.
- Student and Board Taker profiles must not see `Quiz Ready` badges or filters in normal Library browsing; reset hidden `Quiz Ready` filter state if profile/context changes.
- Public Library must not expose Teacher-specific `Quiz Ready` UI.
- Do not remove generated-quiz readiness data from backend payloads; Exam Builder still needs it for selection, question counts, disabled states, and exports.
- Public Library is note-based and contains notes where `visibility=PUBLIC`.
- Public Profile is a public showcase of one creator's public notes and contribution stats.
- Public Profile may show `bio`, optional `learnerLevel`, optional `courseProgram`, and derived subject chips, but it remains a learning profile rather than a social-media profile.
- Public Profile should feel like a lightweight learning portfolio:
  - compact metrics only
  - real note-usage signals such as public-note count, copies, shares, and views when available
  - optional featured-note callout only when backed by real usage data
  - no follower/social-network patterns
- Private Library and Public Library should keep the same top-level list structure:
  - `Search`
  - `Filter`
  - `Sort`
  - notes list
- Current library filtering and sorting stay frontend-side over loaded note-list payloads.
- Backend note-list payloads must expose the metadata needed for library filtering/sorting, including note `courseProgram`, `createdAt`, `updatedAt`, and public-note owner `learnerLevel` when applicable.
- Private Library should expose its primary organization controls inline above the note list in this order:
  - `Search`
  - `Subject`
  - `Popular Tags`
- Private Library search should match note `title` and `tags` in real time.
- Private Library subject filtering should be single-select with `All` as the default chip and should use a one-line horizontal scroll rail rather than wrapping.
- Private Library should keep a `+ More` chip at the end of the subject rail so users can open the full searchable subject selector without adding vertical clutter.
- Private Library should not expose the full tag list by default; show only a limited `Popular Tags` rail plus a `+ More` control.
- Private Library `+ More` should open the shared selector surface:
  - subjects -> searchable single-select list
  - tags -> searchable multi-select list with a selected-tags quick-deselect section near the top
  - mobile -> bottom sheet
  - desktop -> modal/sheet
  - actions -> `Apply`, `Clear`
- Selector option ordering may prioritize recent use first, then frequency, then alphabetical order.
- Private Library tag filtering should remain multi-select, use OR logic within the tag group by default, and combine with search + subject on the loaded note list.
- Rationale: OR matching makes tag browsing feel broader and avoids false empty states when users combine tags from different notes.
- Notes without an explicit subject may derive a temporary fallback subject from existing saved metadata so Library grouping/filtering still works.
- Public Library filters should support:
  - `Course / Program`
  - `Learner Level` when public note results expose it
  - `Subject`
  - `Tags`
  - `By You`
  - `Official`
  - `Community`
- Public Library should keep search first, then one-line horizontal rails for `Subjects` and `Popular Tags` before the note grid.
- Public Library subject filtering should stay single-select with `All` as the default and use a `+ More` chip to open the full searchable selector.
- Public Library tag filtering should stay multi-select, use OR logic within the tag group by default, and expose only a limited `Popular Tags` rail plus a `+ More` selector.
- Public Library `+ More` should reuse the shared selector surface:
  - subjects -> searchable single-select list
  - tags -> searchable multi-select list with selected tags surfaced near the top
  - mobile -> bottom sheet
  - desktop -> modal/sheet
  - actions -> `Apply`, `Clear`
- Public Library is a curated discovery page first, not a flat generic list.
- Discovery mode should preserve:
  - `Featured Notes`
  - `Most Popular`
  - `Recently Added`
- Featured Notes should remain visually distinct from the rest of the Public Library sections.
- Control Public Library density with section limits and per-section `View More`, not by removing previews, tags, subject badges, source labels, or engagement metadata.
- Current discovery-home limits are:
  - Featured Notes -> 3
  - Most Popular -> 5
  - Recently Added -> 5
- `View More` may use the same Public Library route with section-specific state/query params as long as the curated discovery model remains intact.
- Public Library should include the current user's own public notes, other users' public notes, and official NoteLib public/sample notes.
- Public Library cards should label note source as:
  - `By You` for the current user's own public notes
  - `By NoteLib` plus `Official` badge for the official NoteLib account
  - `By {displayName} · @{username}` for other public notes when username is available
- Public author labels are viewer-relative:
  - owner viewing own public note -> `By You`
  - official NoteLib account -> `By NoteLib` with `Official`
  - all other public notes -> `By {displayName} · @{username}` when username is available, otherwise `By {displayName}`
- `displayName` is the readable public author label, not a unique creator identity.
- `username` is the stable public author identity and should back public creator links.
- Public Library cards and public note detail must not rely on `displayName` alone for creator identity when duplicate names exist.
- Creator links should use a stable public identifier:
  - preferred -> username / handle when available
  - fallback direction -> generated public slug
- When disambiguation is needed or a handle exists, public labels may render `By {displayName} · @{handle}` while keeping `displayName` first for readability.
- Never show public author emails or raw private user IDs on public surfaces.
- If stable public handles/slugs are introduced, existing public links must remain valid through compatibility or redirect handling.
- Reserved display names must be blocked server-side. Reject exact matches for:
  - `notelib`
  - `admin`
  - `support`
  - `official`
  - `moderator`
  - `staff`
  - `team`
- Also reject any display name containing `notelib` and return:
  - `This display name is reserved. Please choose another name.`
- Public note detail should switch its primary CTA by ownership:
  - owner -> `Open Note`
  - non-owner -> `Copy to My Library`
- Public note detail header should show `Subject • Author` using the same viewer-relative label logic as library cards.
- Public note detail is read/copy/share only:
  - owner -> `Open Note`, `Share`
  - non-owner -> `Copy to My Library`, `Share`
- Public note detail should not expose edit, delete, generation, or study actions; generation remains a Note Editor responsibility and quizzes remain on study surfaces.
- Public and private note detail should both expose `Summary`, `Key Concepts`, `Quiz`, and `Full Notes` so the original note stays easy to inspect.
- Keep `Summary` as the default tab; `Full Notes` is for reading the complete original note body, not a collapsed preview.
- The `Summary` view should include a subtle `View Full Notes →` CTA that switches tabs without a full page reload and without interrupting the current reading position.
- Public Profile note cards should reuse the public-note route and must not expose private workspace actions.
- Subject UI rules:
  - render subjects as badges across library cards and note headers
  - note headers should place `Subject Badge • Author`
  - `notes.subject` remains the persisted source of truth; do not add a subjects table unless explicitly requested
  - note editor and library subject filters should use backend-driven distinct subject suggestions from persisted notes
  - subject inputs must still accept custom typed values and save them directly into `notes.subject`
  - normalize saved subjects for whitespace and dash formatting so equivalent values reuse the same subject suggestion/filter label when possible
  - treat subject reuse checks as case-insensitive while keeping a readable display label
  - AI-generated subjects should prefer specific reusable academic labels, often `Primary field – subtopic`, rather than broad umbrella fields
  - avoid broad generated labels such as `Medicine`, `Engineering`, `Education`, `Law`, or `Business` when the notes support a more specific subject
- Course / Program UI rules:
  - **Superseded in part by `docs/architecture/ADR-001-canonical-knowledge-architecture.md` (Accepted 2026-08-03) — read that ADR before changing anything in this block.** Two rules previously stated here have been retired by it: that `courseProgram` is "the top-level note-classification shelf above `subject` and `tags`," and that a `course_programs` table must not be added. Both described the single-axis model the ADR replaces. Under the ADR, `courseProgram` is **not** the classification apex — it is decomposed into four independent axes: Subject (*what*), **Domain Context** (*how it is authored*, the sole LLM domain constraint), **Note Learner Level** (*how deep*), and **Applicable Programs** (*where it appears*, discovery only, never reaching a prompt). `v0.70.0` added the audited `course_programs` catalog and `program_families`, plus nullable `notes.course_program_id` and `users.course_program_id` FKs alongside the legacy strings. `v0.71.0` Slice 1 added the JDBC-only `note_course_program` join and writes it from Teacher/Admin authoring surfaces plus the narrow Admin Dashboard curator view. Slice 2 makes filters, facets, badges, and Public Library program search read the join first, with the legacy `notes.course_program` string used only when a note has no join rows; name/slug URL contracts remain unchanged and private Library search still matches title and tags only. Program Family expansion is a save-time authoring pre-fill that adds every catalog member as an explicit selection; nothing resolves a family at read time.
  - **Do not "restore" either retired rule.** `courseProgram` as a single free-text field carrying five incompatible responsibilities is the defect the ADR exists to fix, not a constraint to preserve.
  - `users.courseProgram` and `notes.courseProgram` remain persisted string fields. `notes.courseProgram` is the permanent free-text **personal-notes** program field, not legacy: learners write exactly one value there. Teacher/Admin curation uses catalog-only, one-or-many `note_course_program` rows. This single mode gate controls both vocabulary and cardinality. Discovery stays join-first with the string used whenever there are no join rows; Domain Context is required whenever curation selects more than one program, and a program list must never reach an LLM prompt.
  - note editor, onboarding, profile, and note-detail metadata course/program inputs should use one shared autocomplete behavior backed by saved-value suggestions plus curated defaults
  - `/profile`, the learner Note Editor, private learner Note Detail, and the Dashboard lightweight profile-completion prompt must suggest live `GET /api/course-program-catalog` names first, then the retained hardcoded `COURSE_PROGRAM_SUGGESTIONS` fallback and saved/current values; failed, slow, or empty catalog loads must keep the hardcoded list immediately usable
  - onboarding is deliberately excluded from catalog-first suggestions until after the 2026-09-11 completion checkpoint and must continue using `COURSE_PROGRAM_SUGGESTIONS` directly
  - authenticated saved-value course/program suggestions may still come from `GET /api/course-programs?scope=mine`; public/discovery filter values come from catalog-joined public-note names through `GET /api/course-programs?scope=public`
  - course/program inputs must still allow custom typed values
  - `COURSE_PROGRAM_VALUE_SELECTED` fires only after a committed save **that actually changed the value** on `profile`, `note-editor`, `note-detail`, or `dashboard-prompt`. **Every one of those handlers persists other fields alongside the program**, so firing on unchanged values fills the metric with re-saves of pre-existing (overwhelmingly off-catalog) strings and makes the rate uninterpretable. The comparison is against the **persisted** prior value, never the draft — on the Note Editor the draft *is* the value being saved, so comparing against it suppresses every event; metadata is exactly `{ surface, matchedCatalog }`, catalog matching is trimmed and case-insensitive using course/program normalization, and the raw learner-typed value must never be recorded
  - when the live catalog is unavailable, omit `COURSE_PROGRAM_VALUE_SELECTED` rather than deriving `matchedCatalog` from fallback suggestions; analytics failure must never block the save
  - typing should filter suggestions in real time, case-insensitively, with prefix matches ahead of contains matches
  - typing must not keep the full unfiltered list visible
  - existing matching suggestions should appear before the custom `Use "..."` action
  - exact case-insensitive matches should reuse the existing saved display label instead of creating a casing variant
  - saved course/program values should normalize whitespace and dash formatting so equivalent values reuse the same suggestion/filter label when possible
  - course/program reuse checks should be case-insensitive while keeping a readable display label
  - new `course_programs` catalog names must be normalized for whitespace and dash formatting before duplicate detection and persistence so the stored name exactly matches the Public Library chip and exact-match filter predicate; do not rewrite existing catalog rows as part of create-time normalization
- Public Library canonical browsing route is `/public/library` for both signed-in and signed-out users.
- Do not introduce duplicate Public Library browse routes or wrappers such as `/library/public`; keep legacy paths as redirects only when compatibility is required.
- Public subject listing pages must not become second canonical list pages for query-filtered browsing; use `/public/library?subject={subjectSlug}` for shareable subject filtering. `/public/library/{subject}` is a separate, server-rendered canonical subject landing page (shipped v0.14.0 — see `docs/features/public-library.md`), not a redirect, and must not be merged with the query-filter view without a dedicated future refactor.
- Public SEO note pages use `/public/library/{subject}/{slug}` as the canonical route.
- Public SEO pages must stay accessible without login and indexable only for `PUBLIC` notes.
- Public landing page should emit JSON-LD `WebSite` schema.
- Public Library index should emit JSON-LD `CollectionPage` schema.
- Public Library filter state must stay in sync with URL query params; direct opens of filtered `/public/library?...` URLs must restore the same selected filters in the UI.
- Public Library search inputs must not update the URL on every keypress. Use local input state plus a short debounce, then `router.replace(..., { scroll: false })`.
- Public Library filter interactions must preserve focus and scroll position. Subject chips, tag chips, program changes, sort changes, and clear-filter actions must not jump the page back to the top.
- Public Library tag browsing must always stay accessible through a dedicated action such as `Browse all` / `Browse tags`; do not rely on a disappearing `+ More` tag chip when the visible tag list is short.
- Searchable Public Library selector modals must keep the search input focused while typing; do not let modal rerenders or close-button autofocus steal the caret.
- Public SEO note pages should emit JSON-LD `Article` schema using real note data only.
- `robots.txt` must allow public crawling and disallow authenticated/private app areas such as `/dashboard`, `/library`, `/notes`, `/settings`, `/admin`, and `/api`.
- `sitemap.xml` must include only public SEO-safe routes: `/`, `/privacy`, `/terms`, `/public/library`, canonical public subject URLs, and canonical public note URLs.
- Private notes must never be exposed through the public SEO route.
- Copying a public note must preserve attribution via `copiedFromNoteId` and `copiedFromUserId`.
- Public Library copy UX should keep the existing copy endpoint but make public copies idempotent per user/source note.
- Public Library cards may include subtle inline `Save` plus lightweight heart/like controls as the allowed exception to the no-inline-card-actions rule on note cards.
- Public Library card CTAs should stay compact:
  - icon + short label
  - outline / ghost weight
  - never full width
  - aligned with author metadata in the footer row rather than taking over the full card width
- Guests clicking `Save` should see an auth prompt modal before auth navigation.
- Guests clicking the heart/like control should see an auth prompt modal before auth navigation.
- If the current user already has that public note in their library, replace `Save` with muted `Saved` instead of showing another navigation button inside the card.
- Successful Public Library copies should offer `View Note` and `Start Review` follow-up actions, with:
  - `Start Review` as the primary CTA
  - `View Note` as the secondary CTA
- Public Library copy-success feedback should use:
  - a desktop modal with a visible top-right close button
  - a mobile bottom sheet with tap-outside and swipe-down dismissal
  - a success-leading visual treatment with stronger title hierarchy and a subtle check indicator
  - concise body copy (`You can start reviewing now or come back later from your library.`)
  - desktop action alignment of `View Note` then `Start Review`, right-aligned
  - mobile action stacking with full-width buttons and `Start Review` visually first
  - compact spacing and softened depth so the surface feels product-grade without becoming heavy
- Copied private notes should display attribution as `Copied from {title} in Public Library.` when source metadata exists.
- Study Pack-ready Note Detail should keep quiz history on the note page:
  - show `Recent Sessions` below `Performance Overview`
  - merge completed Quick Review and Challenge Quiz attempts in reverse-chronological order
  - `Recent Sessions` is the entry point into session review on Note Detail
  - desktop and mobile both open the same dedicated session-review page with a clear back path to Note Detail
  - use stored session data only for answer review and concept breakdown; do not call LLMs for session history or review
  - allow graceful fallback summaries for older sessions that do not have full stored quiz detail
  - weak concepts in session review use the same `< 60%` accuracy threshold as other study surfaces

### Explore Navigation Rule

- Authenticated primary navigation order is Dashboard, the existing profile-aware Collections label, Library, Explore, Progress.
- `/explore` is an authenticated composite discovery front door with `Review Sets` and `Notes` tabs plus an Exam Hub index pointer.
- Explore must reuse the existing Official Review Set catalog and Public Library rendering. It must not replace, redirect, or redefine the canonical `/collections/published` and `/public/library` routes.
- **⚠️ NARROWED by the ratified amendment below (2026-08-17), and narrowed only in one place:** the **bare `/public/library` list page** becomes a legitimate future redirect target once an SEO-parity evidence bar clears. **`/public/library/{subject}` and `/public/library/{subject}/{slug}` are NEVER redirected, full stop** — both carry canonical metadata and per-page sitemap entries. Everything else in this rule stands: as of `v0.84.0` the marketing nav names Explore instead of Public Library, which changes **navigation primacy only** — `/public/library` remains a live canonical route and a Discovery System source, not a deleted one.
- Library stays structurally separate from Collections and Explore.
- The mobile bottom tab bar replaces its former Public Library tab with Explore and keeps the existing `mobileTabBarEnabled` preference gate.
- The anonymous marketing navbar is separate and must not gain the authenticated Explore item.
- Exam Hub Official Review Set enrichment uses exact normalized `courseProgram` matching only, remains anonymous-previewable, and must fail open so public-note content still renders.

### Card Interaction Rule

- Library cards, Public Library cards, and Public Profile cards must use a consistent interaction model.
- The whole card should be clickable to open the detail page.
- Do not add inline action buttons or note-card context menus to note cards, except for the Public Library `Save` / `Saved` CTA in the footer row.
- Note cards are preview/navigation surfaces only; note actions belong in Note Detail.

### Design System — Icons and Buttons

1. Use consistent icons for common actions (`edit`, `delete`, `share`, `copy`, `open`, `public/private`) and do not drift per page.
2. Desktop buttons must show icon + text.
3. Mobile buttons must show icon only.
4. Avoid note-card action buttons; if a non-note card needs actions, place them at the bottom-right.
5. Header/page actions should be placed at the top-right.
6. Visibility should be shown as a badge/dropdown, not a large button.
7. Entire note cards should be clickable; do not add `Open` buttons inside cards.
8. Do not introduce a new icon for an existing action without updating this document.
9. Sidebar navigation icons must stay consistent:
   - `Dashboard` -> `Home`
   - `Library` -> `Book`
   - `Public Library` -> `Globe`
   - `Profile` -> `User`
   - `Settings` -> `Gear`
   - `Admin` -> `Shield`
10. Use outline-style icons only. Do not mix outline and filled icon styles.
11. Do not use emoji as icons in product UI.

Primary CTAs may keep full text on mobile when the action would be ambiguous as icon-only.

### Tabs vs Buttons Rule

- Tabs are for switching views such as `Summary`, `Key Concepts`, `Quiz`, and `Full Notes` within the same note.
- Buttons are for actions such as `Start Quiz`, `Delete`, `Save`, and `Share`.
- Tabs should use an underline-style navigation treatment, not filled or outline button styling.
- Tabs may include small outline icons.
- Desktop tabs should show icon + text.
- Mobile tabs should also show icon + text when they switch major note views.
- Note Detail tab order should stay `Summary` -> `Key Concepts` -> `Quiz` -> `Full Notes`.
- Note Detail should still guide the reading flow from `Summary` into the source material through a subtle `View Full Notes →` CTA inside the summary view.
- Switching tabs must not reset page scroll to the top; preserve the current content area when the tab state changes.
- Query-string tab switches on Note Detail must not trigger a note refetch or loading-state remount.

### Mobile Button Rule

- Important action buttons must display icon + text on mobile.
- Do not use icon-only buttons for major actions such as navigation, quiz entry, copy/share, create, save, upgrade, or public-page actions.
- Prefer clarity over minimal UI.
- Keep this behavior consistent across Dashboard, Note Detail, Library, Public pages, Profile, and Settings.
- Small utility controls may remain icon-only only when the action is already highly familiar (`edit`, `delete`, `back`, menu, theme toggle, notifications, avatar).

### Dark Mode Button Contrast Rule

- Outline buttons must use lighter borders and lighter text in dark mode.
- Border contrast must be visibly brighter than the card/background surface behind it.
- Text should be near-white for readability in dark mode.
- Outline buttons should have a visible dark-mode hover fill.
- Do not reuse light-mode border contrast assumptions in dark mode.

### Quiz Mode Icon Rule

- `Quick Review` uses a lightning icon.
- `Challenge Quiz` uses a trophy or clipboard-style challenge icon.
- `Adaptive Practice` uses a target or focused-practice icon.
- Do not use the same icon for different quiz modes.

### Post-Quiz UX Consistency Rule

All three quiz flows (Quick Review, Challenge Quiz, Adaptive Practice) must follow the same UX pattern:

- **No "Note" button** on any quiz screen — `Note` as a `<Button>` is forbidden
- Navigation back to the note must be a `← Back to Note` **text link** (`BackLink` component), placed **below** action buttons, never grouped with them
- **Exactly one primary CTA per result screen.** Never show two equal-weight primary buttons.
- **Button hierarchy** on result screens:
  - Primary: next learning action — Quick Review: `Practice Weak Areas` (Adaptive) when struggling + available; `Practice Again` when struggling + adaptive unavailable; `Take Another Challenge` after strong/perfect result — Challenge Quiz: `Practice Weak Concepts` when weak concepts exist, otherwise `Take Another Challenge` — Adaptive Practice: `Generate New Set`
  - Secondary: review/repeat/support actions (`Review Answers`, upgrade nudge, secondary `Practice Again`)
  - Navigation: `← Back to Note` link below
- Edge states such as empty quiz data, monthly limits, unavailable sessions, or missing weak-area labels must keep a clear next step and use text-link navigation rather than `Back to Note` buttons.
- **Confidence feedback** (Quick Review only): moved to a secondary section below the primary CTA group; after selecting, option buttons are replaced by a badge — `🟢 Confident`, `🟡 Improving`, `🔴 Needs Practice`; "Thanks for the feedback." text is removed
- **Inline learner level selector** (Quick Review and Challenge Quiz result screens): pill-group selector loads the user's current `learnerLevel` via `GET /auth/me` when the result becomes visible; changing a level saves via `updateProfileLearnerLevel` in `lib/api.ts` and shows a toast; do NOT add a new learner level system — reuse the existing `LearnerLevel` enum and `LEARNER_LEVEL_OPTIONS`
- **Adaptive Practice completion**: "Generate New Set" is always the primary button; `← Back to Note` link below
- **Review Answers**: Quick Review, Challenge Quiz, and Adaptive Practice must use the shared post-quiz review pattern showing question text, selected answer, correct answer, explanation, and concept chip.
- Review Answers answer states must stay consistent: correct answer uses restrained green styling, incorrect selected answer uses restrained red styling, neutral distractors stay quiet, and selected-correct answers show both `Your answer` and `Correct answer`.
- Review Answers should use stored quiz/session data (`question`, `choices`, selected canonical choice indexes, `correctIndex`, `explanation`, `concept`) so completed-session history/review can reuse the same structure later.
- Motivation/feedback messages use `mapPerformanceLevel` thresholds for consistency (Excellent / Good / Fair / Needs Improvement)
- While a quiz session is active, replace normal header back navigation with active-session text plus `Leave Quiz`; navigation away must open the shared `Leave quiz?` confirmation instead of leaving immediately.
- The shared leave confirmation copy is `You are currently in an active quiz. Leaving will forfeit your progress.` with `Stay` and `Leave Quiz` actions.
- Confirmed leaves mark the session `FORFEITED`; Challenge Quiz and Adaptive Practice forfeits must not refund quiz credits or mark the session completed.
- Board Exam Mode is the exception to the generic leave-forfeit copy: it uses `Leave exam?` with `Stay` and `Submit & Leave`, and confirming the leave submits the current exam and counts it as completed.

### Challenge Quiz — Exam Mode Rule

- Challenge Quiz must behave as an exam: **no correctness feedback during answering**.
- Board Exam Mode is the explicit strict-exam presentation of the Challenge Quiz engine and must be available as a distinct Challenge mode for Pro users.
- Challenge Quiz entry must present both `Challenge Quiz` and `Board Exam Mode` as explicit mode choices rather than inferring Board Exam from billing or difficulty-selection capability.
- Board Exam Mode must use a formal `Board Exam setup` confirmation state with timer/question/result summary plus `Cancel` and `Start Exam`.
- Board Exam setup must also explain that the mode is a focused, distraction-free exam simulation, results are delayed until completion, and navigation will be limited intentionally during the session.
- Tapping `Start Exam` must show a confirmation modal before quiz generation starts so users understand the stricter flow.
- Board Exam Mode uses the same Challenge Quiz quota and credit rules as standard Challenge Quiz in the current product stage; do not create a separate billing gate for Board Exam Mode.
- Board Exam Mode always uses a fixed recommended difficulty/question count (`DIFFICULTY_MIXED`) — it never exposes a difficulty selector (v0.60.1 removed Challenge Quiz's manual selector entirely; Board Exam Mode never had one).
- Do not render "Correct" / "Incorrect" labels, green/red highlights, or explanations while the quiz is in progress.
- Standard Challenge Quiz may keep a lighter practice-oriented answering UI, but Board Exam Mode must use a more formal neutral selected-answer state and cleaner hierarchy.
- Board Exam running state should reinforce the mode visibly with `Board Exam Mode`, `Exam in progress`, and subtle copy that limited navigation is intentional.
- A one-time, dismissible Board Exam focus tip may explain that distractions are hidden to simulate a real test environment.
- Question-number navigation during Board Exam Mode may show current/answered/unanswered states, but must not reveal correctness.
- Board Exam timers must use persisted session timing as the source of truth (`timerStartedAtEpochSeconds + timeLimitSeconds`) and survive refresh/reload without resetting or extending the exam.
- Board Exam timer UI should surface calm warning states as time gets low, but once time expires it must lock answer changes/navigation immediately.
- Timer expiry must auto-submit exactly once per expiry event; if timeout submission fails, the page may expose explicit retry submission but must not silently keep auto-submitting every tick.
- Browser fullscreen/focus entry is best effort only; a denied fullscreen request must not block starting or resuming the exam.
- The Challenge Quiz start screen must disable difficulty controls and the Start button immediately after Start is clicked.
- Duplicate Challenge Quiz start requests must be blocked while quiz initialization is in flight.
- All result calculations (score, performance level, concept breakdown, weak concepts) must be derived from quiz session data — **no LLM calls for statistics**.
- Use the utility functions in `lib/challenge-quiz-results.ts` for testable result computation.
- Performance level thresholds: 90–100 → Excellent, 75–89 → Good, 50–74 → Fair, 0–49 → Needs Improvement.
- Weak concept threshold: accuracy < 60% (`WEAK_CONCEPT_THRESHOLD`).
- The "Practice Weak Concepts" CTA must only appear when `weakConcepts.length > 0` and must link to Adaptive Practice.

### Challenge Quiz — Progressive Generation

- Challenge mode starts with 5 questions (`INITIAL_CHALLENGE_QUIZ_COUNT`). Board Exam Mode generates a fixed count based on learner profile and is exempt from progressive generation.
- Users can request +5 more questions from the last question via `POST /challenge-quiz/sessions/{sessionId}/generate-more`, up to `MAX_CHALLENGE_QUIZ_QUESTIONS = 20` per session.
- The backend deduplicates generated questions by normalized text against all existing session questions; if fewer than 3 unique new questions survive, it returns `NOT_ENOUGH_NEW_QUESTIONS` (HTTP 409). The frontend must treat this as a soft end-of-questions state (`noMoreQuestions = true`), not an error.
- Score is based on **answered questions** (`selectedChoices.size()`), not the total question count in the session. This allows users to finish early without penalizing unattempted questions.
- Action bar at the last question (Challenge mode only): show `+5 Questions` / `Adding...` when under max and `noMoreQuestions` is false; always show `Complete Quiz` to submit.
- Board Exam Mode retains its existing submit label and flow unchanged.

### Challenge Quiz — Leave Guard Stability

- `onBeforeRouteLeave` and `onConfirmLeave` callbacks passed to `useQuizSessionGuard` must be stable references (wrapped in `useCallback`).
- The timer fires every second and causes a re-render on every tick. If these callbacks are inline arrow functions, `useQuizSessionGuard` recreates `LeaveQuizModal` as a new component function each tick — React unmounts and remounts the open modal every second.
- `onConfirmLeave` should read from `challengeSessionRef.current` instead of `challengeSession` state to avoid listing the session as a dep while still seeing the latest value.

### Note Card Consistency Rule

- Library, Public Library, Public Profile, and public subject listing pages should reuse the shared note-card layout.
- Note cards must use a shared layout and component across note-list pages; Public Library may add subtle discovery metadata such as views and copies, but the base card structure must remain consistent.
- Note cards must use a shared layout and component across note-list pages; Public Library may add subtle discovery metadata such as views, copies, and likes, but the base card structure must remain consistent.
- Shared note-card content order is:
  - TOP ROW: Subject badge + Course/Program badge (neutral/gray) — above title
  - Title (with optional private-library visibility icon `Globe`/`Lock` trailing)
  - Study Pack Ready badge (green) — below title, only when applicable
  - Quality badges (High Quality, Well liked, Popular) — below title alongside stateBadge
  - `Note Preview`
  - `Summary Preview`
  - Tags
  - subtle discovery metrics row (`views`, `copies`, `likes`) when that surface has them
- `SharedNoteCard` props: `courseProgram` (neutral gray badge above title), `stateBadge` (Study Pack Ready, rendered below title), `metadataBadges` (quality badges)
- The "New" badge has been removed; quality badges are High Quality, Well liked, and Popular only
- `Note Preview` comes from note content and `Summary Preview` comes from generated Study Pack summary.
- `Note Preview` should read as the primary preview and `Summary Preview` should stay secondary.
- If no generated summary exists, show `No summary available yet.`
- Use clamped preview text so card heights stay consistent across listing grids.
- Do not render `Public` / `Private` as a large badge on note cards; use a subtle icon instead when the visibility distinction matters.

### Page Responsibility Rule

| Page | Governing question |
|---|---|
| Dashboard | What should I do now? |
| Review Sets (the profile-aware Collections workspace) | What material have I organized into a study journey? |
| Library | What notes do I already have? |
| Explore | What material exists that I don't have yet? |
| Progress | How is my learning progressing? |
| Companion | What guidance applies to this curated journey? |
| Public Profile | What learning work do I share publicly? |
| Profile | Who am I as a learner? |
| Settings | How should NoteLib behave for me? |

**Locked doctrine (2026-07-30):**

- `/explore` is the single owner of content discovery. No other authenticated page may render an inline discovery catalog, adopt-picker, or public-note browse grid.
- Other pages may point at Explore with a link or a single pointer card; they may not do Explore's job.
- **v0.67.0 amendment (ratified 2026-08-15):** Dashboard may render one named, exact course/program-matched public-plan recommendation with coverage and the existing adoption action. It must never render a plan grid, list, filters, paging, or a second browse surface; Explore still owns browse.
- A matched-plan recommendation must resolve to an actual, unadopted published plan before linking. No course/program, no exact match, prior adoption, or lookup failure must produce a link to an empty result; Dashboard may use its existing Explore pointer fallback, while post-mastery remains silent.
- A bounded teaser is not discovery when it has a fixed small item count, no filters/paging/sort, no adopt/copy action, and one see-all link.
- `/public/library` and `/collections/published` remain canonical, separately-addressable routes for deep links, SEO, and anonymous access. This is a navigation-level claim, not a route deletion.
- `/onboarding` is exempt because it is a temporally scoped first-run wizard, not a persistent navigation page.
- Treat this as a deliberate, locked product rule rather than an informal convention.

**Amendment — RATIFIED 2026-08-17 (owner), after `v0.84.0` cleared all four of its stated blockers.** It was dated 2026-07-31 and pending Stage 0; that gate is now closed: `/explore` has real anonymous rendering, canonical metadata and structured data, and the `[CHECKPOINT — due 2026-09-13]` sequencing question was resolved (dissolved 2026-08-17 — the read it protected had already been degraded by `v0.78.0`). The amendment text asked the owner to confirm its narrowing reading *explicitly rather than have it asserted silently*; that confirmation was given 2026-08-17. **⚠️ Ratification removes the DOCTRINE block on Stage 3 and nothing else — Stage 3 still cannot ship**, because its evidence bar is unmet and currently unmeasurable: `/explore` was submitted to the sitemap in `v0.84.0` and has not been indexed, and no SEO measurement mechanism exists (Search Console is unassigned). Redirecting a page that ranks to a page with no ranking history would discard the traffic, not transfer it. Original text follows. a future direction exists to name the **Discovery System** as the product-architecture concept this table already implements — Explore is its primary interface; Public Library, Official Review Sets, and Exam Hubs are its sources and content surfaces. "Explore Owns Discovery" (locked above) is that doctrine's authenticated-navigation scope; this amendment extends the same doctrine toward eventual anonymous access, it does not replace it. Under this framing, `/explore` may eventually absorb `/public/library`'s *list-page* traffic only, once `/explore` itself gains real anonymous rendering, canonical metadata, and structured data. This narrows — it does not reverse — both the "must not replace, redirect, or redefine" language in `### Explore Navigation Rule` below and the "navigation-level claim, not a route deletion" language above: both continue to mean subject-listing pages (`/public/library/{subject}`) and note-detail pages (`/public/library/{subject}/{slug}`) are never redirected, full stop; only the bare list page is a legitimate future redirect target, and only once this amendment and a concrete SEO-parity evidence bar both clear. This also revisits, but does not resolve by itself, the owner's own earlier "Public Library is not absorbed or removed" direction recorded in `ROADMAP.md`'s Review-Set-Centric Navigation section — under this framing that direction stays true (Public Library remains a Discovery System source and route family; only its navigation primacy changes), so this reads as a narrowing of that direction, not a reversal needing separate sign-off, but the owner should confirm that reading explicitly rather than have it asserted silently. Blocked on Explore gaining real anonymous rendering, canonical metadata, structured data, and a resolved sequencing decision against this release's own `[CHECKPOINT — due 2026-09-13]`. Tracked in `ROADMAP.md`'s Backlog Index as "Discovery System — Public Front Door."

### Companion Guidance Doctrine

Ratified 2026-07-31 (Company Redefinition Phase 4, considered and narrowed 2026-07-29 — see `ROADMAP.md`'s Backlog Index "Companion Guidance Doctrine" row for the full pressure-test history). "Companion" today names three structurally different things — admin-authored static content, learner-reactive derived guidance (i.e. "Coach"), and the LLM chat (Ask Companion) — and a literal system-merge of them was considered and rejected: it would remove the vocabulary that keeps them safely apart (a learner would see "Companion" guidance on Dashboard, then be told it's unavailable on a Review Set with no admin-authored content).

**Authoring doctrine (docs/copy only — applies to new guidance surfaces going forward):**

- **One learning responsibility per feature.** Each guidance surface answers exactly one governing question (see the Page Responsibility Rule table above). Do not let a guidance feature quietly answer a second surface's question.
- **One question per surface.** A given page should not present two independently-reasoned "what should I do next" answers competing for the same moment of attention.
- **De-duplication rule.** Before adding a new "what's next" resolver, check whether an existing one already covers the same signal on the same surface; extend it rather than adding a parallel resolver.

**Explicitly not done by this doctrine:** no rename of "Companion," "Ask Companion," or "Coach"; no new user-facing brand; no backend merge. `Feature.ASK_COMPANION`, the `ask_companion_sessions` table, and `AnalyticsEventType.ASK_COMPANION_*` are unchanged. The 8 existing, independently-justified "what's next" resolvers across Dashboard/Collection detail/Progress (e.g. Dashboard/collection pacing staying uncoupled per `docs/features/dashboard.md:95`) are not merged by this doctrine — a full audit-and-merge pass ("Phase 1" of the phased plan in `ROADMAP.md`) stays gated on the still-open Primary-Review-Set-vs-Study/Exam-Focus philosophy question, unresolved as of this doctrine's adoption.

### Auth Redirect Rule

- If a session expires while the user is inside authenticated app pages, login should return them to that interrupted page through the explicit `redirect` query.
- If a logged-out user tries to open a protected route, login should return them to that requested protected page through the explicit `redirect` query.
- Manual login from public pages should land on `Dashboard`.
- Manual sign-out must clear any remembered protected return path and must not reuse a stale `redirect` query on the next login.
- After manual sign-out, the next successful login should land on `Dashboard` unless verification or onboarding gating applies.
- Do not send users back to public marketing or discovery pages automatically after login unless a protected-route redirect explicitly requires it.

### Social Login Rule

- Google OAuth is an alternative sign-in method, not a replacement for email/password.
- Verify Google identity tokens on the backend; never trust frontend-only Google profile data.
- Only auto-link by email when Google reports `email_verified=true`.
- Store provider identity in `user_auth_providers`; do not store provider IDs directly on `users`.
- Existing email/password users with a verified matching Google email must be linked, not duplicated.
- Do not add Apple/Facebook/GitHub or unlink/provider-management UI unless explicitly requested.

### Auth Messaging Rule

- `Your session has expired. Please log in again.` must only appear when login is opened with `reason=session_expired`.
- Manual logout must not reuse the session-expired message; it may show a neutral `You have been logged out.` message instead.
- Manual logout intent must suppress late `401` redirects from in-flight protected requests so logout messaging stays neutral.
- Protected-route access while logged out should use neutral login messaging such as `Please log in to continue.`
- Login-page messaging must be driven by the current auth-route query state, not sticky component state from a previous redirect reason.

### Profile Page Responsibility Rule

- `/profile` is a private identity settings surface, not a public-page controls surface.
- Keep Public Profile sharing and visibility controls on `/public/profile/{userId}` only.
- `View Public Page` on `/profile` is navigation only and should not be grouped with save actions.

### Public Profile Owner Controls Rule

- Public Profile owner controls belong on `/public/profile/{userId}`, not on `/profile`.
- Only the profile owner may see `Edit Profile` and the Public Profile visibility toggle.
- Non-owners may see a share action on Public Profile, but they must not see owner-only editing or privacy controls.
- Public Profile note cards stay action-free for both owners and non-owners.
- If a public profile is turned off, non-owners should see `This profile is private.`

### UI Consistency Rule

- Public Profile should reuse the Note Detail control pattern for visibility and share actions.
- Visibility controls should appear as badge/dropdown controls near the header identity cluster, not as detached toggle buttons.
- Share actions should sit in the lower action row of the header card rather than in the top metadata cluster.

### Back Navigation Rule

- All back navigation uses the `BackLink` component (`components/ui/back-link.tsx`): renders `← {label}` with `ArrowLeft` icon, blue link color (`text-blue-600 dark:text-blue-400`), underlines on hover — same style as "View Full Notes →". Not a button.
- Back links appear on sub-pages only. Main pages (Dashboard, Library, profile-aware Collections, Explore, Progress, Public Library, My Profile, Settings) must NOT have a back link.
- Back navigation always uses explicit routing (`href` prop on `BackLink`) — never `router.back()`.
- Back link label is the destination page name only — do NOT use "Back to X" or "Back" alone.
- My Profile (owner's own public profile) is a main page — no back link.
- Non-owner viewing another user's public profile: `<BackLink href="/public/library" label="Public Library" />`.
- Inline card action buttons (quiz error/limit states etc.) should use short destination labels (`Note`, `Library`) — not "Back to Note" or "Back to Library".
- Back link is positioned above the page header card, left-aligned.
- **Context-aware back navigation**: Profile Settings (`/profile`) should render `← Dashboard` (href `/dashboard`) when reached via `?from=dashboard`, and `← Profile` (href public profile path) in all other cases. Pass `?from=dashboard` in the navigation URL to trigger this behavior.

### Note Ownership Rule

- Generated outputs (summary/key concepts/quizzes), Quick Review, Challenge Quiz, Adaptive Practice, and performance are scoped to `noteId`.
- If legacy payload fields still expose `studyPackId`, treat them as compatibility fields, not primary ownership.

### Profile Type UX Rule

- Do not create separate entities or table flows per profile type.
- Profile Type only changes UI, workflow emphasis, labels, recommendations, and default presentation.
- Shared engine remains:
  - `Note -> Study Pack -> Quiz -> Activity -> Weak Concepts`
- `STUDENT` emphasizes review continuity.
- `BOARD_EXAM` emphasizes quiz practice and weak-area drilling.
- `TEACHER` emphasizes quiz creation from the same note pipeline.

### Learning Profile Metadata Rule

> **Superseded in part by `docs/architecture/ADR-001-canonical-knowledge-architecture.md` (Accepted 2026-08-03) — read that ADR before changing anything in this block.** Two rules below no longer describe the system: `learnerLevel` is **not** exclusively a `User` field (`v0.69.0` shipped `notes.learner_level`, the note's authored depth, which outranks the reader's), and `notes.courseProgram` is **not** the generation source of truth (`notes.domain_context` is, with `courseProgram` as its fallback). The metadata hierarchy is now four independent axes, not one shelf. Resolution belongs to `StudyPackGenerationContextResolver.effectiveAuthoringDomain()` and `.effectiveCurriculumLevel()`; no service reads these fields directly. **Do not restore either retired rule** — the single-axis model they describe is the defect the ADR exists to fix.

- `learnerLevel` lives on `User`, not on Note or a separate learner-profile table.
- `User.courseProgram` remains the profile-level default for new notes.
- Notes may also store an optional note-level `courseProgram`, defaulted from the user's profile and editable per note.
- For Study Pack generation the authoring domain resolves `notes.domainContext` -> **exactly one joined catalog program (`note_course_program`)** -> `notes.courseProgram` -> `users.courseProgram`, and the curriculum level resolves `notes.learnerLevel` -> `users.learnerLevel` -> `COLLEGE`. The level chain never reads `courseProgram`. **The join step is only consulted at exactly one row** (`v0.71.0`): a note applicable to several programs has no single authoring domain, so the chain falls through to the strings rather than picking one arbitrarily. It is not a legacy branch — it is how every learner-authored note resolves its domain. Program *lists* never reach a prompt.
- **`notes.courseProgram` is unreadable — "shadowed" — under an exact condition, and any code gating on it must use that condition (`v0.71.1`, ratified in `ADR-001`):**

  ```
  shadowed = (joinRowCount >= 1) && (joinRowCount == 1 || domainContext != null)
  ```

  Discovery ignores the string whenever *any* join row exists (every library and public read is `EXISTS(join) OR (NOT EXISTS(join) AND legacy matches)`), and generation reads it only through `effectiveAuthoringDomain` — which returns the Domain Context label when set, and otherwise calls `resolveCourseProgram`, which returns the joined catalog name at exactly one row and falls through to the string at 0 or 2+. A copy of a curated note is shadowed on both paths by two independent mechanisms: the inherited Domain Context at 2+ programs, the joined name at exactly one. **Do not simplify this to `joinRowCount > 0`.** That form depends on the invariant *2+ rows implies a non-null Domain Context* holding on every write path, present and future; no code enforces it, and the predicate above is correct whether or not it holds.
- **Never make the personal Course / Program field required on a shadowed note.** Requiring a value nothing can read is a live defect, not a validation. `NoteService.resolveRequestedCourseProgram` falls back to the owner's profile program and throws only when both are null; that residual throw is reachable from the inline editor **and** from `applySuggestions`, which sends `courseProgramText` from stored state through the same `PUT /notes/{id}`. Both close with the predicate above.
- **A learner may be shown their note's Applicable Programs read-only, with provenance; a learner may never author them.** `ADR-001`'s *"No learner-facing Applicable Programs UI"* governs authoring **controls**, not provenance **display** (clarified 2026-08-10). This binds **every** surface that renders a learner's programs — Note Detail today, and library cards the moment the private list projection stops returning an empty `applicablePrograms` array.
- **Applicable Program write and read authorization are deliberately asymmetric (`v0.71.1`): `findAuthorizedNote` is owner AND curator (`ADMIN` or `TEACHER`); `findReadableNote` is admin OR owner. Do not make them symmetric.** `ADMIN` does not grant curation over another user's note, and ownership alone never gives learners authoring authority.
- **Inline catalog creation is an explicit Admin curator action and must never be implemented by enabling `allowCustom` on the Applicable Programs picker.** Keep `allowCustom={false}`; show near matches first, require confirmation, and create through the Admin-only catalog endpoint before selecting the returned row. Teachers may curate applicability from existing catalog values but do not mutate the shared catalog.
- Metadata hierarchy should stay:
  - `courseProgram` -> top-level track/domain
  - `subject` -> reusable academic topic
  - `tags` -> fine-grained keywords
- `learnerLevel` is required during onboarding but remains nullable in storage for pre-existing users.
- `courseProgram` is required during onboarding and later Learning Profile saves, but remains nullable in storage for pre-existing users until they update it.
- Backend generation context carries `learnerLevel` (the reader's), `courseProgram`, `subject`, `tags`, `domainContext`, and `noteLearnerLevel`. Static note and Study Pack content is calibrated by the **effective Domain Context plus the note's authored level** — never by the reader's level. Quizzes and exams take both as the curriculum floor: a lower reader level may soften wording and scaffolding but must never lower curriculum, terminology, or difficulty. Question pools and the Challenge bank key on the effective curriculum level, not the reader's (`v0.70.0`).

### LLM Context Builder Rule

> **Superseded in part by ADR-001 (Accepted 2026-08-03).** The content-context builder no longer "omits learner level and uses course/program": since `v0.69.0` it calibrates from the effective Domain Context plus the note's authored level. What the ADR did **not** retire is the clause that matters most — shared/static content must never be calibrated from the **reader's** level.

- All LLM calls must resolve context through `StudyPackGenerationContextResolver` (backend service).
- Static note and Study Pack content must call the content-context builder, which calibrates depth, vocabulary, terminology, and examples from the effective Domain Context plus the note's authored level. It must never calibrate from the reader's learner level.
- Quiz and exam prompts must call `buildLearnerContextBlock()`, which carries the effective domain and curriculum floor plus the reader's level for scaffolding only.
- Each `DomainContext` enum value declares whether its catalog is quantitative. Treat that declaration as an additive positive signal for computation guidance; keep the existing keyword scan for quantitative subjects, tags, concepts, summaries, and the free-text course/program fallback when Domain Context is null. Do not infer a declared Domain Context's quantitative treatment from its display label, and do not map free-text program names to enum declarations.
- Never inline raw learner-level or course/program formatting in individual prompt builders.
- Learner level defaults to `COLLEGE` for quiz/exam prompts when the user has no saved `learnerLevel`; note and Study Pack content generation must also work when context learner level is null.
- Course/program is omitted from the context block when the user has no saved `courseProgram`.
- In the normal note flow, AI-generated `title`, `subject`, and `tags` must not be persisted before explicit user confirmation.
- When merging AI tags with existing note tags, always deduplicate case-insensitively after trimming whitespace.

### Quiz Generation Rule

- Quick Review comes from the Study Pack quiz generated with static content and should stay lightweight, fast, and course/program-leveled. Per-taker quiz/exam generation remains learner-level aware.
- Challenge Quiz and Adaptive Practice use separate LLM generation flows and must receive learner-level context, defaulting to `COLLEGE` when the user has no saved learner level.
- Local quiz UI development may use `QUIZ_GENERATION_MODE=mock` to stub Challenge Quiz, Adaptive Practice, and Board Exam generation without changing Study Pack generation or the default production LLM path.
- Optional local loading-state testing may add `QUIZ_GENERATION_MOCK_DELAY_MS`, but the default quiz-generation mode must remain real unless explicitly overridden.
- Quick Review must not use the Challenge/Adaptive LLM-generation hard lock or full-screen generation overlay because it does not run an LLM at quiz start.
- Challenge Quiz and Adaptive Practice must reserve a `GENERATING` session before calling the LLM, then transition to `IN_PROGRESS` when the quiz payload is ready or `FAILED` when generation fails.
- Challenge Quiz and Adaptive Practice start flows must be idempotent: return existing `GENERATING` sessions without another LLM call, return existing `IN_PROGRESS` quiz payloads without another LLM call, and allow retry only after `FAILED`.
- While Challenge Quiz or Adaptive Practice generation is active, the UI must disable start controls, difficulty/options controls, app links, sidebar/header navigation, and browser back/refresh through the shared generation lock and native `beforeunload` warning.
- Challenge Quiz and Adaptive Practice reload recovery must check existing session state first: `GENERATING` continues the loading/poll state, `IN_PROGRESS` resumes the quiz, and `FAILED` shows retry.
- Generated quiz JSON contracts must stay strict:
  - exactly 4 choices
  - `answer` must be `A` / `B` / `C` / `D`
  - `explanation` is required
  - `concept` is required
- `MULTI_SELECT` is a plan-agnostic quiz format for Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, and Teacher Quiz only; do not add it to Board Exam prompts or Board Exam UX.
- Multi-select questions must keep exactly 4 choices, use `correctIndices` with 2-3 correct zero-based indexes, and score all-or-nothing. Keep `correctIndex` populated with `correctIndices[0]` as a legacy fallback.
- Quiz session state must store multi-select answers under `selectedMultiChoices` through `QuizSessionStateUtils`; do not manipulate the session JSON directly in service code.
- `MATCHING` is a plan-agnostic quiz format for Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, and Teacher Quiz only; do not add it to Board Exam prompts or Board Exam UX.
- Matching groups use the shared `questionGroup` field, must be 2-4 consecutive items with identical 4-choice arrays, and each item remains single-correct with a distinct `correctIndex`.
- Matching answers use the existing `selectedChoices` session key; do not add a separate matching-answer JSONB key.
- Raw LLM quiz output may use answer letters, but canonical stored/shared quiz data must normalize to:
  - `question`
  - `choices`
  - `correctIndex`
  - `explanation`
  - `concept`
- `A` / `B` / `C` / `D` are UI-only labels derived from displayed order and must not be embedded into canonical choice strings.
- Backend quiz normalization must strip leading hardcoded choice prefixes such as `A. `, `B) `, `c. `, and `D) ` from generated and legacy choice strings before validation/storage.
- Quiz sessions must persist selected canonical choice indexes, not display letters or prefixed choice text.
- Compatibility loaders may accept legacy answer text, `answerIndex`, or string-based selected choices, but runtime grading/rendering must normalize them back to canonical indexes before use.
- Runtime grading must compare canonical choice indexes or explicit correctness metadata, never displayed letters or post-shuffle display positions.
- Quantitative subjects should allow computation and problem-solving questions when the note context supports them.
- Computation explanations should show short step-by-step solution flow rather than a one-line answer.

## UI Terminology (Use Consistently)

- `Dashboard`
- `Library`
- `Public Library`
- `Note Detail` (unified Note + Study Pack view)
- `New Note`
- `Generate Study Pack`
- `Make a Copy`
- `Copy to Library`
- `Make Public`
- `Make Private`

Avoid introducing older terms such as `Study Library` or regenerate/overwrite flows.

## Navigation Structure

Keep app shell grouping:

- Main:
  - Dashboard
  - Profile-aware review-workspace label resolved through `getCollectionLabels().navLabel`
  - Library
  - Explore
  - Progress
- Account:
  - Profile
  - Settings
  - Admin (admins only)

## UI Interaction Guardrails

- Keep note cards consistent across Dashboard, Library, and Public Library:
  - entire card click opens note detail
  - note cards stay action-free and rely on Note Detail for management actions
- Public Profile note cards should follow the same whole-card click pattern as Library and Public Library.
- `Library` should expose a direct `Create Note` entry in the header and empty state so users are not forced through `Dashboard` to start a note.
- Note Editor actions:
  - keep `Generate` as the primary CTA and `Save` as the secondary CTA
  - desktop should show actions at the top and bottom of long note forms
  - mobile should keep a floating primary generate button visible while scrolling
  - `/notes/new` stays in create mode with `Save` + `Generate`
  - `/notes/{id}/edit` for Draft notes stays in edit mode with `Save Changes`, `Cancel`, and `Generate`
  - `/notes/{id}/edit` for Study Pack Ready notes shows `Save Changes`, `Cancel`, and `Make a Copy`. **Note that neither the backend nor this route enforces the content lock** — `NoteService.update` has no status guard and the editor renders an unlocked textarea. The lock is an entry-point convention: Note Detail's `Edit` action routes ready notes to the inline panel instead. The route stays reachable by direct URL deliberately (it is the escape hatch that made ADR-001's R4 verification runnable); do not add a guard without an explicit decision. See `docs/features/notes.md`.
  - edit routes must render `Edit Note` copy, not create-note copy
  - note editor metadata fields are `title`, `courseProgram`, `subject`, `tags`, `content`, and — for Teacher/Admin authors — `domainContext` and `learnerLevel`; `targetProfileType` is not an API or UI field
  - subject suggestions must come from persisted note subjects and still allow custom typed values
  - tags remain optional and should include helper guidance rather than hard validation pressure
- Generate button wording may vary by `profileType` (`Generate`, `Practice`, `Create Quiz`) but must still hit the same Study Pack generation flow.
- Keep primary button labels short; longer outcome explanations belong in helper text below the generate button.
- After generation, default tab should vary by `profileType`:
  - `STUDENT` -> `tab=summary`
  - `BOARD_EXAM` -> `tab=quiz`
  - `TEACHER` -> `tab=quiz`
- Teacher dashboard should prioritize quiz creation and material upload, but still use the shared note-first pipeline.
- Use one shared modal component for confirmations/dialogs (`AppModal`), including delete/share/visibility/leave-flow prompts.
- Do not use browser-native `window.confirm` or `alert` for product dialogs.
- Note Detail edit rules:
  - `DRAFT`: Edit routes to full editor (content + OCR)
  - `STUDY_PACK_READY`: Edit stays on Note Detail. Every owner may edit title/courseProgram/subject/tags; **Teacher/Admin authors may additionally edit Domain Context and Note Learner Level** (`v0.70.0`, narrowed in `v0.83.0`, gated by `isTeacherSelectableNoteTarget` — the shared curator gate). Correcting either authoring axis shapes *future* generation only and never touches the existing Study Pack. Note **content** stays locked; that is the lock this rule protects.
  - Because `PUT /notes/{id}` is a full replace, any surface that hides a field must send the note's stored value back untouched rather than an empty draft. Hiding a field must never null it.
  - While inline metadata edit is active, hide/disable share/visibility/learning actions.
- Share flow for private notes:
  - click Share -> show private-note modal
  - confirm -> make note public
  - then open share-link modal with copy action

## Verification and Access Gating

- Users can sign up/log in before email verification.
- Unverified users must not generate Study Packs.
- Unverified users must not use OCR upload.
- Verification-gated API responses should use structured `403` with:
  - `code=EMAIL_VERIFICATION_REQUIRED`
  - `action=RESEND_VERIFICATION`
- Frontend should present a friendly message for OCR gating:
  - `Verify your email before using OCR upload.`

## OCR Flow (Create/Edit Note)

OCR is optional and attached to Note authoring (`New Note` / edit note).
Create/Edit Note uses one unified import pipeline for images and supported files.

Required behavior:

- User uploads note image.
- OCR extracts text.
- Extracted text is inserted/merged into Note `content`.
- User reviews and edits OCR text directly in the main `Content` field before save/generate.
- Do not add a second OCR-only review textarea in Create/Edit Note.
- If OCR confidence is low, show an inline warning near `Content` instead of a separate confirmation editor.
- OCR upload does not auto-save and does not auto-generate.
- Uploaded images are not stored permanently.
- Note import/extraction is backend-owned; frontend should not be the source of truth for OCR/PDF/DOCX parsing.
- OCR usage must be protected by backend-configured billing-period limits plus per-minute rate limiting.
- If OCR quota is exhausted, return:
  - `You have reached your OCR limit for now. Please try again later or upgrade to Plus or Pro.`
- If OCR request rate limit is exceeded, return:
  - `Too many requests. Please wait a moment and try again.`

## File Import Flow (Create/Edit Note)

File import is part of Note authoring and must populate the main `Content` field before any save or generation action.

Required behavior:

- Support `.txt`, `.pdf`, and `.docx` import in Create/Edit Note.
- Use the same unified upload entry point as image OCR.
- Imported text is inserted/merged into Note `content`.
- Users review and edit imported text directly in the main `Content` field.
- File import does not auto-save and does not auto-generate.
- Text-based PDFs are supported in this flow.
- If a PDF has no embedded text, use OCR fallback before treating it as unreadable.
- If a PDF has no extractable text, show a friendly scanned-PDF message and direct users to image OCR instead.
- File imports must enforce backend-configured size/type/text-length limits before content reaches Note `content`.
- If extracted import text exceeds the configured maximum, return:
  - `This file is too large to process. Please upload a smaller file.`

## Bulk Material Import Rule

- `POST /notes/import-batch` is the deliberate auto-save exception to the single-file import flow.
- Bulk import creates one owned `DRAFT` note per successfully extracted file and must never auto-generate a Study Pack, set `GENERATING`, call an LLM, or add a new quota category.
- Bulk import must reuse the existing per-file extraction pipeline and its verification, file-size, page/text, OCR usage, and OCR rate-limit enforcement.
- Bulk import orchestration must not run inside a batch-wide transaction; one file failure must be recorded in the response and must not roll back notes already created from other files.
- Bulk import is universal and profile-agnostic; do not add `ProfileType` branching or teacher-only gates to the backend endpoint.



## User Access Model

NoteLib uses a hybrid verification model.

Unverified users CAN:
- Create notes
- Edit draft notes
- Copy notes
- Browse Public Library

Unverified users CANNOT:
- Generate Study Pack
- Use OCR
- Take Challenge Quiz
- Use Adaptive Practice
- Make notes public
- Purchase a paid plan
- Use any LLM-powered feature

Verified users:
- Have full access based on plan (Free, Plus, or Pro)

This gating must be enforced both in frontend and backend.

## User State Routing

User states:

1. ANONYMOUS
2. UNVERIFIED
3. VERIFIED

Routing rules:

ANONYMOUS:
- Landing
- Public Library

UNVERIFIED:
- App shell
- Show verification banner
- Allow note creation and copying only

VERIFIED:
- App shell
- Dashboard as primary landing
- Full app access based on plan

Auth routing rules:

- After successful login, the frontend must navigate with `router.replace(...)` to the resolved authenticated home route.
- Do not rely on app-shell visibility to imply navigation away from `/auth` or `/login`.
- Auth pages (`/auth`, `/login`, `/signup`) must redirect authenticated users immediately.
- The authenticated app shell must not render on auth routes.
- Expired-session recovery must clear stale auth state before redirecting to login so re-login behaves like a fresh successful auth flow.

## MVP Scope (Do Not Expand Without Request)

In scope:

- Note creation/editing
- Study Pack generation from notes
- OCR-assisted note input
- Library/Public Library flows
- Quick Review, Challenge Quiz, Adaptive Practice
- Share/copy flows
- Plan and billing usage display

Out of scope unless requested:

- flashcards/spaced repetition
- heavy analytics dashboards
- teacher/classroom tooling
- gamification-heavy systems

## Frontend Conventions (`/frontend`)

Stack:

- Next.js App Router
- TypeScript
- Tailwind CSS
- shadcn/ui
- lucide-react

Rules:

1. Keep pages thin; put logic in `lib/`, hooks, and focused components.
2. Route backend calls through `frontend/lib/api.ts`.
3. Always implement loading and error states.
4. Use theme tokens (`bg-background`, `text-foreground`, etc.).
5. Keep Note Detail unified; do not split Note vs Study Pack detail pages again.
6. **Taxonomy / enumerated fields must use a shared combobox/dropdown, never a freetext `<input>`.** Course/program, learner level, and subject are matched by normalization (e.g. a study plan's `courseProgram` is normalize-matched against the learner's profile value to surface it on the Dashboard); a freetext value that matches no learner silently never appears. Reach for `components/metadata/course-program-combobox.tsx`, `components/notes/subject-combobox.tsx`, or `components/ui/suggestion-combobox.tsx` first. This drift has recurred (Bulk Generate, then the Adoptable Study Plans publish card).

### Sonar / Code Smell Rules (Frontend)

- **Use `toLocaleLowerCase("en")` for subject normalization**: When normalizing note subject strings for comparison (e.g. `normalizeSubjectForMatch`), always use `.toLocaleLowerCase("en")` instead of `.toLowerCase()`. `toLowerCase()` is locale-dependent and can produce inconsistent results across environments. Apply this to every function that lowercases a user-supplied subject or tag string for comparison.
- **Use `globalThis` instead of `window`**: Sonar flags direct `window` access. Replace `window.addEventListener`, `window.removeEventListener`, `window.location`, `window.history`, and any other `window.*` global with the `globalThis.*` equivalent. Apply this fix whenever modifying a file that contains `window.` access outside of type guards.
- **Unknown TypeScript property**: Sonar flags accessing a property that is not in the inferred type of an object. Fix by adding the missing property to the TypeScript interface or type, using `Record<string, T>` when the object is keyed by dynamic strings, or using a type guard. Do not use `as any` to suppress the warning — resolve the underlying type gap. When Sonar reports "Unknown property 'text-sm'" or similar, the property is likely a CSS class key on a plain object; switch to a typed `Record<string, string>` or restructure the object so TypeScript knows the allowed keys.
- **Escape `>` in JSX text content**: Sonar requires bare `>` characters in visible JSX text to be escaped. Use the HTML entity `&gt;` or the JSX expression `{'>'}` instead. This applies only to `>` appearing as readable text between JSX tags, not to JSX syntax angle brackets (`<Component />`) or ternary expressions.

## Backend Conventions (`/backend`)

Rules:

1. Keep controllers thin; business logic in services.
2. Keep generation orchestration in Study Pack service flow:
   validate -> OCR (if image) -> normalize -> LLM -> validate output -> persist.
3. Enforce server-side limits for text/image inputs and quotas.
4. Do not log raw images or full OCR text.
5. Persist only validated generated output.
6. Keep ownership checks note-centric.

## Cost and Quotas

- Free: 10 Study Packs/month
- Free Challenge Quiz: 5/month
- Free OCR: backend-configured monthly quota
- Plus: 50 Study Packs/month
- Plus Challenge Quiz: 25/month
- Plus OCR: backend-configured monthly quota
- Pro: 100 Study Packs/month
- Pro Challenge Quiz: 50/month
- Pro Adaptive Practice: 30/month
- Pro OCR: backend-configured monthly quota
- Adaptive Practice is Pro-only and still quota-limited.
- Weak concepts remain visible to Free users.
- File upload is available on Free, Plus, and Pro.
- Study Pack, Challenge Quiz, and Adaptive Practice quotas are separate from each other.
- OCR usage has its own backend-configured monthly quota by plan.
- Frontend plan limits and feature availability must come from `GET /api/me/plan`, not hardcoded values.
- Settings usage UI should not show OCR counters; OCR remains backend-tracked and enforced.
- OCR limit UX in note import should use a modal:
  - Free: explain OCR is limited and offer `Upgrade`
  - Plus / Pro: explain reset happens on the next billing date
- Expensive OCR and AI generation endpoints must also enforce backend request-rate limits and return `429` with a friendly retry message.

## Billing Provider (Current)

- Active billing provider is `XENDIT`.
- Paid-plan checkout is currently a hosted Xendit invoice flow, not a recurring subscription flow.
- The current manual-renewal billing model is: Monthly checkout grants `30` days and Yearly checkout grants `365` days of paid access for the selected plan.
- Regional pricing is resolved from `CF-IPCountry` and mapped into pricing regions.
- Region pricing config contains localized currency/amounts plus optional intro pricing metadata used for display and eligibility.
- Voucher/promotion rules decide whether intro pricing is shown, but checkout itself stays on the current hosted Xendit invoice flow.
- Intro/first-time subscriber discounts must flow through voucher eligibility and voucher redemption records.
- Paid-plan activation is controlled by webhook-confirmed invoice outcomes only.
- Webhook-confirmed payments must create or extend `subscriptions`; they must not update plan state on `users`.
- If an upgrade starts from Settings/Billing, billing success should send the user to Dashboard instead of back to Settings.
- Success/failure redirect pages may help users return to their previous page, but those redirects never activate a paid plan.
- Xendit webhook statuses currently handled are:
  - `PAID`
  - `FAILED`
  - `EXPIRED`
- Do not create/update webhook registrations dynamically in app code.
- Payment endpoints are:
  - `POST /api/payments/create`
  - `POST /api/webhooks/xendit`
- Webhook processing safety:
  - store provider webhook events in `webhook_events` with unique `(provider, event_id)`
  - duplicate events must return success without reprocessing
  - keep provider transaction inserts idempotent via provider reference IDs
  - reject external or protocol-relative checkout `returnUrl` values
- Billing lifecycle safety jobs:
  - `SubscriptionExpiryJob` (daily): expire overdue active paid subscriptions and downgrade to Free
  - `BillingUsageResetJob` (daily): ensure usage rows exist for the current billing period window

## Dashboard and Library Guardrails

- Dashboard is guidance-first and non-destructive.
- Keep delete actions out of Dashboard.
- Dashboard should personalize section order, CTA emphasis, and labels by `profileType` while reusing the same shared note, quiz, activity, and usage data.
- `STUDENT` dashboard should prioritize `Continue Studying`, weak concepts, recent notes, and quick review.
- `Continue Studying` must show the current note title prominently and include subject plus course/program when available so users can recognize what they are resuming.
- Dashboard continue-study payloads must carry `noteId`, `noteTitle`, `subject`, optional `courseProgram`, and backend-owned `resumeType` in a single API response; do not add follow-up frontend fetches just to label the card.
- `Continue Studying` resume labels must reflect the backend `resumeType` (`Quick Review`, `Challenge Quiz`, `Adaptive Practice`) instead of hardcoding Quick Review copy.
- `BOARD_EXAM` dashboard should prioritize challenge-quiz practice, weak areas, adaptive practice, exam countdown, and weekly activity.
- `TEACHER` dashboard should prioritize quiz creation, material upload, recent materials, and recently generated quizzes.
- Dashboard variants must not introduce separate entities or profile-specific tables; personalization is presentation only.
- Teacher CTA routes should stay explicit:
  - `Create Quiz` -> `/notes/new?mode=quiz`
  - `Paste Material` -> `/notes/new?source=paste`
  - `Upload Material` -> `/notes/new?source=upload`
  - normal `Add Material` -> `/notes/new`
- Post-generation default note-detail view should use query-driven presentation on the unified note route:
  - normal note flow -> `tab=summary`
  - board-exam flow -> `tab=quiz`
  - teacher quiz-focused entry modes -> `tab=quiz`
- Dashboard statistics and weak-concept insights must be computed from stored quiz sessions and activity logs only, never by LLM calls.
- `Focus Areas` should surface top weak concepts for all users, but Adaptive Practice CTA remains Pro-gated through the shared soft paywall for Free and Plus users.
- Keep destructive actions (delete) in Note Detail/Library with explicit confirmation.

## Context Usage Rule
Always read and follow AGENTS.md, SPEC.md, and related feature docs before implementing any task.
Assume these files are the source of truth for architecture and UX decisions.

- docs/architecture for the architecture overview
- docs/features for the context of every feature
- docs/product for the spec and roadmap of the app
- docs/testing for the context of testing of every feature
- docs/ui for the ui design context

## Documentation Source of Truth

Primary docs:

- `README.md`
- `docs/product/SPEC.md`
- `docs/product/ROADMAP.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/architecture/DATA_MODEL.md`
- `docs/features/*`

If conflicts appear:

1. Follow current product docs under `docs/`.
2. Use `docs/legacy/` only for historical reference.

## Testing Rules

All new features must include unit tests.

When modifying existing behavior:
- Update existing tests if behavior changes
- Add new tests for new rules

### An absence assertion must be able to fail

**This has now shipped three times in this repo, twice as "proof" cited in a release record, so it is a rule rather than a review note.** A test asserting that something is *not* rendered is worthless unless it would fail when the thing *is* rendered. Two mechanical ways it goes wrong, both of which pass green forever:

- **Wrong signal.** Asserting the absence of an element that could never render in that scenario anyway. `expect(queryByLabelText("Add a course or program")).not.toBeInTheDocument()` was used to prove a learner sees no programs control — but that aria-label belongs to the *curator* control, which is gated off for a learner regardless. It proved nothing. The same trap catches a selector whose hook you just removed: once a `<label>` loses its `htmlFor`, `queryByLabelText` returns nothing whether or not the input rendered.
- **Wrong timing.** Asserting absence before the thing could have appeared. `await waitFor(() => expect(getMe).toHaveBeenCalled())` then `queryByText(...)` waits only for the *fetch to start*, not for the state update it causes to flush — so the assertion runs on an empty tree and passes whether or not the gate it tests exists.

**The rules:**

1. **Await the same signal the positive case awaits.** If the presence test uses `await screen.findByText(X)`, the absence test must reach the same settled state before querying — not a weaker proxy like "the API was called".
2. **Assert on something only the feature under test can produce** — an element id or role the *other* branch cannot also satisfy.
3. **Prove it can fail.** Temporarily invert the behaviour, run the test, confirm it goes red, restore. A one-minute check that is the only real evidence the assertion has teeth. Do this before citing a test as proof of anything in `RELEASES.md`.

The same standard applies to backend mocks: a repository stub left unstubbed returns an empty collection, so the code path you believe you covered is never entered and every existing assertion still passes. If a test is meant to exercise a join-first read, stub the join with data.

Critical business rules that must always have tests:
- Note state (Draft vs Study Pack)
- Copy note behavior and attribution
- Email verification gating
- Study Pack credit usage
- Public visibility rules
- Quiz session rules (only one in-progress session)
- OCR limits and verification gating

A feature is not complete unless:
- Code compiles
- Tests pass
- New behavior has test coverage

## Subject Generation Strategy

LLM-generated subjects must be reusable academic subject labels, with no topic suffix.

- Correct: `Biology`, `Physics`, `Mathematics`, `Computer Science`, `English`, `Filipino`, `Civil Engineering`, `Electrical Engineering`, `Nursing`, `Accountancy`, `Criminal Law`
- Incorrect: `Biology – Cell Division`, `Physics: Ohm's Law`, `Mathematics – Derivatives`
- Overly broad umbrella labels such as `Engineering`, `Medicine`, `Business`, and `Law` must be ignored/rejected safely when they come from AI metadata suggestions.
- Topic-level specificity belongs in tags and key concepts, not in subject

Backend enforcement (`SubjectSanitizer.stripSubtopicSuffix`):
- Any separator (" – " or ":") triggers stripping → only the left/domain part is kept
- `"Electrical Engineering – Ohm's Law"` → `"Electrical Engineering"`
- Broad single-word AI suggestions (`Engineering`, `Medicine`, `Law`, `Business`, `Education`) are ignored and must not fail Study Pack generation
- Empty/unusable result after stripping is ignored as missing subject metadata; Study Pack generation continues if core summary/key concept/quiz output is valid

## Study Pack Sanitization

`OpenAiLlmStudyPackService` validates and repairs LLM output before saving:

- **Subject**: max 6 words (`SubjectSanitizer`); invalid or overly broad AI subject suggestions are non-blocking and become no subject suggestion
- **Quiz concept**: max 4 words (`KeyConceptSanitizer.MAX_QUIZ_CONCEPT_WORDS`); filler prefixes stripped before truncation
- **Key concepts**: max 4 words each (`KeyConceptSanitizer.MAX_KEY_CONCEPT_WORDS`); repaired in-place, never block study pack creation due to word-count alone

Sanitizer classes live in `backend/.../util/SubjectSanitizer.java` and `KeyConceptSanitizer.java`.

## Public Library Discovery

Discovery mode layout order (no active filters):
1. Search toolbar with `Filter` and `Sort`
2. one-line `Subjects` rail with `All` and `+ More`
3. one-line `Popular Tags` rail with a dedicated `Browse all` action
4. 🔥 Featured Notes — top 3 eligible notes by quality + engagement
5. 📈 Most Popular — top 5 threshold-qualified notes by copies, then views, then likes (excludes Featured)
6. 🆕 Recently Added — top 5 by createdAt (excludes Featured + Popular)

Backend subject filtering: `GET /notes/public?subject=<value>` — case-insensitive, server-side.

Public Library ranking philosophy:
- Featured = quality + engagement
- Popular = social proof
- Recent = freshness
- Evaluation should stay lightweight: simple signals > complex social systems.

Ranking rules:
- Featured eligibility requires:
  - `visibility = PUBLIC`
  - `studyPackStatus = STUDY_PACK_READY`
  - meaningful summary preview
  - quiz/generated study content
  - non-empty note preview/content
- Featured score:
  - `viewCount + (copyCount * 3) + (likeCount * 2)`
- Featured tie-breakers:
  - `copyCount DESC`
  - `viewCount DESC`
  - `createdAt DESC`
- Likes:
  - authenticated users can toggle one like per public note
  - guests clicking like must see an auth modal instead of a silent failure
  - `Well liked` badge threshold is `likeCount >= 10`
- Popular threshold:
  - `copyCount >= 3` or `viewCount >= 20`
- Popular ordering:
  - `copyCount DESC`
  - `viewCount DESC`
  - `likeCount DESC`
  - `createdAt DESC`
- Recent ordering:
  - `createdAt DESC`
- Preserve the current clean discovery dedupe:
  - Popular excludes Featured
  - Recent excludes Featured and Popular

## UI / UX Responsiveness Guidelines

All UI implementations must be responsive and mobile-friendly by default.

### Requirements

- Components must work across:
  - desktop
  - tablet
  - mobile

- Avoid layout issues such as:
  - overflowing buttons or text
  - broken flex/grid layouts
  - elements exceeding container width

- Use responsive patterns:
  - flexible layouts (flex/grid with gap)
  - wrapping where necessary
  - stacked layouts on smaller screens

- Modal and card components must:
  - adapt to smaller widths
  - maintain readable spacing
  - prevent action button overflow

- Button labels must be concise to support smaller screens

### Principle

Design for mobile-first or mobile-safe behavior, even when implementing desktop UI.

UI should feel clean, usable, and visually stable across screen sizes.

## Product-First UI/UX Principles

These principles apply to all frontend work — landing page, demo, pricing, and in-product features.

### Clarity over feature density

- Show what matters to a student or board exam taker first.
- Do not stack features to make a page look impressive; a shorter, clearer page converts better.
- Visual hierarchy: content > actions > secondary info.
- Avoid competing CTAs on the same screen. One primary action per section.

### Align features with learning outcomes

Every visible feature must connect to a student's goal:

| Feature | Learning outcome |
|---|---|
| Study Pack | Understand and organize notes |
| Quizzes | Test retention and find gaps |
| Adaptive Practice | Reinforce weak concepts |
| Board Exam Mode | Simulate high-stakes exam conditions |
| Exports | Use materials offline or in class |

When writing copy for features, always frame them in terms of what the user gains or achieves — not what the system does.

### Avoid generic AI tool positioning

NoteLib is NOT:
- a general-purpose chatbot
- a one-shot summarizer
- a prompt playground

NoteLib IS:
- a structured study tool for students and board exam takers
- a note-first learning workspace with a repeating review loop
- a system for moving from notes → understanding → exam readiness

Do not write headlines or descriptions that could apply to any AI tool. Always anchor copy to study, retention, and exam preparation.

### Demo is a conversion tool

The `/demo` page is the strongest conversion driver on the site. Treat it as a guided learning experience, not a feature preview:
- Each step should feel like progress toward a learning goal.
- The quiz section must feel like a real exam mini-experience (interactive, not just showing answers).
- End the demo with a clear CTA that connects the experience to real use.

### Pricing copy must show progression

FREE → PLUS → PRO should feel like natural steps for a growing student:
- Free = getting started, not "limited"
- Plus = consistent, regular review
- Pro = serious exam preparation

Avoid describing lower plans as crippled. Describe them as suited for their stage.

## Prompting Mode Guidelines

Use two prompt modes depending on the type of task.

### 1. Long Prompt Mode
Use Long Prompt Mode when:
- implementing a new feature
- doing a non-trivial refactor
- changing data flow, persistence, routing, or architecture
- updating multiple related docs/specs
- the task has higher risk or more ambiguity

Long prompts should usually include:
- TASK
- GOAL
- CONTEXT
- implementation scope
- audit step if needed
- documentation updates
- testing expectations
- success criteria

### 2. Short Prompt Mode
Use Short Prompt Mode when:
- polishing UI
- fixing small bugs
- making follow-up refinements
- improving copy, spacing, labels, or interaction details
- the implementation is incremental and low-risk

Short prompts should usually include only:
- TASK
- GOAL
- short context
- implementation bullets
- essential docs/tests only if relevant
- success criteria

### Rule
Explicitly state the prompt mode in every prompt:
- Prompt mode: Long
- Prompt mode: Short

Default to Short Prompt Mode for incremental follow-ups unless the task clearly introduces a new feature or broader architectural change.

## Anti-Drift Rules (v0.12.0+)

These rules exist to prevent the most common forms of context drift across AI coding sessions. Read them before starting any task.

### Version Management Anti-Drift

- The current version is recorded once, in the `Current documentation baseline` line at the top of this file — do **not** restate a version number here. Always keep `backend/pom.xml`, `frontend/package.json`, `RELEASES.md`, `README.md`, `ROADMAP.md`, `AGENTS.md`, and `CLAUDE.md` version references in sync when bumping a version. *(This line used to name a specific version and went stale for two full release cycles — set at the `v0.67.0` kickoff and still reading `v0.67.0` at `v0.68.0` signoff, because both intervening kickoffs updated the baseline line at the top and not this one. The `/version-check` skill's 7-location table lists only one `AGENTS.md` field, so it did not catch the second. De-versioned at `v0.68.0` signoff so there is exactly one version reference per file.)*
- Do not change the version number during a feature implementation — only bump the version as a dedicated version-bump task.
- `RELEASES.md` is the canonical release log. Add new sections at the top. Do not delete old release entries.
- `docs/product/ROADMAP.md` is the canonical roadmap. The current release section must reflect the in-progress version.

### LLM Fan-Out Anti-Drift

- When introducing new LLM fan-out (`CompletableFuture.supplyAsync` patterns), use `llmParallelTaskExecutor`, never reuse the executor that dispatched the parent task — see `OpenAiLlmStudyPackService.generateLongExamParallel` for the canonical shape.

### Learner Level vs Course/Program Anti-Drift

- **Superseded in part by `docs/architecture/ADR-001-canonical-knowledge-architecture.md` (Accepted 2026-08-03) — read that ADR before changing anything in this block.** Two rules previously stated here have been retired by it: that static note and Study Pack content is **leveled by course/program**, and that **per-note learner level columns must never be reintroduced**. `v0.69.0` shipped `notes.domain_context` and `notes.learner_level` — the latter being precisely the per-note level column the old rule forbade. **Do not "restore" either retired rule.** Course/program carrying both the authoring domain and the depth signal is the defect the ADR exists to fix, not a constraint to preserve.
- **Learner Level** and **Course/Program** are separate concerns. Never merge them into a single field, a single UI input, or a single LLM prompt variable.
- Static **note content and Study Pack content are leveled by the effective authoring domain plus the note's own authored level** — `notes.domain_context` → **exactly one joined catalog program (`note_course_program`)** → note `courseProgram` → profile `courseProgram` for the domain, and `notes.learner_level` for the depth. (Same four-step chain stated at "Learning Profile Metadata Rule" above; the join step is consulted only at one row.) **Shared/copied content must never be calibrated from the reader's profile learner level.** That clause survives the ADR intact and is exactly the defect `v0.69.0`'s pre-signoff pressure test found in `buildSubjectSuggestionGuidanceBlock`: two users generating from byte-identical notes must receive identical static guidance. With no authored level, emit both guidance lists rather than silently falling back to the reader.
- `learnerLevel` controls taker-specific quiz/exam difficulty, explanation depth, vocabulary, and question complexity, and remains in `StudyPackGenerationContext` as the reader-level input to curriculum resolution.
- **Persisted quiz reuse does not key on the reader's `learnerLevel` (`v0.70.0`).** `exam_question_pool.learner_level`, `challenge_quiz_question_bank.learner_level`, and `ExamQuestionPoolService.sameLearnerLevel` gating all resolve `StudyPackGenerationContextResolver.effectiveCurriculumLevel(context)` — note level → reader level → `COLLEGE`. Never pass `context.learnerLevel()` into a pool or bank call; a reader-level change must not invalidate content authored for a note that carries its own level.
- Challenge question-bank rows are unique by `(user_id, study_pack_id, question_key, learner_level)`, using nulls-not-distinct semantics for the nullable legacy level. `learner_level` must stay in this key: reads are intentionally level-scoped, and an authored-depth correction must allow the preserved old-level row and a regenerated new-level row to coexist.
- **Challenge bank writes join the caller's transaction, and the best-effort guarantee is therefore PARTIAL.** A constraint violation on this path marks the transaction rollback-only (JPA-mandated) and aborts it on PostgreSQL (25P02), so the surrounding catch cannot save the session. `v0.115`'s widened uniqueness key removes the deterministic collision; a concurrent same-level duplicate remains possible and would still fail the session.
- **Do NOT "fix" this by moving the write into a `REQUIRES_NEW` transaction. That was tried in `v0.81.0` and reverted.** `challenge_quiz_question_bank.origin_session_id` and `claimed_session_id` are FKs to `quick_review_sessions`, and `startSession` inserts that session row in its own **uncommitted** transaction — so a second connection cannot see the parent and **every insert fails the FK check, deterministically and silently**. Isolation is still the right long-term shape, but it requires the session row to be visible to the inner transaction first. Any attempt must state how it solves that.
- Quiz/exam prompts receive learner level and course/program separately through `buildLearnerContextBlock()`; content prompts use `buildContentContextBlock()`, which omits the **reader's** level but does read the note's authored level.
- The note's authored level is the **curriculum floor** for quizzes and exams. A lower reader level may soften scaffolding and wording; it must never lower curriculum, terminology, or difficulty, and a higher reader level must never raise them above the note's level.
- Study Pack, Challenge Quiz, Board Exam, and Adaptive Practice generation must resolve both axes through `StudyPackGenerationContextResolver` — `effectiveAuthoringDomain()` (Domain Context wins; note `courseProgram` then profile `courseProgram` are fallbacks) and `effectiveCurriculumLevel()` (note level → reader level → `COLLEGE`). Never reconstruct either chain inside a generation service.
- Learner Level is required at the user/profile level for completed accounts, but generation context remains nullable for legacy/best-effort paths. `notes.learner_level` is the authored depth axis and outranks the profile level; it must not be removed or narrowed. **The "not renamed before the R4 checkpoint runs" half of this constraint has EXPIRED — R4 resolved 2026-08-04** (`ADR-001` → *R4 verification*), and `ADR-001` constraint 4 now governs renaming: the user-facing label may become `Educational Level` or `Authored Depth`, and any rename is **copy-only — the column stays `learner_level`**. Teacher quiz modal's `targetLearnerLevel` is the only per-generation override, and only an explicitly chosen value is persisted — never the resolved level.
- A collection's `learnerLevel` may pre-fill a visible new-note authoring control from the collection or nearest ancestor, but it is never a server-side default write and never changes an existing note when membership is added. No resolved collection level means no pre-fill, not `COLLEGE`.
- See `docs/features/profile-learning-context.md` for the full rule set.

### Upgrade CTA Anti-Drift

- Never hardcode a plan name as the universal upgrade CTA (e.g., never just `Go Pro` for all users).
- Always use `getUpgradeCtas(currentPlan)` from `frontend/src/config/plans.ts` to resolve plan-aware CTAs.
- Upgrade CTAs that drive in-app plan selection navigate to `/settings?section=plans`, not `/pricing`.
- `/pricing` is the public marketing surface only — linked from navbar and landing page, not from in-app paywalls.
- **Upgrade button LABELS stay feature-named** (`v0.76.0`). A button fired when a learner clicked Board Exam Mode must say `Unlock Board Exam Mode` — a button states what the click does, not why to care. The learning-system promise belongs in the paywall headline and body, never in the button.
- **Paywall headlines split by type** (`v0.76.0`): **capability** paywalls (nothing was used up) carry the narrative headline; **quota** paywalls keep a factual one (`You've reached your Study Pack limit`), because a learner who just hit a wall needs to know that is why the modal appeared. The narrative moves to the body there.
- **`PLAN_CARD_SUBTEXT` must describe the tier, never a feature.** It is keyed on plan type **alone**, so it renders on *every* paywall — a feature name placed there appears on paywalls about other features. This shipped as a real bug before `v0.76.0`: Adaptive Practice copy rendered on the Interview Practice and Board Exam Mode modals. Full contract: `docs/features/pricing.md` → *Paywall copy contract*.

### Analytics Event Anti-Drift

- Never use a string literal for an analytics event name without first adding it to the `AnalyticsEventType` union in `frontend/lib/api.ts`.
- All analytics calls are fire-and-forget (`void`). Do not `await` them or let failures block the primary flow.
- Analytics events fire after the surrounding transaction commits through the `AFTER_COMMIT` event listener, and `analytics_events.user_id` has no hard FK to `users(id)`. Never reintroduce that FK or persist analytics mid-transaction.
- Do not duplicate event tracking: `QUICK_REVIEW_COMPLETED`, `CHALLENGE_QUIZ_COMPLETED`, and `ADAPTIVE_PRACTICE_COMPLETED` are fired once per quiz completion, not per question or per partial step.

### Content Moderation Anti-Drift

- `ContentModerationService` applies token-based exact matching. It does NOT use substring matching — this is intentional to avoid false positives on words like "classic", "Damascus", "passage".
- Dictionary files live in `backend/src/main/resources/moderation/banned_words_*.txt`. Add new languages by dropping a new file — do not modify the service loader.
- `validateOrThrow()` is the integration point for validation boundaries. Call it after blank/length checks, not instead of them.
- The service allows all content when no dictionary files are loaded. Never silently skip loading errors in production.

### Paywall and Limit Surface Anti-Drift

- All paywall copy is action-aware. Never show generic "You've reached your limit" — always name the blocked action.
- `PaywallModal` resolves copy through `resolvePaywallAction(variant)` → `FREE_PAYWALL_CONTENT[action]`.
- `StudyPackLimitModal`, `NearLimitBanner`, and `PostSuccessUpgradeNudge` must use plan-aware CTAs, not hardcoded upgrade labels.
- When Study Pack credits reach `2` or `1`, show a `NearLimitBanner` — do not disable the Generate button.
- When Study Pack credits reach `0`, show a limit modal on click — do not disable the Generate button.

### Onboarding Anti-Drift

- The create-first onboarding flow has eight screens: Profile Type, Course / Program, Learner Level, First Intent, Input Method, Note, Study Pack Generation, and Completion. Closed-set screens 3–5 auto-advance only from a new selection; Course / Program and Note keep explicit actions.
- Onboarding drafts are schema-versioned. Any screen renumbering or answer-shape change must bump the draft schema version, preserve compatible answer fields, and recompute stale resume position instead of trusting the stored `currentStep`.
- Onboarding is one-way during terminal work. Do not add a Back button to Step 7 (Study Pack generation) or Step 8 (Completion).
- **Onboarding auto-advance is allowed ONLY on closed-set choices whose every option keeps the learner inside onboarding** — Screen 4 (First Intent) and Screen 5's Input Method. It is forbidden on: typed input (Course / Program, the note), because only the learner knows when typing is done; **Screen 3's Learner Level `<select>`**, because re-choosing the already-selected value fires no `change` event, which stranded anyone arriving with a value set (via a resumed draft or Back from Screen 4); and **Screen 5's ready-made fallback**, because two of its options leave onboarding and a mis-tap must not eject a learner. Screens that auto-advance render Back only — never a Continue that can never be clicked.
- **Screen 5 serves both Screen 4 branches** (Input Method for own-notes, Official Study Plan / unavailable-program fallback for ready-made). It is not a sub-state of Screen 4. Anything keyed on the ready-made branch — the plan re-resolution effect, `resolveEarliestUnansweredStep`, the practice-first screen predicate — must key on Screen 5. Keying them on Screen 4 silently sent resumed ready-made drafts back a step and made a reload claim no Official Study Plan existed when one did.
- **The onboarding step counter must always show the real step.** Do not special-case it to signal a terminal screen: an earlier build displayed the last step on the adopt screen, which produced a counter that jumped from 4 to 8 while the previous screen was still rendered.
- When Study Pack generation is active, hide the Back button and replace notice copy with `Your Study Pack is being created. This step can't be undone.`
- When the Study Pack limit is reached during onboarding, bump `currentStep` to 8 with `studyPackLimitReached=true`. Step 8 renders the limit-reached layout. This reuses the existing `completeOnboarding` useEffect — do not add a separate completion trigger.
- `handleStartStudyPack()` must check `draft.noteId` before creating a note (idempotency rule).

### Quiz Generation Anti-Drift

- `buildLearnerContextBlock()` is the single formatting point for learner level + course/program in quiz/exam prompts. Static note and Study Pack prompts use the content-context builder, which deliberately excludes learner level.
- Per-item multi-source provenance travels on `QuizItem`, never a parallel index-keyed array: merged quizzes are shuffled after merge, so an index-keyed sidecar silently corrupts source attribution.
- Quiz result statistics (score, performance level, weak concepts) are derived from stored session data only. No LLM calls for stats.
- Weak concept threshold is `< 60%` accuracy (`WEAK_CONCEPT_THRESHOLD`). Do not change this without a test covering the boundary.
- `lib/challenge-quiz-results.ts` owns quiz result computation utilities. Reuse them; do not duplicate the logic.

### Plan Configuration Anti-Drift

- `frontend/src/config/plans.ts` is the canonical source for plan names, CTA labels, and feature lists across all frontend surfaces.
- `docs/product/PLANS.md` is the canonical plan reference document.
- `frontend/lib/pricing-config.ts` owns runtime numeric limits.
- When plan limits or copy change, update all three. Do not update one and leave the others stale.

### Public Library Conversion Anti-Drift

- Public note pages are acquisition surfaces, not only app detail screens. Treat them as such when implementing any public note detail changes.
- The page order must be: teach → interact → convert. Do not move CTAs above the learning experience.
- `Share` must always be visible on public note pages regardless of auth state. Never hide it behind a login gate.
- Mini quiz preview is client-side only for anonymous users. Do not create a quiz session, persist a score, or call the quiz session API for unauthenticated users.
- The signup gate on the mini quiz continuation must appear only after the visitor has answered at least one question — not on page load.
- The soft conversion CTA (`Turn your own notes into something like this`) must appear before `Copy to My Library` and `Generate Study Pack` in the visual hierarchy.
- After a public visitor signs up from a public note page, route them toward copying that note or creating their own Study Pack — not back to the same public note preview.
- Generated note formatting improvements for public pages must not change how content is stored in the database or how authenticated note detail renders it.
- Before implementing any public note detail change, confirm the current RELEASES.md section and `docs/features/public-library.md` Public Note Detail section are current.

### v0.14.0 Post-Ship Anti-Drift

All v0.14.0 planned scope has shipped. Do not reopen these decisions:

- **Multi-note Long Exam** ships via `LongExamService.resolveAdditionalStudyPackIds` / `resolveSourceNoteRefs` / `generateQuizForSources`. It reuses the existing session lifecycle with no new persistence aggregate and no new `QuickReviewSessionMode` enum value. `LongExamStartRequest` accepts an optional `additionalStudyPackIds` list (max 3). Do not alter the proportional question distribution or subject-match validation without a product decision.
- **Interview Practice shipped as a sub-mode of Adaptive Practice** (JSONB `subMode: "INTERVIEW"` on the `ADAPTIVE` discriminator). The 5-mode contract is preserved. It uses a dedicated 10/month Pro-only quota, `gpt-4.1-mini` for critique calls, and `gpt-4.1` for generation. Do not revert or alter this cost split.
- When opening v0.15.1 work, run the release kickoff checklist in `CLAUDE.md` before the first feature commit.
