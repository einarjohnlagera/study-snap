import { getPublicCreatorFocus, getPublicCreatorSummary, getPublicProfileFocus, getPublicProfileSummary } from "./api";
import { getAccessToken } from "./auth";

jest.mock("./auth", () => ({ getAccessToken: jest.fn() }));

describe("Public profile summary API", () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    (getAccessToken as jest.Mock).mockReturnValue("owner-token");
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue({ displayName: "Creator", bio: null, publicNotesCount: 4 }),
    } as unknown as Response);
  });

  afterEach(() => { globalThis.fetch = originalFetch; });

  it("loads the three-field author summary from either identity route", async () => {
    expect(await getPublicProfileSummary("owner-id")).toEqual({
      displayName: "Creator", bio: null, publicNotesCount: 4,
    });
    expect(await getPublicCreatorSummary("creator")).toEqual({
      displayName: "Creator", bio: null, publicNotesCount: 4,
    });
    expect(globalThis.fetch).toHaveBeenNthCalledWith(1,
      "http://localhost:8080/api/public/profile/owner-id/summary",
      { method: "GET", headers: { Authorization: "Bearer owner-token" } });
    expect(globalThis.fetch).toHaveBeenNthCalledWith(2,
      "http://localhost:8080/api/public/creator/creator/summary",
      { method: "GET", headers: { Authorization: "Bearer owner-token" } });
  });

  it("loads aggregate Learning Focus without fetching the full profile", async () => {
    await getPublicProfileFocus("owner-id");
    await getPublicCreatorFocus("creator");
    expect(globalThis.fetch).toHaveBeenNthCalledWith(1,
      "http://localhost:8080/api/public/profile/owner-id/learning-focus",
      { method: "GET", headers: { Authorization: "Bearer owner-token" } });
    expect(globalThis.fetch).toHaveBeenNthCalledWith(2,
      "http://localhost:8080/api/public/creator/creator/learning-focus",
      { method: "GET", headers: { Authorization: "Bearer owner-token" } });
  });
});
