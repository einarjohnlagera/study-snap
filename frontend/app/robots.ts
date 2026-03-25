import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site-metadata";

export default function robots(): MetadataRoute.Robots {
  return {
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
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
