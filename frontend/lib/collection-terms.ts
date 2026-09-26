/**
 * Academic Term placement helpers (v0.160.0, ADR-003).
 *
 * A term is a placement on a CHILD Subject Plan, never a level. One condition drives BOTH term
 * grouping and compact Subject cards on the Year page: `hasTermPlacement`. There is deliberately no
 * count threshold anywhere in this module; "10 subjects" is an observation about one curriculum, not
 * a rule. When every child term is NULL the caller renders today's flat grid of full-size cards.
 */

export const TERM_NOT_SPECIFIED_LABEL = "Term not specified";

export type TermPlacedChild = {
  termLabel?: string | null;
  termOrder?: number | null;
};

export type TermProgressChild = TermPlacedChild & {
  totalConcepts: number;
  masteredConcepts: number;
  notPracticedConcepts: number;
};

export type TermGroup<T> = {
  /** `null` marks the defensive trailing group for children with no term. */
  label: string | null;
  order: number | null;
  children: T[];
};

function placedLabel(child: TermPlacedChild): string | null {
  const trimmed = child.termLabel?.trim();
  return trimmed ? trimmed : null;
}

/** True when at least one child carries a term label. The single gate for grouping and density. */
export function hasTermPlacement(children: readonly TermPlacedChild[]): boolean {
  return children.some((child) => placedLabel(child) !== null);
}

/**
 * Groups children by `termLabel`, ordering groups by the minimum `termOrder` of their members and
 * keeping the incoming (sibling) order inside each group. Children with no label go to ONE trailing
 * group with `label: null`. Returns `[]` when no child is placed; callers must check
 * `hasTermPlacement` first and render the flat list in that case.
 */
export function groupChildrenByTerm<T extends TermPlacedChild>(children: readonly T[]): TermGroup<T>[] {
  if (!hasTermPlacement(children)) {
    return [];
  }
  const placed = new Map<string, TermGroup<T>>();
  const unplaced: T[] = [];
  for (const child of children) {
    const label = placedLabel(child);
    if (label === null) {
      unplaced.push(child);
      continue;
    }
    const order = child.termOrder ?? null;
    const existing = placed.get(label);
    if (existing) {
      existing.children.push(child);
      if (order !== null && (existing.order === null || order < existing.order)) {
        existing.order = order;
      }
    } else {
      placed.set(label, { label, order, children: [child] });
    }
  }
  const groups = [...placed.values()].sort((left, right) => {
    if (left.order === right.order) {
      return 0;
    }
    if (left.order === null) {
      return 1;
    }
    if (right.order === null) {
      return -1;
    }
    return left.order - right.order;
  });
  if (unplaced.length > 0) {
    groups.push({ label: null, order: null, children: unplaced });
  }
  return groups;
}

/**
 * The children in the order the Year page DISPLAYS them: term groups in term order (unplaced last)
 * when any child is placed, otherwise the incoming sibling order untouched. Anything that means
 * "the first Subject" or "the next Subject" (Continue, the dashboard hero's current step) must use
 * this, or it can point at a card that is not the first one on screen.
 */
export function orderChildrenForDisplay<T extends TermPlacedChild>(children: readonly T[]): T[] {
  if (!hasTermPlacement(children)) {
    return [...children];
  }
  return groupChildrenByTerm(children).flatMap((group) => group.children);
}

/** True once the learner has practiced anything in the Subject; `false` reads as `Not started`. */
export function isSubjectStarted(child: Pick<TermProgressChild, "totalConcepts" | "notPracticedConcepts">): boolean {
  return child.totalConcepts > 0 && child.notPracticedConcepts < child.totalConcepts;
}

/**
 * A Subject is "in progress" when it has concept evidence, some of it is no longer untouched, and it
 * is not fully mastered. Derived from fields the child response already carries.
 */
export function isSubjectInProgress(child: TermProgressChild): boolean {
  return isSubjectStarted(child) && child.masteredConcepts < child.totalConcepts;
}

export function countInProgressSubjects(children: readonly TermProgressChild[]): number {
  return children.filter(isSubjectInProgress).length;
}

export type PartialTermPlacement = {
  termed: number;
  total: number;
  /** Titles of the Subjects that break an otherwise all-or-nothing Year (never a locked one when locked Subjects agree). */
  offenders: string[];
};

/**
 * A curated Year must have a term on every Subject Plan or on none. Returns the description of a
 * partial state, or `null` when the Year is coherent (nothing termed, or everything termed). The
 * backend refuses to publish a partial Year; this lets the builder say so while the curator is
 * still assigning terms one at a time. Mirrors `NoteCollectionService.validateTermCoherence`.
 */
export function findPartialTermPlacement<T extends TermPlacedChild & { title: string; termLocked?: boolean }>(
  children: readonly T[],
): PartialTermPlacement | null {
  const termed = children.filter((child) => placedLabel(child) !== null).length;
  if (termed === 0 || termed === children.length) {
    return null;
  }
  // Locked (published) Subjects are the reference when they agree, so the message names Subjects the
  // curator can actually change; otherwise the majority decides. Mirrors validateTermCoherence.
  const lockedStates = new Set(children.filter((child) => child.termLocked === true).map((child) => placedLabel(child) !== null));
  const majorityTermed = lockedStates.size === 1 ? [...lockedStates][0] : termed * 2 >= children.length;
  return {
    termed,
    total: children.length,
    offenders: children.filter((child) => (placedLabel(child) !== null) !== majorityTermed).map((child) => child.title),
  };
}

export type TermOption = {
  label: string;
  order: number;
};

/** Terms already used among a Year's children, one entry per label with its minimum order. */
export function collectTermOptions(children: readonly TermPlacedChild[]): TermOption[] {
  const byLabel = new Map<string, number>();
  for (const child of children) {
    const label = placedLabel(child);
    if (label === null || child.termOrder === null || child.termOrder === undefined) {
      continue;
    }
    const current = byLabel.get(label);
    if (current === undefined || child.termOrder < current) {
      byLabel.set(label, child.termOrder);
    }
  }
  return [...byLabel.entries()]
    .map(([label, order]) => ({ label, order }))
    .sort((left, right) => left.order - right.order || left.label.localeCompare(right.label));
}

/**
 * `Term not specified` is the defensive heading for unplaced children, never a stored term: storing it
 * would render two groups with the same heading in a mixed Year. Case- and spacing-insensitive.
 */
export function isReservedTermLabel(entry: string): boolean {
  return normalizeTermLabel(entry) === normalizeTermLabel(TERM_NOT_SPECIFIED_LABEL);
}

function normalizeTermLabel(label: string): string {
  return label.trim().replaceAll(/\s+/g, " ").toLowerCase();
}

export const TERM_LABEL_MAX_LENGTH = 60;
export const TERM_ORDER_MAX = 32767;

export type ResolvedTerm = { termLabel: string; termOrder: number };

/**
 * Resolves a curator's entry to the `(label, order)` pair the API needs. An entry that matches an
 * existing term (case and spacing-insensitively) SNAPS to that term's stored label and order, which
 * is what prevents "First Semester" / "first  semester" drift inside one Year. A new label gets the
 * next order after the highest one in use. Returns `null` for a blank entry (meaning: clear the
 * term) and throws nothing; over-long labels are the caller's to reject via `TERM_LABEL_MAX_LENGTH`.
 */
export function resolveTermEntry(entry: string, options: readonly TermOption[]): ResolvedTerm | null {
  const trimmed = entry.trim().replaceAll(/\s+/g, " ");
  if (!trimmed) {
    return null;
  }
  const normalized = normalizeTermLabel(trimmed);
  const match = options.find((option) => normalizeTermLabel(option.label) === normalized);
  if (match) {
    return { termLabel: match.label, termOrder: match.order };
  }
  const highest = options.reduce((max, option) => Math.max(max, option.order), 0);
  return { termLabel: trimmed, termOrder: Math.min(highest + 1, TERM_ORDER_MAX) };
}
