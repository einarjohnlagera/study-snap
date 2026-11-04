import { readFileSync } from "node:fs";
import { join } from "node:path";

const source = (path: string) => readFileSync(join(process.cwd(), path), "utf8");

describe("catalog cache route guards", () => {
  it("keeps walks and study plans off Next's stale-while-revalidate fetch cache (g)", () => {
    const notes = source("lib/server-public-notes.ts");
    const walker = notes.slice(notes.indexOf("async function fetchAllPublicNotePages"), notes.indexOf("export async function getServerPublicNoteById"));
    expect(walker).not.toMatch(/next\s*:\s*\{\s*revalidate/);
    expect(walker).toContain('cache: "no-store"');
    expect(source("lib/server-public-study-plans.ts")).not.toMatch(/next\s*:\s*\{\s*revalidate/);
  });

  it("keeps subject and exam pages dynamic, with no ISR revalidate export", () => {
    for (const path of ["app/public/library/[subject]/page.tsx", "app/exam/[slug]/page.tsx"]) {
      const page = source(path);
      expect(page).toContain('export const dynamic = "force-dynamic"');
      expect(page).not.toMatch(/export const revalidate\s*=/);
      expect(page).not.toContain("generateStaticParams");
    }
  });

  it("never substitutes an empty note list on sitemap failure (j)", () => {
    const sitemap = source("app/sitemap.ts");
    expect(sitemap).not.toMatch(/catch\s*\{/);
    expect(sitemap).toContain('getServerPublicNotes({ ttlClass: "sitemap", entry: "sitemap" })');
  });
});
