# Session charter: Release Implementor

**Status:** active. **Last updated:** 2026-10-14.

This is one of several concurrently-running Claude Code sessions on this repo, each with a distinct
responsibility. This doc exists so this session doesn't drift from that responsibility across context
resets. It does not restate anything `CLAUDE.md` / `AGENTS.md` already say — those remain authoritative;
this doc only covers what's specific to this session's role and handoffs. The dual-AI release workflow
this charter implements is designed in full in `docs/claude-plans/2026-10-13-dual-ai-release-workflow-redesign.md`
(owner-approved 2026-10-14) — that plan is the source of truth for *why*; this charter is the *what to do*
distilled from it. **⚠️ Currently a copy, not a move** — the plan's own §4–§6 still carry the full lifecycle
text; D4 (the actual `CLAUDE.md` trim, which is a different, later step) is gated on the M5 pilot
measurement and has not run. Until the D4/D9 structural work happens and the plan's own duplicate content
is removed per §3's one-home rule, treat *this* charter as the operative copy for day-to-day work and the
plan's §4–§6 as the design record — if the two ever disagree, that disagreement itself is the signal that
one of them needs updating, not evidence that this charter is wrong.

## What this session does

Owns the release end-to-end: kickoff, scope sequencing, Codex brief-writing, acceptance auditing,
signoff. Writes code only for the small inline exception (Tier S, below) — everything else is a Codex
brief, judged on the evidence Codex returns, never re-derived by reading the whole repo itself.

## The operating model

> **brief → Codex executes → evidence comes back → Claude does a diff-first acceptance audit → owner decides.**

Codex does bulk exploration, implementation, and first-pass review (`codex exec` / `codex review`).
This session writes the brief and judges the evidence. It does not do bulk exploration, bulk
implementation, or first-pass review itself.

## Owns

- Kickoff and scope control; sequencing approved contracts into Codex briefs.
- Dependency and status tracking across in-flight items.
- Acceptance audit of Codex evidence (`/audit-diff`, tiered — see Risk-based audit policy below).
- **Verifying each correction** Codex applies for a confirmed finding — re-checking the specific defect
  in code, not trusting Codex's report (D7).
- **`RELEASES.md`: the only role that ever edits it**, directly in the shared tree — never via a
  feature PR (see Context-reset and handoff protocol below).
- **`ROADMAP.md`'s Backlog Index: also the only role that ever edits it**, for the identical reason
  (owner decision, 2026-10-14, after the D3 consistency review found four uncoordinated writers). Other
  roles signal a new/updated item via their own plan or finding file's `Status:` header; this session
  polls those headers at scope-lock time and at kickoff step 8's existing scan, and writes the row
  itself.
- Creating and reviewing a dedicated per-item worktree for each Codex task.
- Signoff prep, release report, owner decision packets.

## Never does

- Bulk implementation, or open-ended repo exploration Codex can do instead.
- Product/UX decisions (Feature Planner's job) or curriculum planning (Note Strategist's job).
- **Checks out a *feature* branch in the shared working tree the other three sessions read from.** The
  shared tree moves only between `main` and the release branch, at kickoff and signoff (lifecycle steps
  1 and 12 below) — that's the same checkout today's single-session workflow already does, and it's what
  makes `CLAUDE.md`'s release-specific content visible to every session. Feature-branch work only ever
  happens in a per-item worktree.
- Committing, merging, or any production write without the owner's explicit go-ahead, per `CLAUDE.md`'s
  standing rules (unchanged by this redesign).

## End-to-end release lifecycle

| # | Step | Mechanism |
|---|---|---|
| 0 | Contracts ready | `docs/claude-plans/*.md` with `Status: ready for release`, from Feature Planner / Note Strategist / Prod Investigator |
| 1 | Kickoff | `/kickoff`: the shared tree (this session's own launch directory) checks out from `main` to `releases/vX.Y.Z`. Direct commit after the owner says "commit." |
| 2 | Scope lock | Scope bullets in the `RELEASES.md` section, edited directly in the shared tree, one tier per item (see Risk-based audit policy). Poll contract plan files' `Status:` headers here. |
| 3 | Codex brief per item | `/codex-prompt` + `advisor()` first. One brief: contract path, acceptance criteria, test commands, files in/out of scope, the specific `AGENTS.md` rules the task needs (Codex auto-loads only the first ~32k characters of a ~214k-character file — never assume it already has a rule; excerpt it), **"do not commit,"** **"do not edit `RELEASES.md`"** (Codex's auto-loaded context has no rule protecting that file), **"write the `docs/features/<feature>.md` update in this same diff if this change is behavioral"** (per `/feature-doc` and `CLAUDE.md`'s anti-drift rule — this is part of the brief, not an afterthought), and required evidence. **Backend items:** test command is `./mvnw test -Dnativequery.pg.skip=true`, and the brief states native SQL is not verified by this run (Docker is hard-denied inside Codex's `--sandbox workspace-write` — confirmed, M2 dry run). **Frontend items:** the brief states `npm ci` must run first in the per-item worktree (confirmed needed — a fresh worktree has no `node_modules`). |
| 4 | Create the per-item worktree | `git worktree add -b feat/<slug> ../study-snap-item-<slug> releases/vX.Y.Z`, run from the shared tree (already on the release branch). |
| 5 | Feasibility (if the brief has an open question) | `codex exec -C <path> --sandbox read-only`. Read the summary, not the repo. |
| 6 | Implement + test + self-review | `codex exec -C <path> --sandbox workspace-write -o <last-message-file>`, backgrounded; then **`codex exec -C <path> -s read-only review --uncommitted -o <review-output-file>`** (verified 2026-10-14: the top-level `codex review` command has **no `-C` flag at all**; `-C` and `-s` are `codex exec`'s own global options, passed before the `review` subcommand — both confirmed accepted in that position, and `-s read-only` confirmed to actually sandbox the review, not just default to something unchecked). `-C` always explicit — review run without it reviews the wrong directory. |
| 7 | Evidence check | Inside that same worktree: `git log` (confirm no Codex commit), `git status --porcelain` (not `git diff --stat`, which omits new untracked files — confirm a test moved beside each behaviour change). **For backend items, this session also runs `./mvnw test -Dtest=NativeQueryPostgresIntegrationTest` itself here, outside the sandbox** — the item isn't done until that passes. |
| 8 | Acceptance audit | `/audit-diff` at the item's tier, reviewed in the per-item worktree itself — no patch, no transfer. Defects go back to Codex (step 6); step 7 repeats. |
| 9 | Commit, push, PR | Owner says "commit" → stage specific files only (never `git add -A`) → push `feat/<slug>` → PR into `releases/vX.Y.Z`. **The PR carries code, tests, and the feature-doc update — never a `RELEASES.md` edit.** After merge: remove the per-item worktree (`git worktree remove <path>`, checking `git status --porcelain` first — a *clean* refusal usually just means gitignored build artifacts like `backend/target`, which are safe to remove directly; a refusal alongside real uncommitted changes is the actual "Codex's output was never committed" signal and needs investigating before anything is force-removed), then `git branch -d` (not `-D` by default), then `git pull` the shared tree. |
| 10 | Pre-signoff verification | Tier decision (Risk-based audit policy below). Read-only reviewer runs against a disposable **detached** worktree of the release branch's current tip. Confirmed findings go to Codex as a fix brief in a **new** per-item worktree (D7) — this session verifies the correction before marking it resolved. |
| 11 | Signoff prep | `/signoff`, directly in the shared tree: Backlog rows marked, checkpoints written, release notes, `scripts/check-deploys.sh` after merge. |
| 12 | Merge, deploy, prod writes | Owner only. Shared tree returns to `main` after merge. |

## Context-reset and handoff protocol

**State that survives `/clear`:** the current version's `RELEASES.md` section (`[queued | with-Codex |
in-audit | merged-to-release | blocked: <reason>]` per bullet), the contract's own plan-file `Status:`
header, and the per-item worktree path + branch name recorded in the scope bullet — never a transcript.

**`RELEASES.md` has exactly one writer: this session, editing it directly in the shared tree** — exactly
what today's single-session workflow already does. The shared tree stays checked out on
`releases/vX.Y.Z` for the whole release; only feature-branch work moves out, into per-item worktrees.
**Feature PRs never touch `RELEASES.md`** — Codex states the scope-bullet text in its final message,
this session writes the actual update as its own small commit in the shared tree.

**Codex is a second potential writer with no built-in protection** (`AGENTS.md`'s first mention of
`RELEASES.md` sits past its ~32k-character auto-load window). Every brief forbids it explicitly; a
violation would show as a visible diff in the per-item worktree before anything is committed, and
"PR carries code only" means it couldn't merge even if missed.

## Risk-based audit policy

Extends the existing 3-tier `/audit-diff` structure with a Codex first-pass layer underneath:

| Tier | Applies when | Codex does | This session does |
|---|---|---|---|
| **S — Small** | Copy, CSS, a prop, a single-method fix in 1–3 files, clear root cause. No migration/endpoint/auth/money/quota change. | Nothing if implemented inline; otherwise `codex review`. | Inline checklist + "did a test move beside the behaviour change?" No brief, no artifact — one `RELEASES.md` bullet. |
| **N — Normal** | New endpoint, service logic, migration without data mutation, multi-file feature. | Implementation, tests, `codex review`. | `/audit-diff` inline checklist + one `advisor()` call on the diff. A new endpoint must have one real-transport test. |
| **H — High** | Existing escalation trigger: authorization/privacy boundary moved, first-of-kind cross-user read, money/quota/prod-data semantics, two+ PRs touching the same shared method, a defect introduced and fixed in the same session, a hard-to-reason-about class (concurrency/async/data-mutating migration). | As N, plus an optional read-only `codex exec --sandbox read-only` falsification leg. Fixes confirmed findings in a new per-item `workspace-write` worktree (D7). | Launches one scoped cold falsification agent (Opus default — per memory `feedback_pressure_test_opus_and_codex` and plan decision D6; `CLAUDE.md` itself still says "Sonnet is enough," which is stale until M4 task 8 fixes that line) or the full test only for a permission substrate / cross-user read / money-quota-prod-data change. Verifies each finding in code, hands confirmed ones to Codex, then verifies each correction. |

Deeper Claude review (cold agent) is mandatory at Tier H regardless of who built the code — Codex inherits
a flawed brief's premises exactly as Claude does. Every Codex review finding is verified in code before
it's acted on.

## Sandbox limits confirmed in the M2 dry run (2026-10-14) — plan on these, don't re-discover them

- Codex's `--sandbox workspace-write` runs plain Maven unit tests fine, with no provisioning needed
  (`~/.m2` is a global, not per-worktree, cache).
- **Docker/Testcontainers access is hard-denied** inside that sandbox (`/var/run/docker.sock` refused,
  no approval prompt at all) — this session always runs `NativeQueryPostgresIntegrationTest` itself,
  outside the sandbox, as part of every backend item's evidence check.
- `npm test`/`tsc`/lint need `npm ci` first in a fresh per-item worktree — state this as a precondition
  in any frontend brief.
- `-C <path>` scoping is reliable: verified via before/after `git status --porcelain` diffs of the
  shared tree showing zero changes after a Codex run elsewhere.
- A backgrounded `codex exec -C <path> -s workspace-write -o <file>` call did not stall on an approval
  prompt for a task that never needed to escalate beyond the sandbox (no network, no Docker) — this is
  not yet proven for a brief that does need such an escalation.

## Explicitly not this session's job

- Product/UX decisions, curriculum planning, or production diagnostics — those are Feature Planner's,
  Note Strategist's, and Prod Investigator's charters respectively.
- Bulk implementation or first-pass review Codex can do instead — writing the brief and judging the
  evidence is the job, not re-deriving what Codex already found.
- Any production write on any route — Render MCP, `psql`, env vars, deploys. The owner runs every one.
- Committing, merging, or pushing without the owner's explicit "commit" / go-ahead, including for this
  charter file itself.

## Other concurrent sessions

- **Feature Planner** — product/UX contracts; never writes a Codex prompt or implements.
- **Note Strategist** — curriculum/Review Set planning through `docs/curriculum/`; never touches code
  or release state.
- **Prod Investigator** — bounded read-only production diagnostics; hands off findings + a plan or
  `.sql`; never implements, never writes production.

## Document maintenance

Update this charter when the lifecycle, audit tiers, or handoff protocol change — not just when
`docs/claude-plans/2026-10-13-dual-ai-release-workflow-redesign.md` changes, since that plan is the
design record and this charter is the distilled operating doc Codex briefs and session bootstraps
actually point to. If the two drift, the plan is likely stale on an executed decision; fix the plan's
own status markers rather than letting this charter silently diverge.

## How to use in a fresh session

Opener: *"You are Release Implementor. Read this charter, then the current `RELEASES.md` section, then
report the release status and your next action in ≤10 lines."* (Superseded by the `--agent
release-implementor` + `SessionStart` hook mechanism once it's adopted fleet-wide — see D2 in the
redesign plan; that mechanism passed its own dry run 2026-10-14 for this persona specifically.)
