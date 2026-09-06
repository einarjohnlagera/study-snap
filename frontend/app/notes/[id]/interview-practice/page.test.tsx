import { fireEvent, render, screen } from "@testing-library/react";
import InterviewPracticePage from "./page";
import {
  answerInterviewPracticeQuestion,
  completeInterviewPracticeSession,
  getCollection,
  getMe,
  getNote,
  listNotes,
  startInterviewPractice,
} from "@/lib/api";

const pushMock = jest.fn();
const replaceMock = jest.fn();
let searchParamsMock = new URLSearchParams();

jest.mock("next/navigation", () => ({
  useParams: () => ({ id: "note-1" }),
  usePathname: () => "/notes/note-1/interview-practice",
  useSearchParams: () => searchParamsMock,
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
  getCollection: jest.fn(),
  getMe: jest.fn(),
  getNote: jest.fn(),
  listNotes: jest.fn(),
  startInterviewPractice: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

describe("InterviewPracticePage", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    searchParamsMock = new URLSearchParams();
    (getCollection as jest.Mock).mockReset();
    (startInterviewPractice as jest.Mock).mockReset();
    (answerInterviewPracticeQuestion as jest.Mock).mockReset();
    (completeInterviewPracticeSession as jest.Mock).mockReset();
    (listNotes as jest.Mock).mockReset();
    (getMe as jest.Mock).mockResolvedValue({
      profileType: "PROFESSIONAL",
      planType: "PRO",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Backend Interview Prep",
      studyPackId: "sp-1",
      studyPackStatus: "STUDY_PACK_READY",
      courseProgram: "Software Engineering",
    });
    (listNotes as jest.Mock).mockResolvedValue([]);
    (getCollection as jest.Mock).mockResolvedValue({ id: "collection-1", items: [] });
  });

  it("renders the polished prestart setup rhythm", async () => {
    render(<InterviewPracticePage />);

    expect(await screen.findByRole("heading", { name: "Interview Practice" })).toBeInTheDocument();
    expect(screen.getByText("Scenario-based practice with a critique after every answer.")).toBeInTheDocument();
    expect(screen.getByText("Session length")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /5 questions/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /10 questions/i })).toBeInTheDocument();
    expect(screen.getByText("Soft timer, non-enforcing")).toBeInTheDocument();
    expect(screen.getByText("Scenario MCQ with per-answer critique")).toBeInTheDocument();
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
    expect(screen.queryByRole("dialog", { name: "Prepare for the part that isn't a quiz" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Unlock Interview Practice - Pro" }));

    expect(await screen.findByRole("dialog", { name: "Prepare for the part that isn't a quiz" })).toBeInTheDocument();
    expect(startInterviewPractice).not.toHaveBeenCalled();
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it("scopes collection launches to plan notes and preselects up to the Interview Practice cap", async () => {
    searchParamsMock = new URLSearchParams("collectionId=collection-1");
    (getCollection as jest.Mock).mockResolvedValue({
      id: "collection-1",
      items: [
        { noteId: "note-1", position: 0, studyPackStatus: "STUDY_PACK_READY", generatedQuizId: "quiz-1" },
        { noteId: "note-2", position: 1, studyPackStatus: "STUDY_PACK_READY", generatedQuizId: "quiz-2" },
        { noteId: "note-3", position: 2, studyPackStatus: "STUDY_PACK_READY", generatedQuizId: "quiz-3" },
        { noteId: "note-4", position: 3, studyPackStatus: "STUDY_PACK_READY", generatedQuizId: "quiz-4" },
      ],
    });
    (listNotes as jest.Mock).mockResolvedValue([
      { id: "note-2", title: "Plan Interview Two", courseProgram: "Software Engineering", subject: "Backend", studyPackStatus: "STUDY_PACK_READY" },
      { id: "note-3", title: "Plan Interview Three", courseProgram: "Software Engineering", subject: "Backend", studyPackStatus: "STUDY_PACK_READY" },
      { id: "note-4", title: "Plan Interview Four", courseProgram: "Software Engineering", subject: "Backend", studyPackStatus: "STUDY_PACK_READY" },
      { id: "note-9", title: "Outside Interview Note", courseProgram: "Software Engineering", subject: "Backend", studyPackStatus: "STUDY_PACK_READY" },
    ]);

    render(<InterviewPracticePage />);

    expect(await screen.findByRole("button", { name: /Plan Interview Two/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: /Plan Interview Three/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: /Plan Interview Four/ })).toHaveAttribute("aria-pressed", "false");
    expect(screen.queryByRole("button", { name: /Outside Interview Note/ })).not.toBeInTheDocument();
    expect(screen.getByText("3 notes · 5 questions")).toBeInTheDocument();
    expect(screen.getByText("Add up to 2 more notes from this plan.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Collection/ })).toHaveAttribute("href", "/collections/collection-1");
    expect(screen.queryByRole("button", { name: "Choose another mode" })).not.toBeInTheDocument();
  });

  it("falls back to the normal source picker when collection lookup fails", async () => {
    searchParamsMock = new URLSearchParams("collectionId=missing");
    (getCollection as jest.Mock).mockRejectedValue(new Error("Not found"));
    (listNotes as jest.Mock).mockResolvedValue([
      { id: "note-2", title: "Fallback Course Note", courseProgram: "Software Engineering", subject: "Backend", studyPackStatus: "STUDY_PACK_READY" },
      { id: "note-3", title: "Other Course Note", courseProgram: "Nursing", subject: "Backend", studyPackStatus: "STUDY_PACK_READY" },
    ]);

    render(<InterviewPracticePage />);

    expect(await screen.findByRole("button", { name: /Fallback Course Note/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Other Course Note/ })).not.toBeInTheDocument();
    expect(screen.getByText("1 note · 5 questions")).toBeInTheDocument();
  });

  it("attributes readiness-gap launches to Interview Practice", async () => {
    const question = {
      question: "Which answer is strongest?",
      choices: ["Option A", "Option B", "Option C", "Option D"],
      correctIndex: 0,
      concept: "System design",
      explanation: "Explanation",
    };
    (startInterviewPractice as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      status: "IN_PROGRESS",
      question,
      questionCount: 5,
      currentQuestionIndex: 0,
    });
    (answerInterviewPracticeQuestion as jest.Mock).mockResolvedValue({
      verdict: "STRONG",
      rationale: "Clear reasoning.",
      followUp: "Keep it concise.",
      nextQuestion: question,
    });
    (completeInterviewPracticeSession as jest.Mock).mockResolvedValue({
      band: "ALMOST_READY",
      scorePercentage: 80,
      correctAnswers: 4,
      totalQuestions: 5,
      strengths: [],
      gaps: [{ noteId: "note-gap", concept: "Caching strategy" }],
      talkingPoints: [],
      pacingNotes: [],
    });

    render(<InterviewPracticePage />);
    fireEvent.click(await screen.findByRole("button", { name: "Start Interview Practice" }));

    for (let index = 0; index < 5; index += 1) {
      fireEvent.click(await screen.findByRole("button", { name: "A. Option A" }));
      fireEvent.click(screen.getByRole("button", { name: "Submit Answer" }));
      const advanceLabel = index === 4 ? "Complete" : "Next Question";
      fireEvent.click(await screen.findByRole("button", { name: advanceLabel }));
    }

    fireEvent.click(await screen.findByRole("button", { name: "Caching strategy" }));
    expect(pushMock).toHaveBeenCalledWith(
      "/notes/note-gap/adaptive-practice?entry=interview-practice-gap",
    );
  });
});
