import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import {
  ApiRequestError,
  askCompanionQuestion,
  getActiveAskCompanionSession,
  startAskCompanionSession,
  type AskCompanionSessionResponse,
} from "@/lib/api";
import { AskCompanionPanel } from "./ask-companion-panel";

jest.mock("@/lib/api", () => {
  const actual = jest.requireActual("@/lib/api");
  return {
    ...actual,
    askCompanionQuestion: jest.fn(),
    getActiveAskCompanionSession: jest.fn(),
    startAskCompanionSession: jest.fn(),
  };
});

const getActiveMock = getActiveAskCompanionSession as jest.MockedFunction<typeof getActiveAskCompanionSession>;
const startMock = startAskCompanionSession as jest.MockedFunction<typeof startAskCompanionSession>;
const askMock = askCompanionQuestion as jest.MockedFunction<typeof askCompanionQuestion>;

function session(overrides: Partial<AskCompanionSessionResponse> = {}): AskCompanionSessionResponse {
  return {
    sessionId: "session-1",
    collectionId: "collection-1",
    status: "ACTIVE",
    turnCount: 0,
    turnLimit: 6,
    turnsRemaining: 6,
    turns: [],
    usedThisMonth: 1,
    monthlyLimit: 20,
    usagePeriodEndsAt: "2026-08-01T00:00:00Z",
    ...overrides,
  };
}

describe("AskCompanionPanel", () => {
  beforeEach(() => {
    getActiveMock.mockReset();
    startMock.mockReset();
    askMock.mockReset();
    getActiveMock.mockResolvedValue(null);
  });

  it("shows the plan-aware Plus upgrade prompt to Free users without loading a session", () => {
    render(<AskCompanionPanel collectionId="collection-1" currentPlan="FREE" />);

    expect(screen.getByText("Unlock Ask Companion — get Plus")).toHaveAttribute(
      "href",
      "/settings?section=plans",
    );
    expect(getActiveMock).not.toHaveBeenCalled();
  });

  it("restores an active conversation and its two prior turns on load", async () => {
    getActiveMock.mockResolvedValue(session({
      turnCount: 2,
      turnsRemaining: 4,
      turns: [
        { question: "Where do I start?", answer: "Start with the overview.", createdAt: "2026-07-29T01:00:00Z" },
        { question: "What should I avoid?", answer: "Avoid passive reading.", createdAt: "2026-07-29T01:01:00Z" },
      ],
    }));

    render(<AskCompanionPanel collectionId="collection-1" currentPlan="PLUS" />);

    expect(await screen.findByText("Where do I start?")).toBeInTheDocument();
    expect(screen.getByText("Avoid passive reading.")).toBeInTheDocument();
    expect(screen.getByText("4 turns remaining")).toBeInTheDocument();
  });

  it("shows a retry state when loading the active session fails", async () => {
    getActiveMock.mockRejectedValueOnce(new Error("Network unavailable"));
    getActiveMock.mockResolvedValueOnce(null);

    render(<AskCompanionPanel collectionId="collection-1" currentPlan="PRO" />);

    expect(await screen.findByRole("heading", { name: "Couldn't load Ask Companion" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByRole("heading", { name: "Ask about this Review Set" })).toBeInTheDocument();
    expect(getActiveMock).toHaveBeenCalledTimes(2);
  });

  it("preserves the typed question when asking fails", async () => {
    startMock.mockResolvedValue(session());
    askMock.mockRejectedValue(new Error("Temporary failure"));

    render(<AskCompanionPanel collectionId="collection-1" currentPlan="PLUS" />);

    const input = await screen.findByLabelText("Your question");
    fireEvent.change(input, { target: { value: "What should I focus on?" } });
    fireEvent.click(screen.getByRole("button", { name: "Ask Companion" }));

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Temporary failure"));
    expect(input).toHaveValue("What should I focus on?");
  });

  it("starts a new quota-counted session when asking after the six-turn cap", async () => {
    getActiveMock.mockResolvedValue(session({
      status: "ENDED",
      turnCount: 6,
      turnsRemaining: 0,
    }));
    const newSession = session({ sessionId: "session-2", usedThisMonth: 2 });
    startMock.mockResolvedValue(newSession);
    askMock.mockResolvedValue(session({
      sessionId: "session-2",
      turnCount: 1,
      turnsRemaining: 5,
      usedThisMonth: 2,
      turns: [{
        question: "Can we continue?",
        answer: "Yes, using the authored guide.",
        createdAt: "2026-07-29T02:00:00Z",
      }],
    }));

    render(<AskCompanionPanel collectionId="collection-1" currentPlan="PLUS" />);

    expect(await screen.findByText("This conversation has reached its limit.")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Your question"), { target: { value: "Can we continue?" } });
    fireEvent.click(screen.getByRole("button", { name: "Start new conversation" }));

    await waitFor(() => expect(startMock).toHaveBeenCalledWith("collection-1"));
    expect(askMock).toHaveBeenCalledWith("session-2", "Can we continue?");
    expect(await screen.findByText("Yes, using the authored guide.")).toBeInTheDocument();
  });

  it("shows quota exhaustion separately without implying Pro has more sessions", async () => {
    startMock.mockRejectedValue(new ApiRequestError("Quota reached", {
      code: "ASK_COMPANION_QUOTA_EXHAUSTED",
      status: 403,
    }));

    render(<AskCompanionPanel collectionId="collection-1" currentPlan="PLUS" />);

    fireEvent.change(await screen.findByLabelText("Your question"), { target: { value: "Question 21" } });
    fireEvent.click(screen.getByRole("button", { name: "Ask Companion" }));

    expect(await screen.findByText("Monthly Ask Companion limit reached")).toBeInTheDocument();
    expect(screen.getByText(/Plus and Pro both include 20 sessions/)).toBeInTheDocument();
    expect(screen.queryByText("Upgrade to Pro")).not.toBeInTheDocument();
  });
});
