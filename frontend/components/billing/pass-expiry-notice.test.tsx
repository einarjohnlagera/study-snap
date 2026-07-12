import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ComponentProps } from "react";
import { PassExpiryNotice } from "./pass-expiry-notice";

const NOW = new Date("2026-04-01T00:00:00Z").getTime();

function renderNotice(overrides: Partial<ComponentProps<typeof PassExpiryNotice>> = {}) {
  const onRenew = jest.fn();
  return {
    onRenew,
    ...render(
      <PassExpiryNotice
        userId="user-1"
        planType="PLUS"
        premiumEndsAt={new Date(NOW + 7 * 24 * 60 * 60 * 1000).toISOString()}
        onRenew={onRenew}
        {...overrides}
      />,
    ),
  };
}

describe("PassExpiryNotice", () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(NOW);
    globalThis.localStorage.clear();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("renders the seven-day notice and renews the current Plus pass using the configured CTA", () => {
    const { onRenew } = renderNotice();

    expect(screen.getByText(/Your Plus pass ends on Apr 8, 2026/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Get another Plus pass" }));
    expect(onRenew).toHaveBeenCalledWith("PLUS");
  });

  it("uses distinct one-day framing and the current Pro-pass CTA", () => {
    renderNotice({
      planType: "PRO",
      premiumEndsAt: new Date(NOW + 24 * 60 * 60 * 1000).toISOString(),
    });

    expect(screen.getByText(/Your Pro pass ends tomorrow/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Get another Pro pass" })).toBeInTheDocument();
  });

  it("does not render outside the email-aligned expiry windows, for Free, or without an end date", () => {
    const { rerender } = renderNotice({
      premiumEndsAt: new Date(NOW + 5 * 24 * 60 * 60 * 1000).toISOString(),
    });
    expect(screen.queryByLabelText("Pass expiry notice")).not.toBeInTheDocument();

    rerender(
      <PassExpiryNotice
        userId="user-1"
        planType="FREE"
        premiumEndsAt={new Date(NOW + 7 * 24 * 60 * 60 * 1000).toISOString()}
        onRenew={jest.fn()}
      />,
    );
    expect(screen.queryByLabelText("Pass expiry notice")).not.toBeInTheDocument();

    rerender(
      <PassExpiryNotice userId="user-1" planType="PLUS" premiumEndsAt={null} onRenew={jest.fn()} />,
    );
    expect(screen.queryByLabelText("Pass expiry notice")).not.toBeInTheDocument();
  });

  it("keeps the one-day notice eligible after dismissing the seven-day notice", async () => {
    const { rerender } = renderNotice();

    fireEvent.click(screen.getByRole("button", { name: "Dismiss" }));
    expect(screen.queryByLabelText("Pass expiry notice")).not.toBeInTheDocument();

    rerender(
      <PassExpiryNotice
        userId="user-1"
        planType="PLUS"
        premiumEndsAt={new Date(NOW + 24 * 60 * 60 * 1000).toISOString()}
        onRenew={jest.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText(/Your Plus pass ends tomorrow/)).toBeInTheDocument();
    });
  });
});
