import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import OnboardingPage from "./page";
import {
  completeOnboarding,
  createNote,
  createStudyPackFromNote,
  generateNoteFromTopic,
  getMe,
  getNote,
  trackAnalyticsEvent,
} from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";

const routerMock = {
  push: jest.fn(),
  replace: jest.fn(),
  refresh: jest.fn(),
};

const baseMe = {
  id: "user-1",
  email: "note@example.com",
  pendingEmail: null,
  firstName: "Note",
  lastName: null,
  displayName: "Note",
  bio: null,
  learnerLevel: null,
  courseProgram: null,
  publicProfileVisible: true,
  countryCode: null,
  profileType: null,
  examDate: null,
  engagementMode: "FOCUSED" as const,
  inactivityRemindersEnabled: false,
  weakConceptRemindersEnabled: false,
  themePreference: "SYSTEM" as const,
  emailVerifiedAt: "2026-04-27T00:00:00Z",
  onboardingCompletedAt: null,
  productOnboardingCompletedAt: null,
  studyPackCount: 0,
  role: "USER" as const,
  status: "ACTIVE" as const,
  planType: "FREE" as const,
  subscription: {
    cancelAtPeriodEnd: false,
    premiumEndsAt: null,
    cancelledAt: null,
  },
};

const readyNote = {
  id: "note-1",
  title: "Newton's Laws of Motion",
  subject: null,
  courseProgram: null,
  targetProfileType: "STUDENT" as const,
  tags: [],
  content: "Newton's Laws study content",
  visibility: "PRIVATE" as const,
  createdAt: "2026-04-27T00:00:00Z",
  updatedAt: "2026-04-27T00:00:00Z",
  copiedFromNoteId: null,
  copiedFromUserId: null,
  copiedFromTitle: null,
  copiedFromPublic: false,
  copiedAt: null,
  studyPackId: "study-pack-1",
  studyPackStatus: "STUDY_PACK_READY" as const,
  summary: "Force changes motion. Mass resists acceleration. These laws explain how objects move.",
  keyConcepts: ["Inertia", "Force", "Mass", "Acceleration", "Momentum"],
  quiz: [
    {
      question: "Which law explains inertia?",
      choices: ["First law", "Second law", "Third law"],
      correctIndex: 0,
      explanation: "The first law defines inertia.",
    },
  ],
  generatedQuiz: null,
  quizCount: 1,
  quickReviewAvailable: true,
  challengeQuizAvailable: true,
  adaptivePracticeAvailable: false,
  difficultySelectionAvailable: false,
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/route-guards", () => ({
  redirectToLoginWithCurrentDestination: jest.fn(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
  setAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  completeOnboarding: jest.fn(),
  createNote: jest.fn(),
  createStudyPackFromNote: jest.fn(),
  generateNoteFromTopic: jest.fn(),
  getMe: jest.fn(),
  getNote: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

describe("OnboardingPage", () => {
  beforeEach(() => {
    routerMock.push.mockReset();
    routerMock.replace.mockReset();
    routerMock.refresh.mockReset();
    window.localStorage.clear();

    (getAuthUser as jest.Mock).mockReset();
    (setAuthUser as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (generateNoteFromTopic as jest.Mock).mockReset();
    (createNote as jest.Mock).mockReset();
    (createStudyPackFromNote as jest.Mock).mockReset();
    (getNote as jest.Mock).mockReset();
    (completeOnboarding as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();

    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      email: "note@example.com",
      displayName: "Note",
      emailVerifiedAt: "2026-04-27T00:00:00Z",
      onboardingCompletedAt: null,
      role: "USER",
      planType: "FREE",
      accessToken: "token",
      refreshToken: "refresh",
      accessTokenExpiresAt: "2026-04-27T01:00:00Z",
      refreshTokenExpiresAt: "2026-05-27T01:00:00Z",
    });
    (getMe as jest.Mock).mockResolvedValue(baseMe);
    (completeOnboarding as jest.Mock).mockResolvedValue({
      ...baseMe,
      profileType: "STUDENT",
      onboardingCompletedAt: "2026-04-27T00:05:00Z",
    });
  });

  it("redirects users who already completed onboarding", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      email: "note@example.com",
      displayName: "Note",
      emailVerifiedAt: "2026-04-27T00:00:00Z",
      onboardingCompletedAt: "2026-04-27T00:05:00Z",
      role: "USER",
      planType: "FREE",
      accessToken: "token",
      refreshToken: "refresh",
      accessTokenExpiresAt: "2026-04-27T01:00:00Z",
      refreshTokenExpiresAt: "2026-05-27T01:00:00Z",
    });

    render(<OnboardingPage />);

    await waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalledWith("/dashboard");
    });
    expect(getMe).not.toHaveBeenCalled();
  });

  it("completes the generate-note path and opens the first Study Pack", async () => {
    (generateNoteFromTopic as jest.Mock).mockResolvedValue({
      content: "Newton's Laws study content",
    });
    (createNote as jest.Mock).mockResolvedValue({
      ...readyNote,
      studyPackId: null,
      studyPackStatus: "DRAFT",
      summary: null,
      keyConcepts: [],
      quiz: [],
      title: null,
    });
    (createStudyPackFromNote as jest.Mock).mockResolvedValue(readyNote);
    (completeOnboarding as jest.Mock).mockResolvedValue({
      ...baseMe,
      profileType: "STUDENT",
      onboardingCompletedAt: "2026-04-27T00:05:00Z",
    });

    render(<OnboardingPage />);

    expect(await screen.findByText("Welcome to NoteLib. Let's set things up.")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Student"));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("What's your goal right now?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Understand a topic in depth" }));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("How do you want to start?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Generate a note/ }));
    fireEvent.change(screen.getByPlaceholderText("Create a note about Newton’s Laws of Motion..."), {
      target: { value: "Newton's Laws of Motion" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack →" }));

    expect(await screen.findByText("Your Study Pack is ready.")).toBeInTheDocument();
    expect(screen.getByText("Inertia")).toBeInTheDocument();
    expect(screen.getByText("+1 more")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("You just started your study loop.")).toBeInTheDocument();
    await waitFor(() => {
      expect(completeOnboarding).toHaveBeenCalledWith({
        profileType: "STUDENT",
        examDate: null,
      });
    });

    fireEvent.click(screen.getByRole("button", { name: "Continue Studying" }));

    expect(generateNoteFromTopic).toHaveBeenCalledWith("Newton's Laws of Motion");
    expect(createNote).toHaveBeenCalledWith({
      title: "Newton's Laws of Motion",
      targetProfileType: "STUDENT",
      content: "Newton's Laws study content",
    });
    expect(createStudyPackFromNote).toHaveBeenCalledWith("note-1");
    expect(routerMock.push).toHaveBeenCalledWith("/study-packs/study-pack-1");
  });

  it("allows board takers to finish without an exam date", async () => {
    const longNote = "Board review content ".repeat(4);
    (createNote as jest.Mock).mockResolvedValue({
      ...readyNote,
      id: "note-2",
      title: null,
      content: longNote,
      studyPackId: null,
      studyPackStatus: "DRAFT",
      summary: null,
      keyConcepts: [],
      quiz: [],
    });
    (createStudyPackFromNote as jest.Mock).mockResolvedValue({
      ...readyNote,
      id: "note-2",
      title: null,
      content: longNote,
      targetProfileType: "BOARD_TAKER",
      studyPackId: "study-pack-2",
    });
    (completeOnboarding as jest.Mock).mockResolvedValue({
      ...baseMe,
      profileType: "BOARD_EXAM",
      onboardingCompletedAt: "2026-04-27T00:05:00Z",
    });

    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Board Taker"));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByLabelText("When is your exam? (optional)")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Practice under exam-style conditions" }));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    fireEvent.click(await screen.findByRole("button", { name: /Write or paste my own note/ }));
    fireEvent.change(screen.getByPlaceholderText("Paste or write your notes here..."), {
      target: { value: longNote },
    });
    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack →" }));

    expect(await screen.findByText("Your Study Pack is ready.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    await waitFor(() => {
      expect(completeOnboarding).toHaveBeenCalledWith({
        profileType: "BOARD_EXAM",
        examDate: null,
      });
    });
  });

  it("keeps the Study Pack CTA disabled until the own-note minimum is met", async () => {
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Teacher"));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    fireEvent.click(await screen.findByRole("button", { name: "Create study material for students" }));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    fireEvent.click(await screen.findByRole("button", { name: /Write or paste my own note/ }));
    fireEvent.change(screen.getByPlaceholderText("Paste or write your notes here..."), {
      target: { value: "Short onboarding note draft." },
    });

    expect(screen.getByText(/\/ 50 minimum$/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Generate Study Pack →" })).toBeDisabled();
  });
});
