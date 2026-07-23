export function normalizeConceptKey(concept: string): string {
  return concept.trim().toLowerCase();
}

export function buildConceptAnchorId(concept: string): string {
  const slug = normalizeConceptKey(concept)
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  return `concept-${slug || "item"}`;
}
