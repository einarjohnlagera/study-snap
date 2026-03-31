import type { MetadataRoute } from "next";
import { learnGuides } from "@/lib/learn-guides";
import { absoluteUrl } from "@/lib/site-metadata";

export default function sitemap(): MetadataRoute.Sitemap {
  return [
    {
      url: absoluteUrl("/"),
      changeFrequency: "weekly" as const,
      priority: 1,
    },
    {
      url: absoluteUrl("/pricing"),
      changeFrequency: "weekly" as const,
      priority: 0.9,
    },
    {
      url: absoluteUrl("/learn"),
      changeFrequency: "weekly" as const,
      priority: 0.9,
    },
    ...learnGuides.map((guide) => ({
      url: absoluteUrl(`/learn/${guide.slug}`),
      changeFrequency: "monthly" as const,
      priority: 0.8,
    })),
    {
      url: absoluteUrl("/privacy"),
      changeFrequency: "monthly" as const,
      priority: 0.3,
    },
    {
      url: absoluteUrl("/terms"),
      changeFrequency: "monthly" as const,
      priority: 0.3,
    },
    {
      url: absoluteUrl("/public/library"),
      changeFrequency: "daily" as const,
      priority: 0.9,
    },
  ];
}
