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

  // ⚠️ REGRESSION GUARD, NOT A BUG FIX -- and the distinction is the point of this comment.
  // v0.140.0 went looking for a defect here on the theory that normalizeBareMath was wired to
  // workingSolution alone, because grepping the consuming components for it finds nothing. That
  // inference was WRONG: renderMathText calls it internally (quiz-working-solution.tsx:206), so every
  // call site -- questions, choices, explanations -- already gets the repair. Checking whether the
  // call was THERE instead of reading what the code DOES is the same error CLAUDE.md records against
  // the v0.138.0 pass, which "verified" an unbounded read as unfixed because the branch still existed.
  //
  // The behaviour was nonetheless untested from this component's side, and it is load-bearing:
  // undelimited math is real in production (measured 2026-09-10: 5 packs with bare backslash math in
  // `question`, 15 in `explanation`), and the 11 generation prompts that DO instruct $...$ are complied
  // with imperfectly. Mutation-verified: deleting the normalizeBareMath call inside renderMathText
  // fails this test and nothing else in this file.
  it("renders UNDELIMITED LaTeX as math, not as visible backslashes", () => {
    const { container } = render(
      <QuizQuestionText text={"Compute \\frac{a}{b} for the given values."} />,
    );

    expect(container.querySelector(".katex")).toBeInTheDocument();
    const visualBranch = container.querySelector(".katex-html");
    expect(visualBranch?.textContent).not.toContain("\\frac");
    // The prose either side must survive untouched -- the repair wraps the math span only.
    expect(container.textContent).toContain("Compute");
    expect(container.textContent).toContain("for the given values.");
  });

  // ⚠️ v0.141.0 — THE REAL PRODUCTION STRING, not an invented one. Verified affected: 44 quiz strings
  // carry BOTH a currency amount and a formula (6 questions, 26 explanations, 12 working solutions).
  //
  // The mechanism: isInlineDollarOpen opens a math span on any `$` not followed by whitespace, so
  // `$50,000` opens one. findInlineDollarCloseIndex then correctly REJECTS each intervening `$`
  // preceded by a space -- it walks past `$5,000` and past `$A = ...` -- and closes on the formula's
  // FINAL `$`, whose previous character is a digit. KaTeX is handed the entire sentence, fails, and
  // renderMathSegment re-emits the source. The reader sees the raw formula, backslashes and all.
  it("renders the formula, not raw LaTeX, when a question mentions money AND a formula", () => {
    const eac = "Calculate the Equivalent Annual Cost (EAC) if an asset costs $50,000, has a salvage "
      + "value of $5,000 after 5 years, and the interest rate is 10% per year. (Use capital recovery "
      + "factor formula: $A = P \\times \\frac{i(1+i)^n}{(1+i)^n -1}$)";
    const { container } = render(<QuizQuestionText text={eac} />);

    // ⚠️ ASSERT WHAT THE READER SEES. The comment at quiz-working-solution.tsx:55-60 records the last
    // time this heuristic was fixed wrongly: it captured "10-" as LaTeX, which KaTeX renders HAPPILY
    // because a trailing binary operator is legal -- so the error fallback never fired and the reader
    // silently saw a subtraction with the dollar signs eaten. A test asserting "something rendered"
    // or "did not crash" passes under both the defect and the fix.
    expect(container.querySelector(".katex")).toBeInTheDocument();
    const visual = container.querySelector(".katex-html");
    expect(visual?.textContent).not.toContain("\\frac");
    expect(visual?.textContent).not.toContain("\\times");

    // The money stays money: both amounts must survive as readable text, with their dollar signs.
    expect(container.textContent).toContain("$50,000");
    expect(container.textContent).toContain("$5,000");
    // And the prose must not have been swallowed into the math span.
    expect(container.textContent).toContain("has a salvage value of");
    expect(container.textContent).toContain("interest rate is 10% per year");
  });

  // ⚠️ THE REGRESSION THIS FIX COULD EASILY CAUSE, GUARDED WITH ANOTHER REAL PRODUCTION STRING.
  // `$10000$` is the model writing a NUMBER as math -- properly paired delimiters around digits. A
  // naive "digits after $ means currency" rule would skip the opener, leaving the CLOSING `$` to be
  // read as an opener, which would swallow the rest of the sentence including the real formula. This
  // string renders correctly today and must still render correctly after the fix.
  it("still treats a properly-delimited number as math, not currency", () => {
    const aw = "Calculate the Annual Worth of a project with NPV $10000$, interest rate 5% per year, "
      + "and a project life of 4 years. Use $CRF=\\frac{i(1+i)^n}{(1+i)^n-1}$.";
    const { container } = render(<QuizQuestionText text={aw} />);

    expect(container.querySelectorAll(".katex").length).toBeGreaterThanOrEqual(2);
    const visual = container.querySelector(".katex-html");
    expect(visual?.textContent).not.toContain("\\frac");
    expect(container.textContent).toContain("interest rate 5% per year");
    expect(container.textContent).toContain("a project life of 4 years");
  });

  // Math that legitimately BEGINS with a digit, also a real production string. `$3x^2$` must not be
  // mistaken for a currency amount.
  it("still renders math that begins with a digit", () => {
    const { container } = render(
      <QuizQuestionText text={"The function is a product of two functions, $3x^2$ and $\\sin x$."} />,
    );

    expect(container.querySelectorAll(".katex").length).toBeGreaterThanOrEqual(2);
    expect(container.querySelector(".katex-html")?.textContent).not.toContain("\\sin");
  });

  // The counterpart guard: normalizeBareMath's first design rule is "NEVER make things worse." A
  // bare backslash that is NOT a known math command must pass through untouched, or Windows paths
  // and literal "\n" in question text would be mangled into math.
  it("leaves a non-math backslash alone", () => {
    const { container } = render(<QuizQuestionText text={"Save the file to C:\\Users\\notes"} />);

    expect(container.querySelector(".katex")).not.toBeInTheDocument();
    expect(container.textContent).toBe("Save the file to C:\\Users\\notes");
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

  // ⚠️ THE PRE-DECLARED GUARD FOR v0.117.0 ITEM 1, AND ITS FIXTURE IS THE POINT: the interrogative
  // follows the LAST Statement label. `text.split(STATEMENT_RE)` pairs each label with everything up to
  // the next one, so only the FINAL segment can swallow the question — a fixture with the question
  // FIRST parses correctly under both the defect and the fix and proves nothing.
  it("breaks a trailing question off the last statement instead of swallowing it", () => {
    render(
      <QuizQuestionText
        text={"Statement 1: Water boils at 100C. Statement 2: Ice melts at 0C. Which statements are correct?"}
      />,
    );

    // The question must be its own node, not tacked onto Statement 2's body.
    expect(screen.getByText("Which statements are correct?")).toBeInTheDocument();
    expect(screen.queryByText(/Ice melts at 0C\. Which statements are correct\?/)).not.toBeInTheDocument();
  });

  // A question the model generated correctly already carries a real newline, and the renderer honours
  // it. The heuristic must not touch that path -- otherwise it becomes a general text-repair framework,
  // which is exactly what this release forbids.
  it("leaves a newline-separated question alone rather than re-splitting it", () => {
    render(
      <QuizQuestionText text={"Statement 1: Water boils at 100C.\nWhich statement is correct?"} />,
    );

    expect(screen.getByText("Which statement is correct?")).toBeInTheDocument();
  });

  // Narrowness, asserted directly: a statement body with no trailing interrogative must be left exactly
  // as it is. Without this, a broadened heuristic could start splitting ordinary sentences apart.
  it("leaves a statement that ends in a period untouched", () => {
    render(
      <QuizQuestionText text={"Statement 1: Water boils at 100C. Statement 2: Ice melts at 0C."} />,
    );

    expect(screen.getByText(/Ice melts at 0C\./)).toBeInTheDocument();
  });

  it("still splits Statement-labelled prompts onto their own lines", () => {
    render(
      <QuizQuestionText text={"Evaluate both.\nStatement 1: Water boils at 100C.\nStatement 2: Ice melts at 0C."} />,
    );

    expect(screen.getByText(/Statement 1:/)).toBeInTheDocument();
    expect(screen.getByText(/Statement 2:/)).toBeInTheDocument();
  });
});
