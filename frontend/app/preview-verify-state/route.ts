// THROW-AWAY: reports the stub schedule and this instance's counters. Never merged.
import { NextResponse } from "next/server";
import { FAIL_END_U, FAIL_START_U, PERIOD_U, SCALE, U_MS, WITHDRAW_U, schedule, shared } from "@/preview-verify/stub";

export const dynamic = "force-dynamic";

export async function GET() {
  return NextResponse.json({
    ...schedule(),
    scale: SCALE, periodU: PERIOD_U, withdrawU: WITHDRAW_U, failStartU: FAIL_START_U, failEndU: FAIL_END_U,
    instance: shared.instance, stubInstalled: shared.installed, calls: shared.calls, seq: shared.seq,
    blockedApiRequests: shared.blocked.slice(-10), passthrough: shared.passthrough,
    pageTtlMs: process.env.NOTELIB_CATALOG_PAGE_TTL_MS ?? null, sitemapTtlMs: process.env.NOTELIB_CATALOG_SITEMAP_TTL_MS ?? null,
    uMs: U_MS,
  }, { headers: { "cache-control": "no-store" } });
}
