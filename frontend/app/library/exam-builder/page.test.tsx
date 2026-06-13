import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import ExamBuilderPage from "./page";
import {
  exportCombinedGeneratedQuizDocx,
  getCollection,
  getGeneratedQuiz,
  getMe,
  listNotes,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

const pushMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: pushMock,
};
let notesParam = "note-99,note-77";
let collectionIdParam: string | null = null;

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/library/exam-builder",
  useSearchParams: () => ({
    get: (key: string) => {
      if (key === "notes") return notesParam;
      if (key === "collectionId") return collectionIdParam;
      return null;
    },
  }),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/api", () => ({
  exportCombinedGeneratedQuizDocx: jest.fn(),
  getCollection: jest.fn(),
  getGeneratedQuiz: jest.fn(),
  getMe: jest.fn(),
  isMultipleExamVersionsNotAllowedError: jest.fn(() => false),
  listNotes: jest.fn(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

describe("Exam Builder page", () => {
  beforeEach(() => {
    notesParam = "note-99,note-77";
    collectionIdParam = null;
    pushMock.mockReset();
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "teacher-1",
      role: "USER",
      planType: "PLUS",
      profileType: "TEACHER",
    });
    (exportCombinedGeneratedQuizDocx as jest.Mock).mockReset();
    (exportCombinedGeneratedQuizDocx as jest.Mock).mockResolvedValue({ filename: "combined-exam-with-answers.docx" });
    (getGeneratedQuiz as jest.Mock).mockReset();
    (getCollection as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (getMe as jest.Mock).mockResolvedValue({
      schoolName: "NoteLib Academy",
    });
    (getGeneratedQuiz as jest.Mock).mockImplementation(async (noteId: string) => ({
      id: `generated-${noteId}`,
      noteId,
      questions: Array.from({ length: 10 }, (_, questionIndex) => ({
        question: `${noteId} question ${questionIndex + 1}`,
        choices: ["A", "B", "C", "D"],
        correctIndex: questionIndex % 4,
        explanation: `${noteId} explanation ${questionIndex + 1}`,
      })),
      generatedAt: "2026-03-24T10:00:00Z",
    }));
    (getCollection as jest.Mock).mockResolvedValue({
      id: "collection-1",
      title: "Unit One",
      description: null,
      createdAt: "2026-06-01T00:00:00Z",
      updatedAt: "2026-06-02T00:00:00Z",
      items: [],
    });
    (listNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-42",
        title: "Cell Respiration",
        courseProgram: "Nursing",
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
        subject: "Chemistry",
        tags: ["review", "exam", "cells"],
        contentPreview: "Generated chemistry review preview...",
        summaryPreview: "Generated chemistry summary preview.",
        visibility: "PUBLIC",
        studyPackId: "pack-99",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 3,
        generatedQuizId: "generated-99",
        generatedQuizQuestionCount: 5,
        createdAt: "2026-03-21T10:00:00Z",
        updatedAt: "2026-03-22T10:00:00Z",
      },
      {
        id: "note-77",
        title: "Dosage Calculations",
        courseProgram: "Pharmacy",
        subject: null,
        tags: ["math", "medication", "review"],
        contentPreview: "Medication dosage formulas and unit conversions...",
        summaryPreview: "Practice dosage conversion steps for common prescriptions.",
        visibility: "PRIVATE",
        studyPackId: "pack-77",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 4,
        generatedQuizId: "generated-77",
        generatedQuizQuestionCount: 2,
        createdAt: "2026-03-18T10:00:00Z",
        updatedAt: "2026-03-23T10:00:00Z",
      },
      {
        id: "note-55",
        title: "Pathophysiology Cases",
        courseProgram: "Nursing",
        subject: "Case Studies",
        tags: ["cases", "exam"],
        contentPreview: "Symptoms and differential review...",
        summaryPreview: "Use the symptoms to identify likely diagnoses.",
        visibility: "PRIVATE",
        studyPackId: "pack-55",
        studyPackStatus: "STUDY_PACK_READY",
        quizCount: 2,
        generatedQuizId: "generated-55",
        generatedQuizQuestionCount: 10,
        createdAt: "2026-03-17T10:00:00Z",
        updatedAt: "2026-03-20T10:00:00Z",
      },
    ]);
  });

  it("keeps the plain notes query on the existing template-first path", async () => {
    render(<ExamBuilderPage />);

    expect(await screen.findByRole("heading", { name: "Choose a template" })).toBeInTheDocument();
    expect(getCollection).not.toHaveBeenCalled();
  });

  it("seeds collection sections from trimmed labels in collection order", async () => {
    collectionIdParam = "collection-1";
    notesParam = "note-77,note-99,note-55";
    (getCollection as jest.Mock).mockResolvedValue({
      id: "collection-1",
      title: "Unit One",
      description: null,
      createdAt: "2026-06-01T00:00:00Z",
      updatedAt: "2026-06-02T00:00:00Z",
      items: [
        {
          noteId: "note-77",
          label: null,
          position: 3,
          generatedQuizId: "generated-77",
        },
        {
          noteId: "note-55",
          label: " Week 1 ",
          position: 1,
          generatedQuizId: "generated-55",
        },
        {
          noteId: "note-42",
          label: "Week 2",
          position: 2,
          generatedQuizId: null,
        },
        {
          noteId: "note-99",
          label: "Week 1",
          position: 0,
          generatedQuizId: "generated-99",
        },
      ],
    });

    render(<ExamBuilderPage />);

    expect(await screen.findByDisplayValue("Week 1")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Section B")).toBeInTheDocument();
    expect(screen.queryByDisplayValue("Week 2")).not.toBeInTheDocument();
    expect(screen.getByText("Sections from collection labels")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Choose a template" })).not.toBeInTheDocument();
    expect(getGeneratedQuiz).toHaveBeenNthCalledWith(1, "note-99");
    expect(getGeneratedQuiz).toHaveBeenNthCalledWith(2, "note-55");
    expect(getGeneratedQuiz).toHaveBeenNthCalledWith(3, "note-77");
  });

  it("shows the existing load error state when the collection cannot be loaded", async () => {
    collectionIdParam = "missing-collection";
    notesParam = "";
    (getCollection as jest.Mock).mockRejectedValue(new Error("Collection not found."));

    render(<ExamBuilderPage />);

    expect(await screen.findByRole("heading", { name: "Could not load exam builder" })).toBeInTheDocument();
    expect(screen.getByText("Collection not found.")).toBeInTheDocument();
  });

  it("shows the existing empty state for a collection with no quiz-ready notes", async () => {
    collectionIdParam = "collection-1";
    notesParam = "";
    (getCollection as jest.Mock).mockResolvedValue({
      id: "collection-1",
      title: "Draft Unit",
      description: null,
      createdAt: "2026-06-01T00:00:00Z",
      updatedAt: "2026-06-02T00:00:00Z",
      items: [
        {
          noteId: "note-42",
          label: "Week 1",
          position: 0,
          generatedQuizId: null,
        },
      ],
    });

    render(<ExamBuilderPage />);

    expect(await screen.findByRole("heading", { name: "No quiz-ready notes selected" })).toBeInTheDocument();
    expect(getGeneratedQuiz).not.toHaveBeenCalled();
  });

  it("renders selected notes on the dedicated page and exports sectioned exams", async () => {
    render(<ExamBuilderPage />);

    expect(await screen.findByRole("heading", { name: "Exam Builder" })).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Choose a template" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Start Blank Begin with one empty section. Add, rename, and reorder sections as you go." }));

    expect(await screen.findByDisplayValue("Untitled section")).toBeInTheDocument();
    expect(screen.getAllByText("10 questions")).toHaveLength(2);

    fireEvent.click(screen.getByRole("button", { name: "Move Dosage Calculations up" }));
    fireEvent.click(screen.getByRole("button", { name: "Remove Zygote Review" }));
    fireEvent.click(screen.getByRole("button", { name: "Export Exam" }));
    fireEvent.click(screen.getByRole("button", { name: "2" }));
    fireEvent.click(await screen.findByRole("button", { name: /Quiz \+ Answers/i }));

    await waitFor(() => {
      expect(exportCombinedGeneratedQuizDocx).toHaveBeenCalledWith({
        sections: [
          {
            title: "Untitled section",
            questionRefs: Array.from({ length: 10 }, (_, questionIndex) => ({
              noteId: "note-77",
              questionIndex,
            })),
          },
        ],
        includeAnswerKey: true,
        includeExplanations: true,
        headerOverride: {
          className: null,
          includeDate: true,
        },
        versionCount: 2,
      });
    });
  });

  it("keeps the section title input focused while typing", async () => {
    render(<ExamBuilderPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Start Blank Begin with one empty section. Add, rename, and reorder sections as you go." }));

    const input = await screen.findByLabelText("Section title 1");
    input.focus();

    expect(input).toHaveFocus();

    fireEvent.change(input, { target: { value: "Section A - Application" } });

    expect(input).toHaveFocus();
    expect(input).toHaveValue("Section A - Application");
  });

  it("adds remaining quiz-ready notes to the default last section and keeps the URL selection current", async () => {
    render(<ExamBuilderPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Prelim Exam Basic Concepts -> Problem Solving -> Application" }));
    fireEvent.click(screen.getByRole("button", { name: "Add Notes" }));

    expect(await screen.findByRole("heading", { name: "Add Notes" })).toBeInTheDocument();
    expect(screen.getByText("Pathophysiology Cases")).toBeInTheDocument();
    expect(screen.queryByText("Cell Respiration")).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: /Zygote Review/i })).not.toBeInTheDocument();

    const targetSection = screen.getByLabelText("Target section") as HTMLSelectElement;
    expect(targetSection.selectedOptions[0]).toHaveTextContent("Section C - Application");

    fireEvent.click(screen.getByRole("checkbox", { name: /Pathophysiology Cases/i }));
    fireEvent.click(screen.getByRole("button", { name: "Add Selected Notes" }));

    expect(await screen.findByText("Pathophysiology Cases")).toBeInTheDocument();
    await waitFor(() => {
      expect(getGeneratedQuiz).toHaveBeenCalledWith("note-55");
      expect(pushMock).toHaveBeenCalledWith(expect.stringContaining("notes=note-99%2Cnote-77%2Cnote-55"));
    });
  });

  it("shows an Add Notes empty state when every quiz-ready note is already selected", async () => {
    notesParam = "note-99,note-77,note-55";
    render(<ExamBuilderPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Start Blank Begin with one empty section. Add, rename, and reorder sections as you go." }));
    fireEvent.click(screen.getByRole("button", { name: "Add Notes" }));

    expect(await screen.findByText("All your quiz-ready notes are already in this exam. Create or generate a new note to add more.")).toBeInTheDocument();
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

  it("applies Even Balance with the original equal-slice distribution", async () => {
    render(<ExamBuilderPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Prelim Exam Basic Concepts -> Problem Solving -> Application" }));
    fireEvent.click(await screen.findByRole("button", { name: "Even Balance" }));
    fireEvent.click(screen.getByRole("button", { name: "Export Exam" }));
    fireEvent.click(await screen.findByRole("button", { name: /Quiz \+ Answers/i }));

    await waitFor(() => {
      expect(exportCombinedGeneratedQuizDocx).toHaveBeenCalledWith({
        sections: [
          {
            title: "Section A - Basic Concepts",
            questionRefs: [
              { noteId: "note-99", questionIndex: 0 },
              { noteId: "note-99", questionIndex: 1 },
              { noteId: "note-99", questionIndex: 2 },
              { noteId: "note-99", questionIndex: 3 },
              { noteId: "note-99", questionIndex: 4 },
              { noteId: "note-99", questionIndex: 5 },
              { noteId: "note-99", questionIndex: 6 },
            ],
          },
          {
            title: "Section B - Problem Solving",
            questionRefs: [
              { noteId: "note-99", questionIndex: 7 },
              { noteId: "note-99", questionIndex: 8 },
              { noteId: "note-99", questionIndex: 9 },
              { noteId: "note-77", questionIndex: 0 },
              { noteId: "note-77", questionIndex: 1 },
              { noteId: "note-77", questionIndex: 2 },
              { noteId: "note-77", questionIndex: 3 },
            ],
          },
          {
            title: "Section C - Application",
            questionRefs: [
              { noteId: "note-77", questionIndex: 4 },
              { noteId: "note-77", questionIndex: 5 },
              { noteId: "note-77", questionIndex: 6 },
              { noteId: "note-77", questionIndex: 7 },
              { noteId: "note-77", questionIndex: 8 },
              { noteId: "note-77", questionIndex: 9 },
            ],
          },
        ],
        includeAnswerKey: true,
        includeExplanations: true,
        headerOverride: {
          className: null,
          includeDate: true,
        },
        versionCount: 1,
      });
    });
  });

  it("keeps balance guidance compact and renders section composition as chips", async () => {
    const { container } = render(<ExamBuilderPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Prelim Exam Basic Concepts -> Problem Solving -> Application" }));

    const evenBalanceButton = screen.getByRole("button", { name: "Even Balance" });
    const smartBalanceButton = screen.getByRole("button", { name: "Smart Balance" });
    expect(evenBalanceButton).toHaveAttribute("title", "Spreads questions equally across all sections.");
    expect(smartBalanceButton).toHaveAttribute(
      "title",
      "Balances question counts and spreads topic diversity across sections, using each section's learning intent as a guide.",
    );
    expect(evenBalanceButton.querySelector(".lucide-scale")).toBeInTheDocument();
    expect(smartBalanceButton.querySelector(".lucide-layout-grid")).toBeInTheDocument();
    expect(screen.getByText("Reorganize existing questions across sections without changing their content.")).toBeInTheDocument();
    expect(container).not.toHaveTextContent("Note 1");
    expect(screen.getByText("Section A - Basic Concepts")).toBeInTheDocument();
    expect(screen.getByText("20 Qs")).toBeInTheDocument();
  });

  it("applies Smart Balance deterministically across sections", async () => {
    render(<ExamBuilderPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Prelim Exam Basic Concepts -> Problem Solving -> Application" }));
    fireEvent.click(await screen.findByRole("button", { name: "Smart Balance" }));

    expect(await screen.findByRole("heading", { name: "Apply Smart Balance?" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Apply Smart Balance" }));
    expect(await screen.findByText("Rebalanced 20 questions across 3 sections. Balanced by section size and topic coverage.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Export Exam" }));
    fireEvent.click(await screen.findByRole("button", { name: /Quiz \+ Answers/i }));

    await waitFor(() => {
      expect(exportCombinedGeneratedQuizDocx).toHaveBeenCalledTimes(1);
    });

    const exportRequest = (exportCombinedGeneratedQuizDocx as jest.Mock).mock.calls[0]?.[0];
    expect(exportRequest).toMatchObject({
      includeAnswerKey: true,
      includeExplanations: true,
    });
    expect(exportRequest.sections.map((section: { title: string }) => section.title)).toEqual([
      "Section A - Basic Concepts",
      "Section B - Problem Solving",
      "Section C - Application",
    ]);
    expect(exportRequest.sections.map((section: { questionRefs: Array<unknown> }) => section.questionRefs.length)).toEqual([7, 7, 6]);

    const allQuestionRefs = exportRequest.sections.flatMap((section: { questionRefs: Array<{ noteId: string; questionIndex: number }> }) => section.questionRefs);
    expect(allQuestionRefs).toHaveLength(20);
    expect(new Set(allQuestionRefs.map((questionRef: { noteId: string; questionIndex: number }) => `${questionRef.noteId}:${questionRef.questionIndex}`)).size).toBe(20);
    expect(exportRequest.sections.every((section: { questionRefs: Array<{ noteId: string }> }) => (
      new Set(section.questionRefs.map((questionRef) => questionRef.noteId)).size === 2
    ))).toBe(true);
  });
});
