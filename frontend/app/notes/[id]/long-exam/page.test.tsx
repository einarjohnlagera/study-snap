import { fireEvent, render, screen } from "@testing-library/react";
import LongExamPage from "./page";
import { getAuthUser } from "@/lib/auth";
import { getActiveLongExamSession, getNote, listNotes, startLongExam } from "@/lib/api";

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

    expect(await screen.findByText("LONG EXAM MODE")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Start Long Exam" })).toBeInTheDocument();
    expect(screen.getByText("Review the exam setup for Comprehensive Biology before you begin.")).toBeInTheDocument();
    expect(screen.getByText("Before you begin")).toBeInTheDocument();
    expect(screen.getByText("The full question set is generated before the exam starts. Stay focused once the exam begins; your mastery report appears after submission.")).toBeInTheDocument();
    expect(screen.getByText("Timer")).toBeInTheDocument();
    expect(screen.getByText("Question count")).toBeInTheDocument();
    expect(screen.getByText("Monthly limit")).toBeInTheDocument();
    expect(screen.getByText("Untimed - complete at your own pace.")).toBeInTheDocument();
    expect(screen.getByText("Fixed long-form exam (20 / 25 / 30 by learner level).")).toBeInTheDocument();
    expect(screen.getByText("Counts toward your monthly Long Exam usage.")).toBeInTheDocument();
    expect(screen.getByText("Exam will cover 1 note (25 questions).")).toBeInTheDocument();
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

    expect(await screen.findByText("Add notes from this subject")).toBeInTheDocument();
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
    expect(screen.getByText("Exam will cover 3 notes (25 questions).")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Start Long Exam" }));

    expect(startLongExam).toHaveBeenCalledWith("sp-1", {
      additionalStudyPackIds: ["sp-2", "sp-3"],
    });
  });

  it("omits additional study pack ids when no extra notes are selected", async () => {
    render(<LongExamPage />);

    fireEvent.click(await screen.findByRole("button", { name: "Start Long Exam" }));

    expect(startLongExam).toHaveBeenCalledWith("sp-1", {});
  });
});
