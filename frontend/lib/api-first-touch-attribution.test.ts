import { loginWithGoogle, signup } from "./api";
import { captureFirstTouchAttribution, getFirstTouchAttribution } from "./first-touch-attribution";

describe("signup first-touch request shape", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
    window.sessionStorage.clear();
  });

  it.each(["email", "google"])("sends firstTouchSurface in the %s request body", async (method) => {
    window.sessionStorage.clear();
    window.history.replaceState({}, "", "/public/notes/example");
    captureFirstTouchAttribution();
    const fetchMock = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: jest.fn().mockResolvedValue({ userId: "user-1" }),
    } as unknown as Response);
    globalThis.fetch = fetchMock;

    if (method === "email") {
      await signup({ email: "note@example.com", password: "password123", firstName: "Note", ...getFirstTouchAttribution() });
    } else {
      await loginWithGoogle({ code: "google-code", ...getFirstTouchAttribution() });
    }

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain(method === "email" ? "/auth/signup" : "/auth/google");
    expect(JSON.parse(init.body as string)).toHaveProperty("firstTouchSurface", "public_note");
  });
});
