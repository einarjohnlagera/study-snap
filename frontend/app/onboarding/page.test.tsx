import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import OnboardingPage from "./page";
import {
  adoptGoal,
  adoptStudyPlan,
  completeOnboarding,
  completeOnboardingProfileType,
  createNote,
  createStudyPackFromNote,
  generateNoteFromTopic,
  getMe,
  getMyPlan,
  getNote,
  getOfficialStudyPlanWishlistStatus,
  listCollections,
  listPublicStudyPlans,
  requestOfficialStudyPlan,
  trackAnalyticsEvent,
  updateLearningProfileContext,
} from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";
import { shouldShowOfficialPlanRequestAction } from "@/lib/onboarding-v2";

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
  adoptGoal: jest.fn(),
  adoptStudyPlan: jest.fn(),
  completeOnboarding: jest.fn(),
  completeOnboardingProfileType: jest.fn(),
  createNote: jest.fn(),
  createStudyPackFromNote: jest.fn(),
  generateNoteFromTopic: jest.fn(),
  getMe: jest.fn(),
  getMyPlan: jest.fn(),
  getNote: jest.fn(),
  getOfficialStudyPlanWishlistStatus: jest.fn(),
  isNoteGenerationLimitReachedError: (error: unknown) => error instanceof Error && error.message === "NOTE_GENERATION_LIMIT_REACHED",
  listCollections: jest.fn(),
  listPublicStudyPlans: jest.fn(),
  requestOfficialStudyPlan: jest.fn(),
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
    (getOfficialStudyPlanWishlistStatus as jest.Mock).mockReset();
    (completeOnboarding as jest.Mock).mockReset();
    (completeOnboardingProfileType as jest.Mock).mockReset();
    (trackAnalyticsEvent as jest.Mock).mockReset();
    (updateLearningProfileContext as jest.Mock).mockReset();
    (adoptStudyPlan as jest.Mock).mockReset();
    (adoptGoal as jest.Mock).mockReset();
    (listCollections as jest.Mock).mockReset();
    (listPublicStudyPlans as jest.Mock).mockReset();
    (requestOfficialStudyPlan as jest.Mock).mockReset();
    (listCollections as jest.Mock).mockResolvedValue([]);
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);
    (getOfficialStudyPlanWishlistStatus as jest.Mock).mockResolvedValue({ requested: false });
    (requestOfficialStudyPlan as jest.Mock).mockResolvedValue({ requested: true });

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

  async function completeLearningProfile(learnerLevel = "COLLEGE", courseProgram = "Nursing") {
    fireEvent.change(screen.getByLabelText("Course / Program"), {
      target: { value: courseProgram },
    });
    await clickContinue();
    // Profile-agnostic: the heading varies by profile type, so assert the screen by its control.
    expect(await screen.findByLabelText("Learner Level")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Learner Level"), {
      target: { value: learnerLevel },
    });
    // Selecting records; Continue advances. Never auto-advance a <select>: re-choosing the value that
    // is already selected fires no change event, which is what made this screen a dead end.
    await clickContinue();
    expect(await screen.findByText("What would you like to do first?")).toBeInTheDocument();
  }

  const clickContinue = async () => {
    const button = await screen.findByRole("button", { name: "Continue" });
    await waitFor(() => expect(button).not.toBeDisabled());
    fireEvent.click(button);
  };

  const chooseReadyMadeIntent = async () => {
    fireEvent.click(await screen.findByRole("button", { name: /Study with ready-made materials|Use existing teaching and study materials/ }));
  };

  /**
   * Step 3 is now the first-intent step. Reaching the note-input screen means choosing the "own notes"
   * door first -- the input-method question (generate vs paste) is a sub-choice inside that branch.
   */
  const chooseOwnNotesIntent = async () => {
    fireEvent.click(await screen.findByRole("button", { name: /Build from my own notes|Create teaching or study materials/ }));
  };

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

    const { unmount } = render(<OnboardingPage />);

    await waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalledWith("/dashboard");
    });
    expect(getMe).not.toHaveBeenCalled();

    unmount();

    expect(trackAnalyticsEvent).not.toHaveBeenCalledWith(
      expect.objectContaining({ eventType: "ONBOARDING_V2_ABANDONED" }),
    );
  });

  it("tracks abandonment once on unmount after multiple in-progress step changes", async () => {
    const { unmount } = render(<OnboardingPage />);

    await waitFor(() => {
      expect(trackAnalyticsEvent).toHaveBeenCalledWith(
        expect.objectContaining({ eventType: "ONBOARDING_V2_STARTED" }),
      );
    });

    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();
    expect(await screen.findByText("What are you studying?")).toBeInTheDocument();

    await completeLearningProfile();
    await chooseOwnNotesIntent();
    expect(await screen.findByText("How do you want to begin your first note?")).toBeInTheDocument();

    expect(trackAnalyticsEvent).not.toHaveBeenCalledWith(
      expect.objectContaining({ eventType: "ONBOARDING_V2_ABANDONED" }),
    );

    unmount();

    const abandonmentEvents = (trackAnalyticsEvent as jest.Mock).mock.calls
      .map(([event]) => event)
      .filter((event) => event.eventType === "ONBOARDING_V2_ABANDONED");
    expect(abandonmentEvents).toEqual([
      expect.objectContaining({
        metadata: { last_step: 5 },
      }),
    ]);
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
    expect(screen.queryByText("Step 1 of 8")).not.toBeInTheDocument();
    expect(screen.queryByText("What are you studying?")).not.toBeInTheDocument();

    fireEvent.click(screen.getByLabelText("Professional"));
    fireEvent.click(screen.getByRole("button", { name: "Save Profile Type" }));

    await waitFor(() => {
      expect(completeOnboardingProfileType).toHaveBeenCalledWith({ profileType: "PROFESSIONAL" });
    });
    expect(completeOnboarding).not.toHaveBeenCalled();
    expect(createNote).not.toHaveBeenCalled();
    expect(routerMock.replace).toHaveBeenCalledWith("/dashboard");
  });

  it("advances by tap on the safe screens, by Continue on the rest, and Back never strands", async () => {
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();
    expect(await screen.findByText("Step 2 of 8")).toBeInTheDocument();

    // Course / Program is typed, so it cannot auto-advance -- the system cannot know you are done.
    fireEvent.change(screen.getByLabelText("Course / Program"), { target: { value: "Nursing" } });
    expect(screen.getByText("Step 2 of 8")).toBeInTheDocument();
    await clickContinue();
    expect(await screen.findByText("Step 3 of 8")).toBeInTheDocument();

    // Learner Level is a <select>: re-choosing the value already selected fires no change event, so
    // auto-advancing here made the screen a dead end for anyone arriving with a value set.
    fireEvent.change(screen.getByLabelText("Learner Level"), { target: { value: "COLLEGE" } });
    expect(screen.getByText("Step 3 of 8")).toBeInTheDocument();
    await clickContinue();
    expect(await screen.findByText("Step 4 of 8")).toBeInTheDocument();

    // THE REGRESSION. Back onto Learner Level with a value already selected must still lead forward.
    fireEvent.click(screen.getByRole("button", { name: "Back" }));
    expect(await screen.findByText("Step 3 of 8")).toBeInTheDocument();
    expect((screen.getByLabelText("Learner Level") as HTMLSelectElement).value).toBe("COLLEGE");
    await clickContinue();
    expect(await screen.findByText("Step 4 of 8")).toBeInTheDocument();

    // Step 4 is tap-to-advance and has no Continue: both doors stay inside onboarding.
    expect(screen.queryByRole("button", { name: "Continue" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Build from my own notes/ }));
    expect(await screen.findByText("Step 5 of 8")).toBeInTheDocument();

    // Step 5's input method is tap-to-advance for the same reason.
    expect(screen.queryByRole("button", { name: "Continue" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Create a note" }));
    expect(await screen.findByText("Step 6 of 8")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Back" }));
    expect(await screen.findByText("Step 5 of 8")).toBeInTheDocument();
    expect(screen.getByText("How do you want to begin your first note?")).toBeInTheDocument();
    // The already-chosen option must still lead forward on a card screen too.
    fireEvent.click(screen.getByRole("button", { name: "Write or paste my own note" }));
    expect(await screen.findByText("Step 6 of 8")).toBeInTheDocument();

    await waitFor(() => {
      const viewed = (trackAnalyticsEvent as jest.Mock).mock.calls
        .map(([event]) => event)
        .filter((event) => event.eventType === "ONBOARDING_V2_STEP_VIEWED")
        .map((event) => event.metadata.step_name);
      expect(viewed).toEqual(expect.arrayContaining([
        "profile",
        "course-program",
        "learner-level",
        "first-intent",
        "input-method",
        "note",
      ]));
    });
  });

  it("pre-fills the learner level from the chosen profile type, and re-defaults on a switch", async () => {
    render(<OnboardingPage />);

    // Exam Reviewer arrives at Screen 3 with Board Exam Review already chosen, and Continue enabled --
    // there is nothing to select before moving on.
    fireEvent.click(await screen.findByLabelText("Exam Reviewer"));
    await clickContinue();
    // C9: each profile type gets its own question. An exam reviewer is not "studying".
    expect(await screen.findByText("What are you reviewing for?")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Course / Program"), { target: { value: "Nursing" } });
    await clickContinue();
    // Profile-agnostic: the heading varies by profile type, so assert the screen by its control.
    expect(await screen.findByLabelText("Learner Level")).toBeInTheDocument();
    expect((screen.getByLabelText("Learner Level") as HTMLSelectElement).value).toBe("BOARD_EXAM_REVIEW");
    expect(screen.getByRole("button", { name: "Continue" })).not.toBeDisabled();

    // Switching profile type re-defaults: the previous level was chosen for a different kind of learner.
    fireEvent.click(screen.getByRole("button", { name: "Back" }));
    // Still an Exam Reviewer on the way back, so Screen 2 still asks the reviewer's question.
    expect(await screen.findByText("What are you reviewing for?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Back" }));
    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();
    await clickContinue();
    // Profile-agnostic: the heading varies by profile type, so assert the screen by its control.
    expect(await screen.findByLabelText("Learner Level")).toBeInTheDocument();
    expect((screen.getByLabelText("Learner Level") as HTMLSelectElement).value).toBe("COLLEGE");
  });

  it("renders a migrated pre-split draft at the earliest unanswered screen with answers intact", async () => {
    window.localStorage.setItem("notelib.onboarding-v2:user-1", JSON.stringify({
      startedAtMs: Date.now(),
      currentStep: 5,
      profileType: "STUDENT",
      learnerLevel: null,
      courseProgram: "Nursing",
      intent: "own_notes",
      reviewSetAvailable: false,
      inputMethod: "own_note",
      topic: "",
      noteContent: "A saved note answer",
      generatedNoteReady: false,
      noteId: null,
      studyPackId: null,
    }));

    render(<OnboardingPage />);

    // Profile-agnostic: the heading varies by profile type, so assert the screen by its control.
    expect(await screen.findByLabelText("Learner Level")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Back" }));
    expect(await screen.findByLabelText("Course / Program")).toHaveValue("Nursing");
  });

  it("keeps course/program on an explicit disabled Continue until it is answered", async () => {
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();

    expect(await screen.findByText("What are you studying?")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continue" })).toBeDisabled();
    expect(screen.getByLabelText("Course / Program")).toHaveAttribute("aria-autocomplete", "list");
  });

  it("reframes learner level as default quiz difficulty for teachers", async () => {
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Teacher"));
    await clickContinue();
    fireEvent.change(screen.getByLabelText("Course / Program"), { target: { value: "Nursing" } });
    await clickContinue();

    // The "Required." prefix went with the typography pass -- the control's own required state carries
    // that. What must survive is the REFRAMING: for a teacher this field means quiz difficulty, not
    // their own study level, and that is information the heading does not give.
    expect(await screen.findByText(
      "This sets the default difficulty for quizzes you generate. You can change it per quiz.",
    )).toBeInTheDocument();
  });

  it("adopts a qualifying standalone official Review Set and lands on its detail page, without generation", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([
      {
        id: "source-plan-1",
        title: "LET Official Review Set",
        description: "A curated LET review sequence.",
        visibility: "PUBLIC",
        courseProgram: "LET",
        sourcePlanId: null,
        itemCount: 3,
        readyCount: 2,
        childCount: 0,
        notesPracticed: 0,
        createdAt: "2026-07-01T00:00:00Z",
        updatedAt: "2026-07-01T00:00:00Z",
      },
    ]);
    (adoptStudyPlan as jest.Mock).mockResolvedValue({
      collectionId: "personal-plan-1",
      copiedCount: 3,
      skippedCount: 0,
      alreadyAdopted: false,
    });
    (completeOnboarding as jest.Mock).mockResolvedValue({
      ...baseMe,
      profileType: "BOARD_EXAM",
      onboardingCompletedAt: "2026-07-23T00:05:00Z",
    });

    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Exam Reviewer"));
    await clickContinue();
    await completeLearningProfile("COLLEGE", "LET");

    await chooseReadyMadeIntent();
    expect(await screen.findByText("You're preparing for LET.")).toBeInTheDocument();
    expect(await screen.findByText("2 of 3 notes practice-ready")).toBeInTheDocument();
    expect(screen.queryByText("How do you want to begin your first note?")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Create a Note" })).not.toBeInTheDocument();
    // The adopt screen is Step 5 and says so. It used to claim "8 of 8" to signal terminal, which read
    // as a bug -- adopting a plan simply finishes onboarding early.
    expect(screen.getByText("Step 5 of 8")).toBeInTheDocument();

    fireEvent.click(await screen.findByRole("button", { name: "Start this Review Set" }));

    await waitFor(() => {
      expect(adoptStudyPlan).toHaveBeenCalledWith("source-plan-1");
      expect(completeOnboarding).toHaveBeenCalledWith({
        profileType: "BOARD_EXAM",
        examDate: null,
      });
      expect(routerMock.push).toHaveBeenCalledWith("/collections/personal-plan-1");
    });
    expect(createNote).not.toHaveBeenCalled();
    expect(createStudyPackFromNote).not.toHaveBeenCalled();
    expect(generateNoteFromTopic).not.toHaveBeenCalled();

    const trackedEventTypes = (trackAnalyticsEvent as jest.Mock).mock.calls.map(([event]) => event.eventType);
    expect(trackedEventTypes.filter((eventType) => eventType === "ONBOARDING_V2_PRACTICE_FIRST_ELIGIBLE")).toHaveLength(1);
    expect(trackedEventTypes.filter((eventType) => eventType === "ONBOARDING_V2_PRACTICE_FIRST_PLAN_ADOPTED")).toHaveLength(1);
  });

  it("adopts a qualifying Goal-shaped Review Set and lands on the adopted Goal's detail page", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([
      {
        id: "source-goal-1",
        title: "LET Official Goal",
        description: "A curated LET review sequence.",
        visibility: "PUBLIC",
        courseProgram: "LET",
        sourcePlanId: null,
        itemCount: 6,
        readyCount: 4,
        childCount: 2,
        notesPracticed: 0,
        createdAt: "2026-07-01T00:00:00Z",
        updatedAt: "2026-07-01T00:00:00Z",
      },
    ]);
    (adoptGoal as jest.Mock).mockResolvedValue({
      goalCollectionId: "personal-goal-1",
      adoptedSubjectCount: 2,
      skippedSubjectCount: 0,
      totalNotesCopied: 6,
      totalNotesSkipped: 0,
      alreadyAdopted: false,
    });
    (completeOnboarding as jest.Mock).mockResolvedValue({
      ...baseMe,
      profileType: "BOARD_EXAM",
      onboardingCompletedAt: "2026-07-23T00:05:00Z",
    });

    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Exam Reviewer"));
    await clickContinue();
    await completeLearningProfile("COLLEGE", "LET");
    await chooseReadyMadeIntent();
    fireEvent.click(await screen.findByRole("button", { name: "Start this Goal" }));

    await waitFor(() => {
      expect(adoptGoal).toHaveBeenCalledWith("source-goal-1");
      expect(routerMock.push).toHaveBeenCalledWith("/collections/personal-goal-1");
    });
  });

  it.each([
    ["Student", "STUDENT"],
    ["Teacher", "TEACHER"],
    ["Professional", "PROFESSIONAL"],
  ])("still reaches the create-first input screen for %s via the own-notes intent", async (profileLabel) => {
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText(profileLabel));
    await clickContinue();
    await completeLearningProfile();

    await chooseOwnNotesIntent();
    expect(await screen.findByText("How do you want to begin your first note?")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create a note" })).toBeInTheDocument();
    // Availability is now resolved for EVERY profile type, not only BOARD_EXAM -- that is the one
    // structural eligibility change in the Intent Router. This previously asserted the opposite.
    await waitFor(() => expect(listPublicStudyPlans).toHaveBeenCalled());
  });

  it("falls through to the unchanged input step when a published Board Review Set has no ready notes", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([
      {
        id: "source-plan-1",
        title: "LET Official Review Set",
        description: "A curated LET review sequence.",
        visibility: "PUBLIC",
        courseProgram: "LET",
        sourcePlanId: null,
        itemCount: 3,
        readyCount: 0,
        childCount: 0,
        notesPracticed: 0,
        createdAt: "2026-07-01T00:00:00Z",
        updatedAt: "2026-07-01T00:00:00Z",
      },
    ]);

    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Exam Reviewer"));
    await clickContinue();
    await completeLearningProfile("COLLEGE", "LET");

    await chooseOwnNotesIntent();
    expect(await screen.findByText("How do you want to begin your first note?")).toBeInTheDocument();
    expect(screen.queryByText("You're preparing for LET.")).not.toBeInTheDocument();
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
    await clickContinue();

    expect(await screen.findByText("What are you studying?")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Course / Program"), {
      target: { value: "AWS Certification" },
    });
    await clickContinue();
    fireEvent.change(screen.getByLabelText("Learner Level"), {
      target: { value: "COLLEGE" },
    });
    await clickContinue();

    await chooseOwnNotesIntent();
    expect(await screen.findByText("How do you want to begin your first note?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Create a note" }));
    fireEvent.change(screen.getByPlaceholderText("Create a note about Newton’s Laws of Motion..."), {
      target: { value: "Newton's Laws of Motion" },
    });
    expect(screen.getByRole("button", { name: "Generate Study Pack →" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: /Create a Note/ }));

    const generatedNoteEditor = await screen.findByPlaceholderText("Your note will appear here.");
    expect(generatedNoteEditor).toHaveValue("Newton's Laws study content");
    expect(
      screen.getByText("Your note is ready. You can edit it before generating your Study Pack."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Create Again" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Regenerate Note/i })).not.toBeInTheDocument();
    expect(createStudyPackFromNote).not.toHaveBeenCalled();

    fireEvent.change(generatedNoteEditor, {
      target: { value: "Edited Newton note content for onboarding so the study pack can start." },
    });
    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack →" }));

    expect(await screen.findByText("Your Study Pack is ready.")).toBeInTheDocument();
    expect(screen.getByText("Saved to your library — yours to quiz against anytime.")).toBeInTheDocument();
    expect(screen.getByText("Going back will start a new Study Pack. Your current one will be saved.")).toBeInTheDocument();
    expect(screen.getByText("Inertia")).toBeInTheDocument();
    expect(screen.getByText("+1 more")).toBeInTheDocument();

    await clickContinue();

    expect(await screen.findByText("Your Newton's Laws of Motion Study Pack is ready. Come back tomorrow to keep building on it.")).toBeInTheDocument();
    await waitFor(() => {
      expect(completeOnboarding).toHaveBeenCalledWith({
        profileType: "STUDENT",
        examDate: null,
      });
    });
    await waitFor(() => {
      expect(updateLearningProfileContext).toHaveBeenCalledWith("COLLEGE", "AWS Certification");
    });

    const studyPackButton = screen.getByRole("button", { name: "Open your Study Pack" });
    expect(studyPackButton).toHaveClass("bg-primary");
    expect(screen.getByRole("button", { name: "Go to Dashboard" })).toHaveClass("bg-transparent");
    fireEvent.click(studyPackButton);

    // Both calls must carry the Step 2 Course / Program. The learner branch of both
    // NoteGenerationService.resolveAuthoringContext and NoteService.resolveRequestedCourseProgram
    // throws CourseProgramSelectionRequiredException when the request omits it and the profile has
    // none — and onboarding only persists the profile value at Step 5, after both calls. Omitting it
    // here made onboarding a dead end for every new user (finding B0).
    expect(generateNoteFromTopic).toHaveBeenCalledWith("Newton's Laws of Motion", "AWS Certification");
    expect(createNote).toHaveBeenCalledWith({
      title: "Newton's Laws of Motion",
      courseProgramText: "AWS Certification",
      // Explicit nulls, not omissions: UpsertNoteRequest requires both authoring axes so a caller
      // cannot silently drop them (PUT is a full replace and omission persists as null).
      domainContext: null,
      learnerLevel: null,
      targetProfileType: "STUDENT",
      content: "Edited Newton note content for onboarding so the study pack can start.",
    });
    expect(createStudyPackFromNote).toHaveBeenCalledWith("note-1", { autoApplyMetadata: true });
    expect(routerMock.push).toHaveBeenCalledWith("/study-packs/study-pack-1");

    const completedEvents = (trackAnalyticsEvent as jest.Mock).mock.calls
      .map(([event]) => event)
      .filter((event) => event.eventType === "ONBOARDING_V2_COMPLETED");
    expect(completedEvents).toEqual([{
      eventType: "ONBOARDING_V2_COMPLETED",
      metadata: {
        profile_type: "STUDENT",
        learner_level: "COLLEGE",
        course_program: "AWS Certification",
        method: "generate",
        time_elapsed_seconds: expect.any(Number),
      },
    }]);
    const viewedStepNames = (trackAnalyticsEvent as jest.Mock).mock.calls
      .map(([event]) => event)
      .filter((event) => event.eventType === "ONBOARDING_V2_STEP_VIEWED")
      .map((event) => event.metadata.step_name);
    expect(viewedStepNames).toEqual(expect.arrayContaining([
      "profile",
      "course-program",
      "learner-level",
      "first-intent",
      "input-method",
      "note",
      "generating",
      "completion",
    ]));

    routerMock.push.mockReset();
    fireEvent.click(screen.getByRole("button", { name: "Go to Dashboard" }));
    expect(routerMock.push).toHaveBeenCalledWith("/dashboard");
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
    await clickContinue();

    expect(await screen.findByText("What are you studying?")).toBeInTheDocument();
    await completeLearningProfile("COLLEGE", "AWS Certification");

    await chooseOwnNotesIntent();
    expect(await screen.findByText("How do you want to begin your first note?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Create a note" }));
    fireEvent.change(screen.getByPlaceholderText("Create a note about Newton’s Laws of Motion..."), {
      target: { value: "Newton's Laws of Motion" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Create a Note/ }));

    const generatedNoteEditor = await screen.findByPlaceholderText("Your note will appear here.");
    fireEvent.change(generatedNoteEditor, {
      target: { value: "Edited Newton note content for onboarding so the study pack can start." },
    });
    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack →" }));

    expect(await screen.findByText("Your Study Pack is ready.")).toBeInTheDocument();
    await clickContinue();

    expect(await screen.findByText("Your Newton's Laws of Motion Study Pack is ready. Come back tomorrow to keep building on it.")).toBeInTheDocument();
    expect(await screen.findByText("AWS Certification Plan")).toBeInTheDocument();
    expect(screen.getByText("Optional: explore an official study plan alongside the Study Pack you just created.")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Start this Study Plan" }));

    await waitFor(() => {
      expect(adoptStudyPlan).toHaveBeenCalledWith("source-plan-1");
    });
  });

  // The whole point of stage 2. These values were previously written at Step 5, fire-and-forget, with the
  // error swallowed -- so a failure lost them permanently and silently while onboardingCompletedAt was
  // already set, and the user was never routed back. Five real accounts finished onboarding that way.
  it("persists the learning context at Step 2, before advancing", async () => {
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();
    expect(await screen.findByText("What are you studying?")).toBeInTheDocument();
    await completeLearningProfile("COLLEGE", "Nursing");

    await waitFor(() => {
      expect(updateLearningProfileContext).toHaveBeenCalledWith("COLLEGE", "Nursing");
    });
    await chooseOwnNotesIntent();
    expect(await screen.findByText("How do you want to begin your first note?")).toBeInTheDocument();
  });

  it("blocks Step 2 and surfaces the error when the learning context cannot be saved", async () => {
    (updateLearningProfileContext as jest.Mock).mockRejectedValue(new Error("Network unavailable"));
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();
    expect(await screen.findByText("What are you studying?")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Course / Program"), { target: { value: "Nursing" } });
    await clickContinue();
    fireEvent.change(screen.getByLabelText("Learner Level"), { target: { value: "COLLEGE" } });
    await clickContinue();

    expect(await screen.findByText("Network unavailable")).toBeInTheDocument();
    expect(screen.getByText("What level are you studying at?")).toBeInTheDocument();
    expect(screen.queryByText("How do you want to begin your first note?")).not.toBeInTheDocument();
  });

  it("persists the profile type at Step 1 without blocking the transition", async () => {
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();

    // Advances immediately: profileType is re-sent by completeOnboarding, so nothing is at risk if this
    // call fails. That is why it is fire-and-forget here and emphatically not at Step 2.
    expect(screen.getByText("What are you studying?")).toBeInTheDocument();
    await waitFor(() => {
      expect(completeOnboardingProfileType).toHaveBeenCalledWith({ profileType: "STUDENT" });
    });
  });

  // ---- Intent Router --------------------------------------------------------------------------------

  it("offers both intent doors, neither disabled, and labels an unavailable Review Set neutrally", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();
    await completeLearningProfile("COLLEGE", "Nursing");

    const readyMade = await screen.findByRole("button", { name: /Study with ready-made materials/ });
    const ownNotes = screen.getByRole("button", { name: /Build from my own notes/ });
    // Never disabled: its next screen still offers useful alternatives, and disabling it would make the
    // path read as a dead end before the user has seen what is behind it.
    expect(readyMade).not.toBeDisabled();
    expect(ownNotes).not.toBeDisabled();
    expect(screen.getByText("No Official Study Plan yet for Nursing")).toBeInTheDocument();
  });

  it("does not claim a Review Set is missing when the availability lookup fails", async () => {
    // Fails OPEN. Asserting absence when content exists is the worse error, so an unknown result renders
    // no availability line at all rather than a false negative.
    (listPublicStudyPlans as jest.Mock).mockRejectedValue(new Error("network"));
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();
    await completeLearningProfile("COLLEGE", "Nursing");

    await screen.findByRole("button", { name: /Study with ready-made materials/ });
    expect(screen.queryByText(/No Official .* yet for/)).not.toBeInTheDocument();
  });

  // A resumed draft loses `practiceFirstPlan` (component state) while keeping `reviewSetAvailable: true`
  // (localStorage). Before the re-resolve, one click on the ready-made door hit `!practiceFirstPlan` and
  // told the learner their program had no Review Set -- contradicting the state the screen was painted
  // from -- and two of the three exits offered there complete onboarding and navigate away.
  it("re-resolves the plan on a resumed draft instead of claiming no Review Set exists", async () => {
    const qualifyingPlan = {
      id: "source-plan-1",
      title: "Nursing Review Set",
      description: "A curated Nursing sequence.",
      visibility: "PUBLIC",
      courseProgram: "Nursing",
      sourcePlanId: null,
      parentCollectionId: null,
      itemCount: 3,
      childCount: 0,
      readyCount: 2,
      notesPracticed: 0,
      createdAt: "2026-06-01T00:00:00Z",
      updatedAt: "2026-06-02T00:00:00Z",
    };
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([qualifyingPlan]);
    window.localStorage.setItem("notelib.onboarding-v2:user-1", JSON.stringify({
      startedAtMs: Date.now(),
      // Current-schema draft: without the version this is treated as pre-split and its resume point is
      // recomputed, which is a different code path from the one this test is about.
      schemaVersion: 1,
      currentStep: 5,
      profileType: "STUDENT",
      learnerLevel: "COLLEGE",
      courseProgram: "Nursing",
      reviewSetAvailable: true,
      intent: "ready_made",
      examDate: "",
      inputMethod: null,
      topic: "",
      noteContent: "",
      generatedNoteReady: false,
      noteId: null,
      studyPackId: null,
    }));

    render(<OnboardingPage />);

    expect(await screen.findByText("You're preparing for Nursing.")).toBeInTheDocument();
    expect(screen.queryByText(/still building an Official/)).not.toBeInTheDocument();
  });

  it("shows the honest unavailable state with all three fallbacks, and never auto-redirects", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();
    await completeLearningProfile("COLLEGE", "Nursing");
    await chooseReadyMadeIntent();

    expect(await screen.findByText(/still building an Official Study Plan for Nursing/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Build from my own notes/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Explore related public notes/ })).toBeInTheDocument();
    // Renamed: the exit now names where it goes, and it acts on click rather than selecting.
    expect(screen.getByRole("button", { name: "Go to Dashboard" })).toBeInTheDocument();
    expect(routerMock.push).not.toHaveBeenCalled();
  });

  it("records Official Study Plan interest and confirms in place without ending onboarding", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();
    await completeLearningProfile("COLLEGE", "Nursing");
    await chooseReadyMadeIntent();

    const requestButton = await screen.findByRole("button", { name: "Request this Official Study Plan" });
    await waitFor(() => expect(requestButton).not.toBeDisabled());
    fireEvent.click(requestButton);

    expect(await screen.findByText("Your request is recorded.")).toBeInTheDocument();
    expect(requestOfficialStudyPlan).toHaveBeenCalledWith("Nursing");
    expect(trackAnalyticsEvent).toHaveBeenCalledWith(expect.objectContaining({
      eventType: "ONBOARDING_V2_OFFICIAL_PLAN_REQUESTED",
    }));
    expect(completeOnboarding).not.toHaveBeenCalled();
    expect(routerMock.push).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: /Build from my own notes/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Explore related public notes/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Go to Dashboard" })).toBeInTheDocument();
  });

  it("shows an existing Official Study Plan request as confirmed", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);
    (getOfficialStudyPlanWishlistStatus as jest.Mock).mockResolvedValue({ requested: true });
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();
    await completeLearningProfile("COLLEGE", "Nursing");
    await chooseReadyMadeIntent();

    expect(await screen.findByText("Your request is recorded.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Request this Official Study Plan" })).not.toBeInTheDocument();
    expect(requestOfficialStudyPlan).not.toHaveBeenCalled();
  });

  it("keeps fallback routes usable when recording Official Study Plan interest fails", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);
    (requestOfficialStudyPlan as jest.Mock).mockRejectedValue(new Error("network failed"));
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();
    await completeLearningProfile("COLLEGE", "Nursing");
    await chooseReadyMadeIntent();

    const requestButton = await screen.findByRole("button", { name: "Request this Official Study Plan" });
    await waitFor(() => expect(requestButton).not.toBeDisabled());
    fireEvent.click(requestButton);

    expect(await screen.findByRole("alert")).toHaveTextContent("We couldn't record your request");
    expect(screen.queryByText("Your request is recorded.")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Build from my own notes/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Explore related public notes/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Go to Dashboard" })).toBeInTheDocument();
    expect(completeOnboarding).not.toHaveBeenCalled();
    expect(routerMock.push).not.toHaveBeenCalled();
  });

  it("does not render an Official Study Plan request action for an empty draft program", async () => {
    expect(shouldShowOfficialPlanRequestAction("   ")).toBe(false);
    window.localStorage.setItem("notelib.onboarding-v2:user-1", JSON.stringify({
      startedAtMs: Date.now(),
      schemaVersion: 1,
      currentStep: 5,
      profileType: "STUDENT",
      learnerLevel: "COLLEGE",
      courseProgram: "   ",
      reviewSetAvailable: false,
      intent: "ready_made",
      examDate: "",
      inputMethod: null,
      topic: "",
      noteContent: "",
      generatedNoteReady: false,
      noteId: null,
      studyPackId: null,
    }));

    render(<OnboardingPage />);

    await waitFor(() => expect(getMe).toHaveBeenCalled());
    expect(screen.queryByRole("button", { name: "Request this Official Study Plan" })).not.toBeInTheDocument();
    expect(requestOfficialStudyPlan).not.toHaveBeenCalled();
  });

  it("completes onboarding BEFORE navigating away on a terminal fallback", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);
    const callOrder: string[] = [];
    (completeOnboarding as jest.Mock).mockImplementation(async () => {
      callOrder.push("complete");
      return { ...baseMe, onboardingCompletedAt: "2026-08-07T00:00:00Z" };
    });
    routerMock.push.mockImplementation(() => { callOrder.push("push"); });

    render(<OnboardingPage />);
    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();
    await completeLearningProfile("COLLEGE", "Nursing");
    await chooseReadyMadeIntent();
    fireEvent.click(await screen.findByRole("button", { name: /Explore related public notes/ }));
    fireEvent.click(await screen.findByRole("button", { name: "Finish" }));

    // Navigating first would leave a user who closes the tab mid-transition permanently un-onboarded.
    await waitFor(() => expect(callOrder).toEqual(["complete", "push"]));
    // A SLUG, not the raw value. The consumer resolves this with resolvePublicLibraryValueBySlug, so
    // "Nursing" never matches "nursing" and the UI showed no active filter while the API still filtered.
    // This assertion previously enshrined that defect.
    expect(routerMock.push).toHaveBeenCalledWith("/public/library?courseProgram=nursing");
  });

  it("routes the own-notes fallback into the create flow without completing onboarding", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();
    await completeLearningProfile("COLLEGE", "Nursing");
    await chooseReadyMadeIntent();
    fireEvent.click(await screen.findByRole("button", { name: /Build from my own notes/ }));
    await clickContinue();

    // Stays inside onboarding and finishes through the create flow, so completion must NOT fire here.
    expect(await screen.findByText("How do you want to begin your first note?")).toBeInTheDocument();
    expect(completeOnboarding).not.toHaveBeenCalled();
  });

  it("adapts the intent copy for teachers", async () => {
    (listPublicStudyPlans as jest.Mock).mockResolvedValue([]);
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Teacher"));
    await clickContinue();
    await completeLearningProfile("COLLEGE", "Nursing");

    expect(await screen.findByRole("button", { name: /Use existing teaching and study materials/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Create teaching or study materials/ })).toBeInTheDocument();
  });

  it("switches between modes and only shows the active input surface", async () => {
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();
    expect(await screen.findByText("What are you studying?")).toBeInTheDocument();
    await completeLearningProfile();

    // Step 2 now persists the learning context before advancing, so the transition is asynchronous.
    await chooseOwnNotesIntent();
    expect(await screen.findByText("How do you want to begin your first note?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Create a note" }));
    expect(screen.getByPlaceholderText("Create a note about Newton’s Laws of Motion...")).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("Paste or write your notes here...")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Back" }));
    expect(screen.getByText("How do you want to begin your first note?")).toBeInTheDocument();
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
        fileUploadAvailable: true,
        ocrAvailable: true,
      },
    });

    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Student"));
    await clickContinue();
    expect(await screen.findByText("What are you studying?")).toBeInTheDocument();
    await completeLearningProfile();
    // Step 2 now persists the learning context before advancing, so the transition is asynchronous.
    await chooseOwnNotesIntent();
    expect(await screen.findByText("How do you want to begin your first note?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Create a note" }));

    expect(await screen.findByText("You've reached your topic note limit for this month.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create a Note" })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: "Get Plus" }));

    expect(await screen.findByText("You've reached your topic note limit")).toBeInTheDocument();
    expect(screen.getByText("More topic notes means more of your library is ready when you sit down to study.")).toBeInTheDocument();
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
    await clickContinue();

    fireEvent.change(screen.getByLabelText("Course / Program"), { target: { value: "Pharmacy" } });
    await clickContinue();
    expect(await screen.findByLabelText("When is your exam? (optional)")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Learner Level"), { target: { value: "BOARD_EXAM_REVIEW" } });
    await clickContinue();
    expect(await screen.findByText("What would you like to do first?")).toBeInTheDocument();

    await chooseOwnNotesIntent();
    fireEvent.click(await screen.findByRole("button", { name: "Write or paste my own note" }));
    fireEvent.change(await screen.findByPlaceholderText("Paste or write your notes here..."), {
      target: { value: longNote },
    });
    fireEvent.click(screen.getByRole("button", { name: "Generate Study Pack →" }));

    expect(await screen.findByText("Your Study Pack is ready.")).toBeInTheDocument();
    await clickContinue();

    await waitFor(() => {
      expect(completeOnboarding).toHaveBeenCalledWith({
        profileType: "BOARD_EXAM",
        examDate: null,
      });
    });
    expect(await screen.findByText("Your Study Pack is ready. Come back tomorrow to keep building on it.")).toBeInTheDocument();
  });

  it("shows Professional profile option with learner context and no exam date field", async () => {
    render(<OnboardingPage />);

    expect(await screen.findByText("🎓 Student")).toBeInTheDocument();
    expect(screen.getByText("📋 Exam Reviewer")).toBeInTheDocument();
    expect(screen.getByText("🏫 Teacher")).toBeInTheDocument();
    expect(screen.getByText("💼 Professional")).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText("Professional"));
    await clickContinue();

    expect(screen.getByLabelText("Course / Program")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Course / Program"), { target: { value: "Medicine" } });
    await clickContinue();

    expect(await screen.findByRole("group", { name: "Recommended for Professionals" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Professional" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Personal Learning" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Course / Program")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("When is your exam? (optional)")).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Learner Level"), { target: { value: "PROFESSIONAL" } });
    await clickContinue();
    expect(await screen.findByText("What would you like to do first?")).toBeInTheDocument();
    await chooseOwnNotesIntent();
    expect(await screen.findByText("How do you want to begin your first note?")).toBeInTheDocument();
  });

  it("keeps the Study Pack CTA disabled until the own-note minimum is met", async () => {
    render(<OnboardingPage />);

    fireEvent.click(await screen.findByLabelText("Teacher"));
    await clickContinue();
    // C9: a teacher is asked what they TEACH. "What are you studying?" described something they never do.
    expect(await screen.findByText("What do you teach?")).toBeInTheDocument();
    await completeLearningProfile("GRADE_SCHOOL", "Grade 5 Science");

    await chooseOwnNotesIntent();
    fireEvent.click(await screen.findByRole("button", { name: "Write or paste my own note" }));
    fireEvent.change(await screen.findByPlaceholderText("Paste or write your notes here..."), {
      target: { value: "Short onboarding note draft." },
    });

    expect(screen.getByText(/\/ 50 minimum$/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Generate Study Pack →" })).toBeDisabled();
  });
});
