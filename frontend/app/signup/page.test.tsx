import SignupPage from "./page";
import { redirect } from "next/navigation";

const redirectMock = redirect as unknown as jest.Mock;

jest.mock("next/navigation", () => ({
  redirect: jest.fn(),
}));

describe("SignupPage", () => {
  beforeEach(() => {
    redirectMock.mockReset();
  });

  it("forwards copy-note signup intent and the return path to auth", async () => {
    await SignupPage({
      searchParams: Promise.resolve({
        redirect: "/public/library/science/cell-structure?copy=1&intent=generate",
        intent: "copy-note",
      }),
    });

    expect(redirectMock).toHaveBeenCalledWith(
      "/auth?mode=signup&redirect=%2Fpublic%2Flibrary%2Fscience%2Fcell-structure%3Fcopy%3D1%26intent%3Dgenerate&intent=copy-note",
    );
  });

  it("keeps the default signup redirect when no intent is provided", async () => {
    await SignupPage({ searchParams: Promise.resolve({}) });

    expect(redirectMock).toHaveBeenCalledWith("/auth?mode=signup");
  });
});
