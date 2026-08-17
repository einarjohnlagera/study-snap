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

  it("parses and builds the authored depth query param", () => {
    expect(parsePublicLibraryFilters("?level=JUNIOR_HIGH")).toMatchObject({
      level: "JUNIOR_HIGH",
    });
    expect(buildPublicLibraryUrl({ level: "JUNIOR_HIGH" }))
      .toBe("/public/library?level=JUNIOR_HIGH");
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
