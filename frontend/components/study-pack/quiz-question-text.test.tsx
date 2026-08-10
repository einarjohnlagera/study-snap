import { render, screen } from "@testing-library/react";
import { QuizQuestionText } from "./quiz-question-text";
import { applyInlineDisplayStyle } from "./quiz-working-solution";

describe("QuizQuestionText", () => {
  // The model emits inline LaTeX for algebraic prompts. Before this was wired up, a question read
  // literally as "simplify \(\frac{x^3 - 4x^2 + 5x}{x - 2}\)?" in Quick Review.
  it("renders inline LaTeX as math instead of raw markup", () => {
    const { container } = render(
      <QuizQuestionText text={"Which operation simplifies \\(\\frac{x^3 - 4x^2 + 5x}{x - 2}\\)?"} />,
    );

    expect(container.querySelector(".katex")).toBeInTheDocument();
    // Assert against the VISUAL branch only. KaTeX's `htmlAndMathml` output also emits a MathML
    // <annotation> carrying the original TeX source -- that is standard, is never rendered, and is what
    // assistive tech may fall back to. Searching the whole DOM for raw markup would flag it wrongly.
    const visualBranch = container.querySelector(".katex-html");
    expect(visualBranch?.textContent).not.toContain("\\frac");
    expect(visualBranch?.textContent).not.toContain("\\(");
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

  // A `$` immediately after a binary operator is a new amount, not a closing delimiter. The first version
  // of the currency rule only rejected whitespace, so "$10-$20" captured "10-" as LaTeX -- which KaTeX
  // renders happily (a trailing binary operator is legal), so the error fallback never fired and the
  // reader saw a subtraction with both dollar signs swallowed.
  it.each([
    ["Prices range $10-$20 per unit.", "Prices range $10-$20 per unit."],
    ["Compare $5+$3 against $8.", "Compare $5+$3 against $8."],
    ["Margin fell from $12/$4 last year.", "Margin fell from $12/$4 last year."],
  ])("keeps operator-separated currency as plain text: %s", (input, expected) => {
    const { container } = render(<QuizQuestionText text={input} />);

    expect(container.querySelector(".katex")).not.toBeInTheDocument();
    expect(container.textContent).toBe(expected);
  });

  it("still renders genuine inline dollar math", () => {
    const { container } = render(<QuizQuestionText text={"Simplify $x^2 + 2x$ fully."} />);

    expect(container.querySelector(".katex")).toBeInTheDocument();
    expect(container.textContent).not.toContain("$");
  });

  // Inline KaTeX renders \frac numerator/denominator at script size, so a fraction in a question stem
  // reads much smaller than the words around it. \displaystyle restores full size while staying inline.
  // These assert the SELECTION LOGIC only -- whether it looks right is a visual judgement, not a test.
  describe("inline display style", () => {
    it("promotes size-collapsing constructs so they are not rendered at script size", () => {
      expect(applyInlineDisplayStyle("\\frac{x^3 - 4x^2 + 5x}{x - 2}", false))
        .toBe("\\displaystyle \\frac{x^3 - 4x^2 + 5x}{x - 2}");
      expect(applyInlineDisplayStyle("\\sum_{i=1}^{n} i", false)).toContain("\\displaystyle");
      expect(applyInlineDisplayStyle("\\int_0^1 x\\,dx", false)).toContain("\\displaystyle");
    });

    it("leaves ordinary inline math untouched, so simple variables do not grow", () => {
      expect(applyInlineDisplayStyle("x", false)).toBe("x");
      expect(applyInlineDisplayStyle("x^2 + y^2", false)).toBe("x^2 + y^2");
      // A longer macro that merely starts with one of the names must not match.
      expect(applyInlineDisplayStyle("\\intercal", false)).toBe("\\intercal");
    });

    it("leaves block math untouched, since it is already display style", () => {
      expect(applyInlineDisplayStyle("\\frac{a}{b}", true)).toBe("\\frac{a}{b}");
    });

    // The helper tests above pass even if nothing calls it, so this asserts the WIRING through the real
    // render. KaTeX emits a `reset-size6 size3` sizing span for a text-style fraction (the script-sized
    // numerator/denominator that made this look small) and omits it in display style.
    // Deliberately a fraction with no superscripts: KaTeX also emits that sizing span for exponents,
    // which are legitimately script-sized, so `x^3` in the stem would match for the wrong reason.
    it("renders a fraction in a question at full size, not script size", () => {
      const { container } = render(<QuizQuestionText text={"Which operation simplifies $\\frac{a + b}{c - d}$?"} />);

      expect(container.querySelector(".katex")).toBeInTheDocument();
      expect(container.querySelector(".mfrac")).toBeInTheDocument();
      expect(container.querySelector(".reset-size6.size3")).not.toBeInTheDocument();
    });
  });

  it("still splits Statement-labelled prompts onto their own lines", () => {
    render(
      <QuizQuestionText text={"Evaluate both.\nStatement 1: Water boils at 100C.\nStatement 2: Ice melts at 0C."} />,
    );

    expect(screen.getByText(/Statement 1:/)).toBeInTheDocument();
    expect(screen.getByText(/Statement 2:/)).toBeInTheDocument();
  });
});
