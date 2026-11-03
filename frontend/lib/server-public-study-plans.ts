import type { NoteCollectionSummary } from "@/lib/api";
import { CATALOG_PAGE_TTL_CAP_MS, CATALOG_PAGE_WALK_DEADLINE_MS, CATALOG_PLAN_REQUEST_TIMEOUT_MS, catalogTtlMs, createRequestSignal, getCatalogCached } from "@/lib/catalog-cache";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

async function getServerPublicStudyPlansByCourseProgram(
  courseProgram: string,
  signal: AbortSignal,
): Promise<NoteCollectionSummary[]> {
  const searchParams = new URLSearchParams({ courseProgram });
  const request = createRequestSignal(signal, CATALOG_PLAN_REQUEST_TIMEOUT_MS);
  try {
    const response = await fetch(`${API_BASE_URL}/collections/public?${searchParams.toString()}`, {
      method: "GET",
      cache: "no-store",
      signal: request.signal,
    });
    if (!response.ok) throw new Error("Could not load public study plans.");
    const parsed: unknown = await response.json();
    if (!Array.isArray(parsed)) throw new Error("Invalid public study plans response.");
    return parsed as NoteCollectionSummary[];
  } finally {
    request.clear();
  }
}

export async function getServerPublicStudyPlansByCoursePrograms(
  coursePrograms: readonly string[],
): Promise<NoteCollectionSummary[]> {
  const normalizedCoursePrograms = Array.from(new Set(coursePrograms
    .map((courseProgram) => courseProgram.trim())
    .filter((courseProgram) => courseProgram.length > 0))).sort();
  if (normalizedCoursePrograms.length === 0) return [];
  return getCatalogCached(
    `plans:${JSON.stringify(normalizedCoursePrograms)}`,
    catalogTtlMs(CATALOG_PAGE_TTL_CAP_MS, process.env.NOTELIB_CATALOG_PAGE_TTL_MS),
    async (signal) => {
      const results = await Promise.all(
        normalizedCoursePrograms.map((courseProgram) => getServerPublicStudyPlansByCourseProgram(courseProgram, signal)),
      );
      const plansById = new Map<string, NoteCollectionSummary>();
      results.flat().forEach((plan) => plansById.set(plan.id, plan));
      return Array.from(plansById.values());
    },
    { hardCapMs: CATALOG_PAGE_TTL_CAP_MS, deadlineMs: CATALOG_PAGE_WALK_DEADLINE_MS },
  );
}
