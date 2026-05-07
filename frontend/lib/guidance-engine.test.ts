import { pickActiveGuidance, type GuidanceRule } from "./guidance-engine";
import * as guidance from "@/lib/guidance";

jest.mock("@/lib/guidance", () => ({
  hasSeenTip: jest.fn(),
}));

const mockHasSeenTip = guidance.hasSeenTip as jest.Mock;

describe("pickActiveGuidance", () => {
  beforeEach(() => {
    mockHasSeenTip.mockReset();
    mockHasSeenTip.mockReturnValue(false);
  });

  it("returns null when rules array is empty", () => {
    expect(pickActiveGuidance([])).toBeNull();
  });

  it("returns null when all conditions are false", () => {
    const rules: GuidanceRule[] = [
      { id: "tip-a", priority: 1, condition: () => false, message: "Tip A" },
    ];
    expect(pickActiveGuidance(rules)).toBeNull();
  });

  it("returns null when all matching tips have been seen", () => {
    mockHasSeenTip.mockReturnValue(true);
    const rules: GuidanceRule[] = [
      { id: "tip-a", priority: 1, condition: () => true, message: "Tip A" },
    ];
    expect(pickActiveGuidance(rules)).toBeNull();
  });

  it("returns the matching unseen rule", () => {
    const rule: GuidanceRule = { id: "tip-a", priority: 1, condition: () => true, message: "Tip A" };
    expect(pickActiveGuidance([rule])).toBe(rule);
  });

  it("picks the lowest priority number first", () => {
    const low: GuidanceRule = { id: "low", priority: 1, condition: () => true, message: "Low" };
    const high: GuidanceRule = { id: "high", priority: 10, condition: () => true, message: "High" };
    expect(pickActiveGuidance([high, low])).toBe(low);
  });

  it("skips seen tips and falls through to next in priority order", () => {
    mockHasSeenTip.mockImplementation((id: string) => id === "tip-a");
    const tipA: GuidanceRule = { id: "tip-a", priority: 1, condition: () => true, message: "Tip A" };
    const tipB: GuidanceRule = { id: "tip-b", priority: 2, condition: () => true, message: "Tip B" };
    expect(pickActiveGuidance([tipA, tipB])).toBe(tipB);
  });

  it("skips rules whose condition returns false even if unseen", () => {
    const inactive: GuidanceRule = { id: "inactive", priority: 1, condition: () => false, message: "Inactive" };
    const active: GuidanceRule = { id: "active", priority: 2, condition: () => true, message: "Active" };
    expect(pickActiveGuidance([inactive, active])).toBe(active);
  });

  it("does not mutate the original rules array order", () => {
    const first: GuidanceRule = { id: "first", priority: 5, condition: () => true, message: "First" };
    const second: GuidanceRule = { id: "second", priority: 1, condition: () => true, message: "Second" };
    const rules = [first, second];
    pickActiveGuidance(rules);
    expect(rules[0]).toBe(first);
    expect(rules[1]).toBe(second);
  });
});
