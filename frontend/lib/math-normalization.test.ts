import { normalizeBareMath } from "@/lib/math-normalization";

describe("normalizeBareMath", () => {
  describe("the reported defect", () => {
    // Both strings are copied from the owner's 2026-08-12 screenshots of the Practice Quiz.
    it("wraps a bare \\frac so it stops printing its backslash", () => {
      expect(normalizeBareMath("d = \\frac{(y_2 - y_1)}{(x_2 - x_1)}"))
        .toBe("d = $\\frac{(y_2 - y_1)}{(x_2 - x_1)}$");
    });

    it("wraps a bare \\sqrt including its nested braces", () => {
      expect(normalizeBareMath("d = \\sqrt{(x_2 - x_1)^2 + (y_2 - y_1)^2}"))
        .toBe("d = $\\sqrt{(x_2 - x_1)^2 + (y_2 - y_1)^2}$");
    });

    it("wraps bare carets", () => {
      expect(normalizeBareMath("x^2 + y^2 = 1")).toBe("$x^{2}$ + $y^{2}$ = 1");
    });

    it("wraps carets in a quadratic", () => {
      expect(normalizeBareMath("y = ax^2 + bx + c")).toBe("y = $ax^{2}$ + bx + c");
    });
  });

  describe("correctness that bare LaTeX would get wrong", () => {
    it("braces a multi-digit exponent, since x^10 is x¹0 in LaTeX but x^{10} is x¹⁰", () => {
      expect(normalizeBareMath("x^10")).toBe("$x^{10}$");
    });

    it("braces a parenthesised exponent", () => {
      expect(normalizeBareMath("e^(3x)")).toBe("$e^{(3x)}$");
    });

    it("expands backwards over a balanced group so the base is not orphaned", () => {
      expect(normalizeBareMath("(a + b)^2")).toBe("$(a + b)^{2}$");
    });

    it("converts Unicode scripts inside a wrapped span, which KaTeX cannot render raw", () => {
      expect(normalizeBareMath("\\sqrt{x₂}")).toBe("$\\sqrt{x_{2}}$");
    });
  });

  describe("never makes things worse", () => {
    it("leaves a string that already has dollar delimiters completely alone", () => {
      const input = "$x^2$ and a bare \\frac{a}{b}";
      expect(normalizeBareMath(input)).toBe(input);
    });

    it("leaves a string that already has paren delimiters completely alone", () => {
      const input = "Simplify \\(\\frac{x^3 - 4x^2}{x - 2}\\)";
      expect(normalizeBareMath(input)).toBe(input);
    });

    it("is idempotent — running twice changes nothing", () => {
      const once = normalizeBareMath("x^2 + y^2 = 1");
      expect(normalizeBareMath(once)).toBe(once);
    });

    it.each([
      ["a Windows path", "Save to C:\\Users\\notes\\file.txt"],
      ["a literal newline escape", "Use \\n to break the line"],
      ["an unknown command", "This \\foobar is not math"],
      ["a lone backslash", "A backslash \\ on its own"],
      ["prose with an underscore in an identifier", "The value user_id is a column"],
      ["plain prose", "Photosynthesis converts light energy into chemical energy"],
    ])("leaves %s untouched", (_label, input) => {
      expect(normalizeBareMath(input)).toBe(input);
    });

    it("leaves Unicode scripts alone when nothing is being wrapped — they display fine as text", () => {
      expect(normalizeBareMath("The points x₁ and y₂ are given")).toBe("The points x₁ and y₂ are given");
    });

    it("returns empty and nullish input unchanged", () => {
      expect(normalizeBareMath("")).toBe("");
    });

    it("does not wrap a trailing caret with no exponent", () => {
      expect(normalizeBareMath("The caret ^ symbol")).toBe("The caret ^ symbol");
    });

    it("does not wrap a caret with no base", () => {
      expect(normalizeBareMath("^2 alone")).toBe("^2 alone");
    });

    it("leaves an unbalanced brace command completely alone", () => {
      // This previously asserted "$\\frac${a" — output strictly worse than the input, in a file
      // whose Rule 1 is "never make things worse". Refusing unbalanced input is the correct answer.
      expect(normalizeBareMath("\\frac{a")).toBe("\\frac{a");
    });
  });

  // A command or group immediately followed by a script is the most common shape of real math
  // content — trig identities, pi-r-squared, arctan. An earlier version handled the command and the
  // script as two separate wraps and retracted already-emitted output to do it, which corrupted the
  // result: `a^b^c` produced `$a^{b}$b^{c}$`, silently duplicating `b` with no error styling, and
  // `\pi^2` produced `$\p$pi^{2}$`. These pin the shapes that broke.
  describe("a script attached to a command or group", () => {
    it.each([
      ["\\pi^2", "$\\pi^{2}$"],
      ["\\sqrt{2}^2", "$\\sqrt{2}^{2}$"],
      ["\\frac{a}{b}^2", "$\\frac{a}{b}^{2}$"],
      ["\\tan^{-1}(x)", "$\\tan^{-1}$(x)"],
      ["\\sin^2 x + \\cos^2 x = 1", "$\\sin^{2}$ x + $\\cos^{2}$ x = 1"],
      ["\\pi r^2", "$\\pi$ $r^{2}$"],
    ])("wraps %s as one span", (input, expected) => {
      expect(normalizeBareMath(input)).toBe(expected);
    });

    it("does not duplicate the base of a chained script", () => {
      // `a^b^c` is a double-superscript error in LaTeX, so leaving the second script literal is
      // correct. What must never happen is the `b` appearing twice.
      expect(normalizeBareMath("a^b^c")).toBe("$a^{b}$^c");
    });

    it.each([
      "a^b^c",
      "\\pi^2",
      "\\sin^2 x + \\cos^2 x = 1",
      "\\sqrt{2}^2",
      "\\frac{a}{b}^2",
      "\\frac{a",
      "\\tan^{-1}(x)",
      "x^2 + y^2 = 1",
      "(a + b)^2",
    ])("emits balanced $ delimiters for %s", (input) => {
      const output = normalizeBareMath(input);
      expect((output.match(/\$/g) ?? []).length % 2).toBe(0);
    });

    it("never drops or duplicates non-math characters", () => {
      // Strip every delimiter and brace the normaliser is allowed to add; what remains must be the
      // input. This catches the whole corruption class, not just the shapes listed above.
      const inputs = ["\\pi^2", "a^b^c", "\\sqrt{2}^2", "\\pi r^2", "x^2 + y^2 = 1"];
      for (const input of inputs) {
        const stripped = normalizeBareMath(input).replace(/\$/g, "").replace(/\{|\}/g, "");
        expect(stripped).toBe(input.replace(/\{|\}/g, ""));
      }
    });
  });

  describe("multiple constructs in one string", () => {
    it("wraps each construct independently", () => {
      expect(normalizeBareMath("Area is \\pi r^2 exactly"))
        .toBe("Area is $\\pi$ $r^{2}$ exactly");
    });

    it("handles a subscript and a superscript in the same string", () => {
      expect(normalizeBareMath("x_1 and x^2")).toBe("$x_{1}$ and $x^{2}$");
    });
  });
});
