import {
  buildDiscoveryIntentFallbackPath,
  clearDiscoveryIntentCookie,
  getDiscoveryIntentCookie,
  setDiscoveryIntentCookie,
} from "./discovery-intent";

describe("discovery intent cookie", () => {
  beforeEach(() => {
    clearDiscoveryIntentCookie();
  });

  it("round-trips the plan and Explore context", () => {
    const intent = {
      planId: "plan-1",
      planType: "study-plan" as const,
      returnPath: "/explore?source=dashboard",
    };

    setDiscoveryIntentCookie(intent);

    expect(getDiscoveryIntentCookie()).toEqual(intent);
  });

  it("rejects a returnPath that is not an Explore path", () => {
    // returnPath round-trips through a cookie, so it is attacker-influenceable: anything that can
    // write the cookie chooses where the post-signup handoff sends the visitor. isExplorePath is the
    // only thing preventing that from becoming an open redirect, and nothing else pinned it —
    // weakening it to `typeof === "string"` left all 32 tests green.
    for (const hostile of [
      "https://evil.example.com",
      "//evil.example.com",
      "/dashboard",
      "/exploration",
      "/explore-evil",
      "javascript:alert(1)",
      "",
    ]) {
      globalThis.document.cookie = `notelib-discovery-intent=${encodeURIComponent(JSON.stringify({
        planId: "plan-1",
        planType: "study-plan",
        returnPath: hostile,
      }))}`;

      expect(getDiscoveryIntentCookie()).toBeNull();
    }
  });

  it("never builds a fallback path outside Explore", () => {
    expect(buildDiscoveryIntentFallbackPath("https://evil.example.com")).toBe(
      "/explore?discoveryNotice=adopt-unavailable",
    );
    expect(buildDiscoveryIntentFallbackPath("//evil.example.com")).toBe(
      "/explore?discoveryNotice=adopt-unavailable",
    );
    expect(buildDiscoveryIntentFallbackPath("/dashboard")).toBe(
      "/explore?discoveryNotice=adopt-unavailable",
    );
  });

  it("clears malformed or partial values without throwing", () => {
    document.cookie = "notelib-discovery-intent=%7B%22planId%22%3A%22plan-1%22%7D; path=/";

    expect(getDiscoveryIntentCookie()).toBeNull();
    expect(document.cookie).not.toContain("notelib-discovery-intent=");
  });

  it("preserves safe Explore context when building an unavailable notice", () => {
    expect(buildDiscoveryIntentFallbackPath("/explore?source=dashboard")).toBe(
      "/explore?source=dashboard&discoveryNotice=adopt-unavailable",
    );
    expect(buildDiscoveryIntentFallbackPath("https://example.com")).toBe(
      "/explore?discoveryNotice=adopt-unavailable",
    );
  });

  it("tolerates blocked cookie writes", () => {
    const cookieDescriptor = Object.getOwnPropertyDescriptor(Document.prototype, "cookie");
    const setter = jest.spyOn(Document.prototype, "cookie", "set").mockImplementation(() => {
      throw new Error("Cookies blocked");
    });

    expect(() => setDiscoveryIntentCookie({
      planId: "plan-1",
      planType: "goal",
      returnPath: "/explore",
    })).not.toThrow();

    setter.mockRestore();
    expect(cookieDescriptor).toBeDefined();
  });
});
