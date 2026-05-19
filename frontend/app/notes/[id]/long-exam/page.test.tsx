import { fireEvent, render, screen } from "@testing-library/react";
import LongExamPage from "./page";
import { getAuthUser } from "@/lib/auth";
import { getActiveLongExamSession, getMe, getNote, listNotes, startLongExam } from "@/lib/api";

const pushMock = jest.fn();
const replaceMock = jest.fn();

jest.mock("next/navigation", () => ({
  useParams: () => ({ id: "note-1" }),
  useRouter: () => ({
    push: pushMock,
    replace: replaceMock,
  }),
}));

jest.mock("@/components/study-pack/quiz-session-guard", () => ({
  useQuizSessionGuard: () => ({
    requestLeave: jest.fn(),
    LeaveQuizModal: () => null,
  }),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  completeLongExamSession: jest.fn(),
  forfeitLongExamSession: jest.fn(),
  getActiveLongExamSession: jest.fn(),
  getLongExamSession: jest.fn(),
  getMe: jest.fn(),
  getNote: jest.fn(),
  listNotes: jest.fn(),
  resumeLongExamSession: jest.fn(),
  saveLongExamProgress: jest.fn(),
  startLongExam: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

describe("LongExamPage", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PRO",
    });
    (getMe as jest.Mock).mockResolvedValue({
      learnerLevel: "COLLEGE",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Comprehensive Biology",
      subject: "Biology",
      studyPackId: "sp-1",
      studyPackStatus: "STUDY_PACK_READY",
    });
    (getActiveLongExamSession as jest.Mock).mockResolvedValue(null);
    (listNotes as jest.Mock).mockResolvedValue([]);
    (startLongExam as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "GENERATING",
      quiz: [],
      totalQuestions: 25,
      difficulty: "mixed",
      canResume: false,
      timeLimitSeconds: 0,
      timerStartedAtEpochSeconds: 0,
      sourceNoteRefs: [],
    });
  });

  it("renders the polished Long Exam prestart setup rhythm", async () => {
    render(<LongExamPage />);

    expect(await screen.findByRole("heading", { name: "Long Exam" })).toBeInTheDocument();
    expect(screen.getByText("A comprehensive mastery sitting built from your note. Treat it as a focused study session, not a quick quiz.")).toBeInTheDocument();
    expect(screen.getByText("Comprehensive Biology")).toBeInTheDocument();
    expect(screen.getByText("What to expect")).toBeInTheDocument();
    expect(screen.getByText("Fixed question set")).toBeInTheDocument();
    expect(screen.getByText("Generated upfront — no progressive add. 25 questions from this note.")).toBeInTheDocument();
    expect(screen.getByText("The clock does not pause")).toBeInTheDocument();
    expect(screen.getByText("Scored against every question")).toBeInTheDocument();
    expect(screen.getByText("Mastery report at the end")).toBeInTheDocument();
    expect(screen.getByText("1 note · 25 questions")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Begin Long Exam" })).toBeInTheDocument();
  });

  it("routes Choose another mode back to the shared mode picker", async () => {
    render(<LongExamPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Choose another mode" }));

    expect(pushMock).toHaveBeenCalledWith("/study-packs/sp-1/challenge-quiz");
  });

  it("renders selectable same-subject source notes", async () => {
    (listNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-2",
        title: "Cell Transport",
        subject: "Biology",
        studyPackId: "sp-2",
        studyPackStatus: "STUDY_PACK_READY",
      },
      {
        id: "note-3",
        title: "Organic Chemistry",
        subject: "Chemistry",
        studyPackId: "sp-3",
        studyPackStatus: "STUDY_PACK_READY",
      },
    ]);

    render(<LongExamPage />);

    expect(await screen.findByText("Span this exam across more notes")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Cell Transport/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Organic Chemistry/ })).not.toBeInTheDocument();
  });

  it("updates source summary and sends selected additional study pack ids", async () => {
    (listNotes as jest.Mock).mockResolvedValue([
      {
        id: "note-2",
        title: "Cell Transport",
        subject: "Biology",
        studyPackId: "sp-2",
        studyPackStatus: "STUDY_PACK_READY",
      },
      {
        id: "note-3",
        title: "Genetics Lab",
        subject: "Biology",
        studyPackId: "sp-3",
        studyPackStatus: "STUDY_PACK_READY",
      },
    ]);

    render(<LongExamPage />);

    fireEvent.click(await screen.findByRole("button", { name: /Cell Transport/ }));
    fireEvent.click(screen.getByRole("button", { name: /Genetics Lab/ }));
    expect(screen.getByText("3 notes · 25 questions")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Begin Long Exam" }));

    expect(startLongExam).toHaveBeenCalledWith("sp-1", {
      additionalStudyPackIds: ["sp-2", "sp-3"],
    });
  });

  it("omits additional study pack ids when no extra notes are selected", async () => {
    render(<LongExamPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Begin Long Exam" }));

    expect(startLongExam).toHaveBeenCalledWith("sp-1", {});
  });
});
