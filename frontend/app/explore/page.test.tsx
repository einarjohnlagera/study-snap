import { render, screen } from "@testing-library/react";
import ExplorePage, { metadata } from "./page";

jest.mock("./explore-page-client", () => ({
  ExplorePageClient: () => <div>Explore client</div>,
}));

describe("ExplorePage metadata", () => {
  it("is self-canonical with Open Graph metadata", () => {
    expect(metadata).toMatchObject({
      alternates: { canonical: "https://notelib.app/explore" },
      openGraph: { url: "https://notelib.app/explore" },
    });
  });

  it("describes the distinct plans-and-notes composite collection", () => {
    render(<ExplorePage />);

    const script = document.querySelector("#explore-structured-data");
    expect(script).not.toBeNull();
    expect(JSON.parse(script?.textContent ?? "{}")).toMatchObject({
      "@type": "CollectionPage",
      name: "NoteLib Explore — Official Study Plans and Public Notes",
      url: "https://notelib.app/explore",
    });
    expect(screen.getByText("Explore client")).toBeInTheDocument();
  });
});
