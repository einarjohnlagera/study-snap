import { captureFirstTouchAttribution, classifyFirstTouchSurface, getFirstTouchAttribution } from "./first-touch-attribution";

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
      firstTouchSurface: "landing",
    });
  });

  it("preserves the first landing attribution when a later route has different UTM values", () => {
    captureFirstTouchAttribution();
    window.history.replaceState({}, "", "/auth?utm_source=other");
    captureFirstTouchAttribution();

    expect(getFirstTouchAttribution().utmSource).toBe("instagram");
    expect(getFirstTouchAttribution().firstTouchSurface).toBe("landing");
  });

  it.each([
    ["/", "landing"],
    ["/public", "public_note"],
    ["/public/library", "public_note"],
    ["/public/notes/example", "public_note"],
    ["/public/creator/example", "public_note"],
    ["/public/profile/example", "public_note"],
    ["/publicity", "other"],
    ["/explore", "explore"],
    ["/explore/topics", "explore"],
    ["/explorer", "other"],
    ["/exam", "exam_hub"],
    ["/exam/ale", "exam_hub"],
    ["/example", "other"],
    ["/auth", "other"],
  ])("classifies %s as %s", (pathname, expected) => {
    expect(classifyFirstTouchSurface(pathname)).toBe(expected);
  });

  it("round-trips the entry surface through real sessionStorage", () => {
    window.history.replaceState({}, "", "/exam/ale?utm_source=search");

    captureFirstTouchAttribution();

    expect(getFirstTouchAttribution().firstTouchSurface).toBe("exam_hub");
  });

  it("drops an unrecognized stored surface on read", () => {
    window.sessionStorage.setItem("notelib-first-touch-attribution", JSON.stringify({ firstTouchSurface: "<script>" }));

    expect(getFirstTouchAttribution().firstTouchSurface).toBeUndefined();
  });

  it("returns no attribution when storage has no entry", () => {
    expect(getFirstTouchAttribution()).toEqual({});
  });
});
