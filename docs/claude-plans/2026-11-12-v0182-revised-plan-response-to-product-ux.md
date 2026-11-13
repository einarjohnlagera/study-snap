# Response to Product UX: revised interactive LLM migration plan

Status: planning response to the **2026-11-12** required next response; the controlling preregistration is `2026-11-12-v0182-llm-migration-preregistration.md`, **Revision 2 with the 2026-11-13 pre-freeze audit correction, still DRAFT**. All sample sizes, thresholds, operating characteristics, usage counts and calendar dates live there. References below are to its sections unless a source document is named.

## 1. Revised benchmark matrix

Usage-read labels and their dated definitions are in **section 3**. Full means blind answer-key, ambiguity, grounding and explanation review; lighter still means compatibility and the approved class-B semantic rubric. The proposed study-tip placement awaits owner confirmation.

| Workflow | Class | Evaluation tier | Usage-read reference |
|---|---|---|---|
| Note-scoped Adaptive Practice | A | Full | §3 U1/U6 |
| Plan-scoped Adaptive Practice | A | Full | §3 U1/U6 |
| Teacher quiz | A | Full | §3 U3 |
| Board Exam generation | A | Full | §3 U1/U5 |
| Long Exam direct, Long-Exam-pool and Board-Exam-pool subgroups | A | Full | §3 U1/U5/U6 |
| Interview Practice generation | A | Full | §3 U1 |
| Quick Review study tip | B | **PROPOSED lighter** | §3 U6 |
| Collection companion | B | Lighter | §3 U4; adoption/copy caveat there |
| Ask Companion | B | Lighter | §3 U1/U2 |
| Interview critique | B | Lighter | §3 U1 |

## 2. Route-level acceptance and workflow safety

The route remains the decision unit. **Sections 1, 4 and 8** preregister pooled paired statistics for noncritical differences and independent workflow gates for systematic or reproducible and critical failures. A candidate-only critical finding triggers a frozen, same-workflow confirmation; a confirmed critical failure cannot be averaged away. An isolated final parse/schema failure triggers two owner-run, same-input rechecks under unchanged settings; only reproduction on both confirms T1, while an unreproduced failure is investigated and counted once in T3. Missing/invalid evidence is investigated or INCONCLUSIVE. **Section 9** requires paired Sol-versus-Luna improvement on the gate Luna failed, with cost and latency checks. Its reconciled deadline rule prevents an attended save of a route with a confirmed critical or compatibility failure. The section-1 all-tier calculation shows the **20%** equivalent-route target holds through the modeled **0–5%** final-parse grid under its stated assumptions and fails on Interactive at **6%**; persistent or correlated failures remain a separate gate.

## 3. Estimated maximum spend

**PROPOSED separately approved API stage caps: $12 for baseline + Luna primary, $80 for triggered frozen confirmations (including Sol on a reserve only when the paired rule calls for it), and $55 for justified Sol primary routes.** Their computed combined ceiling is **$147 if all three are approved**, not a single up-front authorization. **Section 5** gives dated per-workflow token sizes, low/high **1×/3×** candidate output multipliers, lean versus production retry/fan-out calls, probability-weighted expected and scheduled-worst figures for each stage, the Sol false-escalation rate, and the one-call headroom and cumulative-reservation runner gap. The prices are the repository estimate pending provider confirmation; blind Codex-plan scoring is outside API spend. No execution starts until the owner approves the relevant stage and the runner can enforce its cap; Sol calls require a separate decision.

## 4. Answer-key verification gaps

The production-path register in **section 7, “Answer-key guard register”** identifies the shared structural and narrow consistency checks for every question-generating path. Board Exam and Long Exam have **no independent factual answer-key guard**; the Challenge semantic verifier is confined to its own path and its switches remain out of scope. Full blind evaluation checks every generated question for key correctness, multiple defensible answers, grounding and teaching explanations. This is an inventory and evaluation plan; it proposes no verifier or prompt change.

## 5. Post-migration roadmap recommendation and owning sessions

| Stream | Owning session and next decision |
|---|---|
| Database reliability | **Prod Investigator**, read-only: reconcile the dated kill/memory evidence and determine whether a lighter catalog list read would materially relieve the observed pressure before any build. The walker hypothesis is unproven (`docs/product/ROADMAP.md`, Backlog Index “lighter list read for the walkers” decision; `docs/claude-findings/2026-10-28-db-signal9-kills-crawler-shaped-public-traffic.md`). |
| Publication privacy correctness | **Feature Planner**: take the existing Phase 0 plan through its separate scope/approval path. Preserve the ALE/PNLE published-stamp contract while correcting withdrawn/deleted parent reachability (`docs/claude-plans/2026-11-11-collection-publication-boundary-phase0-scope.md`, §§1–2; parent discovery plan §D). |
| Long-term Quiz Quality | **Feature Planner with Product UX**: define a dedicated evidence-driven initiative for correctness, distractor plausibility, difficulty calibration, ambiguity and explanations, using the existing quality harness and findings. Preregister rubric, comparable material, meaningful samples and learner-value gates; do not restart failed prompt-only experiments without a new hypothesis (`docs/claude-plans/2026-10-27-challenge-quiz-quality-preregistration.md`; `docs/claude-plans/2026-11-10-next-releases-sequence-recommendation.md`, §§6–7). Challenge Quiz implementation remains on hold; no next release number is reserved. |
| Study Plan Discovery | **Feature Planner**, when capacity permits, following the staged discovery plan and Phase 0 dependency (`docs/claude-plans/2026-11-10-study-plan-discovery-and-community-collections-plan.md`, §D). |

**Board Exam Redo Missed fit test:** the Feature Planner should ask whether the isolated collision fix stays within the investigated backend branches, collision-dialog copy and focused tests; whether it changes only the described session states; whether a backend deploy can be safely scheduled; and whether the migration's build, attended-save spacing and checkpoints remain intact. It **does require a backend deploy** and frontend copy (`docs/claude-plans/2026-11-10-next-releases-sequence-recommendation.md`, §5). Recommend an earlier isolated slot if those checks pass. It does **not** ride the model-only `v0.182.0` environment saves; if a safe deploy slot would threaten the migration target, place it after migration. The release session receives only the resulting approved plan, not ownership of these investigations.

## 6. Decisions still required of the owner

**Section 12** is the controlling list: approve spend and freeze, confirm production route values and provider documentation, fix the class-B sampling frame and privacy/scoring mechanism, confirm the proposed study-tip placement and protocol numbers, and select a supported post-shutdown emergency model. The owner’s settled decisions and the target/backstop treatment are also recorded there and in the Revision 2 log; they are not being reopened here.

No paid run, model switch, production change, merge or deployment is authorized.
