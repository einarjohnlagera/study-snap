// THROW-AWAY: wraps globalThis.fetch so stubbed API paths are answered in-process. The real API is never contacted:
// a request to the configured API base that this stub does not own is BLOCKED (404) and recorded.
import { SCALE, U_MS, shared, stubFetch } from "./stub";

export function installPreviewStub(): void {
  if (shared.installed) return;
  shared.installed = true;
  const original = globalThis.fetch;
  const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";
  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const raw = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
    let url: URL | null = null;
    try { url = new URL(raw); } catch { url = null; }
    if (url) {
      const signal = init?.signal ?? (typeof input === "object" && "signal" in input ? (input as Request).signal : undefined);
      const stubbed = await stubFetch(url, signal);
      if (stubbed) return stubbed;
      if (raw.startsWith(apiBase)) {
        shared.blocked.push(raw.slice(0, 160));
        return new Response("{}", { status: 404, headers: { "content-type": "application/json" } });
      }
    }
    shared.passthrough += 1;
    return original(input, init);
  }) as typeof fetch;
  // Shorten ONLY the sitemap TTL (lowering-only override, clamped by the module). The page TTL stays the real 120 s at scale 1.
  process.env.NOTELIB_CATALOG_SITEMAP_TTL_MS ??= String(Math.round(U_MS / 2));
  if (SCALE < 1) process.env.NOTELIB_CATALOG_PAGE_TTL_MS ??= String(U_MS);
}
