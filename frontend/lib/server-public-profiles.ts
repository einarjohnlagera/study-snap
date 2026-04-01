import type { PublicProfileResponse } from "@/lib/api";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

function buildApiUrl(path: string) {
  return `${API_BASE_URL}${path}`;
}

export async function getServerPublicProfile(userId: string): Promise<PublicProfileResponse | null> {
  const response = await fetch(buildApiUrl(`/public/profile/${userId}`), {
    method: "GET",
    next: { revalidate: 300 },
  });

  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error("Could not load public profile.");
  }

  return (await response.json()) as PublicProfileResponse;
}
