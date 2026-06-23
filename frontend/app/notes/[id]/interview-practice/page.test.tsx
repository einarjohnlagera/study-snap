import { fireEvent, render, screen } from "@testing-library/react";
import InterviewPracticePage from "./page";
import { getMe, getNote, startInterviewPractice } from "@/lib/api";

const pushMock = jest.fn();
const replaceMock = jest.fn();

jest.mock("next/navigation", () => ({
  useParams: () => ({ id: "note-1" }),
  usePathname: () => "/notes/note-1/interview-practice",
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
  listNotes: jest.fn(() => Promise.resolve([])),
  startInterviewPractice: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

describe("InterviewPracticePage", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    (startInterviewPractice as jest.Mock).mockReset();
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

    expect(await screen.findByRole("heading", { name: "Interview Practice" })).toBeInTheDocument();
    expect(screen.getByText("Scenario-based practice with AI critique after every answer.")).toBeInTheDocument();
    expect(screen.getByText("Session length")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /5 questions/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /10 questions/i })).toBeInTheDocument();
    expect(screen.getByText("Soft timer, non-enforcing")).toBeInTheDocument();
    expect(screen.getByText("Scenario MCQ with AI critique")).toBeInTheDocument();
    expect(screen.getByText("Counts toward your monthly limit")).toBeInTheDocument();
    expect(screen.queryByText("This will use 1 of your 10 Interview Practice sessions this month.")).not.toBeInTheDocument();
  });

  it("routes Choose another mode back to the shared mode picker", async () => {
    render(<InterviewPracticePage />);

    fireEvent.click(await screen.findByRole("button", { name: "Choose another mode" }));

    expect(pushMock).toHaveBeenCalledWith("/study-packs/sp-1/challenge-quiz");
  });

  it("shows Interview Practice setup to Professional Plus users and opens the paywall from the Start CTA", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      profileType: "PROFESSIONAL",
      planType: "PLUS",
    });

    render(<InterviewPracticePage />);

    expect(await screen.findByRole("heading", { name: "Interview Practice" })).toBeInTheDocument();
    expect(screen.getByText("Session length")).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: "Unlock Interview Practice" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Unlock Interview Practice - Pro" }));

    expect(await screen.findByRole("dialog", { name: "Unlock Interview Practice" })).toBeInTheDocument();
    expect(startInterviewPractice).not.toHaveBeenCalled();
    expect(replaceMock).not.toHaveBeenCalled();
  });
});
