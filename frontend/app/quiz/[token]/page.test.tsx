import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import SharedQuizPage from "./page";
import { getPublicSharedQuiz, getSharedQuizResults, trackAnalyticsEvent } from "@/lib/api";

jest.mock("next/navigation", () => ({
  useParams: () => ({ token: "tok123" }),
}));

let mockAuthUser: unknown = null;

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => mockAuthUser,
}));

jest.mock("@/components/notes/public-library-copy-action", () => ({
  PublicLibraryCopyAction: ({ noteId }: { noteId: string }) => (
    <button type="button" data-testid={`copy-action-${noteId}`}>Add to Library</button>
  ),
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
const trackAnalyticsEventMock = trackAnalyticsEvent as jest.MockedFunction<typeof trackAnalyticsEvent>;

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

function stubQuiz(questions: unknown[], sourceNotes: unknown[] = []) {
  getPublicSharedQuizMock.mockResolvedValue({
    quizId: "quiz-1",
    noteTitle: "Cell Structure",
    questions,
    sourceNotes,
  } as never);
}

async function completeSingleQuestionQuiz() {
  await screen.findByText("Which one?");
  fireEvent.click(screen.getByRole("button", { name: /Bravo/ }));
  fireEvent.click(screen.getByRole("button", { name: "Submit Answers" }));
  await screen.findByText("Quiz Complete");
}

beforeEach(() => {
  jest.clearAllMocks();
  mockAuthUser = null;
  getSharedQuizResultsMock.mockResolvedValue({ score: 1, total: 1, items: [] } as never);
});

describe("shared quiz results — continue learning", () => {
  // ⚠️ THE PRE-DECLARED DISCRIMINATING GUARD. A PUBLIC-source fixture passes under a version that leaks
  // every source, so the EMPTY case is what pins the rule. Private sources are omitted entirely by the
  // server, and an empty list must render NOTHING -- no heading, no placeholder, no "1 source is private".
  it("renders no continue-learning affordance when there is no eligible source", async () => {
    stubQuiz([SINGLE_CHOICE_QUESTION], []);
    render(<SharedQuizPage />);
    await completeSingleQuestionQuiz();

    expect(screen.queryByText("Keep learning")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "View Note" })).not.toBeInTheDocument();
  });

  it("offers an anonymous recipient the public note, never a copy action", async () => {
    stubQuiz([SINGLE_CHOICE_QUESTION], [{ id: "note-9", title: "Cell Structure" }]);
    render(<SharedQuizPage />);
    await completeSingleQuestionQuiz();

    const viewNote = screen.getByRole("link", { name: "View Note" });
    expect(viewNote).toHaveAttribute("href", "/public/notes/note-9");
    expect(screen.queryByTestId("copy-action-note-9")).not.toBeInTheDocument();
  });

  it("offers an authenticated recipient the copy action instead", async () => {
    mockAuthUser = { id: "user-1" };
    stubQuiz([SINGLE_CHOICE_QUESTION], [{ id: "note-9", title: "Cell Structure" }]);
    render(<SharedQuizPage />);
    await completeSingleQuestionQuiz();

    expect(await screen.findByTestId("copy-action-note-9")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "View Note" })).not.toBeInTheDocument();
  });

  // ⚠️ THIS GUARD EXISTS BECAUSE ITS ABSENCE ALREADY COST A DEFECT. The v0.121.0 slice-2 change intended to
  // make this 54-character CTA wrap, but the edit landed on the SHORT "Learn about NoteLib" button on the
  // inactive-link screen instead. The buttonVariants unit tests passed because they exercise the helper
  // directly; nothing asserted the CTA itself, so the real overflow shipped unfixed.
  it("lets the long results call-to-action wrap instead of overflowing", async () => {
    stubQuiz([SINGLE_CHOICE_QUESTION], []);
    render(<SharedQuizPage />);
    await completeSingleQuestionQuiz();

    const cta = screen.getByRole("link", { name: /Save your score/ });
    expect(cta.className).toContain("whitespace-normal");
    expect(cta.className).not.toContain("whitespace-nowrap");
  });
});

describe("shared quiz page", () => {
  // The concept used to render as a bare string directly under the stem, so it read as a second sentence
  // OF the question. Asserting only that the concept text appears would pass under that defect -- the
  // label is the whole fix, so the label is what is asserted.
  it("labels the concept so it does not read as part of the question", async () => {
    stubQuiz([SINGLE_CHOICE_QUESTION]);

    render(<SharedQuizPage />);

    expect(await screen.findByText("Which one?")).toBeInTheDocument();
    expect(screen.getByText("Topic")).toBeInTheDocument();
    expect(screen.getByText("Concept")).toBeInTheDocument();
  });

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

  // ⚠️ REWRITTEN, NOT DELETED (v0.121.0). This test previously asserted
  // `expect(getByRole("button", { name: /Charlie/ })).toBeDisabled()` under the comment "Single-choice
  // selection stays one-shot, exactly as it shipped" -- i.e. it PINNED the defect. A single-choice answer
  // locked every other choice the moment it was made, so a recipient who misclicked could never correct it.
  // The answers-slot half of the assertion is genuine and is kept.
  it("commits a single-choice answer to its slot and lets the recipient change it", async () => {
    stubQuiz([SINGLE_CHOICE_QUESTION]);
    render(<SharedQuizPage />);
    await screen.findByText("Which one?");

    expect(screen.queryByText("Select all that apply")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Bravo/ }));
    expect(screen.getByRole("button", { name: /Bravo/ })).toHaveAttribute("aria-pressed", "true");

    // The correction the recipient could not previously make.
    expect(screen.getByRole("button", { name: /Charlie/ })).not.toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: /Charlie/ }));
    expect(screen.getByRole("button", { name: /Charlie/ })).toHaveAttribute("aria-pressed", "true");

    fireEvent.click(screen.getByRole("button", { name: "Submit Answers" }));

    await waitFor(() => expect(getSharedQuizResultsMock).toHaveBeenCalledWith("tok123", [2], [null]));
  });

  it("records a completion once a recipient's answers are graded", async () => {
    stubQuiz([SINGLE_CHOICE_QUESTION]);
    getSharedQuizResultsMock.mockResolvedValue({ score: 1, total: 1, items: [] } as never);
    render(<SharedQuizPage />);
    await screen.findByText("Which one?");

    fireEvent.click(screen.getByRole("button", { name: /Bravo/ }));
    fireEvent.click(screen.getByRole("button", { name: "Submit Answers" }));

    await waitFor(() => {
      expect(trackAnalyticsEventMock).toHaveBeenCalledWith({
        eventType: "QUIZ_SHARE_LINK_COMPLETED",
        entityId: "quiz-1",
        metadata: { token: "tok123", score: 1, total: 1 },
      });
    });
  });

  // ⚠️ The discriminating half. An event fired before/regardless of grading would satisfy the test above
  // while inflating the completion rate with submissions that never produced a score -- which is exactly
  // the metric the release checkpoint reads.
  it("records no completion when grading fails", async () => {
    stubQuiz([SINGLE_CHOICE_QUESTION]);
    getSharedQuizResultsMock.mockRejectedValue(new Error("boom") as never);
    render(<SharedQuizPage />);
    await screen.findByText("Which one?");

    fireEvent.click(screen.getByRole("button", { name: /Bravo/ }));
    fireEvent.click(screen.getByRole("button", { name: "Submit Answers" }));

    expect(await screen.findByText("Could not submit answers. Please try again.")).toBeInTheDocument();
    expect(
      trackAnalyticsEventMock.mock.calls.some(([call]) => call?.eventType === "QUIZ_SHARE_LINK_COMPLETED"),
    ).toBe(false);
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

  // ⚠️ THE DISCRIMINATING GUARD FOR INDEX-ADDRESSED ANSWERS. Under the previous append-only model
  // (`[...answers, x]`) a revisited question appended a NEW entry instead of overwriting its own slot, so
  // the array grew past `questions.length` and the server rejected the submit outright. A forward-only
  // walk passes under an off-by-one in the index write and proves nothing.
  it("grades the changed answer when a recipient goes back and corrects one", async () => {
    stubQuiz([SINGLE_CHOICE_QUESTION, MULTI_SELECT_QUESTION]);
    render(<SharedQuizPage />);
    await screen.findByText("Which one?");

    fireEvent.click(screen.getByRole("button", { name: /Alpha/ }));
    fireEvent.click(screen.getByRole("button", { name: "Next Question" }));

    await screen.findByText("Select all that apply");
    fireEvent.click(screen.getByRole("button", { name: /Bravo/ }));

    fireEvent.click(screen.getByRole("button", { name: "Back" }));
    await screen.findByText("Which one?");
    // The prior selection is restored rather than lost.
    expect(screen.getByRole("button", { name: /Alpha/ })).toHaveAttribute("aria-pressed", "true");

    fireEvent.click(screen.getByRole("button", { name: /Delta/ }));
    fireEvent.click(screen.getByRole("button", { name: "Next Question" }));
    await screen.findByText("Select all that apply");
    // Returning forward restores the multi-select selection too.
    expect(screen.getByRole("button", { name: /Bravo/ })).toHaveAttribute("aria-pressed", "true");

    fireEvent.click(screen.getByRole("button", { name: "Submit Answers" }));

    await waitFor(() => {
      // Full-length, pairing intact, and the CHANGED single-choice answer (3, not 0) is the one sent.
      expect(getSharedQuizResultsMock).toHaveBeenCalledWith("tok123", [3, null], [null, [1]]);
    });
  });

  it("offers no Back control on the first question", async () => {
    stubQuiz([SINGLE_CHOICE_QUESTION, MULTI_SELECT_QUESTION]);
    render(<SharedQuizPage />);
    await screen.findByText("Which one?");

    expect(screen.queryByRole("button", { name: "Back" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Alpha/ }));
    fireEvent.click(screen.getByRole("button", { name: "Next Question" }));

    expect(await screen.findByRole("button", { name: "Back" })).toBeInTheDocument();
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
