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
  const response = await fetch(buildApiUrl(`/public/profile/${userId}`), {
    method: "GET",
    next: { revalidate: 300 },
  });

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
    focus: await getServerFocus(`/public/profile/${userId}/learning-focus`),
  };
}

async function getServerFocus(path: string): Promise<PublicProfileFocusResponse> {
  const response = await fetch(buildApiUrl(path), { method: "GET", next: { revalidate: 300 } });
  if (!response.ok) throw new Error("Could not load public profile Learning Focus.");
  return (await response.json()) as PublicProfileFocusResponse;
}

export async function getServerPublicCreatorProfile(username: string): Promise<ServerPublicProfileResult> {
  const response = await fetch(buildApiUrl(`/public/creator/${username}`), {
    method: "GET",
    next: { revalidate: 300 },
  });

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
    focus: await getServerFocus(`/public/creator/${username}/learning-focus`),
  };
}
