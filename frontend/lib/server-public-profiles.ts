import type { PublicProfileResponse, PublicProfileFocusResponse } from "@/lib/api";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

function buildApiUrl(path: string) {
  return `${API_BASE_URL}${path}`;
}

export type ServerPublicProfileResult =
  | { status: "ok"; profile: PublicProfileResponse; focus?: PublicProfileFocusResponse }
  | { status: "private" }
  | { status: "not_found" };

export async function getServerPublicProfile(userId: string): Promise<ServerPublicProfileResult> {
  // Both requests are fired before either is awaited, so a visibility toggle can't land in the gap
  // between two sequential requests. The no-op .catch keeps an unused rejection (the private/
  // not-found profile branches below never await this promise) from surfacing as unhandled.
  const profileFetch = fetch(buildApiUrl(`/public/profile/${userId}`), {
    method: "GET",
    next: { revalidate: 300 },
  });
  const focusPromise = getServerFocus(`/public/profile/${userId}/learning-focus`);
  focusPromise.catch(() => {});

  const response = await profileFetch;

  if (response.status === 404) {
    return { status: "not_found" };
  }
  if (response.status === 403) {
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
}

async function getServerFocus(path: string): Promise<PublicProfileFocusResponse> {
  const response = await fetch(buildApiUrl(path), { method: "GET", next: { revalidate: 300 } });
  if (!response.ok) throw new Error("Could not load public profile Learning Focus.");
  return (await response.json()) as PublicProfileFocusResponse;
}

export async function getServerPublicCreatorProfile(username: string): Promise<ServerPublicProfileResult> {
  const profileFetch = fetch(buildApiUrl(`/public/creator/${username}`), {
    method: "GET",
    next: { revalidate: 300 },
  });
  const focusPromise = getServerFocus(`/public/creator/${username}/learning-focus`);
  focusPromise.catch(() => {});

  const response = await profileFetch;

  if (response.status === 404) {
    return { status: "not_found" };
  }
  if (response.status === 403) {
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
}
