import robots from "./robots";

describe("robots metadata route", () => {
  it("allows public crawling, disallows private app routes, and exposes the sitemap URL", () => {
    expect(robots()).toEqual({
      rules: {
        userAgent: "*",
        allow: ["/", "/public/", "/public/library/"],
        disallow: [
          "/admin",
          "/auth",
          "/api",
          "/dashboard",
          "/library",
          "/login",
          "/notes",
          "/onboarding",
          "/profile",
          "/settings",
          "/signup",
          "/study",
          "/study-packs",
          "/verify-email",
        ],
      },
      sitemap: "https://www.notelib.app/sitemap.xml",
    });
  });
});
