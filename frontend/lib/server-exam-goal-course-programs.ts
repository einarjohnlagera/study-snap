import {
  EXAM_HUBS,
  EXAM_HUB_SLUGS,
  type ExamHubSlug,
} from "@/lib/exam-hub-config";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";
const EXAM_GOAL_COURSE_PROGRAMS_PATH = "/public/exam-goals/course-programs";

type ExamGoalCoursePrograms = Record<ExamHubSlug, readonly string[]>;

function fallbackCoursePrograms(): ExamGoalCoursePrograms {
  return {
    ale: EXAM_HUBS.ale.coursePrograms,
    pnle: EXAM_HUBS.pnle.coursePrograms,
    let: EXAM_HUBS.let.coursePrograms,
    cpale: EXAM_HUBS.cpale.coursePrograms,
  };
}

function parseCoursePrograms(payload: unknown): ExamGoalCoursePrograms {
  const fallback = fallbackCoursePrograms();
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    return fallback;
  }

  const values = payload as Record<string, unknown>;
  const resolvePrograms = (slug: ExamHubSlug) => {
    const coursePrograms = values[slug];
    const isValid = Array.isArray(coursePrograms)
      && coursePrograms.length > 0
      && coursePrograms.every((value) => typeof value === "string" && value.trim().length > 0);
    return isValid ? coursePrograms : fallback[slug];
  };
  return {
    ale: resolvePrograms("ale"),
    pnle: resolvePrograms("pnle"),
    let: resolvePrograms("let"),
    cpale: resolvePrograms("cpale"),
  };
}

async function getServerExamGoalCourseProgramMap(): Promise<ExamGoalCoursePrograms> {
  try {
    const response = await fetch(`${API_BASE_URL}${EXAM_GOAL_COURSE_PROGRAMS_PATH}`, {
      method: "GET",
      next: { revalidate: 300 },
    });
    if (!response.ok) {
      return fallbackCoursePrograms();
    }
    return parseCoursePrograms(await response.json());
  } catch {
    return fallbackCoursePrograms();
  }
}

export async function getServerExamGoalCoursePrograms(slug: ExamHubSlug): Promise<readonly string[]> {
  const courseProgramsBySlug = await getServerExamGoalCourseProgramMap();
  return courseProgramsBySlug[slug];
}

export async function getServerExamSlugForCourseProgram(
  courseProgram: string | null | undefined,
): Promise<ExamHubSlug | null> {
  const normalizedCourseProgram = courseProgram?.trim().toLowerCase();
  if (!normalizedCourseProgram) {
    return null;
  }

  const courseProgramsBySlug = await getServerExamGoalCourseProgramMap();
  return EXAM_HUB_SLUGS.find((slug) => (
    courseProgramsBySlug[slug].some((value) => value.trim().toLowerCase() === normalizedCourseProgram)
  )) ?? null;
}
