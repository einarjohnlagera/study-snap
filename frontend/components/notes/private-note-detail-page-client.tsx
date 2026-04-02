"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Brain, ChevronDown, FileText } from "lucide-react";
import { NearLimitBanner } from "@/components/billing/near-limit-banner";
import { PaywallModal, type PaywallModalVariant } from "@/components/billing/paywall-modal";
import { StudyPackLimitModal } from "@/components/billing/study-pack-limit-modal";
import { AiSuggestionModal } from "@/components/notes/ai-suggestion-modal";
import { ResponsiveActionButton, ResponsiveActionContent } from "@/components/ui/action-button";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { AppModal } from "@/components/ui/app-modal";
import { DeleteConfirmationModal } from "@/components/notes/delete-confirmation-modal";
import { SubjectCombobox } from "@/components/notes/subject-combobox";
import { SubjectBadge } from "@/components/notes/subject-badge";
import { PracticeQuizCard } from "@/components/study-pack/practice-quiz-card";
import { getAuthUser, setAuthUser } from "@/lib/auth";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import {
  formatStudyPackResetDate,
  isStudyPackLimitReached,
  isStudyPackLimitReachedMessage,
  resolveRemainingUsageCredits,
  shouldShowNearStudyPackLimitBanner,
} from "@/lib/plans";
import { buildPublicLibraryNotePath } from "@/lib/public-note-path";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import {
  completeProductOnboarding,
  copyNote,
  createStudyPackFromNote,
  deleteNote,
  getChallengeQuizPerformanceSummary,
  getMyStudyPack,
  getNote,
  getQuickReviewPerformanceSummary,
  isEmailNotVerifiedError,
  listSubjects,
  trackAnalyticsEvent,
  startQuickReviewSession,
  updateNote,
  updateNoteVisibility,
  type ChallengeQuizPerformanceSummaryResponse,
  type NoteResponse,
  type NoteVisibility,
  type QuickReviewPerformanceSummaryResponse,
} from "@/lib/api";
import {
  clearFirstStudyOnboardingStep,
  getFirstStudyOnboardingStep,
  hasPendingFirstStudyOnboarding,
  setFirstStudyOnboardingStep,
  type FirstStudyOnboardingStep,
} from "@/lib/first-study-onboarding";
import {
  buildNoteDetailPathWithTab,
  resolveGeneratedNoteTab,
  type NoteDetailTab,
} from "@/lib/note-entry";
import { hasExistingNoteMetadata } from "@/lib/note-metadata";
import { cn } from "@/lib/utils";

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

function StudyPackTabs({
  activeTab,
  onChange,
}: Readonly<{
  activeTab: NoteDetailTab;
  onChange: (tab: NoteDetailTab) => void;
}>) {
  const tabs: Array<{
    label: string;
    tab: NoteDetailTab;
    icon: typeof FileText;
  }> = [
    { label: "Summary", tab: "summary", icon: FileText },
    { label: "Quiz", tab: "quiz", icon: Brain },
  ];

  return (
    <div className="border-b border-border" role="tablist" aria-label="Study Pack view">
      <div className="flex items-center gap-5 sm:gap-6">
        {tabs.map((item) => {
          const Icon = item.icon;
          const isActive = activeTab === item.tab;
          return (
            <button
              key={item.tab}
              type="button"
              role="tab"
              aria-label={item.label}
              aria-selected={isActive}
              onClick={() => {
                onChange(item.tab);
              }}
              className={cn(
                "inline-flex min-h-10 items-center justify-center gap-2 border-b-2 border-transparent px-1 pb-3 pt-1 text-sm font-medium transition-colors",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 focus-visible:ring-offset-background",
                isActive
                  ? "border-blue-600 text-foreground dark:border-blue-400"
                  : "text-foreground/55 hover:text-foreground/80",
              )}
            >
              <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
              <span>{item.label}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

function buildShareUrl(subject: string | null, title: string | null) {
  const path = buildPublicLibraryNotePath({ subject, title });
  if (globalThis.window === undefined) {
    return path;
  }
  return new URL(path, globalThis.location.origin).toString();
}

function normalizeMetadataInput(value: string): string | null {
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
}

type PrivateNoteDetailPageClientProps = {
  routeId: string;
};

type PendingSuggestion = {
  noteId: string;
  title: string;
  subject: string | null;
  tags: string[];
};

export function PrivateNoteDetailPageClient({ routeId }: Readonly<PrivateNoteDetailPageClientProps>) {
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
  const [showLimitReachedModal, setShowLimitReachedModal] = useState(false);
  const [firstStudyStep, setFirstStudyStep] = useState<FirstStudyOnboardingStep | null>(null);
  const [showGenerateStudyPackGuide, setShowGenerateStudyPackGuide] = useState(false);
  const [showQuickReviewGuide, setShowQuickReviewGuide] = useState(false);
  const [pendingSuggestion, setPendingSuggestion] = useState<PendingSuggestion | null>(null);
  const [applyingSuggestion, setApplyingSuggestion] = useState(false);

  const [shareModalUrl, setShareModalUrl] = useState("");
  const [shareModalCopied, setShareModalCopied] = useState(false);

  const [isPremiumPlan, setIsPremiumPlan] = useState(false);
  const [isEmailVerified, setIsEmailVerified] = useState(false);
  const [profileType, setProfileType] = useState<"STUDENT" | "BOARD_EXAM" | "TEACHER">("STUDENT");
  const [subjectSuggestions, setSubjectSuggestions] = useState<string[]>([]);

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
  const { usageSummary, refreshUsageSummary } = useBillingUsageSummary();

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
      setProfileType(authUser?.profileType === "BOARD_EXAM" || authUser?.profileType === "TEACHER" ? authUser.profileType : "STUDENT");
      if (authUser && hasPendingFirstStudyOnboarding(authUser)) {
        setFirstStudyStep(getFirstStudyOnboardingStep(authUser.id));
        return;
      }
      setFirstStudyStep(null);
    };
    syncAuthState();
    globalThis.addEventListener("studysnap-auth-change", syncAuthState);
    return () => {
      globalThis.removeEventListener("studysnap-auth-change", syncAuthState);
    };
  }, []);

  useEffect(() => {
    let active = true;

    void listSubjects("mine")
      .then((subjects) => {
        if (active) {
          setSubjectSuggestions(subjects);
        }
      })
      .catch(() => {
        if (active) {
          setSubjectSuggestions([]);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!toast) {
      return;
    }
    const timeout = globalThis.setTimeout(() => setToast(null), 2600);
    return () => globalThis.clearTimeout(timeout);
  }, [toast]);

  useEffect(() => {
    if (!shareModalCopied) {
      return;
    }
    const timeout = globalThis.setTimeout(() => setShareModalCopied(false), 2000);
    return () => globalThis.clearTimeout(timeout);
  }, [shareModalCopied]);

  useEffect(() => {
    if (!note) {
      return;
    }
    if (firstStudyStep === "saved-note" && note.studyPackStatus === "DRAFT") {
      setShowGenerateStudyPackGuide(true);
      setShowQuickReviewGuide(false);
      return;
    }
    if (firstStudyStep === "saved-note" && note.studyPackStatus === "STUDY_PACK_READY") {
      const authUser = getAuthUser();
      if (authUser) {
        setFirstStudyOnboardingStep(authUser.id, "study-pack-ready");
      }
      setFirstStudyStep("study-pack-ready");
      setShowGenerateStudyPackGuide(false);
      setShowQuickReviewGuide(true);
      return;
    }
    if (firstStudyStep === "study-pack-ready" && note.studyPackStatus === "STUDY_PACK_READY") {
      setShowGenerateStudyPackGuide(false);
      setShowQuickReviewGuide(true);
      return;
    }
    setShowGenerateStudyPackGuide(false);
  }, [firstStudyStep, note]);

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
    globalThis.addEventListener("mousedown", handleOutsideClick);
    return () => globalThis.removeEventListener("mousedown", handleOutsideClick);
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
      setToast("Copied to Library");
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
  const tags = note?.tags ?? [];
  const visibility = (note?.visibility ?? "PRIVATE");
  const isPublic = visibility === "PUBLIC";
  const canManageVisibility = isEmailVerified || isPublic;
  const hasAdaptiveTargets = (challengeSummary?.latestWeakConcepts?.length ?? 0) > 0;
  const hasCopyAttribution = Boolean(note?.copiedFromUserId && note?.copiedFromNoteId);
  const copiedSourceTitle = note?.copiedFromTitle?.trim() || "Untitled note";
  const studyPacksRemaining = usageSummary
    ? resolveRemainingUsageCredits(
      usageSummary.usage.studyPacksUsed,
      usageSummary.limits.studyPacksPerMonth,
      usageSummary.remaining?.studyPacksRemaining,
    )
    : null;
  const usageResetDateLabel = formatStudyPackResetDate(usageSummary?.usageCycle?.endsAt);
  const hasReachedStudyPackLimit = isStudyPackLimitReached(studyPacksRemaining);
  const shouldShowNearLimitBanner = usageSummary
    ? shouldShowNearStudyPackLimitBanner(usageSummary.plan, studyPacksRemaining)
    : false;
  const showFirstStudyPackSuccessBanner = firstStudyStep === "study-pack-ready"
    && note?.studyPackStatus === "STUDY_PACK_READY";
  const activeStudyPackTab: NoteDetailTab = searchParams.get("tab") === "quiz" ? "quiz" : "summary";
  const openPaywallModal = useCallback((variant: PaywallModalVariant, source: string) => {
    void trackAnalyticsEvent({
      eventType: "FEATURE_LOCKED_CLICKED",
      metadata: {
        feature: variant === "adaptive-practice" ? "adaptive" : "study_pack_limit",
        source,
        path: pathname,
        noteId: note?.id ?? null,
      },
    });
    setActivePaywallModal(variant);
  }, [note?.id, pathname]);

  const openStudyPackLimitModal = useCallback((source: string) => {
    void trackAnalyticsEvent({
      eventType: "FEATURE_LOCKED_CLICKED",
      metadata: {
        feature: "study_pack_limit",
        source,
        path: pathname,
        noteId: note?.id ?? null,
      },
    });
    setShowLimitReachedModal(true);
  }, [note?.id, pathname]);

  const handleChangeStudyPackTab = useCallback((nextTab: NoteDetailTab) => {
    if (!note || isDraft || activeStudyPackTab === nextTab) {
      return;
    }
    const next = new URLSearchParams(searchParams.toString());
    router.replace(buildNoteDetailPathWithTab(note.id, nextTab, next), { scroll: false });
  }, [activeStudyPackTab, isDraft, note, router, searchParams]);

  const finalizeGeneratedRedirect = useCallback((noteId: string) => {
    const next = new URLSearchParams(searchParams.toString());
    next.set("created", "1");
    const tab = resolveGeneratedNoteTab(profileType, null, null);
    router.replace(buildNoteDetailPathWithTab(noteId, tab, next));
    void loadDetail();
  }, [loadDetail, profileType, router, searchParams]);

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
      openStudyPackLimitModal("private_note_detail_generate");
      return;
    }

    setGenerating(true);
    try {
      const generated = await createStudyPackFromNote(note.id);
      await refreshUsageSummary();
      const authUser = getAuthUser();
      if (authUser && firstStudyStep === "saved-note") {
        setFirstStudyOnboardingStep(authUser.id, "study-pack-ready");
        setFirstStudyStep("study-pack-ready");
      }
      if (!hasExistingNoteMetadata(note)) {
        const updated = await updateNote(note.id, {
          title: generated.title,
          subject: generated.subject ?? null,
          tags: generated.tags ?? [],
          content: note.content,
        });
        setNote(updated);
        finalizeGeneratedRedirect(note.id);
        return;
      }
      setPendingSuggestion({
        noteId: note.id,
        title: generated.title,
        subject: generated.subject ?? null,
        tags: generated.tags ?? [],
      });
    } catch (err) {
      if (isEmailNotVerifiedError(err)) {
        setToast("Email verification is required before generating Study Packs.");
      } else {
        const message = err instanceof Error ? err.message : "Could not generate Study Pack.";
        if (isStudyPackLimitReachedMessage(message)) {
          void refreshUsageSummary();
          openStudyPackLimitModal("private_note_detail_generate_error");
        } else {
          setError(message);
        }
      }
    } finally {
      setGenerating(false);
    }
  };

  const applySuggestions = useCallback(async () => {
    if (!note || !pendingSuggestion || applyingSuggestion) {
      return;
    }
    setApplyingSuggestion(true);
    try {
      const updated = await updateNote(pendingSuggestion.noteId, {
        title: pendingSuggestion.title,
        subject: pendingSuggestion.subject,
        tags: pendingSuggestion.tags,
        content: note.content,
      });
      setNote(updated);
      setPendingSuggestion(null);
      finalizeGeneratedRedirect(pendingSuggestion.noteId);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not apply suggestions.";
      setError(message);
    } finally {
      setApplyingSuggestion(false);
    }
  }, [applyingSuggestion, finalizeGeneratedRedirect, note, pendingSuggestion]);

  const keepMineAndContinue = useCallback(() => {
    if (!pendingSuggestion) {
      return;
    }
    const noteId = pendingSuggestion.noteId;
    setPendingSuggestion(null);
    finalizeGeneratedRedirect(noteId);
  }, [finalizeGeneratedRedirect, pendingSuggestion]);

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

  const dismissFirstStudyGuide = useCallback(async () => {
    const authUser = getAuthUser();
    if (!authUser) {
      return;
    }
    try {
      const me = await completeProductOnboarding(true);
      setAuthUser({
        ...authUser,
        displayName: me.displayName,
        profileType: me.profileType,
        emailVerifiedAt: me.emailVerifiedAt,
        onboardingCompletedAt: me.onboardingCompletedAt,
        productOnboardingCompletedAt: me.productOnboardingCompletedAt,
      });
    } catch {
      // Ignore best-effort onboarding dismissal failures.
    } finally {
      clearFirstStudyOnboardingStep(authUser.id);
      setFirstStudyStep(null);
      setShowGenerateStudyPackGuide(false);
      setShowQuickReviewGuide(false);
    }
  }, []);

  const handleStartChallengeQuiz = () => {
    if (!note) {
      return;
    }
    if (!isEmailVerified) {
      setToast("Verify your email to use this feature.");
      return;
    }
    if (!note.challengeQuizAvailable) {
      setToast("Generate a Study Pack with quiz questions before starting Challenge Quiz.");
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
    if (!note.adaptivePracticeAvailable) {
      openPaywallModal("adaptive-practice", "private_note_detail_adaptive_practice");
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
      setShareModalUrl(buildShareUrl(note.subject, note.title));
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
      setShareModalUrl(buildShareUrl(updated.subject, updated.title));
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
        Back to Library
      </Link>

      {loading ? (
        <Card className="p-6">Loading note...</Card>
      ) : error ? (
        <Card className="space-y-3 p-6">
          <h1 className="text-xl font-semibold">Could not load note</h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <ResponsiveActionButton type="button" onClick={() => void loadDetail()} action="retry" label="Retry" />
        </Card>
      ) : note ? (
        <div className="space-y-6">
          {shouldShowNearLimitBanner ? (
            <NearLimitBanner
              planType={usageSummary?.plan ?? (isPremiumPlan ? "PREMIUM" : "FREE")}
              remainingCredits={studyPacksRemaining}
              resetDateLabel={usageResetDateLabel}
            />
          ) : null}
          {showFirstStudyPackSuccessBanner ? (
            <Card className="space-y-3 border-blue-500/30 bg-blue-500/5 p-4 sm:p-6">
              <div className="space-y-1">
                <h2 className="text-lg font-semibold sm:text-xl">Your Study Pack is ready!</h2>
                <p className="text-sm text-foreground/80">
                  Now try the Challenge Quiz to test yourself.
                </p>
              </div>
              <div className="flex flex-col gap-2 sm:flex-row">
                <ResponsiveActionButton type="button" className="w-full sm:w-auto" onClick={handleStartChallengeQuiz} action="challengeQuiz" label="Start Challenge Quiz" showTextOnMobile />
                <ResponsiveActionButton
                  type="button"
                  variant="outline"
                  className="w-full sm:w-auto"
                  onClick={() => setShowQuickReviewGuide(true)}
                  action="open"
                  label="View Next Steps"
                />
              </div>
            </Card>
          ) : null}
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
                    {isDraft ? "Draft" : "Study Pack"}
                  </span>
                  {isInlineMetadataEditMode ? (
                    <span className={`inline-flex items-center rounded-full border px-2 py-1 text-xs font-medium ${visibilityChip(visibility)}`}>
                      <ResponsiveActionContent action={visibility === "PUBLIC" ? "public" : "private"} label={visibility === "PUBLIC" ? "Public" : "Private"} showTextOnMobile iconClassName="h-3.5 w-3.5" />
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
                        <ResponsiveActionContent action={visibility === "PUBLIC" ? "public" : "private"} label={visibility === "PUBLIC" ? "Public" : "Private"} showTextOnMobile iconClassName="h-3.5 w-3.5" />
                        <ChevronDown className="h-3.5 w-3.5" aria-hidden="true" />
                      </button>
                      {visibilityMenuOpen ? (
                        <div className="absolute left-0 top-8 z-20 w-64 rounded-md border border-border bg-background p-1 shadow-sm">
                          <button
                            type="button"
                            className="w-full rounded px-3 py-2 text-left hover:bg-muted/60"
                            onClick={() => handleSelectVisibility("PRIVATE")}
                          >
                            <p className="text-sm font-medium">
                              <ResponsiveActionContent action="private" label="Private" showTextOnMobile />
                            </p>
                            <p className="text-xs text-foreground/70">Only visible in Library</p>
                          </button>
                          <button
                            type="button"
                            className={`w-full rounded px-3 py-2 text-left hover:bg-muted/60 ${!isEmailVerified ? "cursor-not-allowed opacity-60" : ""}`}
                            onClick={() => handleSelectVisibility("PUBLIC")}
                            disabled={!isEmailVerified}
                          >
                            <p className="text-sm font-medium">
                              <ResponsiveActionContent action="public" label="Public" showTextOnMobile />
                            </p>
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
                    <ResponsiveActionButton type="button" variant="outline" size="sm" onClick={handleCancelMetadataEdit} disabled={savingMetadata} action="back" label="Cancel" showTextOnMobile />
                    <ResponsiveActionButton type="button" size="sm" onClick={() => void handleSaveMetadata()} disabled={savingMetadata} action="save" label={savingMetadata ? "Saving..." : "Save"} />
                  </>
                ) : (
                  <>
                    <ResponsiveActionButton type="button" variant="outline" size="sm" onClick={handleEdit} action="edit" label="Edit" />
                    <ResponsiveActionButton
                      type="button"
                      variant="outline"
                      size="sm"
                      className="border-red-300 text-red-700 hover:bg-red-50 dark:border-red-900 dark:text-red-400 dark:hover:bg-red-950/40"
                      onClick={() => setShowDeleteConfirm(true)}
                      disabled={deleting}
                      action="delete"
                      label="Delete"
                    />
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
                  <SubjectCombobox
                    id="note-subject-inline"
                    value={metadataDraft.subject}
                    suggestions={subjectSuggestions}
                    onChange={(value) => setMetadataDraft((previous) => ({ ...previous, subject: value }))}
                    placeholder="Choose or type a subject"
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
                <div className="flex flex-wrap items-center gap-2 text-sm text-foreground/80">
                  <SubjectBadge subject={note?.subject} />
                  <span className="text-foreground/45">•</span>
                  <span>By You</span>
                </div>
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
                    <ResponsiveActionButton type="button" onClick={() => void handleGenerate()} disabled={generating || !isEmailVerified} action="studyPack" label={generating ? "Generating..." : "Generate Study Pack"} showTextOnMobile />
                  ) : (
                    <>
                      <ResponsiveActionButton type="button" onClick={() => void handleStartQuickReview()} action="quickReview" label="Start Quick Review" showTextOnMobile />
                      <ResponsiveActionButton type="button" variant="outline" onClick={handleStartChallengeQuiz} action="challengeQuiz" label="Challenge Quiz" />
                      {hasAdaptiveTargets ? (
                        <ResponsiveActionButton type="button" variant="outline" onClick={handleStartAdaptivePractice} action="adaptivePractice" label="Adaptive Practice" />
                      ) : null}
                    </>
                  )}
                </div>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <ResponsiveActionButton type="button" variant="outline" onClick={() => void handleMakeCopy()} disabled={copying} action="copy" label={copying ? "Copying..." : "Make a Copy"} />
                  <ResponsiveActionButton type="button" variant="outline" onClick={() => void handleCopyLink()} disabled={sharing} action="share" label={sharing ? "Sharing..." : "Share"} />
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

      {!isDraft ? (
        <Card className="space-y-3 p-4 sm:p-6">
          <StudyPackTabs activeTab={activeStudyPackTab} onChange={handleChangeStudyPackTab} />
          <p className="text-xs text-foreground/60">
            Switch between summary review and quiz-first practice without leaving this note.
          </p>
        </Card>
      ) : null}

      {isDraft || activeStudyPackTab === "summary" ? (
        <>
          <Card id="study-pack-summary" className="space-y-3 p-4 sm:p-6">
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
        </>
      ) : null}

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

      {isDraft || activeStudyPackTab === "quiz" ? (
        isDraft ? (
          <Card id="practice-quiz" className="space-y-3 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Practice Quiz</h2>
            <p className="text-sm text-foreground/75">No quiz yet. Generate a Study Pack to create practice questions from this note.</p>
          </Card>
        ) : (
          <div id="practice-quiz">
            <PracticeQuizCard quiz={note.quiz} />
          </div>
        )
      ) : null}
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

      <AiSuggestionModal
        open={Boolean(pendingSuggestion)}
        title={pendingSuggestion?.title ?? ""}
        subject={pendingSuggestion?.subject ?? null}
        tags={pendingSuggestion?.tags ?? []}
        applying={applyingSuggestion}
        onApply={() => {
          void applySuggestions();
        }}
        onKeepMine={keepMineAndContinue}
      />

      <AppModal
        isOpen={showGenerateStudyPackGuide}
        title="Step 2: Generate your Study Pack"
        description="This will create a summary, key concepts, and quiz from your notes."
        onClose={() => {
          void dismissFirstStudyGuide();
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                void dismissFirstStudyGuide();
              }}
            >
              Skip guide
            </Button>
            <Button
              type="button"
              onClick={() => {
                setShowGenerateStudyPackGuide(false);
                void handleGenerate();
              }}
            >
              Generate Study Pack
            </Button>
          </div>
        )}
      />

      <AppModal
        isOpen={showQuickReviewGuide}
        title="Step 3: Try Quick Review"
        description="Quick Review helps you remember what you just studied."
        onClose={() => {
          void dismissFirstStudyGuide();
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                void dismissFirstStudyGuide();
              }}
            >
              Skip guide
            </Button>
            <Button
              type="button"
              onClick={() => {
                setShowQuickReviewGuide(false);
                void handleStartQuickReview();
              }}
            >
              Start Quick Review
            </Button>
          </div>
        )}
      />

      <PaywallModal
        isOpen={activePaywallModal !== null}
        variant={activePaywallModal ?? "adaptive-practice"}
        source="private_note_detail"
        onClose={() => setActivePaywallModal(null)}
      />

      <StudyPackLimitModal
        isOpen={showLimitReachedModal}
        planType={usageSummary?.plan ?? (isPremiumPlan ? "PREMIUM" : "FREE")}
        resetDateLabel={usageResetDateLabel}
        onClose={() => setShowLimitReachedModal(false)}
      />
    </main>
  );
}
