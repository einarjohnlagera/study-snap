  /**
 * Wraps bare LaTeX in `$...$` so the KaTeX renderer will actually render it.
 *
 * Why this exists: the renderer only activates INSIDE delimiters (`$...$`, `\(...\)`, `$$...$$`,
 * `\[...\]`), but no generation prompt ever told the model to emit them, so stored content mixes
 * styles — sometimes correctly delimited, sometimes a bare `\frac{a}{b}` that prints with its
 * backslash visible. Prompts are fixed going forward; this repairs what is already stored, at
 * display time, without touching the database.
 *
 * Design rules, in priority order:
 *
 * 1. NEVER make things worse. A string that renders acceptably today must come out unchanged.
 *    Anything not on the allowlist is left alone — a bare backslash is never wrapped, so Windows
 *    paths, a literal "\n", and chemistry notation pass through untouched.
 * 2. If a string ALREADY contains a math delimiter, it is left completely alone. That gives
 *    idempotency for free (output contains `$`, so re-running is a no-op) and removes any
 *    possibility of double-wrapping into `$$x$$`. Each quiz field is a separate string, so a
 *    correctly-delimited question does not stop its bare sibling choices from being repaired.
 * 3. Wrapped spans stay minimal. The less text inside `$...$`, the less chance of handing KaTeX
 *    something it cannot parse.
 */

/** Commands common in study content. A backslash followed by anything else is NOT math to us. */
const ALLOWED_COMMANDS = [
  "frac", "dfrac", "tfrac", "sqrt", "sum", "prod", "int", "iint", "oint", "lim",
  "binom", "overline", "underline", "vec", "hat", "bar", "dot", "ddot",
  "log", "ln", "exp", "sin", "cos", "tan", "csc", "sec", "cot",
  "alpha", "beta", "gamma", "delta", "epsilon", "varepsilon", "zeta", "eta", "theta",
  "vartheta", "iota", "kappa", "lambda", "mu", "nu", "xi", "rho", "sigma", "tau",
  "upsilon", "phi", "varphi", "chi", "psi", "omega",
  "Gamma", "Delta", "Theta", "Lambda", "Xi", "Pi", "Sigma", "Phi", "Psi", "Omega",
  "pi", "infty", "partial", "nabla",
  "cdot", "times", "div", "pm", "mp", "leq", "geq", "neq", "approx", "equiv",
  "propto", "in", "notin", "subset", "supset", "cup", "cap", "rightarrow", "leftarrow",
  "Rightarrow", "Leftarrow", "leftrightarrow", "ldots", "cdots", "angle", "degree",
];

const COMMAND_LOOKUP = new Set(ALLOWED_COMMANDS);

/** Any delimiter at all means the author (or a previous run) already handled this string. */
const EXISTING_DELIMITER_PATTERN = /\$|\\\(|\\\)|\\\[|\\\]/;

/**
 * KaTeX cannot render Unicode sub/superscript characters, so a span containing them would come
 * out as a red error — worse than the plain text we started with. Converted to LaTeX, but ONLY
 * inside a span we are already wrapping; untouched elsewhere, where they display fine as-is.
 */
const UNICODE_SUBSCRIPTS = "₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎";
const UNICODE_SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾";
const UNICODE_SCRIPT_VALUES = "0123456789+-=()";

function convertUnicodeScripts(text: string): string {
  let result = "";
  for (const character of text) {
    const subscriptIndex = UNICODE_SUBSCRIPTS.indexOf(character);
    if (subscriptIndex >= 0) {
      result += `_{${UNICODE_SCRIPT_VALUES[subscriptIndex]}}`;
      continue;
    }
    const superscriptIndex = UNICODE_SUPERSCRIPTS.indexOf(character);
    if (superscriptIndex >= 0) {
      result += `^{${UNICODE_SCRIPT_VALUES[superscriptIndex]}}`;
      continue;
    }
    result += character;
  }
  return result;
}

/** Reads a balanced `{...}` or `(...)` group starting at `start`. Returns -1 if unbalanced. */
function readBalancedGroup(text: string, start: number, open: string, close: string): number {
  if (text[start] !== open) {
    return -1;
  }
  let depth = 0;
  for (let index = start; index < text.length; index += 1) {
    if (text[index] === open) {
      depth += 1;
    } else if (text[index] === close) {
      depth -= 1;
      if (depth === 0) {
        return index + 1;
      }
    }
  }
  return -1;
}

/**
 * A construct found in the source, plus the LaTeX it should be wrapped as.
 * `start` is where the construct begins in the source; `end` is one past its last character.
 */
type MathSpan = { start: number; end: number; latex: string };

/**
 * Reads an allowlisted command at `index`, together with its brace groups and any trailing
 * `^`/`_` script.
 *
 * Absorbing the trailing script is what makes `\pi^2`, `\sin^2 x`, `\sqrt{2}^2` and
 * `\frac{a}{b}^2` come out as one span. Handling the command and the script as two separate
 * wraps is what produced corrupted output before — see the note on `normalizeBareMath`.
 *
 * Returns null — meaning "leave this text completely alone" — when the command is unknown OR
 * when a brace group is unbalanced. Refusing unbalanced input matters: `\frac{a` was previously
 * wrapped as `$\frac${a`, which adds delimiters to text that displayed fine.
 */
function readCommandSpan(text: string, index: number): MathSpan | null {
  if (text[index] !== "\\") {
    return null;
  }
  let cursor = index + 1;
  while (cursor < text.length && /[A-Za-z]/.test(text[cursor])) {
    cursor += 1;
  }
  if (!COMMAND_LOOKUP.has(text.slice(index + 1, cursor))) {
    return null;
  }
  if (text[cursor] === "[") {
    const optionalEnd = readBalancedGroup(text, cursor, "[", "]");
    if (optionalEnd === -1) {
      return null;
    }
    cursor = optionalEnd;
  }
  while (text[cursor] === "{") {
    const groupEnd = readBalancedGroup(text, cursor, "{", "}");
    if (groupEnd === -1) {
      return null;
    }
    cursor = groupEnd;
  }
  let latex = text.slice(index, cursor);
  while (text[cursor] === "^" || text[cursor] === "_") {
    const script = readScriptValue(text, cursor);
    if (script === null) {
      break;
    }
    latex += `${text[cursor]}${script.value}`;
    cursor = script.end;
  }
  return { start: index, end: cursor, latex };
}

/**
 * Walks back over the base of a `^` / `_`, expanding across a balanced `(...)` when present.
 *
 * Subscripts are deliberately stricter than superscripts: `_` demands a SINGLE-character base,
 * because snake_case is ordinary prose. Without that rule "the value user_id is a column" becomes
 * "$user_{id}$" — a false positive that makes readable text worse, which rule 1 forbids. Real
 * subscripts (`x_1`, `y_2`, `a_{ij}`) are single-character by convention, so nothing is lost.
 * Superscripts need no such guard: `ax^2` is math, and `ax^2` is not a naming convention.
 */
function readScriptBaseStart(text: string, operatorIndex: number, operator: string): number {
  let cursor = operatorIndex - 1;
  if (cursor < 0) {
    return -1;
  }
  if (text[cursor] === ")" || text[cursor] === "}" || text[cursor] === "]") {
    const open = text[cursor] === ")" ? "(" : text[cursor] === "}" ? "{" : "[";
    const close = text[cursor];
    let depth = 0;
    for (let index = cursor; index >= 0; index -= 1) {
      if (text[index] === close) {
        depth += 1;
      } else if (text[index] === open) {
        depth -= 1;
        if (depth === 0) {
          return index;
        }
      }
    }
    return -1;
  }
  if (!/[A-Za-z0-9]/.test(text[cursor])) {
    return -1;
  }
  if (operator === "_") {
    const isSingleCharacterBase = cursor === 0 || !/[A-Za-z0-9]/.test(text[cursor - 1]);
    return isSingleCharacterBase ? cursor : -1;
  }
  while (cursor > 0 && /[A-Za-z0-9]/.test(text[cursor - 1])) {
    cursor -= 1;
  }
  return cursor;
}

/**
 * Reads the exponent/subscript after `^` or `_`, returning its end index and a brace-wrapped form.
 * Bracing matters for correctness, not just tidiness: bare `x^10` is x¹0 in LaTeX, `x^{10}` is x¹⁰.
 */
function readScriptValue(text: string, operatorIndex: number): { end: number; value: string } | null {
  const start = operatorIndex + 1;
  if (start >= text.length) {
    return null;
  }
  const braceEnd = readBalancedGroup(text, start, "{", "}");
  if (braceEnd !== -1) {
    return { end: braceEnd, value: text.slice(start, braceEnd) };
  }
  const parenEnd = readBalancedGroup(text, start, "(", ")");
  if (parenEnd !== -1) {
    return { end: parenEnd, value: `{${text.slice(start, parenEnd)}}` };
  }
  let cursor = start;
  if (text[cursor] === "-" || text[cursor] === "+") {
    cursor += 1;
  }
  const valueStart = cursor;
  while (cursor < text.length && /[A-Za-z0-9]/.test(text[cursor])) {
    cursor += 1;
  }
  if (cursor === valueStart) {
    return null;
  }
  return { end: cursor, value: `{${text.slice(start, cursor)}}` };
}

/**
 * Reads a `base^script` / `base_script` construct whose base has not already been emitted.
 *
 * `minimumStart` is the earliest source index still uncommitted. If the base would reach back
 * before it, the base belongs to a span already written out (a wrapped command, or an earlier
 * script), so this returns null and the operator is left as literal text. That is what keeps
 * `a^b^c` — which is a double-superscript error in LaTeX anyway — from being mangled.
 */
function readScriptSpan(text: string, operatorIndex: number, minimumStart: number): MathSpan | null {
  const operator = text[operatorIndex];
  const baseStart = readScriptBaseStart(text, operatorIndex, operator);
  if (baseStart === -1 || baseStart < minimumStart) {
    return null;
  }
  const script = readScriptValue(text, operatorIndex);
  if (script === null) {
    return null;
  }
  return {
    start: baseStart,
    end: script.end,
    latex: `${text.slice(baseStart, operatorIndex)}${operator}${script.value}`,
  };
}

function wrap(segment: string): string {
  return `$${convertUnicodeScripts(segment)}$`;
}

/**
 * Wraps bare LaTeX constructs in `$...$`. Returns the input unchanged when there is nothing safe
 * to do — which is the common case, and deliberately so.
 *
 * **This function must never retract characters it has already appended.** An earlier version
 * emitted the base of a `^`/`_` and then sliced it back off the output so it could be re-emitted
 * inside `$...$`. That assumed the tail of the output was verbatim source, which stops being true
 * the moment `wrap()` has inserted a `$`. The slice then ate a delimiter and duplicated the base:
 * `a^b^c` became `$a^{b}$b^{c}$` — silently duplicated content, no error styling — and `\pi^2`
 * became `$\p$pi^{2}$`. Instead, source text is committed only once a construct's true start is
 * known, so no rewriting of prior output is ever required.
 */
export function normalizeBareMath(text: string): string {
  if (!text || (!text.includes("\\") && !text.includes("^") && !text.includes("_"))) {
    return text;
  }
  // Already delimited (or already normalized) — hands off. This also gives idempotency.
  if (EXISTING_DELIMITER_PATTERN.test(text)) {
    return text;
  }

  let result = "";
  let committed = 0;
  let cursor = 0;
  let changed = false;

  while (cursor < text.length) {
    const character = text[cursor];
    const span = character === "\\"
      ? readCommandSpan(text, cursor)
      : (character === "^" || character === "_")
        ? readScriptSpan(text, cursor, committed)
        : null;

    if (span === null) {
      cursor += 1;
      continue;
    }

    result += text.slice(committed, span.start);
    result += wrap(span.latex);
    cursor = span.end;
    committed = span.end;
    changed = true;
  }

  result += text.slice(committed);
  return changed ? result : text;
}
