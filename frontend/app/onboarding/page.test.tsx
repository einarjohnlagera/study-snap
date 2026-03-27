import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import OnboardingPage from "./page";
import { completeOnboarding, getMe } from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";

const routerMock = {
  push: jest.fn(),
  replace: jest.fn(),
  refresh: jest.fn(),
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
  getMe: jest.fn(),
}));

describe("OnboardingPage", () => {
  beforeEach(() => {
    routerMock.push.mockReset();
    routerMock.replace.mockReset();
    routerMock.refresh.mockReset();
    (getAuthUser as jest.Mock).mockReset();
    (setAuthUser as jest.Mock).mockReset();
    (getMe as jest.Mock).mockReset();
    (completeOnboarding as jest.Mock).mockReset();
  });

  it("walks through the board exam flow and saves exam date plus reminder preferences", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      email: "[email protected]",
      displayName: "Note",
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      onboardingCompletedAt: null,
    });
    (getMe as jest.Mock).mockResolvedValue({
      profileType: null,
      examDate: null,
      engagementMode: "FOCUSED",
      inactivityRemindersEnabled: false,
      weakConceptRemindersEnabled: false,
      onboardingCompletedAt: null,
    });
    (completeOnboarding as jest.Mock).mockResolvedValue({
      displayName: "Note",
      profileType: "BOARD_EXAM",
      examDate: "2026-10-18",
      engagementMode: "STREAK",
      inactivityRemindersEnabled: true,
      weakConceptRemindersEnabled: true,
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      onboardingCompletedAt: "2026-03-24T00:05:00Z",
      productOnboardingCompletedAt: null,
    });

    render(<OnboardingPage />);

    expect(await screen.findByText("Let's set up NoteLib for you.")).toBeInTheDocument();
    expect(await screen.findByText("What will you use NoteLib for?")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Board Exam"));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("Learning Style")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Streak"));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("Study Reminder Frequency")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Stay on track"));
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByText("When is your exam?")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Select date"), {
      target: { value: "2026-10-18" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Finish setup" }));

    await waitFor(() => {
      expect(completeOnboarding).toHaveBeenCalledWith({
        profileType: "BOARD_EXAM",
        engagementMode: "STREAK",
        inactivityRemindersEnabled: true,
        weakConceptRemindersEnabled: true,
        examDate: "2026-10-18",
      });
    });
    expect(setAuthUser).toHaveBeenCalledWith(expect.objectContaining({
      profileType: "BOARD_EXAM",
      onboardingCompletedAt: "2026-03-24T00:05:00Z",
    }));
    expect(routerMock.push).toHaveBeenCalledWith("/dashboard");
  });

  it("does not repeat when onboarding is already completed", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      email: "[email protected]",
      displayName: "Note",
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      onboardingCompletedAt: "2026-03-24T00:05:00Z",
    });

    render(<OnboardingPage />);

    await waitFor(() => {
      expect(routerMock.replace).toHaveBeenCalledWith("/dashboard");
    });
    expect(getMe).not.toHaveBeenCalled();
    expect(completeOnboarding).not.toHaveBeenCalled();
  });
});
