import { fireEvent, render, screen } from "@testing-library/react";
import InterviewPracticePage from "./page";
import { getMe, getNote } from "@/lib/api";

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

jest.mock("@/lib/api", () => ({
  answerInterviewPracticeQuestion: jest.fn(),
  completeInterviewPracticeSession: jest.fn(),
  forfeitInterviewPracticeSession: jest.fn(),
  getMe: jest.fn(),
  getNote: jest.fn(),
  startInterviewPractice: jest.fn(),
}));

describe("InterviewPracticePage", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    (getMe as jest.Mock).mockResolvedValue({
      profileType: "PROFESSIONAL",
      planType: "PRO",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Backend Interview Prep",
      studyPackId: "sp-1",
      studyPackStatus: "STUDY_PACK_READY",
    });
  });

  it("renders the polished prestart setup rhythm", async () => {
    render(<InterviewPracticePage />);

    expect(await screen.findByText("INTERVIEW PRACTICE")).toBeInTheDocument();
    expect(await screen.findByText((_, element) => (
      element?.textContent === "Review your practice setup for Backend Interview Prep."
    ))).toBeInTheDocument();
    expect(screen.getByText("Session length")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /5 questions/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /10 questions/i })).toBeInTheDocument();
    expect(screen.getByText("Soft timer")).toBeInTheDocument();
    expect(screen.getByText("Format")).toBeInTheDocument();
    expect(screen.getByText("Monthly limit")).toBeInTheDocument();
    expect(screen.queryByText("This will use 1 of your 10 Interview Practice sessions this month.")).not.toBeInTheDocument();
  });

  it("routes Choose another mode back to the shared mode picker", async () => {
    render(<InterviewPracticePage />);

    fireEvent.click(await screen.findByRole("button", { name: "Choose another mode" }));

    expect(pushMock).toHaveBeenCalledWith("/study-packs/sp-1/challenge-quiz");
  });
});
