import { getAccessToken } from "./auth";
import { recordReviewCommitmentPrompted } from "./api";

// ⚠️ THIS FILE EXISTS BECAUSE COMPONENT TESTS CANNOT COVER WHAT IT COVERS.
// Every frontend reference to `recordReviewCommitmentPrompted` — the prompt component and all four
// page suites — mocks `@/lib/api` wholesale, so none of them executes a single line of the real
// request: not the method, not the headers, not the body. That is the exact gap `CLAUDE.md` records
// from `v0.119.0`, where BOTH of a feature's JSON POSTs shipped without a `Content-Type`, Spring
// rejected every request with `HttpMediaTypeNotSupportedException` before the controller was even
// entered, and 2,182 frontend tests passed anyway. The backend half of this endpoint is covered by a
// real `MockMvc` POST; this is the client half of the same rule.
//
// Found by the `v0.139.0` cold agent, which noted the repo already had sixteen `lib/api-*.test.ts`
// files establishing the pattern and this endpoint had none.

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

describe("recordReviewCommitmentPrompted", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (getAccessToken as jest.Mock).mockReturnValue("access-token");
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ message: "recorded" }),
    });
  });

  it("POSTs JSON with an explicit Content-Type and a body", async () => {
    await recordReviewCommitmentPrompted();

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    const [url, init] = (globalThis.fetch as jest.Mock).mock.calls[0];

    expect(String(url)).toContain("/me/review-commitment/prompted");
    expect(init.method).toBe("POST");

    // ⚠️ The endpoint declares @RequestBody, so a request without a Content-Type is rejected
    // BEFORE the controller runs. Assert the header itself, not merely that a body was sent.
    const headers = new Headers(init.headers);
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(headers.get("Authorization")).toBe("Bearer access-token");

    // The body must be valid JSON — the backend binds it to an (empty) record.
    expect(() => JSON.parse(init.body as string)).not.toThrow();
  });

  it("surfaces a failed impression as a rejection rather than resolving silently", async () => {
    globalThis.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({ message: "boom" }),
    });

    // The caller deliberately swallows this (an impression that cannot be recorded must never break
    // the prompt), but the API function itself must still report the failure so that choice stays
    // the CALLER's rather than being hidden here.
    await expect(recordReviewCommitmentPrompted()).rejects.toThrow();
  });
});
