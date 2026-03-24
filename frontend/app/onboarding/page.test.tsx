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

  it("submits profile type and learning style, then redirects to dashboard", async () => {
    (getAuthUser as jest.Mock).mockReturnValue({
      id: "user-1",
      email: "[email protected]",
      displayName: "Note",
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      onboardingCompletedAt: null,
    });
    (getMe as jest.Mock).mockResolvedValue({
      profileType: null,
      engagementMode: "FOCUSED",
      onboardingCompletedAt: null,
    });
    (completeOnboarding as jest.Mock).mockResolvedValue({
      displayName: "Note",
      profileType: "TEACHER",
      engagementMode: "STREAK",
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      onboardingCompletedAt: "2026-03-24T00:05:00Z",
    });

    render(<OnboardingPage />);

    expect(await screen.findByText("Let's set up your study style.")).toBeInTheDocument();
    expect(await screen.findByText("Teacher")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Teacher"));
    fireEvent.click(screen.getByDisplayValue("STREAK"));
    fireEvent.click(screen.getByRole("button", { name: "Finish setup" }));

    await waitFor(() => {
      expect(completeOnboarding).toHaveBeenCalledWith({
        profileType: "TEACHER",
        engagementMode: "STREAK",
      });
    });
    expect(setAuthUser).toHaveBeenCalledWith(expect.objectContaining({
      profileType: "TEACHER",
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
