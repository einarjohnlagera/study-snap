import { getAccessToken } from "./auth";
import { activateExamGoal, addExamGoal, removeExamGoal } from "./api";

jest.mock("./auth", () => ({
  beginManualLogoutRedirect: jest.fn(), clearAuthUser: jest.fn(), getAccessToken: jest.fn(),
  getAuthUser: jest.fn(), getRefreshToken: jest.fn(), handleUnauthorizedSession: jest.fn(),
  patchAuthUser: jest.fn(), setAuthUser: jest.fn(),
}));

describe("exam goal management requests", () => {
  beforeEach(() => {
    (getAccessToken as jest.Mock).mockReturnValue("access-token");
    globalThis.fetch = jest.fn().mockResolvedValue({ ok: true, status: 200, json: async () => [] });
  });

  it("sends add as authenticated JSON POST", async () => {
    await addExamGoal("pnle");
    const [url, init] = (globalThis.fetch as jest.Mock).mock.calls[0];
    expect(String(url)).toContain("/users/profile/exam-goals");
    expect(init.method).toBe("POST");
    expect(new Headers(init.headers).get("Content-Type")).toBe("application/json");
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer access-token");
    expect(JSON.parse(init.body)).toEqual({ slug: "pnle" });
  });

  it("sends activate and remove to the selected slug", async () => {
    await activateExamGoal("ale");
    await removeExamGoal("ale");
    const calls = (globalThis.fetch as jest.Mock).mock.calls;
    expect(String(calls[0][0])).toContain("/users/profile/exam-goals/ale/active");
    expect(calls[0][1].method).toBe("PUT");
    expect(String(calls[1][0])).toContain("/users/profile/exam-goals/ale");
    expect(calls[1][1].method).toBe("DELETE");
  });
});
