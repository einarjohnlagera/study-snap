"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import { VerifyEmailRequiredModal } from "@/components/auth/verify-email-required-modal";
import { PaywallModal } from "@/components/billing/paywall-modal";
import { QuizFeedbackPanel } from "@/components/feedback/quiz-feedback-panel";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { QuizAnswerReview } from "@/components/study-pack/quiz-answer-review";
import { QuizGenerationOverlay } from "@/components/study-pack/quiz-generation-overlay";
import { QuizChoiceList } from "@/components/study-pack/quiz-choice-list";
import { QuizQuestionText } from "@/components/study-pack/quiz-question-text";
import { QuizMatchingGroup } from "@/components/study-pack/quiz-matching-group";
import { GoalNudgeCard } from "@/components/study-pack/goal-nudge-card";
import { PostSessionNextStep } from "@/components/study-pack/post-session-next-step";
import { ReviewCommitmentPrompt } from "@/components/study-pack/review-commitment-prompt";
import { WeeklyPacingEchoCard } from "@/components/study-pack/weekly-pacing-echo-card";
import { CompanionResultBridgeCard, hasCompanionResultBridgeExcerpt } from "@/components/study-pack/companion-result-bridge-card";
import { ResultGuidanceGroup } from "@/components/study-pack/result-guidance-group";
import { shouldRenderTwiceMissedCta, TwiceMissedAskCompanionCard } from "@/components/study-pack/twice-missed-ask-companion-card";
import { useQuizSessionGuard } from "@/components/study-pack/quiz-session-guard";
import { hasComputationalWorkingSolution, QuizWorkingSolution } from "@/components/study-pack/quiz-working-solution";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import { getAuthUser } from "@/lib/auth";
import { resolveRemainingUsageCredits } from "@/lib/plans";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import {
  completeAdaptivePracticeSession,
  forfeitAdaptivePracticeSession,
  generateAdaptiveQuickReviewQuiz,
  getAdaptivePracticeSession,
  getCollectionGoal,
  getInProgressAdaptivePracticeSession,
  getMe,
  getMyStudyPack,
  getNote,
  getPostSessionNextStep,
  isEmailNotVerifiedError,
  trackAnalyticsEvent,
  type AdaptiveConceptSelectionReason,
  type AdaptivePracticeFocusConcept,
  type CompanionContent,
  type NoteResponse,
  type AdaptivePracticeCompleteResponse,
  type PostSessionNextStepResponse,
  type QuickReviewAdaptiveQuizResponse,
} from "@/lib/api";
import { getCollectionLabels } from "@/lib/collection-labels";
import { buildConceptAnchorId, normalizeConceptKey } from "@/lib/concepts";
import { isQuizSelectionCorrect, resolveQuizCorrectIndex, resolveQuizItemGroupAt } from "@/lib/quiz";
import { mapPerformanceLevel } from "@/lib/challenge-quiz-results";
import type { AppPlanType } from "@/src/config/plans";
import { renderMathText } from "@/components/study-pack/quiz-working-solution";
import {
  ADAPTIVE_PRACTICE_ENTRY_QUERY_PARAM,
  normalizeAdaptivePracticeEntry,
} from "@/lib/adaptive-practice-entry";

function AdaptivePracticeLoading() {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="h-4 w-40 animate-pulse rounded bg-foreground/10" />
      <div className="h-7 w-3/4 animate-pulse rounded bg-foreground/10" />
      <div className="space-y-2">
        {Array.from({ length: 4 }).map((_, index) => (
          <div key={`adaptive-choice-${index}`} className="h-10 w-full animate-pulse rounded bg-foreground/10" />
        ))}
      </div>
    </Card>
  );
}

function formatSelectionRationale(
  concept: string | null | undefined,
  reason: AdaptiveConceptSelectionReason | null | undefined,
) {
  const normalizedConcept = concept?.trim();
  if (!normalizedConcept || !reason) {
    return null;
  }
  let reasonLabel: string;
  switch (reason) {
    case "DUE":
      reasonLabel = "due for review";
      break;
    case "WEAK":
      reasonLabel = "missed last time";
      break;
    case "BOTH":
      reasonLabel = "missed last time and due for review";
      break;
    default:
      return null;
  }
  return `Reviewing: ${normalizedConcept} — ${reasonLabel}`;
}

/**
 * One source pack's slice of the focus list.
 *
 * ⚠️ KEYED ON THE SOURCE PACK, NEVER ON THE CONCEPT STRING. Two packs weak on "Shear Force" are two
 * distinct focus entries; grouping by name would merge them and thereby assert cross-pack canonical
 * concept identity, which is ADR-sized and explicitly out of scope (v0.107.0).
 */
type AdaptiveFocusGroup = {
  key: string;
  sourceTitle: string | null;
  concepts: string[];
};

/** Groups whose concepts stay expanded before the learner asks for the rest. */
const ADAPTIVE_FOCUS_VISIBLE_GROUPS = 2;
/** Concepts shown per visible group in the compact view. */
const ADAPTIVE_FOCUS_VISIBLE_CONCEPTS = 3;

function buildAdaptiveFocusGroups(entries: AdaptivePracticeFocusConcept[]): AdaptiveFocusGroup[] {
  const groups = new Map<string, AdaptiveFocusGroup>();
  for (const entry of entries) {
    // A legacy plan-scoped session can carry focus entries with no source stamp at all. They share
    // one unattributed bucket rather than becoming one group each -- and they are NOT folded into a
    // real pack's group, which would be a guess about provenance.
    const key = entry.sourceStudyPackId ?? "__unattributed__";
    const existing = groups.get(key);
    if (existing) {
      existing.concepts.push(entry.concept);
      continue;
    }
    groups.set(key, {
      key,
      sourceTitle: entry.sourceStudyPackId ? entry.sourceTitle ?? null : null,
      concepts: [entry.concept],
    });
  }
  return [...groups.values()];
}

function formatAdaptiveFocusSummary(conceptCount: number, sourceCount: number) {
  const concepts = `${conceptCount} weak ${conceptCount === 1 ? "concept" : "concepts"}`;
  if (sourceCount <= 1) {
    return concepts;
  }
  return `${concepts} across ${sourceCount} ${sourceCount === 1 ? "note" : "notes"}`;
}

/**
 * The practice-entry overview: a compact weakness summary, concepts grouped by their source note,
 * and progressive disclosure.
 *
 * ⚠️ THIS IS A PRACTICE-ENTRY SURFACE, NOT A PROGRESS REPORT. It shows enough to explain why this
 * practice exists; it must not make the learner read a diagnostic inventory before they can start.
 */
function AdaptiveFocusOverview({ groups }: { groups: AdaptiveFocusGroup[] }) {
  const [expanded, setExpanded] = useState(false);
  const conceptCount = groups.reduce((total, group) => total + group.concepts.length, 0);
  if (conceptCount === 0) {
    return (
      <p className="text-sm text-foreground/80">
        No specific weak concepts were found. This set still focuses on recent review gaps.
      </p>
    );
  }
  const overflowsGroups = groups.length > ADAPTIVE_FOCUS_VISIBLE_GROUPS;
  const overflowsConcepts = groups.some((group) => group.concepts.length > ADAPTIVE_FOCUS_VISIBLE_CONCEPTS);
  const canExpand = overflowsGroups || overflowsConcepts;
  const visibleGroups = expanded ? groups : groups.slice(0, ADAPTIVE_FOCUS_VISIBLE_GROUPS);
  const hiddenGroupCount = groups.length - visibleGroups.length;
  return (
    <div className="space-y-3">
      <p className="text-sm font-medium text-foreground">
        {formatAdaptiveFocusSummary(conceptCount, groups.length)}
      </p>
      <ul className="space-y-3">
        {visibleGroups.map((group) => {
          const visibleConcepts = expanded
            ? group.concepts
            : group.concepts.slice(0, ADAPTIVE_FOCUS_VISIBLE_CONCEPTS);
          const hiddenConceptCount = group.concepts.length - visibleConcepts.length;
          return (
            <li key={`adaptive-focus-group-${group.key}`} className="space-y-1">
              <p className="text-sm font-medium text-foreground">
                {group.sourceTitle ?? "Other notes"}
                <span className="font-normal text-foreground/60">
                  {" "}
                  — {group.concepts.length} {group.concepts.length === 1 ? "concept" : "concepts"}
                </span>
              </p>
              <p className="text-sm text-foreground/75">
                {visibleConcepts.join(" · ")}
                {hiddenConceptCount > 0 ? ` · +${hiddenConceptCount} more` : ""}
              </p>
            </li>
          );
        })}
      </ul>
      {canExpand ? (
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm text-foreground/60">
            {hiddenGroupCount > 0
              ? `+ ${hiddenGroupCount} more ${hiddenGroupCount === 1 ? "note" : "notes"}`
              : ""}
          </p>
          <button
            type="button"
            onClick={() => setExpanded((current) => !current)}
            className="text-sm font-medium text-blue-600 underline underline-offset-4 dark:text-blue-400"
          >
            {expanded ? "Show less" : "Show all concepts"}
          </button>
        </div>
      ) : null}
    </div>
  );
}

export default function AdaptivePracticePage() {
  const router = useRouter();
  const pathname = usePathname();
  const sessionAddressed = pathname.startsWith("/adaptive-practice/sessions/");
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const requestInFlightRef = useRef(false);
  const [completionResult, setCompletionResult] = useState<AdaptivePracticeCompleteResponse | null>(null);
  const [completionSignalLoaded, setCompletionSignalLoaded] = useState(false);
  const loadedForNoteRef = useRef<string | null>(null);
  const legacyRedirectTargetRef = useRef<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [startingAdaptive, setStartingAdaptive] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [adaptiveQuiz, setAdaptiveQuiz] = useState<QuickReviewAdaptiveQuizResponse | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selectedChoices, setSelectedChoices] = useState<Record<number, number>>({});
  const [selectedMultiChoices, setSelectedMultiChoices] = useState<Record<number, number[]>>({});
  const [completionTracked, setCompletionTracked] = useState(false);
  const [premiumLocked, setPremiumLocked] = useState(false);
  const [quizStarted, setQuizStarted] = useState(false);
  const [sessionStartedAt, setSessionStartedAt] = useState<number | null>(null);
  const [note, setNote] = useState<NoteResponse | null>(null);
  const [showPremiumPaywall, setShowPremiumPaywall] = useState(false);
  const [showVerifyEmailModal, setShowVerifyEmailModal] = useState(false);
  const [showAnswerReview, setShowAnswerReview] = useState(false);
  const [showLimitReachedState, setShowLimitReachedState] = useState(false);
  const [nextStepResponse, setNextStepResponse] = useState<PostSessionNextStepResponse | null>(null);
  const [weeklyPacingWeeksRemaining, setWeeklyPacingWeeksRemaining] = useState<number | null>(null);
  const [primaryCollectionId, setPrimaryCollectionId] = useState<string | null>(null);
  const [primaryCollectionCompanion, setPrimaryCollectionCompanion] = useState<CompanionContent | null>(null);
  const { usageSummary } = useBillingUsageSummary();

  const routeId = useMemo(() => {
    if (!params?.id) {
      return "";
    }
    return Array.isArray(params.id) ? params.id[0] : params.id;
  }, [params]);
  const noteId = sessionAddressed ? adaptiveQuiz?.noteId ?? "" : routeId;
  const noteDetailHref = useMemo(
    () => sessionAddressed ? "/dashboard" : note ? `/notes/${note.id}` : "/library",
    [note, sessionAddressed],
  );
  const backLabel = sessionAddressed ? "Dashboard" : "Note";
  const adaptivePracticeEntry = useMemo(
    () => normalizeAdaptivePracticeEntry(searchParams.get(ADAPTIVE_PRACTICE_ENTRY_QUERY_PARAM)),
    [searchParams],
  );
  const currentPlan = usageSummary?.plan ?? getAuthUser()?.planType ?? "FREE";
  const hasNextStepGuidance = nextStepResponse !== null || weeklyPacingWeeksRemaining !== null;
  const hasCompanionExcerpt = hasCompanionResultBridgeExcerpt(primaryCollectionCompanion);
  const hasTwiceMissedCompanionGuidance = shouldRenderTwiceMissedCta(
    completionResult?.twiceMissedConcepts ?? [],
    currentPlan as AppPlanType,
    primaryCollectionId,
    primaryCollectionCompanion,
  );
  const hasCompanionGuidance = hasCompanionExcerpt || hasTwiceMissedCompanionGuidance;
  const adaptivePracticeRemaining = usageSummary
    ? resolveRemainingUsageCredits(
      usageSummary.usage.adaptivePracticeUsed,
      usageSummary.limits.adaptivePracticePerMonth,
      usageSummary.remaining?.adaptivePracticeRemaining,
    )
    : null;
  const hasAdaptivePracticeQuota = (usageSummary?.limits.adaptivePracticePerMonth ?? 0) > 0;
  const hasReachedAdaptivePracticeLimit = hasAdaptivePracticeQuota
    && adaptivePracticeRemaining !== null
    && adaptivePracticeRemaining <= 0;
  const shouldUpgradeForAdaptivePracticeLimit = currentPlan !== "PRO" && hasReachedAdaptivePracticeLimit;
  const openAdaptivePracticePaywall = useCallback((source: string) => {
    void trackAnalyticsEvent({
      eventType: "FEATURE_LOCKED_CLICKED",
      metadata: {
        feature: "adaptive",
        source,
        path: pathname,
        noteId,
      },
    });
    setShowPremiumPaywall(true);
  }, [noteId, pathname]);

  const applyAdaptiveSession = useCallback((response: QuickReviewAdaptiveQuizResponse) => {
    setAdaptiveQuiz(response);
    setCurrentIndex(0);
    setSelectedChoices({});
    setSelectedMultiChoices({});
    setCompletionTracked(false);
    setShowAnswerReview(false);
    setNextStepResponse(null);
    if (response.status === "IN_PROGRESS" && response.quiz.length > 0) {
      setQuizStarted(true);
      setSessionStartedAt(Date.now());
      setError(null);
      return;
    }
    setQuizStarted(false);
    setSessionStartedAt(null);
    setError(null);
  }, []);

  const loadAdaptiveQuiz = useCallback(async (force = false) => {
    if (requestInFlightRef.current) {
      return;
    }
    if (!routeId) {
      setError(sessionAddressed ? "Adaptive Practice session not found." : "Note not found.");
      setLoading(false);
      return;
    }

    if (!force && loadedForNoteRef.current === routeId) {
      return;
    }

    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    requestInFlightRef.current = true;
    setError(null);
    setPremiumLocked(false);
    setShowLimitReachedState(false);
    const authUser = getAuthUser();
    if (!authUser?.emailVerifiedAt) {
      setAdaptiveQuiz(null);
      setError("Verify your email to use this feature.");
      setShowVerifyEmailModal(true);
      setLoading(false);
      requestInFlightRef.current = false;
      return;
    }

    try {
      if (sessionAddressed) {
        const response = await getAdaptivePracticeSession(routeId);
        loadedForNoteRef.current = routeId;
        if (response.noteId) {
          setNote(await getNote(response.noteId).catch(() => null));
        } else {
          setNote(null);
        }
        applyAdaptiveSession(response);
        return;
      }
      const detail = await getNote(noteId);
      loadedForNoteRef.current = routeId;
      if (detail.studyPackStatus !== "STUDY_PACK_READY") {
        setNote(detail);
        setError("Generate a Study Pack first.");
        setAdaptiveQuiz(null);
        return;
      }
      setNote(detail);
      if (!detail.adaptivePracticeAvailable) {
        setAdaptiveQuiz(null);
        setPremiumLocked(true);
        setShowPremiumPaywall(true);
        return;
      }
      const response = await getInProgressAdaptivePracticeSession(detail.id);
      if (!response.sessionId && hasReachedAdaptivePracticeLimit) {
        setAdaptiveQuiz(null);
        if (shouldUpgradeForAdaptivePracticeLimit) {
          setShowPremiumPaywall(true);
        } else {
          setShowLimitReachedState(true);
        }
        return;
      }
      applyAdaptiveSession(response);
    } catch (err) {
      if (!sessionAddressed && pathname.startsWith("/study-packs/")) {
        const byStudyPack = await getMyStudyPack(noteId).catch(() => null);
        if (byStudyPack?.noteId) {
          const nextQuery = searchParams.toString();
          const targetHref = nextQuery
            ? `/notes/${byStudyPack.noteId}/adaptive-practice?${nextQuery}`
            : `/notes/${byStudyPack.noteId}/adaptive-practice`;
          if (legacyRedirectTargetRef.current !== targetHref) {
            legacyRedirectTargetRef.current = targetHref;
            router.replace(targetHref);
          }
          return;
        }
      }
      loadedForNoteRef.current = null;
      const message = isEmailNotVerifiedError(err)
        ? "Verify your email to use this feature."
        : err instanceof Error
          ? err.message
          : "Could not generate adaptive practice.";
      if (isEmailNotVerifiedError(err)) {
        setShowVerifyEmailModal(true);
      }
      setError(message);
      setAdaptiveQuiz(null);
    } finally {
      setLoading(false);
      requestInFlightRef.current = false;
    }
  }, [applyAdaptiveSession, hasReachedAdaptivePracticeLimit, noteId, pathname, routeId, router, searchParams, sessionAddressed, shouldUpgradeForAdaptivePracticeLimit]);

  useEffect(() => {
    if (!routeId) {
      return;
    }
    if (loadedForNoteRef.current === routeId) {
      return;
    }
    void loadAdaptiveQuiz();
  }, [loadAdaptiveQuiz, routeId]);

  useEffect(() => {
    if (sessionAddressed || !hasReachedAdaptivePracticeLimit || adaptiveQuiz?.sessionId) {
      return;
    }
    if (shouldUpgradeForAdaptivePracticeLimit) {
      setPremiumLocked(false);
      setShowLimitReachedState(false);
      setShowPremiumPaywall(true);
      return;
    }
    setPremiumLocked(false);
    setShowPremiumPaywall(false);
    setShowLimitReachedState(true);
  }, [adaptiveQuiz?.sessionId, hasReachedAdaptivePracticeLimit, sessionAddressed, shouldUpgradeForAdaptivePracticeLimit]);

  const quiz = useMemo(() => adaptiveQuiz?.quiz ?? [], [adaptiveQuiz]);
  const hasQuestions = quiz.length > 0;
  const currentQuestion = hasQuestions ? quiz[currentIndex] : null;
  const currentMatchingGroup = resolveQuizItemGroupAt(quiz, currentIndex);
  const currentRationales = (() => {
    const questionIndexes = currentMatchingGroup
      ? currentMatchingGroup.items.map((_, offset) => currentMatchingGroup.startIndex + offset)
      : [currentIndex];
    const uniqueRationales = new Set<string>();
    questionIndexes.forEach((questionIndex) => {
      const question = quiz[questionIndex];
      // The reason now travels WITH its focus concept rather than in a quiz-parallel array, so
      // resolve it by matching the question's concept AND its source pack. Matching the pack too
      // matters once a plan has two packs weak on the same concept string -- they are two distinct
      // focus entries, and concept-only matching would silently pick the first.
      const focus = adaptiveQuiz?.focusConcepts?.find(
        (entry) =>
          normalizeConceptKey(entry.concept) === normalizeConceptKey(question?.concept ?? "") &&
          (!question?.sourceStudyPackId || entry.sourceStudyPackId === question.sourceStudyPackId),
      );
      const rationale = formatSelectionRationale(question?.concept, focus?.selectionReason ?? null);
      if (rationale) {
        uniqueRationales.add(rationale);
      }
    });
    return Array.from(uniqueRationales);
  })();
  const selectedChoiceIndex = selectedChoices[currentIndex] ?? null;
  const selectedMultiChoiceIndices = selectedMultiChoices[currentIndex] ?? [];
  const currentQuestionIsMultiSelect = currentQuestion?.questionFormat === "MULTI_SELECT";
  const hasAnsweredCurrent = currentMatchingGroup
    ? currentMatchingGroup.items.every((_, offset) => selectedChoices[currentMatchingGroup.startIndex + offset] != null)
    : currentQuestionIsMultiSelect ? selectedMultiChoiceIndices.length > 0 : selectedChoiceIndex !== null;
  const isComplete = hasQuestions && currentIndex >= quiz.length;
  const score = useMemo(() => {
    return quiz.reduce((count, question, index) => {
      const selected = question.questionFormat === "MULTI_SELECT" ? selectedMultiChoices[index] : selectedChoices[index];
      return isQuizSelectionCorrect(question, selected) ? count + 1 : count;
    }, 0);
  }, [quiz, selectedChoices, selectedMultiChoices]);
  const scorePercentage = useMemo(() => {
    if (quiz.length === 0) {
      return 0;
    }
    return Number(((score / quiz.length) * 100).toFixed(0));
  }, [quiz.length, score]);
  const completionMessage = useMemo(() => {
    const level = mapPerformanceLevel(scorePercentage);
    if (level === "Excellent") return "Outstanding. You've mastered these weak areas.";
    if (level === "Good") return "Great work. You're making strong progress on these concepts.";
    if (level === "Fair") return "Good effort. Keep reviewing these concepts to build confidence.";
    return "Keep going. These concepts need more practice — try again when ready.";
  }, [scorePercentage]);

  useEffect(() => {
    if (!isComplete) {
      return;
    }
    void getMe().then((me) => {
      if (me.primaryCollectionId) {
        setPrimaryCollectionId(me.primaryCollectionId);
        setPrimaryCollectionCompanion(null);
        void getCollectionGoal(me.primaryCollectionId)
          .then((goal) => {
            setWeeklyPacingWeeksRemaining(goal.weeksRemaining);
            setPrimaryCollectionCompanion(goal.companion);
          })
          .catch(() => {
            setPrimaryCollectionId(null);
            setPrimaryCollectionCompanion(null);
          });
      } else {
        setPrimaryCollectionId(null);
        setPrimaryCollectionCompanion(null);
      }
    }).catch(() => {
      setPrimaryCollectionId(null);
      setPrimaryCollectionCompanion(null);
    });
  }, [isComplete]);

  useEffect(() => {
    if (adaptiveQuiz?.status !== "GENERATING" || (!sessionAddressed && !note)) {
      return;
    }

    let isMounted = true;
    const pollGenerationStatus = async () => {
      try {
        const response = sessionAddressed
          ? await getAdaptivePracticeSession(adaptiveQuiz.sessionId ?? routeId)
          : await getInProgressAdaptivePracticeSession(note!.id);
        if (!isMounted) {
          return;
        }
        applyAdaptiveSession(response);
      } catch (err) {
        if (isMounted) {
          const message = err instanceof Error ? err.message : "Could not load Adaptive Practice generation status.";
          setError(message);
          setAdaptiveQuiz(null);
        }
      }
    };

    void pollGenerationStatus();
    const intervalId = globalThis.setInterval(() => {
      void pollGenerationStatus();
    }, 2000);

    return () => {
      isMounted = false;
      globalThis.clearInterval(intervalId);
    };
  }, [adaptiveQuiz?.sessionId, adaptiveQuiz?.status, applyAdaptiveSession, note, routeId, sessionAddressed]);

  const handleStartAdaptivePractice = useCallback(async () => {
    if (!note || requestInFlightRef.current) {
      if (sessionAddressed) {
        setError("Start a new plan-scoped practice session from its plan.");
      }
      return;
    }

    const authUser = getAuthUser();
    if (!authUser?.emailVerifiedAt) {
      setError("Verify your email to use this feature.");
      setShowVerifyEmailModal(true);
      return;
    }
    if (!note.adaptivePracticeAvailable) {
      setPremiumLocked(true);
      openAdaptivePracticePaywall("adaptive_practice_start");
      return;
    }
    if (hasReachedAdaptivePracticeLimit) {
      if (shouldUpgradeForAdaptivePracticeLimit) {
        openAdaptivePracticePaywall("adaptive_practice_limit_reached");
        return;
      }
      setShowLimitReachedState(true);
      return;
    }

    requestInFlightRef.current = true;
    setStartingAdaptive(true);
    setError(null);
    try {
      const response = await generateAdaptiveQuickReviewQuiz(note.id, adaptivePracticeEntry);
      applyAdaptiveSession(response);
    } catch (err) {
      const message = isEmailNotVerifiedError(err)
        ? "Verify your email to use this feature."
        : err instanceof Error
          ? err.message
          : "Could not generate adaptive practice.";
      if (isEmailNotVerifiedError(err)) {
        setShowVerifyEmailModal(true);
      }
      if (message.toLowerCase().includes("monthly adaptive practice limit")) {
        if (currentPlan !== "PRO") {
          openAdaptivePracticePaywall("adaptive_practice_limit_reached");
        } else {
          setShowLimitReachedState(true);
        }
      } else {
        setError(message);
      }
    } finally {
      requestInFlightRef.current = false;
      setStartingAdaptive(false);
    }
  }, [adaptivePracticeEntry, applyAdaptiveSession, currentPlan, hasReachedAdaptivePracticeLimit, note, openAdaptivePracticePaywall, sessionAddressed, shouldUpgradeForAdaptivePracticeLimit]);

  const adaptiveGenerationLocked = startingAdaptive || adaptiveQuiz?.status === "GENERATING";
  // Display names for the focus list. Duplicates are preserved DELIBERATELY: two packs weak on the
  // same concept are two distinct focus entries, and de-duplicating here would re-introduce, in the
  // UI, exactly the cross-pack merge the response shape exists to prevent.
  const adaptiveFocusEntries = useMemo(() => adaptiveQuiz?.focusConcepts ?? [], [adaptiveQuiz]);
  const adaptiveFocusConceptNames = useMemo(
    () => adaptiveFocusEntries.map((entry) => entry.concept),
    [adaptiveFocusEntries],
  );
  /**
   * True when the same concept string appears for more than one pack.
   *
   * Duplicates are preserved deliberately -- two packs weak on one concept are two distinct focus
   * entries -- so the SOURCE has to be shown, or the list reads as a rendering bug. This is the
   * disambiguation the "never merge concepts across packs" decision rests on.
   */
  const adaptiveFocusHasDuplicateNames = useMemo(
    () => new Set(adaptiveFocusConceptNames).size !== adaptiveFocusConceptNames.length,
    [adaptiveFocusConceptNames],
  );
  /**
   * The focus list partitioned by SOURCE PACK, which is what makes a plan-scoped session legible:
   * "14 weak concepts across 4 notes" instead of one undifferentiated run-on line.
   */
  const adaptiveFocusGroups = useMemo(
    () => buildAdaptiveFocusGroups(adaptiveFocusEntries),
    [adaptiveFocusEntries],
  );

  const adaptiveQuizActive = Boolean(
    adaptiveQuiz?.sessionId
    && adaptiveQuiz.status === "IN_PROGRESS"
    && quizStarted
    && hasQuestions
    && !isComplete
    && !completionTracked
    && !error,
  );
  const { requestLeave, LeaveQuizModal } = useQuizSessionGuard({
    active: adaptiveQuizActive,
    fallbackHref: noteDetailHref,
    onConfirmLeave: async () => {
      if (!adaptiveQuiz?.sessionId) {
        return;
      }
      await forfeitAdaptivePracticeSession(adaptiveQuiz.sessionId);
    },
  });
  const { LeaveQuizModal: GenerationLockModal } = useQuizSessionGuard({
    active: adaptiveGenerationLocked,
    fallbackHref: noteDetailHref,
    onConfirmLeave: () => undefined,
    blockWithoutConfirmation: true,
  });

  const handleSelectChoice = (choiceIndex: number) => {
    if (!currentQuestion || hasAnsweredCurrent) {
      return;
    }
    setSelectedChoices((prev) => ({
      ...prev,
      [currentIndex]: choiceIndex,
    }));
  };

  const handleSelectMatchingChoice = (questionIndex: number, choiceIndex: number) => {
    if (!currentMatchingGroup || hasAnsweredCurrent) {
      return;
    }
    setSelectedChoices((prev) => ({
      ...prev,
      [questionIndex]: choiceIndex,
    }));
  };

  const handleSelectMultiChoices = (choiceIndices: number[]) => {
    if (!currentQuestion || !currentQuestionIsMultiSelect) {
      return;
    }
    setSelectedMultiChoices((prev) => ({
      ...prev,
      [currentIndex]: choiceIndices,
    }));
  };

  const handleNext = () => {
    if (!hasAnsweredCurrent) {
      return;
    }
    const nextIndex = currentMatchingGroup ? currentMatchingGroup.endIndex + 1 : currentIndex + 1;
    if (nextIndex >= quiz.length && !completionTracked && adaptiveQuiz?.sessionId) {
      setCompletionTracked(true);
      void trackAnalyticsEvent({
        eventType: "ADAPTIVE_PRACTICE_COMPLETED",
        entityId: adaptiveQuiz?.sessionId,
        metadata: {
          scorePercentage: quiz.length > 0 ? Math.round((score / quiz.length) * 100) : 0,
          totalQuestions: quiz.length,
        },
      });
      const durationSeconds = sessionStartedAt
        ? Math.max(0, Math.round((Date.now() - sessionStartedAt) / 1000))
        : undefined;
      if (adaptiveQuiz?.sessionId) {
        const correctConceptNames = quiz
          .filter((question, index) => {
            const selected = question.questionFormat === "MULTI_SELECT"
              ? selectedMultiChoices[index]
              : selectedChoices[index];
            return isQuizSelectionCorrect(question, selected);
          })
          .map((question) => question.concept)
          .filter((concept): concept is string => Boolean(concept?.trim()));
        const completeRequest: Parameters<typeof completeAdaptivePracticeSession>[1] = {
          correctAnswers: score,
          totalQuestions: quiz.length,
          durationSeconds,
          // Sent so the server can bucket ConceptHealth by (sourceStudyPackId, concept). Without
          // them its breakdown is empty, and a plan-scoped session attributes every concept to the
          // anchor pack and records no misses -- the exact over-attribution shape item 1 removed.
          selectedChoices,
          selectedMultiChoices,
        };
        if (correctConceptNames.length > 0) {
          completeRequest.correctConceptNames = correctConceptNames;
        }
        setNextStepResponse(null);
        void completeAdaptivePracticeSession(adaptiveQuiz.sessionId, completeRequest)
          .then((completed) => {
            setCompletionResult(completed);
            setCompletionSignalLoaded(true);
            // Collection-anchored sessions have no single Study Pack, so their shared next-step
            // lookup is intentionally omitted rather than inventing a mutable source-pack anchor.
            return adaptiveQuiz.studyPackId
              ? getPostSessionNextStep(adaptiveQuiz.studyPackId)
              : Promise.resolve(null);
          })
          .then(setNextStepResponse)
          .catch(() => {
            // Completion and next-step persistence should not block adaptive practice flow.
            setCompletionSignalLoaded(true);
            setNextStepResponse(null);
          });
      } else {
        // No session id to complete against (e.g. a response applied without one) — there is no
        // first-quiz-ever signal to wait for, so fall back to the generic feedback panel immediately
        // instead of leaving it permanently unrendered.
        setCompletionSignalLoaded(true);
      }
    }
    setCurrentIndex(nextIndex);
  };

  return (
    <main className="mx-auto w-full max-w-3xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <div className="flex items-center justify-between gap-3">
        {adaptiveQuizActive ? (
          <>
            <p className="text-sm font-medium text-foreground/80">Adaptive Practice in progress</p>
            <Button type="button" variant="outline" size="sm" onClick={() => requestLeave()}>
              Leave Quiz
            </Button>
          </>
        ) : (
          <BackLink href={noteDetailHref} label={backLabel} />
        )}
      </div>

      {adaptiveGenerationLocked ? (
        <QuizGenerationOverlay />
      ) : null}

      {loading ? (
        <AdaptivePracticeLoading />
      ) : showLimitReachedState ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Adaptive Practice
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">You’ve reached your quiz limit for this month</h1>
          <p className="text-sm text-foreground/75">Your Adaptive Practice limit resets on your next billing cycle.</p>
          <BackLink href={noteDetailHref} label={backLabel} />
        </Card>
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">Could not generate adaptive practice</h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" className="w-full sm:w-auto" onClick={() => void loadAdaptiveQuiz(true)}>
              Try Again
            </Button>
          </div>
        </Card>
      ) : premiumLocked ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Adaptive Practice
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">Adaptive Practice unavailable</h1>
          <p className="text-sm text-foreground/75">
            Adaptive Practice is not available on your current plan.
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="button" className="w-full sm:w-auto" onClick={() => openAdaptivePracticePaywall("adaptive_practice_page_card")}>
              See upgrade options
            </Button>
          </div>
        </Card>
      ) : adaptiveQuiz?.status === "FAILED" ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Adaptive Practice
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">Could not generate adaptive practice</h1>
          <p className="text-sm text-foreground/75">
            {adaptiveQuiz.message}
          </p>
          {sessionAddressed ? (
            <BackLink href={noteDetailHref} label={backLabel} />
          ) : (
            <Button
              type="button"
              className="w-full sm:w-auto"
              onClick={() => void handleStartAdaptivePractice()}
              disabled={adaptiveGenerationLocked}
            >
              {adaptiveGenerationLocked ? "Starting..." : "Try Again"}
            </Button>
          )}
        </Card>
      ) : !adaptiveQuiz || (!hasQuestions && adaptiveFocusConceptNames.length === 0) ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Adaptive Practice
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">Practice Weak Areas</h1>
          <p className="text-sm text-foreground/75">
            {adaptiveQuiz?.message ?? "Adaptive practice is unavailable right now."}
          </p>
          <Link href={note ? `/notes/${note.id}/quick-review` : "/dashboard"} className="w-full sm:w-auto">
            <Button type="button" className="w-full sm:w-auto">
              Start Quick Review
            </Button>
          </Link>
        </Card>
      ) : !hasQuestions || !quizStarted ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Adaptive Practice
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">{adaptiveQuiz.title}</h1>
          <div className="rounded-md border border-blue-500/30 bg-blue-500/10 p-3 text-sm text-foreground/85">
            <p className="font-medium text-foreground">
              Focusing on concepts you need to improve.
            </p>
          </div>
          <div className="rounded-md border border-border bg-background p-3 text-sm text-foreground/80">
            <AdaptiveFocusOverview groups={adaptiveFocusGroups} />
          </div>
          <Button
            type="button"
            className="w-full sm:w-auto"
            onClick={() => void handleStartAdaptivePractice()}
            disabled={adaptiveGenerationLocked}
          >
            {adaptiveGenerationLocked ? "Starting..." : "Start Adaptive Practice"}
          </Button>
        </Card>
      ) : isComplete ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Adaptive Practice Complete
          </p>
          <h1 className="text-xl font-semibold sm:text-2xl">{adaptiveQuiz.title}</h1>
          <p className="text-sm text-foreground/75">
            Score: {score} / {quiz.length} ({scorePercentage}%)
          </p>
          <div className="rounded-md border border-border bg-background p-3 text-sm text-foreground/85">
            {completionMessage}
          </div>
          {adaptiveFocusConceptNames.length > 0 ? (
            <div className="rounded-md border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-foreground/80">
              <p className="text-xs font-semibold uppercase tracking-wide text-amber-700 dark:text-amber-300">
                Targeted Weak Areas
              </p>
              <ul className="mt-2 list-disc space-y-1 pl-5">
                {adaptiveFocusEntries.map((entry) => {
                  const concept = entry.concept;
                  return (
                  <li key={`${entry.sourceStudyPackId}-${concept}`}>
                    {note?.id && note.keyConcepts?.some((keyConcept) => (
                      normalizeConceptKey(keyConcept) === normalizeConceptKey(concept)
                    )) ? (
                      <Link
                        href={`/notes/${note.id}?tab=key-concepts#${buildConceptAnchorId(concept)}`}
                        className="font-medium text-amber-700 underline underline-offset-4 dark:text-amber-300"
                      >
                        {concept}
                      </Link>
                    ) : concept}
                    {adaptiveFocusHasDuplicateNames && entry.sourceTitle ? (
                      <span className="text-foreground/60"> · {entry.sourceTitle}</span>
                    ) : null}
                  </li>
                  );
                })}
              </ul>
            </div>
          ) : (
            <div className="rounded-md border border-border bg-background p-3 text-sm text-foreground/75">
              <p className="font-medium text-foreground">Targeted Weak Areas</p>
              <p className="mt-1">
                No targeted weak areas were attached to this set. Generate a new set or review your answers to keep practicing.
              </p>
            </div>
          )}
          <ReviewCommitmentPrompt
            isFirstCompletedSessionEver={completionResult?.isFirstCompletedSessionEver}
            noteId={note?.id ?? null}
          />
          {hasNextStepGuidance ? (
            <ResultGuidanceGroup label="What to do next" testId="adaptive-next-step-guidance">
              <PostSessionNextStep
                response={nextStepResponse}
                currentPlan={currentPlan}
                noteId={note?.id ?? null}
                onOpenPaywall={() => openAdaptivePracticePaywall("adaptive_practice_results_next_step")}
                originatingQuizMode="ADAPTIVE"
                contained
              />
              {nextStepResponse?.goalNudge ? (
                <GoalNudgeCard goalNudge={nextStepResponse.goalNudge} noteId={note?.id ?? null} contained />
              ) : null}
              <WeeklyPacingEchoCard
                weeksRemaining={weeklyPacingWeeksRemaining}
                goalLabel={getCollectionLabels(getAuthUser()?.profileType ?? null).goalSingular}
                contained
              />
            </ResultGuidanceGroup>
          ) : null}
          {hasCompanionGuidance ? (
            <ResultGuidanceGroup label="Companion guidance" testId="adaptive-companion-guidance">
              <CompanionResultBridgeCard
                companion={primaryCollectionCompanion}
                reviewSetLabel={getCollectionLabels(getAuthUser()?.profileType ?? null).singular}
                contained
              />
              <TwiceMissedAskCompanionCard
                twiceMissedConcepts={completionResult?.twiceMissedConcepts ?? []}
                currentPlan={currentPlan as AppPlanType}
                primaryCollectionId={primaryCollectionId}
                companion={primaryCollectionCompanion}
                contained
              />
            </ResultGuidanceGroup>
          ) : null}
          <div className="flex flex-col gap-2 sm:flex-row">
            {nextStepResponse === null && !sessionAddressed ? (
              <Button
                type="button"
                className="w-full sm:w-auto"
                onClick={() => void handleStartAdaptivePractice()}
                disabled={adaptiveGenerationLocked}
              >
                {adaptiveGenerationLocked ? "Starting..." : "Generate New Set"}
              </Button>
            ) : null}
            <Button
              type="button"
              variant="outline"
              className="w-full sm:w-auto"
              onClick={() => setShowAnswerReview((previous) => !previous)}
            >
              {showAnswerReview ? "Hide Answer Review" : "Review Answers"}
            </Button>
          </div>
          <div className="pt-1">
            <BackLink href={noteDetailHref} label={backLabel} />
          </div>
          {showAnswerReview ? (
            <QuizAnswerReview
              quiz={quiz}
              selectedChoices={selectedChoices}
              selectedMultiChoices={selectedMultiChoices}
              className="mt-2"
              planType={currentPlan}
            />
          ) : null}
          {completionSignalLoaded ? (
            <QuizFeedbackPanel
              quizLabel="Adaptive Practice"
              noteTitle={note?.title}
              section={showAnswerReview ? "review" : "results"}
              isFirstCompletedSessionEver={completionResult?.isFirstCompletedSessionEver}
              isSecondCompletedSessionEver={completionResult?.isSecondCompletedSessionEver}
              userId={getAuthUser()?.id}
            />
          ) : null}
        </Card>
      ) : (
        <div className="space-y-4">
          <Card className="space-y-2 p-4 sm:p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Adaptive Practice
            </p>
            <h1 className="text-xl font-semibold sm:text-2xl">{adaptiveQuiz.title}</h1>
            <p className="text-sm text-foreground/75">
              New follow-up practice based on your weak areas.
            </p>
            {adaptiveFocusConceptNames.length > 0 ? (
              // ⚠️ A single comma-joined line across several source packs MERGES them: two packs weak
              // on one concept read as a duplicate, and the source that disambiguates them is
              // discarded. Multi-source sessions therefore report per-source counts; a single-source
              // session can still list names, because nothing can be merged across one pack.
              <p className="text-sm text-foreground/75">
                {adaptiveFocusGroups.length > 1
                  ? `Focus: ${adaptiveFocusGroups
                      .map((group) => `${group.sourceTitle ?? "Other notes"} (${group.concepts.length})`)
                      .join(" · ")}`
                  : `Focus concepts: ${adaptiveFocusConceptNames.join(", ")}`}
              </p>
            ) : null}
            <p className="text-sm text-foreground/75">
              Question {currentIndex + 1} of {quiz.length}
            </p>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            {currentRationales.length > 0 ? (
              <div className="flex flex-wrap gap-2" aria-label="Why these questions">
                {currentRationales.map((rationale) => (
                  <p
                    key={rationale}
                    className="w-fit max-w-full rounded-full border border-blue-200 bg-blue-50 px-2.5 py-1 text-xs font-medium leading-relaxed text-blue-700 dark:border-blue-800 dark:bg-blue-950/40 dark:text-blue-300"
                  >
                    {rationale}
                  </p>
                ))}
              </div>
            ) : null}
            {currentMatchingGroup ? (
              <QuizMatchingGroup
                items={currentMatchingGroup.items}
                groupStartIndex={currentMatchingGroup.startIndex}
                selectedChoices={selectedChoices}
                revealAnswer={hasAnsweredCurrent}
                onSelectChoice={handleSelectMatchingChoice}
              />
            ) : currentQuestion ? (
              <>
                <h2 className="text-lg font-semibold">
                  {currentIndex + 1}. <QuizQuestionText text={currentQuestion.question} />
                </h2>
                <QuizChoiceList
                  questionKey={currentQuestion.question}
                  choices={currentQuestion.choices}
                  correctIndex={resolveQuizCorrectIndex(currentQuestion)}
                  correctIndices={currentQuestion.correctIndices}
                  questionFormat={currentQuestion.questionFormat}
                  selectedChoiceIndex={selectedChoiceIndex}
                  selectedMultiChoiceIndices={selectedMultiChoiceIndices}
                  revealAnswer={hasAnsweredCurrent && !currentQuestionIsMultiSelect}
                  onSelectChoice={handleSelectChoice}
                  onSelectMultiChoices={handleSelectMultiChoices}
                />
              </>
            ) : null}

            {hasAnsweredCurrent && currentQuestion && !currentQuestionIsMultiSelect && !currentMatchingGroup ? (
              <div className="space-y-3 rounded-md border border-border bg-background p-3 text-sm text-foreground/80">
                <p>
                  <span className="font-medium text-foreground">Explanation:</span>{" "}
                  {renderMathText(currentQuestion.explanation)}
                </p>
                {hasComputationalWorkingSolution(currentQuestion) ? (
                  <QuizWorkingSolution
                    workingSolution={currentQuestion.workingSolution}
                    planType={currentPlan}
                  />
                ) : null}
              </div>
            ) : null}

            <div className="flex justify-stretch sm:justify-end">
              <Button type="button" className="w-full sm:w-auto" onClick={handleNext} disabled={!hasAnsweredCurrent}>
                {currentIndex + 1 >= quiz.length ? "Finish Adaptive Practice" : "Next Question"}
              </Button>
            </div>
          </Card>
        </div>
      )}

      <PaywallModal
        isOpen={showPremiumPaywall}
        variant="adaptive-practice"
        source="adaptive_practice_page"
        onClose={() => setShowPremiumPaywall(false)}
      />
      <VerifyEmailRequiredModal
        isOpen={showVerifyEmailModal}
        onClose={() => setShowVerifyEmailModal(false)}
      />
      <LeaveQuizModal />
      <GenerationLockModal />
    </main>
  );
}
