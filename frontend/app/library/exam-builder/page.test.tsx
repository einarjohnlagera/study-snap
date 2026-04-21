import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import ExamBuilderPage from "./page";
import {
  exportCombinedGeneratedQuizDocx,
  listNotes,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

const pushMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: pushMock,
};
let notesParam = "note-99,note-77";

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  useSearchParams: () => ({
    get: (key: string) => (key === "notes" ? notesParam : null),
  }),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/api", () => ({
  exportCombinedGeneratedQuizDocx: jest.fn(),
  listNotes: jest.fn(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

describe("Exam Builder page", () => {
  beforeEach(() => {
    notesParam = "note-99,note-77";
    pushMock.mockReset();
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      profileType: "TEACHER",
    });
    (exportCombinedGeneratedQuizDocx as jest.Mock).mockReset();
    (exportCombinedGeneratedQuizDocx as jest.Mock).mockResolvedValue({ filename: "combined-exam-with-answers.docx" });
    (listNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-42",
        title: "Cell Respiration",
        courseProgram: "Nursing",
        learnerLevel: "COLLEGE",
        subject: "Biology",
        tags: ["cells", "energy", "mitochondria"],
        contentPreview: "ATP production in mitochondria...",
        summaryPreview: "Mitochondria convert glucose into usable ATP energy.",
        visibility: "PRIVATE",
        studyPackId: null,
        studyPackStatus: "DRAFT",
        quizCount: null,
        generatedQuizId: null,
        generatedQuizQuestionCount: null,
        createdAt: "2026-03-20T10:00:00Z",
        updatedAt: "2026-03-21T10:00:00Z",
      },
      {
        id: "note-99",
        title: "Zygote Review",
        courseProgram: "Chemistry",
        learnerLevel: "COLLEGE",
        subject: "Chemistry",
        tags: ["review", "exam", "cells"],
        contentPreview: "Generated chemistry review preview...",
        summaryPreview: "Generated chemistry summary preview.",
        visibility: "PUBLIC",
        studyPackId: "pack-99",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        generatedQuizId: "generated-99",
        generatedQuizQuestionCount: 10,
        createdAt: "2026-03-21T10:00:00Z",
        updatedAt: "2026-03-22T10:00:00Z",
      },
      {
        id: "note-77",
        title: "Dosage Calculations",
        courseProgram: "Pharmacy",
        learnerLevel: "COLLEGE",
        subject: null,
        tags: ["math", "medication", "review"],
        contentPreview: "Medication dosage formulas and unit conversions...",
        summaryPreview: "Practice dosage conversion steps for common prescriptions.",
        visibility: "PRIVATE",
        studyPackId: "pack-77",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 4,
        generatedQuizId: "generated-77",
        generatedQuizQuestionCount: 10,
        createdAt: "2026-03-18T10:00:00Z",
        updatedAt: "2026-03-23T10:00:00Z",
      },
    ]);
  });

  it("renders selected notes on the dedicated page and exports sectioned exams", async () => {
    render(<ExamBuilderPage />);

    expect(await screen.findByRole("heading", { name: "Exam Builder" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Choose a template" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Start from Scratch Begin with one flexible section and build the rest your way." }));

    expect(await screen.findByDisplayValue("Section A")).toBeInTheDocument();
    expect(screen.getAllByText("10 questions")).toHaveLength(2);

    fireEvent.click(screen.getByRole("button", { name: "Move Dosage Calculations up" }));
    fireEvent.click(screen.getByRole("button", { name: "Remove Zygote Review" }));
    fireEvent.click(screen.getByRole("button", { name: "Export Exam" }));

    await waitFor(() => {
      expect(exportCombinedGeneratedQuizDocx).toHaveBeenCalledWith({
        sections: [
          {
            title: "Section A",
            noteIds: ["note-77"],
          },
        ],
        includeAnswerKey: true,
        includeExplanations: true,
      });
    });
  });

  it("keeps the section title input focused while typing", async () => {
    render(<ExamBuilderPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Start from Scratch Begin with one flexible section and build the rest your way." }));

    const input = await screen.findByLabelText("Section title 1");
    input.focus();

    expect(input).toHaveFocus();

    fireEvent.change(input, { target: { value: "Section A - Application" } });

    expect(input).toHaveFocus();
    expect(input).toHaveValue("Section A - Application");
  });

  it("applies built-in templates and asks before replacing an edited structure", async () => {
    render(<ExamBuilderPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Prelim Exam Basic Concepts -> Problem Solving -> Application" }));

    expect(await screen.findByDisplayValue("Section A - Basic Concepts")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Section B - Problem Solving")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Section C - Application")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Section title 1"), {
      target: { value: "Section A - Intro Review" },
    });

    fireEvent.click(screen.getByRole("button", { name: "Change Template" }));
    fireEvent.click(screen.getByRole("button", { name: "Final Exam Comprehensive Review -> Advanced Problems -> Case-Based Questions" }));

    expect(screen.getByRole("heading", { name: "Apply new template?" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Apply Template" }));

    expect(await screen.findByDisplayValue("Section A - Comprehensive Review")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Section B - Advanced Problems")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Section C - Case-Based Questions")).toBeInTheDocument();
  });
});
