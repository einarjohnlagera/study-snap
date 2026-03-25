import type { MetadataRoute } from "next";
import {
  buildPublicLibraryNotePath,
  buildPublicLibrarySubjectPath,
} from "@/lib/public-note-path";
import { getPublicSubjectEntries, getServerPublicNotes } from "@/lib/server-public-notes";
import { absoluteUrl } from "@/lib/site-metadata";

export const dynamic = "force-dynamic";

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const staticRoutes: MetadataRoute.Sitemap = [
    {
      url: absoluteUrl("/"),
      changeFrequency: "weekly" as const,
      priority: 1,
    },
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

  const publicNotes = await getServerPublicNotes();
  const subjectRoutes: MetadataRoute.Sitemap = getPublicSubjectEntries(publicNotes).map((subject) => ({
    url: absoluteUrl(buildPublicLibrarySubjectPath(subject.label)),
    lastModified: subject.lastModified || undefined,
    changeFrequency: "daily" as const,
    priority: 0.8,
  }));
  const noteRoutes: MetadataRoute.Sitemap = publicNotes.map((note) => ({
    url: absoluteUrl(
      buildPublicLibraryNotePath({
        subject: note.subject,
        title: note.title,
      }),
    ),
    lastModified: note.updatedAt || undefined,
    changeFrequency: "monthly" as const,
    priority: 0.7,
  })).sort((left, right) => left.url.localeCompare(right.url));

  return [...staticRoutes, ...subjectRoutes, ...noteRoutes];
}
