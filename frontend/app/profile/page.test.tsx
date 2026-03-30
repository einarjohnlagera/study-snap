import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import ProfilePage from "./page";
import { completeOnboardingProfileType, getMe, updateUserProfile } from "@/lib/api";

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
  updateUserProfile: jest.fn(),
}));

const profileResponse = {
  id: "user-1",
  email: "[email protected]",
  pendingEmail: null,
  firstName: "Note",
  lastName: "User",
  displayName: "Note User",
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
    (getMe as jest.Mock).mockReset();
    (updateUserProfile as jest.Mock).mockReset();
    (completeOnboardingProfileType as jest.Mock).mockReset();
    (getMe as jest.Mock).mockResolvedValue(profileResponse);
    (completeOnboardingProfileType as jest.Mock).mockResolvedValue(profileResponse);
  });

  it("saves identity fields without mixing in preferences", async () => {
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
    fireEvent.change(screen.getByLabelText("Email"), {
      target: { value: "[email protected]" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save Identity" }));

    await waitFor(() => {
      expect(updateUserProfile).toHaveBeenCalledWith({
        firstName: "Updated",
        lastName: "Person",
        email: "[email protected]",
      });
    });

    expect(screen.getByText("Profile updated successfully.")).toBeInTheDocument();
    expect(screen.queryByText("Learning Style")).not.toBeInTheDocument();
    expect(screen.queryByText("Study Reminder Frequency")).not.toBeInTheDocument();
  });

  it("shows pending email verification guidance after email change", async () => {
    (updateUserProfile as jest.Mock).mockResolvedValue({
      ...profileResponse,
      pendingEmail: "[email protected]",
    });

    render(<ProfilePage />);

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
});
