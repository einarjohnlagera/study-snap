import {
  beginManualLogoutRedirect,
  clearAuthUser,
  getAccessToken,
  getAuthUser,
  patchAuthUser,
  getRefreshToken,
  handleUnauthorizedSession,
  setAuthUser,
  type AuthUser,
} from "./auth";
import type { MePlanResponse } from "./me-plan";
import type { ThemePreference } from "./theme-preferences";

export type { MePlanResponse } from "./me-plan";

export type QuizItem = {
  question: string;
  choices: string[];
  correctIndex: number;
  correctIndices?: number[] | null;
  answerIndex?: number;
  correctAnswerIndex?: number;
  answer?: string;
  concept?: string;
  explanation: string;
  questionFormat?: "MCQ" | "TRUE_FALSE" | "MULTI_SELECT" | "MATCHING" | null;
  questionGroup?: string | null;
  questionType?: "CONCEPTUAL" | "COMPUTATIONAL" | null;
  workingSolution?: string | null;
};

export type GeneratedQuizResponse = {
  id?: string;
  noteId: string;
  questions: QuizItem[];
  generatedAt: string;
};

export type QuizShareLinkResponse = {
  id: string;
  token: string;
  shareUrl: string;
  isActive: boolean;
  createdAt: string;
};

export type PublicQuizItem = {
  question: string;
  choices: string[];
  concept?: string | null;
};

export type PublicSharedQuizResponse = {
  quizId: string;
  noteTitle: string;
  questions: PublicQuizItem[];
};

export type SharedQuizResultItem = {
  correct: boolean;
  correctIndex: number;
  explanation?: string | null;
};

export type SharedQuizResultsResponse = {
  score: number;
  total: number;
  items: SharedQuizResultItem[];
};

export type QuizDocxExportMode = "QUIZ_ONLY" | "WITH_ANSWERS";

export type StudyPackResponse = {
  id: string;
  noteId: string | null;
  inputType: "text" | "image";
  extractedText: string | null;
  title: string;
  summary: string;
  sourceText?: string | null;
  subject?: string | null;
  keyConcepts: string[];
  tags: string[];
  quiz: QuizItem[];
  createdAt: string;
  meta: {
    ocrConfidence: number | null;
    latencyMs: number | null;
  };
};

export type NoteTextExtractionResponse = {
  inputType: "image" | "txt" | "pdf" | "docx";
  extractedText: string;
  meta: {
    ocrConfidence: number | null;
    lowConfidence: boolean;
  };
};

export type StudyPackListItemResponse = {
  id: string;
  title: string;
  summaryPreview: string;
  quizCount: number;
  subject?: string | null;
  tags: string[];
  createdAt: string;
};

export type StudyPackListPageResponse = {
  items: StudyPackListItemResponse[];
  nextCursor: string | null;
  hasMore: boolean;
};

export type ContinueStudyingReason = "RESUME_REVIEW" | "LOW_SCORE_RECENT" | "RECENTLY_OPENED" | "RECENTLY_CREATED";
export type ContinueStudyingResumeState = "QUESTION_IN_PROGRESS" | "RETRY_TRANSITION" | "RETRY_IN_PROGRESS";
export type ContinueStudyingResumeType = "QUICK_REVIEW" | "CHALLENGE" | "ADAPTIVE" | "LONG_EXAM";
export type TodayFocusType =
  | "RESUME_REVIEW"
  | "RETRY_REVIEW"
  | "PRACTICE_WEAK_CONCEPT"
  | "REVIEW_PACK"
  | "STUDY_SUGGESTION";

export type ContinueStudyingResponse = {
  studyPackId: string | null;
  noteId: string | null;
  noteTitle: string | null;
  subject: string | null;
  courseProgram: string | null;
  summaryPreview: string | null;
  resumeType: ContinueStudyingResumeType | null;
  reason: ContinueStudyingReason | null;
  lastScorePercentage: number | null;
  lastReviewedAt: string | null;
  lastOpenedAt: string | null;
  createdAt: string | null;
  currentQuestionIndex: number | null;
  totalQuestions: number | null;
  currentRound: "INITIAL" | "RETRY" | null;
  remainingQuestions: number | null;
  resumeState: ContinueStudyingResumeState | null;
};

export type TodayFocusResponse = {
  type: TodayFocusType;
  studyPackId: string | null;
  noteId: string | null;
  title: string;
  message: string;
  actionLabel: string;
};

export type PostSessionNextStepResponse = {
  type: Extract<TodayFocusType, "PRACTICE_WEAK_CONCEPT" | "RETRY_REVIEW" | "REVIEW_PACK">;
  studyPackId: string;
  noteId: string | null;
  title: string;
  message: string;
  actionLabel: string;
  actionHref: string;
  concepts: string[];
  adaptivePracticeAvailable: boolean;
  adaptivePracticeRemaining: number | null;
  goalNudge: GoalNudgeResponse | null;
};

export type GoalNudgeResponse = {
  studyGoal: string;
  goalType: "EXAM" | "SUBJECT";
  goalName: string;
  goalLabel: string;
  masteryPercentage: number;
  dueConcepts: number;
  weakestGoalSubject: string | null;
};

export type SubjectProgressEntry = {
  subject: string;
  totalConcepts: number;
  masteredConcepts: number;
  dueConcepts: number;
  notPracticedConcepts: number;
  masteryPercentage: number;
};

export type GoalSummaryResponse = {
  studyGoal: string;
  goalType: "EXAM" | "SUBJECT" | "SUBJECT_FOCUS";
  goalName: string;
  goalLabel: string;
  masteryPercentage: number;
  masteredConcepts: number;
  totalConcepts: number;
  notPracticedConcepts: number;
  weakestGoalSubject: string | null;
};

export type ProgressReportResponse = {
  subjects: SubjectProgressEntry[];
  goalSummary?: GoalSummaryResponse | null;
  userCoursePrograms?: string[] | null;
  profileType?: ProfileType | null;
};

export type DashboardConceptInsightResponse = {
  conceptName: string;
  accuracyPercentage: number;
};

export type DashboardPerformanceSummaryResponse = {
  averageQuizScore: number | null;
  totalQuizzesTaken: number;
  studyPacksCreated: number;
  strongestConcept: DashboardConceptInsightResponse | null;
  weakestConcept: DashboardConceptInsightResponse | null;
};

export type DashboardFocusAreasResponse = {
  concepts: DashboardConceptInsightResponse[];
  practiceNoteId: string | null;
  adaptivePracticeAvailable: boolean;
};

export type DashboardWeeklyActivityResponse = {
  studyPacksCreated: number;
  quizzesTaken: number;
  adaptiveSessions: number;
  studyDays: number;
};

export type DashboardOverviewResponse = {
  performanceSummary: DashboardPerformanceSummaryResponse;
  focusAreas: DashboardFocusAreasResponse;
  weeklyActivity: DashboardWeeklyActivityResponse;
};

export type ProfileType = "STUDENT" | "BOARD_EXAM" | "TEACHER" | "PARENT" | "PROFESSIONAL";
export type NoteTargetProfileType = "STUDENT" | "BOARD_TAKER" | "PROFESSIONAL";
export type PaidPlanType = "PLUS" | "PRO";
export type LearnerLevel =
  | "GRADE_SCHOOL"
  | "JUNIOR_HIGH"
  | "SENIOR_HIGH"
  | "COLLEGE"
  | "BOARD_EXAM_REVIEW"
  | "PROFESSIONAL"
  | "PERSONAL_LEARNING";
export type PlanType = "FREE" | PaidPlanType;
export type TeacherQuizQuestionCount = 10 | 20 | 30;
export type BillingCycle = "MONTHLY" | "YEARLY" | "EXAM_CYCLE";
export type UserRole = "USER" | "ADMIN";
export type EngagementMode = "FOCUSED" | "CONSISTENCY" | "STREAK";
export type SubscriptionCancellationReason =
  | "TOO_EXPENSIVE"
  | "NOT_USING_ENOUGH"
  | "MISSING_FEATURES"
  | "TECHNICAL_ISSUES"
  | "FOUND_ANOTHER_TOOL"
  | "JUST_TRYING_IT_OUT"
  | "OTHER";

export type SubscriptionPlanStatusResponse = {
  cancelAtPeriodEnd: boolean;
  premiumEndsAt: string | null;
  cancelledAt: string | null;
};

export type BillingPricingCycleResponse = {
  amount: number | null;
  durationDays: number | null;
  introAmount: number | null;
  introEligible: boolean;
  available: boolean;
};

export type BillingPlanPricingResponse = {
  planType: PaidPlanType;
  monthly: BillingPricingCycleResponse;
  yearly: BillingPricingCycleResponse;
  examCycle: BillingPricingCycleResponse;
};

export type BillingPricingResponse = {
  region: string;
  currency: string;
  plus: BillingPlanPricingResponse;
  pro: BillingPlanPricingResponse;
};

export type AnalyticsEventType =
  | "NOTE_CREATED"
  | "STUDY_PACK_GENERATED"
  | "QUICK_REVIEW_STARTED"
  | "QUICK_REVIEW_COMPLETED"
  | "CHALLENGE_QUIZ_STARTED"
  | "CHALLENGE_QUIZ_COMPLETED"
  | "BOARD_EXAM_STARTED"
  | "LONG_EXAM_STARTED"
  | "LONG_EXAM_COMPLETED"
  | "LONG_EXAM_FORFEITED"
  | "ADAPTIVE_PRACTICE_STARTED"
  | "ADAPTIVE_PRACTICE_COMPLETED"
  | "INTERVIEW_PRACTICE_STARTED"
  | "INTERVIEW_PRACTICE_COMPLETED"
  | "INTERVIEW_PRACTICE_FORFEITED"
  | "INTERVIEW_PRACTICE_QUOTA_EXHAUSTED"
  | "PAYWALL_VIEWED"
  | "PAYWALL_DISMISSED"
  | "FEATURE_LOCKED_CLICKED"
  | "UPGRADE_CLICKED"
  | "SUBSCRIPTION_STARTED"
  | "PUBLIC_NOTE_VIEWED"
  | "PUBLIC_NOTE_COPIED"
  | "PUBLIC_NOTE_COPY_CLICKED"
  | "PUBLIC_NOTE_QUIZ_YOURSELF_CLICKED"
  | "PUBLIC_NOTE_SHARED"
  | "PUBLIC_PROFILE_SHARED"
  | "EXAM_HUB_VIEWED"
  | "EXAM_HUB_CTA_CLICKED"
  | "STUDY_GOAL_SET"
  | "STUDY_GOAL_DISMISSED"
  | "GOAL_NUDGE_SHOWN"
  | "GOAL_NUDGE_CTA_CLICKED"
  | "DASHBOARD_GOAL_CARD_VIEWED"
  | "DASHBOARD_GOAL_CARD_CTA_CLICKED"
  | "COPY_ON_SIGNUP_COMPLETED"
  | "QUIZ_SHARE_LINK_CREATED"
  | "QUIZ_SHARE_LINK_TOGGLED"
  | "QUIZ_SHARE_LINK_OPENED"
  | "LOGIN"
  | "SIGNUP"
  | "LANDING_PAGE_VIEWED"
  | "LANDING_CTA_CLICKED"
  | "GUARDIAN_INTEREST"
  | "DEMO_OPENED"
  | "SIGNUP_STARTED"
  | "SIGNUP_COMPLETED"
  | "EMAIL_VERIFICATION_SENT"
  | "EMAIL_VERIFIED"
  | "ONBOARDING_V2_STARTED"
  | "ONBOARDING_V2_STEP_VIEWED"
  | "ONBOARDING_V2_PROFILE_SELECTED"
  | "ONBOARDING_V2_GOAL_SELECTED"
  | "ONBOARDING_V2_EXAM_DATE_SET"
  | "ONBOARDING_V2_INPUT_METHOD_SELECTED"
  | "ONBOARDING_V2_TOPIC_SUBMITTED"
  | "ONBOARDING_V2_OWN_NOTE_SUBMITTED"
  | "ONBOARDING_V2_STUDY_PACK_GENERATED"
  | "ONBOARDING_V2_STUDY_PACK_ERROR"
  | "ONBOARDING_V2_COMPLETED"
  | "ONBOARDING_V2_CTA_CONTINUE_STUDYING"
  | "ONBOARDING_V2_CTA_GO_TO_DASHBOARD"
  | "ONBOARDING_V2_CTA_GO_TO_SAVED_NOTE"
  | "ONBOARDING_V2_BACK_NAVIGATED"
  | "ONBOARDING_V2_ABANDONED";

export type AnalyticsEventRequest = {
  eventType: AnalyticsEventType;
  entityId?: string | null;
  metadata?: Record<string, unknown> | null;
};

export type AdminAnalyticsSummaryResponse = {
  landingPageViews: number;
  landingCtaClicks: number;
  publicNoteViews: number;
  publicNoteCopyClicks: number;
  signupsStarted: number;
  signupsCompleted: number;
  emailVerificationsCompleted: number;
  totalUsers: number;
  totalNotes: number;
  totalStudyPacksGenerated: number;
  totalChallengeQuizzes: number;
  totalAdaptiveQuizzes: number;
  totalUpgrades: number;
};

export type AdminDashboardSummaryResponse = {
  overview: {
    totalUsers: number;
    verifiedUsers: number;
    premiumUsers: number;
    premiumWaitlistCount: number;
    totalNotes: number;
    totalStudyPacksGenerated: number;
    totalPublicNotes: number;
    totalPublicNoteViews: number;
    totalPublicNoteCopies: number;
    totalUpgrades: number;
  };
  billing: {
    activePremiumSubscriptions: number;
    monthlySubscriptions: number;
    yearlySubscriptions: number;
    cancelAtPeriodEndSubscriptions: number;
    failedPayments: number;
    estimatedMrr: number;
    estimatedArr: number;
  };
  engagement: {
    studyPacksGeneratedThisWeek: number;
    quickReviewsStarted: number;
    challengeQuizzesStarted: number;
    adaptivePracticeStarted: number;
    longExamsStarted: number;
    interviewPracticeStarted: number;
    paywallViews: number;
    upgradeClicks: number;
    signups: number;
    verifiedAccounts: number;
  };
};

export type AdminPublicNoteMetricItemResponse = {
  noteId: string;
  title: string | null;
  subject: string | null;
  totalCount: number;
};

export type AdminSubjectMetricItemResponse = {
  subject: string | null;
  studyPackCount: number;
};

export type AdminRecentUpgradeItemResponse = {
  subscriptionId: string;
  userEmail: string;
  billingCycle: "MONTHLY" | "YEARLY";
  provider: "NONE" | "INTERNAL_MIGRATION" | "XENDIT";
  transactionId: string | null;
  amount: number | null;
  currency: string | null;
  cancelAtPeriodEnd: boolean;
  startedAt: string;
};

export type AdminRecentFailedPaymentItemResponse = {
  transactionId: string;
  userEmail: string;
  amount: number;
  currency: string;
  provider: "NONE" | "INTERNAL_MIGRATION" | "XENDIT";
  createdAt: string;
};

export type AdminIssueRefundResponse = {
  transactionId: string;
  userEmail: string;
  amount: number;
  currency: string;
};

export type AdminRecentFeedbackItemResponse = {
  feedbackId: string;
  userEmail: string;
  message: string;
  pageUrl: string | null;
  status: "NEW" | "REVIEWED" | "CLOSED";
  createdAt: string;
};

export type AdminDashboardTopContentResponse = {
  mostViewedPublicNotes: AdminPublicNoteMetricItemResponse[];
  mostCopiedPublicNotes: AdminPublicNoteMetricItemResponse[];
  topSubjectsByStudyPackGeneration: AdminSubjectMetricItemResponse[];
};

export type AdminDashboardRecentEventsResponse = {
  recentPremiumUpgrades: AdminRecentUpgradeItemResponse[];
  recentFailedPayments: AdminRecentFailedPaymentItemResponse[];
  recentFeedback: AdminRecentFeedbackItemResponse[];
};

export type AdminFunnelMetricsResponse = {
  activation: {
    totalVerifiedUsers: number;
    activatedUsers: number;
    activationRatePercent: number;
    medianDaysToFirstPack: number | null;
  };
  stuckUsers: {
    stuckUsersCount: number;
  };
  quotaHit: {
    freeUsersHitQuota: number;
    totalFreeUsers: number;
    ratePercent: number;
  };
  paywallConversion: {
    usersSeenPaywall: number;
    usersUpgradedAfterPaywall: number;
    ratePercent: number;
  };
  valueLoop: {
    usersGeneratedPack: number;
    usersStartedQuizWithin7Days: number;
    ratePercent: number;
  };
};

export type AdminRegenerateSummariesResponse = {
  queued: number;
  skipped: number;
};

export type AdminRegenerationStatusResponse = {
  total: number;
  processed: number;
  failed: number;
  done: boolean;
};

export type SubmitFeedbackRequest = {
  message: string;
};

export type SignupRequest = {
  email: string;
  password: string;
  firstName: string;
  displayName?: string;
};

export type LoginRequest = {
  email: string;
  password: string;
  keepSignedIn?: boolean;
};

export type GoogleAuthRequest = {
  code: string;
  keepSignedIn?: boolean;
};

export type GoogleConnectRequest = {
  code: string;
};

export type AuthResponse = {
  userId: string;
  email: string;
  displayName: string | null;
  profileType: ProfileType | null;
  emailVerifiedAt: string | null;
  onboardingCompletedAt: string | null;
  productOnboardingCompletedAt: string | null;
  themePreference?: ThemePreference | null;
  role: UserRole;
  planType: PlanType;
  token: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
};

export type MeResponse = {
  id: string;
  email: string;
  pendingEmail: string | null;
  firstName: string;
  lastName: string | null;
  displayName: string | null;
  username?: string | null;
  bio: string | null;
  learnerLevel: LearnerLevel | null;
  courseProgram: string | null;
  studyGoal?: string | null;
  focusSubjects?: string[] | null;
  schoolName: string | null;
  publicProfileVisible: boolean;
  countryCode: string | null;
  profileType: ProfileType | null;
  examDate: string | null;
  engagementMode: EngagementMode;
  inactivityRemindersEnabled: boolean;
  weakConceptRemindersEnabled: boolean;
  themePreference?: ThemePreference | null;
  emailVerifiedAt: string | null;
  onboardingCompletedAt: string | null;
  productOnboardingCompletedAt: string | null;
  studyPackCount: number;
  role: UserRole;
  status: "ACTIVE" | "SUSPENDED";
  planType: PlanType;
  subscription: SubscriptionPlanStatusResponse;
};

export type SignInMethodsResponse = {
  email: string;
  passwordEnabled: boolean;
  googleConnected: boolean;
  googleEmail: string | null;
};

export type UpdateEngagementModeRequest = {
  engagementMode: EngagementMode;
};

export type UpdateStudyRemindersRequest = {
  inactivityRemindersEnabled: boolean;
  weakConceptRemindersEnabled: boolean;
};

export type UpdateThemePreferenceRequest = {
  themePreference: ThemePreference;
};

export type UpdateUserProfileRequest = {
  firstName?: string;
  lastName?: string;
  displayName?: string;
  username?: string;
  bio?: string;
  learnerLevel?: LearnerLevel | null;
  courseProgram?: string;
  schoolName?: string;
  email?: string;
};

export type UpdatePublicProfileVisibilityRequest = {
  publicProfileVisible: boolean;
};

export type CancelPremiumSubscriptionRequest = {
  reason?: SubscriptionCancellationReason | null;
  feedback?: string | null;
};

export type StudyEngagementResponse = {
  engagementMode: EngagementMode;
  currentStreak: number;
  longestStreak: number;
  studyDaysThisWeek: number;
};

export type MasterySnapshotResponse = {
  averageRecentScore: number | null;
  bestRecentScore: number | null;
  studyPacksReviewed: number;
};

export type OnboardingProfileTypeRequest = {
  profileType: ProfileType;
};

export type CompleteOnboardingRequest = {
  profileType: ProfileType;
  examDate: string | null;
};

export type SimpleMessageResponse = {
  message: string;
};

export type NeedsTextConfirmationResponse = {
  status: "needs_text_confirmation";
  id: string;
  extractedText: string;
  meta: {
    ocrConfidence: number | null;
    latencyMs: number | null;
  };
};

export type StudyPackApiResponse = StudyPackResponse | NeedsTextConfirmationResponse;
export type QuickReviewActivityType =
  | "STARTED_QUICK_REVIEW"
  | "COMPLETED_QUICK_REVIEW"
  | "COMPLETED_ADAPTIVE_QUIZ";

export type QuickReviewSessionStartResponse = {
  sessionId: string | null;
  status: "IN_PROGRESS" | "COMPLETED" | "FORFEITED" | null;
  currentQuestionIndex: number;
  currentRound: "INITIAL" | "RETRY" | null;
  retryCount: number;
  sessionState: Record<string, unknown> | null;
};

export type QuickReviewSessionCompleteRequest = {
  correctAnswers: number;
  totalQuestions: number;
  retryCount: number;
  durationSeconds?: number;
  sessionMetadata?: {
    weakConcepts?: string[];
    [key: string]: unknown;
  };
};

export type QuickReviewConfidenceLevel = "HIGH" | "MEDIUM" | "LOW";

export type QuickReviewSessionProgressRequest = {
  currentQuestionIndex: number;
  currentRound: "INITIAL" | "RETRY";
  retryCount: number;
  sessionState?: Record<string, unknown>;
};

export type QuickReviewSessionSummaryResponse = {
  id: string;
  studyPackId: string;
  status?: "IN_PROGRESS" | "COMPLETED" | "FORFEITED";
  currentQuestionIndex?: number;
  currentRound?: "INITIAL" | "RETRY";
  totalQuestions: number;
  correctAnswers: number;
  scorePercentage: number;
  retryCount: number;
  durationSeconds: number | null;
  confidenceLevel?: QuickReviewConfidenceLevel | null;
  weakConcepts?: string[];
  sessionState?: Record<string, unknown> | null;
  createdAt: string;
  completedAt: string | null;
};

export type QuickReviewPerformanceSummaryResponse = {
  bestScorePercentage: number | null;
  attempts: number;
  lastScorePercentage: number | null;
  lastReviewedAt: string | null;
};

export type QuickReviewIncorrectQuestionInput = {
  question: string;
  correctAnswer: string;
  explanation: string;
};

export type QuickReviewStudyTipRequest = {
  incorrectQuestions: QuickReviewIncorrectQuestionInput[];
};

export type QuickReviewStudyTipResponse = {
  studyTip: string | null;
};

export type QuizSessionStatus = "GENERATING" | "FAILED" | "IN_PROGRESS" | "PAUSED" | "COMPLETED" | "FORFEITED";

export type QuickReviewAdaptiveQuizResponse = {
  sessionId: string | null;
  status: QuizSessionStatus | null;
  studyPackId: string;
  title: string;
  weakConcepts: string[];
  quiz: QuizItem[];
  message: string;
};

export type AdaptivePracticeCompleteRequest = {
  correctAnswers: number;
  totalQuestions: number;
  durationSeconds?: number;
  correctConceptNames?: string[];
};

export type ConceptHealthEntry = {
  concept: string;
  lastCorrectAt: string | null;
  isDue: boolean;
  daysSinceReview: number | null;
};

export type ChallengeQuizMode = "challenge" | "board_exam";
export type QuizSessionMode = "QUICK_REVIEW" | "CHALLENGE" | "ADAPTIVE" | "LONG_EXAM";

export type QuizSessionHistoryMode =
  | QuizSessionMode
  | "BOARD_EXAM"
  | "INTERVIEW_PRACTICE";

export type NotePerformanceSummaryResponse = {
  noteId: string;
  noteTitle: string | null;
  bestScore: number;
  averageScore: number | null;
  attemptCount: number;
  lastAttemptedAt: string;
  bestSessionId: string;
  bestSessionMode: Extract<QuizSessionMode, "QUICK_REVIEW" | "CHALLENGE">;
};

/**
 * LEARNING — session is scored and persisted as a performance record (default).
 * PREVIEW  — quiz runs normally but the backend does not record a scored session.
 *            Default for Creator mode (Teacher) so reviewing quiz material does not
 *            pollute performance history. The user can explicitly choose LEARNING to
 *            take the quiz as a learner.
 */
export type QuizStartSessionMode = "LEARNING" | "PREVIEW";

export type ChallengeQuizStartRequest = {
  difficulty?: "easy" | "medium" | "hard";
  mode?: ChallengeQuizMode;
  sessionMode?: QuizStartSessionMode;
  additionalStudyPackIds?: string[];
};

export type ChallengeQuizStartResponse = {
  sessionId: string | null;
  status: QuizSessionStatus | null;
  studyPackId: string;
  title: string;
  totalQuestions: number;
  timeLimitSeconds: number;
  usedThisMonth: number;
  monthlyLimit: number;
  boardExamUsedThisMonth: number;
  boardExamMonthlyLimit: number;
  difficultySelectionAvailable: boolean;
  mode: ChallengeQuizMode;
  selectedDifficulty: "easy" | "medium" | "hard" | "mixed";
  quiz: QuizItem[];
  currentQuestionIndex: number;
  sessionState: Record<string, unknown> | null;
  sourceNoteRefs?: LongExamSourceNoteRef[] | null;
};

export type ChallengeQuizProgressRequest = {
  currentQuestionIndex: number;
  sessionState?: Record<string, unknown>;
};

export type ChallengeQuizCompleteRequest = {
  correctAnswers: number;
  totalQuestions: number;
  durationSeconds: number;
};

export type ChallengeQuizSessionResponse = {
  sessionId: string;
  studyPackId: string;
  status: QuizSessionStatus;
  totalQuestions: number;
  correctAnswers: number;
  scorePercentage: number;
  performanceLevel: "Excellent" | "Good" | "Fair" | "Needs Improvement";
  conceptBreakdown: {
    concept: string;
    correctAnswers: number;
    totalQuestions: number;
    accuracyPercentage: number;
  }[];
  weakConcepts: string[];
  durationSeconds: number | null;
  createdAt: string;
  completedAt: string | null;
};

export type GenerateMoreChallengeQuizResponse = {
  newQuestions: QuizItem[];
  totalQuestions: number;
  timeLimitSeconds: number;
  timerStartedAtEpochSeconds: number;
};

export type ChallengeQuizSessionSummaryResponse = {
  sessionId: string;
  totalQuestions: number;
  correctAnswers: number;
  scorePercentage: number;
  performanceLevel: "Excellent" | "Good" | "Fair" | "Needs Improvement";
  conceptBreakdown: {
    concept: string;
    correctAnswers: number;
    totalQuestions: number;
    accuracyPercentage: number;
  }[];
  weakConcepts: string[];
  createdAt: string;
  completedAt: string | null;
};

export type RecentQuizSessionHistoryResponse = {
  sessionId: string;
  sessionMode: QuizSessionHistoryMode;
  totalQuestions: number;
  correctAnswers: number;
  scorePercentage: number;
  retryCount: number;
  performanceLevel: string | null;
  weakConcepts: string[];
  participatingNoteCount: number;
  createdAt: string;
  completedAt: string | null;
};

export type QuizSessionReviewResponse = {
  sessionId: string;
  studyPackId: string;
  sessionMode: QuizSessionHistoryMode;
  status: QuizSessionStatus;
  totalQuestions: number;
  correctAnswers: number;
  scorePercentage: number;
  retryCount: number;
  durationSeconds: number | null;
  weakConcepts: string[];
  conceptBreakdown: {
    concept: string;
    correctAnswers: number;
    totalQuestions: number;
    accuracyPercentage: number;
  }[];
  quiz: QuizItem[];
  selectedChoices: Record<string, number>;
  selectedMultiChoices?: Record<string, number[]>;
  createdAt: string;
  completedAt: string | null;
};

export type LongExamStartResponse = {
  sessionId: string | null;
  status: QuizSessionStatus | null;
  quiz: QuizItem[];
  totalQuestions: number;
  difficulty: string | null;
  canResume: boolean;
  timeLimitSeconds: number;
  timerStartedAtEpochSeconds: number;
  sourceNoteRefs: LongExamSourceNoteRef[];
  usedThisMonth: number;
  monthlyLimit: number;
};

export type LongExamSessionResponse = {
  sessionId: string;
  status: QuizSessionStatus;
  quiz: QuizItem[];
  selectedChoices: Record<string, number>;
  selectedMultiChoices?: Record<string, number[]>;
  currentQuestionIndex: number;
  totalQuestions: number;
  difficulty: string | null;
  paused: boolean;
  timeLimitSeconds: number;
  timerStartedAtEpochSeconds: number;
  sourceNoteRefs: LongExamSourceNoteRef[];
};

export type LongExamSourceNoteRef = {
  studyPackId: string;
  noteId: string;
  noteTitle: string;
  questionCount: number;
};

export type LongExamSourceNote = {
  noteId: string;
  noteTitle: string;
};

export type LongExamMasteryReportResponse = {
  sessionId: string;
  totalQuestions: number;
  answeredQuestions: number;
  scorePercentage: number;
  domainBreakdown: Array<{
    domain: string;
    totalQuestions: number;
    correctAnswers: number;
    accuracyPercentage: number;
  }>;
  weakDomains: string[];
  performanceSummary: string;
  suggestedNextStep: string;
  sourceNotes: LongExamSourceNote[];
};

export type InterviewPracticeStartResponse = {
  sessionId: string;
  status: QuizSessionStatus;
  noteId: string;
  studyPackId: string;
  questionCount: number;
  currentQuestionIndex: number;
  softTimerSeconds: number;
  question: QuizItem | null;
  sourceNoteRefs?: Array<{
    noteId: string;
    noteTitle: string | null;
    studyPackId: string;
    questionCount: number;
  }>;
};

export type InterviewPracticeAnswerResponse = {
  verdict: "STRONG" | "WORKABLE" | "RECONSIDER";
  rationale: string;
  followUp: string;
  nextQuestion: QuizItem | null;
};

export type InterviewReadinessReportResponse = {
  sessionId: string;
  totalQuestions: number;
  correctAnswers: number;
  scorePercentage: number;
  band: "READY" | "ALMOST_READY" | "NEEDS_PRACTICE";
  strengths: string[];
  gaps: Array<{
    concept: string;
    noteId: string;
  }>;
  talkingPoints: string[];
  pacingNotes: number[];
};

export type ChallengeQuizPerformanceSummaryResponse = {
  bestScorePercentage: number | null;
  attempts: number;
  lastScorePercentage: number | null;
  lastCompletedAt: string | null;
  latestPerformanceLevel: "Excellent" | "Good" | "Fair" | "Needs Improvement" | null;
  latestWeakConcepts: string[];
};

export type ShareLinkResponse = {
  token: string;
  shareUrl: string;
};

export type PublicShareResponse = {
  id: string;
  title: string;
  summary: string;
  keyConcepts: string[];
  quiz: QuizItem[];
};

export type ShareRemixResponse = {
  studyPackId: string;
  noteId: string | null;
};

export type BillingCheckoutSessionResponse = {
  checkoutUrl: string;
};

export type BillingCheckoutSessionRequest = {
  planType?: PaidPlanType | null;
  billingCycle?: BillingCycle | null;
  returnUrl?: string | null;
};

export type BillingUsageSummaryResponse = {
  planType: PlanType;
  studyPacksUsed: number;
  studyPacksLimit: number;
  challengeQuizUsed: number;
  challengeQuizLimit: number;
  adaptivePracticeUsed: number;
  adaptivePracticeLimit: number;
  interviewPracticeUsed: number;
  interviewPracticeLimit: number;
  longExamUsed: number;
  longExamLimit: number;
  boardExamUsed: number;
  boardExamLimit: number;
  adaptivePracticeAvailable: boolean;
  interviewPracticeAvailable: boolean;
  difficultySelectionAvailable: boolean;
};

export type BillingHistoryItemResponse = {
  id: string;
  date: string;
  description: string;
  amount: number;
  currency: string;
  status: "PENDING" | "SUCCESS" | "FAILED" | "REFUNDED";
  provider: "NONE" | "XENDIT";
  providerReferenceId: string;
};

export type BillingHistoryResponse = {
  currentPlan: PlanType;
  subscriptionStatus: "ACTIVE" | "CANCELED" | "EXPIRED" | null;
  billingType: BillingCycle | null;
  currentPeriodStart: string | null;
  currentPeriodEnd: string | null;
  cancelAtPeriodEnd: boolean;
  cancellationEffectiveAt: string | null;
  transactions: BillingHistoryItemResponse[];
};

export type UpdateStudyPackMetadataRequest = {
  title: string;
  subject?: string | null;
};

export type UpsertNoteRequest = {
  title?: string | null;
  subject?: string | null;
  courseProgram?: string | null;
  tags?: string[];
  targetProfileType?: NoteTargetProfileType | null;
  content: string;
};

export type GenerateNoteFromTopicResponse = {
  content: string;
};

export type NoteResponse = {
  id: string;
  title: string | null;
  subject: string | null;
  courseProgram?: string | null;
  targetProfileType: NoteTargetProfileType;
  tags: string[];
  content: string;
  visibility: NoteVisibility;
  createdAt: string;
  updatedAt: string;
  copiedFromNoteId: string | null;
  copiedFromUserId: string | null;
  copiedFromTitle: string | null;
  copiedFromPublic: boolean;
  copiedAt: string | null;
  studyPackId?: string | null;
  studyPackStatus?: NoteStudyPackStatus;
  summary: string | null;
  keyConcepts: string[];
  quiz: QuizItem[];
  generatedQuiz: GeneratedQuizResponse | null;
  lastUsedTargetLearnerLevel: LearnerLevel | null;
  quizCount: number;
  quickReviewAvailable: boolean;
  challengeQuizAvailable: boolean;
  adaptivePracticeAvailable: boolean;
  difficultySelectionAvailable: boolean;
};

export type NoteStudyPackStatus = "DRAFT" | "GENERATING" | "FAILED" | "STUDY_PACK_READY";
export type NoteVisibility = "PRIVATE" | "PUBLIC";
export type SubjectSuggestionScope = "mine" | "public";
export type CourseProgramSuggestionScope = "mine" | "public";

export type NoteListItemResponse = {
  id: string;
  ownerUserId: string | null;
  title: string | null;
  courseProgram: string | null;
  targetProfileType: NoteTargetProfileType;
  subject: string | null;
  tags: string[];
  contentPreview: string;
  summaryPreview: string;
  visibility: NoteVisibility;
  studyPackId: string | null;
  studyPackStatus: NoteStudyPackStatus;
  quizCount: number | null;
  copyCount: number | null;
  likeCount: number | null;
  shareCount: number | null;
  viewCount: number | null;
  authorDisplayName: string;
  authorUsername?: string | null;
  isOfficialAuthor: boolean;
  isCurrentUser?: boolean;
  createdAt: string;
  updatedAt: string;
  lastSessionCompletedAt?: string | null;
  generatedQuizId?: string | null;
  generatedQuizGeneratedAt?: string | null;
  generatedQuizQuestionCount?: number | null;
  copiedFromNoteId?: string | null;
  copiedFromPublic?: boolean;
  likedByCurrentUser: boolean;
};

export type PublicNoteListResponse = {
  items: NoteListItemResponse[];
  total: number;
};

export type SavedLibraryFilterState = {
  search?: string;
  subject?: string;
  courseProgram?: string;
  tags?: string[];
  status?: string;
  visibility?: string;
  sort?: string;
};

export type SavedLibraryFilterResponse = {
  id: string;
  name: string;
  filterState: SavedLibraryFilterState;
  createdAt: string;
};

export type MultiNoteQuizDocxExportRequest = {
  sections: Array<{
    title: string;
    questionRefs: Array<{
      noteId: string;
      questionIndex: number;
    }>;
  }>;
  includeAnswerKey: boolean;
  includeExplanations: boolean;
  headerOverride?: QuizDocxHeaderOverride | null;
  versionCount?: number | null;
};

export type QuizDocxHeaderOverride = {
  className?: string | null;
  includeDate?: boolean | null;
};

export type PublicNoteLikeResponse = {
  liked: boolean;
  likeCount: number;
};

export type PublicNoteDetailResponse = {
  id: string;
  ownerUserId: string | null;
  title: string | null;
  subject: string | null;
  tags: string[];
  content: string;
  contentPreview: string;
  studyPackStatus: NoteStudyPackStatus;
  summary: string | null;
  keyConcepts: string[];
  quiz: QuizItem[];
  authorDisplayName: string;
  authorUsername?: string | null;
  isOfficialAuthor: boolean;
  isCurrentUser: boolean;
  updatedAt: string;
};

export type PublicProfileNoteResponse = {
  noteId: string;
  title: string | null;
  courseProgram: string | null;
  subject: string | null;
  tags: string[];
  contentPreview: string;
  summaryPreview: string;
  copyCount: number;
  shareCount: number;
  viewCount: number;
  slug: string;
};

export type SubjectCountResponse = {
  subject: string;
  count: number;
};

export type PublicProfileResponse = {
  displayName: string;
  username?: string | null;
  bio: string | null;
  learnerLevel: LearnerLevel | null;
  courseProgram: string | null;
  profileType: ProfileType | null;
  isOfficial: boolean;
  publicProfileVisible: boolean;
  isCurrentUser: boolean;
  userId: string;
  publicNotesCount: number;
  totalCopies: number;
  totalShares: number;
  totalViews: number;
  totalProfileShares: number;
  notesBySubject: SubjectCountResponse[];
  totalPublicSubjectCount: number;
  publicNotes: PublicProfileNoteResponse[];
};

type ApiErrorPayload = {
  error?: {
    code?: string;
    message?: string;
    details?: string;
    action?: string;
  };
};

export class ApiRequestError extends Error {
  readonly code: string | null;
  readonly action: string | null;
  readonly status: number;

  constructor(message: string, options: { code?: string | null; action?: string | null; status: number }) {
    super(message);
    this.name = "ApiRequestError";
    this.code = options.code ?? null;
    this.action = options.action ?? null;
    this.status = options.status;
  }
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

function buildUrl(path: string) {
  return `${API_BASE_URL}${path}`;
}

function buildAuthHeaders(contentType?: string): HeadersInit {
  const headers: Record<string, string> = {};
  if (contentType) {
    headers["Content-Type"] = contentType;
  }
  const accessToken = getAccessToken();
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  return headers;
}

export function isNeedsTextConfirmationResponse(
  payload: StudyPackApiResponse,
): payload is NeedsTextConfirmationResponse {
  return "status" in payload && payload.status === "needs_text_confirmation";
}

async function parseApiResponse<T>(
  response: Response,
  fallbackMessage = "Request failed. Please try again.",
): Promise<T> {
  if (response.ok) {
    return (await response.json()) as T;
  }

  let errorPayload: ApiErrorPayload | null = null;
  try {
    errorPayload = (await response.json()) as ApiErrorPayload;
  } catch {
    // Ignore JSON parse failures and use fallback below.
  }

  const message = errorPayload?.error?.message ?? fallbackMessage;
  throw new ApiRequestError(message, {
    code: errorPayload?.error?.code ?? null,
    action: errorPayload?.error?.action ?? null,
    status: response.status,
  });
}

async function throwApiRequestError(
  response: Response,
  fallbackMessage = "Request failed. Please try again.",
): Promise<never> {
  let errorPayload: ApiErrorPayload | null = null;
  try {
    errorPayload = (await response.json()) as ApiErrorPayload;
  } catch {
    // Ignore JSON parse failures and use fallback below.
  }

  const message = errorPayload?.error?.message ?? fallbackMessage;
  throw new ApiRequestError(message, {
    code: errorPayload?.error?.code ?? null,
    action: errorPayload?.error?.action ?? null,
    status: response.status,
  });
}

function extractDownloadFilename(contentDisposition: string | null, fallback: string): string {
  if (!contentDisposition) {
    return fallback;
  }
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1]);
  }
  const basicMatch = contentDisposition.match(/filename=\"?([^\";]+)\"?/i);
  return basicMatch?.[1] ?? fallback;
}

function triggerBlobDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function toAuthUser(payload: AuthResponse): AuthUser {
  return {
    id: payload.userId,
    email: payload.email,
    displayName: payload.displayName,
    profileType: payload.profileType,
    emailVerifiedAt: payload.emailVerifiedAt,
    onboardingCompletedAt: payload.onboardingCompletedAt,
    productOnboardingCompletedAt: payload.productOnboardingCompletedAt,
    themePreference: payload.themePreference,
    role: payload.role,
    planType: payload.planType,
    accessToken: payload.token,
    refreshToken: payload.refreshToken,
    accessTokenExpiresAt: payload.accessTokenExpiresAt,
    refreshTokenExpiresAt: payload.refreshTokenExpiresAt,
  };
}

function syncStoredAuthUserFromMe(me: MeResponse): void {
  const authUser = getAuthUser();
  if (authUser?.id !== me.id) {
    return;
  }

  patchAuthUser({
    email: me.pendingEmail ?? me.email,
    displayName: me.displayName,
    username: me.username,
    profileType: me.profileType,
    emailVerifiedAt: me.emailVerifiedAt,
    onboardingCompletedAt: me.onboardingCompletedAt,
    productOnboardingCompletedAt: me.productOnboardingCompletedAt,
    themePreference: me.themePreference,
  });
}

let refreshPromise: Promise<boolean> | null = null;

async function tryRefreshAccessToken(): Promise<boolean> {
  if (refreshPromise) {
    return refreshPromise;
  }
  refreshPromise = doRefreshAccessToken().finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

async function doRefreshAccessToken(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    clearAuthUser();
    return false;
  }

  const response = await fetch(buildUrl("/auth/refresh"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ refreshToken }),
  });

  if (!response.ok) {
    clearAuthUser();
    return false;
  }

  const payload = (await response.json()) as AuthResponse;
  setAuthUser(toAuthUser(payload));
  return true;
}

async function fetchWithAuth(path: string, init: RequestInit, retry = true): Promise<Response> {
  const response = await fetch(buildUrl(path), init);
  if (response.status !== 401 || !retry) {
    if (response.status === 401) {
      handleUnauthorizedSession();
    }
    return response;
  }
  const refreshed = await tryRefreshAccessToken();
  if (!refreshed) {
    handleUnauthorizedSession();
    return response;
  }
  const updatedHeaders = new Headers(init.headers ?? {});
  const token = getAccessToken();
  if (token) {
    updatedHeaders.set("Authorization", `Bearer ${token}`);
  }
  const retriedResponse = await fetch(buildUrl(path), {
    ...init,
    headers: updatedHeaders,
  });
  if (retriedResponse.status === 401) {
    handleUnauthorizedSession();
  }
  return retriedResponse;
}

export async function signup(request: SignupRequest): Promise<AuthUser> {
  const response = await fetch(buildUrl("/auth/signup"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
  const payload = await parseApiResponse<AuthResponse>(response, "Could not create account. Please try again.");
  return toAuthUser(payload);
}

export async function login(request: LoginRequest): Promise<AuthUser> {
  const response = await fetch(buildUrl("/auth/login"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
  const payload = await parseApiResponse<AuthResponse>(response, "Could not log in. Please try again.");
  return toAuthUser(payload);
}

export async function loginWithGoogle(request: GoogleAuthRequest): Promise<AuthUser> {
  const response = await fetch(buildUrl("/auth/google"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
  const payload = await parseApiResponse<AuthResponse>(response, "Could not continue with Google. Please try again.");
  return toAuthUser(payload);
}

export async function getSignInMethods(): Promise<SignInMethodsResponse> {
  const response = await fetchWithAuth(
    "/auth/sign-in-methods",
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${getAccessToken()}`,
      },
    },
    true,
  );
  return parseApiResponse<SignInMethodsResponse>(response, "Could not load sign-in methods.");
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  const response = await fetchWithAuth(
    "/auth/change-password",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${getAccessToken()}`,
      },
      body: JSON.stringify({ currentPassword, newPassword }),
    },
    true,
  );
  await parseApiResponse<{ message: string }>(response, "Could not change password. Please try again.");
}

export async function connectGoogle(request: GoogleConnectRequest): Promise<SignInMethodsResponse> {
  const response = await fetchWithAuth(
    "/auth/google/connect",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${getAccessToken()}`,
      },
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<SignInMethodsResponse>(response, "Could not connect Google. Please try again.");
}

export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken();
  beginManualLogoutRedirect();
  try {
    if (!refreshToken) {
      return;
    }
    await fetch(buildUrl("/auth/logout"), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ refreshToken }),
    });
  } finally {
    clearAuthUser();
  }
}

export async function getMe(): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/auth/me",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not load profile. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function updateUserProfile(request: UpdateUserProfileRequest): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/users/profile",
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update profile. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function updateProfileLearnerLevel(level: LearnerLevel): Promise<MeResponse> {
  const current = await getMe();
  return updateUserProfile({
    firstName: current.firstName,
    lastName: current.lastName ?? "",
    displayName: current.displayName ?? "",
    username: current.username ?? "",
    bio: current.bio ?? "",
    learnerLevel: level,
    courseProgram: current.courseProgram ?? "",
    schoolName: current.schoolName ?? "",
    email: current.email,
  });
}

export async function updateLearningProfileContext(
  learnerLevel: LearnerLevel | null,
  courseProgram: string | null,
): Promise<MeResponse> {
  const current = await getMe();
  return updateUserProfile({
    firstName: current.firstName,
    lastName: current.lastName ?? "",
    displayName: current.displayName ?? "",
    username: current.username ?? "",
    bio: current.bio ?? "",
    learnerLevel: learnerLevel ?? current.learnerLevel ?? null,
    courseProgram: courseProgram?.trim() || current.courseProgram || "",
    schoolName: current.schoolName ?? "",
    email: current.email,
  });
}

export async function updateExamDate(examDate: string | null): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/users/profile/exam-date",
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ examDate: examDate ?? null }),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update exam date. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function setStudyGoal(studyGoal: string | null): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/users/profile/goal",
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ studyGoal: studyGoal ?? null }),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update study goal. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function setFocusSubjects(subjects: string[]): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/users/profile/focus-subjects",
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ subjects }),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update focus subjects. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function updatePublicProfileVisibility(
  request: UpdatePublicProfileVisibilityRequest,
): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/users/profile/public-visibility",
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update public profile visibility.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function getAdminDashboardSummary(): Promise<AdminDashboardSummaryResponse> {
  const response = await fetchWithAuth(
    "/admin/dashboard/summary",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<AdminDashboardSummaryResponse>(response, "Could not load admin summary.");
}

export async function getAdminDashboardTopContent(): Promise<AdminDashboardTopContentResponse> {
  const response = await fetchWithAuth(
    "/admin/dashboard/top-content",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<AdminDashboardTopContentResponse>(response, "Could not load admin content metrics.");
}

export async function getAdminDashboardRecentEvents(): Promise<AdminDashboardRecentEventsResponse> {
  const response = await fetchWithAuth(
    "/admin/dashboard/recent-events",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<AdminDashboardRecentEventsResponse>(response, "Could not load recent admin events.");
}

export async function getAdminFunnelMetrics(): Promise<AdminFunnelMetricsResponse> {
  const response = await fetchWithAuth(
    "/admin/funnel/metrics",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<AdminFunnelMetricsResponse>(response, "Could not load funnel metrics.");
}

export async function regenerateAdminSummaries(): Promise<AdminRegenerateSummariesResponse> {
  const response = await fetchWithAuth(
    "/admin/study-packs/regenerate-summaries",
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<AdminRegenerateSummariesResponse>(response, "Could not queue regeneration.");
}

export async function getAdminRegenerationStatus(): Promise<AdminRegenerationStatusResponse> {
  const response = await fetchWithAuth(
    "/admin/study-packs/regeneration-status",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<AdminRegenerationStatusResponse>(response, "Could not fetch regeneration status.");
}

export type CampaignStatusResponse = {
  eligibleCount: number;
  totalSent: number;
  lastSentAt: string | null;
};

export type ReEngagementSendResult = {
  sent: number;
  skipped: number;
};

export async function getReEngagementCampaignStatus(): Promise<CampaignStatusResponse> {
  const response = await fetchWithAuth(
    "/admin/campaigns/re-engagement/status",
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<CampaignStatusResponse>(response, "Could not load campaign status.");
}

export async function sendReEngagementCampaign(): Promise<ReEngagementSendResult> {
  const response = await fetchWithAuth(
    "/admin/campaigns/re-engagement/send",
    { method: "POST", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<ReEngagementSendResult>(response, "Could not send campaign.");
}

export async function issueAdminRefund(transactionId: string): Promise<AdminIssueRefundResponse> {
  const response = await fetchWithAuth(
    "/admin/billing/refund",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ transactionId }),
    },
    true,
  );
  return parseApiResponse<AdminIssueRefundResponse>(response, "Could not issue refund.");
}

export async function submitFeedback(
  request: SubmitFeedbackRequest,
  pageUrl: string | null,
): Promise<SimpleMessageResponse> {
  const headers = new Headers(buildAuthHeaders("application/json"));
  if (pageUrl && pageUrl.trim().length > 0) {
    headers.set("X-Page-Url", pageUrl);
  }

  const response = await fetchWithAuth(
    "/feedback",
    {
      method: "POST",
      headers,
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not send feedback. Please try again.");
}

export async function completeOnboardingProfileType(
  request: OnboardingProfileTypeRequest,
): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/auth/onboarding/profile-type",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not complete onboarding. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function completeOnboarding(
  request: CompleteOnboardingRequest,
): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/auth/onboarding",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not complete onboarding. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function completeProductOnboarding(
  skipped = false,
): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/auth/product-onboarding/complete",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ skipped }),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not complete onboarding. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function requestEmailVerification(): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    "/auth/resend-verification",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not send verification email. Please try again.");
}

export async function verifyEmailToken(token: string): Promise<SimpleMessageResponse> {
  const query = new URLSearchParams({ token }).toString();
  const response = await fetch(buildUrl(`/auth/verify-email?${query}`), {
    method: "GET",
  });
  return parseApiResponse<SimpleMessageResponse>(response, "Could not verify email. Please try again.");
}

export async function trackAnalyticsEvent(request: AnalyticsEventRequest): Promise<void> {
  try {
    await fetch(buildUrl("/analytics/events"), {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({
        eventType: request.eventType,
        entityId: request.entityId ?? null,
        metadata: request.metadata ?? {},
      }),
      keepalive: true,
    });
  } catch {
    // Analytics must never interrupt the main product flow.
  }
}

export async function confirmEmailVerification(token: string): Promise<SimpleMessageResponse> {
  return verifyEmailToken(token);
}

export async function requestEmailVerificationLegacy(): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    "/auth/verify-email/request",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not send verification email. Please try again.");
}

export async function forgotPassword(email: string): Promise<SimpleMessageResponse> {
  const response = await fetch(buildUrl("/auth/forgot-password"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email }),
  });
  return parseApiResponse<SimpleMessageResponse>(response, "Could not send reset email. Please try again.");
}

export async function resetPassword(token: string, newPassword: string): Promise<SimpleMessageResponse> {
  const response = await fetch(buildUrl("/auth/reset-password"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token, newPassword }),
  });
  return parseApiResponse<SimpleMessageResponse>(response, "Could not reset password. Please try again.");
}

export async function updateEngagementMode(request: UpdateEngagementModeRequest): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/auth/preferences/engagement-mode",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update engagement mode. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function updateStudyReminders(request: UpdateStudyRemindersRequest): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/auth/preferences/study-reminders",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update study reminders. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function updateThemePreference(request: UpdateThemePreferenceRequest): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/auth/preferences/theme",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update theme preference. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function createStudyPackFromText(notesText: string): Promise<StudyPackResponse> {
  const response = await fetchWithAuth(
    "/study-packs",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ notesText }),
    },
    true,
  );

  const payload = await parseApiResponse<StudyPackApiResponse>(
    response,
    "We could not generate your study pack right now. Please try again.",
  );
  if (isNeedsTextConfirmationResponse(payload)) {
    throw new Error("Unexpected OCR confirmation response for text input.");
  }
  return payload;
}

const AUTO_APPLY_METADATA_QUERY_PARAM = "autoApplyMetadata";

export async function createStudyPackFromNote(
  noteId: string,
  options: { autoApplyMetadata?: boolean } = {},
): Promise<NoteResponse> {
  const query = options.autoApplyMetadata ? `?${AUTO_APPLY_METADATA_QUERY_PARAM}=true` : "";
  const response = await fetchWithAuth(
    `/notes/${noteId}/generate${query}`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NoteResponse>(
    response,
    "We could not generate your study pack right now. Please try again.",
  );
}

export async function generateNoteFromTopic(
  topic: string,
  courseProgram?: string,
): Promise<GenerateNoteFromTopicResponse> {
  const body: Record<string, string> = { topic };
  if (courseProgram && courseProgram.trim().length > 0) {
    body.courseProgram = courseProgram.trim();
  }
  const response = await fetchWithAuth(
    "/notes/generate",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(body),
    },
    true,
  );
  return parseApiResponse<GenerateNoteFromTopicResponse>(
    response,
    "We could not generate a note right now. Please try again.",
  );
}

export async function createStudyPackFromImage(imageFile: File): Promise<StudyPackApiResponse> {
  const formData = new FormData();
  formData.append("image", imageFile);

  const response = await fetchWithAuth(
    "/study-packs",
    {
      method: "POST",
      headers: buildAuthHeaders(),
      body: formData,
    },
    true,
  );
  return parseApiResponse<StudyPackApiResponse>(
    response,
    "We could not generate your study pack right now. Please try again.",
  );
}

export async function extractNoteTextFromFile(file: File): Promise<NoteTextExtractionResponse> {
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetchWithAuth(
    "/notes/extract-text",
    {
      method: "POST",
      headers: buildAuthHeaders(),
      body: formData,
    },
    true,
  );
  return parseApiResponse<NoteTextExtractionResponse>(
    response,
    "We could not import text from this file right now. Please try again.",
  );
}

export async function confirmStudyPackText(
  draftId: string,
  notesText: string,
): Promise<StudyPackResponse> {
  const response = await fetchWithAuth(
    "/study-packs/confirm-text",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ draftId, notesText }),
    },
    true,
  );

  const payload = await parseApiResponse<StudyPackApiResponse>(
    response,
    "We could not generate your study pack right now. Please try again.",
  );
  if (isNeedsTextConfirmationResponse(payload)) {
    throw new Error("Unexpected OCR confirmation response for text confirmation.");
  }
  return payload;
}

export async function listMyStudyPacksPage(
  options: { limit?: number; cursor?: string | null } = {},
): Promise<StudyPackListPageResponse> {
  const queryParams = new URLSearchParams();
  if (options.limit && options.limit > 0) {
    queryParams.set("limit", String(options.limit));
  }
  if (options.cursor) {
    queryParams.set("cursor", options.cursor);
  }

  const path = queryParams.size > 0 ? `/study-packs?${queryParams.toString()}` : "/study-packs";
  const response = await fetchWithAuth(
    path,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<StudyPackListPageResponse>(response, "Could not load your Study Packs.");
}

export async function listMyStudyPacks(): Promise<StudyPackListItemResponse[]> {
  const pageSize = 100;
  const maxPages = 50;
  const items: StudyPackListItemResponse[] = [];

  let cursor: string | null = null;
  let hasMore = true;
  let pagesFetched = 0;

  while (hasMore && pagesFetched < maxPages) {
    const page = await listMyStudyPacksPage({ limit: pageSize, cursor });
    items.push(...page.items);
    hasMore = page.hasMore;
    cursor = page.nextCursor;
    pagesFetched += 1;
    if (!cursor) {
      break;
    }
  }

  return items;
}

export async function getContinueStudyingRecommendation(): Promise<ContinueStudyingResponse> {
  const response = await fetchWithAuth(
    "/dashboard/continue-studying",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ContinueStudyingResponse>(
    response,
    "Could not load continue studying recommendation.",
  );
}

export async function getMyStudyPack(id: string): Promise<StudyPackResponse> {
  const response = await fetchWithAuth(
    `/study-packs/${id}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<StudyPackResponse>(response, "Could not load this Study Pack.");
}

export async function getConceptHealth(studyPackId: string): Promise<ConceptHealthEntry[]> {
  const response = await fetchWithAuth(
    `/study-packs/${studyPackId}/concept-health`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ConceptHealthEntry[]>(response, "Could not load concept review signals.");
}

export async function getPostSessionNextStep(studyPackId: string): Promise<PostSessionNextStepResponse> {
  const response = await fetchWithAuth(
    `/study-packs/${studyPackId}/next-step`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<PostSessionNextStepResponse>(response, "Could not load the next study step.");
}

export async function getGoalSummary(): Promise<GoalNudgeResponse | null> {
  const response = await fetchWithAuth(
    "/me/goal",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  if (!response.ok) {
    await throwApiRequestError(response, "Could not load your study goal.");
  }
  if (response.status === 204) {
    return null;
  }
  const body = await response.text();
  if (!body.trim()) {
    return null;
  }
  return JSON.parse(body) as GoalNudgeResponse | null;
}

export async function getProgressReport(): Promise<ProgressReportResponse> {
  const response = await fetchWithAuth(
    "/me/progress",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ProgressReportResponse>(response, "Could not load your progress report.");
}

export async function updateStudyPackTags(studyPackId: string, tags: string[]): Promise<StudyPackResponse> {
  const response = await fetchWithAuth(
    `/study-packs/${studyPackId}/tags`,
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ tags }),
    },
    true,
  );
  return parseApiResponse<StudyPackResponse>(response, "Could not update tags.");
}

export async function updateStudyPackMetadata(
  studyPackId: string,
  request: UpdateStudyPackMetadataRequest,
): Promise<StudyPackResponse> {
  const response = await fetchWithAuth(
    `/study-packs/${studyPackId}/metadata`,
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<StudyPackResponse>(response, "Could not update Study Pack metadata.");
}

export async function deleteMyStudyPack(id: string): Promise<void> {
  const response = await fetchWithAuth(
    `/study-packs/${id}`,
    {
      method: "DELETE",
      headers: buildAuthHeaders(),
    },
    true,
  );
  if (!response.ok) {
    await parseApiResponse<void>(response, "Could not delete this Study Pack.");
  }
}

export async function createStudyPackShareLink(studyPackId: string): Promise<ShareLinkResponse> {
  const response = await fetchWithAuth(
    `/study-packs/${studyPackId}/share`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ShareLinkResponse>(response, "Could not create a share link.");
}

export async function getPublicSharedStudyPack(token: string): Promise<PublicShareResponse> {
  const response = await fetch(buildUrl(`/p/${token}`), {
    method: "GET",
  });
  return parseApiResponse<PublicShareResponse>(response, "Could not load this shared Study Pack.");
}

export async function remixSharedStudyPack(token: string): Promise<ShareRemixResponse> {
  const response = await fetchWithAuth(
    `/p/${token}/remix`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ShareRemixResponse>(response, "Could not copy this Study Pack.");
}

export async function trackQuickReviewActivity(
  studyPackId: string,
  activityType: QuickReviewActivityType,
): Promise<void> {
  const response = await fetchWithAuth(
    `/study-packs/${studyPackId}/quick-review/activity`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ activityType }),
    },
    true,
  );
  if (!response.ok) {
    await parseApiResponse<void>(response, "Could not track quick review activity.");
  }
}

export async function startQuickReviewSession(
  noteId: string,
): Promise<QuickReviewSessionStartResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/quick-review/start`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuickReviewSessionStartResponse>(response, "Could not start Quick Review.");
}

export async function getInProgressQuickReviewSession(
  noteId: string,
): Promise<QuickReviewSessionStartResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/quick-review/in-progress`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuickReviewSessionStartResponse>(response, "Could not load in-progress Quick Review.");
}

export async function updateQuickReviewSessionProgress(
  sessionId: string,
  request: QuickReviewSessionProgressRequest,
): Promise<QuickReviewSessionSummaryResponse> {
  const response = await fetchWithAuth(
    `/quick-review/${sessionId}/progress`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<QuickReviewSessionSummaryResponse>(
    response,
    "Could not save Quick Review progress.",
  );
}

export async function completeQuickReviewSession(
  sessionId: string,
  request: QuickReviewSessionCompleteRequest,
): Promise<QuickReviewSessionSummaryResponse> {
  const response = await fetchWithAuth(
    `/quick-review/${sessionId}/complete`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<QuickReviewSessionSummaryResponse>(response, "Could not save Quick Review results.");
}

export async function forfeitQuickReviewSession(
  sessionId: string,
): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    `/quick-review/${sessionId}/forfeit`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not leave Quick Review.");
}

export async function saveQuickReviewConfidence(
  sessionId: string,
  confidenceLevel: QuickReviewConfidenceLevel,
): Promise<QuickReviewSessionSummaryResponse> {
  const response = await fetchWithAuth(
    `/quick-review/${sessionId}/confidence`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ confidenceLevel }),
    },
    true,
  );
  return parseApiResponse<QuickReviewSessionSummaryResponse>(
    response,
    "Could not save confidence feedback.",
  );
}

export async function listRecentQuickReviewSessions(
  noteId: string,
  limit = 5,
): Promise<QuickReviewSessionSummaryResponse[]> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/quick-review/recent?limit=${limit}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuickReviewSessionSummaryResponse[]>(
    response,
    "Could not load recent Quick Review sessions.",
  );
}

export async function getQuickReviewPerformanceSummary(
  noteId: string,
): Promise<QuickReviewPerformanceSummaryResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/quick-review/performance-summary`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuickReviewPerformanceSummaryResponse>(
    response,
    "Could not load Quick Review performance summary.",
  );
}

export async function getQuickReviewSessionReview(
  noteId: string,
  sessionId: string,
): Promise<QuizSessionReviewResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/quick-review/sessions/${sessionId}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuizSessionReviewResponse>(
    response,
    "Could not load Quick Review session review.",
  );
}

export async function generateQuickReviewStudyTip(
  noteId: string,
  request: QuickReviewStudyTipRequest,
): Promise<QuickReviewStudyTipResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/quick-review/study-tip`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<QuickReviewStudyTipResponse>(response, "Could not generate study tip.");
}

export async function generateAdaptiveQuickReviewQuiz(
  noteId: string,
): Promise<QuickReviewAdaptiveQuizResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/adaptive-practice/start`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuickReviewAdaptiveQuizResponse>(
    response,
    "Could not generate adaptive practice quiz.",
  );
}

export async function getInProgressAdaptivePracticeSession(
  noteId: string,
): Promise<QuickReviewAdaptiveQuizResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/adaptive-practice/in-progress`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuickReviewAdaptiveQuizResponse>(
    response,
    "Could not load Adaptive Practice session.",
  );
}

export async function completeAdaptivePracticeSession(
  sessionId: string,
  request: AdaptivePracticeCompleteRequest,
): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    `/adaptive-practice/sessions/${sessionId}/complete`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(
    response,
    "Could not complete adaptive practice session.",
  );
}

export async function forfeitAdaptivePracticeSession(
  sessionId: string,
): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    `/adaptive-practice/sessions/${sessionId}/forfeit`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(
    response,
    "Could not leave Adaptive Practice.",
  );
}

export async function startChallengeQuizSession(
  noteId: string,
  request: ChallengeQuizStartRequest = {},
): Promise<ChallengeQuizStartResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/challenge-quiz/start`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<ChallengeQuizStartResponse>(response, "Could not start Challenge Quiz.");
}

export async function getInProgressChallengeQuizSession(
  noteId: string,
): Promise<ChallengeQuizStartResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/challenge-quiz/in-progress`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ChallengeQuizStartResponse>(
    response,
    "Could not load in-progress Challenge Quiz.",
  );
}

export async function updateChallengeQuizSessionProgress(
  sessionId: string,
  request: ChallengeQuizProgressRequest,
  options: { keepalive?: boolean } = {},
): Promise<ChallengeQuizStartResponse> {
  const response = await fetchWithAuth(
    `/challenge-quiz/sessions/${sessionId}/progress`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
      keepalive: options.keepalive ?? false,
    },
    true,
  );
  return parseApiResponse<ChallengeQuizStartResponse>(
    response,
    "Could not save Challenge Quiz progress.",
  );
}

export async function completeChallengeQuizSession(
  sessionId: string,
  request: ChallengeQuizCompleteRequest,
): Promise<ChallengeQuizSessionResponse> {
  const response = await fetchWithAuth(
    `/challenge-quiz/sessions/${sessionId}/complete`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<ChallengeQuizSessionResponse>(response, "Could not save Challenge Quiz results.");
}

export async function forfeitChallengeQuizSession(
  sessionId: string,
): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    `/challenge-quiz/sessions/${sessionId}/forfeit`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not leave Challenge Quiz.");
}

export async function generateMoreChallengeQuizQuestions(
  sessionId: string,
): Promise<GenerateMoreChallengeQuizResponse> {
  const response = await fetchWithAuth(
    `/challenge-quiz/sessions/${sessionId}/generate-more`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<GenerateMoreChallengeQuizResponse>(
    response,
    "Could not generate more questions.",
  );
}

export async function listRecentChallengeQuizSessions(
  noteId: string,
  limit = 5,
): Promise<ChallengeQuizSessionSummaryResponse[]> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/challenge-quiz/recent?limit=${limit}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ChallengeQuizSessionSummaryResponse[]>(
    response,
    "Could not load recent Challenge Quiz sessions.",
  );
}

export async function listRecentQuizSessions(
  noteId: string,
  limit = 5,
): Promise<RecentQuizSessionHistoryResponse[]> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/quiz-sessions/recent?limit=${limit}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<RecentQuizSessionHistoryResponse[]>(
    response,
    "Could not load recent quiz sessions.",
  );
}

export async function getChallengeQuizPerformanceSummary(
  noteId: string,
): Promise<ChallengeQuizPerformanceSummaryResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/challenge-quiz/performance-summary`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ChallengeQuizPerformanceSummaryResponse>(
    response,
    "Could not load Challenge Quiz performance summary.",
  );
}

export async function getChallengeQuizSessionReview(
  noteId: string,
  sessionId: string,
): Promise<QuizSessionReviewResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/challenge-quiz/sessions/${sessionId}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuizSessionReviewResponse>(
    response,
    "Could not load Challenge Quiz session review.",
  );
}

export async function startLongExam(
  studyPackId: string,
  body: { difficulty?: string; additionalStudyPackIds?: string[] } = {},
): Promise<LongExamStartResponse> {
  const response = await fetchWithAuth(
    `/long-exam/study-packs/${studyPackId}/start`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(body),
    },
    true,
  );
  return parseApiResponse<LongExamStartResponse>(response, "Could not start Long Exam.");
}

export async function getActiveLongExamSession(
  studyPackId: string,
): Promise<LongExamStartResponse | null> {
  const response = await fetchWithAuth(
    `/long-exam/study-packs/${studyPackId}/active`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  if (!response.ok) {
    return throwApiRequestError(response, "Could not load active Long Exam.");
  }
  const text = await response.text();
  if (!text || text.trim() === "" || text.trim() === "null") {
    return null;
  }
  return JSON.parse(text) as LongExamStartResponse;
}

export async function getLongExamSession(
  sessionId: string,
): Promise<LongExamSessionResponse> {
  const response = await fetchWithAuth(
    `/long-exam/sessions/${sessionId}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<LongExamSessionResponse>(response, "Could not load Long Exam session.");
}

export async function saveLongExamProgress(
  sessionId: string,
  body: { questionIndex: number; selectedChoiceIndex: number; selectedMultiChoiceIndices?: number[] },
): Promise<LongExamSessionResponse> {
  const response = await fetchWithAuth(
    `/long-exam/sessions/${sessionId}/progress`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(body),
    },
    true,
  );
  return parseApiResponse<LongExamSessionResponse>(response, "Could not save Long Exam progress.");
}

export async function pauseLongExamSession(
  sessionId: string,
): Promise<LongExamSessionResponse> {
  const response = await fetchWithAuth(
    `/long-exam/sessions/${sessionId}/pause`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<LongExamSessionResponse>(response, "Could not pause Long Exam.");
}

export async function resumeLongExamSession(
  sessionId: string,
): Promise<LongExamSessionResponse> {
  const response = await fetchWithAuth(
    `/long-exam/sessions/${sessionId}/resume`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<LongExamSessionResponse>(response, "Could not resume Long Exam.");
}

export async function completeLongExamSession(
  sessionId: string,
  body: { durationSeconds: number },
): Promise<LongExamMasteryReportResponse> {
  const response = await fetchWithAuth(
    `/long-exam/sessions/${sessionId}/complete`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(body),
    },
    true,
  );
  return parseApiResponse<LongExamMasteryReportResponse>(response, "Could not complete Long Exam.");
}

export async function forfeitLongExamSession(
  sessionId: string,
): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    `/long-exam/sessions/${sessionId}/forfeit`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not forfeit Long Exam.");
}

export async function startInterviewPractice(
  body: { noteId: string; questionCount: number; additionalNoteIds?: string[] },
): Promise<InterviewPracticeStartResponse> {
  const response = await fetchWithAuth(
    "/interview-practice/start",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(body),
    },
    true,
  );
  return parseApiResponse<InterviewPracticeStartResponse>(response, "Could not start Interview Practice.");
}

export async function answerInterviewPracticeQuestion(
  sessionId: string,
  body: { questionIndex: number; selectedChoice: "A" | "B" | "C" | "D"; timeSpentSeconds: number },
): Promise<InterviewPracticeAnswerResponse> {
  const response = await fetchWithAuth(
    `/interview-practice/sessions/${sessionId}/answer`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(body),
    },
    true,
  );
  return parseApiResponse<InterviewPracticeAnswerResponse>(response, "Could not review your interview answer.");
}

export async function completeInterviewPracticeSession(
  sessionId: string,
): Promise<InterviewReadinessReportResponse> {
  const response = await fetchWithAuth(
    `/interview-practice/sessions/${sessionId}/complete`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<InterviewReadinessReportResponse>(response, "Could not complete Interview Practice.");
}

export async function forfeitInterviewPracticeSession(
  sessionId: string,
): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    `/interview-practice/sessions/${sessionId}/forfeit`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not forfeit Interview Practice.");
}

export async function getTodayFocus(): Promise<TodayFocusResponse> {
  const response = await fetchWithAuth(
    "/dashboard/today-focus",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<TodayFocusResponse>(response, "Could not load today's focus.");
}

export async function getDashboardOverview(): Promise<DashboardOverviewResponse> {
  const response = await fetchWithAuth(
    "/dashboard/overview",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<DashboardOverviewResponse>(response, "Could not load dashboard overview.");
}

export async function getUserNotePerformanceSummary(limit = 5): Promise<NotePerformanceSummaryResponse[]> {
  const response = await fetchWithAuth(
    `/dashboard/note-performance?limit=${limit}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NotePerformanceSummaryResponse[]>(response, "Could not load note performance summary.");
}

export async function getStudyEngagement(): Promise<StudyEngagementResponse> {
  const response = await fetchWithAuth(
    "/dashboard/study-engagement",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<StudyEngagementResponse>(response, "Could not load study engagement.");
}

export async function getMasterySnapshot(): Promise<MasterySnapshotResponse> {
  const response = await fetchWithAuth(
    "/dashboard/mastery-snapshot",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<MasterySnapshotResponse>(response, "Could not load mastery snapshot.");
}

export async function createPremiumCheckoutSession(
  request?: BillingCheckoutSessionRequest,
): Promise<BillingCheckoutSessionResponse> {
  const response = await fetchWithAuth(
    "/payments/create",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({
        planType: request?.planType ?? null,
        billingCycle: request?.billingCycle ?? null,
        returnUrl: request?.returnUrl ?? null,
      }),
    },
    true,
  );
  return parseApiResponse<BillingCheckoutSessionResponse>(
    response,
    "Could not start checkout. Please try again.",
  );
}

export async function getBillingPricing(): Promise<BillingPricingResponse> {
  const accessToken = getAccessToken();
  const response = accessToken
    ? await fetchWithAuth(
        "/billing/pricing",
        {
          method: "GET",
          headers: buildAuthHeaders(),
        },
        true,
      )
    : await fetch(buildUrl("/billing/pricing"), {
        method: "GET",
      });

  return parseApiResponse<BillingPricingResponse>(
    response,
    "Could not load pricing.",
  );
}

export async function cancelPremiumSubscription(
  request: CancelPremiumSubscriptionRequest = {},
): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/billing/subscription/cancel",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<MeResponse>(
    response,
    "Could not schedule plan cancellation. Please try again.",
  );
}

export async function getBillingUsageSummary(): Promise<BillingUsageSummaryResponse> {
  const response = await fetchWithAuth(
    "/billing/usage-summary",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<BillingUsageSummaryResponse>(
    response,
    "Could not load billing usage.",
  );
}

export async function getMyPlan(): Promise<MePlanResponse> {
  const response = await fetchWithAuth(
    "/me/plan",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<MePlanResponse>(
    response,
    "Could not load plan details.",
  );
}

export async function getBillingHistory(): Promise<BillingHistoryResponse> {
  const response = await fetchWithAuth(
    "/billing/history",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<BillingHistoryResponse>(
    response,
    "Could not load billing history.",
  );
}

export async function createNote(
  request: UpsertNoteRequest,
  options: { keepalive?: boolean } = {},
): Promise<NoteResponse> {
  const response = await fetchWithAuth(
    "/notes",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
      keepalive: options.keepalive ?? false,
    },
    true,
  );
  return parseApiResponse<NoteResponse>(response, "Could not save note.");
}

export async function getNote(noteId: string): Promise<NoteResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NoteResponse>(response, "Could not load note.");
}

export async function getGeneratedQuiz(noteId: string): Promise<GeneratedQuizResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/generated-quiz`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<GeneratedQuizResponse>(response, "Could not load generated quiz.");
}

export async function generateGeneratedQuiz(
  noteId: string,
  questionCount: TeacherQuizQuestionCount = 10,
  targetLearnerLevel?: string | null,
): Promise<GeneratedQuizResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/generated-quiz`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ questionCount, targetLearnerLevel: targetLearnerLevel ?? null }),
    },
    true,
  );
  return parseApiResponse<GeneratedQuizResponse>(response, "Could not generate quiz.");
}

export async function createQuizShareLink(generatedQuizId: string): Promise<QuizShareLinkResponse> {
  const response = await fetchWithAuth(
    "/quiz-share",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ generatedQuizId }),
    },
    true,
  );
  return parseApiResponse<QuizShareLinkResponse>(response, "Could not create share link.");
}

export async function getQuizShareLinkByQuizId(generatedQuizId: string): Promise<QuizShareLinkResponse | null> {
  const response = await fetchWithAuth(
    `/quiz-share/${generatedQuizId}`,
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  if (response.status === 404) {
    return null;
  }
  return parseApiResponse<QuizShareLinkResponse>(response, "Could not load share link.");
}

export async function toggleQuizShareLink(token: string): Promise<QuizShareLinkResponse> {
  const response = await fetchWithAuth(
    `/quiz-share/${token}/toggle`,
    {
      method: "PATCH",
      headers: buildAuthHeaders("application/json"),
    },
    true,
  );
  return parseApiResponse<QuizShareLinkResponse>(response, "Could not update share link.");
}

export async function getPublicSharedQuiz(token: string): Promise<PublicSharedQuizResponse> {
  const response = await fetch(buildUrl(`/quiz/share/${token}`), {
    method: "GET",
  });
  return parseApiResponse<PublicSharedQuizResponse>(response, "Could not load shared quiz.");
}

export async function getSharedQuizResults(
  token: string,
  answers: number[],
): Promise<SharedQuizResultsResponse> {
  const response = await fetch(buildUrl(`/quiz/share/${token}/results`), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ answers }),
  });
  return parseApiResponse<SharedQuizResultsResponse>(response, "Could not check quiz results.");
}

export async function exportGeneratedQuizDocx(
  quizId: string,
  mode: QuizDocxExportMode,
  headerOverride?: QuizDocxHeaderOverride | null,
  versionCount?: number | null,
): Promise<{ filename: string }> {
  const response = await fetchWithAuth(
    `/quizzes/${quizId}/export-docx?mode=${mode}`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({
        headerOverride: headerOverride ?? null,
        versionCount: versionCount ?? null,
      }),
    },
    true,
  );
  if (!response.ok) {
    return throwApiRequestError(response, "Could not export quiz.");
  }
  const blob = await response.blob();
  const fallbackFilename = mode === "WITH_ANSWERS" ? "generated-quiz-with-answers.docx" : "generated-quiz.docx";
  const filename = extractDownloadFilename(response.headers.get("content-disposition"), fallbackFilename);
  triggerBlobDownload(blob, filename);
  return { filename };
}

export async function exportCombinedGeneratedQuizDocx(
  request: MultiNoteQuizDocxExportRequest,
): Promise<{ filename: string }> {
  const response = await fetchWithAuth(
    "/quizzes/export-docx",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  if (!response.ok) {
    return throwApiRequestError(response, "Could not export exam.");
  }
  const blob = await response.blob();
  const fallbackFilename = request.includeAnswerKey || request.includeExplanations
    ? "combined-exam-with-answers.docx"
    : "combined-exam.docx";
  const filename = extractDownloadFilename(response.headers.get("content-disposition"), fallbackFilename);
  triggerBlobDownload(blob, filename);
  return { filename };
}

export async function listNotes(): Promise<NoteListItemResponse[]> {
  const response = await fetchWithAuth(
    "/notes",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NoteListItemResponse[]>(response, "Could not load notes.");
}

export async function getSavedLibraryFilters(): Promise<SavedLibraryFilterResponse[]> {
  const response = await fetchWithAuth(
    "/library-filters",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<SavedLibraryFilterResponse[]>(response, "Could not load saved filters.");
}

export async function createSavedLibraryFilter(
  name: string,
  filterState: SavedLibraryFilterState,
): Promise<SavedLibraryFilterResponse> {
  const response = await fetchWithAuth(
    "/library-filters",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ name, filterState }),
    },
    true,
  );
  return parseApiResponse<SavedLibraryFilterResponse>(response, "Could not save filter.");
}

export async function deleteSavedLibraryFilter(id: string): Promise<void> {
  const response = await fetchWithAuth(
    `/library-filters/${id}`,
    {
      method: "DELETE",
      headers: buildAuthHeaders(),
    },
    true,
  );
  if (response.ok) {
    return;
  }
  await parseApiResponse<never>(response, "Could not delete filter.");
}

export async function listPublicNotes(params?: {
  audience?: NoteTargetProfileType;
  courseProgram?: string;
  creator?: string | null;
  search?: string;
  size?: number;
  sort?: "copied" | "featured" | "popular" | "recent" | "title" | "views";
  subject?: string;
  tags?: string[];
}): Promise<PublicNoteListResponse> {
  const searchParams = new URLSearchParams();
  if (params?.audience) {
    searchParams.set("audience", params.audience);
  }
  if (params?.courseProgram) {
    searchParams.set("courseProgram", params.courseProgram);
  }
  if (params?.creator) {
    searchParams.set("creator", params.creator);
  }
  if (params?.search) {
    searchParams.set("search", params.search);
  }
  if (typeof params?.size === "number") {
    searchParams.set("size", String(params.size));
  }
  if (params?.sort) {
    searchParams.set("sort", params.sort);
  }
  if (params?.subject) {
    searchParams.set("subject", params.subject);
  }
  (params?.tags ?? []).forEach((tag) => searchParams.append("tag", tag));
  const query = searchParams.size > 0 ? `?${searchParams.toString()}` : "";
  const response = await fetch(buildUrl(`/notes/public${query}`), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<PublicNoteListResponse>(response, "Could not load public notes.");
}

export async function togglePublicNoteLike(noteId: string): Promise<PublicNoteLikeResponse> {
  const response = await fetchWithAuth(
    `/notes/public/${noteId}/like`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<PublicNoteLikeResponse>(response, "Could not update note like.");
}

export async function listSubjects(scope: SubjectSuggestionScope = "public"): Promise<string[]> {
  const path = `/subjects?scope=${scope}`;
  const response = scope === "mine"
    ? await fetchWithAuth(
        path,
        {
          method: "GET",
          headers: buildAuthHeaders(),
        },
        true,
      )
    : await fetch(buildUrl(path), {
        method: "GET",
        headers: buildAuthHeaders(),
      });
  return parseApiResponse<string[]>(response, "Could not load subjects.");
}

export async function listCoursePrograms(scope: CourseProgramSuggestionScope = "public"): Promise<string[]> {
  const path = `/course-programs?scope=${scope}`;
  const response = scope === "mine"
    ? await fetchWithAuth(
        path,
        {
          method: "GET",
          headers: buildAuthHeaders(),
        },
        true,
      )
    : await fetch(buildUrl(path), {
        method: "GET",
        headers: buildAuthHeaders(),
      });
  return parseApiResponse<string[]>(response, "Could not load course/program suggestions.");
}

export async function getPublicNote(noteId: string): Promise<PublicNoteDetailResponse> {
  const response = await fetch(buildUrl(`/notes/public/${noteId}`), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<PublicNoteDetailResponse>(response, "Could not load this public note.");
}

export async function getPublicNoteBySeoPath(
  subject: string,
  slug: string,
): Promise<PublicNoteDetailResponse> {
  const response = await fetch(buildUrl(`/notes/public/seo/${subject}/${slug}`), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<PublicNoteDetailResponse>(response, "Could not load this public note.");
}

export async function getPublicProfile(userId: string): Promise<PublicProfileResponse> {
  const response = await fetch(buildUrl(`/public/profile/${userId}`), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<PublicProfileResponse>(response, "Could not load this public profile.");
}

export async function getPublicCreatorProfile(username: string): Promise<PublicProfileResponse> {
  const response = await fetch(buildUrl(`/public/creator/${username}`), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<PublicProfileResponse>(response, "Could not load this public profile.");
}

export async function updateNote(
  noteId: string,
  request: UpsertNoteRequest,
  options: { keepalive?: boolean } = {},
): Promise<NoteResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}`,
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
      keepalive: options.keepalive ?? false,
    },
    true,
  );
  return parseApiResponse<NoteResponse>(response, "Could not save note.");
}

export async function deleteNote(noteId: string): Promise<void> {
  const response = await fetchWithAuth(
    `/notes/${noteId}`,
    {
      method: "DELETE",
      headers: buildAuthHeaders(),
    },
    true,
  );
  if (!response.ok) {
    await parseApiResponse<void>(response, "Could not delete note.");
  }
}

export function isEmailNotVerifiedError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError
    && (error.code === "EMAIL_NOT_VERIFIED" || error.code === "EMAIL_VERIFICATION_REQUIRED");
}

export function isOcrLimitReachedError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === "OCR_LIMIT_REACHED";
}

export function isNoteGenerationLimitReachedError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === "NOTE_GENERATION_LIMIT_REACHED";
}

export function isExportLimitReachedError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === "MONTHLY_EXPORT_LIMIT_REACHED";
}

export function isQuestionCountNotAllowedError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === "QUESTION_COUNT_NOT_ALLOWED";
}

export function isMultipleExamVersionsNotAllowedError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === "MULTIPLE_EXAM_VERSIONS_NOT_ALLOWED";
}

export function isQuizShareLinkLimitExceededError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === "QUIZ_SHARE_LINK_LIMIT_EXCEEDED";
}

export function isNotEnoughNewQuestionsError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === "NOT_ENOUGH_NEW_QUESTIONS";
}

export async function updateNoteVisibility(noteId: string, visibility: NoteVisibility): Promise<NoteResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/visibility`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ visibility }),
    },
    true,
  );
  return parseApiResponse<NoteResponse>(response, "Could not update note visibility.");
}

export async function copyNote(noteId: string, options?: { includeStudyPack?: boolean }): Promise<NoteResponse> {
  const params = options?.includeStudyPack === false ? "?includeStudyPack=false" : "";
  const response = await fetchWithAuth(
    `/notes/${noteId}/copy${params}`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NoteResponse>(response, "Could not copy note.");
}

export async function copyNoteOnSignup(publicNoteId: string): Promise<{ noteId: string }> {
  const response = await fetchWithAuth(
    "/notes/copy-on-signup",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ publicNoteId }),
    },
    true,
  );
  return parseApiResponse<{ noteId: string }>(response, "Could not copy note.");
}
