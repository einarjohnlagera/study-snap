"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import { Check, Lock, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PracticeQuizCard } from "@/components/study-pack/practice-quiz-card";
import { getAuthUser } from "@/lib/auth";
import { PLAN_BILLING_PATH } from "@/lib/plans";
import { requireVerifiedOnboardedUser } from "@/lib/route-guards";
import {
  createStudyPackShareLink,
  getQuickReviewPerformanceSummary,
  getInProgressQuickReviewSession,
  getMyStudyPack,
  listRecentQuickReviewSessions,
  startQuickReviewSession,
  updateStudyPackMetadata,
  updateStudyPackTags,
  type QuickReviewPerformanceSummaryResponse,
  type QuickReviewSessionSummaryResponse,
  type StudyPackResponse,
} from "@/lib/api";

const TAG_MAX_LENGTH = 30;

function StudyPackDetailLoading() {
  return (
    <div className="space-y-6">
      <Card className="space-y-3">
        <div className="h-4 w-28 animate-pulse rounded bg-foreground/10" />
        <div className="h-8 w-2/3 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-1/3 animate-pulse rounded bg-foreground/10" />
      </Card>
      <Card className="space-y-3">
        <div className="h-6 w-28 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-5/6 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-2/3 animate-pulse rounded bg-foreground/10" />
      </Card>
      <Card className="space-y-3">
        <div className="h-6 w-36 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-3/4 animate-pulse rounded bg-foreground/10" />
      </Card>
      <Card className="space-y-4">
        <div className="h-6 w-24 animate-pulse rounded bg-foreground/10" />
        {Array.from({ length: 3 }).map((_, index) => (
          <div key={`quiz-skeleton-${index}`} className="space-y-2">
            <div className="h-4 w-3/4 animate-pulse rounded bg-foreground/10" />
            <div className="h-4 w-1/2 animate-pulse rounded bg-foreground/10" />
            <div className="h-4 w-2/3 animate-pulse rounded bg-foreground/10" />
          </div>
        ))}
      </Card>
    </div>
  );
}

export default function StudyPackDetailPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const params = useParams<{ id: string }>();
  const [studyPack, setStudyPack] = useState<StudyPackResponse | null>(null);
  const [recentSessions, setRecentSessions] = useState<QuickReviewSessionSummaryResponse[]>([]);
  const [performanceSummary, setPerformanceSummary] = useState<QuickReviewPerformanceSummaryResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [performanceError, setPerformanceError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [startingQuickReview, setStartingQuickReview] = useState(false);
  const [hasInProgressQuickReview, setHasInProgressQuickReview] = useState(false);
  const [creatingShareLink, setCreatingShareLink] = useState(false);
  const [updatingTags, setUpdatingTags] = useState(false);
  const [updatingMetadata, setUpdatingMetadata] = useState(false);
  const [editingField, setEditingField] = useState<"title" | "subject" | null>(null);
  const [metadataTitle, setMetadataTitle] = useState("");
  const [metadataSubject, setMetadataSubject] = useState("");
  const [metadataError, setMetadataError] = useState<string | null>(null);
  const [addingTag, setAddingTag] = useState(false);
  const [newTagValue, setNewTagValue] = useState("");
  const [tagError, setTagError] = useState<string | null>(null);
  const [shareError, setShareError] = useState<string | null>(null);
  const [shareToast, setShareToast] = useState<string | null>(null);
  const [showOriginalNotes, setShowOriginalNotes] = useState(false);
  const [isPremiumUser, setIsPremiumUser] = useState(false);
  const [challengeHint, setChallengeHint] = useState<string | null>(null);
  const shareToastTimeoutRef = useRef<number | null>(null);
  const navigationOrigin = searchParams.get("from");
  const backNavigation = useMemo(() => {
    if (navigationOrigin === "dashboard") {
      return {
        href: "/dashboard",
        label: "Back to Dashboard",
      };
    }
    return {
      href: "/library",
      label: "Back to Library",
    };
  }, [navigationOrigin]);

  const studyPackId = useMemo(() => {
    if (!params?.id) {
      return "";
    }
    return Array.isArray(params.id) ? params.id[0] : params.id;
  }, [params]);

  const loadStudyPack = useCallback(async () => {
    if (!studyPackId) {
      setError("Study Pack not found.");
      setLoading(false);
      return;
    }

    if (!requireVerifiedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const detail = await getMyStudyPack(studyPackId);
      setStudyPack(detail);
      setTagError(null);
      setAddingTag(false);
      setNewTagValue("");
      try {
        const history = await listRecentQuickReviewSessions(studyPackId, 5);
        setRecentSessions(history);
        setHistoryError(null);
      } catch (historyErr) {
        const message = historyErr instanceof Error ? historyErr.message : "Could not load recent sessions.";
        setHistoryError(message);
        setRecentSessions([]);
      }
      try {
        const summary = await getQuickReviewPerformanceSummary(studyPackId);
        setPerformanceSummary(summary);
        setPerformanceError(null);
      } catch (performanceErr) {
        const message = performanceErr instanceof Error ? performanceErr.message : "Could not load review performance.";
        setPerformanceError(message);
        setPerformanceSummary(null);
      }
      try {
        const inProgress = await getInProgressQuickReviewSession(studyPackId);
        setHasInProgressQuickReview(Boolean(inProgress.sessionId));
      } catch {
        setHasInProgressQuickReview(false);
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load this Study Pack.";
      setError(message);
      setStudyPack(null);
      setRecentSessions([]);
      setPerformanceSummary(null);
      setHistoryError(null);
      setPerformanceError(null);
      setHasInProgressQuickReview(false);
      setTagError(null);
      setAddingTag(false);
      setNewTagValue("");
    } finally {
      setLoading(false);
    }
  }, [router, studyPackId]);

  useEffect(() => {
    void loadStudyPack();
  }, [loadStudyPack]);

  useEffect(() => {
    return () => {
      if (shareToastTimeoutRef.current !== null) {
        window.clearTimeout(shareToastTimeoutRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (!studyPack) {
      return;
    }
    setMetadataTitle(studyPack.title);
    setMetadataSubject(studyPack.subject ?? "");
    setMetadataError(null);
    setShowOriginalNotes(false);
    setChallengeHint(null);
  }, [studyPack]);

  useEffect(() => {
    const syncPlan = () => {
      const authUser = getAuthUser();
      setIsPremiumUser(authUser?.planType === "PREMIUM");
    };
    syncPlan();
    window.addEventListener("studysnap-auth-change", syncPlan);
    return () => {
      window.removeEventListener("studysnap-auth-change", syncPlan);
    };
  }, []);

  const isNotFound = error?.toLowerCase().includes("not found") ?? false;

  const formatScore = (value: number | null) => {
    if (value === null) {
      return "—";
    }
    if (Number.isInteger(value)) {
      return `${value}%`;
    }
    return `${value.toFixed(2).replace(/\.?0+$/, "")}%`;
  };
  const formattedCreatedDate = useMemo(() => {
    if (!studyPack?.createdAt) {
      return "Not available";
    }
    const date = new Date(studyPack.createdAt);
    if (Number.isNaN(date.getTime())) {
      return "Not available";
    }
    return date.toLocaleDateString(undefined, {
      month: "short",
      day: "numeric",
      year: "numeric",
    });
  }, [studyPack?.createdAt]);
  const originalNotesText = useMemo(() => {
    return studyPack?.sourceText?.trim() ?? "";
  }, [studyPack?.sourceText]);
  const hasOriginalNotes = originalNotesText.length > 0;
  const editingMetadata = editingField !== null;

  const latestCompletedSession = recentSessions[0] ?? null;
  const focusAreas = Array.from(
    new Set(
      (latestCompletedSession?.weakConcepts ?? [])
        .map((concept) => concept.trim())
        .filter((concept) => concept.length > 0),
    ),
  ).slice(0, 4);
  const hasWeakConcepts = focusAreas.length > 0;
  const showAdaptivePracticeEntry = isPremiumUser && hasWeakConcepts;

  const suggestedNextStep = (() => {
    if (!latestCompletedSession) {
      return "Start your first Quick Review to discover which concepts need more work.";
    }
    if (hasWeakConcepts) {
      return "Practice weak concepts to strengthen this topic.";
    }
    return "Continue reviewing this Study Pack.";
  })();

  const handleStartQuickReview = async () => {
    if (!studyPack) {
      return;
    }
    setStartingQuickReview(true);
    try {
      const started = await startQuickReviewSession(studyPack.id);
      if (started.sessionId) {
        router.push(`/study-packs/${studyPack.id}/quick-review?sessionId=${started.sessionId}`);
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not start Quick Review.";
      setHistoryError(message);
    } finally {
      setStartingQuickReview(false);
    }
  };

  const handleStartChallengeQuiz = () => {
    if (!studyPack) {
      return;
    }
    if (!isPremiumUser) {
      setChallengeHint("This feature is available in the Premium plan");
      router.push(PLAN_BILLING_PATH);
      return;
    }
    setChallengeHint(null);
    router.push(`/study-packs/${studyPack.id}/challenge-quiz`);
  };

  const handleStartAdaptivePractice = () => {
    if (!studyPack || !showAdaptivePracticeEntry) {
      return;
    }
    router.push(`/study-packs/${studyPack.id}/adaptive-practice`);
  };

  const showShareToast = useCallback((message: string) => {
    setShareToast(message);
    if (shareToastTimeoutRef.current !== null) {
      window.clearTimeout(shareToastTimeoutRef.current);
    }
    shareToastTimeoutRef.current = window.setTimeout(() => {
      setShareToast(null);
      shareToastTimeoutRef.current = null;
    }, 2200);
  }, []);

  useEffect(() => {
    const copied = searchParams.get("copied") === "1";
    const created = searchParams.get("created") === "1";
    if (!copied && !created) {
      return;
    }
    showShareToast(created ? "Study Pack created successfully" : "Study Pack copied to your library.");

    const paramsWithoutCopied = new URLSearchParams(searchParams.toString());
    paramsWithoutCopied.delete("copied");
    paramsWithoutCopied.delete("created");
    const nextUrl = paramsWithoutCopied.size > 0
      ? `${pathname}?${paramsWithoutCopied.toString()}`
      : pathname;
    router.replace(nextUrl);
  }, [pathname, router, searchParams, showShareToast]);

  const handleCopyShareLink = useCallback(async () => {
    if (!studyPack || creatingShareLink) {
      return;
    }

    setCreatingShareLink(true);
    setShareError(null);
    try {
      const share = await createStudyPackShareLink(studyPack.id);
      const shareUrl = share.shareUrl.startsWith("http")
        ? share.shareUrl
        : new URL(share.shareUrl, window.location.origin).toString();

      if (!navigator.clipboard) {
        throw new Error("Clipboard is not available in this browser.");
      }

      await navigator.clipboard.writeText(shareUrl);
      showShareToast("Share link copied");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not copy share link.";
      setShareError(message);
    } finally {
      setCreatingShareLink(false);
    }
  }, [creatingShareLink, showShareToast, studyPack]);

  const persistTags = useCallback(async (nextTags: string[]) => {
    if (!studyPack || updatingTags) {
      return false;
    }

    setUpdatingTags(true);
    setTagError(null);
    try {
      const updatedStudyPack = await updateStudyPackTags(studyPack.id, nextTags);
      setStudyPack(updatedStudyPack);
      return true;
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not update tags.";
      setTagError(message);
      return false;
    } finally {
      setUpdatingTags(false);
    }
  }, [studyPack, updatingTags]);

  const handleSaveMetadata = useCallback(async () => {
    if (!studyPack || updatingMetadata) {
      return;
    }

    const nextTitle = metadataTitle.trim();
    const nextSubject = metadataSubject.trim();
    if (!nextTitle) {
      setMetadataError("Title is required.");
      return;
    }

    setUpdatingMetadata(true);
    setMetadataError(null);
    try {
      const updatedStudyPack = await updateStudyPackMetadata(studyPack.id, {
        title: nextTitle,
        subject: nextSubject.length > 0 ? nextSubject : null,
      });
      setStudyPack(updatedStudyPack);
      setEditingField(null);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not update Study Pack metadata.";
      setMetadataError(message);
    } finally {
      setUpdatingMetadata(false);
    }
  }, [metadataSubject, metadataTitle, studyPack, updatingMetadata]);

  const handleCancelMetadataEdit = useCallback(() => {
    if (studyPack) {
      setMetadataTitle(studyPack.title);
      setMetadataSubject(studyPack.subject ?? "");
    }
    setEditingField(null);
    setMetadataError(null);
  }, [studyPack]);

  const handleAddTag = useCallback(async () => {
    if (!studyPack) {
      return;
    }

    const trimmedTag = newTagValue.trim();
    if (!trimmedTag) {
      setNewTagValue("");
      setAddingTag(false);
      setTagError(null);
      return;
    }
    if (trimmedTag.length > TAG_MAX_LENGTH) {
      setTagError("Tags must be 30 characters or fewer.");
      return;
    }

    const alreadyExists = studyPack.tags.some(
      (tag) => tag.trim().toLowerCase() === trimmedTag.toLowerCase(),
    );
    if (alreadyExists) {
      setNewTagValue("");
      setAddingTag(false);
      setTagError(null);
      return;
    }

    const updated = await persistTags([...studyPack.tags, trimmedTag]);
    if (updated) {
      setNewTagValue("");
      setAddingTag(false);
    }
  }, [newTagValue, persistTags, studyPack]);

  const handleRemoveTag = useCallback(async (tagToRemove: string) => {
    if (!studyPack) {
      return;
    }
    const nextTags = studyPack.tags.filter((tag) => tag !== tagToRemove);
    await persistTags(nextTags);
  }, [persistTags, studyPack]);

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <div className="flex items-center justify-between gap-3">
        <Link href={backNavigation.href} className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400">
          {backNavigation.label}
        </Link>
      </div>

      {loading ? (
        <StudyPackDetailLoading />
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">
            {isNotFound ? "Study Pack not found" : "Could not load this Study Pack"}
          </h1>
          <p className="text-sm text-foreground/75">
            {isNotFound
              ? "This Study Pack is unavailable or does not belong to your account."
              : error}
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            {!isNotFound ? (
              <Button type="button" className="w-full sm:w-auto" onClick={() => void loadStudyPack()}>
                Retry
              </Button>
            ) : null}
            <Link href={backNavigation.href} className="w-full sm:w-auto">
              <Button type="button" variant="outline" className="w-full sm:w-auto">
                {backNavigation.label}
              </Button>
            </Link>
          </div>
        </Card>
      ) : studyPack ? (
        <div className="space-y-6">
          <Card className="space-y-3 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Study Pack
            </p>
            {editingMetadata ? (
              <div className="space-y-3">
                {editingField === "title" ? (
                  <div className="space-y-1">
                  <label htmlFor="study-pack-title" className="text-xs font-medium uppercase tracking-wide text-foreground/60">
                    Title
                  </label>
                  <input
                    id="study-pack-title"
                    type="text"
                    value={metadataTitle}
                    onChange={(event) => setMetadataTitle(event.target.value)}
                    className="h-10 w-full rounded-md border border-border bg-background px-3 text-base text-foreground outline-none transition-colors focus:ring-2 focus:ring-blue-600"
                    disabled={updatingMetadata}
                    autoFocus
                    onKeyDown={(event) => {
                      if (event.key === "Enter") {
                        event.preventDefault();
                        void handleSaveMetadata();
                      } else if (event.key === "Escape") {
                        event.preventDefault();
                        handleCancelMetadataEdit();
                      }
                    }}
                  />
                  </div>
                ) : null}
                {editingField === "subject" ? (
                  <div className="space-y-1">
                  <label htmlFor="study-pack-subject" className="text-xs font-medium uppercase tracking-wide text-foreground/60">
                    Subject
                  </label>
                  <input
                    id="study-pack-subject"
                    type="text"
                    value={metadataSubject}
                    onChange={(event) => setMetadataSubject(event.target.value)}
                    placeholder="Optional subject"
                    className="h-10 w-full rounded-md border border-border bg-background px-3 text-base text-foreground outline-none transition-colors focus:ring-2 focus:ring-blue-600"
                    disabled={updatingMetadata}
                    autoFocus
                    onKeyDown={(event) => {
                      if (event.key === "Enter") {
                        event.preventDefault();
                        void handleSaveMetadata();
                      } else if (event.key === "Escape") {
                        event.preventDefault();
                        handleCancelMetadataEdit();
                      }
                    }}
                  />
                  </div>
                ) : null}
                <div className="flex flex-wrap gap-2">
                  <Button
                    type="button"
                    size="sm"
                    onClick={() => void handleSaveMetadata()}
                    disabled={updatingMetadata}
                    aria-label="Save metadata"
                    className="h-9 w-9 px-0"
                  >
                    <Check className="h-4 w-4" />
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={handleCancelMetadataEdit}
                    disabled={updatingMetadata}
                    aria-label="Cancel metadata edit"
                    className="h-9 w-9 px-0"
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            ) : (
              <>
                <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
                  <button
                    type="button"
                    className="cursor-text text-left hover:text-blue-600 dark:hover:text-blue-400"
                    onClick={() => {
                      setEditingField("title");
                      setMetadataError(null);
                    }}
                    disabled={updatingMetadata}
                  >
                    {studyPack.title}
                  </button>
                </h1>
                <p>
                  <button
                    type="button"
                    className={`text-sm ${studyPack.subject?.trim() ? "text-foreground/75" : "text-foreground/55"} hover:text-blue-600 dark:hover:text-blue-400`}
                    onClick={() => {
                      setEditingField("subject");
                      setMetadataError(null);
                    }}
                    disabled={updatingMetadata}
                  >
                    {studyPack.subject?.trim() || "Add subject"}
                  </button>
                </p>
                <p className="text-sm text-foreground/75">
                  {studyPack.keyConcepts.length} concepts | {studyPack.quiz.length} questions
                </p>
              </>
            )}
            <p className="text-sm text-foreground/65">Created {formattedCreatedDate}</p>
            {metadataError ? <p className="text-xs text-red-600 dark:text-red-400">{metadataError}</p> : null}
            <div className="space-y-2">
              <div className="flex flex-wrap items-center gap-2">
                {studyPack.tags.map((tag, index) => (
                  <span
                    key={`${tag}-${index}`}
                    className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-2 py-1 text-xs text-foreground/75"
                  >
                    {tag}
                    <button
                      type="button"
                      className="text-foreground/60 hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
                      aria-label={`Remove tag ${tag}`}
                      onClick={() => void handleRemoveTag(tag)}
                      disabled={updatingTags}
                    >
                      x
                    </button>
                  </span>
                ))}

                {addingTag ? (
                  <div className="flex flex-wrap items-center gap-2">
                    <input
                      type="text"
                      value={newTagValue}
                      onChange={(event) => setNewTagValue(event.target.value)}
                      placeholder="Enter tag"
                      maxLength={TAG_MAX_LENGTH}
                      className="h-8 w-36 rounded-md border border-border bg-background px-2 text-xs text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
                      onKeyDown={(event) => {
                        if (event.key === "Enter") {
                          event.preventDefault();
                          void handleAddTag();
                        } else if (event.key === "Escape") {
                          setAddingTag(false);
                          setNewTagValue("");
                          setTagError(null);
                        }
                      }}
                      disabled={updatingTags}
                      autoFocus
                    />
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      className="h-8 px-2 text-xs"
                      onClick={() => void handleAddTag()}
                      disabled={updatingTags}
                    >
                      Add
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      className="h-8 px-2 text-xs"
                      onClick={() => {
                        setAddingTag(false);
                        setNewTagValue("");
                        setTagError(null);
                      }}
                      disabled={updatingTags}
                    >
                      Cancel
                    </Button>
                  </div>
                ) : (
                  <button
                    type="button"
                    className="rounded-full border border-dashed border-border px-2 py-1 text-xs text-foreground/70 hover:bg-muted/40 disabled:cursor-not-allowed disabled:opacity-50"
                    onClick={() => {
                      setAddingTag(true);
                      setTagError(null);
                    }}
                    disabled={updatingTags}
                  >
                    + Add tag
                  </button>
                )}
              </div>
              {tagError ? <p className="text-xs text-red-600 dark:text-red-400">{tagError}</p> : null}
            </div>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div className="space-y-2">
                <div className="flex flex-col gap-2 sm:flex-row">
                  <Button type="button" className="w-full sm:w-auto" onClick={() => void handleStartQuickReview()} disabled={startingQuickReview}>
                    {startingQuickReview
                      ? (hasInProgressQuickReview ? "Resuming..." : "Starting...")
                      : (hasInProgressQuickReview ? "Resume Quick Review" : "Start Quick Review")}
                  </Button>
                  <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={handleStartChallengeQuiz}>
                    {!isPremiumUser ? <Lock className="h-4 w-4" aria-hidden="true" /> : null}
                    Start Challenge Quiz
                  </Button>
                  {showAdaptivePracticeEntry ? (
                    <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={handleStartAdaptivePractice}>
                      Practice Weak Concepts
                    </Button>
                  ) : null}
                </div>
                {!isPremiumUser && challengeHint ? (
                  <p className="text-xs text-foreground/70">{challengeHint}</p>
                ) : null}
              </div>
              <div className="space-y-1">
                <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Share Study Pack</p>
                <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => void handleCopyShareLink()} disabled={creatingShareLink}>
                  {creatingShareLink ? "Copying..." : "Copy Link"}
                </Button>
                {shareError ? <p className="text-xs text-red-600 dark:text-red-400">{shareError}</p> : null}
              </div>
            </div>
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Summary</h2>
            <p className="whitespace-pre-wrap text-sm leading-relaxed text-foreground/85">{studyPack.summary}</p>
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-lg font-semibold sm:text-xl">Original Notes</h2>
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => setShowOriginalNotes((previous) => !previous)}
                disabled={!hasOriginalNotes}
              >
                {showOriginalNotes ? "Hide original notes" : "Show original notes"}
              </Button>
            </div>
            {showOriginalNotes ? (
              hasOriginalNotes ? (
                <div className="max-h-80 overflow-y-auto rounded-md border border-border bg-background p-3">
                  <p className="whitespace-pre-wrap text-sm leading-relaxed text-foreground/85">{originalNotesText}</p>
                </div>
              ) : (
                <p className="text-sm text-foreground/75">Original notes are not available for this Study Pack.</p>
              )
            ) : (
              <p className="text-sm text-foreground/75">
                Review the original text used to generate this Study Pack.
              </p>
            )}
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Key Concepts</h2>
            <ul className="list-disc space-y-2 pl-5 text-sm leading-relaxed text-foreground/85">
              {studyPack.keyConcepts.map((concept, index) => (
                <li key={`${studyPack.id}-concept-${index}`}>{concept}</li>
              ))}
            </ul>
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Review Performance</h2>
            {performanceError ? (
              <p className="text-sm text-foreground/75">{performanceError}</p>
            ) : !performanceSummary || performanceSummary.attempts === 0 ? (
              <p className="text-sm text-foreground/75">No Quick Reviews yet. Start your first review.</p>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <div className="rounded-md border border-border bg-background p-3">
                  <p className="text-xs uppercase tracking-wide text-foreground/60">Best Score</p>
                  <p className="mt-1 text-lg font-semibold">{formatScore(performanceSummary.bestScorePercentage)}</p>
                </div>
                <div className="rounded-md border border-border bg-background p-3">
                  <p className="text-xs uppercase tracking-wide text-foreground/60">Attempts</p>
                  <p className="mt-1 text-lg font-semibold">{performanceSummary.attempts}</p>
                </div>
                <div className="rounded-md border border-border bg-background p-3">
                  <p className="text-xs uppercase tracking-wide text-foreground/60">Last Score</p>
                  <p className="mt-1 text-lg font-semibold">{formatScore(performanceSummary.lastScorePercentage)}</p>
                </div>
                <div className="rounded-md border border-border bg-background p-3">
                  <p className="text-xs uppercase tracking-wide text-foreground/60">Last Reviewed</p>
                  <p className="mt-1 text-sm font-medium">
                    {performanceSummary.lastReviewedAt
                      ? new Date(performanceSummary.lastReviewedAt).toLocaleString()
                      : "—"}
                  </p>
                </div>
              </div>
            )}
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">AI Study Coach</h2>
            {historyError ? (
              <p className="text-sm text-foreground/75">{historyError}</p>
            ) : (
              <div className="space-y-3">
                {focusAreas.length > 0 ? (
                  <div className="space-y-2">
                    <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
                      Focus Areas
                    </p>
                    <ul className="list-disc space-y-1 pl-5 text-sm text-foreground/85">
                      {focusAreas.map((concept) => (
                        <li key={`focus-area-${concept}`}>{concept}</li>
                      ))}
                    </ul>
                  </div>
                ) : null}
                <div className="rounded-md border border-border bg-background p-3">
                  <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">
                    Suggested Next Step
                  </p>
                  <p className="mt-2 text-sm text-foreground/80">{suggestedNextStep}</p>
                </div>
              </div>
            )}
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Recent Review Sessions</h2>
            {historyError ? (
              <p className="text-sm text-foreground/75">{historyError}</p>
            ) : recentSessions.length === 0 ? (
              <p className="text-sm text-foreground/75">No completed Quick Review sessions yet.</p>
            ) : (
              <div className="space-y-2">
                {recentSessions.map((session) => (
                  <div
                    key={session.id}
                    className="flex flex-col gap-1 rounded-md border border-border bg-background px-3 py-2 text-sm sm:flex-row sm:items-center sm:justify-between"
                  >
                    <span className="text-foreground/75">
                      {session.completedAt
                        ? new Date(session.completedAt).toLocaleString()
                        : new Date(session.createdAt).toLocaleString()}
                    </span>
                    <span className="font-medium text-foreground">
                      {session.correctAnswers}/{session.totalQuestions} ({session.scorePercentage}%)
                    </span>
                  </div>
                ))}
              </div>
            )}
          </Card>

          <PracticeQuizCard quiz={studyPack.quiz} />
        </div>
      ) : null}
      {shareToast ? (
        <div
          role="status"
          aria-live="polite"
          className="fixed right-4 bottom-4 z-50 rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm"
        >
          {shareToast}
        </div>
      ) : null}
    </main>
  );
}
