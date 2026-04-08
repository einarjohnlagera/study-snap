import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useQuizSessionGuard } from "./quiz-session-guard";

const pushMock = jest.fn();
const onConfirmLeaveMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    push: pushMock,
  }),
}));

function GuardHarness() {
  const { requestLeave, LeaveQuizModal } = useQuizSessionGuard({
    active: true,
    fallbackHref: "/notes/note-1",
    onConfirmLeave: onConfirmLeaveMock,
  });

  return (
    <div>
      <a href="/dashboard">Dashboard</a>
      <button type="button" onClick={() => requestLeave()}>
        Leave Quiz
      </button>
      <LeaveQuizModal />
    </div>
  );
}

describe("useQuizSessionGuard", () => {
  beforeEach(() => {
    pushMock.mockReset();
    onConfirmLeaveMock.mockReset();
    onConfirmLeaveMock.mockResolvedValue(undefined);
    window.history.replaceState(null, "", "/notes/note-1/challenge-quiz");
  });

  it("blocks internal route clicks until the user confirms leaving", async () => {
    render(<GuardHarness />);

    fireEvent.click(screen.getByRole("link", { name: "Dashboard" }));

    expect(screen.getByRole("dialog", { name: "Leave quiz?" })).toBeInTheDocument();
    expect(pushMock).not.toHaveBeenCalled();

    const leaveButtons = screen.getAllByRole("button", { name: "Leave Quiz" });
    fireEvent.click(leaveButtons[leaveButtons.length - 1]!);

    await waitFor(() => {
      expect(onConfirmLeaveMock).toHaveBeenCalledTimes(1);
    });
    expect(pushMock).toHaveBeenCalledWith("/dashboard");
  });

  it("keeps the user in the quiz when they choose Stay", () => {
    render(<GuardHarness />);

    fireEvent.click(screen.getByRole("button", { name: "Leave Quiz" }));
    fireEvent.click(screen.getByRole("button", { name: "Stay" }));

    expect(screen.queryByRole("dialog", { name: "Leave quiz?" })).not.toBeInTheDocument();
    expect(onConfirmLeaveMock).not.toHaveBeenCalled();
    expect(pushMock).not.toHaveBeenCalled();
  });

  it("warns on refresh while the quiz is active", () => {
    render(<GuardHarness />);

    const event = new Event("beforeunload", { cancelable: true });
    fireEvent(window, event);

    expect(event.defaultPrevented).toBe(true);
  });
});
