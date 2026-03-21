import { render, screen, waitFor } from "@testing-library/react";
import ChallengeQuizPage from "./page";
import { getAuthUser } from "@/lib/auth";
import { getInProgressChallengeQuizSession, getNote } from "@/lib/api";

const pushMock = jest.fn();
const replaceMock = jest.fn();
const routerMock = {
  push: pushMock,
  replace: replaceMock,
};
const paramsMock = { id: "note-1" };

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/notes/note-1/challenge-quiz",
  useParams: () => paramsMock,
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  completeChallengeQuizSession: jest.fn(),
  getInProgressChallengeQuizSession: jest.fn(),
  getMyStudyPack: jest.fn(),
  getNote: jest.fn(),
  isEmailNotVerifiedError: () => false,
  startChallengeQuizSession: jest.fn(),
  updateChallengeQuizSessionProgress: jest.fn(),
}));

describe("ChallengeQuizPage", () => {
  beforeEach(() => {
    pushMock.mockReset();
    replaceMock.mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (getNote as jest.Mock).mockReset();
    (getInProgressChallengeQuizSession as jest.Mock).mockReset();
  });

  it("loads note/session once and does not loop initialization calls", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      planType: "PREMIUM",
      emailVerifiedAt: "2026-03-21T09:00:00Z",
    });
    (getNote as jest.Mock).mockResolvedValue({
      id: "note-1",
      title: "Challenge Note",
      subject: "Biology",
      tags: ["cells"],
      content: "content",
      visibility: "PRIVATE",
      createdAt: "2026-03-21T10:00:00Z",
      updatedAt: "2026-03-21T10:30:00Z",
      copiedFromNoteId: null,
      copiedFromUserId: null,
      copiedFromTitle: null,
      copiedFromPublic: false,
      copiedAt: null,
      studyPackId: "sp-1",
      studyPackStatus: "STUDY_PACK_READY",
      summary: "Summary",
      keyConcepts: ["Concept"],
      quiz: [],
      quizCount: 0,
      quickReviewAvailable: true,
      challengeQuizAvailable: true,
      adaptivePracticeAvailable: false,
    });
    (getInProgressChallengeQuizSession as jest.Mock).mockResolvedValue({
      sessionId: "session-1",
      studyPackId: "sp-1",
      title: "Challenge Note",
      totalQuestions: 1,
      timeLimitSeconds: 600,
      usedThisMonth: 0,
      monthlyLimit: 50,
      quiz: [
        {
          question: "What powers the cell?",
          choices: ["A", "B", "C", "D"],
          answer: "A",
          concept: "Concept",
          explanation: "Explanation",
        },
      ],
      currentQuestionIndex: 0,
      sessionState: {
        selectedChoices: {},
        timerStartedAtEpochSeconds: Math.floor(Date.now() / 1000),
      },
    });

    render(<ChallengeQuizPage />);

    await screen.findByText("Challenge Quiz in progress");
    await waitFor(() => {
      expect(getNote).toHaveBeenCalledTimes(1);
      expect(getInProgressChallengeQuizSession).toHaveBeenCalledTimes(1);
    });
    expect(replaceMock).not.toHaveBeenCalled();
  });
});
