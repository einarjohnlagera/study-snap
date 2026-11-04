import { render } from "@testing-library/react";
import { existsSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { PublicListingSkeleton } from "./public-page-skeleton";
import ExamLoading from "@/app/exam/[slug]/loading";

describe("public listing loading boundary", () => {
  it("renders six decorative cards with no text or live region", () => {
    const { container } = render(<PublicListingSkeleton />);
    const root = container.querySelector('[data-public-page-skeleton="listing"]');
    expect(root).toHaveAttribute("aria-busy", "true");
    expect(root?.textContent?.trim()).toBe("");
    expect(container.querySelector('[role="status"], [aria-live]')).toBeNull();
    expect(root?.querySelectorAll(".grid > div")).toHaveLength(6);
    const blocks = root?.querySelectorAll(".motion-reduce\\:animate-none");
    expect(blocks).toHaveLength(20);
    blocks?.forEach((block) => expect(block).toHaveAttribute("aria-hidden", "true"));
  });

  it.each([ExamLoading])("renders the shared skeleton through the exam hub loading component", (Loading) => {
    const { container } = render(<Loading />);
    expect(container.querySelector('[data-public-page-skeleton="listing"]')).toHaveAttribute("aria-busy", "true");
  });

  it("keeps loading.tsx on the exam hub only: the subject listing and the note route have none (a loading.tsx makes Next run generateMetadata, which walks the catalog, on a link prefetch)", () => {
    const root = process.cwd();
    for (const path of ["app/exam/[slug]/loading.tsx", "app/public/library/[subject]/page.tsx", "app/public/library/[subject]/subject-listing-content.tsx"]) {
      expect(existsSync(join(root, path))).toBe(true);
    }
    const loadingFiles = readdirSync(join(root, "app/public"), { recursive: true, encoding: "utf8" }).filter((file) => /(^|\/)loading\.tsx?$/.test(file));
    expect(loadingFiles).toEqual([]);
    expect(existsSync(join(root, "app/public/library/[subject]/(listing)"))).toBe(false);
  });
});
