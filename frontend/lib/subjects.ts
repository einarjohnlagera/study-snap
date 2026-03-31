export function normalizeSubject(subject: string | null | undefined): string | null {
  const value = subject?.trim();
  return value && value.length > 0 ? value : null;
}

export function getSubjectDisplayLabel(subject: string | null | undefined): string {
  return normalizeSubject(subject) ?? "General";
}

export function hasExplicitSubject(subject: string | null | undefined): boolean {
  return normalizeSubject(subject) !== null;
}
