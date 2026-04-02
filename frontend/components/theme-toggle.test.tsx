import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ThemeToggle } from "./theme-toggle";
import { updateThemePreference } from "@/lib/api";
import { getAuthUser, patchAuthUser } from "@/lib/auth";

const setThemeMock = jest.fn();
const getAuthUserMock = getAuthUser as jest.Mock;

jest.mock("next-themes", () => ({
  useTheme: jest.fn(() => ({
    resolvedTheme: "light",
    setTheme: setThemeMock,
  })),
}));

jest.mock("@/lib/api", () => ({
  updateThemePreference: jest.fn(),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: jest.fn(),
  patchAuthUser: jest.fn(),
}));

describe("ThemeToggle", () => {
  beforeEach(() => {
    setThemeMock.mockReset();
    getAuthUserMock.mockReset();
    (patchAuthUser as jest.Mock).mockReset();
    (updateThemePreference as jest.Mock).mockReset();
  });

  it("toggles theme locally for anonymous users without calling the API", () => {
    getAuthUserMock.mockReturnValue(null);

    render(<ThemeToggle />);

    fireEvent.click(screen.getByRole("button", { name: "Toggle theme" }));

    expect(setThemeMock).toHaveBeenCalledWith("dark");
    expect(updateThemePreference).not.toHaveBeenCalled();
    expect(patchAuthUser).not.toHaveBeenCalled();
  });

  it("persists theme preference for authenticated users", async () => {
    getAuthUserMock.mockReturnValue({
      id: "user-1",
      themePreference: "LIGHT",
    });
    (updateThemePreference as jest.Mock).mockResolvedValue({
      id: "user-1",
      themePreference: "DARK",
    });

    render(<ThemeToggle />);

    fireEvent.click(screen.getByRole("button", { name: "Toggle theme" }));

    expect(setThemeMock).toHaveBeenCalledWith("dark");
    expect(patchAuthUser).toHaveBeenCalledWith({ themePreference: "DARK" });
    await waitFor(() => {
      expect(updateThemePreference).toHaveBeenCalledWith({ themePreference: "DARK" });
    });
  });
});
