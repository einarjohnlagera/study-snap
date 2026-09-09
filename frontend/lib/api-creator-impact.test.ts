import { getCreatorImpact, getCreatorImpactSummary } from "./api";
import { getAccessToken, getRefreshToken } from "./auth";

jest.mock("./auth", () => ({
  beginManualLogoutRedirect: jest.fn(),
  clearAuthUser: jest.fn(),
  getAccessToken: jest.fn(),
  getAuthUser: jest.fn(),
  getRefreshToken: jest.fn(),
  handleUnauthorizedSession: jest.fn(),
  patchAuthUser: jest.fn(),
  setAuthUser: jest.fn(),
}));

describe("Creator impact API", () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    jest.clearAllMocks();
    (getAccessToken as jest.Mock).mockReturnValue("access-token");
    (getRefreshToken as jest.Mock).mockReturnValue("refresh-token");
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue({}),
    } as unknown as Response);
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("always sends the required impacted discriminator with bounded pagination", async () => {
    await getCreatorImpact(false, 3, 40);

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/creator-impact/me?impacted=false&page=3&size=40",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("loads the fixed-size summary from its distinct route", async () => {
    await getCreatorImpactSummary();

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/creator-impact/me/summary",
      expect.objectContaining({ method: "GET" }),
    );
  });
});
