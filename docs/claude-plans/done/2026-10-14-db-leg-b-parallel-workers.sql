-- Leg B — disable parallel query workers on notelib-db-prod. OWNER-RUN — Claude does not execute
-- production writes. Written 2026-10-14 (Release Implementor, v0.172.0 kickoff), from Leg B of
-- docs/claude-plans/2026-10-14-db-oom-memory-ceiling-fix-plan.md.
--
-- Why: each parallel worker is a full extra Postgres process competing for the 0.1-core / 256 MB
-- budget. notes (~13.7k rows) and analytics_events (~77k rows) are too small for parallelism to
-- buy meaningful wall-clock time. The discovery-sections ranked query currently plans a
-- Gather Merge (finding §6).
--
-- Target, verified by Prod Investigator: database notelib_db_prod, app role notelib_db_prod_user.
-- Reversible: ALTER DATABASE notelib_db_prod RESET max_parallel_workers_per_gather;

-- 1. Pre-check (expect the server default, normally 2)
SHOW max_parallel_workers_per_gather;

-- 2. The change. Expect: ALTER DATABASE
ALTER DATABASE notelib_db_prod SET max_parallel_workers_per_gather = 0;

-- 3. Verify from a NEW session (the setting applies only to sessions opened after step 2;
--    the backend's 20 pooled connections pick it up as they are recycled or at the next deploy).
--    Expect: 0
SHOW max_parallel_workers_per_gather;

-- 4. Verify the plan change: re-run the finding §6 EXPLAIN (no ANALYZE) for the discovery-sections
--    ranked query; the Gather / Gather Merge node should be gone.

-- Kill criterion (shared with v0.172.0): count `*terminated by signal 9*` on
-- dpg-d6tvb8fkijhs73fda4m0-a over the 7 days after both this SET and the v0.172.0 deploy.
-- Baseline: 9 in 2026-10-08 → 10-14.
