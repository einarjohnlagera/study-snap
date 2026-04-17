import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { GeneratedQuizPreviewPageClient } from "./generated-quiz-preview-page-client";
import { generateGeneratedQuiz, getGeneratedQuiz, getNote } from "@/lib/api";
import { exportGeneratedQuizDocument } from "@/lib/generated-quiz-export";

const pushMock = jest.fn();
const replaceMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: replaceMock,
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/api", () => ({
  getGeneratedQuiz: jest.fn(),
  generateGeneratedQuiz: jest.fn(),
  getNote: jest.fn(),
}));

jest.mock("@/lib/generated-quiz-export", () => ({
  exportGeneratedQuizDocument: jest.fn(),
}));

describe("GeneratedQuizPreviewPageClient", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    (getNote as jest.Mock).mockReset();
    (getGeneratedQuiz as jest.Mock).mockReset();
    (generateGeneratedQuiz as jest.Mock).mockReset();
    (exportGeneratedQuizDocument as jest.Mock).mockReset();

    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Teacher Note",
      subject: "Biology",
      courseProgram: "Nursing",
      tags: [],
      content: "Cells",
      visibility: "PRIVATE",
      createdAt: "2026-04-17T09:00:00Z",
      updatedAt: "2026-04-17T09:00:00Z",
      copiedFromNoteId: null,
      copiedFromUserId: null,
      copiedFromTitle: null,
      copiedFromPublic: false,
      copiedAt: null,
      studyPackId: null,
      studyPackStatus: "DRAFT",
      summary: null,
      keyConcepts: [],
      quiz: [],
      generatedQuiz: null,
      quizCount: 0,
      quickReviewAvailable: false,
      challengeQuizAvailable: false,
      adaptivePracticeAvailable: false,
      difficultySelectionAvailable: false,
    });
    (getGeneratedQuiz as jest.Mock).mockResolvedValue({
      noteId: "note-1",
      generatedAt: "2026-04-17T09:00:00Z",
      questions: [
        {
          question: "What is the nucleus?",
          choices: ["Control center", "Energy source", "Cell wall", "Waste product"],
          correctIndex: 0,
          concept: "Cells",
          explanation: "The nucleus controls cell activity.",
        },
      ],
    });
    (generateGeneratedQuiz as jest.Mock).mockResolvedValue({
      noteId: "note-1",
      generatedAt: "2026-04-17T10:00:00Z",
      questions: [
        {
          question: "What does the membrane do?",
          choices: ["Protects the cell", "Creates ATP", "Stores DNA", "Builds proteins"],
          correctIndex: 0,
          concept: "Cells",
          explanation: "The membrane protects and regulates transport.",
        },
      ],
    });
    (exportGeneratedQuizDocument as jest.Mock).mockResolvedValue({ filename: "quiz.txt" });
  });

  it("renders the generated quiz with answers visible", async () => {
    render(<GeneratedQuizPreviewPageClient noteId="note-1" />);

    expect(await screen.findByRole("heading", { name: "Teacher Note" })).toBeInTheDocument();
    expect(screen.getByText("What is the nucleus?")).toBeInTheDocument();
    expect(screen.getByText("✓ Correct")).toBeInTheDocument();
    expect(screen.getByText("The nucleus controls cell activity.")).toBeInTheDocument();
  });

  it("exports from the dedicated quiz view", async () => {
    render(<GeneratedQuizPreviewPageClient noteId="note-1" />);

    await screen.findByRole("heading", { name: "Teacher Note" });
    fireEvent.click(screen.getByRole("button", { name: "Export" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Export Answer Key" }));

    await waitFor(() => {
      expect(exportGeneratedQuizDocument).toHaveBeenCalled();
    });
  });

  it("regenerates after confirmation", async () => {
    render(<GeneratedQuizPreviewPageClient noteId="note-1" />);

    await screen.findByRole("heading", { name: "Teacher Note" });
    fireEvent.click(screen.getByRole("button", { name: "Regenerate Quiz" }));
    expect(screen.getByText("Regenerate quiz?")).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button", { name: "Regenerate Quiz" }).at(-1) as HTMLButtonElement);

    await waitFor(() => {
      expect(generateGeneratedQuiz).toHaveBeenCalledWith("note-1");
    });
    expect(await screen.findByText("What does the membrane do?")).toBeInTheDocument();
  });
});
