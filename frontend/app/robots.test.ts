import robots from "./robots";

describe("robots metadata route", () => {
  it("allows public crawling, disallows private app routes, and exposes the sitemap URL", () => {
    expect(robots()).toEqual({
      rules: {
        userAgent: "*",
        allow: "/",
        disallow: [
          "/dashboard",
          "/study",
          "/settings",
          "/profile",
          "/api",
          "/app",
        ],
      },
      sitemap: "https://notelib.app/sitemap.xml",
    });
  });
});
