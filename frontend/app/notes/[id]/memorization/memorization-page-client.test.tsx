import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemorizationPageClient } from "./memorization-page-client";
import { getAuthUser } from "@/lib/auth";
import {
  getConceptHealth,
  getMemorizationCards,
  getNote,
  gradeMemorizationCard,
} from "@/lib/api";

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
  getMemorizationCards: jest.fn(),
  getNote: jest.fn(),
  gradeMemorizationCard: jest.fn(),
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
  keyConcepts: ["Cells", "DNA", "Mitosis"],
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

describe("MemorizationPageClient", () => {
  beforeEach(() => {
    jest.useRealTimers();
    replaceMock.mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (getConceptHealth as jest.Mock).mockReset();
    (getMemorizationCards as jest.Mock).mockReset();
    (getNote as jest.Mock).mockReset();
    (gradeMemorizationCard as jest.Mock).mockReset();
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "STUDENT" });
    (getNote as jest.Mock).mockResolvedValue(readyNote);
    (getMemorizationCards as jest.Mock).mockResolvedValue([]);
    (gradeMemorizationCard as jest.Mock).mockImplementation(async (_studyPackId, concept, grade) => ({
      concept: concept.toLowerCase(),
      intervalDays: grade === "GOOD" ? 1 : 0,
      easeFactor: 2.5,
      repetitions: grade === "GOOD" ? 1 : 0,
      dueAt: grade === "GOOD" ? "2099-07-05T08:00:00Z" : "2020-01-01T00:00:00Z",
      lastReviewedAt: "2026-07-04T08:00:00Z",
      lastGrade: grade,
    }));
  });

  it("reviews only matched concepts and advances after a successful grade", async () => {
    render(<MemorizationPageClient noteId="note-1" />);

    expect(await screen.findByRole("heading", { name: "Biology Review" })).toBeInTheDocument();
    expect(screen.getByText("2 due")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Cells" })).toBeInTheDocument();
    expect(screen.queryByText("Mitosis")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Reveal definition" }));
    expect(screen.getByText("Cells are the basic unit of life.")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Good Normal interval" }));

    await waitFor(() => {
      expect(gradeMemorizationCard).toHaveBeenCalledWith("sp-1", "Cells", "GOOD");
    });
    expect(await screen.findByRole("heading", { name: "DNA" })).toBeInTheDocument();
    expect(getConceptHealth).not.toHaveBeenCalled();
  });

  it("orders due cards by persisted due date", async () => {
    (getMemorizationCards as jest.Mock).mockResolvedValue([
      {
        concept: "dna",
        intervalDays: 1,
        easeFactor: 2.5,
        repetitions: 1,
        dueAt: "2020-01-01T00:00:00Z",
        lastReviewedAt: "2019-12-31T00:00:00Z",
        lastGrade: "GOOD",
      },
      {
        concept: "cells",
        intervalDays: 1,
        easeFactor: 2.5,
        repetitions: 1,
        dueAt: "2021-01-01T00:00:00Z",
        lastReviewedAt: "2020-12-31T00:00:00Z",
        lastGrade: "GOOD",
      },
    ]);

    render(<MemorizationPageClient noteId="note-1" />);

    expect(await screen.findByRole("heading", { name: "DNA" })).toBeInTheDocument();
  });

  it("shows an all-caught-up state when no eligible card is due", async () => {
    (getMemorizationCards as jest.Mock).mockResolvedValue([
      {
        concept: "cells",
        intervalDays: 1,
        easeFactor: 2.5,
        repetitions: 1,
        dueAt: "2099-01-01T00:00:00Z",
        lastReviewedAt: "2026-07-04T08:00:00Z",
        lastGrade: "GOOD",
      },
      {
        concept: "dna",
        intervalDays: 1,
        easeFactor: 2.5,
        repetitions: 1,
        dueAt: "2099-01-02T00:00:00Z",
        lastReviewedAt: "2026-07-04T08:00:00Z",
        lastGrade: "GOOD",
      },
    ]);

    render(<MemorizationPageClient noteId="note-1" />);

    expect(await screen.findByRole("heading", { name: "You're all caught up" })).toBeInTheDocument();
    expect(screen.getByText(/No memorization cards are due right now/)).toBeInTheDocument();
  });

  it("shows an empty state when no concepts have matched explanations", async () => {
    (getNote as jest.Mock).mockResolvedValue({
      ...readyNote,
      keyConcepts: ["Mitosis"],
      quiz: [],
    });

    render(<MemorizationPageClient noteId="note-1" />);

    expect(await screen.findByRole("heading", { name: "Nothing to memorize yet" })).toBeInTheDocument();
    expect(screen.getByText("This Study Pack does not have key concepts with matched quiz explanations yet.")).toBeInTheDocument();
  });

  it.each([
    ["DRAFT", "No key concepts yet", "No key concepts yet. Generate a Study Pack to extract the most important ideas from this note."],
    ["GENERATING", "Key concepts are being generated", "Key concepts are being generated from your note."],
    ["FAILED", "Memorization is not available yet", "Generation did not complete, so key concepts are not available yet. Retry generation when you are ready."],
  ] as const)("shows the %s guard instead of loading the schedule", async (studyPackStatus, title, message) => {
    (getNote as jest.Mock).mockResolvedValue({
      ...readyNote,
      studyPackStatus,
      studyPackId: studyPackStatus === "DRAFT" ? null : "sp-1",
      keyConcepts: [],
      quiz: [],
    });

    render(<MemorizationPageClient noteId="note-1" />);

    expect(await screen.findByRole("heading", { name: title })).toBeInTheDocument();
    expect(screen.getByText(message)).toBeInTheDocument();
    expect(getMemorizationCards).not.toHaveBeenCalled();
  });

  it("keeps the same card visible when grading fails", async () => {
    (gradeMemorizationCard as jest.Mock).mockRejectedValue(new Error("Could not save grade."));

    render(<MemorizationPageClient noteId="note-1" />);

    await screen.findByRole("heading", { name: "Cells" });
    fireEvent.click(screen.getByRole("button", { name: "Reveal definition" }));
    fireEvent.click(screen.getByRole("button", { name: "Good Normal interval" }));

    expect(await screen.findByText("Could not save grade.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Cells" })).toBeInTheDocument();
    expect(screen.getByText("Cells are the basic unit of life.")).toBeInTheDocument();
  });

  it("shows an error state when loading memorization schedule fails", async () => {
    (getMemorizationCards as jest.Mock).mockRejectedValue(new Error("Schedule unavailable."));

    render(<MemorizationPageClient noteId="note-1" />);

    expect(await screen.findByRole("heading", { name: "Memorization unavailable" })).toBeInTheDocument();
    expect(screen.getByText("Schedule unavailable.")).toBeInTheDocument();
  });

  it("redirects teachers back to note detail without loading memorization", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({ profileType: "TEACHER" });

    render(<MemorizationPageClient noteId="note-1" />);

    await waitFor(() => {
      expect(replaceMock).toHaveBeenCalledWith("/notes/note-1?tab=key-concepts");
    });
    expect(getNote).not.toHaveBeenCalled();
    expect(getMemorizationCards).not.toHaveBeenCalled();
  });
});
