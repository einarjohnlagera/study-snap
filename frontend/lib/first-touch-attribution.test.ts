import { captureFirstTouchAttribution, getFirstTouchAttribution } from "./first-touch-attribution";

describe("first-touch attribution", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    window.history.replaceState({}, "", "/?utm_source=instagram&utm_medium=paid&utm_campaign=board-review&utm_content=story&utm_term=pnle");
    Object.defineProperty(document, "referrer", { configurable: true, value: "https://example.com/campaign" });
  });

  it("captures the first landing query and referrer across navigation", () => {
    captureFirstTouchAttribution();
    window.history.replaceState({}, "", "/auth?mode=signup");

    expect(getFirstTouchAttribution()).toEqual({
      utmSource: "instagram",
      utmMedium: "paid",
      utmCampaign: "board-review",
      utmContent: "story",
      utmTerm: "pnle",
      referrer: "https://example.com/campaign",
    });
  });

  it("preserves the first landing attribution when a later route has different UTM values", () => {
    captureFirstTouchAttribution();
    window.history.replaceState({}, "", "/auth?utm_source=other");
    captureFirstTouchAttribution();

    expect(getFirstTouchAttribution().utmSource).toBe("instagram");
  });
});
