import PublicLibraryRedirectPage from "./page";

const redirectMock = jest.fn((destination: string) => {
  throw new Error(`NEXT_REDIRECT:${destination}`);
});

jest.mock("next/navigation", () => ({
  redirect: (destination: string) => redirectMock(destination),
}));

describe("PublicLibraryRedirectPage", () => {
  beforeEach(() => {
    redirectMock.mockReset();
  });

  it("redirects the legacy authenticated public library route to the canonical public library URL", async () => {
    await PublicLibraryRedirectPage({
      searchParams: Promise.resolve({ subject: "history", tag: ["mexican-history"], search: "cinco" }),
    });

    expect(redirectMock).toHaveBeenCalledWith("/public/library?search=cinco&subject=history&tag=mexican-history");
  });
});
