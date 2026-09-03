import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import SharedQuizPage from "./page";
import { getPublicSharedQuiz, getSharedQuizResults } from "@/lib/api";

jest.mock("next/navigation", () => ({
  useParams: () => ({ token: "tok123" }),
}));

jest.mock("@/lib/api", () => ({
  ApiRequestError: class ApiRequestError extends Error {
    status: number;
    constructor(status: number) {
      super("api error");
      this.status = status;
    }
  },
  getPublicSharedQuiz: jest.fn(),
  getSharedQuizResults: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

const getPublicSharedQuizMock = getPublicSharedQuiz as jest.MockedFunction<typeof getPublicSharedQuiz>;
const getSharedQuizResultsMock = getSharedQuizResults as jest.MockedFunction<typeof getSharedQuizResults>;

const MULTI_SELECT_QUESTION = {
  question: "Which apply?",
  choices: ["Alpha", "Bravo", "Charlie", "Delta"],
  concept: "Concept",
  questionFormat: "MULTI_SELECT",
};

const SINGLE_CHOICE_QUESTION = {
  question: "Which one?",
  choices: ["Alpha", "Bravo", "Charlie", "Delta"],
  concept: "Concept",
  questionFormat: "MCQ",
};

function stubQuiz(questions: unknown[]) {
  getPublicSharedQuizMock.mockResolvedValue({
    quizId: "quiz-1",
    noteTitle: "Cell Structure",
    questions,
  } as never);
}

beforeEach(() => {
  jest.clearAllMocks();
  getSharedQuizResultsMock.mockResolvedValue({ score: 1, total: 1, items: [] } as never);
});

describe("shared quiz page", () => {
  it("lets a recipient select several choices on a MULTI_SELECT question and submits the whole set", async () => {
    stubQuiz([MULTI_SELECT_QUESTION]);
    render(<SharedQuizPage />);
    await screen.findByText("Select all that apply");

    fireEvent.click(screen.getByRole("button", { name: /Alpha/ }));
    fireEvent.click(screen.getByRole("button", { name: /Charlie/ }));

    // Both stay selected. Under single-choice handling the second click replaced the first, which is the
    // recipient-facing half of the mis-grading defect: the correct set was unreachable.
    expect(screen.getByRole("button", { name: /Alpha/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: /Charlie/ })).toHaveAttribute("aria-pressed", "true");

    fireEvent.click(screen.getByRole("button", { name: "Submit Answers" }));

    await waitFor(() => expect(getSharedQuizResultsMock).toHaveBeenCalled());
    expect(getSharedQuizResultsMock).toHaveBeenCalledWith("tok123", [null], [[0, 2]]);
  });

  it("deselects a MULTI_SELECT choice on a second click", async () => {
    stubQuiz([MULTI_SELECT_QUESTION]);
    render(<SharedQuizPage />);
    await screen.findByText("Select all that apply");

    fireEvent.click(screen.getByRole("button", { name: /Alpha/ }));
    fireEvent.click(screen.getByRole("button", { name: /Charlie/ }));
    fireEvent.click(screen.getByRole("button", { name: /Alpha/ }));

    expect(screen.getByRole("button", { name: /Alpha/ })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "Submit Answers" })).toBeEnabled();

    fireEvent.click(screen.getByRole("button", { name: "Submit Answers" }));

    await waitFor(() => expect(getSharedQuizResultsMock).toHaveBeenCalledWith("tok123", [null], [[2]]));
  });

  it("keeps Submit disabled until a MULTI_SELECT question has at least one selection", async () => {
    stubQuiz([MULTI_SELECT_QUESTION]);
    render(<SharedQuizPage />);
    await screen.findByText("Select all that apply");

    expect(screen.getByRole("button", { name: "Submit Answers" })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: /Bravo/ }));

    expect(screen.getByRole("button", { name: "Submit Answers" })).toBeEnabled();
  });

  it("still commits a single-choice answer on click and sends it in the answers slot", async () => {
    stubQuiz([SINGLE_CHOICE_QUESTION]);
    render(<SharedQuizPage />);
    await screen.findByText("Which one?");

    expect(screen.queryByText("Select all that apply")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Bravo/ }));
    // Single-choice selection stays one-shot, exactly as it shipped.
    expect(screen.getByRole("button", { name: /Charlie/ })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: "Submit Answers" }));

    await waitFor(() => expect(getSharedQuizResultsMock).toHaveBeenCalledWith("tok123", [1], [null]));
  });

  it("aligns answers and multiAnswers positionally across a mixed quiz", async () => {
    stubQuiz([SINGLE_CHOICE_QUESTION, MULTI_SELECT_QUESTION]);
    render(<SharedQuizPage />);
    await screen.findByText("Which one?");

    fireEvent.click(screen.getByRole("button", { name: /Delta/ }));
    fireEvent.click(screen.getByRole("button", { name: "Next Question" }));

    await screen.findByText("Select all that apply");
    fireEvent.click(screen.getByRole("button", { name: /Alpha/ }));
    fireEvent.click(screen.getByRole("button", { name: /Bravo/ }));
    fireEvent.click(screen.getByRole("button", { name: "Submit Answers" }));

    await waitFor(() => expect(getSharedQuizResultsMock).toHaveBeenCalledWith("tok123", [3, null], [null, [0, 1]]));
  });

  it("counts a MULTI_SELECT question as answered once any box is checked", async () => {
    stubQuiz([MULTI_SELECT_QUESTION, SINGLE_CHOICE_QUESTION]);
    render(<SharedQuizPage />);
    await screen.findByText("Select all that apply");

    expect(screen.getByText(/0 answered/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Alpha/ }));
    expect(screen.getByText(/1 answered/)).toBeInTheDocument();

    // A second box is still the same one answered question, not two.
    fireEvent.click(screen.getByRole("button", { name: /Charlie/ }));
    expect(screen.getByText(/1 answered/)).toBeInTheDocument();
  });

  it("marks every correct choice of a MULTI_SELECT question on the review screen", async () => {
    stubQuiz([MULTI_SELECT_QUESTION]);
    getSharedQuizResultsMock.mockResolvedValue({
      score: 1,
      total: 1,
      items: [{ correct: true, correctIndex: 0, correctIndices: [0, 2], explanation: "Because A and C" }],
    } as never);
    render(<SharedQuizPage />);
    await screen.findByText("Select all that apply");

    fireEvent.click(screen.getByRole("button", { name: /Alpha/ }));
    fireEvent.click(screen.getByRole("button", { name: /Charlie/ }));
    fireEvent.click(screen.getByRole("button", { name: "Submit Answers" }));

    await screen.findByText("Score: 1 / 1 correct");
    // correctIndex alone would mark only Alpha, understating the answer key the recipient is reviewing.
    expect(screen.getAllByText("Correct")).toHaveLength(2);
  });

  it("marks the single correct choice when the result carries no correct-answer set", async () => {
    stubQuiz([SINGLE_CHOICE_QUESTION]);
    getSharedQuizResultsMock.mockResolvedValue({
      score: 0,
      total: 1,
      items: [{ correct: false, correctIndex: 2, correctIndices: [], explanation: "Because C" }],
    } as never);
    render(<SharedQuizPage />);
    await screen.findByText("Which one?");

    fireEvent.click(screen.getByRole("button", { name: /Alpha/ }));
    fireEvent.click(screen.getByRole("button", { name: "Submit Answers" }));

    await screen.findByText("Score: 0 / 1 correct");
    expect(screen.getAllByText("Correct")).toHaveLength(1);
    expect(screen.getByText("Your answer")).toBeInTheDocument();
  });
});
