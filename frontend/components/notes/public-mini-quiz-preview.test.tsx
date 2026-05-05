import { fireEvent, render, screen } from "@testing-library/react";
import { PublicMiniQuizPreview } from "./public-mini-quiz-preview";

let currentAuthUser: { id: string } | null = null;

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(() => currentAuthUser),
}));

const publicSeoCopyCtaMock = jest.fn(
  ({
    label,
  }: {
    label?: string;
  }) => <button type="button">{label ?? "Copy to My Library"}</button>,
);

jest.mock("./public-seo-copy-cta", () => ({
  PublicSeoCopyCta: (props: {
    label?: string;
    redirectTarget?: "library" | "generate" | "quick-review";
    guestAuthMode?: "login" | "signup";
  }) => publicSeoCopyCtaMock(props),
}));

describe("PublicMiniQuizPreview", () => {
  const quiz = [
    {
      question: "What controls the cell?",
      choices: ["Nucleus", "Cytoplasm", "Membrane", "Ribosome"],
      correctIndex: 0,
      answer: "Nucleus",
      explanation: "The nucleus controls cell activity.",
    },
  ];

  beforeEach(() => {
    currentAuthUser = null;
    publicSeoCopyCtaMock.mockClear();
  });

  it("renders one quick-check question with the notes-first prompt copy", () => {
    render(<PublicMiniQuizPreview quiz={quiz} noteId="note-1" />);

    expect(screen.getByRole("heading", { name: "🧠 Quick Check" })).toBeInTheDocument();
    expect(screen.getByText("Quick check: see what you remember from the summary.")).toBeInTheDocument();
    expect(screen.getByText("What controls the cell?")).toBeInTheDocument();
  });

  it("shows the post-answer CTA block only after a visitor answers", () => {
    render(<PublicMiniQuizPreview quiz={quiz} noteId="note-1" />);

    expect(screen.queryByText("Want more practice like this?")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Nucleus/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));

    expect(screen.getByText("Want more practice like this?")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create your own Study Pack" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Copy to My Library" })).toBeInTheDocument();
  });

  it("uses signup-aware CTA props for anonymous visitors", () => {
    render(<PublicMiniQuizPreview quiz={quiz} noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: /Nucleus/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));

    expect(publicSeoCopyCtaMock).toHaveBeenCalledWith(
      expect.objectContaining({
        label: "Create your own Study Pack",
        redirectTarget: "generate",
        guestAuthMode: "signup",
      }),
    );
    expect(publicSeoCopyCtaMock).toHaveBeenCalledWith(
      expect.objectContaining({
        label: "Copy to My Library",
        guestAuthMode: "signup",
      }),
    );
  });

  it("keeps the same CTA actions available for signed-in users", () => {
    currentAuthUser = { id: "user-1" };

    render(<PublicMiniQuizPreview quiz={quiz} noteId="note-1" />);

    fireEvent.click(screen.getByRole("button", { name: /Nucleus/i }));
    fireEvent.click(screen.getByRole("button", { name: "Check Answer" }));

    expect(screen.getByText(/keep reviewing in your workspace/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create your own Study Pack" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Copy to My Library" })).toBeInTheDocument();
  });
});
