import {
  clearAuthUser,
  getAccessToken,
  getRefreshToken,
  setAuthUser,
  type AuthUser,
} from "./auth";

export type QuizItem = {
  question: string;
  choices: string[];
  answer: string;
  concept?: string;
  explanation: string;
};

export type StudyPackResponse = {
  id: string;
  inputType: "text" | "image";
  extractedText: string | null;
  title: string;
  summary: string;
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

export type StudyPackListItemResponse = {
  id: string;
  title: string;
  summaryPreview: string;
  quizCount: number;
  tags: string[];
  createdAt: string;
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
  title: string;
  message: string;
  actionLabel: string;
};

export type ProfileType = "STUDENT" | "PARENT" | "PROFESSIONAL";
export type PlanType = "FREE" | "PREMIUM";
export type UserRole = "USER" | "ADMIN";

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
  firstName: string;
  lastName: string | null;
  displayName: string;
  countryCode: string | null;
  profileType: ProfileType | null;
  emailVerifiedAt: string | null;
  role: UserRole;
  status: "ACTIVE" | "SUSPENDED";
  planType: PlanType;
};

export type OnboardingProfileTypeRequest = {
  profileType: ProfileType;
};

export type SimpleMessageResponse = {
  message: string;
};

export type VerifyEmailRequest = {
  token: string;
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
export type QuickReviewActivityType = "STARTED_QUICK_REVIEW" | "COMPLETED_QUICK_REVIEW";

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
  studyPackId: string;
  title: string;
  weakConcepts: string[];
  quiz: QuizItem[];
  message: string;
};

type ApiErrorPayload = {
  error?: {
    code?: string;
    message?: string;
    details?: string;
  };
};

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
  throw new Error(message);
}

function toAuthUser(payload: AuthResponse): AuthUser {
  return {
    id: payload.userId,
    email: payload.email,
    displayName: payload.displayName,
    profileType: payload.profileType,
    emailVerifiedAt: payload.emailVerifiedAt,
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
    return response;
  }
  const refreshed = await tryRefreshAccessToken();
  if (!refreshed) {
    return response;
  }
  const updatedHeaders = new Headers(init.headers ?? {});
  const token = getAccessToken();
  if (token) {
    updatedHeaders.set("Authorization", `Bearer ${token}`);
  }
  return fetch(buildUrl(path), {
    ...init,
    headers: updatedHeaders,
  });
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

export async function requestEmailVerification(): Promise<SimpleMessageResponse> {
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

export async function confirmEmailVerification(request: VerifyEmailRequest): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/auth/verify-email/confirm",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<MeResponse>(response, "Could not verify email. Please try again.");
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

export async function listMyStudyPacks(): Promise<StudyPackListItemResponse[]> {
  const response = await fetchWithAuth(
    "/study-packs",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<StudyPackListItemResponse[]>(response, "Could not load your Study Packs.");
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
  studyPackId: string,
): Promise<QuickReviewSessionStartResponse> {
  const response = await fetchWithAuth(
    "/quick-review-sessions/start",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ studyPackId }),
    },
    true,
  );
  return parseApiResponse<QuickReviewSessionStartResponse>(response, "Could not start Quick Review.");
}

export async function getInProgressQuickReviewSession(
  studyPackId: string,
): Promise<QuickReviewSessionStartResponse> {
  const response = await fetchWithAuth(
    `/quick-review-sessions/study-packs/${studyPackId}/in-progress`,
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
    `/quick-review-sessions/${sessionId}/progress`,
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
    `/quick-review-sessions/${sessionId}/complete`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<QuickReviewSessionSummaryResponse>(response, "Could not save Quick Review results.");
}

export async function listRecentQuickReviewSessions(
  studyPackId: string,
  limit = 5,
): Promise<QuickReviewSessionSummaryResponse[]> {
  const response = await fetchWithAuth(
    `/quick-review-sessions/study-packs/${studyPackId}/recent?limit=${limit}`,
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
  studyPackId: string,
): Promise<QuickReviewPerformanceSummaryResponse> {
  const response = await fetchWithAuth(
    `/quick-review-sessions/study-packs/${studyPackId}/performance-summary`,
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
  studyPackId: string,
  request: QuickReviewStudyTipRequest,
): Promise<QuickReviewStudyTipResponse> {
  const response = await fetchWithAuth(
    `/quick-review-sessions/study-packs/${studyPackId}/study-tip`,
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
  studyPackId: string,
): Promise<QuickReviewAdaptiveQuizResponse> {
  const response = await fetchWithAuth(
    `/quick-review-sessions/study-packs/${studyPackId}/adaptive-practice`,
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
