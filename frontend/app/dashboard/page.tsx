"use client";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { NearLimitBanner } from "@/components/billing/near-limit-banner";
import { PaywallModal, type PaywallModalVariant } from "@/components/billing/paywall-modal";
import { WelcomeBackFeedbackPrompt } from "@/components/feedback/welcome-back-feedback-prompt";
import { Card } from "@/components/ui/card";
import { ResponsiveActionButton, ResponsiveActionLink } from "@/components/ui/action-button";
import { useBillingUsageSummary } from "@/hooks/use-billing-usage-summary";
import {
  formatStudyPackResetDate,
  resolveRemainingUsageCredits,
  shouldShowNearStudyPackLimitBanner,
} from "@/lib/plans";
import {
  completeProductOnboarding,
  getContinueStudyingRecommendation,
  getDashboardOverview,
  getFeedbackPromptContext,
  getCollectionGoal,
  getGoalSummary,
  getMe,
  getQuickReviewLastReviewedBatch,
  getTodayFocus,
  listNotes,
  type ContinueStudyingResponse,
  type DashboardOverviewResponse,
  type GoalCollectionDetailResponse,
  type GoalNudgeResponse,
  type FeedbackPromptContextResponse,
  type MeResponse,
  type NoteListItemResponse,
  type ProfileType,
  type TodayFocusResponse,
} from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { ContinueSpotlight } from "./continue-spotlight";
import { DashboardPersonalizationPrompt } from "./dashboard-personalization-prompt";
import { LightweightProfileCompletionPrompt } from "@/components/dashboard/lightweight-profile-completion-prompt";
import { DashboardHero } from "./dashboard-hero";
import { DashboardMonthlyUsageCard } from "./dashboard-monthly-usage-card";
import { DashboardFocusAreasCard } from "./dashboard-focus-areas-card";
import { TodayFocusCard } from "./today-focus-card";
import { DashboardWeeklyActivityCard } from "./dashboard-weekly-activity-card";
import { StudyPackGrid } from "./study-pack-grid";
import { DashboardLoading } from "./dashboard-loading";
import { DashboardEmpty } from "./dashboard-empty";
import { DashboardError } from "./dashboard-error";
import { FreePlanUpgradeCard } from "./free-plan-upgrade-card";
import { DashboardActionCard } from "./dashboard-action-card";
import { DashboardStrongestNotes } from "./dashboard-strongest-notes";
import { DashboardCommunityNotesSection } from "./dashboard-community-notes-section";
import { DashboardStudyPlanSection } from "./dashboard-study-plan-section";
import { DashboardPrimaryCollectionHero, DashboardPrimaryCollectionHeroSkeleton } from "./dashboard-primary-collection-hero";
import { ProfessionalInterviewPracticeCard } from "@/components/dashboard/professional-interview-practice-card";
import { DashboardGoalCard } from "@/components/dashboard/dashboard-goal-card";
import { GoalPromptBanner } from "@/components/dashboard/goal-prompt-banner";
import { AppModal } from "@/components/ui/app-modal";
import {
  clearFirstStudyOnboardingStep,
  isFirstStudyOnboardingEligible,
  setFirstStudyOnboardingStep,
} from "@/lib/first-study-onboarding";
import {
  dismissDashboardPersonalizationPrompt,
  hasDismissedDashboardPersonalizationPrompt,
} from "@/lib/dashboard-personalization-prompt";
import { PROFILE_LEARNING_PROFILE_SECTION_ID } from "@/lib/profile-sections";
import { GuidanceTip } from "@/components/ui/guidance-tip";
import { pickActiveGuidance, type GuidanceRule } from "@/lib/guidance-engine";
import {
  clearPendingLightweightProfileCompletion,
  hasPendingLightweightProfileCompletion,
} from "@/lib/onboarding-v2";
import {
  dismissLightweightProfileCompletionPrompt,
  hasDismissedLightweightProfileCompletionPrompt,
} from "@/lib/lightweight-profile-completion-prompt";

type SupportedDashboardProfileType = "STUDENT" | "BOARD_EXAM" | "TEACHER" | "PROFESSIONAL";
type TeacherGeneratedQuizSummary = {
  noteId: string;
  title: string;
  subject: string | null;
  generatedAt: string;
  updatedAt: string;
  questionCount: number;
};

const MAX_TEACHER_QUIZ_NOTES = 8;
const DASHBOARD_NOTE_FETCH_LIMIT = 20;

function formatDashboardDate(value: string): string {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "Unavailable";
  }
  return parsed.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function resolveDashboardProfileType(profileType: ProfileType | null | undefined): SupportedDashboardProfileType {
  if (profileType === "BOARD_EXAM" || profileType === "TEACHER" || profileType === "PROFESSIONAL") {
    return profileType;
  }
  return "STUDENT";
}

function formatExamCountdown(examDate: string | null): string | null {
  if (!examDate) {
    return null;
  }
  const exam = new Date(`${examDate}T00:00:00`);
  if (Number.isNaN(exam.getTime())) {
    return null;
  }
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diffMs = exam.getTime() - today.getTime();
  const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
  if (diffDays < 0) {
    return "Your exam date has passed. Keep practicing to stay sharp.";
  }
  if (diffDays === 0) {
    return "Your exam is today. Focus on a final round of review.";
  }
  if (diffDays === 1) {
    return "You have 1 day until your exam.";
  }
  return `You have ${diffDays} days until your exam.`;
}

function resolveStudentPrimaryHref(
  recommendation: ContinueStudyingResponse | null,
  recentNotes: NoteListItemResponse[],
): string {
  if (recommendation?.noteId) {
    return `/notes/${recommendation.noteId}/quick-review`;
  }
  if (recentNotes[0]?.id) {
    return `/notes/${recentNotes[0].id}`;
  }
  return "/notes/new";
}

function resolveChallengeQuizHref(
  recommendation: ContinueStudyingResponse | null,
  mostRecentReadyNoteId: string | null,
): string {
  if (recommendation?.noteId) {
    return `/notes/${recommendation.noteId}/challenge-quiz`;
  }
  if (mostRecentReadyNoteId) {
    return `/notes/${mostRecentReadyNoteId}/challenge-quiz`;
  }
  return "/notes/new";
}

function getWelcomeMessage(profileType: SupportedDashboardProfileType) {
  if (profileType === "TEACHER") {
    return "Welcome to NoteLib! Start by creating a note, then generate a Study Pack and review the quiz in Quiz Preview.";
  }
  if (profileType === "PROFESSIONAL") {
    return "Welcome to NoteLib! Start by creating a note, then generate a Study Pack and start your certification review.";
  }
  return "Welcome to NoteLib! Start by creating a note, then generate your first Study Pack.";
}

function getFirstStudyDescription(profileType: SupportedDashboardProfileType) {
  if (profileType === "TEACHER") {
    return "NoteLib helps teachers turn notes into summaries, key concepts, and export-ready quiz previews. Let’s create your first teaching note.";
  }
  if (profileType === "PROFESSIONAL") {
    return "NoteLib helps you turn your study materials into scenario-based quizzes and certification practice. Let's create your first study pack.";
  }
  return "NoteLib helps you turn notes into summaries, key concepts, and quizzes. Let’s create your first study pack.";
}

function TeacherGeneratedQuizSection({
  items,
  emptyActionHref,
  emptyActionLabel,
  emptyActionIcon,
}: Readonly<{
  items: TeacherGeneratedQuizSummary[];
  emptyActionHref: string;
  emptyActionLabel: string;
  emptyActionIcon: "open" | "create";
}>) {
  return (
    <section className="space-y-3 sm:space-y-4">
      <div className="flex flex-col gap-0.5 sm:flex-row sm:items-center sm:justify-between">
        <h2 className="text-lg font-semibold sm:text-xl">Recently Generated Quizzes</h2>
        <p className="text-xs text-foreground/65">{items.length} quiz previews</p>
      </div>
      {items.length > 0 ? (
        <div className="grid gap-4 md:grid-cols-2">
          {items.map((item) => (
            <Link key={item.noteId} href={`/notes/${item.noteId}/quiz`} className="block">
              <Card className="h-full space-y-3 p-4 transition-colors hover:bg-highlight hover:shadow-md sm:p-6">
                <div className="space-y-1.5">
                  <h3 className="text-base font-semibold sm:text-lg">{item.title}</h3>
                  <p className="text-sm text-foreground/70">{item.subject?.trim() || "No subject"}</p>
                  <p className="text-xs text-foreground/60">Generated {formatDashboardDate(item.generatedAt)}</p>
                </div>
                <p className="text-sm text-foreground/75">
                  Review answers and explanations before exporting this quiz for class use.
                </p>
                <div className="flex items-center justify-between text-xs text-foreground/60">
                  <span>{item.questionCount} questions</span>
                  <span>Open Quiz Preview &rarr;</span>
                </div>
              </Card>
            </Link>
          ))}
        </div>
      ) : (
        <Card className="space-y-3 p-4 sm:p-6">
          <h3 className="text-base font-semibold sm:text-lg">No generated quizzes yet</h3>
          <p className="text-sm text-foreground/75">
            Generate a quiz from any ready note to review answers and explanations before class.
          </p>
          <ResponsiveActionLink
            href={emptyActionHref}
            action={emptyActionIcon}
            label={emptyActionLabel}
            className="w-full sm:w-auto"
          />
        </Card>
      )}
    </section>
  );
}

function TeacherReadyToExportSection({
  items,
}: Readonly<{
  items: TeacherGeneratedQuizSummary[];
}>) {
  const latestQuiz = items[0] ?? null;

  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold sm:text-xl">Ready to Export</h2>
        <p className="text-sm text-foreground/75">
          Quiz export stays inside Quiz Preview so you can review answers and explanations first.
        </p>
      </div>
      {latestQuiz ? (
        <>
          <div className="space-y-2 rounded-xl border border-border bg-background p-4">
            <p className="text-sm font-medium text-foreground">{latestQuiz.title}</p>
            <p className="text-xs text-foreground/60">
              {latestQuiz.questionCount} questions • Generated {formatDashboardDate(latestQuiz.generatedAt)}
            </p>
            <p className="text-xs text-foreground/60">
              {items.length} {items.length === 1 ? "quiz preview is" : "quiz previews are"} ready for export.
            </p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <ResponsiveActionLink href={`/notes/${latestQuiz.noteId}/quiz`} action="open" label="Open Latest Quiz Preview" className="w-full sm:w-auto" />
            <ResponsiveActionLink href="/library" action="library" label="View All Notes" variant="outline" className="w-full sm:w-auto" />
          </div>
        </>
      ) : (
        <p className="text-sm text-foreground/75">
          Once a generated quiz exists, it will appear here for quick access to the export-ready preview.
        </p>
      )}
    </Card>
  );
}

function TeacherTipsCard() {
  return (
    <Card className="space-y-3 p-4 sm:p-6">
      <h2 className="text-lg font-semibold sm:text-xl">Teacher Help / Tips</h2>
      <div className="space-y-2 text-sm text-foreground/75">
        <p>Start with one clear lesson note per topic so quiz questions stay focused and export cleanly.</p>
        <p>Generate the quiz after the Study Pack is ready, then review answers and explanations in Quiz Preview before class.</p>
        <p>Use Regenerate when you want a fresh version, then export from Quiz Preview once the set looks right.</p>
      </div>
    </Card>
  );
}

function DashboardGoalCardSkeleton() {
  return (
    <Card
      aria-label="Loading study goal"
      className="overflow-hidden border-blue-500/15 bg-linear-to-br from-blue-500/5 via-background to-emerald-500/5 p-4 sm:p-6"
    >
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-3">
          <div className="h-3 w-24 animate-pulse rounded bg-foreground/10" />
          <div className="h-5 w-64 max-w-full animate-pulse rounded bg-foreground/10" />
          <div className="h-4 w-40 animate-pulse rounded bg-foreground/10" />
        </div>
        <div className="h-10 w-20 animate-pulse rounded bg-foreground/10" />
      </div>
      <div className="mt-4 h-4 w-48 animate-pulse rounded bg-foreground/10" />
    </Card>
  );
}

export default function DashboardPage() {
  const router = useRouter();
  const [items, setItems] = useState<NoteListItemResponse[]>([]);
  const [recentNoteMetaById, setRecentNoteMetaById] = useState<Record<string, { lastReviewedAt: string | null; quizCount: number | null }>>({});
  const [teacherGeneratedQuizzes, setTeacherGeneratedQuizzes] = useState<TeacherGeneratedQuizSummary[]>([]);
  const [greetingName, setGreetingName] = useState("there");
  const [profile, setProfile] = useState<MeResponse | null>(null);
  const [goalSummary, setGoalSummary] = useState<GoalNudgeResponse | null>(null);
  const [goalSummaryLoading, setGoalSummaryLoading] = useState(false);
  const [primaryCollectionGoal, setPrimaryCollectionGoal] = useState<GoalCollectionDetailResponse | null>(null);
  const [primaryCollectionGoalLoading, setPrimaryCollectionGoalLoading] = useState(false);
  const [continueStudying, setContinueStudying] = useState<ContinueStudyingResponse | null>(null);
  const [todayFocus, setTodayFocus] = useState<TodayFocusResponse | null>(null);
  const [overview, setOverview] = useState<DashboardOverviewResponse | null>(null);
  const [feedbackPromptContext, setFeedbackPromptContext] = useState<FeedbackPromptContextResponse | null>(null);
  const [activePaywallModal, setActivePaywallModal] = useState<PaywallModalVariant | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [contentVisible, setContentVisible] = useState(false);
  const [showWelcomeMessage, setShowWelcomeMessage] = useState(false);
  const [showFirstStudyWelcomeModal, setShowFirstStudyWelcomeModal] = useState(false);
  const [showPersonalizationPrompt, setShowPersonalizationPrompt] = useState(false);
  const [showLightweightProfileCompletionPrompt, setShowLightweightProfileCompletionPrompt] = useState(false);
  const { usageSummary } = useBillingUsageSummary();

  const loadDashboard = useCallback(async () => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const [notesResult, meResult, continueStudyingResult, todayFocusResult, overviewResult, feedbackContextResult] = await Promise.allSettled([
        listNotes(DASHBOARD_NOTE_FETCH_LIMIT),
        getMe(),
        getContinueStudyingRecommendation(),
        getTodayFocus(),
        getDashboardOverview(),
        getFeedbackPromptContext(),
      ]);

      if (notesResult.status !== "fulfilled") {
        throw notesResult.reason;
      }

      const notes = notesResult.value;
      setItems(notes);

      if (meResult.status === "fulfilled") {
        const me = meResult.value;
        setProfile(me);
        if (me.primaryCollectionId) {
          setPrimaryCollectionGoal(null);
          setPrimaryCollectionGoalLoading(true);
          void getCollectionGoal(me.primaryCollectionId)
            .then((goal) => {
              setPrimaryCollectionGoal(goal);
            })
            .catch((primaryCollectionError) => {
              globalThis.console.warn("Could not load dashboard primary collection.", primaryCollectionError);
              setPrimaryCollectionGoal(null);
            })
            .finally(() => {
              setPrimaryCollectionGoalLoading(false);
            });
        } else {
          setPrimaryCollectionGoal(null);
          setPrimaryCollectionGoalLoading(false);
        }
        if (me.studyGoal) {
          setGoalSummary(null);
          setGoalSummaryLoading(true);
          void getGoalSummary()
            .then((summary) => {
              setGoalSummary(summary);
            })
            .catch((goalErr) => {
              globalThis.console.warn("Could not load dashboard goal summary.", goalErr);
              setGoalSummary(null);
            })
            .finally(() => {
              setGoalSummaryLoading(false);
            });
        } else {
          setGoalSummary(null);
          setGoalSummaryLoading(false);
        }
        const preferredName = me.firstName?.trim()
          || me.displayName?.trim()
          || "there";
        setGreetingName(preferredName);
        setShowFirstStudyWelcomeModal(isFirstStudyOnboardingEligible(me));

        if (me.profileType === "TEACHER") {
          const generatedQuizItems = [...notes]
            .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
            .slice(0, MAX_TEACHER_QUIZ_NOTES)
            .filter((note) => note.generatedQuizId !== null)
            .map((note) => ({
              noteId: note.id,
              title: note.title?.trim() || "Untitled note",
              subject: note.subject ?? null,
              generatedAt: note.generatedQuizGeneratedAt!,
              updatedAt: note.updatedAt,
              questionCount: note.generatedQuizQuestionCount ?? 0,
            }))
            .sort((left, right) => new Date(right.generatedAt).getTime() - new Date(left.generatedAt).getTime());
          setTeacherGeneratedQuizzes(generatedQuizItems);
          setRecentNoteMetaById({});
        } else {
          const recentStudyPackNotes = [...notes]
            .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
            .slice(0, 4)
            .filter((note) => note.studyPackStatus === "STUDY_PACK_READY" && Boolean(note.studyPackId));

          if (recentStudyPackNotes.length > 0) {
            let lastReviewedAtByNoteId = new Map<string, string | null>();
            try {
              const lastReviewedResults = await getQuickReviewLastReviewedBatch(
                recentStudyPackNotes.map((note) => note.id),
              );
              lastReviewedAtByNoteId = new Map(
                lastReviewedResults.map((result) => [result.noteId, result.lastReviewedAt]),
              );
            } catch {
              // Quick Review history is optional Dashboard metadata; keep Stage 1 content visible.
            }
            const entries = recentStudyPackNotes.map((note) => [
              note.id,
              {
                lastReviewedAt: lastReviewedAtByNoteId.get(note.id) ?? null,
                quizCount: note.quizCount,
              },
            ] as const);
            setRecentNoteMetaById(Object.fromEntries(entries));
          } else {
            setRecentNoteMetaById({});
          }
          setTeacherGeneratedQuizzes([]);
        }
      } else {
        setGoalSummary(null);
        setGoalSummaryLoading(false);
        setPrimaryCollectionGoal(null);
        setPrimaryCollectionGoalLoading(false);
        setTeacherGeneratedQuizzes([]);
        setRecentNoteMetaById({});
      }
      setContinueStudying(continueStudyingResult.status === "fulfilled" ? continueStudyingResult.value : null);
      setTodayFocus(todayFocusResult.status === "fulfilled" ? todayFocusResult.value : null);
      setOverview(overviewResult.status === "fulfilled" ? overviewResult.value : null);
      setFeedbackPromptContext(feedbackContextResult.status === "fulfilled" ? feedbackContextResult.value : null);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load your notes.";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  useEffect(() => {
    if (loading) {
      setContentVisible(false);
      return;
    }
    const timer = setTimeout(() => setContentVisible(true), 20);
    return () => clearTimeout(timer);
  }, [loading]);

  useEffect(() => {
    const authUser = getAuthUser();
    if (!authUser?.emailVerifiedAt) {
      setShowWelcomeMessage(false);
      return;
    }

    const welcomeStorageKey = `notelib-dashboard-welcome-shown-${authUser.id}`;
    const alreadyShown = globalThis.localStorage.getItem(welcomeStorageKey) === "1";
    setShowWelcomeMessage(!alreadyShown);
  }, []);

  useEffect(() => {
    const authUser = getAuthUser();
    if (!authUser?.id || !profile?.onboardingCompletedAt || profile?.learnerLevel) {
      setShowPersonalizationPrompt(false);
      return;
    }
    setShowPersonalizationPrompt(!hasDismissedDashboardPersonalizationPrompt(authUser.id));
  }, [profile?.onboardingCompletedAt, profile?.learnerLevel]);

  useEffect(() => {
    const authUser = getAuthUser();
    const profileIsIncomplete = !profile?.profileType || !profile.learnerLevel || !profile.courseProgram?.trim();
    if (!authUser?.id || !profileIsIncomplete || !hasPendingLightweightProfileCompletion(authUser.id)) {
      setShowLightweightProfileCompletionPrompt(false);
      return;
    }
    setShowLightweightProfileCompletionPrompt(!hasDismissedLightweightProfileCompletionPrompt(authUser.id));
  }, [profile?.courseProgram, profile?.learnerLevel, profile?.profileType]);

  const dismissWelcomeMessage = useCallback(() => {
    const authUser = getAuthUser();
    if (authUser) {
      const welcomeStorageKey = `notelib-dashboard-welcome-shown-${authUser.id}`;
      globalThis.localStorage.setItem(welcomeStorageKey, "1");
    }
    setShowWelcomeMessage(false);
  }, []);

  const dismissPersonalizationPrompt = useCallback(() => {
    const authUser = getAuthUser();
    if (authUser?.id) {
      dismissDashboardPersonalizationPrompt(authUser.id);
    }
    setShowPersonalizationPrompt(false);
  }, []);

  const dismissLightweightProfilePrompt = useCallback(() => {
    const authUser = getAuthUser();
    if (authUser?.id) {
      dismissLightweightProfileCompletionPrompt(authUser.id);
    }
    setShowLightweightProfileCompletionPrompt(false);
  }, []);

  const completeLightweightProfilePrompt = useCallback((me: MeResponse) => {
    const authUser = getAuthUser();
    if (authUser) {
      clearPendingLightweightProfileCompletion(authUser.id);
      setAuthUser({
        ...authUser,
        displayName: me.displayName,
        profileType: me.profileType,
        emailVerifiedAt: me.emailVerifiedAt,
        onboardingCompletedAt: me.onboardingCompletedAt,
        productOnboardingCompletedAt: me.productOnboardingCompletedAt,
      });
    }
    setProfile(me);
    setShowLightweightProfileCompletionPrompt(false);
  }, []);

  const handleOpenPreferences = useCallback(() => {
    router.push(`/profile?from=dashboard#${PROFILE_LEARNING_PROFILE_SECTION_ID}`);
  }, [router]);

  const handleSkipFirstStudyOnboarding = useCallback(async () => {
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
      // Best-effort dismissal only.
    } finally {
      clearFirstStudyOnboardingStep(authUser.id);
      setShowFirstStudyWelcomeModal(false);
    }
  }, []);

  const recentNotes = useMemo(
    () => [...items]
      .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
      .slice(0, 4),
    [items],
  );
  const recentReadyNotes = useMemo(
    () => recentNotes.filter((note) => note.studyPackStatus === "STUDY_PACK_READY"),
    [recentNotes],
  );
  const recentTeacherGeneratedQuizzes = useMemo(
    () => teacherGeneratedQuizzes.slice(0, 4),
    [teacherGeneratedQuizzes],
  );
  const dashboardProfileType = useMemo(
    () => resolveDashboardProfileType(profile?.profileType),
    [profile?.profileType],
  );
  const totalNoteCount = overview?.totalNoteCount ?? items.length;
  const hasCompletedSession = overview?.hasQuizQuestions
    ?? items.some((note) => (note.quizCount ?? 0) > 0);
  const latestCompletedTopic = recentReadyNotes.find((note) => (note.quizCount ?? 0) > 0)?.subject
    ?? recentReadyNotes.find((note) => (note.quizCount ?? 0) > 0)?.title
    ?? null;
  const dashboardGuidanceRules: GuidanceRule[] = [
    {
      id: "dashboard-post-completion",
      priority: 1,
      condition: () => dashboardProfileType !== "TEACHER" && latestCompletedTopic !== null,
      message: `Nice work with ${latestCompletedTopic}. Come back to review it again later — spaced review helps it stick.`,
    },
    {
      id: "teacher-dashboard-intro",
      priority: 2,
      condition: () => dashboardProfileType === "TEACHER",
      message: "NoteLib turns your lesson notes into ready-to-use quiz drafts. Start by creating a note with your lesson content.",
    },
    {
      id: "dashboard-review-rhythm",
      priority: 3,
      condition: () => dashboardProfileType !== "TEACHER" && hasCompletedSession,
      message: "A quick return visit matters: reviewing concepts over time makes recall stronger than one long study session.",
    },
  ];
  const activeDashboardTip = pickActiveGuidance(dashboardGuidanceRules);
  const studentPrimaryHref = useMemo(
    () => resolveStudentPrimaryHref(continueStudying, recentNotes),
    [continueStudying, recentNotes],
  );
  const boardExamChallengeHref = useMemo(
    () => resolveChallengeQuizHref(
      continueStudying,
      overview?.mostRecentReadyNoteId ?? recentReadyNotes[0]?.id ?? null,
    ),
    [continueStudying, overview?.mostRecentReadyNoteId, recentReadyNotes],
  );
  const examCountdown = useMemo(
    () => formatExamCountdown(profile?.examDate ?? null),
    [profile?.examDate],
  );
  const examPacingLine = overview?.examPacingPlan
    ? `${overview.examPacingPlan.dueConceptCount} concepts due — study ~${overview.examPacingPlan.dailyConceptTarget}/day to stay on track for your exam in ${overview.examPacingPlan.daysRemaining} days.`
    : null;
  const studyPacksRemaining = usageSummary
    ? resolveRemainingUsageCredits(
      usageSummary.usage.studyPacksUsed,
      usageSummary.limits.studyPacksPerMonth,
      usageSummary.remaining?.studyPacksRemaining,
    )
    : null;
  const usageResetDateLabel = formatStudyPackResetDate(usageSummary?.usageCycle?.endsAt);
  const shouldShowNearLimitBanner = usageSummary
    ? shouldShowNearStudyPackLimitBanner(
      usageSummary.plan,
      studyPacksRemaining,
    )
    : false;
  const shouldShowFreeUpgradeCard = usageSummary?.plan === "FREE" && dashboardProfileType !== "TEACHER";
  const currentPlan = usageSummary?.plan ?? profile?.planType ?? "FREE";
  const teacherGeneratedQuizEmptyAction = recentReadyNotes[0]?.id
    ? {
        href: `/notes/${recentReadyNotes[0].id}`,
        label: "Open Recent Ready Note",
        icon: "open" as const,
      }
    : {
        href: "/notes/new",
        label: "Create Note",
        icon: "create" as const,
      };
  const quickReviewCard = (
    <DashboardActionCard
      title="Quick Review"
      description={dashboardProfileType === "PROFESSIONAL"
        ? "Use Quick Review to reinforce your study material and keep applied knowledge fresh."
        : "Use Quick Review to reinforce what you just studied and keep recall active."}
      actionLabel="Start Quick Review"
      actionHref={recentReadyNotes[0]?.id ? `/notes/${recentReadyNotes[0].id}/quick-review` : "/notes/new"}
      actionIcon="quickReview"
      secondaryActionLabel="Review Recent Note"
      secondaryActionHref={recentNotes[0]?.id ? `/notes/${recentNotes[0].id}` : "/library"}
      secondaryActionIcon="open"
    />
  );
  const dashboardGoalFallback = profile?.studyGoal ? (
    goalSummary ? (
      <DashboardGoalCard goalSummary={goalSummary} />
    ) : goalSummaryLoading ? (
      <DashboardGoalCardSkeleton />
    ) : null
  ) : (
    <GoalPromptBanner
      studyGoal={profile?.studyGoal ?? null}
      courseProgram={profile?.courseProgram ?? null}
      profileType={profile?.profileType ?? null}
    />
  );
  const hasPrimaryCollection = Boolean(profile?.primaryCollectionId);

  return (
    <div className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <DashboardHero
        greetingName={greetingName}
        supportingText={dashboardProfileType === "BOARD_EXAM"
          ? "Ready to focus on exam prep?"
          : dashboardProfileType === "TEACHER"
            ? "Ready to build your next class review?"
            : dashboardProfileType === "PROFESSIONAL"
              ? "Ready to build certification readiness?"
            : undefined}
        description={dashboardProfileType === "BOARD_EXAM"
          ? "Your exam prep workspace. Practice, review weak areas, and keep your momentum steady."
          : dashboardProfileType === "TEACHER"
            ? "Turn materials into quiz-ready study packs, question sets, and reusable class review content."
            : dashboardProfileType === "PROFESSIONAL"
              ? "Your professional learning workspace. Review study material, practice applied scenarios, and track certification readiness."
            : "Your note workspace. Revisit saved notes, reinforce weak concepts, and keep studying with less friction."}
      />

      {loading ? (
        <DashboardLoading />
      ) : error ? (
        <div
          className="space-y-6"
          style={{ opacity: contentVisible ? 1 : 0, transition: "opacity 220ms ease-out" }}
        >
          <DashboardError message={error} onRetry={loadDashboard} />
        </div>
      ) : (
        <div
          className="space-y-6"
          style={{ opacity: contentVisible ? 1 : 0, transition: "opacity 220ms ease-out" }}
        >
          {hasPrimaryCollection ? (
            primaryCollectionGoal ? (
              <DashboardPrimaryCollectionHero
                goal={primaryCollectionGoal}
                profileType={profile?.profileType}
              />
            ) : primaryCollectionGoalLoading ? (
              <DashboardPrimaryCollectionHeroSkeleton />
            ) : dashboardGoalFallback
          ) : null}
          {shouldShowNearLimitBanner ? (
            <NearLimitBanner
              planType={usageSummary?.plan ?? "FREE"}
              remainingCredits={studyPacksRemaining}
              resetDateLabel={usageResetDateLabel}
              analyticsSource="dashboard_study_pack_near_limit"
            />
          ) : null}
          {shouldShowFreeUpgradeCard ? <FreePlanUpgradeCard /> : null}
          {!hasPrimaryCollection ? dashboardGoalFallback : null}
          {showWelcomeMessage && !showFirstStudyWelcomeModal ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <p className="text-sm text-foreground/80">
                {getWelcomeMessage(dashboardProfileType)}
              </p>
              <div className="flex flex-col gap-2 sm:flex-row">
                <ResponsiveActionLink href="/notes/new" action="create" label="Create Note" className="w-full sm:w-auto" />
                <ResponsiveActionButton type="button" variant="outline" className="w-full sm:w-auto" onClick={dismissWelcomeMessage} action="back" label="Dismiss" />
              </div>
            </Card>
          ) : null}
          {showLightweightProfileCompletionPrompt && profile ? (
            <LightweightProfileCompletionPrompt
              initialProfileType={profile.profileType}
              initialLearnerLevel={profile.learnerLevel}
              initialCourseProgram={profile.courseProgram}
              initialExamDate={profile.examDate}
              onDismiss={dismissLightweightProfilePrompt}
              onComplete={completeLightweightProfilePrompt}
            />
          ) : null}
          {profile && feedbackPromptContext ? (
            <WelcomeBackFeedbackPrompt
              userId={profile.id}
              returningAfterInactivity={feedbackPromptContext.returningAfterInactivity}
              hasCompletedQuizSession={feedbackPromptContext.hasCompletedQuizSession}
            />
          ) : null}
          {activeDashboardTip ? <GuidanceTip tipId={activeDashboardTip.id} message={activeDashboardTip.message} /> : null}
          {dashboardProfileType !== "TEACHER" && todayFocus?.type === "DUE_CONCEPTS_REVIEW" ? (
            <TodayFocusCard
              focus={todayFocus}
              onUnlockAdaptivePractice={() => setActivePaywallModal("adaptive-practice")}
            />
          ) : null}
          {dashboardProfileType === "STUDENT" || dashboardProfileType === "PROFESSIONAL" ? (
            <>
              {continueStudying?.noteId ? (
                <section className="space-y-3">
                  <h2 className="text-lg font-semibold sm:text-xl">Continue Studying</h2>
                  <ContinueSpotlight recommendation={continueStudying} profileType={dashboardProfileType} />
                </section>
              ) : (
                <DashboardActionCard
                  title="Continue Studying"
                  description={dashboardProfileType === "PROFESSIONAL"
                    ? "Jump back into your study material or start a certification-focused review session."
                    : "Jump back into your latest note or start a fresh review session."}
                  actionLabel="Continue Studying"
                  actionHref={studentPrimaryHref}
                  actionIcon="open"
                  secondaryActionLabel="Create Note"
                  secondaryActionHref="/notes/new"
                  secondaryActionIcon="create"
                />
              )}
              {showPersonalizationPrompt ? (
                <DashboardPersonalizationPrompt
                  onDismiss={dismissPersonalizationPrompt}
                  onOpenPreferences={handleOpenPreferences}
                />
              ) : null}
              {dashboardProfileType === "PROFESSIONAL" ? (
                <ProfessionalInterviewPracticeCard
                  currentPlan={currentPlan}
                  usageSummary={usageSummary}
                  readyNoteId={recentReadyNotes[0]?.id ?? null}
                  onUpgrade={() => setActivePaywallModal("interview-practice-limit")}
                />
              ) : null}
              <DashboardFocusAreasCard
                title="Weak Concepts"
                focusAreas={overview?.focusAreas ?? null}
                emptyStateText={dashboardProfileType === "PROFESSIONAL"
                  ? "Finish a few Certification Reviews to reveal the concepts that need more applied practice."
                  : "Finish a few Challenge Quizzes to reveal the concepts that need more review."}
                primaryActionLabel="Practice Weak Concepts"
                lockedActionLabel="Unlock Adaptive Practice"
                onUnlockAdaptivePractice={() => setActivePaywallModal("adaptive-practice")}
              />
              {totalNoteCount === 0 ? (
                <DashboardEmpty profileType={dashboardProfileType} />
              ) : (
                <StudyPackGrid
                  notes={recentNotes}
                  totalNotes={totalNoteCount}
                  recentNoteMetaById={recentNoteMetaById}
                  title="Recent Notes"
                  countLabel="saved"
                  viewAllLabel="View All in Library"
                />
              )}
              <DashboardStudyPlanSection
                courseProgram={profile?.courseProgram ?? null}
                profileType={profile?.profileType ?? null}
                primaryCollectionId={profile?.primaryCollectionId ?? null}
                discoveryPresentation="pointer"
              />
              <DashboardCommunityNotesSection
                courseProgram={profile?.courseProgram ?? null}
                viewerUserId={profile?.id ?? null}
              />
              <DashboardStrongestNotes />
              {hasCompletedSession ? (
                <>
                  <DashboardMonthlyUsageCard usageSummary={usageSummary} title="Usage / Progress" />
                  {quickReviewCard}
                </>
              ) : (
                <>
                  {quickReviewCard}
                  <DashboardMonthlyUsageCard usageSummary={usageSummary} title="Usage / Progress" />
                </>
              )}
            </>
          ) : null}

          {dashboardProfileType === "BOARD_EXAM" ? (
            <>
              {dashboardProfileType === "BOARD_EXAM" && examCountdown ? (
                <Card className="space-y-3 p-4 sm:p-6">
                  <h2 className="text-lg font-semibold sm:text-xl">Exam Countdown</h2>
                  <p className="text-sm text-foreground/75">{examPacingLine ?? examCountdown}</p>
                </Card>
              ) : null}
              <DashboardActionCard
                title="Start Board Exam"
                description="Exam Reviewer mode opens in Board Exam by default, with Challenge Quiz still available from the setup flow."
                actionLabel="Start Board Exam"
                actionHref={boardExamChallengeHref}
                actionIcon="challengeQuiz"
                secondaryActionLabel="Review Recent Note"
                secondaryActionHref={recentNotes[0]?.id ? `/notes/${recentNotes[0].id}` : "/library"}
                secondaryActionIcon="open"
              />
              {showPersonalizationPrompt ? (
                <DashboardPersonalizationPrompt
                  onDismiss={dismissPersonalizationPrompt}
                  onOpenPreferences={handleOpenPreferences}
                />
              ) : null}
              <DashboardFocusAreasCard
                title="Weak Areas"
                focusAreas={overview?.focusAreas ?? null}
                emptyStateText="Finish a few Practice Quizzes to reveal the topics that need more exam prep."
                showAction={false}
                onUnlockAdaptivePractice={() => setActivePaywallModal("adaptive-practice")}
              />
              <Card className="space-y-3 p-4 sm:p-6">
                <h2 className="text-lg font-semibold sm:text-xl">Adaptive Practice</h2>
                <p className="text-sm text-foreground/75">
                  Focus on weak topics with targeted practice sets built from your recent performance.
                </p>
                {overview?.focusAreas?.practiceNoteId ? (
                  overview.focusAreas.adaptivePracticeAvailable ? (
                    <ResponsiveActionLink
                      href={`/notes/${overview.focusAreas.practiceNoteId}/adaptive-practice`}
                      action="adaptivePractice"
                      label="Practice Weak Areas"
                    />
                  ) : (
                    <ResponsiveActionButton type="button" variant="outline" onClick={() => setActivePaywallModal("adaptive-practice")} action="adaptivePractice" label="Unlock Adaptive Practice" />
                  )
                ) : (
                  <p className="text-sm text-foreground/70">
                    Complete Challenge Quizzes first to unlock targeted weak-area practice.
                  </p>
                )}
              </Card>
              <DashboardWeeklyActivityCard
                activity={overview?.weeklyActivity ?? null}
                title="Study Activity This Week"
              />
              <DashboardStudyPlanSection
                courseProgram={profile?.courseProgram ?? null}
                profileType={profile?.profileType ?? null}
                primaryCollectionId={profile?.primaryCollectionId ?? null}
                discoveryPresentation="pointer"
              />
              <DashboardCommunityNotesSection
                courseProgram={profile?.courseProgram ?? null}
                viewerUserId={profile?.id ?? null}
              />
              <DashboardMonthlyUsageCard usageSummary={usageSummary} title="Usage / Progress" />
            </>
          ) : null}

          {dashboardProfileType === "TEACHER" ? (
            <>
              <DashboardActionCard
                title="Create Teaching Material"
                description="Create a note, generate a Study Pack, then review the generated quiz before exporting it for class use."
                actionLabel="Create Note"
                actionHref="/notes/new"
                actionIcon="create"
                secondaryActionLabel="Open Library"
                secondaryActionHref="/library"
                secondaryActionIcon="library"
              />
              {showPersonalizationPrompt ? (
                <DashboardPersonalizationPrompt
                  onDismiss={dismissPersonalizationPrompt}
                  onOpenPreferences={handleOpenPreferences}
                />
              ) : null}
              <TeacherReadyToExportSection items={teacherGeneratedQuizzes} />
              <TeacherGeneratedQuizSection
                items={recentTeacherGeneratedQuizzes}
                emptyActionHref={teacherGeneratedQuizEmptyAction.href}
                emptyActionLabel={teacherGeneratedQuizEmptyAction.label}
                emptyActionIcon={teacherGeneratedQuizEmptyAction.icon}
              />
              {totalNoteCount === 0 ? (
                <Card className="space-y-4 p-4 sm:p-6">
                  <h2 className="text-lg font-semibold sm:text-xl">Start your teaching workspace</h2>
                  <p className="max-w-2xl text-sm text-foreground/75">
                    Create a note, generate a quiz, then export it as DOCX for your class. Use Exam Builder to combine multiple notes into a single exam.
                  </p>
                  <div className="flex flex-col gap-2 sm:flex-row">
                    <ResponsiveActionLink href="/notes/new" action="create" label="Create Your First Note" className="w-full sm:w-auto" />
                    <ResponsiveActionLink href="/library" action="library" label="Open Library" variant="outline" className="w-full sm:w-auto" />
                  </div>
                </Card>
              ) : (
                <StudyPackGrid
                  notes={recentNotes}
                  totalNotes={totalNoteCount}
                  recentNoteMetaById={{}}
                  title="Recent Notes"
                  countLabel="notes"
                  viewAllLabel="View All in Library"
                  readyStatusLabel="Study Pack Ready"
                  draftStatusLabel="Draft"
                />
              )}
              <DashboardCommunityNotesSection
                courseProgram={profile?.courseProgram ?? null}
                viewerUserId={profile?.id ?? null}
              />
              <TeacherTipsCard />
            </>
          ) : null}
        </div>
      )}

      <PaywallModal
        isOpen={activePaywallModal !== null}
        variant={activePaywallModal ?? "adaptive-practice"}
        onClose={() => setActivePaywallModal(null)}
        source="dashboard_focus_areas"
      />

      <AppModal
        isOpen={showFirstStudyWelcomeModal}
        title="Welcome to NoteLib"
        description={getFirstStudyDescription(dashboardProfileType)}
        onClose={() => {
          void handleSkipFirstStudyOnboarding();
        }}
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <ResponsiveActionButton
              type="button"
              variant="outline"
              className="w-full sm:w-auto"
              onClick={() => {
                void handleSkipFirstStudyOnboarding();
              }}
              action="back"
              label="Skip for now"
              showTextOnMobile
            />
            <ResponsiveActionButton
              type="button"
              className="w-full sm:w-auto"
              onClick={() => {
                const authUser = getAuthUser();
                if (authUser) {
                  setFirstStudyOnboardingStep(authUser.id, "create-note");
                }
                setShowFirstStudyWelcomeModal(false);
                router.push("/notes/new");
              }}
              action="create"
              label="Create My First Note"
              showTextOnMobile
            />
          </div>
        )}
      />
    </div>
  );
}
