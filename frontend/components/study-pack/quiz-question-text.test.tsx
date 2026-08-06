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

  // C4. A lone `$` is currency, not math. The old guard only checked for a delimiter *start*, so these
  // reached the tokenizer: the single-dollar case came back as two spans instead of a bare string —
  // reintroducing the exact break-words displacement the test above guards — and the two-dollar case had
  // the middle of the sentence rendered as italic math with both dollar signs swallowed. Accountancy and
  // Business Administration are seeded programs, so cost questions are routine.
  it("treats a lone dollar sign as currency, not an unclosed math delimiter", () => {
    const { container } = render(<QuizQuestionText text="What is the cost of $5?" />);

    expect(container.querySelector(".katex")).not.toBeInTheDocument();
    expect(container.textContent).toBe("What is the cost of $5?");
    expect(container.querySelector("span")).not.toBeInTheDocument();
  });

  it("does not turn two currency amounts into one math span", () => {
    const { container } = render(<QuizQuestionText text="Item A costs $5 and item B costs $10" />);

    expect(container.querySelector(".katex")).not.toBeInTheDocument();
    expect(container.textContent).toBe("Item A costs $5 and item B costs $10");
    expect(container.querySelector("span")).not.toBeInTheDocument();
  });

  it("still renders genuine inline dollar math", () => {
    const { container } = render(<QuizQuestionText text={"Simplify $x^2 + 2x$ fully."} />);

    expect(container.querySelector(".katex")).toBeInTheDocument();
    expect(container.textContent).not.toContain("$");
  });

  it("still splits Statement-labelled prompts onto their own lines", () => {
    render(
      <QuizQuestionText text={"Evaluate both.\nStatement 1: Water boils at 100C.\nStatement 2: Ice melts at 0C."} />,
    );

    expect(screen.getByText(/Statement 1:/)).toBeInTheDocument();
    expect(screen.getByText(/Statement 2:/)).toBeInTheDocument();
  });
});
