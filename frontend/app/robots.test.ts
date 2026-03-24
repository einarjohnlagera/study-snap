import robots from "./robots";

describe("robots metadata route", () => {
  it("allows public crawling, disallows private app routes, and exposes the sitemap URL", () => {
    expect(robots()).toEqual({
      rules: {
        userAgent: "*",
        allow: "/",
        disallow: [
          "/api",
          "/admin",
          "/auth",
          "/dashboard",
          "/library",
          "/login",
          "/notes",
          "/onboarding",
          "/profile",
          "/settings",
          "/study",
          "/study-packs",
          "/verify-email",
        ],
      },
      sitemap: "https://www.notelib.app/sitemap.xml",
    });
  });
});
