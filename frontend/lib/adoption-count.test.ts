import {
  ADOPTION_COUNT_DISPLAY_THRESHOLD,
  formatCompactAdoptionCount,
  getCompactAdoptionLabel,
  getDetailedAdoptionLabel,
  shouldShowAdoptionCount,
} from "./adoption-count";

describe("adoption count display rules", () => {
  it("pins the owner's threshold at 5", () => {
    // ⚠️ This is an owner decision (2026-09-07), not a computed value. If a later distribution read
    // suggests a different number, that does NOT change it -- the decision has to be remade.
    expect(ADOPTION_COUNT_DISPLAY_THRESHOLD).toBe(5);
  });

  it.each([
    [0, false],
    [1, false],
    [4, false],
    [5, true],
    [8, true],
    [40, true],
  ])("shows the count at %i adopters: %s", (count, expected) => {
    expect(shouldShowAdoptionCount(count)).toBe(expected);
  });

  it("shows nothing at all below the threshold, rather than a floor or a range", () => {
    // ⚠️ The load-bearing assertion: "fewer than 5" or "1-4" would still disclose the smallness the
    // threshold exists to hide. Omission is the decision, so null is the only acceptable result.
    for (const count of [0, 1, 2, 3, 4]) {
      expect(getCompactAdoptionLabel(count)).toBeNull();
      expect(getDetailedAdoptionLabel(count)).toBeNull();
    }
  });

  it("treats a missing count as not displayable rather than as zero adopters", () => {
    // The field is optional on the API types, so an older payload must degrade to silence.
    for (const absent of [null, undefined]) {
      expect(shouldShowAdoptionCount(absent)).toBe(false);
      expect(getCompactAdoptionLabel(absent)).toBeNull();
      expect(getDetailedAdoptionLabel(absent)).toBeNull();
    }
  });

  it.each([
    [5, "5"],
    [40, "40"],
    [999, "999"],
    [1000, "1K"],
    [1200, "1.2K"],
    [12000, "12K"],
  ])("formats %i compactly as %s", (count, expected) => {
    expect(formatCompactAdoptionCount(count)).toBe(expected);
  });

  it("truncates rather than rounds, so the label never overstates adopters", () => {
    // ⚠️ 1999 must not read "2K" -- that claims more adopters than exist. This is the assertion that
    // fails if someone swaps the floor for Math.round.
    expect(formatCompactAdoptionCount(1999)).toBe("1.9K");
    expect(formatCompactAdoptionCount(1099)).toBe("1K");
  });

  it("says 'adopted', never 'learners'", () => {
    // ⚠️ The repo cannot prove every counted owner is semantically a learner, and ProfileType must
    // not be used to filter adoptions to justify the word.
    const compact = getCompactAdoptionLabel(40);
    const detailed = getDetailedAdoptionLabel(40);
    expect(compact).toBe("40 adopted");
    expect(compact).not.toMatch(/learner/i);
    expect(detailed).not.toMatch(/learner/i);
  });
});
