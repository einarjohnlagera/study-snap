**Status:** read-only measurement, no code or process changes. Produced for Handoff step 1 (M0) of `docs/claude-plans/2026-10-13-dual-ai-release-workflow-redesign.md` §7/§9 task 1.

# M0 Baseline Measurement — last 6 releases (v0.165.0–v0.170.0)

Source data: `~/.claude-personal/projects/-Users-einarlagera-IdeaProjects-study-snap/*.jsonl` (Claude) and `~/.codex/sessions/2026/**/rollout-*.jsonl` (Codex). Release windows are each version's kickoff-commit to signoff-commit timestamp, read directly from `git log` on `releases/vX.Y.Z` (not estimated).

## Release windows and shipped scope

| Release | Kickoff (local +08:00) | Signoff (local +08:00) | Wall time | Top-level bullets in RELEASES.md section (proxy for items shipped) |
|---|---|---|---|---|
| v0.165.0 — Bounded Discovery | 2026-10-02 08:34:11 | 2026-10-05 23:02:06 | 3d 14h 28m | 18 |
| v0.166.0 — Measured Twice | 2026-10-06 14:31:17 | 2026-10-07 13:59:06 | 23h 28m | 9 |
| v0.167.0 — Study Journey Purpose | 2026-10-07 22:52:07 | 2026-10-08 17:04:40 | 18h 12m | 13 |
| v0.168.0 — Free Quota, Fairly Metered | 2026-10-09 14:36:22 | 2026-10-09 14:56:23 | 20m | 7 |
| v0.169.0 — Study Journey, Continued | 2026-10-10 00:05:00 | 2026-10-12 22:38:45 | 2d 22h 33m | 3 |
| v0.170.0 — Study Journey, Closed | 2026-10-13 12:08:16 | 2026-10-13 20:05:38 | 7h 57m | 3 |

v0.168.0 is a genuine 20-minute release (one fix commit + one test-clarification commit between kickoff and signoff) — not a measurement error.

## Claude side (four token columns kept separate, per plan §7 Measurement)

Deduplicated by message `uuid` across all transcript files first (several sessions carry a duplicated boundary message from a prior compact/resume), then bucketed by each `assistant` message's own timestamp into the release window it falls in. "Main" = the role's own conversational turns. "Subagent" = `isSidechain: true` forked/cold-agent runs (pressure tests, falsification legs, focused investigations) — kept separate per the plan's own instruction not to double-count or miss these.

| Release | Role (main) | input | cache_creation | cache_read | output | turns (n) |
|---|---|--:|--:|--:|--:|--:|
| v0.165.0 | Release Implementor | 1,912 | 5,698,152 | 460,825,303 | 689,073 | 880 |
| v0.166.0 | Release Implementor | 1,656 | 5,705,443 | 407,818,946 | 681,455 | 810 |
| v0.166.0 | Note Strategist | 108 | 259,670 | 13,666,832 | 51,458 | 54 |
| v0.166.0 | Feature Planner | 28 | 2,927,918 | 7,316,431 | 24,991 | 14 |
| v0.167.0 | Release Implementor | 3,369 | 6,567,444 | 526,755,187 | 951,341 | 967 |
| v0.168.0 | Release Implementor | 446 | 260,569 | 117,865,286 | 124,480 | 223 |
| v0.169.0 | Release Implementor | 450 | 4,726,281 | 153,919,888 | 140,595 | 220 |
| v0.169.0 | Feature Planner | 38 | 3,773,336 | 7,165,450 | 28,075 | 21 |
| v0.170.0 | Release Implementor | 1,072 | 2,316,844 | 118,212,202 | 348,604 | 504 |

**Subagent / cold-agent runs (pressure tests, falsification, focused investigations), same windows:**

| Release | Subagent task | cache_creation | cache_read | output | n |
|---|---|--:|--:|--:|--:|
| v0.165.0 | Cold falsification pass on v0.165.0 | 257,416 | 7,448,667 | 707 | 78 |
| v0.166.0 | Falsify adoptGoal background-job implementation claims | 311,749 | 8,508,761 | 2,828 | 82 |
| v0.166.0 | Verify overdue/due-today CHECKPOINT rows | 56,122 | 1,718,335 | 4,923 | 10 |
| v0.166.0 | Audit ROADMAP.md for genuinely overdue checkpoints | 17,382 | 1,857,528 | 2,736 | 2 |
| v0.166.0 | Root-cause HikariCP leak in adoptGoal/getByUsername | 219,162 | 2,645,203 | 532 | 36 |
| v0.166.0 | Verify bulk regeneration restart/deploy durability | 52,422 | 6,509,437 | 4,961 | 24 |
| v0.167.0 | Find goal-setting UI and adoption-listing endpoints | 194,471 | 1,158,490 | 7,304 | 37 |
| v0.167.0 | Cold falsification pass on Study Journey Purpose diff | 355,878 | 10,665,704 | 2,587 | 97 |
| v0.168.0 | *(none fired in-window)* | — | — | — | — |
| v0.169.0 | *(none fired in-window — see Gap note below)* | — | — | — | — |
| v0.170.0 | Falsify multi-goal exam support release claims | 141,177 | 2,579,501 | 477 | 33 |

**Owner-facing Claude turns per release** (count of human-authored `user` messages in main sessions, excluding tool-result-only synthetic turns and sidechains): v0.165.0 = 14, v0.166.0 = 23, v0.167.0 = 36, v0.168.0 = 2, v0.169.0 = 4, v0.170.0 = 9. **Caveat:** this is a mechanical proxy (presence of plain-text content with no `tool_result` block); it is consistent with the recorded ~3-turn ship-fast cadence target for small releases but has not been manually spot-checked line-by-line, so treat the exact counts as approximate, not exact.

## Codex side

Codex's `token_usage_record.thread_token_usage` is a **cumulative** per-thread counter, not a per-turn delta — summing the raw per-record `turn_token_usage` field double/triple counts (a direct sum produced a nonsensical 3.25B input tokens against a thread total of 35M, confirmed by checking one file). Numbers below are reconstructed as **deltas** between consecutive cumulative records, bucketed by each record's own timestamp, so a single rollout spanning two release windows would split correctly (none of the 7 Codex rollouts found this October actually crossed a release boundary — each sat entirely inside one window or entirely outside all of them).

| Release | Codex turns | input | cached_input | output | reasoning_output |
|---|--:|--:|--:|--:|--:|
| v0.165.0 | 64 | 6,641,972 | 6,437,120 | 25,411 | 6,935 |
| v0.166.0 | 336 | 40,963,646 | 39,860,608 | 117,653 | 38,875 |
| v0.167.0 | 118 | 12,849,245 | 12,687,232 | 31,408 | 9,112 |
| v0.168.0 | 10 | 462,881 | 407,552 | 4,101 | 641 |
| v0.169.0 | 22 | 1,268,766 | 1,208,320 | 6,623 | 773 |
| v0.170.0 | 211 | 28,180,715 | 27,776,768 | 59,261 | 15,070 |

**Notable finding, not hypothetical:** the v0.168.0 Codex rollout's `cwd` is `~/.claude-personal/jobs/ead88f3a/tmp/codex-falsify-v0.168.0/repo` — a temp clone, not the main worktree. This confirms **a read-only Codex falsification leg already ran against this repo before the redesign plan existed** (consistent with D8's premise that this is an existing, not hypothetical, practice worth formally comparing against the Opus cold agent per §7 M7). All other six rollouts ran with `cwd` = the real repo path.

## Ranked line items (per plan §7 Measurement)

1. **Pressure-test / cold-agent (subagent) runs are the clearest isolatable line item**, exactly as the plan hypothesizes. For releases that ran one, subagent `cache_read` ranges from ~1.2M (small focused checks) to ~10.7M tokens (full falsification passes) per run, and a release can run several (v0.166.0 ran five). This is additive on top of the main session's own cost.
2. **Per-turn CLAUDE.md cost cannot be cleanly isolated from the numbers above.** `cache_read_input_tokens` on the main session reflects the *entire* accumulating conversation cache (prior tool outputs, file reads, everything), not just the auto-injected CLAUDE.md — e.g. v0.168.0's 223-turn session alone read back 117.9M cache tokens, far more than 223 turns × a ~13–15k-token CLAUDE.md would produce on its own. Isolating the CLAUDE.md-specific share would need per-turn breakdown (first-turn `cache_creation` size as a proxy), which this pass did not attempt — flagging rather than guessing, per the plan's own measurement honesty rule.
3. **Claude-side reads of AGENTS.md are not separately observable from token counts alone** — would need a grep over `tool_use` blocks in each transcript for `Read` calls targeting `AGENTS.md`, not attempted in this pass (scope note, not a blocker: this is listed by the plan as one of the harder-to-isolate items).
4. Do not collapse the four Claude columns into one number — how Pro quota weights cached vs. fresh tokens is unconfirmed, so a token total is not a cost total.

## Measurement caveats

- **A genuine 5-day apparent gap in the raw file-modification timestamps (Oct 8 18:48 → Oct 14 07:32) is NOT missing data** — it's an artifact of one single, long-running "Release Implementor" session file (`ead88f3a-...jsonl`) whose filesystem mtime only advances past the gap boundary on its last write. The session's *internal* message timestamps cover the full window continuously; this was caught before being reported as a data-loss finding.
- **A real gap does exist between v0.169.0 signoff (Oct 12 22:38 local) and v0.170.0 kickoff (Oct 13 12:08 local)** — substantial Claude activity in that ~13.5-hour window (458 Release Implementor turns, 82 Feature Planner turns plus a 207-turn Feature Planner subagent run, 38 Prod Investigator turns) is **not attributable to either release** under strict kickoff-to-signoff windowing. Some of this is almost certainly the dual-AI workflow redesign plan itself being authored. This is inter-release overhead the per-release table above does not capture, and the redesign plan does not yet have a place to put it.
- Session-to-role attribution uses each file's `custom-title.json` (`customTitle` field) — these already exist informally today ("Release Implementor", "Feature Planner", "Note Strategist", "Prod Investigator") even though the redesign's charter files don't yet exist. Not every session has one; those fall back to "unknown" (none did for the in-window data above).
- v0.168.0 is a two-commit, 20-minute release — its numbers are real but too small a sample to generalize a per-item average from.
- Earlier releases than v0.165.0 were not pulled; the plan asked for 2–3, this covers 6 (three with subagent/pressure-test activity, three without) since the extra releases were cheap to include once the windowing script existed and give the D6/D8 benchmarks (§7 M6/M7) more real Tier-H occurrences to eventually draw on.
