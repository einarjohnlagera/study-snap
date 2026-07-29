import type { CompanionContent } from "@/lib/api";

export const ASK_COMPANION_DRAFT_QUERY_PARAM = "askCompanionDraft";

function hasText(value: string | null | undefined): boolean {
  return Boolean(value?.trim());
}

export function hasRenderableCompanionContent(companion: CompanionContent | null): boolean {
  if (!companion) {
    return false;
  }
  return Boolean(
    hasText(companion.overview)
    || hasText(companion.studyStrategy)
    || hasText(companion.commonMistakes)
    || hasText(companion.resources)
    || (companion.faq ?? []).some((item) => hasText(item.question) || hasText(item.answer))
    || (companion.mentorTips ?? []).some((tip) => hasText(tip.title) || hasText(tip.body)),
  );
}

export function buildAskCompanionDraft(concept: string): string {
  return `Can you explain ${concept.trim()} a different way?`;
}

export function buildAskCompanionCollectionHref(collectionId: string, concept: string): string {
  const searchParams = new URLSearchParams({
    [ASK_COMPANION_DRAFT_QUERY_PARAM]: buildAskCompanionDraft(concept),
  });
  return `/collections/${encodeURIComponent(collectionId)}?${searchParams.toString()}`;
}
