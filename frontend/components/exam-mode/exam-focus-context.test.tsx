import { act, render, screen } from "@testing-library/react";
import { useState } from "react";
import {
  ExamFocusProvider,
  useBottomViewportClaim,
  useExamFocusContext,
  useExamFocusMode,
} from "./exam-focus-context";

function FocusInspector() {
  const { isExamFocusActive } = useExamFocusContext();
  return <span data-testid="status">{isExamFocusActive ? "active" : "idle"}</span>;
}

function BottomViewportInspector() {
  const { isBottomViewportClaimed } = useExamFocusContext();
  return <span data-testid="bottom-viewport-status">{isBottomViewportClaimed ? "claimed" : "available"}</span>;
}

function BottomViewportToggle({ initiallyClaimed = false }: { initiallyClaimed?: boolean }) {
  const [claimed, setClaimed] = useState(initiallyClaimed);
  useBottomViewportClaim(claimed);
  return (
    <>
      <BottomViewportInspector />
      <FocusInspector />
      <button type="button" onClick={() => setClaimed((current) => !current)}>
        Toggle bottom viewport
      </button>
    </>
  );
}

function FocusToggle({ initiallyActive = false }: { initiallyActive?: boolean }) {
  const [active, setActive] = useState(initiallyActive);
  useExamFocusMode(active);
  return (
    <>
      <FocusInspector />
      <button type="button" onClick={() => setActive((current) => !current)}>
        Toggle
      </button>
    </>
  );
}

describe("ExamFocusProvider", () => {
  it("defaults to inactive", () => {
    render(
      <ExamFocusProvider>
        <FocusInspector />
      </ExamFocusProvider>,
    );

    expect(screen.getByTestId("status")).toHaveTextContent("idle");
  });

  it("activates when useExamFocusMode(true) mounts and clears when unmounted", () => {
    const { unmount } = render(
      <ExamFocusProvider>
        <FocusToggle initiallyActive />
      </ExamFocusProvider>,
    );

    expect(screen.getByTestId("status")).toHaveTextContent("active");

    unmount();
  });

  it("toggles active state from inside a consumer component", () => {
    render(
      <ExamFocusProvider>
        <FocusToggle />
      </ExamFocusProvider>,
    );

    expect(screen.getByTestId("status")).toHaveTextContent("idle");

    act(() => {
      screen.getByText("Toggle").click();
    });

    expect(screen.getByTestId("status")).toHaveTextContent("active");

    act(() => {
      screen.getByText("Toggle").click();
    });

    expect(screen.getByTestId("status")).toHaveTextContent("idle");
  });

  it("tracks a bottom-viewport claim separately from exam focus", () => {
    render(
      <ExamFocusProvider>
        <BottomViewportToggle />
      </ExamFocusProvider>,
    );

    expect(screen.getByTestId("bottom-viewport-status")).toHaveTextContent("available");
    expect(screen.getByTestId("status")).toHaveTextContent("idle");

    act(() => {
      screen.getByText("Toggle bottom viewport").click();
    });

    expect(screen.getByTestId("bottom-viewport-status")).toHaveTextContent("claimed");
    expect(screen.getByTestId("status")).toHaveTextContent("idle");
  });
});
