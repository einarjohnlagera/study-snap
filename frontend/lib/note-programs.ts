/**
 * The read semantics every discovery surface uses, in one place: joined catalog programs first when a
 * note has any, otherwise its personal free-text program.
 *
 * Mirrors the backend's `NoteEffectivePrograms` and the SQL discovery already runs. A curator-authored
 * note carries a null `courseProgram` by definition (ADR-001), so any client reading that string alone
 * sees a curated note as programme-less — the shape behind findings M3 and M4.
 */
export function resolveEffectivePrograms(
  applicablePrograms: string[] | null | undefined,
  courseProgram: string | null | undefined,
): string[] {
  const joined = Array.isArray(applicablePrograms)
    ? [...new Set(applicablePrograms.map((program) => program?.trim()).filter(Boolean))] as string[]
    : [];
  if (joined.length > 0) {
    return joined;
  }
  const legacy = courseProgram?.trim();
  return legacy ? [legacy] : [];
}

/**
 * True when two notes share at least one program. Used to offer program-matched sources rather than a
 * learner's entire library — a note applicable to several programs matches on any of them.
 */
export function sharesAnyProgram(
  left: readonly string[],
  right: readonly string[],
): boolean {
  if (left.length === 0 || right.length === 0) {
    return false;
  }
  const lookup = new Set(left);
  return right.some((program) => lookup.has(program));
}
