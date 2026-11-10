import { fireEvent, render, screen } from "@testing-library/react";
import PublicCreatorError from "../../app/public/creator/[username]/error";
import PublicProfileError from "../../app/public/profile/[userId]/error";
import { PublicProfileErrorState } from "./public-profile-error-state";

const refreshMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: refreshMock, push: jest.fn() }),
}));

const HEADING = "We couldn't load this profile";
const BODY = "Something went wrong while loading this profile. Please try again in a little while.";

describe("PublicProfileErrorState", () => {
  beforeEach(() => {
    refreshMock.mockReset();
  });

  it("shows the approved copy and a single Try again action", () => {
    render(<PublicProfileErrorState reset={jest.fn()} />);

    expect(screen.getByRole("heading", { level: 1, name: HEADING })).toBeInTheDocument();
    expect(screen.getByText(BODY)).toBeInTheDocument();
    expect(screen.getAllByRole("button")).toHaveLength(1);
    expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
  });

  it("retries by refreshing the route AND resetting the boundary (reset alone would not re-fetch a failed server render)", () => {
    const reset = jest.fn();
    render(<PublicProfileErrorState reset={reset} />);

    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(refreshMock).toHaveBeenCalledTimes(1);
    expect(reset).toHaveBeenCalledTimes(1);
  });

  it("does not imply the profile is private, deleted or missing, and promises no timing", () => {
    const { container } = render(<PublicProfileErrorState reset={jest.fn()} />);
    const text = container.textContent ?? "";

    expect(text).not.toMatch(/private|deleted|removed|not found|doesn't exist|does not exist|right away|immediately|instantly/i);
  });
});

describe.each([
  ["creator route", PublicCreatorError],
  ["profile route", PublicProfileError],
])("%s error.tsx", (_name, RouteError) => {
  beforeEach(() => {
    refreshMock.mockReset();
  });

  it("renders the shared state and never shows the error message or digest", () => {
    const error = Object.assign(new Error("connect ECONNREFUSED 10.0.0.7:5432 password=hunter2"), { digest: "digest-4242" });
    const { container } = render(<RouteError error={error} reset={jest.fn()} />);
    const text = container.textContent ?? "";

    expect(screen.getByRole("heading", { level: 1, name: HEADING })).toBeInTheDocument();
    expect(text).not.toContain("ECONNREFUSED");
    expect(text).not.toContain("hunter2");
    expect(text).not.toContain("digest-4242");
  });

  it("passes the boundary's reset through to Try again", () => {
    const reset = jest.fn();
    render(<RouteError error={new Error("boom")} reset={reset} />);

    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(reset).toHaveBeenCalledTimes(1);
    expect(refreshMock).toHaveBeenCalledTimes(1);
  });
});
