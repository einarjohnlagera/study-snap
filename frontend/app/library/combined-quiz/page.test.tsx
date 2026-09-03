import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import CombinedQuizBuilderPage from "./page";
import { ApiRequestError, createCombinedQuiz, listNotes } from "@/lib/api";

const pushMock = jest.fn();
let notesParam = "note-ready,note-unready";

jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
  useSearchParams: () => new URLSearchParams(`notes=${notesParam}`),
}));

jest.mock("@/lib/route-guards", () => ({
  requireAuthenticatedOnboardedUser: () => true,
}));

jest.mock("@/lib/api", () => {
  class ApiRequestError extends Error {
    code: string | null;
    status: number;

    constructor(message: string, options: { code?: string; status?: number } = {}) {
      super(message);
      this.code = options.code ?? null;
      this.status = options.status ?? 500;
    }
  }
  return {
    ApiRequestError,
    createCombinedQuiz: jest.fn(),
    listNotes: jest.fn(),
  };
});

function note(id: string, questionCount: number | null, generatedQuizId = questionCount === null ? null : `quiz-${id}`) {
  return {
    id,
    title: `Note ${id}`,
    generatedQuizId,
    generatedQuizQuestionCount: questionCount,
  };
}

describe("CombinedQuizBuilderPage", () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    notesParam = "note-ready,note-unready";
    pushMock.mockReset();
    (listNotes as jest.Mock).mockReset().mockResolvedValue([
      note("note-ready", 3),
      note("note-unready", null),
    ]);
    (createCombinedQuiz as jest.Mock).mockReset().mockResolvedValue({
      id: "combined-1",
      title: "Learner review",
      sections: [],
      createdAt: "2026-09-03T00:00:00Z",
    });
  });

  it("shows a retry state for a transient notes load failure instead of a not-found state", async () => {
    (listNotes as jest.Mock).mockRejectedValue(new Error("Network unavailable"));
    render(<CombinedQuizBuilderPage />);

    expect(await screen.findByRole("heading", { name: "Could not load selected notes" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
    expect(screen.queryByText(/not found/i)).not.toBeInTheDocument();
  });

  it("filters ineligible notes and assembles every question with the entered title", async () => {
    render(<CombinedQuizBuilderPage />);

    expect(await screen.findByText(/1 selected note has no generated quiz and will not be included/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Quiz title"), { target: { value: "Learner review" } });
    fireEvent.click(screen.getByRole("button", { name: "Assemble quiz" }));

    await waitFor(() => expect(createCombinedQuiz).toHaveBeenCalledWith({
      title: "Learner review",
      sections: [{ noteId: "note-ready", questionIndexes: [0, 1, 2] }],
    }));
    expect(pushMock).toHaveBeenCalledWith("/library/combined-quiz/combined-1");
  });

  /**
   * ⚠️ A combined quiz row is IMMUTABLE, so a duplicate POST orphans the first row permanently and nothing
   * can clean it up.
   *
   * <p>The guarantee is held jointly by the `assembling` state in `canAssemble` AND `Button`'s
   * `disabled={loading || disabled}`. That was verified by mutation rather than assumed: removing EITHER one
   * alone still passes, and removing BOTH reds this test — so this pins the outcome, not one mechanism. A
   * stronger ref-based guard was written, shown unnecessary by the same mutation, and removed rather than
   * shipped as code whose necessity could not be demonstrated.
   */
  it("assembles once when the button is double-clicked before the first request settles", async () => {
    let releaseAssemble: ((value: unknown) => void) | undefined;
    (createCombinedQuiz as jest.Mock).mockImplementation(() => new Promise((resolve) => {
      releaseAssemble = () => resolve({ id: "combined-1", title: "Learner review", sections: [], createdAt: "2026-09-03T00:00:00Z" });
    }));
    render(<CombinedQuizBuilderPage />);
    await screen.findByText(/1 selected note has no generated quiz/);
    fireEvent.change(screen.getByLabelText("Quiz title"), { target: { value: "Learner review" } });

    const assembleButton = screen.getByRole("button", { name: "Assemble quiz" });
    fireEvent.click(assembleButton);
    fireEvent.click(assembleButton);

    expect(createCombinedQuiz).toHaveBeenCalledTimes(1);
    releaseAssemble?.(undefined);
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/library/combined-quiz/combined-1"));
  });

  it("blocks an over-cap selection before a request is sent", async () => {
    notesParam = "note-ready";
    (listNotes as jest.Mock).mockResolvedValue([note("note-ready", 101)]);

    render(<CombinedQuizBuilderPage />);

    expect(await screen.findByText(/selection exceeds the combined-quiz limit/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Assemble quiz" })).toHaveAttribute("aria-disabled", "true");
    fireEvent.click(screen.getByRole("button", { name: "Assemble quiz" }));
    expect(createCombinedQuiz).not.toHaveBeenCalled();
  });

  it("preserves the selection and entered title when the server rejects a changed source quiz", async () => {
    (createCombinedQuiz as jest.Mock).mockRejectedValue(new ApiRequestError("Invalid", {
      code: "COMBINED_QUIZ_INVALID",
      status: 400,
    }));
    render(<CombinedQuizBuilderPage />);

    await screen.findByText("Confirm your quiz");
    fireEvent.change(screen.getByLabelText("Quiz title"), { target: { value: "Still here" } });
    fireEvent.click(screen.getByRole("button", { name: "Assemble quiz" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/source quiz changed length/);
    expect(screen.getByDisplayValue("Still here")).toBeInTheDocument();
    expect(screen.getByText(/1 eligible source note/)).toBeInTheDocument();
  });

});
