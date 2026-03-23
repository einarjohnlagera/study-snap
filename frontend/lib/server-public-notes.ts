import type { PublicNoteDetailResponse } from "@/lib/api";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

function buildApiUrl(path: string) {
  return `${API_BASE_URL}${path}`;
}

async function fetchPublicNote(path: string): Promise<PublicNoteDetailResponse | null> {
  const response = await fetch(buildApiUrl(path), {
    method: "GET",
    next: { revalidate: 300 },
  });

  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error("Could not load public note.");
  }
  return (await response.json()) as PublicNoteDetailResponse;
}

export async function getServerPublicNoteById(noteId: string) {
  return fetchPublicNote(`/notes/public/${noteId}`);
}

export async function getServerPublicNoteBySeoPath(subject: string, slug: string) {
  return fetchPublicNote(`/notes/public/seo/${subject}/${slug}`);
}
