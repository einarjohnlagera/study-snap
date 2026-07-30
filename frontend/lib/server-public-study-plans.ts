import { API_BASE_URL, type NoteCollectionSummary } from "@/lib/api";

async function getServerPublicStudyPlansByCourseProgram(
  courseProgram: string,
): Promise<NoteCollectionSummary[]> {
  const searchParams = new URLSearchParams({ courseProgram });
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/collections/public?${searchParams.toString()}`, {
      method: "GET",
      next: { revalidate: 300 },
    });
  } catch {
    return [];
  }
  if (!response.ok) {
    return [];
  }
  try {
    return (await response.json()) as NoteCollectionSummary[];
  } catch {
    return [];
  }
}

export async function getServerPublicStudyPlansByCoursePrograms(
  coursePrograms: readonly string[],
): Promise<NoteCollectionSummary[]> {
  const normalizedCoursePrograms = coursePrograms
    .map((courseProgram) => courseProgram.trim())
    .filter((courseProgram) => courseProgram.length > 0);
  const results = await Promise.allSettled(
    normalizedCoursePrograms.map((courseProgram) => getServerPublicStudyPlansByCourseProgram(courseProgram)),
  );
  const plansById = new Map<string, NoteCollectionSummary>();
  results.forEach((result) => {
    if (result.status === "fulfilled") {
      result.value.forEach((plan) => plansById.set(plan.id, plan));
    }
  });
  return Array.from(plansById.values());
}
