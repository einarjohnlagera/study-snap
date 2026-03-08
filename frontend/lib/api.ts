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

export function isNeedsTextConfirmationResponse(
  payload: StudyPackApiResponse,
): payload is NeedsTextConfirmationResponse {
  return "status" in payload && payload.status === "needs_text_confirmation";
}

async function parseApiResponse(response: Response): Promise<StudyPackApiResponse> {
  if (response.ok) {
    return (await response.json()) as StudyPackApiResponse;
  }

  const fallbackMessage = "We could not generate your study pack right now. Please try again.";

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

export async function createStudyPackFromImage(
  imageFile: File,
): Promise<StudyPackApiResponse> {
  const formData = new FormData();
  formData.append("image", imageFile);

  const response = await fetch(buildUrl("/studyPack"), {
    method: "POST",
    body: formData,
  });

  return parseApiResponse(response);
}

export async function confirmStudyPackText(
  draftId: string,
  notesText: string,
): Promise<StudyPackResponse> {
  const response = await fetch(buildUrl("/studyPack/confirm-text"), {
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

