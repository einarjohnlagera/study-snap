import { render } from "@testing-library/react";
import LearnArticlePage from "./page";
import { learnGuides } from "@/lib/learn-guides";

describe("LearnArticlePage", () => {
  it("emits Article structured data for a resolved guide", async () => {
    const guide = learnGuides[0];

    const { container } = render(
      await LearnArticlePage({ params: Promise.resolve({ slug: guide.slug }) }),
    );

    const structuredData = container.querySelector("#learn-article-structured-data");
    expect(structuredData).not.toBeNull();
    expect(structuredData?.textContent).toContain('"@type":"Article"');
    expect(structuredData?.textContent).toContain(`"headline":"${guide.title}"`);
    expect(structuredData?.textContent).toContain(`/learn/${guide.slug}`);
  });
});
