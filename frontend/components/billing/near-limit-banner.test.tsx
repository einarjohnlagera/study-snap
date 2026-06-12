import { render, screen } from "@testing-library/react";
import { NearLimitBanner } from "./near-limit-banner";

describe("NearLimitBanner", () => {
  it("renders free-plan near-limit messaging", () => {
    render(<NearLimitBanner planType="FREE" remainingCredits={2} resetDateLabel="April 15" />);

    expect(screen.getByRole("status")).toHaveTextContent("You have 2 Study Packs left this month on the Free plan.");
  });

  it("renders free-plan reached-limit messaging with reset date", () => {
    render(<NearLimitBanner planType="FREE" remainingCredits={0} resetDateLabel="April 15" />);

    const status = screen.getByRole("status");
    expect(status).toHaveTextContent("You’ve reached your Free plan limit for this month.");
    expect(status).toHaveTextContent("Resets on April 15.");
  });

  it("renders pro reached-limit messaging with reset date", () => {
    render(<NearLimitBanner planType="PRO" remainingCredits={0} resetDateLabel="April 20" />);

    const status = screen.getByRole("status");
    expect(status).toHaveTextContent("You’ve used all your Study Packs this month.");
    expect(status).toHaveTextContent("Resets on April 20.");
  });
});
