import { act, render, screen } from "@testing-library/react";
import { useState } from "react";
import {
  ExamFocusProvider,
  useExamFocusContext,
  useExamFocusMode,
} from "./exam-focus-context";

function FocusInspector() {
  const { isExamFocusActive } = useExamFocusContext();
  return <span data-testid="status">{isExamFocusActive ? "active" : "idle"}</span>;
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
});
