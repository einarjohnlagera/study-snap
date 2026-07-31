import {
  buildPublicLibraryUrl,
  type PublicLibraryUrlFilters,
} from "@/lib/public-library-url";

export const EXPLORE_PATH = "/explore";
export const EXPLORE_TAB_QUERY_PARAM = "tab";
export const EXPLORE_SOURCE_QUERY_PARAM = "source";

export type ExploreTab = "notes" | "review-sets";

export function resolveExploreTab(value: string | null | undefined): ExploreTab {
  return value === "notes" ? "notes" : "review-sets";
}

type BuildExploreUrlOptions = {
  tab?: ExploreTab;
  source?: string;
  filters?: PublicLibraryUrlFilters;
};

export function buildExploreUrl({
  tab,
  source,
  filters,
}: BuildExploreUrlOptions = {}): string {
  const params = new URLSearchParams();
  if (tab && tab !== "review-sets") {
    params.set(EXPLORE_TAB_QUERY_PARAM, tab);
  }
  if (source) {
    params.set(EXPLORE_SOURCE_QUERY_PARAM, source);
  }
  return buildPublicLibraryUrl(filters ?? {}, params, EXPLORE_PATH);
}
