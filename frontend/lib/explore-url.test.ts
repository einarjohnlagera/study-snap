import {
  buildExploreUrl,
  resolveExploreTab,
} from "./explore-url";

describe("buildExploreUrl", () => {
  it.each([
    [{}, "/explore"],
    [{ tab: "notes" as const }, "/explore?tab=notes"],
    [{ tab: "review-sets" as const }, "/explore"],
    [{ source: "dashboard" }, "/explore?source=dashboard"],
    [{ tab: "notes" as const, source: "collections" }, "/explore?tab=notes&source=collections"],
    [{ filters: { courseProgram: "BSN" } }, "/explore?courseProgram=BSN"],
    [
      {
        tab: "notes" as const,
        source: "dashboard",
        filters: { courseProgram: "BSN", subject: "Anatomy" },
      },
      "/explore?tab=notes&source=dashboard&subject=Anatomy&courseProgram=BSN",
    ],
  ])("builds the canonical Explore URL for %o", (options, expected) => {
    expect(buildExploreUrl(options)).toBe(expected);
  });
});

describe("resolveExploreTab", () => {
  it("resolves only the exact notes value to the Notes tab", () => {
    expect(resolveExploreTab("notes")).toBe("notes");
  });

  it.each([null, undefined, "review-sets", "other"])(
    "falls back to Review Sets for %s",
    (value) => {
      expect(resolveExploreTab(value)).toBe("review-sets");
    },
  );
});
