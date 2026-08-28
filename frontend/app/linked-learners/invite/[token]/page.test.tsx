import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import LinkedLearnerInvitationPage from "./page";
import {
  redeemLinkedLearnerInvitationLink,
  resolveLinkedLearnerInvitationLink,
} from "@/lib/api";
import { getAuthUser, resolveAuthenticatedHome } from "@/lib/auth";

const replace = jest.fn();
const push = jest.fn();

jest.mock("next/navigation", () => ({
  useParams: () => ({ token: "AbCdEf0123456789GhIjKl" }),
  useRouter: () => ({ replace, push }),
}));

jest.mock("@/lib/api", () => ({
  resolveLinkedLearnerInvitationLink: jest.fn(),
  redeemLinkedLearnerInvitationLink: jest.fn(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
  resolveAuthenticatedHome: jest.fn(),
  buildLoginPath: ({ redirectTo, reason }: { redirectTo: string; reason: string }) =>
    `/login?redirect=${encodeURIComponent(redirectTo)}&reason=${reason}`,
  LOGIN_REASON_AUTH_REQUIRED: "auth_required",
}));

const authenticatedUser = {
  id: "user-1",
  emailVerifiedAt: "2026-08-28T00:00:00Z",
  onboardingCompletedAt: "2026-08-28T00:00:00Z",
  profileType: "STUDENT",
};

beforeEach(() => {
  jest.clearAllMocks();
  document.cookie = "notelib-connection-invite=; path=/; max-age=0; SameSite=Strict";
  jest.mocked(getAuthUser).mockReturnValue(authenticatedUser as ReturnType<typeof getAuthUser>);
  jest.mocked(resolveAuthenticatedHome).mockReturnValue("/dashboard");
  jest.mocked(resolveLinkedLearnerInvitationLink).mockResolvedValue({
    inviterName: "Morgan",
    inviterRole: "SUPPORTER",
  });
});

it("requires authentication and stores the token before redirecting", async () => {
  jest.mocked(getAuthUser).mockReturnValue(null);

  render(<LinkedLearnerInvitationPage />);

  await waitFor(() => expect(replace).toHaveBeenCalledWith(
    "/login?redirect=%2Flinked-learners%2Finvite%2FAbCdEf0123456789GhIjKl&reason=auth_required",
  ));
  expect(document.cookie).toContain("notelib-connection-invite=AbCdEf0123456789GhIjKl");
  expect(resolveLinkedLearnerInvitationLink).not.toHaveBeenCalled();
});

it("shows only the authenticated invitation decision details", async () => {
  render(<LinkedLearnerInvitationPage />);

  expect(await screen.findByText("Connect with Morgan")).toBeInTheDocument();
  expect(screen.getByText("They would support your learning.")).toBeInTheDocument();
  expect(screen.queryByText(/@/)).not.toBeInTheDocument();
});

it("redeems into pending and collects the learner birth year", async () => {
  jest.mocked(redeemLinkedLearnerInvitationLink).mockResolvedValue({
    relationshipId: "relationship-1",
    status: "PENDING",
  });
  render(<LinkedLearnerInvitationPage />);
  await screen.findByText("Connect with Morgan");

  fireEvent.change(screen.getByLabelText(/Your birth year/), { target: { value: "2012" } });
  fireEvent.click(screen.getByRole("button", { name: "Confirm connection request" }));

  await waitFor(() => expect(redeemLinkedLearnerInvitationLink)
    .toHaveBeenCalledWith("AbCdEf0123456789GhIjKl", 2012));
  expect(await screen.findByText("Request sent")).toBeInTheDocument();
  expect(screen.getByText(/pending until the person who created the link confirms/i)).toBeInTheDocument();
});
