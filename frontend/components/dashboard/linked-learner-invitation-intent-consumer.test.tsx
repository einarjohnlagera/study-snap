import { render, waitFor } from "@testing-library/react";
import { LinkedLearnerInvitationIntentConsumer } from "./linked-learner-invitation-intent-consumer";
import { setLinkedLearnerInvitationIntentCookie } from "@/lib/linked-learner-invitation-intent";

const replace = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
}));

beforeEach(() => {
  jest.clearAllMocks();
  document.cookie = "notelib-connection-invite=; path=/; max-age=0; SameSite=Strict";
});

it("resumes a signup-carried invitation exactly once from the dashboard", async () => {
  setLinkedLearnerInvitationIntentCookie("AbCdEf0123456789GhIjKl");

  const firstMount = render(<LinkedLearnerInvitationIntentConsumer />);

  await waitFor(() => expect(replace)
    .toHaveBeenCalledWith("/linked-learners/invite/AbCdEf0123456789GhIjKl"));
  firstMount.unmount();
  render(<LinkedLearnerInvitationIntentConsumer />);
  expect(replace).toHaveBeenCalledTimes(1);
  expect(document.cookie).not.toContain("notelib-connection-invite=");
});
