import { render } from "@testing-library/react";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { PublicListingSkeleton } from "./public-page-skeleton";
import SubjectLoading from "@/app/public/library/[subject]/(listing)/loading";
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

  it.each([SubjectLoading, ExamLoading])("renders the shared skeleton through a route loading component", (Loading) => {
    const { container } = render(<Loading />);
    expect(container.querySelector('[data-public-page-skeleton="listing"]')).toHaveAttribute("aria-busy", "true");
  });

  it("limits the listing boundary to the route group and keeps the note route free of loading files", () => {
    const root = process.cwd();
    for (const path of ["app/public/library/[subject]/(listing)/loading.tsx", "app/exam/[slug]/loading.tsx", "app/public/library/[subject]/(listing)/page.tsx"]) {
      expect(existsSync(join(root, path))).toBe(true);
    }
    for (const path of ["app/public/library/[subject]/loading.tsx", "app/public/library/[subject]/[slug]/loading.tsx", "app/public/library/[subject]/page.tsx"]) {
      expect(existsSync(join(root, path))).toBe(false);
    }
  });
});
