import { fireEvent, render, screen } from "@testing-library/react";
import { PublicMiniQuizPreview } from "./public-mini-quiz-preview";
import { PUBLIC_LIBRARY_RETURN_URL_STORAGE_KEY } from "@/lib/public-library-url";

let currentAuthUser: { id: string } | null = null;

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(() => currentAuthUser),
}));

const publicSeoCopyCtaMock = jest.fn(
  ({ label }: { label?: string }) => <button type="button">{label ?? "Copy to My Library"}</button>,
);

jest.mock("./public-seo-copy-cta", () => ({
  PublicSeoCopyCta: (props: {
    label?: string;
    redirectTarget?: "library" | "generate" | "quick-review";
  }) => publicSeoCopyCtaMock(props),
}));

const makeQuestion = (n: number) => ({
  question: `Question ${n}`,
  choices: ["Choice A", "Choice B", "Choice C", "Choice D"],
  correctIndex: 0,
  answer: "Choice A",
  explanation: `Explanation for question ${n}.`,
});

describe("PublicMiniQuizPreview", () => {
  const singleQuiz = [makeQuestion(1)];
  const multiQuiz = [makeQuestion(1), makeQuestion(2), makeQuestion(3)];

  beforeEach(() => {
    currentAuthUser = null;
    publicSeoCopyCtaMock.mockClear();
    window.sessionStorage.clear();
  });

  function completeSingleQuestionQuiz(relatedNotes?: Parameters<typeof PublicMiniQuizPreview>[0]["relatedNotes"]) {
    render(<PublicMiniQuizPreview quiz={singleQuiz} noteId="note-1" relatedNotes={relatedNotes} />);
    fireEvent.click(screen.getByRole("button", { name: /Choice A/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));
    fireEvent.click(screen.getByRole("button", { name: "See Results" }));
  }

  it("renders nothing when quiz is empty", () => {
    const { container } = render(<PublicMiniQuizPreview quiz={[]} noteId="note-1" />);
    expect(container).toBeEmptyDOMElement();
  });

  it("renders the quick check heading and first question", () => {
    render(<PublicMiniQuizPreview quiz={singleQuiz} noteId="note-1" />);

    expect(screen.getByRole("heading", { name: "🧠 Quick Check" })).toBeInTheDocument();
    expect(screen.getByText("See what you remember from the summary.")).toBeInTheDocument();
    expect(screen.getByText("Question 1")).toBeInTheDocument();
  });

  it("shows a progress badge for multi-question quizzes", () => {
    render(<PublicMiniQuizPreview quiz={multiQuiz} noteId="note-1" />);
    expect(screen.getByText("1 / 3")).toBeInTheDocument();
  });

  it("hides the progress badge for single-question quizzes", () => {
    render(<PublicMiniQuizPreview quiz={singleQuiz} noteId="note-1" />);
    expect(screen.queryByText(/\d+ \/ \d+/)).not.toBeInTheDocument();
  });

  it("does not show the completion state or CTAs before answering", () => {
    render(<PublicMiniQuizPreview quiz={singleQuiz} noteId="note-1" />);
    expect(screen.queryByText("🎉 Quick Check Complete")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Quiz yourself on this note/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Copy to My Library/i })).not.toBeInTheDocument();
  });

  it("shows feedback and Next Question button after answering a non-last question", () => {
    render(<PublicMiniQuizPreview quiz={multiQuiz} noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: /Choice A/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));

    expect(screen.queryByRole("button", { name: "Check Answer" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next Question →" })).toBeInTheDocument();
    expect(screen.queryByText("🎉 Quick Check Complete")).not.toBeInTheDocument();
    // Progress badge still shows current question until Next is clicked
    expect(screen.getByText("1 / 3")).toBeInTheDocument();
  });

  it("advances to the next question after clicking Next", () => {
    render(<PublicMiniQuizPreview quiz={multiQuiz} noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: /Choice A/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));
    fireEvent.click(screen.getByRole("button", { name: "Next Question →" }));

    expect(screen.getByText("Question 2")).toBeInTheDocument();
    expect(screen.getByText("2 / 3")).toBeInTheDocument();
  });

  it("shows See Results instead of Next Question on the last question", () => {
    render(<PublicMiniQuizPreview quiz={singleQuiz} noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: /Choice A/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));

    expect(screen.getByRole("button", { name: "See Results" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Next Question →" })).not.toBeInTheDocument();
  });

  it("shows completion state after finishing all questions", () => {
    render(<PublicMiniQuizPreview quiz={singleQuiz} noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: /Choice A/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));
    fireEvent.click(screen.getByRole("button", { name: "See Results" }));

    expect(screen.getByText("🎉 Quick Check Complete")).toBeInTheDocument();
    expect(screen.queryByText("Question 1")).not.toBeInTheDocument();
  });

  it("shows the outcome prompt after a mix of correct and incorrect answers", () => {
    render(<PublicMiniQuizPreview quiz={multiQuiz.slice(0, 2)} noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: /Choice A/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));
    fireEvent.click(screen.getByRole("button", { name: "Next Question →" }));
    fireEvent.click(screen.getByRole("button", { name: /Choice B/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));
    fireEvent.click(screen.getByRole("button", { name: "See Results" }));

    expect(screen.getByText(/Want the full quiz and your results saved\?/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Quiz yourself on this note" })).toBeInTheDocument();
  });

  it("passes the correct CTAs in completion state", () => {
    render(<PublicMiniQuizPreview quiz={singleQuiz} noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: /Choice A/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));
    fireEvent.click(screen.getByRole("button", { name: "See Results" }));

    expect(publicSeoCopyCtaMock).toHaveBeenCalledWith(
      expect.objectContaining({
        label: "Quiz yourself on this note",
        redirectTarget: "quick-review",
        analyticsEvent: "PUBLIC_NOTE_QUIZ_YOURSELF_CLICKED",
      }),
    );
    expect(publicSeoCopyCtaMock).toHaveBeenCalledWith(
      expect.objectContaining({ label: "Copy to My Library" }),
    );
  });

  it("shows the same completion CTAs for signed-in users", () => {
    currentAuthUser = { id: "user-1" };

    render(<PublicMiniQuizPreview quiz={singleQuiz} noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: /Choice A/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));
    fireEvent.click(screen.getByRole("button", { name: "See Results" }));

    expect(screen.getByText("🎉 Quick Check Complete")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Quiz yourself on this note" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Copy to My Library" })).toBeInTheDocument();
  });

  it("works end-to-end through a 3-question quiz", () => {
    render(<PublicMiniQuizPreview quiz={multiQuiz} noteId="note-1" />);

    // Q1
    fireEvent.click(screen.getByRole("button", { name: /Choice A/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));
    fireEvent.click(screen.getByRole("button", { name: "Next Question →" }));

    // Q2
    expect(screen.getByText("Question 2")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Choice A/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));
    fireEvent.click(screen.getByRole("button", { name: "Next Question →" }));

    // Q3 — last question
    expect(screen.getByText("Question 3")).toBeInTheDocument();
    expect(screen.getByText("3 / 3")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Choice A/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));
    fireEvent.click(screen.getByRole("button", { name: "See Results" }));

    expect(screen.getByText("🎉 Quick Check Complete")).toBeInTheDocument();
    expect(
      screen.getByText(/You completed all 3 preview questions/i),
    ).toBeInTheDocument();
  });

  describe("More from {subject} related notes", () => {
    it("prefers the note preview when it clears the minimum length", () => {
      completeSingleQuestionQuiz([
        {
          id: "note-2",
          title: "Cell Membranes",
          subject: "Biology",
          contentPreview: "The cell membrane controls what enters and exits the cell.",
          summaryPreview: "A short summary.",
        },
      ]);

      expect(screen.getByText("Cell Membranes")).toBeInTheDocument();
      expect(
        screen.getByText("The cell membrane controls what enters and exits the cell."),
      ).toBeInTheDocument();
      expect(screen.queryByText("A short summary.")).not.toBeInTheDocument();
    });

    it("falls back to the summary when the note preview is an empty string, not just null/undefined", () => {
      completeSingleQuestionQuiz([
        {
          id: "note-2",
          title: "Cell Membranes",
          subject: "Biology",
          contentPreview: "",
          summaryPreview: "A study-ready summary of cell membranes.",
        },
      ]);

      expect(screen.getByText("A study-ready summary of cell membranes.")).toBeInTheDocument();
    });

    it("falls back to the summary when the note preview is too short to be a real preview", () => {
      completeSingleQuestionQuiz([
        {
          id: "note-2",
          title: "Cell Membranes",
          subject: "Biology",
          contentPreview: "Too short",
          summaryPreview: "A study-ready summary of cell membranes.",
        },
      ]);

      expect(screen.getByText("A study-ready summary of cell membranes.")).toBeInTheDocument();
    });

    it("renders no preview line when both the note and summary are empty", () => {
      completeSingleQuestionQuiz([
        { id: "note-2", title: "Cell Membranes", subject: "Biology", contentPreview: "", summaryPreview: "" },
      ]);

      const card = screen.getByText("Cell Membranes").closest("a");
      expect(card?.querySelector("p:nth-of-type(2)")).toBeNull();
    });

    it("saves the related note's subject-landing page as the Public Library return URL on click", () => {
      completeSingleQuestionQuiz([
        {
          id: "note-2",
          title: "Cell Membranes",
          subject: "Biology",
          contentPreview: "The cell membrane controls what enters and exits the cell.",
          summaryPreview: "A short summary.",
        },
      ]);

      expect(window.sessionStorage.getItem(PUBLIC_LIBRARY_RETURN_URL_STORAGE_KEY)).toBeNull();
      fireEvent.click(screen.getByText("Cell Membranes").closest("a") as HTMLElement);
      expect(window.sessionStorage.getItem(PUBLIC_LIBRARY_RETURN_URL_STORAGE_KEY)).toBe("/public/library/biology");
    });

    it("does not render the related-notes section when there are none", () => {
      completeSingleQuestionQuiz([]);
      expect(screen.queryByText(/More from/)).not.toBeInTheDocument();
    });
  });
});
