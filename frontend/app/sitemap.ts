import type { MetadataRoute } from "next";
import { buildPublicLibraryNotePath, buildPublicLibrarySubjectPath } from "@/lib/public-note-path";
import { learnGuides } from "@/lib/learn-guides";
import { getPublicSubjectEntries, getServerPublicNotes } from "@/lib/server-public-notes";
import { absoluteUrl } from "@/lib/site-metadata";

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  let publicNotes: Awaited<ReturnType<typeof getServerPublicNotes>>;
  try {
    publicNotes = await getServerPublicNotes();
  } catch {
    publicNotes = [];
  }
  const publicSubjects = getPublicSubjectEntries(publicNotes);

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
      url: absoluteUrl("/how-it-works"),
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
      url: absoluteUrl("/refund"),
      changeFrequency: "monthly" as const,
      priority: 0.3,
    },
    {
      url: absoluteUrl("/public/library"),
      changeFrequency: "daily" as const,
      priority: 0.9,
    },
    ...publicSubjects.map((subject) => ({
      url: absoluteUrl(buildPublicLibrarySubjectPath(subject.label)),
      lastModified: subject.lastModified ?? undefined,
      changeFrequency: "daily" as const,
      priority: 0.8,
    })),
    ...publicNotes.map((note) => ({
      url: absoluteUrl(buildPublicLibraryNotePath({ subject: note.subject, title: note.title })),
      lastModified: note.updatedAt ?? undefined,
      changeFrequency: "monthly" as const,
      priority: 0.7,
    })),
  ];
}
