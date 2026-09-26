import {
  collectTermOptions,
  countInProgressSubjects,
  groupChildrenByTerm,
  hasTermPlacement,
  isReservedTermLabel,
  isSubjectInProgress,
  resolveTermEntry,
} from "./collection-terms";

type Child = {
  id: string;
  termLabel: string | null;
  termOrder: number | null;
  totalConcepts: number;
  masteredConcepts: number;
  notPracticedConcepts: number;
};

function child(id: string, termLabel: string | null, termOrder: number | null, overrides: Partial<Child> = {}): Child {
  return { id, termLabel, termOrder, totalConcepts: 0, masteredConcepts: 0, notPracticedConcepts: 0, ...overrides };
}

describe("hasTermPlacement", () => {
  it("is false for an empty list and for all-NULL children (the Review Set case)", () => {
    expect(hasTermPlacement([])).toBe(false);
    expect(hasTermPlacement([child("a", null, null), child("b", null, null)])).toBe(false);
  });

  it("treats a blank label as unplaced", () => {
    expect(hasTermPlacement([child("a", "   ", 1)])).toBe(false);
  });

  it("is true as soon as one child carries a label", () => {
    expect(hasTermPlacement([child("a", null, null), child("b", "First Semester", 1)])).toBe(true);
  });
});

describe("groupChildrenByTerm", () => {
  it("returns no groups when nothing is placed so callers keep the flat list", () => {
    expect(groupChildrenByTerm([child("a", null, null)])).toEqual([]);
  });

  it("orders groups by min(termOrder) and keeps sibling order inside a group", () => {
    const groups = groupChildrenByTerm([
      child("s2-first", "Second Semester", 2),
      child("s1-first", "First Semester", 1),
      child("s2-second", "Second Semester", 2),
      child("s1-second", "First Semester", 1),
    ]);
    expect(groups.map((group) => group.label)).toEqual(["First Semester", "Second Semester"]);
    expect(groups[0].children.map((entry) => entry.id)).toEqual(["s1-first", "s1-second"]);
    expect(groups[1].children.map((entry) => entry.id)).toEqual(["s2-first", "s2-second"]);
  });

  it("uses the minimum order when members of one label disagree", () => {
    const groups = groupChildrenByTerm([
      child("a", "Summer", 9),
      child("b", "Summer", 1),
      child("c", "First Semester", 5),
    ]);
    expect(groups.map((group) => group.label)).toEqual(["Summer", "First Semester"]);
  });

  it("puts unplaced children in ONE trailing group with a null label", () => {
    const groups = groupChildrenByTerm([
      child("loose-1", null, null),
      child("placed", "First Semester", 1),
      child("loose-2", null, null),
    ]);
    expect(groups.map((group) => group.label)).toEqual(["First Semester", null]);
    expect(groups[1].children.map((entry) => entry.id)).toEqual(["loose-1", "loose-2"]);
  });

  it("sorts a labelled group with a null order after ordered groups but before the unplaced group", () => {
    const groups = groupChildrenByTerm([
      child("loose", null, null),
      child("no-order", "Mystery", null),
      child("ordered", "First Semester", 1),
    ]);
    expect(groups.map((group) => group.label)).toEqual(["First Semester", "Mystery", null]);
  });
});

describe("in-progress counting", () => {
  it("is false with no evidence, untouched content, and fully mastered content", () => {
    expect(isSubjectInProgress(child("a", null, null))).toBe(false);
    expect(isSubjectInProgress(child("b", null, null, { totalConcepts: 4, notPracticedConcepts: 4 }))).toBe(false);
    expect(isSubjectInProgress(child("c", null, null, { totalConcepts: 4, masteredConcepts: 4, notPracticedConcepts: 0 }))).toBe(false);
  });

  it("is true for started but unfinished content", () => {
    expect(isSubjectInProgress(child("a", null, null, { totalConcepts: 4, masteredConcepts: 1, notPracticedConcepts: 2 }))).toBe(true);
    expect(isSubjectInProgress(child("b", null, null, { totalConcepts: 4, masteredConcepts: 0, notPracticedConcepts: 3 }))).toBe(true);
  });

  it("counts only the in-progress subjects", () => {
    expect(countInProgressSubjects([
      child("a", null, null, { totalConcepts: 4, masteredConcepts: 1, notPracticedConcepts: 2 }),
      child("b", null, null, { totalConcepts: 4, notPracticedConcepts: 4 }),
    ])).toBe(1);
  });
});

describe("collectTermOptions and resolveTermEntry", () => {
  const children = [
    child("a", "Second Semester", 2),
    child("b", "First Semester", 1),
    child("c", "First Semester", 1),
    child("d", null, null),
  ];

  it("lists each used term once, ordered by order", () => {
    expect(collectTermOptions(children)).toEqual([
      { label: "First Semester", order: 1 },
      { label: "Second Semester", order: 2 },
    ]);
  });

  it("returns null for a blank entry, meaning clear the term", () => {
    expect(resolveTermEntry("   ", collectTermOptions(children))).toBeNull();
  });

  it("snaps a case- and spacing-variant entry to the existing term's label and order", () => {
    expect(resolveTermEntry("  second   SEMESTER ", collectTermOptions(children))).toEqual({
      termLabel: "Second Semester",
      termOrder: 2,
    });
  });

  it("gives a new term the next order after the highest in use", () => {
    expect(resolveTermEntry("Summer", collectTermOptions(children))).toEqual({ termLabel: "Summer", termOrder: 3 });
  });

  it("gives the first term in an empty Year order 1", () => {
    expect(resolveTermEntry("First Semester", [])).toEqual({ termLabel: "First Semester", termOrder: 1 });
  });
});

describe("isReservedTermLabel", () => {
  it("rejects the Term not specified heading in any case or spacing, and nothing else", () => {
    expect(isReservedTermLabel("Term not specified")).toBe(true);
    expect(isReservedTermLabel("  TERM   Not Specified ")).toBe(true);
    expect(isReservedTermLabel("Term 1")).toBe(false);
  });
});
