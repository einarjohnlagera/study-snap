import { getCurrentUserId, type AuthUser } from "./auth";

export type QuizItem = {
  question: string;
  choices: string[];
  answer: string;
  explanation: string;
};

export type StudyPackResponse = {
  id: string;
  inputType: "text" | "image";
  extractedText: string | null;
  title: string;
  summary: string;
  keyConcepts: string[];
  tags: string[];
  quiz: QuizItem[];
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

export type ProfileType = "STUDENT" | "PARENT" | "PROFESSIONAL";
export type PlanType = "FREE" | "PREMIUM";

export type SignupRequest = {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  displayName?: string;
  countryCode?: string;
  profileType?: ProfileType;
};

export type LoginRequest = {
  email: string;
  password: string;
};

export type AuthResponse = {
  userId: string;
  email: string;
  displayName: string;
  profileType: ProfileType;
  planType: PlanType;
  token: string;
};

export type MeResponse = {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  displayName: string;
  countryCode: string | null;
  profileType: ProfileType;
  status: "ACTIVE" | "SUSPENDED";
  planType: PlanType;
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

type ApiErrorPayload = {
  error?: {
    code?: string;
    message?: string;
    details?: string;
  };
};

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

function buildUrl(path: string) {
  return `${API_BASE_URL}${path}`;
}

function buildAuthHeaders(contentType?: string): HeadersInit {
  const headers: Record<string, string> = {};
  if (contentType) {
    headers["Content-Type"] = contentType;
  }
  const userId = getCurrentUserId();
  if (userId) {
    headers["X-User-Id"] = userId;
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

export async function createStudyPackFromText(
  notesText: string,
): Promise<StudyPackResponse> {
  const response = await fetch(buildUrl("/studyPack"), {
    method: "POST",
    headers: buildAuthHeaders("application/json"),
    body: JSON.stringify({ notesText }),
  });

  const payload = await parseApiResponse<StudyPackApiResponse>(
    response,
    "We could not generate your study pack right now. Please try again.",
  );
  if (isNeedsTextConfirmationResponse(payload)) {
    throw new Error("Unexpected OCR confirmation response for text input.");
  }

  return payload;
}

export async function createStudyPackFromImage(
  imageFile: File,
): Promise<StudyPackApiResponse> {
  const formData = new FormData();
  formData.append("image", imageFile);

  const response = await fetch(buildUrl("/studyPack"), {
    method: "POST",
    headers: buildAuthHeaders(),
    body: formData,
  });

  return parseApiResponse<StudyPackApiResponse>(
    response,
    "We could not generate your study pack right now. Please try again.",
  );
}

export async function confirmStudyPackText(
  draftId: string,
  notesText: string,
): Promise<StudyPackResponse> {
  const response = await fetch(buildUrl("/studyPack/confirm-text"), {
    method: "POST",
    headers: buildAuthHeaders("application/json"),
    body: JSON.stringify({ draftId, notesText }),
  });

  const payload = await parseApiResponse<StudyPackApiResponse>(
    response,
    "We could not generate your study pack right now. Please try again.",
  );
  if (isNeedsTextConfirmationResponse(payload)) {
    throw new Error("Unexpected OCR confirmation response for text confirmation.");
  }

  return payload;
}

function toAuthUser(payload: AuthResponse): AuthUser {
  return {
    id: payload.userId,
    email: payload.email,
    displayName: payload.displayName,
    profileType: payload.profileType,
    planType: payload.planType,
    token: payload.token,
  };
}

export async function signup(request: SignupRequest): Promise<AuthUser> {
  const response = await fetch(buildUrl("/auth/signup"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
  const payload = await parseApiResponse<AuthResponse>(
    response,
    "Could not create account. Please try again.",
  );
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
  const payload = await parseApiResponse<AuthResponse>(
    response,
    "Could not log in. Please try again.",
  );
  return toAuthUser(payload);
}

export async function getMe(): Promise<MeResponse> {
  const response = await fetch(buildUrl("/auth/me"), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<MeResponse>(
    response,
    "Could not load profile. Please try again.",
  );
}

export async function listMyStudyPacks(): Promise<StudyPackListItemResponse[]> {
  const response = await fetch(buildUrl("/studyPack"), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<StudyPackListItemResponse[]>(
    response,
    "Could not load your Study Packs.",
  );
}

export async function deleteMyStudyPack(id: string): Promise<void> {
  const response = await fetch(buildUrl(`/studyPack/${id}`), {
    method: "DELETE",
    headers: buildAuthHeaders(),
  });
  if (!response.ok) {
    await parseApiResponse<void>(response, "Could not delete this Study Pack.");
  }
}

