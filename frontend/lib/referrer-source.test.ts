import { bucketReferrerSource } from "./referrer-source";

describe("bucketReferrerSource", () => {
  it.each([
    ["", "direct"],
    ["http://localhost/another-page", "direct"],
    ["https://www.google.com/search?q=free+pnle+notes", "google"],
    ["https://duckduckgo.com/?q=free+pnle+notes", "other-search"],
    ["https://www.instagram.com/notelib", "social"],
    ["https://example.org/referral", "direct"],
  ] as const)("buckets %s as %s", (referrer, expectedSource) => {
    expect(bucketReferrerSource(referrer)).toBe(expectedSource);
  });
});
