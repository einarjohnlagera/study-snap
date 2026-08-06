import katex from "katex";
import type { ReactNode } from "react";
import type { QuizItem } from "@/lib/api";

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
 * character is not whitespace — the same rule markdown math parsers use. `\(`, `\[` and `$$` are
 * unambiguous and need only a closing delimiter.
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
    if (previous !== undefined && !/\s/.test(previous)) {
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

function renderMathSegment(latex: string, delimiter: MathDelimiter, key: string) {
  try {
    const rendered = katex.renderToString(latex, {
      displayMode: delimiter.displayMode,
      throwOnError: false,
      output: "html",
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
 * Renders text that MAY contain LaTeX, for callers that already have their own wrapper element.
 *
 * Returns the raw string untouched when the text contains no math delimiters, which is the
 * overwhelming majority of questions and options. That matters: wrapping every plain string in an
 * extra span changes which element `getByText` resolves to, and would silently move styling like
 * `break-words` off the element tests assert against. Structure is only added where math exists.
 */
export function renderMathText(text: string): ReactNode {
  if (!findMathSpanFrom(text, 0)) {
    return text;
  }
  return <>{renderWorkingSolution(text)}</>;
}

export function renderWorkingSolution(text: string): ReactNode[] {
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
