import { fireEvent, render, screen } from "@testing-library/react";
import { PublicFlashcardsPreview } from "./public-flashcards-preview";

let currentAuthUser: { id: string } | null = null;

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(() => currentAuthUser),
}));

const publicSeoCopyCtaMock = jest.fn(
  ({ label }: { label?: string }) => <button type="button">{label ?? "Copy to My Library"}</button>,
);

jest.mock("./public-seo-copy-cta", () => ({
  PublicSeoCopyCta: (props: { label?: string }) => publicSeoCopyCtaMock(props),
}));

const makeQuizItem = (concept: string, explanation: string) => ({
  question: `What is ${concept}?`,
  choices: ["Choice A", "Choice B", "Choice C", "Choice D"],
  correctIndex: 0,
  answer: "Choice A",
  concept,
  explanation,
});

describe("PublicFlashcardsPreview", () => {
  const keyConcepts = ["Stair Tread Depth", "Occupancy Classification", "Passive Cooling"];
  const quiz = [
    makeQuizItem("Stair Tread Depth", "Minimum 11 inches."),
    makeQuizItem("Occupancy Classification", "Groups buildings by use for code purposes."),
    makeQuizItem("Passive Cooling", "Uses natural airflow instead of mechanical systems."),
  ];

  beforeEach(() => {
    currentAuthUser = null;
    publicSeoCopyCtaMock.mockClear();
  });

  it("renders nothing when no concept has a matching explanation", () => {
    const { container } = render(
      <PublicFlashcardsPreview keyConcepts={["Unmatched Concept"]} quiz={[]} noteId="note-1" />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("renders the preview heading and first card front", () => {
    render(<PublicFlashcardsPreview keyConcepts={keyConcepts} quiz={quiz} noteId="note-1" />);

    expect(screen.getByRole("heading", { name: "🧠 Flashcards Preview" })).toBeInTheDocument();
    expect(screen.getByText("Stair Tread Depth")).toBeInTheDocument();
    expect(screen.queryByText("Minimum 11 inches.")).not.toBeInTheDocument();
  });

  it("shows a progress badge capped at 3 cards", () => {
    render(<PublicFlashcardsPreview keyConcepts={keyConcepts} quiz={quiz} noteId="note-1" />);
    expect(screen.getByText("1 / 3")).toBeInTheDocument();
  });

  it("flips to reveal the definition on click", () => {
    render(<PublicFlashcardsPreview keyConcepts={keyConcepts} quiz={quiz} noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: "Flip to definition" }));

    expect(screen.getByText("Minimum 11 inches.")).toBeInTheDocument();
  });

  it("advances to the next card and resets the flip state", () => {
    render(<PublicFlashcardsPreview keyConcepts={keyConcepts} quiz={quiz} noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: "Flip to definition" }));
    fireEvent.click(screen.getByRole("button", { name: "Next Card →" }));

    expect(screen.getByText("Occupancy Classification")).toBeInTheDocument();
    expect(screen.queryByText("Groups buildings by use for code purposes.")).not.toBeInTheDocument();
    expect(screen.getByText("2 / 3")).toBeInTheDocument();
  });

  it("shows See Results on the last card", () => {
    render(<PublicFlashcardsPreview keyConcepts={["Stair Tread Depth"]} quiz={quiz} noteId="note-1" />);

    expect(screen.getByRole("button", { name: "See Results" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Next Card →" })).not.toBeInTheDocument();
  });

  it("shows completion state after finishing all preview cards", () => {
    render(<PublicFlashcardsPreview keyConcepts={["Stair Tread Depth"]} quiz={quiz} noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: "See Results" }));

    expect(screen.getByText("🎉 Flashcards Preview Complete")).toBeInTheDocument();
    expect(screen.queryByText("Concept")).not.toBeInTheDocument();
  });

  it("passes the Continue Learning CTA with the flashcards analytics event", () => {
    render(<PublicFlashcardsPreview keyConcepts={["Stair Tread Depth"]} quiz={quiz} noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: "See Results" }));

    expect(publicSeoCopyCtaMock).toHaveBeenCalledWith(
      expect.objectContaining({
        label: "Continue Learning",
        analyticsEvent: "PUBLIC_NOTE_FLASHCARDS_CLICKED",
      }),
    );
  });

  it("shows copy-framed messaging for already signed-in users", () => {
    currentAuthUser = { id: "user-1" };

    render(<PublicFlashcardsPreview keyConcepts={["Stair Tread Depth"]} quiz={quiz} noteId="note-1" />);
    fireEvent.click(screen.getByRole("button", { name: "See Results" }));

    expect(
      screen.getByText("Copy this note to your library and continue learning with the full deck."),
    ).toBeInTheDocument();
  });

  it("works end-to-end through a 3-card preview", () => {
    render(<PublicFlashcardsPreview keyConcepts={keyConcepts} quiz={quiz} noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: "Next Card →" }));
    expect(screen.getByText("Occupancy Classification")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Next Card →" }));
    expect(screen.getByText("Passive Cooling")).toBeInTheDocument();
    expect(screen.getByText("3 / 3")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "See Results" }));
    expect(screen.getByText("🎉 Flashcards Preview Complete")).toBeInTheDocument();
    expect(screen.getByText(/You've previewed 3 flashcards/i)).toBeInTheDocument();
  });
});
