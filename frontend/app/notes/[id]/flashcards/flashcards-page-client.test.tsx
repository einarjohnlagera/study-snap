import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { FlashcardsPageClient } from "./flashcards-page-client";
import { getAuthUser } from "@/lib/auth";
import { getConceptHealth, getNote } from "@/lib/api";

const replaceMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: replaceMock,
  }),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  getConceptHealth: jest.fn(),
  getNote: jest.fn(),
}));

const readyNote = {
  id: "note-1",
  title: "Biology Review",
  subject: "Biology",
  courseProgram: "STEM",
  tags: [],
  content: "Cell notes",
  visibility: "PRIVATE" as const,
  createdAt: "2026-07-04T08:00:00Z",
  updatedAt: "2026-07-04T08:30:00Z",
  copiedFromNoteId: null,
  copiedFromUserId: null,
  copiedFromTitle: null,
  copiedFromPublic: false,
  copiedAt: null,
  studyPackId: "sp-1",
  studyPackStatus: "STUDY_PACK_READY" as const,
  summary: "Summary",
  keyConcepts: ["Cells", "DNA"],
  quiz: [
    {
      question: "What controls cell activity?",
      choices: ["Nucleus", "Ribosome", "Golgi", "Lysosome"],
      correctIndex: 0,
      concept: " cells ",
      explanation: "Cells are the basic unit of life.",
    },
    {
      question: "What stores genetic information?",
      choices: ["DNA", "ATP", "RNA", "Protein"],
      correctIndex: 0,
      concept: "DNA",
      explanation: "DNA stores inherited genetic instructions.",
    },
  ],
  generatedQuiz: null,
  lastUsedTargetLearnerLevel: null,
  quizCount: 2,
  quickReviewAvailable: true,
  challengeQuizAvailable: true,
  adaptivePracticeAvailable: true,
};

describe("FlashcardsPageClient", () => {
  beforeEach(() => {
    replaceMock.mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (getConceptHealth as jest.Mock).mockReset();
    (getNote as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT" });
    (getNote as jest.Mock).mockResolvedValue(readyNote);
  });

  it("renders matched key concepts as flip cards", async () => {
    render(<FlashcardsPageClient noteId="note-1" />);

    expect(await screen.findByRole("heading", { name: "Biology Review" })).toBeInTheDocument();
    expect(screen.getByText("Card 1 of 2")).toBeInTheDocument();
    expect(screen.getByText("Cells")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Flip to definition" }));

    expect(screen.getByText("Cells are the basic unit of life.")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Next" }));

    expect(screen.getByText("Card 2 of 2")).toBeInTheDocument();
    expect(screen.getByText("DNA")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Flip to definition" }));
    expect(screen.getByText("DNA stores inherited genetic instructions.")).toBeInTheDocument();
    expect(getConceptHealth).not.toHaveBeenCalled();
  });

  it("shows a per-card fallback when no quiz explanation matches the concept", async () => {
    (getNote as jest.Mock).mockResolvedValue({
      ...readyNote,
      keyConcepts: ["Cells", "Mitosis"],
      quiz: readyNote.quiz.slice(0, 1),
    });

    render(<FlashcardsPageClient noteId="note-1" />);

    await screen.findByText("Cells");
    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByText("Mitosis")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Flip to definition" }));

    expect(screen.getByText("No definition yet for this concept.")).toBeInTheDocument();
    expect(screen.getByText("This Study Pack did not include a quiz explanation matched to this concept.")).toBeInTheDocument();
  });

  it("fuzzy-matches a key concept to a differently-worded quiz concept", async () => {
    (getNote as jest.Mock).mockResolvedValue({
      ...readyNote,
      keyConcepts: ["Chloroplast"],
      quiz: [
        {
          question: "Where does photosynthesis occur?",
          choices: ["Chloroplast structure", "Mitochondria", "Nucleus", "Ribosome"],
          correctIndex: 0,
          concept: "Chloroplast structure",
          explanation: "The chloroplast is the organelle where photosynthesis takes place.",
        },
      ],
    });

    render(<FlashcardsPageClient noteId="note-1" />);

    await screen.findByText("Chloroplast");
    fireEvent.click(screen.getByRole("button", { name: "Flip to definition" }));

    expect(screen.getByText("The chloroplast is the organelle where photosynthesis takes place.")).toBeInTheDocument();
  });

  it("shows an explicit empty state when a ready Study Pack has no key concepts", async () => {
    (getNote as jest.Mock).mockResolvedValue({
      ...readyNote,
      keyConcepts: [],
      quiz: [],
    });

    render(<FlashcardsPageClient noteId="note-1" />);

    expect(await screen.findByRole("heading", { name: "Nothing to review yet" })).toBeInTheDocument();
    expect(screen.getByText("This Study Pack does not have key concepts to review yet.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Flashcard deck")).not.toBeInTheDocument();
  });

  it.each([
    ["DRAFT", "No key concepts yet", "No key concepts yet. Generate a Study Pack to extract the most important ideas from this note."],
    ["GENERATING", "Key concepts are being generated", "Key concepts are being generated from your note."],
    ["FAILED", "Flashcards are not available yet", "Generation did not complete, so key concepts are not available yet. Retry generation when you are ready."],
  ] as const)("shows the %s guard instead of a broken deck", async (studyPackStatus, title, message) => {
    (getNote as jest.Mock).mockResolvedValue({
      ...readyNote,
      studyPackStatus,
      keyConcepts: [],
      quiz: [],
    });

    render(<FlashcardsPageClient noteId="note-1" />);

    expect(await screen.findByRole("heading", { name: title })).toBeInTheDocument();
    expect(screen.getByText(message)).toBeInTheDocument();
    expect(screen.queryByLabelText("Flashcard deck")).not.toBeInTheDocument();
  });

  it("shows a retry-back error state when loading the note fails", async () => {
    (getNote as jest.Mock).mockRejectedValue(new Error("Could not load note."));

    render(<FlashcardsPageClient noteId="note-1" />);

    expect(await screen.findByRole("heading", { name: "Flashcards unavailable" })).toBeInTheDocument();
    expect(screen.getByText("Could not load note.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Note" })).toHaveAttribute(
      "href",
      "/notes/note-1?tab=key-concepts",
    );
  });

  it("redirects teachers back to note detail without loading flashcards", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "TEACHER" });

    render(<FlashcardsPageClient noteId="note-1" />);

    await waitFor(() => {
      expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?tab=key-concepts");
    });
    expect(getNote).not.toHaveBeenCalled();
  });
});
