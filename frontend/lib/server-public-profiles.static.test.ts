import { readFileSync } from "node:fs";
import { join } from "node:path";

const source = (path: string) => readFileSync(join(process.cwd(), path), "utf8");

describe("creator and profile freshness guards", () => {
  it("keeps the profile reads off Next's data cache and on the hard-TTL profile partition", () => {
    const reader = source("lib/server-public-profiles.ts");
    expect(reader).not.toMatch(/revalidate/);
    expect(reader).not.toMatch(/next\s*:\s*\{/);
    expect(reader).not.toContain("unstable_cache");
    expect(reader.match(/cache:\s*"no-store"/g)).toHaveLength(2);
    expect(reader).toContain("`profile:id:");
    expect(reader).toContain("`profile:username:");
  });

  it("does not depend on AbortSignal.any, which the jsdom test environment lacks", () => {
    expect(source("lib/server-public-profiles.ts")).not.toMatch(/AbortSignal\.any\s*\(/);
  });

  it("keeps the creator and profile routes dynamic, with no ISR revalidate export, and a route-level error boundary", () => {
    for (const dir of ["app/public/creator/[username]", "app/public/profile/[userId]"]) {
      const page = source(`${dir}/page.tsx`);
      expect(page).toContain('export const dynamic = "force-dynamic"');
      expect(page).not.toMatch(/export const revalidate\s*=/);
      expect(page).not.toContain("generateStaticParams");
      const boundary = source(`${dir}/error.tsx`);
      expect(boundary).toContain('"use client"');
      expect(boundary).toContain("PublicProfileErrorState");
    }
  });
});
