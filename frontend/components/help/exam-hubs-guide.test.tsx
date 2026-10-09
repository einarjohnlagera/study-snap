import { render, screen } from "@testing-library/react";
import { ExamHubsGuide } from "./exam-hubs-guide";

describe("ExamHubsGuide", () => {
  it("lists all five configured hubs, including CPALE and CELE", () => {
    render(<ExamHubsGuide />);

    const links = ["ale", "pnle", "let", "cpale", "ce"].map((slug) =>
      screen.getByRole("link", { name: new RegExp(`^${slug === "ce" ? "CELE" : slug.toUpperCase()}\\b`) }),
    );

    expect(links).toHaveLength(5);
    links.forEach((link, index) => {
      expect(link).toHaveAttribute("href", `/exam/${["ale", "pnle", "let", "cpale", "ce"][index]}`);
    });
    expect(screen.getByText("Civil Engineering Licensure Examination (CELE)")).toBeInTheDocument();
  });
});
