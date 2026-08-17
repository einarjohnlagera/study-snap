import {
  buildPublicLibraryUrl,
  parsePublicLibraryFilters,
} from "./public-library-url";

describe("public-library-url creator filter", () => {
  it("builds a creator-filtered public library URL", () => {
    expect(buildPublicLibraryUrl({ creator: "einarjohn" })).toBe("/public/library?creator=einarjohn");
  });

  it("parses the creator query param", () => {
    expect(parsePublicLibraryFilters("?creator=einarjohn")).toMatchObject({
      creator: "einarjohn",
    });
  });

  it.each(["most_copied", "recommended"] as const)("preserves the %s backend sort key", (sort) => {
    expect(parsePublicLibraryFilters(`?sort=${sort}`)).toMatchObject({ sort });
    expect(buildPublicLibraryUrl({ sort })).toBe(`/public/library?sort=${sort}`);
  });

  it("discards a legacy audience parameter without affecting other filters", () => {
    const parsed = parsePublicLibraryFilters("?audience=BOARD_TAKER&subject=history");

    expect(parsed).toMatchObject({ subject: "history" });
    expect(Object.hasOwn(parsed, "audience")).toBe(false);
    expect(buildPublicLibraryUrl(parsed, "?audience=BOARD_TAKER&subject=history"))
      .toBe("/public/library?subject=history");
  });
});
