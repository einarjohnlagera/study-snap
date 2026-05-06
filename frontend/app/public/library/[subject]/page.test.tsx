import PublicLibrarySubjectRedirectPage from "./page";

const permanentRedirectMock = jest.fn((destination: string) => {
  throw new Error(`NEXT_REDIRECT:${destination}`);
});

jest.mock("next/navigation", () => ({
  permanentRedirect: (destination: string) => permanentRedirectMock(destination),
}));

describe("PublicLibrarySubjectRedirectPage", () => {
  beforeEach(() => {
    permanentRedirectMock.mockReset();
  });

  it("redirects legacy subject pages to the canonical query-based public library route", async () => {
    await PublicLibrarySubjectRedirectPage({
      params: Promise.resolve({ subject: "science" }),
      searchParams: Promise.resolve({ tag: "cells" }),
    });

    expect(permanentRedirectMock).toHaveBeenCalledWith("/public/library?subject=science&tag=cells");
  });
});
