import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import OnboardingPage from "./page";
import {
  adoptStudyPlan,
  completeOnboarding,
  completeOnboardingProfileType,
  createNote,
  createStudyPackFromNote,
  generateNoteFromTopic,
  getMe,
  getMyPlan,
  getNote,
  listCollections,
  listPublicStudyPlans,
  trackAnalyticsEvent,
  updateLearningProfileContext,
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
  usePathname: () => "/onboarding",
}));

jest.mock("@/lib/route-guards", () => ({
  redirectToLoginWithCurrentDestination: jest.fn(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
  setAuthUser: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  adoptStudyPlan: jest.fn(),
  completeOnboarding: jest.fn(),
  completeOnboardingProfileType: jest.fn(),
  createNote: jest.fn(),
  createStudyPackFromNote: jest.fn(),
  generateNoteFromTopic: jest.fn(),
  getMe: jest.fn(),
  getMyPlan: jest.fn(),
  getNote: jest.fn(),
  isNoteGenerationLimitReachedError: (error: unknown) => error instanceof Error && error.message === "NOTE_GENERATION_LIMIT_REACHED",
  listCollections: jest.fn(),
  listPublicStudyPlans: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
  updateLearningProfileContext: jest.fn(),
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
    (getMyPlan as jest.Mock).mockReset();
    (generateNoteFromTopic as jest.Mock).mockReset();
    (createNote as jest.Mock).mockReset();
    (createStudyPackFromNote as jest.Mock).mockReset();
    (getNote as jest.Mock).mockReset();
    (completeOnboarding as jest.Mock).mockReset();
    (completeOnboardingProfileType as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (updateLearningProfileContext as jest.Mock).mockReset();
    (adoptStudyPlan as jest.Mock).mockReset();
    (listCollections as jest.Mock).mockReset();
    (listPublicStudyPlans as jest.Mock).mockReset();
    (listCollections as jest.Mock).mockResolvedValue([]);
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);

    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      email: "note@example.com",
      displayName: "Note",
      profileType: null,
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
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "FREE",
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
        noteGenerationsPerMonth: 5,
      },
      usage: {
        studyPacksUsed: 0,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
        noteGenerationsUsed: 0,
      },
      remaining: {
        studyPacksRemaining: 10,
        challengeQuizzesRemaining: 5,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
        noteGenerationsRemaining: 5,
      },
      features: {
        adaptivePracticeAvailable: false,
        difficultySelectionAvailable: false,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });
    (completeOnboarding as jest.Mock).mockResolvedValue({
      ...baseMe,
      profileType: "STUDENT",
      onboardingCompletedAt: "2026-04-27T00:05:00Z",
    });
    (completeOnboardingProfileType as jest.Mock).mockResolvedValue({
      ...baseMe,
      profileType: "STUDENT",
      onboardingCompletedAt: "2026-04-27T00:05:00Z",
    });
    (updateLearningProfileContext as jest.Mock).mockResolvedValue(baseMe);
  });

  function fillLearningProfile(learnerLevel = "COLLEGE", courseProgram = "Nursing") {
    fireEvent.change(screen.getByLabelText("Learner Level"), {
      target: { value: learnerLevel },
    });
    fireEvent.change(screen.getByLabelText("Course / Program"), {
      target: { value: courseProgram },
    });
  }

  it("redirects users who already completed onboarding", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      email: "note@example.com",
      displayName: "Note",
      profileType: "STUDENT",
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

  it("shows only profile-type setup for completed users missing profileType", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      email: "note@example.com",
      displayName: "Note",
      profileType: null,
      emailVerifiedAt: "2026-04-27T00:00:00Z",
      onboardingCompletedAt: "2026-04-27T00:05:00Z",
      role: "USER",
      planType: "FREE",
      accessToken: "token",
      refreshToken: "refresh",
      accessTokenExpiresAt: "2026-04-27T01:00:00Z",
      refreshTokenExpiresAt: "2026-05-27T01:00:00Z",
    });
    (getMe as jest.Mock).mockResolvedValue({
      ...baseMe,
      onboardingCompletedAt: "2026-04-27T00:05:00Z",
      profileType: null,
    });

    render(<OnboardingPage />);

    expect(await screen.findByText("Choose your profile type")).toBeInTheDocument();
    expect(screen.getByText("Profile setup")).toBeInTheDocument();
    expect(screen.queryByText("Step 1 of 5")).not.toBeInTheDocument();
    expect(screen.queryByText("Set up your learning profile")).not.toBeInTheDocument();

    fireEvent.click(screen.getByLabelText("Professional"));
    fireEvent.click(screen.getByRole("button", { name: "Save Profile Type" }));

    await waitFor(() => {
      expect(completeOnboardingProfileType).toHaveBeenCalledWith({ profileType: "PROFESSIONAL" });
    });
    expect(completeOnboarding).not.toHaveBeenCalled();
    expect(createNote).not.toHaveBeenCalled();
    expect(routerMock.replace).toHaveBeenCalledWith("/dashboard");
  });

  it("disables step two Continue when the learner level is cleared", async () => {
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("Set up your learning profile")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Course / Program"), {
      target: { value: "Nursing" },
    });
    // Selecting a profile now pre-defaults the learner level; clearing it re-triggers the required gate.
    fireEvent.change(screen.getByLabelText("Learner Level"), { target: { value: "" } });

    expect(screen.getByRole("button", { name: "Continue" })).toBeDisabled();
    expect(screen.getByText("Please select your learner level.")).toBeInTheDocument();
  });

  it("reframes learner level as default quiz difficulty for teachers", async () => {
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Teacher"));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText(
      "Required. This sets the default difficulty for quizzes you generate. You can change it per quiz.",
    )).toBeInTheDocument();
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

    expect(await screen.findByText("Set up your learning profile")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Learner Level"), {
      target: { value: "COLLEGE" },
    });
    fireEvent.change(screen.getByLabelText("Course / Program"), {
      target: { value: "AWS Certification" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("How do you want to start?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Generate a note" }));
    fireEvent.change(screen.getByPlaceholderText("Create a note about Newton’s Laws of Motion..."), {
      target: { value: "Newton's Laws of Motion" },
    });
    expect(screen.getByRole("button", { name: "Generate Study Pack →" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: /Generate Note/ }));

    const generatedNoteEditor = await screen.findByPlaceholderText("Your generated note will appear here.");
    expect(generatedNoteEditor).toHaveValue("Newton's Laws study content");
    expect(
      screen.getByText("Your note is ready. You can edit it before generating your Study Pack."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Generate Again" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Regenerate Note/i })).not.toBeInTheDocument();
    expect(createStudyPackFromNote).not.toHaveBeenCalled();

    fireEvent.change(generatedNoteEditor, {
      target: { value: "Edited Newton note content for onboarding so the study pack can start." },
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
    await waitFor(() => {
      expect(updateLearningProfileContext).toHaveBeenCalledWith("COLLEGE", "AWS Certification");
    });

    fireEvent.click(screen.getByRole("button", { name: "Continue Studying" }));

    expect(generateNoteFromTopic).toHaveBeenCalledWith("Newton's Laws of Motion");
    expect(createNote).toHaveBeenCalledWith({
      title: "Newton's Laws of Motion",
      targetProfileType: "STUDENT",
      content: "Edited Newton note content for onboarding so the study pack can start.",
    });
    expect(createStudyPackFromNote).toHaveBeenCalledWith("note-1", { autoApplyMetadata: true });
    expect(routerMock.push).toHaveBeenCalledWith("/study-packs/study-pack-1");
  });

  it("surfaces a recommended adopt card on completion when the track has a published plan", async () => {
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
    (adoptStudyPlan as jest.Mock).mockResolvedValue({
      collectionId: "personal-plan-1",
      copiedCount: 4,
      skippedCount: 0,
      alreadyAdopted: false,
    });
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([
      {
        id: "source-plan-1",
        title: "AWS Certification Plan",
        description: "A curated AWS Certification review sequence.",
        visibility: "PUBLIC" as const,
        courseProgram: "AWS Certification",
        sourcePlanId: null,
        itemCount: 4,
        notesPracticed: 0,
        createdAt: "2026-06-01T00:00:00Z",
        updatedAt: "2026-06-02T00:00:00Z",
      },
    ]);

    render(<OnboardingPage />);

    expect(await screen.findByText("Welcome to NoteLib. Let's set things up.")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Student"));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("Set up your learning profile")).toBeInTheDocument();
    fillLearningProfile("COLLEGE", "AWS Certification");
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("How do you want to start?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Generate a note" }));
    fireEvent.change(screen.getByPlaceholderText("Create a note about Newton’s Laws of Motion..."), {
      target: { value: "Newton's Laws of Motion" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Generate Note/ }));

    const generatedNoteEditor = await screen.findByPlaceholderText("Your generated note will appear here.");
    fireEvent.change(generatedNoteEditor, {
      target: { value: "Edited Newton note content for onboarding so the study pack can start." },
    });
    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack →" }));

    expect(await screen.findByText("Your Study Pack is ready.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("You just started your study loop.")).toBeInTheDocument();
    expect(await screen.findByText("AWS Certification Plan")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Start this Study Plan" }));

    await waitFor(() => {
      expect(adoptStudyPlan).toHaveBeenCalledWith("source-plan-1");
    });
  });

  it("switches between modes and only shows the active input surface", async () => {
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByText("Set up your learning profile")).toBeInTheDocument();
    fillLearningProfile();
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    fireEvent.click(screen.getByRole("button", { name: "Generate a note" }));
    expect(screen.getByPlaceholderText("Create a note about Newton’s Laws of Motion...")).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("Paste or write your notes here...")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Write or paste my own note" }));
    expect(screen.getByPlaceholderText("Paste or write your notes here...")).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("Create a note about Newton’s Laws of Motion...")).not.toBeInTheDocument();
  });

  it("disables topic note generation at the free plan limit and opens the paywall", async () => {
    (getMyPlan as jest.Mock).mockResolvedValue({
      plan: "FREE",
      limits: {
        studyPacksPerMonth: 10,
        challengeQuizzesPerMonth: 5,
        adaptivePracticePerMonth: 0,
        ocrPerMonth: 20,
        noteGenerationsPerMonth: 5,
      },
      usage: {
        studyPacksUsed: 0,
        challengeQuizzesUsed: 0,
        adaptivePracticeUsed: 0,
        ocrUsed: 0,
        noteGenerationsUsed: 5,
      },
      remaining: {
        studyPacksRemaining: 10,
        challengeQuizzesRemaining: 5,
        adaptivePracticeRemaining: 0,
        ocrRemaining: 20,
        noteGenerationsRemaining: 0,
      },
      features: {
        adaptivePracticeAvailable: false,
        difficultySelectionAvailable: false,
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });

    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByText("Set up your learning profile")).toBeInTheDocument();
    fillLearningProfile();
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    fireEvent.click(screen.getByRole("button", { name: "Generate a note" }));

    expect(screen.getByText("You've reached your topic note generation limit for this month.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Generate Note" })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: "Get Plus" }));

    expect(await screen.findByText("You've reached your note generation limit")).toBeInTheDocument();
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

    fireEvent.click(await screen.findByLabelText("Exam Reviewer"));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByLabelText("When is your exam? (optional)")).toBeInTheDocument();
    fillLearningProfile("BOARD_EXAM_REVIEW", "Pharmacy");
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    fireEvent.click(await screen.findByRole("button", { name: "Write or paste my own note" }));
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

  it("shows Professional profile option with learner context and no exam date field", async () => {
    render(<OnboardingPage />);

    expect(await screen.findByText("🎓 Student")).toBeInTheDocument();
    expect(screen.getByText("📋 Exam Reviewer")).toBeInTheDocument();
    expect(screen.getByText("🏫 Teacher")).toBeInTheDocument();
    expect(screen.getByText("💼 Professional")).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText("Professional"));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByRole("group", { name: "Recommended for Professionals" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Professional" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Personal Learning" })).toBeInTheDocument();
    expect(screen.getByLabelText("Course / Program")).toBeInTheDocument();
    expect(screen.queryByLabelText("When is your exam? (optional)")).not.toBeInTheDocument();

    fillLearningProfile("PROFESSIONAL", "Medicine");
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByText("How do you want to start?")).toBeInTheDocument();
  });

  it("keeps the Study Pack CTA disabled until the own-note minimum is met", async () => {
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Teacher"));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByText("Set up your learning profile")).toBeInTheDocument();
    fillLearningProfile("GRADE_SCHOOL", "Grade 5 Science");
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    fireEvent.click(await screen.findByRole("button", { name: "Write or paste my own note" }));
    fireEvent.change(screen.getByPlaceholderText("Paste or write your notes here..."), {
      target: { value: "Short onboarding note draft." },
    });

    expect(screen.getByText(/\/ 50 minimum$/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Generate Study Pack →" })).toBeDisabled();
  });
});
