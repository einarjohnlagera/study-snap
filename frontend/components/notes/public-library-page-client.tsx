"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowUpDown, CheckCircle2, Filter, X } from "lucide-react";
import { useRouteProgress } from "@/components/navigation/route-progress-provider";
import { Button } from "@/components/ui/button";
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
import { getAuthUser } from "@/lib/auth";
import {
  type LearnerLevel,
  listNotes,
  listPublicNotes,
  listSubjects,
  type NoteListItemResponse,
} from "@/lib/api";
import {
  formatLearnerLevel,
  mergeCourseProgramSuggestions,
  normalizeCourseProgram,
} from "@/lib/learning-profile";
import { resolvePublicNoteAuthorMeta } from "@/lib/public-note-author";
import { buildPublicLibraryNotePath, buildPublicProfilePath } from "@/lib/public-note-path";
import { normalizeSubject } from "@/lib/subjects";
import { getBrowsingCardClassName } from "@/lib/clickable-card";
import {
  excludeById,
  getFeaturedNotes,
  getPopularNotes,
  getRecentNotes,
} from "@/lib/public-library-discovery";
import { buildCopiedNotePath } from "@/lib/public-note-copy";
import {
  buildPublicLibraryUrl,
  parsePublicLibraryFilters,
  resolvePublicLibraryValueBySlug,
  resolvePublicLibraryValuesBySlug,
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
const ALL_LEARNER_LEVELS = "__ALL_LEARNER_LEVELS__";
const FEATURED_NOTES_LIMIT = 3;
const POPULAR_NOTES_LIMIT = 5;
const RECENT_NOTES_LIMIT = 5;
const POPULAR_TAG_LIMIT_MOBILE = 4;
const POPULAR_TAG_LIMIT_DESKTOP = 6;
const POPULAR_SUBJECT_LIMIT_MOBILE = 4;
const POPULAR_SUBJECT_LIMIT_DESKTOP = 6;
const MORE_TAGS_LABEL = "+ More";
const MORE_SUBJECTS_LABEL = "+ More";
const TAG_SELECTOR_TITLE = "Select tags";
const SUBJECT_SELECTOR_TITLE = "Select subject";
const COPY_SUCCESS_MODAL_TITLE = "Copied to your library";
const COPY_SUCCESS_BODY_LINE_ONE = "You can start reviewing now or come back later from your library.";
const MODAL_VIEW_NOTE_LABEL = "View Note";
const MODAL_START_REVIEW_LABEL = "Start Review";
const MOBILE_SUCCESS_SHEET_MEDIA_QUERY = "(max-width: 639px)";
const CLOSE_MODAL_LABEL = "Close copied to your library";
const SHARE_PUBLIC_LIBRARY_LABEL = "Share this list";
const SHARE_PUBLIC_LIBRARY_MODAL_TITLE = "Share this list";
const SHARE_PUBLIC_LIBRARY_COPY_ERROR = "Could not copy the public library link.";
const SHAREABLE_URL_LABEL = "Shareable URL";
const SHARE_LINK_COPIED_MESSAGE = "Link copied";

type ShareState = "idle" | "copied" | "error";

type PublicLibrarySortOption =
  | "NEWEST"
  | "MOST_COPIED"
  | "MOST_VIEWED"
  | "TITLE_ASC";

type PublicLibrarySourceFilter = "BY_YOU" | "OFFICIAL" | "COMMUNITY";

const PUBLIC_SORT_LABELS: Record<PublicLibrarySortOption, string> = {
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

function resolveSortOption(sort: PublicLibrarySortQuery | null): PublicLibrarySortOption {
  switch (sort) {
    case "copied":
      return "MOST_COPIED";
    case "title":
      return "TITLE_ASC";
    case "views":
      return "MOST_VIEWED";
    case "popular":
      return "MOST_COPIED";
    case "recent":
    default:
      return "NEWEST";
  }
}

function resolveSortQuery(sort: PublicLibrarySortOption): PublicLibrarySortQuery | null {
  switch (sort) {
    case "MOST_COPIED":
      return "copied";
    case "MOST_VIEWED":
      return "views";
    case "TITLE_ASC":
      return "title";
    case "NEWEST":
    default:
      return null;
  }
}

function countActivePublicFilterGroups({
  courseProgram,
  learnerLevel,
  subject,
  tags,
  sourceFilters,
}: {
  courseProgram: string;
  learnerLevel: string;
  subject: string;
  tags: string[];
  sourceFilters: PublicLibrarySourceFilter[];
}) {
  return [
    courseProgram !== ALL_COURSE_PROGRAMS,
    learnerLevel !== ALL_LEARNER_LEVELS,
    subject !== ALL_SUBJECTS,
    tags.length > 0,
    sourceFilters.length > 0,
  ].filter(Boolean).length;
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
  item: Pick<NoteListItemResponse, "ownerUserId" | "authorDisplayName" | "isCurrentUser" | "isOfficialAuthor">,
  currentUserId: string | null,
) {
  const authorMeta = resolvePublicNoteAuthorMeta({
    ownerUserId: item.ownerUserId,
    currentUserId,
    authorDisplayName: item.authorDisplayName,
    isCurrentUser: item.isCurrentUser,
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

interface PublicNoteCardProps {
  item: NoteListItemResponse;
  currentUserId: string | null;
  onNavigate: (path: string) => void;
  existingCopyNoteId?: string | null;
  onCopySuccess: (payload: { copiedNoteId: string; sourceNoteId: string }) => void;
  onLikeSuccess: (payload: { noteId: string; liked: boolean; likeCount: number }) => void;
}

function PublicNoteCard({
  item,
  currentUserId,
  onNavigate,
  existingCopyNoteId = null,
  onCopySuccess,
  onLikeSuccess,
}: Readonly<PublicNoteCardProps>) {
  const itemTags = normalizeTags(item.tags);
  const authorBadge = resolveAuthorBadge(item, currentUserId);
  const path = buildPublicLibraryNotePath({ subject: item.subject, title: item.title });
  const isOwner = item.ownerUserId === currentUserId || item.isCurrentUser;

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
        notePreviewLines={2}
        summaryPreviewLines={2}
        showPreviewLabels={false}
        footer={(
          <div className="flex flex-wrap items-start justify-between gap-3 text-xs text-foreground/65">
            <div className="flex min-w-0 flex-wrap items-center gap-2">
              {item.ownerUserId ? (
                <Link
                  href={buildPublicProfilePath(item.ownerUserId)}
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
                onCopySuccess={({ copiedNoteId }) => onCopySuccess({
                  copiedNoteId,
                  sourceNoteId: item.id,
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
  onNavigate: (path: string) => void;
  onViewMore: () => void;
  copiedNoteIdsBySourceId: Record<string, string>;
  onCopySuccess: (payload: { copiedNoteId: string; sourceNoteId: string }) => void;
  onLikeSuccess: (payload: { noteId: string; liked: boolean; likeCount: number }) => void;
}

function PublicLibraryDiscoverySection({
  title,
  description,
  items,
  currentUserId,
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

export function PublicLibraryPageClient() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const searchParamsKey = searchParams.toString();
  const parsedUrlFilters = useMemo(() => parsePublicLibraryFilters(searchParamsKey), [searchParamsKey]);
  const [currentUserId, setCurrentUserId] = useState<string | null>(() => getAuthUser()?.id ?? null);
  const [selectedTargetProfile, setSelectedTargetProfile] = useState<NoteTargetProfileFilter>(NOTE_TARGET_PROFILE_ALL);
  const [items, setItems] = useState<NoteListItemResponse[]>([]);
  const [copiedNoteIdsBySourceId, setCopiedNoteIdsBySourceId] = useState<Record<string, string>>({});
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedCourseProgram, setSelectedCourseProgram] = useState<string>(ALL_COURSE_PROGRAMS);
  const [selectedLearnerLevel, setSelectedLearnerLevel] = useState<string>(ALL_LEARNER_LEVELS);
  const [selectedSubject, setSelectedSubject] = useState<string>(ALL_SUBJECTS);
  const [subjectDraft, setSubjectDraft] = useState<string>(ALL_SUBJECTS);
  const [selectedSort, setSelectedSort] = useState<PublicLibrarySortOption>("NEWEST");
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [tagDraft, setTagDraft] = useState<string[]>([]);
  const [selectedSourceFilters, setSelectedSourceFilters] = useState<PublicLibrarySourceFilter[]>([]);
  const [tagSearchQuery, setTagSearchQuery] = useState("");
  const [subjectSearchQuery, setSubjectSearchQuery] = useState("");
  const [subjectSuggestions, setSubjectSuggestions] = useState<string[]>([]);
  const [filterSheetOpen, setFilterSheetOpen] = useState(false);
  const [sortSheetOpen, setSortSheetOpen] = useState(false);
  const [tagSelectorOpen, setTagSelectorOpen] = useState(false);
  const [subjectSelectorOpen, setSubjectSelectorOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [recentTags, setRecentTags] = useState<string[]>([]);
  const [recentSubjects, setRecentSubjects] = useState<string[]>([]);
  const [copySuccessState, setCopySuccessState] = useState<{
    copiedNoteId: string;
  } | null>(null);
  const [isMobileSuccessSheet, setIsMobileSuccessSheet] = useState(false);
  const [shareModalOpen, setShareModalOpen] = useState(false);
  const [shareState, setShareState] = useState<ShareState>("idle");

  const loadNotes = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [notesResult, subjectsResult] = await Promise.allSettled([
        listPublicNotes({
          audience: parsedUrlFilters.audience ?? undefined,
          courseProgram: parsedUrlFilters.courseProgram ?? undefined,
          search: parsedUrlFilters.search ?? undefined,
          sort: parsedUrlFilters.sort ?? undefined,
          subject: parsedUrlFilters.subject ?? undefined,
          tags: parsedUrlFilters.tags,
        }),
        listSubjects("public"),
      ]);
      if (notesResult.status !== "fulfilled") {
        throw notesResult.reason;
      }
      setItems(notesResult.value);
      setSubjectSuggestions(subjectsResult.status === "fulfilled" ? subjectsResult.value : []);
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : "Could not load public notes.";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [parsedUrlFilters.audience, parsedUrlFilters.courseProgram, parsedUrlFilters.search, parsedUrlFilters.sort, parsedUrlFilters.subject, parsedUrlFilters.tags]);

  useEffect(() => {
    void loadNotes();
  }, [loadNotes]);

  useEffect(() => {
    const syncAuth = () => {
      setCurrentUserId(getAuthUser()?.id ?? null);
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

  const handleCopySuccess = useCallback((payload: { copiedNoteId: string; sourceNoteId: string }) => {
    setCopiedNoteIdsBySourceId((previous) => ({
      ...previous,
      [payload.sourceNoteId]: payload.copiedNoteId,
    }));
    setCopySuccessState({
      copiedNoteId: payload.copiedNoteId,
    });
  }, []);

  const handleLikeSuccess = useCallback((payload: { noteId: string; liked: boolean; likeCount: number }) => {
    setItems((previous) => previous.map((item) => (
      item.id === payload.noteId
        ? {
            ...item,
            likeCount: payload.likeCount,
            likedByCurrentUser: payload.liked,
          }
        : item
    )));
  }, []);

  const replacePublicLibraryFilters = useCallback((nextFilters: PublicLibraryUrlFilters) => {
    const currentUrl = buildPublicLibraryUrl(parsedUrlFilters, searchParamsKey);
    const nextUrl = buildPublicLibraryUrl(nextFilters, searchParamsKey);
    if (currentUrl !== nextUrl) {
      router.replace(nextUrl);
    }
  }, [parsedUrlFilters, router, searchParamsKey]);

  const derivedSubjects = useMemo(() => {
    const subjectSet = new Set<string>();
    for (const item of items) {
      const subject = normalizeSubject(item.subject);
      if (subject) {
        subjectSet.add(subject);
      }
    }
    return Array.from(subjectSet).sort((left, right) => left.localeCompare(right));
  }, [items]);

  const availableSubjects = subjectSuggestions.length > 0 ? subjectSuggestions : derivedSubjects;

  const availableCoursePrograms = useMemo(() => {
    return mergeCourseProgramSuggestions(items.map((item) => item.courseProgram));
  }, [items]);

  const availableLearnerLevels = useMemo(() => {
    const learnerLevels = new Set<LearnerLevel>();
    for (const item of items) {
      if (item.learnerLevel) {
        learnerLevels.add(item.learnerLevel);
      }
    }
    return Array.from(learnerLevels).sort((left, right) => formatLearnerLevel(left)?.localeCompare(formatLearnerLevel(right) ?? "") ?? 0);
  }, [items]);

  const availableTags = useMemo(() => {
    const tagSet = new Set<string>();
    for (const item of items) {
      for (const tag of normalizeTags(item.tags)) {
        tagSet.add(tag);
      }
    }
    return Array.from(tagSet).sort((left, right) => left.localeCompare(right));
  }, [items]);

  useEffect(() => {
    setSearchQuery(parsedUrlFilters.search ?? "");
    setSelectedTargetProfile(parsedUrlFilters.audience ?? NOTE_TARGET_PROFILE_ALL);
    setSelectedSort(resolveSortOption(parsedUrlFilters.sort));

    const resolvedCourseProgram = parsedUrlFilters.courseProgram
      ? resolvePublicLibraryValueBySlug(availableCoursePrograms, parsedUrlFilters.courseProgram)
      : null;
    setSelectedCourseProgram(resolvedCourseProgram ?? ALL_COURSE_PROGRAMS);

    const resolvedSubject = parsedUrlFilters.subject
      ? resolvePublicLibraryValueBySlug(availableSubjects, parsedUrlFilters.subject)
      : null;
    const nextSelectedSubject = resolvedSubject ?? ALL_SUBJECTS;
    setSelectedSubject(nextSelectedSubject);
    setSubjectDraft(nextSelectedSubject);

    const resolvedTags = resolvePublicLibraryValuesBySlug(availableTags, parsedUrlFilters.tags);
    setSelectedTags(resolvedTags);
    setTagDraft(resolvedTags);
  }, [
    availableCoursePrograms,
    availableSubjects,
    availableTags,
    parsedUrlFilters.audience,
    parsedUrlFilters.courseProgram,
    parsedUrlFilters.search,
    parsedUrlFilters.sort,
    parsedUrlFilters.subject,
    parsedUrlFilters.tags,
  ]);

  const subjectCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const item of items) {
      const subject = normalizeSubject(item.subject);
      if (subject) {
        counts.set(subject, (counts.get(subject) ?? 0) + 1);
      }
    }
    return counts;
  }, [items]);

  const tagCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const item of items) {
      for (const tag of normalizeTags(item.tags)) {
        counts.set(tag, (counts.get(tag) ?? 0) + 1);
      }
    }
    return counts;
  }, [items]);

  useEffect(() => {
    if (selectedCourseProgram !== ALL_COURSE_PROGRAMS && !availableCoursePrograms.includes(selectedCourseProgram)) {
      setSelectedCourseProgram(ALL_COURSE_PROGRAMS);
    }
  }, [availableCoursePrograms, selectedCourseProgram]);

  useEffect(() => {
    if (selectedLearnerLevel !== ALL_LEARNER_LEVELS && !availableLearnerLevels.includes(selectedLearnerLevel as LearnerLevel)) {
      setSelectedLearnerLevel(ALL_LEARNER_LEVELS);
    }
  }, [availableLearnerLevels, selectedLearnerLevel]);

  useEffect(() => {
    if (selectedSubject !== ALL_SUBJECTS && !availableSubjects.includes(selectedSubject)) {
      setSelectedSubject(ALL_SUBJECTS);
    }
    if (subjectDraft !== ALL_SUBJECTS && !availableSubjects.includes(subjectDraft)) {
      setSubjectDraft(ALL_SUBJECTS);
    }
  }, [availableSubjects, selectedSubject, subjectDraft]);

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
    if (subjectSelectorOpen) {
      setSubjectDraft(selectedSubject);
      setSubjectSearchQuery("");
    }
  }, [selectedSubject, subjectSelectorOpen]);

  const toggleDraftTag = useCallback((tag: string) => {
    setTagDraft((previous) => (
      previous.includes(tag)
        ? previous.filter((selectedTag) => selectedTag !== tag)
        : [...previous, tag]
    ));
  }, []);

  const toggleSourceFilter = useCallback((filter: PublicLibrarySourceFilter) => {
    setSelectedSourceFilters((previous) => (
      previous.includes(filter)
        ? previous.filter((selectedFilter) => selectedFilter !== filter)
        : [...previous, filter]
    ));
  }, []);

  const clearFilters = useCallback(() => {
    setSearchQuery("");
    setSelectedCourseProgram(ALL_COURSE_PROGRAMS);
    setSelectedLearnerLevel(ALL_LEARNER_LEVELS);
    setSelectedSubject(ALL_SUBJECTS);
    setSubjectDraft(ALL_SUBJECTS);
    setSelectedTags([]);
    setTagDraft([]);
    setSelectedSourceFilters([]);
    setSubjectSearchQuery("");
    setTagSearchQuery("");
    replacePublicLibraryFilters({
      audience: null,
      courseProgram: null,
      search: null,
      sort: null,
      subject: null,
      tags: [],
      view: null,
    });
  }, [replacePublicLibraryFilters]);

  const subjectPriorityComparator = useMemo(
    () => buildPriorityComparator(recentSubjects, subjectCounts),
    [recentSubjects, subjectCounts],
  );
  const tagPriorityComparator = useMemo(
    () => buildPriorityComparator(recentTags, tagCounts),
    [recentTags, tagCounts],
  );

  const displayedSubjects = useMemo(() => {
    return [...availableSubjects].sort(subjectPriorityComparator);
  }, [availableSubjects, subjectPriorityComparator]);

  const filteredModalSubjects = useMemo(() => {
    const query = subjectSearchQuery.trim().toLowerCase();
    return displayedSubjects.filter((subject) => (
      query.length === 0 || subject.toLowerCase().includes(query)
    ));
  }, [displayedSubjects, subjectSearchQuery]);

  const displayedTags = useMemo(() => {
    return [...availableTags].sort(tagPriorityComparator);
  }, [availableTags, tagPriorityComparator]);

  const filteredModalTags = useMemo(() => {
    const query = tagSearchQuery.trim().toLowerCase();
    return displayedTags.filter((tag) => (
      query.length === 0 || tag.toLowerCase().includes(query)
    ));
  }, [displayedTags, tagSearchQuery]);

  const visibleSubjectLimit = isMobileSuccessSheet
    ? POPULAR_SUBJECT_LIMIT_MOBILE
    : POPULAR_SUBJECT_LIMIT_DESKTOP;
  const visibleTagLimit = isMobileSuccessSheet
    ? POPULAR_TAG_LIMIT_MOBILE
    : POPULAR_TAG_LIMIT_DESKTOP;

  const visibleSubjectChips = useMemo(() => {
    const ordered = selectedSubject !== ALL_SUBJECTS
      ? [selectedSubject, ...displayedSubjects.filter((subject) => subject !== selectedSubject)]
      : displayedSubjects;
    return Array.from(new Set(ordered)).slice(0, visibleSubjectLimit);
  }, [displayedSubjects, selectedSubject, visibleSubjectLimit]);

  const remainingSubjectCount = useMemo(() => {
    const visible = new Set(visibleSubjectChips);
    return displayedSubjects.filter((subject) => !visible.has(subject)).length;
  }, [displayedSubjects, visibleSubjectChips]);

  const visiblePopularTags = useMemo(() => {
    const ordered = [
      ...selectedTags,
      ...displayedTags.filter((tag) => !selectedTags.includes(tag)),
    ];
    return Array.from(new Set(ordered)).slice(0, Math.max(visibleTagLimit, selectedTags.length));
  }, [displayedTags, selectedTags, visibleTagLimit]);

  const remainingTagCount = useMemo(() => {
    const visible = new Set(visiblePopularTags);
    return displayedTags.filter((tag) => !visible.has(tag)).length;
  }, [displayedTags, visiblePopularTags]);

  const activeFilterCount = countActivePublicFilterGroups({
    courseProgram: selectedCourseProgram,
    learnerLevel: selectedLearnerLevel,
    subject: selectedSubject,
    tags: selectedTags,
    sourceFilters: selectedSourceFilters,
  });

  const hasActiveFilters = searchQuery.trim().length > 0
    || selectedCourseProgram !== ALL_COURSE_PROGRAMS
    || selectedLearnerLevel !== ALL_LEARNER_LEVELS
    || selectedSubject !== ALL_SUBJECTS
    || selectedTags.length > 0
    || selectedSourceFilters.length > 0;
  const activeDiscoveryView = resolveDiscoveryView(parsedUrlFilters.view);

  // Discovery mode: no active search/filter and default sort → show discovery sections
  const isDiscoveryMode = !hasActiveFilters && selectedSort === "NEWEST";
  const isSectionView = isDiscoveryMode && activeDiscoveryView !== null;

  const featuredRankedNotes = useMemo(
    () => getFeaturedNotes(items, items.length),
    [items],
  );
  const featuredNotes = useMemo(
    () => featuredRankedNotes.slice(0, FEATURED_NOTES_LIMIT),
    [featuredRankedNotes],
  );
  const popularRankedNotes = useMemo(() => {
    const featuredIds = new Set(featuredNotes.map((n) => n.id));
    return getPopularNotes(excludeById(items, featuredIds), items.length);
  }, [items, featuredNotes]);
  const popularNotes = useMemo(
    () => popularRankedNotes.slice(0, POPULAR_NOTES_LIMIT),
    [popularRankedNotes],
  );
  const recentRankedNotes = useMemo(() => {
    const usedIds = new Set([...featuredNotes, ...popularNotes].map((n) => n.id));
    return getRecentNotes(excludeById(items, usedIds), items.length);
  }, [items, featuredNotes, popularNotes]);
  const recentNotes = useMemo(
    () => recentRankedNotes.slice(0, RECENT_NOTES_LIMIT),
    [recentRankedNotes],
  );
  const sectionViewItems = useMemo(() => {
    switch (activeDiscoveryView) {
      case "featured":
        return featuredRankedNotes;
      case "popular":
        return popularRankedNotes;
      case "recent":
        return recentRankedNotes;
      default:
        return [];
    }
  }, [activeDiscoveryView, featuredRankedNotes, popularRankedNotes, recentRankedNotes]);

  const filteredItems = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    const selectedCourseProgramLookup = selectedCourseProgram === ALL_COURSE_PROGRAMS
      ? null
      : normalizeCourseProgram(selectedCourseProgram)?.toLowerCase() ?? null;
    return items.filter((item) => {
      const title = item.title?.trim() || "Untitled note";
      const tags = normalizeTags(item.tags);
      const courseProgram = normalizeCourseProgram(item.courseProgram);
      const courseProgramLookup = courseProgram?.toLowerCase() ?? null;
      const learnerLevelLabel = formatLearnerLevel(item.learnerLevel);
      const normalizedSubject = normalizeSubject(item.subject);
      const titleMatch = query.length === 0
        || title.toLowerCase().includes(query)
        || (courseProgram?.toLowerCase().includes(query) ?? false)
        || (learnerLevelLabel?.toLowerCase().includes(query) ?? false)
        || (normalizedSubject?.toLowerCase().includes(query) ?? false)
        || item.contentPreview.toLowerCase().includes(query)
        || tags.some((tag) => tag.toLowerCase().includes(query));
      const courseProgramMatch = selectedCourseProgramLookup === null
        || courseProgramLookup === selectedCourseProgramLookup;
      const learnerLevelMatch = selectedLearnerLevel === ALL_LEARNER_LEVELS
        || item.learnerLevel === selectedLearnerLevel;
      const subjectMatch = selectedSubject === ALL_SUBJECTS
        || normalizedSubject === selectedSubject;
      const tagMatch = selectedTags.length === 0
        || selectedTags.some((selectedTag) => tags.includes(selectedTag));
      const sourceMatch = selectedSourceFilters.length === 0
        || selectedSourceFilters.some((filter) => (
          filter === "BY_YOU"
            ? item.ownerUserId === currentUserId || item.isCurrentUser
            : filter === "OFFICIAL"
              ? item.isOfficialAuthor
              : !(item.ownerUserId === currentUserId || item.isCurrentUser || item.isOfficialAuthor)
        ));

      return titleMatch && courseProgramMatch && learnerLevelMatch && subjectMatch && tagMatch && sourceMatch;
    });
  }, [currentUserId, items, searchQuery, selectedCourseProgram, selectedLearnerLevel, selectedSourceFilters, selectedSubject, selectedTags]);

  const sortedItems = useMemo(() => {
    const byNewest = (left: NoteListItemResponse, right: NoteListItemResponse) => (
      new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime()
    );
    const metricValue = (
      item: NoteListItemResponse,
      key: "copyCount" | "viewCount",
    ) => item[key] ?? 0;

    return [...filteredItems].sort((left, right) => {
      switch (selectedSort) {
        case "TITLE_ASC":
          return (left.title ?? "Untitled note").localeCompare(right.title ?? "Untitled note");
        case "MOST_COPIED": {
          const primaryDelta = metricValue(right, "copyCount") - metricValue(left, "copyCount");
          if (primaryDelta !== 0) {
            return primaryDelta;
          }
          return byNewest(left, right);
        }
        case "MOST_VIEWED": {
          const primaryDelta = metricValue(right, "viewCount") - metricValue(left, "viewCount");
          if (primaryDelta !== 0) {
            return primaryDelta;
          }
          return byNewest(left, right);
        }
        case "NEWEST":
        default:
          return byNewest(left, right);
      }
    });
  }, [filteredItems, selectedSort]);

  const activeFilterSummary = hasActiveFilters ? (
    <div className="flex flex-wrap items-center gap-2">
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

      {selectedLearnerLevel !== ALL_LEARNER_LEVELS ? (
        <span className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs">
          Level: {formatLearnerLevel(selectedLearnerLevel)}
          <button
            type="button"
            className="text-foreground/65 hover:text-foreground"
            onClick={() => setSelectedLearnerLevel(ALL_LEARNER_LEVELS)}
            aria-label="Clear learner level filter"
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
    }, searchParamsKey));
  }, [parsedUrlFilters, router, searchParamsKey, startRouteProgress]);
  const clearDiscoveryView = useCallback(() => {
    startRouteProgress();
    router.push(buildPublicLibraryUrl({
      ...parsedUrlFilters,
      view: null,
    }, searchParamsKey));
  }, [parsedUrlFilters, router, searchParamsKey, startRouteProgress]);
  const activeSectionCopy = activeDiscoveryView === null ? null : DISCOVERY_SECTION_COPY[activeDiscoveryView];
  const currentPublicLibraryPath = useMemo(
    () => buildPublicLibraryUrl(parsedUrlFilters, searchParamsKey),
    [parsedUrlFilters, searchParamsKey],
  );
  const resolvedShareUrl = useMemo(() => {
    if (globalThis.window === undefined) {
      return currentPublicLibraryPath;
    }
    return new URL(currentPublicLibraryPath, globalThis.location.origin).toString();
  }, [currentPublicLibraryPath]);
  const truncatedShareUrl = useMemo(() => {
    if (resolvedShareUrl.length <= 58) {
      return resolvedShareUrl;
    }
    return `${resolvedShareUrl.slice(0, 55)}...`;
  }, [resolvedShareUrl]);

  const handleCopyShareLink = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(resolvedShareUrl);
      setShareState("copied");
    } catch {
      setShareState("error");
    }
  }, [resolvedShareUrl]);

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <PageHeader
        eyebrow="LIBRARY"
        title="Public Library"
        description="Explore public notes from you, the community, and official NoteLib examples. Copy a note into your library when you want to study it in your own workspace."
        actions={(
          <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setShareModalOpen(true)}>
            {SHARE_PUBLIC_LIBRARY_LABEL}
          </Button>
        )}
        brandLogo
      />

      <AppModal
        isOpen={shareModalOpen}
        title={SHARE_PUBLIC_LIBRARY_MODAL_TITLE}
        onClose={() => {
          setShareModalOpen(false);
          setShareState("idle");
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <ResponsiveActionButton
              type="button"
              variant="outline"
              onClick={() => {
                setShareModalOpen(false);
                setShareState("idle");
              }}
              action="back"
              label="Close"
              showTextOnMobile
            />
            <ResponsiveActionButton
              type="button"
              onClick={() => void handleCopyShareLink()}
              action="share"
              label={shareState === "copied" ? "Copied" : "Copy Link"}
            />
          </div>
        )}
      >
        <div className="space-y-2">
          <p className="text-xs uppercase tracking-wide text-foreground/60">{SHAREABLE_URL_LABEL}</p>
          <p className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground/85">
            {truncatedShareUrl}
          </p>
          {shareState === "copied" ? (
            <p className="text-xs text-emerald-700 dark:text-emerald-300">{SHARE_LINK_COPIED_MESSAGE}</p>
          ) : null}
          {shareState === "error" ? (
            <p className="text-xs text-red-600 dark:text-red-400">{SHARE_PUBLIC_LIBRARY_COPY_ERROR}</p>
          ) : null}
        </div>
      </AppModal>

      <GuidanceTip
        tipId="public-library-intro"
        message="Browse notes created by others. Copy any note into your library to study it in your own workspace — full Study Pack included."
      />

      {loading ? (
        <div className="grid gap-4 md:grid-cols-2">
          {Array.from({ length: 4 }).map((_, index) => (
            <Card key={`public-library-loading-${index}`} className="space-y-3 p-4 sm:p-6">
              <Skeleton className="h-5 w-2/3" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-1/2" />
            </Card>
          ))}
        </div>
      ) : error ? (
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
                <input
                  id="public-library-search"
                  type="search"
                  value={searchQuery}
                  onChange={(event) => {
                    const nextSearch = event.target.value;
                    setSearchQuery(nextSearch);
                    replacePublicLibraryFilters({
                      ...parsedUrlFilters,
                      search: nextSearch,
                      view: null,
                    });
                  }}
                  placeholder="Search public notes..."
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
                />
              </div>

              <div className="grid grid-cols-2 gap-2 sm:flex sm:shrink-0">
                <Button
                  type="button"
                  variant="outline"
                  className="w-full sm:min-w-30"
                  onClick={() => setFilterSheetOpen(true)}
                  aria-label="Open filters"
                >
                  <span className="inline-flex items-center gap-2">
                    <Filter className="h-4 w-4" aria-hidden="true" />
                    <span>Filter</span>
                    {activeFilterCount > 0 ? (
                      <span className="inline-flex min-w-5 items-center justify-center rounded-full bg-blue-600 px-1.5 py-0.5 text-[11px] font-semibold text-white dark:bg-blue-500">
                        {activeFilterCount}
                      </span>
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

            <div className="space-y-2">
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-medium">For</p>
                {selectedTargetProfile !== NOTE_TARGET_PROFILE_ALL ? (
                  <button
                    type="button"
                    className="shrink-0 text-xs font-medium text-blue-700 hover:text-blue-800 dark:text-blue-300 dark:hover:text-blue-200"
                    onClick={() => {
                      setSelectedTargetProfile(NOTE_TARGET_PROFILE_ALL);
                      replacePublicLibraryFilters({
                        ...parsedUrlFilters,
                        audience: null,
                        view: null,
                      });
                    }}
                  >
                    View all notes
                  </button>
                ) : null}
              </div>
              <div className={getScrollRailClassName()}>
                <button
                  type="button"
                  className={getFilterChipClassName(selectedTargetProfile === NOTE_TARGET_PROFILE_ALL)}
                  onClick={() => {
                    setSelectedTargetProfile(NOTE_TARGET_PROFILE_ALL);
                    replacePublicLibraryFilters({
                      ...parsedUrlFilters,
                      audience: null,
                      view: null,
                    });
                  }}
                  aria-pressed={selectedTargetProfile === NOTE_TARGET_PROFILE_ALL}
                >
                  All
                </button>
                {PUBLIC_NOTE_TARGET_PROFILE_TYPES.map((targetProfileType) => (
                  <button
                    key={targetProfileType}
                    type="button"
                    className={getFilterChipClassName(selectedTargetProfile === targetProfileType)}
                    onClick={() => {
                      setSelectedTargetProfile(targetProfileType);
                      replacePublicLibraryFilters({
                        ...parsedUrlFilters,
                        audience: targetProfileType,
                        view: null,
                      });
                    }}
                    aria-pressed={selectedTargetProfile === targetProfileType}
                  >
                    {getNoteTargetProfileLabel(targetProfileType)}
                  </button>
                ))}
              </div>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-medium">Subjects</p>
                {selectedSubject !== ALL_SUBJECTS ? (
                  <button
                    type="button"
                    className="shrink-0 text-xs font-medium text-blue-700 hover:text-blue-800 dark:text-blue-300 dark:hover:text-blue-200"
                    onClick={() => {
                      setSelectedSubject(ALL_SUBJECTS);
                      replacePublicLibraryFilters({
                        ...parsedUrlFilters,
                        subject: null,
                        view: null,
                      });
                    }}
                  >
                    Reset
                  </button>
                ) : null}
              </div>
              <div className={getScrollRailClassName()}>
                <button
                  type="button"
                  className={getFilterChipClassName(selectedSubject === ALL_SUBJECTS)}
                  onClick={() => {
                    setSelectedSubject(ALL_SUBJECTS);
                    replacePublicLibraryFilters({
                      ...parsedUrlFilters,
                      subject: null,
                      view: null,
                    });
                  }}
                  aria-pressed={selectedSubject === ALL_SUBJECTS}
                >
                  All
                </button>
                {visibleSubjectChips.map((subject) => (
                  <button
                    key={subject}
                    type="button"
                    className={getFilterChipClassName(selectedSubject === subject)}
                    onClick={() => {
                      setSelectedSubject(subject);
                      setRecentSubjects((previous) => updateRecentValues(previous, [subject]));
                      replacePublicLibraryFilters({
                        ...parsedUrlFilters,
                        subject: slugifyPublicLibraryFilterValue(subject),
                        view: null,
                      });
                    }}
                    aria-pressed={selectedSubject === subject}
                  >
                    {subject}
                  </button>
                ))}
                {remainingSubjectCount > 0 ? (
                  <button
                    type="button"
                    className={getFilterChipClassName(false)}
                    onClick={() => setSubjectSelectorOpen(true)}
                  >
                    {MORE_SUBJECTS_LABEL}
                  </button>
                ) : null}
              </div>
            </div>

            {availableTags.length > 0 ? (
              <div className="space-y-2">
                <div className="flex items-center justify-between gap-3">
                  <p className="text-sm font-medium">Popular Tags</p>
                  {selectedTags.length > 0 ? (
                    <button
                      type="button"
                      className="shrink-0 text-xs font-medium text-blue-700 hover:text-blue-800 dark:text-blue-300 dark:hover:text-blue-200"
                      onClick={() => {
                        setSelectedTags([]);
                        setTagDraft([]);
                        replacePublicLibraryFilters({
                          ...parsedUrlFilters,
                          tags: [],
                          view: null,
                        });
                      }}
                    >
                      Clear tags
                    </button>
                  ) : null}
                </div>
                <div className={getScrollRailClassName()}>
                  {visiblePopularTags.map((tag) => (
                    <button
                      key={tag}
                      type="button"
                    className={getFilterChipClassName(selectedTags.includes(tag))}
                    onClick={() => {
                      setSelectedTags((previous) => {
                        const next = previous.includes(tag)
                          ? previous.filter((selectedTag) => selectedTag !== tag)
                          : [...previous, tag];
                        if (!previous.includes(tag)) {
                          setRecentTags((recentPrevious) => updateRecentValues(recentPrevious, [tag]));
                        }
                        replacePublicLibraryFilters({
                          ...parsedUrlFilters,
                          tags: next.map((selectedTag) => slugifyPublicLibraryFilterValue(selectedTag)),
                          view: null,
                        });
                        return next;
                      });
                    }}
                      aria-pressed={selectedTags.includes(tag)}
                    >
                      {tag}
                    </button>
                  ))}
                  {remainingTagCount > 0 ? (
                    <button
                      type="button"
                      className={getFilterChipClassName(false)}
                      onClick={() => setTagSelectorOpen(true)}
                    >
                      {MORE_TAGS_LABEL}
                    </button>
                  ) : null}
                </div>
              </div>
            ) : null}

            <div className="flex flex-col gap-3 border-t border-border pt-3">
              <p className="text-xs text-foreground/60">Sorted by {PUBLIC_SORT_LABELS[selectedSort]}</p>
              {activeFilterSummary}
            </div>
          </Card>

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

              <div className="grid gap-4 md:grid-cols-2">
                {sectionViewItems.map((item) => (
                  <PublicNoteCard
                    key={item.id}
                    item={item}
                    currentUserId={currentUserId}
                    onNavigate={(path) => {
                      startRouteProgress();
                      router.push(path);
                    }}
                    existingCopyNoteId={copiedNoteIdsBySourceId[item.id] ?? null}
                    onCopySuccess={handleCopySuccess}
                    onLikeSuccess={handleLikeSuccess}
                  />
                ))}
              </div>
            </div>
          ) : isDiscoveryMode ? (
            <div className="space-y-10">
              {items.length === 0 ? (
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
                  onNavigate={(path) => {
                    startRouteProgress();
                    router.push(path);
                  }}
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
                  onNavigate={(path) => {
                    startRouteProgress();
                    router.push(path);
                  }}
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
                  onNavigate={(path) => {
                    startRouteProgress();
                    router.push(path);
                  }}
                  onViewMore={() => openDiscoveryView("recent")}
                  copiedNoteIdsBySourceId={copiedNoteIdsBySourceId}
                  onCopySuccess={handleCopySuccess}
                  onLikeSuccess={handleLikeSuccess}
                />
              ) : null}
            </div>
          ) : sortedItems.length === 0 ? (
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
                ) : (
                  <>
                    <h2 className="text-base font-semibold sm:text-lg">No public notes match your filters.</h2>
                    <p className="text-sm text-foreground/75">Try adjusting search or filters.</p>
                    <Button type="button" variant="outline" onClick={clearFilters} className="w-full sm:w-auto">
                      Clear filters
                    </Button>
                  </>
                )}
            </Card>
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {sortedItems.map((item) => (
                <PublicNoteCard
                  key={item.id}
                  item={item}
                  currentUserId={currentUserId}
                  onNavigate={(path) => {
                    startRouteProgress();
                    router.push(path);
                  }}
                  existingCopyNoteId={copiedNoteIdsBySourceId[item.id] ?? null}
                  onCopySuccess={handleCopySuccess}
                  onLikeSuccess={handleLikeSuccess}
                />
              ))}
            </div>
          )}
        </div>
      )}

      <LibrarySheetModal
        isOpen={filterSheetOpen}
        title="Filter public notes"
        onClose={() => setFilterSheetOpen(false)}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={clearFilters}>
              Clear filters
            </Button>
            <Button type="button" onClick={() => setFilterSheetOpen(false)}>
              Done
            </Button>
          </div>
        )}
      >
        <div className="space-y-2">
          <label htmlFor="public-library-filter-course-program" className="text-sm font-medium">
            Course / Program
          </label>
          <select
            id="public-library-filter-course-program"
            value={selectedCourseProgram}
            onChange={(event) => {
              const nextCourseProgram = event.target.value;
              setSelectedCourseProgram(nextCourseProgram);
              replacePublicLibraryFilters({
                ...parsedUrlFilters,
                courseProgram: nextCourseProgram === ALL_COURSE_PROGRAMS
                  ? null
                  : slugifyPublicLibraryFilterValue(nextCourseProgram),
                view: null,
              });
            }}
            className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus:ring-2 focus:ring-blue-600"
          >
            <option value={ALL_COURSE_PROGRAMS}>All course/programs</option>
            {availableCoursePrograms.map((courseProgram) => (
              <option key={courseProgram} value={courseProgram}>
                {courseProgram}
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-2">
          <label htmlFor="public-library-filter-learner-level" className="text-sm font-medium">
            Learner Level
          </label>
          <select
            id="public-library-filter-learner-level"
            value={selectedLearnerLevel}
            onChange={(event) => setSelectedLearnerLevel(event.target.value)}
            className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus:ring-2 focus:ring-blue-600"
          >
            <option value={ALL_LEARNER_LEVELS}>All learner levels</option>
            {availableLearnerLevels.map((learnerLevel) => (
              <option key={learnerLevel} value={learnerLevel}>
                {formatLearnerLevel(learnerLevel)}
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-2">
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
      </LibrarySheetModal>

      <LibrarySheetModal
        isOpen={subjectSelectorOpen}
        title={SUBJECT_SELECTOR_TITLE}
        onClose={() => setSubjectSelectorOpen(false)}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={() => setSubjectDraft(ALL_SUBJECTS)}>
              Clear
            </Button>
            <Button
              type="button"
              onClick={() => {
                setSelectedSubject(subjectDraft);
                if (subjectDraft !== ALL_SUBJECTS) {
                  setRecentSubjects((previous) => updateRecentValues(previous, [subjectDraft]));
                }
                replacePublicLibraryFilters({
                  ...parsedUrlFilters,
                  subject: subjectDraft === ALL_SUBJECTS ? null : slugifyPublicLibraryFilterValue(subjectDraft),
                  view: null,
                });
                setSubjectSelectorOpen(false);
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
            value={subjectSearchQuery}
            onChange={(event) => setSubjectSearchQuery(event.target.value)}
            placeholder="Search subjects..."
            className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
          />
          {subjectDraft !== ALL_SUBJECTS ? (
            <div className="space-y-2">
              <p className="text-xs font-medium uppercase tracking-wide text-foreground/55">Selected subject</p>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  className={getFilterChipClassName(true)}
                  onClick={() => setSubjectDraft(ALL_SUBJECTS)}
                >
                  {subjectDraft}
                </button>
              </div>
            </div>
          ) : null}
        </div>
        {filteredModalSubjects.length === 0 ? (
          <p className="text-sm text-foreground/65">No subjects match your search.</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className={getFilterChipClassName(subjectDraft === ALL_SUBJECTS)}
              onClick={() => setSubjectDraft(ALL_SUBJECTS)}
              aria-pressed={subjectDraft === ALL_SUBJECTS}
            >
              All
            </button>
            {filteredModalSubjects.map((subject) => (
              <button
                key={subject}
                type="button"
                className={getFilterChipClassName(subjectDraft === subject)}
                onClick={() => setSubjectDraft(subject)}
                aria-pressed={subjectDraft === subject}
              >
                {subject}
              </button>
            ))}
          </div>
        )}
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
              <div className={getScrollRailClassName()}>
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
          {(Object.entries(PUBLIC_SORT_LABELS) as Array<[PublicLibrarySortOption, string]>).map(([value, label]) => {
            const isSelected = selectedSort === value;
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
        headerActions={(
          <button
            type="button"
            aria-label={CLOSE_MODAL_LABEL}
            className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-border/80 bg-background/90 text-foreground/60 transition-colors hover:bg-highlight hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2"
            onClick={() => setCopySuccessState(null)}
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        )}
        enableSwipeToClose={isMobileSuccessSheet}
        actions={copySuccessState ? (
          <div className="flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-end">
            <ResponsiveActionButton
              type="button"
              variant="outline"
              className="w-full sm:w-auto"
              action="library"
              label={MODAL_VIEW_NOTE_LABEL}
              onClick={() => {
                startRouteProgress();
                router.push(buildCopiedNotePath(copySuccessState.copiedNoteId, "library"));
              }}
              showTextOnMobile
            />
            <ResponsiveActionButton
              type="button"
              className="w-full sm:w-auto"
              action="quickReview"
              label={MODAL_START_REVIEW_LABEL}
              onClick={() => {
                startRouteProgress();
                router.push(buildCopiedNotePath(copySuccessState.copiedNoteId, "quick-review"));
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
    </main>
  );
}
