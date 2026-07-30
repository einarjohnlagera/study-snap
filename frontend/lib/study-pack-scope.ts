type StudyPackScopeCounts = Readonly<{
  keyConceptCount: number | null | undefined;
  quizCount: number | null | undefined;
}>;

export type StudyPackScope = Readonly<{
  keyConceptCount: number;
  quizCount: number;
  reviewTimeMinutes: number;
}>;

function getPositiveCount(count: number | null | undefined): number {
  return typeof count === "number" && count > 0 ? count : 0;
}

export function getStudyPackScope({keyConceptCount, quizCount}: StudyPackScopeCounts): StudyPackScope | null {
  const normalizedKeyConceptCount = getPositiveCount(keyConceptCount);
  const normalizedQuizCount = getPositiveCount(quizCount);

  if (normalizedKeyConceptCount === 0 && normalizedQuizCount === 0) {
    return null;
  }

  return {
    keyConceptCount: normalizedKeyConceptCount,
    quizCount: normalizedQuizCount,
    reviewTimeMinutes: Math.max(1, Math.round(normalizedQuizCount + normalizedKeyConceptCount * 0.5)),
  };
}

export function formatStudyPackScope(scope: StudyPackScope): string {
  const parts = [
    scope.keyConceptCount > 0
      ? `${scope.keyConceptCount} ${scope.keyConceptCount === 1 ? "concept" : "concepts"}`
      : null,
    scope.quizCount > 0
      ? `${scope.quizCount} ${scope.quizCount === 1 ? "question" : "questions"}`
      : null,
    `~${scope.reviewTimeMinutes} min`,
  ];

  return parts.filter((part): part is string => part !== null).join(" · ");
}
