import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import ProfilePage from "./page";
import {
  completeOnboardingProfileType,
  connectGoogle,
  getMe,
  getSignInMethods,
  listCoursePrograms,
  listSubjects,
  setFocusSubjects,
  updateExamDate,
  updateUserProfile,
} from "@/lib/api";

const routerMock = {
  push: jest.fn(),
  refresh: jest.fn(),
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  useSearchParams: () => new URLSearchParams(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => ({ id: "user-1", emailVerifiedAt: "2026-03-20T00:00:00Z" }),
}));

jest.mock("@/lib/route-guards", () => ({
  redirectToLoginWithCurrentDestination: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  completeOnboardingProfileType: jest.fn(),
  connectGoogle: jest.fn(),
  getMe: jest.fn(),
  getSignInMethods: jest.fn(),
  getUserNotePerformanceSummary: jest.fn().mockResolvedValue([]),
  listCoursePrograms: jest.fn(),
  listSubjects: jest.fn(),
  setFocusSubjects: jest.fn(),
  setStudyGoal: jest.fn(),
  updateExamDate: jest.fn(),
  updateUserProfile: jest.fn(),
}));

const profileResponse = {
  id: "user-1",
  email: "[email protected]",
  pendingEmail: null,
  firstName: "Note",
  lastName: "User",
  displayName: "Note User",
  username: "noteuser",
  bio: "Reviewing pathology one note at a time.",
  learnerLevel: "COLLEGE",
  courseProgram: "Nursing",
  studyGoal: null,
  focusSubjects: [],
  schoolName: null,
  publicProfileVisible: true,
  countryCode: null,
  profileType: "STUDENT",
  examDate: null,
  engagementMode: "FOCUSED",
  inactivityRemindersEnabled: false,
  weakConceptRemindersEnabled: false,
  emailVerifiedAt: "2026-03-20T00:00:00Z",
  onboardingCompletedAt: "2026-03-20T00:05:00Z",
  productOnboardingCompletedAt: null,
  studyPackCount: 3,
  role: "USER",
  status: "ACTIVE",
  planType: "FREE",
  subscription: {
    cancelAtPeriodEnd: false,
    premiumEndsAt: null,
    cancelledAt: null,
  },
} as const;

describe("Profile page", () => {
  beforeEach(() => {
    routerMock.push.mockReset();
    routerMock.refresh.mockReset();
    (getMe as jest.Mock).mockReset();
    (getSignInMethods as jest.Mock).mockReset();
    (listCoursePrograms as jest.Mock).mockReset();
    (listSubjects as jest.Mock).mockReset();
    (setFocusSubjects as jest.Mock).mockReset();
    (updateUserProfile as jest.Mock).mockReset();
    (connectGoogle as jest.Mock).mockReset();
    (completeOnboardingProfileType as jest.Mock).mockReset();
    (updateExamDate as jest.Mock).mockReset();
    (getMe as jest.Mock).mockResolvedValue(profileResponse);
    (getSignInMethods as jest.Mock).mockResolvedValue({
      email: "[email protected]",
      passwordEnabled: true,
      googleConnected: false,
      googleEmail: null,
    });
    (listCoursePrograms as jest.Mock).mockResolvedValue(["Nursing", "Computer Science"]);
    (listSubjects as jest.Mock).mockResolvedValue(["Pharmacology", "Anatomy", "Biochemistry"]);
    (setFocusSubjects as jest.Mock).mockResolvedValue({
      ...profileResponse,
      focusSubjects: ["Pharmacology"],
    });
    (completeOnboardingProfileType as jest.Mock).mockResolvedValue(profileResponse);
    (updateExamDate as jest.Mock).mockResolvedValue(profileResponse);
  });

  it("saves identity fields without changing profile type settings", async () => {
    (updateUserProfile as jest.Mock).mockResolvedValue({
      ...profileResponse,
      firstName: "Updated",
      lastName: "Person",
      displayName: "Updated Person",
    });

    render(<ProfilePage />);

    fireEvent.change(await screen.findByLabelText("First Name"), {
      target: { value: "Updated" },
    });
    fireEvent.change(screen.getByLabelText("Last Name"), {
      target: { value: "Person" },
    });
    fireEvent.change(screen.getByLabelText("Display Name"), {
      target: { value: "Study Buddy" },
    });
    fireEvent.change(screen.getByLabelText("Email"), {
      target: { value: "[email protected]" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save Identity" }));

    await waitFor(() => {
      expect(updateUserProfile).toHaveBeenCalledWith({
        firstName: "Updated",
        lastName: "Person",
        displayName: "Study Buddy",
        username: "noteuser",
        bio: "Reviewing pathology one note at a time.",
        learnerLevel: "COLLEGE",
        courseProgram: "Nursing",
        schoolName: "",
        email: "[email protected]",
      });
    });
    expect(completeOnboardingProfileType).not.toHaveBeenCalled();

    expect(await screen.findByText("Identity updated successfully.")).toBeInTheDocument();
    expect(screen.queryByText("Learning Style")).not.toBeInTheDocument();
    expect(screen.queryByText("Study Reminder Frequency")).not.toBeInTheDocument();
  });

  it("saves profile type separately from identity fields", async () => {
    (completeOnboardingProfileType as jest.Mock).mockResolvedValue({
      ...profileResponse,
      profileType: "TEACHER",
    });

    render(<ProfilePage />);

    fireEvent.click(await screen.findByRole("button", { name: /Teacher Generate, preview, and export quiz materials from your notes\./i }));
    fireEvent.click(screen.getByRole("button", { name: "Save Profile Type" }));
    fireEvent.click(await screen.findByRole("button", { name: "Switch" }));

    await waitFor(() => {
      expect(completeOnboardingProfileType).toHaveBeenCalledWith({ profileType: "TEACHER" });
    });
    expect(updateUserProfile).not.toHaveBeenCalled();
    expect(await screen.findByText("You're now in Teacher mode — focused on generate, review, and export.")).toBeInTheDocument();
  });

  it("shows and saves school name inside the Profile Type card for teacher profiles", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...profileResponse,
      profileType: "TEACHER",
      schoolName: "Old School",
    });
    (updateUserProfile as jest.Mock).mockResolvedValue({
      ...profileResponse,
      profileType: "TEACHER",
      schoolName: "NoteLib Academy",
    });

    render(<ProfilePage />);

    const schoolNameInput = await screen.findByLabelText("School Name");
    expect(schoolNameInput).toHaveValue("Old School");
    fireEvent.change(schoolNameInput, { target: { value: "NoteLib Academy" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Profile Type" }));

    await waitFor(() => {
      expect(updateUserProfile).toHaveBeenCalledWith({
        firstName: "Note",
        lastName: "User",
        displayName: "Note User",
        username: "noteuser",
        bio: "Reviewing pathology one note at a time.",
        learnerLevel: "COLLEGE",
        courseProgram: "Nursing",
        schoolName: "NoteLib Academy",
        email: "[email protected]",
      });
    });
    expect(await screen.findByText("School name updated.")).toBeInTheDocument();
  });

  it("reframes learner level helper copy for teacher profiles", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...profileResponse,
      profileType: "TEACHER",
    });

    render(<ProfilePage />);

    expect(await screen.findByText(
      "This sets the default difficulty for quizzes you generate. You can change it per quiz.",
    )).toBeInTheDocument();
  });

  it("hides Teaching Info from non-teacher profiles", async () => {
    render(<ProfilePage />);

    await screen.findByText("Profile Type");
    expect(screen.queryByLabelText("School Name")).not.toBeInTheDocument();
  });

  it("shows and saves exam date for Exam Reviewer profiles", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...profileResponse,
      profileType: "BOARD_EXAM",
      examDate: "2999-10-15",
    });
    (updateExamDate as jest.Mock).mockResolvedValue({
      ...profileResponse,
      profileType: "BOARD_EXAM",
      examDate: "2999-12-20",
    });

    render(<ProfilePage />);

    const examDateInput = await screen.findByLabelText("Exam Date");
    expect(examDateInput).toHaveValue("2999-10-15");
    expect(screen.getByText(/days until your exam\./)).toBeInTheDocument();

    fireEvent.change(examDateInput, { target: { value: "2999-12-20" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Profile Type" }));

    await waitFor(() => {
      expect(updateExamDate).toHaveBeenCalledWith("2999-12-20");
    });
    expect(await screen.findByText("Exam date updated successfully.")).toBeInTheDocument();
    expect(screen.getByLabelText("Exam Date")).toHaveValue("2999-12-20");
  });

  it.each(["STUDENT", "TEACHER", "PROFESSIONAL"])(
    "hides exam date for %s profiles",
    async (profileType) => {
      (getMe as jest.Mock).mockResolvedValue({
        ...profileResponse,
        profileType,
        examDate: "2999-10-15",
      });

      render(<ProfilePage />);

      await screen.findByText("Profile Type");
      expect(screen.queryByLabelText("Exam Date")).not.toBeInTheDocument();
    },
  );

  it("clears Exam Reviewer exam date when the saved input is empty", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...profileResponse,
      profileType: "BOARD_EXAM",
      examDate: "2999-10-15",
    });
    (updateExamDate as jest.Mock).mockResolvedValue({
      ...profileResponse,
      profileType: "BOARD_EXAM",
      examDate: null,
    });

    render(<ProfilePage />);

    const examDateInput = await screen.findByLabelText("Exam Date");
    fireEvent.change(examDateInput, { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Profile Type" }));

    await waitFor(() => {
      expect(updateExamDate).toHaveBeenCalledWith(null);
    });
    expect(await screen.findByText("Exam date cleared.")).toBeInTheDocument();
    expect(screen.getByLabelText("Exam Date")).toHaveValue("");
    expect(screen.queryByText(/days until your exam\./)).not.toBeInTheDocument();
  });

  it("shows exam date save errors without keeping the unsaved date", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...profileResponse,
      profileType: "BOARD_EXAM",
      examDate: "2999-10-15",
    });
    (updateExamDate as jest.Mock).mockRejectedValue(new Error("Could not update exam date."));

    render(<ProfilePage />);

    const examDateInput = await screen.findByLabelText("Exam Date");
    fireEvent.change(examDateInput, { target: { value: "2999-12-20" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Profile Type" }));

    expect(await screen.findByText("Could not update exam date.")).toBeInTheDocument();
    expect(screen.getByLabelText("Exam Date")).toHaveValue("2999-10-15");
  });

  it("shows pending email verification guidance after email change", async () => {
    (updateUserProfile as jest.Mock).mockResolvedValue({
      ...profileResponse,
      pendingEmail: "[email protected]",
    });

    render(<ProfilePage />);

    fireEvent.change(await screen.findByLabelText("Display Name"), {
      target: { value: "Note Hero" },
    });
    fireEvent.change(await screen.findByLabelText("Email"), {
      target: { value: "[email protected]" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save Identity" }));

    expect(
      await screen.findByText("Please verify your new email address before it replaces your current email."),
    ).toBeInTheDocument();
    expect(
      screen.getByText((_, element) =>
        element?.textContent === "Pending email change: [email protected]",
      ),
    ).toBeInTheDocument();
  });

  it("shows a public profile navigation link in the top card without share controls", async () => {
    render(<ProfilePage />);

    expect(await screen.findByRole("link", { name: "View Public Page" })).toHaveAttribute(
      "href",
      "/public/creator/noteuser",
    );
    expect(screen.queryByRole("link", { name: "Profile" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Share Public Profile" })).not.toBeInTheDocument();
    expect(screen.queryByText("Public Profile On")).not.toBeInTheDocument();
  });

  it("scrolls the Profile Type section into view from the profile-type hash", async () => {
    const scrollIntoView = jest.fn();
    const focus = jest.fn();
    const originalRequestAnimationFrame = globalThis.requestAnimationFrame;
    const originalScrollIntoView = window.HTMLElement.prototype.scrollIntoView;
    const originalFocus = window.HTMLElement.prototype.focus;
    window.HTMLElement.prototype.scrollIntoView = scrollIntoView;
    window.HTMLElement.prototype.focus = focus;
    globalThis.requestAnimationFrame = ((callback: FrameRequestCallback) => {
      callback(0);
      return 1;
    }) as typeof globalThis.requestAnimationFrame;
    window.history.replaceState({}, "", "/profile#profile-type");

    render(<ProfilePage />);

    await screen.findByText("Profile Type");
    const profileTypeSection = document.getElementById("profile-type");
    expect(profileTypeSection).not.toBeNull();

    await waitFor(() => {
      expect(scrollIntoView).toHaveBeenCalledWith({ behavior: "smooth", block: "start" });
      expect(focus).toHaveBeenCalledWith({ preventScroll: true });
    });

    globalThis.requestAnimationFrame = originalRequestAnimationFrame;
    window.HTMLElement.prototype.scrollIntoView = originalScrollIntoView;
    window.HTMLElement.prototype.focus = originalFocus;
    window.history.replaceState({}, "", "/profile");
  });

  it("shows connected sign-in methods and Google connect entry point", async () => {
    render(<ProfilePage />);

    expect(await screen.findByText("Sign-in Methods")).toBeInTheDocument();
    expect(screen.getByText("Email and password")).toBeInTheDocument();
    expect(screen.getByText("Enabled")).toBeInTheDocument();
    expect(screen.getByText("Google")).toBeInTheDocument();
    expect(screen.getByText("Not connected")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Connect Google" })).toBeInTheDocument();
  });

  it("shows connected Google email when already linked", async () => {
    (getSignInMethods as jest.Mock).mockResolvedValue({
      email: "[email protected]",
      passwordEnabled: false,
      googleConnected: true,
      googleEmail: "[email protected]",
    });

    render(<ProfilePage />);

    expect(await screen.findByText("Not set up")).toBeInTheDocument();
    expect(screen.getByText((_, element) =>
      element?.textContent === "Connected: [email protected]",
    )).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Connect Google" })).not.toBeInTheDocument();
  });

  it("saves the bio field with learning profile settings", async () => {
    (updateUserProfile as jest.Mock).mockResolvedValue({
      ...profileResponse,
      bio: "Focused on endocrine board review.",
    });

    render(<ProfilePage />);

    fireEvent.change(await screen.findByLabelText("Bio"), {
      target: { value: "Focused on endocrine board review." },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save Learning Profile" }));

    await waitFor(() => {
      expect(updateUserProfile).toHaveBeenCalledWith({
        firstName: "Note",
        lastName: "User",
        displayName: "Note User",
        username: "noteuser",
        bio: "Focused on endocrine board review.",
        learnerLevel: "COLLEGE",
        courseProgram: "Nursing",
        schoolName: "",
        email: "[email protected]",
      });
    });
  });

  it("saves learner level and course/program from the learning profile card", async () => {
    (updateUserProfile as jest.Mock).mockResolvedValue({
      ...profileResponse,
      learnerLevel: "BOARD_EXAM_REVIEW",
      courseProgram: "Pharmacy",
      bio: "Board review focus with pharmacology notes.",
    });

    render(<ProfilePage />);

    fireEvent.change(await screen.findByDisplayValue("College"), {
      target: { value: "BOARD_EXAM_REVIEW" },
    });
    fireEvent.change(screen.getByDisplayValue("Nursing"), {
      target: { value: "Pharmacy" },
    });
    fireEvent.change(screen.getByLabelText("Bio"), {
      target: { value: "Board review focus with pharmacology notes." },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save Learning Profile" }));

    await waitFor(() => {
      expect(updateUserProfile).toHaveBeenCalledWith({
        firstName: "Note",
        lastName: "User",
        displayName: "Note User",
        username: "noteuser",
        bio: "Board review focus with pharmacology notes.",
        learnerLevel: "BOARD_EXAM_REVIEW",
        courseProgram: "Pharmacy",
        schoolName: "",
        email: "[email protected]",
      });
    });
    expect(await screen.findByText("Learning profile updated successfully.")).toBeInTheDocument();
  });

  it("updates the course/program helper text when learner level changes", async () => {
    render(<ProfilePage />);

    expect(
      await screen.findByText("Enter your degree like Engineering, Nursing, Accountancy, etc."),
    ).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Learner Level"), {
      target: { value: "PROFESSIONAL" },
    });

    expect(
      await screen.findByText("Enter your field like Law, Medicine, IT, Education, etc."),
    ).toBeInTheDocument();
  });

  it("shows inline validation when learner level or course/program is missing", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...profileResponse,
      learnerLevel: null,
      courseProgram: null,
    });

    render(<ProfilePage />);

    await screen.findByText("Learning Profile");
    fireEvent.click(screen.getByRole("button", { name: "Save Learning Profile" }));

    expect(updateUserProfile).not.toHaveBeenCalled();
    expect(await screen.findByText("Please select your learner level.")).toBeInTheDocument();
    expect(await screen.findByText("Please select or enter your course / program.")).toBeInTheDocument();
  });

  it("hides Study Focus for teacher profiles", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...profileResponse,
      profileType: "TEACHER",
    });

    render(<ProfilePage />);

    await screen.findByText("Profile Type");
    expect(screen.queryByText("Study Focus")).not.toBeInTheDocument();
    expect(screen.queryByText("Exam Focus")).not.toBeInTheDocument();
  });

  it("uses Exam Focus copy for board exam profiles", async () => {
    (getMe as jest.Mock).mockResolvedValue({
      ...profileResponse,
      profileType: "BOARD_EXAM",
    });

    render(<ProfilePage />);

    expect(await screen.findByRole("heading", { name: "Exam Focus" })).toBeInTheDocument();
    expect(screen.getByText("Pick the specific subjects you want to track readiness for.")).toBeInTheDocument();
  });

  it("uses Study Focus copy for student profiles", async () => {
    render(<ProfilePage />);

    expect(await screen.findByRole("heading", { name: "Study Focus" })).toBeInTheDocument();
    expect(screen.getByText("Pick the subjects you're preparing for this term.")).toBeInTheDocument();
  });

  it("selects multiple subject chips and saves focus subjects", async () => {
    (setFocusSubjects as jest.Mock).mockResolvedValue({
      ...profileResponse,
      focusSubjects: ["Pharmacology", "Anatomy"],
    });

    render(<ProfilePage />);

    fireEvent.click(await screen.findByRole("button", { name: "Pharmacology" }));
    fireEvent.click(screen.getByRole("button", { name: "Anatomy" }));

    const saveButton = screen.getByRole("button", { name: "Save Focus" });
    expect(saveButton).toBeInTheDocument();
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(setFocusSubjects).toHaveBeenCalledWith(["Pharmacology", "Anatomy"]);
    });
  });

  it("shows the Study Focus empty state when no subjects exist", async () => {
    (listSubjects as jest.Mock).mockResolvedValue([]);
    (getMe as jest.Mock).mockResolvedValue({
      ...profileResponse,
      studyGoal: null,
      focusSubjects: [],
    });

    render(<ProfilePage />);

    expect(await screen.findByText("Create some notes first — your subjects will appear here as focus options.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save Focus" })).not.toBeInTheDocument();
  });
});
