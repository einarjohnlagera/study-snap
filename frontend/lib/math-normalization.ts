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

export function containsUnicodeScriptCharacters(text: string): boolean {
  return [...text].some((character) => (
    UNICODE_SUBSCRIPTS.includes(character) || UNICODE_SUPERSCRIPTS.includes(character)
  ));
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
 * If an allowlisted command starts at `index`, returns the end of the command plus its trailing
 * brace groups (`\frac{a}{b}` consumes both). Returns -1 when this is not a command we handle.
 */
function readCommandSpan(text: string, index: number): number {
  if (text[index] !== "\\") {
    return -1;
  }
  let cursor = index + 1;
  while (cursor < text.length && /[A-Za-z]/.test(text[cursor])) {
    cursor += 1;
  }
  const name = text.slice(index + 1, cursor);
  if (!COMMAND_LOOKUP.has(name)) {
    return -1;
  }
  // Consume any brace groups belonging to the command, plus \sqrt's optional [n] index.
  for (;;) {
    if (text[cursor] === "[") {
      const optionalEnd = readBalancedGroup(text, cursor, "[", "]");
      if (optionalEnd === -1) {
        break;
      }
      cursor = optionalEnd;
      continue;
    }
    const groupEnd = readBalancedGroup(text, cursor, "{", "}");
    if (groupEnd === -1) {
      break;
    }
    cursor = groupEnd;
  }
  return cursor;
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
  const digitsStart = cursor;
  while (cursor < text.length && /[A-Za-z0-9]/.test(text[cursor])) {
    cursor += 1;
  }
  if (cursor === digitsStart) {
    return null;
  }
  return { end: cursor, value: `{${text.slice(start, cursor)}}` };
}

function wrap(segment: string): string {
  return `$${convertUnicodeScripts(segment)}$`;
}

/**
 * Wraps bare LaTeX constructs in `$...$`. Returns the input unchanged when there is nothing safe
 * to do — which is the common case, and deliberately so.
 */
export function normalizeBareMath(text: string): string {
  if (!text || (!text.includes("\\") && !text.includes("^") && !text.includes("_"))) {
    return text;
  }
  // Rule 2: already delimited (or already normalized) — hands off.
  if (EXISTING_DELIMITER_PATTERN.test(text)) {
    return text;
  }

  let result = "";
  let cursor = 0;
  let changed = false;

  while (cursor < text.length) {
    const character = text[cursor];

    if (character === "\\") {
      const commandEnd = readCommandSpan(text, cursor);
      if (commandEnd === -1) {
        // Not an allowlisted command: a lone backslash, a Windows path, a literal \n. Leave it.
        result += character;
        cursor += 1;
        continue;
      }
      result += wrap(text.slice(cursor, commandEnd));
      cursor = commandEnd;
      changed = true;
      continue;
    }

    if (character === "^" || character === "_") {
      const baseStart = readScriptBaseStart(text, cursor, character);
      const script = readScriptValue(text, cursor);
      if (baseStart === -1 || script === null) {
        result += character;
        cursor += 1;
        continue;
      }
      // The base was already appended to `result`; take it back so it lands inside the wrapper.
      const baseLength = cursor - baseStart;
      result = result.slice(0, result.length - baseLength);
      result += wrap(`${text.slice(baseStart, cursor)}${character}${script.value}`);
      cursor = script.end;
      changed = true;
      continue;
    }

    result += character;
    cursor += 1;
  }

  return changed ? result : text;
}
