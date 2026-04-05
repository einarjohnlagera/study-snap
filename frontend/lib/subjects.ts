export function normalizeSubject(subject: string | null | undefined): string | null {
  const value = subject?.trim();
  if (!value || value.length === 0) {
    return null;
  }

  const normalized = value.replace(/\s*[-–—]\s*/g, " – ").replace(/\s+/g, " ").trim();
  return normalized.length > 0 ? normalized : null;
}

export function getSubjectDisplayLabel(subject: string | null | undefined): string {
  return normalizeSubject(subject) ?? "General";
}

export function hasExplicitSubject(subject: string | null | undefined): boolean {
  return normalizeSubject(subject) !== null;
}
