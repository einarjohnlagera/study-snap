"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowUpDown, CheckCircle2, ChevronDown, Filter, Loader2, X } from "lucide-react";
import { useRouteProgress } from "@/components/navigation/route-progress-provider";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { AppModal } from "@/components/ui/app-modal";
import { SharedNoteCard } from "@/components/notes/shared-note-card";
import { PageHeader } from "@/components/page-header";
import { LibrarySheetModal } from "@/components/notes/library-sheet-modal";
import { NoteStateBadge } from "@/components/notes/note-state-badge";
import { NoteQualityBadges } from "@/components/notes/note-quality-badge";
import { PublicLibraryCopyAction } from "@/components/notes/public-library-copy-action";
import { PublicLibraryLikeAction } from "@/components/notes/public-library-like-action";
import { ResponsiveActionButton } from "@/components/ui/action-button";
import { GuidanceTip } from "@/components/ui/guidance-tip";
import { Skeleton } from "@/components/ui/skeleton";
import { ToastMessage } from "@/components/ui/toast-message";
import { getAuthUser } from "@/lib/auth";
import { PublicLibraryDiscoveryFeedbackPrompt } from "@/components/feedback/public-library-discovery-feedback-prompt";
import { markPublicLibraryNoteAdoptedThisSession } from "@/lib/early-lifecycle-feedback-signals";
import { buildExploreUrl } from "@/lib/explore-url";
import {
  listCoursePrograms,
  listNotes,
  listPublicLibraryDiscoverySections,
  listPublicNotes,
  listPublicStudyPlans,
  listSubjects,
  listTags,
  type NoteCollectionSummary,
  type NoteListItemResponse,
  type PublicLibraryDiscoverySectionsResponse,
} from "@/lib/api";
import { normalizeCourseProgram } from "@/lib/learning-profile";
import { resolvePublicNoteAuthorMeta } from "@/lib/public-note-author";
import { buildPublicCreatorOrProfilePath, buildPublicLibraryNotePath } from "@/lib/public-note-path";
import { getBrowsingCardClassName } from "@/lib/clickable-card";
import { buildCopiedNotePath } from "@/lib/public-note-copy";
import {
  buildPublicLibraryUrl,
  PUBLIC_LIBRARY_PATH,
  parsePublicLibraryFilters,
  resolvePublicLibraryValueBySlug,
  resolvePublicLibraryValuesBySlug,
  savePublicLibraryReturnUrl,
  slugifyPublicLibraryFilterValue,
  type PublicLibraryDiscoveryView,
  type PublicLibraryUrlFilters,
  type PublicLibrarySortQuery,
} from "@/lib/public-library-url";
import {
  getNoteTargetProfileLabel,
  NOTE_TARGET_PROFILE_ALL,
  PUBLIC_NOTE_TARGET_PROFILE_TYPES,
  type NoteTargetProfileFilter,
} from "@/lib/note-target-profile";

const ALL_COURSE_PROGRAMS = "__ALL_COURSE_PROGRAMS__";
const ALL_SUBJECTS = "__ALL_SUBJECTS__";
const PUBLIC_LIBRARY_COURSE_PROGRAM_CTA_KEY = "notelib_public_library_cp_cta_dismissed";
const PUBLIC_LIBRARY_SPARSE_AUDIENCE_THRESHOLD = 10;
const FEATURED_NOTES_LIMIT = 3;
const POPULAR_NOTES_LIMIT = 5;
const RECENT_NOTES_LIMIT = 5;
const PUBLIC_LIBRARY_PAGE_SIZE = 20;
const POPULAR_TAG_LIMIT_MOBILE = 4;
const POPULAR_TAG_LIMIT_DESKTOP = 6;
const COURSE_PROGRAM_CHIP_LIMIT = 6;
const BROWSE_ALL_LABEL = "Browse all";
const TAG_SELECTOR_TITLE = "Select tags";
const COPY_SUCCESS_MODAL_TITLE = "Copied to your library";
const COPY_SUCCESS_BODY_LINE_ONE = "The note and its Study Pack are now in your library — open it to read, quiz yourself, and track your progress.";
const MODAL_VIEW_NOTE_LABEL = "View Note";
const MOBILE_SUCCESS_SHEET_MEDIA_QUERY = "(max-width: 639px)";
const SHARE_PUBLIC_LIBRARY_LABEL = "Share this list";
const SHARE_PUBLIC_LIBRARY_COPY_ERROR = "Could not copy the public library link.";
const SHARE_LINK_COPIED_MESSAGE = "Link copied";
const PUBLIC_LIBRARY_SEARCH_DEBOUNCE_MS = 250;
const PUBLISHED_STUDY_PLANS_PATH = "/collections/published";
const PUBLISHED_STUDY_PLANS_PATH_FROM_LIBRARY = `${PUBLISHED_STUDY_PLANS_PATH}?ref=/public/library`;
const EXPLORE_REVIEW_SETS_PATH = buildExploreUrl({ tab: "review-sets" });
const TEXT_LINK_CLASS_NAME = "shrink-0 text-xs font-medium text-blue-700 hover:text-blue-800 dark:text-blue-300 dark:hover:text-blue-200";
const SCROLL_RAIL_FADE_CLASS_NAME = "[mask-image:linear-gradient(to_right,black_85%,transparent_100%)]";
const EMPTY_FACET_COUNTS = new Map<string, number>();

type PublicLibrarySortOption =
  | "RECOMMENDED"
  | "NEWEST"
  | "MOST_COPIED"
  | "MOST_VIEWED"
  | "TITLE_ASC";

type PublicLibrarySourceFilter = "BY_YOU" | "OFFICIAL" | "COMMUNITY";
type PublicLibraryFilterKey = "audience" | "courseProgram" | "ready" | "search" | "source" | "subject" | "tags";

const PUBLIC_SORT_LABELS: Record<PublicLibrarySortOption, string> = {
  RECOMMENDED: "Recommended",
  NEWEST: "Newest",
  MOST_COPIED: "Most Copied",
  MOST_VIEWED: "Most Viewed",
  TITLE_ASC: "Title A-Z",
};

const DISCOVERY_SECTION_COPY: Record<PublicLibraryDiscoveryView, {
  title: string;
  description: string;
}> = {
  featured: {
    title: "⭐ Featured Notes",
    description: "Browse the full featured ranking without losing the Public Library discovery layout.",
  },
  popular: {
    title: "🔥 Most Popular",
    description: "Browse the most copied and most viewed public notes in one dedicated section view.",
  },
  recent: {
    title: "🆕 Recently Added",
    description: "Browse the newest public notes without the rest of the homepage sections competing for space.",
  },
};

function normalizeTags(tags: string[] | null | undefined): string[] {
  if (!Array.isArray(tags)) {
    return [];
  }
  return tags
    .map((tag) => tag?.trim())
    .filter((tag): tag is string => Boolean(tag && tag.length > 0));
}

function resolveDiscoveryView(value: string | null): PublicLibraryDiscoveryView | null {
  if (value === "featured" || value === "popular" || value === "recent") {
    return value;
  }
  return null;
}

function hasUrlFilterCriteria(filters: PublicLibraryUrlFilters): boolean {
  return Boolean(
    filters.audience
    || filters.courseProgram
    || filters.creator
    || filters.search
    || filters.subject
    || (filters.tags?.length ?? 0) > 0,
  );
}

function resolveSortOption(
  sort: PublicLibrarySortQuery | null,
  defaultsToRecommended: boolean,
): PublicLibrarySortOption {
  switch (sort) {
    case "recommended":
      return "RECOMMENDED";
    case "most_copied":
    case "copied":
      return "MOST_COPIED";
    case "title":
      return "TITLE_ASC";
    case "views":
      return "MOST_VIEWED";
    case "popular":
      return "MOST_COPIED";
    case "recent":
      return "NEWEST";
    default:
      return defaultsToRecommended ? "RECOMMENDED" : "NEWEST";
  }
}

function resolveSortQuery(sort: PublicLibrarySortOption): PublicLibrarySortQuery | null {
  switch (sort) {
    case "RECOMMENDED":
      return "recommended";
    case "MOST_COPIED":
      return "most_copied";
    case "MOST_VIEWED":
      return "views";
    case "TITLE_ASC":
      return "title";
    case "NEWEST":
    default:
      return "recent";
  }
}

function getComboboxItemClassName(isSelected: boolean) {
  return `w-full px-3 py-2.5 text-left text-sm transition-colors ${
    isSelected
      ? "bg-blue-500/10 font-medium text-blue-700 dark:text-blue-300"
      : "text-foreground hover:bg-highlight"
  }`;
}

function getFilterChipClassName(isSelected: boolean) {
  return `motion-pressable motion-lift shrink-0 rounded-full border px-3 py-1.5 text-sm font-medium transition-colors ${
    isSelected
      ? "border-blue-600 bg-blue-600 text-white dark:border-blue-400 dark:bg-blue-500 dark:text-slate-950"
      : "border-border bg-background text-foreground/75 hover:bg-highlight active:bg-highlight-strong"
  }`;
}

function getScrollRailClassName() {
  return "flex flex-nowrap gap-2 overflow-x-auto pb-1 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden";
}

function getFadedScrollRailClassName() {
  return `${getScrollRailClassName()} ${SCROLL_RAIL_FADE_CLASS_NAME}`;
}

function updateRecentValues(previous: string[], values: string[]) {
  const next = [...previous];
  for (const value of values) {
    const existingIndex = next.indexOf(value);
    if (existingIndex >= 0) {
      next.splice(existingIndex, 1);
    }
    next.unshift(value);
  }
  return next.slice(0, 8);
}

function buildPriorityComparator(recentValues: string[], counts: Map<string, number>) {
  return (left: string, right: string) => {
    const leftRecentIndex = recentValues.indexOf(left);
    const rightRecentIndex = recentValues.indexOf(right);
    const leftIsRecent = leftRecentIndex >= 0;
    const rightIsRecent = rightRecentIndex >= 0;

    if (leftIsRecent || rightIsRecent) {
      if (leftIsRecent && rightIsRecent && leftRecentIndex !== rightRecentIndex) {
        return leftRecentIndex - rightRecentIndex;
      }
      if (leftIsRecent) {
        return -1;
      }
      if (rightIsRecent) {
        return 1;
      }
    }

    const countDiff = (counts.get(right) ?? 0) - (counts.get(left) ?? 0);
    if (countDiff !== 0) {
      return countDiff;
    }

    return left.localeCompare(right);
  };
}

function resolveAuthorBadge(
  item: Pick<NoteListItemResponse, "ownerUserId" | "authorDisplayName" | "authorUsername" | "isCurrentUser" | "isOfficialAuthor">,
  currentUserId: string | null,
  currentUsername: string | null,
) {
  const authorMeta = resolvePublicNoteAuthorMeta({
    ownerUserId: item.ownerUserId,
    currentUserId,
    authorDisplayName: item.authorDisplayName,
    authorUsername: item.authorUsername,
    isCurrentUser: isViewerAuthor(item, currentUserId, currentUsername),
    isOfficialAuthor: item.isOfficialAuthor,
  });

  if (authorMeta.label === "By You") {
    return {
      label: authorMeta.label,
      className: "text-emerald-700 dark:text-emerald-300",
      showOfficialBadge: false,
    };
  }
  if (authorMeta.showOfficialBadge) {
    return {
      label: authorMeta.label,
      className: "text-blue-700 dark:text-blue-300",
      showOfficialBadge: true,
    };
  }
  return {
    label: authorMeta.label,
    className: "text-foreground/75",
    showOfficialBadge: false,
  };
}

function isViewerAuthor(
  item: Pick<NoteListItemResponse, "ownerUserId" | "authorUsername" | "isCurrentUser">,
  currentUserId: string | null,
  currentUsername: string | null,
) {
  const normalizedAuthorUsername = item.authorUsername?.trim().toLowerCase();
  const normalizedCurrentUsername = currentUsername?.trim().toLowerCase();
  return Boolean(item.isCurrentUser)
    || (item.ownerUserId !== null && item.ownerUserId === currentUserId)
    || (normalizedAuthorUsername !== undefined
      && normalizedAuthorUsername.length > 0
      && normalizedAuthorUsername === normalizedCurrentUsername);
}

interface PublicNoteCardProps {
  item: NoteListItemResponse;
  currentUserId: string | null;
  currentUsername: string | null;
  onNavigate: (path: string) => void;
  existingCopyNoteId?: string | null;
  onCopySuccess: (payload: { copiedNoteId: string; sourceNoteId: string; studyPackStatus: NoteListItemResponse["studyPackStatus"] }) => void;
  onLikeSuccess: (payload: { noteId: string; liked: boolean; likeCount: number }) => void;
}

function PublicNoteCard({
  item,
  currentUserId,
  currentUsername,
  onNavigate,
  existingCopyNoteId = null,
  onCopySuccess,
  onLikeSuccess,
}: Readonly<PublicNoteCardProps>) {
  const itemTags = normalizeTags(item.tags);
  const authorBadge = resolveAuthorBadge(item, currentUserId, currentUsername);
  const path = buildPublicLibraryNotePath({ subject: item.subject, title: item.title });
  const isOwner = isViewerAuthor(item, currentUserId, currentUsername);

  return (
    <Card
      role="link"
      tabIndex={0}
      onClick={() => onNavigate(path)}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onNavigate(path);
        }
      }}
      className={getBrowsingCardClassName("flex h-full flex-col justify-between space-y-3 p-4 sm:p-5")}
    >
      <SharedNoteCard
        title={item.title}
        courseProgram={normalizeCourseProgram(item.courseProgram)}
        applicablePrograms={item.applicablePrograms}
        subject={item.subject}
        tags={itemTags}
        contentPreview={item.contentPreview}
        summaryPreview={item.summaryPreview}
        copyCount={typeof item.copyCount === "number" && item.copyCount > 0 ? item.copyCount : null}
        viewCount={typeof item.viewCount === "number" && item.viewCount > 0 ? item.viewCount : null}
        metricsTrailing={(
          <PublicLibraryLikeAction
            noteId={item.id}
            likeCount={item.likeCount ?? 0}
            liked={item.likedByCurrentUser}
            onLikeSuccess={({ liked, likeCount }) => onLikeSuccess({
              noteId: item.id,
              liked,
              likeCount,
            })}
          />
        )}
        stateBadge={<NoteStateBadge status={item.studyPackStatus} />}
        metadataBadges={(
          <NoteQualityBadges
            copyCount={item.copyCount}
            likeCount={item.likeCount}
            viewCount={item.viewCount}
          />
        )}
        tagDisplayLimit={4}
        previewLines={2}
        footer={(
          <div className="flex flex-wrap items-start justify-between gap-3 text-xs text-foreground/65">
            <div className="flex min-w-0 flex-wrap items-center gap-2">
              {item.ownerUserId || item.authorUsername ? (
                <Link
                  href={buildPublicCreatorOrProfilePath({ userId: item.ownerUserId, username: item.authorUsername })}
                  onClick={(event) => event.stopPropagation()}
                  onKeyDown={(event) => event.stopPropagation()}
                  className={`font-medium hover:underline ${authorBadge.className}`}
                >
                  {authorBadge.label}
                </Link>
              ) : (
                <span className={authorBadge.className}>{authorBadge.label}</span>
              )}
              {authorBadge.showOfficialBadge ? (
                <span className="inline-flex items-center rounded-full border border-blue-500/35 bg-blue-500/10 px-2 py-1 text-[11px] font-medium text-blue-700 dark:text-blue-300">
                  Official
                </span>
              ) : null}
            </div>
            <div className="shrink-0">
              <PublicLibraryCopyAction
                noteId={item.id}
                isOwner={isOwner}
                existingCopyNoteId={existingCopyNoteId}
                onCopySuccess={({ copiedNoteId, studyPackStatus }) => onCopySuccess({
                  copiedNoteId,
                  sourceNoteId: item.id,
                  studyPackStatus,
                })}
              />
            </div>
          </div>
        )}
      />
    </Card>
  );
}

interface PublicLibraryDiscoverySectionProps {
  title: string;
  description?: string;
  items: NoteListItemResponse[];
  currentUserId: string | null;
  currentUsername: string | null;
  onNavigate: (path: string) => void;
  onViewMore: () => void;
  copiedNoteIdsBySourceId: Record<string, string>;
  onCopySuccess: (payload: { copiedNoteId: string; sourceNoteId: string; studyPackStatus: NoteListItemResponse["studyPackStatus"] }) => void;
  onLikeSuccess: (payload: { noteId: string; liked: boolean; likeCount: number }) => void;
}

function PublicLibraryDiscoverySection({
  title,
  description,
  items,
  currentUserId,
  currentUsername,
  onNavigate,
  onViewMore,
  copiedNoteIdsBySourceId,
  onCopySuccess,
  onLikeSuccess,
}: Readonly<PublicLibraryDiscoverySectionProps>) {
  if (items.length === 0) {
    return null;
  }

  return (
    <section aria-label={title} className="space-y-3">
      <div className="flex items-start justify-between gap-3">
        <div className="space-y-1">
          <h2 className="text-base font-semibold">{title}</h2>
          {description ? (
            <p className="text-xs text-foreground/55">{description}</p>
          ) : null}
        </div>
        <button
          type="button"
          onClick={onViewMore}
          className="shrink-0 text-sm font-medium text-blue-700 transition-colors hover:text-blue-800 hover:underline dark:text-blue-300 dark:hover:text-blue-200"
        >
          View More
        </button>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {items.map((item) => (
          <PublicNoteCard
            key={item.id}
            item={item}
            currentUserId={currentUserId}
            currentUsername={currentUsername}
            onNavigate={onNavigate}
            existingCopyNoteId={copiedNoteIdsBySourceId[item.id] ?? null}
            onCopySuccess={onCopySuccess}
            onLikeSuccess={onLikeSuccess}
          />
        ))}
      </div>
    </section>
  );
}

type PublicLibraryPageClientProps = {
  basePath?: string;
  embedded?: boolean;
};

export function PublicLibraryPageClient({
  basePath = PUBLIC_LIBRARY_PATH,
  embedded = false,
}: Readonly<PublicLibraryPageClientProps> = {}) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const searchParamsKey = searchParams.toString();
  const parsedUrlFilters = useMemo(() => parsePublicLibraryFilters(searchParamsKey), [searchParamsKey]);
  const [currentUserId, setCurrentUserId] = useState<string | null>(() => getAuthUser()?.id ?? null);
  const [currentUsername, setCurrentUsername] = useState<string | null>(() => getAuthUser()?.username ?? null);
  const [selectedTargetProfile, setSelectedTargetProfile] = useState<NoteTargetProfileFilter>(NOTE_TARGET_PROFILE_ALL);
  const [items, setItems] = useState<NoteListItemResponse[]>([]);
  const [totalMatching, setTotalMatching] = useState<number>(0);
  const [hasMore, setHasMore] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);
  const [loadMoreLoading, setLoadMoreLoading] = useState(false);
  const [loadMoreError, setLoadMoreError] = useState<string | null>(null);
  const [copiedNoteIdsBySourceId, setCopiedNoteIdsBySourceId] = useState<Record<string, string>>({});
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedCourseProgram, setSelectedCourseProgram] = useState<string>(ALL_COURSE_PROGRAMS);
  const [courseProgramDraft, setCourseProgramDraft] = useState<string>(ALL_COURSE_PROGRAMS);
  const [selectedSubject, setSelectedSubject] = useState<string>(ALL_SUBJECTS);
  const [selectedSort, setSelectedSort] = useState<PublicLibrarySortOption>(() => (
    resolveSortOption(parsedUrlFilters.sort, hasUrlFilterCriteria(parsedUrlFilters))
  ));
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [tagDraft, setTagDraft] = useState<string[]>([]);
  const [selectedSourceFilters, setSelectedSourceFilters] = useState<PublicLibrarySourceFilter[]>([]);
  const [studyPackReadyOnly, setStudyPackReadyOnly] = useState(false);
  const [studyPackReadyDraft, setStudyPackReadyDraft] = useState(false);
  const [lastChangedFilter, setLastChangedFilter] = useState<PublicLibraryFilterKey | null>(null);
  const [officialPlan, setOfficialPlan] = useState<NoteCollectionSummary | null>(null);
  const [tagSearchQuery, setTagSearchQuery] = useState("");
  const [subjectSearchQuery, setSubjectSearchQuery] = useState("");
  const [courseProgramSearchQuery, setCourseProgramSearchQuery] = useState("");
  const [subjectSuggestions, setSubjectSuggestions] = useState<string[]>([]);
  const [courseProgramSuggestions, setCourseProgramSuggestions] = useState<string[]>([]);
  const [tagSuggestions, setTagSuggestions] = useState<string[]>([]);
  const [discoverySections, setDiscoverySections] = useState<PublicLibraryDiscoverySectionsResponse>({
    featured: [],
    popular: [],
    recent: [],
  });
  const [discoveryLoading, setDiscoveryLoading] = useState(false);
  const [discoveryError, setDiscoveryError] = useState<string | null>(null);
  const [filterSheetOpen, setFilterSheetOpen] = useState(false);
  const [sortSheetOpen, setSortSheetOpen] = useState(false);
  const [tagSelectorOpen, setTagSelectorOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [hasLoadedOnce, setHasLoadedOnce] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Tracks the last search term this component wrote to the URL, so the
  // URL -> input hydration effect can ignore the echo of our own debounced
  // write and never clobber characters typed after the debounce fired.
  const lastSyncedSearchRef = useRef<string | null>(null);
  const listRequestTokenRef = useRef(0);
  const discoveryRequestTokenRef = useRef(0);
  const [recentTags, setRecentTags] = useState<string[]>([]);
  const [recentSubjects, setRecentSubjects] = useState<string[]>([]);
  const [recentCoursePrograms, setRecentCoursePrograms] = useState<string[]>([]);
  const [copySuccessState, setCopySuccessState] = useState<{
    copiedNoteId: string;
    studyPackStatus: NoteListItemResponse["studyPackStatus"];
  } | null>(null);
  const [isMobileSuccessSheet, setIsMobileSuccessSheet] = useState(false);
  const [shareToastMessage, setShareToastMessage] = useState<string | null>(null);
  const [shareToastTone, setShareToastTone] = useState<"success" | "error">("success");
  // Modal draft state — staged until "Apply" is clicked
  const [audienceDraft, setAudienceDraft] = useState<NoteTargetProfileFilter>(NOTE_TARGET_PROFILE_ALL);
  const [ctaDismissed, setCtaDismissed] = useState<boolean>(() => {
    try {
      return globalThis.sessionStorage?.getItem(PUBLIC_LIBRARY_COURSE_PROGRAM_CTA_KEY) === "1";
    } catch {
      return false;
    }
  });
  const [subjectFilterDraft, setSubjectFilterDraft] = useState<string>(ALL_SUBJECTS);
  const [tagsFilterDraft, setTagsFilterDraft] = useState<string[]>([]);
  const [subjectComboOpen, setSubjectComboOpen] = useState(false);
  const [courseProgramComboOpen, setCourseProgramComboOpen] = useState(false);
  const [subjectComboTyped, setSubjectComboTyped] = useState(false);
  const [courseProgramComboTyped, setCourseProgramComboTyped] = useState(false);
  const subjectDropdownRef = useRef<HTMLDivElement>(null);
  const courseProgramDropdownRef = useRef<HTMLDivElement>(null);

  const effectiveAudience = useMemo<NoteTargetProfileFilter>(() => {
    if (parsedUrlFilters.audience) return parsedUrlFilters.audience;
    return NOTE_TARGET_PROFILE_ALL;
  }, [parsedUrlFilters.audience]);

  const activeDiscoveryView = resolveDiscoveryView(parsedUrlFilters.view);
  const effectiveSelectedSort = selectedSort === "NEWEST"
    && parsedUrlFilters.sort === null
    && (hasUrlFilterCriteria(parsedUrlFilters) || selectedSourceFilters.length > 0 || studyPackReadyOnly)
    ? "RECOMMENDED"
    : selectedSort;
  const isDiscoveryMode = !hasUrlFilterCriteria(parsedUrlFilters)
    && selectedSourceFilters.length === 0
    && !studyPackReadyOnly
    && effectiveSelectedSort === "NEWEST";
  const isSectionView = isDiscoveryMode && activeDiscoveryView !== null;

  const buildPageRequest = useCallback((page: number) => ({
    audience: effectiveAudience !== NOTE_TARGET_PROFILE_ALL ? effectiveAudience : undefined,
    courseProgram: parsedUrlFilters.courseProgram ?? undefined,
    creator: parsedUrlFilters.creator ?? undefined,
    page,
    pageSize: PUBLIC_LIBRARY_PAGE_SIZE,
    readyOnly: studyPackReadyOnly,
    search: parsedUrlFilters.search ?? undefined,
    sort: activeDiscoveryView ?? resolveSortQuery(effectiveSelectedSort) ?? undefined,
    source: selectedSourceFilters.map((filter) => filter.toLowerCase() as "by_you" | "official" | "community"),
    subject: parsedUrlFilters.subject ?? undefined,
    tags: parsedUrlFilters.tags,
  }), [activeDiscoveryView, effectiveAudience, effectiveSelectedSort, parsedUrlFilters.courseProgram, parsedUrlFilters.creator, parsedUrlFilters.search, parsedUrlFilters.subject, parsedUrlFilters.tags, selectedSourceFilters, studyPackReadyOnly]);

  const loadNotes = useCallback(async () => {
    const requestToken = ++listRequestTokenRef.current;
    setLoading(true);
    setLoadMoreLoading(false);
    setError(null);
    setLoadMoreError(null);
    try {
      const [notesResult, subjectsResult, courseProgramsResult, tagsResult] = await Promise.allSettled([
        listPublicNotes(buildPageRequest(0)),
        listSubjects("public"),
        listCoursePrograms("public"),
        listTags("public"),
      ]);
      if (requestToken !== listRequestTokenRef.current) {
        return;
      }
      if (notesResult.status !== "fulfilled") {
        throw notesResult.reason;
      }
      setItems(notesResult.value.items);
      setTotalMatching(notesResult.value.totalMatching ?? notesResult.value.total);
      setHasMore(notesResult.value.hasMore ?? false);
      setCurrentPage(notesResult.value.page ?? 0);
      setSubjectSuggestions(subjectsResult.status === "fulfilled" ? subjectsResult.value : []);
      setCourseProgramSuggestions(courseProgramsResult.status === "fulfilled" ? courseProgramsResult.value : []);
      setTagSuggestions(tagsResult.status === "fulfilled" ? tagsResult.value : []);
    } catch (loadError) {
      if (requestToken === listRequestTokenRef.current) {
        const message = loadError instanceof Error ? loadError.message : "Could not load public notes.";
        setError(message);
      }
    } finally {
      if (requestToken === listRequestTokenRef.current) {
        setLoading(false);
        setHasLoadedOnce(true);
      }
    }
  }, [buildPageRequest]);

  useEffect(() => {
    void loadNotes();
  }, [loadNotes]);

  const loadDiscoverySections = useCallback(async () => {
    const requestToken = ++discoveryRequestTokenRef.current;
    setDiscoveryLoading(true);
    setDiscoveryError(null);
    try {
      const response = await listPublicLibraryDiscoverySections({
        audience: effectiveAudience !== NOTE_TARGET_PROFILE_ALL ? effectiveAudience : undefined,
      });
      if (requestToken === discoveryRequestTokenRef.current) {
        setDiscoverySections(response);
      }
    } catch (loadError) {
      if (requestToken === discoveryRequestTokenRef.current) {
        setDiscoverySections({ featured: [], popular: [], recent: [] });
        setDiscoveryError(loadError instanceof Error ? loadError.message : "Could not load discovery sections.");
      }
    } finally {
      if (requestToken === discoveryRequestTokenRef.current) {
        setDiscoveryLoading(false);
      }
    }
  }, [effectiveAudience]);

  useEffect(() => {
    if (isDiscoveryMode && activeDiscoveryView === null) {
      void loadDiscoverySections();
      return;
    }
    discoveryRequestTokenRef.current += 1;
  }, [activeDiscoveryView, isDiscoveryMode, loadDiscoverySections]);

  const handleLoadMore = useCallback(async () => {
    const requestToken = ++listRequestTokenRef.current;
    const nextPage = currentPage + 1;
    setLoadMoreLoading(true);
    setLoadMoreError(null);
    try {
      const response = await listPublicNotes(buildPageRequest(nextPage));
      if (requestToken !== listRequestTokenRef.current) {
        return;
      }
      setItems((previous) => {
        const existingIds = new Set(previous.map((item) => item.id));
        return [...previous, ...response.items.filter((item) => !existingIds.has(item.id))];
      });
      setTotalMatching(response.totalMatching ?? response.total);
      setHasMore(response.hasMore ?? false);
      setCurrentPage(response.page ?? nextPage);
    } catch (loadError) {
      if (requestToken === listRequestTokenRef.current) {
        setLoadMoreError(loadError instanceof Error ? loadError.message : "Could not load more public notes.");
      }
    } finally {
      if (requestToken === listRequestTokenRef.current) {
        setLoadMoreLoading(false);
      }
    }
  }, [buildPageRequest, currentPage]);

  useEffect(() => {
    const syncAuth = () => {
      const authUser = getAuthUser();
      setCurrentUserId(authUser?.id ?? null);
      setCurrentUsername(authUser?.username ?? null);
    };

    syncAuth();
    globalThis.addEventListener("studysnap-auth-change", syncAuth);
    return () => {
      globalThis.removeEventListener("studysnap-auth-change", syncAuth);
    };
  }, []);

  useEffect(() => {
    if (globalThis.window === undefined || typeof globalThis.matchMedia !== "function") {
      return;
    }

    const mediaQuery = globalThis.matchMedia(MOBILE_SUCCESS_SHEET_MEDIA_QUERY);
    const syncMatches = () => {
      setIsMobileSuccessSheet(mediaQuery.matches);
    };

    syncMatches();
    mediaQuery.addEventListener("change", syncMatches);
    return () => {
      mediaQuery.removeEventListener("change", syncMatches);
    };
  }, []);

  const loadCopiedNotes = useCallback(async () => {
    if (!currentUserId) {
      setCopiedNoteIdsBySourceId({});
      return;
    }

    try {
      const mine = await listNotes();
      const next: Record<string, string> = {};
      for (const item of mine) {
        if (item.copiedFromPublic && item.copiedFromNoteId) {
          next[item.copiedFromNoteId] = item.id;
        }
      }
      setCopiedNoteIdsBySourceId(next);
    } catch {
      setCopiedNoteIdsBySourceId({});
    }
  }, [currentUserId]);

  useEffect(() => {
    void loadCopiedNotes();
  }, [loadCopiedNotes]);

  const handleCopySuccess = useCallback((payload: { copiedNoteId: string; sourceNoteId: string; studyPackStatus: NoteListItemResponse["studyPackStatus"] }) => {
    markPublicLibraryNoteAdoptedThisSession();
    setCopiedNoteIdsBySourceId((previous) => ({
      ...previous,
      [payload.sourceNoteId]: payload.copiedNoteId,
    }));
    setCopySuccessState({
      copiedNoteId: payload.copiedNoteId,
      studyPackStatus: payload.studyPackStatus,
    });
  }, []);

  const handleLikeSuccess = useCallback((payload: { noteId: string; liked: boolean; likeCount: number }) => {
    const updateItem = (item: NoteListItemResponse) => (
      item.id === payload.noteId
        ? {
            ...item,
            likeCount: payload.likeCount,
            likedByCurrentUser: payload.liked,
          }
        : item
    );
    setItems((previous) => previous.map((item) => (
      updateItem(item)
    )));
    setDiscoverySections((previous) => ({
      featured: previous.featured.map(updateItem),
      popular: previous.popular.map(updateItem),
      recent: previous.recent.map(updateItem),
    }));
  }, []);

  const replacePublicLibraryFilters = useCallback((nextFilters: PublicLibraryUrlFilters) => {
    const currentUrl = buildPublicLibraryUrl(parsedUrlFilters, searchParamsKey, basePath);
    const nextUrl = buildPublicLibraryUrl(nextFilters, searchParamsKey, basePath);
    if (currentUrl !== nextUrl) {
      router.replace(nextUrl, { scroll: false });
    }
  }, [basePath, parsedUrlFilters, router, searchParamsKey]);

  const availableSubjects = subjectSuggestions;
  const availableCoursePrograms = courseProgramSuggestions;
  const availableTags = tagSuggestions;

  useEffect(() => {
    setSelectedTargetProfile(effectiveAudience);
    setSelectedSort(resolveSortOption(parsedUrlFilters.sort, hasUrlFilterCriteria(parsedUrlFilters)));

    const resolvedCourseProgram = parsedUrlFilters.courseProgram
      ? resolvePublicLibraryValueBySlug(availableCoursePrograms, parsedUrlFilters.courseProgram)
      : null;
    const nextSelectedCourseProgram = resolvedCourseProgram ?? ALL_COURSE_PROGRAMS;
    setSelectedCourseProgram(nextSelectedCourseProgram);
    setCourseProgramDraft(nextSelectedCourseProgram);

    const resolvedSubject = parsedUrlFilters.subject
      ? resolvePublicLibraryValueBySlug(availableSubjects, parsedUrlFilters.subject)
      : null;
    const nextSelectedSubject = resolvedSubject ?? ALL_SUBJECTS;
    setSelectedSubject(nextSelectedSubject);

    const resolvedTags = resolvePublicLibraryValuesBySlug(availableTags, parsedUrlFilters.tags);
    setSelectedTags(resolvedTags);
    setTagDraft(resolvedTags);
  }, [
    availableCoursePrograms,
    availableSubjects,
    availableTags,
    effectiveAudience,
    parsedUrlFilters,
    parsedUrlFilters.courseProgram,
    parsedUrlFilters.sort,
    parsedUrlFilters.subject,
    parsedUrlFilters.tags,
  ]);

  const activeCourseProgram = selectedCourseProgram === ALL_COURSE_PROGRAMS
    ? null
    : normalizeCourseProgram(selectedCourseProgram);

  useEffect(() => {
    if (!activeCourseProgram) {
      setOfficialPlan(null);
      return;
    }

    let cancelled = false;
    setOfficialPlan(null);
    void listPublicStudyPlans({ courseProgram: activeCourseProgram })
      .then((plans) => {
        if (!cancelled) {
          // Match the Dashboard recommendation convention: use the API's first matching plan.
          setOfficialPlan(plans[0] ?? null);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setOfficialPlan(null);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [activeCourseProgram]);

  // Hydrate the search input from the URL only on genuine external changes
  // (initial load, back/forward) — keyed on the search term alone so a refetch
  // resolving (which changes available subject/tag lists) cannot reset the
  // input mid-typing. The echo guard skips our own debounced URL writes.
  useEffect(() => {
    const urlSearch = parsedUrlFilters.search ?? null;
    if (urlSearch === lastSyncedSearchRef.current) {
      return;
    }
    setSearchQuery(parsedUrlFilters.search ?? "");
  }, [parsedUrlFilters.search]);

  useEffect(() => {
    if (selectedCourseProgram !== ALL_COURSE_PROGRAMS && !availableCoursePrograms.includes(selectedCourseProgram)) {
      setSelectedCourseProgram(ALL_COURSE_PROGRAMS);
    }
    if (courseProgramDraft !== ALL_COURSE_PROGRAMS && !availableCoursePrograms.includes(courseProgramDraft)) {
      setCourseProgramDraft(ALL_COURSE_PROGRAMS);
    }
  }, [availableCoursePrograms, courseProgramDraft, selectedCourseProgram]);

  useEffect(() => {
    if (selectedSubject !== ALL_SUBJECTS && !availableSubjects.includes(selectedSubject)) {
      setSelectedSubject(ALL_SUBJECTS);
    }
  }, [availableSubjects, selectedSubject]);

  useEffect(() => {
    setSelectedTags((previous) => previous.filter((tag) => availableTags.includes(tag)));
    setTagDraft((previous) => previous.filter((tag) => availableTags.includes(tag)));
  }, [availableTags]);

  useEffect(() => {
    if (tagSelectorOpen) {
      setTagDraft(selectedTags);
      setTagSearchQuery("");
    }
  }, [selectedTags, tagSelectorOpen]);

  useEffect(() => {
    if (filterSheetOpen) {
      setAudienceDraft(selectedTargetProfile);
      setSubjectFilterDraft(selectedSubject);
      setTagsFilterDraft(selectedTags);
      setCourseProgramDraft(selectedCourseProgram);
      setStudyPackReadyDraft(studyPackReadyOnly);
      setSubjectSearchQuery("");
      setCourseProgramSearchQuery("");
      setSubjectComboOpen(false);
      setCourseProgramComboOpen(false);
    }
  }, [filterSheetOpen, selectedCourseProgram, selectedSubject, selectedTags, selectedTargetProfile, studyPackReadyOnly]);

  useEffect(() => {
    const timeoutId = globalThis.setTimeout(() => {
      const nextSearch = searchQuery.trim();
      if ((nextSearch || null) === parsedUrlFilters.search) {
        return;
      }
      lastSyncedSearchRef.current = nextSearch.length > 0 ? nextSearch : null;
      replacePublicLibraryFilters({
        ...parsedUrlFilters,
        search: nextSearch.length > 0 ? nextSearch : null,
        view: null,
      });
    }, PUBLIC_LIBRARY_SEARCH_DEBOUNCE_MS);

    return () => {
      globalThis.clearTimeout(timeoutId);
    };
  }, [parsedUrlFilters, replacePublicLibraryFilters, searchQuery]);

  useEffect(() => {
    if (!shareToastMessage) {
      return;
    }

    const timeoutId = globalThis.setTimeout(() => {
      setShareToastMessage(null);
    }, 2200);

    return () => {
      globalThis.clearTimeout(timeoutId);
    };
  }, [shareToastMessage]);

  useEffect(() => {
    if (!subjectComboOpen) return;
    const id = globalThis.setTimeout(() => {
      subjectDropdownRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }, 0);
    return () => globalThis.clearTimeout(id);
  }, [subjectComboOpen]);

  useEffect(() => {
    if (!courseProgramComboOpen) return;
    const id = globalThis.setTimeout(() => {
      courseProgramDropdownRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }, 0);
    return () => globalThis.clearTimeout(id);
  }, [courseProgramComboOpen]);

  const toggleDraftTag = useCallback((tag: string) => {
    setTagDraft((previous) => (
      previous.includes(tag)
        ? previous.filter((selectedTag) => selectedTag !== tag)
        : [...previous, tag]
    ));
  }, []);

  const toggleSourceFilter = useCallback((filter: PublicLibrarySourceFilter) => {
    setLastChangedFilter("source");
    setSelectedSourceFilters((previous) => (
      previous.includes(filter)
        ? previous.filter((selectedFilter) => selectedFilter !== filter)
        : [...previous, filter]
    ));
  }, []);

  const clearFilters = useCallback(() => {
    setSearchQuery("");
    setSelectedCourseProgram(ALL_COURSE_PROGRAMS);
    setCourseProgramDraft(ALL_COURSE_PROGRAMS);
    setSelectedSubject(ALL_SUBJECTS);
    setSubjectFilterDraft(ALL_SUBJECTS);
    setSelectedTags([]);
    setTagDraft([]);
    setTagsFilterDraft([]);
    setSelectedSourceFilters([]);
    setStudyPackReadyOnly(false);
    setStudyPackReadyDraft(false);
    setLastChangedFilter(null);
    setSelectedSort("NEWEST");
    setAudienceDraft(NOTE_TARGET_PROFILE_ALL);
    setCourseProgramSearchQuery("");
    setSubjectSearchQuery("");
    setTagSearchQuery("");
    replacePublicLibraryFilters({
      audience: null,
      courseProgram: null,
      creator: null,
      search: null,
      sort: null,
      subject: null,
      tags: [],
      view: null,
    });
  }, [replacePublicLibraryFilters]);

  const dropMostRecentFilter = useCallback(() => {
    switch (lastChangedFilter) {
      case "audience":
        setSelectedTargetProfile(NOTE_TARGET_PROFILE_ALL);
        replacePublicLibraryFilters({ ...parsedUrlFilters, audience: null, view: null });
        break;
      case "courseProgram":
        setSelectedCourseProgram(ALL_COURSE_PROGRAMS);
        replacePublicLibraryFilters({ ...parsedUrlFilters, courseProgram: null, view: null });
        break;
      case "ready":
        setStudyPackReadyOnly(false);
        break;
      case "search":
        setSearchQuery("");
        replacePublicLibraryFilters({ ...parsedUrlFilters, search: null, view: null });
        break;
      case "source":
        setSelectedSourceFilters([]);
        break;
      case "subject":
        setSelectedSubject(ALL_SUBJECTS);
        replacePublicLibraryFilters({ ...parsedUrlFilters, subject: null, view: null });
        break;
      case "tags":
        setSelectedTags([]);
        replacePublicLibraryFilters({ ...parsedUrlFilters, tags: [], view: null });
        break;
      default:
        clearFilters();
        return;
    }
    setLastChangedFilter(null);
  }, [clearFilters, lastChangedFilter, parsedUrlFilters, replacePublicLibraryFilters]);

  const handleCtaDismiss = useCallback(() => {
    try {
      globalThis.sessionStorage?.setItem(PUBLIC_LIBRARY_COURSE_PROGRAM_CTA_KEY, "1");
    } catch {
      // sessionStorage unavailable (e.g. private browsing with storage blocked)
    }
    setCtaDismissed(true);
  }, []);

  const applyCourseProgramChip = useCallback((courseProgram: string) => {
    setLastChangedFilter("courseProgram");
    setSelectedCourseProgram(courseProgram);
    setCourseProgramDraft(courseProgram);
    setRecentCoursePrograms((previous) => updateRecentValues(previous, [courseProgram]));
    replacePublicLibraryFilters({
      ...parsedUrlFilters,
      courseProgram: slugifyPublicLibraryFilterValue(courseProgram),
      view: null,
    });
  }, [parsedUrlFilters, replacePublicLibraryFilters]);

  const applyModalFilters = useCallback(() => {
    const nextAudience = audienceDraft !== NOTE_TARGET_PROFILE_ALL ? audienceDraft : null;
    const nextSubject = subjectFilterDraft !== ALL_SUBJECTS ? slugifyPublicLibraryFilterValue(subjectFilterDraft) : null;
    const nextTags = tagsFilterDraft.map((tag) => slugifyPublicLibraryFilterValue(tag));
    const nextCourseProgram = courseProgramDraft !== ALL_COURSE_PROGRAMS ? slugifyPublicLibraryFilterValue(courseProgramDraft) : null;
    const filterChanges: Array<[PublicLibraryFilterKey, boolean]> = [
      ["audience", audienceDraft !== selectedTargetProfile],
      ["courseProgram", courseProgramDraft !== selectedCourseProgram],
      ["subject", subjectFilterDraft !== selectedSubject],
      ["tags", tagsFilterDraft.join("\u0000") !== selectedTags.join("\u0000")],
      ["ready", studyPackReadyDraft !== studyPackReadyOnly],
    ];
    const latestChange = filterChanges.find(([, changed]) => changed)?.[0];
    if (latestChange) {
      setLastChangedFilter(latestChange);
    }
    setStudyPackReadyOnly(studyPackReadyDraft);

    if (subjectFilterDraft !== ALL_SUBJECTS) {
      setRecentSubjects((previous) => updateRecentValues(previous, [subjectFilterDraft]));
    }
    if (tagsFilterDraft.length > 0) {
      setRecentTags((previous) => updateRecentValues(previous, [...tagsFilterDraft].reverse()));
    }
    if (courseProgramDraft !== ALL_COURSE_PROGRAMS) {
      setRecentCoursePrograms((previous) => updateRecentValues(previous, [courseProgramDraft]));
    }

    replacePublicLibraryFilters({
      ...parsedUrlFilters,
      audience: nextAudience,
      subject: nextSubject,
      tags: nextTags,
      courseProgram: nextCourseProgram,
      view: null,
    });
    setFilterSheetOpen(false);
  }, [audienceDraft, courseProgramDraft, parsedUrlFilters, replacePublicLibraryFilters, selectedCourseProgram, selectedSubject, selectedTags, selectedTargetProfile, studyPackReadyDraft, studyPackReadyOnly, subjectFilterDraft, tagsFilterDraft]);

  const subjectPriorityComparator = useMemo(
    () => buildPriorityComparator(recentSubjects, EMPTY_FACET_COUNTS),
    [recentSubjects],
  );
  const courseProgramPriorityComparator = useMemo(
    () => buildPriorityComparator(recentCoursePrograms, EMPTY_FACET_COUNTS),
    [recentCoursePrograms],
  );
  const tagPriorityComparator = useMemo(
    () => buildPriorityComparator(recentTags, EMPTY_FACET_COUNTS),
    [recentTags],
  );

  const displayedSubjects = useMemo(() => {
    return [...availableSubjects].sort(subjectPriorityComparator);
  }, [availableSubjects, subjectPriorityComparator]);

  const filteredModalSubjects = useMemo(() => {
    const query = subjectComboTyped ? subjectSearchQuery.trim().toLowerCase() : "";
    return displayedSubjects.filter((subject) => (
      query.length === 0 || subject.toLowerCase().includes(query)
    ));
  }, [displayedSubjects, subjectComboTyped, subjectSearchQuery]);

  const displayedCoursePrograms = useMemo(() => {
    return [...availableCoursePrograms].sort(courseProgramPriorityComparator);
  }, [availableCoursePrograms, courseProgramPriorityComparator]);

  const topCoursePrograms = useMemo(
    () => displayedCoursePrograms.slice(0, COURSE_PROGRAM_CHIP_LIMIT),
    [displayedCoursePrograms],
  );

  const filteredModalCoursePrograms = useMemo(() => {
    const query = courseProgramComboTyped ? courseProgramSearchQuery.trim().toLowerCase() : "";
    return displayedCoursePrograms.filter((courseProgram) => (
      query.length === 0 || courseProgram.toLowerCase().includes(query)
    ));
  }, [courseProgramComboTyped, courseProgramSearchQuery, displayedCoursePrograms]);

  const displayedTags = useMemo(() => {
    return [...availableTags].sort(tagPriorityComparator);
  }, [availableTags, tagPriorityComparator]);

  const filteredModalTags = useMemo(() => {
    const query = tagSearchQuery.trim().toLowerCase();
    return displayedTags.filter((tag) => (
      query.length === 0 || tag.toLowerCase().includes(query)
    ));
  }, [displayedTags, tagSearchQuery]);

  const visibleTagLimit = isMobileSuccessSheet
    ? POPULAR_TAG_LIMIT_MOBILE
    : POPULAR_TAG_LIMIT_DESKTOP;

  const visiblePopularTags = useMemo(() => {
    const ordered = [
      ...tagsFilterDraft.filter((tag) => displayedTags.includes(tag)),
      ...displayedTags.filter((tag) => !tagsFilterDraft.includes(tag)),
    ];
    return Array.from(new Set(ordered)).slice(0, Math.max(visibleTagLimit, tagsFilterDraft.length));
  }, [displayedTags, tagsFilterDraft, visibleTagLimit]);

  const hasActiveFilters = searchQuery.trim().length > 0
    || selectedTargetProfile !== NOTE_TARGET_PROFILE_ALL
    || selectedCourseProgram !== ALL_COURSE_PROGRAMS
    || parsedUrlFilters.creator !== null
    || selectedSubject !== ALL_SUBJECTS
    || selectedTags.length > 0
    || selectedSourceFilters.length > 0
    || studyPackReadyOnly;
  const hasActiveUrlFilters = (parsedUrlFilters.search?.trim().length ?? 0) > 0
    || selectedTargetProfile !== NOTE_TARGET_PROFILE_ALL
    || selectedCourseProgram !== ALL_COURSE_PROGRAMS
    || parsedUrlFilters.creator !== null
    || selectedSubject !== ALL_SUBJECTS
    || selectedTags.length > 0
    || selectedSourceFilters.length > 0
    || studyPackReadyOnly;

  const featuredNotes = useMemo(
    () => discoverySections.featured.slice(0, FEATURED_NOTES_LIMIT),
    [discoverySections.featured],
  );
  const popularNotes = useMemo(
    () => discoverySections.popular.slice(0, POPULAR_NOTES_LIMIT),
    [discoverySections.popular],
  );
  const recentNotes = useMemo(
    () => discoverySections.recent.slice(0, RECENT_NOTES_LIMIT),
    [discoverySections.recent],
  );

  const clearCreatorFilter = useCallback(() => {
    replacePublicLibraryFilters({
      ...parsedUrlFilters,
      creator: null,
      view: null,
    });
  }, [parsedUrlFilters, replacePublicLibraryFilters]);

  const activeFilterSummary = hasActiveFilters ? (
    <div className="flex flex-wrap items-center gap-2">
      {parsedUrlFilters.creator ? (
        <span className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs">
          By @{parsedUrlFilters.creator}
          <button
            type="button"
            className="text-foreground/65 hover:text-foreground"
            onClick={clearCreatorFilter}
            aria-label="Clear creator filter"
          >
            x
          </button>
        </span>
      ) : null}

      {selectedTargetProfile !== NOTE_TARGET_PROFILE_ALL ? (
        <span className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs">
          For: {getNoteTargetProfileLabel(selectedTargetProfile)}
          <button
            type="button"
            className="text-foreground/65 hover:text-foreground"
            onClick={() => {
              setSelectedTargetProfile(NOTE_TARGET_PROFILE_ALL);
              replacePublicLibraryFilters({
                ...parsedUrlFilters,
                audience: null,
                view: null,
              });
            }}
            aria-label="Clear note audience filter"
          >
            x
          </button>
        </span>
      ) : null}

      {selectedCourseProgram !== ALL_COURSE_PROGRAMS ? (
        <span className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs">
          Course: {selectedCourseProgram}
          <button
            type="button"
            className="text-foreground/65 hover:text-foreground"
            onClick={() => {
              setSelectedCourseProgram(ALL_COURSE_PROGRAMS);
              replacePublicLibraryFilters({
                ...parsedUrlFilters,
                courseProgram: null,
                view: null,
              });
            }}
            aria-label="Clear course program filter"
          >
            x
          </button>
        </span>
      ) : null}

      {selectedSubject !== ALL_SUBJECTS ? (
        <span className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs">
          Subject: {selectedSubject}
          <button
            type="button"
            className="text-foreground/65 hover:text-foreground"
            onClick={() => {
              setSelectedSubject(ALL_SUBJECTS);
              replacePublicLibraryFilters({
                ...parsedUrlFilters,
                subject: null,
                view: null,
              });
            }}
            aria-label="Clear subject filter"
          >
            x
          </button>
        </span>
      ) : null}

      {selectedSourceFilters.map((filter) => (
        <span key={filter} className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs">
          {filter === "BY_YOU" ? "By You" : filter === "OFFICIAL" ? "Official" : "Community"}
          <button
            type="button"
            className="text-foreground/65 hover:text-foreground"
            onClick={() => setSelectedSourceFilters((previous) => previous.filter((value) => value !== filter))}
            aria-label={`Remove ${filter === "BY_YOU" ? "By You" : filter === "OFFICIAL" ? "Official" : "Community"} filter`}
          >
            x
          </button>
        </span>
      ))}

      {selectedTags.map((tag) => (
        <span key={tag} className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs">
          {tag}
          <button
            type="button"
            className="text-foreground/65 hover:text-foreground"
            onClick={() => {
              setSelectedTags((previous) => {
                const next = previous.filter((value) => value !== tag);
                replacePublicLibraryFilters({
                  ...parsedUrlFilters,
                  tags: next.map((selectedTag) => slugifyPublicLibraryFilterValue(selectedTag)),
                  view: null,
                });
                return next;
              });
            }}
            aria-label={`Remove tag filter ${tag}`}
          >
            x
          </button>
        </span>
      ))}

      <Button type="button" variant="outline" size="sm" className="h-8" onClick={clearFilters}>
        Clear all
      </Button>
    </div>
  ) : null;
  const startRouteProgress = useRouteProgress();
  const openDiscoveryView = useCallback((view: PublicLibraryDiscoveryView) => {
    startRouteProgress();
    router.push(buildPublicLibraryUrl({
      ...parsedUrlFilters,
      view,
    }, searchParamsKey, basePath), { scroll: false });
  }, [basePath, parsedUrlFilters, router, searchParamsKey, startRouteProgress]);
  const clearDiscoveryView = useCallback(() => {
    startRouteProgress();
    router.push(buildPublicLibraryUrl({
      ...parsedUrlFilters,
      view: null,
    }, searchParamsKey, basePath), { scroll: false });
  }, [basePath, parsedUrlFilters, router, searchParamsKey, startRouteProgress]);
  const activeSectionCopy = activeDiscoveryView === null ? null : DISCOVERY_SECTION_COPY[activeDiscoveryView];
  const currentPublicLibraryPath = useMemo(
    () => buildPublicLibraryUrl(parsedUrlFilters, searchParamsKey, basePath),
    [basePath, parsedUrlFilters, searchParamsKey],
  );
  const handleNoteNavigate = useCallback((path: string) => {
    savePublicLibraryReturnUrl(currentPublicLibraryPath);
    startRouteProgress();
    router.push(path);
  }, [currentPublicLibraryPath, router, startRouteProgress]);
  const resolvedShareUrl = useMemo(() => {
    if (globalThis.window === undefined) {
      return currentPublicLibraryPath;
    }
    return new URL(currentPublicLibraryPath, globalThis.location.origin).toString();
  }, [currentPublicLibraryPath]);
  const handleCopyShareLink = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(resolvedShareUrl);
      setShareToastTone("success");
      setShareToastMessage(SHARE_LINK_COPIED_MESSAGE);
    } catch {
      setShareToastTone("error");
      setShareToastMessage(SHARE_PUBLIC_LIBRARY_COPY_ERROR);
    }
  }, [resolvedShareUrl]);

  const Container = embedded ? "div" : "main";

  return (
    <Container className={embedded
      ? "w-full space-y-6"
      : "mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10"}
    >
      {!embedded ? (
        <PageHeader
          eyebrow="LIBRARY"
          title="Public Library"
          description="Explore public notes from you, the community, and official NoteLib examples. Copy a note into your library when you want to study it in your own workspace."
          actions={(
            <Button
              type="button"
              variant="outline"
              className="w-full sm:w-auto lg:hidden"
              onClick={() => void handleCopyShareLink()}
            >
              {SHARE_PUBLIC_LIBRARY_LABEL}
            </Button>
          )}
          brandLogo
        />
      ) : (
        <div className="space-y-1">
          <h2 className="text-xl font-semibold tracking-tight">Community Notes</h2>
          <p className="text-sm text-foreground/65">
            Browse public notes and copy useful material into your own library.
          </p>
        </div>
      )}

      <GuidanceTip
        tipId="public-library-intro"
        message="Browse notes created by others. Copy any note into your library to study it in your own workspace — full Study Pack included."
      />

      {currentUserId ? <PublicLibraryDiscoveryFeedbackPrompt userId={currentUserId} /> : null}

      {loading && !hasLoadedOnce ? (
        <div className="grid gap-4 md:grid-cols-2">
          {Array.from({ length: 4 }).map((_, index) => (
            <Card key={`public-library-loading-${index}`} className="space-y-3 p-4 sm:p-6">
              <Skeleton className="h-5 w-2/3" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-1/2" />
            </Card>
          ))}
        </div>
      ) : error && items.length === 0 ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h2 className="text-xl font-semibold">Could not load public notes</h2>
          <p className="text-sm text-foreground/75">{error}</p>
          <ResponsiveActionButton type="button" variant="outline" onClick={() => void loadNotes()} action="retry" label="Retry" />
        </Card>
      ) : (
        <div className="space-y-4">
          <Card className="space-y-4 p-4 sm:p-5">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
              <div className="min-w-0 flex-1 space-y-2">
                <label htmlFor="public-library-search" className="text-sm font-medium">
                  Search
                </label>
                <div className="relative">
                  <input
                    id="public-library-search"
                    type="search"
                    value={searchQuery}
                    onChange={(event) => { setLastChangedFilter("search"); setSearchQuery(event.target.value); }}
                    placeholder="Search public notes..."
                    className={`h-10 w-full rounded-lg border border-border bg-background pl-3 ${
                      searchQuery ? "pr-10" : "pr-3"
                    } text-base text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600 sm:text-sm [&::-webkit-search-cancel-button]:hidden`}
                  />
                  {searchQuery ? (
                    <button
                      type="button"
                      onClick={() => setSearchQuery("")}
                      aria-label="Clear search"
                      className="absolute inset-y-0 right-0 flex w-9 items-center justify-center text-foreground/60 transition-colors hover:text-foreground"
                    >
                      <X className="h-4 w-4" />
                    </button>
                  ) : null}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2 sm:flex sm:shrink-0">
                <Button
                  type="button"
                  variant="outline"
                  className="relative w-full sm:min-w-30"
                  onClick={() => setFilterSheetOpen(true)}
                  aria-label="Open filters"
                >
                  <span className="inline-flex items-center gap-2">
                    <Filter className="h-4 w-4" aria-hidden="true" />
                    <span>More Filters</span>
                    {hasActiveFilters ? (
                      <span className="absolute right-2 top-2 h-2 w-2 rounded-full bg-blue-600 dark:bg-blue-400" aria-hidden="true" />
                    ) : null}
                  </span>
                </Button>

                <Button
                  type="button"
                  variant="outline"
                  className="w-full sm:min-w-30"
                  onClick={() => setSortSheetOpen(true)}
                  aria-label="Open sorting"
                >
                  <span className="inline-flex items-center gap-2">
                    <ArrowUpDown className="h-4 w-4" aria-hidden="true" />
                    <span>Sort</span>
                  </span>
                </Button>
              </div>
            </div>

            <div className="space-y-3 border-t border-border pt-3">
              <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                <div className="flex items-center gap-2">
                  <p data-testid="note-count-pill" className="text-sm text-foreground/50">
                    {hasActiveUrlFilters
                      ? `${items.length} of ${totalMatching} notes`
                      : `${totalMatching} notes`}
                  </p>
                  {loading ? (
                    <span
                      className="inline-flex items-center gap-1.5 text-xs text-foreground/50"
                      aria-live="polite"
                    >
                      <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" />
                      Searching…
                    </span>
                  ) : error ? (
                    <span className="text-xs text-red-600 dark:text-red-400">Couldn’t refresh results</span>
                  ) : null}
                </div>
                {!hasActiveFilters ? (
                  <p className="text-xs text-foreground/50">
                    Sorted by {PUBLIC_SORT_LABELS[selectedSort]}
                  </p>
                ) : null}
              </div>
              {hasActiveFilters ? activeFilterSummary : null}
            </div>
          </Card>

          {!ctaDismissed && !parsedUrlFilters.courseProgram && !parsedUrlFilters.creator ? (
            <Card className="flex flex-col gap-2 p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5">
              <div className="min-w-0 space-y-2">
                <p className="text-sm text-foreground/75">
                  Studying for a specific exam or program? Browse notes by Course or Program.
                </p>
                {topCoursePrograms.length > 0 ? (
                  <div className="flex flex-wrap gap-2" aria-label="Popular course and program filters">
                    {topCoursePrograms.map((courseProgram) => (
                      <button
                        key={courseProgram}
                        type="button"
                        onClick={() => applyCourseProgramChip(courseProgram)}
                        className="rounded-full border border-border bg-background px-3 py-1 text-xs font-medium text-foreground transition-colors hover:bg-highlight"
                      >
                        {courseProgram}
                      </button>
                    ))}
                  </div>
                ) : null}
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setFilterSheetOpen(true)}
                >
                  Browse by Course/Program
                </Button>
                <button
                  type="button"
                  aria-label="Dismiss this tip"
                  onClick={handleCtaDismiss}
                  className="text-foreground/50 hover:text-foreground"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            </Card>
          ) : null}

          {selectedTargetProfile !== NOTE_TARGET_PROFILE_ALL && totalMatching > 0 && totalMatching < PUBLIC_LIBRARY_SPARSE_AUDIENCE_THRESHOLD ? (
            <Card className="flex flex-col gap-3 border-amber-500/20 bg-amber-500/5 p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5">
              <p className="text-sm text-foreground/75">
                Only a few{" "}
                <span className="font-medium">{getNoteTargetProfileLabel(selectedTargetProfile)}</span>
                {" "}notes are available right now. Browse all notes to find more study material.
              </p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="shrink-0"
                onClick={() => {
                  setSelectedTargetProfile(NOTE_TARGET_PROFILE_ALL);
                  replacePublicLibraryFilters({ ...parsedUrlFilters, audience: null, view: null });
                }}
              >
                View all notes
              </Button>
            </Card>
          ) : null}

          {!isDiscoveryMode && activeCourseProgram && officialPlan ? (
            <p className="text-sm text-foreground/75">
              Looking for a full Study Plan for {activeCourseProgram}?{" "}
              <Link
                href={embedded ? EXPLORE_REVIEW_SETS_PATH : PUBLISHED_STUDY_PLANS_PATH_FROM_LIBRARY}
                className="font-medium text-blue-700 transition-colors hover:underline dark:text-blue-300"
              >
                Browse official plans →
              </Link>
            </p>
          ) : null}

          {isSectionView && activeSectionCopy ? (
            <div className="space-y-6">
              <Card className="space-y-3 border-blue-500/20 bg-blue-500/5 p-4 sm:p-6">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="space-y-1">
                    <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
                      Curated Discovery
                    </p>
                    <h2 className="text-lg font-semibold sm:text-xl">{activeSectionCopy.title}</h2>
                    <p className="text-sm text-foreground/75">{activeSectionCopy.description}</p>
                  </div>
                  <Button type="button" variant="outline" size="sm" onClick={clearDiscoveryView}>
                    Back to Discovery
                  </Button>
                </div>
              </Card>

              {items.length === 0 ? (
                <Card className="space-y-3 p-4 sm:p-6">
                  <h2 className="text-base font-semibold sm:text-lg">No notes are available in this section yet.</h2>
                  <p className="text-sm text-foreground/75">Return to Discovery to browse the other public-note sections.</p>
                </Card>
              ) : (
                <div className="grid gap-4 md:grid-cols-2">
                  {items.map((item) => (
                    <PublicNoteCard
                      key={item.id}
                      item={item}
                      currentUserId={currentUserId}
                      currentUsername={currentUsername}
                      onNavigate={handleNoteNavigate}
                      existingCopyNoteId={copiedNoteIdsBySourceId[item.id] ?? null}
                      onCopySuccess={handleCopySuccess}
                      onLikeSuccess={handleLikeSuccess}
                    />
                  ))}
                </div>
              )}
            </div>
          ) : isDiscoveryMode ? (
            <div className="space-y-10">
              {discoveryError ? (
                <Card className="space-y-3 p-4 sm:p-6">
                  <h2 className="text-base font-semibold sm:text-lg">Could not load discovery sections</h2>
                  <p className="text-sm text-foreground/75">{discoveryError}</p>
                  <Button type="button" variant="outline" onClick={() => void loadDiscoverySections()}>
                    Retry
                  </Button>
                </Card>
              ) : discoveryLoading && featuredNotes.length === 0 && popularNotes.length === 0 && recentNotes.length === 0 ? (
                <Card className="flex items-center gap-2 p-4 text-sm text-foreground/65 sm:p-6">
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                  Loading discovery sections…
                </Card>
              ) : totalMatching === 0 ? (
                <Card className="space-y-3 p-4 sm:p-6">
                  <h2 className="text-base font-semibold sm:text-lg">
                    {selectedTargetProfile === NOTE_TARGET_PROFILE_ALL
                      ? "No public notes yet."
                      : "No notes available for this category yet."}
                  </h2>
                  <p className="text-sm text-foreground/75">
                    {selectedTargetProfile === NOTE_TARGET_PROFILE_ALL
                      ? "Be the first to share a note to the public library."
                      : "Try another category or view the full Public Library."}
                  </p>
                  {selectedTargetProfile !== NOTE_TARGET_PROFILE_ALL ? (
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => {
                        setSelectedTargetProfile(NOTE_TARGET_PROFILE_ALL);
                        replacePublicLibraryFilters({
                          ...parsedUrlFilters,
                          audience: null,
                          view: null,
                        });
                      }}
                      className="w-full sm:w-auto"
                    >
                      View all notes
                    </Button>
                  ) : null}
                </Card>
              ) : null}

              {featuredNotes.length > 0 ? (
                <PublicLibraryDiscoverySection
                  title="⭐ Featured Notes"
                  description="High-engagement notes worth studying"
                  items={featuredNotes}
                  currentUserId={currentUserId}
                  currentUsername={currentUsername}
                  onNavigate={handleNoteNavigate}
                  onViewMore={() => openDiscoveryView("featured")}
                  copiedNoteIdsBySourceId={copiedNoteIdsBySourceId}
                  onCopySuccess={handleCopySuccess}
                  onLikeSuccess={handleLikeSuccess}
                />
              ) : null}

              {popularNotes.length > 0 ? (
                <PublicLibraryDiscoverySection
                  title="🔥 Most Popular"
                  items={popularNotes}
                  currentUserId={currentUserId}
                  currentUsername={currentUsername}
                  onNavigate={handleNoteNavigate}
                  onViewMore={() => openDiscoveryView("popular")}
                  copiedNoteIdsBySourceId={copiedNoteIdsBySourceId}
                  onCopySuccess={handleCopySuccess}
                  onLikeSuccess={handleLikeSuccess}
                />
              ) : null}

              {recentNotes.length > 0 ? (
                <PublicLibraryDiscoverySection
                  title="🆕 Recently Added"
                  items={recentNotes}
                  currentUserId={currentUserId}
                  currentUsername={currentUsername}
                  onNavigate={handleNoteNavigate}
                  onViewMore={() => openDiscoveryView("recent")}
                  copiedNoteIdsBySourceId={copiedNoteIdsBySourceId}
                  onCopySuccess={handleCopySuccess}
                  onLikeSuccess={handleLikeSuccess}
                />
              ) : null}
            </div>
          ) : items.length === 0 ? (
            <Card className="space-y-3 p-4 sm:p-6">
              {searchQuery.trim().length === 0
                && !hasActiveFilters
                && selectedTargetProfile !== NOTE_TARGET_PROFILE_ALL ? (
                  <>
                    <h2 className="text-base font-semibold sm:text-lg">No notes available for this category yet.</h2>
                    <p className="text-sm text-foreground/75">Try another note audience or browse the full Public Library.</p>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => {
                        setSelectedTargetProfile(NOTE_TARGET_PROFILE_ALL);
                        replacePublicLibraryFilters({
                          ...parsedUrlFilters,
                          audience: null,
                          view: null,
                        });
                      }}
                      className="w-full sm:w-auto"
                    >
                      View all notes
                    </Button>
                  </>
                ) : selectedCourseProgram !== ALL_COURSE_PROGRAMS && searchQuery.trim().length === 0 ? (
                  <>
                    <h2 className="text-base font-semibold sm:text-lg">No {selectedCourseProgram} notes shared yet.</h2>
                    <p className="text-sm text-foreground/75">Got notes? Share them with the community.</p>
                    <Link
                      href={currentUserId ? "/notes/new" : "/auth"}
                      className={buttonVariants({ variant: "outline", className: "w-full sm:w-auto" })}
                    >
                      Share a note
                    </Link>
                  </>
                ) : (
                  <>
                    <h2 className="text-base font-semibold sm:text-lg">No public notes match your filters.</h2>
                    <p className="text-sm text-foreground/75">Try adjusting search or filters.</p>
                    <Button type="button" variant="outline" onClick={clearFilters} className="w-full sm:w-auto">
                      Clear filters
                    </Button>
                    <Button type="button" variant="outline" onClick={dropMostRecentFilter} className="w-full sm:w-auto">
                      Remove last filter
                    </Button>
                  </>
                )}
            </Card>
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {items.map((item) => (
                <PublicNoteCard
                  key={item.id}
                  item={item}
                  currentUserId={currentUserId}
                  currentUsername={currentUsername}
                  onNavigate={handleNoteNavigate}
                  existingCopyNoteId={copiedNoteIdsBySourceId[item.id] ?? null}
                  onCopySuccess={handleCopySuccess}
                  onLikeSuccess={handleLikeSuccess}
                />
              ))}
            </div>
          )}

          {(!isDiscoveryMode || isSectionView) && (hasMore || loadMoreError) ? (
            <div className="flex flex-col items-center gap-2 pt-2">
              {loadMoreError ? (
                <p className="text-sm text-red-600 dark:text-red-400" role="alert">{loadMoreError}</p>
              ) : null}
              <Button
                type="button"
                variant="outline"
                onClick={() => void handleLoadMore()}
                disabled={loadMoreLoading}
              >
                {loadMoreLoading ? (
                  <span className="inline-flex items-center gap-2">
                    <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                    Loading…
                  </span>
                ) : loadMoreError ? "Retry load more" : "Load more"}
              </Button>
            </div>
          ) : null}
        </div>
      )}

      <LibrarySheetModal
        isOpen={filterSheetOpen}
        title="More Filters"
        onClose={() => setFilterSheetOpen(false)}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={clearFilters}>
              Clear all
            </Button>
            <Button type="button" onClick={applyModalFilters}>
              Apply
            </Button>
          </div>
        )}
      >
        <div className="space-y-6">
          <div className="space-y-3">
            <p className="text-sm font-medium">For</p>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                className={getFilterChipClassName(audienceDraft === NOTE_TARGET_PROFILE_ALL)}
                onClick={() => setAudienceDraft(NOTE_TARGET_PROFILE_ALL)}
                aria-pressed={audienceDraft === NOTE_TARGET_PROFILE_ALL}
              >
                All
              </button>
              {PUBLIC_NOTE_TARGET_PROFILE_TYPES.map((targetProfileType) => (
                <button
                  key={targetProfileType}
                  type="button"
                  className={getFilterChipClassName(audienceDraft === targetProfileType)}
                  onClick={() => setAudienceDraft(targetProfileType)}
                  aria-pressed={audienceDraft === targetProfileType}
                >
                  {getNoteTargetProfileLabel(targetProfileType)}
                </button>
              ))}
            </div>
          </div>

          {availableCoursePrograms.length > 0 ? (
            <div className="space-y-2">
              <p className="text-sm font-medium">Course / Program</p>
              <p className="text-xs leading-relaxed text-foreground/60">
                A note can apply to several programs, so it appears under each one it serves.
              </p>
              <div className="relative">
                <input
                  type="text"
                  aria-label="Course / Program"
                  value={courseProgramComboOpen ? courseProgramSearchQuery : (courseProgramDraft !== ALL_COURSE_PROGRAMS ? courseProgramDraft : "")}
                  onChange={(event) => { setCourseProgramSearchQuery(event.target.value); setCourseProgramComboTyped(true); }}
                  placeholder={courseProgramComboOpen ? "Search course or program..." : "All"}
                  onFocus={() => { setCourseProgramComboOpen(true); setCourseProgramComboTyped(false); setCourseProgramSearchQuery(courseProgramDraft !== ALL_COURSE_PROGRAMS ? courseProgramDraft : ""); }}
                  onBlur={() => globalThis.setTimeout(() => setCourseProgramComboOpen(false), 150)}
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 pr-14 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
                />
                {courseProgramDraft !== ALL_COURSE_PROGRAMS ? (
                  <button
                    type="button"
                    aria-label="Clear course/program filter"
                    onMouseDown={(event) => { event.preventDefault(); setCourseProgramDraft(ALL_COURSE_PROGRAMS); setCourseProgramSearchQuery(""); setCourseProgramComboTyped(false); setCourseProgramComboOpen(false); }}
                    className="absolute right-8 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center text-foreground/45 transition-colors hover:text-foreground"
                  >
                    <X className="h-4 w-4" aria-hidden="true" />
                  </button>
                ) : null}
                <ChevronDown
                  className={`pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-foreground/45 transition-transform duration-200 ${courseProgramComboOpen ? "rotate-180" : ""}`}
                  aria-hidden="true"
                />
              </div>
              {courseProgramComboOpen ? (
                <div ref={courseProgramDropdownRef} className="max-h-44 overflow-y-auto rounded-md border border-border shadow-sm">
                  <button
                    type="button"
                    className={getComboboxItemClassName(courseProgramDraft === ALL_COURSE_PROGRAMS)}
                    onMouseDown={(event) => { event.preventDefault(); setCourseProgramDraft(ALL_COURSE_PROGRAMS); setCourseProgramSearchQuery(""); setCourseProgramComboOpen(false); }}
                  >
                    All
                  </button>
                  {filteredModalCoursePrograms.length === 0 ? (
                    <p className="px-3 py-2.5 text-sm text-foreground/65">No course/programs match your search.</p>
                  ) : (
                    filteredModalCoursePrograms.map((courseProgram) => (
                      <button
                        key={courseProgram}
                        type="button"
                        className={getComboboxItemClassName(courseProgramDraft === courseProgram)}
                        onMouseDown={(event) => { event.preventDefault(); setCourseProgramDraft(courseProgram); setCourseProgramSearchQuery(""); setCourseProgramComboOpen(false); }}
                      >
                        {courseProgram}
                      </button>
                    ))
                  )}
                </div>
              ) : null}
            </div>
          ) : null}

          {availableSubjects.length > 0 ? (
            <div className="space-y-2">
              <p className="text-sm font-medium">Subjects</p>
              <div className="relative">
                <input
                  type="text"
                  aria-label="Subject"
                  value={subjectComboOpen ? subjectSearchQuery : (subjectFilterDraft !== ALL_SUBJECTS ? subjectFilterDraft : "")}
                  onChange={(event) => { setSubjectSearchQuery(event.target.value); setSubjectComboTyped(true); }}
                  placeholder={subjectComboOpen ? "Search subjects..." : "All"}
                  onFocus={() => { setSubjectComboOpen(true); setSubjectComboTyped(false); setSubjectSearchQuery(subjectFilterDraft !== ALL_SUBJECTS ? subjectFilterDraft : ""); }}
                  onBlur={() => globalThis.setTimeout(() => setSubjectComboOpen(false), 150)}
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 pr-14 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
                />
                {subjectFilterDraft !== ALL_SUBJECTS ? (
                  <button
                    type="button"
                    aria-label="Clear subject filter"
                    onMouseDown={(event) => { event.preventDefault(); setSubjectFilterDraft(ALL_SUBJECTS); setSubjectSearchQuery(""); setSubjectComboTyped(false); setSubjectComboOpen(false); }}
                    className="absolute right-8 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center text-foreground/45 transition-colors hover:text-foreground"
                  >
                    <X className="h-4 w-4" aria-hidden="true" />
                  </button>
                ) : null}
                <ChevronDown
                  className={`pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-foreground/45 transition-transform duration-200 ${subjectComboOpen ? "rotate-180" : ""}`}
                  aria-hidden="true"
                />
              </div>
              {subjectComboOpen ? (
                <div ref={subjectDropdownRef} className="max-h-44 overflow-y-auto rounded-md border border-border shadow-sm">
                  <button
                    type="button"
                    className={getComboboxItemClassName(subjectFilterDraft === ALL_SUBJECTS)}
                    onMouseDown={(event) => { event.preventDefault(); setSubjectFilterDraft(ALL_SUBJECTS); setSubjectSearchQuery(""); setSubjectComboOpen(false); }}
                  >
                    All
                  </button>
                  {filteredModalSubjects.length === 0 ? (
                    <p className="px-3 py-2.5 text-sm text-foreground/65">No subjects match your search.</p>
                  ) : (
                    filteredModalSubjects.map((subject) => (
                      <button
                        key={subject}
                        type="button"
                        className={getComboboxItemClassName(subjectFilterDraft === subject)}
                        onMouseDown={(event) => { event.preventDefault(); setSubjectFilterDraft(subject); setSubjectSearchQuery(""); setSubjectComboOpen(false); }}
                      >
                        {subject}
                      </button>
                    ))
                  )}
                </div>
              ) : null}
            </div>
          ) : null}

          {availableTags.length > 0 ? (
            <div className="space-y-3">
              <div className="flex items-center justify-between gap-2">
                <p className="text-sm font-medium">Popular Tags</p>
                <button
                  type="button"
                  className={TEXT_LINK_CLASS_NAME}
                  onClick={() => setTagSelectorOpen(true)}
                >
                  {BROWSE_ALL_LABEL}
                </button>
              </div>
              <div className="flex flex-wrap gap-2">
                {visiblePopularTags.map((tag) => (
                  <button
                    key={tag}
                    type="button"
                    className={getFilterChipClassName(tagsFilterDraft.includes(tag))}
                    onClick={() => setTagsFilterDraft((previous) => (
                      previous.includes(tag)
                        ? previous.filter((t) => t !== tag)
                        : [...previous, tag]
                    ))}
                    aria-pressed={tagsFilterDraft.includes(tag)}
                  >
                    {tag}
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          <div className="space-y-3">
            <p className="text-sm font-medium">Study readiness</p>
            <label className="flex cursor-pointer items-center gap-2 rounded-lg border border-border p-3 text-sm transition-colors hover:bg-highlight active:bg-highlight-strong">
              <input
                type="checkbox"
                checked={studyPackReadyDraft}
                onChange={(event) => setStudyPackReadyDraft(event.target.checked)}
                className="h-4 w-4 rounded border-border"
              />
              <span>Study Pack Ready</span>
            </label>
          </div>

          <div className="space-y-3">
            <p className="text-sm font-medium">Source</p>
            <div className="space-y-1 rounded-lg border border-border p-2">
              {([
                { value: "BY_YOU" as const, label: "By You" },
                { value: "OFFICIAL" as const, label: "Official" },
                { value: "COMMUNITY" as const, label: "Community" },
              ]).map((option) => (
                <label key={option.value} className="flex cursor-pointer items-center gap-2 rounded px-2 py-1 text-sm transition-colors hover:bg-highlight active:bg-highlight-strong">
                  <input
                    type="checkbox"
                    checked={selectedSourceFilters.includes(option.value)}
                    onChange={() => toggleSourceFilter(option.value)}
                    className="h-4 w-4 rounded border-border"
                  />
                  <span>{option.label}</span>
                </label>
              ))}
            </div>
          </div>
        </div>
      </LibrarySheetModal>

      <LibrarySheetModal
        isOpen={tagSelectorOpen}
        title={TAG_SELECTOR_TITLE}
        onClose={() => setTagSelectorOpen(false)}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={() => setTagDraft([])}>
              Clear
            </Button>
            <Button
              type="button"
              onClick={() => {
                setSelectedTags(tagDraft);
                setTagsFilterDraft(tagDraft);
                setLastChangedFilter("tags");
                if (tagDraft.length > 0) {
                  setRecentTags((previous) => updateRecentValues(previous, [...tagDraft].reverse()));
                }
                replacePublicLibraryFilters({
                  ...parsedUrlFilters,
                  tags: tagDraft.map((tag) => slugifyPublicLibraryFilterValue(tag)),
                  view: null,
                });
                setTagSelectorOpen(false);
              }}
            >
              Apply
            </Button>
          </div>
        )}
      >
        <div className="sticky top-0 z-10 space-y-3 bg-background pb-3">
          <input
            type="search"
            value={tagSearchQuery}
            onChange={(event) => setTagSearchQuery(event.target.value)}
            placeholder="Search tags..."
            data-autofocus="true"
            className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
          />
          {tagDraft.length > 0 ? (
            <div className="space-y-2">
              <div className="flex items-center justify-between gap-2">
                <p className="text-xs font-medium uppercase tracking-wide text-foreground/55">Selected tags</p>
                <button
                  type="button"
                  className="text-xs font-medium text-blue-700 hover:text-blue-800 dark:text-blue-300 dark:hover:text-blue-200"
                  onClick={() => setTagDraft([])}
                >
                  Clear all
                </button>
              </div>
              <div className="relative">
              <div className={getFadedScrollRailClassName()}>
                {tagDraft.map((tag) => (
                  <button
                    key={`selected-${tag}`}
                    type="button"
                    className={getFilterChipClassName(true)}
                    onClick={() => toggleDraftTag(tag)}
                  >
                    {tag} ×
                  </button>
                ))}
              </div>
              </div>
            </div>
          ) : null}
        </div>
        {availableTags.length === 0 ? (
          <p className="text-sm text-foreground/65">No tags available yet.</p>
        ) : filteredModalTags.length === 0 ? (
          <p className="text-sm text-foreground/65">No tags match your search.</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {filteredModalTags.map((tag) => (
              <button
                key={tag}
                type="button"
                className={getFilterChipClassName(tagDraft.includes(tag))}
                onClick={() => toggleDraftTag(tag)}
                aria-pressed={tagDraft.includes(tag)}
              >
                {tag}
              </button>
            ))}
          </div>
        )}
      </LibrarySheetModal>

      <LibrarySheetModal
        isOpen={sortSheetOpen}
        title="Sort public notes"
        onClose={() => setSortSheetOpen(false)}
      >
        <div className="space-y-2">
          {(Object.entries(PUBLIC_SORT_LABELS) as Array<[PublicLibrarySortOption, string]>)
            .filter(([value]) => hasActiveFilters || value !== "RECOMMENDED")
            .map(([value, label]) => {
            const isSelected = effectiveSelectedSort === value;
            return (
              <button
                key={value}
                type="button"
                className={`w-full rounded-lg border px-3 py-3 text-left text-sm transition-colors ${
                  isSelected
                    ? "border-blue-600 bg-blue-50 text-blue-700 dark:border-blue-400 dark:bg-blue-950/40 dark:text-blue-200"
                    : "border-border bg-background hover:bg-highlight active:bg-highlight-strong"
                }`}
                onClick={() => {
                  setSelectedSort(value);
                  replacePublicLibraryFilters({
                    ...parsedUrlFilters,
                    sort: resolveSortQuery(value),
                  });
                  setSortSheetOpen(false);
                }}
              >
                {label}
              </button>
            );
            })}
        </div>
      </LibrarySheetModal>

      {shareToastMessage ? <ToastMessage message={shareToastMessage} tone={shareToastTone} /> : null}

      <AppModal
        isOpen={copySuccessState !== null}
        title={COPY_SUCCESS_MODAL_TITLE}
        onClose={() => setCopySuccessState(null)}
        titleClassName="text-xl font-semibold tracking-tight sm:text-[1.4rem]"
        titleIcon={(
          <div className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-emerald-500/25 bg-emerald-500/10 text-emerald-600 dark:border-emerald-400/25 dark:bg-emerald-400/10 dark:text-emerald-300">
            <CheckCircle2 className="h-4.5 w-4.5" aria-hidden="true" />
          </div>
        )}
        panelClassName={isMobileSuccessSheet
          ? "w-full max-w-full self-end rounded-b-none rounded-t-[28px] border-b-0 px-5 pt-4 pb-5 shadow-[0_-18px_50px_rgba(3,7,18,0.22)] sm:w-[90%] sm:max-w-[400px] sm:self-auto sm:rounded-b-[28px] sm:border-b sm:px-6 sm:pt-5 sm:pb-6"
          : "max-w-[400px] rounded-[28px] p-6 shadow-[0_24px_60px_rgba(3,7,18,0.16)] sm:p-7"}
        contentClassName="space-y-3"
        actionsClassName="mt-5"
        headerClassName={isMobileSuccessSheet ? "gap-3" : "gap-4"}
        enableSwipeToClose={isMobileSuccessSheet}
        actions={copySuccessState ? (
          <div className="flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-end">
            <ResponsiveActionButton
              type="button"
              className="w-full sm:w-auto"
              action="library"
              label={MODAL_VIEW_NOTE_LABEL}
              onClick={() => {
                startRouteProgress();
                router.push(buildCopiedNotePath(copySuccessState.copiedNoteId, "library"));
              }}
              showTextOnMobile
            />
          </div>
        ) : null}
      >
        {copySuccessState ? (
          <div className="space-y-3 text-sm leading-relaxed text-foreground/78">
            {isMobileSuccessSheet ? (
              <div className="mb-4">
                <div className="mx-auto h-1.5 w-12 rounded-full bg-foreground/15" aria-hidden="true" />
              </div>
            ) : null}
            <p>{COPY_SUCCESS_BODY_LINE_ONE}</p>
          </div>
        ) : null}
      </AppModal>
    </Container>
  );
}
