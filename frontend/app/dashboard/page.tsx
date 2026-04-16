"use client";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { NearLimitBanner } from "@/components/billing/near-limit-banner";
import { PaywallModal, type PaywallModalVariant } from "@/components/billing/paywall-modal";
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
  getMe,
  getQuickReviewPerformanceSummary,
  listNotes,
  type ContinueStudyingResponse,
  type DashboardOverviewResponse,
  type MeResponse,
  type NoteListItemResponse,
  type ProfileType,
} from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { ContinueSpotlight } from "./continue-spotlight";
import { DashboardHero } from "./dashboard-hero";
import { DashboardMonthlyUsageCard } from "./dashboard-monthly-usage-card";
import { DashboardFocusAreasCard } from "./dashboard-focus-areas-card";
import { DashboardWeeklyActivityCard } from "./dashboard-weekly-activity-card";
import { StudyPackGrid } from "./study-pack-grid";
import { DashboardLoading } from "./dashboard-loading";
import { DashboardEmpty } from "./dashboard-empty";
import { DashboardError } from "./dashboard-error";
import { FreePlanUpgradeCard } from "./free-plan-upgrade-card";
import { DashboardActionCard } from "./dashboard-action-card";
import { DashboardStrongestNotes } from "./dashboard-strongest-notes";
import { AppModal } from "@/components/ui/app-modal";
import {
  clearFirstStudyOnboardingStep,
  isFirstStudyOnboardingEligible,
  setFirstStudyOnboardingStep,
} from "@/lib/first-study-onboarding";

type SupportedDashboardProfileType = "STUDENT" | "BOARD_EXAM" | "TEACHER";

function resolveDashboardProfileType(profileType: ProfileType | null | undefined): SupportedDashboardProfileType {
  if (profileType === "BOARD_EXAM" || profileType === "TEACHER") {
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
  notes: NoteListItemResponse[],
): string {
  if (recommendation?.noteId) {
    return `/notes/${recommendation.noteId}/challenge-quiz`;
  }
  const recentReadyNote = notes.find((note) => note.studyPackStatus === "STUDY_PACK_READY");
  if (recentReadyNote?.id) {
    return `/notes/${recentReadyNote.id}/challenge-quiz`;
  }
  return "/notes/new";
}

export default function DashboardPage() {
  const router = useRouter();
  const [items, setItems] = useState<NoteListItemResponse[]>([]);
  const [recentNoteMetaById, setRecentNoteMetaById] = useState<Record<string, { lastReviewedAt: string | null; quizCount: number | null }>>({});
  const [greetingName, setGreetingName] = useState("there");
  const [profile, setProfile] = useState<MeResponse | null>(null);
  const [continueStudying, setContinueStudying] = useState<ContinueStudyingResponse | null>(null);
  const [overview, setOverview] = useState<DashboardOverviewResponse | null>(null);
  const [activePaywallModal, setActivePaywallModal] = useState<PaywallModalVariant | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [contentVisible, setContentVisible] = useState(false);
  const [showWelcomeMessage, setShowWelcomeMessage] = useState(false);
  const [showFirstStudyWelcomeModal, setShowFirstStudyWelcomeModal] = useState(false);
  const { usageSummary } = useBillingUsageSummary();

  const loadDashboard = useCallback(async () => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const notes = await listNotes();
      setItems(notes);

      const recentStudyPackNotes = [...notes]
        .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
        .slice(0, 4)
        .filter((note) => note.studyPackStatus === "STUDY_PACK_READY" && Boolean(note.studyPackId));

      if (recentStudyPackNotes.length > 0) {
        const entries = await Promise.all(
          recentStudyPackNotes.map(async (note) => {
            const quickReviewResult = await getQuickReviewPerformanceSummary(note.id).catch(() => null);
            return [
              note.id,
              {
                lastReviewedAt: quickReviewResult?.lastReviewedAt ?? null,
                quizCount: note.quizCount,
              },
            ] as const;
          }),
        );
        setRecentNoteMetaById(Object.fromEntries(entries));
      } else {
        setRecentNoteMetaById({});
      }

      const [meResult, continueStudyingResult, overviewResult] = await Promise.allSettled([
        getMe(),
        getContinueStudyingRecommendation(),
        getDashboardOverview(),
      ]);

      if (meResult.status === "fulfilled") {
        const me = meResult.value;
        setProfile(me);
        const preferredName = me.firstName?.trim()
          || me.displayName?.trim()
          || "there";
        setGreetingName(preferredName);
        setShowFirstStudyWelcomeModal(isFirstStudyOnboardingEligible(me));
      }
      setContinueStudying(continueStudyingResult.status === "fulfilled" ? continueStudyingResult.value : null);
      setOverview(overviewResult.status === "fulfilled" ? overviewResult.value : null);
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

  const dismissWelcomeMessage = useCallback(() => {
    const authUser = getAuthUser();
    if (authUser) {
      const welcomeStorageKey = `notelib-dashboard-welcome-shown-${authUser.id}`;
      globalThis.localStorage.setItem(welcomeStorageKey, "1");
    }
    setShowWelcomeMessage(false);
  }, []);

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
  const dashboardProfileType = useMemo(
    () => resolveDashboardProfileType(profile?.profileType),
    [profile?.profileType],
  );
  const studentPrimaryHref = useMemo(
    () => resolveStudentPrimaryHref(continueStudying, recentNotes),
    [continueStudying, recentNotes],
  );
  const boardExamChallengeHref = useMemo(
    () => resolveChallengeQuizHref(continueStudying, items),
    [continueStudying, items],
  );
  const examCountdown = useMemo(
    () => formatExamCountdown(profile?.examDate ?? null),
    [profile?.examDate],
  );
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
  const shouldShowFreeUpgradeCard = usageSummary?.plan === "FREE";
  const teacherQuizBuilderHref = "/notes/new?mode=quiz";
  const teacherPasteMaterialHref = "/notes/new?source=paste";
  const teacherUploadHref = "/notes/new?source=upload";
  const showTeacherRecentQuizSection = recentReadyNotes.length > 0;

  return (
    <div className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <DashboardHero
        greetingName={greetingName}
        description={dashboardProfileType === "BOARD_EXAM"
          ? "Your exam prep workspace. Practice, review weak areas, and keep your momentum steady."
          : dashboardProfileType === "TEACHER"
            ? "Turn materials into quiz-ready study packs, question sets, and reusable class review content."
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
          {shouldShowNearLimitBanner ? (
            <NearLimitBanner
              planType={usageSummary?.plan ?? "FREE"}
              remainingCredits={studyPacksRemaining}
              resetDateLabel={usageResetDateLabel}
            />
          ) : null}
          {shouldShowFreeUpgradeCard ? <FreePlanUpgradeCard /> : null}
          {showWelcomeMessage && !showFirstStudyWelcomeModal ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <p className="text-sm text-foreground/80">
                Welcome to NoteLib! Start by creating a note, then generate your first Study Pack.
              </p>
              <div className="flex flex-col gap-2 sm:flex-row">
                <ResponsiveActionLink href="/notes/new" action="create" label="Create Note" className="w-full sm:w-auto" />
                <ResponsiveActionButton type="button" variant="outline" className="w-full sm:w-auto" onClick={dismissWelcomeMessage} action="back" label="Dismiss" />
              </div>
            </Card>
          ) : null}
          {dashboardProfileType === "STUDENT" ? (
            <>
              {continueStudying?.noteId ? (
                <section className="space-y-3">
                  <h2 className="text-lg font-semibold sm:text-xl">Continue Studying</h2>
                  <ContinueSpotlight recommendation={continueStudying} />
                </section>
              ) : (
                <DashboardActionCard
                  title="Continue Studying"
                  description="Jump back into your latest note or start a fresh review session."
                  actionLabel="Continue Studying"
                  actionHref={studentPrimaryHref}
                  actionIcon="open"
                  secondaryActionLabel="Create Note"
                  secondaryActionHref="/notes/new"
                  secondaryActionIcon="create"
                />
              )}
              <DashboardFocusAreasCard
                title="Weak Concepts"
                focusAreas={overview?.focusAreas ?? null}
                emptyStateText="Finish a few Challenge Quizzes to reveal the concepts that need more review."
                primaryActionLabel="Practice Weak Concepts"
                lockedActionLabel="Unlock Adaptive Practice"
                onUnlockAdaptivePractice={() => setActivePaywallModal("adaptive-practice")}
              />
              {items.length === 0 ? (
                <DashboardEmpty />
              ) : (
                <StudyPackGrid
                  notes={recentNotes}
                  totalNotes={items.length}
                  recentNoteMetaById={recentNoteMetaById}
                  title="Recent Notes"
                  countLabel="saved"
                  viewAllLabel="View All in Library"
                />
              )}
              <DashboardStrongestNotes />
              <DashboardActionCard
                title="Quick Review"
                description="Use Quick Review to reinforce what you just studied and keep recall active."
                actionLabel="Start Quick Review"
                actionHref={recentReadyNotes[0]?.id ? `/notes/${recentReadyNotes[0].id}/quick-review` : "/notes/new"}
                actionIcon="quickReview"
                secondaryActionLabel="Review Recent Note"
                secondaryActionHref={recentNotes[0]?.id ? `/notes/${recentNotes[0].id}` : "/library"}
                secondaryActionIcon="open"
              />
              <DashboardMonthlyUsageCard usageSummary={usageSummary} title="Usage / Progress" />
            </>
          ) : null}

          {dashboardProfileType === "BOARD_EXAM" ? (
            <>
              {examCountdown ? (
                <Card className="space-y-3 p-4 sm:p-6">
                  <h2 className="text-lg font-semibold sm:text-xl">Exam Countdown</h2>
                  <p className="text-sm text-foreground/75">{examCountdown}</p>
                </Card>
              ) : null}
              <DashboardActionCard
                title="Practice Challenge Quiz"
                description="Use timed quiz practice to prepare for test conditions and sharpen exam recall."
                actionLabel="Practice Challenge Quiz"
                actionHref={boardExamChallengeHref}
                actionIcon="challengeQuiz"
                secondaryActionLabel="Review Recent Note"
                secondaryActionHref={recentNotes[0]?.id ? `/notes/${recentNotes[0].id}` : "/library"}
                secondaryActionIcon="open"
              />
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
              <DashboardMonthlyUsageCard usageSummary={usageSummary} title="Usage / Progress" />
            </>
          ) : null}

          {dashboardProfileType === "TEACHER" ? (
            <>
              <DashboardActionCard
                title="Create Quiz"
                description="Turn teaching materials into quiz-ready study packs and question sets for class review."
                actionLabel="Create Quiz"
                actionHref={teacherQuizBuilderHref}
                actionIcon="challengeQuiz"
                secondaryActionLabel="Add Material"
                secondaryActionHref="/notes/new"
                secondaryActionIcon="create"
              />
              <DashboardActionCard
                title="Upload / Paste Material"
                description="Start with pasted notes, reviewer text, or uploaded files to build quiz material faster."
                actionLabel="Paste Material"
                actionHref={teacherPasteMaterialHref}
                actionIcon="studyPack"
                secondaryActionLabel="Upload Material"
                secondaryActionHref={teacherUploadHref}
                secondaryActionIcon="studyPack"
              />
              {items.length === 0 ? (
                <DashboardEmpty />
              ) : (
                <StudyPackGrid
                  notes={recentNotes}
                  totalNotes={items.length}
                  recentNoteMetaById={recentNoteMetaById}
                  title="Recent Materials"
                  countLabel="materials"
                  viewAllLabel="View All Materials"
                  readyStatusLabel="Quiz Material"
                  draftStatusLabel="Material Draft"
                />
              )}
              {showTeacherRecentQuizSection ? (
                <StudyPackGrid
                  notes={recentReadyNotes}
                  totalNotes={recentReadyNotes.length}
                  recentNoteMetaById={recentNoteMetaById}
                  title="Recently Generated Quizzes"
                  countLabel="quiz-ready"
                  viewAllLabel="View All Quiz Materials"
                  readyStatusLabel="Quiz Ready"
                  draftStatusLabel="Draft"
                />
              ) : (
                <Card className="space-y-3 p-4 sm:p-6">
                  <h2 className="text-lg font-semibold sm:text-xl">Recently Generated Quizzes</h2>
                  <p className="text-sm text-foreground/75">
                    Generate your first Study Pack to open quiz-ready material for class review.
                  </p>
                </Card>
              )}
              <DashboardWeeklyActivityCard
                activity={overview?.weeklyActivity ?? null}
                title="Activity"
                labels={{
                  studyPacksCreated: "Materials Created",
                  quizzesTaken: "Quizzes Generated",
                  adaptiveSessions: "Adaptive Sessions",
                  studyDays: "Active Days",
                }}
              />
              <DashboardMonthlyUsageCard usageSummary={usageSummary} title="Usage" />
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
        description="NoteLib helps you turn notes into summaries, key concepts, and quizzes. Let’s create your first study pack."
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
