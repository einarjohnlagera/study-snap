const COPIED_QUERY_PARAM = "copied";
const GENERATE_QUERY_PARAM = "generate";
const START_QUICK_REVIEW_QUERY_PARAM = "startQuickReview";

export type PublicCopyRedirectTarget = "library" | "generate" | "quick-review";

export function buildCopiedNotePath(noteId: string, redirectTarget: PublicCopyRedirectTarget = "library") {
  const next = new URLSearchParams({ [COPIED_QUERY_PARAM]: "1" });

  if (redirectTarget === "generate" || redirectTarget === "quick-review") {
    next.set(GENERATE_QUERY_PARAM, "1");
  }
  if (redirectTarget === "quick-review") {
    next.set(START_QUICK_REVIEW_QUERY_PARAM, "1");
  }

  return `/notes/${noteId}?${next.toString()}`;
}

export function buildPublicCopyIntentQuery(redirectTarget: PublicCopyRedirectTarget = "library") {
  const next = new URLSearchParams({ copy: "1" });
  next.set("intent", redirectTarget);
  return next.toString();
}

export const PUBLIC_NOTE_COPY_QUERY_PARAMS = {
  copied: COPIED_QUERY_PARAM,
  generate: GENERATE_QUERY_PARAM,
  startQuickReview: START_QUICK_REVIEW_QUERY_PARAM,
} as const;
