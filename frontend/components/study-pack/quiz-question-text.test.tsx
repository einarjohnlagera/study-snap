import { render, screen } from "@testing-library/react";
import { QuizQuestionText } from "./quiz-question-text";

describe("QuizQuestionText", () => {
  // The model emits inline LaTeX for algebraic prompts. Before this was wired up, a question read
  // literally as "simplify \(\frac{x^3 - 4x^2 + 5x}{x - 2}\)?" in Quick Review.
  it("renders inline LaTeX as math instead of raw markup", () => {
    const { container } = render(
      <QuizQuestionText text={"Which operation simplifies \\(\\frac{x^3 - 4x^2 + 5x}{x - 2}\\)?"} />,
    );

    expect(container.querySelector(".katex")).toBeInTheDocument();
    expect(screen.queryByText(/\\frac/)).not.toBeInTheDocument();
    expect(screen.queryByText(/\\\(/)).not.toBeInTheDocument();
  });

  // Plain text must come back as a bare string, not wrapped in an extra element: wrapping moves which
  // node getByText resolves to, which silently relocates styling like break-words off the element
  // callers put it on.
  it("leaves text without math completely unwrapped", () => {
    const { container } = render(<QuizQuestionText text="Which organ oxygenates blood?" />);

    expect(container.querySelector(".katex")).not.toBeInTheDocument();
    expect(container.textContent).toBe("Which organ oxygenates blood?");
    expect(container.querySelector("span")).not.toBeInTheDocument();
  });

  it("still splits Statement-labelled prompts onto their own lines", () => {
    render(
      <QuizQuestionText text={"Evaluate both.\nStatement 1: Water boils at 100C.\nStatement 2: Ice melts at 0C."} />,
    );

    expect(screen.getByText(/Statement 1:/)).toBeInTheDocument();
    expect(screen.getByText(/Statement 2:/)).toBeInTheDocument();
  });
});
