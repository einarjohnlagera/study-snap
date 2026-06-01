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
});
