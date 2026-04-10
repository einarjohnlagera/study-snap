import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ThemeSelector } from "./theme-selector";
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

describe("ThemeSelector", () => {
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

  it("shows system mode as following the current device theme", () => {
    getAuthUserMock.mockReturnValue(null);

    render(<ThemeSelector />);

    expect(screen.getByRole("button", { name: "Use System theme" })).toHaveAttribute("aria-pressed", "true");
    expect(
      screen.getByText("System follows your device setting and updates automatically. Currently using Dark."),
    ).toBeInTheDocument();
  });

  it("persists explicit theme selection for authenticated users", async () => {
    getAuthUserMock.mockReturnValue({
      id: "user-1",
      themePreference: "SYSTEM",
    });
    (updateThemePreference as jest.Mock).mockResolvedValue({
      id: "user-1",
      themePreference: "LIGHT",
    });

    render(<ThemeSelector />);

    fireEvent.click(screen.getByRole("button", { name: "Use Light theme" }));

    expect(setThemeMock).toHaveBeenCalledWith("light");
    expect(patchAuthUser).toHaveBeenCalledWith({ themePreference: "LIGHT" });
    await waitFor(() => {
      expect(updateThemePreference).toHaveBeenCalledWith({ themePreference: "LIGHT" });
    });
  });

  it("saves system preference when selected explicitly", async () => {
    useThemeMock.mockReturnValue({
      theme: "light",
      resolvedTheme: "light",
      systemTheme: "light",
      setTheme: setThemeMock,
    });
    getAuthUserMock.mockReturnValue({
      id: "user-1",
      themePreference: "LIGHT",
    });
    (updateThemePreference as jest.Mock).mockResolvedValue({
      id: "user-1",
      themePreference: "SYSTEM",
    });

    render(<ThemeSelector />);

    fireEvent.click(screen.getByRole("button", { name: "Use System theme" }));

    expect(setThemeMock).toHaveBeenCalledWith("system");
    expect(patchAuthUser).toHaveBeenCalledWith({ themePreference: "SYSTEM" });
    await waitFor(() => {
      expect(updateThemePreference).toHaveBeenCalledWith({ themePreference: "SYSTEM" });
    });
  });
});
