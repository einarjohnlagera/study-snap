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

  it("shows a compact always-visible desktop theme group", () => {
    getAuthUserMock.mockReturnValue(null);

    render(<ThemeToggle />);

    expect(screen.getByRole("group", { name: "Theme" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Use Light theme" })).toHaveAttribute("title", "Light");
    expect(screen.getByRole("button", { name: "Use Dark theme" })).toHaveAttribute("title", "Dark");
    expect(screen.getByRole("button", { name: "Use System theme" })).toHaveAttribute("title", "System");
    expect(screen.getByRole("button", { name: "Use System theme" })).toHaveAttribute("aria-pressed", "true");
  });

  it("persists desktop theme selection for authenticated users", async () => {
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

    fireEvent.click(screen.getByRole("button", { name: "Use Light theme" }));

    expect(setThemeMock).toHaveBeenCalledWith("light");
    expect(patchAuthUser).toHaveBeenCalledWith({ themePreference: "LIGHT" });
    await waitFor(() => {
      expect(updateThemePreference).toHaveBeenCalledWith({ themePreference: "LIGHT" });
    });
  });

  it("shows a collapsed mobile trigger that expands the inline theme panel", () => {
    getAuthUserMock.mockReturnValue(null);

    render(<ThemeToggle />);

    const trigger = screen.getByRole("button", { name: "Theme: System" });
    expect(trigger).toHaveAttribute("aria-expanded", "false");

    fireEvent.click(trigger);

    expect(trigger).toHaveAttribute("aria-expanded", "true");
    expect(screen.getAllByRole("button", { name: "Use Light theme" })).toHaveLength(2);
    expect(screen.getAllByRole("button", { name: "Use Dark theme" })).toHaveLength(2);
    expect(screen.getAllByRole("button", { name: "Use System theme" })[1]).toHaveAttribute("aria-pressed", "true");
  });

  it("collapses the mobile panel after selecting a theme", async () => {
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

    render(<ThemeToggle />);

    const trigger = screen.getByRole("button", { name: "Theme: Light" });
    fireEvent.click(trigger);
    fireEvent.click(screen.getAllByRole("button", { name: "Use System theme" })[1]);

    expect(setThemeMock).toHaveBeenCalledWith("system");
    expect(patchAuthUser).toHaveBeenCalledWith({ themePreference: "SYSTEM" });
    await waitFor(() => {
      expect(updateThemePreference).toHaveBeenCalledWith({ themePreference: "SYSTEM" });
      expect(trigger).toHaveAttribute("aria-expanded", "false");
    });
  });

  it("collapses the mobile panel when clicking away", () => {
    getAuthUserMock.mockReturnValue(null);

    render(<ThemeToggle />);

    const trigger = screen.getByRole("button", { name: "Theme: System" });
    fireEvent.click(trigger);
    expect(trigger).toHaveAttribute("aria-expanded", "true");

    fireEvent.mouseDown(document.body);

    expect(trigger).toHaveAttribute("aria-expanded", "false");
  });
});
