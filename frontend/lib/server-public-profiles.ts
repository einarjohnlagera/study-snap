import type { PublicProfileResponse, PublicProfileFocusResponse } from "@/lib/api";
import {
  CATALOG_FAILURE_BACKOFF_MS,
  CATALOG_PAGE_TTL_CAP_MS,
  CATALOG_PROFILE_DEADLINE_MS,
  CATALOG_PROFILE_REQUEST_TIMEOUT_MS,
  catalogTtlMs,
  createRequestSignal,
  getCatalogCached,
} from "@/lib/catalog-cache";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

function buildApiUrl(path: string) {
  return `${API_BASE_URL}${path}`;
}

export type ServerPublicProfileResult =
  | { status: "ok"; profile: PublicProfileResponse; focus?: PublicProfileFocusResponse }
  | { status: "private" }
  | { status: "not_found" };

async function getServerFocus(path: string, signal: AbortSignal): Promise<PublicProfileFocusResponse> {
  const response = await fetch(buildApiUrl(path), { method: "GET", cache: "no-store", signal });
  if (!response.ok) throw new Error("Could not load public profile Learning Focus.");
  return (await response.json()) as PublicProfileFocusResponse;
}

async function loadPublicProfile(path: string, loaderSignal: AbortSignal): Promise<ServerPublicProfileResult> {
  const profileRequest = createRequestSignal(loaderSignal, CATALOG_PROFILE_REQUEST_TIMEOUT_MS);
  // The focus request must abort on the loader's deadline, on its own timeout, AND when the profile
  // turns out private/not-found. AbortSignal.any would do it in one call but is not available in the
  // jsdom test environment, so link the loader signal to a controller we can also abort ourselves.
  const focusController = new AbortController();
  const abortFocus = () => focusController.abort();
  if (loaderSignal.aborted) abortFocus();
  else loaderSignal.addEventListener("abort", abortFocus, { once: true });
  const focusRequest = createRequestSignal(focusController.signal, CATALOG_PROFILE_REQUEST_TIMEOUT_MS);
  const focusSignal = focusRequest.signal;
  try {
    // Both requests are fired before either is awaited, so a visibility toggle can't land in the gap
    // between two sequential requests. The no-op .catch keeps an unused rejection (the private/
    // not-found profile branches below never await this promise) from surfacing as unhandled.
    const profileFetch = fetch(buildApiUrl(path), {
      method: "GET",
      cache: "no-store",
      signal: profileRequest.signal,
    });
    const focusPromise = getServerFocus(`${path}/learning-focus`, focusSignal)
      .finally(() => focusRequest.clear());
    focusPromise.catch(() => {});

    const response = await profileFetch;
    if (response.status === 404) {
      focusController.abort();
      return { status: "not_found" };
    }
    if (response.status === 403) {
      focusController.abort();
      return { status: "private" };
    }
    if (!response.ok) {
      throw new Error("Could not load public profile.");
    }
    return {
      status: "ok",
      profile: (await response.json()) as PublicProfileResponse,
      focus: await focusPromise,
    };
  } finally {
    profileRequest.clear();
    loaderSignal.removeEventListener("abort", abortFocus);
    focusController.abort();
  }
}

const cacheOptions = {
  hardCapMs: CATALOG_PAGE_TTL_CAP_MS,
  deadlineMs: CATALOG_PROFILE_DEADLINE_MS,
  shouldCache: (result: ServerPublicProfileResult) => result.status === "ok" || result.status === "private",
  failureBackoffMs: CATALOG_FAILURE_BACKOFF_MS,
};

export async function getServerPublicProfile(userId: string): Promise<ServerPublicProfileResult> {
  const value = userId.trim();
  if (!value) return { status: "not_found" };
  return getCatalogCached(
    `profile:id:${value.toLowerCase()}`,
    catalogTtlMs(CATALOG_PAGE_TTL_CAP_MS, process.env.NOTELIB_CATALOG_PAGE_TTL_MS),
    (signal) => loadPublicProfile(`/public/profile/${encodeURIComponent(value)}`, signal),
    cacheOptions,
  );
}

export async function getServerPublicCreatorProfile(username: string): Promise<ServerPublicProfileResult> {
  const value = username.trim();
  if (!value) return { status: "not_found" };
  return getCatalogCached(
    `profile:username:${value.toLowerCase()}`,
    catalogTtlMs(CATALOG_PAGE_TTL_CAP_MS, process.env.NOTELIB_CATALOG_PAGE_TTL_MS),
    (signal) => loadPublicProfile(`/public/creator/${encodeURIComponent(value)}`, signal),
    cacheOptions,
  );
}
