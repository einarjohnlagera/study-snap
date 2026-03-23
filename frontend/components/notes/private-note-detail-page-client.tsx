"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { NearLimitBanner } from "@/components/billing/near-limit-banner";
import { PaywallModal, type PaywallModalVariant } from "@/components/billing/paywall-modal";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { AppModal } from "@/components/ui/app-modal";
import { DeleteConfirmationModal } from "@/components/notes/delete-confirmation-modal";
import { PracticeQuizCard } from "@/components/study-pack/practice-quiz-card";
import { getAuthUser } from "@/lib/auth";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import {
  PLAN_BILLING_PATH,
  hasReachedUsageLimit,
  isStudyPackLimitReachedMessage,
  shouldShowNearStudyPackLimitBanner,
} from "@/lib/plans";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import {
  copyNote,
  createStudyPackFromNote,
  deleteNote,
  getChallengeQuizPerformanceSummary,
  getMyStudyPack,
  getNote,
  getQuickReviewPerformanceSummary,
  isEmailNotVerifiedError,
  startQuickReviewSession,
  updateNote,
  updateNoteVisibility,
  type ChallengeQuizPerformanceSummaryResponse,
  type NoteResponse,
  type NoteVisibility,
  type QuickReviewPerformanceSummaryResponse,
} from "@/lib/api";

function stateChip(status: "DRAFT" | "STUDY_PACK_READY") {
  if (status === "STUDY_PACK_READY") {
    return "border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300";
  }
  return "border-border bg-muted/50 text-foreground/70";
}

function visibilityChip(visibility: NoteVisibility) {
  if (visibility === "PUBLIC") {
    return "border-blue-500/40 bg-blue-500/10 text-blue-700 dark:text-blue-300";
  }
  return "border-border bg-muted/50 text-foreground/70";
}

function truncateShareUrl(url: string, maxLength = 58) {
  if (url.length <= maxLength) {
    return url;
  }
  return `${url.slice(0, maxLength - 3)}...`;
}

function buildShareUrl(noteId: string) {
  if (typeof window === "undefined") {
    return `/public/notes/${noteId}`;
  }
  return new URL(`/public/notes/${noteId}`, window.location.origin).toString();
}

function normalizeMetadataInput(value: string): string | null {
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
}

type PrivateNoteDetailPageClientProps = {
  routeId: string;
};

export function PrivateNoteDetailPageClient({ routeId }: PrivateNoteDetailPageClientProps) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const visibilityMenuRef = useRef<HTMLDivElement | null>(null);

  const [note, setNote] = useState<NoteResponse | null>(null);
  const [quickSummary, setQuickSummary] = useState<QuickReviewPerformanceSummaryResponse | null>(null);
  const [challengeSummary, setChallengeSummary] = useState<ChallengeQuizPerformanceSummaryResponse | null>(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const [generating, setGenerating] = useState(false);
  const [copying, setCopying] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [togglingVisibility, setTogglingVisibility] = useState(false);
  const [savingMetadata, setSavingMetadata] = useState(false);

  const [visibilityMenuOpen, setVisibilityMenuOpen] = useState(false);
  const [showMakePublicConfirm, setShowMakePublicConfirm] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [showSharePrivateConfirm, setShowSharePrivateConfirm] = useState(false);
  const [showShareLinkModal, setShowShareLinkModal] = useState(false);
  const [activePaywallModal, setActivePaywallModal] = useState<PaywallModalVariant | null>(null);

  const [shareModalUrl, setShareModalUrl] = useState("");
  const [shareModalCopied, setShareModalCopied] = useState(false);

  const [isPremiumPlan, setIsPremiumPlan] = useState(false);
  const [isEmailVerified, setIsEmailVerified] = useState(false);

  const [isInlineMetadataEditMode, setIsInlineMetadataEditMode] = useState(false);
  const [metadataTagDraft, setMetadataTagDraft] = useState("");
  const [metadataDraft, setMetadataDraft] = useState<{
    title: string;
    subject: string;
    tags: string[];
  }>({
    title: "",
    subject: "",
    tags: [],
  });
  const { usageSummary } = useBillingUsageSummary();

  const normalizedRouteId = useMemo(() => routeId, [routeId]);

  const loadDetail = useCallback(async () => {
    if (!normalizedRouteId) {
      setError("Note not found.");
      setLoading(false);
      return;
    }
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const loadedNote = await getNote(normalizedRouteId);
      setNote(loadedNote);

      if (!loadedNote.quickReviewAvailable) {
        setQuickSummary(null);
        setChallengeSummary(null);
        return;
      }

      const [quick, challenge] = await Promise.allSettled([
        getQuickReviewPerformanceSummary(loadedNote.id),
        getChallengeQuizPerformanceSummary(loadedNote.id),
      ]);
      setQuickSummary(quick.status === "fulfilled" ? quick.value : null);
      setChallengeSummary(challenge.status === "fulfilled" ? challenge.value : null);
    } catch (err) {
      if (pathname.startsWith("/study-packs/")) {
        const byStudyPack = await getMyStudyPack(normalizedRouteId).catch(() => null);
        if (byStudyPack?.noteId) {
          const nextQuery = searchParams.toString();
          router.replace(nextQuery ? `/notes/${byStudyPack.noteId}?${nextQuery}` : `/notes/${byStudyPack.noteId}`);
          return;
        }
      }
      const message = err instanceof Error ? err.message : "Could not load this note.";
      setError(message);
      setNote(null);
      setQuickSummary(null);
      setChallengeSummary(null);
    } finally {
      setLoading(false);
    }
  }, [normalizedRouteId, pathname, router, searchParams]);

  useEffect(() => {
    void loadDetail();
  }, [loadDetail]);

  useEffect(() => {
    const syncAuthState = () => {
      const authUser = getAuthUser();
      setIsPremiumPlan((authUser?.planType ?? "FREE") === "PREMIUM");
      setIsEmailVerified(Boolean(authUser?.emailVerifiedAt));
    };
    syncAuthState();
    window.addEventListener("studysnap-auth-change", syncAuthState);
    return () => {
      window.removeEventListener("studysnap-auth-change", syncAuthState);
    };
  }, []);

  useEffect(() => {
    if (!toast) {
      return;
    }
    const timeout = window.setTimeout(() => setToast(null), 2600);
    return () => window.clearTimeout(timeout);
  }, [toast]);

  useEffect(() => {
    if (!shareModalCopied) {
      return;
    }
    const timeout = window.setTimeout(() => setShareModalCopied(false), 2000);
    return () => window.clearTimeout(timeout);
  }, [shareModalCopied]);

  useEffect(() => {
    if (!visibilityMenuOpen) {
      return;
    }
    const handleOutsideClick = (event: MouseEvent) => {
      const target = event.target as Node;
      if (visibilityMenuRef.current && !visibilityMenuRef.current.contains(target)) {
        setVisibilityMenuOpen(false);
      }
    };
    window.addEventListener("mousedown", handleOutsideClick);
    return () => window.removeEventListener("mousedown", handleOutsideClick);
  }, [visibilityMenuOpen]);

  useEffect(() => {
    const created = searchParams.get("created") === "1";
    const copied = searchParams.get("copied") === "1";
    const saved = searchParams.get("saved") === "1";
    if (!created && !copied && !saved) {
      return;
    }

    if (created) {
      setToast("Study Pack generated successfully.");
    } else if (copied) {
      setToast("Copied to My Library");
    } else {
      setToast("Note saved.");
    }

    const next = new URLSearchParams(searchParams.toString());
    next.delete("created");
    next.delete("copied");
    next.delete("saved");
    router.replace(next.size > 0 ? `${pathname}?${next.toString()}` : pathname);
  }, [pathname, router, searchParams]);

  useEffect(() => {
    if (!note || isInlineMetadataEditMode) {
      return;
    }
    setMetadataDraft({
      title: note.title ?? "",
      subject: note.subject ?? "",
      tags: [...(note.tags ?? [])],
    });
  }, [isInlineMetadataEditMode, note]);

  const isDraft = note?.studyPackStatus !== "STUDY_PACK_READY";
  const title = note?.title?.trim() || "Untitled note";
  const subject = note?.subject?.trim() || "No subject";
  const tags = note?.tags ?? [];
  const visibility = (note?.visibility ?? "PRIVATE") as NoteVisibility;
  const isPublic = visibility === "PUBLIC";
  const canManageVisibility = isEmailVerified || isPublic;
  const hasAdaptiveTargets = (challengeSummary?.latestWeakConcepts?.length ?? 0) > 0;
  const hasCopyAttribution = Boolean(note?.copiedFromUserId && note?.copiedFromNoteId);
  const copiedSourceTitle = note?.copiedFromTitle?.trim() || "Untitled note";
  const studyPacksUsed = usageSummary?.studyPacksUsed ?? 0;
  const studyPacksLimit = usageSummary?.studyPacksLimit ?? 0;
  const hasReachedStudyPackLimit = usageSummary?.planType === "FREE"
    && hasReachedUsageLimit(studyPacksUsed, studyPacksLimit);
  const shouldShowNearLimitBanner = usageSummary
    ? shouldShowNearStudyPackLimitBanner(usageSummary.planType, studyPacksUsed, studyPacksLimit)
    : false;

  const performVisibilityUpdate = useCallback(async (
    nextVisibility: NoteVisibility,
    options: { silentSuccessToast?: boolean } = {},
  ): Promise<NoteResponse | null> => {
    if (!note || togglingVisibility || visibility === nextVisibility) {
      return note ?? null;
    }
    if (nextVisibility === "PUBLIC" && !isEmailVerified) {
      setToast("Verify your email before publishing notes to the Public Library.");
      return null;
    }

    setTogglingVisibility(true);
    setVisibilityMenuOpen(false);
    try {
      const updated = await updateNoteVisibility(note.id, nextVisibility);
      setNote(updated);
      if (!options.silentSuccessToast) {
        setToast(nextVisibility === "PUBLIC" ? "Note is now public." : "Note is now private.");
      }
      return updated;
    } catch (err) {
      if (isEmailNotVerifiedError(err)) {
        setToast("Verify your email before publishing notes to the Public Library.");
      } else {
        const message = err instanceof Error ? err.message : "Could not update note visibility.";
        setError(message);
      }
      return null;
    } finally {
      setTogglingVisibility(false);
    }
  }, [isEmailVerified, note, togglingVisibility, visibility]);

  const handleGenerate = async () => {
    if (!note || generating || !isDraft) {
      return;
    }
    if (!isEmailVerified) {
      setToast("Email verification is required before generating Study Packs.");
      return;
    }
    if (hasReachedStudyPackLimit) {
      setActivePaywallModal("study-pack-limit");
      return;
    }

    setGenerating(true);
    try {
      await createStudyPackFromNote(note.id);
      const next = new URLSearchParams(searchParams.toString());
      next.set("created", "1");
      router.replace(`${pathname}?${next.toString()}`);
      void loadDetail();
    } catch (err) {
      if (isEmailNotVerifiedError(err)) {
        setToast("Email verification is required before generating Study Packs.");
      } else {
        const message = err instanceof Error ? err.message : "Could not generate Study Pack.";
        if (isStudyPackLimitReachedMessage(message)) {
          setActivePaywallModal("study-pack-limit");
        } else {
          setError(message);
        }
      }
    } finally {
      setGenerating(false);
    }
  };

  const handleMakeCopy = async () => {
    if (!note || copying) {
      return;
    }
    setCopying(true);
    try {
      const copied = await copyNote(note.id);
      router.push(`/notes/${copied.id}?copied=1`);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not copy note.";
      setError(message);
    } finally {
      setCopying(false);
    }
  };

  const handleSelectVisibility = (nextVisibility: NoteVisibility) => {
    if (nextVisibility === visibility || togglingVisibility) {
      setVisibilityMenuOpen(false);
      return;
    }
    if (nextVisibility === "PUBLIC") {
      if (!isEmailVerified) {
        setVisibilityMenuOpen(false);
        setToast("Verify your email before publishing notes to the Public Library.");
        return;
      }
      setVisibilityMenuOpen(false);
      setShowMakePublicConfirm(true);
      return;
    }
    void performVisibilityUpdate("PRIVATE");
  };

  const handleEdit = () => {
    if (!note) {
      return;
    }
    if (!isDraft) {
      setMetadataDraft({
        title: note.title ?? "",
        subject: note.subject ?? "",
        tags: [...(note.tags ?? [])],
      });
      setMetadataTagDraft("");
      setIsInlineMetadataEditMode(true);
      return;
    }
    router.push(`/notes/${note.id}/edit`);
  };

  const handleCancelMetadataEdit = () => {
    if (!note || savingMetadata) {
      return;
    }
    setMetadataDraft({
      title: note.title ?? "",
      subject: note.subject ?? "",
      tags: [...(note.tags ?? [])],
    });
    setMetadataTagDraft("");
    setIsInlineMetadataEditMode(false);
  };

  const handleAddMetadataTag = () => {
    const candidate = metadataTagDraft.trim();
    if (candidate.length === 0) {
      return;
    }
    const duplicate = metadataDraft.tags.some((tag) => tag.toLowerCase() === candidate.toLowerCase());
    if (duplicate) {
      setMetadataTagDraft("");
      return;
    }
    setMetadataDraft((previous) => ({
      ...previous,
      tags: [...previous.tags, candidate],
    }));
    setMetadataTagDraft("");
  };

  const handleSaveMetadata = async () => {
    if (!note || savingMetadata) {
      return;
    }
    setSavingMetadata(true);
    try {
      const updated = await updateNote(note.id, {
        title: normalizeMetadataInput(metadataDraft.title),
        subject: normalizeMetadataInput(metadataDraft.subject),
        tags: metadataDraft.tags,
        content: note.content,
      });
      setNote(updated);
      setMetadataDraft({
        title: updated.title ?? "",
        subject: updated.subject ?? "",
        tags: [...(updated.tags ?? [])],
      });
      setMetadataTagDraft("");
      setIsInlineMetadataEditMode(false);
      setToast("Note details updated");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not update note details.";
      setError(message);
    } finally {
      setSavingMetadata(false);
    }
  };

  const handleStartQuickReview = async () => {
    if (!note) {
      return;
    }
    try {
      const started = await startQuickReviewSession(note.id);
      if (started.sessionId) {
        router.push(`/notes/${note.id}/quick-review?sessionId=${started.sessionId}`);
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not start Quick Review.";
      setError(message);
    }
  };

  const handleStartChallengeQuiz = () => {
    if (!note) {
      return;
    }
    if (!isEmailVerified) {
      setToast("Verify your email to use this feature.");
      return;
    }
    if (!isPremiumPlan) {
      setActivePaywallModal("challenge-quiz");
      return;
    }
    router.push(`/notes/${note.id}/challenge-quiz`);
  };

  const handleStartAdaptivePractice = () => {
    if (!note) {
      return;
    }
    if (!isEmailVerified) {
      setToast("Verify your email to use this feature.");
      return;
    }
    if (!isPremiumPlan) {
      setActivePaywallModal("adaptive-practice");
      return;
    }
    router.push(`/notes/${note.id}/adaptive-practice`);
  };

  const handleCopyLink = async () => {
    if (!note || sharing || isInlineMetadataEditMode) {
      return;
    }
    setSharing(true);
    try {
      if (!isPublic) {
        setShowSharePrivateConfirm(true);
        return;
      }
      setShareModalUrl(buildShareUrl(note.id));
      setShowShareLinkModal(true);
      setShareModalCopied(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not create share link.";
      setError(message);
    } finally {
      setSharing(false);
    }
  };

  const handleMakePublicAndShare = async () => {
    if (!note || sharing) {
      return;
    }
    setSharing(true);
    try {
      const updated = await performVisibilityUpdate("PUBLIC", { silentSuccessToast: true });
      if (!updated) {
        return;
      }
      setShowSharePrivateConfirm(false);
      setShareModalUrl(buildShareUrl(updated.id));
      setShowShareLinkModal(true);
      setShareModalCopied(false);
    } finally {
      setSharing(false);
    }
  };

  const handleCopyShareLinkFromModal = async () => {
    if (!shareModalUrl) {
      return;
    }
    try {
      await navigator.clipboard.writeText(shareModalUrl);
      setShareModalCopied(true);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not copy share link.";
      setError(message);
    }
  };

  const handleDeleteNote = async () => {
    if (!note || deleting) {
      return;
    }
    setDeleting(true);
    try {
      await deleteNote(note.id);
      router.push("/library");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not delete note.";
      setError(message);
    } finally {
      setDeleting(false);
      setShowDeleteConfirm(false);
    }
  };

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <Link href="/library" className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400">
        Back to My Library
      </Link>

      {loading ? (
        <Card className="p-6">Loading note...</Card>
      ) : error ? (
        <Card className="space-y-3 p-6">
          <h1 className="text-xl font-semibold">Could not load note</h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <Button type="button" onClick={() => void loadDetail()}>Retry</Button>
        </Card>
      ) : note ? (
        <div className="space-y-6">
          {shouldShowNearLimitBanner ? <NearLimitBanner /> : null}
          <Card className="space-y-4 p-4 sm:p-6">
            <div className="flex items-start justify-between gap-3">
              <div className="space-y-3">
                {isInlineMetadataEditMode ? (
                  <div className="space-y-2">
                    <label htmlFor="note-title-inline" className="text-xs font-semibold uppercase tracking-wide text-foreground/60">
                      Title
                    </label>
                    <input
                      id="note-title-inline"
                      type="text"
                      value={metadataDraft.title}
                      onChange={(event) => setMetadataDraft((previous) => ({ ...previous, title: event.target.value }))}
                      className="h-10 w-full rounded-lg border border-border bg-background px-3 text-base text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600 sm:text-lg"
                      placeholder="Untitled note"
                    />
                  </div>
                ) : (
                  <h1 className="text-2xl font-semibold sm:text-3xl">{title}</h1>
                )}

                {hasCopyAttribution ? (
                  <p className="text-xs text-foreground/70">
                    Source: Public Library - {copiedSourceTitle}
                    {note.copiedFromPublic && note.copiedFromNoteId ? (
                      <>
                        {" "}
                        <Link
                          href={`/public/notes/${note.copiedFromNoteId}`}
                          className="font-medium text-blue-600 hover:underline dark:text-blue-400"
                        >
                          View Original
                        </Link>
                      </>
                    ) : null}
                  </p>
                ) : null}

                <div className="flex flex-wrap items-center gap-2">
                  <span className={`inline-flex items-center rounded-full border px-2 py-1 text-xs font-medium ${stateChip(isDraft ? "DRAFT" : "STUDY_PACK_READY")}`}>
                    {isDraft ? "Draft" : "✨ Study Pack"}
                  </span>
                  {isInlineMetadataEditMode ? (
                    <span className={`inline-flex items-center rounded-full border px-2 py-1 text-xs font-medium ${visibilityChip(visibility)}`}>
                      {visibility === "PUBLIC" ? "🌍 Public" : "🔒 Private"}
                    </span>
                  ) : (
                    <div className="relative" ref={visibilityMenuRef}>
                      <button
                        type="button"
                        className={`inline-flex items-center rounded-full border px-2 py-1 text-xs font-medium ${visibilityChip(visibility)}`}
                        onClick={() => setVisibilityMenuOpen((open) => !open)}
                        aria-haspopup="menu"
                        aria-expanded={visibilityMenuOpen}
                        disabled={togglingVisibility || !canManageVisibility}
                      >
                        {visibility === "PUBLIC" ? "🌍 Public ▼" : "🔒 Private ▼"}
                      </button>
                      {visibilityMenuOpen ? (
                        <div className="absolute left-0 top-8 z-20 w-64 rounded-md border border-border bg-background p-1 shadow-sm">
                          <button
                            type="button"
                            className="w-full rounded px-3 py-2 text-left hover:bg-muted/60"
                            onClick={() => handleSelectVisibility("PRIVATE")}
                          >
                            <p className="text-sm font-medium">🔒 Private</p>
                            <p className="text-xs text-foreground/70">Only visible in My Library</p>
                          </button>
                          <button
                            type="button"
                            className={`w-full rounded px-3 py-2 text-left hover:bg-muted/60 ${!isEmailVerified ? "cursor-not-allowed opacity-60" : ""}`}
                            onClick={() => handleSelectVisibility("PUBLIC")}
                            disabled={!isEmailVerified}
                          >
                            <p className="text-sm font-medium">🌍 Public</p>
                            <p className="text-xs text-foreground/70">Visible in Public Library</p>
                          </button>
                        </div>
                      ) : null}
                    </div>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-2">
                {isInlineMetadataEditMode ? (
                  <>
                    <Button type="button" variant="outline" size="sm" onClick={handleCancelMetadataEdit} disabled={savingMetadata}>
                      Cancel
                    </Button>
                    <Button type="button" size="sm" onClick={() => void handleSaveMetadata()} disabled={savingMetadata}>
                      {savingMetadata ? "Saving..." : "Save"}
                    </Button>
                  </>
                ) : (
                  <>
                    <Button type="button" variant="outline" size="sm" onClick={handleEdit}>
                      Edit
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="border-red-300 text-red-700 hover:bg-red-50 dark:border-red-900 dark:text-red-400 dark:hover:bg-red-950/40"
                      onClick={() => setShowDeleteConfirm(true)}
                      disabled={deleting}
                    >
                      Delete
                    </Button>
                  </>
                )}
              </div>
            </div>

            {isInlineMetadataEditMode ? (
              <div className="space-y-4">
                <div className="space-y-2">
                  <label htmlFor="note-subject-inline" className="text-xs font-semibold uppercase tracking-wide text-foreground/60">
                    Subject
                  </label>
                  <input
                    id="note-subject-inline"
                    type="text"
                    value={metadataDraft.subject}
                    onChange={(event) => setMetadataDraft((previous) => ({ ...previous, subject: event.target.value }))}
                    className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600"
                    placeholder="No subject"
                  />
                </div>
                <div className="space-y-2">
                  <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Tags</p>
                  <div className="flex flex-col gap-2 sm:flex-row">
                    <input
                      id="note-tags-inline"
                      type="text"
                      value={metadataTagDraft}
                      onChange={(event) => setMetadataTagDraft(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") {
                          event.preventDefault();
                          handleAddMetadataTag();
                        }
                      }}
                      className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus-visible:ring-2 focus-visible:ring-blue-600"
                      placeholder="Add a tag"
                    />
                    <Button type="button" variant="outline" onClick={handleAddMetadataTag}>
                      Add tag
                    </Button>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {metadataDraft.tags.length > 0 ? metadataDraft.tags.map((tag) => (
                      <span key={tag} className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-2 py-1 text-xs text-foreground/75">
                        {tag}
                        <button
                          type="button"
                          className="text-foreground/60 hover:text-foreground"
                          aria-label={`Remove ${tag}`}
                          onClick={() => {
                            setMetadataDraft((previous) => ({
                              ...previous,
                              tags: previous.tags.filter((value) => value !== tag),
                            }));
                          }}
                        >
                          x
                        </button>
                      </span>
                    )) : (
                      <span className="rounded-full border border-dashed border-border px-2 py-1 text-xs text-foreground/55">No tags</span>
                    )}
                  </div>
                </div>
              </div>
            ) : (
              <>
                <p className="text-sm text-foreground/75">{subject}</p>
                <div className="flex flex-wrap gap-2">
                  {tags.length > 0 ? tags.map((tag, index) => (
                    <span key={`${tag}-${index}`} className="rounded-full border border-border bg-background px-2 py-1 text-xs text-foreground/75">
                      {tag}
                    </span>
                  )) : (
                    <span className="rounded-full border border-dashed border-border px-2 py-1 text-xs text-foreground/55">No tags</span>
                  )}
                </div>
              </>
            )}
            {isInlineMetadataEditMode ? (
              <p className="text-xs text-foreground/70">
                Note content cannot be edited after generating a Study Pack. You can still update the title, subject, and tags.
              </p>
            ) : isDraft ? (
              <p className="text-xs text-foreground/70">
                Generating locks this note to preserve its Study Pack. Need changes later? Use Make a Copy.
              </p>
            ) : null}
            {!isInlineMetadataEditMode ? (
              <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                <div className="flex flex-col gap-2 sm:flex-row">
                  {isDraft ? (
                    <Button type="button" onClick={() => void handleGenerate()} disabled={generating || !isEmailVerified}>
                      {generating ? "Generating..." : "Generate Study Pack"}
                    </Button>
                  ) : (
                    <>
                      <Button type="button" onClick={() => void handleStartQuickReview()}>
                        Start Quick Review
                      </Button>
                      <Button type="button" variant="outline" onClick={handleStartChallengeQuiz}>
                        {isPremiumPlan ? "Challenge Quiz" : "Challenge Quiz (Premium)"}
                      </Button>
                      {hasAdaptiveTargets ? (
                        <Button type="button" variant="outline" onClick={handleStartAdaptivePractice}>
                          {isPremiumPlan ? "Adaptive Practice" : "Adaptive Practice (Premium)"}
                        </Button>
                      ) : null}
                    </>
                  )}
                </div>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <Button type="button" variant="outline" onClick={() => void handleMakeCopy()} disabled={copying}>
                    {copying ? "Copying..." : "Make a Copy"}
                  </Button>
                  <Button type="button" variant="outline" onClick={() => void handleCopyLink()} disabled={sharing}>
                    {sharing ? "Sharing..." : "Share"}
                  </Button>
                </div>
              </div>
            ) : null}
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Note Content</h2>
            <p className="whitespace-pre-wrap text-sm leading-relaxed text-foreground/85">
              {note.content.trim().length > 0 ? note.content : "No content yet."}
            </p>
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Summary</h2>
            <p className="text-sm text-foreground/75">
              {isDraft ? "No summary yet. Generate a Study Pack to turn this note into a structured study guide." : (note.summary ?? "No summary available.")}
            </p>
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Key Concepts</h2>
            {isDraft ? (
              <p className="text-sm text-foreground/75">No key concepts yet. Generate a Study Pack to extract the most important ideas from this note.</p>
            ) : (
              <ul className="list-disc space-y-2 pl-5 text-sm leading-relaxed text-foreground/85">
                {note.keyConcepts.map((concept, index) => (
                  <li key={`${note.id}-concept-${index}`}>{concept}</li>
                ))}
              </ul>
            )}
          </Card>

          <Card className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Performance Overview</h2>
            {isDraft ? (
              <p className="text-sm text-foreground/75">Performance will appear after Quick Review or Challenge Quiz.</p>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="rounded-md border border-border bg-background p-3">
                  <p className="text-xs uppercase tracking-wide text-foreground/60">Quick Review</p>
                  <p className="mt-1 text-sm text-foreground/80">Attempts: {quickSummary?.attempts ?? 0}</p>
                  <p className="text-sm text-foreground/80">Last score: {quickSummary?.lastScorePercentage ?? "-"}</p>
                </div>
                <div className="rounded-md border border-border bg-background p-3">
                  <p className="text-xs uppercase tracking-wide text-foreground/60">Challenge Quiz</p>
                  <p className="mt-1 text-sm text-foreground/80">Attempts: {challengeSummary?.attempts ?? 0}</p>
                  <p className="text-sm text-foreground/80">Best score: {challengeSummary?.bestScorePercentage ?? "-"}</p>
                </div>
              </div>
            )}
          </Card>

          {isDraft ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <h2 className="text-lg font-semibold sm:text-xl">Practice Quiz</h2>
              <p className="text-sm text-foreground/75">No quiz yet. Generate a Study Pack to create practice questions from this note.</p>
            </Card>
          ) : (
            <PracticeQuizCard quiz={note.quiz} />
          )}
        </div>
      ) : null}

      {toast ? (
        <div role="status" aria-live="polite" className="fixed bottom-4 right-4 z-50 rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm">
          {toast}
        </div>
      ) : null}

      <AppModal
        isOpen={showMakePublicConfirm}
        title="Make this note public?"
        description="This will make your note visible in the Public Library. Other students will be able to view and copy this note."
        onClose={() => {
          if (!togglingVisibility) {
            setShowMakePublicConfirm(false);
          }
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={() => setShowMakePublicConfirm(false)}
              disabled={togglingVisibility}
            >
              Cancel
            </Button>
            <Button
              type="button"
              onClick={() => {
                setShowMakePublicConfirm(false);
                void performVisibilityUpdate("PUBLIC");
              }}
              disabled={togglingVisibility}
            >
              {togglingVisibility ? "Updating..." : "Make Public"}
            </Button>
          </div>
        )}
      />

      <AppModal
        isOpen={showSharePrivateConfirm}
        title="This note is private"
        description="You need to make this note public before sharing. Anyone with the link will be able to view and copy this note."
        onClose={() => {
          if (!sharing) {
            setShowSharePrivateConfirm(false);
          }
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={() => setShowSharePrivateConfirm(false)}
              disabled={sharing}
            >
              Cancel
            </Button>
            <Button type="button" onClick={() => void handleMakePublicAndShare()} disabled={sharing}>
              {sharing ? "Updating..." : "Make Public & Share"}
            </Button>
          </div>
        )}
      />

      <AppModal
        isOpen={showShareLinkModal}
        title="Share this note"
        onClose={() => {
          setShowShareLinkModal(false);
          setShareModalCopied(false);
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setShowShareLinkModal(false);
                setShareModalCopied(false);
              }}
            >
              Close
            </Button>
            <Button type="button" onClick={() => void handleCopyShareLinkFromModal()}>
              {shareModalCopied ? "Copied" : "Copy Link"}
            </Button>
          </div>
        )}
      >
        <div className="space-y-2">
          <p className="text-xs uppercase tracking-wide text-foreground/60">Shareable URL</p>
          <p className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground/85">
            {truncateShareUrl(shareModalUrl)}
          </p>
          {shareModalCopied ? (
            <p className="text-xs text-emerald-700 dark:text-emerald-300">Link copied</p>
          ) : null}
        </div>
      </AppModal>

      <DeleteConfirmationModal
        isOpen={showDeleteConfirm}
        title="Delete this note?"
        message="This will permanently delete this note and all generated Study Pack content. This action cannot be undone."
        confirmText={deleting ? "Deleting..." : "Delete note"}
        onCancel={() => {
          if (!deleting) {
            setShowDeleteConfirm(false);
          }
        }}
        onConfirm={() => {
          if (!deleting) {
            void handleDeleteNote();
          }
        }}
      />

      <PaywallModal
        isOpen={activePaywallModal !== null}
        variant={activePaywallModal ?? "challenge-quiz"}
        onClose={() => setActivePaywallModal(null)}
        onUpgrade={() => {
          setActivePaywallModal(null);
          router.push(PLAN_BILLING_PATH);
        }}
      />
    </main>
  );
}
