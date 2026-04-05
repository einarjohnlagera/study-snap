import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import ProfilePage from "./page";
import { completeOnboardingProfileType, getMe, listCoursePrograms, updateUserProfile } from "@/lib/api";

const routerMock = {
  push: jest.fn(),
  refresh: jest.fn(),
};

jest.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => ({ id: "user-1", emailVerifiedAt: "2026-03-20T00:00:00Z" }),
}));

jest.mock("@/lib/route-guards", () => ({
  redirectToLoginWithCurrentDestination: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  completeOnboardingProfileType: jest.fn(),
  getMe: jest.fn(),
  listCoursePrograms: jest.fn(),
  updateUserProfile: jest.fn(),
}));

const profileResponse = {
  id: "user-1",
  email: "[email protected]",
  pendingEmail: null,
  firstName: "Note",
  lastName: "User",
  displayName: "Note User",
  bio: "Reviewing pathology one note at a time.",
  learnerLevel: "COLLEGE",
  courseProgram: "Nursing",
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
    (listCoursePrograms as jest.Mock).mockReset();
    (updateUserProfile as jest.Mock).mockReset();
    (completeOnboardingProfileType as jest.Mock).mockReset();
    (getMe as jest.Mock).mockResolvedValue(profileResponse);
    (listCoursePrograms as jest.Mock).mockResolvedValue(["Nursing", "Computer Science"]);
    (completeOnboardingProfileType as jest.Mock).mockResolvedValue(profileResponse);
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
        bio: "Reviewing pathology one note at a time.",
        learnerLevel: "COLLEGE",
        courseProgram: "Nursing",
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

    fireEvent.change(await screen.findByDisplayValue("Student"), {
      target: { value: "TEACHER" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save Profile Type" }));

    await waitFor(() => {
      expect(completeOnboardingProfileType).toHaveBeenCalledWith({ profileType: "TEACHER" });
    });
    expect(updateUserProfile).not.toHaveBeenCalled();
    expect(await screen.findByText("Profile type updated successfully.")).toBeInTheDocument();
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
      "/public/profile/user-1",
    );
    expect(screen.queryByRole("button", { name: "Share Public Profile" })).not.toBeInTheDocument();
    expect(screen.queryByText("Public Profile On")).not.toBeInTheDocument();
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
        bio: "Focused on endocrine board review.",
        learnerLevel: "COLLEGE",
        courseProgram: "Nursing",
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
        bio: "Board review focus with pharmacology notes.",
        learnerLevel: "BOARD_EXAM_REVIEW",
        courseProgram: "Pharmacy",
        email: "[email protected]",
      });
    });
    expect(await screen.findByText("Learning profile updated successfully.")).toBeInTheDocument();
  });
});
