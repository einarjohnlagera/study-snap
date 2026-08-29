import katex from "katex";
import type { ReactNode } from "react";
import type { QuizItem } from "@/lib/api";
import { normalizeBareMath } from "@/lib/math-normalization";

type QuizWorkingSolutionProps = {
  workingSolution: string | null | undefined;
  alwaysShow?: boolean;
  planType?: string | null;
};

type MathDelimiter = {
  displayMode: boolean;
  end: string;
  start: string;
};

const BLOCK_DOLLAR_DELIMITER: MathDelimiter = { start: "$$", end: "$$", displayMode: true };
const BLOCK_BRACKET_DELIMITER: MathDelimiter = { start: "\\[", end: "\\]", displayMode: true };
const INLINE_DOLLAR_DELIMITER: MathDelimiter = { start: "$", end: "$", displayMode: false };
const INLINE_PAREN_DELIMITER: MathDelimiter = { start: "\\(", end: "\\)", displayMode: false };
const MATH_DELIMITERS = [
  BLOCK_DOLLAR_DELIMITER,
  BLOCK_BRACKET_DELIMITER,
  INLINE_DOLLAR_DELIMITER,
  INLINE_PAREN_DELIMITER,
];
const KATEX_ERROR_CLASS = "katex-error";

export function hasComputationalWorkingSolution(
  question: Pick<QuizItem, "questionType" | "workingSolution"> | null | undefined,
) {
  return question?.questionType === "COMPUTATIONAL" && Boolean(question.workingSolution?.trim());
}

function findDelimiterAt(text: string, index: number) {
  for (const delimiter of MATH_DELIMITERS) {
    if (delimiter === INLINE_DOLLAR_DELIMITER && text.startsWith(BLOCK_DOLLAR_DELIMITER.start, index)) {
      continue;
    }
    if (text.startsWith(delimiter.start, index)) {
      return delimiter;
    }
  }
  return null;
}

/**
 * A lone `$` is currency, not math. `"What is the cost of $5?"` has a delimiter *start* but no closing
 * delimiter, and `"Item A costs $5 and item B costs $10"` has two `$` that are not a math span at all —
 * treating them as one made KaTeX render the middle of the sentence as italic math with the dollar signs
 * swallowed. Both are routine: Accountancy and Business Administration are seeded programs.
 *
 * So `$` opens a span only when the next character is not whitespace, and closes only when the previous
 * character is neither whitespace NOR a binary operator. The operator half matters as much as the
 * whitespace half: `"$10-$20"` and `"$5+$3"` both satisfy "previous character is not whitespace", so the
 * first version of this rule captured `"10-"` as LaTeX — which KaTeX renders happily, since a trailing
 * binary operator is legal, so the error fallback never fired and the reader saw a subtraction with the
 * dollar signs swallowed. A `$` sitting immediately after an operator is a new amount, not a close.
 * `\(`, `\[` and `$$` are unambiguous and need only a closing delimiter.
 */
function isInlineDollarOpen(text: string, index: number) {
  const next = text[index + 1];
  return next !== undefined && !/\s/.test(next);
}

function findInlineDollarCloseIndex(text: string, fromIndex: number) {
  for (let index = fromIndex; index < text.length; index += 1) {
    if (text[index] !== "$") {
      continue;
    }
    if (text.startsWith(BLOCK_DOLLAR_DELIMITER.start, index)) {
      index += 1;
      continue;
    }
    const previous = text[index - 1];
    if (previous !== undefined && !/[\s+\-*/=<>^~]/.test(previous)) {
      return index;
    }
  }
  return -1;
}

type MathSpan = {
  contentEnd: number;
  contentStart: number;
  delimiter: MathDelimiter;
  start: number;
};

/**
 * The single source of truth for "is there renderable math here, and where". Both the guard in
 * `renderMathText` and the tokenizer in `renderWorkingSolution` go through this, so they cannot disagree
 * about what counts as math — the disagreement between them is what produced the stray-wrapper bug.
 */
function findMathSpanFrom(text: string, fromIndex: number): MathSpan | null {
  for (let index = fromIndex; index < text.length; index += 1) {
    const delimiter = findDelimiterAt(text, index);
    if (!delimiter) {
      continue;
    }
    const contentStart = index + delimiter.start.length;
    if (delimiter === INLINE_DOLLAR_DELIMITER) {
      if (!isInlineDollarOpen(text, index)) {
        continue;
      }
      const contentEnd = findInlineDollarCloseIndex(text, contentStart);
      if (contentEnd === -1) {
        continue;
      }
      return { contentEnd, contentStart, delimiter, start: index };
    }
    const contentEnd = text.indexOf(delimiter.end, contentStart);
    if (contentEnd === -1) {
      continue;
    }
    return { contentEnd, contentStart, delimiter, start: index };
  }
  return null;
}

function renderPlainTextSegment(segment: string, key: string) {
  return (
    <span key={key} className="whitespace-pre-wrap">
      {segment}
    </span>
  );
}

/**
 * Inline (text-style) KaTeX renders these constructs at script size — a `\frac` numerator and denominator
 * come out around 0.7em, so a fraction in a question stem reads noticeably smaller than the words around
 * it even though `.katex` is already 1.21em. `\displaystyle` restores full size while keeping the math
 * inline.
 *
 * Applied ONLY to segments that contain a construct which actually shrinks. A blanket `\displaystyle`
 * would make every inline expression taller for no gain, and raising the `.katex` font size instead would
 * oversize simple variables like `$x$` against body text.
 */
// The boundary is a negative lookahead for a letter, not `\b`: LaTeX macro names are letters only, and
// `\b` fails after `\sum_` or `\int_` because `_` counts as a word character. This form also avoids
// matching a longer macro that merely starts with one of these names.
const DISPLAY_STYLE_CONSTRUCT_PATTERN = /\\(?:d?frac|[dt]?binom|sum|prod|int|oint|lim)(?![a-zA-Z])/;

export function applyInlineDisplayStyle(latex: string, displayMode: boolean): string {
  if (displayMode || !DISPLAY_STYLE_CONSTRUCT_PATTERN.test(latex)) {
    return latex;
  }
  return `\\displaystyle ${latex}`;
}

function renderMathSegment(latex: string, delimiter: MathDelimiter, key: string) {
  try {
    const rendered = katex.renderToString(applyInlineDisplayStyle(latex, delimiter.displayMode), {
      displayMode: delimiter.displayMode,
      throwOnError: false,
      // "html" alone emits aria-hidden markup and no MathML, so rendered math is INVISIBLE to screen
      // readers. That was survivable while it only affected working solutions; the v0.71.0 sweep
      // extended it to every question, option and explanation in the app, which made a narrow gap a
      // broad one. "htmlAndMathml" adds a MathML branch for assistive tech at no visual cost.
      output: "htmlAndMathml",
    });
    if (rendered.includes(KATEX_ERROR_CLASS)) {
      return renderPlainTextSegment(delimiter.start + latex + delimiter.end, key);
    }
    const Component = delimiter.displayMode ? "div" : "span";
    return <Component key={key} dangerouslySetInnerHTML={{ __html: rendered }} />;
  } catch {
    return renderPlainTextSegment(delimiter.start + latex + delimiter.end, key);
  }
}

/**
 * Render an ALREADY-EXTRACTED LaTeX string — for callers whose parser has separated math from prose,
 * so there are no delimiters left to scan for.
 *
 * <p>⚠️ This exists so the product keeps ONE KaTeX configuration. `SummaryMarkdown` needs math
 * rendering, but markdown makes the text-scanning path above unusable: `_` is emphasis, so
 * `$x_1 + x_2$` is mangled into `<em>` before any scanner could find it. `remark-math` therefore
 * tokenizes there and hands the raw TeX here, rather than the alternative — adding `rehype-katex`,
 * which would be a SECOND rendering configuration free to drift from this one on `output`,
 * `throwOnError` and the error fallback.
 */
export function renderExtractedMath(latex: string, displayMode: boolean, key: string) {
  return renderMathSegment(latex, displayMode ? BLOCK_DOLLAR_DELIMITER : INLINE_DOLLAR_DELIMITER, key);
}

/**
 * Renders text that MAY contain LaTeX, for callers that already have their own wrapper element.
 *
 * Returns the raw string untouched when the text contains no math delimiters, which is the
 * overwhelming majority of questions and options. That matters: wrapping every plain string in an
 * extra span changes which element `getByText` resolves to, and would silently move styling like
 * `break-words` off the element tests assert against. Structure is only added where math exists.
 */
export function renderMathText(text: string | null | undefined): ReactNode {
  // The C3 sweep replaced ~15 bare `{value}` expressions with `renderMathText(value)`. React rendered a
  // null or undefined value as nothing; this reads `text.length` and would throw, taking the page down.
  // One call site can genuinely pass undefined: `choices[resolveQuizCorrectIndex(item)]` where that
  // resolver returns -1.
  if (!text) {
    return text ?? null;
  }
  // Stored content predates any instruction to emit delimiters, so bare LaTeX is repaired here at
  // display time. A no-op for anything already delimited or without math. See math-normalization.ts.
  const normalized = normalizeBareMath(text);
  if (!findMathSpanFrom(normalized, 0)) {
    return text;
  }
  return <>{renderWorkingSolution(normalized)}</>;
}

export function renderWorkingSolution(rawText: string): ReactNode[] {
  const text = normalizeBareMath(rawText);
  if (!findMathSpanFrom(text, 0)) {
    return [renderPlainTextSegment(text, "plain-0")];
  }

  const nodes: ReactNode[] = [];
  let cursor = 0;
  let keyIndex = 0;
  while (cursor < text.length) {
    const span = findMathSpanFrom(text, cursor);
    if (!span) {
      nodes.push(renderPlainTextSegment(text.slice(cursor), `plain-${keyIndex}`));
      break;
    }

    const plainText = text.slice(cursor, span.start);
    if (plainText) {
      nodes.push(renderPlainTextSegment(plainText, `plain-${keyIndex}`));
      keyIndex += 1;
    }

    const latex = text.slice(span.contentStart, span.contentEnd);
    nodes.push(renderMathSegment(latex, span.delimiter, `math-${keyIndex}`));
    keyIndex += 1;
    cursor = span.contentEnd + span.delimiter.end.length;
  }
  return nodes;
}

export function QuizWorkingSolution({
  workingSolution,
  alwaysShow = false,
  planType = null,
}: Readonly<QuizWorkingSolutionProps>) {
  const trimmedSolution = workingSolution?.trim();

  if (!trimmedSolution) {
    return null;
  }
  if (!alwaysShow && planType !== "PRO") {
    return null;
  }

  return (
    <div className="space-y-2 rounded-md border border-blue-500/20 bg-blue-500/5 p-3">
      <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
        Working Solution
      </p>
      <div className="break-words rounded-md border border-border bg-background/70 p-3 font-mono text-xs leading-relaxed text-foreground/80">
        {renderWorkingSolution(trimmedSolution)}
      </div>
      <p className="text-xs text-foreground/55">AI-generated — verify calculations</p>
    </div>
  );
}
