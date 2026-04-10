import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ThemeToggle } from "./theme-toggle";
import { updateThemePreference } from "@/lib/api";
import { getAuthUser, patchAuthUser } from "@/lib/auth";

const setThemeMock = jest.fn();
const getAuthUserMock = getAuthUser as jest.Mock;
const useThemeMock = jest.fn();

jest.mock("next-themes", () => ({
  useTheme: () => useThemeMock(),
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
    useThemeMock.mockReset();
    getAuthUserMock.mockReset();
    (patchAuthUser as jest.Mock).mockReset();
    (updateThemePreference as jest.Mock).mockReset();

    useThemeMock.mockReturnValue({
      theme: "system",
      resolvedTheme: "dark",
      systemTheme: "dark",
      setTheme: setThemeMock,
    });
  });

  it("shows the current theme mode in the top-bar control", () => {
    getAuthUserMock.mockReturnValue(null);

    render(<ThemeToggle />);

    const button = screen.getByRole("button", { name: "Theme: System" });
    expect(button).toHaveAttribute("title", "Theme: System");
  });

  it("cycles from system to dark for anonymous users without calling the API", () => {
    getAuthUserMock.mockReturnValue(null);

    render(<ThemeToggle />);

    fireEvent.click(screen.getByRole("button", { name: "Theme: System" }));

    expect(setThemeMock).toHaveBeenCalledWith("dark");
    expect(updateThemePreference).not.toHaveBeenCalled();
    expect(patchAuthUser).not.toHaveBeenCalled();
  });

  it("cycles from dark to light for authenticated users and persists the preference", async () => {
    useThemeMock.mockReturnValue({
      theme: "dark",
      resolvedTheme: "dark",
      systemTheme: "dark",
      setTheme: setThemeMock,
    });
    getAuthUserMock.mockReturnValue({
      id: "user-1",
      themePreference: "DARK",
    });
    (updateThemePreference as jest.Mock).mockResolvedValue({
      id: "user-1",
      themePreference: "LIGHT",
    });

    render(<ThemeToggle />);

    fireEvent.click(screen.getByRole("button", { name: "Theme: Dark" }));

    expect(setThemeMock).toHaveBeenCalledWith("light");
    expect(patchAuthUser).toHaveBeenCalledWith({ themePreference: "LIGHT" });
    await waitFor(() => {
      expect(updateThemePreference).toHaveBeenCalledWith({ themePreference: "LIGHT" });
    });
  });

  it("cycles from light back to system", () => {
    useThemeMock.mockReturnValue({
      theme: "light",
      resolvedTheme: "light",
      systemTheme: "light",
      setTheme: setThemeMock,
    });
    getAuthUserMock.mockReturnValue(null);

    render(<ThemeToggle />);

    fireEvent.click(screen.getByRole("button", { name: "Theme: Light" }));

    expect(setThemeMock).toHaveBeenCalledWith("system");
  });
});
