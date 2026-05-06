import { fireEvent, render, screen } from "@testing-library/react";
import { PublicMiniQuizPreview } from "./public-mini-quiz-preview";

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
    guestAuthMode?: "login" | "signup";
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
  });

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
    expect(screen.queryByRole("button", { name: /Copy & Start Practicing/i })).not.toBeInTheDocument();
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

  it("passes signup CTAs in completion state for anonymous visitors", () => {
    render(<PublicMiniQuizPreview quiz={singleQuiz} noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: /Choice A/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));
    fireEvent.click(screen.getByRole("button", { name: "See Results" }));

    expect(publicSeoCopyCtaMock).toHaveBeenCalledWith(
      expect.objectContaining({
        label: "Copy & Start Practicing",
        redirectTarget: "quick-review",
        guestAuthMode: "signup",
      }),
    );
    expect(publicSeoCopyCtaMock).toHaveBeenCalledWith(
      expect.objectContaining({
        label: "Copy to My Library",
        guestAuthMode: "signup",
      }),
    );
  });

  it("shows the same completion CTAs for signed-in users", () => {
    currentAuthUser = { id: "user-1" };

    render(<PublicMiniQuizPreview quiz={singleQuiz} noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: /Choice A/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));
    fireEvent.click(screen.getByRole("button", { name: "See Results" }));

    expect(screen.getByText("🎉 Quick Check Complete")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Copy & Start Practicing" })).toBeInTheDocument();
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
});
