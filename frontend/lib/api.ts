import {
  clearAuthUser,
  getAccessToken,
  getRefreshToken,
  handleUnauthorizedSession,
  setAuthUser,
  type AuthUser,
} from "./auth";
import type { MePlanResponse } from "./me-plan";

export type { MePlanResponse } from "./me-plan";

export type QuizItem = {
  question: string;
  choices: string[];
  answer: string;
  concept?: string;
  explanation: string;
};

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
export type TodayFocusType =
  | "RESUME_REVIEW"
  | "RETRY_REVIEW"
  | "PRACTICE_WEAK_CONCEPT"
  | "REVIEW_PACK"
  | "STUDY_SUGGESTION";

export type ContinueStudyingResponse = {
  studyPackId: string | null;
  noteId: string | null;
  title: string | null;
  summaryPreview: string | null;
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
export type PlanType = "FREE" | "PREMIUM";
export type BillingCycle = "MONTHLY" | "YEARLY";
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

export type BillingPricingResponse = {
  region: string;
  currency: string;
  monthlyPrice: number;
  yearlyPrice: number;
  introMonthlyPrice: number | null;
  hasIntroPromo: boolean;
  introEligible: boolean;
};

export type AnalyticsEventType =
  | "NOTE_CREATED"
  | "STUDY_PACK_GENERATED"
  | "QUICK_REVIEW_STARTED"
  | "CHALLENGE_QUIZ_STARTED"
  | "ADAPTIVE_PRACTICE_STARTED"
  | "PAYWALL_VIEWED"
  | "PAYWALL_DISMISSED"
  | "FEATURE_LOCKED_CLICKED"
  | "UPGRADE_CLICKED"
  | "SUBSCRIPTION_STARTED"
  | "PUBLIC_NOTE_VIEWED"
  | "PUBLIC_NOTE_COPIED"
  | "PUBLIC_NOTE_COPY_CLICKED"
  | "LOGIN"
  | "SIGNUP"
  | "LANDING_PAGE_VIEWED"
  | "LANDING_CTA_CLICKED"
  | "DEMO_OPENED"
  | "SIGNUP_STARTED"
  | "SIGNUP_COMPLETED"
  | "EMAIL_VERIFICATION_SENT"
  | "EMAIL_VERIFIED";

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
  provider: "NONE" | "STRIPE" | "PAYMONGO";
  cancelAtPeriodEnd: boolean;
  startedAt: string;
};

export type AdminRecentFailedPaymentItemResponse = {
  transactionId: string;
  userEmail: string;
  amount: number;
  currency: string;
  provider: "NONE" | "STRIPE" | "PAYMONGO";
  createdAt: string;
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

export type AuthResponse = {
  userId: string;
  email: string;
  displayName: string;
  profileType: ProfileType | null;
  emailVerifiedAt: string | null;
  onboardingCompletedAt: string | null;
  productOnboardingCompletedAt: string | null;
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
  displayName: string;
  countryCode: string | null;
  profileType: ProfileType | null;
  examDate: string | null;
  engagementMode: EngagementMode;
  inactivityRemindersEnabled: boolean;
  weakConceptRemindersEnabled: boolean;
  emailVerifiedAt: string | null;
  onboardingCompletedAt: string | null;
  productOnboardingCompletedAt: string | null;
  studyPackCount: number;
  role: UserRole;
  status: "ACTIVE" | "SUSPENDED";
  planType: PlanType;
  subscription: SubscriptionPlanStatusResponse;
};

export type UpdateEngagementModeRequest = {
  engagementMode: EngagementMode;
};

export type UpdateStudyRemindersRequest = {
  inactivityRemindersEnabled: boolean;
  weakConceptRemindersEnabled: boolean;
};

export type UpdateUserProfileRequest = {
  firstName: string;
  lastName: string;
  email: string;
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
  engagementMode: EngagementMode;
  inactivityRemindersEnabled: boolean;
  weakConceptRemindersEnabled: boolean;
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
  status: "IN_PROGRESS" | "COMPLETED" | null;
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
  status?: "IN_PROGRESS" | "COMPLETED";
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

export type QuickReviewAdaptiveQuizResponse = {
  sessionId: string | null;
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
};

export type ChallengeQuizStartRequest = {
  difficulty?: "easy" | "medium" | "hard";
};

export type ChallengeQuizStartResponse = {
  sessionId: string | null;
  studyPackId: string;
  title: string;
  totalQuestions: number;
  timeLimitSeconds: number;
  usedThisMonth: number;
  monthlyLimit: number;
  difficultySelectionAvailable: boolean;
  selectedDifficulty: "easy" | "medium" | "hard";
  quiz: QuizItem[];
  currentQuestionIndex: number;
  sessionState: Record<string, unknown> | null;
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
  status: "IN_PROGRESS" | "COMPLETED";
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

export type BillingUsageSummaryResponse = {
  planType: PlanType;
  studyPacksUsed: number;
  studyPacksLimit: number;
  challengeQuizUsed: number;
  challengeQuizLimit: number;
  adaptivePracticeUsed: number;
  adaptivePracticeLimit: number;
  adaptivePracticeAvailable: boolean;
  difficultySelectionAvailable: boolean;
};

export type BillingHistoryItemResponse = {
  id: string;
  date: string;
  description: string;
  amount: number;
  currency: string;
  status: "PENDING" | "SUCCESS" | "FAILED" | "REFUNDED";
  provider: "NONE" | "STRIPE" | "PAYMONGO";
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
  tags?: string[];
  content: string;
};

export type NoteResponse = {
  id: string;
  title: string | null;
  subject: string | null;
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
  quizCount: number;
  quickReviewAvailable: boolean;
  challengeQuizAvailable: boolean;
  adaptivePracticeAvailable: boolean;
  difficultySelectionAvailable: boolean;
};

export type NoteStudyPackStatus = "DRAFT" | "STUDY_PACK_READY";
export type NoteVisibility = "PRIVATE" | "PUBLIC";

export type NoteListItemResponse = {
  id: string;
  ownerUserId: string | null;
  title: string | null;
  subject: string | null;
  tags: string[];
  contentPreview: string;
  visibility: NoteVisibility;
  studyPackId: string | null;
  studyPackStatus: NoteStudyPackStatus;
  quizCount: number | null;
  official: boolean;
  updatedAt: string;
};

export type PublicNoteDetailResponse = {
  id: string;
  ownerUserId: string | null;
  title: string | null;
  subject: string | null;
  tags: string[];
  contentPreview: string;
  studyPackStatus: NoteStudyPackStatus;
  summary: string | null;
  keyConcepts: string[];
  quiz: QuizItem[];
  official: boolean;
  authorDisplayName: string;
  updatedAt: string;
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

function toAuthUser(payload: AuthResponse): AuthUser {
  return {
    id: payload.userId,
    email: payload.email,
    displayName: payload.displayName,
    profileType: payload.profileType,
    emailVerifiedAt: payload.emailVerifiedAt,
    onboardingCompletedAt: payload.onboardingCompletedAt,
    productOnboardingCompletedAt: payload.productOnboardingCompletedAt,
    role: payload.role,
    planType: payload.planType,
    accessToken: payload.token,
    refreshToken: payload.refreshToken,
    accessTokenExpiresAt: payload.accessTokenExpiresAt,
    refreshTokenExpiresAt: payload.refreshTokenExpiresAt,
  };
}

async function tryRefreshAccessToken(): Promise<boolean> {
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

export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    clearAuthUser();
    return;
  }
  await fetch(buildUrl("/auth/logout"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ refreshToken }),
  });
  clearAuthUser();
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
  return parseApiResponse<MeResponse>(response, "Could not load profile. Please try again.");
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
  return parseApiResponse<MeResponse>(response, "Could not update profile. Please try again.");
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
  return parseApiResponse<MeResponse>(response, "Could not complete onboarding. Please try again.");
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
  return parseApiResponse<MeResponse>(response, "Could not complete onboarding. Please try again.");
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
  return parseApiResponse<MeResponse>(response, "Could not complete onboarding. Please try again.");
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
  return parseApiResponse<MeResponse>(response, "Could not update engagement mode. Please try again.");
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
  return parseApiResponse<MeResponse>(response, "Could not update study reminders. Please try again.");
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

export async function createStudyPackFromNote(noteId: string): Promise<StudyPackResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/generate`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<StudyPackResponse>(
    response,
    "We could not generate your study pack right now. Please try again.",
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
  billingCycle: BillingCycle = "MONTHLY",
  voucherCode?: string | null,
): Promise<BillingCheckoutSessionResponse> {
  const response = await fetchWithAuth(
    "/billing/checkout-session",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ billingCycle, voucherCode: voucherCode ?? null }),
    },
    true,
  );
  return parseApiResponse<BillingCheckoutSessionResponse>(
    response,
    "Could not start Premium checkout. Please try again.",
  );
}

export async function joinPremiumWaitlist(): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    "/premium/waitlist",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(
    response,
    "Could not join the Premium waitlist right now. Please try again.",
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
    "Could not load Premium pricing.",
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
    "Could not schedule Premium cancellation. Please try again.",
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

export async function listPublicNotes(): Promise<NoteListItemResponse[]> {
  const response = await fetch(buildUrl("/notes/public"), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<NoteListItemResponse[]>(response, "Could not load public notes.");
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

export async function copyNote(noteId: string): Promise<NoteResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/copy`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NoteResponse>(response, "Could not copy note.");
}
