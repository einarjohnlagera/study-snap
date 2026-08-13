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

    it("leaves an unbalanced brace command as plain text rather than wrapping garbage", () => {
      expect(normalizeBareMath("\\frac{a")).toBe("$\\frac${a");
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
