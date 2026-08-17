import type { Metadata } from "next";
import { StructuredDataScript } from "@/components/seo/structured-data-script";
import { absoluteUrl, buildPageMetadata } from "@/lib/site-metadata";
import { buildCollectionPageStructuredData } from "@/lib/structured-data";
import { ExplorePageClient } from "./explore-page-client";

const exploreDescription =
  "Explore official study plans and public notes shared by the NoteLib community.";

export const metadata: Metadata = buildPageMetadata({
  title: "Explore Study Plans and Public Notes | NoteLib",
  description: exploreDescription,
  path: "/explore",
});

export default function ExplorePage() {
  return (
    <>
      <StructuredDataScript
        id="explore-structured-data"
        data={buildCollectionPageStructuredData({
          name: "NoteLib Explore — Official Study Plans and Public Notes",
          url: absoluteUrl("/explore"),
          description: exploreDescription,
        })}
      />
      <ExplorePageClient />
    </>
  );
}
