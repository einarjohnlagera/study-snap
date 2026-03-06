export type QuizItem = {
  question: string;
  choices: string[];
  answer: string;
  explanation: string;
};

export type ReviewResponse = {
  id: string;
  inputType: "text" | "image";
  extractedText: string | null;
  title: string;
  summary: string;
  keyConcepts: string[];
  quiz: QuizItem[];
  meta: {
    ocrConfidence: number | null;
    latencyMs: number | null;
  };
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

export type ReviewApiResponse = ReviewResponse | NeedsTextConfirmationResponse;

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

export function isNeedsTextConfirmationResponse(
  payload: ReviewApiResponse,
): payload is NeedsTextConfirmationResponse {
  return "status" in payload && payload.status === "needs_text_confirmation";
}

async function parseApiResponse(response: Response): Promise<ReviewApiResponse> {
  if (response.ok) {
    return (await response.json()) as ReviewApiResponse;
  }

  const fallbackMessage = "We could not generate your review right now. Please try again.";

  let errorPayload: ApiErrorPayload | null = null;
  try {
    errorPayload = (await response.json()) as ApiErrorPayload;
  } catch {
    // Ignore JSON parse failures and use fallback below.
  }

  const message = errorPayload?.error?.message ?? fallbackMessage;
  throw new Error(message);
}

export async function createReviewFromText(
  notesText: string,
): Promise<ReviewResponse> {
  const response = await fetch(buildUrl("/review"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ notesText }),
  });

  const payload = await parseApiResponse(response);
  if (isNeedsTextConfirmationResponse(payload)) {
    throw new Error("Unexpected OCR confirmation response for text input.");
  }

  return payload;
}

export async function createReviewFromImage(
  imageFile: File,
): Promise<ReviewApiResponse> {
  const formData = new FormData();
  formData.append("image", imageFile);

  const response = await fetch(buildUrl("/review"), {
    method: "POST",
    body: formData,
  });

  return parseApiResponse(response);
}

export async function confirmReviewText(
  draftId: string,
  notesText: string,
): Promise<ReviewResponse> {
  const response = await fetch(buildUrl("/review/confirm-text"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ draftId, notesText }),
  });

  const payload = await parseApiResponse(response);
  if (isNeedsTextConfirmationResponse(payload)) {
    throw new Error("Unexpected OCR confirmation response for text confirmation.");
  }

  return payload;
}
