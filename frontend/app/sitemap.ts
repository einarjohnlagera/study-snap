import type { MetadataRoute } from "next";
import { buildPublicLibraryNotePath } from "@/lib/public-note-path";
import { getServerPublicNotes } from "@/lib/server-public-notes";
import { absoluteUrl } from "@/lib/site-metadata";

export const dynamic = "force-dynamic";

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const staticRoutes: MetadataRoute.Sitemap = [
    {
      url: absoluteUrl("/"),
    },
    {
      url: absoluteUrl("/pricing"),
    },
    {
      url: absoluteUrl("/public/library"),
    },
  ];

  const publicNotes = await getServerPublicNotes();
  const noteRoutes: MetadataRoute.Sitemap = publicNotes.map((note) => ({
    url: absoluteUrl(
      buildPublicLibraryNotePath({
        subject: note.subject,
        title: note.title,
      }),
    ),
    lastModified: note.updatedAt || undefined,
  }));

  return [...staticRoutes, ...noteRoutes];
}
